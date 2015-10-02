/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.protection;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "protection_system_update")
public class ProtectionSystemUpdateRequestParam extends RPClusterVirtualArrayResourceUpdateParam {

    private String ipAddress;
    private Integer portNumber;
    private String userName;
    private String password;

    public ProtectionSystemUpdateRequestParam() {
    }

    public ProtectionSystemUpdateRequestParam(String ipAddress,
            Integer portNumber, String userName, String password) {
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
        this.userName = userName;
        this.password = password;
    }

    /**
     * Updated IP Address of the Protection System device
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
     * Updated Management Port Number of the Protection System device
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
     * The updated user name to connect to the Protection System device management port
     * 
     */
    @XmlElement(name = "user_name", nillable = true)
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * The updated password to connect to the Protection System device management port
     * 
     */
    @XmlElement(name = "password", nillable = true)
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
