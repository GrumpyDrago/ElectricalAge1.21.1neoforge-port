package mods.eln.compat;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Access to the running server, the logical side and the game event bus.
 */
public final class FMLCommonHandler {
    private static final FMLCommonHandler INSTANCE = new FMLCommonHandler();

    private FMLCommonHandler() {
    }

    public static FMLCommonHandler instance() {
        return INSTANCE;
    }

    public Side getEffectiveSide() {
        return EffectiveSide.get().isClient() ? Side.CLIENT : Side.SERVER;
    }

    public MinecraftServer getMinecraftServerInstance() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public IEventBus bus() {
        return NeoForge.EVENT_BUS;
    }
}
