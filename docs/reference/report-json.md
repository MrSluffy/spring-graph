# JSON report (`graph.json`)

Next to `graph.txt`, the processor writes `META-INF/springgraph/graph.json` to the class output on every compile
(`build/classes/java/main` for Gradle, `target/classes` for Maven). It holds the same graph as the text report, plus every
coded finding tied to its class and constructor parameter. It is meant for tools: the
[IntelliJ plugin](../intellij.md), CI checks, dashboards.

It is also written when a strict build fails, so tools can show the findings of that build.

## Compatibility

- `schemaVersion` is `1`. It changes only when a field is removed or its meaning changes.
- New fields may appear within a version. Readers must ignore fields they do not know.
- New `resolution` values may appear within a version. Treat an unknown value as "not validated".
- Absent values are `null`, never omitted.
- The file is pure ASCII: every non-ASCII character is written as a `\uXXXX` escape, so it reads the same whatever
  encoding the compiler used. Any JSON parser decodes these.
- Values come from source code (class, parameter and qualifier names). Tools that display them as HTML must escape them.
- Output is deterministic: components sorted by name, dependencies in parameter order, findings in the order the build
  printed them.

## Layout

```json
{
  "schemaVersion": 1,
  "mode": "advisory",
  "coverage": { "validated": 4, "total": 15 },
  "components": [
    {
      "name": "p.OrderService",
      "conditional": null,
      "dependencies": [
        {
          "index": 0,
          "parameter": "gateway",
          "type": "p.Gateway",
          "qualifier": null,
          "resolution": "STATIC",
          "targets": ["p.DefaultGateway"],
          "reason": null
        }
      ]
    }
  ],
  "excluded": [
    { "name": "p.Config", "reason": "@Configuration is not interpreted (its @Bean methods are unknown)", "configuration": true }
  ],
  "findings": [
    {
      "code": "SG001",
      "severity": "WARNING",
      "component": "p.OrderService",
      "parameter": 4,
      "message": "No static binding found for p.Missing.\n\nDependency path:\n  ..."
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `mode` | `advisory` or `strict` |
| `coverage` | Statically validated dependencies, out of all dependencies of analysed components |
| `components[].name` | Qualified class name of an analysed component |
| `components[].conditional` | `@Profile` or `@Conditional*` when the component is conditional (then it is never used to resolve a dependency), else `null` |
| `dependencies[].index` | Constructor parameter position, from 0 |
| `dependencies[].parameter` | Parameter name as javac sees it |
| `dependencies[].type` | Erased, fully qualified requested type |
| `dependencies[].qualifier` | The parameter's `@Qualifier` value, or `null` |
| `dependencies[].resolution` | `STATIC` (validated), `BOUNDARY`, `REPOSITORY`, `VALUE`, `MISSING`, `AMBIGUOUS`, `UNSUPPORTED`. Only `STATIC` counts as validated. See the [support matrix](support-matrix.md) |
| `dependencies[].targets` | The chosen component for `STATIC`, the candidates for `AMBIGUOUS`, otherwise empty |
| `dependencies[].reason` | For `STATIC`: the rule that chose the target (`@Qualifier`, `@Primary`, `@Fallback`), or `null` for a single candidate. For `AMBIGUOUS`: `@Primary` when several candidates are primary |
| `excluded[]` | Classes not analysed: unsupported scope or constructors, and `@Configuration` classes (`configuration: true`) |
| `findings[].code` | `SG001` to `SG009`; see [diagnostics](diagnostics.md) |
| `findings[].severity` | `ERROR` or `WARNING`, as the build printed it |
| `findings[].component` | The class the finding is attached to, or `null` (for example, a mistyped runtime boundary) |
| `findings[].parameter` | The constructor parameter index it is attached to, or `null` |
| `findings[].message` | The full message without the `SGnnn: ` prefix. The first line is the summary |

The full output for a fixture that covers every classification is locked by the processor's `SnapshotTest`
(`springgraph-processor/src/test/resources/snapshots/full-advisory.json`).
