/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.smis;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

@XmlRootElement(name = "smis_provider_create")
public class SMISProviderCreateParam {

    private String name;
    private String ipAddress;
    private Integer portNumber;
    private String userName;
    private String password;
    private Boolean useSSL;

    public SMISProviderCreateParam() {}
    
    public SMISProviderCreateParam(String name, String ipAddress,
            Integer portNumber, String userName, String password, Boolean useSSL) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
        this.userName = userName;
        this.password = password;
        this.useSSL = useSSL;
    }

    /**
     * Name of the SMIS Provider 
     */     
    @XmlElement(required = true)
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * IP address of the SMIS provider. 
     * @valid example: 10.247.99.87
     */     
    @XmlElement(required = true, name = "ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * The port number of the SMIS provider  
     * @valid example: 5989 
     */     
    @XmlElement(required = true, name = "port_number")
    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    /**
     * User name of the SMIS provider  
     * @valid example: none 
     */     
    @XmlElement(required = true, name = "user_name")
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Password of the SMIS provider  
     * @valid example: none 
     */     
    @XmlElement(required = true)
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Specifies whether to use SSL (Secure Sockets Layer) 
     * as the authentication method.
     * @valid none 
     */     
    @XmlElement(required = true, name = "use_ssl")
    public Boolean getUseSSL() {
        return useSSL;
    }

    public void setUseSSL(Boolean useSSL) {
        this.useSSL = useSSL;
    }
    
}
