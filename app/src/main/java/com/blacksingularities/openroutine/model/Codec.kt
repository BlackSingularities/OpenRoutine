package com.blacksingularities.openroutine.model

import org.json.JSONArray
import org.json.JSONObject

/** Zapis rutyn, zmiennych i dziennika w JSON (pliki aplikacji, eksport i import). */
object Codec {
    const val FORMAT = 1

    fun block(b: Block): JSONObject {
        val params = JSONObject()
        b.params.forEach { (k, v) -> params.put(k, v) }
        return JSONObject().put("id", b.id).put("type", b.type).put("params", params)
    }

    fun block(o: JSONObject): Block {
        val p = o.optJSONObject("params")
        val params = buildMap {
            p?.keys()?.forEach { k -> put(k, p.optString(k)) }
        }
        return Block(type = o.getString("type"), params = params, id = o.optString("id").ifBlank { newId() })
    }

    private fun blocks(list: List<Block>) = JSONArray().apply { list.forEach { put(block(it)) } }
    private fun blocks(a: JSONArray?): List<Block> =
        if (a == null) emptyList() else (0 until a.length()).map { block(a.getJSONObject(it)) }

    fun routine(r: Routine): JSONObject = JSONObject()
        .put("id", r.id)
        .put("name", r.name)
        .put("enabled", r.enabled)
        .put("triggers", blocks(r.triggers))
        .put("conditions", blocks(r.conditions))
        .put("matchAll", r.matchAll)
        .put("actions", blocks(r.actions))
        .put("cooldownSec", r.cooldownSec)
        .put("stopOnError", r.stopOnError)
        .put("lastRun", r.lastRun)
        .put("runs", r.runs)

    fun routine(o: JSONObject): Routine = Routine(
        id = o.optString("id").ifBlank { newId() },
        name = o.optString("name", "Rutyna"),
        enabled = o.optBoolean("enabled", true),
        triggers = blocks(o.optJSONArray("triggers")),
        conditions = blocks(o.optJSONArray("conditions")),
        matchAll = o.optBoolean("matchAll", true),
        actions = blocks(o.optJSONArray("actions")),
        cooldownSec = o.optInt("cooldownSec", 0),
        stopOnError = o.optBoolean("stopOnError", false),
        lastRun = o.optLong("lastRun", 0L),
        runs = o.optInt("runs", 0),
    )

    fun encode(routines: List<Routine>, vars: Map<String, String>): String {
        val v = JSONObject()
        vars.forEach { (k, value) -> v.put(k, value) }
        return JSONObject()
            .put("app", "OpenRoutine")
            .put("format", FORMAT)
            .put("routines", JSONArray().apply { routines.forEach { put(routine(it)) } })
            .put("vars", v)
            .toString(2)
    }

    data class Bundle(val routines: List<Routine>, val vars: Map<String, String>)

    fun decode(json: String): Bundle {
        val o = JSONObject(json)
        val a = o.optJSONArray("routines") ?: JSONArray()
        val routines = (0 until a.length()).map { routine(a.getJSONObject(it)) }
        val v = o.optJSONObject("vars")
        val vars = buildMap { v?.keys()?.forEach { k -> put(k, v.optString(k)) } }
        return Bundle(routines, vars)
    }

    fun encodeLog(entries: List<LogEntry>): String = JSONArray().apply {
        entries.forEach { put(JSONObject().put("t", it.time).put("r", it.routine).put("m", it.message).put("ok", it.ok)) }
    }.toString()

    fun decodeLog(json: String): List<LogEntry> {
        val a = JSONArray(json)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            LogEntry(o.optLong("t"), o.optString("r"), o.optString("m"), o.optBoolean("ok", true))
        }
    }
}
