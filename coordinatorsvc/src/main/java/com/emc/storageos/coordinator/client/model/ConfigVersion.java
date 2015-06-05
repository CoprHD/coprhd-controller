/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.client.model;

/**
 * ConfigVersion is only published as a node level object in UpgradeService
 * According to CoordinatorClassInfo's requirement, only attribute is necessary.
 * To comply with other similar classes, we gave it a dummy id and kind.
 *      "global", "upgradeconfigversion"
 */
public class ConfigVersion implements CoordinatorSerializable {
    private static final String ENCODING_INVALID   = "";

    private String _configVersion = null;

    public ConfigVersion() {}

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
