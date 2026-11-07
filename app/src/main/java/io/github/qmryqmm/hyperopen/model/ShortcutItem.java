package io.github.qmryqmm.hyperopen.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.qmryqmm.hyperopen.util.IpcExecutor;

public class ShortcutItem {
    private static final String TAG = "HyperOpen_Shortcut";

    public static final String ACTION_APP = "APP";
    public static final String ACTION_INTENT_URI = "INTENT_URI";
    public static final String ACTION_ACTIVITY = "ACTIVITY";
    public static final String ACTION_BROADCAST = "BROADCAST";
    public static final String ACTION_FLASHLIGHT = "FLASHLIGHT";
    public static final String ACTION_SHELL = "SHELL";
    public static final String ACTION_XIAOAI = "XIAOAI";
    public static final String ACTION_MUTE = "MUTE";
    public static final String ACTION_DND = "DND";
    public static final String ACTION_CAMERA = "CAMERA";
    public static final String ACTION_FOLDER = "FOLDER";

    /**
     * INTENT_URI 内嵌的目标用户标记（URI 写法：i.hyperopen_user=999）。
     * Intent URI 规范没有 user 字段，跨用户（如 999 = MIUI/HyperOS 应用双开 XSpace/分身）
     * 只能在发起侧指定；分发时该标记会被剥离，不会传给目标 Activity。
     */
    public static final String EXTRA_TARGET_USER = "hyperopen_user";

    public String id;
    public String title;
    public String subtitle;
    public String actionType;
    public String actionPayload;
    /** 手动指定图标来源包名（优先级最高，留空则自动解析） */
    public String iconPackage;
    /** 手动指定内置符号 key（如 scan/flashlight/camera/calc/note/mic/music/settings/bus/wallet/sparkle） */
    public String iconGlyph;
    public int tintColor;
    public boolean enabled;

    /** 该快捷方式独享的 Miuix 矢量底垫配置 */
    public boolean glyphPadEnabled = false;
    public int glyphPadColor = 0xFF2A82E4;
    public int glyphPadMode = 1; // 0=SOLID, 1=TRANSLUCENT, 2=INVERTED
    /** 该快捷方式独享的应用图标形状裁切方案：1=标准抗锯齿圆形裁切, 2=自适应外扩满铺 */
    public int appIconCropMode = 2;
    /** 排版模式下的用户自定义偏移量（dp），用于关闭自动吸附时的自由摆放 */
    public float customOffsetX = 0f;
    public float customOffsetY = 0f;
    /** 启动目标 Android 用户 ID：0 = 本机主空间，999 = MIUI/HyperOS 应用双开（XSpace 分身） */
    public int userId = 0;
    /** SHELL 动作是否以 root（su）权限执行；false = 普通 shell（sh）身份 */
    public boolean shellUseRoot = false;

    /* ===== 提示文字（顶部胶囊）自定义样式 ===== */
    /** 是否跟随全局提示文字样式设置（默认 true；关闭后展开单项专属自定义） */
    public boolean followGlobalHintStyle = true;
    /** 提示标题颜色（0xAARRGGBB）；0 = 跟随 tintColor（旧行为） */
    public int hintTextColor = 0;
    /** 提示标题透明度 0.05~1.00；默认 1.00 */
    public float hintTextAlpha = 1.0f;
    /** 提示副标题颜色；0 = 跟随标题颜色 */
    public int hintTextColorSub = 0;
    /** 提示副标题透明度；-1 = 跟随标题透明度 ×0.82（旧行为 0xAA） */
    public float hintTextAlphaSub = -1f;
    /** 文字投影强度 0~100（步进 5，0=关闭）；黑色柔光半径 = 值/20×4px */
    public int hintTextShadow = 0;
    /** 文字描边宽度 0~4dp（0=关闭）；深色描边提升壁纸背景可读性 */
    public int hintTextStroke = 0;
    /** 字重：0=Light 1=Regular 2=Medium 3=Bold（作用于标题，默认与旧版假粗体一致） */
    public int hintTextWeight = 3;
    /** 字间距 -2~+6（步进 0.5，实现系数 ×0.05em）；0=系统默认 */
    public float hintTextLetterSpacing = 0f;
    /** 选中时是否显示标题/副标题 */
    public boolean showHintTitle = true;
    public boolean showHintSubtitle = true;

    public ShortcutItem(String id, String title, String subtitle, String actionType, String actionPayload, int tintColor, boolean enabled) {
        this(id, title, subtitle, actionType, actionPayload, "", "", tintColor, enabled, false, 0xFF2A82E4, 1, 2, 0f, 0f);
    }

    public ShortcutItem(String id, String title, String subtitle, String actionType, String actionPayload,
                        String iconPackage, String iconGlyph, int tintColor, boolean enabled) {
        this(id, title, subtitle, actionType, actionPayload, iconPackage, iconGlyph, tintColor, enabled, false, 0xFF2A82E4, 1, 2, 0f, 0f);
    }

