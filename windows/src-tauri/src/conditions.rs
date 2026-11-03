//! Warunki – sprawdzane w chwili uruchomienia rutyny.

use crate::engine;
use crate::model::{Block, Routine};
use crate::sys;
use crate::vars;
use chrono::{Datelike, Local, Timelike};
use std::collections::HashMap;
use std::net::{TcpStream, ToSocketAddrs};
use std::time::Duration;

fn op_num(op: &str, a: f64, b: f64) -> bool {
    match op {
        "gt" => a > b,
        "ge" => a >= b,
        "le" => a <= b,
        "eq" => (a - b).abs() < 0.5,
        "ne" => (a - b).abs() >= 0.5,
        _ => a < b,
    }
}

fn yes(b: &Block, k: &str, d: &str) -> bool {
    matches!(b.pd(k, d), "yes" | "on" | "1" | "true")
}

/// MM-DD albo RRRR-MM-DD → porównywalna liczba.
fn date_key(s: &str, yearly: bool) -> Option<u32> {
    let parts: Vec<u32> = s.trim().split('-').filter_map(|x| x.parse().ok()).collect();
    match (parts.len(), yearly) {
        (2, _) => Some(parts[0] * 100 + parts[1]),
        (3, true) => Some(parts[1] * 100 + parts[2]),
        (3, false) => Some(parts[0] * 10000 + parts[1] * 100 + parts[2]),
        _ => None,
    }
}

