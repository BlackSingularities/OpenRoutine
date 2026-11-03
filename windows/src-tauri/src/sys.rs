//! Odczyt stanu Windows i operacje systemowe (WinAPI, rejestr użytkownika, PowerShell).

use std::collections::HashSet;
use std::os::windows::process::CommandExt;
use std::process::Command;

use windows::core::{w, BOOL, HSTRING, PCWSTR, PWSTR};
use windows::Win32::Foundation::{CloseHandle, HANDLE, HWND, LPARAM, RECT, WPARAM};
use windows::Win32::Media::Audio::Endpoints::IAudioEndpointVolume;
use windows::Win32::Media::Audio::{eConsole, eRender, IMMDeviceEnumerator, MMDeviceEnumerator};
use windows::Win32::Storage::FileSystem::{GetDiskFreeSpaceExW, GetDriveTypeW, GetLogicalDrives, GetVolumeInformationW};
use windows::Win32::System::Com::{CoCreateInstance, CoInitializeEx, CLSCTX_ALL, COINIT_MULTITHREADED};
use windows::Win32::System::DataExchange::{CloseClipboard, EmptyClipboard, GetClipboardData, GetClipboardSequenceNumber, OpenClipboard, SetClipboardData};
use windows::Win32::System::Diagnostics::ToolHelp::{CreateToolhelp32Snapshot, Process32FirstW, Process32NextW, PROCESSENTRY32W, TH32CS_SNAPPROCESS};
use windows::Win32::System::Memory::{GlobalAlloc, GlobalLock, GlobalUnlock, GMEM_MOVEABLE};
use windows::Win32::System::Power::{GetSystemPowerStatus, SetSuspendState, SYSTEM_POWER_STATUS};
use windows::Win32::System::Registry::{RegGetValueW, RegDeleteKeyValueW, RegSetKeyValueW, HKEY_CURRENT_USER, REG_DWORD, REG_SZ, RRF_RT_REG_DWORD, RRF_RT_REG_SZ};
use windows::Win32::System::Shutdown::LockWorkStation;
use windows::Win32::System::StationsAndDesktops::{CloseDesktop, OpenInputDesktop, SwitchDesktop, DESKTOP_CONTROL_FLAGS, DESKTOP_SWITCHDESKTOP};
use windows::Win32::System::SystemInformation::{GetTickCount64, GlobalMemoryStatusEx, MEMORYSTATUSEX};
use windows::Win32::System::Threading::{GetSystemTimes, OpenProcess, QueryFullProcessImageNameW, PROCESS_NAME_WIN32, PROCESS_QUERY_LIMITED_INFORMATION};
use windows::Win32::Networking::WinInet::{InternetGetConnectedState, INTERNET_CONNECTION};
use windows::Win32::Graphics::Gdi::{GetMonitorInfoW, MonitorFromWindow, MONITORINFO, MONITOR_DEFAULTTONEAREST};
use windows::Win32::UI::Input::KeyboardAndMouse::*;
use windows::Win32::UI::Shell::SHEmptyRecycleBinW;
use windows::Win32::UI::WindowsAndMessaging::*;

const CREATE_NO_WINDOW: u32 = 0x0800_0000;

fn wide(s: &str) -> Vec<u16> {
    s.encode_utf16().chain(std::iter::once(0)).collect()
}

// ---------------------------------------------------------------- zasilanie

pub struct Power {
    pub percent: i32,
    pub ac: bool,
    pub has_battery: bool,
    pub saver: bool,
}

pub fn power() -> Power {
    let mut s = SYSTEM_POWER_STATUS::default();
    unsafe {
        let _ = GetSystemPowerStatus(&mut s);
    }
    let has_battery = s.BatteryFlag != 128 && s.BatteryFlag != 255;
    Power {
        percent: if has_battery && s.BatteryLifePercent <= 100 { s.BatteryLifePercent as i32 } else { -1 },
        ac: s.ACLineStatus == 1 || !has_battery,
        has_battery,
        saver: s.SystemStatusFlag == 1,
    }
}

