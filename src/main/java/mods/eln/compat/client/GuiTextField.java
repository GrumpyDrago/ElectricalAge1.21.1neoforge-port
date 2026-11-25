package mods.eln.compat.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A text field with the (character, key code) input interface used by the Electrical Age GUIs, backed by {@link EditBox}.
 */
public class GuiTextField extends Gui {
    private final EditBox box;

    public GuiTextField(Font font, int x, int y, int width, int height) {
        box = new EditBox(font, x, y, width, height, Component.empty());
    }

    public void setTextColor(int color) {
        box.setTextColor(color == -1 ? 0xE0E0E0 : color);
    }

    public void setDisabledTextColour(int color) {
        box.setTextColorUneditable(color == -1 ? 0x707070 : color);
    }

    public void setEnableBackgroundDrawing(boolean on) {
        box.setBordered(on);
    }

    public void setMaxStringLength(int length) {
        box.setMaxLength(length);
    }

    public void setText(String text) {
        box.setValue(text == null ? "" : text);
        box.moveCursorToStart(false);
    }

    public String getText() {
        return box.getValue();
    }

    public void setEnabled(boolean enabled) {
        box.setEditable(enabled);
    }

    public boolean isFocused() {
        return box.isFocused();
    }

    public void setFocused(boolean focused) {
        box.setFocused(focused);
    }

    public boolean getVisible() {
        return box.isVisible();
    }

    public void setVisible(boolean visible) {
        box.setVisible(visible);
    }

    protected void moveBy(int dx, int dy) {
        box.setX(box.getX() + dx);
        box.setY(box.getY() + dy);
    }

    public void drawTextBox() {
        GuiGraphics g = GL11.getGuiGraphics();
        if (g == null) return;
        g.flush();
        box.renderWidget(g, 0, 0, 0f);
        g.flush();
    }

    public boolean textboxKeyTyped(char key, int code) {
        if (!isFocused()) return false;
        if (code == 0) return box.charTyped(key, 0);
        int modifiers = 0;
        if (Screen.hasShiftDown()) modifiers |= 1;
        if (Screen.hasControlDown()) modifiers |= 2;
        return box.keyPressed(code, 0, modifiers);
    }

    public void mouseClicked(int x, int y, int button) {
        boolean inside = x >= box.getX() && y >= box.getY() && x < box.getX() + box.getWidth() && y < box.getY() + box.getHeight();
        setFocused(inside);
        if (inside && button == 0) box.onClick(x, y);
    }
}
