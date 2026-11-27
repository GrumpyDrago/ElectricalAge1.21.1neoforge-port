package mods.eln.gametest

import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.compat.client.GuiContainer
import mods.eln.compat.client.GuiScreen
import mods.eln.compat.openGui
import mods.eln.generic.GenericCreativeTab
import mods.eln.misc.Direction
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge

/**
 * Development aid: builds a flat world showing every component, opens every GUI, takes screenshots and quits.
 * Run with `./gradlew runClientSmokeTest`; the pictures end up in run/screenshots.
 */
object ClientSmokeTest {
    private val mc: Minecraft get() = Minecraft.getInstance()
    private var wait = 100
    private val steps = ArrayDeque<() -> Unit>()
    private var started = false

    private const val GROUND = -60
    private val FPS = java.lang.Boolean.getBoolean("eln.clientSmokeTest.fps")
    private var frames = 0
    private var since = 0L

    @SubscribeEvent
    fun onFrame(@Suppress("UNUSED_PARAMETER") event: net.neoforged.neoforge.client.event.RenderFrameEvent.Post) {
        frames++
    }

    private val JADE = java.lang.Boolean.getBoolean("eln.clientSmokeTest.jade")
    private val JEI = java.lang.Boolean.getBoolean("eln.clientSmokeTest.jei")
    private val ORES = java.lang.Boolean.getBoolean("eln.clientSmokeTest.ores")
    /** Close-ups of the parts added on the expansion-testing branch, at work. */
    private val EXPANSION = java.lang.Boolean.getBoolean("eln.clientSmokeTest.expansion")
    private val sixPositions = ArrayList<Triple<String, BlockPos, Direction>>()
    private val transparentPositions = ArrayList<Pair<String, BlockPos>>()

    @JvmStatic
    fun start() = NeoForge.EVENT_BUS.register(this)

    private fun later(ticks: Int = 2, body: () -> Unit) {
        steps.add {
            body()
            wait = ticks
        }
    }

    private fun onServer(body: (ServerLevel, ServerPlayer) -> Unit) {
        val server = mc.singleplayerServer!!
        server.execute {
            try {
                body(server.overworld(), server.playerList.players.first())
            } catch (e: Throwable) {
                Eln.logger.error("SMOKE server step failed", e)
            }
        }
    }

    private fun shot(name: String) {
        Screenshot.grab(mc.gameDirectory, "eln_$name.png", mc.mainRenderTarget) {}
    }

    @SubscribeEvent
    fun onTick(@Suppress("UNUSED_PARAMETER") event: ClientTickEvent.Post) {
        if (wait-- > 0) return
        wait = 2
        try {
            if (!started) {
                if (mc.overlay == null && (mc.screen is TitleScreen || mc.screen is AccessibilityOnboardingScreen)) {
                    started = true
                    plan()
                    createWorld()
                    wait = 200
                }
                return
            }
            if (mc.level == null) {
                if (mc.screen is DisconnectedScreen) {
                    Eln.logger.error("SMOKE disconnected")
                    mc.stop()
                }
                return
            }
            val step = steps.removeFirstOrNull() ?: return
            step()
        } catch (e: Throwable) {
            Eln.logger.error("SMOKE step failed", e)
        }
    }

    private fun createWorld() {
        val settings = LevelSettings("elntest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, GameRules(), WorldDataConfiguration.DEFAULT)
        mc.createWorldOpenFlows().createFreshLevel(
            "elntest" + System.currentTimeMillis(), settings, WorldOptions(0L, ORES, false),
            { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(if (ORES) WorldPresets.NORMAL else WorldPresets.FLAT).value().createWorldDimensions() }, mc.screen
        )
    }

    private fun look(x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        onServer { level, player ->
            player.abilities.mayfly = true
            player.abilities.flying = true
            player.onUpdateAbilities()
            player.teleportTo(level, x, y, z, yaw, pitch)
        }
    }

    /** Counts the ores in freshly generated terrain. */
    private fun planOres() {
        later(400) {
            onServer { level, _ ->
                val found = HashMap<String, Int>()
                val pos = BlockPos.MutableBlockPos()
                for (cx in 0..7) for (cz in 0..7) {
                    val chunk = level.getChunk(cx, cz)
                    for (x in 0..15) for (z in 0..15) for (y in level.minBuildHeight until 128) {
                        val id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(pos.set(x, y, z)).block)
                        if (id.namespace == Eln.MODID) found.merge(id.path, 1, Int::plus)
                    }
                }
                Eln.logger.info("SMOKE ores in 64 chunks: {}", found)
            }
        }
        later { mc.stop() }
    }

