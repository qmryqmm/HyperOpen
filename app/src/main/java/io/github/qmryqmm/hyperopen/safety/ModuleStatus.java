package io.github.qmryqmm.hyperopen.safety;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * 实时模块状态与注入检测工具。
 * <p>
 * 解决「永远只提示 Hook 已启用」的问题，提供真实的双层状态检验：
 * 1. [LSPosed 模块激活状态]：通过模块自身进程自检 Hook 探测（内存态）与 /data/adb/lspd 磁盘配置探测（配置态）；
 * 2. [SystemUI 注入生效状态]：通过 SystemUI 进程上报的 PID、时间戳与活跃探活检验；
 * 3. [一键秒级自重启]：修改 LSPosed 启用状态后一键重启本应用完成注入生效，无需手动划掉后台。
 */
public final class ModuleStatus {

    public static final String ACTION_PING_HOOK = "io.github.qmryqmm.hyperopen.ACTION_PING_HOOK";

    private ModuleStatus() {}

    /**
     * 检测本模块在 LSPosed / Xposed 框架中是否已被激活（当前运行进程内存态）。
     * 默认返回 false；当框架加载本模块自身时，由 MainHook 将其 Hook 为返回 true。
     */
    public static boolean isModuleActivated() {
        return false;
    }

    /**
     * 通过 Root 权限实时探测 LSPosed 磁盘数据库 (/data/adb/lspd)，
     * 获知用户是否刚刚在 LSPosed 管理器中开启了本模块（即便本应用进程尚未重启）。
     */
    public static boolean isLsposedConfigEnabled() {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{
                "su", "-c", "grep -r -l io.github.qmryqmm.hyperopen /data/adb/lspd 2>/dev/null"
            });
            p.getOutputStream().close();
            if (!p.waitFor(1500, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                return false;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            return p.exitValue() == 0 && line != null && !line.trim().isEmpty();
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (p != null) p.destroy();
        }
    }

    /**
     * 一键秒级重启本软件（冷重启），让 LSPosed 框架重新加载本进程并使 Hook 生效。
     */
    public static void restartSelf(Context context) {
        if (context == null) return;
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
                Runtime.getRuntime().exit(0);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 综合实时状态数据结构
     */
    public static final class LiveStatus {
        /** 当前进程内框架 Hook 是否已激活生效（内存态） */
        public final boolean lsposedActive;
        /** LSPosed 磁盘配置中是否已开启本模块（配置态，支持即时探测） */
        public final boolean lsposedConfigEnabled;
        /** SystemUI 进程内 Hook 是否已成功注入且活跃 */
        public final boolean hookInjected;
        /** 是否处于安全模式（连续崩溃保护） */
        public final boolean safeMode;
        /** 用户设置的 Hook 总开关 */
        public final boolean hookEnabled;
        /** 注入的 SystemUI 进程 PID（0 为未知） */
        public final int systemUiPid;
        /** 上次注入成功的时间戳 */
        public final long lastInjectedTime;
        /** 连续崩溃次数 */
        public final int crashCount;
        /** 触发安全模式的崩溃阈值 */
        public final int crashThreshold;
        /** 历史累计崩溃次数 */
        public final int totalCrash;
        /** 上次会话存活状态确认 */
        public final boolean sessionHealthy;
        /** 崩溃日志文本 */
        public final String crashLog;
        /** 宿主 SystemUI 版本名 */
        public final String systemUiVersion;

        public LiveStatus(
            boolean lsposedActive,
            boolean lsposedConfigEnabled,
            boolean hookInjected,
            boolean safeMode,
            boolean hookEnabled,
            int systemUiPid,
            long lastInjectedTime,
            int crashCount,
            int crashThreshold,
            int totalCrash,
            boolean sessionHealthy,
            String crashLog,
            String systemUiVersion
        ) {
            this.lsposedActive = lsposedActive;
            this.lsposedConfigEnabled = lsposedConfigEnabled;
            this.hookInjected = hookInjected;
            this.safeMode = safeMode;
            this.hookEnabled = hookEnabled;
            this.systemUiPid = systemUiPid;
            this.lastInjectedTime = lastInjectedTime;
            this.crashCount = crashCount;
            this.crashThreshold = crashThreshold;
            this.totalCrash = totalCrash;
            this.sessionHealthy = sessionHealthy;
            this.crashLog = crashLog;
            this.systemUiVersion = systemUiVersion;
        }
    }

    /**
     * 获取当前综合实时状态
     */
    public static LiveStatus getLiveStatus(Context ctx) {
        boolean lsposedActive = isModuleActivated();
        // 若当前进程未注入，则通过 Root 实时探测磁盘配置，判定是否刚在 LSPosed 中开启
        boolean lsposedConfig = lsposedActive || isLsposedConfigEnabled();

        Bundle state = SafetyStore.getState(ctx);

        boolean safeMode = state.getBoolean("safe_mode", false);
        boolean hookEnabled = state.getBoolean("hook_enabled", true);
        int crashCount = state.getInt("crash_count", 0);
        int threshold = state.getInt("threshold", SafetyStore.CRASH_THRESHOLD);
        int totalCrash = state.getInt("total_crash", 0);
        boolean sessionHealthy = state.getBoolean("session_healthy", true);
        String crashLog = state.getString("crash_log", "");

        int injectedPid = state.getInt("hook_injected_pid", 0);
        long injectedTime = state.getLong("hook_injected_time", 0L);
        boolean hookActive = state.getBoolean("hook_active", false);
        String sysUiVersion = state.getString("hook_sysui_version", "");

        // 判定 Hook 是否真正注入：
        // 1. 安全模式下或总开关关闭时，Hook 未注入；
        // 2. 只有上报过注入 PID 且标记为 active 时才视为已注入；
        // 3. 时间在过去合理时间内（若超过 7 天无任何新会话记录，则视为已失效）。
        long now = System.currentTimeMillis();
        boolean timeValid = injectedTime > 0 && (now - injectedTime) < (7L * 24 * 3600 * 1000L);
        boolean injected = !safeMode && hookEnabled && hookActive && injectedPid > 0 && timeValid;

        return new LiveStatus(
            lsposedActive,
            lsposedConfig,
            injected,
            safeMode,
            hookEnabled,
            injectedPid,
            injectedTime,
            crashCount,
            threshold,
            totalCrash,
            sessionHealthy,
            crashLog != null ? crashLog : "",
            sysUiVersion != null ? sysUiVersion : ""
        );
    }

    /**
     * 向 SystemUI 发送探活广播，促使 SystemUI 内的 Hook 模块即时刷新活跃时间戳
     */
    public static void pingSystemUi(Context ctx) {
        if (ctx == null) return;
        try {
            Intent intent = new Intent(ACTION_PING_HOOK);
            intent.setPackage("com.android.systemui");
            ctx.sendBroadcast(intent);
        } catch (Throwable ignored) {}
    }
}
