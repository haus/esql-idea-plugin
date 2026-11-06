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

import co.elastic.plugin.version.VersionResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Maps the "ES|QL version" setting values to the entries of the settings page combo. */
final class VersionOptions {

    private VersionOptions() {}

    /**
     * Auto, each released version, and the local build when there is one ({@code localLabel} not null). A saved
     * value that matches none of them (for example a version a newer plugin offered) is kept as an extra entry,
     * so opening the page does not change it.
     */
    static List<VersionOption> options(List<String> released, String localLabel, String stored) {
        List<VersionOption> options = new ArrayList<>();
        options.add(new VersionOption(VersionResolver.AUTO, "Auto"));
        for (String id : released) {
            options.add(new VersionOption(id, id));
        }
        if (localLabel != null) {
            options.add(new VersionOption(VersionResolver.LOCAL, localLabel));
        }
        String value = normalize(stored);
        if (options.stream().noneMatch(o -> o.value().equals(value))) {
            options.add(new VersionOption(value, value + " (not available)"));
        }
        return List.copyOf(options);
    }

    /** The entry for a stored value, or the first entry (Auto) when there is none. */
    static VersionOption find(List<VersionOption> options, String stored) {
        String value = normalize(stored);
        return options.stream().filter(o -> o.value().equals(value)).findFirst().orElse(options.getFirst());
    }

    /** The stored value in lower case, with an empty value meaning Auto. */
    static String normalize(String stored) {
        if (stored == null || stored.isBlank()) {
            return VersionResolver.AUTO;
        }
        return stored.trim().toLowerCase(Locale.ROOT);
    }
}
