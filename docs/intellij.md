# IntelliJ plugin (experimental)

The SpringGraph plugin checks a project's Spring constructor injection and shows the results in IntelliJ IDEA. It works
with or without the processor in your build:

- **IDE analysis**: the plugin ships the published processor and runs it itself, so nothing needs to change in your
  `build.gradle.kts` or `pom.xml`.
- **Build reports**: if your build already runs the processor, the plugin shows what that build reported, including a
  failed strict build.

Either way the processor does the analysis and writes a [JSON report](reference/report-json.md); the plugin only shows
it. Without the plugin, a build that runs the processor already lists its findings in the Build tool window.

## What it shows

- **Gutter icons** on every constructor parameter of an analysed component:

    | Icon | Meaning | Click |
    |---|---|---|
    | Check | Validated | Goes to the chosen bean |
    | Information | Runtime boundary, Spring Data repository or `@Value`: not validated by design | |
    | Warning | Unsupported (SG006, SG008) | |
    | Error | Missing (SG001) or ambiguous (SG007) | Ambiguous: choose among the candidates |

    The tooltip shows the classification, the rule that chose the bean (`via @Primary`), the findings for that
    parameter, and whether the result comes from the build or the IDE analysis. Turn the icons off under
    **Settings | Editor | General | Gutter Icons | SpringGraph dependency**.
- **SpringGraph tool window** (right side): one entry per module, with its mode, coverage and origin (build or IDE
  analysis), every finding, the analysed components and their dependencies, and the excluded classes. Double-click or
  press Enter to go to the source. Type to search. The toolbar has **Analyze**, **Refresh** and **Settings**.

## Requirements

- IntelliJ IDEA 2025.3 or newer.
- For the IDE analysis: each analysed module needs a JDK 17 or newer (with `javac`, not a JRE) and Spring on its
  classpath.
- For build reports: the processor `io.github.mrsluffy:springgraph-processor` `0.1.0-rc.4` or newer in the build, with
  `-Aspringgraph.mode=advisory` or `strict`. Earlier versions do not write `graph.json`.

## Install

- **JetBrains Marketplace** (once published and reviewed): **Settings | Plugins | Marketplace**, search for
  *SpringGraph*, install, and restart. Updates arrive through the IDE.
- **From disk:** download `springgraph-intellij-<version>.zip` from a
  [GitHub Release](https://github.com/MrSluffy/spring-graph/releases) (tags `intellij-v*`; the zip is signed, see
  `SHA256SUMS` and the `.crt` next to it). Development builds are the `springgraph-intellij` artifact of the
  [IntelliJ plugin workflow](https://github.com/MrSluffy/spring-graph/actions/workflows/intellij.yml), or build it
  (below). Then **Settings | Plugins | gear icon | Install Plugin from Disk**, choose the zip, and restart.

## Use

1. Open the **SpringGraph** tool window and click **Analyze** (also **Tools | Analyze with SpringGraph**). It runs in the
   background and can be cancelled. A summary lists the analysed and skipped modules.
2. If your build runs the processor, building from the IDE refreshes its reports. After a build from the terminal,
   click **Refresh**: the IDE does not notice terminal builds.

## Settings

**Settings | Tools | SpringGraph**, saved per project in `.idea/springgraph.xml`. Commit that file to share the settings
with your team.

| Setting | Default | Meaning |
|---|---|---|
| Show reports from | Build report when available, otherwise IDE analysis | Per module: *Auto* uses the build's report when the build runs the processor, and the IDE analysis for the other modules. *Build reports only* and *IDE analysis only* restrict it to one source |
| IDE analysis mode | `advisory` | `advisory` reports findings as warnings, `strict` as errors. The graph is the same |
| Runtime boundaries | empty | Types Spring supplies at runtime (for example `java.time.Clock`), one per line or comma separated. Typos are reported as SG009 |
| Run the IDE analysis after every build | off | Re-analyse when a build from the IDE finishes |

Changing the mode or the boundaries starts a new analysis unless **Build reports only** is selected. In **Auto** mode,
modules with build reports are skipped; select **IDE analysis only** to apply these settings to those modules. These
settings never change your Gradle or Maven build, so CI keeps its own configuration.

## How the IDE analysis works

For each module with main Java sources and Spring on its classpath, the plugin runs `javac -proc:only` as a separate
process, using what Gradle or Maven imported into the project:

- the module's JDK, main source roots (not tests or generated sources), compile classpath and source encoding;
- the bundled SpringGraph processor, plus the module's other annotation processors (Lombok, MapStruct), so generated
  constructors are visible. A SpringGraph processor configured in the build is left out, so only the bundled one runs.

`-proc:only` runs annotation processing only and writes no class files. The output goes to IntelliJ's system directory,
never into the project or its build output.

**Trusted projects only.** Like a build, the analysis runs code the project controls: its annotation processors and
its classpath. So it does not run in a project opened in safe mode (untrusted). Trust the project first. Build reports
are still shown in untrusted projects, but only files inside the project directory are read, and reports over 16 MiB
are skipped.

**The bundled processor** is a pinned release of `io.github.mrsluffy:springgraph-processor` from Maven Central
(currently `0.1.0-rc.4`, shown under Settings). The plugin build downloads it, checks its SHA-256 against the value
pinned in `springgraph-intellij/gradle.properties`, and ships it inside the plugin. At runtime the plugin checks the
hash again before every run and never downloads anything, so it works offline and a given plugin version always runs
the same processor. A new processor version comes with a new plugin version.

## Limitations

- Icons show the last analysis or build. They are matched to a constructor by parameter count and erased types, and
  hidden when those no longer match. Other edits, such as a changed `@Qualifier`, keep the old result until the next
  run.
- Constructors generated by Lombok have no source to attach icons to. Their dependencies are listed in the tool window.
- Only main sources, like the processor setup in Getting started.
- The IDE analysis takes a few seconds per module. Code generated by build plugins other than annotation processors is
  visible only when its output directory is registered as a main Java source root that is not marked as generated.
  Source roots marked as generated are excluded from IDE analysis.

## Build from source

The plugin is a separate Gradle build in `springgraph-intellij/`. By default it downloads IntelliJ IDEA 2025.3
(about 1.5 GB). To use an installed IDE of the same version line instead, set its install directory in
`~/.gradle/gradle.properties`:

```properties
springgraph.intellijLocalPath=C:\\Program Files\\JetBrains\\IntelliJ IDEA 2025.3
```

Then:

```
./gradlew -p springgraph-intellij build buildPlugin   # tests, then build/distributions/springgraph-intellij-<version>.zip
./gradlew -p springgraph-intellij verifyPlugin        # JetBrains Plugin Verifier
./gradlew -p springgraph-intellij runIde              # a sandbox IDE with the plugin installed
```

To bundle a newer processor release, change `bundledProcessorVersion` and `bundledProcessorSha256` in
`springgraph-intellij/gradle.properties`. Take the hash of the `.jar` on `repo1.maven.org` after checking its `.asc`
signature with the release key. The build fails if the downloaded jar does not match.
