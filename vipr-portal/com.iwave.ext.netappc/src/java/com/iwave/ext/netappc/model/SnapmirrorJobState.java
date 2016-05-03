/*
 * Copyright (c) 2012-2016 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc.model;

public enum SnapmirrorJobState {

    initial("initial"),
    queued("queued"),
    running("running"),
    waiting("waiting"),
    pausing("pausing"),
    paused("paused"),
    quitting("quitting"),
    success("success"),
    failure("failure"),
    reschedule("reschedule"),
    error("error"),
    quit("quit"),
    dead("dead"),
    unknown("unknown"),
    restart("restart"),
    dormant("dormant");

    private String label;

    SnapmirrorJobState(String label) {
        this.label = label;
    }

    public static SnapmirrorJobState valueOfLabel(String label) {
        for (SnapmirrorJobState t : values()) {
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