    /** Looks up recipes; JEI has to be in the mods folder. */
    private fun planJei() {
        fun look(name: String, stack: ItemStack, uses: Boolean) {
            later(10) {
                mc.setScreen(InventoryScreen(mc.player!!))
                val found = if (uses) JeiSmokeTest.showUses(stack) else JeiSmokeTest.showRecipes(stack)
                Eln.logger.info("SMOKE JEI {} {}: {}", if (uses) "uses of" else "recipes for", name, found)
            }
            later(5) { shot("jei_" + name.lowercase().replace(Regex("[^a-z0-9]+"), "_")) }
        }
        later(100) { mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE }
        for (name in listOf(
            "50V Macerator", "200V Macerator", "50V Plate Machine", "Rolling Shaft Machine", "50V Compressor", "50V Magnetizer",
            "Old 800V Arc Furnace", "Fabricator", "Electrical Furnace",
            "Stone Heat Furnace", "50V Egg Incubator"
        )) look(name, Eln.findItemStack(name, 1), true)
        look("Sand", ItemStack(Items.SAND), true)
        // Not in the creative tabs.
        for (name in listOf("Low Voltage Cable", "Medium Voltage Relay")) look(name, Eln.findItemStack(name, 1), false)
        for (text in listOf("voltage cable", "current cable", "voltage relay", "signal")) {
            later(10) {
                mc.setScreen(InventoryScreen(mc.player!!))
                Eln.logger.info("SMOKE JEI finds for {}: {}", text, JeiSmokeTest.search(text).joinToString { it.hoverName.string })
            }
            later(5) { shot("jei_search_" + text.replace(' ', '_')) }
        }
        later { JeiSmokeTest.search("") }
        look("Silicon Dust", Eln.findItemStack("Silicon Dust", 1), false)
        later(5) {
            for (name in listOf("50V Macerator", "200V Compressor", "Electrical Furnace")) {
                val (_, pos) = transparentPositions.first { it.first == name }
                gui("transparent", name, pos, Direction.YP) {
                    Eln.logger.info("SMOKE JEI {} pixels of the {} GUI show recipes", JeiSmokeTest.clickAreas(it), name)
                }
            }
            later {
                Eln.logger.info("SMOKE done")
                mc.stop()
            }
        }
    }

