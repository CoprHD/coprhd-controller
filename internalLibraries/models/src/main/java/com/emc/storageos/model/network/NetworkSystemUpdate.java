/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.valid.Length;
import com.emc.storageos.model.valid.Range;

/**
 * These are the arguments used for updating (PUT) to a Network System.
 */
@XmlRootElement(name = "network_system_update")
public class NetworkSystemUpdate {

    private String name;
    private String ipAddress;
    private Integer portNumber;
    private String userName;
    private String password;
    private String smisProviderIp;
    private Integer smisPortNumber;
    private String smisUserName;
    private String smisPassword;
    private Boolean smisUseSsl;
    
    public NetworkSystemUpdate() {}

    /** 
     * Name of the Network System
     * 
     * @valid none
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
     * IP address of the Network System for SSH communication.
     * This field is applicable for network systems of type 'mds'.
     * It is ignored for 'brocade' type network systems and can be null.
     * 
     * @valid example: 10.247.12.99
     */
    @XmlElement(name = "ip_address")
    @JsonProperty("ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * Port number of the Network System for SSH communication.
     * This field is applicable for network systems of type 'mds'.
     * It is ignored for 'brocade' type network systems and can be null.
     */
    @XmlElement(name = "port_number")
    @Range(min=1,max=65535)
    @JsonProperty("port_number")
    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    /**
     * User name used for SSH login to the Network System.
     * This field is applicable for network systems of type 'mds'.
     * It is ignored for 'brocade' type network systems and can be null.
     * 
     * @valid example: user1
     */
    @XmlElement(name = "user_name")
    @JsonProperty("user_name")
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Password used for SSH login to the Network System.
     * This field is applicable for network systems of type 'mds'.
     * It is ignored for 'brocade' type network systems and can be null.
     * 
     * @valid example: abc1
     */
    @XmlElement()
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * IP Address of the SMIS Provider
     * This field is applicable for network systems of type 'brocade'.
     * It is ignored for 'mds' type network systems and can be null.
     * 
     * @valid example: 10.247.12.100
     */
    @XmlElement(name = "smis_provider_ip")
    @JsonProperty("smis_provider_ip")
    public String getSmisProviderIp() {
        return smisProviderIp;
    }

    public void setSmisProviderIp(String smisProviderIp) {
        this.smisProviderIp = smisProviderIp;
    }

    /**
     * Port number of the SMIS Provider to connect to
     * This field is applicable for network systems of type 'brocade'.
     * It is ignored for 'mds' type network systems and can be null.
     */
    @XmlElement(name = "smis_port_number")
    @Range(min=1,max=65535)
    @JsonProperty("smis_port_number")
    public Integer getSmisPortNumber() {
        return smisPortNumber;
    }

    public void setSmisPortNumber(Integer smisPortNumber) {
        this.smisPortNumber = smisPortNumber;
    }

    /**
     * User name to connect to the SMIS Provider
     * This field is applicable for network systems of type 'brocade'.
     * It is ignored for 'mds' type network systems and can be null.
     * 
     * @valid example: smisuser1
     */
    @XmlElement(name = "smis_user_name")
    @JsonProperty("smis_user_name")
    public String getSmisUserName() {
        return smisUserName;
    }

    public void setSmisUserName(String smisUserName) {
        this.smisUserName = smisUserName;
    }

    /**
     * Password to connect to the SMIS provider
     * This field is applicable for network systems of type 'brocade'.
     * It is ignored for 'mds' type network systems and can be null.
     * 
     * @valid example: smispassword1
     */
    @XmlElement(name = "smis_password")
    @JsonProperty("smis_password")
    public String getSmisPassword() {
        return smisPassword;
    }

    public void setSmisPassword(String smisPassword) {
        this.smisPassword = smisPassword;
    }

    /**
     * Determines the protocol used for connection purposes.
     * If HTTPS, then set true, else false.
     * This field is applicable for network systems of type 'brocade'.
     * It is ignored for 'mds' type network systems and can be null.
     * 
     * @valid true
     * @valid false
     */
    @XmlElement(name = "smis_use_ssl")
    @JsonProperty("smis_use_ssl")
    public Boolean getSmisUseSsl() {
        return smisUseSsl;
    }

    public void setSmisUseSsl(Boolean smisUseSsl) {
        this.smisUseSsl = smisUseSsl;
    }
 
}
