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
import org.junit.Assert;
import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that version data lookups read the grammar's token resources without loading any generated lexer or
 * parser class: only the selected grammar should be loaded. Runs the lookups in a class loader of its own so the
 * classes other tests loaded do not count.
 */
public class VersionDataLoadingTest {

    /** A class loader over the plugin's and the grammar module's output that tells which classes it loaded. */
    private static final class IsolatedLoader extends URLClassLoader {
        IsolatedLoader(URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        boolean hasLoaded(String name) {
            return findLoadedClass(name) != null;
        }
    }

    /** The class path entry (directory or jar) that holds a resource. */
    private static URL entryOf(Class<?> owner, String resource) throws Exception {
        URL url = owner.getResource("/" + resource);
        Assert.assertNotNull("Missing resource " + resource, url);
        String s = url.toString();
        if (s.startsWith("jar:")) {
            return new URL(s.substring("jar:".length(), s.indexOf("!/")));
        }
        return new URL(s.substring(0, s.length() - resource.length()));
    }

    private static IsolatedLoader isolatedLoader() throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(entryOf(VersionData.class, "co/elastic/plugin/version/VersionData.class"));
        urls.add(entryOf(VersionData.class, "co/elastic/plugin/data/9.0/functions.txt"));
        urls.add(entryOf(GrammarVersions.class, "co/elastic/grammar/GrammarVersions.class"));
        urls.add(entryOf(GrammarVersions.class, "co/elastic/grammar/versions.txt"));
        urls.add(entryOf(org.antlr.v4.runtime.Lexer.class, "org/antlr/v4/runtime/Lexer.class"));
        return new IsolatedLoader(urls.stream().distinct().toArray(URL[]::new));
    }

    private static void assertNoGrammarClassLoaded(IsolatedLoader loader) {
        for (String id : GrammarVersions.released()) {
            String pkg = "co.elastic.grammar.v" + id.replace('.', '_');
            Assert.assertFalse("Loaded the " + id + " lexer", loader.hasLoaded(pkg + ".EsqlBaseLexer"));
            Assert.assertFalse("Loaded the " + id + " parser", loader.hasLoaded(pkg + ".EsqlBaseParser"));
        }
    }

    @Test
    public void testIsKnownInAnyVersionLoadsNoGrammarClass() throws Exception {
        try (IsolatedLoader loader = isolatedLoader()) {
            Class<?> data = loader.loadClass(VersionData.class.getName());
            Assert.assertNotSame(VersionData.class, data);
            // a field name: known nowhere, so every version is looked at
            Assert.assertEquals(false, data.getMethod("isKnownInAnyVersion", String.class)
                .invoke(null, "my_field"));
            Assert.assertEquals(true, data.getMethod("isKnownInAnyVersion", String.class)
                .invoke(null, "fork"));
            assertNoGrammarClassLoaded(loader);
        }
    }

    @Test
    public void testIsAvailableLoadsNoGrammarClass() throws Exception {
        try (IsolatedLoader loader = isolatedLoader()) {
            Class<?> data = loader.loadClass(VersionData.class.getName());
            var isAvailable = data.getMethod("isAvailable", String.class, String.class);
            Assert.assertEquals(true, isAvailable.invoke(null, "9.0", "from"));
            Assert.assertEquals(false, isAvailable.invoke(null, "9.0", "fork"));
            Assert.assertEquals(true, isAvailable.invoke(null, "9.5", "fork"));
            assertNoGrammarClassLoaded(loader);
        }
    }
}
