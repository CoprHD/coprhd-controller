/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.VirtualNAS.VirtualNasState;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.MetricsKeys;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * StorageScheduler service for block and file storage. StorageScheduler is done
 * based on desired class-of-service parameters for the provisioned storage.
 */
public class FileStorageScheduler {

    public final Logger _log = LoggerFactory
            .getLogger(FileStorageScheduler.class);

    private static final String ENABLE_METERING = "enable-metering";
    private DbClient _dbClient;
    private StorageScheduler _scheduler;
    private CustomConfigHandler customConfigHandler;
    private Map<String, String> configInfo;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setScheduleUtils(StorageScheduler scheduleUtils) {
        _scheduler = scheduleUtils;
    }

    public void setCustomConfigHandler(CustomConfigHandler customConfigHandler) {
        this.customConfigHandler = customConfigHandler;
    }

    public Map<String, String> getConfigInfo() {
		return configInfo;
	}

	public void setConfigInfo(Map<String, String> configInfo) {
		this.configInfo = configInfo;
	}

	/**
     * Schedule storage for fileshare in the varray with the given CoS
     * capabilities.
     * 
     * @param vArray
     * @param vPool
     * @param capabilities
     * @param project
     * @return list of recommended storage ports for VNAS
     */
    public List<FileRecommendation> placeFileShare(VirtualArray vArray,
            VirtualPool vPool, VirtualPoolCapabilityValuesWrapper capabilities,
            Project project) {

        _log.debug("Schedule storage for {} resource(s) of size {}.",
                capabilities.getResourceCount(), capabilities.getSize());

        // Get all storage pools that match the passed vpool params and
        // protocols. In addition, the pool must have enough capacity
        // to hold at least one resource of the requested size.
        List<StoragePool> candidatePools = _scheduler.getMatchingPools(vArray,
                vPool, capabilities);
        
        // Holds the invalid virtual nas servers from both 
        // assigned and un-assigned list.
        // the invalid vnas server clould be
        // over loaded or protocol not supported or 
        // the ports from these invalid vnas servers
        // should not be considered for file provisioning!!!
        List<VirtualNAS> invalidNasServers = new ArrayList<VirtualNAS>();

        // Get the recommendation based on virtual nas servers
        Map<VirtualNAS, List<StoragePool>> vNASPoolMap = getRecommendedVirtualNASBasedOnCandidatePools(
                vPool, vArray.getId(), candidatePools, project, invalidNasServers);

        VirtualNAS currvNAS = null;
        List<FileRecommendation> fileRecommendations = null;
        
        if (!vNASPoolMap.isEmpty()) {
            for (Entry<VirtualNAS, List<StoragePool>> eachVNASEntry : vNASPoolMap.entrySet()) {
            	// If No storage pools recommended!!!
            	if(eachVNASEntry.getValue().isEmpty()) {
            		continue;
            	}
            	
            	currvNAS = eachVNASEntry.getKey();
            	if (currvNAS != null) {
                    _log.info("Best vNAS selected: {}", currvNAS.getNasName());
                    List<StoragePool> recommendedPools = eachVNASEntry.getValue();
                    
                    // Get the recommendations for the current vnas pools.
                    List<Recommendation> poolRecommendations = _scheduler
                            .getRecommendationsForPools(vArray.getId().toString(),
                            		recommendedPools, capabilities);
                    // If we did not find pool recommendation for current vNAS
                    // Pick the pools from next available vNas recommended pools!!!
                    if(poolRecommendations.isEmpty()) {
                    	_log.info("Skipping vNAS {}, as pools are not having enough resources",
                    			currvNAS.getNasName());
                    	continue;
                    }

                    // Get the file recommendations for pool recommendation!!!
                    fileRecommendations = getFileRecommendationsForVNAS(currvNAS,
                            vArray.getId(), vPool, poolRecommendations);
                    
                    if( !fileRecommendations.isEmpty()) {
                    	_log.info("Selected vNAS {} for placement",
                    			currvNAS.getNasName());
                    	break;
                    } 
                }
            } 
        }
        
        // In case of
        // 1. vNAS does not provide file recommendations or
        // 2. vpool does not have storage pools from vnx or
        // 3. vnx does not have vdms
        // Get the file recommendations
        if(fileRecommendations == null || fileRecommendations.isEmpty()) {
        	// Get the recommendations for the candidate pools.
        	_log.info("Placement on HADomain matching pools");
            List<Recommendation> poolRecommendations = _scheduler
                    .getRecommendationsForPools(vArray.getId().toString(),
                            candidatePools, capabilities);
            
            fileRecommendations = selectStorageHADomainMatchingVpool(vPool,
                    vArray.getId(), poolRecommendations, invalidNasServers); 
        }
        // We need to place all the resources. If we can't then
        // log an error and clear the list of recommendations.
        if (fileRecommendations == null || fileRecommendations.isEmpty()) {
            _log.error(
                    "Could not find matching pools for virtual array {} & vpool {}",
                    vArray.getId(), vPool.getId());
        }

        return fileRecommendations;
    }