pub fn idle_secs() -> u64 {
    let mut li = LASTINPUTINFO { cbSize: std::mem::size_of::<LASTINPUTINFO>() as u32, dwTime: 0 };
    unsafe {
        if GetLastInputInfo(&mut li).as_bool() {
            let now = GetTickCount64() as u32;
            return (now.wrapping_sub(li.dwTime) / 1000) as u64;
        }
    }
    0
}

pub fn uptime_secs() -> u64 {
    unsafe { GetTickCount64() / 1000 }
}

// ---------------------------------------------------------------- procesy i okna

fn process_path(pid: u32) -> String {
    unsafe {
        let Ok(h) = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid) else { return String::new() };
        let mut buf = [0u16; 1024];
        let mut len = buf.len() as u32;
        let r = QueryFullProcessImageNameW(h, PROCESS_NAME_WIN32, PWSTR(buf.as_mut_ptr()), &mut len);
        let _ = CloseHandle(h);
        if r.is_ok() {
            String::from_utf16_lossy(&buf[..len as usize])
        } else {
            String::new()
        }
    }
}

pub fn exe_name(path: &str) -> String {
    path.rsplit(['\\', '/']).next().unwrap_or("").to_lowercase()
}

pub struct Foreground {
    pub exe: String,
    pub title: String,
    pub fullscreen: bool,
}

pub fn foreground() -> Foreground {
    unsafe {
        let hwnd = GetForegroundWindow();
        if hwnd.0.is_null() {
            return Foreground { exe: String::new(), title: String::new(), fullscreen: false };
        }
        let mut buf = [0u16; 512];
        let n = GetWindowTextW(hwnd, &mut buf);
        let title = String::from_utf16_lossy(&buf[..n.max(0) as usize]);
        let mut pid = 0u32;
        GetWindowThreadProcessId(hwnd, Some(&mut pid));
        let exe = exe_name(&process_path(pid));
        Foreground { fullscreen: is_fullscreen(hwnd, &exe), exe, title }
    }
}

unsafe fn is_fullscreen(hwnd: HWND, exe: &str) -> bool {
    if hwnd == GetDesktopWindow() || hwnd == GetShellWindow() || exe == "explorer.exe" || exe.is_empty() {
        return false;
    }
    let mut r = RECT::default();
    if GetWindowRect(hwnd, &mut r).is_err() {
        return false;
    }
    let mon = MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST);
    let mut mi = MONITORINFO { cbSize: std::mem::size_of::<MONITORINFO>() as u32, ..Default::default() };
    if !GetMonitorInfoW(mon, &mut mi).as_bool() {
        return false;
    }
    let m = mi.rcMonitor;
    r.left <= m.left && r.top <= m.top && r.right >= m.right && r.bottom >= m.bottom
}

pub fn processes() -> HashSet<String> {
    let mut out = HashSet::new();
    unsafe {
        let Ok(snap) = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0) else { return out };
        let mut e = PROCESSENTRY32W { dwSize: std::mem::size_of::<PROCESSENTRY32W>() as u32, ..Default::default() };
        if Process32FirstW(snap, &mut e).is_ok() {
            loop {
                let len = e.szExeFile.iter().position(|&c| c == 0).unwrap_or(e.szExeFile.len());
                out.insert(String::from_utf16_lossy(&e.szExeFile[..len]).to_lowercase());
                if Process32NextW(snap, &mut e).is_err() {
                    break;
                }
            }
        }
        let _ = CloseHandle(snap);
    }
    out
}

