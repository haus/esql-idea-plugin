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
import co.elastic.plugin.settings.EsqlPluginSettings;
import org.junit.Assert;
import org.junit.Test;

public class GrammarSelectionTest {

    @Test
    public void testOnWithoutServerEnablesDevSyntax() {
        EsqlPluginSettings s = new EsqlPluginSettings();
        s.devSyntax = "on";
        Assert.assertTrue(GrammarSelection.resolve(s, null).devSyntax());
    }

    @Test
    public void testAutoFollowsSnapshotFlag() {
        EsqlPluginSettings s = new EsqlPluginSettings();
        Assert.assertFalse(GrammarSelection.resolve(s, null).devSyntax());
        Assert.assertFalse(GrammarSelection.resolve(s, new ServerInfo("9.5.0", false)).devSyntax());
        Assert.assertTrue(GrammarSelection.resolve(s, new ServerInfo("9.5.0-SNAPSHOT", true)).devSyntax());
    }

    @Test
    public void testOffIgnoresSnapshot() {
        EsqlPluginSettings s = new EsqlPluginSettings();
        s.devSyntax = "off";
        Assert.assertFalse(GrammarSelection.resolve(s, new ServerInfo("9.5.0-SNAPSHOT", true)).devSyntax());
    }

    @Test
    public void testResolveUsesSettingsAndDetectedServer() {
        EsqlPluginSettings s = new EsqlPluginSettings();
        s.esqlVersion = "auto";
        Selection selection = GrammarSelection.resolve(s, new ServerInfo("9.2.1", false));
        Assert.assertEquals("9.2", selection.version().id());
    }

    @Test
    public void testResolveIsRecomputedPerCall() {
        EsqlPluginSettings s = new EsqlPluginSettings();
        Assert.assertEquals("9.1", GrammarSelection.resolve(s, new ServerInfo("9.1.0", false)).version().id());
        Assert.assertEquals("9.4", GrammarSelection.resolve(s, new ServerInfo("9.4.2", false)).version().id());
        Assert.assertEquals(GrammarVersions.newest(), GrammarSelection.resolve(s, null).version().id());
        s.esqlVersion = "9.0";
        Assert.assertEquals("9.0", GrammarSelection.resolve(s, new ServerInfo("9.4.2", false)).version().id());
    }

    @Test
    public void testStaleSavedVersionFallsBack() {
        EsqlPluginSettings s = new EsqlPluginSettings();
        s.esqlVersion = "9.7";
        Selection selection = GrammarSelection.resolve(s, null);
        Assert.assertEquals(GrammarVersions.newest(), selection.version().id());
        Assert.assertNotNull(selection.note());
        Assert.assertTrue(selection.note(), selection.note().contains("9.7"));
    }

    @Test
    public void testLocalSettingWithoutLocalGrammarFallsBack() {
        EsqlPluginSettings s = new EsqlPluginSettings();
        s.esqlVersion = "local";
        Selection selection = GrammarSelection.resolve(s, null, null);
        Assert.assertEquals(GrammarVersions.newest(), selection.version().id());
        Assert.assertFalse(selection.version().local());
        Assert.assertNotNull(selection.note());
    }

    @Test
    public void testLocalSettingUsesLocalGrammar() {
        EsqlPluginSettings s = new EsqlPluginSettings();
        s.esqlVersion = "local";
        GrammarVersion local = new GrammarVersion("local", "Local build (main @ 0123abcd)", true);
        Assert.assertEquals(local, GrammarSelection.resolve(s, null, local).version());
    }

    @Test
    public void testAutoNeverChoosesLocalGrammar() {
        EsqlPluginSettings s = new EsqlPluginSettings();
        GrammarVersion local = new GrammarVersion("local", "Local build (main @ 0123abcd)", true);
        Assert.assertFalse(GrammarSelection.resolve(s, null, local).version().local());
        Assert.assertFalse(GrammarSelection.resolve(s, new ServerInfo("9.9.0", false), local).version().local());
    }

    @Test
    public void testLocalVersionMatchesBuild() {
        GrammarVersion local = GrammarSelection.localVersion();
        if (GrammarVersions.local().isEmpty()) {
            Assert.assertNull(local);
        } else {
            Assert.assertEquals(GrammarVersions.LOCAL_ID, local.id());
            Assert.assertEquals(GrammarVersions.localLabel().orElseThrow(), local.label());
            Assert.assertTrue(local.local());
        }
    }

    @Test
    public void testHandEditedSettingIsNormalised() {
        EsqlPluginSettings s = new EsqlPluginSettings();
        ServerInfo server = new ServerInfo("9.2.1", false);
        s.esqlVersion = "Auto";
        Assert.assertEquals("9.2", GrammarSelection.resolve(s, server).version().id());
        s.esqlVersion = " 9.3 ";
        Selection selection = GrammarSelection.resolve(s, null);
        Assert.assertEquals("9.3", selection.version().id());
        Assert.assertNull(selection.note());
        s.esqlVersion = " ";
        Assert.assertEquals("9.2", GrammarSelection.resolve(s, server).version().id());
        s.esqlVersion = "LOCAL";
        GrammarVersion local = new GrammarVersion("local", "Local build (main @ 0123abcd)", true);
        Assert.assertEquals(local, GrammarSelection.resolve(s, null, local).version());
    }
}
