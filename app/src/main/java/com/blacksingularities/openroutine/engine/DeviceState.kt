package com.blacksingularities.openroutine.engine

import android.app.KeyguardManager
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings

/** Bieżący stan urządzenia dla warunków i zmiennych. */
object DeviceState {
    private fun batteryIntent(ctx: Context): Intent? =
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun battery(ctx: Context): Int {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val v = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (v in 0..100) return v
        val i = batteryIntent(ctx) ?: return -1
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level < 0 || scale <= 0) -1 else level * 100 / scale
    }

    fun charging(ctx: Context): Boolean {
        val plugged = batteryIntent(ctx)?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged != 0
    }

    /** "wifi", "cellular", "other" albo "none". */
    fun network(ctx: Context): String {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return "none"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "other"
            else -> "none"
        }
    }

    fun wifi(ctx: Context) = network(ctx) == "wifi"

    fun screenOn(ctx: Context) = ctx.getSystemService(PowerManager::class.java)?.isInteractive == true

    fun powerSave(ctx: Context) = ctx.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

    fun headset(ctx: Context): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return false
        val types = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        )
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types }
    }

    fun ringer(ctx: Context): String = when (ctx.getSystemService(AudioManager::class.java)?.ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> "silent"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        else -> "normal"
    }

    fun dndOn(ctx: Context): Boolean {
        val f = ctx.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter ?: return false
        return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    fun bluetoothOn(ctx: Context): Boolean =
        runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)

    /** Temperatura baterii w °C (z dokładnością do 0,1) albo null. */
    fun batteryTemp(ctx: Context): Double? {
        val t = batteryIntent(ctx)?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: return null
        return if (t == Int.MIN_VALUE) null else t / 10.0
    }

    fun airplane(ctx: Context): Boolean =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    fun locked(ctx: Context): Boolean = ctx.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    fun musicActive(ctx: Context): Boolean = ctx.getSystemService(AudioManager::class.java)?.isMusicActive == true

    fun inCall(ctx: Context): Boolean {
        val mode = ctx.getSystemService(AudioManager::class.java)?.mode ?: return false
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
    }

    fun stream(name: String): Int = when (name) {
        "ring" -> AudioManager.STREAM_RING
        "notification" -> AudioManager.STREAM_NOTIFICATION
        "alarm" -> AudioManager.STREAM_ALARM
        "call" -> AudioManager.STREAM_VOICE_CALL
        else -> AudioManager.STREAM_MUSIC
    }

    /** Głośność strumienia w procentach. */
    fun volume(ctx: Context, stream: String): Int {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return 0
        val s = stream(stream)
        val max = am.getStreamMaxVolume(s).coerceAtLeast(1)
        return am.getStreamVolume(s) * 100 / max
    }

    fun installed(ctx: Context, pkg: String): Boolean =
        pkg.isNotBlank() && runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess
}
