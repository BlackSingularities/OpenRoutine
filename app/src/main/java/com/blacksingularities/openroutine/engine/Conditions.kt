package com.blacksingularities.openroutine.engine

import com.blacksingularities.openroutine.model.Block
import com.blacksingularities.openroutine.model.Fmt
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
            else -> error("Nieznany warunek ${b.type}")
        }
    }
}
