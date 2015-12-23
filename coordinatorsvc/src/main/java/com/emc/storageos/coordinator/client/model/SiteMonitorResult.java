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
    
    public SiteMonitorResult() {
        
    }
    
    public SiteMonitorResult(boolean isActiveSiteLeaderAlive, boolean isActiveSiteStable) {
        this.isActiveSiteLeaderAlive = isActiveSiteLeaderAlive;
        this.isActiveSiteStable = isActiveSiteStable;
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

    @Override
    public String encodeAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(isActiveSiteLeaderAlive);
        sb.append(ENCODING_SEPARATOR);
        sb.append(isActiveSiteStable);
        return sb.toString();
    }

    @Override
    public SiteMonitorResult decodeFromString(String infoStr) throws FatalCoordinatorException {
        if (infoStr == null) {
            return null;
        }

        final String[] strings = infoStr.split(ENCODING_SEPARATOR);
        if (strings.length != 2) {
            throw CoordinatorException.fatals.decodingError("invalid site monitor state info");
        }
        
        return new SiteMonitorResult(Boolean.parseBoolean(strings[0]), Boolean.parseBoolean(strings[1]));
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, "siteMonitorState");
    }

}
