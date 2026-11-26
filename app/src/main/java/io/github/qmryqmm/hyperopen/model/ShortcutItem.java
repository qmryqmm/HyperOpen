package io.github.qmryqmm.hyperopen.model;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Color;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.qmryqmm.hyperopen.log.ShortcutLaunchLogStore;
import io.github.qmryqmm.hyperopen.util.IpcExecutor;

public class ShortcutItem {
    private static final String TAG = "HyperOpen_Shortcut";
    private static final int MAX_VALID_USER_ID = 9999;

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

    public static final float DEFAULT_GLYPH_PAD_ALPHA = 0.65f;
    public static final float MIN_GLYPH_PAD_ALPHA = 0.05f;

    /** 该快捷方式独享的矢量图标底垫配置（纯色底板 + 独立不透明度） */
    public boolean glyphPadEnabled = false;
    public int glyphPadColor = 0xFF2A82E4;
    public float glyphPadAlpha = DEFAULT_GLYPH_PAD_ALPHA;

    public static float clampPadAlpha(float alpha) {
        if (Float.isNaN(alpha) || alpha <= 0f) return DEFAULT_GLYPH_PAD_ALPHA;
        return Math.max(MIN_GLYPH_PAD_ALPHA, Math.min(1f, alpha));
    }
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
        this(id, title, subtitle, actionType, actionPayload, "", "", tintColor, enabled, false, 0xFF2A82E4, DEFAULT_GLYPH_PAD_ALPHA, 2, 0f, 0f);
    }

    public ShortcutItem(String id, String title, String subtitle, String actionType, String actionPayload,
                        String iconPackage, String iconGlyph, int tintColor, boolean enabled) {
        this(id, title, subtitle, actionType, actionPayload, iconPackage, iconGlyph, tintColor, enabled, false, 0xFF2A82E4, DEFAULT_GLYPH_PAD_ALPHA, 2, 0f, 0f);
    }

    public ShortcutItem(String id, String title, String subtitle, String actionType, String actionPayload,
                        String iconPackage, String iconGlyph, int tintColor, boolean enabled,
                        boolean glyphPadEnabled, int glyphPadColor, float glyphPadAlpha) {
        this(id, title, subtitle, actionType, actionPayload, iconPackage, iconGlyph, tintColor, enabled, glyphPadEnabled, glyphPadColor, glyphPadAlpha, 2, 0f, 0f);
    }

    public ShortcutItem(String id, String title, String subtitle, String actionType, String actionPayload,
                        String iconPackage, String iconGlyph, int tintColor, boolean enabled,
                        boolean glyphPadEnabled, int glyphPadColor, float glyphPadAlpha, int appIconCropMode) {
        this(id, title, subtitle, actionType, actionPayload, iconPackage, iconGlyph, tintColor, enabled, glyphPadEnabled, glyphPadColor, glyphPadAlpha, appIconCropMode, 0f, 0f);
    }

    public ShortcutItem(String id, String title, String subtitle, String actionType, String actionPayload,
                        String iconPackage, String iconGlyph, int tintColor, boolean enabled,
                        boolean glyphPadEnabled, int glyphPadColor, float glyphPadAlpha, int appIconCropMode,
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
        this.glyphPadAlpha = clampPadAlpha(glyphPadAlpha);
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
            json.put("glyphPadAlpha", (double) clampPadAlpha(glyphPadAlpha));
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

    @SuppressWarnings("deprecation")
    public static ShortcutItem fromJson(JSONObject json) {
        int tintColor = json.optInt("tintColor", Color.CYAN);
        boolean padEnabled = json.optBoolean("glyphPadEnabled", false);
        int padColor = json.optInt("glyphPadColor", 0xFF2A82E4);
        float padAlpha;
        if (json.has("glyphPadAlpha")) {
            padAlpha = clampPadAlpha((float) json.optDouble("glyphPadAlpha", DEFAULT_GLYPH_PAD_ALPHA));
        } else {
            // 无损迁移旧版 glyphPadMode（SOLID / TRANSLUCENT / INVERTED）到独立双层参数，保持视觉完全一致
            int legacyMode = json.optInt("glyphPadMode", io.github.qmryqmm.hyperopen.config.HyperOpenConfig.GLYPH_PAD_MODE_TRANSLUCENT);
            if (padEnabled) {
                if (legacyMode == io.github.qmryqmm.hyperopen.config.HyperOpenConfig.GLYPH_PAD_MODE_SOLID) {
                    padAlpha = 1.0f;
                    tintColor = 0xFFFFFFFF;
                } else if (legacyMode == io.github.qmryqmm.hyperopen.config.HyperOpenConfig.GLYPH_PAD_MODE_INVERTED) {
                    if (tintColor == 0) {
                        tintColor = padColor != 0 ? padColor : 0xFFFFFFFF;
                    }
                    padColor = 0xFFFFFFFF;
                    padAlpha = 0.20f;
                } else {
                    padAlpha = DEFAULT_GLYPH_PAD_ALPHA;
                    tintColor = 0xFFFFFFFF;
                }
            } else {
                if (legacyMode == io.github.qmryqmm.hyperopen.config.HyperOpenConfig.GLYPH_PAD_MODE_SOLID) {
                    padAlpha = 1.0f;
                } else if (legacyMode == io.github.qmryqmm.hyperopen.config.HyperOpenConfig.GLYPH_PAD_MODE_INVERTED) {
                    padAlpha = 0.20f;
                } else {
                    padAlpha = DEFAULT_GLYPH_PAD_ALPHA;
                }
            }
        }
        ShortcutItem item = new ShortcutItem(
            json.optString("id", ""),
            json.optString("title", "未命名"),
            json.optString("subtitle", ""),
            json.optString("actionType", ACTION_APP),
            json.optString("actionPayload", ""),
            json.optString("iconPackage", ""),
            json.optString("iconGlyph", ""),
            tintColor,
            json.optBoolean("enabled", true),
            padEnabled,
            padColor,
            padAlpha,
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
            glyphPadEnabled, glyphPadColor, glyphPadAlpha, appIconCropMode,
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

    private static boolean isChromatic(int color) {
        if (color == 0) return false;
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b))) >= 28;
    }

    /**
     * 智能解析该快捷方式的图标主色调（高不透明有彩色底垫优先，低透/黑白底垫回退有彩色前景色）
     */
    public int resolveIconPrimaryColor() {
        boolean padVisible = glyphPadEnabled && glyphPadColor != 0 && clampPadAlpha(glyphPadAlpha) >= 0.40f;
        if (padVisible && isChromatic(glyphPadColor)) {
            return glyphPadColor;
        }
        if (isChromatic(tintColor)) {
            return tintColor;
        }
        if (padVisible) {
            return glyphPadColor;
        }
        return tintColor != 0 ? tintColor : 0xFF2A82E4;
    }

    /** 解析后的提示标题颜色：跟随全局时按全局模式，否则 hintTextColor 非 0 用自定义、回退智能底色 */
    public int resolveHintTitleColor() {
        return resolveHintTitleColor(null);
    }

    public int resolveHintTitleColor(io.github.qmryqmm.hyperopen.config.HyperOpenConfig config) {
        if (!followGlobalHintStyle && hintTextColor != 0) {
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

    /** 解析后的提示副标题颜色：跟随全局时与标题一致；否则自定义色 → 标题色 → 智能底色链式回退 */
    public int resolveHintSubtitleColor() {
        return resolveHintSubtitleColor(null);
    }

    public int resolveHintSubtitleColor(io.github.qmryqmm.hyperopen.config.HyperOpenConfig config) {
        if (!followGlobalHintStyle && hintTextColorSub != 0) {
            return hintTextColorSub;
        }
        return resolveHintTitleColor(config);
    }

    /** 解析后的提示副标题透明度：自定义值 → 标题透明度×0.82（对应旧版 0xAA） */
    public float resolveHintSubtitleAlpha() {
        if (hintTextAlphaSub >= 0f) {
            return Math.max(0.05f, Math.min(1f, hintTextAlphaSub));
        }
        return resolveHintTitleAlpha() * 0.82f;
    }

    /**
     * 解析文件夹子项列表；异常或为空时直接返回空列表（绝不回退到默认顶层快捷方式）。
     */
    public List<ShortcutItem> parseFolderChildren() {
        List<ShortcutItem> list = new ArrayList<>();
        if (!ACTION_FOLDER.equals(actionType) || actionPayload == null) {
            return list;
        }
        String trimmed = actionPayload.trim();
        if (trimmed.isEmpty()) {
            return list;
        }
        try {
            org.json.JSONArray arr = new org.json.JSONArray(trimmed);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) {
                    list.add(ShortcutItem.fromJson(obj));
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "解析文件夹子项 JSON 失败，返回空列表", t);
            return new ArrayList<>();
        }
        return list;
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
        String defaultSource = isOwnAppProcess(context)
                ? ShortcutLaunchLogStore.SOURCE_UNKNOWN
                : ShortcutLaunchLogStore.SOURCE_LOCKSCREEN;
        executeAsync(context, defaultSource);
    }

    /**
     * 在后台 IPC 线程池中异步执行该快捷方式动作，并记录触发来源。
     */
    public void executeAsync(final Context context, final String source) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        final String resolvedSource = (source == null || source.isEmpty())
                ? (isOwnAppProcess(appCtx) ? ShortcutLaunchLogStore.SOURCE_UNKNOWN : ShortcutLaunchLogStore.SOURCE_LOCKSCREEN)
                : source;
        IpcExecutor.runShortcutAsync(new Runnable() {
            @Override
            public void run() {
                execute(appCtx, resolvedSource);
            }
        });
    }

    /**
     * 在 SystemUI 或 App 上下文中分发执行该快捷方式动作
     */
    public void execute(Context context) {
        String defaultSource = isOwnAppProcess(context)
                ? ShortcutLaunchLogStore.SOURCE_UNKNOWN
                : ShortcutLaunchLogStore.SOURCE_LOCKSCREEN;
        execute(context, defaultSource);
    }

    /**
     * 分发执行该快捷方式动作并记录唯一一条启动日志
     */
    public void execute(Context context, String source) {
        if (context == null) return;
        final long startMs = System.currentTimeMillis();
        final String resolvedSource = (source == null || source.isEmpty())
                ? (isOwnAppProcess(context) ? ShortcutLaunchLogStore.SOURCE_UNKNOWN : ShortcutLaunchLogStore.SOURCE_LOCKSCREEN)
                : source;
        final ShortcutLaunchLogStore.Entry entry = createLogTemplate(resolvedSource, actionPayload, false);
        boolean delegatedOrLogged = false;
        try {
            switch (actionType) {
                case ACTION_APP: {
                    String pkg = actionPayload != null ? actionPayload.trim() : "";
                    entry.params = "pkg=" + pkg + (userId > 0 ? ", user=" + userId : "");
                    if (pkg.isEmpty()) {
                        showToast(context, "未指定目标应用包名");
                        recordExecutionLog(context, entry, startMs, false, "未指定目标应用包名", "");
                        delegatedOrLogged = true;
                        break;
                    }
                    if (userId > 0) {
                        // 跨用户启动（如 999 应用双开 / 工作资料 / 手机分身）：优先零 Root 直启目标空间
                        launchAppForUser(context, pkg, userId, entry, startMs);
                        delegatedOrLogged = true;
                        break;
                    }
                    Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        context.startActivity(intent);
                        recordExecutionLog(context, entry, startMs, true, "", "");
                        delegatedOrLogged = true;
                    } else {
                        String errMsg = "未找到目标应用: " + actionPayload;
                        showToast(context, errMsg);
                        recordExecutionLog(context, entry, startMs, false, errMsg, "");
                        delegatedOrLogged = true;
                    }
                    break;
                }
                case ACTION_INTENT_URI: {
                    entry.params = actionPayload != null ? actionPayload : "";
                    if (actionPayload == null || actionPayload.trim().isEmpty()) {
                        showToast(context, "Intent URI 为空");
                        recordExecutionLog(context, entry, startMs, false, "Intent URI 为空", "");
                        delegatedOrLogged = true;
                        break;
                    }
                    // 最终目标用户提到 try 外：失败兜底时要据此禁止回落主空间应用
                    int targetUser = userId > 0 ? userId : -1;
                    try {
                        Intent intent = Intent.parseUri(actionPayload, Intent.URI_INTENT_SCHEME);
                        // 剥离 URI 内嵌用户标记（i.hyperopen_user=N）并解析最终目标用户：
                        // 编辑界面「启动用户」字段（userId>0）优先，其次 URI 内嵌标记
                        int uriUser = extractTargetUser(intent);
                        if (targetUser <= 0) targetUser = uriUser;
                        entry.userId = targetUser > 0 ? targetUser : userId;
                        if (targetUser > 0) {
                            // 指定了目标用户（如 999 应用双开/分身），走跨用户启动
                            launchAsUser(context, intent, targetUser, entry, startMs);
                            delegatedOrLogged = true;
                            break;
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        recordExecutionLog(context, entry, startMs, true, "", "");
                        delegatedOrLogged = true;
                    } catch (SecurityException se) {
                        Log.w(TAG, "直接拉起 Intent 被安全权限拦截，尝试通过 Root/Shell 兜底执行: " + actionPayload, se);
                        // 当从 MainActivity 运行且遇到非导出组件权限问题时，通过 su 执行 am start
                        entry.useRoot = true;
                        runRootAmStart(context, actionPayload, entry);
                        delegatedOrLogged = true;
                    } catch (Throwable t) {
                        Log.e(TAG, "启动 Intent 异常: " + actionPayload, t);
                        // 指定了目标用户却失败时绝不回落主空间应用（否则表现为"开的是主微信"），只提示失败
                        if (targetUser > 0) {
                            String errMsg = "跨用户启动失败(用户 " + targetUser + "): " + describe(t);
                            showToast(context, errMsg);
                            recordExecutionLog(context, entry, startMs, false, errMsg, "");
                            delegatedOrLogged = true;
                        } else if (actionPayload.contains("com.tencent.mm")) {
                            Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
                            if (launch != null) {
                                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(launch);
                                recordExecutionLog(context, entry, startMs, true, "", "回落启动微信主应用");
                            } else {
                                String errMsg = "启动失败: " + describe(t);
                                showToast(context, errMsg);
                                recordExecutionLog(context, entry, startMs, false, errMsg, "");
                            }
                            delegatedOrLogged = true;
                        } else if (actionPayload.contains("com.eg.android.AlipayGphone") || actionPayload.contains("alipays://")) {
                            Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.eg.android.AlipayGphone");
                            if (launch != null) {
                                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(launch);
                                recordExecutionLog(context, entry, startMs, true, "", "回落启动支付宝主应用");
                            } else {
                                String errMsg = "启动失败: " + describe(t);
                                showToast(context, errMsg);
                                recordExecutionLog(context, entry, startMs, false, errMsg, "");
                            }
                            delegatedOrLogged = true;
                        } else {
                            String errMsg = "启动失败: " + describe(t);
                            showToast(context, errMsg);
                            recordExecutionLog(context, entry, startMs, false, errMsg, "");
                            delegatedOrLogged = true;
                        }
                    }
                    break;
                }
                case ACTION_ACTIVITY: {
                    // 结构化 Intent：组件 / Data URI / Extra / Action / NEW_TASK / Root 见 ActivityLaunchSpec JSON
                    ActivityLaunchSpec spec = ActivityLaunchSpec.fromJson(actionPayload);
                    entry.useRoot = spec.useRoot;
                    entry.params = describeActivitySpec(spec, actionPayload);
                    if (spec.useRoot) {
                        // Root 打开：只识别 目标组件 / 目标包名（am start -n，可与「启动用户」组合），其余参数忽略
                        ComponentName cn = spec.resolveComponent();
                        if (cn == null) {
                            String pkg = spec.packageName == null ? "" : spec.packageName.trim();
                            if (!pkg.isEmpty()) {
                                if (userId > 0) {
                                    UserHandle targetHandle = resolveUserHandle(userId);
                                    if (targetHandle == null) {
                                        String errMsg = "用户 ID 无效: " + userId;
                                        showToast(context, errMsg);
                                        recordExecutionLog(context, entry, startMs, false, errMsg, "");
                                        delegatedOrLogged = true;
                                        break;
                                    }
                                    if (android.os.Build.VERSION.SDK_INT >= 21) {
                                        try {
                                            LauncherApps la = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                                            if (la != null) {
                                                List<LauncherActivityInfo> activities = la.getActivityList(pkg, targetHandle);
                                                if (activities != null && !activities.isEmpty()) {
                                                    cn = activities.get(0).getComponentName();
                                                }
                                            }
                                        } catch (Throwable t) {
                                            Log.w(TAG, "LauncherApps 解析目标空间入口失败: pkg=" + pkg + ", user=" + userId, t);
                                        }
                                    }
                                    if (cn == null) {
                                        // 目标空间未暴露给 LauncherApps（如独立用户或主空间未安装），直接走 Root 在目标空间解析并启动
                                        resolveAndLaunchAppViaRoot(context, pkg, userId, entry);
                                        delegatedOrLogged = true;
                                        break;
                                    }
                                } else {
                                    Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
                                    if (launch != null) cn = launch.getComponent();
                                }
                            }
                        }
                        if (cn == null) {
                            String errMsg = "Root 打开失败：请填写目标组件（仅类名）与目标包名";
                            showToast(context, errMsg);
                            recordExecutionLog(context, entry, startMs, false, errMsg, "");
                            delegatedOrLogged = true;
                            break;
                        }
                        String cmd = "am start " + (userId > 0 ? "--user " + userId + " " : "")
                                + "-n " + quoteForSh(cn.flattenToString());
                        entry.params = cmd;
                        runRootCommand(context, cmd, entry, true);
                        delegatedOrLogged = true;
                    } else {
                        Intent intent = spec.buildIntent();
                        if (!(context instanceof Activity)) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        }
                        if (userId > 0) {
                            launchAsUser(context, intent, userId, entry, startMs);
                            delegatedOrLogged = true;
                        } else {
                            context.startActivity(intent);
                            recordExecutionLog(context, entry, startMs, true, "", "");
                            delegatedOrLogged = true;
                        }
                    }
                    break;
                }
                case ACTION_BROADCAST: {
                    entry.params = actionPayload != null ? actionPayload : "";
                    Intent intent = new Intent(actionPayload);
                    context.sendBroadcast(intent);
                    recordExecutionLog(context, entry, startMs, true, "", "");
                    delegatedOrLogged = true;
                    break;
                }
                case ACTION_FLASHLIGHT: {
                    entry.params = "手电筒开关";
                    toggleFlashlight(context, entry, startMs);
                    delegatedOrLogged = true;
                    break;
                }
                case ACTION_XIAOAI: {
                    entry.params = "唤醒小爱同学";
                    wakeUpXiaoAi(context, entry, startMs);
                    delegatedOrLogged = true;
                    break;
                }
                case ACTION_MUTE: {
                    entry.params = "静音/响铃切换";
                    toggleSilentMode(context, entry, startMs);
                    delegatedOrLogged = true;
                    break;
                }
                case ACTION_DND: {
                    entry.params = "勿扰模式切换";
                    toggleDndMode(context, entry, startMs);
                    delegatedOrLogged = true;
                    break;
                }
                case ACTION_CAMERA: {
                    entry.params = "系统相机";
                    openCamera(context, entry, startMs);
                    delegatedOrLogged = true;
                    break;
                }
                case ACTION_SHELL: {
                    entry.useRoot = shellUseRoot;
                    String normalizedShell = normalizeShellCommand(actionPayload);
                    entry.params = normalizedShell;
                    if (normalizedShell.isEmpty()) {
                        showToast(context, "Shell 命令为空");
                        recordExecutionLog(context, entry, startMs, false, "Shell 命令为空", "");
                        delegatedOrLogged = true;
                        break;
                    }
                    runShellCommand(context, normalizedShell, shellUseRoot, entry, isAmCommand(normalizedShell));
                    delegatedOrLogged = true;
                    break;
                }
                default:
                    Log.w(TAG, "未知动作类型: " + actionType);
                    recordExecutionLog(context, entry, startMs, false, "未知动作类型: " + actionType, "");
                    delegatedOrLogged = true;
                    break;
            }
        } catch (Throwable t) {
            Log.e(TAG, "执行快捷动作失败: " + title, t);
            String errMsg = "启动失败: " + describe(t);
            showToast(context, errMsg);
            if (!delegatedOrLogged) {
                recordExecutionLog(context, entry, startMs, false, errMsg, "");
            }
        }
    }

    private ShortcutLaunchLogStore.Entry createLogTemplate(String source, String params, boolean useRoot) {
        ShortcutLaunchLogStore.Entry e = new ShortcutLaunchLogStore.Entry();
        e.id = UUID.randomUUID().toString();
        e.timestamp = System.currentTimeMillis();
        e.source = source != null && !source.isEmpty() ? source : ShortcutLaunchLogStore.SOURCE_UNKNOWN;
        e.shortcutTitle = title != null ? title : "";
        e.actionType = actionType != null ? actionType : "";
        e.useRoot = useRoot;
        e.userId = userId;
        e.params = params != null ? params : "";
        return e;
    }

    private static void recordExecutionLog(Context context, ShortcutLaunchLogStore.Entry entry,
            long startMs, boolean success, String errorMessage, String outputSnippet) {
        if (context == null || entry == null) return;
        try {
            entry.durationMs = Math.max(0L, System.currentTimeMillis() - startMs);
            entry.success = success;
            entry.errorMessage = errorMessage != null ? errorMessage : "";
            entry.outputSnippet = outputSnippet != null ? outputSnippet : "";
            ShortcutLaunchLogStore.recordFromAnyProcess(context, entry);
        } catch (Throwable t) {
            Log.w(TAG, "记录执行日志异常", t);
        }
    }

    private static String describeActivitySpec(ActivityLaunchSpec spec, String fallbackPayload) {
        if (spec == null) {
            return fallbackPayload != null ? fallbackPayload : "";
        }
        StringBuilder sb = new StringBuilder();
        ComponentName cn = spec.resolveComponent();
        if (cn != null) {
            sb.append(cn.flattenToShortString());
        } else if (spec.packageName != null && !spec.packageName.trim().isEmpty()) {
            sb.append(spec.packageName.trim());
        }
        if (spec.action != null && !spec.action.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("action=").append(spec.action.trim());
        }
        if (spec.data != null && !spec.data.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("data=").append(spec.data.trim());
        }
        if (sb.length() == 0 && fallbackPayload != null) {
            return fallbackPayload;
        }
        return sb.toString();
    }

    private static void wakeUpXiaoAi(Context context, ShortcutLaunchLogStore.Entry entry, long startMs) {
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
                recordExecutionLog(context, entry, startMs, true, "", "VOICE_COMMAND");
                return;
            }
        } catch (Throwable ignored) {}

        try {
            Intent assistIntent = new Intent(Intent.ACTION_ASSIST);
            assistIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(assistIntent);
            recordExecutionLog(context, entry, startMs, true, "", "ACTION_ASSIST");
        } catch (Throwable t) {
            Log.e(TAG, "唤醒小爱失败", t);
            String errMsg = "唤醒小爱失败: " + describe(t);
            showToast(context, errMsg);
            recordExecutionLog(context, entry, startMs, false, errMsg, "");
        }
    }

    private static void toggleSilentMode(Context context, ShortcutLaunchLogStore.Entry entry, long startMs) {
        android.media.AudioManager am =
                (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            recordExecutionLog(context, entry, startMs, false, "AudioManager 不可用", "");
            return;
        }
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
                String okMsg = toNormal ? "已开启响铃模式" : "已开启静音模式";
                showToast(context, okMsg);
                recordExecutionLog(context, entry, startMs, true, "", okMsg);
            } catch (Throwable direct) {
                // 直调被权限/厂商限制（App 进程缺权限、SystemUI 个别 ROM 拒绝等）
                // AOSP AudioManagerShellCommand：cmd audio set-ringer-mode NORMAL|VIBRATE|SILENT（大小写敏感）
                Log.w(TAG, "setRingerMode 直调失败: " + describe(direct));
                if (entry != null) entry.useRoot = true;
                String cmd = "cmd audio set-ringer-mode " + (toNormal ? "NORMAL" : "SILENT");
                if (ownApp) {
                    // App 进程：同步 Root 执行并立即回读——真成功/真失败当下见分晓，不再等 1.8s
                    ShellExecResult res = runRootCommandSyncWithResult(cmd, 8000);
                    if (am.getRingerMode() == target) {
                        String okMsg = toNormal ? "已开启响铃模式" : "已开启静音模式";
                        showToast(context, okMsg);
                        recordExecutionLog(context, entry, startMs, true, "", okMsg + " (Root)");
                    } else {
                        String errMsg = "切换静音失败: " + describe(direct)
                                + (res.errorDetail.isEmpty() ? "；Root 兜底未生效" : "；" + res.errorDetail);
                        showToast(context, "切换静音失败: " + describe(direct)
                                + "；Root 兜底未生效，请检查本应用 Root 权限");
                        recordExecutionLog(context, entry, startMs, false, errMsg, buildOutputSnippet(res));
                    }
                } else {
                    // SystemUI 进程：su 弹不了授权 → 桥接到模块 App 进程执行，1.8s 后延迟复读诚实提示并记日志
                    runRootCommand(context, cmd, null, false);
                    verifyRingerLater(context, am, target, toNormal, direct, ownApp, entry, startMs);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "切换静音失败", t);
            String errMsg = "切换静音失败: " + describe(t);
            showToast(context, errMsg);
            recordExecutionLog(context, entry, startMs, false, errMsg, "");
        }
    }

    /** 静音切换 Root 兜底后的延迟复读校验（1.8s 后回查铃声模式，成功/失败如实提示并记录终态日志） */
    private static void verifyRingerLater(Context context, android.media.AudioManager am,
            int target, boolean toNormal, Throwable direct, boolean ownApp,
            ShortcutLaunchLogStore.Entry entry, long startMs) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (am.getRingerMode() == target) {
                    String okMsg = toNormal ? "已开启响铃模式" : "已开启静音模式";
                    showToast(context, okMsg);
                    recordExecutionLog(context, entry, startMs, true, "", okMsg + " (Root)");
                } else {
                    String errMsg = "切换静音失败: " + describe(direct)
                            + (ownApp ? "；Root 兜底未生效，请检查本应用 Root 权限" : "；Root 兜底未生效");
                    showToast(context, "切换静音失败: " + describe(direct)
                            + (ownApp ? "；Root 兜底未生效，请检查本应用 Root 权限" : ""));
                    recordExecutionLog(context, entry, startMs, false, errMsg, "");
                }
            } catch (Throwable t) {
                String errMsg = "切换静音失败: " + describe(t);
                showToast(context, errMsg);
                recordExecutionLog(context, entry, startMs, false, errMsg, "");
            }
        }, 1800);
    }

    private static void toggleDndMode(Context context, ShortcutLaunchLogStore.Entry entry, long startMs) {
        android.app.NotificationManager nm =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            recordExecutionLog(context, entry, startMs, false, "NotificationManager 不可用", "");
            return;
        }
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
                String errMsg = "切换勿扰失败: 无法读取勿扰状态（请检查勿扰权限）";
                showToast(context, errMsg);
                recordExecutionLog(context, entry, startMs, false, errMsg, "");
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
                String okMsg = toPriority ? "已开启勿扰模式" : "已关闭勿扰模式";
                showToast(context, okMsg);
                recordExecutionLog(context, entry, startMs, true, "", okMsg);
            } catch (Throwable direct) {
                // 通知策略访问未授予（App 进程必现 SecurityException / SystemUI 个别 ROM 也未授）
                Log.w(TAG, "setInterruptionFilter 直调失败: " + describe(direct));
                if (entry != null) entry.useRoot = true;
                String setCmd = "cmd notification set_dnd " + (toPriority ? "priority" : "off");
                if (ownApp) {
                    // App 进程：同步 Root 直接执行本次切换并立即回读——真成功/真失败当下见分晓
                    ShellExecResult res = runRootCommandSyncWithResult(setCmd, 8000);
                    if (readDndFilterQuiet(nm) == target) {
                        String okMsg = toPriority ? "已开启勿扰模式" : "已关闭勿扰模式";
                        showToast(context, okMsg);
                        recordExecutionLog(context, entry, startMs, true, "", okMsg + " (Root)");
                    } else {
                        String errMsg = "切换勿扰失败: " + describe(direct)
                                + (res.errorDetail.isEmpty() ? "" : "；" + res.errorDetail);
                        showToast(context, "切换勿扰失败: " + describe(direct));
                        recordExecutionLog(context, entry, startMs, false, errMsg, buildOutputSnippet(res));
                        if (!hasDndAccess(nm)) guideDndAccessSettings(context);
                    }
                } else {
                    // SystemUI 进程：su 弹不了授权 → 桥接到模块 App 进程执行 + 延迟回读
                    runRootCommand(context, "cmd notification allow_dnd " + context.getPackageName(), null, false);
                    runRootCommand(context, setCmd, null, false);
                    verifyDndLater(context, nm, target, toPriority, direct, entry, startMs);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "切换勿扰模式失败", t);
            String errMsg = "切换勿扰失败: " + describe(t);
            showToast(context, errMsg);
            recordExecutionLog(context, entry, startMs, false, errMsg, "");
        }
    }

    /** 勿扰切换 Root 兜底后的延迟复读校验（1.8s 后回查过滤器，回读受权限挡住时如实说明并记录终态日志） */
    private static void verifyDndLater(Context context, android.app.NotificationManager nm,
            int target, boolean toPriority, Throwable direct,
            ShortcutLaunchLogStore.Entry entry, long startMs) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (nm.getCurrentInterruptionFilter() == target) {
                    String okMsg = toPriority ? "已开启勿扰模式" : "已关闭勿扰模式";
                    showToast(context, okMsg);
                    recordExecutionLog(context, entry, startMs, true, "", okMsg + " (Root)");
                } else {
                    String errMsg = "切换勿扰失败: " + describe(direct);
                    showToast(context, errMsg);
                    recordExecutionLog(context, entry, startMs, false, errMsg, "");
                }
            } catch (Throwable t) {
                // 回读也被策略权限挡住：无法证伪，如实说明是 Root 提交而非确认成功
                String msg = "已通过 Root 提交勿扰切换（状态回读受限）";
                showToast(context, msg);
                recordExecutionLog(context, entry, startMs, true, "", msg);
            }
        }, 1800);
    }

    /** 异常可读描述：message 为 null 时退回类名，避免 toast 出现 "null" */
    private static String describe(Throwable t) {
        if (t == null) return "未知异常";
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
        return runRootCommandSyncWithResult(cmd, timeoutMs).success;
    }

    private static ShellExecResult runRootCommandSyncWithResult(String cmd, long timeoutMs) {
        return executeShellProcess(true, cmd, timeoutMs, false);
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

    private static void openCamera(Context context, ShortcutLaunchLogStore.Entry entry, long startMs) {
        try {
            Intent intent = new Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
            recordExecutionLog(context, entry, startMs, true, "", "");
        } catch (Throwable t) {
            try {
                Intent fallback = new Intent("android.media.action.STILL_IMAGE_CAMERA");
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                recordExecutionLog(context, entry, startMs, true, "", "fallback: STILL_IMAGE_CAMERA");
            } catch (Throwable t2) {
                Log.e(TAG, "打开相机失败", t2);
                String errMsg = "打开相机失败: " + describe(t2);
                showToast(context, errMsg);
                recordExecutionLog(context, entry, startMs, false, errMsg, "");
            }
        }
    }

    private static volatile boolean isFlashlightOn = false;
    private static volatile boolean torchCallbackRegistered = false;
    private static volatile String targetTorchCameraId;

    private static synchronized void ensureTorchCallback(CameraManager cm) {
        if (torchCallbackRegistered || cm == null) return;
        try {
            cm.registerTorchCallback(new CameraManager.TorchCallback() {
                @Override
                public void onTorchModeChanged(String cameraId, boolean enabled) {
                    String target = targetTorchCameraId;
                    if (target != null && !target.equals(cameraId)) {
                        return;
                    }
                    isFlashlightOn = enabled;
                }

                @Override
                public void onTorchModeUnavailable(String cameraId) {
                    String target = targetTorchCameraId;
                    if (target != null && !target.equals(cameraId)) {
                        return;
                    }
                    isFlashlightOn = false;
                }
            }, new Handler(Looper.getMainLooper()));
            torchCallbackRegistered = true;
        } catch (Throwable t) {
            Log.w(TAG, "注册手电筒状态回调失败", t);
        }
    }

    private static String resolveTorchCameraId(CameraManager cm) {
        try {
            String[] cameraIdList = cm.getCameraIdList();
            if (cameraIdList == null || cameraIdList.length == 0) {
                return null;
            }
            String anyFlashCamera = null;
            for (String id : cameraIdList) {
                try {
                    CameraCharacteristics chars = cm.getCameraCharacteristics(id);
                    Boolean hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (!Boolean.TRUE.equals(hasFlash)) {
                        continue;
                    }
                    if (anyFlashCamera == null) {
                        anyFlashCamera = id;
                    }
                    Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        return id;
                    }
                } catch (Throwable ignored) {}
            }
            return anyFlashCamera != null ? anyFlashCamera : cameraIdList[0];
        } catch (Throwable t) {
            Log.w(TAG, "解析手电筒目标镜头失败", t);
            return null;
        }
    }

    private static void toggleFlashlight(Context context, ShortcutLaunchLogStore.Entry entry, long startMs) {
        try {
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) {
                recordExecutionLog(context, entry, startMs, false, "CameraManager 不可用", "");
                return;
            }
            String cameraId = resolveTorchCameraId(cm);
            if (cameraId == null) {
                recordExecutionLog(context, entry, startMs, false, "未找到支持闪光灯的相机", "");
                return;
            }
            targetTorchCameraId = cameraId;
            ensureTorchCallback(cm);
            boolean nextState = !isFlashlightOn;
            cm.setTorchMode(cameraId, nextState);
            isFlashlightOn = nextState;
            recordExecutionLog(context, entry, startMs, true, "", nextState ? "手电筒已开启" : "手电筒已关闭");
        } catch (Throwable t) {
            Log.e(TAG, "切换手电筒状态失败", t);
            recordExecutionLog(context, entry, startMs, false, "切换手电筒失败: " + describe(t), "");
        }
    }

    /**
     * 安全构造指定 userId 的 UserHandle，防止负数、0 或超大值乘以 100000 溢出。
     * 优先反射调用 UserHandle.of(int)，失败后回退 UserHandle.getUserHandleForUid(userId * 100000)。
     */
    private static UserHandle resolveUserHandle(int userId) {
        if (userId <= 0 || userId > MAX_VALID_USER_ID) {
            return null;
        }
        try {
            java.lang.reflect.Method ofMethod = UserHandle.class.getMethod("of", int.class);
            Object res = ofMethod.invoke(null, userId);
            if (res instanceof UserHandle) {
                return (UserHandle) res;
            }
        } catch (Throwable ignored) {
        }
        try {
            return UserHandle.getUserHandleForUid(userId * 100000);
        } catch (Throwable t) {
            Log.w(TAG, "构造 UserHandle 失败: userId=" + userId, t);
            return null;
        }
    }

    /** 剥离反射调用包装的 InvocationTargetException，获取真实根因异常 */
    private static Throwable unwrapCause(Throwable t) {
        Throwable cur = t;
        while (cur instanceof java.lang.reflect.InvocationTargetException && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }

    /**
     * 跨用户启动应用（零 Root 优先）：
     * 1) 校验 userId 合法性；
     * 2) 优先通过 LauncherApps.getActivityList + startMainActivity 直启目标空间真实入口；
     * 3) 若 startMainActivity 失败，构造显式 MAIN/LAUNCHER Intent 走 launchAsUser；
     * 4) 若 LauncherApps 未返回入口，在非 App 进程（SystemUI）尝试 getLaunchIntentForPackage -> launchAsUser；
     * 5) 最终回退 Root 在目标空间解析并启动。
     */
    private static void launchAppForUser(Context context, String pkg, int userId,
            ShortcutLaunchLogStore.Entry entry, long startMs) {
        UserHandle targetHandle = resolveUserHandle(userId);
        if (targetHandle == null) {
            String errMsg = "用户 ID 无效: " + userId;
            showToast(context, errMsg);
            recordExecutionLog(context, entry, startMs, false, errMsg, "");
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            try {
                LauncherApps la = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                if (la != null) {
                    List<LauncherActivityInfo> activities = la.getActivityList(pkg, targetHandle);
                    if (activities != null && !activities.isEmpty()) {
                        ComponentName comp = activities.get(0).getComponentName();
                        try {
                            la.startMainActivity(comp, targetHandle, null, null);
                            recordExecutionLog(context, entry, startMs, true, "", "LauncherApps: " + comp.flattenToShortString());
                            return;
                        } catch (Throwable t) {
                            Log.w(TAG, "LauncherApps.startMainActivity 启动跨用户应用失败，改走 launchAsUser: " + comp, t);
                        }
                        Intent fallbackIntent = new Intent(Intent.ACTION_MAIN);
                        fallbackIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                        fallbackIntent.setComponent(comp);
                        fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        launchAsUser(context, fallbackIntent, userId, entry, startMs);
                        return;
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "LauncherApps 解析跨用户应用失败: pkg=" + pkg + ", user=" + userId, t);
            }
        }
        if (!isOwnAppProcess(context)) {
            try {
                Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent != null) {
                    launchAsUser(context, intent, userId, entry, startMs);
                    return;
                }
            } catch (Throwable t) {
                Log.w(TAG, "SystemUI getLaunchIntentForPackage 跨用户启动失败: pkg=" + pkg + ", user=" + userId, t);
            }
        }
        resolveAndLaunchAppViaRoot(context, pkg, userId, entry);
    }

    /** 通过 Root 在指定用户空间解析包的主入口 Activity 并启动 */
    private static void resolveAndLaunchAppViaRoot(Context context, String pkg, int userId,
            ShortcutLaunchLogStore.Entry entry) {
        String quotedPkg = quoteForSh(pkg);
        String cmd = "comp=$(cmd package resolve-activity --user " + userId
                + " --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "
                + quotedPkg + " 2>/dev/null | tail -n 1); "
                + "if [ -n \"$comp\" ] && [ \"$comp\" != \"No activity found\" ]; then "
                + "am start --user " + userId + " -n \"$comp\"; "
                + "else am start --user " + userId
                + " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "
                + quotedPkg + "; fi";
        if (entry != null) entry.useRoot = true;
        runRootCommand(context, cmd, entry, true);
    }

    /**
     * 以指定用户身份启动 Activity（如 userId=999 → MIUI/HyperOS 应用双开 XSpace/分身）。
     * Intent URI 规范里没有 user 字段，用户只能在发起侧指定：
     * 1) 系统上下文 startActivityAsUser / createContextAsUser（需 INTERACT_ACROSS_USERS[_FULL]，SystemUI/系统签名可直连）
     * 2) Root 兜底：su -c 'am start --user N "intent:..."'
     */
    private static void launchAsUser(Context context, Intent intent, int userId,
            ShortcutLaunchLogStore.Entry entry, long startMs) {
        UserHandle target = resolveUserHandle(userId);
        if (target == null) {
            String errMsg = "用户 ID 无效: " + userId;
            showToast(context, errMsg);
            recordExecutionLog(context, entry, startMs, false, errMsg, "");
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            // 0) Context#startActivityAsUser（SDK 桩非公开）：SystemUI 等系统上下文
            //    持有 INTERACT_ACROSS_USERS[_FULL] 时可直连，最快路径
            try {
                java.lang.reflect.Method m =
                        Context.class.getMethod("startActivityAsUser", Intent.class, UserHandle.class);
                m.invoke(context, intent, target);
                recordExecutionLog(context, entry, startMs, true, "", "startActivityAsUser(" + userId + ")");
                return;
            } catch (Throwable t) {
                Throwable cause = unwrapCause(t);
                if (cause instanceof android.content.ActivityNotFoundException) {
                    String errMsg = "用户 " + userId + " 中未找到目标应用或组件";
                    Log.w(TAG, errMsg, cause);
                    showToast(context, errMsg);
                    recordExecutionLog(context, entry, startMs, false, errMsg, "");
                    return;
                }
                if (!(cause instanceof NoSuchMethodException
                        || cause instanceof IllegalAccessException
                        || cause instanceof SecurityException)) {
                    Log.w(TAG, "startActivityAsUser 启动 user " + userId + " 失败，尝试 createContextAsUser", cause);
                } else {
                    Log.w(TAG, "startActivityAsUser 启动 user " + userId + " 不可用，尝试 createContextAsUser", cause);
                }
            }
            // 1) Context#createContextAsUser（SDK 桩非公开）反射调用（API 30+ 运行时存在）
            try {
                java.lang.reflect.Method m =
                        Context.class.getMethod("createContextAsUser", UserHandle.class, int.class);
                Context userCtx = (Context) m.invoke(context, target, 0);
                userCtx.startActivity(intent);
                recordExecutionLog(context, entry, startMs, true, "", "createContextAsUser(" + userId + ")");
                return;
            } catch (Throwable t) {
                Throwable cause = unwrapCause(t);
                if (cause instanceof android.content.ActivityNotFoundException) {
                    String errMsg = "用户 " + userId + " 中未找到目标应用或组件";
                    Log.w(TAG, errMsg, cause);
                    showToast(context, errMsg);
                    recordExecutionLog(context, entry, startMs, false, errMsg, "");
                    return;
                }
                Log.w(TAG, "createContextAsUser 启动 user " + userId + " 失败，改用 am start --user 兜底", cause);
            }
        }
        try {
            String uri = intent.toUri(Intent.URI_INTENT_SCHEME);
            if (entry != null) entry.useRoot = true;
            runRootCommand(context, "am start --user " + userId + " " + quoteForSh(uri), entry, true);
        } catch (Throwable t) {
            Log.e(TAG, "跨用户启动失败: user=" + userId + " intent=" + intent, t);
            String errMsg = "启动失败(需跨用户权限或 Root): " + describe(t);
            showToast(context, errMsg);
            recordExecutionLog(context, entry, startMs, false, errMsg, "");
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

    /** POSIX 单引号安全转义：包裹为 '...' 并转义内部单引号 */
    private static String quoteForSh(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /** Shell 进程执行结果（包含有界输出、超时、退出码及错误详情） */
    public static final class ShellExecResult {
        public boolean success;
        public int exitCode = -1;
        public boolean timedOut;
        public String stdout = "";
        public String stderr = "";
        public String errorDetail = "";
        public long durationMs;
    }

    private static void runRootAmStart(Context context, String payload, ShortcutLaunchLogStore.Entry entry) {
        runRootCommand(context, "am start " + quoteForSh(payload), entry, true);
    }

    /** 统一清洗 Shell 命令中的 CRLF/CR 换行并去除首尾空白，避免剪贴板换行符导致参数末尾带 \r 失效。 */
    private static String normalizeShellCommand(String cmd) {
        if (cmd == null) return "";
        return cmd.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    /** 判断规范化后的命令是否包含 am start / am startservice / am broadcast 调用。 */
    private static boolean isAmCommand(String normalizedCmd) {
        if (normalizedCmd == null || normalizedCmd.isEmpty()) return false;
        return normalizedCmd.contains("am start")
                || normalizedCmd.contains("am startservice")
                || normalizedCmd.contains("am broadcast");
    }

    /**
     * root 命令统一入口：优先桥接给拥有 Root 授权的模块 App 后台代理执行（避免 SystemUI/KernelSU 无权调用 su）；
     * 若桥接未受理或无响应，自动降级回退到当前进程本地 su 执行。
     */
    private static void runRootCommand(Context context, String cmd,
            ShortcutLaunchLogStore.Entry entry, boolean checkAmError) {
        // 如果当前就是 App 自身进程（如用户在编辑页面点立即测试），直接本地执行
        if (isOwnAppProcess(context)) {
            runShellBinaryWithLog(context, true, cmd, entry, checkAmError, 0L);
            return;
        }

        // 在 SystemUI 进程中：
        // 因面板完全展开时已静默预热解冻模块进程，此处桥接将直接由已解冻的模块 App 秒级执行；
        // 若桥接失败再回退本地尝试
        if (runRootViaBridge(context, cmd, entry, checkAmError)) {
            return;
        }
        runShellBinaryWithLog(context, true, cmd, entry, checkAmError, 0L);
    }

    /** Shell 动作执行入口（供 Provider 桥接回调兼容旧调用）：useRoot=true 经 su 以 root 身份执行，false 以普通 sh 身份执行 */
    public static void runShellCommand(String cmd, boolean useRoot) {
        runShellBinaryWithLog(null, useRoot, cmd, null, false, 0L);
    }

    /** Shell 动作执行入口（供 Provider 桥接回调并记录真实执行日志） */
    public static void runShellCommandWithLog(Context context, String cmd, boolean useRoot,
            ShortcutLaunchLogStore.Entry template, boolean checkAmError) {
        runShellBinaryWithLog(context, useRoot, cmd, template, checkAmError, 0L);
    }

    /** Shell 动作执行入口（支持指定最长同步等待时间，用于跨进程 Binder 桥接保活防冻结） */
    public static void runShellCommandWithLog(Context context, String cmd, boolean useRoot,
            ShortcutLaunchLogStore.Entry template, boolean checkAmError, long maxSyncWaitMs) {
        runShellBinaryWithLog(context, useRoot, cmd, template, checkAmError, maxSyncWaitMs);
    }

    /** Shell 动作执行入口：root 命令桥接到模块 App 进程（SystemUI 内 su 常因锁屏无法弹 Magisk 授权而失败） */
    private static void runShellCommand(Context context, String cmd, boolean useRoot,
            ShortcutLaunchLogStore.Entry entry, boolean checkAmError) {
        if (useRoot) {
            runRootCommand(context, cmd, entry, checkAmError);
        } else {
            runShellBinaryWithLog(context, false, cmd, entry, checkAmError, 0L);
        }
    }

    /**
     * 把 root/shell 命令桥接给模块 App 进程执行（SettingsProvider 的 run_shell 方法）。
     * 背景：指纹面板在 SystemUI 进程里执行，su 从系统进程请求 root 时 Magisk 无法弹授权/超时；
     * 而模块 App 进程的 su 已获授权（编辑界面测试按钮即在该进程执行成功）。
     * fire-and-forget：返回是否受理，不等待命令结果；若携带日志模板则由模块 App 进程在命令结束后落盘唯一一条日志。
     */
    private static boolean runRootViaBridge(Context context, String cmd,
            ShortcutLaunchLogStore.Entry template, boolean checkAmError) {
        try {
            Bundle b = template != null ? template.toBundle() : new Bundle();
            b.putString("cmd", cmd);
            b.putBoolean("useRoot", true);
            b.putString("useRootStr", "true"); // 兼容 adb content call 等只支持字符串 extra 的入口
            b.putBoolean("has_log_meta", template != null);
            b.putBoolean("check_am_error", checkAmError);
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

    private static void runShellBinaryWithLog(Context context, boolean useRoot, String cmd,
            ShortcutLaunchLogStore.Entry template, boolean checkAmError, long maxSyncWaitMs) {
        final CountDownLatch latch = new CountDownLatch(1);
        // 走独立的守护线程执行，绝不霸占 IpcExecutor 单线程 HandlerThread 造成 SystemUI IPC 阻塞
        Thread runner = new Thread(() -> {
            try {
                ShellExecResult res;
                try {
                    res = executeShellProcess(useRoot, cmd, 15000L, checkAmError);
                } catch (Throwable t) {
                    Log.e(TAG, (useRoot ? "Root" : "Shell") + " 命令执行异常: " + cmd, t);
                    res = new ShellExecResult();
                    res.success = false;
                    res.errorDetail = describe(t);
                }
                if (context != null && template != null) {
                    ShortcutLaunchLogStore.Entry finalEntry = template.copy();
                    finalEntry.durationMs = res.durationMs;
                    finalEntry.success = res.success;
                    finalEntry.errorMessage = res.errorDetail != null ? res.errorDetail : "";
                    finalEntry.outputSnippet = buildOutputSnippet(res);
                    ShortcutLaunchLogStore.recordFromAnyProcess(context, finalEntry);
                }
            } finally {
                latch.countDown();
            }
        }, "HyperOpen-ShellRunner");
        runner.setDaemon(true);
        runner.start();

        if (maxSyncWaitMs > 0 && Looper.myLooper() != Looper.getMainLooper()) {
            try {
                latch.await(maxSyncWaitMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String buildOutputSnippet(ShellExecResult res) {
        if (res == null) return "";
        String out = res.stdout != null ? res.stdout.trim() : "";
        String err = res.stderr != null ? res.stderr.trim() : "";
        if (!out.isEmpty() && !err.isEmpty()) {
            return out + "\n[stderr] " + err;
        }
        return !out.isEmpty() ? out : err;
    }

    private static ShellExecResult executeShellProcess(boolean useRoot, String cmd,
            long timeoutMs, boolean checkAmError) {
        ShellExecResult result = new ShellExecResult();
        long startNs = System.nanoTime();
        String normalizedCmd = normalizeShellCommand(cmd);
        String[] candidates = useRoot
                ? new String[]{"su", "/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su"}
                : new String[]{"/system/bin/sh", "sh"};
        String usedBin = candidates[0];
        Process p = null;
        try {
            IOException lastIoEx = null;
            for (String bin : candidates) {
                try {
                    p = new ProcessBuilder(bin, "-c", normalizedCmd).start();
                    usedBin = bin;
                    break;
                } catch (IOException e) {
                    lastIoEx = e;
                }
            }
            if (p == null) {
                result.durationMs = (System.nanoTime() - startNs) / 1_000_000L;
                result.success = false;
                result.errorDetail = lastIoEx != null ? describe(lastIoEx) : "无法启动 Shell 进程";
                Log.w(TAG, (useRoot ? "su" : "sh") + " 启动失败: " + result.errorDetail);
                return result;
            }

            // 立即关闭子进程 stdin，防止命令尝试读取标准输入导致挂起至超时
            try {
                p.getOutputStream().close();
            } catch (Throwable ignored) {}

            final InputStream in = p.getInputStream();
            final InputStream err = p.getErrorStream();
            final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
            final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

            Thread drainIn = new Thread(() -> readBoundedAndDrain(in, outBuf, 2048), "HyperOpen-DrainIn");
            Thread drainErr = new Thread(() -> readBoundedAndDrain(err, errBuf, 4096), "HyperOpen-DrainErr");
            drainIn.setDaemon(true);
            drainErr.setDaemon(true);
            drainIn.start();
            drainErr.start();

            boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                Log.w(TAG, usedBin + " 命令执行超时(" + timeoutMs + "ms): " + normalizedCmd);
                p.destroyForcibly();
                try { drainIn.join(300L); } catch (InterruptedException ignored) {}
                try { drainErr.join(300L); } catch (InterruptedException ignored) {}
                result.durationMs = (System.nanoTime() - startNs) / 1_000_000L;
                result.timedOut = true;
                result.success = false;
                result.stdout = new String(outBuf.toByteArray(), StandardCharsets.UTF_8).trim();
                result.stderr = new String(errBuf.toByteArray(), StandardCharsets.UTF_8).trim();
                result.errorDetail = "命令执行超时 (" + timeoutMs + "ms)";
                return result;
            }

            try { drainIn.join(500L); } catch (InterruptedException ignored) {}
            try { drainErr.join(500L); } catch (InterruptedException ignored) {}
            result.durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            result.stdout = new String(outBuf.toByteArray(), StandardCharsets.UTF_8).trim();
            result.stderr = new String(errBuf.toByteArray(), StandardCharsets.UTF_8).trim();

            int code = p.exitValue();
            result.exitCode = code;
            if (code != 0) {
                Log.w(TAG, usedBin + " 命令 exit=" + code + ": " + normalizedCmd);
                result.success = false;
                result.errorDetail = "退出码 " + code
                        + (result.stderr.isEmpty() ? "" : ": " + result.stderr);
                return result;
            }

            if (checkAmError) {
                String amErr = findAmErrorLine(result.stdout, result.stderr);
                if (amErr != null) {
                    Log.w(TAG, usedBin + " am 命令输出报错: " + amErr);
                    result.success = false;
                    result.errorDetail = amErr;
                    return result;
                }
            }

            result.success = true;
            return result;
        } catch (Throwable t) {
            result.durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            result.success = false;
            result.errorDetail = describe(t);
            Log.w(TAG, usedBin + " 执行异常: " + result.errorDetail);
            return result;
        } finally {
            if (p != null) {
                try { p.destroy(); } catch (Throwable ignored) {}
            }
        }
    }

    private static String findAmErrorLine(String stdout, String stderr) {
        String combined = (stdout != null ? stdout : "") + "\n" + (stderr != null ? stderr : "");
        String[] lines = combined.split("\\r?\\n");
        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("Warning:")) {
                continue;
            }
            if (trimmed.contains("Error:") || trimmed.contains("Exception:")
                    || trimmed.contains("Activity not started")) {
                return trimmed;
            }
        }
        return null;
    }

    private static void readBoundedAndDrain(InputStream stream, ByteArrayOutputStream out, int maxBytes) {
        if (stream == null) return;
        try {
            byte[] buf = new byte[1024];
            int remaining = maxBytes;
            int n;
            while ((n = stream.read(buf)) != -1) {
                if (remaining > 0) {
                    int toWrite = Math.min(n, remaining);
                    out.write(buf, 0, toWrite);
                    remaining -= toWrite;
                }
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
