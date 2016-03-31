/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.systems;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

@XmlRootElement(name = "storage_system_create")
public class StorageSystemRequestParam {

    private String name;
    private String systemType;
    private String ipAddress;
    private Integer portNumber;
    private String userName;
    private String password;
    private String serialNumber;
    private String smisProviderIP;
    private Integer smisPortNumber;
    private String smisUserName;
    private String smisPassword;
    private Boolean smisUseSSL;

    public StorageSystemRequestParam() {
    }

    /**
     * Name of the storage system
     * 
     */
    @XmlElement(required = true, name = "name")
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Type of the storage system allowed on POST
     * Valid values: 
     *  isilon
     *  vnxfile
     *  netapp
     *  vnxe
     *  netappc
     *  ecs
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
     * IP Address of the storage system
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
     * Port Number used to connect to the storage system
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
     * Username to connect to storage system
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
     * Password to connect to storage system
     * 
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
     * Serial ID of the storage system
     * 
     */
    @XmlElement(name = "serial_number")
    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    /**
     * IP Address of SMIS Provider
     * This field is required for storage systems of type 'vnxfile'.
     * It is ignored for other storage system types and can be null.
     * 
     */
    @XmlElement(name = "smis_provider_ip")
    public String getSmisProviderIP() {
        return smisProviderIP;
    }

    public void setSmisProviderIP(String smisProviderIP) {
        this.smisProviderIP = smisProviderIP;
    }

    /**
     * Port number of SMIS Provider to connect to
     * This field is required for storage systems of type 'vnxfile'.
     * It is ignored for other storage system types and can be null.
     * 
     */
    @XmlElement(name = "smis_port_number")
    public Integer getSmisPortNumber() {
        return smisPortNumber;
    }

    public void setSmisPortNumber(Integer smisPortNumber) {
        this.smisPortNumber = smisPortNumber;
    }

    /**
     * Username to connect to SMIS Provider
     * This field is required for storage systems of type 'vnxfile'.
     * It is ignored for other storage system types and can be null.
     * 
     */
    @XmlElement(name = "smis_user_name")
    public String getSmisUserName() {
        return smisUserName;
    }

    public void setSmisUserName(String smisUserName) {
        this.smisUserName = smisUserName;
    }

    /**
     * Password to connect to SMIS provider
     * This field is required for storage systems of type 'vnxfile'.
     * It is ignored for other storage system types and can be null.
     * 
     */
    @XmlElement(name = "smis_password")
    public String getSmisPassword() {
        return smisPassword;
    }

    public void setSmisPassword(String smisPassword) {
        this.smisPassword = smisPassword;
    }

    /**
     * Determines the protocol used for connection purposes.
     * If HTTPS, then set true, else false.
     * This field is required for storage systems of type 'vnxfile'.
     * It is ignored for other storage system types and can be null.
     * 
     */
    @XmlElement(name = "smis_use_ssl")
    public Boolean getSmisUseSSL() {
        return smisUseSSL;
    }

    public void setSmisUseSSL(Boolean smisUseSSL) {
        this.smisUseSSL = smisUseSSL;
    }

}
