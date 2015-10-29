/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

/**
 * Info for a VPlex target port.
 */
public class VPlexTargetInfo extends VPlexResourceInfo {

    // Defines the valid values for the target export status.
    public static enum ExportStatus {
        ok
    }

    // The port WWN id.
    private String portWwn;

    // The node WWN id.
    private String nodeWwn;

    // The target export status
    private String exportStatus;

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
     * Getter for the node WWN.
     * 
     * @return The node WWN.
     */
    public String getNodeWwn() {
        return VPlexApiUtils.formatWWN(nodeWwn);
    }

    /**
     * Setter for the node WWN.
     * 
     * @param strVal The node WWN.
     */
    public void setNodeWwn(String strVal) {
        nodeWwn = strVal;
    }

    /**
     * Getter for the export status for the target.
     * 
     * @return The export status for the target.
     */
    public String getExportStatus() {
        return exportStatus;
    }

    /**
     * Setter for the export status for the target.
     * 
     * @param status The export status for the target.
     */
    public void setExportStatus(String status) {
        exportStatus = status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("TargetInfo ( ");
        str.append(super.toString());
        str.append(", portWWN: ").append(portWwn);
        str.append(", nodeWWN: ").append(nodeWwn);
        str.append(", exportStatus: ").append(exportStatus);
        str.append(" )");
        return str.toString();
    }
}
