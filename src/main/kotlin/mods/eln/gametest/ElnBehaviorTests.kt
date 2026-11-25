package mods.eln.gametest

import mods.eln.Eln
import mods.eln.compat.OreDict
import mods.eln.compat.orNull
import mods.eln.craft.MachineRecipes
import mods.eln.fluid.FluidNames
import mods.eln.fluid.FuelRegistry
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.RecipesList
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.sixnode.TreeResinCollector.TreeResinCollectorElement
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.sixnode.electricalsource.ElectricalSourceElement
import mods.eln.sixnode.lampsocket.LampSocketContainer
import mods.eln.sixnode.lampsocket.LampSocketElement
import mods.eln.transparentnode.FuelGeneratorElement
import mods.eln.transparentnode.WireMachineElement
import mods.eln.transparentnode.electricalmachine.ElectricalMachineElement
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.HopperBlock
import net.minecraft.world.level.block.entity.HopperBlockEntity
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.neoforge.capabilities.BlockCapabilityCache
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs
import net.minecraft.core.Direction as McDirection

/** What players do with the mod. Run with `./gradlew runGameTestServer`. */
@GameTestHolder(Eln.MODID)
@PrefixGameTestTemplate(false)
object ElnBehaviorTests {
    private const val EMPTY = "empty"

    @Suppress("DEPRECATION")
    private fun player(helper: GameTestHelper): ServerPlayer = helper.makeMockServerPlayerInLevel()

    /** The machine this test has placed; others run next to it. */
    private fun ownNode(helper: GameTestHelper, id: Int): TransparentNode =
        NodeManager.instance!!.nodeList.filterIsInstance<TransparentNode>()
            .first { it.elementId == id && helper.bounds.contains(it.coordinate.toVec3()) }

    private fun floor(helper: GameTestHelper) {
        for (x in 0..10) for (z in 0..10) helper.setBlock(BlockPos(x, 0, z), Blocks.STONE)
    }

    private fun placeSix(helper: GameTestHelper, player: ServerPlayer, name: String, pos: BlockPos): SixNode {
        val p = helper.absolutePos(pos)
        val placed = Eln.sixNodeItem.placeBlockAt(Eln.findItemStack(name, 1), player, helper.level, p.x, p.y, p.z, 1)
        helper.assertTrue(placed, "$name was not placed")
        return NodeManager.instance!!.getNodeFromCoordonate(Coordinate(p.x, p.y, p.z, helper.level)) as SixNode
    }

    private fun source(helper: GameTestHelper, player: ServerPlayer, pos: BlockPos, voltage: Double) {
        (placeSix(helper, player, "Electrical Source", pos).getElement(Direction.YN) as ElectricalSourceElement).voltageSource.setVoltage(voltage)
    }

