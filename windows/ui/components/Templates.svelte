<script lang="ts">
  import Icon from "./Icon.svelte";
  import { GROUPS, PRESETS, presetToRoutine, type Preset } from "../lib/presets";
  import { SPEC } from "../lib/catalog";
  import { S, addRoutine, toast } from "../lib/store.svelte";

  let q = $state("");
  let group = $state("");
  const list = $derived(PRESETS.filter((p) => (!group || p.group === group) && (!q || (p.title + " " + p.desc).toLowerCase().includes(q.toLowerCase()))));
  const used = $derived(new Set(S.data.routines.map((r) => r.name)));

  function use(p: Preset) {
    addRoutine(presetToRoutine(p));
    toast(`Dodano „${p.title}”. Sprawdź ustawienia klocków i gotowe.`, "ok");
  }
  const icons = (p: Preset) => [...new Set([...p.r.triggers, ...p.r.actions].map((b) => SPEC[b.type]?.icon ?? "info"))].slice(0, 5);
</script>

<div class="tp">
  <div class="bar">
    <div class="search"><Icon name="search" size={13} /><input type="text" placeholder="Szukaj szablonów…" bind:value={q} /></div>
    <span class="muted small">{PRESETS.length} gotowych rutyn dla Windows – dodaj i dopasuj.</span>
  </div>
  <div class="groups">
    <button class="chip" class:on={!group} onclick={() => (group = "")}>Wszystkie</button>
    {#each GROUPS as g}<button class="chip" class:on={group === g} onclick={() => (group = g)}>{g}</button>{/each}
  </div>
  <div class="scroll">
    {#each GROUPS.filter((g) => list.some((p) => p.group === g)) as g}
      <h3>{g}</h3>
      <div class="grid">
        {#each list.filter((p) => p.group === g) as p (p.title)}
          <div class="card">
            <div class="ics">{#each icons(p) as i}<span><Icon name={i} size={13} /></span>{/each}</div>
            <b>{p.title}</b>
            <p>{p.desc}</p>
            <div class="foot">
              <span class="muted small">{p.r.triggers.length} wyzw. · {p.r.conditions?.length ?? 0} war. · {p.r.actions.length} akcji</span>
              <button class="btn sm" class:primary={!used.has(p.title)} onclick={() => use(p)}><Icon name={used.has(p.title) ? "copy" : "plus"} size={12} />{used.has(p.title) ? "Dodaj ponownie" : "Dodaj"}</button>
            </div>
          </div>
        {/each}
      </div>
    {:else}
      <div class="empty">Brak szablonów dla „{q}”.</div>
    {/each}
  </div>
</div>

<style>
  .tp {
    height: 100%;
    display: flex;
    flex-direction: column;
  }
  .bar {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 12px 16px 8px;
  }
  .search {
    width: 300px;
    display: flex;
    align-items: center;
    gap: 6px;
    height: 28px;
    padding: 0 10px;
    border: 1px solid var(--line-2);
    border-radius: 999px;
    color: var(--text-3);
  }
  .search input {
    flex: 1;
    border: 0;
    background: transparent;
    height: 26px;
    padding: 0;
    box-shadow: none !important;
  }
  .groups {
    display: flex;
    flex-wrap: wrap;
    gap: 5px;
    padding: 0 16px 10px;
    border-bottom: 1px solid var(--line);
  }
  .groups .chip {
    cursor: pointer;
    background: transparent;
    height: 23px;
  }
  .groups .chip.on {
    background: var(--sel-bg);
  }
  .scroll {
    flex: 1;
    overflow: auto;
    padding: 4px 16px 24px;
  }
  h3 {
    font-size: 11px;
    text-transform: uppercase;
    letter-spacing: 0.6px;
    color: var(--text-3);
    margin: 16px 0 8px;
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
    gap: 8px;
  }
  .card {
    display: flex;
    flex-direction: column;
    gap: 5px;
    padding: 11px 12px 10px;
    border: 1px solid var(--line);
    border-radius: 12px;
    background: var(--surface);
  }
  .card:hover {
    border-color: var(--line-2);
    background: var(--raised);
  }
  .ics {
    display: flex;
    gap: 4px;
    margin-bottom: 2px;
  }
  .ics span {
    width: 24px;
    height: 24px;
    border-radius: 7px;
    background: var(--raised-2);
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .ics span:first-child {
    background: var(--sel-bg);
    color: var(--sel-fg);
  }
  .card b {
    font-size: 13px;
  }
  .card p {
    margin: 0;
    flex: 1;
    font-size: 11.5px;
    line-height: 1.4;
    color: var(--text-2);
  }
  .foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 4px;
  }
</style>
