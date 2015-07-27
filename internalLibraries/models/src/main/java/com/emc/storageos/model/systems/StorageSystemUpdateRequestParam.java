/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.systems;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.valid.Length;
import com.emc.storageos.model.valid.Range;

@XmlRootElement(name = "storage_system_update")
public class StorageSystemUpdateRequestParam {

    private String name;
    private String ipAddress;
    private Integer portNumber;
    private String userName;
    private String password;
    private String smisProviderIP;
    private Integer smisPortNumber;
    private String smisUserName;
    private String smisPassword;
    private Boolean smisUseSSL;
    private Integer maxResources; 
    private Boolean isUnlimitedResourcesSet;
    
    public StorageSystemUpdateRequestParam() {}

    /**
     * Name of the storage system
     * 
     * @valid none
     */
    @XmlElement(name = "name")
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * IP Address of the storage system
     * 
     * @valid none
     */
    @XmlElement(name = "ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * Port Number used to connect to the storage system
     * 
     * @valid none
     */
    @XmlElement(name = "port_number")
    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    /**
     * Username to connect to storage system
     * 
     * @valid none
     */
    @XmlElement(name = "user_name", nillable = true)
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
     * @valid none
     */
    @XmlElement(name = "password", nillable = true)
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * IP Address of SMIS Provider
     * This field is applicable for storage systems of type 'vnxfile'.
     * It is ignored for other storage system types and can be null.
     * 
     * @valid none
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
     * This field is applicable for storage systems of type 'vnxfile'.
     * It is ignored for other storage system types and can be null.
     * 
     * @valid none
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
     * This field is applicable for storage systems of type 'vnxfile'.
     * It is ignored for other storage system types and can be null.
     * 
     * @valid none
     */
    @XmlElement(name = "smis_user_name")
    public String getSmisUserName() {
        return smisUserName;
    }

    public void setSmisUserName(String smisUserName) {
        this.smisUserName = smisUserName;
    }

    /**
     * Password to connect to SMIS Provider
     * This field is applicable for storage systems of type 'vnxfile'.
     * It is ignored for other storage system types and can be null.
     * 
     * @valid none
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
     * This field is applicable for storage systems of type 'vnxfile'.
     * It is ignored for other storage system types and can be null.
     * 
     *  @valid true
     *  @valid false
     */
    @XmlElement(name = "smis_use_ssl")
    public Boolean getSmisUseSSL() {
        return smisUseSSL;
    }

    public void setSmisUseSSL(Boolean smisUseSSL) {
        this.smisUseSSL = smisUseSSL;
    }

    /**
     * Determines the maximum number of resources that are allowed to be created on the storage system
     * 
     * @valid none
     */
    @XmlElement(name = "max_resources")
    @Range(min=0, max=Integer.MAX_VALUE)
    public Integer getMaxResources() {
        return maxResources;
    }

   public void setMaxResources(Integer maxResources) {
        this.maxResources = maxResources;
    }
   
   /**
    * Whether limit on number of Resources has been set
    * 
    * @valid none
    */
   @XmlElement(name = "unlimited_resources")
   public Boolean getIsUnlimitedResourcesSet() {
       return isUnlimitedResourcesSet;
   }

   public void setIsUnlimitedResourcesSet(Boolean isUnlimitedResourcesSet){
       this.isUnlimitedResourcesSet = isUnlimitedResourcesSet;
   }

}
