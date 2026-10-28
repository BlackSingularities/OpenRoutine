package com.blacksingularities.openroutine.model

enum class Kind(val title: String, val plural: String) {
    TRIGGER("Wyzwalacz", "Gdy"),
    CONDITION("Warunek", "Jeśli"),
    ACTION("Akcja", "Wykonaj"),
}

enum class ParamType { TEXT, MULTILINE, NUMBER, TIME, DAYS, CHOICE, BOOL, APP, FOLDER }

enum class Category(val title: String) {
    TIME("Czas"),
    DEVICE("Urządzenie"),
    CONNECT("Łączność"),
    FILES("Pliki"),
    MESSAGES("Powiadomienia i SMS"),
    SOUND("Dźwięk i ekran"),
    APPS("Aplikacje i sieć"),
    LOGIC("Logika i zmienne"),
    INTEGRATION("Integracje"),
}

/** Uprawnienia i ustawienia systemu, których wymaga klocek. */
enum class Need(val title: String, val description: String) {
    NOTIFICATIONS("Powiadomienia", "Pokazywanie powiadomień z akcji i stanu działania."),
    EXACT_ALARM("Dokładne alarmy", "Uruchamianie rutyn co do minuty o wybranej godzinie."),
    MONITOR("Praca w tle", "Nasłuchiwanie zdarzeń (bateria, ekran, sieć, foldery) bez ograniczeń oszczędzania baterii."),
    NOTIFICATION_ACCESS("Dostęp do powiadomień", "Reagowanie na powiadomienia innych aplikacji."),
    DND_ACCESS("Tryb Nie przeszkadzać", "Zmiana trybu dzwonka i Nie przeszkadzać."),
    WRITE_SETTINGS("Ustawienia systemu", "Zmiana jasności ekranu."),
    OVERLAY("Wyświetlanie nad aplikacjami", "Otwieranie aplikacji i stron, gdy OpenRoutine działa w tle."),
    SMS_RECEIVE("Odbieranie SMS", "Reagowanie na przychodzące wiadomości SMS."),
    SMS_SEND("Wysyłanie SMS", "Wysyłanie SMS-ów z akcji."),
}

data class Param(
    val key: String,
    val label: String,
    val type: ParamType = ParamType.TEXT,
    val default: String = "",
    val options: List<Pair<String, String>> = emptyList(),
    val hint: String = "",
    val optional: Boolean = false,
)

class BlockSpec(
    val id: String,
    val kind: Kind,
    val category: Category,
    val title: String,
    val description: String,
    val params: List<Param> = emptyList(),
    val needs: Set<Need> = emptySet(),
    /** Zmienne, które wyzwalacz przekazuje akcjom, np. {file}. */
    val provides: List<String> = emptyList(),
    private val describe: ((P) -> String)? = null,
) {
    fun defaults(): Map<String, String> = params.associate { it.key to it.default }
    fun summary(values: Map<String, String>): String = describe?.invoke(P(this, values)) ?: title
    fun param(key: String): Param? = params.firstOrNull { it.key == key }
}

/** Dostęp do wartości parametrów w opisach klocków. */
class P(private val spec: BlockSpec, private val values: Map<String, String>) {
    operator fun get(key: String): String = values[key] ?: spec.param(key)?.default.orEmpty()
    fun label(key: String): String {
        val v = get(key)
        return spec.param(key)?.options?.firstOrNull { it.first == v }?.second ?: v
    }
    fun or(key: String, fallback: String): String = get(key).ifBlank { fallback }
    fun folder(key: String): String = Fmt.folderName(get(key)).ifBlank { "(wybierz folder)" }
}

