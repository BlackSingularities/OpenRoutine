# Zmiany

## 1.4.1

- Komunikat o nowej wersji bez listy zmian, linków i informacji, skąd pobierana jest aktualizacja.

## 1.4.0

- Android: **kontekstowy tryb telefonu** – nowy wyzwalacz i warunek „Kontekst telefonu”: telefon sam rozpoznaje, że jesteś w aucie (Bluetooth auta, prędkość z lokalizacji), na spotkaniu (trwające wydarzenie w kalendarzu) albo na siłowni (kalendarz, Bluetooth). Własne słowa kluczowe, np. nazwa auta.
- Android: nowy wyzwalacz i warunek **„W pobliżu miejsca”** – wejście w obszar wokół punktu (szerokość, długość, promień) albo wyjście z niego; zmienne {lat}, {lon}, {distance}.
- Android: nowa akcja **„Przeczytaj powiadomienia”** – głosowe streszczenie ostatnich powiadomień, np. po wejściu do auta.
- Android: nowe uprawnienia – Lokalizacja i Kalendarz.

## 1.3.1

- Windows: autostart jest teraz domyślnie wyłączony – aplikacja nie dopisuje się sama do rejestru przy pierwszym uruchomieniu; włączysz go w Ustawieniach.
- Windows: instalator nie zamyka już siłowo procesów (`taskkill /F`) – działającą aplikację zamyka standardowe okno instalatora. Mniej fałszywych alarmów Microsoft Defender („Program modyfikujący ustawienia”).
- Windows: nowa ikona aplikacji.

## 1.3.0

- Windows: nowy ekran **Przegląd** – powitanie, liczniki dnia, najbliższe uruchomienia, ostatnie zdarzenia, szybkie uruchamianie rutyn ręcznych i ze skrótem, lista obserwowanych zdarzeń i rutyn do uzupełnienia.
- Windows: **paleta poleceń** (Ctrl+K) do szukania rutyn, szablonów i poleceń oraz komplet skrótów klawiszowych (Ctrl+1…7, Ctrl+Enter, Ctrl+D, Ctrl+E, Delete, strzałki, F1 – ściągawka).
- Windows: edytor pokazuje najbliższe uruchomienie, oznacza klocki do uzupełnienia, sprawdza warunki „na żywo”, pozwala przeciągać klocki oraz zwijać i rozwijać wszystkie naraz.
- Windows: menu kontekstowe rutyn, cofanie usunięcia, podgląd szablonu przed dodaniem, gęstość interfejsu (kompaktowa / wygodna), zapamiętywanie położenia i rozmiaru okna.
- Windows: nowe wyzwalacze – wschód / zachód słońca (z lokalizacją w Ustawieniach), dostępność serwera, zmiana na stronie lub w API; nowe warunki – dzień / noc, dzień miesiąca, zawartość schowka; nowe akcje – pytanie Tak / Nie, pytanie o tekst, lista plików, zmiana nazw plików według szablonu.
- Windows: 10 nowych szablonów (razem 85).

## 1.2.0

- **Nowość: OpenRoutine dla Windows** – osobna aplikacja (Tauri + Svelte) w stylu OpenPano, bardziej kompaktowa, z ikoną w zasobniku i autostartem. Wydawana wyłącznie jako jeden instalator `OpenRoutine-x.y.z-setup-x64.exe`.
- Windows: 31 wyzwalaczy – godzina, interwał, dzień miesiąca, jednorazowo, po zalogowaniu, ręcznie, globalny skrót klawiszowy, bezczynność i powrót, blokada ekranu, zasilacz, bateria, oszczędzanie energii, CPU, RAM, miejsce na dysku, monitory, motyw, pełny ekran, start/zamknięcie/aktywacja aplikacji, tytuł okna, internet, Wi-Fi, dysk USB, plik w folderze, schowek, zdarzenie HTTP/CLI, inna rutyna, zmiana zmiennej.
- Windows: 31 warunków i 62 akcje – m.in. powiadomienia, mowa, głośność, multimedia, programy i okna, klawisze i wpisywanie tekstu, blokada, uśpienie, wyłączanie, plan zasilania, motyw, tapeta, jasność, zrzut ekranu, kopiowanie/przenoszenie/porządkowanie plików, ZIP, Kosz, TEMP, DNS, HTTP, pobieranie, PowerShell, CMD, zmienne, obliczenia, tekst, JSON i sterowanie rutynami.
- Windows: 75 szablonów w 9 grupach, dziennik z eksportem CSV, zmienne globalne z podglądem, widok stanu systemu na żywo, eksport i import rutyn, lokalny serwer HTTP z tokenem i polecenia `--run` / `--event`.

## 1.1.2

- Dopasowane kolorystycznie tła ikon aplikacji i spójne zasoby graficzne.
- Nowa ikona aplikacji, warianty systemowe i odświeżona grafika na stronie projektu.

## 1.1.0
- 9 nowych wyzwalaczy (razem 25): dzień miesiąca, jednorazowo, połączenie telefoniczne, internet, urządzenie Bluetooth, zmiana trybu dzwonka i Nie przeszkadzać, temperatura baterii, instalacje aplikacji.
- 12 nowych warunków (razem 26): miesiąc, zakres dat, tryb samolotowy, blokada ekranu, muzyka, rozmowa, głośność, temperatura baterii, latarka, aplikacja zainstalowana, stan innej rutyny, czas od ostatniego uruchomienia; porównania zmiennych: nie zawiera, zaczyna się, kończy się, ≥, ≤, wyrażenie regularne.
- 18 nowych akcji (razem 45): krok głośności i wyciszenie, wygaszanie i obracanie ekranu, czyszczenie powiadomień, telefon, e-mail, budzik, minutnik, nawigacja, ekrany ustawień, przetwarzanie tekstu, losowanie, data do zmiennej, odczyt JSON, wczytanie pliku, usuwanie zmiennej, „Pomiń akcje, jeśli…”, czekanie do godziny; działania reszta/min/max.
- Nowe zmienne wbudowane: dzień, miesiąc, rok, znacznik czasu, ładowanie, sieć, dzwonek, głośność, temperatura baterii.
- 27 nowych, rozbudowanych szablonów (razem 42), pogrupowanych tematycznie.
- Dolny pasek na całą szerokość, bez obramowania, przyklejony do dołu ekranu.

## 1.0.0
- Pierwsze wydanie OpenRoutine: rutyny „zdarzenie → warunek → akcja” w czarno-białym interfejsie w stylu OpenPDF.
- 16 wyzwalaczy: godzina i dni tygodnia, interwał, start telefonu, ręcznie/skrót, bateria, ładowarka, ekran, słuchawki, Wi-Fi, Bluetooth, tryb samolotowy, oszczędzanie baterii, nowy plik w folderze, powiadomienie aplikacji, SMS, zdarzenie z innej aplikacji.
- 14 warunków (wszystkie lub dowolny) i 27 akcji: powiadomienia, mowa, dźwięk, głośność, jasność, latarka, muzyka, aplikacje, strony, HTTP, SMS, schowek, pliki, zmienne, pauzy, sterowanie rutynami, intencje.
- Zmienne w tekstach ({time}, {battery}, {file}, {sms_body} i własne), 15 szablonów, dziennik, próba w edytorze, skróty na ekranie głównym, eksport i import.
- Jawna integracja z innymi aplikacjami przez intencję z tokenem.
- Automatyczne aktualizacje z wydań.
