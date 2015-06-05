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

// Logger imports
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring bean for a managed CIM indication filter.
 * 
 * This class is for filters that are created on the CIMOM. Connections SHOULD
 * delete them before closing!
 */
public class CimManagedFilterInfo extends CimFilterInfo {

    // The key identifies the managed filter.
    private String _key = "";

    // The managed filter query.
    private String _query = "";

    // The query language for the managed filter.
    private String _queryLanguage = CimConstants.DEFAULT_QUERY_LANGUAGE;
    
    // The logger.
    private static final Logger s_logger = LoggerFactory.getLogger(CimManagedFilterInfo.class);

    /**
     * Getter for the filter key.
     * 
     * @return The filter key.
     */
    public String getKey() {
        return _key;
    }

    /**
     * Setter for the filter key.
     * 
     * This key is used to name the CIM indication filter object.
     */
    public void setKey(String value) {
        _key = value;
    }

    /**
     * Getter for the filter query.
     * 
     * @return The filter query.
     */
    public String getQuery() {
        return _query;
    }

    /**
     * Setter for the filter query.
     * 
     * @param value The filter query.
     */
    public void setQuery(String value) {
        _query = value;
    }

    /**
     * Getter for the filter query language.
     * 
     * @return The filter query language.
     */
    public String getQueryLanguage() {
        return _queryLanguage;
    }

    /**
     * Setter for the filter query language.
     * 
     * @param value The filter query language.
     */
    public void setQueryLanguage(String value) {
        _queryLanguage = value;
    }
    
    /**
     * Setter for the CIM indication filter name.
     * 
     * @param listenerHostIP The IP address of the host that is listening for
     *        indications resulting from this filter.
     */
    @Override
    public void setName(String listenerHostIP) {

        StringBuilder strBuilder = new StringBuilder();

        // The prefix for the filter name is the IP address for the listener
        // host that will receive indications resulting from this filter.
        // Using the host allows us to easily identify the owner of the filters
        // created on the ECOM provider.
        String filterNamePrefix;
        if ((listenerHostIP != null) && (listenerHostIP.length() != 0)) {
            filterNamePrefix = listenerHostIP;
        } else {
            filterNamePrefix = String.valueOf(System.currentTimeMillis());
        }
        strBuilder.append(filterNamePrefix);
        strBuilder.append(CimConstants.PATH_NAME_DELIMITER);
        strBuilder.append(_key);

        String filterName = strBuilder.toString();
        s_logger.debug("Managed Filter Name is {}", filterName);
        super.setName(filterName);
    }
}