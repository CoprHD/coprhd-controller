/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.scaleio.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScaleIOQueryAllVolumesResult {
    public static final String VOLUME_ID = "ID";
    public static final String VOLUME_NAME = "Name";
    public static final String VOLUME_POOL_NAME = "poolName";
    public static final String VOLUME_SIZE_BYTES = "SizeInBytes";

    private Map<String, String> protectionDomains = new HashMap<>();
    private Map<String, List<ScaleIOAttributes>> protectionDomainToVolumes = new HashMap<>();
    private Map<String, ScaleIOAttributes> volumeInfoMap = new HashMap<>();
    private Map<String, String> volumeNameToVolumeId = new HashMap<>();
    private String errorString;
    private boolean isSuccess;

    public void setErrorString(String errorString) {
        this.errorString = errorString;
    }

    public String getErrorString() {
        return errorString;
    }

    public void setIsSuccess(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public void addProtectionDomain(String protectionDomainId, String protectionDomainName) {
        protectionDomains.put(protectionDomainId, protectionDomainName);
    }

    public void addVolume(String protectionDomainId, String id, String name, String poolName, String sizeInBytes) {
        List<ScaleIOAttributes> volumes = protectionDomainToVolumes.get(protectionDomainId);
        if (volumes == null) {
            volumes = new ArrayList<>();
            protectionDomainToVolumes.put(protectionDomainId, volumes);
        }
        ScaleIOAttributes properties = new ScaleIOAttributes();
        properties.put(VOLUME_ID, id);
        properties.put(VOLUME_NAME, name);
        volumeNameToVolumeId.put(name, id); // Required for ScaleIO 1.2 multi-snapshot output bug (CTRL-4516)
        properties.put(VOLUME_POOL_NAME, poolName);
        properties.put(VOLUME_SIZE_BYTES, sizeInBytes);
        volumes.add(properties);
        volumeInfoMap.put(id, properties);
    }

    public Collection<String> getProtectionDomainIds() {
        return protectionDomains.keySet();
    }

    public Collection<String> getAllVolumeIds(String protectionDomainId) {
        Set<String> ids = new HashSet<>();
        List<ScaleIOAttributes> volumes = protectionDomainToVolumes.get(protectionDomainId);
        if (volumes != null) {
            for (ScaleIOAttributes properties : volumes) {
                ids.add((String) properties.get(VOLUME_ID));
            }
        }
        return ids;
    }

    public Collection<String> getAllVolumeIds() {
        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, List<ScaleIOAttributes>> entry : protectionDomainToVolumes.entrySet()) {
            List<ScaleIOAttributes> volumes = entry.getValue();
            for (ScaleIOAttributes properties : volumes) {
                ids.add((String) properties.get(VOLUME_ID));
            }
        }
        return ids;
    }

    public String getVolumeIdFromName(String name) {
        return volumeNameToVolumeId.get(name);
    }

    public ScaleIOAttributes getScaleIOAttributesOfVolume(String volumeId) {
        return volumeInfoMap.get(volumeId);
    }
}
