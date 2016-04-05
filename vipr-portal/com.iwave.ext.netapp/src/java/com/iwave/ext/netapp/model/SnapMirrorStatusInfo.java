package com.iwave.ext.netapp.model;

import java.io.Serializable;

/**
 * The SnapMirror pair status.
 */
public class SnapMirrorStatusInfo implements Serializable {

    // uninitialized", "snapmirrored", "broken-off", "quiesced", "source", and "unknown".
    // SnapMirror pair state
    private SnapMirrorState mirrorState;

    // SnapMirror pair transfer status
    // "Idle, "Transferring", "Pending", "Aborting", "Migrating", "Quiescing", " +
    // ""Resyncing", "Waiting", "Syncing", "In-sync" and "Paused".
    private SnapMirrorTransferStatus transferType;

    // current-transfer-type
    // store, schedule, retry, retrieve, resync, and migrate
    private SnapMirrorCurrentTransferType currentTransferType;

    // current-transfer-error
    private String currentTransferError;

    private String destinationLocation;

    private String sourceLocation;

    public SnapMirrorStatusInfo() {

    }

    // getter and setter methods
    public SnapMirrorState getMirrorState() {
        return mirrorState;
    }

    public void setMirrorState(SnapMirrorState mirrorState) {
        this.mirrorState = mirrorState;
    }

    public SnapMirrorTransferStatus getTransferType() {
        return transferType;
    }

    public void setTransferType(SnapMirrorTransferStatus transferType) {
        this.transferType = transferType;
    }

    public void setCurrentTransferType(SnapMirrorCurrentTransferType currentTransferType) {
        this.currentTransferType = currentTransferType;
    }

    public SnapMirrorCurrentTransferType getCurrentTransferType() {
        return currentTransferType;
    }

    public String getCurrentTransferError() {
        return currentTransferError;
    }

    public void setCurrentTransferError(String currentTransferError) {
        this.currentTransferError = currentTransferError;
    }

    public String getDestinationLocation() {
        return destinationLocation;
    }

    public void setDestinationLocation(String destinationLocation) {
        this.destinationLocation = destinationLocation;
    }

    public String getSourceLocation() {
        return sourceLocation;
    }

    public void setSourceLocation(String sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    @Override
    public String toString() {
        return "SnapMirrorStatusInfo [mirrorState=" + mirrorState + ", transferType=" + transferType + ", currentTransferType="
                + currentTransferType + ", currentTransferError=" + currentTransferError + ", destinationLocation=" + destinationLocation
                + ", sourceLocation=" + sourceLocation + "]";
    }

}