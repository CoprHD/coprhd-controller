/*
 * Copyright (c) 2012-2016 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc.model;

/**
 * Specifies the status of the SnapMirror relationship
 *
 * */
public enum SnapmirrorRelationshipStatus {
    idle("idle"),
    transferring("transferring"),
    checking("checking"),
    quiescing("quiescing"),
    quiesced("quiesced"),
    queued("queued"),
    preparing("preparing"),
    finalizing("finalizing"),
    aborting("aborting");

    private String label;

    SnapmirrorRelationshipStatus(String label) {
        this.label = label;
    }

    public static SnapmirrorRelationshipStatus valueOfLabel(String label) {
        for (SnapmirrorRelationshipStatus t : values()) {
            if (label.equals(t.label))
                return t;
        }
        throw new IllegalArgumentException(label + " is not a valid label for Snapmirror Relationship Status");
    }

    @Override
    public String toString() {
        return label;
    }

}
