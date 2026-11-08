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
package io.springgraph.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Locks the exact output (every diagnostic with severity and position, in emission order, plus the full report) for a
 * fixture that exercises every classification and diagnostic. Any change to wording, ordering, or report layout fails here.
 *
 * <p>After an intentional output change, regenerate with {@code ./gradlew :springgraph-processor:test -PupdateSnapshots}
 * and review the diff of {@code src/test/resources/snapshots/}.
 */
class SnapshotTest {

    private static final String[][] FIXTURE = {
        {
            "org.springframework.data.repository.Repository",
            "package org.springframework.data.repository; public interface Repository<T, ID> {}"
        },
        {"p.Gateway", "package p; public interface Gateway {}"},
        {
            "p.DefaultGateway",
            "package p; @org.springframework.stereotype.Component public class DefaultGateway implements Gateway {}"
        },
        {"p.Clock", "package p; public interface Clock {}"},
        {
            "p.OrderRepo",
            "package p; public interface OrderRepo extends org.springframework.data.repository.Repository<String, Long> {}"
        },
        {"p.Missing", "package p; public interface Missing {}"},
        // static, runtime boundary, repository, @Value, missing
        {"p.OrderService", """
                    package p;
                    @org.springframework.stereotype.Service
                    public class OrderService {
                        public OrderService(Gateway gateway, Clock clock, OrderRepo repo,
                                @org.springframework.beans.factory.annotation.Value("${x:y}") String value, Missing missing) {}
                    }
                    """},
        // ambiguity, parameter-name fallback, @Primary
        {"p.Sender", "package p; public interface Sender {}"},
        {
            "p.SmsSender",
            "package p; @org.springframework.stereotype.Component public class SmsSender implements Sender {}"
        },
        {
            "p.MailSender",
            "package p; @org.springframework.stereotype.Component(\"mail\") public class MailSender implements Sender {}"
        },
        {"p.Notifier", "package p; public interface Notifier {}"},
        {
            "p.PrimaryNotifier",
            "package p; @org.springframework.stereotype.Component @org.springframework.context.annotation.Primary public class PrimaryNotifier implements Notifier {}"
        },
        {
            "p.OtherNotifier",
            "package p; @org.springframework.stereotype.Component public class OtherNotifier implements Notifier {}"
        },
        {"p.Messaging", """
                    package p;
                    @org.springframework.stereotype.Service
                    public class Messaging {
                        public Messaging(Sender sender, Sender smsSender, Notifier notifier) {}
                    }
                    """},
        // qualifier, lazy, generic, collection
        {"p.Unsupported", """
                    package p;
                    @org.springframework.stereotype.Service
                    public class Unsupported {
                        public Unsupported(@org.springframework.beans.factory.annotation.Qualifier("x") Gateway q,
                                @org.springframework.context.annotation.Lazy Gateway lazy,
                                java.util.List<Gateway> all, java.util.Optional<Gateway> maybe) {}
                    }
                    """},
        // conditional candidates
        {"p.Store", "package p; public interface Store {}"},
        {
            "p.JpaStore",
            "package p; @org.springframework.stereotype.Repository @org.springframework.context.annotation.Profile(\"jpa\") public class JpaStore implements Store {}"
        },
        {
            "p.JdbcStore",
            "package p; @org.springframework.stereotype.Repository @org.springframework.context.annotation.Profile(\"jdbc\") public class JdbcStore implements Store {}"
        },
        {
            "p.StoreUser",
            "package p; @org.springframework.stereotype.Service public class StoreUser { public StoreUser(Store store) {} }"
        },
        // excluded: scope, constructors; @Configuration listed
        {
            "p.Proto",
            "package p; @org.springframework.stereotype.Component @org.springframework.context.annotation.Scope(\"prototype\") public class Proto {}"
        },
        {
            "p.TwoCtors",
            "package p; @org.springframework.stereotype.Component public class TwoCtors { public TwoCtors() {} public TwoCtors(String s) {} }"
        },
        {"p.Config", "package p; @org.springframework.context.annotation.Configuration public class Config {}"},
        // cycle
        {
            "p.CycleA",
            "package p; @org.springframework.stereotype.Service public class CycleA { public CycleA(CycleB b) {} }"
        },
        {
            "p.CycleB",
            "package p; @org.springframework.stereotype.Service public class CycleB { public CycleB(CycleA a) {} }"
        },
    };

    /** Clock is used; Clok is a typo; Runnable is stale; Gateway is shadowed by DefaultGateway; Store is used by conditionals. */
    private static final String BOUNDARIES = "p.Clock,p.Clok,java.lang.Runnable,p.Gateway,p.Store";

    @ParameterizedTest
    @ValueSource(strings = {"strict", "advisory"})
    void outputMatchesSnapshot(String mode) throws IOException {
        List<JavaFileObject> sources = new ArrayList<>();
        for (String[] f : FIXTURE) {
            sources.add(JavaFileObjects.forSourceString(f[0], f[1]));
        }
        Compilation c = Compiler.javac()
                .withProcessors(new SpringGraphProcessor())
                .withOptions("-Aspringgraph.mode=" + mode, "-Aspringgraph.runtimeBoundaries=" + BOUNDARIES)
                .compile(sources);

        StringBuilder actual = new StringBuilder("== diagnostics ==\n");
        for (Diagnostic<? extends JavaFileObject> d : c.diagnostics()) {
            String message = d.getMessage(null);
            if (!message.startsWith("SG") && !message.startsWith("SpringGraph")) {
                continue; // javac's own notes
            }
            String where = d.getSource() == null
                    ? "-"
                    : d.getSource().getName().replaceAll(".*/", "") + ":" + d.getLineNumber();
            actual.append(d.getKind())
                    .append(' ')
                    .append(where)
                    .append(' ')
                    .append(message.replace("\r", ""))
                    .append("\n---\n");
        }
        // compile-testing hides generated files when compilation fails (strict mode here), so only advisory locks the
        // report.
        if (c.status() == Compilation.Status.SUCCESS) {
            actual.append("== report ==\n")
                    .append(c.generatedFile(StandardLocation.CLASS_OUTPUT, SpringGraphProcessor.REPORT_PATH)
                            .orElseThrow()
                            .getCharContent(true));
        }

        assertSnapshot("snapshots/full-" + mode + ".txt", actual.toString());
        if (c.status() == Compilation.Status.SUCCESS) {
            assertSnapshot(
                    "snapshots/full-" + mode + ".json",
                    c.generatedFile(StandardLocation.CLASS_OUTPUT, SpringGraphProcessor.JSON_REPORT_PATH)
                            .orElseThrow()
                            .getCharContent(true)
                            .toString());
        }
    }

    private static void assertSnapshot(String name, String actual) throws IOException {
        if (System.getProperty("updateSnapshots") != null) {
            Path target = Path.of("src/test/resources").resolve(name);
            Files.createDirectories(target.getParent());
            Files.writeString(target, actual, StandardCharsets.UTF_8);
            return;
        }
        try (InputStream in = SnapshotTest.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Missing snapshot " + name + "; run with -PupdateSnapshots");
            }
            assertEquals(new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r", ""), actual);
        }
    }
}
