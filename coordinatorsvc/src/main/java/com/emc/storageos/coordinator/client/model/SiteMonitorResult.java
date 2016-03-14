/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

public class SiteMonitorResult implements CoordinatorSerializable {
    
    public static final String CONFIG_KIND = "siteMonitorState";
    public static final String CONFIG_ID = "global";

    private long dbQuorumLostSince;
    
    public SiteMonitorResult() {
        
    }
    
    private SiteMonitorResult(long dbQuorumLostSince) {
        this.dbQuorumLostSince = dbQuorumLostSince;
    }

    public long getDbQuorumLostSince() {
        return dbQuorumLostSince;
    }

    public void setDbQuorumLostSince(long dbQuorumLostSince) {
        this.dbQuorumLostSince = dbQuorumLostSince;
    }

    @Override
    public String encodeAsString() {
        return String.valueOf(dbQuorumLostSince);
    }

    @Override
    public SiteMonitorResult decodeFromString(String infoStr) throws FatalCoordinatorException {
        if (infoStr == null) {
            return null;
        }

        try {
            return new SiteMonitorResult(Long.valueOf(infoStr));
        } catch (NumberFormatException e) {
            return new SiteMonitorResult();
        }

    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, "siteMonitorState");
    }

}
