<script lang="ts">
  import { open } from "@tauri-apps/plugin-dialog";
  import Icon from "./Icon.svelte";
  import Switch from "./Switch.svelte";
  import { DAY_NAMES, type Param } from "../lib/catalog";
  import { S, loadPickers } from "../lib/store.svelte";

  let { param, value = $bindable(), onchange, selfId = "" }: { param: Param; value: string; onchange: () => void; selfId?: string } = $props();
  const t = $derived(param.type ?? "text");
  const uid = Math.random().toString(36).slice(2);

  function set(v: string) {
    value = v;
    onchange();
  }

  async function browse(dir: boolean) {
    const r = await open({ directory: dir, multiple: false });
    if (typeof r === "string") set(r);
  }

  function toggleDay(d: number) {
    const cur = new Set((value || "").split(""));
    const k = String(d);
    cur.has(k) ? cur.delete(k) : cur.add(k);
    set([...cur].filter((c) => /[1-7]/.test(c)).sort().join(""));
  }

  let recording = $state(false);
  function onKey(e: KeyboardEvent) {
    if (!recording) return;
    e.preventDefault();
    const k = e.key;
    if (["Control", "Alt", "Shift", "Meta"].includes(k)) return;
    const map: Record<string, string> = { " ": "space", ArrowUp: "up", ArrowDown: "down", ArrowLeft: "left", ArrowRight: "right", Escape: "esc", PageUp: "pgup", PageDown: "pgdn", Delete: "del", Insert: "ins" };
    let key = map[k] ?? (k.length === 1 ? k.toLowerCase() : k.toLowerCase());
    if (/^Digit\d$/.test(e.code)) key = e.code.slice(5);
    else if (/^Key[A-Z]$/.test(e.code)) key = e.code.slice(3).toLowerCase();
    const mods = [e.ctrlKey && "ctrl", e.altKey && "alt", e.shiftKey && "shift", e.metaKey && "win"].filter(Boolean);
    if (!mods.length) return;
    set([...mods, key].join("+"));
    recording = false;
  }

  const dtValue = $derived((value || "").replace(" ", "T"));
  const listId = $derived(t === "app" ? "dl-procs" : t === "window" ? "dl-wins" : t === "var" ? "dl-vars" : undefined);
  const hotkeyBad = $derived(t === "keys" && S.failedHotkeys.includes(value?.toLowerCase()));
</script>

