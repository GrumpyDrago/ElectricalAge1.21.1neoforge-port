package mods.eln.transparentnode

import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.fluids.FluidUtil
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.templates.FluidTank
import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.cable.CableRenderType
import mods.eln.fluid.FluidNames
import mods.eln.fluid.FuelRegistry
import mods.eln.fluid.IElnFluidHandler
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.published
import mods.eln.node.transparent.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.component.PowerSource
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.sound.LoopedSound
import mods.eln.wiki.Data
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import mods.eln.compat.client.GL11
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.floor
import net.minecraft.core.Direction as McDirection

class FuelGeneratorDescriptor(name: String, internal val obj: Obj3D?, internal val cable: ElectricalCableDescriptor,
                              internal val nominalPower: Double, internal val maxVoltage: Double,
                              tankCapacityInSecondsAtNominalPower: Double)
    : TransparentNodeDescriptor(name, FuelGeneratorElement::class.java, FuelGeneratorRender::class.java) {
    companion object {
        internal fun EfficiencyFactorVsLoadFactor(loadFactor: Double) = when (Utils.limit(loadFactor, 0.0, 1.5)) {
            in 0.0..0.1 -> 1.375
            in 0.1..0.2 -> 1.125
            in 0.2..0.3 -> 1.050
            in 0.3..0.4 -> 1.025
            in 0.4..0.5 -> 1.010
            in 0.5..1.1 -> 1.000
            in 1.1..1.2 -> 1.050
            in 1.2..1.3 -> 1.100
            in 1.3..1.4 -> 1.150
            in 1.4..1.5 -> 1.5
            else -> 1.5
        }

        const val TankCapacityInBuckets = 2
        val GeneratorBailOutVoltageRatio = 0.5
        val MinimalLoadFractionOfNominalPower = 0.1
        val VoltageStabilizationGracePeriod = 1.0
    }

    internal val tankEnergyCapacity = tankCapacityInSecondsAtNominalPower * nominalPower;

    internal val main = obj?.getPart("main")
    internal val switch = obj?.getPart("switch")
    internal val cableRenderDescriptor = cable.render

    internal val fuels = FuelRegistry.engineList

    init {
        voltageLevelColor = VoltageLevelColor.fromCable(cable)
    }

    override fun setParent(item: net.minecraft.world.item.Item, damage: Int) {
        super.setParent(item, damage)
        Data.addEnergy(newItemStack())
    }

    fun draw(on: Boolean = false) {
        main?.draw()
        if (on) {
            UtilsClient.drawLight(switch)
        } else {
            switch?.draw()
        }
    }

    override fun handleRenderType(item: ItemStack, type: IItemRenderer.ItemRenderType) = true

    override fun shouldUseRenderHelper(
        type: IItemRenderer.ItemRenderType, item: ItemStack,
        helper: IItemRenderer.ItemRendererHelper) = type != IItemRenderer.ItemRenderType.INVENTORY

    override fun renderItem(type: IItemRenderer.ItemRenderType, item: ItemStack, vararg data: Any) = when (type) {
        IItemRenderer.ItemRenderType.INVENTORY -> super.renderItem(type, item, *data)
        else -> {
            objItemScale(obj)
            preserveMatrix {
                Direction.ZP.glRotateXnRef()
                GL11.glTranslatef(0f, -1f, 0f)
                GL11.glScalef(0.6f, 0.6f, 0.6f)
                draw()
            }
        }
    }

    override fun addInformation(itemStack: ItemStack, entityPlayer: Player,
                                list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)

        list.add(tr("Produces electricity using gasoline or diesel."))
        list.add("  " + tr("Nominal voltage: %1$ V", Utils.plotValue(cable.electricalNominalVoltage)))
        list.add("  " + tr("Nominal power: %1$ W", Utils.plotValue(nominalPower)))
    }
}

