# Diagnostics reference (0.1.0)

Severity rule: diagnostics marked "mode" are **errors in strict mode** and **warnings in advisory mode**.
Diagnostics are attached to the source element (class or constructor parameter) when one exists.

| Code | Severity | Meaning |
|---|---|---|
| SG001 | mode | Missing binding |
| SG002 | – | Reserved (conflicting binding identity); not emitted in 0.1.0 |
| SG003 | mode | Eager constructor dependency cycle |
| SG004 | mode | Unsupported scope |
| SG005 | mode | Unsupported constructor selection |
| SG006 | mode | Unsupported dependency-resolution feature |
| SG007 | mode | Ambiguous binding |
| SG008 | mode | Unsupported qualifier |
| SG009 | varies | Configuration problem |

## SG001: No static binding found

No analysed component can satisfy the constructor parameter, the type is not a declared runtime boundary, and it is not a
Spring Data repository interface. The message includes the dependency path from a root component and possible fixes.

In strict mode this is a finding relative to the graph assumptions: only components in the module being compiled are known.
A bean defined by `@Bean`, by auto-configuration, or in another module is a runtime boundary that has to be declared.

Fixes: add a `@Component`/`@Service`/... implementation, or declare a runtime boundary (`-Aspringgraph.runtimeBoundaries=<type>`).

## SG003: Eager constructor dependency cycle

A cycle made only of statically resolved constructor dependencies, for example
`A -> B -> A`. Reported once per cycle on the component where the cycle closes. Cycles that pass through runtime boundaries,
`@Lazy`, providers, or unsupported dependencies are not detected.

## SG004: Unsupported scope

The component has `@Scope` with a value other than `singleton` (for example `prototype`, `request`). It is excluded from
the graph. Other components that depend on it are not reported as missing.

## SG005: Unsupported constructor selection

The component has zero or several non-private constructors. 0.1.0 supports exactly one. The component is excluded from the
graph, without cascading SG001s. Lombok-generated constructors count, because Lombok adds them before analysis.

## SG006: Unsupported dependency-resolution feature

The processor will not guess. The dependency is reported as not validated when:

- the parameter type is generic, or a collection, `Map`, `Optional`, `ObjectProvider`, `ObjectFactory` or `Provider`;
- the parameter has `@Lazy`;
- several candidates exist and one is `@Primary`;
- a candidate has `@Profile` or a `@Conditional*` annotation (directly or via meta-annotation). Declare the type a runtime
  boundary if Spring supplies it in every environment you care about;
- several candidates exist and the parameter name equals exactly one candidate's bean name. Spring picks that bean only if the code
  is compiled with `-parameters` (Spring Boot's Maven and Gradle plugins enable it). Add `@Qualifier` to remove the dependency on the
  compiler flag.

## SG007: Ambiguous binding

Several unconditional components implement the requested type, none is `@Primary`, and no parameter-name match applies.
The message lists the candidates. A common cause with Lombok: a field `@Qualifier` is not copied to the generated constructor
unless `lombok.config` contains `lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier`.

## SG008: Unsupported qualifier

The constructor parameter carries `@Qualifier`. Qualifier matching is not implemented in 0.1.0, so the dependency is not
validated.

## SG009: Configuration problem

| Situation | Severity |
|---|---|
| `springgraph.mode` is not `advisory` or `strict` | error |
| A declared runtime boundary type is not on the compilation classpath (usually a typo); includes "Did you mean" | mode |
| A declared boundary exists but no analysed component requests it (stale) | warning |
| A declared boundary is requested, but every such dependency is resolved another way | warning |
| The report file could not be written | warning |

## Note: low coverage

Not a diagnostic code. When fewer than half of the analysed dependencies are statically validated, the build prints one note:
`SpringGraph: only N of M dependencies are statically validated ...`.
