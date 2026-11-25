@file:JvmName("Mc")
@file:Suppress("unused")

/**
 * Coordinate based / nullable-stack flavoured helpers over the Minecraft API.
 *
 * Electrical Age addresses the world with plain x, y, z integers and models "no item" as null. These extensions keep
 * that vocabulary in one place. Java code reaches them as static methods on `Mc`.
 */
package mods.eln.compat

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageSources
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.util.Locale

// ---------------------------------------------------------------- resources

private fun sanitizePath(path: String): String =
    path.lowercase(Locale.ROOT).replace(' ', '_').replace('\\', '/').replace(Regex("[^a-z0-9/._-]"), "_").replace("/./", "/")

/** Builds a [ResourceLocation], normalising the legacy mixed-case asset names the code still refers to. */
fun rl(namespace: String, path: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(namespace.lowercase(Locale.ROOT), sanitizePath(path))

fun rl(path: String): ResourceLocation {
    val idx = path.indexOf(':')
    return if (idx < 0) rl("minecraft", path) else rl(path.substring(0, idx), path.substring(idx + 1))
}

/** Turns a human readable name such as "Low Voltage Cable" into a registry path. */
fun registryPath(name: String): String =
    name.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_')

// ---------------------------------------------------------------- server / registries

val currentServer: MinecraftServer?
    @JvmName("currentServer") get() = ServerLifecycleHooks.getCurrentServer()

private val fallbackRegistries: RegistryAccess by lazy {
    RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
}

/** The registries of the running game, falling back to the static ones when no level is loaded. */
fun registries(): HolderLookup.Provider {
    ServerLifecycleHooks.getCurrentServer()?.let { return it.registryAccess() }
    if (FMLEnvironment.dist != null && FMLEnvironment.dist.isClient) {
        ClientAccess.registries()?.let { return it }
    }
    return fallbackRegistries
}

// ---------------------------------------------------------------- dimensions

/**
 * Electrical Age stores dimensions as integers. Vanilla dimensions keep their historical numbers; every other dimension
 * gets a number derived from its name, so it is identical on client and server and across restarts.
 */
fun dimensionId(key: ResourceKey<Level>): Int = when (key) {
    Level.OVERWORLD -> 0
    Level.NETHER -> -1
    Level.END -> 1
    else -> dimensionIds.computeIfAbsent(key) { (it.location().toString().hashCode() and 0x3FFFFFFF) or 0x40000000 }
}

private val dimensionIds = java.util.concurrent.ConcurrentHashMap<ResourceKey<Level>, Int>()

val Level.dimensionId: Int
    @JvmName("dimensionId") get() = dimensionId(dimension())

val Entity.dimension: Int
    @JvmName("dimension") get() = level().dimensionId

/** The loaded server level with this id, or null. */
fun serverLevelOrNull(dimension: Int): ServerLevel? =
    ServerLifecycleHooks.getCurrentServer()?.allLevels?.firstOrNull { it.dimensionId == dimension }

fun serverLevel(dimension: Int): ServerLevel =
    serverLevelOrNull(dimension) ?: throw IllegalStateException("No loaded level with Electrical Age dimension id $dimension")

fun MinecraftServer.worldServerForDimension(dimension: Int): ServerLevel = serverLevel(dimension)

val MinecraftServer.players: List<ServerPlayer>
    @JvmName("players") get() = playerList.players

// ---------------------------------------------------------------- world access

fun pos(x: Int, y: Int, z: Int): BlockPos = BlockPos(x, y, z)

fun BlockGetter.getBlock(x: Int, y: Int, z: Int): Block = getBlockState(BlockPos(x, y, z)).block

fun BlockGetter.getState(x: Int, y: Int, z: Int): BlockState = getBlockState(BlockPos(x, y, z))

fun BlockGetter.getTileEntity(x: Int, y: Int, z: Int): BlockEntity? = getBlockEntity(BlockPos(x, y, z))

fun BlockGetter.isAirBlock(x: Int, y: Int, z: Int): Boolean = getBlockState(BlockPos(x, y, z)).isAir

fun Level.blockExists(x: Int, y: Int, z: Int): Boolean {
    val p = BlockPos(x, y, z)
    return isInWorldBounds(p) && hasChunkAt(p)
}

fun Level.setBlock(x: Int, y: Int, z: Int, block: Block): Boolean = setBlock(BlockPos(x, y, z), block.defaultBlockState(), 3)

fun Level.setBlock(x: Int, y: Int, z: Int, state: BlockState, flags: Int): Boolean = setBlock(BlockPos(x, y, z), state, flags)

fun Level.setBlockToAir(x: Int, y: Int, z: Int): Boolean = removeBlock(BlockPos(x, y, z), false)

fun Level.spawnEntityInWorld(entity: Entity): Boolean = addFreshEntity(entity)

fun <T : Entity> Level.getEntitiesWithinAABB(cls: Class<T>, box: AABB): List<T> = getEntitiesOfClass(cls, box)

fun Level.notifyBlockChange(x: Int, y: Int, z: Int, block: Block) = updateNeighborsAt(BlockPos(x, y, z), block)

fun Level.markBlockForUpdate(x: Int, y: Int, z: Int) {
    val p = BlockPos(x, y, z)
    val state = getBlockState(p)
    sendBlockUpdated(p, state, state, 3)
}

fun Level.getSavedLightValue(layer: LightLayer, x: Int, y: Int, z: Int): Int = getBrightness(layer, BlockPos(x, y, z))

fun Level.getSkyBlockTypeBrightness(layer: LightLayer, x: Int, y: Int, z: Int): Int = getBrightness(layer, BlockPos(x, y, z))

/** How much the sky light is currently reduced (night / weather), 0..11. */
fun Level.calculateSkylightSubtracted(): Int {
    updateSkyBrightness()
    return skyDarken
}

fun Level.canBlockSeeTheSky(x: Int, y: Int, z: Int): Boolean = canSeeSky(BlockPos(x, y, z))

fun Level.getStrongestIndirectPower(x: Int, y: Int, z: Int): Int = getBestNeighborSignal(BlockPos(x, y, z))

fun Level.getIndirectPowerLevelTo(x: Int, y: Int, z: Int, side: Int): Int =
    getSignal(BlockPos(x, y, z), Direction.from3DDataValue(side))

/** Forces light in this block to be recomputed. */
fun Level.checkLight(x: Int, y: Int, z: Int) {
    chunkSource.lightEngine.checkBlock(BlockPos(x, y, z))
}

val Level.rand: RandomSource
    @JvmName("rand") get() = random

val Level.worldTime: Long
    @JvmName("getWorldTime") get() = dayTime

val Level.totalWorldTime: Long
    @JvmName("getTotalWorldTime") get() = gameTime

val Level.doTileDrops: Boolean
    @JvmName("doTileDrops") get() = gameRules.getBoolean(GameRules.RULE_DOBLOCKDROPS)

fun Level.explode(source: Entity?, x: Double, y: Double, z: Double, strength: Float) {
    explode(source, x, y, z, strength, Level.ExplosionInteraction.BLOCK)
}

fun Level.createExplosion(source: Entity?, x: Double, y: Double, z: Double, strength: Float, @Suppress("UNUSED_PARAMETER") smoking: Boolean) {
    explode(source, x, y, z, strength, Level.ExplosionInteraction.BLOCK)
}

fun Level.playSoundEffect(x: Double, y: Double, z: Double, sound: SoundEvent, volume: Float, pitch: Float) {
    playSound(null, x, y, z, sound, SoundSource.BLOCKS, volume, pitch)
}

fun soundEvent(name: String): SoundEvent = SoundEvent.createVariableRangeEvent(rl(name))

// ---------------------------------------------------------------- blocks

val Block.isOpaqueCube: Boolean
    @JvmName("isOpaqueCube") get() = defaultBlockState().isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)

