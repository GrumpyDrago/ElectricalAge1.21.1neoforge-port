package mods.eln.server.console

import mods.eln.compat.*

import mods.eln.misc.FC
import net.minecraft.commands.CommandSourceStack
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.Commands
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import java.util.concurrent.CompletableFuture

val ElnConsoleCommandList = mutableListOf<IConsoleCommand>()

internal fun findConsoleCommand(
    name: String,
    commands: Iterable<IConsoleCommand> = ElnConsoleCommandList
): IConsoleCommand? {
    return commands.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

object ElnConsoleCommands {

    init {
        ElnConsoleCommandList.addAll(listOf(
            ElnLsCommand(),
            ElnConfigCommand(),
            ElnAboutCommand(),
            ElnVersionCommand(),
            ElnNewWindDirectionCommand(),
            ElnMatrixCommand(),
            ElnManCommand(),
            ElnWailaEasyModeCommand(),
            ElnDebugCommand(),
            ElnSimSnapshotCommand(),
            ElnWatchdogCommand(),
            ElnIconsCommand(),
            ElnPoleMapCommand(),
            ElnStopShaftCommand(),
            ElnResetAmbientTempsCommand(),
            ElnResetLampLifeCommand(),
            ElnZoneDumpCommand(),
            ElnZoneCleanCommand(),
            ElnZoneRemoveCommand(),
            ElnZoneDestroyCommand()
        ))
    }

        fun cprint(player: Player, text: String, indent: Int = 0) {
            printIndented(text, indent).forEach { player.sendSystemMessage(Component.literal(it)) }
        }

        fun cprint(ics: CommandSourceStack, text: String, indent: Int = 0) {
            printIndented(text, indent).forEach {
                ics.sendSystemMessage(Component.literal(it))
            }
        }

        fun cprint(ics: CommandSourceStack, text: String, url: String) {
            val msg = Component.literal(FC.BRIGHT_GREY + text)
                .withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, url)) }
            ics.sendSystemMessage(msg)
        }

        fun printIndented(text: String, indent: Int): List<String> {
            val lineLength = 60 - indent * 2
            val list = mutableListOf<String>()
            val finalLine = text
                .split(' ')
                .fold("", { acc, next ->
                    if (acc.length + next.length > lineLength) {
                        list.add(acc)
                        next
                    } else {
                        "$acc $next"
                    }
                })
            list.add(finalLine)
            var mostRecentColor = '7' // default color is light gray
            val list2 = list.map  {
                line ->
                val lastLine = "§$mostRecentColor$line"
                if ("§" in line) {
                    mostRecentColor = line[line.lastIndexOf("§") + 1]
                }
                lastLine
            }
            val whitespace = (0 until indent * 2).joinToString("") { " " }
            return list2.map{"$whitespace$it"}
        }

        fun getArgBool(ics: CommandSourceStack, arg: String): Boolean? {
            val lowerArg = arg.lowercase()
            return if (lowerArg.isEmpty()) {
                cprint(ics, "Error: Empty argument.", indent = 1)
                null
            }else if (lowerArg == "0" || lowerArg == "false" || lowerArg == "no" || lowerArg == "disabled" || lowerArg == "disable") {
                false
            } else if (lowerArg == "1" || lowerArg == "true" || lowerArg == "yes" || lowerArg == "enabled" || lowerArg == "enable") {
                true
            } else {
                cprint(ics, "Error: Expected (true/false), got $arg",  indent = 1)
                null
            }
        }

        fun boolToStr(value: Boolean): String {
            return if (value) "Enabled" else "Disabled"
        }

    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("eln")
                .executes { processCommand(it.source, emptyList()) }
                .then(
                    Commands.argument("arguments", StringArgumentType.greedyString())
                        .suggests { context, builder -> suggest(context, builder) }
                        .executes { processCommand(it.source, split(StringArgumentType.getString(it, "arguments"))) }
                )
        )
    }

    private fun split(text: String): List<String> = text.trim().split(' ').filter { it.isNotEmpty() }

    private fun suggest(@Suppress("UNUSED_PARAMETER") context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val typed = builder.remaining
        val args = typed.split(' ')
        val options = addTabCompletionOptions(args)
        // Complete just the last word.
        val offset = builder.createOffset(builder.start + typed.lastIndexOf(' ') + 1)
        options.filter { it.startsWith(args.last(), ignoreCase = true) }.forEach { offset.suggest(it) }
        return offset.buildFuture()
    }

    private fun processCommand(ics: CommandSourceStack, args: List<String>): Int {
        if (args.isEmpty()) {
            cprint(ics,"${FC.DARK_CYAN}Electrical Age Console, run /eln ls for commands${FC.BRIGHT_GREY }")
            return 0
        }
        val permissions = determinePermissionsList(ics)
        val command = findConsoleCommand(args[0])
        if (command == null) {
            cprint(ics,"${FC.DARK_CYAN}Command not found, run /eln ls for commands${FC.BRIGHT_GREY }")
            return 0
        }
        cprint(ics, "${FC.DARK_CYAN}${ics.textName} $${FC.DARK_YELLOW} /eln ${args.joinToString(" ")}")
        val canRun = permissions.any { command.requiredPermission().contains(it) }
        if (canRun) {
            command.runCommand(ics, args.drop(1))
            return 1
        }
        cprint(ics, "${FC.DARK_CYAN}You do not have permission to run that command. " +
            "You need to have one of the following: ${command.requiredPermission()}${FC.BRIGHT_GREY }")
        return 0
    }

    private fun determinePermissionsList(ics: CommandSourceStack): List<UserPermission> {
        val permissions = mutableListOf<UserPermission>()
        val player = ics.player
        if (player == null) permissions.add(UserPermission.IS_CONSOLE)
        if (player?.isCreative == true) permissions.add(UserPermission.IS_CREATIVE)
        if (player == null || ics.hasPermission(2) || ics.server.isSingleplayer) permissions.add(UserPermission.IS_OPERATOR)
        return permissions
    }

    private fun addTabCompletionOptions(args: List<String>): List<String> {
        if (args.size <= 1) return ElnConsoleCommandList.map { it.name }
        val command = findConsoleCommand(args[0]) ?: return emptyList()
        return command.getTabCompletion(args.drop(1))
    }
}
