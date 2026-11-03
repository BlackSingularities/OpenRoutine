<script lang="ts">
  // Menu kontekstowe rutyny, podgląd szablonu przed dodaniem i ściągawka skrótów.
  import { save } from "@tauri-apps/plugin-dialog";
  import Icon from "./Icon.svelte";
  import { api } from "../lib/api";
  import { SPEC, summary, KIND_TITLE } from "../lib/catalog";
  import { block } from "../lib/api";
  import { presetToRoutine } from "../lib/presets";
  import { S, addRoutine, duplicate, removeRoutine, setEnabled, openRoutine, toast } from "../lib/store.svelte";

  const ctxR = $derived(S.ctx ? S.data.routines.find((r) => r.id === S.ctx!.id) : null);
  const pos = $derived(S.ctx ? { x: Math.min(S.ctx.x, window.innerWidth - 220), y: Math.min(S.ctx.y, window.innerHeight - 260) } : { x: 0, y: 0 });

  function act(f: () => void) {
    S.ctx = null;
    f();
  }
  async function exportOne() {
    const r = ctxR;
    if (!r) return;
    const p = await save({ defaultPath: `${r.name}.openroutine.json`, filters: [{ name: "OpenRoutine", extensions: ["json"] }] });
    if (!p) return;
    await api.writeText(p, JSON.stringify({ app: "OpenRoutine", version: 1, routines: [$state.snapshot(r)] }, null, 2));
    toast("Wyeksportowano rutynę.", "ok");
  }

  const pv = $derived(S.preview);
  const pvBlocks = $derived(
    pv
      ? ([["trigger", pv.r.triggers], ["condition", pv.r.conditions ?? []], ["action", pv.r.actions]] as const).map(([k, list]) => ({ k, list: list.map((b) => block(b.type, b.params)) }))
      : [],
  );

  const SHORTCUTS: [string, string][] = [
    ["Ctrl+K", "Paleta – szukaj rutyn, szablonów i poleceń"],
    ["Ctrl+N", "Nowa rutyna"],
    ["Ctrl+F", "Szukaj na liście"],
    ["Ctrl+1…7", "Przegląd, Rutyny, Szablony, Dziennik, Zmienne, Stan, Ustawienia"],
    ["Ctrl+,", "Ustawienia"],
    ["Ctrl+P", "Wstrzymaj / wznów wszystkie rutyny"],
    ["Ctrl+Enter", "Uruchom otwartą rutynę"],
    ["Ctrl+D", "Powiel otwartą rutynę"],
    ["Ctrl+E", "Włącz / wyłącz otwartą rutynę"],
    ["Delete", "Usuń otwartą rutynę (z możliwością cofnięcia)"],
    ["↑ / ↓", "Poprzednia / następna rutyna na liście"],
    ["F1", "Ta ściągawka"],
  ];
</script>

<svelte:window onkeydown={(e) => { if (e.key === "Escape") { S.ctx = null; S.preview = null; S.help = false; } }} onclick={() => (S.ctx = null)} onblur={() => (S.ctx = null)} />