/// Widoczne okna najwyższego poziomu: (tytuł, hwnd).
pub fn windows_list() -> Vec<(String, isize)> {
    unsafe extern "system" fn cb(hwnd: HWND, lp: LPARAM) -> BOOL {
        let v = &mut *(lp.0 as *mut Vec<(String, isize)>);
        if IsWindowVisible(hwnd).as_bool() {
            let mut buf = [0u16; 512];
            let n = GetWindowTextW(hwnd, &mut buf);
            if n > 0 {
                v.push((String::from_utf16_lossy(&buf[..n as usize]), hwnd.0 as isize));
            }
        }
        BOOL(1)
    }
    let mut v: Vec<(String, isize)> = Vec::new();
    unsafe {
        let _ = EnumWindows(Some(cb), LPARAM(&mut v as *mut _ as isize));
    }
    v
}

/// Działanie na oknach, których tytuł zawiera tekst: minimize | maximize | restore | close | focus.
pub fn window_op(title_part: &str, op: &str) -> usize {
    let needle = title_part.to_lowercase();
    let mut n = 0;
    for (t, h) in windows_list() {
        if !needle.is_empty() && !t.to_lowercase().contains(&needle) {
            continue;
        }
        let hwnd = HWND(h as *mut _);
        unsafe {
            match op {
                "minimize" => {
                    let _ = ShowWindow(hwnd, SW_MINIMIZE);
                }
                "maximize" => {
                    let _ = ShowWindow(hwnd, SW_MAXIMIZE);
                }
                "restore" => {
                    let _ = ShowWindow(hwnd, SW_RESTORE);
                }
                "close" => {
                    let _ = PostMessageW(Some(hwnd), WM_CLOSE, WPARAM(0), LPARAM(0));
                }
                _ => {
                    let _ = ShowWindow(hwnd, SW_RESTORE);
                    let _ = SetForegroundWindow(hwnd);
                }
            }
        }
        n += 1;
        if op == "focus" {
            break;
        }
    }
    n
}

pub fn minimize_all() {
    // Win+D
    send_combo("win+d");
}

// ---------------------------------------------------------------- CPU, pamięć, dyski

#[derive(Default)]
pub struct CpuSampler {
    last: Option<(u64, u64)>,
}

fn ft(f: windows::Win32::Foundation::FILETIME) -> u64 {
    ((f.dwHighDateTime as u64) << 32) | f.dwLowDateTime as u64
}

impl CpuSampler {
    pub fn sample(&mut self) -> f64 {
        let (mut i, mut k, mut u) = Default::default();
        unsafe {
            if GetSystemTimes(Some(&mut i), Some(&mut k), Some(&mut u)).is_err() {
                return 0.0;
            }
        }
        let idle = ft(i);
        let total = ft(k) + ft(u);
        let r = match self.last {
            Some((li, lt)) if total > lt => {
                let dt = (total - lt) as f64;
                let di = idle.saturating_sub(li) as f64;
                ((1.0 - di / dt) * 100.0).clamp(0.0, 100.0)
            }
            _ => 0.0,
        };
        self.last = Some((idle, total));
        r
    }
}

pub fn memory_percent() -> f64 {
    let mut m = MEMORYSTATUSEX { dwLength: std::mem::size_of::<MEMORYSTATUSEX>() as u32, ..Default::default() };
    unsafe {
        if GlobalMemoryStatusEx(&mut m).is_ok() {
            return m.dwMemoryLoad as f64;
        }
    }
    0.0
}

#[derive(Clone, Debug, PartialEq)]
pub struct Drive {
    pub letter: char,
    pub removable: bool,
    pub label: String,
}

pub fn drives() -> Vec<Drive> {
    let mask = unsafe { GetLogicalDrives() };
    let mut v = Vec::new();
    for i in 0..26 {
        if mask & (1 << i) == 0 {
            continue;
        }
        let letter = (b'A' + i as u8) as char;
        let root = HSTRING::from(format!("{letter}:\\"));
        let t = unsafe { GetDriveTypeW(&root) };
        if t == 1 || t == 0 {
            continue;
        }
        let mut name = [0u16; 128];
        let label = unsafe {
            if GetVolumeInformationW(&root, Some(&mut name), None, None, None, None).is_ok() {
                let n = name.iter().position(|&c| c == 0).unwrap_or(0);
                String::from_utf16_lossy(&name[..n])
            } else {
                String::new()
            }
        };
        v.push(Drive { letter, removable: t == 2, label });
    }
    v
}

