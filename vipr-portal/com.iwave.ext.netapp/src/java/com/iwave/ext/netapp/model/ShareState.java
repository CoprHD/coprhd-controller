/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp.model;

public enum ShareState {

    none("None"),
    read("Read"),
    write("Write"),
    all("All");

    private String label;

    ShareState(String label) {
        this.label = label;
    }

    public static ShareState valueOfLabel(String label) {
        for (ShareState t : values()) {
            if (label.equals(t.label))
                return t;
        }
        throw new IllegalArgumentException(label + " is not a valid label for ShareState");
    }

    @Override
    public String toString() {
        return label;
    }
}
