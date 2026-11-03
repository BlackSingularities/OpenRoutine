//! Zmienne w tekstach ({time}, {battery}, własne), wzorce plików, liczby losowe, ścieżki JSON.

use chrono::{Datelike, Local, Timelike};
use regex::Regex;
use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::OnceLock;

static SEED: AtomicU64 = AtomicU64::new(0);

pub fn rand_u64() -> u64 {
    let mut x = SEED.load(Ordering::Relaxed);
    if x == 0 {
        x = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_nanos() as u64 | 1;
    }
    x ^= x << 13;
    x ^= x >> 7;
    x ^= x << 17;
    SEED.store(x, Ordering::Relaxed);
    x
}

pub fn rand_range(a: i64, b: i64) -> i64 {
    let (lo, hi) = if a <= b { (a, b) } else { (b, a) };
    lo + (rand_u64() % ((hi - lo + 1) as u64)) as i64
}

pub const WEEKDAYS: [&str; 7] = ["poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota", "niedziela"];
pub const MONTHS: [&str; 12] = ["stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia"];

/// Zmienne wbudowane, liczone w chwili użycia.
pub fn builtin(name: &str) -> Option<String> {
    let n = Local::now();
    Some(match name {
        "time" => n.format("%H:%M").to_string(),
        "time_s" => n.format("%H:%M:%S").to_string(),
        "date" => n.format("%Y-%m-%d").to_string(),
        "date_pl" => format!("{} {} {}", n.day(), MONTHS[n.month0() as usize], n.year()),
        "datetime" => n.format("%Y-%m-%d %H:%M").to_string(),
        "stamp" => n.format("%Y%m%d-%H%M%S").to_string(),
        "weekday" => WEEKDAYS[n.weekday().num_days_from_monday() as usize].to_string(),
        "hour" => format!("{:02}", n.hour()),
        "minute" => format!("{:02}", n.minute()),
        "day" => format!("{:02}", n.day()),
        "month" => format!("{:02}", n.month()),
        "year" => n.year().to_string(),
        "week" => n.iso_week().week().to_string(),
        "timestamp" => n.timestamp().to_string(),
        "random" => rand_range(0, 100).to_string(),
        "uuid" => format!("{:016x}{:016x}", rand_u64(), rand_u64()),
        "battery" => crate::sys::power().percent.to_string(),
        "charging" => (if crate::sys::power().ac { "tak" } else { "nie" }).to_string(),
        "idle" => crate::sys::idle_secs().to_string(),
        "uptime_h" => format!("{:.1}", crate::sys::uptime_secs() as f64 / 3600.0),
        "ram" => format!("{:.0}", crate::sys::memory_percent()),
        "volume" => crate::sys::volume().map(|v| v.0.to_string()).unwrap_or_default(),
        "clipboard" => crate::sys::clipboard_get(),
        "foreground_app" => crate::sys::foreground().exe,
        "foreground_title" => crate::sys::foreground().title,
        "online" => (if crate::sys::online() { "tak" } else { "nie" }).to_string(),
        "user" => std::env::var("USERNAME").unwrap_or_default(),
        "computer" => std::env::var("COMPUTERNAME").unwrap_or_default(),
        "home" => std::env::var("USERPROFILE").unwrap_or_default(),
        "desktop" => known_folder("Desktop"),
        "documents" => known_folder("Personal"),
        "downloads" => downloads(),
        "pictures" => known_folder("My Pictures"),
        "temp" => std::env::temp_dir().display().to_string().trim_end_matches('\\').to_string(),
        "appdata" => std::env::var("APPDATA").unwrap_or_default(),
        "nl" => "\n".into(),
        "tab" => "\t".into(),
        _ => return None,
    })
}

fn known_folder(name: &str) -> String {
    let v = crate::sys::reg_get_string(r"Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders", name).unwrap_or_default();
    expand_env(&v)
}

fn downloads() -> String {
    let v = crate::sys::reg_get_string(r"Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders", "{374DE290-123F-4565-9164-39C4925E467B}").unwrap_or_default();
    if v.is_empty() {
        format!("{}\\Downloads", std::env::var("USERPROFILE").unwrap_or_default())
    } else {
        expand_env(&v)
    }
}

/// %USERPROFILE% i inne zmienne środowiskowe.
pub fn expand_env(s: &str) -> String {
    static RE: OnceLock<Regex> = OnceLock::new();
    let re = RE.get_or_init(|| Regex::new(r"%([A-Za-z0-9_()]+)%").unwrap());
    re.replace_all(s, |c: &regex::Captures| std::env::var(&c[1]).unwrap_or_else(|_| c[0].to_string())).into_owned()
}

