/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

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