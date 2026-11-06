#!/usr/bin/env python3
"""Copies the ES|QL grammar from an Elasticsearch checkout into this plugin.

Released versions go to antlr/grammars/<version>/, a local build (any ref, or the checkout's working tree) goes
to antlr/local/. The checkout is only read, through git archive/show/rev-parse/log or plain file reads.
Standard library only. See README.md.
"""
import argparse
import io
import re
import shutil
import subprocess
import sys
import tarfile
from pathlib import Path

ANTLR_DIR = "x-pack/plugin/esql/src/main/antlr"
TOP_LEVEL = ("EsqlBaseLexer.g4", "EsqlBaseParser.g4")
SUBDIRS = ("lexer", "parser")
REPO_ROOT = Path(__file__).resolve().parent.parent

LOCAL_CHANGES = """\
Local changes to re-apply when syncing (scripts/sync_grammar.py does this):
  - EsqlBaseParser.g4: `metadata` uses a separate `metadataSource` rule (needed by autocomplete)
  - predicates the plugin cannot evaluate (feature flags, EsqlCapabilities checks) call
    this.isDevVersion() instead, and the EsqlCapabilities import is dropped:"""
LOCAL_CHANGES_END = """\
  - EsqlBaseLexer.g4, EsqlBaseParser.g4: @header imports co.elastic.grammar.LexerConfig / ParserConfig
  - LexerConfig.java: rewindToTokenStart() copied from ES (used by lexer/InExpression.g4)"""

CAPABILITIES_IMPORT = re.compile(r"^[ \t]*import\s+[\w.]*\bEsqlCapabilities\s*;[ \t]*\n", re.MULTILINE)
PREDICATE = re.compile(r"\{([^{}]*)\}\?")
# Calls in a predicate that the plugin cannot evaluate: feature flag methods and capability checks.
UNSUPPORTED_CALL = re.compile(
    r"\bthis\.(?!isDevVersion\b)is\w+Enabled\s*\(\s*\)"
    r"|(?:\b[\w.]*\.)?\bEsqlCapabilities\.Cap\.\w+\.isEnabled\s*\(\s*\)"
)
METHOD_CALL = re.compile(r"(\w+)\s*\(")
# Methods a predicate may call, by the config class the generated lexer or parser extends.
LEXER_METHODS = frozenset({"isDevVersion", "isPromqlQuery"})
PARSER_METHODS = frozenset({"isDevVersion"})
DEV_CHECK = "this.isDevVersion()"
METADATA_RULE = re.compile(
    r"^metadata\n(\s*):\s*METADATA UNQUOTED_SOURCE \(COMMA UNQUOTED_SOURCE\)\*\n", re.MULTILINE
)
HEADER_START = re.compile(r"@header\s*\{")


class AdjustmentError(Exception):
    """A local adjustment no longer applies to the grammar; `name` says which one."""

    def __init__(self, name, detail=""):
        self.name = name
        super().__init__(f"Local adjustment '{name}' no longer applies: {detail}".rstrip(": "))


def _split_metadata(parser):
    if not METADATA_RULE.search(parser):
        raise AdjustmentError("metadataSource", "no `metadata` rule of the form METADATA UNQUOTED_SOURCE (COMMA UNQUOTED_SOURCE)*")
    return METADATA_RULE.sub(
        lambda m: "metadata\n{0}: METADATA metadataSource (COMMA metadataSource)*\n{0};\n\n"
        "metadataSource\n{0}: UNQUOTED_SOURCE\n".format(m.group(1).replace("\n", "")),
        parser,
        count=1,
    )


def _predicate_calls(text, pattern):
    for predicate in PREDICATE.finditer(text):
        for call in pattern.finditer(predicate.group(1)):
            yield call


def unsupported_predicates(files):
    """Maps each predicate call the plugin cannot evaluate to the sorted files it appears in."""
    found = {}
    for name, text in files.items():
        for call in _predicate_calls(text, UNSUPPORTED_CALL):
            found.setdefault(re.sub(r"\s+", "", call.group(0)), set()).add(name)
    return {call: sorted(names) for call, names in sorted(found.items())}


