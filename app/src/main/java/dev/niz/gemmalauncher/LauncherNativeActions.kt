package dev.niz.gemmalauncher

enum class NativeLauncherAction(
    val label: String,
    val openingMessage: String,
) {
    Settings("Settings", "Opening settings."),
    Notifications("Notifications", "Opening notification settings."),
    Wifi("Wi-Fi", "Opening Wi-Fi controls."),
    Internet("Internet", "Opening internet controls."),
    Bluetooth("Bluetooth", "Opening Bluetooth controls."),
    Display("Display", "Opening display settings."),
    Sound("Sound", "Opening sound controls."),
    Battery("Battery", "Opening battery settings."),
    Camera("Camera", "Opening camera."),
}

private data class NativeActionPattern(
    val action: NativeLauncherAction,
    val explicitOnly: Boolean,
    val aliases: List<String>,
)

private val NATIVE_ACTION_PATTERNS = listOf(
    NativeActionPattern(
        action = NativeLauncherAction.Settings,
        explicitOnly = false,
        aliases = listOf("settings", "system settings", "device settings")
    ),
    NativeActionPattern(
        action = NativeLauncherAction.Notifications,
        explicitOnly = false,
        aliases = listOf("notifications", "notification settings", "show notifications", "open notifications")
    ),
    NativeActionPattern(
        action = NativeLauncherAction.Wifi,
        explicitOnly = false,
        aliases = listOf("wifi", "wi fi", "wifi settings", "wireless", "wireless settings", "open wifi")
    ),
    NativeActionPattern(
        action = NativeLauncherAction.Internet,
        explicitOnly = false,
        aliases = listOf("internet", "network", "network settings", "internet settings", "connectivity", "mobile data")
    ),
    NativeActionPattern(
        action = NativeLauncherAction.Bluetooth,
        explicitOnly = false,
        aliases = listOf("bluetooth", "bt", "bluetooth settings", "open bluetooth")
    ),
    NativeActionPattern(
        action = NativeLauncherAction.Display,
        explicitOnly = false,
        aliases = listOf("display", "screen", "brightness", "display settings", "screen settings")
    ),
    NativeActionPattern(
        action = NativeLauncherAction.Sound,
        explicitOnly = false,
        aliases = listOf("sound", "volume", "audio", "sound settings", "volume settings")
    ),
    NativeActionPattern(
        action = NativeLauncherAction.Battery,
        explicitOnly = true,
        aliases = listOf("battery", "battery settings", "power", "power settings", "battery saver")
    ),
    NativeActionPattern(
        action = NativeLauncherAction.Camera,
        explicitOnly = true,
        aliases = listOf("camera", "take photo", "take picture", "photo")
    ),
)

fun resolveNativeLauncherAction(
    message: String,
    explicit: Boolean,
): NativeLauncherAction? {
    val normalized = normalizeNativeActionText(message)
    if (normalized.isBlank()) return null

    return NATIVE_ACTION_PATTERNS.firstOrNull { pattern ->
        (!pattern.explicitOnly || explicit) && pattern.aliases.any { alias ->
            normalized == alias || normalized.startsWith("$alias ")
        }
    }?.action
}

private fun normalizeNativeActionText(value: String): String {
    return value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}
