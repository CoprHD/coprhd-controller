/*
 * Copyright (c) 2012-2016 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc.model;

/* 
 * Information about the SnapMirror Relationship
 */
public class SnapmirrorInfo {

    // source info
    private String sourceLocation;
    private String sourceVolume;
    private String sourceVserver;
    private String sourceCluster;

    // destination info
    private String destinationLocation;
    private String destinationVolume;
    private String destinationVserver;
    private String destinationCluster;

    public SnapmirrorInfo(SnapmirrorInfo snapmirrorInfo) {
        // source
        this.sourceLocation = snapmirrorInfo.sourceLocation;
        this.sourceCluster = snapmirrorInfo.sourceCluster;
        this.sourceVserver = snapmirrorInfo.sourceVserver;
        this.sourceVolume = snapmirrorInfo.sourceCluster;

        // destination
        this.destinationLocation = snapmirrorInfo.destinationLocation;
        this.destinationCluster = snapmirrorInfo.destinationCluster;
        this.destinationVserver = snapmirrorInfo.destinationVserver;
        this.destinationVolume = snapmirrorInfo.destinationVolume;
    }

    // default constructor
    public SnapmirrorInfo() {

    }

    // getter and setter methods for Source Cluster

    public String getSourceLocation() {
        return sourceLocation;
    }

    public void setSourceLocation(String sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    public String getSourceVolume() {
        return sourceVolume;
    }

    public void setSourceVolume(String sourceVolume) {
        this.sourceVolume = sourceVolume;
    }

    public String getSourceVserver() {
        return sourceVserver;
    }

    public void setSourceVserver(String sourceVserver) {
        this.sourceVserver = sourceVserver;
    }

    public String getSourceCluster() {
        return sourceCluster;
    }

    public void setSourceCluster(String sourceCluster) {
        this.sourceCluster = sourceCluster;
    }

    // getter and setter methods for Destination Cluster

    public String getDestinationLocation() {
        return destinationLocation;
    }

    public void setDestinationLocation(String destinationLocation) {
        this.destinationLocation = destinationLocation;
    }

    public String getDestinationVolume() {
        return destinationVolume;
    }

    public void setDestinationVolume(String destinationVolume) {
        this.destinationVolume = destinationVolume;
    }

    public String getDestinationVserver() {
        return destinationVserver;
    }

    public void setDestinationVserver(String destinationVserver) {
        this.destinationVserver = destinationVserver;
    }

    public String getDestinationCluster() {
        return destinationCluster;
    }

    public void setDestinationCluster(String destinationCluster) {
        this.destinationCluster = destinationCluster;
    }

    @Override
    public String toString() {
        return "SnapmirrorInfo [sourceLocation=" + sourceLocation + ", sourceVolume=" + sourceVolume + ", sourceVserver=" + sourceVserver
                + ", sourceCluster=" + sourceCluster + ", destinationLocation=" + destinationLocation + ", destinationVolume="
                + destinationVolume + ", destinationVserver=" + destinationVserver + ", destinationCluster=" + destinationCluster + "]";
    }

}
