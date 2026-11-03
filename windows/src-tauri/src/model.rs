//! Rutyny, dziennik i ustawienia – zapisywane jako JSON w %APPDATA%\OpenRoutine.

use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, HashMap, VecDeque};

pub fn new_id() -> String {
    let n = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_nanos();
    let r = crate::vars::rand_u64();
    format!("{:x}", (n as u64) ^ r).chars().take(10).collect()
}

/// Klocek rutyny: wyzwalacz (`t.*`), warunek (`c.*`) albo akcja (`a.*`).
#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct Block {
    #[serde(default = "new_id")]
    pub id: String,
    #[serde(rename = "type")]
    pub kind: String,
    #[serde(default)]
    pub params: HashMap<String, String>,
    #[serde(default)]
    pub off: bool,
}

impl Block {
    pub fn p(&self, k: &str) -> &str {
        self.params.get(k).map(|s| s.as_str()).unwrap_or("")
    }
    pub fn pd<'a>(&'a self, k: &str, d: &'a str) -> &'a str {
        match self.params.get(k) {
            Some(v) if !v.is_empty() => v,
            _ => d,
        }
    }
    pub fn num(&self, k: &str, d: f64) -> f64 {
        self.p(k).trim().replace(',', ".").parse().unwrap_or(d)
    }
    pub fn flag(&self, k: &str) -> bool {
        matches!(self.p(k), "1" | "true" | "yes" | "on")
    }
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Routine {
    #[serde(default = "new_id")]
    pub id: String,
    #[serde(default)]
    pub name: String,
    #[serde(default = "yes")]
    pub enabled: bool,
    #[serde(default)]
    pub group: String,
    #[serde(default)]
    pub notes: String,
    #[serde(default)]
    pub triggers: Vec<Block>,
    #[serde(default)]
    pub conditions: Vec<Block>,
    #[serde(default = "yes")]
    pub match_all: bool,
    #[serde(default)]
    pub actions: Vec<Block>,
    #[serde(default)]
    pub cooldown_sec: u64,
    #[serde(default)]
    pub stop_on_error: bool,
    #[serde(default)]
    pub quiet: bool,
    #[serde(default)]
    pub last_run: i64,
    #[serde(default)]
    pub runs: u64,
    #[serde(default)]
    pub last_ok: bool,
}

fn yes() -> bool {
    true
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct LogEntry {
    pub time: i64,
    pub routine: String,
    pub message: String,
    pub ok: bool,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Settings {
    #[serde(default = "yes")]
    pub autostart: bool,
    #[serde(default = "yes")]
    pub close_to_tray: bool,
    #[serde(default)]
    pub start_minimized: bool,
    #[serde(default = "yes")]
    pub engine_on: bool,
    #[serde(default)]
    pub webhook_on: bool,
    #[serde(default = "default_port")]
    pub webhook_port: u16,
    #[serde(default)]
    pub token: String,
    #[serde(default = "yes")]
    pub notify_errors: bool,
    #[serde(default = "default_log")]
    pub log_limit: usize,
    #[serde(default = "default_theme")]
    pub theme: String,
    #[serde(default)]
    pub custom_color: String,
    #[serde(default = "yes")]
    pub check_updates: bool,
    #[serde(default)]
    pub skipped_version: String,
    #[serde(default)]
    pub onboarded: bool,
    #[serde(default)]
    pub density: String,
}

fn default_port() -> u16 {
    8765
}
fn default_log() -> usize {
    500
}
fn default_theme() -> String {
    "dark".into()
}

impl Default for Settings {
    fn default() -> Self {
        serde_json::from_str("{}").unwrap()
    }
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct Data {
    #[serde(default)]
    pub routines: Vec<Routine>,
    #[serde(default)]
    pub vars: BTreeMap<String, String>,
    #[serde(default)]
    pub settings: Settings,
    #[serde(default)]
    pub log: VecDeque<LogEntry>,
}

/// Zdarzenie przekazywane do silnika.
#[derive(Clone, Debug, Default)]
pub struct Event {
    pub kind: String,
    pub data: HashMap<String, String>,
}

impl Event {
    pub fn new(kind: &str) -> Self {
        Self { kind: kind.into(), data: HashMap::new() }
    }
    pub fn with(mut self, k: &str, v: impl Into<String>) -> Self {
        self.data.insert(k.into(), v.into());
        self
    }
    pub fn get(&self, k: &str) -> &str {
        self.data.get(k).map(|s| s.as_str()).unwrap_or("")
    }
}
