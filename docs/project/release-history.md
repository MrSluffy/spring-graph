# Release history

| Version | Status | Where | Notes |
|---|---|---|---|
| Unreleased | On `development` | — | `@Qualifier`, `@Primary`, `@Fallback` resolution; decorator fix; `@Named` reported as SG008 |
| `0.1.0-rc.2` | Partly published | GitHub Packages | Includes the Gradle module metadata and signatures. Maven Central rejected the deployment at validation, so no GitHub Release was created |
| `0.1.0-rc.1` | Published | GitHub Packages | First pipeline run. Missing the Gradle `.module` file and signatures; Gradle still resolves it through the POM |

Pre-release versions are for testing the release pipeline. The first intended public release is `0.1.0`.

Full details: [CHANGELOG.md](https://github.com/MrSluffy/spring-graph/blob/development/CHANGELOG.md).

## Installing a pre-release from GitHub Packages

GitHub requires a personal access token (classic) with `read:packages`, even for public packages.

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/mrsluffy/spring-graph")
        credentials {
            username = providers.gradleProperty("gpr.user").getOrNull() ?: System.getenv("USERNAME")
            password = providers.gradleProperty("gpr.key").getOrNull() ?: System.getenv("TOKEN")
        }
    }
}
dependencies {
    annotationProcessor("io.springgraph:springgraph-processor:0.1.0-rc.2")
}
```

Then enable it with `-Aspringgraph.mode=advisory`; see [Getting started](../getting-started.md).
