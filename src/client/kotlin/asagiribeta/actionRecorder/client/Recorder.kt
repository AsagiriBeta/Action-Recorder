package asagiribeta.actionRecorder.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.acos
import kotlin.math.min

object Recorder {
    private val client: MinecraftClient = MinecraftClient.getInstance()

    data class Config(
        var visibleBlocksMaxDist: Double = 8.0,
        var visibleBlocksMaxAngleDeg: Double = 35.0,
        var visibleBlocksMaxReport: Int = 64,
        var visibleSamplePeriodTicks: Long = 5L,
        var inventoryPollPeriodTicks: Long = 2L,
        var flushIntervalTicks: Long = 20L
    )

    @Volatile
    private var isRecording: Boolean = false

    private var config: Config = Config()

    private var lastYaw: Float? = null
    private var lastPitch: Float? = null
    private var lastPos: Vec3d? = null

    private var prevAttackPressed = false
    private var prevUsePressed = false
    private var prevSneak = false
    private var prevJump = false
    private var prevForward = false
    private var prevBack = false
    private var prevLeft = false
    private var prevRight = false
    private var prevSprint = false
    private var prevDrop = false
    private var prevSwap = false
    private var prevPick = false
    private var prevInvKey = false

    private var tickCounter: Long = 0

    // 背包/热键栏跟踪
    private var prevInvSnapshot: List<Pair<String, Int>>? = null
    private var prevSelectedHotbar: Int? = null

    // 屏幕跟踪（容器打开/关闭）
    private var wasHandledScreen = false
    private var lastHandledTitle: String? = null

