# Contributing

Thanks for trying SpringGraph. It is experimental, so the most useful contributions right now are reports from real projects.

## Reporting a wrong finding

False positives (a valid app rejected) and wrong classifications matter most. Please include:

- the SG code and full message;
- a minimal class set that reproduces it;
- what Spring does at runtime (context starts, or the exception it throws);
- Java, Spring Framework/Boot versions, build tool, and other annotation processors in use.

Every confirmed wrong finding gets a regression test in `SpringGraphProcessorTest`.

## Formatting and license headers

Formatting is enforced with [Spotless](https://github.com/diffplug/spotless): palantir-java-format for Java, ktlint for
Gradle Kotlin scripts, and trailing-whitespace/final-newline rules for docs and config. Spotless also adds the Apache 2.0
header (`gradle/license-header.txt`) to every Java file.

```
./gradlew spotlessApply   # format everything, add missing headers
./gradlew spotlessCheck   # what CI runs; also part of ./gradlew build
```

## Building and testing

Requires JDK 21 (the processor targets Java 17).

```
./gradlew build
```

This runs:

- `SpringGraphProcessorTest`: compiler tests for every diagnostic;
- `SpringAgreementTest` (in `springgraph-spring`): compiles each scenario with the processor, starts the result in a real Spring
  context, and checks that the two agree (no finding means Spring starts; SG001/SG003/SG007 mean Spring fails);
- the construction-integration experiment and the examples (compiled with the processor in strict mode).

A change to resolution rules needs a scenario in `SpringAgreementTest`, so the claim is checked against what Spring actually does.

CI (`.github/workflows/ci.yml`) runs the Gradle build on Linux and Windows, the Maven + Lombok example on Java 17 (including a
seeded mistake that must fail the build), and `spotlessCheck`.

## Code map (`springgraph-processor`)

`io.springgraph.processor.SpringGraphProcessor` is the only API. Everything under `io.springgraph.processor.internal` is
public only so the subpackages can use each other; it has no compatibility guarantee.

| Package (`…processor.`) | Classes | Responsibility |
|---|---|---|
| (root) | `SpringGraphProcessor` | javac entry point: options, collecting candidates per round |
| `internal` | `GraphAnalysis` | the pipeline, in order: discover, resolve, check boundaries, report findings, write report |
| `internal.config` | `Options`, `Mode` | `-A` options |
| `internal.discovery` | `ComponentFactory`, `Annotations`, `SpringTypes` | finding components, scope and constructor rules (SG004, SG005), reading Spring annotations by name |
| `internal.resolution` | `DependencyResolver`, `BoundaryUsage` | ordered rules for one parameter (SG006, SG008); start here for resolution changes |
| `internal.findings` | `BoundaryChecker`, `ResolutionReporter`, `DependencyPaths`, `CycleDetector`, `TextSimilarity` | SG001, SG003, SG007, SG009 |
| `internal.report` | `GraphReport` | report text |
| `internal.model` | `Component`, `Dependency`, `Resolution` | graph model |
| `internal.diagnostic` | `Diagnostics`, `Code` | severity rules and diagnostic codes |

## Output snapshots

`SnapshotTest` locks the exact diagnostics and report for a fixture that covers every classification. If you change
output on purpose, regenerate and review the diff:

```
./gradlew :springgraph-processor:test -PupdateSnapshots
git diff springgraph-processor/src/test/resources/snapshots/
```

## Design rules for changes

- Never guess at Spring semantics. If the processor cannot determine the result, classify the dependency as unsupported (SG006/SG008)
  instead of resolving it or reporting it as an error.
- An unknown dependency never silently becomes a runtime boundary.
- Nothing that is not statically validated is reported as validated.
- Keep the report deterministic.

See `docs/architecture/graph-boundaries.md`.

## License of contributions

SpringGraph is licensed under the Apache License 2.0. Unless you state otherwise, any contribution you submit is licensed under the same
terms, as described in section 5 of the [license](LICENSE).
