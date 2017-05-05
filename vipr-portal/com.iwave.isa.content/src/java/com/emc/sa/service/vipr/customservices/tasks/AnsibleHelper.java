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

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

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
    public static String makeExtraArg(final Map<String, List<String>> input, final Step step) throws Exception {
        if (input == null) {
            return null;
        }

        final List<String> arrayInput = new ArrayList<>();
        final List<String> inputParamList = new ArrayList<>();

        for (final String inputGroupKey : step.getInputGroups().keySet()) {
            if (inputGroupKey.equals(CustomServicesConstants.INPUT_PARAMS)) {
                for (final CustomServicesWorkflowDocument.Input stepInput : step.getInputGroups().get(inputGroupKey).getInputGroup()) {
                    //Add only the extra-vars which is in the input_params to the extra-vars argument
                        inputParamList.add(stepInput.getName());
                    if (StringUtils.isNotBlank(stepInput.getType())
                            && stepInput.getType().equals(CustomServicesConstants.InputType.ASSET_OPTION_MULTI.toString())) {
                        //this is for array type. currently the only array type is InputType.ASSET_OPTION_MULTI. Do not think this can occur in anything other than input_params section
                        arrayInput.add(stepInput.getName());
                    }
                }
            }
        }

        final StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : input.entrySet()) {
            boolean arrayElement = false;
            if (!inputParamList.contains(e.getKey())) {
                continue;
            }

            if(arrayInput.contains(e.getKey())){
                arrayElement = true;
            }

            if (CollectionUtils.isNotEmpty(e.getValue())) {
                sb.append(e.getKey()).append("=");

                if (e.getValue().size() > 1 || arrayElement) {
                    // for table support
                    sb.append("[");
                }

                // Not wrapping with quotes. we will leave it to the ansible user.
                String prefix = "";
                for (String eachVal : e.getValue()) {
                    // order ctx always sends only non-empty values. hence no check required for string being empty
                    sb.append(prefix);
                    prefix = ",";
                    sb.append(eachVal.replace("\"", ""));
                }
                if (e.getValue().size() > 1 || arrayElement) {
                    sb.append("]");
                }
                sb.append(" ");
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
