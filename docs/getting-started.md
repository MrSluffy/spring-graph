# Getting started (0.1.0, experimental)

SpringGraph is an annotation processor. It checks a supported subset of Spring constructor injection while your code
compiles. It does not change your runtime: Spring still constructs every bean. Nothing needs to be on the runtime classpath.

## Requirements

- Java 17 or newer for compilation.
- Spring Framework 6.2 / Spring Boot 3.5, or Spring Framework 7 / Spring Boot 4.1. Those are the versions it has been tried with.
- Java sources in a single module. Kotlin is not supported.

## 1. Get the processor

Releases are published to Maven Central and GitHub Packages, and attached to
[GitHub Releases](https://github.com/MrSluffy/spring-graph/releases) (see [releasing](releasing.md)). Until the first
release is out, build it from source.

- **Maven Central**: no extra repository needed for Maven; for Gradle use `mavenCentral()`.
- **GitHub Packages**: add the repository `https://maven.pkg.github.com/mrsluffy/spring-graph` (the owner must be
  lowercase). GitHub requires a personal access token (classic) with `read:packages`, even for public packages. In
  Gradle:

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
  ```

  Put `gpr.user=<your GitHub username>` and `gpr.key=<token>` in `~/.gradle/gradle.properties`, never in the project.
  For Maven, add a `<server>` with the same token to `~/.m2/settings.xml`, as described in
  [GitHub's Maven registry docs](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry).
- **From source**: install into your local Maven repository, then use `mavenLocal()` in Gradle:

  ```
  git clone https://github.com/MrSluffy/spring-graph.git
  cd spring-graph
  ./gradlew :springgraph-processor:publishToMavenLocal
  ```

  This installs `io.springgraph:springgraph-processor:0.1.0` into `~/.m2/repository`.

## 2. Add it to your build

Start in advisory mode. Findings are warnings and the build always passes.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    annotationProcessor("io.springgraph:springgraph-processor:0.1.0")
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.addAll(listOf(
        "-Aspringgraph.mode=advisory",
        "-Aspringgraph.runtimeBoundaries="
    ))
}
```

Configure only `compileJava`. Test sources usually have test-only wiring that would produce noise.

### Maven

Add to `maven-compiler-plugin`'s configuration. If you already use `annotationProcessorPaths` (Lombok, MapStruct), add SpringGraph to
the same list. The order does not matter.

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <!-- existing processors, e.g. Lombok -->
      <path>
        <groupId>io.springgraph</groupId>
        <artifactId>springgraph-processor</artifactId>
        <version>0.1.0</version>
      </path>
    </annotationProcessorPaths>
    <compilerArgs>
      <arg>-Aspringgraph.mode=advisory</arg>
      <arg>-Aspringgraph.runtimeBoundaries=</arg>
    </compilerArgs>
  </configuration>
</plugin>
```

Once `annotationProcessorPaths` is set, Maven only runs the processors listed there. If a processor used to be discovered from
the classpath, list it too.

## 3. Read the report

Compile, then open `META-INF/springgraph/graph.txt` in the class output
(`build/classes/java/main` for Gradle, `target/classes` for Maven):

```text
SpringGraph report (mode=advisory)
com.example.OrderService
  com.example.PaymentGateway -> com.example.DefaultPaymentGateway [validated]
  com.example.OrderRepository -> Spring Data repository, created at runtime [not statically validated]
  com.fasterxml.jackson.databind.ObjectMapper -> <missing> [unresolved]
...
Coverage: 1 of 3 dependencies statically validated
```

## 4. Declare runtime boundaries

A `<missing>` dependency that Spring does supply (Boot auto-configuration, a `@Bean` method, another module) is a **runtime
boundary**. Declare its fully qualified type, comma-separated:

```
-Aspringgraph.runtimeBoundaries=com.fasterxml.jackson.databind.ObjectMapper,javax.sql.DataSource
```

Boundaries are listed as `[not statically validated]` in the report. A test that starts the application context is still the only
check that they exist. Misspelled or unused boundaries are reported as SG009.

You do not need to declare:

- Spring Data repository interfaces (recognized automatically, never counted as validated);
- `@Value` parameters (property values, not beans).

## 5. Switch to strict

When the advisory build shows no findings you disagree with, set `-Aspringgraph.mode=strict`. Findings become compile errors.

## What is checked, and what is not

Checked: classes annotated directly with `@Component`, `@Service`, `@Repository`, `@Controller` or `@RestController` that have exactly
one non-private constructor, including Lombok-generated ones. Missing, ambiguous and cyclic constructor dependencies are reported.

Not checked (reported as not validated, never guessed): qualifiers, `@Primary`, generics and collections, `@Lazy`, providers, profile and
conditional components, non-singleton scopes, `@Bean` methods, field and setter injection, other modules. See
[support matrix](reference/support-matrix.md) and [diagnostics](reference/diagnostics.md).

## Build impact

On the projects tried (7 to about 190 source files) the processing overhead was within run-to-run noise. The processor is not
incremental, so Gradle recompiles the whole module when it is enabled. See [performance](evaluation/performance.md).

## Turning it off

Remove `-Aspringgraph.mode`. Without it the processor does nothing.
