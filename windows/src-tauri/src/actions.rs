//! Akcje rutyn.

use crate::engine::{self, Flow, RunMode};
use crate::model::{Block, Event, Routine};
use crate::sys;
use crate::vars;
use std::collections::HashMap;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};
use tauri::AppHandle;
use tauri_plugin_notification::NotificationExt;

type R = Result<Flow, String>;

pub fn notify(app: &AppHandle, title: &str, body: &str) {
    let _ = app.notification().builder().title(title).body(body).show();
}

pub fn title(kind: &str) -> &str {
    match kind {
        "a.notify" => "Powiadomienie",
        "a.msgbox" => "Okno komunikatu",
        "a.speak" => "Powiedz na głos",
        "a.run" => "Uruchom program",
        "a.open" => "Otwórz",
        "a.kill" => "Zamknij aplikację",
        "a.http" => "Żądanie HTTP",
        "a.download" => "Pobierz plik",
        "a.file_copy" => "Kopiuj pliki",
        "a.file_move" => "Przenieś pliki",
        "a.file_delete" => "Usuń pliki",
        "a.file_write" => "Zapisz do pliku",
        "a.file_read" => "Wczytaj plik",
        "a.organize" => "Uporządkuj folder",
        "a.zip" => "Spakuj ZIP",
        "a.unzip" => "Rozpakuj ZIP",
        "a.powershell" => "PowerShell",
        "a.cmd" => "Polecenie",
        "a.volume" => "Głośność",
        "a.theme" => "Motyw Windows",
        "a.wallpaper" => "Tapeta",
        "a.power_plan" => "Plan zasilania",
        "a.shutdown" => "Wyłączanie",
        "a.sleep" => "Uśpienie",
        "a.lock" => "Zablokuj",
        "a.brightness" => "Jasność",
        "a.screenshot" => "Zrzut ekranu",
        "a.clip_set" => "Schowek",
        "a.json" => "Odczytaj JSON",
        "a.var_calc" => "Oblicz",
        _ => kind,
    }
}

fn wait(ms: u64, stop: &AtomicBool) -> Result<(), String> {
    let t = Instant::now();
    while (t.elapsed().as_millis() as u64) < ms {
        if stop.load(Ordering::SeqCst) {
            return Err("przerwano".into());
        }
        std::thread::sleep(Duration::from_millis(ms.saturating_sub(t.elapsed().as_millis() as u64).min(200)));
    }
    Ok(())
}

pub fn set_var(name: &str, value: String) {
    let name = name.trim().trim_matches(['{', '}']).to_string();
    if name.is_empty() {
        return;
    }
    let e = engine::get();
    let changed = {
        let mut d = e.data.lock().unwrap();
        let old = d.vars.insert(name.clone(), value.clone());
        old.as_deref() != Some(value.as_str())
    };
    e.touched();
    if changed {
        e.fire(Event::new("t.var_change").with("name", name).with("value", value));
    }
}

fn store(local: &mut HashMap<String, String>, scope_global: bool, name: &str, value: String) {
    let n = name.trim().trim_matches(['{', '}']);
    if n.is_empty() {
        return;
    }
    local.insert(n.to_string(), value.clone());
    if scope_global {
        set_var(n, value);
    }
}

struct FileSel {
    files: Vec<PathBuf>,
}

fn collect(folder: &str, pattern: &str, older_days: f64, recursive: bool) -> Result<FileSel, String> {
    let root = PathBuf::from(folder.trim());
    if folder.trim().is_empty() {
        return Err("nie wskazano folderu".into());
    }
    if root.is_file() {
        return Ok(FileSel { files: vec![root] });
    }
    if !root.is_dir() {
        return Err(format!("brak folderu {}", root.display()));
    }
    let cutoff = std::time::SystemTime::now() - Duration::from_secs_f64((older_days.max(0.0)) * 86400.0);
    let mut files = Vec::new();
    fn walk(d: &Path, pat: &str, cutoff: std::time::SystemTime, use_age: bool, rec: bool, out: &mut Vec<PathBuf>, depth: usize) {
        let Ok(rd) = std::fs::read_dir(d) else { return };
        for e in rd.flatten() {
            let Ok(md) = e.metadata() else { continue };
            if md.is_dir() {
                if rec && depth < 8 {
                    walk(&e.path(), pat, cutoff, use_age, rec, out, depth + 1);
                }
                continue;
            }
            if !vars::wildcard_any(pat, &e.file_name().to_string_lossy()) {
                continue;
            }
            if use_age && md.modified().map(|m| m > cutoff).unwrap_or(true) {
                continue;
            }
            out.push(e.path());
            if out.len() >= 50_000 {
                return;
            }
        }
    }
    walk(&root, pattern, cutoff, older_days > 0.0, recursive, &mut files, 0);
    Ok(FileSel { files })
}

