package io.github.qmryqmm.hyperopen.config;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.qmryqmm.hyperopen.model.ShortcutItem;
import io.github.qmryqmm.hyperopen.util.DePrefs;

/**
 * 配置导入 / 导出（JSON）。
 *
 * <p>导出的是「用户指纹面板配置」本身，不含任何运行时状态或 App 自身界面偏好：
 * <ul>
 *   <li>{@code safety_*} 是崩溃保护 / 安全模式的会话状态，跨设备或跨时间导入会误导启用判定；</li>
 *   <li>{@code last_real_fod_*} 是本机指纹传感器实测坐标，换机型必须是另一组值（SystemUI 展开时会自己写回）；</li>
 *   <li>{@code __de_migrated_v1} 是存储迁移标记；</li>
 *   <li>{@code ui_style / theme_mode / monet_colors / bar_blur / floating_nav_bar / predictive_back / edge_back / page_switch_gesture}
 *       属于本机 App 自身的外观与手势设置，不应随面板配置切换而被覆盖。</li>
 * </ul>
 *
 * <p>写盘格式：{@code values} 里逐键带类型标记（b/i/f/l/s/ss）。SharedPreferences 的
 * getInt / getFloat 对类型敏感，只靠 JSON number 无法区分 100 和 100.0，标记类型后
 * 回写才不会踩 ClassCastException。{@code shortcuts} 单独提成结构化数组，方便手工编辑与分享。
 *
 * <p>导入采用「精确还原」：在保留运行时状态、App 界面设置以及（未选择替换时的）快捷方式后，
 * 先清空旧面板参数键再按文件写入，避免前一份配置残留键污染当前配置，并确保旧版本配置能正确触发迁移。
 */
public final class ConfigBackup {

    private static final String TAG = "HyperOpen_Backup";

    public static final String FORMAT = "hyperopen-config";
    public static final int FORMAT_VERSION = 1;

    /** 单次导入的文件大小上限，防止误选大文件把进程撑爆。 */
    private static final int MAX_IMPORT_BYTES = 4 * 1024 * 1024;

    static final String KEY_SHORTCUTS = "shortcuts_json";
    private static final String KEY_ICON_GLOW_DEPRECATED = "icon_glow_effect";
    private static final String KEY_BLUR_BASELINE_HEALED = "blur_baseline_healed";

    /** 运行时状态：既不出现在导出文件里，也不会被导入覆盖或清理。 */
    private static final String SKIP_PREFIX = "safety_";
    private static final Set<String> RUNTIME_SKIP_KEYS = new HashSet<>(Arrays.asList(
            "last_real_fod_x",
            "last_real_fod_y",
            DePrefs.MIGRATION_MARK
    ));

