package io.github.qmryqmm.hyperopen.view;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.qmryqmm.hyperopen.config.HyperOpenConfig;
import io.github.qmryqmm.hyperopen.model.ShortcutItem;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一图标解析工厂。
 * 解析优先级：
 * 1) item.iconGlyph 或 item.iconPackage 为 base64: 前缀的本地上传图片 → 解码并按裁切方案处理
 * 2) item.iconGlyph 手动指定内置符号 / Miuix 矢量图标 key
 * 3) item.iconPackage 手动指定包名 → 提取该应用图标
 * 4) ACTION_APP → 目标应用图标
 * 5) ACTION_INTENT_URI → 解析 Intent 目标应用图标（package / component / resolveActivity 三级）
 * 6) 根据 id/title/subtitle 关键词匹配内置符号
 * 7) 兜底星芒符号
 * <p>
 * 全部内置符号为 24x24 网格手绘 Path（SF Symbols 细线风格），按 item.tintColor 着色。
 * 纯 Java Path 绘制，无反射、无隐藏 API，可同时在 SystemUI 进程与模块设置进程使用。
 */
public final class IconGlyphFactory {
    private static final String TAG = "HyperOpen_IconGlyph";
    public static final String CUSTOM_IMAGE_PREFIX = "base64:";

    private static final Map<String, Drawable.ConstantState> ICON_CACHE = new HashMap<>();

    /**
     * 已裁切成圆形的图标缓存（key = 包名/自定义标识 + 裁切模式）。
     * 裁切需要分配多张 192x192 位图并逐像素扫描，每次呼出面板对每个图标重做一遍
     * 会在 SystemUI 主线程上产生明显卡顿与 GC 压力。缓存的是 ConstantState，
     * 每次返回 newDrawable()：共享同一份像素，但 Drawable 实例互相独立。
     */
    private static final Map<String, Drawable.ConstantState> PROCESSED_CACHE = new HashMap<>();
    /** Intent URI → 目标包名（避免每次重新 parseUri + resolveActivity 这类 PackageManager 跨进程调用） */
    private static final Map<String, String> INTENT_PKG_CACHE = new HashMap<>();

    /**
     * 清理图标缓存：当系统主题、浅/深色模式或分辨率 DPI 变更时调用，确保图标实时刷新
     */
    public static void clearCache() {
        synchronized (ICON_CACHE) {
            ICON_CACHE.clear();
        }
        synchronized (PROCESSED_CACHE) {
            PROCESSED_CACHE.clear();
        }
        synchronized (INTENT_PKG_CACHE) {
            INTENT_PKG_CACHE.clear();
        }
        Log.i(TAG, "已清空图标解析与位图裁切缓存");
    }

    public static boolean isCustomImage(@Nullable String s) {
        return s != null && s.startsWith(CUSTOM_IMAGE_PREFIX) && s.length() > CUSTOM_IMAGE_PREFIX.length();
    }

