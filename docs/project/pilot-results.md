# Pilot results

Phase 4 applied SpringGraph to four applications. The full write-ups are in the Evaluation section of this site.

| Application | Shape | Validated | Boundaries declared | False positives found |
|---|---|---|---|---|
| `examples/pilot-boot` | Boot 3.5, JPA, 7 files | 2 of 6 | 2 (`Clock`, `ObjectMapper`) | 1: `@Value` parameters reported as missing beans |
| [spring-petclinic](../evaluation/petclinic.md) | Boot 4.1, controllers + repositories | 0 of 6 | 0 | 0 (every edge is a Spring Data repository) |
| [spring-petclinic-rest](../evaluation/petclinic-rest.md) | Boot 4.1, service layer, profiles, MapStruct | 21 of 36 | 8 | 1: `@Profile` alternatives reported as ambiguous |
| [Maven + Lombok](../evaluation/maven-lombok.md) | Maven + Lombok | 3 of 3 | 0 | 1: parameter-name bean selection (`-parameters`) reported as ambiguous |

All three false positives were fixed, and each has a regression test. A fourth class (`@Named` parameters being ignored)
was found and fixed during the qualifier work in Phase 7.

## Comparison with real Spring

Each seeded mistake was compiled with SpringGraph and also started in a real Spring context:

| Mistake | SpringGraph | Spring |
|---|---|---|
| Missing implementation | SG001 at compile time | `NoSuchBeanDefinitionException` at startup |
| Second implementation | SG007 | `NoUniqueBeanDefinitionException` |
| Constructor cycle | SG003, with the cycle path, at compile time | `BeanCurrentlyInCreationException` (root exception, without the path) at startup |
| Field `@Qualifier` not copied by Lombok | SG007 | `NoUniqueBeanDefinitionException` |

The Spring column is the root exception from a plain Spring context, not Spring Boot's startup failure analysis. Boot's
analysis is more readable and, for cycles, does list the beans involved, but only once the application starts.

These scenarios now run automatically in `SpringAgreementTest` on every build (21 scenarios).

## Feedback time

On the pilot, a seeded mistake was reported in about 3 s (compile) against about 11 s (compile plus a Boot context test).
The gap grows with application startup time and shrinks if a fast slice test already exists. See
[developer experience](../evaluation/developer-experience.md).

## Build overhead

| Project | What was measured | Median difference |
|---|---|---|
| `examples/pilot-boot` (7 files) | Clean Gradle compile | about 60 to 180 ms, within run-to-run noise |
| spring-petclinic | Clean Gradle compile | about 14 ms, within run-to-run noise |
| spring-petclinic-rest (about 190 files) | Annotation processing only (`javac -proc:only`), not the project's own build | about 400 ms; run-to-run variation was about 4 s |

The spring-petclinic-rest number is processing time, not a full-build measurement. Build overhead was not measured on a
large application, and incremental builds were not measured. See [performance](../evaluation/performance.md).

## What the pilots showed is still missing

- **Low coverage on infrastructure-heavy apps.** Most edges are repositories or Boot beans, which are never counted as
  validated.
- **Beans from the app's own `@Configuration`** still need boundary declarations. Limited `@Bean` analysis (Phase 7) would
  remove them.
- **Profile-switched implementations** need one boundary declaration per interface.
