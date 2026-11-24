package mods.eln.misc

import net.minecraft.world.phys.Vec3

/** A vector that is changed in place. */
class MutVec3(var xCoord: Double, var yCoord: Double, var zCoord: Double) {
    constructor(v: Vec3) : this(v.x, v.y, v.z)

    fun toVec3(): Vec3 = Vec3(xCoord, yCoord, zCoord)

    fun set(v: Vec3) {
        xCoord = v.x
        yCoord = v.y
        zCoord = v.z
    }

    fun rotateAroundY(angle: Float) = set(toVec3().yRot(angle))

    fun rotateAroundZ(angle: Float) = set(toVec3().zRot(angle))

    fun rotateAroundX(angle: Float) = set(toVec3().xRot(angle))
}
