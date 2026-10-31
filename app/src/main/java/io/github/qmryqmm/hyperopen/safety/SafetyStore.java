package io.github.qmryqmm.hyperopen.safety;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import io.github.qmryqmm.hyperopen.config.HyperOpenConfig;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 安全模式持久化状态机。
 *
 * 全部写入都发生在模块 App 进程（由 SettingsProvider 调用），SystemUI 进程只通过
 * ContentProvider 读写，从而保证：
 *   1. SystemUI 崩溃后进程内存全部丢失，但崩溃计数依然存活；
 *   2. 只有一个写入者，计数不会出现竞态。
 *
 * 崩溃判定有两个信号源：
 *   A. 启动期异常退出：上一次会话在 SESSION_MAX_MS 内结束，且从未上报过存活确认
 *      → 典型的 Hook 引发的开机崩溃 / 崩溃循环；
 *   B. 未捕获异常：异常堆栈里出现本模块类或被 Hook 的 MIUI 类
 *      → 运行期由 Hook 引发的崩溃。
 *
 * 两个信号都写入同一个时间戳队列；队列内 CRASH_WINDOW_MS 内累计达到
 * CRASH_THRESHOLD(2) 次，即判定为「连续崩溃」，自动进入安全模式并停用全部 Hook。
 */
public final class SafetyStore {
    private static final String TAG = "HyperOpen_Safety";

    private static final Object LOCK = new Object();

    /** 是否处于安全模式（Hook 被强制停用，粘性，需用户手动退出） */
    public static final String KEY_SAFE_MODE       = "safety_safe_mode";
    /** Hook 总开关（用户手动开关） */
    public static final String KEY_HOOK_ENABLED    = "safety_hook_enabled";
    /** 连续崩溃时间戳队列（逗号分隔的 epoch ms） */
    public static final String KEY_CRASH_TIMES     = "safety_crash_times";
    /** 人类可读的崩溃日志（换行分隔） */
    public static final String KEY_CRASH_LOG       = "safety_crash_log";
    /** 本次 SystemUI 会话的启动时间 */
    public static final String KEY_SESSION_START   = "safety_session_start";
    /** 本次会话是否已上报存活确认 */
    public static final String KEY_SESSION_HEALTHY = "safety_session_healthy";
    /** 历史崩溃总次数 */
    public static final String KEY_TOTAL_CRASH     = "safety_total_crash";
    /**
     * 本次会话是否已经上报过一次崩溃。
     *
     * 用于去重：一次崩溃可能同时被两个信号捕获 —— 运行期的未捕获异常哨兵（信号 B）
     * 会即时上报，随后进程死亡，下一次启动的启动期启发式（信号 A）又会看到
     * 「未确认存活就退出」。若不去重，单次崩溃就会被记成 2 次，阈值 2 会立刻被
     * 一次崩溃触发。因此上报过信号 B 的会话，不再重复计入信号 A。
     */
    public static final String KEY_SESSION_REPORTED = "safety_session_reported";
    /**
     * 本次会话是否实际安装过 Hook。
     *
     * 只有「装了 Hook 的会话」异常退出才归因于 Hook；没有装 Hook 的会话（安全模式
     * 期间、开关关闭期间）即使异常退出也与我们无关，不能计入崩溃队列 ——
     * 否则开机阶段 SystemUI 的常规重启会凭空凑够阈值，造成误判。
     */
    public static final String KEY_SESSION_HOOKED  = "safety_session_hooked";

    /** 连续崩溃阈值：连续 2 次即进入安全模式 */
    public static final int CRASH_THRESHOLD = 2;
    /** 连续崩溃判定窗口：5 分钟内的崩溃才算「连续」 */
    public static final long CRASH_WINDOW_MS = 5 * 60 * 1000L;
    /** 只有启动后 3 分钟内异常结束的会话，才算「启动期崩溃」 */
    public static final long SESSION_MAX_MS = 3 * 60 * 1000L;
    /** 存活确认延迟：SystemUI 启动 15 秒未崩溃即视为本次 Hook 安装成功 */
    public static final long HEARTBEAT_DELAY_MS = 15 * 1000L;

    private static final int CRASH_LOG_MAX = 12;

    private SafetyStore() {}

    // ──────────────────────────────────────────────────────────────
    // 对外状态机接口（全部由 SettingsProvider 在模块 App 进程内调用）
    // ──────────────────────────────────────────────────────────────