def _replace_unsupported_predicates(text):
    return PREDICATE.sub(lambda m: "{" + UNSUPPORTED_CALL.sub(DEV_CHECK, m.group(1)) + "}?", text)


def _allowed_methods(name):
    if name == "EsqlBaseLexer.g4" or name.startswith("lexer/"):
        return LEXER_METHODS
    if name == "EsqlBaseParser.g4" or name.startswith("parser/"):
        return PARSER_METHODS
    return frozenset()


def _check_no_unsupported_left(files):
    """Fails on anything the generated code could not compile against LexerConfig / ParserConfig."""
    for name, text in sorted(files.items()):
        if "EsqlCapabilities" in text:
            line = next(line.strip() for line in text.splitlines() if "EsqlCapabilities" in line)
            raise AdjustmentError("unsupported predicates", f"{name} still references EsqlCapabilities: {line}")
        allowed = _allowed_methods(name)
        for predicate in PREDICATE.finditer(text):
            for call in METHOD_CALL.finditer(predicate.group(1)):
                if call.group(1) not in allowed:
                    raise AdjustmentError(
                        "unsupported predicates",
                        f"{name} has a predicate the plugin cannot evaluate: {predicate.group(0)}",
                    )


def _add_header_import(text, name, class_name):
    match = HEADER_START.search(text)
    end = text.find("\n}", match.end()) if match else -1
    if end < 0:
        raise AdjustmentError(name, "no @header block in the grammar")
    return text[: end + 1] + f"import co.elastic.grammar.{class_name};\n" + text[end + 1 :]


def apply_local_adjustments(files):
    """files maps a path relative to the antlr directory to its text. Returns adjusted copies."""
    out = {name: CAPABILITIES_IMPORT.sub("", _replace_unsupported_predicates(text))
           for name, text in files.items()}
    for name, config in (("EsqlBaseLexer.g4", "LexerConfig"), ("EsqlBaseParser.g4", "ParserConfig")):
        if name not in out:
            raise AdjustmentError(config + " import", f"{name} is missing")
        out[name] = _add_header_import(out[name], config + " import", config)
    out["EsqlBaseParser.g4"] = _split_metadata(out["EsqlBaseParser.g4"])
    _check_no_unsupported_left(out)
    return out


def provenance_comment(ref, commit, date, grammar_commit, grammar_date, replaced=None):
    """replaced is what unsupported_predicates returned for the grammar."""
    replaced_lines = [f"      {', '.join(names)}: {call}" for call, names in (replaced or {}).items()]
    lines = (
        [
            "Synced from elastic/elasticsearch, x-pack/plugin/esql/src/main/antlr",
            f"  ref: {ref} @ {commit} ({date})",
            f"  last commit touching the grammar: {grammar_commit} ({grammar_date})",
        ]
        + LOCAL_CHANGES.splitlines()
        + (replaced_lines or ["      (none in this version)"])
        + LOCAL_CHANGES_END.splitlines()
    )
    return "".join(f"// {line}\n" for line in lines)


def add_provenance(text, comment):
    """Puts the comment after the leading license block comment."""
    end = text.find("*/")
    if not text.startswith("/*") or end < 0:
        raise AdjustmentError("provenance comment", "grammar does not start with a license block")
    split = end + 2
    if text[split : split + 1] == "\n":
        split += 1
    return text[:split] + comment + text[split:]


def check_version(version):
    if not re.fullmatch(r"\d+\.\d+", version):
        raise ValueError(f"Not a release version like 9.5: {version!r}")


def git(es_repo, *args, binary=False):
    result = subprocess.run(["git", "-C", str(es_repo), *args], capture_output=True, check=False)
    if result.returncode != 0:
        raise SystemExit(f"git {' '.join(args)} failed: {result.stderr.decode().strip()}")
    return result.stdout if binary else result.stdout.decode().strip()


def wanted(rel):
    parts = rel.split("/")
    if not rel.endswith(".g4"):
        return False
    return rel in TOP_LEVEL or (len(parts) == 2 and parts[0] in SUBDIRS)