    /** App 自身界面与交互偏好：剥离出面板配置导入导出，防止切配置或导入他人配置改写本机 App 外观。 */
    private static final Set<String> APP_UI_SKIP_KEYS = new HashSet<>(Arrays.asList(
            "ui_style",
            "theme_mode",
            "monet_colors",
            "bar_blur",
            "floating_nav_bar",
            "predictive_back",
            "edge_back",
            "page_switch_gesture"
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
        public final boolean hasShortcuts;
        public final int layoutStyle;
        public final int materialStyle;

        Result(
                boolean ok,
                String message,
                int keyCount,
                int shortcutCount,
                String appVersion,
                boolean hasShortcuts,
                int layoutStyle,
                int materialStyle
        ) {
            this.ok = ok;
            this.message = message;
            this.keyCount = keyCount;
            this.shortcutCount = shortcutCount;
            this.appVersion = appVersion;
            this.hasShortcuts = hasShortcuts;
            this.layoutStyle = layoutStyle;
            this.materialStyle = materialStyle;
        }

        public static Result fail(String message) {
            return new Result(
                    false,
                    message,
                    0,
                    0,
                    null,
                    false,
                    HyperOpenConfig.LAYOUT_HONEYCOMB,
                    HyperOpenConfig.MATERIAL_GAUSSIAN_BLUR
            );
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 导出
    // ──────────────────────────────────────────────────────────────

    /** 把当前配置序列化成 JSON 文本（带缩进）。 */
    public static String buildExportJson(Context ctx) throws Exception {
        return buildExportJson(ctx, true, true);
    }

    /** 把当前配置序列化成 JSON 文本，可选择是否包含快捷方式（带缩进）。 */
    public static String buildExportJson(Context ctx, boolean includeShortcuts) throws Exception {
        return buildExportJson(ctx, includeShortcuts, true);
    }

    /** 把当前配置序列化成 JSON 文本，可选择是否包含快捷方式与是否格式化缩进。 */
    public static String buildExportJson(Context ctx, boolean includeShortcuts, boolean prettyPrint) throws Exception {
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

        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("version", FORMAT_VERSION);
        root.put("appVersion", versionName(ctx));
        root.put("exportedAt", System.currentTimeMillis());
        root.put("exportedAtText", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        root.put("values", values);
        if (includeShortcuts) {
            JSONArray shortcuts = new JSONArray();
            List<ShortcutItem> items = HyperOpenConfig.parseShortcutsJson(sp.getString(KEY_SHORTCUTS, null));
            for (ShortcutItem it : items) {
                shortcuts.put(it.toJson());
            }
            root.put("shortcuts", shortcuts);
        }
        return prettyPrint ? root.toString(2) : root.toString();
    }

    /** 建议的导出文件名，例如 hyperopen-0.66-20261120-2010.json */
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
    // 导入 / 预检
    // ──────────────────────────────────────────────────────────────

    /** 只解析校验，不落盘。用于弹确认框或缓存元数据时判断配置是否可用。 */
    public static Result preview(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return Result.fail("配置内容为空");
            }
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
                if (isSkipped(k) || KEY_SHORTCUTS.equals(k)) continue;
                if (values.optJSONObject(k) != null) count++;
            }

            boolean hasShortcuts = false;
            int shortcutCount = 0;
            JSONArray shortcuts = root.optJSONArray("shortcuts");
            if (shortcuts != null) {
                hasShortcuts = true;
                for (int i = 0; i < shortcuts.length(); i++) {
                    if (shortcuts.optJSONObject(i) != null) shortcutCount++;
                }
            } else {
                // 兼容手写 / 早期格式：values 里直接带 shortcuts_json 字符串
                JSONObject sj = values.optJSONObject(KEY_SHORTCUTS);
                if (sj != null) {
                    hasShortcuts = true;
                    try {
                        JSONArray arr = new JSONArray(sj.optString("v", ""));
                        for (int i = 0; i < arr.length(); i++) {
                            if (arr.optJSONObject(i) != null) shortcutCount++;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }

            int layoutStyle = HyperOpenConfig.LAYOUT_HONEYCOMB;
            JSONObject layoutObj = values.optJSONObject("layout_style");
            if (layoutObj != null) {
                layoutStyle = layoutObj.optInt("v", HyperOpenConfig.LAYOUT_HONEYCOMB);
            }
            int materialStyle = HyperOpenConfig.MATERIAL_GAUSSIAN_BLUR;
            JSONObject materialObj = values.optJSONObject("material_style");
            if (materialObj != null) {
                materialStyle = materialObj.optInt("v", HyperOpenConfig.MATERIAL_GAUSSIAN_BLUR);
            }

            String appVersion = root.isNull("appVersion") ? "?" : root.optString("appVersion", "?");
            return new Result(true, "可导入", count, shortcutCount, appVersion, hasShortcuts, layoutStyle, materialStyle);
        } catch (Throwable t) {
            Log.w(TAG, "解析配置失败", t);
            return Result.fail("文件解析失败：" + t.getMessage());
        }
    }

    /**
     * 应用一份配置：按类型精确还原写回 prefs，然后重新开放读权限并通知 SystemUI 热重载。
     */
    public static Result apply(Context ctx, String json) {
        return apply(ctx, json, true);
    }

    /** 应用配置，可选择是否同时应用快捷方式。 */
    public static Result apply(Context ctx, String json, boolean includeShortcuts) {
        try {
            Result pre = preview(json);
            if (!pre.ok) {
                return pre;
            }
            JSONObject root = new JSONObject(json);
            JSONObject values = root.getJSONObject("values");

            SharedPreferences sp = DePrefs.get(ctx, HyperOpenConfig.PREF_NAME);
            SharedPreferences.Editor editor = sp.edit();

            boolean willApplyShortcuts = includeShortcuts && pre.hasShortcuts;

            // 先清理旧面板键，仅保留运行时状态、App 界面设置和（不替换时的）快捷方式，
            // 防止前一份配置遗留键值污染目标配置，并让缺少现代键的旧版本配置能触发迁移。
            for (String existingKey : sp.getAll().keySet()) {
                if (isSkipped(existingKey)) continue;
                if (KEY_SHORTCUTS.equals(existingKey) && !willApplyShortcuts) continue;
                editor.remove(existingKey);
            }

            int count = 0;
            int shortcutCount = 0;
            for (Iterator<String> it = values.keys(); it.hasNext(); ) {
                String k = it.next();
                if (isSkipped(k)) continue;
                if (KEY_SHORTCUTS.equals(k) && !willApplyShortcuts) continue;
                JSONObject item = values.optJSONObject(k);
                if (item == null) continue;
                if (!writeTypedPref(editor, k, item)) continue;
                if (KEY_SHORTCUTS.equals(k)) {
                    shortcutCount = pre.shortcutCount;
                } else {
                    count++;
                }
            }

            // 快捷方式：优先用结构化的 shortcuts 数组，否则保留上面回退写入的 values.shortcuts_json
            JSONArray shortcuts = root.optJSONArray("shortcuts");
            if (willApplyShortcuts && shortcuts != null) {
                List<ShortcutItem> items = new ArrayList<>();
                for (int i = 0; i < shortcuts.length(); i++) {
                    JSONObject obj = shortcuts.optJSONObject(i);
                    if (obj != null) {
                        items.add(ShortcutItem.fromJson(obj));
                    }
                }
                editor.putString(KEY_SHORTCUTS, HyperOpenConfig.serializeShortcutsJson(items));
                shortcutCount = items.size();
            }

            if (!editor.commit()) {
                return Result.fail("保存配置失败：写入存储未完成");
            }

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
            return new Result(true, "导入完成", count, shortcutCount, pre.appVersion, pre.hasShortcuts, pre.layoutStyle, pre.materialStyle);
        } catch (Throwable t) {
            Log.e(TAG, "导入配置失败", t);
            return Result.fail("导入失败：" + t.getMessage());
        }
    }

    /** 从配置 JSON 中解析快捷方式清单；若该配置不含快捷方式字段则返回 null。 */
    public static List<ShortcutItem> parseShortcutsFromBackup(String json) {
        try {
            if (json == null || json.trim().isEmpty()) return null;
            JSONObject root = new JSONObject(json);
            JSONArray shortcuts = root.optJSONArray("shortcuts");
            if (shortcuts != null) {
                List<ShortcutItem> items = new ArrayList<>();
                for (int i = 0; i < shortcuts.length(); i++) {
                    JSONObject obj = shortcuts.optJSONObject(i);
                    if (obj != null) items.add(ShortcutItem.fromJson(obj));
                }
                return items;
            }
            JSONObject values = root.optJSONObject("values");
            JSONObject sj = values != null ? values.optJSONObject(KEY_SHORTCUTS) : null;
            if (sj != null) {
                List<ShortcutItem> items = new ArrayList<>();
                String raw = sj.optString("v", "");
                if (!raw.trim().isEmpty()) {
                    JSONArray arr = new JSONArray(raw);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.optJSONObject(i);
                        if (obj != null) items.add(ShortcutItem.fromJson(obj));
                    }
                }
                return items;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 对比当前生效参数（不含 App 自身界面设置与快捷方式）是否与目标配置一致。 */
    public static boolean areCurrentParamsMatching(Context ctx, String backupJson) {
        try {
            if (ctx == null || backupJson == null || backupJson.trim().isEmpty()) return false;
            JSONObject root = new JSONObject(backupJson);
            JSONObject values = root.optJSONObject("values");
            if (values == null) return false;

            SharedPreferences sp = DePrefs.get(ctx, HyperOpenConfig.PREF_NAME);
            Map<String, ?> all = sp.getAll();
            Map<String, Object> currentMap = new HashMap<>();
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                String k = entry.getKey();
                if (isParamExcludedFromDiff(k)) continue;
                Object v = entry.getValue();
                if (v instanceof Boolean || v instanceof Integer || v instanceof Float
                        || v instanceof Long || v instanceof String || v instanceof Set) {
                    currentMap.put(k, v);
                }
            }

            Map<String, Object> profileMap = new HashMap<>();
            for (Iterator<String> it = values.keys(); it.hasNext(); ) {
                String k = it.next();
                if (isParamExcludedFromDiff(k)) continue;
                JSONObject item = values.optJSONObject(k);
                if (item == null) continue;
                Object decoded = decodeTypedValue(item);
                if (decoded != null) {
                    profileMap.put(k, decoded);
                }
            }

            if (!currentMap.keySet().equals(profileMap.keySet())) {
                return false;
            }
            for (String key : currentMap.keySet()) {
                if (!valuesEqual(currentMap.get(key), profileMap.get(key))) {
                    return false;
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 对比当前生效快捷方式是否与目标配置一致；目标配置不含快捷方式时视为一致（不管理快捷项）。 */
    public static boolean areCurrentShortcutsMatching(Context ctx, String backupJson) {
        try {
            if (ctx == null || backupJson == null || backupJson.trim().isEmpty()) return true;
            List<ShortcutItem> profileShortcuts = parseShortcutsFromBackup(backupJson);
            if (profileShortcuts == null) return true;
            SharedPreferences sp = DePrefs.get(ctx, HyperOpenConfig.PREF_NAME);
            List<ShortcutItem> currentShortcuts = HyperOpenConfig.parseShortcutsJson(sp.getString(KEY_SHORTCUTS, null));
            String currentSerialized = HyperOpenConfig.serializeShortcutsJson(currentShortcuts);
            String profileSerialized = HyperOpenConfig.serializeShortcutsJson(profileShortcuts);
            return currentSerialized.equals(profileSerialized);
        } catch (Throwable ignored) {
            return false;
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

    /** 读取 SAF 文件展示名并去掉末尾 .json 扩展名，作为单份配置导入的默认名称建议。 */
    public static String queryDisplayName(Context ctx, Uri uri) {
        if (ctx == null || uri == null) return "";
        String name = null;
        try (Cursor cursor = ctx.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && !cursor.isNull(idx)) {
                    name = cursor.getString(idx);
                }
            }
        } catch (Throwable ignored) {
        }
        if (name == null || name.trim().isEmpty()) {
            String segment = uri.getLastPathSegment();
            if (segment != null) {
                int slash = segment.lastIndexOf('/');
                if (slash >= 0 && slash + 1 < segment.length()) {
                    segment = segment.substring(slash + 1);
                }
                int colon = segment.lastIndexOf(':');
                if (colon >= 0 && colon + 1 < segment.length()) {
                    segment = segment.substring(colon + 1);
                }
                name = segment;
            }
        }
        if (name == null) return "";
        String trimmed = name.trim();
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(".json") && trimmed.length() > 5) {
            trimmed = trimmed.substring(0, trimmed.length() - 5).trim();
        }
        return trimmed;
    }

    // ──────────────────────────────────────────────────────────────

    public static boolean isSkipped(String key) {
        return key == null
                || key.startsWith(SKIP_PREFIX)
                || RUNTIME_SKIP_KEYS.contains(key)
                || APP_UI_SKIP_KEYS.contains(key);
    }

    private static boolean isParamExcludedFromDiff(String key) {
        return isSkipped(key)
                || KEY_SHORTCUTS.equals(key)
                || KEY_ICON_GLOW_DEPRECATED.equals(key)
                || KEY_BLUR_BASELINE_HEALED.equals(key);
    }

    private static boolean writeTypedPref(SharedPreferences.Editor editor, String k, JSONObject item) {
        String type = item.optString("t", "");
        Object raw = item.opt("v");
        switch (type) {
            case "b":
                editor.putBoolean(k, item.optBoolean("v"));
                return true;
            case "i":
                editor.putInt(k, item.optInt("v"));
                return true;
            case "f":
                editor.putFloat(k, (float) item.optDouble("v"));
                return true;
            case "l":
                editor.putLong(k, item.optLong("v"));
                return true;
            case "s":
                editor.putString(k, raw == null || raw == JSONObject.NULL ? "" : String.valueOf(raw));
                return true;
            case "ss": {
                JSONArray arr = item.optJSONArray("v");
                Set<String> set = new HashSet<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        set.add(arr.optString(i));
                    }
                }
                editor.putStringSet(k, set);
                return true;
            }
            default:
                Log.w(TAG, "未知的配置项类型标记，跳过: " + k + " t=" + type);
                return false;
        }
    }

    private static Object decodeTypedValue(JSONObject item) {
        String type = item.optString("t", "");
        Object raw = item.opt("v");
        switch (type) {
            case "b":
                return item.optBoolean("v");
            case "i":
                return item.optInt("v");
            case "f":
                return (float) item.optDouble("v");
            case "l":
                return item.optLong("v");
            case "s":
                return raw == null || raw == JSONObject.NULL ? "" : String.valueOf(raw);
            case "ss": {
                JSONArray arr = item.optJSONArray("v");
                Set<String> set = new HashSet<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        set.add(arr.optString(i));
                    }
                }
                return set;
            }
            default:
                return null;
        }
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof Float && b instanceof Float) {
            return Math.abs((Float) a - (Float) b) < 1e-4f;
        }
        return Objects.equals(a, b);
    }

    private static String versionName(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }
}
