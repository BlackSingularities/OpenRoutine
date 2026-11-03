<script lang="ts">
  import Icon from "./Icon.svelte";
  import { api } from "../lib/api";
  import { SPEC } from "../lib/catalog";
  import { S, openRoutine, addRoutine, fmtAgo } from "../lib/store.svelte";
  import { nextRun, fmtNext, sunTimes, fmtMin, routineIssues } from "../lib/schedule";
  import { PRESETS, presetToRoutine } from "../lib/presets";

  let now = $state(Date.now());
  $effect(() => {
    const t = setInterval(() => (now = Date.now()), 30000);
    return () => clearInterval(t);
  });

  const loc = $derived<[number, number]>([S.data.settings.latitude ?? 52.23, S.data.settings.longitude ?? 21.01]);
  const startOfDay = $derived(new Date(new Date(now).setHours(0, 0, 0, 0)).getTime());
  const today = $derived(S.data.log.filter((l) => l.time >= startOfDay));
  const okToday = $derived(today.filter((l) => l.ok).length);
  const errToday = $derived(today.filter((l) => !l.ok).length);
  const enabled = $derived(S.data.routines.filter((r) => r.enabled));
  const upcoming = $derived(
    S.data.routines
      .map((r) => ({ r, t: nextRun(r, loc, new Date(now)) }))
      .filter((x): x is { r: (typeof x)["r"]; t: number } => x.t != null)
      .sort((a, b) => a.t - b.t)
      .slice(0, 7),
  );
  const manual = $derived(S.data.routines.filter((r) => r.triggers.some((t) => t.type === "t.manual" || t.type === "t.hotkey") && r.enabled).slice(0, 8));
  const issues = $derived(S.data.routines.filter((r) => r.enabled && routineIssues(r) > 0));
  const watching = $derived.by(() => {
    const m = new Map<string, number>();
    for (const r of enabled) for (const t of r.triggers) if (!t.off) m.set(t.type, (m.get(t.type) ?? 0) + 1);
    return [...m.entries()].sort((a, b) => b[1] - a[1]).slice(0, 10);
  });
  const sun = $derived(sunTimes(new Date(now), loc[0], loc[1]));
  const hour = $derived(new Date(now).getHours());
  const hello = $derived(hour < 5 ? "Dobranoc" : hour < 12 ? "Dzień dobry" : hour < 18 ? "Miłego popołudnia" : "Dobry wieczór");
  const starters = PRESETS.filter((p) => ["Porządek w Pobranych", "Przerwa dla oczu (20-20-20)", "Słaba bateria", "Zablokuj, gdy odchodzę", "Ciemny motyw wieczorem", "Sprzątanie w niedzielę"].includes(p.title));
  const hotkeyOf = (r: (typeof S.data.routines)[number]) => r.triggers.find((t) => t.type === "t.hotkey")?.params.keys;
</script>

