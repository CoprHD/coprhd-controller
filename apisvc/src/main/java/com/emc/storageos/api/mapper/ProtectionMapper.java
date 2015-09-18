/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapDiscoveredSystemObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.protection.ProtectionSetRestRep;
import com.emc.storageos.model.protection.ProtectionSystemRPClusterRestRep;
import com.emc.storageos.model.protection.ProtectionSystemRestRep;

public class ProtectionMapper {
    public static ProtectionSetRestRep map(ProtectionSet from) {
        if (from == null) {
            return null;
        }
        ProtectionSetRestRep to = new ProtectionSetRestRep();
        mapDataObjectFields(from, to);
        to.setProtectionSystem(toRelatedResource(ResourceTypeEnum.PROTECTION_SYSTEM, from.getProtectionSystem()));
        to.setProtectionId(from.getProtectionId());
        if (from.getVolumes() != null) {
            for (String volume : from.getVolumes()) {
                to.getVolumes().add(toRelatedResource(ResourceTypeEnum.VOLUME, URI.create(volume)));
            }
        }
        to.setProtectionStatus(from.getProtectionStatus());
        to.setProject(toRelatedResource(ResourceTypeEnum.PROJECT, from.getProject()));
        to.setNativeGuid(from.getNativeGuid());

        return to;
    }

    public static ProtectionSystemRestRep map(ProtectionSystem from) {
        if (from == null) {
            return null;
        }
        ProtectionSystemRestRep to = new ProtectionSystemRestRep();
        mapDiscoveredSystemObjectFields(from, to);
        to.setInstallationId(from.getInstallationId());
        to.setMajorVersion(from.getMajorVersion());
        to.setMinorVersion(from.getMinorVersion());
        to.setIpAddress(from.getIpAddress());
        to.setPortNumber(from.getPortNumber());
        to.setReachableStatus(from.getReachableStatus());
        to.setUsername(from.getUsername());
        to.setNativeGuid(from.getNativeGuid());
        if (from.getRpSiteNames() != null) {
            List<ProtectionSystemRPClusterRestRep> clusterReps = new ArrayList<>();
            for (Map.Entry<String, String> clusterEntry : from.getRpSiteNames().entrySet()) {
                ProtectionSystemRPClusterRestRep clusterRep = new ProtectionSystemRPClusterRestRep();
                clusterRep.setClusterId(clusterEntry.getKey());
                clusterRep.setClusterName(clusterEntry.getValue());
                // See if there are assigned virtual arrays for clusters
                if (from.getSiteAssignedVirtualArrays() != null && from.getSiteAssignedVirtualArrays().get(clusterEntry.getKey()) != null) {
                    List<String> assignedVarrays = new ArrayList<>();
                    assignedVarrays.addAll(from.getSiteAssignedVirtualArrays().get(clusterEntry.getKey()));
                    clusterRep.setAssignedVarrays(assignedVarrays);
                }
                clusterReps.add(clusterRep);
            }
            to.setRpClusters(clusterReps);
        }
        return to;
    }
}