{#if S.ctx && ctxR}
  <div class="menu" style="left:{pos.x}px; top:{pos.y}px" role="menu" tabindex="-1" onclick={(e) => e.stopPropagation()} onkeydown={() => {}}>
    <button onclick={() => act(() => openRoutine(ctxR.id))}><Icon name="edit" size={13} />Edytuj</button>
    <button onclick={() => act(() => api.run(ctxR.id, false))}><Icon name="play" size={13} />Uruchom</button>
    <button onclick={() => act(() => api.run(ctxR.id, true))}><Icon name="flask" size={13} />Wypróbuj (bez warunków)</button>
    <button onclick={() => act(() => setEnabled(ctxR, !ctxR.enabled))}><Icon name={ctxR.enabled ? "eyeOff" : "eye"} size={13} />{ctxR.enabled ? "Wyłącz" : "Włącz"}</button>
    <hr />
    <button onclick={() => act(() => duplicate(ctxR))}><Icon name="copy" size={13} />Powiel</button>
    <button onclick={() => act(exportOne)}><Icon name="upload" size={13} />Eksportuj…</button>
    <hr />
    <button class="danger" onclick={() => act(() => removeRoutine(ctxR.id))}><Icon name="trash" size={13} />Usuń</button>
  </div>
{/if}

{#if pv}
  <div class="scrim" role="presentation" onclick={() => (S.preview = null)}>
    <div class="dlg" role="dialog" aria-modal="true" tabindex="-1" onclick={(e) => e.stopPropagation()} onkeydown={() => {}}>
      <div class="dh">
        <span class="chip">{pv.group}</span>
        <button class="btn ghost sm icon" onclick={() => (S.preview = null)} aria-label="Zamknij"><Icon name="x" size={14} /></button>
      </div>
      <h2>{pv.title}</h2>
      <p class="desc">{pv.desc}</p>
      <div class="flow">
        {#each pvBlocks as g}
          {#if g.list.length}
            <div class="fg">
              <span class="tag">{KIND_TITLE[g.k].plural}</span>
              {#each g.list as b}
                <div class="fb"><Icon name={SPEC[b.type]?.icon ?? "info"} size={13} /><b>{SPEC[b.type]?.title}</b><span>{summary(b)}</span></div>
              {/each}
            </div>
          {/if}
        {/each}
      </div>
      <p class="muted small">Rutyna zostanie dodana jako włączona – możesz ją od razu dopasować w edytorze.</p>
      <div class="foot">
        <button class="btn" onclick={() => (S.preview = null)}>Anuluj</button>
        <button class="btn primary" onclick={() => { const p = pv; addRoutine(presetToRoutine(p)); S.preview = null; toast(`Dodano „${p.title}”.`, "ok"); }}><Icon name="plus" size={13} />Dodaj rutynę</button>
      </div>
    </div>
  </div>
{/if}

{#if S.help}
  <div class="scrim" role="presentation" onclick={() => (S.help = false)}>
    <div class="dlg sm" role="dialog" aria-modal="true" tabindex="-1" onclick={(e) => e.stopPropagation()} onkeydown={() => {}}>
      <div class="dh"><h2>Skróty klawiszowe</h2><button class="btn ghost sm icon" onclick={() => (S.help = false)} aria-label="Zamknij"><Icon name="x" size={14} /></button></div>
      <div class="keys">
        {#each SHORTCUTS as [k, d]}<kbd>{k}</kbd><span>{d}</span>{/each}
      </div>
      <p class="muted small">Globalne skróty dla rutyn ustawisz wyzwalaczem „Skrót klawiszowy” – działają w każdej aplikacji.</p>
    </div>
  </div>
{/if}

<style>
  .menu {
    position: fixed;
    z-index: 95;
    width: 210px;
    padding: 4px;
    background: var(--surface);
    border: 1px solid var(--line-2);
    border-radius: 10px;
    box-shadow: var(--shadow);
    animation: pop 0.1s ease-out;
  }
  .menu button {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 8px;
    height: 28px;
    padding: 0 9px;
    border: 0;
    border-radius: 6px;
    background: transparent;
    cursor: pointer;
    text-align: left;
    font-size: 12px;
  }
  .menu button:hover {
    background: var(--sel-bg);
    color: var(--sel-fg);
  }
  .menu .danger {
    color: var(--err);
  }
  .menu hr {
    border: 0;
    height: 1px;
    background: var(--line);
    margin: 4px 2px;
  }
  .scrim {
    position: fixed;
    inset: 0;
    z-index: 85;
    background: rgba(0, 0, 0, 0.45);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
  }
  .dlg {
    width: min(560px, 100%);
    max-height: 100%;
    overflow: auto;
    background: var(--surface);
    border: 1px solid var(--line-2);
    border-radius: 14px;
    box-shadow: var(--shadow);
    padding: 14px 16px;
    display: flex;
    flex-direction: column;
    gap: 8px;
    animation: pop 0.14s ease-out;
  }
  .dlg.sm {
    width: min(480px, 100%);
  }
  .dh {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  h2 {
    margin: 0;
    font-family: var(--font-display);
    font-size: 17px;
  }
  .desc {
    margin: 0;
    color: var(--text-2);
    line-height: 1.45;
  }
  .flow {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin: 4px 0;
  }
  .fg {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }
  .tag {
    align-self: flex-start;
    font-weight: 700;
    font-size: 11.5px;
    padding: 1px 9px;
    border-radius: 999px;
    background: var(--sel-bg);
    color: var(--sel-fg);
    margin-bottom: 2px;
  }
  .fb {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 5px 9px;
    border: 1px solid var(--line);
    border-radius: 8px;
    font-size: 12px;
    min-width: 0;
  }
  .fb span {
    color: var(--text-3);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .foot {
    display: flex;
    justify-content: flex-end;
    gap: 6px;
    margin-top: 4px;
  }
  .keys {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 6px 14px;
    align-items: center;
  }
  kbd {
    font-family: var(--mono);
    font-size: 11px;
    padding: 2px 7px;
    border: 1px solid var(--line-2);
    border-radius: 5px;
    justify-self: start;
  }
  .keys span {
    color: var(--text-2);
  }
  @keyframes pop {
    from {
      transform: scale(0.98);
      opacity: 0;
    }
  }
</style>
