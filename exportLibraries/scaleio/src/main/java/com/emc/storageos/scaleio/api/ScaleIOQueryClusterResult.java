/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api;

public class ScaleIOQueryClusterResult {
    private String clusterMode;
    private String clusterState;
    private String tieBreakerState;
    private String primaryIP;
    private String secondaryIP;
    private String tieBreakerIP;

    public void setIPs(String primaryIP, String secondaryIP, String tieBreakerIP) {
        this.primaryIP = primaryIP;
        this.secondaryIP = secondaryIP;
        this.tieBreakerIP = tieBreakerIP;
    }

    public String getPrimaryIP() {
        return primaryIP;
    }

    public void setPrimaryIP(String primaryIP) {
        this.primaryIP = primaryIP;
    }

    public String getSecondaryIP() {
        return secondaryIP;
    }

    public void setSecondaryIP(String secondaryIP) {
        this.secondaryIP = secondaryIP;
    }

    public String getTieBreakerIP() {
        return tieBreakerIP;
    }

    public void setTieBreakerIP(String tieBreakerIP) {
        this.tieBreakerIP = tieBreakerIP;
    }

    public void setClusterMode(String clusterMode) {
        this.clusterMode = clusterMode;
    }

    public String getClusterMode() {
        return clusterMode;
    }

    public void setClusterState(String clusterState) {
        this.clusterState = clusterState;
    }

    public String getClusterState() {
        return clusterState;
    }

    public void setTieBreakerState(String tieBreakerState) {
        this.tieBreakerState = tieBreakerState;
    }

    public String getTieBreakerState() {
        return tieBreakerState;
    }
}