fn unique(dest: &Path) -> PathBuf {
    if !dest.exists() {
        return dest.to_path_buf();
    }
    let stem = dest.file_stem().map(|s| s.to_string_lossy().to_string()).unwrap_or_default();
    let ext = dest.extension().map(|s| format!(".{}", s.to_string_lossy())).unwrap_or_default();
    let dir = dest.parent().unwrap_or(Path::new("."));
    for i in 2..10_000 {
        let p = dir.join(format!("{stem} ({i}){ext}"));
        if !p.exists() {
            return p;
        }
    }
    dest.to_path_buf()
}

fn place(src: &Path, dest_dir: &Path, conflict: &str, mv: bool) -> Result<bool, String> {
    std::fs::create_dir_all(dest_dir).map_err(|e| e.to_string())?;
    let mut dest = dest_dir.join(src.file_name().unwrap_or_default());
    if dest.exists() {
        match conflict {
            "skip" => return Ok(false),
            "overwrite" => {}
            _ => dest = unique(&dest),
        }
    }
    if mv {
        if std::fs::rename(src, &dest).is_err() {
            std::fs::copy(src, &dest).map_err(|e| e.to_string())?;
            std::fs::remove_file(src).map_err(|e| e.to_string())?;
        }
    } else {
        std::fs::copy(src, &dest).map_err(|e| e.to_string())?;
    }
    Ok(true)
}

fn recycle(paths: &[PathBuf]) -> Result<(), String> {
    for chunk in paths.chunks(200) {
        let list = chunk.iter().map(|p| sys::ps_quote(&p.display().to_string())).collect::<Vec<_>>().join(",");
        sys::powershell(&format!(
            "Add-Type -AssemblyName Microsoft.VisualBasic; foreach($f in @({list})){{ [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($f,'OnlyErrorDialogs','SendToRecycleBin') }}"
        ))?;
    }
    Ok(())
}

fn category(ext: &str) -> &'static str {
    match ext {
        "jpg" | "jpeg" | "png" | "gif" | "webp" | "bmp" | "heic" | "tif" | "tiff" | "svg" | "raw" | "cr2" | "nef" | "arw" | "dng" | "avif" => "Obrazy",
        "pdf" | "doc" | "docx" | "odt" | "rtf" | "txt" | "md" | "xls" | "xlsx" | "ods" | "csv" | "ppt" | "pptx" | "odp" | "epub" => "Dokumenty",
        "zip" | "rar" | "7z" | "tar" | "gz" | "bz2" | "xz" | "iso" => "Archiwa",
        "exe" | "msi" | "msix" | "appx" | "bat" | "cmd" => "Instalatory",
        "mp4" | "mkv" | "avi" | "mov" | "wmv" | "webm" | "m4v" => "Wideo",
        "mp3" | "flac" | "wav" | "ogg" | "m4a" | "aac" | "wma" | "opus" => "Muzyka",
        "ttf" | "otf" | "woff" | "woff2" => "Czcionki",
        "torrent" => "Torrenty",
        _ => "Inne",
    }
}

fn urlencode(s: &str) -> String {
    s.bytes()
        .map(|b| match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => (b as char).to_string(),
            _ => format!("%{b:02X}"),
        })
        .collect()
}

