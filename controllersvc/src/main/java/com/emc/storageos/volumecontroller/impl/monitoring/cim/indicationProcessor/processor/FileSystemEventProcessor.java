/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.processor;

import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.emc.storageos.volumecontroller.impl.monitoring.cim.event.CIMFileShareRecordableDeviceEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.BaseProcessor;

@Component("FileSystemEventProcessor")
public class FileSystemEventProcessor extends BaseProcessor {

    /**
     * Logger to log the debug information
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(StorageVolumeEventProcessor.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void processIndication(Hashtable<String, String> notification) {
        try {
            String type;
            logMessage("FileShare indication found", new Object[] {});
            CIMFileShareRecordableDeviceEvent fEvent = (CIMFileShareRecordableDeviceEvent) getApplicationContext()
                    .getBean(
                            CIMFileShareRecordableDeviceEvent.class
                                    .getSimpleName());
            fEvent.setIndication(notification);
            logMessage(
                    "Requesting Event Manager to perssit this FileShare event",
                    new Object[] {});
            // If no Event type found don't persist.
            type = fEvent.getType();
            if (type != null && type.length() > 0) {
                _recordableEventManager.recordEvents(fEvent);
                _logger.info(" Event Type Persisted {}", fEvent.getType());
            } else {
                logMessage(
                        " -> Unable to pesist event into Cassandra because event type is {}",
                        new Object[] { fEvent.getType() });
            }
            fEvent = null;

        } catch (Exception e) {
            _logger.error("Failed to process File System Indication {}",
                    e.getMessage());
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

}
