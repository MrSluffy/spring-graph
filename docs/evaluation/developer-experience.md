# Developer-experience evaluation: pilot-boot (Phase 4, first pass)

App: `examples/pilot-boot`. Spring Boot 3.5.16, Spring Data JPA + H2, web, 7 components/entities.
Strict mode. It is a small synthetic app, not a third-party codebase; treat results as a first signal only.

## Adoption burden

| Metric | Result |
|---|---|
| Business-code changes | 0 |
| New annotations in application code | 0 |
| Handwritten configuration | 1 processor dependency pair + 2 compiler options + 2 boundary declarations (was 3 before repository recognition) |
| Boundaries needed | `java.time.Clock` (from an uninterpreted `@Configuration`), `ObjectMapper` (Boot). `OrderRepository` is recognized automatically. |
| Duplicate Boot configuration | None |
| Statically validated | 2 of 6 dependencies (33%) |
| Validated by the `@SpringBootTest` context test | The 3 boundaries (context starts; 1 test passes) |

## False positives found

| # | Finding | Action |
|---|---|---|
| 1 | `@Value("${...}") String` reported as SG001 missing `java.lang.String`, with a fix suggesting `String` be declared a boundary | Fixed. `@Value` parameters are now classified as non-bean values. Regression test added (`valueInjectionIsNotAMissingBean`). |

| 2 | `@Profile`-switched implementations of one interface reported as SG007 ambiguity (7 in spring-petclinic-rest) | Fixed. Conditional candidates are never resolved; dependency becomes SG006 or a declared boundary. 5 regression tests. See `petclinic-rest.md`. |

| 3 | Two candidates where the parameter name equals one bean name: SG007, though Spring starts when compiled with `-parameters` | Fixed. Now SG006 with a `@Qualifier` suggestion. Found by comparing against a real Spring context in `maven-lombok.md`. 3 regression tests. |

Two further apps: `petclinic.md` (controllers + repositories, 0 false positives) and `petclinic-rest.md` (service layer, 1 false-positive class found and fixed, 8 boundaries needed, 58% validated).

## Friction observed (not fixed)

- **Low coverage on infrastructure-heavy code.** In this app most edges are boundaries, so strict mode
  validates only the business-to-business edges. That is the honest result, but the guarantee is small.
- **Boundaries for beans declared in the same module.** `Clock` is defined by `@Bean` in `AppConfig`, yet must be declared a boundary because
  `@Configuration` is not interpreted. Limited `@Bean` analysis (Phase 7) would remove this one.
- **Spring Data repositories**: resolved by a decision (see graph-boundaries.md). They are recognized as a
  distinct kind and never counted as validated. Only checked against one repository in one app.
- **Boundary declarations are stringly-typed** FQNs in build files. Unused, misspelled, and shadowed entries are now reported as SG009
  (see `graph-boundaries.md`). The check immediately found a stale `ObjectMapper` entry in this project's own `sample-basic`.

## Seeded mistakes (plan scenarios 1-3)

Run by a local script (3 repetitions per scenario, warm daemon; raw data in
`seeded-mistakes-raw.csv`, messages in `seeded-mistakes-messages.md`). Each mistake is added to the pilot as a
temporary source file and removed afterwards.

| Scenario | SpringGraph: `compileJava` fails | Baseline: `gradlew test` (compile + Boot context test) fails | Detected |
|---|---|---|---|
| Missing implementation | ~2.9-3.7 s (SG001) | ~11.3-11.6 s (`NoSuchBeanDefinitionException`) | 3/3 both |
| Second implementation (ambiguity) | ~2.8 s (SG007) | ~11.2-12.4 s (`NoUniqueBeanDefinitionException`) | 3/3 both |
| Constructor cycle | ~2.9-3.7 s (SG003) | ~11.3-12.4 s (`BeanCurrentlyInCreationException`) | 3/3 both |

Medians over three reruns of the whole script (3 reps each) varied by up to about 0.9 s for SpringGraph and 1.2 s for the baseline.

What this shows:
- Feedback arrives about 3-4x sooner (about 3 s vs about 11 s). Most of the baseline time is Gradle's test task plus starting a Spring Boot context, so
  the gap grows with application startup time and shrinks if the developer already runs a fast slice test.
- The baseline only catches these because a `@SpringBootTest` exists. Without one, the mistake surfaces when the app starts.
- Message quality is comparable on content. Spring already names the missing type and lists ambiguous candidates.
  SpringGraph adds the source location, the dependency path from a root component, and fix suggestions. The cycle comparison
  used the root exception from the test (`BeanCurrentlyInCreationException`), which does not list the cycle; Spring Boot's
  startup failure analysis does show the beans in a cycle, but only at startup. SpringGraph shows the cycle at compile time
  (see `SpringGraphProcessorTest`).
- All 9 detections per side were correct and there were no false positives in these runs.

## Not measured yet

- Time to understand and fix a diagnostic (needs other people).
- Scenarios 4-7 from the plan on this app (Boot-provided bean was exercised; repository was exercised; cross-module
  refactor and conditional configuration were not).
- Incremental build behavior; multi-module; conditional configuration.
- Only wiring mistakes the processor supports were seeded. Mistakes in unsupported areas (qualifiers, conditions) are
  not caught and were not tested for being missed.

## Reading against the success criteria

1. No business-code changes: met on this app.
2. Useful diagnostics: demonstrated on unit tests and one real false-positive fix; not yet on seeded mistakes in the pilot.
3. No duplicate provider methods: met (boundaries only).
4. Boundaries visible and not misrepresented: met (report labels them `[not statically validated]`).
5. Build overhead: see `performance.md`; negligible here, not yet shown on a large app.
6. No regular rejection of valid apps: one false positive found in the first valid app; fixed. Sample size of one.
