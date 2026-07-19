plugins {
    // Picks the remapping loom for obfuscated MC (<26.1) and the no-remap loom for 26.1+,
    // and exposes a uniform modImplementation / applyMojangMappings / modJar API for both.
    id("dev.kikugie.loom-back-compat")
    id("me.modmuss50.mod-publish-plugin") version "1.1.0"
}

// Per Stonecutter: do NOT set group here.
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "fallow"

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1"   -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else                          -> JavaVersion.VERSION_17
}

// Modrinth game versions this jar covers (used for publishing).
val compatibleVersions: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
    ?.asList().orEmpty().map { it.toString() }

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://maven.terraformersmc.com/") { name = "Terraformers" }
}

loom {
    // Separate `main` (common + server) and `client` source sets.
    splitEnvironmentSourceSets()

    mods {
        create("fallow") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
        // Dev-only companion mod: the test source set carries the @GameTest classes and its own
        // fabric.mod.json (src/test/resources). Registering it here gives the runGametest server a
        // distinct mod whose fabric-gametest entrypoint is loaded from test classes. The published
        // jar never sees it (different source set), so runServer no longer crashes on a missing
        // gametest class.
        create("fallow_gametest") {
            sourceSet(sourceSets["test"])
        }
    }

    runs {
        // `./gradlew runTestWorld` boots the client straight into the void test world
        // (saves/fallow-test: labeled platforms per feature).
        create("testWorld") {
            client()
            configName = "Fallow Test World"
            programArgs("--quickPlaySingleplayer", "fallow-test")
        }

        // `./gradlew runGametest` boots a headless dedicated server, runs every @GameTest
        // (from the fabric-gametest entrypoint), writes a JUnit report, then exits with a
        // pass/fail code. Sourced from src/test so the tests stay out of the shipped jar.
        create("gametest") {
            server()
            configName = "Fallow Game Test"
            source(sourceSets["test"])
            vmArg("-Dfabric-api.gametest")
            vmArg("-Dfabric-api.gametest.report-file=" +
                "${layout.buildDirectory.get()}/gametest/junit.xml")
            runDir("build/gametest")
        }

        // `./gradlew runClientGametest` launches a real client, runs every fabric-client-gametest
        // entrypoint (the visual CropVisualTest), and exits. Sourced from src/test so the test
        // classes stay out of the shipped jar. Screenshots land in build/clientGametest/screenshots.
        create("clientGametest") {
            client()
            configName = "Fallow Client Game Test"
            source(sourceSets["test"])
            vmArg("-Dfabric.client.gametest")
            runDir("build/clientGametest")
        }
    }
}

val fabricApiVersion: String = sc.properties["deps.fabric_api"]
val modmenuVersion: String = sc.properties["deps.modmenu"]

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // No-op on unobfuscated 26.1; applies Mojang mappings on remapped older versions.
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    modImplementation("com.terraformersmc:modmenu:$modmenuVersion")

    testImplementation("net.fabricmc:fabric-loader-junit:${property("deps.fabric_loader")}")
}

tasks.test {
    useJUnitPlatform()
}

val mcCompat: String = sc.properties["mod.mc_compat"]
val javaMajor = requiredJava.majorVersion

// fabric.mod.json lives in the main source set; filesMatching is a no-op on tasks lacking the file.
tasks.withType<org.gradle.language.jvm.tasks.ProcessResources>().configureEach {
    inputs.property("version", version)
    inputs.property("minecraft", mcCompat)
    inputs.property("java", javaMajor)

    filesMatching("fabric.mod.json") {
        expand(mapOf(
            "version" to version,
            "minecraft" to mcCompat,
            "java" to javaMajor,
        ))
    }
    filesMatching("*.mixins.json") {
        expand(mapOf("java" to "JAVA_$javaMajor"))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = requiredJava.majorVersion.toInt()
}

// GameTest environment definitions live in main resources so the runGametest server loads them as
// the mod's data, but they are test-only scaffolding: strip them from the published jar. The dev
// runs read resources from build/resources/main directly, so they keep working.
tasks.jar {
    exclude("data/fallow/test_environment/**")
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

// Modrinth publishing via the Mod Publish Plugin. Each node uploads one version with its own
// gameVersions; fan out across nodes with chiseledPublishMods (see stonecutter.gradle.kts).
// Without MODRINTH_TOKEN (local runs) it dry-runs: the config is validated and the jar built, but
// nothing uploads. The token comes from .env locally or the CI secret. See docs/promotion.md.
publishMods {
    file = loomx.modJar.flatMap { it.archiveFile }
    version = project.version.toString()
    type = me.modmuss50.mpp.ReleaseType.STABLE
    displayName = "Fallow ${project.version}"
    modLoaders.add("fabric")
    changelog = "Fallow ${property("mod.version")} for Minecraft $mcCompat. " +
        "See the project page for the full feature list."
    dryRun = !providers.environmentVariable("MODRINTH_TOKEN").isPresent

    modrinth {
        projectId = property("mod.modrinth_id") as String
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        minecraftVersions.addAll(compatibleVersions)
        requires("fabric-api")
        optional("modmenu")
    }
}
