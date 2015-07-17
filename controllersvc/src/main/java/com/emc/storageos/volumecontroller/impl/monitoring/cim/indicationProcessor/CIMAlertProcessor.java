/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor;

import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.MonitoringPropertiesLoader;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.alert.CIMFileShareAlertRecordableDeviceEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.alert.CIMOtherAlertRecordableDeviceEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.alert.CIMVolumeAlertRecordableDeviceEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;

@Component("alertProcessor")
public class CIMAlertProcessor extends BaseProcessor{


    /**
     * Logger to log the debug information
     */
    private static final Logger _logger = LoggerFactory.getLogger(CIMAlertProcessor.class);

    /**
     * Describes the instance type of event that is of Storage Volume type.
     */
    private static final String STORAGE_VOLUME_ALERT_REPRESENTATION = "storagevolume";

    /**
     * Reference to DbClient
     */
    @Autowired
    private DbClient _dbClient;

    /**
     * Reference to Monitoring Properties Loader
     */
    @Autowired
    protected MonitoringPropertiesLoader _monitoringPropertiesLoader;

    /**
     * Default Constructor
     */
    public CIMAlertProcessor() {
        super();
    }

    
    /**
     * Converts the key value pairs into Alert Indication and persists into
     * Cassandra.
     * 
     * @param notification
     * @return
     */
    public void processIndication(Hashtable<String, String> notification) {
        if (_monitoringPropertiesLoader!=null && _monitoringPropertiesLoader.isToLogIndications()) {
            _logger.debug("Converting CimIndication into an Event");
        }

        try {
            if (isStorageVolumeAlert(notification)) {
                CIMVolumeAlertRecordableDeviceEvent vAlert = new CIMVolumeAlertRecordableDeviceEvent(_dbClient, _monitoringPropertiesLoader, notification);
                _recordableEventManager.recordEvents(vAlert);
            } else if (isFileShareAlert(notification)) {
                CIMFileShareAlertRecordableDeviceEvent fAlert = new CIMFileShareAlertRecordableDeviceEvent(_dbClient, _monitoringPropertiesLoader, notification);
                _recordableEventManager.recordEvents(fAlert);
            } else {
                CIMOtherAlertRecordableDeviceEvent gAlert = new CIMOtherAlertRecordableDeviceEvent(_dbClient, _monitoringPropertiesLoader, notification);
                _recordableEventManager.recordEvents(gAlert);
            }
        } catch (Exception e) {
            _logger.error("Exception occurred while proessing indication", e);
        }
    }

    /**
     * Verifies the received instance event, whether it is a Storage Volume type
     * or not - the check is not case sensitive
     * 
     * 
     * @param notification
     * @return
     */
    private Boolean isStorageVolumeAlert(Hashtable<String, String> notification) {
        Boolean isStorageVolumeAlert = Boolean.FALSE;
        String alertClassSuffixTag = notification.get(CIMConstants.ALERT_MANAGED_ELEMENT_CLASS_SUFFIX_TAG);
        if (alertClassSuffixTag != null)
            isStorageVolumeAlert = alertClassSuffixTag.equalsIgnoreCase(STORAGE_VOLUME_ALERT_REPRESENTATION);
        return isStorageVolumeAlert;
    }

    /**
     * Verifies the received instance event, whether it is a FileShare type or
     * not - the check is not case sensitive
     * 
     * 
     * @TODO - Yet to determine the type of attribute to use and to find the
     *       value that that FileShare alerts will be having
     * @param notification
     * @return
     */
    private Boolean isFileShareAlert(Hashtable<String, String> notification) {
        Boolean isFileShareAlert = Boolean.FALSE;
        // @TODO - Yet to determine the type of attribute to use and to find the
        // value that FileShare alerts will be having

        return isFileShareAlert;
    }    
}