pub fn check(c: &Block, local: &HashMap<String, String>, r: &Routine) -> bool {
    let e = engine::get();
    let snap = e.snap.lock().unwrap().clone();
    let now = Local::now();
    let min_now = now.hour() * 60 + now.minute();
    match c.kind.as_str() {
        "c.time" => match (vars::hm(c.p("from")), vars::hm(c.p("to"))) {
            (Some(a), Some(b)) => vars::in_window(min_now, a, b),
            _ => true,
        },
        "c.days" => vars::day_ok(c.p("days"), now.weekday().number_from_monday()),
        "c.month" => {
            let m = now.month().to_string();
            c.p("months").split([',', ' ', ';']).any(|x| x.trim() == m)
        }
        "c.date" => {
            let yearly = c.p("from").trim().len() <= 5;
            let today = if yearly { now.month() * 100 + now.day() } else { now.year() as u32 * 10000 + now.month() * 100 + now.day() };
            match (date_key(c.p("from"), yearly), date_key(c.p("to"), yearly)) {
                (Some(a), Some(b)) if a <= b => today >= a && today <= b,
                (Some(a), Some(b)) => today >= a || today <= b,
                _ => false,
            }
        }
        "c.battery" => snap.battery >= 0 && op_num(c.pd("op", "lt"), snap.battery as f64, c.num("level", 30.0)),
        "c.power" => (c.pd("state", "ac") == "ac") == snap.ac,
        "c.saver" => yes(c, "state", "on") == snap.saver,
        "c.idle" => op_num(c.pd("op", "ge"), snap.idle as f64 / 60.0, c.num("minutes", 5.0)),
        "c.locked" => yes(c, "state", "yes") == snap.locked,
        "c.app_running" => {
            let procs = sys::processes();
            let p = c.p("app").trim().to_lowercase();
            let name = if p.ends_with(".exe") || p.contains('*') { p } else { format!("{p}.exe") };
            let hit = procs.iter().any(|x| vars::wildcard(&name, x));
            yes(c, "state", "yes") == hit
        }
        "c.app_focus" => {
            let p = c.p("app").trim().to_lowercase();
            let name = if p.ends_with(".exe") || p.contains('*') { p } else { format!("{p}.exe") };
            let t = c.p("title").to_lowercase();
            vars::wildcard(&name, &snap.fg_exe) && (t.is_empty() || snap.fg_title.to_lowercase().contains(&t))
        }
        "c.fullscreen" => yes(c, "state", "yes") == snap.fullscreen,
        "c.network" => (c.pd("state", "online") == "online") == snap.online,
        "c.wifi" => {
            let f = c.p("ssid");
            let ok = if f.trim().is_empty() { !snap.ssid.is_empty() } else { vars::wildcard_any(f, &snap.ssid) };
            yes(c, "state", "yes") == ok
        }
        "c.cpu" => op_num(c.pd("op", "gt"), snap.cpu, c.num("level", 80.0)),
        "c.ram" => op_num(c.pd("op", "gt"), snap.ram, c.num("level", 80.0)),
        "c.disk" => sys::disk_free(c.pd("drive", "C")).map(|(_, gb)| op_num(c.pd("op", "lt"), gb, c.num("gb", 20.0))).unwrap_or(false),
        "c.var" => {
            let name = c.p("name").trim().trim_matches(['{', '}']);
            let g = e.data.lock().unwrap().vars.clone();
            let val = local.get(name).cloned().or_else(|| g.get(name).cloned()).or_else(|| vars::builtin(name)).unwrap_or_default();
            vars::compare(&val, c.pd("op", "eq"), &vars::expand(c.p("value"), local, &g))
        }
        "c.file" => {
            let g = e.data.lock().unwrap().vars.clone();
            let p = vars::expand(c.p("path"), local, &g);
            yes(c, "state", "yes") == std::path::Path::new(&p).exists()
        }
        "c.drive" => {
            let f = c.p("drive").trim().to_uppercase();
            let hit = snap.drives.iter().any(|d| d.letter.starts_with(&f) || (!d.label.is_empty() && vars::wildcard_any(c.p("drive"), &d.label)));
            yes(c, "state", "yes") == hit
        }
        "c.monitors" => op_num(c.pd("op", "ge"), snap.monitors as f64, c.num("count", 2.0)),
        "c.volume" => op_num(c.pd("op", "gt"), snap.volume as f64, c.num("level", 50.0)),
        "c.muted" => yes(c, "state", "yes") == snap.muted,
        "c.theme" => (c.pd("mode", "dark") == "dark") == snap.dark,
        "c.random" => (vars::rand_range(0, 99) as f64) < c.num("percent", 50.0),
        "c.since_last" => r.last_run == 0 || (engine::now_ms() - r.last_run) as f64 >= c.num("minutes", 60.0) * 60_000.0,
        "c.routine" => {
            let d = e.data.lock().unwrap();
            let name = c.p("routine");
            let on = d.routines.iter().find(|x| x.id == name || x.name.eq_ignore_ascii_case(name)).map(|x| x.enabled).unwrap_or(false);
            yes(c, "state", "on") == on
        }
        "c.uptime" => op_num(c.pd("op", "gt"), snap.uptime as f64 / 3600.0, c.num("hours", 8.0)),
        "c.computer" => {
            let name = std::env::var("COMPUTERNAME").unwrap_or_default();
            vars::wildcard_any(c.p("name"), &name)
        }
        "c.host" => {
            let host = c.p("host").trim().to_string();
            let addr = if host.contains(':') { host } else { format!("{host}:443") };
            let ok = addr
                .to_socket_addrs()
                .ok()
                .and_then(|mut it| it.next())
                .map(|a| TcpStream::connect_timeout(&a, Duration::from_millis(c.num("timeout", 1500.0) as u64)).is_ok())
                .unwrap_or(false);
            yes(c, "state", "yes") == ok
        }
        "c.sun" => {
            let (lat, lon) = engine::location();
            let Some((rise, set)) = crate::probe::sun_today(lat, lon) else { return false };
            let day = min_now >= rise && min_now < set;
            (c.pd("state", "day") == "day") == day
        }
        "c.monthday" => {
            let last = {
                let (y, m) = (now.year(), now.month());
                let nm = if m == 12 { chrono::NaiveDate::from_ymd_opt(y + 1, 1, 1) } else { chrono::NaiveDate::from_ymd_opt(y, m + 1, 1) };
                nm.and_then(|d| d.pred_opt()).map(|d| d.day()).unwrap_or(28)
            };
            let d = now.day();
            c.p("days").split([',', ' ', ';']).map(|x| x.trim()).any(|x| x == d.to_string() || (x == "last" && d == last) || (x == "ostatni" && d == last))
        }
        "c.clipboard" => {
            let text = sys::clipboard_get();
            let f = c.p("text");
            let hit = if c.pd("match", "contains") == "regex" { regex::Regex::new(f).map(|r| r.is_match(&text)).unwrap_or(false) } else { text.to_lowercase().contains(&f.to_lowercase()) };
            yes(c, "state", "yes") == hit
        }
        "c.window" => {
            let t = c.p("title").to_lowercase();
            let hit = sys::windows_list().iter().any(|(x, _)| x.to_lowercase().contains(&t));
            yes(c, "state", "yes") == hit
        }
        _ => true,
    }
}
