# Research and baseline (Phase 0)

Status: **draft**. Items marked TODO were not verified against sources yet.

## Baseline

- Build: JDK 21, Gradle 8.7. The processor itself is compiled with `--release 17` so it loads in a Java 17 javac.
- Spring Framework 6.2.8 (tests, sample), Spring Boot 3.5.16 (pilot-boot).
- Also exercised once on Spring Boot 4.1.0 / Spring Framework 7 / JDK 17 (spring-petclinic, see
  `docs/evaluation/petclinic.md`). Pinned supported Boot lines: still TODO; only these two have been tried.

## Hypotheses

- Validation: developers diagnose supported wiring mistakes earlier with no business-code changes.
- Construction (separate): generated construction beats Spring and Spring AOT with identical behavior.
  Not started. Gate B is open.

## Gate A status (validation value)

Prototype implemented in `springgraph-processor`: missing, ambiguous, cycle, unsupported
constructs, runtime boundaries, deterministic report. 13 compiler tests pass.
Not yet shown: usefulness on a real Boot application, false-positive rate, build overhead.

## Questions (from the plan)

| # | Question | Answer |
|---|---|---|
| 1 | What does Spring AOT validate/generate? | TODO. Read Spring Boot and Framework AOT docs. |
| 2 | Which errors could SpringGraph report earlier? | Missing, ambiguous, and cyclic constructor dependencies within the module, at javac time. Spring reports these at context refresh or AOT processing. |
| 3 | Components visible during javac | Sources of the module being compiled, plus classpath types. Only the module's sources are treated as candidates. |
| 4 | Bindings only known in context processing | Boot auto-configuration, conditions, profiles, `@Bean` methods, Spring Data repositories. |
| 5 | Generated construction and lifecycle/proxies | TODO. Needs the handwritten integration spike. |
| 6 | Spring AOT contribution vs adapter | TODO. |
| 7 | Adoption configuration | Two compiler options and a processor dependency (see graph-boundaries.md). |
| 8 | Advantage without generated construction | The whole validation product. |

## Open work

- Integration spike done (see spring-integration.md). Remaining: real AOP proxies, Boot, Spring AOT.
- Pilot on a real Boot application with persistence.
- Compare against Spring AOT output for the same wiring errors.

## Sources to read

- https://docs.spring.io/spring-boot/reference/packaging/aot.html
- https://docs.spring.io/spring-framework/reference/core/aot.html
- https://dagger.dev/dev-guide/
- https://docs.oracle.com/en/java/javase/17/docs/api/java.compiler/javax/annotation/processing/package-summary.html
