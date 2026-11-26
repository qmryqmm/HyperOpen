package io.github.qmryqmm.hyperopen.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import io.github.qmryqmm.hyperopen.model.ActivityLaunchSpec
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * 设备 Android 用户空间选项，供编辑页与选择器共享展示。
 */
data class AndroidUserOption(
    val id: Int,
    val name: String,
    val isProfile: Boolean = true,
    val isRunning: Boolean = true
)

/**
 * Android 多用户/分身空间辅助工具：
 * 1) 零 Root 快速获取主空间与 Profile（应用双开 999、工作资料等）；
 * 2) 可选通过 Root (`pm list users`) 补充独立用户（如手机分身）及系统命名；
 * 3) 检测目标应用在各用户空间中的安装状态；
 * 4) 从快捷方式动作参数中提取目标包名。
 */
object AndroidUserHelper {

    private const val CACHE_TTL_MS = 60_000L
    private const val ROOT_TIMEOUT_MS = 2500L

    private val userHandleRegex = Regex("""UserHandle\{(\d+)\}""")
    private val pmUserInfoRegex = Regex("""UserInfo\{(\d+):([^:]*):([0-9a-fA-F]+)\}(\s+running)?""")

    @Volatile
    private var cachedUsers: List<AndroidUserOption>? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    /**
     * 从 UserHandle 提取整数 userId：
     * 优先反射调用 getIdentifier()，回退正则解析 toString()，再回退 hashCode()。
     */
    fun extractUserId(handle: UserHandle): Int {
        try {
            val m = UserHandle::class.java.getMethod("getIdentifier")
            val res = m.invoke(handle)
            if (res is Int) {
                return res
            }
        } catch (_: Throwable) {
        }
        val match = userHandleRegex.find(handle.toString())
        val parsed = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (parsed != null) {
            return parsed
        }
        return handle.hashCode()
    }

    /**
     * 从 LauncherApps.getProfiles() 与 UserManager.getUserProfiles() 中查找匹配 userId 的真实 UserHandle。
     */
    fun resolveProfileHandle(ctx: Context, userId: Int): UserHandle? {
        for (handle in collectProfileHandles(ctx)) {
            if (extractUserId(handle) == userId) {
                return handle
            }
        }
        return null
    }

    /**
     * 零 Root 快速加载当前可见的用户空间（合并 LauncherApps 与 UserManager 的 Profile）。
     * 确保 0 -> "主空间" 始终存在；999 命名为 "应用双开 (XSpace)"，其他 > 0 Profile 命名为 "工作资料"。
     */
    fun loadFastUsers(ctx: Context): List<AndroidUserOption> {
        val map = LinkedHashMap<Int, AndroidUserOption>()
        map[0] = AndroidUserOption(
            id = 0,
            name = "主空间",
            isProfile = true,
            isRunning = true
        )
        for (handle in collectProfileHandles(ctx)) {
            val id = extractUserId(handle)
            if (id < 0 || map.containsKey(id)) continue
            val name = when (id) {
                0 -> "主空间"
                999 -> "应用双开 (XSpace)"
                else -> "工作资料"
            }
            map[id] = AndroidUserOption(
                id = id,
                name = name,
                isProfile = true,
                isRunning = true
            )
        }
        return sortUserOptions(map.values)
    }

    /**
     * 结合 Root `pm list users` 丰富设备用户列表（应在 Dispatchers.IO 调用，进程内缓存 60s）。
     * 无 Root 或执行失败时静默降级为 loadFastUsers(ctx) 的结果。
     */
    fun loadDeviceUsersWithRootEnrich(ctx: Context): List<AndroidUserOption> {
        val now = System.currentTimeMillis()
        val cached = cachedUsers
        if (cached != null && (now - cachedAtMs) in 0 until CACHE_TTL_MS) {
            return cached
        }
        synchronized(this) {
            val now2 = System.currentTimeMillis()
            val cached2 = cachedUsers
            if (cached2 != null && (now2 - cachedAtMs) in 0 until CACHE_TTL_MS) {
                return cached2
            }
            val map = LinkedHashMap<Int, AndroidUserOption>()
            for (opt in loadFastUsers(ctx)) {
                map[opt.id] = opt
            }
            val output = runPmListUsersQuiet()
            if (output != null) {
                for (match in pmUserInfoRegex.findAll(output)) {
                    val id = match.groupValues[1].toIntOrNull() ?: continue
                    val rawName = match.groupValues[2].trim()
                    val isRunning = match.groupValues[4].isNotEmpty()
                    val existing = map[id]
                    val resolvedName = when (id) {
                        0 -> "主空间"
                        999 -> "应用双开 (XSpace)"
                        else -> rawName.ifEmpty { "用户 $id" }
                    }
                    if (existing != null) {
                        map[id] = existing.copy(
                            name = resolvedName,
                            isRunning = isRunning
                        )
                    } else {
                        map[id] = AndroidUserOption(
                            id = id,
                            name = resolvedName,
                            isProfile = false,
                            isRunning = isRunning
                        )
                    }
                }
            }
            val result = sortUserOptions(map.values)
            cachedUsers = result
            cachedAtMs = System.currentTimeMillis()
            return result
        }
    }

