pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.5"
    id("dev.kikugie.loom-back-compat") version "0.3"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // Two nodes: current 26.2 plus the 26.1.x line kept for players still on it. Add nodes
        // here plus [version] sections in stonecutter.properties.toml, like Shulker Pocket.
        versions(
            "26.2",     // covers 26.2
            "26.1.2",   // covers 26.1, 26.1.1, 26.1.2
        )
        vcsVersion = "26.2"
    }
}

rootProject.name = "fallow"
