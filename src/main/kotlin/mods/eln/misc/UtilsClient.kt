@file:Suppress("NAME_SHADOWING")
package mods.eln.misc

import mods.eln.compat.client.Gui
import mods.eln.packets.ElnPayload
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.item.ItemDisplayContext
import net.neoforged.neoforge.network.PacketDistributor
import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Obj3D.Obj3DPart
import mods.eln.node.six.SixNodeEntity
import mods.eln.node.transparent.TransparentNodeEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.util.Mth
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.Level
import mods.eln.compat.IItemRenderer.ItemRenderType
import mods.eln.compat.client.Keyboard
import mods.eln.compat.client.GL11
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.math.sqrt

object UtilsClient {
    @JvmField
    var guiLastOpen: Screen? = null
    @JvmStatic
    var uuid = Int.MIN_VALUE
        get() {
            if (field > -1) field = Int.MIN_VALUE
            return field++
        }
        private set
    val whiteTexture = rl("eln", "sprites/cable.png")
    val portableBatteryOverlayResource = rl("eln", "sprites/portablebatteryoverlay.png")
    fun distanceFromClientPlayer(@Suppress("UNUSED_PARAMETER") world: Level?, xCoord: Int, yCoord: Int, zCoord: Int): Float {
        val player = Minecraft.getInstance().player!!
        return Math.sqrt((xCoord - player.x) * (xCoord - player.x) + (yCoord - player.y) * (yCoord - player.y) + (zCoord - player.z) * (zCoord - player.z)).toFloat()
    }

    @JvmStatic
    fun distanceFromClientPlayer(tileEntity: SixNodeEntity): Float {
        return distanceFromClientPlayer(tileEntity.worldObj, tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord)
    }

    val clientPlayer: LocalPlayer
        get() = Minecraft.getInstance().player!!

    fun drawHaloNoLightSetup(halo: Obj3DPart?, r: Float, g: Float, b: Float, w: Level, x: Int, y: Int, z: Int, bilinear: Boolean) {
        if (halo == null) return
        withBilinearFilters(halo, bilinear) {
            val light = getLight(w, x, y, z) * 19 / 15 - 4
            val e: Entity = clientPlayer
            val d = (Math.abs(x - e.x) + Math.abs(y - e.y) + Math.abs(z - e.z)).toFloat()
            GL11.glColor4f(r, g, b, 1f - light / 15f)
            halo.draw(d * 20, 1f, 0f, 0f)
            GL11.glColor4f(1f, 1f, 1f, 1f)
        }
    }

    @JvmStatic
    fun clientOpenGui(gui: Screen?) {
        guiLastOpen = gui
        Minecraft.getInstance().setScreen(gui)
    }

    @JvmStatic
    fun drawHalo(halo: Obj3DPart?, r: Float, g: Float, b: Float, w: Level, x: Int, y: Int, z: Int, bilinear: Boolean) {
        disableLight()
        enableBlend()
        drawHaloNoLightSetup(halo, r, g, b, w, x, y, z, bilinear)
        enableLight()
        disableBlend()
    }

    @JvmStatic
    fun drawHaloNoLightSetup(halo: Obj3DPart?, r: Float, g: Float, b: Float, e: BlockEntity, bilinear: Boolean) {
        drawHaloNoLightSetup(halo, r, g, b, e.worldObj, e.xCoord, e.yCoord, e.zCoord, bilinear)
    }

    @JvmStatic
    fun drawHalo(halo: Obj3DPart?, r: Float, g: Float, b: Float, e: BlockEntity, bilinear: Boolean) {
        drawHalo(halo, r, g, b, e.worldObj, e.xCoord, e.yCoord, e.zCoord, bilinear)
    }

    @JvmStatic
    fun drawHaloNoLightSetup(halo: Obj3DPart?, @Suppress("UNUSED_PARAMETER") distance: Float) {
        if (halo == null) return
        // This overload deliberately draws without rebinding, using the first face group's texture.
        // Preserve that behavior while restoring the texture's original filters afterward.
        val faceGroup = halo.faceGroup.firstOrNull()
        if (faceGroup?.textureResource == null) {
            halo.drawNoBind()
            return
        }
        faceGroup.bindTexture()
        val filterState = saveTextureFilter()
        setBilinearFilter()
        try {
            halo.drawNoBind()
        } finally {
            faceGroup.bindTexture()
            restoreTextureFilter(filterState)
        }
    }