    /**
     * SystemUI 每次启动时调用。
     * 内部判定上一次会话是否异常结束（信号 A），维护崩溃队列，并给出本次是否允许安装 Hook。
     *
     * 本次会话一律先登记为「未装 Hook」；只有调用方随后真的要安装 Hook 时，
     * 才由 {@link #markSessionHooked} 把它升级为「Hook 会话」。这样即使本次因安全模式
     * 或开关关闭而没有安装 Hook，异常退出也不会被归因到 Hook 头上。
     */
    public static Bundle safetyCheck(Context ctx) {
        synchronized (LOCK) {
            SharedPreferences p = prefs(ctx);
            long now = System.currentTimeMillis();

            long prevStart = p.getLong(KEY_SESSION_START, 0L);
            boolean prevHealthy = p.getBoolean(KEY_SESSION_HEALTHY, true);
            boolean prevReported = p.getBoolean(KEY_SESSION_REPORTED, false);
            boolean prevHooked = p.getBoolean(KEY_SESSION_HOOKED, false);

            // 只有「上一会话确实装了 Hook」「没在运行期即时上报过崩溃（去重）」
            // 「没来得及确认存活」「且在启动窗口内结束」这四个条件同时成立，
            // 才判定为 Hook 引发的启动期崩溃。
            boolean prematureExit = prevStart > 0L
                && prevHooked
                && !prevReported
                && !prevHealthy
                && (now - prevStart) <= SESSION_MAX_MS;

            List<Long> times = prune(p, now);
            int total = p.getInt(KEY_TOTAL_CRASH, 0);
            List<String> log = readLog(p);

            if (prematureExit) {
                times.add(now);
                total++;
                log.add(stamp(now) + "  [启动期异常退出] Hook 安装后 SystemUI 未确认存活即崩溃"
                    + "（存活时长 " + ((now - prevStart) / 1000L) + " 秒）");
                Log.e(TAG, "检测到启动期异常退出，崩溃队列 = " + times.size() + " 次");
            }

            boolean safeMode = p.getBoolean(KEY_SAFE_MODE, false);
            boolean hookEnabled = p.getBoolean(KEY_HOOK_ENABLED, true);
            boolean justTriggered = false;
            if (times.size() >= CRASH_THRESHOLD && !safeMode) {
                safeMode = true;
                justTriggered = true;
                log.add(stamp(now) + "  → 连续崩溃达阈值 " + CRASH_THRESHOLD + " 次，自动进入安全模式，全部 Hook 已停用");
                Log.e(TAG, "!!! 连续崩溃 " + times.size() + " 次，自动进入安全模式，停用 Hook !!!");
            }

            p.edit()
                .putLong(KEY_SESSION_START, now)
                .putBoolean(KEY_SESSION_HEALTHY, false)
                .putBoolean(KEY_SESSION_REPORTED, false)
                .putBoolean(KEY_SESSION_HOOKED, false)
                .putString(KEY_CRASH_TIMES, join(times))
                .putInt(KEY_TOTAL_CRASH, total)
                .putBoolean(KEY_SAFE_MODE, safeMode)
                .putString(KEY_CRASH_LOG, joinLog(log))
                .apply();

            applyWorldReadable(ctx);

            Bundle b = buildState(p, times, safeMode, total, log);
            b.putBoolean("allowed", !safeMode && hookEnabled);
            b.putBoolean("just_triggered", justTriggered);
            b.putBoolean("premature_exit", prematureExit);
            return b;
        }
    }

    /**
     * 由 SystemUI 在「确实要安装 Hook」的那一刻调用，把本次会话标记为 Hook 会话。
     * 只有被标记过的会话，其异常退出才会被计入崩溃队列。
     */
    public static Bundle markSessionHooked(Context ctx) {
        synchronized (LOCK) {
            SharedPreferences p = prefs(ctx);
            p.edit()
                .putBoolean(KEY_SESSION_HOOKED, true)
                .putBoolean(KEY_SESSION_HEALTHY, false)
                .putBoolean(KEY_SESSION_REPORTED, false)
                .apply();
            applyWorldReadable(ctx);
            Log.i(TAG, "本次 SystemUI 会话已登记为 Hook 会话");
            return getStateLocked(p);
        }
    }

