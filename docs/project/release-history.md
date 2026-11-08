# Release history

| Version | Status | Where | Notes |
|---|---|---|---|
| `0.1.0-rc.4` | Published | Maven Central, GitHub Packages, [GitHub Release](https://github.com/MrSluffy/spring-graph/releases/tag/v0.1.0-rc.4) | Adds the JSON report `graph.json` (ASCII, schema 1). The version bundled in the IntelliJ plugin |
| `0.1.0-rc.3` | Published | Maven Central, GitHub Packages, [GitHub Release](https://github.com/MrSluffy/spring-graph/releases/tag/v0.1.0-rc.3) | First complete release run. `@Qualifier`, `@Primary`, `@Fallback` resolution; decorator fix; `@Named` reported as SG008; `groupId` changed to `io.github.mrsluffy` |
| `0.1.0-rc.2` | Partly published | GitHub Packages | Includes the Gradle module metadata and signatures. Maven Central rejected the deployment at validation, so no GitHub Release was created |
| `0.1.0-rc.1` | Published | GitHub Packages | First pipeline run. Missing the Gradle `.module` file and signatures; Gradle still resolves it through the POM |

Pre-release versions are for testing the release pipeline. The first intended public release is `0.1.0`.

`0.1.0-rc.1` and `0.1.0-rc.2` were published as `io.springgraph:springgraph-processor`. Later versions use
`io.github.mrsluffy:springgraph-processor`, because Maven Central only accepts a namespace the publisher can prove they
own.

Full details: [CHANGELOG.md](https://github.com/MrSluffy/spring-graph/blob/development/CHANGELOG.md).

## Installing a pre-release

From `0.1.0-rc.3` on, use Maven Central; see [Getting started](../getting-started.md).

The older `0.1.0-rc.1` and `0.1.0-rc.2` exist only on GitHub Packages, under the old `groupId`. GitHub requires a
personal access token (classic) with `read:packages`, even for public packages.

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
