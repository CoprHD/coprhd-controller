/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.ipsec;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "node_state")
public class IPsecNodeState {

    private String ip;
    private String version;

    @XmlElement(name = "node_version")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    @Override
    public String toString() {
        return "IPsecNodeState{" +
                "ip='" + ip + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}
