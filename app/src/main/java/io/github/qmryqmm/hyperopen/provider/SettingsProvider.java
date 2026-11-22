package io.github.qmryqmm.hyperopen.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.qmryqmm.hyperopen.BuildConfig;
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig;
import io.github.qmryqmm.hyperopen.safety.SafetyStore;
import io.github.qmryqmm.hyperopen.util.DePrefs;

public class SettingsProvider extends ContentProvider {
    private static final String TAG = "HyperOpen_Provider";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final int PER_USER_RANGE = 100000;
    public static final String AUTHORITY = "io.github.qmryqmm.hyperopen.provider";

    private volatile int cachedSystemUiAppId = -1;

    public static final String METHOD_GET_CONFIG = "get_config";
    public static final String METHOD_SAVE_FOD_CENTER = "save_fod_center";
    /** 轻量后台保活/解冻 Ping：仅用于在指纹面板展开时后台解冻模块进程，绝不启动任何前台 Activity */
    public static final String METHOD_PING = "ping";
    /**
     * 跨进程 Shell/Root 执行桥：SystemUI 侧把命令交回模块 App 进程执行。
     * 背景：SystemUI 进程里 su 请求 root 时 Magisk 无法弹授权/超时；模块 App 进程的 su 已获授权。
     * extras: cmd(String, 必填), useRoot(boolean, 默认 true), useRootStr(String, 兼容 adb content call)
     */
    public static final String METHOD_RUN_SHELL = "run_shell";

    /** 安全模式：启动闸门查询（SystemUI 每次启动时调用，并据此决定是否安装 Hook） */
    public static final String METHOD_SAFETY_CHECK = "safety_check";
    /** 安全模式：即时上报一次 Hook 相关崩溃 */
    public static final String METHOD_SAFETY_REPORT = "safety_report_crash";
    /** 安全模式：存活确认（Hook 装好后一段时间仍活着 → 本次会话视为健康） */
    public static final String METHOD_SAFETY_HEARTBEAT = "safety_heartbeat";
    /** 安全模式：只读状态查询（供模块 UI 显示） */
    public static final String METHOD_SAFETY_STATE = "safety_state";
    /** 安全模式：退出安全模式并重新启用 Hook */
    public static final String METHOD_SAFETY_EXIT = "safety_exit";
    /** 安全模式：清空崩溃计数与崩溃日志 */
    public static final String METHOD_SAFETY_CLEAR = "safety_clear";
    /** 安全模式：Hook 总开关 */
    public static final String METHOD_SAFETY_SET_ENABLED = "safety_set_enabled";
    /** 安全模式：把本次 SystemUI 会话登记为「已安装 Hook」，其异常退出才计入崩溃队列 */
    public static final String METHOD_SAFETY_MARK_HOOKED = "safety_mark_hooked";
    /** 安全模式：SystemUI 上报实时 Hook 注入成功与运行状态 */
    public static final String METHOD_SAFETY_REPORT_INJECTED = "safety_report_injected";

