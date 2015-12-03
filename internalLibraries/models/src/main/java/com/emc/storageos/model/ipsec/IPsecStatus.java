/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.ipsec;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement(name = "ipsec_status")
public class IPsecStatus {

    private boolean isGood;
    private String[] disconnectedNodes;

    @XmlElement(name = "version")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    private String version;

    @XmlElement(name = "is_good")
    public boolean getIsGood() {
        return isGood;
    }

    public void setIsGood(boolean isGood) {
        this.isGood = isGood;
    }

    @XmlElement(name = "disconnected_nodes")
    public String[] getDisconnectedNodes() {
        return disconnectedNodes;
    }

    public void setDisconnectedNodes(String[] nodes) {
        this.disconnectedNodes = nodes;
    }
}
