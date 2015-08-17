/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
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
