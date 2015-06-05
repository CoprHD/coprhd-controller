/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
// Copyright 2012 by EMC Corporation ("EMC").
//
// UNPUBLISHED  CONFIDENTIAL  AND  PROPRIETARY  PROPERTY OF EMC. The copyright
// notice above does not evidence any actual  or  intended publication of this
// software. Disclosure and dissemination are pursuant to separate agreements.
// Unauthorized use, distribution or dissemination are strictly prohibited.

package com.emc.storageos.cimadapter.connections.cim;

/**
 * Defines an enumeration for the descriptions associated with the
 * CIM_AlertIndication AlertType property value. The CIM_AlertIndication
 * AlertType property value is an integer. This enumeration is used to associate
 * a textual description to that numeric value. The index for each enumerated
 * value corresponds to the associated CIM_AlertIndication AlertType property
 * value. So, for example, a CIM_AlertIndication AlertType property value of 2
 * corresponds to the enumerated value ALERT_TYPE_COMM_ALERT. Note that
 * CIM_AlertIndication AlertType property values start at 1, so the enumerated
 * value ALERT_TYPE_UNUSED is really just a dummy value to properly align the
 * enumeration indices with the AlertType property values. The static toString()
 * method is called to perform the translation.
 */
public enum CimAlertType {

    // The expected alert types.
    ALERT_TYPE_UNUSED("Unused"),
    ALERT_TYPE_OTHER("Other"),
    ALERT_TYPE_COMM_ALERT("CommunicationsAlert"),
    ALERT_TYPE_QUALITY_ALERT("QualityOfServiceAlert"),
    ALERT_TYPE_PROC_ERROR("ProcessingError"),
    ALERT_TYPE_DVC_ALERT("DeviceAlert"),
    ALERT_TYPE_ENV_ALERT("EnvironmentalAlert"),
    ALERT_TYPE_MODEL_CHANGE("ModelChange"),
    ALERT_TYPE_SECURITY_ALERT("SecurityAlert");

    // A textual description for the associated numerical property value.
    private String _description;

    /**
     * Constructs a CimAlertType given an AlertType value.
     * 
     * @param description The associated AlertType description.
     */
    private CimAlertType(String description) {
        _description = description;
    }

    /**
     * Gets the description for the given alert type value.
     * 
     * @param value the alert type value.
     * 
     * @return the corresponding description.
     */
    public static String toString(int value) {
        CimAlertType[] values = CimAlertType.values();

        // If the value is 0 or outside the range of enumeration indices, then
        // just return the numerical value.
        if (value == 0 || value >= values.length) {
            return Integer.toString(value);
        }
        return CimAlertType.values()[value]._description;
    }
}