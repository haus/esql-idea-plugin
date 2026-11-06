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
package co.elastic.plugin.settings;

import co.elastic.grammar.GrammarVersions;
import co.elastic.plugin.EditorRefresh;
import co.elastic.plugin.version.GrammarSelection;
import co.elastic.plugin.version.Selection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.List;
import java.util.Locale;

/**
 * Settings page under Tools | ES|QL.
 */
public class EsqlSettingsConfigurable implements Configurable {
    private static final String[] DEV_SYNTAX_OPTIONS = {"Auto", "On", "Off"};

    private ComboBox<VersionOption> version;
    private List<VersionOption> versionOptions = List.of();
    private JBLabel versionNote;
    private ComboBox<String> devSyntax;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "ES|QL";
    }

    @Override
    public @Nullable JComponent createComponent() {
        version = new ComboBox<>();
        versionNote = new JBLabel();
        devSyntax = new ComboBox<>(DEV_SYNTAX_OPTIONS);
        version.addActionListener(e -> updateNote());
        devSyntax.addActionListener(e -> updateNote());
        JPanel panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("ES|QL version:", version)
            .addComponentToRightColumn(versionNote)
            .addComponentToRightColumn(new JBLabel(
                "Auto uses the version of the active connection's server."))
            .addLabeledComponent("Dev-only syntax:", devSyntax)
            .addComponentToRightColumn(new JBLabel(
                "Auto turns it on only when the active connection is a snapshot build."))
            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();
        reset();
        return panel;
    }

    private static EsqlPluginSettings settings() {
        return ApplicationManager.getApplication().getService(EsqlPluginSettings.class);
    }

    private static String toOption(String stored) {
        for (String option : DEV_SYNTAX_OPTIONS) {
            if (option.equalsIgnoreCase(stored)) {
                return option;
            }
        }
        return DEV_SYNTAX_OPTIONS[0];
    }

    /** The setting value of the selected version entry. */
    String selectedVersion() {
        VersionOption selected = (VersionOption) version.getSelectedItem();
        return selected == null ? VersionOptions.normalize(null) : selected.value();
    }

    private String selectedDevSyntax() {
        return ((String) devSyntax.getSelectedItem()).toLowerCase(Locale.ROOT);
    }

    /** Shows what the values on the page resolve to against the active connection, before they are applied. */
    private void updateNote() {
        if (version == null || versionNote == null || devSyntax == null) {
            return;
        }
        EsqlPluginSettings preview = new EsqlPluginSettings();
        preview.esqlVersion = selectedVersion();
        preview.devSyntax = selectedDevSyntax();
        Selection selection = GrammarSelection.resolve(preview, GrammarSelection.detectedServer());
        versionNote.setText(selection.note() != null ? selection.note() : "Using " + selection.version().label());
    }

    @Override
    public boolean isModified() {
        if (version == null || devSyntax == null) {
            return false;
        }
        return !VersionOptions.normalize(settings().esqlVersion).equals(selectedVersion())
            || !toOption(settings().devSyntax).equals(devSyntax.getSelectedItem());
    }

    @Override
    public void apply() {
        if (version == null || devSyntax == null) {
            return;
        }
        settings().esqlVersion = selectedVersion();
        settings().devSyntax = selectedDevSyntax();
        EditorRefresh.restartAll();
    }

    @Override
    public void reset() {
        if (version == null || devSyntax == null) {
            return;
        }
        String stored = settings().esqlVersion;
        versionOptions = VersionOptions.options(GrammarVersions.released(),
            GrammarVersions.localLabel().orElse(null), stored);
        version.setModel(new DefaultComboBoxModel<>(versionOptions.toArray(new VersionOption[0])));
        version.setSelectedItem(VersionOptions.find(versionOptions, stored));
        devSyntax.setSelectedItem(toOption(settings().devSyntax));
        updateNote();
    }

    @Override
    public void disposeUIResources() {
        version = null;
        versionNote = null;
        devSyntax = null;
    }

    ComboBox<VersionOption> versionCombo() {
        return version;
    }

    List<VersionOption> versionOptions() {
        return versionOptions;
    }

    ComboBox<String> devSyntaxCombo() {
        return devSyntax;
    }

    String noteText() {
        return versionNote.getText();
    }
}
