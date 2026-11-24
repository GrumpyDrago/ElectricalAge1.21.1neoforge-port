package mods.eln.misc

import mods.eln.Eln
import net.neoforged.fml.ModList
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.extension

/** Access to the mod's own files below assets/eln, on either side and before resource packs are loaded. */
object ElnAssets {
    private fun root(): Path? {
        val file = try {
            ModList.get()?.getModFileById(Eln.MODID)?.file
        } catch (e: Exception) {
            null
        }
        return file?.findResource("assets", "eln")
    }

    @JvmStatic
    @Throws(java.io.IOException::class)
    fun open(path: String): InputStream? {
        val normal = path.lowercase(Locale.ROOT).replace(' ', '_')
        val root = root()
        if (root != null) {
            val file = root.resolve(normal)
            return if (Files.exists(file)) Files.newInputStream(file) else null
        }
        return ElnAssets::class.java.classLoader.getResourceAsStream("assets/eln/$normal")
    }

    @JvmStatic
    fun exists(path: String): Boolean = open(path)?.use { true } ?: false

    /** Paths, relative to assets/eln, of all files with this extension below [folder]. */
    @JvmStatic
    fun list(folder: String, extension: String): List<String> {
        val root = root() ?: return emptyList()
        val base = root.resolve(folder)
        if (!Files.isDirectory(base)) return emptyList()
        Files.walk(base).use { stream ->
            return stream.filter { it.extension == extension }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .sorted()
                .toList()
        }
    }
}
