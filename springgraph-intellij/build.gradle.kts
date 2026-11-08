import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.security.MessageDigest
import java.util.HexFormat

plugins {
    java
    alias(libs.plugins.intellij.platform)
}

group = "io.github.mrsluffy"
version = providers.gradleProperty("pluginVersion").get()

java {
    toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.toolchain.get()) }
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

// Build against an installed IDE instead of downloading one (about 1.5 GB): set
// springgraph.intellijLocalPath=<IDE install directory> in ~/.gradle/gradle.properties, or pass it with -P.
// It should be on the platformVersion branch, so the plugin is not compiled against newer APIs.
val localIde = providers.gradleProperty("springgraph.intellijLocalPath")

dependencies {
    intellijPlatform {
        if (localIde.isPresent) {
            local(localIde)
        } else {
            intellijIdea(providers.gradleProperty("platformVersion"))
        }
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
    testImplementation(libs.junit4)
    testImplementation(libs.opentest4j)
}

intellijPlatform {
    // Indexing the one small settings page for Settings search is not worth starting a whole IDE in every build.
    buildSearchableOptions = false
    // No GUI forms or @NotNull instrumentation needed.
    instrumentCode = false
    // ./gradlew -p springgraph-intellij verifyPlugin: binary compatibility with the target IDE (JetBrains Plugin Verifier).
    pluginVerification {
        ides {
            if (localIde.isPresent) {
                local(file(localIde))
            } else {
                create(IntelliJPlatformType.IntellijIdea, providers.gradleProperty("platformVersion"))
            }
        }
    }
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null } // open-ended: also loads in newer IDEs
        }
    }
}

// --- Bundled processor (IDE analysis) ---
// The exact published jar, never the in-repo build: pinned version, non-transitive (it has no dependencies), and its
// SHA-256 checked before it goes into the plugin. The plugin extracts it at runtime and never downloads anything.
val bundledProcessorVersion = providers.gradleProperty("bundledProcessorVersion")
val bundledProcessorSha256 = providers.gradleProperty("bundledProcessorSha256")
val bundledProcessor: Configuration = configurations.create("bundledProcessor") {
    isTransitive = false
    isCanBeConsumed = false
}
dependencies {
    bundledProcessor(bundledProcessorVersion.map { "io.github.mrsluffy:springgraph-processor:$it" })
}

val bundleProcessor = tasks.register("bundleProcessor") {
    description = "Verifies the pinned processor jar and stages it as a plugin resource."
    val jars = bundledProcessor
    val version = bundledProcessorVersion
    val expected = bundledProcessorSha256
    val output = layout.buildDirectory.dir("generated/bundled-processor")
    inputs.files(jars)
    inputs.property("version", version)
    inputs.property("sha256", expected)
    outputs.dir(output)
    doLast {
        val jar = jars.singleFile
        val actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar.readBytes()))
        check(actual == expected.get()) {
            "${jar.name}: SHA-256 is $actual, expected ${expected.get()} (bundledProcessorSha256 in gradle.properties)"
        }
        val dir = output.get().asFile.resolve("springgraph")
        dir.deleteRecursively()
        dir.mkdirs()
        jar.copyTo(dir.resolve("springgraph-processor.jar"))
        dir.resolve("processor.properties").writeText("version=${version.get()}\nsha256=$actual\n")
    }
}
sourceSets.main { resources.srcDir(bundleProcessor) }
tasks.test { systemProperty("springgraph.bundledProcessorVersion", bundledProcessorVersion.get()) }

// The plugin parses the processor's JSON report; test against the processor's own snapshot, so a format change on
// either side fails here.
tasks.processTestResources {
    from("../springgraph-processor/src/test/resources/snapshots/full-advisory.json") { into("processor") }
}

// Ship LICENSE and NOTICE in the plugin jar.
tasks.jar { into("META-INF") { from(rootDir.resolve("../LICENSE"), rootDir.resolve("../NOTICE")) } }