    /**
     * Returns the File recommendations for vNAS
     * 
     * @param vNAS
     * @param vArrayURI
     * @param vPool
     * @param poolRecommendations
     * @return
     */
    private List<FileRecommendation> getFileRecommendationsForVNAS(VirtualNAS vNAS,
            URI vArrayURI, VirtualPool vPool, List<Recommendation> poolRecommendations) {

        List<FileRecommendation> fileRecommendations = new ArrayList<FileRecommendation>();
        List<StoragePort> ports = getAssociatedStoragePorts(vNAS);

        List<URI> storagePortURIList = new ArrayList<URI>();
        for (Iterator<StoragePort> iterator = ports.iterator(); iterator.hasNext();) {
            StoragePort storagePort = iterator.next();
            storagePortURIList.add(storagePort.getId());
        }

        for (Iterator<Recommendation> iterator = poolRecommendations.iterator(); iterator.hasNext();) {

            Recommendation recommendation = iterator.next();
            FileRecommendation fRec = new FileRecommendation(recommendation);

            URI storageDevice = fRec.getSourceStorageSystem();

            if (vNAS.getStorageDeviceURI().equals(storageDevice)) {
                fRec.setStoragePorts(storagePortURIList);
                fRec.setvNAS(vNAS.getId());
                fileRecommendations.add(fRec);
            }

        }

        return fileRecommendations;
    }

    /**
     * Select storage port for exporting file share to the given client. One IP
     * transport zone per varray. Selects only one storage port for all exports
     * of a file share
     * 
     * @param fs
     *            file share being exported
     * @param protocol
     *            file storage protocol for this export
     * @param clients
     *            client network address or name
     * @return storgaePort
     * @throws badRequestException
     */
    public StoragePort placeFileShareExport(FileShare fs, String protocol,
            List<String> clients) {
        StoragePort sp;

        if (fs.getStoragePort() == null) {
            _log.debug("placement for file system {} with no assigned port.",
                    fs.getName());
            // if no storage port is selected yet, select one and record the
            // selection
            List<StoragePort> ports = getStorageSystemPortsInVarray(
                    fs.getStorageDevice(), fs.getVirtualArray());

            // Filter ports based on protocol (for example, if CIFS or NFS is
            // required)
            if ((null != protocol) && (!protocol.isEmpty())) {
                getPortsWithFileSharingProtocol(protocol, ports);
            }

            if (ports == null || ports.isEmpty()) {
                _log.error(MessageFormat
                        .format("There are no active and registered storage ports assigned to virtual array {0}",
                                fs.getVirtualArray()));
                throw APIException.badRequests.noStoragePortFoundForVArray(fs
                        .getVirtualArray().toString());
            }
            Collections.shuffle(ports);
            sp = ports.get(0);

            // update storage port selections for file share exports
            fs.setStoragePort(sp.getId());
            fs.setPortName(sp.getPortName());
            _dbClient.persistObject(fs);
        } else {
            // if a storage port is already selected for the fileshare, use that
            // port for all exports
            sp = _dbClient.queryObject(StoragePort.class, fs.getStoragePort());
            _log.debug("placement for file system {} with port {}.",
                    fs.getName(), sp.getPortName());

            // verify port supports new request.
            if ((null != protocol) && (!protocol.isEmpty())) {
                List<StoragePort> ports = new ArrayList<StoragePort>();
                ports.add(sp);
                getPortsWithFileSharingProtocol(protocol, ports);

                if (ports.isEmpty()) {
                    _log.error(MessageFormat
                            .format("There are no active and registered storage ports assigned to virtual array {0}",
                                    fs.getVirtualArray()));
                    throw APIException.badRequests
                            .noStoragePortFoundForVArray(fs.getVirtualArray()
                                    .toString());
                }
            }
        }

        return sp;
    }

