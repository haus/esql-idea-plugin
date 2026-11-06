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

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;

public class GrammarVersionsTest {

    @Test
    public void testReleasedListsNineFive() {
        Assert.assertEquals(List.of("9.5"), GrammarVersions.released());
    }

    @Test
    public void testNewestIsLastReleased() {
        Assert.assertEquals(GrammarVersions.released().getLast(), GrammarVersions.newest());
    }

    @Test
    public void testLocalIsEmptyWithoutLocalGrammar() {
        Assert.assertEquals(Optional.empty(), GrammarVersions.local());
        Assert.assertEquals(Optional.empty(), GrammarVersions.localLabel());
    }

    @Test
    public void testVersionsResourceIsOnTheClasspath() {
        Assert.assertNotNull(GrammarVersions.class.getResource("/co/elastic/grammar/versions.txt"));
    }

    @Test
    public void testParseReadsReleasedAndLocalLines() {
        GrammarVersions.Listing listing = GrammarVersions.parse(
            List.of("released 9.4", "released 9.5", "local upstream/main 0123abcd"));
        Assert.assertEquals(List.of("9.4", "9.5"), listing.released());
        Assert.assertEquals(Optional.of("local"), listing.local());
        Assert.assertEquals(Optional.of("Local build (upstream/main @ 0123abcd)"), listing.localLabel());
    }

    @Test
    public void testGeneratedClassesArePublicAndPerVersion() throws ClassNotFoundException {
        Class<?> lexer = Class.forName("co.elastic.grammar.v9_5.EsqlBaseLexer");
        Class<?> parser = Class.forName("co.elastic.grammar.v9_5.EsqlBaseParser");
        Assert.assertTrue(Modifier.isPublic(lexer.getModifiers()));
        Assert.assertTrue(Modifier.isPublic(parser.getModifiers()));
    }
}
