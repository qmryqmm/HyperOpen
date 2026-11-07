package io.github.qmryqmm.hyperopen.ui.edit

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import io.github.qmryqmm.hyperopen.ui.IconPainter
import io.github.qmryqmm.hyperopen.ui.MiuixIconRegistry
import io.github.qmryqmm.hyperopen.ui.RemixIconRegistry
import io.github.qmryqmm.hyperopen.ui.components.toComposeImageBitmap
import io.github.qmryqmm.hyperopen.view.IconGlyphFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 快捷方式动作与图标选择弹窗集合。
 * 包含：
 *   1. [PresetActionPickerDialog] 常用快捷方式预设选择弹窗
 *   2. [AppListPickerDialog] 已安装应用选择弹窗
 *   3. [ActivityPickerDialog] 应用内 Activity 组件精准定位弹窗
 *   4. [MiuixIconPickerDialog] MIUIX 官方扩展矢量图标选择弹窗
 *   5. [RemixIconPickerDialog] Remix Icon 矢量图标网格选择弹窗
 */

data class PresetAction(
    val title: String,
    val subtitle: String,
    val actionType: String,
    val actionPayload: String = "",
    val userId: Int = 0,
    val shellUseRoot: Boolean = false,
    val iconGlyph: String = "",
    val iconPackage: String = "",
    val tintColor: Int = 0xFF2A82E4.toInt(),
    val glyphPadEnabled: Boolean = true,
    val glyphPadColor: Int = 0xFF2A82E4.toInt(),
    val glyphPadMode: Int = 1,
    val tag: String = "",
)

