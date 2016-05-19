/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

public class SiteMonitorResult implements CoordinatorSerializable {
    private static final String ENCODING_SEPARATOR = "\0";

    public static final String CONFIG_KIND = "siteMonitorState";
    public static final String CONFIG_ID = "global";

    private long dbQuorumLostSince;
    private long dbQuorumLastActive;
    
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

    public long getDbQuorumLastActive() {
        return dbQuorumLastActive;
    }

    public void setDbQuorumLastActive(long dbQuorumLastActive) {
        this.dbQuorumLastActive = dbQuorumLastActive;
    }

    @Override
    public String encodeAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(dbQuorumLostSince);
        sb.append(ENCODING_SEPARATOR);
        sb.append(dbQuorumLastActive);
        return sb.toString();
    }

    @Override
    public SiteMonitorResult decodeFromString(String infoStr) throws FatalCoordinatorException {
        if (infoStr == null) {
            return null;
        }

        try {
            final String[] strings = infoStr.split(ENCODING_SEPARATOR);
            SiteMonitorResult result = new SiteMonitorResult();
            if (strings.length >= 1) {
                long lostSince = Long.valueOf(strings[0]);
                result.setDbQuorumLostSince(lostSince);
            } 
            if (strings.length > 1) {
                long lastActive = Long.valueOf(strings[1]);
                result.setDbQuorumLastActive(lastActive);
            } 
            return result;
        } catch (NumberFormatException e) {
            return new SiteMonitorResult();
        }

    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, "siteMonitorState");
    }

}
