# Release criteria

## Experimental validation release (`0.x`)

| Requirement | Status |
|---|---|
| Documented Java and Spring baseline | ✅ |
| Explicit graph-boundary semantics | ✅ |
| Reliable diagnostics for the supported subset | ✅ Compiler tests, snapshot tests, and Spring-agreement tests |
| Runtime boundary reporting | ✅ |
| No required business-code rewrites | ✅ Zero on all pilots |
| No duplicate Boot configuration | ✅ |
| Compiler and real-context tests | ✅ |
| Measured build overhead | 🟡 Clean compile on two small projects; annotation processing only (not a full build) on a 190-file project |
| Clear limitations | ✅ [Support matrix](../reference/support-matrix.md) |

**Blocking `0.1.0`:** the Maven Central validation failure (namespace or signing key) and one complete release-pipeline
run. See [milestones](milestones.md).

## Stable validation release (`1.0`)

| Requirement | Status |
|---|---|
| Tested compatibility matrix | ❌ Two Spring lines tried manually |
| Stable configuration and diagnostic contracts | 🟡 Codes documented; still changing in `0.x` |
| Documented incremental-build behavior | ❌ Not tested |
| Maven and Gradle examples | ✅ |
| Regression coverage from pilot applications | 🟡 From maintainer-run pilots; none from outside users |
| Migration and troubleshooting documentation | ❌ |

## Generated-construction release

Not applicable until [Gate B](gates.md) passes.

| Requirement | Status |
|---|---|
| Verified lifecycle, destruction, and proxy behavior | 🟡 Lifecycle and destruction verified in the integration spike; proxies only with a hand-rolled JDK proxy in a plain Spring context, not real AOP auto-proxying or Spring Boot |
| Predictable registration ownership | ✅ In the integration spike |
| Spring-owned scopes and singleton identity | ✅ In the integration spike |
| Supported Spring AOT behavior documented | ❌ |
| Evidence the extension is worth its complexity | ❌ |
