<script lang="ts">
  import Icon from "./Icon.svelte";
  import { CATS, SPECS, KIND_TITLE, type Kind } from "../lib/catalog";

  let { kind, onpick, onclose }: { kind: Kind; onpick: (id: string) => void; onclose: () => void } = $props();
  let q = $state("");
  let cat = $state("");
  let input: HTMLInputElement;

  const all = $derived(SPECS.filter((s) => s.kind === kind));
  const cats = $derived(Object.keys(CATS).filter((c) => all.some((s) => s.cat === c)));
  const list = $derived(
    all.filter((s) => (!cat || s.cat === cat) && (!q || (s.title + " " + s.desc).toLowerCase().includes(q.toLowerCase()))),
  );
  $effect(() => input?.focus());
</script>

<svelte:window onkeydown={(e) => e.key === "Escape" && onclose()} />

<div class="scrim" role="presentation" onclick={onclose}>
  <div class="dlg" role="dialog" aria-modal="true" tabindex="-1" onclick={(e) => e.stopPropagation()} onkeydown={() => {}}>
    <div class="top">
      <b>Dodaj: {KIND_TITLE[kind].one.toLowerCase()}</b>
      <div class="search"><Icon name="search" size={14} /><input bind:this={input} type="text" placeholder="Szukaj…" bind:value={q} onkeydown={(e) => e.key === "Enter" && list[0] && onpick(list[0].id)} /></div>
      <button class="btn ghost sm icon" onclick={onclose} aria-label="Zamknij"><Icon name="x" size={14} /></button>
    </div>
    <div class="main">
      <nav>
        <button class:on={!cat} onclick={() => (cat = "")}><Icon name="list" size={14} />Wszystkie<span>{all.length}</span></button>
        {#each cats as c}
          <button class:on={cat === c} onclick={() => (cat = c)}><Icon name={CATS[c].icon} size={14} />{CATS[c].title}<span>{all.filter((s) => s.cat === c).length}</span></button>
        {/each}
      </nav>
      <div class="items">
        {#each list as s (s.id)}
          <button class="it" onclick={() => onpick(s.id)}>
            <span class="ic"><Icon name={s.icon} size={15} /></span>
            <span class="tx"><b>{s.title}</b><span>{s.desc}</span></span>
          </button>
        {:else}
          <div class="empty">Nic nie znaleziono.</div>
        {/each}
      </div>
    </div>
  </div>
</div>

<style>
  .scrim {
    position: fixed;
    inset: 0;
    z-index: 80;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
  }
  .dlg {
    width: min(820px, 100%);
    height: min(560px, 100%);
    display: flex;
    flex-direction: column;
    background: var(--surface);
    border: 1px solid var(--line-2);
    border-radius: 14px;
    box-shadow: var(--shadow);
    overflow: hidden;
    animation: pop 0.14s ease-out;
  }
  .top {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 9px 10px 9px 14px;
    border-bottom: 1px solid var(--line);
  }
  .top b {
    font-size: 13px;
    white-space: nowrap;
  }
  .search {
    flex: 1;
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
  .main {
    flex: 1;
    min-height: 0;
    display: grid;
    grid-template-columns: 200px 1fr;
  }
  nav {
    padding: 6px;
    display: flex;
    flex-direction: column;
    gap: 1px;
    overflow: auto;
    border-right: 1px solid var(--line);
  }
  nav button {
    display: flex;
    align-items: center;
    gap: 8px;
    height: 28px;
    padding: 0 8px;
    border: 0;
    border-radius: 7px;
    background: transparent;
    color: var(--text-2);
    cursor: pointer;
    text-align: left;
    font-size: 12px;
  }
  nav button span {
    margin-left: auto;
    font-size: 10.5px;
    color: var(--text-3);
  }
  nav button:hover {
    background: var(--raised);
    color: var(--text);
  }
  nav button.on {
    background: var(--sel-bg);
    color: var(--sel-fg);
    font-weight: 600;
  }
  nav button.on span {
    color: inherit;
    opacity: 0.7;
  }
  .items {
    overflow: auto;
    padding: 6px;
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
    gap: 4px;
    align-content: start;
  }
  .it {
    display: flex;
    gap: 9px;
    align-items: flex-start;
    padding: 8px;
    border: 1px solid transparent;
    border-radius: 9px;
    background: transparent;
    cursor: pointer;
    text-align: left;
  }
  .it:hover {
    background: var(--raised);
    border-color: var(--line);
  }
  .ic {
    width: 28px;
    height: 28px;
    flex: none;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--raised-2);
  }
  .it:hover .ic {
    background: var(--sel-bg);
    color: var(--sel-fg);
  }
  .tx {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }
  .tx b {
    font-size: 12.5px;
    font-weight: 600;
  }
  .tx span {
    font-size: 11px;
    color: var(--text-3);
    line-height: 1.35;
  }
  @keyframes pop {
    from {
      transform: scale(0.98);
      opacity: 0;
    }
  }
</style>
