//! OpenRoutine dla Windows – okno, zasobnik systemowy, polecenia dla interfejsu.

mod actions;
mod conditions;
mod engine;
mod hotkeys;
mod model;
mod probe;
mod sys;
mod vars;
mod webhook;

use engine::{Engine, RunMode};
use model::*;
use tauri::menu::{Menu, MenuItem, PredefinedMenuItem, Submenu};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Manager, WindowEvent};

fn show_main(app: &AppHandle) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.show();
        let _ = w.unminimize();
        let _ = w.set_focus();
    }
}

/// Argumenty: --run "Rutyna", --event nazwa [--extra dane], --show.
fn handle_args(app: &AppHandle, args: &[String], from_second: bool) {
    let e = engine::get();
    let val = |flag: &str| args.iter().position(|a| a == flag).and_then(|i| args.get(i + 1)).cloned();
    if let Some(name) = val("--run") {
        match e.find_routine(&name) {
            Some(id) => e.run(&id, Event::new("t.manual").with("source", "cli"), RunMode::Manual),
            None => e.log("OpenRoutine", format!("Wiersz poleceń: brak rutyny „{name}”"), false),
        }
        return;
    }
    if let Some(name) = val("--event") {
        e.fire(Event::new("t.event").with("event", name).with("extra", val("--extra").unwrap_or_default()).with("source", "cli"));
        return;
    }
    if from_second {
        show_main(app);
    }
}

pub fn refresh_tray(app: &AppHandle) {
    if let Some(tray) = app.tray_by_id("main") {
        if let Ok(m) = build_menu(app) {
            let _ = tray.set_menu(Some(m));
        }
        let on = engine::get().data.lock().unwrap().settings.engine_on;
        let _ = tray.set_tooltip(Some(if on { "OpenRoutine – działa" } else { "OpenRoutine – wstrzymany" }));
    }
}

