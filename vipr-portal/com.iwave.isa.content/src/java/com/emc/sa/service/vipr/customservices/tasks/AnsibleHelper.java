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
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnsibleHelper {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AnsibleHelper.class);

    private AnsibleHelper() {}

    public static String getOptions(final String key, final Map<String, List<String>> input) {

        logger.info(input.keySet().toString());

        if (input != null && input.get(key) != null && !StringUtils.isEmpty(input.get(key).get(0))) {
            return StringUtils.strip(input.get(key).get(0).toString(), "\"");
        }

        logger.info("key not defined. key:{}", key);

        return null;
    }

    /**
     * Ansible extra Argument format:
     * --extra_vars "key1=value1 key2=value2"
     *
     * @param input
     * @return
     * @throws Exception
     */
    public static String makeExtraArg(final Map<String, List<String>> input) throws Exception {
        if (input == null) {
            return null;
        }

        final StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : input.entrySet()) {
            // TODO find a better way to fix this
            if (e.getValue() != null && !StringUtils.isEmpty(e.getValue().get(0))) {
                sb.append(e.getKey()).append("=").append(e.getValue().get(0).replace("\"", "")).append(" ");
            }
        }
        logger.info("extra vars:{}", sb.toString());

        return sb.toString().trim();
    }

    public static String parseOut(final String out) {
        final String regexString = Pattern.quote("output_start") + "(?s)(.*?)" + Pattern.quote("output_end");
        final Pattern pattern = Pattern.compile(regexString);
        final Matcher matcher = pattern.matcher(out);

        while (matcher.find()) {
            return matcher.group(1);
        }

        return out;
    }

    public static void writeResourceToFile(final byte[] bytes, final String fileName) {
        try (FileOutputStream fileOuputStream = new FileOutputStream(fileName)) {
            fileOuputStream.write(bytes);
        } catch (final IOException e) {
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("Creating file failed with exception:" +
                            e.getMessage());
        }
    }
}
