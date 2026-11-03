<script lang="ts">
  import Icon from "./Icon.svelte";
  import { api } from "../lib/api";
  import { PRESETS } from "../lib/presets";
  import { S, addRoutine, openRoutine, saveSettings } from "../lib/store.svelte";

  interface Item {
    icon: string;
    label: string;
    hint: string;
    run: () => void;
  }
  let q = $state("");
  let idx = $state(0);
  let input: HTMLInputElement;

  const close = () => (S.palette = false);
  const go = (tab: typeof S.tab) => () => (S.tab = tab);

  const commands: Item[] = [
    { icon: "plus", label: "Nowa rutyna", hint: "Ctrl+N", run: () => addRoutine({ name: "Nowa rutyna", triggers: [], actions: [] }) },
    { icon: "gauge", label: "Przegląd", hint: "Ctrl+1", run: go("overview") },
    { icon: "routine", label: "Rutyny", hint: "Ctrl+2", run: go("routines") },
    { icon: "template", label: "Szablony", hint: "Ctrl+3", run: go("templates") },
    { icon: "list", label: "Dziennik", hint: "Ctrl+4", run: go("log") },
    { icon: "variable", label: "Zmienne", hint: "Ctrl+5", run: go("vars") },
    { icon: "cpu", label: "Stan systemu", hint: "Ctrl+6", run: go("state") },
    { icon: "gear", label: "Ustawienia", hint: "Ctrl+,", run: go("settings") },
    { icon: "pause", label: "Wstrzymaj / wznów wszystkie rutyny", hint: "Ctrl+P", run: () => { S.data.settings.engineOn = !S.data.settings.engineOn; saveSettings(); } },
    { icon: "keyboard", label: "Skróty klawiszowe", hint: "F1", run: () => (S.help = true) },
  ];

  const items = $derived.by<Item[]>(() => {
    const s = q.trim().toLowerCase();
    const routines: Item[] = S.data.routines.flatMap((r) => [
      { icon: r.enabled ? "routine" : "eyeOff", label: r.name, hint: r.group || "rutyna", run: () => openRoutine(r.id) },
      ...(s ? [{ icon: "play", label: `Uruchom: ${r.name}`, hint: "uruchom teraz", run: () => api.run(r.id, false) }] : []),
    ]);
    const tpl: Item[] = s ? PRESETS.map((p) => ({ icon: "template", label: `Szablon: ${p.title}`, hint: p.group, run: () => { S.preview = p; } })) : [];
    const all = [...commands, ...routines, ...tpl];
    if (!s) return all.slice(0, 40);
    return all.filter((i) => (i.label + " " + i.hint).toLowerCase().includes(s)).slice(0, 40);
  });

  $effect(() => {
    q;
    idx = 0;
  });
  $effect(() => input?.focus());

  function key(e: KeyboardEvent) {
    if (e.key === "ArrowDown") (idx = Math.min(items.length - 1, idx + 1)), e.preventDefault();
    else if (e.key === "ArrowUp") (idx = Math.max(0, idx - 1)), e.preventDefault();
    else if (e.key === "Enter" && items[idx]) pick(items[idx]);
    else if (e.key === "Escape") close();
  }
  function pick(i: Item) {
    close();
    i.run();
  }
</script>

<div class="scrim" role="presentation" onclick={close}>
  <div class="pal" role="dialog" aria-modal="true" tabindex="-1" onclick={(e) => e.stopPropagation()} onkeydown={() => {}}>
    <div class="in"><Icon name="search" size={15} /><input bind:this={input} bind:value={q} onkeydown={key} placeholder="Szukaj rutyn, szablonów i poleceń…" /><kbd>Esc</kbd></div>
    <div class="list">
      {#each items as it, i}
        <button class:on={i === idx} onmouseenter={() => (idx = i)} onclick={() => pick(it)}>
          <Icon name={it.icon} size={14} /><span>{it.label}</span><em>{it.hint}</em>
        </button>
      {:else}
        <div class="none">Brak wyników.</div>
      {/each}
    </div>
  </div>
</div>

<style>
  .scrim {
    position: fixed;
    inset: 0;
    z-index: 90;
    background: rgba(0, 0, 0, 0.4);
    display: flex;
    justify-content: center;
    padding-top: 12vh;
  }
  .pal {
    width: min(560px, calc(100% - 40px));
    max-height: 60vh;
    display: flex;
    flex-direction: column;
    background: var(--surface);
    border: 1px solid var(--line-2);
    border-radius: 14px;
    box-shadow: var(--shadow);
    overflow: hidden;
    align-self: flex-start;
  }
  .in {
    display: flex;
    align-items: center;
    gap: 9px;
    padding: 0 12px;
    border-bottom: 1px solid var(--line);
    color: var(--text-3);
  }
  .in input {
    flex: 1;
    height: 42px;
    border: 0 !important;
    background: transparent !important;
    box-shadow: none !important;
    font-size: 14px;
    padding: 0;
  }
  kbd {
    font-family: var(--mono);
    font-size: 10px;
    border: 1px solid var(--line-2);
    border-radius: 4px;
    padding: 1px 5px;
  }
  .list {
    overflow: auto;
    padding: 5px;
  }
  .list button {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 7px 9px;
    border: 0;
    border-radius: 8px;
    background: transparent;
    text-align: left;
    cursor: pointer;
    color: var(--text-2);
  }
  .list button span {
    flex: 1;
    color: var(--text);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .list button em {
    font-style: normal;
    font-size: 11px;
    color: var(--text-3);
  }
  .list button.on {
    background: var(--sel-bg);
    color: var(--sel-fg);
  }
  .list button.on span,
  .list button.on em {
    color: inherit;
  }
  .none {
    padding: 16px;
    text-align: center;
    color: var(--text-3);
  }
</style>
