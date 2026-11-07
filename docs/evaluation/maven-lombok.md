# Maven + Lombok check

Project: `examples/maven-lombok` (standalone Maven project, not part of the Gradle build).
Harness: a local script (not in the repository). Every seeded scenario is compiled with the real Maven build and also started
in a real Spring context (`AnnotationConfigApplicationContext`), so each SpringGraph verdict is compared with what Spring actually does.

## Setup

- Maven 3.8.8, maven-compiler-plugin 3.13.0, `release 17`, Spring Framework 6.2.8, Lombok 1.18.38.
- SpringGraph and Lombok are both in `annotationProcessorPaths`; options go through `compilerArgs`.
- The processor comes from the local repository: `./gradlew :springgraph-processor:publishToMavenLocal` installs
  `io.springgraph:springgraph-processor:0.1.0`. (The first run used a manual `install-file` of 0.1.0-SNAPSHOT; results were re-run on 0.1.0.)

## Results

| # | Scenario | SpringGraph | Spring at runtime | Agreement |
|---|---|---|---|---|
| 1 | `@RequiredArgsConstructor` / `@AllArgsConstructor` services, Lombok listed first | strict passes, 3 of 3 validated | (valid) | yes |
| 2 | Same, SpringGraph listed first | strict passes, 3 of 3 validated | (valid) | yes; order does not matter |
| 3-4 | Missing implementation behind a Lombok constructor | SG001 at the class (strict fails, advisory warns) | `NoSuchBeanDefinitionException` | yes |
| 5 | Field `@Qualifier` + `@RequiredArgsConstructor`, no `lombok.config` | SG007 ambiguous | `NoUniqueBeanDefinitionException` (Lombok does not copy the qualifier to the constructor) | yes; catches a known Lombok pitfall at build time |
| 6 | Same with `lombok.copyableAnnotations += ...Qualifier` | SG008 unsupported | context starts | conservative (not validated, not wrong) |
| 7 | Two candidates, parameter named after one bean (`smsSender`) | **was SG007 (false positive when -parameters is on); now SG006** | fails without `-parameters`, starts with it | now consistent: outcome depends on a compiler flag the processor cannot see |

Why Lombok works: Lombok adds the constructor to the AST before SpringGraph's analysis, which runs in the final processing round.

## Fix made

Found in scenario 7: Spring resolves several candidates by matching the parameter name against bean names when parameter names
are compiled in (`-parameters`, enabled by Spring Boot's Maven and Gradle plugins). If exactly one candidate's bean name equals the
parameter name, the processor now reports SG006 with a suggestion to add `@Qualifier`, instead of SG007. Bean names: stereotype `value`,
otherwise `Introspector.decapitalize` of the simple class name. 3 regression tests added. No change on the other pilots
(spring-petclinic-rest still 21 of 36 with no diagnostics).

## Not covered

- Maven inside a Spring Boot parent (only plain Maven here); Maven 3.9.x.
- Lombok `@Value`/`onConstructor_`, `@Builder`; `@Qualifier` on `@Bean` methods.
- Nested-class bean names (`Outer.Inner`) are derived but untested.
- Gradle + Lombok (expected to behave the same; not run).
