package mods.eln.simplenode.energyconverter

import mods.eln.gui.GuiHelper
import mods.eln.gui.GuiScreenEln
import mods.eln.gui.GuiTextFieldEln
import mods.eln.gui.GuiVerticalTrackBar
import mods.eln.gui.IGuiObject
import mods.eln.i18n.I18N
import mods.eln.misc.Utils
import kotlin.math.log
import kotlin.math.pow

class EnergyConverterElnToOtherGui(var render: EnergyConverterElnToOtherEntity) : GuiScreenEln() {

    var resistanceSelector: GuiVerticalTrackBar? = null
    var powerEntry: GuiTextFieldEln? = null

    private val maxResistance = 100_000.0

    override fun initGui() {
        super.initGui()
        resistanceSelector = newGuiVerticalTrackBar(6, 6 + 2, 20, 82)
        resistanceSelector!!.setStepIdMax(100)
        resistanceSelector!!.setEnable(true)
        resistanceSelector!!.setRange(0f, 1f)
        powerEntry = newGuiTextField(6, 96, 70)
        powerEntry!!.enabled
        powerEntry!!.setComment(listOf("Select the resistance").toTypedArray())
        syncVoltage()
    }

    fun syncVoltage() {
        resistanceSelector!!.value = selectedResistanceToSlider(render.selectedResistance)
        powerEntry!!.text = render.selectedResistance.toInt().toString()
        render.hasChanges = false
    }

    fun selectedResistanceToSlider(selectedResistance: Double): Float {
        return log(selectedResistance / 100 + 1, (maxResistance / 100) + 1).toFloat()
    }

    fun sliderToSelectedResistance(slider: Float): Double {
        return 100 * (((maxResistance / 100) + 1).pow(slider.toDouble()) - 1)
    }

    override fun guiObjectEvent(guiObject: IGuiObject) {
        super.guiObjectEvent(guiObject)
        when {
            guiObject === resistanceSelector -> {
                render.selectedResistance = sliderToSelectedResistance(resistanceSelector!!.value)
                render.sender.clientSendDouble(NetworkType.SET_OHMS.id, render.selectedResistance)
            }
            guiObject === powerEntry -> {
                render.selectedResistance = powerEntry!!.text.toDoubleOrNull()?: render.selectedResistance
                render.sender.clientSendDouble(NetworkType.SET_OHMS.id, render.selectedResistance)
            }
        }
    }

    override fun preDraw(f: Float, x: Int, y: Int) {
        super.preDraw(f, x, y)
        if (render.hasChanges) syncVoltage()
        resistanceSelector!!.setComment(0, Utils.plotOhm(render.selectedResistance))
    }

    override fun newHelper(): GuiHelper {
        return GuiHelper(this, 82, 115)
    }
}
