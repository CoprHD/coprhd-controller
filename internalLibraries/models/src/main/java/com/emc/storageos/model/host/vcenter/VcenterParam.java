/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.vcenter;

import com.emc.storageos.model.valid.Range;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Captures POST data for a vCenter.
 */
public abstract class VcenterParam {

    private String name;
    private Integer portNumber;
    private String userName;
    private String password;
    private Boolean useSsl;
    private String osVersion;
    
    public VcenterParam() {}
    
    public VcenterParam(String name, Integer portNumber, String userName,
            String password, Boolean useSsl, String osVersion) {
        this.name = name;
        this.portNumber = portNumber;
        this.userName = userName;
        this.password = password;
        this.useSsl = useSsl;
        this.osVersion = osVersion;
    }

    /** 
     * The user label for this vCenter 
     * @valid none
     */
    @XmlElement()
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    /** 
     * The integer port number of the vCenter management interface. 
     * @Range (min=1, max= 65535)
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
     * The user credential used to login to the vCenter. 
     * @valid none
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
     * The password credential used to login to the vCenter. 
     * @valid none
     */
    @XmlElement()
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /** 
     * A flag indicating whether SSL should be used to communicate with the vCenter. 
     * @valid true = use SSL
     * @valid false = do not use SSL
     */
    @XmlElement(name = "use_ssl")
    @JsonProperty("use_ssl")
    public Boolean getUseSsl() {
        return useSsl;
    }

    public void setUseSsl(Boolean useSsl) {
        this.useSsl = useSsl;
    }

    /** 
     * The operating system version of the vCenter. 
     * @valid none
     */
    @XmlElement(name = "os_version")
    @JsonProperty("os_version")
    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    /** Gets the vCenter IP address */
    public abstract String findIpAddress();
}
