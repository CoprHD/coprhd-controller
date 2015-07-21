/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;

@Component("CIMVolumeViewUpdatableDeviceEvent")
@Scope("prototype")
public class CIMVolumeViewUpdatableDeviceEvent extends
        CIMStoragePoolUpdatableDeviceEvent{
    /**
     * Logger to log the debug statements
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(CIMVolumeViewUpdatableDeviceEvent.class);

    /**
     * Overloaded constructor
     * 
     * @param dbClient
     */
    @Autowired
    public CIMVolumeViewUpdatableDeviceEvent(DbClient dbClient) {
        super(dbClient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getNativeGuid() {

        _logger.debug("Computing NativeGuid for VolumeView Event assoicaued with StoragePool");

        if (_nativeGuid != null) {
            _logger.debug("Using already computed NativeGuid : {}", _nativeGuid);
            return _nativeGuid;
        }

        try {
            _nativeGuid = NativeGUIDGenerator.generateSPNativeGuidFromVolumeViewIndication(_indication);
            logMessage("NativeGuid for StoragePool Computed as  : [{}]",
                    new Object[] { _nativeGuid });
        } catch (Exception e) {
            _logger.error("Unable to compute NativeGuid :", e);
        }

        return _nativeGuid;

    }

    /**
     * Identifies and use VMAX specific attributes to read the corresponding values from VMAX VolumeView
     * indication
     * VMAX Volume View Indication doesn't have Subscribed capacity.
     * VMAX Volume View Indication doesn't have Pool name as well.
     * @return
     */
    public boolean updateStoragePoolObjectFromVMAXVolumeViewIndication() {

        return retriveAndProcessIndicationAttributeValues(
                CIMConstants.VOLUME_VIEW_INDICATION_FREE_CAPACITY, null,
                CIMConstants.VOLUME_VIEW_INDICATION_TOTAL_CAPACITY, null);

    }

    /**
     * Identifies and use VNX specific attributes to read the corresponding values from VNX VolumeView
     * indication
     * VNX Volume View Indication doesn't have Subscribed capacity.
     * @return
     */
    public boolean updateStoragePoolObjectFromVNXVolumeViewIndication() {

        return retriveAndProcessIndicationAttributeValues(
                CIMConstants.VOLUME_VIEW_INDICATION_FREE_CAPACITY,
                CIMConstants.VOLUME_VIEW_INDICATION_POOL_NAME,
                CIMConstants.VOLUME_VIEW_INDICATION_TOTAL_CAPACITY, null);
    }


    /**
     * Log the messages. This method eliminates the logging condition check
     * every time when we need to log a message.
     * 
     * @param msg
     * @param obj
     */
    private void logMessage(String msg, Object[] obj) {
        if (_monitoringPropertiesLoader.isToLogIndications()) {
            _logger.debug("-> " + msg, obj);
        }
    }
}
