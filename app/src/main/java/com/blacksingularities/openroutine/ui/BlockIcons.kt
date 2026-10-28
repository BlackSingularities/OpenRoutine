package com.blacksingularities.openroutine.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.blacksingularities.openroutine.model.Kind

/** Ikona klocka – po rodzaju zdarzenia lub akcji, nie po kategorii. */
fun blockIcon(type: String): ImageVector = when (type.substringAfter('.')) {
    "time" -> Icons.Rounded.Schedule
    "interval" -> Icons.Rounded.Timer
    "boot" -> Icons.Rounded.PowerSettingsNew
    "manual" -> Icons.Rounded.TouchApp
    "battery" -> Icons.Rounded.BatteryChargingFull
    "power", "charging" -> Icons.Rounded.Power
    "screen" -> Icons.Rounded.Smartphone
    "headset" -> Icons.Rounded.Headphones
    "wifi", "network" -> Icons.Rounded.Wifi
    "bluetooth" -> Icons.Rounded.Bluetooth
    "airplane" -> Icons.Rounded.AirplanemodeActive
    "powersave" -> Icons.Rounded.BatterySaver
    "folder", "file_exists", "files" -> Icons.Rounded.Folder
    "notification" -> Icons.Rounded.NotificationsActive
    "sms" -> Icons.Rounded.Sms
    "external", "broadcast" -> Icons.Rounded.Hub
    "time_window" -> Icons.Rounded.Schedule
    "days" -> Icons.Rounded.CalendarMonth
    "ringer", "volume" -> Icons.Rounded.GraphicEq
    "dnd" -> Icons.Rounded.DoNotDisturbOn
    "variable", "set_var" -> Icons.Rounded.DataObject
    "chance" -> Icons.Rounded.Casino
    "notify" -> Icons.Rounded.Notifications
    "toast" -> Icons.Rounded.ChatBubble
    "speak" -> Icons.Rounded.RecordVoiceOver
    "vibrate" -> Icons.Rounded.Vibration
    "sound", "media" -> Icons.Rounded.MusicNote
    "brightness" -> Icons.Rounded.Brightness6
    "torch" -> Icons.Rounded.FlashlightOn
    "open_app" -> Icons.Rounded.Apps
    "open_url" -> Icons.Rounded.Public
    "http" -> Icons.Rounded.Http
    "share" -> Icons.Rounded.Share
    "clipboard" -> Icons.Rounded.ContentCopy
    "write_file" -> Icons.Rounded.Description
    "calc_var" -> Icons.Rounded.Calculate
    "wait" -> Icons.Rounded.HourglassEmpty
    "stop_if" -> Icons.Rounded.Stop
    "run_routine" -> Icons.Rounded.PlayArrow
    "toggle_routine" -> Icons.Rounded.ToggleOn
    "log" -> Icons.Rounded.History
    else -> Icons.Rounded.Extension
}

fun kindIcon(kind: Kind): ImageVector = when (kind) {
    Kind.TRIGGER -> Icons.Rounded.Bolt
    Kind.CONDITION -> Icons.Rounded.FilterAlt
    Kind.ACTION -> Icons.Rounded.PlayArrow
}

val blockedIcon: ImageVector get() = Icons.Rounded.Block
