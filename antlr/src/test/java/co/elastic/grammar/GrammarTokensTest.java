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

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class GrammarTokensTest {

    @Test
    public void testParseReadsNamesAndLiterals() {
        GrammarTokens tokens = GrammarTokens.parse(List.of("FROM=3", "EQ=40", "DEV_JOIN_LEFT=36", "'from'=3",
            "'=='=40", "'it\\'s'=41", "", "'left'=36"));
        Assert.assertTrue(tokens.hasToken("FROM"));
        Assert.assertFalse(tokens.hasToken("from"));
        Assert.assertFalse(tokens.hasToken(null));
        Assert.assertEquals(36, tokens.type("DEV_JOIN_LEFT"));
        Assert.assertEquals(-1, tokens.type("NOPE"));
        Assert.assertEquals("from", tokens.literal(3));
        Assert.assertEquals("==", tokens.literal(40));
        Assert.assertEquals("it's", tokens.literal(41));
        Assert.assertEquals("left", tokens.literal(36));
        Assert.assertNull(tokens.literal(99));
    }

    /** The resource must agree with the generated classes it stands in for. */
    @Test
    public void testResourceMatchesTheGeneratedVocabularyOfEveryVersion() {
        for (String id : GrammarVersions.released()) {
            EsqlGrammar grammar = EsqlGrammar.forVersion(id);
            GrammarTokens tokens = EsqlGrammar.tokensOrNewest(id);
            Vocabulary vocabulary = grammar.newParser(
                new CommonTokenStream(grammar.newLexer(CharStreams.fromString(""), false)), false).getVocabulary();
            for (int type = 1; type <= vocabulary.getMaxTokenType(); type++) {
                String name = vocabulary.getSymbolicName(type);
                if (name != null) {
                    Assert.assertEquals(id + " " + name, type, tokens.type(name));
                }
                String literal = vocabulary.getLiteralName(type);
                if (literal != null) {
                    Assert.assertEquals(id + " " + literal, literal.substring(1, literal.length() - 1),
                        tokens.literal(type));
                }
            }
        }
    }

    @Test
    public void testDevOnlyTokensHaveTheirLiteral() {
        GrammarTokens newest = EsqlGrammar.tokensOrNewest("9.5");
        Assert.assertEquals("left", newest.literal(newest.type("DEV_JOIN_LEFT")));
        Assert.assertEquals("right", newest.literal(newest.type("DEV_JOIN_RIGHT")));
        Assert.assertEquals("dedup", newest.literal(newest.type("DEV_DEDUP")));
        Assert.assertEquals("lookup_🐔", newest.literal(newest.type("DEV_LOOKUP")));
        GrammarTokens oldest = EsqlGrammar.tokensOrNewest("9.1");
        Assert.assertEquals("ts", oldest.literal(oldest.type("DEV_TIME_SERIES")));
        Assert.assertEquals("full", oldest.literal(oldest.type("DEV_JOIN_FULL")));
    }

    @Test
    public void testUnknownIdUsesTheNewestTokens() {
        Assert.assertSame(EsqlGrammar.tokensOrNewest(GrammarVersions.newest()), EsqlGrammar.tokensOrNewest("0.0"));
        Assert.assertSame(EsqlGrammar.tokensOrNewest(GrammarVersions.newest()), EsqlGrammar.tokensOrNewest(null));
    }

    @Test
    public void testGrammarHasTokenUsesTheResource() {
        Assert.assertTrue(EsqlGrammar.forVersion("9.5").hasToken("FORK"));
        Assert.assertFalse(EsqlGrammar.forVersion("9.0").hasToken("FORK"));
        Assert.assertTrue(EsqlGrammar.forVersion("9.5").hasToken("DEV_DEDUP"));
    }
}
