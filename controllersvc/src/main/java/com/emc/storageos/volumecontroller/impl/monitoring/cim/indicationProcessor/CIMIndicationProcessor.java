/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor;

import java.util.Hashtable;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.MonitoringPropertiesLoader;

/**
 * An event processor to process the events that were received from consumer in
 * name/value pairs format passed in a Hashtable<String, String>.
 * 
 * This Class will process the corresponding event identified by their
 * properties available inside Hashtable. Two main events identified as of now
 * to process: 1.CIM_AlertIndication 2.CIM_Instance 2.1 CIM_InstCreation 2.2
 * CIM_InstModification 2.3 CIM_InstDeletion
 */
@Component
public class CIMIndicationProcessor {

    /**
     * Logger to log the debug statements
     */
    private static final Logger _logger = LoggerFactory.getLogger(CIMIndicationProcessor.class);
    /**
     * Reference to CIMAlert processor.
     */
    @Autowired
    private CIMAlertProcessor _alertProcessor;
    /**
     * Reference to CIMInstance processor
     */
    @Autowired
    private CIMEventProcessor _eventProcessor;

    /**
     * Reference to MonitoringPropertiesLoader
     */
    @Autowired
    private MonitoringPropertiesLoader _monitoringPropertiesLoader;

    /**
     * Default Constructor
     */
    public CIMIndicationProcessor() {
        super();
    }

    /**
     * Indication will be inserted into cassandra provided timestamp as row key.
     * Identify the indication type and process it accordingly.
     * 
     * @param cimNotification
     *            of type Hashtable.
     */
    public void processIndication(Hashtable<String, String> cimNotification) {

        String cimIndicationType = getCimIndicationType(cimNotification);
        if (_monitoringPropertiesLoader.isToLogIndications()) {
            _logger.debug("Indication with key : value pairs received --> \n" + getIndicationData(cimNotification));
        }

        if (cimIndicationType != null && cimIndicationType.equals(CimConstants.CIM_ALERT_INDICATION_TYPE)) {
            if (_monitoringPropertiesLoader.isToLogIndications()) {
                _logger.debug("CimIndication of type \"Alert\" received");
            }
            _alertProcessor.processIndication(cimNotification);

        } else if (cimIndicationType != null && cimIndicationType.equals(CimConstants.CIM_INST_INDICATION_TYPE)) {
            if (_monitoringPropertiesLoader.isToLogIndications()) {
                _logger.debug("CimIndication of type \"Instance\" received");
            }
            _eventProcessor.processIndication(cimNotification);
        } else {
            if (_monitoringPropertiesLoader.isToLogIndications()) {
                _logger.debug("Unknown CIM Inidcation received {}", cimIndicationType);
            }
        }
    }

    /**
     * build the String from Hashtable with its key value pairs
     * 
     * @param cimNotification
     */
    static public String getIndicationData(Hashtable<String, String> cimNotification) {
        Set<String> enumKeys = cimNotification.keySet();
        StringBuilder sb = new StringBuilder();

        for (String key : enumKeys) {
            sb.append(key).append(" : ").append(cimNotification.get(key)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Determines the type of indication
     * 
     * @param cimNotification
     * @return
     */
    private String getCimIndicationType(Hashtable<String, String> cimNotification) {
        String cimIndicationType = cimNotification.get(CimConstants.CIM_INDICATION_TYPE_KEY);
        return cimIndicationType;
    }

}
