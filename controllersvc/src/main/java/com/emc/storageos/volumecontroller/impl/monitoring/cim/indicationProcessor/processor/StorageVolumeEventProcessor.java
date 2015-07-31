/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.processor;

import java.net.URI;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.event.CIMInstanceRecordableDeviceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.volumecontroller.impl.monitoring.cim.event.CIMVolumeRecordableDeviceEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.event.CIMSnapshotRecordableDeviceEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.BaseProcessor;

@Component("StorageVolumeEventProcessor")
public class StorageVolumeEventProcessor extends BaseProcessor {

    @Autowired
    private DbClient dbClient;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient client) {
        dbClient = client;
    }

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
            CIMInstanceRecordableDeviceEvent vEvent;
            String nativeGuid = NativeGUIDGenerator.generateNativeGuid(notification);
            // First search in ViPR DB for volumes
            List<URI> resourceURIs = new ArrayList<URI>();
            _logger.info("nativeGuid :{}", nativeGuid);
            resourceURIs = getDbClient().queryByConstraint(AlternateIdConstraint.Factory.getVolumeNativeGuidConstraint(nativeGuid));
            if (resourceURIs.isEmpty()) {
                // This has to be a snapshot GUID.
                logMessage("StorageVolumeSnapshot indication found", new Object[] {});
                vEvent = (CIMSnapshotRecordableDeviceEvent) getApplicationContext()
                        .getBean(
                                CIMSnapshotRecordableDeviceEvent.class
                                        .getSimpleName());
            } else {
                logMessage("StorageVolume indication found", new Object[] {});
                vEvent = (CIMVolumeRecordableDeviceEvent) getApplicationContext()
                        .getBean(
                                CIMVolumeRecordableDeviceEvent.class
                                        .getSimpleName());
            }
            vEvent.setIndication(notification);
            logMessage(
                    "Requesting Event Manager to persist this Volume event ",
                    new Object[] {});
            type = vEvent.getType();
            // If no Event type found don't persist.
            if (type != null && type.length() > 0) {
                getRecordableEventManager().recordEvents(vEvent);
                _logger.info(" Event Type Persisted {}", vEvent.getType());
            } else {
                logMessage(
                        " -> Unable to pesist event into Cassandra because event type is {} ",
                        new Object[] { vEvent.getType() });
            }
            vEvent = null;
        } catch (Exception e) {
            _logger.error("Failed to process Storage Volume Indication",
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