    public ShortcutItem(String id, String title, String subtitle, String actionType, String actionPayload,
                        String iconPackage, String iconGlyph, int tintColor, boolean enabled,
                        boolean glyphPadEnabled, int glyphPadColor, int glyphPadMode) {
        this(id, title, subtitle, actionType, actionPayload, iconPackage, iconGlyph, tintColor, enabled, glyphPadEnabled, glyphPadColor, glyphPadMode, 2, 0f, 0f);
    }

    public ShortcutItem(String id, String title, String subtitle, String actionType, String actionPayload,
                        String iconPackage, String iconGlyph, int tintColor, boolean enabled,
                        boolean glyphPadEnabled, int glyphPadColor, int glyphPadMode, int appIconCropMode) {
        this(id, title, subtitle, actionType, actionPayload, iconPackage, iconGlyph, tintColor, enabled, glyphPadEnabled, glyphPadColor, glyphPadMode, appIconCropMode, 0f, 0f);
    }

    public ShortcutItem(String id, String title, String subtitle, String actionType, String actionPayload,
                        String iconPackage, String iconGlyph, int tintColor, boolean enabled,
                        boolean glyphPadEnabled, int glyphPadColor, int glyphPadMode, int appIconCropMode,
                        float customOffsetX, float customOffsetY) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.actionType = actionType;
        this.actionPayload = actionPayload;
        this.iconPackage = iconPackage == null ? "" : iconPackage;
        this.iconGlyph = iconGlyph == null ? "" : iconGlyph;
        this.tintColor = tintColor;
        this.enabled = enabled;
        this.glyphPadEnabled = glyphPadEnabled;
        this.glyphPadColor = glyphPadColor;
        this.glyphPadMode = glyphPadMode;
        this.appIconCropMode = appIconCropMode > 0 ? appIconCropMode : 2;
        this.customOffsetX = customOffsetX;
        this.customOffsetY = customOffsetY;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title);
            json.put("subtitle", subtitle);
            json.put("actionType", actionType);
            json.put("actionPayload", actionPayload);
            json.put("iconPackage", iconPackage == null ? "" : iconPackage);
            json.put("iconGlyph", iconGlyph == null ? "" : iconGlyph);
            json.put("tintColor", tintColor);
            json.put("enabled", enabled);
            json.put("glyphPadEnabled", glyphPadEnabled);
            json.put("glyphPadColor", glyphPadColor);
            json.put("glyphPadMode", glyphPadMode);
            json.put("appIconCropMode", appIconCropMode);
            json.put("customOffsetX", (double) customOffsetX);
            json.put("customOffsetY", (double) customOffsetY);
            json.put("userId", userId);
            json.put("shellUseRoot", shellUseRoot);
            json.put("followGlobalHintStyle", followGlobalHintStyle);
            json.put("hintTextColor", hintTextColor);
            json.put("hintTextAlpha", (double) hintTextAlpha);
            json.put("hintTextColorSub", hintTextColorSub);
            json.put("hintTextAlphaSub", (double) hintTextAlphaSub);
            json.put("hintTextShadow", hintTextShadow);
            json.put("hintTextStroke", hintTextStroke);
            json.put("hintTextWeight", hintTextWeight);
            json.put("hintTextLetterSpacing", (double) hintTextLetterSpacing);
            json.put("showHintTitle", showHintTitle);
            json.put("showHintSubtitle", showHintSubtitle);
        } catch (JSONException e) {
            Log.e(TAG, "Error serializing ShortcutItem to JSON", e);
        }
        return json;
    }

    public static ShortcutItem fromJson(JSONObject json) {
        ShortcutItem item = new ShortcutItem(
            json.optString("id", ""),
            json.optString("title", "未命名"),
            json.optString("subtitle", ""),
            json.optString("actionType", ACTION_APP),
            json.optString("actionPayload", ""),
            json.optString("iconPackage", ""),
            json.optString("iconGlyph", ""),
            json.optInt("tintColor", Color.CYAN),
            json.optBoolean("enabled", true),
            json.optBoolean("glyphPadEnabled", false),
            json.optInt("glyphPadColor", 0xFF2A82E4),
            json.optInt("glyphPadMode", 1),
            json.optInt("appIconCropMode", 2),
            (float) json.optDouble("customOffsetX", 0.0),
            (float) json.optDouble("customOffsetY", 0.0)
        );
        item.userId = json.optInt("userId", 0);
        item.shellUseRoot = json.optBoolean("shellUseRoot", false);
        item.hintTextColor = json.optInt("hintTextColor", 0);
        item.hintTextAlpha = (float) json.optDouble("hintTextAlpha", 1.0);
        item.hintTextColorSub = json.optInt("hintTextColorSub", 0);
        item.hintTextAlphaSub = (float) json.optDouble("hintTextAlphaSub", -1.0);
        item.hintTextShadow = json.optInt("hintTextShadow", 0);
        item.hintTextStroke = json.optInt("hintTextStroke", 0);
        item.hintTextWeight = json.optInt("hintTextWeight", 3);
        item.hintTextLetterSpacing = (float) json.optDouble("hintTextLetterSpacing", 0.0);
        item.showHintTitle = json.optBoolean("showHintTitle", true);
        item.showHintSubtitle = json.optBoolean("showHintSubtitle", true);
        if (json.has("followGlobalHintStyle")) {
            item.followGlobalHintStyle = json.optBoolean("followGlobalHintStyle", true);
        } else {
            // 旧版本数据兼容：若以前特意配置过非默认文字参数，保持 false；否则默认跟随全局
            boolean hasCustom = item.hintTextColor != 0 || item.hintTextStroke > 0
                || item.hintTextShadow > 0 || item.hintTextWeight != 3 || item.hintTextLetterSpacing != 0f;
            item.followGlobalHintStyle = !hasCustom;
        }
        return item;
    }

    public ShortcutItem copy() {
        ShortcutItem item = new ShortcutItem(
            id, title, subtitle, actionType, actionPayload,
            iconPackage, iconGlyph, tintColor, enabled,
            glyphPadEnabled, glyphPadColor, glyphPadMode, appIconCropMode,
            customOffsetX, customOffsetY
        );
        item.userId = userId;
        item.shellUseRoot = shellUseRoot;
        item.followGlobalHintStyle = followGlobalHintStyle;
        item.hintTextColor = hintTextColor;
        item.hintTextAlpha = hintTextAlpha;
        item.hintTextColorSub = hintTextColorSub;
        item.hintTextAlphaSub = hintTextAlphaSub;
        item.hintTextShadow = hintTextShadow;
        item.hintTextStroke = hintTextStroke;
        item.hintTextWeight = hintTextWeight;
        item.hintTextLetterSpacing = hintTextLetterSpacing;
        item.showHintTitle = showHintTitle;
        item.showHintSubtitle = showHintSubtitle;
        return item;
    }

    /**
     * 智能解析该快捷方式的图标主色调（底垫颜色优先，其次为 tintColor）
     */
    public int resolveIconPrimaryColor() {
        if (glyphPadEnabled && glyphPadColor != 0) {
            return glyphPadColor;
        }
        return tintColor != 0 ? tintColor : 0xFF2A82E4;
    }

    /** 解析后的提示标题颜色：hintTextColor 非 0 用自定义，否则智能回退 */
    public int resolveHintTitleColor() {
        return resolveHintTitleColor(null);
    }

    public int resolveHintTitleColor(io.github.qmryqmm.hyperopen.config.HyperOpenConfig config) {
        if (hintTextColor != 0) {
            return hintTextColor;
        }
        if (config != null) {
            if (config.globalHintColorMode == io.github.qmryqmm.hyperopen.config.HyperOpenConfig.HINT_COLOR_MODE_WHITE) {
                return 0xFFFFFFFF;
            } else if (config.globalHintColorMode == io.github.qmryqmm.hyperopen.config.HyperOpenConfig.HINT_COLOR_MODE_CUSTOM) {
                return config.globalHintCustomColor;
            }
        }
        // 模式 0 / 默认：智能跟随图标底色/主题色
        return resolveIconPrimaryColor();
    }

    /** 解析后的提示标题透明度，钳在 0.05~1.00 */
    public float resolveHintTitleAlpha() {
        float a = hintTextAlpha <= 0f ? 1f : hintTextAlpha;
        return Math.max(0.05f, Math.min(1f, a));
    }

    /** 解析后的提示副标题颜色：自定义色 → 标题色 → 智能底色链式回退 */
    public int resolveHintSubtitleColor() {
        return resolveHintSubtitleColor(null);
    }

    public int resolveHintSubtitleColor(io.github.qmryqmm.hyperopen.config.HyperOpenConfig config) {
        return hintTextColorSub != 0 ? hintTextColorSub : resolveHintTitleColor(config);
    }

    /** 解析后的提示副标题透明度：自定义值 → 标题透明度×0.82（对应旧版 0xAA） */
    public float resolveHintSubtitleAlpha() {
        if (hintTextAlphaSub >= 0f) {
            return Math.max(0.05f, Math.min(1f, hintTextAlphaSub));
        }
        return resolveHintTitleAlpha() * 0.82f;
    }

    /**
     * 解析文件夹子项列表
     */
    public List<ShortcutItem> parseFolderChildren() {
        if (!ACTION_FOLDER.equals(actionType) || actionPayload == null || actionPayload.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return io.github.qmryqmm.hyperopen.config.HyperOpenConfig.parseShortcutsJson(actionPayload);
    }

    /**
     * 将子项列表序列化写回 actionPayload
     */
    public void setFolderChildren(List<ShortcutItem> children) {
        this.actionType = ACTION_FOLDER;
        this.actionPayload = io.github.qmryqmm.hyperopen.config.HyperOpenConfig.serializeShortcutsJson(children);
    }

    /**
     * 在后台 IPC 线程池中异步执行该快捷方式动作：
     * 即使其他模块（如 freeformx）挂在 system_server 的 startActivity 路径上造成 Binder 线程拥堵，
     * 也不会阻塞 SystemUI 主线程与手势收尾。
     */
    public void executeAsync(final Context context) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        IpcExecutor.runAsync(new Runnable() {
            @Override
            public void run() {
                execute(appCtx);
            }
        });
    }

    /**
     * 在 SystemUI 上下文中分发执行该快捷方式动作
     */
    public void execute(Context context) {
        if (context == null) return;
        try {
            switch (actionType) {
                case ACTION_APP: {
                    Intent intent = context.getPackageManager().getLaunchIntentForPackage(actionPayload);
                    if (intent != null) {
                        if (userId > 0) {
                            // 编辑界面「启动用户」指定了跨用户（如 999 应用双开/分身）
                            launchAsUser(context, intent, userId);
                            break;
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        context.startActivity(intent);
                    } else {
                        showToast(context, "未找到目标应用: " + actionPayload);
                    }
                    break;
                }
                case ACTION_INTENT_URI: {
                    // 最终目标用户提到 try 外：失败兜底时要据此禁止回落主空间应用
                    int targetUser = userId > 0 ? userId : -1;
                    try {
                        Intent intent = Intent.parseUri(actionPayload, Intent.URI_INTENT_SCHEME);
                        // 剥离 URI 内嵌用户标记（i.hyperopen_user=N）并解析最终目标用户：
                        // 编辑界面「启动用户」字段（userId>0）优先，其次 URI 内嵌标记
                        int uriUser = extractTargetUser(intent);
                        if (targetUser <= 0) targetUser = uriUser;
                        if (targetUser > 0) {
                            // 指定了目标用户（如 999 应用双开/分身），走跨用户启动
                            launchAsUser(context, intent, targetUser);
                            break;
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (SecurityException se) {
                        Log.w(TAG, "直接拉起 Intent 被安全权限拦截，尝试通过 Root/Shell 兜底执行: " + actionPayload, se);
                        // 当从 MainActivity 运行且遇到非导出组件权限问题时，通过 su 执行 am start
                        runRootAmStart(context, actionPayload);
                    } catch (Throwable t) {
                        Log.e(TAG, "启动 Intent 异常: " + actionPayload, t);
                        // 指定了目标用户却失败时绝不回落主空间应用（否则表现为"开的是主微信"），只提示失败
                        if (targetUser > 0) {
                            showToast(context, "跨用户启动失败(用户 " + targetUser + "): " + t.getMessage());
                        } else if (actionPayload.contains("com.tencent.mm")) {
                            Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
                            if (launch != null) {
                                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(launch);
                            }
                        } else if (actionPayload.contains("com.eg.android.AlipayGphone") || actionPayload.contains("alipays://")) {
                            Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.eg.android.AlipayGphone");
                            if (launch != null) {
                                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(launch);
                            }
                        } else {
                            showToast(context, "启动失败: " + t.getMessage());
                        }
                    }
                    break;
                }
                case ACTION_ACTIVITY: {
                    // 结构化 Intent：组件 / Data URI / Extra / Action / NEW_TASK / Root 见 ActivityLaunchSpec JSON
                    ActivityLaunchSpec spec = ActivityLaunchSpec.fromJson(actionPayload);
                    if (spec.useRoot) {
                        // Root 打开：只识别 目标组件 / 目标包名（am start -n，可与「启动用户」组合），其余参数忽略
                        ComponentName cn = spec.resolveComponent();
                        if (cn == null) {
                            String pkg = spec.packageName == null ? "" : spec.packageName.trim();
                            if (!pkg.isEmpty()) {
                                Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
                                if (launch != null) cn = launch.getComponent();
                            }
                        }
                        if (cn == null) {
                            showToast(context, "Root 打开失败：请填写目标组件（仅类名）与目标包名");
                            break;
                        }
                        String cmd = "am start " + (userId > 0 ? "--user " + userId + " " : "")
                                + "-n " + cn.flattenToString();
                        runRootCommand(context, cmd);
                    } else {
                        Intent intent = spec.buildIntent();
                        if (userId > 0) {
                            launchAsUser(context, intent, userId);
                        } else {
                            context.startActivity(intent);
                        }
                    }
                    break;
                }
                case ACTION_BROADCAST: {
                    Intent intent = new Intent(actionPayload);
                    context.sendBroadcast(intent);
                    break;
                }
                case ACTION_FLASHLIGHT: {
                    toggleFlashlight(context);
                    break;
                }
                case ACTION_XIAOAI: {
                    wakeUpXiaoAi(context);
                    break;
                }
                case ACTION_MUTE: {
                    toggleSilentMode(context);
                    break;
                }
                case ACTION_DND: {
                    toggleDndMode(context);
                    break;
                }
                case ACTION_CAMERA: {
                    openCamera(context);
                    break;
                }
                case ACTION_SHELL: {
                    runShellCommand(context, actionPayload, shellUseRoot);
                    break;
                }
                default:
                    Log.w(TAG, "未知动作类型: " + actionType);
                    break;
            }
        } catch (Throwable t) {
            Log.e(TAG, "执行快捷动作失败: " + title, t);
            showToast(context, "启动失败: " + t.getMessage());
        }
    }

    private static void wakeUpXiaoAi(Context context) {
        try {
            Intent wakeBroadcast = new Intent("com.miui.voiceassist.WAKEUP");
            wakeBroadcast.setPackage("com.miui.voiceassist");
            context.sendBroadcast(wakeBroadcast);
        } catch (Throwable ignored) {}

        try {
            Intent intent = new Intent("android.intent.action.VOICE_COMMAND");
            intent.setPackage("com.miui.voiceassist");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            Intent assistIntent = new Intent(Intent.ACTION_ASSIST);
            assistIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(assistIntent);
        } catch (Throwable t) {
            Log.e(TAG, "唤醒小爱失败", t);
            showToast(context, "唤醒小爱失败: " + t.getMessage());
        }
    }

    private static void toggleSilentMode(Context context) {
        android.media.AudioManager am =
                (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        final boolean ownApp = isOwnAppProcess(context);
        try {
            int currentMode = am.getRingerMode();
            final boolean toNormal = currentMode == android.media.AudioManager.RINGER_MODE_SILENT
                    || currentMode == android.media.AudioManager.RINGER_MODE_VIBRATE;
            final int target = toNormal
                    ? android.media.AudioManager.RINGER_MODE_NORMAL
                    : android.media.AudioManager.RINGER_MODE_SILENT;
            try {
                am.setRingerMode(target);
                showToast(context, toNormal ? "已开启响铃模式" : "已开启静音模式");
            } catch (Throwable direct) {
                // 直调被权限/厂商限制（App 进程缺权限、SystemUI 个别 ROM 拒绝等）
                // AOSP AudioManagerShellCommand：cmd audio set-ringer-mode NORMAL|VIBRATE|SILENT（大小写敏感）
                Log.w(TAG, "setRingerMode 直调失败: " + describe(direct));
                if (ownApp) {
                    // App 进程：同步 Root 执行并立即回读——真成功/真失败当下见分晓，不再等 1.8s
                    runRootCommandSync(
                            "cmd audio set-ringer-mode " + (toNormal ? "NORMAL" : "SILENT"), 8000);
                    if (am.getRingerMode() == target) {
                        showToast(context, toNormal ? "已开启响铃模式" : "已开启静音模式");
                    } else {
                        showToast(context, "切换静音失败: " + describe(direct)
                                + "；Root 兜底未生效，请检查本应用 Root 权限");
                    }
                } else {
                    // SystemUI 进程：su 弹不了授权 → 桥接到模块 App 进程执行，1.8s 后延迟复读诚实提示
                    runRootCommand(context,
                            "cmd audio set-ringer-mode " + (toNormal ? "NORMAL" : "SILENT"));
                    verifyRingerLater(context, am, target, toNormal, direct, ownApp);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "切换静音失败", t);
            showToast(context, "切换静音失败: " + describe(t));
        }
    }

    /** 静音切换 Root 兜底后的延迟复读校验（1.8s 后回查铃声模式，成功/失败如实提示） */
    private static void verifyRingerLater(Context context, android.media.AudioManager am,
            int target, boolean toNormal, Throwable direct, boolean ownApp) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (am.getRingerMode() == target) {
                    showToast(context, toNormal ? "已开启响铃模式" : "已开启静音模式");
                } else {
                    showToast(context, "切换静音失败: " + describe(direct)
                            + (ownApp ? "；Root 兜底未生效，请检查本应用 Root 权限" : ""));
                }
            } catch (Throwable t) {
                showToast(context, "切换静音失败: " + describe(t));
            }
        }, 1800);
    }

    private static void toggleDndMode(Context context) {
        android.app.NotificationManager nm =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        final boolean ownApp = isOwnAppProcess(context);
        try {
            // 1) 读取当前过滤器（App 进程无「勿扰访问」权限时读取可能被拒 → -1）
            int currentFilter = readDndFilterQuiet(nm);
            boolean hasAccess = hasDndAccess(nm);
            // 2) App 进程：权限缺失时先 Root 静默自投 allow_dnd（一次生效，随后直调即成）
            if (ownApp && (!hasAccess || currentFilter < 0)
                    && runRootCommandSync(
                            "cmd notification allow_dnd " + context.getPackageName(), 8000)) {
                hasAccess = hasDndAccess(nm);
                if (currentFilter < 0) currentFilter = readDndFilterQuiet(nm);
            }
            if (currentFilter < 0) {
                // 读不到状态无法计算切换方向：如实失败；无权限时引导系统授权页
                Log.w(TAG, "勿扰状态读取失败 hasAccess=" + hasAccess);
                showToast(context, "切换勿扰失败: 无法读取勿扰状态（请检查勿扰权限）");
                if (ownApp && !hasAccess) guideDndAccessSettings(context);
                return;
            }
            final boolean toPriority =
                    currentFilter == android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
            final int target = toPriority
                    ? android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    : android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
            try {
                nm.setInterruptionFilter(target);
                showToast(context, toPriority ? "已开启勿扰模式" : "已关闭勿扰模式");
            } catch (Throwable direct) {
                // 通知策略访问未授予（App 进程必现 SecurityException / SystemUI 个别 ROM 也未授）
                Log.w(TAG, "setInterruptionFilter 直调失败: " + describe(direct));
                if (ownApp) {
                    // App 进程：同步 Root 直接执行本次切换并立即回读——真成功/真失败当下见分晓
                    runRootCommandSync(
                            "cmd notification set_dnd " + (toPriority ? "priority" : "off"), 8000);
                    if (readDndFilterQuiet(nm) == target) {
                        showToast(context, toPriority ? "已开启勿扰模式" : "已关闭勿扰模式");
                    } else {
                        showToast(context, "切换勿扰失败: " + describe(direct));
                        if (!hasDndAccess(nm)) guideDndAccessSettings(context);
                    }
                } else {
                    // SystemUI 进程：su 弹不了授权 → 桥接到模块 App 进程执行 + 延迟回读
                    runRootCommand(context, "cmd notification allow_dnd " + context.getPackageName());
                    runRootCommand(context,
                            "cmd notification set_dnd " + (toPriority ? "priority" : "off"));
                    verifyDndLater(context, nm, target, toPriority, direct);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "切换勿扰模式失败", t);
            showToast(context, "切换勿扰失败: " + describe(t));
        }
    }

    /** 勿扰切换 Root 兜底后的延迟复读校验（1.8s 后回查过滤器，回读受权限挡住时如实说明） */
    private static void verifyDndLater(Context context, android.app.NotificationManager nm,
            int target, boolean toPriority, Throwable direct) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (nm.getCurrentInterruptionFilter() == target) {
                    showToast(context, toPriority ? "已开启勿扰模式" : "已关闭勿扰模式");
                } else {
                    showToast(context, "切换勿扰失败: " + describe(direct));
                }
            } catch (Throwable t) {
                // 回读也被策略权限挡住：无法证伪，如实说明是 Root 提交而非确认成功
                showToast(context, "已通过 Root 提交勿扰切换（状态回读受限）");
            }
        }, 1800);
    }

    /** 异常可读描述：message 为 null 时退回类名，避免 toast 出现 "null" */
    private static String describe(Throwable t) {
        String msg = t.getMessage();
        return (msg == null || msg.isEmpty()) ? t.getClass().getSimpleName() : msg;
    }

    /** 是否运行在模块 App 自身进程（编辑界面「立即启动测试」；SystemUI 进程包名为 com.android.systemui） */
    private static boolean isOwnAppProcess(Context context) {
        try {
            return context != null
                    && "io.github.qmryqmm.hyperopen".equals(context.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * App 进程内同步执行 root 命令（带超时，返回是否 exit 0）。
     * 仅限 ownApp 调用——SystemUI 进程 su 授权弹窗不可见会挂起，那里必须走异步桥接。
     */
    private static boolean runRootCommandSync(String cmd, long timeoutMs) {
        // 先尝试 su -mm，若不支持或执行失败则降级到普通 su
        boolean ok = executeShellProcess("su -mm", cmd, timeoutMs);
        if (!ok) {
            ok = executeShellProcess("su", cmd, timeoutMs);
        }
        return ok;
    }

    /** 读取当前勿扰过滤器；被权限拒绝等异常时返回 -1 */
    private static int readDndFilterQuiet(android.app.NotificationManager nm) {
        try {
            return nm.getCurrentInterruptionFilter();
        } catch (Throwable t) {
            Log.w(TAG, "读取勿扰过滤器失败: " + describe(t));
            return -1;
        }
    }

    /** 是否已获系统「勿扰访问」（通知策略访问）权限 */
    private static boolean hasDndAccess(android.app.NotificationManager nm) {
        try {
            return nm.isNotificationPolicyAccessGranted();
        } catch (Throwable t) {
            return false;
        }
    }

    /** App 进程内引导到系统「勿扰访问」授权页（无 Root 时的唯一出路） */
    private static void guideDndAccessSettings(Context context) {
        try {
            Intent i = new Intent(
                    android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            showToast(context, "已打开「勿扰访问」设置，请允许 HyperOpen 后重试");
        } catch (Throwable t) {
            Log.w(TAG, "打开勿扰授权页失败", t);
        }
    }

    private static void openCamera(Context context) {
        try {
            Intent intent = new Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        } catch (Throwable t) {
            try {
                Intent fallback = new Intent("android.media.action.STILL_IMAGE_CAMERA");
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Throwable t2) {
                Log.e(TAG, "打开相机失败", t2);
                showToast(context, "打开相机失败: " + t2.getMessage());
            }
        }
    }

    private static volatile boolean isFlashlightOn = false;
    private static volatile boolean torchCallbackRegistered = false;

    private static synchronized void ensureTorchCallback(CameraManager cm) {
        if (torchCallbackRegistered || cm == null) return;
        try {
            cm.registerTorchCallback(new CameraManager.TorchCallback() {
                @Override
                public void onTorchModeChanged(String cameraId, boolean enabled) {
                    isFlashlightOn = enabled;
                }

                @Override
                public void onTorchModeUnavailable(String cameraId) {
                    isFlashlightOn = false;
                }
            }, new Handler(Looper.getMainLooper()));
            torchCallbackRegistered = true;
        } catch (Throwable t) {
            Log.w(TAG, "注册手电筒状态回调失败", t);
        }
    }

    private static void toggleFlashlight(Context context) {
        try {
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm != null) {
                ensureTorchCallback(cm);
                String[] cameraIdList = cm.getCameraIdList();
                if (cameraIdList.length > 0) {
                    String cameraId = cameraIdList[0];
                    boolean nextState = !isFlashlightOn;
                    cm.setTorchMode(cameraId, nextState);
                    isFlashlightOn = nextState;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "切换手电筒状态失败", t);
        }
    }

    /**
     * 以指定用户身份启动 Activity（如 userId=999 → MIUI/HyperOS 应用双开 XSpace/分身）。
     * Intent URI 规范里没有 user 字段，用户只能在发起侧指定：
     * 1) 系统上下文 createContextAsUser（需 INTERACT_ACROSS_USERS[_FULL]，SystemUI/系统签名可直连）
     * 2) Root 兜底：su -c 'am start --user N "intent:..."'
     */
    private static void launchAsUser(Context context, Intent intent, int userId) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            // getUserHandleForUid(uid) 按 uid/PER_USER_RANGE(100000) 取用户 → userId*100000 即该用户的 UserHandle
            android.os.UserHandle target =
                    android.os.UserHandle.getUserHandleForUid(userId * 100000);
            // 0) Context#startActivityAsUser（SDK 桩非公开）：SystemUI 等系统上下文
            //    持有 INTERACT_ACROSS_USERS[_FULL] 时可直连，最快路径
            try {
                java.lang.reflect.Method m =
                        Context.class.getMethod("startActivityAsUser", Intent.class, android.os.UserHandle.class);
                m.invoke(context, intent, target);
                return;
            } catch (Throwable t) {
                Log.w(TAG, "startActivityAsUser 启动 user " + userId + " 不可用，尝试 createContextAsUser", t);
            }
            // 1) Context#createContextAsUser（SDK 桩非公开）反射调用（API 30+ 运行时存在）
            try {
                java.lang.reflect.Method m =
                        Context.class.getMethod("createContextAsUser", android.os.UserHandle.class, int.class);
                Context userCtx = (Context) m.invoke(context, target, 0);
                userCtx.startActivity(intent);
                return;
            } catch (Throwable t) {
                Log.w(TAG, "createContextAsUser 启动 user " + userId + " 失败，改用 am start --user 兜底", t);
            }
        }
        try {
            String uri = intent.toUri(Intent.URI_INTENT_SCHEME);
            runRootCommand(context, "am start --user " + userId + " " + quoteForSh(uri));
        } catch (Throwable t) {
            Log.e(TAG, "跨用户启动失败: user=" + userId + " intent=" + intent, t);
            showToast(context, "启动失败(需跨用户权限或 Root): " + t.getMessage());
        }
    }

    /** 从 Intent 中取出并剥离 hyperopen_user 标记（URI 写法：i.hyperopen_user=999），未指定返回 -1 */
    private static int extractTargetUser(Intent intent) {
        int user = intent.getIntExtra(EXTRA_TARGET_USER, -1);
        if (user < 0) {
            String s = intent.getStringExtra(EXTRA_TARGET_USER);
            if (s != null) {
                try {
                    user = Integer.parseInt(s.trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        if (user >= 0) {
            intent.removeExtra(EXTRA_TARGET_USER);
        }
        return user;
    }

    /**
     * 把 userId 同步写回 payload 的 i.hyperopen_user 标记（保持 payload 是合法 Intent URI）。
     * 双保险：payload 自描述可携带；且 SystemUI 里跑的旧版模块代码（只认 URI 标记、不认 userId
     * 字段）同样能按用户启动。userId > 0 写入/替换标记；userId <= 0 剥掉已有标记。
     */
    public static String syncUserMarker(String payload, int userId) {
        if (payload == null) return null;
        String p = payload.replaceAll(";[iS]\\." + EXTRA_TARGET_USER + "=[^;]*", "");
        if (userId > 0) {
            int endIdx = p.lastIndexOf(";end");
            if (endIdx >= 0) {
                p = p.substring(0, endIdx) + ";i." + EXTRA_TARGET_USER + "=" + userId + p.substring(endIdx);
            }
        }
        return p;
    }

    /** 从 INTENT_URI payload 中读出内嵌用户标记（i.hyperopen_user=N），无标记或解析失败返回 -1 */
    public static int extractUserFromPayload(String payload) {
        if (payload == null || payload.isEmpty()) return -1;
        try {
            return extractTargetUser(Intent.parseUri(payload, Intent.URI_INTENT_SCHEME));
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 用双引号包裹并转义，避免 Intent URI 中的分号被 sh 截断识别为命令分隔符 */
    private static String quoteForSh(String s) {
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }

    private static void runRootAmStart(Context context, String payload) {
        runRootCommand(context, "am start " + quoteForSh(payload));
    }

    /**
     * root 命令统一入口：优先桥接给拥有 Root 授权的模块 App 后台代理执行（避免 SystemUI/KernelSU 无权调用 su）；
     * 若桥接未受理或无响应，自动降级回退到当前进程本地 su 执行。
     */
    private static void runRootCommand(Context context, String cmd) {
        // 如果当前就是 App 自身进程（如用户在编辑页面点立即测试），直接本地执行
        if (isOwnAppProcess(context)) {
            runShellBinary("su", cmd);
            return;
        }

        // 在 SystemUI 进程中：
        // 因面板完全展开时已静默预热解冻模块进程，此处桥接将直接由已解冻的模块 App 秒级执行；
        // 若桥接失败再回退本地尝试
        if (runRootViaBridge(context, cmd)) {
            return;
        }
        runShellBinary("su", cmd);
    }

    /** Shell 动作执行入口（供 Provider 桥接回调）：useRoot=true 经 su 以 root 身份执行，false 以普通 sh 身份执行 */
    public static void runShellCommand(String cmd, boolean useRoot) {
        runShellBinary(useRoot ? "su" : "sh", cmd);
    }

    /** Shell 动作执行入口：root 命令桥接到模块 App 进程（SystemUI 内 su 常因锁屏无法弹 Magisk 授权而失败） */
    private static void runShellCommand(Context context, String cmd, boolean useRoot) {
        if (useRoot) {
            runRootCommand(context, cmd);
        } else {
            runShellBinary("sh", cmd);
        }
    }

    /**
     * 把 root/shell 命令桥接给模块 App 进程执行（SettingsProvider 的 run_shell 方法）。
     * 背景：指纹面板在 SystemUI 进程里执行，su 从系统进程请求 root 时 Magisk 无法弹授权/超时；
     * 而模块 App 进程的 su 已获授权（编辑界面测试按钮即在该进程执行成功）。
     * fire-and-forget：返回是否受理，不等待命令结果。
     */
    private static boolean runRootViaBridge(Context context, String cmd) {
        try {
            Bundle b = new Bundle();
            b.putString("cmd", cmd);
            b.putBoolean("useRoot", true);
            b.putString("useRootStr", "true"); // 兼容 adb content call 等只支持字符串 extra 的入口
            Bundle r = context.getContentResolver().call(
                    Uri.parse("content://io.github.qmryqmm.hyperopen.provider"),
                    "run_shell", null, b);
            boolean accepted = r != null && r.getBoolean("accepted", false);
            if (!accepted) {
                Log.w(TAG, "run_shell 桥接未受理，退回本地执行: " + cmd);
            }
            return accepted;
        } catch (Throwable t) {
            Log.w(TAG, "run_shell 桥接失败，退回本地执行: " + cmd, t);
            return false;
        }
    }

    private static void runShellBinary(String shell, String cmd) {
        // 走独立的守护线程执行，绝不霸占 IpcExecutor 单线程 HandlerThread 造成 SystemUI IPC 阻塞
        Thread runner = new Thread(() -> {
            try {
                if ("su".equals(shell)) {
                    // 优化执行：对于 root 命令，优先以 su -mm 执行，失败则无缝降级到普通 su
                    boolean ok = executeShellProcess("su -mm", cmd, 15000L);
                    if (!ok) {
                        executeShellProcess("su", cmd, 15000L);
                    }
                } else {
                    executeShellProcess(shell, cmd, 15000L);
                }
            } catch (Throwable t) {
                Log.e(TAG, ("su".equals(shell) ? "Root" : "Shell") + " 命令执行异常: " + cmd, t);
            }
        }, "HyperOpen-ShellRunner");
        runner.setDaemon(true);
        runner.start();
    }

    private static boolean executeShellProcess(String shellBin, String cmd, long timeoutMs) {
        Process p = null;
        DataOutputStream os = null;
        try {
            p = Runtime.getRuntime().exec(shellBin);
            final InputStream in = p.getInputStream();
            final InputStream err = p.getErrorStream();
            Thread drainIn = new Thread(() -> drainStream(in), "HyperOpen-DrainIn");
            Thread drainErr = new Thread(() -> drainStream(err), "HyperOpen-DrainErr");
            drainIn.setDaemon(true);
            drainErr.setDaemon(true);
            drainIn.start();
            drainErr.start();

            os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + "\nexit\n");
            os.flush();
            os.close();
            os = null;

            boolean finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                Log.w(TAG, shellBin + " 命令执行超时(" + timeoutMs + "ms): " + cmd);
                p.destroyForcibly();
                return false;
            }
            int code = p.exitValue();
            if (code != 0) {
                Log.w(TAG, shellBin + " 命令 exit=" + code + ": " + cmd);
            }
            return code == 0;
        } catch (Throwable t) {
            Log.w(TAG, shellBin + " 执行异常: " + t.getMessage());
            return false;
        } finally {
            if (os != null) {
                try { os.close(); } catch (Throwable ignored) {}
            }
            if (p != null) {
                try { p.destroy(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void drainStream(InputStream stream) {
        if (stream == null) return;
        try {
            byte[] buf = new byte[1024];
            while (stream.read(buf) != -1) {
                // discard
            }
        } catch (Throwable ignored) {
        } finally {
            try { stream.close(); } catch (Throwable ignored) {}
        }
    }

    private static void showToast(Context context, String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT).show()
        );
    }
}
