package com.iwave.ext.netappc.model;

public class SnapMirrorVolumeStatus {

    private boolean isDestination;

    private boolean isSource;

    private boolean isTransferBroken;

    private boolean isTransferInProgress;

    public boolean isDestination() {
        return isDestination;
    }

    public void setDestination(boolean isDestination) {
        this.isDestination = isDestination;
    }

    public boolean isSource() {
        return isSource;
    }

    public void setSource(boolean isSource) {
        this.isSource = isSource;
    }

    public boolean isTransferBroken() {
        return isTransferBroken;
    }

    public void setTransferBroken(boolean isTransferBroken) {
        this.isTransferBroken = isTransferBroken;
    }

    public boolean isTransferInProgress() {
        return isTransferInProgress;
    }

    public void setTransferInProgress(boolean isTransferInProgress) {
        this.isTransferInProgress = isTransferInProgress;
    }

    public SnapMirrorVolumeStatus() {
        // TODO Auto-generated constructor stub
    }

}
