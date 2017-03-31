package com.emc.sa.service.vipr.customservices.tasks;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;

import java.util.List;
import java.util.Map;

public class RestExecutor implements MakeCustomServicesExecutor {

    private final static String TYPE = "REST API";
    @Override public ViPRExecutionTask<CustomServicesTaskResult> makeCustomServicesExecutor(final Map<String, List<String>> input, final CustomServicesWorkflowDocument.Step step) {
        return new CustomServicesRESTExecution(input, step);
    }

    @Override public String getType() {
        return TYPE;
    }
}
