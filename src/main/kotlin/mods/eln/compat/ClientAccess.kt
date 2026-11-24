package mods.eln.compat

import net.minecraft.client.Minecraft
import net.minecraft.core.HolderLookup

/** Client-only lookups, kept in their own class so a dedicated server never loads them. */
internal object ClientAccess {
    fun registries(): HolderLookup.Provider? = Minecraft.getInstance()?.level?.registryAccess()
}
