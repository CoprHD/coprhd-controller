/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
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

/**
 * Indication object that has the arrival time and actual indication
 * 
 */
public class ArrivedIndication {

    /**
     * A reference to actual indication
     */
    private Hashtable<String, String> _indication;

    /**
     * Indication arrival time.
     */
    private long _arrivalTime;

    /**
     * @param indication
     * @param arrivalTime
     */
    public ArrivedIndication(Hashtable<String, String> indication,
            long arrivalTime) {
        _arrivalTime = arrivalTime;
        _indication = indication;
    }

    /**
     * Getter of indication
     * 
     * @return
     */
    public Hashtable<String, String> getIndication() {
        return _indication;
    }

    /**
     * Setter of indication
     * 
     * @param indication
     */
    public void setIndication(Hashtable<String, String> indication) {
        _indication = indication;
    }

    /**
     * Getter of Arrival time
     * 
     * @return
     */
    public long getArrivalTime() {
        return _arrivalTime;
    }

    /**
     * Setter of Arrival time
     * 
     * @param arrivalTime
     */
    public void setArrivalTime(long arrivalTime) {
        _arrivalTime = arrivalTime;
    }

}
