/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import org.apache.commons.lang.builder.HashCodeBuilder;

import com.emc.storageos.coordinator.exceptions.DecodingException;
import com.emc.storageos.services.util.Strings;

/**
 * This class stores only the hash for the vdc config for now
 * The complete vdc configuration is found in the local db
 * We are simply creating a target here for VdcSiteManager to watch.
 */
public class SiteInfo implements CoordinatorSerializable {
    public static final String CONFIG_KIND = "sitetargetconfig";
    public static final String CONFIG_ID = "global";

    public static final String UPDATE_DATA_REVISION = "update_data_revision";
    public static final String RECONFIG_RESTART = "reconfig_restart";
    public static final String NONE = "none";

    private static final String ENCODING_SEPARATOR = "\0";

    private final long version;
    private final String actionRequired;

    public SiteInfo() {
        version = 0;
        actionRequired = NONE;
    }

    public SiteInfo(final long version, final String actionRequired) {
        this.version = version;
        this.actionRequired = actionRequired;
    }

    public long getVersion() {
        return version;
    }

    public String getActionRequired() {
        return actionRequired;
    }

    @Override
    public String toString() {
        return "config version=" + Strings.repr(version) + ", action=" + actionRequired;
    }

    @Override
    public String encodeAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(version);
        sb.append(ENCODING_SEPARATOR);
        sb.append(actionRequired);
        return sb.toString();
    }

    @Override
    public SiteInfo decodeFromString(String infoStr) throws DecodingException {
        if (infoStr == null) {
            return null;
        }

        final String[] strings = infoStr.split(ENCODING_SEPARATOR);
        if (strings.length != 2) {
            throw CoordinatorException.fatals.decodingError("invalid site info");
        }

        Long hash = Long.valueOf(strings[0]);
        return new SiteInfo(hash, strings[1]);
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, "siteInfo");
    }
}
