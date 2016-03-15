/*
 *  Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */

package com.iwave.ext.netapp;

import java.util.List;

public class NameServerInfo {

    private String name;         // Name of the name server DNS or NIS.
    private List<VFNetInfo> servers;   // List of server IP addresses.

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<VFNetInfo> getNameServers() {
        return servers;
    }

    public void setNameServers(List<VFNetInfo> servers) {
        this.servers = servers;
    }
}