package io.github.qmryqmm.hyperopen.market

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.net.ssl.SSLException

/**
 * 双市场 HTTP 客户端（纯 HttpURLConnection + Dispatchers.IO + org.json）。
 *
 * 特性：
 * - 仅在市场页面主动发起网络请求，不依赖任何第三方网络库，SystemUI 进程绝对零调用；
 * - 配合 ctx.cacheDir/market_http_cache 为详情接口提供 ETag / If-None-Match / 304 本地缓存；
 * - ?download=1 下载计数请求不带 If-None-Match，保证服务端准确计数，同时自动更新本地缓存（遇 304 时也可回退本地缓存）；
 * - 同时完整封装配置市场与快捷方式市场接口（供 Stage 2 与 Stage 3 复用）。
 */
object MarketApiClient {

    private const val CACHE_DIR_NAME = "market_http_cache"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 20_000
    private val CACHE_LOCK = Any()

    private data class HttpResult(
        val code: Int,
        val body: String,
        val etag: String?,
        val retryAfterHeader: Int?,
    )

    private data class CachedEntry(
        val etag: String,
        val body: String,
    )

    suspend fun checkHealth(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val url = "${MarketSettings.getBaseUrl(ctx)}/api/health"
        val res = executeRequest(ctx, url = url, method = "GET")
        if (res.code !in 200..299) {
            throw buildHttpException(res)
        }
        val json = JSONObject(res.body)
        json.optBoolean("ok", false)
    }

    // ─────────────────────────────────────────────────────────────────────
    // 配置市场接口
    // ─────────────────────────────────────────────────────────────────────

    suspend fun listConfigs(
        ctx: Context,
        query: String = "",
        layoutStyle: Int? = null,
        materialStyle: Int? = null,
        hasShortcuts: Boolean? = null,
        sort: String = "latest",
        page: Int = 1,
        pageSize: Int = 20,
    ): MarketPageResult<MarketConfigSummary> = withContext(Dispatchers.IO) {
        val params = buildList {
            if (query.isNotBlank()) add("q=${encodeQuery(query.trim())}")
            if (layoutStyle != null) add("layout=$layoutStyle")
            if (materialStyle != null) add("material=$materialStyle")
            if (hasShortcuts != null) add("hasShortcuts=${if (hasShortcuts) "1" else "0"}")
            add("sort=${encodeQuery(if (sort == "downloads") "downloads" else "latest")}")
            add("page=${page.coerceAtLeast(1)}")
            add("pageSize=${pageSize.coerceIn(1, 50)}")
        }.joinToString("&")

        val url = "${MarketSettings.getBaseUrl(ctx)}/api/configs?$params"
        val res = executeRequest(ctx, url = url, method = "GET")
        if (res.code !in 200..299) {
            throw buildHttpException(res)
        }
        val root = JSONObject(res.body)
        if (!root.optBoolean("ok", false)) {
            throw MarketApiException(res.code, root.optString("error", "加载配置列表失败"))
        }
        val itemsArr = root.optJSONArray("items") ?: JSONArray()
        val items = ArrayList<MarketConfigSummary>(itemsArr.length())
        for (i in 0 until itemsArr.length()) {
            itemsArr.optJSONObject(i)?.let { items.add(parseConfigSummary(it)) }
        }
        MarketPageResult(
            items = items,
            pagination = parsePagination(root.optJSONObject("pagination"), page, pageSize),
        )
    }

