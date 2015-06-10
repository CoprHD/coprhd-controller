/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc;

public class SVMNetInfo {

    private String netInterface;
    private String ipAddress;
    private String netMask;
    private String role;
    
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
    
    public String getRole() {
    	return role;
    }
    
    public void setRole(String role) {
    	this.role = role;
    }
}
