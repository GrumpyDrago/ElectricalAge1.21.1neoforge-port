package mods.eln.craft

import mods.eln.Eln
import net.minecraft.world.item.crafting.RecipeManager
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.AddReloadListenerEvent
import net.neoforged.neoforge.event.TagsUpdatedEvent

/**
 * Adds the recipes to the recipe manager after each data reload.
 *
 * They are built in code because many of them depend on the configuration. A data pack recipe with the same id wins, so
 * packs can still change them; an unconditional removal is done by overriding the recipe with one that cannot be made.
 */
object RecipeInjector {
    private var manager: RecipeManager? = null

    @SubscribeEvent
    fun onReload(event: AddReloadListenerEvent) {
        manager = event.serverResources.recipeManager
    }

    // Which recipes exist depends on the materials that are around, so this has to wait for the tags.
    @SubscribeEvent
    fun onTags(event: TagsUpdatedEvent) {
        if (event.updateCause == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) manager?.let(::inject)
        if (event.shouldUpdateStaticData()) CraftingRecipes.buildMachines()
    }

    private fun inject(manager: RecipeManager) {
        val existing = manager.recipes
        val known = existing.map { it.id() }.toHashSet()
        val added = CraftingRecipes.buildCrafting().filter { it.id() !in known }
        manager.replaceRecipes(existing + added)
        Eln.logger.info("Added {} recipes", added.size)
    }
}