    /**
     * SystemUI 运行期由崩溃哨兵上报一次 Hook 相关崩溃（信号 B）。
     */
    public static Bundle reportCrash(Context ctx, String reason) {
        synchronized (LOCK) {
            SharedPreferences p = prefs(ctx);
            long now = System.currentTimeMillis();

            List<Long> times = prune(p, now);
            times.add(now);
            int total = p.getInt(KEY_TOTAL_CRASH, 0) + 1;

            boolean safeMode = p.getBoolean(KEY_SAFE_MODE, false);
            boolean justTriggered = false;
            if (times.size() >= CRASH_THRESHOLD && !safeMode) {
                safeMode = true;
                justTriggered = true;
            }

            List<String> log = readLog(p);
            log.add(stamp(now) + "  [Hook 相关未捕获异常] " + (reason == null ? "未知崩溃" : reason));
            if (justTriggered) {
                log.add(stamp(now) + "  → 连续崩溃达阈值 " + CRASH_THRESHOLD + " 次，自动进入安全模式，全部 Hook 已停用");
                Log.e(TAG, "!!! 连续崩溃 " + times.size() + " 次，自动进入安全模式，停用 Hook !!!");
            }

            p.edit()
                .putString(KEY_CRASH_TIMES, join(times))
                .putInt(KEY_TOTAL_CRASH, total)
                .putBoolean(KEY_SAFE_MODE, safeMode)
                .putBoolean(KEY_SESSION_REPORTED, true)
                .putString(KEY_CRASH_LOG, joinLog(log))
                .apply();

            applyWorldReadable(ctx);

            Bundle b = buildState(p, times, safeMode, total, log);
            b.putBoolean("allowed", !safeMode && p.getBoolean(KEY_HOOK_ENABLED, true));
            b.putBoolean("just_triggered", justTriggered);
            b.putBoolean("premature_exit", false);
            return b;
        }
    }

    /**
     * SystemUI 启动 HEARTBEAT_DELAY_MS 后上报存活确认，标记本次 Hook 安装成功。
     */
    public static Bundle heartbeat(Context ctx) {
        synchronized (LOCK) {
            SharedPreferences p = prefs(ctx);
            p.edit().putBoolean(KEY_SESSION_HEALTHY, true).apply();
            applyWorldReadable(ctx);
            Log.i(TAG, "已收到 SystemUI 存活确认，本次 Hook 安装判定为成功");
            return getStateLocked(p);
        }
    }

    /** 只读查询当前状态。 */
    public static Bundle getState(Context ctx) {
        synchronized (LOCK) {
            return getStateLocked(prefs(ctx));
        }
    }

    /** 退出安全模式：清除连续崩溃计数并重新启用 Hook。 */
    public static Bundle exitSafeMode(Context ctx) {
        synchronized (LOCK) {
            SharedPreferences p = prefs(ctx);
            long now = System.currentTimeMillis();
            List<String> log = readLog(p);
            log.add(stamp(now) + "  [手动操作] 用户退出安全模式，连续崩溃计数已清空，Hook 重新启用");

            p.edit()
                .putBoolean(KEY_SAFE_MODE, false)
                .putBoolean(KEY_HOOK_ENABLED, true)
                .putString(KEY_CRASH_TIMES, "")
                .putLong(KEY_SESSION_START, 0L)
                .putBoolean(KEY_SESSION_HEALTHY, true)
                .putBoolean(KEY_SESSION_REPORTED, false)
                .putBoolean(KEY_SESSION_HOOKED, false)
                .putString(KEY_CRASH_LOG, joinLog(log))
                .apply();

            applyWorldReadable(ctx);
            Log.i(TAG, "已退出安全模式");
            return getStateLocked(p);
        }
    }

    /** 仅清空崩溃记录，不改变安全模式开关。 */
    public static Bundle clearCrash(Context ctx) {
        synchronized (LOCK) {
            SharedPreferences p = prefs(ctx);
            List<String> log = readLog(p);
            log.add(stamp(System.currentTimeMillis()) + "  [手动操作] 崩溃记录已清空");

            p.edit()
                .putString(KEY_CRASH_TIMES, "")
                .putString(KEY_CRASH_LOG, joinLog(log))
                .putLong(KEY_SESSION_START, 0L)
                .putBoolean(KEY_SESSION_HEALTHY, true)
                .putBoolean(KEY_SESSION_REPORTED, false)
                .putBoolean(KEY_SESSION_HOOKED, false)
                .apply();

            applyWorldReadable(ctx);
            return getStateLocked(p);
        }
    }

