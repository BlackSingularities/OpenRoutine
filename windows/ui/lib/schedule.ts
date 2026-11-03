// Najbliższe uruchomienia rutyn z wyzwalaczy czasowych i sprawdzanie kompletności klocków.
import type { Block, Routine } from "./api";
import { SPEC } from "./catalog";

const hm = (s: string): number | null => {
  const m = /^(\d{1,2}):(\d{2})$/.exec((s || "").trim());
  if (!m) return null;
  const h = +m[1], mi = +m[2];
  return h < 24 && mi < 60 ? h * 60 + mi : null;
};
const dayOk = (days: string, d: Date) => !days || days.includes(String(((d.getDay() + 6) % 7) + 1));
const at = (d: Date, min: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate(), Math.floor(min / 60), min % 60, 0, 0);
const p = (b: Block, k: string) => b.params[k] ?? SPEC[b.type]?.params?.find((x) => x.key === k)?.def ?? "";

/** Wschód i zachód (minuty lokalne) – ten sam algorytm NOAA co w silniku. */
export function sunTimes(date: Date, lat: number, lon: number): [number, number] | null {
  const start = new Date(date.getFullYear(), 0, 0);
  const n = Math.floor((+date - +start) / 86400000);
  const g = ((2 * Math.PI) / 365) * (n - 1);
  const eq = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
  const decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
  const la = (lat * Math.PI) / 180;
  const c = Math.cos((90.833 * Math.PI) / 180) / (Math.cos(la) * Math.cos(decl)) - Math.tan(la) * Math.tan(decl);
  if (c < -1 || c > 1) return null;
  const ha = (Math.acos(c) * 180) / Math.PI;
  const tz = -date.getTimezoneOffset();
  const mod = (x: number) => ((x % 1440) + 1440) % 1440;
  return [Math.round(mod(720 - 4 * (lon + ha) - eq + tz)), Math.round(mod(720 - 4 * (lon - ha) - eq + tz))];
}

export const fmtMin = (m: number) => `${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`;

function nextFor(b: Block, now: Date, r: Routine, loc: [number, number]): number | null {
  const nowMin = now.getHours() * 60 + now.getMinutes();
  switch (b.type) {
    case "t.time": {
      const t = hm(p(b, "time"));
      if (t == null) return null;
      for (let i = 0; i < 8; i++) {
        const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() + i);
        if ((i > 0 || t > nowMin) && dayOk(p(b, "days"), d)) return +at(d, t);
      }
      return null;
    }
    case "t.sun": {
      for (let i = 0; i < 8; i++) {
        const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() + i);
        const s = sunTimes(d, loc[0], loc[1]);
        if (!s) continue;
        const t = (((p(b, "event") === "sunrise" ? s[0] : s[1]) + (+p(b, "offset") || 0)) % 1440 + 1440) % 1440;
        if ((i > 0 || t > nowMin) && dayOk(p(b, "days"), d)) return +at(d, t);
      }
      return null;
    }
    case "t.monthday": {
      const t = hm(p(b, "time")) ?? 540;
      for (let i = 0; i < 3; i++) {
        const y = now.getFullYear(), m = now.getMonth() + i;
        const last = new Date(y, m + 1, 0).getDate();
        const day = p(b, "day") === "last" ? last : Math.min(+p(b, "day") || 1, last);
        const d = at(new Date(y, m, day), t);
        if (+d > +now) return +d;
      }
      return null;
    }
    case "t.once": {
      const d = new Date((p(b, "at") || "").replace(" ", "T"));
      return isNaN(+d) || +d < +now ? null : +d;
    }
    case "t.interval": {
      const every = Math.max(1, +p(b, "minutes") || 30) * 60000;
      const base = r.lastRun && +now - r.lastRun < every ? r.lastRun : +now;
      let t = base + every;
      const from = hm(p(b, "from")), to = hm(p(b, "to"));
      for (let i = 0; i < 2000; i++) {
        const d = new Date(t);
        const m = d.getHours() * 60 + d.getMinutes();
        const inWin = from == null || to == null ? true : from <= to ? m >= from && m < to : m >= from || m < to;
        if (inWin && dayOk(p(b, "days"), d)) return t;
        t += every;
      }
      return null;
    }
  }
  return null;
}

export function nextRun(r: Routine, loc: [number, number], now = new Date()): number | null {
  if (!r.enabled) return null;
  let best: number | null = null;
  for (const b of r.triggers) {
    if (b.off) continue;
    const t = nextFor(b, now, r, loc);
    if (t != null && (best == null || t < best)) best = t;
  }
  return best;
}

export function fmtNext(t: number, now = Date.now()): string {
  const d = new Date(t);
  const today = new Date(now);
  const time = d.toLocaleTimeString("pl-PL", { hour: "2-digit", minute: "2-digit" });
  const diff = Math.round((t - now) / 60000);
  if (diff < 60) return `za ${Math.max(1, diff)} min`;
  const days = Math.round((+new Date(d.getFullYear(), d.getMonth(), d.getDate()) - +new Date(today.getFullYear(), today.getMonth(), today.getDate())) / 86400000);
  if (days === 0) return `dziś ${time}`;
  if (days === 1) return `jutro ${time}`;
  return d.toLocaleDateString("pl-PL", { weekday: "short", day: "numeric", month: "short" }) + " " + time;
}

const NEEDS_VALUE = new Set(["text", "folder", "file", "app", "keys", "routine", "window", "var", "datetime", "time"]);

/** Brakujące wymagane pola klocka (nazwy etykiet). */
export function missing(b: Block): string[] {
  const s = SPEC[b.type];
  if (!s) return ["nieznany klocek"];
  return (s.params ?? []).filter((x) => !x.optional && NEEDS_VALUE.has(x.type ?? "text") && !(b.params[x.key] ?? "").trim() && !["t.app_start", "t.app_stop", "t.app_focus", "t.drive", "t.wifi", "t.clipboard", "t.var_change", "t.routine_done", "c.computer"].includes(b.type)).map((x) => x.label);
}

export function routineIssues(r: Routine): number {
  return [...r.triggers, ...r.conditions, ...r.actions].filter((b) => !b.off && missing(b).length).length + (r.actions.length ? 0 : 1);
}
