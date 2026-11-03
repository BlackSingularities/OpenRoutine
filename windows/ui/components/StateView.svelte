<script lang="ts">
  import Icon from "./Icon.svelte";
  import { S } from "../lib/store.svelte";

  const s = $derived(S.snap);
  const up = $derived(s ? `${Math.floor(s.uptime / 86400)} d ${Math.floor((s.uptime % 86400) / 3600)} h ${Math.floor((s.uptime % 3600) / 60)} min` : "");
  const idle = $derived(s ? (s.idle < 60 ? `${s.idle} s` : `${Math.floor(s.idle / 60)} min`) : "");
  const tiles = $derived(
    s
      ? [
          { icon: "battery", t: "Bateria", v: s.hasBattery ? `${s.battery}%` : "brak", d: s.hasBattery ? (s.ac ? "ładowanie / zasilacz" : "na baterii") + (s.saver ? " · oszczędzanie" : "") : "komputer stacjonarny", p: s.hasBattery ? s.battery : -1 },
          { icon: "cpu", t: "Procesor", v: `${Math.round(s.cpu)}%`, d: "obciążenie", p: s.cpu },
          { icon: "memory", t: "Pamięć RAM", v: `${Math.round(s.ram)}%`, d: "zajęta", p: s.ram },
          { icon: "volume", t: "Głośność", v: s.muted ? "wyciszone" : `${s.volume}%`, d: "główne wyjście", p: s.muted ? 0 : s.volume },
          { icon: "moon", t: "Bezczynność", v: idle, d: s.locked ? "ekran zablokowany" : "od ostatniego ruchu", p: -1 },
          { icon: "globe", t: "Internet", v: s.online ? "połączono" : "brak", d: s.ssid ? `Wi-Fi: ${s.ssid}` : "bez Wi-Fi", p: -1 },
          { icon: "monitor", t: "Monitory", v: String(s.monitors), d: s.fullscreen ? "aplikacja na pełnym ekranie" : "podłączone", p: -1 },
          { icon: "contrast", t: "Motyw Windows", v: s.dark ? "ciemny" : "jasny", d: "aplikacje", p: -1 },
          { icon: "clock", t: "Czas pracy", v: up, d: "od uruchomienia Windows", p: -1 },
        ]
      : [],
  );
</script>

<div class="sv">
  <div class="head">
    <h2>Stan systemu na żywo</h2>
    <span class="muted small">Te wartości widzą wyzwalacze i warunki – przydatne przy ustawianiu progów.</span>
  </div>
  {#if s}
    <div class="grid">
      {#each tiles as t}
        <div class="tile">
          <div class="th"><Icon name={t.icon} size={14} /><span>{t.t}</span></div>
          <b>{t.v}</b>
          <span class="d">{t.d}</span>
          {#if t.p >= 0}<div class="bar"><i style="width:{Math.min(100, t.p)}%"></i></div>{/if}
        </div>
      {/each}
    </div>
    <div class="wide">
      <div class="tile">
        <div class="th"><Icon name="focus" size={14} /><span>Aktywna aplikacja</span></div>
        <b class="mono selectable">{s.fgExe || "—"}</b>
        <span class="d selectable">{s.fgTitle}</span>
      </div>
      <div class="tile">
        <div class="th"><Icon name="disk" size={14} /><span>Dyski</span></div>
        <div class="drv">
          {#each s.drives as d}<span class="chip" class:on={d.removable}>{d.letter} {d.label}{d.removable ? " · USB" : ""}</span>{/each}
        </div>
      </div>
    </div>
  {:else}
    <div class="empty">Odczytywanie…</div>
  {/if}
</div>

<style>
  .sv {
    height: 100%;
    overflow: auto;
    padding: 14px 18px 24px;
  }
  .head {
    display: flex;
    align-items: baseline;
    gap: 12px;
    margin-bottom: 12px;
  }
  h2 {
    margin: 0;
    font-size: 14px;
    font-family: var(--font-display);
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 8px;
  }
  .wide {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    margin-top: 8px;
  }
  .tile {
    border: 1px solid var(--line);
    border-radius: 12px;
    padding: 10px 12px;
    display: flex;
    flex-direction: column;
    gap: 3px;
    min-width: 0;
  }
  .th {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    color: var(--text-3);
  }
  .tile b {
    font-family: var(--font-display);
    font-size: 19px;
    font-weight: 650;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .tile b.mono {
    font-size: 14px;
  }
  .d {
    font-size: 11px;
    color: var(--text-2);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .bar {
    height: 4px;
    border-radius: 4px;
    background: rgba(var(--fg-rgb), 0.12);
    margin-top: 5px;
    overflow: hidden;
  }
  .bar i {
    display: block;
    height: 100%;
    background: var(--sel-bg);
    border-radius: 4px;
    transition: width 0.4s;
  }
  .drv {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    margin-top: 3px;
  }
</style>
