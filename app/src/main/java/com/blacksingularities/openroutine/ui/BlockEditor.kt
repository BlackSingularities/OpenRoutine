package com.blacksingularities.openroutine.ui

import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blacksingularities.openroutine.engine.Perms
import com.blacksingularities.openroutine.model.Block
import com.blacksingularities.openroutine.model.BlockSpec
import com.blacksingularities.openroutine.model.Catalog
import com.blacksingularities.openroutine.model.Category
import com.blacksingularities.openroutine.model.Fmt
import com.blacksingularities.openroutine.model.Kind
import com.blacksingularities.openroutine.model.Need
import com.blacksingularities.openroutine.model.Param
import com.blacksingularities.openroutine.model.ParamType
import com.blacksingularities.openroutine.ui.components.ActionRow
import com.blacksingularities.openroutine.ui.components.DarkDialog
import com.blacksingularities.openroutine.ui.components.DarkField
import com.blacksingularities.openroutine.ui.components.Group
import com.blacksingularities.openroutine.ui.components.PillButton
import com.blacksingularities.openroutine.ui.components.SwitchRow
import com.blacksingularities.openroutine.ui.theme.Palette

/** Lista klocków danego rodzaju, pogrupowana według kategorii, z wyszukiwaniem. */
@Composable
fun BlockPicker(kind: Kind, onDismiss: () -> Unit, onPick: (BlockSpec) -> Unit) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Palette.Ink).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (kind) {
                        Kind.TRIGGER -> "Wybierz wyzwalacz"
                        Kind.CONDITION -> "Wybierz warunek"
                        Kind.ACTION -> "Wybierz akcję"
                    },
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f),
                )
                SmallIcon(Icons.Rounded.Close, "Zamknij") { onDismiss() }
            }
            Column(Modifier.padding(horizontal = 14.dp)) { DarkField(query, { query = it }, "Szukaj") }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val specs = Catalog.of(kind).filter {
                    query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true) || it.category.title.contains(query, true)
                }
                if (specs.isEmpty()) Text("Nic nie pasuje do „$query”.", color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                Category.entries.forEach { cat ->
                    val inCat = specs.filter { it.category == cat }
                    if (inCat.isNotEmpty()) Group(cat.title) {
                        inCat.forEach { s -> ActionRow(blockIcon(s.id), s.title, s.description) { onPick(s) } }
                    }
                }
            }
        }
    }
}