    @Override
    public boolean onCreate() {
        Log.i(TAG, "SettingsProvider 初始化成功");
        // Direct Boot：Provider 现在锁定期即被拉起，此刻尝试把旧版 CE 数据迁到 DE
        //（用户未解锁时会自动跳过，由后续 call()/MainActivity 入口继续迁移）
        try {
            Context sctx = getContext();
            if (sctx != null) {
                io.github.qmryqmm.hyperopen.util.DePrefs.migrateCeToDe(sctx, HyperOpenConfig.PREF_NAME);
            }
        } catch (Throwable ignored) {}
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (!isCallerAuthorized()) {
            Log.w(TAG, "拒绝未授权 Provider 调用: method=" + method + ", uid=" + Binder.getCallingUid());
            if (METHOD_RUN_SHELL.equals(method)) {
                Bundle denied = new Bundle();
                denied.putBoolean("accepted", false);
                return denied;
            }
            return new Bundle();
        }
        // 任意入口先补一次 CE→DE 迁移（已迁移时为进程内 HashSet 短路，代价可忽略）：
        // 解锁后的第一次调用把旧版数据搬过来，后续读取即为完整用户数据。
        try {
            Context mctx = getContext();
            if (mctx != null) {
                DePrefs.migrateCeToDe(mctx, HyperOpenConfig.PREF_NAME);
            }
        } catch (Throwable ignored) {}
        // ── 安全模式相关方法：写入方只在本进程（模块 App），SystemUI 侧只读或上报 ──
        // 注意：call() 内的任何未捕获异常都会跨进程抛回 SystemUI 并可能导致其崩溃，
        // 因此这里必须整段兜底，绝不让异常逃逸出 Provider。
        if (isSafetyMethod(method)) {
            Context sctx = getContext();
            if (sctx == null) {
                return wrap(null);
            }
            try {
                if (METHOD_SAFETY_CHECK.equals(method)) {
                    return wrap(SafetyStore.safetyCheck(sctx));
                }
                if (METHOD_SAFETY_REPORT.equals(method)) {
                    return wrap(SafetyStore.reportCrash(sctx, arg));
                }
                if (METHOD_SAFETY_HEARTBEAT.equals(method)) {
                    return wrap(SafetyStore.heartbeat(sctx));
                }
                if (METHOD_SAFETY_STATE.equals(method)) {
                    return wrap(SafetyStore.getState(sctx));
                }
                if (METHOD_SAFETY_EXIT.equals(method)) {
                    Log.w(TAG, "收到退出安全模式请求，Hook 将在 SystemUI 下次启动时恢复");
                    return wrap(SafetyStore.exitSafeMode(sctx));
                }
                if (METHOD_SAFETY_CLEAR.equals(method)) {
                    return wrap(SafetyStore.clearCrash(sctx));
                }
                if (METHOD_SAFETY_SET_ENABLED.equals(method)) {
                    boolean enabled = extras == null || extras.getBoolean("enabled", true);
                    return wrap(SafetyStore.setHookEnabled(sctx, enabled));
                }
                if (METHOD_SAFETY_MARK_HOOKED.equals(method)) {
                    return wrap(SafetyStore.markSessionHooked(sctx));
                }
                if (METHOD_SAFETY_REPORT_INJECTED.equals(method)) {
                    int pid = extras != null ? extras.getInt("pid", 0) : 0;
                    boolean active = extras == null || extras.getBoolean("active", true);
                    int hookCount = extras != null ? extras.getInt("hook_count", 0) : 0;
                    String version = extras != null ? extras.getString("version", "") : "";
                    return wrap(SafetyStore.reportHookInjected(sctx, pid, active, hookCount, version));
                }
            } catch (Throwable t) {
                Log.e(TAG, "安全模式方法 " + method + " 执行异常，返回保守结果", t);
                return wrap(null);
            }
        }

        if (METHOD_SAVE_FOD_CENTER.equals(method)) {
            Context ctx = getContext();
            if (ctx != null && extras != null) {
                try {
                    float fx = extras.getFloat("fod_x", 0f);
                    float fy = extras.getFloat("fod_y", 0f);
                    if (fx > 0f && fy > 0f && Float.isFinite(fx) && Float.isFinite(fy)) {
                        SharedPreferences sp = DePrefs.get(ctx, HyperOpenConfig.PREF_NAME);
                        sp.edit()
                            .putFloat("last_real_fod_x", fx)
                            .putFloat("last_real_fod_y", fy)
                            .commit();
                        DePrefs.applyWorldReadable(ctx, HyperOpenConfig.PREF_NAME);
                    }
                } catch (Throwable ignored) {}
            }
            return new Bundle();
        }

        if (METHOD_PING.equals(method)) {
            // 轻量静默探活：宿主拉起即完成解冻，不产生任何前台弹窗或界面展示
            Bundle r = new Bundle();
            r.putBoolean("pong", true);
            return r;
        }

        if (METHOD_RUN_SHELL.equals(method)) {
            Bundle r = new Bundle();
            r.putBoolean("accepted", false);
            try {
                if (extras != null) {
                    String cmd = extras.getString("cmd");
                    boolean useRoot = extras.getBoolean("useRoot", true);
                    if (BuildConfig.DEBUG) {
                        String useRootStr = extras.getString("useRootStr");
                        if (useRootStr != null) {
                            useRoot = "true".equalsIgnoreCase(useRootStr);
                        }
                    }
                    if (cmd != null && !cmd.isEmpty()) {
                        // fire-and-forget：runShellCommand 内部自行异步执行，不阻塞 Binder 调用
                        io.github.qmryqmm.hyperopen.model.ShortcutItem.runShellCommand(cmd, useRoot);
                        Log.i(TAG, "run_shell 桥接受理(" + (useRoot ? "root" : "shell") + "): " + cmd);
                        r.putBoolean("accepted", true);
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "run_shell 桥接执行异常", t);
            }
            return r;
        }

        if (METHOD_GET_CONFIG.equals(method)) {
            Context ctx = getContext();
            if (ctx != null) {
                SharedPreferences sp = DePrefs.get(ctx, HyperOpenConfig.PREF_NAME);
                Bundle bundle = new Bundle();
                bundle.putInt("material_style", safeGetInt(sp, "material_style", HyperOpenConfig.MATERIAL_GAUSSIAN_BLUR));
                bundle.putInt("offset_y", safeGetInt(sp, "offset_y", 100));
                bundle.putInt("region_diameter", safeGetInt(sp, "region_diameter", 220));
                bundle.putBoolean("show_region_bg", safeGetBoolean(sp, "show_region_bg", false));
                bundle.putBoolean("show_header_pill_bg", safeGetBoolean(sp, "show_header_pill_bg", false));
                bundle.putBoolean("show_header_pill", safeGetBoolean(sp, "show_header_pill", true));
                bundle.putInt("header_top_margin", safeGetInt(sp, "header_top_margin", 100));
                bundle.putInt("header_title_size", safeGetInt(sp, "header_title_size", 17));
                bundle.putInt("header_subtitle_size", safeGetInt(sp, "header_subtitle_size", 11));
                bundle.putInt("header_blur_radius", safeGetInt(sp, "header_blur_radius", 25));
                bundle.putBoolean("header_text_centered", safeGetBoolean(sp, "header_text_centered", true));
                bundle.putInt("icon_size", safeGetInt(sp, "icon_size", 60));
                bundle.putInt("max_icon_count", safeGetInt(sp, "max_icon_count", 0));
                int layoutStyle = safeGetInt(sp, "layout_style", HyperOpenConfig.LAYOUT_HONEYCOMB);
                bundle.putInt("layout_style", layoutStyle);
                bundle.putBoolean("stock_system_region", safeGetBoolean(sp, "stock_system_region", true));
                bundle.putBoolean("ring_sector_mode", safeGetBoolean(sp, "ring_sector_mode", true));
                bundle.putInt("sector_line_color", safeGetInt(sp, "sector_line_color", 0xFFBFBFBF));
                bundle.putFloat("sector_line_alpha", safeGetFloat(sp, "sector_line_alpha", 0.4f));
                bundle.putInt("sector_dash_length", safeGetInt(sp, "sector_dash_length", 6));
                bundle.putInt("sector_dash_gap", safeGetInt(sp, "sector_dash_gap", 5));
                bundle.putFloat("sector_line_width", safeGetFloat(sp, "sector_line_width", 1.2f));
                bundle.putBoolean("sector_highlight_enabled", safeGetBoolean(sp, "sector_highlight_enabled", true));
                bundle.putInt("anim_duration_ms", safeGetInt(sp, "anim_duration_ms", 320));
                bundle.putInt("panel_expand_anim_type", HyperOpenConfig.normalizePanelExpandAnimType(
                        safeGetInt(sp, "panel_expand_anim_type", HyperOpenConfig.PANEL_EXPAND_BLOOM),
                        layoutStyle,
                        safeGetBoolean(sp, "stock_system_anim", true)
                ));
                bundle.putInt("panel_expand_stagger_ms", safeGetInt(sp, "panel_expand_stagger_ms", 12));
                bundle.putBoolean("dismiss_anim_enabled", safeGetBoolean(sp, "dismiss_anim_enabled", true));
                bundle.putInt("panel_collapse_anim_type", safeGetInt(sp, "panel_collapse_anim_type", HyperOpenConfig.PANEL_COLLAPSE_SELECTED_POP));
                bundle.putInt("panel_collapse_duration_ms", safeGetInt(sp, "panel_collapse_duration_ms", 120));
                bundle.putInt("panel_collapse_stagger_ms", safeGetInt(sp, "panel_collapse_stagger_ms", 0));
                bundle.putFloat("panel_selected_pop_scale", safeGetFloat(sp, "panel_selected_pop_scale", 1.30f));
                bundle.putInt("global_hint_color_mode", safeGetInt(sp, "global_hint_color_mode", HyperOpenConfig.HINT_COLOR_MODE_AUTO));
                bundle.putInt("global_hint_custom_color", safeGetInt(sp, "global_hint_custom_color", 0xFFFFFFFF));
                bundle.putInt("default_hint_text_stroke", safeGetInt(sp, "default_hint_text_stroke", 0));
                bundle.putInt("default_hint_text_shadow", safeGetInt(sp, "default_hint_text_shadow", 0));
                bundle.putInt("default_hint_text_weight", safeGetInt(sp, "default_hint_text_weight", 3));
                bundle.putFloat("default_hint_text_letter_spacing", safeGetFloat(sp, "default_hint_text_letter_spacing", 0f));
                bundle.putBoolean("default_show_hint_title", safeGetBoolean(sp, "default_show_hint_title", true));
                bundle.putBoolean("default_show_hint_subtitle", safeGetBoolean(sp, "default_show_hint_subtitle", true));
                bundle.putInt("folder_hover_delay_ms", safeGetInt(sp, "folder_hover_delay_ms", 250));
                bundle.putFloat("folder_child_display_range_scale", safeGetFloat(sp, "folder_child_display_range_scale", 1f));
                bundle.putInt("folder_child_direction_mode", HyperOpenConfig.normalizeFolderChildDirectionMode(
                        safeGetInt(sp, "folder_child_direction_mode", HyperOpenConfig.FOLDER_CHILD_DIRECTION_OUTWARD)
                ));
                bundle.putFloat("folder_child_direction_offset_deg", safeGetFloat(sp, "folder_child_direction_offset_deg", 0f));
                bundle.putInt("folder_child_order_mode", safeGetInt(sp, "folder_child_order_mode", 0));
                bundle.putInt("folder_child_min_gap_dp", safeGetInt(sp, "folder_child_min_gap_dp", 8));
                bundle.putFloat("folder_child_size_scale", safeGetFloat(sp, "folder_child_size_scale", 1f));
                bundle.putInt("folder_child_expand_anim_type", safeGetInt(sp, "folder_child_expand_anim_type", HyperOpenConfig.FOLDER_CHILD_EXPAND_FAN_SPRING));
                bundle.putInt("folder_child_expand_duration_ms", safeGetInt(sp, "folder_child_expand_duration_ms", 180));
                bundle.putInt("folder_child_expand_stagger_ms", safeGetInt(sp, "folder_child_expand_stagger_ms", 15));
                bundle.putInt("folder_child_collapse_anim_type", safeGetInt(sp, "folder_child_collapse_anim_type", HyperOpenConfig.FOLDER_CHILD_COLLAPSE_CENTER));
                bundle.putInt("folder_child_collapse_duration_ms", safeGetInt(sp, "folder_child_collapse_duration_ms", 140));
                bundle.putInt("folder_child_collapse_stagger_ms", safeGetInt(sp, "folder_child_collapse_stagger_ms", 0));
                bundle.putFloat("folder_child_start_scale", safeGetFloat(sp, "folder_child_start_scale", 0.1f));
                bundle.putFloat("folder_child_overshoot", safeGetFloat(sp, "folder_child_overshoot", 1.2f));
                bundle.putFloat("max_scale", safeGetFloat(sp, "max_scale", 1.60f));
                bundle.putInt("influence_radius", safeGetInt(sp, "influence_radius", 70));
                bundle.putFloat("icon_alpha", safeGetFloat(sp, "icon_alpha", 1.0f));
                bundle.putFloat("fisheye_zoom_alpha", safeGetFloat(sp, "fisheye_zoom_alpha", 1.0f));
                bundle.putFloat("fisheye_other_alpha", safeGetFloat(sp, "fisheye_other_alpha", 1.0f));
                bundle.putInt("icon_corner_radius", safeGetInt(sp, "icon_corner_radius", 50));
                bundle.putFloat("fullscreen_blur_mask_alpha", safeGetFloat(sp, "fullscreen_blur_mask_alpha", 1.0f));
                bundle.putFloat("fullscreen_dim_alpha", safeGetFloat(sp, "fullscreen_dim_alpha", 0.45f));
                bundle.putFloat("header_dim_alpha", safeGetFloat(sp, "header_dim_alpha", 0f));
                bundle.putFloat("region_dim_alpha", safeGetFloat(sp, "region_dim_alpha", 0f));
                bundle.putFloat("bubble_dim_alpha", safeGetFloat(sp, "bubble_dim_alpha", 0f));
                bundle.putInt("show_delay_ms", safeGetInt(sp, "show_delay_ms", 500));
                bundle.putBoolean("disable_system_enter_anim", safeGetBoolean(sp, "disable_system_enter_anim", false));
                bundle.putBoolean("face_unlock_quick_open", safeGetBoolean(sp, "face_unlock_quick_open", true));
                bundle.putBoolean("ble_unlock_quick_open", safeGetBoolean(sp, "ble_unlock_quick_open", true));
                bundle.putBoolean("haptic_enabled", safeGetBoolean(sp, "haptic_enabled", true));
                bundle.putInt("haptic_strength", safeGetInt(sp, "haptic_strength", 140));
                bundle.putInt("haptic_duration_ms", safeGetInt(sp, "haptic_duration_ms", 24));
                bundle.putInt("haptic_mode", safeGetInt(sp, "haptic_mode", HyperOpenConfig.HAPTIC_MODE_STOCK));
                bundle.putInt("haptic_stock_wave_expand", safeGetInt(sp, "haptic_stock_wave_expand", HyperOpenConfig.HAPTIC_STOCK_WAVE_LONG_PRESS));
                bundle.putBoolean("haptic_brake_pulse", safeGetBoolean(sp, "haptic_brake_pulse", false));
                bundle.putInt("haptic_double_gap_ms", safeGetInt(sp, "haptic_double_gap_ms", 28));
                bundle.putBoolean("haptic_slide_enabled", safeGetBoolean(sp, "haptic_slide_enabled", true));
                bundle.putInt("haptic_slide_strength", safeGetInt(sp, "haptic_slide_strength", 100));
                bundle.putInt("haptic_slide_duration_ms", safeGetInt(sp, "haptic_slide_duration_ms", 12));
                bundle.putInt("haptic_slide_mode", safeGetInt(sp, "haptic_slide_mode", HyperOpenConfig.HAPTIC_MODE_STOCK));
                bundle.putInt("haptic_stock_wave_slide", safeGetInt(sp, "haptic_stock_wave_slide", HyperOpenConfig.HAPTIC_STOCK_WAVE_MESH_NORMAL));
                bundle.putBoolean("haptic_slide_brake_pulse", safeGetBoolean(sp, "haptic_slide_brake_pulse", false));
                bundle.putInt("haptic_slide_double_gap_ms", safeGetInt(sp, "haptic_slide_double_gap_ms", 28));
                bundle.putFloat("last_real_fod_x", safeGetFloat(sp, "last_real_fod_x", 0f));
                bundle.putFloat("last_real_fod_y", safeGetFloat(sp, "last_real_fod_y", 0f));
                bundle.putInt("region_blur_radius", safeGetInt(sp, "region_blur_radius", 25));
                bundle.putInt("icon_spacing_offset", safeGetInt(sp, "icon_spacing_offset", 5));
                int bubblePad = safeGetInt(sp, "bubble_padding_percent", 0);
                bundle.putInt("bubble_padding_percent", bubblePad);
                bundle.putBoolean("show_bubble_bg",
                        sp.contains("show_bubble_bg") ? safeGetBoolean(sp, "show_bubble_bg", false) : (bubblePad > 0));
                bundle.putInt("bubble_blur_radius", safeGetInt(sp, "bubble_blur_radius", 25));
                // 旧版 selected_icon_scale：仅作迁移折算用途传给 SystemUI 侧合并进 max_scale（已废弃的独立参数）
                bundle.putFloat("selected_icon_scale", safeGetFloat(sp, "selected_icon_scale", 1.0f));
                bundle.putInt("app_icon_crop_mode", safeGetInt(sp, "app_icon_crop_mode", HyperOpenConfig.CROP_ADAPTIVE_EXPAND));
                bundle.putBoolean("glyph_pad_enabled", safeGetBoolean(sp, "glyph_pad_enabled", true));
                bundle.putInt("glyph_pad_color", safeGetInt(sp, "glyph_pad_color", 0xFF2A82E4));
                bundle.putInt("glyph_pad_mode", safeGetInt(sp, "glyph_pad_mode", HyperOpenConfig.GLYPH_PAD_MODE_TRANSLUCENT));
                bundle.putBoolean("icon_glow_effect", false);
                bundle.putInt("glass_preset", safeGetInt(sp, "glass_preset", HyperOpenConfig.GLASS_PRESET_DEFAULT));
                bundle.putFloat("glass_refract_ior", safeGetFloat(sp, "glass_refract_ior", 1.12f));
                bundle.putFloat("glass_thickness", safeGetFloat(sp, "glass_thickness", 1.5f));
                bundle.putFloat("glass_specular", safeGetFloat(sp, "glass_specular", 0.8f));
                bundle.putFloat("glass_matte_alpha", safeGetFloat(sp, "glass_matte_alpha", 0.12f));
                bundle.putFloat("glass_border_light", safeGetFloat(sp, "glass_border_light", 0.45f));
                bundle.putFloat("glass_background_saturation", safeGetFloat(sp, "glass_background_saturation", 1.5f));
                bundle.putFloat("glass_background_brightness", safeGetFloat(sp, "glass_background_brightness", 0f));
                bundle.putFloat("glass_background_burn", safeGetFloat(sp, "glass_background_burn", 0f));
                bundle.putFloat("glass_matte_white_mix", safeGetFloat(sp, "glass_matte_white_mix", 0f));
                bundle.putFloat("glass_matte_color_mix", safeGetFloat(sp, "glass_matte_color_mix", 0.4f));
                bundle.putFloat("glass_matte_color_power", safeGetFloat(sp, "glass_matte_color_power", 1.5f));
                bundle.putFloat("glass_edge_range", safeGetFloat(sp, "glass_edge_range", 50f));
                bundle.putFloat("glass_edge_power", safeGetFloat(sp, "glass_edge_power", 4f));
                bundle.putFloat("glass_reflect_offset", safeGetFloat(sp, "glass_reflect_offset", 600f));
                if (sp.contains("global_blur_radius")) {
                    bundle.putInt("global_blur_radius", safeGetInt(sp, "global_blur_radius", 25));
                }
                bundle.putFloat("global_dim_alpha", safeGetFloat(sp, "global_dim_alpha", 0f));
                bundle.putBoolean("bubble_material_follow_global", safeGetBoolean(sp, "bubble_material_follow_global", true));
                bundle.putBoolean("region_material_follow_global", safeGetBoolean(sp, "region_material_follow_global", true));
                bundle.putBoolean("header_material_follow_global", safeGetBoolean(sp, "header_material_follow_global", true));
                bundle.putFloat("bubble_glass_matte_alpha", safeGetFloat(sp, "bubble_glass_matte_alpha", 0.12f));
                bundle.putFloat("region_glass_matte_alpha", safeGetFloat(sp, "region_glass_matte_alpha", 0.12f));
                bundle.putFloat("header_glass_matte_alpha", safeGetFloat(sp, "header_glass_matte_alpha", 0.12f));
                bundle.putBoolean("blur_baseline_healed", safeGetBoolean(sp, "blur_baseline_healed", false));
                bundle.putString("shortcuts_json", safeGetString(sp, "shortcuts_json", null));
                Log.d(TAG, "成功响应 SystemUI 配置查询: offsetY=" + bundle.getInt("offset_y"));
                return bundle;
            }
        }
        return null;
    }

    private boolean isCallerAuthorized() {
        int callingUid = Binder.getCallingUid();
        if (callingUid < 0) {
            return false;
        }
        int myUid = Process.myUid();
        if (callingUid == myUid) {
            return true;
        }
        if (BuildConfig.DEBUG && (callingUid == Process.ROOT_UID || callingUid == Process.SHELL_UID)) {
            return true;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return false;
        }
        int systemUiAppId = resolveSystemUiAppId(ctx);
        if (systemUiAppId <= 0 || getAppId(callingUid) != systemUiAppId) {
            return false;
        }
        String callingPkg;
        try {
            callingPkg = getCallingPackage();
        } catch (Throwable t) {
            Log.w(TAG, "校验调用方包名异常: uid=" + callingUid, t);
            return false;
        }
        if (callingPkg != null && !SYSTEMUI_PACKAGE.equals(callingPkg)) {
            Log.w(TAG, "拒绝同 UID 非 SystemUI 包名调用: pkg=" + callingPkg + ", uid=" + callingUid);
            return false;
        }
        return true;
    }

    private int resolveSystemUiAppId(@NonNull Context ctx) {
        int cached = cachedSystemUiAppId;
        if (cached > 0) {
            return cached;
        }
        try {
            PackageManager pm = ctx.getPackageManager();
            if (pm == null) {
                return -1;
            }
            ApplicationInfo ai = pm.getApplicationInfo(SYSTEMUI_PACKAGE, PackageManager.MATCH_SYSTEM_ONLY);
            if (ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                int appId = getAppId(ai.uid);
                if (appId > 0) {
                    cachedSystemUiAppId = appId;
                    return appId;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "解析 com.android.systemui appId 失败: " + t.getMessage());
        }
        return -1;
    }

    private static int getAppId(int uid) {
        return uid >= 0 ? uid % PER_USER_RANGE : -1;
    }

    private static int safeGetInt(@NonNull SharedPreferences sp, @NonNull String key, int def) {
        try {
            return sp.getInt(key, def);
        } catch (ClassCastException e) {
            Log.w(TAG, "读取整型配置类型不匹配，回退默认值: key=" + key, e);
            return def;
        }
    }

    private static float safeGetFloat(@NonNull SharedPreferences sp, @NonNull String key, float def) {
        try {
            float v = sp.getFloat(key, def);
            if (!Float.isFinite(v)) {
                Log.w(TAG, "读取浮点配置出现非法值(NaN/Inf)，回退默认值: key=" + key);
                return def;
            }
            return v;
        } catch (ClassCastException e) {
            Log.w(TAG, "读取浮点配置类型不匹配，回退默认值: key=" + key, e);
            return def;
        }
    }

    private static boolean safeGetBoolean(@NonNull SharedPreferences sp, @NonNull String key, boolean def) {
        try {
            return sp.getBoolean(key, def);
        } catch (ClassCastException e) {
            Log.w(TAG, "读取布尔配置类型不匹配，回退默认值: key=" + key, e);
            return def;
        }
    }

    @Nullable
    private static String safeGetString(@NonNull SharedPreferences sp, @NonNull String key, @Nullable String def) {
        try {
            return sp.getString(key, def);
        } catch (ClassCastException e) {
            Log.w(TAG, "读取字符串配置类型不匹配，回退默认值: key=" + key, e);
            return def;
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "vnd.android.cursor.item/vnd.io.github.qmryqmm.hyperopen.config";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    private static boolean isSafetyMethod(@Nullable String method) {
        if (method == null) {
            return false;
        }
        return METHOD_SAFETY_CHECK.equals(method)
            || METHOD_SAFETY_REPORT.equals(method)
            || METHOD_SAFETY_HEARTBEAT.equals(method)
            || METHOD_SAFETY_STATE.equals(method)
            || METHOD_SAFETY_EXIT.equals(method)
            || METHOD_SAFETY_CLEAR.equals(method)
            || METHOD_SAFETY_SET_ENABLED.equals(method)
            || METHOD_SAFETY_MARK_HOOKED.equals(method)
            || METHOD_SAFETY_REPORT_INJECTED.equals(method);
    }

    /**
     * SafetyStore 返回的 Bundle 可能为 null（例如 Context 获取失败、存储暂不可用）。
     *
     * 语义修正：null ≠ 「安全模式激活」。这里返回一个带 storage_unavailable=true 的
     * 「状态不可查」结果，调用方（SafetyGuard）必须把它当「未决」处理——不写结论、
     * 稍后重试，绝不能据此永久拒绝安装 Hook 或误报安全模式。
     * 旧版语义会把「Direct Boot 锁定期读不到存储」误判成安全模式，
     * 是「重启后模块不生效」的根因之一。
     */
    @NonNull
    private Bundle wrap(@Nullable Bundle b) {
        if (b != null) {
            return b;
        }
        Log.e(TAG, "安全模式状态读取失败（存储暂不可用），返回可重试的未决结果");
        Bundle fallback = new Bundle();
        fallback.putBoolean("storage_unavailable", true);
        fallback.putBoolean("allowed", false);
        fallback.putBoolean("safe_mode", false);
        fallback.putBoolean("hook_enabled", false);
        fallback.putBoolean("just_triggered", false);
        fallback.putBoolean("premature_exit", false);
        fallback.putInt("crash_count", 0);
        fallback.putInt("total_crash", 0);
        fallback.putInt("threshold", SafetyStore.CRASH_THRESHOLD);
        return fallback;
    }
}
