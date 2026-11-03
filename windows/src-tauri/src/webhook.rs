//! Lokalny serwer HTTP (tylko 127.0.0.1) dla wyzwalacza „Zdarzenie zewnętrzne”:
//!   GET/POST http://127.0.0.1:8765/event/NAZWA?token=TOKEN&extra=...
//!   GET/POST http://127.0.0.1:8765/run/NAZWA_RUTYNY?token=TOKEN

use crate::engine::{self, RunMode};
use crate::model::Event;
use std::io::Read;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Duration;

static GEN: AtomicU64 = AtomicU64::new(0);

fn decode(s: &str) -> String {
    let b = s.as_bytes();
    let mut out = Vec::with_capacity(b.len());
    let mut i = 0;
    while i < b.len() {
        match b[i] {
            b'+' => out.push(b' '),
            b'%' if i + 2 < b.len() => {
                if let Ok(v) = u8::from_str_radix(&s[i + 1..i + 3], 16) {
                    out.push(v);
                    i += 2;
                } else {
                    out.push(b'%');
                }
            }
            c => out.push(c),
        }
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

/// Uruchamia (lub zatrzymuje) serwer zgodnie z ustawieniami.
pub fn restart() {
    let g = GEN.fetch_add(1, Ordering::SeqCst) + 1;
    let (on, port) = {
        let d = engine::get().data.lock().unwrap();
        (d.settings.webhook_on, d.settings.webhook_port)
    };
    if !on {
        return;
    }
    std::thread::spawn(move || {
        // poprzedni serwer zwalnia port w ciągu sekundy
        let mut server = None;
        for _ in 0..10 {
            if let Ok(s) = tiny_http::Server::http(("127.0.0.1", port)) {
                server = Some(s);
                break;
            }
            std::thread::sleep(Duration::from_millis(300));
        }
        let Some(server) = server else {
            engine::get().log("OpenRoutine", format!("Nie można uruchomić serwera HTTP na porcie {port}"), false);
            return;
        };
        while GEN.load(Ordering::SeqCst) == g {
            let Ok(Some(mut req)) = server.recv_timeout(Duration::from_millis(800)) else { continue };
            let url = req.url().to_string();
            let (path, query) = url.split_once('?').unwrap_or((&url, ""));
            let mut q: Vec<(String, String)> = query.split('&').filter_map(|kv| kv.split_once('=').map(|(k, v)| (decode(k), decode(v)))).collect();
            let header_token = req.headers().iter().find(|h| h.field.equiv("X-Token")).map(|h| h.value.to_string());
            let mut body = String::new();
            let _ = req.as_reader().take(1_000_000).read_to_string(&mut body);
            let e = engine::get();
            let token = e.data.lock().unwrap().settings.token.clone();
            let given = header_token.or_else(|| q.iter().find(|(k, _)| k == "token").map(|(_, v)| v.clone())).unwrap_or_default();
            let parts: Vec<String> = path.trim_matches('/').splitn(2, '/').map(decode).collect();
            let (status, msg) = if parts.first().map(|s| s.as_str()) == Some("status") {
                (200, format!("{{\"ok\":true,\"app\":\"OpenRoutine\",\"version\":\"{}\"}}", env!("CARGO_PKG_VERSION")))
            } else if given != token {
                (403, "{\"ok\":false,\"error\":\"token\"}".to_string())
            } else {
                q.retain(|(k, _)| k != "token");
                let name = parts.get(1).cloned().unwrap_or_default();
                let mut ev = Event::new("t.event").with("event", name.clone()).with("body", body.clone()).with("source", "http");
                let extra = q.iter().find(|(k, _)| k == "extra").map(|(_, v)| v.clone()).unwrap_or_else(|| body.clone());
                ev = ev.with("extra", extra);
                for (k, v) in &q {
                    ev = ev.with(&format!("q_{k}"), v.clone());
                }
                match parts.first().map(|s| s.as_str()) {
                    Some("event") if !name.is_empty() => {
                        e.fire(ev);
                        (200, "{\"ok\":true}".to_string())
                    }
                    Some("run") => match e.find_routine(&name) {
                        Some(id) => {
                            ev.kind = "t.manual".into();
                            e.run(&id, ev, RunMode::Manual);
                            (200, "{\"ok\":true}".to_string())
                        }
                        None => (404, "{\"ok\":false,\"error\":\"routine\"}".to_string()),
                    },
                    _ => (404, "{\"ok\":false,\"error\":\"path\"}".to_string()),
                }
            };
            let h = tiny_http::Header::from_bytes(&b"Content-Type"[..], &b"application/json"[..]).unwrap();
            let _ = req.respond(tiny_http::Response::from_string(msg).with_status_code(status).with_header(h));
        }
    });
}
