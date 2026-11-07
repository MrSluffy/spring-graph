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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

class SpringGraphProcessorTest {

    private static JavaFileObject src(String name, String body) {
        return JavaFileObjects.forSourceString(name, body);
    }

    private static Compilation compile(String mode, String boundaries, JavaFileObject... files) {
        Compiler compiler = Compiler.javac().withProcessors(new SpringGraphProcessor());
        if (mode != null) {
            compiler = compiler.withOptions(
                    "-Aspringgraph.mode=" + mode,
                    "-Aspringgraph.runtimeBoundaries=" + (boundaries == null ? "" : boundaries));
        }
        return compiler.compile(files);
    }

    private static final JavaFileObject GATEWAY =
            src("p.PaymentGateway", "package p; public interface PaymentGateway {}");
    private static final JavaFileObject DEFAULT_GATEWAY = src("p.DefaultPaymentGateway", """
            package p;
            import org.springframework.stereotype.Component;
            @Component public class DefaultPaymentGateway implements PaymentGateway {}
            """);
    private static final JavaFileObject ORDER_SERVICE = src("p.OrderService", """
            package p;
            import org.springframework.stereotype.Service;
            @Service public class OrderService {
                public OrderService(PaymentGateway gateway) {}
            }
            """);

    @Test
    void noOptInMeansNoAnalysis() {
        Compilation c = compile(null, null, GATEWAY, ORDER_SERVICE);
        assertThat(c).succeededWithoutWarnings();
    }

    @Test
    void validGraphResolvesInterfaceToImplementation() throws IOException {
        Compilation c = compile("strict", null, GATEWAY, DEFAULT_GATEWAY, ORDER_SERVICE);
        assertThat(c).succeeded();
        String report = c.generatedFile(StandardLocation.CLASS_OUTPUT, SpringGraphProcessor.REPORT_PATH)
                .orElseThrow()
                .getCharContent(true)
                .toString();
        assertTrue(report.contains("p.PaymentGateway -> p.DefaultPaymentGateway [validated]"), report);
        assertTrue(report.contains("1 of 1 dependencies statically validated"), report);
    }

    @Test
    void strictReportsMissingBindingWithPathAndLocation() {
        Compilation c = compile("strict", null, GATEWAY, ORDER_SERVICE);
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("SG001").inFile(ORDER_SERVICE);
        assertThat(c).hadErrorContaining("p.OrderService");
    }

    @Test
    void advisoryDowngradesMissingToWarning() {
        Compilation c = compile("advisory", null, GATEWAY, ORDER_SERVICE);
        assertThat(c).succeeded();
        assertThat(c).hadWarningContaining("SG001");
    }

    @Test
    void runtimeBoundaryIsNotAnErrorAndIsNotReportedAsValidated() throws IOException {
        Compilation c = compile("strict", "p.PaymentGateway", GATEWAY, ORDER_SERVICE);
        assertThat(c).succeeded();
        String report = c.generatedFile(StandardLocation.CLASS_OUTPUT, SpringGraphProcessor.REPORT_PATH)
                .orElseThrow()
                .getCharContent(true)
                .toString();
        assertTrue(report.contains("[not statically validated]"), report);
        assertTrue(report.contains("0 of 1 dependencies statically validated"), report);
    }

    @Test
    void unknownDependencyDoesNotSilentlyBecomeBoundary() {
        Compilation c = compile("strict", "some.Other", GATEWAY, ORDER_SERVICE);
        assertThat(c).hadErrorContaining("SG001");
    }

    @Test
    void ambiguousBinding() {
        JavaFileObject other = src("p.OtherGateway", """
                package p;
                import org.springframework.stereotype.Component;
                @Component public class OtherGateway implements PaymentGateway {}
                """);
        Compilation c = compile("strict", null, GATEWAY, DEFAULT_GATEWAY, other, ORDER_SERVICE);
        assertThat(c).hadErrorContaining("SG007");
        assertThat(c).hadErrorContaining("p.OtherGateway");
    }

    @Test
    void constructorCycleReportsFullPathOnce() {
        JavaFileObject a = src("p.A", """
                package p;
                import org.springframework.stereotype.Service;
                @Service public class A { public A(B b) {} }
                """);
        JavaFileObject b = src("p.B", """
                package p;
                import org.springframework.stereotype.Service;
                @Service public class B { public B(A a) {} }
                """);
        Compilation c = compile("strict", null, a, b);
        assertThat(c).hadErrorContaining("SG003");
        String message = c.errors().get(0).getMessage(null);
        assertTrue(message.matches("(?s).*p\\.A\\s+-> p\\.B\\s+-> p\\.A\\s*"), message);
        org.junit.jupiter.api.Assertions.assertEquals(1, c.errors().size());
    }

