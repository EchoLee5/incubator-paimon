/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.codegen.codesplit;

import org.apache.paimon.utils.FileIOUtils;
import org.apache.paimon.utils.Pair;
import org.apache.paimon.utils.StringUtils;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.SimpleCompiler;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.apache.paimon.utils.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/** Util class for code split tests. */
public final class CodeSplitTestUtil {

    /**
     * A pattern matcher linebreak regexp that represents any Unicode linebreak sequence making it
     * effectively equivalent to below codes.
     *
     * <pre>{@code
     * &#92;u000D&#92;u000A|[&#92;u000A&#92;u000B&#92;u000C&#92;u000D&#92;u0085&#92;u2028&#92;u2029]
     * }</pre>
     */
    public static final String UNIVERSAL_NEW_LINE_REGEXP = "\\R";

    private CodeSplitTestUtil() {}

    /**
     * Trim every line of provided multiline String.
     *
     * @param multilineString multiline string which line should be trimmed.
     * @return multiline string with trimmed lines.
     */
    public static String trimLines(String multilineString) {
        if (StringUtils.isNullOrWhitespaceOnly(multilineString)) {
            return "";
        }

        return Arrays.stream(multilineString.split(UNIVERSAL_NEW_LINE_REGEXP))
                .map(String::trim)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    public static String readResource(String resourcePath) {
        try {
            return FileIOUtils.readFileUtf8(
                    new File(
                            BlockStatementGrouperTest.class
                                    .getClassLoader()
                                    .getResource("codesplit/" + resourcePath)
                                    .toURI()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void tryCompile(String baseResource) throws Exception {
        URI baseUri =
                BlockStatementGrouperTest.class
                        .getClassLoader()
                        .getResource("codesplit/" + baseResource)
                        .toURI();

        Map<String, String> expectedClasses =
                Files.list(Paths.get(baseUri))
                        .filter(path -> path.toUri().toString().endsWith(".java"))
                        .map(
                                path ->
                                        Pair.of(
                                                path.toUri().toString(),
                                                CodeSplitTestUtil.readResource(
                                                        baseResource + path.getFileName())))
                        .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        assertThat(expectedClasses).isNotEmpty();

        for (Entry<String, String> entry : expectedClasses.entrySet()) {
            String classFile = entry.getKey();
            String code = entry.getValue();
            try {
                CodeSplitTestUtil.tryCompile(CodeSplitTestUtil.class.getClassLoader(), code);
            } catch (CompileException e) {
                fail(
                        String.format(
                                "Compilation for file [%s] failed with message: %s",
                                classFile, e.getMessage()));
            }
        }
    }

    public static void tryCompile(ClassLoader cl, String code) throws CompileException {
        checkNotNull(cl, "Classloader must not be null.");
        SimpleCompiler compiler = new SimpleCompiler();
        compiler.setParentClassLoader(cl);
        compiler.cook(code);
    }

    public static String removeApacheHeader(String content) {
        String[] lines = content.trim().split("\n");
        List<String> removed = new ArrayList<>();
        int i;
        for (i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.startsWith("/*") && !line.startsWith(" *") && !"".equals(line)) {
                break;
            }
        }
        for (; i < lines.length; i++) {
            removed.add(lines[i]);
        }
        return String.join("\n", removed);
    }
}
