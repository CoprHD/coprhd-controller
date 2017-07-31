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

import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.catalog.primitives.CustomServicesRESTApiPrimitiveDAO;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.db.restapi.CustomServicesRESTApiPrimitive;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

public class RestExecutor implements MakeCustomServicesExecutor {

    private final static String TYPE = CustomServicesConstants.REST_API_PRIMITIVE_TYPE;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RestExecutor.class);
    @Autowired
    private CoordinatorClient coordinator;
    @Autowired
    private CustomServicesRESTApiPrimitiveDAO customServicesRESTApiDao;

    @Override public ViPRExecutionTask<CustomServicesTaskResult> makeCustomServicesExecutor(final Map<String, List<String>> input, final CustomServicesWorkflowDocument.Step step) {
        final CustomServicesRESTApiPrimitive primitive = customServicesRESTApiDao.get(step.getOperation());
        if( null == primitive ) {
            logger.error("Error retrieving the REST primitive from DB. {} not found in DB", step.getOperation());
            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                    "\"Error retrieving the REST primitive from DB.");
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed(step.getOperation() + " not found in DB");
        }
        return new CustomServicesRESTExecution(input, step, coordinator, primitive);
    }

    @Override public String getType() {
        return TYPE;
    }

    @Override public void setParam(Object object) {

    }
}
