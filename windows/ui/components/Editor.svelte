<script lang="ts">
  import { save } from "@tauri-apps/plugin-dialog";
  import Icon from "./Icon.svelte";
  import Switch from "./Switch.svelte";
  import BlockCard from "./BlockCard.svelte";
  import BlockPicker from "./BlockPicker.svelte";
  import { KIND_TITLE, BUILTIN_VARS, SPEC, type Kind } from "../lib/catalog";
  import { api, block, newId, type Block, type Routine } from "../lib/api";
  import { S, saveRoutine, duplicate, removeRoutine, toast, fmtAgo } from "../lib/store.svelte";
  import { nextRun, fmtNext, routineIssues } from "../lib/schedule";

  let { routine: r }: { routine: Routine } = $props();
  let picker = $state<Kind | null>(null);
  let open = $state<Record<string, boolean>>({});
  let results = $state<Record<string, boolean>>({});
  let testing = $state(false);
  let drag = $state<{ key: string; i: number } | null>(null);
  const loc = $derived<[number, number]>([S.data.settings.latitude ?? 52.23, S.data.settings.longitude ?? 21.01]);
  const next = $derived(nextRun(r, loc));
  const issues = $derived(routineIssues(r));

  async function testConds() {
    testing = true;
    saveRoutine(r, true);
    await new Promise((res) => setTimeout(res, 200));
    const res = await api.testConditions(r.id);
    results = Object.fromEntries(r.conditions.map((c, i) => [c.id, res[i]]));
    testing = false;
    const all = r.matchAll ? res.every(Boolean) : res.some(Boolean);
    toast(all ? "Warunki są teraz spełnione – rutyna by się wykonała." : "Warunki nie są teraz spełnione.", all ? "ok" : "info");
    setTimeout(() => (results = {}), 15000);
  }
  function dropAt(key: "triggers" | "conditions" | "actions", to: number) {
    if (!drag || drag.key !== key || drag.i === to) return (drag = null);
    const a = r[key];
    const [it] = a.splice(drag.i, 1);
    a.splice(to, 0, it);
    drag = null;
    changed();
  }
  function setAll(v: boolean) {
    for (const b of [...r.triggers, ...r.conditions, ...r.actions]) open[b.id] = v;
  }
  let showVars = $state(false);

  const lists: { kind: Kind; key: "triggers" | "conditions" | "actions" }[] = [
    { kind: "trigger", key: "triggers" },
    { kind: "condition", key: "conditions" },
    { kind: "action", key: "actions" },
  ];

  const changed = () => saveRoutine(r);
  const running = $derived(S.running.includes(r.id));
  const groups = $derived([...new Set(S.data.routines.map((x) => x.group).filter(Boolean))]);
  const provided = $derived([...new Set(r.triggers.flatMap((t) => SPEC[t.type]?.provides ?? []).concat(r.actions.flatMap((a) => SPEC[a.type]?.provides ?? [])))]);

  function add(kind: Kind, id: string) {
    const key = lists.find((l) => l.kind === kind)!.key;
    const b = block(id);
    r[key].push(b);
    open[b.id] = true;
    picker = null;
    changed();
  }
  function remove(key: "triggers" | "conditions" | "actions", i: number) {
    r[key].splice(i, 1);
    changed();
  }
  function move(key: "triggers" | "conditions" | "actions", i: number, d: number) {
    const a = r[key];
    const j = i + d;
    if (j < 0 || j >= a.length) return;
    [a[i], a[j]] = [a[j], a[i]];
    changed();
  }
  function dup(key: "triggers" | "conditions" | "actions", i: number) {
    const src = $state.snapshot(r[key][i]) as Block;
    r[key].splice(i + 1, 0, { ...src, id: newId() });
    changed();
  }

  async function exportOne() {
    const p = await save({ defaultPath: `${r.name}.openroutine.json`, filters: [{ name: "OpenRoutine", extensions: ["json"] }] });
    if (!p) return;
    await api.writeText(p, JSON.stringify({ app: "OpenRoutine", version: 1, routines: [$state.snapshot(r)] }, null, 2));
    toast("Wyeksportowano rutynę.", "ok");
  }

  function cliLine() {
    navigator.clipboard.writeText(`"%ProgramFiles%\\OpenRoutine\\OpenRoutine.exe" --run "${r.name}"`);
    toast("Skopiowano polecenie – możesz je wkleić do skrótu lub Harmonogramu zadań.", "ok");
  }
</script>

