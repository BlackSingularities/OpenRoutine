//! Silnik: przechowuje dane, co sekundę porównuje stan systemu z poprzednim,
//! odpala wyzwalacze i wykonuje rutyny w osobnych wątkach.

use crate::model::*;
use crate::sys;
use crate::vars;
use chrono::{Datelike, Local, NaiveDateTime, TimeZone, Timelike};
use serde::Serialize;
use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::time::{Duration, Instant};
use tauri::{AppHandle, Emitter};

pub struct Engine {
    pub data: Mutex<Data>,
    pub app: AppHandle,
    pub dir: PathBuf,
    pub running: Mutex<HashMap<String, Arc<AtomicBool>>>,
    /// Zmienia się przy każdej zmianie rutyn – monitor odświeża wtedy listę wyzwalaczy.
    pub gen: AtomicU64,
    dirty: AtomicBool,
    pub started: Instant,
    pub snap: Mutex<Snap>,
    /// Numer sekwencji schowka po ostatnim zapisie przez akcję – nie wyzwala rutyn.
    pub own_clip: AtomicU32,
}

static ENGINE: OnceLock<Arc<Engine>> = OnceLock::new();

pub fn get() -> &'static Arc<Engine> {
    ENGINE.get().expect("engine not started")
}

pub fn now_ms() -> i64 {
    Local::now().timestamp_millis()
}

#[derive(Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Snap {
    pub battery: i32,
    pub ac: bool,
    pub has_battery: bool,
    pub saver: bool,
    pub idle: u64,
    pub fg_exe: String,
    pub fg_title: String,
    pub fullscreen: bool,
    pub locked: bool,
    pub online: bool,
    pub ssid: String,
    pub cpu: f64,
    pub ram: f64,
    pub monitors: i32,
    pub drives: Vec<DriveInfo>,
    pub dark: bool,
    pub volume: i32,
    pub muted: bool,
    pub uptime: u64,
    #[serde(skip)]
    pub procs: HashSet<String>,
    #[serde(skip)]
    pub procs_fresh: bool,
    #[serde(skip)]
    pub clip_seq: u32,
}

#[derive(Clone, Debug, Default, Serialize, PartialEq)]
pub struct DriveInfo {
    pub letter: String,
    pub label: String,
    pub removable: bool,
}

impl Engine {
    pub fn start(app: AppHandle, dir: PathBuf) -> Arc<Engine> {
        let _ = std::fs::create_dir_all(&dir);
        let mut data: Data = std::fs::read_to_string(dir.join("data.json")).ok().and_then(|s| serde_json::from_str(&s).ok()).unwrap_or_default();
        if data.settings.token.is_empty() {
            data.settings.token = format!("{:012x}", vars::rand_u64() & 0xffff_ffff_ffff);
        }
        let first = !dir.join("data.json").exists();
        let e = Arc::new(Engine {
            data: Mutex::new(data),
            app,
            dir,
            running: Mutex::new(HashMap::new()),
            gen: AtomicU64::new(1),
            dirty: AtomicBool::new(first),
            started: Instant::now(),
            snap: Mutex::new(Snap::default()),
            own_clip: AtomicU32::new(0),
        });
        let _ = ENGINE.set(e.clone());
        let me = e.clone();
        std::thread::Builder::new().name("monitor".into()).spawn(move || me.monitor_loop()).ok();
        e
    }

    pub fn changed(&self) {
        self.gen.fetch_add(1, Ordering::SeqCst);
        self.dirty.store(true, Ordering::SeqCst);
        let _ = self.app.emit("data-changed", ());
        crate::refresh_tray(&self.app);
        crate::hotkeys::reload();
    }

    /// Zmiana tylko statystyk lub dziennika – bez przebudowy wyzwalaczy.
    pub fn touched(&self) {
        self.dirty.store(true, Ordering::SeqCst);
        let _ = self.app.emit("data-changed", ());
    }

