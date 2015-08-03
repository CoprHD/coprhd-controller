/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.util;

import java.util.regex.Pattern;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

public class VersionChecker {

    private static CoordinatorClient coordinator;

    /**
     * Compare the two versions
     * 
     * @param minimumSupportedVersion
     * @param version - version discovered
     * @return 0 if versions are equal,
     *         < 0 if version is lower than minimumSupportedVersion,
     *         > 0 if version is higher than minimumSupportedVersion.
     */
    public static int verifyVersionDetails(String minimumSupportedVersion, String version) {
        if (minimumSupportedVersion == null) {
            throw new IllegalArgumentException("minimum supported version received cannot be null");
        } else if (version == null) {
            throw new IllegalArgumentException("discovered version received cannot be null");
        }
        version = version.trim();

        // split by dots, parentheses, and adjoining letters and numbers
        String[] versionToVerifyWith = Pattern.compile("[\\.|\\)|\\(| ]|(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)", Pattern.DOTALL).split(
                minimumSupportedVersion);
        String[] versionProvided = Pattern.compile("[\\.|\\)|\\(| ]|(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)", Pattern.DOTALL).split(version);

        // to remove leading zeroes from the first part. For vnxblock, the version is 05.32
        versionToVerifyWith[0] = versionToVerifyWith[0].replaceFirst("^0*", "");
        versionProvided[0] = versionProvided[0].replaceFirst("^0*", "");

        int i = 0;
        while (i < versionProvided.length && i < versionToVerifyWith.length
                && versionProvided[i].equals(versionToVerifyWith[i])) {
            i++;
        }

        if (i < versionProvided.length && i < versionToVerifyWith.length) {
            int length = (versionToVerifyWith[i].length() > versionProvided[i].length()) ?
                    versionToVerifyWith[i].length() : versionProvided[i].length();
            if (versionToVerifyWith[i].length() > versionProvided[i].length()) {
                versionProvided[i] = String.format("%" + length + 's', versionProvided[i]);
            } else {
                versionToVerifyWith[i] = String.format("%" + length + 's', versionToVerifyWith[i]);
            }
            int diff = versionProvided[i].compareTo(versionToVerifyWith[i]);
            return diff < 0 ? -1 : diff == 0 ? 0 : 1;
        }

        return versionProvided.length < versionToVerifyWith.length ? -1
                : versionProvided.length == versionToVerifyWith.length ? 0 : 1;
    }

    /**
     * Gets the minimum supported version from the coordinator based on system type.
     * 
     * @param systemType the system type
     * @return the minimum supported version
     */
    public static String getMinimumSupportedVersion(DiscoveredDataObject.Type systemType) {
        String minimumSupportedVersion = null;
        if (systemType != null) {
            String minimumSupportedVersionKey = getLookupKeyBasedOnSystemType(systemType.toString());
            // Get and return supported version from co-ordinator.
            minimumSupportedVersion = ControllerUtils.getPropertyValueFromCoordinator(coordinator, minimumSupportedVersionKey);
        }
        return minimumSupportedVersion;
    }

    /**
     * Gets the lookup key based on system type.
     * 
     * @param systemType
     * @return the key
     */
    private static String getLookupKeyBasedOnSystemType(String systemType) {
        return String.format("controller_%s_firmware_version", systemType);
    }

    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        VersionChecker.coordinator = coordinator;
    }
}
