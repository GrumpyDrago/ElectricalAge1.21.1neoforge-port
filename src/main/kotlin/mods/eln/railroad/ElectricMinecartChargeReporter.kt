package mods.eln.railroad

import mods.eln.Eln
import mods.eln.compat.currentEquippedItem
import mods.eln.i18n.I18N
import mods.eln.misc.Utils
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

class ElectricMinecartChargeReporter {
    @SubscribeEvent
    fun onRightClickItem(event: PlayerInteractEvent.RightClickItem) = report(event.entity, event.hand)

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) = report(event.entity, event.hand)

    private fun report(player: Player, hand: InteractionHand) {
        if (player.level().isClientSide || hand != InteractionHand.MAIN_HAND) return
        val minecart = player.vehicle as? EntityElectricMinecart ?: return
        val held = player.currentEquippedItem ?: return
        if (Eln.multiMeterElement?.checkSameItemStack(held) == true || Eln.allMeterElement?.checkSameItemStack(held) == true) {
            Utils.addChatMessage(player, I18N.tr("Cart Energy: ") + Utils.plotEnergy(minecart.energyBufferJoules))
        }
    }
}
