package mods.eln.config

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.common.Tags
import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.item.electricalitem.OreScannerConfigElement
import net.minecraft.world.level.block.Block

/**
 * Loads ore scanner configuration from JsonConfig and resolves ore entries
 * to block keys for use by the ore scanner and auto miner.
 */
object OreScannerConfigLoader {

    private const val ORE_FACTORS_PATH = "tools.xrayScanner.oreFactors"
    private const val AUTO_DISCOVERY_KEY = "tools.xrayScanner.autoDiscoverOreDictionaryOres"
    private const val AUTO_DISCOVERY_FACTOR_KEY = "tools.xrayScanner.autoDiscoveryOreFactor"
    private const val DEFAULT_OTHER_MOD_FACTOR = 0.15

    /**
     * Loads ore scanner entries from config. Each config key is either:
     * - A block reference (contains ':'): "modid:name" or "modid:name:meta"
     * - An OreDictionary name (no ':'): "oreCopper"
     *
     * Returns deduplicated list where later entries override earlier by blockKey.
     */
    fun loadOreScannerConfig(): List<OreScannerConfigElement> {
        val config = Eln.config
        val oreFactors = config.getStringDoubleMap(ORE_FACTORS_PATH)
        val blockKeyMap = linkedMapOf<Int, Float>()

        for ((key, factor) in oreFactors) {
            val f = factor.toFloat()
            if (key.contains(':')) {
                resolveBlockReference(key, f, blockKeyMap)
            } else {
                resolveOreDictionaryName(key, f, blockKeyMap)
            }
        }

        return blockKeyMap.map { (blockKey, factor) ->
            OreScannerConfigElement(blockKey, factor)
        }
    }

    /**
     * Auto-discovers ores from OreDictionary that are not already in configEntries.
     * Only runs if autoDiscoverOreDictionaryOres is enabled.
     * Uses autoDiscoveryOreFactor for all auto-discovered ores.
     */
    fun loadOreDictionaryAutoDiscovery(existingBlockKeys: Map<Int, Float>): List<OreScannerConfigElement> {
        val config = Eln.config
        if (!config.getBooleanOrElse(AUTO_DISCOVERY_KEY, true)) {
            return emptyList()
        }

        val otherModFactor = config.getDoubleOrElse(AUTO_DISCOVERY_FACTOR_KEY, DEFAULT_OTHER_MOD_FACTOR).toFloat()
        val results = mutableListOf<OreScannerConfigElement>()

        for (block in BuiltInRegistries.BLOCK) {
            if (!block.defaultBlockState().`is`(Tags.Blocks.ORES)) continue
            val blockKey = blockId(block)
            if (blockKey !in existingBlockKeys) {
                results.add(OreScannerConfigElement(blockKey, otherModFactor))
            }
        }

        return results
    }

    private fun resolveBlockReference(key: String, factor: Float, blockKeyMap: MutableMap<Int, Float>) {
        val parts = key.split(':')
        if (parts.size < 2) {
            return
        }
        val id = ResourceLocation.tryBuild(parts[0], parts[1]) ?: return
        if (!BuiltInRegistries.BLOCK.containsKey(id)) return
        blockKeyMap[blockId(BuiltInRegistries.BLOCK.get(id))] = factor
    }

    private fun resolveOreDictionaryName(key: String, factor: Float, blockKeyMap: MutableMap<Int, Float>) {
        val ores = OreDict.getOres(key)
        if (ores.isEmpty()) {
            return
        }

        for (stack in ores) {
            val block = Block.byItem(stack.item)
            if (!isAir(block)) blockKeyMap[blockId(block)] = factor
        }
    }
}
