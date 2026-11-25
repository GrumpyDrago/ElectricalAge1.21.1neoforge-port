package mods.eln.client

import com.google.gson.JsonParser
import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.misc.FC
import mods.eln.misc.Version
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ReceivingLevelScreen
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import org.semver4j.Semver
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Compares the version of the mod with the newest release in its GitHub repository, and tells the player the result, every
 * time a world is loaded or a server joined.
 */
object VersionCheckerHandler {
    private const val REPOSITORY = "GrumpyDrago/ElectricalAge1.21.1neoforge-port"
    private const val RELEASES_API = "https://api.github.com/repos/$REPOSITORY/releases"
    private const val RELEASES_PAGE = "https://github.com/$REPOSITORY/releases"
    private const val TIMEOUT_MS = 10000

    internal sealed interface Result {
        data class NewVersion(val version: String, val page: String) : Result
        data object UpToDate : Result
        data object UnderTest : Result
        data object NothingReleased : Result
        data object Failed : Result
    }

    private class Check {
        @Volatile
        var result: Result? = null
    }

    /** The check whose result the player has not seen yet. */
    private var check: Check? = null

    /** From now on, checks whenever a world is loaded or a server joined. */
    fun start() = NeoForge.EVENT_BUS.register(this)

    @SubscribeEvent
    fun onJoin(@Suppress("UNUSED_PARAMETER") event: ClientPlayerNetworkEvent.LoggingIn) {
        val check = Check()
        this.check = check
        Thread({
            check.result = try {
                evaluate(download(), Version.SEMVER)
            } catch (e: Exception) {
                Eln.logger.warn("Unable to check the latest available version: {}", e.toString())
                Result.Failed
            }
        }, "Eln version check").apply { isDaemon = true }.start()
    }

    @SubscribeEvent
    fun onLeave(@Suppress("UNUSED_PARAMETER") event: ClientPlayerNetworkEvent.LoggingOut) {
        check = null
    }

    private fun download(): String {
        val connection = URI(RELEASES_API).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "ElectricalAge")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP error $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * [releases] is the list of releases from GitHub, [current] the version in use. Pre-releases only matter to those
     * already using one. Releases whose tag is not a version, with or without a leading "v", are ignored.
     */
    internal fun evaluate(releases: String, current: Semver): Result {
        val candidates = JsonParser.parseString(releases).asJsonArray.map { it.asJsonObject }
            .filter { it.get("draft")?.asBoolean != true }
            .mapNotNull { release ->
                Semver.parse(release.get("tag_name").asString.removePrefix("v"))?.let { it to release }
            }
            .filter { (version, release) ->
                val preRelease = release.get("prerelease")?.asBoolean == true || version.preRelease.isNotEmpty()
                !preRelease || current.preRelease.isNotEmpty()
            }
        val (newest, release) = candidates.maxByOrNull { it.first } ?: return Result.NothingReleased
        return when {
            newest.isGreaterThan(current) -> Result.NewVersion(newest.version, release.get("html_url")?.asString ?: RELEASES_PAGE)
            newest.isLowerThan(current) -> Result.UnderTest
            else -> Result.UpToDate
        }
    }

    private fun message(result: Result): String? = when (result) {
        is Result.NewVersion -> FC.GREEN + "> " + tr("New version available: %1$ - get it at %2$", result.version, result.page)
        Result.UpToDate -> "> " + tr("No update available (latest version)")
        Result.UnderTest -> FC.RED + "> " + tr("Warning: this is a version under test!")
        Result.Failed -> FC.RED + "> " + tr("Unable to check the latest available version.")
        Result.NothingReleased -> null
    }

    @SubscribeEvent
    fun tick(@Suppress("UNUSED_PARAMETER") event: ClientTickEvent.Post) {
        val result = check?.result ?: return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        // Messages fade, so wait until there is something to see.
        if (mc.level == null || mc.screen is ReceivingLevelScreen) return
        check = null

        val message = message(result) ?: return
        player.sendSystemMessage(Component.literal("Electrical Age"))
        player.sendSystemMessage(Component.literal(message))
    }
}