/// Wolne miejsce na dysku w procentach i GB.
pub fn disk_free(letter: &str) -> Option<(f64, f64)> {
    let l = letter.trim().chars().next()?.to_ascii_uppercase();
    let root = HSTRING::from(format!("{l}:\\"));
    let (mut free, mut total) = (0u64, 0u64);
    unsafe {
        GetDiskFreeSpaceExW(&root, Some(&mut free), Some(&mut total), None).ok()?;
    }
    if total == 0 {
        return None;
    }
    Some((free as f64 * 100.0 / total as f64, free as f64 / 1_073_741_824.0))
}

// ---------------------------------------------------------------- sieć, sesja, ekran

pub fn online() -> bool {
    let mut f = INTERNET_CONNECTION(0);
    unsafe { InternetGetConnectedState(&mut f, None).is_ok() }
}

pub fn wifi_ssid() -> String {
    let out = run_hidden("netsh", &["wlan", "show", "interfaces"]).unwrap_or_default();
    for line in out.lines() {
        let l = line.trim();
        if l.starts_with("SSID") && !l.starts_with("BSSID") {
            if let Some((_, v)) = l.split_once(':') {
                return v.trim().to_string();
            }
        }
    }
    String::new()
}

pub fn locked() -> bool {
    unsafe {
        match OpenInputDesktop(DESKTOP_CONTROL_FLAGS(0), false, DESKTOP_SWITCHDESKTOP) {
            Ok(h) => {
                let ok = SwitchDesktop(h).is_ok();
                let _ = CloseDesktop(h);
                !ok
            }
            Err(_) => true,
        }
    }
}

pub fn monitors() -> i32 {
    unsafe { GetSystemMetrics(SM_CMONITORS) }
}

pub fn lock() -> Result<(), String> {
    unsafe { LockWorkStation().map_err(|e| e.to_string()) }
}

pub fn sleep(hibernate: bool) -> Result<(), String> {
    if unsafe { SetSuspendState(hibernate, false, false) } {
        Ok(())
    } else {
        Err("system odmówił uśpienia".into())
    }
}

pub fn shutdown(mode: &str, delay: u32) -> Result<(), String> {
    let d = delay.to_string();
    let args: Vec<&str> = match mode {
        "restart" => vec!["/r", "/t", &d],
        "logoff" => vec!["/l"],
        "cancel" => vec!["/a"],
        _ => vec!["/s", "/t", &d],
    };
    run_hidden("shutdown", &args).map(|_| ())
}

pub fn monitor_off() {
    unsafe {
        let _ = PostMessageW(Some(HWND_BROADCAST), WM_SYSCOMMAND, WPARAM(SC_MONITORPOWER as usize), LPARAM(2));
    }
}

pub fn empty_recycle_bin() -> Result<(), String> {
    // SHERB_NOCONFIRMATION | SHERB_NOPROGRESSUI | SHERB_NOSOUND
    unsafe { SHEmptyRecycleBinW(None, PCWSTR::null(), 0x1 | 0x2 | 0x4).map_err(|e| e.to_string()) }
}

pub fn message_box(title: &str, text: &str) {
    let (t, x) = (HSTRING::from(title), HSTRING::from(text));
    unsafe {
        MessageBoxW(None, &x, &t, MB_OK | MB_ICONINFORMATION | MB_TOPMOST | MB_SETFOREGROUND);
    }
}

/// Pytanie Tak/Nie na wierzchu wszystkich okien.
pub fn confirm(title: &str, text: &str) -> bool {
    let (t, x) = (HSTRING::from(title), HSTRING::from(text));
    unsafe { MessageBoxW(None, &x, &t, MB_YESNO | MB_ICONQUESTION | MB_TOPMOST | MB_SETFOREGROUND) == IDYES }
}