val PRESET_ACTIONS = listOf(
    PresetAction(
        title = "自定义快捷方式",
        subtitle = "自由配置目标应用、自定义 Intent 或 Shell",
        actionType = ShortcutItem.ACTION_APP,
        iconGlyph = "remix:sparkling-fill",
        tintColor = 0xFF3482FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF3482FF.toInt(),
        glyphPadMode = 1,
        tag = "自定义",
    ),
    PresetAction(
        title = "微信快捷聚合 (文件夹)",
        subtitle = "长按悬停 250ms 展开扫一扫、付款码与收款码",
        actionType = ShortcutItem.ACTION_FOLDER,
        actionPayload = "[{\"id\":\"wx_scan\",\"title\":\"微信扫一扫\",\"subtitle\":\"扫码识别\",\"actionType\":\"INTENT_URI\",\"actionPayload\":\"intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10000000;package=com.tencent.mm;component=com.tencent.mm/.plugin.scanner.ui.BaseScanUI;end\",\"iconGlyph\":\"remix:qr-scan-2-line\",\"tintColor\":-16268960,\"glyphPadEnabled\":true,\"glyphPadColor\":-16268960,\"glyphPadMode\":1,\"appIconCropMode\":2,\"enabled\":true},{\"id\":\"wx_pay\",\"title\":\"微信付款码\",\"subtitle\":\"商家付款\",\"actionType\":\"INTENT_URI\",\"actionPayload\":\"intent:#Intent;launchFlags=0x10000000;component=com.tencent.mm/com.tencent.mm.plugin.offline.ui.WalletOfflineCoinPurseUI;i.key_entry_scene=2;end\",\"iconGlyph\":\"remix:qr-code-line\",\"tintColor\":-16268960,\"glyphPadEnabled\":true,\"glyphPadColor\":-16268960,\"glyphPadMode\":1,\"appIconCropMode\":2,\"enabled\":true},{\"id\":\"wx_receive\",\"title\":\"微信收款码\",\"subtitle\":\"面对面收款\",\"actionType\":\"INTENT_URI\",\"actionPayload\":\"intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10000000;package=com.tencent.mm;component=com.tencent.mm/.plugin.collect.ui.CollectMainUI;end\",\"iconGlyph\":\"remix:money-cny-circle-line\",\"tintColor\":-16268960,\"glyphPadEnabled\":true,\"glyphPadColor\":-16268960,\"glyphPadMode\":1,\"appIconCropMode\":2,\"enabled\":true}]",
        iconGlyph = "remix:folder-fill",
        tintColor = 0xFF07C160.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF07C160.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "支付宝快捷聚合 (文件夹)",
        subtitle = "长按悬停 250ms 展开扫一扫、乘车码与付款码",
        actionType = ShortcutItem.ACTION_FOLDER,
        actionPayload = "[{\"id\":\"alipay_scan\",\"title\":\"支付宝扫一扫\",\"subtitle\":\"扫码识别\",\"actionType\":\"INTENT_URI\",\"actionPayload\":\"intent://platformapi/startapp?appId=10000007#Intent;scheme=alipays;package=com.eg.android.AlipayGphone;end\",\"iconGlyph\":\"remix:qr-scan-2-line\",\"tintColor\":-15304961,\"glyphPadEnabled\":true,\"glyphPadColor\":-15304961,\"glyphPadMode\":1,\"appIconCropMode\":2,\"enabled\":true},{\"id\":\"bus_code\",\"title\":\"乘车码\",\"subtitle\":\"地铁公交一卡通\",\"actionType\":\"INTENT_URI\",\"actionPayload\":\"intent://platformapi/startapp?appId=200011235#Intent;scheme=alipays;package=com.eg.android.AlipayGphone;end\",\"iconGlyph\":\"remix:subway-fill\",\"tintColor\":-11352806,\"glyphPadEnabled\":true,\"glyphPadColor\":-11352806,\"glyphPadMode\":1,\"appIconCropMode\":2,\"enabled\":true},{\"id\":\"alipay_pay\",\"title\":\"支付宝付款码\",\"subtitle\":\"收银台付款\",\"actionType\":\"INTENT_URI\",\"actionPayload\":\"alipays://platformapi/startapp?appId=20000056&chInfo=ch_xiaomiLeft\",\"iconGlyph\":\"remix:qr-code-fill\",\"tintColor\":-15304961,\"glyphPadEnabled\":true,\"glyphPadColor\":-15304961,\"glyphPadMode\":1,\"appIconCropMode\":2,\"enabled\":true}]",
        iconGlyph = "remix:folder-fill",
        tintColor = 0xFF1677FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1677FF.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "唤醒小爱",
        subtitle = "一键唤醒语音助手与小爱同学",
        actionType = ShortcutItem.ACTION_XIAOAI,
        iconGlyph = "remix:mic-fill",
        tintColor = 0xFF1485EE.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1485EE.toInt(),
        glyphPadMode = 1,
        tag = "系统助手",
    ),
    PresetAction(
        title = "静音模式",
        subtitle = "快速切换响铃与静音震动",
        actionType = ShortcutItem.ACTION_MUTE,
        iconGlyph = "remix:volume-mute-fill",
        tintColor = 0xFFFA8C16.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFFFA8C16.toInt(),
        glyphPadMode = 1,
        tag = "系统开关",
    ),
    PresetAction(
        title = "勿扰模式",
        subtitle = "快速开启或退出静音勿扰模式",
        actionType = ShortcutItem.ACTION_DND,
        iconGlyph = "remix:notification-3-fill",
        tintColor = 0xFF722ED1.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF722ED1.toInt(),
        glyphPadMode = 1,
        tag = "系统开关",
    ),
    PresetAction(
        title = "强光手电筒",
        subtitle = "开关手机后置 LED 强光照明",
        actionType = ShortcutItem.ACTION_FLASHLIGHT,
        iconGlyph = "remix:flashlight-fill",
        tintColor = 0xFFFAAD14.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFFFAAD14.toInt(),
        glyphPadMode = 1,
        tag = "常用工具",
    ),
    PresetAction(
        title = "街拍相机",
        subtitle = "秒级直达系统原生相机抓拍界面",
        actionType = ShortcutItem.ACTION_CAMERA,
        iconGlyph = "remix:camera-fill",
        tintColor = 0xFFFF4D4F.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFFFF4D4F.toInt(),
        glyphPadMode = 1,
        tag = "系统相机",
    ),
    PresetAction(
        title = "微信扫一扫",
        subtitle = "直接唤起微信官方扫码镜头",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10000000;package=com.tencent.mm;component=com.tencent.mm/.plugin.scanner.ui.BaseScanUI;end",
        // userId 默认 0 = 主空间；要启动应用双开/分身（XSpace user 999）在编辑界面「启动用户」里选择
        userId = 0,
        iconGlyph = "remix:qr-scan-2-line",
        tintColor = 0xFF07C160.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF07C160.toInt(),
        glyphPadMode = 1,
        tag = "快捷扫码",
    ),
    PresetAction(
        title = "微信付款码",
        subtitle = "出示收银台向商家付款",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;launchFlags=0x10000000;component=com.tencent.mm/com.tencent.mm.plugin.offline.ui.WalletOfflineCoinPurseUI;i.key_entry_scene=2;end",
        iconGlyph = "remix:qr-code-line",
        tintColor = 0xFF07C160.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF07C160.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "微信收款码",
        subtitle = "快速出示面对面二维码收款",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10000000;package=com.tencent.mm;component=com.tencent.mm/.plugin.collect.ui.CollectMainUI;end",
        iconGlyph = "remix:money-cny-circle-line",
        tintColor = 0xFF07C160.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF07C160.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "微信朋友圈",
        subtitle = "直接打开微信朋友圈主页",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;launchFlags=0x14000000;component=com.tencent.mm/.plugin.sns.ui.SnsTimeLineUI;B.sns_timeline_NeedFirstLoadint=true;end",
        iconGlyph = "remix:image-2-fill",
        tintColor = 0xFF07C160.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF07C160.toInt(),
        glyphPadMode = 1,
        tag = "社交娱乐",
    ),
    PresetAction(
        title = "微信名片",
        subtitle = "展示微信个人二维码名片",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x14000000;package=com.tencent.mm;component=com.tencent.mm/.plugin.setting.ui.setting.SelfQRCodeUI;end",
        iconGlyph = "remix:user-3-fill",
        tintColor = 0xFF07C160.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF07C160.toInt(),
        glyphPadMode = 1,
        tag = "社交娱乐",
    ),
    PresetAction(
        title = "微信群收款",
        subtitle = "快速发起微信AA分账收款",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10000000;package=com.tencent.mm;component=com.tencent.mm/.plugin.aa.ui.AAEntranceUI;end",
        iconGlyph = "remix:money-cny-circle-fill",
        tintColor = 0xFF07C160.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF07C160.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "支付宝扫一扫",
        subtitle = "快速识别二维码或扫码骑行",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "alipays://platformapi/startapp?appId=10000007&sourceId=xiaomiLeft&source=shortcut&showOthers=true&chInfo=ch_xiaomiLeft",
        iconGlyph = "remix:qr-scan-2-line",
        tintColor = 0xFF1677FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1677FF.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "支付宝付款码",
        subtitle = "直接打开支付宝付款条码",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "alipays://platformapi/startapp?appId=20000056&chInfo=ch_xiaomiLeft",
        iconGlyph = "remix:qr-code-fill",
        tintColor = 0xFF1677FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1677FF.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "支付宝收款码",
        subtitle = "快速展示支付宝面对面二维码收钱",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "alipays://platformapi/startapp?appId=20000123&chInfo=ch_xiaomi_left&sceneCode=KF_CHANGSHANG&shareUserId=2088831085791813&partnerId=ch_xiaomi_left&pikshemo=YES",
        iconGlyph = "remix:money-cny-circle-fill",
        tintColor = 0xFF1677FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1677FF.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "乘车码",
        subtitle = "地铁与公交交通一卡通直接刷码",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "alipays://platformapi/startapp?appId=200011235&source=xiaomiLeft&chInfo=ch_xiaomiLeft",
        iconGlyph = "remix:subway-fill",
        tintColor = 0xFF52C41A.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF52C41A.toInt(),
        glyphPadMode = 1,
        tag = "绿色出行",
    ),
    PresetAction(
        title = "支付宝市民中心",
        subtitle = "政务办事、社保医保公积金",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "alipays://platformapi/startapp?appId=20000178&chInfo=ch_xiaomi_left&sceneCode=KF_CHANGSHANG&shareUserId=2088831085791813&partnerId=ch_xiaomi_left&pikshemo=YES",
        iconGlyph = "remix:shield-check-fill",
        tintColor = 0xFF1677FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1677FF.toInt(),
        glyphPadMode = 1,
        tag = "生活服务",
    ),
    PresetAction(
        title = "支付宝生活缴费",
        subtitle = "水费、电费、燃气费便民缴费",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "alipays://platformapi/startapp?appId=20000193&chInfo=ch_xiaomi_left&sceneCode=KF_CHANGSHANG&shareUserId=2088831085791813&partnerId=ch_xiaomi_left&pikshemo=YES",
        iconGlyph = "remix:coupon-3-fill",
        tintColor = 0xFF1677FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1677FF.toInt(),
        glyphPadMode = 1,
        tag = "生活服务",
    ),
    PresetAction(
        title = "系统扫一扫",
        subtitle = "小米系统原生扫码器",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=miui.intent.action.scanner;launchFlags=0x10000000;component=com.xiaomi.scanner/.app.ScanActivity;i.extra_intent_module_index=0;S.fromApp=com.miui.personalassistant;S.miref=PersonalAssistant_QuickStart;end",
        iconGlyph = "remix:qr-scan-2-fill",
        tintColor = 0xFF3482FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF3482FF.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "扫文档",
        subtitle = "小米扫一扫快速提取文档",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=miui.intent.action.scanner;launchFlags=0x10000000;component=com.xiaomi.scanner/.app.ScanActivity;i.extra_intent_module_index=5;S.fromApp=com.miui.personalassistant;S.miref=PersonalAssistant_QuickStart;end",
        iconGlyph = "remix:sticky-note-fill",
        tintColor = 0xFF3482FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF3482FF.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "扫一扫翻译",
        subtitle = "小米扫一扫实时拍照翻译",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=miui.intent.action.scanner;launchFlags=0x10000000;component=com.xiaomi.scanner/.app.ScanActivity;i.extra_intent_module_index=2;S.fromApp=com.miui.personalassistant;S.miref=PersonalAssistant_QuickStart;end",
        iconGlyph = "remix:translate-2",
        tintColor = 0xFF3482FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF3482FF.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "垃圾清理",
        subtitle = "安全中心一键扫描垃圾清理",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=miui.intent.action.GARBAGE_CLEANUP;category=android.intent.category.DEFAULT;launchFlags=0x10000000;component=com.miui.cleanmaster/com.miui.optimizecenter.MainActivity;end",
        iconGlyph = "remix:delete-bin-6-fill",
        tintColor = 0xFF52C41A.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF52C41A.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "科学计算器",
        subtitle = "小米计算器高级科学换算",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;launchFlags=0x10080000;component=com.miui.calculator/.cal.AllInOneCalculatorActivity;B.extra_is_scientific=true;S.miref=PersonalAssistant_QuickStart;end",
        iconGlyph = "remix:calculator-fill",
        tintColor = 0xFF13C2C2.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF13C2C2.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "汇率计算",
        subtitle = "即时多国汇率实时换算",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=com.miui.calculator.action.CURRENCY;launchFlags=0x10080000;component=com.miui.calculator/.convert.CurrencyActivity;S.external_jump_type=personalassistant;S.miref=PersonalAssistant_QuickStart;end",
        iconGlyph = "remix:exchange-funds-fill",
        tintColor = 0xFF13C2C2.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF13C2C2.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "新的录音",
        subtitle = "快速开启系统高保真录音",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=mi.quicksearch.intent.action.RETRIEVE;launchFlags=0x10000000;component=com.android.soundrecorder/.SoundRecorder;S.miref=PersonalAssistant_QuickStart;end",
        iconGlyph = "remix:disc-fill",
        tintColor = 0xFFFAAD14.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFFFAAD14.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "新建日程",
        subtitle = "日历快速创建待办与活动",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=android.intent.action.INSERT;category=android.intent.category.DEFAULT;type=vnd.android.cursor.item/event;package=com.android.calendar;end",
        iconGlyph = "remix:calendar-event-fill",
        tintColor = 0xFF1890FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1890FF.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "Mi Pay",
        subtitle = "小米智能卡银行卡刷卡界面",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=com.miui.intent.action.DOUBLE_CLICK;launchFlags=0x10000000;i.card_group_id=2;end",
        iconGlyph = "remix:bank-card-fill",
        tintColor = 0xFFFA541C.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFFFA541C.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "交通卡",
        subtitle = "刷公交地铁一卡通卡面",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=com.miui.intent.action.DOUBLE_CLICK;launchFlags=0x10000000;S.event_source=shortcut_of_trans_card;end",
        iconGlyph = "remix:subway-fill",
        tintColor = 0xFF1890FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1890FF.toInt(),
        glyphPadMode = 1,
        tag = "绿色出行",
    ),
    PresetAction(
        title = "门卡",
        subtitle = "快速调出小区/门禁实体模拟卡",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=com.miui.intent.action.DOUBLE_CLICK;launchFlags=0x10000000;i.card_group_id=3;end",
        iconGlyph = "remix:lock-unlock-fill",
        tintColor = 0xFF52C41A.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF52C41A.toInt(),
        glyphPadMode = 1,
        tag = "生活服务",
    ),
    PresetAction(
        title = "淘宝拍立淘",
        subtitle = "淘宝拍照图像识物查同款",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "taobao://h5.m.taobao.com/tusou/capture.html?pssource=xiaomi&bc_fl_src=growth_dhh_2200803434487_android-xiaomikuaijie-1069565676-xiaomikuaijie",
        iconGlyph = "remix:shopping-bag-3-fill",
        tintColor = 0xFFFF5000.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFFFF5000.toInt(),
        glyphPadMode = 1,
        tag = "生活服务",
    ),
)

