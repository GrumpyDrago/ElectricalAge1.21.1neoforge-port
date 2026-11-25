package mods.eln.compat;

/**
 * Logical side.
 */
public enum Side {
    CLIENT, SERVER;

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isServer() {
        return this == SERVER;
    }
}
