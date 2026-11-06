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

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/** The IDE side of {@link GrammarFallback}: the log and notification for a grammar that cannot be loaded. */
public class ITGrammarFallbackNotificationTest extends BasePlatformTestCase {

    public void testLogAndNotifyShowsAWarning() {
        List<Notification> shown = new ArrayList<>();
        ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
            .subscribe(Notifications.TOPIC, new Notifications() {
                @Override
                public void notify(Notification notification) {
                    shown.add(notification);
                }
            });

        GrammarFallback.logAndNotify("9.7", "9.5", new IllegalArgumentException("No ES|QL grammar for version 9.7"));

        assertEquals(1, shown.size());
        Notification notification = shown.getFirst();
        assertEquals(GrammarFallback.NOTIFICATION_GROUP, notification.getGroupId());
        assertTrue(notification.getContent(), notification.getContent().contains("9.7"));
        assertTrue(notification.getContent(), notification.getContent().contains("9.5"));
    }
}