    @Test
    void multipleConstructorsAreUnsupportedWithoutCascadingErrors() {
        JavaFileObject multi = src("p.Multi", """
                package p;
                import org.springframework.stereotype.Service;
                @Service public class Multi {
                    public Multi() {}
                    public Multi(String s) {}
                }
                """);
        JavaFileObject user = src("p.User", """
                package p;
                import org.springframework.stereotype.Service;
                @Service public class User { public User(Multi m) {} }
                """);
        Compilation c = compile("strict", null, multi, user);
        assertThat(c).hadErrorContaining("SG005");
        org.junit.jupiter.api.Assertions.assertEquals(1, c.errors().size());
    }

    @Test
    void prototypeScopeIsUnsupported() {
        JavaFileObject proto = src("p.Proto", """
                package p;
                import org.springframework.context.annotation.Scope;
                import org.springframework.stereotype.Component;
                @Component @Scope("prototype") public class Proto {}
                """);
        assertThat(compile("strict", null, proto)).hadErrorContaining("SG004");
    }

    @Test
    void genericAndQualifiedInjectionAreUnsupportedNotGuessed() {
        JavaFileObject svc = src("p.Svc", """
                package p;
                import java.util.List;
                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.stereotype.Service;
                @Service public class Svc {
                    public Svc(List<PaymentGateway> all, @Qualifier("x") PaymentGateway one) {}
                }
                """);
        Compilation c = compile("strict", null, GATEWAY, DEFAULT_GATEWAY, svc);
        assertThat(c).hadErrorContaining("SG006");
        assertThat(c).hadErrorContaining("SG008");
    }

    // Regression: found by the pilot app. @Value String parameters are not bean dependencies.
    @Test
    void valueInjectionIsNotAMissingBean() throws IOException {
        JavaFileObject svc = src("p.Cfg", """
                package p;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.stereotype.Service;
                @Service public class Cfg { public Cfg(@Value("${x:y}") String x) {} }
                """);
        Compilation c = compile("strict", null, svc);
        assertThat(c).succeeded();
        assertTrue(report(c).contains("@Value property/expression [not a bean, not validated]"), report(c));
        assertTrue(report(c).contains("0 of 1 dependencies statically validated"), report(c));
    }

    private static final JavaFileObject DATA_REPOSITORY = src("org.springframework.data.repository.Repository", """
            package org.springframework.data.repository;
            public interface Repository<T, ID> {}
            """);
    private static final JavaFileObject NO_REPO_BEAN = src("org.springframework.data.repository.NoRepositoryBean", """
            package org.springframework.data.repository;
            public @interface NoRepositoryBean {}
            """);

    private static JavaFileObject repoUser(String repoType) {
        return src("p.RepoUser", """
                package p;
                import org.springframework.stereotype.Service;
                @Service public class RepoUser { public RepoUser(%s r) {} }
                """.formatted(repoType));
    }

    @Test
    void springDataRepositoryIsItsOwnClassificationNotValidated() throws IOException {
        JavaFileObject repo = src("p.OrderRepo", """
                package p;
                public interface OrderRepo extends org.springframework.data.repository.Repository<String, Long> {}
                """);
        Compilation c = compile("strict", null, DATA_REPOSITORY, repo, repoUser("OrderRepo"));
        assertThat(c).succeeded();
        String r = report(c);
        assertTrue(
                r.contains("p.OrderRepo -> Spring Data repository, created at runtime [not statically validated]"), r);
        assertTrue(r.contains("0 of 1 dependencies statically validated"), r);
    }

    @Test
    void noRepositoryBeanAndPlainInterfacesAreStillMissing() {
        JavaFileObject base = src("p.BaseRepo", """
                package p;
                @org.springframework.data.repository.NoRepositoryBean
                public interface BaseRepo extends org.springframework.data.repository.Repository<String, Long> {}
                """);
        assertThat(compile("strict", null, DATA_REPOSITORY, NO_REPO_BEAN, base, repoUser("BaseRepo")))
                .hadErrorContaining("SG001");
        assertThat(compile("strict", null, GATEWAY, repoUser("PaymentGateway"))).hadErrorContaining("SG001");
    }

