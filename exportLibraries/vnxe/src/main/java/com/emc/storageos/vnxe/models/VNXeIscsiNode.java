/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class VNXeIscsiNode extends VNXeBase{
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