/** Formularz parametrów klocka. */
@Composable
fun BlockDialog(vm: AppViewModel, block: Block, variables: List<Pair<String, String>>, onDismiss: () -> Unit, onSave: (Block) -> Unit) {
    val spec = block.spec
    var values by remember(block.id) { mutableStateOf((spec?.defaults() ?: emptyMap()) + block.params) }
    DarkDialog(
        title = spec?.title ?: "Nieznany klocek",
        onDismiss = onDismiss,
        buttons = {
            PillButton("Anuluj", primary = false) { onDismiss() }
            PillButton("Gotowe", enabled = spec != null) { onSave(block.copy(params = values)) }
        },
    ) {
        if (spec == null) {
            Text("Ten klocek pochodzi z nowszej wersji aplikacji.", color = Palette.TextDim, fontSize = 13.sp)
            return@DarkDialog
        }
        Text(spec.description, color = Palette.TextDim, fontSize = 13.sp, lineHeight = 18.sp)
        spec.params.forEach { p ->
            ParamField(vm, p, values[p.key].orEmpty(), variables) { values = values + (p.key to it) }
        }
        if (spec.provides.isNotEmpty()) {
            Text(
                "Udostępnia akcjom: " + spec.provides.joinToString(" ") { "{$it}" },
                color = Palette.TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            )
        }
        if (spec.needs.isNotEmpty()) {
            Text("Wymaga: " + spec.needs.joinToString { it.title }, color = Palette.TextFaint, fontSize = 11.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParamField(vm: AppViewModel, p: Param, value: String, variables: List<Pair<String, String>>, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val label = p.label + if (p.optional) " (opcjonalnie)" else ""
    when (p.type) {
        ParamType.TEXT, ParamType.MULTILINE, ParamType.NUMBER -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DarkField(
                value, onChange, label,
                singleLine = p.type != ParamType.MULTILINE,
                number = p.type == ParamType.NUMBER,
                minLines = 3,
            )
            if (p.hint.isNotEmpty()) Text(p.hint, color = Palette.TextFaint, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
            if (p.type != ParamType.NUMBER) VariableChips(variables) { onChange(value + "{$it}") }
        }
        ParamType.TIME -> PickerBox(Icons.Rounded.Schedule, label, value.ifBlank { "—" }) {
            val t = Fmt.parseTime(value) ?: (8 * 60)
            TimePickerDialog(ctx, { _, h, m -> onChange(Fmt.formatTime(h * 60 + m)) }, t / 60, t % 60, true).show()
        }
        ParamType.DAYS -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label + " · " + Fmt.days(value), color = Palette.TextDim, fontSize = 12.sp)
            val set = Fmt.dayset(value)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Pn", "Wt", "Śr", "Cz", "Pt", "Sb", "Nd").forEachIndexed { i, d ->
                    val day = i + 1
                    val sel = day in set
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) Color.White else Color.White.copy(alpha = 0.07f))
                            .clickable {
                                val next = if (sel) set - day else set + day
                                onChange(next.sorted().joinToString(""))
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(d, color = if (sel) Palette.Ink else Color.White, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
        ParamType.CHOICE -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = Palette.TextDim, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                p.options.forEach { (key, text) ->
                    val sel = key == value
                    Text(
                        text,
                        color = if (sel) Palette.Ink else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) Color.White else Color.White.copy(alpha = 0.07f))
                            .clickable { onChange(key) }
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                    )
                }
            }
        }
        ParamType.BOOL -> Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f))) {
            SwitchRow(Icons.Rounded.ToggleOn, label, null, value == "true") { onChange(it.toString()) }
        }
        ParamType.APP -> {
            var picking by remember { mutableStateOf(false) }
            PickerBox(Icons.Rounded.Apps, label, Fmt.appName(value).ifBlank { if (p.optional) "Dowolna" else "Wybierz aplikację" }) {
                vm.loadApps()
                picking = true
            }
            if (picking) AppPicker(vm, optional = p.optional, onDismiss = { picking = false }) {
                picking = false
                onChange(it)
            }
        }
        ParamType.FOLDER -> {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    runCatching {
                        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                    onChange(uri.toString())
                }
            }
            PickerBox(Icons.Rounded.Folder, label, Fmt.folderName(value).ifBlank { "Wybierz folder" }) { launcher.launch(null) }
        }
    }
}

@Composable
private fun PickerBox(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(label, color = Palette.TextDim, fontSize = 11.sp)
            Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Text("Zmień", color = Palette.TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun VariableChips(variables: List<Pair<String, String>>, onInsert: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        variables.forEach { (name, _) ->
            Text(
                "{$name}",
                color = Palette.TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable { onInsert(name) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun AppPicker(vm: AppViewModel, optional: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadApps() }
    DarkDialog(
        title = "Wybierz aplikację",
        onDismiss = onDismiss,
        buttons = {
            if (optional) PillButton("Dowolna", primary = false) { onPick("") }
            PillButton("Anuluj", primary = false) { onDismiss() }
        },
    ) {
        DarkField(query, { query = it }, "Szukaj")
        val list = vm.apps.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
        if (vm.apps.isEmpty()) Text("Wczytywanie…", color = Palette.TextDim, fontSize = 13.sp)
        list.take(150).forEach { a ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(a.pkg) }.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(a.label, color = Color.White, fontSize = 14.sp)
                Text(a.pkg, color = Palette.TextFaint, fontSize = 11.sp)
            }
        }
    }
}

/** Nadawanie uprawnień: okno systemowe dla uprawnień w aplikacji, w pozostałych przypadkach ekran ustawień. */
@Composable
fun rememberNeedRequester(vm: AppViewModel): (Need) -> Unit {
    var pending by remember { mutableStateOf<Need?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val n = pending
        pending = null
        if (!ok && n != null) vm.openSettings(n)
        vm.resumed()
    }
    return { n ->
        val perm = Perms.runtime(n)
        if (perm != null && !vm.granted(n)) {
            pending = n
            launcher.launch(perm)
        } else {
            vm.openSettings(n)
        }
    }
}