def read_archive(es_repo, rev):
    data = git(es_repo, "archive", rev, ANTLR_DIR, binary=True)
    files = {}
    with tarfile.open(fileobj=io.BytesIO(data)) as tar:
        for member in tar.getmembers():
            rel = member.name[len(ANTLR_DIR) + 1 :] if member.name.startswith(ANTLR_DIR + "/") else ""
            if member.isfile() and wanted(rel):
                files[rel] = tar.extractfile(member).read().decode("utf-8")
    return files


def read_worktree(es_repo):
    root = Path(es_repo) / ANTLR_DIR
    files = {}
    for path in sorted(root.rglob("*.g4")):
        rel = path.relative_to(root).as_posix()
        if wanted(rel):
            files[rel] = path.read_text(encoding="utf-8")
    return files


def commit_info(es_repo, rev):
    commit = git(es_repo, "rev-parse", "--short=12", rev + "^{commit}")
    date = git(es_repo, "log", "-1", "--format=%cs", rev)
    grammar_commit = git(es_repo, "log", "-1", "--format=%H", rev, "--", ANTLR_DIR)[:12]
    grammar_date = git(es_repo, "log", "-1", "--format=%cs", rev, "--", ANTLR_DIR)
    return commit, date, grammar_commit, grammar_date


def write_output(out_dir, files, ref, commit, date):
    if out_dir.exists():
        shutil.rmtree(out_dir)
    for rel, text in files.items():
        path = out_dir / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    (out_dir / "source.txt").write_text(f"ref: {ref}\ncommit: {commit}\ndate: {date}\n", encoding="utf-8")


def sync(es_repo, out_dir, ref, rev=None, worktree=False):
    files = read_worktree(es_repo) if worktree else read_archive(es_repo, rev)
    if not files:
        raise SystemExit(f"No grammar files found under {ANTLR_DIR} in {es_repo}")
    commit, date, grammar_commit, grammar_date = commit_info(es_repo, "HEAD" if worktree else rev)
    replaced = unsupported_predicates(files)
    files = apply_local_adjustments(files)
    comment = provenance_comment(ref, commit, date, grammar_commit, grammar_date, replaced)
    for name in TOP_LEVEL:
        files[name] = add_provenance(files[name], comment)
    write_output(out_dir, files, ref, commit, date)
    print(f"Wrote {len(files)} grammar files to {out_dir} (ref {ref}, commit {commit})")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Sync the ES|QL grammar from an Elasticsearch checkout.")
    parser.add_argument("--es-repo", required=True, help="path to an Elasticsearch checkout (only read)")
    parser.add_argument("--remote", default="upstream", help="remote holding the release branches (default: upstream)")
    parser.add_argument("--commit", help="released version only: use this commit instead of the branch tip")
    parser.add_argument("--local", action="store_true", help="write antlr/local/ instead of a released version")
    parser.add_argument("--ref", help="with --local: git ref (branch, tag or commit) to take the grammar from")
    parser.add_argument("--worktree", action="store_true", help="with --local: use the checkout's working tree")
    parser.add_argument("version", nargs="?", help="released version such as 9.5")
    args = parser.parse_args(argv)

    if args.local:
        if args.version or args.commit:
            parser.error("--local takes --ref or --worktree, not a version or --commit")
        if bool(args.ref) == args.worktree:
            parser.error("--local needs exactly one of --ref and --worktree")
        sync(args.es_repo, REPO_ROOT / "antlr" / "local", args.ref or "worktree", args.ref, args.worktree)
    else:
        if not args.version or args.ref or args.worktree:
            parser.error("give a version such as 9.5 (or use --local)")
        try:
            check_version(args.version)
        except ValueError as e:
            parser.error(str(e))
        ref = f"{args.remote}/{args.version}"
        sync(args.es_repo, REPO_ROOT / "antlr" / "grammars" / args.version, ref, args.commit or ref)


if __name__ == "__main__":
    try:
        main()
    except AdjustmentError as e:
        sys.exit(f"error: {e}")
