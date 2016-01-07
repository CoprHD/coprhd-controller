/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.protection;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

@XmlRootElement(name = "protection_system_create")
public class ProtectionSystemRequestParam {

    private String label;
    private String systemType;
    private String ipAddress;
    private Integer portNumber;
    private String userName;
    private String password;
    private String registrationMode;

    public ProtectionSystemRequestParam() {
    }

    public ProtectionSystemRequestParam(String label, String systemType,
            String ipAddress, Integer portNumber, String userName,
            String password, String registrationMode) {
        this.label = label;
        this.systemType = systemType;
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
        this.userName = userName;
        this.password = password;
        this.registrationMode = registrationMode;
    }

    /**
     * The label given to the new Protection System
     * 
     */
    @XmlElement(required = true, name = "name")
    @Length(min = 2, max = 128)
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * System type for the new Protection System
     * 
     */
    @XmlElement(required = true, name = "system_type")
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    /**
     * IP Address of the Protection System device
     * 
     */
    @XmlElement(required = true, name = "ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * Management Port Number of the Protection System device
     * 
     */
    @XmlElement(required = true, name = "port_number")
    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    /**
     * The user name to connect to the Protection System device management port
     * 
     */
    @XmlElement(required = true, nillable = true, name = "user_name")
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * The password to connect to the Protection System device management port
     * 
     */
    @XmlElement(name = "password", required = true, nillable = true)
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * The registration mode for the Protection System
     * Valid values:
     *  REGISTERED
     *  UNREGISTERED
     */
    @XmlElement(name = "registration_mode")
    public String getRegistrationMode() {
        return registrationMode;
    }

    public void setRegistrationMode(String registrationMode) {
        this.registrationMode = registrationMode;
    }

}