    /** Looks at everything; Jade has to be in the mods folder. */
    private fun planJade() {
        var seen = 0
        var wrong = 0
        val items = (Eln.sixNodeItem.subItemList.values + Eln.transparentNodeItem.subItemList.values).associate { it.name to it.newItemStack(1) }
        fun at(name: String, pos: BlockPos, side: Direction) {
            // Elements on the floor and machines from above, others from the north.
            fun look() = if (side == Direction.YN) look(pos.x + 0.5, pos.y + 0.8, pos.z + 0.5, 0f, 90f)
            else look(pos.x + 0.5, pos.y + (if (side == Direction.YP) 0.95 else 0.5) - 1.62, pos.z - 1.5, 0f, 0f)
            later(9) { look() }
            later(0) {
                val expected = items.getValue(name).hoverName.string
                fun check(last: Boolean) {
                    val text = JadeSmokeTest.text.trim().replace(Regex("\\s*[\\r\\n]+"), " | ")
                    val right = JadeSmokeTest.name == expected
                    if (!right && !last) {
                        steps.addFirst { check(true) }
                        look()
                        wait = 40
                        return
                    }
                    seen++
                    if (right) Eln.logger.info("SMOKE Jade at {}: {}", JadeSmokeTest.pos, text)
                    else {
                        wrong++
                        Eln.logger.error("SMOKE Jade shows '{}' at {} instead of '{}' at {}: {}", JadeSmokeTest.name, JadeSmokeTest.pos, expected, pos, text)
                    }
                    if (name in setOf("Tree Resin Collector", "Low Voltage Cable", "50V Macerator", "Large Steam Turbine")) {
                        shot("jade_" + name.lowercase().replace(' ', '_'))
                    }
                }
                check(false)
            }
        }
        later(60) { mc.options.hideGui = false }
        later(5) {
            for ((name, pos, side) in sixPositions) at(name, pos, side)
            for ((name, pos) in transparentPositions) {
                at(name, pos, Direction.YN)
                // The other blocks of large machines.
                Eln.ghostManager.ghostTable.values.filter { it.observatorCoordonate!!.blockPos == pos }.maxByOrNull { it.elementCoordinate!!.blockPos.distSqr(pos) }
                    ?.let { at(name, it.elementCoordinate!!.blockPos, Direction.YN) }
            }
            later {
                Eln.logger.info("SMOKE Jade showed {}, {} wrong", seen, wrong)
                mc.stop()
            }
        }
    }

