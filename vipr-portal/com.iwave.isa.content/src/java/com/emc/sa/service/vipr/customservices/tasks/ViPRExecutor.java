package com.emc.sa.service.vipr.customservices.tasks;

import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAOs;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.vipr.client.impl.RestClient;

public class ViPRExecutor implements MakeCustomServicesExecutor {
    private final static String TYPE = CustomServicesConstants.VIPR_PRIMITIVE_TYPE;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ViPRExecutor.class);
    @Autowired
    private CustomServicesPrimitiveDAOs daos;
    private RestClient client;
    @Override public ViPRExecutionTask<CustomServicesTaskResult> makeCustomServicesExecutor(final Map<String, List<String>> input, final CustomServicesWorkflowDocument.Step step, final int iterCount) {
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
