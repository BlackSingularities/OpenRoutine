package com.blacksingularities.openroutine.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.openroutine.data.Repo
import com.blacksingularities.openroutine.engine.Engine
import com.blacksingularities.openroutine.model.Block
import com.blacksingularities.openroutine.model.Catalog
import com.blacksingularities.openroutine.model.Kind
import com.blacksingularities.openroutine.model.Routine
import com.blacksingularities.openroutine.ui.components.ActionRow
import com.blacksingularities.openroutine.ui.components.CompactChoice
import com.blacksingularities.openroutine.ui.components.DarkDialog
import com.blacksingularities.openroutine.ui.components.DarkField
import com.blacksingularities.openroutine.ui.components.Group
import com.blacksingularities.openroutine.ui.components.PillButton
import com.blacksingularities.openroutine.ui.components.SwitchRow
import com.blacksingularities.openroutine.ui.theme.Palette

/** Edycja rutyny: nazwa, wyzwalacze, warunki, akcje i opcje. Zmiany trafiają do szkicu do chwili zapisu. */
@Composable
fun EditorScreen(vm: AppViewModel, draft: Routine) {
    val original = remember(draft.id) { Repo.get(draft.id) }
    var picking by remember { mutableStateOf<Kind?>(null) }
    var editing by remember { mutableStateOf<Pair<Kind, Block>?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    val userVars by vm.vars.collectAsStateWithLifecycle()
    val tick = vm.tick
    val missing = remember(draft, tick) { vm.missingNeeds(draft) }
    val changed = original == null || original != draft
    val request = rememberNeedRequester(vm)

    fun set(r: Routine) { vm.draft = r }
    fun leave() { if (changed && draft.actions.isNotEmpty()) confirmLeave = true else vm.closeDraft() }

    BackHandler { leave() }

    val variables = remember(draft.triggers, userVars) {
        val list = mutableListOf<Pair<String, String>>()
        draft.triggers.flatMap { it.spec?.provides.orEmpty() }.distinct().forEach { list += it to "z wyzwalacza" }
        list += Catalog.builtins
        userVars.keys.sorted().forEach { list += it to "zmienna" }
        list += "http_response" to "odpowiedź HTTP"
        list.distinctBy { it.first }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallIcon(Icons.Rounded.Close, "Zamknij") { leave() }
            Text(
                if (vm.draftIsNew) "Nowa rutyna" else "Edycja rutyny",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
            SmallIcon(Icons.Rounded.PlayArrow, "Wypróbuj") {
                Engine.runManual(draft)
                vm.toast = "Próba – wynik w Dzienniku"
            }
            Spacer(Modifier.width(4.dp))
            PillButton("Zapisz") { vm.saveDraft() }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DarkField(draft.name, { set(draft.copy(name = it)) }, "Nazwa rutyny")

            BlockSection(
                kind = Kind.TRIGGER,
                title = "Gdy · dowolny z wyzwalaczy",
                blocks = draft.triggers,
                empty = "Dodaj zdarzenie, które uruchamia rutynę.",
                onEdit = { editing = Kind.TRIGGER to it },
                onMove = null,
                onRemove = { b -> set(draft.copy(triggers = draft.triggers - b)) },
                onAdd = { picking = Kind.TRIGGER },
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BlockSection(
                    kind = Kind.CONDITION,
                    title = "Jeśli · warunki (opcjonalnie)",
                    blocks = draft.conditions,
                    empty = "Bez warunków rutyna działa zawsze.",
                    onEdit = { editing = Kind.CONDITION to it },
                    onMove = null,
                    onRemove = { b -> set(draft.copy(conditions = draft.conditions - b)) },
                    onAdd = { picking = Kind.CONDITION },
                )
                if (draft.conditions.size > 1) {
                    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f))) {
                        CompactChoice(
                            rowIcon = kindIcon(Kind.CONDITION),
                            title = "Wymagane",
                            options = listOf(true, false),
                            selected = draft.matchAll,
                            label = { if (it) "wszystkie warunki" else "dowolny warunek" },
                            short = { if (it) "Wszystkie" else "Dowolny" },
                        ) { set(draft.copy(matchAll = it)) }
                    }
                }
            }

            BlockSection(
                kind = Kind.ACTION,
                title = "Wykonaj · po kolei",
                blocks = draft.actions,
                empty = "Dodaj co najmniej jedną akcję.",
                onEdit = { editing = Kind.ACTION to it },
                onMove = { b, d ->
                    val list = draft.actions.toMutableList()
                    val i = list.indexOf(b)
                    val j = i + d
                    if (i >= 0 && j in list.indices) {
                        list.add(j, list.removeAt(i))
                        set(draft.copy(actions = list))
                    }
                },
                onRemove = { b -> set(draft.copy(actions = draft.actions - b)) },
                onAdd = { picking = Kind.ACTION },
            )

            Group("Opcje") {
                SwitchRow(Icons.Rounded.PowerSettingsNew, "Włączona", "Wyłączona rutyna nie reaguje na zdarzenia", draft.enabled) { set(draft.copy(enabled = it)) }
                SwitchRow(Icons.Rounded.ErrorOutline, "Przerwij po błędzie", "Domyślnie kolejne akcje wykonują się mimo błędu jednej z nich", draft.stopOnError) { set(draft.copy(stopOnError = it)) }
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, null, tint = Color.White, modifier = Modifier.padding(horizontal = 7.dp).size(18.dp))
                    DarkField(
                        if (draft.cooldownSec == 0) "" else draft.cooldownSec.toString(),
                        { v -> set(draft.copy(cooldownSec = v.filter { it.isDigit() }.take(6).toIntOrNull() ?: 0)) },
                        "Odstęp między uruchomieniami (s)",
                        number = true,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (missing.isNotEmpty()) {
                Group("Potrzebne uprawnienia") {
                    missing.forEach { n ->
                        ActionRow(Icons.Rounded.Lock, n.title, n.description, trailing = { Text("Nadaj", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }) {
                            request(n)
                        }
                    }
                }
            }

            if (!vm.draftIsNew) {
                ActionRow(Icons.Rounded.Delete, "Usuń rutynę", danger = true) { vm.delete(draft) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    picking?.let { kind ->
        BlockPicker(kind, onDismiss = { picking = null }) { spec ->
            picking = null
            editing = kind to Block(spec.id, spec.defaults())
        }
    }

    editing?.let { (kind, block) ->
        BlockDialog(
            vm = vm,
            block = block,
            variables = variables,
            onDismiss = { editing = null },
        ) { updated ->
            editing = null
            fun List<Block>.put(b: Block) = if (any { it.id == b.id }) map { if (it.id == b.id) b else it } else this + b
            set(
                when (kind) {
                    Kind.TRIGGER -> draft.copy(triggers = draft.triggers.put(updated))
                    Kind.CONDITION -> draft.copy(conditions = draft.conditions.put(updated))
                    Kind.ACTION -> draft.copy(actions = draft.actions.put(updated))
                },
            )
        }
    }

    if (confirmLeave) {
        DarkDialog(
            title = "Zapisać zmiany?",
            onDismiss = { confirmLeave = false },
            buttons = {
                PillButton("Odrzuć", primary = false) {
                    confirmLeave = false
                    vm.closeDraft()
                }
                PillButton("Zapisz") {
                    confirmLeave = false
                    vm.saveDraft()
                }
            },
        ) {
            Text("Rutyna ma niezapisane zmiany.", color = Palette.TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BlockSection(
    kind: Kind,
    title: String,
    blocks: List<Block>,
    empty: String,
    onEdit: (Block) -> Unit,
    onMove: ((Block, Int) -> Unit)?,
    onRemove: (Block) -> Unit,
    onAdd: () -> Unit,
) {
    Group(title) {
        if (blocks.isEmpty()) {
            Text(empty, fontSize = 12.sp, color = Palette.TextDim, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        blocks.forEachIndexed { i, b ->
            Row(
                Modifier.fillMaxWidth().clickable { onEdit(b) }.padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(blockIcon(b.type), null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        (if (kind == Kind.ACTION) "${i + 1}. " else "") + (b.spec?.title ?: b.type),
                        fontSize = 11.sp, color = Palette.TextDim,
                    )
                    Text(b.summary(), fontSize = 14.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (onMove != null && blocks.size > 1) {
                    if (i > 0) SmallIcon(Icons.Rounded.ArrowUpward, "Wyżej", Palette.TextDim) { onMove(b, -1) }
                    if (i < blocks.lastIndex) SmallIcon(Icons.Rounded.ArrowDownward, "Niżej", Palette.TextDim) { onMove(b, 1) }
                }
                SmallIcon(Icons.Rounded.Close, "Usuń", Palette.TextDim) { onRemove(b) }
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, tint = Palette.Ink, modifier = Modifier.size(18.dp))
            }
            Text(
                when (kind) {
                    Kind.TRIGGER -> "Dodaj wyzwalacz"
                    Kind.CONDITION -> "Dodaj warunek"
                    Kind.ACTION -> "Dodaj akcję"
                },
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
