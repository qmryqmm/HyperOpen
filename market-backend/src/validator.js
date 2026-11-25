export const MAX_PAYLOAD_BYTES = 512 * 1024;
export const MAX_CUSTOM_IMAGE_BYTES = 64 * 1024;
export const MAX_CUSTOM_IMAGE_COUNT = 24;
export const MAX_TOP_LEVEL_SHORTCUTS = 50;
export const MAX_TOTAL_SHORTCUTS = 100;

export class ValidationError extends Error {
  constructor(message, status = 400) {
    super(message);
    this.name = "ValidationError";
    this.status = status;
  }
}

export const RUNTIME_SKIP_KEYS = new Set([
  "last_real_fod_x",
  "last_real_fod_y",
  "__de_migrated_v1",
]);

export const APP_UI_SKIP_KEYS = new Set([
  "ui_style",
  "theme_mode",
  "monet_colors",
  "bar_blur",
  "floating_nav_bar",
  "predictive_back",
  "edge_back",
  "page_switch_gesture",
]);

/**
 * 与 ConfigBackup.ALLOWED_KEY_TYPES 完全一致的 119 键白名单与类型标记
 */
export const ALLOWED_KEY_TYPES = Object.freeze({
  material_style: "i",
  offset_y: "i",
  region_diameter: "i",
  show_region_bg: "b",
  show_header_pill_bg: "b",
  show_header_pill: "b",
  header_top_margin: "i",
  header_title_size: "i",
  header_subtitle_size: "i",
  header_blur_radius: "i",
  header_text_centered: "b",
  icon_size: "i",
  max_icon_count: "i",
  layout_style: "i",
  stock_system_region: "b",
  ring_sector_mode: "b",
  sector_line_color: "i",
  sector_line_alpha: "f",
  sector_dash_length: "i",
  sector_dash_gap: "i",
  sector_line_width: "f",
  sector_highlight_enabled: "b",
  anim_duration_ms: "i",
  panel_expand_anim_type: "i",
  panel_expand_stagger_ms: "i",
  dismiss_anim_enabled: "b",
  panel_collapse_anim_type: "i",
  panel_collapse_duration_ms: "i",
  panel_collapse_stagger_ms: "i",
  panel_selected_pop_scale: "f",
  global_hint_color_mode: "i",
  global_hint_custom_color: "i",
  default_hint_text_stroke: "i",
  default_hint_text_shadow: "i",
  default_hint_text_weight: "i",
  default_hint_text_letter_spacing: "f",
  default_show_hint_title: "b",
  default_show_hint_subtitle: "b",
  folder_hover_delay_ms: "i",
  folder_child_display_range_scale: "f",
  folder_child_direction_mode: "i",
  folder_child_direction_offset_deg: "f",
  folder_child_order_mode: "i",
  folder_child_min_gap_dp: "i",
  folder_child_size_scale: "f",
  folder_child_expand_anim_type: "i",
  folder_child_expand_duration_ms: "i",
  folder_child_expand_stagger_ms: "i",
  folder_child_collapse_anim_type: "i",
  folder_child_collapse_duration_ms: "i",
  folder_child_collapse_stagger_ms: "i",
  folder_child_start_scale: "f",
  folder_child_overshoot: "f",
  max_scale: "f",
  influence_radius: "i",
  icon_alpha: "f",
  fisheye_zoom_alpha: "f",
  fisheye_other_alpha: "f",
  icon_corner_radius: "i",
  fullscreen_blur_mask_alpha: "f",
  fullscreen_dim_alpha: "f",
  header_dim_alpha: "f",
  region_dim_alpha: "f",
  bubble_dim_alpha: "f",
  show_delay_ms: "i",
  disable_system_enter_anim: "b",
  face_unlock_quick_open: "b",
  ble_unlock_quick_open: "b",
  haptic_enabled: "b",
  haptic_strength: "i",
  haptic_duration_ms: "i",
  haptic_mode: "i",
  haptic_stock_wave_expand: "i",
  haptic_brake_pulse: "b",
  haptic_double_gap_ms: "i",
  haptic_slide_enabled: "b",
  haptic_slide_strength: "i",
  haptic_slide_duration_ms: "i",
  haptic_slide_mode: "i",
  haptic_stock_wave_slide: "i",
  haptic_slide_brake_pulse: "b",
  haptic_slide_double_gap_ms: "i",
  region_blur_radius: "i",
  icon_spacing_offset: "i",
  show_bubble_bg: "b",
  bubble_padding_percent: "i",
  bubble_blur_radius: "i",
  app_icon_crop_mode: "i",
  glyph_pad_enabled: "b",
  glyph_pad_color: "i",
  glass_preset: "i",
  glass_refract_ior: "f",
  glass_thickness: "f",
  glass_specular: "f",
  glass_matte_alpha: "f",
  glass_border_light: "f",
  glass_background_saturation: "f",
  glass_background_brightness: "f",
  glass_background_burn: "f",
  glass_matte_white_mix: "f",
  glass_matte_color_mix: "f",
  glass_matte_color_power: "f",
  glass_edge_range: "f",
  glass_edge_power: "f",
  glass_reflect_offset: "f",
  global_blur_radius: "i",
  global_dim_alpha: "f",
  bubble_material_follow_global: "b",
  region_material_follow_global: "b",
  header_material_follow_global: "b",
  bubble_glass_matte_alpha: "f",
  region_glass_matte_alpha: "f",
  header_glass_matte_alpha: "f",
  // 6 个旧版兼容键
  stock_system_anim: "b",
  selected_icon_scale: "f",
  shortcuts_json: "s",
  blur_baseline_healed: "b",
  glyph_pad_mode: "i",
  icon_glow_effect: "b",
});

