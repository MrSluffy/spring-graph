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

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration;

/**
 * Turns an IntelliJ module into a {@link JavacRequest}, using what Gradle or Maven import into the project model: main
 * source roots, compile classpath, JDK, encoding and annotation processor path. Run {@link #plan} in a read action in
 * smart mode; {@link #collectSources} does file I/O and runs outside it.
 */
public final class ModuleAnalysisPlanner {

    /** The oldest JDK the processor runs on. */
    static final JavaSdkVersion MIN_JDK = JavaSdkVersion.JDK_17;

    private static final String SPRING_COMPONENT = "org.springframework.stereotype.Component";

    /** Either a module to analyse (sources still to be listed) or why it is skipped. */
    public sealed interface Plan permits Run, Skip {
        String module();
    }

    /** {@code sourceRoots} are walked by {@link #collectSources} to fill in the request. */
    public record Run(String module, List<Path> sourceRoots, JavacRequest request) implements Plan {}

    public record Skip(String module, String reason) implements Plan {}

    private ModuleAnalysisPlanner() {}

    public static Plan plan(Project project, Module module, Path processorJar, String mode, String boundaries) {
        String name = module.getName();
        List<Path> roots = mainSourceRoots(module);
        if (roots.isEmpty()) {
            return new Skip(name, "no main Java source roots");
        }
        GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
        if (JavaPsiFacade.getInstance(project).findClass(SPRING_COMPONENT, scope) == null) {
            return new Skip(name, "Spring is not on its classpath");
        }

        Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        if (sdk == null) {
            sdk = ProjectRootManager.getInstance(project).getProjectSdk();
        }
        if (sdk == null || !(sdk.getSdkType() instanceof JavaSdk javaSdk) || sdk.getHomePath() == null) {
            return new Skip(name, "no JDK configured");
        }
        JavaSdkVersion version = javaSdk.getVersion(sdk);
        if (version == null || !version.isAtLeast(MIN_JDK)) {
            return new Skip(name, "JDK " + sdk.getVersionString() + " is older than 17, which the processor needs");
        }
        Path javac = javac(Path.of(sdk.getHomePath()));
        if (javac == null) {
            return new Skip(name, "the JDK at " + sdk.getHomePath() + " has no javac (is it a JRE?)");
        }

        // The module's own output is left out: its sources are what is analysed.
        List<Path> classpath = toPaths(OrderEnumerator.orderEntries(module)
                .withoutSdk()
                .withoutModuleSourceEntries()
                .productionOnly()
                .compileOnly()
                .recursively()
                .classes()
                .getPathsList()
                .getPathList());

        Set<Path> sourcepath = new LinkedHashSet<>(roots);
        OrderEnumerator.orderEntries(module).productionOnly().recursively().forEachModule(dependency -> {
            sourcepath.addAll(mainSourceRoots(dependency));
            return true;
        });

        List<Path> processorPath = new ArrayList<>();
        processorPath.add(processorJar);
        processorPath.addAll(otherProcessors(project, module, classpath));

        String encoding =
                EncodingProjectManager.getInstance(project).getDefaultCharset().name();
        JavacRequest request = new JavacRequest(
                javac,
                List.of(),
                classpath,
                new ArrayList<>(sourcepath),
                processorPath,
                encoding,
                mode,
                boundaries,
                IdeReports.workDir(project, name));
        return new Run(name, roots, request);
    }

    /** Fills in the {@code .java} files under the source roots. File I/O: call outside a read action. */
    public static JavacRequest collectSources(Run run) throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path root : run.sourceRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java") && Files.isRegularFile(p))
                        .sorted()
                        .forEach(sources::add);
            } catch (UncheckedIOException ex) {
                throw ex.getCause();
            }
        }
        JavacRequest r = run.request();
        return new JavacRequest(
                r.javac(),
                sources,
                r.classpath(),
                r.sourcepath(),
                r.processorPath(),
                r.encoding(),
                r.mode(),
                r.boundaries(),
                r.workDir());
    }

    /** Main (not test, not generated) Java source roots on the local file system. */
    static List<Path> mainSourceRoots(Module module) {
        List<Path> roots = new ArrayList<>();
        for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
            for (SourceFolder folder : entry.getSourceFolders(JavaSourceRootType.SOURCE)) {
                JavaSourceRootProperties properties = folder.getJpsElement().getProperties(JavaSourceRootType.SOURCE);
                // Generated roots (build/generated/...) would be generated again by the module's processors.
                if (properties != null && properties.isForGeneratedSources()) {
                    continue;
                }
                VirtualFile file = folder.getFile();
                if (file != null && file.isInLocalFileSystem()) {
                    roots.add(file.toNioPath());
                }
            }
        }
        return roots;
    }

    /**
     * The module's other annotation processors (Lombok, MapStruct) as Gradle or Maven configured them. Without them,
     * constructors Lombok generates would be missing. SpringGraph itself is removed: the bundled copy runs instead.
     */
    static List<Path> otherProcessors(Project project, Module module, List<Path> classpath) {
        AnnotationProcessingConfiguration profile =
                CompilerConfiguration.getInstance(project).getAnnotationProcessingConfiguration(module);
        List<Path> paths = new ArrayList<>();
        if (profile.isObtainProcessorsFromClasspath()) {
            paths.addAll(classpath); // javac would discover processors on the classpath
        } else {
            for (String entry : profile.getProcessorPath().split(java.io.File.pathSeparator)) {
                if (!entry.isBlank()) {
                    try {
                        paths.add(Path.of(entry));
                    } catch (InvalidPathException ignored) {
                        // not a usable path; javac would ignore it too
                    }
                }
            }
        }
        paths.removeIf(ModuleAnalysisPlanner::isSpringGraphProcessor);
        return paths;
    }

    /** True for a SpringGraph processor jar or class directory, from any build or version. */
    static boolean isSpringGraphProcessor(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        if (name.startsWith("springgraph-processor") && name.endsWith(".jar")) {
            return true;
        }
        return Files.isRegularFile(path.resolve("io/springgraph/processor/SpringGraphProcessor.class"));
    }

    private static Path javac(Path jdkHome) {
        for (String name : List.of("javac", "javac.exe")) {
            Path candidate = jdkHome.resolve("bin").resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Path> toPaths(List<String> paths) {
        List<Path> result = new ArrayList<>();
        for (String p : paths) {
            try {
                result.add(Path.of(p));
            } catch (InvalidPathException ignored) {
                // skip unusable entries
            }
        }
        return result;
    }
}
