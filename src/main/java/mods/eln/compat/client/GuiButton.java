package mods.eln.compat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.ResourceLocation;

/**
 * A push button drawn with the vanilla button sprites.
 */
public class GuiButton extends Gui {
    private static final WidgetSprites SPRITES = new WidgetSprites(
        ResourceLocation.withDefaultNamespace("widget/button"),
        ResourceLocation.withDefaultNamespace("widget/button_disabled"),
        ResourceLocation.withDefaultNamespace("widget/button_highlighted"));

    public int id;
    public int xPosition, yPosition;
    public int width, height;
    public String displayString;
    public boolean enabled = true;
    public boolean visible = true;

    public GuiButton(int id, int x, int y, int width, int height, String text) {
        this.id = id;
        this.xPosition = x;
        this.yPosition = y;
        this.width = width;
        this.height = height;
        this.displayString = text;
    }

    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        GuiGraphics g = GL11.getGuiGraphics();
        if (g == null) return;
        boolean hover = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
        g.flush();
        g.blitSprite(SPRITES.get(enabled, hover), xPosition, yPosition, width, height);
        int color = !enabled ? 0xFFA0A0A0 : hover ? 0xFFFFFFA0 : 0xFFE0E0E0;
        drawCenteredString(mc.font, displayString, xPosition + width / 2, yPosition + (height - 8) / 2, color);
    }

    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        return enabled && visible && mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
    }
}