private object Opt {
    val onOff = listOf("on" to "Włączony", "off" to "Wyłączony")
    val yesNo = listOf("yes" to "Tak", "no" to "Nie")
    val connected = listOf("connected" to "Podłączone", "disconnected" to "Odłączone")
    val belowAbove = listOf("below" to "Spada do", "above" to "Rośnie do")
    val screen = listOf("on" to "Włączony", "off" to "Wyłączony", "unlock" to "Odblokowany")
    val network = listOf("wifi" to "Wi-Fi", "cellular" to "Komórkowa", "online" to "Dowolna", "none" to "Brak")
    val ringer = listOf("normal" to "Dźwięk", "vibrate" to "Wibracje", "silent" to "Cisza")
    val dnd = listOf("off" to "Wyłączony", "priority" to "Tylko priorytetowe", "alarms" to "Tylko alarmy", "none" to "Pełna cisza")
    val streams = listOf("music" to "Multimedia", "ring" to "Dzwonek", "notification" to "Powiadomienia", "alarm" to "Alarm", "call" to "Rozmowa")
    val vibrate = listOf("short" to "Krótko", "long" to "Długo", "double" to "Podwójnie", "sos" to "SOS")
    val sound = listOf("notification" to "Powiadomienie", "alarm" to "Alarm", "ringtone" to "Dzwonek")
    val torch = listOf("on" to "Włącz", "off" to "Wyłącz", "toggle" to "Przełącz")
    val media = listOf("play_pause" to "Odtwórz/pauza", "play" to "Odtwórz", "pause" to "Pauza", "next" to "Następny", "previous" to "Poprzedni", "stop" to "Stop")
    val http = listOf("GET" to "GET", "POST" to "POST", "PUT" to "PUT", "DELETE" to "DELETE")
    val compare = listOf("eq" to "równa się", "ne" to "różni się od", "contains" to "zawiera", "gt" to "większa niż", "lt" to "mniejsza niż", "empty" to "jest pusta", "set" to "ma wartość")
    val calc = listOf("add" to "Dodaj", "sub" to "Odejmij", "mul" to "Pomnóż", "div" to "Podziel")
    val files = listOf("copy" to "Kopiuj", "move" to "Przenieś", "delete" to "Usuń")
    val writeMode = listOf("append" to "Dopisz na końcu", "overwrite" to "Nadpisz")
    val routineState = listOf("on" to "Włącz", "off" to "Wyłącz", "toggle" to "Przełącz")
    val priority = listOf("normal" to "Zwykły", "high" to "Wysoki")
    val brightness = listOf("manual" to "Ręczna", "auto" to "Automatyczna")
    val contentType = listOf("application/json" to "JSON", "text/plain" to "Tekst", "application/x-www-form-urlencoded" to "Formularz")
}

object Catalog {
    val all: List<BlockSpec> = buildList {
        triggers()
        conditions()
        actions()
    }
    private val byId: Map<String, BlockSpec> by lazy { all.associateBy { it.id } }

    operator fun get(id: String): BlockSpec? = byId[id]
    fun of(kind: Kind): List<BlockSpec> = all.filter { it.kind == kind }

    /** Zmienne wbudowane, dostępne w każdym polu tekstowym. */
    val builtins = listOf(
        "time" to "godzina (12:30)",
        "date" to "data (2026-10-28)",
        "datetime" to "data i godzina",
        "weekday" to "dzień tygodnia",
        "battery" to "poziom baterii %",
        "routine" to "nazwa rutyny",
        "trigger" to "nazwa wyzwalacza",
        "random" to "liczba 0–100",
    )
}

private fun MutableList<BlockSpec>.t(
    id: String, cat: Category, title: String, desc: String, params: List<Param> = emptyList(),
    needs: Set<Need> = emptySet(), provides: List<String> = emptyList(), d: ((P) -> String)? = null,
) = add(BlockSpec("t.$id", Kind.TRIGGER, cat, title, desc, params, needs, provides, d))

private fun MutableList<BlockSpec>.c(
    id: String, cat: Category, title: String, desc: String, params: List<Param> = emptyList(),
    needs: Set<Need> = emptySet(), d: ((P) -> String)? = null,
) = add(BlockSpec("c.$id", Kind.CONDITION, cat, title, desc, params, needs, emptyList(), d))

private fun MutableList<BlockSpec>.a(
    id: String, cat: Category, title: String, desc: String, params: List<Param> = emptyList(),
    needs: Set<Need> = emptySet(), d: ((P) -> String)? = null,
) = add(BlockSpec("a.$id", Kind.ACTION, cat, title, desc, params, needs, emptyList(), d))

private fun choice(key: String, label: String, options: List<Pair<String, String>>, default: String = options.first().first) =
    Param(key, label, ParamType.CHOICE, default, options)

