/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import java.util.List;

import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.xmlgen.XMLConstants;

public class HostStorageDomain {

    private String objectID;

    private String name;

    private String portID;

    private String domainID;

    private String hostMode;

    private String hostMode2;

    private String hostModeOption;

    private String displayName;

    private String domainType;

    private String nickname;

    private List<Path> pathList;

    private List<WorldWideName> wwnList;

    private List<ISCSIName> iscsiList;

    private List<FreeLun> freeLunList;

    public HostStorageDomain(String portID, String name, String domainType, String nickName) {
        this.portID = portID;
        this.name = name;
        this.domainType = domainType;
        this.nickname = nickName;
    }

    public HostStorageDomain(HostStorageDomain oldHSD) {
        this.setObjectID(oldHSD.getObjectID());
        this.setPortID(oldHSD.getPortID());
        this.setDomainID(oldHSD.getDomainID());
    }

    public HostStorageDomain(String objectID) {
        this.objectID = objectID;
    }

    public HostStorageDomain() {
        // TODO Auto-generated constructor stub
    }

    public String getObjectID() {
        return objectID;
    }

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

    public String getPortID() {
        return portID;
    }

    public void setPortID(String portID) {
        this.portID = portID;
    }

    public String getDomainID() {
        return domainID;
    }

    public void setDomainID(String domainID) {
        this.domainID = domainID;
    }

    public String getHostMode() {
        return hostMode;
    }

    public void setHostMode(String hostMode) {
        this.hostMode = hostMode;
    }

    public String getHostMode2() {
        return hostMode2;
    }

    public void setHostMode2(String hostMode2) {
        this.hostMode2 = hostMode2;
    }

    public String getHostModeOption() {
        return hostModeOption;
    }

    public void setHostModeOption(String hostModeOption) {
        this.hostModeOption = hostModeOption;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDomainType() {
        return domainType;
    }

    public void setDomainType(String domainType) {
        this.domainType = domainType;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * @return the pathList
     */
    public List<Path> getPathList() {
        return pathList;
    }

    /**
     * @param pathList the pathList to set
     */
    public void setPathList(List<Path> pathList) {
        this.pathList = pathList;
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

    /**
     * @return the iscsciList
     */
    public List<ISCSIName> getIscsiList() {
        return iscsiList;
    }

    /**
     * @param iscsciList the iscsciList to set
     */
    public void setIscsiList(List<ISCSIName> iscsiList) {
        this.iscsiList = iscsiList;
    }

    /**
     * @return the freeLunList
     */
    public List<FreeLun> getFreeLunList() {
        return freeLunList;
    }

    /**
     * @param freeLunList the freeLunList to set
     */
    public void setFreeLunList(List<FreeLun> freeLunList) {
        this.freeLunList = freeLunList;
    }

    public String toXMLString() {

        StringBuilder xmlString = new StringBuilder();

        if (null != this.name) {
            xmlString.append(HDSConstants.SPACE_STR).append("name=")
                    .append(HDSConstants.QUOTATION_STR).append(this.name)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.portID) {
            xmlString.append(HDSConstants.SPACE_STR).append("portID=")
                    .append(HDSConstants.QUOTATION_STR).append(this.portID)
                    .append(HDSConstants.QUOTATION_STR);
        }

        if (null != this.domainType) {
            xmlString.append(HDSConstants.SPACE_STR).append("domainType=")
                    .append(HDSConstants.QUOTATION_STR).append(this.domainType)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.nickname) {
            xmlString.append(HDSConstants.SPACE_STR).append("nickname=")
                    .append(HDSConstants.QUOTATION_STR).append(this.nickname)
                    .append(HDSConstants.QUOTATION_STR);
        }

        if (null != this.hostMode) {
            xmlString.append(HDSConstants.SPACE_STR).append("hostMode=")
                    .append(HDSConstants.QUOTATION_STR).append(this.hostMode)
                    .append(HDSConstants.QUOTATION_STR);
        }

        if (null != this.hostModeOption) {
            xmlString.append(HDSConstants.SPACE_STR).append("hostModeOption=")
                    .append(HDSConstants.QUOTATION_STR).append(this.hostModeOption)
                    .append(HDSConstants.QUOTATION_STR);
        }

        if (null != this.objectID) {
            xmlString.append(HDSConstants.SPACE_STR).append("objectID=")
                    .append(HDSConstants.QUOTATION_STR).append(this.objectID)
                    .append(HDSConstants.QUOTATION_STR);
        }
        return xmlString.toString();
    }

    /**
     * Generates the XML String for the child nodes.
     * 
     * @return
     */
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

        if (null != this.iscsiList && !this.iscsiList.isEmpty()) {
            for (ISCSIName iscsiName : this.iscsiList) {
                childNodeXmlString.append(XMLConstants.LESS_THAN_OP);
                childNodeXmlString.append(HDSConstants.ISCSINAME);
                childNodeXmlString.append(iscsiName.toXMLString());
                childNodeXmlString.append(XMLConstants.XML_CLOSING_TAG);
            }
        }

        return childNodeXmlString.toString();
    }
}
