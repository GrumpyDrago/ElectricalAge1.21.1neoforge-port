package mods.eln.fluid

/**
 * Finds the entry of the fuel configuration that is meant for a fluid. Mods do not agree on what to call fuels, but most
 * use common tags for them.
 */
class FuelNames(names: Iterable<String>) {
    private val byKey = LinkedHashMap<String, String>()

    init {
        for (name in names) byKey.putIfAbsent(key(name), name)
    }

    /**
     * [id] is what the fluid is registered as, [tags] the ids of the tags it is in. The configuration can name a fluid by
     * its id; after that its own name is worth more than tags, which can be broader.
     */
    fun find(id: String, tags: Collection<String>): String? {
        byKey[key(id)]?.let { return it }
        find(id.substringAfter(':').removePrefix("still_").removeSuffix("_still").removeSuffix("_source").removeSuffix("_fluid"))?.let { return it }
        return tags.sorted().firstNotNullOfOrNull { tag ->
            if (tag.startsWith("c:")) find(tag.removePrefix("c:").removePrefix("fuels/")) else null
        }
    }

    private fun find(name: String): String? {
        val key = key(name)
        return byKey[key] ?: aliases[key]?.let { byKey[it] }
    }

    companion object {
        /** Names are the same whatever the spelling. */
        private fun key(name: String) = name.lowercase().filter { it.isLetterOrDigit() || it == ':' || it == '/' }

        /** Other names of fuels, as keys. */
        private val aliases = mapOf(
            "crudeoil" to "oil", "shaleoil" to "oil", "sulfuriccrudeoil" to "oil", "petroleumcrude" to "oil",
            "heavyfuel" to "heavyoil", "fueloil" to "heavyoil", "heavyfueloil" to "heavyoil", "sulfuricheavyfuel" to "heavyoil",
            "lightfuel" to "lightoil", "lightfueloil" to "lightoil", "sulfuriclightfuel" to "lightoil",
            "sulfuricnaphtha" to "naphtha", "sulfuricdiesel" to "dieselsulfur", "dieselfuel" to "diesel",
            "petrol" to "gasoline", "benzin" to "gasoline", "benzol" to "aromatics", "benzene" to "aromatics",
            "jetfuel" to "kerosene", "refinedfuel" to "fuel",
            "vegetableoil" to "plantoil", "seedoil" to "plantoil", "canolaoil" to "plantoil", "refinedcanolaoil" to "plantoil",
            "creosoteoil" to "creosote",
            "petroleumgas" to "lpg", "propane" to "lpg", "butane" to "lpg", "liquefiedpetroleumgas" to "lpg",
            "methane" to "naturalgas", "ethene" to "naturalgas", "ethylene" to "naturalgas",
            "liquidhydrogen" to "hydrogen"
        )
    }
}
