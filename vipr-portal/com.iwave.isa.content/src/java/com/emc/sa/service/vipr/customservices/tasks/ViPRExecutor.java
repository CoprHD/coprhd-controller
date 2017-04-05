package com.emc.sa.service.vipr.customservices.tasks;

import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAOs;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.vipr.client.impl.RestClient;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;


public class ViPRExecutor implements MakeCustomServicesExecutor {
    private final static String TYPE = "ViPR REST API";
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesViprExecution.class);
    @Autowired
    private CustomServicesPrimitiveDAOs daos;
    private RestClient client;
    @Override public ViPRExecutionTask<CustomServicesTaskResult> makeCustomServicesExecutor(final Map<String, List<String>> input, final CustomServicesWorkflowDocument.Step step) {
        try {
            return new CustomServicesViprExecution(input, step, daos, client);
        } catch (Exception e) {
            logger.info("exception is:{}", e);

            return null;
        }
    }

    @Override public String getType() {
        return TYPE;
    }

    @Override public void setParam(Object object) {
        client = (RestClient)object;
    }
}
