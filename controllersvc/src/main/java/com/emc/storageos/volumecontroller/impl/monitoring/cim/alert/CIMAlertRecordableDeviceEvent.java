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

package com.emc.storageos.volumecontroller.impl.monitoring.cim.alert;

//Java imports
import java.util.Hashtable;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.CIMRecordableDeviceEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.MonitoringPropertiesLoader;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.AlertType;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.Severity;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;

/**
 * An event processor to process the events that were received from consumer in
 * name/value pairs format passed in a Hashtable<String, String>.
 */
/**
 * @author kanchg
 * 
 */
public abstract class CIMAlertRecordableDeviceEvent extends
        CIMRecordableDeviceEvent {

    /**
     * Logger to log the debug statements
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(CIMAlertRecordableDeviceEvent.class);

    /**
     * Represents a value of AlertTypeTag that tells this alert is of type file.
     */
    private static final String FILE_ALERT_TYPE_TAG_VALUE = "Other";

    /**
     * Hashtable that holds an incoming CIM Indication
     */
    protected Hashtable<String, String> _indication;

    /**
     * Represents a Block Alert if IndicationClassTag contains this. Usually this will be available in Volume related Alerts
     */
    protected static final String OSLS_ALERT_INDICATION = "OSLS.AlertIndication";

    /**
     * Represents a File Alert if IndicationClassTag contains this. This represents a File Alert Indication
     */
    protected static final String CIM_ALERT_INDICATION = "CIM.AlertIndication";

    /**
     * Reference to Monitoring Properties Loader
     */
    protected MonitoringPropertiesLoader _monitoringPropertiesLoader;

    /**
     * Overloaded constructor
     * 
     * @param dbClient
     */
    public CIMAlertRecordableDeviceEvent(DbClient dbClient) {
        super(dbClient);
    }

    /**
     * returns the indication
     * 
     * @return
     */
    public Hashtable<String, String> getIndication() {
        return _indication;
    }

    /**
     * Sets the indications
     * 
     * @param indication
     */
    public void setIndication(Hashtable<String, String> indication) {
        _indication = indication;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSeverity() {

        String severityStr = _indication.get(CIMConstants.OTHER_SEVERITY);
        Severity computedSeverity = Severity.UNKNOWN;
        if ((severityStr != null && severityStr.length() == 0)
                || severityStr == null) {
            
            int severity;
            
            severityStr = _indication.get(CIMConstants.PERCEIVED_SEVERITY);
            try {
                if (severityStr != null) {
                    severity = new Integer(severityStr);

                    if (severity >= 0 && severity <= 9) {
                        computedSeverity = Severity.values()[severity];
                    }
                } 
                severityStr = computedSeverity.name();
            } catch (NumberFormatException e) {
                _logger.error("Unable to find the right severity ", e);
                severityStr = Severity.UNKNOWN.name();
            } catch (Exception e) {
                _logger.error("Unable to find the right severity ", e);
                severityStr = Severity.UNKNOWN.name();
            }
        }
        else
        {
            severityStr = computedSeverity.name();
        }
        if (_monitoringPropertiesLoader.isToLogIndications())
            _logger.debug("Severity calclulated for Alert {}", severityStr);
        return severityStr;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.impl.monitoring.RecordableEvent#
     * getDescription()
     */
    @Override
    public String getDescription() {

        String description = "";
        description = _indication.get(CIMConstants.PROBABLE_CAUSE_DESCRIPTION);
        if (description == null) {
            description = _indication.get(CIMConstants.DESCRIPTION);
        }
        if (_monitoringPropertiesLoader.isToLogIndications())
            _logger.debug("Description Found : {}", description);
        return description;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAlertType() {
        String alertType = null;
        alertType = _indication.get(CIMConstants.ALERT_TYPE_TAG);

        if (alertType != null
                && alertType.equalsIgnoreCase(FILE_ALERT_TYPE_TAG_VALUE)) {
            alertType = _indication.get(CIMConstants.OTHER_ALERT_TYPE_EVENT_ID);
        }
        return alertType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTimestamp() {
        return Long.parseLong(_indication.get(CIMConstants.INDICATION_TIME));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
        return AlertType.AlertIndication.name();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSource() {
        return _indication.get(CIMConstants.INDICATION_SOURCE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getEventId() {
        return RecordableBourneEvent.getUniqueEventId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getService() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getExtensions() {
        return null;

    }

    @Override
    public String getOperationalStatusDescriptions() {
        return null;
    }

    @Override
    public String getOperationalStatusCodes() {
        return null;
    }
}
