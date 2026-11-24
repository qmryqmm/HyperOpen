package io.github.qmryqmm.hyperopen.market

import android.content.Context
import java.net.URI

/**
 * 市场页独立偏好持久化。
 * 刻意存放在私有 CE SharedPreferences（hyper_open_market_prefs），
 * 绝不写入 hyper_open_settings 或 DE 共享配置，确保 SystemUI 零联网且配置备份不被污染。
 */
object MarketSettings {

    private const val PREF_NAME = "hyper_open_market_prefs"
    private const val KEY_BASE_URL = "api_base_url"
    private const val KEY_LAST_AUTHOR = "last_author"
    private const val PREFIX_MANAGE_TOKEN = "manage_token_"

    const val DEFAULT_BASE_URL = "https://market.qmmry.cfd"
    const val KIND_CONFIG = "config"
    const val KIND_SHORTCUT = "shortcut"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 规范化并校验服务器 Base URL：
     * 去除首尾空白与末尾斜杠，仅接受 http:// 或 https:// 且包含有效主机名。
     * 不合法时返回 null。
     */
    fun normalizeBaseUrl(rawUrl: String?): String? {
        if (rawUrl == null) return null
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return null
        }
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host?.trim().orEmpty()
            if (host.isEmpty()) return null
            if (!uri.rawQuery.isNullOrEmpty() || !uri.rawFragment.isNullOrEmpty()) return null
            val portPart = if (uri.port > 0) ":${uri.port}" else ""
            val pathPart = uri.rawPath?.trimEnd('/').orEmpty()
            "$scheme://$host$portPart$pathPart"
        } catch (_: Throwable) {
            null
        }
    }

    fun getBaseUrl(ctx: Context): String {
        val stored = prefs(ctx).getString(KEY_BASE_URL, null)
        return normalizeBaseUrl(stored) ?: DEFAULT_BASE_URL
    }

    fun setBaseUrl(ctx: Context, rawUrl: String): String {
        val normalized = normalizeBaseUrl(rawUrl)
            ?: throw IllegalArgumentException("请输入有效的 http:// 或 https:// 服务器地址")
        prefs(ctx).edit().putString(KEY_BASE_URL, normalized).apply()
        return normalized
    }

    fun resetBaseUrl(ctx: Context): String {
        prefs(ctx).edit().putString(KEY_BASE_URL, DEFAULT_BASE_URL).apply()
        return DEFAULT_BASE_URL
    }

    fun getLastAuthor(ctx: Context): String {
        return prefs(ctx).getString(KEY_LAST_AUTHOR, "")?.trim().orEmpty()
    }

    fun setLastAuthor(ctx: Context, author: String) {
        prefs(ctx).edit().putString(KEY_LAST_AUTHOR, author.trim().take(40)).apply()
    }

    private fun tokenPrefKey(kind: String, id: String): String =
        "$PREFIX_MANAGE_TOKEN$kind:$id"

    fun getManageToken(ctx: Context, kind: String, id: String): String? {
        if (id.isBlank()) return null
        val v = prefs(ctx).getString(tokenPrefKey(kind, id), null)?.trim()
        return if (v.isNullOrEmpty()) null else v
    }

    fun saveManageToken(ctx: Context, kind: String, id: String, token: String) {
        if (id.isBlank() || token.isBlank()) return
        prefs(ctx).edit().putString(tokenPrefKey(kind, id), token.trim()).apply()
    }

    fun removeManageToken(ctx: Context, kind: String, id: String) {
        if (id.isBlank()) return
        prefs(ctx).edit().remove(tokenPrefKey(kind, id)).apply()
    }

    fun hasManageToken(ctx: Context, kind: String, id: String): Boolean {
        return getManageToken(ctx, kind, id) != null
    }
}
