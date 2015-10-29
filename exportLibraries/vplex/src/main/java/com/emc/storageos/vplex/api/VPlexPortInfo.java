/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Info for a VPlex port.
 * 
 * Currently stored and returned in format received from VPlex.
 * 
 * Sample data from the VPlex:
 * 
 * "attributes": [
 * {
 * "value": "0x50001442607dc400",
 * "name": "address"
 * },
 * {
 * "value": null,
 * "name": "current-speed"
 * },
 * {
 * "value": "true",
 * "name": "enabled"
 * },
 * {
 * "value": "8Gbits/s",
 * "name": "max-speed"
 * },
 * {
 * "value": "A0-FC00",
 * "name": "name"
 * },
 * {
 * "value": "0x5000144046e07dc4",
 * "name": "node-wwn"
 * },
 * {
 * "value": "error",
 * "name": "operational-status"
 * },
 * {
 * "value": "no-link",
 * "name": "port-status"
 * },
 * {
 * "value": "0x50001442607dc400",
 * "name": "port-wwn"
 * },
 * {
 * "value": [
 * "fc"
 * ],
 * "name": "protocols"
 * },
 * {
 * "value": "front-end",
 * "name": "role"
 * },
 * {
 * "value": "P0000000046E07DC4-A0-FC00",
 * "name": "target-port"
 * },
 * {
 * "value": "p2p",
 * "name": "topology"
 * }
 * ],
 */
public class VPlexPortInfo extends VPlexResourceInfo {

    // Defines units in which port speeds can be returned.
    public static enum SpeedUnits {
        BITS_PER_SECOND,
        GBITS_PER_SECOND
    }

    // Defines the valid values for the port operational status.
    public static enum OperationalStatus {
        ok
    }

    // Enumerates the port attributes we are interested in and
    // parse from the VPlex director response. There must be a setter
    // method for each attribute specified. The format of the setter
    // method must be as specified by the base class method
    // getAttributeSetterMethodName.
    public static enum PortAttribute {
        PORT_WWN("port-wwn"),
        NODE_WWN("node-wwn"),
        ROLE("role"),
        PROTOCOLS("protocols"),
        CURRENT_SPEED("current-speed"),
        TARGET_PORT("target-port"),
        OPERATIONAL_STATUS("operational-status");

        // The VPlex name for the attribute.
        private String _name;

        /**
         * Constructor.
         * 
         * @param name The VPlex attribute name.
         */
        PortAttribute(String name) {
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
        public static PortAttribute valueOfAttribute(String name) {
            PortAttribute[] portAtts = values();
            for (int i = 0; i < portAtts.length; i++) {
                if (portAtts[i].getAttributeName().equals(name)) {
                    return portAtts[i];
                }
            }
            return null;
        }
    };

    // Defines the port roles/types.
    public static enum PortRole {
        FRONTEND("front-end"),
        BACKEND("back-end"),
        WAN_COM("wan-com"),
        LOACAL_COM("local-com"),
        MANAGEMENT("management"),
        INTER_DIRECTOR_COM("inter-director-communication");

        // The VPlex name for the role.
        private String _roleName;

        /**
         * Constructor.
         * 
         * @param roleName The VPlex role name.
         */
        PortRole(String roleName) {
            _roleName = roleName;
        }

        /**
         * Getter for the VPlex name for the role.
         * 
         * @return The VPlex name for the role.
         */
        public String getRoleName() {
            return _roleName;
        }

        /**
         * Returns the enum whose role matches the passed role, else null when
         * not found.
         * 
         * @param role The role to match.
         * 
         * @return The enum whose role matches the passed role, else null when
         *         not found.
         */
        public static PortRole valueOfRole(String role) {
            PortRole[] portRoles = values();
            for (int i = 0; i < portRoles.length; i++) {
                if (portRoles[i].getRoleName().equals(role)) {
                    return portRoles[i];
                }
            }
            return null;
        }
    };

    // Converts Gbits/s to Bits/s.
    public static final Long BITS_GBITS_CONVERSION = new Long(1024 * 1024 * 1024);

    // The director for the port
    private VPlexDirectorInfo directorInfo;

    // The port WWN.
    private String portWwn;

    // The node WWN.
    private String nodeWwn;

    // The target port.
    private String targetPort;

    // The role of the port i.e., front-end, back-end, wan-com, etc...
    private String role;

    // List of support protocols.
    private List<String> protocols;

    // The current port speed GB/s
    private String currentSpeed;

    // The operational status
    private String operationalStatus;

    /**
     * Getter for the port director.
     * 
     * @return The port director.
     */
    public VPlexDirectorInfo getDirectorInfo() {
        return directorInfo;
    }

    /**
     * Setter for the port director.
     * 
     * @param info The port director.
     */
    public void setDirectorInfo(VPlexDirectorInfo info) {
        directorInfo = info;
    }

