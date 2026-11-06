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
package co.elastic.grammar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The ES|QL grammar versions this build has generated classes for, read from the {@code versions.txt} resource
 * that the build writes next to this class. It has a {@code released <id>} line per released version, oldest to
 * newest, and a {@code local <ref> <commit>} line when the build included a local grammar.
 */
public final class GrammarVersions {

    /** The id of the local grammar, when the build has one. */
    public static final String LOCAL_ID = "local";

    private static final String RESOURCE = "versions.txt";

    private static final Listing LISTING = parse(readResource());

    private GrammarVersions() {}

    /** Released version ids, oldest to newest. */
    public static List<String> released() {
        return LISTING.released();
    }

    public static String newest() {
        return LISTING.released().getLast();
    }

    /** The id of the local grammar, or empty when this build has none. */
    public static Optional<String> local() {
        return LISTING.local();
    }

    /** A label for the local grammar such as "Local build (upstream/main @ 0123abcd)", or empty. */
    public static Optional<String> localLabel() {
        return LISTING.localLabel();
    }

    /** The contents of {@code versions.txt}. */
    record Listing(List<String> released, Optional<String> localRef, Optional<String> localCommit) {

        Optional<String> local() {
            return localRef.map(ref -> LOCAL_ID);
        }

        Optional<String> localLabel() {
            return localRef.map(ref -> "Local build (" + ref + " @ " + localCommit.orElseThrow() + ")");
        }
    }

    static Listing parse(List<String> lines) {
        List<String> released = new ArrayList<>();
        String localRef = null;
        String localCommit = null;
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 2 && parts[0].equals("released")) {
                released.add(parts[1]);
            } else if (parts.length == 3 && parts[0].equals("local")) {
                localRef = parts[1];
                localCommit = parts[2];
            } else if (!line.isBlank()) {
                throw new IllegalStateException("Unexpected line in " + RESOURCE + ": " + line);
            }
        }
        if (released.isEmpty()) {
            throw new IllegalStateException("No released grammar versions in " + RESOURCE);
        }
        return new Listing(List.copyOf(released), Optional.ofNullable(localRef), Optional.ofNullable(localCommit));
    }

    private static List<String> readResource() {
        InputStream in = GrammarVersions.class.getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new IllegalStateException("Missing resource " + RESOURCE + "; the build writes it");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
