plugins {
    `java-library`
    `maven-publish`
    id("net.neoforged.moddev") version "2.0.134"
    idea
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

tasks.named<Wrapper>("wrapper") {
    distributionType = Wrapper.DistributionType.BIN
}

val modId = property("mod_id") as String
val modVersion = property("mod_version") as String
val modGroupId = property("mod_group_id") as String
val modName = property("mod_name") as String
val modLicense = property("mod_license") as String
val minecraftVersion = property("minecraft_version") as String
val minecraftVersionRange = property("minecraft_version_range") as String
val neoVersion = property("neo_version") as String
val loaderVersionRange = property("loader_version_range") as String
val parchmentMappingsVersion = property("parchment_mappings_version") as String
val parchmentMinecraftVersion = property("parchment_minecraft_version") as String

version = modVersion
group = modGroupId

repositories {
    mavenCentral()
    maven {
        name = "Kotlin for Forge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
    }
    maven { url = uri("https://jitpack.io") }
}

base {
    archivesName.set(modId)
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

neoForge {
    version = neoVersion

    parchment {
        mappingsVersion = parchmentMappingsVersion
        minecraftVersion = parchmentMinecraftVersion
    }

    runs {
        register("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }

        register("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }

        register("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }

        register("data") {
            data()
            programArguments.addAll(
                "--mod", modId,
                "--all",
                "--output", file("src/generated/resources/").absolutePath,
                "--existing", file("src/main/resources/").absolutePath
            )
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets.main.get().resources.srcDir("src/generated/resources")

val localRuntime: Configuration by configurations.creating
configurations.runtimeClasspath.get().extendsFrom(localRuntime)

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("thedarkcolour:kotlinforforge-neoforge:5.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0-RC")
}

val generateModMetadata by tasks.registering(ProcessResources::class) {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersionRange,
        "neo_version" to neoVersion,
        "loader_version_range" to loaderVersionRange,
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_license" to modLicense,
        "mod_version" to modVersion,
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}

sourceSets.main.get().resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("file://${project.projectDir}/repo")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

kotlin {
    jvmToolchain(21)
}
