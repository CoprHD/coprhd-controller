/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp.model;

public enum VolumeType {

    flexible("Flexible"),
    traditional("Traditional"),
    cache("Cache");

    private String label;

    VolumeType(String label) {
        this.label = label;
    }

    public static VolumeType valueOfLabel(String label) {
        for (VolumeType t : values()) {
            if (label.equals(t.label))
                return t;
        }
        throw new IllegalArgumentException(label + " is not a valid label for VolumeType");
    }

    @Override
    public String toString() {
        return label;
    }
}
