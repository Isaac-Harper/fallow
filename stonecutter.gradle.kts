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
tasks.register("chiseledBuild") {
    group = "build"
    description = "Compiles and tests every version node."
    dependsOn(stonecutter.tasks.named("build"))
}
