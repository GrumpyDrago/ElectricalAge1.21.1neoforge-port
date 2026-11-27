package mods.eln.item

import mods.eln.Eln
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Utils
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Lockout/tagout: before working on a circuit, each worker puts their own padlock on the switch that feeds it. The
 * switch stays open as long as any lock is on it, and only the one who put a lock on can take it off, so nobody turns
 * the power back on while someone is still working.
 */
class LockoutPadlockDescriptor(name: String) : GenericItemUsingDamageDescriptor(name) {
    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.addAll(tr("Use it on a breaker, switch or breaker box to lock\nit open with your name on it. Nobody can switch\nit on until every lock is off; only you can take\nyours off, by using a padlock on it again.").split("\n"))
    }
}

/** A switching device that can be locked open. */
interface Lockable {
    val lockout: Lockout

    /** A lock was put on or taken off: open the contacts, or let them close again as they would. */
    fun lockoutChanged()
}

class Lockout {
    private val locks = LinkedHashMap<UUID, String>()

    val isLocked get() = locks.isNotEmpty()

    fun owners(): Collection<String> = locks.values

    fun describe(): String = tr("Locked out by %1$", locks.values.joinToString(", "))

    /**
     * [player] uses a padlock on [device]: puts their lock on, or takes it off if it is there. In creative mode,
     * sneaking takes every lock off. Returns the message for the player.
     */
    fun use(device: Lockable, player: Player): String {
        val id = player.uuid
        val message = when {
            player.abilities.instabuild && player.isShiftKeyDown && isLocked -> {
                locks.clear()
                tr("You cut every lock off.")
            }
            locks.containsKey(id) -> {
                locks.remove(id)
                if (isLocked) tr("You took your lock off. Still locked by %1$.", locks.values.joinToString(", "))
                else tr("You took your lock off. It can be switched on again.")
            }
            else -> {
                locks[id] = player.name.string
                tr("Locked out: it stays off until you take your lock off.")
            }
        }
        device.lockoutChanged()
        return message
    }

    fun writeToNBT(nbt: CompoundTag, prefix: String = "") {
        val list = ListTag()
        for ((id, name) in locks) {
            val t = CompoundTag()
            t.putUUID("id", id)
            t.putString("name", name)
            list.add(t)
        }
        nbt.put(prefix + "lockout", list)
    }

    fun readFromNBT(nbt: CompoundTag, prefix: String = "") {
        locks.clear()
        val list = nbt.getList(prefix + "lockout", Tag.TAG_COMPOUND.toInt())
        for (i in 0 until list.size) {
            val t = list.getCompound(i)
            if (t.hasUUID("id")) locks[t.getUUID("id")] = t.getString("name")
        }
    }

    companion object {
        /** Handles a padlock used on [device] by [player]; true if it was a padlock. */
        @JvmStatic
        fun tryUse(device: Any?, player: Player, stack: ItemStack?): Boolean {
            if (stack == null || device !is Lockable) return false
            val padlock = Eln.lockoutPadlockElement ?: return false
            if (!padlock.checkSameItemStack(stack)) return false
            Utils.addChatMessage(player, device.lockout.use(device, player))
            return true
        }
    }
}