<div class="ed">
  <div class="top">
    <Switch label="" checked={r.enabled} onchange={(v) => { r.enabled = v; saveRoutine(r, true); }} tip={r.enabled ? "Rutyna włączona" : "Rutyna wyłączona"} />
    <input class="name" type="text" bind:value={r.name} oninput={changed} placeholder="Nazwa rutyny" />
    <div class="grow"></div>
    {#if running}
      <button class="btn sm" onclick={() => api.stop(r.id)}><Icon name="stop" size={13} />Zatrzymaj</button>
    {:else}
      <button class="btn sm" data-tip="Wykonuje akcje od razu, bez sprawdzania warunków" onclick={() => api.run(r.id, true)}><Icon name="flask" size={13} />Wypróbuj</button>
      <button class="btn primary sm" data-tip="Uruchamia rutynę ze sprawdzeniem warunków" onclick={() => api.run(r.id, false)}><Icon name="play" size={13} />Uruchom</button>
    {/if}
    <button class="btn ghost sm icon" data-tip="Powiel rutynę" onclick={() => duplicate(r)}><Icon name="copy" size={14} /></button>
    <button class="btn ghost sm icon" data-tip="Eksportuj do pliku" onclick={exportOne}><Icon name="upload" size={14} /></button>
    <button class="btn ghost sm icon" data-tip="Skopiuj polecenie wiersza poleceń" onclick={cliLine}><Icon name="terminal" size={14} /></button>
    <button class="btn ghost sm icon" data-tip="Usuń rutynę (Delete, można cofnąć)" onclick={() => removeRoutine(r.id)}><Icon name="trash" size={14} /></button>
  </div>
  <div class="meta">
    <span>Uruchomień: <b>{r.runs}</b></span>
    <span>Ostatnio: <b>{fmtAgo(r.lastRun)}</b>{#if r.lastRun && !r.lastOk}<span class="chip err">błąd</span>{/if}</span>
    {#if next}<span>Następnie: <b>{fmtNext(next)}</b></span>{/if}
    {#if issues}<span class="iss"><Icon name="info" size={12} />{r.actions.length ? `${issues} do uzupełnienia` : "brak akcji"}</span>{/if}
    <button class="lnk" onclick={() => setAll(true)}>rozwiń</button><button class="lnk" onclick={() => setAll(false)}>zwiń</button>
    <label class="grp"><Icon name="template" size={12} /><input type="text" list="dl-groups" placeholder="Grupa" bind:value={r.group} oninput={changed} /></label>
    <datalist id="dl-groups">{#each groups as g}<option value={g}></option>{/each}</datalist>
  </div>

  <div class="scroll">
    {#each lists as l}
      <section>
        <header>
          <span class="tag">{KIND_TITLE[l.kind].plural}</span>
          <span class="hint">{KIND_TITLE[l.kind].hint}</span>
          {#if l.kind === "condition" && r.conditions.length > 1}
            <div class="seg">
              <button class:on={r.matchAll} onclick={() => { r.matchAll = true; changed(); }}>wszystkie</button>
              <button class:on={!r.matchAll} onclick={() => { r.matchAll = false; changed(); }}>dowolny</button>
            </div>
          {/if}
          <div class="grow"></div>
          {#if l.kind === "condition" && r.conditions.length}
            <button class="btn ghost sm" disabled={testing} data-tip="Sprawdza każdy warunek w tej chwili" onclick={testConds}><Icon name="flask" size={13} />{testing ? "Sprawdzam…" : "Sprawdź teraz"}</button>
          {/if}
          <button class="btn sm" onclick={() => (picker = l.kind)}><Icon name="plus" size={13} />Dodaj</button>
        </header>
        <div class="blocks">
          {#each r[l.key] as b, i (b.id)}
            <BlockCard bind:block={r[l.key][i]} index={i} count={r[l.key].length} selfId={r.id} bind:expanded={open[b.id]} onchange={changed} onremove={() => remove(l.key, i)} onmove={(d) => move(l.key, i, d)} onduplicate={() => dup(l.key, i)} result={results[b.id] ?? null} ondragstart={() => (drag = { key: l.key, i })} ondrop={() => dropAt(l.key, i)} />
          {:else}
            <button class="placeholder" onclick={() => (picker = l.kind)}>
              <Icon name="plus" size={14} />
              {l.kind === "trigger" ? "Dodaj wyzwalacz – bez niego rutyna działa tylko po kliknięciu „Uruchom”" : l.kind === "condition" ? "Brak warunków – rutyna wykona się zawsze" : "Dodaj pierwszą akcję"}
            </button>
          {/each}
        </div>
      </section>
    {/each}

    <section class="opts">
      <header><span class="tag">Opcje</span></header>
      <div class="og">
        <label class="f"><span>Nie częściej niż co (s)</span><input type="number" min="0" bind:value={r.cooldownSec} oninput={changed} /></label>
        <Switch label="Przerwij po pierwszym błędzie" checked={r.stopOnError} onchange={(v) => { r.stopOnError = v; changed(); }} />
        <Switch label="Nie zapisuj udanych uruchomień w dzienniku" checked={r.quiet} onchange={(v) => { r.quiet = v; changed(); }} />
      </div>
      <textarea placeholder="Notatki…" bind:value={r.notes} oninput={changed}></textarea>
    </section>

    <section class="vars">
      <button class="vh" onclick={() => (showVars = !showVars)}><Icon name="variable" size={13} />Zmienne do użycia w polach tekstowych<Icon name={showVars ? "up" : "down"} size={13} /></button>
      {#if showVars}
        <div class="vl selectable">
          {#each provided as v}<code class="chip on">{`{${v}}`}</code>{/each}
          {#each Object.keys(S.data.vars) as v}<code class="chip" title={S.data.vars[v]}>{`{${v}}`}</code>{/each}
          {#each BUILTIN_VARS as [v, d]}<code class="chip" title={d}>{`{${v}}`}</code>{/each}
          <span class="muted small">Działają też zmienne środowiskowe, np. %USERPROFILE%.</span>
        </div>
      {/if}
    </section>
  </div>
</div>

{#if picker}<BlockPicker kind={picker} onpick={(id) => add(picker!, id)} onclose={() => (picker = null)} />{/if}

<style>
  .ed {
    height: 100%;
    display: flex;
    flex-direction: column;
    min-width: 0;
  }
  .top {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 12px 4px;
  }
  .top :global(.sw) {
    width: auto;
  }
  .name {
    flex: 1;
    max-width: 420px;
    border: 1px solid transparent !important;
    background: transparent !important;
    font-family: var(--font-display);
    font-size: 16px;
    font-weight: 650;
    height: 32px !important;
    padding: 0 6px !important;
    border-radius: 8px !important;
  }
  .name:hover {
    border-color: var(--line) !important;
  }
  .name:focus {
    border-color: var(--line-2) !important;
    box-shadow: none !important;
  }
  .grow {
    flex: 1;
  }
  .iss {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    color: var(--warn);
  }
  .lnk {
    border: 0;
    background: none;
    padding: 0;
    color: var(--text-3);
    cursor: pointer;
    font-size: 11px;
    text-decoration: underline;
    text-underline-offset: 2px;
  }
  .lnk:hover {
    color: var(--text);
  }
  .meta {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 0 18px 8px;
    font-size: 11.5px;
    color: var(--text-3);
    border-bottom: 1px solid var(--line);
  }
  .meta b {
    color: var(--text-2);
    font-weight: 600;
  }
  .meta .chip {
    margin-left: 6px;
    height: 17px;
  }
  .grp {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 5px;
  }
  .grp input {
    height: 22px;
    width: 150px;
    font-size: 11.5px;
  }
  .scroll {
    flex: 1;
    overflow: auto;
    padding: 10px 12px 24px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }
  section header {
    display: flex;
    align-items: center;
    gap: 9px;
    margin-bottom: 6px;
    min-height: 26px;
  }
  .tag {
    font-family: var(--font-display);
    font-weight: 700;
    font-size: 13px;
    padding: 2px 10px;
    border-radius: 999px;
    background: var(--sel-bg);
    color: var(--sel-fg);
  }
  .hint {
    font-size: 11.5px;
    color: var(--text-3);
  }
  .seg button {
    height: 20px;
    font-size: 11px;
    padding: 0 8px;
  }
  .blocks {
    display: flex;
    flex-direction: column;
    gap: 5px;
  }
  .placeholder {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border: 1px dashed var(--line-2);
    border-radius: 10px;
    background: transparent;
    color: var(--text-3);
    cursor: pointer;
    font-size: 12px;
    text-align: left;
  }
  .placeholder:hover {
    color: var(--text);
    border-color: rgba(var(--fg-rgb), 0.45);
  }
  .opts .og {
    display: grid;
    grid-template-columns: 200px 1fr 1fr;
    gap: 10px 16px;
    align-items: end;
    margin-bottom: 8px;
  }
  .f {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 11px;
    color: var(--text-2);
  }
  .f input {
    width: 100%;
  }
  .vh {
    display: flex;
    align-items: center;
    gap: 7px;
    border: 0;
    background: none;
    color: var(--text-3);
    cursor: pointer;
    font-size: 11.5px;
    padding: 0;
  }
  .vh:hover {
    color: var(--text);
  }
  .vl {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    margin-top: 8px;
  }
  .vl code {
    font-family: var(--mono);
    font-size: 10.5px;
  }
</style>
