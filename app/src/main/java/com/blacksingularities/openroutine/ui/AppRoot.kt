package com.blacksingularities.openroutine.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openroutine.ui.theme.Palette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AppRoot(vm: AppViewModel) {
    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        val draft = vm.draft
        if (draft != null) {
            EditorScreen(vm, draft)
        } else {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Header(vm.tab)
                Box(Modifier.weight(1f)) {
                    when (vm.tab) {
                        Tab.ROUTINES -> RoutinesScreen(vm)
                        Tab.TEMPLATES -> TemplatesScreen(vm)
                        Tab.LOG -> LogScreen(vm)
                        Tab.MORE -> MoreScreen(vm)
                    }
                }
                BottomTabs(vm.tab) { vm.tab = it }
            }
        }
        ToastHost(vm)
    }
}

@Composable
private fun Header(tab: Tab) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(if (tab == Tab.ROUTINES) "OpenRoutine" else tab.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
        if (tab == Tab.ROUTINES) Text("zdarzenie → warunek → akcja", fontSize = 11.sp, color = Palette.TextFaint, modifier = Modifier.padding(bottom = 5.dp))
    }
}

private fun tabIcon(t: Tab): ImageVector = when (t) {
    Tab.ROUTINES -> Icons.Rounded.Bolt
    Tab.TEMPLATES -> Icons.Rounded.AutoAwesome
    Tab.LOG -> Icons.Rounded.History
    Tab.MORE -> Icons.Rounded.Tune
}

@Composable
private fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab.entries.forEach { t ->
            val sel = t == selected
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) Color.White else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(tabIcon(t), null, tint = if (sel) Palette.Ink else Palette.TextDim, modifier = Modifier.size(18.dp))
                if (sel) {
                    Spacer(Modifier.width(6.dp))
                    Text(t.title, color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ToastHost(vm: AppViewModel) {
    val msg = vm.toast
    LaunchedEffect(msg) {
        if (msg != null) {
            delay(2_800)
            vm.toast = null
        }
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 84.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = msg != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                msg.orEmpty(),
                color = Palette.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable { vm.toast = null }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

/** Czas względny: „przed chwilą”, „dziś 14:05”, „wczoraj 9:00”, „12.10 8:30”. */
fun whenText(ms: Long): String {
    if (ms <= 0) return "nigdy"
    val now = System.currentTimeMillis()
    val diff = now - ms
    if (diff in 0 until 60_000) return "przed chwilą"
    if (diff in 0 until 3_600_000) return "${diff / 60_000} min temu"
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val today = Calendar.getInstance()
    val time = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(ms))
    val dayDiff = (today.get(Calendar.YEAR) - c.get(Calendar.YEAR)) * 366 + today.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR)
    return when (dayDiff) {
        0 -> "dziś $time"
        1 -> "wczoraj $time"
        -1 -> "jutro $time"
        else -> SimpleDateFormat("d.MM HH:mm", Locale.ROOT).format(Date(ms))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, fontSize = 13.sp, color = Palette.TextDim, textAlign = TextAlign.Center, lineHeight = 18.sp)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
