/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.collectdata;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "scaleio_status_param")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ScaleIOCollectDataParam {
    private String iPAddress;
    private Integer portNumber;
    private String userName;
    private Boolean useSSL;
    private String password;

    public ScaleIOCollectDataParam() {
    }

    /**
     * provider's IP address.
     * 
     */
    @XmlElement(name = "ip_address")
    public String getIPAddress() {
        return iPAddress;
    }

    public void setIPAddress(String iPAddress) {
        this.iPAddress = iPAddress;
    }

    /**
     * The port number used to connect with the
     * provider, typically 5988 or 5989.
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
     * Login credential at the provider.
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
     * Whether or not secure SSL connection is used.
     * 
     */
    @XmlElement(name = "use_ssl")
    public Boolean getUseSSL() {
        return useSSL;
    }

    public void setUseSSL(Boolean useSSL) {
        this.useSSL = useSSL;
    }

    /**
     * URL of the Element Management system that is associated with the Provider.
     * 
     */
    @XmlElement(name = "password")
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
