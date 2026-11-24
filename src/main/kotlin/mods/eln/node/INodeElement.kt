package mods.eln.node

import mods.eln.compat.*


interface INodeElement {
    fun needPublish()
    fun reconnect()
    fun inventoryChange(inventory: IInventory?)
}
