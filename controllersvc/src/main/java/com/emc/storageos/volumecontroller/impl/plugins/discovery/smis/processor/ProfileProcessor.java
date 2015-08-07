/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.cim.CIMInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;

/**
 * Verify Provider Version.
 */
public class ProfileProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(ProfileProcessor.class);
    private static final String REGISTEREDNAME = "RegisteredName";
    private static final String REGISTEREDVERSION = "RegisteredVersion";
    private static final String REGISTEREDORGANIZATION = "RegisteredOrganization";
    private static final String ARRAY = "Array";
    private static final String ELEVEN = "11";
    CIMInstance highestVersionRegProfile = null;
    String highestVersion = "";
    private static final Pattern VERSION_PATTERN = Pattern
            .compile("^(?:(\\d+))?(?:\\.(\\d+))?(?:\\.(\\d+))?$");// "([0-9]+)\\.([0-9]+)\\.([0-9]+)");

    /**
     * {@inheritDoc}
     */
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        @SuppressWarnings("unchecked")
        final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
        CIMInstance profileInstance = isRegisteredProfileValid(it);
        if (null != profileInstance) {
            _logger.info("RegisteredProfile : {}", profileInstance.getObjectPath());
            addPath(keyMap, operation.getResult(), profileInstance.getObjectPath());
        } else {
            throw new SMIPluginException(
                    "Provider Version not supported,hence skipping scanning",
                    SMIPluginException.ERRORCODE_PROVIDER_NOT_SUPPORTED);
        }
    }

    /**
     * is Registered Profile Valid.
     * 
     * @param profileinstances
     * @return
     */
    private CIMInstance isRegisteredProfileValid(Iterator<CIMInstance> profileinstances) {
        CIMInstance profileInstance = null;
        while (profileinstances.hasNext()) {
            profileInstance = profileinstances.next();
            String registeredName = getCIMPropertyValue(profileInstance, REGISTEREDNAME);
            String registeredVersion = getCIMPropertyValue(profileInstance, REGISTEREDVERSION);
            String registeredOrganization = getCIMPropertyValue(profileInstance, REGISTEREDORGANIZATION);
            if (registeredName.contains(ARRAY)
                    && registeredOrganization.equalsIgnoreCase(ELEVEN)) {
                if (highestVersion.isEmpty()) {
                    highestVersionRegProfile = profileInstance;
                    highestVersion = registeredVersion;
                } else if (compareVersions(registeredVersion, highestVersion) > 0) {
                    highestVersionRegProfile = profileInstance;
                    highestVersion = registeredVersion;
                }
            }
        }
        return highestVersionRegProfile;
    }

    /**
     * Compare two 3-part version numbers and indicate whether version1 is equal to version2,
     * version1 is greater than version2, version1 is less than version2.
     * 
     * @param fullVersion1
     *            String containing version of the format major.minor.patch where major, minor, and patch are
     *            numbers
     * @param fullVersion2
     *            String containing version of the format major.minor.patch where major, minor, and patch are
     *            numbers
     * @return Return 0 if version1 equal to version 2,
     *         1 if version1 greater than version2,
     *         -1 if version1 less than version2
     */
    private int compareVersions(String fullVersion1, String fullVersion2) {
        Matcher v1Matcher = VERSION_PATTERN.matcher(fullVersion1);
        boolean isV1Match = v1Matcher.matches();
        Matcher v2Matcher = VERSION_PATTERN.matcher(fullVersion2);
        boolean isV2Match = v2Matcher.matches();
        if (isV1Match && !isV2Match) {
            return 1;
        } else if (!isV1Match && isV2Match) {
            return -1;
        } else if (!isV1Match && !isV2Match) {
            return 0;
        }
        String v1Major = v1Matcher.groupCount() >= 1 ? v1Matcher.group(1) : "0";
        String v1Minor = v1Matcher.groupCount() >= 2 ? v1Matcher.group(2) : "0";
        String v1Release = v1Matcher.groupCount() >= 3 ? v1Matcher.group(3) : "0";
        // Group can null if less than a 3 part version was specified but this is handled in
        // compareVersionParts
        String v2Major = v2Matcher.groupCount() >= 1 ? v2Matcher.group(1) : "0";
        String v2Minor = v2Matcher.groupCount() >= 2 ? v2Matcher.group(2) : "0";
        String v2Release = v2Matcher.groupCount() >= 3 ? v2Matcher.group(3) : "0";
        int compareMajor = compareVersionParts(v1Major, v2Major);
        if (compareMajor == 1) {
            return 1;
        } else if (compareMajor == -1) {
            return -1;
        } else {
            int compareMinor = compareVersionParts(v1Minor, v2Minor);
            if (compareMinor == 1) {
                return 1;
            } else if (compareMinor == -1) {
                return -1;
            } else {
                int compareRelease = compareVersionParts(v1Release, v2Release);
                if (compareRelease == 1) {
                    return 1;
                } else if (compareRelease == -1) {
                    return -1;
                } else {
                    return 0;
                }
            }
        }
    }

    /**
     * Compare 2 version number Strings. Version String which cannot be translated to an Integer will be
     * considered 0.
     * 
     * @return Return 0 if version1 equal to version 2,
     *         1 if version1 greater than version2,
     *         -1 if version1 less than version2
     */
    private int compareVersionParts(String version1, String version2) {
        int compare = 0;
        Integer v1;
        Integer v2;
        try {
            v1 = version1 != null ? Integer.parseInt(version1) : 0;
        } catch (NumberFormatException e) {
            v1 = 0;
        }
        try {
            v2 = version2 != null ? Integer.parseInt(version2) : 0;
        } catch (NumberFormatException e) {
            v2 = 0;
        }
        if (v1 > v2) {
            compare = 1;
        } else if (v1 < v2) {
            compare = -1;
        } else {
            compare = 0;
        }
        return compare;
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
