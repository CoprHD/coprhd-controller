/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import com.emc.storageos.hds.HDSConstants;

public class ReplicationInfo {

    private String objectID;
    private String pairName;
    private String pvolArrayType;
    private String pvolSerialNumber;
    private String pvolDevNum;
    private String svolArrayType;
    private String svolSerialNumber;
    private String svolDevNum;
    private String replicationFunction;
    private String status;
    private String pvolPoolID;

    public String getObjectID() {
        return objectID;
    }

    public void setObjectID(String objectID) {
        this.objectID = objectID;
    }

    public String getPairName() {
        return pairName;
    }

    public void setPairName(String pairName) {
        this.pairName = pairName;
    }

    public String getPvolArrayType() {
        return pvolArrayType;
    }

    public void setPvolArrayType(String pvolArrayType) {
        this.pvolArrayType = pvolArrayType;
    }

    public String getPvolSerialNumber() {
        return pvolSerialNumber;
    }

    public void setPvolSerialNumber(String pvolSerialNumber) {
        this.pvolSerialNumber = pvolSerialNumber;
    }

    public String getPvolDevNum() {
        return pvolDevNum;
    }

    public void setPvolDevNum(String pvolDevNum) {
        this.pvolDevNum = pvolDevNum;
    }

    public String getSvolArrayType() {
        return svolArrayType;
    }

    public void setSvolArrayType(String svolArrayType) {
        this.svolArrayType = svolArrayType;
    }

    public String getSvolSerialNumber() {
        return svolSerialNumber;
    }

    public void setSvolSerialNumber(String svolSerialNumber) {
        this.svolSerialNumber = svolSerialNumber;
    }

    public String getSvolDevNum() {
        return svolDevNum;
    }

    public void setSvolDevNum(String svolDevNum) {
        this.svolDevNum = svolDevNum;
    }

    public String getReplicationFunction() {
        return replicationFunction;
    }

    public void setReplicationFunction(String replicationFunction) {
        this.replicationFunction = replicationFunction;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPvolPoolID() {
        return pvolPoolID;
    }

    public void setPvolPoolID(String pvolPoolID) {
        this.pvolPoolID = pvolPoolID;
    }

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();

        if (null != this.objectID) {
            xmlString.append(HDSConstants.SPACE_STR).append("objectID=")
                    .append(HDSConstants.QUOTATION_STR).append(this.objectID)
                    .append(HDSConstants.QUOTATION_STR);
        }

        if (null != this.pairName) {
            xmlString.append(HDSConstants.SPACE_STR).append("pairName=")
                    .append(HDSConstants.QUOTATION_STR).append(this.pairName)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.pvolArrayType) {
            xmlString.append(HDSConstants.SPACE_STR).append("pvolArrayType=")
                    .append(HDSConstants.QUOTATION_STR).append(this.pvolArrayType)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.pvolSerialNumber) {
            xmlString.append(HDSConstants.SPACE_STR).append("pvolSerialNumber=")
                    .append(HDSConstants.QUOTATION_STR).append(this.pvolSerialNumber)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.pvolDevNum) {
            xmlString.append(HDSConstants.SPACE_STR)
                    .append("pvolDevNum=").append(HDSConstants.QUOTATION_STR)
                    .append(this.pvolDevNum).append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.svolArrayType) {
            xmlString.append(HDSConstants.SPACE_STR)
                    .append("svolArrayType=").append(HDSConstants.QUOTATION_STR).append(this.svolArrayType)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.svolSerialNumber) {
            xmlString.append(HDSConstants.SPACE_STR)
                    .append("svolSerialNumber=").append(HDSConstants.QUOTATION_STR)
                    .append(this.svolSerialNumber).append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.svolDevNum) {
            xmlString.append(HDSConstants.SPACE_STR)
                    .append("svolDevNum=").append(HDSConstants.QUOTATION_STR)
                    .append(this.svolDevNum).append(HDSConstants.QUOTATION_STR);
        }

        if (null != this.replicationFunction) {
            xmlString.append(HDSConstants.SPACE_STR)
                    .append("replicationFunction=").append(HDSConstants.QUOTATION_STR)
                    .append(this.replicationFunction).append(HDSConstants.QUOTATION_STR);
        }

        if (null != this.status) {
            xmlString.append(HDSConstants.SPACE_STR)
                    .append("status=").append(HDSConstants.QUOTATION_STR)
                    .append(this.status).append(HDSConstants.QUOTATION_STR);
        }

        if (null != this.pvolPoolID) {
            xmlString.append(HDSConstants.SPACE_STR)
                    .append("pvolPoolID=").append(HDSConstants.QUOTATION_STR)
                    .append(this.pvolPoolID).append(HDSConstants.QUOTATION_STR);
        }
        return xmlString.toString();
    }

    @Override
    public String toString() {
        return toXMLString();
    }

}
