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

public class VFNetInfo {

    private String netInterface;
    private String ipAddress;
    private String netMask;
    
    public String getNetInterface() {
        return netInterface;
    }
    
    public void setNetInterface(String netInterface) {
        this.netInterface = netInterface;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public String getNetMask() {
        return netMask;
    }
    
    public void setNetMask(String netMask) {
        this.netMask = netMask;
    }
    
}
