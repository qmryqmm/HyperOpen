package io.github.qmryqmm.hyperopen.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.qmryqmm.hyperopen.factory.HyperOSMaterialFactory;
import io.github.qmryqmm.hyperopen.model.ShortcutItem;
import io.github.qmryqmm.hyperopen.util.DePrefs;

/** 配置管理页的持久化存储。每份配置以紧凑 JSON 原子化落盘，保证并发安全与快速迁移。 */
public final class ConfigProfileStore {

    private static final String TAG = "HyperOpen_ProfileStore";

    public static final String FORMAT = "hyperopen-config-profiles";
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_NAME_LENGTH = 32;

    private static final String STORE_FILE_NAME = "config_profiles.json";
    private static final String CORRUPT_BACKUP_NAME = "config_profiles.json.corrupt";

    private static final String LEGACY_PREF_NAME = "hyperopen_config_profiles";
    private static final String LEGACY_KEY_PROFILES = "profiles_json";
    private static final String LEGACY_KEY_ACTIVE_ID = "active_profile_id";

    private static final Object LOCK = new Object();

    private ConfigProfileStore() {
    }

    public static final class StoreState {
        public final List<ConfigProfile> profiles;
        public final String activeId;

        public StoreState(List<ConfigProfile> profiles, String activeId) {
            this.profiles = profiles;
            this.activeId = activeId == null ? "" : activeId;
        }
    }

    public static final class DuplicateResult {
        public final List<ConfigProfile> allProfiles;
        public final List<ConfigProfile> copies;

        DuplicateResult(List<ConfigProfile> allProfiles, List<ConfigProfile> copies) {
            this.allProfiles = allProfiles;
            this.copies = copies;
        }
    }

    public static final class DeleteResult {
        public final List<ConfigProfile> remainingProfiles;
        public final String activeId;
        public final int deletedCount;

        DeleteResult(List<ConfigProfile> remainingProfiles, String activeId, int deletedCount) {
            this.remainingProfiles = remainingProfiles;
            this.activeId = activeId;
            this.deletedCount = deletedCount;
        }
    }

    public static final class ProfileSyncStatus {
        public static final ProfileSyncStatus CLEAN = new ProfileSyncStatus(false, false, false);

        public final boolean paramsModified;
        public final boolean shortcutsModified;
        public final boolean profileHasShortcuts;

        public ProfileSyncStatus(boolean paramsModified, boolean shortcutsModified, boolean profileHasShortcuts) {
            this.paramsModified = paramsModified;
            this.shortcutsModified = shortcutsModified;
            this.profileHasShortcuts = profileHasShortcuts;
        }

        public boolean isModified() {
            return paramsModified || shortcutsModified;
        }
    }

    public static final class ImportCandidate {
        public final String name;
        public final String backupJson;
        public final int keyCount;
        public final int shortcutCount;
        public final boolean hasShortcuts;
        public final int layoutStyle;
        public final int materialStyle;
        public final boolean hasNameConflict;
        public final int skippedKeyCount;
        public final List<String> highRiskShortcuts;

        ImportCandidate(
                String name,
                String backupJson,
                int keyCount,
                int shortcutCount,
                boolean hasShortcuts,
                int layoutStyle,
                int materialStyle,
                boolean hasNameConflict
        ) {
            this(name, backupJson, keyCount, shortcutCount, hasShortcuts, layoutStyle, materialStyle,
                    hasNameConflict, 0, Collections.emptyList());
        }

        ImportCandidate(
                String name,
                String backupJson,
                int keyCount,
                int shortcutCount,
                boolean hasShortcuts,
                int layoutStyle,
                int materialStyle,
                boolean hasNameConflict,
                int skippedKeyCount,
                List<String> highRiskShortcuts
        ) {
            this.name = name;
            this.backupJson = backupJson;
            this.keyCount = keyCount;
            this.shortcutCount = shortcutCount;
            this.hasShortcuts = hasShortcuts;
            this.layoutStyle = layoutStyle;
            this.materialStyle = materialStyle;
            this.hasNameConflict = hasNameConflict;
            this.skippedKeyCount = skippedKeyCount;
            this.highRiskShortcuts = highRiskShortcuts == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(highRiskShortcuts));
        }

