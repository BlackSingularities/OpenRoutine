//! Globalne skróty klawiszowe (RegisterHotKey) w osobnym wątku z pętlą komunikatów.

use crate::engine;
use crate::model::Event;
use std::collections::HashMap;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Mutex;
use windows::Win32::System::Threading::GetCurrentThreadId;
use windows::Win32::UI::Input::KeyboardAndMouse::{RegisterHotKey, UnregisterHotKey, HOT_KEY_MODIFIERS, MOD_ALT, MOD_CONTROL, MOD_NOREPEAT, MOD_SHIFT, MOD_WIN};
use windows::Win32::UI::WindowsAndMessaging::{GetMessageW, PostThreadMessageW, MSG, WM_APP, WM_HOTKEY};
use windows::Win32::Foundation::{LPARAM, WPARAM};

static THREAD: AtomicU32 = AtomicU32::new(0);
static FAILED: Mutex<Vec<String>> = Mutex::new(Vec::new());
const RELOAD: u32 = WM_APP + 7;

/// „Ctrl + Shift + N” → „ctrl+shift+n” (stała kolejność modyfikatorów).
pub fn normalize(s: &str) -> String {
    let parts: Vec<String> = s.split('+').map(|p| p.trim().to_lowercase()).filter(|p| !p.is_empty()).collect();
    let mut mods: Vec<&str> = Vec::new();
    for m in ["ctrl", "alt", "shift", "win"] {
        if parts.iter().any(|p| p == m || (m == "ctrl" && p == "control")) {
            mods.push(m);
        }
    }
    let key = parts.iter().find(|p| !matches!(p.as_str(), "ctrl" | "control" | "alt" | "shift" | "win")).cloned().unwrap_or_default();
    let mut v: Vec<String> = mods.iter().map(|s| s.to_string()).collect();
    v.push(key);
    v.join("+")
}

fn parse(combo: &str) -> Option<(HOT_KEY_MODIFIERS, u32)> {
    let n = normalize(combo);
    let mut m = MOD_NOREPEAT;
    let mut key = None;
    for p in n.split('+') {
        match p {
            "ctrl" => m |= MOD_CONTROL,
            "alt" => m |= MOD_ALT,
            "shift" => m |= MOD_SHIFT,
            "win" => m |= MOD_WIN,
            k => key = crate::sys::vk_from_name(k),
        }
    }
    key.map(|k| (m, k as u32))
}

pub fn failed() -> Vec<String> {
    FAILED.lock().unwrap().clone()
}

pub fn reload() {
    let t = THREAD.load(Ordering::SeqCst);
    if t != 0 {
        unsafe {
            let _ = PostThreadMessageW(t, RELOAD, WPARAM(0), LPARAM(0));
        }
    }
}

fn wanted() -> Vec<String> {
    let e = engine::get();
    let d = e.data.lock().unwrap();
    if !d.settings.engine_on {
        return Vec::new();
    }
    let mut v: Vec<String> = d
        .routines
        .iter()
        .filter(|r| r.enabled)
        .flat_map(|r| r.triggers.iter().filter(|t| !t.off && t.kind == "t.hotkey").map(|t| normalize(t.p("keys"))))
        .filter(|s| !s.ends_with('+') && !s.is_empty())
        .collect();
    v.sort();
    v.dedup();
    v
}

pub fn start() {
    std::thread::Builder::new()
        .name("hotkeys".into())
        .spawn(|| unsafe {
            THREAD.store(GetCurrentThreadId(), Ordering::SeqCst);
            let mut ids: HashMap<i32, String> = HashMap::new();
            let register = |ids: &mut HashMap<i32, String>| {
                for id in ids.keys() {
                    let _ = UnregisterHotKey(None, *id);
                }
                ids.clear();
                let mut bad = Vec::new();
                for (i, c) in wanted().into_iter().enumerate() {
                    let id = 100 + i as i32;
                    match parse(&c) {
                        Some((m, vk)) if RegisterHotKey(None, id, m, vk).is_ok() => {
                            ids.insert(id, c);
                        }
                        _ => bad.push(c),
                    }
                }
                *FAILED.lock().unwrap() = bad;
            };
            register(&mut ids);
            let mut msg = MSG::default();
            while GetMessageW(&mut msg, None, 0, 0).as_bool() {
                if msg.message == RELOAD {
                    register(&mut ids);
                } else if msg.message == WM_HOTKEY {
                    if let Some(c) = ids.get(&(msg.wParam.0 as i32)) {
                        engine::get().fire(Event::new("t.hotkey").with("keys", c.clone()));
                    }
                }
            }
        })
        .ok();
}

#[cfg(test)]
mod tests {
    #[test]
    fn normalizes() {
        assert_eq!(super::normalize("Shift + Ctrl + N"), "ctrl+shift+n");
        assert_eq!(super::normalize("win+alt+F5"), "alt+win+f5");
    }
}
