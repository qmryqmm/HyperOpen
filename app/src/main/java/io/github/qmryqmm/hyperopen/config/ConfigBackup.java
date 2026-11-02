package io.github.qmryqmm.hyperopen.config;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.qmryqmm.hyperopen.model.ShortcutItem;
import io.github.qmryqmm.hyperopen.util.DePrefs;

/**
 * 配置导入 / 导出（JSON）。
 *
 * <p>导出的是「用户配置」本身，不含任何运行时状态：
 * <ul>
 *   <li>{@code safety_*} 是崩溃保护 / 安全模式的会话状态，跨设备或跨时间导入会误导启用判定；</li>
 *   <li>{@code last_real_fod_*} 是本机指纹传感器实测坐标，换机型必须是另一组值（SystemUI 展开时会自己写回）；</li>
 *   <li>{@code __de_migrated_v1} 是存储迁移标记。</li>
 * </ul>
 *
 * <p>写盘格式：{@code values} 里逐键带类型标记（b/i/f/l/s/ss）。SharedPreferences 的
 * getInt / getFloat 对类型敏感，只靠 JSON number 无法区分 100 和 100.0，标记类型后
 * 回写才不会踩 ClassCastException。{@code shortcuts} 单独提成结构化数组，方便手工编辑与分享。
 *
 * <p>导入采用「合并覆盖」而不是「先清空再写」：文件里没有的键保持原值，避免旧版配置文件
 * 把后来新增的配置项一把清空。
 */
public final class ConfigBackup {

    private static final String TAG = "HyperOpen_Backup";

    public static final String FORMAT = "hyperopen-config";
    public static final int FORMAT_VERSION = 1;

    /** 单次导入的文件大小上限，防止误选大文件把进程撑爆。 */
    private static final int MAX_IMPORT_BYTES = 4 * 1024 * 1024;

    private static final String KEY_SHORTCUTS = "shortcuts_json";

    /** 运行时状态：既不出现在导出文件里，也不会被导入覆盖。 */
    private static final String SKIP_PREFIX = "safety_";
    private static final Set<String> SKIP_KEYS = new HashSet<>(java.util.Arrays.asList(
            "last_real_fod_x",
            "last_real_fod_y",
            DePrefs.MIGRATION_MARK
    ));

    private ConfigBackup() {
    }

    /** 导入 / 预检结果。 */
    public static final class Result {
        public final boolean ok;
        public final String message;
        public final int keyCount;
        public final int shortcutCount;
        public final String appVersion;

        Result(boolean ok, String message, int keyCount, int shortcutCount, String appVersion) {
            this.ok = ok;
            this.message = message;
            this.keyCount = keyCount;
            this.shortcutCount = shortcutCount;
            this.appVersion = appVersion;
        }

