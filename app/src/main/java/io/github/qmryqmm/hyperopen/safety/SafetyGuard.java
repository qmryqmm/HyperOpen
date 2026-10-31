package io.github.qmryqmm.hyperopen.safety;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.qmryqmm.hyperopen.util.IpcExecutor;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * SystemUI 进程内的安全模式守卫。
 *
 * 职责：
 *   1. 进程加载时向模块 Provider 查询安全模式状态，决定是否安装 Hook；
 *   2. 安装崩溃哨兵，捕获 Hook 相关的未捕获异常并即时上报；
 *   3. 延迟上报存活确认，用于区分「Hook 安装成功」与「安装后立刻崩溃」。
 *
 * 该类不依赖任何模块 UI 类，所有跨进程调用与反射调用都被 try/catch 包裹，
 * 保证即使整套安全机制自身失效，也不会给 SystemUI 引入新的崩溃点。
 */
public final class SafetyGuard {
    private static final String TAG = "HyperOpen_Safety";

    private static final Uri PROVIDER_URI = Uri.parse("content://io.github.qmryqmm.hyperopen.provider");
    private static final String M_PROVIDER_CHECK  = "safety_check";
    private static final String M_PROVIDER_REPORT = "safety_report_crash";
    private static final String M_PROVIDER_HEART  = "safety_heartbeat";
    private static final String M_PROVIDER_STATE  = "safety_state";
    private static final String M_PROVIDER_MARK   = "safety_mark_hooked";

    /** 本模块类名 / 被 Hook 的 MIUI 类名特征，命中才判定为「Hook 相关崩溃」 */
    private static final String[] HOOK_SIGNATURES = {
        "io.github.qmryqmm.hyperopen",
        "MiuiGxzwQuickOpenView",
        "MiuiGxzwIconView",
        "CustomHyperOpenContainer",
        "CircularBubbleView",
        "CircularRegionBackdropView",
        "de.robv.android.xposed"
    };

    private static volatile boolean hookAllowed = false;
    private static Context appContext;
    private static final AtomicBoolean crashReported = new AtomicBoolean(false);
    private static boolean sentinelInstalled = false;

    /**
     * 运行期「即时停用 Hook」的执行器。
     *
     * SafetyGuard 本身不认识任何 Hook 实现类，只持有这个由 Hook 侧注册进来的动作。
     * 安全模式在运行期被触发时（崩溃哨兵凑够阈值，或状态被外部改为安全模式），
     * 立刻执行它把已经装上去的 Hook 全部拆掉，而不是等到下一次 SystemUI 重启。
     */
    private static volatile Runnable deactivator;
    /** 保证停用动作只执行一次 */
    private static final AtomicBoolean deactivated = new AtomicBoolean(false);

    public static void setDeactivator(Runnable r) {
        deactivator = r;
    }

    /**
     * 运行期即时停用全部 Hook。
     * 由崩溃哨兵在阈值触发的那一刻调用，也由 {@link #refresh()} 在发现
     * 安全模式被外部激活时调用。
     */
    private static void deactivateNow(String why) {
        if (!deactivated.compareAndSet(false, true)) {
            return;
        }
        hookAllowed = false;
        Log.e(TAG, "!!! 安全模式生效（" + why + "），立即停用本进程内全部 Hook !!!");
        Runnable r = deactivator;
        if (r == null) {
            // Hook 尚未部署或已自行退出：没有可拆的钩子，仅保持 hookAllowed=false
            Log.w(TAG, "当前无已部署的 Hook 需要卸载，本进程后续 Hook 逻辑一律拒绝执行");
            return;
        }
        try {
            r.run();
        } catch (Throwable t) {
            Log.e(TAG, "即时停用 Hook 时发生异常（安全模式标志已生效，Hook 逻辑已全部关闭）", t);
        }
    }

    /**
     * 本进程内的判定缓存。
     *
     * 必须在进程内只做一次「启动闸门」查询：safetyCheck 每调用一次都会把 session_start
     * 刷新为当前时间并把 session_healthy 置为 false，如果重复调用，第二次就会把
     * 「自己刚刚写下的这次会话」误判成上一次会话的启动期崩溃，凭空凑出崩溃计数。
     * 因此这里把首次结论缓存下来，重复调用直接返回缓存。
     */
    private static Boolean decided = null;

    private SafetyGuard() {}

