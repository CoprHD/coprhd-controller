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
