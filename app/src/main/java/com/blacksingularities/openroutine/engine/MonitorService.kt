package com.blacksingularities.openroutine.engine

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.blacksingularities.openroutine.R
import com.blacksingularities.openroutine.data.Repo
import com.blacksingularities.openroutine.model.Need
import com.blacksingularities.openroutine.notify.Notifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Usługa pierwszoplanowa nasłuchująca zdarzeń, których Android nie dostarcza aplikacjom w tle:
 * ekran, bateria, zasilanie, słuchawki, Wi-Fi, Bluetooth, tryb samolotowy, oszczędzanie energii i foldery.
 * Działa tylko wtedy, gdy jakaś włączona rutyna jej potrzebuje.
 */
class MonitorService : Service() {
    private var lastBattery = -1
    private var wifiUp: Boolean? = null
    private var pollJob: Job? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    if (level < 0 || scale <= 0) return
                    val pct = level * 100 / scale
                    val prev = lastBattery
                    lastBattery = pct
                    if (prev >= 0 && prev != pct) Engine.dispatch("t.battery", mapOf("battery" to "$pct", "prev" to "$prev"))
                }
                Intent.ACTION_POWER_CONNECTED -> Engine.dispatch("t.power", mapOf("state" to "connected"))
                Intent.ACTION_POWER_DISCONNECTED -> Engine.dispatch("t.power", mapOf("state" to "disconnected"))
                Intent.ACTION_SCREEN_ON -> Engine.dispatch("t.screen", mapOf("state" to "on"))
                Intent.ACTION_SCREEN_OFF -> Engine.dispatch("t.screen", mapOf("state" to "off"))
                Intent.ACTION_USER_PRESENT -> Engine.dispatch("t.screen", mapOf("state" to "unlock"))
                AudioManager.ACTION_HEADSET_PLUG -> {
                    if (isInitialStickyBroadcast) return
                    val state = if (intent.getIntExtra("state", 0) == 1) "connected" else "disconnected"
                    Engine.dispatch("t.headset", mapOf("state" to state))
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                    BluetoothAdapter.STATE_ON -> Engine.dispatch("t.bluetooth", mapOf("state" to "on"))
                    BluetoothAdapter.STATE_OFF -> Engine.dispatch("t.bluetooth", mapOf("state" to "off"))
                }
                Intent.ACTION_AIRPLANE_MODE_CHANGED ->
                    Engine.dispatch("t.airplane", mapOf("state" to if (intent.getBooleanExtra("state", false)) "on" else "off"))
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                    Engine.dispatch("t.powersave", mapOf("state" to if (DeviceState.powerSave(context)) "on" else "off"))
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifier.MONITOR_ID, notification(), type)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val cm = getSystemService(ConnectivityManager::class.java)
        wifiUp = DeviceState.wifi(this)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = wifiChanged(true)
            override fun onLost(network: Network) = wifiChanged(DeviceState.wifi(this@MonitorService))
        }
        runCatching {
            cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), cb)
            callback = cb
        }

        pollJob = Engine.scope.launch {
            while (isActive) {
                runCatching { FolderWatch.poll(this@MonitorService) }
                delay(60_000)
            }
        }
    }

    private fun wifiChanged(up: Boolean) {
        if (wifiUp == up) return
        wifiUp = up
        Engine.dispatch("t.wifi", mapOf("state" to if (up) "connected" else "disconnected"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching { androidx.core.app.NotificationManagerCompat.from(this).notify(Notifier.MONITOR_ID, notification()) }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        callback?.let { cb -> runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) } }
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun notification() = NotificationCompat.Builder(this, Notifier.CHANNEL_MONITOR)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("OpenRoutine czuwa")
        .setContentText(
            Repo.routines.value.count { it.enabled }.let { n -> "Aktywne rutyny: $n" },
        )
        .setOngoing(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setContentIntent(Notifier.openApp(this))
        .build()

    companion object {
        fun needed(): Boolean = Repo.routines.value.any { r ->
            r.enabled && r.triggers.any { it.spec?.needs?.contains(Need.MONITOR) == true }
        }

        fun sync(ctx: Context) {
            val intent = Intent(ctx, MonitorService::class.java)
            if (needed()) {
                runCatching { ContextCompat.startForegroundService(ctx, intent) }
                    .onFailure { Repo.log("OpenRoutine", "Nie można uruchomić pracy w tle: ${it.message}", false) }
            } else {
                ctx.stopService(intent)
            }
        }
    }
}

/** Wykrywanie nowych plików w folderach wskazanych w wyzwalaczach „Nowy plik w folderze”. */
object FolderWatch {
    private const val PREFS = "folders"

    fun poll(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        for (r in Repo.routines.value) {
            if (!r.enabled) continue
            for (b in r.triggers.filter { it.type == "t.folder" }) {
                val folder = b["folder"]
                if (folder.isBlank()) continue
                val every = (b["minutes"].trim().toIntOrNull() ?: 5).coerceAtLeast(1) * 60_000L
                val last = prefs.getLong("at_${b.id}", 0L)
                if (now - last < every - 5_000) continue
                val docs = try {
                    Files.list(ctx, folder).filter { !it.isDir }
                } catch (e: Exception) {
                    prefs.edit().putLong("at_${b.id}", now).apply()
                    Repo.log(r.name, "Folder: ${e.message}", false)
                    continue
                }
                val seenKey = "seen_${b.id}"
                val first = !prefs.contains(seenKey)
                val seen = prefs.getStringSet(seenKey, emptySet()).orEmpty()
                val fresh = docs.filter { it.id !in seen && com.blacksingularities.openroutine.model.Fmt.glob(b["pattern"], it.name) }
                prefs.edit().putStringSet(seenKey, docs.map { it.id }.toSet()).putLong("at_${b.id}", now).apply()
                if (first) continue
                for (d in fresh) {
                    Engine.launch(
                        r,
                        com.blacksingularities.openroutine.model.Event(
                            "t.folder",
                            mapOf("block" to b.id, "file" to d.name, "file_uri" to d.uri.toString(), "file_size" to d.size.toString(), "trigger" to "Nowy plik"),
                        ),
                    )
                }
            }
        }
    }

    /** Po zmianie folderu w wyzwalaczu zaczynamy od nowa (bez uruchamiania dla istniejących plików). */
    fun reset(ctx: Context, blockId: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("seen_$blockId").remove("at_$blockId").apply()
    }
}
