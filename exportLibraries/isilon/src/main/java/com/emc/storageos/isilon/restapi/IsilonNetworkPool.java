/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;
/*
 * Class representing the isilon network pool object
 * member names should match the key names in json object
 * 
 */
public class IsilonNetworkPool {
    
    String access_zone;
    String groupnet;
    String sc_dns_zone;
    String subnet;
    String alloc_method;
    String addr_family;
    
    /*access zone name*/
    public String getAccess_zone() {
        return access_zone;
    }
    public void setAccess_zone(String access_zone) {
        this.access_zone = access_zone;
    }
    /*group net name*/
    public String getGroupnet() {
        return groupnet;
    }
    public void setGroupnet(String groupnet) {
        this.groupnet = groupnet;
    }
    /*smart connect zone name*/
    public String getSc_dns_zone() {
        return sc_dns_zone;
    }
    public void setSc_dns_zone(String sc_dns_zone) {
        this.sc_dns_zone = sc_dns_zone;
    }
    
    public String getSubnet() {
        return subnet;
    }
    public void setSubnet(String subnet) {
        this.subnet = subnet;
    }
    public String getAlloc_method() {
        return alloc_method;
    }
    public void setAlloc_method(String alloc_method) {
        this.alloc_method = alloc_method;
    }
    public String getAddr_family() {
        return addr_family;
    }
    public void setAddr_family(String addr_family) {
        this.addr_family = addr_family;
    }
    
    @Override
    public String toString() {
        return "IsilonNetworkPool [access_zone=" + access_zone + 
                ", groupnet=" + groupnet + ", sc_dns_zone=" + sc_dns_zone + ", subnet="
                + subnet + ", alloc_method=" + alloc_method + ", addr_family=" + addr_family + "]";
    }
}