    /**
     * Retrieve list of recommended storage ports for file placement
     * Follows the step to get recommendations for file placement.
     * 1. Get the valid vNas servers assigned to given project.
     * a. Get the active vNas assigned to project.
     * b. filter out vNas, if any of them are overloaded.
     * c. filter out vNas which based on vPool protocols.
     * 2. if any valid assigned vNas found for project, goto step #3
     * a. get all vNas servers in given virtual array
     * b. filter out vNas server which are assigned to some project
     * c. filter out vNas, if any of them are overloaded.
     * d. filter out vNas which based on vPool protocols
     * 3. If No - vNas servers found, goto step #
     * 3. if the dynamic metric collection enabled
     * a. sort the list of qualified vNas servers based on
     * i. vNas - avgPercentageBusy
     * ii. vNas - StorageObjects
     * iii. vNas - Storage capacity
     * 4. sort the list of qualified vNas servers based on static load
     * i. vNas - StorageObjects
     * ii. vNas - Storage capacity
     * 5. Pick the overlapping vNas server in the order and recommended by vPool.
     * 6. Pick the overlapping StorageHADomian recommended by vPool.
     * 
     * @param vPool
     * @param vArrayURI virtual array URI
     * @param poolRecommendations
     * @param project
     * @return list of recommended storage ports for VNAS
     *
     */
    private Map<VirtualNAS, List<StoragePool>> getRecommendedVirtualNASBasedOnCandidatePools(
            VirtualPool vPool, URI vArrayURI,
            List<StoragePool> candidatePools, Project project,
            List<VirtualNAS> invalidNasServers) {

        Map<VirtualNAS, List<StoragePool>> map = new LinkedHashMap<VirtualNAS, List<StoragePool>>();

        _log.info(
                "Get matching recommendations based on assigned VNAS to project {}",
                project.getLabel());

        List<VirtualNAS> vNASList = getVNASServersInProject(project, vArrayURI,
                vPool, invalidNasServers);

        if (vNASList == null || vNASList.isEmpty()) {
            _log.info(
                    "Get matching recommendations based on un-assigned VNAS in varray {}",
                    vArrayURI);
            vNASList = getUnassignedVNASServers(vArrayURI, vPool, project, invalidNasServers);
        }

        if (vNASList != null && !vNASList.isEmpty()) {
        	
        	boolean meteringEnabled = Boolean.parseBoolean(configInfo.get(ENABLE_METERING));
        	
        	if(meteringEnabled) {
        		
        		_log.info("Metering collection is enabled. Sort  vNAS list based on performance.");
        		
	            String dynamicPerformanceEnabled = customConfigHandler.getComputedCustomConfigValue(
	                    CustomConfigConstants.NAS_DYNAMIC_PERFORMANCE_PLACEMENT_ENABLED, "vnxfile", null);
	
	            _log.info("NAS dynamic performance placement enabled? : {}", dynamicPerformanceEnabled);
	
	            if (Boolean.valueOf(dynamicPerformanceEnabled)) {
	                _log.debug("Considering dynamic load to sort virtual NASs");
	                sortVNASListOnDyanamicLoad(vNASList);
	            } else {
	                _log.debug("Considering static load to sort virtual NASs");
	                sortVNASListOnStaticLoad(vNASList);
	            }
	
	        } else {
	        	Collections.shuffle(vNASList);
	        }
        }

        for (VirtualNAS vNAS : vNASList) {

            List<StoragePool> storagePools = new ArrayList<StoragePool>();

            for (StoragePool storagePool : candidatePools) {

                if (vNAS.getStorageDeviceURI().equals(storagePool.getStorageDevice())) {
                    storagePools.add(storagePool);
                }
            }
            if(!storagePools.isEmpty()) {
            	map.put(vNAS, storagePools);
            }
        }

        return map;
    }