        public boolean hasHighRiskShortcuts() {
            return !highRiskShortcuts.isEmpty();
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
    }

    public static final class ImportPreview {
        public final boolean ok;
        public final String message;
        public final boolean isBundle;
        public final String sourceVersionOrTime;
        public final int skippedCorruptCount;
        public final List<ImportCandidate> candidates;

        ImportPreview(
                boolean ok,
                String message,
                boolean isBundle,
                String sourceVersionOrTime,
                int skippedCorruptCount,
                List<ImportCandidate> candidates
        ) {
            this.ok = ok;
            this.message = message;
            this.isBundle = isBundle;
            this.sourceVersionOrTime = sourceVersionOrTime == null ? "" : sourceVersionOrTime;
            this.skippedCorruptCount = skippedCorruptCount;
            this.candidates = candidates;
        }

        public boolean anyHasShortcuts() {
            for (ImportCandidate candidate : candidates) {
                if (candidate.hasShortcuts) return true;
            }
            return false;
        }

        public int totalSkippedKeyCount() {
            int total = 0;
            for (ImportCandidate candidate : candidates) {
                total += candidate.skippedKeyCount;
            }
            return total;
        }

        public boolean anyHighRiskShortcuts() {
            for (ImportCandidate candidate : candidates) {
                if (!candidate.highRiskShortcuts.isEmpty()) return true;
            }
            return false;
        }

        public List<String> collectHighRiskShortcuts() {
            List<String> out = new ArrayList<>();
            boolean prefixProfile = isBundle && candidates.size() > 1;
            for (ImportCandidate candidate : candidates) {
                for (String entry : candidate.highRiskShortcuts) {
                    out.add(prefixProfile ? ("[" + candidate.name + "] " + entry) : entry);
                }
            }
            return out;
        }

        public static ImportPreview fail(String message) {
            return new ImportPreview(false, message, false, "", 0, new ArrayList<>());
        }
    }

    public static final class ImportCommitResult {
        public final boolean ok;
        public final List<ConfigProfile> importedProfiles;
        public final List<ConfigProfile> allProfiles;
        public final int shortcutCount;
        public final int skippedCorruptCount;
        public final String message;

        ImportCommitResult(
                boolean ok,
                List<ConfigProfile> importedProfiles,
                List<ConfigProfile> allProfiles,
                int shortcutCount,
                int skippedCorruptCount,
                String message
        ) {
            this.ok = ok;
            this.importedProfiles = importedProfiles;
            this.allProfiles = allProfiles;
            this.shortcutCount = shortcutCount;
            this.skippedCorruptCount = skippedCorruptCount;
            this.message = message;
        }

