/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import com.emc.storageos.model.valid.Endpoint;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Request POST parameter for host IP interface creation.
 */
@XmlRootElement(name = "ip_interface_update")
public class IpInterfaceUpdateParam extends IpInterfaceParam {

    private String protocol;
    private String ipAddress;

    public IpInterfaceUpdateParam() {
    }

    public IpInterfaceUpdateParam(String protocol, String ipAddress) {
        super();
        this.protocol = protocol;
        this.ipAddress = ipAddress;
    }

    public IpInterfaceUpdateParam(Integer netmask, Integer prefixLength,
            String scopeId, String protocol, String ipAddress, String name) {
        super(netmask, prefixLength, scopeId, name);
        this.protocol = protocol;
        this.ipAddress = ipAddress;
    }

    @Override
    public String findIPaddress() {
        return ipAddress;
    }

    @Override
    public String findProtocol() {
        return protocol;
    }

    /**
     * The protocol supported by the interface which should be IPv4 or IPv6.
     * 
     */
    // @EnumType(HostInterface.Protocol.class)
    @XmlElement()
    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * The IPv4 or IPv6 address of this interface.
     * 
     */
    @XmlElement(name = "ip_address")
    @Endpoint(type = Endpoint.EndpointType.IP)
    @JsonProperty("ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

}
