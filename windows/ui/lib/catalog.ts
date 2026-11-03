// Katalog klocków: wyzwalacze (t.*), warunki (c.*), akcje (a.*). Klucze parametrów muszą
// zgadzać się z silnikiem w src-tauri/src/{engine,conditions,actions}.rs.

export type Kind = "trigger" | "condition" | "action";
export type PType = "text" | "multiline" | "number" | "time" | "days" | "choice" | "bool" | "app" | "folder" | "file" | "keys" | "routine" | "datetime" | "window" | "var";

export interface Param {
  key: string;
  label: string;
  type?: PType;
  def?: string;
  options?: [string, string][];
  hint?: string;
  optional?: boolean;
}

export interface Spec {
  id: string;
  kind: Kind;
  cat: string;
  title: string;
  desc: string;
  icon: string;
  params?: Param[];
  provides?: string[];
  sum?: (p: (k: string) => string, l: (k: string) => string) => string;
}

export const CATS: Record<string, { title: string; icon: string }> = {
  time: { title: "Czas", icon: "clock" },
  system: { title: "System i zasilanie", icon: "cpu" },
  apps: { title: "Aplikacje i okna", icon: "app" },
  net: { title: "Sieć i urządzenia", icon: "wifi" },
  files: { title: "Pliki i foldery", icon: "folder" },
  input: { title: "Klawiatura i schowek", icon: "keyboard" },
  notify: { title: "Komunikaty i dźwięk", icon: "bell" },
  look: { title: "Wygląd i ekran", icon: "monitor" },
  web: { title: "Internet i integracje", icon: "globe" },
  script: { title: "Skrypty", icon: "terminal" },
  logic: { title: "Logika i zmienne", icon: "logic" },
};

const onOff: [string, string][] = [["on", "Włączone"], ["off", "Wyłączone"]];
const yesNo: [string, string][] = [["yes", "Tak"], ["no", "Nie"]];
const cmpNum: [string, string][] = [["lt", "mniej niż"], ["le", "co najwyżej"], ["gt", "więcej niż"], ["ge", "co najmniej"], ["eq", "równo"]];
const cmpVar: [string, string][] = [
  ["eq", "="], ["ne", "≠"], ["contains", "zawiera"], ["not_contains", "nie zawiera"], ["starts", "zaczyna się od"], ["ends", "kończy się na"],
  ["gt", ">"], ["ge", "≥"], ["lt", "<"], ["le", "≤"], ["regex", "pasuje do regex"], ["wild", "pasuje do wzorca *?"], ["empty", "jest pusta"], ["set", "ma wartość"],
];
const scope: Param = { key: "scope", label: "Zasięg zmiennej", type: "choice", def: "local", options: [["local", "Tylko ta rutyna"], ["global", "Globalna (zapamiętana)"]] };
const conflict: Param = { key: "conflict", label: "Gdy plik istnieje", type: "choice", def: "rename", options: [["rename", "Dodaj numer"], ["overwrite", "Zastąp"], ["skip", "Pomiń"]] };

export const DAY_NAMES = ["Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd"];
export function daysLabel(d: string): string {
  if (!d || d === "1234567") return "codziennie";
  if (d === "12345") return "w dni robocze";
  if (d === "67") return "w weekend";
  return d.split("").map((c) => DAY_NAMES[+c - 1]).filter(Boolean).join(", ");
}
const short = (s: string, n = 34) => (s.length > n ? s.slice(0, n - 1) + "…" : s);
const folderName = (s: string) => (s ? s.replace(/[\\/]+$/, "").split(/[\\/]/).pop() || s : "(wybierz folder)");

