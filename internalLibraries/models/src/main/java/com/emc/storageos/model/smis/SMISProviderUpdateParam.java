/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.smis;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

@XmlRootElement(name = "smis_provider_update")
public class SMISProviderUpdateParam {

    private String name;
    private String ipAddress;
    private Integer portNumber;
    private String userName;
    private String password;
    private Boolean useSSL;

    public SMISProviderUpdateParam() {
    }

    public SMISProviderUpdateParam(String name, String ipAddress,
            Integer portNumber, String userName, String password, Boolean useSSL) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
        this.userName = userName;
        this.password = password;
        this.useSSL = useSSL;
    }

    /**
     * New SMIS Provider name
     */
    @XmlElement
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * New SMIS Provider IP address
     * 
     */
    @XmlElement(name = "ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * New SMIS Provider port number
     * 
     */
    @XmlElement(name = "port_number")
    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    /**
     * New SMIS Provider user name
     * 
     */
    @XmlElement(name = "user_name")
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * New SMIS Provider password
     * 
     */
    @XmlElement
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Specifies whether to use SSL (Secure Sockets Layer)
     * as the authentication method.
     * 
     */
    @XmlElement(name = "use_ssl")
    public Boolean getUseSSL() {
        return useSSL;
    }

    public void setUseSSL(Boolean useSSL) {
        this.useSSL = useSSL;
    }

}
