/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshot.TechnologyType;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.vplex.api.VPlexApiException;

public class VPlexUtil {
	private static Logger _log = LoggerFactory.getLogger(VPlexUtil.class);

	private static final String VPLEX = "vplex";
	
	/**
     * Convenience method to get HA MirrorVpool if it's set.
     * 
     * @param sourceVirtualPool A Reference to VPLEX Volume source virtual pool
     * @param associatedVolumeIds Set of associated backend volumes for a VPLEX volume
     * @param dbClient an instance of {@link DbClient}
     * 
     * @return returns HA mirror vpool if its set for the HA Vpool else returns null
     */
    public static VirtualPool getHAMirrorVpool(VirtualPool sourceVirtualPool, StringSet associatedVolumeIds, DbClient dbClient){
    	VirtualPool haMirrorVpool = null;
    	StringMap haVarrayVpoolMap = sourceVirtualPool.getHaVarrayVpoolMap();
        if(associatedVolumeIds .size() > 1 && haVarrayVpoolMap != null 
        		&& !haVarrayVpoolMap.isEmpty()){
        	String haVarray = haVarrayVpoolMap.keySet().iterator().next();
        	String haVpoolStr = haVarrayVpoolMap.get(haVarray);
        	if(haVpoolStr != null && !(haVpoolStr.equals(NullColumnValueGetter.getNullURI().toString()))){
        		VirtualPool haVpool = dbClient.queryObject(VirtualPool.class, URI.create(haVpoolStr));
        		if(haVpool.getMirrorVirtualPool() != null){
        			haMirrorVpool = dbClient.queryObject(VirtualPool.class, URI.create(haVpool.getMirrorVirtualPool()));
        		}
        	}
        }
        return haMirrorVpool;
    }
    
    /**
     * Convenience method to get HA Varray URI if it's set.
     * 
     * @param sourceVirtualPool A Reference to VPLEX Volume source virtual pool
     * @param dbClient an instance of {@link DbClient}
     * 
     * @return returns HA varray URI if its set for the HA varray else returns null
     */
    public static URI getHAVarray(VirtualPool sourceVirtualPool){
    	URI haVarrayURI = null;
    	StringMap haVarrayVpoolMap = sourceVirtualPool.getHaVarrayVpoolMap();
        if(haVarrayVpoolMap != null && !haVarrayVpoolMap.isEmpty()){
        	String haVarrayStr = haVarrayVpoolMap.keySet().iterator().next();
        	haVarrayURI = URI.create(haVarrayStr);
        }
        return haVarrayURI;
    }
    
    /**
     * Returns the source or ha backend volume of the passed VPLEX volume. 
     *  
     * @param vplexVolume A reference to the VPLEX volume.
     * @param sourceVolume A boolean thats used to return either source
     *        or ha backend volume.
     * @param dbClient an instance of {@link DbClient}
     *          
     * @return A reference to the backend volume 
     *         If sourceVolume is true returns source backend 
     *         volume else returns ha backend volume.
     * 
     */
    public static Volume getVPLEXBackendVolume(Volume vplexVolume, boolean sourceVolume, DbClient dbClient) {
        return getVPLEXBackendVolume(vplexVolume, sourceVolume, dbClient, true);
    }
        