fn text_op(input: &str, op: &str, a: &str, b: &str) -> Result<String, String> {
    Ok(match op {
        "upper" => input.to_uppercase(),
        "lower" => input.to_lowercase(),
        "trim" => input.trim().to_string(),
        "length" => input.chars().count().to_string(),
        "replace" => input.replace(a, b),
        "regex_extract" => {
            let re = regex::Regex::new(a).map_err(|e| e.to_string())?;
            re.captures(input).map(|c| c.get(1).or_else(|| c.get(0)).map(|m| m.as_str().to_string()).unwrap_or_default()).unwrap_or_default()
        }
        "regex_replace" => regex::Regex::new(a).map_err(|e| e.to_string())?.replace_all(input, b).into_owned(),
        "digits" => input.chars().filter(|c| c.is_ascii_digit()).collect(),
        "first_line" => input.lines().next().unwrap_or("").to_string(),
        "line_count" => input.lines().count().to_string(),
        "substring" => {
            let from: usize = a.trim().parse().unwrap_or(0);
            let len: usize = b.trim().parse().unwrap_or(usize::MAX);
            input.chars().skip(from).take(len).collect()
        }
        "urlencode" => urlencode(input),
        "capitalize" => {
            let mut c = input.chars();
            c.next().map(|f| f.to_uppercase().collect::<String>() + c.as_str()).unwrap_or_default()
        }
        "slug" => {
            let s: String = input.to_lowercase().chars().map(|c| if c.is_alphanumeric() { c } else { '-' }).collect();
            s.split('-').filter(|x| !x.is_empty()).collect::<Vec<_>>().join("-")
        }
        "reverse" => input.chars().rev().collect(),
        _ => input.to_string(),
    })
}

