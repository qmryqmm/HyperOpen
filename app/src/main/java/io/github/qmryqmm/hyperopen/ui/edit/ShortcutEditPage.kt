package io.github.qmryqmm.hyperopen.ui.edit

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import io.github.qmryqmm.hyperopen.model.ActivityLaunchSpec
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import io.github.qmryqmm.hyperopen.ui.IconPainter
import io.github.qmryqmm.hyperopen.ui.MiuixIconRegistry
import io.github.qmryqmm.hyperopen.ui.RemixIconRegistry
import io.github.qmryqmm.hyperopen.ui.components.CollapsibleSectionTitle
import io.github.qmryqmm.hyperopen.ui.components.ShortcutBubbleIcon
import io.github.qmryqmm.hyperopen.ui.components.ShortcutCard
import io.github.qmryqmm.hyperopen.ui.components.ShortcutListInteractionState
import io.github.qmryqmm.hyperopen.ui.components.ShortcutRowNode
import io.github.qmryqmm.hyperopen.ui.components.dragRowVisuals
import io.github.qmryqmm.hyperopen.ui.components.duplicateShortcut
import io.github.qmryqmm.hyperopen.ui.components.newBlankChildShortcut
import io.github.qmryqmm.hyperopen.ui.components.shortcutRowGestures
import io.github.qmryqmm.hyperopen.ui.components.toComposeImageBitmap
import io.github.qmryqmm.hyperopen.view.IconGlyphFactory
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 快捷方式详情编辑页面。
 * 提供完整的一站式配置：
 *   - 图标外观：相册上传/裁切、应用图标、MIUIX 矢量图标、底垫质感与颜色；
 *   - 基础信息：标题、副标题、主题色调；
 *   - 提示文字：顶部胶囊内独立颜色、透明度、投影、描边、字重与字距；
 *   - 动作配置：App 启动、Intent URI、Activity 精准定位（参数构建）、广播、手电筒、勿扰、静音与 Shell。
 */

/** 设备 Android 用户条目（id + 显示名），供「启动用户」选择列表使用 */
data class AndroidUserOption(val id: Int, val name: String)

/**
 * 枚举设备上的 Android 用户：
 * 1) 反射 UserManager#getUsers()（@SystemApi；可用时拿到真实用户名，如「手机分身」）
 * 2) 失败则退化为常用固定项：主空间 0 + 应用双开/手机分身 999
 */
fun loadAndroidUsers(ctx: android.content.Context): List<AndroidUserOption> {
    val found = mutableListOf<AndroidUserOption>()
    try {
        val um = ctx.getSystemService(android.os.UserManager::class.java)
        if (um != null) {
            val raw: List<*>? = try {
                @Suppress("UNCHECKED_CAST")
                um.javaClass.getMethod("getUsers").invoke(um) as? List<*>
            } catch (_: Throwable) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    um.javaClass.getMethod("getUsers", java.lang.Boolean.TYPE)
                        .invoke(um, true) as? List<*>
                } catch (_: Throwable) {
                    null
                }
            }
            raw?.forEach { u ->
                if (u == null) return@forEach
                try {
                    val id = u.javaClass.getField("id").getInt(u)
                    val name = (u.javaClass.getField("name").get(u) as? String)
                        ?.ifBlank { "用户$id" } ?: "用户$id"
                    found.add(AndroidUserOption(id, name))
                } catch (_: Throwable) {
                }
            }
        }
    } catch (_: Throwable) {
    }
    if (found.isEmpty()) found.add(AndroidUserOption(0, "主空间"))
    if (found.none { it.id == 999 }) found.add(AndroidUserOption(999, "应用双开 / 手机分身"))
    return found.sortedBy { it.id }
}

/** 「启动 Activity」附加 Extra 的编辑器内存行（保存/测试时转 ActivityLaunchSpec.Extra） */
data class ActivityExtraRow(
    val key: String = "",
    val type: String = ActivityLaunchSpec.TYPE_STRING,
    val value: String = "",
)

/** Extra 类型循环切换：string → int → long → bool → float → string */
private fun nextExtraType(t: String): String = when (t) {
    ActivityLaunchSpec.TYPE_STRING -> ActivityLaunchSpec.TYPE_INT
    ActivityLaunchSpec.TYPE_INT -> ActivityLaunchSpec.TYPE_LONG
    ActivityLaunchSpec.TYPE_LONG -> ActivityLaunchSpec.TYPE_BOOL
    ActivityLaunchSpec.TYPE_BOOL -> ActivityLaunchSpec.TYPE_FLOAT
    else -> ActivityLaunchSpec.TYPE_STRING
}

/** 预览卡片上的「铅笔」编辑角标：主色圆底 + 白色铅笔，提示该区域可点开浮窗编辑 */
@Composable
private fun EditBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Edit,
            contentDescription = "编辑",
            tint = Color.White,
            modifier = Modifier.size(11.dp),
        )
    }
}

