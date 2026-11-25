package mods.eln.compat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.NotNull;

/**
 * Screen with a menu; see {@link GuiScreen} for the callback style.
 */
public abstract class GuiContainer extends AbstractContainerScreen<AbstractContainerMenu> {
    protected int xSize = 176, ySize = 166;
    public AbstractContainerMenu inventorySlots;
    protected Font fontRendererObj = Minecraft.getInstance().font;

    protected GuiContainer(AbstractContainerMenu menu) {
        super(menu, Minecraft.getInstance().player.getInventory(), Component.empty());
        inventorySlots = menu;
    }

    public void initGui() {
        imageWidth = xSize;
        imageHeight = ySize;
        super.init();
    }

    @Override
    protected void init() {
        initGui();
    }

    protected void keyTyped(char key, int code) {
        if (code == Keyboard.KEY_ESCAPE) onClose();
    }

    protected void mouseClicked(int x, int y, int button) {
    }

    protected void mouseMovedOrUp(int x, int y, int button) {
    }

    public void updateScreen() {
    }

    public void onGuiClosed() {
    }

    protected abstract void drawGuiContainerBackgroundLayer(float partialTick, int mouseX, int mouseY);

    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        GL11.inGui(g, () -> {
            drawGuiContainerBackgroundLayer(partialTick, mouseX, mouseY);
            return null;
        });
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        GL11.inGui(g, () -> {
            drawGuiContainerForegroundLayer(mouseX, mouseY);
            return null;
        });
    }

    @Override
    public boolean keyPressed(int code, int scan, int modifiers) {
        keyTyped(GuiScreen.charFor(code), code);
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
        super.mouseClicked(x, y, button);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        mouseMovedOrUp((int) x, (int) y, button);
        return super.mouseReleased(x, y, button);
    }

    @Override
    protected void containerTick() {
        updateScreen();
    }

    @Override
    public void removed() {
        onGuiClosed();
        super.removed();
    }
}
