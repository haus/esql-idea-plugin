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
package co.elastic.plugin.version;

import co.elastic.grammar.GrammarVersions;
import co.elastic.plugin.connection.EsqlPluginQueryManager;
import co.elastic.plugin.settings.EsqlPluginSettings;
import com.intellij.openapi.application.ApplicationManager;

import java.util.List;
import java.util.Locale;

/**
 * Works out which grammar settings apply right now, from the plugin settings and the active connection.
 */
public final class GrammarSelection {
    private static final List<GrammarVersion> RELEASED = GrammarVersions.released().stream()
        .map(id -> new GrammarVersion(id, id, false))
        .toList();
    private static final GrammarVersion LOCAL = GrammarVersions.local()
        .map(id -> new GrammarVersion(id, GrammarVersions.localLabel().orElse(id), true))
        .orElse(null);

    private GrammarSelection() {}

    /** Resolves the settings against the active connection. Not cached: call it each time it is needed. */
    public static Selection current() {
        EsqlPluginSettings settings = ApplicationManager.getApplication().getService(EsqlPluginSettings.class);
        return resolve(settings, detectedServer());
    }

    /** The version detected on the active connection, or null when it is not connected. */
    public static ServerInfo detectedServer() {
        EsqlPluginQueryManager manager =
            ApplicationManager.getApplication().getService(EsqlPluginQueryManager.class);
        return manager == null ? null : manager.getActiveServerInfo();
    }

    public static Selection resolve(EsqlPluginSettings settings, ServerInfo detected) {
        return resolve(settings, detected, LOCAL);
    }

    static Selection resolve(EsqlPluginSettings settings, ServerInfo detected, GrammarVersion local) {
        // Hand-edited values such as "Auto" or " 9.3 " mean the same as on the settings page
        String version = settings.esqlVersion == null ? null : settings.esqlVersion.trim().toLowerCase(Locale.ROOT);
        return VersionResolver.resolve(version, DevSyntaxMode.fromSetting(settings.devSyntax),
            detected, RELEASED, local);
    }

    /** The local grammar of this build, or null when it has none. */
    static GrammarVersion localVersion() {
        return LOCAL;
    }
}
