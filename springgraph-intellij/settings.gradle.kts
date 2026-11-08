// A separate Gradle build, so the main build and CI never download an IDE. Run it with the root wrapper:
//   ./gradlew -p springgraph-intellij build       (Windows: .\gradlew.bat -p springgraph-intellij build)
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}

rootProject.name = "springgraph-intellij"
