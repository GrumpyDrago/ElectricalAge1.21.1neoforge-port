package mods.eln.i18n

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

internal object LanguageFileGenerator {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /** Keys Minecraft itself derives from registry names; they do not appear in the source code. */
    private val registryKey = Regex("^(item|block|entity|fluid_type|death\\.attack|key)\\.eln.*|^ElectricalAge$|^Wrench$|^Wiki$")

    @Throws(IOException::class)
    @JvmStatic
    fun updateFile(
        file: File,
        strings: Map<String, Set<TranslationItem>>,
        existingTranslations: Map<String, String>?
    ) {
        val json = JsonObject()
        for (items in strings.values) {
            for (item in items) {
                val existing = existingTranslations?.get(item.key)
                json.addProperty(
                    item.key,
                    existing ?: I18N.resolveUnicodeEscapes(item.text).replace("\\\"", "\"").replace("\\n", "\n")
                )
            }
        }
        existingTranslations?.forEach { (key, value) -> if (registryKey.matches(key)) json.addProperty(key, value) }
        file.writeText(gson.toJson(json) + "\n", StandardCharsets.UTF_8)
    }
}
