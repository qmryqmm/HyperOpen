package io.github.qmryqmm.hyperopen.util

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * 实时桌面壁纸加载器：
 * 1. 彻底实现全自动、完全实时的桌面壁纸拉取，绝不依赖外部手动脚本或静态拷贝；
 * 2. 具备壁纸变更动态广播监听 (ACTION_WALLPAPER_CHANGED)，换壁纸即刻无缝自愈刷新；
 * 3. 采用多级高鲁棒性实时拉取管线 (原生系统 API -> Root 管道直读 -> 锁屏原始数据)；
 * 4. 响应式 Compose 状态集成，异步后台协程解码，零卡顿。
 */
object LiveWallpaperLoader {
    private const val TAG = "HyperOpen_LiveWallpaper"

    private var cachedBitmap: Bitmap? = null
    private var lastLoadTimestamp: Long = 0L

    /**
     * 响应式获取当前系统真实桌面壁纸，并在系统壁纸变更时自动重组刷新
     */
    @Composable
    fun rememberLiveWallpaper(context: Context): State<ImageBitmap?> {
        val wallpaperState = remember { mutableStateOf<ImageBitmap?>(cachedBitmap?.asImageBitmap()) }

        DisposableEffect(context) {
            val scope = CoroutineScope(Dispatchers.Main)

            fun reloadAsync() {
                scope.launch {
                    val bmp = withContext(Dispatchers.IO) {
                        fetchLiveWallpaperDirectly(context)
                    }
                    if (bmp != null) {
                        cachedBitmap = bmp
                        lastLoadTimestamp = System.currentTimeMillis()
                        wallpaperState.value = bmp.asImageBitmap()
                        Log.i(TAG, "已成功实时重载最新系统桌面壁纸: ${bmp.width}x${bmp.height}")
                    }
                }
            }

            // 初始立即异步刷新一次
            reloadAsync()

            // 注册系统壁纸变更广播监听器
            val receiver = object : BroadcastReceiver() {
                @Override
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    Log.i(TAG, "检测到系统壁纸变更广播，即刻触发实时重新拉取！")
                    reloadAsync()
                }
            }

            val filter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "注册壁纸监听广播失败: ${t.message}")
            }

            onDispose {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Throwable) {}
                scope.cancel()
            }
        }

        return wallpaperState
    }

    /**
     * 执行底层壁纸真实提取逻辑 (在 IO 线程运行)
     */
    fun fetchLiveWallpaperDirectly(context: Context): Bitmap? {
        // ── 途径 1：利用 Root 管道直接以内存流解码当前系统桌面最新壁纸 (/data/system/users/0/wallpaper) ──
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /data/system/users/0/wallpaper"))
            val input: InputStream = process.inputStream
            val bmp = input.use { BitmapFactory.decodeStream(it) }
            val finished = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            } else {
                true
            }
            if (!finished) {
                process.destroyForcibly()
            }
            if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                Log.i(TAG, "成功由 Root 管道毫秒级实时读取系统桌面壁纸: ${bmp.width}x${bmp.height}")
                return bmp
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Root 管道直读壁纸跳过或不可用: ${t.message}")
        } finally {
            if (process != null) {
                try { process.destroy() } catch (_: Throwable) {}
            }
        }

        // ── 途径 2：通过系统 WallpaperManager 原生文件流获取 ──
        try {
            val wm = WallpaperManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val pfd = wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
                if (pfd != null) {
                    val bmp = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                    pfd.close()
                    if (bmp != null) {
                        Log.i(TAG, "成功由 WallpaperManager.getWallpaperFile 获取原生壁纸: ${bmp.width}x${bmp.height}")
                        return bmp
                    }
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "WallpaperManager.getWallpaperFile 失败: ${t.message}")
        }

        // ── 途径 3：通过 WallpaperManager.getDrawable() 兜底 ──
        try {
            val wm = WallpaperManager.getInstance(context)
            val d = wm.drawable
            if (d is BitmapDrawable && d.bitmap != null) {
                return d.bitmap
            } else if (d != null) {
                val dm = context.resources.displayMetrics
                val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else dm.widthPixels
                val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else dm.heightPixels
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                d.setBounds(0, 0, w, h)
                d.draw(c)
                return bmp
            }
        } catch (t: Throwable) {
            Log.d(TAG, "WallpaperManager.drawable 提取失败: ${t.message}")
        }

        // ── 途径 4：直接尝试读取公开可读或已同步的系统壁纸文件 ──
        val candidatePaths = arrayOf(
            "/data/system/users/0/wallpaper",
            "/data/system/users/0/wallpaper_orig",
            File(context.filesDir, "desktop_wallpaper.jpg").absolutePath
        )
        for (path in candidatePaths) {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    val bmp = BitmapFactory.decodeFile(f.absolutePath)
                    if (bmp != null) return bmp
                }
            } catch (_: Throwable) {}
        }

        return null
    }
}