        public static ImportCommitResult fail(String message) {
            return new ImportCommitResult(false, new ArrayList<>(), new ArrayList<>(), 0, 0, message);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 读取与生命周期操作
    // ──────────────────────────────────────────────────────────────

    public static StoreState loadState(Context ctx) {
        synchronized (LOCK) {
            return readStateLocked(ctx);
        }
    }

    public static List<ConfigProfile> load(Context ctx) {
        synchronized (LOCK) {
            return readStateLocked(ctx).profiles;
        }
    }

    public static String activeId(Context ctx) {
        synchronized (LOCK) {
            return readStateLocked(ctx).activeId;
        }
    }

    public static void setActive(Context ctx, String id) {
        synchronized (LOCK) {
            StoreState state = readStateLocked(ctx);
            String candidate = id == null ? "" : id.trim();
            if (!candidate.isEmpty() && !containsId(state.profiles, candidate)) {
                candidate = "";
            }
            writeStateLocked(ctx, state.profiles, candidate);
        }
    }

    public static ConfigProfile createFromCurrent(Context ctx, String name) throws Exception {
        long now = System.currentTimeMillis();
        String backupJson = ConfigBackup.buildExportJson(ctx, true, false);
        ConfigBackup.Result preview = ConfigBackup.preview(backupJson);
        if (!preview.ok) {
            throw new IllegalStateException(preview.message);
        }
        return new ConfigProfile(
                UUID.randomUUID().toString(),
                sanitizeName(name, "未命名配置"),
                now,
                now,
                backupJson,
                preview.keyCount,
                preview.shortcutCount,
                preview.hasShortcuts,
                preview.layoutStyle,
                preview.materialStyle
        );
    }

    public static StoreState add(Context ctx, ConfigProfile profile, boolean setAsActive) {
        synchronized (LOCK) {
            StoreState state = readStateLocked(ctx);
            List<ConfigProfile> profiles = new ArrayList<>(state.profiles);
            profiles.add(profile);
            String active = setAsActive ? profile.id : state.activeId;
            writeStateLocked(ctx, profiles, active);
            return new StoreState(profiles, active);
        }
    }

    public static DuplicateResult duplicate(Context ctx, Set<String> ids) {
        synchronized (LOCK) {
            StoreState state = readStateLocked(ctx);
            List<ConfigProfile> updated = new ArrayList<>(state.profiles.size() + ids.size());
            List<ConfigProfile> copies = new ArrayList<>();
            Set<String> usedNames = collectNames(state.profiles);
            long now = System.currentTimeMillis();
            for (ConfigProfile profile : state.profiles) {
                updated.add(profile);
                if (!ids.contains(profile.id)) continue;
                String copyName = makeCopyName(profile.name, usedNames);
                usedNames.add(copyName);
                ConfigProfile copy = new ConfigProfile(
                        UUID.randomUUID().toString(),
                        copyName,
                        now,
                        now,
                        profile.backupJson,
                        profile.keyCount,
                        profile.shortcutCount,
                        profile.hasShortcuts,
                        profile.layoutStyle,
                        profile.materialStyle
                );
                updated.add(copy);
                copies.add(copy);
            }
            writeStateLocked(ctx, updated, state.activeId);
            return new DuplicateResult(updated, copies);
        }
    }

    public static ConfigProfile rename(Context ctx, String id, String name) {
        String cleanName = sanitizeName(name, null);
        if (cleanName == null) return null;
        synchronized (LOCK) {
            StoreState state = readStateLocked(ctx);
            List<ConfigProfile> profiles = new ArrayList<>(state.profiles);
            long now = System.currentTimeMillis();
            for (int i = 0; i < profiles.size(); i++) {
                ConfigProfile profile = profiles.get(i);
                if (profile.id.equals(id)) {
                    ConfigProfile renamed = profile.renamed(cleanName, now);
                    profiles.set(i, renamed);
                    writeStateLocked(ctx, profiles, state.activeId);
                    return renamed;
                }
            }
            return null;
        }
    }

    /** 将当前生效参数和/或快捷项目同步覆盖到指定配置。 */
    public static ConfigProfile syncFromCurrent(
            Context ctx,
            String id,
            boolean syncParams,
            boolean syncShortcuts
    ) throws Exception {
        return syncFromCurrent(ctx, id, syncParams, syncShortcuts, false);
    }

    /**
     * 将当前生效参数和/或快捷项目同步覆盖到指定配置。
     *
     * @param preserveSoftGlassMetadata 当目标配置原为柔光玻璃且当前设备不支持柔光玻璃时，
     *                                  同步参数时保留原配置的 material_style 与 glass_* 参数。
     */
    public static ConfigProfile syncFromCurrent(
            Context ctx,
            String id,
            boolean syncParams,
            boolean syncShortcuts,
            boolean preserveSoftGlassMetadata
    ) throws Exception {
        if (!syncParams && !syncShortcuts) return null;
        synchronized (LOCK) {
            StoreState state = readStateLocked(ctx);
            List<ConfigProfile> profiles = new ArrayList<>(state.profiles);
            for (int i = 0; i < profiles.size(); i++) {
                ConfigProfile profile = profiles.get(i);
                if (!profile.id.equals(id)) continue;

                String backupJson;
                if (syncParams) {
                    String currentJson = ConfigBackup.buildExportJson(ctx, syncShortcuts, false);
                    JSONObject currentRoot = new JSONObject(currentJson);
                    JSONObject previousRoot = new JSONObject(profile.backupJson);
                    if (!syncShortcuts) {
                        JSONArray previousShortcuts = previousRoot.optJSONArray("shortcuts");
                        if (previousShortcuts != null) {
                            currentRoot.put("shortcuts", previousShortcuts);
                        }
                        JSONObject previousValues = previousRoot.optJSONObject("values");
                        JSONObject currentValues = currentRoot.optJSONObject("values");
                        if (previousValues != null && currentValues != null) {
                            JSONObject legacyShortcuts = previousValues.optJSONObject(ConfigBackup.KEY_SHORTCUTS);
                            if (legacyShortcuts != null) {
                                currentValues.put(ConfigBackup.KEY_SHORTCUTS, legacyShortcuts);
                            }
                        }
                    }
                    if (preserveSoftGlassMetadata
                            && !HyperOSMaterialFactory.isSoftGlassSupported()
                            && profile.materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS) {
                        preserveSoftGlassValues(
                                previousRoot.optJSONObject("values"),
                                currentRoot.optJSONObject("values")
                        );
                    }
                    backupJson = currentRoot.toString();
                } else {
                    JSONObject previousRoot = new JSONObject(profile.backupJson);
                    SharedPreferences sp = DePrefs.get(ctx, HyperOpenConfig.PREF_NAME);
                    List<ShortcutItem> items = HyperOpenConfig.parseShortcutsJson(
                            sp.getString(ConfigBackup.KEY_SHORTCUTS, null)
                    );
                    JSONArray shortcutsArr = new JSONArray();
                    for (ShortcutItem item : items) {
                        shortcutsArr.put(item.toJson());
                    }
                    previousRoot.put("shortcuts", shortcutsArr);
                    JSONObject prevValues = previousRoot.optJSONObject("values");
                    if (prevValues != null) {
                        prevValues.remove(ConfigBackup.KEY_SHORTCUTS);
                    }
                    backupJson = previousRoot.toString();
                }

                ConfigBackup.Result preview = ConfigBackup.preview(backupJson);
                if (!preview.ok) {
                    throw new IllegalStateException(preview.message);
                }
                ConfigProfile synced = new ConfigProfile(
                        profile.id,
                        profile.name,
                        profile.createdAt,
                        System.currentTimeMillis(),
                        backupJson,
                        preview.keyCount,
                        preview.shortcutCount,
                        preview.hasShortcuts,
                        preview.layoutStyle,
                        preview.materialStyle
                );
                profiles.set(i, synced);
                writeStateLocked(ctx, profiles, state.activeId);
                return synced;
            }
            return null;
        }
    }

    private static boolean isSoftGlassMetadataKey(String key) {
        return "material_style".equals(key)
                || key.startsWith("glass_")
                || "bubble_glass_matte_alpha".equals(key)
                || "region_glass_matte_alpha".equals(key)
                || "header_glass_matte_alpha".equals(key);
    }

    private static void preserveSoftGlassValues(JSONObject prevValues, JSONObject currValues)
            throws Exception {
        if (prevValues == null || currValues == null) return;
        List<String> keysToClear = new ArrayList<>();
        for (Iterator<String> it = currValues.keys(); it.hasNext(); ) {
            String k = it.next();
            if (isSoftGlassMetadataKey(k)) {
                keysToClear.add(k);
            }
        }
        for (String k : keysToClear) {
            currValues.remove(k);
        }
        for (Iterator<String> it = prevValues.keys(); it.hasNext(); ) {
            String k = it.next();
            if (isSoftGlassMetadataKey(k)) {
                currValues.put(k, prevValues.get(k));
            }
        }
    }

    /** 检查当前生效配置相对指定激活配置是否发生了修改（参数 / 快捷项目分别判定）。 */
    public static ProfileSyncStatus inspectSyncStatus(Context ctx, ConfigProfile activeProfile) {
        if (ctx == null || activeProfile == null) {
            return ProfileSyncStatus.CLEAN;
        }
        boolean paramsModified = !ConfigBackup.areCurrentParamsMatching(ctx, activeProfile.backupJson);
        boolean shortcutsModified = activeProfile.hasShortcuts
                && !ConfigBackup.areCurrentShortcutsMatching(ctx, activeProfile.backupJson);
        return new ProfileSyncStatus(paramsModified, shortcutsModified, activeProfile.hasShortcuts);
    }

    public static void reorder(Context ctx, List<String> orderedIds) {
        synchronized (LOCK) {
            StoreState state = readStateLocked(ctx);
            Map<String, ConfigProfile> byId = new LinkedHashMap<>();
            for (ConfigProfile profile : state.profiles) {
                byId.put(profile.id, profile);
            }
            List<ConfigProfile> reordered = new ArrayList<>(state.profiles.size());
            if (orderedIds != null) {
                for (String id : orderedIds) {
                    ConfigProfile profile = byId.remove(id);
                    if (profile != null) reordered.add(profile);
                }
            }
            reordered.addAll(byId.values());
            writeStateLocked(ctx, reordered, state.activeId);
        }
    }

    public static DeleteResult delete(Context ctx, Set<String> ids) {
        synchronized (LOCK) {
            StoreState state = readStateLocked(ctx);
            List<ConfigProfile> remaining = new ArrayList<>(state.profiles.size());
            int removed = 0;
            for (ConfigProfile profile : state.profiles) {
                if (ids.contains(profile.id)) {
                    removed++;
                } else {
                    remaining.add(profile);
                }
            }
            String nextActive = ids.contains(state.activeId) ? "" : state.activeId;
            writeStateLocked(ctx, remaining, nextActive);
            return new DeleteResult(remaining, nextActive, removed);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 导入 / 导出
    // ──────────────────────────────────────────────────────────────

    public static String buildExportJson(
            List<ConfigProfile> profiles,
            boolean includeShortcuts
    ) throws Exception {
        if (profiles == null || profiles.isEmpty()) {
            throw new IllegalArgumentException("没有可导出的配置");
        }
        JSONArray items = new JSONArray();
        int shortcutCount = 0;
        for (ConfigProfile profile : profiles) {
            JSONObject item = new JSONObject();
            item.put("name", profile.name);
            item.put("createdAt", profile.createdAt);
            item.put("updatedAt", profile.updatedAt);
            JSONObject config = new JSONObject(profile.backupJson);
            if (!includeShortcuts) {
                config.remove("shortcuts");
                JSONObject values = config.optJSONObject("values");
                if (values != null) values.remove(ConfigBackup.KEY_SHORTCUTS);
            } else if (profile.hasShortcuts) {
                shortcutCount += profile.shortcutCount;
            }
            item.put("config", config);
            items.put(item);
        }

        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("version", FORMAT_VERSION);
        root.put("exportedAt", System.currentTimeMillis());
        root.put("exportedAtText", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        root.put("includeShortcuts", includeShortcuts);
        root.put("profileCount", items.length());
        root.put("shortcutCount", shortcutCount);
        root.put("profiles", items);
        return root.toString(2);
    }

    /** 第一阶段：读取并预检导入文件，支持单份配置或配置包，并跳过配置包内个别损坏条目。 */
    public static ImportPreview previewImport(
            Context ctx,
            String json,
            String suggestedSingleName
    ) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return ImportPreview.fail("所选文件内容为空");
            }
            Set<String> existingNames;
            synchronized (LOCK) {
                existingNames = collectNames(readStateLocked(ctx).profiles);
            }

            JSONObject root = new JSONObject(json);
            String format = optCleanString(root, "format");
            List<ImportCandidate> candidates = new ArrayList<>();
            int skippedCorrupt = 0;

            if (FORMAT.equals(format)) {
                int version = root.optInt("version", 0);
                if (version <= 0 || version > FORMAT_VERSION) {
                    return ImportPreview.fail("配置包版本不受支持");
                }
                JSONArray items = root.optJSONArray("profiles");
                if (items == null) {
                    return ImportPreview.fail("配置包缺少 profiles 字段");
                }
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) {
                        skippedCorrupt++;
                        continue;
                    }
                    JSONObject config = item.optJSONObject("config");
                    if (config == null) {
                        skippedCorrupt++;
                        continue;
                    }
                    String configJson = config.toString();
                    ConfigBackup.Result preview = ConfigBackup.preview(configJson);
                    if (!preview.ok) {
                        skippedCorrupt++;
                        continue;
                    }
                    String name = sanitizeName(optCleanString(item, "name"), "导入的配置");
                    candidates.add(new ImportCandidate(
                            name,
                            configJson,
                            preview.keyCount,
                            preview.shortcutCount,
                            preview.hasShortcuts,
                            preview.layoutStyle,
                            preview.materialStyle,
                            existingNames.contains(name),
                            preview.skippedKeyCount,
                            preview.highRiskShortcuts
                    ));
                }
                if (candidates.isEmpty()) {
                    return ImportPreview.fail("文件中没有可导入的有效配置");
                }
                String sourceInfo = optCleanString(root, "exportedAtText");
                return new ImportPreview(true, "解析成功", true, sourceInfo, skippedCorrupt, candidates);
            } else if (ConfigBackup.FORMAT.equals(format)) {
                ConfigBackup.Result preview = ConfigBackup.preview(json);
                if (!preview.ok) {
                    return ImportPreview.fail(preview.message);
                }
                String defaultName = sanitizeName(suggestedSingleName, "导入的配置");
                String normalizedJson = root.toString();
                candidates.add(new ImportCandidate(
                        defaultName,
                        normalizedJson,
                        preview.keyCount,
                        preview.shortcutCount,
                        preview.hasShortcuts,
                        preview.layoutStyle,
                        preview.materialStyle,
                        existingNames.contains(defaultName),
                        preview.skippedKeyCount,
                        preview.highRiskShortcuts
                ));
                return new ImportPreview(true, "解析成功", false, preview.appVersion, 0, candidates);
            } else {
                return ImportPreview.fail("不是 HyperOpen 配置或配置包");
            }
        } catch (Throwable t) {
            return ImportPreview.fail("解析失败：" + t.getMessage());
        }
    }

