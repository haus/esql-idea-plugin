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
package co.elastic.plugin.autocomplete;

import co.elastic.grammar.EsqlGrammar;
import co.elastic.plugin.version.Selection;
import com.intellij.openapi.diagnostic.Logger;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;


public class Parser {
    public record ParserInfo(EsqlGrammar grammar, org.antlr.v4.runtime.Parser parser, List<Token> tokens,
                             List<String> queriedIndexes, boolean devSyntax) {}

    public static Parser.ParserInfo parse(String query, Selection selection) {
        EsqlGrammar grammar = EsqlGrammar.forVersionOrNewest(selection.version().id(),
            id -> Logger.getInstance(Parser.class).warn("No ES|QL grammar for version " + id
                + ", using the newest"));
        boolean devSyntax = selection.devSyntax();
        var lexer = grammar.newLexer(CharStreams.fromString(query), devSyntax);
        lexer.removeErrorListeners();

        CommonTokenStream tokenStream = new CommonTokenStream(lexer);

        List<String> queriedIndexes = new ArrayList<>();
        var parser = grammar.newParser(tokenStream, devSyntax);
        parser.removeErrorListeners();
        int indexPattern = grammar.ruleIndex("indexPattern");
        parser.addParseListener(new ParseTreeListener() {
            @Override
            public void visitTerminal(TerminalNode node) {}

            @Override
            public void visitErrorNode(ErrorNode node) {}

            @Override
            public void enterEveryRule(ParserRuleContext ctx) {}

            @Override
            public void exitEveryRule(ParserRuleContext ctx) {
                if (indexPattern >= 0 && ctx.getRuleIndex() == indexPattern) {
                    queriedIndexes.add(ctx.getText());
                }
            }
        });
        grammar.singleStatement(parser);

        // Make a copy of input tokens array because we are going to remove the EOF
        var tokens = new ArrayList<>(tokenStream.getTokens());
        // Remove EOF
        tokens.removeLast();

        // Reset the parser so CodeCompletionCore can use the ATN from the start
        parser.reset();

        return new ParserInfo(grammar, parser, tokens, queriedIndexes, devSyntax);
    }
}
