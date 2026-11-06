import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import sync_grammar
from sync_grammar import AdjustmentError, apply_local_adjustments

LEXER = """lexer grammar EsqlBaseLexer;

@header {
import org.elasticsearch.xpack.esql.action.EsqlCapabilities;
}

options {
  superClass=LexerConfig;
}
"""

PARSER = """parser grammar EsqlBaseParser;

@header {
import org.elasticsearch.xpack.esql.action.EsqlCapabilities;
}

sourceCommand
    : fromCommand
    | {EsqlCapabilities.Cap.EXTERNAL_COMMAND.isEnabled()}? externalCommand
    ;

metadata
    : METADATA UNQUOTED_SOURCE (COMMA UNQUOTED_SOURCE)*
    ;
"""

FROM = "DEV_EXTERNAL : {EsqlCapabilities.Cap.EXTERNAL_COMMAND.isEnabled()}? 'external' -> pushMode(FROM_MODE);\n"


def sample():
    return {"EsqlBaseLexer.g4": LEXER, "EsqlBaseParser.g4": PARSER, "lexer/From.g4": FROM}


class ApplyLocalAdjustmentsTest(unittest.TestCase):
    def test_metadata_rule_is_split(self):
        out = apply_local_adjustments(sample())["EsqlBaseParser.g4"]
        self.assertIn("METADATA metadataSource (COMMA metadataSource)*", out)
        self.assertIn("metadataSource\n    : UNQUOTED_SOURCE\n    ;", out)

    def test_external_command_check_replaced_and_import_removed(self):
        out = apply_local_adjustments(sample())
        self.assertIn("{this.isDevVersion()}? externalCommand", out["EsqlBaseParser.g4"])
        self.assertIn("{this.isDevVersion()}? 'external'", out["lexer/From.g4"])
        for text in out.values():
            self.assertNotIn("EsqlCapabilities", text)

    def test_header_import_added_for_config_classes(self):
        out = apply_local_adjustments(sample())
        self.assertIn("import co.elastic.grammar.LexerConfig;\n}", out["EsqlBaseLexer.g4"])
        self.assertIn("import co.elastic.grammar.ParserConfig;\n}", out["EsqlBaseParser.g4"])
        self.assertNotIn("ParserConfig", out["EsqlBaseLexer.g4"])

    def test_input_is_not_modified(self):
        files = sample()
        apply_local_adjustments(files)
        self.assertEqual(sample(), files)

    def test_grammar_without_external_command_still_applies(self):
        files = sample()
        files["EsqlBaseParser.g4"] = PARSER.replace(
            "    | {EsqlCapabilities.Cap.EXTERNAL_COMMAND.isEnabled()}? externalCommand\n", ""
        ).replace("import org.elasticsearch.xpack.esql.action.EsqlCapabilities;\n", "")
        del files["lexer/From.g4"]
        out = apply_local_adjustments(files)
        self.assertIn("metadataSource", out["EsqlBaseParser.g4"])

    def test_adjustment_error_names_the_failed_step(self):
        files = sample()
        files["EsqlBaseParser.g4"] = PARSER.split("metadata\n")[0]
        with self.assertRaises(AdjustmentError) as ctx:
            apply_local_adjustments(files)
        self.assertIn("metadataSource", str(ctx.exception))


