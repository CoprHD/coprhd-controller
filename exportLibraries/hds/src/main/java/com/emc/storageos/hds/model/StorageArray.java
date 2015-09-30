/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import java.util.List;

import com.emc.storageos.hds.HDSConstants;

public class StorageArray {

    private String objectID;

    private String name;

    private String serialNumber;

    private String arrayFamily;
    private String arrayType;
    private String displayArrayType;

    private String description;
    private String controllerVersion;
    private String productName;

    private List<PortController> portControllerList;

    private List<Pool> thinPoolList;

    private List<Pool> thickPoolList;

    private List<Port> portList;

    private List<LogicalUnit> luList;

    private List<HostStorageDomain> hsdList;

    private List<TieringPolicy> tieringPolicyList;

    public StorageArray() {
    }

    /**
     * Constructor to initialize this object
     * 
     * @param objectID
     */
    public StorageArray(String objectID) {
        this.objectID = objectID;
    }

    public String getObjectID() {
        return objectID;
    }

    public void setObjectID(String objectID) {
        this.objectID = objectID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getArrayFamily() {
        return arrayFamily;
    }

    public void setArrayFamily(String arrayFamily) {
        this.arrayFamily = arrayFamily;
    }

    public String getArrayType() {
        return arrayType;
    }

    public void setArrayType(String arrayType) {
        this.arrayType = arrayType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getControllerVersion() {
        return controllerVersion;
    }

    public void setControllerVersion(String controllerVersion) {
        this.controllerVersion = controllerVersion;
    }

    /**
     * @return the displayArrayType
     */
    public String getDisplayArrayType() {
        return displayArrayType;
    }

    /**
     * @param displayArrayType the displayArrayType to set
     */
    public void setDisplayArrayType(String displayArrayType) {
        this.displayArrayType = displayArrayType;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public List<PortController> getPortControllerList() {
        return portControllerList;
    }

    public void setPortControllerList(List<PortController> portControllerList) {
        this.portControllerList = portControllerList;
    }

    public List<Port> getPortList() {
        return portList;
    }

    public void setPortList(List<Port> portList) {
        this.portList = portList;
    }

    /**
     * @return the luList
     */
    public List<LogicalUnit> getLuList() {
        return luList;
    }

    /**
     * @param luList the luList to set
     */
    public void setLuList(List<LogicalUnit> luList) {
        this.luList = luList;
    }

    public List<HostStorageDomain> getHsdList() {
        return hsdList;
    }

    public void setHsdList(List<HostStorageDomain> hsdList) {
        this.hsdList = hsdList;
    }

    public List<Pool> getThinPoolList() {
        return thinPoolList;
    }

    public void setThinPoolList(List<Pool> thinPoolList) {
        this.thinPoolList = thinPoolList;
    }

    public List<Pool> getThickPoolList() {
        return thickPoolList;
    }

    public void setThickPoolList(List<Pool> thickPoolList) {
        this.thickPoolList = thickPoolList;
    }

    public List<TieringPolicy> getTieringPolicyList() {
        return tieringPolicyList;
    }

    public void setTieringPolicyList(List<TieringPolicy> tieringPolicyList) {
        this.tieringPolicyList = tieringPolicyList;
    }

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != this.objectID) {
            xmlString.append(HDSConstants.SPACE_STR).append("objectID=")
                    .append(HDSConstants.QUOTATION_STR).append(this.objectID)
                    .append(HDSConstants.QUOTATION_STR);
        }
        return xmlString.toString();
    }

}
