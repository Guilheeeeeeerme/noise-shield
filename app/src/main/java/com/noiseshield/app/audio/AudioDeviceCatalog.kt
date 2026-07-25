package com.noiseshield.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.noiseshield.app.R
import com.noiseshield.app.data.AudioDevicePreference
import com.noiseshield.app.data.AudioRouteDevice

/**
 * Lists and fingerprints Android audio devices for independent input/output routing.
 */
object AudioDeviceCatalog {
    fun listInputs(context: Context): List<AudioRouteDevice> =
        listDevices(context, AudioManager.GET_DEVICES_INPUTS)
            .filter { isUsefulInput(it.type) }
            .dedupe()

    fun listOutputs(context: Context): List<AudioRouteDevice> =
        listDevices(context, AudioManager.GET_DEVICES_OUTPUTS)
            .filter { isUsefulOutput(it.type) }
            .dedupe()

    fun resolveDeviceId(
        available: List<AudioRouteDevice>,
        preference: AudioDevicePreference,
        preferBuiltinWhenAuto: Boolean = false,
        preferBluetoothWhenAuto: Boolean = false,
    ): Int {
        if (!preference.isAuto) {
            matchPreference(available, preference)?.id?.let { return it }
        }
        if (preferBluetoothWhenAuto) {
            available.firstOrNull { it.isBluetooth }?.id?.let { return it }
        }
        if (preferBuiltinWhenAuto) {
            available.firstOrNull { it.isBuiltin }?.id?.let { return it }
        }
        return AudioDevicePreference.DEVICE_ID_AUTO
    }

    fun matchPreference(
        available: List<AudioRouteDevice>,
        preference: AudioDevicePreference,
    ): AudioRouteDevice? {
        if (preference.isAuto) return null
        available.firstOrNull { it.fingerprint == preference.fingerprint }?.let { return it }
        if (preference.deviceId != 0) {
            available.firstOrNull { it.id == preference.deviceId }?.let { return it }
        }
        val parts = preference.fingerprint.split('|')
        if (parts.size >= 2) {
            val type = parts[0].toIntOrNull()
            val product = parts.getOrNull(1).orEmpty()
            if (type != null) {
                available.firstOrNull { it.type == type && product.isNotBlank() &&
                    it.fingerprint.contains("|$product|") }?.let { return it }
                available.firstOrNull { it.type == type }?.let { return it }
            }
        }
        return null
    }

    fun fingerprintOf(info: AudioDeviceInfo): String {
        val address = if (Build.VERSION.SDK_INT >= 28) {
            info.address.orEmpty()
        } else {
            ""
        }
        val product = info.productName?.toString().orEmpty()
        return "${info.type}|$product|$address"
    }

    private fun listDevices(context: Context, flags: Int): List<AudioRouteDevice> {
        val manager = context.getSystemService(AudioManager::class.java) ?: return emptyList()
        return manager.getDevices(flags).map { info ->
            val cleaned = cleanProductName(info.productName?.toString())
            AudioRouteDevice(
                id = info.id,
                fingerprint = fingerprintOf(info),
                name = friendlyName(context, info.type, cleaned),
                type = info.type,
                isBuiltin = isBuiltinType(info.type),
                isBluetooth = isBluetoothType(info.type),
            )
        }
    }

    private fun List<AudioRouteDevice>.dedupe(): List<AudioRouteDevice> =
        groupBy { device ->
            when {
                device.isBuiltin -> "builtin:${device.type}"
                device.isBluetooth -> "bt:${device.fingerprint.substringAfterLast('|').ifBlank {
                    device.fingerprint
                }}"
                else -> device.fingerprint
            }
        }.values.map { group ->
            group.firstOrNull { it.isBluetooth && it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
                ?: group.firstOrNull { it.isBuiltin }
                ?: group.firstOrNull { it.isBluetooth }
                ?: group.first()
        }.sortedWith(
            compareByDescending<AudioRouteDevice> { it.isBuiltin }
                .thenByDescending { it.isBluetooth }
                .thenBy { it.name.lowercase() },
        )

    /** Keep readable brand/model names; drop MACs, hex dumps, and symbol soup. */
    internal fun cleanProductName(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.length < 2) return null
        if (MAC_REGEX.matches(text)) return null
        if (HEXISH_REGEX.matches(text)) return null
        val letters = text.count { it.isLetter() }
        if (letters < 2) return null
        val printable = text.filter { it.isLetterOrDigit() || it.isWhitespace() || it in "-_+.'" }
            .replace(Regex("\\s+"), " ")
            .trim()
        if (printable.length < 2 || printable.count { it.isLetter() } < 2) return null
        return if (printable.length > 22) printable.take(20).trimEnd() + "…" else printable
    }

    private fun friendlyName(context: Context, type: Int, cleanedProduct: String?): String {
        val named = { res: Int ->
            if (cleanedProduct != null) context.getString(res, cleanedProduct)
            else null
        }
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC ->
                context.getString(R.string.device_phone_mic)
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ->
                context.getString(R.string.device_phone_speaker)
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ->
                context.getString(R.string.device_phone_earpiece)
            AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                named(R.string.device_wired_headset_named)
                    ?: context.getString(R.string.device_wired_headset)
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES ->
                named(R.string.device_wired_headphones_named)
                    ?: context.getString(R.string.device_wired_headphones)
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            -> named(R.string.device_bluetooth_named)
                ?: context.getString(R.string.device_bluetooth)
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            -> named(R.string.device_usb_named)
                ?: context.getString(R.string.device_usb)
            else -> {
                if (Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                    named(R.string.device_bluetooth_named)
                        ?: context.getString(R.string.device_bluetooth)
                } else if (Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                    named(R.string.device_bluetooth_named)
                        ?: context.getString(R.string.device_bluetooth)
                } else {
                    context.getString(R.string.device_other)
                }
            }
        }
    }

    private fun isBuiltinType(type: Int): Boolean =
        type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
            type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
            type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE

    private fun isBluetoothType(type: Int): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
            (Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_SPEAKER)

    private fun isUsefulInput(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        -> true
        else -> Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }

    private fun isUsefulOutput(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        -> true
        else -> Build.VERSION.SDK_INT >= 31 && (
            type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                type == AudioDeviceInfo.TYPE_BLE_HEADSET
            )
    }

    private val MAC_REGEX =
        Regex("""^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$""")
    private val HEXISH_REGEX =
        Regex("""^(0x)?[0-9A-Fa-f]{6,}$""")
}
