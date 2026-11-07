plugins {
    java
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.toolchain.get()) }
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.h2)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // SpringGraph is enabled with -PwithSpringGraph=false to measure the baseline build.
    if (providers.gradleProperty("withSpringGraph").getOrElse("true") == "true") {
        compileOnly(project(":springgraph-processor"))
        annotationProcessor(project(":springgraph-processor"))
    }
}

// Boundaries: types Spring supplies at runtime (see docs/evaluation/developer-experience.md)
val defaultBoundaries = "java.time.Clock,com.fasterxml.jackson.databind.ObjectMapper"

tasks.withType<JavaCompile>().configureEach {
    if (providers.gradleProperty("withSpringGraph").getOrElse("true") == "true") {
        val mode = providers.gradleProperty("springgraphMode").getOrElse("strict")
        val boundaries = providers.gradleProperty("springgraphBoundaries").getOrElse(defaultBoundaries)
        options.compilerArgs.addAll(listOf("-Aspringgraph.mode=$mode", "-Aspringgraph.runtimeBoundaries=$boundaries"))
    }
}

tasks.test { useJUnitPlatform() }
