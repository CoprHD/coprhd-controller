/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.util.EndpointUtility;

/**
 * A ManagementStation that manage multiple Host/Manage system.
 * 
 * @author sauraa
 *
 */
@Cf("ManagementStation")
public class ManagementStation extends DiscoveredComputeSystemWithAcls {
    private String _type;
    private String _managementStationName;
    private String _userName;
    private String _password;
    private String _ipAddress;
    private Integer _portNumber;
    private String _osVersion;
    private Boolean _useSsl;

    /**
     * The expected list of host OS types
     * 
     */
    public enum ManagementStationType {
        HMC, Other
    }

    /**
     * Gets the ManagementStation type which is an instance of {@link ManagementStationType}
     * 
     * @return The type of ManagementStation.
     */
    @Name("type")
    public String getType() {
        return _type;
    }

    /**
     * Sets the type of ManagementStation
     * 
     * @see ManagementStationType
     * @param type the ManagementStation type
     */
    public void setType(String type) {
        this._type = type;
        setChanged("type");
    }

    /**
     * The short or fully qualified host name
     * 
     * @return the short or fully qualified host name
     */
    @Name("managementStationName")
    @AlternateId("AltIdIndex")
    public String getManagementStationName() {
        return _managementStationName;
    }

    /**
     * Sets the short or fully qualified host name or an IP address
     * 
     * @param hostName the host name
     */
    public void setManagementStationName(String cs) {
        this._managementStationName = EndpointUtility.changeCase(cs);
        setChanged("managementStationName");
    }

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
     * Gets the ManagementStation management IP address
     * 
     * @return the ManagementStation management IP address
     */
    @AlternateId("AltIdIndex")
    @Name("ipAddress")
    public String getIpAddress() {
        return _ipAddress;
    }

    /**
     * Sets the ManagementStation management IP address
     * 
     * @param ipAddress the management IP address
     */
    public void setIpAddress(String ipAddress) {
        this._ipAddress = ipAddress;
        setChanged("ipAddress");
    }

    /**
     * Gets the ManagementStation management port number
     * 
     * @return the ManagementStation management port number
     */
    @Name("portNumber")
    public Integer getPortNumber() {
        return _portNumber;
    }

    /**
     * Sets the ManagementStation management port number
     * 
     * @return the ManagementStation management port number
     */
    public void setPortNumber(Integer portNumber) {
        this._portNumber = portNumber;
        setChanged("portNumber");
    }

    /**
     * Gets the OS version of the ManagementStation instance
     * 
     * @return the OS version of the ManagementStation instance
     */
    @Name("osVersion")
    public String getOsVersion() {
        return _osVersion;
    }

    /**
     * Sets the OS version of the ManagementStation
     * 
     * @param osVersion the OS version
     */
    public void setOsVersion(String osVersion) {
        this._osVersion = osVersion;
        setChanged("osVersion");
    }

    /**
     * Get whether SSL should be used when communicating with the ManagementStation
     * 
     * @return whether SSL should be used when communicating with the ManagementStation
     */
    @Name("useSSL")
    public Boolean getUseSSL() {
        return _useSsl;
    }

    /**
     * Sets the flag that indicates if SSL should be used when communicating with the ManagementStation
     * 
     * @param useSsl true or false to indicate if SSL should be used
     */
    public void setUseSSL(Boolean useSsl) {
        this._useSsl = useSsl;
        setChanged("useSSL");
    }

    @Override
    public Object[] auditParameters() {
        return new Object[] { getLabel(), getIpAddress(),
                getPortNumber(), getOsVersion(), getId() };
    }

}