    /**
     * 检测目标包名在指定用户列表中的安装状态（应在 Dispatchers.IO 调用）：
     * - true：已安装；false：未安装；null：非 Profile 独立用户无法免 Root 检测。
     */
    fun inspectPackageInstalledByUser(
        ctx: Context,
        packageName: String,
        userIds: List<Int>
    ): Map<Int, Boolean?> {
        val pkg = packageName.trim()
        val result = LinkedHashMap<Int, Boolean?>()
        if (pkg.isEmpty()) {
            for (id in userIds) {
                result[id] = null
            }
            return result
        }
        val pm = ctx.packageManager
        val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        for (userId in userIds) {
            if (userId == 0) {
                result[userId] = try {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                } catch (_: Throwable) {
                    false
                }
                continue
            }
            if (userId < 0) {
                result[userId] = null
                continue
            }
            val handle = resolveProfileHandle(ctx, userId)
            if (handle == null || la == null) {
                // 非 Profile 独立用户（如手机分身），无法免 Root 检测
                result[userId] = null
                continue
            }
            var installed: Boolean? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val info = la.getApplicationInfo(pkg, 0, handle)
                    installed = info != null
                } catch (_: PackageManager.NameNotFoundException) {
                    installed = false
                } catch (_: Throwable) {
                    installed = null
                }
            }
            if (installed == null) {
                installed = try {
                    !la.getActivityList(pkg, handle).isNullOrEmpty()
                } catch (_: Throwable) {
                    false
                }
            }
            result[userId] = installed
        }
        return result
    }

    /**
     * 从快捷方式动作参数中提取目标应用包名（支持 APP / ACTIVITY / INTENT_URI）。
     */
    fun resolveTargetPackageFromShortcut(
        actionType: String,
        actionPayload: String,
        actPackage: String,
        actComponent: String
    ): String? {
        return when (actionType) {
            ShortcutItem.ACTION_APP -> {
                actionPayload.trim().takeIf { it.isNotEmpty() }
            }
            ShortcutItem.ACTION_ACTIVITY -> {
                val pkg = actPackage.trim()
                if (pkg.isNotEmpty()) {
                    return pkg
                }
                val comp = actComponent.trim()
                if (comp.isNotEmpty()) {
                    val cn = ComponentName.unflattenFromString(comp)
                    if (cn != null && cn.packageName.isNotEmpty()) {
                        return cn.packageName
                    }
                    val slashIdx = comp.indexOf('/')
                    if (slashIdx > 0) {
                        return comp.substring(0, slashIdx).trim().takeIf { it.isNotEmpty() }
                    }
                }
                val spec = ActivityLaunchSpec.fromJson(actionPayload)
                val specPkg = spec.packageName?.trim().orEmpty()
                if (specPkg.isNotEmpty()) {
                    return specPkg
                }
                spec.resolveComponent()?.packageName?.takeIf { it.isNotEmpty() }
            }
            ShortcutItem.ACTION_INTENT_URI -> {
                val trimmed = actionPayload.trim()
                if (trimmed.isEmpty()) return null
                try {
                    val intent = Intent.parseUri(trimmed, Intent.URI_INTENT_SCHEME)
                    intent.`package`?.trim()?.takeIf { it.isNotEmpty() }
                        ?: intent.component?.packageName?.trim()?.takeIf { it.isNotEmpty() }
                } catch (_: Throwable) {
                    null
                }
            }
            else -> null
        }
    }

    private fun collectProfileHandles(ctx: Context): List<UserHandle> {
        val list = ArrayList<UserHandle>()
        try {
            val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            if (la != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                la.profiles?.let { list.addAll(it) }
            }
        } catch (_: Throwable) {
        }
        try {
            val um = ctx.getSystemService(Context.USER_SERVICE) as? UserManager
            um?.userProfiles?.let { list.addAll(it) }
        } catch (_: Throwable) {
        }
        return list
    }

    private fun sortUserOptions(options: Collection<AndroidUserOption>): List<AndroidUserOption> {
        return options.sortedWith(compareBy<AndroidUserOption> { if (it.id == 0) Int.MIN_VALUE else it.id })
    }

    private fun runPmListUsersQuiet(): String? {
        val suBins = arrayOf("su", "/system/bin/su")
        var process: Process? = null
        try {
            for (bin in suBins) {
                try {
                    process = ProcessBuilder(bin, "-c", "pm list users").start()
                    break
                } catch (_: IOException) {
                }
            }
            val p = process ?: return null
            try {
                p.outputStream.close()
            } catch (_: Throwable) {
            }
            val outBuf = ByteArrayOutputStream()
            val errBuf = ByteArrayOutputStream()
            val drainIn = Thread { drainQuiet(p.inputStream, outBuf, 8192) }.apply {
                isDaemon = true
                start()
            }
            val drainErr = Thread { drainQuiet(p.errorStream, errBuf, 2048) }.apply {
                isDaemon = true
                start()
            }
            val finished = p.waitFor(ROOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                p.destroyForcibly()
                return null
            }
            try {
                drainIn.join(300L)
            } catch (_: InterruptedException) {
            }
            try {
                drainErr.join(300L)
            } catch (_: InterruptedException) {
            }
            if (p.exitValue() != 0) {
                return null
            }
            return String(outBuf.toByteArray(), StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            return null
        } finally {
            try {
                process?.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    private fun drainQuiet(stream: InputStream?, out: ByteArrayOutputStream, maxBytes: Int) {
        if (stream == null) return
        try {
            val buf = ByteArray(1024)
            var remaining = maxBytes
            while (true) {
                val n = stream.read(buf)
                if (n == -1) break
                if (remaining > 0) {
                    val toWrite = minOf(n, remaining)
                    out.write(buf, 0, toWrite)
                    remaining -= toWrite
                }
            }
        } catch (_: Throwable) {
        } finally {
            try {
                stream.close()
            } catch (_: Throwable) {
            }
        }
    }
}