    /**
     * Getter for the port WWN.
     * 
     * @return The port WWN.
     */
    public String getPortWwn() {
        return VPlexApiUtils.formatWWN(portWwn);
    }

    /**
     * Setter for the port WWN.
     * 
     * @param strVal The port WWN.
     */
    public void setPortWwn(String strVal) {
        portWwn = strVal;
    }

    /**
     * Getter for the port node WWN.
     * 
     * @return The port node WWN.
     */
    public String getNodeWwn() {
        return VPlexApiUtils.formatWWN(nodeWwn);
    }

    /**
     * Setter for the port node WWN.
     * 
     * @param strVal The port node WWN.
     */
    public void setNodeWwn(String strVal) {
        nodeWwn = strVal;
    }

    /**
     * Getter for the target-port.
     * 
     * @return The target-port value.
     */
    public String getTargetPort() {
        return targetPort;
    }

    /**
     * Setter for the target-port.
     * 
     * @param strVal The target-port value.
     */
    public void setTargetPort(String strVal) {
        targetPort = strVal;
    }

    /**
     * Getter for the port role.
     * 
     * @return The port role.
     */
    public String getRole() {
        return role;
    }

    /**
     * Setter for the port role.
     * 
     * @param strVal The port role.
     */
    public void setRole(String strVal) {
        role = strVal;
    }

    /**
     * Getter for the support protocols.
     * 
     * @return The supported protocols.
     */
    public List<String> getProtocols() {
        return protocols;
    }

    /**
     * Setter for the support protocols.
     * 
     * @param listVal The supported protocols.
     */
    public void setProtocols(List<String> listVal) {
        protocols = listVal;
    }

    /**
     * Getter for the current port speed.
     * 
     * @return The current port speed.
     */
    public String getCurrentSpeed() {
        return currentSpeed;
    }

    /**
     * Gets the current port speed in the requested units.
     * 
     * @param units The desired units of measure.
     * 
     * @return The current port speed in the requested units, or null if the
     *         value cannot be determined.
     * 
     * @throws VPlexApiException When an error occurs converting the speed.
     */
    public Long getCurrentSpeed(SpeedUnits units) throws VPlexApiException {
        return getSpeed(currentSpeed, units);
    }

    /**
     * Setter for the current port speed.
     * 
     * @param strVal The current port speed.
     */
    public void setCurrentSpeed(String strVal) {
        currentSpeed = strVal;
    }

    /**
     * Return whether or not the port is a backend port.
     * 
     * @return true if the port is a backend port, false otherwise.
     */
    public boolean isBackendPort() {
        return PortRole.BACKEND.getRoleName().equals(role);
    }

    /**
     * Return whether or not the port is a frontend port.
     * 
     * @return true if the port is a frontend port, false otherwise.
     */
    public boolean isFrontendPort() {
        return PortRole.FRONTEND.getRoleName().equals(role);
    }

    /**
     * Converts the passed port speed specified as a String in Gbits/s to
     * a Long value in the requested units.
     * 
     * @param speedStr The port speed in Gbits/s.
     * @param units The desired units.
     * 
     * @return The port speed in the requested units, or null when the
     *         value cannot be determined.
     * 
     * @throws VPlexApiException When invalid formatted speed is passed.
     */
    private Long getSpeed(String speedStr, SpeedUnits units) throws VPlexApiException {
        if ((speedStr == null) || (VPlexApiConstants.NULL_ATT_VAL.equals(speedStr))) {
            return null;
        } else {
            Long speed = null;
            Pattern p = Pattern.compile("(\\d+)");
            Matcher m = p.matcher(speedStr);
            if (m.find()) {
                speed = Long.valueOf(m.group(1));
                if (units == SpeedUnits.GBITS_PER_SECOND) {
                    return speed;
                } else {
                    return speed * BITS_GBITS_CONVERSION;
                }
            } else {
                throw new VPlexApiException(String.format(
                        "Unexpected format for speed: %s", speedStr));
            }
        }
    }

    /**
     * Getter for the operational status of the port.
     * 
     * @return The operational status of the port.
     */
    public String getOperationalStatus() {
        return operationalStatus;
    }

    /**
     * Setter for the operational status of the port.
     * 
     * @param status The operational status of the port.
     */
    public void setOperationalStatus(String status) {
        operationalStatus = status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAttributeFilters() {
        List<String> attFilters = new ArrayList<String>();
        for (PortAttribute att : PortAttribute.values()) {
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
        str.append("PortInfo ( ");
        str.append(super.toString());
        str.append(", portWwn: ").append(portWwn);
        str.append(", nodeWwn: ").append(nodeWwn);
        str.append(", role: ").append(role);
        str.append(", protocols: ").append((protocols == null ? "null" : protocols.toString()));
        str.append(", currentSpeed: ").append(currentSpeed);
        str.append(", operationalStatus: ").append(operationalStatus);
        str.append(" )");
        return str.toString();
    }
}
