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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class ITEsqlSettingsConfigurableTest extends BasePlatformTestCase {

    private String savedVersion;
    private String savedDevSyntax;
    private EsqlSettingsConfigurable configurable;

    private static EsqlPluginSettings settings() {
        return ApplicationManager.getApplication().getService(EsqlPluginSettings.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        savedVersion = settings().esqlVersion;
        savedDevSyntax = settings().devSyntax;
        settings().esqlVersion = "auto";
        settings().devSyntax = "auto";
        configurable = new EsqlSettingsConfigurable();
        assertNotNull(configurable.createComponent());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            configurable.disposeUIResources();
            settings().esqlVersion = savedVersion;
            settings().devSyntax = savedDevSyntax;
        } finally {
            super.tearDown();
        }
    }

    private void selectVersion(String value) {
        configurable.versionCombo().setSelectedItem(
            VersionOptions.find(configurable.versionOptions(), value));
    }

    public void testVersionComboStartsOnSavedValue() {
        assertFalse(configurable.isModified());
        assertEquals("auto", configurable.selectedVersion());
    }

    public void testChangingVersionMarksModifiedAndApplyStoresIt() {
        selectVersion("9.3");
        assertTrue(configurable.isModified());
        configurable.apply();
        assertEquals("9.3", settings().esqlVersion);
        assertFalse(configurable.isModified());
    }

    public void testResetRestoresSavedVersion() {
        selectVersion("9.3");
        configurable.apply();
        selectVersion("9.0");
        assertTrue(configurable.isModified());
        configurable.reset();
        assertEquals("9.3", configurable.selectedVersion());
        assertFalse(configurable.isModified());
    }

    public void testNoteFollowsTheCombo() {
        String autoNote = configurable.noteText();
        assertTrue(autoNote, autoNote.contains("no connection"));
        selectVersion("9.1");
        assertTrue(configurable.noteText(), configurable.noteText().contains("9.1"));
        assertFalse(configurable.noteText().equals(autoNote));
    }

    public void testDevSyntaxStillApplied() {
        configurable.devSyntaxCombo().setSelectedItem("On");
        assertTrue(configurable.isModified());
        configurable.apply();
        assertEquals("on", settings().devSyntax);
        assertEquals("auto", settings().esqlVersion);
    }
}
