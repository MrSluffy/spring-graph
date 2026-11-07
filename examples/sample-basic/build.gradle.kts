plugins {
    java
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.toolchain.get()) }
}

dependencies {
    implementation(libs.spring.context)
    compileOnly(project(":springgraph-processor"))
    annotationProcessor(project(":springgraph-processor"))
}

tasks.withType<JavaCompile>().configureEach {
    // Opt-in: advisory first, switch to strict once runtime boundaries are declared.
    options.compilerArgs.add("-Aspringgraph.mode=strict")
}
