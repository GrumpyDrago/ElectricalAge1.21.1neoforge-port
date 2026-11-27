package mods.eln.gametest

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import mods.eln.Eln
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.TooltipFlag
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.io.File

/**
 * Writes every item of the mod's creative tabs, with its tooltip, to the file named by the system property
 * `eln.dumpGuide`, for script/guide_pages.py to make the guidebook's item pages from. Does nothing without it.
 */
@GameTestHolder(Eln.MODID)
@PrefixGameTestTemplate(false)
object ElnGuideDump {
    @GameTest(template = "empty")
    @JvmStatic
    fun dumpItemsForTheGuide(helper: GameTestHelper) {
        val path = System.getProperty("eln.dumpGuide")
        if (path.isNullOrEmpty()) {
            helper.succeed()
            return
        }
        val level = helper.level
        @Suppress("DEPRECATION")
        val player = helper.makeMockServerPlayerInLevel()
        val parameters = CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess())
        val out = JsonArray()
        for (tab in BuiltInRegistries.CREATIVE_MODE_TAB) {
            val key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab) ?: continue
            if (key.namespace != Eln.MODID) continue
            tab.buildContents(parameters)
            val items = JsonArray()
            val seen = HashSet<String>()
            for (stack in tab.displayItems) {
                val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
                if (!seen.add(id)) continue
                val item = JsonObject()
                item.addProperty("id", id)
                item.addProperty("name", stack.hoverName.string)
                val lines = JsonArray()
                try {
                    // The descriptions come from the descriptors; the item's own tooltip is only built on the client.
                    val text = ArrayList<String>()
                    val block = mods.eln.generic.GenericItemBlockUsingDamageDescriptor.getDescriptor(stack)
                    val plain = mods.eln.generic.GenericItemUsingDamageDescriptor.getDescriptor(stack)
                    when {
                        block != null -> block.addInformation(stack, player, text, false)
                        plain != null -> plain.addInformation(stack, player, text, false)
                        else -> stack.getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.NORMAL).drop(1)
                            .forEach { text.add(it.string) }
                    }
                    text.forEach { lines.add(it.replace(Regex("§."), "")) }
                } catch (e: Exception) {
                    Eln.logger.warn("GUIDE no tooltip for {}: {}", id, e.toString())
                }
                item.add("tooltip", lines)
                items.add(item)
            }
            val t = JsonObject()
            t.addProperty("tab", key.path)
            t.addProperty("title", tab.displayName.string)
            t.add("items", items)
            out.add(t)
        }
        File(path).writeText(GsonBuilder().setPrettyPrinting().create().toJson(out))
        Eln.logger.info("GUIDE wrote {} tabs to {}", out.size(), path)
        helper.succeed()
    }
}