    suspend fun getConfigDetail(
        ctx: Context,
        id: String,
        download: Boolean = false,
    ): MarketConfigDetail = withContext(Dispatchers.IO) {
        val baseUrl = MarketSettings.getBaseUrl(ctx)
        val encodedId = encodePathSegment(id)
        val url = if (download) {
            "$baseUrl/api/configs/$encodedId?download=1"
        } else {
            "$baseUrl/api/configs/$encodedId"
        }
        val cacheKey = "${MarketSettings.KIND_CONFIG}:$baseUrl:$id"
        val cached = readCache(ctx, cacheKey)

        // 若带 ?download=1，不发 If-None-Match，确保服务端计一次下载；若仍返回 304 则复用缓存正文
        val headers = if (!download && cached != null && cached.etag.isNotEmpty()) {
            mapOf("If-None-Match" to cached.etag)
        } else {
            emptyMap()
        }

        val res = executeRequest(ctx, url = url, method = "GET", headers = headers)
        if (res.code == HttpURLConnection.HTTP_NOT_MODIFIED && cached != null) {
            return@withContext parseConfigDetail(JSONObject(cached.body), fromCache = true)
        }
        if (res.code !in 200..299) {
            throw buildHttpException(res)
        }
        val root = JSONObject(res.body)
        if (!root.optBoolean("ok", false)) {
            throw MarketApiException(res.code, root.optString("error", "获取配置详情失败"))
        }
        val sha = root.optString("sha256", "")
        val etag = res.etag?.takeIf { it.isNotBlank() }
            ?: if (sha.isNotEmpty()) "\"$sha\"" else ""
        if (etag.isNotEmpty()) {
            writeCache(ctx, cacheKey, etag, res.body)
        }
        parseConfigDetail(root, fromCache = false)
    }

