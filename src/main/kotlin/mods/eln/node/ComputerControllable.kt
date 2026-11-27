package mods.eln.node

/**
 * A part a computer (CC: Tweaked) can read numbers from and give orders to, through the `eln` peripheral. Every part
 * can be read as the text Jade shows; this gives real numbers, and actions where switching from afar makes sense.
 * Nothing here depends on CC: Tweaked, so it works without it.
 */
interface ComputerControllable {
    /** Numbers, flags and words a computer can read, by name. Lists and maps become Lua tables. */
    fun computerValues(): Map<String, Any?>

    /** The actions [computerCall] takes, as "name(arguments): what it does". */
    fun computerActions(): List<String> = emptyList()

    /**
     * Does [action] with [args] (numbers come as Double, flags as Boolean). Returns what to give back to the computer;
     * throws [IllegalArgumentException] with a message for a wrong action or argument.
     */
    fun computerCall(action: String, args: List<Any?>): Any? = throw IllegalArgumentException("no action $action")

    companion object {
        fun number(args: List<Any?>, i: Int, name: String): Double =
            (args.getOrNull(i) as? Number)?.toDouble() ?: throw IllegalArgumentException("argument ${i + 1} ($name) must be a number")

        fun flag(args: List<Any?>, i: Int, name: String): Boolean =
            args.getOrNull(i) as? Boolean ?: throw IllegalArgumentException("argument ${i + 1} ($name) must be true or false")
    }
}
