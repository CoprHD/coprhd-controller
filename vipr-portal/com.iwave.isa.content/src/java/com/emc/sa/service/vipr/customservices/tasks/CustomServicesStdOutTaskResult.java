/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.emc.sa.service.vipr.customservices.tasks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections.CollectionUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Output;

public class CustomServicesStdOutTaskResult extends CustomServicesTaskResult {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesStdOutTaskResult.class);
    
    public CustomServicesStdOutTaskResult(final List<Output> keys, final String out, final String err, final int retCode) {
        super(out, err, retCode, parseShellOutput(out, keys));
    }
    
    public static Map<String, List<String>> parseShellOutput(final String result, final List<Output> keys) {
        final String shellOutput = getShellOutput(result);
        final Map<String, List<String>> output = new HashMap<String, List<String>>();
        if(!CollectionUtils.isEmpty(keys)) {
            for(final Output key : keys) {
                output.put(key.getName(), parseShellOutput(shellOutput, key.getName()));
            }
        }
        return output;
    }
    
    private static List<String> parseShellOutput(final String result, final String key) {
        final List<String> out = new ArrayList<String>();

        final JsonNode node;
        try {
            node = new ObjectMapper().readTree(result);
        } catch (final IOException e) {
            logger.warn("Could not parse shell output" + e);
            return null;
        }

        final JsonNode arrNode = node.get(key);

        if (arrNode == null) {
            logger.warn("Could not find value for:{}", key);
            return null;
        }

        if (arrNode.isArray()) {
            for (final JsonNode objNode : arrNode) {
                out.add(objNode.toString());
            }
        } else {
            out.add(arrNode.toString());
        }

        logger.info("parsed result key:{} value:{}", key, out);

        return out;
    }

    public static String getShellOutput(final String out) {
        final String regexString = Pattern.quote("output_start") + "(?s)(.*?)" + Pattern.quote("output_end");
        final Pattern pattern = Pattern.compile(regexString);
        final Matcher matcher = pattern.matcher(out);

        final StringBuilder result = new StringBuilder("{");
        String prefix = "";
        while (matcher.find()) {
            result.append(prefix);
            prefix = ",";
            result.append(matcher.group(1));
        }

        result.append("}");

        return result.toString();
    }
}