    /**
     * Sort list of VNAS servers based on dynamic load on each VNAS
     * 
     * @param vNASList list of VNAS servers
     * 
     */
    private void sortVNASListOnDyanamicLoad(List<VirtualNAS> vNASList) {

        Collections.sort(vNASList, new Comparator<VirtualNAS>() {

            @Override
            public int compare(VirtualNAS v1, VirtualNAS v2) {

                // 1. Sort virtual nas servers based on dynamic load factor 'avgPercentBusy'.
                // 2. If multiple virtual nas servers found to be similar performance,
                // sort the virtual nas based on capacity and then number of storage objects!!!
                int value = 0;
                Double avgUsedPercentV1 = MetricsKeys.getDoubleOrNull(MetricsKeys.avgPortPercentBusy, v1.getMetrics());
                Double avgUsedPercentV2 = MetricsKeys.getDoubleOrNull(MetricsKeys.avgPortPercentBusy, v2.getMetrics());
                if (avgUsedPercentV1 != null && avgUsedPercentV2 != null) {
                    value = avgUsedPercentV1.compareTo(avgUsedPercentV2);
                }

                if (value == 0) {
                    Long storageCapacityOfV1 = MetricsKeys.getLong(MetricsKeys.usedStorageCapacity, v1.getMetrics());
                    Long storageCapacityOfV2 = MetricsKeys.getLong(MetricsKeys.usedStorageCapacity, v2.getMetrics());
                    value = storageCapacityOfV1.compareTo(storageCapacityOfV2);
                }

                if (value == 0) {
                    Long storageObjectsOfV1 = MetricsKeys.getLong(MetricsKeys.storageObjects, v1.getMetrics());
                    Long storageObjectsOfV2 = MetricsKeys.getLong(MetricsKeys.storageObjects, v2.getMetrics());
                    value = storageObjectsOfV1.compareTo(storageObjectsOfV2);
                }

                return value;
            }
        });

    }

    /**
     * Sort list of VNAS servers based on static load on each VNAS
     * 
     * @param vNASList list of VNAS servers
     * 
     */
    private void sortVNASListOnStaticLoad(List<VirtualNAS> vNASList) {

        Collections.sort(vNASList, new Comparator<VirtualNAS>() {

            @Override
            public int compare(VirtualNAS v1, VirtualNAS v2) {
            	
            	 int value = 0;
                 Double percentLoadV1 = MetricsKeys.getDoubleOrNull(MetricsKeys.percentLoad, v1.getMetrics());
                 Double percentLoadV2 = MetricsKeys.getDoubleOrNull(MetricsKeys.percentLoad, v2.getMetrics());
                 if (percentLoadV1 != null && percentLoadV2 != null) {
                     value = percentLoadV1.compareTo(percentLoadV2);
                 }

                if (value == 0) {
                	// 1. Sort virtual nas servers based on static load factor 'storageCapacity'.
                    // 2. If multiple virtual nas servers found to be similar performance,
                    // sort the virtual nas based on number of storage objects!!!
                    Long storageCapacityOfV1 = MetricsKeys.getLong(MetricsKeys.usedStorageCapacity, v1.getMetrics());
                    Long storageCapacityOfV2 = MetricsKeys.getLong(MetricsKeys.usedStorageCapacity, v2.getMetrics());
                    value = storageCapacityOfV1.compareTo(storageCapacityOfV2);
                }
                
                if (value == 0) {
                    Long storageObjectsOfV1 = MetricsKeys.getLong(MetricsKeys.storageObjects, v1.getMetrics());
                    Long storageObjectsOfV2 = MetricsKeys.getLong(MetricsKeys.storageObjects, v2.getMetrics());
                    return storageObjectsOfV1.compareTo(storageObjectsOfV2);
                }
                
                return value;

            }
        });

    }

