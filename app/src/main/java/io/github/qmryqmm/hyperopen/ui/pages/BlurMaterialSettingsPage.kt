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
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 全局模糊材质（Gaussian Blur）调节页面。
 * 一个统一滑块控制所有组件的模糊半径与压暗程度，与柔光玻璃页面互斥。
 * 仅当 materialStyle == MATERIAL_GAUSSIAN_BLUR 时入口可见。
 */
@Composable
fun BlurMaterialSettingsPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    globalBlurRadius: Float, onGlobalBlurRadius: (Float) -> Unit,
    globalDimAlpha: Float, onGlobalDimAlpha: (Float) -> Unit,
    onResetToDefaults: () -> Unit,
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
        // ── 全局模糊参数 ──
        item(key = "blur_title") { SmallTitle("全局模糊参数") }
        item(key = "blur_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                SliderPreference(
                    title = "模糊半径",
                    summary = "统一控制图标气泡、图标显示区域与顶部提示胶囊的背景模糊强度",
                    valueText = if (globalBlurRadius.toInt() == 0) "已关闭模糊" else "${globalBlurRadius.toInt()} dp",
                    value = globalBlurRadius,
                    valueRange = 0f..50f,
                    onValueChange = onGlobalBlurRadius,
                )
                SliderPreference(
                    title = "压暗程度",
                    summary = "统一控制所有组件背景的黑色蒙层透明度，与模糊独立叠加",
                    valueText = "${(globalDimAlpha * 100).toInt()}%",
                    value = globalDimAlpha * 100f,
                    valueRange = 0f..100f,
                    onValueChange = { onGlobalDimAlpha(it / 100f) },
                )
            }
        }

        // ── 快捷重置按钮 ──
        item(key = "reset_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                TextButton(
                    text = "重置为默认值",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = onResetToDefaults,
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
