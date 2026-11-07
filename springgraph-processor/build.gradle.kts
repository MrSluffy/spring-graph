plugins {
    `java-library`
    `maven-publish`
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.toolchain.get()) }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    // The processor deliberately has no Spring dependency: stereotypes are matched by name.
    testImplementation(libs.spring.context)
    testImplementation(libs.compile.testing)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Processor runs inside the application's javac, so it must load on the oldest supported JDK.
tasks.compileJava { options.release = libs.versions.java.release.get().toInt() }
tasks.javadoc { (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet") }

tasks.test {
    useJUnitPlatform()
    // ./gradlew :springgraph-processor:test -PupdateSnapshots rewrites src/test/resources/snapshots/ (see SnapshotTest).
    if (providers.gradleProperty("updateSnapshots").isPresent) {
        systemProperty("updateSnapshots", "true")
        outputs.upToDateWhen { false }
    }
}

// Ship LICENSE and NOTICE inside every jar (main, sources, javadoc).
tasks.withType<Jar>().configureEach {
    into("META-INF") { from(rootProject.file("LICENSE"), rootProject.file("NOTICE")) }
}

publishing {
    publications {
        create<MavenPublication>("processor") {
            from(components["java"])
            pom {
                name = "SpringGraph processor"
                description = "Experimental build-time validation of a supported subset of Spring constructor injection."
                developers {
                    developer { name = "John Andrew Camu" }
                }
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution = "repo"
                    }
                }
            }
        }
    }
    // Only the local Maven repository (~/.m2) is configured: ./gradlew publishToMavenLocal
}
