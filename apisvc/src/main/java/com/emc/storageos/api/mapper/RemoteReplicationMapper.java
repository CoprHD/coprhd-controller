/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.util.HashSet;
import java.util.Set;

import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupRestRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairRestRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetRestRep;

public class RemoteReplicationMapper {

    public static RemoteReplicationSetRestRep map(RemoteReplicationSet from) {
        if (from == null) {
            return null;
        }
        RemoteReplicationSetRestRep to = new RemoteReplicationSetRestRep();

        to.setId(from.getId());
        to.setDeviceLabel(from.getDeviceLabel());
        to.setNativeId(from.getNativeId());
        to.setReachable(from.getReachable());
        to.setStorageSystemType(from.getStorageSystemType());
        to.setReplicationState(from.getReplicationState());
        if (from.getSupportedElementTypes() != null) {
            to.setSupportedElementTypes(from.getSupportedElementTypes());
        }
        if (from.getSupportedReplicationModes() != null) {
            to.setSupportedReplicationModes(from.getSupportedReplicationModes());
        }

        if (from.getSupportedReplicationLinkGranularity() != null) {
            to.setSupportedReplicationLinkGranularity(from.getSupportedReplicationLinkGranularity());
        }

        if (from.getSystemToRolesMap() != null) {
            Set<String> sourceSystems = new HashSet<>();
            Set<String> targetSystems = new HashSet<>();
            for (String storageSystemName : from.getSystemToRolesMap().keySet()) {
                for (String role:from.getSystemToRolesMap().get(storageSystemName)) {
                    if (com.emc.storageos.storagedriver.model.remotereplication.
                            RemoteReplicationSet.ReplicationRole.TARGET.name().equals(role)) {
                        targetSystems.add(storageSystemName);
                    } else if (com.emc.storageos.storagedriver.model.remotereplication.
                            RemoteReplicationSet.ReplicationRole.SOURCE.name().equals(role)) {
                        sourceSystems.add(storageSystemName);
                    }
                }
            }
            to.setSourceSystems(sourceSystems);
            to.setTargetSystems(targetSystems);
        }

        return to;
    }

    public static RemoteReplicationGroupRestRep map(RemoteReplicationGroup from) {
        if (from == null) {
            return null;
        }
        RemoteReplicationGroupRestRep to = new RemoteReplicationGroupRestRep();

        to.setId(from.getId());
        to.setDeviceLabel(from.getDeviceLabel());
        to.setNativeId(from.getNativeId());
        to.setReachable(from.getReachable());
        to.setStorageSystemType(from.getStorageSystemType());
        to.setDisplayName(from.getDisplayName());
        to.setReplicationMode(from.getReplicationMode());
        to.setReplicationState(from.getReplicationState());

        return to;
    }

    public static RemoteReplicationPairRestRep map(RemoteReplicationPair from) {
        if (from == null) {
            return null;
        }
        RemoteReplicationPairRestRep to = new RemoteReplicationPairRestRep();

        to.setId(from.getId());
        to.setNativeId(from.getNativeId());
        to.setElementType(from.getElementType().toString());
        to.setReplicationMode(from.getReplicationMode());
        to.setReplicationState(from.getReplicationState());
        to.setSourceElement(from.getSourceElement().getURI());
        to.setTargetElement(from.getTargetElement().getURI());
        if (from.getReplicationSet() != null) {
            to.setReplicationSet(toRelatedResource(ResourceTypeEnum.REMOTE_REPLICATION_SET, from.getReplicationSet()));
        }
        if (from.getReplicationGroup() != null) {
            to.setReplicationGroup(toRelatedResource(ResourceTypeEnum.REMOTE_REPLICATION_GROUP, from.getReplicationGroup()));
        }
        to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.getTenant().getURI()));
        to.setProject(toRelatedResource(ResourceTypeEnum.PROJECT, from.getProject().getURI()));

        to.setReplicationDirection(from.getReplicationDirection());
        return to;
    }
}
