package com.emc.sa.service.vipr.customservices.tasks;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.CustomServicesConstants;

import java.util.List;
import java.util.Map;

public class RemoteAnsibleExecutor implements MakeCustomServicesExecutor {
    private final static String TYPE = "Remote Ansible";

    @Override public ViPRExecutionTask<CustomServicesTaskResult> makeCustomServicesExecutor(final Map<String, List<String>> input, final CustomServicesWorkflowDocument.Step step) {
        return new CustomServicesRemoteAnsibleExecution(input, step);
    }


    @Override public String getType() {
        return TYPE;
    }
}
