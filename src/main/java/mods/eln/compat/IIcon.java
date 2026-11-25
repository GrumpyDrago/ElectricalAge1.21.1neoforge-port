package mods.eln.compat;

/**
 * Name of a texture, in the form {@code namespace:path}.
 */
public class IIcon {
    private final String iconName;

    public IIcon(String iconName) {
        this.iconName = iconName;
    }

    public String getIconName() {
        return iconName;
    }
}