    pub fn save_now(&self) {
        if !self.dirty.swap(false, Ordering::SeqCst) {
            return;
        }
        let json = {
            let d = self.data.lock().unwrap();
            serde_json::to_string_pretty(&*d).unwrap_or_default()
        };
        let tmp = self.dir.join("data.json.tmp");
        if std::fs::write(&tmp, json).is_ok() {
            let _ = std::fs::rename(&tmp, self.dir.join("data.json"));
        }
    }

    pub fn log(&self, routine: &str, message: impl Into<String>, ok: bool) {
        let message = message.into();
        {
            let mut d = self.data.lock().unwrap();
            let limit = d.settings.log_limit.clamp(50, 5000);
            d.log.push_front(LogEntry { time: now_ms(), routine: routine.into(), message: message.clone(), ok });
            while d.log.len() > limit {
                d.log.pop_back();
            }
        }
        self.touched();
        if !ok {
            let notify = self.data.lock().unwrap().settings.notify_errors;
            if notify {
                crate::actions::notify(&self.app, &format!("OpenRoutine – {routine}"), &message);
            }
        }
    }

    // ------------------------------------------------------------ zdarzenia zewnętrzne

    /// Zdarzenie spoza monitora (skrót, HTTP, wiersz poleceń, zakończenie rutyny, zmiana zmiennej).
    pub fn fire(&self, ev: Event) {
        let targets: Vec<(String, Event)> = {
            let d = self.data.lock().unwrap();
            if !d.settings.engine_on {
                return;
            }
            d.routines
                .iter()
                .filter(|r| r.enabled)
                .filter_map(|r| r.triggers.iter().filter(|t| !t.off && t.kind == ev.kind).find(|t| external_match(t, &ev, r)).map(|t| (r.id.clone(), ev_for(t, &ev))))
                .collect()
        };
        for (id, e) in targets {
            self.run(&id, e, RunMode::Trigger);
        }
    }

    pub fn find_routine(&self, key: &str) -> Option<String> {
        let d = self.data.lock().unwrap();
        let k = key.trim().to_lowercase();
        d.routines.iter().find(|r| r.id == key || r.name.to_lowercase() == k).map(|r| r.id.clone())
    }

    // ------------------------------------------------------------ wykonanie

    pub fn run(&self, id: &str, ev: Event, mode: RunMode) {
        let Some(r) = self.data.lock().unwrap().routines.iter().find(|r| r.id == id).cloned() else { return };
        if mode == RunMode::Trigger {
            if !r.enabled {
                return;
            }
            if r.cooldown_sec > 0 && r.last_run > 0 && now_ms() - r.last_run < (r.cooldown_sec as i64) * 1000 {
                return;
            }
        }
        let stop = Arc::new(AtomicBool::new(false));
        {
            let mut run = self.running.lock().unwrap();
            if run.contains_key(id) {
                return;
            }
            run.insert(id.to_string(), stop.clone());
        }
        let _ = self.app.emit("running", self.running_ids());
        let me = get().clone();
        std::thread::spawn(move || {
            let out = me.execute(&r, ev, mode, &stop);
            me.running.lock().unwrap().remove(&r.id);
            let _ = me.app.emit("running", me.running_ids());
            if let Some(ok) = out {
                me.fire(Event::new("t.routine_done").with("routine", r.name.clone()).with("routine_id", r.id.clone()).with("result", if ok { "ok" } else { "error" }));
            }
        });
    }

    pub fn running_ids(&self) -> Vec<String> {
        self.running.lock().unwrap().keys().cloned().collect()
    }

    pub fn stop(&self, id: &str) {
        if let Some(f) = self.running.lock().unwrap().get(id) {
            f.store(true, Ordering::SeqCst);
        }
    }

