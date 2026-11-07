plugins {
    `java-library`
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.toolchain.get()) }
}

dependencies {
    api(libs.spring.context)
    testImplementation(libs.jakarta.annotation.api)
    // Spring-agreement tests: compile fixtures with the processor, then start them in a real context.
    testImplementation(project(":springgraph-processor"))
    testImplementation(libs.compile.testing)
    testImplementation(libs.lombok)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