/// Podstawia {zmienne}: dane zdarzenia, zmienne lokalne przebiegu, globalne i wbudowane.
pub fn expand(text: &str, local: &HashMap<String, String>, global: &std::collections::BTreeMap<String, String>) -> String {
    static RE: OnceLock<Regex> = OnceLock::new();
    let re = RE.get_or_init(|| Regex::new(r"\{([A-Za-z0-9_.]+)\}").unwrap());
    if !text.contains('{') {
        return expand_env(text);
    }
    let s = re.replace_all(text, |c: &regex::Captures| {
        let k = &c[1];
        local.get(k).or_else(|| global.get(k)).cloned().or_else(|| builtin(k)).unwrap_or_else(|| c[0].to_string())
    });
    expand_env(&s)
}

/// Wzorzec nazw plików: „*.pdf;*.jpg”, „raport-??.docx”; pusty = wszystko.
pub fn wildcard_any(patterns: &str, name: &str) -> bool {
    let pats: Vec<&str> = patterns.split([';', ',']).map(|s| s.trim()).filter(|s| !s.is_empty()).collect();
    pats.is_empty() || pats.iter().any(|p| wildcard(p, name))
}

pub fn wildcard(p: &str, s: &str) -> bool {
    let p: Vec<char> = p.to_lowercase().chars().collect();
    let s: Vec<char> = s.to_lowercase().chars().collect();
    let (mut pi, mut si, mut star, mut mark) = (0usize, 0usize, usize::MAX, 0usize);
    while si < s.len() {
        if pi < p.len() && (p[pi] == '?' || p[pi] == s[si]) {
            pi += 1;
            si += 1;
        } else if pi < p.len() && p[pi] == '*' {
            star = pi;
            mark = si;
            pi += 1;
        } else if star != usize::MAX {
            pi = star + 1;
            mark += 1;
            si = mark;
        } else {
            return false;
        }
    }
    while pi < p.len() && p[pi] == '*' {
        pi += 1;
    }
    pi == p.len()
}

/// Porównanie tekstu/liczby jak w warunku „Zmienna”.
pub fn compare(a: &str, op: &str, b: &str) -> bool {
    let num = |s: &str| s.trim().replace(',', ".").parse::<f64>().ok();
    let al = a.to_lowercase();
    let bl = b.to_lowercase();
    match op {
        "eq" => match (num(a), num(b)) {
            (Some(x), Some(y)) => (x - y).abs() < 1e-9,
            _ => al == bl,
        },
        "ne" => !compare(a, "eq", b),
        "contains" => al.contains(&bl),
        "not_contains" => !al.contains(&bl),
        "starts" => al.starts_with(&bl),
        "ends" => al.ends_with(&bl),
        "gt" | "ge" | "lt" | "le" => match (num(a), num(b)) {
            (Some(x), Some(y)) => match op {
                "gt" => x > y,
                "ge" => x >= y,
                "lt" => x < y,
                _ => x <= y,
            },
            _ => false,
        },
        "regex" => Regex::new(b).map(|r| r.is_match(a)).unwrap_or(false),
        "empty" => a.trim().is_empty(),
        "set" => !a.trim().is_empty(),
        "wild" => wildcard(b, a),
        _ => false,
    }
}

/// Ścieżka w JSON: „a.b.0.c”.
pub fn json_path(v: &serde_json::Value, path: &str) -> Option<String> {
    let mut cur = v;
    for part in path.split('.').filter(|p| !p.is_empty()) {
        cur = match cur {
            serde_json::Value::Array(a) => a.get(part.parse::<usize>().ok()?)?,
            serde_json::Value::Object(o) => o.get(part)?,
            _ => return None,
        };
    }
    Some(match cur {
        serde_json::Value::String(s) => s.clone(),
        serde_json::Value::Null => String::new(),
        x => x.to_string(),
    })
}

