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
import java.util.Set;

import io.github.qmryqmm.hyperopen.factory.HyperOSMaterialFactory;
import io.github.qmryqmm.hyperopen.model.ActivityLaunchSpec;
import io.github.qmryqmm.hyperopen.model.ShortcutItem;
import io.github.qmryqmm.hyperopen.util.DePrefs;

/**
 * 配置导入 / 导出（JSON）。
 *
 * <p>导出的是「用户指纹面板配置」本身，不含任何运行时状态或 App 自身界面偏好：
 * <ul>
 *   <li>{@code safety_*} 是崩溃保护 / 安全模式的会话状态，跨设备或跨时间导入会误导启用判定；</li>
 *   <li>{@code hook_*} 是 SystemUI 实时注入状态（PID、注入时间戳、挂载数与版本），属于运行态；</li>
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

    /** 运行时状态：既不出现在导出文件里，也不会被导入覆盖或清理。 */
    private static final String SKIP_PREFIX_SAFETY = "safety_";
    private static final String SKIP_PREFIX_HOOK = "hook_";
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

    /** 导入白名单：仅允许写入已定义的面板参数键与 6 个旧版兼容键，并强制校验预期值类型。 */
    private static final Map<String, String> ALLOWED_KEY_TYPES;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("material_style", "i");
        m.put("offset_y", "i");
        m.put("region_diameter", "i");
        m.put("show_region_bg", "b");
        m.put("show_header_pill_bg", "b");
        m.put("show_header_pill", "b");
        m.put("header_top_margin", "i");
        m.put("header_title_size", "i");
        m.put("header_subtitle_size", "i");
        m.put("header_blur_radius", "i");
        m.put("header_text_centered", "b");
        m.put("icon_size", "i");
        m.put("max_icon_count", "i");
        m.put("layout_style", "i");
        m.put("stock_system_region", "b");
        m.put("ring_sector_mode", "b");
        m.put("sector_line_color", "i");
        m.put("sector_line_alpha", "f");
        m.put("sector_dash_length", "i");
        m.put("sector_dash_gap", "i");
        m.put("sector_line_width", "f");
        m.put("sector_highlight_enabled", "b");
        m.put("anim_duration_ms", "i");
        m.put("panel_expand_anim_type", "i");
        m.put("panel_expand_stagger_ms", "i");
        m.put("dismiss_anim_enabled", "b");
        m.put("panel_collapse_anim_type", "i");
        m.put("panel_collapse_duration_ms", "i");
        m.put("panel_collapse_stagger_ms", "i");
        m.put("panel_selected_pop_scale", "f");
        m.put("global_hint_color_mode", "i");
        m.put("global_hint_custom_color", "i");
        m.put("default_hint_text_stroke", "i");
        m.put("default_hint_text_shadow", "i");
        m.put("default_hint_text_weight", "i");
        m.put("default_hint_text_letter_spacing", "f");
        m.put("default_show_hint_title", "b");
        m.put("default_show_hint_subtitle", "b");
        m.put("folder_hover_delay_ms", "i");
        m.put("folder_child_display_range_scale", "f");
        m.put("folder_child_direction_mode", "i");
        m.put("folder_child_direction_offset_deg", "f");
        m.put("folder_child_order_mode", "i");
        m.put("folder_child_min_gap_dp", "i");
        m.put("folder_child_size_scale", "f");
        m.put("folder_child_expand_anim_type", "i");
        m.put("folder_child_expand_duration_ms", "i");
        m.put("folder_child_expand_stagger_ms", "i");
        m.put("folder_child_collapse_anim_type", "i");
        m.put("folder_child_collapse_duration_ms", "i");
        m.put("folder_child_collapse_stagger_ms", "i");
        m.put("folder_child_start_scale", "f");
        m.put("folder_child_overshoot", "f");
        m.put("max_scale", "f");
        m.put("influence_radius", "i");
        m.put("icon_alpha", "f");
        m.put("fisheye_zoom_alpha", "f");
        m.put("fisheye_other_alpha", "f");
        m.put("icon_corner_radius", "i");
        m.put("fullscreen_blur_mask_alpha", "f");
        m.put("fullscreen_dim_alpha", "f");
        m.put("header_dim_alpha", "f");
        m.put("region_dim_alpha", "f");
        m.put("bubble_dim_alpha", "f");
        m.put("show_delay_ms", "i");
        m.put("disable_system_enter_anim", "b");
        m.put("face_unlock_quick_open", "b");
        m.put("ble_unlock_quick_open", "b");
        m.put("haptic_enabled", "b");
        m.put("haptic_strength", "i");
        m.put("haptic_duration_ms", "i");
        m.put("haptic_mode", "i");
        m.put("haptic_stock_wave_expand", "i");
        m.put("haptic_brake_pulse", "b");
        m.put("haptic_double_gap_ms", "i");
        m.put("haptic_slide_enabled", "b");
        m.put("haptic_slide_strength", "i");
        m.put("haptic_slide_duration_ms", "i");
        m.put("haptic_slide_mode", "i");
        m.put("haptic_stock_wave_slide", "i");
        m.put("haptic_slide_brake_pulse", "b");
        m.put("haptic_slide_double_gap_ms", "i");
        m.put("region_blur_radius", "i");
        m.put("icon_spacing_offset", "i");
        m.put("show_bubble_bg", "b");
        m.put("bubble_padding_percent", "i");
        m.put("bubble_blur_radius", "i");
        m.put("app_icon_crop_mode", "i");
        m.put("glyph_pad_enabled", "b");
        m.put("glyph_pad_color", "i");
        m.put("glass_preset", "i");
        m.put("glass_refract_ior", "f");
        m.put("glass_thickness", "f");
        m.put("glass_specular", "f");
        m.put("glass_matte_alpha", "f");
        m.put("glass_border_light", "f");
        m.put("glass_background_saturation", "f");
        m.put("glass_background_brightness", "f");
        m.put("glass_background_burn", "f");
        m.put("glass_matte_white_mix", "f");
        m.put("glass_matte_color_mix", "f");
        m.put("glass_matte_color_power", "f");
        m.put("glass_edge_range", "f");
        m.put("glass_edge_power", "f");
        m.put("glass_reflect_offset", "f");
        m.put("global_blur_radius", "i");
        m.put("global_dim_alpha", "f");
        m.put("bubble_material_follow_global", "b");
        m.put("region_material_follow_global", "b");
        m.put("header_material_follow_global", "b");
        m.put("bubble_glass_matte_alpha", "f");
        m.put("region_glass_matte_alpha", "f");
        m.put("header_glass_matte_alpha", "f");
        // 6 个旧版兼容键
        m.put("stock_system_anim", "b");
        m.put("selected_icon_scale", "f");
        m.put("shortcuts_json", "s");
        m.put("blur_baseline_healed", "b");
        m.put("glyph_pad_mode", "i");
        m.put("icon_glow_effect", "b");
        ALLOWED_KEY_TYPES = Collections.unmodifiableMap(m);
    }

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
        public final int skippedKeyCount;
        public final List<String> highRiskShortcuts;

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
            this(ok, message, keyCount, shortcutCount, appVersion, hasShortcuts, layoutStyle, materialStyle,
                    0, Collections.emptyList());
        }

        Result(
                boolean ok,
                String message,
                int keyCount,
                int shortcutCount,
                String appVersion,
                boolean hasShortcuts,
                int layoutStyle,
                int materialStyle,
                int skippedKeyCount,
                List<String> highRiskShortcuts
        ) {
            this.ok = ok;
            this.message = message;
            this.keyCount = keyCount;
            this.shortcutCount = shortcutCount;
            this.appVersion = appVersion;
            this.hasShortcuts = hasShortcuts;
            this.layoutStyle = layoutStyle;
            this.materialStyle = materialStyle;
            this.skippedKeyCount = skippedKeyCount;
            this.highRiskShortcuts = highRiskShortcuts == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(highRiskShortcuts));
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
                    HyperOpenConfig.MATERIAL_GAUSSIAN_BLUR,
                    0,
                    Collections.emptyList()
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
            int skippedKeyCount = 0;
            for (Iterator<String> it = values.keys(); it.hasNext(); ) {
                String k = it.next();
                if (isSkipped(k) || KEY_SHORTCUTS.equals(k)) continue;
                JSONObject item = values.optJSONObject(k);
                if (parseValidatedPref(k, item) != null) {
                    count++;
                } else {
                    skippedKeyCount++;
                }
            }

            boolean hasShortcuts = false;
            int shortcutCount = 0;
            List<ShortcutItem> parsedShortcuts = new ArrayList<>();
            JSONArray shortcuts = root.optJSONArray("shortcuts");
            if (shortcuts != null) {
                hasShortcuts = true;
                for (int i = 0; i < shortcuts.length(); i++) {
                    JSONObject obj = shortcuts.optJSONObject(i);
                    if (obj != null) {
                        shortcutCount++;
                        parsedShortcuts.add(ShortcutItem.fromJson(obj));
                    }
                }
            } else {
                // 兼容手写 / 早期格式：values 里直接带 shortcuts_json 字符串
                JSONObject sj = values.optJSONObject(KEY_SHORTCUTS);
                if (sj != null) {
                    Object validatedSj = parseValidatedPref(KEY_SHORTCUTS, sj);
                    if (validatedSj instanceof String) {
                        hasShortcuts = true;
                        String rawShortcuts = ((String) validatedSj).trim();
                        if (!rawShortcuts.isEmpty()) {
                            try {
                                JSONArray arr = new JSONArray(rawShortcuts);
                                for (int i = 0; i < arr.length(); i++) {
                                    JSONObject obj = arr.optJSONObject(i);
                                    if (obj != null) {
                                        shortcutCount++;
                                        parsedShortcuts.add(ShortcutItem.fromJson(obj));
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    } else {
                        skippedKeyCount++;
                    }
                }
            }

            List<String> highRiskShortcuts = new ArrayList<>();
            if (!parsedShortcuts.isEmpty()) {
                collectHighRiskShortcuts(parsedShortcuts, "", 0, highRiskShortcuts);
            }

            int layoutStyle = HyperOpenConfig.LAYOUT_HONEYCOMB;
            Object parsedLayout = parseValidatedPref("layout_style", values.optJSONObject("layout_style"));
            if (parsedLayout instanceof Integer) {
                layoutStyle = (Integer) parsedLayout;
            }
            int materialStyle = HyperOpenConfig.MATERIAL_GAUSSIAN_BLUR;
            Object parsedMaterial = parseValidatedPref("material_style", values.optJSONObject("material_style"));
            if (parsedMaterial instanceof Integer) {
                materialStyle = (Integer) parsedMaterial;
            }

            String appVersion = root.isNull("appVersion") ? "?" : root.optString("appVersion", "?");
            return new Result(
                    true,
                    "可导入",
                    count,
                    shortcutCount,
                    appVersion,
                    hasShortcuts,
                    layoutStyle,
                    materialStyle,
                    skippedKeyCount,
                    highRiskShortcuts
            );
        } catch (Throwable t) {
            Log.w(TAG, "解析配置失败", t);
            return Result.fail("文件解析失败：" + t.getMessage());
        }
    }

    /**
     * 应用一份配置：按类型精确还原写回 prefs，然后重新开放读权限并通知 SystemUI 热重载。
     */
    public static Result apply(Context ctx, String json) {
        return apply(ctx, json, true, true);
    }

    /** 应用配置，可选择是否同时应用快捷方式。 */
    public static Result apply(Context ctx, String json, boolean includeShortcuts) {
        return apply(ctx, json, true, includeShortcuts);
    }

    /** 应用配置，支持独立选择参数、快捷方式以及可选覆写本机 offsetY。 */
    public static Result apply(
            Context ctx,
            String json,
            boolean applyParams,
            boolean applyShortcuts,
            Integer overrideOffsetY
    ) {
        try {
            String prepared = prepareBackupJsonForImport(json, applyParams ? overrideOffsetY : null, applyShortcuts);
            return apply(ctx, prepared, applyParams, applyShortcuts);
        } catch (Throwable t) {
            return Result.fail(t.getMessage() != null ? t.getMessage() : "配置预处理失败");
        }
    }

    /**
     * 对待导入/应用的备份 JSON 做一键预检与预处理：
     * 可选覆写 {@code offset_y}（保留本机指纹位置偏移）并按需剔除快捷项目，
     * 返回规范化 JSON 供 {@link #apply} 或 {@link ConfigProfileStore#previewImport} 直接复用。
     */
    public static String prepareBackupJsonForImport(
            String backupJson,
            Integer overrideOffsetY,
            boolean includeShortcuts
    ) throws Exception {
        return prepareBackupJsonForImport(backupJson, overrideOffsetY, includeShortcuts, null);
    }

    /**
     * 对待导入/应用的备份 JSON 做一键预检与预处理：
     * 可选覆写 {@code offset_y}、按需剔除或注入外部快捷项目列表（例如配置市场配套快捷包），
     * 返回规范化 JSON 供 {@link #apply} 或 {@link ConfigProfileStore#previewImport} 直接复用。
     */
    public static String prepareBackupJsonForImport(
            String backupJson,
            Integer overrideOffsetY,
            boolean includeShortcuts,
            List<ShortcutItem> overrideShortcuts
    ) throws Exception {
        Result pre = preview(backupJson);
        if (!pre.ok) {
            throw new IllegalArgumentException(pre.message);
        }
        JSONObject root = new JSONObject(backupJson);
        JSONObject values = root.optJSONObject("values");
        if (values == null) {
            throw new IllegalArgumentException("配置文件缺少 values 字段");
        }
        if (overrideOffsetY != null) {
            int clampedOffsetY = clampInt("offset_y", overrideOffsetY);
            JSONObject offsetEntry = new JSONObject();
            offsetEntry.put("t", "i");
            offsetEntry.put("v", clampedOffsetY);
            values.put("offset_y", offsetEntry);
        }
        if (!includeShortcuts) {
            root.remove("shortcuts");
            values.remove(KEY_SHORTCUTS);
        } else if (overrideShortcuts != null) {
            JSONArray arr = new JSONArray();
            for (ShortcutItem item : overrideShortcuts) {
                if (item != null) {
                    arr.put(item.toJson());
                }
            }
            root.put("shortcuts", arr);
            values.remove(KEY_SHORTCUTS);
        }
        return root.toString();
    }

    /**
     * 纯内存沙箱预览构建：解析配置 JSON、按白名单校验与钳制参数、构造 {@link HyperOpenConfig}
     * 并执行柔光玻璃降级适配；{@code !includeShortcuts} 或目标不含快捷项时回退到
     * {@code fallbackShortcuts}。全程零写盘、零广播，不修改任何本地存储。
     */
    public static HyperOpenConfig buildPreviewConfigFromBackupJson(
            Context ctx,
            String backupJson,
            List<ShortcutItem> fallbackShortcuts,
            boolean includeShortcuts,
            boolean softGlassSupported
    ) throws Exception {
        Result pre = preview(backupJson);
        if (!pre.ok) {
            throw new IllegalArgumentException(pre.message);
        }
        JSONObject root = new JSONObject(backupJson);
        JSONObject rawValues = root.optJSONObject("values");
        JSONObject validatedValues = new JSONObject();
        if (rawValues != null) {
            for (Iterator<String> it = rawValues.keys(); it.hasNext(); ) {
                String k = it.next();
                if (isSkipped(k) || KEY_SHORTCUTS.equals(k)) {
                    continue;
                }
                Object validated = parseValidatedPref(k, rawValues.optJSONObject(k));
                if (validated != null) {
                    JSONObject encoded = encodeValue(validated);
                    if (encoded != null) {
                        validatedValues.put(k, encoded);
                    }
                }
            }
        }
        HyperOpenConfig config = HyperOpenConfig.fromBackupValues(validatedValues);
        HyperOpenConfig.adaptUnsupportedSoftGlass(config, softGlassSupported);

        if (ctx != null) {
            try {
                SharedPreferences sp = DePrefs.get(ctx, HyperOpenConfig.PREF_NAME);
                config.lastRealFodX = sp.getFloat("last_real_fod_x", 0f);
                config.lastRealFodY = sp.getFloat("last_real_fod_y", 0f);
            } catch (Throwable ignored) {
            }
        }

        List<ShortcutItem> resolvedShortcuts = null;
        if (includeShortcuts && pre.hasShortcuts) {
            List<ShortcutItem> parsed = parseShortcutsFromBackup(backupJson);
            if (parsed != null && !parsed.isEmpty()) {
                resolvedShortcuts = new ArrayList<>(parsed.size());
                for (ShortcutItem item : parsed) {
                    if (item != null) {
                        resolvedShortcuts.add(item.copy());
                    }
                }
            }
        }
        if (resolvedShortcuts == null) {
            if (fallbackShortcuts != null && !fallbackShortcuts.isEmpty()) {
                resolvedShortcuts = new ArrayList<>(fallbackShortcuts.size());
                for (ShortcutItem item : fallbackShortcuts) {
                    if (item != null) {
                        resolvedShortcuts.add(item.copy());
                    }
                }
            } else {
                resolvedShortcuts = HyperOpenConfig.getDefaultShortcuts();
            }
        }
        config.shortcuts = resolvedShortcuts;
        return config;
    }

    /** 应用配置，支持独立选择是否应用面板参数与快捷方式列表。 */
    public static Result apply(
            Context ctx,
            String json,
            boolean applyParams,
            boolean applyShortcuts
    ) {
        try {
            if (!applyParams && !applyShortcuts) {
                return Result.fail("请至少选择一项要应用的内容");
            }
            Result pre = preview(json);
            if (!pre.ok) {
                return pre;
            }
            JSONObject root = new JSONObject(json);
            JSONObject values = root.getJSONObject("values");

            boolean willApplyShortcuts = applyShortcuts && pre.hasShortcuts;
            if (!applyParams && !willApplyShortcuts) {
                return Result.fail("该配置不包含可应用的快捷方式");
            }

            SharedPreferences sp = DePrefs.get(ctx, HyperOpenConfig.PREF_NAME);
            SharedPreferences.Editor editor = sp.edit();

            int count = 0;
            int skippedKeyCount = 0;
            int shortcutCount = 0;

            if (applyParams) {
                // 先清理旧面板键，仅保留运行时状态、App 界面设置和（不替换时的）快捷方式，
                // 防止前一份配置遗留键值污染目标配置，并让缺少现代键的旧版本配置能触发迁移。
                for (String existingKey : sp.getAll().keySet()) {
                    if (isSkipped(existingKey)) continue;
                    if (KEY_SHORTCUTS.equals(existingKey) && !willApplyShortcuts) continue;
                    editor.remove(existingKey);
                }

                for (Iterator<String> it = values.keys(); it.hasNext(); ) {
                    String k = it.next();
                    if (isSkipped(k)) continue;
                    if (KEY_SHORTCUTS.equals(k) && !willApplyShortcuts) continue;
                    JSONObject item = values.optJSONObject(k);
                    if (!writeTypedPref(editor, k, item)) {
                        skippedKeyCount++;
                        continue;
                    }
                    if (KEY_SHORTCUTS.equals(k)) {
                        shortcutCount = pre.shortcutCount;
                    } else {
                        count++;
                    }
                }

                if (!HyperOSMaterialFactory.isSoftGlassSupported()
                        && pre.materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS) {
                    writeUnsupportedSoftGlassFallback(editor, values);
                }
            }

            // 快捷方式：优先用结构化的 shortcuts 数组，否则使用 values.shortcuts_json
            if (willApplyShortcuts) {
                JSONArray shortcuts = root.optJSONArray("shortcuts");
                if (shortcuts != null) {
                    List<ShortcutItem> items = new ArrayList<>();
                    for (int i = 0; i < shortcuts.length(); i++) {
                        JSONObject obj = shortcuts.optJSONObject(i);
                        if (obj != null) {
                            items.add(ShortcutItem.fromJson(obj));
                        }
                    }
                    editor.putString(KEY_SHORTCUTS, HyperOpenConfig.serializeShortcutsJson(items));
                    shortcutCount = items.size();
                } else if (!applyParams) {
                    JSONObject sj = values.optJSONObject(KEY_SHORTCUTS);
                    if (sj != null && writeTypedPref(editor, KEY_SHORTCUTS, sj)) {
                        shortcutCount = pre.shortcutCount;
                    }
                }
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

            Log.i(TAG, "配置应用完成: " + count + " 项参数, " + shortcutCount + " 个快捷方式, 跳过=" + skippedKeyCount);
            return new Result(
                    true,
                    "应用完成",
                    count,
                    shortcutCount,
                    pre.appVersion,
                    pre.hasShortcuts,
                    pre.layoutStyle,
                    pre.materialStyle,
                    skippedKeyCount,
                    pre.highRiskShortcuts
            );
        } catch (Throwable t) {
            Log.e(TAG, "应用配置失败", t);
            return Result.fail("应用失败：" + t.getMessage());
        }
    }

    /**
     * 在不支持柔光玻璃的设备上写入与 {@link HyperOpenConfig#adaptUnsupportedSoftGlass} 一致的
     * 降级材质与模糊半径补偿值，确保真机生效与配置同步状态判定一致。
     */
    private static void writeUnsupportedSoftGlassFallback(
            SharedPreferences.Editor editor,
            JSONObject values
    ) {
        HyperOpenConfig adapted = HyperOpenConfig.fromBackupValues(values);
        editor.putInt("material_style", adapted.materialStyle);
        editor.putInt("global_blur_radius", adapted.globalBlurRadius);
        editor.putInt("bubble_blur_radius", adapted.bubbleBlurRadius);
        editor.putInt("region_blur_radius", adapted.regionBlurRadius);
        editor.putInt("header_blur_radius", adapted.headerBlurRadius);
        editor.putBoolean("blur_baseline_healed", adapted.blurBaselineHealed);
        if (values == null || !values.has("global_blur_radius")) {
            editor.putFloat("global_dim_alpha", adapted.globalDimAlpha);
            editor.putBoolean("bubble_material_follow_global", adapted.bubbleMaterialFollowGlobal);
            editor.putBoolean("region_material_follow_global", adapted.regionMaterialFollowGlobal);
            editor.putBoolean("header_material_follow_global", adapted.headerMaterialFollowGlobal);
            editor.putFloat("bubble_glass_matte_alpha", adapted.bubbleGlassMatteAlpha);
            editor.putFloat("region_glass_matte_alpha", adapted.regionGlassMatteAlpha);
            editor.putFloat("header_glass_matte_alpha", adapted.headerGlassMatteAlpha);
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
                String raw = sj.isNull("v") ? null : sj.optString("v", "");
                return HyperOpenConfig.parseShortcutsJson(raw);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 对比当前生效参数（不含 App 自身界面设置、运行时状态与快捷方式）是否与目标配置一致。 */
    public static boolean areCurrentParamsMatching(Context ctx, String backupJson) {
        try {
            if (ctx == null || backupJson == null || backupJson.trim().isEmpty()) return false;
            JSONObject root = new JSONObject(backupJson);
            JSONObject values = root.optJSONObject("values");
            if (values == null) return false;
            HyperOpenConfig current = HyperOpenConfig.loadFromApp(ctx);
            HyperOpenConfig target = HyperOpenConfig.fromBackupValues(values);
            return current.panelParamsEquals(target);
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
            String currentSerialized = HyperOpenConfig.serializeShortcutsJson(normalizeShortcutsForDiff(currentShortcuts));
            String profileSerialized = HyperOpenConfig.serializeShortcutsJson(normalizeShortcutsForDiff(profileShortcuts));
            return currentSerialized.equals(profileSerialized);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static List<ShortcutItem> normalizeShortcutsForDiff(List<ShortcutItem> items) {
        List<ShortcutItem> normalized = new ArrayList<>(items.size());
        for (ShortcutItem item : items) {
            ShortcutItem copy = item.copy();
            if (ShortcutItem.ACTION_FOLDER.equals(copy.actionType)) {
                copy.setFolderChildren(copy.parseFolderChildren());
            }
            normalized.add(copy);
        }
        return normalized;
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

    public static boolean isAllowedKey(String key) {
        return key != null && ALLOWED_KEY_TYPES.containsKey(key);
    }

    public static boolean isSkipped(String key) {
        return key == null
                || key.startsWith(SKIP_PREFIX_SAFETY)
                || key.startsWith(SKIP_PREFIX_HOOK)
                || RUNTIME_SKIP_KEYS.contains(key)
                || APP_UI_SKIP_KEYS.contains(key);
    }

    private static Object parseValidatedPref(String k, JSONObject item) {
        if (k == null || isSkipped(k) || item == null) {
            return null;
        }
        String expectedType = ALLOWED_KEY_TYPES.get(k);
        if (expectedType == null) {
            Log.w(TAG, "忽略非白名单配置键: " + k);
            return null;
        }
        String type = item.optString("t", "");
        if (!expectedType.equals(type)) {
            Log.w(TAG, "配置项类型与白名单不符，跳过: " + k + " expected=" + expectedType + " actual=" + type);
            return null;
        }
        if (!item.has("v")) {
            Log.w(TAG, "配置项缺少值字段，跳过: " + k);
            return null;
        }
        Object raw = item.opt("v");
        switch (expectedType) {
            case "b":
                if (!(raw instanceof Boolean)) {
                    Log.w(TAG, "布尔配置项值类型非法，跳过: " + k);
                    return null;
                }
                return raw;
            case "i": {
                if (!(raw instanceof Number) || (raw instanceof Boolean)) {
                    Log.w(TAG, "整型配置项值类型非法，跳过: " + k);
                    return null;
                }
                double d = ((Number) raw).doubleValue();
                if (!Double.isFinite(d) || d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
                    Log.w(TAG, "整型配置项数值非法或越界，跳过: " + k);
                    return null;
                }
                return clampInt(k, ((Number) raw).intValue());
            }
            case "f": {
                if (!(raw instanceof Number) || (raw instanceof Boolean)) {
                    Log.w(TAG, "浮点配置项值类型非法，跳过: " + k);
                    return null;
                }
                double d = ((Number) raw).doubleValue();
                if (!Double.isFinite(d)) {
                    Log.w(TAG, "浮点配置项出现 NaN/Infinity，跳过: " + k);
                    return null;
                }
                float f = (float) d;
                if (!Float.isFinite(f)) {
                    Log.w(TAG, "浮点配置项超出 float 表示范围，跳过: " + k);
                    return null;
                }
                return clampFloat(k, f);
            }
            case "l": {
                if (!(raw instanceof Number) || (raw instanceof Boolean)) {
                    Log.w(TAG, "长整型配置项值类型非法，跳过: " + k);
                    return null;
                }
                double d = ((Number) raw).doubleValue();
                if (!Double.isFinite(d)) {
                    Log.w(TAG, "长整型配置项出现非法值，跳过: " + k);
                    return null;
                }
                return ((Number) raw).longValue();
            }
            case "s":
                if (raw == null || raw == JSONObject.NULL) {
                    return "";
                }
                if (!(raw instanceof String)) {
                    Log.w(TAG, "字符串配置项值类型非法，跳过: " + k);
                    return null;
                }
                return raw;
            case "ss": {
                if (!(raw instanceof JSONArray)) {
                    Log.w(TAG, "字符串集合配置项值类型非法，跳过: " + k);
                    return null;
                }
                JSONArray arr = (JSONArray) raw;
                Set<String> set = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    Object elem = arr.opt(i);
                    if (elem != null && elem != JSONObject.NULL) {
                        set.add(String.valueOf(elem));
                    }
                }
                return set;
            }
            default:
                return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean writeTypedPref(SharedPreferences.Editor editor, String k, JSONObject item) {
        Object validated = parseValidatedPref(k, item);
        if (validated == null) {
            return false;
        }
        String expectedType = ALLOWED_KEY_TYPES.get(k);
        if (expectedType == null) {
            return false;
        }
        switch (expectedType) {
            case "b":
                editor.putBoolean(k, (Boolean) validated);
                return true;
            case "i":
                editor.putInt(k, (Integer) validated);
                return true;
            case "f":
                editor.putFloat(k, (Float) validated);
                return true;
            case "l":
                editor.putLong(k, (Long) validated);
                return true;
            case "s":
                editor.putString(k, (String) validated);
                return true;
            case "ss":
                editor.putStringSet(k, (Set<String>) validated);
                return true;
            default:
                return false;
        }
    }

    private static int clampInt(String key, int value) {
        switch (key) {
            case "icon_size":
                return clamp(value, 20, 200);
            case "max_icon_count":
                return clamp(value, 0, 64);
            case "region_diameter":
                return clamp(value, 60, 1200);
            case "offset_y":
                return clamp(value, -500, 1000);
            case "header_top_margin":
                return clamp(value, 0, 1000);
            case "header_title_size":
                return clamp(value, 8, 72);
            case "header_subtitle_size":
                return clamp(value, 6, 60);
            case "header_blur_radius":
            case "region_blur_radius":
            case "bubble_blur_radius":
            case "global_blur_radius":
                return clamp(value, 0, 200);
            case "icon_corner_radius":
                return clamp(value, 0, 50);
            case "bubble_padding_percent":
                return clamp(value, 0, 80);
            case "icon_spacing_offset":
            case "folder_child_min_gap_dp":
                return clamp(value, -50, 100);
            case "influence_radius":
                return clamp(value, 0, 1000);
            case "show_delay_ms":
                return clamp(value, 0, 10000);
            case "anim_duration_ms":
            case "panel_collapse_duration_ms":
            case "folder_child_expand_duration_ms":
            case "folder_child_collapse_duration_ms":
            case "folder_hover_delay_ms":
                return clamp(value, 0, 5000);
            case "panel_expand_stagger_ms":
            case "panel_collapse_stagger_ms":
            case "folder_child_expand_stagger_ms":
            case "folder_child_collapse_stagger_ms":
                return clamp(value, 0, 1000);
            case "haptic_strength":
            case "haptic_slide_strength":
                return clamp(value, 1, 255);
            case "haptic_duration_ms":
            case "haptic_slide_duration_ms":
                return clamp(value, 1, 1000);
            case "haptic_double_gap_ms":
            case "haptic_slide_double_gap_ms":
                return clamp(value, 0, 1000);
            case "sector_dash_length":
            case "sector_dash_gap":
                return clamp(value, 0, 200);
            case "default_hint_text_stroke":
                return clamp(value, 0, 20);
            case "default_hint_text_shadow":
                return clamp(value, 0, 200);
            case "default_hint_text_weight":
                return clamp(value, 0, 3);
            default:
                return value;
        }
    }

    private static float clampFloat(String key, float value) {
        switch (key) {
            case "max_scale":
            case "selected_icon_scale":
            case "panel_selected_pop_scale":
            case "folder_child_overshoot":
                return clamp(value, 0.1f, 5.0f);
            case "folder_child_display_range_scale":
            case "folder_child_size_scale":
            case "folder_child_start_scale":
                return clamp(value, 0.05f, 5.0f);
            case "folder_child_direction_offset_deg":
                return clamp(value, -360f, 360f);
            case "sector_line_alpha":
            case "icon_alpha":
            case "fisheye_zoom_alpha":
            case "fisheye_other_alpha":
            case "fullscreen_blur_mask_alpha":
            case "fullscreen_dim_alpha":
            case "header_dim_alpha":
            case "region_dim_alpha":
            case "bubble_dim_alpha":
            case "global_dim_alpha":
            case "glass_matte_alpha":
            case "bubble_glass_matte_alpha":
            case "region_glass_matte_alpha":
            case "header_glass_matte_alpha":
            case "glass_border_light":
            case "glass_background_burn":
            case "glass_matte_white_mix":
            case "glass_matte_color_mix":
                return clamp(value, 0f, 1f);
            case "sector_line_width":
                return clamp(value, 0.1f, 20f);
            case "default_hint_text_letter_spacing":
                return clamp(value, -10f, 20f);
            case "glass_refract_ior":
                return clamp(value, 0.5f, 4.0f);
            case "glass_thickness":
                return clamp(value, 0f, 20f);
            case "glass_specular":
            case "glass_background_saturation":
                return clamp(value, 0f, 10f);
            case "glass_background_brightness":
                return clamp(value, -1f, 1f);
            case "glass_matte_color_power":
            case "glass_edge_power":
                return clamp(value, 0.1f, 20f);
            case "glass_edge_range":
                return clamp(value, 0f, 500f);
            case "glass_reflect_offset":
                return clamp(value, 0f, 5000f);
            default:
                return value;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void collectHighRiskShortcuts(
            List<ShortcutItem> items,
            String pathPrefix,
            int depth,
            List<String> out
    ) {
        if (items == null || depth > 4) {
            return;
        }
        for (ShortcutItem item : items) {
            if (item == null) {
                continue;
            }
            String title = item.title != null && !item.title.trim().isEmpty()
                    ? item.title.trim()
                    : "未命名快捷方式";
            String displayName = pathPrefix.isEmpty() ? title : pathPrefix + " > " + title;
            String riskReason = describeHighRiskReason(item);
            if (riskReason != null) {
                out.add(displayName + "（" + riskReason + "）");
            }
            if (ShortcutItem.ACTION_FOLDER.equals(item.actionType)) {
                collectHighRiskShortcuts(item.parseFolderChildren(), displayName, depth + 1, out);
            }
        }
    }

    private static String describeHighRiskReason(ShortcutItem item) {
        if (ShortcutItem.ACTION_SHELL.equals(item.actionType)) {
            return item.shellUseRoot ? "Shell Root 命令" : "Shell 命令";
        }
        if (ShortcutItem.ACTION_BROADCAST.equals(item.actionType)) {
            return item.shellUseRoot ? "自定义广播 + Root" : "自定义广播";
        }
        if (ShortcutItem.ACTION_ACTIVITY.equals(item.actionType)) {
            ActivityLaunchSpec spec = ActivityLaunchSpec.fromJson(item.actionPayload);
            if (spec.useRoot || item.shellUseRoot) {
                return "Activity Root 启动";
            }
        }
        if (item.shellUseRoot) {
            return "Root 权限";
        }
        return null;
    }

    private static String versionName(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }
}
