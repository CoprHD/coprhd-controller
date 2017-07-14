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

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.URIUtil;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.CustomServicesConstants;

public class LocalAnsibleExecutor implements MakeCustomServicesExecutor {
    private final static String TYPE = CustomServicesConstants.ANSIBLE_PRIMITIVE_TYPE;
    @Autowired
    private DbClient dbClient;

    @Override public ViPRExecutionTask<CustomServicesTaskResult> makeCustomServicesExecutor(final Map<String, List<String>> input, final CustomServicesWorkflowDocument.Step step, final int iterCount) {
        final String folderUniqueStep = step.getId().replace("-", "");
        final String orderDir = String.format("%s%s/%s/", CustomServicesConstants.ORDER_DIR_PATH,
                ExecutionUtils.currentContext().getOrder().getOrderNumber(),folderUniqueStep);
        MakeCustomServicesExecutor.createOrderDir(orderDir);

        return new CustomServicesLocalAnsibleExecution(input, step, dbClient, orderDir);
    }

    @Override public String getType() {
        return TYPE;
    }

    @Override public void setParam(Object object) {

    }
}
