/*
 * Copyright (c) 2012-2016 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc.model;

/**
 * information about the Vserver peer relationship
 */
public class VserverPeerInfo {

    public VserverPeerState getVserverPeerState() {
        return vserverPeerState;
    }

    public void setVserverPeerState(VserverPeerState vserverPeerState) {
        this.vserverPeerState = vserverPeerState;
    }

    public String getPeerCluster() {
        return peerCluster;
    }

    public void setPeerCluster(String peerCluster) {
        this.peerCluster = peerCluster;
    }

    public String getPeerVserver() {
        return peerVserver;
    }

    public void setPeerVserver(String peerVserver) {
        this.peerVserver = peerVserver;
    }

    public String getVserver() {
        return vserver;
    }

    public void setVserver(String vserver) {
        this.vserver = vserver;
    }

    public VserverPeerInfo() {
        // TODO Auto-generated constructor stub
    }

    // State of the Vserver peer relationship
    private VserverPeerState vserverPeerState;
    // name of the peer Cluster
    private String peerCluster;
    // name of the peer Vserver in the relationship
    private String peerVserver;
    // name of the local Vserver in the relationship
    private String vserver;

}
