package com.emc.sa.service.vipr.customservices.tasks;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.CustomServicesConstants;

import java.util.List;
import java.util.Map;

public class ShellScriptExecutor implements MakeCustomServicesExecutor {

    private final static String TYPE = "Shell Script";

    @Override public ViPRExecutionTask<CustomServicesTaskResult> makeCustomServicesExecutor(final Map<String, List<String>> input,
            final CustomServicesWorkflowDocument.Step step) {
        final String orderDir = String.format("%s%s/", CustomServicesConstants.ORDER_DIR_PATH,
                ExecutionUtils.currentContext().getOrder().getOrderNumber());

        MakeCustomServicesExecutor.createOrderDir(orderDir);

        return new CustomServicesShellScriptExecution(input, step);
    }

    @Override public String getType() {
        return TYPE;
    }
}
