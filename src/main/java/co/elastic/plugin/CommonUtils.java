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
package co.elastic.plugin;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainText;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CommonUtils {

    public static final String ESQL_SEPARATORS = "\"'[ ()=]+\n";
    public static final String ESQL_COMMENT = "// ES|QL";
    public static final String[] SOURCE_COMMANDS = new String[]{"FROM", "ROW", "SHOW"};
    public static final String[] PROCESSING_COMMANDS = new String[]{"DISSECT", "DROP", "ENRICH", "EVAL",
        "GROK", "LOOKUP JOIN", "KEEP", "LIMIT", "MV_EXPAND", "RENAME", "SORT", "STATS", "WHERE"};

    // checking if there's a comment above the text block, and if it's marked with "ES|QL"
    public static boolean checkEsqlCommentAbove(PsiElement element) {
        if (element != null) {
            Project project = element.getProject();
            PsiFile psiFile = element.getContainingFile();
            Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);

            if (document != null) {
                int offset = element.getTextRange().getStartOffset();
                int line = document.getLineNumber(offset);
                if (line > 0) {
                    int prevLineStart = document.getLineStartOffset(line - 1);
                    // get the element at the end of the previous line
                    // -1 to avoid out of bounds
                    int prevLineEnd = document.getLineEndOffset(line - 1);
                    String prevLineText = document.getText(new TextRange(prevLineStart, prevLineEnd));
                    return prevLineText.trim().startsWith(ESQL_COMMENT);
                }
            }
        }
        return false;
    }

    // finds all ES|QL blocks in a plain text file
    // each block starts on the line after a "// ES|QL" comment and ends at the next newline with a semicolon
    public static List<TextRange> findEsqlBlocksInPlainText(PsiElement element) {
        List<TextRange> ranges = new ArrayList<>();
        if (!(element instanceof PsiPlainText)) return ranges;

        Project project = element.getProject();
        PsiFile psiFile = element.getContainingFile();
        Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
        if (document == null) return ranges;

        int totalLines = document.getLineCount();
        for (int i = 0; i < totalLines; i++) {
            int lineStart = document.getLineStartOffset(i);
            int lineEnd = document.getLineEndOffset(i);
            String lineText = document.getText(new TextRange(lineStart, lineEnd)).trim();

            if (lineText.startsWith(ESQL_COMMENT)) {
                int blockStart = -1;
                int blockEnd = -1;
                i++;
                while (i < totalLines) {
                    lineStart = document.getLineStartOffset(i);
                    lineEnd = document.getLineEndOffset(i);
                    lineText = document.getText(new TextRange(lineStart, lineEnd)).trim();
                    if (lineText.equals(";")) break;
                    if (blockStart == -1) blockStart = document.getLineStartOffset(i);
                    blockEnd = document.getLineEndOffset(i);
                    i++;
                }
                if (blockStart != -1) {
                    ranges.add(new TextRange(blockStart, blockEnd));
                }
            }
        }
        return ranges;
    }

    public static boolean checkEsqlCommentAbove(PsiElement element, int offset) {
        if (element instanceof PsiPlainText) {
            return findEsqlBlocksInPlainText(element).stream()
                .anyMatch(range -> range.getStartOffset() <= offset && offset <= range.getEndOffset());
        }
        return checkEsqlCommentAbove(element);
    }

    public static boolean isKotlinString(PsiElement element) {
        return Optional.ofNullable(element)
            .map(x -> x.toString().equals("STRING_TEMPLATE"))
            .orElse(false);
    }

    public static boolean isEsqlTextBlock(@NotNull PsiElement element) {
        if (element == null || element.getNode() == null) return false;
        IElementType type = element.getNode().getElementType();
        if (type == TokenType.WHITE_SPACE) {
            return false;
        }

        // it's a literal expression (Java text block with triple quotes)
        PsiElement firstChild = element.getFirstChild();
        if (firstChild != null && firstChild.getNode() != null
            && "TEXT_BLOCK_LITERAL".equals(firstChild.getNode().getElementType().toString())) {
            return checkEsqlCommentAbove(element);
        }

        // STRING_TEMPLATE to match kotlin triple quote
        if (element.toString().equals("STRING_TEMPLATE")) {
            return checkEsqlCommentAbove(element);
        }

        return false;
    }

    public static ElasticsearchClient createClientInstance(String url, String apikey) {
        return ElasticsearchClient.of(b -> b
            .host(url)
            .apiKey(apikey)
            .transportOptions(t -> t
                .setHeader("Content-Type", "application/vnd.elasticsearch+json; compatible-with=8")
                .setHeader("Accept", "application/vnd.elasticsearch+json; compatible-with=8")
            )
        );
    }
}