    /// Zwraca None, gdy warunki nie były spełnione (nic nie wykonano).
    fn execute(&self, r: &Routine, ev: Event, mode: RunMode, stop: &AtomicBool) -> Option<bool> {
        let mut local: HashMap<String, String> = ev.data.clone();
        local.insert("routine".into(), r.name.clone());
        local.insert("trigger".into(), trigger_label(&ev.kind).into());
        if mode != RunMode::Test {
            let conds: Vec<&Block> = r.conditions.iter().filter(|c| !c.off).collect();
            if !conds.is_empty() {
                let res: Vec<bool> = conds.iter().map(|c| crate::conditions::check(c, &local, r)).collect();
                let pass = if r.match_all { res.iter().all(|&x| x) } else { res.iter().any(|&x| x) };
                if !pass {
                    return None;
                }
            }
        }
        let t0 = Instant::now();
        let mut errors: Vec<String> = Vec::new();
        let mut skip = 0usize;
        let mut stopped = false;
        let mut done = 0usize;
        for a in r.actions.iter() {
            if stop.load(Ordering::SeqCst) {
                errors.push("przerwano ręcznie".into());
                break;
            }
            if a.off {
                continue;
            }
            if skip > 0 {
                skip -= 1;
                continue;
            }
            match crate::actions::run(a, &mut local, r, stop) {
                Ok(Flow::Next) => done += 1,
                Ok(Flow::Skip(n)) => {
                    done += 1;
                    skip = n
                }
                Ok(Flow::Stop) => {
                    stopped = true;
                    break;
                }
                Err(e) => {
                    errors.push(format!("{}: {}", crate::actions::title(&a.kind), e));
                    if r.stop_on_error {
                        break;
                    }
                }
            }
        }
        let ok = errors.is_empty();
        {
            let mut d = self.data.lock().unwrap();
            if let Some(x) = d.routines.iter_mut().find(|x| x.id == r.id) {
                x.last_run = now_ms();
                x.runs += 1;
                x.last_ok = ok;
            }
        }
        let how = match mode {
            RunMode::Test => "próba",
            RunMode::Manual => "ręcznie",
            RunMode::Trigger => trigger_label(&ev.kind),
        };
        let ms = t0.elapsed().as_millis();
        if ok {
            if !r.quiet {
                let extra = if stopped { " · zatrzymano warunkiem" } else { "" };
                self.log(&r.name, format!("Wykonano {done} akcji ({how}, {ms} ms){extra}"), true);
            } else {
                self.touched();
            }
        } else {
            self.log(&r.name, format!("{} ({how})", errors.join(" · ")), false);
        }
        Some(ok)
    }

    // ------------------------------------------------------------ monitor

    fn monitor_loop(self: Arc<Self>) {
        let mut prev: Option<Snap> = None;
        let mut mem: HashMap<String, Mem> = HashMap::new();
        let mut cpu = sys::CpuSampler::default();
        let mut gen = 0u64;
        let mut watched: Vec<(String, Vec<Block>)> = Vec::new();
        let mut need: HashSet<String> = HashSet::new();
        let mut tick: u64 = 0;
        let mut last_save = Instant::now();
        cpu.sample();
        loop {
            let t_start = Instant::now();
            let g = self.gen.load(Ordering::SeqCst);
            if g != gen {
                gen = g;
                let d = self.data.lock().unwrap();
                watched = if d.settings.engine_on {
                    d.routines.iter().filter(|r| r.enabled).map(|r| (r.id.clone(), r.triggers.iter().filter(|t| !t.off).cloned().collect())).collect()
                } else {
                    Vec::new()
                };
                need = watched.iter().flat_map(|(_, ts)| ts.iter().map(|t| t.kind.clone())).collect();
                let ids: HashSet<String> = watched.iter().flat_map(|(_, ts)| ts.iter().map(|t| t.id.clone())).collect();
                mem.retain(|k, _| ids.contains(k));
            }

            let cur = self.sample(prev.as_ref(), tick, &need, &mut cpu);
            *self.snap.lock().unwrap() = cur.clone();
            if tick % 2 == 0 {
                let _ = self.app.emit("snap", &cur);
            }

            if let Some(p) = prev.as_ref() {
                let now = Local::now();
                let mut fire: Vec<(String, Event)> = Vec::new();
                for (rid, ts) in &watched {
                    for t in ts {
                        let m = mem.entry(t.id.clone()).or_default();
                        if let Some(ev) = eval_trigger(t, &cur, p, m, &now, self.started, tick) {
                            if !fire.iter().any(|(x, _)| x == rid) {
                                fire.push((rid.clone(), ev));
                            }
                        }
                    }
                }
                for (id, ev) in fire {
                    self.run(&id, ev, RunMode::Trigger);
                }
            }
            let mut keep = cur;
            if !keep.procs_fresh {
                if let Some(p) = prev.take() {
                    keep.procs = p.procs;
                }
            }
            prev = Some(keep);
            tick += 1;
            if last_save.elapsed() > Duration::from_secs(3) {
                self.save_now();
                last_save = Instant::now();
            }
            let el = t_start.elapsed();
            std::thread::sleep(Duration::from_millis(1000).saturating_sub(el).max(Duration::from_millis(200)));
        }
    }

