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
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class GrammarFallbackTest {

    private record Report(String id, String newest, Throwable cause) {}

    private final List<Report> reports = new ArrayList<>();
    private final GrammarFallback fallback =
        new GrammarFallback((id, newest, cause) -> reports.add(new Report(id, newest, cause)));

    @Test
    public void testKnownVersionIsNotReported() {
        EsqlGrammar grammar = fallback.grammarFor("9.3");
        Assert.assertEquals("9.3", grammar.id());
        Assert.assertTrue(reports.isEmpty());
    }

    @Test
    public void testFallbackWhenGrammarCannotBeLoaded() {
        EsqlGrammar grammar = fallback.grammarFor("9.7");
        Assert.assertEquals(GrammarVersions.newest(), grammar.id());
        Assert.assertEquals(1, reports.size());
        Assert.assertEquals("9.7", reports.getFirst().id());
        Assert.assertEquals(GrammarVersions.newest(), reports.getFirst().newest());
        Assert.assertNotNull(reports.getFirst().cause());
    }

    @Test
    public void testEachMissingVersionIsReportedOnce() {
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals(GrammarVersions.newest(), fallback.grammarFor("9.7").id());
        }
        Assert.assertEquals(1, reports.size());
        fallback.grammarFor("8.19");
        fallback.grammarFor("8.19");
        Assert.assertEquals(List.of("9.7", "8.19"), reports.stream().map(Report::id).toList());
    }

    @Test
    public void testSelectionIsLoadedThroughTheSharedHelper() {
        Selection selection = new Selection(new GrammarVersion("9.1", "9.1", false), false, null);
        Assert.assertEquals("9.1", GrammarFallback.grammarFor(selection).id());
    }
}