@Composable
fun PresetActionPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (PresetAction) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = "新建快捷方式",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "提供丰富预选常用动作，轻触即可自动填充配置",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(PRESET_ACTIONS) { action ->
                        val vector = remember(action.iconGlyph) {
                            if (RemixIconRegistry.isRemixIcon(action.iconGlyph)) {
                                RemixIconRegistry.find(action.iconGlyph)
                            } else {
                                MiuixIconRegistry.find(action.iconGlyph)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MiuixTheme.colorScheme.surfaceVariant)
                                .clickable { onSelect(action) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(action.glyphPadColor).copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (vector != null) {
                                    Icon(
                                        imageVector = vector,
                                        contentDescription = null,
                                        tint = Color(action.tintColor),
                                        modifier = Modifier.size(20.dp),
                                    )
                                } else {
                                    Text("⚡", fontSize = 16.sp)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = action.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                    if (action.tag.isNotBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                text = action.tag,
                                                fontSize = 10.sp,
                                                color = MiuixTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = action.subtitle,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                "选用",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 应用选择弹窗组件
// ─────────────────────────────────────────────────────────────────────

data class AppInfoItem(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val icon: android.graphics.drawable.Drawable?,
)

@Composable
fun AppListPickerDialog(
    onDismiss: () -> Unit,
    onSelectApp: (pkg: String, name: String) -> Unit,
) {
    val ctx = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableIntStateOf(0) } // 0=全部, 1=用户应用, 2=系统应用
    var allApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val installedPackages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            val list = installedPackages.mapNotNull { pi ->
                val appInfo = pi.applicationInfo ?: return@mapNotNull null
                val pkg = pi.packageName
                val name = appInfo.loadLabel(pm).toString().ifBlank { pkg }
                val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val icon = try {
                    appInfo.loadIcon(pm)
                } catch (t: Throwable) {
                    null
                }
                AppInfoItem(pkg, name, isSys, icon)
            }.distinctBy { it.packageName }
                .sortedWith(compareBy({ it.appName.lowercase() }, { it.packageName }))
            withContext(Dispatchers.Main) {
                allApps = list
                isLoading = false
            }
        }
    }

    val filteredApps = remember(searchQuery, filterTab, allApps) {
        var base = when (filterTab) {
            1 -> allApps.filter { !it.isSystem }
            2 -> allApps.filter { it.isSystem }
            else -> allApps
        }
        if (searchQuery.isNotBlank()) {
            base = base.filter {
                it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        base
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = "选择应用程序",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "选取应用作为快捷图标或启动目标",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TabRow(
                    tabs = listOf("全部 (${allApps.size})", "用户应用", "系统应用"),
                    selectedTabIndex = filterTab,
                    onTabSelected = { filterTab = it },
                    colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "搜索应用名称或包名...",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("正在加载已安装应用...", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                    ) {
                        items(filteredApps.size, key = { filteredApps[it].packageName }) { idx ->
                            val app = filteredApps[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectApp(app.packageName, app.appName) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (app.icon != null) {
                                    val bmp = remember(app.packageName) {
                                        val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                                        val c = android.graphics.Canvas(b)
                                        app.icon.setBounds(0, 0, 96, 96)
                                        app.icon.draw(c)
                                        b.toComposeImageBitmap()
                                    }
                                    Image(
                                        painter = BitmapPainter(bmp),
                                        contentDescription = app.appName,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MiuixTheme.colorScheme.surfaceVariant),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.appName,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (app.isSystem) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "系统",
                                                fontSize = 10.sp,
                                                color = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .background(
                                                        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                        RoundedCornerShape(4.dp),
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = app.packageName,
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

data class ActivityInfoItem(
    val className: String,
    val exported: Boolean,
    val label: String,
)

/** 应用 → 活动列表两级选择器：点活动直接填入 目标组件/目标包名 */
@Composable
fun ActivityPickerDialog(
    onDismiss: () -> Unit,
    onPick: (pkg: String, cls: String, appName: String) -> Unit,
) {
    val ctx = LocalContext.current
    var selectedApp by remember { mutableStateOf<AppInfoItem?>(null) }
    var appSearch by remember { mutableStateOf("") }
    var filterTab by remember { mutableIntStateOf(0) } // 0=全部, 1=用户应用, 2=系统应用
    var allApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var activities by remember { mutableStateOf<List<ActivityInfoItem>>(emptyList()) }
    var actLoading by remember { mutableStateOf(false) }
    var actSearch by remember { mutableStateOf("") }
    var actFilter by remember { mutableIntStateOf(0) } // 0=全部, 1=已导出, 2=未导出

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val installedPackages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            val list = installedPackages.mapNotNull { pi ->
                val appInfo = pi.applicationInfo ?: return@mapNotNull null
                val pkg = pi.packageName
                val name = appInfo.loadLabel(pm).toString().ifBlank { pkg }
                val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val icon = try {
                    appInfo.loadIcon(pm)
                } catch (t: Throwable) {
                    null
                }
                AppInfoItem(pkg, name, isSys, icon)
            }.distinctBy { it.packageName }
                .sortedWith(compareBy({ it.appName.lowercase() }, { it.packageName }))
            withContext(Dispatchers.Main) {
                allApps = list
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedApp) {
        val app = selectedApp ?: return@LaunchedEffect
        actLoading = true
        activities = emptyList()
        withContext(Dispatchers.IO) {
            val list = try {
                val pm = ctx.packageManager
                @Suppress("DEPRECATION")
                val acts = pm.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES).activities ?: emptyArray()
                acts.map { ai ->
                    val label = try {
                        ai.loadLabel(pm).toString()
                    } catch (t: Throwable) {
                        ""
                    }
                    ActivityInfoItem(ai.name, ai.exported, label)
                }.sortedBy { it.className }
            } catch (t: Throwable) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                activities = list
                actLoading = false
            }
        }
    }

    val filteredApps = remember(appSearch, filterTab, allApps) {
        var base = when (filterTab) {
            1 -> allApps.filter { !it.isSystem }
            2 -> allApps.filter { it.isSystem }
            else -> allApps
        }
        if (appSearch.isNotBlank()) {
            base = base.filter {
                it.appName.contains(appSearch, ignoreCase = true) || it.packageName.contains(appSearch, ignoreCase = true)
            }
        }
        base
    }

    val filteredActs = remember(actSearch, actFilter, activities) {
        var base = when (actFilter) {
            1 -> activities.filter { it.exported }
            2 -> activities.filter { !it.exported }
            else -> activities
        }
        if (actSearch.isNotBlank()) {
            base = base.filter {
                it.className.contains(actSearch, ignoreCase = true) ||
                    it.label.contains(actSearch, ignoreCase = true)
            }
        }
        base
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                val app = selectedApp
                if (app == null) {
                    Text(
                        text = "选择应用程序",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "点击应用查看其活动列表，点活动直接填入组件参数",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    TabRow(
                        tabs = listOf("全部 (${allApps.size})", "用户应用", "系统应用"),
                        selectedTabIndex = filterTab,
                        onTabSelected = { filterTab = it },
                        colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent),
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    TextField(
                        value = appSearch,
                        onValueChange = { appSearch = it },
                        label = "搜索应用名称或包名...",
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("正在加载已安装应用...", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                        ) {
                            items(filteredApps.size, key = { "app-" + filteredApps[it].packageName }) { idx ->
                                val item = filteredApps[idx]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedApp = item }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (item.icon != null) {
                                        val bmp = remember(item.packageName) {
                                            val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                                            val c = android.graphics.Canvas(b)
                                            item.icon.setBounds(0, 0, 96, 96)
                                            item.icon.draw(c)
                                            b.toComposeImageBitmap()
                                        }
                                        Image(
                                            painter = BitmapPainter(bmp),
                                            contentDescription = item.appName,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MiuixTheme.colorScheme.surfaceVariant),
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.appName,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MiuixTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (item.isSystem) {
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "系统",
                                                    fontSize = 10.sp,
                                                    color = MiuixTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .background(
                                                            MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                            RoundedCornerShape(4.dp),
                                                        )
                                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                                )
                                            }
                                        }
                                        Text(
                                            text = item.packageName,
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            text = "← 返回",
                            onClick = {
                                selectedApp = null
                                activities = emptyList()
                                actSearch = ""
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = app.appName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "点活动直接填入 目标组件 / 目标包名（共 ${activities.size} 个活动）",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    TabRow(
                        tabs = listOf("全部 (${activities.size})", "已导出", "未导出"),
                        selectedTabIndex = actFilter,
                        onTabSelected = { actFilter = it },
                        colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent),
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    TextField(
                        value = actSearch,
                        onValueChange = { actSearch = it },
                        label = "搜索活动类名或标签...",
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                    if (actLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("正在加载活动列表...", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    } else if (activities.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("该应用未声明任何活动（或无法读取）", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                        ) {
                            items(filteredActs.size, key = { "act-" + filteredActs[it].className }) { idx ->
                                val act = filteredActs[idx]
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(app.packageName, act.className, app.appName) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = act.className,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (act.exported) "导出" else "未导出",
                                            fontSize = 10.sp,
                                            color = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .background(
                                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                    RoundedCornerShape(4.dp),
                                                )
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                        )
                                    }
                                    if (act.label.isNotBlank()) {
                                        Text(
                                            text = act.label,
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Miuix 矢量图标选择弹窗组件（完整 125+ 图标库 + 5 档粗细切换 + 搜索）
// ─────────────────────────────────────────────────────────────────────

@Composable
fun MiuixIconPickerDialog(
    currentIconWithWeight: String,
    onDismiss: () -> Unit,
    onSelectIcon: (String) -> Unit,
) {
    val initialWeight = remember(currentIconWithWeight) {
        if (currentIconWithWeight.contains(":")) currentIconWithWeight.split(":")[1].trim() else "Regular"
    }
    val initialName = remember(currentIconWithWeight) {
        if (currentIconWithWeight.contains(":")) currentIconWithWeight.split(":")[0].trim() else currentIconWithWeight.trim()
    }

    var selectedWeight by remember { mutableStateOf(initialWeight) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredNames = remember(searchQuery) {
        if (searchQuery.isBlank()) MiuixIconRegistry.ICON_NAMES
        else MiuixIconRegistry.ICON_NAMES.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    val weightIndex = remember(selectedWeight) {
        MiuixIconRegistry.WEIGHTS.indexOf(selectedWeight).coerceAtLeast(0)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = "选择 Miuix 矢量图标",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "支持 125+ 完整官方矢量库，并可调节线条粗细",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // 粗细字重切换器
                Text(
                    text = "图标粗细：$selectedWeight",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiuixIconRegistry.WEIGHTS.forEach { weight ->
                        val isSelected = weight == selectedWeight
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedWeight = weight }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = weight,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "搜索图标名称（如 Scan, Search, Settings...）",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredNames, key = { it }) { name ->
                        val isSelected = name.equals(initialName, ignoreCase = true)
                        val vector = remember(name, selectedWeight) {
                            MiuixIconRegistry.find(name, selectedWeight)
                        }

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else MiuixTheme.colorScheme.surfaceVariant,
                                )
                                .clickable {
                                    val finalKey = "$name:$selectedWeight"
                                    onSelectIcon(finalKey)
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (vector != null) {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = name,
                                    tint = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Remix Icon 矢量图标选择弹窗组件（128款高频锁屏精选 + 分类标签 + 实时搜索）
// ─────────────────────────────────────────────────────────────────────

@Composable
fun RemixIconPickerDialog(
    currentIconKey: String,
    onDismiss: () -> Unit,
    onSelectIcon: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("全部") }

    val categories = remember {
        listOf("全部", "核心高频", "系统控制", "支付卡证", "出行导航", "影音娱乐", "通讯工具")
    }

    val filteredIcons = remember(searchQuery, selectedCategory) {
        RemixIconRegistry.ALL_ICONS.filter { item ->
            val matchQuery = if (searchQuery.isBlank()) true else {
                item.desc.contains(searchQuery, ignoreCase = true) ||
                item.rawName.contains(searchQuery, ignoreCase = true)
            }
            val matchCat = when (selectedCategory) {
                "核心高频" -> listOf("flashlight", "qr-scan", "qr-code", "camera", "mic", "calculator", "search", "sticky-note").any { item.rawName.startsWith(it) }
                "系统控制" -> listOf("bluetooth", "wifi", "hotspot", "volume", "notification", "flight", "battery", "screenshot", "lock", "settings").any { item.rawName.startsWith(it) }
                "支付卡证" -> listOf("wallet", "bank-card", "contactless", "shield", "coupon", "shopping", "exchange", "money").any { item.rawName.startsWith(it) }
                "出行导航" -> listOf("bus", "subway", "map-pin", "navigation", "taxi", "riding", "car", "compass").any { item.rawName.startsWith(it) }
                "影音娱乐" -> listOf("music", "disc", "play", "pause", "speed", "headphone", "video", "film", "image", "book", "gamepad", "radio").any { item.rawName.startsWith(it) }
                "通讯工具" -> listOf("phone", "chat", "message", "mail", "alarm", "timer", "calendar", "translate", "earth", "share", "user", "heart", "star", "sparkling", "folder", "delete").any { item.rawName.startsWith(it) }
                else -> true
            }
            matchQuery && matchCat
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = "选择 Remix Icon 快捷图标",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "精选 128 款最高频锁屏动作与硬件开关图标，支持纯白与底垫交融渲染",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // 分类切换标签（横向滑动支持完整名称展示，杜绝挤压省略）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.forEach { cat ->
                        val isSelected = cat == selectedCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = cat,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "搜索图标名称或中文描述（如 扫一扫, 手电筒, 蓝牙...）",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredIcons, key = { it.key }) { item ->
                        val isSelected = currentIconKey == item.key || currentIconKey == item.rawName
                        val vector = remember(item.key) {
                            RemixIconRegistry.find(item.key)
                        }

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else MiuixTheme.colorScheme.surfaceVariant,
                                )
                                .clickable {
                                    onSelectIcon(item.key)
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (vector != null) {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = item.desc,
                                    tint = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.desc,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = item.rawName,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Page1：软件设置（主题入口 / 安全模式 / 关于）
// ─────────────────────────────────────────────────────────────────────

