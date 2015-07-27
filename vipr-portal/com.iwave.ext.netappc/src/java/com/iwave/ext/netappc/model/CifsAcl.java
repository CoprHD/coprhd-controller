/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc.model;

import java.io.Serializable;

public class CifsAcl implements Serializable {
    private static final long serialVersionUID = 1L;

    private CifsAccess access;
    
    private String shareName;
    private String userName;
    
    /** if not null, indicates that ACL is for Unix group */
    private String groupName;

    public CifsAccess getAccess() {
        return access;
    }

    public void setAccess(CifsAccess access) {
        this.access = access;
    }

    public String getShareName() {
        return shareName;
    }

    public void setShareName(String shareName) {
        this.shareName = shareName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("CifsAcl(");
        buf.append(shareName);
        buf.append(") access=");
        buf.append(access);
        buf.append(" user=");
        buf.append(userName);
        buf.append(" group=");
        buf.append(groupName);
        return buf.toString();
    }

}
