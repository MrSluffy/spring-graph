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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import io.springgraph.intellij.report.ReportModel;
import io.springgraph.intellij.report.ReportReader;
import java.nio.file.Path;

/**
 * The whole IDE analysis of one module, on disk: the project model is turned into a javac request, the bundled
 * processor runs, and its report is read back.
 */
public class ModuleAnalysisPlannerTest extends JavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // The fixture's own JDK is a mock that reports Java 1.5; use the real JDK running the tests (21).
        Sdk jdk = JavaSdk.getInstance().createJdk("springgraph-test-jdk", System.getProperty("java.home"), false);
        WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable()));
        ModuleRootModificationUtil.setModuleSdk(getModule(), jdk);
    }

    public void testAnalysesASpringModuleWithTheBundledProcessor() throws Exception {
        myFixture.addFileToProject(
                "org/springframework/stereotype/Component.java",
                "package org.springframework.stereotype; public @interface Component {}");
        myFixture.addFileToProject(
                "org/springframework/stereotype/Service.java",
                "package org.springframework.stereotype; @Component public @interface Service {}");
        myFixture.addFileToProject("p/Gateway.java", "package p; public interface Gateway {}");
        myFixture.addFileToProject(
                "p/DefaultGateway.java",
                "package p; @org.springframework.stereotype.Service public class DefaultGateway implements Gateway {}");
        myFixture.addFileToProject("p/Missing.java", "package p; public interface Missing {}");
        myFixture.addFileToProject("p/OrderService.java", """
                package p;
                @org.springframework.stereotype.Service
                public class OrderService { public OrderService(Gateway gateway, Missing missing) {} }
                """);
        Path processor = BundledProcessor.load().extractTo(IdeReports.processorCache());

        ModuleAnalysisPlanner.Plan plan = ReadAction.compute(
                () -> ModuleAnalysisPlanner.plan(getProject(), getModule(), processor, "strict", "p.Missing"));
        assertTrue(plan.toString(), plan instanceof ModuleAnalysisPlanner.Run);
        JavacRequest request = ModuleAnalysisPlanner.collectSources((ModuleAnalysisPlanner.Run) plan);
        assertEquals(6, request.sources().size());
        assertEquals(processor, request.processorPath().get(0));
        assertEquals(IdeReports.report(getProject(), getModule().getName()), request.report());

        JavacRunner.Result result = JavacRunner.run(request, 120, () -> false);
        assertEquals(result.output(), 0, result.exitCode());
        ReportModel model = ReportReader.read(result.report());
        assertEquals("strict", model.mode);
        ReportModel.Component orders = model.components().stream()
                .filter(c -> "p.OrderService".equals(c.name))
                .findFirst()
                .orElseThrow();
        assertEquals(ReportModel.Resolution.STATIC, orders.dependencies().get(0).resolution());
        assertEquals("p.DefaultGateway", orders.dependencies().get(0).targets().get(0));
        assertEquals(
                ReportModel.Resolution.BOUNDARY, orders.dependencies().get(1).resolution());
    }

    public void testSkipsModulesWithoutSpring() throws Exception {
        myFixture.addFileToProject("p/Plain.java", "package p; public class Plain {}");
        ModuleAnalysisPlanner.Plan plan = ReadAction.compute(
                () -> ModuleAnalysisPlanner.plan(getProject(), getModule(), Path.of("processor.jar"), "advisory", ""));
        assertInstanceOf(plan, ModuleAnalysisPlanner.Skip.class);
        assertEquals("Spring is not on its classpath", ((ModuleAnalysisPlanner.Skip) plan).reason());
    }
}
