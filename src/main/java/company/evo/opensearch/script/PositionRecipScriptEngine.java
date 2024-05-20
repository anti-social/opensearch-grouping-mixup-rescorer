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

package company.evo.opensearch.script;

import org.opensearch.script.ScriptContext;
import org.opensearch.script.ScriptEngine;
import org.opensearch.script.ScoreScript;

import java.util.Set;
import java.util.Collections;
import java.util.Map;

public class PositionRecipScriptEngine implements ScriptEngine {
    @Override
    public String getType() {
        return "grouping_mixup_scripts";
    }

    @Override
    public Set<ScriptContext<?>> getSupportedContexts() {
        return Collections.singleton(ScoreScript.CONTEXT);
    }

    @Override
    public <T> T compile(String name, String code, ScriptContext<T> context, Map<String, String> params) {
        if (!context.equals(ScoreScript.CONTEXT)) {
            throw new IllegalArgumentException(getType() + " scripts cannot be used for context [" + context.name + "]");
        }
        // we use the script "source" as the script identifier
        if ("position_recip".equals(code)) {
            ScoreScript.Factory factory = new PositionRecipScript.PositionRecipFactory();
            return context.factoryClazz.cast(factory);
        }
        throw new IllegalArgumentException("Unknown script name [" + code + "]");
    }
}
