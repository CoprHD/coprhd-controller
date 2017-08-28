/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.factories;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;

public class VolumeFactory {
    public static <T extends Volume> Volume newInstance(T from) {
        Volume to = new Volume();

        to.setId(URIUtil.createId(Volume.class));

        // DataObject properties
        to.setLabel(from.getLabel());
        if (from.getTag() != null && !from.getTag().isEmpty()) {
            to.setTag(new ScopedLabelSet(from.getTag()));
        }

        // BlockObject properties
        to.setStorageController(from.getStorageController());
        to.setSystemType(from.getSystemType());
        to.setProtectionController(from.getProtectionController());
        to.setNativeId(from.getNativeId());
        to.setNativeGuid(from.getNativeGuid());
        if (from.getExtensions() != null && !from.getExtensions().isEmpty()) {
            to.setExtensions(new StringMap(from.getExtensions()));
        }
        to.setConsistencyGroup(from.getConsistencyGroup());
        if (from.getProtocol() != null && !from.getProtocol().isEmpty()) {
            to.setProtocol(new StringSet(from.getProtocol()));
        }
        to.setVirtualArray(from.getVirtualArray());
        to.setWWN(from.getWWN());
        to.setDeviceLabel(from.getDeviceLabel());
        to.setAlternateName(from.getAlternateName());
        to.setRefreshRequired(from.getRefreshRequired());

        to.setProject(from.getProject());
        to.setCapacity(from.getCapacity());
        to.setThinVolumePreAllocationSize(from.getThinVolumePreAllocationSize());
        to.setThinlyProvisioned(from.getThinlyProvisioned());
        to.setVirtualPool(from.getVirtualPool());
        to.setPool(from.getPool());
        to.setTenant(from.getTenant());
        to.setProvisionedCapacity(from.getProvisionedCapacity());
        to.setAllocatedCapacity(from.getAllocatedCapacity());
        if (from.getAssociatedVolumes() != null && !from.getAssociatedVolumes().isEmpty()) {
            to.setAssociatedVolumes(new StringSet(from.getAssociatedVolumes()));
        }
        to.setPersonality(from.getPersonality());
        to.setAccessState(from.getAccessState());
        to.setProtectionSet(from.getProtectionSet());
        to.setRSetName(from.getRSetName());
        to.setRpCopyName(from.getRpCopyName());
        to.setInternalSiteName(from.getInternalSiteName());
        to.setIsComposite(from.getIsComposite());
        to.setCompositionType(from.getCompositionType());
        to.setMetaMemberSize(from.getMetaMemberSize());
        to.setMetaMemberCount(from.getMetaMemberCount());
        if (from.getMirrors() != null && !from.getMirrors().isEmpty()) {
            to.setMirrors(new StringSet(from.getMirrors()));
        }
        if (from.getRpTargets() != null && !from.getRpTargets().isEmpty()) {
            to.setRpTargets(new StringSet(from.getRpTargets()));
        }
        to.setTotalMetaMemberCapacity(from.getTotalMetaMemberCapacity());
        to.setAutoTieringPolicyUri(from.getAutoTieringPolicyUri());
        to.setSyncActive(from.getSyncActive());
        if (from.getFullCopies() != null && !from.getFullCopies().isEmpty()) {
            to.setFullCopies(new StringSet(from.getFullCopies()));
        }
        to.setAssociatedSourceVolume(from.getAssociatedSourceVolume());

        return to;
    }
}