<div class="f" class:wide={t === "multiline" || t === "days" || t === "folder" || t === "file"} class:sw={t === "bool"}>
  {#if t !== "bool"}<span class="lbl">{param.label}{#if param.optional}<i> · opcjonalnie</i>{/if}</span>{/if}
  {#if t === "multiline"}
    <textarea rows="2" {value} placeholder={param.hint} oninput={(e) => set(e.currentTarget.value)}></textarea>
  {:else if t === "number"}
    <input type="number" {value} oninput={(e) => set(e.currentTarget.value)} />
  {:else if t === "time"}
    <input type="time" {value} oninput={(e) => set(e.currentTarget.value)} />
  {:else if t === "datetime"}
    <input type="datetime-local" value={dtValue} oninput={(e) => set(e.currentTarget.value.replace("T", " "))} />
  {:else if t === "bool"}
    <Switch label={param.label} checked={value === "1"} onchange={(v) => set(v ? "1" : "0")} />
  {:else if t === "choice"}
    {#if (param.options?.length ?? 0) <= 3 && param.options!.every((o) => o[1].length < 16)}
      <div class="seg">
        {#each param.options! as o}<button type="button" class:on={value === o[0]} onclick={() => set(o[0])}>{o[1]}</button>{/each}
      </div>
    {:else}
      <select {value} onchange={(e) => set(e.currentTarget.value)}>
        {#each param.options! as o}<option value={o[0]}>{o[1]}</option>{/each}
      </select>
    {/if}
  {:else if t === "days"}
    <div class="days">
      {#each DAY_NAMES as d, i}<button type="button" class="chip" class:on={(value || "").includes(String(i + 1))} onclick={() => toggleDay(i + 1)}>{d}</button>{/each}
      <span class="sep"></span>
      <button type="button" class="btn ghost sm" onclick={() => set("12345")}>Robocze</button>
      <button type="button" class="btn ghost sm" onclick={() => set("67")}>Weekend</button>
      <button type="button" class="btn ghost sm" onclick={() => set("1234567")}>Codziennie</button>
    </div>
  {:else if t === "routine"}
    <select {value} onchange={(e) => set(e.currentTarget.value)}>
      <option value="">{param.optional ? "(dowolna)" : "(wybierz)"}</option>
      {#each S.data.routines.filter((r) => r.id !== selfId) as r}<option value={r.id}>{r.name}</option>{/each}
    </select>
  {:else if t === "keys"}
    <div class="row">
      <input type="text" class:rec={recording} {value} readonly={recording} placeholder="ctrl+alt+n" oninput={(e) => set(e.currentTarget.value)} onkeydown={onKey} onblur={() => (recording = false)} />
      <button type="button" class="btn sm" class:active={recording} onclick={(e) => { recording = !recording; (e.currentTarget.previousElementSibling as HTMLInputElement)?.focus(); }}>
        <Icon name="keyboard" size={13} />{recording ? "Naciśnij skrót…" : "Nagraj"}
      </button>
    </div>
    {#if hotkeyBad}<span class="warn">Ten skrót jest zajęty przez inną aplikację lub system.</span>{/if}
  {:else if t === "folder" || t === "file"}
    <div class="row">
      <input type="text" {value} placeholder={param.hint ?? (t === "folder" ? "C:\\Users\\…" : "")} oninput={(e) => set(e.currentTarget.value)} />
      <button type="button" class="btn sm icon" data-tip={t === "folder" ? "Wybierz folder" : "Wybierz plik"} onclick={() => browse(t === "folder")}><Icon name={t === "folder" ? "folder" : "file"} size={14} /></button>
    </div>
  {:else}
    <input type="text" {value} placeholder={param.hint ?? ""} list={listId} onfocus={() => (t === "app" || t === "window") && loadPickers()} oninput={(e) => set(e.currentTarget.value)} />
  {/if}
</div>

{#if t === "app"}
  <datalist id="dl-procs">{#each S.processes as p}<option value={p.replace(/\.exe$/, "")}></option>{/each}</datalist>
{:else if t === "window"}
  <datalist id="dl-wins">{#each S.windows as w}<option value={w}></option>{/each}</datalist>
{:else if t === "var"}
  <datalist id="dl-vars">{#each Object.keys(S.data.vars) as v}<option value={v}></option>{/each}</datalist>
{/if}

<style>
  .f {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
  }
  .f.wide {
    grid-column: 1 / -1;
  }
  .f.sw {
    justify-content: flex-end;
  }
  .lbl {
    font-size: 11px;
    color: var(--text-2);
  }
  .lbl i {
    font-style: normal;
    color: var(--text-3);
  }
  .f > input,
  .f > select {
    width: 100%;
  }
  .row {
    display: flex;
    gap: 6px;
  }
  .row input {
    flex: 1;
  }
  .days {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    align-items: center;
  }
  .days .chip {
    cursor: pointer;
    background: transparent;
    min-width: 30px;
    justify-content: center;
    height: 24px;
  }
  .days .chip.on {
    background: var(--sel-bg);
  }
  .sep {
    flex: 1;
  }
  .seg {
    align-self: flex-start;
  }
  .seg button {
    height: 22px;
    font-size: 11.5px;
  }
  input.rec {
    border-color: var(--sel-bg);
    box-shadow: 0 0 0 3px rgba(var(--fg-rgb), 0.16);
  }
  .warn {
    color: var(--warn);
    font-size: 11px;
  }
</style>
