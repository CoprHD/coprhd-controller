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
 * Spring bean for a CIM indication filter.
 * 
 * This class is used for filters that already exist on the CIMOM. Connections
 * SHOULD not delete them!
 */
public class CimFilterInfo {

    // The filter name.
    private String _name;

    /**
     * Getter for the CIM indication filter name.
     * 
     * @return value The CIM indication filter name.
     */
    public String getName() {
        return _name;
    }

    /**
     * Setter for the CIM indication filter name.
     * 
     * @param value The CIM indication filter name.
     */
    public void setName(String value) {
        _name = value;
    }
}