package io.github.qmryqmm.hyperopen.ui

import androidx.compose.ui.graphics.vector.ImageVector
import io.github.qmryqmm.hyperopen.ui.icons.glass.MiuixGlassIconRegistry
import top.yukonga.miuix.kmp.icon.MiuixIcons

/**
 * Miuix 全量扩展图标库注册表（涵盖 125+ 常用官方矢量图标），
 * 并支持动态切换 5 档粗细（Light / Normal / Regular / Medium / Demibold）。
 */
object MiuixIconRegistry {

    private const val GLASS_PREFIX = "glass:"

    val WEIGHTS = listOf("Light", "Normal", "Regular", "Medium", "Demibold")

    val ICON_NAMES: List<String> = listOf(
        "Add", "AddCircle", "AddFolder", "Alarm", "Album", "All", "Answer", "AppRecording",
        "Back", "Background", "Backup", "BankCards", "Blocklist", "CallRecording", "Carrier",
        "ChevronBackward", "ChevronForward", "Clear", "Close", "Close2", "CloudFill", "Community",
        "Contacts", "ContactsBook", "ContactsCircle", "ConvertFile", "Copy", "Create", "Cut",
        "Delete", "Download", "Edit", "Email", "ExpandLess", "ExpandMore", "Favorites",
        "FavoritesFill", "File", "FileDownloads", "Filter", "Folder", "FolderFill", "Forward",
        "GridView", "Help", "Hide", "Home", "HorizontalSplit", "Image", "Import", "Info",
        "Layers", "Link", "ListView", "Location", "Lock", "MapAlbum", "Merge", "Messages",
        "Mic", "MicSlash", "MindMap", "Months", "More", "MoreCircle", "MoveFile", "Music",
        "Notes", "NotesFill", "Ok", "Paste", "Pause", "Phone", "Photos", "Pin", "Play",
        "Playlist", "Promotions", "Recent", "Recording", "RecordingTape", "Redo", "Refresh",
        "Remove", "RemoveContact", "Rename", "Replace", "Reply", "ReplyAll", "Report", "Reset",
        "RotateLeft", "Scan", "ScreenCapture", "ScreenMirroring", "Search", "SearchDevice",
        "SelectAll", "Send", "Settings", "Share", "Show", "Sidebar", "Sort", "Stopwatch",
        "Store", "Tasks", "Theme", "Timer", "TopDownloads", "Translate", "Trim", "Tune",
        "Undo", "Unlock", "Unpin", "Update", "UploadCloud", "VerticalSplit", "VolumeOff",
        "VolumeUp", "Weeks", "WorldClock", "Years", "ZoomOut"
    )

    private val cache = HashMap<String, ImageVector?>()

    /**
     * 获取指定名称和字重的 Miuix 矢量图标。
     * 支持 "Scan" 或 "Scan:Medium" 这种带粗细后缀的 key。
     */
    fun find(key: String?, weightOverride: String? = null): ImageVector? {
        if (key.isNullOrBlank()) return null
        val cleanKey = key.trim()

        if (isGlassKey(cleanKey)) {
            return MiuixGlassIconRegistry.find(cleanKey.substring(GLASS_PREFIX.length), weightOverride)
        }

        val rawName: String
        val rawWeight: String
        if (cleanKey.contains(":")) {
            val parts = cleanKey.split(":")
            rawName = parts[0].trim()
            rawWeight = weightOverride ?: parts.getOrElse(1) { "Regular" }.trim()
        } else {
            rawName = cleanKey
            rawWeight = weightOverride ?: "Regular"
        }

        val matchedName = ICON_NAMES.firstOrNull { it.equals(rawName, ignoreCase = true) } ?: return null
        val targetWeight = WEIGHTS.firstOrNull { it.equals(rawWeight, ignoreCase = true) } ?: "Regular"
        val cacheKey = "$matchedName:$targetWeight"

        if (cache.containsKey(cacheKey)) {
            return cache[cacheKey]
        }

        val vector = try {
            val clazz = Class.forName("top.yukonga.miuix.kmp.icon.extended.${matchedName}Kt")
            val method = when (targetWeight.lowercase()) {
                "light" -> clazz.getMethod("get$matchedName", MiuixIcons.Light::class.java).invoke(null, MiuixIcons.Light)
                "normal" -> clazz.getMethod("get$matchedName", MiuixIcons.Normal::class.java).invoke(null, MiuixIcons.Normal)
                "medium" -> clazz.getMethod("get$matchedName", MiuixIcons.Medium::class.java).invoke(null, MiuixIcons.Medium)
                "demibold" -> clazz.getMethod("get$matchedName", MiuixIcons.Demibold::class.java).invoke(null, MiuixIcons.Demibold)
                else -> clazz.getMethod("get$matchedName", MiuixIcons.Regular::class.java).invoke(null, MiuixIcons.Regular)
            }
            method as? ImageVector
        } catch (t: Throwable) {
            null
        }

        cache[cacheKey] = vector
        return vector
    }

    fun isGlassKey(key: String?): Boolean {
        return key?.trim()?.startsWith(GLASS_PREFIX, ignoreCase = true) == true
    }

    fun iconNameFromKey(key: String?): String {
        val cleanKey = key?.trim().orEmpty()
        val iconKey = if (isGlassKey(cleanKey)) {
            cleanKey.substring(GLASS_PREFIX.length)
        } else {
            cleanKey
        }
        return iconKey.split(":").firstOrNull()?.trim().orEmpty()
    }

    fun iconWeightFromKey(key: String?): String {
        val cleanKey = key?.trim().orEmpty()
        val iconKey = if (isGlassKey(cleanKey)) {
            cleanKey.substring(GLASS_PREFIX.length)
        } else {
            cleanKey
        }
        return iconKey.split(":")
            .getOrNull(1)
            ?.trim()
            ?.takeIf { candidate -> WEIGHTS.any { it.equals(candidate, ignoreCase = true) } }
            ?: "Regular"
    }
}
