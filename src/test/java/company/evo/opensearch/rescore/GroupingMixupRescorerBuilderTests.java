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

import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.ParseField;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.opensearch.search.rescore.RescorerBuilder;
import org.opensearch.test.AbstractWireSerializingTestCase;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import static java.util.Collections.singletonList;

public class GroupingMixupRescorerBuilderTests extends AbstractWireSerializingTestCase<GroupingMixupRescorerBuilder> {
    @Override
    public NamedXContentRegistry xContentRegistry() {
        return new NamedXContentRegistry(singletonList(
                new NamedXContentRegistry.Entry(
                        RescorerBuilder.class,
                        new ParseField(GroupingMixupRescorerBuilder.NAME),
                        GroupingMixupRescorerBuilder::fromXContent
                )
        ));
    }

    @Override
    protected GroupingMixupRescorerBuilder createTestInstance() {
        String groupingField = randomAlphaOfLength(5);
        Map<String, Object> scriptParams = new HashMap<>();
        return new GroupingMixupRescorerBuilder(
                groupingField,
                new Script(ScriptType.INLINE, "grouping_mixup_scripts", "position_recip", scriptParams)
        )
                .windowSize(between(0, Integer.MAX_VALUE));
    }

    @Override
    protected Writeable.Reader<GroupingMixupRescorerBuilder> instanceReader() {
        return GroupingMixupRescorerBuilder::new;
    }

    public void testSerializationDeserialization() throws IOException {
        String json = "{" +
                "\"window_size\":5000," +
                "\"grouping_mixup\":{" +
                    "\"field\":\"manufacturer\"," +
                    "\"rescore_script\":{" +
                        "\"source\":\"position_recip\"," +
                        "\"lang\":\"grouping_mixup_scripts\"" +
                "}}}";
        XContentParser parser = createParser(JsonXContent.jsonXContent, json);
        assertEquals(XContentParser.Token.START_OBJECT, parser.nextToken());
        GroupingMixupRescorerBuilder rescorerBuilder = (GroupingMixupRescorerBuilder) RescorerBuilder.parseFromXContent(parser);
        XContentBuilder contentBuilder = JsonXContent.contentBuilder();
        rescorerBuilder.toXContent(contentBuilder, null);
        String serializedJson = BytesReference.bytes(contentBuilder).utf8ToString();
        assertEquals(json, serializedJson);
    }
}
