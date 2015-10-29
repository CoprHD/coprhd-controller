/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Info for a VPlex logging volume
 */
public class VPlexSystemVolumeInfo extends VPlexResourceInfo {

    // Enumerates the system volume attributes we are interested in and
    // parse from the VPlex system volume response. There must be a setter
    // method for each attribute specified. The format of the setter
    // method must be as specified by the base class method
    // getAttributeSetterMethodName.
    public static enum VolumeAttribute {
        TYPE("volume-type"),
        CAPACITY("capacity");

        // The VPlex name for the attribute.
        private String _name;

        /**
         * Constructor.
         * 
         * @param name The VPlex attribute name.
         */
        VolumeAttribute(String name) {
            _name = name;
        }

        /**
         * Getter for the VPlex name for the attribute.
         * 
         * @return The VPlex name for the attribute.
         */
        public String getAttributeName() {
            return _name;
        }

        /**
         * Returns the enum whose name matches the passed name, else null when
         * not found.
         * 
         * @param name The name to match.
         * 
         * @return The enum whose name matches the passed name, else null when
         *         not found.
         */
        public static VolumeAttribute valueOfAttribute(String name) {
            VolumeAttribute[] volumeAtts = values();
            for (int i = 0; i < volumeAtts.length; i++) {
                if (volumeAtts[i].getAttributeName().equals(name)) {
                    return volumeAtts[i];
                }
            }
            return null;
        }
    };

    // The type of the system volume.
    private String volumeType;

    // The capacity
    private String capacity;

    /**
     * Getter for the volume type.
     * 
     * @return The volume type.
     */
    public String getVolumeType() {
        return volumeType;
    }

    /**
     * Setter for the volume type.
     * 
     * @param strVal The volume type.
     */
    public void setVolumeType(String strVal) {
        volumeType = strVal;
    }

    /**
     * Getter for the volume capacity.
     * 
     * @return The volume capacity.
     */
    public String getCapacity() {
        return capacity;
    }

    /**
     * Setter for the volume capacity.
     * 
     * @param strVal The volume capacity.
     */
    public void setCapacity(String strVal) {
        capacity = strVal;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAttributeFilters() {
        List<String> attFilters = new ArrayList<String>();
        for (VolumeAttribute att : VolumeAttribute.values()) {
            attFilters.add(att.getAttributeName());
        }
        return attFilters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("SystemVolumeInfo ( ");
        str.append(super.toString());
        str.append(", volumeType: ").append(volumeType);
        str.append(", capacity: ").append(capacity);
        str.append(" )");
        return str.toString();
    }
}
