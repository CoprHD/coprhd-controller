/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeNfsServer extends VNXeBase {
    private VNXeBase nasServer;
    private List<VNXeBase> fileInterfaces;

    public VNXeBase getNasServer() {
        return nasServer;
    }

    public void setNasServer(VNXeBase nasServer) {
        this.nasServer = nasServer;
    }

    public List<VNXeBase> getFileInterfaces() {
        return fileInterfaces;
    }

    public void setFileInterfaces(List<VNXeBase> fileInterfaces) {
        this.fileInterfaces = fileInterfaces;
    }

}
