/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.monitoring.cim.enums;

/**
 * SMI-S Provider does send indications
 * When the OperationalStatus of a volume changes. Below, is a table of the
 * values captured in enum for the OperationalStatus
 */
public enum OperationalStatus {

    UNKNOWN(0), 
    OTHER(1), 
    OK(2), 
    DEGRADED(3), 
    STRESSED(4), 
    PREDICTIVEFAILURE(5), 
    ERROR(6), 
    NON_RECOVERABLEERROR(7), 
    STARTING(8), 
    STOPPING(9),
    STOPPED(10), 
    IN_SERVICE(11), 
    NO_CONTACT(12),
    LOST_COMMUNICATION(13), 
    ABORTED(14), 
    DORMANT(15),
    SUPPORTING_ENTITY_IN_ERROR(16),
    COMPLETED(17), 
    POWER_MODE(18), 
    RELOCATING(19); 
   
    /**
     * Default Constructor
     * @param rep
     */
    OperationalStatus(int rep) {
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
    public int getOperationalStatus() {
        return _index;
    }
}
