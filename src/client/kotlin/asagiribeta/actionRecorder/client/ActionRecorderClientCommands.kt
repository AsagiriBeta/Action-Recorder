package asagiribeta.actionRecorder.client

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

object ActionRecorderClientCommands {
    fun applyConfig(key: String, value: Any): String {
        var msg = ""
        Recorder.setConfig {
            when (key.lowercase()) {
                "visibleblocksmxdist", "visibleblocksmaxdist" -> {
                    val d = (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull()
                    if (d != null) visibleBlocksMaxDist = d else msg = "值无效"
                }
                "visibleblocksmaxangle", "visibleblocksmaxangledeg" -> {
                    val d = (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull()
                    if (d != null) visibleBlocksMaxAngleDeg = d else msg = "值无效"
                }
                "visibleblocksmaxreport" -> {
                    val i = (value as? Number)?.toInt() ?: value.toString().toIntOrNull()
                    if (i != null) visibleBlocksMaxReport = i else msg = "值无效"
                }
                "visiblesampleperiod", "visiblesampleperiodticks" -> {
                    val l = (value as? Number)?.toLong() ?: value.toString().toLongOrNull()
                    if (l != null) visibleSamplePeriodTicks = l else msg = "值无效"
                }
                "inventorypollperiod", "inventorypollperiodticks" -> {
                    val l = (value as? Number)?.toLong() ?: value.toString().toLongOrNull()
                    if (l != null) inventoryPollPeriodTicks = l else msg = "值无效"
                }
                "flushinterval", "flushintervalticks" -> {
                    val l = (value as? Number)?.toLong() ?: value.toString().toLongOrNull()
                    if (l != null) flushIntervalTicks = l else msg = "值无效"
                }
                else -> msg = "未知参数: $key"
            }
        }
        if (msg.isEmpty()) msg = "已设置 $key = $value"
        return msg
    }

    fun feedback(message: String) {
        MinecraftClient.getInstance().inGameHud.chatHud.addMessage(Text.literal("[AR] $message"))
    }
}
