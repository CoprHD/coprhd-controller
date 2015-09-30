/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

public class Port {

    private String objectID;
    private String displayName;
    private Long channelSpeed;
    private String portControllerID;
    private String portID;
    // possible values: [Fibre]
    private String portType;
    private String wwpn;
    private String topology;
    private String arrayType;
    private String serialNumber;
    private String portRole;

    /**
     * @return the objectID
     */
    public String getObjectID() {
        return objectID;
    }

    /**
     * @param objectID the objectID to set
     */
    public void setObjectID(String objectID) {
        this.objectID = objectID;
    }

    /**
     * @return the displayName
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @param displayName the displayName to set
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return the channelSpeed
     */
    public Long getChannelSpeed() {
        return channelSpeed;
    }

    /**
     * @param channelSpeed the channelSpeed to set
     */
    public void setChannelSpeed(Long channelSpeed) {
        this.channelSpeed = channelSpeed;
    }

    /**
     * @return the portControllerID
     */
    public String getPortControllerID() {
        return portControllerID;
    }

    /**
     * @param portControllerID the portControllerID to set
     */
    public void setPortControllerID(String portControllerID) {
        this.portControllerID = portControllerID;
    }

    /**
     * @return the portID
     */
    public String getPortID() {
        return portID;
    }

    /**
     * @param portID the portID to set
     */
    public void setPortID(String portID) {
        this.portID = portID;
    }

    /**
     * @return the portType
     */
    public String getPortType() {
        return portType;
    }

    /**
     * @param portType the portType to set
     */
    public void setPortType(String portType) {
        this.portType = portType;
    }

    /**
     * @return the wwpn
     */
    public String getWwpn() {
        return wwpn.replace(".", ":");
    }

    /**
     * @param wwpn the wwpn to set
     */
    public void setWwpn(String wwpn) {
        this.wwpn = wwpn;
    }

    /**
     * @return the topology
     */
    public String getTopology() {
        return topology;
    }

    /**
     * @param topology the topology to set
     */
    public void setTopology(String topology) {
        this.topology = topology;
    }

    /**
     * @return the arrayType
     */
    public String getArrayType() {
        return arrayType;
    }

    /**
     * @param arrayType the arrayType to set
     */
    public void setArrayType(String arrayType) {
        this.arrayType = arrayType;
    }

    /**
     * @return the serialNumber
     */
    public String getSerialNumber() {
        return serialNumber;
    }

    /**
     * @param serialNumber the serialNumber to set
     */
    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    /**
     * @return the portRole
     */
    public String getPortRole() {
        return portRole;
    }

    /**
     * @param portRole the portRole to set
     */
    public void setPortRole(String portRole) {
        this.portRole = portRole;
    }
}
