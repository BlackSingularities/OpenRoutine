package com.blacksingularities.openroutine.engine

import android.content.Context
import com.blacksingularities.openroutine.data.Repo
import com.blacksingularities.openroutine.model.Block
import com.blacksingularities.openroutine.model.Catalog
import com.blacksingularities.openroutine.model.Event
import com.blacksingularities.openroutine.model.Fmt
import com.blacksingularities.openroutine.model.Routine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** Sygnał przerwania rutyny przez akcję „Przerwij, jeśli…”. */
class StopRoutine : Exception()

/** Kontekst wykonania: zmienne widoczne dla kolejnych akcji. */
class Run(val ctx: Context, val routine: Routine, val event: Event, val vars: MutableMap<String, String>, val depth: Int) {
    fun expand(text: String) = Fmt.expand(text, vars)
    fun param(b: Block, key: String) = expand(b[key])
}

object Engine {
    lateinit var app: Context
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = ConcurrentHashMap<String, Job>()

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** Zdarzenie z systemu: uruchom każdą włączoną rutynę, której wyzwalacz pasuje. */
    fun dispatch(type: String, data: Map<String, String> = emptyMap()) {
        for (r in Repo.routines.value) {
            if (!r.enabled) continue
            val hit = r.triggers.firstOrNull { it.type == type && Triggers.matches(it, data) } ?: continue
            launch(r, Event(type, data + ("trigger" to (hit.spec?.title ?: type))))
        }
    }

    fun launch(r: Routine, event: Event, manual: Boolean = false, depth: Int = 0): Job {
        val job = scope.launch { run(r, event, manual, depth) }
        running[r.id + ":" + job.hashCode()] = job
        job.invokeOnCompletion { running.remove(r.id + ":" + job.hashCode()) }
        return job
    }

    fun runManual(r: Routine) = launch(r, Event("t.manual", mapOf("trigger" to "Ręcznie")), manual = true)

    /** Zatrzymuje trwające wykonania (np. długie „Czekaj”). */
    fun cancelAll() {
        running.values.forEach { it.cancel() }
        running.clear()
    }

    val activeCount: Int get() = running.size

    fun baseVars(ctx: Context, r: Routine): MutableMap<String, String> {
        val now = LocalDateTime.now()
        val vars = mutableMapOf(
            "time" to now.format(DateTimeFormatter.ofPattern("HH:mm")),
            "date" to now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            "datetime" to now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            "weekday" to Fmt.dayNames[now.dayOfWeek.value - 1],
            "hour" to now.hour.toString(),
            "minute" to now.minute.toString(),
            "battery" to DeviceState.battery(ctx).toString(),
            "routine" to r.name,
            "random" to Random.nextInt(0, 101).toString(),
        )
        vars.putAll(Repo.vars.value)
        return vars
    }

    suspend fun run(r: Routine, event: Event, manual: Boolean, depth: Int): Boolean {
        if (depth > 5) {
            Repo.log(r.name, "Przerwano: zbyt głębokie wywołania rutyn", false)
            return false
        }
        val fresh = Repo.get(r.id) ?: r
        if (!manual && fresh.cooldownSec > 0 && System.currentTimeMillis() - fresh.lastRun < fresh.cooldownSec * 1000L) return false

        val vars = baseVars(app, fresh)
        vars.putAll(event.data)
        val run = Run(app, fresh, event, vars, depth)

        if (fresh.conditions.isNotEmpty()) {
            val results = fresh.conditions.map { c ->
                runCatching { Conditions.check(run, c) }.getOrElse { e ->
                    Repo.log(fresh.name, "Warunek „${c.spec?.title}”: ${e.message}", false)
                    false
                }
            }
            val pass = if (fresh.matchAll) results.all { it } else results.any { it }
            if (!pass) {
                if (manual) Repo.log(fresh.name, "Warunki niespełnione – akcje pominięte", false)
                return false
            }
        }

        Repo.markRun(fresh.id)
        var errors = 0
        for (a in fresh.actions) {
            try {
                Actions.run(run, a)
            } catch (_: StopRoutine) {
                Repo.log(fresh.name, "Zatrzymano przez „Przerwij, jeśli…”")
                return true
            } catch (e: kotlinx.coroutines.CancellationException) {
                Repo.log(fresh.name, "Przerwano", false)
                throw e
            } catch (e: Throwable) {
                errors++
                Repo.log(fresh.name, "${a.spec?.title ?: a.type}: ${e.message ?: e.javaClass.simpleName}", false)
                if (fresh.stopOnError) return false
            }
        }
        val trigger = event.data["trigger"] ?: Catalog[event.type]?.title ?: event.type
        Repo.log(fresh.name, if (errors == 0) "Wykonano ($trigger, akcji: ${fresh.actions.size})" else "Wykonano z błędami: $errors", errors == 0)
        return errors == 0
    }
}

/** Czy zdarzenie pasuje do parametrów wyzwalacza. */
object Triggers {
    fun matches(b: Block, data: Map<String, String>): Boolean = when (b.type) {
        "t.time", "t.interval", "t.folder" -> data["block"] == b.id
        "t.battery" -> {
            val now = data["battery"]?.toIntOrNull()
            val prev = data["prev"]?.toIntOrNull()
            val level = b["level"].trim().toIntOrNull()
            if (now == null || prev == null || level == null) false
            else if (b["dir"] == "above") prev < level && now >= level
            else prev > level && now <= level
        }
        "t.power", "t.screen", "t.headset", "t.wifi", "t.bluetooth", "t.airplane", "t.powersave" -> b["state"] == data["state"]
        "t.notification" -> {
            val app = b["app"].trim()
            val text = b["contains"].trim()
            (app.isEmpty() || app == data["notif_app"]) &&
                (text.isEmpty() || "${data["notif_title"]} ${data["notif_text"]}".contains(text, ignoreCase = true))
        }
        "t.sms" -> {
            val from = b["from"].trim()
            val text = b["contains"].trim()
            (from.isEmpty() || data["sms_from"].orEmpty().contains(from, ignoreCase = true)) &&
                (text.isEmpty() || data["sms_body"].orEmpty().contains(text, ignoreCase = true))
        }
        "t.external" -> b["event"].trim().equals(data["event"], ignoreCase = true)
        else -> true
    }
}
