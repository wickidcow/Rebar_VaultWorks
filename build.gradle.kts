plugins {
    java
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
}
group = "io.github.wickidcow"
version = providers.gradleProperty("version").get()
repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/") { content { includeGroup("io.github.pylonmc") } }
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://jitpack.io")
}
val rebarVersion = providers.gradleProperty("rebar.version").get()
val minecraftVersion = providers.gradleProperty("minecraft.version").get()
dependencies {
    compileOnly("io.github.pylonmc:rebar:$rebarVersion")
    compileOnly("io.papermc.paper:paper-api:$minecraftVersion.build.+")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
tasks.test { useJUnitPlatform() }
tasks.jar { archiveBaseName.set("Rebar_VaultWorks") }
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
bukkit {
    name = "VaultWorks"
    main = "io.github.wickidcow.vaultworks.VaultWorks"
    version = project.version.toString()
    apiVersion = minecraftVersion
    depend = listOf("Rebar")
    load = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.PluginLoadOrder.STARTUP
    authors = listOf("wickidcow")
    description = "Physical vault cells with native Rebar electric cargo access."
}
