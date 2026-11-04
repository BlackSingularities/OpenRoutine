package com.blacksingularities.openroutine.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.blacksingularities.openroutine.data.Repo
import com.blacksingularities.openroutine.model.Block
import com.blacksingularities.openroutine.model.Event
import com.blacksingularities.openroutine.model.Need
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Kontekstowy tryb telefonu: rozpoznaje, że jesteś w aucie, na spotkaniu albo na siłowni
 * (Bluetooth, prędkość z lokalizacji, kalendarz) oraz czy jesteś w pobliżu wskazanego miejsca.
 */
object PhoneContext {
    private val CAR = listOf("car", "auto", "carplay", "android auto", "sync", "uconnect", "mylink", "mmi", "idrive", "bmw", "audi", "vw", "skoda", "toyota", "ford", "kia", "hyundai", "mazda", "opel", "renault", "peugeot", "volvo", "tesla", "mercedes", "seat", "honda", "nissan")
    private val MEETING = listOf("spotkanie", "meeting", "call", "teams", "zoom", "meet", "rozmowa", "narada", "konferencja")
    private val GYM = listOf("siłownia", "silownia", "gym", "trening", "fitness", "workout", "crossfit", "basen", "joga", "yoga")

    /** Nazwy aktualnie połączonych urządzeń Bluetooth (uzupełniane przez MonitorService). */
    val btConnected: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile var lastLocation: Location? = null
        private set

    private var listener: LocationListener? = null

    private fun words(extra: String, base: List<String>) =
        base + extra.split(',', ';').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    private fun hit(text: String, words: List<String>): Boolean {
        val t = text.lowercase()
        return words.any { w -> Regex("(^|[^\\p{L}\\p{N}])${Regex.escape(w)}($|[^\\p{L}\\p{N}])").containsMatchIn(t) }
    }

