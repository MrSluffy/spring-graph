pluginManagement {
    repositories {
        gradlePluginPortal()
        // com.vanniktech.maven.publish releases after 0.13 are only published to Maven Central.
        mavenCentral()
    }
}

rootProject.name = "springgraph"

include("springgraph-processor")
include("springgraph-spring")
include("examples:sample-basic")
include("examples:pilot-boot")
