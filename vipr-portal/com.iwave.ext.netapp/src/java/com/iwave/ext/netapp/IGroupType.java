/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.netapp;

/**
 * @author sdorcas
 * Enumeration of possible Initiator Group types
 */
public enum IGroupType {

	iscsi("iSCSI"), fcp("FCP");
    
    private String label;

    IGroupType(String label) {
        this.label = label;
    }

    public static IGroupType valueOfLabel(String label) {
        for (IGroupType t : values()) {
            if (label.equals(t.label))
                return t;
        }
        throw new IllegalArgumentException(label + " is not a valid label for IGroupType");
    }
    
    @Override
    public String toString() {
        return label;
    }
	
    /*
	static public String[] listAllTypes()
	{
		String[] types = new String[] {iscsi.toString(), fcp.toString()};
		return types;
	}
    */
}
