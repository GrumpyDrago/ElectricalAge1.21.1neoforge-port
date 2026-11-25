package mods.eln.misc

import mods.eln.compat.*

import mods.eln.node.NodeBlockEntity
import net.minecraft.world.level.block.Block
import net.minecraft.world.entity.Entity
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.Level
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.abs
import kotlin.math.floor

class Coordinate : INBTTReady {
    @JvmField
    var x = 0
    @JvmField
    var y = 0
    @JvmField
    var z = 0
    @JvmField
    var dimension = 0

    constructor() {
        x = 0
        y = 0
        z = 0
        dimension = 0
    }

    constructor(coord: Coordinate) {
        x = coord.x
        y = coord.y
        z = coord.z
        dimension = coord.dimension
    }

    constructor(nbt: CompoundTag, str: String) {
        readFromNBT(nbt, str)
    }

    // Emulates the default Minecraft behavior for determining block coordinates
    constructor(v: Vec3, d: Int) {
        x = floor(v.xCoord).toInt()
        y = floor(v.yCoord).toInt()
        z = floor(v.zCoord).toInt()
        dimension = d
    }

    override fun hashCode(): Int {
        // Bases are flat and regular; make sure that neighbors do not end up in the same buckets.
        var h = x * -0x61c88647 + z
        h = h * -0x61c88647 + y
        h = h * -0x61c88647 + dimension
        return h xor (h ushr 15)
    }

    fun worldDimension(): Int {
        return dimension
    }

    private var w: Level? = null
    fun world(): Level {
        return if (w == null) {
            serverLevel(worldDimension())
        } else w!!
    }

    constructor(entity: NodeBlockEntity) {
        x = entity.xCoord
        y = entity.yCoord
        z = entity.zCoord
        dimension = entity.worldObj.dimensionId
    }

    constructor(x: Int, y: Int, z: Int, dimention: Int) {
        this.x = x
        this.y = y
        this.z = z
        this.dimension = dimention
    }

    constructor(x: Int, y: Int, z: Int, world: Level) {
        this.x = x
        this.y = y
        this.z = z
        dimension = world.dimensionId
        if (world.isClientSide) w = world
    }

    constructor(entity: BlockEntity) {
        x = entity.xCoord
        y = entity.yCoord
        z = entity.zCoord
        dimension = entity.worldObj.dimensionId
        if (entity.worldObj.isClientSide) w = entity.worldObj
    }

    fun newWithOffset(x: Int, y: Int, z: Int): Coordinate {
        return Coordinate(this.x + x, this.y + y, this.z + z, dimension)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Coordinate) return false
        return other.x == x && other.y == y && other.z == z && other.dimension == dimension
    }

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        x = nbt.getInt(str + "x")
        y = nbt.getInt(str + "y")
        z = nbt.getInt(str + "z")
        dimension = nbt.getInt(str + "d")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        nbt.putInt(str + "x", x)
        nbt.putInt(str + "y", y)
        nbt.putInt(str + "z", z)
        nbt.putInt(str + "d", dimension)
    }

    override fun toString(): String {
        return "X : $x Y : $y Z : $z D : $dimension"
    }

    fun move(dir: Direction) {
        when (dir) {
            Direction.XN -> x--
            Direction.XP -> x++
            Direction.YN -> y--
            Direction.YP -> y++
            Direction.ZN -> z--
            Direction.ZP -> z++
        }
    }

    fun moved(direction: Direction): Coordinate {
        val moved = Coordinate(this)
        moved.move(direction)
        return moved
    }

    var block: Block
        get() = world().getBlock(x, y, z)
        set(b) {
            world().setBlock(x, y, z, b)
        }

    fun getAxisAlignedBB(ray: Int): AABB {
        return AABB((
            x - ray).toDouble(), (y - ray).toDouble(), (z - ray).toDouble(), (
            x + ray + 1).toDouble(), (y + ray + 1).toDouble(), (z + ray + 1).toDouble())
    }

    fun distanceTo(e: Entity): Double {
        return abs(e.x - (x + 0.5)) + abs(e.y - (y + 0.5)) + abs(e.z - (z + 0.5))
    }

    val state: BlockState
        get() = world().getState(x, y, z)
    val blockPos: BlockPos
        get() = BlockPos(x, y, z)
    val blockExist: Boolean
        get() {
            val w = serverLevelOrNull(dimension) ?: return false
            return w.blockExists(x, y, z)
        }
    val worldExist: Boolean
        get() = serverLevelOrNull(dimension) != null

    fun copyTo(v: DoubleArray) {
        v[0] = x + 0.5
        v[1] = y + 0.5
        v[2] = z + 0.5
    }

    fun setPosition(vp: DoubleArray) {
        x = vp[0].toInt()
        y = vp[1].toInt()
        z = vp[2].toInt()
    }

    // Emulates the default Minecraft behavior for determining block coordinates
    fun setPosition(vp: Vec3) {
        x = floor(vp.xCoord).toInt()
        y = floor(vp.yCoord).toInt()
        z = floor(vp.zCoord).toInt()
    }

    val tileEntity: BlockEntity?
        get() = world().getTileEntity(x, y, z)

    fun invalidate() {
        x = -1
        y = -1
        z = -1
        dimension = -5123
    }

    val isValid: Boolean
        get() = dimension != -5123

    fun trueDistanceTo(c: Coordinate): Double {
        val dx = (x - c.x).toLong()
        val dy = (y - c.y).toLong()
        val dz = (z - c.z).toLong()
        return Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble())
    }

    fun setDimension(dimension: Int) {
        this.dimension = dimension
        w = null
    }

    fun copyFrom(c: Coordinate) {
        x = c.x
        y = c.y
        z = c.z
        dimension = c.dimension
    }

    fun applyTransformation(front: Direction, coordinate: Coordinate) {
        front.rotateFromXN(this)
        x += coordinate.x
        y += coordinate.y
        z += coordinate.z
    }

    fun setWorld(worldObj: Level) {
        if (worldObj.isClientSide) w = worldObj
        dimension = worldObj.dimensionId
    }

    operator fun compareTo(o: Coordinate): Int {
        return when {
            dimension != o.dimension ->
                dimension - o.dimension
            x != o.x ->
                x - o.x
            y != o.y ->
                y - o.y
            z != o.z ->
                z - o.z
            else -> 0
        }
    }

    fun subtract(b: Coordinate): Coordinate {
        return newWithOffset(-b.x, -b.y, -b.z)
    }

    fun negate(): Coordinate {
        return Coordinate(-x, -y, -z, dimension)
    }

    // Emulates the default Minecraft behavior for determining block coordinates
    fun toVec3(): Vec3 {
        return Vec3(this.x + 0.5, this.y + 0.5, this.z + 0.5)
    }

    companion object {
        @JvmStatic
        fun getAxisAlignedBB(a: Coordinate, b: Coordinate): AABB {
            return AABB(
                a.x.coerceAtMost(b.x).toDouble(), a.y.coerceAtMost(b.y).toDouble(), a.z.coerceAtMost(b.z).toDouble(),
                a.x.coerceAtLeast(b.x) + 1.0, a.y.coerceAtLeast(b.y) + 1.0, a.z.coerceAtLeast(b.z) + 1.0)
        }
    }
}