    fn sample(&self, prev: Option<&Snap>, tick: u64, need: &HashSet<String>, cpu: &mut sys::CpuSampler) -> Snap {
        let mut s = prev.cloned().unwrap_or_default();
        let first = prev.is_none();
        let pw = sys::power();
        s.battery = pw.percent;
        s.ac = pw.ac;
        s.has_battery = pw.has_battery;
        s.saver = pw.saver;
        s.idle = sys::idle_secs();
        let fg = sys::foreground();
        s.fg_exe = fg.exe;
        s.fg_title = fg.title;
        s.fullscreen = fg.fullscreen;
        s.locked = sys::locked();
        s.monitors = sys::monitors();
        s.uptime = sys::uptime_secs();
        s.clip_seq = sys::clipboard_seq();
        s.procs_fresh = false;
        if first || tick % 2 == 0 {
            s.cpu = cpu.sample();
            s.ram = sys::memory_percent();
            s.drives = sys::drives().into_iter().map(|d| DriveInfo { letter: format!("{}:", d.letter), label: d.label, removable: d.removable }).collect();
        }
        if first || tick % 3 == 0 {
            if first || need.iter().any(|k| k == "t.app_start" || k == "t.app_stop") {
                s.procs = sys::processes();
                s.procs_fresh = true;
            }
            s.online = sys::online();
            s.dark = sys::dark_mode();
            if let Some((v, m)) = sys::volume() {
                s.volume = v;
                s.muted = m;
            }
        }
        if first || tick % 15 == 0 {
            s.ssid = sys::wifi_ssid();
        }
        s
    }
}

#[derive(Clone, Copy, PartialEq, Debug)]
pub enum RunMode {
    Trigger,
    Manual,
    Test,
}

pub enum Flow {
    Next,
    Stop,
    Skip(usize),
}

#[derive(Default)]
struct Mem {
    key: String,
    last: i64,
    flag: bool,
    since: Option<Instant>,
    files: Option<HashMap<PathBuf, (u64, u64)>>,
    next_scan: i64,
    title_hit: bool,
}

fn app_match(pattern: &str, exe: &str) -> bool {
    let p = pattern.trim().to_lowercase();
    if p.is_empty() {
        return true;
    }
    p.split([';', ',']).map(|x| x.trim()).filter(|x| !x.is_empty()).any(|x| {
        let x = sys::exe_name(x);
        let x = if x.contains('.') || x.contains('*') { x } else { format!("{x}.exe") };
        vars::wildcard(&x, exe)
    })
}

fn level_edge(prev: f64, cur: f64, dir: &str, level: f64) -> bool {
    if dir == "above" {
        prev < level && cur >= level
    } else {
        prev > level && cur <= level
    }
}

