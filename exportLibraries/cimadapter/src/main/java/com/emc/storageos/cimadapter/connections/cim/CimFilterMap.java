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

// Java imports
import java.util.Map;

/**
 * Spring bean for the CIM indication filters map.
 */
public class CimFilterMap {

    // A reference to the filter map.
    private Map<String, CimFilterInfo> _filtersMap;

    /**
     * Getter for the CIM indication filters map.
     * 
     * @return The CIM indication filters map.
     */
    public Map<String, CimFilterInfo> getFilters() {
        return _filtersMap;
    }

    /**
     * Setter for the CIM indication filters map.
     * 
     * @param map The CIM indication filters map.
     */
    public void setFilters(Map<String, CimFilterInfo> filtersMap) {
        _filtersMap = filtersMap;
    }
}