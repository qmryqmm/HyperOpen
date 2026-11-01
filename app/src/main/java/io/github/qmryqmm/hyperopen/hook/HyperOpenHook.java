package io.github.qmryqmm.hyperopen.hook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.github.qmryqmm.hyperopen.config.HyperOpenConfig;
import io.github.qmryqmm.hyperopen.model.ShortcutItem;
import io.github.qmryqmm.hyperopen.view.CustomHyperOpenContainer;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HyperOpenHook {
    private static final String TAG = "HyperOpen_Hook";
    private static final String CONTAINER_TAG = "hyperos_custom_hyperopen_container";

    /** 宿主 View 上的附加字段：本模块额外加到窗口上的 flag（用于复位时精确还原，不误删原生 flag） */
    private static final String FIELD_ADDED_FLAGS = "hyperOpenAddedWindowFlags";
    /** 宿主 View 上的附加字段：attach 状态监听器（卸载时摘除） */
    private static final String FIELD_ATTACH_LISTENER = "hyperOpenAttachListener";

    /** MiuiGxzwQuickOpenView$1 (mShowRunnable) 上的附加字段：标记「这是展开面板的 Runnable」 */
    private static final String FIELD_SHOW_RUNNABLE = "hyperOpenShowRunnable";

    /** 本模块在展开时为窗口追加的 flag */
    private static final int OUR_WINDOW_FLAGS =
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_BLUR_BEHIND;

    /**
     * 全部已安装 Hook 的卸载句柄。
     *
     * 安全模式在运行期被触发时，必须能立刻把已经装上去的 Hook 全部拆掉 ——
     * 只靠「下次启动不装」是不够的：用户此刻的 SystemUI 仍在崩溃，等待重启
     * 意味着还要再崩若干次。因此这里保存每一个 Unhook，供 {@link #uninstall()} 使用。
     */
    private static final List<XC_MethodHook.Unhook> HANDLES =
        Collections.synchronizedList(new ArrayList<XC_MethodHook.Unhook>());

    /**
     * Hook 是否处于生效状态。
     *
     * 所有回调入口都先查这个 volatile 标志：安全模式一旦触发，标志立刻置 false，
     * 即使某个回调正在执行、或卸载句柄尚未全部处理完，也不会再有任何本模块逻辑
     * 作用于 SystemUI。这是一次内存读取，不产生跨进程开销。
     */
    private static volatile boolean active = false;

    /** 已被注入 CustomHyperOpenContainer 的宿主 View（弱引用，避免拖住 SystemUI 视图树） */
    private static final List<WeakReference<FrameLayout>> HOSTS =
        Collections.synchronizedList(new ArrayList<WeakReference<FrameLayout>>());

    /**
     * 进程级广播接收器（配置热重载 / 息屏复位）。
     * 只注册一次：旧实现每构造一个 QuickOpenView 就注册一个接收器，既泄漏又会重复刷新。
     */
    private static Context receiverContext;
    private static BroadcastReceiver configReceiver;
    private static BroadcastReceiver screenReceiver;

    /** 缓存 MiuiGxzwUtils Class 引用，避免每次展开反射查找 */
    private static volatile Class<?> cachedGxzwUtilsClass;

    /**
     * 缓存「系统快捷面板弹出 Runnable」(MiuiGxzwQuickOpenView$1, $r8$classId=0) 的 Class 引用。
     *
     * 系统机制（逆向自系统界面_17.03.260226.r.apk 的 MiuiGxzwManager#dismissGxzwView）：
     *   指纹解锁成功后松手，若满足 fod_quick_open 开关等条件，会执行
     *   {@code mHandler.postDelayed(mShowRunnable, 500L | 1000L)} 再唤出快捷面板。
     *   本模块 Hook android.os.Handler#postDelayed，动态替换该 Runnable 的延迟，
     *   从而实现「指纹解锁后进入快捷方式的等待时间」可调。
     */
    private static volatile Class<?> cachedShowRunnableClass;

    /**
     * 缓存 BlurUtils#applyBlur 的跨版本兼容解析结果。
     * 该方法签名随系统界面版本变化（2 参/3 参），见 {@link #applyBlurCompat}。
     */
    private static volatile Method cachedApplyBlurMethod;

    /** dismiss 签名解析缓存（跨版本：dismiss(I)V / dismiss()V），见 {@link #dismissCompat}。 */
    private static volatile Method cachedDismissMethod;

    /** BlurUtils 实例缓存（跨版本取链解析成功后写入），见 {@link #resolveBlurUtils}。 */
    private static volatile Object cachedBlurUtilsInstance;

    /** 缓存宿主 SystemUI 的 versionName，仅用于跨版本兼容诊断日志 */
    private static volatile String cachedHostVersionName;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        final ClassLoader cl = lpparam.classLoader;

        // 每次部署前清空历史句柄，避免重复部署时句柄表里混入上一轮的失效项
        HANDLES.clear();
        active = true;

        // 配置异步刷新完成后（主线程回调），把新配置推给所有未处于展示态的容器。
        // 展示中的容器不热替换配置，避免手势进行中几何参数突变；下次展开会自动取最新缓存。
        HyperOpenConfig.setOnConfigUpdatedListener(new HyperOpenConfig.OnConfigUpdatedListener() {
            @Override
            public void onConfigUpdated(HyperOpenConfig config) {
                if (!active) return;
                for (FrameLayout host : snapshotHosts()) {
                    try {
                        CustomHyperOpenContainer c = getContainer(host);
                        if (c != null && c.getVisibility() != View.VISIBLE) {
                            c.updateConfig(config);
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "推送新配置到容器失败", t);
                    }
                }
            }
        });

        try {
            final Class<?> quickOpenViewClass = XposedHelpers.findClass(
                "com.miui.keyguard.biometrics.fod.MiuiGxzwQuickOpenView", cl
            );

            // 1. Hook 构造函数：注入 CustomHyperOpenContainer 并挂载到 view 上
            record(XposedBridge.hookAllConstructors(quickOpenViewClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!active) return;
                    FrameLayout view = (FrameLayout) param.thisObject;
                    Context context = view.getContext();

                    // 仅读进程内缓存（首轮以 XSharedPreferences 本地文件兜底），不做跨进程调用；
                    // 最新配置由后台线程异步拉取后再推送进来。
                    HyperOpenConfig config = HyperOpenConfig.getCached();
                    final CustomHyperOpenContainer container = new CustomHyperOpenContainer(context, config, cl);
                    container.setTag(CONTAINER_TAG);
                    container.setVisibility(View.GONE);

                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    );
                    view.addView(container, lp);
                    XposedHelpers.setAdditionalInstanceField(view, "customContainer", container);
                    rememberHost(view);

                    // 标记系统 mShowRunnable，供 Handler#postDelayed 拦截器精准识别展开任务
                    markShowRunnable(view);

                    // 宿主窗口被移除（息屏、锁屏重建、指纹图层销毁等）时立刻复位，
                    // 绝不让 VISIBLE 容器 / 追加的窗口 flag 残留到下一次 attach。
                    armDetachReset(view);

                    // 注册配置实时更新与息屏复位广播监听（进程级，只注册一次）
                    ensureProcessReceivers(context);

                    // 后台异步预取最新配置
                    HyperOpenConfig.refreshAsync(context);

                    Log.i(TAG, "已成功向 MiuiGxzwQuickOpenView 注入 CustomHyperOpenContainer！");
                }
            }));

            // 2. Hook startShowQuickOpenItemAnimation：完全接管展开流程，阻止系统默认5项图标与扇形黑底
            record(XposedHelpers.findAndHookMethod(quickOpenViewClass, "startShowQuickOpenItemAnimation", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!active) return;
                    FrameLayout view = (FrameLayout) param.thisObject;

                    // 完全阻止系统原生 startShowQuickOpenItemAnimation 执行 (避免创建原生5项View、动画和扇形黑底)
                    param.setResult(null);

                    // 展开路径只读进程内缓存：绝不在手势线程上同步调用 ContentProvider。
                    // 模块进程常被系统冻结，同步 call() 需先解冻，秒级阻塞会直接导致 SystemUI 输入 ANR。
                    // 配置变更由广播实时推送，这里仅做节流的后台兜底刷新。
                    HyperOpenConfig latestConfig = HyperOpenConfig.getCached();
                    HyperOpenConfig.refreshAsyncThrottled(view.getContext());

                    // 获取或创建 CustomHyperOpenContainer
                    CustomHyperOpenContainer container = (CustomHyperOpenContainer)
                        XposedHelpers.getAdditionalInstanceField(view, "customContainer");

                    // 若容器已处于展示状态，则无需重复初始化（也不重复下发模糊 / 窗口事务）
                    if (container != null && container.getVisibility() == View.VISIBLE) {
                        return;
                    }

                    // 全屏高斯模糊与全屏压暗独立渲染：模糊受 fullscreenBlurMaskAlpha 调控，压暗受 fullscreenDimAlpha 调控
                    // 两者分别容错：applyBlur 签名随系统界面版本变化（见 applyBlurCompat），
                    // 绝不能因模糊解析/调用失败而连带丢掉全屏压暗。
                    try {
                        Object blurUtils = resolveBlurUtils(view);
                        if (blurUtils != null) {
                            Object vri = XposedHelpers.callMethod(view, "getViewRootImpl");
                            if (latestConfig.fullscreenBlurMaskAlpha <= 0.001f) {
                                // 用户关闭全屏模糊：彻底移除全屏高斯模糊
                                if (vri != null) {
                                    applyBlurCompat(blurUtils, vri, 0);
                                }
                            } else {
                                // 用户开启全屏模糊：根据比例动态设置全屏高斯模糊半径
                                float blurRatio = Math.max(0.0f, Math.min(1.0f, latestConfig.fullscreenBlurMaskAlpha));
                                float blurRadius = (float) XposedHelpers.callMethod(blurUtils, "blurRadiusOfRatio", blurRatio);
                                if (vri != null) {
                                    applyBlurCompat(blurUtils, vri, (int) blurRadius);
                                }
                            }
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "设置全屏背景模糊失败(SystemUI " + hostVersionName(view.getContext()) + ")", t);
                    }

                    // 全屏压暗背景（0% ~ 100% 独立控制，与预览界面 Color.Black.copy(alpha = fullscreenDimAlpha) 1:1 严格对齐）
                    try {
                        float dimRatio = Math.max(0.0f, Math.min(1.0f, latestConfig.fullscreenDimAlpha));
                        if (dimRatio <= 0.001f) {
                            view.setBackground(null);
                        } else {
                            int dimAlpha = Math.max(0, Math.min(255, Math.round(dimRatio * 255f)));
                            view.setBackgroundColor(android.graphics.Color.argb(dimAlpha, 0, 0, 0));
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "设置全屏压暗背景失败(SystemUI " + hostVersionName(view.getContext()) + ")", t);
                    }

                    // 允许触摸穿透并更新 WindowManager 布局标记 (开启 FLAG_BLUR_BEHIND 赋能局部模糊)
                    // 与原生 MiuiGxzwQuickOpenView.startShowQuickOpenItemAnimation 严格对齐：
                    // 若当前尚未 attach 到窗口，则置 mPendingUpdateLp = true，交由原生 onAttachedToWindow() 统一提交。
                    try {
                        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) XposedHelpers.getObjectField(view, "mLayoutParams");
                        if (lp != null) {
                            int missing = OUR_WINDOW_FLAGS & ~lp.flags;
                            if (missing != 0) {
                                lp.flags |= missing;
                                Object prev = XposedHelpers.getAdditionalInstanceField(view, FIELD_ADDED_FLAGS);
                                int added = (prev instanceof Integer ? (Integer) prev : 0) | missing;
                                XposedHelpers.setAdditionalInstanceField(view, FIELD_ADDED_FLAGS, added);
                                if (view.isAttachedToWindow()) {
                                    WindowManager wm = (WindowManager) XposedHelpers.getObjectField(view, "mWindowManager");
                                    if (wm != null) {
                                        wm.updateViewLayout(view, lp);
                                    }
                                } else {
                                    try {
                                        XposedHelpers.setBooleanField(view, "mPendingUpdateLp", true);
                                    } catch (Throwable ignored) {}
                                }
                            }
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "更新 LayoutParams 失败", t);
                    }

                    // 清理可能残余的原生 item 数据
                    try {
                        XposedHelpers.callMethod(view, "cleanQuickOpenItemList");
                    } catch (Throwable ignored) {}

                    // 隐藏原生的所有其它子 View (标题、副标题、教学提示等)
                    for (int i = 0; i < view.getChildCount(); i++) {
                        View child = view.getChildAt(i);
                        if (!CONTAINER_TAG.equals(child.getTag()) && child.getVisibility() != View.GONE) {
                            child.setVisibility(View.GONE);
                        }
                    }

                    if (container == null) {
                        container = new CustomHyperOpenContainer(view.getContext(), latestConfig, cl);
                        container.setTag(CONTAINER_TAG);
                        view.addView(container, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                        ));
                        XposedHelpers.setAdditionalInstanceField(view, "customContainer", container);
                        rememberHost(view);
                        armDetachReset(view);
                    }

                    // 每次展开前，热更新容器配置！
                    container.updateConfig(latestConfig);

                    // 获取 FOD 精准指纹中心坐标：
                    // 优先直接读取宿主在 initQuickViewParams 中已算好的 mFingerRect，避免每次展开调用 caculateGxzwIconSize
                    float fodX = -1f, fodY = -1f;
                    try {
                        Object fingerRectObj = XposedHelpers.getObjectField(view, "mFingerRect");
                        if (fingerRectObj instanceof RectF) {
                            RectF rect = (RectF) fingerRectObj;
                            if (rect.width() > 0 && rect.height() > 0) {
                                fodX = rect.centerX();
                                fodY = rect.centerY();
                            }
                        }
                    } catch (Throwable ignored) {}

                    if (fodX <= 0f || fodY <= 0f) {
                        try {
                            Class<?> utilsClass = cachedGxzwUtilsClass;
                            if (utilsClass == null) {
                                utilsClass = XposedHelpers.findClass(
                                    "com.miui.keyguard.biometrics.fod.MiuiGxzwUtils", cl
                                );
                                cachedGxzwUtilsClass = utilsClass;
                            }
                            int iconX = XposedHelpers.getStaticIntField(utilsClass, "GXZW_ICON_X");
                            int iconY = XposedHelpers.getStaticIntField(utilsClass, "GXZW_ICON_Y");
                            int iconW = XposedHelpers.getStaticIntField(utilsClass, "GXZW_ICON_WIDTH");
                            int iconH = XposedHelpers.getStaticIntField(utilsClass, "GXZW_ICON_HEIGHT");
                            if (iconW <= 0 || iconH <= 0) {
                                XposedHelpers.callStaticMethod(utilsClass, "caculateGxzwIconSize", view.getContext());
                                iconX = XposedHelpers.getStaticIntField(utilsClass, "GXZW_ICON_X");
                                iconY = XposedHelpers.getStaticIntField(utilsClass, "GXZW_ICON_Y");
                                iconW = XposedHelpers.getStaticIntField(utilsClass, "GXZW_ICON_WIDTH");
                                iconH = XposedHelpers.getStaticIntField(utilsClass, "GXZW_ICON_HEIGHT");
                            }
                            fodX = iconX + iconW / 2.0f;
                            fodY = iconY + iconH / 2.0f;
                        } catch (Throwable t) {
                            Point displaySize = getDisplaySize(view.getContext());
                            fodX = displaySize.x / 2.0f;
                            fodY = displaySize.y * 0.795f;
                        }
                    }

                    // 异步上报实机真实 FOD 中心坐标至模块配置，供 App 预览 1:1 绝对对齐
                    HyperOpenConfig.reportRealFodCenterAsync(view.getContext(), fodX, fodY);

                    container.setVisibility(View.VISIBLE);
                    container.bringToFront();
                    container.setupAndExpand(fodX, fodY);
                    Log.i(TAG, "CustomHyperOpenContainer 展开成功，指纹中心: (" + fodX + ", " + fodY + ")");

                    // 在指纹快捷面板完全展开展示的这一瞬间，后台轻量唤醒/解冻模块 App 进程（纯粹 Provider 探活，零前台弹窗）
                    // 若用户随后滑动选中了依赖 Root 的快捷方式，模块已被解冻处于活跃态，am start 命令可秒级响应
                    warmupModuleProcessAsync(view.getContext());
                }
            }));

            // 3. Hook dismiss：退出并复位自定义容器。
            // 跨版本签名：17.03.x / 20250121.x 为 dismiss(I)V；20240808.0 / 20230316.0 为无参 dismiss()V。
            // 先装带参签名，NoSuchMethodError 时降级装无参签名，after 语义一致。
            XC_MethodHook dismissResetHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!active) return;
                    resetHostState((FrameLayout) param.thisObject, "dismiss");
                }
            };
            try {
                record(XposedHelpers.findAndHookMethod(quickOpenViewClass, "dismiss", int.class, dismissResetHook));
            } catch (NoSuchMethodError e) {
                Log.i(TAG, "未找到 dismiss(I)V，降级安装无参 dismiss()V（20240808.0 / 20230316.0）");
                record(XposedHelpers.findAndHookMethod(quickOpenViewClass, "dismiss", dismissResetHook));
            }

            Log.i(TAG, "已成功安装 MiuiGxzwQuickOpenView 核心生命周期接管 Hook！");

        } catch (Throwable t) {
            Log.e(TAG, "Hook MiuiGxzwQuickOpenView 异常", t);
        }

        // 4. Hook MiuiGxzwIconView$2.onPointerEvent(MotionEvent) 拦截手势事件并驱动蜂窝鱼眼
        try {
            Class<?> iconView2Class = XposedHelpers.findClass(
                "com.miui.keyguard.biometrics.fod.MiuiGxzwIconView$2", cl
            );
            record(XposedHelpers.findAndHookMethod(iconView2Class, "onPointerEvent", MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!active) return;
                    try {
                        Object listener = param.thisObject;
                        final Object iconView = XposedHelpers.getObjectField(listener, "this$0");
                        if (iconView == null) return;

                        final Object quickOpenView = XposedHelpers.getObjectField(iconView, "mMiuiGxzwQuickOpenView");
                        if (quickOpenView == null) return;

                        final FrameLayout frameLayout = (FrameLayout) quickOpenView;
                        final CustomHyperOpenContainer container = (CustomHyperOpenContainer)
                            XposedHelpers.getAdditionalInstanceField(frameLayout, "customContainer");

                        // 只有在自定义快捷面板处于展示态时，才拦截后续手势
                        if (container == null || container.getVisibility() != View.VISIBLE) return;

                        MotionEvent event = (MotionEvent) param.args[0];
                        int action = event.getActionMasked();
                        final float rawX = event.getRawX();
                        final float rawY = event.getRawY();

                        if (action == MotionEvent.ACTION_MOVE) {
                            // 折叠 120Hz/240Hz 高频触摸采样：每帧最多在主线程执行一次鱼眼计算，零 Runnable 分配
                            container.scheduleFingerMove(rawX, rawY);
                            param.setResult(null); // 拦截原生滑动处理，避免原生逻辑干扰
                        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                            final boolean isUp = (action == MotionEvent.ACTION_UP);
                            // 对齐原生 MiuiGxzwIconView$2：松手或取消时必须将 isCatchDownEvent 复位为 false
                            try {
                                XposedHelpers.setBooleanField(iconView, "isCatchDownEvent", false);
                            } catch (Throwable ignored) {}

                            Runnable finishTask = new Runnable() {
                                @Override
                                public void run() {
                                    // 对齐原生 Lambda4：通知 MiuiGxzwIconView 手指已抬起，确保传感器/动画状态完整收尾
                                    try {
                                        XposedHelpers.callMethod(iconView, "onTouchUp", true);
                                    } catch (Throwable ignored) {}

                                    ShortcutItem selected = null;
                                    if (isUp) {
                                        selected = container.consumeSelectedAndReset();
                                    } else {
                                        container.resetBubbles();
                                    }

                                    // 优先在主线程立刻关闭并移除悬浮面板窗口，随后在后台 IPC 线程异步发起应用启动：
                                    // 即使 system_server 因其他模块阻塞 startActivity，也绝不卡住 SystemUI 手势收尾与窗口销毁
                                    try {
                                        dismissCompat(quickOpenView);
                                    } catch (Throwable t) {
                                        Log.e(TAG, "调用 dismiss 异常", t);
                                    }

                                    if (selected != null) {
                                        selected.executeAsync(frameLayout.getContext());
                                    }
                                }
                            };
                            if (Looper.myLooper() == Looper.getMainLooper()) {
                                finishTask.run();
                            } else {
                                frameLayout.post(finishTask);
                            }
                            param.setResult(null);
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "onPointerEvent hook error", t);
                    }
                }
            }));
            Log.i(TAG, "已成功安装 MiuiGxzwIconView$2.onPointerEvent 触摸拦截 Hook！");
        } catch (Throwable t) {
            Log.e(TAG, "Hook MiuiGxzwIconView$2 失败", t);
        }

        // 5. Hook android.os.Handler#postDelayed：
        //    拦截系统在指纹解锁后调度 mShowRunnable 唤出快捷面板的延迟，替换为用户设定的等待阈值
        try {
            record(XposedHelpers.findAndHookMethod(
                android.os.Handler.class, "postDelayed",
                Runnable.class, long.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!active) return;
                        Runnable r = (Runnable) param.args[0];
                        long delay = (Long) param.args[1];
                        if (r == null || delay <= 0L) return;

                        // 跳过 Android 内部类（如 Invalidation/Elastic 等系统低频延迟任务），
                        // 只拦截 MiuiGxzwQuickOpenView$1 的展开 Runnable。
                        Class<?> showRunnableClass = cachedShowRunnableClass;
                        if (showRunnableClass == null) {
                            try {
                                showRunnableClass = XposedHelpers.findClass(
                                    "com.miui.keyguard.biometrics.fod.MiuiGxzwQuickOpenView$1", cl
                                );
                                cachedShowRunnableClass = showRunnableClass;
                            } catch (Throwable ignore) {
                                return;
                            }
                        }
                        if (!showRunnableClass.isInstance(r)) {
                            return;
                        }

                        // 该内类被 R8 复用为多种 Runnable，只有 classId=0 的实例才是「展开面板」任务：
                        // classId=1 是写入 fod_auth_fingerprint，classId=2 是设置 closeView 无障碍描述，
                        // 两者都不能被替换延迟。故还须读取本模块注入到内类上的标记字段做精准判定。
                        if (!isShowRunnableMarked(r)) {
                            return;
                        }

                        HyperOpenConfig cfg = HyperOpenConfig.getCached();
                        int userDelay = cfg != null ? Math.max(0, cfg.showDelayMs) : 500;
                        if (userDelay == 500) {
                            // 500ms = 系统默认：不干预，保持系统原生 delay（普通 500ms / 无障碍 1000ms）
                            Log.i(TAG, "快捷面板展开：系统默认，不干预原生延迟 " + delay + "ms");
                            return;
                        }
                        Log.i(TAG, "快捷面板展开：等待阈值 " + delay + "ms → " + userDelay + "ms");
                        param.args[1] = (long) userDelay;
                    }
                }
            ));
            Log.i(TAG, "已成功安装 Handler#postDelayed 指纹快捷等待阈值拦截 Hook！");
        } catch (Throwable t) {
            Log.e(TAG, "Hook Handler#postDelayed 失败", t);
        }

        // 6. Hook MiuiGxzwManager.isUnlockByGxzw() 与 KeyguardUpdateMonitorInjector.isFingerprintUnlock()：
        //    人脸识别成功停留在锁屏时，用户按指纹图标会先进入桌面（触发 onKeyguardHide -> dismissGxzwView）；
        //    若用户继续保持按住指纹，放行解锁判定为 true，驱动系统安排 mShowRunnable 从而在桌面上唤出快捷面板。
        try {
            Class<?> gxzwManagerClass = XposedHelpers.findClass(
                "com.miui.keyguard.biometrics.fod.MiuiGxzwManager", cl
            );
            record(XposedHelpers.findAndHookMethod(
                gxzwManagerClass, "isUnlockByGxzw", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!active) return;
                        HyperOpenConfig cfg = HyperOpenConfig.getCached();
                        if (cfg == null || !cfg.faceUnlockQuickOpen) return;
                        if (isFaceUnlockPressing(param.thisObject)) {
                            param.setResult(Boolean.TRUE);
                        }
                    }
                }
            ));
            Log.i(TAG, "已成功安装 MiuiGxzwManager.isUnlockByGxzw 人脸解锁长按放行 Hook！");
        } catch (Throwable t) {
            Log.e(TAG, "Hook MiuiGxzwManager.isUnlockByGxzw 失败", t);
        }

        try {
            Class<?> stubClass = XposedHelpers.findClass(
                "com.android.keyguard.stub.MiuiKeyguardUpdateMonitorInjectorStub", cl
            );
            record(XposedHelpers.findAndHookMethod(
                stubClass, "isFingerprintUnlock", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!active) return;
                        HyperOpenConfig cfg = HyperOpenConfig.getCached();
                        if (cfg == null || !cfg.faceUnlockQuickOpen) return;
                        if (isFaceUnlockPressingGlobal(cl)) {
                            param.setResult(Boolean.TRUE);
                        }
                    }
                }
            ));
            Log.i(TAG, "已成功安装 MiuiKeyguardUpdateMonitorInjectorStub.isFingerprintUnlock 人脸解锁长按放行 Hook！");
        } catch (Throwable t) {
            Log.w(TAG, "Hook MiuiKeyguardUpdateMonitorInjectorStub.isFingerprintUnlock 失败 (可能为旧版 MIUI)", t);
        }

        // 跨版本兼容：直接 Hook 底层实体类 KeyguardUpdateMonitorInjector.isFingerprintUnlock()，
        // 彻底覆盖 20240808.0 / 20250121.0 等未采用新 Stub 体系或使用 KeyguardStub 包装的旧版本。
        try {
            Class<?> injectorClass = XposedHelpers.findClass(
                "com.android.keyguard.injector.KeyguardUpdateMonitorInjector", cl
            );
            record(XposedHelpers.findAndHookMethod(
                injectorClass, "isFingerprintUnlock", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!active) return;
                        HyperOpenConfig cfg = HyperOpenConfig.getCached();
                        if (cfg == null || !cfg.faceUnlockQuickOpen) return;
                        if (isFaceUnlockPressingGlobal(cl)) {
                            param.setResult(Boolean.TRUE);
                        }
                    }
                }
            ));
            Log.i(TAG, "已成功安装 KeyguardUpdateMonitorInjector.isFingerprintUnlock 跨版本底层兼容 Hook！");
        } catch (Throwable t) {
            Log.w(TAG, "Hook KeyguardUpdateMonitorInjector.isFingerprintUnlock 失败", t);
        }

        // 7. Hook MiuiGxzwIconView$1.run() (mGotoUnlockRunnable)：
        //    在人脸识别成功停留在锁屏时，用户按指纹图标进入桌面时添加震动，
        //    震动反馈与正常指纹解锁一致，且跟随模块震动参数（模式、强度、时长与总开关）。
        try {
            Class<?> gotoUnlockClass = XposedHelpers.findClass(
                "com.miui.keyguard.biometrics.fod.MiuiGxzwIconView$1", cl
            );
            record(XposedHelpers.findAndHookMethod(
                gotoUnlockClass, "run", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!active) return;
                        HyperOpenConfig cfg = HyperOpenConfig.getCached();
                        if (cfg == null || !cfg.hapticEnabled) return;
                        try {
                            Object iconView = XposedHelpers.getObjectField(param.thisObject, "this$0");
                            if (iconView instanceof View) {
                                Context ctx = ((View) iconView).getContext();
                                android.os.Vibrator v = (android.os.Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                                CustomHyperOpenContainer.performConfiguredHaptic(ctx, v, cfg);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            ));
            Log.i(TAG, "已成功安装 MiuiGxzwIconView$1.run 人脸解锁按指纹进桌面触觉震动 Hook！");
        } catch (Throwable t) {
            Log.w(TAG, "Hook MiuiGxzwIconView$1 失败", t);
        }
    }

    /**
     * 在 MiuiGxzwQuickOpenView 构造时，给其 mShowRunnable（$r8$classId=0 的 MiuiGxzwQuickOpenView$1）
     * 实例打上附加字段标记，供 postDelayed 拦截器精准识别。这样无需依赖被 R8 重排的字段布局，
     * 且能从根本上区分同被 R8 复用的 classId=1 / classId=2 两个长得一模一样的 Runnable。
     */
    private static void markShowRunnable(FrameLayout quickOpenView) {
        if (quickOpenView == null) return;
        try {
            Object runnable = XposedHelpers.getObjectField(quickOpenView, "mShowRunnable");
            if (runnable instanceof Runnable) {
                XposedHelpers.setAdditionalInstanceField(runnable, FIELD_SHOW_RUNNABLE, Boolean.TRUE);
            }
        } catch (Throwable t) {
            Log.w(TAG, "标记系统展开 Runnable 失败", t);
        }
    }

    private static boolean isShowRunnableMarked(Runnable r) {
        try {
            Object mark = XposedHelpers.getAdditionalInstanceField(r, FIELD_SHOW_RUNNABLE);
            return Boolean.TRUE.equals(mark);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 判断当前是否处于「人脸识别成功停留在锁屏，且用户正按住指纹图标」的状态。
     * 当且仅当手指按下且处于人脸解锁状态时放行，确保：
     * 1. 短按轻触（< 等待时间即松手）：onTouchUp 已置 mTouchDown=false，原生直接进桌面；
     * 2. 持续长按（手指不松开）：dismissGxzwView 判定为有效解锁，顺利触发 postDelayed(mShowRunnable) 展开快捷面板。
     */
    private static boolean isFaceUnlockPressing(Object gxzwManager) {
        if (gxzwManager == null) return false;
        try {
            Object iconView = XposedHelpers.getObjectField(gxzwManager, "mMiuiGxzwIconView");
            if (iconView == null) return false;
            boolean touchDown = XposedHelpers.getBooleanField(iconView, "mTouchDown");
            boolean catchDown = XposedHelpers.getBooleanField(iconView, "isCatchDownEvent");
            if (!touchDown || !catchDown) return false;

            // 1. 尝试从 gxzwManager 字段获取 injector
            try {
                Object injector = XposedHelpers.getObjectField(gxzwManager, "mKeyguardUpdateMonitorInjector");
                if (injector != null) {
                    Boolean shouldListen = (Boolean) XposedHelpers.callMethod(injector, "shouldListenForFingerprintWhenUnlocked");
                    if (Boolean.TRUE.equals(shouldListen)) return true;
                }
            } catch (Throwable ignored) {}

            // 2. 尝试从 InterfacesImplManager 获取单例 (新版)
            try {
                ClassLoader cl = gxzwManager.getClass().getClassLoader();
                Class<?> stubClass = XposedHelpers.findClass(
                    "com.android.keyguard.stub.MiuiKeyguardUpdateMonitorInjectorStub", cl
                );
                Class<?> ifaceMgr = XposedHelpers.findClass(
                    "com.miui.systemui.interfacesmanager.InterfacesImplManager", cl
                );
                Object stub = XposedHelpers.callStaticMethod(ifaceMgr, "getImpl", stubClass);
                if (stub != null) {
                    Boolean shouldListen = (Boolean) XposedHelpers.callMethod(stub, "shouldListenForFingerprintWhenUnlocked");
                    if (Boolean.TRUE.equals(shouldListen)) return true;
                }
            } catch (Throwable ignored) {}

            // 3. 跨版本回退：尝试通过 MiuiFaceUnlockManager 直接检查是否处于停留锁屏状态 (三代通用)
            try {
                ClassLoader cl = gxzwManager.getClass().getClassLoader();
                Class<?> faceMgrClass = XposedHelpers.findClass(
                    "com.miui.keyguard.biometrics.faceunlock.MiuiFaceUnlockManager", cl
                );
                Class<?> ifaceMgr = XposedHelpers.findClass(
                    "com.miui.systemui.interfacesmanager.InterfacesImplManager", cl
                );
                Object faceMgr = XposedHelpers.callStaticMethod(ifaceMgr, "getImpl", faceMgrClass);
                if (faceMgr != null) {
                    Boolean stayScreen = (Boolean) XposedHelpers.callMethod(faceMgr, "isFaceUnlockSuccessAndStayScreen");
                    if (Boolean.TRUE.equals(stayScreen)) return true;
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Log.w(TAG, "检查人脸解锁按下状态异常", t);
        }
        return false;
    }

    private static boolean isFaceUnlockPressingGlobal(ClassLoader cl) {
        try {
            Class<?> factoryClass = XposedHelpers.findClass(
                "com.miui.keyguard.biometrics.fod.MiuiFingerPrintFactory", cl
            );
            Object mgr = XposedHelpers.callStaticMethod(factoryClass, "getFingerPrintManager");
            return isFaceUnlockPressing(mgr);
        } catch (Throwable t) {
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 跨版本兼容层：BlurUtils#applyBlur 签名适配
    // ──────────────────────────────────────────────────────────────

    /**
     * 跨版本兼容调用 {@code BlurUtils#applyBlur}。
     *
     * 该方法签名随系统界面版本变化（三份系统界面 APK 反编译核对）：
     * <ul>
     *   <li>17.03.x（17.03.260226.r 等新版）：{@code applyBlur(ViewRootImpl, int)} —— 2 参；</li>
     *   <li>20250121.x 及更早旧版：{@code applyBlur(ViewRootImpl, int, boolean)} —— 3 参，
     *       第 3 参直通 {@code SyncRtSurfaceTransactionApplier$SurfaceParams$Builder#withOpaque(Z)}，
     *       旧版原生调用惯例恒传 false。</li>
     * </ul>
     *
     * 按「方法名 + 参数个数」探测并缓存解析结果，不依赖版本号硬编码判断，对未来新版本同样健壮；
     * 旧版 3 参签名自动补 {@code withOpaque(false)}，与原生行为严格对齐。
     * 解析/调用失败向上抛，由各调用点既有的 try/catch 记录，不改变原有容错语义。
     */
    private static void applyBlurCompat(Object blurUtils, Object vri, int radius) throws Throwable {
        Method m = cachedApplyBlurMethod;
        if (m == null) {
            m = resolveApplyBlur(blurUtils.getClass());
            cachedApplyBlurMethod = m;
        }
        try {
            if (m.getParameterTypes().length == 3) {
                // 旧版签名：第 3 参 withOpaque 恒传 false，对齐原生调用惯例
                m.invoke(blurUtils, vri, radius, Boolean.FALSE);
            } else {
                m.invoke(blurUtils, vri, radius);
            }
        } catch (InvocationTargetException e) {
            // 还原目标方法自身抛出的异常，保持与旧实现 callMethod 一致的异常语义
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    /** 解析 BlurUtils#applyBlur：优先新版 2 参签名，其次旧版 3 参签名；均不存在则抛 NoSuchMethodException。 */
    private static Method resolveApplyBlur(Class<?> blurUtilsClass) throws NoSuchMethodException {
        Method twoArg = null;
        Method threeArg = null;
        for (Method m : blurUtilsClass.getMethods()) {
            if (!"applyBlur".equals(m.getName())) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 2) twoArg = m;
            else if (params.length == 3) threeArg = m;
        }
        Method resolved = twoArg != null ? twoArg : threeArg;
        if (resolved == null) {
            throw new NoSuchMethodException("BlurUtils#applyBlur(ViewRootImpl,int[,boolean]) 不存在");
        }
        resolved.setAccessible(true);
        Log.i(TAG, "applyBlur 兼容签名已解析: " + resolved
            + (resolved.getParameterTypes().length == 3 ? "（旧版 3 参，自动补 withOpaque=false）" : "（新版 2 参）"));
        return resolved;
    }

    /** 宿主 SystemUI 的 versionName（仅用于跨版本兼容诊断日志；取不到时返回 unknown）。 */
    private static String hostVersionName(Context context) {
        String v = cachedHostVersionName;
        if (v != null) return v;
        try {
            v = context.getPackageManager().getPackageInfo("com.android.systemui", 0).versionName;
        } catch (Throwable ignored) {
            v = "unknown";
        }
        cachedHostVersionName = v;
        return v;
    }

    // ──────────────────────────────────────────────────────────────
    // 跨版本兼容层：dismiss 双签名 + BlurUtils 取链（20240808.0 / 20230316.0 适配）
    // ──────────────────────────────────────────────────────────────

    /**
     * 调用宿主 dismiss：新版 {@code dismiss(I)V} 优先，无参 {@code dismiss()}V 降级。
     * 20240808.0 / 20230316.0 只有无参版本；带参调用会抛 NoSuchMethodError，此处捕获后重试无参。
     * 解析结果缓存到 {@link #cachedDismissMethod}，失败不缓存，交由调用点容错。
     */
    private static void dismissCompat(Object host) throws Throwable {
        Method m = cachedDismissMethod;
        if (m == null) {
            m = resolveDismiss(host.getClass());
            cachedDismissMethod = m;
        }
        try {
            if (m.getParameterTypes().length == 1) {
                m.invoke(host, 0);
            } else {
                m.invoke(host);
            }
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    /** 解析 dismiss：优先 dismiss(I)V，其次 dismiss()V；均不存在则抛 NoSuchMethodException。 */
    private static Method resolveDismiss(Class<?> hostClass) throws NoSuchMethodException {
        Method withInt = null;
        Method noArg = null;
        for (Method m : hostClass.getMethods()) {
            if (!"dismiss".equals(m.getName())) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 1 && params[0] == int.class) withInt = m;
            else if (params.length == 0) noArg = m;
        }
        Method resolved = withInt != null ? withInt : noArg;
        if (resolved == null) {
            throw new NoSuchMethodException("MiuiGxzwQuickOpenView#dismiss(I)V / dismiss()V 不存在");
        }
        resolved.setAccessible(true);
        Log.i(TAG, "dismiss 兼容签名已解析: " + resolved);
        return resolved;
    }

    /**
     * 跨版本解析宿主的 BlurUtils 实例。三代取链殊途同归，终点都是
     * {@code com.android.systemui.statusbar.BlurUtils} 单例：
     * <ul>
     *   <li>HyperOS 族（17.03.x / 20250121.x / 20250121.0.8）：
     *       {@code mBlurUtils(BlurUtilsDelegate) → blurUtils(BlurUtils)} 直达；</li>
     *   <li>20240808.0：{@code mBlurUtils(CommonStub$registerBlur$1) → $sysUIProvider(SysUIProvider)}
     *       → {@code mBlurUtils(dagger.Lazy) → get()}；</li>
     *   <li>20230316.0：{@code mBlurUtils(MiuiStub$6) → this$0(MiuiStub) → mSysUIProvider(SysUIProvider)}
     *       → {@code mBlurUtils(dagger.Lazy) → get()}。</li>
     * </ul>
     * SysUIProvider 字段名两代不同（$sysUIProvider / mSysUIProvider），按字段类型
     * {@code miui.stub.MiuiStub$SysUIProvider} 查找而非按名字；直查失败再走 this$0 一跳。
     * 解析成功的实例缓存；失败返回 null（BlurUtils 为进程单例，缓存安全）。
     */
    private static Object resolveBlurUtils(Object view) {
        Object cached = cachedBlurUtilsInstance;
        if (cached != null) return cached;
        try {
            Object holder = XposedHelpers.getObjectField(view, "mBlurUtils");
            if (holder == null) return null;

            // 1) HyperOS 直达：holder.blurUtils
            Object direct = tryGetField(holder, "blurUtils");
            if (direct != null) {
                cachedBlurUtilsInstance = direct;
                Log.i(TAG, "BlurUtils 取链已解析(HyperOS 直达): " + direct.getClass().getName());
                return direct;
            }

            // 2) MIUI stub 链：holder[.this$0] → SysUIProvider → mBlurUtils(Lazy).get()
            Object result = tryResolveViaSysUIProvider(holder);
            if (result == null) {
                Object outer = tryGetField(holder, "this$0");
                if (outer != null) result = tryResolveViaSysUIProvider(outer);
            }
            if (result != null) {
                cachedBlurUtilsInstance = result;
                Log.i(TAG, "BlurUtils 取链已解析(MIUI stub 链): " + result.getClass().getName());
            }
            return result;
        } catch (Throwable t) {
            Log.w(TAG, "解析 BlurUtils 取链失败", t);
            return null;
        }
    }

    /** 在给定对象上按类型找 MiuiStub$SysUIProvider 字段，再取 mBlurUtils(Lazy).get()。 */
    private static Object tryResolveViaSysUIProvider(Object holder) {
        try {
            for (Field f : holder.getClass().getDeclaredFields()) {
                if (!"miui.stub.MiuiStub$SysUIProvider".equals(f.getType().getName())) continue;
                f.setAccessible(true);
                Object provider = f.get(holder);
                if (provider == null) return null;
                Object lazy = tryGetField(provider, "mBlurUtils");
                if (lazy == null) return null;
                return XposedHelpers.callMethod(lazy, "get");
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object tryGetField(Object obj, String name) {
        try {
            return XposedHelpers.getObjectField(obj, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 状态复位：dismiss / 息屏 / 窗口移除 统一走这里
    // ──────────────────────────────────────────────────────────────

    private static CustomHyperOpenContainer getContainer(FrameLayout host) {
        Object o = XposedHelpers.getAdditionalInstanceField(host, "customContainer");
        return o instanceof CustomHyperOpenContainer ? (CustomHyperOpenContainer) o : null;
    }

    /**
     * 把宿主恢复到「从未展开过」的状态：容器隐藏复位、全屏模糊清零、
     * 本模块追加的窗口 flag 精确还原。操作幂等，可重复调用。
     * 必须在主线程调用。
     */
    private static void resetHostState(FrameLayout view, String why) {
        if (view == null) return;

        CustomHyperOpenContainer container = getContainer(view);
        if (container != null) {
            try {
                container.resetBubbles();
            } catch (Throwable t) {
                Log.w(TAG, "复位气泡失败(" + why + ")", t);
            }
            if (container.getVisibility() != View.GONE) {
                container.setVisibility(View.GONE);
            }
        }

        // 原生 removeQuickView() 在移除窗口时已经调用过 applyBlur(vri, 0)；
        // 仅当宿主仍附着在窗口上时才补充清零全屏模糊，避免冗余跨线程/渲染调用。
        if (view.isAttachedToWindow()) {
            try {
                view.setBackground(null);
            } catch (Throwable ignored) {}
            try {
                Object blurUtils = resolveBlurUtils(view);
                if (blurUtils != null) {
                    Object vri = XposedHelpers.callMethod(view, "getViewRootImpl");
                    if (vri != null) {
                        applyBlurCompat(blurUtils, vri, 0);
                    }
                }
            } catch (Throwable ignored) {}
        }

        restoreWindowFlags(view, why);
    }

    /**
     * 还原本模块在展开时追加到窗口 LayoutParams 上的 flag（FLAG_BLUR_BEHIND / FLAG_NOT_TOUCH_MODAL）。
     *
     * 只在窗口仍附着且可见时才下发 updateViewLayout；窗口已移除/不可见时仅修改
     * LayoutParams 对象本身，下次 addView / relayout 自然使用干净的 flag，
     * 不额外给 system_server 增加窗口事务。
     */
    private static void restoreWindowFlags(FrameLayout view, String why) {
        try {
            Object saved = XposedHelpers.getAdditionalInstanceField(view, FIELD_ADDED_FLAGS);
            if (!(saved instanceof Integer)) return;
            XposedHelpers.removeAdditionalInstanceField(view, FIELD_ADDED_FLAGS);
            int added = (Integer) saved;
            if (added == 0) return;

            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) XposedHelpers.getObjectField(view, "mLayoutParams");
            if (lp == null || (lp.flags & added) == 0) return;
            lp.flags &= ~added;

            if (view.isAttachedToWindow() && view.getWindowVisibility() == View.VISIBLE) {
                WindowManager wm = (WindowManager) XposedHelpers.getObjectField(view, "mWindowManager");
                if (wm != null) {
                    wm.updateViewLayout(view, lp);
                }
            }
            Log.i(TAG, "已还原窗口 flag(" + why + "): 0x" + Integer.toHexString(added));
        } catch (Throwable t) {
            Log.w(TAG, "还原窗口 flag 失败(" + why + ")", t);
        }
    }

    /** 宿主窗口被移除时立即复位，防止 VISIBLE 容器 / 追加 flag 残留到下一次 attach */
    private static void armDetachReset(final FrameLayout view) {
        try {
            if (XposedHelpers.getAdditionalInstanceField(view, FIELD_ATTACH_LISTENER) != null) {
                return;
            }
            View.OnAttachStateChangeListener l = new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {}

                @Override
                public void onViewDetachedFromWindow(View v) {
                    if (!active) return;
                    try {
                        resetHostState((FrameLayout) v, "detached");
                    } catch (Throwable t) {
                        Log.w(TAG, "窗口移除复位失败", t);
                    }
                }
            };
            view.addOnAttachStateChangeListener(l);
            XposedHelpers.setAdditionalInstanceField(view, FIELD_ATTACH_LISTENER, l);
        } catch (Throwable t) {
            Log.w(TAG, "挂载窗口移除复位监听失败", t);
        }
    }

    /** 息屏时：若面板仍处于展示态，先走原生 dismiss 收尾，再无条件复位本模块状态 */
    private static void onScreenOff() {
        if (!active) return;
        for (FrameLayout host : snapshotHosts()) {
            try {
                CustomHyperOpenContainer c = getContainer(host);
                if (c != null && c.getVisibility() == View.VISIBLE) {
                    try {
                        dismissCompat(host);
                    } catch (Throwable t) {
                        Log.w(TAG, "息屏时调用原生 dismiss 失败", t);
                    }
                }
                resetHostState(host, "screen_off");
            } catch (Throwable t) {
                Log.w(TAG, "息屏复位失败", t);
            }
        }
    }

    /**
     * 注册进程级广播：
     *   - 配置变更：仅触发后台异步拉取，主线程不做任何跨进程调用；
     *   - 息屏：复位所有宿主，杜绝预览状态直接锁屏后的残留。
     */
    private static synchronized void ensureProcessReceivers(Context context) {
        if (configReceiver != null || context == null) return;
        Context app = context.getApplicationContext();
        final Context ctx = app != null ? app : context;

        BroadcastReceiver cfg = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (!active) return;
                Log.i(TAG, "收到模块配置变更广播，后台异步热重载！");
                try {
                    io.github.qmryqmm.hyperopen.safety.SafetyGuard.refresh();
                } catch (Throwable ignored) {}
                HyperOpenConfig.refreshAsync(c);
            }
        };
        BroadcastReceiver screen = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    onScreenOff();
                }
            }
        };

        try {
            IntentFilter f = new IntentFilter(HyperOpenConfig.ACTION_CONFIG_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(cfg, f, Context.RECEIVER_EXPORTED);
            } else {
                ctx.registerReceiver(cfg, f);
            }
            configReceiver = cfg;
        } catch (Throwable t) {
            Log.w(TAG, "注册配置变更广播失败", t);
        }

        try {
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(screen, f, Context.RECEIVER_EXPORTED);
            } else {
                ctx.registerReceiver(screen, f);
            }
            screenReceiver = screen;
        } catch (Throwable t) {
            Log.w(TAG, "注册息屏广播失败", t);
        }

        receiverContext = ctx;
    }

    private static synchronized void releaseProcessReceivers() {
        if (receiverContext == null) return;
        if (configReceiver != null) {
            try { receiverContext.unregisterReceiver(configReceiver); } catch (Throwable ignored) {}
        }
        if (screenReceiver != null) {
            try { receiverContext.unregisterReceiver(screenReceiver); } catch (Throwable ignored) {}
        }
        configReceiver = null;
        screenReceiver = null;
        receiverContext = null;
    }

    private static List<FrameLayout> snapshotHosts() {
        List<FrameLayout> out = new ArrayList<>();
        synchronized (HOSTS) {
            for (WeakReference<FrameLayout> ref : HOSTS) {
                FrameLayout h = ref.get();
                if (h != null) out.add(h);
            }
        }
        return out;
    }

    // ──────────────────────────────────────────────────────────────
    // Hook 卸载：安全模式运行期即时停用
    // ──────────────────────────────────────────────────────────────

    /**
     * 把一次 hook 安装返回的句柄全部登记下来。
     * hookAllMethods / hookAllConstructors 返回的是句柄集合，hookMethod /
     * findAndHookMethod 返回单个句柄，这里统一收口，保证没有漏网的 Hook。
     */
    private static void record(Object result) {
        if (result == null) {
            return;
        }
        if (result instanceof XC_MethodHook.Unhook) {
            HANDLES.add((XC_MethodHook.Unhook) result);
        } else if (result instanceof Set) {
            for (Object o : (Set<?>) result) {
                if (o instanceof XC_MethodHook.Unhook) {
                    HANDLES.add((XC_MethodHook.Unhook) o);
                }
            }
        }
    }

    private static void rememberHost(FrameLayout view) {
        synchronized (HOSTS) {
            for (WeakReference<FrameLayout> ref : HOSTS) {
                if (ref.get() == view) {
                    return;
                }
            }
            HOSTS.add(new WeakReference<>(view));
        }
    }

    /**
     * 立刻停用全部 Hook —— 安全模式的执行端。
     *
     * 做三件事，缺一不可：
     *   1. 先把 active 置 false：从这一刻起所有回调直接 return，不再干预 SystemUI；
     *   2. 逐个 unhook：把回调从 Xposed 的钩子链上摘掉，彻底消除调用开销；
     *   3. 把已注入的 CustomHyperOpenContainer 从视图树里摘除并复位，
     *      否则界面上会残留一个半展开的自定义面板。
     *
     * 整个过程被 try/catch 逐项包裹：任何一步失败都不能影响其余步骤，
     * 更不能让清理动作本身成为新的崩溃源。
     *
     * @return 实际卸载成功的 Hook 数量
     */
    public static int uninstall() {
        // 1. 先断逻辑，后拆钩子：避免卸载过程中仍有回调进入本模块代码
        boolean wasActive = active;
        active = false;

        if (!wasActive && HANDLES.isEmpty()) {
            return 0;
        }
        Log.e(TAG, "!!! 安全模式触发：正在即时停用全部 Hook !!!");

        HyperOpenConfig.setOnConfigUpdatedListener(null);
        releaseProcessReceivers();

        // 2. 拆除视图树上已注入的自定义容器
        synchronized (HOSTS) {
            for (WeakReference<FrameLayout> ref : HOSTS) {
                FrameLayout host = ref.get();
                if (host == null) continue;
                try {
                    // 先还原模糊与窗口 flag，再摘除容器
                    resetHostState(host, "uninstall");
                    CustomHyperOpenContainer container = getContainer(host);
                    if (container != null) {
                        if (container.getParent() instanceof ViewGroup) {
                            ((ViewGroup) container.getParent()).removeView(container);
                        }
                    }
                    XposedHelpers.removeAdditionalInstanceField(host, "customContainer");
                    Object l = XposedHelpers.getAdditionalInstanceField(host, FIELD_ATTACH_LISTENER);
                    if (l instanceof View.OnAttachStateChangeListener) {
                        host.removeOnAttachStateChangeListener((View.OnAttachStateChangeListener) l);
                    }
                    XposedHelpers.removeAdditionalInstanceField(host, FIELD_ATTACH_LISTENER);
                } catch (Throwable t) {
                    Log.w(TAG, "移除已注入的自定义容器失败", t);
                }
            }
            HOSTS.clear();
        }

        // 3. 逐个卸载 Hook
        int removed = 0;
        List<XC_MethodHook.Unhook> snapshot;
        synchronized (HANDLES) {
            snapshot = new ArrayList<>(HANDLES);
            HANDLES.clear();
        }
        for (XC_MethodHook.Unhook u : snapshot) {
            try {
                Member m = u.getHookedMethod();
                u.unhook();
                removed++;
                if (m != null) {
                    Log.i(TAG, "已卸载 Hook: " + m.getDeclaringClass().getName() + "#" + m.getName());
                }
            } catch (Throwable t) {
                Log.w(TAG, "卸载 Hook 失败", t);
            }
        }

        Log.e(TAG, "安全模式已停用全部 Hook，共卸载 " + removed + " 个回调，SystemUI 已恢复原生行为");
        return removed;
    }

    /**
     * 在指纹面板完全展开时后台静默唤醒模块进程（通过纯粹的 ContentProvider ping 探活，不展示任何 Activity）。
     * 消除系统 Linux cgroup v2 Freezer 挂起导致的跨进程延迟，为潜在的 Root 动作提前预热解冻。
     */
    private static void warmupModuleProcessAsync(Context context) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        io.github.qmryqmm.hyperopen.util.IpcExecutor.runAsync(() -> {
            try {
                appCtx.getContentResolver().call(
                    android.net.Uri.parse("content://io.github.qmryqmm.hyperopen.provider"),
                    "ping", null, null
                );
            } catch (Throwable ignored) {}
        });
    }

    private static Point getDisplaySize(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        if (wm != null) {
            Display display = wm.getDefaultDisplay();
            display.getRealSize(size);
        }
        return size;
    }
}