    fun init() {
        // 注册生命周期与 tick 事件
        ClientLifecycleEvents.CLIENT_STOPPING.register { stop() }
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { _ ->
            onEndTick()
        })
    }

    fun start(): Boolean {
        if (isRecording) return false
        // 准备新会话：重置计数与状态，打开文件
        tickCounter = 0
        resetPrevStatesToCurrent()
        CsvLogger.init()
        CsvLogger.setFlushInterval(config.flushIntervalTicks)
        isRecording = true
        // 写入一个会话开始事件，包含配置快照
        log("session_start", mapOf(
            "config" to mapOf(
                "visibleBlocksMaxDist" to config.visibleBlocksMaxDist,
                "visibleBlocksMaxAngleDeg" to config.visibleBlocksMaxAngleDeg,
                "visibleBlocksMaxReport" to config.visibleBlocksMaxReport,
                "visibleSamplePeriodTicks" to config.visibleSamplePeriodTicks,
                "inventoryPollPeriodTicks" to config.inventoryPollPeriodTicks,
                "flushIntervalTicks" to config.flushIntervalTicks
            )
        ))
        return true
    }

    fun stop(): Boolean {
        if (!isRecording) return false
        log("session_stop", emptyMap())
        CsvLogger.close()
        isRecording = false
        return true
    }

    fun isRecording(): Boolean = isRecording

    fun getConfig(): Config = config

    fun setConfig(modifier: Config.() -> Unit) {
        config.modifier()
        // 运行中动态应用关键参数
        if (isRecording) {
            CsvLogger.setFlushInterval(config.flushIntervalTicks)
        }
    }

    private fun resetPrevStatesToCurrent() {
        val opts = client.options
        prevAttackPressed = opts.attackKey.isPressed
        prevUsePressed = opts.useKey.isPressed
        prevJump = opts.jumpKey.isPressed
        prevSneak = opts.sneakKey.isPressed
        prevForward = opts.forwardKey.isPressed
        prevBack = opts.backKey.isPressed
        prevLeft = opts.leftKey.isPressed
        prevRight = opts.rightKey.isPressed
        prevSprint = opts.sprintKey.isPressed
        prevDrop = opts.dropKey.isPressed
        prevSwap = opts.swapHandsKey.isPressed
        prevPick = opts.pickItemKey.isPressed
        prevInvKey = opts.inventoryKey.isPressed
        lastYaw = client.player?.yaw
        lastPitch = client.player?.pitch
        lastPos = client.player?.pos
        prevInvSnapshot = client.player?.inventory?.let { snapshotInventory(it) }
        prevSelectedHotbar = client.player?.inventory?.selectedSlot
        wasHandledScreen = client.currentScreen is HandledScreen<*>
        lastHandledTitle = (client.currentScreen as? HandledScreen<*>)?.title?.string
    }

    private fun onEndTick() {
        val player = client.player ?: return
        val world = client.world ?: return
        tickCounter++

        if (!isRecording) {
            // 不录制时只维护必要的内部计数，避免溢出
            return
        }

        val pos = player.pos
        val yaw = player.yaw
        val pitch = player.pitch

        // 记录视角/位置变化
        if (lastYaw != yaw || lastPitch != pitch || lastPos != pos) {
            log(
                event = "state",
                payload = mapOf(
                    "x" to pos.x,
                    "y" to pos.y,
                    "z" to pos.z,
                    "yaw" to yaw,
                    "pitch" to pitch,
                    "sneaking" to player.isSneaking,
                    "sprinting" to player.isSprinting
                )
            )
            lastYaw = yaw
            lastPitch = pitch
            lastPos = pos
        }

        // 容器界面打开/关闭检测
        val screen = client.currentScreen
        val isHandled = screen is HandledScreen<*>
        if (isHandled && !wasHandledScreen) {
            lastHandledTitle = (screen as HandledScreen<*>).title.string
            log("container_open", mapOf("title" to lastHandledTitle!!))
        } else if (!isHandled && wasHandledScreen) {
            log("container_close", mapOf("title" to (lastHandledTitle ?: "")))
            lastHandledTitle = null
        }
        wasHandledScreen = isHandled

        // 键位状态边沿检测
        val opts = client.options
        val attack = opts.attackKey.isPressed
        val use = opts.useKey.isPressed
        val jump = opts.jumpKey.isPressed
        val sneak = opts.sneakKey.isPressed
        val forward = opts.forwardKey.isPressed
        val back = opts.backKey.isPressed
        val left = opts.leftKey.isPressed
        val right = opts.rightKey.isPressed
        val sprint = opts.sprintKey.isPressed
        val drop = opts.dropKey.isPressed
        val swap = opts.swapHandsKey.isPressed
        val pick = opts.pickItemKey.isPressed
        val invKey = opts.inventoryKey.isPressed

        // 按下
        detectEdge("attack_down", prevAttackPressed, attack) { onAttackOrUseAttackContext() }
        detectEdge("use_down", prevUsePressed, use) { onUseContext() }
        detectEdge("jump_down", prevJump, jump)
        detectEdge("sneak_down", prevSneak, sneak)
        detectEdge("forward_down", prevForward, forward)
        detectEdge("back_down", prevBack, back)
        detectEdge("left_down", prevLeft, left)
        detectEdge("right_down", prevRight, right)
        detectEdge("sprint_down", prevSprint, sprint)
        detectEdge("drop_down", prevDrop, drop)
        detectEdge("swap_hands_down", prevSwap, swap)
        detectEdge("pick_block_down", prevPick, pick)
        detectEdge("inventory_down", prevInvKey, invKey)

        // 松开
        detectRelease("attack_up", prevAttackPressed, attack)
        detectRelease("use_up", prevUsePressed, use)
        detectRelease("jump_up", prevJump, jump)
        detectRelease("sneak_up", prevSneak, sneak)
        detectRelease("forward_up", prevForward, forward)
        detectRelease("back_up", prevBack, back)
        detectRelease("left_up", prevLeft, left)
        detectRelease("right_up", prevRight, right)
        detectRelease("sprint_up", prevSprint, sprint)
        detectRelease("drop_up", prevDrop, drop)
        detectRelease("swap_hands_up", prevSwap, swap)
        detectRelease("pick_block_up", prevPick, pick)
        detectRelease("inventory_up", prevInvKey, invKey)

        prevAttackPressed = attack
        prevUsePressed = use
        prevJump = jump
        prevSneak = sneak
        prevForward = forward
        prevBack = back
        prevLeft = left
        prevRight = right
        prevSprint = sprint
        prevDrop = drop
        prevSwap = swap
        prevPick = pick
        prevInvKey = invKey

        // 背包差分与热键栏选择（按配置频率）
        if (tickCounter % config.inventoryPollPeriodTicks == 0L) {
            val inv = player.inventory
            val snapshot = snapshotInventory(inv)
            val prev = prevInvSnapshot
            if (prev != null && prev.size == snapshot.size) {
                for (i in snapshot.indices) {
                    val (id, cnt) = snapshot[i]
                    val (pid, pcnt) = prev[i]
                    if (id != pid || cnt != pcnt) {
                        log("inventory_change", mapOf(
                            "slot" to i,
                            "from" to mapOf("id" to pid, "count" to pcnt),
                            "to" to mapOf("id" to id, "count" to cnt)
                        ))
                    }
                }
            }
            prevInvSnapshot = snapshot

            val sel = inv.selectedSlot
            val psel = prevSelectedHotbar
            if (psel == null || sel != psel) {
                log("hotbar_select", mapOf("slot" to sel))
                prevSelectedHotbar = sel
            }
        }

        // 可见方块采样（按配置频率）
        if (config.visibleSamplePeriodTicks > 0 && tickCounter % config.visibleSamplePeriodTicks == 0L) {
            val look = player.rotationVecClient
            val origin = player.eyePos
            val maxDist = config.visibleBlocksMaxDist
            val maxAngleDeg = config.visibleBlocksMaxAngleDeg
            val maxReport = config.visibleBlocksMaxReport

            val box = Box(
                origin.x - maxDist, origin.y - maxDist, origin.z - maxDist,
                origin.x + maxDist, origin.y + maxDist, origin.z + maxDist
            )

            val results = mutableListOf<Map<String, Any>>()
            val minX = box.minX.toInt()
            val minY = box.minY.toInt()
            val minZ = box.minZ.toInt()
            val maxX = box.maxX.toInt()
            val maxY = box.maxY.toInt()
            val maxZ = box.maxZ.toInt()

            outer@ for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
                val bp = BlockPos(x, y, z)
                val state = world.getBlockState(bp)
                if (state.isAir) continue
                val center = Vec3d.ofCenter(bp)
                val to = center.subtract(origin)
                if (to.lengthSquared() > maxDist * maxDist) continue
                val angle = angleBetween(look, to)
                if (angle > maxAngleDeg) continue
                val id = Registries.BLOCK.getId(state.block).toString()
                results.add(mapOf(
                    "pos" to listOf(x, y, z),
                    "id" to id
                ))
                if (results.size >= maxReport) break@outer
            }

            if (results.isNotEmpty()) {
                log("visible_blocks", mapOf("blocks" to results))
            }
        }
    }

    private fun snapshotInventory(inv: net.minecraft.entity.player.PlayerInventory): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>(inv.size())
        for (i in 0 until inv.size()) {
            val st: ItemStack = inv.getStack(i)
            val id = if (st.isEmpty) "minecraft:air" else Registries.ITEM.getId(st.item).toString()
            out.add(id to (if (st.isEmpty) 0 else st.count))
        }
        return out
    }

    private fun detectEdge(event: String, prev: Boolean, curr: Boolean, onTrue: () -> Unit = {}) {
        if (!prev && curr) {
            log(event, emptyMap<String, Any>())
            onTrue()
        }
    }

    private fun detectRelease(event: String, prev: Boolean, curr: Boolean) {
        if (prev && !curr) {
            log(event, emptyMap<String, Any>())
        }
    }

    private fun onAttackOrUseAttackContext() {
        val target = client.crosshairTarget
        when (target?.type) {
            HitResult.Type.BLOCK -> {
                val bhr = target as BlockHitResult
                val bp = bhr.blockPos
                val face: Direction = bhr.side
                val world = client.world ?: return
                val state = world.getBlockState(bp)
                val id = Registries.BLOCK.getId(state.block).toString()
                log("attack_block", mapOf(
                    "pos" to listOf(bp.x, bp.y, bp.z),
                    "face" to face.name,
                    "block" to id
                ))
            }
            HitResult.Type.ENTITY -> {
                val ehr = target as EntityHitResult
                logEntity("attack_entity", ehr.entity)
            }
            else -> log("attack_air", emptyMap())
        }
    }

    private fun onUseContext() {
        val player = client.player ?: return
        val handStack: ItemStack = player.mainHandStack
        val itemId = if (!handStack.isEmpty) Registries.ITEM.getId(handStack.item).toString() else "minecraft:air"

        val target = client.crosshairTarget
        when (target?.type) {
            HitResult.Type.BLOCK -> {
                val bhr = target as BlockHitResult
                val bp = bhr.blockPos
                val face: Direction = bhr.side
                log("use_block", mapOf(
                    "pos" to listOf(bp.x, bp.y, bp.z),
                    "face" to face.name,
                    "item" to itemId
                ))
            }
            HitResult.Type.ENTITY -> {
                val ehr = target as EntityHitResult
                val payload = mutableMapOf<String, Any>(
                    "item" to itemId
                )
                addEntity(payload, ehr.entity)
                log("use_entity", payload)
            }
            else -> log("use_item", mapOf("item" to itemId))
        }
    }

    private fun logEntity(event: String, entity: Entity) {
        val payload = mutableMapOf<String, Any>()
        addEntity(payload, entity)
        log(event, payload)
    }

    private fun addEntity(map: MutableMap<String, Any>, entity: Entity) {
        val typeId = Registries.ENTITY_TYPE.getId(entity.type).toString()
        map["entity_type"] = typeId
        map["entity_id"] = entity.id
        map["entity_pos"] = listOf(entity.x, entity.y, entity.z)
    }

    fun log(event: String, payload: Map<String, Any?>) {
        if (!isRecording) return
        val player = client.player
        val tick = tickCounter
        val pos = player?.pos
        val yaw = player?.yaw
        val pitch = player?.pitch
        CsvLogger.write(
            event = event,
            tick = tick,
            x = pos?.x,
            y = pos?.y,
            z = pos?.z,
            yaw = yaw,
            pitch = pitch,
            payload = payload
        )
    }

    private fun angleBetween(a: Vec3d, b: Vec3d): Double {
        val aN = a.normalize()
        val bN = b.normalize()
        val dot = aN.dotProduct(bN)
        val clamped = min(1.0, kotlin.math.max(-1.0, dot))
        return Math.toDegrees(acos(clamped))
    }

    fun onDoAttackTriggered() {
        if (!isRecording) return
        onAttackOrUseAttackContext()
    }

    fun onDoItemUseTriggered() {
        if (!isRecording) return
        onUseContext()
    }
}
