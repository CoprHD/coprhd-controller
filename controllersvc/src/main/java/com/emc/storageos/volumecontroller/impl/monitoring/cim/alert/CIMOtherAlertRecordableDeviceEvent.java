/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.alert;

import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.MonitoringPropertiesLoader;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;

public class CIMOtherAlertRecordableDeviceEvent extends
        CIMAlertRecordableDeviceEvent {

    /**
     * Logger to log the debug statements
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(CIMOtherAlertRecordableDeviceEvent.class);

    /**
     * Overloaded constructor
     * 
     * @param dbClient
     */
    public CIMOtherAlertRecordableDeviceEvent(DbClient dbClient,
            MonitoringPropertiesLoader mLoader,
            Hashtable<String, String> notification) {
        super(dbClient);
        _monitoringPropertiesLoader = mLoader;
        _indication = notification;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getNativeGuid() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends DataObject> getResourceClass() {

        String alertType = _indication.get(CIMConstants.INDICATION_CLASS_TAG);
        if (alertType != null && alertType.trim().length() > 0) {
            if (alertType.contains(OSLS_ALERT_INDICATION)) {
                return Volume.class;
            } else if (alertType.contains(CIM_ALERT_INDICATION)) {
                return FileShare.class;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getExtensions() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRecordType() {
        return RecordType.Alert.name();
    }

}
