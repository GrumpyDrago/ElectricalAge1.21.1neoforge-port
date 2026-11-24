# Agents

If you learn things about making the Gradle build work in the sandbox, document it under the Sandbox section.

# Various Notes

* This is a Minecraft NeoForge 1.21.1 mod called Electrical Age (Eln), ported from Forge 1.7.10. It uses ModDevGradle and Kotlin for Forge. `docs/neoforge-port.md` explains how the code is laid out.
* We are aiming to move to 100% Kotlin. Where possible, use Kotlin for new code.
* Java source files belong in the `src/main/java` directory. Kotlin source files belong in the `src/main/kotlin` directory.
* Do not update the Gradle configuration or wrapper without asking first.
* If you create documentation, please add it do the `docs` directory.
* Do not move the `id` or `subId` of existing registered items or blocks unless explicitly asked. Those values are world-save sensitive and changing unrelated ones breaks existing saves.
* **i18n (internationalization) workflow**:
  - Wrap all user-facing strings with `tr()` and item/block names with `TR_NAME()`.
  - Avoid dynamically constructing strings; keep them as complete phrases so translators can work with them.
  - Use positional parameters (`%1$`, `%2$`, …) for runtime values instead of string concatenation or direct variable embedding — this allows translators to reorder arguments for their language.
  - After adding new items or translatable strings, run `./gradlew generateLangFiles` to update the language files. Do not manually edit language files unless fixing an existing translation.
* After adding an item or block, run `./gradlew runData` so that it gets a model, loot table and tags in `src/generated/resources`.
* Check changes with `./gradlew test runGameTestServer`, and rendering or GUI changes with `./gradlew runClientSmokeTest` (opens a window for a few minutes, and leaves screenshots in `run/screenshots`).

# Sandbox

* When `./gradlew` fails inside the sandbox claiming it “Could not determine a usable wildcard IP”, rerun with `GRADLE_USER_HOME=$PWD/.gradle` and, if needed, request escalation—the build otherwise succeeds once it can write its cache and bind to an IP.
* Gradle needs JDK 21 (`JAVA_HOME`). On Windows, `winget install EclipseAdoptium.Temurin.21.JDK` provides one.
* `./gradlew runServer` needs the Minecraft EULA to be accepted by the user. `runGameTestServer` does not.
* Unit tests run inside a NeoForge test environment whose working directory is below `build`; use the `eln.projectDir` system property to find project files.
