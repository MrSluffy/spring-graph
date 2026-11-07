# External pilot 2: spring-petclinic-rest

Second application not written for this project, and the first with a real service layer. Cloned (shallow) from
`spring-petclinic/spring-petclinic-rest` into `../pilots-petclinic-rest`, unmodified.

## Why this app

Layers: REST controllers -> `ClinicServiceImpl` / `UserServiceImpl` -> repositories. Repository implementations are switched by
`@Profile` (`jpa`, `jdbc`, `spring-data-jpa`), DTOs come from the OpenAPI generator, mappers from MapStruct
(`componentModel=spring`). That exercises profile selection, generated sources, and a second annotation processor.

## Setup

- Spring Boot 4.1.2, Maven. The project requires Maven 3.9.9+ (enforcer); its wrapper was used for `generate-sources`.
- The pom is not edited. A local harness script (not in the repository) runs
  `javac -proc:only` with SpringGraph **and** MapStruct on the processor path, over the app sources plus the generated DTOs,
  using a classpath resolved by `dependency:build-classpath`.
- This is a processing-only run, not the project's real compile. It does not prove SpringGraph works inside Maven's
  `annotationProcessorPaths` or alongside Lombok/other processors beyond MapStruct.

## Findings (in order)

| Step | Result |
|---|---|
| First strict run, no boundaries | 15 errors: 8 x SG001, 7 x SG007 |
| SG001 (8) | `DataSource` (7) and `JdbcTemplate` (1), supplied by Boot. Legitimate, need boundary declarations. |
| SG007 (7) | **False positives.** Each of `OwnerRepository`, `PetRepository`, ... has `@Profile`-selected implementations; only one set is ever active. The processor saw several candidates and called it ambiguous. |
| Fix | Components with `@Profile`/`@Conditional*` (directly or via meta-annotation) are never used to resolve a dependency. Matching dependencies become SG006 (unsupported), unless the user declares the type a runtime boundary. 5 regression tests added. |
| Strict run after fix, no boundaries | 7 x SG006 (was SG007) + 8 x SG001 |
| Strict run with 8 declared boundaries (`DataSource`, `JdbcTemplate`, 6 repository interfaces) | **0 errors, exit 0** |
| Statically validated | **21 of 36** dependencies (58%) |
| Not validated | 15, all declared runtime boundaries (7 x `DataSource`, 1 x `JdbcTemplate`, 7 x profile-switched repository interfaces) |
| MapStruct-generated `*MapperImpl` classes | Resolved statically, e.g. `OwnerMapper -> OwnerMapperImpl [validated]`. Components generated in a later round are seen. |

## Burden

- Business-code changes: 0. New annotations: 0.
- Boundary declarations: **8** for a 36-dependency app. Two are plain Boot infrastructure; six exist only because implementations
  are profile-switched. That is a real cost of the "never guess at conditions" policy for apps that use profiles this way.
- The 3 `@Configuration` classes are listed as not interpreted.

## Overhead (annotation processing only, `javac -proc:only`, 6 rounds, includes javac JVM start-up)

| | min | median | max |
|---|---|---|---|
| MapStruct only | 3625 | 6673 | 7500 |
| MapStruct + SpringGraph | 3785 | 7079 | 7772 |

Run-to-run variation (about 4 s) is larger than the difference (about 160 ms by minimum, about 400 ms by median).
No conclusion beyond "small relative to noise on a 190-source-file app". Raw data: `petclinic-rest-overhead-raw.csv`.

## Limits

- The 21 validated edges are assumed correct because the upstream app starts; the app's context was not started in this run.
- Seeded mistakes were not run on this app. The same code paths were verified on `pilot-boot` and `spring-petclinic`.
- Boundary typos are now reported (SG009). After that change the 8 declared boundaries here all count as used (no SG009).
- Sample of one conditional-selection style (`@Profile`); `@ConditionalOnProperty` configs are only unit-tested.