export const SPECS: Spec[] = [
  // ======================================================= WYZWALACZE
  { id: "t.time", kind: "trigger", cat: "time", icon: "clock", title: "O godzinie", desc: "Codziennie lub w wybrane dni tygodnia o podanej godzinie.",
    params: [{ key: "time", label: "Godzina", type: "time", def: "08:00" }, { key: "days", label: "Dni", type: "days", def: "1234567" }],
    sum: (p) => `${p("time")} · ${daysLabel(p("days"))}` },
  { id: "t.interval", kind: "trigger", cat: "time", icon: "repeat", title: "Co pewien czas", desc: "Cyklicznie co N minut, opcjonalnie tylko w wybranych godzinach i dniach.",
    params: [{ key: "minutes", label: "Co ile minut", type: "number", def: "30" }, { key: "from", label: "Od godziny", type: "time", optional: true }, { key: "to", label: "Do godziny", type: "time", optional: true }, { key: "days", label: "Dni", type: "days", def: "1234567" }],
    sum: (p) => `co ${p("minutes")} min${p("from") && p("to") ? ` · ${p("from")}–${p("to")}` : ""}` },
  { id: "t.monthday", kind: "trigger", cat: "time", icon: "calendar", title: "W dniu miesiąca", desc: "Wybranego dnia każdego miesiąca (także ostatniego).",
    params: [{ key: "day", label: "Dzień", type: "choice", def: "1", options: [...Array.from({ length: 31 }, (_, i) => [String(i + 1), String(i + 1)] as [string, string]), ["last", "ostatni"]] }, { key: "time", label: "Godzina", type: "time", def: "09:00" }],
    sum: (p) => `${p("day") === "last" ? "ostatni dzień" : p("day") + "."} miesiąca, ${p("time")}` },
  { id: "t.once", kind: "trigger", cat: "time", icon: "calendar", title: "Jednorazowo", desc: "Jeden raz o podanej dacie i godzinie (np. przypomnienie).",
    params: [{ key: "at", label: "Data i godzina", type: "datetime", def: "" }], sum: (p) => p("at") || "(ustaw datę)" },
  { id: "t.startup", kind: "trigger", cat: "time", icon: "power", title: "Po zalogowaniu", desc: "Gdy OpenRoutine wystartuje razem z Windows (lub zostanie uruchomiony).",
    params: [{ key: "delay", label: "Opóźnienie (s)", type: "number", def: "30" }], provides: ["uptime_min"], sum: (p) => `po ${p("delay")} s` },
  { id: "t.manual", kind: "trigger", cat: "time", icon: "play", title: "Ręcznie", desc: "Przyciskiem w aplikacji, z menu ikony w zasobniku, z wiersza poleceń (--run) lub przez HTTP (/run).",
    sum: () => "przycisk, zasobnik, --run" },
  { id: "t.hotkey", kind: "trigger", cat: "input", icon: "keyboard", title: "Skrót klawiszowy", desc: "Globalny skrót działający w każdej aplikacji, np. Ctrl+Alt+N.",
    params: [{ key: "keys", label: "Skrót", type: "keys", def: "ctrl+alt+n" }], sum: (p) => p("keys").split("+").map((k) => k[0]?.toUpperCase() + k.slice(1)).join(" + ") },
  { id: "t.idle", kind: "trigger", cat: "system", icon: "moon", title: "Bezczynność", desc: "Gdy przez N minut nie używasz myszy ani klawiatury.",
    params: [{ key: "minutes", label: "Minut bezczynności", type: "number", def: "10" }], provides: ["idle_min"], sum: (p) => `${p("minutes")} min bez ruchu` },
  { id: "t.active", kind: "trigger", cat: "system", icon: "sun", title: "Powrót do komputera", desc: "Gdy wrócisz po co najmniej N minutach bezczynności.",
    params: [{ key: "minutes", label: "Po przerwie dłuższej niż (min)", type: "number", def: "15" }], provides: ["idle_min"], sum: (p) => `po przerwie ≥ ${p("minutes")} min` },
  { id: "t.lock", kind: "trigger", cat: "system", icon: "lock", title: "Blokada ekranu", desc: "Zablokowanie (Win+L) lub odblokowanie sesji.",
    params: [{ key: "state", label: "Gdy", type: "choice", def: "lock", options: [["lock", "Zablokowano"], ["unlock", "Odblokowano"]] }], sum: (_, l) => l("state") },
  { id: "t.power", kind: "trigger", cat: "system", icon: "plug", title: "Zasilacz", desc: "Podłączenie lub odłączenie zasilania laptopa.",
    params: [{ key: "state", label: "Gdy", type: "choice", def: "plugged", options: [["plugged", "Podłączono"], ["unplugged", "Odłączono"]] }], provides: ["battery"], sum: (_, l) => l("state") },
  { id: "t.battery", kind: "trigger", cat: "system", icon: "battery", title: "Poziom baterii", desc: "Gdy bateria spadnie do progu lub naładuje się do niego.",
    params: [{ key: "dir", label: "Kierunek", type: "choice", def: "below", options: [["below", "Spada do"], ["above", "Rośnie do"]] }, { key: "level", label: "Próg %", type: "number", def: "20" }],
    provides: ["battery"], sum: (p, l) => `${l("dir")} ${p("level")}%` },
  { id: "t.saver", kind: "trigger", cat: "system", icon: "leaf", title: "Oszczędzanie baterii", desc: "Włączenie lub wyłączenie trybu oszczędzania energii.",
    params: [{ key: "state", label: "Gdy", type: "choice", def: "on", options: onOff }], sum: (_, l) => l("state") },
  { id: "t.cpu", kind: "trigger", cat: "system", icon: "cpu", title: "Obciążenie procesora", desc: "Gdy procesor jest zajęty powyżej progu przez dłuższy czas.",
    params: [{ key: "level", label: "Powyżej %", type: "number", def: "90" }, { key: "seconds", label: "Przez sekund", type: "number", def: "60" }], provides: ["value"], sum: (p) => `> ${p("level")}% przez ${p("seconds")} s` },
  { id: "t.ram", kind: "trigger", cat: "system", icon: "memory", title: "Zajęta pamięć", desc: "Gdy zajęta pamięć RAM przekroczy próg.",
    params: [{ key: "level", label: "Powyżej %", type: "number", def: "90" }, { key: "seconds", label: "Przez sekund", type: "number", def: "30" }], provides: ["value"], sum: (p) => `> ${p("level")}%` },
  { id: "t.disk", kind: "trigger", cat: "system", icon: "disk", title: "Mało miejsca na dysku", desc: "Gdy wolne miejsce spadnie poniżej progu (sprawdzane co 30 s).",
    params: [{ key: "drive", label: "Dysk", type: "text", def: "C" }, { key: "gb", label: "Poniżej GB", type: "number", def: "10" }], provides: ["free_gb"], sum: (p) => `${p("drive")}: < ${p("gb")} GB` },
  { id: "t.monitors", kind: "trigger", cat: "look", icon: "monitor", title: "Podłączenie monitora", desc: "Zmiana liczby monitorów (stacja dokująca, projektor).",
    params: [{ key: "dir", label: "Gdy", type: "choice", def: "any", options: [["any", "Dowolna zmiana"], ["more", "Podłączono"], ["less", "Odłączono"]] }], provides: ["monitors"], sum: (_, l) => l("dir") },
  { id: "t.theme", kind: "trigger", cat: "look", icon: "contrast", title: "Zmiana motywu Windows", desc: "Gdy Windows przełączy się na jasny lub ciemny motyw.",
    params: [{ key: "mode", label: "Na", type: "choice", def: "any", options: [["any", "Dowolny"], ["dark", "Ciemny"], ["light", "Jasny"]] }], provides: ["theme"], sum: (_, l) => l("mode") },
  { id: "t.fullscreen", kind: "trigger", cat: "look", icon: "fullscreen", title: "Pełny ekran", desc: "Gdy aplikacja (gra, film, prezentacja) przejdzie na pełny ekran lub go opuści.",
    params: [{ key: "state", label: "Gdy", type: "choice", def: "enter", options: [["enter", "Wejście"], ["exit", "Wyjście"]] }], provides: ["app"], sum: (_, l) => l("state") },
  { id: "t.app_start", kind: "trigger", cat: "apps", icon: "app", title: "Uruchomienie aplikacji", desc: "Gdy wystartuje program (np. chrome, Teams, steam). Kilka nazw rozdziel średnikiem.",
    params: [{ key: "app", label: "Aplikacja (.exe)", type: "app", def: "" }], provides: ["app"], sum: (p) => p("app") || "dowolna" },
  { id: "t.app_stop", kind: "trigger", cat: "apps", icon: "app", title: "Zamknięcie aplikacji", desc: "Gdy program zostanie zamknięty.",
    params: [{ key: "app", label: "Aplikacja (.exe)", type: "app", def: "" }], provides: ["app"], sum: (p) => p("app") || "dowolna" },
  { id: "t.app_focus", kind: "trigger", cat: "apps", icon: "focus", title: "Przełączenie na aplikację", desc: "Gdy wybrana aplikacja stanie się aktywnym oknem.",
    params: [{ key: "app", label: "Aplikacja (.exe)", type: "app", def: "" }], provides: ["app", "title"], sum: (p) => p("app") || "dowolna" },
  { id: "t.window_title", kind: "trigger", cat: "apps", icon: "window", title: "Tytuł okna", desc: "Gdy aktywne okno ma w tytule podany tekst (np. „YouTube”, „Faktura”).",
    params: [{ key: "text", label: "Tytuł zawiera", type: "window", def: "" }], provides: ["title", "app"], sum: (p) => `„${short(p("text"))}”` },
  { id: "t.network", kind: "trigger", cat: "net", icon: "globe", title: "Internet", desc: "Pojawienie się lub utrata połączenia z internetem.",
    params: [{ key: "state", label: "Gdy", type: "choice", def: "online", options: [["online", "Połączono"], ["offline", "Rozłączono"]] }], sum: (_, l) => l("state") },
  { id: "t.wifi", kind: "trigger", cat: "net", icon: "wifi", title: "Sieć Wi-Fi", desc: "Połączenie z siecią (np. domową, biurową) lub rozłączenie. Puste = dowolna.",
    params: [{ key: "state", label: "Gdy", type: "choice", def: "connected", options: [["connected", "Połączono"], ["disconnected", "Rozłączono"]] }, { key: "ssid", label: "Nazwa sieci (SSID)", type: "text", def: "", optional: true }],
    provides: ["ssid"], sum: (p, l) => `${l("state")}${p("ssid") ? " · " + p("ssid") : ""}` },
  { id: "t.drive", kind: "trigger", cat: "net", icon: "usb", title: "Dysk USB", desc: "Podłączenie lub odłączenie pendrive’a, karty pamięci, dysku zewnętrznego.",
    params: [{ key: "state", label: "Gdy", type: "choice", def: "connected", options: [["connected", "Podłączono"], ["removed", "Odłączono"]] }, { key: "label", label: "Etykieta dysku (wzorzec)", type: "text", def: "", optional: true }],
    provides: ["drive", "drive_label"], sum: (p, l) => `${l("state")}${p("label") ? " · " + p("label") : ""}` },
  { id: "t.file", kind: "trigger", cat: "files", icon: "file", title: "Plik w folderze", desc: "Nowy, zmieniony lub usunięty plik pasujący do wzorca (np. *.pdf;*.jpg).",
    params: [{ key: "folder", label: "Folder", type: "folder", def: "" }, { key: "pattern", label: "Wzorzec", type: "text", def: "*", hint: "*.pdf;*.docx" },
      { key: "change", label: "Zmiana", type: "choice", def: "new", options: [["new", "Nowy plik"], ["changed", "Zmieniony"], ["deleted", "Usunięty"], ["any", "Dowolna"]] },
      { key: "recursive", label: "Także podfoldery", type: "bool", def: "0" }, { key: "every", label: "Sprawdzaj co (s)", type: "number", def: "3" }],
    provides: ["file", "file_name", "file_base", "file_ext", "file_dir", "change"], sum: (p, l) => `${l("change")} · ${folderName(p("folder"))} · ${p("pattern") || "*"}` },
  { id: "t.clipboard", kind: "trigger", cat: "input", icon: "clipboard", title: "Skopiowano tekst", desc: "Gdy w schowku pojawi się tekst (opcjonalnie zawierający frazę lub pasujący do wyrażenia).",
    params: [{ key: "match", label: "Dopasowanie", type: "choice", def: "contains", options: [["contains", "Zawiera"], ["regex", "Wyrażenie regularne"]] }, { key: "filter", label: "Tekst / wzorzec", type: "text", def: "", optional: true }],
    provides: ["clipboard"], sum: (p) => (p("filter") ? `„${short(p("filter"), 24)}”` : "dowolny tekst") },
  { id: "t.sun", kind: "trigger", cat: "time", icon: "sun", title: "Wschód / zachód słońca", desc: "O wschodzie lub zachodzie słońca w Twojej lokalizacji (Ustawienia), z przesunięciem w minutach.",
    params: [{ key: "event", label: "Zdarzenie", type: "choice", def: "sunset", options: [["sunrise", "Wschód"], ["sunset", "Zachód"]] }, { key: "offset", label: "Przesunięcie (min, − wcześniej)", type: "number", def: "0" }, { key: "days", label: "Dni", type: "days", def: "1234567" }],
    provides: ["sunrise", "sunset"], sum: (p, l) => `${l("event")}${+p("offset") ? ` ${+p("offset") > 0 ? "+" : ""}${p("offset")} min` : ""}` },
  { id: "t.host", kind: "trigger", cat: "net", icon: "server", title: "Serwer przestał / zaczął odpowiadać", desc: "Sprawdza w tle host[:port] (NAS, drukarka, serwer) i reaguje na zmianę dostępności.",
    params: [{ key: "host", label: "Host[:port]", type: "text", def: "192.168.1.1:80" }, { key: "state", label: "Gdy", type: "choice", def: "down", options: [["down", "Przestał odpowiadać"], ["up", "Znów odpowiada"]] }, { key: "every", label: "Sprawdzaj co (s)", type: "number", def: "30" }],
    provides: ["host"], sum: (p, l) => `${p("host")} · ${l("state").toLowerCase()}` },
  { id: "t.web_change", kind: "trigger", cat: "web", icon: "globe", title: "Zmiana na stronie / w API", desc: "Pobiera adres co N minut i reaguje, gdy treść (lub wartość ze ścieżki JSON) się zmieni – ceny, statusy, kursy.",
    params: [{ key: "url", label: "Adres", type: "text", def: "https://" }, { key: "json", label: "Ścieżka JSON (opcjonalnie)", type: "text", def: "", optional: true }, { key: "minutes", label: "Co ile minut", type: "number", def: "15" }],
    provides: ["value", "old_value"], sum: (p) => `${p("url").replace(/^https?:\/\//, "").slice(0, 28)} · co ${p("minutes")} min` },
  { id: "t.event", kind: "trigger", cat: "web", icon: "webhook", title: "Zdarzenie zewnętrzne", desc: "Wywołanie przez HTTP (http://127.0.0.1:port/event/NAZWA?token=…), wiersz poleceń (--event NAZWA) lub inną rutynę.",
    params: [{ key: "name", label: "Nazwa zdarzenia", type: "text", def: "moje-zdarzenie" }], provides: ["extra", "body", "source"], sum: (p) => p("name") },
  { id: "t.routine_done", kind: "trigger", cat: "logic", icon: "chain", title: "Po innej rutynie", desc: "Po zakończeniu wybranej rutyny (lub dowolnej).",
    params: [{ key: "routine", label: "Rutyna", type: "routine", def: "", optional: true }, { key: "result", label: "Wynik", type: "choice", def: "any", options: [["any", "Dowolny"], ["ok", "Sukces"], ["error", "Błąd"]] }],
    provides: ["routine", "result"], sum: (p, l) => `${p("routine") ? "wybrana" : "dowolna"} · ${l("result")}` },
  { id: "t.var_change", kind: "trigger", cat: "logic", icon: "variable", title: "Zmiana zmiennej", desc: "Gdy globalna zmienna zmieni wartość.",
    params: [{ key: "name", label: "Zmienna", type: "var", def: "" }], provides: ["name", "value"], sum: (p) => (p("name") ? `{${p("name")}}` : "dowolna") },

  // ======================================================= WARUNKI
  { id: "c.time", kind: "condition", cat: "time", icon: "clock", title: "Pora dnia", desc: "Między godzinami (także przez północ, np. 22:00–06:00).",
    params: [{ key: "from", label: "Od", type: "time", def: "08:00" }, { key: "to", label: "Do", type: "time", def: "16:00" }], sum: (p) => `${p("from")}–${p("to")}` },
  { id: "c.days", kind: "condition", cat: "time", icon: "calendar", title: "Dzień tygodnia", desc: "Tylko w wybrane dni.", params: [{ key: "days", label: "Dni", type: "days", def: "12345" }], sum: (p) => daysLabel(p("days")) },
  { id: "c.month", kind: "condition", cat: "time", icon: "calendar", title: "Miesiąc", desc: "Numery miesięcy po przecinku, np. 6,7,8.", params: [{ key: "months", label: "Miesiące", type: "text", def: "12,1,2" }], sum: (p) => p("months") },
  { id: "c.date", kind: "condition", cat: "time", icon: "calendar", title: "Zakres dat", desc: "MM-DD (co roku, np. 12-20…01-06) albo RRRR-MM-DD.",
    params: [{ key: "from", label: "Od", type: "text", def: "12-20" }, { key: "to", label: "Do", type: "text", def: "01-06" }], sum: (p) => `${p("from")} … ${p("to")}` },
  { id: "c.sun", kind: "condition", cat: "time", icon: "sun", title: "Dzień / noc", desc: "Między wschodem a zachodem słońca w Twojej lokalizacji.",
    params: [{ key: "state", label: "Teraz jest", type: "choice", def: "day", options: [["day", "Dzień"], ["night", "Noc"]] }], sum: (_, l) => l("state") },
  { id: "c.monthday", kind: "condition", cat: "time", icon: "calendar", title: "Dzień miesiąca", desc: "Wybrane dni miesiąca po przecinku, np. 1,15,last.",
    params: [{ key: "days", label: "Dni", type: "text", def: "1,15" }], sum: (p) => p("days") },
  { id: "c.clipboard", kind: "condition", cat: "input", icon: "clipboard", title: "Schowek zawiera", desc: "Czy tekst w schowku zawiera frazę lub pasuje do wyrażenia.",
    params: [{ key: "match", label: "Dopasowanie", type: "choice", def: "contains", options: [["contains", "Zawiera"], ["regex", "Regex"]] }, { key: "text", label: "Tekst / wzorzec", type: "text", def: "" }, { key: "state", label: "Pasuje", type: "choice", def: "yes", options: yesNo }],
    sum: (p) => `„${short(p("text"), 24)}”` },
  { id: "c.battery", kind: "condition", cat: "system", icon: "battery", title: "Poziom baterii", desc: "Porównanie poziomu naładowania.",
    params: [{ key: "op", label: "Bateria", type: "choice", def: "lt", options: cmpNum }, { key: "level", label: "%", type: "number", def: "30" }], sum: (p, l) => `${l("op")} ${p("level")}%` },
  { id: "c.power", kind: "condition", cat: "system", icon: "plug", title: "Zasilanie", desc: "Z zasilacza lub z baterii.",
    params: [{ key: "state", label: "Komputer działa", type: "choice", def: "ac", options: [["ac", "Na zasilaczu"], ["battery", "Na baterii"]] }], sum: (_, l) => l("state") },
  { id: "c.saver", kind: "condition", cat: "system", icon: "leaf", title: "Oszczędzanie baterii", desc: "Stan oszczędzania energii.", params: [{ key: "state", label: "Tryb", type: "choice", def: "on", options: onOff }], sum: (_, l) => l("state") },
  { id: "c.idle", kind: "condition", cat: "system", icon: "moon", title: "Bezczynność", desc: "Jak długo nie używasz komputera.",
    params: [{ key: "op", label: "Bezczynność", type: "choice", def: "ge", options: cmpNum }, { key: "minutes", label: "Minut", type: "number", def: "5" }], sum: (p, l) => `${l("op")} ${p("minutes")} min` },
  { id: "c.locked", kind: "condition", cat: "system", icon: "lock", title: "Ekran zablokowany", desc: "Czy sesja jest zablokowana.", params: [{ key: "state", label: "Zablokowany", type: "choice", def: "yes", options: yesNo }], sum: (_, l) => l("state") },
  { id: "c.cpu", kind: "condition", cat: "system", icon: "cpu", title: "Obciążenie procesora", desc: "Aktualne użycie CPU.",
    params: [{ key: "op", label: "CPU", type: "choice", def: "gt", options: cmpNum }, { key: "level", label: "%", type: "number", def: "80" }], sum: (p, l) => `${l("op")} ${p("level")}%` },
  { id: "c.ram", kind: "condition", cat: "system", icon: "memory", title: "Zajęta pamięć", desc: "Aktualne użycie RAM.",
    params: [{ key: "op", label: "RAM", type: "choice", def: "gt", options: cmpNum }, { key: "level", label: "%", type: "number", def: "80" }], sum: (p, l) => `${l("op")} ${p("level")}%` },
  { id: "c.disk", kind: "condition", cat: "system", icon: "disk", title: "Wolne miejsce", desc: "Wolne miejsce na dysku w GB.",
    params: [{ key: "drive", label: "Dysk", type: "text", def: "C" }, { key: "op", label: "Wolne", type: "choice", def: "lt", options: cmpNum }, { key: "gb", label: "GB", type: "number", def: "20" }], sum: (p, l) => `${p("drive")}: ${l("op")} ${p("gb")} GB` },
  { id: "c.uptime", kind: "condition", cat: "system", icon: "clock", title: "Czas pracy komputera", desc: "Jak długo Windows działa od uruchomienia.",
    params: [{ key: "op", label: "Działa", type: "choice", def: "gt", options: cmpNum }, { key: "hours", label: "Godzin", type: "number", def: "8" }], sum: (p, l) => `${l("op")} ${p("hours")} h` },
  { id: "c.computer", kind: "condition", cat: "system", icon: "monitor", title: "Nazwa komputera", desc: "Przydatne, gdy te same rutyny są na kilku komputerach.", params: [{ key: "name", label: "Nazwa (wzorzec)", type: "text", def: "" }], sum: (p) => p("name") },
  { id: "c.app_running", kind: "condition", cat: "apps", icon: "app", title: "Aplikacja działa", desc: "Czy program jest uruchomiony.",
    params: [{ key: "app", label: "Aplikacja (.exe)", type: "app", def: "" }, { key: "state", label: "Uruchomiona", type: "choice", def: "yes", options: yesNo }], sum: (p, l) => `${p("app")}: ${l("state").toLowerCase()}` },
  { id: "c.app_focus", kind: "condition", cat: "apps", icon: "focus", title: "Aktywna aplikacja", desc: "Która aplikacja jest na pierwszym planie.",
    params: [{ key: "app", label: "Aplikacja (.exe)", type: "app", def: "*" }, { key: "title", label: "Tytuł zawiera", type: "text", def: "", optional: true }], sum: (p) => p("app") },
  { id: "c.window", kind: "condition", cat: "apps", icon: "window", title: "Otwarte okno", desc: "Czy istnieje okno z tekstem w tytule.",
    params: [{ key: "title", label: "Tytuł zawiera", type: "window", def: "" }, { key: "state", label: "Istnieje", type: "choice", def: "yes", options: yesNo }], sum: (p, l) => `„${short(p("title"), 20)}”: ${l("state").toLowerCase()}` },
  { id: "c.fullscreen", kind: "condition", cat: "look", icon: "fullscreen", title: "Pełny ekran", desc: "Czy aktywna aplikacja jest na pełnym ekranie (gra, film, prezentacja).",
    params: [{ key: "state", label: "Pełny ekran", type: "choice", def: "no", options: yesNo }], sum: (_, l) => l("state") },
  { id: "c.monitors", kind: "condition", cat: "look", icon: "monitor", title: "Liczba monitorów", desc: "Ile monitorów jest podłączonych.",
    params: [{ key: "op", label: "Monitorów", type: "choice", def: "ge", options: cmpNum }, { key: "count", label: "Liczba", type: "number", def: "2" }], sum: (p, l) => `${l("op")} ${p("count")}` },
  { id: "c.theme", kind: "condition", cat: "look", icon: "contrast", title: "Motyw Windows", desc: "Aktualny motyw aplikacji.",
    params: [{ key: "mode", label: "Motyw", type: "choice", def: "dark", options: [["dark", "Ciemny"], ["light", "Jasny"]] }], sum: (_, l) => l("mode") },
  { id: "c.volume", kind: "condition", cat: "notify", icon: "volume", title: "Głośność", desc: "Poziom głośności głównej.",
    params: [{ key: "op", label: "Głośność", type: "choice", def: "gt", options: cmpNum }, { key: "level", label: "%", type: "number", def: "50" }], sum: (p, l) => `${l("op")} ${p("level")}%` },
  { id: "c.muted", kind: "condition", cat: "notify", icon: "mute", title: "Dźwięk wyciszony", desc: "Czy głośniki są wyciszone.", params: [{ key: "state", label: "Wyciszony", type: "choice", def: "yes", options: yesNo }], sum: (_, l) => l("state") },
  { id: "c.network", kind: "condition", cat: "net", icon: "globe", title: "Internet", desc: "Czy jest połączenie z internetem.",
    params: [{ key: "state", label: "Internet", type: "choice", def: "online", options: [["online", "Dostępny"], ["offline", "Brak"]] }], sum: (_, l) => l("state") },
  { id: "c.wifi", kind: "condition", cat: "net", icon: "wifi", title: "Sieć Wi-Fi", desc: "Czy komputer jest w danej sieci (puste = dowolnej).",
    params: [{ key: "ssid", label: "SSID (wzorzec)", type: "text", def: "" }, { key: "state", label: "Połączony", type: "choice", def: "yes", options: yesNo }], sum: (p, l) => `${p("ssid") || "dowolna"}: ${l("state").toLowerCase()}` },
  { id: "c.host", kind: "condition", cat: "net", icon: "server", title: "Serwer osiągalny", desc: "Czy da się połączyć z hostem (np. nas.local:5000, 192.168.1.10:22).",
    params: [{ key: "host", label: "Host[:port]", type: "text", def: "google.com" }, { key: "state", label: "Osiągalny", type: "choice", def: "yes", options: yesNo }, { key: "timeout", label: "Limit (ms)", type: "number", def: "1500" }],
    sum: (p, l) => `${p("host")}: ${l("state").toLowerCase()}` },
  { id: "c.drive", kind: "condition", cat: "net", icon: "usb", title: "Dysk podłączony", desc: "Litera (E) lub etykieta dysku.",
    params: [{ key: "drive", label: "Litera lub etykieta", type: "text", def: "E" }, { key: "state", label: "Podłączony", type: "choice", def: "yes", options: yesNo }], sum: (p, l) => `${p("drive")}: ${l("state").toLowerCase()}` },
  { id: "c.file", kind: "condition", cat: "files", icon: "file", title: "Plik istnieje", desc: "Czy istnieje plik lub folder.",
    params: [{ key: "path", label: "Ścieżka", type: "file", def: "" }, { key: "state", label: "Istnieje", type: "choice", def: "yes", options: yesNo }], sum: (p, l) => `${folderName(p("path"))}: ${l("state").toLowerCase()}` },
  { id: "c.var", kind: "condition", cat: "logic", icon: "variable", title: "Zmienna", desc: "Porównanie zmiennej (własnej, wbudowanej lub z wyzwalacza).",
    params: [{ key: "name", label: "Zmienna", type: "var", def: "" }, { key: "op", label: "Warunek", type: "choice", def: "eq", options: cmpVar }, { key: "value", label: "Wartość", type: "text", def: "", optional: true }],
    sum: (p, l) => `{${p("name")}} ${l("op")} ${p("value")}` },
  { id: "c.random", kind: "condition", cat: "logic", icon: "dice", title: "Losowo", desc: "Z podanym prawdopodobieństwem.", params: [{ key: "percent", label: "Szansa %", type: "number", def: "50" }], sum: (p) => `${p("percent")}%` },
  { id: "c.since_last", kind: "condition", cat: "logic", icon: "clock", title: "Od ostatniego uruchomienia", desc: "Rutyna nie działała od co najmniej N minut.",
    params: [{ key: "minutes", label: "Minut", type: "number", def: "60" }], sum: (p) => `≥ ${p("minutes")} min` },
  { id: "c.routine", kind: "condition", cat: "logic", icon: "chain", title: "Stan innej rutyny", desc: "Czy inna rutyna jest włączona.",
    params: [{ key: "routine", label: "Rutyna", type: "routine", def: "" }, { key: "state", label: "Stan", type: "choice", def: "on", options: onOff }], sum: (_, l) => l("state") },

  // ======================================================= AKCJE
  { id: "a.notify", kind: "action", cat: "notify", icon: "bell", title: "Powiadomienie", desc: "Powiadomienie Windows w prawym dolnym rogu.",
    params: [{ key: "title", label: "Tytuł", type: "text", def: "OpenRoutine" }, { key: "text", label: "Treść", type: "multiline", def: "" }], sum: (p) => short(p("title") + (p("text") ? ": " + p("text") : "")) },
  { id: "a.msgbox", kind: "action", cat: "notify", icon: "message", title: "Okno komunikatu", desc: "Okno na wierzchu z przyciskiem OK – trudno je przeoczyć.",
    params: [{ key: "title", label: "Tytuł", type: "text", def: "OpenRoutine" }, { key: "text", label: "Treść", type: "multiline", def: "" }, { key: "wait", label: "Czekaj na OK przed kolejną akcją", type: "bool", def: "0" }], sum: (p) => short(p("text") || p("title")) },
  { id: "a.confirm", kind: "action", cat: "notify", icon: "message", title: "Zapytaj: Tak / Nie", desc: "Okno z pytaniem; odpowiedź w {answer}. Przy „Nie” może przerwać rutynę.",
    params: [{ key: "title", label: "Tytuł", type: "text", def: "OpenRoutine" }, { key: "text", label: "Pytanie", type: "multiline", def: "Kontynuować?" }, { key: "on_no", label: "Przy „Nie”", type: "choice", def: "stop", options: [["stop", "Przerwij rutynę"], ["continue", "Kontynuuj"]] }],
    provides: ["answer"], sum: (p) => `„${short(p("text"))}”` },
  { id: "a.input", kind: "action", cat: "notify", icon: "type", title: "Zapytaj o tekst", desc: "Okno z polem tekstowym – wpisana wartość trafia do zmiennej.",
    params: [{ key: "title", label: "Tytuł", type: "text", def: "OpenRoutine" }, { key: "text", label: "Pytanie", type: "text", def: "Wpisz wartość:" }, { key: "default", label: "Domyślnie", type: "text", def: "", optional: true }, { key: "var", label: "Do zmiennej", type: "var", def: "input" }, scope, { key: "stop_if_empty", label: "Przerwij, gdy pusto lub Anuluj", type: "bool", def: "1" }],
    sum: (p) => `→ {${p("var")}}` },
  { id: "a.speak", kind: "action", cat: "notify", icon: "speak", title: "Powiedz na głos", desc: "Syntezator mowy Windows.",
    params: [{ key: "text", label: "Tekst", type: "multiline", def: "Jest {time}" }, { key: "rate", label: "Tempo (−10…10)", type: "number", def: "0" }, { key: "volume", label: "Głośność %", type: "number", def: "100" }], sum: (p) => `„${short(p("text"))}”` },
  { id: "a.beep", kind: "action", cat: "notify", icon: "bell", title: "Dźwięk systemowy", desc: "Jeden z dźwięków Windows.",
    params: [{ key: "kind", label: "Dźwięk", type: "choice", def: "info", options: [["info", "Informacja"], ["warning", "Ostrzeżenie"], ["error", "Błąd"], ["question", "Pytanie"], ["default", "Domyślny"]] }], sum: (_, l) => l("kind") },
  { id: "a.tone", kind: "action", cat: "notify", icon: "wave", title: "Sygnał dźwiękowy", desc: "Ton o podanej częstotliwości.",
    params: [{ key: "freq", label: "Hz", type: "number", def: "880" }, { key: "ms", label: "Czas (ms)", type: "number", def: "300" }, { key: "repeat", label: "Powtórzeń", type: "number", def: "1" }], sum: (p) => `${p("freq")} Hz × ${p("repeat")}` },
  { id: "a.sound", kind: "action", cat: "notify", icon: "music", title: "Odtwórz plik dźwiękowy", desc: "WAV odtwarzany w tle; inne formaty otwiera domyślny odtwarzacz.",
    params: [{ key: "file", label: "Plik", type: "file", def: "C:\\Windows\\Media\\Alarm01.wav" }], sum: (p) => folderName(p("file")) },
  { id: "a.volume", kind: "action", cat: "notify", icon: "volume", title: "Głośność", desc: "Ustaw, zmień o krok, wycisz lub przywróć dźwięk.",
    params: [{ key: "mode", label: "Działanie", type: "choice", def: "set", options: [["set", "Ustaw"], ["up", "Głośniej o"], ["down", "Ciszej o"], ["mute", "Wycisz"], ["unmute", "Włącz dźwięk"], ["toggle", "Przełącz wyciszenie"]] }, { key: "level", label: "Poziom / krok %", type: "number", def: "40" }],
    sum: (p, l) => (["mute", "unmute", "toggle"].includes(p("mode")) ? l("mode") : `${l("mode")} ${p("level")}%`) },
  { id: "a.media", kind: "action", cat: "notify", icon: "play", title: "Sterowanie muzyką", desc: "Odtwórz/pauza, następny, poprzedni utwór (Spotify, przeglądarka…).",
    params: [{ key: "cmd", label: "Polecenie", type: "choice", def: "play", options: [["play", "Odtwórz / pauza"], ["next", "Następny"], ["prev", "Poprzedni"], ["stop", "Stop"]] }], sum: (_, l) => l("cmd") },

  { id: "a.run", kind: "action", cat: "apps", icon: "app", title: "Uruchom program", desc: "Program, skrót .lnk lub skrypt z argumentami.",
    params: [{ key: "path", label: "Program", type: "file", def: "notepad.exe" }, { key: "args", label: "Argumenty", type: "text", def: "", optional: true }, { key: "cwd", label: "Folder roboczy", type: "folder", def: "", optional: true },
      { key: "minimized", label: "Zminimalizowany", type: "bool", def: "0" }, { key: "wait", label: "Czekaj na zakończenie ({exit_code})", type: "bool", def: "0" }], provides: ["exit_code"], sum: (p) => folderName(p("path")) },
  { id: "a.open", kind: "action", cat: "apps", icon: "external", title: "Otwórz plik, folder lub stronę", desc: "Dowolna ścieżka lub adres – otworzy się w domyślnej aplikacji.",
    params: [{ key: "target", label: "Ścieżka lub adres", type: "text", def: "https://" }], sum: (p) => short(p("target")) },
  { id: "a.settings", kind: "action", cat: "apps", icon: "gear", title: "Otwórz ustawienia Windows", desc: "Wybrany ekran Ustawień.",
    params: [{ key: "page", label: "Ekran", type: "choice", def: "", options: [["", "Strona główna"], ["display", "Ekran"], ["sound", "Dźwięk"], ["notifications", "Powiadomienia"], ["quiethours", "Nie przeszkadzać / Skupienie"], ["powersleep", "Zasilanie i uśpienie"], ["batterysaver", "Bateria"], ["storagesense", "Magazyn"], ["network-wifi", "Wi-Fi"], ["bluetooth", "Bluetooth"], ["printers", "Drukarki"], ["personalization-background", "Tło"], ["colors", "Kolory"], ["nightlight", "Światło nocne"], ["appsfeatures", "Aplikacje"], ["defaultapps", "Aplikacje domyślne"], ["startupapps", "Autostart"], ["windowsupdate", "Windows Update"], ["privacy", "Prywatność"], ["dateandtime", "Data i godzina"]] }],
    sum: (_, l) => l("page") },
  { id: "a.kill", kind: "action", cat: "apps", icon: "x", title: "Zamknij aplikację", desc: "Zamyka program po nazwie (kilka nazw – średnik).",
    params: [{ key: "app", label: "Aplikacja (.exe)", type: "app", def: "" }, { key: "force", label: "Wymuś (bez pytania o zapis)", type: "bool", def: "0" }], provides: ["killed"], sum: (p) => p("app") },
  { id: "a.window", kind: "action", cat: "apps", icon: "window", title: "Okno: zminimalizuj, zamknij…", desc: "Działanie na oknach z tekstem w tytule.",
    params: [{ key: "title", label: "Tytuł zawiera", type: "window", def: "" }, { key: "op", label: "Działanie", type: "choice", def: "minimize", options: [["minimize", "Zminimalizuj"], ["maximize", "Zmaksymalizuj"], ["restore", "Przywróć"], ["focus", "Przełącz na"], ["close", "Zamknij"]] }, { key: "quiet", label: "Bez błędu, gdy brak okna", type: "bool", def: "1" }],
    sum: (p, l) => `${l("op")} „${short(p("title"), 20)}”` },
  { id: "a.minimize_all", kind: "action", cat: "apps", icon: "desktop", title: "Pokaż pulpit", desc: "Minimalizuje wszystkie okna (Win+D)." },

  { id: "a.keys", kind: "action", cat: "input", icon: "keyboard", title: "Naciśnij klawisze", desc: "Kombinacje oddzielone spacją, np. „ctrl+s”, „win+shift+s”, „alt+tab”.",
    params: [{ key: "keys", label: "Klawisze", type: "text", def: "ctrl+s" }], sum: (p) => p("keys") },
  { id: "a.type", kind: "action", cat: "input", icon: "type", title: "Wpisz tekst", desc: "Wpisuje tekst w aktywnym oknie, jakbyś pisał.",
    params: [{ key: "text", label: "Tekst", type: "multiline", def: "" }, { key: "delay", label: "Opóźnienie (s)", type: "number", def: "0" }], sum: (p) => `„${short(p("text"))}”` },
  { id: "a.clip_set", kind: "action", cat: "input", icon: "clipboard", title: "Skopiuj do schowka", desc: "Wstawia tekst do schowka.", params: [{ key: "text", label: "Tekst", type: "multiline", def: "" }], sum: (p) => `„${short(p("text"))}”` },
  { id: "a.clip_get", kind: "action", cat: "input", icon: "clipboard", title: "Schowek do zmiennej", desc: "Zapisuje zawartość schowka.", params: [{ key: "var", label: "Zmienna", type: "var", def: "clip" }, scope], sum: (p) => `→ {${p("var")}}` },

  { id: "a.lock", kind: "action", cat: "system", icon: "lock", title: "Zablokuj komputer", desc: "Jak Win+L." },
  { id: "a.monitor_off", kind: "action", cat: "system", icon: "monitor", title: "Wyłącz monitor", desc: "Wygasza ekran (ruch myszy go wybudzi)." },
  { id: "a.sleep", kind: "action", cat: "system", icon: "moon", title: "Uśpij", desc: "Uśpienie lub hibernacja.",
    params: [{ key: "mode", label: "Tryb", type: "choice", def: "sleep", options: [["sleep", "Uśpienie"], ["hibernate", "Hibernacja"]] }], sum: (_, l) => l("mode") },
  { id: "a.shutdown", kind: "action", cat: "system", icon: "power", title: "Wyłącz / uruchom ponownie", desc: "Z opóźnieniem – Windows pokaże ostrzeżenie. „Anuluj” przerywa zaplanowane wyłączenie.",
    params: [{ key: "mode", label: "Działanie", type: "choice", def: "shutdown", options: [["shutdown", "Wyłącz"], ["restart", "Uruchom ponownie"], ["logoff", "Wyloguj"], ["cancel", "Anuluj wyłączanie"]] }, { key: "delay", label: "Za (s)", type: "number", def: "60" }],
    sum: (p, l) => `${l("mode")}${["shutdown", "restart"].includes(p("mode")) ? ` za ${p("delay")} s` : ""}` },
  { id: "a.power_plan", kind: "action", cat: "system", icon: "bolt", title: "Plan zasilania", desc: "Zrównoważony, wysoka wydajność lub oszczędzanie energii.",
    params: [{ key: "plan", label: "Plan", type: "choice", def: "balanced", options: [["balanced", "Zrównoważony"], ["high", "Wysoka wydajność"], ["saver", "Oszczędzanie energii"]] }], sum: (_, l) => l("plan") },
  { id: "a.recycle", kind: "action", cat: "system", icon: "trash", title: "Opróżnij Kosz", desc: "Trwale usuwa pliki z Kosza." },
  { id: "a.clear_temp", kind: "action", cat: "system", icon: "broom", title: "Wyczyść pliki tymczasowe", desc: "Usuwa z %TEMP% pliki starsze niż N dni (zajęte pomija).",
    params: [{ key: "days", label: "Starsze niż (dni)", type: "number", def: "7" }], provides: ["files_count"], sum: (p) => `> ${p("days")} dni` },
  { id: "a.dns_flush", kind: "action", cat: "system", icon: "refresh", title: "Wyczyść pamięć DNS", desc: "ipconfig /flushdns – pomaga, gdy strony się nie otwierają." },
  { id: "a.restart_explorer", kind: "action", cat: "system", icon: "refresh", title: "Uruchom ponownie Eksplorator", desc: "Odświeża pasek zadań i pulpit, gdy się zawieszą." },
  { id: "a.engine_pause", kind: "action", cat: "logic", icon: "pause", title: "Wstrzymaj wszystkie rutyny", desc: "Na określony czas (0 = do ręcznego wznowienia).",
    params: [{ key: "minutes", label: "Na minut", type: "number", def: "60" }], sum: (p) => (+p("minutes") > 0 ? `na ${p("minutes")} min` : "do wznowienia") },

  { id: "a.theme", kind: "action", cat: "look", icon: "contrast", title: "Motyw jasny / ciemny", desc: "Przełącza motyw Windows i aplikacji.",
    params: [{ key: "mode", label: "Motyw", type: "choice", def: "dark", options: [["dark", "Ciemny"], ["light", "Jasny"], ["toggle", "Przełącz"]] }, { key: "scope", label: "Dotyczy", type: "choice", def: "both", options: [["both", "System i aplikacje"], ["apps", "Tylko aplikacje"], ["system", "Tylko pasek i menu"]] }],
    sum: (_, l) => l("mode") },
  { id: "a.wallpaper", kind: "action", cat: "look", icon: "image", title: "Tapeta", desc: "Obraz albo folder (wtedy losowy obraz z folderu).", params: [{ key: "file", label: "Obraz lub folder", type: "file", def: "" }], sum: (p) => folderName(p("file")) },
  { id: "a.brightness", kind: "action", cat: "look", icon: "sun", title: "Jasność ekranu", desc: "Działa na ekranach laptopów i części monitorów.", params: [{ key: "level", label: "Jasność %", type: "number", def: "70" }], sum: (p) => `${p("level")}%` },
  { id: "a.screenshot", kind: "action", cat: "look", icon: "camera", title: "Zrzut ekranu", desc: "Zapisuje PNG wszystkich monitorów do folderu ({file}).",
    params: [{ key: "folder", label: "Folder", type: "folder", def: "{pictures}\\Zrzuty OpenRoutine" }], provides: ["file"], sum: (p) => folderName(p("folder")) },

  { id: "a.file_copy", kind: "action", cat: "files", icon: "copy", title: "Kopiuj pliki", desc: "Pliki pasujące do wzorca (opcjonalnie starsze niż N dni) do innego folderu.",
    params: [{ key: "source", label: "Z folderu (lub plik)", type: "folder", def: "" }, { key: "pattern", label: "Wzorzec", type: "text", def: "*" }, { key: "dest", label: "Do folderu", type: "folder", def: "" }, { key: "older_days", label: "Starsze niż (dni, 0 = wszystkie)", type: "number", def: "0" }, { key: "recursive", label: "Także podfoldery", type: "bool", def: "0" }, conflict],
    provides: ["files_count"], sum: (p) => `${folderName(p("source"))} → ${folderName(p("dest"))}` },
  { id: "a.file_move", kind: "action", cat: "files", icon: "move", title: "Przenieś pliki", desc: "Jak kopiowanie, ale przenosi.",
    params: [{ key: "source", label: "Z folderu (lub plik)", type: "folder", def: "" }, { key: "pattern", label: "Wzorzec", type: "text", def: "*" }, { key: "dest", label: "Do folderu", type: "folder", def: "" }, { key: "older_days", label: "Starsze niż (dni, 0 = wszystkie)", type: "number", def: "0" }, { key: "recursive", label: "Także podfoldery", type: "bool", def: "0" }, conflict],
    provides: ["files_count"], sum: (p) => `${folderName(p("source"))} → ${folderName(p("dest"))}` },
  { id: "a.file_delete", kind: "action", cat: "files", icon: "trash", title: "Usuń pliki", desc: "Domyślnie do Kosza – można je odzyskać.",
    params: [{ key: "source", label: "Folder (lub plik)", type: "folder", def: "" }, { key: "pattern", label: "Wzorzec", type: "text", def: "*.tmp" }, { key: "older_days", label: "Starsze niż (dni)", type: "number", def: "30" }, { key: "recursive", label: "Także podfoldery", type: "bool", def: "0" },
      { key: "mode", label: "Sposób", type: "choice", def: "recycle", options: [["recycle", "Do Kosza"], ["permanent", "Trwale"]] }], provides: ["files_count"], sum: (p) => `${folderName(p("source"))} · ${p("pattern")}` },
  { id: "a.organize", kind: "action", cat: "files", icon: "sort", title: "Uporządkuj folder", desc: "Rozkłada pliki do podfolderów: Obrazy, Dokumenty, Archiwa, Instalatory… albo według miesiąca lub rozszerzenia.",
    params: [{ key: "folder", label: "Folder", type: "folder", def: "{downloads}" }, { key: "by", label: "Według", type: "choice", def: "type", options: [["type", "Rodzaju"], ["date", "Miesiąca"], ["ext", "Rozszerzenia"]] }, { key: "pattern", label: "Wzorzec", type: "text", def: "*" }, { key: "older_days", label: "Starsze niż (dni)", type: "number", def: "0" }],
    provides: ["files_count"], sum: (p, l) => `${folderName(p("folder"))} · wg ${l("by").toLowerCase()}` },
  { id: "a.file_write", kind: "action", cat: "files", icon: "edit", title: "Zapisz tekst do pliku", desc: "Dopisz wiersz (np. dziennik) lub nadpisz plik.",
    params: [{ key: "path", label: "Plik", type: "file", def: "{documents}\\dziennik.txt" }, { key: "text", label: "Tekst", type: "multiline", def: "{datetime} " }, { key: "mode", label: "Tryb", type: "choice", def: "append", options: [["append", "Dopisz"], ["overwrite", "Nadpisz"]] }, { key: "newline", label: "Nowy wiersz na końcu", type: "bool", def: "1" }],
    sum: (p, l) => `${l("mode")} · ${folderName(p("path"))}` },
  { id: "a.file_read", kind: "action", cat: "files", icon: "file", title: "Wczytaj plik do zmiennej", desc: "Tekst pliku do zmiennej.", params: [{ key: "path", label: "Plik", type: "file", def: "" }, { key: "var", label: "Zmienna", type: "var", def: "file_text" }, scope], sum: (p) => `→ {${p("var")}}` },
  { id: "a.file_list", kind: "action", cat: "files", icon: "list", title: "Lista plików do zmiennej", desc: "Nazwy lub ścieżki plików (każdy w nowym wierszu) i ich liczba w {files_count}.",
    params: [{ key: "folder", label: "Folder", type: "folder", def: "" }, { key: "pattern", label: "Wzorzec", type: "text", def: "*" }, { key: "older_days", label: "Starsze niż (dni)", type: "number", def: "0" }, { key: "recursive", label: "Także podfoldery", type: "bool", def: "0" },
      { key: "format", label: "Zapisz", type: "choice", def: "name", options: [["name", "Nazwy"], ["path", "Pełne ścieżki"]] }, { key: "var", label: "Do zmiennej", type: "var", def: "files" }, scope],
    provides: ["files_count"], sum: (p) => `${folderName(p("folder"))} → {${p("var")}}` },
  { id: "a.rename", kind: "action", cat: "files", icon: "edit", title: "Zmień nazwy plików", desc: "Według szablonu: {name}, {ext}, {n} (numer), {mdate}, {mtime} i zmienne; opcjonalnie zamiana tekstu.",
    params: [{ key: "folder", label: "Folder", type: "folder", def: "" }, { key: "pattern", label: "Wzorzec", type: "text", def: "*.jpg" }, { key: "template", label: "Nowa nazwa", type: "text", def: "{mdate} {name}{ext}" },
      { key: "digits", label: "Cyfr w {n}", type: "number", def: "3" }, { key: "find", label: "Zamień tekst", type: "text", def: "", optional: true }, { key: "replace", label: "na", type: "text", def: "", optional: true }],
    provides: ["files_count"], sum: (p) => `${folderName(p("folder"))} · ${p("template")}` },
  { id: "a.folder_create", kind: "action", cat: "files", icon: "folder", title: "Utwórz folder", desc: "Np. {documents}\\Raporty\\{year}-{month}.", params: [{ key: "path", label: "Folder", type: "text", def: "" }], sum: (p) => short(p("path")) },
  { id: "a.zip", kind: "action", cat: "files", icon: "archive", title: "Spakuj do ZIP", desc: "Folder lub pliki (można użyć *) do archiwum – świetne do kopii zapasowych.",
    params: [{ key: "source", label: "Co spakować", type: "folder", def: "" }, { key: "dest", label: "Plik ZIP", type: "text", def: "{documents}\\Kopia {stamp}.zip" }], provides: ["zip"], sum: (p) => `${folderName(p("source"))} → ZIP` },
  { id: "a.unzip", kind: "action", cat: "files", icon: "archive", title: "Rozpakuj ZIP", desc: "Archiwum do folderu.", params: [{ key: "source", label: "Plik ZIP", type: "file", def: "{file}" }, { key: "dest", label: "Do folderu", type: "folder", def: "" }], sum: (p) => folderName(p("source")) },

  { id: "a.http", kind: "action", cat: "web", icon: "globe", title: "Żądanie HTTP", desc: "Webhook, API, Home Assistant, ntfy, Discord… Odpowiedź w {http_response}, kod w {http_code}.",
    params: [{ key: "method", label: "Metoda", type: "choice", def: "GET", options: [["GET", "GET"], ["POST", "POST"], ["PUT", "PUT"], ["PATCH", "PATCH"], ["DELETE", "DELETE"]] }, { key: "url", label: "Adres", type: "text", def: "https://" },
      { key: "headers", label: "Nagłówki (Nazwa: wartość)", type: "multiline", def: "", optional: true }, { key: "body", label: "Treść", type: "multiline", def: "", optional: true }, { key: "json", label: "Wyciągnij z JSON (a.b.0)", type: "text", def: "", optional: true },
      { key: "var", label: "Zapisz do zmiennej", type: "var", def: "", optional: true }, scope, { key: "timeout", label: "Limit (s)", type: "number", def: "20" }, { key: "ignore_errors", label: "Kod ≥ 400 nie jest błędem", type: "bool", def: "0" }],
    provides: ["http_response", "http_code"], sum: (p) => `${p("method")} ${short(p("url").replace(/^https?:\/\//, ""), 30)}` },
  { id: "a.download", kind: "action", cat: "web", icon: "download", title: "Pobierz plik", desc: "Z adresu do pliku lub folderu ({file}).",
    params: [{ key: "url", label: "Adres", type: "text", def: "https://" }, { key: "path", label: "Plik lub folder", type: "folder", def: "{downloads}\\" }], provides: ["file"], sum: (p) => short(p("url").replace(/^https?:\/\//, "")) },
  { id: "a.mail", kind: "action", cat: "web", icon: "mail", title: "Napisz e-mail", desc: "Otwiera nową wiadomość w programie pocztowym.",
    params: [{ key: "to", label: "Do", type: "text", def: "" }, { key: "subject", label: "Temat", type: "text", def: "" }, { key: "body", label: "Treść", type: "multiline", def: "" }], sum: (p) => p("to") || "(adresat)" },

  { id: "a.powershell", kind: "action", cat: "script", icon: "terminal", title: "Skrypt PowerShell", desc: "Uruchamia się bez okna; wynik w {output}.",
    params: [{ key: "script", label: "Skrypt", type: "multiline", def: "Get-Date" }, { key: "var", label: "Wynik do zmiennej", type: "var", def: "", optional: true }, scope, { key: "ignore_errors", label: "Ignoruj błędy", type: "bool", def: "0" }],
    provides: ["output"], sum: (p) => short(p("script").split("\n")[0]) },
  { id: "a.cmd", kind: "action", cat: "script", icon: "terminal", title: "Polecenie CMD", desc: "Polecenie wiersza poleceń bez okna; wynik w {output}.",
    params: [{ key: "script", label: "Polecenie", type: "text", def: "echo %DATE%" }, { key: "var", label: "Wynik do zmiennej", type: "var", def: "", optional: true }, scope, { key: "ignore_errors", label: "Ignoruj błędy", type: "bool", def: "0" }],
    provides: ["output"], sum: (p) => short(p("script")) },

  { id: "a.var_set", kind: "action", cat: "logic", icon: "variable", title: "Ustaw zmienną", desc: "Zapamiętuje wartość (globalnie – także po restarcie).",
    params: [{ key: "name", label: "Zmienna", type: "var", def: "" }, { key: "value", label: "Wartość", type: "text", def: "" }, { ...scope, def: "global" }], sum: (p) => `{${p("name")}} = ${short(p("value"), 20)}` },
  { id: "a.var_inc", kind: "action", cat: "logic", icon: "plus", title: "Zwiększ licznik", desc: "Dodaje liczbę do zmiennej globalnej (ujemna odejmuje).",
    params: [{ key: "name", label: "Zmienna", type: "var", def: "licznik" }, { key: "by", label: "O ile", type: "number", def: "1" }], sum: (p) => `{${p("name")}} + ${p("by")}` },
  { id: "a.var_calc", kind: "action", cat: "logic", icon: "calc", title: "Oblicz", desc: "Wyrażenie z + − × / % ^ i nawiasami, np. {licznik} * 2 + 1.",
    params: [{ key: "name", label: "Wynik do zmiennej", type: "var", def: "wynik" }, { key: "expr", label: "Wyrażenie", type: "text", def: "" }, { ...scope, def: "global" }], sum: (p) => `{${p("name")}} = ${short(p("expr"), 24)}` },
  { id: "a.var_del", kind: "action", cat: "logic", icon: "trash", title: "Usuń zmienną", desc: "Usuwa zmienną globalną.", params: [{ key: "name", label: "Zmienna", type: "var", def: "" }], sum: (p) => `{${p("name")}}` },
  { id: "a.text", kind: "action", cat: "logic", icon: "type", title: "Przetwórz tekst", desc: "Wielkie/małe litery, zamiana, wyrażenia regularne, fragment, cyfry…",
    params: [{ key: "input", label: "Tekst", type: "text", def: "{clipboard}" }, { key: "op", label: "Działanie", type: "choice", def: "trim", options: [["trim", "Usuń spacje z brzegów"], ["upper", "WIELKIE LITERY"], ["lower", "małe litery"], ["capitalize", "Pierwsza wielka"], ["replace", "Zamień A na B"], ["regex_extract", "Wyciągnij regex A"], ["regex_replace", "Regex A → B"], ["digits", "Tylko cyfry"], ["substring", "Fragment od A, długość B"], ["first_line", "Pierwszy wiersz"], ["line_count", "Liczba wierszy"], ["length", "Długość"], ["urlencode", "Kodowanie URL"], ["slug", "Slug-z-tekstu"], ["reverse", "Odwróć"]] },
      { key: "a", label: "A", type: "text", def: "", optional: true }, { key: "b", label: "B", type: "text", def: "", optional: true }, { key: "var", label: "Wynik do zmiennej", type: "var", def: "text" }, scope],
    sum: (p, l) => `${l("op")} → {${p("var")}}` },
  { id: "a.json", kind: "action", cat: "logic", icon: "braces", title: "Odczytaj JSON", desc: "Wartość ze ścieżki a.b.0.c.",
    params: [{ key: "source", label: "JSON", type: "text", def: "{http_response}" }, { key: "path", label: "Ścieżka", type: "text", def: "" }, { key: "var", label: "Do zmiennej", type: "var", def: "json_value" }, scope], sum: (p) => `${p("path")} → {${p("var")}}` },
  { id: "a.random", kind: "action", cat: "logic", icon: "dice", title: "Losowa liczba", desc: "Liczba całkowita z zakresu.",
    params: [{ key: "min", label: "Od", type: "number", def: "1" }, { key: "max", label: "Do", type: "number", def: "100" }, { key: "name", label: "Do zmiennej", type: "var", def: "random_value" }, scope], sum: (p) => `${p("min")}–${p("max")} → {${p("name")}}` },
  { id: "a.pick", kind: "action", cat: "logic", icon: "dice", title: "Losuj z listy", desc: "Jedna pozycja z listy (każda w nowym wierszu).",
    params: [{ key: "items", label: "Lista", type: "multiline", def: "" }, { key: "name", label: "Do zmiennej", type: "var", def: "picked" }, scope], sum: (p) => `→ {${p("name")}}` },
  { id: "a.date", kind: "action", cat: "logic", icon: "calendar", title: "Data do zmiennej", desc: "Format strftime (%Y-%m-%d %H:%M, %A…) z przesunięciem w minutach.",
    params: [{ key: "format", label: "Format", type: "text", def: "%Y-%m-%d" }, { key: "offset", label: "Przesunięcie (min)", type: "number", def: "0" }, { key: "name", label: "Do zmiennej", type: "var", def: "date_value" }, scope], sum: (p) => `${p("format")} → {${p("name")}}` },
  { id: "a.wait", kind: "action", cat: "logic", icon: "hourglass", title: "Czekaj", desc: "Pauza przed kolejną akcją.", params: [{ key: "seconds", label: "Sekund", type: "number", def: "5" }], sum: (p) => `${p("seconds")} s` },
  { id: "a.wait_until", kind: "action", cat: "logic", icon: "hourglass", title: "Czekaj do godziny", desc: "Wstrzymuje rutynę do podanej godziny.", params: [{ key: "time", label: "Godzina", type: "time", def: "12:00" }], sum: (p) => `do ${p("time")}` },
  { id: "a.stop_if", kind: "action", cat: "logic", icon: "stop", title: "Przerwij, jeśli…", desc: "Kończy rutynę (bez błędu), gdy warunek na zmiennej jest spełniony.",
    params: [{ key: "name", label: "Zmienna", type: "var", def: "" }, { key: "op", label: "Warunek", type: "choice", def: "eq", options: cmpVar }, { key: "value", label: "Wartość", type: "text", def: "", optional: true }], sum: (p, l) => `{${p("name")}} ${l("op")} ${p("value")}` },
  { id: "a.skip_if", kind: "action", cat: "logic", icon: "skip", title: "Pomiń akcje, jeśli…", desc: "Pomija N kolejnych akcji – prosty „jeśli–to”.",
    params: [{ key: "name", label: "Zmienna", type: "var", def: "" }, { key: "op", label: "Warunek", type: "choice", def: "eq", options: cmpVar }, { key: "value", label: "Wartość", type: "text", def: "", optional: true }, { key: "count", label: "Ile akcji pominąć", type: "number", def: "1" }],
    sum: (p, l) => `{${p("name")}} ${l("op")} ${p("value")} → pomiń ${p("count")}` },
  { id: "a.run_routine", kind: "action", cat: "logic", icon: "chain", title: "Uruchom inną rutynę", desc: "Przekazuje jej swoje zmienne.",
    params: [{ key: "routine", label: "Rutyna", type: "routine", def: "" }, { key: "wait", label: "Czekaj na zakończenie", type: "bool", def: "0" }], sum: () => "wybrana rutyna" },
  { id: "a.toggle_routine", kind: "action", cat: "logic", icon: "toggle", title: "Włącz / wyłącz rutynę", desc: "Zmienia stan innej rutyny.",
    params: [{ key: "routine", label: "Rutyna", type: "routine", def: "" }, { key: "state", label: "Stan", type: "choice", def: "toggle", options: [["on", "Włącz"], ["off", "Wyłącz"], ["toggle", "Przełącz"]] }], sum: (_, l) => l("state") },
  { id: "a.log", kind: "action", cat: "logic", icon: "list", title: "Wpis do dziennika", desc: "Dodaje wiersz w dzienniku OpenRoutine.", params: [{ key: "text", label: "Tekst", type: "text", def: "" }], sum: (p) => short(p("text")) },
];

export const SPEC: Record<string, Spec> = Object.fromEntries(SPECS.map((s) => [s.id, s]));

export const KIND_TITLE: Record<Kind, { plural: string; one: string; hint: string }> = {
  trigger: { plural: "Gdy", one: "Wyzwalacz", hint: "Wystarczy dowolny z wyzwalaczy." },
  condition: { plural: "Jeśli", one: "Warunek", hint: "Opcjonalne – sprawdzane w chwili wyzwolenia." },
  action: { plural: "Wykonaj", one: "Akcja", hint: "Po kolei, od góry." },
};

export function defaults(id: string): Record<string, string> {
  const s = SPEC[id];
  return Object.fromEntries((s?.params ?? []).map((p) => [p.key, p.def ?? ""]));
}

export function summary(b: { type: string; params: Record<string, string> }): string {
  const s = SPEC[b.type];
  if (!s) return `Nieznany klocek (${b.type})`;
  const p = (k: string) => b.params[k] ?? s.params?.find((x) => x.key === k)?.def ?? "";
  const l = (k: string) => {
    const v = p(k);
    return s.params?.find((x) => x.key === k)?.options?.find((o) => o[0] === v)?.[1] ?? v;
  };
  try {
    return s.sum ? s.sum(p, l) : s.desc;
  } catch {
    return s.title;
  }
}

export const BUILTIN_VARS: [string, string][] = [
  ["time", "godzina 14:05"], ["time_s", "z sekundami"], ["date", "2026-11-03"], ["date_pl", "3 listopada 2026"], ["datetime", "data i godzina"], ["stamp", "20261103-140501"],
  ["weekday", "dzień tygodnia"], ["hour", "godzina"], ["minute", "minuta"], ["day", "dzień"], ["month", "miesiąc"], ["year", "rok"], ["week", "tydzień roku"], ["timestamp", "sekundy od 1970"],
  ["battery", "bateria %"], ["charging", "tak/nie"], ["idle", "bezczynność (s)"], ["uptime_h", "czas pracy (h)"], ["ram", "zajęta pamięć %"], ["volume", "głośność %"],
  ["clipboard", "schowek"], ["foreground_app", "aktywna aplikacja"], ["foreground_title", "tytuł okna"], ["online", "internet tak/nie"],
  ["user", "użytkownik"], ["computer", "nazwa komputera"], ["home", "folder użytkownika"], ["desktop", "Pulpit"], ["documents", "Dokumenty"], ["downloads", "Pobrane"], ["pictures", "Obrazy"], ["temp", "TEMP"], ["appdata", "AppData"],
  ["sunrise", "wschód słońca (z wyzw.)"], ["random", "0–100"], ["uuid", "losowy identyfikator"], ["routine", "nazwa rutyny"], ["trigger", "co uruchomiło"], ["nl", "nowy wiersz"],
];
