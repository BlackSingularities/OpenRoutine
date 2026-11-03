<script lang="ts">
  import Icon from "./Icon.svelte";
  import { api } from "../lib/api";
  import { BUILTIN_VARS } from "../lib/catalog";
  import { S, toast } from "../lib/store.svelte";

  let name = $state("");
  let value = $state("");
  let test = $state("Dziś jest {weekday}, {date_pl}, godz. {time}. Bateria: {battery}%.");
  let preview = $state("");
  const entries = $derived(Object.entries(S.data.vars).sort((a, b) => a[0].localeCompare(b[0], "pl")));

  async function add() {
    const n = name.trim().replace(/[{}\s]/g, "_");
    if (!n) return;
    await api.setVar(n, value);
    S.data.vars[n] = value;
    name = value = "";
  }
  async function upd(k: string, v: string) {
    S.data.vars[k] = v;
    await api.setVar(k, v);
  }
  async function del(k: string) {
    delete S.data.vars[k];
    await api.deleteVar(k);
  }
  $effect(() => {
    const t = test;
    const h = setTimeout(async () => (preview = await api.preview(t)), 200);
    return () => clearTimeout(h);
  });
</script>

<div class="vv">
  <div class="col">
    <h2>Zmienne globalne</h2>
    <p class="muted small">Zapamiętywane między uruchomieniami i po restarcie. Ustawiają je akcje „Ustaw zmienną”, „Zwiększ licznik”, „Oblicz” i inne z zasięgiem globalnym. Użycie w tekstach: <code>{"{nazwa}"}</code>.</p>
    <div class="add">
      <input type="text" placeholder="nazwa" bind:value={name} onkeydown={(e) => e.key === "Enter" && add()} />
      <input type="text" placeholder="wartość" bind:value={value} onkeydown={(e) => e.key === "Enter" && add()} />
      <button class="btn primary sm" onclick={add} disabled={!name.trim()}><Icon name="plus" size={13} />Dodaj</button>
    </div>
    <div class="list">
      {#each entries as [k, v] (k)}
        <div class="row">
          <code class="k selectable" role="button" tabindex="0" data-tip="Kopiuj" onclick={() => { navigator.clipboard.writeText(`{${k}}`); toast(`Skopiowano {${k}}`, "ok"); }} onkeydown={() => {}}>{`{${k}}`}</code>
          <input type="text" value={v} onchange={(e) => upd(k, e.currentTarget.value)} />
          <button class="btn ghost sm icon" data-tip="Usuń" onclick={() => del(k)}><Icon name="trash" size={13} /></button>
        </div>
      {:else}
        <div class="empty">Brak zmiennych.</div>
      {/each}
    </div>
  </div>
  <div class="col">
    <h2>Podgląd tekstu</h2>
    <textarea rows="3" bind:value={test}></textarea>
    <div class="pv selectable">{preview}</div>
    <h2>Wbudowane</h2>
    <div class="bi selectable">
      {#each BUILTIN_VARS as [k, d]}<div><code>{`{${k}}`}</code><span>{d}</span></div>{/each}
    </div>
  </div>
</div>

<style>
  .vv {
    height: 100%;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0;
  }
  .col {
    padding: 14px 18px;
    overflow: auto;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  h2 {
    margin: 6px 0 0;
    font-size: 13px;
    font-family: var(--font-display);
  }
  p {
    margin: 0;
    line-height: 1.45;
  }
  code {
    font-family: var(--mono);
    font-size: 11px;
  }
  .add {
    display: grid;
    grid-template-columns: 160px 1fr auto;
    gap: 6px;
  }
  .list {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }
  .row {
    display: grid;
    grid-template-columns: 160px 1fr auto;
    gap: 6px;
    align-items: center;
  }
  .k {
    cursor: pointer;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .pv {
    padding: 8px 10px;
    border: 1px solid var(--line);
    border-radius: 10px;
    min-height: 34px;
    white-space: pre-wrap;
    font-size: 12px;
  }
  .bi {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 3px 12px;
  }
  .bi div {
    display: flex;
    gap: 8px;
    align-items: baseline;
    font-size: 11.5px;
  }
  .bi span {
    color: var(--text-3);
  }
</style>