<div class="ov">
  <div class="hero">
    <div>
      <h1>{hello}</h1>
      <p>
        {#if !S.data.settings.engineOn}<span class="warn">Rutyny są wstrzymane.</span>
        {:else if !S.data.routines.length}Zacznij od gotowego szablonu albo utwórz własną rutynę.
        {:else}Czuwa {enabled.length} {enabled.length === 1 ? "rutyna" : enabled.length < 5 && enabled.length > 1 ? "rutyny" : "rutyn"}{upcoming[0] ? ` · najbliższa: ${upcoming[0].r.name}, ${fmtNext(upcoming[0].t, now)}` : ""}.{/if}
      </p>
    </div>
    <div class="grow"></div>
    {#if sun}<div class="sun" data-tip="Dla lokalizacji z Ustawień"><Icon name="sun" size={13} />{fmtMin(sun[0])}<Icon name="moon" size={13} />{fmtMin(sun[1])}</div>{/if}
  </div>

  <div class="kpis">
    <div class="kpi"><span>Włączone</span><b>{enabled.length}<i>/{S.data.routines.length}</i></b></div>
    <div class="kpi"><span>Wykonania dziś</span><b>{okToday}</b></div>
    <div class="kpi" class:bad={errToday > 0}><span>Błędy dziś</span><b>{errToday}</b></div>
    <div class="kpi"><span>W toku</span><b>{S.running.length}</b></div>
    <div class="kpi"><span>Zmienne</span><b>{Object.keys(S.data.vars).length}</b></div>
  </div>

  <div class="grid">
    <section>
      <h3><Icon name="clock" size={13} />Najbliższe uruchomienia</h3>
      {#each upcoming as u (u.r.id)}
        <button class="row" onclick={() => openRoutine(u.r.id)}>
          <span class="when">{fmtNext(u.t, now)}</span><span class="nm">{u.r.name}</span><Icon name="right" size={12} />
        </button>
      {:else}
        <p class="none">Brak rutyn z wyzwalaczem czasowym.</p>
      {/each}
    </section>

    <section>
      <h3><Icon name="list" size={13} />Ostatnie zdarzenia</h3>
      {#each S.data.log.slice(0, 7) as l, i (l.time + ":" + i)}
        <button class="row" class:err={!l.ok} onclick={() => (S.tab = "log")}>
          <Icon name={l.ok ? "ok" : "alert"} size={12} /><span class="nm">{l.routine}</span><span class="when">{fmtAgo(l.time)}</span>
        </button>
      {:else}
        <p class="none">Jeszcze nic się nie wydarzyło.</p>
      {/each}
    </section>

    <section>
      <h3><Icon name="play" size={13} />Szybkie uruchamianie</h3>
      <div class="quick">
        {#each manual as r (r.id)}
          <button class="q" class:busy={S.running.includes(r.id)} onclick={() => api.run(r.id, false)} oncontextmenu={(e) => { e.preventDefault(); openRoutine(r.id); }}>
            <Icon name={SPEC[r.actions[0]?.type]?.icon ?? "play"} size={15} />
            <span>{r.name}</span>
            {#if hotkeyOf(r)}<kbd>{hotkeyOf(r)}</kbd>{/if}
          </button>
        {:else}
          <p class="none">Rutyny z wyzwalaczem „Ręcznie” lub skrótem klawiszowym pojawią się tutaj.</p>
        {/each}
      </div>
    </section>

    <section>
      <h3><Icon name="eye" size={13} />Co obserwuję</h3>
      <div class="chips">
        {#each watching as [t, n]}<span class="chip"><Icon name={SPEC[t]?.icon ?? "info"} size={11} />{SPEC[t]?.title ?? t}{n > 1 ? ` ×${n}` : ""}</span>{:else}<p class="none">Nic – dodaj wyzwalacze do rutyn.</p>{/each}
      </div>
      {#if issues.length}
        <h3 class="mt"><Icon name="alert" size={13} />Do uzupełnienia</h3>
        {#each issues.slice(0, 4) as r (r.id)}
          <button class="row err" onclick={() => openRoutine(r.id)}><span class="nm">{r.name}</span><span class="when">{routineIssues(r)} {routineIssues(r) === 1 ? "problem" : "problemy"}</span></button>
        {/each}
      {/if}
    </section>
  </div>

  {#if S.data.routines.length < 4}
    <section class="start">
      <h3><Icon name="template" size={13} />Na dobry początek</h3>
      <div class="st">
        {#each starters as p (p.title)}
          <button class="s" onclick={() => addRoutine(presetToRoutine(p))}><b>{p.title}</b><span>{p.desc}</span><i><Icon name="plus" size={12} /></i></button>
        {/each}
      </div>
    </section>
  {/if}
</div>

<style>
  .ov {
    height: 100%;
    overflow: auto;
    padding: 16px 18px 24px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .hero {
    display: flex;
    align-items: flex-end;
    gap: 12px;
  }
  h1 {
    margin: 0;
    font-family: var(--font-display);
    font-size: 21px;
    font-weight: 700;
  }
  .hero p {
    margin: 3px 0 0;
    color: var(--text-2);
  }
  .warn {
    color: var(--warn);
  }
  .grow {
    flex: 1;
  }
  .sun {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 11.5px;
    color: var(--text-2);
    padding: 4px 10px;
    border: 1px solid var(--line);
    border-radius: 999px;
  }
  .kpis {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 8px;
  }
  .kpi {
    border: 1px solid var(--line);
    border-radius: 12px;
    padding: 9px 12px;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .kpi span {
    font-size: 11px;
    color: var(--text-3);
  }
  .kpi b {
    font-family: var(--font-display);
    font-size: 22px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }
  .kpi i {
    font-style: normal;
    font-size: 13px;
    color: var(--text-3);
    font-weight: 500;
  }
  .kpi.bad {
    border-color: var(--err);
  }
  .kpi.bad b {
    color: var(--err);
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }
  section {
    border: 1px solid var(--line);
    border-radius: 12px;
    padding: 10px 10px 8px;
    min-width: 0;
  }
  h3 {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0 4px 6px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.4px;
    text-transform: uppercase;
    color: var(--text-3);
  }
  h3.mt {
    margin-top: 12px;
  }
  .row {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 5px 6px;
    border: 0;
    border-radius: 7px;
    background: transparent;
    cursor: pointer;
    text-align: left;
    color: var(--text-2);
  }
  .row:hover {
    background: var(--raised);
    color: var(--text);
  }
  .row.err {
    color: var(--err);
  }
  .nm {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--text);
    font-weight: 500;
  }
  .when {
    font-size: 11px;
    color: var(--text-3);
    white-space: nowrap;
    font-variant-numeric: tabular-nums;
  }
  .row:first-of-type .when {
    min-width: 0;
  }
  .none {
    margin: 4px 6px;
    font-size: 11.5px;
    color: var(--text-3);
  }
  .quick {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 6px;
  }
  .q {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 5px;
    padding: 9px 10px;
    border: 1px solid var(--line-2);
    border-radius: 10px;
    background: transparent;
    cursor: pointer;
    text-align: left;
    min-width: 0;
  }
  .q span {
    font-weight: 600;
    font-size: 12px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 100%;
  }
  .q:hover {
    background: var(--sel-bg);
    color: var(--sel-fg);
    border-color: var(--sel-bg);
  }
  .q.busy {
    opacity: 0.6;
  }
  kbd {
    font-family: var(--mono);
    font-size: 10px;
    opacity: 0.65;
  }
  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    padding: 0 4px;
  }
  .start .st {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 6px;
  }
  .s {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 3px;
    padding: 9px 30px 9px 10px;
    border: 1px solid var(--line);
    border-radius: 10px;
    background: transparent;
    text-align: left;
    cursor: pointer;
  }
  .s:hover {
    border-color: var(--line-2);
    background: var(--raised);
  }
  .s b {
    font-size: 12.5px;
  }
  .s span {
    font-size: 11px;
    color: var(--text-3);
    line-height: 1.35;
  }
  .s i {
    position: absolute;
    top: 9px;
    right: 9px;
  }
  @media (max-width: 1000px) {
    .grid,
    .start .st {
      grid-template-columns: 1fr;
    }
    .kpis {
      grid-template-columns: repeat(3, 1fr);
    }
  }
</style>