    private fun placeTransparent(helper: GameTestHelper, player: ServerPlayer, name: String, pos: BlockPos): TransparentNode {
        val p = helper.absolutePos(pos)
        val placed = Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(name, 1), player, helper.level, p.x, p.y, p.z, 1)
        helper.assertTrue(placed, "$name was not placed")
        return NodeManager.instance!!.getNodeFromCoordonate(Coordinate(p.x, p.y, p.z, helper.level)) as TransparentNode
    }

    /** A machine with power on all sides. */
    private fun machine(helper: GameTestHelper, player: ServerPlayer, name: String, pos: BlockPos, voltage: Double) =
        powered(helper, player, name, pos, voltage).element as ElectricalMachineElement

    private fun powered(helper: GameTestHelper, player: ServerPlayer, name: String, pos: BlockPos, voltage: Double): TransparentNode {
        floor(helper)
        val node = placeTransparent(helper, player, name, pos)
        for (d in McDirection.Plane.HORIZONTAL) source(helper, player, pos.relative(d), voltage)
        return node
    }

    /** Items as `/give` makes them, without any data. */
    @GameTest(template = EMPTY)
    @JvmStatic
    fun everyPlainItemCanBeUsed(helper: GameTestHelper) {
        val player = player(helper)
        player.setGameMode(GameType.SURVIVAL)
        val floor = BlockPos(5, 1, 5)
        val failures = ArrayList<String>()
        var used = 0
        for (item in BuiltInRegistries.ITEM) {
            val id = BuiltInRegistries.ITEM.getKey(item)
            if (id.namespace != Eln.MODID) continue
            floor(helper)
            helper.setBlock(floor, Blocks.STONE)
            val center = helper.absoluteVec(floor.center)
            player.moveTo(center.x, center.y + 1.0, center.z)
            val stack = ItemStack(item)
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, stack)
                stack.getUseDuration(player)
                stack.useAnimation
                stack.maxDamage
                stack.isBarVisible
                stack.getDestroySpeed(Blocks.STONE.defaultBlockState())
                stack.inventoryTick(helper.level, player, 0, true)
                stack.use(helper.level, player, InteractionHand.MAIN_HAND)
                val hit = BlockHitResult(center.add(0.0, 0.5, 0.0), McDirection.UP, helper.absolutePos(floor), false)
                stack.useOn(UseOnContext(player, InteractionHand.MAIN_HAND, hit))
                stack.mineBlock(helper.level, Blocks.STONE.defaultBlockState(), helper.absolutePos(floor), player)
                stack.onEntitySwing(player, InteractionHand.MAIN_HAND)
                if (!stack.isEmpty) stack.onDroppedByPlayer(player)
                used++
            } catch (e: Throwable) {
                // Mock players have no client to show a screen.
                if (e is UnsupportedOperationException && "advanced_open_screen" in e.message.orEmpty()) continue
                Eln.logger.error("using {}", id, e)
                failures.add("$id: $e")
            } finally {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                for (x in 0..10) for (y in 1..10) for (z in 0..10) {
                    val p = BlockPos(x, y, z)
                    if (!helper.getBlockState(p).isAir) helper.setBlock(p, Blocks.AIR)
                }
            }
        }
        Eln.logger.info("Used {} items", used)
        failures.forEach { Eln.logger.error("FAILED {}", it) }
        helper.assertTrue(failures.isEmpty(), failures.joinToString())
        helper.succeed()
    }

    @GameTest(template = EMPTY, timeoutTicks = 1200)
    @JvmStatic
    fun maceratorMacerates(helper: GameTestHelper) {
        val machine = machine(helper, player(helper), "48V Macerator", BlockPos(5, 1, 5), 48.0)
        val recipe = Eln.instance.maceratorRecipes.recipes.first()
        machine.inventory!!.setInventorySlotContents(machine.inSlotId, recipe.input.copyWithCount(1))
        helper.succeedWhen {
            val out = (machine.outSlotId until machine.inSlotId).mapNotNull { machine.inventory!!.getStackInSlot(it) }
            helper.assertTrue(out.any { it.item === recipe.output[0].item }, "nothing came out")
            helper.assertTrue(machine.inventory!!.getStackInSlot(machine.inSlotId) == null, "the input is still there")
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 1200)
    @JvmStatic
    fun wireMachinesMakeCables(helper: GameTestHelper) {
        val player = player(helper)
        fun wireMachine(name: String, pos: BlockPos, vararg inputs: ItemStack): WireMachineElement {
            val machine = powered(helper, player, name, pos, 200.0).element as WireMachineElement
            inputs.forEachIndexed { slot, stack -> machine.inventory.setInventorySlotContents(slot, stack) }
            return machine
        }

        fun wire(name: String) = Eln.findItemStack(name, 1).also {
            (Eln.sixNodeItem.getDescriptor(it) as UtilityCableDescriptor).setRemainingLengthMeters(it, 20.0)
        }

        fun wheel() = Eln.findItemStack("Iron Roller Wheel", 1)
        val roller = wireMachine("Wire Roller", BlockPos(2, 1, 2), ItemStack(Items.COPPER_INGOT, 10), wheel(), wheel())
        val insulator = wireMachine(
            "Wire Insulator", BlockPos(8, 1, 2),
            wire("Copper 14 AWG Cable Bare"), ItemStack(OreDict.getOres("itemRubber").first().item, 64)
        )
        // Two and the ground.
        val wires = Array(3) { wire("Copper 14 AWG Cable 600V") }
        val combiner = wireMachine("Wire Combiner", BlockPos(5, 1, 8), *wires)
        helper.succeedWhen {
            fun made(machine: WireMachineElement, slot: Int) =
                machine.inventory.getStackInSlot(slot)?.let { BuiltInRegistries.ITEM.getKey(it.item).path }
            helper.assertTrue(made(roller, 3).orEmpty().endsWith("_cable_bare"), "the roller made ${made(roller, 3)}")
            helper.assertTrue(made(insulator, 2) == "copper_14_awg_cable_600v", "the insulator made ${made(insulator, 2)}")
            helper.assertTrue(made(combiner, 5) == "wound_wire_bundle", "the combiner made ${made(combiner, 5)}")
        }
    }

    /** NeoForge has tags that all kinds of ores are in. */
    @GameTest(template = EMPTY)
    @JvmStatic
    fun machinesTellMaterialsApart(helper: GameTestHelper) {
        fun expect(list: RecipesList, input: ItemStack, output: String?) {
            val found = list.getRecipe(input.copyWithCount(64))?.let { BuiltInRegistries.ITEM.getKey(it.output[0].item).path }
            helper.assertTrue(found == output, "$input becomes $found instead of $output")
        }
        with(Eln.instance) {
            for (ore in listOf(Items.IRON_ORE, Items.DEEPSLATE_IRON_ORE)) expect(maceratorRecipes, ItemStack(ore), "iron_dust")
            for (ore in listOf(Items.GOLD_ORE, Items.DEEPSLATE_GOLD_ORE)) expect(maceratorRecipes, ItemStack(ore), "gold_dust")
            for (ore in listOf(Items.COPPER_ORE, Items.DEEPSLATE_COPPER_ORE)) expect(maceratorRecipes, ItemStack(ore), "copper_dust")
            for (ore in listOf(Items.COAL_ORE, Items.DEEPSLATE_COAL_ORE)) expect(maceratorRecipes, ItemStack(ore), "coal")
            for ((raw, dust) in listOf(Items.RAW_IRON to "iron_dust", Items.RAW_GOLD to "gold_dust", Items.RAW_COPPER to "copper_dust")) {
                expect(maceratorRecipes, ItemStack(raw), dust)
            }
            expect(arcFurnaceRecipes, ItemStack(Items.RAW_IRON), "iron_ingot")
            expect(arcFurnaceRecipes, ItemStack(Items.RAW_GOLD), "gold_ingot")
            expect(arcFurnaceRecipes, ItemStack(Items.RAW_COPPER), "copper_ingot")
            expect(maceratorRecipes, Eln.findItemStack("Lead Ore", 1), "lead_dust")
            expect(maceratorRecipes, ItemStack(Items.SAND), "silicon_dust")
            expect(maceratorRecipes, ItemStack(Items.EMERALD_ORE), null)
            expect(maceratorRecipes, ItemStack(Items.BLUE_DYE), null)
            expect(arcFurnaceRecipes, ItemStack(Items.DIAMOND_ORE), "diamond")
            expect(arcFurnaceRecipes, ItemStack(Items.EMERALD_ORE), "emerald")
            expect(compressorRecipes, ItemStack(Items.BIRCH_LOG), "tree_resin")
        }
        helper.succeed()
    }

    /** Recipe viewers show what machines can do. */
    @GameTest(template = EMPTY)
    @JvmStatic
    fun machineRecipesAreListed(helper: GameTestHelper) {
        val failures = ArrayList<String>()
        for (kind in MachineRecipes.all()) {
            Eln.logger.info("{}: {} recipes, machines {}", kind.id, kind.recipes.size, kind.machines)
            if (kind.machines.isEmpty() || kind.recipes.isEmpty()) failures.add("${kind.id}: nothing to show")
            if (kind.machines.distinctBy { it.item }.size != kind.machines.size) failures.add("${kind.id}: machines twice")
            val list = kind.list
            if (list != null && kind.recipes.size != list.recipes.size) {
                list.recipes.filter { list.getInputs(it).isEmpty() }.forEach { failures.add("${kind.id}: nothing makes ${it.output[0]} of ${it.input}") }
            }
            for (recipe in kind.recipes) {
                val stacks = (recipe.inputs + recipe.tools).onEach { if (it.isEmpty()) failures.add("${kind.id}: nothing to put in for ${recipe.outputs}") }
                if ((stacks.flatten() + recipe.outputs).any { it.isEmpty }) failures.add("${kind.id}: empty stack for ${recipe.outputs}")
                if (recipe.outputs.isEmpty()) failures.add("${kind.id}: no output")
            }
        }
        val macerator = MachineRecipes.all().first { it.id == "macerator" }
        helper.assertTrue(macerator.machines.size == 2, "macerators: ${macerator.machines}")
        helper.assertTrue(
            macerator.recipes.any { r -> r.inputs[0].any { it.`is`(Items.SAND) } && r.outputs[0].item === Eln.findItemStack("Silicon Dust", 1).item },
            "sand is missing"
        )
        failures.forEach { Eln.logger.error("FAILED {}", it) }
        helper.assertTrue(failures.isEmpty(), failures.joinToString())
        helper.succeed()
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    @JvmStatic
    fun hopperFeedsMachine(helper: GameTestHelper) {
        val pos = BlockPos(5, 1, 5)
        val machine = machine(helper, player(helper), "48V Macerator", pos, 0.0)
        val input = Eln.instance.maceratorRecipes.recipes.first().input.item
        helper.setBlock(pos.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, McDirection.DOWN))
        helper.getBlockEntity<HopperBlockEntity>(pos.above()).setItem(0, ItemStack(input, 3))
        helper.succeedWhen {
            // Ticks are over in no time here, while the chunks of a new world take a moment until they tick blocks.
            val hopperPos = helper.absolutePos(pos.above())
            val hopper = helper.getBlockEntity<HopperBlockEntity>(pos.above())
            HopperBlockEntity.pushItemsTick(helper.level, hopperPos, helper.level.getBlockState(hopperPos), hopper)
            helper.assertTrue(machine.inventory!!.getStackInSlot(machine.inSlotId)?.count == 3, "the hopper did not feed the machine")
        }
    }

    /** As importers and item pipes do it. */
    @GameTest(template = EMPTY)
    @JvmStatic
    fun pipesTakeTreeResin(helper: GameTestHelper) {
        val player = player(helper)
        val level = helper.level
        floor(helper)
        val tree = BlockPos(5, 1, 5)
        val pos = tree.north().above()
        val p = helper.absolutePos(pos)
        for (y in 0..3) helper.setBlock(tree.above(y), Blocks.OAK_LOG)
        helper.setBlock(pos.above(2), Blocks.OAK_LEAVES)
        fun handler(side: McDirection?) = level.getCapability(Capabilities.ItemHandler.BLOCK, p, side)
        // Some pipes look once and then wait to be told.
        val cache = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level, p, McDirection.NORTH)
        helper.assertTrue(cache.capability == null, "there are items in the air")

        // A cable has no items, the block gets them along with the collector.
        helper.setBlock(pos.below(), Blocks.STONE)
        fun place(name: String, face: Int) = helper.assertTrue(
            Eln.sixNodeItem.placeBlockAt(Eln.findItemStack(name, 1), player, level, p.x, p.y, p.z, face), "$name was not placed"
        )
        place("Low Voltage Cable", 1)
        helper.assertTrue(cache.capability == null && handler(null) == null, "a cable has items")
        place("Tree Resin Collector", McDirection.NORTH.get3DDataValue())
        for (side in McDirection.entries + null) helper.assertTrue(handler(side) != null, "pipes do not find the collector from $side")
        helper.assertTrue(cache.capability != null, "the pipe was not told of the collector")

        val node = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(p.x, p.y, p.z, level)) as SixNode
        val element = node.sideElementList.filterIsInstance<TreeResinCollectorElement>().single()
        fun fill(amount: Double) {
            val tag = CompoundTag()
            element.writeToNBT(tag)
            tag.putDouble("timeFromLastActivated", amount / element.productPerSecond)
            element.readFromNBT(tag)
        }

        fun left(): Double {
            val tag = CompoundTag()
            element.writeToNBT(tag)
            return tag.getDouble("timeFromLastActivated") * element.productPerSecond
        }

        fun take(amount: Int, simulate: Boolean = false): Int {
            val items = cache.capability!!
            return (0 until items.slots).sumOf { slot ->
                items.extractItem(slot, amount, simulate).also {
                    helper.assertTrue(it.isEmpty || it.item === Eln.treeResin.newItemStack(1).item, "there is $it in the collector")
                }.count
            }
        }

        fun expect(filled: Double, amount: Int, taken: Int, rest: Double) {
            fill(filled)
            helper.assertTrue(take(amount, true) == taken, "$amount of $filled should give $taken")
            helper.assertTrue(abs(left() - filled) < 1e-6, "looking took resin")
            helper.assertTrue(take(amount) == taken, "$amount of $filled did not give $taken")
            helper.assertTrue(abs(left() - rest) < 1e-6, "$filled left ${left()} instead of $rest")
        }
        helper.assertTrue(element.productPerSecond > 1e-6, "the tree makes no resin")
        expect(0.0, 64, 0, 0.0)
        expect(0.99, 64, 0, 0.99)
        expect(1.0, 64, 1, 0.0)
        expect(1.7, 64, 1, 0.7)
        expect(2.0, 1, 1, 1.0)
        helper.assertTrue(take(1) == 1 && take(1) == 0, "the second piece was not there")
        // It holds two, however long it is left alone.
        expect(50.0, 64, 2, 0.0)
        expect(50.0, 1, 1, 1.0)
        val items = cache.capability!!
        val resin = Eln.treeResin.newItemStack(1)
        helper.assertTrue((0 until items.slots).all { items.insertItem(it, resin, false).count == 1 }, "resin went into the collector")

        // A right click does the same.
        fill(1.7)
        element.onBlockActivated(player, element.side, 0f, 0f, 0f)
        helper.assertTrue(abs(left() - 0.7) < 1e-6, "a click left ${left()}")

        fill(2.0)
        helper.setBlock(pos.below(), Blocks.HOPPER)
        val hopper = helper.getBlockEntity<HopperBlockEntity>(pos.below())
        repeat(3) { HopperBlockEntity.suckInItems(level, hopper) }
        helper.assertTrue((0 until hopper.containerSize).sumOf { hopper.getItem(it).count } == 2, "the hopper did not take the resin")

        helper.setBlock(tree.above(), Blocks.AIR)
        helper.runAfterDelay(2) {
            helper.assertTrue(cache.capability == null, "the collector is gone, but not its items")
            helper.succeed()
        }
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun itemHandlersWorkFromAllSides(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val failures = ArrayList<String>()
        for ((id, descriptor) in Eln.transparentNodeItem.subItemList.toSortedMap()) {
            val pos = BlockPos(5, 1, 5)
            val p = helper.absolutePos(pos)
            try {
                if (!Eln.transparentNodeItem.placeBlockAt(descriptor.newItemStack(1), player, helper.level, p.x, p.y, p.z, 1)) continue
                val node = ownNode(helper, id)
                for (side in McDirection.entries + null) {
                    val handler = helper.level.getCapability(Capabilities.ItemHandler.BLOCK, node.coordinate.blockPos, side) ?: continue
                    for (slot in 0 until handler.slots) {
                        handler.getStackInSlot(slot)
                        handler.getSlotLimit(slot)
                        handler.isItemValid(slot, ItemStack(Items.IRON_INGOT))
                        handler.insertItem(slot, ItemStack(Items.IRON_INGOT), true)
                        handler.extractItem(slot, 1, true)
                    }
                }
                for (side in McDirection.entries + null) {
                    val tank = helper.level.getCapability(Capabilities.FluidHandler.BLOCK, node.coordinate.blockPos, side) ?: continue
                    for (t in 0 until tank.tanks) {
                        tank.getFluidInTank(t)
                        tank.getTankCapacity(t)
                    }
                }
                helper.level.removeBlock(node.coordinate.blockPos, false)
            } catch (e: Throwable) {
                Eln.logger.error("capabilities of {}", descriptor.name, e)
                failures.add("${descriptor.name}: $e")
            } finally {
                for (x in 0..10) for (y in 1..10) for (z in 0..10) {
                    val q = BlockPos(x, y, z)
                    if (!helper.getBlockState(q).isAir) helper.setBlock(q, Blocks.AIR)
                }
            }
        }
        helper.assertTrue(failures.isEmpty(), failures.joinToString())
        helper.succeed()
    }

    /** With mods that have fuels in run/mods, this is about theirs. */
    @GameTest(template = EMPTY)
    @JvmStatic
    fun enginesTakeFuelsOfAllMods(helper: GameTestHelper) {
        val failures = ArrayList<String>()
        val fluids = BuiltInRegistries.FLUID.filter { it !== Fluids.EMPTY && it.defaultFluidState().isSource }
        // Those of other mods do not cope with players that have no client.
        val player = FakePlayerFactory.getMinecraft(helper.level)
        for (name in listOf("48V Fuel Generator", "Gas Turbine", "Radial Motor", "Fuel Heat Furnace", "Steam Turbine")) {
            val stack = Eln.findItemStack(name, 1)
            val descriptor = Eln.transparentNodeItem.getDescriptor(stack) as TransparentNodeDescriptor
            // Some stand on something, and others reach below where they stand.
            helper.setBlock(BlockPos(5, 3, 5), Blocks.STONE)
            val p = helper.absolutePos(BlockPos(5, 4, 5))
            helper.assertTrue(Eln.transparentNodeItem.placeBlockAt(stack, player, helper.level, p.x, p.y, p.z, 1), "$name was not placed")
            val node = ownNode(helper, descriptor.parentItemDamage)
            val taken = ArrayList<String>()
            for (fluid in fluids) {
                val tank = helper.level.getCapability(Capabilities.FluidHandler.BLOCK, node.coordinate.blockPos, McDirection.UP)
                if (tank == null) {
                    failures.add("$name has no tank")
                    break
                }
                val filled = tank.fill(FluidStack(fluid, 1000), IFluidHandler.FluidAction.SIMULATE)
                if (filled == 0) continue
                taken.add("${FluidNames.idOf(fluid)}=${FuelRegistry.nameOf(fluid)}")
                if (FuelRegistry.heatEnergyPerMilliBucket(fluid) <= 0) failures.add("$name gets nothing out of ${FluidNames.idOf(fluid)}")
            }
            Eln.logger.info("{} takes {} fluids: {}", name, taken.size, taken)
            val generator = node.element
            if (generator is FuelGeneratorElement && taken.size > 1) {
                val tank = helper.level.getCapability(Capabilities.FluidHandler.BLOCK, node.coordinate.blockPos, McDirection.UP)!!
                val (fuel, other) = taken.take(2).map { BuiltInRegistries.FLUID.get(ResourceLocation.parse(it.substringBefore('='))) }
                fun fill(fluid: Fluid) = tank.fill(FluidStack(fluid, 1500), IFluidHandler.FluidAction.EXECUTE)
                val filled = listOf(fill(fuel), fill(other), fill(fuel), fill(fuel))
                if (filled != listOf(1500, 0, 500, 0)) failures.add("$name was filled with $filled")
                val nbt = CompoundTag()
                generator.writeToNBT(nbt)
                generator.tankFluid = Fluids.EMPTY
                generator.readFromNBT(nbt)
                if (generator.tankFluid !== fuel || generator.tankLevel != 1.0) failures.add("$name forgot its fuel")
                // By hand, in halves of the tank.
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(fuel.bucket))
                generator.onBlockActivated(player, Direction.YP, 0f, 0f, 0f)
                if (player.mainHandItem.item !== fuel.bucket) failures.add("$name took a bucket when full")
                generator.tankLevel = 0.4
                generator.onBlockActivated(player, Direction.YP, 0f, 0f, 0f)
                if (abs(generator.tankLevel - 0.9) > 1e-9 || player.mainHandItem.item === fuel.bucket) failures.add("$name took no bucket: ${generator.tankLevel} ${player.mainHandItem}")
                if (!player.inventory.contains(ItemStack(Items.BUCKET))) failures.add("$name kept the bucket")
                player.inventory.clearContent()
            }
            for (water in listOf(Fluids.WATER, Fluids.LAVA)) if (taken.any { it.startsWith(FluidNames.idOf(water) + "=") }) failures.add("$name takes $water")
            for (x in 0..10) for (y in 1..10) for (z in 0..10) {
                if (!helper.getBlockState(BlockPos(x, y, z)).isAir) helper.setBlock(BlockPos(x, y, z), Blocks.AIR)
            }
        }
        for (fluid in fluids) {
            val id = BuiltInRegistries.FLUID.getKey(fluid)
            if (id.namespace != "minecraft" && FuelRegistry.nameOf(fluid).isEmpty()) Eln.logger.info("no fuel: {}", id)
        }
        helper.assertTrue(failures.isEmpty(), failures.joinToString())
        helper.succeed()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun brokenMachineDropsItsContents(helper: GameTestHelper) {
        val pos = BlockPos(5, 1, 5)
        val machine = machine(helper, player(helper), "48V Macerator", pos, 0.0)
        machine.inventory!!.setInventorySlotContents(machine.inSlotId, ItemStack(Items.DIAMOND, 7))
        val box = AABB(helper.absolutePos(pos)).inflate(3.0)
        helper.level.destroyBlock(helper.absolutePos(pos), true)
        helper.runAfterDelay(2) {
            val drops = helper.level.getEntitiesOfClass(ItemEntity::class.java, box).map { it.item }
            helper.assertTrue(drops.filter { it.`is`(Items.DIAMOND) }.sumOf { it.count } == 7, "contents: $drops")
            helper.assertTrue(drops.any { it.item === Eln.findItemStack("48V Macerator", 1).item }, "the machine: $drops")
            helper.succeed()
        }
    }

    /** An open GUI must not hold on to what a broken machine has dropped. */
    @GameTest(template = EMPTY)
    @JvmStatic
    fun openGuiClosesWithItsMachine(helper: GameTestHelper) {
        val player = player(helper)
        val pos = BlockPos(5, 1, 5)
        val machine = machine(helper, player, "48V Macerator", pos, 0.0)
        val center = helper.absoluteVec(pos.center)
        player.moveTo(center.x, center.y + 1.0, center.z)
        machine.inventory!!.setInventorySlotContents(machine.inSlotId, ItemStack(Items.DIAMOND, 7))
        val menu = machine.newContainer(Direction.YP, player)!!
        helper.assertTrue(menu.stillValid(player), "cannot be opened")
        player.moveTo(center.x + 40.0, center.y, center.z)
        helper.assertFalse(menu.stillValid(player), "usable from far away")
        player.moveTo(center.x, center.y + 1.0, center.z)
        helper.level.destroyBlock(helper.absolutePos(pos), true)
        helper.assertFalse(menu.stillValid(player), "usable after it was broken")
        helper.assertTrue(menu.slots.none { it.item.`is`(Items.DIAMOND) }, "the contents exist twice")
        helper.succeed()
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    @JvmStatic
    fun lampLightsUp(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val pos = BlockPos(5, 1, 5)
        val socket = placeSix(helper, player, "Simple Lamp Socket", pos).getElement(Direction.YN) as LampSocketElement
        for (d in McDirection.Plane.HORIZONTAL) source(helper, player, pos.relative(d), 120.0)
        socket.inventory.setInventorySlotContents(LampSocketContainer.LAMP_SLOT_ID, Eln.findItemStack("120V Incandescent Light Bulb", 1).orNull())
        socket.inventory.setInventorySlotContents(LampSocketContainer.CABLE_SLOT_ID, Eln.findItemStack("Low Voltage Cable", 1))
        socket.poweredByLampSupply = false
        socket.sixNode!!.reconnect()
        helper.succeedWhen {
            val state = helper.getBlockState(pos)
            helper.assertTrue(state.getLightEmission(helper.level, helper.absolutePos(pos)) > 5, "the lamp is dark")
        }
    }

    /** A block that is not a node must not make circuits get rebuilt. */
    @GameTest(template = EMPTY)
    @JvmStatic
    fun circuitsOnlyChangeWhenTheyHaveTo(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val pos = BlockPos(5, 1, 5)
        val cable = placeSix(helper, player, "Low Voltage Cable", pos)
        placeSix(helper, player, "Low Voltage Cable", pos.east())
        helper.assertTrue(cable.nodeConnectionList.size == 1, "not connected")
        val connection = cable.nodeConnectionList[0]

        helper.level.updateNeighborsAt(helper.absolutePos(pos.above()), Blocks.STONE)
        helper.assertTrue(cable.nodeConnectionList.single() === connection, "reconnected for no reason")

        // A block above closes nothing here, one around the corner does.
        helper.setBlock(pos.west(), Blocks.STONE)
        helper.assertTrue(cable.nodeConnectionList.size == 1, "lost the connection")
        helper.setBlock(pos.west(), Blocks.AIR)
        helper.assertTrue(cable.nodeConnectionList.size == 1, "lost the connection")
        helper.succeed()
    }

    /** Cables connect around the edge of a block, until something is put there. */
    @GameTest(template = EMPTY)
    @JvmStatic
    fun cablesWrapAroundEdges(helper: GameTestHelper) {
        val player = player(helper)
        val block = BlockPos(5, 3, 5)
        helper.setBlock(block, Blocks.STONE)
        val top = placeSix(helper, player, "Low Voltage Cable", block.above())
        val sidePos = helper.absolutePos(block.east())
        helper.assertTrue(
            Eln.sixNodeItem.placeBlockAt(Eln.findItemStack("Low Voltage Cable", 1), player, helper.level, sidePos.x, sidePos.y, sidePos.z, McDirection.EAST.get3DDataValue()),
            "the cable on the side was not placed"
        )
        helper.assertTrue(top.nodeConnectionList.size == 1, "not connected around the edge")
        helper.setBlock(block.above().east(), Blocks.STONE)
        helper.assertTrue(top.nodeConnectionList.isEmpty(), "still connected through a block")
        helper.setBlock(block.above().east(), Blocks.AIR)
        helper.assertTrue(top.nodeConnectionList.size == 1, "not connected again")
        helper.succeed()
    }
}
