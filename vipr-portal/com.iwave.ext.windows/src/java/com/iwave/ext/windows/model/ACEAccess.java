/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.model;

import java.io.Serializable;

/**
 * Enumeration of CIFS access. This matches the values available in windows
 * share permissions dialog.
 * 
 * @author Chris Dail
 */
public enum ACEAccess implements Serializable {
    full("Full Control"),
    change("Change"),
    read("Read");

    private String label;

    ACEAccess(String label) {
        this.label = label;
    }

    public static ACEAccess valueOfLabel(String label) {
        for (ACEAccess t : values()) {
            if (label.equals(t.label)) {
                return t;
            }
        }
        throw new IllegalArgumentException(label + " is not a valid label for CifsAccess");
    }

    public void setLabel(String label) {
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
