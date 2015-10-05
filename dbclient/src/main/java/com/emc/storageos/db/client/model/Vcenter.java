/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * A server that runs a vcenter instance that manages ESX clusters and hosts. 
 * @author elalih
 *
 */
@Cf("Vcenter")
public class Vcenter extends DiscoveredComputeSystemWithAcls {
    private String _userName;
    private String _password;
    private String _ipAddress;
    private Integer _portNumber;
    private String _osVersion;
    private Boolean _useSsl;
    private Boolean _cascadeTenancy;

    /**
     * Gets the login account name
     * 
     * @return the login account name
     */
    @Name("username")
    public String getUsername() {
        return _userName;
    }

    /**
     * Sets the login account name
     * 
     * @param username the login account name
     */
    public void setUsername(String username) {
        this._userName = username;
        setChanged("username");
    }

    /**
     * Gets the login account password
     * 
     * @return the login account password
     */
    @Encrypt
    @Name("password")
    public String getPassword() {
        return _password;
    }

    /**
     * Sets the login account password
     * 
     * @param password the login account password
     */
    public void setPassword(String password) {
        this._password = password;
        setChanged("password");
    }

    /**
     * Gets the vcenter management IP address
     * 
     * @return the vcenter management IP address
     */
    @AlternateId("AltIdIndex")
    @Name("ipAddress")
    public String getIpAddress() {
        return _ipAddress;
    }

    /**
     * Sets the vcenter management IP address
     * 
     * @param ipAddress the management IP address
     */
    public void setIpAddress(String ipAddress) {
        this._ipAddress = ipAddress;
        setChanged("ipAddress");
    }

    /**
     * Gets the vcenter management port number
     * 
     * @return the vcenter management port number
     */
    @Name("portNumber")
    public Integer getPortNumber() {
        return _portNumber;
    }

    /**
     * Sets the vcenter management port number
     * 
     * @return the vcenter management port number
     */
    public void setPortNumber(Integer portNumber) {
        this._portNumber = portNumber;
        setChanged("portNumber");
    }

    /**
     * Gets the OS version of the vcenter instance
     * 
     * @return the OS version of the vcenter instance
     */
    @Name("osVersion")
    public String getOsVersion() {
        return _osVersion;
    }

    /**
     * Sets the OS version of the vcenter
     * 
     * @param osVersion the OS version
     */
    public void setOsVersion(String osVersion) {
        this._osVersion = osVersion;
        setChanged("osVersion");
    }

    /**
     * Get whether SSL should be used when communicating with the vcenter
     * 
     * @return whether SSL should be used when communicating with the vcenter
     */
    @Name("useSSL")
    public Boolean getUseSSL() {
        return _useSsl;
    }

    /**
     * Sets the flag that indicates if SSL should be used when communicating with the vcenter
     * @param useSsl true or false to indicate if SSL should be used
     */
    public void setUseSSL(Boolean useSsl) {
        this._useSsl = useSsl;
        setChanged("useSSL");
    }

    /**
     * Get whether the vCenter's tenancy to be cascaded to its datacenters
     * and hosts and clusters or not.
     *
     * @return whether the vCenter's tenancy to be cascaded to its datacenters
     * and hosts and clusters or not.
     */
    @Name("cascadeTenancy")
    public Boolean getCascadeTenancy() {
        return _cascadeTenancy;
    }

    /**
     * Sets the flag that indicates if vCenter's tenancy to be cascaded to its datacenters
     * and hosts and clusters or not.
     *
     * @param cascadeTenancy true or false to indicate if vCenter's tenancy to be
     *                       cascaded to its datacenters and hosts and clusters or not.
     */
    public void setCascadeTenancy(Boolean cascadeTenancy) {
        this._cascadeTenancy = cascadeTenancy;
        setChanged("cascadeTenancy");
    }

    @Override
    public Object[] auditParameters() {
        return new Object[] {getLabel(), getIpAddress(),
                getPortNumber(), getOsVersion(), getTenant(), getId()};
    }
}
