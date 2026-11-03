package io.github.qmryqmm.hyperopen.ui.pages

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 运行权限自检二级页面。
 * 检查并引导授予模块运行所需的各项关键系统权限：
 *   1. Root 授权（重启 SystemUI、Shell 动作、跨用户启动与壁纸读取兜底）；
 *   2. 已安装应用读取权限（应用选择列表、图标加载）；
 *   3. 勿扰访问策略权限；
 *   4. 所有文件存储访问权限（自定义图标选择）；
 *   5. LSPosed 作用域与启用状态检测。
 */

@Composable
fun PermissionCheckPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
) {
    val ctx = LocalContext.current
    var detectTick by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    val status = remember { androidx.compose.runtime.mutableStateMapOf<String, Pair<Boolean?, String>>() }
    val items = remember {
        listOf(
            Triple("root", "Root 权限", "用途：重启系统界面、SHELL/root 启动动作、跨用户启动兜底、锁屏壁纸读取兜底。建议授权。"),
            Triple("packages", "应用列表", "用途：快捷方式「从已安装应用选取」列表、应用图标获取与图标裁切缓存、APP 动作启动目标解析。"),
            Triple("dnd", "勿扰策略访问", "用途：快捷动作切换勿扰与响铃模式。本项仅反映本应用自身授权状态，有root无需授权"),
            Triple("storage", "所有文件访问", "用途：直接读取本地图片作为自定义图标；经系统相册 / 文件选择器选图时无需此权限。"),
            Triple("lsposed", "LSPosed 模块与作用域", "用途：模块需在 LSPosed 中启用并勾选作用域 com.android.systemui，否则锁屏快捷面板不生效；读取 LSPosed 数据校验依赖 Root。"),
        )
    }

    val permScope = androidx.compose.runtime.rememberCoroutineScope()
    // 从系统授权页返回后整页复检
    val permSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { detectTick++ }

    fun refreshKey(key: String, rootOk: Boolean) {
        permScope.launch {
            status[key] = withContext(Dispatchers.IO) {
                when (key) {
                    "root" -> detectRootAccess()
                    "packages" -> detectPackageVisibility(ctx)
                    "dnd" -> detectDndAccess(ctx)
                    "storage" -> detectStorageAccess()
                    else -> detectLsposedScope(ctx, rootOk)
                }
            }
        }
    }

    // 点击「未满足」卡片时尝试申请对应权限
    fun tryRequestPermission(key: String) {
        when (key) {
            "root" -> {
                status[key] = null to "正在请求 Root 授权…"
                Toast.makeText(ctx, "已唤起 Root 授权请求，请在弹窗中允许", Toast.LENGTH_SHORT).show()
                refreshKey("root", false)
            }
            "packages" -> Toast.makeText(
                ctx,
                "该权限随安装包声明授予，无法运行时申请；若应用列表偏少属系统限制",
                Toast.LENGTH_SHORT
            ).show()
            "dnd" -> runCatching {
                permSettingsLauncher.launch(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }.onFailure {
                Toast.makeText(ctx, "未找到勿扰授权设置页", Toast.LENGTH_SHORT).show()
            }
            "storage" -> {
                val intent = runCatching {
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:${ctx.packageName}")
                    )
                }.getOrElse { Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION) }
                runCatching { permSettingsLauncher.launch(intent) }.onFailure {
                    Toast.makeText(ctx, "未找到文件访问授权设置页", Toast.LENGTH_SHORT).show()
                }
            }
            else -> { // lsposed：打开 LSPosed 管理器，装不上则退回复检
                val managerIntent = listOf("org.lsposed.manager", "io.github.lsposed.manager")
                    .firstNotNullOfOrNull { pkg -> ctx.packageManager.getLaunchIntentForPackage(pkg) }
                if (managerIntent != null) {
                    Toast.makeText(ctx, "已打开 LSPosed 管理器，请确认模块已启用并勾选作用域", Toast.LENGTH_SHORT).show()
                    runCatching {
                        managerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(managerIntent)
                    }
                    status[key] = null to "已打开 LSPosed 管理器，切换回来后复检…"
                } else {
                    Toast.makeText(ctx, "未安装 LSPosed 管理器，改用 Root 复检", Toast.LENGTH_SHORT).show()
                    status[key] = null to "未检测到管理器，尝试 Root 复检…"
                }
                refreshKey(key, status["root"]?.first == true)
            }
        }
    }

    LaunchedEffect(detectTick) {
        running = true
        status.clear()
        items.forEach { (key, _, _) -> status[key] = null to "检测中…" }
        val root = withContext(Dispatchers.IO) { detectRootAccess() }
        status["root"] = root
        status["packages"] = withContext(Dispatchers.IO) { detectPackageVisibility(ctx) }
        status["dnd"] = withContext(Dispatchers.IO) { detectDndAccess(ctx) }
        status["storage"] = withContext(Dispatchers.IO) { detectStorageAccess() }
        status["lsposed"] = withContext(Dispatchers.IO) { detectLsposedScope(ctx, root.first == true) }
        running = false
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "perm_header") { SmallTitle("权限检测") }
        item(key = "perm_overview") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = if (running) {
                            "正在检测…（共 ${items.size} 项）"
                        } else {
                            val ok = items.count { status[it.first]?.first == true }
                            val no = items.count { status[it.first]?.first == false }
                            "共 ${items.size} 项：已满足 $ok 项 · 未满足 $no 项"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "绿色 ✓ 已满足 · 红色 ✗ 未满足（点击可尝试申请） · 黄色 … 检测中或需在 LSPosed 中人工确认",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item(key = "perm_refresh") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "重新检测",
                    summary = "重新扫描全部权限状态",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Scan,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = { detectTick++ },
                )
            }
        }
        items.forEach { (key, itemTitle, purpose) ->
            item(key = "perm_$key") {
                val st = status[key]
                val color = when (st?.first) {
                    true -> Color(0xFF07C160)
                    false -> Color(0xFFE94634)
                    null -> Color(0xFFFF9933)
                }
                val label = when (st?.first) {
                    true -> "✓ 已满足"
                    false -> "✗ 未满足 · 点击申请"
                    null -> "… 检测中"
                }
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .clickable(enabled = st?.first == false) { tryRequestPermission(key) },
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = itemTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = color,
                            )
                        }
                        Text(
                            text = purpose,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            text = st?.second ?: "检测中…",
                            fontSize = 12.sp,
                            color = color.copy(alpha = 0.9f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── 权限检测执行器（全部跑在 IO 线程）：返回 (状态 true=满足 / false=不满足 / null=待确认, 说明文字) ──

private fun detectRootAccess(): Pair<Boolean?, String> {
    var process: Process? = null
    return try {
        process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        process.outputStream.close()
        val finished = process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            false to "检测超时：首次使用需在弹窗中允许 Root，授权后点「重新检测」"
        } else {
            val out = process.inputStream.bufferedReader().readText().trim()
            if (process.exitValue() == 0 && out.contains("uid=0")) {
                true to out.lineSequence().first().trim()
            } else {
                val err = process.errorStream.bufferedReader().readText().trim()
                false to (err.ifBlank { "su 未授权或系统未 Root" }).lineSequence().first().take(70)
            }
        }
    } catch (e: Exception) {
        false to (e.message ?: "未找到 su（系统未 Root）")
    } finally {
        process?.destroy()
    }
}

private fun detectPackageVisibility(ctx: android.content.Context): Pair<Boolean?, String> {
    val declared = ctx.checkSelfPermission("android.permission.QUERY_ALL_PACKAGES") == PackageManager.PERMISSION_GRANTED
    val count = try {
        ctx.packageManager.getInstalledApplications(0).size
    } catch (e: Exception) {
        -1
    }
    return when {
        declared && count >= 50 -> true to "已声明并授予；当前可见 $count 个应用"
        declared -> true to "已声明并授予；可见 $count 个应用（偏少，可能受系统限制）"
        count in 0..49 -> false to "未授权；仅可见 $count 个应用，应用选择器与图标加载会不全"
        else -> true to "可见 $count 个应用"
    }
}


private fun detectDndAccess(ctx: android.content.Context): Pair<Boolean?, String> {
    return try {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            true to "已授予勿扰策略访问"
        } else {
            false to "未授予勿扰策略访问（本项仅供参考，有root无需授权）"
        }
    } catch (e: Exception) {
        null to "检测失败：${e.message}"
    }
}

