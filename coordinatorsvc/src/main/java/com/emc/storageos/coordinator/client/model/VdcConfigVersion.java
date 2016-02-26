/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

/**
 * VdcConfigVersion is published as a node level object in VdcManager. It
 * reflects current local vdc config version
 */
public class VdcConfigVersion implements CoordinatorSerializable {
    private static final String ENCODING_INVALID = "";

    private String vdcConfigVersion = ENCODING_INVALID;

    public VdcConfigVersion() {
    }

    public VdcConfigVersion(String configVersion) {
        vdcConfigVersion = configVersion;
    }

    public String getConfigVersion() {
        return vdcConfigVersion;
    }

    @Override
    public String toString() {
        return "vdcConfigVersion=" + vdcConfigVersion;
    }

    @Override
    public String encodeAsString() {
        return vdcConfigVersion != null? vdcConfigVersion : ENCODING_INVALID;
    }

    @Override
    public VdcConfigVersion decodeFromString(String infoStr) {
        if (infoStr == null) {
            return null;
        } else if (ENCODING_INVALID.equals(infoStr)) {
            return new VdcConfigVersion();
        } else {
            return new VdcConfigVersion(infoStr);
        }
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo("global", "upgradeconfigversion", "vdcconfigVersion");
    }
}