    /** 第二阶段：将预览确认后的配置写入存储，自动处理重名并按需剔除快捷项目。 */
    public static ImportCommitResult commitImport(
            Context ctx,
            ImportPreview preview,
            String singleNameOverride,
            boolean includeShortcuts
    ) {
        if (preview == null || !preview.ok || preview.candidates.isEmpty()) {
            return ImportCommitResult.fail("没有可导入的配置");
        }
        try {
            synchronized (LOCK) {
                StoreState state = readStateLocked(ctx);
                List<ConfigProfile> profiles = new ArrayList<>(state.profiles);
                Set<String> usedNames = collectNames(profiles);
                List<ConfigProfile> imported = new ArrayList<>();
                int shortcutCount = 0;
                long now = System.currentTimeMillis();

                for (int i = 0; i < preview.candidates.size(); i++) {
                    ImportCandidate candidate = preview.candidates.get(i);
                    String rawName = (!preview.isBundle && i == 0 && singleNameOverride != null && !singleNameOverride.trim().isEmpty())
                            ? singleNameOverride
                            : candidate.name;
                    String uniqueName = makeUniqueImportName(sanitizeName(rawName, "导入的配置"), usedNames);
                    usedNames.add(uniqueName);

                    JSONObject config = new JSONObject(candidate.backupJson);
                    if (!includeShortcuts) {
                        config.remove("shortcuts");
                        JSONObject values = config.optJSONObject("values");
                        if (values != null) values.remove(ConfigBackup.KEY_SHORTCUTS);
                    }
                    String backupJson = config.toString();
                    ConfigBackup.Result checked = ConfigBackup.preview(backupJson);
                    if (!checked.ok) {
                        throw new IllegalStateException(checked.message);
                    }
                    ConfigProfile profile = new ConfigProfile(
                            UUID.randomUUID().toString(),
                            uniqueName,
                            now,
                            now,
                            backupJson,
                            checked.keyCount,
                            checked.shortcutCount,
                            checked.hasShortcuts,
                            checked.layoutStyle,
                            checked.materialStyle
                    );
                    profiles.add(profile);
                    imported.add(profile);
                    if (checked.hasShortcuts) {
                        shortcutCount += checked.shortcutCount;
                    }
                }

                writeStateLocked(ctx, profiles, state.activeId);
                return new ImportCommitResult(
                        true,
                        imported,
                        profiles,
                        shortcutCount,
                        preview.skippedCorruptCount,
                        "导入完成"
                );
            }
        } catch (Throwable t) {
            return ImportCommitResult.fail("导入失败：" + t.getMessage());
        }
    }

