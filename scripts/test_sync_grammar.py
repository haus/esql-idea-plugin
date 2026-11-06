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


class RenderHeaderTest(unittest.TestCase):
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
