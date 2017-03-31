package com.emc.sa.service.vipr.customservices.tasks;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

public class RestExecutor implements MakeCustomServicesExecutor {

    private final static String TYPE = "REST API";
    @Autowired
    private CoordinatorClient coordinator;

    @Override public ViPRExecutionTask<CustomServicesTaskResult> makeCustomServicesExecutor(final Map<String, List<String>> input, final CustomServicesWorkflowDocument.Step step) {
        return new CustomServicesRESTExecution(input, step, coordinator);
    }

    @Override public String getType() {
        return TYPE;
    }
}
