package com.blacksingularities.openroutine.model

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(10)

/** Pojedynczy klocek rutyny: wyzwalacz, warunek albo akcja z katalogu [Catalog]. */
data class Block(
    val type: String,
    val params: Map<String, String> = emptyMap(),
    val id: String = newId(),
) {
    val spec: BlockSpec? get() = Catalog[type]

    /** Wartość parametru albo jego wartość domyślna z katalogu. */
    operator fun get(key: String): String =
        params[key] ?: spec?.params?.firstOrNull { it.key == key }?.default.orEmpty()

    fun summary(): String = spec?.summary(params) ?: "Nieznany klocek ($type)"
}

/** Rutyna: gdy zajdzie dowolny z wyzwalaczy i warunki są spełnione – wykonaj akcje po kolei. */
data class Routine(
    val id: String = newId(),
    val name: String = "Nowa rutyna",
    val enabled: Boolean = true,
    val triggers: List<Block> = emptyList(),
    val conditions: List<Block> = emptyList(),
    val matchAll: Boolean = true,
    val actions: List<Block> = emptyList(),
    val cooldownSec: Int = 0,
    val stopOnError: Boolean = false,
    val lastRun: Long = 0L,
    val runs: Int = 0,
) {
    /** Kopia z nowymi identyfikatorami (szablon, duplikat, import). */
    fun fresh(newName: String = name) = copy(
        id = newId(),
        name = newName,
        triggers = triggers.map { it.copy(id = newId()) },
        conditions = conditions.map { it.copy(id = newId()) },
        actions = actions.map { it.copy(id = newId()) },
        lastRun = 0L,
        runs = 0,
    )

    val needs: Set<Need> get() = (triggers + conditions + actions).flatMap { it.spec?.needs.orEmpty() }.toSet()
}

data class LogEntry(val time: Long, val routine: String, val message: String, val ok: Boolean)

/** Zdarzenie przekazywane do silnika: typ wyzwalacza i dane (zmienne dostępne w akcjach). */
data class Event(val type: String, val data: Map<String, String> = emptyMap())
