# Support matrix

| Feature | Status |
|---|---|
| `@Component`, `@Service`, `@Repository`, `@Controller`, `@RestController` (direct) | Supported |
| Constructor injection, exactly one non-private constructor | Supported |
| Concrete and non-generic interface resolution | Supported |
| Singleton scope (default, or `@Scope("singleton")`) | Supported |
| Missing / ambiguous / eager constructor cycle | Supported (SG001 / SG007 / SG003) |
| Declared runtime boundaries | Supported (report only); typos, stale and shadowed declarations reported as SG009 |
| Spring Data repository interfaces (extend `Repository`, not `@NoRepositoryBean`) | Recognized, reported as "created at runtime", never counted as validated |
| `@Value` constructor parameters | Recognized as property values, not bean dependencies |
| Other scopes | SG004, component excluded |
| Zero or multiple constructors | SG005, component excluded |
| Generics, collections, Optional, Provider, `@Lazy` | SG006, dependency not validated |
| `@Qualifier("name")` on a constructor parameter | Supported since `0.1.0-rc.3`. A candidate matches by its class-level `@Qualifier` value (also inherited from a superclass) or by its bean name, as in Spring |
| `@Qualifier` without a value; `@Named` or custom qualifier annotations on a parameter | SG008, dependency not validated |
| `@Qualifier` when a candidate is named by `@Named` / `@ManagedBean` or has a custom qualifier | SG008, dependency not validated |
| Several candidates match the same `@Qualifier` | SG006: Spring then chooses by bean name, which is not modelled |
| `@Primary` | Supported since `0.1.0-rc.3`: a unique `@Primary` candidate is validated; several are SG007. Also via meta-annotation |
| `@Fallback` (Spring 6.2+) | Supported since `0.1.0-rc.3`: without `@Primary`, a unique non-`@Fallback` candidate is validated |
| A component that requests its own type (decorator pattern) | Supported since `0.1.0-rc.3`: it is not its own candidate unless nothing else matches, as in Spring |
| `@Priority` among several remaining candidates | SG006, dependency not validated |
| Several candidates, parameter name equals one bean name | SG006: Spring's name fallback needs `-parameters`, which the processor cannot see |
| Lombok-generated constructors (`@RequiredArgsConstructor`, `@AllArgsConstructor`) | Supported, any processor order (Maven checked; see `maven-lombok.md`) |
| Maven `annotationProcessorPaths` | Supported (plain Maven 3.8.8 checked) |
| `@Profile` / `@Conditional*` components (also via meta-annotation) | Kept in the graph and their own dependencies are checked, but never used to resolve a dependency. A dependency with conditional candidates is SG006 unless its type is declared a runtime boundary. |
| `@Configuration` / `@Bean` | Not interpreted; listed as excluded in the report |
| Composed stereotypes, field/method injection, conditions, profiles, multi-module, Kotlin | Not supported |

In advisory mode every diagnostic is a warning; in strict mode every diagnostic is an error.
