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
package co.elastic.plugin.version;

import co.elastic.grammar.EsqlGrammar;
import co.elastic.grammar.GrammarVersions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The function names and metadata options of each ES|QL version, from the {@code data/<id>/functions.txt} and
 * {@code metadata.txt} resources. A version id with no data (such as the local build) uses the newest version's
 * data. The files are read on first use and kept.
 */
public final class VersionData {

    private static final String DEV_PREFIX = "DEV_";

    private static final Map<String, Set<String>> FUNCTIONS = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> METADATA = new ConcurrentHashMap<>();

    private VersionData() {}

    /** Upper case function names of a version. */
    public static Set<String> functions(String id) {
        return FUNCTIONS.computeIfAbsent(dataId(id), v -> Set.copyOf(readLines(v, "functions.txt")));
    }

    /** The options after {@code FROM ... METADATA} in a version. */
    public static List<String> metadataOptions(String id) {
        return METADATA.computeIfAbsent(dataId(id), v -> readLines(v, "metadata.txt"));
    }

    /**
     * Whether a command, keyword or function exists in a version: it is a function of that version, or a keyword
     * of that version's grammar.
     */
    public static boolean isAvailable(String id, String word) {
        if (word == null || word.isBlank()) {
            return false;
        }
        return isIn(id, word.trim().toUpperCase(Locale.ROOT));
    }

    /** Whether the word is a function or keyword in at least one released version. */
    public static boolean isKnownInAnyVersion(String word) {
        if (word == null || word.isBlank()) {
            return false;
        }
        String upper = word.trim().toUpperCase(Locale.ROOT);
        for (String id : GrammarVersions.released()) {
            if (isIn(id, upper)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIn(String id, String upper) {
        if (functions(id).contains(upper)) {
            return true;
        }
        EsqlGrammar grammar = EsqlGrammar.forVersionOrNewest(id, null);
        return grammar.hasToken(upper) || grammar.hasToken(DEV_PREFIX + upper);
    }

    private static String dataId(String id) {
        return id != null && GrammarVersions.released().contains(id) ? id : GrammarVersions.newest();
    }

    private static List<String> readLines(String id, String file) {
        String path = "/co/elastic/plugin/data/" + id + "/" + file;
        try (InputStream in = VersionData.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + path);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return reader.lines().map(String::trim).filter(line -> !line.isEmpty()).collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
