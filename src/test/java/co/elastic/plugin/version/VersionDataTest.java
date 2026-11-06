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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VersionDataTest {

    /** The function names that CommonUtils.FUNCTIONS held before the lists became per version. */
    private static final List<String> FORMER_HARDCODED_FUNCTIONS = List.of(
        "ABS",
        "ACOS",
        "ASIN",
        "ATAN",
        "ATAN2",
        "BIT_LENGTH",
        "BUCKET",
        "BYTE_LENGTH",
        "CASE",
        "CATEGORIZE",
        "CBRT",
        "CEIL",
        "CIDR_MATCH",
        "COALESCE",
        "CONCAT",
        "COS",
        "COSH",
        "DATE_DIFF",
        "DATE_EXTRACT",
        "DATE_FORMAT",
        "DATE_PARSE",
        "DATE_TRUNC",
        "E",
        "ENDS_WITH",
        "EXP",
        "FLOOR",
        "FROM_BASE64",
        "GREATEST",
        "HASH",
        "HYPOT",
        "IP_PREFIX",
        "LEAST",
        "LEFT",
        "LENGTH",
        "LOCATE",
        "LOG",
        "LOG10",
        "LTRIM",
        "MATCH",
        "MV_APPEND",
        "MV_AVG",
        "MV_CONCAT",
        "MV_COUNT",
        "MV_DEDUPE",
        "MV_FIRST",
        "MV_LAST",
        "MV_MAX",
        "MV_MEDIAN",
        "MV_MEDIAN_ABSOLUTE_DEVIATION",
        "MV_MIN",
        "MV_PERCENTILE",
        "MV_PSERIES_WEIGHTED_SUM",
        "MV_SLICE",
        "MV_SORT",
        "MV_SUM",
        "MV_ZIP",
        "NOW",
        "PI",
        "POW",
        "QSTR",
        "REPEAT",
        "REPLACE",
        "REVERSE",
        "RIGHT",
        "ROUND",
        "RTRIM",
        "SIGNUM",
        "SIN",
        "SINH",
        "SPACE",
        "SPLIT",
        "SQRT",
        "STARTS_WITH",
        "ST_CONTAINS",
        "ST_DISJOINT",
        "ST_DISTANCE",
        "ST_ENVELOPE",
        "ST_INTERSECTS",
        "ST_WITHIN",
        "ST_X",
        "ST_XMAX",
        "ST_XMIN",
        "ST_Y",
        "ST_YMAX",
        "ST_YMIN",
        "SUBSTRING",
        "TAN",
        "TANH",
        "TAU",
        "TO_BASE64",
        "TO_BOOLEAN",
        "TO_CARTESIANPOINT",
        "TO_CARTESIANSHAPE",
        "TO_DATEPERIOD",
        "TO_DATETIME",
        "TO_DATE_NANOS",
        "TO_DEGREES",
        "TO_DOUBLE",
        "TO_GEOPOINT",
        "TO_GEOSHAPE",
        "TO_INTEGER",
        "TO_IP",
        "TO_LONG",
        "TO_LOWER",
        "TO_RADIANS",
        "TO_STRING",
        "TO_TIMEDURATION",
        "TO_UNSIGNED_LONG",
        "TO_UPPER",
        "TO_VERSION",
        "TRIM");

    @Test
    public void testNineFiveFunctionsContainEveryCurrentlyHardcodedName() {
        Set<String> missing = new HashSet<>(FORMER_HARDCODED_FUNCTIONS);
        missing.removeAll(VersionData.functions("9.5"));
        Assert.assertEquals(Set.of(), missing);
    }

    @Test
    public void testOlderVersionsHaveFewerFunctions() {
        Set<String> oldest = VersionData.functions("9.0");
        Set<String> newest = VersionData.functions("9.5");
        Assert.assertTrue(oldest.size() < newest.size());
        // a few functions were removed after 9.0, so only most of the names carry over
        Set<String> shared = new HashSet<>(oldest);
        shared.retainAll(newest);
        Assert.assertTrue(shared.size() > oldest.size() - 5);
        Assert.assertTrue(VersionData.functions("9.4").size() < newest.size());
        Assert.assertFalse(oldest.contains("ST_GEOHASH"));
        Assert.assertTrue(newest.contains("ST_GEOHASH"));
    }

    @Test
    public void testFunctionNamesAreUpperCase() {
        for (String name : VersionData.functions("9.3")) {
            Assert.assertEquals(name.toUpperCase(), name);
        }
    }

    /** A version added to the grammars must come with its data files, or selecting it fails at runtime. */
    @Test
    public void testEveryReleasedVersionHasFunctionsAndMetadata() {
        for (String id : GrammarVersions.released()) {
            for (String file : List.of("functions.txt", "metadata.txt")) {
                String path = "/co/elastic/plugin/data/" + id + "/" + file;
                Assert.assertNotNull("Missing resource " + path, VersionData.class.getResource(path));
            }
        }
    }

    @Test
    public void testMetadataOptionsAreNonEmptyAndContainId() {
        for (String id : List.of("9.0", "9.1", "9.2", "9.3", "9.4", "9.5")) {
            List<String> options = VersionData.metadataOptions(id);
            Assert.assertFalse(id, options.isEmpty());
            Assert.assertTrue(id, options.contains("_id"));
        }
        Assert.assertFalse(VersionData.metadataOptions("9.0").contains("_source"));
        Assert.assertTrue(VersionData.metadataOptions("9.5").contains("_source"));
    }

    @Test
    public void testIsAvailableForKeywordAndFunction() {
        Assert.assertTrue(VersionData.isAvailable("9.0", "from"));
        Assert.assertTrue(VersionData.isAvailable("9.0", "ABS"));
        Assert.assertTrue(VersionData.isAvailable("9.5", "st_geohash"));
        Assert.assertFalse(VersionData.isAvailable("9.0", "st_geohash"));
        Assert.assertFalse(VersionData.isAvailable("9.5", "no_such_thing"));
        Assert.assertFalse(VersionData.isAvailable("9.5", ""));
    }

    @Test
    public void testUnknownVersionUsesNewestData() {
        Assert.assertEquals(VersionData.functions("9.5"), VersionData.functions("local"));
        Assert.assertEquals(VersionData.metadataOptions("9.5"), VersionData.metadataOptions(null));
        Assert.assertTrue(VersionData.isAvailable("nope", "abs"));
    }

    @Test
    public void testIsKnownInAnyVersion() {
        Assert.assertTrue(VersionData.isKnownInAnyVersion("st_geohash"));
        Assert.assertTrue(VersionData.isKnownInAnyVersion("TERM"));
        Assert.assertTrue(VersionData.isKnownInAnyVersion("from"));
        Assert.assertFalse(VersionData.isKnownInAnyVersion("my_field"));
        Assert.assertFalse(VersionData.isKnownInAnyVersion("processing-commands"));
        Assert.assertFalse(VersionData.isKnownInAnyVersion(" "));
        Assert.assertFalse(VersionData.isKnownInAnyVersion(null));
    }
}