private fun MutableList<BlockSpec>.triggers() {
    val m = setOf(Need.MONITOR)
    t("time", Category.TIME, "O godzinie", "Codziennie lub w wybrane dni tygodnia.",
        listOf(Param("time", "Godzina", ParamType.TIME, "07:00"), Param("days", "Dni", ParamType.DAYS, "1234567")),
        setOf(Need.EXACT_ALARM)) { "o ${it["time"]}, ${Fmt.days(it["days"])}" }
    t("interval", Category.TIME, "Co pewien czas", "Powtarzanie co określoną liczbę minut.",
        listOf(Param("minutes", "Co ile minut", ParamType.NUMBER, "60")),
        setOf(Need.EXACT_ALARM)) { "co ${Fmt.minutes(it["minutes"])}" }
    t("boot", Category.DEVICE, "Po uruchomieniu telefonu", "Raz, po włączeniu urządzenia.") { "po uruchomieniu telefonu" }
    t("manual", Category.INTEGRATION, "Ręcznie lub skrótem", "Przycisk „Uruchom”, skrót na ekranie głównym.") { "ręcznie / skrótem" }
    t("battery", Category.DEVICE, "Poziom baterii", "Gdy bateria przekroczy próg.",
        listOf(choice("dir", "Kiedy", Opt.belowAbove), Param("level", "Próg %", ParamType.NUMBER, "20")),
        m, listOf("battery")) { "bateria ${it.label("dir").lowercase()} ${it["level"]}%" }
    t("power", Category.DEVICE, "Ładowarka", "Podłączenie lub odłączenie zasilania.",
        listOf(choice("state", "Stan", Opt.connected)), m) { "ładowarka: ${it.label("state").lowercase()}" }
    t("screen", Category.DEVICE, "Ekran", "Włączenie, wyłączenie lub odblokowanie ekranu.",
        listOf(choice("state", "Stan", Opt.screen)), m) { "ekran: ${it.label("state").lowercase()}" }
    t("headset", Category.SOUND, "Słuchawki przewodowe", "Podłączenie lub odłączenie słuchawek.",
        listOf(choice("state", "Stan", Opt.connected)), m) { "słuchawki: ${it.label("state").lowercase()}" }
    t("wifi", Category.CONNECT, "Wi-Fi", "Połączenie z siecią Wi-Fi lub jej utrata.",
        listOf(choice("state", "Stan", listOf("connected" to "Połączono", "disconnected" to "Rozłączono"))), m) { "Wi-Fi: ${it.label("state").lowercase()}" }
    t("bluetooth", Category.CONNECT, "Bluetooth", "Włączenie lub wyłączenie Bluetooth.",
        listOf(choice("state", "Stan", Opt.onOff)), m) { "Bluetooth: ${it.label("state").lowercase()}" }
    t("airplane", Category.CONNECT, "Tryb samolotowy", "Zmiana trybu samolotowego.",
        listOf(choice("state", "Stan", Opt.onOff)), m) { "tryb samolotowy: ${it.label("state").lowercase()}" }
    t("powersave", Category.DEVICE, "Oszczędzanie baterii", "Zmiana trybu oszczędzania energii.",
        listOf(choice("state", "Stan", Opt.onOff)), m) { "oszczędzanie: ${it.label("state").lowercase()}" }
    t("folder", Category.FILES, "Nowy plik w folderze", "Sprawdza folder i reaguje na każdy nowy plik.",
        listOf(
            Param("folder", "Folder", ParamType.FOLDER),
            Param("pattern", "Wzorzec nazwy", ParamType.TEXT, "*", hint = "np. *.pdf;*.jpg"),
            Param("minutes", "Sprawdzaj co (min)", ParamType.NUMBER, "5"),
        ), m, listOf("file", "file_uri", "file_size")) { "nowy plik ${it.or("pattern", "*")} w ${it.folder("folder")}" }
    t("notification", Category.MESSAGES, "Powiadomienie aplikacji", "Gdy inna aplikacja pokaże powiadomienie.",
        listOf(
            Param("app", "Aplikacja", ParamType.APP, optional = true, hint = "puste = dowolna"),
            Param("contains", "Tekst zawiera", optional = true),
        ), setOf(Need.NOTIFICATION_ACCESS), listOf("notif_app", "notif_title", "notif_text")) {
        val app = Fmt.appName(it["app"]).ifBlank { "dowolnej aplikacji" }
        "powiadomienie z $app" + if (it["contains"].isNotBlank()) " z „${it["contains"]}”" else ""
    }
    t("sms", Category.MESSAGES, "Przychodzący SMS", "Gdy przyjdzie SMS (opcjonalnie od nadawcy lub z tekstem).",
        listOf(Param("from", "Nadawca zawiera", optional = true), Param("contains", "Treść zawiera", optional = true)),
        setOf(Need.SMS_RECEIVE), listOf("sms_from", "sms_body")) {
        "SMS" + (if (it["from"].isNotBlank()) " od ${it["from"]}" else "") + (if (it["contains"].isNotBlank()) " z „${it["contains"]}”" else "")
    }
    t("external", Category.INTEGRATION, "Zdarzenie z innej aplikacji", "Intencja wysłana z tokenem przez Taskera, skrypt lub adb.",
        listOf(Param("event", "Nazwa zdarzenia", default = "moje_zdarzenie")),
        provides = listOf("extra")) { "zdarzenie „${it["event"]}”" }
}

