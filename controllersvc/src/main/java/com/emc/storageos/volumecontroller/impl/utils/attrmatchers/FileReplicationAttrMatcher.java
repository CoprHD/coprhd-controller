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


import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.CopyTypes;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.SupportedFileReplicationTypes;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ListMultimap;

/**
 * FileReplicationAttrMatcher - is an attribute matcher to select storage pools does support file replication.
 * This attribute matcher will execute only if the 'file_replication_type' key is set in attribute matcher.
 * The replication matcher would call for either local or remote replication.
 * 
 * Local replication - The inputs would be replication type (Local) and copy mode (Synchronous or Asynchronous)
 *   The matcher would filter the storage pools of storage system which does support LOCAL replication and 
 *   then filter those storage pools which does support the given copy mode!!!.
 * 
 * Remote replication - 
 *   Input : replication type - REMOTE
 *           copyMode - Synchronous or Asynchronous
 *           remoteCopies - file_replication copies in terms of virtual array and virtual pool pair
 *           
 *   1. Get the list of storage pools identified by source virtual pool.
 *   2. Filter the source storage pools of storage system which does support REMOTE replication and 
 *      storage pools which does support the given copy mode!!!.
 *   3. Get the list of storage pools identified by target virtual pool.   
 *   4. Filter the target storage pools of storage system which does support the given copy mode.
 *   4. Filter the remote storage pools which are from the same source storage device type but they should belong
 *      different storage array(cluster).
 *   5. If remote storage pools found, then set the source storage pools as matched storage pools for source virtual pool.       
 *          
 *           
 * @author lakhiv
 *
 */

