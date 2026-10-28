package com.blacksingularities.openroutine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.openroutine.ui.components.PillButton
import com.blacksingularities.openroutine.ui.theme.Palette

@Composable
fun LogScreen(vm: AppViewModel) {
    val log by vm.log.collectAsStateWithLifecycle()
    var errorsOnly by remember { mutableStateOf(false) }
    val shown = if (errorsOnly) log.filter { !it.ok } else log

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(if (errorsOnly) "Wszystkie" else "Tylko błędy", primary = false) { errorsOnly = !errorsOnly }
            PillButton("Zatrzymaj", primary = false) { vm.stopAll() }
            Box(Modifier.weight(1f))
            if (log.isNotEmpty()) PillButton("Wyczyść", primary = false) { vm.clearLog() }
        }
        if (shown.isEmpty()) {
            EmptyState(Icons.Rounded.History, if (errorsOnly) "Brak błędów" else "Dziennik jest pusty", "Tu zobaczysz każde uruchomienie rutyny, pominięcia i błędy akcji.")
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            itemsIndexed(shown) { _, e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.padding(top = 5.dp, end = 12.dp).size(8.dp).clip(CircleShape)
                            .background(if (e.ok) Color.White.copy(alpha = 0.5f) else Palette.Record),
                    )
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(e.routine, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(whenText(e.time), color = Palette.TextFaint, fontSize = 11.sp)
                        }
                        Text(e.message, color = if (e.ok) Palette.TextDim else Palette.Record, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().padding(start = 20.dp).height(1.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.06f)))
            }
        }
    }
}
