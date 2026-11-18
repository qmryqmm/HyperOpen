package io.github.qmryqmm.hyperopen.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 柔光玻璃（Soft Light Glass）专属效果与系统级微调二级页面。
 * 对齐小米澎湃 OS（HyperOS）桌面与系统界面原生光学渲染规范，
 * 支持在桌面柔光、系统界面拟真与全自由自定义调参之间无缝切换。
 */
@Composable
fun SoftGlassSettingsPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    glassPreset: Int, onGlassPreset: (Int) -> Unit,
    glassRefractIor: Float, onGlassRefractIor: (Float) -> Unit,
    glassThickness: Float, onGlassThickness: (Float) -> Unit,
    glassSpecular: Float, onGlassSpecular: (Float) -> Unit,
    glassMatteAlpha: Float, onGlassMatteAlpha: (Float) -> Unit,
    glassBorderLight: Float, onGlassBorderLight: (Float) -> Unit,
    glassBackgroundSaturation: Float, onGlassBackgroundSaturation: (Float) -> Unit,
    glassBackgroundBrightness: Float, onGlassBackgroundBrightness: (Float) -> Unit,
    glassBackgroundBurn: Float, onGlassBackgroundBurn: (Float) -> Unit,
    glassMatteWhiteMix: Float, onGlassMatteWhiteMix: (Float) -> Unit,
    glassMatteColorMix: Float, onGlassMatteColorMix: (Float) -> Unit,
    glassMatteColorPower: Float, onGlassMatteColorPower: (Float) -> Unit,
    glassEdgeRange: Float, onGlassEdgeRange: (Float) -> Unit,
    glassEdgePower: Float, onGlassEdgePower: (Float) -> Unit,
    glassReflectOffset: Float, onGlassReflectOffset: (Float) -> Unit,
    onResetToDefaultPreset: () -> Unit,
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
        // ── 光学风格预设 ──
        item(key = "glass_preset_title") { SmallTitle("光学风格预设") }
        item(key = "glass_preset_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "系统默认值 (推荐)",
                    summary = "对齐澎湃OS系统界面 Glass_Common_Small_Thin 原生光学规范与参数",
                    endActions = {
                        if (glassPreset == HyperOpenConfig.GLASS_PRESET_DEFAULT) {
                            Text("✓", color = MiuixTheme.colorScheme.primary, fontSize = 16.sp)
                        }
                    },
                    onClick = { onGlassPreset(HyperOpenConfig.GLASS_PRESET_DEFAULT) },
                )
                ArrowPreference(
                    title = "自定义参数微调",
                    summary = "解锁全部底层光学滑块，自由调节折射率、边缘厚度与漫射度",
                    endActions = {
                        if (glassPreset == HyperOpenConfig.GLASS_PRESET_CUSTOM) {
                            Text("✓", color = MiuixTheme.colorScheme.primary, fontSize = 16.sp)
                        }
                    },
                    onClick = { onGlassPreset(HyperOpenConfig.GLASS_PRESET_CUSTOM) },
                )
            }
        }

        // ── 自定义光学参数微调（仅在自定义模式下展开） ──
        item(key = "custom_params_title") {
            AnimatedVisibility(
                visible = glassPreset == HyperOpenConfig.GLASS_PRESET_CUSTOM,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                SmallTitle("底层光学参数微调")
            }
        }
        item(key = "custom_params_card") {
            AnimatedVisibility(
                visible = glassPreset == HyperOpenConfig.GLASS_PRESET_CUSTOM,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    SliderPreference(
                        title = "透镜折射率 (IOR)",
                        summary = "1.00 为平整无畸变漫射玻璃；数值越高折射聚光越明显",
                        valueText = "%.2f".format(glassRefractIor),
                        value = glassRefractIor,
                        valueRange = 1.0f..2.0f,
                        onValueChange = onGlassRefractIor,
                    )
                    SliderPreference(
                        title = "边缘光学厚度 (Thickness)",
                        summary = "控制四周菲涅尔光圈的聚集宽度与次表面厚度",
                        valueText = "%.2f".format(glassThickness),
                        value = glassThickness,
                        valueRange = 0.5f..4.0f,
                        onValueChange = onGlassThickness,
                    )
                    SliderPreference(
                        title = "表面漫光反射强度 (Specular)",
                        summary = "顶部自然天光反射的强弱；柔光质感建议保持在 0.40 ~ 0.80 之间",
                        valueText = "%.2f".format(glassSpecular),
                        value = glassSpecular,
                        valueRange = 0.0f..2.0f,
                        onValueChange = onGlassSpecular,
                    )
                    SliderPreference(
                        title = "雾面微晶底色浓度 (Matte Alpha)",
                        summary = "柔光玻璃内部哑光微晶的漫反射基底不透明度",
                        valueText = "${(glassMatteAlpha * 100).toInt()}%",
                        value = glassMatteAlpha * 100f,
                        valueRange = 5f..40f,
                        onValueChange = { onGlassMatteAlpha(it / 100f) },
                    )
                    SliderPreference(
                        title = "边缘微光描边亮度 (Border Light)",
                        summary = "外圈低反差微光的亮度；桌面柔光风格建议保持低对比度",
                        valueText = "${(glassBorderLight * 100).toInt()}%",
                        value = glassBorderLight * 100f,
                        valueRange = 10f..100f,
                        onValueChange = { onGlassBorderLight(it / 100f) },
                    )
                    SliderPreference(
                        title = "背景采样饱和度 (Background Saturation)",
                        summary = "调整透过玻璃看到的底部图标与背景色彩浓度，不改变模糊半径",
                        valueText = "%.2f".format(glassBackgroundSaturation),
                        value = glassBackgroundSaturation,
                        valueRange = 0f..3f,
                        onValueChange = onGlassBackgroundSaturation,
                    )
                    SliderPreference(
                        title = "背景采样亮度 (Background Brightness)",
                        summary = "压暗或提亮玻璃后的背景采样画面，正值提亮、负值压暗",
                        valueText = "%.2f".format(glassBackgroundBrightness),
                        value = glassBackgroundBrightness,
                        valueRange = -1f..1f,
                        onValueChange = onGlassBackgroundBrightness,
                    )
                    SliderPreference(
                        title = "背景暗部烧灼 (Background Burn)",
                        summary = "增强背景暗部沉淀感；数值越高，阴影区域越深",
                        valueText = "${(glassBackgroundBurn * 100).toInt()}%",
                        value = glassBackgroundBurn * 100f,
                        valueRange = 0f..100f,
                        onValueChange = { onGlassBackgroundBurn(it / 100f) },
                    )
                    SliderPreference(
                        title = "雾面白色混合 (Matte White Mix)",
                        summary = "向玻璃雾面底色混入白色，控制奶白感与通透度",
                        valueText = "${(glassMatteWhiteMix * 100).toInt()}%",
                        value = glassMatteWhiteMix * 100f,
                        valueRange = 0f..100f,
                        onValueChange = { onGlassMatteWhiteMix(it / 100f) },
                    )
                    SliderPreference(
                        title = "雾面颜色混合 (Matte Color Mix)",
                        summary = "控制雾面底色参与材质合成的比例",
                        valueText = "${(glassMatteColorMix * 100).toInt()}%",
                        value = glassMatteColorMix * 100f,
                        valueRange = 0f..100f,
                        onValueChange = { onGlassMatteColorMix(it / 100f) },
                    )
                    SliderPreference(
                        title = "雾面颜色曲线 (Matte Color Power)",
                        summary = "调整雾面颜色的明暗过渡曲线，数值越高过渡越集中",
                        valueText = "%.2f".format(glassMatteColorPower),
                        value = glassMatteColorPower,
                        valueRange = 0.25f..4f,
                        onValueChange = onGlassMatteColorPower,
                    )
                    SliderPreference(
                        title = "边缘光作用范围 (Edge Range)",
                        summary = "控制菲涅尔边缘光由外圈向内部延伸的宽度",
                        valueText = "${glassEdgeRange.toInt()}",
                        value = glassEdgeRange,
                        valueRange = 0f..100f,
                        onValueChange = onGlassEdgeRange,
                    )
                    SliderPreference(
                        title = "边缘光过渡曲线 (Edge Power)",
                        summary = "调整边缘光衰减速度，数值越高光圈越集中于最外侧",
                        valueText = "%.2f".format(glassEdgePower),
                        value = glassEdgePower,
                        valueRange = 1f..10f,
                        onValueChange = onGlassEdgePower,
                    )
                    SliderPreference(
                        title = "表面反射偏移 (Reflect Offset)",
                        summary = "移动玻璃表面环境反射纹理，改变高光与折射反光位置",
                        valueText = "${glassReflectOffset.toInt()}",
                        value = glassReflectOffset,
                        valueRange = 0f..1200f,
                        onValueChange = onGlassReflectOffset,
                    )
                }
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
                    text = "重置为系统默认值参数",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = onResetToDefaultPreset,
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
