package io.github.qmryqmm.hyperopen.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 桌面图标显隐控制。
 *
 * 承载 LAUNCHER 入口的是 manifest 里的 activity-alias（LauncherEntry），
 * 所以「隐藏图标」只是把该 alias 置为 disabled：MainActivity 本身仍带
 * `de.robv.android.xposed.category.MODULE_SETTINGS` 入口，依然能从 LSPosed
 * 管理器打开设置页把图标恢复回来，不会把自己锁在外面。
 *
 * 状态以系统的组件状态为唯一真相源（不落 SharedPreferences）：
 * 组件启用状态由 PackageManagerService 持久化，清 App 数据也不会丢，
 * 用 adb 改过之后回到设置页显示的也是桌面实际表现。
 */
object LauncherIcon {

    /** 与 AndroidManifest.xml 中 activity-alias 的 name 保持一致。 */
    private const val ALIAS_CLASS = "io.github.qmryqmm.hyperopen.LauncherEntry"

    private fun alias(ctx: Context): ComponentName = ComponentName(ctx.packageName, ALIAS_CLASS)

    fun isHidden(ctx: Context): Boolean = try {
        val state = ctx.packageManager.getComponentEnabledSetting(alias(ctx))
        state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
    } catch (t: Throwable) {
        false
    }

    /**
     * 切换桌面图标显隐。
     *
     * `DONT_KILL_APP` 是必需的：改组件状态默认会连带杀掉本进程，设置页会当场闪退。
     *
     * @return true 表示系统已接受新的组件状态；false 表示写入失败，调用方应回滚 UI。
     */
    fun setHidden(ctx: Context, hidden: Boolean): Boolean = try {
        val state = if (hidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        ctx.packageManager.setComponentEnabledSetting(
            alias(ctx),
            state,
            PackageManager.DONT_KILL_APP,
        )
        true
    } catch (t: Throwable) {
        false
    }
}
