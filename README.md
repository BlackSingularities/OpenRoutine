# OpenRoutine

**Graficzny automat dla Twoich aplikacji: zdarzenie → warunek → akcja.** Na początek pliki, harmonogramy i jawnie udostępnione integracje – a do tego bateria, ekran, sieć, powiadomienia i SMS. Czarno-biały interfejs w stylu OpenPDF, bez reklam, śledzenia i konta. Rutyny zostają w telefonie.

[![Build & Release](../../actions/workflows/android.yml/badge.svg)](../../actions/workflows/android.yml)

## Pobieranie

Najnowszy plik jest w **[Releases → Latest](../../releases/latest)**. Wydanie powstaje wyłącznie po wypchnięciu tagu `vX.Y.Z`, a aplikacja sama proponuje aktualizację.

1. Pobierz `OpenRoutine-x.y.z.apk` na telefon i zainstaluj (Android 10 / API 29 lub nowszy).
2. Zacznij od zakładki **Szablony** albo przycisku **Nowa rutyna**.
3. Nadaj uprawnienia, o które prosi aplikacja – tylko te, których wymagają Twoje rutyny (zakładka **Więcej**).

## Jak to działa

| Część | Znaczenie |
|---|---|
| **Gdy** | Wyzwalacze – wystarczy dowolny z nich. |
| **Jeśli** | Warunki (opcjonalne) – wszystkie albo dowolny. |
| **Wykonaj** | Akcje po kolei; błąd jednej nie zatrzymuje kolejnych, chyba że włączysz „Przerwij po błędzie”. |

Dodatkowo: odstęp między uruchomieniami, przycisk „Wypróbuj”, skrót na ekranie głównym, duplikowanie i kolejność rutyn.

### Wyzwalacze (16)
O godzinie (dni tygodnia) · Co pewien czas · Po uruchomieniu telefonu · Ręcznie lub skrótem · Poziom baterii (spada do / rośnie do) · Ładowarka · Ekran (włączony, wyłączony, odblokowany) · Słuchawki · Wi-Fi · Bluetooth · Tryb samolotowy · Oszczędzanie baterii · Nowy plik w folderze (wzorce `*.pdf;*.jpg`) · Powiadomienie aplikacji (filtr aplikacji i tekstu) · Przychodzący SMS (nadawca, treść) · Zdarzenie z innej aplikacji.

### Warunki (14)
Pora dnia (także przez północ) · Dzień tygodnia · Poziom baterii · Ładowanie · Sieć (Wi-Fi, komórkowa, dowolna, brak) · Ekran · Słuchawki (przewodowe i Bluetooth) · Tryb dzwonka · Nie przeszkadzać · Oszczędzanie baterii · Bluetooth · Zmienna (=, ≠, zawiera, >, <, pusta, ma wartość) · Plik w folderze · Losowo.

### Akcje (27)
Powiadomienie · Krótki komunikat · Powiedz na głos (TTS) · Wibracja (wzory) · Dźwięk · Tryb dzwonka · Nie przeszkadzać · Głośność strumieni · Jasność (ręczna/automatyczna) · Latarka · Sterowanie muzyką · Otwórz aplikację · Otwórz stronę / tel: / geo: · Żądanie HTTP (metoda, nagłówki, treść, odpowiedź do zmiennej) · Udostępnij tekst · Schowek · Wyślij SMS · Kopiuj / przenieś / usuń pliki (wzorzec, wiek) · Zapis tekstu do pliku · Ustaw zmienną · Licz na zmiennej · Czekaj · Przerwij, jeśli… · Uruchom inną rutynę · Włącz / wyłącz rutynę · Wyślij intencję · Wpis do dziennika.

### Zmienne
W każdym polu tekstowym: `{time}`, `{date}`, `{datetime}`, `{weekday}`, `{battery}`, `{routine}`, `{trigger}`, `{random}`, dane wyzwalacza (`{file}`, `{file_uri}`, `{sms_from}`, `{sms_body}`, `{notif_app}`, `{notif_title}`, `{notif_text}`, `{extra}`), `{http_response}`, `{http_code}`, `{files_count}` i własne zmienne.

### Integracje
Inne aplikacje uruchamiają wyzwalacz „Zdarzenie z innej aplikacji” intencją z tokenem z zakładki **Więcej**:

```bash
adb shell am broadcast -a com.blacksingularities.openroutine.EVENT -p com.blacksingularities.openroutine --es event latarka --es token TOKEN --es extra "dane"
```

W drugą stronę działają akcje „Wyślij intencję” i „Żądanie HTTP”.

## Uprawnienia

Każde jest potrzebne tylko wtedy, gdy używasz klocka, który go wymaga: powiadomienia, dokładne alarmy, praca w tle (usługa nasłuchu ze stałym powiadomieniem – działa tylko, gdy któraś rutyna jej potrzebuje), dostęp do powiadomień, Nie przeszkadzać, ustawienia systemu (jasność), wyświetlanie nad aplikacjami (otwieranie ekranów z tła – bez niego rutyna pokaże powiadomienie do dotknięcia), SMS. Pliki są dostępne wyłącznie w folderach wskazanych przez użytkownika.

## Budowanie

```bash
gradle :app:assembleDebug
```

JDK 17 i Android SDK (compileSdk 36). Wydanie buduje GitHub Actions **tylko po wypchnięciu tagu** `vX.Y.Z` zgodnego z plikiem `VERSION`:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Podpis z sekretów `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` (bez nich – klucz debug).

### Struktura
- `model/` – rutyny, katalog klocków, szablony, zapis JSON, czyste funkcje (wzorce, godziny, zmienne).
- `engine/` – silnik, warunki, akcje, alarmy, usługa nasłuchu, odbiorniki (start, SMS, integracje, powiadomienia), pliki SAF.
- `ui/` – ekrany Compose w stylu OpenPDF.
- `docs/` – strona projektu.

---

Pomysł i wykonanie: **Adam Rędzikowski** · [redzikowski.com](https://redzikowski.com) · [☕ Postaw kawę](https://buycoffee.to/adamredzikowski)
