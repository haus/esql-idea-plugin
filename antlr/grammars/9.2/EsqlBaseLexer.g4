/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
// Synced from elastic/elasticsearch, x-pack/plugin/esql/src/main/antlr
//   ref: upstream/9.2 @ 819875014b5d (2026-05-11)
//   last commit touching the grammar: 7bf5c48e983a (2025-09-30)
// Local changes to re-apply when syncing (scripts/sync_grammar.py does this):
//   - EsqlBaseParser.g4: `metadata` uses a separate `metadataSource` rule (needed by autocomplete)
//   - predicates the plugin cannot evaluate (feature flags, EsqlCapabilities checks) call
//     this.isDevVersion() instead, and the EsqlCapabilities import is dropped:
//       (none in this version)
//   - EsqlBaseLexer.g4, EsqlBaseParser.g4: @header imports co.elastic.grammar.LexerConfig / ParserConfig
//   - LexerConfig.java: rewindToTokenStart() copied from ES (used by lexer/InExpression.g4)
lexer grammar EsqlBaseLexer;

@header {
/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
import co.elastic.grammar.LexerConfig;
}

options {
  superClass=LexerConfig;
  caseInsensitive=true;
}

/*
 * Before modifying this file or the files in the `lexer` subdirectory, please read
 * the section below as changes here  have significant impact in the ANTLR generated
 * code and its consumption in Kibana.
 *
 * A. To add a development command (only available behind in snapshot/dev builds)
 *
 * Since the tokens/modes are in development, add a predicate like this:
 * DEV_MYCOMMAND : {this.isDevVersion()}? 'mycommand' -> ...
 *
 * B. To add a new (production-ready) token
 *
 * Be sure to go through step A (add a development token).
 * Make sure to remove the prefix and conditional before promoting the tokens in
 * production.
 *
 * C. Renaming a token
 *
 * Avoid renaming the token. But if you really have to, please check with the
 * Kibana team as they might be using the generated ANTLR "dictionary".
 *
 * D. To remove a token
 *
 * If the tokens haven't made it to production (and make sure to double check),
 * simply remove them from the grammar.
 * If the tokens get promoted to release, check with the Kibana team the impact
 * they have on the UI (auto-completion, etc...)
 */

/*
 * Import lexer grammars for each command, making sure to import
 * the UNKNOWN_CMD token *last* because it takes over parsing for
 * all other commands.
 */
import ChangePoint,
       Enrich,
       Explain,
       Expression,
       From,
       Fork,
       Fuse,
       Inline,
       Join,
       Lookup,
       MvExpand,
       Project,
       Rename,
       Set,
       Show,
       UnknownCommand;

LINE_COMMENT
    : '//' ~[\r\n]* '\r'? '\n'? -> channel(HIDDEN)
    ;

MULTILINE_COMMENT
    : '/*' (MULTILINE_COMMENT|.)*? '*/' -> channel(HIDDEN)
    ;

WS
    : [ \r\n\t]+ -> channel(HIDDEN)
    ;