    /**
     * Get list of unassigned VNAS servers
     * 
     * @param vArrayURI
     * @param vpool
     * @return vNASList
     * 
     */
    private List<VirtualNAS> getUnassignedVNASServers(URI vArrayURI,
            VirtualPool vpool, Project project,
            List<VirtualNAS> invalidNasServers ) {

        List<VirtualNAS> vNASList = new ArrayList<VirtualNAS>();

        _log.debug("Get VNAS servers in the vArray {}", vArrayURI);

        List<URI> vNASURIList = _dbClient
                .queryByConstraint(ContainmentConstraint.Factory
                        .getVirtualNASInVirtualArrayConstraint(vArrayURI));

        vNASList = _dbClient.queryObject(VirtualNAS.class, vNASURIList);
        if (vNASList != null && !vNASList.isEmpty()) {
            for (Iterator<VirtualNAS> iterator = vNASList.iterator(); iterator
                    .hasNext();) {
                VirtualNAS vNAS = iterator.next();
                
                if (!isVNASActive(vNAS)) {
                    _log.debug("Removing vNAS {} as it is inactive",
                            vNAS.getId());
                    iterator.remove();
                    invalidNasServers.add(vNAS);
                } else if (MetricsKeys.getBoolean(MetricsKeys.overLoaded,
                        vNAS.getMetrics())) {
                    _log.debug("Removing vNAS {} as it is overloaded",
                            vNAS.getId());
                    iterator.remove();
                    invalidNasServers.add(vNAS);
                } else if (!vNAS.getProtocols().containsAll(
                        vpool.getProtocols())) {
                    _log.debug("Removing vNAS {} as it does not support vpool protocols: {}",
                            vNAS.getId(), vpool.getProtocols());
                    iterator.remove();
                    invalidNasServers.add(vNAS);
                } else if (!NullColumnValueGetter.isNullURI(vNAS.getProject())) {
                	if ( !project.getId().equals(vNAS.getProject()) ) {
                		_log.debug("Removing vNAS {} as it is assigned to project",
                    			vNAS.getId());
                		iterator.remove();
                		invalidNasServers.add(vNAS);
                	} 
                } 
            }
        }
        if (vNASList != null) {
            _log.info("Got  {} un-assigned vNas servers in the vArray {}",
                    vNASList.size(), vArrayURI);
        }
        return vNASList;
    }

    /**
     * Get list of associated storage ports of VNAS server
     * 
     * @param vNAS
     * @return spList
     * 
     */
    private List<StoragePort> getAssociatedStoragePorts(VirtualNAS vNAS) {

        StringSet spIdSet = vNAS.getStoragePorts();

        List<URI> spURIList = new ArrayList<URI>();
        if (spIdSet != null && !spIdSet.isEmpty()) {
            for (String id : spIdSet) {
                spURIList.add(URI.create(id));
            }
        }

        List<StoragePort> spList = _dbClient.queryObject(StoragePort.class,
                spURIList);

        if (spIdSet != null && !spList.isEmpty()) {
            for (Iterator<StoragePort> iterator = spList.iterator(); iterator
                    .hasNext();) {
                StoragePort storagePort = iterator.next();

                if (storagePort.getInactive()
                        || storagePort.getTaggedVirtualArrays() == null
                        || !RegistrationStatus.REGISTERED.toString()
                                .equalsIgnoreCase(
                                        storagePort.getRegistrationStatus())
                        || (StoragePort.OperationalStatus.valueOf(storagePort
                                .getOperationalStatus()))
                                .equals(StoragePort.OperationalStatus.NOT_OK)
                        || !DiscoveredDataObject.CompatibilityStatus.COMPATIBLE
                                .name().equals(
                                        storagePort.getCompatibilityStatus())
                        || !DiscoveryStatus.VISIBLE.name().equals(
                                storagePort.getDiscoveryStatus())) {

                    iterator.remove();
                }

            }
        }

        if (spList != null && !spList.isEmpty()) {
            Collections.sort(spList, new Comparator<StoragePort>() {

                @Override
                public int compare(StoragePort sp1, StoragePort sp2) {

                    if (sp1.getMetrics() != null && sp2.getMetrics() != null) {

                        Double sp1UsedPercent = MetricsKeys.getDoubleOrNull(MetricsKeys.avgPortPercentBusy, sp1.getMetrics());
                        Double sp2UsedPercent = MetricsKeys.getDoubleOrNull(MetricsKeys.avgPortPercentBusy, sp2.getMetrics());

                        if (sp1UsedPercent != null && sp2UsedPercent != null) {
                            return sp1UsedPercent.compareTo(sp2UsedPercent);
                        }
                    }
                    return 0;
                }
            });
        }
        return spList;

    }

