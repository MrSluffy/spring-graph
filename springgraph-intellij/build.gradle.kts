import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

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
    // No settings pages, so there is nothing to index; this task also starts a whole IDE.
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

// The plugin parses the processor's JSON report; test against the processor's own snapshot, so a format change on
// either side fails here.
tasks.processTestResources {
    from("../springgraph-processor/src/test/resources/snapshots/full-advisory.json") { into("processor") }
}

// Ship LICENSE and NOTICE in the plugin jar.
tasks.jar { into("META-INF") { from(rootDir.resolve("../LICENSE"), rootDir.resolve("../NOTICE")) } }
