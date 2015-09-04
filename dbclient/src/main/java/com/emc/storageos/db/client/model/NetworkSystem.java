/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * NetworkDevice data object
 */
@Cf("NetworkSystem")
public class NetworkSystem extends DiscoveredSystemObject {

    // serial number
    private String _serialNumber;

    // secondary/backup management interface IP address
    private String _secondaryIP;

    // management port number
    private Integer _portNumber;

    // management interface user
    // TODO - this needs to be encrypted
    private String _username;

    // management interface password
    // TODO - this needs to be encrypted
    private String _password;

    // management interface IP address
    private String _ipAddress;

    // SMI-S interface IP address
    private String _smisProviderIP;

    // SMI-S port number (5989)
    private Integer _smisPortNumber;

    // SMI-S user.
    private String _smisUserName;

    // SMI-S password.
    private String _smisPassword;

    // SMI-S flag indicates whether or not to use SSL protocol.
    private Boolean _smisUseSSL;

    // software version
    private String _version;

    // system uptime
    private String _uptime;

    @Name("serialNumber")
    public String getSerialNumber() {
        return _serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this._serialNumber = serialNumber;
        setChanged("serialNumber");
    }

    @Name("ipAddress")
    public String getIpAddress() {
        return _ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this._ipAddress = ipAddress;
        setChanged("ipAddress");
    }

    @Name("secondaryIP")
    public String getSecondaryIP() {
        return _secondaryIP;
    }

    public void setSecondaryIP(String secondaryIP) {
        _secondaryIP = secondaryIP;
        setChanged("secondaryIP");
    }

    @Name("portNumber")
    public Integer getPortNumber() {
        return _portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this._portNumber = portNumber;
        setChanged("portNumber");
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
    @Name("smisProviderIP")
    public String getSmisProviderIP() {
        return _smisProviderIP;
    }

    public void setSmisProviderIP(String smisProviderIP) {
        this._smisProviderIP = smisProviderIP;
        setChanged("smisProviderIP");
    }

    @Name("smisPortNumber")
    public Integer getSmisPortNumber() {
        return _smisPortNumber;
    }

    public void setSmisPortNumber(Integer smisPortNumber) {
        this._smisPortNumber = smisPortNumber;
        setChanged("smisPortNumber");
    }

    @Name("smisUserName")
    public String getSmisUserName() {
        return _smisUserName;
    }

    public void setSmisUserName(String smisUserName) {
        this._smisUserName = smisUserName;
        setChanged("smisUserName");
    }

    @Encrypt
    @Name("smisPassword")
    public String getSmisPassword() {
        return _smisPassword;
    }

    public void setSmisPassword(String smisPassword) {
        this._smisPassword = smisPassword;
        setChanged("smisPassword");
    }

    @Name("smisUseSSL")
    public Boolean getSmisUseSSL() {
        return (_smisUseSSL != null) && _smisUseSSL;
    }

    public void setSmisUseSSL(Boolean smisUseSSL) {
        this._smisUseSSL = smisUseSSL;
        setChanged("smisUseSSL");
    }

    @Name("version")
    public String getVersion() {
        return _version;
    }

    public void setVersion(String version) {
        this._version = version;
        setChanged("version");
    }

    @Name("uptime")
    public String getUptime() {
        return _uptime;
    }

    public void setUptime(String uptime) {
        this._uptime = uptime;
        setChanged("uptime");
    }
}
