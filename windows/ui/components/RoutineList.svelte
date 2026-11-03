<script lang="ts">
  import Icon from "./Icon.svelte";
  import { SPEC, summary } from "../lib/catalog";
  import { api, type Routine } from "../lib/api";
  import { S, addRoutine, fmtAgo } from "../lib/store.svelte";

  let q = $state("");
  let filter = $state<"all" | "on" | "off" | "err">("all");
  let dragId = $state("");

  const list = $derived(
    S.data.routines.filter((r) => {
      if (filter === "on" && !r.enabled) return false;
      if (filter === "off" && r.enabled) return false;
      if (filter === "err" && (r.lastOk || !r.lastRun)) return false;
      if (!q) return true;
      const s = q.toLowerCase();
      return (r.name + " " + r.group + " " + r.notes).toLowerCase().includes(s) || [...r.triggers, ...r.actions].some((b) => (SPEC[b.type]?.title ?? "").toLowerCase().includes(s));
    }),
  );
  const grouped = $derived.by(() => {
    const m = new Map<string, Routine[]>();
    for (const r of list) {
      const g = r.group || "";
      if (!m.has(g)) m.set(g, []);
      m.get(g)!.push(r);
    }
    return [...m.entries()].sort((a, b) => (a[0] === "" ? -1 : b[0] === "" ? 1 : a[0].localeCompare(b[0], "pl")));
  });

  function line(r: Routine) {
    const t = r.triggers.filter((b) => !b.off);
    if (!t.length) return "tylko ręcznie";
    const first = `${SPEC[t[0].type]?.title ?? ""}: ${summary(t[0])}`;
    return t.length > 1 ? `${first} +${t.length - 1}` : first;
  }

  function drop(target: string) {
    if (!dragId || dragId === target) return;
    const ids = S.data.routines.map((r) => r.id);
    const from = ids.indexOf(dragId);
    const to = ids.indexOf(target);
    ids.splice(to, 0, ids.splice(from, 1)[0]);
    S.data.routines.sort((a, b) => ids.indexOf(a.id) - ids.indexOf(b.id));
    api.reorder(ids);
    dragId = "";
  }

  function toggle(r: Routine) {
    r.enabled = !r.enabled;
    api.setEnabled(r.id, r.enabled);
  }
</script>