fn eval_trigger(t: &Block, cur: &Snap, p: &Snap, m: &mut Mem, now: &chrono::DateTime<Local>, started: Instant, tick: u64) -> Option<Event> {
    let k = t.kind.as_str();
    let ev = Event::new(k);
    let min_now = now.hour() * 60 + now.minute();
    let wd = now.weekday().number_from_monday();
    let ts = now.timestamp();
    match k {
        "t.time" => {
            let at = vars::hm(t.p("time"))?;
            let key = now.format("%Y%m%d%H%M").to_string();
            if at == min_now && vars::day_ok(t.p("days"), wd) && m.key != key {
                m.key = key;
                return Some(ev);
            }
            None
        }
        "t.interval" => {
            let every = (t.num("minutes", 30.0).max(1.0) * 60.0) as i64;
            if m.last == 0 {
                m.last = ts;
                return None;
            }
            let (from, to) = (vars::hm(t.p("from")), vars::hm(t.p("to")));
            if let (Some(a), Some(b)) = (from, to) {
                if !vars::in_window(min_now, a, b) {
                    return None;
                }
            }
            if !vars::day_ok(t.p("days"), wd) {
                return None;
            }
            if ts - m.last >= every {
                m.last = ts;
                return Some(ev);
            }
            None
        }
        "t.monthday" => {
            let at = vars::hm(t.pd("time", "09:00"))?;
            let day = t.p("day");
            let last_day = {
                let (y, mo) = (now.year(), now.month());
                let nm = if mo == 12 { chrono::NaiveDate::from_ymd_opt(y + 1, 1, 1) } else { chrono::NaiveDate::from_ymd_opt(y, mo + 1, 1) };
                nm.and_then(|d| d.pred_opt()).map(|d| d.day()).unwrap_or(28)
            };
            let want = if day == "last" { last_day } else { day.parse::<u32>().unwrap_or(1).min(last_day) };
            let key = now.format("%Y%m%d%H%M").to_string();
            if now.day() == want && at == min_now && m.key != key {
                m.key = key;
                return Some(ev);
            }
            None
        }
        "t.once" => {
            let dt = NaiveDateTime::parse_from_str(t.p("at").trim(), "%Y-%m-%d %H:%M").ok()?;
            let at = Local.from_local_datetime(&dt).single()?.timestamp();
            if !m.flag && ts >= at && ts - at < 120 {
                m.flag = true;
                return Some(ev);
            }
            None
        }
        "t.startup" => {
            let delay = t.num("delay", 0.0).max(0.0) as u64;
            if !m.flag && started.elapsed().as_secs() >= delay {
                m.flag = true;
                return Some(ev.with("uptime_min", (cur.uptime / 60).to_string()));
            }
            None
        }
        "t.idle" => {
            let lim = (t.num("minutes", 10.0) * 60.0) as u64;
            if cur.idle >= lim && !m.flag {
                m.flag = true;
                return Some(ev.with("idle_min", (cur.idle / 60).to_string()));
            }
            if cur.idle < lim {
                m.flag = false;
            }
            None
        }
        "t.active" => {
            let lim = (t.num("minutes", 10.0) * 60.0) as u64;
            if p.idle >= lim && cur.idle < p.idle && cur.idle < 5 {
                return Some(ev.with("idle_min", (p.idle / 60).to_string()));
            }
            None
        }
        "t.lock" => {
            if p.locked != cur.locked && (t.pd("state", "lock") == "lock") == cur.locked {
                return Some(ev);
            }
            None
        }
        "t.power" => {
            if !cur.has_battery || p.ac == cur.ac {
                return None;
            }
            (t.pd("state", "plugged") == "plugged" && cur.ac || t.pd("state", "plugged") == "unplugged" && !cur.ac).then(|| ev.with("battery", cur.battery.to_string()))
        }
        "t.battery" => {
            if cur.battery < 0 || p.battery < 0 {
                return None;
            }
            level_edge(p.battery as f64, cur.battery as f64, t.pd("dir", "below"), t.num("level", 20.0)).then(|| ev.with("battery", cur.battery.to_string()))
        }
        "t.saver" => (p.saver != cur.saver && (t.pd("state", "on") == "on") == cur.saver).then_some(ev),
        "t.cpu" | "t.ram" => {
            let v = if k == "t.cpu" { cur.cpu } else { cur.ram };
            let lim = t.num("level", 90.0);
            let secs = t.num("seconds", 30.0).max(0.0) as u64;
            if v >= lim {
                let since = *m.since.get_or_insert_with(Instant::now);
                if !m.flag && since.elapsed().as_secs() >= secs {
                    m.flag = true;
                    return Some(ev.with("value", format!("{v:.0}")));
                }
            } else if v < lim - 5.0 {
                m.since = None;
                m.flag = false;
            }
            None
        }
        "t.disk" => {
            if tick % 30 != 0 {
                return None;
            }
            let (_, gb) = sys::disk_free(t.pd("drive", "C"))?;
            let lim = t.num("gb", 10.0);
            if gb < lim && !m.flag {
                m.flag = true;
                return Some(ev.with("free_gb", format!("{gb:.1}")));
            }
            if gb >= lim + 1.0 {
                m.flag = false;
            }
            None
        }
        "t.monitors" => {
            if p.monitors == cur.monitors || p.monitors == 0 {
                return None;
            }
            let ok = match t.pd("dir", "any") {
                "more" => cur.monitors > p.monitors,
                "less" => cur.monitors < p.monitors,
                _ => true,
            };
            ok.then(|| ev.with("monitors", cur.monitors.to_string()))
        }
        "t.theme" => {
            if p.dark == cur.dark {
                return None;
            }
            match t.pd("mode", "any") {
                "dark" if !cur.dark => None,
                "light" if cur.dark => None,
                _ => Some(ev.with("theme", if cur.dark { "ciemny" } else { "jasny" })),
            }
        }
        "t.fullscreen" => (p.fullscreen != cur.fullscreen && (t.pd("state", "enter") == "enter") == cur.fullscreen).then(|| ev.with("app", cur.fg_exe.clone())),
        "t.app_start" | "t.app_stop" => {
            if !cur.procs_fresh || p.procs.is_empty() {
                return None;
            }
            let (a, b) = if k == "t.app_start" { (&cur.procs, &p.procs) } else { (&p.procs, &cur.procs) };
            let hit = a.iter().find(|x| !b.contains(*x) && app_match(t.p("app"), x))?;
            Some(ev.with("app", hit.clone()))
        }
        "t.app_focus" => {
            if p.fg_exe == cur.fg_exe || cur.fg_exe.is_empty() || !app_match(t.p("app"), &cur.fg_exe) {
                return None;
            }
            Some(ev.with("app", cur.fg_exe.clone()).with("title", cur.fg_title.clone()))
        }
        "t.window_title" => {
            let needle = t.p("text").to_lowercase();
            if needle.is_empty() {
                return None;
            }
            let hit = cur.fg_title.to_lowercase().contains(&needle);
            let fire = hit && !m.title_hit;
            m.title_hit = hit;
            fire.then(|| ev.with("title", cur.fg_title.clone()).with("app", cur.fg_exe.clone()))
        }
        "t.network" => (p.online != cur.online && (t.pd("state", "online") == "online") == cur.online).then_some(ev),
        "t.wifi" => {
            if p.ssid == cur.ssid {
                return None;
            }
            let f = t.p("ssid");
            let fm = |s: &str| f.trim().is_empty() || vars::wildcard_any(f, s);
            if t.pd("state", "connected") == "connected" {
                (!cur.ssid.is_empty() && fm(&cur.ssid)).then(|| ev.with("ssid", cur.ssid.clone()))
            } else {
                (!p.ssid.is_empty() && fm(&p.ssid)).then(|| ev.with("ssid", p.ssid.clone()))
            }
        }
        "t.drive" => {
            let (a, b) = if t.pd("state", "connected") == "connected" { (&cur.drives, &p.drives) } else { (&p.drives, &cur.drives) };
            let f = t.p("label");
            let d = a.iter().find(|d| d.removable && !b.iter().any(|x| x.letter == d.letter) && (f.trim().is_empty() || vars::wildcard_any(f, &d.label)))?;
            Some(ev.with("drive", d.letter.clone()).with("drive_label", d.label.clone()))
        }
        "t.clipboard" => {
            if p.clip_seq == cur.clip_seq || cur.clip_seq == get().own_clip.load(Ordering::SeqCst) {
                return None;
            }
            let text = sys::clipboard_get();
            if text.is_empty() {
                return None;
            }
            let f = t.p("filter");
            let ok = match t.pd("match", "contains") {
                "regex" => regex::Regex::new(f).map(|r| r.is_match(&text)).unwrap_or(false),
                _ => f.is_empty() || text.to_lowercase().contains(&f.to_lowercase()),
            };
            ok.then(|| ev.with("clipboard", text))
        }
        "t.file" => file_trigger(t, m, ev, ts),
        "t.sun" => {
            let (lat, lon) = location();
            let (rise, set) = crate::probe::sun_today(lat, lon)?;
            let base = if t.pd("event", "sunset") == "sunrise" { rise } else { set } as i64;
            let at = (base + t.num("offset", 0.0) as i64).rem_euclid(1440) as u32;
            let key = now.format("%Y%m%d").to_string();
            if at == min_now && vars::day_ok(t.p("days"), wd) && m.key != key {
                m.key = key;
                return Some(ev.with("sunrise", fmt_hm(rise)).with("sunset", fmt_hm(set)));
            }
            None
        }
        "t.host" => {
            let up = crate::probe::host(t.p("host"), t.num("every", 30.0) as u64)?;
            let prev = m.flag;
            let known = m.key == "known";
            m.flag = up;
            m.key = "known".into();
            if !known || prev == up {
                return None;
            }
            ((t.pd("state", "down") == "up") == up).then(|| ev.with("host", t.p("host").to_string()))
        }
        "t.web_change" => {
            let v = crate::probe::page(t.p("url"), t.p("json"), (t.num("minutes", 15.0) * 60.0) as u64)?;
            let old = std::mem::replace(&mut m.key, v.clone());
            if old.is_empty() || old == v {
                return None;
            }
            let short = |s: &str| s.chars().take(2000).collect::<String>();
            Some(ev.with("value", short(&v)).with("old_value", short(&old)))
        }
        _ => None,
    }
}

