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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StorageSystem.SupportedReplicationTypes;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ListMultimap;

public class RemoteMirrorProtectionMatcher extends AttributeMatcher {
    private static final Logger _logger = LoggerFactory.getLogger(RemoteMirrorProtectionMatcher.class);
    private static final String STORAGE_DEVICE = "storageDevice";
    
    private AttributeMatcher rdfGroupPlacementMatcher;

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
        URI projectURI = null;
		Project project = null;
		if (null != attributeMap.get(Attributes.project.toString())) {

			projectURI = (URI) attributeMap.get(Attributes.project.toString());
			project = _objectCache.getDbClient().queryObject(Project.class,
					projectURI);
		}
      
       Set<String> copyModes = getSupportedCopyModesFromGivenRemoteSettings(remoteCopySettings);
       String copyMode = null;
       for (String cMode : copyModes) {
    	   copyMode = cMode;
       }
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
            	URI rmSystemUri = null;
            	for (String rmSysUri : system.getRemotelyConnectedTo()) {
            		rmSystemUri = URI.create(rmSysUri);
            	}
            	
            	StorageSystem remoteSystem = _objectCache.getDbClient().queryObject(StorageSystem.class, rmSystemUri);
                _logger.info("Remotely Connected To : {}", Joiner.on("\t").join(system.getRemotelyConnectedTo()));
                Set<String> copies = new HashSet<String>(system.getRemotelyConnectedTo());
                copies.retainAll(remotestorageToPoolMap.asMap().keySet());
                _logger.info("Remotely Connected Systems Matched with Remote VArray : {}", Joiner.on("\t").join(copies));
                if (!copies.isEmpty() && isRemotelyConnectedViaExpectedCopyMode(system, remoteCopySettings)
                		&& findRAGroup(system, remoteSystem,copyMode, project, null)) {
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

	public AttributeMatcher getRdfGroupPlacementMatcher() {
		return rdfGroupPlacementMatcher;
	}

	public void setRdfGroupPlacementMatcher(AttributeMatcher rdfGroupPlacementMatcher) {
		this.rdfGroupPlacementMatcher = rdfGroupPlacementMatcher;
	}
	
	 private List<RemoteDirectorGroup> storeRAGroupsinList(final Iterator<URI> raGroupIter) {
	        List<RemoteDirectorGroup> groups = new ArrayList<RemoteDirectorGroup>();
	        while (raGroupIter.hasNext()) {
	            URI raGroupId = raGroupIter.next();
	            RemoteDirectorGroup raGroup = _objectCache.getDbClient().queryObject(RemoteDirectorGroup.class,
	                    raGroupId);
	            if (!raGroup.getInactive()) {
	                groups.add(raGroup);
	            }
	        }
	        return groups;
	    }
	 
	
	
	private boolean findRAGroup(final StorageSystem sourceStorageSystem,
         final StorageSystem targetStorageSystem, final String copyMode, final Project project,
         final URI consistencyGroupUri) {
		if (project == null) return true;
     URIQueryResultList raGroupsInDB = new URIQueryResultList();

     BlockConsistencyGroup cgObj = null;
     if (null != consistencyGroupUri) {
         cgObj = _objectCache.getDbClient().queryObject(BlockConsistencyGroup.class, consistencyGroupUri);
     }
     // Primary name check, "V-<projectname>" or "<projectname>"
     StringSet grpNames = SRDFUtils.getQualifyingRDFGroupNames(project);

     // For placement requiring project label, at least warn if the project label is so long that
     // it may cause an issue now or in the future.
     // If placement doesn't require project-based label below, remove this check.
    
     _objectCache.getDbClient().queryByConstraint(ContainmentConstraint.Factory
             .getStorageDeviceRemoteGroupsConstraint(sourceStorageSystem.getId()), raGroupsInDB);
     Iterator<URI> raGroupIter = raGroupsInDB.iterator();
     //TODO CG
    // List<RemoteDirectorGroup> raGroups = findRAGroupAssociatedWithCG(raGroupIter, cgObj);
     List<RemoteDirectorGroup> raGroups = storeRAGroupsinList(raGroupIter);
     for (RemoteDirectorGroup raGroup : raGroups) {
         URI raGroupId = raGroup.getId();

         _logger.info(String
                 .format("SRDF RA Group Placement: Checking to see if RA Group: %s is suitable for SRDF protection, given the request.",
                         raGroup.getLabel()));
         _logger.info(String.format(
                 "SRDF RA Group Placement: Source Array: %s --> Target Array: %s",
                 sourceStorageSystem.getNativeGuid(), targetStorageSystem.getNativeGuid()));

         // Check to see if it exists in the DB and is active
         if (null == raGroup || raGroup.getInactive()) {
             _logger.info("SRDF RA Group Placement: Found that the RA Group is either not in the database or in the deactivated state, not considering.");
             continue;
         }

         // Check to see if the RA Group contains (substring is OK) any of the desired labels
         if (raGroup.getLabel() == null || !SRDFUtils.containsRaGroupName(grpNames, raGroup.getLabel())) {
             _logger.info(String
                     .format("SRDF RA Group Placement: Found that the RA Group does not have a label or does not contain any of the names (%s), which is currently required for leveraging existing RA Groups.",
                             StringUtils.join(grpNames, ",")));
             continue;
         }

         // Check to see if the source storage system ID matches
         if (!raGroup.getSourceStorageSystemUri().equals(sourceStorageSystem.getId())) {
             _logger.info(String
                     .format("SRDF RA Group Placement: Found that the RA Group does not cater to the source storage system we require.  We require %s, but this group is defined as %s",
                             sourceStorageSystem.getNativeGuid(), raGroup.getNativeGuid()));
             continue;
         }

         // Check to see if the remote storage system ID matches
         if (!raGroup.getRemoteStorageSystemUri().equals(targetStorageSystem.getId())) {
             _logger.info(String
                     .format("SRDF RA Group Placement: Found that the RA Group does not cater to the remote (target) storage system we require.  We require %s, but this group is defined as %s",
                             targetStorageSystem.getNativeGuid(), raGroup.getNativeGuid()));
             continue;
         }

         // Check to see if the connectivity status is UP
         if (!raGroup.getConnectivityStatus().equals(
                 RemoteDirectorGroup.ConnectivityStatus.UP.toString())) {
             _logger.info(String
                     .format("SRDF RA Group Placement: Found that the RA Group is not in the proper connectivity state of UP, instead it is in the state: %s",
                             raGroup.getConnectivityStatus().toString()));
             continue;
         }

         // Just a warning in case the RA group isn't set properly, a sign of a possible bad
         // decision to come.
         if (raGroup.getSupportedCopyMode() == null) {
             _logger.warn(String
                     .format("SRDF RA Group Placement: Copy Mode not set on RA Group %s, probably an unsupported SRDF Deployment: ",
                             raGroup.getLabel()));
         }

         // Check to see if the policy of the RDF group is set to ALL or the same as in our vpool
         // for that copy
         if (raGroup.getSupportedCopyMode() != null
                 && !raGroup.getSupportedCopyMode().equals(
                         RemoteDirectorGroup.SupportedCopyModes.ALL.toString())
                 && !raGroup.getSupportedCopyMode().equals(copyMode)) {
             _logger.info(String
                     .format("SRDF RA Group Placement: Found that the RA Group does is using the proper copy policy of %s, instead it is using copy policy: %s",
                             copyMode, raGroup.getSupportedCopyMode().toString()));
             continue;
         }

         // More than 1 RA Group is available, only if RA Groups corresponding to given CGs is
         // not available.
         // Look for empty RA Groups alone, which can be used to create this new CG.
         if (raGroups.size() > 1 && null != cgObj && raGroup.getVolumes() != null
                 && !raGroup.getVolumes().isEmpty()) {
             _logger.info(String
                     .format("Found that the RDF Group has existing volumes with a CG different from expected: %s .",
                             cgObj.getLabel()));
             continue;
         }

         _logger.info(String
                 .format("SRDF RA Group Placement: RA Group: %s on %s --> %s is selected for SRDF protection",
                         raGroup.getLabel(), sourceStorageSystem.getNativeGuid(),
                         targetStorageSystem.getNativeGuid()));
         return true;
     }

     _logger.warn("SRDF RA Group Placement: No RA Group was suitable for SRDF protection.  See previous log messages for specific failed criteria on each RA Group considered.");
     return false;
 }
}
