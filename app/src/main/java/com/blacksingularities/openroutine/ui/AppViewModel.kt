package com.blacksingularities.openroutine.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blacksingularities.openroutine.App
import com.blacksingularities.openroutine.MainActivity
import com.blacksingularities.openroutine.R
import com.blacksingularities.openroutine.data.Repo
import com.blacksingularities.openroutine.engine.Engine
import com.blacksingularities.openroutine.engine.FolderWatch
import com.blacksingularities.openroutine.engine.Perms
import com.blacksingularities.openroutine.engine.Scheduler
import com.blacksingularities.openroutine.model.Codec
import com.blacksingularities.openroutine.model.Need
import com.blacksingularities.openroutine.model.Preset
import com.blacksingularities.openroutine.model.Routine
import com.blacksingularities.openroutine.update.UpdateManager
import com.blacksingularities.openroutine.update.UpdateState
import com.blacksingularities.openroutine.update.isNewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(val title: String) { ROUTINES("Rutyny"), TEMPLATES("Szablony"), LOG("Dziennik"), MORE("Więcej") }

data class AppInfo(val pkg: String, val label: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext

    val routines = Repo.routines
    val vars = Repo.vars
    val log = Repo.log

    var tab by mutableStateOf(Tab.ROUTINES)
    /** Szkic edytowanej rutyny; null = lista. */
    var draft by mutableStateOf<Routine?>(null)
    var draftIsNew by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    /** Zwiększany po powrocie do aplikacji – odświeża stan uprawnień. */
    var tick by mutableIntStateOf(0)
        private set
    var nextRuns by mutableStateOf<Map<String, Long>>(emptyMap())
        private set

    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
    var updateBannerHidden by mutableStateOf(false)

    val updatePending: Boolean
        get() = update is UpdateState.Available || update is UpdateState.Downloading || update is UpdateState.Ready || update is UpdateState.Installing

    init {
        if (System.currentTimeMillis() - Repo.lastUpdateCheck > 6 * 3_600_000L) checkUpdate(silent = true)
    }

    fun resumed() {
        tick++
        App.syncBackground(ctx)
        nextRuns = Scheduler.nextRuns(ctx)
    }

    private fun synced() {
        App.syncBackground(ctx)
        nextRuns = Scheduler.nextRuns(ctx)
    }

    // --- Rutyny ---

    fun newRoutine() {
        draft = Routine(name = "Rutyna ${routines.value.size + 1}")
        draftIsNew = true
    }

    fun edit(r: Routine) {
        draft = r
        draftIsNew = false
    }

    fun fromPreset(p: Preset) {
        draft = p.routine.fresh()
        draftIsNew = true
    }

    fun saveDraft() {
        val d = draft ?: return
        if (d.triggers.isEmpty()) {
            toast = "Dodaj co najmniej jeden wyzwalacz"
            return
        }
        if (d.actions.isEmpty()) {
            toast = "Dodaj co najmniej jedną akcję"
            return
        }
        val name = d.name.trim().ifBlank { "Rutyna" }
        val old = Repo.get(d.id)
        // Zmieniony folder w wyzwalaczu – nie uruchamiaj rutyny dla plików, które już tam były.
        d.triggers.filter { it.type == "t.folder" }.forEach { b ->
            val before = old?.triggers?.firstOrNull { it.id == b.id }
            if (before == null || before["folder"] != b["folder"]) FolderWatch.reset(ctx, b.id)
        }
        Repo.upsert(d.copy(name = name))
        draft = null
        tab = Tab.ROUTINES
        synced()
        val missing = missingNeeds(d)
        toast = if (missing.isEmpty()) "Zapisano „$name”" else "Zapisano. Nadaj uprawnienia: ${missing.joinToString { it.title }}"
    }

    fun closeDraft() {
        draft = null
    }

    fun setEnabled(r: Routine, on: Boolean) {
        Repo.update(r.id) { it.copy(enabled = on) }
        synced()
    }

    fun delete(r: Routine) {
        Repo.delete(r.id)
        if (draft?.id == r.id) draft = null
        synced()
        toast = "Usunięto „${r.name}”"
    }

    fun duplicate(r: Routine) {
        Repo.upsert(r.fresh("${r.name} (kopia)").copy(enabled = false))
        synced()
        toast = "Utworzono kopię (wyłączoną)"
    }

    fun move(r: Routine, delta: Int) = Repo.move(r.id, delta)

    fun runNow(r: Routine) {
        Engine.runManual(r)
        toast = "Uruchomiono „${r.name}”"
    }

    fun stopAll() {
        val n = Engine.activeCount
        Engine.cancelAll()
        toast = if (n == 0) "Nic nie działa" else "Zatrzymano: $n"
    }

    fun pinShortcut(r: Routine) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
            toast = "Ekran główny nie obsługuje skrótów"
            return
        }
        val intent = Intent(ctx, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(MainActivity.EXTRA_RUN, r.id)
            .putExtra(MainActivity.EXTRA_FROM_SHORTCUT, true)
        val info = ShortcutInfoCompat.Builder(ctx, "run_${r.id}")
            .setShortLabel(r.name.take(24))
            .setLongLabel("Uruchom: ${r.name}")
            .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
    }

    // --- Uprawnienia ---

    fun missingNeeds(r: Routine): List<Need> = r.needs.filterNot { Perms.granted(ctx, it) }

    fun neededNow(): Set<Need> = routines.value.filter { it.enabled }.flatMap { it.needs }.toSet()

    fun granted(n: Need) = Perms.granted(ctx, n)

    fun openSettings(n: Need) {
        runCatching { ctx.startActivity(Perms.settingsIntent(ctx, n)) }
            .onFailure { toast = "Nie można otworzyć ustawień" }
    }

    // --- Zmienne i dziennik ---

    fun setVar(name: String, value: String) = Repo.setVar(name, value)
    fun removeVar(name: String) = Repo.removeVar(name)
    fun clearLog() = Repo.clearLog()

    val token: String get() = Repo.token
    fun regenerateToken() {
        Repo.regenerateToken()
        tick++
        toast = "Nowy token – zaktualizuj go w innych aplikacjach"
    }

    // --- Kopia ---

    fun exportTo(uri: Uri) = viewModelScope.launch {
        toast = try {
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(Repo.export().toByteArray()) }
            }
            "Wyeksportowano ${routines.value.size} rutyn"
        } catch (e: Exception) {
            "Eksport nieudany: ${e.message}"
        }
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        toast = try {
            val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
            val bundle = Codec.decode(text)
            val names = routines.value.map { it.name.lowercase() }.toSet()
            val items = bundle.routines.map { r ->
                val name = if (r.name.lowercase() in names) "${r.name} (import)" else r.name
                r.fresh(name).copy(enabled = false)
            }
            Repo.addAll(items)
            Repo.mergeVars(bundle.vars)
            synced()
            "Zaimportowano ${items.size} rutyn (wyłączone – sprawdź i włącz)"
        } catch (e: Exception) {
            "To nie jest plik OpenRoutine: ${e.message}"
        }
    }

    // --- Aplikacje do wyboru ---

    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set

    fun loadApps() {
        if (apps.isNotEmpty()) return
        viewModelScope.launch {
            apps = withContext(Dispatchers.IO) {
                val pm = ctx.packageManager
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
                    .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                    .distinctBy { it.pkg }
                    .sortedBy { it.label.lowercase() }
            }
        }
    }

    // --- Aktualizacje ---

    fun checkUpdate(silent: Boolean) {
        if (update is UpdateState.Checking || update is UpdateState.Downloading || update is UpdateState.Installing) return
        update = UpdateState.Checking
        viewModelScope.launch {
            update = try {
                val info = UpdateManager.check()
                Repo.lastUpdateCheck = System.currentTimeMillis()
                if (isNewer(info.version, UpdateManager.currentVersion)) {
                    updateBannerHidden = false
                    UpdateState.Available(info)
                } else {
                    if (!silent) toast = "Masz najnowszą wersję"
                    UpdateState.UpToDate
                }
            } catch (e: Exception) {
                if (!silent) toast = e.message ?: "Nie można sprawdzić aktualizacji"
                if (silent) UpdateState.Idle else UpdateState.Failed(e.message ?: "Błąd")
            }
        }
    }

    fun updateNow() {
        when (val u = update) {
            is UpdateState.Available -> {
                if (!UpdateManager.canInstall(ctx)) {
                    toast = "Zezwól na instalowanie aplikacji z OpenRoutine i dotknij ponownie"
                    UpdateManager.openInstallPermissionSettings(ctx)
                    return
                }
                viewModelScope.launch {
                    try {
                        update = UpdateState.Downloading(u.info, 0f)
                        val file = UpdateManager.download(ctx, u.info) { p -> update = UpdateState.Downloading(u.info, p) }
                        update = UpdateState.Installing(u.info)
                        UpdateManager.install(ctx, file)
                        update = UpdateState.Ready(u.info, file)
                    } catch (e: Exception) {
                        update = UpdateState.Available(u.info)
                        toast = e.message ?: "Nie udało się pobrać aktualizacji"
                    }
                }
            }
            is UpdateState.Ready -> viewModelScope.launch {
                runCatching { UpdateManager.install(ctx, u.file) }.onFailure { toast = it.message }
            }
            else -> Unit
        }
    }
}