val BlockState.isOpaqueCubeState: Boolean
    @JvmName("isOpaqueCubeState") get() = isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)

fun Block.isReplaceable(world: BlockGetter?, x: Int, y: Int, z: Int): Boolean {
    val state = world?.getBlockState(BlockPos(x, y, z))
    return (if (state != null && state.block === this) state else defaultBlockState()).canBeReplaced()
}

// ---------------------------------------------------------------- block entities / entities

val BlockEntity.xCoord: Int
    @JvmName("xCoord") get() = blockPos.x
val BlockEntity.yCoord: Int
    @JvmName("yCoord") get() = blockPos.y
val BlockEntity.zCoord: Int
    @JvmName("zCoord") get() = blockPos.z
val BlockEntity.worldObj: Level
    @JvmName("worldObj") get() = level!!

val Entity.worldObj: Level
    @JvmName("worldObj") get() = level()

val Vec3.xCoord: Double
    @JvmName("xCoord") get() = x
val Vec3.yCoord: Double
    @JvmName("yCoord") get() = y
val Vec3.zCoord: Double
    @JvmName("zCoord") get() = z

val Entity.motionX: Double
    @JvmName("motionX") get() = deltaMovement.x
val Entity.motionY: Double
    @JvmName("motionY") get() = deltaMovement.y
