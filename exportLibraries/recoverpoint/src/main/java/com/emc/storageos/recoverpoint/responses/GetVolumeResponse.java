/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.responses;

import java.io.Serializable;

/**
 * Every volume in a consistency group, even journals, are represented here.
 * 
 */
@SuppressWarnings("serial")
public class GetVolumeResponse implements Serializable {
    private String internalSiteName;
    private boolean production;
    private String wwn;
    private String rpCopyName;

    public String getInternalSiteName() {
        return internalSiteName;
    }

    public void setInternalSiteName(String internalSiteName) {
        this.internalSiteName = internalSiteName;
    }

    public boolean isProduction() {
        return production;
    }

    public void setProduction(boolean production) {
        this.production = production;
    }

    public String getWwn() {
        return wwn;
    }

    public void setWwn(String wwn) {
        this.wwn = wwn;
    }

    public String getRpCopyName() {
        return rpCopyName;
    }

    public void setRpCopyName(String rpCopyName) {
        this.rpCopyName = rpCopyName;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("\n\tinternalSiteName: " + internalSiteName);
        sb.append("\n\tproduction:       " + production);
        sb.append("\n\twwn:              " + wwn);
        sb.append("\n\trpCopyName:       " + rpCopyName);
        return sb.toString();
    }
}
