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

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class EsqlGrammarTest {

    private static final String NEWEST = GrammarVersions.newest();

    @Test
    public void testSingleStatementParsesAValidQuery() {
        EsqlGrammar grammar = EsqlGrammar.forVersion(NEWEST);
        List<String> errors = new ArrayList<>();
        BaseErrorListener collector = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                    int charPositionInLine, String msg, RecognitionException e) {
                errors.add(line + ":" + charPositionInLine + " " + msg);
            }
        };

        Lexer lexer = grammar.newLexer(CharStreams.fromString("FROM a | LIMIT 1"), false);
        lexer.removeErrorListeners();
        lexer.addErrorListener(collector);
        Parser parser = grammar.newParser(new CommonTokenStream(lexer), false);
        parser.removeErrorListeners();
        parser.addErrorListener(collector);

        Assert.assertNotNull(grammar.singleStatement(parser));
        Assert.assertEquals(List.of(), errors);
    }

    @Test
    public void testRuleIndexIsMinusOneWhenAbsent() {
        EsqlGrammar grammar = EsqlGrammar.forVersion(NEWEST);
        Assert.assertEquals(-1, grammar.ruleIndex("noSuchRule"));
        Assert.assertTrue(grammar.ruleIndex("metadataSource") >= 0);
    }

    @Test
    public void testTokenNameOfPipe() {
        EsqlGrammar grammar = EsqlGrammar.forVersion(NEWEST);
        // a pipe at the very start lexes as an unknown command, so it follows a source command here
        Lexer lexer = grammar.newLexer(CharStreams.fromString("FROM a | LIMIT 1"), false);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        Parser parser = grammar.newParser(tokens, false);

        Token pipe = tokens.getTokens().stream().filter(t -> t.getText().equals("|")).findFirst().orElseThrow();
        Assert.assertEquals("PIPE", grammar.tokenName(parser, pipe.getType()));
    }

    @Test
    public void testForVersionIsCachedPerId() {
        Assert.assertSame(EsqlGrammar.forVersion(NEWEST), EsqlGrammar.forVersion(NEWEST));
        Assert.assertEquals(NEWEST, EsqlGrammar.forVersion(NEWEST).id());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownIdThrows() {
        EsqlGrammar.forVersion("1.0");
    }

    @Test
    public void testForVersionOrNewestFallsBackAndReports() {
        List<String> missing = new ArrayList<>();
        EsqlGrammar grammar = EsqlGrammar.forVersionOrNewest("1.0", missing::add);
        Assert.assertEquals(NEWEST, grammar.id());
        Assert.assertEquals(List.of("1.0"), missing);
    }

    @Test
    public void testForVersionOrNewestDoesNotReportAKnownId() {
        List<String> missing = new ArrayList<>();
        EsqlGrammar grammar = EsqlGrammar.forVersionOrNewest(NEWEST, missing::add);
        Assert.assertEquals(NEWEST, grammar.id());
        Assert.assertEquals(List.of(), missing);
    }

    @Test
    public void testNewestIsTheLastReleasedVersion() {
        List<String> released = GrammarVersions.released();
        Assert.assertEquals("9.5", GrammarVersions.newest());
        Assert.assertEquals(released.getLast(), GrammarVersions.newest());
    }
}
