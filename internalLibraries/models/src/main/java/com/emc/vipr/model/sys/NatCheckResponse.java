/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys;

import javax.xml.bind.annotation.XmlElement;

public class NatCheckResponse {
    private boolean behindNAT;
    private String seenIp;

    @XmlElement(name = "isNodesReachable")
    public boolean isBehindNAT() {
        return this.behindNAT;
    }

    public void setBehindNAT(boolean behindNAT) {
        this.behindNAT = behindNAT;
    }

    @XmlElement(name = "short_id")
    public String getSeenIp() {
        return this.seenIp;
    }

    public void setSeenIp(String ip) {
        this.seenIp = ip;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("NatCheckResponse [behindNAT=");
        builder.append(behindNAT);
        builder.append(", seenIp=");
        builder.append(seenIp);
        builder.append("]");
        return builder.toString();
    }
}