    private fun has(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    /** Tytuły trwających teraz wydarzeń w kalendarzu (bez całodniowych i oznaczonych jako „wolny”). */
    fun currentEvents(ctx: Context): List<String> {
        if (!has(ctx, Manifest.permission.READ_CALENDAR)) return emptyList()
        val now = System.currentTimeMillis()
        val proj = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.AVAILABILITY)
        return runCatching {
            CalendarContract.Instances.query(ctx.contentResolver, proj, now - 1, now + 1)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getInt(1) == 1) continue
                        if (c.getInt(2) == CalendarContract.Instances.AVAILABILITY_FREE) continue
                        add(c.getString(0).orEmpty())
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** Prędkość w km/h z ostatniego świeżego (≤ 3 min) odczytu lokalizacji. */
    fun speedKmh(): Double? {
        val l = lastLocation ?: return null
        if (!l.hasSpeed() || System.currentTimeMillis() - l.time > 180_000) return null
        return l.speed * 3.6
    }

    /** Czy trwa dany kontekst: car / meeting / gym. Zwraca też opis źródła. */
    fun detect(ctx: Context, kind: String, extra: String = ""): Pair<Boolean, String> {
        val bt = btConnected.toList()
        return when (kind) {
            "car" -> {
                val w = words(extra, CAR)
                bt.firstOrNull { hit(it, w) }?.let { return true to "Bluetooth: $it" }
                val v = speedKmh()
                if (v != null && v >= 25) true to "prędkość ${v.roundToInt()} km/h" else false to ""
            }
            "meeting" -> {
                val ev = currentEvents(ctx)
                val w = words(extra, MEETING)
                val match = ev.firstOrNull { extra.isBlank() || hit(it, w) }
                if (match != null) true to "kalendarz: $match" else false to ""
            }
            "gym" -> {
                val w = words(extra, GYM)
                currentEvents(ctx).firstOrNull { hit(it, w) }?.let { return true to "kalendarz: $it" }
                bt.firstOrNull { hit(it, w) }?.let { return true to "Bluetooth: $it" }
                false to ""
            }
            else -> false to ""
        }
    }

    /** Odległość w metrach od punktu albo null, gdy brak lokalizacji. */
    fun distanceTo(lat: Double, lon: Double): Float? {
        val l = lastLocation ?: return null
        val out = FloatArray(1)
        Location.distanceBetween(l.latitude, l.longitude, lat, lon, out)
        return out[0]
    }

    /** Parsuje „52.2297, 21.0122” lub dwa osobne pola. */
    fun coords(b: Block): Pair<Double, Double>? {
        val lat = b["lat"].trim().replace(',', '.').toDoubleOrNull()
        val lon = b["lon"].trim().replace(',', '.').toDoubleOrNull()
        if (lat != null && lon != null) return lat to lon
        val parts = b["lat"].split(Regex("[,;\\s]+")).mapNotNull { it.trim().toDoubleOrNull() }
        return if (parts.size >= 2) parts[0] to parts[1] else null
    }

    fun radius(b: Block) = (b["radius"].trim().toIntOrNull() ?: 200).coerceAtLeast(30)

    /** Czy jesteś w obszarze (z histerezą 15%, by nie migało na granicy). null = brak danych. */
    fun inside(b: Block, wasInside: Boolean? = null): Boolean? {
        val (lat, lon) = coords(b) ?: return null
        val d = distanceTo(lat, lon) ?: return null
        val r = radius(b)
        return when (wasInside) {
            true -> d <= r * 1.15
            false -> d <= r * 0.85
            null -> d <= r
        }
    }

    // --- lokalizacja ---

    fun locationNeeded(): Boolean = Repo.routines.value.any { r ->
        r.enabled && (r.triggers + r.conditions).any { it.spec?.needs?.contains(Need.LOCATION) == true }
    }

    @SuppressLint("MissingPermission")
    fun startLocation(ctx: Context) {
        if (listener != null || !locationNeeded()) return
        if (!has(ctx, Manifest.permission.ACCESS_FINE_LOCATION) && !has(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)) return
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        val l = LocationListener { loc ->
            lastLocation = loc
            poll(ctx)
        }
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31 && lm.allProviders.contains(LocationManager.FUSED_PROVIDER)) add(LocationManager.FUSED_PROVIDER)
            else {
                if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
                if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            }
        }
        for (p in providers) {
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.let { if (lastLocation == null || it.time > lastLocation!!.time) lastLocation = it }
            runCatching { lm.requestLocationUpdates(p, 30_000L, 20f, l, Looper.getMainLooper()) }
        }
        listener = l
    }

    /** Gdy brak świeżej lokalizacji (np. usługa nie działa) – weź ostatnią znaną z systemu. */
    @SuppressLint("MissingPermission")
    fun refresh(ctx: Context) {
        val cur = lastLocation
        if (cur != null && System.currentTimeMillis() - cur.time < 120_000) return
        if (!has(ctx, Manifest.permission.ACCESS_FINE_LOCATION) && !has(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)) return
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        val best = lm.allProviders.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
        if (best != null && (cur == null || best.time > cur.time)) lastLocation = best
    }

    fun stopLocation(ctx: Context) {
        listener?.let { l -> runCatching { ctx.getSystemService(LocationManager::class.java)?.removeUpdates(l) } }
        listener = null
    }

    // --- wyzwalacze „Kontekst” i „Miejsce” ---

    private const val PREFS = "context_state"

    /** Sprawdza wyzwalacze kontekstu i miejsca, uruchamia rutyny przy zmianie stanu. */
    fun poll(ctx: Context) {
        if (!locationNeeded()) stopLocation(ctx) else startLocation(ctx)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (r in Repo.routines.value) {
            if (!r.enabled) continue
            for (b in r.triggers) {
                val key = "s_${b.id}"
                val prev: Boolean? = if (prefs.contains(key)) prefs.getBoolean(key, false) else null
                val data: Map<String, String>
                val now: Boolean = when (b.type) {
                    "t.context" -> {
                        val (on, why) = detect(ctx, b["context"], b["keywords"])
                        data = mapOf("context" to b["context"], "context_source" to why)
                        on
                    }
                    "t.place" -> {
                        val ins = inside(b, prev) ?: continue
                        val (lat, lon) = coords(b) ?: continue
                        val d = distanceTo(lat, lon)
                        data = mapOf("distance" to (d?.roundToInt()?.toString() ?: ""), "place" to b["name"])
                        ins
                    }
                    else -> continue
                }
                if (prev == now) continue
                prefs.edit().putBoolean(key, now).apply()
                if (prev == null) continue
                val want = b["state"] == "enter"
                if (now != want) continue
                Engine.launch(r, Event(b.type, data + ("block" to b.id) + ("trigger" to (b.spec?.title ?: b.type))))
            }
        }
    }

    fun vars(): Map<String, String> {
        val l = lastLocation ?: return emptyMap()
        return mapOf("lat" to "%.6f".format(java.util.Locale.US, l.latitude), "lon" to "%.6f".format(java.util.Locale.US, l.longitude))
    }
}

/** Bufor ostatnich powiadomień do głosowego streszczenia. */
object NotifBuffer {
    data class Item(val time: Long, val app: String, val title: String, val text: String)

    private val items = ArrayDeque<Item>()

    @Synchronized fun add(item: Item) {
        items.addLast(item)
        while (items.size > 100) items.removeFirst()
    }

    @Synchronized fun recent(minutes: Int, limit: Int, clear: Boolean): List<Item> {
        val from = System.currentTimeMillis() - minutes * 60_000L
        val out = items.filter { it.time >= from }.takeLast(limit)
        if (clear) items.clear()
        return out
    }

    fun summary(ctx: Context, minutes: Int, limit: Int, clear: Boolean): String {
        val list = recent(minutes, limit, clear)
        if (list.isEmpty()) return "Brak nowych powiadomień."
        val pm = ctx.packageManager
        fun label(pkg: String) = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        val head = when (list.size) {
            1 -> "Masz jedno powiadomienie."
            in 2..4 -> "Masz ${list.size} powiadomienia."
            else -> "Masz ${list.size} powiadomień."
        }
        return head + " " + list.joinToString(" ") { i ->
            val body = listOf(i.title, i.text.take(160)).filter { it.isNotBlank() }.joinToString(": ")
            "${label(i.app)}. $body."
        }
    }
}
