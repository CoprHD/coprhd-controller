/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.util.EndpointUtility;

/**
 * IPv4 or IPV6 interface of a host.
 */
@Cf("IpInterface")
public class IpInterface extends HostInterface {

    private String _ipAddress;
    private String _netmask;
    private Integer _prefixLength;
    private String _scopeId;

    /**
     * Default Constructor.
     */
    public IpInterface() {
        setIsManualCreation(true);
    }

    /**
     * The netmask of the IPv4 interface
     * 
     * @return the IPv4 interface netmask
     */
    @Name("netmask")
    public String getNetmask() {
        return _netmask;
    }

    /**
     * Sets the netmask of the IPv4 interface
     * 
     * @param netmask of the IPv4 interface
     */
    public void setNetmask(String netmask) {
        this._netmask = netmask;
        setChanged("netmask");
    }

    /**
     * Gets the IPv4 or IPv6 address of this interface
     * 
     * @return the IPv4 or IPv6 address of this interface
     */
    @Name("ipAddress")
    @AlternateId("AltIdIndex")
    public String getIpAddress() {
        return _ipAddress;
    }

    /**
     * Sets the IP address for this interface
     * 
     * @param ipAddress the IP address of the interface
     */
    public void setIpAddress(String ipAddress) {
        this._ipAddress = EndpointUtility.changeCase(ipAddress);
        setChanged("ipAddress");
    }

    /**
     * Gets the IPv6 prefix length
     * 
     * @return the IPv6 prefix length
     */
    @Name("prefixLength")
    public Integer getPrefixLength() {
        return _prefixLength;
    }

    /**
     * Sets the IPv6 prefix length
     * 
     * @param prefixLength the IPv6 prefix length
     */
    public void setPrefixLength(Integer prefixLength) {
        this._prefixLength = prefixLength;
        setChanged("prefixLength");
    }

    /**
     * Gets the IPv6 scope id
     * 
     * @return the IPv6 scope id
     */
    @Name("scopeId")
    public String getScopeId() {
        return _scopeId;
    }

    /**
     * Sets the IPv6 scope Id
     * 
     * @param scopeId the IPv6 scope Id
     */
    public void setScopeId(String scopeId) {
        this._scopeId = scopeId;
        setChanged("scopeId");
    }

    @Override
    public Object[] auditParameters() {
        return new Object[] { getIpAddress(),
                getHost(), getId() };
    }
}
