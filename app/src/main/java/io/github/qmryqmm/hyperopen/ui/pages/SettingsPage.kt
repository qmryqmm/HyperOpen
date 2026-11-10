package io.github.qmryqmm.hyperopen.ui.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.qmryqmm.hyperopen.BuildConfig
import io.github.qmryqmm.hyperopen.safety.ModuleStatus
import io.github.qmryqmm.hyperopen.safety.SafetyStore
import io.github.qmryqmm.hyperopen.util.LauncherIcon
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 软件设置页面（Tab 2）。
 * 包含：
 *   - 外观与权限设置入口；
 *   - 重启系统界面 (SystemUI) 快捷按钮；
 *   - 桌面图标显隐控制；
 *   - 配置备份与恢复操作；
 *   - 安全模式状态机卡片与崩溃日志查看器；
 *   - 关于信息与开源社区链接。
 */

@Composable
fun SettingsPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    safetyTick: Int,
    onSafetyRefresh: () -> Unit,
    onOpenThemePage: () -> Unit,
    onOpenPermissionPage: () -> Unit,
    onResetDefaults: () -> Unit,
    onResetShortcuts: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── 主题设置 ──
        item(key = "theme_title") { SmallTitle("主题") }
        item(key = "theme_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "主题设置",
                    summary = "深浅模式 · Monet 动态取色 · 悬浮底栏",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Theme,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = onOpenThemePage,
                )
            }
        }

        // ── 权限检测 ──
        item(key = "permission_title") { SmallTitle("权限") }
        item(key = "permission_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "权限检测",
                    summary = "Root · 应用列表 · 振动 · 勿扰 · 存储",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Scan,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = onOpenPermissionPage,
                )
            }
        }

        // ── 模块运行状态与安全保护 ──
        item(key = "safety_title") { SmallTitle("模块运行状态与安全保护") }
        item(key = "safety_card") {
            SafetyCard(tick = safetyTick, onRequestRefresh = onSafetyRefresh)
        }

        item(key = "restart_systemui_card") {
            val ctx = LocalContext.current
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "\u91cd\u542f\u7cfb\u7edf\u754c\u9762",
                    summary = "\u91cd\u542f SystemUI \u4f7f\u914d\u7f6e\u7acb\u5373\u751f\u6548\uff08\u9700\u8981 Root \u6743\u9650\uff09",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Reset,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = { restartSystemUi(ctx) },
                )
            }
        }

        // ── 恢复默认参数与预设布局 ──
        item(key = "reset_defaults_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "恢复默认参数",
                    summary = "重置全部外观、几何、物理与等待时间参数为出厂默认值，但保留现有快捷项目",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Reset,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = onResetDefaults,
                )
                ArrowPreference(
                    title = "恢复预设快捷项目",
                    summary = "重置快捷项目列表为预设",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Reset,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = onResetShortcuts,
                )
            }
        }

        // ── 桌面图标 ──
        item(key = "launcher_icon_title") { SmallTitle("桌面图标") }
        item(key = "launcher_icon_card") {
            val ctx = LocalContext.current
            var iconHidden by remember { mutableStateOf(LauncherIcon.isHidden(ctx)) }
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                SwitchPreference(
                    title = "隐藏桌面图标",
                    summary = "从桌面与应用抽屉中移除本模块图标；隐藏后仍可从 LSPosed 管理器打开本页恢复",
                    checked = iconHidden,
                    onCheckedChange = { hide ->
                        if (LauncherIcon.setHidden(ctx, hide)) {
                            iconHidden = hide
                            Toast.makeText(
                                ctx,
                                if (hide) {
                                    "桌面图标已隐藏，可从 LSPosed 管理器重新打开本页"
                                } else {
                                    "桌面图标已恢复"
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            Toast.makeText(ctx, "修改桌面图标失败，请重试", Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }
        }

        // ── 配置备份 ──
        item(key = "config_backup_title") { SmallTitle("配置备份") }
        item(key = "config_backup_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "导出配置",
                    summary = "把当前全部参数与快捷方式写成 JSON 文件，便于备份或换机迁移",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Share,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = onExportConfig,
                )
                ArrowPreference(
                    title = "导入配置",
                    summary = "从 JSON 文件恢复参数与快捷方式；只覆盖文件里包含的项",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Import,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = onImportConfig,
                )
            }
        }

        // ── 关于 ──
        item(key = "about_title") { SmallTitle("关于") }
        item(key = "about_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                AboutLine("模块名称", "HyperOpen")
                AboutLine("作者", "青冥日月", url = "https://www.coolapk.com/u/3268816")
                AboutLine("Github", "HyperOpen", url = "https://github.com/qmryqmm/HyperOpen")
                AboutLine("当前版本", "v${io.github.qmryqmm.hyperopen.BuildConfig.VERSION_NAME}")
                AboutLine("功能简介", "指纹快捷面板自定义 · 图标与动效增强")
                AboutLine("使用方式", "指纹解锁时按压指纹传感器唤出快捷面板")
                AboutLine("生效条件", "LSPosed 激活模块并勾选 SystemUI 作用域，重启 SystemUI")
                AboutLine("UI 框架", "Miuix Compose 0.9.4 ")
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** root重启系统界面 (the system relaunches it automatically); toast on failure. */
fun restartSystemUi(ctx: android.content.Context) {
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    fun toast(msg: String) = handler.post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    Thread {
        try {
            val p = ProcessBuilder("su", "-c", "pkill -f com.android.systemui || killall com.android.systemui")
                .redirectErrorStream(true)
                .start()
            if (p.waitFor() != 0) toast("\u91cd\u542f\u5931\u8d25\uff1a\u8bf7\u786e\u8ba4\u5df2\u6388\u4e88 Root \u6743\u9650")
        } catch (t: Throwable) {
            toast("\u91cd\u542f\u5931\u8d25\uff1a\u672a\u83b7\u53d6\u5230 Root \u6743\u9650")
        }
    }.start()
}
@Composable
fun AboutLine(label: String, value: String, url: String? = null) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (url != null) {
                    Modifier.clickable {
                        try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Throwable) {
                            Toast.makeText(ctx, "\u65e0\u6cd5\u6253\u5f00\u94fe\u63a5", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
            modifier = Modifier.size(width = 84.dp, height = 20.dp),
        )
        Text(
            value,
            color = if (url != null) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun android.graphics.Bitmap.toComposeImageBitmap(): androidx.compose.ui.graphics.ImageBitmap = this.asImageBitmap()

// ─────────────────────────────────────────────────────────────────────
// 模块运行状态与安全模式卡片
// ─────────────────────────────────────────────────────────────────────

@Composable
fun SafetyCard(tick: Int, onRequestRefresh: () -> Unit) {
    val ctx = LocalContext.current

    // 进入页面时主动触发一次 SystemUI 探活广播，促使 SystemUI 刷新 PID 与时间戳
    LaunchedEffect(tick) {
        ModuleStatus.pingSystemUi(ctx)
    }

    val live = remember(tick) { ModuleStatus.getLiveStatus(ctx) }

    val lspBadgeText = when {
        live.lsposedActive -> "LSPosed: 已激活运行"
        live.lsposedConfigEnabled -> "LSPosed: 框架已开启 (需重启生效)"
        else -> "LSPosed: 未启用"
    }
    val lspBadgeColor = when {
        live.lsposedActive -> Color(0xFF07C160)
        live.lsposedConfigEnabled -> Color(0xFFFAAD14)
        else -> Color(0xFFE94634)
    }
    val lspBadgeBg = when {
        live.lsposedActive -> Color(0x1F07C160)
        live.lsposedConfigEnabled -> Color(0x1FFAAD14)
        else -> Color(0x1FE94634)
    }

    val hookBadgeText = when {
        live.safeMode -> "Hook: 安全模式保护 (已停用)"
        !live.hookEnabled -> "Hook: 总开关已关闭"
        live.hookInjected -> "SystemUI: 已注入运行 (PID ${live.systemUiPid})"
        !live.lsposedActive && !live.lsposedConfigEnabled -> "SystemUI: 未注入 (模块未激活)"
        else -> "SystemUI: 未注入 (等待生效/未勾选作用域)"
    }
    val hookBadgeColor = when {
        live.safeMode -> Color(0xFFE94634)
        !live.hookEnabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        live.hookInjected -> Color(0xFF07C160)
        !live.lsposedActive && !live.lsposedConfigEnabled -> Color(0xFFE94634)
        else -> Color(0xFFFAAD14)
    }
    val hookBadgeBg = when {
        live.safeMode -> Color(0x1FE94634)
        !live.hookEnabled -> Color(0x15888888)
        live.hookInjected -> Color(0x1F07C160)
        !live.lsposedActive && !live.lsposedConfigEnabled -> Color(0x1FE94634)
        else -> Color(0x1FFAAD14)
    }

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // 实时状态指示胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(lspBadgeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(lspBadgeText, color = lspBadgeColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(hookBadgeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(hookBadgeText, color = hookBadgeColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(10.dp))

            // 状态异常排查指引
            if (!live.lsposedActive && live.lsposedConfigEnabled) {
                Text(
                    "⚡ 检测到 LSPosed 管理器中已开启本模块，由于 Android 虚拟机的注入机制，需要冷重启本软件才能完成 Hook 加载。请点击下方「重启本软件」即可立即生效。",
                    color = Color(0xFFFAAD14),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            } else if (!live.lsposedConfigEnabled) {
                Text(
                    "⚠️ 未检测到 LSPosed 开启本模块：请在 LSPosed 管理器中启用 HyperOpen 并勾选作用域「系统界面 (com.android.systemui)」，然后重启本软件和系统界面。",
                    color = Color(0xFFE94634),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            } else if (!live.hookInjected && live.hookEnabled && !live.safeMode) {
                Text(
                    "⚠️ 模块已激活但 SystemUI 尚未成功挂载：请确认在 LSPosed 中已勾选「系统界面」作用域，并点击下方「重启系统界面」重新挂载。",
                    color = Color(0xFFFAAD14),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            InfoLine("目标进程：com.android.systemui${if (live.systemUiPid > 0) " (PID: ${live.systemUiPid})" else ""}")
            InfoLine(
                if (live.lastInjectedTime > 0L) {
                    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(live.lastInjectedTime))
                    "最近注入确认：$fmt"
                } else "最近注入确认：尚未注入",
            )
            InfoLine("连续崩溃保护：${live.crashCount} / ${live.crashThreshold}")
            InfoLine("历史累计崩溃：${live.totalCrash} 次")
            InfoLine("上次会话存活确认：${if (live.sessionHealthy) "已确认" else "未确认（疑似崩溃）"}")

            Spacer(Modifier.height(8.dp))
            Text(
                "崩溃记录",
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 13.sp,
            )
            Text(
                if (live.crashLog.isBlank()) "暂无崩溃记录。" else live.crashLog,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE),
            )

            Spacer(Modifier.height(12.dp))

            // 操作按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = "刷新状态",
                    onClick = {
                        ModuleStatus.pingSystemUi(ctx)
                        onRequestRefresh()
                        Toast.makeText(ctx, "已刷新模块状态", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "重启软件",
                    onClick = {
                        ModuleStatus.restartSelf(ctx)
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            if (live.safeMode || live.crashCount > 0 || live.crashLog.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (live.safeMode) {
                        TextButton(
                            text = "退出安全模式",
                            onClick = {
                                SafetyStore.exitSafeMode(ctx)
                                Toast.makeText(ctx, "已退出安全模式，重启 SystemUI 后 Hook 恢复生效", Toast.LENGTH_LONG).show()
                                onRequestRefresh()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(
                        text = "清空崩溃记录",
                        onClick = {
                            SafetyStore.clearCrash(ctx)
                            Toast.makeText(ctx, "崩溃记录已清空", Toast.LENGTH_SHORT).show()
                            onRequestRefresh()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SwitchPreference(
                title = "启用指纹快捷方式增强 (Hook 总开关)",
                checked = live.hookEnabled && !live.safeMode,
                onCheckedChange = { checked ->
                    SafetyStore.setHookEnabled(ctx, checked)
                    onRequestRefresh()
                    Toast.makeText(
                        ctx,
                        if (checked) "Hook 已启用，重启 SystemUI 后生效" else "Hook 已停用，重启 SystemUI 后生效",
                        Toast.LENGTH_LONG,
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "当 Hook 导致 SystemUI 在 5 分钟内连续崩溃 2 次时，模块会自动进入安全模式并停用全部 Hook，SystemUI 恢复原生指纹界面。修复配置后点「退出安全模式」即可重新启用。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
fun InfoLine(text: String) {
    Text(
        text,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 2.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────
// 二级页：主题设置（深浅模式 / Monet / 悬浮底栏）
// ─────────────────────────────────────────────────────────────────────