class UnsupportedPredicateTest(unittest.TestCase):
    def test_feature_flag_method_replaced_by_dev_check(self):
        files = sample()
        files["EsqlBaseParser.g4"] = PARSER.replace(
            "EsqlCapabilities.Cap.EXTERNAL_COMMAND.isEnabled()", "this.isExternalDataSourcesEnabled()"
        )
        files["lexer/From.g4"] = FROM.replace(
            "EsqlCapabilities.Cap.EXTERNAL_COMMAND.isEnabled()", "this.isExternalDataSourcesEnabled()"
        )
        out = apply_local_adjustments(files)
        self.assertIn("{this.isDevVersion()}? externalCommand", out["EsqlBaseParser.g4"])
        self.assertIn("{this.isDevVersion()}? 'external'", out["lexer/From.g4"])
        self.assertNotIn("isExternalDataSourcesEnabled", "".join(out.values()))

    def test_any_capability_check_replaced(self):
        files = sample()
        files["lexer/Other.g4"] = (
            "A : { EsqlCapabilities.Cap.FOO_BAR.isEnabled() }? 'a';\n"
            "B : {org.elasticsearch.xpack.esql.action.EsqlCapabilities.Cap.BAZ.isEnabled()}? 'b';\n"
            "C : {!EsqlCapabilities.Cap.QUX.isEnabled()}? 'c';\n"
        )
        out = apply_local_adjustments(files)["lexer/Other.g4"]
        self.assertIn("A : { this.isDevVersion() }? 'a'", out)
        self.assertIn("B : {this.isDevVersion()}? 'b'", out)
        self.assertIn("C : {!this.isDevVersion()}? 'c'", out)
        self.assertNotIn("EsqlCapabilities", out)

    def test_any_capabilities_import_dropped(self):
        files = sample()
        files["EsqlBaseLexer.g4"] = LEXER.replace(
            "import org.elasticsearch.xpack.esql.action.EsqlCapabilities;",
            "import org.elasticsearch.xpack.esql.capabilities.EsqlCapabilities;",
        )
        out = apply_local_adjustments(files)
        self.assertNotIn("EsqlCapabilities", out["EsqlBaseLexer.g4"])

    def test_dev_and_promql_predicates_kept(self):
        files = sample()
        files["lexer/Promql.g4"] = (
            "P : ')' {this.isPromqlQuery()}? {this.decPromqlDepth();} -> type(RP);\n"
            "D : {this.isDevVersion()}? 'd';\n"
        )
        out = apply_local_adjustments(files)
        self.assertEqual(files["lexer/Promql.g4"], out["lexer/Promql.g4"])

    def test_replacements_are_reported_per_call(self):
        files = sample()
        files["lexer/Other.g4"] = "A : {this.isFooEnabled()}? 'a';\n"
        replaced = sync_grammar.unsupported_predicates(files)
        self.assertEqual(
            {
                "EsqlCapabilities.Cap.EXTERNAL_COMMAND.isEnabled()": ["EsqlBaseParser.g4", "lexer/From.g4"],
                "this.isFooEnabled()": ["lexer/Other.g4"],
            },
            replaced,
        )

    def test_leftover_unknown_predicate_fails(self):
        files = sample()
        files["lexer/Other.g4"] = "A : {this.someOtherCheck()}? 'a';\n"
        with self.assertRaises(AdjustmentError) as ctx:
            apply_local_adjustments(files)
        self.assertIn("someOtherCheck", str(ctx.exception))
        self.assertIn("lexer/Other.g4", str(ctx.exception))

    def test_lexer_only_method_in_parser_fails(self):
        files = sample()
        files["parser/Promql.g4"] = "p : {this.isPromqlQuery()}? X;\n"
        with self.assertRaises(AdjustmentError) as ctx:
            apply_local_adjustments(files)
        self.assertIn("isPromqlQuery", str(ctx.exception))

    def test_leftover_capabilities_reference_fails(self):
        files = sample()
        files["parser/Other.g4"] = "x : {EsqlCapabilities.isSomething()}? X;\n"
        with self.assertRaises(AdjustmentError) as ctx:
            apply_local_adjustments(files)
        self.assertIn("EsqlCapabilities", str(ctx.exception))


class RenderHeaderTest(unittest.TestCase):
    def test_header_lists_each_replaced_predicate(self):
        text = sync_grammar.provenance_comment(
            ref="upstream/9.4", commit="0031350b4e3a", date="2026-11-05",
            grammar_commit="f65bf4192900", grammar_date="2026-10-21",
            replaced={"this.isExternalDataSourcesEnabled()": ["EsqlBaseParser.g4", "lexer/From.g4"]},
        )
        self.assertIn("EsqlBaseParser.g4, lexer/From.g4: this.isExternalDataSourcesEnabled()", text)
        self.assertTrue(all(line.startswith("//") for line in text.splitlines()))

    def test_header_says_when_nothing_was_replaced(self):
        text = sync_grammar.provenance_comment(
            ref="upstream/9.0", commit="48123dd234b7", date="2026-11-05",
            grammar_commit="f65bf4192900", grammar_date="2026-10-21", replaced={},
        )
        self.assertIn("none in this version", text)

    def test_header_records_source_and_local_changes(self):
        text = sync_grammar.provenance_comment(
            ref="upstream/9.5", commit="efb86f5984c1", date="2026-11-05",
            grammar_commit="f65bf4192900", grammar_date="2026-10-21",
        )
        self.assertIn("ref: upstream/9.5 @ efb86f5984c1 (2026-11-05)", text)
        self.assertIn("f65bf4192900 (2026-10-21)", text)
        self.assertIn("metadataSource", text)
        self.assertTrue(all(line.startswith("//") for line in text.splitlines()))

    def test_add_header_goes_after_license_block(self):
        src = "/*\n * license\n */\nparser grammar X;\n"
        out = sync_grammar.add_provenance(src, "// note\n")
        self.assertEqual("/*\n * license\n */\n// note\nparser grammar X;\n", out)


class VersionNameTest(unittest.TestCase):
    def test_only_minor_versions_are_accepted(self):
        sync_grammar.check_version("9.5")
        for bad in ("local", "9", "../9.5", "9.5.1"):
            with self.assertRaises(ValueError):
                sync_grammar.check_version(bad)


if __name__ == "__main__":
    unittest.main()
