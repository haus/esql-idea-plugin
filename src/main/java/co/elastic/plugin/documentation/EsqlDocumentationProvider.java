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
package co.elastic.plugin.documentation;

import co.elastic.plugin.version.GrammarSelection;
import co.elastic.plugin.version.VersionData;
import com.intellij.lang.Language;
import com.intellij.model.Pointer;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

import static co.elastic.plugin.CommonUtils.ESQL_SEPARATORS;
import static co.elastic.plugin.CommonUtils.checkEsqlCommentAbove;
import static co.elastic.plugin.CommonUtils.isKotlinString;

@SuppressWarnings("UnstableApiUsage")
public class EsqlDocumentationProvider implements DocumentationTargetProvider {

    @Override
    public @NotNull List<? extends @NotNull DocumentationTarget> documentationTargets(@NotNull PsiFile psiFile, int i) {
        var elementAtOffset = psiFile.findElementAt(i);
        // for kotlin, navigate up to the STRING_TEMPLATE element
        if (elementAtOffset.getLanguage().is(Language.findLanguageByID("kotlin"))) {
            elementAtOffset = elementAtOffset.getParent().getParent();
        }
        if (elementAtOffset == null || !("TEXT_BLOCK_LITERAL".equals(elementAtOffset.getNode().getElementType().toString())
                                         || isKotlinString(elementAtOffset)
                                         || elementAtOffset instanceof PsiPlainText)
            || !checkEsqlCommentAbove(elementAtOffset, i)) {
            return List.of();
        }

        String word = findWordAtCursor(psiFile, i);

        if (word == null || word.isEmpty()) {
            return List.of();
        }
        return List.of(new EsqlDocs(word.trim().toLowerCase()));
    }

    private static String findWordAtCursor(@NotNull PsiFile psiFile, int i) {

        var elementAtOffset = psiFile.findElementAt(i);
        if (elementAtOffset == null) {
            return null;
        }

        String text = elementAtOffset.getText();

        int relativeStartOffset = i - elementAtOffset.getTextRange().getStartOffset();

        // getting index of first character in the word
        char firstChar = text.charAt(relativeStartOffset);
        int firstCharIndex = relativeStartOffset;
        while (!ESQL_SEPARATORS.contains(String.valueOf(firstChar))) {
            firstCharIndex--;
            firstChar = text.charAt(firstCharIndex);
        }

        char lastChar = text.charAt(relativeStartOffset);
        int lastCharIndex = relativeStartOffset;
        while (!ESQL_SEPARATORS.contains(String.valueOf(lastChar))) {
            lastCharIndex++;
            lastChar = text.charAt(lastCharIndex);
        }

        return text.substring(firstCharIndex, lastCharIndex);
    }

    class EsqlDocs implements DocumentationTarget {

        protected final String word;

        public EsqlDocs(String word) {
            this.word = word;
        }

        @Override
        public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
            return (Pointer<DocumentationTarget>) () -> new EsqlDocs(word);
        }

        @Override
        public @NotNull TargetPresentation computePresentation() {
            return TargetPresentation.builder("").presentation();
        }

        @Override
        public @Nullable DocumentationResult computeDocumentation() {
            String unavailable = notAvailableHtml(GrammarSelection.current().version().id(), word);
            if (unavailable != null) {
                return DocumentationResult.documentation(unavailable);
            }
            return EsqlDocsMap.getDocforCommand(word);
        }
    }

    /**
     * The documentation to show when a command or function exists in some released version but not in the
     * selected one. Null for anything else, including words that are no command or function at all.
     */
    static @Nullable String notAvailableHtml(String versionId, String word) {
        if (VersionData.isAvailable(versionId, word) || !VersionData.isKnownInAnyVersion(word)) {
            return null;
        }
        return "<p>" + StringUtil.escapeXmlEntities(word.toUpperCase(Locale.ROOT))
               + " is not available in ES|QL " + StringUtil.escapeXmlEntities(versionId) + ".</p>";
    }
}