<div class="rl">
  <div class="bar">
    <div class="search"><Icon name="search" size={13} /><input type="text" placeholder="Szukaj rutyn…" bind:value={q} /></div>
    <button class="btn primary sm" onclick={() => addRoutine({ name: "Nowa rutyna", triggers: [], actions: [] })}><Icon name="plus" size={13} />Nowa</button>
  </div>
  <div class="seg full flt">
    <button class:on={filter === "all"} onclick={() => (filter = "all")}>Wszystkie {S.data.routines.length}</button>
    <button class:on={filter === "on"} onclick={() => (filter = "on")}>Włączone</button>
    <button class:on={filter === "off"} onclick={() => (filter = "off")}>Wyłączone</button>
    <button class:on={filter === "err"} onclick={() => (filter = "err")}>Z błędem</button>
  </div>
  <div class="items">
    {#each grouped as [g, rs] (g)}
      {#if g}<div class="gh">{g}<span>{rs.length}</span></div>{/if}
      {#each rs as r (r.id)}
        <div
          class="it" class:sel={S.selected === r.id} class:off={!r.enabled} class:drag={dragId === r.id}
          role="button" tabindex="0" draggable="true"
          ondragstart={() => (dragId = r.id)} ondragover={(e) => e.preventDefault()} ondrop={() => drop(r.id)} ondragend={() => (dragId = "")}
          onclick={() => (S.selected = r.id)} onkeydown={(e) => e.key === "Enter" && (S.selected = r.id)}
        >
          <button class="dot" class:on={r.enabled} data-tip={r.enabled ? "Wyłącz" : "Włącz"} aria-label="Włącz lub wyłącz" onclick={(e) => { e.stopPropagation(); toggle(r); }}></button>
          <div class="tx">
            <b>{r.name || "(bez nazwy)"}</b>
            <span>{line(r)} · {r.actions.length} {r.actions.length === 1 ? "akcja" : r.actions.length < 5 && r.actions.length > 0 ? "akcje" : "akcji"}</span>
          </div>
          <div class="right">
            {#if S.running.includes(r.id)}<span class="spin" data-tip="Trwa wykonywanie"></span>
            {:else if r.lastRun && !r.lastOk}<span class="err" data-tip="Ostatnie uruchomienie z błędem"><Icon name="alert" size={13} /></span>{/if}
            <span class="ago">{r.lastRun ? fmtAgo(r.lastRun) : ""}</span>
            <button class="btn ghost sm icon run" data-tip="Uruchom" onclick={(e) => { e.stopPropagation(); api.run(r.id, false); }}><Icon name="play" size={12} /></button>
          </div>
        </div>
      {/each}
    {:else}
      <div class="empty">
        {#if S.data.routines.length}Brak pasujących rutyn.{:else}
          <Icon name="routine" size={30} stroke={1.4} />
          <span>Nie masz jeszcze rutyn.</span>
          <button class="btn sm" onclick={() => (S.tab = "templates")}><Icon name="template" size={13} />Wybierz z szablonów</button>
        {/if}
      </div>
    {/each}
  </div>
</div>

<style>
  .rl {
    height: 100%;
    display: flex;
    flex-direction: column;
    min-height: 0;
  }
  .bar {
    display: flex;
    gap: 6px;
    padding: 10px 10px 6px;
  }
  .search {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 6px;
    height: 26px;
    padding: 0 9px;
    border: 1px solid var(--line-2);
    border-radius: 999px;
    color: var(--text-3);
  }
  .search input {
    flex: 1;
    border: 0;
    background: transparent;
    height: 24px;
    padding: 0;
    box-shadow: none !important;
    font-size: 12px;
  }
  .flt {
    margin: 0 10px 6px;
    padding: 2px;
  }
  .flt button {
    height: 21px;
    font-size: 11px;
    padding: 0 4px;
  }
  .items {
    flex: 1;
    overflow: auto;
    padding: 2px 6px 12px;
  }
  .gh {
    display: flex;
    justify-content: space-between;
    padding: 10px 8px 4px;
    font-size: 10.5px;
    font-weight: 700;
    letter-spacing: 0.5px;
    text-transform: uppercase;
    color: var(--text-3);
  }
  .it {
    display: flex;
    align-items: center;
    gap: 9px;
    padding: 6px 6px 6px 9px;
    border-radius: 9px;
    cursor: pointer;
    border: 1px solid transparent;
  }
  .it:hover {
    background: var(--raised);
  }
  .it.sel {
    background: var(--raised-2);
    border-color: var(--line-2);
  }
  .it.drag {
    opacity: 0.4;
  }
  .it.off .tx b {
    color: var(--text-3);
  }
  .dot {
    width: 10px;
    height: 10px;
    flex: none;
    border-radius: 50%;
    border: 1.5px solid rgba(var(--fg-rgb), 0.4);
    background: transparent;
    cursor: pointer;
    padding: 0;
  }
  .dot.on {
    background: var(--sel-bg);
    border-color: var(--sel-bg);
  }
  .tx {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    line-height: 1.3;
  }
  .tx b {
    font-weight: 600;
    font-size: 12.5px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .tx span {
    font-size: 11px;
    color: var(--text-3);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .right {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  .ago {
    font-size: 10.5px;
    color: var(--text-3);
    white-space: nowrap;
  }
  .run {
    display: none;
  }
  .it:hover .run {
    display: inline-flex;
  }
  .it:hover .ago {
    display: none;
  }
  .err {
    color: var(--err);
    display: flex;
  }
  .spin {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    border: 2px solid rgba(var(--fg-rgb), 0.2);
    border-top-color: var(--text);
    animation: spin 0.8s linear infinite;
  }
  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