pub fn location() -> (f64, f64) {
    let d = get().data.lock().unwrap();
    (d.settings.latitude, d.settings.longitude)
}

pub fn fmt_hm(m: u32) -> String {
    format!("{:02}:{:02}", m / 60, m % 60)
}

fn scan(dir: &Path, pattern: &str, recursive: bool, out: &mut HashMap<PathBuf, (u64, u64)>, depth: usize) {
    let Ok(rd) = std::fs::read_dir(dir) else { return };
    for e in rd.flatten() {
        if out.len() > 20_000 {
            return;
        }
        let Ok(md) = e.metadata() else { continue };
        let path = e.path();
        if md.is_dir() {
            if recursive && depth < 6 {
                scan(&path, pattern, recursive, out, depth + 1);
            }
            continue;
        }
        let name = e.file_name().to_string_lossy().to_string();
        // pliki w trakcie pobierania
        if name.ends_with(".crdownload") || name.ends_with(".part") || name.ends_with(".tmp") || name.starts_with("~$") {
            continue;
        }
        if !vars::wildcard_any(pattern, &name) {
            continue;
        }
        let mt = md.modified().ok().and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok()).map(|d| d.as_secs()).unwrap_or(0);
        out.insert(path, (mt, md.len()));
    }
}

fn file_trigger(t: &Block, m: &mut Mem, ev: Event, ts: i64) -> Option<Event> {
    if ts < m.next_scan {
        return None;
    }
    m.next_scan = ts + t.num("every", 3.0).clamp(1.0, 3600.0) as i64;
    let folder = vars::expand_env(t.p("folder"));
    if folder.trim().is_empty() {
        return None;
    }
    let mut now = HashMap::new();
    scan(Path::new(&folder), t.p("pattern"), t.flag("recursive"), &mut now, 0);
    let Some(old) = m.files.replace(now.clone()) else { return None };
    let kind = t.pd("change", "new");
    let mut hit: Option<(&PathBuf, &str)> = None;
    if kind == "new" || kind == "any" {
        hit = now.keys().find(|p| !old.contains_key(*p)).map(|p| (p, "nowy"));
    }
    if hit.is_none() && (kind == "changed" || kind == "any") {
        hit = now.iter().find(|(p, v)| old.get(*p).is_some_and(|o| o != *v)).map(|(p, _)| (p, "zmieniony"));
    }
    if hit.is_none() && (kind == "deleted" || kind == "any") {
        hit = old.keys().find(|p| !now.contains_key(*p)).map(|p| (p, "usunięty"));
    }
    let (p, change) = hit?;
    Some(file_event(ev, p).with("change", change))
}