val Entity.motionZ: Double
    @JvmName("motionZ") get() = deltaMovement.z

fun Entity.attackEntityFrom(source: DamageSource, amount: Float): Boolean = hurt(source, amount)

fun Entity.damageSources2(): DamageSources = damageSources()

// ---------------------------------------------------------------- players

val Player.currentEquippedItem: ItemStack?
    @JvmName("getCurrentEquippedItem") get() = mainHandItem.orNull()

fun Inventory.getCurrentItem(): ItemStack? = getSelected().orNull()

fun Inventory.getStackInSlot(slot: Int): ItemStack? = getItem(slot).orNull()

fun Player.addChatMessage(message: Component) = sendSystemMessage(message)

val Player.isCreativeMode: Boolean
    @JvmName("isCreativeMode") get() = abilities.instabuild

/** Opens an Electrical Age GUI, see [mods.eln.GuiHandler] for the ids. */
fun Player.openGui(@Suppress("UNUSED_PARAMETER") mod: Any?, id: Int, world: Level, x: Int, y: Int, z: Int) {
    mods.eln.GuiHandler.openGui(this, id, world, x, y, z)
}

// ---------------------------------------------------------------- item stacks

fun ItemStack?.orNull(): ItemStack? = if (this == null || isEmpty) null else this

fun orEmpty(stack: ItemStack?): ItemStack = stack ?: ItemStack.EMPTY

/** Items that stand for one numbered Electrical Age descriptor. */
interface IElnItem {
    /** The descriptor's (group shl 6) + subId number. */
    val elnId: Int
}

/** The descriptor number for Electrical Age items, the durability damage for everything else. */
val ItemStack.itemDamage: Int
    @JvmName("getItemDamage") get() = (item as? IElnItem)?.elnId ?: damageValue

fun ItemStack.isItemEqual(other: ItemStack?): Boolean = other != null && ItemStack.isSameItem(this, other)

fun ItemStack.splitStack(amount: Int): ItemStack = split(amount)

/**
 * A snapshot of the custom data on this stack, or null. Changing the returned tag does not affect the stack; use
 * [updateTag] or assign the property to store changes.
 */
var ItemStack.tagCompound: CompoundTag?
    @JvmName("getTagCompound") get() = get(DataComponents.CUSTOM_DATA)?.copyTag()
    @JvmName("setTagCompound") set(value) {
        if (value == null) remove(DataComponents.CUSTOM_DATA)
        else set(DataComponents.CUSTOM_DATA, CustomData.of(value))
    }

fun ItemStack.hasTagCompound(): Boolean = has(DataComponents.CUSTOM_DATA)

inline fun ItemStack.updateTag(block: (CompoundTag) -> Unit) {
    val tag = tagCompound ?: CompoundTag()
    block(tag)
    tagCompound = tag
}

/** The display name as plain text, without the decoration vanilla adds in chat. */
val ItemStack.plainName: String
    @JvmName("plainName") get() = hoverName.string

fun ItemStack.writeToNBT(nbt: CompoundTag): CompoundTag {
    if (isEmpty) return nbt
    val saved = save(registries())
    if (saved is CompoundTag) nbt.merge(saved)
    return nbt
}

fun loadItemStack(nbt: CompoundTag): ItemStack? = ItemStack.parse(registries(), nbt).orElse(null).orNull()

fun itemId(item: Item): Int = BuiltInRegistries.ITEM.getId(item)

fun itemById(id: Int): Item = BuiltInRegistries.ITEM.byId(id)

fun blockId(block: Block): Int = BuiltInRegistries.BLOCK.getId(block)

fun blockById(id: Int): Block = BuiltInRegistries.BLOCK.byId(id)

fun newItemEntity(world: Level, x: Double, y: Double, z: Double, stack: ItemStack): ItemEntity {
    val e = ItemEntity(world, x, y, z, stack)
    e.setPickUpDelay(10)
    return e
}

fun isAir(block: Block): Boolean = block === Blocks.AIR || block.defaultBlockState().isAir
