package com.blacksingularities.openroutine.model

/** Gotowe szablony – punkt wyjścia do własnych rutyn. */
data class Preset(val title: String, val description: String, val routine: Routine)

private fun b(type: String, vararg params: Pair<String, String>) = Block(type, mapOf(*params))

object Presets {
    val all: List<Preset> = listOf(
        Preset(
            "Dobranoc", "O 23:00 włącza Nie przeszkadzać, ścisza dzwonek i przyciemnia ekran.",
            Routine(
                name = "Dobranoc",
                triggers = listOf(b("t.time", "time" to "23:00", "days" to "1234567")),
                actions = listOf(
                    b("a.dnd", "mode" to "priority"),
                    b("a.volume", "stream" to "ring", "level" to "20"),
                    b("a.brightness", "mode" to "manual", "level" to "10"),
                    b("a.notify", "title" to "Dobranoc", "text" to "Tryb nocny włączony o {time}"),
                ),
            ),
        ),
        Preset(
            "Dzień dobry", "W dni robocze o 7:00 wyłącza ciszę i mówi, ile masz baterii.",
            Routine(
                name = "Dzień dobry",
                triggers = listOf(b("t.time", "time" to "07:00", "days" to "12345")),
                actions = listOf(
                    b("a.dnd", "mode" to "off"),
                    b("a.ringer", "mode" to "normal"),
                    b("a.speak", "text" to "Dzień dobry. Jest {time}, {weekday}. Bateria {battery} procent."),
                ),
            ),
        ),
        Preset(
            "Słaba bateria", "Przy 15% bez ładowarki: powiadomienie, ciemniejszy ekran i wibracja.",
            Routine(
                name = "Słaba bateria",
                triggers = listOf(b("t.battery", "dir" to "below", "level" to "15")),
                conditions = listOf(b("c.charging", "state" to "no")),
                actions = listOf(
                    b("a.notify", "title" to "Bateria {battery}%", "text" to "Podłącz ładowarkę", "priority" to "high"),
                    b("a.brightness", "mode" to "manual", "level" to "20"),
                    b("a.vibrate", "pattern" to "double"),
                ),
            ),
        ),
        Preset(
            "Naładowano", "Przy 90% na ładowarce przypomina o odłączeniu – bateria żyje dłużej.",
            Routine(
                name = "Naładowano do 90%",
                triggers = listOf(b("t.battery", "dir" to "above", "level" to "90")),
                conditions = listOf(b("c.charging", "state" to "yes")),
                actions = listOf(
                    b("a.notify", "title" to "Bateria {battery}%", "text" to "Możesz odłączyć ładowarkę"),
                    b("a.sound", "kind" to "notification"),
                ),
            ),
        ),
        Preset(
            "Porządek w Pobranych", "Codziennie o 3:00 przenosi stare dokumenty do archiwum.",
            Routine(
                name = "Porządek w Pobranych",
                triggers = listOf(b("t.time", "time" to "03:00", "days" to "1234567")),
                actions = listOf(
                    b("a.files", "op" to "move", "pattern" to "*.pdf;*.docx;*.xlsx;*.zip", "older_days" to "14"),
                    b("a.log", "text" to "Porządek w Pobranych: {datetime}"),
                ),
            ),
        ),
        Preset(
            "Kopia nowych zdjęć", "Każde nowe zdjęcie z wybranego folderu kopiuje do drugiego.",
            Routine(
                name = "Kopia nowych zdjęć",
                triggers = listOf(b("t.folder", "pattern" to "*.jpg;*.jpeg;*.png;*.heic", "minutes" to "5")),
                actions = listOf(
                    b("a.files", "op" to "copy", "pattern" to "{file}"),
                    b("a.log", "text" to "Skopiowano {file}"),
                ),
            ),
        ),
        Preset(
            "Pij wodę", "Co godzinę między 9 a 21 przypomina o wodzie i liczy szklanki.",
            Routine(
                name = "Pij wodę",
                triggers = listOf(b("t.interval", "minutes" to "60")),
                conditions = listOf(b("c.time_window", "from" to "09:00", "to" to "21:00")),
                actions = listOf(
                    b("a.calc_var", "name" to "woda", "op" to "add", "amount" to "1"),
                    b("a.notify", "title" to "Napij się wody", "text" to "To już {woda}. przypomnienie"),
                ),
            ),
        ),
        Preset(
            "Słuchawki = muzyka", "Po podłączeniu słuchawek ustawia głośność i otwiera odtwarzacz.",
            Routine(
                name = "Słuchawki = muzyka",
                triggers = listOf(b("t.headset", "state" to "connected")),
                actions = listOf(
                    b("a.volume", "stream" to "music", "level" to "40"),
                    b("a.open_app"),
                ),
            ),
        ),
        Preset(
            "Ładowanie nocą", "Telefon na ładowarce po 22:00 – tylko alarmy.",
            Routine(
                name = "Ładowanie nocą",
                triggers = listOf(b("t.power", "state" to "connected")),
                conditions = listOf(b("c.time_window", "from" to "22:00", "to" to "06:00")),
                actions = listOf(b("a.dnd", "mode" to "alarms")),
            ),
        ),
        Preset(
            "SMS do webhooka", "Przekazuje SMS-y z kodami do Twojego serwera.",
            Routine(
                name = "SMS do webhooka",
                triggers = listOf(b("t.sms", "contains" to "kod")),
                actions = listOf(
                    b(
                        "a.http", "method" to "POST", "url" to "https://example.com/hook",
                        "content_type" to "application/json",
                        "body" to "{\"from\":\"{sms_from}\",\"text\":\"{sms_body}\",\"at\":\"{datetime}\"}",
                    ),
                ),
            ),
        ),
        Preset(
            "Dziennik powiadomień", "Zapisuje każde powiadomienie do pliku tekstowego.",
            Routine(
                name = "Dziennik powiadomień",
                triggers = listOf(b("t.notification")),
                actions = listOf(
                    b("a.write_file", "name" to "powiadomienia.txt", "mode" to "append", "text" to "{datetime} [{notif_app}] {notif_title}: {notif_text}"),
                ),
            ),
        ),
        Preset(
            "Latarka z zewnątrz", "Inna aplikacja (np. Tasker) przełącza latarkę zdarzeniem „latarka”.",
            Routine(
                name = "Latarka z zewnątrz",
                triggers = listOf(b("t.external", "event" to "latarka")),
                actions = listOf(b("a.torch", "state" to "toggle")),
            ),
        ),
        Preset(
            "Spotkanie", "Jednym dotknięciem: wibracje, Nie przeszkadzać i powrót po godzinie.",
            Routine(
                name = "Spotkanie",
                triggers = listOf(b("t.manual")),
                actions = listOf(
                    b("a.ringer", "mode" to "vibrate"),
                    b("a.dnd", "mode" to "priority"),
                    b("a.toast", "text" to "Tryb spotkania do {time} + 1 h"),
                    b("a.wait", "seconds" to "3600"),
                    b("a.dnd", "mode" to "off"),
                    b("a.ringer", "mode" to "normal"),
                ),
            ),
        ),
        Preset(
            "Licznik odblokowań", "Liczy odblokowania i ostrzega po 80 w ciągu dnia.",
            Routine(
                name = "Licznik odblokowań",
                triggers = listOf(b("t.screen", "state" to "unlock")),
                actions = listOf(
                    b("a.calc_var", "name" to "odblokowania", "op" to "add", "amount" to "1"),
                    b("a.stop_if", "name" to "odblokowania", "op" to "ne", "value" to "80"),
                    b("a.notify", "title" to "80 odblokowań", "text" to "Może czas na przerwę od telefonu?"),
                ),
            ),
        ),
        Preset(
            "Wyzeruj liczniki", "O północy zeruje liczniki wody i odblokowań.",
            Routine(
                name = "Wyzeruj liczniki",
                triggers = listOf(b("t.time", "time" to "00:00", "days" to "1234567")),
                actions = listOf(
                    b("a.set_var", "name" to "woda", "value" to "0"),
                    b("a.set_var", "name" to "odblokowania", "value" to "0"),
                ),
            ),
        ),
    )
}
