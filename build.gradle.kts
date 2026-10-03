plugins {
    java
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    id("xyz.jpenilla.run-paper") version "3.1.0"
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
    description = "Distributed powered storage, searchable terminals and wireless Vault access for Rebar."

    commands {
        register("vaultworks") {
            description = "VaultWorks administration and diagnostics."
            usage = "/<command> doctor | recovery | testpower"
            permission = "vaultworks.admin"
        }
    }

    permissions {
        register("vaultworks.admin") {
            description = "Allows VaultWorks administrative diagnostics."
            default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP
        }
    }
}

tasks.runServer {
    providers.gradleProperty("rebar.serverJar").orNull?.let { pluginJars.from(file(it)) }
    minecraftVersion(minecraftVersion)
    maxHeapSize = "1G"
    jvmArgs("-Dcom.mojang.eula.agree=true", "-Dterminal.jline=false", "-Dterminal.ansi=false")
}

// Opt-in real-server regression harness; never included in the player JAR.
val integrationTest = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += compileClasspath
}
val integrationTestJar = tasks.register<Jar>("integrationTestJar") {
    archiveBaseName.set("VaultWorksRuntimeTests")
    from(integrationTest.output)
}
tasks.runServer {
    if (providers.gradleProperty("vaultworks.runtimeTests").isPresent) {
        dependsOn(integrationTestJar)
        pluginJars.from(integrationTestJar.flatMap { it.archiveFile })
    }
}
