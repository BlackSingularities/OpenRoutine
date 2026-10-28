package com.blacksingularities.openroutine.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.openroutine.model.Need
import com.blacksingularities.openroutine.update.UpdateManager
import com.blacksingularities.openroutine.update.UpdateState
import com.blacksingularities.openroutine.ui.components.ActionRow
import com.blacksingularities.openroutine.ui.components.DarkDialog
import com.blacksingularities.openroutine.ui.components.DarkField
import com.blacksingularities.openroutine.ui.components.Group
import com.blacksingularities.openroutine.ui.components.InfoRow
import com.blacksingularities.openroutine.ui.components.PillButton
import com.blacksingularities.openroutine.ui.theme.Palette
import java.time.LocalDate

const val AUTHOR = "Adam Rędzikowski"
const val AUTHOR_URL = "https://redzikowski.com"
const val COFFEE_URL = "https://buycoffee.to/adamredzikowski"

@Composable
fun MoreScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val vars by vm.vars.collectAsStateWithLifecycle()
    val tick = vm.tick
    val needed = remember(tick) { vm.neededNow() }
    val request = rememberNeedRequester(vm)
    var varDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u -> if (u != null) vm.exportTo(u) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) vm.importFrom(u) }

    fun copy(text: String, what: String) {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("OpenRoutine", text))
        vm.toast = "Skopiowano $what"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Group("Uprawnienia") {
            Need.entries.forEach { n ->
                val ok = remember(tick, n) { vm.granted(n) }
                val subtitle = n.description + if (n in needed && !ok) " Potrzebne dla Twoich rutyn." else ""
                ActionRow(
                    if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                    n.title,
                    subtitle,
                    trailing = {
                        Text(
                            if (ok) "Nadane" else "Nadaj",
                            color = if (ok) Palette.TextFaint else if (n in needed) Palette.Ink else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = if (!ok && n in needed) Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 10.dp, vertical = 5.dp) else Modifier,
                        )
                    },
                ) { request(n) }
            }
        }

        Group("Zmienne") {
            if (vars.isEmpty()) {
                Text("Rutyny zapisują tu wartości (akcje „Ustaw zmienną”, „Licz na zmiennej”, odpowiedzi HTTP). Używaj ich w tekstach jako {nazwa}.", color = Palette.TextDim, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            vars.toSortedMap().forEach { (k, v) ->
                ActionRow(Icons.Rounded.DataObject, "{$k}", v.take(120).ifBlank { "(pusta)" }) { varDialog = k to v }
            }
            ActionRow(Icons.Rounded.Add, "Dodaj zmienną") { varDialog = "" to "" }
        }

        Group("Integracje") {
            InfoRow(Icons.Rounded.Key, "Token zdarzeń zewnętrznych", vm.token)
            ActionRow(Icons.Rounded.ContentCopy, "Kopiuj token") { copy(vm.token, "token") }
            val cmd = "am broadcast -a com.blacksingularities.openroutine.EVENT -p ${ctx.packageName} --es event moje_zdarzenie --es token ${vm.token} --es extra \"dane\""
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("Inne aplikacje (Tasker, Automate, skrypty, adb) uruchamiają wyzwalacz „Zdarzenie z innej aplikacji” intencją:", color = Palette.TextDim, fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(6.dp))
                SelectionContainer {
                    Text(
                        cmd,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(10.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("W drugą stronę akcja „Wyślij intencję” przekazuje zdarzenia do innych aplikacji, a „Żądanie HTTP” – do serwerów i webhooków.", color = Palette.TextDim, fontSize = 12.sp, lineHeight = 16.sp)
            }
            ActionRow(Icons.Rounded.ContentCopy, "Kopiuj przykładowe polecenie") { copy(cmd, "polecenie") }
            ActionRow(Icons.Rounded.Refresh, "Wygeneruj nowy token", "Stary przestanie działać") { vm.regenerateToken() }
        }

        Group("Kopia zapasowa") {
            ActionRow(Icons.Rounded.Upload, "Eksportuj rutyny", "Plik JSON z rutynami i zmiennymi") { export.launch("OpenRoutine-${LocalDate.now()}.json") }
            ActionRow(Icons.Rounded.Download, "Importuj rutyny", "Zaimportowane będą wyłączone do sprawdzenia") { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }
        }

        Group("Aktualizacje") {
            InfoRow(Icons.Rounded.Info, "Wersja", UpdateManager.currentVersion)
            ActionRow(
                Icons.Rounded.SystemUpdate,
                when (val u = vm.update) {
                    is UpdateState.Checking -> "Sprawdzanie…"
                    is UpdateState.Available -> "Zainstaluj wersję ${u.info.version}"
                    else -> "Sprawdź aktualizacje"
                },
                "Aplikacja sama pobiera i instaluje nowe wydania",
            ) { if (vm.update is UpdateState.Available) vm.updateNow() else vm.checkUpdate(silent = false) }
        }

        Group("O aplikacji") {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text("OpenRoutine", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Graficzny automat dla Twoich aplikacji: zdarzenie → warunek → akcja. Bez reklam i śledzenia – rutyny zostają w telefonie.", color = Palette.TextDim, fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Pomysł i wykonanie: $AUTHOR", color = Color.White, fontSize = 13.sp)
            }
            ActionRow(Icons.Rounded.Language, "redzikowski.com") { uri.openUri(AUTHOR_URL) }
            ActionRow(Icons.Rounded.Coffee, "Postaw kawę", "Wesprzyj rozwój na buycoffee.to") { uri.openUri(COFFEE_URL) }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("Pomysł i wykonanie: $AUTHOR · redzikowski.com", color = Palette.TextFaint, fontSize = 11.sp)
        }
    }

    varDialog?.let { (origName, origValue) ->
        var name by remember(origName) { mutableStateOf(origName) }
        var value by remember(origName) { mutableStateOf(origValue) }
        DarkDialog(
            title = if (origName.isEmpty()) "Nowa zmienna" else "Zmienna {$origName}",
            onDismiss = { varDialog = null },
            buttons = {
                if (origName.isNotEmpty()) PillButton("Usuń", danger = true) {
                    vm.removeVar(origName)
                    varDialog = null
                }
                PillButton("Zapisz", enabled = name.isNotBlank()) {
                    if (origName.isNotEmpty() && origName != name.trim()) vm.removeVar(origName)
                    vm.setVar(name.trim(), value)
                    varDialog = null
                }
            },
        ) {
            DarkField(name, { name = it.filter { c -> c.isLetterOrDigit() || c == '_' } }, "Nazwa")
            DarkField(value, { value = it }, "Wartość", singleLine = false, minLines = 2)
        }
    }
}
