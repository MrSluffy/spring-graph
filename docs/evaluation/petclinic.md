# External pilot: spring-petclinic

First application not written for this project. Cloned (shallow) from `spring-projects/spring-petclinic`
into `../pilots-petclinic`, unmodified. SpringGraph is attached through a Gradle init script (`-I`) that adds the processor to `annotationProcessor` and the options
to `compileJava`, so the project's own build files are untouched.

## Environment

- Spring Boot 4.1.0 (Spring Framework 7), Spring Data JPA, Thymeleaf, validation, cache. Java 17 toolchain (Corretto 17.0.14).
- Gradle 9.5.1 (the project's wrapper). Processor jar built with `--release 17` (a change made for this pilot: it previously targeted 21 and
  could not load in a Java 17 javac).
- Windows laptop; same machine as the other evaluations. Hardware not recorded.

## Results

| Check | Result |
|---|---|
| Processor runs on Boot 4.1 / Spring 7 / JDK 17 | Yes, no processor crash |
| Components found | 8 (6 `@Controller`, 1 `@Component`, plus 2 `@Configuration` classes listed as not interpreted) |
| Dependencies analysed | 6, all on Spring Data repositories |
| Statically validated | **0 of 6** (all six are repository edges, reported as "created at runtime") |
| Boundaries declared by the user | 0 |
| False positives (advisory and strict) | 0 |
| Business-code changes | 0 |
| Strict build | Passes |
| App's `PetClinicIntegrationTests` with processor in the build | 3 of 3 pass |
| Seeded missing binding (`BillingService` -> `Billing`) | SG001 at the seeded file, strict build fails, source removed afterwards |
| Clean compile, 5 rounds each (ms) | without 4581 / **4772** / 5502, with 4047 / **4786** / 5449 (min / median / max) |

Overhead is indistinguishable from noise here (median difference 14 ms). Raw data: `petclinic-build-overhead-raw.csv`.

## What this does and does not show

- No false positives on a real, well-known app, and zero configuration. Good for the "does not reject valid apps" criterion (now two apps, only one
  independent of this project).
- The validation value for this app is small: petclinic has no service-to-service wiring, so the only checkable edges
  are controller-to-repository, which the processor deliberately does not validate. A mistake in controller wiring that would be caught
  (a missing component) was caught in the seeded test, but the app as shipped has almost nothing for the tool to validate.
  That is a limitation of the product's supported subset, not an issue with petclinic.
- Not exercised: qualifiers, `@Bean` interplay (the two `@Configuration` classes are only listed), generics, multiple constructors, conditionals, multi-module.
- Not tested against Spring AOT or native-image processing.
- The `unknown enum constant Status.STABLE` warning seen during the test run comes from a library annotation in the
  test classpath, not from the processor.

## Follow-ups

- Try an app with a real service layer (more component-to-component edges) to measure coverage that is not repositories.
- Decide whether reporting 0 of 6 coverage should warn in the build output: an all-boundary graph gives little assurance.
