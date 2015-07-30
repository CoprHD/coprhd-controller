/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import com.emc.storageos.model.valid.Endpoint;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Request POST parameter for host IP interface creation.
 */
@XmlRootElement(name = "ip_interface_create")
public class IpInterfaceCreateParam extends IpInterfaceParam {

    private String protocol;
    private String ipAddress;

    public IpInterfaceCreateParam() {
    }

    public IpInterfaceCreateParam(String protocol, String ipAddress) {
        this.protocol = protocol;
        this.ipAddress = ipAddress;
    }

    /**
     * The protocol supported by the interface which should be IPv4 or IPv6.
     * 
     * @valid example IPv4
     * @valid example IPv6
     */
    // @EnumType(HostInterface.Protocol.class)
    @XmlElement(required = true)
    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * The IPv4 or IPv6 address of this interface.
     * 
     * @valid example: 10.247.12.99
     */
    @XmlElement(name = "ip_address", required = true)
    @Endpoint(type = Endpoint.EndpointType.IP)
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
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
}
