package com.blacksingularities.openroutine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openroutine.model.Catalog
import com.blacksingularities.openroutine.model.Kind
import com.blacksingularities.openroutine.model.Presets
import com.blacksingularities.openroutine.ui.theme.Palette

@Composable
fun TemplatesScreen(vm: AppViewModel) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Gotowe przepisy do dostosowania. Po wybraniu otworzy się edytor – zmień, co chcesz, i zapisz. " +
                    "Do dyspozycji masz ${Catalog.of(Kind.TRIGGER).size} wyzwalaczy, ${Catalog.of(Kind.CONDITION).size} warunków i ${Catalog.of(Kind.ACTION).size} akcji.",
                color = Palette.TextDim, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        Presets.all.groupBy { it.group }.forEach { (group, presets) ->
            item(key = "group:$group") {
                Text(
                    group.uppercase(), color = Palette.TextFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 6.dp, top = 10.dp),
                )
            }
            items(presets, key = { it.title }) { p ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable { vm.fromPreset(p) }
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                            (p.routine.triggers + p.routine.conditions + p.routine.actions).map { it.type }.distinct().take(4).forEach { t ->
                                Box(
                                    Modifier.size(30.dp).clip(RoundedCornerShape(50)).background(Palette.PanelHigh).padding(2.dp)
                                        .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(blockIcon(t), null, tint = Color.White, modifier = Modifier.size(15.dp)) }
                            }
                        }
                        Text(p.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(Color.White), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, "Użyj", tint = Palette.Ink, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(p.description, fontSize = 13.sp, color = Palette.TextDim, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        "Gdy " + p.routine.triggers.joinToString(" lub ") { it.summary() } + " → " + p.routine.actions.joinToString(", ") { it.summary() },
                        fontSize = 11.sp, color = Palette.TextFaint, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
