/*
 * Copyright (c) 2012-2016 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc.model;

/* 
 * Information about the SnapMirror Relationship
 */
public class SnapmirrorInfo {

    // The type of transfer taking place
    private SnapmirrorTransferType currentTransferType;
    // The type of the previous transfer for the relationship
    private SnapmirrorTransferType lastTransferType;

    // Specifies the mirror state of the SnapMirror relationship
    private SnapmirrorState mirrorState;

    // Specifies the status of the SnapMirror relationship
    private SnapmirrorRelationshipStatus relationshipStatus;

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

    // error encountered by the current transfer
    private String currentTransferError;

    // getter and setter methods for Snapmirror
    public SnapmirrorTransferType getCurrentTransferType() {
        return currentTransferType;
    }

    public void setCurrentTransferType(SnapmirrorTransferType currentTransferType) {
        this.currentTransferType = currentTransferType;
    }

    public SnapmirrorTransferType getLastTransferType() {
        return lastTransferType;
    }

    public void setLastTransferType(SnapmirrorTransferType lastTransferType) {
        this.lastTransferType = lastTransferType;
    }

    public SnapmirrorState getMirrorState() {
        return mirrorState;
    }

    public void setMirrorState(SnapmirrorState mirrorState) {
        this.mirrorState = mirrorState;
    }

    public SnapmirrorRelationshipStatus getRelationshipStatus() {
        return relationshipStatus;
    }

    public void setRelationshipStatus(SnapmirrorRelationshipStatus relationshipStatus) {
        this.relationshipStatus = relationshipStatus;
    }

    public String getCurrentTransferError() {
        return currentTransferError;
    }

    public void setCurrentTransferError(String currentTransferError) {
        this.currentTransferError = currentTransferError;
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

}
