plugins {
    id("dev.kikugie.stonecutter")
}

// Active = the version your IDE compiles and the state src/ is left in.
stonecutter active "26.1.2"

stonecutter parameters {
    replacements {
        // No downgrade rules yet: single 26.1.x node. When backporting, centralize renamed-symbol
        // replacements here (see Shulker Pocket's stonecutter.gradle.kts for the pattern).
    }
}

// Fan a task out across every version node (one node today; matches Shulker Pocket CI usage).
// chiseledBuild compiles + tests all nodes (CI); chiseledPublishMods uploads every node's jar to
// Modrinth (the tag publish job).
tasks.register("chiseledBuild") {
    group = "build"
    description = "Compiles and tests every version node."
    dependsOn(stonecutter.tasks.named("build"))
}

tasks.register("chiseledPublishMods") {
    group = "publishing"
    description = "Publishes every version node's jar to Modrinth."
    dependsOn(stonecutter.tasks.named("publishMods"))
}

// Upload versions in a defined order so they list sensibly on Modrinth.
stonecutter tasks {
    order("publishModrinth")
}
