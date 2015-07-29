/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.processor.volumeView;

import java.util.Hashtable;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.BaseProcessor;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;

public class VolumeViewEventProcessor extends BaseProcessor {

    /**
     * Type of all event processors will be holding this map
     */
    private Map<String, BaseProcessor> _volumeViewProcessors;

    /**
     * Logger to log the debug information
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(VolumeViewEventProcessor.class);

    /**
     * Default Constructor
     */
    public VolumeViewEventProcessor(
            Map<String, BaseProcessor> volumeViewProcessors) {
        _volumeViewProcessors = volumeViewProcessors;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processIndication(Hashtable<String, String> notification) {

        String className = getIndicationSourceInstanceSPCreationClassName(notification);

        if (className != null) {
            BaseProcessor processor = _volumeViewProcessors.get(className);
            if (processor != null) {
                processor.processIndication(notification);
            } else {
                logMessage("No processor found to process this indication",
                        new Object[] {});
            }
        } else {
            logMessage("No need to process this indication", new Object[] {});
        }
    }

    /**
     * Log the messages. This method eliminates the logging condition check
     * every time when we need to log a message.
     * 
     * @param msg
     * @param obj
     */
    private void logMessage(String msg, Object[] obj) {
        if (getMonitoringPropertiesLoader().isToLogIndications()) {
            _logger.debug(msg, obj);
        }
    }

    /**
     * Retrieves the Creation ClassName from the indication that is being used
     * to find the Volume View event associated with StoragePool or not.
     * 
     * @param notification
     * @return ClassName
     */
    private String getIndicationSourceInstanceSPCreationClassName(
            Hashtable<String, String> notification) {
        return notification
                .get(CIMConstants.SOURCE_INSTANCE_SP_CREATION_CLASS_NAME);
    }

}
