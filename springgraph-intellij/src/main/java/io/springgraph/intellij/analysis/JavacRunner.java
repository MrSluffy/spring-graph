/*
 * Copyright 2026 John Andrew Camu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.springgraph.intellij.analysis;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * Runs {@code javac -proc:only} as a separate process with the module's own JDK, so neither the processor nor the
 * project's processors (Lombok, MapStruct) run inside the IDE. All arguments go through an argument file: one quoted
 * argument per line, so long classpaths fit on Windows and no value can become a separate option.
 */
public final class JavacRunner {

    /** Output kept for error messages; the report itself is read from disk. */
    private static final int MAX_OUTPUT_CHARS = 64 * 1024;

    /**
     * @param exitCode javac's exit code: 1 is expected in strict mode with findings
     * @param report   the written graph.json, or null if javac did not get as far as writing it
     * @param output   javac's output, truncated
     */
    public record Result(int exitCode, Path report, String output) {}

    private JavacRunner() {}

    public static Result run(JavacRequest request, long timeoutSeconds, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        resetDirectory(request.classOutput());
        resetDirectory(request.generatedSources());
        Path argFile = request.workDir().resolve("javac.args");
        Files.write(argFile, argumentFileLines(request), StandardCharsets.UTF_8);

        // javac reads the argument file in its JVM's default charset, which is not UTF-8 on JDK 17 for Windows: make it
        // UTF-8 (and the output too; the stdout/stderr properties exist from JDK 19 and are ignored before).
        ProcessBuilder builder = new ProcessBuilder(
                        request.javac().toString(),
                        "-J-Dfile.encoding=UTF-8",
                        "-J-Dstdout.encoding=UTF-8",
                        "-J-Dstderr.encoding=UTF-8",
                        "@" + argFile)
                .directory(request.workDir().toFile())
                .redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> read(process.getInputStream(), output), "SpringGraph javac output");
        reader.setDaemon(true);
        reader.start();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
            if (cancelled.getAsBoolean() || System.nanoTime() > deadline) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
                reader.join(2000);
                throw new InterruptedException(
                        cancelled.getAsBoolean()
                                ? "cancelled"
                                : "javac did not finish within " + timeoutSeconds + " s");
            }
        }
        reader.join(5000);
        Path report = Files.isRegularFile(request.report()) ? request.report() : null;
        synchronized (output) {
            return new Result(process.exitValue(), report, output.toString());
        }
    }

    /** The argument file, one argument per line. Visible for tests. */
    static List<String> argumentFileLines(JavacRequest request) {
        List<String> args = new ArrayList<>();
        args.add("-proc:only");
        args.add("-implicit:none");
        args.add("-encoding");
        args.add(request.encoding());
        args.add("-d");
        args.add(path(request.classOutput()));
        args.add("-s");
        args.add(path(request.generatedSources()));
        args.add("-classpath");
        args.add(paths(request.classpath()));
        args.add("-sourcepath");
        args.add(paths(request.sourcepath()));
        // An explicit processor path also disables processor discovery on the classpath, so the project's own
        // processors run only when they are listed (ModuleAnalysisPlanner adds them).
        args.add("-processorpath");
        args.add(paths(request.processorPath()));
        args.add("-Aspringgraph.mode=" + request.mode());
        args.add("-Aspringgraph.runtimeBoundaries=" + request.boundaries());
        for (Path source : request.sources()) {
            args.add(path(source));
        }
        List<String> lines = new ArrayList<>(args.size());
        for (String arg : args) {
            lines.add(quote(arg));
        }
        return lines;
    }

    /**
     * A double-quoted argument-file token: spaces stay inside one argument, and quotes and backslashes are escaped.
     * Paths use forward slashes (javac accepts them on Windows). Control characters are rejected: no path or option
     * value needs one, and a line break would end the token.
     */
    static String quote(String arg) {
        StringBuilder sb = new StringBuilder(arg.length() + 2).append('"');
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                throw new IllegalArgumentException("control character in javac argument: " + arg.strip());
            }
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.append('"').toString();
    }

    private static String path(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private static String paths(List<Path> paths) {
        List<String> parts = new ArrayList<>(paths.size());
        for (Path p : paths) {
            parts.add(path(p));
        }
        return String.join(File.pathSeparator, parts);
    }

    private static void read(InputStream in, StringBuilder output) {
        char[] buffer = new char[8192];
        // A Reader, so multi-byte characters split across reads decode correctly. Always drained, even past the limit,
        // so javac never blocks on a full pipe.
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            for (int n; (n = reader.read(buffer)) > 0; ) {
                synchronized (output) {
                    if (output.length() < MAX_OUTPUT_CHARS) {
                        output.append(buffer, 0, Math.min(n, MAX_OUTPUT_CHARS - output.length()));
                    }
                }
            }
        } catch (IOException ignored) {
            // the process ended or was killed
        }
    }

    private static void resetDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                });
            } catch (UncheckedIOException ex) {
                throw ex.getCause();
            }
        }
        Files.createDirectories(dir);
    }
}