    /**
     * 在 MainHook 中调用。
     *
     * @return Boolean.TRUE  允许安装 Hook
     *         Boolean.FALSE 安全模式生效 / Hook 开关关闭，禁止安装
     *         null          Provider 尚不可达，无法判定，调用方应稍后重试
     */
    public static synchronized Boolean install(Context ctx) {
        if (decided != null) {
            return decided;
        }
        if (ctx == null) {
            Log.e(TAG, "无法获取 SystemUI Context，出于安全考虑本次不安装 Hook");
            decided = Boolean.FALSE;
            return decided;
        }
        appContext = ctx;

        // 崩溃哨兵与安全模式判定无关，先装上：即便 Provider 暂时不可达，
        // 运行期崩溃也能被即时记录，不会等到下一次启动才补算。
        installCrashSentinel();

        Bundle state = call(ctx, M_PROVIDER_CHECK, null);
        if (state == null) {
            Log.w(TAG, "无法查询安全模式状态（Provider 不可达），稍后重试");
            return null; // 不写 decided：Provider 未就绪时结论无效，允许后续重试
        }
        if (state.getBoolean("storage_unavailable", false)) {
            // 语义修正：存储暂不可用 ≠ 安全模式。绝不据此写 decided=FALSE，
            // 否则 Direct Boot 锁定期会被误判成「安全模式已激活」且永不翻身
            Log.w(TAG, "安全模式状态存储暂不可用，稍后重试（不视为安全模式）");
            return null;
        }

        boolean safeMode = state.getBoolean("safe_mode", false);
        boolean allowed = state.getBoolean("allowed", !safeMode);
        hookAllowed = allowed;
        decided = allowed;

        if (state.getBoolean("just_triggered", false)) {
            Log.e(TAG, "!!! SystemUI 连续崩溃达阈值，本次启动已自动进入安全模式，全部 Hook 停用 !!!");
        } else if (safeMode) {
            Log.e(TAG, "安全模式处于激活状态，跳过全部 Hook 安装（崩溃计数 "
                + state.getInt("crash_count", 0) + "/" + state.getInt("threshold", 2) + "）");
        } else if (!allowed) {
            Log.i(TAG, "Hook 总开关处于关闭状态，跳过全部 Hook 安装");
        } else {
            Log.i(TAG, "安全模式检查通过，准备安装 Hook（当前崩溃计数 "
                + state.getInt("crash_count", 0) + "/" + state.getInt("threshold", 2) + "）");
            // 先登记为「Hook 会话」，再安装 Hook：登记必须发生在 Hook 有机会崩溃之前，
            // 否则一旦 Hook 在安装过程中就崩掉，这次崩溃就归不了因。
            call(ctx, M_PROVIDER_MARK, null);
            scheduleHeartbeat();
            // 不再启用死循环式的 10s 运行期巡检 (startRuntimeWatch)：
            // 避免在用户长时间不用手机、模块 App 进程被系统深度冻结/杀死后，因跨进程 IPC 异常引发误停用。
            // 外部安全状态变更统一通过广播按需驱动刷新。
        }

        return allowed;
    }

    /** 主动重新拉取一次安全模式状态（可在关键 Hook 入口调用）。 */
    public static boolean refresh() {
        if (appContext == null) {
            return hookAllowed;
        }
        Bundle state = call(appContext, M_PROVIDER_STATE, null);
        if (state != null && state.getBoolean("storage_unavailable", false)) {
            // 存储暂不可用：保持当前 Hook 状态不变，绝不因「读不到状态」拆掉已装好的 Hook
            Log.w(TAG, "巡检读到状态存储暂不可用，保持当前 Hook 状态不变");
            return hookAllowed;
        }
        if (state != null) {
            boolean allowed = state.getBoolean("allowed", !state.getBoolean("safe_mode", false));
            hookAllowed = allowed;
            if (!allowed) {
                // 状态被外部（用户在模块界面点「进入安全模式」/关闭总开关）改为停用，
                // 同样要立刻拆掉本进程内已经装好的 Hook。
                final String why = "外部状态变更: " + (state.getBoolean("safe_mode", false) ? "安全模式" : "Hook 总开关关闭");
                // 卸载 Hook 涉及视图树操作，必须回到主线程执行（refresh 可能在后台 IPC 线程被调用）
                IpcExecutor.runOnMain(new Runnable() {
                    @Override
                    public void run() {
                        deactivateNow(why);
                    }
                });
            }
        }
        return hookAllowed;
    }

