package com.blacksingularities.openroutine

import com.blacksingularities.openroutine.engine.Triggers
import com.blacksingularities.openroutine.model.Block
import com.blacksingularities.openroutine.model.Catalog
import com.blacksingularities.openroutine.model.Codec
import com.blacksingularities.openroutine.model.Fmt
import com.blacksingularities.openroutine.model.Kind
import com.blacksingularities.openroutine.model.Presets
import com.blacksingularities.openroutine.model.Routine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RulesTest {
    @Test
    fun expandReplacesKnownAndKeepsUnknown() {
        val out = Fmt.expand("Bateria {battery}% · {var:woda} · {brak} · {\"json\":1}", mapOf("battery" to "42", "woda" to "3"))
        assertEquals("Bateria 42% · 3 · {brak} · {\"json\":1}", out)
    }

    @Test
    fun globMatchesSeveralPatterns() {
        assertTrue(Fmt.glob("*.pdf;*.JPG", "Faktura.PDF"))
        assertTrue(Fmt.glob("*.pdf, *.jpg", "zdjecie.jpg"))
        assertTrue(Fmt.glob("raport-??.txt", "raport-07.txt"))
        assertFalse(Fmt.glob("*.pdf", "notatka.txt"))
        assertTrue(Fmt.glob("", "cokolwiek"))
        assertTrue(Fmt.glob("plik (1).txt", "plik (1).txt"))
    }

    @Test
    fun windowHandlesMidnight() {
        assertTrue(Fmt.inWindow(22 * 60, 6 * 60, 23 * 60))
        assertTrue(Fmt.inWindow(22 * 60, 6 * 60, 5 * 60))
        assertFalse(Fmt.inWindow(22 * 60, 6 * 60, 12 * 60))
        assertTrue(Fmt.inWindow(8 * 60, 22 * 60, 12 * 60))
        assertFalse(Fmt.inWindow(8 * 60, 22 * 60, 22 * 60))
    }

    @Test
    fun nextDailyRespectsDays() {
        // 2026-10-28 to środa.
        val now = LocalDateTime.of(2026, 10, 28, 8, 0)
        assertEquals(LocalDateTime.of(2026, 10, 28, 9, 30), Fmt.nextDaily(9 * 60 + 30, "1234567", now))
        assertEquals(LocalDateTime.of(2026, 10, 29, 7, 0), Fmt.nextDaily(7 * 60, "1234567", now))
        assertEquals(LocalDateTime.of(2026, 10, 31, 7, 0), Fmt.nextDaily(7 * 60, "67", now))
        assertEquals(LocalDateTime.of(2026, 11, 4, 8, 0), Fmt.nextDaily(8 * 60, "3", now))
    }

    @Test
    fun parseAndFormatTime() {
        assertEquals(7 * 60 + 5, Fmt.parseTime("07:05"))
        assertEquals(null, Fmt.parseTime("25:00"))
        assertEquals("23:59", Fmt.formatTime(23 * 60 + 59))
    }

    @Test
    fun compareAndCalc() {
        assertTrue(Fmt.compare("10", "gt", "9"))
        assertTrue(Fmt.compare("10.0", "eq", "10"))
        assertTrue(Fmt.compare("Kod 1234", "contains", "kod"))
        assertTrue(Fmt.compare(null, "empty", ""))
        assertEquals("4", Fmt.calc("3", "add", "1"))
        assertEquals("1", Fmt.calc(null, "add", "1"))
        assertEquals("2.5", Fmt.calc("5", "div", "2"))
        assertEquals("5", Fmt.calc("5", "div", "0"))
    }

    @Test
    fun daysAndFolderLabels() {
        assertEquals("codziennie", Fmt.days("1234567"))
        assertEquals("pn–pt", Fmt.days("12345"))
        assertEquals("weekendy", Fmt.days("67"))
        assertEquals("pn, śr", Fmt.days("13"))
        assertEquals("Download/Zdjęcia", Fmt.folderName("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FZdj%C4%99cia"))
    }

    @Test
    fun batteryTriggerFiresOnlyWhenCrossing() {
        val below = Block("t.battery", mapOf("dir" to "below", "level" to "20"))
        assertTrue(Triggers.matches(below, mapOf("prev" to "21", "battery" to "20")))
        assertFalse(Triggers.matches(below, mapOf("prev" to "20", "battery" to "19")))
        val above = Block("t.battery", mapOf("dir" to "above", "level" to "90"))
        assertTrue(Triggers.matches(above, mapOf("prev" to "89", "battery" to "90")))
        assertFalse(Triggers.matches(above, mapOf("prev" to "91", "battery" to "92")))
    }

    @Test
    fun notificationAndSmsFilters() {
        val n = Block("t.notification", mapOf("app" to "com.x", "contains" to "kod"))
        assertTrue(Triggers.matches(n, mapOf("notif_app" to "com.x", "notif_title" to "Twój KOD", "notif_text" to "")))
        assertFalse(Triggers.matches(n, mapOf("notif_app" to "com.y", "notif_title" to "kod")))
        val s = Block("t.sms", mapOf("from" to "+48"))
        assertTrue(Triggers.matches(s, mapOf("sms_from" to "+48600100200", "sms_body" to "x")))
    }

    @Test
    fun catalogIsConsistent() {
        val ids = Catalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(Catalog.of(Kind.TRIGGER).size >= 15)
        assertTrue(Catalog.of(Kind.CONDITION).size >= 14)
        assertTrue(Catalog.of(Kind.ACTION).size >= 25)
        Catalog.all.forEach { spec ->
            // Każdy opis musi działać na wartościach domyślnych.
            assertTrue(spec.summary(spec.defaults()).isNotBlank())
            spec.params.filter { it.options.isNotEmpty() }.forEach { p -> assertTrue(p.options.any { it.first == p.default }) }
        }
    }

    @Test
    fun presetsUseKnownBlocks() {
        Presets.all.forEach { p ->
            val r = p.routine
            assertTrue(p.title, r.triggers.isNotEmpty() && r.actions.isNotEmpty())
            (r.triggers + r.conditions + r.actions).forEach { assertNotNull("${p.title}: ${it.type}", it.spec) }
        }
    }

    @Test
    fun codecRoundTrip() {
        val r = Routine(
            name = "Test „ąę”",
            triggers = listOf(Block("t.time", mapOf("time" to "07:00", "days" to "12345"))),
            conditions = listOf(Block("c.chance", mapOf("percent" to "30"))),
            matchAll = false,
            actions = listOf(Block("a.notify", mapOf("title" to "A", "text" to "{time}"))),
            cooldownSec = 60,
        )
        val bundle = Codec.decode(Codec.encode(listOf(r), mapOf("licznik" to "5")))
        assertEquals(listOf(r), bundle.routines)
        assertEquals("5", bundle.vars["licznik"])
        val copy = r.fresh()
        assertFalse(copy.id == r.id)
        assertEquals(r.actions.map { it.params }, copy.actions.map { it.params })
    }
}
