package mods.eln.gametest

import mods.eln.Eln
import mods.eln.compat.orNull
import mods.eln.craft.CraftingRecipes
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.NodeBase
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.sixnode.electricalsource.ElectricalSourceElement
import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.neoforged.neoforge.network.connection.ConnectionType

/** In-world smoke tests; run with `./gradlew runGameTestServer`. */
@GameTestHolder(Eln.MODID)
@PrefixGameTestTemplate(false)
object ElnGameTests {
    private const val EMPTY = "empty"

    private fun coordinate(helper: GameTestHelper, pos: BlockPos): Coordinate {
        val p = helper.absolutePos(pos)
        return Coordinate(p.x, p.y, p.z, helper.level)
    }

    private fun node(helper: GameTestHelper, pos: BlockPos): NodeBase? =
        NodeManager.instance!!.getNodeFromCoordonate(coordinate(helper, pos))

    @Suppress("DEPRECATION")
    private fun player(helper: GameTestHelper): ServerPlayer = helper.makeMockServerPlayerInLevel()

    private fun walls(helper: GameTestHelper, pos: BlockPos) {
        for (d in net.minecraft.core.Direction.entries) helper.setBlock(pos.relative(d), Blocks.STONE)
    }

    private fun clear(helper: GameTestHelper, pos: BlockPos) {
        helper.setBlock(pos, Blocks.AIR)
        for (x in -4..4) for (y in -4..4) for (z in -4..4) {
            val p = pos.offset(x, y, z)
            if (!helper.getBlockState(p).isAir) helper.setBlock(p, Blocks.AIR)
        }
    }

