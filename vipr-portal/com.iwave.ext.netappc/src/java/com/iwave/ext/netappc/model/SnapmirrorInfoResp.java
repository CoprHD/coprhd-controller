package com.iwave.ext.netappc.model;

public class SnapmirrorInfoResp extends SnapmirrorInfo {

    public SnapmirrorInfoResp() {
        // TODO Auto-generated constructor stub
    }

    // The type of transfer taking place
    private SnapmirrorTransferType currentTransferType;
    // The type of the previous transfer for the relationship
    private SnapmirrorTransferType lastTransferType;

    // Specifies the mirror state of the SnapMirror relationship
    private SnapmirrorState mirrorState;

    // Specifies the status of the SnapMirror relationship
    private SnapmirrorRelationshipStatus relationshipStatus;

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

}
