/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.SupportedReplicationTypes;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ListMultimap;

public class RemoteMirrorProtectionMatcher extends AttributeMatcher {
    private static final Logger _logger = LoggerFactory.getLogger(RemoteMirrorProtectionMatcher.class);
    private static final String STORAGE_DEVICE = "storageDevice";

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        return (null != attributeMap && attributeMap.containsKey(Attributes.remote_copy.toString()));
    }

    private Set<String> getPoolUris(List<StoragePool> matchedPools) {
        Set<String> poolUris = new HashSet<String>();
        for (StoragePool pool : matchedPools) {
            poolUris.add(pool.getId().toString());
        }
        return poolUris;
    }

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(
            List<StoragePool> allPools, Map<String, Object> attributeMap) {
        Map<String, List<String>> remoteCopySettings = (Map<String, List<String>>)
                attributeMap.get(Attributes.remote_copy.toString());
        _logger.info("Pools matching remote protection  Started :  {} ",
                Joiner.on("\t").join(getNativeGuidFromPools(allPools)));
        // group by storage system
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        ListMultimap<URI, StoragePool> storageToPoolMap = ArrayListMultimap.create();
        for (StoragePool pool : allPools) {
            storageToPoolMap.put(pool.getStorageDevice(), pool);
        }
        _logger.info("Grouped Source Storage Devices : {}", storageToPoolMap.asMap().keySet());
        Set<String> remotePoolUris = returnRemotePoolsAssociatedWithRemoteCopySettings(remoteCopySettings, getPoolUris(allPools));
        _logger.info("Remote Pools found : {}", remotePoolUris);
        // get Remote Storage Systems associated with given remote Settings via VPool's matched or
        // assigned Pools
        ListMultimap<String, URI> remotestorageToPoolMap = groupStoragePoolsByStorageSystem(remotePoolUris);
        _logger.info("Grouped Remote Storage Devices : {}", remotestorageToPoolMap.asMap().keySet());
        for (Entry<URI, Collection<StoragePool>> storageToPoolsEntry : storageToPoolMap
                .asMap().entrySet()) {
            StorageSystem system = _objectCache.queryObject(StorageSystem.class, storageToPoolsEntry.getKey());
            if (null == system.getSupportedReplicationTypes()) {
                continue;
            }
            if (system.getSupportedReplicationTypes().contains(SupportedReplicationTypes.SRDF.toString()) &&
                    null != system.getRemotelyConnectedTo()) {
                _logger.info("Remotely Connected To : {}", Joiner.on("\t").join(system.getRemotelyConnectedTo()));
                Set<String> copies = new HashSet<String>(system.getRemotelyConnectedTo());
                copies.retainAll(remotestorageToPoolMap.asMap().keySet());
                _logger.info("Remotely Connected Systems Matched with Remote VArray : {}", Joiner.on("\t").join(copies));
                if (!copies.isEmpty() && isRemotelyConnectedViaExpectedCopyMode(system, remoteCopySettings)) {
                    _logger.info(String.format("Adding Pools %s, as associated Storage System %s is connected to any remote Storage System",
                            Joiner.on("\t").join(storageToPoolsEntry.getValue()), system.getNativeGuid()));
                    matchedPools.addAll(storageToPoolsEntry.getValue());
                } else {
                    _logger.info(String.format("Skipping Pools %s, as associated Storage System %s is not connected to any remote Storage System",
                            Joiner.on("\t").join(storageToPoolsEntry.getValue()), system.getNativeGuid()));
                }
            } else {
                _logger.info(
                        "Skipping Pools {}, as associated Storage System is not SRDF supported or there are no available active RA Groups",
                        Joiner.on("\t").join(storageToPoolsEntry.getValue()));
            }
        }
        _logger.info("Pools matching remote mirror protection Ended: {}", Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));
        return matchedPools;
    }

    private Set<String> getSupportedCopyModesFromGivenRemoteSettings(Map<String, List<String>> remoteCopySettings) {
        Set<String> copyModes = new HashSet<String>();
        for (Entry<String, List<String>> entry : remoteCopySettings.entrySet()) {
            copyModes.addAll(entry.getValue());
        }
        return copyModes;
    }

    private boolean isRemotelyConnectedViaExpectedCopyMode(StorageSystem system, Map<String, List<String>> remoteCopySettings) {
        List<URI> raGroupUris = _objectCache.getDbClient().queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceRemoteGroupsConstraint(system.getId()));
        _logger.info("List of RA Groups {}", Joiner.on("\t").join(raGroupUris));
        Set<String> copyModes = getSupportedCopyModesFromGivenRemoteSettings(remoteCopySettings);
        _logger.info("Supported Copy Modes from Given Settings {}", Joiner.on("\t").join(copyModes));
        for (URI raGroupUri : raGroupUris) {
            RemoteDirectorGroup raGroup = _objectCache.queryObject(RemoteDirectorGroup.class, raGroupUri);
            if (null == raGroup || raGroup.getInactive()) {
                continue;
            }
            if (system.getRemotelyConnectedTo() != null
                    && system.getRemotelyConnectedTo().contains(raGroup.getRemoteStorageSystemUri().toString())) {
                if (SupportedCopyModes.ALL.toString().equalsIgnoreCase(raGroup.getSupportedCopyMode())
                        || copyModes.contains(raGroup.getSupportedCopyMode())) {
                    _logger.info("Found Mode {} with RA Group {}", raGroup.getSupportedCopyMode(), raGroup.getNativeGuid());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Choose Pools based on remote VPool's matched or assigned Pools
     * 
     * @param remoteCopySettings
     * @return
     */
    private Set<String> returnRemotePoolsAssociatedWithRemoteCopySettings(
            Map<String, List<String>> remoteCopySettings,
            Set<String> poolUris) {
        Set<String> remotePoolUris = new HashSet<String>();
        for (Entry<String, List<String>> entry : remoteCopySettings.entrySet()) {
            VirtualPool vPool = _objectCache.queryObject(VirtualPool.class,
                    URI.create(entry.getKey()));
            if (null == vPool) {
                remotePoolUris.addAll(poolUris);
            } else if (null != vPool.getUseMatchedPools() && vPool.getUseMatchedPools()) {
                if (null != vPool.getMatchedStoragePools()) {
                    remotePoolUris.addAll(vPool.getMatchedStoragePools());
                }
            } else if (null != vPool.getAssignedStoragePools()) {
                remotePoolUris.addAll(vPool.getAssignedStoragePools());
            }
        }
        return remotePoolUris;
    }

    /**
     * Group Storage Pools by Storage System
     * 
     * @param allPoolUris
     * @return
     */
    private ListMultimap<String, URI> groupStoragePoolsByStorageSystem(Set<String> allPoolUris) {
        Set<String> columnNames = new HashSet<String>();
        columnNames.add(STORAGE_DEVICE);
        Collection<StoragePool> storagePools = _objectCache.getDbClient().queryObjectFields(StoragePool.class, columnNames,
                new ArrayList<URI>(
                        Collections2.transform(allPoolUris, CommonTransformerFunctions.FCTN_STRING_TO_URI)));
        ListMultimap<String, URI> storageToPoolMap = ArrayListMultimap.create();
        for (StoragePool pool : storagePools) {
            storageToPoolMap.put(pool.getStorageDevice().toString(), pool.getId());
        }
        return storageToPoolMap;
    }

    @Override
    public Map<String, Set<String>> getAvailableAttribute(List<StoragePool> neighborhoodPools,
            URI vArrayId) {
        Map<String, Set<String>> availableAttrMap = new HashMap<String, Set<String>>(1);
        try {
            ListMultimap<URI, StoragePool> storageToPoolMap = ArrayListMultimap.create();
            for (StoragePool pool : neighborhoodPools) {
                storageToPoolMap.put(pool.getStorageDevice(), pool);
            }
            boolean foundCopyModeAll = false;
            for (Entry<URI, Collection<StoragePool>> storageToPoolsEntry : storageToPoolMap
                    .asMap().entrySet()) {
                StorageSystem system = _objectCache.queryObject(StorageSystem.class, storageToPoolsEntry.getKey());
                if (null == system.getSupportedReplicationTypes()) {
                    continue;
                }
                if (system.getSupportedReplicationTypes().contains(SupportedReplicationTypes.SRDF.toString()) &&
                        null != system.getRemotelyConnectedTo()) {
                    List<URI> raGroupUris = _objectCache.getDbClient().queryByConstraint(
                            ContainmentConstraint.Factory.getStorageDeviceRemoteGroupsConstraint(system
                                    .getId()));
                    List<RemoteDirectorGroup> RemoteDirectorGroup = _objectCache.queryObject(RemoteDirectorGroup.class, raGroupUris);
                    Set<String> copyModes = new HashSet<String>();
                    for (RemoteDirectorGroup rg : RemoteDirectorGroup) {
                        if (SupportedCopyModes.ALL.toString().equalsIgnoreCase(rg.getSupportedCopyMode())) {
                            _logger.info("found Copy Mode ALL with RA Group {} ", rg.getId());
                            foundCopyModeAll = true;
                            copyModes.add(SupportedCopyModes.SYNCHRONOUS.toString());
                            copyModes.add(SupportedCopyModes.ASYNCHRONOUS.toString());
                            copyModes.add(SupportedCopyModes.ACTIVE.toString());
                            break;
                        } else {
                            copyModes.add(rg.getSupportedCopyMode());
                        }
                    }
                    if (availableAttrMap.get(Attributes.remote_copy.toString()) == null) {
                        availableAttrMap.put(Attributes.remote_copy.toString(), new HashSet<String>());
                    }
                    availableAttrMap.get(Attributes.remote_copy.toString()).addAll(copyModes);
                    if (foundCopyModeAll) {
                        return availableAttrMap;
                    }
                }
            }
        } catch (Exception e) {
            _logger.error("Available Attribute failed in remote mirror protection matcher", e);
        }
        return availableAttrMap;
    }
}
