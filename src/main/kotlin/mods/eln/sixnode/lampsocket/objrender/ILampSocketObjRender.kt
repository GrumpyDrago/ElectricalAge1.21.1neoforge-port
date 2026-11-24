package mods.eln.sixnode.lampsocket.objrender

import mods.eln.compat.*

import mods.eln.sixnode.lampsocket.LampSocketDescriptor
import mods.eln.sixnode.lampsocket.LampSocketRender
import mods.eln.compat.IItemRenderer.ItemRenderType

interface ILampSocketObjRender {

    fun draw(descriptor: LampSocketDescriptor, type: ItemRenderType, distanceToPlayer: Double)

    fun draw(render: LampSocketRender, distanceToPlayer: Double)

}