    /** 设置 Hook 总开关；关闭时不清空计数，开启时若处于安全模式则先退出安全模式。 */
    public static Bundle setHookEnabled(Context ctx, boolean enabled) {
        synchronized (LOCK) {
            SharedPreferences p = prefs(ctx);
            SharedPreferences.Editor e = p.edit().putBoolean(KEY_HOOK_ENABLED, enabled);
            if (enabled) {
                e.putBoolean(KEY_SAFE_MODE, false)
                 .putString(KEY_CRASH_TIMES, "")
                 .putLong(KEY_SESSION_START, 0L)
                 .putBoolean(KEY_SESSION_HEALTHY, true)
                 .putBoolean(KEY_SESSION_REPORTED, false)
                 .putBoolean(KEY_SESSION_HOOKED, false);
            }
            e.apply();
            applyWorldReadable(ctx);
            Log.i(TAG, "Hook 总开关已设置为: " + enabled);
            try {
                android.content.Intent notify = new android.content.Intent(HyperOpenConfig.ACTION_CONFIG_CHANGED);
                notify.setPackage("com.android.systemui");
                ctx.sendBroadcast(notify);
            } catch (Throwable ignored) {}
            return getStateLocked(p);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 内部实现
    // ──────────────────────────────────────────────────────────────

    private static SharedPreferences prefs(Context ctx) {
        // Direct Boot：安全状态必须存 DE（设备加密）存储，
        // 否则开机锁定期 safety_check 读不到 → 模块误判/失效
        return io.github.qmryqmm.hyperopen.util.DePrefs.get(ctx, HyperOpenConfig.PREF_NAME);
    }

    private static Bundle getStateLocked(SharedPreferences p) {
        long now = System.currentTimeMillis();
        List<Long> times = prune(p, now);
        boolean safeMode = p.getBoolean(KEY_SAFE_MODE, false);
        int total = p.getInt(KEY_TOTAL_CRASH, 0);
        List<String> log = readLog(p);

        Bundle b = buildState(p, times, safeMode, total, log);
        b.putBoolean("allowed", !safeMode && p.getBoolean(KEY_HOOK_ENABLED, true));
        b.putBoolean("just_triggered", false);
        b.putBoolean("premature_exit", false);
        return b;
    }

    private static Bundle buildState(SharedPreferences p, List<Long> times,
                                     boolean safeMode, int total, List<String> log) {
        Bundle b = new Bundle();
        b.putBoolean("safe_mode", safeMode);
        b.putBoolean("hook_enabled", p.getBoolean(KEY_HOOK_ENABLED, true));
        b.putInt("crash_count", times.size());
        b.putInt("total_crash", total);
        b.putInt("threshold", CRASH_THRESHOLD);
        b.putLong("window_ms", CRASH_WINDOW_MS);
        b.putLong("last_crash_ts", times.isEmpty() ? 0L : times.get(times.size() - 1));
        b.putString("crash_log", joinLog(log));
        b.putBoolean("session_healthy", p.getBoolean(KEY_SESSION_HEALTHY, true));
        return b;
    }

    private static List<Long> prune(SharedPreferences p, long now) {
        List<Long> out = new ArrayList<>();
        for (Long t : parseLongs(p.getString(KEY_CRASH_TIMES, ""))) {
            if (now - t <= CRASH_WINDOW_MS) {
                out.add(t);
            }
        }
        return out;
    }

    private static List<Long> parseLongs(String s) {
        List<Long> out = new ArrayList<>();
        if (s == null || s.isEmpty()) {
            return out;
        }
        for (String part : s.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            try {
                out.add(Long.parseLong(t));
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private static String join(List<Long> times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(times.get(i));
        }
        return sb.toString();
    }

    private static List<String> readLog(SharedPreferences p) {
        List<String> out = new ArrayList<>();
        String raw = p.getString(KEY_CRASH_LOG, "");
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String line : raw.split("\n")) {
            if (!line.trim().isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private static String joinLog(List<String> log) {
        while (log.size() > CRASH_LOG_MAX) {
            log.remove(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < log.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(log.get(i));
        }
        return sb.toString();
    }

    private static String stamp(long ts) {
        return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(ts));
    }

    /**
     * 保持 DE prefs 文件与目录对其它进程可读，作为 XSharedPreferences 兜底路径的前提。
     */
    static void applyWorldReadable(Context ctx) {
        // prefs 已迁 DE，可读化同步作用于 DE 路径
        io.github.qmryqmm.hyperopen.util.DePrefs.applyWorldReadable(ctx, HyperOpenConfig.PREF_NAME);
    }
}