    suspend fun uploadConfig(
        ctx: Context,
        name: String,
        description: String,
        author: String,
        tags: List<String>,
        minAppVersionCode: Int,
        appVersion: String,
        jsonBody: String,
    ): MarketUploadResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("name", name.trim())
            if (description.isNotBlank()) put("description", description.trim())
            put("author", author.trim())
            if (tags.isNotEmpty()) put("tags", JSONArray(tags))
            if (minAppVersionCode > 0) put("minAppVersionCode", minAppVersionCode)
            if (appVersion.isNotBlank()) put("appVersion", appVersion.trim())
            put("jsonBody", jsonBody)
        }
        val bodyText = payload.toString()
        if (bodyText.toByteArray(StandardCharsets.UTF_8).size > MarketSanitizer.MAX_PAYLOAD_BYTES) {
            throw MarketApiException(413, "上传内容超过 512 KB 上限，请精简快捷项或移除自定义图标")
        }
        val url = "${MarketSettings.getBaseUrl(ctx)}/api/configs"
        val res = executeRequest(ctx, url = url, method = "POST", jsonBody = bodyText)
        if (res.code !in 200..299) {
            throw buildHttpException(res)
        }
        val root = JSONObject(res.body)
        if (!root.optBoolean("ok", false)) {
            throw MarketApiException(res.code, root.optString("error", "上传配置失败"))
        }
        MarketUploadResult(
            id = root.optString("id", ""),
            status = root.optString("status", "approved"),
            reviewReasons = parseStringArray(root.optJSONArray("reviewReasons")),
            manageToken = root.optString("manageToken", ""),
            configItem = root.optJSONObject("item")?.let(::parseConfigSummary),
        )
    }

    suspend fun deleteConfig(
        ctx: Context,
        id: String,
        manageToken: String,
    ): MarketDeleteResult = withContext(Dispatchers.IO) {
        val url = "${MarketSettings.getBaseUrl(ctx)}/api/configs/${encodePathSegment(id)}"
        val res = executeRequest(
            ctx,
            url = url,
            method = "DELETE",
            headers = mapOf("X-Manage-Token" to manageToken.trim()),
        )
        if (res.code !in 200..299) {
            throw buildHttpException(res)
        }
        val root = JSONObject(res.body)
        if (!root.optBoolean("ok", false)) {
            throw MarketApiException(res.code, root.optString("error", "删除配置失败"))
        }
        MarketDeleteResult(
            id = root.optString("id", id),
            deleted = root.optBoolean("deleted", true),
        )
    }

    suspend fun reportConfig(
        ctx: Context,
        id: String,
        reason: String = "",
    ): MarketReportResult = withContext(Dispatchers.IO) {
        val url = "${MarketSettings.getBaseUrl(ctx)}/api/configs/${encodePathSegment(id)}/report"
        val payload = JSONObject().apply {
            if (reason.isNotBlank()) put("reason", reason.trim().take(300))
        }.toString()
        val res = executeRequest(ctx, url = url, method = "POST", jsonBody = payload)
        if (res.code !in 200..299) {
            throw buildHttpException(res)
        }
        val root = JSONObject(res.body)
        if (!root.optBoolean("ok", false)) {
            throw MarketApiException(res.code, root.optString("error", "举报失败"))
        }
        MarketReportResult(
            id = root.optString("id", id),
            reportsCount = root.optInt("reportsCount", 1),
            hidden = root.optBoolean("hidden", false),
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // 快捷方式市场接口（供 Stage 3 复用）
    // ─────────────────────────────────────────────────────────────────────

    suspend fun listShortcuts(
        ctx: Context,
        query: String = "",
        actionType: String? = null,
        hasRoot: Boolean? = null,
        hasImages: Boolean? = null,
        sort: String = "latest",
        page: Int = 1,
        pageSize: Int = 20,
    ): MarketPageResult<MarketShortcutPackSummary> = withContext(Dispatchers.IO) {
        val params = buildList {
            if (query.isNotBlank()) add("q=${encodeQuery(query.trim())}")
            if (!actionType.isNullOrBlank()) add("actionType=${encodeQuery(actionType.trim())}")
            if (hasRoot != null) add("hasRoot=${if (hasRoot) "1" else "0"}")
            if (hasImages != null) add("hasImages=${if (hasImages) "1" else "0"}")
            add("sort=${encodeQuery(if (sort == "downloads") "downloads" else "latest")}")
            add("page=${page.coerceAtLeast(1)}")
            add("pageSize=${pageSize.coerceIn(1, 50)}")
        }.joinToString("&")

        val url = "${MarketSettings.getBaseUrl(ctx)}/api/shortcuts?$params"
        val res = executeRequest(ctx, url = url, method = "GET")
        if (res.code !in 200..299) {
            throw buildHttpException(res)
        }
        val root = JSONObject(res.body)
        if (!root.optBoolean("ok", false)) {
            throw MarketApiException(res.code, root.optString("error", "加载快捷方式包失败"))
        }
        val itemsArr = root.optJSONArray("items") ?: JSONArray()
        val items = ArrayList<MarketShortcutPackSummary>(itemsArr.length())
        for (i in 0 until itemsArr.length()) {
            itemsArr.optJSONObject(i)?.let { items.add(parseShortcutPackSummary(it)) }
        }
        MarketPageResult(
            items = items,
            pagination = parsePagination(root.optJSONObject("pagination"), page, pageSize),
        )
    }

    suspend fun getShortcutDetail(
        ctx: Context,
        id: String,
        download: Boolean = false,
    ): MarketShortcutPackDetail = withContext(Dispatchers.IO) {
        val baseUrl = MarketSettings.getBaseUrl(ctx)
        val encodedId = encodePathSegment(id)
        val url = if (download) {
            "$baseUrl/api/shortcuts/$encodedId?download=1"
        } else {
            "$baseUrl/api/shortcuts/$encodedId"
        }
        val cacheKey = "${MarketSettings.KIND_SHORTCUT}:$baseUrl:$id"
        val cached = readCache(ctx, cacheKey)
        val headers = if (!download && cached != null && cached.etag.isNotEmpty()) {
            mapOf("If-None-Match" to cached.etag)
        } else {
            emptyMap()
        }

        val res = executeRequest(ctx, url = url, method = "GET", headers = headers)
        if (res.code == HttpURLConnection.HTTP_NOT_MODIFIED && cached != null) {
            return@withContext parseShortcutPackDetail(JSONObject(cached.body), fromCache = true)
        }
        if (res.code !in 200..299) {
            throw buildHttpException(res)
        }
        val root = JSONObject(res.body)
        if (!root.optBoolean("ok", false)) {
            throw MarketApiException(res.code, root.optString("error", "获取快捷方式包详情失败"))
        }
        val sha = root.optString("sha256", "")
        val etag = res.etag?.takeIf { it.isNotBlank() }
            ?: if (sha.isNotEmpty()) "\"$sha\"" else ""
        if (etag.isNotEmpty()) {
            writeCache(ctx, cacheKey, etag, res.body)
        }
        parseShortcutPackDetail(root, fromCache = false)
    }

    suspend fun uploadShortcutPack(
        ctx: Context,
        name: String,
        description: String,
        author: String,
        tags: List<String>,
        minAppVersionCode: Int,
        appVersion: String,
        shortcutsJson: String,
    ): MarketUploadResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("name", name.trim())
            if (description.isNotBlank()) put("description", description.trim())
            put("author", author.trim())
            if (tags.isNotEmpty()) put("tags", JSONArray(tags))
            if (minAppVersionCode > 0) put("minAppVersionCode", minAppVersionCode)
            if (appVersion.isNotBlank()) put("appVersion", appVersion.trim())
            put("shortcutsJson", shortcutsJson)
        }
        val bodyText = payload.toString()
        if (bodyText.toByteArray(StandardCharsets.UTF_8).size > MarketSanitizer.MAX_PAYLOAD_BYTES) {
            throw MarketApiException(413, "上传内容超过 512 KB 上限，请精简快捷项或移除自定义图标")
        }
        val url = "${MarketSettings.getBaseUrl(ctx)}/api/shortcuts"
        val res = executeRequest(ctx, url = url, method = "POST", jsonBody = bodyText)
        if (res.code !in 200..299) {
            throw buildHttpException(res)
        }
        val root = JSONObject(res.body)
        if (!root.optBoolean("ok", false)) {
            throw MarketApiException(res.code, root.optString("error", "上传快捷方式包失败"))
        }
        MarketUploadResult(
            id = root.optString("id", ""),
            status = root.optString("status", "approved"),
            reviewReasons = parseStringArray(root.optJSONArray("reviewReasons")),
            manageToken = root.optString("manageToken", ""),
            shortcutPackItem = root.optJSONObject("item")?.let(::parseShortcutPackSummary),
        )
    }

    suspend fun deleteShortcutPack(
        ctx: Context,
        id: String,
        manageToken: String,
    ): MarketDeleteResult = withContext(Dispatchers.IO) {
        val url = "${MarketSettings.getBaseUrl(ctx)}/api/shortcuts/${encodePathSegment(id)}"
        val res = executeRequest(
            ctx,
            url = url,
            method = "DELETE",
            headers = mapOf("X-Manage-Token" to manageToken.trim()),
        )
        if (res.code !in 200..299) {
            throw buildHttpException(res)
        }
        val root = JSONObject(res.body)
        if (!root.optBoolean("ok", false)) {
            throw MarketApiException(res.code, root.optString("error", "删除快捷方式包失败"))
        }
        MarketDeleteResult(
            id = root.optString("id", id),
            deleted = root.optBoolean("deleted", true),
        )
    }

    suspend fun reportShortcutPack(
        ctx: Context,
        id: String,
        reason: String = "",
    ): MarketReportResult = withContext(Dispatchers.IO) {
        val url = "${MarketSettings.getBaseUrl(ctx)}/api/shortcuts/${encodePathSegment(id)}/report"
        val payload = JSONObject().apply {
            if (reason.isNotBlank()) put("reason", reason.trim().take(300))
        }.toString()
        val res = executeRequest(ctx, url = url, method = "POST", jsonBody = payload)
        if (res.code !in 200..299) {
            throw buildHttpException(res)
        }
        val root = JSONObject(res.body)
        if (!root.optBoolean("ok", false)) {
            throw MarketApiException(res.code, root.optString("error", "举报失败"))
        }
        MarketReportResult(
            id = root.optString("id", id),
            reportsCount = root.optInt("reportsCount", 1),
            hidden = root.optBoolean("hidden", false),
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // HTTP 传输、错误映射与 ETag 本地文件缓存
    // ─────────────────────────────────────────────────────────────────────

    private fun executeRequest(
        ctx: Context,
        url: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        jsonBody: String? = null,
    ): HttpResult {
        if (!isNetworkAvailable(ctx)) {
            throw MarketApiException(
                statusCode = 0,
                message = "当前设备无可用网络连接，请检查网络后重试",
                isNetworkError = true,
            )
        }
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "HyperOpen-Android")
                for ((k, v) in headers) {
                    setRequestProperty(k, v)
                }
                if (jsonBody != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val bytes = jsonBody.toByteArray(StandardCharsets.UTF_8)
                    setFixedLengthStreamingMode(bytes.size)
                    outputStream.use { os ->
                        os.write(bytes)
                        os.flush()
                    }
                }
            }

            val code = conn.responseCode
            val etag = conn.getHeaderField("ETag")?.trim()
            val retryAfter = conn.getHeaderField("Retry-After")?.trim()?.toIntOrNull()
            val stream: InputStream? = if (code in 200..399) {
                conn.inputStream
            } else {
                conn.errorStream ?: conn.inputStream
            }
            val body = stream?.use(::readAllUtf8) ?: ""
            return HttpResult(code = code, body = body, etag = etag, retryAfterHeader = retryAfter)
        } catch (e: MarketApiException) {
            throw e
        } catch (e: UnknownHostException) {
            throw MarketApiException(
                statusCode = 0,
                message = "无法解析市场服务器域名，请检查网络或服务器地址",
                isNetworkError = true,
                cause = e,
            )
        } catch (e: SocketTimeoutException) {
            throw MarketApiException(
                statusCode = 0,
                message = "连接市场服务器超时，请稍后重试",
                isNetworkError = true,
                cause = e,
            )
        } catch (e: ConnectException) {
            throw MarketApiException(
                statusCode = 0,
                message = "无法连接到市场服务器，请检查网络或服务器地址",
                isNetworkError = true,
                cause = e,
            )
        } catch (e: SSLException) {
            throw MarketApiException(
                statusCode = 0,
                message = "HTTPS 安全连接失败，请检查系统时间或服务器地址",
                isNetworkError = true,
                cause = e,
            )
        } catch (e: Throwable) {
            throw MarketApiException(
                statusCode = 0,
                message = "网络请求失败：${e.message ?: e.javaClass.simpleName}",
                isNetworkError = true,
                cause = e,
            )
        } finally {
            conn?.disconnect()
        }
    }

    private fun buildHttpException(res: HttpResult): MarketApiException {
        var serverErr = ""
        var retryAfter = res.retryAfterHeader
        var existingId: String? = null
        var existingStatus: String? = null
        if (res.body.isNotBlank()) {
            try {
                val obj = JSONObject(res.body)
                serverErr = obj.optString("error", "").trim()
                if (obj.has("retryAfterSeconds")) {
                    retryAfter = obj.optInt("retryAfterSeconds", retryAfter ?: 0).takeIf { it > 0 } ?: retryAfter
                }
                if (obj.has("existingId") && !obj.isNull("existingId")) {
                    existingId = obj.optString("existingId", "").takeIf { it.isNotBlank() }
                }
                if (obj.has("existingStatus") && !obj.isNull("existingStatus")) {
                    existingStatus = obj.optString("existingStatus", "").takeIf { it.isNotBlank() }
                }
            } catch (_: Throwable) {
            }
        }
        val msg = when (res.code) {
            400 -> if (serverErr.isNotEmpty()) "参数校验失败：$serverErr" else "请求参数无效"
            401 -> if (serverErr.isNotEmpty()) "缺少管理凭证：$serverErr" else "缺少有效管理凭证"
            403 -> if (serverErr.isNotEmpty()) "管理凭证无效：$serverErr" else "无权执行此操作"
            404 -> if (serverErr.isNotEmpty()) serverErr else "目标条目不存在或已下架"
            409 -> if (existingId != null) {
                val stLabel = when (existingStatus) {
                    "pending" -> "待审核"
                    "approved" -> "已上架"
                    else -> existingStatus.orEmpty()
                }
                "市场中已存在相同内容（ID: $existingId${if (stLabel.isNotEmpty()) "，状态: $stLabel" else ""}）"
            } else {
                serverErr.ifEmpty { "操作冲突，请勿重复提交" }
            }
            413 -> serverErr.ifEmpty { "上传内容超过 512 KB 上限，请精简配置或压缩图片后重试" }
            429 -> {
                val base = serverErr.ifEmpty { "操作过于频繁，请稍后再试" }
                if (retryAfter != null && retryAfter > 0) "$base（请 ${retryAfter} 秒后再试）" else base
            }
            in 500..599 -> "服务器暂时异常（HTTP ${res.code}）${if (serverErr.isNotEmpty()) "：$serverErr" else ""}"
            else -> serverErr.ifEmpty { "请求失败（HTTP ${res.code}）" }
        }
        return MarketApiException(
            statusCode = res.code,
            message = msg,
            serverError = serverErr,
            retryAfterSeconds = retryAfter,
            existingId = existingId,
            existingStatus = existingStatus,
        )
    }

    private fun isNetworkAvailable(ctx: Context): Boolean {
        return try {
            val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Throwable) {
            true
        }
    }

    private fun cacheFile(ctx: Context, cacheKey: String): File {
        val dir = File(ctx.applicationContext.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "${sha256Hex(cacheKey)}.json")
    }

    private fun readCache(ctx: Context, cacheKey: String): CachedEntry? {
        synchronized(CACHE_LOCK) {
            return try {
                val file = cacheFile(ctx, cacheKey)
                if (!file.exists()) return null
                val raw = file.readText(StandardCharsets.UTF_8)
                if (raw.isBlank()) return null
                val obj = JSONObject(raw)
                val etag = obj.optString("etag", "")
                val body = obj.optString("body", "")
                if (body.isBlank()) null else CachedEntry(etag = etag, body = body)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun writeCache(ctx: Context, cacheKey: String, etag: String, body: String) {
        synchronized(CACHE_LOCK) {
            try {
                val target = cacheFile(ctx, cacheKey)
                val tmp = File(target.parentFile, "${target.name}.tmp")
                val wrapper = JSONObject().apply {
                    put("etag", etag)
                    put("cachedAt", System.currentTimeMillis())
                    put("body", body)
                }
                tmp.writeText(wrapper.toString(), StandardCharsets.UTF_8)
                if (target.exists()) target.delete()
                tmp.renameTo(target)
            } catch (_: Throwable) {
            }
        }
    }

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    private fun readAllUtf8(input: InputStream): String {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            bos.write(buf, 0, n)
        }
        return bos.toString(StandardCharsets.UTF_8.name())
    }

    private fun encodeQuery(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    private fun encodePathSegment(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    // ─────────────────────────────────────────────────────────────────────
    // JSON 解析辅助
    // ─────────────────────────────────────────────────────────────────────

    private fun parseStringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotEmpty()) list.add(s)
        }
        return list
    }

    private fun parseRiskArray(arr: JSONArray?): List<MarketRiskEntry> {
        if (arr == null) return emptyList()
        val list = ArrayList<MarketRiskEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(
                MarketRiskEntry(
                    path = o.optString("path", ""),
                    title = o.optString("title", ""),
                    actionType = o.optString("actionType", ""),
                    reason = o.optString("reason", ""),
                    shellUseRoot = o.optBoolean("shellUseRoot", false),
                    userId = o.optInt("userId", 0),
                    payload = o.optString("payload", ""),
                )
            )
        }
        return list
    }

    private fun parsePagination(obj: JSONObject?, fallbackPage: Int, fallbackPageSize: Int): MarketPagination {
        if (obj == null) {
            return MarketPagination(fallbackPage, fallbackPageSize, 0, 1, false)
        }
        return MarketPagination(
            page = obj.optInt("page", fallbackPage),
            pageSize = obj.optInt("pageSize", fallbackPageSize),
            total = obj.optInt("total", 0),
            totalPages = obj.optInt("totalPages", 1),
            hasMore = obj.optBoolean("hasMore", false),
        )
    }

    private fun parseConfigSummary(o: JSONObject): MarketConfigSummary {
        return MarketConfigSummary(
            id = o.optString("id", ""),
            name = o.optString("name", ""),
            description = o.optString("description", ""),
            author = o.optString("author", ""),
            tags = parseStringArray(o.optJSONArray("tags")),
            layoutStyle = o.optInt("layoutStyle", 0),
            materialStyle = o.optInt("materialStyle", 2),
            keyCount = o.optInt("keyCount", 0),
            shortcutCount = o.optInt("shortcutCount", 0),
            hasShortcuts = o.optBoolean("hasShortcuts", false),
            customImageCount = o.optInt("customImageCount", 0),
            hasRootRisk = o.optBoolean("hasRootRisk", false),
            riskSummary = parseRiskArray(o.optJSONArray("riskSummary")),
            reviewReasons = parseStringArray(o.optJSONArray("reviewReasons")),
            minAppVersionCode = o.optInt("minAppVersionCode", 0),
            appVersion = o.optString("appVersion", ""),
            downloads = o.optInt("downloads", 0),
            reportsCount = o.optInt("reportsCount", 0),
            status = o.optString("status", "approved"),
            sha256 = o.optString("sha256", ""),
            sizeBytes = o.optInt("sizeBytes", 0),
            createdAt = o.optLong("createdAt", 0L),
            updatedAt = o.optLong("updatedAt", 0L),
        )
    }

    private fun parseConfigDetail(root: JSONObject, fromCache: Boolean): MarketConfigDetail {
        val itemObj = root.optJSONObject("item")
            ?: throw MarketApiException(500, "服务端返回缺少 item 字段")
        return MarketConfigDetail(
            item = parseConfigSummary(itemObj),
            jsonBody = root.optString("jsonBody", ""),
            sha256 = root.optString("sha256", ""),
            fromCache = fromCache,
        )
    }

    private fun parseShortcutPackSummary(o: JSONObject): MarketShortcutPackSummary {
        return MarketShortcutPackSummary(
            id = o.optString("id", ""),
            name = o.optString("name", ""),
            description = o.optString("description", ""),
            author = o.optString("author", ""),
            tags = parseStringArray(o.optJSONArray("tags")),
            shortcutCount = o.optInt("shortcutCount", 0),
            folderCount = o.optInt("folderCount", 0),
            actionTypes = parseStringArray(o.optJSONArray("actionTypes")),
            customImageCount = o.optInt("customImageCount", 0),
            hasRootRisk = o.optBoolean("hasRootRisk", false),
            riskSummary = parseRiskArray(o.optJSONArray("riskSummary")),
            reviewReasons = parseStringArray(o.optJSONArray("reviewReasons")),
            minAppVersionCode = o.optInt("minAppVersionCode", 0),
            appVersion = o.optString("appVersion", ""),
            downloads = o.optInt("downloads", 0),
            reportsCount = o.optInt("reportsCount", 0),
            status = o.optString("status", "approved"),
            sha256 = o.optString("sha256", ""),
            sizeBytes = o.optInt("sizeBytes", 0),
            createdAt = o.optLong("createdAt", 0L),
            updatedAt = o.optLong("updatedAt", 0L),
        )
    }

    private fun parseShortcutPackDetail(root: JSONObject, fromCache: Boolean): MarketShortcutPackDetail {
        val itemObj = root.optJSONObject("item")
            ?: throw MarketApiException(500, "服务端返回缺少 item 字段")
        return MarketShortcutPackDetail(
            item = parseShortcutPackSummary(itemObj),
            shortcutsJson = root.optString("shortcutsJson", "[]"),
            sha256 = root.optString("sha256", ""),
            fromCache = fromCache,
        )
    }
}
