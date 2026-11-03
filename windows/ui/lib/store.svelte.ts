// Stan interfejsu: dane z silnika, wybór, toasty. Zmiany rutyn zapisują się same.
import { listen } from "@tauri-apps/api/event";
import { api, makeRoutine, type Data, type Routine, type Snap, type RoutineDraft } from "./api";
import { applyTheme, type ThemeId } from "./theme";

export type Tab = "overview" | "routines" | "templates" | "log" | "vars" | "state" | "settings";

export const S = $state({
  ready: false,
  data: { routines: [], vars: {}, settings: {} as any, log: [] } as Data,
  snap: null as Snap | null,
  running: [] as string[],
  tab: "overview" as Tab,
  palette: false,
  help: false,
  preview: null as null | import("./presets").Preset,
  ctx: null as null | { x: number; y: number; id: string },
  selected: "" as string,
  toasts: [] as { id: number; text: string; kind: "ok" | "error" | "info"; action?: { label: string; run: () => void } }[],
  processes: [] as string[],
  windows: [] as string[],
  failedHotkeys: [] as string[],
});

let toastId = 0;
export function toast(text: string, kind: "ok" | "error" | "info" = "info", action?: { label: string; run: () => void }) {
  const id = ++toastId;
  S.toasts.push({ id, text, kind, action });
  setTimeout(() => dismissToast(id), action ? 7000 : kind === "error" ? 6000 : 3200);
}
export function dismissToast(id: number) {
  S.toasts = S.toasts.filter((t) => t.id !== id);
}

// Edytowana kopia jest lokalna; do silnika trafia z opóźnieniem, żeby pisanie było płynne.
const timers = new Map<string, any>();
export function saveRoutine(r: Routine, now = false) {
  const i = S.data.routines.findIndex((x) => x.id === r.id);
  if (i >= 0) S.data.routines[i] = r;
  else S.data.routines.push(r);
  clearTimeout(timers.get(r.id));
  const go = () => {
    timers.delete(r.id);
    api.saveRoutine($state.snapshot(r) as Routine).catch((e) => toast(String(e), "error"));
  };
  if (now) go();
  else timers.set(r.id, setTimeout(go, 350));
}
export function pendingSave() {
  return timers.size > 0;
}

export async function refresh() {
  const d = await api.getData();
  // nie nadpisuj rutyny, która właśnie czeka na zapis
  for (const id of timers.keys()) {
    const local = S.data.routines.find((r) => r.id === id);
    const j = d.routines.findIndex((r) => r.id === id);
    if (local && j >= 0) d.routines[j] = { ...local, lastRun: d.routines[j].lastRun, runs: d.routines[j].runs, lastOk: d.routines[j].lastOk };
  }
  S.data = d;
  if (S.selected && !d.routines.some((r) => r.id === S.selected)) S.selected = "";
}

export function addRoutine(d: RoutineDraft, open = true): Routine {
  const r = makeRoutine(d);
  saveRoutine(r, true);
  if (open) {
    S.selected = r.id;
    S.tab = "routines";
  }
  return r;
}

export function duplicate(r: Routine) {
  const copy = makeRoutine({ ...$state.snapshot(r), name: r.name + " (kopia)", enabled: false } as any);
  saveRoutine(copy, true);
  S.selected = copy.id;
  toast("Utworzono kopię – jest wyłączona, dopóki jej nie włączysz.", "info");
}

export async function removeRoutine(id: string) {
  const r = S.data.routines.find((x) => x.id === id);
  if (!r) return;
  const copy = $state.snapshot(r) as Routine;
  const pos = S.data.routines.indexOf(r);
  S.data.routines = S.data.routines.filter((x) => x.id !== id);
  if (S.selected === id) S.selected = "";
  await api.deleteRoutine(id);
  toast(`Usunięto „${copy.name}”.`, "info", {
    label: "Cofnij",
    run: async () => {
      await api.saveRoutine(copy);
      const ids = S.data.routines.map((x) => x.id);
      ids.splice(pos, 0, copy.id);
      await api.reorder(ids);
      await refresh();
      S.selected = copy.id;
    },
  });
}

export async function setEnabled(r: Routine, on: boolean) {
  r.enabled = on;
  await api.setEnabled(r.id, on);
}

export function openRoutine(id: string) {
  S.selected = id;
  S.tab = "routines";
}

export async function saveSettings() {
  await api.saveSettings($state.snapshot(S.data.settings));
  applyTheme((S.data.settings.theme || "dark") as ThemeId, S.data.settings.customColor);
}

export function setTheme(id: ThemeId) {
  S.data.settings.theme = id;
  saveSettings();
}

export async function loadPickers() {
  try {
    [S.processes, S.windows] = await Promise.all([api.processes(), api.windowTitles()]);
  } catch {}
}

export async function init() {
  await refresh();
  applyTheme((S.data.settings.theme || "dark") as ThemeId, S.data.settings.customColor);
  S.running = await api.running();
  S.snap = await api.snapshot();
  S.ready = true;
  let t: any = null;
  await listen("data-changed", () => {
    clearTimeout(t);
    t = setTimeout(() => refresh(), 120);
    setTimeout(async () => (S.failedHotkeys = await api.failedHotkeys()), 600);
  });
  await listen<string[]>("running", (e) => (S.running = e.payload));
  await listen<Snap>("snap", (e) => (S.snap = e.payload));
  S.failedHotkeys = await api.failedHotkeys();
  loadPickers();
}

export function fmtAgo(ms: number): string {
  if (!ms) return "jeszcze nie";
  const s = Math.round((Date.now() - ms) / 1000);
  if (s < 60) return "przed chwilą";
  if (s < 3600) return `${Math.floor(s / 60)} min temu`;
  if (s < 86400) return `${Math.floor(s / 3600)} h temu`;
  const d = new Date(ms);
  return d.toLocaleDateString("pl-PL", { day: "numeric", month: "short" }) + " " + d.toLocaleTimeString("pl-PL", { hour: "2-digit", minute: "2-digit" });
}

export function fmtTime(ms: number): string {
  const d = new Date(ms);
  return d.toLocaleDateString("pl-PL", { day: "2-digit", month: "2-digit" }) + " " + d.toLocaleTimeString("pl-PL");
}