private fun detectStorageAccess(): Pair<Boolean?, String> {
    return try {
        if (android.os.Environment.isExternalStorageManager()) {
            true to "已授予所有文件访问权限"
        } else {
            false to "未授予（可选：经系统相册 / 文件选择器选图时不需要）"
        }
    } catch (e: Exception) {
        false to "检测失败：${e.message}"
    }
}

private fun detectLsposedScope(ctx: android.content.Context, hasRoot: Boolean): Pair<Boolean?, String> {
    val managerInstalled = listOf("org.lsposed.manager", "io.github.lsposed.manager").any { pkg ->
        try {
            ctx.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    if (!hasRoot) {
        return if (managerInstalled) {
            null to "已装 LSPosed 管理器；读取作用域需 Root，通过 Root 检测后点「重新检测」"
        } else {
            false to "未检测到 LSPosed 管理器，且无 Root 无法校验作用域"
        }
    }
    return try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "grep -r -l io.github.qmryqmm.hyperopen /data/adb/lspd 2>/dev/null"))
        p.outputStream.close()
        val finished = p.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            p.destroyForcibly()
            null to "读取 LSPosed 数据超时，点「重新检测」重试"
        } else {
            val out = p.inputStream.bufferedReader().readText().trim()
            when {
                p.exitValue() == 0 && out.isNotEmpty() ->
                    true to "LSPosed 数据中找到本模块记录：${out.lineSequence().first().take(60)}"
                p.exitValue() == 0 ->
                    false to if (managerInstalled) "未找到本模块记录；请在 LSPosed 中启用模块并勾选作用域 com.android.systemui" else "未找到本模块记录，且未安装 LSPosed 管理器"
                else ->
                    false to "/data/adb/lspd 下未找到本模块（LSPosed 未安装或模块未启用）"
            }
        }
    } catch (e: Exception) {
        false to (e.message ?: "读取 LSPosed 数据失败")
    }
}