    /**
     * Hook 部署阶段自身抛出异常时上报一次崩溃。
     * 这类异常不会逃逸到进程顶层，但同样说明本次 Hook 安装有问题，
     * 必须计入崩溃队列，避免用户陷入「每次启动都失败」却无法自救的死循环。
     */
    public static void reportDeployFailure(Throwable t) {
        if (appContext == null) {
            Log.e(TAG, "Hook 部署失败，且无 Context 可上报", t);
            return;
        }
        if (!crashReported.compareAndSet(false, true)) {
            return;
        }
        try {
            String reason = "Hook 部署阶段异常: " + describe(t);
            Log.e(TAG, "上报 Hook 部署失败: " + reason);
            Bundle res = call(appContext, M_PROVIDER_REPORT, reason);
            // 部署失败 + 已达阈值 → 立即拆掉本次刚装上的（可能装了一半的）Hook，
            // 避免半成品钩子留在 SystemUI 里继续制造异常。
            if (res != null && res.getBoolean("just_triggered", false)) {
                deactivateNow("Hook 部署异常达阈值");
            }
        } catch (Throwable ignored) {}
    }

    // ──────────────────────────────────────────────────────────────
    // 崩溃哨兵
    // ──────────────────────────────────────────────────────────────

    /**
     * 安装未捕获异常哨兵。
     *
     * 优先链式替换 Thread 的 uncaughtExceptionPreHandler（API 26+ 由 RuntimeInit 安装的
     * LoggingHandler），它在线程即将因未捕获异常终止时最先被调用；链式调用保证原有的
     * 崩溃日志行为不被破坏。
     *
     * 若该隐藏接口不可用，则回退为 Hook Thread#setDefaultUncaughtExceptionHandler。
     */
    private static synchronized void installCrashSentinel() {
        if (sentinelInstalled) {
            return;
        }
        sentinelInstalled = true;

        Object original = getPreHandler();

        try {
            Method setter = Thread.class.getDeclaredMethod(
                "setUncaughtExceptionPreHandler", Thread.UncaughtExceptionHandler.class);
            setter.setAccessible(true);
            setter.invoke(null, buildSentinel(original));
            Log.i(TAG, "崩溃哨兵已安装 (Thread.setUncaughtExceptionPreHandler)");
            return;
        } catch (Throwable ignored) {}

        try {
            Field field = Thread.class.getDeclaredField("uncaughtExceptionPreHandler");
            field.setAccessible(true);
            field.set(null, buildSentinel(original));
            Log.i(TAG, "崩溃哨兵已安装 (uncaughtExceptionPreHandler 字段)");
            return;
        } catch (Throwable t) {
            Log.w(TAG, "preHandler 路径均不可用，回退 setDefaultUncaughtExceptionHandler: " + t);
        }

        installDefaultHandlerSentinel();
    }