export const VALID_ACTION_TYPES = new Set([
  "APP",
  "INTENT_URI",
  "ACTIVITY",
  "BROADCAST",
  "FLASHLIGHT",
  "SHELL",
  "XIAOAI",
  "MUTE",
  "DND",
  "CAMERA",
  "FOLDER",
]);

const INT_MIN = -2147483648;
const INT_MAX = 2147483647;
const FLOAT_MAX = 3.4028235e38;
const DEFAULT_GLYPH_PAD_ALPHA = 0.65;
const MIN_GLYPH_PAD_ALPHA = 0.05;

function isPlainObject(v) {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

function clamp(v, min, max) {
  return Math.max(min, Math.min(max, v));
}

function clampPadAlpha(alpha) {
  if (!Number.isFinite(alpha) || alpha <= 0) return DEFAULT_GLYPH_PAD_ALPHA;
  return clamp(alpha, MIN_GLYPH_PAD_ALPHA, 1);
}

export function isSkippedKey(key) {
  return (
    typeof key !== "string" ||
    key.startsWith("safety_") ||
    key.startsWith("hook_") ||
    RUNTIME_SKIP_KEYS.has(key) ||
    APP_UI_SKIP_KEYS.has(key)
  );
}

function clampInt(key, value) {
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

function clampFloat(key, value) {
  switch (key) {
    case "max_scale":
    case "selected_icon_scale":
    case "panel_selected_pop_scale":
    case "folder_child_overshoot":
      return clamp(value, 0.1, 5.0);
    case "folder_child_display_range_scale":
    case "folder_child_size_scale":
    case "folder_child_start_scale":
      return clamp(value, 0.05, 5.0);
    case "folder_child_direction_offset_deg":
      return clamp(value, -360, 360);
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
      return clamp(value, 0, 1);
    case "sector_line_width":
      return clamp(value, 0.1, 20);
    case "default_hint_text_letter_spacing":
      return clamp(value, -10, 20);
    case "glass_refract_ior":
      return clamp(value, 0.5, 4.0);
    case "glass_thickness":
      return clamp(value, 0, 20);
    case "glass_specular":
    case "glass_background_saturation":
      return clamp(value, 0, 10);
    case "glass_background_brightness":
      return clamp(value, -1, 1);
    case "glass_matte_color_power":
    case "glass_edge_power":
      return clamp(value, 0.1, 20);
    case "glass_edge_range":
      return clamp(value, 0, 500);
    case "glass_reflect_offset":
      return clamp(value, 0, 5000);
    default:
      return value;
  }
}

function assertFiniteNumber(val, fieldName) {
  if (typeof val !== "number" || !Number.isFinite(val)) {
    throw new ValidationError(`字段 ${fieldName} 必须是有效有限数值`);
  }
  return val;
}

function validateTypedPref(key, entry) {
  if (!isPlainObject(entry)) {
    throw new ValidationError(`配置键 ${key} 的值结构无效`);
  }
  const expectedType = ALLOWED_KEY_TYPES[key];
  if (!expectedType) return null;
  if (entry.t !== expectedType) {
    throw new ValidationError(`配置键 ${key} 类型不匹配（期望 ${expectedType}）`);
  }
  if (!Object.prototype.hasOwnProperty.call(entry, "v")) {
    throw new ValidationError(`配置键 ${key} 缺少 v 字段`);
  }
  const raw = entry.v;
  switch (expectedType) {
    case "b":
      if (typeof raw !== "boolean") {
        throw new ValidationError(`配置键 ${key} 必须是布尔值`);
      }
      return { t: "b", v: raw };
    case "i": {
      if (typeof raw !== "number" || !Number.isFinite(raw) || raw < INT_MIN || raw > INT_MAX) {
        throw new ValidationError(`配置键 ${key} 必须是有效的 32 位整数`);
      }
      return { t: "i", v: clampInt(key, Math.trunc(raw)) };
    }
    case "f": {
      if (typeof raw !== "number" || !Number.isFinite(raw) || Math.abs(raw) > FLOAT_MAX) {
        throw new ValidationError(`配置键 ${key} 必须是有效的有限浮点数`);
      }
      return { t: "f", v: clampFloat(key, raw) };
    }
    case "l": {
      if (typeof raw !== "number" || !Number.isFinite(raw) || !Number.isSafeInteger(Math.trunc(raw))) {
        throw new ValidationError(`配置键 ${key} 必须是有效的长整数`);
      }
      return { t: "l", v: Math.trunc(raw) };
    }
    case "s": {
      if (raw === null) return { t: "s", v: "" };
      if (typeof raw !== "string") {
        throw new ValidationError(`配置键 ${key} 必须是字符串`);
      }
      if (raw.length > 262144) {
        throw new ValidationError(`配置键 ${key} 字符串长度超限`);
      }
      return { t: "s", v: raw };
    }
    case "ss": {
      if (!Array.isArray(raw)) {
        throw new ValidationError(`配置键 ${key} 必须是字符串数组`);
      }
      const deduped = [];
      const seen = new Set();
      for (const item of raw) {
        if (item === null || item === undefined) continue;
        const s = String(item);
        if (!seen.has(s)) {
          seen.add(s);
          deduped.push(s);
        }
      }
      return { t: "ss", v: deduped };
    }
    default:
      return null;
  }
}

function detectImageMagic(bytes) {
  if (bytes.length < 12) return null;
  // PNG: 89 50 4E 47 0D 0A 1A 0A
  if (
    bytes[0] === 0x89 &&
    bytes[1] === 0x50 &&
    bytes[2] === 0x4e &&
    bytes[3] === 0x47 &&
    bytes[4] === 0x0d &&
    bytes[5] === 0x0a &&
    bytes[6] === 0x1a &&
    bytes[7] === 0x0a
  ) {
    return "image/png";
  }
  // JPEG: FF D8 FF
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) {
    return "image/jpeg";
  }
  // WEBP: RIFF....WEBP
  if (
    bytes[0] === 0x52 &&
    bytes[1] === 0x49 &&
    bytes[2] === 0x46 &&
    bytes[3] === 0x46 &&
    bytes[8] === 0x57 &&
    bytes[9] === 0x45 &&
    bytes[10] === 0x42 &&
    bytes[11] === 0x50
  ) {
    return "image/webp";
  }
  return null;
}

export function validateBase64CustomImage(rawValue, pathLabel, fieldName) {
  if (typeof rawValue !== "string" || !rawValue.startsWith("base64:")) {
    return null;
  }
  const clean = rawValue.slice(7).replace(/\s+/g, "");
  if (!clean) {
    throw new ValidationError(`${pathLabel}.${fieldName} 自定义图片内容为空`);
  }
  if (clean.length % 4 !== 0 || !/^[A-Za-z0-9+/]+={0,2}$/.test(clean)) {
    throw new ValidationError(`${pathLabel}.${fieldName} Base64 编码格式非法`);
  }
  const padding = clean.endsWith("==") ? 2 : clean.endsWith("=") ? 1 : 0;
  const estimatedBytes = (clean.length * 3) / 4 - padding;
  if (estimatedBytes > MAX_CUSTOM_IMAGE_BYTES) {
    throw new ValidationError(`${pathLabel}.${fieldName} 自定义图片超过 64 KB 上限`);
  }
  let binary;
  try {
    binary = atob(clean);
  } catch {
    throw new ValidationError(`${pathLabel}.${fieldName} Base64 解码失败`);
  }
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  if (bytes.length > MAX_CUSTOM_IMAGE_BYTES) {
    throw new ValidationError(`${pathLabel}.${fieldName} 自定义图片超过 64 KB 上限`);
  }
  const mimeType = detectImageMagic(bytes);
  if (!mimeType) {
    throw new ValidationError(`${pathLabel}.${fieldName} 图片魔数校验失败（仅支持 PNG / JPEG / WEBP）`);
  }
  return {
    normalizedValue: `base64:${clean}`,
    path: pathLabel,
    field: fieldName,
    mimeType,
    sizeBytes: bytes.length,
    dataUri: `data:${mimeType};base64,${clean}`,
  };
}

function inspectIntentUriRisk(payload) {
  if (!payload) return null;
  const userMatch = /;[iS]\.hyperopen_user=([1-9]\d*)(?:;|$)/.exec(payload);
  if (userMatch) {
    return `跨用户 Intent URI (hyperopen_user=${userMatch[1]})`;
  }
  if (/[\r\n`]|\$\(/.test(payload)) {
    return "Intent URI 含换行或命令替换字符";
  }
  if (/^(?:file|su):/i.test(payload.trim()) || /;scheme=(?:file|su)(?:;|$)/i.test(payload)) {
    return "Intent URI 使用敏感 scheme";
  }
  if (/;[iS]\.(?:cmd|command|shell|script|su|root|exec)=/i.test(payload)) {
    return "Intent URI 含命令型 Extra 参数";
  }
  const intentMarkers = payload.match(/#Intent;/g);
  if (intentMarkers && intentMarkers.length > 1) {
    return "Intent URI 含多个 #Intent 片段";
  }
  if (intentMarkers) {
    const endIdx = payload.lastIndexOf(";end");
    if (endIdx < 0 || payload.slice(endIdx + 4).trim().length > 0) {
      return "Intent URI 在 #Intent 块外存在异常参数";
    }
  } else if (/(?:^|[;\s|&])(?:su|sh\s+-c|busybox|magisk|ksud|apd)(?=$|[;\s|&])|(?:^|[;\s|&])(?:am\s+start|pm\s+install|setprop\s+|chmod\s+|chown\s+)/i.test(payload)) {
    return "Intent URI 含疑似 Shell/特权命令特征";
  }
  if (/(?:^|[;\s|&])(?:su|sh\s+-c|busybox|magisk|ksud|apd)(?=$|[;\s|&])/i.test(payload)) {
    return "Intent URI 含疑似特权执行关键字";
  }
  return null;
}

function asInt32(value, fallback, fieldName) {
  if (value === undefined || value === null) return fallback;
  assertFiniteNumber(value, fieldName);
  return Math.trunc(value) | 0;
}

function asFiniteFloat(value, fallback, fieldName) {
  if (value === undefined || value === null) return fallback;
  assertFiniteNumber(value, fieldName);
  if (Math.abs(value) > FLOAT_MAX) {
    throw new ValidationError(`字段 ${fieldName} 超出浮点数范围`);
  }
  return value;
}

function asBoolean(value, fallback, fieldName) {
  if (value === undefined || value === null) return fallback;
  if (typeof value !== "boolean") {
    throw new ValidationError(`字段 ${fieldName} 必须是布尔值`);
  }
  return value;
}

function asString(value, fallback, fieldName, maxLen = 4096) {
  if (value === undefined || value === null) return fallback;
  if (typeof value !== "string") {
    throw new ValidationError(`字段 ${fieldName} 必须是字符串`);
  }
  if (value.length > maxLen) {
    throw new ValidationError(`字段 ${fieldName} 长度超出 ${maxLen} 字符上限`);
  }
  return value;
}

export function normalizeShortcuts(rawShortcuts, options = { forceZeroOffsets: false }) {
  if (!Array.isArray(rawShortcuts)) {
    throw new ValidationError("shortcuts 必须是数组");
  }
  if (rawShortcuts.length > MAX_TOP_LEVEL_SHORTCUTS) {
    throw new ValidationError(`顶层快捷方式数量不能超过 ${MAX_TOP_LEVEL_SHORTCUTS} 个`);
  }

  let totalCount = 0;
  let folderCount = 0;
  const actionTypesSet = new Set();
  const extractedImages = [];
  const riskSummary = [];

  function normalizeOne(raw, depth, pathPrefix) {
    if (!isPlainObject(raw)) {
      throw new ValidationError("快捷方式条目必须是 JSON 对象");
    }
    totalCount += 1;
    if (totalCount > MAX_TOTAL_SHORTCUTS) {
      throw new ValidationError(`含文件夹子项在内的快捷方式总数不能超过 ${MAX_TOTAL_SHORTCUTS} 个`);
    }

    const actionType = asString(raw.actionType, "APP", "actionType", 32).trim();
    if (!VALID_ACTION_TYPES.has(actionType)) {
      throw new ValidationError(`不支持的动作类型: ${actionType}`);
    }
    if (depth > 0 && actionType === "FOLDER") {
      throw new ValidationError("禁止文件夹嵌套文件夹");
    }
    actionTypesSet.add(actionType);

    const id = asString(raw.id, "", "id", 128);
    const title = asString(raw.title, "未命名", "title", 128);
    const subtitle = asString(raw.subtitle, "", "subtitle", 256);
    const displayTitle = title.trim() || "未命名快捷方式";
    const itemPath = pathPrefix ? `${pathPrefix} > ${displayTitle}` : displayTitle;

    let iconPackage = asString(raw.iconPackage, "", "iconPackage", 131072);
    let iconGlyph = asString(raw.iconGlyph, "", "iconGlyph", 131072);

    if (iconPackage.startsWith("base64:")) {
      const img = validateBase64CustomImage(iconPackage, itemPath, "iconPackage");
      if (extractedImages.length >= MAX_CUSTOM_IMAGE_COUNT) {
        throw new ValidationError(`单次上传自定义图片总数不能超过 ${MAX_CUSTOM_IMAGE_COUNT} 张`);
      }
      extractedImages.push(img);
      iconPackage = img.normalizedValue;
    } else if (iconPackage.length > 256) {
      throw new ValidationError(`${itemPath}.iconPackage 长度超过上限`);
    }

    if (iconGlyph.startsWith("base64:")) {
      const img = validateBase64CustomImage(iconGlyph, itemPath, "iconGlyph");
      if (extractedImages.length >= MAX_CUSTOM_IMAGE_COUNT) {
        throw new ValidationError(`单次上传自定义图片总数不能超过 ${MAX_CUSTOM_IMAGE_COUNT} 张`);
      }
      extractedImages.push(img);
      iconGlyph = img.normalizedValue;
    } else if (iconGlyph.length > 256) {
      throw new ValidationError(`${itemPath}.iconGlyph 长度超过上限`);
    }

    let tintColor = asInt32(raw.tintColor, -16711681, "tintColor");
    const enabled = asBoolean(raw.enabled, true, "enabled");
    const glyphPadEnabled = asBoolean(raw.glyphPadEnabled, false, "glyphPadEnabled");
    let glyphPadColor = asInt32(raw.glyphPadColor, -14007580, "glyphPadColor");

    let glyphPadAlpha;
    if (raw.glyphPadAlpha !== undefined && raw.glyphPadAlpha !== null) {
      glyphPadAlpha = clampPadAlpha(asFiniteFloat(raw.glyphPadAlpha, DEFAULT_GLYPH_PAD_ALPHA, "glyphPadAlpha"));
    } else {
      const legacyMode = asInt32(raw.glyphPadMode, 1, "glyphPadMode");
      if (glyphPadEnabled) {
        if (legacyMode === 0) {
          glyphPadAlpha = 1.0;
          tintColor = -1;
        } else if (legacyMode === 2) {
          if (tintColor === 0) tintColor = glyphPadColor !== 0 ? glyphPadColor : -1;
          glyphPadColor = -1;
          glyphPadAlpha = 0.20;
        } else {
          glyphPadAlpha = DEFAULT_GLYPH_PAD_ALPHA;
          tintColor = -1;
        }
      } else if (legacyMode === 0) {
        glyphPadAlpha = 1.0;
      } else if (legacyMode === 2) {
        glyphPadAlpha = 0.20;
      } else {
        glyphPadAlpha = DEFAULT_GLYPH_PAD_ALPHA;
      }
    }

    const rawCrop = asInt32(raw.appIconCropMode, 2, "appIconCropMode");
    const appIconCropMode = rawCrop > 0 ? rawCrop : 2;
    const customOffsetX = options.forceZeroOffsets
      ? 0
      : asFiniteFloat(raw.customOffsetX, 0, "customOffsetX");
    const customOffsetY = options.forceZeroOffsets
      ? 0
      : asFiniteFloat(raw.customOffsetY, 0, "customOffsetY");
    const userId = asInt32(raw.userId, 0, "userId");
    if (userId < 0 || userId > 99999) {
      throw new ValidationError(`${itemPath}.userId 超出合法范围`);
    }
    const shellUseRoot = asBoolean(raw.shellUseRoot, false, "shellUseRoot");

    const hintTextColor = asInt32(raw.hintTextColor, 0, "hintTextColor");
    const hintTextAlpha = asFiniteFloat(raw.hintTextAlpha, 1.0, "hintTextAlpha");
    const hintTextColorSub = asInt32(raw.hintTextColorSub, 0, "hintTextColorSub");
    const hintTextAlphaSub = asFiniteFloat(raw.hintTextAlphaSub, -1.0, "hintTextAlphaSub");
    const hintTextShadow = clamp(asInt32(raw.hintTextShadow, 0, "hintTextShadow"), 0, 200);
    const hintTextStroke = clamp(asInt32(raw.hintTextStroke, 0, "hintTextStroke"), 0, 20);
    const hintTextWeight = clamp(asInt32(raw.hintTextWeight, 3, "hintTextWeight"), 0, 3);
    const hintTextLetterSpacing = clamp(
      asFiniteFloat(raw.hintTextLetterSpacing, 0.0, "hintTextLetterSpacing"),
      -10,
      20
    );
    const showHintTitle = asBoolean(raw.showHintTitle, true, "showHintTitle");
    const showHintSubtitle = asBoolean(raw.showHintSubtitle, true, "showHintSubtitle");

    let followGlobalHintStyle;
    if (raw.followGlobalHintStyle !== undefined && raw.followGlobalHintStyle !== null) {
      followGlobalHintStyle = asBoolean(raw.followGlobalHintStyle, true, "followGlobalHintStyle");
    } else {
      const hasCustom =
        hintTextColor !== 0 ||
        hintTextStroke > 0 ||
        hintTextShadow > 0 ||
        hintTextWeight !== 3 ||
        hintTextLetterSpacing !== 0;
      followGlobalHintStyle = !hasCustom;
    }

    let actionPayload = asString(raw.actionPayload, "", "actionPayload", 65536);
    let activityUseRoot = false;

    if (actionType === "FOLDER") {
      if (depth === 0) folderCount += 1;
      let childrenArr = [];
      const trimmedPayload = actionPayload.trim();
      if (trimmedPayload.length > 0) {
        let parsedChildren;
        try {
          parsedChildren = JSON.parse(trimmedPayload);
        } catch {
          throw new ValidationError(`${itemPath} 文件夹子项 JSON 解析失败`);
        }
        if (!Array.isArray(parsedChildren)) {
          throw new ValidationError(`${itemPath} 文件夹子项必须是 JSON 数组`);
        }
        childrenArr = parsedChildren.map((child) => normalizeOne(child, depth + 1, itemPath));
      }
      actionPayload = JSON.stringify(childrenArr);
    } else if (actionType === "ACTIVITY") {
      const trimmedPayload = actionPayload.trim();
      if (trimmedPayload.length > 0) {
        let spec;
        try {
          spec = JSON.parse(trimmedPayload);
        } catch {
          throw new ValidationError(`${itemPath} ActivityLaunchSpec JSON 解析失败`);
        }
        if (!isPlainObject(spec)) {
          throw new ValidationError(`${itemPath} ActivityLaunchSpec 必须是 JSON 对象`);
        }
        activityUseRoot = spec.useRoot === true;
      }
    }

    const riskReasons = [];
    if (actionType === "SHELL") {
      riskReasons.push(shellUseRoot ? "Shell Root 命令" : "Shell 命令");
    } else if (actionType === "BROADCAST") {
      riskReasons.push(shellUseRoot ? "自定义广播 + Root" : "自定义广播");
    } else if (actionType === "ACTIVITY" && (activityUseRoot || shellUseRoot)) {
      riskReasons.push("Activity Root 启动");
    } else if (shellUseRoot) {
      riskReasons.push("Root 权限");
    }

    if (userId > 0) {
      riskReasons.push(`跨用户启动 (userId=${userId})`);
    }
    if (actionType === "INTENT_URI") {
      const intentRisk = inspectIntentUriRisk(actionPayload);
      if (intentRisk) {
        riskReasons.push(intentRisk);
      }
    }

    if (riskReasons.length > 0) {
      riskSummary.push({
        path: itemPath,
        title: displayTitle,
        actionType,
        reason: riskReasons.join("；"),
        shellUseRoot: shellUseRoot || activityUseRoot,
        userId,
        payload: actionPayload,
      });
    }

    return {
      id,
      title,
      subtitle,
      actionType,
      actionPayload,
      iconPackage,
      iconGlyph,
      tintColor,
      enabled,
      glyphPadEnabled,
      glyphPadColor,
      glyphPadAlpha,
      appIconCropMode,
      customOffsetX,
      customOffsetY,
      userId,
      shellUseRoot,
      followGlobalHintStyle,
      hintTextColor,
      hintTextAlpha,
      hintTextColorSub,
      hintTextAlphaSub,
      hintTextShadow,
      hintTextStroke,
      hintTextWeight,
      hintTextLetterSpacing,
      showHintTitle,
      showHintSubtitle,
    };
  }

  const normalizedItems = rawShortcuts.map((item) => normalizeOne(item, 0, ""));

  return {
    items: normalizedItems,
    shortcutsJson: JSON.stringify(normalizedItems),
    shortcutCount: normalizedItems.length,
    totalShortcutCount: totalCount,
    folderCount,
    actionTypes: Array.from(actionTypesSet),
    extractedImages,
    customImageCount: extractedImages.length,
    riskSummary,
    hasRootRisk: riskSummary.length > 0,
  };
}

export function validateAuthorDisplayName(rawName) {
  if (typeof rawName !== "string") {
    throw new ValidationError("作者昵称不能为空且必须是字符串");
  }
  if (rawName.length > 120) {
    throw new ValidationError("作者昵称长度超出限制");
  }
  if (/[\u0000-\u001F\u007F-\u009F]/.test(rawName)) {
    throw new ValidationError("作者昵称不能包含控制字符或换行符");
  }
  const displayName = rawName.trim();
  if (!displayName || displayName.length > 40) {
    throw new ValidationError("作者昵称不能为空且不能超过 40 个字符");
  }
  return {
    displayName,
    normalizedName: displayName.toLowerCase(),
  };
}

export function validateBadgeLabel(rawLabel) {
  if (rawLabel === undefined || rawLabel === null) {
    return "";
  }
  if (typeof rawLabel !== "string") {
    throw new ValidationError("徽章文案必须是字符串");
  }
  if (rawLabel.length > 80) {
    throw new ValidationError("徽章文案长度超出限制");
  }
  if (/[\u0000-\u001F\u007F-\u009F]/.test(rawLabel)) {
    throw new ValidationError("徽章文案不能包含控制字符或换行符");
  }
  const badgeLabel = rawLabel.trim();
  if (badgeLabel.length > 20) {
    throw new ValidationError("徽章文案不能超过 20 个字符");
  }
  return badgeLabel;
}

export function validateAndNormalizeConfig(rawInput) {
  let root = rawInput;
  if (typeof rawInput === "string") {
    if (!rawInput.trim()) {
      throw new ValidationError("配置内容不能为空");
    }
    try {
      root = JSON.parse(rawInput);
    } catch {
      throw new ValidationError("配置 JSON 解析失败");
    }
  }
  if (!isPlainObject(root)) {
    throw new ValidationError("配置根节点必须是 JSON 对象");
  }
  if (root.format !== "hyperopen-config") {
    throw new ValidationError("不是 HyperOpen 的配置文件（format 必须为 hyperopen-config）");
  }
  if (typeof root.version !== "number" || !Number.isInteger(root.version) || root.version <= 0 || root.version > 1) {
    throw new ValidationError("配置文件版本号不受支持（仅支持 version=1）");
  }
  if (!isPlainObject(root.values)) {
    throw new ValidationError("配置文件缺少有效的 values 对象");
  }

  const normalizedValues = {};
  let keyCount = 0;
  const rawKeys = Object.keys(root.values).sort();
  for (const key of rawKeys) {
    if (isSkippedKey(key) || key === "shortcuts_json") continue;
    if (!Object.prototype.hasOwnProperty.call(ALLOWED_KEY_TYPES, key)) {
      continue;
    }
    const validated = validateTypedPref(key, root.values[key]);
    if (validated) {
      normalizedValues[key] = validated;
      keyCount += 1;
    }
  }

  if (keyCount === 0) {
    throw new ValidationError("配置不包含任何有效的面板参数键");
  }

  // 配置市场纯外观化：强制剥离顶层 shortcuts 与 values.shortcuts_json
  const layoutStyle = normalizedValues.layout_style ? normalizedValues.layout_style.v : 0;
  const materialStyle = normalizedValues.material_style ? normalizedValues.material_style.v : 2;

  let appVersion = "";
  if (root.appVersion !== undefined && root.appVersion !== null) {
    if (typeof root.appVersion !== "string") {
      throw new ValidationError("appVersion 必须是字符串");
    }
    appVersion = root.appVersion.trim().slice(0, 32);
  }

  const canonical = {
    format: "hyperopen-config",
    version: 1,
    appVersion,
    values: normalizedValues,
  };

  const jsonBody = JSON.stringify(canonical);

  return {
    jsonBody,
    layoutStyle,
    materialStyle,
    keyCount,
    shortcutCount: 0,
    hasShortcuts: false,
    appVersion,
    extractedImages: [],
    customImageCount: 0,
    riskSummary: [],
    hasRootRisk: false,
    reviewReasons: [],
    status: "approved",
  };
}

export const validateConfigSubmission = validateAndNormalizeConfig;

export function validateShortcutEntryType(raw) {
  if (raw === undefined || raw === null || raw === "") {
    return "pack";
  }
  if (typeof raw !== "string") {
    throw new ValidationError("entryType 参数仅支持 single 或 pack", 400);
  }
  const normalized = raw.trim().toLowerCase();
  if (normalized !== "single" && normalized !== "pack") {
    throw new ValidationError("entryType 参数仅支持 single 或 pack", 400);
  }
  return normalized;
}

export function validateUploadMetadata(body, options = { allowEmptyAuthor: false, allowEmptyName: false }) {
  if (!isPlainObject(body)) {
    throw new ValidationError("请求体必须是 JSON 对象");
  }
  const allowEmptyName = Boolean(options?.allowEmptyName);
  const name = asString(body.name, "", "name", 120).trim();
  if (name.length > 60 || (!name && !allowEmptyName)) {
    throw new ValidationError("名称不能为空且不能超过 60 个字符");
  }
  const description = asString(body.description, "", "description", 1000).trim();
  if (description.length > 500) {
    throw new ValidationError("简介不能超过 500 个字符");
  }

  let author = "";
  let normalizedAuthor = "";
  const isAuthorEmpty =
    body.author === undefined ||
    body.author === null ||
    (typeof body.author === "string" && body.author.trim() === "");
  if (isAuthorEmpty && options.allowEmptyAuthor) {
    author = "";
    normalizedAuthor = "";
  } else {
    const parsedAuthor = validateAuthorDisplayName(body.author);
    author = parsedAuthor.displayName;
    normalizedAuthor = parsedAuthor.normalizedName;
  }

  const tags = [];
  const seenTag = new Set();
  if (body.tags !== undefined && body.tags !== null) {
    if (!Array.isArray(body.tags)) {
      throw new ValidationError("tags 必须是字符串数组");
    }
    if (body.tags.length > 8) {
      throw new ValidationError("标签数量不能超过 8 个");
    }
    for (const rawTag of body.tags) {
      if (typeof rawTag !== "string") {
        throw new ValidationError("标签项必须是字符串");
      }
      const tag = rawTag.trim();
      if (!tag) continue;
      if (tag.length > 20) {
        throw new ValidationError("单个标签长度不能超过 20 个字符");
      }
      if (!seenTag.has(tag)) {
        seenTag.add(tag);
        tags.push(tag);
      }
    }
  }

  let minAppVersionCode = 0;
  if (body.minAppVersionCode !== undefined && body.minAppVersionCode !== null) {
    assertFiniteNumber(body.minAppVersionCode, "minAppVersionCode");
    minAppVersionCode = clamp(Math.trunc(body.minAppVersionCode), 0, 999999);
  }

  let appVersion = "";
  if (body.appVersion !== undefined && body.appVersion !== null) {
    appVersion = asString(body.appVersion, "", "appVersion", 64).trim().slice(0, 32);
  }

  return {
    name,
    description,
    author,
    normalizedAuthor,
    tags,
    minAppVersionCode,
    appVersion,
  };
}

export function compactRiskSummaryForMetadata(riskSummary) {
  return riskSummary.map((r) => ({
    path: r.path,
    title: r.title,
    actionType: r.actionType,
    reason: r.reason,
    shellUseRoot: r.shellUseRoot,
    userId: r.userId,
    payload: r.payload.length > 300 ? `${r.payload.slice(0, 300)}...` : r.payload,
  }));
}
