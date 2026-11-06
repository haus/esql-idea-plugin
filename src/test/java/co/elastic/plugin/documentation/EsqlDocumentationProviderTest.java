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
package co.elastic.plugin.documentation;

import org.junit.Assert;
import org.junit.Test;

public class EsqlDocumentationProviderTest {

    @Test
    public void testAvailableWordHasNoNote() {
        Assert.assertNull(EsqlDocumentationProvider.notAvailableHtml("9.5", "abs"));
        Assert.assertNull(EsqlDocumentationProvider.notAvailableHtml("9.0", "from"));
    }

    @Test
    public void testMissingWordSaysWhichVersionLacksIt() {
        String html = EsqlDocumentationProvider.notAvailableHtml("9.0", "st_geohash");
        Assert.assertNotNull(html);
        Assert.assertTrue(html, html.contains("ST_GEOHASH is not available in ES|QL 9.0"));
    }
}
