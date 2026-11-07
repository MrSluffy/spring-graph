# Graph boundaries (prototype decisions)

## Opt-in

The processor does nothing unless a mode is supplied as a compiler option:

| Option | Meaning |
|---|---|
| `-Aspringgraph.mode=advisory` | All findings are warnings. Missing bindings are "suspected". |
| `-Aspringgraph.mode=strict` | Findings are errors. The module is treated as a closed static boundary. |
| `-Aspringgraph.runtimeBoundaries=a.B,c.D` | Fully qualified types Spring supplies at runtime. |

Why options and not annotations: no annotations module, no runtime classpath change, and
boundaries are centralized in build configuration. Revisit after the pilot (Phase 4).

## Classification

| Result | Report text | Diagnostic |
|---|---|---|
| Resolved | `[validated]` | none |
| Declared boundary | `[not statically validated]` | none (report only) |
| Spring Data repository | `Spring Data repository, created at runtime [not statically validated]` | none (report only) |
| `@Value` parameter | `@Value property/expression [not a bean, not validated]` | none |
| Missing | `<missing>` | SG001 |
| Ambiguous | `<ambiguous: ...>` | SG007 |
| Unsupported | `[unsupported, not validated]` | SG004/5/6/8 |

A boundary only applies when no static candidate exists. An undeclared unknown type is never
silently treated as a boundary.

### Decision: conditional candidates are never resolved

Found by the spring-petclinic-rest pilot. If any component that could satisfy a dependency carries `@Profile` or a
`@Conditional*` annotation, the processor does not resolve, rank, or call the dependency ambiguous: which
candidate is active depends on the environment. The dependency is SG006 (unsupported) unless its type is explicitly
declared a runtime boundary, in which case the declaration wins. The cost is one boundary declaration per profile-switched
interface (6 of 8 declarations in that pilot).

### Boundary declaration checks (SG009)

A declared boundary that does nothing is reported, because a silently ignored declaration hides a missing binding:

| Situation | Severity | Message gist |
|---|---|---|
| Type not on the compilation classpath (typo, removed class) | Error in strict, warning in advisory | "not found on the compilation classpath", with a "Did you mean" suggestion from the requested types (simple-name edit distance up to 3) |
| Type exists but no analysed component requests it | Warning | "never requested ... remove if stale" |
| Type is requested but every such dependency is resolved statically or classified by another rule | Warning | "does not apply to any dependency" |

A typo never replaces the real finding: the unresolved dependency still gets its SG001.

### Low-coverage note

When fewer than half of the dependencies are statically validated, the build emits a single compiler NOTE pointing at the
report. It is not a warning, and not shown at 50% or above.

### Decision: Spring Data repositories are recognized, not declared

An interface extending `org.springframework.data.repository.Repository` (and not `@NoRepositoryBean`)
is classified as its own kind, not as an "unknown type turned boundary". The rule is narrow and
checkable from the type hierarchy. It is still never counted as validated, because whether Spring Data
actually creates the bean depends on repository scanning and conditions the processor cannot see.
This keeps the "no automatic unknown-is-external" policy intact. Revisit if it produces false negatives
(for example repositories excluded from scanning); the pilot is the only evidence so far.

## Round handling

Components are collected by name in every round. Analysis runs once, in the final round, so
types produced by other processors are visible. Components are re-resolved by name at that point;
no compiler objects are kept across rounds. Messages in the final round are still reported by
javac as errors.

## Incremental compilation

The processor is not declared incremental. Gradle falls back to full recompilation of the module.
Incremental behavior is untested and not claimed.
