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
    
    public static final String DEFAULT_TARGET_VERSION="0"; // No target data revision set

    private static final String ENCODING_SEPARATOR = "\0";

    private final long vdcConfigVersion;
    private final String actionRequired;
    private final String targetDataRevision;

    public SiteInfo() {
        vdcConfigVersion = 0;
        actionRequired = NONE;
        targetDataRevision = DEFAULT_TARGET_VERSION;
    }

    public SiteInfo(final long version, final String actionRequired) {
        this(version, actionRequired, DEFAULT_TARGET_VERSION);
    }

    public SiteInfo(final long version, final String actionRequired, final String targetDataRevision) {
        this.vdcConfigVersion = version;
        this.actionRequired = actionRequired;
        this.targetDataRevision = targetDataRevision;
    }

    public long getVdcConfigVersion() {
        return vdcConfigVersion;
    }

    public String getActionRequired() {
        return actionRequired;
    }
    
    public String getTargetDataRevision() {
        return targetDataRevision;
    }
    
    public boolean isNullTargetDataRevision() {
        return SiteInfo.DEFAULT_TARGET_VERSION.equals(targetDataRevision);
    }

    @Override
    public String toString() {
        return "vdc config version=" + Strings.repr(vdcConfigVersion) + ", action=" + actionRequired + ", target data revision=" + targetDataRevision;
    }

    @Override
    public String encodeAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(vdcConfigVersion);
        sb.append(ENCODING_SEPARATOR);
        sb.append(actionRequired);
        sb.append(ENCODING_SEPARATOR);
        sb.append(targetDataRevision);
        return sb.toString();
    }

    @Override
    public SiteInfo decodeFromString(String infoStr) throws DecodingException {
        if (infoStr == null) {
            return null;
        }

        final String[] strings = infoStr.split(ENCODING_SEPARATOR);
        if (strings.length != 3) {
            throw CoordinatorException.fatals.decodingError("invalid site info");
        }

        Long hash = Long.valueOf(strings[0]);
        return new SiteInfo(hash, strings[1], strings[2]);
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        // site specific target
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, "siteInfo", true);
    }
}