    // Regression: found by the spring-petclinic-rest pilot. Profile-switched implementations of one interface
    // were reported as SG007 ambiguity, but only one is ever active.
    private static final JavaFileObject JPA_GATEWAY = src("p.JpaGateway", """
            package p;
            import org.springframework.context.annotation.Profile;
            import org.springframework.stereotype.Repository;
            @Repository @Profile("jpa") public class JpaGateway implements PaymentGateway {}
            """);
    private static final JavaFileObject JDBC_GATEWAY = src("p.JdbcGateway", """
            package p;
            import org.springframework.context.annotation.Profile;
            import org.springframework.stereotype.Repository;
            @Repository @Profile("jdbc") public class JdbcGateway implements PaymentGateway {}
            """);

    @Test
    void profileAlternativesAreUnsupportedNotAmbiguous() {
        Compilation c = compile("strict", null, GATEWAY, JPA_GATEWAY, JDBC_GATEWAY, ORDER_SERVICE);
        assertThat(c).hadErrorContaining("SG006");
        assertThat(c).hadErrorContaining("p.JpaGateway (@Profile)");
        org.junit.jupiter.api.Assertions.assertEquals(
                1, c.errors().size(), c.errors().toString());
        assertTrue(c.errors().stream().noneMatch(d -> d.getMessage(null).contains("SG007")));
    }

    @Test
    void declaredBoundaryAcceptsProfileAlternativesAndReportMarksThemConditional() throws IOException {
        Compilation c = compile("strict", "p.PaymentGateway", GATEWAY, JPA_GATEWAY, JDBC_GATEWAY, ORDER_SERVICE);
        assertThat(c).succeeded();
        String r = report(c);
        assertTrue(r.contains("p.JpaGateway [conditional: @Profile"), r);
        assertTrue(r.contains("p.PaymentGateway -> Spring runtime boundary [not statically validated]"), r);
    }

    @Test
    void singleConditionalCandidateIsNeverReportedValidated() {
        JavaFileObject cond = src("p.Cond", """
                package p;
                import org.springframework.context.annotation.Condition;
                import org.springframework.context.annotation.ConditionContext;
                import org.springframework.core.type.AnnotatedTypeMetadata;
                public class Cond implements Condition {
                    public boolean matches(ConditionContext c, AnnotatedTypeMetadata m) { return true; }
                }
                """);
        JavaFileObject impl = src("p.OnlyIfGateway", """
                package p;
                import org.springframework.context.annotation.Conditional;
                import org.springframework.stereotype.Component;
                @Component @Conditional(Cond.class) public class OnlyIfGateway implements PaymentGateway {}
                """);
        assertThat(compile("strict", null, GATEWAY, cond, impl, ORDER_SERVICE)).hadErrorContaining("SG006");
    }

    @Test
    void conditionalComponentsOwnDependenciesAreStillChecked() {
        JavaFileObject conditional = src("p.NeedsMissing", """
                package p;
                import org.springframework.context.annotation.Profile;
                import org.springframework.stereotype.Service;
                @Service @Profile("x") public class NeedsMissing { public NeedsMissing(PaymentGateway g) {} }
                """);
        assertThat(compile("strict", null, GATEWAY, conditional)).hadErrorContaining("SG001");
    }

    @Test
    void lowCoverageProducesOneNoteAndFullCoverageProducesNone() {
        Compilation low = compile("strict", "p.PaymentGateway", GATEWAY, ORDER_SERVICE);
        assertThat(low).succeeded();
        assertThat(low).hadNoteContaining("only 0 of 1 dependencies are statically validated");
        Compilation full = compile("strict", null, GATEWAY, DEFAULT_GATEWAY, ORDER_SERVICE);
        assertTrue(full.notes().stream().noneMatch(d -> d.getMessage(null).contains("statically validated")));
    }

    private static boolean hasSg009(Compilation c) {
        return c.diagnostics().stream().anyMatch(d -> d.getMessage(null).contains("SG009"));
    }

    @Test
    void misspelledBoundaryIsReportedWithSuggestionAndDependencyStaysMissing() {
        Compilation c = compile("strict", "p.PaymentGatewy", GATEWAY, ORDER_SERVICE);
        assertThat(c).hadErrorContaining("SG001"); // the typo must not hide the real problem
        assertThat(c).hadErrorContaining("SG009");
        assertThat(c).hadErrorContaining("not found on the compilation classpath");
        assertThat(c).hadErrorContaining("Did you mean 'p.PaymentGateway'?");
    }

