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
 * Spring bean for the listener configuration.
 */
public class CimListenerInfo {
    
    // The listener host IP address.
    private String _hostIP;

    // The listener protocol.
    private String _protocol = CimConstants.DEFAULT_PROTOCOL;

    // The listener port.
    private int _port = 0;

    // The listener queue size.
    private int _queueSize = CimConstants.DEFAULT_QUEUE_SIZE;
    
    // Default SMI-S's SSL port to pull public certificates from SMI-S 
    private int defaultSMISSSLPort;
    
    /**
     * Getter for the IP address for the listener host.
     * 
     * @return The IP address for the listener host.
     */
    public String getHostIP() {
        return _hostIP;
    }

    /**
     * Setter for the IP address for the listener host.
     * 
     * @param hostIP The IP address for the listener host.
     */
    public void setHostIP(String hostIP) {
        _hostIP = hostIP;
    }

    /**
     * Getter for the listener protocol.
     * 
     * @return The listener protocol.
     */
    public String getProtocol() {
        return _protocol;
    }

    /**
     * Setter for the listener protocol.
     * 
     * @param value The listener protocol.
     */
    public void setProtocol(String value) {
        _protocol = value;
    }

    /**
     * Getter for the listener port.
     * 
     * @return The listener port.
     */
    public int getPort() {
        return _port;
    }

    /**
     * Setter for the listener port.
     * 
     * @param value The listener port.
     */
    public void setPort(int value) {
        _port = value;
    }

    /**
     * Getter for the listener queue size.
     * 
     * @return The listener queue size.
     */
    public int getQueueSize() {
        return _queueSize;
    }

    /**
     * Setter for the listener queue size.
     * 
     * @param value The listener queue size.
     */
    public void setQueueSize(int value) {
        _queueSize = value;
    }
    
    /**
     * Returns default smi-s ssl port number
     * @return default smi-s ssl port number
     */
    public int getDefaultSMISSSLPort() {
        return defaultSMISSSLPort;
    }
    
    /**
     * Setter for the default smi-s ssl port number
     * @param defaultSMISSSLPort
     */
    public void setDefaultSMISSSLPort(int defaultSMISSSLPort) {
        this.defaultSMISSSLPort = defaultSMISSSLPort;
    }    

    @Override
    /**
     * Compares this CimListenerInfo object with another CimListenerInfo
     * object for equality.
     * 
     * Immediately returns false if the given object is not a
     * CimListenerInfo object.
     * 
     * Two CimListenerInfo objects are equal if they have the
     * same host IP, protocol, port and queue size values.
     * 
     * @param obj the CimListenerInfo object to compare against
     * 
     * @return true if the objects are equal, false otherwise.
     */
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof CimListenerInfo)) {
            return false;
        }

        CimListenerInfo info = (CimListenerInfo) obj;
        if (!_hostIP.equals(info.getHostIP())) {
            return false;
        }
        if (_port != info.getPort()) {
            return false;
        }
        if (_queueSize != info.getQueueSize()) {
            return false;
        }
        if (!_protocol.equals(info.getProtocol())) {
            return false;
        }

        return true;
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        final int PRIME = 31;
        int hash = 1;
        hash = hash * PRIME + ((_hostIP == null) ? 0 : _hostIP.hashCode());
        hash = hash * PRIME + ((_protocol == null) ? 0 : _protocol.hashCode());
        hash = hash * PRIME + _port;
        hash = hash * PRIME + _queueSize;
        return hash;
    }
}