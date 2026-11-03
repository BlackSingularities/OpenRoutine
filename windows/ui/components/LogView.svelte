<script lang="ts">
  import { save } from "@tauri-apps/plugin-dialog";
  import Icon from "./Icon.svelte";
  import { api } from "../lib/api";
  import { S, fmtTime, toast } from "../lib/store.svelte";

  let q = $state("");
  let only = $state<"all" | "ok" | "err">("all");
  const list = $derived(S.data.log.filter((l) => (only === "all" || (only === "ok") === l.ok) && (!q || (l.routine + " " + l.message).toLowerCase().includes(q.toLowerCase()))));
  const errs = $derived(S.data.log.filter((l) => !l.ok).length);

  async function exportLog() {
    const p = await save({ defaultPath: `OpenRoutine dziennik.csv`, filters: [{ name: "CSV", extensions: ["csv"] }] });
    if (!p) return;
    const rows = S.data.log.map((l) => [new Date(l.time).toISOString(), l.routine, l.ok ? "ok" : "błąd", l.message].map((x) => `"${String(x).replace(/"/g, '""')}"`).join(";"));
    await api.writeText(p, "﻿czas;rutyna;wynik;opis\n" + rows.join("\n"));
    toast("Zapisano dziennik.", "ok");
  }
  function open(name: string) {
    const r = S.data.routines.find((x) => x.name === name);
    if (r) {
      S.selected = r.id;
      S.tab = "routines";
    }
  }
</script>

<div class="lv">
  <div class="bar">
    <div class="search"><Icon name="search" size={13} /><input type="text" placeholder="Szukaj w dzienniku…" bind:value={q} /></div>
    <div class="seg">
      <button class:on={only === "all"} onclick={() => (only = "all")}>Wszystko {S.data.log.length}</button>
      <button class:on={only === "ok"} onclick={() => (only = "ok")}>Udane</button>
      <button class:on={only === "err"} onclick={() => (only = "err")}>Błędy {errs}</button>
    </div>
    <div class="grow"></div>
    <button class="btn sm" onclick={exportLog} disabled={!S.data.log.length}><Icon name="download" size={13} />CSV</button>
    <button class="btn ghost sm" onclick={() => api.clearLog()} disabled={!S.data.log.length}><Icon name="trash" size={13} />Wyczyść</button>
  </div>
  <div class="tbl selectable">
    {#each list as l, i (l.time + ":" + i)}
      <div class="row" class:bad={!l.ok}>
        <span class="st"><Icon name={l.ok ? "ok" : "alert"} size={13} /></span>
        <span class="t mono">{fmtTime(l.time)}</span>
        <button class="r" onclick={() => open(l.routine)}>{l.routine}</button>
        <span class="m">{l.message}</span>
      </div>
    {:else}
      <div class="empty"><Icon name="list" size={28} stroke={1.4} />Dziennik jest pusty – tu pojawią się wykonane rutyny i błędy.</div>
    {/each}
  </div>
</div>

<style>
  .lv {
    height: 100%;
    display: flex;
    flex-direction: column;
  }
  .bar {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 16px 10px;
    border-bottom: 1px solid var(--line);
  }
  .grow {
    flex: 1;
  }
  .search {
    width: 280px;
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
  .seg button {
    height: 22px;
    font-size: 11.5px;
  }
  .tbl {
    flex: 1;
    overflow: auto;
    padding: 4px 8px 20px;
  }
  .row {
    display: grid;
    grid-template-columns: 20px 132px 190px 1fr;
    gap: 8px;
    align-items: baseline;
    padding: 5px 8px;
    border-radius: 7px;
    font-size: 12px;
  }
  .row:nth-child(odd) {
    background: rgba(var(--fg-rgb), 0.025);
  }
  .st {
    color: var(--text-3);
    align-self: center;
  }
  .bad .st,
  .bad .m {
    color: var(--err);
  }
  .t {
    color: var(--text-3);
    font-size: 11px;
  }
  .r {
    border: 0;
    background: none;
    padding: 0;
    text-align: left;
    font-weight: 600;
    cursor: pointer;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .r:hover {
    text-decoration: underline;
  }
  .m {
    color: var(--text-2);
    word-break: break-word;
  }
</style>
