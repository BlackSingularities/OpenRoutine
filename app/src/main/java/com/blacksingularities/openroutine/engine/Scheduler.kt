package com.blacksingularities.openroutine.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.blacksingularities.openroutine.data.Repo
import com.blacksingularities.openroutine.model.Event
import com.blacksingularities.openroutine.model.Fmt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.ZoneId

/** Alarmy dla wyzwalaczy „O godzinie” i „Co pewien czas” – jeden alarm na klocek. */
object Scheduler {
    private const val PREFS = "alarms"

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Najbliższe uruchomienia (do podglądu na liście rutyn). */
    fun nextRuns(ctx: Context): Map<String, Long> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .filterKeys { it.startsWith("next_") }
            .mapNotNull { (k, v) -> (v as? Long)?.let { k.removePrefix("next_") to it } }
            .toMap()

    fun sync(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getStringSet("codes", emptySet()).orEmpty()
        val now = System.currentTimeMillis()
        val codes = mutableSetOf<String>()
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith("next_") }.forEach { edit.remove(it) }
        val nextPerRoutine = mutableMapOf<String, Long>()

        for (r in Repo.routines.value) {
            if (!r.enabled) continue
            for (b in r.triggers) {
                val at = when (b.type) {
                    "t.time" -> {
                        val t = Fmt.parseTime(b["time"]) ?: continue
                        Fmt.nextDaily(t, b["days"], LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    "t.interval" -> {
                        val period = (b["minutes"].trim().toIntOrNull() ?: 60).coerceAtLeast(1) * 60_000L
                        val saved = prefs.getLong("iv_" + b.id, 0L)
                        val t = if (saved > now && saved - now <= period) saved else now + period
                        edit.putLong("iv_" + b.id, t)
                        t
                    }
                    else -> continue
                }
                val code = (r.id + b.id).hashCode()
                set(ctx, am, code, r.id, b.id, at)
                codes += code.toString()
                nextPerRoutine[r.id] = minOf(nextPerRoutine[r.id] ?: Long.MAX_VALUE, at)
            }
        }
        for (c in old - codes) {
            val pi = PendingIntent.getBroadcast(ctx, c.toInt(), Intent(ctx, AlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }
        nextPerRoutine.forEach { (id, t) -> edit.putLong("next_$id", t) }
        edit.putStringSet("codes", codes).apply()
    }

    private fun set(ctx: Context, am: AlarmManager, code: Int, routineId: String, blockId: String, at: Long) {
        val intent = Intent(ctx, AlarmReceiver::class.java).putExtra("routine", routineId).putExtra("block", blockId)
        val pi = PendingIntent.getBroadcast(ctx, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (canExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun consumeInterval(ctx: Context, blockId: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("iv_$blockId").apply()
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rid = intent.getStringExtra("routine") ?: return
        val bid = intent.getStringExtra("block") ?: return
        val pending = goAsync()
        val r = Repo.get(rid)
        val b = r?.triggers?.firstOrNull { it.id == bid }
        if (b?.type == "t.interval") Scheduler.consumeInterval(context, bid)
        Scheduler.sync(context)
        if (r == null || b == null || !r.enabled) {
            pending.finish()
            return
        }
        val job = Engine.launch(r, Event(b.type, mapOf("block" to bid, "trigger" to (b.spec?.title ?: b.type))))
        Engine.scope.launch {
            // Odbiornik ma ograniczony czas; długie rutyny (np. „Czekaj”) działają dalej w tle.
            withTimeoutOrNull(8_000) { job.join() }
            pending.finish()
        }
    }
}