public class FileReplicationAttrMatcher extends AttributeMatcher {
    private static final Logger _logger = LoggerFactory.getLogger(FileReplicationAttrMatcher.class);
    private static final String STORAGE_DEVICE = "storageDevice";
    private static final String SUPPORTED_COPY_TYPES = "supportedCopyTypes";

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        return (null != attributeMap && attributeMap.containsKey(Attributes.file_replication_type.toString()));
    }

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(
            List<StoragePool> allPools, Map<String, Object> attributeMap) {
    	
        Map<String, List<String>> remoteCopySettings = (Map<String, List<String>>)
                attributeMap.get(Attributes.file_replication.toString());
               
        _logger.info("Pools matching file replication protection  Started :  {} ",
                Joiner.on("\t").join(getNativeGuidFromPools(allPools)));
        
        // Group the storage pools by storage system
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        ListMultimap<URI, StoragePool> storageToPoolMap = ArrayListMultimap.create();
        for (StoragePool pool : allPools) {
            storageToPoolMap.put(pool.getStorageDevice(), pool);
        }
        _logger.debug("Grouped Source Storage Devices : {}", storageToPoolMap.asMap().keySet());
        
        Set<String> remotePoolUris = null;
        ListMultimap<String, URI> remotestorageToPoolMap = null;
        ListMultimap<String, URI> remotestorageTypeMap = ArrayListMultimap.create();
        String  replicationType = (String)attributeMap.get(Attributes.file_replication_type.toString());
        String copyMode = SupportedCopyModes.ASYNCHRONOUS.toString();
        if ( attributeMap.get(Attributes.file_replication_copy_mode.toString()) != null) {
        	copyMode = (String)attributeMap.get(Attributes.file_replication_copy_mode.toString());
        }
        
        if (remoteCopySettings != null && !remoteCopySettings.isEmpty()) {
        	
        	// Get the assigned or matched storage pools of remote virtual pool!!!
        	remotePoolUris = returnRemotePoolsAssociatedWithRemoteCopySettings(remoteCopySettings, getPoolUris(allPools));
            
            // Get Remote Storage Systems associated with given remote Settings via VPool's matched or
            // assigned Pools
            remotestorageToPoolMap = groupStoragePoolsByStorageSystem(remotePoolUris, copyMode);
            _logger.info("Grouped Remote Storage Devices : {}", remotestorageToPoolMap.asMap().keySet());
            
            // Group the remote storage system based on storage device type!!!
            for (Entry<String, Collection<URI>> storageToPoolsEntry : remotestorageToPoolMap
                    .asMap().entrySet()) {
            	StorageSystem system = _objectCache.queryObject(StorageSystem.class, URI.create(storageToPoolsEntry.getKey()));
            	if(system != null) {
            		remotestorageTypeMap.put(system.getSystemType(), system.getId());
            	}
            }  
        }
        
        for (Entry<URI, Collection<StoragePool>> storageToPoolsEntry : storageToPoolMap
                .asMap().entrySet()) {
            StorageSystem system = _objectCache.queryObject(StorageSystem.class, storageToPoolsEntry.getKey());
            if (null == system.getSupportedReplicationTypes() || 
            		system.getSupportedReplicationTypes().isEmpty()) {
            	_logger.debug("Storage system {} does not support replication, skipping the pools of the device",
            			system.getLabel());
                continue;
            }
            // In case of remote replication, verify the target copies have valid storage pools.
            if(replicationType.equalsIgnoreCase(SupportedFileReplicationTypes.REMOTE.toString())) {
            	// Remote replication!!
            	if (system.getSupportedReplicationTypes().contains(SupportedFileReplicationTypes.REMOTE.toString()) ) {         	
            		// Get the remote pool of storage system same type!!!
            		// If we find valid storage pools in remote system, add the source storage system pools matched pools!!!
                    if (remoteStoragePoolsOfSourceType(system, remotestorageTypeMap)){
                        _logger.info(String.format("Adding Pools %s, as associated Storage System %s is remote replication Storage System",
                                Joiner.on("\t").join(storageToPoolsEntry.getValue()), system.getNativeGuid()));
                        matchedPools.addAll(storageToPoolsEntry.getValue());
                    } else {
                        _logger.info(String.format("Skipping Pools %s, as same Storage System type pools not found",
                                Joiner.on("\t").join(storageToPoolsEntry.getValue())));
                    }
                }else {
                    _logger.info(
                            "Skipping Pools {}, as associated Storage System is not replication supported",
                            Joiner.on("\t").join(storageToPoolsEntry.getValue()));
                }	
            }else if (replicationType.equalsIgnoreCase(SupportedFileReplicationTypes.LOCAL.toString())) {
            	// Local replication!!!
            	Set<StoragePool> storagePools = new HashSet<StoragePool>();
            	String copyType = getPoolCopyTypeFromCopyModes(copyMode);
            	// Add all the storage pools of storage system which supports local replication!!!
            	if (system.getSupportedReplicationTypes().contains(SupportedFileReplicationTypes.LOCAL.toString())) {
            		for (StoragePool sp: storageToPoolsEntry.getValue()) {
            			if (sp.getSupportedCopyTypes().contains(copyType)) {
            				storagePools.add(sp);
            			}
            		}
            		if (!storagePools.isEmpty()) {
            			matchedPools.addAll(storagePools);
            		}else {
            			 _logger.info(
                                 "Skipping Pools {}, as the Storage pools are not supported copy type",
                                 Joiner.on("\t").join(storageToPoolsEntry.getValue()));
            		}
            	}else {
                    _logger.info(
                            "Skipping Pools {}, as associated Storage System is not replication supported",
                            Joiner.on("\t").join(storageToPoolsEntry.getValue()));
                }
            } else {
            	_logger.info("Invalid replication type given {}",replicationType);
            }
        }
        _logger.info("Pools matching file replication protection Ended: {}", Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));
        return matchedPools;
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
            
            for (Entry<URI, Collection<StoragePool>> storageToPoolsEntry : storageToPoolMap
                    .asMap().entrySet()) {
                StorageSystem system = _objectCache.queryObject(StorageSystem.class, storageToPoolsEntry.getKey());
                if (null == system.getSupportedReplicationTypes() || 
                		system.getSupportedReplicationTypes().isEmpty()) {
                    continue;
                }
                Set<String> copyModes = new HashSet<String>();
                if (system.getSupportedReplicationTypes().contains("REMOTE") || 
                		system.getSupportedReplicationTypes().contains("LOCAL")) {
                	copyModes.add(SupportedCopyModes.ASYNCHRONOUS.toString());
                    if (availableAttrMap.get(Attributes.file_replication.toString()) == null) {
                        availableAttrMap.put(Attributes.file_replication.toString(), new HashSet<String>());
                    }
                    availableAttrMap.get(Attributes.file_replication.toString()).addAll(copyModes);
                }
            }
        } catch (Exception e) {
            _logger.error("Available Attribute failed in FileReplicationAttrMatcher matcher", e);
        }
        return availableAttrMap;
    }
    
    private Set<String> getPoolUris(List<StoragePool> matchedPools) {
        Set<String> poolUris = new HashSet<String>();
        for (StoragePool pool : matchedPools) {
            poolUris.add(pool.getId().toString());
        }
        return poolUris;
    }

    private String getPoolCopyTypeFromCopyModes(String supportedCopyMode) {
    	String copyType = CopyTypes.ASYNC.name();
    	if (SupportedCopyModes.SYNCHRONOUS.name().equals(supportedCopyMode)) {
    		copyType = CopyTypes.SYNC.name();
    	} 
    	return copyType;
    }
    
    /**
     * remoteStoragePoolsOfSourceType - verifies that the target system is different than source!!!
     * 1. Source and target storage system should be of same type
     * 2. Target storage system should be different one of same type
     * 
     * @param remoteCopySettings
     * @return
     */
    private boolean remoteStoragePoolsOfSourceType(StorageSystem sourceSystem,
    		ListMultimap<String, URI> remotestorageTypeMap) {
    	if (remotestorageTypeMap != null && !remotestorageTypeMap.isEmpty()) {
    		List<URI> remoteSystems = remotestorageTypeMap.get(sourceSystem.getSystemType());
    		if (remoteSystems != null && !remoteSystems.isEmpty()) {
    			List<StorageSystem> targetSystems = _objectCache.queryObject(StorageSystem.class, remoteSystems);
    			for (StorageSystem targetSystem : targetSystems) {
    				if (!targetSystem.getInactive() && 
    						!targetSystem.getNativeGuid().equalsIgnoreCase(sourceSystem.getNativeGuid())){
    					return true;
    				}
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
    private ListMultimap<String, URI> groupStoragePoolsByStorageSystem(Set<String> allPoolUris,
    		String copyMode) {
        Set<String> columnNames = new HashSet<String>();
        columnNames.add(STORAGE_DEVICE);
        columnNames.add(SUPPORTED_COPY_TYPES);
        String copyType = getPoolCopyTypeFromCopyModes(copyMode);
        Collection<StoragePool> storagePools = _objectCache.getDbClient().queryObjectFields(StoragePool.class, columnNames,
                new ArrayList<URI>(
                        Collections2.transform(allPoolUris, CommonTransformerFunctions.FCTN_STRING_TO_URI)));
        ListMultimap<String, URI> storageToPoolMap = ArrayListMultimap.create();
        for (StoragePool pool : storagePools) {
        	if (pool.getSupportedCopyTypes() == null || 
        			!pool.getSupportedCopyTypes().contains(copyType)) {
        		_logger.debug("Skipping the storage pool {} as it does not supports copy type", pool.getNativeGuid());
        		continue;
        	}
            storageToPoolMap.put(pool.getStorageDevice().toString(), pool.getId());
        }
        return storageToPoolMap;
    }
}