    @JvmStatic
    fun drawHalo(halo: Obj3DPart?, distance: Float) {
        disableLight()
        enableBlend()
        drawHaloNoLightSetup(halo, distance)
        enableLight()
        disableBlend()
    }

    @JvmStatic
    fun drawHaloNoLightSetup(halo: Obj3DPart?, r: Float, g: Float, b: Float, e: Entity, bilinear: Boolean) {
        if (halo == null) return
        withBilinearFilters(halo, bilinear) {
            val light = getLight(e.worldObj, Mth.floor(e.x), Mth.floor(e.y), Mth.floor(e.z))
            GL11.glColor4f(r, g, b, 1f - light / 15f)
            halo.draw()
            GL11.glColor4f(1f, 1f, 1f, 1f)
        }
    }

    @JvmStatic
    fun drawHalo(halo: Obj3DPart?, r: Float, g: Float, b: Float, e: Entity, bilinear: Boolean) {
        disableLight()
        enableBlend()
        drawHaloNoLightSetup(halo, r, g, b, e, bilinear)
        enableLight()
        disableBlend()
    }

    data class TextureFilterState(val minFilter: Int, val magFilter: Int)

    /** Saves the filters on the currently bound texture. */
    @JvmStatic
    fun saveTextureFilter(): TextureFilterState = TextureFilterState(
        GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER),
        GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER)
    )

    @JvmStatic
    fun setBilinearFilter() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
    }

    @JvmStatic
    fun setNearestFilter() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
    }

    @JvmStatic
    fun restoreTextureFilter(state: TextureFilterState) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, state.minFilter)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, state.magFilter)
    }

    private fun withBilinearFilters(halo: Obj3DPart, enabled: Boolean, draw: () -> Unit) {
        if (!enabled) {
            draw()
            return
        }

        // Texture filters belong to texture objects, not OpenGL's transient draw state.
        // Bind each texture this model can draw before changing it, then restore it afterward.
        val filterStates = halo.faceGroup
            .filter { it.textureResource != null }
            .distinctBy { it.textureResource }
            .map { faceGroup ->
                faceGroup.bindTexture()
                faceGroup to saveTextureFilter().also { setBilinearFilter() }
            }
        try {
            draw()
        } finally {
            filterStates.forEach { (faceGroup, filterState) ->
                faceGroup.bindTexture()
                restoreTextureFilter(filterState)
            }
        }
    }

    @JvmStatic
    fun disableCulling() {
        GL11.glDisable(GL11.GL_CULL_FACE)
    }

    @JvmStatic
    fun enableCulling() {
        GL11.glEnable(GL11.GL_CULL_FACE)
    }

    @JvmStatic
    fun disableTexture() {
        bindTexture(whiteTexture)
        //GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    @JvmStatic
    fun enableTexture() {
        //GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    @JvmStatic
    fun disableLight() {
        GL11.glDisable(GL11.GL_LIGHTING)
    }

    @JvmStatic
    fun enableLight() {
        GL11.glEnable(GL11.GL_LIGHTING)
    }

    @JvmStatic
    fun enableBlend() {
        GL11.glEnable(GL11.GL_BLEND)
    }

    @JvmStatic
    fun disableBlend() {
        GL11.glDisable(GL11.GL_BLEND)
    }

    fun drawIcon(type: ItemRenderType) {
        GL11.glNormal3f(0f, 1f, 0f)
        disableLight()
        enableBlend()
        when (type) {
            ItemRenderType.INVENTORY -> {
                disableCulling()
                GL11.glBegin(GL11.GL_QUADS)
                GL11.glTexCoord2f(1f, 0f)
                GL11.glVertex3f(16f, 0f, 0f)
                GL11.glTexCoord2f(0f, 0f)
                GL11.glVertex3f(0f, 0f, 0f)
                GL11.glTexCoord2f(0f, 1f)
                GL11.glVertex3f(0f, 16f, 0f)
                GL11.glTexCoord2f(1f, 1f)
                GL11.glVertex3f(16f, 16f, 0f)
                GL11.glEnd()
                enableCulling()
            }
            ItemRenderType.ENTITY -> {
                disableCulling()
                GL11.glBegin(GL11.GL_QUADS)
                GL11.glTexCoord2f(1f, 1f)
                GL11.glVertex3f(0f, 0f, 0.5f)
                GL11.glTexCoord2f(0f, 1f)
                GL11.glVertex3f(0.0f, 0f, -0.5f)
                GL11.glTexCoord2f(0f, 0f)
                GL11.glVertex3f(0.0f, 1f, -0.5f)
                GL11.glTexCoord2f(1f, 0f)
                GL11.glVertex3f(0.0f, 1f, 0.5f)
                GL11.glEnd()
                enableCulling()
            }
            else -> {
                GL11.glTranslatef(0.5f, -0.3f, 0.5f)
                disableCulling()
                GL11.glBegin(GL11.GL_QUADS)
                GL11.glTexCoord2f(1f, 1f)
                GL11.glVertex3f(0.0f, 0.5f, 0.5f)
                GL11.glTexCoord2f(0f, 1f)
                GL11.glVertex3f(0.0f, 0.5f, -0.5f)
                GL11.glTexCoord2f(0f, 0f)
                GL11.glVertex3f(0.0f, 1.5f, -0.5f)
                GL11.glTexCoord2f(1f, 0f)
                GL11.glVertex3f(0.0f, 1.5f, 0.5f)
                GL11.glEnd()
                enableCulling()
            }
        }
        disableBlend()
        enableLight()
    }

    @JvmStatic
    fun drawIcon(type: ItemRenderType, icon: ResourceLocation?) {
        bindTexture(icon)
        drawIcon(type)
    }

    fun drawEnergyBare(type: ItemRenderType, e: Float) {
        drawIcon(type, portableBatteryOverlayResource)
        disableLight()
        val x = 13f
        val y = 14f - e * 12f
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glColor3f(0f, 0f, 0f)
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glVertex3f(x + 1f, 2f, 0.01f)
        GL11.glVertex3f(x, 2f, 0.01f)
        GL11.glVertex3f(x, 14f, 0.01f)
        GL11.glVertex3f(x + 1f, 14f, 0.01f)
        GL11.glEnd()
        GL11.glColor3f(1f, e, 0f)
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glVertex3f(x + 1f, y, 0.02f)
        GL11.glVertex3f(x, y, 0.02f)
        GL11.glVertex3f(x, 14f, 0.02f)
        GL11.glVertex3f(x + 1f, 14f, 0.02f)
        GL11.glEnd()
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glColor3f(1f, 1f, 1f)
        enableLight()
    }

    @JvmStatic
    fun bindTexture(resource: ResourceLocation?) {
        GL11.bindTexture(resource)
    }

    @JvmStatic
    fun ledOnOffColor(on: Boolean) {
        if (!on) GL11.glColor3f(0.7f, 0f, 0f) // Red
        else GL11.glColor3f(0f, 0.7f, 0f) // Green
    }

    @JvmStatic
    fun ledOnOffColorC(on: Boolean): Color {
        return if (!on) Color(0.7f, 0f, 0f) // Red
        else Color(0f, 0.7f, 0f) // Green
    }

    @JvmStatic
    fun drawLight(part: Obj3DPart?) {
        if (part == null) return
        disableLight()
        enableBlend()
        part.draw()
        enableLight()
        disableBlend()
    }

    @JvmStatic
    fun drawLightNoBind(part: Obj3DPart?) {
        if (part == null) return
        disableLight()
        enableBlend()
        part.drawNoBind()
        enableLight()
        disableBlend()
    }

    @JvmStatic
    fun drawGuiBackground(ressource: ResourceLocation?, guiScreen: Screen, xSize: Int, ySize: Int) {
        bindTexture(ressource)
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
        val x = (guiScreen.width - xSize) / 2
        val y = (guiScreen.height - ySize) / 2
        Gui.drawTexturedModalRect(x, y, 0, 0, xSize, ySize)
    }

    fun drawLight(part: Obj3DPart?, angle: Float, x: Float, y: Float, z: Float) {
        if (part == null) return
        disableLight()
        enableBlend()
        part.draw(angle, x, y, z)
        enableLight()
        disableBlend()
    }

    @JvmStatic
    fun glDefaultColor() {
        GL11.glColor4f(1f, 1f, 1f, 1f)
    }

    @JvmStatic
    fun drawEntityItem(entityItem: ItemEntity?, x: Double, y: Double, z: Double, roty: Float, scale: Float) {
        if (entityItem == null) return
        val buffers = GL11.buffers ?: return
        GL11.glPushMatrix()
        GL11.glTranslatef(x.toFloat(), y.toFloat(), z.toFloat())
        GL11.glRotatef(roty, 0f, 1f, 0f)
        GL11.glScalef(scale, scale, scale)
        val mc = Minecraft.getInstance()
        mc.itemRenderer.renderStatic(
            entityItem.item, ItemDisplayContext.GROUND, GL11.light, OverlayTexture.NO_OVERLAY, GL11.pose, buffers, mc.level, 0
        )
        GL11.glPopMatrix()
    }

    fun drawConnectionPinSixNode(d: Float, w: Float, h: Float) {
        var d = d
        var w = w
        var h = h
        d += 0.1f
        d *= 0.0625f
        w *= 0.0625f
        h *= 0.0625f
        val w2 = w * 0.5f
        disableTexture()
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glVertex3f(-w2, d, 0f)
        GL11.glVertex3f(w2, d, 0f)
        GL11.glVertex3f(w2, d, h)
        GL11.glVertex3f(-w2, d, h)
        GL11.glEnd()
        enableTexture()
    }

    @JvmStatic
    fun drawConnectionPinSixNode(front: LRDU, dList: FloatArray, w: Float, h: Float) {
        // front.glRotateOnX();
        // drawConnectionPinSixNode(d[front.toInt()], w, h);
        var w = w
        var h = h
        var d = dList[front.toInt()]
        d += 0.04f
        d *= 0.0625f
        w *= 0.0625f
        h *= 0.0625f
        val w2 = w * 0.5f
        disableTexture()
        GL11.glBegin(GL11.GL_QUADS)
        when (front) {
            LRDU.Left -> {
                GL11.glVertex3f(0f, -w2, -d)
                GL11.glVertex3f(0f, w2, -d)
                GL11.glVertex3f(h, w2, -d)
                GL11.glVertex3f(h, -w2, -d)
            }
            LRDU.Right -> {
                GL11.glVertex3f(h, -w2, d)
                GL11.glVertex3f(h, w2, d)
                GL11.glVertex3f(0f, w2, d)
                GL11.glVertex3f(0f, -w2, d)
            }
            LRDU.Down -> {
                GL11.glVertex3f(h, -d, -w2)
                GL11.glVertex3f(h, -d, w2)
                GL11.glVertex3f(0f, -d, w2)
                GL11.glVertex3f(0f, -d, -w2)
            }
            LRDU.Up -> {
                GL11.glVertex3f(0f, d, -w2)
                GL11.glVertex3f(0f, d, w2)
                GL11.glVertex3f(h, d, w2)
                GL11.glVertex3f(h, d, -w2)
            }
        }
        GL11.glEnd()
        enableTexture()
    }

    fun mc(): Minecraft {
        return Minecraft.getInstance()
    }

    fun guiScale() {
        GL11.glScalef(16f, 16f, 1f)
    }

    @JvmStatic
    fun drawItemStack(par1ItemStack: ItemStack?, x: Int, y: Int, @Suppress("UNUSED_PARAMETER") par4Str: String?, gui: Boolean) {
        val stack = par1ItemStack.orNull() ?: return
        val graphics = GL11.guiGraphics
        if (graphics != null) {
            graphics.flush()
            graphics.renderItem(stack, x, y)
            if (stack.count > 1) graphics.renderItemDecorations(Minecraft.getInstance().font, stack, x, y)
            graphics.flush()
            return
        }
        // Inside an item or block renderer: draw the flat GUI form at pixel scale.
        val buffers = GL11.buffers ?: return
        GL11.glPushMatrix()
        GL11.glTranslatef(x + 8f, y + 8f, 0f)
        GL11.glScalef(16f, -16f, 1f)
        val mc = Minecraft.getInstance()
        mc.itemRenderer.renderStatic(
            stack, ItemDisplayContext.GUI, GL11.light, OverlayTexture.NO_OVERLAY, GL11.pose, buffers, mc.level, 0
        )
        GL11.glPopMatrix()
    }

    fun clientDistanceTo(e: Entity?): Double {
        if (e == null) return 100000000.0
        val c: Entity = Minecraft.getInstance().player!!
        val x = c.x - e.x
        val y = c.y - e.y
        val z = c.z - e.z
        return sqrt(x * x + y * y + z * z)
    }

    @JvmStatic
    fun clientDistanceTo(t: TransparentNodeEntity?): Double {
        if (t == null) return 100000000.0
        val c: Entity = Minecraft.getInstance().player!!
        val x = c.x - t.xCoord
        val y = c.y - t.yCoord
        val z = c.z - t.zCoord
        return sqrt(x * x + y * y + z * z)
    }

    fun getLight(w: Level, x: Int, y: Int, z: Int): Int {
        val b = w.getSkyBlockTypeBrightness(LightLayer.BLOCK, x, y, z)
        val s = w.getSkyBlockTypeBrightness(LightLayer.SKY, x, y, z) - w.calculateSkylightSubtracted()
        return b.coerceAtLeast(s)
    }

    @JvmStatic
    fun disableDepthTest() {
        GL11.glDisable(GL11.GL_DEPTH_TEST)
    }

    @JvmStatic
    fun enableDepthTest() {
        GL11.glEnable(GL11.GL_DEPTH_TEST)
    }

    @JvmStatic
    fun sendPacketToServer(bos: ByteArrayOutputStream) {
        PacketDistributor.sendToServer(ElnPayload(bos.toByteArray()))
    }

    val glListsAllocated = HashSet<Int>()
    @JvmStatic
    fun glGenListsSafe(): Int {
        val id = GL11.glGenLists(1)
        glListsAllocated.add(id)
        return id
    }

    @JvmStatic
    fun glDeleteListsSafe(id: Int) {
        glListsAllocated.remove(id)
        GL11.glDeleteLists(id, 1)
    }

    @JvmStatic
    fun glDeleteListsAllSafe() {
        try {
            for (id in glListsAllocated) {
                GL11.glDeleteLists(id, 1)
            }
            glListsAllocated.clear()
        } catch (e: Exception) {
            //nic
        }
    }

    @JvmStatic
    fun showItemTooltip(details: List<String>, realismDetails: List<String>, realisticEnum: RealisticEnum?, dst: MutableList<String>) {
        if (realisticEnum != null)
            dst.add("§r${realisticEnum.color}${realisticEnum.name}§r")
        if (details.isNotEmpty()) {
            if (isShiftHeld()) {
                dst.addAll(details)
            } else {
                dst.add("§F§o${tr("Hold [shift] for details")}")
            }
        }
        if (realismDetails.isNotEmpty()) {
            if (isControlHeld()) {
                dst.addAll(realismDetails)
            } else {
                if (realisticEnum != null) {
                    if (realismDetails.isNotEmpty()) {
                        dst.add("§F§o${tr("Hold [ctrl] for realism details")}")
                    }
                }
            }
        }
        dst.listLengthFormatter(24)
    }

    private fun List<String>.listLengthFormatter(@Suppress("UNUSED_PARAMETER") length: Int) {}

    private fun isShiftHeld(): Boolean {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
    }

    private fun isControlHeld(): Boolean {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
    }

    @JvmStatic
    fun getWeather(world: Level): Double {
        if (world.isThundering) return 1.0
        return if (world.isRaining) 0.5 else 0.0
    }
}
