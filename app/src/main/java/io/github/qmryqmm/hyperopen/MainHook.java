package io.github.qmryqmm.hyperopen;

import android.content.Context;
import android.util.Log;

import io.github.qmryqmm.hyperopen.hook.HyperOpenHook;
import io.github.qmryqmm.hyperopen.safety.SafetyGuard;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "HyperOpen";
    public static final String TARGET_PACKAGE = "com.android.systemui";
    public static final String MODULE_PACKAGE = "io.github.qmryqmm.hyperopen";

    /** 是否已得出明确结论（允许安装 / 明确拒绝）。只有「未决」才允许后续重试与解锁补装 */
    private static volatile boolean concluded = false;
    /** 已排定一次重试，防止多个部署点重复排队 */
    private static volatile boolean retryPending = false;
    /** 重试上下文（补装需要 lpparam，而解锁广播回调里拿不到参数） */
    private static volatile XC_LoadPackage.LoadPackageParam savedLpparam;
    private static volatile Context appContext;
    /** ACTION_USER_UNLOCKED 补装监听只注册一次 */
    private static volatile boolean unlockReceiverArmed = false;
    /** Provider 未就绪/存储不可用时的后台重试次数上限（之后转为等解锁事件补装） */
    private static final int MAX_SAFETY_RETRY = 8;
    private static int safetyRetry = 0;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (MODULE_PACKAGE.equals(lpparam.packageName)) {
            try {
                XposedHelpers.findAndHookMethod(
                    "io.github.qmryqmm.hyperopen.safety.ModuleStatus",
                    lpparam.classLoader,
                    "isModuleActivated",
                    de.robv.android.xposed.XC_MethodReplacement.returnConstant(Boolean.TRUE)
                );
                Log.i(TAG, "已成功激活模块自身进程自检 Hook: " + lpparam.processName);
            } catch (Throwable t) {
                Log.w(TAG, "自检 Hook 挂载失败", t);
            }
            return;
        }

        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        Log.i(TAG, "检测到小米 SystemUI 进程加载，开始部署指纹快捷方式增强 Hook: " + lpparam.processName);

        // 1. 若此刻 Application 已就绪，直接进入安全模式闸门
        Context ctx = currentApplication(lpparam);
        if (ctx != null) {
            deploy(lpparam, ctx);
            return;
        }

        // 2. 否则挂到最早的可用 Context 回调点上，拿到 Context 后再过闸门
        if (!armContextCallbacks(lpparam)) {
            Log.e(TAG, "未能挂载任何 Context 回调点，本次启动放弃部署 Hook");
        }
    }

    /**
     * 安全模式闸门：只有 SafetyGuard 判定允许，才真正安装 Hook。
     *
     * 「未决」（Provider 不可达 / 存储暂不可用）不写结论：
     * 后台指数退避重试 + 监听 ACTION_USER_UNLOCKED 在用户解锁后补装，
     * 彻底解决「重启后模块不生效，必须打开 App 手动重启系统界面」的时序问题。
     */
    private static synchronized void deploy(XC_LoadPackage.LoadPackageParam lpparam, Context ctx) {
        if (concluded) {
            return;
        }
        savedLpparam = lpparam;
        try {
            Context app = ctx.getApplicationContext();
            appContext = app != null ? app : ctx;
        } catch (Throwable t) {
            appContext = ctx;
        }
        armUnlockReceiver();

        Boolean allowed;
        try {
            allowed = SafetyGuard.install(ctx);
        } catch (Throwable t) {
            Log.e(TAG, "安全模式检查异常，为保护 SystemUI 本次不安装 Hook", t);
            return;
        }

        if (allowed == null) {
            // 状态暂不可查：后台线程指数退避重试，绝不阻塞/占用主线程
            scheduleRetry();
            return;
        }

        concluded = true;
        retryPending = false;

        if (!allowed) {
            Log.e(TAG, "安全模式已激活或 Hook 开关已关闭 → 本次启动不安装任何 Hook，SystemUI 将以原生状态运行");
            return;
        }

        try {
            // 注册「运行期即时停用」执行器：崩溃哨兵凑够阈值的那一刻，
            // 由 SafetyGuard 直接回调这里，把已装好的 Hook 全部拆掉。
            SafetyGuard.setDeactivator(new Runnable() {
                @Override
                public void run() {
                    HyperOpenHook.uninstall();
                }
            });
            HyperOpenHook.hook(lpparam);
            HyperOpenHook.reportInjectedStatus(ctx);
            Log.i(TAG, "Hook 部署流程结束");
        } catch (Throwable t) {
            Log.e(TAG, "Hook 部署发生未预期异常", t);
            try {
                SafetyGuard.reportDeployFailure(t);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * 状态暂不可查时的后台重试：1s/2s/4s/…/30s 封顶，共约 2 分钟；
     * 超限后不再空转，转入等待 ACTION_USER_UNLOCKED 解锁事件补装。
     */
    private static void scheduleRetry() {
        if (retryPending) {
            return;
        }
        if (safetyRetry >= MAX_SAFETY_RETRY) {
            Log.e(TAG, "多次重试后仍无法确认安全模式状态，转入等待用户解锁事件（ACTION_USER_UNLOCKED）补装");
            return;
        }
        retryPending = true;
        safetyRetry++;
        final long delay = Math.min(500L << safetyRetry, 30000L);
        Log.w(TAG, "安全模式状态暂不可查，第 " + safetyRetry + " 次重试将在 " + delay + "ms 后进行（后台线程）");
        try {
            // 重试走 IpcExecutor 后台线程：模块进程可能被冻结/冷启动耗时数秒，
            // 以前放主线程 postDelayed 有 SystemUI 输入 ANR 风险
            io.github.qmryqmm.hyperopen.util.IpcExecutor.runAsyncDelayed(new Runnable() {
                @Override
                public void run() {
                    retryPending = false;
                    XC_LoadPackage.LoadPackageParam lp = savedLpparam;
                    Context c = appContext;
                    if (lp == null || c == null) {
                        return;
                    }
                    deploy(lp, c);
                }
            }, delay);
        } catch (Throwable t) {
            retryPending = false;
            Log.e(TAG, "安排安全模式重试失败", t);
        }
    }

    /**
     * 注册 ACTION_USER_UNLOCKED 补装监听（进程级一次）。
     *
     * Direct Boot 场景：SystemUI 在用户解锁前启动，锁定期安全状态可能不可查；
     * 用户解锁瞬间系统发出 ACTION_USER_UNLOCKED，此刻 CE/Provider 均就绪，
     * 立即补装并刷新配置。若此前已装好（concluded=true），仅做一次配置热刷新，
     * 把锁定期临时用的默认配置换成迁移后的用户真实配置。
     */
    private static void armUnlockReceiver() {
        if (unlockReceiverArmed) {
            return;
        }
        unlockReceiverArmed = true;
        try {
            final Context c = appContext;
            if (c == null) {
                return;
            }
            android.content.IntentFilter filter =
                new android.content.IntentFilter(android.content.Intent.ACTION_USER_UNLOCKED);
            android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context context, android.content.Intent intent) {
                    Log.i(TAG, "收到 ACTION_USER_UNLOCKED，尝试补装 Hook 并刷新配置");
                    try {
                        io.github.qmryqmm.hyperopen.config.HyperOpenConfig.refreshAsync(c);
                    } catch (Throwable ignored) {}
                    XC_LoadPackage.LoadPackageParam lp = savedLpparam;
                    if (lp != null) {
                        deploy(lp, c);
                    }
                }
            };
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                c.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                c.registerReceiver(receiver, filter);
            }
            Log.i(TAG, "ACTION_USER_UNLOCKED 补装监听已注册");
        } catch (Throwable t) {
            Log.w(TAG, "注册用户解锁广播失败", t);
        }
    }

    private static Context currentApplication(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> helper = XposedHelpers.findClass("android.app.AndroidAppHelper", lpparam.classLoader);
            Object ctx = XposedHelpers.callStaticMethod(helper, "currentApplication");
            return ctx instanceof Context ? (Context) ctx : null;
        } catch (Throwable t) {
            Log.w(TAG, "获取当前 Application 失败: " + t);
            return null;
        }
    }

    /**
     * 依次尝试 Application#attachBaseContext / Application#onCreate /
     * Instrumentation#callApplicationOnCreate 三个回调点，任一触发即完成部署。
     */
    private static boolean armContextCallbacks(XC_LoadPackage.LoadPackageParam lpparam) {
        boolean armed = false;

        try {
            XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader,
                "attachBaseContext", Context.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object self = param.thisObject;
                        Context ctx = self instanceof Context ? (Context) self : null;
                        if (ctx == null && param.args.length > 0 && param.args[0] instanceof Context) {
                            ctx = (Context) param.args[0];
                        }
                        if (ctx != null) {
                            deploy(lpparam, ctx);
                        }
                    }
                });
            armed = true;
            Log.i(TAG, "已挂载 Application#attachBaseContext 部署点");
        } catch (Throwable t) {
            Log.w(TAG, "挂载 Application#attachBaseContext 失败", t);
        }

        try {
            XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader,
                "onCreate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof Context) {
                            deploy(lpparam, (Context) param.thisObject);
                        }
                    }
                });
            armed = true;
            Log.i(TAG, "已挂载 Application#onCreate 部署点");
        } catch (Throwable t) {
            Log.w(TAG, "挂载 Application#onCreate 失败", t);
        }

        try {
            XposedHelpers.findAndHookMethod("android.app.Instrumentation", lpparam.classLoader,
                "callApplicationOnCreate", android.app.Application.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof Context) {
                            deploy(lpparam, (Context) param.args[0]);
                        }
                    }
                });
            armed = true;
            Log.i(TAG, "已挂载 Instrumentation#callApplicationOnCreate 部署点");
        } catch (Throwable t) {
            Log.w(TAG, "挂载 Instrumentation#callApplicationOnCreate 失败", t);
        }

        return armed;
    }
}
