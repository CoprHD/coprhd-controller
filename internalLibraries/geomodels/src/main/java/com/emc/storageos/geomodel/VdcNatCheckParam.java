/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.geomodel;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "vdc-nat-check")
public class VdcNatCheckParam {
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
