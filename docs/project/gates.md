# Gates

The plan has two independent go/no-go decisions. A failed construction gate does not block the validation product.

## Gate A: validation value ✅ Passed

> Proceed if a prototype demonstrates useful diagnostics beyond the selected baseline without excessive configuration or
> false positives.

| Criterion | Evidence |
|---|---|
| Useful diagnostics | Missing, ambiguous and cyclic dependencies reported at compile time with the dependency path and source location. The pilot compared against the root exception of a plain Spring context (`BeanCurrentlyInCreationException`), which does not list the cycle; Spring Boot's startup failure analysis does show the beans in a cycle, but only at startup |
| Earlier feedback | About 3 s (compile) against about 11 s (Boot context test) on the pilot, for each seeded mistake |
| Low configuration | Zero business-code changes and zero new annotations on all pilots. Configuration: one processor dependency and two compiler options |
| Few false positives | Three classes found across four applications, all fixed with regression tests |

**Caveat:** every pilot was run by the maintainers. Outside users have not tried it yet.

## Gate B: generated construction value ⏸️ Open

> Proceed with construction generation only if lifecycle and proxy behavior remain correct, registration does not create
> duplicate beans, integration does not require a second container, and measured benefits justify the complexity.

| Criterion | Status |
|---|---|
| Lifecycle and proxy behavior correct | 🟡 Partly verified. Lifecycle callbacks passed; proxies were tested with a hand-rolled JDK-proxy `BeanPostProcessor` in a plain Spring context, not with real `@Transactional`/AOP auto-proxying or Spring Boot |
| No duplicate beans | ✅ The generated callback is attached to the scanned definition; no second definition is registered |
| No second container | ✅ Spring stays the only container |
| Measured benefit | ❌ Not measured. Each construction also does container lookups, which are not free |

Also open: Spring AOT. Instance suppliers set by a post-processor at runtime are not AOT-processed; an AOT-compatible
variant would need a `BeanRegistrationAotProcessor`. See [Spring integration](../architecture/spring-integration.md).

**Decision:** stay validation-only until there are measurements on a realistic application, compared with plain Spring
and Spring AOT.
