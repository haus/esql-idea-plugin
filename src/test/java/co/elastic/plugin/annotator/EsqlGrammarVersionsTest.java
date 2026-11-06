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
package co.elastic.plugin.annotator;

import co.elastic.grammar.EsqlGrammar;
import co.elastic.grammar.GrammarVersions;
import co.elastic.plugin.annotator.SyntaxErrors.SyntaxError;
import co.elastic.plugin.autocomplete.Completion;
import co.elastic.plugin.autocomplete.CompletionTestAccess;
import co.elastic.plugin.connection.EsqlSchemaProvider;
import co.elastic.plugin.version.GrammarVersion;
import co.elastic.plugin.version.Selection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the same checks against every released grammar: base syntax parses everywhere, newer commands parse only
 * from the version that added them, and completion never throws on an older grammar.
 */
@RunWith(Parameterized.class)
public class EsqlGrammarVersionsTest {

    /**
     * Each query uses one command and maps to the first version whose grammar has the command outside dev mode.
     * See the report of the task that added 9.0 to 9.4 for how each version was found.
     */
    private static final Map<String, String> FIRST_VERSION = Map.ofEntries(
        Map.entry("FROM test | CHANGE_POINT value ON k", "9.1"),
        Map.entry("FROM test | SAMPLE 0.5", "9.1"),
        Map.entry("FROM test | FORK (WHERE a > 1) (WHERE b > 1)", "9.1"),
        // 9.1 had COMPLETION ... WITH inference_id; the map form came in 9.2
        Map.entry("FROM test | COMPLETION answer = question WITH { \"inference_id\": \"my_endpoint\" }", "9.2"),
        Map.entry("TS k8s | LIMIT 5", "9.2"),
        Map.entry("FROM test | RERANK \"query\" ON title WITH { \"inference_id\": \"my_endpoint\" }", "9.2"),
        Map.entry("FROM test | INLINE STATS c = COUNT() BY x", "9.2"),
        Map.entry("FROM test | INLINESTATS c = COUNT() BY x", "9.2"),
        Map.entry("FROM test METADATA _id, _index, _score | FORK (WHERE a > 1) (WHERE b > 1) | FUSE", "9.2"),
        Map.entry("PROMQL index=k8s step=1m result=(1 + 1)", "9.4"),
        Map.entry("FROM test | URI_PARTS parts = last_name | STATS c = COUNT() BY parts.scheme | LIMIT 10", "9.4"),
        Map.entry("FROM test | REGISTERED_DOMAIN rd = last_name | STATS c = COUNT() BY rd.registered_domain",
            "9.4"),
        Map.entry("FROM test | USER_AGENT ua = input", "9.4"),
        Map.entry("TS k8s | METRICS_INFO | KEEP metric_name, metric_type | SORT metric_name", "9.4"),
        Map.entry("TS k8s | TS_INFO | SORT metric_name, dimensions", "9.4"),
        Map.entry("FROM test | MMR ON dense_embedding LIMIT 10", "9.4"),
        Map.entry("FROM test | LIMIT 100 | MMR published_date ON vector LIMIT 10", "9.4"),
        Map.entry("FROM test | IP_LOCATION g = ip WITH { \"properties\": [\"country_iso_code\"] } | KEEP g.*",
            "9.5"),
        Map.entry("PROMQL index=k8s step=1m result=(1 + 1) | TS_COLLAPSE", "9.5")
    );

    @Parameterized.Parameters(name = "{0}")
    public static List<String> versions() {
        return GrammarVersions.released();
    }

    private final String version;
    private final EsqlGrammar grammar;

    public EsqlGrammarVersionsTest(String version) {
        this.version = version;
        this.grammar = EsqlGrammar.forVersion(version);
    }

    private Selection selection() {
        return new Selection(new GrammarVersion(version, version, false), false, null);
    }

    private Set<Completion> completions(String query) {
        return CompletionTestAccess.computeCompletions(query, null, selection());
    }

    private static boolean isBefore(String version, String other) {
        List<String> released = GrammarVersions.released();
        int index = released.indexOf(version);
        int otherIndex = released.indexOf(other);
        Assert.assertTrue("unknown version " + other, otherIndex >= 0);
        return index < otherIndex;
    }

    @Test
    public void testBaseQueriesParse() {
        String query = "FROM a METADATA _id | WHERE x > 1 | EVAL y = x + 1 | STATS c = COUNT() BY y | SORT y | LIMIT 5";
        Assert.assertEquals(List.of(), SyntaxErrors.of(grammar, query, false));
    }

    @Test
    public void testCommandsParseOnlyFromTheirFirstVersion() {
        List<String> failures = new ArrayList<>();
        FIRST_VERSION.forEach((query, first) -> {
            List<SyntaxError> errors = SyntaxErrors.of(grammar, query, false);
            if (isBefore(version, first)) {
                if (errors.isEmpty()) {
                    failures.add("parses before " + first + ": " + query);
                }
            } else if (errors.isEmpty() == false) {
                failures.add("fails from " + first + ": " + query + "\n    " + errors);
            }
        });
        Assert.assertTrue("Version " + version + ":\n" + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void testPartialQueriesNeverThrowInCompletion() {
        for (String query : List.of("", "FROM ", "FROM x | ", "|")) {
            try {
                completions(query);
            } catch (RuntimeException e) {
                throw new AssertionError("completion threw for \"" + query + "\" on " + version, e);
            }
        }
        Set<Completion> afterPipe = completions("FROM x | ");
        Assert.assertTrue(afterPipe.toString(), afterPipe.contains(new Completion("WHERE", Completion.Kind.KEYWORD)));
        Assert.assertTrue(afterPipe.toString(), afterPipe.contains(new Completion("LIMIT", Completion.Kind.KEYWORD)));
    }

    @Test
    public void testFieldCompletionOffersSchemaFields() {
        EsqlSchemaProvider schema = new EsqlSchemaProvider() {
            @Override
            public List<String> getIndices() {
                return List.of("x");
            }

            @Override
            public List<String> getFields(String indexName) {
                return "x".equals(indexName) ? List.of("field1", "field2", "@timestamp") : List.of();
            }
        };
        List<String> failures = new ArrayList<>();
        for (String query : List.of("FROM x | KEEP ", "FROM x | WHERE ", "FROM x | SORT ")) {
            Set<Completion> offered = CompletionTestAccess.computeCompletions(query, schema, selection());
            for (String field : List.of("field1", "field2", "@timestamp")) {
                if (offered.contains(new Completion(field, Completion.Kind.FIELD)) == false) {
                    failures.add("\"" + query + "\" does not offer " + field);
                }
            }
        }
        Assert.assertTrue("Version " + version + ":\n" + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void testMetadataCompletionOffersAtLeastIdAndIndex() {
        Set<Completion> metadata = completions("FROM x METADATA ");
        Assert.assertTrue(metadata.toString(), metadata.contains(new Completion("_id", Completion.Kind.METADATA)));
        Assert.assertTrue(metadata.toString(),
            metadata.contains(new Completion("_index", Completion.Kind.METADATA)));
    }
}
