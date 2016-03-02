/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.vplex.api.VPlexPortInfo.PortRole;

/**
 * Info for a VPlex director.
 */
public class VPlexDirectorInfo extends VPlexResourceInfo {

    // Used to assign a unique slot number for each director.
    // Accounts for the maximum number of directors in a
    // VPlex Metro/Geo configuration, which is two clusters
    // with 4 engines containing 2 directors each. Directors
    // are named like #-#-A/B (e.g., 1-1-A), where the first
    // number is the cluster and the second is the engine in
    // that cluster. This name is turned into indices to
    // access the map such that 1-1-A, would be [0][0][0] or
    // slot 0 and 1-1-B would be [0][0][1] or slot 1.
    private static final int[][][] DIRECTOR_SLOT_MAP = {
            { { 0, 1 }, { 2, 3 }, { 4, 5 }, { 6, 7 } },
            { { 8, 9 }, { 10, 11 }, { 12, 13 }, { 14, 15 } }
    };

    // Enumerates the director attributes we are interested in and
    // parse from the VPlex director response. There must be a setter
    // method for each attribute specified. The format of the setter
    // method must be as specified by the base class method
    // getAttributeSetterMethodName.
    public static enum DirectorAttribute {
        ID("director-id"),
        ENGINE_ID("engine-id"),
        SERIAL_NUMBER("serial-number"),
        SP_ID("sp-id"),
        HOST_NAME("hostname");

        // The VPlex name for the attribute.
        private String _name;

        /**
         * Constructor.
         * 
         * @param name The VPlex attribute name.
         */
        DirectorAttribute(String name) {
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
        public static DirectorAttribute valueOfAttribute(String name) {
            DirectorAttribute[] directorAtts = values();
            for (int i = 0; i < directorAtts.length; i++) {
                if (directorAtts[i].getAttributeName().equals(name)) {
                    return directorAtts[i];
                }
            }
            return null;
        }
    };

    // The director id.
    private String directorId;

    // The engine id.
    private String engineId;

    // The serial number.
    private String serialNumber;

    // The SP id.
    private String spId;

    // The hostname.
    private String hostName;

    // The port information for the director
    private List<VPlexPortInfo> portInfoList = new ArrayList<VPlexPortInfo>();

    /**
     * Getter for the director id.
     * 
     * @return The director id.
     */
    public String getDirectorId() {
        return directorId;
    }

    /**
     * Setter for the director id.
     * 
     * @param strVal The director id.
     */
    public void setDirectorId(String strVal) {
        directorId = strVal;
    }

    /**
     * Getter for the engine id.
     * 
     * @return The engine id.
     */
    public String getEngineId() {
        return engineId;
    }

    /**
     * Setter for the engine id.
     * 
     * @param strVal The engine id.
     */
    public void setEngineId(String strVal) {
        engineId = strVal;
    }

    /**
     * Getter for the serial number.
     * 
     * @return The serial number.
     */
    public String getSerialNumber() {
        return serialNumber;
    }

    /**
     * Setter for the serial number.
     * 
     * @param strVal The serial number.
     */
    public void setSerialNumber(String strVal) {
        serialNumber = strVal;
    }

    /**
     * Getter for the SP id.
     * 
     * @return The SP id.
     */
    public String getSpId() {
        return spId;
    }

    /**
     * Setter for the SP id.
     * 
     * @param strVal The SP id.
     */
    public void setSpId(String strVal) {
        spId = strVal;
    }

    /**
     * Getter for the hostname.
     * 
     * @return The hostname.
     */
    public String getHostname() {
        return hostName;
    }

    /**
     * Setter for the hostname.
     * 
     * @param strVal The hostname.
     */
    public void setHostname(String strVal) {
        hostName = strVal;
    }

    /**
     * Getter for the port info for the director.
     * 
     * @return The port info for the director.
     */
    public List<VPlexPortInfo> getPortInfo() {
        return portInfoList;
    }

    /**
     * Setter for the port info for the director.
     * 
     * @param infoList The port info for the director.
     */
    public void setPortInfo(List<VPlexPortInfo> infoList) {
        portInfoList = infoList;
    }

    /**
     * Return the number of director ports whose role is one of the passed
     * roles.
     * 
     * @param portRoles The roles to compare.
     * 
     * @return A count of the number of director ports whose role is one of the
     *         passed roles.
     */
    public int getNumberOfPortsOfType(List<PortRole> portRoles) {
        int count = 0;
        for (VPlexPortInfo portInfo : portInfoList) {
            PortRole portRole = PortRole.valueOfRole(portInfo.getRole());
            if ((portRole != null) && (portRoles.contains(portRole))) {
                count++;
            }
        }

        return count;
    }

    /**
     * Returns a unique slot number for each director in the VPlex.
     * 
     * @return A unique slot number for each director in the VPlex.
     */
    public int getSlotNumber() {
        String[] engineIdComp = engineId.split("-");
        int x = Integer.parseInt(engineIdComp[0]) - 1;
        int y = Integer.parseInt(engineIdComp[1]) - 1;
        int z = (spId.equals("A") ? 0 : 1);
        return DIRECTOR_SLOT_MAP[x][y][z];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAttributeFilters() {
        List<String> attFilters = new ArrayList<String>();
        for (DirectorAttribute att : DirectorAttribute.values()) {
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
        str.append("DirectorInfo ( ");
        str.append(super.toString());
        str.append(", directorId: ").append(directorId);
        str.append(", engineId: ").append(engineId);
        str.append(", serialNumber: ").append(serialNumber);
        str.append(", spId: ").append(spId);
        str.append(", hostName: ").append(hostName);
        for (VPlexPortInfo portInfo : portInfoList) {
            str.append(", ");
            str.append(portInfo.toString());
        }
        str.append(" )");
        return str.toString();
    }
}
