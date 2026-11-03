<script lang="ts">
  import { open as openDlg, save } from "@tauri-apps/plugin-dialog";
  import { openUrl, openPath } from "@tauri-apps/plugin-opener";
  import Icon from "./Icon.svelte";
  import Switch from "./Switch.svelte";
  import { api, type Routine } from "../lib/api";
  import { THEMES, type ThemeId } from "../lib/theme";
  import { S, saveSettings, setTheme, toast, refresh } from "../lib/store.svelte";
  import { upd, checkForUpdate, openUpdate } from "../lib/updates.svelte";

  const st = $derived(S.data.settings);
  const THEME_NAMES: Record<string, string> = { dark: "Czarny", light: "Biały", gray: "Grafit", classic: "Klasyczny", custom: "Własny" };
  const base = $derived(`http://127.0.0.1:${st.webhookPort}`);
  let showToken = $state(false);
  const CITIES: [string, number, number][] = [
    ["Warszawa", 52.2297, 21.0122], ["Kraków", 50.0647, 19.945], ["Łódź", 51.7592, 19.456], ["Wrocław", 51.1079, 17.0385], ["Poznań", 52.4064, 16.9252],
    ["Gdańsk", 54.352, 18.6466], ["Szczecin", 53.4285, 14.5528], ["Bydgoszcz", 53.1235, 18.0084], ["Lublin", 51.2465, 22.5684], ["Białystok", 53.1325, 23.1688],
    ["Katowice", 50.2649, 19.0238], ["Rzeszów", 50.0412, 21.9991], ["Olsztyn", 53.7784, 20.4801], ["Kielce", 50.8661, 20.6286], ["Opole", 50.6751, 17.9213],
    ["Zielona Góra", 51.9356, 15.5062], ["Gorzów Wlkp.", 52.7368, 15.2288], ["Toruń", 53.0138, 18.5984], ["Londyn", 51.5072, -0.1276], ["Berlin", 52.52, 13.405],
  ];
  let sun = $state<[string, string] | null>(null);
  $effect(() => {
    st.latitude;
    st.longitude;
    api.sunInfo().then((v) => (sun = v)).catch(() => {});
  });
  function setCity(name: string) {
    const c = CITIES.find((x) => x[0] === name);
    if (!c) return;
    S.data.settings.city = c[0];
    S.data.settings.latitude = c[1];
    S.data.settings.longitude = c[2];
    saveSettings();
  }

  function set<K extends keyof typeof st>(k: K, v: (typeof st)[K]) {
    (S.data.settings as any)[k] = v;
    saveSettings();
  }
  function copy(t: string) {
    navigator.clipboard.writeText(t);
    toast("Skopiowano.", "ok");
  }

  async function exportAll() {
    const p = await save({ defaultPath: `OpenRoutine kopia.json`, filters: [{ name: "OpenRoutine", extensions: ["json"] }] });
    if (!p) return;
    await api.writeText(p, JSON.stringify({ app: "OpenRoutine", version: 1, routines: $state.snapshot(S.data.routines), vars: $state.snapshot(S.data.vars) }, null, 2));
    toast(`Wyeksportowano ${S.data.routines.length} rutyn.`, "ok");
  }
  async function importFile() {
    const p = await openDlg({ multiple: false, filters: [{ name: "OpenRoutine", extensions: ["json"] }] });
    if (typeof p !== "string") return;
    try {
      const j = JSON.parse(await api.readText(p));
      const rs: Routine[] = Array.isArray(j) ? j : j.routines ?? [];
      if (!rs.length) return toast("W pliku nie ma rutyn.", "error");
      const n = await api.importRoutines(rs.map((r) => ({ enabled: false, group: "", notes: "", matchAll: true, cooldownSec: 0, stopOnError: false, quiet: false, conditions: [], lastRun: 0, runs: 0, lastOk: true, ...r, enabled: false })));
      for (const [k, v] of Object.entries(j.vars ?? {})) if (!(k in S.data.vars)) await api.setVar(k, String(v));
      await refresh();
      toast(`Zaimportowano ${n} rutyn (wyłączone – włącz te, których potrzebujesz).`, "ok");
    } catch (e) {
      toast("Nie można odczytać pliku: " + e, "error");
    }
  }
  async function manualUpdate() {
    const ok = await checkForUpdate();
    if (!ok) toast("Nie udało się sprawdzić aktualizacji.", "error");
    else if (!upd.available) toast(`Masz najnowszą wersję (${upd.current}).`, "ok");
  }
