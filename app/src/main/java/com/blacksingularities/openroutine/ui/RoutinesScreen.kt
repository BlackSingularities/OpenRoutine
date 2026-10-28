package com.blacksingularities.openroutine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.openroutine.model.Kind
import com.blacksingularities.openroutine.model.Routine
import com.blacksingularities.openroutine.ui.components.DarkDialog
import com.blacksingularities.openroutine.ui.components.DarkField
import com.blacksingularities.openroutine.ui.components.PillButton
import com.blacksingularities.openroutine.ui.theme.Palette

@Composable
fun RoutinesScreen(vm: AppViewModel) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<Routine?>(null) }
    val tick = vm.tick
    val missing = remember(routines, tick) { vm.neededNow().filterNot { vm.granted(it) } }
    val shown = if (query.isBlank()) routines else routines.filter { r ->
        r.name.contains(query, true) || (r.triggers + r.conditions + r.actions).any { it.summary().contains(query, true) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { UpdateBanner(vm) }
            if (missing.isNotEmpty()) item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { vm.tab = Tab.MORE }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Rutyny potrzebują uprawnień", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(missing.joinToString { it.title }, fontSize = 11.sp, color = Palette.TextDim, lineHeight = 14.sp)
                    }
                    Text("Nadaj", color = Palette.Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            if (routines.size > 4) item {
                DarkField(query, { query = it }, "Szukaj rutyn")
            }
            if (routines.isEmpty()) item {
                EmptyState(
                    Icons.Rounded.Bolt,
                    "Brak rutyn",
                    "Rutyna to przepis: gdy coś się wydarzy i warunki są spełnione, telefon wykona akcje. Zacznij od szablonu albo od zera.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Szablony", primary = false) { vm.tab = Tab.TEMPLATES }
                        PillButton("Nowa rutyna") { vm.newRoutine() }
                    }
                }
            }
            if (routines.isNotEmpty() && shown.isEmpty()) item {
                EmptyState(Icons.Rounded.Search, "Nic nie znaleziono", "Zmień wyszukiwaną frazę.")
            }
            items(shown, key = { it.id }) { r ->
                RoutineCard(vm, r, vm.nextRuns[r.id], onDelete = { confirmDelete = r })
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .clickable { vm.newRoutine() }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, null, tint = Palette.Ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Nowa rutyna", color = Palette.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }

    confirmDelete?.let { r ->
        DarkDialog(
            title = "Usunąć „${r.name}”?",
            onDismiss = { confirmDelete = null },
            buttons = {
                PillButton("Anuluj", primary = false) { confirmDelete = null }
                PillButton("Usuń", danger = true) {
                    vm.delete(r)
                    confirmDelete = null
                }
            },
        ) {
            Text("Rutyny nie da się przywrócić. Możesz ją najpierw wyeksportować w zakładce Więcej.", color = Palette.TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RoutineCard(vm: AppViewModel, r: Routine, next: Long?, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val dim = !r.enabled
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = if (dim) 0.035f else 0.07f))
            .clickable { vm.edit(r) }
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(if (dim) Color.White.copy(alpha = 0.08f) else Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(blockIcon(r.triggers.firstOrNull()?.type ?: "t.manual"), null, tint = if (dim) Palette.TextDim else Palette.Ink, modifier = Modifier.size(19.dp))
            }
            Text(
                r.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (dim) Palette.TextDim else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Switch(
                checked = r.enabled,
                onCheckedChange = { vm.setEnabled(r, it) },
                modifier = Modifier.scale(0.82f),
                colors = SwitchDefaults.colors(checkedTrackColor = Palette.Amber, checkedThumbColor = Palette.Ink),
            )
        }
        Spacer(Modifier.height(8.dp))
        SummaryLine(Kind.TRIGGER, r.triggers.joinToString(" lub ") { it.summary() })
        if (r.conditions.isNotEmpty()) SummaryLine(Kind.CONDITION, r.conditions.joinToString(if (r.matchAll) " i " else " lub ") { it.summary() })
        SummaryLine(Kind.ACTION, r.actions.joinToString(", ") { it.summary() })
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val meta = buildString {
                append("Ostatnio: ").append(whenText(r.lastRun))
                if (r.runs > 0) append(" · ").append(r.runs).append("×")
                if (r.enabled && next != null) append(" · następnie ").append(whenText(next))
            }
            Text(meta, fontSize = 11.sp, color = Palette.TextFaint, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            SmallIcon(Icons.Rounded.PlayArrow, "Uruchom teraz") { vm.runNow(r) }
            Box {
                SmallIcon(Icons.Rounded.MoreVert, "Więcej") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Palette.PanelHigh) {
                    MenuItem(Icons.Rounded.Edit, "Edytuj") { menu = false; vm.edit(r) }
                    MenuItem(Icons.Rounded.ContentCopy, "Duplikuj") { menu = false; vm.duplicate(r) }
                    MenuItem(Icons.Rounded.AddToHomeScreen, "Skrót na ekranie głównym") { menu = false; vm.pinShortcut(r) }
                    MenuItem(Icons.Rounded.ArrowUpward, "Wyżej") { menu = false; vm.move(r, -1) }
                    MenuItem(Icons.Rounded.ArrowDownward, "Niżej") { menu = false; vm.move(r, 1) }
                    MenuItem(Icons.Rounded.Delete, "Usuń", danger = true) { menu = false; onDelete() }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(kind: Kind, text: String) {
    Row(Modifier.padding(vertical = 2.dp, horizontal = 2.dp), verticalAlignment = Alignment.Top) {
        Text(
            kind.plural.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Palette.TextFaint,
            modifier = Modifier.width(62.dp).padding(top = 2.dp),
        )
        Text(text.ifBlank { "—" }, fontSize = 13.sp, color = Palette.TextDim, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SmallIcon(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MenuItem(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = if (danger) Palette.Record else Color.White
    DropdownMenuItem(
        text = { Text(text, color = c, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, null, tint = c, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
    )
}