    public static String suggestedFileName(List<ConfigProfile> targets) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
        if (targets != null && targets.size() == 1) {
            String safeName = sanitizeFileSegment(targets.get(0).name);
            if (!safeName.isEmpty()) {
                return "hyperopen-" + safeName + "-" + stamp + ".json";
            }
        }
        return "hyperopen-profiles-" + stamp + ".json";
    }

    // ──────────────────────────────────────────────────────────────
    // 内部存储读写与迁移
    // ──────────────────────────────────────────────────────────────

    private static File storeFile(Context ctx) {
        return new File(ctx.getFilesDir(), STORE_FILE_NAME);
    }

    private static StoreState readStateLocked(Context ctx) {
        File file = storeFile(ctx);
        AtomicFile atomic = new AtomicFile(file);
        try (FileInputStream fis = atomic.openRead()) {
            String raw = readAllUtf8(fis);
            if (raw.trim().isEmpty()) {
                return new StoreState(new ArrayList<>(), "");
            }
            JSONObject root = new JSONObject(raw);
            JSONArray array = root.optJSONArray("profiles");
            List<ConfigProfile> profiles = parseProfilesArray(array);
            String activeId = optCleanString(root, "activeId");
            if (!activeId.isEmpty() && !containsId(profiles, activeId)) {
                activeId = "";
            }
            return new StoreState(profiles, activeId);
        } catch (FileNotFoundException missing) {
            return migrateFromLegacyPrefsLocked(ctx);
        } catch (Throwable t) {
            Log.e(TAG, "读取配置库文件失败，保留损坏备份以避免静默丢失", t);
            backupCorruptFile(file);
            return new StoreState(new ArrayList<>(), "");
        }
    }

    private static StoreState migrateFromLegacyPrefsLocked(Context ctx) {
        try {
            SharedPreferences legacy = DePrefs.get(ctx, LEGACY_PREF_NAME);
            String raw = legacy.getString(LEGACY_KEY_PROFILES, "");
            String legacyActive = legacy.getString(LEGACY_KEY_ACTIVE_ID, "");
            if ((raw == null || raw.trim().isEmpty()) && (legacyActive == null || legacyActive.trim().isEmpty())) {
                return new StoreState(new ArrayList<>(), "");
            }
            JSONArray array = raw != null && !raw.trim().isEmpty() ? new JSONArray(raw) : new JSONArray();
            List<ConfigProfile> profiles = parseProfilesArray(array);
            String activeId = legacyActive == null ? "" : legacyActive.trim();
            if (!activeId.isEmpty() && !containsId(profiles, activeId)) {
                activeId = "";
            }
            if (!profiles.isEmpty() || !activeId.isEmpty()) {
                writeStateLocked(ctx, profiles, activeId);
                Log.i(TAG, "已从旧版 prefs 迁移配置库: " + profiles.size() + " 份");
            }
            return new StoreState(profiles, activeId);
        } catch (Throwable t) {
            Log.w(TAG, "迁移旧版配置库失败", t);
            return new StoreState(new ArrayList<>(), "");
        }
    }

    private static List<ConfigProfile> parseProfilesArray(JSONArray array) {
        List<ConfigProfile> result = new ArrayList<>();
        if (array == null) return result;
        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            try {
                ConfigProfile profile = ConfigProfile.fromJson(item);
                if (!seenIds.add(profile.id)) {
                    profile = new ConfigProfile(
                            UUID.randomUUID().toString(),
                            profile.name,
                            profile.createdAt,
                            profile.updatedAt,
                            profile.backupJson,
                            profile.keyCount,
                            profile.shortcutCount,
                            profile.hasShortcuts,
                            profile.layoutStyle,
                            profile.materialStyle
                    );
                    seenIds.add(profile.id);
                }
                result.add(profile);
            } catch (Throwable t) {
                Log.w(TAG, "跳过损坏的配置条目 index=" + i, t);
            }
        }
        return result;
    }

    private static void writeStateLocked(Context ctx, List<ConfigProfile> profiles, String activeId) {
        File file = storeFile(ctx);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream fos = null;
        try {
            JSONArray array = new JSONArray();
            for (ConfigProfile profile : profiles) {
                try {
                    array.put(profile.toJson());
                } catch (Throwable t) {
                    Log.w(TAG, "序列化配置失败，已跳过 id=" + profile.id, t);
                }
            }
            JSONObject root = new JSONObject();
            root.put("version", FORMAT_VERSION);
            root.put("activeId", activeId == null ? "" : activeId);
            root.put("profiles", array);

            fos = atomic.startWrite();
            fos.write(root.toString().getBytes(StandardCharsets.UTF_8));
            fos.flush();
            atomic.finishWrite(fos);
        } catch (Throwable t) {
            if (fos != null) {
                atomic.failWrite(fos);
            }
            throw new IllegalStateException("写入配置库失败：" + t.getMessage(), t);
        }
    }

    private static void backupCorruptFile(File file) {
        try {
            if (file.exists()) {
                File backup = new File(file.getParentFile(), CORRUPT_BACKUP_NAME);
                if (backup.exists()) backup.delete();
                file.renameTo(backup);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String readAllUtf8(FileInputStream fis) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    private static boolean containsId(List<ConfigProfile> profiles, String id) {
        for (ConfigProfile profile : profiles) {
            if (profile.id.equals(id)) return true;
        }
        return false;
    }

    private static Set<String> collectNames(List<ConfigProfile> profiles) {
        Set<String> names = new HashSet<>();
        for (ConfigProfile profile : profiles) {
            names.add(profile.name);
        }
        return names;
    }

    private static String makeCopyName(String originalName, Set<String> usedNames) {
        String clean = sanitizeName(originalName, "配置");
        String base = clean.replaceFirst("(?: 副本(?: \\d+)?)+$", "").trim();
        if (base.isEmpty()) base = clean;
        String first = clampWithSuffix(base, " 副本");
        if (!usedNames.contains(first)) return first;
        int index = 2;
        while (true) {
            String candidate = clampWithSuffix(base, " 副本 " + index);
            if (!usedNames.contains(candidate)) return candidate;
            index++;
        }
    }

    private static String makeUniqueImportName(String baseName, Set<String> usedNames) {
        if (!usedNames.contains(baseName)) return baseName;
        int index = 2;
        while (true) {
            String candidate = clampWithSuffix(baseName, " (" + index + ")");
            if (!usedNames.contains(candidate)) return candidate;
            index++;
        }
    }

    private static String clampWithSuffix(String base, String suffix) {
        int maxBase = Math.max(1, MAX_NAME_LENGTH - suffix.length());
        String trimmed = base.length() > maxBase ? base.substring(0, maxBase).trim() : base;
        if (trimmed.isEmpty()) trimmed = "配置";
        return trimmed + suffix;
    }

    private static String sanitizeName(String raw, String fallback) {
        if (raw == null) return fallback;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return fallback;
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH).trim();
        }
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String sanitizeFileSegment(String name) {
        if (name == null) return "";
        String cleaned = name.trim()
                .replaceAll("[\\\\/:*?\"<>|\\s]+", "_")
                .replaceAll("^_+|_+$", "");
        if (cleaned.length() > 24) {
            cleaned = cleaned.substring(0, 24).replaceAll("_+$", "");
        }
        return cleaned;
    }

    private static String optCleanString(JSONObject obj, String key) {
        if (obj == null || obj.isNull(key)) return "";
        return obj.optString(key, "").trim();
    }
}
