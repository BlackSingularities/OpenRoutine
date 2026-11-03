//! Sprawdzenia sieciowe w tle (host osiągalny, treść strony) – monitor czyta tylko ostatni wynik,
//! więc wolna sieć nie opóźnia pozostałych wyzwalaczy.

use std::collections::HashMap;
use std::net::{TcpStream, ToSocketAddrs};
use std::sync::Mutex;
use std::time::{Duration, Instant};

struct Entry {
    value: Option<String>,
    checked: Instant,
    busy: bool,
}

static CACHE: Mutex<Option<HashMap<String, Entry>>> = Mutex::new(None);

fn cached(key: String, every: Duration, work: impl FnOnce() -> Option<String> + Send + 'static) -> Option<String> {
    let mut g = CACHE.lock().unwrap();
    let m = g.get_or_insert_with(HashMap::new);
    let e = m.entry(key.clone()).or_insert(Entry { value: None, checked: Instant::now() - every * 2, busy: false });
    if !e.busy && e.checked.elapsed() >= every {
        e.busy = true;
        std::thread::spawn(move || {
            let v = work();
            let mut g = CACHE.lock().unwrap();
            if let Some(e) = g.get_or_insert_with(HashMap::new).get_mut(&key) {
                e.value = v;
                e.checked = Instant::now();
                e.busy = false;
            }
        });
    }
    e.value.clone()
}

pub fn reachable_now(host: &str, timeout_ms: u64) -> bool {
    let h = host.trim();
    let addr = if h.contains(':') { h.to_string() } else { format!("{h}:443") };
    addr.to_socket_addrs()
        .ok()
        .and_then(|mut it| it.next())
        .map(|a| TcpStream::connect_timeout(&a, Duration::from_millis(timeout_ms)).is_ok())
        .unwrap_or(false)
}

/// Ostatni znany wynik: Some(true/false) albo None, gdy jeszcze nie sprawdzono.
pub fn host(host: &str, every_s: u64) -> Option<bool> {
    let h = host.to_string();
    cached(format!("host:{host}"), Duration::from_secs(every_s.max(5)), move || Some(if reachable_now(&h, 2000) { "1" } else { "0" }.into())).map(|v| v == "1")
}

/// Treść strony (opcjonalnie wartość ze ścieżki JSON); None przy błędzie lub przed pierwszym pobraniem.
pub fn page(url: &str, json: &str, every_s: u64) -> Option<String> {
    let (u, j) = (url.to_string(), json.trim().to_string());
    cached(format!("page:{url}|{json}"), Duration::from_secs(every_s.max(30)), move || {
        let r = ureq::get(&u).set("User-Agent", "OpenRoutine/Windows").timeout(Duration::from_secs(20)).call().ok()?;
        let body = r.into_string().ok()?;
        if j.is_empty() {
            Some(body)
        } else {
            let v: serde_json::Value = serde_json::from_str(&body).ok()?;
            crate::vars::json_path(&v, &j)
        }
    })
}

// ------------------------------------------------------------------ słońce

/// Wschód i zachód słońca (minuty od północy czasu lokalnego) – algorytm NOAA.
pub fn sun_times(day_of_year: u32, lat: f64, lon: f64, tz_min: f64) -> Option<(f64, f64)> {
    use std::f64::consts::PI;
    let g = 2.0 * PI / 365.0 * (day_of_year as f64 - 1.0);
    let eq = 229.18 * (0.000075 + 0.001868 * g.cos() - 0.032077 * g.sin() - 0.014615 * (2.0 * g).cos() - 0.040849 * (2.0 * g).sin());
    let decl = 0.006918 - 0.399912 * g.cos() + 0.070257 * g.sin() - 0.006758 * (2.0 * g).cos() + 0.000907 * (2.0 * g).sin() - 0.002697 * (3.0 * g).cos() + 0.00148 * (3.0 * g).sin();
    let la = lat.to_radians();
    let c = (90.833f64.to_radians().cos() / (la.cos() * decl.cos())) - la.tan() * decl.tan();
    if !(-1.0..=1.0).contains(&c) {
        return None; // dzień lub noc polarna
    }
    let ha = c.acos().to_degrees();
    let rise = 720.0 - 4.0 * (lon + ha) - eq + tz_min;
    let set = 720.0 - 4.0 * (lon - ha) - eq + tz_min;
    Some((rise.rem_euclid(1440.0), set.rem_euclid(1440.0)))
}

pub fn sun_today(lat: f64, lon: f64) -> Option<(u32, u32)> {
    use chrono::{Datelike, Local, Offset};
    let n = Local::now();
    let tz = n.offset().fix().local_minus_utc() as f64 / 60.0;
    sun_times(n.ordinal(), lat, lon, tz).map(|(a, b)| (a.round() as u32, b.round() as u32))
}

#[cfg(test)]
mod tests {
    #[test]
    fn warsaw_midsummer() {
        // Warszawa, 21 czerwca, UTC+2: wschód ok. 4:14, zachód ok. 21:01
        let (r, s) = super::sun_times(172, 52.23, 21.01, 120.0).unwrap();
        assert!((r - 254.0).abs() < 10.0, "{r}");
        assert!((s - 1261.0).abs() < 10.0, "{s}");
    }
}
