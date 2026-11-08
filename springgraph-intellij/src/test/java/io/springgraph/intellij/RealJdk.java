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

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;

/**
 * Light tests need {@code java.lang.String} to resolve, or constructors no longer match the report. The default mock JDK
 * only exists in the IntelliJ source tree, not in an installed or downloaded IDE, so use the JDK running the tests.
 */
public final class RealJdk {

    public static final LightProjectDescriptor DESCRIPTOR = new DefaultLightProjectDescriptor(
            () -> JavaSdk.getInstance().createJdk("test-jdk", System.getProperty("java.home"), false));

    private RealJdk() {}
}
