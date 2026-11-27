package mods.eln.registration

/**
 * The variants of the chemistry batteries, as multiples of the standard one: the capacity oriented has half the voltage
 * and twice the energy, the voltage oriented twice the voltage, the current oriented twice the power for half the
 * energy, and the life oriented does not wear out. (Energy is power times discharge time.)
 */
enum class Variant(val voltage: Double, val power: Double, val time: Double, val life: Double, val wears: Boolean) {
    STANDARD(1.0, 1.0, 1.0, 1.0, true),
    CAPACITY(0.5, 0.5, 4.0, 1.0, true),
    VOLTAGE(2.0, 1.0, 1.0, 1.0, true),
    CURRENT(1.0, 2.0, 0.25, 1.0, true),
    LIFE(1.0, 1.0, 1.0, 8.0, false),
}
