/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
