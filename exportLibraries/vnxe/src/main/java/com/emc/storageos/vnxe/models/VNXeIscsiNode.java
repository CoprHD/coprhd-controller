/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeIscsiNode extends VNXeBase {
    private String name;
    private String alias;
    private VNXeEthernetPort ethernetPort;
    private VNXeIscsiPortal iscsiPortal;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public VNXeEthernetPort getEthernetPort() {
        return ethernetPort;
    }

    public void setEthernetPort(VNXeEthernetPort ethernetPort) {
        this.ethernetPort = ethernetPort;
    }

    public VNXeIscsiPortal getIscsiPortal() {
        return iscsiPortal;
    }

    public void setIscsiPortal(VNXeIscsiPortal iscsiPortal) {
        this.iscsiPortal = iscsiPortal;
    }

}
