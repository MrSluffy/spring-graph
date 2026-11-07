plugins {
    base // gives the root project a `check` task, so `./gradlew build` runs spotlessCheck
    alias(libs.plugins.spotless)
}

allprojects {
    group = "io.springgraph"
    // version comes from gradle.properties (set it with scripts/set-version.sh)
    repositories { mavenCentral() }
}

// ./gradlew spotlessApply formats everything; ./gradlew spotlessCheck (part of `check`) fails on unformatted files.
spotless {
    java {
        target("springgraph-*/src/**/*.java", "examples/*/src/**/*.java")
        targetExclude("**/build/**", "**/target/**")
        palantirJavaFormat(libs.versions.palantir.java.format.get())
        removeUnusedImports()
        licenseHeaderFile(rootProject.file("gradle/license-header.txt"))
    }
    kotlinGradle {
        target("*.gradle.kts", "springgraph-*/*.gradle.kts", "examples/*/*.gradle.kts")
        // intellij_idea: standard Kotlin style, without ktlint_official's chain-wrapping of short calls.
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(mapOf("ktlint_code_style" to "intellij_idea"))
    }
    format("misc") {
        target("*.md", "docs/**/*.md", ".gitignore", ".gitattributes", ".github/**/*.yml", "gradle/*.toml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
