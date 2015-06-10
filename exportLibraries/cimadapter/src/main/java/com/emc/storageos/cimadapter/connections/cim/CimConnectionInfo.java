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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bean for specifying a CIM connection.
 */
public class CimConnectionInfo {

    // The type of CIM connection.
    private String _type = CimConstants.ECOM_CONNECTION_TYPE;

    // The host for the CIM connection.
    private String _host = CimConstants.DFLT_CIM_CONNECTION_HOST;

    // The port for the CIM connection.
    private int _port = CimConstants.DFLT_CIM_CONNECTION_PORT;

    // The user for the CIM connection.
    private String _user = "";

    // The password for the CIM connection.
    private String _password = "";

    // The interop NS for the CIM connection.
    private String _interopNS = CimConstants.DFLT_CIM_CONNECTION_INTEROP_NS;

    // The implementation NS for the CIM connection.
    private String _implNS = CimConstants.DFLT_CIM_CONNECTION_IMPL_NS;

    // Whether or not SSL is used for the CIM connection.
    private boolean _useSSL = true;

    // A map of the connection parameters.
    private Map<String, String> _connectionParams = new HashMap<String, String>();

    /**
     * Getter for the connection type.
     * 
     * @return The connection type.
     */
    public String getType() {
        return _type;
    }

    /**
     * Setter for the connection type.
     * 
     * @param value The connection type.
     */
    public void setType(String value) {
        _type = value;
        _connectionParams.put(CimConstants.CIM_TYPE, value);
    }

    /**
     * Getter for the connection host.
     * 
     * @return The connection host.
     */
    public String getHost() {
        return _host;
    }

    /**
     * Setter for the connection host.
     * 
     * @param value The connection host.
     */
    public void setHost(String value) {
        _host = value;
        _connectionParams.put(CimConstants.CIM_HOST, value);
    }

    /**
     * Getter for the connection port.
     * 
     * @return The connection port.
     */
    public int getPort() {
        return _port;
    }

    /**
     * Setter for the connection port.
     * 
     * @param value The connection port.
     */
    public void setPort(int value) {
        _port = value;
        _connectionParams.put(CimConstants.CIM_PORT, Integer.toString(value));
    }

    /**
     * Getter for the connection user.
     * 
     * @return The connection user.
     */
    public String getUser() {
        return _user;
    }

    /**
     * Setter for the connection user.
     * 
     * @param value The connection user.
     */
    public void setUser(String value) {
        _user = value;
        _connectionParams.put(CimConstants.CIM_USER, value);
    }

    /**
     * Getter for the connection password.
     * 
     * @return The connection password.
     */
    public String getPassword() {
        return _password;
    }

    /**
     * Setter for the connection password.
     * 
     * @param value The connection password.
     */
    public void setPassword(String value) {
        _password = value;
        _connectionParams.put(CimConstants.CIM_PW, value);
    }

    /**
     * Getter for the connection interop NS.
     * 
     * @return The connection interop NS.
     */
    public String getInteropNS() {
        return _interopNS;
    }

    /**
     * Setter for the connection interop NS.
     * 
     * @param value The connection interop NS.
     */
    public void setInteropNS(String value) {
        _interopNS = value;
        _connectionParams.put(CimConstants.CIM_INTEROP_NS, value);
    }

    /**
     * Getter for the connection implementation NS.
     * 
     * @return The connection implementation NS.
     */
    public String getImplNS() {
        return _implNS;
    }

    /**
     * Setter for the connection implementation NS.
     * 
     * @param value The connection implementation NS.
     */
    public void setImplNS(String value) {
        _implNS = value;
        _connectionParams.put(CimConstants.CIM_IMPL_NS, value);
    }

    /**
     * Getter for whether or not the connection uses SSL.
     * 
     * @return true if the connection uses SSL, false otherwise.
     */
    public boolean getUseSSL() {
        return _useSSL;
    }

    /**
     * Setter for whether or not the connection uses SSL.
     * 
     * @param value true if the connection should use SSL, false otherwise.
     */
    public void setUseSSL(boolean value) {
        _useSSL = value;
        _connectionParams.put(CimConstants.CIM_USE_SSL, Boolean.toString(value));
    }

    /**
     * Returns the keys for connection parameters
     * 
     * @return A set containing the keys for the connection parameters.
     */
    public Set<String> getConnectionParameterNames() {
        return _connectionParams.keySet();
    }

    /**
     * Get the connection parameters with the passed key.
     * 
     * @param name The key for the connection parameter.
     * 
     * @return The parameter value.
     */
    public String getConnectionParameter(String name) {
        return _connectionParams.get(name);
    }
}