    @Test
    void misspelledBoundaryIsOnlyAWarningInAdvisoryMode() {
        Compilation c = compile("advisory", "p.PaymentGatewy", GATEWAY, ORDER_SERVICE);
        assertThat(c).succeeded();
        assertThat(c).hadWarningContaining("SG009");
    }

    @Test
    void staleBoundaryForAnExistingTypeIsAWarning() {
        Compilation c = compile("strict", "java.lang.Runnable", GATEWAY, DEFAULT_GATEWAY, ORDER_SERVICE);
        assertThat(c).succeeded();
        assertThat(c).hadWarningContaining("SG009");
        assertThat(c).hadWarningContaining("never requested");
    }

    @Test
    void boundaryShadowedByStaticBindingIsWarned() {
        Compilation c = compile("strict", "p.PaymentGateway", GATEWAY, DEFAULT_GATEWAY, ORDER_SERVICE);
        assertThat(c).succeeded();
        assertThat(c).hadWarningContaining("does not apply to any dependency");
    }

    @Test
    void usedBoundariesProduceNoSg009() {
        assertTrue(!hasSg009(compile("strict", "p.PaymentGateway", GATEWAY, ORDER_SERVICE)));
        assertTrue(!hasSg009(compile("strict", "p.PaymentGateway", GATEWAY, JPA_GATEWAY, JDBC_GATEWAY, ORDER_SERVICE)));
    }

    // Regression: found by the maven-lombok runtime comparison. With -parameters Spring resolves several candidates by
    // matching the parameter name to a bean name, so SG007 would be a false positive.
    private static final JavaFileObject SMS = src("p.SmsGateway", """
            package p;
            import org.springframework.stereotype.Component;
            @Component public class SmsGateway implements PaymentGateway {}
            """);
    private static final JavaFileObject NAMED_MAIL = src("p.MailGateway", """
            package p;
            import org.springframework.stereotype.Component;
            @Component("mail") public class MailGateway implements PaymentGateway {}
            """);

    private static JavaFileObject userWithParam(String paramName) {
        return src("p.Named", """
                package p;
                import org.springframework.stereotype.Service;
                @Service public class Named { public Named(PaymentGateway %s) {} }
                """.formatted(paramName));
    }

    @Test
    void parameterNameMatchingDefaultBeanNameIsUnsupportedNotAmbiguous() {
        Compilation c = compile("strict", null, GATEWAY, SMS, NAMED_MAIL, userWithParam("smsGateway"));
        assertThat(c).hadErrorContaining("SG006");
        assertThat(c).hadErrorContaining("-parameters");
        assertTrue(c.errors().stream().noneMatch(d -> d.getMessage(null).contains("SG007")));
    }

    @Test
    void parameterNameMatchingExplicitBeanNameIsUnsupportedNotAmbiguous() {
        Compilation c = compile("strict", null, GATEWAY, SMS, NAMED_MAIL, userWithParam("mail"));
        assertThat(c).hadErrorContaining("SG006");
        assertTrue(c.errors().stream().noneMatch(d -> d.getMessage(null).contains("SG007")));
    }

    @Test
    void parameterNameMatchingNoBeanIsStillAmbiguous() {
        // "mailGateway" is the class-derived name, but the explicit bean name is "mail"
        assertThat(compile("strict", null, GATEWAY, SMS, NAMED_MAIL, userWithParam("mailGateway")))
                .hadErrorContaining("SG007");
        assertThat(compile("strict", null, GATEWAY, SMS, NAMED_MAIL, userWithParam("gateway")))
                .hadErrorContaining("SG007");
    }

    @Test
    void invalidModeIsReported() {
        assertThat(compile("bogus", null, GATEWAY)).hadErrorContaining("SG009");
    }

    @Test
    void reportIsDeterministic() throws IOException {
        String first = report(compile("strict", null, GATEWAY, DEFAULT_GATEWAY, ORDER_SERVICE));
        String second = report(compile("strict", null, ORDER_SERVICE, DEFAULT_GATEWAY, GATEWAY));
        org.junit.jupiter.api.Assertions.assertEquals(first, second);
    }

    private static String report(Compilation c) throws IOException {
        return c.generatedFile(StandardLocation.CLASS_OUTPUT, SpringGraphProcessor.REPORT_PATH)
                .orElseThrow()
                .getCharContent(true)
                .toString();
    }
}
