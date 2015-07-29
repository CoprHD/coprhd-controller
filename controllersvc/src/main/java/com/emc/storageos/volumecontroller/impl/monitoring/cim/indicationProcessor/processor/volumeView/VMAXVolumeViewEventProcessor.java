/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.processor.volumeView;

import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.emc.storageos.volumecontroller.impl.monitoring.cim.event.CIMVolumeViewUpdatableDeviceEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.BaseProcessor;

@Component("VMAXVolumeViewEventProcessor")
public class VMAXVolumeViewEventProcessor extends BaseProcessor {

    /**
     * Logger to log the debug information
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(VMAXVolumeViewEventProcessor.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void processIndication(Hashtable<String, String> notification) {
        try {

            logMessage("VMAX VolumeView indication found", new Object[] {});
            CIMVolumeViewUpdatableDeviceEvent spEvent = (CIMVolumeViewUpdatableDeviceEvent) getApplicationContext()
                    .getBean(
                            CIMVolumeViewUpdatableDeviceEvent.class
                                    .getSimpleName());
            spEvent.setIndication(notification);
            Boolean status = spEvent
                    .updateStoragePoolObjectFromVMAXVolumeViewIndication();
            if (status) {
                logMessage(
                        "VMAX StoragePool object updated sucessfully from VMAX Volume View Event",
                        new Object[] {});
            } else {
                logMessage("VMAX StoragePool object not updated",
                        new Object[] {});
            }
            spEvent = null;

        } catch (Exception e) {
            _logger.error("Failed to process VNX Volume View Indication {}",
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
