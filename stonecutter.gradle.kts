plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2"

// Stonecutter 0.9.6 has no built-in "chiseled"/registerChiseled API (verified: no such type
// exists in the plugin jar). Build our own aggregator that depends on `buildAndCollect` across
// every registered version subproject. With a single node today this just runs that one node's
// task, but it becomes the multi-version "build everything" entry point once 26.2 is added.
tasks.register("chiseledBuild") {
    group = "project"
    description = "Runs buildAndCollect for every Stonecutter version and collects the jars."
    dependsOn(stonecutter.tasks.named("buildAndCollect").map { it.values })
}