class FuelGeneratorElement(transparentNode: TransparentNode, descriptor_: TransparentNodeDescriptor) :
    TransparentNodeElement(transparentNode, descriptor_) {
    internal var positiveLoad = NbtElectricalLoad("positiveLoad")
    internal var powerSource = PowerSource("powerSource", positiveLoad)
    internal var slowProcess = FuelGeneratorSlowProcess(this)
    override var descriptor = descriptor_ as FuelGeneratorDescriptor
    internal var tankLevel = 0.0
    /** Nothing in worlds from before it was saved, even if there is fuel. */
    internal var tankFluid: Fluid = Fluids.EMPTY
    internal var on by published(false)
    internal var voltageGracePeriod = 0.0

    init {
        electricalLoadList.add(positiveLoad)
        electricalComponentList.add(powerSource)
        slowProcessList.add(NodePeriodicPublishProcess(transparentNode, 1.0, 0.5))
        slowProcessList.add(slowProcess)
    }

    override fun getElectricalLoad(side: Direction, lrdu: LRDU): ElectricalLoad? = when (lrdu) {
        LRDU.Down -> when (side) {
            front, front.inverse -> positiveLoad
            else -> null
        }
        else -> null
    }

    private fun accepts(fluid: Fluid) = FuelRegistry.isOneOf(fluid, descriptor.fuels) &&
        (fluid === tankFluid || tankFluid === Fluids.EMPTY || tankLevel * FuelGeneratorTank.CAPACITY < 1)

    /** Puts up to [amount] mB in, and returns how many. */
    internal fun fill(fluid: Fluid, amount: Int, doFill: Boolean): Int {
        if (amount <= 0 || !accepts(fluid)) return 0
        // What is in the tank is not a whole number.
        val space = floor((1.0 - tankLevel) * FuelGeneratorTank.CAPACITY + 1e-6).toInt().coerceAtLeast(0)
        val filled = minOf(amount, space)
        if (doFill && filled > 0) {
            tankFluid = fluid
            tankLevel = minOf(1.0, tankLevel + filled.toDouble() / FuelGeneratorTank.CAPACITY)
        }
        return filled
    }

    private val tank = FuelGeneratorTank(this)

    override fun getFluidHandler() = tank

    override fun getThermalLoad(side: Direction, lrdu: LRDU): ThermalLoad? = null

    override fun getConnectionMask(side: Direction, lrdu: LRDU): Int = when (lrdu) {
        LRDU.Down -> when (side) {
            front, front.inverse -> NodeBase.maskElectricalPower
            else -> 0
        }
        else -> 0
    }

    override fun multiMeterString(side: Direction) = Utils.plotVolt("U+:", positiveLoad.voltage) +
        Utils.plotAmpere("I+:", positiveLoad.current) +
        Utils.plotPercent("Fuel level:", tankLevel)


    override fun thermoMeterString(side: Direction): String = ""

    override fun initialize() {
        descriptor.cable.applyTo(positiveLoad)
        powerSource.setMaximumVoltage(descriptor.maxVoltage)
        powerSource.setMaximumCurrent(descriptor.nominalPower * 5 / descriptor.maxVoltage)
        connect()
    }

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        node!!.lrduCubeMask.getTranslate(Direction.YN).serialize(stream)
        stream.writeBoolean(on)
        stream.writeFloat((positiveLoad.voltage / descriptor.maxVoltage).toFloat())
    }

    override fun onBlockActivated(player: Player, side: Direction, vx: Float, vy: Float, vz: Float): Boolean {
        if (player.worldObj.isClientSide == false) {
            val bucket = player.currentEquippedItem
            val held = if (bucket == null) null else FluidUtil.getFluidContained(bucket).orElse(null)
            if (held != null && !held.isEmpty) {
                val handler = FluidUtil.getFluidHandler(bucket!!.copyWithCount(1)).orElse(null)
                if (handler != null && fill(held.fluid, FluidType.BUCKET_VOLUME, false) == FluidType.BUCKET_VOLUME) {
                    // Canisters and the like may hold more, and some give only all of it.
                    val given = handler.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.EXECUTE)
                    if (given.amount == FluidType.BUCKET_VOLUME) {
                        fill(given.fluid, given.amount, true)
                        if (!player.isCreativeMode) {
                            bucket.shrink(1)
                            val rest = handler.container
                            if (!rest.isEmpty && !player.inventory.add(rest)) player.drop(rest, false)
                        }
                        return true
                    }
                }
            } else {
                if (Eln.multiMeterElement.checkSameItemStack(player.currentEquippedItem) ||
                    Eln.thermometerElement.checkSameItemStack(player.currentEquippedItem) ||
                    Eln.allMeterElement.checkSameItemStack(player.currentEquippedItem)) {
                    return false
                }

                if (on) {
                    on = false
                } else {
                    if (tankLevel > 0) {
                        on = true
                        voltageGracePeriod = FuelGeneratorDescriptor.VoltageStabilizationGracePeriod
                    }
                }
                return true
            }
        }

        return false
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        tankLevel = nbt.getDouble("tankLevel")
        tankFluid = FluidNames.find(nbt.getString("tankFluid")) ?: Fluids.EMPTY
        on = nbt.getBoolean("on")
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        nbt.putDouble("tankLevel", tankLevel)
        nbt.putString("tankFluid", FluidNames.idOf(tankFluid))
        nbt.putBoolean("on", on)
    }

    override fun getWaila(): Map<String, String> = mutableMapOf(
        Pair(tr("State"), if (on) tr("ON") else tr("OFF")),
        Pair(tr("Fuel level"), Utils.plotPercent("", tankLevel)),
        Pair(tr("Generated power"), Utils.plotPower("", powerSource.effectivePower)),
        Pair(tr("Voltage"), Utils.plotVolt("", powerSource.voltage))
    )
}

