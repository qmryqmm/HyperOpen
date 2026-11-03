package io.github.qmryqmm.hyperopen.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qmryqmm.hyperopen.ui.ThemeMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 外观与主题设置二级页面。
 * 提供深浅色模式（跟随系统/浅色/深色）、Monet 动态取色、悬浮式底栏切换。
 */
@Composable
fun ThemeSettingsPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    themeMode: Int, onThemeMode: (Int) -> Unit,
    monet: Boolean, onMonet: (Boolean) -> Unit,
    floatingNavBar: Boolean, onFloatingNavBar: (Boolean) -> Unit,
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
        // ── 深浅模式选择器 ──
        item(key = "theme_mode_title") { SmallTitle("深浅模式") }
        item(key = "theme_mode_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "跟随系统",
                    summary = "随系统深色模式设置自适应切换",
                    endActions = {
                        if (themeMode == ThemeMode.FOLLOW_SYSTEM) {
                            Text("✓", color = MiuixTheme.colorScheme.primary, fontSize = 16.sp)
                        }
                    },
                    onClick = { onThemeMode(ThemeMode.FOLLOW_SYSTEM) },
                )
                ArrowPreference(
                    title = "浅色模式",
                    summary = "始终使用明亮色彩",
                    endActions = {
                        if (themeMode == ThemeMode.LIGHT) {
                            Text("✓", color = MiuixTheme.colorScheme.primary, fontSize = 16.sp)
                        }
                    },
                    onClick = { onThemeMode(ThemeMode.LIGHT) },
                )
                ArrowPreference(
                    title = "深色模式",
                    summary = "始终使用暗色护眼色彩",
                    endActions = {
                        if (themeMode == ThemeMode.DARK) {
                            Text("✓", color = MiuixTheme.colorScheme.primary, fontSize = 16.sp)
                        }
                    },
                    onClick = { onThemeMode(ThemeMode.DARK) },
                )
            }
        }

        // ── Monet 动态取色 ──
        item(key = "monet_title") { SmallTitle("色彩风格") }
        item(key = "monet_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                SwitchPreference(
                    title = "启用 Monet 动态取色",
                    summary = "吸取系统壁纸主题色，与系统界面深度融合",
                    checked = monet,
                    onCheckedChange = onMonet,
                )
            }
        }

        // ── 外观效果 ──
        item(key = "appearance_title") { SmallTitle("导航栏样式") }
        item(key = "appearance_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                SwitchPreference(
                    title = "悬浮底栏",
                    summary = "胶囊悬浮底栏",
                    checked = floatingNavBar,
                    onCheckedChange = onFloatingNavBar,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
