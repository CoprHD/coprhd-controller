/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import java.util.List;

import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.xmlgen.XMLConstants;

/**
 * <LogicalUnit objectID="LU.AMS200.73012495.7" devNum="7"
 * capacityInKB="4194304" devCount="1" devType="" raidType="RAID6"
 * commandDevice="0" commandDeviceSecurity="0" chassis="0" arrayGroup="3"
 * isComposite="0" path="1" defaultPortController="1" currentPortController="1"
 * trueCopyVolumeType="Simplex" shadowImageVolumeType="Simplex"
 * quickShadowVolumeType="Simplex" universalReplicatorVolumeType="Simplex"
 * sysVolFlag="0" externalVolume="0"/>
 * 
 * 
 */
public class LogicalUnit {
    private String objectID;
    private String capacityInKB;
    private int devCount;
    private Integer devNum;
    private int composite;
    private int chassis;
    private String dpPoolID;
    private String consumedCapacityInKB;
    private int path;
    private String arrayGroup;
    private String raidType;
    private List<LDEV> ldevList;
    private String name;
    private String emulation;
    private String dpType;

    /**
     * Constructor to initialize LogicalUnit
     * 
     * @param capacityInKB
     */
    public LogicalUnit(String dpPoolID, String capacityInKB, String volumeName, String emulationType, Integer devNum) {
        this.dpPoolID = dpPoolID;
        this.capacityInKB = capacityInKB;
        this.name = volumeName;
        this.emulation = emulationType;
        this.devNum = devNum;
    }

    public LogicalUnit(String luObjectID, String capacityInKB) {
        this.objectID = luObjectID;
        this.capacityInKB = capacityInKB;
    }

    public LogicalUnit() {

    }

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
     * @return the capacityInKB
     */
    public String getCapacityInKB() {
        return capacityInKB;
    }

    /**
     * @param capacityInKB the capacityInKB to set
     */
    public void setCapacityInKB(String capacityInKB) {
        this.capacityInKB = capacityInKB;
    }

    /**
     * @return the devCount
     */
    public int getDevCount() {
        return devCount;
    }

    /**
     * @param devCount the devCount to set
     */
    public void setDevCount(int devCount) {
        this.devCount = devCount;
    }

    /**
     * @return the devNum
     */
    public int getDevNum() {
        return devNum;
    }

    /**
     * @param devNum the devNum to set
     */
    public void setDevNum(int devNum) {
        this.devNum = devNum;
    }

    /**
     * @return the composite
     */
    public int getComposite() {
        return composite;
    }

    /**
     * @param composite the composite to set
     */
    public void setComposite(int composite) {
        this.composite = composite;
    }

    /**
     * @return the dpPoolID
     */
    public String getDpPoolID() {
        return dpPoolID;
    }

    /**
     * @param dpPoolID the dpPoolID to set
     */
    public void setDpPoolID(String dpPoolID) {
        this.dpPoolID = dpPoolID;
    }

    /**
     * @return the consumedCapacityInKB
     */
    public String getConsumedCapacityInKB() {
        return consumedCapacityInKB;
    }

    /**
     * @param consumedCapacityInKB the consumedCapacityInKB to set
     */
    public void setConsumedCapacityInKB(String consumedCapacityInKB) {
        this.consumedCapacityInKB = consumedCapacityInKB;
    }

    /**
     * @return the path
     */
    public int getPath() {
        return path;
    }

    /**
     * @param path the path to set
     */
    public void setPath(int path) {
        this.path = path;
    }

    /**
     * @return the arrayGroup
     */
    public String getArrayGroup() {
        return arrayGroup;
    }

    /**
     * @param arrayGroup the arrayGroup to set
     */
    public void setArrayGroup(String arrayGroup) {
        this.arrayGroup = arrayGroup;
    }

    /**
     * @return the raidType
     */
    public String getRaidType() {
        return raidType;
    }

    /**
     * @param raidType the raidType to set
     */
    public void setRaidType(String raidType) {
        this.raidType = raidType;
    }

    /**
     * @return the ldevList
     */
    public List<LDEV> getLdevList() {
        return ldevList;
    }

    /**
     * @param ldevList the ldevList to set
     */
    public void setLdevList(List<LDEV> ldevList) {
        this.ldevList = ldevList;
    }

    /**
     * @return the chassis
     */
    public int getChassis() {
        return chassis;
    }

    /**
     * @param chassis the chassis to set
     */
    public void setChassis(int chassis) {
        this.chassis = chassis;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the emulation
     */
    public String getEmulation() {
        return emulation;
    }

    /**
     * @param emulation the emulation to set
     */
    public void setEmulation(String emulation) {
        this.emulation = emulation;
    }

    /**
     * @return the dpType
     */
    public String getDpType() {
        return dpType;
    }

    /**
     * @param dpType the dpType to set
     */
    public void setDpType(String dpType) {
        this.dpType = dpType;
    }

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != this.capacityInKB) {
            xmlString.append(HDSConstants.SPACE_STR).append("capacityInKB=")
                    .append(HDSConstants.QUOTATION_STR).append(this.capacityInKB)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.name) {
            xmlString.append(HDSConstants.SPACE_STR).append(XMLConstants.SPACE)
                    .append("name=").append(HDSConstants.QUOTATION_STR).append(this.name)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.emulation) {
            xmlString.append(HDSConstants.SPACE_STR).append(XMLConstants.SPACE)
                    .append("emulation=").append(HDSConstants.QUOTATION_STR)
                    .append(this.emulation).append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.dpPoolID) {
            xmlString.append(HDSConstants.SPACE_STR).append(XMLConstants.SPACE)
                    .append("dpPoolID=").append(HDSConstants.QUOTATION_STR)
                    .append(this.dpPoolID).append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.objectID) {
            xmlString.append(HDSConstants.SPACE_STR).append(XMLConstants.SPACE)
                    .append("objectID=").append(HDSConstants.QUOTATION_STR)
                    .append(this.objectID).append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.devNum) {
            xmlString.append(HDSConstants.SPACE_STR).append(XMLConstants.SPACE)
                    .append("devNum=").append(HDSConstants.QUOTATION_STR)
                    .append(this.devNum).append(HDSConstants.QUOTATION_STR);
        }
        return xmlString.toString();
    }
}
