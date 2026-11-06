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
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;
import java.util.List;

/** Collects the lexer and parser errors of a query for grammar tests. */
final class SyntaxErrors {

    record SyntaxError(int line, int charPositionInLine, String message) {
        @Override
        public String toString() {
            return line + ":" + charPositionInLine + " " + message;
        }
    }

    private SyntaxErrors() {}

    static List<SyntaxError> of(EsqlGrammar grammar, String query, boolean isDevVersion) {
        List<SyntaxError> errors = new ArrayList<>();
        BaseErrorListener collector = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                    int charPositionInLine, String msg, RecognitionException e) {
                errors.add(new SyntaxError(line, charPositionInLine, msg));
            }
        };

        Lexer lexer = grammar.newLexer(CharStreams.fromString(query), isDevVersion);
        lexer.removeErrorListeners();
        lexer.addErrorListener(collector);

        Parser parser = grammar.newParser(new CommonTokenStream(lexer), isDevVersion);
        parser.removeErrorListeners();
        parser.addErrorListener(collector);
        parser.setBuildParseTree(false);
        grammar.singleStatement(parser);
        return errors;
    }
}
