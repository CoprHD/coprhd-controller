/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

/**
 * ConfigVersion is only published as a node level object in UpgradeService
 * According to CoordinatorClassInfo's requirement, only attribute is necessary.
 * To comply with other similar classes, we gave it a dummy id and kind.
 * "global", "upgradeconfigversion"
 */
public class ConfigVersion implements CoordinatorSerializable {
    private static final String ENCODING_INVALID = "";

    private String _configVersion = null;

    public ConfigVersion() {
    }

    public ConfigVersion(String configVersion) {
        _configVersion = configVersion;
    }

    public String getConfigVersion() {
        return _configVersion;
    }

    @Override
    public String toString() {
        return "configVersion=" + _configVersion;
    }

    @Override
    public String encodeAsString() {
        return _configVersion != null ? _configVersion : ENCODING_INVALID;
    }

    @Override
    public ConfigVersion decodeFromString(String infoStr) {
        if (infoStr == null) {
            return null;
        } else if (ENCODING_INVALID.equals(infoStr)) {
            return new ConfigVersion();
        } else {
            return new ConfigVersion(infoStr);
        }
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo("global", "upgradeconfigversion", "configVersion");
    }
}
