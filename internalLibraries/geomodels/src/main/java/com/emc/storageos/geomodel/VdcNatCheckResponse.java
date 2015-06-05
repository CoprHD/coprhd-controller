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
