package com.blacksingularities.openroutine.model

/** Gotowe szablony – punkt wyjścia do własnych rutyn. */
data class Preset(val title: String, val description: String, val routine: Routine, val group: String = "Inne")

private fun b(type: String, vararg params: Pair<String, String>) = Block(type, mapOf(*params))

/** Grupy szablonów w kolejności wyświetlania. */
object PresetGroup {
    const val DAY = "Dzień i noc"
    const val BATTERY = "Bateria i ładowanie"
    const val WORK = "Praca i skupienie"
    const val CONNECT = "Łączność i podróże"
    const val MESSAGES = "Wiadomości i połączenia"
    const val HEALTH = "Zdrowie i nawyki"
    const val FILES = "Pliki"
    const val ADVANCED = "Integracje i zaawansowane"
    val order = listOf(DAY, BATTERY, WORK, CONNECT, MESSAGES, HEALTH, FILES, ADVANCED)
}

object Presets {
    val all: List<Preset> by lazy { (basic + extended).sortedBy { PresetGroup.order.indexOf(it.group).let { i -> if (i < 0) 99 else i } } }

    /** Grupa dla szablonów z pierwszej wersji. */
    private val basicGroups = mapOf(
        "Dobranoc" to PresetGroup.DAY, "Dzień dobry" to PresetGroup.DAY, "Słaba bateria" to PresetGroup.BATTERY,
        "Naładowano" to PresetGroup.BATTERY, "Porządek w Pobranych" to PresetGroup.FILES, "Kopia nowych zdjęć" to PresetGroup.FILES,
        "Pij wodę" to PresetGroup.HEALTH, "Słuchawki = muzyka" to PresetGroup.CONNECT, "Ładowanie nocą" to PresetGroup.BATTERY,
        "SMS do webhooka" to PresetGroup.ADVANCED, "Dziennik powiadomień" to PresetGroup.MESSAGES, "Latarka z zewnątrz" to PresetGroup.ADVANCED,
        "Spotkanie" to PresetGroup.WORK, "Licznik odblokowań" to PresetGroup.HEALTH, "Wyzeruj liczniki" to PresetGroup.HEALTH,
    )

    private val basic: List<Preset> get() = basicList.map { it.copy(group = basicGroups[it.title] ?: it.group) }