private fun MutableList<BlockSpec>.conditions() {
    c("time_window", Category.TIME, "Pora dnia", "Tylko między podanymi godzinami (także przez północ).",
        listOf(Param("from", "Od", ParamType.TIME, "08:00"), Param("to", "Do", ParamType.TIME, "22:00"))) { "między ${it["from"]} a ${it["to"]}" }
    c("days", Category.TIME, "Dzień tygodnia", "Tylko w wybrane dni.",
        listOf(Param("days", "Dni", ParamType.DAYS, "12345"))) { Fmt.days(it["days"]) }
    c("battery", Category.DEVICE, "Poziom baterii", "Bateria w podanym zakresie.",
        listOf(Param("min", "Od %", ParamType.NUMBER, "0"), Param("max", "Do %", ParamType.NUMBER, "100"))) { "bateria ${it["min"]}–${it["max"]}%" }
    c("charging", Category.DEVICE, "Ładowanie", "Czy telefon jest podłączony do zasilania.",
        listOf(choice("state", "Ładuje się", Opt.yesNo))) { if (it["state"] == "yes") "ładuje się" else "nie ładuje się" }
    c("network", Category.CONNECT, "Sieć", "Rodzaj aktywnego połączenia.",
        listOf(choice("type", "Połączenie", Opt.network))) { "sieć: ${it.label("type").lowercase()}" }
    c("screen", Category.DEVICE, "Ekran", "Czy ekran jest włączony.",
        listOf(choice("state", "Ekran", Opt.onOff))) { "ekran ${it.label("state").lowercase()}" }
    c("headset", Category.SOUND, "Słuchawki", "Czy podłączone są słuchawki (przewodowe lub Bluetooth).",
        listOf(choice("state", "Podłączone", Opt.yesNo))) { if (it["state"] == "yes") "słuchawki podłączone" else "bez słuchawek" }
    c("ringer", Category.SOUND, "Tryb dzwonka", "Bieżący tryb dzwonka.",
        listOf(choice("mode", "Tryb", Opt.ringer))) { "dzwonek: ${it.label("mode").lowercase()}" }
    c("dnd", Category.SOUND, "Nie przeszkadzać", "Czy tryb Nie przeszkadzać jest włączony.",
        listOf(choice("state", "Tryb", Opt.onOff))) { "Nie przeszkadzać ${it.label("state").lowercase()}" }
    c("powersave", Category.DEVICE, "Oszczędzanie baterii", "Czy tryb oszczędzania jest włączony.",
        listOf(choice("state", "Tryb", Opt.onOff))) { "oszczędzanie ${it.label("state").lowercase()}" }
    c("bluetooth", Category.CONNECT, "Bluetooth", "Czy Bluetooth jest włączony.",
        listOf(choice("state", "Stan", Opt.onOff))) { "Bluetooth ${it.label("state").lowercase()}" }
    c("variable", Category.LOGIC, "Zmienna", "Porównanie zmiennej (także {battery}, {sms_body} itd.).",
        listOf(Param("name", "Zmienna", default = "licznik"), choice("op", "Warunek", Opt.compare), Param("value", "Wartość", optional = true))) {
        val op = it["op"]
        if (op == "empty" || op == "set") "{${it["name"]}} ${it.label("op")}" else "{${it["name"]}} ${it.label("op")} ${it["value"]}"
    }
    c("file_exists", Category.FILES, "Plik w folderze", "Czy w folderze jest plik pasujący do wzorca.",
        listOf(Param("folder", "Folder", ParamType.FOLDER), Param("pattern", "Wzorzec nazwy", default = "*"), choice("state", "Istnieje", Opt.yesNo))) {
        (if (it["state"] == "yes") "jest " else "brak ") + "${it["pattern"]} w ${it.folder("folder")}"
    }
    c("chance", Category.LOGIC, "Losowo", "Przepuszcza z podanym prawdopodobieństwem.",
        listOf(Param("percent", "Szansa %", ParamType.NUMBER, "50"))) { "losowo ${it["percent"]}%" }
}

