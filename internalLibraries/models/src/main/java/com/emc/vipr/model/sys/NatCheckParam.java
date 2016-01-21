/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys;

import javax.xml.bind.annotation.XmlElement;

public class NatCheckParam {
    private String ipv4Address;
    private String ipv6Address;

    @XmlElement(name = "ipv4")
    public String getIPv4Address() {
        return this.ipv4Address;
    }

    public void setIPv4Address(String ipv4Address) {
        this.ipv4Address = ipv4Address;
    }

    @XmlElement(name = "ipv6")
    public String getIPv6Address() {
        return this.ipv6Address;
    }

    public void setIPv6Address(String ipv6Address) {
        this.ipv6Address = ipv6Address;
    }
}
