package mods.eln.gridnode.electricalpole

import mods.eln.gridnode.GridDescriptor
import mods.eln.misc.Obj3D
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor

enum class Kind {
    OVERHEAD,
    TRANSFORMER_TO_GROUND,
    SHUNT_TO_GROUND,
}

/**
 * Created by svein on 07/08/15.
 */
class ElectricalPoleDescriptor(name: String,
                               obj: Obj3D, cableTexture: String,
                               cableDescriptor: ElectricalCableDescriptor,
                               val kind: Kind,
                               connectRange: Int, val voltageLimit: Double,
                               /** What its line carries for good, in amps. */
                               val ratedCurrent: Double)
    : GridDescriptor(name, obj, ElectricalPoleElement::class.java, ElectricalPoleRender::class.java, cableTexture, cableDescriptor, connectRange) {

    /**
     * The overhead conductor of its line, for its heat and voltage: lines are strung with high voltage cable, but
     * carry far more than an 800 V cable, as overhead lines are cooled by the air. T2 towers carry 40 A at 51.2 kV
     * (about 2 MW), T1 poles 20 A at 12.8 kV (about 250 kW).
     */
    val conductor = ElectricalCableDescriptor("$name conductor", cableDescriptor.render, "", false).apply {
        setPhysicalConstantLikeNormalCable(
            voltageLimit, voltageLimit * ratedCurrent, 0.0005, voltageLimit * 1.3, voltageLimit * ratedCurrent * 1.2,
            40.0, mods.eln.Eln.cableWarmLimit, -100.0, mods.eln.Eln.cableHeatingTime, mods.eln.Eln.cableThermalConductionTao
        )
    }
    val minimalLoadToHum = 0.2f

    val includeTransformer get() = kind == Kind.TRANSFORMER_TO_GROUND
    val isShunt get() = kind == Kind.SHUNT_TO_GROUND

    init {
        obj.getPart("foot")?.let {
            // Don't draw the foot on the T1 utility pole.
            // XXX is there a better way to check for the model?
            if (kind != Kind.OVERHEAD || name == "Transmission Tower")
                static_parts.add(it)
        }
        if (includeTransformer) {
            arrayOf("transformer", "cables").forEach {
                obj.getPart(it)?.let { rotating_parts.add(it) }
            }
        }
    }

    override fun hasCustomIcon() = this.name == "Transmission Tower"
}
