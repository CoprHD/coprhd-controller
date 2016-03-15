/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */

package com.iwave.ext.netapp;

import java.util.List;

public class VFilerInfo {

    private String name;         // Name of the vFiler.
    private String uuid;         // UUID of the vFiler.
    private String ipspace;      // Name of the vFiler's ipspace.
    private List<VFNetInfo> interfaces;   // List of all network interfaces associated with this vFiler.
    private List<NameServerInfo> dnsInfo; // List Name server info
    private List<NameServerInfo> nisInfo;

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

    public List<NameServerInfo> getDnsServers() {
        return dnsInfo;
    }

    public void setDnsServers(List<NameServerInfo> dnsInfo) {
        this.dnsInfo = dnsInfo;
    }

    public List<NameServerInfo> getNisServers() {
        return nisInfo;
    }

    public void setNisServers(List<NameServerInfo> nisInfo) {
        this.nisInfo = nisInfo;
    }
}
