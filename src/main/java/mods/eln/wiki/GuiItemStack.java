package mods.eln.wiki;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

import mods.eln.compat.Mc;

import mods.eln.gui.GuiHelper;
import mods.eln.gui.IGuiObject;
import mods.eln.misc.UtilsClient;
import net.minecraft.client.Minecraft;
import mods.eln.compat.client.Gui;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import mods.eln.compat.client.GL11;

import java.util.List;

public class GuiItemStack extends Gui implements IGuiObject {

    public GuiItemStack(int x, int y, ItemStack stack, GuiHelper helper) {
        this.posX = x;
        this.posY = y;
        h = 18;
        w = 18;
        this.stack = stack;
        this.helper = helper;
    }


    int posX, posY, h, w;
    ItemStack stack;

    public GuiHelper helper;
    static final ResourceLocation slotSkin = Mc.rl("textures/gui/container/furnace.png");


    @Override
    public void idraw(int x, int y, float f) {
        GL11.glColor3f(1f, 1f, 1f);
        UtilsClient.bindTexture(slotSkin);
        drawTexturedModalRect(posX - 1, posY - 1, 55, 16, 73 - 55, 34 - 16);
        if (stack != null) UtilsClient.drawItemStack(stack, posX, posY, null, true);
    }

    @Override
    public void idraw2(int x, int y) {
        if (stack == null) return;
        if ((x >= posX && y >= posY && x < posX + w && y < posY + h)) {
            int px, py;
            px = posX;
            py = posY;
            List<String> list = new ArrayList<>();
            for (Component line : Screen.getTooltipFromItem(Minecraft.getInstance(), stack)) list.add(line.getString());
            helper.drawHoveringText(list, x, y, Minecraft.getInstance().font);
        }
    }

    @Override
    public boolean ikeyTyped(char key, int code) {

        return false;
    }

    @Override
    public void imouseClicked(int x, int y, int code) {
        if (x >= posX && y >= posY && x < posX + w && y < posY + h) {
            if (stack != null) {
                UtilsClient.clientOpenGui(new ItemDefault(stack, helper.screen));
            }
			/*if(observer != null){
				observer.guiObjectEvent(this);
			}*/
        }

    }

    @Override
    public void imouseMove(int x, int y) {


    }

    @Override
    public void imouseMovedOrUp(int x, int y, int witch) {


    }

    @Override
    public void translate(int x, int y) {

        this.posX += x;
        this.posY += y;
    }


    @Override
    public int getYMax() {

        return posY + h;
    }


}
