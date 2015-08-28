/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import java.util.List;

import com.emc.storageos.hds.HDSConstants;

public class ReplicationGroup {
    private String objectID;

    private String replicationGroupID;

    private String groupName;

    private String replicationFunction;

    private List<ReplicationInfo> replicationInfoList;

    @Override
    public String toString() {
        return String.format("objectID %s replicationGroupID %s groupName %s", objectID, replicationGroupID, groupName);
    }

    public String getObjectID() {
        return objectID;
    }

    public void setObjectID(String objectID) {
        this.objectID = objectID;
    }

    public String getReplicationGroupID() {
        return replicationGroupID;
    }

    public void setReplicationGroupID(String replicationGroupID) {
        this.replicationGroupID = replicationGroupID;
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
        if (null != this.replicationGroupID) {
            xmlString.append(HDSConstants.SPACE_STR)
                    .append("replicationGroupID=").append(HDSConstants.QUOTATION_STR).append(this.replicationGroupID)
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