    /**
     * Get list of VNAS servers assigned to a project
     * 
     * @param project
     * @param varrayUri
     * @param vpool
     * @return vNASList
     * 
     */
    private List<VirtualNAS> getVNASServersInProject(Project project,
            URI varrayUri, VirtualPool vpool,
            List<VirtualNAS> invalidNasServers) {

        List<VirtualNAS> vNASList = null;

        _log.debug("Get VNAS servers assigned to project {}", project);

        StringSet vNASServerIdSet = project.getAssignedVNasServers();

        if (vNASServerIdSet != null && !vNASServerIdSet.isEmpty()) {

            _log.info("Number of vNAS servers assigned to this project: {}",
                    vNASServerIdSet.size());

            List<URI> vNASURIList = new ArrayList<URI>();
            for (String vNASId : vNASServerIdSet) {
                vNASURIList.add(URI.create(vNASId));
            }

            vNASList = _dbClient.queryObject(VirtualNAS.class, vNASURIList);

            for (Iterator<VirtualNAS> iterator = vNASList.iterator(); iterator
                    .hasNext();) {
                VirtualNAS virtualNAS = iterator.next();

                // Remove inactive, incompatible, invisible vNAS

                if (!isVNASActive(virtualNAS)) {
                    _log.debug("Removing vNAS {} as it is inactive", virtualNAS.getId());
                    iterator.remove();
                    invalidNasServers.add(virtualNAS);
                    
                } else if (!virtualNAS.getAssignedVirtualArrays().contains(
                        varrayUri.toString())) {
                    _log.debug("Removing vNAS {} as it is not part of varray: {}",
                            virtualNAS.getId(), varrayUri.toString());
                    iterator.remove();
                    invalidNasServers.add(virtualNAS);
                } else if (MetricsKeys.getBoolean(MetricsKeys.overLoaded,
                        virtualNAS.getMetrics())) {
                    _log.debug("Removing vNAS {} as it is overloaded",
                            virtualNAS.getId());
                    iterator.remove();
                    invalidNasServers.add(virtualNAS);
                } else if (!virtualNAS.getProtocols().containsAll(
                        vpool.getProtocols())) {
                    _log.debug("Removing vNAS {} as it does not support vpool protocols: {}",
                            virtualNAS.getId(), vpool.getProtocols());
                    iterator.remove();
                    invalidNasServers.add(virtualNAS);
                }
            }

        }
        if (vNASList != null) {
            _log.info(
                    "Got {} assigned VNAS servers for project {}",
                    vNASList.size(), project);
        }
        return vNASList;

    }

