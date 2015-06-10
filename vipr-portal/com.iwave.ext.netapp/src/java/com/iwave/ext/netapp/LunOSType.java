/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp;

/**
 * @author sdorcas
 */

public enum LunOSType {

    aix("AIX"),
    hpux("HP-UX"),
    hyper_v("Hyper-V"),
    linux("Linux"),
    netware("Netware"),
    openvms("OpenVMS"),
    solaris("Solaris"),
    vmware("VMware"),
    windows("Windows"),
    xen("Xen"),
    
    // the following are valid for IGroups, but not for LUNs
    default_("Unknown"),    // initiators belong to an unknown host type
    
    // the following are valid for LUNs, but not for IGroups
    solaris_efi("Solaris_EFI"),
    windows_gpt("Windows-GPT"),
    windows_2008("Windows-2008"),
    image("Image");    // no assumptions will be made about the data stored in the LUN

    private String label;

    LunOSType(String label) {
        this.label = label;
    }

    public static LunOSType valueOfLabel(String label) {
        for (LunOSType t : values()) {
            if (label.equals(t.label))
                return t;
        }
        throw new IllegalArgumentException(label + " is not a valid label for LunOSType");
    }
    
    @Override
    public String toString() {
        return label;
    }
    
    public static LunOSType apiValueOf(String name) {
        if ("default".equals(name)) {
            return default_;
        }
        return valueOf(name);
    }

    public String apiName() {
        if (this == default_) {
            return "default";
        }
        return name();
    }

    /*
    static public String[] listAllTypes() {
    	String[] types = {solaris.toString(),
    				windows.toString(),
    				hpux.toString(),
    				aix.toString(),
    				linux.toString(),
    				netware.toString(),
    				vmware.toString(),
    				windows_2008.toString(),
    				xen.toString(),
    				hyper_v.toString() };
    	
    	return types;
    }
    */
}