pub fn beep(kind: &str) {
    let s = match kind {
        "error" => MB_ICONHAND,
        "warning" => MB_ICONEXCLAMATION,
        "question" => MB_ICONQUESTION,
        "info" => MB_ICONASTERISK,
        _ => MB_OK,
    };
    unsafe {
        let _ = windows::Win32::System::Diagnostics::Debug::MessageBeep(s);
    }
}

pub fn tone(freq: u32, ms: u32) {
    unsafe {
        let _ = windows::Win32::System::Diagnostics::Debug::Beep(freq.clamp(37, 32767), ms.min(10_000));
    }
}

pub fn set_wallpaper(path: &str) -> Result<(), String> {
    let mut p = wide(path);
    unsafe {
        SystemParametersInfoW(SPI_SETDESKWALLPAPER, 0, Some(p.as_mut_ptr() as *mut _), SPIF_UPDATEINIFILE | SPIF_SENDCHANGE).map_err(|e| e.to_string())
    }
}

// ---------------------------------------------------------------- rejestr użytkownika

pub fn reg_get_dword(key: &str, name: &str) -> Option<u32> {
    let (k, n) = (HSTRING::from(key), HSTRING::from(name));
    let mut v: u32 = 0;
    let mut sz = 4u32;
    let r = unsafe { RegGetValueW(HKEY_CURRENT_USER, &k, &n, RRF_RT_REG_DWORD, None, Some(&mut v as *mut _ as *mut _), Some(&mut sz)) };
    r.is_ok().then_some(v)
}

pub fn reg_set_dword(key: &str, name: &str, v: u32) -> bool {
    let (k, n) = (HSTRING::from(key), HSTRING::from(name));
    unsafe { RegSetKeyValueW(HKEY_CURRENT_USER, &k, &n, REG_DWORD.0, Some(&v as *const _ as *const _), 4).is_ok() }
}

pub fn reg_get_string(key: &str, name: &str) -> Option<String> {
    let (k, n) = (HSTRING::from(key), HSTRING::from(name));
    let mut buf = vec![0u16; 2048];
    let mut sz = (buf.len() * 2) as u32;
    let r = unsafe { RegGetValueW(HKEY_CURRENT_USER, &k, &n, RRF_RT_REG_SZ, None, Some(buf.as_mut_ptr() as *mut _), Some(&mut sz)) };
    if r.is_err() {
        return None;
    }
    let len = buf.iter().position(|&c| c == 0).unwrap_or(0);
    Some(String::from_utf16_lossy(&buf[..len]))
}

pub fn reg_set_string(key: &str, name: &str, v: &str) -> bool {
    let (k, n) = (HSTRING::from(key), HSTRING::from(name));
    let data = wide(v);
    unsafe { RegSetKeyValueW(HKEY_CURRENT_USER, &k, &n, REG_SZ.0, Some(data.as_ptr() as *const _), (data.len() * 2) as u32).is_ok() }
}

pub fn reg_delete(key: &str, name: &str) {
    let (k, n) = (HSTRING::from(key), HSTRING::from(name));
    unsafe {
        let _ = RegDeleteKeyValueW(HKEY_CURRENT_USER, &k, &n);
    }
}

const PERSONALIZE: &str = r"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize";
const RUN: &str = r"Software\Microsoft\Windows\CurrentVersion\Run";

pub fn dark_mode() -> bool {
    reg_get_dword(PERSONALIZE, "AppsUseLightTheme").map(|v| v == 0).unwrap_or(false)
}

pub fn set_dark_mode(dark: bool, apps: bool, system: bool) {
    let v = if dark { 0 } else { 1 };
    if apps {
        reg_set_dword(PERSONALIZE, "AppsUseLightTheme", v);
    }
    if system {
        reg_set_dword(PERSONALIZE, "SystemUsesLightTheme", v);
    }
    let s = wide("ImmersiveColorSet");
    unsafe {
        SendMessageTimeoutW(HWND_BROADCAST, WM_SETTINGCHANGE, WPARAM(0), LPARAM(s.as_ptr() as isize), SMTO_ABORTIFHUNG, 200, None);
    }
}