    /**
     * Returns the source or ha backend volume of the passed VPLEX volume. 
     *  
     * @param vplexVolume A reference to the VPLEX volume.
     * @param sourceVolume A boolean thats used to return either source
     *        or ha backend volume.
     * @param dbClient an instance of {@link DbClient}
     * @param errorIfNotFound A boolean thats used to either return null or throw error
     *          
     * @return A reference to the backend volume 
     *         If sourceVolume is true returns source backend 
     *         volume else returns ha backend volume.
     * 
     */
    public static Volume getVPLEXBackendVolume(Volume vplexVolume, boolean sourceVolume, DbClient dbClient, boolean errorIfNotFound) {
        String vplexVolumeId = vplexVolume.getId().toString();
        StringSet associatedVolumeIds = vplexVolume.getAssociatedVolumes();
        Volume backendVolume = null;
        if (associatedVolumeIds == null) {
            if (errorIfNotFound) {
                throw InternalServerErrorException.internalServerErrors
                .noAssociatedVolumesForVPLEXVolume(vplexVolumeId);
            } else {
                return backendVolume;
            }
        } 

        // Get the backend volume either source or ha.
        for (String associatedVolumeId : associatedVolumeIds) {
            Volume associatedVolume = dbClient.queryObject(Volume.class,
                URI.create(associatedVolumeId));
            if (associatedVolume != null) {
                if (sourceVolume && associatedVolume.getVirtualArray().equals(vplexVolume.getVirtualArray())) {
                    backendVolume = associatedVolume;
                    break;
                }
                if (!sourceVolume && !(associatedVolume.getVirtualArray().equals(vplexVolume.getVirtualArray()))) {
                    backendVolume = associatedVolume;
                    break;
                }
            }
        }
        
        return backendVolume;
    }
    
