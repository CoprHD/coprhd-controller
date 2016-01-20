/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

public class SiteMonitorResult implements CoordinatorSerializable {
    
    public static final String CONFIG_KIND = "siteMonitorState";
    public static final String CONFIG_ID = "global";
    
    private static final String ENCODING_SEPARATOR = "\0";
    
    private boolean isActiveSiteLeaderAlive;
    private boolean isActiveSiteStable;
    private long dbQuorumLostSince;
    
    public SiteMonitorResult() {
        
    }
    
    private SiteMonitorResult(boolean isActiveSiteLeaderAlive, boolean isActiveSiteStable, long dbQuorumLostSince) {
        this.isActiveSiteLeaderAlive = isActiveSiteLeaderAlive;
        this.isActiveSiteStable = isActiveSiteStable;
        this.dbQuorumLostSince = dbQuorumLostSince;
    }

    public boolean isActiveSiteLeaderAlive() {
        return isActiveSiteLeaderAlive;
    }

    public void setActiveSiteLeaderAlive(boolean isActiveSiteLeaderAlive) {
        this.isActiveSiteLeaderAlive = isActiveSiteLeaderAlive;
    }

    public boolean isActiveSiteStable() {
        return isActiveSiteStable;
    }

    public void setActiveSiteStable(boolean isActiveSiteStable) {
        this.isActiveSiteStable = isActiveSiteStable;
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
        sb.append(isActiveSiteLeaderAlive);
        sb.append(ENCODING_SEPARATOR);
        sb.append(isActiveSiteStable);
        sb.append(ENCODING_SEPARATOR);
        sb.append(dbQuorumLostSince);
        return sb.toString();
    }

    @Override
    public SiteMonitorResult decodeFromString(String infoStr) throws FatalCoordinatorException {
        if (infoStr == null) {
            return null;
        }

        final String[] strings = infoStr.split(ENCODING_SEPARATOR);
        if (strings.length != 3) {
            throw CoordinatorException.fatals.decodingError("invalid site monitor state info");
        }
        
        return new SiteMonitorResult(Boolean.parseBoolean(strings[0]), Boolean.parseBoolean(strings[1]),
                Long.valueOf(strings[2]));
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, "siteMonitorState");
    }

}
