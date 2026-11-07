# Build-overhead measurement (validation-only)

## Setup

- OS: Windows (laptop); CPU/RAM not recorded.
- JDK: Amazon Corretto 21.0.10. Gradle 8.7 (warm daemon). Spring Boot 3.5.16.
- Project: `examples/pilot-boot`, 7 source files.
- Command (run by a local timing script): `gradlew :examples:pilot-boot:cleanCompileJava :examples:pilot-boot:compileJava -q`
  with `-PwithSpringGraph=false|true`, alternated, after one warm-up. Wall-clock including Gradle client startup.
- Raw data: `build-overhead-raw.csv`.

## Result (8 runs each, ms; second run, after Spring Data repository recognition)

| | min | median | max |
|---|---|---|---|
| Without SpringGraph | 2028 | 2111 | 2146 |
| With SpringGraph (strict) | 2066 | 2291 | 2989 |

Median difference about 180 ms (about 8.5%). The with-SpringGraph max (2989) is an outlier; the minimum
difference is about 40 ms. An earlier run (before repository recognition, noisier machine state) gave
2980 vs 3039 ms medians (about 60 ms). The two runs disagree by a factor of three on the overhead, so treat
it as "tens to low hundreds of ms on a 7-file project, not precisely known".

## Limitations

- Tiny project; says nothing about large graphs. Processor cost grows with components and the
  assignability checks (currently all-pairs per dependency, O(deps x components)).
- Wall-clock includes Gradle process startup, which dominates.
- Only clean full compilation. Incremental compilation is not measured; the processor is not incremental, so Gradle recompiles the whole module.
- Processor memory and metadata size not measured (report for pilot is under 1 KB).
- Single machine, background load not controlled.

Conclusion: no evidence of meaningful overhead at this scale; no evidence either way at realistic scale.