/// Prosty kalkulator: + - * / % ^ i nawiasy.
pub fn calc(expr: &str) -> Result<f64, String> {
    struct P<'a> {
        s: &'a [u8],
        i: usize,
    }
    impl P<'_> {
        fn ws(&mut self) {
            while self.i < self.s.len() && self.s[self.i].is_ascii_whitespace() {
                self.i += 1;
            }
        }
        fn expr(&mut self) -> Result<f64, String> {
            let mut v = self.term()?;
            loop {
                self.ws();
                match self.s.get(self.i) {
                    Some(b'+') => {
                        self.i += 1;
                        v += self.term()?
                    }
                    Some(b'-') => {
                        self.i += 1;
                        v -= self.term()?
                    }
                    _ => return Ok(v),
                }
            }
        }
        fn term(&mut self) -> Result<f64, String> {
            let mut v = self.pow()?;
            loop {
                self.ws();
                match self.s.get(self.i) {
                    Some(b'*') => {
                        self.i += 1;
                        v *= self.pow()?
                    }
                    Some(b'/') => {
                        self.i += 1;
                        let d = self.pow()?;
                        if d == 0.0 {
                            return Err("dzielenie przez zero".into());
                        }
                        v /= d
                    }
                    Some(b'%') => {
                        self.i += 1;
                        v %= self.pow()?
                    }
                    _ => return Ok(v),
                }
            }
        }
        fn pow(&mut self) -> Result<f64, String> {
            let b = self.atom()?;
            self.ws();
            if self.s.get(self.i) == Some(&b'^') {
                self.i += 1;
                return Ok(b.powf(self.pow()?));
            }
            Ok(b)
        }
        fn atom(&mut self) -> Result<f64, String> {
            self.ws();
            match self.s.get(self.i) {
                Some(b'(') => {
                    self.i += 1;
                    let v = self.expr()?;
                    self.ws();
                    if self.s.get(self.i) != Some(&b')') {
                        return Err("brak nawiasu".into());
                    }
                    self.i += 1;
                    Ok(v)
                }
                Some(b'-') => {
                    self.i += 1;
                    Ok(-self.atom()?)
                }
                _ => {
                    let st = self.i;
                    while self.i < self.s.len() && (self.s[self.i].is_ascii_digit() || self.s[self.i] == b'.') {
                        self.i += 1;
                    }
                    std::str::from_utf8(&self.s[st..self.i]).unwrap().parse().map_err(|_| "niepoprawne wyrażenie".to_string())
                }
            }
        }
    }
    let e = expr.replace(',', ".");
    let mut p = P { s: e.as_bytes(), i: 0 };
    let v = p.expr()?;
    p.ws();
    if p.i != p.s.len() {
        return Err("niepoprawne wyrażenie".into());
    }
    Ok(v)
}

pub fn fmt_num(v: f64) -> String {
    if v.fract().abs() < 1e-9 && v.abs() < 1e15 {
        format!("{}", v.round() as i64)
    } else {
        let s = format!("{:.6}", v);
        s.trim_end_matches('0').trim_end_matches('.').to_string()
    }
}

/// „HH:MM” → minuty od północy.
pub fn hm(s: &str) -> Option<u32> {
    let (h, m) = s.trim().split_once(':')?;
    let (h, m): (u32, u32) = (h.trim().parse().ok()?, m.trim().parse().ok()?);
    (h < 24 && m < 60).then_some(h * 60 + m)
}

/// Dni tygodnia jako cyfry 1 (pon) – 7 (niedz); pusty = codziennie.
pub fn day_ok(days: &str, wd: u32) -> bool {
    days.trim().is_empty() || days.contains(char::from_digit(wd, 10).unwrap_or('0'))
}

pub fn in_window(now: u32, from: u32, to: u32) -> bool {
    if from <= to {
        now >= from && now < to
    } else {
        now >= from || now < to
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn wildcards() {
        assert!(wildcard("*.pdf", "Faktura.PDF"));
        assert!(wildcard("raport-??.docx", "raport-01.docx"));
        assert!(!wildcard("*.pdf", "a.pdfx"));
        assert!(wildcard_any("*.jpg; *.png", "x.png"));
        assert!(wildcard_any("", "cokolwiek"));
    }

    #[test]
    fn calculator() {
        assert_eq!(calc("2+3*4").unwrap(), 14.0);
        assert_eq!(calc("(2+3)*4").unwrap(), 20.0);
        assert_eq!(calc("2^3^2").unwrap(), 512.0);
        assert_eq!(calc("-3 + 5").unwrap(), 2.0);
        assert!(calc("1/0").is_err());
        assert_eq!(fmt_num(2.50), "2.5");
    }

    #[test]
    fn comparisons() {
        assert!(compare("10", "gt", "9"));
        assert!(compare("Ala", "eq", "ala"));
        assert!(compare("abc123", "regex", r"\d+"));
        assert!(compare("", "empty", ""));
    }

    #[test]
    fn time_windows() {
        assert!(in_window(hm("23:30").unwrap(), hm("22:00").unwrap(), hm("06:00").unwrap()));
        assert!(!in_window(hm("12:00").unwrap(), hm("22:00").unwrap(), hm("06:00").unwrap()));
        assert!(day_ok("12345", 3));
        assert!(!day_ok("67", 1));
    }

    #[test]
    fn json() {
        let v: serde_json::Value = serde_json::from_str(r#"{"a":{"b":[{"c":5}]}}"#).unwrap();
        assert_eq!(json_path(&v, "a.b.0.c").unwrap(), "5");
    }
}
