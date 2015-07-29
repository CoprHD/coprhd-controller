/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.processor.storagePool;

import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.emc.storageos.volumecontroller.impl.monitoring.cim.event.CIMStoragePoolUpdatableDeviceEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.BaseProcessor;

@Component("VNXStoragePoolEventProcessor")
public class VNXStoragePoolEventProcessor extends BaseProcessor {

    /**
     * Logger to log the debug information
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(VNXStoragePoolEventProcessor.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void processIndication(Hashtable<String, String> notification) {
        try {

            logMessage("VNX StoragePool indication found", new Object[] {});
            CIMStoragePoolUpdatableDeviceEvent spEvent = (CIMStoragePoolUpdatableDeviceEvent) getApplicationContext()
                    .getBean(
                            CIMStoragePoolUpdatableDeviceEvent.class
                                    .getSimpleName());
            spEvent.setIndication(notification);
            Boolean status = spEvent
                    .updateStoragePoolObjectFromVNXStoragePoolIndication();
            if (status) {
                logMessage(
                        "VNX StoragePool object updated sucessfully from VNX StoragePool Event",
                        new Object[] {});
            } else {
                logMessage("VNX StoragePool object not updated",
                        new Object[] {});
            }

            getRecordableEventManager().recordEvents(spEvent);
            logMessage("VNX StoragePool Event persisted in DB",
                    new Object[] {});

        } catch (Exception e) {
            _logger.error("Failed to process VNX Storage Pool Indication",
                    e);
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