pub fn file_event(ev: Event, p: &Path) -> Event {
    ev.with("file", p.display().to_string())
        .with("file_name", p.file_name().map(|s| s.to_string_lossy().to_string()).unwrap_or_default())
        .with("file_base", p.file_stem().map(|s| s.to_string_lossy().to_string()).unwrap_or_default())
        .with("file_ext", p.extension().map(|s| s.to_string_lossy().to_lowercase()).unwrap_or_default())
        .with("file_dir", p.parent().map(|s| s.display().to_string()).unwrap_or_default())
}

fn external_match(t: &Block, ev: &Event, r: &Routine) -> bool {
    match t.kind.as_str() {
        "t.hotkey" => crate::hotkeys::normalize(t.p("keys")) == ev.get("keys"),
        "t.event" => t.p("name").trim().eq_ignore_ascii_case(ev.get("event")),
        "t.routine_done" => {
            if ev.get("routine_id") == r.id {
                return false;
            }
            let name = t.p("routine");
            let name_ok = name.trim().is_empty() || name == ev.get("routine_id") || name.eq_ignore_ascii_case(ev.get("routine"));
            let res = t.pd("result", "any");
            name_ok && (res == "any" || res == ev.get("result"))
        }
        "t.var_change" => {
            let n = t.p("name").trim();
            n.is_empty() || n.eq_ignore_ascii_case(ev.get("name"))
        }
        _ => false,
    }
}

