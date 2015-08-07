/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp.model;

public enum SpaceGuarantee {
    none("None"),
    file("File"),
    volume("Volume");

    private String label;

    SpaceGuarantee(String label) {
        this.label = label;
    }

    public static SpaceGuarantee valueOfLabel(String label) {
        for (SpaceGuarantee t : values()) {
            if (label.equals(t.label)) {
                return t;
            }
        }
        throw new IllegalArgumentException(label + " is not a valid label for SpaceGuarantee");
    }

    @Override
    public String toString() {
        return label;
    }
}
