plugins {
    `java-library`
    // Configures the publication (with sources and javadoc jars), signing, and the Maven Central upload.
    alias(libs.plugins.maven.publish)
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.toolchain.get()) }
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
tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        // Internal packages are documented for contributors, but shown separately from the API.
        group("API", listOf("io.springgraph.processor"))
        group("Internal (no compatibility guarantee)", listOf("io.springgraph.processor.internal*"))
    }
}

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

// Publishing targets (see docs/releasing.md):
//   ./gradlew publishToMavenLocal                                   local testing, no credentials or signing needed
//   ./gradlew publishAllPublicationsToGitHubPackagesRepository      GitHub Packages
//   ./gradlew publishAndReleaseToMavenCentral                       Maven Central (signed)
mavenPublishing {
    coordinates(project.group.toString(), "springgraph-processor", project.version.toString())
    publishToMavenCentral()

    // Maven Central requires signatures. Sign whenever a key is supplied (ORG_GRADLE_PROJECT_signingInMemoryKey),
    // so local builds and publishToMavenLocal keep working without one.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    pom {
        name = "SpringGraph processor"
        description = "Experimental build-time validation of a supported subset of Spring constructor injection."
        inceptionYear = "2026"
        url = "https://github.com/MrSluffy/spring-graph"
        licenses {
            license {
                name = "Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "MrSluffy"
                name = "John Andrew Camu"
                url = "https://github.com/MrSluffy"
            }
        }
        scm {
            url = "https://github.com/MrSluffy/spring-graph"
            connection = "scm:git:https://github.com/MrSluffy/spring-graph.git"
            developerConnection = "scm:git:ssh://git@github.com/MrSluffy/spring-graph.git"
        }
        issueManagement {
            system = "GitHub"
            url = "https://github.com/MrSluffy/spring-graph/issues"
        }
    }
}

// The staging repository must only ever contain the version being released: clear leftovers from earlier versions.
val stagingRepo = layout.buildDirectory.dir("staging-repo")
tasks.withType<PublishToMavenRepository>().configureEach {
    if (name.endsWith("ToStagingRepository")) {
        doFirst { stagingRepo.get().asFile.deleteRecursively() }
    }
}

publishing {
    repositories {
        // Release staging: a plain Maven-layout directory, written without any credentials or signing key.
        // The release workflow builds here once, then signs and uploads these exact files without running Gradle
        // again (see docs/releasing.md). ./gradlew :springgraph-processor:publishAllPublicationsToStagingRepository
        maven {
            name = "Staging"
            url = uri(stagingRepo)
        }
        // Manual use only; the release workflow does not run Gradle with credentials.
        // Credentials: ORG_GRADLE_PROJECT_GitHubPackagesUsername / ORG_GRADLE_PROJECT_GitHubPackagesPassword.
        maven {
            name = "GitHubPackages"
            // GitHub Packages requires the owner in lowercase.
            url = uri("https://maven.pkg.github.com/mrsluffy/spring-graph")
            credentials(PasswordCredentials::class)
        }
    }
}
