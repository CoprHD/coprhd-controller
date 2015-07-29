/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.hds.model;

import java.util.List;

import com.emc.storageos.hds.HDSConstants;

public class SnapshotGroup {
    private String objectID;

    private String arrayType;

    private String serialNumber;

    private String groupName;

    private String replicationFunction;

    private List<ReplicationInfo> replicationInfoList;

    public String getObjectID() {
        return objectID;
    }

    public void setObjectID(String objectID) {
        this.objectID = objectID;
    }

    public String getArrayType() {
        return arrayType;
    }

    public void setArrayType(String arrayType) {
        this.arrayType = arrayType;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getReplicationFunction() {
        return replicationFunction;
    }

    public void setReplicationFunction(String replicationFunction) {
        this.replicationFunction = replicationFunction;
    }

    public List<ReplicationInfo> getReplicationInfoList() {
        return replicationInfoList;
    }

    public void setReplicationInfoList(List<ReplicationInfo> replicationInfoList) {
        this.replicationInfoList = replicationInfoList;
    }

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != this.objectID) {
            xmlString.append(HDSConstants.SPACE_STR).append("objectID=")
                    .append(HDSConstants.QUOTATION_STR).append(this.objectID)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.arrayType) {
            xmlString.append(HDSConstants.SPACE_STR).append("arrayType=")
                    .append(HDSConstants.QUOTATION_STR).append(this.arrayType)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.serialNumber) {
            xmlString.append(HDSConstants.SPACE_STR).append("serialNumber=")
                    .append(HDSConstants.QUOTATION_STR).append(this.serialNumber)
                    .append(HDSConstants.QUOTATION_STR);
        }

        if (null != this.groupName) {
            xmlString.append(HDSConstants.SPACE_STR)
                    .append("groupName=").append(HDSConstants.QUOTATION_STR)
                    .append(this.groupName).append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.replicationFunction) {
            xmlString.append(HDSConstants.SPACE_STR)
                    .append("replicationFunction=").append(HDSConstants.QUOTATION_STR)
                    .append(this.replicationFunction).append(HDSConstants.QUOTATION_STR);
        }
        return xmlString.toString();
    }
}
