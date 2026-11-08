# Engineering milestones

Infrastructure that is not a phase of the product plan, but was needed to ship it.

## Project basics ✅

- Apache License 2.0, `NOTICE`, license headers on every Java file.
- `CONTRIBUTING.md` and `SECURITY.md` (private vulnerability reporting).
- Gradle version catalog (`gradle/libs.versions.toml`) and Dependabot for dependencies and actions.
- Single version source: `gradle.properties`.

## Code structure ✅

- The processor was split from one 700-line class into small packages under `io.springgraph.processor.internal`
  (config, discovery, resolution, findings, report, model, diagnostic). `SpringGraphProcessor` is the only API.
- Output is locked by snapshot tests, so refactors cannot silently change diagnostics or the report.

## Formatting ✅

Spotless: palantir-java-format for Java, ktlint for Gradle scripts, license headers. Part of `./gradlew build` and CI.

## CI ✅

`.github/workflows/ci.yml`, on pull requests and pushes to `development`:

- Gradle build and all tests on Linux and Windows.
- A Maven + Lombok consumer on Java 17, including a seeded mistake that must fail the build.
- Spotless check.
- Read-only permissions, actions pinned to commit SHAs, no publishing secrets.

## Documentation site ✅

This site: MkDocs Material, built from `docs/` and published to GitHub Pages by `.github/workflows/pages.yml`. The build
job has read-only access and no secrets; only the deploy job can write to Pages.

## Release pipeline 🟡

`.github/workflows/release.yml`, started by a signed `v*` tag. It went through two external security reviews.

- **Build once, publish the same files:** Gradle runs only in the first job, which has no secrets. Later jobs check out no
  code and verify the files against checksums before signing or publishing.
- **Tag checks:** the tag must be annotated, signed by the configured maintainer key, on the release branch, and equal to the
  project version. Every publishing job re-checks that the tag still points to the built commit.
- **Secrets:** each secret is available to one job only, in a protected `release` environment with required approval.
- **Targets:** GitHub Packages, Maven Central (validated, then published manually by default), and a GitHub Release with
  jars, signatures and checksums.

**Status:** proven up to signing and GitHub Packages (`rc.1`, `rc.2`). Maven Central rejected `rc.2` at validation, so the
Central upload and the GitHub Release have not completed yet.
