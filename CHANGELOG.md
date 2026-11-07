# Changelog

## 0.1.0 (experimental), 2026-11-07

First experimental validation-only release. Local publication only (`publishToMavenLocal`).

- Annotation processor `io.springgraph:springgraph-processor`, opt-in with `-Aspringgraph.mode=advisory|strict`.
- Discovery of directly annotated `@Component`, `@Service`, `@Repository`, `@Controller` and `@RestController` classes with exactly one
  non-private constructor (including Lombok-generated constructors).
- Concrete and non-generic interface resolution; SG001 missing, SG007 ambiguous, SG003 eager constructor cycle.
- Explicit runtime boundaries (`-Aspringgraph.runtimeBoundaries`) with SG009 checks for typos and unused or ineffective entries.
- Spring Data repository interfaces and `@Value` parameters recognized and reported as not statically validated.
- No guessing for profiles/conditions, `@Primary`, qualifiers, generics/collections/providers, `@Lazy`, non-singleton scopes,
  and parameter-name bean selection (SG004, SG005, SG006, SG008).
- Deterministic report at `META-INF/springgraph/graph.txt`; a note when less than half of dependencies are validated.
- Tried on spring-petclinic, spring-petclinic-rest, and a Maven + Lombok project; three false-positive classes found and fixed
  (see `docs/evaluation/`).

- Licensed under the Apache License 2.0 (`LICENSE`, also included in `META-INF/` of every jar and declared in the POM).

Known limitations: single module; not incremental; no qualifier matching; no `@Bean` analysis.
