/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import java.util.List;

import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.xmlgen.XMLConstants;

public class HDSHost {

    private String objectID;

    private String name;

    private String hostID;

    private String ipAddress;

    private String hostType;

    private String osType;

    private String capacityInKB;

    private String statusOfDBUpdating;

    private Integer numOfLus;

    private List<LogicalUnit> luList;

    private List<ISCSIName> iscsiList;

    private List<WorldWideName> wwnList;

    private List<ConfigFile> configFileList;

    private List<SnapshotGroup> snapshotGroupList;

    public HDSHost() {
    }

    public HDSHost(String name) {
        this.name = name;
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
     * @return the hostID
     */
    public String getHostID() {
        return hostID;
    }

    /**
     * @param hostID the hostID to set
     */
    public void setHostID(String hostID) {
        this.hostID = hostID;
    }

    /**
     * @return the ipAddress
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * @param ipAddress the ipAddress to set
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * @return the hostType
     */
    public String getHostType() {
        return hostType;
    }

    /**
     * @param hostType the hostType to set
     */
    public void setHostType(String hostType) {
        this.hostType = hostType;
    }

    /**
     * 
     * @return the OS type
     */
    public String getOsType() {
        return osType;
    }

    /**
     * 
     * @param osType the osType to set
     */
    public void setOsType(String osType) {
        this.osType = osType;
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
     * @return the statusOfDBUpdating
     */
    public String getStatusOfDBUpdating() {
        return statusOfDBUpdating;
    }

    /**
     * @param statusOfDBUpdating the statusOfDBUpdating to set
     */
    public void setStatusOfDBUpdating(String statusOfDBUpdating) {
        this.statusOfDBUpdating = statusOfDBUpdating;
    }

    /**
     * @return the numOfLus
     */
    public Integer getNumOfLus() {
        return numOfLus;
    }

    /**
     * @param numOfLus the numOfLus to set
     */
    public void setNumOfLus(Integer numOfLus) {
        this.numOfLus = numOfLus;
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

    /**
     * @return the iscsiList
     */
    public List<ISCSIName> getIscsiList() {
        return iscsiList;
    }

    /**
     * @param iscsiList the iscsiList to set
     */
    public void setIscsiList(List<ISCSIName> iscsiList) {
        this.iscsiList = iscsiList;
    }

    /**
     * @return the wwnList
     */
    public List<WorldWideName> getWwnList() {
        return wwnList;
    }

    /**
     * @param wwnList the wwnList to set
     */
    public void setWwnList(List<WorldWideName> wwnList) {
        this.wwnList = wwnList;
    }

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != this.name) {
            xmlString.append(HDSConstants.SPACE_STR).append("name=")
                    .append(HDSConstants.QUOTATION_STR).append(this.name)
                    .append(HDSConstants.QUOTATION_STR);
        }

        if (null != this.objectID) {
            xmlString.append(HDSConstants.SPACE_STR).append("objectID=")
                    .append(HDSConstants.QUOTATION_STR).append(this.objectID)
                    .append(HDSConstants.QUOTATION_STR);
        }

        if (null != this.hostType) {
            xmlString.append(HDSConstants.SPACE_STR).append("hostType=")
                    .append(HDSConstants.QUOTATION_STR).append(this.hostType)
                    .append(HDSConstants.QUOTATION_STR);
        }

        if (null != this.osType) {
            xmlString.append(HDSConstants.SPACE_STR).append("osType=")
                    .append(HDSConstants.QUOTATION_STR).append(this.osType)
                    .append(HDSConstants.QUOTATION_STR);
        }
        return xmlString.toString();
    }

    public String getChildNodeXMLString() {
        StringBuilder childNodeXmlString = new StringBuilder();

        if (null != this.wwnList && !this.wwnList.isEmpty()) {
            for (WorldWideName wwnName : this.wwnList) {
                childNodeXmlString.append(XMLConstants.LESS_THAN_OP);
                childNodeXmlString.append(HDSConstants.WORLDWIDENAME);
                childNodeXmlString.append(wwnName.toXMLString());
                childNodeXmlString.append(XMLConstants.XML_CLOSING_TAG);
            }
        }

        if (null != this.wwnList && !this.wwnList.isEmpty()) {
            for (ISCSIName iscsiName : this.iscsiList) {
                childNodeXmlString.append(XMLConstants.LESS_THAN_OP);
                childNodeXmlString.append(HDSConstants.ISCSINAME);
                childNodeXmlString.append(iscsiName.toXMLString());
                childNodeXmlString.append(XMLConstants.XML_CLOSING_TAG);
            }
        }

        return childNodeXmlString.toString();
    }

    public List<ConfigFile> getConfigFileList() {
        return configFileList;
    }

    public void setConfigFileList(List<ConfigFile> configFileList) {
        this.configFileList = configFileList;
    }

    public List<SnapshotGroup> getSnapshotGroupList() {
        return snapshotGroupList;
    }

    public void setSnapshotGroupList(List<SnapshotGroup> snapshotGroupList) {
        this.snapshotGroupList = snapshotGroupList;
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        return ((HDSHost) obj).getName().equals(this.getName());
    }

}
