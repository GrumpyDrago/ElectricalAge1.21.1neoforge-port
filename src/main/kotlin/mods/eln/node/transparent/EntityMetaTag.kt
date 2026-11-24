package mods.eln.node.transparent

/**
 * Tells apart the block entity classes a transparent node can have, depending on the capabilities it needs.
 */
enum class EntityMetaTag(val meta: Int) {
    Fluid(1),
    Basic(3);
}