fn ev_for(_t: &Block, ev: &Event) -> Event {
    ev.clone()
}

pub fn trigger_label(kind: &str) -> &'static str {
    match kind {
        "t.time" => "o godzinie",
        "t.interval" => "co pewien czas",
        "t.monthday" => "dzień miesiąca",
        "t.once" => "jednorazowo",
        "t.startup" => "start systemu",
        "t.manual" => "ręcznie",
        "t.hotkey" => "skrót klawiszowy",
        "t.idle" => "bezczynność",
        "t.active" => "powrót do komputera",
        "t.lock" => "blokada ekranu",
        "t.power" => "zasilacz",
        "t.battery" => "poziom baterii",
        "t.saver" => "oszczędzanie baterii",
        "t.cpu" => "obciążenie procesora",
        "t.ram" => "zajęta pamięć",
        "t.disk" => "mało miejsca",
        "t.monitors" => "monitory",
        "t.theme" => "motyw Windows",
        "t.fullscreen" => "pełny ekran",
        "t.app_start" => "start aplikacji",
        "t.app_stop" => "zamknięcie aplikacji",
        "t.app_focus" => "aktywna aplikacja",
        "t.window_title" => "tytuł okna",
        "t.network" => "internet",
        "t.wifi" => "Wi-Fi",
        "t.drive" => "dysk USB",
        "t.clipboard" => "schowek",
        "t.file" => "plik w folderze",
        "t.event" => "zdarzenie zewnętrzne",
        "t.routine_done" => "inna rutyna",
        "t.var_change" => "zmiana zmiennej",
        "t.sun" => "słońce",
        "t.host" => "dostępność hosta",
        "t.web_change" => "zmiana strony",
        _ => "zdarzenie",
    }
}
