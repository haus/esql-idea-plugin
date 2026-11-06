<img alt="Elastic logo" align="right" width="auto" height="auto" src="readme-resources/edti-logo.180x66.png">

# Elasticsearch ES|QL Intellij IDEA Plugin

Experimental plugin enabling autocompletion, syntax check, documentation and execution for [ES|QL](https://www.elastic.co/docs/reference/query-languages/esql) queries in Intellij IDEA for Java, Kotlin and text files.

Now available in the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/28898-elasticsearch-es-ql)!

## Activation

Comment `// ES|QL` above a text block (triple quotes) and the string will be identified as an ES|QL query.

![Screenshot](/readme-resources/activation-java.gif)

Same for text files, but you need to separate multiple queries with a semicolon.

![Screenshot](/readme-resources/activation-txt.gif)

## Autocompletion

Autocompletion will trigger automatically while typing, returning a list of acceptable commands/values to continue the query correctly. Alternatively, type `(ctrl + space)` to manually trigger autocompletion. 

![Screenshot](/readme-resources/autocomplete-txt.gif)

If connected to a server instance, the plugin will also fetch indices and field names and add them to the suggestion list where it's possible to use them.

![Screenshot](/readme-resources/autocomplete-java.gif)


## Syntax check

The plugin will highlight errors in queries, explaining what to fix.

![Screenshot](/readme-resources/syntax-check-java.gif)

## Documentation

Hovering with the cursor over commands will display documentation describing what the command can be used for and its correct syntax. 

![Screenshot](/readme-resources/docs-java.gif)

## Configurable with a server instance

By using the settings, the plugin can be connected to an Elasticsearch server instance to fetch indices and field names, which will be then added to the autocompletion options. 

![Screenshot](/readme-resources/connect.gif)

## Run queries

Once connected to an Elasticsearch server instance, queries can be executed by clicking on the green `Run` button. Results will be displayed in the plugin's tool window.

![Screenshot](/readme-resources/query-run-txt.gif)

### Test it locally

By running `./gradlew runIde` (if you want to test .kt files, Kotlin must be configured in the sandbox IDE).

## ES|QL grammar versions

The plugin ships one ES|QL grammar per released Elasticsearch minor version, under `antlr/grammars/<version>/`.
The build generates a parser for each of them. `scripts/sync_grammar.py` (Python 3, standard library only) copies the
grammar from an Elasticsearch checkout and re-applies the plugin's local adjustments, which are listed in the header
comment of the synced `EsqlBaseLexer.g4` and `EsqlBaseParser.g4`. The script only reads the checkout (through
`git archive`, `git log` and `git rev-parse`), so fetch the branches you need yourself first. It writes
`source.txt` (ref, commit, date) next to the grammar.

### Sync a released version

```
git -C ~/repos/elasticsearch fetch upstream
python3 scripts/sync_grammar.py --es-repo ~/repos/elasticsearch 9.5
```

This takes the tip of `upstream/9.5` (use `--remote` for another remote name, or `--commit SHA` to pin a commit)
and rewrites `antlr/grammars/9.5/`. Review the diff, run `./gradlew test` and commit. If an adjustment no longer
applies to the grammar, the script stops and names the one that failed.

### Add a new minor version

1. Add the version (for example `9.6`) as a new line in `scripts/grammar-versions.txt`.
2. Run `python3 scripts/sync_grammar.py --es-repo ~/repos/elasticsearch 9.6`.
3. Run `./gradlew test` and commit the new `antlr/grammars/9.6/` directory.

To refresh every version, run the script once for each line of `scripts/grammar-versions.txt`.

### Build with an unreleased grammar (`main`)

To try grammar changes that are not released yet, generate the grammar into `antlr/local/` in your own clone:

```
python3 scripts/sync_grammar.py --es-repo ~/repos/elasticsearch --local --ref upstream/main
# or, to use the files in the checkout's working tree (uncommitted edits included):
python3 scripts/sync_grammar.py --es-repo ~/repos/elasticsearch --local --worktree
./gradlew runIde        # try it in a sandbox IDE
./gradlew buildPlugin   # or build a plugin zip for your own use (see the guard below)
```

`--ref` accepts any branch, tag or commit. The build adds a "local" grammar version, which is never chosen
automatically. `antlr/local/` is gitignored. Release tasks (`buildPlugin` and `publishPlugin`) fail while it
exists, to keep a local grammar out of a published plugin. To build a zip anyway, delete `antlr/local/` first, or run
Gradle with `-x verifyNoLocalGrammar`. Delete the directory to go back to released grammars only.

A grammar from `main` can use predicates or helpers that the plugin's `LexerConfig` and `ParserConfig` classes do
not have yet. Then the Java compile step fails and the plugin needs a matching change.