    /**
     * Validate whether VNAS is active or not
     * 
     * @param virtualNAS
     * @return true if VNAS is active else false
     * 
     */
    private boolean isVNASActive(VirtualNAS virtualNAS) {

        if (virtualNAS.getInactive()
                || virtualNAS.getAssignedVirtualArrays() == null
                || virtualNAS.getAssignedVirtualArrays().isEmpty()
                || !RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                        virtualNAS.getRegistrationStatus())
                || !DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()
                        .equals(virtualNAS.getCompatibilityStatus())
                || !VirtualNasState.LOADED.name().equals(virtualNAS.getVNasState())
                || !DiscoveryStatus.VISIBLE.name().equals(
                        virtualNAS.getDiscoveryStatus())) {
            return false;
        }
        return true;
    }

    /**
     * Fetches and returns all the storage ports for a given storage system that
     * are in a given varray
     * 
     * @param storageSystemUri
     *            the storage system URI
     * @param varray
     *            the varray URI
     * @return a list of all the storage ports for a given storage system
     */
    private List<StoragePort> getStorageSystemPortsInVarray(
            URI storageSystemUri, URI varray) {
        List<URI> allPorts = _dbClient
                .queryByConstraint(ContainmentConstraint.Factory
                        .getStorageDeviceStoragePortConstraint(storageSystemUri));
        List<StoragePort> ports = _dbClient.queryObject(StoragePort.class,
                allPorts);
        Iterator<StoragePort> itr = ports.iterator();
        StoragePort temp = null;
        while (itr.hasNext()) {
            temp = itr.next();
            if (temp.getInactive()
                    || temp.getTaggedVirtualArrays() == null
                    || !temp.getTaggedVirtualArrays().contains(
                            varray.toString())
                    || !RegistrationStatus.REGISTERED.toString()
                            .equalsIgnoreCase(temp.getRegistrationStatus())
                    || (StoragePort.OperationalStatus.valueOf(temp
                            .getOperationalStatus()))
                            .equals(StoragePort.OperationalStatus.NOT_OK)
                    || !DiscoveredDataObject.CompatibilityStatus.COMPATIBLE
                            .name().equals(temp.getCompatibilityStatus())
                    || !DiscoveryStatus.VISIBLE.name().equals(
                            temp.getDiscoveryStatus())) {
                itr.remove();
            }
        }
        return ports;
    }

    /**
     * Removes storage ports that do not support the specified file sharing
     * protocol.
     * 
     * @param protocol
     *            the required protocol that the port must support.
     * @param ports
     *            the list of available ports.
     */
    private void getPortsWithFileSharingProtocol(String protocol,
            List<StoragePort> ports) {

        if (null == protocol || null == ports || ports.isEmpty()) {
            return;
        }

        _log.debug("Validate protocol: {}", protocol);
        if (!StorageProtocol.File.NFS.name().equalsIgnoreCase(protocol)
                && !StorageProtocol.File.CIFS.name().equalsIgnoreCase(protocol)) {
            _log.warn("Not a valid file sharing protocol: {}", protocol);
            return;
        }

        StoragePort tempPort = null;
        StorageHADomain haDomain = null;
        Iterator<StoragePort> itr = ports.iterator();
        while (itr.hasNext()) {
            tempPort = itr.next();

            haDomain = null;
            URI domainUri = tempPort.getStorageHADomain();
            if (null != domainUri) {
                haDomain = _dbClient.queryObject(StorageHADomain.class,
                        domainUri);
            }

            if (null != haDomain) {
                StringSet supportedProtocols = haDomain
                        .getFileSharingProtocols();
                if (supportedProtocols == null
                        || !supportedProtocols.contains(protocol)) {
                    itr.remove();
                    _log.debug("Removing port {}", tempPort.getPortName());
                }
            }

            _log.debug("Number ports remainng: {}", ports.size());
        }
    }

    private List<URI> getvNasStoragePortUris(List<VirtualNAS> invalidNasServers) {

    	List<URI> spUriList = new ArrayList<URI>();

    	for ( VirtualNAS vNas : invalidNasServers ) {

    		StringSet spIdSet = vNas.getStoragePorts();
    		if (spIdSet != null && !spIdSet.isEmpty()) {
    			for (String id : spIdSet) {
    				spUriList.add(URI.create(id));
    			}
    		}
    	}	
    	return spUriList;
    }
    	
    
    
    /**
     * Select the right StorageHADomain matching vpool protocols.
     * 
     * @param vpool
     * @param vArray
     * @param poolRecommends
     *            recommendations after selecting matching storage pools.
     * @return list of FileRecommendation
     */
    private List<FileRecommendation> selectStorageHADomainMatchingVpool(
            VirtualPool vpool, URI vArray, List<Recommendation> poolRecommends,
            List<VirtualNAS> invalidNasServers) {
    	
    	// Get the storage ports from invalid vnas servers!!!
    	List<URI> invalidPorts = getvNasStoragePortUris(invalidNasServers);

        _log.debug("select matching StorageHADomain");
        List<FileRecommendation> result = new ArrayList<FileRecommendation>();
        for (Recommendation recommendation : poolRecommends) {
            FileRecommendation rec = new FileRecommendation(recommendation);
            URI storageUri = recommendation.getSourceStorageSystem();

            StorageSystem storage = _dbClient.queryObject(StorageSystem.class,
                    storageUri);
            // Same check for VNXe will be done here.
            // TODO: normalize behavior across file arrays so that this check is
            // not required.
            // TODO: Implement fake storageHADomain for DD to fit the viPR model
            if (!storage.getSystemType().equals(Type.netapp.toString())
                    && !storage.getSystemType().equals(Type.netappc.toString())
                    && !storage.getSystemType().equals(Type.vnxe.toString())
                    && !storage.getSystemType().equals(Type.vnxfile.toString())
                    && !storage.getSystemType().equals(
                            Type.datadomain.toString())) {
                result.add(rec);
                continue;
            }

            List<StoragePort> portList = getStorageSystemPortsInVarray(
                    storageUri, vArray);
            if (portList == null || portList.isEmpty()) {
                _log.info("No valid storage port found from the virtual array: "
                        + vArray);
                continue;
            }

            List<URI> storagePorts = new ArrayList<URI>();
            boolean foundValidPort = false;
            for (StoragePort port : portList) {
            	if ( invalidPorts.contains(port.getId()) ) {
            		_log.debug("Storage port {} belongs to invalid vNas server ",
                            port.getIpAddress());
            		continue;
            	}
                
            	foundValidPort = true;
                _log.debug("Looking for port {}", port.getLabel());
                URI haDomainUri = port.getStorageHADomain();
                // Data Domain does not have a filer entity.
                if ((haDomainUri == null)
                        && (!storage.getSystemType().equals(
                                Type.datadomain.toString()))) {
                    _log.info("No StorageHADomain URI for port {}",
                            port.getLabel());
                    continue;
                }

                StorageHADomain haDomain = null;
                if (haDomainUri != null) {
                    haDomain = _dbClient.queryObject(StorageHADomain.class,
                            haDomainUri);
                }
                if (haDomain != null) {
                    StringSet protocols = haDomain.getFileSharingProtocols();
                    // to see if it matches virtualPool's protocols
                    StringSet vpoolProtocols = vpool.getProtocols();
                    if (protocols != null
                            && protocols.containsAll(vpoolProtocols)) {
                        _log.info(
                                "Found the StorageHADomain {} for recommended storagepool: {}",
                                haDomain.getName(),
                                recommendation.getSourceStoragePool());
                        storagePorts.add(port.getId());
                    }
                } else if (storage.getSystemType().equals(
                        Type.datadomain.toString())) {
                    // The same file system on DD can support NFS and CIFS
                    storagePorts.add(port.getId());
                } else {
                    _log.error("No StorageHADomain for port {}",
                            port.getIpAddress());
                }
            }

            // select storage port randomly from all candidate ports (to
            // minimize collisions).
            if ( foundValidPort ) {
            	Collections.shuffle(storagePorts);
            	rec.setStoragePorts(storagePorts);
            	result.add(rec);
            } else {
            	_log.info("No valid storage port found from the storage system : "
            			+ storageUri
            			+ ", All ports belongs to invalid vNas "
                        );
            }
        }
        return result;
    }

}
