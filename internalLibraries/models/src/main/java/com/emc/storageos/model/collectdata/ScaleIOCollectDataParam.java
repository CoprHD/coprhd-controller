/**
 * Copyright (c) 2017 EMC Corporation
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
     * provider, typically 443.
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
