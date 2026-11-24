package mods.eln.compat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * Screen without a menu. Translates modern input / render callbacks to the integer based ones the Electrical Age GUI
 * toolkit uses.
 */
public abstract class GuiScreen extends Screen {
    protected Font fontRendererObj = Minecraft.getInstance().font;

    protected GuiScreen() {
        super(Component.empty());
    }

    public void initGui() {
    }

    @Override
    protected void init() {
        super.init();
        initGui();
    }

    protected void keyTyped(char key, int code) {
        if (code == Keyboard.KEY_ESCAPE) onClose();
    }

    protected void mouseClicked(int x, int y, int button) {
    }

    protected void mouseMovedOrUp(int x, int y, int button) {
    }

    public void drawScreen(int x, int y, float partialTick) {
    }

    public void updateScreen() {
    }

    public void onGuiClosed() {
    }

    public boolean doesGuiPauseGame() {
        return true;
    }

    public static void drawDefaultBackground() {
        GuiGraphics g = GL11.getGuiGraphics();
        if (g == null) return;
        g.flush();
        g.fillGradient(0, 0, g.guiWidth(), g.guiHeight(), 0xC0101010, 0xD0101010);
        g.flush();
    }

    static char charFor(int code) {
        return (char) switch (code) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> 13;
            case GLFW.GLFW_KEY_BACKSPACE -> 8;
            case GLFW.GLFW_KEY_TAB -> 9;
            case GLFW.GLFW_KEY_ESCAPE -> 27;
            default -> 0;
        };
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GL11.inGui(g, () -> {
            drawScreen(mouseX, mouseY, partialTick);
            return null;
        });
    }

    @Override
    public boolean keyPressed(int code, int scan, int modifiers) {
        keyTyped(charFor(code), code);
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        keyTyped(c, 0);
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        mouseClicked((int) x, (int) y, button);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        mouseMovedOrUp((int) x, (int) y, button);
        return true;
    }

    @Override
    public void tick() {
        updateScreen();
    }

    @Override
    public void removed() {
        onGuiClosed();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return doesGuiPauseGame();
    }
}