/** What pipes see of the fuel generator. They can only fill it. */
class FuelGeneratorTank(private val generator: FuelGeneratorElement) : IElnFluidHandler {
    companion object {
        const val CAPACITY = FuelGeneratorDescriptor.TankCapacityInBuckets * FluidType.BUCKET_VOLUME
    }

    override fun fill(from: McDirection?, resource: FluidStack?, doFill: Boolean): Int =
        if (resource == null || resource.isEmpty) 0 else generator.fill(resource.fluid, resource.amount, doFill)

    override fun drain(from: McDirection?, resource: FluidStack?, doDrain: Boolean): FluidStack? = null

    override fun drain(from: McDirection?, maxDrain: Int, doDrain: Boolean): FluidStack? = null

    override fun canFill(from: McDirection?, stack: FluidStack): Boolean = FuelRegistry.isOneOf(stack.fluid, generator.descriptor.fuels)

    override fun tankFor(from: McDirection?): FluidTank {
        val tank = FluidTank(CAPACITY)
        val amount = (generator.tankLevel * CAPACITY).toInt()
        if (amount > 0 && generator.tankFluid !== Fluids.EMPTY) tank.fluid = FluidStack(generator.tankFluid, amount)
        return tank
    }

    override fun forSide(side: McDirection?): IFluidHandler {
        val owner = this
        return object : IFluidHandler {
            override fun getTanks() = 1
            override fun getFluidInTank(index: Int): FluidStack = owner.tankFor(side).fluid
            override fun getTankCapacity(index: Int) = CAPACITY
            override fun isFluidValid(index: Int, stack: FluidStack) = owner.canFill(side, stack)
            override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction) = owner.fill(side, resource, action.execute())
            override fun drain(resource: FluidStack, action: IFluidHandler.FluidAction): FluidStack = FluidStack.EMPTY
            override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction): FluidStack = FluidStack.EMPTY
        }
    }
}

class FuelGeneratorRender(tileEntity: TransparentNodeEntity, descriptor: TransparentNodeDescriptor) :
    TransparentNodeElementRender(tileEntity, descriptor) {
    internal var descriptor: FuelGeneratorDescriptor
    private var renderPreProcess: CableRenderType? = null
    private val eConn = LRDUMask()
    private var on = false
    private var voltageRatio = SlewLimiter(1f)
    private val sound = object : LoopedSound("eln:FuelGenerator", coordinate(), SoundInstance.Attenuation.LINEAR) {
        override fun getVolume() = if (on) 0.2f else 0f
        override fun getPitch() = 0.75f + 1f * voltageRatio.position
    }

    init {
        this.descriptor = descriptor as FuelGeneratorDescriptor
        addLoopedSound(sound)
    }

    override fun draw() {
        renderPreProcess = drawCable(Direction.YN, descriptor.cableRenderDescriptor, eConn, renderPreProcess)
        front!!.glRotateZnRef()
        descriptor.draw(on)
    }

    override fun cameraDrawOptimisation(): Boolean = false

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        eConn.deserialize(stream)
        val on_ = stream.readBoolean()
        voltageRatio.target = stream.readFloat()
        if (on_ != on) {
            voltageRatio.position = voltageRatio.target
        }
        on = on_
        renderPreProcess = null
    }

    override fun refresh(deltaT: Float) {
        super.refresh(deltaT)
        voltageRatio.step(deltaT)
    }
}

class FuelGeneratorSlowProcess(internal val generator: FuelGeneratorElement) : IProcess {
    override fun process(time: Double) {
        if (generator.on) {
            val power = Math.max(generator.powerSource.effectivePower,
                generator.descriptor.nominalPower * FuelGeneratorDescriptor.MinimalLoadFractionOfNominalPower)
            generator.tankLevel = Math.max(0.0, generator.tankLevel - time *
                FuelGeneratorDescriptor.EfficiencyFactorVsLoadFactor(power / generator.descriptor.nominalPower) *
                power / generator.descriptor.tankEnergyCapacity)

            if (generator.tankLevel <= 0) {
                generator.on = false;
            }

            if (generator.voltageGracePeriod > 0) {
                generator.voltageGracePeriod -= time;
            } else if (generator.positiveLoad.voltage <
                FuelGeneratorDescriptor.GeneratorBailOutVoltageRatio * generator.descriptor.maxVoltage) {
                generator.on = false;
            }
        }

        if (generator.on) {
            generator.powerSource.power = generator.descriptor.nominalPower
        } else {
            generator.powerSource.power = 0.0
        }
    }
}
