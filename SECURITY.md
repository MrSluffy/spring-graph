# Security policy

## Supported versions

SpringGraph is experimental. Only the latest 0.x release receives fixes.

| Version | Supported |
|---|---|
| 0.1.x | yes |

## Scope

SpringGraph is a compile-time annotation processor. It adds no code to the application runtime and writes one report file
(`META-INF/springgraph/graph.txt`) to the class output. Relevant issues include, for example:

- the processor executing, loading, or writing anything beyond its documented behavior during compilation;
- the report or diagnostics exposing information that should not end up in build output or artifacts;
- a vulnerability in a dependency shipped with the processor (0.1.0 has no runtime dependencies).

A wrong or missing wiring finding is a correctness bug, not a security issue. Please report it as described in
[CONTRIBUTING.md](CONTRIBUTING.md).

## Reporting a vulnerability

Email **werdna.jac@gmail.com** with:

- a description of the issue and its impact;
- steps or a minimal project to reproduce it;
- the SpringGraph, Java, and build tool versions.

Please do not open a public issue for a suspected vulnerability. You can expect an acknowledgement within 7 days. This is a
single-maintainer project, so fix timelines depend on severity and complexity; you will be kept informed and credited unless you
prefer otherwise.
