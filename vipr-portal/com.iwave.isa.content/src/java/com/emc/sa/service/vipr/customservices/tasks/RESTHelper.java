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

import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RESTHelper {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RESTHelper.class);
    /**
     * POST body format:
     *  "{\n" +
     "  \"consistency_group\": \"$consistency_group\",\n" +
     "  \"count\": \"$count\",\n" +
     "  \"name\": \"$name\",\n" +
     "  \"project\": \"$project\",\n" +
     "  \"size\": \"$size\",\n" +
     "  \"varray\": \"$varray\",\n" +
     "  \"vpool\": \"$vpool\"\n" +
     "}";
     * @param body
     * @return
     */
    public static String makePostBody(String body, Map<String, List<String>> input) {
      /*body = "{\n" +
                "  \"consistency_group\": $consistency_group,\n" +
                "  \"count\": $count,\n" +
                "  \"name\": $name,\n" +
                "  \"project\": $project,\n" +
                "  \"size\": $size,\n" +
                "  \"varray\": $varray,\n" +
                "  \"vpool\": $vpool\n" +
                "}";*/
        body = "{\n" +
                "  \"name\": $name,\n" +
                "  \"owner\": $owner\n" +
                "}\n";
        logger.info("Body is:{}", body);
        Matcher m = Pattern.compile("\\$(\\w+)").matcher(body);

        while (m.find()) {
            String pat = m.group(1);
            String newpat = "$" + pat;
            if (input.get(pat) == null || input.get(pat).get(0) == null) {
                logger.info("input.get(pat) is null for pat:{}", pat);
                body = body.replace(newpat, "\"" + " " + "\"");
            } else {
                logger.info("pat:{} new pat:{}", input.get(pat).get(0), newpat);
                body = body.replace(newpat, "\"" + input.get(pat).get(0).replace("\"", "") + "\"");
            }
        }
        logger.info("New body:{}", body);
        return body;
    }

    /**
     * Example uri: "/block/volumes/{id}/findname/{name}";
     * @param templatePath
     * @return
     */
    public static String makePath(String templatePath, Map<String, List<String>> input) {
        logger.info("path is:{}", templatePath);
        final UriTemplate template = new UriTemplate(templatePath);
        final List<String> pathParameters = template.getVariableNames();
        final Map<String, Object> pathParameterMap = new HashMap<String, Object>();

        for(final String key : pathParameters) {
            List<String> value = input.get(key);
            logger.info("path key:{} value:{}", key, value);
            if(null == value) {
                logger.info("value is null");
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Unfulfilled path parameter: " + key);
            }
            //TODO find a better fix
            pathParameterMap.put(key, value.get(0).replace("\"",""));
        }

        final String path = template.expand(pathParameterMap).getPath();

        logger.info("URI string is: {}", path);

        return path;
    }
}
