package io.github.qmryqmm.hyperopen.view;

import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import io.github.qmryqmm.hyperopen.model.ShortcutItem;

/**
 * 提示胶囊文字样式统一驱动器：把 {@link ShortcutItem} 的 10 个 hint* 字段
 * 应用到标题/副标题 TextView（颜色+透明度 / 投影 / 描边 / 字重 / 字间距 / 显隐），
 * SystemUI 进程（CustomHyperOpenContainer）与模块 App 进程（HyperOpenPreviewView）共用，
 * 保证真机与 1:1 预览渲染完全一致。
 *
 * <p>透明度合成分量：最终 alpha = 基色自带 alpha × hint*Alpha（基色无 alpha 时按 0xFF 计），
 * RGB 保持基色不变；副标题回退链 = hintTextColorSub → 标题色 → tintColor（见 ShortcutItem.resolve*）。
 */
public final class HintTextStyler {

    /** 描边固定深色：覆盖亮色文字在浅色壁纸上的可读性 */
    private static final int STROKE_COLOR = 0xB3000000;
    /** 投影颜色（黑色柔光） */
    private static final int SHADOW_COLOR = 0x99000000;
    /** 字间距滑条系数：滑条值 ×0.05em（-2~+6 → -0.1~+0.3em） */
    private static final float LETTER_SPACING_UNIT = 0.05f;

    private HintTextStyler() {
    }

    /**
     * 选中态应用：按字段设置文案、可见性、
     * 颜色+透明度、投影、描边、字重（仅标题）、字间距。
     * 支持传入全局配置以实现智能跟随底色与全局默认质感。
     */
    public static void apply(TextView tv, ShortcutItem item, boolean isTitle) {
        apply(tv, item, isTitle, null);
    }

    public static void apply(TextView tv, ShortcutItem item, boolean isTitle, io.github.qmryqmm.hyperopen.config.HyperOpenConfig config) {
        boolean show = isTitle ? item.showHintTitle : item.showHintSubtitle;
        tv.setVisibility(show ? View.VISIBLE : View.GONE);

        if (isTitle) {
            tv.setText(item.title == null ? "" : item.title);
        } else {
            tv.setText(item.subtitle == null ? "" : item.subtitle);
        }

        int color;
        float alpha;
        int shadow;
        int stroke;
        int weight;
        float spacing;

        if (item.followGlobalHintStyle) {
            // 跟随全局配置：颜色按全局模式（默认智能跟随图标底色），质感/版式全取全局
            color = isTitle ? item.resolveHintTitleColor(config) : item.resolveHintSubtitleColor(config);
            alpha = isTitle ? 1.0f : 0.82f;
            shadow = (config != null) ? config.defaultHintTextShadow : 0;
            stroke = (config != null) ? config.defaultHintTextStroke : 0;
            weight = (config != null) ? config.defaultHintTextWeight : 3;
            spacing = (config != null) ? config.defaultHintTextLetterSpacing : 0f;
        } else {
            // 单独自定义覆盖
            color = isTitle ? (item.hintTextColor != 0 ? item.hintTextColor : item.resolveIconPrimaryColor())
                            : (item.hintTextColorSub != 0 ? item.hintTextColorSub : (item.hintTextColor != 0 ? item.hintTextColor : item.resolveIconPrimaryColor()));
            alpha = isTitle ? item.resolveHintTitleAlpha() : item.resolveHintSubtitleAlpha();
            shadow = Math.max(0, item.hintTextShadow);
            stroke = Math.max(0, item.hintTextStroke);
            weight = (item.hintTextWeight >= 0) ? item.hintTextWeight : 3;
            spacing = item.hintTextLetterSpacing;
        }

        int baseAlpha = color >>> 24;
        if (baseAlpha == 0) {
            baseAlpha = 255;
        }
        int finalAlpha = Math.max(1, Math.min(255, Math.round(baseAlpha * alpha)));
        tv.setTextColor((color & 0x00FFFFFF) | (finalAlpha << 24));

        float density = tv.getResources().getDisplayMetrics().density;
        tv.setShadowLayer(shadow / 20f * 4f, 0f, 0f, SHADOW_COLOR);
        if (tv instanceof StrokeTextView) {
            ((StrokeTextView) tv).setStroke(stroke * density, STROKE_COLOR);
        }
        if (isTitle) {
            applyTitleWeight(tv, weight);
        }
        tv.setLetterSpacing(spacing * LETTER_SPACING_UNIT);
    }

    /** 复位到 idle/默认样式（buildHeader 初始观感：白标题假粗体、0xAA 白副标题、无特效） */
    public static void reset(TextView tv, boolean isTitle) {
        if (tv.getVisibility() != View.VISIBLE) {
            tv.setVisibility(View.VISIBLE);
        }
        tv.setTextColor(isTitle ? Color.WHITE : 0xAAFFFFFF);
        tv.setShadowLayer(0f, 0f, 0f, 0);
        if (tv instanceof StrokeTextView) {
            ((StrokeTextView) tv).setStroke(0f, STROKE_COLOR);
        }
        tv.setLetterSpacing(0f);
        if (isTitle) {
            applyTitleWeight(tv, 3);
        }
    }

    /** 四档字重，仅作用于标题：0 Light / 1 Regular / 2 Medium / 3 Bold（假粗体，与旧版一致） */
    private static void applyTitleWeight(TextView tv, int weight) {
        switch (weight) {
            case 0:
                tv.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                tv.getPaint().setFakeBoldText(false);
                break;
            case 1:
                tv.setTypeface(Typeface.DEFAULT);
                tv.getPaint().setFakeBoldText(false);
                break;
            case 2:
                tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                tv.getPaint().setFakeBoldText(false);
                break;
            default:
                tv.setTypeface(Typeface.DEFAULT);
                tv.getPaint().setFakeBoldText(true);
                break;
        }
    }

    /** 取 TextView 当前 sp 字号（编辑页预览卡对齐胶囊观感用） */
    public static float getTextSizeSp(TextView tv) {
        return tv.getTextSize() / tv.getResources().getDisplayMetrics().scaledDensity;
    }

    /** 稳定的 sp→px（编辑页预览卡尺寸用） */
    public static float spToPx(View v, float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                v.getResources().getDisplayMetrics());
    }
}
