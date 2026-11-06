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

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Loads the generated lexer and parser of one ES|QL grammar version by name, so that callers never refer to
 * generated classes or their constants. Token types and rule indexes differ between versions; look them up by
 * name through this class.
 */
public final class EsqlGrammar {

    /** Prefix of the per-version packages that hold the generated grammar classes. */
    private static final String GRAMMAR_PACKAGE_PREFIX = "co.elastic.grammar.v";

    private static final Map<String, EsqlGrammar> BY_ID = new ConcurrentHashMap<>();

    private final String id;
    private final Constructor<? extends Lexer> lexerConstructor;
    private final Constructor<? extends Parser> parserConstructor;
    private final Method singleStatement;
    private final Map<String, Integer> ruleIndexes;

    private EsqlGrammar(String id, Constructor<? extends Lexer> lexerConstructor,
                        Constructor<? extends Parser> parserConstructor, Method singleStatement,
                        String[] ruleNames) {
        this.id = id;
        this.lexerConstructor = lexerConstructor;
        this.parserConstructor = parserConstructor;
        this.singleStatement = singleStatement;
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 0; i < ruleNames.length; i++) {
            indexes.put(ruleNames[i], i);
        }
        this.ruleIndexes = Map.copyOf(indexes);
    }

    /**
     * Returns the grammar for a version id.
     *
     * @throws IllegalArgumentException when this build has no generated classes for the id
     */
    public static EsqlGrammar forVersion(String id) {
        if (id == null) {
            throw new IllegalArgumentException("No ES|QL grammar for version null");
        }
        return BY_ID.computeIfAbsent(id, EsqlGrammar::load);
    }

    /**
     * Returns the grammar for a version id, or the newest grammar when there is none for the id. In that case
     * {@code onFallback} receives the missing id.
     */
    public static EsqlGrammar forVersionOrNewest(String id, Consumer<String> onFallback) {
        try {
            return forVersion(id);
        } catch (IllegalArgumentException e) {
            if (onFallback != null) {
                onFallback.accept(id);
            }
            return forVersion(GrammarVersions.newest());
        }
    }

    /**
     * The Java package of the generated classes for a version id, for example {@code co.elastic.grammar.v9_5}
     * for "9.5". Keep in sync with {@code grammarPackage} in {@code antlr/build.gradle}.
     */
    private static String packageFor(String id) {
        return GRAMMAR_PACKAGE_PREFIX + id.replace('.', '_');
    }

    private static EsqlGrammar load(String id) {
        if (!GrammarVersions.released().contains(id) && GrammarVersions.local().filter(id::equals).isEmpty()) {
            throw new IllegalArgumentException("No ES|QL grammar for version " + id);
        }
        String pkg = packageFor(id);
        try {
            Class<? extends Lexer> lexerClass =
                Class.forName(pkg + ".EsqlBaseLexer").asSubclass(Lexer.class);
            Class<? extends Parser> parserClass =
                Class.forName(pkg + ".EsqlBaseParser").asSubclass(Parser.class);
            String[] ruleNames = (String[]) parserClass.getField("ruleNames").get(null);
            return new EsqlGrammar(id, lexerClass.getConstructor(CharStream.class),
                parserClass.getConstructor(TokenStream.class), parserClass.getMethod("singleStatement"),
                ruleNames);
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalArgumentException("No usable ES|QL grammar for version " + id, e);
        }
    }

    public String id() {
        return id;
    }

    public Lexer newLexer(CharStream in, boolean devSyntax) {
        Lexer lexer = newInstance(lexerConstructor, in);
        ((LexerConfig) lexer).setEsqlConfig(new EsqlConfig(devSyntax));
        return lexer;
    }

    public Parser newParser(TokenStream tokens, boolean devSyntax) {
        Parser parser = newInstance(parserConstructor, tokens);
        ((ParserConfig) parser).setEsqlConfig(new EsqlConfig(devSyntax));
        return parser;
    }

    /** Runs the grammar's start rule on a parser made by {@link #newParser}. */
    public ParserRuleContext singleStatement(Parser parser) {
        try {
            return (ParserRuleContext) singleStatement.invoke(parser);
        } catch (InvocationTargetException e) {
            throw rethrow(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The index of a parser rule, or -1 when this version has no rule of that name. */
    public int ruleIndex(String ruleName) {
        return ruleIndexes.getOrDefault(ruleName, -1);
    }

    /** The symbolic name of a token type (for example {@code PIPE}), or null when it has none. */
    public String tokenName(Parser parser, int tokenType) {
        return parser.getVocabulary().getSymbolicName(tokenType);
    }

    private static <T> T newInstance(Constructor<T> constructor, Object arg) {
        try {
            return constructor.newInstance(arg);
        } catch (InvocationTargetException e) {
            throw rethrow(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RuntimeException rethrow(Throwable cause) {
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(cause);
    }
}
