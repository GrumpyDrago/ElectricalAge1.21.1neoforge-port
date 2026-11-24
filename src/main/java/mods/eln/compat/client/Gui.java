package mods.eln.compat.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 2D drawing helpers. Everything goes through {@link GL11}, so it only works while a GUI context is open.
 */
public class Gui {

    public static void drawRect(int x0, int y0, int x1, int y1, int argb) {
        drawGradientRect(x0, y0, x1, y1, argb, argb);
    }

    public static void drawGradientRect(int x0, int y0, int x1, int y1, int top, int bottom) {
        if (x0 > x1) {
            int t = x0;
            x0 = x1;
            x1 = t;
        }
        if (y0 > y1) {
            int t = y0;
            y0 = y1;
            y1 = t;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_QUADS);
        color(top);
        GL11.glVertex2f(x1, y0);
        GL11.glVertex2f(x0, y0);
        color(bottom);
        GL11.glVertex2f(x0, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glEnd();
        GL11.glPopAttrib();
    }

    private static void color(int argb) {
        GL11.glColor4f((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    /** Draws a piece of the bound 256x256 texture. */
    public static void drawTexturedModalRect(int x, int y, int u, int v, int width, int height) {
        float s = 1f / 256f;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u * s, (v + height) * s);
        GL11.glVertex2f(x, y + height);
        GL11.glTexCoord2f((u + width) * s, (v + height) * s);
        GL11.glVertex2f(x + width, y + height);
        GL11.glTexCoord2f((u + width) * s, v * s);
        GL11.glVertex2f(x + width, y);
        GL11.glTexCoord2f(u * s, v * s);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
    }

    /** Text with a drop shadow. */
    public static void drawString(Font font, String text, int x, int y, int color) {
        GL11.drawString(text, x, y, color, true);
    }

    public static void drawCenteredString(Font font, String text, int x, int y, int color) {
        GL11.drawString(text, x - font.width(text) / 2f, y, color, true);
    }

    /** Text without a shadow. */
    public static void drawText(Font font, String text, int x, int y, int color) {
        GL11.drawString(text, x, y, color, false);
    }

    public static void drawTextShadow(Font font, String text, int x, int y, int color) {
        GL11.drawString(text, x, y, color, true);
    }

    /** Clips drawing to a rectangle in GUI coordinates. */
    public static void enableScissor(int x0, int y0, int x1, int y1) {
        GuiGraphics g = GL11.getGuiGraphics();
        if (g != null) g.enableScissor(x0, y0, x1, y1);
    }

    public static void disableScissor() {
        GuiGraphics g = GL11.getGuiGraphics();
        if (g != null) g.disableScissor();
    }
}