pub fn set_autostart(on: bool) {
    if on {
        if let Ok(exe) = std::env::current_exe() {
            reg_set_string(RUN, "OpenRoutine", &format!("\"{}\" --autostart", exe.display()));
        }
    } else {
        reg_delete(RUN, "OpenRoutine");
    }
}

pub fn autostart_enabled() -> bool {
    reg_get_string(RUN, "OpenRoutine").is_some()
}

// ---------------------------------------------------------------- schowek

pub fn clipboard_seq() -> u32 {
    unsafe { GetClipboardSequenceNumber() }
}

const CF_UNICODETEXT: u32 = 13;

pub fn clipboard_get() -> String {
    unsafe {
        if OpenClipboard(None).is_err() {
            return String::new();
        }
        let mut s = String::new();
        if let Ok(h) = GetClipboardData(CF_UNICODETEXT) {
            let g = windows::Win32::Foundation::HGLOBAL(h.0);
            let p = GlobalLock(g) as *const u16;
            if !p.is_null() {
                let mut n = 0;
                while *p.add(n) != 0 && n < 4_000_000 {
                    n += 1;
                }
                s = String::from_utf16_lossy(std::slice::from_raw_parts(p, n));
                let _ = GlobalUnlock(g);
            }
        }
        let _ = CloseClipboard();
        s
    }
}

pub fn clipboard_set(text: &str) -> Result<(), String> {
    let data = wide(text);
    unsafe {
        for _ in 0..10 {
            if OpenClipboard(None).is_ok() {
                let _ = EmptyClipboard();
                let g = GlobalAlloc(GMEM_MOVEABLE, data.len() * 2).map_err(|e| e.to_string())?;
                let p = GlobalLock(g) as *mut u16;
                std::ptr::copy_nonoverlapping(data.as_ptr(), p, data.len());
                let _ = GlobalUnlock(g);
                let r = SetClipboardData(CF_UNICODETEXT, Some(HANDLE(g.0)));
                let _ = CloseClipboard();
                return r.map(|_| ()).map_err(|e| e.to_string());
            }
            std::thread::sleep(std::time::Duration::from_millis(30));
        }
    }
    Err("schowek jest zajęty".into())
}

// ---------------------------------------------------------------- dźwięk

fn endpoint() -> Option<IAudioEndpointVolume> {
    unsafe {
        let _ = CoInitializeEx(None, COINIT_MULTITHREADED);
        let en: IMMDeviceEnumerator = CoCreateInstance(&MMDeviceEnumerator, None, CLSCTX_ALL).ok()?;
        let dev = en.GetDefaultAudioEndpoint(eRender, eConsole).ok()?;
        dev.Activate::<IAudioEndpointVolume>(CLSCTX_ALL, None).ok()
    }
}

/// Głośność 0–100 i wyciszenie.
pub fn volume() -> Option<(i32, bool)> {
    let ep = endpoint()?;
    unsafe {
        let v = ep.GetMasterVolumeLevelScalar().ok()?;
        let m = ep.GetMute().ok()?.as_bool();
        Some(((v * 100.0).round() as i32, m))
    }
}

pub fn set_volume(level: Option<i32>, mute: Option<bool>) -> Result<(), String> {
    let ep = endpoint().ok_or("brak urządzenia audio")?;
    unsafe {
        if let Some(l) = level {
            ep.SetMasterVolumeLevelScalar(l.clamp(0, 100) as f32 / 100.0, std::ptr::null()).map_err(|e| e.to_string())?;
        }
        if let Some(m) = mute {
            ep.SetMute(m, std::ptr::null()).map_err(|e| e.to_string())?;
        }
    }
    Ok(())
}

// ---------------------------------------------------------------- klawiatura