fn build_menu(app: &AppHandle) -> tauri::Result<Menu<tauri::Wry>> {
    let e = engine::get();
    let (manual, on): (Vec<(String, String)>, bool) = {
        let d = e.data.lock().unwrap();
        (
            d.routines.iter().filter(|r| r.triggers.iter().any(|t| t.kind == "t.manual" && !t.off)).take(25).map(|r| (r.id.clone(), r.name.clone())).collect(),
            d.settings.engine_on,
        )
    };
    let show = MenuItem::with_id(app, "show", "Pokaż OpenRoutine", true, None::<&str>)?;
    let toggle = MenuItem::with_id(app, "toggle", if on { "Wstrzymaj wszystkie rutyny" } else { "Wznów rutyny" }, true, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", "Zakończ", true, None::<&str>)?;
    let sep = PredefinedMenuItem::separator(app)?;
    let sep2 = PredefinedMenuItem::separator(app)?;
    let items: Vec<MenuItem<tauri::Wry>> = if manual.is_empty() {
        vec![MenuItem::with_id(app, "none", "Brak rutyn uruchamianych ręcznie", false, None::<&str>)?]
    } else {
        manual.iter().map(|(id, n)| MenuItem::with_id(app, format!("run:{id}"), n, true, None::<&str>)).collect::<Result<_, _>>()?
    };
    let refs: Vec<&dyn tauri::menu::IsMenuItem<tauri::Wry>> = items.iter().map(|i| i as &dyn tauri::menu::IsMenuItem<tauri::Wry>).collect();
    let sub = Submenu::with_items(app, "Uruchom", true, &refs)?;
    Menu::with_items(app, &[&show, &sep, &sub, &toggle, &sep2, &quit])
}

// ------------------------------------------------------------------ polecenia

#[tauri::command]
fn get_data() -> Data {
    engine::get().data.lock().unwrap().clone()
}

#[tauri::command]
fn save_routine(routine: Routine) {
    let e = engine::get();
    {
        let mut d = e.data.lock().unwrap();
        match d.routines.iter_mut().find(|r| r.id == routine.id) {
            Some(r) => {
                let (lr, runs, ok) = (r.last_run, r.runs, r.last_ok);
                *r = routine;
                r.last_run = lr;
                r.runs = runs;
                r.last_ok = ok;
            }
            None => d.routines.push(routine),
        }
    }
    e.changed();
}

#[tauri::command]
fn import_routines(routines: Vec<Routine>) -> usize {
    let e = engine::get();
    let n = routines.len();
    {
        let mut d = e.data.lock().unwrap();
        for mut r in routines {
            r.id = new_id();
            r.last_run = 0;
            r.runs = 0;
            for b in r.triggers.iter_mut().chain(r.conditions.iter_mut()).chain(r.actions.iter_mut()) {
                b.id = new_id();
            }
            d.routines.push(r);
        }
    }
    e.changed();
    n
}

#[tauri::command]
fn delete_routine(id: String) {
    let e = engine::get();
    e.data.lock().unwrap().routines.retain(|r| r.id != id);
    e.changed();
}

#[tauri::command]
fn reorder(ids: Vec<String>) {
    let e = engine::get();
    {
        let mut d = e.data.lock().unwrap();
        d.routines.sort_by_key(|r| ids.iter().position(|x| *x == r.id).unwrap_or(usize::MAX));
    }
    e.changed();
}

#[tauri::command]
fn set_enabled(id: String, on: bool) {
    let e = engine::get();
    if let Some(r) = e.data.lock().unwrap().routines.iter_mut().find(|r| r.id == id) {
        r.enabled = on;
    }
    e.changed();
}

#[tauri::command]
fn run_routine(id: String, test: bool) {
    engine::get().run(&id, Event::new("t.manual").with("source", "app"), if test { RunMode::Test } else { RunMode::Manual });
}

#[tauri::command]
fn stop_routine(id: String) {
    engine::get().stop(&id);
}

#[tauri::command]
fn running() -> Vec<String> {
    engine::get().running_ids()
}

#[tauri::command]
fn save_settings(settings: Settings) {
    let e = engine::get();
    let restart_hook = {
        let mut d = e.data.lock().unwrap();
        let r = d.settings.webhook_on != settings.webhook_on || d.settings.webhook_port != settings.webhook_port;
        sys::set_autostart(settings.autostart);
        d.settings = settings;
        r
    };
    if restart_hook {
        webhook::restart();
    }
    e.changed();
}

#[tauri::command]
fn new_token() -> String {
    let e = engine::get();
    let t = format!("{:012x}", vars::rand_u64() & 0xffff_ffff_ffff);
    e.data.lock().unwrap().settings.token = t.clone();
    e.changed();
    t
}

#[tauri::command]
fn set_var(name: String, value: String) {
    actions::set_var(&name, value);
}

#[tauri::command]
fn delete_var(name: String) {
    let e = engine::get();
    e.data.lock().unwrap().vars.remove(&name);
    e.touched();
}

#[tauri::command]
fn clear_log() {
    let e = engine::get();
    e.data.lock().unwrap().log.clear();
    e.touched();
}

#[tauri::command]
fn snapshot() -> engine::Snap {
    engine::get().snap.lock().unwrap().clone()
}

#[tauri::command]
fn processes() -> Vec<String> {
    let mut v: Vec<String> = sys::processes().into_iter().filter(|p| p.ends_with(".exe")).collect();
    v.sort();
    v
}

#[tauri::command]
fn window_titles() -> Vec<String> {
    let mut v: Vec<String> = sys::windows_list().into_iter().map(|w| w.0).filter(|t| t != "Program Manager").collect();
    v.sort();
    v.dedup();
    v
}

#[tauri::command]
fn failed_hotkeys() -> Vec<String> {
    hotkeys::failed()
}

#[tauri::command]
fn write_text(path: String, text: String) -> Result<(), String> {
    std::fs::write(path, text).map_err(|e| e.to_string())
}

#[tauri::command]
fn read_text(path: String) -> Result<String, String> {
    std::fs::read_to_string(path).map_err(|e| e.to_string())
}

#[tauri::command]
fn data_dir() -> String {
    engine::get().dir.display().to_string()
}

#[tauri::command]
fn preview_text(text: String) -> String {
    let g = engine::get().data.lock().unwrap().vars.clone();
    vars::expand(&text, &Default::default(), &g)
}

#[tauri::command]
fn autostart_state() -> bool {
    sys::autostart_enabled()
}

#[tauri::command]
fn fire_event(name: String, extra: String) {
    engine::get().fire(Event::new("t.event").with("event", name).with("extra", extra).with("source", "app"));
}

/// Wynik każdego warunku rutyny w tej chwili (do podglądu w edytorze).
#[tauri::command]
fn test_conditions(id: String) -> Vec<bool> {
    let r = engine::get().data.lock().unwrap().routines.iter().find(|r| r.id == id).cloned();
    match r {
        Some(r) => r.conditions.iter().map(|c| c.off || conditions::check(c, &Default::default(), &r)).collect(),
        None => Vec::new(),
    }
}

/// Dzisiejszy wschód i zachód słońca dla ustawionej lokalizacji.
#[tauri::command]
fn sun_info() -> Option<(String, String)> {
    let (lat, lon) = engine::location();
    probe::sun_today(lat, lon).map(|(a, b)| (engine::fmt_hm(a), engine::fmt_hm(b)))
}

#[tauri::command]
async fn check_host(host: String) -> bool {
    tauri::async_runtime::spawn_blocking(move || probe::reachable_now(&host, 2000)).await.unwrap_or(false)
}

#[tauri::command]
fn quit_app(app: AppHandle) {
    engine::get().save_now();
    app.exit(0);
}

// ------------------------------------------------------------------ start

pub fn run() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let autostart = args.iter().any(|a| a == "--autostart");
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, argv, _cwd| {
            let a: Vec<String> = argv.into_iter().skip(1).collect();
            handle_args(app, &a, true);
        }))
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_notification::init())
        .plugin(
            tauri_plugin_window_state::Builder::default()
                .with_state_flags(tauri_plugin_window_state::StateFlags::all() & !tauri_plugin_window_state::StateFlags::VISIBLE)
                .build(),
        )
        .setup(move |app| {
            let dir = app.path().app_data_dir().unwrap_or_else(|_| std::env::temp_dir().join("OpenRoutine"));
            let e = Engine::start(app.handle().clone(), dir);
            {
                let mut d = e.data.lock().unwrap();
                // Autostart włącza wyłącznie użytkownik w Ustawieniach; tu tylko synchronizacja z rejestrem.
                d.settings.autostart = sys::autostart_enabled();
            }
            hotkeys::start();
            webhook::restart();

            let menu = build_menu(app.handle())?;
            TrayIconBuilder::with_id("main")
                .icon(app.default_window_icon().cloned().unwrap())
                .tooltip("OpenRoutine")
                .menu(&menu)
                .show_menu_on_left_click(false)
                .on_menu_event(|app, ev| {
                    let id = ev.id().as_ref().to_string();
                    let e = engine::get();
                    match id.as_str() {
                        "show" => show_main(app),
                        "quit" => {
                            e.save_now();
                            app.exit(0);
                        }
                        "toggle" => {
                            {
                                let mut d = e.data.lock().unwrap();
                                d.settings.engine_on = !d.settings.engine_on;
                            }
                            e.changed();
                        }
                        s if s.starts_with("run:") => e.run(&s[4..], Event::new("t.manual").with("source", "tray"), RunMode::Manual),
                        _ => {}
                    }
                })
                .on_tray_icon_event(|tray, ev| {
                    if let TrayIconEvent::Click { button: MouseButton::Left, button_state: MouseButtonState::Up, .. } = ev {
                        show_main(tray.app_handle());
                    }
                })
                .build(app)?;
            refresh_tray(app.handle());

            let hidden = autostart || e.data.lock().unwrap().settings.start_minimized;
            if !hidden {
                show_main(app.handle());
            }
            handle_args(app.handle(), &args, false);
            Ok(())
        })
        .on_window_event(|w, ev| {
            if let WindowEvent::CloseRequested { api, .. } = ev {
                let e = engine::get();
                let tray = e.data.lock().unwrap().settings.close_to_tray;
                e.save_now();
                if tray {
                    api.prevent_close();
                    let _ = w.hide();
                } else {
                    w.app_handle().exit(0);
                }
            }
        })
        .invoke_handler(tauri::generate_handler![
            get_data,
            save_routine,
            import_routines,
            delete_routine,
            reorder,
            set_enabled,
            run_routine,
            stop_routine,
            running,
            save_settings,
            new_token,
            set_var,
            delete_var,
            clear_log,
            snapshot,
            processes,
            window_titles,
            failed_hotkeys,
            write_text,
            read_text,
            data_dir,
            preview_text,
            autostart_state,
            fire_event,
            test_conditions,
            sun_info,
            check_host,
            quit_app,
        ])
        .run(tauri::generate_context!())
        .expect("error while running OpenRoutine");
}