    @Nullable
    public static Drawable decodeCustomImageDrawable(@Nullable Context ctx, @Nullable String spec) {
        if (ctx == null || !isCustomImage(spec)) return null;
        try {
            String b64 = spec.substring(CUSTOM_IMAGE_PREFIX.length()).trim();
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp != null) {
                return new BitmapDrawable(ctx.getResources(), bmp);
            }
        } catch (Throwable t) {
            Log.w(TAG, "解码自定义上传图片失败: " + t.getMessage());
        }
        return null;
    }

    @Nullable
    private static Drawable processedCustomImage(@Nullable Context ctx, @Nullable String spec, int cropMode, int cornerRadiusPercent) {
        if (ctx == null || !isCustomImage(spec)) return null;
        String key = "custom|" + spec.hashCode() + "|" + spec.length() + "|" + cropMode + "|" + cornerRadiusPercent;
        synchronized (PROCESSED_CACHE) {
            Drawable.ConstantState cs = PROCESSED_CACHE.get(key);
            if (cs != null) return cs.newDrawable(ctx.getResources());
        }
        Drawable raw = decodeCustomImageDrawable(ctx, spec);
        if (raw == null) return null;
        Drawable out = AppIconCropProcessor.process(raw, cropMode, ctx.getResources(), cornerRadiusPercent);
        if (out == null) out = raw;
        Drawable.ConstantState cs = out.getConstantState();
        if (cs != null) {
            synchronized (PROCESSED_CACHE) {
                if (PROCESSED_CACHE.size() > 48) PROCESSED_CACHE.clear();
                PROCESSED_CACHE.put(key, cs);
            }
            return cs.newDrawable(ctx.getResources());
        }
        return out;
    }

    @Nullable
    private static Drawable processedAppIcon(@Nullable Context ctx, @Nullable String pkg, int cropMode, int cornerRadiusPercent) {
        if (ctx == null || pkg == null || pkg.isEmpty()) return null;
        if (isCustomImage(pkg)) {
            return processedCustomImage(ctx, pkg, cropMode, cornerRadiusPercent);
        }
        String key = pkg + "|" + cropMode + "|" + cornerRadiusPercent;
        synchronized (PROCESSED_CACHE) {
            Drawable.ConstantState cs = PROCESSED_CACHE.get(key);
            if (cs != null) return cs.newDrawable(ctx.getResources());
        }
        Drawable raw = appIcon(ctx, pkg);
        if (raw == null) return null;
        Drawable out = AppIconCropProcessor.process(raw, cropMode, ctx.getResources(), cornerRadiusPercent);
        if (out == null) return null;
        Drawable.ConstantState cs = out.getConstantState();
        if (cs != null) {
            synchronized (PROCESSED_CACHE) {
                if (PROCESSED_CACHE.size() > 48) PROCESSED_CACHE.clear();
                PROCESSED_CACHE.put(key, cs);
            }
            return cs.newDrawable(ctx.getResources());
        }
        return out;
    }

    @Nullable
    private static String packageFromIntentCached(@Nullable Context ctx, @NonNull String uri) {
        synchronized (INTENT_PKG_CACHE) {
            String hit = INTENT_PKG_CACHE.get(uri);
            if (hit != null) return hit.isEmpty() ? null : hit;
        }
        String pkg = null;
        try {
            Intent intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
            String p = intent.getPackage();
            if (notBlank(p)) {
                pkg = p.trim();
            } else if (intent.getComponent() != null) {
                pkg = intent.getComponent().getPackageName();
            } else if (ctx != null) {
                ResolveInfo ri = ctx.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
                if (ri != null && ri.activityInfo != null) pkg = ri.activityInfo.packageName;
            }
        } catch (Throwable t) {
            Log.d(TAG, "Intent 包名解析失败: " + t.getMessage());
        }
        // 解析失败（包名为空）不缓存，允许之后重试（例如目标应用稍后才安装）
        if (pkg != null) {
            synchronized (INTENT_PKG_CACHE) {
                if (INTENT_PKG_CACHE.size() > 64) INTENT_PKG_CACHE.clear();
                INTENT_PKG_CACHE.put(uri, pkg);
            }
        }
        return pkg;
    }

    private IconGlyphFactory() {}

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /**
     * 统一解析入口（重载 1：带 HyperOpenConfig 参数，支持裁切模式与底垫配置）
     */
    @NonNull
    public static Drawable resolve(@Nullable Context ctx, @Nullable ShortcutItem item, @Nullable HyperOpenConfig config) {
        if (ctx == null || item == null) return createGlyph("sparkle", 0xFFFFFFFF);

        int cropMode = item.appIconCropMode > 0
            ? item.appIconCropMode
            : (config != null ? config.appIconCropMode : HyperOpenConfig.CROP_ADAPTIVE_EXPAND);
        int cornerRadiusPercent = config != null ? Math.max(0, Math.min(50, config.iconCornerRadius)) : 50;

        // 0) 优先级最高：本地上传的自定义图片（存储在 iconPackage 或 iconGlyph 中，以 base64: 开头）
        if (isCustomImage(item.iconPackage)) {
            Drawable d = processedCustomImage(ctx, item.iconPackage, cropMode, cornerRadiusPercent);
            if (d != null) return d;
        }
        if (isCustomImage(item.iconGlyph)) {
            Drawable d = processedCustomImage(ctx, item.iconGlyph, cropMode, cornerRadiusPercent);
            if (d != null) return d;
        }

        // 1) 手动指定的自定义 Miuix 矢量图标 / 内置符号
        if (notBlank(item.iconGlyph)) {
            boolean padEnabled = item.glyphPadEnabled;
            int padColor = item.glyphPadColor;
            int padMode = item.glyphPadMode;
            Drawable d = resolveMiuixIcon(item.iconGlyph.trim(), item.tintColor, padEnabled, padColor, padMode, cornerRadiusPercent);
            if (d != null) return d;
        }

        // 2) 优先级次之：手动指定应用包名图标
        if (notBlank(item.iconPackage)) {
            Drawable d = processedAppIcon(ctx, item.iconPackage.trim(), cropMode, cornerRadiusPercent);
            if (d != null) return d;
        }

        // 3) 动作默认行为：APP 类型直接取目标应用图标
        if (ShortcutItem.ACTION_APP.equals(item.actionType) && notBlank(item.actionPayload)) {
            Drawable d = processedAppIcon(ctx, item.actionPayload.trim(), cropMode, cornerRadiusPercent);
            if (d != null) return d;
        }

        // 4) 动作默认行为：INTENT_URI 解析目标应用图标
        if (ShortcutItem.ACTION_INTENT_URI.equals(item.actionType) && notBlank(item.actionPayload)) {
            Drawable d = processedAppIcon(ctx, packageFromIntentCached(ctx, item.actionPayload), cropMode, cornerRadiusPercent);
            if (d != null) return d;
        }

        // 4b) 动作默认行为：ACTIVITY 结构化规格解析目标应用图标
        if (ShortcutItem.ACTION_ACTIVITY.equals(item.actionType) && notBlank(item.actionPayload)) {
            String pkg = io.github.qmryqmm.hyperopen.model.ActivityLaunchSpec
                    .fromJson(item.actionPayload).resolvePackage();
            Drawable d = processedAppIcon(ctx, pkg, cropMode, cornerRadiusPercent);
            if (d != null) return d;
        }

        // 4c) 动作默认行为：FOLDER 文件夹气泡（优先使用内置 folder 图标）
        if (ShortcutItem.ACTION_FOLDER.equals(item.actionType)) {
            return resolveMiuixIcon("remix:folder-fill", item.tintColor, item.glyphPadEnabled, item.glyphPadColor, item.glyphPadMode, cornerRadiusPercent);
        }

        // 5) 关键词匹配内置符号兜底
        return createGlyph(glyphForItem(item), item.tintColor);
    }

    /**
     * 统一解析入口（重载 2：默认回退到默认设置）
     */
    @NonNull
    public static Drawable resolve(@Nullable Context ctx, @Nullable ShortcutItem item) {
        return resolve(ctx, item, null);
    }

    /**
     * 解析当前快捷方式所对应的原始应用图标或本地上传图片（若使用的是位图/应用图标则返回非空 Drawable，否则返回 null）
     */
    @Nullable
    public static Drawable resolveRawAppIcon(
        @Nullable Context ctx,
        @Nullable String iconPackage,
        @Nullable String iconGlyph,
        @Nullable String actionType,
        @Nullable String actionPayload
    ) {
        if (ctx == null) return null;
        if (isCustomImage(iconPackage)) {
            return decodeCustomImageDrawable(ctx, iconPackage);
        }
        if (isCustomImage(iconGlyph)) {
            return decodeCustomImageDrawable(ctx, iconGlyph);
        }
        if (notBlank(iconGlyph)) {
            return null;
        }
        if (notBlank(iconPackage)) {
            Drawable d = appIcon(ctx, iconPackage.trim());
            if (d != null) return d;
        }
        if (ShortcutItem.ACTION_APP.equals(actionType) && notBlank(actionPayload)) {
            Drawable d = appIcon(ctx, actionPayload.trim());
            if (d != null) return d;
        }
        if (ShortcutItem.ACTION_INTENT_URI.equals(actionType) && notBlank(actionPayload)) {
            Drawable d = iconFromIntent(ctx, actionPayload.trim());
            if (d != null) return d;
        }
        if (ShortcutItem.ACTION_ACTIVITY.equals(actionType) && notBlank(actionPayload)) {
            String pkg = io.github.qmryqmm.hyperopen.model.ActivityLaunchSpec
                    .fromJson(actionPayload).resolvePackage();
            if (pkg != null) {
                Drawable d = appIcon(ctx, pkg);
                if (d != null) return d;
            }
        }
        return null;
    }

    /**
     * 解析 Miuix / Remix 矢量图标或兼容内置符号
     */
    @Nullable
    public static Drawable resolveMiuixIcon(@NonNull String glyphKey, int tintColor, boolean padEnabled, int padColor, int padMode) {
        return resolveMiuixIcon(glyphKey, tintColor, padEnabled, padColor, padMode, 50);
    }

    @Nullable
    public static Drawable resolveMiuixIcon(@NonNull String glyphKey, int tintColor, boolean padEnabled, int padColor, int padMode, int cornerRadiusPercent) {
        // 1. 优先尝试 Remix Icon 矢量
        try {
            if (io.github.qmryqmm.hyperopen.ui.RemixIconRegistry.INSTANCE.isRemixIcon(glyphKey)) {
                androidx.compose.ui.graphics.vector.ImageVector rv =
                    io.github.qmryqmm.hyperopen.ui.RemixIconRegistry.INSTANCE.find(glyphKey);
                if (rv != null) {
                    return new MiuixVectorDrawable(rv, tintColor, padEnabled, padColor, padMode, cornerRadiusPercent);
                }
            }
        } catch (Throwable ignored) {}

        // 2. 尝试 Miuix 官方扩展图标
        try {
            androidx.compose.ui.graphics.vector.ImageVector v =
                io.github.qmryqmm.hyperopen.ui.MiuixIconRegistry.INSTANCE.find(glyphKey, null);
            if (v != null) {
                return new MiuixVectorDrawable(v, tintColor, padEnabled, padColor, padMode, cornerRadiusPercent);
            }
        } catch (Throwable ignored) {}

        String clean = glyphKey;
        if (clean.contains(":")) {
            clean = clean.split(":")[0].trim();
        }
        return new GlyphDrawable(clean.toLowerCase(), tintColor, padEnabled, padColor, padMode, cornerRadiusPercent);
    }

    @Nullable
    public static Drawable resolveMiuixIcon(@NonNull String glyphKey, int tintColor) {
        return resolveMiuixIcon(glyphKey, tintColor, false, 0, HyperOpenConfig.GLYPH_PAD_MODE_TRANSLUCENT);
    }

    /**
     * 应用图标提取（带进程级缓存）
     */
    @Nullable
    public static Drawable appIcon(@Nullable Context ctx, @Nullable String pkg) {
        if (ctx == null || pkg == null || pkg.isEmpty()) return null;
        if (isCustomImage(pkg)) {
            return decodeCustomImageDrawable(ctx, pkg);
        }
        synchronized (ICON_CACHE) {
            Drawable.ConstantState hit = ICON_CACHE.get(pkg);
            if (hit != null) return hit.newDrawable(ctx.getResources());
        }
        try {
            Drawable d = ctx.getPackageManager().getApplicationIcon(pkg);
            if (d != null) {
                Drawable.ConstantState cs = d.getConstantState();
                if (cs != null) {
                    synchronized (ICON_CACHE) {
                        if (ICON_CACHE.size() > 48) ICON_CACHE.clear();
                        ICON_CACHE.put(pkg, cs);
                    }
                    return cs.newDrawable(ctx.getResources());
                }
                return d;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * 从 Intent URI 解析目标应用图标：
     * intent.getPackage() → intent.getComponent() → resolveActivity
     */
    @Nullable
    private static Drawable iconFromIntent(@Nullable Context ctx, @NonNull String uri) {
        if (ctx == null) return null;
        try {
            Intent intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
            String pkg = intent.getPackage();
            if (notBlank(pkg)) {
                Drawable d = appIcon(ctx, pkg.trim());
                if (d != null) return d;
            }
            ComponentName cn = intent.getComponent();
            if (cn != null) {
                Drawable d = appIcon(ctx, cn.getPackageName());
                if (d != null) return d;
            }
            try {
                ResolveInfo ri = ctx.getPackageManager()
                    .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
                if (ri != null && ri.activityInfo != null) {
                    Drawable d = appIcon(ctx, ri.activityInfo.packageName);
                    if (d != null) return d;
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Log.d(TAG, "Intent 图标解析失败: " + t.getMessage());
        }
        return null;
    }

    /**
     * 根据 id / title / subtitle 关键词匹配内置符号 key
     */
    @NonNull
    public static String glyphForItem(@Nullable ShortcutItem item) {
        if (item == null) return "sparkle";
        String hay = ((item.id == null ? "" : item.id) + " "
            + (item.title == null ? "" : item.title) + " "
            + (item.subtitle == null ? "" : item.subtitle)).toLowerCase();

        if (match(hay, "扫码", "扫一扫", "扫描", "scan", "qr", "码识别")) return "scan";
        if (match(hay, "付款", "收款", "支付", "钱包", "pay", "wallet", "money")) return "wallet";
        if (match(hay, "乘车", "公交", "地铁", "一卡通", "交通", "bus", "transit", "card")) return "bus";
        if (match(hay, "手电", "电筒", "flash", "torch")) return "flashlight";
        if (match(hay, "相机", "拍照", "街拍", "摄像", "camera", "photo")) return "camera";
        if (match(hay, "计算", "calc")) return "calc";
        if (match(hay, "便签", "笔记", "备忘", "note")) return "note";
        if (match(hay, "录音", "麦克风", "配音", "record", "mic", "voice")) return "mic";
        if (match(hay, "音乐", "识曲", "听歌", "music", "song", "歌曲")) return "music";
        if (match(hay, "设置", "setting")) return "settings";
        return "sparkle";
    }

    private static boolean match(String hay, String... keys) {
        for (String k : keys) {
            if (hay.contains(k)) return true;
        }
        return false;
    }

    /**
     * 创建内置矢量符号 Drawable（按 tint 着色并向白色提亮 18% 提升玻璃底上的可读性）
     */
    @NonNull
    public static Drawable createGlyph(@Nullable String key, int tintColor) {
        if (key == null) key = "sparkle";
        return new GlyphDrawable(key, tintColor == 0 ? 0xFFFFFFFF : tintColor);
    }

    // ============================ 内置矢量符号 ============================

    public static class GlyphDrawable extends Drawable {
        private static final float GRID = 24f;

        private final Path strokePath = new Path();
        private final Path fillPath = new Path();
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String key;
        private boolean padEnabled = false;
        private int padColor = 0;
        private int padMode = HyperOpenConfig.GLYPH_PAD_MODE_TRANSLUCENT;
        private int cornerRadiusPercent = 50;
        private final Paint padPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint padBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        GlyphDrawable(String key, int tintColor) {
            this(key, tintColor, false, 0, HyperOpenConfig.GLYPH_PAD_MODE_TRANSLUCENT, 50);
        }

        GlyphDrawable(String key, int tintColor, boolean padEnabled, int padColor, int padMode) {
            this(key, tintColor, padEnabled, padColor, padMode, 50);
        }

        GlyphDrawable(String key, int tintColor, boolean padEnabled, int padColor, int padMode, int cornerRadiusPercent) {
            this.key = key;
            this.padEnabled = padEnabled;
            this.padColor = padColor;
            this.padMode = padMode;
            this.cornerRadiusPercent = Math.max(0, Math.min(50, cornerRadiusPercent));

            int baseColor = padColor != 0 ? padColor : tintColor;
            int color;
            if (padEnabled) {
                if (padMode == HyperOpenConfig.GLYPH_PAD_MODE_INVERTED) {
                    color = blend(baseColor, 0xFFFFFFFF, 0.15f);
                } else {
                    color = 0xFFFFFFFF;
                }

                padPaint.setStyle(Paint.Style.FILL);
                if (padMode == HyperOpenConfig.GLYPH_PAD_MODE_SOLID) {
                    padPaint.setColor(Color.rgb(Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)));
                } else if (padMode == HyperOpenConfig.GLYPH_PAD_MODE_INVERTED) {
                    padPaint.setColor(Color.argb(48, 255, 255, 255));
                } else {
                    padPaint.setColor(Color.argb(165, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)));
                }

                padBorderPaint.setStyle(Paint.Style.STROKE);
                padBorderPaint.setStrokeWidth(2.5f);
                padBorderPaint.setColor(Color.argb(45, 255, 255, 255));
            } else {
                color = blend(tintColor, 0xFFFFFFFF, 0.18f);
            }

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(1.75f);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            strokePaint.setColor(color);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(color);
            buildGlyph(key, strokePath, fillPath);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;

            if (padEnabled) {
                float cx = b.exactCenterX();
                float cy = b.exactCenterY();
                float halfRadius = Math.max(1f, Math.min(b.width(), b.height()) / 2f - 1f);
                float cornerRadius = halfRadius * (cornerRadiusPercent / 50f);
                canvas.drawRoundRect(
                    cx - halfRadius,
                    cy - halfRadius,
                    cx + halfRadius,
                    cy + halfRadius,
                    cornerRadius,
                    cornerRadius,
                    padPaint
                );
                float borderHalf = halfRadius - 1.25f;
                float borderCorner = Math.max(0f, cornerRadius - 1.25f);
                canvas.drawRoundRect(
                    cx - borderHalf,
                    cy - borderHalf,
                    cx + borderHalf,
                    cy + borderHalf,
                    borderCorner,
                    borderCorner,
                    padBorderPaint
                );
            }

            float contentFactor = padEnabled ? 0.58f : 0.82f;
            float scale = Math.min(b.width(), b.height()) / GRID * contentFactor;
            canvas.save();
            canvas.translate(b.centerX() - (GRID / 2f) * scale, b.centerY() - (GRID / 2f) * scale);
            canvas.scale(scale, scale);
            canvas.drawPath(strokePath, strokePaint);
            canvas.drawPath(fillPath, fillPaint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            strokePaint.setAlpha(alpha);
            fillPaint.setAlpha(alpha);
            padPaint.setAlpha(alpha);
            padBorderPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            strokePaint.setColorFilter(colorFilter);
            fillPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return (int) (GRID * 4);
        }

        @Override
        public int getIntrinsicHeight() {
            return (int) (GRID * 4);
        }
    }

    // ---------- Path 构建 ----------

    private static void line(Path p, float x1, float y1, float x2, float y2) {
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
    }

    private static void dot(Path p, float x, float y, float r) {
        p.addCircle(x, y, r, Path.Direction.CW);
    }

    private static void rrect(Path p, float l, float t, float r, float b, float rad) {
        p.addRoundRect(l, t, r, b, rad, rad, Path.Direction.CW);
    }

    private static void buildGlyph(String key, Path sp, Path fp) {
        switch (key) {
            case "arrow_left":
            case "back": {
                // 左箭头 (用于悬浮返回按钮)
                sp.moveTo(15, 5);
                sp.lineTo(8, 12);
                sp.lineTo(15, 19);
                break;
            }
            case "qrcode": {
                // 二维码：三个角回字取景码 + 点阵
                // 左上回字
                rrect(sp, 3.2f, 3.2f, 9.8f, 9.8f, 1.2f);
                rrect(fp, 5.2f, 5.2f, 7.8f, 7.8f, 0.6f);
                // 右上回字
                rrect(sp, 14.2f, 3.2f, 20.8f, 9.8f, 1.2f);
                rrect(fp, 16.2f, 5.2f, 18.8f, 7.8f, 0.6f);
                // 左下回字
                rrect(sp, 3.2f, 14.2f, 9.8f, 20.8f, 1.2f);
                rrect(fp, 5.2f, 16.2f, 7.8f, 18.8f, 0.6f);
                // 右下与中间点阵
                dot(fp, 12f, 12f, 1.1f);
                dot(fp, 17.5f, 14f, 1.1f);
                dot(fp, 14f, 17.5f, 1.1f);
                dot(fp, 18.5f, 18.5f, 1.1f);
                break;
            }
            case "scan": {
                // 二维码取景框：四角括号 + 中心码块
                sp.moveTo(3, 8.5f); sp.lineTo(3, 4.8f); sp.quadTo(3, 3, 4.8f, 3); sp.lineTo(8.5f, 3);
                sp.moveTo(15.5f, 3); sp.lineTo(19.2f, 3); sp.quadTo(21, 3, 21, 4.8f); sp.lineTo(21, 8.5f);
                sp.moveTo(21, 15.5f); sp.lineTo(21, 19.2f); sp.quadTo(21, 21, 19.2f, 21); sp.lineTo(15.5f, 21);
                sp.moveTo(8.5f, 21); sp.lineTo(4.8f, 21); sp.quadTo(3, 21, 3, 19.2f); sp.lineTo(3, 15.5f);
                rrect(sp, 9.4f, 9.4f, 14.6f, 14.6f, 1.4f);
                dot(fp, 10.9f, 10.9f, 0.85f);
                dot(fp, 13.1f, 13.1f, 0.85f);
                break;
            }
            case "flashlight": {
                // 手电筒：机身轮廓 + 灯头隔线 + 开关
                sp.moveTo(8.8f, 3); sp.lineTo(15.2f, 3); sp.lineTo(15.2f, 6.2f);
                sp.lineTo(13.6f, 9.2f); sp.lineTo(13.6f, 21); sp.lineTo(10.4f, 21);
                sp.lineTo(10.4f, 9.2f); sp.lineTo(8.8f, 6.2f); sp.close();
                line(sp, 8.8f, 5.5f, 15.2f, 5.5f);
                dot(fp, 12, 13.2f, 1.0f);
                break;
            }
            case "camera": {
                // 相机：机身 + 顶部取景凸起 + 镜头 + 指示灯
                rrect(sp, 3, 7, 21, 19.2f, 2.4f);
                sp.moveTo(8.2f, 7); sp.lineTo(9.3f, 4.8f); sp.quadTo(9.7f, 4, 10.6f, 4);
                sp.lineTo(13.4f, 4); sp.quadTo(14.3f, 4, 14.7f, 4.8f); sp.lineTo(15.8f, 7);
                sp.addCircle(12, 13.1f, 3.4f, Path.Direction.CW);
                dot(fp, 17.7f, 9.9f, 0.8f);
                break;
            }
            case "calc": {
                // 计算器：机身 + 屏幕线 + 九宫按键
                rrect(sp, 4.8f, 3, 19.2f, 21, 2.6f);
                line(sp, 7.9f, 7.3f, 16.1f, 7.3f);
                float[] xs = {8.7f, 12f, 15.3f};
                float[] ys = {11.5f, 15.2f, 18.9f};
                for (float x : xs) for (float y : ys) dot(fp, x, y, 0.95f);
                break;
            }
            case "note": {
                // 便签：折角页 + 两行文字线
                sp.moveTo(6, 3.3f); sp.lineTo(13.5f, 3.3f); sp.lineTo(18.7f, 8.5f);
                sp.lineTo(18.7f, 20.3f); sp.quadTo(18.7f, 21, 18, 21); sp.lineTo(6.7f, 21);
                sp.quadTo(6, 21, 6, 20.3f); sp.close();
                sp.moveTo(13.5f, 3.3f); sp.lineTo(13.5f, 7.8f); sp.quadTo(13.5f, 8.5f, 14.2f, 8.5f);
                sp.lineTo(18.7f, 8.5f);
                line(sp, 9.2f, 12.8f, 15.5f, 12.8f);
                line(sp, 9.2f, 16.4f, 13.2f, 16.4f);
                break;
            }
            case "mic": {
                // 麦克风：拾音胶囊 + 弧形支架 + 麦杆底座
                rrect(sp, 9.4f, 2.6f, 14.6f, 11.8f, 2.6f);
                sp.moveTo(6.1f, 10.8f); sp.quadTo(6.1f, 16.2f, 12, 16.2f); sp.quadTo(17.9f, 16.2f, 17.9f, 10.8f);
                line(sp, 12, 16.2f, 12, 19.9f);
                line(sp, 8.6f, 20.7f, 15.4f, 20.7f);
                break;
            }
            case "music": {
                // 音符：双八分音符（双头 + 连梁）
                dot(fp, 9.8f, 18, 2.5f);
                dot(fp, 16.6f, 16.4f, 2.5f);
                line(sp, 12.3f, 18, 12.3f, 5.5f);
                line(sp, 19.1f, 16.4f, 19.1f, 3.9f);
                fp.moveTo(12.3f, 4.1f); fp.lineTo(19.1f, 2.5f); fp.lineTo(19.1f, 5.3f); fp.lineTo(12.3f, 6.9f); fp.close();
                break;
            }
            case "settings": {
                // 齿轮：中心环 + 8 根齿柱（三角函数生成，避免复杂路径）
                sp.addCircle(12, 12, 3.1f, Path.Direction.CW);
                double start = -Math.PI / 2;
                for (int k = 0; k < 8; k++) {
                    double a = start + k * Math.PI / 4;
                    float x1 = 12 + 5.6f * (float) Math.cos(a);
                    float y1 = 12 + 5.6f * (float) Math.sin(a);
                    float x2 = 12 + 9.2f * (float) Math.cos(a);
                    float y2 = 12 + 9.2f * (float) Math.sin(a);
                    line(sp, x1, y1, x2, y2);
                }
                break;
            }
            case "bus": {
                // 交通卡：卡体 + 磁条线 + 刷码点阵
                rrect(sp, 2.8f, 4.6f, 21.2f, 19.4f, 2.4f);
                line(sp, 2.8f, 9.4f, 21.2f, 9.4f);
                dot(fp, 6.9f, 13.2f, 1.05f);
                dot(fp, 10.7f, 13.2f, 1.05f);
                dot(fp, 14.5f, 13.2f, 1.05f);
                dot(fp, 6.9f, 16.9f, 1.05f);
                dot(fp, 10.7f, 16.9f, 1.05f);
                rrect(sp, 13.5f, 15.3f, 17.1f, 18.5f, 0.5f);
                break;
            }
            case "wallet": {
                // 钱包：卡包体 + 侧袋 + 搭扣
                rrect(sp, 2.8f, 5.2f, 21.2f, 18.8f, 3.0f);
                rrect(sp, 14.2f, 10.4f, 21.2f, 15.6f, 1.6f);
                dot(fp, 17.7f, 13, 0.95f);
                break;
            }
            case "bankcards": {
                // 银行卡/付款码：卡体 + 芯片 + 磁条
                rrect(sp, 3, 5.2f, 21, 18.8f, 2.4f);
                line(sp, 3, 9.5f, 21, 9.5f);
                rrect(sp, 5.8f, 12.8f, 10.2f, 16.2f, 0.8f);
                break;
            }
            case "photos": {
                // 相册/照片：相框 + 太阳 + 群山
                rrect(sp, 3.2f, 4, 20.8f, 20, 2.4f);
                dot(fp, 7.8f, 8.5f, 1.3f);
                sp.moveTo(3.5f, 17.2f);
                sp.lineTo(8.2f, 12.5f);
                sp.lineTo(12.8f, 16.8f);
                sp.lineTo(15.5f, 14.2f);
                sp.lineTo(20.5f, 18.8f);
                break;
            }
            case "search": {
                // 搜索/放大镜：镜框 + 镜柄
                sp.addCircle(10.5f, 10.5f, 5.5f, Path.Direction.CW);
                line(sp, 14.5f, 14.5f, 19.5f, 19.5f);
                break;
            }
            case "home": {
                // 主页/房子
                sp.moveTo(12, 3.5f);
                sp.lineTo(20, 10.5f);
                sp.lineTo(17.8f, 10.5f);
                sp.lineTo(17.8f, 20);
                sp.lineTo(6.2f, 20);
                sp.lineTo(6.2f, 10.5f);
                sp.lineTo(4, 10.5f);
                sp.close();
                rrect(sp, 9.5f, 13, 14.5f, 20, 0.8f);
                break;
            }
            case "folder": {
                // 文件夹
                sp.moveTo(3.2f, 6);
                sp.lineTo(8.5f, 6);
                sp.lineTo(10.5f, 8.2f);
                sp.lineTo(20.8f, 8.2f);
                sp.lineTo(20.8f, 19.5f);
                sp.lineTo(3.2f, 19.5f);
                sp.close();
                break;
            }
            case "alarm": {
                // 闹钟：钟面 + 表耳 + 指针
                sp.addCircle(12, 13, 6.8f, Path.Direction.CW);
                line(sp, 12, 13, 12, 9.5f);
                line(sp, 12, 13, 15, 13);
                line(sp, 4.8f, 5.2f, 7.8f, 7.5f);
                line(sp, 19.2f, 5.2f, 16.2f, 7.5f);
                break;
            }
            case "lock": {
                // 锁
                rrect(sp, 5.5f, 10, 18.5f, 20.5f, 2.2f);
                sp.moveTo(8.5f, 10);
                sp.lineTo(8.5f, 6.8f);
                sp.quadTo(8.5f, 3.8f, 12, 3.8f);
                sp.quadTo(15.5f, 3.8f, 15.5f, 6.8f);
                sp.lineTo(15.5f, 10);
                dot(fp, 12, 14.5f, 1.1f);
                line(sp, 12, 14.5f, 12, 17.5f);
                break;
            }
            case "sparkle":
            default: {
                // 兜底：四角星芒（填充）
                fp.moveTo(12, 2.8f);
                fp.cubicTo(12.9f, 8.6f, 15.4f, 11.1f, 21.2f, 12);
                fp.cubicTo(15.4f, 12.9f, 12.9f, 15.4f, 12, 21.2f);
                fp.cubicTo(11.1f, 15.4f, 8.6f, 12.9f, 2.8f, 12);
                fp.cubicTo(8.6f, 11.1f, 11.1f, 8.6f, 12, 2.8f);
                fp.close();
                break;
            }
        }
    }

    private static int blend(int c1, int c2, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        int a = Math.round(Color.alpha(c1) * (1 - r) + Color.alpha(c2) * r);
        int cr = Math.round(Color.red(c1) * (1 - r) + Color.red(c2) * r);
        int cg = Math.round(Color.green(c1) * (1 - r) + Color.green(c2) * r);
        int cb = Math.round(Color.blue(c1) * (1 - r) + Color.blue(c2) * r);
        return Color.argb(a, cr, cg, cb);
    }
}
