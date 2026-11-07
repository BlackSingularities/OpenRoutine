<script lang="ts">
  import { onMount } from "svelte";
  import { getVersion } from "@tauri-apps/api/app";
  import { initFeedback } from "./lib/feedback.js";
  import Icon from "./components/Icon.svelte";
  import Tooltip from "./components/Tooltip.svelte";
  import RoutineList from "./components/RoutineList.svelte";
  import Editor from "./components/Editor.svelte";
  import Templates from "./components/Templates.svelte";
  import LogView from "./components/LogView.svelte";
  import VarsView from "./components/VarsView.svelte";
  import StateView from "./components/StateView.svelte";
  import SettingsView from "./components/SettingsView.svelte";
  import Overview from "./components/Overview.svelte";
  import Palette from "./components/Palette.svelte";
  import Dialogs from "./components/Dialogs.svelte";
  import { api } from "./lib/api";
  import { S, init, saveSettings, addRoutine, duplicate, removeRoutine, setEnabled, dismissToast, type Tab } from "./lib/store.svelte";
  import { upd, startUpdateChecks, openUpdate } from "./lib/updates.svelte";

  const TABS: { id: Tab; icon: string; label: string }[] = [
    { id: "overview", icon: "gauge", label: "Przegląd" },
    { id: "routines", icon: "routine", label: "Rutyny" },
    { id: "templates", icon: "template", label: "Szablony" },
    { id: "log", icon: "list", label: "Dziennik" },
    { id: "vars", icon: "variable", label: "Zmienne" },
    { id: "state", icon: "cpu", label: "Stan" },
    { id: "settings", icon: "gear", label: "Ustawienia" },
  ];

  const sel = $derived(S.data.routines.find((r) => r.id === S.selected));
  const errors = $derived(S.data.log.slice(0, 50).filter((l) => !l.ok && Date.now() - l.time < 86400000).length);
  const active = $derived(S.data.routines.filter((r) => r.enabled).length);
  const showUpd = $derived(!!upd.available && S.data.settings.skippedVersion !== upd.available.version);

  onMount(() => {
    initFeedback({ app: "openroutine", appName: "OpenRoutine", version: () => getVersion(), lang: "pl", platform: "windows" });
    init().then(() => startUpdateChecks(() => S.data.settings.checkUpdates));
    const k = (e: KeyboardEvent) => {
      const typing = (e.target as HTMLElement)?.closest("input, textarea, select");
      const r = S.tab === "routines" ? S.data.routines.find((x) => x.id === S.selected) : undefined;
      const ctrl = e.ctrlKey && !e.altKey;
      if (ctrl && e.key.toLowerCase() === "k") {
        e.preventDefault();
        S.palette = !S.palette;
      } else if (ctrl && e.key.toLowerCase() === "n" && !e.shiftKey) {
        e.preventDefault();
        addRoutine({ name: "Nowa rutyna", triggers: [], actions: [] });
      } else if (ctrl && e.key.toLowerCase() === "f") {
        e.preventDefault();
        document.querySelector<HTMLInputElement>(".search input")?.focus();
      } else if (ctrl && /^[1-7]$/.test(e.key)) {
        e.preventDefault();
        S.tab = TABS[+e.key - 1].id;
      } else if (ctrl && e.key === ",") {
        e.preventDefault();
        S.tab = "settings";
      } else if (ctrl && e.key.toLowerCase() === "p") {
        e.preventDefault();
        toggleEngine();
      } else if (e.key === "F1") {
        e.preventDefault();
        S.help = !S.help;
      } else if (r && ctrl && e.key === "Enter") {
        e.preventDefault();
        api.run(r.id, false);
      } else if (r && ctrl && e.key.toLowerCase() === "d") {
        e.preventDefault();
        duplicate(r);
      } else if (r && ctrl && e.key.toLowerCase() === "e") {
        e.preventDefault();
        setEnabled(r, !r.enabled);
      } else if (r && !typing && e.key === "Delete") {
        removeRoutine(r.id);
      } else if (S.tab === "routines" && !typing && (e.key === "ArrowDown" || e.key === "ArrowUp")) {
        const ids = [...document.querySelectorAll<HTMLElement>(".rl .it[data-id]")].map((x) => x.dataset.id!);
        if (!ids.length) return;
        e.preventDefault();
        const i = ids.indexOf(S.selected);
        S.selected = ids[Math.max(0, Math.min(ids.length - 1, i + (e.key === "ArrowDown" ? 1 : -1)))] ?? ids[0];
      }
    };
    window.addEventListener("keydown", k);
    // brak menu kontekstowego przeglądarki poza polami tekstowymi
    window.addEventListener("contextmenu", (e) => {
      if (!(e.target as HTMLElement).closest("input, textarea, .selectable")) e.preventDefault();
    });
    return () => window.removeEventListener("keydown", k);
  });

  function toggleEngine() {
    S.data.settings.engineOn = !S.data.settings.engineOn;
    saveSettings();
  }
