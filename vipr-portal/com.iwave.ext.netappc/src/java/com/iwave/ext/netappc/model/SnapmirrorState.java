/*
 * Copyright (c) 2012-2016 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc.model;

/*
 * Specifies the mirror state of the SnapMirror relationship
 */
public enum SnapmirrorState {
    READY("uninitialized"),
    SYNCRONIZED("snapmirrored"),
    FAILOVER("broken-off"),
    PAUSED("quiesced"),
    UNKNOWN("unknown"),
    SOURCE("source");

    private String label;

    SnapmirrorState(String label) {
        this.label = label;
    }

    public static SnapmirrorState valueOfLabel(String label) {
        for (SnapmirrorState t : values()) {
            if (label.equals(t.label))
                return t;
        }
        throw new IllegalArgumentException(label + " is not a valid label for Snapmirror State");
    }

    @Override
    public String toString() {
        return label;
    }

}
