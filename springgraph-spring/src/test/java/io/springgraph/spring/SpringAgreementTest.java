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
package io.springgraph.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import io.springgraph.processor.SpringGraphProcessor;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Checks that SpringGraph's verdict agrees with what Spring actually does for the same classes.
 *
 * <p>Each scenario is compiled once in strict mode (to collect SG codes) and once in advisory mode (so it always compiles); the
 * advisory output is loaded into a real {@link AnnotationConfigApplicationContext}. The agreement contract:
 * <ul>
 *   <li>no SG finding: the context starts;</li>
 *   <li>SG001 / SG007 / SG003: the context fails with the matching Spring exception;</li>
 *   <li>SG006 / SG008: "not validated", so either outcome is allowed. The test records the actual outcome to document why.</li>
 * </ul>
 */
class SpringAgreementTest {

    private static final String STARTED = "started";

    private record Outcome(Set<String> codes, String spring) {}

    // ------------------------------------------------------------------ scenarios

    @Test
    void validInterfaceBindingStarts() {
        Outcome o = run(
                false,
                false,
                "seed.Gateway",
                "package seed; public interface Gateway {}",
                "seed.DefaultGateway",
                "package seed; @org.springframework.stereotype.Component public class DefaultGateway implements Gateway {}",
                "seed.OrderService",
                "package seed; @org.springframework.stereotype.Service public class OrderService { public OrderService(Gateway g) {} }");
        assertAgreement(o, Set.of(), STARTED);
    }

    @Test
    void missingBinding() {
        Outcome o = run(
                false,
                false,
                "seed.Gateway",
                "package seed; public interface Gateway {}",
                "seed.OrderService",
                "package seed; @org.springframework.stereotype.Service public class OrderService { public OrderService(Gateway g) {} }");
        assertAgreement(o, Set.of("SG001"), "NoSuchBeanDefinitionException");
    }

    @Test
    void ambiguousBinding() {
        Outcome o = run(
                false,
                false,
                "seed.Gateway",
                "package seed; public interface Gateway {}",
                "seed.A",
                "package seed; @org.springframework.stereotype.Component public class A implements Gateway {}",
                "seed.B",
                "package seed; @org.springframework.stereotype.Component public class B implements Gateway {}",
                "seed.OrderService",
                "package seed; @org.springframework.stereotype.Service public class OrderService { public OrderService(Gateway g) {} }");
        assertAgreement(o, Set.of("SG007"), "NoUniqueBeanDefinitionException");
    }

    @Test
    void constructorCycle() {
        Outcome o = run(
                false,
                false,
                "seed.A",
                "package seed; @org.springframework.stereotype.Service public class A { public A(B b) {} }",
                "seed.B",
                "package seed; @org.springframework.stereotype.Service public class B { public B(A a) {} }");
        assertAgreement(o, Set.of("SG003"), "BeanCurrentlyInCreationException");
    }

    @Test
    void valueParameterIsNotABeanAndStarts() {
        Outcome o = run(
                false,
                false,
                "seed.Cfg",
                "package seed; @org.springframework.stereotype.Service public class Cfg {"
                        + " public Cfg(@org.springframework.beans.factory.annotation.Value(\"${currency:EUR}\") String c) {} }");
        assertAgreement(o, Set.of(), STARTED);
    }

    private static final String[] NAMED_CANDIDATES = {
        "seed.Sender", "package seed; public interface Sender {}",
        "seed.SmsSender",
                "package seed; @org.springframework.stereotype.Component public class SmsSender implements Sender {}",
        "seed.MailSender",
                "package seed; @org.springframework.stereotype.Component public class MailSender implements Sender {}",
        "seed.User",
                "package seed; @org.springframework.stereotype.Service public class User { public User(Sender smsSender) {} }",
    };

    @Test
    void parameterNameFallbackDependsOnCompilerFlag() {
        // Regression for a former SG007 false positive: Spring resolves by parameter name only with -parameters.
        Outcome without = run(false, false, NAMED_CANDIDATES);
        Outcome with = run(true, false, NAMED_CANDIDATES);
        assertAgreement(without, Set.of("SG006"), "NoUniqueBeanDefinitionException");
        assertAgreement(with, Set.of("SG006"), STARTED);
    }

    @Test
    void profileAlternativesAreNotGuessed() {
        // Regression for a former SG007 false positive (spring-petclinic-rest).
        String[] sources = {
            "seed.Repo", "package seed; public interface Repo {}",
            "seed.JpaRepo",
                    "package seed; @org.springframework.stereotype.Repository @org.springframework.context.annotation.Profile(\"jpa\") public class JpaRepo implements Repo {}",
            "seed.JdbcRepo",
                    "package seed; @org.springframework.stereotype.Repository @org.springframework.context.annotation.Profile(\"jdbc\") public class JdbcRepo implements Repo {}",
            "seed.Service",
                    "package seed; @org.springframework.stereotype.Service public class Service { public Service(Repo r) {} }",
        };
        Outcome noProfile = run(false, false, sources);
        assertAgreement(noProfile, Set.of("SG006"), "NoSuchBeanDefinitionException");
        Outcome jpa = runWithProfile("jpa", sources);
        assertAgreement(jpa, Set.of("SG006"), STARTED);
    }

    @Test
    void lombokRequiredArgsConstructorMissingBinding() {
        Outcome o = run(
                false,
                true,
                "seed.Notifier",
                "package seed; public interface Notifier {}",
                "seed.NotifyService",
                "package seed; @org.springframework.stereotype.Service @lombok.RequiredArgsConstructor"
                        + " public class NotifyService { private final Notifier notifier; }");
        assertAgreement(o, Set.of("SG001"), "NoSuchBeanDefinitionException");
    }

