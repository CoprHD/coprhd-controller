/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

public class SiteMonitorResult implements CoordinatorSerializable {
    
    public static final String CONFIG_KIND = "siteMonitorState";
    public static final String CONFIG_ID = "global";
    
    private static final String ENCODING_SEPARATOR = "\0";

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
        StringBuilder sb = new StringBuilder();
        sb.append(dbQuorumLostSince);
        return sb.toString();
    }

    @Override
    public SiteMonitorResult decodeFromString(String infoStr) throws FatalCoordinatorException {
        if (infoStr == null) {
            return null;
        }
        return new SiteMonitorResult(Long.valueOf(infoStr));
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, "siteMonitorState");
    }

}
