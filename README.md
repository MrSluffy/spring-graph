# SpringGraph

Write Spring. Catch wiring mistakes before startup.

SpringGraph is an annotation processor that checks a supported subset of Spring constructor injection while your code compiles.
Spring keeps constructing your beans; SpringGraph adds nothing to the runtime.

**Status: 0.1.0, experimental.** Single-module Java projects. Not published to a public repository.

```text
error: SG001: No static binding found for com.example.PaymentGateway.

  Dependency path:
    com.example.OrderController
      -> com.example.OrderService
        -> com.example.PaymentGateway
```

## Documentation

- [Getting started](docs/getting-started.md): install, Gradle and Maven setup, runtime boundaries
- [Diagnostics](docs/reference/diagnostics.md): every SG code
- [Support matrix](docs/reference/support-matrix.md): what is and is not validated
- [Graph boundaries](docs/architecture/graph-boundaries.md): the rules behind the findings
- [Evaluation](docs/evaluation/): pilot results, false positives found, build overhead
- [Changelog](CHANGELOG.md)

## Build from source

Requires JDK 21 to build. The processor itself targets Java 17.

```
./gradlew build
./gradlew :springgraph-processor:publishToMavenLocal
```

Modules: `springgraph-processor` (the product), `springgraph-spring` (construction-integration experiment, not released),
`examples/` (samples and pilots).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). To report a vulnerability, see [SECURITY.md](SECURITY.md).

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
