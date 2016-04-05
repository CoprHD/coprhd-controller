package com.iwave.ext.netapp.model;

public enum SnapMirrorCurrentTransferType {

    // initialize, store, schedule, retry, retrieve, resync, and migrate

    initialize("initialize"),
    retry("retry"),
    store("store"),
    schedule("schedule"),
    retrieve("retrieve"),
    resync("resync"),
    migrate("migrate"),
    scheduled("scheduled"),
    none("-");

    private String label;

    SnapMirrorCurrentTransferType(String label) {
        this.label = label;
    }

    public static SnapMirrorCurrentTransferType valueOfLabel(String label) {
        for (SnapMirrorCurrentTransferType t : values()) {
            if (label.equals(t.label))
                return t;
        }
        throw new IllegalArgumentException(label + " is not a valid label for SnapMirror Current Transfer State");
    }

    @Override
    public String toString() {
        return label;
    }
}