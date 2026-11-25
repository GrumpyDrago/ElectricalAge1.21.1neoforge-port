plugins {
    `java-library`
    kotlin("jvm") version "2.2.21"
    id("net.neoforged.moddev") version "2.0.148"
}

fun prop(name: String): String = providers.gradleProperty(name).get()

val modId = prop("mod_id")
version = prop("mod_version")
group = prop("mod_group")

base { archivesName = "electricalage-${prop("minecraft_version")}" }

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
kotlin { jvmToolchain(21) }

repositories {
    mavenCentral()
    maven("https://thedarkcolour.github.io/KotlinForForge/") {
        name = "Kotlin for Forge"
        content { includeGroup("thedarkcolour") }
    }
    maven("https://maven.squiddev.cc") {
        name = "CC: Tweaked"
        content { includeGroup("cc.tweaked") }
    }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
}

neoForge {
    version = prop("neo_version")

    parchment {
        minecraftVersion = prop("parchment_minecraft_version")
        mappingsVersion = prop("parchment_mappings_version")
    }

    runs {
        register("client") {
            client()
        }
        register("server") {
            server()
            programArgument("--nogui")
        }
        register("gameTestServer") {
            type = "gameTestServer"
        }
        register("clientSmokeTest") {
            client()
            systemProperty("eln.clientSmokeTest", "true")
            programArguments.addAll("--width", "1280", "--height", "720")
        }
        register("data") {
            data()
            programArguments.addAll(
                "--mod", modId, "--all",
                "--output", file("src/generated/resources/").absolutePath,
                "--existing", file("src/main/resources/").absolutePath
            )
        }
        configureEach {
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            logLevel = org.slf4j.event.Level.INFO
        }
    }

    mods {
        register(modId) { sourceSet(sourceSets.main.get()) }
    }

    unitTest {
        enable()
        testedMod = mods[modId]
    }
}

sourceSets.main.get().resources {
    srcDir("src/generated/resources")
    exclude(".cache")
}

dependencies {
    implementation("thedarkcolour:kotlinforforge-neoforge:${prop("kff_version")}")

    // Pure-Java libraries that Minecraft does not ship; embedded into the mod jar.
    listOf(
        "org.apache.commons:commons-math3:3.6.1" to "[3.6.1,4.0)",
        "org.apache.commons:commons-numbers-core:1.2" to "[1.2,2.0)",
        "org.semver4j:semver4j:4.3.0" to "[4.3.0,5.0)",
    ).forEach { (dep, range) ->
        implementation(dep)
        jarJar(dep) {
            version {
                strictly(range)
                prefer(dep.substringAfterLast(':'))
            }
        }
        "additionalRuntimeClasspath"(dep)
    }
    compileOnly("com.fazecast:jSerialComm:2.6.2")

    // Optional integrations
    compileOnly("cc.tweaked:cc-tweaked-1.21.1-forge-api:1.122.0")
    compileOnly("maven.modrinth:jade:15.10.6+neoforge")

    // i18n AST string extraction (build-time only, not in published jar)
    compileOnly("com.github.javaparser:javaparser-core:3.26.3")
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.21")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation(kotlin("test-junit"))
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val i18nTool: Configuration by configurations.creating
dependencies {
    i18nTool("com.github.javaparser:javaparser-core:3.26.3")
    i18nTool("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.21")
}

// The blender/gimp sources live beside the exported models; they must not end up in the jar.
tasks.processResources {
    exclude("**/_common", "**/_templates", "**/export_*.png", "**/*.blend*", "**/temp/**/*", "**/model-to-be-integrated/**/*")
}

val generateModMetadata by tasks.registering(ProcessResources::class) {
    val replaceProperties = listOf(
        "minecraft_version", "minecraft_version_range", "neo_version", "neo_version_range",
        "loader_version_range", "kff_version", "mod_id", "mod_name", "mod_license", "mod_version"
    ).associateWith { prop(it) }
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}
sourceSets.main.get().resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)

// Provides mods.eln.Tags.VERSION
val generateTags by tasks.registering {
    val out = layout.buildDirectory.dir("generated/sources/tags")
    val v = project.version.toString()
    inputs.property("version", v)
    outputs.dir(out)
    doLast {
        val f = out.get().file("mods/eln/Tags.java").asFile
        f.parentFile.mkdirs()
        f.writeText(
            "package mods.eln;\n\npublic final class Tags {\n    private Tags() {}\n    public static final String VERSION = \"$v\";\n}\n"
        )
    }
}
sourceSets.main.get().java.srcDir(generateTags)

// Test code lives beside the mod so the game can run it, but neither it nor the artwork sources ship.
tasks.jar {
    exclude("mods/eln/gametest/**", "mods/eln/datagen/**", "data/eln/structure/**")
    exclude("**/*.xcf", "**/*.ai", "**/*.aseprite", "**/*.broke", "**/*.blend", "**/*.blend1", "assets/eln/unused-models/**")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "20000"))
}

// Generates language files from tr()/TR_NAME() calls in the source code.
tasks.register<JavaExec>("generateLangFiles") {
    group = "build"
    description = "Generates language files from source code"
    mainClass = "mods.eln.i18n.LanguageFileUpdater"
    args("src/main", "src/main/resources/assets/eln/lang")
    classpath = sourceSets.main.get().runtimeClasspath + i18nTool
    dependsOn("classes")
}

tasks.withType<Test>().configureEach {
    // Tests run in a game directory, not here.
    systemProperty("eln.projectDir", projectDir.absolutePath)
}

tasks.test {
    useJUnitPlatform()
    exclude("**/*BenchmarkTest.*", "**/*ProfilingTest.*")
}

tasks.register<Test>("benchmarkTest") {
    description = "Runs benchmark and profiling tests separately from the regular correctness test suite."
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*BenchmarkTest.*", "**/*ProfilingTest.*")
    shouldRunAfter(tasks.test)

    // ModDevGradle only gives the regular test task the NeoForge test environment, so take it over from there.
    val regular = tasks.test.get()
    dependsOn("prepareNeoForgeTestFiles")
    workingDir = regular.workingDir
    systemProperties(regular.systemProperties)
    jvmArgs(regular.jvmArgs.orEmpty())
    jvmArgumentProviders.addAll(regular.jvmArgumentProviders)
}

// NeoForge 21.1 applies its patches with this version of binarypatcher, while the resolved dependencies have a newer one.
// Without it in the artifact manifest, every setup warns and downloads it separately.
val binaryPatcher: Configuration by configurations.creating { isTransitive = false }
dependencies {
    binaryPatcher("net.neoforged.installertools:binarypatcher:2.1.2:fatjar")
}
tasks.withType<net.neoforged.nfrtgradle.CreateMinecraftArtifacts>().configureEach {
    addArtifactsToManifest(binaryPatcher)
}
