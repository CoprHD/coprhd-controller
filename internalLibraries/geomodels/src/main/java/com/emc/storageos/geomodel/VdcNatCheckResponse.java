/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geomodel;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;


@XmlRootElement
public class VdcNatCheckResponse {

    private boolean behindNAT;
    private String seenIp;

    @XmlElement(name="isNodesReachable")
    public boolean isBehindNAT() {
        return this.behindNAT;
    }
    public void setBehindNAT(boolean behindNAT) {
        this.behindNAT = behindNAT;
    }

    @XmlElement(name="short_id")
    public String getSeenIp() {
        return this.seenIp;
    }
    public void setSeenIp(String ip) {
        this.seenIp = ip;
    }
}
