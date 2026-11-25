package mods.eln.registration

import mods.eln.Eln
import mods.eln.compat.*
import mods.eln.generic.GenericCreativeTab
import mods.eln.node.NodeManager.Companion.registerUuid
import mods.eln.simplenode.computerprobe.ComputerProbeBlock
import mods.eln.simplenode.computerprobe.ComputerProbeEntity
import mods.eln.simplenode.computerprobe.ComputerProbeNode
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherBlock
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherDescriptor
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherEntity
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherNode
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType

object SingleNodeRegistration {
    @JvmStatic
    lateinit var energyConverter: BlockEntityType<EnergyConverterElnToOtherEntity>
        private set

    @JvmStatic
    lateinit var computerProbe: BlockEntityType<ComputerProbeEntity>
        private set

    fun registerSingle() {
        registerEnergyConverter()
        registerComputer()
    }

    private fun addBlock(path: String, block: Block, tab: GenericCreativeTab) {
        ElnRegistry.registerBlockWithItem(path, block)
        tab.add { ItemStack(block) }
    }

    private fun registerEnergyConverter() {
        registerUuid(EnergyConverterElnToOtherNode.getNodeUuidStatic(), EnergyConverterElnToOtherNode::class.java)
        val desc = EnergyConverterElnToOtherDescriptor("EnergyConverterElnToOtherLVU", Eln.instance.ELN_CONVERTER_MAX_POWER)
        val block = EnergyConverterElnToOtherBlock(desc)
        Eln.instance.elnToOtherBlockConverter = block
        addBlock("energy_converter", block, Eln.creativeTabPowerElectronics)
        energyConverter = ElnBlockEntities.create("energy_converter", ::EnergyConverterElnToOtherEntity, block)
    }

    private fun registerComputer() {
        registerUuid(ComputerProbeNode.getNodeUuidStatic(), ComputerProbeNode::class.java)
        val block = ComputerProbeBlock()
        Eln.instance.computerProbeBlock = block
        addBlock("computer_probe", block, Eln.creativeTabSignalProcessing)
        computerProbe = ElnBlockEntities.create("computer_probe", ::ComputerProbeEntity, block)
    }
}
