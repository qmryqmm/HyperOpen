package io.github.qmryqmm.hyperopen.log;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 快捷方式启动日志持久化存储（独立 DE 文件，不污染 hyper_open_settings.xml）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>存储位置：{@code deCtx.getFilesDir()/shortcut_launch_logs.json}，支持 Direct Boot 锁定期读写。</li>
 *   <li>写入方：仅模块 App 进程写入（SystemUI 侧通过 {@link #recordFromAnyProcess} 异步桥接到 {@code SettingsProvider}）。</li>
 *   <li>容量上限：最多保留最新 {@link #MAX_ENTRIES} (100) 条，按时间倒序排列，超出截断尾部。</li>
 *   <li>异常隔离：任何 IO/JSON/Binder 异常全部内部消化，绝不影响 SystemUI 或宿主进程稳定性。</li>
 * </ul>
 */
public final class ShortcutLaunchLogStore {
    private static final String TAG = "HyperOpen_LaunchLog";
    private static final String FILE_NAME = "shortcut_launch_logs.json";
    private static final String OWN_PACKAGE = "io.github.qmryqmm.hyperopen";
    private static final Uri PROVIDER_URI = Uri.parse("content://io.github.qmryqmm.hyperopen.provider");
    private static final String METHOD_RECORD_LAUNCH_LOG = "record_launch_log";

    public static final int MAX_ENTRIES = 100;
    public static final int MAX_PARAMS_LEN = 2048;
    public static final int MAX_ERROR_LEN = 2048;
    public static final int MAX_OUTPUT_LEN = 1024;

    public static final String SOURCE_LOCKSCREEN = "lockscreen";
    public static final String SOURCE_PREVIEW = "preview";
    public static final String SOURCE_SANDBOX_PREVIEW = "sandbox_preview";
    public static final String SOURCE_EDIT_TEST = "edit_test";
    public static final String SOURCE_MARKET_TEST = "market_test";
    public static final String SOURCE_UNKNOWN = "unknown";

    private static final Object LOCK = new Object();

    private ShortcutLaunchLogStore() {}

    /** 单条启动日志实体 */
    public static final class Entry {
        public String id;
        public long timestamp;
        public long durationMs;
        public String source;
        public String shortcutTitle;
        public String actionType;
        public boolean useRoot;
        public int userId;
        public String params;
        public boolean success;
        public String errorMessage;
        public String outputSnippet;

        public Entry() {
            this.id = "";
            this.source = SOURCE_UNKNOWN;
            this.shortcutTitle = "";
            this.actionType = "";
            this.params = "";
            this.errorMessage = "";
            this.outputSnippet = "";
        }

        /** 规范化默认值与字段长度上限 */
        public void normalize() {
            if (id == null || id.isEmpty()) {
                id = UUID.randomUUID().toString();
            }
            if (timestamp <= 0L) {
                timestamp = System.currentTimeMillis();
            }
            if (durationMs < 0L) {
                durationMs = 0L;
            }
            if (source == null || source.isEmpty()) {
                source = SOURCE_UNKNOWN;
            }
            if (shortcutTitle == null) {
                shortcutTitle = "";
            }
            if (actionType == null) {
                actionType = "";
            }
            params = truncate(params, MAX_PARAMS_LEN);
            errorMessage = truncate(errorMessage, MAX_ERROR_LEN);
            outputSnippet = truncate(outputSnippet, MAX_OUTPUT_LEN);
        }

        public JSONObject toJson() {
            normalize();
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("timestamp", timestamp);
                obj.put("durationMs", durationMs);
                obj.put("source", source);
                obj.put("shortcutTitle", shortcutTitle);
                obj.put("actionType", actionType);
                obj.put("useRoot", useRoot);
                obj.put("userId", userId);
                obj.put("params", params);
                obj.put("success", success);
                obj.put("errorMessage", errorMessage);
                obj.put("outputSnippet", outputSnippet);
            } catch (Throwable t) {
                Log.w(TAG, "序列化启动日志 JSON 失败", t);
            }
            return obj;
        }

        public static Entry fromJson(JSONObject obj) {
            if (obj == null) {
                return null;
            }
            Entry e = new Entry();
            e.id = obj.optString("id", "");
            e.timestamp = obj.optLong("timestamp", 0L);
            e.durationMs = obj.optLong("durationMs", 0L);
            e.source = obj.optString("source", SOURCE_UNKNOWN);
            e.shortcutTitle = obj.optString("shortcutTitle", "");
            e.actionType = obj.optString("actionType", "");
            e.useRoot = obj.optBoolean("useRoot", false);
            e.userId = obj.optInt("userId", 0);
            e.params = truncate(obj.optString("params", ""), MAX_PARAMS_LEN);
            e.success = obj.optBoolean("success", false);
            e.errorMessage = truncate(obj.optString("errorMessage", ""), MAX_ERROR_LEN);
            e.outputSnippet = truncate(obj.optString("outputSnippet", ""), MAX_OUTPUT_LEN);
            e.normalize();
            return e;
        }

        public Bundle toBundle() {
            normalize();
            Bundle b = new Bundle();
            b.putString("id", id);
            b.putLong("timestamp", timestamp);
            b.putLong("durationMs", durationMs);
            b.putString("source", source);
            b.putString("shortcutTitle", shortcutTitle);
            b.putString("actionType", actionType);
            b.putBoolean("useRoot", useRoot);
            b.putInt("userId", userId);
            b.putString("params", params);
            b.putBoolean("success", success);
            b.putString("errorMessage", errorMessage);
            b.putString("outputSnippet", outputSnippet);
            return b;
        }

        public static Entry fromBundle(Bundle b) {
            if (b == null) {
                return null;
            }
            Entry e = new Entry();
            e.id = b.getString("id", "");
            e.timestamp = b.getLong("timestamp", 0L);
            e.durationMs = b.getLong("durationMs", 0L);
            e.source = b.getString("source", SOURCE_UNKNOWN);
            e.shortcutTitle = b.getString("shortcutTitle", "");
            e.actionType = b.getString("actionType", "");
            e.useRoot = b.getBoolean("useRoot", false);
            e.userId = b.getInt("userId", 0);
            e.params = truncate(b.getString("params", ""), MAX_PARAMS_LEN);
            e.success = b.getBoolean("success", false);
            e.errorMessage = truncate(b.getString("errorMessage", ""), MAX_ERROR_LEN);
            e.outputSnippet = truncate(b.getString("outputSnippet", ""), MAX_OUTPUT_LEN);
            e.normalize();
            return e;
        }

        /** 复制一份用于延迟或异步执行的日志模板 */
        public Entry copy() {
            Entry c = new Entry();
            c.id = this.id;
            c.timestamp = this.timestamp;
            c.durationMs = this.durationMs;
            c.source = this.source;
            c.shortcutTitle = this.shortcutTitle;
            c.actionType = this.actionType;
            c.useRoot = this.useRoot;
            c.userId = this.userId;
            c.params = this.params;
            c.success = this.success;
            c.errorMessage = this.errorMessage;
            c.outputSnippet = this.outputSnippet;
            return c;
        }
    }

    /** 日志汇总统计 */
    public static final class Summary {
        public int totalCount;
        public int failureCount;
        public long lastTimestamp;
        public boolean lastSuccess;
        public String lastTitle = "";
    }

    /** 在模块 App 进程内本地写入一条日志（最新在前，超出 100 条截断尾部） */
    public static void recordLocal(Context ctx, Entry entry) {
        if (ctx == null || entry == null) {
            return;
        }
        try {
            entry.normalize();
            synchronized (LOCK) {
                List<Entry> list = readListLocked(ctx);
                list.add(0, entry);
                if (list.size() > MAX_ENTRIES) {
                    list = new ArrayList<>(list.subList(0, MAX_ENTRIES));
                }
                writeListLocked(ctx, list);
            }
        } catch (Throwable t) {
            Log.w(TAG, "本地记录启动日志失败", t);
        }
    }

    /**
     * 从任意进程记录日志：
     * - 本 App 进程直接本地写入；
     * - SystemUI 等外部进程在后台守护线程通过 ContentProvider 静默上报，绝不阻塞调用线程。
     */
    public static void recordFromAnyProcess(Context ctx, Entry entry) {
        if (ctx == null || entry == null) {
            return;
        }
        try {
            entry.normalize();
            if (isOwnAppProcess(ctx)) {
                recordLocal(ctx, entry);
                return;
            }
            final Context appCtx = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
            final Bundle payload = entry.toBundle();
            Thread reporter = new Thread(() -> {
                try {
                    appCtx.getContentResolver().call(
                            PROVIDER_URI,
                            METHOD_RECORD_LAUNCH_LOG,
                            null,
                            payload
                    );
                } catch (Throwable t) {
                    Log.w(TAG, "跨进程上报启动日志失败: " + t.getMessage());
                }
            }, "HyperOpen-LaunchLogReport");
            reporter.setDaemon(true);
            reporter.start();
        } catch (Throwable t) {
            Log.w(TAG, "分发记录启动日志异常", t);
        }
    }

    /** 读取全部日志（按时间倒序） */
    public static List<Entry> loadAll(Context ctx) {
        if (ctx == null) {
            return Collections.emptyList();
        }
        try {
            synchronized (LOCK) {
                return readListLocked(ctx);
            }
        } catch (Throwable t) {
            Log.w(TAG, "读取启动日志失败", t);
            return Collections.emptyList();
        }
    }

    /** 获取日志汇总统计 */
    public static Summary getSummary(Context ctx) {
        Summary s = new Summary();
        List<Entry> list = loadAll(ctx);
        s.totalCount = list.size();
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            if (!e.success) {
                s.failureCount++;
            }
            if (i == 0) {
                s.lastTimestamp = e.timestamp;
                s.lastSuccess = e.success;
                s.lastTitle = e.shortcutTitle != null ? e.shortcutTitle : "";
            }
        }
        return s;
    }

    /** 清空全部启动日志 */
    public static void clear(Context ctx) {
        if (ctx == null) {
            return;
        }
        try {
            synchronized (LOCK) {
                File logFile = resolveLogFile(ctx);
                if (logFile != null && logFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    logFile.delete();
                }
                File tmpFile = resolveTmpFile(ctx);
                if (tmpFile != null && tmpFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tmpFile.delete();
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "清空启动日志失败", t);
        }
    }

    /** 动作类型中文展示标签 */
    public static String actionTypeDisplayLabel(String actionType, boolean useRoot) {
        if (actionType == null) {
            return "未知动作";
        }
        switch (actionType) {
            case "APP":
                return "启动应用";
            case "INTENT_URI":
                return useRoot ? "Intent URI (Root)" : "Intent URI";
            case "ACTIVITY":
                return useRoot ? "Activity (Root)" : "指定页面";
            case "BROADCAST":
                return "发送广播";
            case "FLASHLIGHT":
                return "手电筒";
            case "SHELL":
                return useRoot ? "Root 命令" : "Shell 命令";
            case "XIAOAI":
                return "唤醒小爱";
            case "MUTE":
                return useRoot ? "静音切换 (Root)" : "静音切换";
            case "DND":
                return useRoot ? "勿扰切换 (Root)" : "勿扰切换";
            case "CAMERA":
                return "系统相机";
            case "FOLDER":
                return "聚合文件夹";
            default:
                return actionType.isEmpty() ? "未知动作" : actionType;
        }
    }

    /** 触发来源中文展示标签 */
    public static String sourceDisplayLabel(String source) {
        if (source == null) {
            return "未知来源";
        }
        switch (source) {
            case SOURCE_LOCKSCREEN:
                return "锁屏面板";
            case SOURCE_PREVIEW:
                return "桌面预览";
            case SOURCE_SANDBOX_PREVIEW:
                return "沙箱预览";
            case SOURCE_EDIT_TEST:
                return "编辑测试";
            case SOURCE_MARKET_TEST:
                return "市场测试";
            case SOURCE_UNKNOWN:
            default:
                return "未知来源";
        }
    }

    private static boolean isOwnAppProcess(Context ctx) {
        try {
            return ctx != null && OWN_PACKAGE.equals(ctx.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxLen) {
            return s;
        }
        if (maxLen <= 3) {
            return s.substring(0, Math.max(0, maxLen));
        }
        return s.substring(0, maxLen - 3) + "...";
    }

    private static File resolveDeFilesDir(Context ctx) {
        Context target = ctx;
        try {
            Context deCtx = ctx.createDeviceProtectedStorageContext();
            if (deCtx != null) {
                target = deCtx;
            }
        } catch (Throwable ignored) {}
        File dir = target.getFilesDir();
        if (dir == null) {
            dir = ctx.getFilesDir();
        }
        if (dir != null && !dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    private static File resolveLogFile(Context ctx) {
        File dir = resolveDeFilesDir(ctx);
        return dir != null ? new File(dir, FILE_NAME) : null;
    }

    private static File resolveTmpFile(Context ctx) {
        File dir = resolveDeFilesDir(ctx);
        return dir != null ? new File(dir, FILE_NAME + ".tmp") : null;
    }

    private static List<Entry> readListLocked(Context ctx) {
        List<Entry> list = new ArrayList<>();
        File file = resolveLogFile(ctx);
        if (file == null || !file.exists() || file.length() <= 0L) {
            return list;
        }
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            String raw = new String(baos.toByteArray(), StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return list;
            }
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                Entry e = Entry.fromJson(obj);
                if (e != null) {
                    list.add(e);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "解析启动日志文件失败，返回空列表", t);
            return new ArrayList<>();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Throwable ignored) {}
            }
        }
        return list;
    }

    private static void writeListLocked(Context ctx, List<Entry> list) {
        File logFile = resolveLogFile(ctx);
        File tmpFile = resolveTmpFile(ctx);
        if (logFile == null || tmpFile == null) {
            return;
        }
        JSONArray arr = new JSONArray();
        for (int i = 0; i < list.size(); i++) {
            arr.put(list.get(i).toJson());
        }
        byte[] bytes = arr.toString().getBytes(StandardCharsets.UTF_8);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(tmpFile, false);
            fos.write(bytes);
            fos.flush();
            try {
                fos.getFD().sync();
            } catch (Throwable ignored) {}
            fos.close();
            fos = null;

            if (!tmpFile.renameTo(logFile)) {
                if (logFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    logFile.delete();
                }
                if (!tmpFile.renameTo(logFile)) {
                    Log.w(TAG, "原子替换启动日志文件失败");
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "写入启动日志文件失败", t);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Throwable ignored) {}
            }
        }
    }
}
