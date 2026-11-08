# SpringGraph

**Write Spring. Catch wiring mistakes before startup.**

SpringGraph is an annotation processor that checks a supported subset of Spring constructor injection while your code
compiles. Spring keeps constructing your beans; SpringGraph adds nothing to the runtime.

```text
error: SG001: No static binding found for com.example.PaymentGateway.

  Dependency path:
    com.example.OrderController
      -> com.example.OrderService
        -> com.example.PaymentGateway
```

!!! note "Experimental"
    SpringGraph is in `0.x`. The `0.1.0-rc.3` pre-release is on Maven Central as
    `io.github.mrsluffy:springgraph-processor`. See [release history](project/release-history.md).

## Start here

- [Getting started](getting-started.md): add the processor to Gradle or Maven, read the report, declare runtime boundaries
- [Diagnostics](reference/diagnostics.md): what every SG code means and how to fix it
- [Support matrix](reference/support-matrix.md): what is validated, what is reported as not validated

## Where the project is

| | |
|---|---|
| Current phase | Phase 5 (experimental release) finishing; Phase 7 (expansion) started |
| Validation gate (A) | Passed: useful diagnostics on four pilot applications |
| Construction gate (B) | Open: integration works, benefit never measured |

See [milestones](project/milestones.md) for every phase, what was delivered, and what is left.

## Principle

> Ordinary Spring application code should remain ordinary Spring application code.

No business-code changes, no duplicated Boot configuration, no second container. When SpringGraph cannot tell what
Spring would do, it says "not validated" instead of guessing.
