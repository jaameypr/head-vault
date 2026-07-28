plugins {
    id("net.fabricmc.fabric-loom")
}

version = "${property("mod.version")}+${sc.current.version}"
group = property("mod.group") as String
// NOTE: archivesName intentionally uses rootProject.name ("head-vault"), NOT mod.id ("headvault" —
// the fabric mod id used inside fabric.mod.json). This preserves the pre-Stonecutter jar filename
// (head-vault-<version>+<mc>.jar) for parity with the previous Groovy build.
base { archivesName = rootProject.name }

repositories {
    maven("https://maven.nucleoid.xyz/") { name = "Nucleoid" }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // 26.x is unobfuscated — intentionally NO mappings line.

    implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    val sgui = sc.properties.get<String>("deps.sgui")
    include("eu.pb4:sgui:$sgui")
    implementation("eu.pb4:sgui:$sgui")

    val perms = property("deps.permissions")
    include("me.lucko:fabric-permissions-api:$perms")
    implementation("me.lucko:fabric-permissions-api:$perms")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

tasks.processResources {
    val v = version.toString()
    val mc = sc.properties.get<String>("mod.mc_compat")
    inputs.property("version", v)
    inputs.property("minecraft", mc)
    filesMatching("fabric.mod.json") {
        expand("version" to v, "minecraft" to mc)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

// Collect this node's distributable jar into a predictable path for chiseled builds / CI.
tasks.register<Copy>("buildAndCollect") {
    group = "build"
    dependsOn("build")
    // 26.x is unobfuscated (disableObfuscation=true via the `net.fabricmc.fabric-loom` no-remap
    // marker plugin), so loom does not register `remapJar` — the plain `jar` task is the
    // distributable artifact.
    from(tasks.named("jar"))
    // `property(...)` inside this lambda would resolve against the Task (which has its own
    // `property()` method), not the Project — use `project.property(...)` explicitly.
    into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
}