    private fun plan() {
        if (ORES) return planOres()
        later(40) {
            mc.options.hideGui = true
            mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE
            mc.tutorial.setStep(net.minecraft.client.tutorial.TutorialSteps.NONE)
            mc.options.pauseOnLostFocus = false
            onServer { level, player ->
                level.setDayTime(6000)
                level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.server)
                player.abilities.mayfly = true
                player.abilities.flying = true
                player.onUpdateAbilities()
                placeAll(level, player)
            }
        }
        if (JEI) return planJei()
        if (JADE) return planJade()
        if (EXPANSION) return planExpansion()
        // Rows of six nodes on the floor.
        val columns = 20
        val rows = (Eln.sixNodeItem.subItemList.size + columns - 1) / columns
        for (row in 0 until rows step 4) {
            later(30) { look(columns.toDouble(), GROUND + 5.0, row * 3.0 - 3.5, 0f, 40f) }
            later { shot("six_%02d".format(row)) }
        }
        val transparentRows = (Eln.transparentNodeItem.subItemList.size + 9) / 10
        for (row in 0 until transparentRows) {
            later(30) { look(150.0, GROUND + 7.0, row * 14.0 - 12.0, 0f, 25f) }
            later { shot("transparent_%02d".format(row)) }
        }
        if (FPS) {
            for ((name, x, z) in listOf(Triple("six", 20.0, -6.0), Triple("transparent", 150.0, -14.0), Triple("sky", 20.0, -60.0))) {
                later(60) {
                    mc.options.enableVsync().set(false)
                    mc.options.framerateLimit().set(260)
                    look(x, GROUND + 7.0, z, if (name == "sky") 180f else 0f, if (name == "sky") -60f else 25f)
                }
                later(160) { frames = 0; since = System.nanoTime() }
                later { shot("fps_$name") }
                later { Eln.logger.info("SMOKE fps {}: {}", name, frames * 1000000000L / (System.nanoTime() - since)) }
            }
            later { mc.stop() }
            return
        }
        // Inventory icons.
        later(10) { mc.options.hideGui = false }
        val tabs = listOf(
            "power" to { Eln.creativeTabPowerElectronics },
            "signal" to { Eln.creativeTabSignalProcessing },
            "lighting" to { Eln.creativeTabLighting }, "machines" to { Eln.creativeTabMachines },
            "tools" to { Eln.creativeTabToolsArmor }, "ores" to { Eln.creativeTabOresMaterials }, "creative" to { Eln.creativeTabCreative }
        )
        for ((name, tab) in tabs) {
            later(10) { mc.setScreen(IconScreen(tab())) }
            later { shot("icons_$name") }
        }
        later { mc.setScreen(null) }
        later(10) {
            mc.setScreen(
                net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player!!, mc.connection!!.enabledFeatures(), false)
            )
        }
        later {
            val tabs = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB
            for (id in tabs.keySet().filter { it.namespace == Eln.MODID }) {
                Eln.logger.info("SMOKE creative tab {}: {} items, {}", id, tabs.get(id)!!.displayItems.size, tabs.get(id)!!.displayName.string)
            }
            shot("creative")
            mc.setScreen(null)
        }
        // The real inventory screens; mods that make rendering faster treat their slots differently.
        val creativeTabs = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB
        for (id in creativeTabs.keySet().filter { it.namespace == Eln.MODID }) {
            later(10) {
                val screen = net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player!!, mc.connection!!.enabledFeatures(), false)
                mc.setScreen(screen)
                screen.javaClass.getDeclaredMethod("selectTab", net.minecraft.world.item.CreativeModeTab::class.java)
                    .apply { isAccessible = true }.invoke(screen, creativeTabs.get(id))
            }
            later { shot("tab_${id.path}") }
        }
        later(20) {
            mc.setScreen(null)
            onServer { _, player ->
                val items = net.minecraft.core.registries.BuiltInRegistries.ITEM.filter {
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it).namespace == Eln.MODID
                }
                for (slot in 0 until 36) player.inventory.setItem(slot, net.minecraft.world.item.ItemStack(items[slot * items.size / 36], 1))
            }
        }
        later(20) { mc.setScreen(net.minecraft.client.gui.screens.inventory.ContainerScreen(
            net.minecraft.world.inventory.ChestMenu.sixRows(0, mc.player!!.inventory), mc.player!!.inventory, net.minecraft.network.chat.Component.empty())) }
        later { shot("inventory") }
        later { mc.setScreen(null) }
        // Items as /give makes them: without data.
        later(5) { mc.setScreen(PlainItemScreen()) }
        later { mc.setScreen(null) }
        // Things in the hand.
        for (name in listOf(
            "Portable Electrical Mining Drill", "X-Ray Scanner", "MultiMeter", "Small Flashlight", "Copper Plate",
            "Data Logger", "Steam Turbine", "Battery Charger", "Cost Oriented Battery", "Small Solar Panel", "Stone Heat Furnace", "Autominer", "Wind Turbine", "Large Steam Turbine", "Low Voltage Cable", "Portable Battery", "200V Macerator"
        )) {
            later(20) {
                onServer { _, player ->
                    player.inventory.selected = 0
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, Eln.findItemStack(name, 1))
                    player.connection.send(net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0))
                }
            }
            later { shot("hand_" + name.lowercase().replace(Regex("[^a-z0-9]+"), "_")) }
        }
        later(10) { mc.options.cameraType = net.minecraft.client.CameraType.THIRD_PERSON_FRONT }
        later { shot("hand_third_person") }
        later { mc.options.cameraType = net.minecraft.client.CameraType.FIRST_PERSON }
        for (name in listOf("200V Macerator", "Copper Plate", "Portable Electrical Mining Drill")) {
            later(10) { mc.setScreen(TooltipScreen(Eln.findItemStack(name, 1))) }
            later { shot("tooltip_" + name.lowercase().replace(Regex("[^a-z0-9]+"), "_")) }
        }
        later { mc.setScreen(null) }
        // Every GUI.
        later(5) {
            if (!java.lang.Boolean.getBoolean("eln.clientSmokeTest.noGuis")) for ((name, pos, side) in sixPositions.toList()) gui("six", name, pos, side)
            if (!java.lang.Boolean.getBoolean("eln.clientSmokeTest.noGuis")) for ((name, pos) in transparentPositions.toList()) gui("transparent", name, pos, Direction.YP)
            later(10) { Eln.logger.info("SMOKE {} GUIs", guiCount) }
            reload()
        }
    }

    private val showcase = ArrayList<Pair<String, BlockPos>>()
    private val scopeSources = ArrayList<mods.eln.sixnode.electricalsource.ElectricalSourceElement>()

    private fun planExpansion() {
        val z = 30
        val x0 = 60
        val parts = listOf(
            "Overcurrent Protection Relay", "Battery Management System", "Automatic Transfer Switch",
            "MPPT Charge Controller", "Oscilloscope", "Breaker Box", "50V Breaker Box", "600V Breaker Box"
        )
        later(20) { onServer { level, player -> buildShowcase(level, player, parts, x0, z) } }
        later(40) { look(x0 + 8.0, GROUND + 3.5, z - 3.5, 0f, 40f) }
        later { shot("exp_six_overview") }
        // The oscilloscope gets a changing voltage on both channels, and is looked at while it does.
        later(10) { look(x0 + 16.5, GROUND + 1.6, z - 0.9, 0f, 50f) }
        for (i in 0 until 70) {
            later(2) {
                onServer { _, _ ->
                    scopeSources.getOrNull(0)?.voltageSource?.setVoltage(30 + 20 * Math.sin(i * 0.15))
                    scopeSources.getOrNull(1)?.voltageSource?.setVoltage(if ((i / 12) % 2 == 0) 40.0 else 10.0)
                }
            }
        }
        later { shot("exp_oscilloscope") }
        for ((k, name) in parts.withIndex()) {
            if (name == "Oscilloscope") continue
            val x = x0 + k * 4
            later(20) { look(x + 0.5, GROUND + 1.6, z - 0.9, 0f, 50f) }
            later { shot("exp_" + name.lowercase().replace(Regex("[^a-z0-9]+"), "_")) }
        }
        // The bundle out of the breaker box, with cables of three colors leaving it.
        later(20) { look(x0 + 19.5, GROUND + 2.2, z - 1.5, 0f, 45f) }
        later { shot("exp_power_bundle") }
        later(20) { look(x0 + 3.5, GROUND + 2.5, z + 1.5, 0f, 30f) }
        later { shot("exp_batteries") }
        later(40) { look(x0 + 7.0, GROUND + 6.0, z + 2.0, 0f, 15f) }
        later { shot("exp_generators") }
        // The thermal camera, with one battery hot and one warm.
        later(10) {
            mc.options.hideGui = false
            onServer { _, player ->
                player.inventory.selected = 0
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, Eln.findItemStack("Thermal Camera", 1))
                player.connection.send(net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0))
            }
            look(x0 + 3.5, GROUND + 2.5, z + 1.5, 0f, 30f)
        }
        for (i in 0 until 30) {
            later(2) {
                onServer { _, _ ->
                    for (n in NodeManager.instance!!.nodeList.filterIsInstance<TransparentNode>()) {
                        val b = n.element as? mods.eln.transparentnode.battery.BatteryElement ?: continue
                        if (n.coordinate.x == x0 && n.coordinate.z == z + 5) b.thermalLoad.temperatureCelsius = 45.0
                        if (n.coordinate.x == x0 + 2 && n.coordinate.z == z + 5) b.thermalLoad.temperatureCelsius = 15.0
                    }
                }
            }
        }
        later { shot("exp_thermal_camera") }
        later(10) { look(x0 + 8.0, GROUND + 3.5, z - 3.5, 0f, 40f) }
        later(40) { shot("exp_thermal_camera_switchgear") }
        later(5) {
            mc.options.hideGui = true
            for ((name, pos) in showcase) gui("six", name, pos, Direction.YN)
            later(10) { mc.stop() }
        }
    }

    private fun buildShowcase(level: ServerLevel, player: ServerPlayer, parts: List<String>, x0: Int, z: Int) {
        for (x in x0 - 4..x0 + 32) for (dz in -6..16) {
            level.setBlock(BlockPos(x, GROUND - 1, z + dz), Blocks.SMOOTH_STONE.defaultBlockState(), 2)
        }
        fun six(name: String, x: Int, zz: Int): SixNode? {
            if (!Eln.sixNodeItem.placeBlockAt(Eln.findItemStack(name, 1), player, level, x, GROUND, zz, 1)) {
                Eln.logger.error("SMOKE could not place {}", name)
                return null
            }
            return NodeManager.instance!!.nodeList.filterIsInstance<SixNode>()
                .first { it.coordinate.x == x && it.coordinate.y == GROUND && it.coordinate.z == zz }
        }
        fun source(x: Int, zz: Int, voltage: Double) =
            (six("Electrical Source", x, zz)?.getElement(Direction.YN) as? mods.eln.sixnode.electricalsource.ElectricalSourceElement)
                ?.also { it.voltageSource.setVoltage(voltage) }
        val east = Direction.YN.getLRDUGoingTo(Direction.XP)!!
        for ((k, name) in parts.withIndex()) {
            val x = x0 + k * 4
            val node = six(name, x, z) ?: continue
            val element = node.getElement(Direction.YN)!!
            element.front = east
            showcase.add(name to BlockPos(x, GROUND, z))
            if (element is mods.eln.sixnode.protection.SwitchgearElement) {
                element.inventory.setInventorySlotContents(0, Eln.findItemStack("Low Voltage Cable", 1))
                element.inventory.markDirty()
                element.inventoryChanged()
            }
            when (element) {
                is mods.eln.sixnode.protection.OvercurrentRelayElement -> {
                    source(x + 1, z, 50.0)
                    six("Low Voltage Cable", x - 1, z)
                    element.logic.close()
                }
                is mods.eln.sixnode.protection.BatteryManagementElement -> {
                    source(x + 1, z, 49.0)
                    six("Low Voltage Cable", x - 1, z)
                }
                is mods.eln.sixnode.protection.TransferSwitchElement -> {
                    // The load is east, the mains west and the backup to the left.
                    six("Low Voltage Cable", x + 1, z)
                    source(x - 1, z, 50.0)
                    val b = BlockPos(x, GROUND, z).relative(Direction.YN.applyLRDU(east.left()).toForge())
                    source(b.x, b.z, 50.0)
                }
                is mods.eln.sixnode.breakerbox.BreakerBoxElement -> {
                    source(x + 1, z, 50.0)
                    val bundle = when {
                        name.startsWith("600V") -> "600V Power Bundle Cable"
                        name.startsWith("50V") -> "50V Power Bundle Cable"
                        else -> "Power Bundle Cable"
                    }
                    six(bundle, x - 1, z)
                    six(bundle, x - 2, z)
                    val color = mods.eln.sixnode.electricalcable.ElectricalCableElement::class.java.getDeclaredField("color")
                        .apply { isAccessible = true }
                    // Red, blue and yellow off the sides of the bundle; red and yellow side by side stay apart.
                    for ((at, c) in listOf(Pair(x - 2, z + 1) to 1, Pair(x - 2, z - 1) to 4, Pair(x - 1, z + 1) to 11)) {
                        val cable = six("Medium Voltage Cable", at.first, at.second)?.getElement(Direction.YN) ?: continue
                        color.setInt(cable, c)
                        cable.sixNode!!.reconnect()
                    }
                    element.toggle(5)
                    element.toggle(3)
                    element.logic.state[3] = mods.eln.sixnode.breakerbox.BreakerBoxLogic.State.TRIPPED
                }
                is mods.eln.sixnode.mppt.MpptControllerElement -> {
                    source(x + 1, z, 100.0)
                    source(x - 1, z, 50.0)
                }
                is mods.eln.sixnode.oscilloscope.OscilloscopeElement -> {
                    source(x + 1, z, 30.0)?.let { scopeSources.add(it) }
                    source(x - 1, z, 10.0)?.let { scopeSources.add(it) }
                }
            }
            node.reconnect()
        }
        val batteries = listOf("Lead-Acid Battery", "Nickel-Cadmium Battery", "Lithium-Ion Battery", "LiFePO4 Battery")
        for ((k, name) in batteries.withIndex()) {
            if (!Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(name, 1), player, level, x0 + k * 2, GROUND, z + 5, 1)) {
                Eln.logger.error("SMOKE could not place {}", name)
            }
        }
        for ((x, name) in listOf(x0 + 2 to "Monocrystalline Solar Panel", x0 + 12 to "Permanent-Magnet Wind Turbine")) {
            if (!Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(name, 1), player, level, x, GROUND, z + 12, 1)) {
                Eln.logger.error("SMOKE could not place {}", name)
            }
        }
    }

    /** Leaves the world, opens it again and compares the nodes. */
    private fun reload() {
        var before = emptyMap<String, Int>()
        var id = ""
        fun census() = NodeManager.instance!!.nodeList.groupingBy { it.javaClass.simpleName }.eachCount()
        later(20) {
            before = census()
            id = mc.singleplayerServer!!.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).parent.fileName.toString()
            mc.level!!.disconnect()
            mc.disconnect()
            mc.setScreen(TitleScreen())
            mc.createWorldOpenFlows().openWorld(id) { Eln.logger.error("SMOKE could not reopen the world") }
            wait = 100
        }
        later(60) { look(20.0, GROUND + 5.0, 20.5, 0f, 40f) }
        later(10) {
            shot("reloaded")
            val after = census()
            if (after == before) Eln.logger.info("SMOKE nodes survived a reload: {}", after)
            else Eln.logger.error("SMOKE nodes changed over a reload: {} -> {}", before, after)
        }
        later {
            Eln.logger.info("SMOKE done")
            mc.stop()
        }
    }

    private var guiCount = 0

    private fun gui(kind: String, name: String, pos: BlockPos, side: Direction, look: (AbstractContainerScreen<*>) -> Unit = {}) {
        later(4) {
            onServer { level, player ->
                val node = NodeManager.instance!!.nodeList.firstOrNull {
                    it.coordinate.x == pos.x && it.coordinate.y == pos.y && it.coordinate.z == pos.z
                }
                if (node != null && node.hasGui(side)) {
                    // Menus close when the player is out of reach.
                    player.teleportTo(level, pos.x + 0.5, pos.y + 1.5, pos.z + 0.5, 0f, 60f)
                    player.openGui(Eln.instance, GuiHandler.nodeBaseOpen + side.int, level, pos.x, pos.y, pos.z)
                }
            }
        }
        later(0) {
            val screen = mc.screen
            if (screen is GuiScreen || screen is GuiContainer) {
                guiCount++
                if (screen is AbstractContainerScreen<*>) look(screen)
                shot("gui_${kind}_" + name.lowercase().replace(Regex("[^a-z0-9]+"), "_"))
                screen.onClose()
            } else if (screen != null) {
                Eln.logger.error("SMOKE unexpected screen {} for {}", screen, name)
            }
        }
    }

    private fun placeAll(level: ServerLevel, player: ServerPlayer) {
        for (x in -2..45) for (z in -2..60) level.setBlock(BlockPos(x, GROUND - 1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2)
        var n = 0
        for ((id, descriptor) in Eln.sixNodeItem.subItemList.toSortedMap()) {
            val pos = BlockPos(n % 20 * 2, GROUND, n / 20 * 3)
            n++
            var ok = Eln.sixNodeItem.placeBlockAt(descriptor.newItemStack(1), player, level, pos.x, pos.y, pos.z, 1)
            if (!ok) {
                level.setBlock(pos.south(), Blocks.OAK_LOG.defaultBlockState(), 3)
                ok = Eln.sixNodeItem.placeBlockAt(descriptor.newItemStack(1), player, level, pos.x, pos.y, pos.z, 2)
            }
            if (!ok) {
                level.setBlock(pos.above(), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                ok = Eln.sixNodeItem.placeBlockAt(descriptor.newItemStack(1), player, level, pos.x, pos.y, pos.z, 0)
            }
            if (!ok) {
                Eln.logger.error("SMOKE could not place {} {}", id, descriptor.name)
                continue
            }
            val node = NodeManager.instance!!.nodeList.filterIsInstance<SixNode>()
                .first { it.coordinate.x == pos.x && it.coordinate.y == pos.y && it.coordinate.z == pos.z }
            val side = Direction.values().first { node.getSideEnable(it) }
            sixPositions.add(Triple(descriptor.name, pos, side))
        }
        n = 0
        for (x in 100..200) for (z in -5..150) level.setBlock(BlockPos(x, GROUND - 1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2)
        for ((id, descriptor) in Eln.transparentNodeItem.subItemList.toSortedMap()) {
            val pos = BlockPos(110 + n % 10 * 9, GROUND, n / 10 * 14)
            n++
            if (descriptor.mustHaveCeiling()) level.setBlock(pos.above(), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
            if (descriptor.mustHaveWall() || descriptor.mustHaveWallFrontInverse()) {
                level.setBlock(pos.south(), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                level.setBlock(pos.north(), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
            }
            if (!Eln.transparentNodeItem.placeBlockAt(descriptor.newItemStack(1), player, level, pos.x, pos.y, pos.z, 1)) {
                Eln.logger.error("SMOKE could not place {} {}", id, descriptor.name)
                continue
            }
            val node = NodeManager.instance!!.nodeList.filterIsInstance<TransparentNode>().last { it.elementId == id }
            transparentPositions.add(descriptor.name to BlockPos(node.coordinate.x, node.coordinate.y, node.coordinate.z))
        }
        Eln.logger.info("SMOKE placed {} six nodes, {} transparent nodes", sixPositions.size, transparentPositions.size)
    }
}

/** Tooltips and all the ways to draw each item. */
private class PlainItemScreen : GuiScreen() {
    private var done = false

    override fun drawScreen(x: Int, y: Int, partialTick: Float) {
        if (done) return
        done = true
        val g = mods.eln.compat.client.GL11.guiGraphics ?: return
        val mc = Minecraft.getInstance()
        var failures = 0
        var count = 0
        for (item in net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            val id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)
            if (id.namespace != Eln.MODID) continue
            count++
            val stack = net.minecraft.world.item.ItemStack(item)
            try {
                for (flag in listOf(net.minecraft.world.item.TooltipFlag.NORMAL, net.minecraft.world.item.TooltipFlag.ADVANCED)) {
                    stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, flag)
                }
                for (context in net.minecraft.world.item.ItemDisplayContext.entries) {
                    g.pose().pushPose()
                    mc.itemRenderer.renderStatic(
                        stack, context, 0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, g.pose(), g.bufferSource(), mc.level, 0
                    )
                    g.pose().popPose()
                }
                g.flush()
            } catch (e: Throwable) {
                failures++
                Eln.logger.error("SMOKE plain item {}", id, e)
            }
        }
        Eln.logger.info("SMOKE {} plain items, {} failures", count, failures)
    }
}

private class TooltipScreen(private val stack: net.minecraft.world.item.ItemStack) : GuiScreen() {
    override fun drawScreen(x: Int, y: Int, partialTick: Float) {
        drawDefaultBackground()
        val g = mods.eln.compat.client.GL11.guiGraphics ?: return
        g.renderItem(stack, 10, 10)
        g.renderTooltip(Minecraft.getInstance().font, stack, 30, 30)
    }
}

/** All icons of a creative tab at once. */
private class IconScreen(private val tab: GenericCreativeTab) : GuiScreen() {
    private val stacks by lazy { ArrayList<net.minecraft.world.item.ItemStack>().also { mods.eln.generic.CreativeTabPopulator.addEntries(tab, it) } }

    override fun drawScreen(x: Int, y: Int, partialTick: Float) {
        drawDefaultBackground()
        val g = mods.eln.compat.client.GL11.guiGraphics ?: return
        for ((i, stack) in stacks.withIndex()) {
            g.renderItem(stack, 6 + i % 22 * 18, 6 + i / 22 * 18)
        }
    }
}