pub fn vk_from_name(n: &str) -> Option<u16> {
    let n = n.trim().to_lowercase();
    if n.len() == 1 {
        let c = n.chars().next()?.to_ascii_uppercase();
        if c.is_ascii_alphanumeric() {
            return Some(c as u16);
        }
    }
    if let Some(f) = n.strip_prefix('f') {
        if let Ok(i) = f.parse::<u16>() {
            if (1..=24).contains(&i) {
                return Some(0x70 + i - 1);
            }
        }
    }
    if let Some(d) = n.strip_prefix("num") {
        if let Ok(i) = d.parse::<u16>() {
            if i <= 9 {
                return Some(0x60 + i);
            }
        }
    }
    Some(match n.as_str() {
        "space" | "spacja" => 0x20,
        "enter" | "return" => 0x0D,
        "tab" => 0x09,
        "esc" | "escape" => 0x1B,
        "backspace" => 0x08,
        "delete" | "del" => 0x2E,
        "insert" | "ins" => 0x2D,
        "home" => 0x24,
        "end" => 0x23,
        "pgup" | "pageup" => 0x21,
        "pgdn" | "pagedown" => 0x22,
        "up" => 0x26,
        "down" => 0x28,
        "left" => 0x25,
        "right" => 0x27,
        "printscreen" | "prtsc" => 0x2C,
        "pause" => 0x13,
        "plus" | "=" => 0xBB,
        "minus" | "-" => 0xBD,
        "," => 0xBC,
        "." => 0xBE,
        "/" => 0xBF,
        ";" => 0xBA,
        "`" => 0xC0,
        "[" => 0xDB,
        "]" => 0xDD,
        "\\" => 0xDC,
        "'" => 0xDE,
        "volume_up" => 0xAF,
        "volume_down" => 0xAE,
        "volume_mute" => 0xAD,
        "media_next" => 0xB0,
        "media_prev" => 0xB1,
        "media_stop" => 0xB2,
        "media_play" => 0xB3,
        "ctrl" | "control" => 0x11,
        "alt" => 0x12,
        "shift" => 0x10,
        "win" => 0x5B,
        _ => return None,
    })
}

fn key_input(vk: u16, up: bool) -> INPUT {
    let mut flags = KEYBD_EVENT_FLAGS(0);
    if up {
        flags |= KEYEVENTF_KEYUP;
    }
    if matches!(vk, 0x21..=0x2E | 0x5B | 0xAD..=0xB3) {
        flags |= KEYEVENTF_EXTENDEDKEY;
    }
    INPUT {
        r#type: INPUT_KEYBOARD,
        Anonymous: INPUT_0 { ki: KEYBDINPUT { wVk: VIRTUAL_KEY(vk), wScan: 0, dwFlags: flags, time: 0, dwExtraInfo: 0 } },
    }
}

/// Naciska kombinację, np. „ctrl+shift+esc”, „media_play”, „win+d”.
pub fn send_combo(combo: &str) -> bool {
    let keys: Vec<u16> = combo.split('+').filter_map(vk_from_name).collect();
    if keys.is_empty() {
        return false;
    }
    let mut v: Vec<INPUT> = keys.iter().map(|&k| key_input(k, false)).collect();
    v.extend(keys.iter().rev().map(|&k| key_input(k, true)));
    unsafe { SendInput(&v, std::mem::size_of::<INPUT>() as i32) as usize == v.len() }
}

pub fn type_text(text: &str) {
    let mut v = Vec::new();
    for c in text.encode_utf16() {
        for up in [false, true] {
            let mut flags = KEYEVENTF_UNICODE;
            if up {
                flags |= KEYEVENTF_KEYUP;
            }
            v.push(INPUT {
                r#type: INPUT_KEYBOARD,
                Anonymous: INPUT_0 { ki: KEYBDINPUT { wVk: VIRTUAL_KEY(0), wScan: c, dwFlags: flags, time: 0, dwExtraInfo: 0 } },
            });
        }
    }
    for chunk in v.chunks(200) {
        unsafe {
            SendInput(chunk, std::mem::size_of::<INPUT>() as i32);
        }
    }
}

