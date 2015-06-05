/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.windows.model.wmi;

public class MSClusterNetworkInterface {
    private String node;
    private String ipaddress;
    private String network;
    private String name;

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getIpaddress() {
        return ipaddress;
    }

    public void setIpaddress(String ipaddress) {
        this.ipaddress = ipaddress;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public String toString() {
        return String.format("MSClusterNetworkInterface node: %s, ipAddress: %s, network: %s, name: %s", node,
                ipaddress, network, name);
    }
}
