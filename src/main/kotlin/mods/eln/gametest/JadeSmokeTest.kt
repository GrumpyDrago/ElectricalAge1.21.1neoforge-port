package mods.eln.gametest

import mods.eln.Eln
import net.minecraft.core.BlockPos
import snownee.jade.api.BlockAccessor
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.JadeIds
import snownee.jade.api.WailaPlugin

/** Reads what Jade shows, where that is installed. */
@WailaPlugin
class JadeSmokeTest : IWailaPlugin {
    override fun registerClient(registration: IWailaClientRegistration) {
        Eln.logger.info("SMOKE Jade found")
        // After all others.
        registration.addTooltipCollectedCallback(10000) { box, accessor ->
            pos = (accessor as? BlockAccessor)?.position
            name = box.tooltip.getMessage(JadeIds.CORE_OBJECT_NAME).orEmpty()
            text = box.tooltip.message
        }
    }

    companion object {
        var pos: BlockPos? = null
            private set
        var name = ""
            private set
        var text = ""
            private set
    }
}
