/*
 * Copyright (c) 2012-2016 iWave Software LLC
 * All Rights Reserved
 */

package com.iwave.ext.netappc.model;

/*
 * The type of the transfer for the relationship
 */
public enum SnapmirrorTransferType {

    initialize("initialize"),
    update("update"),
    resync("resync"),
    restore("restore"),
    check("check");

    private String label;

    SnapmirrorTransferType(String label) {
        this.label = label;
    }

    public static SnapmirrorTransferType valueOfLabel(String label) {
        for (SnapmirrorTransferType t : values()) {
            if (label.equals(t.label))
                return t;
        }
        throw new IllegalArgumentException(label + " is not a valid label for Snapmirror Transfer Type");
    }

    @Override
    public String toString() {
        return label;
    }

}