@Composable
fun ShortcutEditPage(
    contentPadding: PaddingValues,
    initialItem: ShortcutItem,
    isNew: Boolean,
    backTrigger: Int = 0,
    onSave: (ShortcutItem, Boolean) -> Unit,
    onDelete: (ShortcutItem) -> Unit,
    onExitWithoutSave: () -> Unit,
    onEditChild: ((ShortcutItem, ShortcutItem) -> Unit)? = null,
    existingShortcuts: List<ShortcutItem> = emptyList(),
) {
    val ctx = LocalContext.current
    val lazyListState = rememberLazyListState()

    // ── 选项分组展开收起（与参数配置页同款）：key → 是否已收起，持久化到 hyperopen_ui_state ──
    val uiPrefs = remember { ctx.getSharedPreferences("hyperopen_ui_state", android.content.Context.MODE_PRIVATE) }
    val collapsed = remember {
        androidx.compose.runtime.mutableStateMapOf<String, Boolean>().apply {
            listOf(
                "basic_info", "action_type", "target_user", "payload",
            ).forEach { k ->
                if (uiPrefs.getBoolean("edit_section_collapsed_$k", false)) put(k, true)
            }
        }
    }
    fun isOpen(key: String) = collapsed[key] != true
    fun toggle(key: String) {
        val willCollapse = isOpen(key)
        collapsed[key] = willCollapse
        uiPrefs.edit().putBoolean("edit_section_collapsed_$key", willCollapse).apply()
    }

    var title by remember { mutableStateOf(initialItem.title) }
    var subtitle by remember { mutableStateOf(initialItem.subtitle) }
    var actionType by remember { mutableStateOf(initialItem.actionType) }
    var actionPayload by remember { mutableStateOf(initialItem.actionPayload) }
    var iconPackage by remember { mutableStateOf(initialItem.iconPackage) }
    var iconGlyph by remember { mutableStateOf(initialItem.iconGlyph) }
    var glyphPadEnabled by remember { mutableStateOf(initialItem.glyphPadEnabled) }
    var glyphPadColor by remember { mutableIntStateOf(initialItem.glyphPadColor) }
    var glyphPadMode by remember { mutableIntStateOf(initialItem.glyphPadMode) }
    var appIconCropMode by remember {
        mutableIntStateOf(
            if (initialItem.appIconCropMode > 0) initialItem.appIconCropMode
            else HyperOpenConfig.CROP_ADAPTIVE_EXPAND
        )
    }
    var enabled by remember { mutableStateOf(initialItem.enabled) }
    // ── FOLDER 子快捷方式卡片列表（替代原 JSON 文本框）：直接编辑子项，保存时序列化回 payload ──
    val folderChildren = remember {
        mutableStateListOf<ShortcutItem>().apply { addAll(initialItem.parseFolderChildren()) }
    }
    var childrenRev by remember { mutableIntStateOf(0) }
    // ── 提示文字样式（hint* 字段）：颜色 / 透明度 / 投影 / 描边 / 字重 / 字距 / 显隐 ──
    var followGlobalHintStyle by remember { mutableStateOf(initialItem.followGlobalHintStyle) }
    var hintTextColor by remember { mutableIntStateOf(initialItem.hintTextColor) }
    var hintTextAlpha by remember { mutableFloatStateOf(initialItem.hintTextAlpha) }
    var hintTextColorSub by remember { mutableIntStateOf(initialItem.hintTextColorSub) }
    /** -1 = 跟随标题透明度 ×0.82 */
    var hintTextAlphaSub by remember { mutableFloatStateOf(initialItem.hintTextAlphaSub) }
    var hintTextShadow by remember { mutableFloatStateOf(initialItem.hintTextShadow.toFloat()) }
    var hintTextStroke by remember { mutableFloatStateOf(initialItem.hintTextStroke.toFloat()) }
    var hintTextWeight by remember { mutableIntStateOf(initialItem.hintTextWeight) }
    var hintTextLetterSpacing by remember { mutableFloatStateOf(initialItem.hintTextLetterSpacing) }
    var showHintTitle by remember { mutableStateOf(initialItem.showHintTitle) }
    var showHintSubtitle by remember { mutableStateOf(initialItem.showHintSubtitle) }
    // 颜色十六进制输入（关闭「跟随」后显示，与底垫 padHexInput 同款双向同步模式）
    var hintHexInput by remember {
        val base = if (initialItem.hintTextColor != 0) initialItem.hintTextColor else initialItem.tintColor
        mutableStateOf(String.format("#%06X", base and 0xFFFFFF))
    }
    var hintHexSubInput by remember {
        val base = if (initialItem.hintTextColorSub != 0) initialItem.hintTextColorSub
            else if (initialItem.hintTextColor != 0) initialItem.hintTextColor else initialItem.tintColor
        mutableStateOf(String.format("#%06X", base and 0xFFFFFF))
    }
    // 启动用户（0 = 当前/主空间默认；>0 跨用户，如 999 应用双开/分身）与 SHELL Root 执行开关
    // 启动用户：userId 字段优先；老快捷方式只有 URI 标记（i.hyperopen_user）时从 payload 解析回来
    val initialPickedUser = if (initialItem.userId > 0) initialItem.userId
        else if (initialItem.actionType == ShortcutItem.ACTION_INTENT_URI)
            ShortcutItem.extractUserFromPayload(initialItem.actionPayload).coerceAtLeast(0)
        else 0
    var pickedUser by remember { mutableIntStateOf(initialPickedUser) }
    var shellAsRoot by remember { mutableStateOf(initialItem.shellUseRoot) }
    var customUserText by remember {
        mutableStateOf(if (initialPickedUser > 0 && initialPickedUser != 999) initialPickedUser.toString() else "")
    }
    // ── 「启动 Activity」结构化 Intent 编辑状态（与 actionPayload 的 JSON 双向同步）──
    val initialActSpec = remember(initialItem) {
        if (initialItem.actionType == ShortcutItem.ACTION_ACTIVITY) ActivityLaunchSpec.fromJson(initialItem.actionPayload)
        else ActivityLaunchSpec()
    }
    var actAction by remember { mutableStateOf(initialActSpec.action ?: "") }
    var actData by remember { mutableStateOf(initialActSpec.data ?: "") }
    var actComponent by remember { mutableStateOf(initialActSpec.component ?: "") }
    var actPackage by remember { mutableStateOf(initialActSpec.packageName ?: "") }
    var actExtras by remember {
        mutableStateOf(initialActSpec.extras.map { ActivityExtraRow(it.key ?: "", it.type ?: "string", it.value ?: "") })
    }
    var actNewTask by remember { mutableStateOf(initialActSpec.newTask) }
    var actUseRoot by remember { mutableStateOf(initialActSpec.useRoot) }
    // 拼装当前结构化规格为 payload JSON（保存与「立即启动测试」共用，保证测的就是存的）
    fun buildActPayload(): String {
        val spec = ActivityLaunchSpec()
        spec.action = actAction.trim()
        spec.data = actData.trim()
        spec.component = actComponent.trim()
        spec.packageName = actPackage.trim()
        spec.newTask = actNewTask
        spec.useRoot = actUseRoot
        spec.extras = actExtras.map { ActivityLaunchSpec.Extra(it.key.trim(), it.type, it.value) }
        return spec.toJson()
    }
    // 组装当前编辑态为可保存的快捷方式（「保存修改」按钮与未保存弹窗「保存并退出」共用）
    fun buildSavedItem(): ShortcutItem {
        val newItem = initialItem.copy()
        newItem.title = title.trim().ifBlank { "快捷方式" }
        newItem.subtitle = subtitle.trim()
        newItem.actionType = actionType
        newItem.actionPayload = when {
            actionType == ShortcutItem.ACTION_ACTIVITY -> buildActPayload()
            // FOLDER：子项以卡片列表维护，保存时序列化回 payload JSON 数组
            actionType == ShortcutItem.ACTION_FOLDER -> HyperOpenConfig.serializeShortcutsJson(folderChildren.toList())
            else -> actionPayload.trim()
        }
        newItem.iconPackage = iconPackage.trim()
        newItem.iconGlyph = iconGlyph.trim()
        newItem.glyphPadEnabled = glyphPadEnabled
        newItem.glyphPadColor = glyphPadColor
        newItem.glyphPadMode = glyphPadMode
        newItem.appIconCropMode = appIconCropMode
        newItem.enabled = enabled
        newItem.userId = pickedUser
        newItem.shellUseRoot = shellAsRoot
        newItem.followGlobalHintStyle = followGlobalHintStyle
        newItem.hintTextColor = hintTextColor
        newItem.hintTextAlpha = hintTextAlpha.coerceIn(0.05f, 1f)
        newItem.hintTextColorSub = hintTextColorSub
        newItem.hintTextAlphaSub = hintTextAlphaSub
        newItem.hintTextShadow = hintTextShadow.toInt().coerceIn(0, 100)
        newItem.hintTextStroke = Math.round(hintTextStroke).coerceIn(0, 4)
        newItem.hintTextWeight = hintTextWeight
        newItem.hintTextLetterSpacing = (Math.round(hintTextLetterSpacing * 2f) / 2f).coerceIn(-2f, 6f)
        newItem.showHintTitle = showHintTitle
        newItem.showHintSubtitle = showHintSubtitle
        if (newItem.actionType == ShortcutItem.ACTION_INTENT_URI) {
            // 同步写回 URI 标记（i.hyperopen_user=N）：payload 自描述，
            // 且 SystemUI 里未重启的旧版模块代码也能按用户启动
            newItem.actionPayload = ShortcutItem.syncUserMarker(newItem.actionPayload, pickedUser)
        }
        return newItem
    }
    // 设备用户列表（「启动用户」选择用；反射枚举一次即可）
    val userOptions = remember(ctx) { loadAndroidUsers(ctx) }

    var showAppPicker by remember { mutableStateOf(false) }
    var showActivityPicker by remember { mutableStateOf(false) }
    var showMiuixIconPicker by remember { mutableStateOf(false) }
    var showRemixIconPicker by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showIconDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }

    // 自定义矢量图标底垫颜色：十六进制文本框（与 RGB 分量滑条双向同步）
    var padHexInput by remember {
        mutableStateOf(String.format("#%06X", initialItem.glyphPadColor and 0xFFFFFF))
    }
    fun parseHexColor(text: String): Int? {
        val t = text.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        if (t.length != 6) return null
        return try {
            (0xFF shl 24) or t.toLong(16).toInt()
        } catch (_: Throwable) {
            null
        }
    }
    fun composePadColor(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    fun syncPadHexFromColor() {
        padHexInput = String.format("#%06X", glyphPadColor and 0xFFFFFF)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    val rawBmp = android.graphics.BitmapFactory.decodeStream(stream)
                    if (rawBmp != null && rawBmp.width > 0 && rawBmp.height > 0) {
                        val size = 192
                        val srcW = rawBmp.width
                        val srcH = rawBmp.height
                        val minEdge = kotlin.math.min(srcW, srcH)
                        val cropX = (srcW - minEdge) / 2
                        val cropY = (srcH - minEdge) / 2
                        val cropped = android.graphics.Bitmap.createBitmap(rawBmp, cropX, cropY, minEdge, minEdge)
                        val scaled = if (minEdge != size) {
                            android.graphics.Bitmap.createScaledBitmap(cropped, size, size, true)
                        } else {
                            cropped
                        }
                        val bos = java.io.ByteArrayOutputStream()
                        scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos)
                        val b64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
                        iconPackage = "${IconGlyphFactory.CUSTOM_IMAGE_PREFIX}$b64"
                        iconGlyph = ""
                        Toast.makeText(ctx, "已成功导入本地自定义图标", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "无法解析所选图片文件", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Throwable) {
                Toast.makeText(ctx, "导入图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 检查是否有未保存的修改（基准为「最近一次保存」的快照：保存修改后留在编辑页时重置基准，
    // 返回键不会因已保存内容误弹未保存弹窗）
    var savedBaseline by remember { mutableStateOf(initialItem) }
    val isModified = remember(title, subtitle, actionType, actionPayload, iconPackage, iconGlyph, glyphPadEnabled, glyphPadColor, glyphPadMode, appIconCropMode, enabled, pickedUser, shellAsRoot, actAction, actData, actComponent, actPackage, actExtras, actNewTask, actUseRoot, savedBaseline, followGlobalHintStyle, hintTextColor, hintTextAlpha, hintTextColorSub, hintTextAlphaSub, hintTextShadow, hintTextStroke, hintTextWeight, hintTextLetterSpacing, showHintTitle, showHintSubtitle, childrenRev) {
        title != savedBaseline.title ||
        subtitle != savedBaseline.subtitle ||
        actionType != savedBaseline.actionType ||
        (actionType != ShortcutItem.ACTION_FOLDER && actionPayload != savedBaseline.actionPayload) ||
        iconPackage != savedBaseline.iconPackage ||
        iconGlyph != savedBaseline.iconGlyph ||
        glyphPadEnabled != savedBaseline.glyphPadEnabled ||
        glyphPadColor != savedBaseline.glyphPadColor ||
        glyphPadMode != savedBaseline.glyphPadMode ||
        appIconCropMode != (if (savedBaseline.appIconCropMode > 0) savedBaseline.appIconCropMode else HyperOpenConfig.CROP_ADAPTIVE_EXPAND) ||
        enabled != savedBaseline.enabled ||
        pickedUser != savedBaseline.userId ||
        shellAsRoot != savedBaseline.shellUseRoot ||
        followGlobalHintStyle != savedBaseline.followGlobalHintStyle ||
        hintTextColor != savedBaseline.hintTextColor ||
        hintTextAlpha != savedBaseline.hintTextAlpha ||
        hintTextColorSub != savedBaseline.hintTextColorSub ||
        hintTextAlphaSub != savedBaseline.hintTextAlphaSub ||
        hintTextShadow.toInt() != savedBaseline.hintTextShadow ||
        Math.round(hintTextStroke) != savedBaseline.hintTextStroke ||
        hintTextWeight != savedBaseline.hintTextWeight ||
        Math.round(hintTextLetterSpacing * 2f) / 2 != Math.round(savedBaseline.hintTextLetterSpacing * 2f) / 2 ||
        showHintTitle != savedBaseline.showHintTitle ||
        showHintSubtitle != savedBaseline.showHintSubtitle ||
        (actionType == ShortcutItem.ACTION_ACTIVITY && buildActPayload() != savedBaseline.actionPayload) ||
        // FOLDER：子项列表即 payload，双侧归一化序列化后对比（预设手写 JSON 字段顺序差异不误报）
        (actionType == ShortcutItem.ACTION_FOLDER &&
            HyperOpenConfig.serializeShortcutsJson(folderChildren.toList()) !=
                HyperOpenConfig.serializeShortcutsJson(savedBaseline.parseFolderChildren()))
    }

    // 响应顶部返回键或系统返回手势（记录初始 trigger，只有在当前页面内新发生点击时才响应）
    var lastHandledTrigger by remember { mutableIntStateOf(backTrigger) }
    LaunchedEffect(backTrigger) {
        if (backTrigger > lastHandledTrigger) {
            lastHandledTrigger = backTrigger
            if (isModified || isNew) {
                showUnsavedDialog = true
            } else {
                onExitWithoutSave()
            }
        }
    }

    // 实时图标预览与是否为应用图标判定
    val vectorIcon = remember(iconPackage, iconGlyph) {
        if (iconPackage.isBlank()) {
            if (RemixIconRegistry.isRemixIcon(iconGlyph)) {
                RemixIconRegistry.find(iconGlyph)
            } else {
                MiuixIconRegistry.find(iconGlyph)
            }
        } else null
    }
    val rawAppIconDrawable = remember(iconPackage, iconGlyph, actionType, actionPayload) {
        if (vectorIcon != null) {
            null
        } else {
            IconGlyphFactory.resolveRawAppIcon(ctx, iconPackage, iconGlyph, actionType, actionPayload)
        }
    }
    val isAppIcon = rawAppIconDrawable != null

    val previewItem = remember(
        initialItem, title, subtitle, actionType, actionPayload,
        iconPackage, iconGlyph, appIconCropMode, glyphPadEnabled, glyphPadColor, glyphPadMode
    ) {
        initialItem.copy().apply {
            this.title = title.trim().ifBlank { "快捷方式" }
            this.subtitle = subtitle.trim()
            this.actionType = actionType
            this.actionPayload = when {
                actionType == ShortcutItem.ACTION_ACTIVITY -> buildActPayload()
                actionType == ShortcutItem.ACTION_FOLDER -> HyperOpenConfig.serializeShortcutsJson(folderChildren.toList())
                else -> actionPayload.trim()
            }
            this.iconPackage = iconPackage.trim()
            this.iconGlyph = iconGlyph.trim()
            this.appIconCropMode = appIconCropMode
            this.glyphPadEnabled = glyphPadEnabled
            this.glyphPadColor = glyphPadColor
            this.glyphPadMode = glyphPadMode
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── 预览卡片 ──
        item(key = "preview_card") {
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier.clickable { showIconDialog = true },
                            contentAlignment = Alignment.TopEnd,
                        ) {
                            ShortcutBubbleIcon(
                                item = previewItem,
                                modifier = Modifier.size(64.dp),
                            )
                            EditBadge()
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.clickable { showTextDialog = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = title.ifBlank { "快捷方式名称" },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
                                        fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            EditBadge()
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                text = if (isNew) "确认添加" else "保存修改",
                                onClick = {
                                    val newItem = buildSavedItem()
                                    if (isNew) {
                                        // 新建：保存后退出编辑器，回到快捷方式列表
                                        onSave(newItem, true)
                                    } else {
                                        // 编辑：保存后留在编辑界面继续调整（不自动退出）
                                        savedBaseline = newItem
                                        onSave(newItem, false)
                                    }
                                },
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                            TextButton(
                                text = "启动测试",
                                onClick = {
                                    val testItem = initialItem.copy().apply {
                                        this.title = title.trim().ifBlank { "测试快捷方式" }
                                        this.actionType = actionType
                                        this.actionPayload = when {
                                            actionType == ShortcutItem.ACTION_ACTIVITY -> buildActPayload()
                                            actionType == ShortcutItem.ACTION_FOLDER -> HyperOpenConfig.serializeShortcutsJson(folderChildren.toList())
                                            else -> actionPayload.trim()
                                        }
                                    }
                                    testItem.userId = pickedUser
                                    testItem.shellUseRoot = shellAsRoot
                                    if (testItem.actionType == ShortcutItem.ACTION_INTENT_URI) {
                                        // 与保存一致：URI 同步写回用户标记，测试的就是最终生效的 payload
                                        testItem.actionPayload = ShortcutItem.syncUserMarker(testItem.actionPayload, pickedUser)
                                    }
                                    testItem.executeAsync(ctx)
                                },
                            )
                            if (!isNew) {
                                TextButton(
                                    text = "删除此项",
                                    onClick = {
                                        showDeleteDialog = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 启动选项类型选择 ──
        item(key = "action_type_title") {
            CollapsibleSectionTitle("启动选项与执行动作", isOpen("action_type")) { toggle("action_type") }
        }
        if (isOpen("action_type")) {
        item(key = "action_type_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                RadioButtonPreference(
                    title = "唤醒小爱 (XIAOAI)",
                    summary = "直接唤醒 HyperOS 小爱语音助手",
                    selected = actionType == ShortcutItem.ACTION_XIAOAI,
                    onClick = { actionType = ShortcutItem.ACTION_XIAOAI },
                )
                RadioButtonPreference(
                    title = "静音模式 (MUTE)",
                    summary = "快速切换系统全局静音与响铃模式",
                    selected = actionType == ShortcutItem.ACTION_MUTE,
                    onClick = { actionType = ShortcutItem.ACTION_MUTE },
                )
                RadioButtonPreference(
                    title = "勿扰模式 (DND)",
                    summary = "快速开启或退出静音防打扰模式",
                    selected = actionType == ShortcutItem.ACTION_DND,
                    onClick = { actionType = ShortcutItem.ACTION_DND },
                )
                RadioButtonPreference(
                    title = "开关手电筒 (FLASHLIGHT)",
                    summary = "直接切换手机后置 LED 手电筒",
                    selected = actionType == ShortcutItem.ACTION_FLASHLIGHT,
                    onClick = { actionType = ShortcutItem.ACTION_FLASHLIGHT },
                )
                RadioButtonPreference(
                    title = "街拍相机 (CAMERA)",
                    summary = "极速启动原生系统相机抓拍",
                    selected = actionType == ShortcutItem.ACTION_CAMERA,
                    onClick = { actionType = ShortcutItem.ACTION_CAMERA },
                )
                RadioButtonPreference(
                    title = "文件夹 / 聚合分组 (FOLDER)",
                    summary = "手指滑到该气泡上悬停就地展开多项子快捷方式，兼顾面板简洁与大容量",
                    selected = actionType == ShortcutItem.ACTION_FOLDER,
                    onClick = { actionType = ShortcutItem.ACTION_FOLDER },
                )
                RadioButtonPreference(
                    title = "打开应用 (APP)",
                    summary = "启动手机安装的目标应用程序",
                    selected = actionType == ShortcutItem.ACTION_APP,
                    onClick = { actionType = ShortcutItem.ACTION_APP },
                )
                RadioButtonPreference(
                    title = "深度链接 / Intent (INTENT_URI)",
                    summary = "调起扫一扫、付款码等特定 Intent URI",
                    selected = actionType == ShortcutItem.ACTION_INTENT_URI,
                    onClick = { actionType = ShortcutItem.ACTION_INTENT_URI },
                )
                RadioButtonPreference(
                    title = "启动 Activity (ACTIVITY)",
                    summary = "结构化启动任意 Activity",
                    selected = actionType == ShortcutItem.ACTION_ACTIVITY,
                    onClick = { actionType = ShortcutItem.ACTION_ACTIVITY },
                )
                RadioButtonPreference(
                    title = "Shell 指令 (SHELL)",
                    summary = "执行终端 Shell 脚本命令",
                    selected = actionType == ShortcutItem.ACTION_SHELL,
                    onClick = { actionType = ShortcutItem.ACTION_SHELL },
                )
                RadioButtonPreference(
                    title = "发送广播 (BROADCAST)",
                    summary = "向系统分发广播 Action",
                    selected = actionType == ShortcutItem.ACTION_BROADCAST,
                    onClick = { actionType = ShortcutItem.ACTION_BROADCAST },
                )
            }
        }
        }

        // ── 启动用户选择（APP / INTENT_URI / ACTIVITY 生效；0 = 主空间，>0 跨用户启动）──
        if (actionType == ShortcutItem.ACTION_APP || actionType == ShortcutItem.ACTION_INTENT_URI ||
            actionType == ShortcutItem.ACTION_ACTIVITY) {
            val customUserSelected = pickedUser > 0 && userOptions.none { it.id == pickedUser }
            item(key = "target_user_title") {
                CollapsibleSectionTitle("启动用户（应用双开 / 分身）", isOpen("target_user")) { toggle("target_user") }
            }
            if (isOpen("target_user")) {
            item(key = "target_user_card") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    RadioButtonPreference(
                        title = "当前用户 / 主空间（默认）",
                        summary = "在默认用户空间中启动，不跨用户",
                        selected = pickedUser <= 0,
                        onClick = { pickedUser = 0 },
                    )
                    userOptions.filter { it.id > 0 }.forEach { u ->
                        RadioButtonPreference(
                            title = "${u.id} · ${u.name}",
                            summary = "以 user ${u.id} 身份跨用户启动（该用户内需已安装/双开目标应用）",
                            selected = pickedUser == u.id,
                            onClick = { pickedUser = u.id },
                        )
                    }
                    RadioButtonPreference(
                        title = "自定义用户 ID",
                        summary = "手动输入其他 Android 用户 ID（user > 0）",
                        selected = customUserSelected,
                        onClick = {
                            pickedUser = customUserText.trim().toIntOrNull()
                                ?.takeIf { v -> v > 0 && userOptions.none { it.id == v } } ?: 10
                        },
                    )
                    if (customUserSelected) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            TextField(
                                value = customUserText,
                                onValueChange = { text ->
                                    customUserText = text
                                    text.trim().toIntOrNull()?.let { v -> if (v > 0) pickedUser = v }
                                },
                                label = "目标用户 ID（例如 10）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            }
        }

        // ── 动作参数详情填写 ──
        val isNoPayloadAction = actionType == ShortcutItem.ACTION_FLASHLIGHT ||
            actionType == ShortcutItem.ACTION_XIAOAI ||
            actionType == ShortcutItem.ACTION_MUTE ||
            actionType == ShortcutItem.ACTION_DND ||
            actionType == ShortcutItem.ACTION_CAMERA

        if (!isNoPayloadAction) {
            item(key = "payload_title") {
                CollapsibleSectionTitle(
                    if (actionType == ShortcutItem.ACTION_FOLDER) "子快捷方式列表" else "动作参数详情 (Payload)",
                    isOpen("payload"),
                ) { toggle("payload") }
            }
            if (isOpen("payload")) {
            item(key = "payload_card") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    if (actionType == ShortcutItem.ACTION_ACTIVITY) {
                        // ── 结构化「启动 Activity」编辑器（组件 / Data / Extra / Action / NEW_TASK / Root）──
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Column {
                                TextField(
                                    value = actComponent,
                                    onValueChange = { actComponent = it },
                                    label = "目标组件（仅类名，可留空）",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                                TextField(
                                    value = actPackage,
                                    onValueChange = { actPackage = it },
                                    label = "目标包名（组件填类名时需配合填写，也可单独按包名启动，可选）",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    text = "从已安装应用选取活动",
                                    onClick = { showActivityPicker = true },
                                    modifier = Modifier.align(Alignment.End),
                                )
                                if (!actUseRoot) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "附加 Action（点预设快速填入，或在下方自定义）",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                ) {
                                    listOf(
                                        "无" to "",
                                        "MAIN" to "android.intent.action.MAIN",
                                        "VIEW" to "android.intent.action.VIEW",
                                        "DIAL" to "android.intent.action.DIAL",
                                        "SEND" to "android.intent.action.SEND",
                                        "EDIT" to "android.intent.action.EDIT",
                                        "PICK" to "android.intent.action.PICK",
                                        "SEARCH" to "android.intent.action.SEARCH",
                                        "SETTINGS" to "android.settings.SETTINGS",
                                    ).forEach { (label, value) ->
                                        TextButton(text = label, onClick = { actAction = value })
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                TextField(
                                    value = actAction,
                                    onValueChange = { actAction = it },
                                    label = "自定义 Action 字符串（可留空）",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                                TextField(
                                    value = actData,
                                    onValueChange = { actData = it },
                                    label = "附加 Data URI（可选，例如 https:// / tel: / weixin://）",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "附加 Extra（点类型按钮切换 string/int/long/bool/float）",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.height(6.dp))
                                actExtras.forEachIndexed { i, e ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        TextField(
                                            value = e.key,
                                            onValueChange = { v -> actExtras = actExtras.toMutableList().also { it[i] = e.copy(key = v) } },
                                            label = "Key",
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            text = e.type,
                                            onClick = { actExtras = actExtras.toMutableList().also { it[i] = e.copy(type = nextExtraType(e.type)) } },
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                        )
                                        TextField(
                                            value = e.value,
                                            onValueChange = { v -> actExtras = actExtras.toMutableList().also { it[i] = e.copy(value = v) } },
                                            label = "Value",
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            text = "✕",
                                            onClick = { actExtras = actExtras.toMutableList().also { it.removeAt(i) } },
                                        )
                                    }
                                }
                                TextButton(
                                    text = "＋ 添加 Extra",
                                    onClick = { actExtras = actExtras + ActivityExtraRow() },
                                )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SwitchPreference(
                            title = "使用 Root 打开（su）",
                            summary = "经 root（桥接 su）执行 am start -n 拉起，可绕过组件未导出/权限限制，并支持与「启动用户」组合",
                            checked = actUseRoot,
                            onCheckedChange = { actUseRoot = it },
                        )
                        if (actUseRoot) {
                            Text(
                                text = "Root 模式仅使用 目标组件 / 目标包名（am start -n），Action / Data / Extra / NEW_TASK 不参与执行（已填写内容会保留）",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        } else {
                            SwitchPreference(
                                title = "Intent.FLAG_ACTIVITY_NEW_TASK",
                                summary = "在新任务栈中启动 Activity（从指纹面板/SystemUI 启动时通常必须开启）",
                                checked = actNewTask,
                                onCheckedChange = { actNewTask = it },
                            )
                        }
                    } else if (actionType == ShortcutItem.ACTION_FOLDER) {
                        // ── FOLDER：子快捷方式卡片列表（替代原 JSON 文本框）──
                        FolderChildrenEditor(
                            children = folderChildren,
                            existingShortcuts = existingShortcuts.filter { it.id != initialItem.id },
                            onEditChild = { child -> onEditChild?.invoke(buildSavedItem(), child) },
                            onChanged = { childrenRev++ },
                        )
                    } else {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Column {
                            val payloadSingleLine = actionType == ShortcutItem.ACTION_APP || actionType == ShortcutItem.ACTION_BROADCAST
                            // TextField 内部用 rememberSaveable 保存滚动位置（含横/纵方向），
                            // 当 payload 卡片被移出再移入 LazyColumn 且 singleLine 发生变化时，
                            // 恢复出的滚动方向与新模式不一致会抛出 IllegalArgumentException 闪退。
                            // 以 singleLine 作为 key，让单行/多行模式各自持有独立的保存状态。
                            key(payloadSingleLine) {
                                TextField(
                                    value = actionPayload,
                                    onValueChange = { actionPayload = it },
                                    label = when (actionType) {
                                        ShortcutItem.ACTION_APP -> "应用包名（例如 com.tencent.mm）"
                                        ShortcutItem.ACTION_INTENT_URI -> "Intent URI 字符串"
                                        ShortcutItem.ACTION_FOLDER -> "子快捷方式 JSON 数据（由预设或批量添加生成）"
                                        ShortcutItem.ACTION_SHELL -> "Shell 脚本命令"
                                        ShortcutItem.ACTION_BROADCAST -> "广播 Action"
                                        else -> "参数详情"
                                    },
                                    singleLine = payloadSingleLine,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (actionType == ShortcutItem.ACTION_APP) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    text = "从已安装应用选取",
                                    onClick = {
                                        showAppPicker = true
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                )
                            }
                        }
                    }
                    if (actionType == ShortcutItem.ACTION_SHELL) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SwitchPreference(
                            title = "以 Root 权限执行（su）",
                            summary = "开启后命令经 su 以 root 身份执行，可写受保护的系统设置；关闭则以普通 shell 身份执行",
                            checked = shellAsRoot,
                            onCheckedChange = { shellAsRoot = it },
                        )
                    }
                    }
                }
            }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }

    // ── 应用选择弹窗 ──
    if (showAppPicker) {
        AppListPickerDialog(
            onDismiss = { showAppPicker = false },
            onSelectApp = { pkg, appName ->
                if (actionType == ShortcutItem.ACTION_APP && actionPayload.isBlank()) {
                    actionPayload = pkg
                }
                if (title.isBlank() || title == "新快捷方式") {
                    title = appName
                }
                iconPackage = pkg
                iconGlyph = ""
                showAppPicker = false
            },
        )
    }

    // ── Activity 选择弹窗（应用 → 活动列表两级，点活动直接填参）──
    if (showActivityPicker) {
        ActivityPickerDialog(
            onDismiss = { showActivityPicker = false },
            onPick = { pkg, cls, appName ->
                actComponent = cls
                actPackage = pkg
                if (title.isBlank() || title == "新快捷方式") {
                    title = appName
                }
                iconPackage = pkg
                iconGlyph = ""
                showActivityPicker = false
            },
        )
    }

    // ── Miuix 图标选择弹窗 ──
    if (showMiuixIconPicker) {
        MiuixIconPickerDialog(
            currentIconWithWeight = iconGlyph,
            onDismiss = { showMiuixIconPicker = false },
            onSelectIcon = { glyphKey ->
                iconGlyph = glyphKey
                iconPackage = ""
                showMiuixIconPicker = false
            },
        )
    }

    // ── Remix Icon 图标选择弹窗 ──
    if (showRemixIconPicker) {
        RemixIconPickerDialog(
            currentIconKey = iconGlyph,
            onDismiss = { showRemixIconPicker = false },
            onSelectIcon = { glyphKey ->
                iconGlyph = glyphKey
                iconPackage = ""
                showRemixIconPicker = false
            },
        )
    }

    // ── 删除二次确认弹窗（样式与「恢复默认参数」弹窗一致） ──
    if (showDeleteDialog) {
        Dialog(
            onDismissRequest = { showDeleteDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .padding(vertical = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                ) {
                    Text(
                        text = "确认删除",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "确定要删除「${title.trim().ifBlank { "快捷方式" }}」吗？此操作不可撤销。",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { showDeleteDialog = false },
                        )
                        Spacer(Modifier.width(10.dp))
                        TextButton(
                            text = "删除",
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = {
                                showDeleteDialog = false
                                onDelete(initialItem)
                            },
                        )
                    }
                }
            }
        }
    }

    // ── 退出时未保存确认弹窗（MIUIX 质感弹窗） ──
    if (showUnsavedDialog) {
        Dialog(
            onDismissRequest = { showUnsavedDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .padding(vertical = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                ) {
                    Text(
                        text = "保存修改？",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (isNew) "您刚刚创建的快捷方式尚未保存，是否保存并生效？" else "当前快捷方式的参数已发生更改，退出前是否保存？",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            text = "放弃",
                            onClick = {
                                showUnsavedDialog = false
                                onExitWithoutSave()
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        TextButton(
                            text = "保存并退出",
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = {
                                showUnsavedDialog = false
                                // 保存并退出：共用 buildSavedItem()（含 userId/shellUseRoot/ACTIVITY payload/URI 标记同步）
                                onSave(buildSavedItem(), true)
                            },
                        )
                    }
                }
            }
        }
    }

    // ── 图标设置浮窗（点预览卡片图标打开）──
    if (showIconDialog) {
        Dialog(
            onDismissRequest = { showIconDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .imePadding()
                    .padding(vertical = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Text(
                        text = "图标设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // ── 实时预览圆 ──
                        item(key = "icon_dialog_preview") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                ShortcutBubbleIcon(
                                    item = previewItem,
                                    modifier = Modifier.size(64.dp),
                                )
                            }
                        }

                        // ── 图标来源 ──
                        item(key = "icon_dialog_source") {
                            ArrowPreference(
                                title = "上传本地自定义图片",
                                summary = if (IconGlyphFactory.isCustomImage(iconPackage)) "已使用本地上传的自定义图片" else "从相册或文件管理器选择本地图片作为图标",
                                startAction = {
                                    Icon(
                                        imageVector = MiuixIcons.Add,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                },
                                onClick = { imagePickerLauncher.launch("image/*") },
                            )
                            ArrowPreference(
                                title = "选择系统/应用图标",
                                summary = when {
                                    IconGlyphFactory.isCustomImage(iconPackage) -> "未选择"
                                    iconPackage.isNotBlank() -> "当前选择：$iconPackage"
                                    else -> "未选择"
                                },
                                startAction = {
                                    Icon(
                                        imageVector = MiuixIcons.Photos,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                },
                                onClick = { showAppPicker = true },
                            )
                            ArrowPreference(
                                title = "选择 Remix Icon 快捷图标(推荐)",
                                summary = if (iconPackage.isBlank() && iconGlyph.isNotBlank() && RemixIconRegistry.isRemixIcon(iconGlyph)) {
                                    val meta = RemixIconRegistry.findMeta(iconGlyph)
                                    "当前选择：${meta?.desc ?: iconGlyph} (${meta?.rawName ?: iconGlyph})"
                                } else "未选择",
                                startAction = {
                                    Icon(
                                        imageVector = MiuixIcons.Tune,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                },
                                onClick = { showRemixIconPicker = true },
                            )
                            ArrowPreference(
                                title = "选择 Miuix 矢量图标",
                                summary = if (iconPackage.isBlank() && iconGlyph.isNotBlank() && !RemixIconRegistry.isRemixIcon(iconGlyph) && vectorIcon != null) "当前选择：$iconGlyph" else "未选择",
                                startAction = {
                                    Icon(
                                        imageVector = MiuixIcons.Scan,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                },
                                onClick = { showMiuixIconPicker = true },
                            )
                            if (iconPackage.isNotBlank() || iconGlyph.isNotBlank()) {
                                ArrowPreference(
                                    title = "清除自定义图标",
                                    summary = "恢复跟随启动应用或默认符号",
                                    onClick = {
                                        iconPackage = ""
                                        iconGlyph = ""
                                    },
                                )
                            }
                        }

                        // ── 应用图标形状方案（仅应用图标）──
                        if (isAppIcon) {
                            item(key = "icon_dialog_crop") {
                                RadioButtonPreference(
                                    title = "自适应外扩满铺",
                                    summary = "分离前景与背景图层并外扩满铺正圆，彻底消除直角白边",
                                    selected = appIconCropMode == HyperOpenConfig.CROP_ADAPTIVE_EXPAND,
                                    onClick = { appIconCropMode = HyperOpenConfig.CROP_ADAPTIVE_EXPAND },
                                )
                                RadioButtonPreference(
                                    title = "标准抗锯齿圆形裁切",
                                    summary = "对完整渲染图标直接进行正圆形 BitmapShader 裁切",
                                    selected = appIconCropMode == HyperOpenConfig.CROP_BITMAP_CIRCLE,
                                    onClick = { appIconCropMode = HyperOpenConfig.CROP_BITMAP_CIRCLE },
                                )
                            }
                        }

                        // ── 矢量图标底垫与色彩（仅矢量图标）──
                        if (vectorIcon != null && iconPackage.isBlank()) {
                            item(key = "icon_dialog_pad") {
                                Column {
                                    SwitchPreference(
                                        title = "启用此图标圆形底垫",
                                        summary = "为当前矢量图标添加独立圆形底板",
                                        checked = glyphPadEnabled,
                                        onCheckedChange = { glyphPadEnabled = it },
                                    )

                                    if (glyphPadEnabled) {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                                        Text(
                                            text = "底垫渲染质感",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                                        )

                                        RadioButtonPreference(
                                            title = "磨砂微透底板",
                                            summary = "65% 半透明高光底板，颜色与背景毛玻璃相互交融",
                                            selected = glyphPadMode == HyperOpenConfig.GLYPH_PAD_MODE_TRANSLUCENT,
                                            onClick = { glyphPadMode = HyperOpenConfig.GLYPH_PAD_MODE_TRANSLUCENT },
                                        )
                                        RadioButtonPreference(
                                            title = "饱满纯色底板",
                                            summary = "100% 不透明纯色实心圆，上方呈现白色高对比矢量",
                                            selected = glyphPadMode == HyperOpenConfig.GLYPH_PAD_MODE_SOLID,
                                            onClick = { glyphPadMode = HyperOpenConfig.GLYPH_PAD_MODE_SOLID },
                                        )
                                        RadioButtonPreference(
                                            title = "反色渲染模式",
                                            summary = "微透亮白底板 + 彩色矢量图标",
                                            selected = glyphPadMode == HyperOpenConfig.GLYPH_PAD_MODE_INVERTED,
                                            onClick = { glyphPadMode = HyperOpenConfig.GLYPH_PAD_MODE_INVERTED },
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                                        Text(
                                            text = "快捷品牌色预设",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                                        )

                                        val brandColors = listOf(
                                            "微信绿" to 0xFF07C160.toInt(),
                                            "支付宝蓝" to 0xFF1677FF.toInt(),
                                            "小米橙" to 0xFFFF6900.toInt(),
                                            "网易红" to 0xFFE60026.toInt(),
                                            "极客紫" to 0xFF7928CA.toInt(),
                                            "暗夜黑" to 0xFF1F2329.toInt(),
                                            "科技蓝" to 0xFF2A82E4.toInt(),
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            brandColors.forEach { (name, colorValue) ->
                                                val isSelectedColor = glyphPadColor == colorValue
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(colorValue))
                                                        .clickable {
                                                            glyphPadColor = colorValue
                                                            padHexInput = String.format("#%06X", colorValue and 0xFFFFFF)
                                                        },
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    if (isSelectedColor) {
                                                        Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                    }
                                                }
                                            }
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                                        Text(
                                            text = "自定义底垫颜色",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(glyphPadColor)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (brandColors.none { it.second == glyphPadColor }) {
                                                    Text("✦", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                }
                                            }
                                            TextField(
                                                value = padHexInput,
                                                onValueChange = { s ->
                                                    padHexInput = s
                                                    parseHexColor(s)?.let { glyphPadColor = it }
                                                },
                                                label = "十六进制颜色 (#RRGGBB)",
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }

                                        SliderPreference(
                                            title = "红色分量",
                                            summary = "自定义底垫颜色的红色通道 (R)",
                                            valueText = "${(glyphPadColor shr 16) and 0xFF}",
                                            value = ((glyphPadColor shr 16) and 0xFF).toFloat(),
                                            valueRange = 0f..255f,
                                            onValueChange = { r ->
                                                glyphPadColor = composePadColor(
                                                    r.toInt(), (glyphPadColor shr 8) and 0xFF, glyphPadColor and 0xFF
                                                )
                                                syncPadHexFromColor()
                                            },
                                        )
                                        SliderPreference(
                                            title = "绿色分量",
                                            summary = "自定义底垫颜色的绿色通道 (G)",
                                            valueText = "${(glyphPadColor shr 8) and 0xFF}",
                                            value = ((glyphPadColor shr 8) and 0xFF).toFloat(),
                                            valueRange = 0f..255f,
                                            onValueChange = { g ->
                                                glyphPadColor = composePadColor(
                                                    (glyphPadColor shr 16) and 0xFF, g.toInt(), glyphPadColor and 0xFF
                                                )
                                                syncPadHexFromColor()
                                            },
                                        )
                                        SliderPreference(
                                            title = "蓝色分量",
                                            summary = "自定义底垫颜色的蓝色通道 (B)",
                                            valueText = "${glyphPadColor and 0xFF}",
                                            value = (glyphPadColor and 0xFF).toFloat(),
                                            valueRange = 0f..255f,
                                            onValueChange = { b ->
                                                glyphPadColor = composePadColor(
                                                    (glyphPadColor shr 16) and 0xFF, (glyphPadColor shr 8) and 0xFF, b.toInt()
                                                )
                                                syncPadHexFromColor()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            text = "完成",
                            onClick = { showIconDialog = false },
                        )
                    }
                }
            }
        }
    }

    // ── 文字与提示样式浮窗（点预览卡片文字打开）──
    if (showTextDialog) {
        Dialog(
            onDismissRequest = { showTextDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .imePadding()
                    .padding(vertical = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Text(
                        text = "文字与提示",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // ── 实时预览胶囊：颜色/透明度/字重/字距/投影按与真机一致的回退链解析 ──
                        item(key = "text_dialog_preview") {
                            val previewDensity = androidx.compose.ui.platform.LocalDensity.current.density
                            val globalCfg = remember(ctx) { HyperOpenConfig.loadFromApp(ctx) }

                            val previewTitleBase = Color(
                                if (followGlobalHintStyle) initialItem.resolveHintTitleColor(globalCfg)
                                else if (hintTextColor != 0) hintTextColor else initialItem.resolveIconPrimaryColor()
                            )
                            val previewTitleAlpha = if (followGlobalHintStyle) 1.0f else hintTextAlpha.coerceIn(0.05f, 1f)
                            val previewSubAlphaEffective = previewTitleAlpha * 0.82f
                            val previewSubBase = Color(
                                if (followGlobalHintStyle) initialItem.resolveHintSubtitleColor(globalCfg)
                                else if (hintTextColorSub != 0) hintTextColorSub else (if (hintTextColor != 0) hintTextColor else initialItem.resolveIconPrimaryColor())
                            )
                            val previewSubAlpha =
                                if (followGlobalHintStyle) 0.82f
                                else if (hintTextAlphaSub >= 0f) hintTextAlphaSub.coerceIn(0.05f, 1f) else previewSubAlphaEffective

                            val effectiveShadow = if (followGlobalHintStyle) globalCfg.defaultHintTextShadow.toFloat() else hintTextShadow
                            val previewShadow = if (effectiveShadow >= 0.5f) {
                                Shadow(
                                    color = Color(0x99000000),
                                    blurRadius = effectiveShadow / 20f * 4f * previewDensity,
                                )
                            } else null

                            val effectiveWeight = if (followGlobalHintStyle) globalCfg.defaultHintTextWeight else hintTextWeight
                            val previewWeight = when (effectiveWeight) {
                                0 -> FontWeight.Light
                                2 -> FontWeight.Medium
                                3 -> FontWeight.Bold
                                else -> FontWeight.Normal
                            }

                            val effectiveSpacing = if (followGlobalHintStyle) globalCfg.defaultHintTextLetterSpacing else hintTextLetterSpacing
                            val effectiveShowTitle = if (followGlobalHintStyle) globalCfg.defaultShowHintTitle else showHintTitle
                            val effectiveShowSubtitle = if (followGlobalHintStyle) globalCfg.defaultShowHintSubtitle else showHintSubtitle

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0x33FFFFFF))
                                        .padding(horizontal = 18.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (effectiveShowTitle) {
                                        Text(
                                            text = title.trim().ifBlank { "快捷方式" },
                                            fontSize = 15.sp,
                                            fontWeight = previewWeight,
                                            letterSpacing = (15f * effectiveSpacing * 0.05f).sp,
                                            color = previewTitleBase.copy(alpha = previewTitleBase.alpha * previewTitleAlpha),
                                            style = TextStyle(shadow = previewShadow),
                                        )
                                    }
                                    if (effectiveShowSubtitle) {
                                        Text(
                                            text = subtitle.trim().ifEmpty { "移至目标图标后松手直接打开" },
                                            fontSize = 11.sp,
                                            letterSpacing = (11f * effectiveSpacing * 0.05f).sp,
                                            color = previewSubBase.copy(alpha = previewSubBase.alpha * previewSubAlpha),
                                            style = TextStyle(shadow = previewShadow),
                                        )
                                    }
                                }
                            }
                        }

                        // ── 标题 / 副标题内容 ──
                        item(key = "text_dialog_title") {
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                TextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    label = "快捷方式标题",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        item(key = "text_dialog_subtitle") {
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                TextField(
                                    value = subtitle,
                                    onValueChange = { subtitle = it },
                                    label = "副标题 / 功能描述（可选）",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        item(key = "text_dialog_follow_global") {
                            SwitchPreference(
                                title = "提示文字样式跟随全局配置",
                                summary = "开启后文字显隐、描边、投影柔光、字重、字间距与颜色均统一跟随全局设置，避免重复调整",
                                checked = followGlobalHintStyle,
                                onCheckedChange = { followGlobalHintStyle = it },
                            )
                        }

                        if (!followGlobalHintStyle) {
                            item(key = "text_dialog_content") {
                                Column {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    Text(
                                        text = "文字内容",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                                    )
                                    SwitchPreference(
                                        title = "显示提示标题",
                                        summary = "选中图标时胶囊内显示主标题（快捷方式标题）",
                                        checked = showHintTitle,
                                        onCheckedChange = { showHintTitle = it },
                                    )
                                    SwitchPreference(
                                        title = "显示提示副标题",
                                        summary = "选中图标时胶囊内显示副标题描述",
                                        checked = showHintSubtitle,
                                        onCheckedChange = { showHintSubtitle = it },
                                    )
                                }
                            }

                            item(key = "text_dialog_color") {
                                Column {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    Text(
                                        text = "颜色与透明度",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                                    )
                                    SwitchPreference(
                                        title = "标题颜色跟随图标底色（推荐）",
                                        summary = "开启 = 智能提取该图标底垫色或主题色，关闭可自定义单项覆盖色",
                                        checked = hintTextColor == 0,
                                        onCheckedChange = { follow ->
                                            hintTextColor = if (follow) 0
                                            else parseHexColor(hintHexInput) ?: (initialItem.tintColor or 0xFF000000.toInt())
                                        },
                                    )
                                    if (hintTextColor != 0) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(hintTextColor)),
                                            )
                                            TextField(
                                                value = hintHexInput,
                                                onValueChange = { s ->
                                                    hintHexInput = s
                                                    parseHexColor(s)?.let { hintTextColor = it }
                                                },
                                                label = "标题颜色 十六进制 (#RRGGBB)",
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                        SliderPreference(
                                            title = "标题红色分量",
                                            summary = "提示标题颜色的红色通道 (R)",
                                            valueText = "${(hintTextColor shr 16) and 0xFF}",
                                            value = ((hintTextColor shr 16) and 0xFF).toFloat(),
                                            valueRange = 0f..255f,
                                            onValueChange = { r ->
                                                hintTextColor = composePadColor(r.toInt(), (hintTextColor shr 8) and 0xFF, hintTextColor and 0xFF)
                                                hintHexInput = String.format("#%06X", hintTextColor and 0xFFFFFF)
                                            },
                                        )
                                        SliderPreference(
                                            title = "绿色分量",
                                            summary = "提示标题颜色的绿色通道 (G)",
                                            valueText = "${(hintTextColor shr 8) and 0xFF}",
                                            value = ((hintTextColor shr 8) and 0xFF).toFloat(),
                                            valueRange = 0f..255f,
                                            onValueChange = { g ->
                                                hintTextColor = composePadColor((hintTextColor shr 16) and 0xFF, g.toInt(), hintTextColor and 0xFF)
                                                hintHexInput = String.format("#%06X", hintTextColor and 0xFFFFFF)
                                            },
                                        )
                                        SliderPreference(
                                            title = "蓝色分量",
                                            summary = "提示标题颜色的蓝色通道 (B)",
                                            valueText = "${hintTextColor and 0xFF}",
                                            value = (hintTextColor and 0xFF).toFloat(),
                                            valueRange = 0f..255f,
                                            onValueChange = { b ->
                                                hintTextColor = composePadColor((hintTextColor shr 16) and 0xFF, (hintTextColor shr 8) and 0xFF, b.toInt())
                                                hintHexInput = String.format("#%06X", hintTextColor and 0xFFFFFF)
                                            },
                                        )
                                    }
                                    SliderPreference(
                                        title = "标题透明度",
                                        summary = "提示标题不透明度 5%~100%",
                                        valueText = "${Math.round(hintTextAlpha.coerceIn(0.05f, 1f) * 100)}%",
                                        value = hintTextAlpha.coerceIn(0.05f, 1f),
                                        valueRange = 0.05f..1f,
                                        onValueChange = { hintTextAlpha = (Math.round(it * 20f) / 20f).coerceIn(0.05f, 1f) },
                                    )
                                    SwitchPreference(
                                        title = "副标题颜色跟随标题",
                                        summary = "开启 = 与标题同色；关闭可自定义副标题独立颜色",
                                        checked = hintTextColorSub == 0,
                                        onCheckedChange = { follow ->
                                            hintTextColorSub = if (follow) 0
                                            else parseHexColor(hintHexSubInput)
                                                ?: (if (hintTextColor != 0) hintTextColor else initialItem.tintColor)
                                        },
                                    )
                                    if (hintTextColorSub != 0) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(hintTextColorSub)),
                                            )
                                            TextField(
                                                value = hintHexSubInput,
                                                onValueChange = { s ->
                                                    hintHexSubInput = s
                                                    parseHexColor(s)?.let { hintTextColorSub = it }
                                                },
                                                label = "副标题颜色 十六进制 (#RRGGBB)",
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                        SliderPreference(
                                            title = "副标题红色分量",
                                            summary = "提示副标题颜色的红色通道 (R)",
                                            valueText = "${(hintTextColorSub shr 16) and 0xFF}",
                                            value = ((hintTextColorSub shr 16) and 0xFF).toFloat(),
                                            valueRange = 0f..255f,
                                            onValueChange = { r ->
                                                hintTextColorSub = composePadColor(r.toInt(), (hintTextColorSub shr 8) and 0xFF, hintTextColorSub and 0xFF)
                                                hintHexSubInput = String.format("#%06X", hintTextColorSub and 0xFFFFFF)
                                            },
                                        )
                                        SliderPreference(
                                            title = "副标题绿色分量",
                                            summary = "提示副标题颜色的绿色通道 (G)",
                                            valueText = "${(hintTextColorSub shr 8) and 0xFF}",
                                            value = ((hintTextColorSub shr 8) and 0xFF).toFloat(),
                                            valueRange = 0f..255f,
                                            onValueChange = { g ->
                                                hintTextColorSub = composePadColor((hintTextColorSub shr 16) and 0xFF, g.toInt(), hintTextColorSub and 0xFF)
                                                hintHexSubInput = String.format("#%06X", hintTextColorSub and 0xFFFFFF)
                                            },
                                        )
                                        SliderPreference(
                                            title = "副标题蓝色分量",
                                            summary = "提示副标题颜色的蓝色通道 (B)",
                                            valueText = "${hintTextColorSub and 0xFF}",
                                            value = (hintTextColorSub and 0xFF).toFloat(),
                                            valueRange = 0f..255f,
                                            onValueChange = { b ->
                                                hintTextColorSub = composePadColor((hintTextColorSub shr 16) and 0xFF, (hintTextColorSub shr 8) and 0xFF, b.toInt())
                                                hintHexSubInput = String.format("#%06X", hintTextColorSub and 0xFFFFFF)
                                            },
                                        )
                                    }
                                    SwitchPreference(
                                        title = "副标题透明度跟随标题",
                                        summary = "开启 = 标题透明度 ×82%，关闭可独立调节",
                                        checked = hintTextAlphaSub < 0f,
                                        onCheckedChange = { follow ->
                                            hintTextAlphaSub = if (follow) -1f else hintTextAlpha.coerceIn(0.05f, 1f) * 0.82f
                                        },
                                    )
                                    if (hintTextAlphaSub >= 0f) {
                                        SliderPreference(
                                            title = "副标题透明度",
                                            summary = "提示副标题不透明度 5%~100%",
                                            valueText = "${Math.round(hintTextAlphaSub.coerceIn(0.05f, 1f) * 100)}%",
                                            value = hintTextAlphaSub.coerceIn(0.05f, 1f),
                                            valueRange = 0.05f..1f,
                                            onValueChange = { hintTextAlphaSub = (Math.round(it * 20f) / 20f).coerceIn(0.05f, 1f) },
                                        )
                                    }
                                }
                            }

                            item(key = "text_dialog_style") {
                                Column {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    Text(
                                        text = "质感与排版",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                                    )
                                    SliderPreference(
                                        title = "文字描边",
                                        summary = "深色对比描边，任意壁纸下都清晰；1dp 步进",
                                        valueText = if (Math.round(hintTextStroke) == 0) "关闭" else "${Math.round(hintTextStroke)}dp",
                                        value = Math.round(hintTextStroke).toFloat(),
                                        valueRange = 0f..4f,
                                        onValueChange = { hintTextStroke = Math.round(it).coerceIn(0, 4).toFloat() },
                                    )
                                    SliderPreference(
                                        title = "文字阴影",
                                        summary = "黑色柔光投影，弱壁纸背景下提升可读性；半径 = 强度/20×4px",
                                        valueText = if (hintTextShadow < 0.5f) "关闭" else "${hintTextShadow.toInt()}",
                                        value = hintTextShadow,
                                        valueRange = 0f..100f,
                                        onValueChange = { hintTextShadow = (Math.round(it / 5f) * 5f).coerceIn(0f, 100f) },
                                    )
                                    SliderPreference(
                                        title = "标题字重",
                                        summary = when (hintTextWeight) {
                                            0 -> "细体 (Light)"
                                            1 -> "常规 (Regular)"
                                            2 -> "中粗 (Medium)"
                                            else -> "粗体 (Bold)"
                                        },
                                        valueText = when (hintTextWeight) {
                                            0 -> "Light"
                                            1 -> "Regular"
                                            2 -> "Medium"
                                            else -> "Bold"
                                        },
                                        value = hintTextWeight.toFloat(),
                                        valueRange = 0f..3f,
                                        onValueChange = { hintTextWeight = it.toInt() },
                                    )
                                    SliderPreference(
                                        title = "字间距",
                                        summary = "提示文字字间距 -2~+6（×0.05em），字母文案调宽更精致",
                                        valueText = String.format("%+.1f em", hintTextLetterSpacing),
                                        value = hintTextLetterSpacing,
                                        valueRange = -2f..6f,
                                        onValueChange = { hintTextLetterSpacing = (Math.round(it * 2f) / 2f).coerceIn(-2f, 6f) },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            text = "完成",
                            onClick = { showTextDialog = false },
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 预选动作列表项数据与弹窗
// ─────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────
// FOLDER 子快捷方式卡片列表编辑器（替代原「子快捷方式 JSON」文本框）
// ─────────────────────────────────────────────────────────────────────

/**
 * 聚合文件夹的子快捷方式卡片列表：
 *  - 轻触卡片：进入子快捷方式参数编辑（经 onEditChild 回调）；
 *  - 长按拖拽：子项交换排序；
 *  - 长按不动：多选后复制 / 删除；
 *  - 头部按钮：从预设添加（排除文件夹，不支持嵌套）/ 新建空白子项。
 */
@Composable
private fun FolderChildrenEditor(
    children: androidx.compose.runtime.snapshots.SnapshotStateList<ShortcutItem>,
    existingShortcuts: List<ShortcutItem>,
    onEditChild: (ShortcutItem) -> Unit,
    onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val state = remember { ShortcutListInteractionState() }
    var showPresetDialog by remember { mutableStateOf(false) }
    var showExistingPicker by remember { mutableStateOf(false) }

    fun childNodes(): List<ShortcutRowNode> {
        val seen = HashSet<String>()
        return children.mapIndexed { idx, it ->
            var k = "c:${it.id}"
            if (!seen.add(k)) {
                k = "c:${it.id}#$idx"
                seen.add(k)
            }
            ShortcutRowNode(k, "children", it, 1, null)
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        // ── 头部：标题与提示 ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "子快捷方式 (${children.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "长按拖拽排序",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        // ── 三种添加入口（均分宽度按钮组，带图标，绝不拥挤挤压）──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChildAddActionButton(
                title = "从已有添加",
                icon = MiuixIcons.Copy,
                modifier = Modifier.weight(1f),
                onClick = { showExistingPicker = true },
            )
            ChildAddActionButton(
                title = "从预设添加",
                icon = MiuixIcons.Tune,
                modifier = Modifier.weight(1f),
                onClick = { showPresetDialog = true },
            )
            ChildAddActionButton(
                title = "空白子项",
                icon = MiuixIcons.Add,
                modifier = Modifier.weight(1f),
                onClick = {
                    children.add(newBlankChildShortcut())
                    onChanged()
                },
            )
        }

        // ── 多选操作条（内联）──
        if (state.multiSelect) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已选 ${state.selection.size} 项",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    text = "复制 (${state.selection.size})",
                    onClick = {
                        val selected = children.filter { "c:${it.id}" in state.selection }
                        for (item in selected.asReversed()) {
                            val i = children.indexOfFirst { it.id == item.id }
                            if (i >= 0) children.add(i + 1, duplicateShortcut(item))
                        }
                        onChanged()
                        state.clearSelection()
                        android.widget.Toast.makeText(ctx, "已复制 ${selected.size} 个子快捷方式", android.widget.Toast.LENGTH_SHORT).show()
                    },
                )
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    text = "删除 (${state.selection.size})",
                    onClick = {
                        val n = state.selection.size
                        children.removeAll { "c:${it.id}" in state.selection }
                        onChanged()
                        state.clearSelection()
                        android.widget.Toast.makeText(ctx, "已删除 $n 个子快捷方式", android.widget.Toast.LENGTH_SHORT).show()
                    },
                )
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(text = "取消", onClick = { state.clearSelection() })
            }
        }

        if (children.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "暂无子快捷方式。点击「从已有添加」复制列表里的快捷方式进来，或「从预设添加」/「空白子项」创建；卡片轻触编辑参数，长按拖拽排序，长按不动多选后可复制 / 删除。",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Spacer(Modifier.height(8.dp))
            val nodes = childNodes()
            children.forEachIndexed { index, child ->
                val node = nodes[index]
                key(node.key) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .onGloballyPositioned { coordinates ->
                                val topY = coordinates.positionInParent().y
                                val h = coordinates.size.height.toFloat()
                                state.rowBounds[node.key] = topY to h
                                state.rowHeights[node.key] = h
                                state.rowWindowY[node.key] = coordinates.positionInWindow().y
                            }
                            .shortcutRowGestures(
                                state = state,
                                nodeKey = node.key,
                                getNodes = ::childNodes,
                                move = { n, dir ->
                                    val i = children.indexOfFirst { it.id == n.item.id }
                                    val j = i + dir
                                    if (i >= 0 && j in children.indices) {
                                        val tmp = children[i]
                                        children[i] = children[j]
                                        children[j] = tmp
                                        onChanged()
                                    }
                                },
                                onLongPressNoMove = { state.toggleSelection(node.key) },
                            )
                            .dragRowVisuals(state, node, nodes, enableSwapAnimation = true),
                    ) {
                        ShortcutCard(
                            item = child,
                            selected = node.key in state.selection,
                            endActions = {
                                Switch(
                                    checked = child.enabled,
                                    onCheckedChange = { on ->
                                        if (!state.clickSuppressed()) {
                                            // 必须整体替换元素触发重组，不能原地改 Java 字段
                                            children[index] = child.copy().also { it.enabled = on }
                                            onChanged()
                                        }
                                    },
                                )
                            },
                            onClick = {
                                if (!state.clickSuppressed()) {
                                    if (state.multiSelect) {
                                        state.toggleSelection(node.key)
                                    } else {
                                        onEditChild(child)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showPresetDialog) {
        PresetActionPickerDialog(
            onDismiss = { showPresetDialog = false },
            onSelect = { preset ->
                val child = ShortcutItem(
                    java.util.UUID.randomUUID().toString(),
                    preset.title,
                    preset.subtitle,
                    preset.actionType,
                    preset.actionPayload,
                    preset.iconPackage,
                    preset.iconGlyph,
                    preset.tintColor,
                    true,
                    preset.glyphPadEnabled,
                    preset.glyphPadColor,
                    preset.glyphPadMode,
                )
                child.userId = preset.userId
                child.shellUseRoot = preset.shellUseRoot
                if (child.actionType == ShortcutItem.ACTION_INTENT_URI) {
                    child.actionPayload = ShortcutItem.syncUserMarker(child.actionPayload, preset.userId)
                }
                children.add(child)
                onChanged()
                showPresetDialog = false
            },
            filter = { it.actionType != ShortcutItem.ACTION_FOLDER },
        )
    }

    // ── 从已有快捷方式添加（复制语义：新 id、标题不变；排除聚合文件夹与已在本列表的项）──
    if (showExistingPicker) {
        val candidates = existingShortcuts.filter {
            it.actionType != ShortcutItem.ACTION_FOLDER && children.none { c -> c.id == it.id }
        }
        Dialog(
            onDismissRequest = { showExistingPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(modifier = Modifier.fillMaxWidth(0.92f)) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "从已有快捷方式添加（复制为子项）",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    if (candidates.isEmpty()) {
                        Text(
                            text = "没有可添加的快捷方式。已在子列表中的项和聚合文件夹不可添加。",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            lineHeight = 19.sp,
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .height(360.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            items(candidates.size) { i ->
                                val item = candidates[i]
                                ShortcutCard(
                                    item = item,
                                    summaryOverride = item.subtitle.ifBlank { "轻触右侧按钮添加为子项" },
                                    endActions = {
                                        TextButton(
                                            text = "添加",
                                            onClick = {
                                                val copy = item.copy().also { it.id = java.util.UUID.randomUUID().toString() }
                                                children.add(copy)
                                                onChanged()
                                                showExistingPicker = false
                                                android.widget.Toast.makeText(ctx, "已添加「${item.title}」", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    },
                                    onClick = {
                                        val copy = item.copy().also { it.id = java.util.UUID.randomUUID().toString() }
                                        children.add(copy)
                                        onChanged()
                                        showExistingPicker = false
                                        android.widget.Toast.makeText(ctx, "已添加「${item.title}」", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(text = "关闭", onClick = { showExistingPicker = false })
                    }
                }
            }
        }
    }
}

/**
 * 聚合文件夹子项添加动作卡片按钮（均分宽度、带图标，保证窄屏下绝不挤压变形）。
 */
@Composable
private fun ChildAddActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
