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

import co.elastic.grammar.EsqlGrammar;
import co.elastic.grammar.GrammarVersions;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the grammar of the selected version. When that grammar cannot be loaded it uses the newest one, and logs
 * and shows a notification once per missing version id (not on every parse).
 */
public final class GrammarFallback {

    /** The notification group id registered in plugin.xml. */
    public static final String NOTIFICATION_GROUP = "ES|QL grammar";

    /** Told about a version whose grammar could not be loaded. */
    @FunctionalInterface
    interface Reporter {
        void report(String id, String newest, Throwable cause);
    }

    private static final GrammarFallback SHARED = new GrammarFallback(GrammarFallback::logAndNotify);

    private final Reporter reporter;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    GrammarFallback(Reporter reporter) {
        this.reporter = reporter;
    }

    /** The grammar of the selection's version, or the newest grammar when it cannot be loaded. */
    public static EsqlGrammar grammarFor(Selection selection) {
        return SHARED.grammarFor(selection.version().id());
    }

    EsqlGrammar grammarFor(String id) {
        return EsqlGrammar.forVersionOrNewest(id, (missing, cause) -> {
            if (reported.add(String.valueOf(missing))) {
                reporter.report(missing, GrammarVersions.newest(), cause);
            }
        });
    }

    static void logAndNotify(String id, String newest, Throwable cause) {
        String message = "Could not load the ES|QL grammar for version " + id + ", using " + newest + ".";
        Logger.getInstance(GrammarFallback.class).warn(message, cause);
        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification("ES|QL grammar", message, NotificationType.WARNING)
            .notify(null);
    }
}