</script>

<div class="sv">
  <div class="cols">
    <section>
      <h3><Icon name="power" size={14} />Działanie</h3>
      <Switch label="Rutyny aktywne" checked={st.engineOn} onchange={(v) => set("engineOn", v)} tip="Wyłączenie wstrzymuje wszystkie wyzwalacze" />
      <Switch label="Uruchamiaj razem z Windows" checked={st.autostart} onchange={(v) => set("autostart", v)} />
      <Switch label="Zamknięcie okna chowa do zasobnika" checked={st.closeToTray} onchange={(v) => set("closeToTray", v)} />
      <Switch label="Startuj zminimalizowany" checked={st.startMinimized} onchange={(v) => set("startMinimized", v)} />
      <Switch label="Powiadamiaj o błędach rutyn" checked={st.notifyErrors} onchange={(v) => set("notifyErrors", v)} />
      <label class="f"><span>Długość dziennika (wpisów)</span><input type="number" min="50" max="5000" value={st.logLimit} onchange={(e) => set("logLimit", +e.currentTarget.value)} /></label>
      <p class="note">Rutyny działają w tle, dopóki ikona OpenRoutine jest w zasobniku. Z menu ikony uruchomisz rutyny z wyzwalaczem „Ręcznie”.</p>
    </section>

    <section>
      <h3><Icon name="contrast" size={14} />Wygląd</h3>
      <div class="themes">
        {#each THEMES as t}
          <button class:on={st.theme === t.id} onclick={() => setTheme(t.id as ThemeId)}><span class="sw" style="background:{t.swatch}"></span>{THEME_NAMES[t.id]}</button>
        {/each}
      </div>
      {#if st.theme === "custom"}
        <label class="f row"><span>Kolor tła</span><input type="color" value={st.customColor || "#1e2a44"} oninput={(e) => set("customColor", e.currentTarget.value)} /></label>
      {/if}

      <label class="f row"><span>Gęstość interfejsu</span>
        <div class="seg">
          <button class:on={st.density !== "comfortable"} onclick={() => set("density", "compact")}>Kompaktowa</button>
          <button class:on={st.density === "comfortable"} onclick={() => set("density", "comfortable")}>Wygodna</button>
        </div>
      </label>

      <h3><Icon name="sun" size={14} />Lokalizacja (słońce)</h3>
      <div class="two3">
        <label class="f"><span>Miasto</span>
          <select value={st.city || ""} onchange={(e) => setCity(e.currentTarget.value)}>
            <option value="">Własne współrzędne</option>
            {#each CITIES as c}<option value={c[0]}>{c[0]}</option>{/each}
          </select>
        </label>
        <label class="f"><span>Szerokość</span><input type="number" step="0.0001" value={st.latitude} onchange={(e) => { S.data.settings.city = ""; set("latitude", +e.currentTarget.value); }} /></label>
        <label class="f"><span>Długość</span><input type="number" step="0.0001" value={st.longitude} onchange={(e) => { S.data.settings.city = ""; set("longitude", +e.currentTarget.value); }} /></label>
      </div>
      {#if sun}<p class="note">Dziś wschód {sun[0]}, zachód {sun[1]} – używane przez wyzwalacz „Wschód / zachód słońca” i warunek „Dzień / noc”.</p>{/if}

      <h3><Icon name="upload" size={14} />Kopia i przenoszenie</h3>
      <div class="btns">
        <button class="btn sm" onclick={exportAll}><Icon name="upload" size={13} />Eksportuj wszystko</button>
        <button class="btn sm" onclick={importFile}><Icon name="download" size={13} />Importuj z pliku</button>
        <button class="btn ghost sm" onclick={async () => openPath(await api.dataDir())}><Icon name="folder" size={13} />Folder danych</button>
      </div>
    </section>

    <section>
      <h3><Icon name="webhook" size={14} />Integracje</h3>
      <Switch label="Lokalny serwer HTTP (tylko ten komputer)" checked={st.webhookOn} onchange={(v) => set("webhookOn", v)} />
      <div class="two">
        <label class="f"><span>Port</span><input type="number" min="1024" max="65535" value={st.webhookPort} onchange={(e) => set("webhookPort", +e.currentTarget.value)} /></label>
        <label class="f"><span>Token</span>
          <div class="tok">
            <input type="text" readonly value={showToken ? st.token : "••••••••••••"} class="mono" />
            <button class="btn ghost sm icon" data-tip={showToken ? "Ukryj" : "Pokaż"} onclick={() => (showToken = !showToken)}><Icon name={showToken ? "eyeOff" : "eye"} size={13} /></button>
            <button class="btn ghost sm icon" data-tip="Kopiuj" onclick={() => copy(st.token)}><Icon name="copy" size={13} /></button>
            <button class="btn ghost sm icon" data-tip="Nowy token" onclick={async () => { S.data.settings.token = await api.newToken(); }}><Icon name="refresh" size={13} /></button>
          </div>
        </label>
      </div>
      <div class="code selectable">
        <div><span>Zdarzenie</span><code>{base}/event/NAZWA?token=…&amp;extra=dane</code><button class="btn ghost sm icon" onclick={() => copy(`${base}/event/NAZWA?token=${st.token}&extra=dane`)}><Icon name="copy" size={12} /></button></div>
        <div><span>Rutyna</span><code>{base}/run/NAZWA_RUTYNY?token=…</code><button class="btn ghost sm icon" onclick={() => copy(`${base}/run/NAZWA_RUTYNY?token=${st.token}`)}><Icon name="copy" size={12} /></button></div>
        <div><span>CLI</span><code>OpenRoutine.exe --event NAZWA --extra "dane"</code><button class="btn ghost sm icon" onclick={() => copy(`"%ProgramFiles%\\OpenRoutine\\OpenRoutine.exe" --event NAZWA --extra "dane"`)}><Icon name="copy" size={12} /></button></div>
        <div><span>CLI</span><code>OpenRoutine.exe --run "Nazwa rutyny"</code><button class="btn ghost sm icon" onclick={() => copy(`"%ProgramFiles%\\OpenRoutine\\OpenRoutine.exe" --run "Nazwa rutyny"`)}><Icon name="copy" size={12} /></button></div>
      </div>
      <p class="note">Serwer nasłuchuje wyłącznie na 127.0.0.1. Token można podać też w nagłówku <code>X-Token</code>. W drugą stronę działają akcje „Żądanie HTTP”, „PowerShell” i „Polecenie CMD”.</p>
    </section>

    <section>
      <h3><Icon name="info" size={14} />O programie</h3>
      <div class="about">
        <img src="/icon.png" alt="" />
        <div>
          <b>OpenRoutine dla Windows</b>
          <span>Wersja {upd.current}</span>
        </div>
      </div>
      <Switch label="Sprawdzaj aktualizacje" checked={st.checkUpdates} onchange={(v) => set("checkUpdates", v)} />
      <div class="btns">
        <button class="btn sm" onclick={manualUpdate} disabled={upd.checking}><Icon name="refresh" size={13} />{upd.checking ? "Sprawdzanie…" : "Sprawdź teraz"}</button>
        {#if upd.available}<button class="btn primary sm" onclick={() => openUpdate(true)}><Icon name="download" size={13} />Pobierz {upd.available.version}</button>{/if}
      </div>
      <div class="links">
        <button class="btn ghost sm" onclick={() => openUrl("https://redzikowski.com")}><Icon name="globe" size={13} />redzikowski.com</button>
        <button class="btn ghost sm" onclick={() => openUrl("mailto:kontakt@redzikowski.com")}><Icon name="mail" size={13} />kontakt@redzikowski.com</button>
        <button class="btn ghost sm" onclick={() => openUrl("https://buycoffee.to/adamredzikowski")}><Icon name="coffee" size={13} />Postaw kawę</button>
      </div>
      <p class="note">Pomysł i wykonanie: Adam Rędzikowski · redzikowski.com. Bez reklam, śledzenia i konta – rutyny zostają na tym komputerze.</p>
      <button class="btn ghost sm quit" onclick={() => api.quit()}><Icon name="power" size={13} />Zakończ OpenRoutine (zatrzymuje rutyny)</button>
    </section>
  </div>
</div>

<style>
  .sv {
    height: 100%;
    overflow: auto;
    padding: 14px 16px 24px;
  }
  .cols {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
    gap: 10px;
    align-items: start;
  }
  section {
    border: 1px solid var(--line);
    border-radius: 12px;
    padding: 10px 14px 12px;
    display: flex;
    flex-direction: column;
    gap: 7px;
  }
  h3 {
    display: flex;
    align-items: center;
    gap: 7px;
    margin: 4px 0 2px;
    font-size: 12.5px;
    font-family: var(--font-display);
  }
  .f {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 11px;
    color: var(--text-2);
  }
  .f.row {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
  .f input[type="number"] {
    width: 100%;
    text-align: left;
  }
  .two {
    display: grid;
    grid-template-columns: 90px 1fr;
    gap: 8px;
  }
  .two3 {
    display: grid;
    grid-template-columns: 1.3fr 1fr 1fr;
    gap: 8px;
  }
  .two3 select {
    width: 100%;
  }
  .seg button {
    height: 22px;
    font-size: 11.5px;
  }
  .tok {
    display: flex;
    gap: 2px;
  }
  .tok input {
    flex: 1;
  }
  .note {
    margin: 2px 0 0;
    font-size: 11px;
    line-height: 1.45;
    color: var(--text-3);
  }
  .note code,
  .code code {
    font-family: var(--mono);
    font-size: 10.5px;
  }
  .themes {
    display: flex;
    flex-wrap: wrap;
    gap: 5px;
  }
  .themes button {
    display: flex;
    align-items: center;
    gap: 6px;
    height: 26px;
    padding: 0 10px 0 5px;
    border: 1px solid var(--line-2);
    border-radius: 999px;
    background: transparent;
    cursor: pointer;
    font-size: 11.5px;
  }
  .themes button.on {
    background: var(--sel-bg);
    color: var(--sel-fg);
    border-color: var(--sel-bg);
  }
  .themes .sw {
    width: 16px;
    height: 16px;
    border-radius: 50%;
    border: 1px solid rgba(var(--fg-rgb), 0.3);
  }
  .btns,
  .links {
    display: flex;
    flex-wrap: wrap;
    gap: 5px;
  }
  .code {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 6px 8px;
    border-radius: 9px;
    background: var(--raised);
  }
  .code div {
    display: grid;
    grid-template-columns: 62px 1fr auto;
    align-items: center;
    gap: 6px;
  }
  .code span {
    font-size: 10.5px;
    color: var(--text-3);
  }
  .code code {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .about {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .about img {
    width: 40px;
    height: 40px;
    border-radius: 10px;
  }
  .about div {
    display: flex;
    flex-direction: column;
  }
  .about span {
    font-size: 11px;
    color: var(--text-3);
  }
  .quit {
    align-self: flex-start;
    color: var(--text-3);
  }
</style>
