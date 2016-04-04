package com.iwave.ext.netapp.model;

public enum SnapMirrorState {

    READY("uninitialized"),
    SYNCRONIZED("snapmirrored"),
    FAILOVER("broken-off"),
    PAUSED("quiesced"),
    UNKNOWN("unknown"),
    SOURCE("source");

    private String label;

    SnapMirrorState(String label) {
        this.label = label;
    }

    public static SnapMirrorState valueOfLabel(String label) {
        for (SnapMirrorState t : values()) {
            if (label.equals(t.label))
                return t;
        }
        throw new IllegalArgumentException(label + " is not a valid label for SnapMirrorState");
    }

    @Override
    public String toString() {
        return label;
    }
}
