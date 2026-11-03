<script lang="ts">
  import Icon from "./Icon.svelte";
  import ParamField from "./ParamField.svelte";
  import { SPEC, summary } from "../lib/catalog";
  import type { Block } from "../lib/api";
  import { missing } from "../lib/schedule";

  let {
    block = $bindable(), index, count, selfId, onchange, onremove, onmove, onduplicate, expanded = $bindable(false), result = null, ondragstart, ondrop,
  }: {
    block: Block; index: number; count: number; selfId: string; onchange: () => void; onremove: () => void; onmove: (d: number) => void; onduplicate: () => void; expanded?: boolean;
    result?: boolean | null; ondragstart?: () => void; ondrop?: () => void;
  } = $props();
  const lacks = $derived(block.off ? [] : missing(block));
  let over = $state(false);

  const spec = $derived(SPEC[block.type]);
  const text = $derived(summary(block));
</script>

<div class="card" class:off={block.off} class:open={expanded} class:over role="listitem"
  ondragover={(e) => { if (ondrop) { e.preventDefault(); over = true; } }} ondragleave={() => (over = false)} ondrop={(e) => { e.preventDefault(); over = false; ondrop?.(); }}>
  <div class="head" role="button" tabindex="0" onclick={() => (expanded = !expanded)} onkeydown={(e) => e.key === "Enter" && (expanded = !expanded)}>
    <span class="grip" role="presentation" draggable="true" ondragstart={(e) => { e.dataTransfer?.setData("text/plain", block.id); ondragstart?.(); }} onclick={(e) => e.stopPropagation()} data-tip="Przeciągnij, aby zmienić kolejność">⋮⋮</span>
    <span class="ic"><Icon name={spec?.icon ?? "info"} size={15} /></span>
    <div class="tx">
      <b>{spec?.title ?? block.type}
        {#if lacks.length}<em class="lack" data-tip={"Uzupełnij: " + lacks.join(", ")}>uzupełnij</em>{/if}
        {#if result === true}<em class="res ok" data-tip="Warunek jest teraz spełniony">✓ spełniony</em>{:else if result === false}<em class="res no" data-tip="Warunek nie jest teraz spełniony">✗ niespełniony</em>{/if}
      </b>
      <span>{text}</span>
    </div>
    <div class="tools" role="presentation" onclick={(e) => e.stopPropagation()}>
      <button class="btn ghost sm icon" disabled={index === 0} data-tip="Wyżej" onclick={() => onmove(-1)}><Icon name="up" size={13} /></button>
      <button class="btn ghost sm icon" disabled={index === count - 1} data-tip="Niżej" onclick={() => onmove(1)}><Icon name="down" size={13} /></button>
      <button class="btn ghost sm icon" data-tip={block.off ? "Włącz klocek" : "Wyłącz klocek (bez usuwania)"} onclick={() => { block.off = !block.off; onchange(); }}><Icon name={block.off ? "eyeOff" : "eye"} size={13} /></button>
      <button class="btn ghost sm icon" data-tip="Powiel" onclick={onduplicate}><Icon name="copy" size={13} /></button>
      <button class="btn ghost sm icon" data-tip="Usuń" onclick={onremove}><Icon name="trash" size={13} /></button>
    </div>
  </div>
  {#if expanded}
    <div class="body">
      {#if spec?.desc}<p class="desc">{spec.desc}</p>{/if}
      {#if spec?.params?.length}
        <div class="grid">
          {#each spec.params as p (p.key)}
            <ParamField param={p} bind:value={block.params[p.key]} {onchange} {selfId} />
          {/each}
        </div>
      {/if}
      {#if spec?.provides?.length}
        <div class="prov"><span>Udostępnia:</span>{#each spec.provides as v}<code class="chip">{`{${v}}`}</code>{/each}</div>
      {/if}
    </div>
  {/if}
</div>

<style>
  .card {
    border: 1px solid var(--line);
    border-radius: 10px;
    background: var(--surface);
    transition: border-color 0.12s;
  }
  .card:hover,
  .card.open {
    border-color: var(--line-2);
  }
  .card.off {
    opacity: 0.5;
  }
  .card.over {
    border-color: var(--sel-bg);
  }
  .grip {
    cursor: grab;
    color: var(--text-3);
    font-size: 11px;
    letter-spacing: -2px;
    padding: 0 2px;
    opacity: 0;
    user-select: none;
  }
  .card:hover .grip {
    opacity: 1;
  }
  em {
    font-style: normal;
    font-size: 10px;
    font-weight: 600;
    margin-left: 6px;
    padding: 0 6px;
    border-radius: 999px;
    vertical-align: 1px;
  }
  .lack {
    color: var(--warn);
    border: 1px solid var(--warn);
  }
  .res.ok {
    background: var(--sel-bg);
    color: var(--sel-fg);
  }
  .res.no {
    color: var(--err);
    border: 1px solid var(--err);
  }
  .head {
    display: flex;
    align-items: center;
    gap: 9px;
    padding: 6px 6px 6px 4px;
    cursor: pointer;
    min-height: 40px;
  }
  .ic {
    width: 26px;
    height: 26px;
    flex: none;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--raised);
  }
  .open .ic {
    background: var(--sel-bg);
    color: var(--sel-fg);
  }
  .tx {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    line-height: 1.25;
  }
  .tx b {
    font-weight: 600;
    font-size: 12.5px;
  }
  .tx span {
    font-size: 11.5px;
    color: var(--text-2);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .tools {
    display: flex;
    gap: 1px;
    opacity: 0;
    transition: opacity 0.12s;
  }
  .card:hover .tools,
  .card.open .tools {
    opacity: 1;
  }
  .body {
    padding: 0 10px 10px;
    display: flex;
    flex-direction: column;
    gap: 9px;
  }
  .desc {
    margin: 0;
    font-size: 11.5px;
    color: var(--text-3);
    line-height: 1.4;
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
    gap: 8px 10px;
  }
  .prov {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    align-items: center;
    font-size: 11px;
    color: var(--text-3);
  }
  .prov code {
    font-family: var(--mono);
    user-select: text;
  }
</style>
