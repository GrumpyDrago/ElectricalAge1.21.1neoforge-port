@file:JvmName("LanguageFileUpdater")

package mods.eln.i18n

import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

fun main(args: Array<String>) {
    try {
        if (args.size != 2)
            System.exit(1)

        val srcFolder = File(args[0])
        val languageFileOrFolder = File(args[1])

        // Check if the source folder is present.
        if (!srcFolder.isDirectory)
            System.exit(1)

        // Get the strings to translate from the actual source code.
        val stringsToTranslate = SourceCodeParser.parseSourceFolder(srcFolder).toMutableMap()

        // If a single file is passed to the main method, we just update that particular file.
        if (languageFileOrFolder.isFile) {
            updateLanguageFile(languageFileOrFolder, stringsToTranslate)
        } else if (languageFileOrFolder.isDirectory) {
            val files = languageFileOrFolder.listFiles() ?: return
            for (file in files) {
                if (file.name.endsWith(".json") && !file.name.startsWith("_")) {
                    updateLanguageFile(file, stringsToTranslate)
                }
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
        Runtime.getRuntime().exit(1)
    }
}

@Throws(IOException::class)
private fun updateLanguageFile(languageFile: File, stringsToTranslate: Map<String, Set<TranslationItem>>) {
    val existingTranslations = parseLangFile(languageFile)

    // Update the existing language file.
    LanguageFileGenerator.updateFile(languageFile, stringsToTranslate, existingTranslations)
}

/** Reads a language file into a key-value map. */
@Throws(IOException::class)
private fun parseLangFile(file: File): Map<String, String> {
    val json = JsonParser.parseString(file.readText(StandardCharsets.UTF_8)).asJsonObject
    return json.entrySet().associate { it.key to it.value.asString }
}
