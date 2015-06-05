/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.utility;

import java.util.Hashtable;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.volumecontroller.impl.monitoring.cim.MonitoringPropertiesLoader;
import com.emc.storageos.services.OperationTypeEnum;

/**
 * A Singleton Spring Context Scoped defined bean that helps to determine the
 * right Event Type like Volume Active, InActive, File Share Active, InActive.
 */
@Component
public class EventTypeDeterminer {

    /**
     * Logger to log the debug statements
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(EventTypeDeterminer.class);

    /**
     * Reference to MonitoringPropertiesLoader
     */
    @Autowired
    private MonitoringPropertiesLoader _monitoringPropertiesLoader;

    /**
     * Figure out the File and Block related event type based on the operational
     * status values available in the indication
     * 
     * @param notification
     * @return
     */

    private String determineEventTypeBasedOnOperationalStatus(
            Hashtable<String, String> notification, String[] descs,
            String[] codes, String evtOKType, String evtNOTOKType,
            List<String> propDescriptions, List<String> propCodes) {
        logMessage("Determiming Operational Status for Event",
                new Object[] {});

        String evtType = null;
        String[] values = descs;

        if (values.length > 0) {
            evtType = evtNOTOKType;
            for (String value : values) {
                if (propDescriptions.contains(value)) {
                    evtType = evtOKType;
                    break;
                }
            }
        } else {
            values = codes;
            if (values.length > 0) {
                evtType = evtNOTOKType;
                for (String value : values) {
                    if (propCodes.contains(value)) {
                        evtType = evtOKType;
                        break;
                    }
                }
            } else {
                logMessage("No Operational Status Values Found for this Event",
                        new Object[] {});
            }
        }
        return evtType;

    }

    /**
     * This method helps to identify whether the provided indication type is
     * Instance Modification event or not.
     * 
     * @param notification
     * @return
     */
    private Boolean isInstanceModificationEvent(
            Hashtable<String, String> notification) {

        String eventType = "";
        Boolean isInstanceModificationEvent = Boolean.FALSE;

        eventType = notification.get(CIMConstants.INDICATION_CLASS_TAG);

        if (eventType != null && eventType.length() > 0
                && eventType.contains(CIMConstants.INST_MODIFICATION_EVENT)) {
            isInstanceModificationEvent = Boolean.TRUE;
        }
        return isInstanceModificationEvent;

    }

    /**
     * This is the method that determines the specific event type based on
     * operational statues values provided as part of an indication.
     * 
     * @param notification
     * @param isBlockRelatedEvent
     * @return
     */
    public String determineEventTypeBasedOnOperationStatusValues(
            Hashtable<String, String> notification,
            Boolean isBlockRelatedEvent, String[] descs, String[] codes) {
        String evtType = null;
        if (isInstanceModificationEvent(notification)) {
            if (isBlockRelatedEvent) {
                evtType = determineEventTypeBasedOnOperationalStatus(
                        notification, descs, codes,
                        OperationTypeEnum.OPERATE_BLOCK_VOLUME.getEvType(true),
                        OperationTypeEnum.OPERATE_BLOCK_VOLUME.getEvType(false),
                        _monitoringPropertiesLoader
                                .getBlockEventActiveOSDescs(),
                        _monitoringPropertiesLoader
                                .getBlockEventActiveOSCodes());
            } else {
                evtType = determineEventTypeBasedOnOperationalStatus(
                        notification, descs, codes,
                        OperationTypeEnum.OPERATE_FILE_SYSTEM.getEvType(true),
                        OperationTypeEnum.OPERATE_FILE_SYSTEM.getEvType(false),
                        _monitoringPropertiesLoader
                                .getFileSystemEventActiveOSDescs(),
                        _monitoringPropertiesLoader
                                .getFileSystemEventActiveOSCodes());
            }
            logMessage(
                    "Event type found based on Operational Status Values of Indication provided is - {} ",
                    new Object[] { evtType });
        } else {
            logMessage(
                    "Not an Instance Modification Event Type Indication. No Algorithemic execution required to determine Event Type based on Operational Status Values Provided",
                    new Object[] {});
        }
        return evtType;
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
            _logger.debug("->" + msg, obj);
        }
    }

}
