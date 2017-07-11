/*
 * Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.InvalidSoftwareVersionException;

public class SoftwareVersion implements Comparable<SoftwareVersion> {
    private static final Logger log = LoggerFactory.getLogger(SoftwareVersion.class);

    private static final String SOFTWARE_VERSION_PREFIX = ProductName.getName() + "-";
    private static final String SOFTWARE_VERSION_RELEASE_WILDCARD = "*";
    private static final String SOFTWARE_VERSION_PATTERN = "(" + SOFTWARE_VERSION_PREFIX + "|)(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\w+|\\"
            + SOFTWARE_VERSION_RELEASE_WILDCARD + ")";
    private static final String SOFTWARE_RELEASE_INT_PATTERN = "\\d+";
    private static final Pattern intPattern = Pattern.compile(SOFTWARE_RELEASE_INT_PATTERN);

    private final String prefix;
    private final String release;
    private final int[] versionTuple;

    public SoftwareVersion(String versionStr) throws InvalidSoftwareVersionException {
        try {
            Matcher m = Pattern.compile(SOFTWARE_VERSION_PATTERN).matcher(versionStr);
            m.find();
            prefix = (m.group(1).length() > 0) ? m.group(1) : SOFTWARE_VERSION_PREFIX;
            versionTuple = new int[m.groupCount() - 2];
            for (int i = 0; i < versionTuple.length; i++) {
                versionTuple[i] = Integer.valueOf(m.group(i + 2));
            }
            release = m.group(m.groupCount());

            // Sanity check: the input must match the toString() output
            String stringRepresent = this.toString();
            if (!stringRepresent.equals(versionStr) && !stringRepresent.equals(SOFTWARE_VERSION_PREFIX + versionStr)) {
                throw new Exception();
            }
        } catch (Exception e) {
            throw CoordinatorException.fatals.invalidSoftwareVersion("Invalid software version: " + versionStr);
        }
    }

    public static SoftwareVersion toSoftwareVersion(final String versionStr) {
        try {
            return new SoftwareVersion(versionStr);
        } catch (InvalidSoftwareVersionException e) {
            log.error("toSoftwareVersion(): {}: Skipping.", e);
            return null;
        }
    }

    public static List<SoftwareVersion> toSoftwareVersionList(final String versionsStr) {
        if (versionsStr != null) {
            List<SoftwareVersion> versions = new ArrayList<SoftwareVersion>();
            for (String v : versionsStr.split(",")) {
                try {
                    versions.add(new SoftwareVersion(v));
                } catch (InvalidSoftwareVersionException e) {
                    log.error("stringToList(): {}: Skipping.", e);
                }
            }
            if (!versions.isEmpty()) {
                return versions;
            }
        }
        return null;
    }

    public static String listToString(final List<SoftwareVersion> versions) {
        if (versions != null && !versions.isEmpty()) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < versions.size(); i++) {
                if (i > 0) {
                    s.append(",");
                }
                s.append(versions.get(i));
            }
            return s.toString();
        }
        return null;
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(prefix);
        int[] tuple = versionTuple;
        for (int idx = 0; idx < tuple.length; idx++) {
            str.append(String.valueOf(tuple[idx]));
            str.append(".");
        }
        str.append(release);
        return str.toString();
    }

    @Override
    public int hashCode() {
        return release.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null) {
            SoftwareVersion version = (SoftwareVersion) obj;
            if (this.compareTo(version) == 0 && this.release.equals(version.release)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int compareTo(SoftwareVersion version) {
        int[] thisTuple = versionTuple;
        int[] targetTuple = version.versionTuple;

        for (int idx = 0; idx < thisTuple.length; idx++) {
            if (thisTuple[idx] < targetTuple[idx]) {
                return -1;
            } else if (thisTuple[idx] > targetTuple[idx]) {
                return 1;
            }
        }
        // At this point, the version number sequence is compared to be equal.
        // So compare the release number
        // If both integer, compare as integer.
        // if one is integer, integer is greater than string.
        // otherwise, compare as strings.
        if (isValidInteger(this.release) && isValidInteger(version.release)) {
            return Integer.valueOf(this.release).compareTo(Integer.valueOf(version.release));
        }
        if (isValidInteger(this.release)) {
            return 1;
        }
        if (isValidInteger(version.release)) {
            return -1;
        }

        return this.release.compareTo(version.release);
    }

    /**
     * Check if two versions are equal. If there is a wild card in the release then the two versions are equal if the
     * major/minor number matches. For example 1.0.0.8.* is equal to 1.0.0.8.1 and 1.0.0.8.50
     * 
     * @param v version to compare to this version
     * @return true if the versions are equal accepting wild card releases
     */
    public boolean weakEquals(SoftwareVersion v) {
        log.debug("Version detail info: version release {}, version prefix {}", v.release, v.prefix);
        if (v.release.equals(SOFTWARE_VERSION_RELEASE_WILDCARD) || this.release.equals(SOFTWARE_VERSION_RELEASE_WILDCARD)) {
            return this.prefix.equals(v.prefix) && Arrays.equals(versionTuple, v.versionTuple);
        }
        return this.equals(v);
    }

    /**
     * Check if it's possible to switch from current version to the target version. The method will check the Version metadata of
     * the target version image file, if the current version is in the upgradeFromVersionsList or downgradeFromVersionlist, it will return
     * true.
     * If the current version is naturally upgradeable to the target version, it returns true as well.
     * 
     * @param to
     * @return
     * @throws IOException
     */
    public boolean isSwitchableTo(SoftwareVersion to) throws IOException {
        // Must be different from the current version.
        if (equals(to)) {
            return false;
        }
        if (isNaturallySwitchableTo(to)) {
            return true;
        }
        try {
            if (compareTo(to) < 0) {
                SoftwareVersionMetadata versionMetadata = SoftwareVersionMetadata.getInstance(to);
                for (SoftwareVersion v : versionMetadata.upgradeFromVersionsList) {
                    if (this.weakEquals(v)) {
                        return true;
                    }
                }
            } else {
                SoftwareVersionMetadata versionMetadata = SoftwareVersionMetadata.getInstance(this);
                for (SoftwareVersion v : versionMetadata.downgradeToVersionsList) {
                    if (v.weakEquals(to)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (FileNotFoundException e) {
            log.info("Version " + to + " not found on the system.  Switching to this version is not possible.");
            return false;
        }
    }

    /**
     * Check if it's possible to upgrade from current version to the target version naturally.
     * 
     * @param to
     * @return
     * @throws IOException
     */
    public boolean isNaturallySwitchableTo(SoftwareVersion to) {
        // Must be different from the current version.
        if (equals(to)) {
            return false;
        }

        // All major numbers are the same.
        int minorIdx = versionTuple.length - 1;
        for (int idx = 0; idx < minorIdx; idx++) {
            if (to.versionTuple[idx] != versionTuple[idx]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates whether the version is in valid format.
     * 
     * @param version
     *            - Input version string
     * @return true - if input version string is valid false - otherwise
     */
    public static boolean isValid(String version) {
        try {
            // This is used to suppress violation of unused instance
            // Actually, we just detect whether it would throw Exception when instantiating
            // Exception would be thrown if version is not valid, then return false
            new SoftwareVersion(version); // NOSONAR("squid:S1848")
            return true;
        } catch (InvalidSoftwareVersionException e) {
            return false;
        }
    }

    /**
     * Validate whether a string is parseable to integer
     * 
     * @param str string to match
     * @return true if it is an integer string; false otherwise.
     */
    private static boolean isValidInteger(String str) {
        return intPattern.matcher(str).matches();
    }
}
