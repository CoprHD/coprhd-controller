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

package com.emc.storageos.cimadapter.connections.cim;

// CIM imports
import javax.cim.CIMInstance;

/**
 * A wrapper for queueing an indication and its destination URL.
 */
public class CimQueuedIndication {

    // The destination URL for the CIM indication.
    private String _url;

    // A reference to the CIM indication.
    private CIMInstance _indication;

    /**
     * Constructs a queue item for the given indication.
     * 
     * @param url The destination URL.
     * @param indication The CIM indication.
     */
    public CimQueuedIndication(String url, CIMInstance indication) {
        _url = url;
        _indication = indication;
    }

    /**
     * Getter for the destination URL.
     * 
     * @return The destination URL.
     */
    public String getURL() {
        return _url;
    }

    /**
     * Getter for the CIM indication.
     * 
     * @return The CIM indication.
     */
    public CIMInstance getIndication() {
        return _indication;
    }
}