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
package io.springgraph.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import io.springgraph.intellij.report.ReportModel;
import java.util.List;

/** Maps report entries back to source. Callers need read access. */
public final class PsiLookup {

    private PsiLookup() {}

    public static PsiClass findClass(Project project, String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        return JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project));
    }

    /**
     * True when the constructor still has the parameters the report describes (same count, same erased types). False
     * means the source changed since the build, or it is a different constructor; nothing is shown then.
     */
    public static boolean matches(PsiMethod constructor, ReportModel.Component component) {
        PsiParameter[] parameters = constructor.getParameterList().getParameters();
        List<ReportModel.Dependency> dependencies = component.dependencies();
        if (parameters.length != dependencies.size()) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (!erasedName(parameters[i].getType()).equals(dependencies.get(i).type)) {
                return false;
            }
        }
        return true;
    }

    /** The source constructor the report analysed, or null if none matches any more. */
    public static PsiMethod findConstructor(PsiClass type, ReportModel.Component component) {
        for (PsiMethod constructor : type.getConstructors()) {
            if (matches(constructor, component)) {
                return constructor;
            }
        }
        return null;
    }

    /**
     * Where to go for a report entry: the constructor parameter when it still matches, otherwise the class. Null when
     * the class is not in the project.
     *
     * @param component the analysed component, or null for a class that is only named (excluded, configuration)
     */
    public static Navigatable target(
            Project project, String className, ReportModel.Component component, Integer parameter) {
        PsiClass type = findClass(project, className);
        if (type == null) {
            return null;
        }
        if (component != null && parameter != null) {
            PsiMethod constructor = findConstructor(type, component);
            if (constructor != null
                    && parameter >= 0
                    && parameter < constructor.getParameterList().getParametersCount()) {
                return constructor.getParameterList().getParameters()[parameter];
            }
        }
        return type;
    }

    /** Like javac's erasure as the processor reports it: qualified name without type arguments; varargs as arrays. */
    static String erasedName(PsiType type) {
        PsiType declared = type instanceof PsiEllipsisType ellipsis ? ellipsis.toArrayType() : type;
        return TypeConversionUtil.erasure(declared).getCanonicalText();
    }
}
