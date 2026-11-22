package io.github.qmryqmm.hyperopen.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;
import android.util.Log;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Direct Boot（开机加密锁定）支持：全部持久化 SharedPreferences 统一落在设备加密（DE）存储。
 *
 * 为什么必须在 DE：
 * SystemUI 随开机启动时用户往往尚未解锁，此时凭据加密（CE）存储不可用；
 * 若配置/安全模式状态存在 CE，锁定期 {@code safety_check} 无法完成，
 * 要么模块放弃安装 Hook，要么被兜底逻辑误判成「安全模式激活」，
 * 表现为「重启后模块不生效，必须打开 App 手动重启系统界面」。
 * 迁到 DE 后锁定期即可正常读写，开机即生效。
 *
 * 一次性迁移（CE → DE）：
 * 旧版本数据在 CE。首次由任意入口触发（Provider.onCreate / Provider.call /
 * MainActivity.onCreate），把 CE 全部键值搬到 DE 并打迁移标记。
 * 关键约束：只有确认 CE 可读（用户已解锁）才允许打标记，
 * 否则锁定期「读不到 CE」会被误当成「已迁移」，用户数据即永久丢失。
 */
public final class DePrefs {
    private static final String TAG = "HyperOpen_DePrefs";

    /** 迁移完成标记（写在 DE prefs 里） */
    public static final String MIGRATION_MARK = "__de_migrated_v1";

    /**
     * 会话瞬态键：仅对「当前这一次 SystemUI 会话」有意义，跨重启即失效，不得迁移。
     * 若把 CE 里的旧会话状态搬到 DE，会覆盖锁定期刚写入的新会话状态，
     * 极端时序（解锁后数分钟内再次重启）下会被启动期崩溃启发式误判成一次崩溃。
     * 常量名与 {@code safety.SafetyStore} 的 KEY_* 对应。
     */
    private static final Set<String> TRANSIENT_KEYS = new HashSet<>(java.util.Arrays.asList(
        "safety_session_start",
        "safety_session_healthy",
        "safety_session_reported",
        "safety_session_hooked"
    ));

    /** 进程内已确认迁移完成的 prefs 名，避免每次 IPC 都重复检查 */
    private static final Set<String> MIGRATED = new HashSet<>();

    private DePrefs() {}

    /** 设备加密（DE）存储上下文 */
    public static Context deContext(Context ctx) {
        return ctx.createDeviceProtectedStorageContext();
    }

    /** 统一的 DE SharedPreferences 读写入口 */
    public static SharedPreferences get(Context ctx, String name) {
        return deContext(ctx).getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    /**
     * 一次性把 CE prefs 迁移到 DE（幂等；CE 不可读时本次跳过，下次入口继续尝试）。
     */
    public static synchronized void migrateCeToDe(Context ctx, String... names) {
        for (String name : names) {
            if (MIGRATED.contains(name)) {
                continue;
            }
            migrateOne(ctx, name);
        }
    }

    private static void migrateOne(Context ctx, String name) {
        try {
            SharedPreferences de = get(ctx, name);
            if (de.getBoolean(MIGRATION_MARK, false)) {
                MIGRATED.add(name);
                return;
            }

            // 用户未解锁：CE 绝不可读，本次不迁移、更不能打标记
            try {
                UserManager um = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
                if (um != null && !um.isUserUnlocked()) {
                    Log.w(TAG, "用户尚未解锁，CE→DE 迁移暂缓: " + name);
                    return;
                }
            } catch (Throwable ignored) {}

            SharedPreferences ce;
            try {
                // 默认 Context 即 CE（凭据加密）存储上下文；锁定时读取会抛异常，由 catch 兜住
                ce = ctx.getSharedPreferences(name, Context.MODE_PRIVATE);
                ce.getAll(); // 触发真实读取：锁定/损坏在此暴露
            } catch (Throwable t) {
                Log.w(TAG, "CE 数据暂不可读，迁移暂缓: " + name + " (" + t + ")");
                return;
            }

            Map<String, ?> all = ce.getAll();
            SharedPreferences.Editor ed = de.edit();
            if (all != null) {
                for (Map.Entry<String, ?> e : all.entrySet()) {
                    String k = e.getKey();
                    if (k == null || MIGRATION_MARK.equals(k) || TRANSIENT_KEYS.contains(k) || k.startsWith("hook_")) {
                        // 会话瞬态键与 hook_* 状态键不迁移：CE 旧状态不能覆盖锁定期写入的新状态
                        continue;
                    }
                    if (de.contains(k)) {
                        // 仅在 DE 尚无该键时迁移，绝不覆盖锁定期或当前会话已写入的 DE 新值
                        continue;
                    }
                    Object v = e.getValue();
                    if (v instanceof Boolean) {
                        ed.putBoolean(k, (Boolean) v);
                    } else if (v instanceof Float) {
                        ed.putFloat(k, (Float) v);
                    } else if (v instanceof Integer) {
                        ed.putInt(k, (Integer) v);
                    } else if (v instanceof Long) {
                        ed.putLong(k, (Long) v);
                    } else if (v instanceof String) {
                        ed.putString(k, (String) v);
                    } else if (v instanceof Set) {
                        try {
                            @SuppressWarnings("unchecked")
                            Set<String> s = (Set<String>) v;
                            ed.putStringSet(k, s);
                        } catch (Throwable ignored) {}
                    }
                }
            }
            ed.putBoolean(MIGRATION_MARK, true);
            boolean committed = ed.commit();
            applyWorldReadable(ctx, name);
            if (committed) {
                MIGRATED.add(name);
            }
            Log.i(TAG, "CE→DE 迁移完成: " + name + "（" + (all == null ? 0 : all.size()) + " 个键）");
        } catch (Throwable t) {
            Log.w(TAG, "CE→DE 迁移失败（下次入口重试）: " + name, t);
        }
    }

    /** XSharedPreferences 兜底用：DE prefs 文件路径 */
    public static File prefsFile(Context ctx, String name) {
        return new File(new File(deContext(ctx).getDataDir(), "shared_prefs"), name + ".xml");
    }

    /**
     * 保持 DE prefs 目录与文件对其它进程可读，作为 XSharedPreferences 兜底路径的前提。
     */
    public static void applyWorldReadable(Context ctx, String name) {
        try {
            File dataDir = deContext(ctx).getDataDir();
            dataDir.setReadable(true, false);
            dataDir.setExecutable(true, false);
            File prefsDir = new File(dataDir, "shared_prefs");
            if (prefsDir.exists()) {
                prefsDir.setReadable(true, false);
                prefsDir.setExecutable(true, false);
            }
            File prefsFile = new File(prefsDir, name + ".xml");
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false);
            }
        } catch (Throwable ignored) {}
    }
}
