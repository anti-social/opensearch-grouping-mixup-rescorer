/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package company.evo.opensearch.rescore;

import company.evo.opensearch.plugin.GroupingMixupPlugin;

import org.hamcrest.Matcher;
import org.hamcrest.collection.ArrayMatching;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.MatchQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.plugins.Plugin;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.test.OpenSearchIntegTestCase;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.DoubleStream;

import static org.opensearch.cluster.metadata.IndexMetadata.SETTING_NUMBER_OF_SHARDS;
import static org.opensearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertAcked;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertHitCount;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertOrderedSearchHits;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class GroupingMixupRescorerIT extends OpenSearchIntegTestCase {
    private static final String DEBUG_SEP = "======================";

    private static final MatchQueryBuilder queryBuilder = QueryBuilders
            .matchQuery("name", "the quick brown");

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return List.of(
                GroupingMixupPlugin.class
        );
    }

    public void testEmptyIndex() throws IOException {
        assertAcked(prepareCreate("test")
                .setSettings(Settings.builder().put(SETTING_NUMBER_OF_SHARDS, 1))
                .setMapping(
                        jsonBuilder()
                        .startObject().startObject("properties")
                        .startObject("company_id")
                                .field("type", "integer")
                        .endObject()
                        .endObject().endObject()));
        ensureYellow();
        refresh();

        SearchResponse resp;
        MatchAllQueryBuilder queryBuilder = QueryBuilders.matchAllQuery();

        resp = client().prepareSearch()
                .setQuery(queryBuilder)
                .setRescorer(
                        new GroupingMixupRescorerBuilder(
                                "company_id",
                                new Script(
                                        ScriptType.INLINE,
                                        "grouping_mixup_scripts",
                                        "position_recip",
                                        Collections.emptyMap()))
                        .windowSize(5))
                .execute()
                .actionGet();
        assertHitCount(resp, 0);
    }

    public void testNoRescoring() throws IOException {
        createIndexAndPopulateDocs();

        SearchResponse resp = client().prepareSearch()
                .setQuery(queryBuilder)
                .execute()
                .actionGet();
        assertHitCount(resp, 4);
        SearchHits hits = resp.getHits();
        logger.info("{} {}", hits.getAt(0).getId(), hits.getAt(0).getScore());
        logger.info("{} {}", hits.getAt(1).getId(), hits.getAt(1).getScore());
        logger.info("{} {}", hits.getAt(2).getId(), hits.getAt(2).getScore());
        logger.info("{} {}", hits.getAt(3).getId(), hits.getAt(3).getScore());
        logger.info(DEBUG_SEP);
        assertOrderedSearchHits(resp, "1", "3", "4", "2");
    }

    public void testRescoringAllDocs() throws IOException {
        createIndexAndPopulateDocs();

        SearchResponse resp = client().prepareSearch()
                .setQuery(queryBuilder)
                .setRescorer(
                        new GroupingMixupRescorerBuilder(
                                "company_id",
                                new Script(
                                        ScriptType.INLINE,
                                        "grouping_mixup_scripts",
                                        "position_recip",
                                        Collections.emptyMap()))
                                .windowSize(5))
                .execute()
                .actionGet();
        assertHitCount(resp, 4);
        SearchHits hits = resp.getHits();
        logger.info("{} {}", hits.getAt(0).getId(), hits.getAt(0).getScore());
        logger.info("{} {}", hits.getAt(1).getId(), hits.getAt(1).getScore());
        logger.info("{} {}", hits.getAt(2).getId(), hits.getAt(2).getScore());
        logger.info("{} {}", hits.getAt(3).getId(), hits.getAt(3).getScore());
        logger.info(DEBUG_SEP);
        assertOrderedSearchHits(resp, "1", "4", "2", "3");
        assertOrderedSearchHitScores(resp, 0.5817465F, 0.2326796F, 0.2226915F, 0.2010630F);
    }

    public void testRescoringHitsAnotherOrder() throws IOException {
        createIndexAndPopulateDocs();

        SearchResponse resp = client().prepareSearch()
                .setQuery(
                        QueryBuilders.matchQuery("name", "quick huge fox")
                )
                .setRescorer(
                        new GroupingMixupRescorerBuilder(
                                "company_id",
                                new Script(
                                        ScriptType.INLINE,
                                        "grouping_mixup_scripts",
                                        "position_recip",
                                        Collections.emptyMap()))
                                .windowSize(5))
                .execute()
                .actionGet();
        assertHitCount(resp, 4);
        SearchHits hits = resp.getHits();
        logger.info("{} {}", hits.getAt(0).getId(), hits.getAt(0).getScore());
        logger.info("{} {}", hits.getAt(1).getId(), hits.getAt(1).getScore());
        logger.info("{} {}", hits.getAt(2).getId(), hits.getAt(2).getScore());
        logger.info("{} {}", hits.getAt(3).getId(), hits.getAt(3).getScore());
        logger.info(DEBUG_SEP);
        assertOrderedSearchHits(resp, "3", "2", "4", "1");
        assertOrderedSearchHitScores(resp, 0.45518538F, 0.31794363F, 0.10611849F, 0.053059246F);
    }

    public void testRescoringWithCustomScriptParams() throws IOException {
        createIndexAndPopulateDocs();

        Map<String, Object> scriptParams = new HashMap<>();
        scriptParams.put("m", 1.0);
        scriptParams.put("a", 1.0);
        scriptParams.put("b", 1.1);
        scriptParams.put("c", 0.090909);
        SearchResponse resp = client().prepareSearch()
                .setQuery(queryBuilder)
                .setRescorer(
                        new GroupingMixupRescorerBuilder(
                                "company_id",
                                new Script(
                                        ScriptType.INLINE,
                                        "grouping_mixup_scripts",
                                        "position_recip",
                                        scriptParams))
                                .windowSize(5))
                .execute()
                .actionGet();
        assertHitCount(resp, 4);
        SearchHits hits = resp.getHits();
        logger.info("{} {}", hits.getAt(0).getId(), hits.getAt(0).getScore());
        logger.info("{} {}", hits.getAt(1).getId(), hits.getAt(1).getScore());
        logger.info("{} {}", hits.getAt(2).getId(), hits.getAt(2).getScore());
        logger.info("{} {}", hits.getAt(3).getId(), hits.getAt(3).getScore());
        logger.info(DEBUG_SEP);
        assertOrderedSearchHits(resp, "1", "4", "3", "2");
        assertOrderedSearchHitScores(resp, 0.5817465F, 0.23267967F, 0.22804551F, 0.22269154F);
    }

    public void testRescoringWithSmallSize() throws IOException {
        createIndexAndPopulateDocs();

        SearchResponse resp = client().prepareSearch()
                .setQuery(queryBuilder)
                .setSize(2)
                .setRescorer(
                        new GroupingMixupRescorerBuilder(
                                "company_id",
                                new Script(
                                        ScriptType.INLINE,
                                        "grouping_mixup_scripts",
                                        "position_recip",
                                        Collections.emptyMap()))
                                .windowSize(5))
                .execute()
                .actionGet();
        assertHitCount(resp, 4);
        SearchHits hits = resp.getHits();
        logger.info("{} {}", hits.getAt(0).getId(), hits.getAt(0).getScore());
        logger.info("{} {}", hits.getAt(1).getId(), hits.getAt(1).getScore());
        logger.info(DEBUG_SEP);
        assertOrderedSearchHits(resp, "1", "4");
        assertOrderedSearchHitScores(resp, 0.5817466F, 0.2326797F);
    }

    public void testRescoringWithSmallRescoreWindow() throws IOException {
        createIndexAndPopulateDocs();

        SearchResponse resp = client().prepareSearch()
                .setQuery(queryBuilder)
                .setRescorer(
                        new GroupingMixupRescorerBuilder(
                                "company_id",
                                new Script(
                                        ScriptType.INLINE,
                                        "grouping_mixup_scripts",
                                        "position_recip",
                                        Collections.emptyMap()))
                        .windowSize(3))
                .execute()
                .actionGet();
        assertHitCount(resp, 4);
        SearchHits hits = resp.getHits();
        logger.info("{} {}", hits.getAt(0).getId(), hits.getAt(0).getScore());
        logger.info("{} {}", hits.getAt(1).getId(), hits.getAt(1).getScore());
        logger.info("{} {}", hits.getAt(2).getId(), hits.getAt(2).getScore());
        logger.info("{} {}", hits.getAt(3).getId(), hits.getAt(3).getScore());
        logger.info(DEBUG_SEP);
        assertOrderedSearchHits(resp, "1", "4", "3", "2");
        assertOrderedSearchHitScores(resp, 0.5817466F, 0.2326797F, 0.20106307F, 0.20106307F);
    }

    private void createIndexAndPopulateDocs() throws IOException {
        assertAcked(prepareCreate("test")
                .setSettings(Settings.builder().put(SETTING_NUMBER_OF_SHARDS, 1))
                .setMapping(
                        jsonBuilder().startObject().startObject("properties")
                                .startObject("name")
                                .field("type", "text")
                                .field("analyzer", "whitespace")
                                .endObject()
                                .startObject("company_id")
                                .field("type", "integer")
                                .endObject()
                                .endObject().endObject()));

        client().prepareIndex("test")
                .setId("1")
                .setSource(
                        "name", "the quick brown fox",
                        "company_id", 1)
                .execute()
                .actionGet();
        client().prepareIndex("test")
                .setId("2")
                .setSource(
                        "name", "the quick lazy huge fox jumps over the tree",
                        "company_id", 2)
                .execute()
                .actionGet();
        client().prepareIndex("test")
                .setId("3")
                .setSource(
                        "name", "quick huge brown fox",
                        "company_id", 1)
                .execute()
                .actionGet();
        client().prepareIndex("test")
                .setId("4")
                .setSource("name", "the quick lonely fox")
                .execute()
                .actionGet();
        ensureYellow();
        refresh();
    }

    public static void assertOrderedSearchHitScores(SearchResponse searchResponse, float... scores) {
        final var hits = searchResponse.getHits().getHits();
        assertThat(
            "Different hits length",
            hits.length,
            greaterThanOrEqualTo(scores.length)
        );

        final var originHitScores = new float[hits.length];
        final var hitScores = new Double[hits.length];
        for (int i = 0; i < hitScores.length; i++) {
            originHitScores[i] = hits[i].getScore();
            hitScores[i] = Double.valueOf(hits[i].getScore());
        }
        final List<Matcher<? super Double>> scoresMatchers = IntStream.range(0, scores.length)
            .mapToDouble(i -> scores[i])
            .boxed()
            .map(s -> closeTo(s, 1e-6))
            .collect(Collectors.toList());
        assertThat(
            String.format(Locale.ROOT, "Scores do not match: %s", Arrays.toString(originHitScores)),
            hitScores,
            ArrayMatching.arrayContaining(scoresMatchers)
        );
    }
}
