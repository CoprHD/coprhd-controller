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
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

public final class AnsibleHelper {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AnsibleHelper.class);

    private AnsibleHelper() {
    }

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
        final StringBuilder sb = new StringBuilder();

        if (step.getInputGroups().containsKey(CustomServicesConstants.INPUT_PARAMS)) {
            for (final CustomServicesWorkflowDocument.Input stepInput : step.getInputGroups()
                    .get(CustomServicesConstants.INPUT_PARAMS.toString()).getInputGroup()) {
                // Add only the extra-vars which is in the input_params to the extra-vars argument
                if (input.containsKey(stepInput.getName())) {
                    if (StringUtils.isNotBlank(stepInput.getType())
                            && stepInput.getType().equals(CustomServicesConstants.InputType.ASSET_OPTION_MULTI.toString())) {
                        // this is for array type. currently the only array type is InputType.ASSET_OPTION_MULTI. Do not think this can
                        // occur in anything other than input_params section. For array type the value is at index 0 with comma separated
                        // values (as sent from order form). Hence when we send to ansible we wrap it with [value]
                        sb.append(addExtraArg(stepInput.getName(), input.get(stepInput.getName()), true));
                    } else {
                        sb.append(addExtraArg(stepInput.getName(), input.get(stepInput.getName()), false));
                    }
                }
            }
        }
        logger.info("extra vars:{}", sb.toString());

        return sb.toString().trim();
    }

    private static StringBuilder addExtraArg(final String inputKey, final List<String> inputValue,
            final boolean arrayElement) {
        final StringBuilder sb = new StringBuilder();
        if (CollectionUtils.isNotEmpty(inputValue)) {
            sb.append(inputKey).append("=");

            if (inputValue.size() > 1 || arrayElement) {
                // for table support
                sb.append("[");
            }

            // Not wrapping with quotes. we will leave it to the ansible user.
            String prefix = "";
            for (String eachVal : inputValue) {
                // order ctx always sends only non-empty values. hence no check required for string being empty
                sb.append(prefix);
                prefix = ",";
                sb.append(eachVal.replace("\"", ""));
            }
            if (inputValue.size() > 1 || arrayElement) {
                sb.append("]");
            }
            sb.append(" ");
        }
        return sb;

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