private fun MutableList<BlockSpec>.actions() {
    a("notify", Category.MESSAGES, "Pokaż powiadomienie", "Powiadomienie z tytułem i treścią.",
        listOf(Param("title", "Tytuł", default = "OpenRoutine"), Param("text", "Treść", ParamType.MULTILINE), choice("priority", "Priorytet", Opt.priority)),
        setOf(Need.NOTIFICATIONS)) { "powiadomienie „${it.or("text", it["title"])}”" }
    a("toast", Category.MESSAGES, "Krótki komunikat", "Dymek na dole ekranu.",
        listOf(Param("text", "Tekst"))) { "komunikat „${it["text"]}”" }
    a("speak", Category.SOUND, "Powiedz na głos", "Synteza mowy (silnik TTS telefonu).",
        listOf(Param("text", "Tekst", ParamType.MULTILINE))) { "powiedz „${it["text"]}”" }
    a("vibrate", Category.SOUND, "Wibracja", "Wybrany wzór wibracji.",
        listOf(choice("pattern", "Wzór", Opt.vibrate))) { "wibracja: ${it.label("pattern").lowercase()}" }
    a("sound", Category.SOUND, "Odtwórz dźwięk", "Domyślny dźwięk systemowy.",
        listOf(choice("kind", "Dźwięk", Opt.sound))) { "dźwięk: ${it.label("kind").lowercase()}" }
    a("ringer", Category.SOUND, "Tryb dzwonka", "Dźwięk, wibracje lub cisza.",
        listOf(choice("mode", "Tryb", Opt.ringer)), setOf(Need.DND_ACCESS)) { "dzwonek: ${it.label("mode").lowercase()}" }
    a("dnd", Category.SOUND, "Nie przeszkadzać", "Ustawia tryb Nie przeszkadzać.",
        listOf(choice("mode", "Tryb", Opt.dnd)), setOf(Need.DND_ACCESS)) { "Nie przeszkadzać: ${it.label("mode").lowercase()}" }
    a("volume", Category.SOUND, "Głośność", "Głośność wybranego strumienia w procentach.",
        listOf(choice("stream", "Strumień", Opt.streams), Param("level", "Poziom %", ParamType.NUMBER, "50"))) { "głośność ${it.label("stream").lowercase()} ${it["level"]}%" }
    a("brightness", Category.SOUND, "Jasność ekranu", "Jasność ręczna w procentach albo automatyczna.",
        listOf(choice("mode", "Tryb", Opt.brightness), Param("level", "Poziom %", ParamType.NUMBER, "50")),
        setOf(Need.WRITE_SETTINGS)) { if (it["mode"] == "auto") "jasność automatyczna" else "jasność ${it["level"]}%" }
    a("torch", Category.DEVICE, "Latarka", "Włącza, wyłącza lub przełącza latarkę.",
        listOf(choice("state", "Latarka", Opt.torch))) { "latarka: ${it.label("state").lowercase()}" }
    a("media", Category.SOUND, "Sterowanie muzyką", "Przycisk multimediów dla odtwarzacza.",
        listOf(choice("cmd", "Przycisk", Opt.media))) { "muzyka: ${it.label("cmd").lowercase()}" }
    a("open_app", Category.APPS, "Otwórz aplikację", "Uruchamia wybraną aplikację.",
        listOf(Param("app", "Aplikacja", ParamType.APP)), setOf(Need.OVERLAY)) { "otwórz ${Fmt.appName(it["app"]).ifBlank { "(wybierz)" }}" }
    a("open_url", Category.APPS, "Otwórz stronę", "Adres w przeglądarce lub aplikacji (także tel:, geo:, mailto:).",
        listOf(Param("url", "Adres", default = "https://")), setOf(Need.OVERLAY)) { "otwórz ${it["url"]}" }
    a("http", Category.APPS, "Żądanie HTTP", "Webhook lub API; odpowiedź trafia do zmiennej.",
        listOf(
            choice("method", "Metoda", Opt.http), Param("url", "Adres", default = "https://"),
            choice("content_type", "Typ treści", Opt.contentType), Param("body", "Treść", ParamType.MULTILINE, optional = true),
            Param("headers", "Nagłówki", ParamType.MULTILINE, optional = true, hint = "Nazwa: wartość, w osobnych liniach"),
            Param("save_to", "Zapisz odpowiedź do zmiennej", default = "http_response", optional = true),
        )) { "${it["method"]} ${it["url"]}" }
    a("share", Category.APPS, "Udostępnij tekst", "Systemowe okno udostępniania.",
        listOf(Param("text", "Tekst", ParamType.MULTILINE)), setOf(Need.OVERLAY)) { "udostępnij „${it["text"]}”" }
    a("clipboard", Category.APPS, "Kopiuj do schowka", "Umieszcza tekst w schowku.",
        listOf(Param("text", "Tekst"))) { "schowek: „${it["text"]}”" }
    a("sms", Category.MESSAGES, "Wyślij SMS", "SMS na podany numer (z karty domyślnej).",
        listOf(Param("number", "Numer"), Param("text", "Treść", ParamType.MULTILINE)), setOf(Need.SMS_SEND)) { "SMS do ${it["number"]}" }
    a("files", Category.FILES, "Kopiuj / przenieś / usuń pliki", "Pliki z folderu pasujące do wzorca i wieku.",
        listOf(
            choice("op", "Operacja", Opt.files), Param("folder", "Folder źródłowy", ParamType.FOLDER),
            Param("pattern", "Wzorzec nazwy", default = "*", hint = "np. *.pdf;*.jpg lub {file}"),
            Param("target", "Folder docelowy", ParamType.FOLDER, optional = true),
            Param("older_days", "Starsze niż (dni)", ParamType.NUMBER, "0", optional = true),
        )) { "${it.label("op").lowercase()} ${it["pattern"]} z ${it.folder("folder")}" + if (it["op"] != "delete") " do ${it.folder("target")}" else "" }
    a("write_file", Category.FILES, "Zapisz tekst do pliku", "Dopisuje lub nadpisuje plik tekstowy w folderze.",
        listOf(
            Param("folder", "Folder", ParamType.FOLDER), Param("name", "Nazwa pliku", default = "dziennik.txt"),
            Param("text", "Tekst", ParamType.MULTILINE, "{datetime} {routine}"), choice("mode", "Tryb", Opt.writeMode),
        )) { "${it.label("mode").lowercase()}: ${it["name"]} w ${it.folder("folder")}" }
    a("set_var", Category.LOGIC, "Ustaw zmienną", "Zapamiętuje wartość (dostępna jako {nazwa}).",
        listOf(Param("name", "Zmienna", default = "licznik"), Param("value", "Wartość", default = "0"))) { "{${it["name"]}} = ${it["value"]}" }
    a("calc_var", Category.LOGIC, "Licz na zmiennej", "Dodawanie, odejmowanie, mnożenie, dzielenie.",
        listOf(Param("name", "Zmienna", default = "licznik"), choice("op", "Działanie", Opt.calc), Param("amount", "Wartość", ParamType.NUMBER, "1"))) {
        "{${it["name"]}} ${it.label("op").lowercase()} ${it["amount"]}"
    }
    a("wait", Category.LOGIC, "Czekaj", "Pauza przed następną akcją.",
        listOf(Param("seconds", "Sekundy", ParamType.NUMBER, "5"))) { "czekaj ${it["seconds"]} s" }
    a("stop_if", Category.LOGIC, "Przerwij, jeśli…", "Kończy rutynę, gdy zmienna spełnia warunek.",
        listOf(Param("name", "Zmienna", default = "http_response"), choice("op", "Warunek", Opt.compare), Param("value", "Wartość", optional = true))) {
        "przerwij, gdy {${it["name"]}} ${it.label("op")} ${it["value"]}".trim()
    }
    a("run_routine", Category.LOGIC, "Uruchom inną rutynę", "Po nazwie (z jej warunkami).",
        listOf(Param("name", "Nazwa rutyny"))) { "uruchom „${it["name"]}”" }
    a("toggle_routine", Category.LOGIC, "Włącz / wyłącz rutynę", "Zmienia stan innej rutyny.",
        listOf(Param("name", "Nazwa rutyny"), choice("state", "Stan", Opt.routineState))) { "${it.label("state").lowercase()} „${it["name"]}”" }
    a("broadcast", Category.INTEGRATION, "Wyślij intencję", "Broadcast do innej aplikacji (Tasker, Home Assistant itp.).",
        listOf(
            Param("action", "Akcja intencji", default = "com.example.ACTION"), Param("package", "Pakiet docelowy", optional = true),
            Param("extras", "Dodatki", ParamType.MULTILINE, optional = true, hint = "klucz=wartość, w osobnych liniach"),
        )) { "intencja ${it["action"]}" }
    a("log", Category.LOGIC, "Wpis do dziennika", "Zapisuje tekst w dzienniku OpenRoutine.",
        listOf(Param("text", "Tekst", default = "{routine}: {datetime}"))) { "dziennik: „${it["text"]}”" }
}
