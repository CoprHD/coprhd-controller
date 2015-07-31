/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import com.emc.storageos.hds.HDSConstants;

public class Path {

    private String objectID;

    private String portID;

    private String domainID;

    private String scsiID;

    private String lun;

    private String devNum;

    private String wwnSecurityValidity;

    public Path(String portID, String domainID, String scsiID, String lun, String devNum) {
        this.portID = portID;
        this.domainID = domainID;
        this.scsiID = scsiID;
        this.lun = lun;
        this.devNum = devNum;
    }

    public Path(String objectID) {
        this.objectID = objectID;
    }

    public Path() {
        // TODO Auto-generated constructor stub
    }

    public String getObjectID() {
        return objectID;
    }

    public void setObjectID(String objectID) {
        this.objectID = objectID;
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

    /**
     * @return the scsiID
     */
    public String getScsiID() {
        return scsiID;
    }

    /**
     * @param scsiID the scsiID to set
     */
    public void setScsiID(String scsiID) {
        this.scsiID = scsiID;
    }

    /**
     * @return the lun
     */
    public String getLun() {
        return lun;
    }

    /**
     * @param lun the lun to set
     */
    public void setLun(String lun) {
        this.lun = lun;
    }

    /**
     * @return the devNum
     */
    public String getDevNum() {
        return devNum;
    }

    /**
     * @param devNum the devNum to set
     */
    public void setDevNum(String devNum) {
        this.devNum = devNum;
    }

    /**
     * @return the wwnSecurityValidity
     */
    public String getWwnSecurityValidity() {
        return wwnSecurityValidity;
    }

    /**
     * @param wwnSecurityValidity the wwnSecurityValidity to set
     */
    public void setWwnSecurityValidity(String wwnSecurityValidity) {
        this.wwnSecurityValidity = wwnSecurityValidity;
    }

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != this.portID) {
            xmlString.append(HDSConstants.SPACE_STR).append("portID=")
                    .append(HDSConstants.QUOTATION_STR).append(this.portID)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.domainID) {
            xmlString.append(HDSConstants.SPACE_STR).append("domainID=")
                    .append(HDSConstants.QUOTATION_STR).append(this.domainID)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != scsiID) {
            xmlString.append(HDSConstants.SPACE_STR).append("scsiID=")
                    .append(HDSConstants.QUOTATION_STR).append(this.scsiID)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != lun) {
            xmlString.append(HDSConstants.SPACE_STR).append("lun=")
                    .append(HDSConstants.QUOTATION_STR).append(this.lun)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.devNum) {
            xmlString.append(HDSConstants.SPACE_STR).append("devNum=")
                    .append(HDSConstants.QUOTATION_STR).append(this.devNum)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.objectID) {
            xmlString.append(HDSConstants.SPACE_STR).append("objectID=")
                    .append(HDSConstants.QUOTATION_STR).append(this.objectID)
                    .append(HDSConstants.QUOTATION_STR);
        }

        return xmlString.toString();
    }

}
