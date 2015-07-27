/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

@Cf("ComputeSystem")
public class ComputeSystem extends DiscoveredSystemObject {

    private String _ipAddress;
    private Integer _portNumber;
    private String _username;
    private String _password;
    private String _version;
    private String _osInstallNetwork;
    private StringSet vlans;

    private Boolean _secure = false;

    @Name("secure")
    public Boolean getSecure() {
        return _secure == null ? false : _secure;
    }

    public void setSecure(Boolean secure) {
        this._secure = secure;
        setChanged("secure");
    }

    @Name("username")
    public String getUsername() {
        return _username;
    }

    public void setUsername(String username) {
        this._username = username;
        setChanged("username");
    }

    @Encrypt
    @Name("password")
    public String getPassword() {
        return _password;
    }

    public void setPassword(String password) {
        this._password = password;
        setChanged("password");
    }

    @AlternateId("AltIdIndex")
    @Name("ipAddress")
    public String getIpAddress() {
        return _ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this._ipAddress = ipAddress;
        setChanged("ipAddress");
    }

    @Name("portNumber")
    public Integer getPortNumber() {
        return _portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this._portNumber = portNumber;
        setChanged("portNumber");
    }

    @Name("version")
    public String getVersion() {
        return _version;
    }

    public void setVersion(String version) {
        this._version = version;
        setChanged("version");
    }

    @Name("osInstallNetwork")
    public String getOsInstallNetwork() {
        return _osInstallNetwork;
    }

    public void setOsInstallNetwork(String osInstallNetwork) {
        this._osInstallNetwork = osInstallNetwork;
        setChanged("osInstallNetwork");
    }

    @Name("vlans")
    public StringSet getVlans() {
        return vlans;
    }

    public void setVlans(StringSet vlans) {
        this.vlans = vlans;
        setChanged("vlans");
    }
}
