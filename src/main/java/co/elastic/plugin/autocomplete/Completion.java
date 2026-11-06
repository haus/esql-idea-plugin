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
import co.elastic.grammar.GrammarVersions;
import co.elastic.grammar.completion.CodeCompletionCore;
import co.elastic.grammar.completion.CodeCompletionCore.CandidatesCollection;
import co.elastic.plugin.connection.EsqlSchemaProvider;
import co.elastic.plugin.version.GrammarVersion;
import co.elastic.plugin.version.Selection;
import co.elastic.plugin.version.VersionData;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record Completion(String text, Kind kind) {
    public enum Kind {
        KEYWORD,
        FUNCTION,
        PIPE,
        PLACEHOLDER,
        METADATA,
        NAME,
        FIELD,
    }

    // subquery is preferred because subqueries open with LP, which would otherwise be completed as the start
    // of a function call
    // the field name rules are added per grammar, see fieldNameRule and fieldNamePatternRule
    private static final List<String> PREFERRED_RULES = List.of("indexPattern", "metadataSource", "subquery");

    /**
     * The rule that holds a field name. 9.0 and 9.1 have no fieldName rule; their qualifiedName has the same
     * body. From 9.2 on qualifiedName wraps fieldName, so it is used only when fieldName is missing.
     */
    private static int fieldNameRule(EsqlGrammar grammar) {
        return ruleOrFallback(grammar, "fieldName", "qualifiedName");
    }

    /** Like {@link #fieldNameRule}, for field name patterns (qualifiedNamePattern in 9.0 and 9.1). */
    private static int fieldNamePatternRule(EsqlGrammar grammar) {
        return ruleOrFallback(grammar, "fieldNamePattern", "qualifiedNamePattern");
    }

    private static int ruleOrFallback(EsqlGrammar grammar, String rule, String fallback) {
        int index = grammar.ruleIndex(rule);
        return index >= 0 ? index : grammar.ruleIndex(fallback);
    }

    private static boolean isFieldRuleUsed(EsqlGrammar grammar, CandidatesCollection candidates,
                                           int caretTokenIndex, List<Token> allTokens) {
        return isRuleAlreadyUsed(candidates, fieldNameRule(grammar), caretTokenIndex, allTokens)
            || isRuleAlreadyUsed(candidates, fieldNamePatternRule(grammar), caretTokenIndex, allTokens);
    }

    /** The newest version with dev-only syntax off. Does not need a running IDE. */
    static Set<Completion> computeCompletions(String text, EsqlSchemaProvider queryManager) {
        String newest = GrammarVersions.newest();
        return computeCompletions(text, queryManager,
            new Selection(new GrammarVersion(newest, newest, false), false, null));
    }

    static Set<Completion> computeCompletions(String text, EsqlSchemaProvider queryManager,
                                              Selection selection) {
        var parserInfo = Parser.parse(text, selection);
        return computeCompletions(parserInfo, queryManager);
    }

    static Set<Completion> computeCompletions(Parser.ParserInfo parserInfo, EsqlSchemaProvider queryManager) {
        var tokens = parserInfo.tokens();
        var caretToken = tokens.isEmpty() ? null : tokens.getLast();
        int caretTokenIndex = caretToken != null ? caretToken.getTokenIndex() : 0;
        var candidates = completionCandidates(parserInfo);
        Set<Completion> completions = new HashSet<>();
        completions.addAll(computeTokenCompletions(parserInfo, candidates, caretTokenIndex, tokens));
        completions.addAll(computeSchemaDependentCompletions(parserInfo, candidates, queryManager,
            caretTokenIndex, tokens));
        return completions;
    }

    /**
     * Checks whether a rule candidate is relevant at the caret position. A candidate is filtered out when
     * its startTokenIndex is before the current caret, and there is a whitespace
     * token between them, meaning the user has moved past and the rule has been already matched.
     */
    private static boolean isRuleAlreadyUsed(CandidatesCollection candidates, int ruleIndex,
                                             int caretTokenIndex, List<Token> allTokens) {
        // -1: the grammar has no such rule
        if (ruleIndex < 0) return false;
        var rule = candidates.rules.get(ruleIndex);
        if (rule == null) return false;
        if (rule.startTokenIndex >= caretTokenIndex) return true;
        for (Token t : allTokens) {
            int idx = t.getTokenIndex();
            if (idx <= rule.startTokenIndex) continue;
            if (idx > caretTokenIndex) break;
            if (t.getChannel() != Token.DEFAULT_CHANNEL) return false;
        }
        return true;
    }

    private static Set<Completion> computeTokenCompletions(Parser.ParserInfo parserInfo,
                                                           CandidatesCollection candidates,
                                                           int caretTokenIndex, List<Token> allTokens) {
        Set<Completion> completions = new HashSet<>();
        EsqlGrammar grammar = parserInfo.grammar();
        var parser = parserInfo.parser();
        boolean devSyntax = parserInfo.devSyntax();

        if (isRuleAlreadyUsed(candidates, grammar.ruleIndex("indexPattern"), caretTokenIndex,
            allTokens)) {
            completions.add(new Completion("{string}", Kind.PLACEHOLDER));
        }

        if (isFieldRuleUsed(grammar, candidates, caretTokenIndex, allTokens)) {
            completions.add(new Completion("{var}", Kind.PLACEHOLDER));
        }

        if (isRuleAlreadyUsed(candidates, grammar.ruleIndex("metadataSource"), caretTokenIndex,
            allTokens)) {
            for (String opt : VersionData.metadataOptions(grammar.id())) {
                completions.add(new Completion(opt, Kind.METADATA));
            }
        }

        Set<Integer> tokenCompletions = candidates.tokens.keySet();

        for (Integer tokenType : tokenCompletions) {
            String name = grammar.tokenName(parser, tokenType);
            switch (name == null ? "" : name) {
                // replacing QUOTED_STRING and UNQUOTED_SOURCE with just "{string}"
                case "QUOTED_STRING":
                case "UNQUOTED_SOURCE":
                    completions.add(new Completion("{string}", Kind.PLACEHOLDER));
                    break;
                // replacing UNQUOTED_IDENTIFIER and QUOTED_IDENTIFIER with just {var}
                case "UNQUOTED_IDENTIFIER":
                case "QUOTED_IDENTIFIER":
                    completions.add(new Completion("{var}", Kind.PLACEHOLDER));
                    break;
                // replacing NAMED_OR_POSITIONAL_PARAM, NAMED_OR_POSITIONAL_DOUBLE_PARAMS and ID_PATTERN
                // with just {param}
                case "NAMED_OR_POSITIONAL_PARAM":
                case "NAMED_OR_POSITIONAL_DOUBLE_PARAMS":
                case "ID_PATTERN":
                    completions.add(new Completion("{param}", Kind.PLACEHOLDER));
                    break;
                // replacing DECIMAL_LITERAL with just {num}
                case "DECIMAL_LITERAL":
                    completions.add(new Completion("{num}", Kind.PLACEHOLDER));
                    break;
                // LP means functions, adding brackets to token
                case "LP":
                    for (String function : VersionData.functions(grammar.id())) {
                        completions.add(new Completion(function + "()", Kind.FUNCTION));
                    }
                    break;
                case "PIPE":
                    completions.add(new Completion("|", Kind.PIPE));
                    break;
                case "PARAM":
                case "DOUBLE_PARAMS":
                case "EOF":
                    break;
                default:
                    String display = parser.getVocabulary().getDisplayName(tokenType);
                    boolean devToken = display != null && display.startsWith("DEV_");
                    if (display != null && !display.isEmpty() && (!devToken || devSyntax)) {
                        String tokenText = (devToken ? display.substring("DEV_".length()) : display)
                            .replaceAll("'", "").toUpperCase(Locale.ROOT);
                        completions.add(new Completion(tokenText, Kind.KEYWORD));
                    }
            }
        }

        return completions;
    }

    private static Set<Completion> computeSchemaDependentCompletions(Parser.ParserInfo parserInfo,
                                                                     CandidatesCollection candidates,
                                                                     EsqlSchemaProvider queryManager,
                                                                     int caretTokenIndex,
                                                                     List<Token> allTokens) {
        Set<Completion> completions = new HashSet<>();
        if (queryManager == null) {
            return completions;
        }
        EsqlGrammar grammar = parserInfo.grammar();

        if (isRuleAlreadyUsed(candidates, grammar.ruleIndex("indexPattern"), caretTokenIndex,
            allTokens)) {
            for (String index : queryManager.getIndices()) {
                completions.add(new Completion(index, Kind.NAME));
            }
        }

        if (isFieldRuleUsed(grammar, candidates, caretTokenIndex, allTokens)) {
            var queriedIndexes = parserInfo.queriedIndexes();
            if (!queriedIndexes.isEmpty()) {
                String index = queriedIndexes.getLast();
                for (String field : queryManager.getFields(index)) {
                    completions.add(new Completion(field, Kind.FIELD));
                }
            }
        }

        return completions;
    }

    private static CandidatesCollection completionCandidates(Parser.ParserInfo parserInfo) {
        var parser = parserInfo.parser();
        var tokens = parserInfo.tokens();
        EsqlGrammar grammar = parserInfo.grammar();

        var codeCompletionCode = new CodeCompletionCore(parser);
        List<Integer> preferred = new ArrayList<>();
        for (String rule : PREFERRED_RULES) {
            preferred.add(grammar.ruleIndex(rule));
        }
        preferred.add(fieldNameRule(grammar));
        preferred.add(fieldNamePatternRule(grammar));
        for (int index : preferred) {
            // a version without the rule just gets no special handling for it
            if (index >= 0) {
                codeCompletionCode.preferredRules.add(index);
            }
        }
        codeCompletionCode.predicateContext = parserInfo.devSyntax() ? "dev" : "release";
        var caretIndex = tokens.size() - 1;
        return codeCompletionCode.collectCandidates(caretIndex, null);
    }
}
