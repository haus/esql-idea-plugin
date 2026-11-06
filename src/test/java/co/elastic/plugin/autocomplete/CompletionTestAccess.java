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
package co.elastic.plugin.autocomplete;

import co.elastic.plugin.connection.EsqlSchemaProvider;
import co.elastic.plugin.version.Selection;

import java.util.Set;

/** Lets tests in other packages compute completions, which is package-private in production code. */
public final class CompletionTestAccess {

    private CompletionTestAccess() {}

    public static Set<Completion> computeCompletions(String text, EsqlSchemaProvider schema, Selection selection) {
        return Completion.computeCompletions(text, schema, selection);
    }
}
