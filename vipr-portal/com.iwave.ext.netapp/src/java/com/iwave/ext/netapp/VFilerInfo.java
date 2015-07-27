/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.iwave.ext.netapp;

import java.util.List;

public class VFilerInfo {

    private String           name;         // Name of the vFiler.
    private String           uuid;         // UUID of the vFiler.
    private String           ipspace;      // Name of the vFiler's ipspace.
    private List<VFNetInfo>  interfaces;   // List of all network interfaces associated with this vFiler.
    
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    
    public String getUuid() {
        return uuid;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public String getIpspace() {
        return ipspace;
    }
    public void setIpspace(String ipspace) {
        this.ipspace = ipspace;
    }
    public List<VFNetInfo> getInterfaces() {
        return interfaces;
    }
    public void setInterfaces(List<VFNetInfo> interfaces) {
        this.interfaces = interfaces;
    }
}
