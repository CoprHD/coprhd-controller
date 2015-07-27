/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
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
