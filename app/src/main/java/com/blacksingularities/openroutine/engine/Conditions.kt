package com.blacksingularities.openroutine.engine

import com.blacksingularities.openroutine.data.Repo
import com.blacksingularities.openroutine.model.Block
import com.blacksingularities.openroutine.model.Fmt
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

object Conditions {
    fun check(run: Run, b: Block): Boolean {
        val ctx = run.ctx
        return when (b.type) {
            "c.time_window" -> {
                val from = Fmt.parseTime(b["from"]) ?: error("Zła godzina „${b["from"]}”")
                val to = Fmt.parseTime(b["to"]) ?: error("Zła godzina „${b["to"]}”")
                val now = LocalDateTime.now()
                Fmt.inWindow(from, to, now.hour * 60 + now.minute)
            }
            "c.days" -> LocalDateTime.now().dayOfWeek.value in Fmt.dayset(b["days"]).ifEmpty { (1..7).toSet() }
            "c.battery" -> {
                val level = DeviceState.battery(ctx)
                level in (b["min"].trim().toIntOrNull() ?: 0)..(b["max"].trim().toIntOrNull() ?: 100)
            }
            "c.charging" -> DeviceState.charging(ctx) == (b["state"] == "yes")
            "c.network" -> {
                val net = DeviceState.network(ctx)
                when (b["type"]) {
                    "online" -> net != "none"
                    else -> net == b["type"]
                }
            }
            "c.screen" -> DeviceState.screenOn(ctx) == (b["state"] == "on")
            "c.headset" -> DeviceState.headset(ctx) == (b["state"] == "yes")
            "c.ringer" -> DeviceState.ringer(ctx) == b["mode"]
            "c.dnd" -> DeviceState.dndOn(ctx) == (b["state"] == "on")
            "c.powersave" -> DeviceState.powerSave(ctx) == (b["state"] == "on")
            "c.bluetooth" -> DeviceState.bluetoothOn(ctx) == (b["state"] == "on")
            "c.variable" -> {
                val name = b["name"].trim().removePrefix("{").removeSuffix("}")
                Fmt.compare(run.vars[name], b["op"], run.param(b, "value"))
            }
            "c.file_exists" -> {
                val found = Files.matching(ctx, b["folder"], run.param(b, "pattern")).isNotEmpty()
                found == (b["state"] == "yes")
            }
            "c.chance" -> Random.nextInt(100) < (b["percent"].trim().toIntOrNull() ?: 50)
            "c.month" -> LocalDateTime.now().monthValue in Fmt.months(b["months"]).ifEmpty { (1..12).toSet() }
            "c.date_range" -> Fmt.inDateRange(run.param(b, "from"), run.param(b, "to"), LocalDate.now())
                ?: error("Zły format daty – użyj RRRR-MM-DD lub MM-DD")
            "c.airplane" -> DeviceState.airplane(ctx) == (b["state"] == "on")
            "c.locked" -> DeviceState.locked(ctx) == (b["state"] == "yes")
            "c.music" -> DeviceState.musicActive(ctx) == (b["state"] == "yes")
            "c.in_call" -> DeviceState.inCall(ctx) == (b["state"] == "yes")
            "c.volume" -> DeviceState.volume(ctx, b["stream"]) in (b["min"].trim().toIntOrNull() ?: 0)..(b["max"].trim().toIntOrNull() ?: 100)
            "c.battery_temp" -> {
                val t = DeviceState.batteryTemp(ctx) ?: error("Brak odczytu temperatury")
                t >= (b["min"].trim().toDoubleOrNull() ?: -50.0) && t <= (b["max"].trim().toDoubleOrNull() ?: 100.0)
            }
            "c.torch" -> Torch.on(ctx) == (b["state"] == "on")
            "c.app_installed" -> DeviceState.installed(ctx, b["app"].trim()) == (b["state"] == "yes")
            "c.routine_enabled" -> {
                val target = Repo.byName(run.param(b, "name")) ?: error("Nie ma rutyny „${run.param(b, "name")}”")
                target.enabled == (b["state"] == "on")
            }
            "c.since_run" -> {
                val last = Repo.get(run.routine.id)?.lastRun ?: run.routine.lastRun
                val minutes = b["minutes"].trim().toLongOrNull() ?: 60
                last <= 0 || System.currentTimeMillis() - last >= minutes * 60_000
            }
            else -> error("Nieznany warunek ${b.type}")
        }
    }
}
