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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriTemplate;

import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.java.vipr.CustomServicesViPRPrimitive;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

public final class RESTHelper {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RESTHelper.class);

    private RESTHelper() {
    };

    /**
     * POST body format:
     * "{\n" +
     * " \"consistency_group\": \"$consistency_group\",\n" +
     * " \"count\": \"$count\",\n" +
     * " \"name\": \"$name\",\n" +
     * " \"project\": \"$project\",\n" +
     * " \"size\": \"$size\",\n" +
     * " \"varray\": \"$varray\",\n" +
     * " \"vpool\": \"$vpool\"\n" +
     * "}";
     * 
     * @param body
     * @return
     */
    public static String makePostBody(final String body, final int pos, final Map<String, List<String>> input) {

        logger.info("make body for" + body);
        final String[] strs = body.split("(?<=:)");

        for (int j = 0; j < strs.length; j++) {
            if (StringUtils.isEmpty(strs[j])) {
                continue;
            }

            if (!strs[j].contains("{")) {
                final String value = createBody(strs[j], pos, input);

                if (value.isEmpty()) {
                    final String[] ar = strs[j].split(",");
                    if (ar.length > 1) {
                        strs[j] = strs[j].replace(ar[0] + ",", "");
                        String[] pre = StringUtils.substringsBetween(strs[j - 1], "\"", "\"");
                        strs[j - 1] = strs[j - 1].replace("\"" + pre[pre.length - 1] + "\"" + ":", "");

                    } else {
                        String[] ar1 = strs[j].split("}");
                        strs[j] = strs[j].replace(ar1[0], "");
                        String[] pre = StringUtils.substringsBetween(strs[j - 1], "\"", "\"");
                        strs[j - 1] = strs[j - 1].replace("\"" + pre[pre.length - 1] + "\"" + ":", "");
                        for (int k = 1; k <= j; k++) {
                            if (!strs[j - k].trim().isEmpty()) {
                                strs[j - k] = strs[j - k].trim().replaceAll(",$", "");
                                break;
                            }
                        }
                    }
                } else {
                    strs[j] = value;
                }
                continue;
            }

            // Complex Array of Objects type
            if (strs[j].contains("[{")) {
                int start = j;
                final StringBuilder secondPart = new StringBuilder(strs[j].split("\\[")[1]);

                final String firstPart = strs[j].split("\\[")[0];
                j++;
                int count = -1;
                while (!strs[j].contains("}]")) {
                    // Get the number of Objects in array of object type
                    final int cnt = getCountofObjects(strs[j], input);
                    if (count < cnt) {
                        count = cnt;
                    }
                    secondPart.append(strs[j]);

                    j++;
                }
                final String[] splits = strs[j].split("\\}]");
                final String firstOfLastLine = splits[0];
                final String end = splits[1];
                secondPart.append(firstOfLastLine).append("}");

                int last = j;

                // join all the objects in an array
                strs[start] = firstPart + "[" + makeComplexBody(count, secondPart.toString(), input) + "]" + end;

                while (start + 1 <= last) {
                    strs[++start] = "";
                }
            }
        }

        logger.info("ViPR Request body" + joinStrs(strs));

        return joinStrs(strs);
    }

    public static String createBody(final String strs, final int pos, final Map<String, List<String>> input) {
        if ((!strs.contains("["))) {
            // Single type parameter
            return findReplace(strs, pos, false, input);
        } else {
            // Array type parameter
            return findReplace(strs, pos, true, input);
        }
    }

    public static int getCountofObjects(final String strs, final Map<String, List<String>> input) {
        final Matcher m = Pattern.compile("\\$([\\w\\.\\@]+)").matcher(strs);
        while (m.find()) {
            final String p = m.group(1);
            if (input.get(p) == null) {
                return -1;
            }
            return input.get(p).size();
        }

        return -1;
    }

    public static String joinStrs(final String[] strs) {
        final StringBuilder sb = new StringBuilder(strs[0]);
        for (int j = 1; j < strs.length; j++) {
            sb.append(strs[j]);
        }
        return sb.toString();
    }

    public static String makeComplexBody(final int vals, final String secondPart, final Map<String, List<String>> input) {
        String get = "";
        if (vals == -1) {
            logger.error("Cannot Build ViPR Request body");
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Cannot Build ViPR Request body");
        }
        for (int k = 0; k < vals; k++) {
            // Recur for number of Objects
            get = get + makePostBody(secondPart, k, input) + ",";
        }

        // remove the trailing "," of json body and return
        return get.replaceAll(",$", "");
    }

    public static String findReplace(final String str, final int pos, final boolean isArraytype, final Map<String, List<String>> input) {
        final Matcher m = Pattern.compile("\\$([\\w\\.\\@]+)").matcher(str);
        while (m.find()) {
            final String pat = m.group(0);
            final String pat1 = m.group(1);

            final List<String> val = input.get(pat1);
            final StringBuilder sb = new StringBuilder();
            String vals = "";
            if (val != null && pos < val.size() && !StringUtils.isEmpty(val.get(pos))) {
                if (!isArraytype) {
                    sb.append("\"").append(val.get(pos)).append("\"");
                    vals = sb.toString();

                } else {

                    final String temp = val.get(pos);
                    final String[] strs = temp.split(",");
                    for (int i = 0; i < strs.length; i++) {
                        sb.append("\"").append(strs[i]).append("\"").append(",");
                    }
                    final String value = sb.toString();

                    vals = value.replaceAll(",$", "");

                }
                return str.replace(pat, vals);
            } else {
                return "";
            }

        }

        return "";
    }

    /**
     * Example uri: "/block/volumes/{id}/findname/{name}?query1=value1";
     * @param templatePath
     * @return
     */
    public static String makePath(final String templatePath,final Map<String, List<String>> input,final CustomServicesViPRPrimitive primitive) {
        final UriTemplate template = new UriTemplate(templatePath);
        final List<String> pathParameters = template.getVariableNames();
        final Map<String, Object> pathParameterMap = new HashMap<String, Object>();

        for(final String key : pathParameters) {
            List<String> value = input.get(key);
            if (null == value) {
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Unfulfilled path parameter: " + key);
            }
            //TODO find a better fix
            pathParameterMap.put(key, value.get(0).replace("\"", ""));
        }

        final String path = template.expand(pathParameterMap).getPath();

        logger.info("URI string is: {}", path);

        final StringBuilder fullPath = new StringBuilder(path);

        if (primitive == null || primitive.input() == null) {
            return fullPath.toString();
        }

        final Map<String, List<InputParameter>> viprInputs = primitive.input();
        final List<InputParameter> queries = viprInputs.get(CustomServicesConstants.QUERY_PARAMS);

        String prefix = "?";
        for (final InputParameter a : queries) {
            if (input.get(a.getName()) == null) {
                logger.debug("Query parameter value is not set for:{}", a.getName());
                continue;
            }
            final String value = input.get(a.getName()).get(0);
            if (!StringUtils.isEmpty(value)) {
                fullPath.append(prefix).append(a.getName()).append("=").append(value);
                prefix = "&";
            }
        }

        logger.info("URI string with query:{}", fullPath.toString());

        return fullPath.toString();
    }
}
