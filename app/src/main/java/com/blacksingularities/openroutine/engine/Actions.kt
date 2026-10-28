package com.blacksingularities.openroutine.engine

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.SmsManager
import android.view.KeyEvent
import android.widget.Toast
import com.blacksingularities.openroutine.App
import com.blacksingularities.openroutine.data.Repo
import com.blacksingularities.openroutine.model.Block
import com.blacksingularities.openroutine.model.Event
import com.blacksingularities.openroutine.model.Fmt
import com.blacksingularities.openroutine.notify.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.math.roundToInt

object Actions {
    suspend fun run(run: Run, b: Block) {
        val ctx = run.ctx
        fun p(key: String) = run.param(b, key)
        when (b.type) {
            "a.notify" -> Notifier.show(ctx, p("title").ifBlank { "OpenRoutine" }, p("text"), high = b["priority"] == "high")
            "a.toast" -> withContext(Dispatchers.Main) { Toast.makeText(ctx, p("text"), Toast.LENGTH_LONG).show() }
            "a.speak" -> speak(ctx, p("text"))
            "a.vibrate" -> vibrate(ctx, b["pattern"])
            "a.sound" -> sound(ctx, b["kind"])
            "a.ringer" -> {
                val am = ctx.getSystemService(AudioManager::class.java)
                am.ringerMode = when (b["mode"]) {
                    "silent" -> AudioManager.RINGER_MODE_SILENT
                    "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
            }
            "a.dnd" -> {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (!nm.isNotificationPolicyAccessGranted) error("Brak dostępu do trybu Nie przeszkadzać")
                nm.setInterruptionFilter(
                    when (b["mode"]) {
                        "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                        "none" -> NotificationManager.INTERRUPTION_FILTER_NONE
                        else -> NotificationManager.INTERRUPTION_FILTER_ALL
                    },
                )
            }
            "a.volume" -> {
                val am = ctx.getSystemService(AudioManager::class.java)
                val stream = when (b["stream"]) {
                    "ring" -> AudioManager.STREAM_RING
                    "notification" -> AudioManager.STREAM_NOTIFICATION
                    "alarm" -> AudioManager.STREAM_ALARM
                    "call" -> AudioManager.STREAM_VOICE_CALL
                    else -> AudioManager.STREAM_MUSIC
                }
                val pct = (p("level").trim().toIntOrNull() ?: 50).coerceIn(0, 100)
                val max = am.getStreamMaxVolume(stream)
                am.setStreamVolume(stream, (max * pct / 100f).roundToInt(), 0)
            }
            "a.brightness" -> {
                if (!Settings.System.canWrite(ctx)) error("Brak zgody na zmianę ustawień systemu")
                val cr = ctx.contentResolver
                if (b["mode"] == "auto") {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                } else {
                    val pct = (p("level").trim().toIntOrNull() ?: 50).coerceIn(1, 100)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (pct * 255 / 100).coerceIn(1, 255))
                }
            }
            "a.torch" -> Torch.set(ctx, b["state"])
            "a.media" -> {
                val am = ctx.getSystemService(AudioManager::class.java)
                val code = when (b["cmd"]) {
                    "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
                    "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                    "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                    "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
                    else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                }
                val t = SystemClock.uptimeMillis()
                am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
                am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
            }
            "a.open_app" -> {
                val pkg = b["app"].trim()
                if (pkg.isEmpty()) error("Nie wybrano aplikacji")
                val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: error("Aplikacja $pkg nie jest zainstalowana")
                Launcher.start(ctx, intent, "Otwórz ${Fmt.appName(pkg)}")
            }
            "a.open_url" -> {
                val url = p("url").trim()
                if (url.isEmpty() || url == "https://") error("Pusty adres")
                Launcher.start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Otwórz $url")
            }
            "a.share" -> {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, p("text"))
                Launcher.start(ctx, Intent.createChooser(send, "Udostępnij"), "Udostępnij tekst z rutyny")
            }
            "a.clipboard" -> withContext(Dispatchers.Main) {
                ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("OpenRoutine", p("text")))
            }
            "a.http" -> {
                val response = http(b["method"], p("url"), b["content_type"], p("body"), p("headers"))
                val target = b["save_to"].trim()
                if (target.isNotEmpty()) {
                    run.vars[target] = response.body
                    Repo.setVar(target, response.body)
                }
                run.vars["http_code"] = response.code.toString()
                if (response.code !in 200..299) error("Serwer odpowiedział kodem ${response.code}")
            }
            "a.sms" -> {
                val number = p("number").trim()
                if (number.isEmpty()) error("Brak numeru")
                val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
                val parts = sms.divideMessage(p("text"))
                sms.sendMultipartTextMessage(number, null, parts, null, null)
            }
            "a.files" -> files(run, b)
            "a.write_file" -> withContext(Dispatchers.IO) {
                Files.writeText(ctx, b["folder"], p("name").ifBlank { "dziennik.txt" }, p("text"), b["mode"] != "overwrite")
            }
            "a.set_var" -> {
                val name = b["name"].trim()
                val value = p("value")
                run.vars[name] = value
                Repo.setVar(name, value)
            }
            "a.calc_var" -> {
                val name = b["name"].trim()
                val value = Fmt.calc(Repo.vars.value[name] ?: run.vars[name], b["op"], p("amount"))
                run.vars[name] = value
                Repo.setVar(name, value)
            }
            "a.wait" -> delay(((p("seconds").trim().replace(',', '.').toDoubleOrNull() ?: 1.0) * 1000).toLong().coerceIn(0, 24 * 3_600_000L))
            "a.stop_if" -> {
                val name = b["name"].trim().removePrefix("{").removeSuffix("}")
                if (Fmt.compare(run.vars[name], b["op"], p("value"))) throw StopRoutine()
            }
            "a.run_routine" -> {
                val target = Repo.byName(p("name")) ?: error("Nie ma rutyny „${p("name")}”")
                if (target.id == run.routine.id) error("Rutyna nie może uruchomić samej siebie")
                Engine.run(target, Event("t.manual", run.event.data + ("trigger" to "z rutyny ${run.routine.name}")), false, run.depth + 1)
            }
            "a.toggle_routine" -> {
                val target = Repo.byName(p("name")) ?: error("Nie ma rutyny „${p("name")}”")
                Repo.update(target.id) {
                    it.copy(enabled = when (b["state"]) { "on" -> true; "off" -> false; else -> !it.enabled })
                }
                App.syncBackground(ctx)
            }
            "a.broadcast" -> {
                val intent = Intent(p("action").trim())
                val pkg = p("package").trim()
                if (pkg.isNotEmpty()) intent.setPackage(pkg)
                Fmt.pairs(p("extras"), '=').forEach { (k, v) -> intent.putExtra(k, v) }
                ctx.sendBroadcast(intent)
            }
            "a.log" -> Repo.log(run.routine.name, p("text"))
            else -> error("Nieznana akcja ${b.type}")
        }
    }

    private suspend fun files(run: Run, b: Block) = withContext(Dispatchers.IO) {
        val ctx = run.ctx
        val op = b["op"]
        val docs = Files.matching(ctx, b["folder"], run.param(b, "pattern"), b["older_days"].trim().toIntOrNull() ?: 0)
        var done = 0
        for (d in docs) {
            when (op) {
                "delete" -> if (Files.delete(ctx, d)) done++
                "move" -> {
                    Files.copy(ctx, d, b["target"])
                    Files.delete(ctx, d)
                    done++
                }
                else -> {
                    Files.copy(ctx, d, b["target"])
                    done++
                }
            }
        }
        run.vars["files_count"] = done.toString()
    }

    data class HttpResult(val code: Int, val body: String)

    private suspend fun http(method: String, url: String, contentType: String, body: String, headers: String): HttpResult = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) error("Adres musi zaczynać się od http:// lub https://")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method.ifBlank { "GET" }
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "OpenRoutine")
            Fmt.pairs(headers, ':').forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            if (method != "GET" && method != "DELETE" && body.isNotEmpty()) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "$contentType; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResult(code, text.take(8_000))
        } finally {
            conn.disconnect()
        }
    }

    private fun vibrate(ctx: Context, pattern: String) {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java)
        }
        val timings = when (pattern) {
            "long" -> longArrayOf(0, 900)
            "double" -> longArrayOf(0, 200, 150, 200)
            "sos" -> longArrayOf(0, 150, 100, 150, 100, 150, 250, 450, 100, 450, 100, 450, 250, 150, 100, 150, 100, 150)
            else -> longArrayOf(0, 250)
        }
        v.vibrate(VibrationEffect.createWaveform(timings, -1))
    }

    private suspend fun sound(ctx: Context, kind: String) {
        val type = when (kind) {
            "alarm" -> RingtoneManager.TYPE_ALARM
            "ringtone" -> RingtoneManager.TYPE_RINGTONE
            else -> RingtoneManager.TYPE_NOTIFICATION
        }
        val ringtone = RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(type)) ?: error("Brak dźwięku systemowego")
        ringtone.play()
        // Alarm i dzwonek grają w pętli – ograniczamy je do kilku sekund.
        if (type != RingtoneManager.TYPE_NOTIFICATION) {
            delay(6_000)
            ringtone.stop()
        }
    }

    private suspend fun speak(ctx: Context, text: String) {
        if (text.isBlank()) return
        var tts: TextToSpeech? = null
        try {
            val ok = withTimeout(10_000) {
                suspendCancellableCoroutine { cont ->
                    tts = TextToSpeech(ctx) { status -> if (cont.isActive) cont.resume(status == TextToSpeech.SUCCESS) }
                }
            }
            val engine = tts ?: return
            if (!ok) error("Synteza mowy niedostępna")
            withTimeout(120_000) {
                suspendCancellableCoroutine { cont ->
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                    })
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "openroutine")
                }
            }
        } finally {
            tts?.shutdown()
        }
    }
}

