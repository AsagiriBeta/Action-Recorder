package asagiribeta.actionRecorder.client

import com.google.gson.Gson
import net.fabricmc.loader.api.FabricLoader
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object CsvLogger {
    private val gson = Gson()
    private val lock = ReentrantLock()

    private var writer: BufferedWriter? = null
    private var flushIntervalTicks: Long = 20L

    fun init() {
        lock.withLock {
            if (writer != null) return
            val gameDir = FabricLoader.getInstance().gameDir
            val dir = gameDir.resolve("action_recorder").toAbsolutePath()
            Files.createDirectories(dir)
            val ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-")
            val file: Path = dir.resolve("session-$ts.csv")
            val out = Files.newOutputStream(file)
            writer = BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))
            // header
            writer!!.write("timestamp_ms,tick,event,x,y,z,yaw,pitch,payload_json\n")
            writer!!.flush()
        }
    }

    fun setFlushInterval(ticks: Long) {
        lock.withLock {
            flushIntervalTicks = if (ticks < 0) 0 else ticks
        }
    }

    fun write(
        event: String,
        tick: Long,
        x: Double?, y: Double?, z: Double?,
        yaw: Float?, pitch: Float?,
        payload: Map<String, Any?>
    ) {
        val line = buildString {
            val ms = System.currentTimeMillis()
            append(ms)
            append(','); append(tick)
            append(','); append(escape(event))
            append(','); append(x?.toString() ?: "")
            append(','); append(y?.toString() ?: "")
            append(','); append(z?.toString() ?: "")
            append(','); append(yaw?.toString() ?: "")
            append(','); append(pitch?.toString() ?: "")
            append(','); append(escape(gson.toJson(payload)))
            append('\n')
        }
        lock.withLock {
            writer?.apply {
                write(line)
                if (flushIntervalTicks > 0 && (tick % flushIntervalTicks) == 0L) flush()
            }
        }
    }

    fun close() {
        lock.withLock {
            writer?.apply {
                flush()
                close()
            }
            writer = null
        }
    }

    private fun escape(s: String): String {
        val needsQuote = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuote) return s
        val escaped = s.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ")
        return "\"$escaped\""
    }
}
