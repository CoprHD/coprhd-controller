/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vplex.api;

/**
 * Enumeration of supported backend storage
 * array types that can be connected to the VPLEX.
 */
public enum VPlexApiBackendSystemType {

    HDS("hds"),
    OPENSTACK("openstack"),
    SCALEIO("scaleio"),
    VMAX("vmax"),
    VNXBLOCK("vnxblock"),
    XTREMIO("xtremio"),
	IBMXIV("ibmxiv");

    private String _type;

    /**
     * Constructor.
     * 
     * @param type the backend storage system type
     */
    VPlexApiBackendSystemType(String type) {
        this._type = type;
    }

    /**
     * Getter for the VPLEX backend system type.
     * 
     * @return The VPLEX backend system type.
     */
    public String getType() {
        return _type;
    }

    /**
     * Returns the enum whose type matches the passed type,
     * else null when not found.
     * 
     * @param type The system type to match.
     * 
     * @return The enum whose type matches the passed type,
     *         else null when not found.
     */
    public static VPlexApiBackendSystemType valueOfType(String type) {
        VPlexApiBackendSystemType[] systemTypes = values();
        for (int i = 0; i < systemTypes.length; i++) {
            if (systemTypes[i].getType().equals(type)) {
                return systemTypes[i];
            }
        }
        return null;
    }

}
