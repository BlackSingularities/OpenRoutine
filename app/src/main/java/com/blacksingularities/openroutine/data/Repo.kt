package com.blacksingularities.openroutine.data

import android.content.Context
import android.content.SharedPreferences
import com.blacksingularities.openroutine.model.Codec
import com.blacksingularities.openroutine.model.LogEntry
import com.blacksingularities.openroutine.model.Routine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.SecureRandom

/** Rutyny, zmienne i dziennik – w plikach aplikacji, dostępne jako przepływy stanu. */
object Repo {
    private const val LOG_LIMIT = 500

    private lateinit var dir: File
    lateinit var prefs: SharedPreferences
        private set

    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    private val _vars = MutableStateFlow<Map<String, String>>(emptyMap())
    val vars: StateFlow<Map<String, String>> = _vars.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = context.filesDir
        prefs = context.getSharedPreferences("openroutine", Context.MODE_PRIVATE)
        runCatching {
            val f = File(dir, "routines.json")
            if (f.exists()) {
                val bundle = Codec.decode(f.readText())
                _routines.value = bundle.routines
                _vars.value = bundle.vars
            }
        }
        runCatching {
            val f = File(dir, "log.json")
            if (f.exists()) _log.value = Codec.decodeLog(f.readText())
        }
    }

    fun get(id: String): Routine? = _routines.value.firstOrNull { it.id == id }
    fun byName(name: String): Routine? = _routines.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    @Synchronized
    fun upsert(r: Routine) {
        val list = _routines.value
        _routines.value = if (list.any { it.id == r.id }) list.map { if (it.id == r.id) r else it } else list + r
        save()
    }

    @Synchronized
    fun addAll(items: List<Routine>) {
        _routines.value = _routines.value + items
        save()
    }

    @Synchronized
    fun delete(id: String) {
        _routines.value = _routines.value.filterNot { it.id == id }
        save()
    }

    @Synchronized
    fun move(id: String, delta: Int) {
        val list = _routines.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        _routines.value = list
        save()
    }

    @Synchronized
    fun update(id: String, transform: (Routine) -> Routine) {
        _routines.value = _routines.value.map { if (it.id == id) transform(it) else it }
        save()
    }

    @Synchronized
    fun markRun(id: String) = update(id) { it.copy(lastRun = System.currentTimeMillis(), runs = it.runs + 1) }

    @Synchronized
    fun setVar(name: String, value: String) {
        val key = name.trim().removePrefix("{").removeSuffix("}")
        if (key.isEmpty()) return
        _vars.value = _vars.value + (key to value)
        save()
    }

    @Synchronized
    fun removeVar(name: String) {
        _vars.value = _vars.value - name
        save()
    }

    @Synchronized
    fun mergeVars(values: Map<String, String>) {
        _vars.value = _vars.value + values
        save()
    }

    @Synchronized
    fun log(routine: String, message: String, ok: Boolean = true) {
        _log.value = (listOf(LogEntry(System.currentTimeMillis(), routine, message, ok)) + _log.value).take(LOG_LIMIT)
        write("log.json", Codec.encodeLog(_log.value))
    }

    @Synchronized
    fun clearLog() {
        _log.value = emptyList()
        write("log.json", "[]")
    }

    fun export(): String = Codec.encode(_routines.value, _vars.value)

    /** Token, którym inne aplikacje podpisują zdarzenia wysyłane do OpenRoutine. */
    val token: String
        get() = prefs.getString("token", null) ?: regenerateToken()

    fun regenerateToken(): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
        val rnd = SecureRandom()
        val t = (1..16).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
        prefs.edit().putString("token", t).apply()
        return t
    }

    var lastUpdateCheck: Long
        get() = prefs.getLong("update.lastCheck", 0L)
        set(v) = prefs.edit().putLong("update.lastCheck", v).apply()

    private fun save() = write("routines.json", Codec.encode(_routines.value, _vars.value))

    private fun write(name: String, text: String) {
        runCatching {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(File(dir, name))) {
                File(dir, name).writeText(text)
                tmp.delete()
            }
        }
    }
}
