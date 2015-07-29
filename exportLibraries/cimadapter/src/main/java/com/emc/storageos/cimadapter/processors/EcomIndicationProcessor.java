/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
// Copyright 2012 by EMC Corporation ("EMC").
//
// UNPUBLISHED  CONFIDENTIAL  AND  PROPRIETARY  PROPERTY OF EMC. The copyright
// notice above does not evidence any actual  or  intended publication of this
// software. Disclosure and dissemination are pursuant to separate agreements.
// Unauthorized use, distribution or dissemination are strictly prohibited.

package com.emc.storageos.cimadapter.processors;

// CIM imports
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;

import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.cimadapter.connections.ecom.EcomConnection;

/**
 * The EcomIndicationProcessor class extends the {@link DefaultCimIndicationProcessor} class and does some additional
 * processing for indications received from ECOM connections.
 */
public class EcomIndicationProcessor extends DefaultCimIndicationProcessor {

    /**
     * Constructor
     * 
     * @param connection The ECOM connection associated with this processor.
     */
    public EcomIndicationProcessor(EcomConnection connection) {
        super(connection);
    }

    @Override
    /**
     * {@inheritDoc}
     */
    protected CimIndicationSet processIndication(CIMInstance indication) {
        String propertyName;
        String valueName;
        String className;

        // Call the base class to do the base processing.
        CimIndicationSet eventData = super.processIndication(indication);

        // Now do processing specific to ECOM indications.
        if (eventData.isAlertIndication()) {
            // At the time this code was written, ECOM was setting
            // the class name in AlertingManagedElement object path
            // to lower case. Check for this and attempt to fix it.
            propertyName = CimConstants.ALERT_INDICATION_KEY;
            valueName = propertyName + CimConstants.CLASS_NAME_KEY;
            className = eventData.get(valueName);
            if ((className != null) && (className.equals(className.toLowerCase()))) {
                className = getCorrectClassName(indication, propertyName);
                if (className != null) {
                    eventData.set(valueName, className);
                }
            }
        }

        return eventData;
    }

    /**
     * Attempts to get the correct class name from the named CIM property in the
     * given indication. That property is expected to be a string representation
     * of an object path. This method assumes that one or more characters in
     * that path are in the wrong case and looks for a matching key to get the
     * correct name.
     * 
     * @param indication The CIM indication.
     * @param propertyName The CIM property name.
     * 
     * @return The correct name if found, null otherwise.
     */
    private static String getCorrectClassName(CIMInstance indication, String propertyName) {
        String className = null;
        CIMProperty<?> property = indication.getProperty(propertyName);
        if (property != null) {
            String value = property.getValue().toString();
            try {
                CIMObjectPath path = CimObjectPathCreator.createInstance(value);
                className = getCorrectClassName(path);
            } catch (Exception e) {
                s_logger.debug("Failed getting correct class name from {}", value, e);
            }
        }

        return className;
    }

    /**
     * Attempts to get the correct class name in the given CIM object path. This
     * method assumes that one or more characters are in the wrong case. It
     * looks for a matching key to get the correct name.
     * 
     * @param path The CIM object path.
     * 
     * @return The correct name if found, the given name otherwise.
     */
    private static String getCorrectClassName(CIMObjectPath path) {
        // For comparison, all names are normalized to lowercase.
        String className = path.getObjectName().toLowerCase();
        for (CIMProperty<?> key : path.getKeys()) {
            String value = key.getValue().toString();
            if (className.equals(value.toLowerCase())) {
                className = value;
                break;
            }
        }

        return className;
    }
}
