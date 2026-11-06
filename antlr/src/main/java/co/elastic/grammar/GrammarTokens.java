/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.grammar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The token names and literals of one grammar version, read from the {@code tokens.txt} resource that the build
 * writes next to the generated classes, so that they are available without loading those classes. The resource
 * has the ANTLR {@code .tokens} format: {@code NAME=type} and {@code 'literal'=type} lines. The build adds the
 * literal of dev-only tokens such as {@code DEV_JOIN_LEFT}, which ANTLR leaves out.
 */
public final class GrammarTokens {

    private final Map<String, Integer> typeByName;
    private final Map<Integer, String> literalByType;

    private GrammarTokens(Map<String, Integer> typeByName, Map<Integer, String> literalByType) {
        this.typeByName = Map.copyOf(typeByName);
        this.literalByType = Map.copyOf(literalByType);
    }

    static GrammarTokens parse(List<String> lines) {
        Map<String, Integer> typeByName = new HashMap<>();
        Map<Integer, String> literalByType = new HashMap<>();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            int eq = line.lastIndexOf('=');
            if (eq <= 0) {
                throw new IllegalStateException("Unexpected line in tokens.txt: " + line);
            }
            String key = line.substring(0, eq);
            int type = Integer.parseInt(line.substring(eq + 1).trim());
            if (key.length() >= 2 && key.startsWith("'") && key.endsWith("'")) {
                literalByType.putIfAbsent(type, unescape(key.substring(1, key.length() - 1)));
            } else {
                typeByName.put(key, type);
            }
        }
        return new GrammarTokens(typeByName, literalByType);
    }

    static GrammarTokens read(String resource) {
        InputStream in = GrammarTokens.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalArgumentException("Missing resource " + resource + "; the build writes it");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return parse(reader.lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Whether the grammar has a token with this symbolic name (for example {@code FROM}). */
    public boolean hasToken(String symbolicName) {
        return symbolicName != null && typeByName.containsKey(symbolicName);
    }

    /** The token type of a symbolic name, or -1 when there is no such token. */
    public int type(String symbolicName) {
        return typeByName.getOrDefault(symbolicName, -1);
    }

    /** The literal text of a token type without quotes (for example {@code left}), or null when it has none. */
    public String literal(int type) {
        return literalByType.get(type);
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    default -> out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
