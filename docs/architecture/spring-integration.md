# Spring integration spike (Phase 1)

Code: `springgraph-spring` (`ConstructionRegistrar`, tests under `src/test`). Spring Framework 6.2.8, plain
`AnnotationConfigApplicationContext`.

## Approach

Generated-style factories (handwritten) are attached as **instance suppliers to the scanned bean
definitions**. The registrar never registers new definitions and fails fast if the scanned definition
is missing, not a plain definition of the expected class, or already has a supplier.
Dependencies are fetched with `beanFactory.getBean(...)` before invoking the constructor.

Reason for this ownership model: plain contexts allow bean-definition overriding (Boot turns it off),
so registering a second definition under the same name would depend on registration order.

## Verified (4 tests)

- Supplier code actually runs (once per bean); a plain context never calls it.
- Bean available by name and type; singleton identity; exactly one bean per type.
- Constructor dependencies are the container's instances, including the `@Bean` "Boot-style" `Clock`.
- A dependent receives the proxy produced by a `BeanPostProcessor`, not the raw target.
- `@PostConstruct` once, `@PreDestroy` on close.
- `BeanPostProcessor` sees the generated-constructed beans.
- Bean definition names match a plain context (apart from the registrar's own config).
- Missing scanned definition fails fast.

## Not verified

- Real `@Transactional` / AOP auto-proxying (a hand-rolled JDK proxy post-processor was used).
- Spring Boot (overriding disabled, auto-configuration ordering).
- Spring AOT / native-image: instance suppliers set at runtime by a post-processor are not AOT-processed.
  An AOT-compatible variant would need an AOT contribution (`BeanRegistrationAotProcessor`). TODO.
- Circular/lazy/scoped beans, `@Primary`, qualifiers, constructor-injected `@Value`.
- Any performance benefit. Nothing was measured, and `getBean` calls per construction are not free.

## Gate B status

**Not passed, not failed.** Correctness of the basic model looks sound, but the benefit side is unproven
(no measurements) and the AOT interaction is open. Decision for now: stay validation-only; revisit
Gate B only after measuring a realistic application against Spring AOT.
