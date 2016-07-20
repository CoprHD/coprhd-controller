/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

public class SystemCommandResult {
    private String name;
    private String systemVersion;
    private String model;
    private String serialNumber;
    private String totalNodes;
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getSystemVersion() {
        return systemVersion;
    }
    public void setSystemVersion(String systemVersion) {
        this.systemVersion = systemVersion;
    }
    public String getModel() {
        return model;
    }
    public void setModel(String model) {
        this.model = model;
    }
    public String getSerialNumber() {
        return serialNumber;
    }
    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }
    public String getTotalNodes() {
        return totalNodes;
    }
    public void setTotalNodes(String totalNodes) {
        this.totalNodes = totalNodes;
    }
    
    @Override
    public String toString() {
        return "SystemCommandResult [name=" + name + ", systemVersion=" + systemVersion + ", model=" + model + ", serialNumber="
                + serialNumber + ", totalNodes=" + totalNodes + "]";
    }
}
