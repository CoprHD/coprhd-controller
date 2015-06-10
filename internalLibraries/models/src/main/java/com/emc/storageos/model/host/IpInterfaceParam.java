/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import com.emc.storageos.model.valid.Range;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Captures POST/PUT data for a host IP interface.
 */
public abstract class IpInterfaceParam {

    private Integer netmask;
    private Integer prefixLength;
    private String scopeId;
    private String name;
    
    public IpInterfaceParam() {}
    
    public IpInterfaceParam(Integer netmask, Integer prefixLength,
            String scopeId, String name) {
        this.netmask = netmask;
        this.prefixLength = prefixLength;
        this.scopeId = scopeId;
        this.name = name;
    }
    
    /** The netmask of an IPv4 address expressed as the 
     * integer prefix in an IPv4 address CIDR notation. 
     * For example 24 for 255.255.255.0 and 16 for 
     * 255.255.0.0 etc.. 
     */
    @XmlElement()
    @Range(min=1,max=32)
    public Integer getNetmask() {
        return netmask;
    }
    
    public void setNetmask(Integer netmask) {
        this.netmask = netmask;
    }
    
    /** The IPv6 prefix length. 
     */
    @XmlElement(name = "prefix_length")
    @Range(min=1,max=128)
    @JsonProperty("prefix_length")
    public Integer getPrefixLength() {
        return prefixLength;
    }
    
    public void setPrefixLength(Integer prefixLength) {
        this.prefixLength = prefixLength;
    }
    
    /** The IPv6 scope id. 
     * @valid none
     */
    @XmlElement(name = "scope_id")
    @JsonProperty("scope_id")
    public String getScopeId() {
        return scopeId;
    }
    
    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }
    
    /** The name of the IpInterface
     * @valid none
     */
    @XmlElement()
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    /** Gets the ip interface address */
    public abstract String findIPaddress();
    /** Gets the ip interface protocol */
    public abstract String findProtocol();
    
}
