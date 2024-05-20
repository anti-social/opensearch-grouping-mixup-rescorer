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

import org.opensearch.core.ParseField;
import org.opensearch.core.common.ParsingException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ConstructingObjectParser;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.fielddata.IndexFieldData;
import org.opensearch.index.query.QueryRewriteContext;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.script.ScoreScript;
import org.opensearch.script.Script;
import org.opensearch.search.rescore.RescoreContext;
import org.opensearch.search.rescore.RescorerBuilder;

import java.io.IOException;
import java.util.Objects;

public class GroupingMixupRescorerBuilder extends RescorerBuilder<GroupingMixupRescorerBuilder> {
    public static final String NAME = "grouping_mixup";
    private static ParseField GROUPING_FIELD_FIELD = new ParseField("field", "group_field");
    private static ParseField RESCORE_SCRIPT_FIELD = new ParseField("rescore_script", "decline_script");

    private static final ConstructingObjectParser<GroupingMixupRescorerBuilder, Void> PARSER =
           new ConstructingObjectParser<>(
                   NAME,
                   args -> new GroupingMixupRescorerBuilder((String) args[0], (Script) args[1])
           );
    static {
        PARSER.declareString(ConstructingObjectParser.constructorArg(), GROUPING_FIELD_FIELD);
        PARSER.declareObject(ConstructingObjectParser.constructorArg(), (p, c) -> Script.parse(p), RESCORE_SCRIPT_FIELD);
    }

    private final String groupByField;
    private final Script rescoreScript;

    GroupingMixupRescorerBuilder(String groupByField, Script rescoreScript) {
        super();
        this.groupByField = groupByField;
        this.rescoreScript = rescoreScript;
    }

    public GroupingMixupRescorerBuilder(StreamInput in) throws IOException {
        super(in);
        this.groupByField = in.readString();
        this.rescoreScript = new Script(in);
    }

    @Override
    public void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(groupByField);
        rescoreScript.writeTo(out);
    }

    @Override
    public void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.field(GROUPING_FIELD_FIELD.getPreferredName(), groupByField);
        builder.field(RESCORE_SCRIPT_FIELD.getPreferredName(), rescoreScript);
        builder.endObject();
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public RescorerBuilder<GroupingMixupRescorerBuilder> rewrite(QueryRewriteContext ctx) {
        return this;
    }

    @Override
    public RescoreContext innerBuildContext(int windowSize, QueryShardContext context) {
        IndexFieldData<?> groupingField =
                this.groupByField == null ? null : context.getForField(context.fieldMapper(this.groupByField));
        ScoreScript.LeafFactory scriptFactory = context.compile(rescoreScript, ScoreScript.CONTEXT)
                .newFactory(rescoreScript.getParams(), context.lookup(), context.searcher());
        return new GroupingMixupRescorer.Context(windowSize, groupingField, scriptFactory);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        GroupingMixupRescorerBuilder other = (GroupingMixupRescorerBuilder) obj;
        return groupByField.equals(other.groupByField)
                && rescoreScript.equals(other.rescoreScript);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), groupByField, rescoreScript);
    }

    public static GroupingMixupRescorerBuilder fromXContent(XContentParser parser)
            throws ParsingException
    {
        return PARSER.apply(parser, null);
    }
}
