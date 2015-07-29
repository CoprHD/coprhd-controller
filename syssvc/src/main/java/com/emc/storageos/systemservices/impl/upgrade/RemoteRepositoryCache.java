/*
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
package com.emc.storageos.systemservices.impl.upgrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.coordinator.client.model.CoordinatorClassInfo;
import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;

/**
 * Immutable class to hold information about a list of new ViPR software versions
 */
public class RemoteRepositoryCache implements CoordinatorSerializable {

    private Map<SoftwareVersion, List<SoftwareVersion>> _cachedVersions;
    private long _lastVersionCheck;
    private String _repositoryInfo;

    public RemoteRepositoryCache() {
        _cachedVersions = Collections.emptyMap();
        ;
        _lastVersionCheck = 0L;
        _repositoryInfo = "";
    }

    public RemoteRepositoryCache(final Map<SoftwareVersion, List<SoftwareVersion>> versionsMap, Long lastVersionCheck, String repositoryUrl) {
        _cachedVersions = Collections.unmodifiableMap(versionsMap);
        _lastVersionCheck = lastVersionCheck;
        _repositoryInfo = repositoryUrl;
    }

    public Map<SoftwareVersion, List<SoftwareVersion>> getCachedVersions() {
        return _cachedVersions;
    }

    public long getLastVersionCheck() {
        return _lastVersionCheck;
    }

    public String getRepositoryInfo() {
        return _repositoryInfo;
    }

    @Override
    public String encodeAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(_lastVersionCheck);
        sb.append(PropertyInfoExt.ENCODING_NEWLINE);
        sb.append(_repositoryInfo);
        sb.append(PropertyInfoExt.ENCODING_NEWLINE);
        for (SoftwareVersion softwareVersion : _cachedVersions.keySet()) {
            sb.append(softwareVersion.toString());
            sb.append(PropertyInfoExt.ENCODING_SEPARATOR);
            for (SoftwareVersion s : _cachedVersions.get(softwareVersion)) {
                sb.append(s.toString());
                sb.append(PropertyInfoExt.ENCODING_SEPARATOR);
            }
            sb.append(PropertyInfoExt.ENCODING_NEWLINE);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public RemoteRepositoryCache decodeFromString(String swVersionsString) {
        Map<SoftwareVersion, List<SoftwareVersion>> softwareVersionsMap = new HashMap<SoftwareVersion, List<SoftwareVersion>>();
        long lastRefresh = 0L;
        String repositoryUrl = "";
        if (null != swVersionsString) {
            String[] swVersionsArray = swVersionsString.split(PropertyInfoExt.ENCODING_NEWLINE);
            if (swVersionsArray.length >= 2) {
                lastRefresh = Long.parseLong(swVersionsArray[0]);
                repositoryUrl = swVersionsArray[1];
                for (int i = 2; i < swVersionsArray.length; i++) {
                    String[] pStrings = swVersionsArray[i].split(PropertyInfoExt.ENCODING_SEPARATOR);
                    SoftwareVersion keyVersion = new SoftwareVersion(pStrings[0]);
                    List<SoftwareVersion> valueList = new ArrayList<SoftwareVersion>();
                    int len = pStrings.length;
                    if (len > 1) {
                        for (int j = 1; j < len; j++) {
                            valueList.add(new SoftwareVersion(pStrings[j]));
                        }
                    }
                    softwareVersionsMap.put(keyVersion, valueList);
                }
            }
        }
        return new RemoteRepositoryCache(softwareVersionsMap, lastRefresh, repositoryUrl);
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo("global", "cachedversions", "cachedversions");
    }
}
