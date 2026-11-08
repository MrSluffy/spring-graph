# Changelog

## Unreleased

- `@Qualifier("name")` on constructor parameters is resolved as Spring does: a candidate matches by its class-level
  `@Qualifier` value (also inherited from a superclass) or by its bean name. A qualifier without a match is SG001 with a hint.
- `@Primary` is resolved: a unique `@Primary` candidate is validated, several are SG007. `@Fallback` (Spring 6.2+): a unique
  non-fallback candidate is validated.
- A component is not its own candidate while another one exists, matching Spring. Fixes false SG007 and SG003 findings for the
  decorator pattern.
- `@Named` and custom qualifier annotations on parameters are now reported as SG008. Previously they were ignored, which could
  produce a false SG007.
- The report shows the parameter's qualifier and why a candidate was chosen (`[validated via @Primary]`).
- Still not validated: several candidates matching one qualifier, `@Priority`, `@Named`-named candidates.
- Release pipeline: GitHub Packages now also receives the Gradle module metadata and the signatures.
- **Coordinates changed**: the `groupId` is now `io.github.mrsluffy` (artifact `springgraph-processor` unchanged), the
  Maven Central namespace verified through the GitHub account. `0.1.0-rc.1` and `0.1.0-rc.2` remain on GitHub Packages
  under `io.springgraph`. Java package names are unchanged (`io.springgraph.processor`).
- New machine-readable report `META-INF/springgraph/graph.json` (schema version 1), next to `graph.txt`: the graph plus
  every finding tied to its class and constructor parameter. Also written when a strict build fails. See
  `docs/reference/report-json.md`.
- New, experimental IntelliJ plugin (`springgraph-intellij`, released separately, not on Marketplace yet): gutter icons
  on constructor parameters and a SpringGraph tool window, both read from `graph.json`. See `docs/intellij.md`.
- IntelliJ plugin, standalone: **Analyze with SpringGraph** runs the processor bundled in the plugin (`0.1.0-rc.4` from
  Maven Central, pinned by SHA-256, no runtime download) on each Spring module, so the build needs no changes. New
  settings page (Settings | Tools | SpringGraph): report source (build, IDE analysis, or automatic per module), mode,
  runtime boundaries, and analysis after each build. The Gradle or Maven build is never changed.

- IntelliJ plugin release pipeline (`release-intellij.yml`): a signed `intellij-v<pluginVersion>` tag on a commit of the
  release branch, by the maintainer key, runs the tests and Plugin Verifier, signs the plugin with JetBrains' signer,
  uploads it to JetBrains Marketplace, and creates a GitHub Release with the signed zip. Processor releases keep their
  `v*` tags.

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
