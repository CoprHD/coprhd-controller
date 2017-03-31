package com.emc.sa.service.vipr.customservices.tasks;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface MakeCustomServicesExecutor {
    public static final org.slf4j.Logger logger = LoggerFactory.getLogger(MakeCustomServicesExecutor.class);

    public static boolean createOrderDir(final String orderDir) {
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
}
