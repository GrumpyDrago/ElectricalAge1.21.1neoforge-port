package mods.eln.compat

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.neoforged.fml.loading.FMLEnvironment

/** Client state that common code may ask for; everything is null on a dedicated server. */
object ClientHooks {
    @JvmStatic
    fun player(): Player? = if (FMLEnvironment.dist.isClient) Impl.player() else null

    @JvmStatic
    fun level(): Level? = if (FMLEnvironment.dist.isClient) Impl.level() else null

    @JvmStatic
    fun isPaused(): Boolean = FMLEnvironment.dist.isClient && Impl.isPaused()

    /** Stops the looped sounds and effects that belong to this id. */
    @JvmStatic
    fun killSound(uuid: Int) {
        if (FMLEnvironment.dist.isClient) Impl.kill(uuid)
    }

    /** Shows an Electrical Age GUI, see [mods.eln.GuiHandler]. */
    @JvmStatic
    fun openGui(id: Int, x: Int, y: Int, z: Int) {
        if (FMLEnvironment.dist.isClient) Impl.openGui(id, x, y, z)
    }

    private object Impl {
        fun kill(uuid: Int) = mods.eln.client.ClientProxy.uuidManager.kill(uuid)
        fun openGui(id: Int, x: Int, y: Int, z: Int) = mods.eln.client.ClientGui.open(id, x, y, z)
        fun player(): Player? = Minecraft.getInstance().player
        fun level(): Level? = Minecraft.getInstance().level
        fun isPaused(): Boolean = Minecraft.getInstance().isPaused
    }
}