    private static Object getPreHandler() {
        try {
            Method getter = Thread.class.getDeclaredMethod("getUncaughtExceptionPreHandler");
            getter.setAccessible(true);
            return getter.invoke(null);
        } catch (Throwable t) {
            try {
                Field field = Thread.class.getDeclaredField("uncaughtExceptionPreHandler");
                field.setAccessible(true);
                return field.get(null);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static Thread.UncaughtExceptionHandler buildSentinel(final Object original) {
        return new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                reportIfHookRelated(e);
                // 链式调用原有处理器，保持系统崩溃日志与 AMS 上报行为不变
                if (original instanceof Thread.UncaughtExceptionHandler) {
                    try {
                        ((Thread.UncaughtExceptionHandler) original).uncaughtException(t, e);
                    } catch (Throwable ignored) {}
                }
            }
        };
    }

    /**
     * 崩溃哨兵的统一上报入口：一次崩溃只上报一次；仅当异常与本模块/Hook 目标相关时才计入；
     * 上报后若 Provider 回执说明「本次已达阈值」，立刻拆掉本进程内的全部 Hook。
     */
    private static void reportIfHookRelated(Throwable e) {
        try {
            if (!crashReported.compareAndSet(false, true)) {
                return;
            }
            String reason = describe(e);
            if (!isHookRelated(e)) {
                Log.w(TAG, "捕获到与 Hook 无关的未捕获异常，不上报: " + reason);
                return;
            }
            Log.e(TAG, "捕获到 Hook 相关未捕获异常，立即上报崩溃: " + reason);
            Bundle res = call(appContext, M_PROVIDER_REPORT, reason);
            if (res != null && res.getBoolean("just_triggered", false)) {
                // 连续崩溃已达阈值：不等下次启动，此刻就停用全部 Hook
                deactivateNow("连续崩溃达阈值");
            }
        } catch (Throwable ignored) {}
    }

    private static void installDefaultHandlerSentinel() {
        try {
            XposedHelpers.findAndHookMethod(
                Thread.class, "setDefaultUncaughtExceptionHandler",
                Thread.UncaughtExceptionHandler.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object arg = param.args[0];
                        if (arg == null || arg.getClass().getName().contains("SafetyGuard")) {
                            return;
                        }
                        param.args[0] = wrap(arg);
                    }
                }
            );
            Log.i(TAG, "崩溃哨兵已安装 (setDefaultUncaughtExceptionHandler 回退路径)");
        } catch (Throwable t) {
            Log.w(TAG, "回退崩溃哨兵安装失败，仅依赖启动期启发式判定", t);
        }
    }

    private static Thread.UncaughtExceptionHandler wrap(final Object original) {
        return new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                reportIfHookRelated(e);
                if (original instanceof Thread.UncaughtExceptionHandler) {
                    try {
                        ((Thread.UncaughtExceptionHandler) original).uncaughtException(t, e);
                    } catch (Throwable ignored) {}
                }
            }
        };
    }

    private static boolean isHookRelated(Throwable e) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        Throwable cur = e;
        while (cur != null && depth < 8) {
            sb.append(cur.getClass().getName()).append('\n');
            StackTraceElement[] st = cur.getStackTrace();
            if (st != null) {
                for (StackTraceElement el : st) {
                    sb.append(el.getClassName()).append('.').append(el.getMethodName()).append('\n');
                }
            }
            cur = cur.getCause();
            depth++;
        }
        String blob = sb.toString();
        for (String sig : HOOK_SIGNATURES) {
            if (blob.contains(sig)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(Throwable e) {
        if (e == null) {
            return "未知崩溃";
        }
        StackTraceElement[] st = e.getStackTrace();
        StackTraceElement top = (st != null && st.length > 0) ? st[0] : null;
        String head = e.getClass().getName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        return top == null ? head
            : (head + "  @  " + top.getClassName() + "." + top.getMethodName() + ":" + top.getLineNumber());
    }

    // ──────────────────────────────────────────────────────────────
    // 存活确认
    // ──────────────────────────────────────────────────────────────

    /** 运行期巡检间隔：用户可能在模块界面手动进入/退出安全模式，需要及时跟随 */
    private static final long WATCH_INTERVAL_MS = 10 * 1000L;
    private static boolean watching = false;

    /**
     * 启动运行期巡检。
     *
     * Hook 已经装好之后，安全模式仍可能被外部激活（用户在模块界面点「进入安全模式」、
     * 关闭 Hook 总开关、或崩溃哨兵在别的路径上报成功）。巡检定期查询状态，
     * 一旦发现不允许，立刻执行即时停用。查询失败时不做任何动作，避免误停。
     */
    private static synchronized void startRuntimeWatch() {
        if (watching) {
            return;
        }
        watching = true;
        // 巡检在后台 IPC 线程执行：Provider 所在的模块进程可能被冻结，
        // 绝不能在 SystemUI 主线程上每 10 秒做一次同步跨进程调用
        IpcExecutor.runAsyncDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (deactivated.get()) {
                        watching = false; // 已停用，无需继续巡检
                        return;
                    }
                    refresh();
                } catch (Throwable t) {
                    Log.w(TAG, "安全模式巡检异常", t);
                }
                if (!deactivated.get()) {
                    IpcExecutor.runAsyncDelayed(this, WATCH_INTERVAL_MS);
                } else {
                    watching = false;
                }
            }
        }, WATCH_INTERVAL_MS);
        Log.i(TAG, "安全模式运行期巡检已启动（间隔 " + (WATCH_INTERVAL_MS / 1000L) + " 秒）");
    }

    private static void scheduleHeartbeat() {
        try {
            IpcExecutor.runAsyncDelayed(new Runnable() {
                @Override
                public void run() {
                    call(appContext, M_PROVIDER_HEART, null);
                }
            }, SafetyStore.HEARTBEAT_DELAY_MS);
        } catch (Throwable t) {
            Log.w(TAG, "安排存活确认失败", t);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Provider 调用
    // ──────────────────────────────────────────────────────────────

    private static Bundle call(Context ctx, String method, String arg) {
        if (ctx == null) {
            return null;
        }
        try {
            ContentResolver cr = ctx.getContentResolver();
            if (cr == null) {
                return null;
            }
            return cr.call(PROVIDER_URI, method, arg, null);
        } catch (Throwable t) {
            Log.w(TAG, "调用 Provider 方法 " + method + " 失败: " + t);
            return null;
        }
    }
}