        public static Result fail(String message) {
            return new Result(false, message, 0, 0, null);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 导出
    // ──────────────────────────────────────────────────────────────

    /** 把当前配置序列化成 JSON 文本。 */
    public static String buildExportJson(Context ctx) throws Exception {
        SharedPreferences sp = DePrefs.get(ctx, HyperOpenConfig.PREF_NAME);
        Map<String, ?> all = sp.getAll();

        // 键名排序，让导出文件对人有稳定的阅读顺序
        List<String> keys = new ArrayList<>();
        for (String k : all.keySet()) {
            if (isSkipped(k)) continue;
            keys.add(k);
        }
        Collections.sort(keys);

        JSONObject values = new JSONObject();
        for (String k : keys) {
            Object v = all.get(k);
            if (KEY_SHORTCUTS.equals(k)) {
                // shortcuts 走下面的结构化字段，不再塞回 values，避免同一份数据出现两次
                continue;
            }
            JSONObject item = encodeValue(v);
            if (item != null) {
                values.put(k, item);
            }
        }

        JSONArray shortcuts = new JSONArray();
        List<ShortcutItem> items = HyperOpenConfig.parseShortcutsJson(sp.getString(KEY_SHORTCUTS, null));
        for (ShortcutItem it : items) {
            shortcuts.put(it.toJson());
        }

        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("version", FORMAT_VERSION);
        root.put("appVersion", versionName(ctx));
        root.put("exportedAt", System.currentTimeMillis());
        root.put("exportedAtText", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        root.put("values", values);
        root.put("shortcuts", shortcuts);
        return root.toString(2);
    }

    /** 建议的导出文件名，例如 hyperopen-0.34-20261028-2350.json */
    public static String suggestedFileName(Context ctx) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
        return "hyperopen-" + versionName(ctx) + "-" + stamp + ".json";
    }

    private static JSONObject encodeValue(Object v) throws Exception {
        JSONObject item = new JSONObject();
        if (v instanceof Boolean) {
            item.put("t", "b");
            item.put("v", v);
        } else if (v instanceof Integer) {
            item.put("t", "i");
            item.put("v", v);
        } else if (v instanceof Float) {
            item.put("t", "f");
            item.put("v", ((Float) v).doubleValue());
        } else if (v instanceof Long) {
            item.put("t", "l");
            item.put("v", v);
        } else if (v instanceof String) {
            item.put("t", "s");
            item.put("v", v);
        } else if (v instanceof Set) {
            JSONArray arr = new JSONArray();
            for (Object o : (Set<?>) v) {
                arr.put(String.valueOf(o));
            }
            item.put("t", "ss");
            item.put("v", arr);
        } else {
            Log.w(TAG, "跳过无法序列化的配置项类型: " + (v == null ? "null" : v.getClass().getName()));
            return null;
        }
        return item;
    }

    // ──────────────────────────────────────────────────────────────
    // 导入
    // ──────────────────────────────────────────────────────────────

    /** 只解析校验，不落盘。用于弹确认框前先判断文件是否可用。 */
    public static Result preview(String json) {
        try {
            JSONObject root = new JSONObject(json);
            String format = root.optString("format", "");
            if (!FORMAT.equals(format)) {
                return Result.fail("不是 HyperOpen 的配置文件");
            }
            int version = root.optInt("version", 0);
            if (version <= 0) {
                return Result.fail("配置文件缺少版本号");
            }
            if (version > FORMAT_VERSION) {
                return Result.fail("配置文件版本（v" + version + "）高于当前支持的 v" + FORMAT_VERSION + "，请升级模块");
            }
            JSONObject values = root.optJSONObject("values");
            if (values == null) {
                return Result.fail("配置文件缺少 values 字段");
            }
            int count = 0;
            for (Iterator<String> it = values.keys(); it.hasNext(); ) {
                String k = it.next();
                if (isSkipped(k)) continue;
                if (values.optJSONObject(k) != null) count++;
            }
            int shortcutCount = 0;
            JSONArray shortcuts = root.optJSONArray("shortcuts");
            if (shortcuts != null) {
                shortcutCount = shortcuts.length();
            } else {
                // 兼容手写 / 早期格式：values 里直接带 shortcuts_json 字符串
                JSONObject sj = values.optJSONObject(KEY_SHORTCUTS);
                if (sj != null) {
                    try {
                        shortcutCount = new JSONArray(sj.optString("v", "")).length();
                    } catch (Throwable ignored) {
                    }
                }
            }
            return new Result(true, "可导入", count, shortcutCount, root.optString("appVersion", "?"));
        } catch (Throwable t) {
            Log.w(TAG, "解析配置失败", t);
            return Result.fail("文件解析失败：" + t.getMessage());
        }
    }

    /**
     * 应用一份配置：按类型写回 prefs，然后重新开放读权限并通知 SystemUI 热重载。
     *
     * @return ok=true 时调用方应重建界面（App 内的 compose 状态是启动时一次性快照的）。
     */
    public static Result apply(Context ctx, String json) {
        try {
            Result pre = preview(json);
            if (!pre.ok) {
                return pre;
            }
            JSONObject root = new JSONObject(json);
            JSONObject values = root.getJSONObject("values");

            SharedPreferences sp = DePrefs.get(ctx, HyperOpenConfig.PREF_NAME);
            SharedPreferences.Editor editor = sp.edit();

            int count = 0;
            for (Iterator<String> it = values.keys(); it.hasNext(); ) {
                String k = it.next();
                if (isSkipped(k)) continue;
                JSONObject item = values.optJSONObject(k);
                if (item == null) continue;
                String type = item.optString("t", "");
                Object raw = item.opt("v");
                switch (type) {
                    case "b":
                        editor.putBoolean(k, item.optBoolean("v"));
                        break;
                    case "i":
                        editor.putInt(k, item.optInt("v"));
                        break;
                    case "f":
                        editor.putFloat(k, (float) item.optDouble("v"));
                        break;
                    case "l":
                        editor.putLong(k, item.optLong("v"));
                        break;
                    case "s":
                        editor.putString(k, raw == null ? "" : String.valueOf(raw));
                        break;
                    case "ss": {
                        JSONArray arr = item.optJSONArray("v");
                        Set<String> set = new HashSet<>();
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                set.add(arr.optString(i));
                            }
                        }
                        editor.putStringSet(k, set);
                        break;
                    }
                    default:
                        Log.w(TAG, "未知的配置项类型标记，跳过: " + k + " t=" + type);
                        continue;
                }
                count++;
            }

            // 快捷方式：优先用结构化的 shortcuts 数组，回退到 values 里的 shortcuts_json
            int shortcutCount = 0;
            JSONArray shortcuts = root.optJSONArray("shortcuts");
            if (shortcuts != null) {
                List<ShortcutItem> items = new ArrayList<>();
                for (int i = 0; i < shortcuts.length(); i++) {
                    JSONObject obj = shortcuts.optJSONObject(i);
                    if (obj != null) {
                        items.add(ShortcutItem.fromJson(obj));
                    }
                }
                if (!items.isEmpty()) {
                    editor.putString("shortcuts_json", HyperOpenConfig.serializeShortcutsJson(items));
                    shortcutCount = items.size();
                }
            }

            editor.apply();

            // 与 HyperOpenConfig.save() 保持一致：DE 目录必须可读，SystemUI 才能用
            // XSharedPreferences 兜底；再发广播让运行中的 SystemUI 立刻重载。
            DePrefs.applyWorldReadable(ctx, HyperOpenConfig.PREF_NAME);
            try {
                Intent intent = new Intent(HyperOpenConfig.ACTION_CONFIG_CHANGED);
                intent.setPackage("com.android.systemui");
                ctx.sendBroadcast(intent);
            } catch (Throwable ignored) {
            }

            Log.i(TAG, "配置导入完成: " + count + " 项参数, " + shortcutCount + " 个快捷方式");
            return new Result(true, "导入完成", count, shortcutCount, root.optString("appVersion", "?"));
        } catch (Throwable t) {
            Log.e(TAG, "导入配置失败", t);
            return Result.fail("导入失败：" + t.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 文件读写（SAF Uri）
    // ──────────────────────────────────────────────────────────────

    public static void writeTo(Context ctx, Uri uri, String text) throws Exception {
        try (OutputStream os = ctx.getContentResolver().openOutputStream(uri, "wt")) {
            if (os == null) {
                throw new IllegalStateException("无法打开目标文件");
            }
            os.write(text.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
    }

    public static String readFrom(Context ctx, Uri uri) throws Exception {
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                throw new IllegalStateException("无法读取所选文件");
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = is.read(buf)) > 0) {
                total += n;
                if (total > MAX_IMPORT_BYTES) {
                    throw new IllegalStateException("文件超过 " + (MAX_IMPORT_BYTES / 1024 / 1024) + " MB，不像是配置文件");
                }
                bos.write(buf, 0, n);
            }
            return bos.toString(StandardCharsets.UTF_8.name());
        }
    }

    // ──────────────────────────────────────────────────────────────

    private static boolean isSkipped(String key) {
        return key.startsWith(SKIP_PREFIX) || SKIP_KEYS.contains(key);
    }

    private static String versionName(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }
}
