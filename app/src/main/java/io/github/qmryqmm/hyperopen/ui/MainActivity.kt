package io.github.qmryqmm.hyperopen.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import io.github.qmryqmm.hyperopen.util.DePrefs

/**
 * 模块主设置入口 Activity。
 * 承担职责：
 *   1. Direct Boot 开机加密锁定期向设备保护存储 (DE) 的一次性数据迁移；
 *   2. 加载模块持久化配置并在 Compose 运行时提供环境；
 *   3. 挂载返回键拦截分发接口 [backPressConsumer]（供子组件优雅拦截未保存编辑与全屏预览返回）。
 */
class MainActivity : ComponentActivity() {

    internal var backPressConsumer: (() -> Boolean)? = null

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (backPressConsumer?.invoke() == true) {
            return
        }
        super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 隐藏 API 豁免（L 前缀 = 全部类）：让 App 进程能反射 ViewRootImpl.createBackgroundBlurDrawable，
        // 与 SystemUI 侧的 SurfaceFlinger 硬件毛玻璃完全同源。必须在任何反射之前调用。
        try {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("L")
        } catch (_: Throwable) {}
        // Direct Boot：入口先补一次 CE→DE 数据迁移（幂等；已迁移时为进程内短路）
        try {
            DePrefs.migrateCeToDe(this, HyperOpenConfig.PREF_NAME)
        } catch (_: Throwable) {}
        val config = HyperOpenConfig.loadFromApp(this)
        setContent {
            HyperOpenApp(activity = this, initialConfig = config)
        }
    }
}