    /**
     * This method returns true if the mentioned varray(vararyId) has ports from the mentioned 
     * VPLEX storage system(vplexStorageSystemURI) from the mentioned VPLEX cluster(cluster)
     * 
     * @param vararyId The ID of the varray
     * @param cluster  The vplex cluster value (1 or 2)
     * @param vplexStorageSystemURI The URI of the vplex storage system
     * @param dbClient an instance of {@link DbClient}
     * 
     * @return true or false
     */
    public static boolean checkIfVarrayContainsSpecifiedVplexSystem(String vararyId, String cluster, URI vplexStorageSystemURI, DbClient dbClient) {
        boolean foundVplexOnSpecifiedCluster = false;
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVirtualArrayStoragePortsConstraint(vararyId), storagePortURIs);
        for (URI uri :  storagePortURIs){
            StoragePort storagePort = dbClient.queryObject(StoragePort.class, uri);
            if ((storagePort != null)
            && DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()
                    .equals(storagePort.getCompatibilityStatus())
                && (RegistrationStatus.REGISTERED.toString().equals(storagePort
                    .getRegistrationStatus()))
                && (!DiscoveryStatus.NOTVISIBLE.toString().equals(storagePort
                            .getDiscoveryStatus()))) {
                if(storagePort.getStorageDevice().equals(vplexStorageSystemURI)){
                    String vplexCluster = ConnectivityUtil.getVplexClusterOfPort(storagePort);
                    if(vplexCluster.equals(cluster)){
                        foundVplexOnSpecifiedCluster = true;
                        break;
                    }
                }
            }
        }
       return foundVplexOnSpecifiedCluster;
    }
        
    /**
     * This method validates if the count requested by user to create
     * mirror(s) for a volume is valid.
     * 
     * @param sourceVolume The reference to volume for which mirrors needs to be created
     * @param sourceVPool The reference to virtual pool to which volume is is associated
     * @param count The number of mirrors requested to be created
     * @param currentMirrorCount The current count of the mirror associated with the sourceVolume
     * @param requestedMirrorCount Represent currentMirrorCount + count
     * @param dbClient dbClient an instance of {@link DbClient}
     */
    public static void validateMirrorCountForVplexDistVolume(Volume sourceVolume, VirtualPool sourceVPool, int count, int currentMirrorCount, int requestedMirrorCount, DbClient dbClient){
    	int sourceVpoolMaxCC = sourceVPool.getMaxNativeContinuousCopies() != null ? sourceVPool.getMaxNativeContinuousCopies() : 0 ;
		VirtualPool haVpool = VirtualPool.getHAVPool(sourceVPool, dbClient);
		int haVpoolMaxCC = 0;
		if(haVpool != null){
			haVpoolMaxCC = haVpool.getMaxNativeContinuousCopies();
		}
		
		if((currentMirrorCount > 0 && (sourceVpoolMaxCC + haVpoolMaxCC) < requestedMirrorCount) || 
		        (sourceVpoolMaxCC > 0  && sourceVpoolMaxCC < count) || 
		        (haVpoolMaxCC > 0 && haVpoolMaxCC < count)){
		    if(sourceVpoolMaxCC > 0 && haVpoolMaxCC > 0){
		        Integer currentSourceMirrorCount = getSourceOrHAContinuousCopyCount(sourceVolume, sourceVPool, dbClient);
		        Integer currentHAMirrorCount = getSourceOrHAContinuousCopyCount(sourceVolume, haVpool, dbClient);
		        throw APIException.badRequests.invalidParameterBlockMaximumCopiesForVolumeExceededForSourceAndHA(sourceVpoolMaxCC, haVpoolMaxCC, sourceVolume.getLabel(), sourceVPool.getLabel(), haVpool.getLabel(),
		                currentSourceMirrorCount, currentHAMirrorCount);
		    } else if (sourceVpoolMaxCC > 0 && haVpoolMaxCC == 0){
		        Integer currentSourceMirrorCount = getSourceOrHAContinuousCopyCount(sourceVolume, sourceVPool, dbClient);
		        throw APIException.badRequests.invalidParameterBlockMaximumCopiesForVolumeExceededForSource(sourceVpoolMaxCC, sourceVolume.getLabel(), sourceVPool.getLabel(), currentSourceMirrorCount);
		    } else if (sourceVpoolMaxCC == 0 && haVpoolMaxCC > 0){
		        Integer currentHAMirrorCount = getSourceOrHAContinuousCopyCount(sourceVolume, haVpool, dbClient);
		        throw APIException.badRequests.invalidParameterBlockMaximumCopiesForVolumeExceededForHA(haVpoolMaxCC, sourceVolume.getLabel(), haVpool.getLabel(), currentHAMirrorCount);
		    }
		}
    }
    
    /**
     * Returns Mirror count on the source side or Ha side of the VPlex distributed volume
     * 
     * @param vplexVolume - The reference to VPLEX Distributed volume
     * @param vPool - The reference to source or HA Virtual Pool
     */
    private static Integer getSourceOrHAContinuousCopyCount(Volume vplexVolume, VirtualPool vPool, DbClient dbClient){
        int count = 0;
        if(vplexVolume.getMirrors() != null){
            List<VplexMirror> mirrors = dbClient.queryObject(VplexMirror.class, StringSetUtil.stringSetToUriList(vplexVolume.getMirrors()));
            for (VplexMirror mirror : mirrors) {
                if (!mirror.getInactive() && vPool.getMirrorVirtualPool() != null) {
                   if(mirror.getVirtualPool().equals(URI.create(vPool.getMirrorVirtualPool()))){
                       count = count+1;
                   }
                }
            }
        }
        return count;
    }
      
    /**
     * Map a set of Block Objects to their Varray(s). Includes the SRC varray and HA varray for
     * distributed volumes.
     * * @param dbClient -- for database access
     * @param blockObjectURIs -- the collection  of BlockObjects (e.g. volumes) being exported
     * @param storageURI -- the URI of the Storage System
     * @param exportGroup -- the ExportGroup
     * @return Map of varray URI to set of blockObject URIs
     */
    static public Map<URI, Set<URI>> mapBlockObjectsToVarrays(DbClient dbClient, 
            Collection<URI> blockObjectURIs, URI storageURI, ExportGroup exportGroup) {
        URI exportGroupVarray = exportGroup.getVirtualArray();
        Map<URI, Set<URI>> varrayToBlockObjects = new HashMap<URI, Set<URI>>();
        for (URI blockObjectURI : blockObjectURIs) {
            BlockObject blockObject = BlockObject.fetch(dbClient, blockObjectURI);
            if (blockObject != null) {
                Volume volume = null;
                if (blockObject instanceof BlockSnapshot) {
                    BlockSnapshot snapshot = (BlockSnapshot) blockObject;
                	// Set the volume to be the BlockSnapshot parent volume.
                	volume = dbClient.queryObject(Volume.class, snapshot.getParent());
                	
                    // For all RP BlockSnapshosts, we must use the BlockSnapshot parent, source RP, 
                	// volume to locate the target volume that matches the BlockSnapshot virtual 
                	// array and storage system.
                    if (volume != null && snapshot.getTechnologyType().equalsIgnoreCase(TechnologyType.RP.name())) {
                    	// Get the BlockSnapshot parent volume's associated RP target volumes.
                    	StringSet rpTargets = volume.getRpTargets();
                    	
                    	// Find the target volume whose virtual array and storage system match those
                    	// of the BlockSnapshot.
                    	if (rpTargets != null) {
                    		for (String rpTarget : rpTargets) {
                    			Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(rpTarget));
                    			if (targetVolume.getVirtualArray().equals(snapshot.getVirtualArray()) &&
                    					targetVolume.getStorageController().equals(snapshot.getStorageController())) {
                    				volume = targetVolume;
                    				break;
                    			}
                    		}
                    	}
                    }
                } else if (blockObject instanceof Volume) {
                    volume = (Volume) blockObject;
                }
                if (volume != null && volume.getStorageController().equals(storageURI)) {
                    // The volume's varray counts if either the Vpool autoCrossConnectExport is set,
                    // or if the volume varray matches the ExportGroup varray.
                    URI varray = volume.getVirtualArray();
                    VirtualPool vpool = dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
                    if (varray.equals(exportGroupVarray) || vpool.getAutoCrossConnectExport()) {
                        if (! varrayToBlockObjects.containsKey(varray)) {
                            varrayToBlockObjects.put(varray, new HashSet<URI>());
                        }
                        varrayToBlockObjects.get(varray).add(blockObjectURI);
                    }
                    // Look at the Virtual pool to determine if distributed.
                    // Also make sure it has more than one associated volumes (indicating it is distributed).
                    if (volume.getAssociatedVolumes() != null && volume.getAssociatedVolumes().size() > 1) { 
                        if (NullColumnValueGetter.isNotNullValue(vpool.getHighAvailability())) {
                            if (vpool.getHighAvailability().equals(VirtualPool.HighAvailabilityType.vplex_distributed.name())) {
                                if (vpool.getHaVarrayVpoolMap() != null) {
                                    for (String varrayId : vpool.getHaVarrayVpoolMap().keySet()) {
                                        // The HA varray counts if it matches the ExportGroup varray, or
                                        // if the Vpool autoCrossConnectExport flag is set.
                                        URI varrayURI = URI.create(varrayId);
                                        if (varrayURI.equals(exportGroupVarray) || vpool.getAutoCrossConnectExport()) {
                                            if (!varrayToBlockObjects.containsKey(varrayURI)) {
                                                varrayToBlockObjects.put(varrayURI, new HashSet<URI>());
                                            }
                                            varrayToBlockObjects.get(varrayURI).add(blockObjectURI);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        _log.info("VPLEX: mapBlockObjectsToVarrays varrayToBlockObjects: " + varrayToBlockObjects.toString());
        return varrayToBlockObjects;
    }
    
    /**
     * Pick the HA varray to use for the export. For now only one choice is allowed, and returned.
     * @param haVarrayToVolumesMap -- Map of Varray URI to Set of Volume URIs returned by
     *    getHAVarraysForVolumes
     * @return URI of varray to use for HA export
     */
    static public URI pickHAVarray(Map<URI,Set<URI>> haVarrayToVolumesMap) {
        // For now, pick the one with the highest number of volumes
        if (haVarrayToVolumesMap.size() > 1) {
            _log.error("More than one HA Varray in export: " + haVarrayToVolumesMap.keySet().toString());
            throw VPlexApiException.exceptions.moreThanOneHAVarrayInExport(haVarrayToVolumesMap.keySet().toString());
        }
        if (!haVarrayToVolumesMap.keySet().isEmpty()) {
            return haVarrayToVolumesMap.keySet().iterator().next();
        }
        // It's not an error if we cannot do the HA export, so just return null
        return null;
    }
    
    /**
     * Makes a map of varray to Initiator URIs showing which intiators can access the given varrays.
     * @param dbClient -- DbClient
     * @param blockScheduler -- BlockStorageScheduler
     * @param initiatorURIs -- A list of Initiator URIs
     * @param varrayURIs -- A list of potential varray URIs
     * @param storage -- StorageSystem of the Vplex
     * @return Map of Varray URI to List of Initiator URIs that can be mapped through the varray to the vplex
     */
    public static Map<URI, List<URI>> partitionInitiatorsByVarray(DbClient dbClient, 
            BlockStorageScheduler blockScheduler, List<URI> initiatorURIs, List<URI> varrayURIs, 
            StorageSystem storage) {
        Map<URI, List<URI>> varrayToInitiators = new HashMap<URI, List<URI>>();
        // Read the initiators and partition them by Network
        List<Initiator> initiators = dbClient.queryObject(Initiator.class, initiatorURIs);
        Map<NetworkLite, List<Initiator>> networkToInitiators = NetworkUtil.getInitiatorsByNetwork(initiators, dbClient);
        // Build the output map. For each varray, look at each Network to see if it's connected virtual arrays
        // contains this varray. If so, add all the Initiators in that Network to the varrayToInitiators map.
        for (URI varrayURI : varrayURIs) {
            for (NetworkLite network : networkToInitiators.keySet()) {
                if (network.getConnectedVirtualArrays().contains(varrayURI.toString())) {
                    if (! varrayToInitiators.keySet().contains(varrayURI)) {
                        varrayToInitiators.put(varrayURI, new ArrayList<URI>());
                    }
                    for (Initiator initiator : networkToInitiators.get(network)) {
                        varrayToInitiators.get(varrayURI).add(initiator.getId());
                    }
                }
            }
        }
        return varrayToInitiators;
    }
    
    /**
     * Validate that an export operation connected enough hosts.
     * Where distributed volumes are present, each host must be connected (to at least one varray).
     * When only volumes in the src or ha varray are present, at least one host must be connected.
     * @param dbClient - DbClient
     * @param srcVarray - the srcVarray iff source side volumes are present
     * @param haVarray -- the haVarray iff ha side volumes are present
     * @param initiatorURIs -- a list of Initiator URIs
     * @param varrayToInitiators --varrayToInitiators URI map previously computed
     * @throws exportCreateNoHostsConnected, exportCreateAllHostsNotConnected
     */
    public static void validateVPlexClusterExport(DbClient dbClient, URI srcVarray, URI haVarray,
    		List<URI> initiatorURIs, Map<URI, List<URI>> varrayToInitiators) {
    	if (srcVarray == null && haVarray == null) return;
    	Set<String> unconnectedHostNames = new HashSet<String>();
    	
    	// Retrieve all the Initiators
    	List<Initiator> initiators = dbClient.queryObject(Initiator.class, initiatorURIs);
    	
    	// a Map of Host URI to a List of Initiator objects, then get Initiator list for src, ha
    	Map<URI, List<Initiator>> hostToInitiators = BlockStorageScheduler.getInitiatorsByHostMap(
    													initiators);
    	List<URI> srcVarrayInitiators = new ArrayList<URI>();
    	if (srcVarray != null && varrayToInitiators.get(srcVarray) != null) { 
    		srcVarrayInitiators = varrayToInitiators.get(srcVarray); 
    	}
    	List<URI> haVarrayInitiators = new ArrayList<URI>();
    	if (haVarray != null && varrayToInitiators.get(haVarray) != null) { 
    		haVarrayInitiators = varrayToInitiators.get(haVarray); 
    	}
    	
    	// Cycle through the hosts, determining which have connectivity.
    	int connectedHostCount = 0;
    	for (List<Initiator> hostInitiators : hostToInitiators.values()) {
    		boolean connected = false;
			String hostName = "unknown-host";
			for (Initiator initiator : hostInitiators) {
				hostName = initiator.getHostName();
				if (srcVarrayInitiators.contains(initiator.getId())
						|| haVarrayInitiators.contains(initiator.getId())) {
					connected = true;
					break;
				}
			}
			if (!connected) { 
				unconnectedHostNames.add(hostName); 
			} else { 
				connectedHostCount++; 
			}
    	}
    	
    	// log a message indicating unconnected hosts
    	String whichVarray = (srcVarray == null ? "high availability" :
    		(haVarray == null ? "source" : "source or high availability"));
    	if (unconnectedHostNames.size() > 0) {
    		_log.info(String.format("The following initiators are not connected to the %s varrays: %s", 
    			whichVarray, unconnectedHostNames.toString()));
    	}
    	
    	// If both varrays are present and there are any unconnected hosts, fail.
    	if (srcVarray != null && haVarray != null) {
    		if (unconnectedHostNames.size() > 0) {
    			throw VPlexApiException.exceptions.exportCreateAllHostsNotConnected(
    					unconnectedHostNames.toString());
    		}
    	} else if (connectedHostCount == 0) {	
    		// here we only have one varray or the other. Fail if there are no connected hosts.
    		throw VPlexApiException.exceptions.exportCreateNoHostsConnected(
						whichVarray, unconnectedHostNames.toString());
    	}
    }
    
    /**
     * Given an ExportGroup, a hostURI, a VPlex storage system, and a Varray,
     * finds the ExportMask in the ExportGroup (if any)
     * corresponding to that host and varray on the specified vplex.
     * @param dbClient -- Database client
     * @param exportGroup -- ExportGroup object
     * @param hostURI -- URI of host
     * @param vplexURI -- URI of VPLEX StorageSystem
     * @param varrayURI -- Varray we want the Export Mask in
     * @return ExportMask or null if not found
     */
    public static ExportMask getExportMaskForHostInVarray(DbClient dbClient, 
            ExportGroup exportGroup, URI hostURI, URI vplexURI, URI varrayURI) {
        StringSet maskIds = exportGroup.getExportMasks();
        if (maskIds == null) return null;
        List<ExportMask> exportMasks =
                ExportMaskUtils.getExportMasks(dbClient, exportGroup, vplexURI);
        for (ExportMask exportMask : exportMasks) {
            if (getExportMaskHost(dbClient, exportMask).equals(hostURI) 
                    && ExportMaskUtils.exportMaskInVarray(dbClient, exportMask, varrayURI)) {
                return exportMask;
            }
        }
        return null;
    }
    
    /**
     * Returns the Host URI corresponding to a given VPLEX export mask.
     * This is determined by:
     * 1. If any Initiator has a Host URI, return that (from the first Initiator).
     * 2. Otherwise, if any Initiator has a hostName, return URI(hostName) (first one).
     * 3. Otherwise, return NULL URI.
     * @param exportMask
     * @return URI of host, or Null URI if host undeterminable
     */
    public static URI getExportMaskHost(DbClient dbClient, ExportMask exportMask) {
        if (exportMask.getInitiators() == null || exportMask.getInitiators().isEmpty()) {
            return NullColumnValueGetter.getNullURI();
        }
        Iterator<String> initiatorIter = exportMask.getInitiators().iterator();
        String hostName = null;
        while (initiatorIter.hasNext()) {
            URI  initiatorForHostId = URI.create(initiatorIter.next());
            Initiator initiatorForHost = dbClient.queryObject(Initiator.class, initiatorForHostId);
            if (initiatorForHost == null) { continue; }
            if (NullColumnValueGetter.isNullURI(initiatorForHost.getHost())) {
                // No Host URI
                if (initiatorForHost.getHostName() != null) {
                    // Save the name
                    if (hostName == null) { 
                        hostName = initiatorForHost.getHostName(); 
                        _log.info(String.format("Initiator %s has no Host URI, hostName %s", 
                                initiatorForHost.getInitiatorPort(), hostName));
                    }
                }
            } else {
                // Non-null Host URI, return the first one found
                _log.info(String.format("ExportMask %s (%s) -> Host %s", 
                        exportMask.getMaskName(), exportMask.getId(), initiatorForHost.getHost()));
                return initiatorForHost.getHost();
            }
        }
        // If there was no Initiator with a host URI, then return a hostName as a URI.
        // If there were no hostNames, return null URI.
        _log.info(String.format("ExportMask %s (%s) -> Host %s", 
                exportMask.getMaskName(), exportMask.getId(), (hostName != null ? hostName : "null")));
        return (hostName != null ? URI.create(hostName.replaceAll("\\s","")) : NullColumnValueGetter.getNullURI());
    }
    
    /**
     * Returns the Host URI for an Initiator. 
     * 1. If Initiator has a valid host URI, returns that.
     * 2. Otherwise, returns URI(hostName) or NULL URI.
     * @param initiator - Initiator
     * @return URI of Host
     */
    public static URI getInitiatorHost(Initiator initiator) {
        if (NullColumnValueGetter.isNullURI(initiator.getHost())) {
            if (initiator.getHostName() != null) {
                _log.info(String.format("Initiator %s -> Host %s", 
                        initiator.getInitiatorPort(), initiator.getHostName()));
                return URI.create(initiator.getHostName().replaceAll("\\s",""));
            } else {
                return NullColumnValueGetter.getNullURI();
            }
        } else {
            _log.info(String.format("Initiator %s -> Host %s", 
                    initiator.getInitiatorPort(), initiator.getHost()));
            return initiator.getHost();
        }
    }
    
    /**
     * Filter a list of initiators to contain only those with protocols 
     * supported by the VPLEX.
     * 
     * @param dbClient a database client instance
     * @param initiators list of initiators
     * 
     * @return a filtered list of initiators containing 
     *         only those with protocols supported by VPLEX
     */
    public static List<URI> filterInitiatorsForVplex(DbClient dbClient, List<URI> initiators) {
        
        // filter initiators for FC protocol type only (CTRL-6326)
        List<URI> initsToRemove = new ArrayList<URI>();
        for (URI init : initiators) {
            Initiator initiator = dbClient.queryObject(Initiator.class, init);
            if ((null != initiator) && !HostInterface.Protocol.FC.toString().equals(initiator.getProtocol())) {
                initsToRemove.add(init);
            }
        }
        initiators.removeAll(initsToRemove);
        
        return initiators;
    }
    
    /**
     * Lookup all the BlockObjects from their URI that is passed in.
     * If this is an RP BlockSnapshot, return the paraent VirtualVolume.
     * @param dbClient -- DbClient
     * @param volumeURIList -- List of volume URIs
     * @return A map of URI to BlockObject, which would be the correctly translated 
     * from BlockSnapshot to Volume. 
     */
    public static Map <URI, BlockObject> translateRPSnapshots (DbClient dbClient, List<URI> volumeURIList) {
    	Map<URI, BlockObject> blockObjectCache = new HashMap<URI, BlockObject>();
        // Determine the virtual volume names.
        for (URI boURI : volumeURIList) {
            BlockObject blockObject = Volume.fetchExportMaskBlockObject(dbClient, boURI);
            blockObjectCache.put(blockObject.getId(), blockObject);
        }
        return blockObjectCache;
    }
    
    /**
     * Returns the assembly id (i.e., serial number) for the passed cluster on
     * the passed VPLEX system.
     * 
     * @param clusterId The cluster Id.
     * @param vplexSystem A reference to the VPLEX system
     * 
     * @return The serial number for the passed cluster.
     */
    public static String getVPlexClusterSerialNumber(String clusterId, StorageSystem vplexSystem) {
        String clusterSerialNo = null;
        
        StringMap assemblyIdMap = vplexSystem.getVplexAssemblyIdtoClusterId();
        if ((assemblyIdMap == null) || (assemblyIdMap.isEmpty())) {
            _log.warn("Assembly id map not set for storage system {}", vplexSystem.getId());
        }
        
        for (String assemblyId : assemblyIdMap.keySet()) {
            String clusterIdForAssemblyId = assemblyIdMap.get(assemblyId);
            if (clusterId.equals(clusterIdForAssemblyId)) {
                // The cluster assembly id is the cluster serial number.
                clusterSerialNo = assemblyId;
                break;
            }
        }
        
        // If for some reason we could not determine the serial 
        // number from the assemblyId map, then just use the
        // VPLEX system serial number, which is a combination
        // of the serial numbers from both clusters.
        //
        // Note that we could parse the cluster serial number from
        // the system serial number if we presume that the order
        // will always be cluster1:cluster2. However, we don't
        // really expect that we will wind up having to take this
        // code path. The only known window is for systems discovered
        // prior to 2.2 and after upgrade to 2.2, the user does
        // provisioning, resulting in storage view and zone creation,
        // prior to the system being rediscovered. Since discovery
        // occurs after upgrade, this is highly unlikely, and the net
        // result would only be that the storage view name and/or zone 
        // name would have a component that reflects the system
        // serial number.
        if (clusterSerialNo == null) {
            _log.warn("Could not determine assembly id for cluster {} for VPLEX {}",
                clusterId, vplexSystem.getId());
            clusterSerialNo = vplexSystem.getSerialNumber();
        }
        
        return clusterSerialNo;
    }
    
    /**
     * Returns a set of all VPLEX backend ports as their related 
     * Initiator URIs for a given VPLEX storage system.
     * 
     * @param vplexUri - URI of the VPLEX system to find initiators for
     * @param dbClient - database client instance
     * @return a Set of Initiator URIs
     */
    public static Set<URI> getBackendPortInitiators( URI vplexUri, DbClient dbClient ) {
        
        _log.info("finding backend port initiators for VPLEX: " + vplexUri);
        Set<URI> initiators = new HashSet<URI>();
        
        List<StoragePort> ports = ConnectivityUtil.getStoragePortsForSystem(dbClient, vplexUri);
        for (StoragePort port : ports) {
            if (StoragePort.PortType.backend.name().equals(port.getPortType())) {
                Initiator init = ExportUtils.getInitiator(port.getPortNetworkId(), dbClient);
                if (init != null) {
                    _log.info("found initiator {} for wwpn {}", init.getId(), port.getPortNetworkId());
                    initiators.add(init.getId());
                }
            }
        }
        
        return initiators;
    }
    
    /**
     * Returns a set of all VPLEX backend ports as their related 
     * Initiator URIs.
     * 
     * @param dbClient - database client instance
     * @return a Set of Initiator URIs
     */
    public static Set<URI> getBackendPortInitiators( DbClient dbClient ) {
        
        _log.info("finding backend port initiators for all VPLEX systems");
        Set<URI> initiators = new HashSet<URI>();
        
        List<URI> storageSystemUris = dbClient.queryByType(StorageSystem.class, true);
        List<StorageSystem> storageSystems = dbClient.queryObject(StorageSystem.class, storageSystemUris);
        for (StorageSystem storageSystem : storageSystems ) {
            if (StringUtils.equals(storageSystem.getSystemType(), VPLEX)) {
                initiators.addAll(getBackendPortInitiators(storageSystem.getId(), dbClient));
            }
        }
        
        return initiators;
    }
}
