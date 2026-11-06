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
package co.elastic.plugin.execution;

import co.elastic.plugin.EditorRefresh;
import co.elastic.plugin.settings.EsqlConnection;
import co.elastic.plugin.settings.EsqlPluginSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;

import java.util.ArrayList;
import java.util.List;

public class ITEsqlResultsPanelTest extends BasePlatformTestCase {

    private List<EsqlConnection> savedConnections;
    private String savedActive;

    private static EsqlPluginSettings settings() {
        return ApplicationManager.getApplication().getService(EsqlPluginSettings.class);
    }

    private static EsqlConnection connection(String name) {
        EsqlConnection c = new EsqlConnection();
        c.name = name;
        c.serverUrl = "http://localhost:9200";
        c.apiKey = "key";
        return c;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        savedConnections = new ArrayList<>(settings().connections);
        savedActive = settings().activeConnectionName;
        settings().connections = new ArrayList<>(List.of(connection("first"), connection("second")));
        settings().activeConnectionName = "first";
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            settings().connections = savedConnections;
            settings().activeConnectionName = savedActive;
        } finally {
            super.tearDown();
        }
    }

    public void testSwitchingConnectionRefreshesEditors() {
        EsqlResultsPanel panel = new EsqlResultsPanel();
        @SuppressWarnings("unchecked")
        ComboBox<String> dropdown = UIUtil.findComponentOfType(panel, ComboBox.class);
        assertNotNull(dropdown);
        assertEquals("first", dropdown.getSelectedItem());

        int before = EditorRefresh.restartCount();
        dropdown.setSelectedItem("second");

        assertEquals("second", settings().activeConnectionName);
        assertEquals(before + 1, EditorRefresh.restartCount());
    }
}
