/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_network")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteNetwork {

    private int siteBandwidth;
    private int sitePing;

    public SiteNetwork() {
        siteBandwidth = 0;
        sitePing = 0;
    }

    @XmlElement(name = "site_ping")
    public int getSitePing() {
        return sitePing;
    }

    public void setSitePing(int sitePing) {
        this.sitePing = sitePing;
    }

    @XmlElement(name = "site_bandwidth")
    public int getSiteBandwidth() {
        return siteBandwidth;
    }

    public void setSiteBandwidth(int siteBandwidth) {
        this.siteBandwidth = siteBandwidth;
    }
}
