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
| `@Qualifier` | SG008, dependency not validated |
| Several candidates, parameter name equals one bean name | SG006: Spring's name fallback needs `-parameters`, which the processor cannot see |
| Lombok-generated constructors (`@RequiredArgsConstructor`, `@AllArgsConstructor`) | Supported, any processor order (Maven checked; see `maven-lombok.md`) |
| Maven `annotationProcessorPaths` | Supported (plain Maven 3.8.8 checked) |
| `@Primary` among candidates | SG006, dependency not validated |
| `@Profile` / `@Conditional*` components (also via meta-annotation) | Kept in the graph and their own dependencies are checked, but never used to resolve a dependency. A dependency with conditional candidates is SG006 unless its type is declared a runtime boundary. |
| `@Configuration` / `@Bean` | Not interpreted; listed as excluded in the report |
| Composed stereotypes, field/method injection, conditions, profiles, multi-module, Kotlin | Not supported |

In advisory mode every diagnostic is a warning; in strict mode every diagnostic is an error.