// ---------------------------------------------------------------- procesy zewnętrzne

pub fn run_hidden(cmd: &str, args: &[&str]) -> Result<String, String> {
    let out = Command::new(cmd).args(args).creation_flags(CREATE_NO_WINDOW).output().map_err(|e| e.to_string())?;
    let mut s = decode(&out.stdout);
    if !out.status.success() {
        let e = decode(&out.stderr);
        if s.trim().is_empty() {
            s = e;
        }
        return Err(format!("kod {}: {}", out.status.code().unwrap_or(-1), s.trim()));
    }
    Ok(s)
}

fn decode(b: &[u8]) -> String {
    match String::from_utf8(b.to_vec()) {
        Ok(s) => s,
        Err(_) => b.iter().map(|&c| cp852(c)).collect(),
    }
}

/// Konsola Windows w Polsce używa CP852 – wystarczy dla polskich znaków.
fn cp852(c: u8) -> char {
    match c {
        0xA5 => 'ą', 0xA4 => 'Ą', 0x86 => 'ć', 0x8F => 'Ć', 0xA9 => 'ę', 0xA8 => 'Ę', 0x88 => 'ł', 0x9D => 'Ł',
        0xE4 => 'ń', 0xE3 => 'Ń', 0xA2 => 'ó', 0xE0 => 'Ó', 0x98 => 'ś', 0x97 => 'Ś', 0xAB => 'ź', 0x8D => 'Ź',
        0xBE => 'ż', 0xBD => 'Ż', c if c < 128 => c as char, _ => '?',
    }
}

/// Uruchamia skrypt PowerShell bez okna i zwraca wyjście (UTF-8).
pub fn powershell(script: &str) -> Result<String, String> {
    let full = format!("[Console]::OutputEncoding=[Text.Encoding]::UTF8; $ProgressPreference='SilentlyContinue'; {script}");
    run_hidden("powershell.exe", &["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", &full])
}

pub fn ps_quote(s: &str) -> String {
    format!("'{}'", s.replace('\'', "''"))
}

pub fn speak(text: &str, rate: i32, volume: i32) -> Result<(), String> {
    powershell(&format!(
        "Add-Type -AssemblyName System.Speech; $s=New-Object System.Speech.Synthesis.SpeechSynthesizer; $s.Rate={}; $s.Volume={}; $s.Speak({})",
        rate.clamp(-10, 10),
        volume.clamp(0, 100),
        ps_quote(text)
    ))
    .map(|_| ())
}

/// Uruchamia program, plik, folder lub adres przez powłokę Windows.
pub fn shell_open(target: &str, args: &str, hidden: bool, cwd: &str) -> Result<(), String> {
    let mut c = Command::new("cmd");
    c.creation_flags(CREATE_NO_WINDOW);
    let style = if hidden { "/MIN" } else { "" };
    let mut line = format!("start \"\" {style} \"{}\"", target.trim_matches('"'));
    if !args.trim().is_empty() {
        line.push(' ');
        line.push_str(args);
    }
    c.raw_arg(format!("/C {line}"));
    if !cwd.trim().is_empty() {
        c.current_dir(cwd);
    }
    c.spawn().map(|_| ()).map_err(|e| e.to_string())
}

pub fn kill_process(name: &str, force: bool) -> Result<String, String> {
    let mut n = name.trim().to_string();
    if !n.to_lowercase().ends_with(".exe") {
        n.push_str(".exe");
    }
    let mut args = vec!["/IM", n.as_str(), "/T"];
    if force {
        args.push("/F");
    }
    run_hidden("taskkill", &args)
}


#[allow(dead_code)]
pub fn find_window(title: &str) -> bool {
    let t = HSTRING::from(title);
    unsafe { FindWindowW(PCWSTR::null(), &t).is_ok() }
}

#[allow(dead_code)]
pub const APP_TITLE: PCWSTR = w!("OpenRoutine");
