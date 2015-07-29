/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
// Copyright 2012 by EMC Corporation ("EMC").
//
//UNPUBLISHED  CONFIDENTIAL  AND  PROPRIETARY  PROPERTY OF EMC. The copyright
//notice above does not evidence any actual  or  intended publication of this
//software. Disclosure and dissemination are pursuant to separate agreements.
//Unauthorized use, distribution or dissemination are strictly prohibited.

package com.emc.storageos.cimadapter.processors;

// Java security imports
import java.security.MessageDigest;

// CIM imports
import javax.cim.CIMInstance;

// Apache Commons imports
import org.apache.commons.codec.binary.Hex;

// StorageOS imports
import com.emc.storageos.cimadapter.connections.celerra.CelerraConnection;
import com.emc.storageos.cimadapter.connections.celerra.CelerraMessageSpec;
import com.emc.storageos.cimadapter.connections.celerra.CelerraMessageSpecList;
import com.emc.storageos.cimadapter.connections.cim.CimConstants;

/**
 * The CelerraIndicationProcessor class extends the {@link DefaultCimIndicationProcessor} class and does some additional
 * processing for indications received from Celerra connections.
 */
public class CelerraIndicationProcessor extends DefaultCimIndicationProcessor {

    // The list of message specifications for Celerra connections.
    private CelerraMessageSpecList _messageSpecs;

    /**
     * Constructor
     * 
     * @param connection The Celerra connection associated with this processor.
     */
    public CelerraIndicationProcessor(CelerraConnection connection) {
        super(connection);
        _messageSpecs = connection.getMessageSpecs();
    }

    @Override
    /**
     * {@inheritDoc}
     */
    protected CimIndicationSet processIndication(CIMInstance indication) {
        // Call the base class to do the base processing.
        CimIndicationSet eventData = super.processIndication(indication);

        // Do additional processing for Celerra indications.
        // If this is an InstIndication, nothing else needs to be done.
        if (eventData.isAlertIndication()) {
            // Classify the alert
            String alertType = eventData.get(CimConstants.ALERT_TYPE_KEY);
            eventData.set(CimConstants.OTHER_ALERT_TYPE_COMP_KEY, CimConstants.UNKNOWN_KEY);
            eventData.set(CimConstants.OTHER_ALERT_TYPE_FACILITY_KEY, CimConstants.UNKNOWN_KEY);
            eventData.set(CimConstants.OTHER_ALERT_TYPE_EVENT_ID_KEY, CimConstants.UNKNOWN_KEY);
            if ((alertType != null) && (alertType.equals("1"))) {
                String otherAlertType = eventData.get(CimConstants.OTHER_ALERT_TYPE_KEY);
                if (otherAlertType != null) {
                    // OtherAlertType is populated! Assume that it contains the
                    // component, facility and eventID in CSV format.
                    //
                    // NOTE: Assume OtherSeverity is populated.
                    String[] fields = otherAlertType.split(",", 3);
                    eventData.set(CimConstants.OTHER_ALERT_TYPE_COMP_KEY, fields[0]);
                    if (fields.length > 1) {
                        eventData.set(CimConstants.OTHER_ALERT_TYPE_FACILITY_KEY, fields[1]);
                    }
                    if (fields.length > 2) {
                        eventData.set(CimConstants.OTHER_ALERT_TYPE_EVENT_ID_KEY, fields[2]);
                    }
                } else {
                    // OtherAlertType is not populated. Determine the
                    // component, facility, eventID, and severity from
                    // the ProbableCauseDescription.
                    String message = eventData.get(CimConstants.PROBABLE_CAUSE_DESCR_KEY);
                    if (message != null) {
                        for (CelerraMessageSpec spec : _messageSpecs) {
                            if (message.matches(spec.getPattern())) {
                                eventData.set(CimConstants.OTHER_ALERT_TYPE_COMP_KEY, spec.getComponent());
                                eventData.set(CimConstants.OTHER_ALERT_TYPE_FACILITY_KEY, spec.getFacility());
                                eventData.set(CimConstants.OTHER_ALERT_TYPE_EVENT_ID_KEY, spec.getEventID());
                                eventData.set(CimConstants.OTHER_SEVERITY_KEY, spec.getSeverity());
                            }
                        }
                    }
                }

                // Create "tags" suitable for use in NOTIF ECI names.
                eventData.set(CimConstants.OTHER_ALERT_TYPE_COMP_TAG_KEY,
                        eventData.get(CimConstants.OTHER_ALERT_TYPE_COMP_KEY).replace('_', '-'));

                eventData.set(CimConstants.OTHER_ALERT_TYPE_FACILITY_TAG_KEY,
                        eventData.get(CimConstants.OTHER_ALERT_TYPE_FACILITY_KEY).replace('_', '-'));

                eventData.set(CimConstants.OTHER_ALERT_TYPE_EVENT_ID_TAG_KEY,
                        eventData.get(CimConstants.OTHER_ALERT_TYPE_EVENT_ID_KEY).replace('_', '-'));
            }

            // Generate a message digest from the ProbableCauseDescription
            // for use as a Bourne alert name token in place of an affected
            // object identifier.
            String message = eventData.get(CimConstants.PROBABLE_CAUSE_DESCR_KEY);
            if (message != null) {
                try {
                    MessageDigest md = MessageDigest.getInstance(CimConstants.MD5_HASH_ALGORITHM);
                    byte[] bytes = md.digest(message.getBytes());
                    eventData.set(CimConstants.PROBABLE_CAUSE_DESCR_MD_KEY, new String(Hex.encodeHex(bytes)));
                } catch (Exception e) {
                    s_logger.debug("Exception generating message digest.", e);
                }
            }
        }
        return eventData;
    }
}
