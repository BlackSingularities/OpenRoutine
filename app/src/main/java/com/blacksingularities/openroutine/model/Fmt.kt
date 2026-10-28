package com.blacksingularities.openroutine.model

import java.net.URLDecoder
import java.time.LocalDateTime

/** Czyste funkcje pomocnicze: opisy, wzorce, godziny, zmienne w tekście. */
object Fmt {
    private val dayShort = listOf("pn", "wt", "śr", "cz", "pt", "sb", "nd")
    val dayNames = listOf("poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota", "niedziela")

    /** Etykieta aplikacji dla nazwy pakietu (ustawiana przez aplikację). */
    var appLabel: (String) -> String = { it }

    fun appName(pkg: String): String = if (pkg.isBlank()) "" else appLabel(pkg)

    /** Dni zapisane jako cyfry ISO (1 = poniedziałek … 7 = niedziela). */
    fun dayset(days: String): Set<Int> = days.mapNotNull { it.digitToIntOrNull() }.filter { it in 1..7 }.toSet()

    fun days(days: String): String {
        val set = dayset(days)
        return when {
            set.isEmpty() || set.size == 7 -> "codziennie"
            set == setOf(1, 2, 3, 4, 5) -> "pn–pt"
            set == setOf(6, 7) -> "weekendy"
            else -> set.sorted().joinToString(", ") { dayShort[it - 1] }
        }
    }

    fun minutes(value: String): String {
        val m = value.trim().toIntOrNull() ?: return "$value min"
        return when {
            m % 1440 == 0 -> "${m / 1440} d"
            m % 60 == 0 -> "${m / 60} h"
            m > 60 -> "${m / 60} h ${m % 60} min"
            else -> "$m min"
        }
    }

    /** Czytelna nazwa folderu z adresu drzewa SAF (content://…/tree/primary%3ADownload%2FZdj). */
    fun folderName(uri: String): String {
        if (uri.isBlank()) return ""
        val tail = uri.substringAfterLast("/tree/", uri.substringAfterLast('/'))
        val decoded = runCatching { URLDecoder.decode(tail, "UTF-8") }.getOrDefault(tail)
        val path = decoded.substringAfter(':', decoded)
        return path.ifBlank { "pamięć wewnętrzna" }
    }

    /** „HH:mm” → minuty od północy. */
    fun parseTime(s: String): Int? {
        val parts = s.trim().split(':')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** Czy [now] (minuty od północy) mieści się w przedziale; przedział może przechodzić przez północ. */
    fun inWindow(from: Int, to: Int, now: Int): Boolean = when {
        from == to -> true
        from < to -> now in from until to
        else -> now >= from || now < to
    }

    /** Najbliższe wystąpienie godziny w wybrane dni, ściśle po [now]. */
    fun nextDaily(timeMin: Int, days: String, now: LocalDateTime): LocalDateTime {
        val set = dayset(days).ifEmpty { (1..7).toSet() }
        for (d in 0..7L) {
            val date = now.toLocalDate().plusDays(d)
            if (date.dayOfWeek.value !in set) continue
            val at = date.atTime(timeMin / 60, timeMin % 60)
            if (at.isAfter(now)) return at
        }
        return now.plusDays(1)
    }

    /** Dopasowanie nazwy do wzorców z * i ?, rozdzielonych średnikiem lub przecinkiem; bez rozróżniania wielkości liter. */
    fun glob(pattern: String, name: String): Boolean {
        val patterns = pattern.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }
        if (patterns.isEmpty()) return true
        return patterns.any { p ->
            val regex = buildString {
                p.forEach { ch ->
                    when (ch) {
                        '*' -> append(".*")
                        '?' -> append('.')
                        else -> append(Regex.escape(ch.toString()))
                    }
                }
            }
            Regex(regex, RegexOption.IGNORE_CASE).matches(name)
        }
    }

    private val placeholder = Regex("\\{([A-Za-z0-9_.:-]+)\\}")

    /** Podstawia {zmienna} i {var:zmienna}; nieznane zostają bez zmian. */
    fun expand(text: String, vars: Map<String, String>): String =
        placeholder.replace(text) { m ->
            val key = m.groupValues[1]
            vars[key] ?: vars[key.removePrefix("var:")] ?: m.value
        }

    /** Porównanie wartości dla warunku „Zmienna” i akcji „Przerwij, jeśli…”. */
    fun compare(actual: String?, op: String, expected: String): Boolean {
        val a = actual.orEmpty()
        val an = a.trim().replace(',', '.').toDoubleOrNull()
        val en = expected.trim().replace(',', '.').toDoubleOrNull()
        return when (op) {
            "eq" -> if (an != null && en != null) an == en else a.equals(expected, ignoreCase = true)
            "ne" -> if (an != null && en != null) an != en else !a.equals(expected, ignoreCase = true)
            "contains" -> a.contains(expected, ignoreCase = true)
            "gt" -> an != null && en != null && an > en
            "lt" -> an != null && en != null && an < en
            "empty" -> a.isBlank()
            "set" -> a.isNotBlank()
            else -> false
        }
    }

    /** Wynik działania na liczbach; całkowite bez części dziesiętnej. */
    fun calc(current: String?, op: String, amount: String): String {
        val a = current?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
        val b = amount.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        val r = when (op) {
            "sub" -> a - b
            "mul" -> a * b
            "div" -> if (b == 0.0) a else a / b
            else -> a + b
        }
        return if (r == Math.floor(r) && !r.isInfinite() && kotlin.math.abs(r) < 1e15) r.toLong().toString() else "%.4f".format(java.util.Locale.ROOT, r).trimEnd('0').trimEnd('.')
    }

    /** Linie „klucz=wartość” lub „Nazwa: wartość”. */
    fun pairs(text: String, separator: Char): List<Pair<String, String>> =
        text.lines().mapNotNull { line ->
            val i = line.indexOf(separator)
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }
}