    /** Publishing, saving and loading must work for any node. */
    private fun exercise(node: NodeBase) {
        node.publishPacket ?: throw IllegalStateException("no packet")
        val tag = CompoundTag()
        node.writeToNBT(tag)
        for (side in Direction.values()) {
            node.multiMeterString(side)
            node.thermoMeterString(side)
        }
        val copy = node.javaClass.getConstructor().newInstance()
        copy.readFromNBT(tag)
        copy.unload()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun cablesConnect(helper: GameTestHelper) {
        val player = player(helper)
        val a = BlockPos(3, 2, 3)
        val b = a.east()
        for (p in listOf(a, b)) {
            helper.setBlock(p.below(), Blocks.STONE)
            val abs = helper.absolutePos(p)
            val stack = Eln.findItemStack("Low Voltage Cable", 1)
            helper.assertTrue(Eln.sixNodeItem.placeBlockAt(stack, player, helper.level, abs.x, abs.y, abs.z, 1), "cable was not placed")
        }
        helper.assertTrue(node(helper, a) is SixNode, "no node")
        helper.assertTrue(node(helper, a)!!.nodeConnectionList.size == 1, "cables did not connect")
        helper.setBlock(a.below(), Blocks.AIR)
        helper.runAfterDelay(2) {
            helper.assertBlockPresent(Blocks.AIR, a)
            helper.assertTrue(node(helper, a) == null, "node outlived its block")
            helper.assertTrue(node(helper, b)!!.nodeConnectionList.isEmpty(), "connection outlived the cable")
            helper.succeed()
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    @JvmStatic
    fun powerFlows(helper: GameTestHelper) {
        val player = player(helper)
        fun place(name: String, pos: BlockPos): SixNode {
            helper.setBlock(pos.below(), Blocks.STONE)
            val abs = helper.absolutePos(pos)
            helper.assertTrue(
                Eln.sixNodeItem.placeBlockAt(Eln.findItemStack(name, 1), player, helper.level, abs.x, abs.y, abs.z, 1),
                "$name was not placed"
            )
            return node(helper, pos) as SixNode
        }

        val source = place("Electrical Source", BlockPos(2, 2, 3)).getElement(Direction.YN) as ElectricalSourceElement
        val cable = place("Low Voltage Cable", BlockPos(3, 2, 3))
        source.voltageSource.setVoltage(50.0)
        helper.runAfterDelay(40) {
            val load = Direction.values().firstNotNullOfOrNull { side ->
                LRDU.values().firstNotNullOfOrNull { cable.getElectricalLoad(side, it, NodeBase.maskElectricalPower) }
            }
            helper.assertTrue(load != null, "cable has no load")
            helper.assertTrue(load!!.voltage > 49.0, "cable is at ${load.voltage} V")
            helper.succeed()
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 4000)
    @JvmStatic
    fun everySixNode(helper: GameTestHelper) {
        val player = player(helper)
        val pos = BlockPos(5, 5, 5)
        val abs = helper.absolutePos(pos)
        val failures = ArrayList<String>()
        var placed = 0
        for ((id, descriptor) in Eln.sixNodeItem.subItemList.toSortedMap()) {
            try {
                var ok = false
                for (face in intArrayOf(1, 0, 2, 3, 4, 5)) {
                    walls(helper, pos)
                    if (Eln.sixNodeItem.placeBlockAt(descriptor.newItemStack(1), player, helper.level, abs.x, abs.y, abs.z, face)) {
                        ok = true
                        break
                    }
                    clear(helper, pos)
                }
                if (!ok) {
                    // Things taller than a block, on a bare floor.
                    helper.setBlock(pos.below(), Blocks.STONE)
                    ok = Eln.sixNodeItem.placeBlockAt(descriptor.newItemStack(1), player, helper.level, abs.x, abs.y, abs.z, 1)
                }
                if (!ok) {
                    failures.add("${descriptor.name} ($id): could not be placed")
                    continue
                }
                placed++
                val node = node(helper, pos) as SixNode
                exercise(node)
                for (side in Direction.values()) {
                    val element = node.getElement(side) ?: continue
                    element.getWaila()
                    if (element.hasGui()) element.newContainer(side, player)
                    element.dropItemStack.orNull() ?: failures.add("${descriptor.name}: no drop")
                }
            } catch (e: Throwable) {
                Eln.logger.error("six node ${descriptor.name}", e)
                failures.add("${descriptor.name} ($id): $e")
            } finally {
                clear(helper, pos)
            }
            if (node(helper, pos) != null) failures.add("${descriptor.name}: node left behind")
        }
        Eln.logger.info("Placed {} six nodes", placed)
        failures.forEach { Eln.logger.error("FAILED {}", it) }
        helper.assertTrue(failures.isEmpty(), failures.joinToString())
        helper.succeed()
    }

    @GameTest(template = EMPTY, timeoutTicks = 4000)
    @JvmStatic
    fun everyTransparentNode(helper: GameTestHelper) {
        val player = player(helper)
        val pos = BlockPos(5, 1, 5)
        val abs = helper.absolutePos(pos)
        val failures = ArrayList<String>()
        var placed = 0
        for ((id, descriptor) in Eln.transparentNodeItem.subItemList.toSortedMap()) {
            try {
                val ok = (0..3).any { support ->
                    clear(helper, pos)
                    if (support > 0 || descriptor.mustHaveFloor()) helper.setBlock(pos.below(), Blocks.STONE)
                    if (support == 3) {
                        for (x in 0..10) for (z in 0..10) helper.setBlock(BlockPos(x, 0, z), Blocks.STONE)
                    }
                    if (support == 2) {
                        for (d in net.minecraft.core.Direction.Plane.HORIZONTAL) helper.setBlock(pos.relative(d), Blocks.STONE)
                        if (descriptor.mustHaveCeiling()) helper.setBlock(pos.above(), Blocks.STONE)
                    }
                    Eln.transparentNodeItem.placeBlockAt(descriptor.newItemStack(1), player, helper.level, abs.x, abs.y, abs.z, 1)
                }
                if (!ok) {
                    failures.add("${descriptor.name} ($id): could not be placed")
                    continue
                }
                placed++
                val node = NodeManager.instance!!.nodeList.filterIsInstance<TransparentNode>().first { it.elementId == id }
                exercise(node)
                val element = node.element!!
                element.getWaila()
                if (element.hasGui()) for (side in Direction.values()) element.newContainer(side, player)
                element.dropItemStack.orNull() ?: failures.add("${descriptor.name}: no drop")
                helper.level.removeBlock(BlockPos(node.coordinate.x, node.coordinate.y, node.coordinate.z), false)
                if (NodeManager.instance!!.getNodeFromCoordonate(node.coordinate) != null) failures.add("${descriptor.name}: node left behind")
            } catch (e: Throwable) {
                Eln.logger.error("transparent node ${descriptor.name}", e)
                failures.add("${descriptor.name} ($id): $e")
            } finally {
                clear(helper, pos)
            }
        }
        Eln.logger.info("Placed {} transparent nodes", placed)
        failures.forEach { Eln.logger.error("FAILED {}", it) }
        helper.assertTrue(failures.isEmpty(), failures.joinToString())
        helper.succeed()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun recipesResolve(helper: GameTestHelper) {
        val failures = ArrayList<String>()
        val unsendable = ArrayList<String>()
        val recipes = CraftingRecipes.buildCrafting()
        helper.assertTrue(recipes.size > 300, "only ${recipes.size} recipes")
        for (holder in recipes) {
            val recipe = holder.value
            try {
                val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), helper.level.registryAccess(), ConnectionType.NEOFORGE)
                RecipeHolder.STREAM_CODEC.encode(buffer, holder)
                RecipeHolder.STREAM_CODEC.decode(buffer)
            } catch (e: Exception) {
                unsendable.add("${holder.id()}: $e")
            }
            if (recipe !is CraftingRecipe) continue
            for (ingredient in recipe.ingredients) {
                if (ingredient.isEmpty) continue
                // Barriers stand in for tags without members.
                if (ingredient.items.isEmpty() || ingredient.items.any { it.`is`(Items.BARRIER) }) {
                    failures.add("${holder.id()}: ${ingredient.values.map { v -> v.items.firstOrNull()?.hoverName?.string }}")
                }
            }
        }
        val present = helper.level.recipeManager.recipes.count { it.id().namespace == Eln.MODID }
        Eln.logger.info("{} recipes built, {} active", recipes.size, present)
        with(Eln.instance) {
            Eln.logger.info(
                "machine recipes: macerator {}, compressor {}, plate {}, arc {}, magnetizer {}",
                maceratorRecipes.recipes.size, compressorRecipes.recipes.size,
                plateMachineRecipes.recipes.size, arcFurnaceRecipes.recipes.size, magnetiserRecipes.recipes.size
            )
        }
        failures.forEach { Eln.logger.error("UNCRAFTABLE {}", it) }
        helper.assertTrue(unsendable.isEmpty(), unsendable.joinToString())
        helper.assertTrue(present > 300, "only $present recipes are active")
        helper.assertTrue(Eln.instance.maceratorRecipes.recipes.isNotEmpty(), "no macerator recipes")
        helper.succeed()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun registryIsComplete(helper: GameTestHelper) {
        val items = BuiltInRegistries.ITEM.keySet().count { it.namespace == Eln.MODID }
        Eln.logger.info("{} items", items)
        helper.assertTrue(items > 500, "only $items items")
        helper.succeed()
    }
}
