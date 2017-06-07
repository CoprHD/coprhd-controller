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

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface MakeCustomServicesExecutor {

    public static boolean createOrderDir(final String orderDir) {
        final org.slf4j.Logger logger = LoggerFactory.getLogger(MakeCustomServicesExecutor.class);
        try {
            final File file = new File(orderDir);
            if (!file.exists()) {
                return file.mkdir();
            } else {
                logger.info("Order directory already exists: {}", orderDir);
                return true;
            }
        } catch (final Exception e) {
            logger.error("Failed to create directory" + e);
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("Failed to create Order directory " + orderDir);
        }
    }
    public ViPRExecutionTask<CustomServicesTaskResult> makeCustomServicesExecutor(final Map<String, List<String>> input, final CustomServicesWorkflowDocument.Step step);
    public String getType();
    public void setParam(Object object);
}