</script>

<Tooltip />
<Dialogs />
{#if S.palette}<Palette />{/if}

<div class="shell" class:roomy={S.data.settings.density === "comfortable"}>
  <nav class="rail">
    <img src="/icon.png" alt="OpenRoutine" class="logo" />
    <button class="pk" data-tip="Szukaj i polecenia (Ctrl+K)" onclick={() => (S.palette = true)}><span class="ti"><Icon name="search" size={16} /></span>Szukaj</button>
    {#each TABS as t}
      <button class:on={S.tab === t.id} onclick={() => (S.tab = t.id)}>
        <span class="ti"><Icon name={t.icon} size={17} /></span>
        {t.label}
        {#if t.id === "log" && errors}<b>{errors}</b>{/if}
      </button>
    {/each}
    <div class="grow"></div>
    <button class="eng" class:paused={!S.data.settings.engineOn} data-tip={S.data.settings.engineOn ? "Wstrzymaj wszystkie rutyny" : "Wznów rutyny"} onclick={toggleEngine}>
      <span class="ti"><Icon name={S.data.settings.engineOn ? "pause" : "play"} size={15} /></span>
      {S.data.settings.engineOn ? "Działa" : "Pauza"}
    </button>
  </nav>

  <main>
    {#if !S.ready}
      <div class="empty">Uruchamianie…</div>
    {:else if S.tab === "overview"}
      <Overview />
    {:else if S.tab === "routines"}
      <div class="split">
        <aside><RoutineList /></aside>
        <section class="pane">
          {#if sel}
            {#key sel.id}<Editor routine={sel} />{/key}
          {:else}
            <div class="welcome">
              <img src="/icon.png" alt="" />
              <h1>OpenRoutine</h1>
              <p>Automatyzacja Windows: <b>gdy</b> coś się wydarzy, <b>jeśli</b> warunki są spełnione – <b>wykonaj</b> akcje.</p>
              <div class="wb">
                <button class="btn primary" onclick={() => document.querySelector<HTMLButtonElement>(".rl .btn.primary")?.click()}><Icon name="plus" size={14} />Nowa rutyna</button>
                <button class="btn" onclick={() => (S.tab = "templates")}><Icon name="template" size={14} />Szablony</button>
              </div>
              <p class="muted small">Ctrl+K – paleta poleceń · Ctrl+N – nowa rutyna · F1 – skróty · okno można zamknąć, rutyny działają w zasobniku.</p>
            </div>
          {/if}
        </section>
      </div>
    {:else if S.tab === "templates"}
      <Templates />
    {:else if S.tab === "log"}
      <LogView />
    {:else if S.tab === "vars"}
      <VarsView />
    {:else if S.tab === "state"}
      <StateView />
    {:else}
      <SettingsView />
    {/if}
  </main>

  <footer>
    <span class="led" class:off={!S.data.settings.engineOn}></span>
    <span>{S.data.settings.engineOn ? "Rutyny aktywne" : "Wstrzymano"} · {active}/{S.data.routines.length} włączonych</span>
    {#if S.running.length}<span>· wykonywane: {S.running.length}</span>{/if}
    {#if S.failedHotkeys.length}<span class="warn">· zajęte skróty: {S.failedHotkeys.join(", ")}</span>{/if}
    <div class="grow"></div>
    {#if S.snap}
      <span>{S.snap.hasBattery ? `🔋 ${S.snap.battery}%${S.snap.ac ? " ⚡" : ""} · ` : ""}CPU {Math.round(S.snap.cpu)}% · RAM {Math.round(S.snap.ram)}%{S.snap.ssid ? ` · ${S.snap.ssid}` : ""}</span>
    {/if}
    {#if showUpd && upd.available}
      <button class="upd" onclick={() => openUpdate(true)}><Icon name="download" size={12} />Aktualizacja {upd.available.version}</button>
      <button class="upd ghost" onclick={() => { S.data.settings.skippedVersion = upd.available!.version; saveSettings(); }}>Pomiń</button>
    {/if}
  </footer>
</div>

<div class="toasts">
  {#each S.toasts as t (t.id)}
    <div class="toast {t.kind}">
      <Icon name={t.kind === "error" ? "alert" : t.kind === "ok" ? "ok" : "info"} size={14} /><span>{t.text}</span>
      {#if t.action}<button class="btn sm" onclick={() => { t.action!.run(); dismissToast(t.id); }}>{t.action.label}</button>{/if}
      <button class="x" aria-label="Zamknij" onclick={() => dismissToast(t.id)}><Icon name="x" size={12} /></button>
    </div>
  {/each}
</div>

<style>
  .shell {
    display: grid;
    grid-template-columns: 72px 1fr;
    grid-template-rows: 1fr 24px;
    height: 100%;
  }
  .rail {
    grid-row: 1 / 3;
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 3px;
    padding: 10px 6px 8px;
    border-right: 1px solid var(--line);
    background: var(--bg);
  }
  .logo {
    width: 34px;
    height: 34px;
    align-self: center;
    margin-bottom: 10px;
    border-radius: 9px;
  }
  /* Styl wspólny dla aplikacji Open: wybrana zakładka to kafel z odwróconą ikoną i podpisem. */
  .rail button {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 3px;
    padding: 7px 0 6px;
    border: 0;
    border-radius: 8px;
    background: transparent;
    color: var(--text-2);
    font-size: 10px;
    font-weight: 500;
    cursor: pointer;
    transition: background 0.12s, color 0.12s;
  }
  .rail button:hover {
    background: var(--raised);
    color: var(--text);
  }
  .rail button.on {
    background: var(--sel-bg);
    color: var(--sel-fg);
    font-weight: 700;
  }
  .ti {
    width: 20px;
    height: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .rail b {
    position: absolute;
    top: 3px;
    right: 8px;
    min-width: 15px;
    height: 15px;
    padding: 0 4px;
    border-radius: 8px;
    background: var(--err);
    color: #fff;
    font-size: 9px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .rail .eng.paused {
    color: var(--warn);
  }
  .grow {
    flex: 1;
  }
  main {
    min-width: 0;
    min-height: 0;
    overflow: hidden;
  }
  .split {
    height: 100%;
    display: grid;
    grid-template-columns: minmax(270px, 330px) 1fr;
  }
  aside {
    min-height: 0;
    border-right: 1px solid var(--line);
  }
  .pane {
    min-width: 0;
    min-height: 0;
  }
  .welcome {
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    text-align: center;
    padding: 20px;
  }
  .welcome img {
    width: 72px;
    height: 72px;
    border-radius: 18px;
  }
  .welcome h1 {
    margin: 4px 0 0;
    font-family: var(--font-display);
    font-size: 22px;
  }
  .welcome p {
    margin: 0;
    max-width: 440px;
    color: var(--text-2);
    line-height: 1.5;
  }
  .wb {
    display: flex;
    gap: 8px;
    margin: 10px 0 6px;
  }
  footer {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 0 12px;
    font-size: 11px;
    color: var(--text-3);
    border-top: 1px solid var(--line);
    white-space: nowrap;
    overflow: hidden;
  }
  .led {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--ok);
    box-shadow: 0 0 6px rgba(var(--fg-rgb), 0.4);
  }
  .led.off {
    background: var(--warn);
  }
  .warn {
    color: var(--warn);
  }
  .upd {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 18px;
    padding: 0 8px;
    border-radius: 999px;
    border: 0;
    background: var(--sel-bg);
    color: var(--sel-fg);
    font-size: 10.5px;
    font-weight: 600;
    cursor: pointer;
  }
  .upd.ghost {
    background: transparent;
    color: var(--text-3);
    font-weight: 400;
  }
  .toasts {
    position: fixed;
    right: 14px;
    bottom: 34px;
    z-index: 100;
    display: flex;
    flex-direction: column;
    gap: 6px;
    align-items: flex-end;
  }
  .toast {
    display: flex;
    align-items: center;
    gap: 8px;
    max-width: 420px;
    padding: 8px 12px;
    border-radius: 10px;
    border: 1px solid var(--line-2);
    background: var(--surface);
    box-shadow: var(--shadow);
    font-size: 12px;
    animation: tin 0.16s ease-out;
  }
  .toast span {
    flex: 1;
  }
  .toast .x {
    border: 0;
    background: none;
    color: var(--text-3);
    cursor: pointer;
    padding: 2px;
    display: flex;
  }
  .rail .pk {
    margin-bottom: 6px;
    border: 1px solid var(--line);
  }
  .shell.roomy :global(.it),
  .shell.roomy :global(.card .head) {
    padding-top: 9px;
    padding-bottom: 9px;
  }
  .shell.roomy {
    font-size: 13px;
  }
  .toast.error {
    border-color: var(--err);
    color: var(--err);
  }
  @keyframes tin {
    from {
      transform: translateY(6px);
      opacity: 0;
    }
  }
</style>