pub fn run(a: &Block, local: &mut HashMap<String, String>, r: &Routine, stop: &AtomicBool) -> R {
    let e = engine::get();
    let globals = e.data.lock().unwrap().vars.clone();
    let x = |k: &str| vars::expand(a.p(k), local, &globals);
    let k = a.kind.as_str();
    match k {
        // ------------------------------------------------ komunikaty
        "a.notify" => notify(&e.app, &x("title"), &x("text")),
        "a.msgbox" => {
            let (t, m) = (x("title"), x("text"));
            if a.flag("wait") {
                sys::message_box(&t, &m);
            } else {
                std::thread::spawn(move || sys::message_box(&t, &m));
            }
        }
        "a.speak" => sys::speak(&x("text"), a.num("rate", 0.0) as i32, a.num("volume", 100.0) as i32)?,
        "a.beep" => sys::beep(a.pd("kind", "info")),
        "a.tone" => {
            for _ in 0..(a.num("repeat", 1.0).clamp(1.0, 20.0) as u32) {
                sys::tone(a.num("freq", 880.0) as u32, a.num("ms", 300.0) as u32);
                wait(80, stop)?;
            }
        }
        "a.sound" => {
            let f = x("file");
            if f.to_lowercase().ends_with(".wav") {
                sys::powershell(&format!("(New-Object Media.SoundPlayer {}).PlaySync()", sys::ps_quote(&f)))?;
            } else {
                sys::shell_open(&f, "", false, "")?;
            }
        }
        "a.log" => e.log(&r.name, x("text"), true),

        // ------------------------------------------------ aplikacje i okna
        "a.run" => {
            let path = x("path");
            let args = x("args");
            let cwd = x("cwd");
            if a.flag("wait") {
                let mut c = std::process::Command::new(&path);
                if !args.trim().is_empty() {
                    std::os::windows::process::CommandExt::raw_arg(&mut c, &args);
                }
                if !cwd.trim().is_empty() {
                    c.current_dir(&cwd);
                }
                let st = c.status().map_err(|e| e.to_string())?;
                local.insert("exit_code".into(), st.code().unwrap_or(-1).to_string());
            } else {
                sys::shell_open(&path, &args, a.flag("minimized"), &cwd)?;
            }
        }
        "a.open" => sys::shell_open(&x("target"), "", false, "")?,
        "a.settings" => sys::shell_open(&format!("ms-settings:{}", a.pd("page", "")), "", false, "")?,
        "a.mail" => {
            let url = format!("mailto:{}?subject={}&body={}", x("to"), urlencode(&x("subject")), urlencode(&x("body")));
            sys::shell_open(&url, "", false, "")?
        }
        "a.kill" => {
            let list = x("app");
            let mut killed = 0;
            for n in list.split([';', ',']).map(|s| s.trim()).filter(|s| !s.is_empty()) {
                if sys::kill_process(n, a.flag("force")).is_ok() {
                    killed += 1;
                }
            }
            local.insert("killed".into(), killed.to_string());
        }
        "a.window" => {
            let n = sys::window_op(&x("title"), a.pd("op", "minimize"));
            if n == 0 && !a.flag("quiet") {
                return Err("nie znaleziono okna".into());
            }
        }
        "a.minimize_all" => sys::minimize_all(),
        "a.keys" => {
            for combo in x("keys").split_whitespace() {
                if !sys::send_combo(combo) {
                    return Err(format!("nieznany klawisz: {combo}"));
                }
                wait(60, stop)?;
            }
        }
        "a.type" => {
            wait(a.num("delay", 0.0) as u64 * 1000, stop)?;
            sys::type_text(&x("text"))
        }
        "a.media" => {
            sys::send_combo(match a.pd("cmd", "play") {
                "next" => "media_next",
                "prev" => "media_prev",
                "stop" => "media_stop",
                _ => "media_play",
            });
        }

        // ------------------------------------------------ system
        "a.volume" => {
            let lvl = a.num("level", 50.0) as i32;
            let cur = sys::volume().map(|v| v.0).unwrap_or(50);
            match a.pd("mode", "set") {
                "up" => sys::set_volume(Some(cur + lvl), Some(false))?,
                "down" => sys::set_volume(Some(cur - lvl), None)?,
                "mute" => sys::set_volume(None, Some(true))?,
                "unmute" => sys::set_volume(None, Some(false))?,
                "toggle" => sys::set_volume(None, Some(!sys::volume().map(|v| v.1).unwrap_or(false)))?,
                _ => sys::set_volume(Some(lvl), Some(false))?,
            }
        }
        "a.brightness" => {
            sys::powershell(&format!(
                "(Get-CimInstance -Namespace root/WMI -ClassName WmiMonitorBrightnessMethods -ErrorAction Stop | Select-Object -First 1) | Invoke-CimMethod -MethodName WmiSetBrightness -Arguments @{{Timeout=1;Brightness={}}} | Out-Null",
                a.num("level", 70.0).clamp(0.0, 100.0) as i32
            ))
            .map_err(|_| "ten monitor nie obsługuje zmiany jasności z systemu".to_string())?;
        }
        "a.theme" => {
            let dark = match a.pd("mode", "dark") {
                "light" => false,
                "toggle" => !sys::dark_mode(),
                _ => true,
            };
            let scope = a.pd("scope", "both");
            sys::set_dark_mode(dark, scope != "system", scope != "apps");
        }
        "a.wallpaper" => {
            let f = x("file");
            let f = if Path::new(&f).is_dir() {
                let sel = collect(&f, "*.jpg;*.jpeg;*.png;*.bmp", 0.0, false)?;
                if sel.files.is_empty() {
                    return Err("brak obrazów w folderze".into());
                }
                sel.files[vars::rand_range(0, sel.files.len() as i64 - 1) as usize].display().to_string()
            } else {
                f
            };
            sys::set_wallpaper(&f)?
        }
        "a.power_plan" => {
            let s = match a.pd("plan", "balanced") {
                "high" => "SCHEME_MIN",
                "saver" => "SCHEME_MAX",
                _ => "SCHEME_BALANCED",
            };
            sys::run_hidden("powercfg", &["/setactive", s])?;
        }
        "a.lock" => sys::lock()?,
        "a.sleep" => sys::sleep(a.pd("mode", "sleep") == "hibernate")?,
        "a.shutdown" => sys::shutdown(a.pd("mode", "shutdown"), a.num("delay", 60.0) as u32)?,
        "a.monitor_off" => sys::monitor_off(),
        "a.recycle" => sys::empty_recycle_bin()?,
        "a.clear_temp" => {
            let sel = collect(&std::env::temp_dir().display().to_string(), "", a.num("days", 7.0), true)?;
            let n = sel.files.iter().filter(|p| std::fs::remove_file(p).is_ok()).count();
            local.insert("files_count".into(), n.to_string());
        }
        "a.dns_flush" => {
            sys::run_hidden("ipconfig", &["/flushdns"])?;
        }
        "a.restart_explorer" => {
            let _ = sys::run_hidden("taskkill", &["/F", "/IM", "explorer.exe"]);
            wait(800, stop)?;
            sys::shell_open("explorer.exe", "", false, "")?;
        }

        // ------------------------------------------------ pliki
        "a.file_copy" | "a.file_move" => {
            let sel = collect(&x("source"), &x("pattern"), a.num("older_days", 0.0), a.flag("recursive"))?;
            let dest = PathBuf::from(x("dest"));
            if dest.as_os_str().is_empty() {
                return Err("nie wskazano folderu docelowego".into());
            }
            let mut n = 0;
            for f in &sel.files {
                if place(f, &dest, a.pd("conflict", "rename"), k == "a.file_move")? {
                    n += 1;
                }
            }
            local.insert("files_count".into(), n.to_string());
        }
        "a.file_delete" => {
            let sel = collect(&x("source"), &x("pattern"), a.num("older_days", 0.0), a.flag("recursive"))?;
            if a.pd("mode", "recycle") == "recycle" {
                recycle(&sel.files)?;
            } else {
                for f in &sel.files {
                    std::fs::remove_file(f).map_err(|e| format!("{}: {e}", f.display()))?;
                }
            }
            local.insert("files_count".into(), sel.files.len().to_string());
        }
        "a.file_write" => {
            let p = PathBuf::from(x("path"));
            if let Some(d) = p.parent() {
                let _ = std::fs::create_dir_all(d);
            }
            let mut text = x("text");
            if a.flag("newline") {
                text.push_str("\r\n");
            }
            if a.pd("mode", "append") == "append" {
                use std::io::Write;
                let mut f = std::fs::OpenOptions::new().create(true).append(true).open(&p).map_err(|e| e.to_string())?;
                f.write_all(text.as_bytes()).map_err(|e| e.to_string())?;
            } else {
                std::fs::write(&p, text).map_err(|e| e.to_string())?;
            }
        }
        "a.file_read" => {
            let s = std::fs::read_to_string(x("path")).map_err(|e| e.to_string())?;
            store(local, a.pd("scope", "local") == "global", a.pd("var", "file_text"), s);
        }
        "a.folder_create" => std::fs::create_dir_all(x("path")).map_err(|e| e.to_string())?,
        "a.organize" => {
            let folder = x("folder");
            let sel = collect(&folder, &x("pattern"), a.num("older_days", 0.0), false)?;
            let mut n = 0;
            for f in &sel.files {
                let ext = f.extension().map(|s| s.to_string_lossy().to_lowercase()).unwrap_or_default();
                let sub = match a.pd("by", "type") {
                    "ext" => if ext.is_empty() { "Bez rozszerzenia".to_string() } else { ext.to_uppercase() },
                    "date" => {
                        let m = std::fs::metadata(f).and_then(|m| m.modified()).ok().map(chrono::DateTime::<chrono::Local>::from);
                        m.map(|d| d.format("%Y-%m").to_string()).unwrap_or_else(|| "Inne".into())
                    }
                    _ => category(&ext).to_string(),
                };
                if place(f, &Path::new(&folder).join(sub), "rename", true)? {
                    n += 1;
                }
            }
            local.insert("files_count".into(), n.to_string());
        }
        "a.zip" => {
            let dest = x("dest");
            sys::powershell(&format!("Compress-Archive -Path {} -DestinationPath {} -Force", sys::ps_quote(&x("source")), sys::ps_quote(&dest)))?;
            local.insert("zip".into(), dest);
        }
        "a.unzip" => {
            sys::powershell(&format!("Expand-Archive -Path {} -DestinationPath {} -Force", sys::ps_quote(&x("source")), sys::ps_quote(&x("dest"))))?;
        }
        "a.screenshot" => {
            let dir = x("folder");
            let _ = std::fs::create_dir_all(&dir);
            let file = Path::new(&dir).join(format!("Zrzut {}.png", chrono::Local::now().format("%Y-%m-%d %H-%M-%S")));
            sys::powershell(&format!(
                "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; Add-Type -Namespace W -Name D -MemberDefinition '[DllImport(\"user32.dll\")] public static extern bool SetProcessDPIAware();'; [W.D]::SetProcessDPIAware() | Out-Null; $b=[System.Windows.Forms.SystemInformation]::VirtualScreen; $bmp=New-Object Drawing.Bitmap $b.Width,$b.Height; $g=[Drawing.Graphics]::FromImage($bmp); $g.CopyFromScreen($b.Left,$b.Top,0,0,$bmp.Size); $bmp.Save({},[Drawing.Imaging.ImageFormat]::Png)",
                sys::ps_quote(&file.display().to_string())
            ))?;
            local.insert("file".into(), file.display().to_string());
        }

        // ------------------------------------------------ schowek i tekst
        "a.clip_set" => {
            sys::clipboard_set(&x("text"))?;
            e.own_clip.store(sys::clipboard_seq(), Ordering::SeqCst);
        }
        "a.clip_get" => store(local, a.pd("scope", "local") == "global", a.pd("var", "clip"), sys::clipboard_get()),
        "a.text" => {
            let out = text_op(&x("input"), a.pd("op", "trim"), &x("a"), &x("b"))?;
            store(local, a.pd("scope", "local") == "global", a.pd("var", "text"), out);
        }

        // ------------------------------------------------ sieć
        "a.http" => {
            let url = x("url");
            let method = a.pd("method", "GET").to_uppercase();
            let agent = ureq::AgentBuilder::new().timeout(Duration::from_secs(a.num("timeout", 20.0).clamp(1.0, 300.0) as u64)).build();
            let mut req = agent.request(&method, &url).set("User-Agent", "OpenRoutine/Windows");
            for line in x("headers").lines() {
                if let Some((k, v)) = line.split_once(':') {
                    req = req.set(k.trim(), v.trim());
                }
            }
            let body = x("body");
            let res = if method == "GET" || method == "HEAD" || body.is_empty() { req.call() } else { req.send_string(&body) };
            let (code, text) = match res {
                Ok(r) => (r.status(), r.into_string().unwrap_or_default()),
                Err(ureq::Error::Status(c, r)) => (c, r.into_string().unwrap_or_default()),
                Err(e) => return Err(e.to_string()),
            };
            local.insert("http_code".into(), code.to_string());
            let mut text = text;
            let jp = a.p("json").trim();
            if !jp.is_empty() {
                if let Ok(v) = serde_json::from_str::<serde_json::Value>(&text) {
                    text = vars::json_path(&v, jp).unwrap_or_default();
                }
            }
            local.insert("http_response".into(), text.clone());
            let var = a.p("var").trim();
            if !var.is_empty() {
                store(local, a.pd("scope", "local") == "global", var, text);
            }
            if code >= 400 && !a.flag("ignore_errors") {
                return Err(format!("serwer zwrócił {code}"));
            }
        }
        "a.download" => {
            let url = x("url");
            let mut dest = PathBuf::from(x("path"));
            if dest.is_dir() || x("path").ends_with('\\') {
                let name = url.rsplit('/').next().unwrap_or("plik").split('?').next().unwrap_or("plik").to_string();
                dest = dest.join(if name.is_empty() { "plik".into() } else { name });
            }
            if let Some(d) = dest.parent() {
                let _ = std::fs::create_dir_all(d);
            }
            let r = ureq::get(&url).set("User-Agent", "OpenRoutine/Windows").call().map_err(|e| e.to_string())?;
            let mut buf = Vec::new();
            r.into_reader().take(2_000_000_000).read_to_end(&mut buf).map_err(|e| e.to_string())?;
            std::fs::write(&dest, buf).map_err(|e| e.to_string())?;
            local.insert("file".into(), dest.display().to_string());
        }

        // ------------------------------------------------ skrypty
        "a.powershell" | "a.cmd" => {
            let out = if k == "a.powershell" {
                sys::powershell(&x("script"))
            } else {
                sys::run_hidden("cmd", &["/C", &x("script")])
            };
            let out = match out {
                Ok(s) => s,
                Err(e) if a.flag("ignore_errors") => e,
                Err(e) => return Err(e),
            };
            let out = out.trim_end().to_string();
            local.insert("output".into(), out.clone());
            let var = a.p("var").trim();
            if !var.is_empty() {
                store(local, a.pd("scope", "local") == "global", var, out);
            }
        }

        // ------------------------------------------------ zmienne i logika
        "a.var_set" => store(local, a.pd("scope", "global") == "global", a.p("name"), x("value")),
        "a.var_calc" => {
            let v = vars::calc(&x("expr"))?;
            store(local, a.pd("scope", "global") == "global", a.p("name"), vars::fmt_num(v));
        }
        "a.var_inc" => {
            let n = a.p("name").trim().trim_matches(['{', '}']).to_string();
            let cur: f64 = local.get(&n).or_else(|| globals.get(&n)).and_then(|s| s.parse().ok()).unwrap_or(0.0);
            store(local, true, &n, vars::fmt_num(cur + a.num("by", 1.0)));
        }
        "a.var_del" => {
            let n = a.p("name").trim().trim_matches(['{', '}']).to_string();
            local.remove(&n);
            e.data.lock().unwrap().vars.remove(&n);
            e.touched();
        }
        "a.random" => {
            let v = vars::rand_range(a.num("min", 1.0) as i64, a.num("max", 100.0) as i64);
            store(local, a.pd("scope", "local") == "global", a.pd("name", "random_value"), v.to_string());
        }
        "a.pick" => {
            let items: Vec<String> = x("items").split(['\n', ';']).map(|s| s.trim().to_string()).filter(|s| !s.is_empty()).collect();
            if items.is_empty() {
                return Err("pusta lista".into());
            }
            let v = items[vars::rand_range(0, items.len() as i64 - 1) as usize].clone();
            store(local, a.pd("scope", "local") == "global", a.pd("name", "picked"), v);
        }
        "a.date" => {
            let t = chrono::Local::now() + chrono::Duration::minutes(a.num("offset", 0.0) as i64);
            let f = a.pd("format", "%Y-%m-%d");
            let mut s = String::new();
            use std::fmt::Write;
            if write!(s, "{}", t.format(f)).is_err() {
                return Err("niepoprawny format daty".into());
            }
            store(local, a.pd("scope", "local") == "global", a.pd("name", "date_value"), s);
        }
        "a.json" => {
            let v: serde_json::Value = serde_json::from_str(&x("source")).map_err(|e| format!("to nie jest JSON: {e}"))?;
            let out = vars::json_path(&v, a.p("path")).ok_or("brak takiej ścieżki")?;
            store(local, a.pd("scope", "local") == "global", a.pd("var", "json_value"), out);
        }
        "a.wait" => wait((a.num("seconds", 5.0).max(0.0) * 1000.0) as u64, stop)?,
        "a.wait_until" => {
            let at = vars::hm(a.p("time")).ok_or("niepoprawna godzina")?;
            let n = chrono::Local::now();
            let cur = chrono::Timelike::hour(&n) * 3600 + chrono::Timelike::minute(&n) * 60 + chrono::Timelike::second(&n);
            let target = at * 60;
            let secs = if target >= cur { target - cur } else { 86400 - cur + target };
            wait(secs as u64 * 1000, stop)?;
        }
        "a.stop_if" | "a.skip_if" => {
            let name = a.p("name").trim().trim_matches(['{', '}']);
            let val = local.get(name).cloned().or_else(|| globals.get(name).cloned()).or_else(|| vars::builtin(name)).unwrap_or_default();
            let hit = vars::compare(&val, a.pd("op", "eq"), &x("value"));
            if hit {
                return Ok(if k == "a.stop_if" { Flow::Stop } else { Flow::Skip(a.num("count", 1.0).max(1.0) as usize) });
            }
        }
        "a.run_routine" => {
            let id = e.find_routine(a.p("routine")).ok_or("nie ma takiej rutyny")?;
            if id == r.id {
                return Err("rutyna nie może uruchomić samej siebie".into());
            }
            let mut ev = Event::new("t.manual");
            ev.data = local.clone();
            e.run(&id, ev, RunMode::Manual);
            if a.flag("wait") {
                std::thread::sleep(Duration::from_millis(100));
                let t = Instant::now();
                while e.running.lock().unwrap().contains_key(&id) && t.elapsed() < Duration::from_secs(3600) {
                    wait(200, stop)?;
                }
            }
        }
        "a.toggle_routine" => {
            let id = e.find_routine(a.p("routine")).ok_or("nie ma takiej rutyny")?;
            {
                let mut d = e.data.lock().unwrap();
                if let Some(x) = d.routines.iter_mut().find(|x| x.id == id) {
                    x.enabled = match a.pd("state", "toggle") {
                        "on" => true,
                        "off" => false,
                        _ => !x.enabled,
                    };
                }
            }
            e.changed();
        }
        "a.engine_pause" => {
            let mins = a.num("minutes", 0.0);
            e.data.lock().unwrap().settings.engine_on = false;
            e.changed();
            if mins > 0.0 {
                std::thread::spawn(move || {
                    std::thread::sleep(Duration::from_secs_f64(mins * 60.0));
                    let e = engine::get();
                    e.data.lock().unwrap().settings.engine_on = true;
                    e.changed();
                });
            }
        }
        _ => return Err(format!("nieznana akcja {k}")),
    }
    Ok(Flow::Next)
}
