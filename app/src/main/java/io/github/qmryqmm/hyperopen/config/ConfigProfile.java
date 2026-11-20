package io.github.qmryqmm.hyperopen.config;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.qmryqmm.hyperopen.model.ShortcutItem;

/** 一份可独立保存、复制、导入和应用的 HyperOpen 配置。 */
public final class ConfigProfile {

    public final String id;
    public final String name;
    public final long createdAt;
    public final long updatedAt;
    public final String backupJson;
    public final int keyCount;
    public final int shortcutCount;
    public final boolean hasShortcuts;
    public final int layoutStyle;
    public final int materialStyle;

    public ConfigProfile(
            String id,
            String name,
            long createdAt,
            long updatedAt,
            String backupJson,
            int keyCount,
            int shortcutCount,
            boolean hasShortcuts,
            int layoutStyle,
            int materialStyle
    ) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.backupJson = backupJson;
        this.keyCount = keyCount;
        this.shortcutCount = shortcutCount;
        this.hasShortcuts = hasShortcuts;
        this.layoutStyle = layoutStyle;
        this.materialStyle = materialStyle;
    }

    public ConfigProfile renamed(String newName, long time) {
        return new ConfigProfile(
                id,
                newName,
                createdAt,
                time,
                backupJson,
                keyCount,
                shortcutCount,
                hasShortcuts,
                layoutStyle,
                materialStyle
        );
    }

    public String layoutLabel() {
        switch (layoutStyle) {
            case HyperOpenConfig.LAYOUT_RING:
                return "空心圆环";
            case HyperOpenConfig.LAYOUT_ARC:
                return "弧形扇面";
            case HyperOpenConfig.LAYOUT_SYSTEM_STOCK:
                return "系统经典";
            case HyperOpenConfig.LAYOUT_HONEYCOMB:
            default:
                return "蜂窝";
        }
    }

    public String materialLabel() {
        return materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS ? "柔光玻璃" : "高斯模糊";
    }

    public List<ShortcutItem> parseShortcuts() {
        List<ShortcutItem> parsed = ConfigBackup.parseShortcutsFromBackup(backupJson);
        return parsed != null ? parsed : new ArrayList<>();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("id", id);
        root.put("name", name);
        root.put("createdAt", createdAt);
        root.put("updatedAt", updatedAt);
        root.put("keyCount", keyCount);
        root.put("shortcutCount", shortcutCount);
        root.put("hasShortcuts", hasShortcuts);
        root.put("layoutStyle", layoutStyle);
        root.put("materialStyle", materialStyle);
        root.put("config", new JSONObject(backupJson));
        return root;
    }

    public static ConfigProfile fromJson(JSONObject root) throws JSONException {
        if (root == null) {
            throw new JSONException("配置对象为空");
        }
        JSONObject config = root.optJSONObject("config");
        String backupJson = config != null ? config.toString() : optCleanString(root, "backupJson");
        if (backupJson.isEmpty()) {
            throw new JSONException("配置缺少 config 内容");
        }

        boolean needsPreview = !root.has("keyCount")
                || !root.has("shortcutCount")
                || !root.has("hasShortcuts")
                || !root.has("layoutStyle")
                || !root.has("materialStyle");
        ConfigBackup.Result preview = needsPreview ? ConfigBackup.preview(backupJson) : null;
        if (preview != null && !preview.ok) {
            throw new JSONException(preview.message);
        }

        long now = System.currentTimeMillis();
        String id = optCleanString(root, "id");
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        String name = optCleanString(root, "name");
        if (name.isEmpty()) {
            name = "导入的配置";
        }
        long createdAt = root.optLong("createdAt", now);
        long updatedAt = root.optLong("updatedAt", createdAt);

        int keyCount = root.has("keyCount")
                ? root.optInt("keyCount", 0)
                : preview.keyCount;
        int shortcutCount = root.has("shortcutCount")
                ? root.optInt("shortcutCount", 0)
                : preview.shortcutCount;
        boolean hasShortcuts = root.has("hasShortcuts")
                ? root.optBoolean("hasShortcuts", shortcutCount > 0)
                : preview.hasShortcuts;
        int layoutStyle = root.has("layoutStyle")
                ? root.optInt("layoutStyle", HyperOpenConfig.LAYOUT_HONEYCOMB)
                : preview.layoutStyle;
        int materialStyle = root.has("materialStyle")
                ? root.optInt("materialStyle", HyperOpenConfig.MATERIAL_GAUSSIAN_BLUR)
                : preview.materialStyle;

        return new ConfigProfile(
                id,
                name,
                createdAt,
                updatedAt,
                backupJson,
                keyCount,
                shortcutCount,
                hasShortcuts,
                layoutStyle,
                materialStyle
        );
    }

    private static String optCleanString(JSONObject obj, String key) {
        if (obj == null || obj.isNull(key)) {
            return "";
        }
        return obj.optString(key, "").trim();
    }
}
