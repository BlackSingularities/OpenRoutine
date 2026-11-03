// Typy danych i wywołania silnika (src-tauri/src/lib.rs).
import { invoke } from "@tauri-apps/api/core";
import { defaults } from "./catalog";

export interface Block {
  id: string;
  type: string;
  params: Record<string, string>;
  off?: boolean;
}

export interface Routine {
  id: string;
  name: string;
  enabled: boolean;
  group: string;
  notes: string;
  triggers: Block[];
  conditions: Block[];
  matchAll: boolean;
  actions: Block[];
  cooldownSec: number;
  stopOnError: boolean;
  quiet: boolean;
  lastRun: number;
  runs: number;
  lastOk: boolean;
}

export interface Settings {
  autostart: boolean;
  closeToTray: boolean;
  startMinimized: boolean;
  engineOn: boolean;
  webhookOn: boolean;
  webhookPort: number;
  token: string;
  notifyErrors: boolean;
  logLimit: number;
  theme: string;
  customColor: string;
  checkUpdates: boolean;
  skippedVersion: string;
  onboarded: boolean;
  density: string;
}

export interface LogEntry {
  time: number;
  routine: string;
  message: string;
  ok: boolean;
}

export interface Data {
  routines: Routine[];
  vars: Record<string, string>;
  settings: Settings;
  log: LogEntry[];
}

export interface Snap {
  battery: number;
  ac: boolean;
  hasBattery: boolean;
  saver: boolean;
  idle: number;
  fgExe: string;
  fgTitle: string;
  fullscreen: boolean;
  locked: boolean;
  online: boolean;
  ssid: string;
  cpu: number;
  ram: number;
  monitors: number;
  drives: { letter: string; label: string; removable: boolean }[];
  dark: boolean;
  volume: number;
  muted: boolean;
  uptime: number;
}

export type RoutineDraft = Partial<Omit<Routine, "triggers" | "conditions" | "actions">> & {
  triggers: { type: string; params?: Record<string, string> }[];
  conditions?: { type: string; params?: Record<string, string> }[];
  actions: { type: string; params?: Record<string, string> }[];
};

export const newId = () => Math.random().toString(36).slice(2, 12).padEnd(10, "0");

export function block(type: string, params: Record<string, string> = {}): Block {
  return { id: newId(), type, params: { ...defaults(type), ...params } };
}

export function makeRoutine(d: RoutineDraft): Routine {
  return {
    id: newId(), name: d.name ?? "Nowa rutyna", enabled: d.enabled ?? true, group: d.group ?? "", notes: d.notes ?? "",
    triggers: d.triggers.map((b) => block(b.type, b.params)), conditions: (d.conditions ?? []).map((b) => block(b.type, b.params)),
    matchAll: d.matchAll ?? true, actions: d.actions.map((b) => block(b.type, b.params)), cooldownSec: d.cooldownSec ?? 0,
    stopOnError: d.stopOnError ?? false, quiet: d.quiet ?? false, lastRun: 0, runs: 0, lastOk: true,
  };
}

export const api = {
  getData: () => invoke<Data>("get_data"),
  saveRoutine: (routine: Routine) => invoke("save_routine", { routine }),
  importRoutines: (routines: Routine[]) => invoke<number>("import_routines", { routines }),
  deleteRoutine: (id: string) => invoke("delete_routine", { id }),
  reorder: (ids: string[]) => invoke("reorder", { ids }),
  setEnabled: (id: string, on: boolean) => invoke("set_enabled", { id, on }),
  run: (id: string, test = false) => invoke("run_routine", { id, test }),
  stop: (id: string) => invoke("stop_routine", { id }),
  running: () => invoke<string[]>("running"),
  saveSettings: (settings: Settings) => invoke("save_settings", { settings }),
  newToken: () => invoke<string>("new_token"),
  setVar: (name: string, value: string) => invoke("set_var", { name, value }),
  deleteVar: (name: string) => invoke("delete_var", { name }),
  clearLog: () => invoke("clear_log"),
  snapshot: () => invoke<Snap>("snapshot"),
  processes: () => invoke<string[]>("processes"),
  windowTitles: () => invoke<string[]>("window_titles"),
  failedHotkeys: () => invoke<string[]>("failed_hotkeys"),
  writeText: (path: string, text: string) => invoke("write_text", { path, text }),
  readText: (path: string) => invoke<string>("read_text", { path }),
  dataDir: () => invoke<string>("data_dir"),
  preview: (text: string) => invoke<string>("preview_text", { text }),
  fireEvent: (name: string, extra: string) => invoke("fire_event", { name, extra }),
  quit: () => invoke("quit_app"),
};
