# SpringGraph

Write Spring. Catch wiring mistakes before startup.

SpringGraph is an annotation processor that checks a supported subset of Spring constructor injection while your code compiles.
Spring keeps constructing your beans; SpringGraph adds nothing to the runtime.

**Status: experimental (`0.x`).** Single-module Java projects. The `0.1.0-rc.4` pre-release is on Maven Central and
GitHub Packages.

```text
error: SG001: No static binding found for com.example.PaymentGateway.

  Dependency path:
    com.example.OrderController
      -> com.example.OrderService
        -> com.example.PaymentGateway
```

## Documentation

**Site: [mrsluffy.github.io/spring-graph](https://mrsluffy.github.io/spring-graph/)**, including the project
[milestones](https://mrsluffy.github.io/spring-graph/project/milestones/). The same pages are in `docs/`:

- [Getting started](docs/getting-started.md): install, Gradle and Maven setup, runtime boundaries
- [Diagnostics](docs/reference/diagnostics.md): every SG code
- [Support matrix](docs/reference/support-matrix.md): what is and is not validated
- [Graph boundaries](docs/architecture/graph-boundaries.md): the rules behind the findings
- [Evaluation](docs/evaluation/): pilot results, false positives found, build overhead
- [Changelog](CHANGELOG.md)
- [Release history](docs/project/release-history.md): published versions and how to install pre-releases
- [IntelliJ plugin](docs/intellij.md): checks the project with a bundled processor, no build changes needed; gutter
  icons and a tool window (experimental)

## Build from source

Requires JDK 21 to build. The processor itself targets Java 17.

```
./gradlew build
./gradlew :springgraph-processor:publishToMavenLocal
```

Modules: `springgraph-processor` (the product), `springgraph-spring` (construction-integration experiment, not released),
`examples/` (samples and pilots). `springgraph-intellij` is the IntelliJ plugin, a separate Gradle build
(`./gradlew -p springgraph-intellij build`); see [IntelliJ plugin](docs/intellij.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). To report a vulnerability, see [SECURITY.md](SECURITY.md).

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
