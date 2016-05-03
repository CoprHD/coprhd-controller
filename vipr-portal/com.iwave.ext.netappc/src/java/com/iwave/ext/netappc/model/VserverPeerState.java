/*
 * Copyright (c) 2012-2016 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc.model;

/**
 * 
 * State of the Vserver peer relationship
 *
 */
public enum VserverPeerState {

    peered("peered"),
    pending("pending"),
    initializing("initializing"),
    initiated("initiated"),
    rejected("rejected"),
    suspended("suspended"),
    deleted("deleted");

    private String label;

    VserverPeerState(String label) {
        this.label = label;
    }

    public static VserverPeerState valueOfLabel(String label) {
        for (VserverPeerState t : values()) {
            if (label.equals(t.label))
                return t;
        }
        throw new IllegalArgumentException(label + " is not a valid label for Snapmirror Job State");
    }

    @Override
    public String toString() {
        return label;
    }

}
