/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

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

    private final int version;

    public SiteInfo() {
        version = 0;
    }

    public SiteInfo(final int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "config hash=" + Strings.repr(version);
    }

    @Override
    public boolean equals(Object object) {
        if (object == null) {
            return false;
        }

        if (!(object instanceof SiteInfo)) {
            return false;
        }

        final SiteInfo state = (SiteInfo) object;
        return version == state.version;
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        return builder.append(this.version).toHashCode();
    }

    @Override
    public String encodeAsString() {
        return String.valueOf(version);
    }

    @Override
    public SiteInfo decodeFromString(String infoStr) throws DecodingException {
        Integer hash = Integer.valueOf(infoStr);
        return new SiteInfo(hash);
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, "siteInfo");
    }
}
