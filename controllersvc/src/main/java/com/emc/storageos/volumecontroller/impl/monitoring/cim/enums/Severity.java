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

package com.emc.storageos.volumecontroller.impl.monitoring.cim.enums;

/**
 * Represents the severity
 */
public enum Severity {

    /**
     * the Severity of the indication is unknown or indeterminate.Information
     * and Unknown (respectively) follow common usage. Literally, the Error is
     * purely informational or its severity is simply unknown
     */
    UNKNOWN(0),
    /**
     * by CIM convention, is used to indicate that the Severity's value can be
     * found in the OtherSeverity.
     */
    OTHER(1),
    /**
     * Information should be used when providing an informative response
     */
    INFORMATION(2),
    /**
     * Degraded/Warning should be used when its appropriate to let the user
     * decide if action is needed
     */
    WARNING(3),
    /**
     * Minor should be used to indicate action is needed, but the situation is
     * not serious at this time
     */
    MINOR(4),
    /**
     * Major should be used to indicate action is needed NOW.
     */
    MAJOR(5),
    /**
     * Critical should be used to indicate action is needed NOW and the scope is
     * broad (perhaps an imminent outage to a critical resource will result)
     */
    CRITICAL(6),
    /**
     * Fatal/NonRecoverable should be used to indicate an error occurred, but it
     * is too late to take remedial action.
     */
    FATAL(7),
    /**
     * Tells to keep to pay the attention
     */
    NOTICE(8),
    /**
     * Requires to act now; this is same like major
     */
    EMERGENCY(9);

    /**
     * Default Constructor
     * 
     * @param rep
     */
    Severity(int rep) {
        _index = rep;
    }

    /**
     * index that represents the constant value for the enum constant
     */
    private int _index;

    /**
     * return the severity
     * 
     * @return
     */
    public int getSeverity() {
        return _index;
    }
}
