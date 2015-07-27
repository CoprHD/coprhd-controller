/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp.model;

public enum CifsAccess {
    full("Full Control", "Full Control (rwx)"),
    change("Change", "Change (rwx)"),
    read("Read", "Read (r-x)"),
    none("No Access", "No Access (---)"),
    r__("r--", "r-- (Unix only)"),
    rw_("rw-", "rw- (Unix only)"),
    _w_("-w-", "-w- (Unix only)"),
    _wx("-wx", "-wx (Unix only)"),
    __x("--x", "--x (Unix only)");

    private String label;
    private String access;

    CifsAccess(String access, String label) {
        this.access = access;
        this.label = label;
    }

    public static CifsAccess valueOfAccess(String access) {
        for (CifsAccess t : values()) {
            if (access.equalsIgnoreCase(t.access))
                return t;
        }
        throw new IllegalArgumentException(access + " is not a valid access for CifsAccess");
    }

    public static CifsAccess valueOfLabel(String label) {
        for (CifsAccess t : values()) {
            if (label.equalsIgnoreCase(t.label))
                return t;
        }
        throw new IllegalArgumentException(label + " is not a valid label for CifsAccess");
    }
    
    public String access() {
        return access;
    }

    @Override
    public String toString() {
        return label;
    }
}