/** Latarka: stan śledzony przez wywołanie zwrotne systemu, dzięki czemu działa „Przełącz”. */
object Torch {
    @Volatile private var on = false
    private var registered = false

    private fun cameraId(cm: CameraManager): String =
        cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: error("Brak latarki w urządzeniu")

    fun set(ctx: Context, state: String) {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cameraId(cm)
        if (!registered) {
            registered = true
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == id) on = enabled
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        val target = when (state) {
            "on" -> true
            "off" -> false
            else -> !on
        }
        cm.setTorchMode(id, target)
        on = target
    }
}

/**
 * Otwieranie ekranów z tła. Android 10+ blokuje to, chyba że aplikacja jest na wierzchu albo
 * ma zgodę „Wyświetlanie nad aplikacjami”; w pozostałych przypadkach rutyna pokazuje powiadomienie do dotknięcia.
 */
object Launcher {
    suspend fun start(ctx: Context, intent: Intent, label: String) = withContext(Dispatchers.Main) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (App.inForeground || Settings.canDrawOverlays(ctx)) {
            ctx.startActivity(intent)
        } else {
            val pi = PendingIntent.getActivity(ctx, label.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            Notifier.show(ctx, label, "Dotknij, aby kontynuować", high = true, tap = pi, channel = Notifier.CHANNEL_LAUNCH)
        }
    }
}
