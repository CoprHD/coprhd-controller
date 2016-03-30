package com.iwave.ext.netapp.model;

public enum SnapMirrorTransferStatus {

    idle("Idle"),
    transferring("Transferring"),
    pending("Pending"),
    aborting("Aborting"),
    migrating("Migrating"),
    quiescing("Quiescing"),
    resyncing("Resyncing"),
    syncing("Syncing"),
    insync("In-sync"),
    paused("Paused");

    private String label;

    SnapMirrorTransferStatus(String label) {
        this.label = label;
    }

    public static SnapMirrorTransferStatus valueOfLabel(String label) {
        for (SnapMirrorTransferStatus t : values()) {
            if (label.equals(t.label))
                return t;
        }
        throw new IllegalArgumentException(label + " is not a valid label for SnapMirror Transfer Status");
    }

    @Override
    public String toString() {
        return label;
    }
}