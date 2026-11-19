package io.github.qmryqmm.hyperopen.ui.icons.glass

import androidx.compose.ui.graphics.vector.ImageVector

object MiuixGlassIconRegistry {

    val WEIGHTS = listOf("Light", "Normal", "Regular", "Medium", "Demibold")

    val ICON_NAMES: List<String> = listOf(
        "AiScan",
        "Calculator",
        "CameraOutline",
        "ControlCenter",
        "Flashlight",
        "SmallWindow",
        "Wallet",
        "WiFi7",
        "SuperXiaoai",
        "QuietMode",
        "RingtoneMuted",
        "Translate",
        "Document",
        "Currency",
        "BankCard",
        "Schedule",
        "AccessControl",
        "BluetoothDiscovery",
        "ScreenCasting",
        "Usb",
        "Location",
        "Power",
        "CameraPhone",
        "PrivacySecurity",
    )

    fun find(key: String?, weightOverride: String? = null): ImageVector? {
        if (key.isNullOrBlank()) return null

        val parts = key.trim().split(":")
        val name = parts.firstOrNull()?.trim() ?: return null
        val matchedName = ICON_NAMES.firstOrNull { it.equals(name, ignoreCase = true) } ?: return null
        val rawWeight = weightOverride ?: parts.getOrElse(1) { "Regular" }
        val weight = WEIGHTS.firstOrNull { it.equals(rawWeight, ignoreCase = true) } ?: "Regular"

        return when (matchedName) {
            "AccessControl" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.AccessControl },
                normal = { MiuixGlassIcons.Normal.AccessControl },
                regular = { MiuixGlassIcons.Regular.AccessControl },
                medium = { MiuixGlassIcons.Medium.AccessControl },
                demibold = { MiuixGlassIcons.Demibold.AccessControl },
            )
            "AiScan" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.AiScan },
                normal = { MiuixGlassIcons.Normal.AiScan },
                regular = { MiuixGlassIcons.Regular.AiScan },
                medium = { MiuixGlassIcons.Medium.AiScan },
                demibold = { MiuixGlassIcons.Demibold.AiScan },
            )
            "BankCard" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.BankCard },
                normal = { MiuixGlassIcons.Normal.BankCard },
                regular = { MiuixGlassIcons.Regular.BankCard },
                medium = { MiuixGlassIcons.Medium.BankCard },
                demibold = { MiuixGlassIcons.Demibold.BankCard },
            )
            "BluetoothDiscovery" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.BluetoothDiscovery },
                normal = { MiuixGlassIcons.Normal.BluetoothDiscovery },
                regular = { MiuixGlassIcons.Regular.BluetoothDiscovery },
                medium = { MiuixGlassIcons.Medium.BluetoothDiscovery },
                demibold = { MiuixGlassIcons.Demibold.BluetoothDiscovery },
            )
            "Calculator" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.Calculator },
                normal = { MiuixGlassIcons.Normal.Calculator },
                regular = { MiuixGlassIcons.Regular.Calculator },
                medium = { MiuixGlassIcons.Medium.Calculator },
                demibold = { MiuixGlassIcons.Demibold.Calculator },
            )
            "CameraOutline" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.CameraOutline },
                normal = { MiuixGlassIcons.Normal.CameraOutline },
                regular = { MiuixGlassIcons.Regular.CameraOutline },
                medium = { MiuixGlassIcons.Medium.CameraOutline },
                demibold = { MiuixGlassIcons.Demibold.CameraOutline },
            )
            "CameraPhone" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.CameraPhone },
                normal = { MiuixGlassIcons.Normal.CameraPhone },
                regular = { MiuixGlassIcons.Regular.CameraPhone },
                medium = { MiuixGlassIcons.Medium.CameraPhone },
                demibold = { MiuixGlassIcons.Demibold.CameraPhone },
            )
            "ControlCenter" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.ControlCenter },
                normal = { MiuixGlassIcons.Normal.ControlCenter },
                regular = { MiuixGlassIcons.Regular.ControlCenter },
                medium = { MiuixGlassIcons.Medium.ControlCenter },
                demibold = { MiuixGlassIcons.Demibold.ControlCenter },
            )
            "Currency" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.Currency },
                normal = { MiuixGlassIcons.Normal.Currency },
                regular = { MiuixGlassIcons.Regular.Currency },
                medium = { MiuixGlassIcons.Medium.Currency },
                demibold = { MiuixGlassIcons.Demibold.Currency },
            )
            "Document" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.Document },
                normal = { MiuixGlassIcons.Normal.Document },
                regular = { MiuixGlassIcons.Regular.Document },
                medium = { MiuixGlassIcons.Medium.Document },
                demibold = { MiuixGlassIcons.Demibold.Document },
            )
            "Flashlight" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.Flashlight },
                normal = { MiuixGlassIcons.Normal.Flashlight },
                regular = { MiuixGlassIcons.Regular.Flashlight },
                medium = { MiuixGlassIcons.Medium.Flashlight },
                demibold = { MiuixGlassIcons.Demibold.Flashlight },
            )
            "Location" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.Location },
                normal = { MiuixGlassIcons.Normal.Location },
                regular = { MiuixGlassIcons.Regular.Location },
                medium = { MiuixGlassIcons.Medium.Location },
                demibold = { MiuixGlassIcons.Demibold.Location },
            )
            "Power" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.Power },
                normal = { MiuixGlassIcons.Normal.Power },
                regular = { MiuixGlassIcons.Regular.Power },
                medium = { MiuixGlassIcons.Medium.Power },
                demibold = { MiuixGlassIcons.Demibold.Power },
            )
            "PrivacySecurity" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.PrivacySecurity },
                normal = { MiuixGlassIcons.Normal.PrivacySecurity },
                regular = { MiuixGlassIcons.Regular.PrivacySecurity },
                medium = { MiuixGlassIcons.Medium.PrivacySecurity },
                demibold = { MiuixGlassIcons.Demibold.PrivacySecurity },
            )
            "QuietMode" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.QuietMode },
                normal = { MiuixGlassIcons.Normal.QuietMode },
                regular = { MiuixGlassIcons.Regular.QuietMode },
                medium = { MiuixGlassIcons.Medium.QuietMode },
                demibold = { MiuixGlassIcons.Demibold.QuietMode },
            )
            "RingtoneMuted" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.RingtoneMuted },
                normal = { MiuixGlassIcons.Normal.RingtoneMuted },
                regular = { MiuixGlassIcons.Regular.RingtoneMuted },
                medium = { MiuixGlassIcons.Medium.RingtoneMuted },
                demibold = { MiuixGlassIcons.Demibold.RingtoneMuted },
            )
            "Schedule" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.Schedule },
                normal = { MiuixGlassIcons.Normal.Schedule },
                regular = { MiuixGlassIcons.Regular.Schedule },
                medium = { MiuixGlassIcons.Medium.Schedule },
                demibold = { MiuixGlassIcons.Demibold.Schedule },
            )
            "ScreenCasting" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.ScreenCasting },
                normal = { MiuixGlassIcons.Normal.ScreenCasting },
                regular = { MiuixGlassIcons.Regular.ScreenCasting },
                medium = { MiuixGlassIcons.Medium.ScreenCasting },
                demibold = { MiuixGlassIcons.Demibold.ScreenCasting },
            )
            "SmallWindow" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.SmallWindow },
                normal = { MiuixGlassIcons.Normal.SmallWindow },
                regular = { MiuixGlassIcons.Regular.SmallWindow },
                medium = { MiuixGlassIcons.Medium.SmallWindow },
                demibold = { MiuixGlassIcons.Demibold.SmallWindow },
            )
            "SuperXiaoai" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.SuperXiaoai },
                normal = { MiuixGlassIcons.Normal.SuperXiaoai },
                regular = { MiuixGlassIcons.Regular.SuperXiaoai },
                medium = { MiuixGlassIcons.Medium.SuperXiaoai },
                demibold = { MiuixGlassIcons.Demibold.SuperXiaoai },
            )
            "Translate" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.Translate },
                normal = { MiuixGlassIcons.Normal.Translate },
                regular = { MiuixGlassIcons.Regular.Translate },
                medium = { MiuixGlassIcons.Medium.Translate },
                demibold = { MiuixGlassIcons.Demibold.Translate },
            )
            "Usb" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.Usb },
                normal = { MiuixGlassIcons.Normal.Usb },
                regular = { MiuixGlassIcons.Regular.Usb },
                medium = { MiuixGlassIcons.Medium.Usb },
                demibold = { MiuixGlassIcons.Demibold.Usb },
            )
            "Wallet" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.Wallet },
                normal = { MiuixGlassIcons.Normal.Wallet },
                regular = { MiuixGlassIcons.Regular.Wallet },
                medium = { MiuixGlassIcons.Medium.Wallet },
                demibold = { MiuixGlassIcons.Demibold.Wallet },
            )
            "WiFi7" -> findWeight(
                weight,
                light = { MiuixGlassIcons.Light.WiFi7 },
                normal = { MiuixGlassIcons.Normal.WiFi7 },
                regular = { MiuixGlassIcons.Regular.WiFi7 },
                medium = { MiuixGlassIcons.Medium.WiFi7 },
                demibold = { MiuixGlassIcons.Demibold.WiFi7 },
            )
            else -> null
        }
    }

    private fun findWeight(
        weight: String,
        light: () -> ImageVector,
        normal: () -> ImageVector,
        regular: () -> ImageVector,
        medium: () -> ImageVector,
        demibold: () -> ImageVector,
    ): ImageVector = when (weight.lowercase()) {
        "light" -> light()
        "normal" -> normal()
        "medium" -> medium()
        "demibold" -> demibold()
        else -> regular()
    }
}