    @Test
    void lombokRequiredArgsConstructorValidGraphStarts() {
        Outcome o = run(
                false,
                true,
                "seed.Gateway",
                "package seed; public interface Gateway {}",
                "seed.DefaultGateway",
                "package seed; @org.springframework.stereotype.Component public class DefaultGateway implements Gateway {}",
                "seed.OrderService",
                "package seed; @org.springframework.stereotype.Service @lombok.RequiredArgsConstructor"
                        + " public class OrderService { private final Gateway gateway; }");
        assertAgreement(o, Set.of(), STARTED);
    }

    @Test
    void lombokFieldQualifierIsNotCopiedSoSpringSeesAmbiguity() {
        // Without lombok.config copyableAnnotations, the field @Qualifier is not on the generated constructor.
        String[] sources = NAMED_CANDIDATES.clone();
        sources[7] =
                "package seed; @org.springframework.stereotype.Service @lombok.RequiredArgsConstructor public class User {"
                        + " @org.springframework.beans.factory.annotation.Qualifier(\"smsSender\") private final Sender sender; }";
        Outcome o = run(false, true, sources);
        assertAgreement(o, Set.of("SG007"), "NoUniqueBeanDefinitionException");
    }

    // ------------------------------------------------------------------ harness

    private static void assertAgreement(Outcome actual, Set<String> expectedCodes, String expectedSpring) {
        assertEquals(new TreeSet<>(expectedCodes), new TreeSet<>(actual.codes()), "SpringGraph codes");
        assertEquals(expectedSpring, actual.spring(), "Spring outcome");

        // The contract, independent of the expected values above.
        Set<String> codes = actual.codes();
        if (codes.isEmpty()) {
            assertEquals(STARTED, actual.spring(), "no finding must mean Spring starts");
        }
        if (codes.contains("SG001") || codes.contains("SG007") || codes.contains("SG003")) {
            assertTrue(!STARTED.equals(actual.spring()), "a definite finding must mean Spring fails");
        }
    }

    private static Outcome run(boolean parameters, boolean lombok, String... namesAndSources) {
        return execute(parameters, lombok, null, namesAndSources);
    }

    private static Outcome runWithProfile(String profile, String... namesAndSources) {
        return execute(false, false, profile, namesAndSources);
    }

    private static Outcome execute(boolean parameters, boolean lombok, String profile, String[] namesAndSources) {
        List<JavaFileObject> sources = new ArrayList<>();
        List<String> classNames = new ArrayList<>();
        for (int i = 0; i < namesAndSources.length; i += 2) {
            classNames.add(namesAndSources[i]);
            sources.add(JavaFileObjects.forSourceString(namesAndSources[i], namesAndSources[i + 1]));
        }
        Compilation strict = compile("strict", parameters, lombok, sources);
        Set<String> codes = new TreeSet<>();
        for (Diagnostic<? extends JavaFileObject> d : strict.errors()) {
            Matcher m = Pattern.compile("^(SG\\d{3}):").matcher(d.getMessage(null));
            if (m.find()) {
                codes.add(m.group(1));
            }
        }
        Compilation advisory = compile("advisory", parameters, lombok, sources);
        assertEquals(
                Compilation.Status.SUCCESS, advisory.status(), () -> "advisory compile failed: " + advisory.errors());
        return new Outcome(codes, startSpring(advisory, classNames, profile));
    }

    private static Compilation compile(String mode, boolean parameters, boolean lombok, List<JavaFileObject> sources) {
        List<Processor> processors = new ArrayList<>();
        if (lombok) {
            processors.add(lombokProcessor());
        }
        processors.add(new SpringGraphProcessor());
        List<String> options = new ArrayList<>(List.of("-Aspringgraph.mode=" + mode));
        if (parameters) {
            options.add("-parameters");
        }
        return Compiler.javac().withProcessors(processors).withOptions(options).compile(sources);
    }

    /** Lombok's processor entry point, as listed in its META-INF/services; the enclosing class is package-private. */
    private static Processor lombokProcessor() {
        try {
            var ctor = Class.forName("lombok.launch.AnnotationProcessorHider$AnnotationProcessor")
                    .getDeclaredConstructor();
            ctor.setAccessible(true);
            return (Processor) ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Lombok annotation processor not available", e);
        }
    }

    private static String startSpring(Compilation compilation, List<String> classNames, String profile) {
        InMemoryClassLoader loader = new InMemoryClassLoader(compilation, SpringAgreementTest.class.getClassLoader());
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.setClassLoader(loader);
            if (profile != null) {
                ctx.getEnvironment().setActiveProfiles(profile);
            }
            for (String name : classNames) {
                Class<?> type = loader.loadClass(name);
                if (!type.isInterface()) {
                    ctx.register(type);
                }
            }
            ctx.refresh();
            return STARTED;
        } catch (Exception e) {
            Throwable t = e;
            while (t.getCause() != null) {
                t = t.getCause();
            }
            return t.getClass().getSimpleName();
        }
    }

    /** Loads classes straight from the compiler's in-memory class output. */
    private static final class InMemoryClassLoader extends ClassLoader {
        private final Map<String, byte[]> classes = new HashMap<>();

        InMemoryClassLoader(Compilation compilation, ClassLoader parent) {
            super(parent);
            for (JavaFileObject file : compilation.generatedFiles()) {
                if (file.getKind() != JavaFileObject.Kind.CLASS) {
                    continue;
                }
                String path = file.toUri().getPath();
                String binary = path.substring(
                                path.indexOf("/CLASS_OUTPUT/") + "/CLASS_OUTPUT/".length(), path.length() - 6)
                        .replace('/', '.');
                try (InputStream in = file.openInputStream()) {
                    classes.put(binary, in.readAllBytes());
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
