package mods.eln.client

import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.compat.client.ElnItemRenderer
import mods.eln.compat.client.FlatIcon
import mods.eln.compat.client.GuiContainer
import mods.eln.entity.ElnEntities
import mods.eln.entity.ReplicatorModel
import mods.eln.entity.ReplicatorRender
import mods.eln.fluid.ElnFluids
import mods.eln.generic.DescriptorItem
import mods.eln.node.six.SixNodeEntity
import mods.eln.node.six.SixNodeRender
import mods.eln.node.transparent.TransparentNodeRender
import mods.eln.registration.ElnBlockEntities
import mods.eln.sixnode.tutorialsign.TutorialSignOverlay
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.MinecartRenderer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.AbstractContainerMenu
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import net.neoforged.neoforge.client.event.RenderHighlightEvent
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent
import net.neoforged.neoforge.common.NeoForge

/** Client side setup. */
object ClientProxy {
    @JvmField
    val uuidManager = UuidManager()

    @JvmStatic
    fun register(modBus: IEventBus) {
        modBus.addListener(::setup)
        modBus.addListener(::renderers)
        modBus.addListener(::layers)
        modBus.addListener(::menuScreens)
        modBus.addListener(::extensions)
        modBus.addListener(::keys)
        modBus.addListener(::itemColors)
        modBus.addListener(::itemDecorations)
        NeoForge.EVENT_BUS.register(this)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setup(event: FMLClientSetupEvent) {
        NeoForge.EVENT_BUS.register(TutorialSignOverlay())
        if (Eln.config.getBooleanOrElse("updates.versionCheck.enabled", true)) {
            VersionCheckerHandler.start()
        }
        FrameTime()
        ConnectionListener()
    }

    private fun renderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(ElnBlockEntities.sixNode, ::SixNodeRender)
        event.registerBlockEntityRenderer(ElnBlockEntities.transparentNode, ::TransparentNodeRender)
        event.registerBlockEntityRenderer(ElnBlockEntities.transparentNodeWithFluid) { TransparentNodeRender(it) }
        event.registerEntityRenderer(ElnEntities.replicator, ::ReplicatorRender)
        event.registerEntityRenderer(ElnEntities.electricMinecart) { MinecartRenderer(it, ModelLayers.MINECART) }
    }

    private fun layers(event: EntityRenderersEvent.RegisterLayerDefinitions) {
        event.registerLayerDefinition(ReplicatorModel.LAYER, ReplicatorModel::createLayer)
    }

    private fun menuScreens(event: RegisterMenuScreensEvent) {
        event.register(GuiHandler.menuType, MenuScreens.ScreenConstructor<AbstractContainerMenu, GuiContainer>(ClientGui::screenFor))
    }

    private fun keys(event: RegisterKeyMappingsEvent) {
        Eln.clientKeyHandler = ClientKeyHandler()
        Eln.clientKeyHandler.register(event)
        NeoForge.EVENT_BUS.register(Eln.clientKeyHandler)
    }

    /** Hides the backgrounds of the icons if they are not wanted. */
    private fun itemColors(event: RegisterColorHandlersEvent.Item) {
        val option = "ui.icons.noVoltageBackground"
        if (!Eln.config.getBooleanOrElse(option, false)) return
        // The models are made with them.
        Eln.config.setRuntimeBoolean(option, false)
        val items = BuiltInRegistries.ITEM.filterIsInstance<DescriptorItem>().filter {
            FlatIcon.of(it)?.let { icon -> FlatIcon.isBackground(icon.layers.first()) } == true
        }
        Eln.config.setRuntimeBoolean(option, true)
        event.register({ _, layer -> if (layer == 0) 0x00FFFFFF else -1 }, *items.toTypedArray())
    }

    private fun itemDecorations(event: RegisterItemDecorationsEvent) {
        for (item in BuiltInRegistries.ITEM) {
            val icon = (item as? DescriptorItem)?.let(FlatIcon::of) ?: continue
            if (icon.hasMore) event.register(item, icon)
        }
    }

    private fun extensions(event: RegisterClientExtensionsEvent) {
        val renderer = object : IClientItemExtensions {
            private val instance by lazy { ElnItemRenderer() }
            override fun getCustomRenderer(): BlockEntityWithoutLevelRenderer = instance
        }
        val items = BuiltInRegistries.ITEM.filter { it is DescriptorItem && it.hasCustomRenderer() }
        event.registerItem(renderer, *items.toTypedArray())
        for (fluid in ElnFluids.all) {
            val still = ResourceLocation.fromNamespaceAndPath("eln", "block/fluids/${fluid.name}_still")
            val flow = ResourceLocation.fromNamespaceAndPath("eln", "block/fluids/${fluid.name}_flow")
            event.registerFluidType(object : IClientFluidTypeExtensions {
                override fun getStillTexture(): ResourceLocation = still
                override fun getFlowingTexture(): ResourceLocation = flow
            }, fluid.type)
        }
    }

    /** Outlines just the element under the cursor instead of everything in the block. */
    @SubscribeEvent
    fun onHighlight(event: RenderHighlightEvent.Block) {
        val hit = event.target
        val level = event.camera.entity.level()
        val entity = level.getBlockEntity(hit.blockPos) as? SixNodeEntity ?: return
        val shape = Eln.sixNodeBlock.outline(entity, hit)
        val camera = event.camera.position
        val pos = hit.blockPos
        LevelRenderer.renderVoxelShape(
            event.poseStack, event.multiBufferSource.getBuffer(RenderType.lines()), shape,
            pos.x - camera.x, pos.y - camera.y, pos.z - camera.z, 0f, 0f, 0f, 0.4f, true
        )
        event.isCanceled = true
    }
}