    private val basicList: List<Preset> = listOf(
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

    private val extended: List<Preset> = listOf(
        // --- Dzień i noc ---
        Preset(
            "Budzik na jutro", "W wieczory przed dniem roboczym o 22:00 sam ustawia budzik na 6:45 i przypomina o ładowarce.",
            Routine(
                name = "Budzik na jutro",
                triggers = listOf(b("t.time", "time" to "22:00", "days" to "71234")),
                actions = listOf(
                    b("a.alarm", "time" to "06:45", "label" to "Praca", "silent" to "yes"),
                    b("a.skip_if", "name" to "charging", "op" to "eq", "value" to "tak", "count" to "1"),
                    b("a.notify", "title" to "Budzik ustawiony na 6:45", "text" to "Bateria {battery}% – podłącz ładowarkę na noc"),
                ),
            ),
            PresetGroup.DAY,
        ),
        Preset(
            "Tryb kina", "Jednym dotknięciem: pełna cisza, ciemny ekran i blokada obrotu – po 2,5 h wszystko wraca.",
            Routine(
                name = "Tryb kina",
                triggers = listOf(b("t.manual")),
                actions = listOf(
                    b("a.dnd", "mode" to "none"),
                    b("a.brightness", "mode" to "manual", "level" to "3"),
                    b("a.rotation", "state" to "off"),
                    b("a.toast", "text" to "Tryb kina do {time} + 2,5 h"),
                    b("a.wait", "seconds" to "9000"),
                    b("a.dnd", "mode" to "off"),
                    b("a.brightness", "mode" to "auto"),
                    b("a.rotation", "state" to "on"),
                ),
            ),
            PresetGroup.DAY,
        ),
        Preset(
            "Pogoda o poranku", "W dni robocze o 7:30 pobiera prognozę z wttr.in i czyta temperaturę na głos.",
            Routine(
                name = "Pogoda o poranku",
                triggers = listOf(b("t.time", "time" to "07:30", "days" to "12345")),
                conditions = listOf(b("c.network", "type" to "online")),
                actions = listOf(
                    b("a.http", "method" to "GET", "url" to "https://wttr.in/Warszawa?format=j1", "save_to" to "pogoda"),
                    b("a.json_var", "source" to "{pogoda}", "path" to "current_condition.0.temp_C", "name" to "temperatura"),
                    b("a.json_var", "source" to "{pogoda}", "path" to "current_condition.0.FeelsLikeC", "name" to "odczuwalna"),
                    b("a.speak", "text" to "Dzień dobry. Na zewnątrz {temperatura} stopni, odczuwalnie {odczuwalna}."),
                    b("a.delete_var", "name" to "pogoda"),
                ),
            ),
            PresetGroup.DAY,
        ),
        Preset(
            "Raport dnia", "O 21:30 podsumowuje dzień: szklanki wody, odblokowania i bateria – w powiadomieniu i dzienniku.",
            Routine(
                name = "Raport dnia",
                triggers = listOf(b("t.time", "time" to "21:30", "days" to "1234567")),
                actions = listOf(
                    b("a.date_var", "format" to "EEEE, d MMMM", "name" to "dzis"),
                    b("a.notify", "title" to "Podsumowanie: {dzis}", "text" to "Woda: {woda} · Odblokowania: {odblokowania} · Bateria: {battery}%"),
                    b("a.log", "text" to "Raport {date}: woda {woda}, odblokowania {odblokowania}"),
                ),
            ),
            PresetGroup.DAY,
        ),
        // --- Bateria i ładowanie ---
        Preset(
            "Przegrzanie baterii", "Powyżej 42°C: pauza muzyki, ciemny ekran, krótsze wygaszanie i ostrzeżenie.",
            Routine(
                name = "Przegrzanie baterii",
                triggers = listOf(b("t.battery_temp", "dir" to "above", "temp" to "42")),
                actions = listOf(
                    b("a.media", "cmd" to "pause"),
                    b("a.brightness", "mode" to "manual", "level" to "15"),
                    b("a.screen_timeout", "seconds" to "15"),
                    b("a.vibrate", "pattern" to "long"),
                    b("a.notify", "title" to "Bateria {battery_temp}°C", "text" to "Telefon się przegrzewa – odłóż go w chłodne miejsce", "priority" to "high"),
                    b("a.log", "text" to "Przegrzanie: {battery_temp}°C przy {battery}%"),
                ),
            ),
            PresetGroup.BATTERY,
        ),
        Preset(
            "Bateria krytyczna", "Przy 5% bez ładowarki maksymalnie oszczędza: jasność, wygaszanie, wyciszenie multimediów.",
            Routine(
                name = "Bateria krytyczna",
                triggers = listOf(b("t.battery", "dir" to "below", "level" to "5")),
                conditions = listOf(b("c.charging", "state" to "no")),
                actions = listOf(
                    b("a.brightness", "mode" to "manual", "level" to "5"),
                    b("a.screen_timeout", "seconds" to "15"),
                    b("a.volume_step", "stream" to "music", "cmd" to "mute"),
                    b("a.notify", "title" to "Zostało {battery}%", "text" to "Tryb przetrwania włączony", "priority" to "high"),
                ),
            ),
            PresetGroup.BATTERY,
        ),
        Preset(
            "Nocny stolik", "Telefon na ładowarce między 22 a 6: tylko alarmy, minimalna jasność, szybkie wygaszanie.",
            Routine(
                name = "Nocny stolik",
                triggers = listOf(b("t.power", "state" to "connected")),
                conditions = listOf(b("c.time_window", "from" to "22:00", "to" to "06:00")),
                actions = listOf(
                    b("a.dnd", "mode" to "alarms"),
                    b("a.brightness", "mode" to "manual", "level" to "2"),
                    b("a.screen_timeout", "seconds" to "15"),
                    b("a.notify_clear"),
                ),
            ),
            PresetGroup.BATTERY,
        ),
        // --- Praca i skupienie ---
        Preset(
            "Początek pracy", "W dni robocze o 9:00 przy Wi-Fi: wibracje, tylko priorytetowe powiadomienia, wyciszone multimedia.",
            Routine(
                name = "Początek pracy",
                triggers = listOf(b("t.time", "time" to "09:00", "days" to "12345")),
                conditions = listOf(b("c.network", "type" to "wifi")),
                actions = listOf(
                    b("a.ringer", "mode" to "vibrate"),
                    b("a.dnd", "mode" to "priority"),
                    b("a.volume", "stream" to "music", "level" to "0"),
                    b("a.notify", "title" to "Tryb pracy", "text" to "Skupienie do 17:00"),
                ),
            ),
            PresetGroup.WORK,
        ),
        Preset(
            "Koniec pracy", "O 17:00 w dni robocze przywraca dźwięki i proponuje trasę do domu.",
            Routine(
                name = "Koniec pracy",
                triggers = listOf(b("t.time", "time" to "17:00", "days" to "12345")),
                actions = listOf(
                    b("a.dnd", "mode" to "off"),
                    b("a.ringer", "mode" to "normal"),
                    b("a.volume", "stream" to "ring", "level" to "70"),
                    b("a.navigate", "place" to "Dom"),
                ),
            ),
            PresetGroup.WORK,
        ),
        Preset(
            "Pomodoro", "25 minut skupienia z ciszą i minutnikiem, potem 5 minut przerwy – licznik rund w zmiennej.",
            Routine(
                name = "Pomodoro",
                triggers = listOf(b("t.manual")),
                actions = listOf(
                    b("a.dnd", "mode" to "priority"),
                    b("a.timer", "minutes" to "25", "label" to "Pomodoro", "silent" to "yes"),
                    b("a.wait", "seconds" to "1500"),
                    b("a.calc_var", "name" to "pomodoro", "op" to "add", "amount" to "1"),
                    b("a.dnd", "mode" to "off"),
                    b("a.vibrate", "pattern" to "double"),
                    b("a.notify", "title" to "Przerwa 5 minut", "text" to "Ukończone rundy dziś: {pomodoro}"),
                ),
            ),
            PresetGroup.WORK,
        ),
        // --- Łączność i podróże ---
        Preset(
            "W samochodzie", "Po połączeniu z Bluetooth auta: głośniej, muzyka gra, powitanie głosowe.",
            Routine(
                name = "W samochodzie",
                triggers = listOf(b("t.bt_device", "state" to "connected", "name" to "Car")),
                actions = listOf(
                    b("a.volume", "stream" to "music", "level" to "80"),
                    b("a.wait", "seconds" to "3"),
                    b("a.media", "cmd" to "play"),
                    b("a.speak", "text" to "Szerokiej drogi. Bateria {battery} procent."),
                ),
            ),
            PresetGroup.CONNECT,
        ),
        Preset(
            "Zaparkowano", "Po rozłączeniu z autem zapisuje godzinę parkowania i przypomina o niej.",
            Routine(
                name = "Zaparkowano",
                triggers = listOf(b("t.bt_device", "state" to "disconnected", "name" to "Car")),
                actions = listOf(
                    b("a.set_var", "name" to "parking", "value" to "{datetime}"),
                    b("a.media", "cmd" to "pause"),
                    b("a.notify", "title" to "Zaparkowano o {time}", "text" to "Zapisane w zmiennej {parking}"),
                ),
            ),
            PresetGroup.CONNECT,
        ),
        Preset(
            "Brak internetu", "Zapamiętuje, kiedy zniknął internet, i po powrocie mówi, jak długo go nie było.",
            Routine(
                name = "Brak internetu",
                triggers = listOf(b("t.internet", "state" to "offline")),
                actions = listOf(
                    b("a.set_var", "name" to "offline_od", "value" to "{time}"),
                    b("a.log", "text" to "Utracono internet o {time}"),
                ),
            ),
            PresetGroup.CONNECT,
        ),
        Preset(
            "Internet wrócił", "Gdy internet wraca po przerwie, pokazuje od kiedy go nie było.",
            Routine(
                name = "Internet wrócił",
                triggers = listOf(b("t.internet", "state" to "online")),
                conditions = listOf(b("c.variable", "name" to "offline_od", "op" to "set")),
                actions = listOf(
                    b("a.notify", "title" to "Internet wrócił ({network})", "text" to "Brak połączenia od {offline_od} do {time}"),
                    b("a.delete_var", "name" to "offline_od"),
                ),
            ),
            PresetGroup.CONNECT,
        ),
        Preset(
            "Ochrona słuchu", "Po podłączeniu słuchawek ścisza multimedia, jeśli głośność przekracza 60%.",
            Routine(
                name = "Ochrona słuchu",
                triggers = listOf(b("t.headset", "state" to "connected"), b("t.bt_device", "state" to "connected")),
                conditions = listOf(b("c.volume", "stream" to "music", "min" to "61", "max" to "100")),
                actions = listOf(
                    b("a.volume", "stream" to "music", "level" to "45"),
                    b("a.toast", "text" to "Ściszono do 45% dla ochrony słuchu"),
                ),
            ),
            PresetGroup.CONNECT,
        ),
        // --- Wiadomości i połączenia ---
        Preset(
            "Kod z SMS do schowka", "Wyciąga 4–8 cyfrowy kod z SMS-a, kopiuje go do schowka i pokazuje w powiadomieniu.",
            Routine(
                name = "Kod z SMS do schowka",
                triggers = listOf(b("t.sms", "contains" to "kod")),
                actions = listOf(
                    b("a.text_var", "input" to "{sms_body}", "op" to "regex", "arg1" to "\\b(\\d{4,8})\\b", "save_to" to "kod"),
                    b("a.skip_if", "name" to "kod", "op" to "empty", "count" to "2"),
                    b("a.clipboard", "text" to "{kod}"),
                    b("a.notify", "title" to "Kod {kod}", "text" to "Skopiowano do schowka (od {sms_from})", "priority" to "high"),
                ),
            ),
            PresetGroup.MESSAGES,
        ),
        Preset(
            "Pauza na rozmowę", "Gdy dzwoni telefon, a gra muzyka – pauzuje ją i zapamiętuje to.",
            Routine(
                name = "Pauza na rozmowę",
                triggers = listOf(b("t.call", "state" to "ringing")),
                conditions = listOf(b("c.music", "state" to "yes")),
                actions = listOf(
                    b("a.media", "cmd" to "pause"),
                    b("a.set_var", "name" to "muzyka_pauza", "value" to "1"),
                ),
            ),
            PresetGroup.MESSAGES,
        ),
        Preset(
            "Wznów po rozmowie", "Po zakończeniu rozmowy wznawia muzykę, jeśli to rutyna ją zatrzymała.",
            Routine(
                name = "Wznów po rozmowie",
                triggers = listOf(b("t.call", "state" to "idle")),
                conditions = listOf(b("c.variable", "name" to "muzyka_pauza", "op" to "eq", "value" to "1")),
                actions = listOf(
                    b("a.wait", "seconds" to "2"),
                    b("a.media", "cmd" to "play"),
                    b("a.set_var", "name" to "muzyka_pauza", "value" to "0"),
                ),
            ),
            PresetGroup.MESSAGES,
        ),
        Preset(
            "Autoodpowiedź na urlopie", "Latem odpisuje SMS-em na wiadomości (najwyżej raz na godzinę).",
            Routine(
                name = "Autoodpowiedź na urlopie",
                triggers = listOf(b("t.sms")),
                conditions = listOf(b("c.date_range", "from" to "07-01", "to" to "08-31"), b("c.since_run", "minutes" to "60")),
                actions = listOf(
                    b("a.sms", "number" to "{sms_from}", "text" to "Jestem na urlopie, odpiszę po powrocie. Pilne sprawy: e-mail."),
                    b("a.log", "text" to "Autoodpowiedź do {sms_from}"),
                ),
            ),
            PresetGroup.MESSAGES,
        ),
        Preset(
            "Nowa aplikacja", "Odnotowuje każdą zainstalowaną aplikację w dzienniku i w powiadomieniu.",
            Routine(
                name = "Nowa aplikacja",
                triggers = listOf(b("t.package", "event" to "added")),
                actions = listOf(
                    b("a.notify", "title" to "Zainstalowano {app_name}", "text" to "Pakiet {package} · {datetime}"),
                    b("a.log", "text" to "Instalacja: {app_name} ({package})"),
                ),
            ),
            PresetGroup.MESSAGES,
        ),
        // --- Zdrowie i nawyki ---
        Preset(
            "Leki rano i wieczorem", "O 8:00 i 20:00 ważne przypomnienie z wibracją i licznikiem dawek.",
            Routine(
                name = "Leki",
                triggers = listOf(b("t.time", "time" to "08:00", "days" to "1234567"), b("t.time", "time" to "20:00", "days" to "1234567")),
                actions = listOf(
                    b("a.calc_var", "name" to "dawki", "op" to "add", "amount" to "1"),
                    b("a.vibrate", "pattern" to "double"),
                    b("a.notify", "title" to "Czas na leki", "text" to "Dawka nr {dawki} · {time}", "priority" to "high"),
                ),
            ),
            PresetGroup.HEALTH,
        ),
        Preset(
            "Przerwa od ekranu", "W godzinach pracy co 50 minut, gdy ekran jest włączony – przypomina o przerwie.",
            Routine(
                name = "Przerwa od ekranu",
                triggers = listOf(b("t.interval", "minutes" to "50")),
                conditions = listOf(
                    b("c.days", "days" to "12345"), b("c.time_window", "from" to "09:00", "to" to "17:00"), b("c.screen", "state" to "on"),
                ),
                actions = listOf(
                    b("a.vibrate", "pattern" to "short"),
                    b("a.notify", "title" to "Przerwa", "text" to "Wstań, rozciągnij się i popatrz w dal przez 5 minut"),
                ),
            ),
            PresetGroup.HEALTH,
        ),
        Preset(
            "Rzut kostką", "Losuje liczbę 1–6 i ogłasza wynik – na planszówki albo trudne decyzje.",
            Routine(
                name = "Rzut kostką",
                triggers = listOf(b("t.manual")),
                actions = listOf(
                    b("a.random_var", "min" to "1", "max" to "6", "name" to "kostka"),
                    b("a.vibrate", "pattern" to "short"),
                    b("a.toast", "text" to "Wypadło: {kostka}"),
                    b("a.speak", "text" to "Wypadło {kostka}"),
                ),
            ),
            PresetGroup.HEALTH,
        ),
        // --- Pliki ---
        Preset(
            "Sprzątanie zrzutów ekranu", "W każdą niedzielę o 4:00 usuwa zrzuty ekranu starsze niż 30 dni.",
            Routine(
                name = "Sprzątanie zrzutów ekranu",
                triggers = listOf(b("t.time", "time" to "04:00", "days" to "7")),
                actions = listOf(
                    b("a.files", "op" to "delete", "pattern" to "Screenshot*;*.png", "older_days" to "30"),
                    b("a.log", "text" to "Usunięto zrzutów: {files_count}"),
                ),
            ),
            PresetGroup.FILES,
        ),
        // --- Integracje i zaawansowane ---
        Preset(
            "Stan telefonu do Home Assistant", "Co 30 minut wysyła baterię, ładowanie, temperaturę i sieć do webhooka.",
            Routine(
                name = "Stan telefonu do HA",
                triggers = listOf(b("t.interval", "minutes" to "30")),
                conditions = listOf(b("c.network", "type" to "online")),
                actions = listOf(
                    b(
                        "a.http", "method" to "POST", "url" to "https://homeassistant.local:8123/api/webhook/telefon",
                        "content_type" to "application/json",
                        "body" to "{\"battery\":{battery},\"charging\":\"{charging}\",\"temp\":\"{battery_temp}\",\"network\":\"{network}\",\"at\":\"{datetime}\"}",
                        "save_to" to "",
                    ),
                ),
            ),
            PresetGroup.ADVANCED,
        ),
        Preset(
            "Rachunki co miesiąc", "10. dnia każdego miesiąca o 9:00 ważne przypomnienie o opłatach.",
            Routine(
                name = "Rachunki",
                triggers = listOf(b("t.monthly", "day" to "10", "time" to "09:00")),
                actions = listOf(
                    b("a.date_var", "format" to "LLLL yyyy", "name" to "miesiac"),
                    b("a.notify", "title" to "Rachunki za {miesiac}", "text" to "Prąd, internet, telefon, czynsz", "priority" to "high"),
                    b("a.vibrate", "pattern" to "double"),
                ),
            ),
            PresetGroup.ADVANCED,
        ),
        Preset(
            "Jednorazowe przypomnienie", "Raz, w wybranym dniu i godzinie – potem rutyna sama się wyłącza.",
            Routine(
                name = "Przypomnienie",
                triggers = listOf(b("t.once", "date" to "2026-12-24", "time" to "18:00")),
                actions = listOf(
                    b("a.notify", "title" to "Przypomnienie", "text" to "Wigilia – czas na kolację!", "priority" to "high"),
                    b("a.sound", "kind" to "notification"),
                    b("a.toggle_routine", "name" to "Przypomnienie", "state" to "off"),
                ),
            ),
            PresetGroup.ADVANCED,
        ),
    )
}
