package io.github.qmryqmm.hyperopen.ui

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 全局实时配置 CompositionLocal，供卡片图标与预览组件实时响应参数变化。
 */
val LocalHyperOpenConfig = androidx.compose.runtime.staticCompositionLocalOf<io.github.qmryqmm.hyperopen.config.HyperOpenConfig?> { null }

/**
 * 主题模式常量（与 HyperOpenConfig.THEME_* 对齐）
 */
object ThemeMode {
    const val FOLLOW_SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
}

/**
 * 遵循 miuix 官方模板构建的 Theme 管理器：
 * 使用官方 ThemeController(ColorSchemeMode.*)，支持 System / Light / Dark / MonetSystem / MonetLight / MonetDark。
 */
@Composable
fun HyperOpenTheme(
    themeMode: Int,
    monet: Boolean,
    content: @Composable () -> Unit,
) {
    val mode = remember(themeMode, monet) {
        when (themeMode) {
            ThemeMode.LIGHT -> if (monet) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
            ThemeMode.DARK -> if (monet) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
            else -> if (monet) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
        }
    }

    val controller = remember(mode) {
        ThemeController(mode)
    }

    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> systemDark
    }

    MiuixTheme(
        controller = controller,
    ) {
        val colors = MiuixTheme.colorScheme
        ApplySystemBars(isDark, colors.background)
        content()
    }
}

/** 系统栏外观同步：底色 + 图标深浅（API 23+/30+ 两段兼容）。 */
@Composable
private fun ApplySystemBars(dark: Boolean, backgroundColor: Color) {
    val view = LocalView.current
    val ctx = LocalContext.current
    SideEffect {
        val window = (ctx as? Activity)?.window ?: return@SideEffect
        val bg = backgroundColor.toArgb()
        @Suppress("DEPRECATION")
        window.statusBarColor = bg
        @Suppress("DEPRECATION")
        window.navigationBarColor = bg
        if (Build.VERSION.SDK_INT >= 30) {
            val insets = window.insetsController ?: return@SideEffect
            val appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            insets.setSystemBarsAppearance(
                if (dark) 0 else appearance,
                appearance,
            )
        } else {
            val navFlag = if (Build.VERSION.SDK_INT >= 26) {
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else 0
            val lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or navFlag
            @Suppress("DEPRECATION")
            view.systemUiVisibility = if (dark) {
                view.systemUiVisibility and lightFlags.inv()
            } else {
                view.systemUiVisibility or lightFlags
            }
        }
    }
}
