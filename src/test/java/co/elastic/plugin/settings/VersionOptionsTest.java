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

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class VersionOptionsTest {

    private static final List<String> RELEASED = List.of("9.0", "9.1", "9.5");

    private static List<String> values(List<VersionOption> options) {
        return options.stream().map(VersionOption::value).toList();
    }

    @Test
    public void testAutoThenReleasedVersions() {
        List<VersionOption> options = VersionOptions.options(RELEASED, null, "auto");
        Assert.assertEquals(List.of("auto", "9.0", "9.1", "9.5"), values(options));
        Assert.assertEquals("Auto", options.getFirst().label());
        Assert.assertEquals("9.1", options.get(2).label());
    }

    @Test
    public void testLocalEntryOnlyWithLocalGrammar() {
        List<VersionOption> options = VersionOptions.options(RELEASED, "Local build (main @ 0123abcd)", "auto");
        Assert.assertEquals(List.of("auto", "9.0", "9.1", "9.5", "local"), values(options));
        Assert.assertEquals("Local build (main @ 0123abcd)", options.getLast().label());
    }

    @Test
    public void testStaleSavedValueStaysSelectable() {
        List<VersionOption> options = VersionOptions.options(RELEASED, null, "9.7");
        Assert.assertEquals(List.of("auto", "9.0", "9.1", "9.5", "9.7"), values(options));
        Assert.assertTrue(options.getLast().label().contains("not available"));
        Assert.assertEquals("9.7", VersionOptions.find(options, "9.7").value());
    }

    @Test
    public void testSavedValueIsNormalised() {
        Assert.assertEquals("auto", VersionOptions.normalize(null));
        Assert.assertEquals("auto", VersionOptions.normalize(""));
        Assert.assertEquals("auto", VersionOptions.normalize("Auto"));
        Assert.assertEquals("local", VersionOptions.normalize(" LOCAL "));
        Assert.assertEquals("9.3", VersionOptions.normalize("9.3"));
        List<VersionOption> options = VersionOptions.options(RELEASED, null, "Auto");
        Assert.assertEquals(List.of("auto", "9.0", "9.1", "9.5"), values(options));
        Assert.assertEquals("auto", VersionOptions.find(options, "Auto").value());
    }
}
