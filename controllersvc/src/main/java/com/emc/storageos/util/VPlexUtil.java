/*
 * Copyright (c) 2015 EMC Corporation
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
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshot.TechnologyType;
import com.emc.storageos.db.client.model.DataObject.Flag;
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
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.joiner.Joiner;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.google.common.collect.Collections2;

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
    public static VirtualPool getHAMirrorVpool(VirtualPool sourceVirtualPool, StringSet associatedVolumeIds, DbClient dbClient) {
        VirtualPool haMirrorVpool = null;
        StringMap haVarrayVpoolMap = sourceVirtualPool.getHaVarrayVpoolMap();
        if (associatedVolumeIds.size() > 1 && haVarrayVpoolMap != null
                && !haVarrayVpoolMap.isEmpty()) {
            String haVarray = haVarrayVpoolMap.keySet().iterator().next();
            String haVpoolStr = haVarrayVpoolMap.get(haVarray);
            if (haVpoolStr != null && !(haVpoolStr.equals(NullColumnValueGetter.getNullURI().toString()))) {
                VirtualPool haVpool = dbClient.queryObject(VirtualPool.class, URI.create(haVpoolStr));
                if (haVpool.getMirrorVirtualPool() != null) {
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
    public static URI getHAVarray(VirtualPool sourceVirtualPool) {
        URI haVarrayURI = null;
        StringMap haVarrayVpoolMap = sourceVirtualPool.getHaVarrayVpoolMap();
        if (haVarrayVpoolMap != null && !haVarrayVpoolMap.isEmpty()) {
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
     *            or ha backend volume.
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
     *            or ha backend volume.
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
     * @param cluster The vplex cluster value (1 or 2)
     * @param vplexStorageSystemURI The URI of the vplex storage system
     * @param dbClient an instance of {@link DbClient}
     * 
     * @return true or false
     */
    public static boolean checkIfVarrayContainsSpecifiedVplexSystem(String vararyId, String cluster, URI vplexStorageSystemURI,
            DbClient dbClient) {
        boolean foundVplexOnSpecifiedCluster = false;
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVirtualArrayStoragePortsConstraint(vararyId), storagePortURIs);
        for (URI uri : storagePortURIs) {
            StoragePort storagePort = dbClient.queryObject(StoragePort.class, uri);
            if ((storagePort != null)
                    && DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()
                            .equals(storagePort.getCompatibilityStatus())
                    && (RegistrationStatus.REGISTERED.toString().equals(storagePort
                            .getRegistrationStatus()))
                    && (!DiscoveryStatus.NOTVISIBLE.toString().equals(storagePort
                            .getDiscoveryStatus()))) {
                if (storagePort.getStorageDevice().equals(vplexStorageSystemURI)) {
                    String vplexCluster = ConnectivityUtil.getVplexClusterOfPort(storagePort);
                    if (vplexCluster.equals(cluster)) {
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
    public static void validateMirrorCountForVplexDistVolume(Volume sourceVolume, VirtualPool sourceVPool, int count,
            int currentMirrorCount, int requestedMirrorCount, DbClient dbClient) {
        int sourceVpoolMaxCC = sourceVPool.getMaxNativeContinuousCopies() != null ? sourceVPool.getMaxNativeContinuousCopies() : 0;
        VirtualPool haVpool = VirtualPool.getHAVPool(sourceVPool, dbClient);
        int haVpoolMaxCC = 0;
        if (haVpool != null) {
            haVpoolMaxCC = haVpool.getMaxNativeContinuousCopies();
        }

        if ((currentMirrorCount > 0 && (sourceVpoolMaxCC + haVpoolMaxCC) < requestedMirrorCount) ||
                (sourceVpoolMaxCC > 0 && sourceVpoolMaxCC < count) ||
                (haVpoolMaxCC > 0 && haVpoolMaxCC < count)) {
            if (sourceVpoolMaxCC > 0 && haVpoolMaxCC > 0) {
                Integer currentSourceMirrorCount = getSourceOrHAContinuousCopyCount(sourceVolume, sourceVPool, dbClient);
                Integer currentHAMirrorCount = getSourceOrHAContinuousCopyCount(sourceVolume, haVpool, dbClient);
                throw APIException.badRequests.invalidParameterBlockMaximumCopiesForVolumeExceededForSourceAndHA(sourceVpoolMaxCC,
                        haVpoolMaxCC, sourceVolume.getLabel(), sourceVPool.getLabel(), haVpool.getLabel(),
                        currentSourceMirrorCount, currentHAMirrorCount);
            } else if (sourceVpoolMaxCC > 0 && haVpoolMaxCC == 0) {
                Integer currentSourceMirrorCount = getSourceOrHAContinuousCopyCount(sourceVolume, sourceVPool, dbClient);
                throw APIException.badRequests.invalidParameterBlockMaximumCopiesForVolumeExceededForSource(sourceVpoolMaxCC,
                        sourceVolume.getLabel(), sourceVPool.getLabel(), currentSourceMirrorCount);
            } else if (sourceVpoolMaxCC == 0 && haVpoolMaxCC > 0) {
                Integer currentHAMirrorCount = getSourceOrHAContinuousCopyCount(sourceVolume, haVpool, dbClient);
                throw APIException.badRequests.invalidParameterBlockMaximumCopiesForVolumeExceededForHA(haVpoolMaxCC,
                        sourceVolume.getLabel(), haVpool.getLabel(), currentHAMirrorCount);
            }
        }
    }

    /**
     * Returns Mirror count on the source side or Ha side of the VPlex distributed volume
     * 
     * @param vplexVolume - The reference to VPLEX Distributed volume
     * @param vPool - The reference to source or HA Virtual Pool
     */
    private static Integer getSourceOrHAContinuousCopyCount(Volume vplexVolume, VirtualPool vPool, DbClient dbClient) {
        int count = 0;
        if (vplexVolume.getMirrors() != null) {
            List<VplexMirror> mirrors = dbClient.queryObject(VplexMirror.class, StringSetUtil.stringSetToUriList(vplexVolume.getMirrors()));
            for (VplexMirror mirror : mirrors) {
                if (!mirror.getInactive() && vPool.getMirrorVirtualPool() != null) {
                    if (mirror.getVirtualPool().equals(URI.create(vPool.getMirrorVirtualPool()))) {
                        count = count + 1;
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
     * 
     * @param blockObjectURIs -- the collection of BlockObjects (e.g. volumes) being exported
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
                        if (!varrayToBlockObjects.containsKey(varray)) {
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
     * 
     * @param haVarrayToVolumesMap -- Map of Varray URI to Set of Volume URIs returned by
     *            getHAVarraysForVolumes
     * @return URI of varray to use for HA export
     */
    static public URI pickHAVarray(Map<URI, Set<URI>> haVarrayToVolumesMap) {
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
     * 
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
                    if (!varrayToInitiators.keySet().contains(varrayURI)) {
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
     * 
     * @param dbClient - DbClient
     * @param srcVarray - the srcVarray iff source side volumes are present
     * @param haVarray -- the haVarray iff ha side volumes are present
     * @param initiatorURIs -- a list of Initiator URIs
     * @param varrayToInitiators --varrayToInitiators URI map previously computed
     * @throws exportCreateNoHostsConnected, exportCreateAllHostsNotConnected
     */
    public static void validateVPlexClusterExport(DbClient dbClient, URI srcVarray, URI haVarray,
            List<URI> initiatorURIs, Map<URI, List<URI>> varrayToInitiators) {
        if (srcVarray == null && haVarray == null) {
            return;
        }
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
                hostName = getInitiatorHostResourceName(initiator);
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
        if (!unconnectedHostNames.isEmpty()) {
            _log.info(String.format("The following initiators are not connected to the %s varrays: %s",
                    whichVarray, unconnectedHostNames.toString()));
        }

        // If both varrays are present and there are any unconnected hosts, fail.
        if (srcVarray != null && haVarray != null) {
            if (!unconnectedHostNames.isEmpty()) {
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
     * 
     * @param dbClient -- Database client
     * @param exportGroup -- ExportGroup object
     * @param hostURI -- URI of host
     * @param vplexURI -- URI of VPLEX StorageSystem
     * @param varrayURI -- Varray we want the Export Mask in
     * @return ExportMask or null if not found
     * @throws Exception
     */
    public static ExportMask getExportMaskForHostInVarray(DbClient dbClient,
            ExportGroup exportGroup, URI hostURI, URI vplexURI, URI varrayURI) throws Exception {
        StringSet maskIds = exportGroup.getExportMasks();
        if (maskIds == null) {
            return null;
        }
        ExportMask sharedExportMask = VPlexUtil.getSharedExportMaskInDb(exportGroup, vplexURI, dbClient, varrayURI, null, null);

        List<ExportMask> exportMasks =
                ExportMaskUtils.getExportMasks(dbClient, exportGroup, vplexURI);
        for (ExportMask exportMask : exportMasks) {
            boolean shared = false;
            if (sharedExportMask != null) {
                if (sharedExportMask.getId().equals(exportMask.getId())) {
                    shared = true;
                }
            }
            if (getExportMaskHosts(dbClient, exportMask, shared).contains(hostURI)
                    && ExportMaskUtils.exportMaskInVarray(dbClient, exportMask, varrayURI)) {
                return exportMask;
            }
        }
        return null;
    }

    /**
     * Returns the Host URI(s) corresponding to a given VPLEX export mask.
     * This is determined by:
     * 1. If exportMask is not shared and if any Initiator has a Host URI, return that (from the first Initiator).
     * 2. If exportMask is shared return all Host URIs belonging to the export mask.
     * 3. Otherwise, if any Initiator has a hostName, return URI(hostName) (first one).
     * 4. Otherwise, return NULL URI.
     * 
     * @param dbClient a database client instance
     * @param exportMask reference to ExportMask object
     * @param sharedExportMask boolean that indicates whether passed exportMask is shared or not.
     * @return URI of host, or Null URI if host undeterminable or multiple host URI if ExportMask is shared.
     */
    public static Set<URI> getExportMaskHosts(DbClient dbClient, ExportMask exportMask, boolean sharedExportMask) {
        Set<URI> hostURIs = new HashSet<URI>();
        if (exportMask.getInitiators() == null || exportMask.getInitiators().isEmpty()) {
            return hostURIs;
        }

        Iterator<String> initiatorIter = exportMask.getInitiators().iterator();
        String hostName = null;
        while (initiatorIter.hasNext()) {
            URI initiatorForHostId = URI.create(initiatorIter.next());
            Initiator initiatorForHost = dbClient.queryObject(Initiator.class, initiatorForHostId);
            if (initiatorForHost == null) {
                continue;
            }
            if (NullColumnValueGetter.isNullURI(initiatorForHost.getHost())) {
                // No Host URI
                if (getInitiatorHostResourceName(initiatorForHost) != null) {
                    // Save the name
                    if (hostName == null) {
                        hostName = getInitiatorHostResourceName(initiatorForHost);
                        _log.info(String.format("Initiator %s has no Host URI, hostName %s",
                                initiatorForHost.getInitiatorPort(), hostName));
                    }
                }
            } else {
                // Non-null Host URI, return the first one found
                _log.info(String.format("ExportMask %s (%s) -> Host %s",
                        exportMask.getMaskName(), exportMask.getId(), initiatorForHost.getHost()));
                hostURIs.add(initiatorForHost.getHost());
                // If its not a shared export mask then it represents only one host hence return
                // after getting host for one of the initiator.
                if (!sharedExportMask) {
                    return hostURIs;
                }
            }
        }

        if (!hostURIs.isEmpty()) {
            return hostURIs;
        }
        // If there was no Initiator with a host URI, then return a hostName as a URI.
        // If there were no hostNames, return null URI.
        _log.info(String.format("ExportMask %s (%s) -> Host %s",
                exportMask.getMaskName(), exportMask.getId(), (hostName != null ? hostName : "null")));
        hostURIs.add(hostName != null ? URI.create(hostName.replaceAll("\\s", "")) : NullColumnValueGetter.getNullURI());
        return hostURIs;
    }

    /**
     * Returns the Host URI for an Initiator.
     * 1. If Initiator has a valid host URI, returns that.
     * 2. Otherwise, returns URI(hostName) or NULL URI.
     * 
     * @param initiator - Initiator
     * @return URI of Host
     */
    public static URI getInitiatorHost(Initiator initiator) {
        if (NullColumnValueGetter.isNullURI(initiator.getHost())) {
            if (getInitiatorHostResourceName(initiator) != null) {
                _log.info(String.format("Initiator %s -> Host %s",
                        initiator.getInitiatorPort(), getInitiatorHostResourceName(initiator)));
                return URI.create(getInitiatorHostResourceName(initiator).replaceAll("\\s", ""));
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
     * 
     * Returns the initiator's host name. If the initiator is an RP initiator, returns the cluster name.
     * In the case of RP, only one StorageView per RP cluster need to be created. RP initiators have a cluster name
     * as well as host name fields populated and returning the host name would result in creation of 2 StorageView's
     * for the same RP cluster. 
     * @param initiator Initiator
     * @return Initiator's host name per the above rules.
     */
    public static String getInitiatorHostResourceName(Initiator initiator) {
    	
    	if (initiator.checkInternalFlags(Flag.RECOVERPOINT)) {
    		return initiator.getClusterName();
    	}
    	
    	return initiator.getHostName();
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
     * 
     * @param dbClient -- DbClient
     * @param volumeURIList -- List of volume URIs
     * @return A map of URI to BlockObject, which would be the correctly translated
     *         from BlockSnapshot to Volume.
     */
    public static Map<URI, BlockObject> translateRPSnapshots(DbClient dbClient, List<URI> volumeURIList) {
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
    public static Set<URI> getBackendPortInitiators(URI vplexUri, DbClient dbClient) {

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
    public static Set<URI> getBackendPortInitiators(DbClient dbClient) {

        _log.info("finding backend port initiators for all VPLEX systems");
        Set<URI> initiators = new HashSet<URI>();

        List<URI> storageSystemUris = dbClient.queryByType(StorageSystem.class, true);
        List<StorageSystem> storageSystems = dbClient.queryObject(StorageSystem.class, storageSystemUris);
        for (StorageSystem storageSystem : storageSystems) {
            if (StringUtils.equals(storageSystem.getSystemType(), VPLEX)) {
                initiators.addAll(getBackendPortInitiators(storageSystem.getId(), dbClient));
            }
        }

        return initiators;
    }

    /**
     * Pre Darth CorpHD used to create ExportMask per host even if there was single storage view on VPLEX
     * with multiple host. This method returns the storage view name to ExportMasks map which is single
     * storage view on VPLEX with multiple hosts but in ViPR database there is ExportMask per host.
     * 
     * @param exportGroup - ExportGroup object
     * @param vplexURI - URI of the VPLEX system
     * @param dbClient - database client instance
     * @return the map of shared storage view name to ExportMasks
     */
    public static Map<String, Set<ExportMask>> getSharedStorageView(ExportGroup exportGroup, URI vplexURI, DbClient dbClient) {
        // Map of Storage view name to list of ExportMasks that represent same storage view on VPLEX.
        Map<String, Set<ExportMask>> exportGroupExportMasks = new HashMap<String, Set<ExportMask>>();
        Map<String, Set<ExportMask>> sharedExportMasks = new HashMap<String, Set<ExportMask>>();
        StringSet maskIds = exportGroup.getExportMasks();
        if (maskIds == null) {
            return null;
        }
        List<ExportMask> exportMasks =
                ExportMaskUtils.getExportMasks(dbClient, exportGroup, vplexURI);
        for (ExportMask exportMask : exportMasks) {
            if (!exportMask.getInactive()) {
                if (!exportGroupExportMasks.containsKey(exportMask.getMaskName())) {
                    exportGroupExportMasks.put(exportMask.getMaskName(), new HashSet<ExportMask>());
                }
                exportGroupExportMasks.get(exportMask.getMaskName()).add(exportMask);
            }
        }
        for (Map.Entry<String, Set<ExportMask>> entry : exportGroupExportMasks.entrySet()) {
            if (entry.getValue().size() > 1) {
                sharedExportMasks.put(entry.getKey(), entry.getValue());
            }
        }

        return sharedExportMasks;
    }

    /**
     * Given a list of initiator URIs, make a map of Host URI to a list of Initiators.
     * 
     * @param initiators -- list of Initiator URIs
     * @return -- Map of Host URI to List<Initiator> (objects)
     */
    public static Map<URI, List<Initiator>> makeHostInitiatorsMap(List<URI> initiators, DbClient dbClient) {
        // sort initiators in a host to initiator map
        Map<URI, List<Initiator>> hostInitiatorMap = new HashMap<URI, List<Initiator>>();
        if (!initiators.isEmpty()) {
            for (URI initiatorUri : initiators) {

                Initiator initiator = dbClient.queryObject(Initiator.class, initiatorUri);
                URI initiatorHostURI = VPlexUtil.getInitiatorHost(initiator);
                List<Initiator> initiatorSet = hostInitiatorMap.get(initiatorHostURI);
                if (initiatorSet == null) {
                    hostInitiatorMap.put(initiatorHostURI, new ArrayList<Initiator>());
                    initiatorSet = hostInitiatorMap.get(initiatorHostURI);
                }
                initiatorSet.add(initiator);
            }
        }
        _log.info("assembled map of hosts to initiators: " + hostInitiatorMap);
        return hostInitiatorMap;
    }

    /**
     * This methods takes the list of exportMasks which could be from both the VPLEX cluster and returns exportMasks
     * for a vplexCluster.
     * 
     * @param vplexURI URI of the VPLEX system
     * @param dbClient database client instance
     * @param varrayURI URI of the Virtual Array
     * @param vplexCluster The cluster value for the VPLEX. If null then gets it from the passed varrayURI
     * @param exportMasks List of export masks.
     * @return returns filtered list of exportMasks for a VPLEX cluster
     * @throws Exception
     */
    private static List<ExportMask> getExportMasksForVplexCluster(URI vplexURI, DbClient dbClient, URI varrayURI,
            String vplexCluster, List<ExportMask> exportMasks) throws Exception {
        List<ExportMask> exportMasksForVplexCluster = new ArrayList<ExportMask>();
        if (vplexCluster == null) {
            vplexCluster = ConnectivityUtil.getVplexClusterForVarray(varrayURI, vplexURI, dbClient);
            if (vplexCluster.equals(ConnectivityUtil.CLUSTER_UNKNOWN)) {
                throw new Exception("Unable to find VPLEX cluster for the varray " + varrayURI);
            }
        }
        for (ExportMask mask : exportMasks) {
            // We need to make sure the storage ports presents in the exportmask
            // belongs to the same vplex cluster as the varray.
            // This indicates which cluster this is part of.
            boolean clusterMatch = false;
            _log.info("this ExportMask contains these storage ports: " + mask.getStoragePorts());
            for (String portUri : mask.getStoragePorts()) {
                StoragePort port = dbClient.queryObject(StoragePort.class, URI.create(portUri));
                if (port != null && !port.getInactive()) {
                    if (clusterMatch == false) {
                        // We need to match the VPLEX cluster for the exportMask
                        // as the exportMask for the same host can be in both VPLEX clusters
                        String vplexClusterForMask = ConnectivityUtil.getVplexClusterOfPort(port);
                        clusterMatch = vplexClusterForMask.equals(vplexCluster);
                        if (clusterMatch) {
                            _log.info("a matching ExportMask " + mask.getMaskName()
                                    + " was found on this VPLEX " + varrayURI
                                    + " on  cluster " + vplexCluster);
                            exportMasksForVplexCluster.add(mask);
                        }
                    }
                }
            }
        }
        return exportMasksForVplexCluster;
    }

    /**
     * Returns the shared export mask in the export group i:e single ExportMask in database for multiple hosts
     * corresponding to the single storage view on VPLEX with multiple hosts.
     * 
     * At-least there should be two host in the exportMask to be called as sharedExportMask. Also there shouldn't be more than one
     * exportMask for the exportGroup for a VPLEX cluster.
     * 
     * Note : This is applicable from Darth release onwards.
     * 
     * @param exportGroup ExportGroup object
     * @param vplexURI URI of the VPLEX system
     * @param dbClient database client instance
     * @param varrayUri Varray we want the Export Mask in
     * @param vplexCluster Vplex Cluster we want ExportMask for
     * @param hostInitiatorMap Map of host to initiators that are not yet added to the storage view on VPLEX
     * @return shared ExportMask for a exportGroup
     * @throws Exception
     */
    public static ExportMask getSharedExportMaskInDb(ExportGroup exportGroup, URI vplexURI, DbClient dbClient,
            URI varrayUri, String vplexCluster, Map<URI, List<Initiator>> hostInitiatorMap) throws Exception {
        ExportMask sharedExportMask = null;
        StringSet maskIds = exportGroup.getExportMasks();
        if (maskIds == null) {
            return null;
        }
        StringSet exportGrouphosts = exportGroup.getHosts();
        // Get all the exportMasks for the VPLEX from the export group
        List<ExportMask> exportMasks =
                ExportMaskUtils.getExportMasks(dbClient, exportGroup, vplexURI);

        // exportMasks list could have mask for both the VPLEX cluster for the same initiators for the cross-connect case
        // for the cross-connect case hence get the ExportMask for the specific VPLEX cluster.
        List<ExportMask> exportMasksForVplexCluster = getExportMasksForVplexCluster(vplexURI, dbClient, varrayUri, vplexCluster,
                exportMasks);

        // There is possibility of shared export mask only if there is more than one host in the exportGroup
        // and we found only one exportMask in database for the VPLEX cluster
        if (exportGrouphosts != null && exportGrouphosts.size() > 1 && exportMasksForVplexCluster.size() == 1) {
            ExportMask exportMask = exportMasksForVplexCluster.get(0);
            ArrayList<String> exportMaskInitiators = new ArrayList<String>(exportMask.getInitiators());
            Map<URI, List<Initiator>> exportMaskHostInitiatorsMap = makeHostInitiatorsMap(URIUtil.toURIList(exportMaskInitiators), dbClient);
            // Remove the host which is not yet added by CorpHD
            if (hostInitiatorMap != null) {
                for (Entry<URI, List<Initiator>> entry : hostInitiatorMap.entrySet()) {
                    exportMaskHostInitiatorsMap.remove(entry.getKey());
                }
            }
            // If we found more than one host in the exportMask then its a sharedExportMask
            if (exportMaskHostInitiatorsMap.size() > 1) {
                sharedExportMask = exportMask;
            }
        }

        return sharedExportMask;
    }

    /**
     * Returns ExportMask for the VPLEX cluster where passed in list of initiators is in the existingInitiators List.
     * 
     * @param vplexURI URI of the VPLEX system
     * @param dbClient database client instance
     * @param inits List of Initiators
     * @param varrayUri URI of the Virtual Array
     * @param vplexCluster VPLEX Cluster value (1 or 2)
     * @return the ExportMask with inits in the existingInitiators list
     * @throws Exception
     */
    public static ExportMask getExportMasksWithExistingInitiators(URI vplexURI, DbClient dbClient, List<Initiator> inits,
            URI varrayURI, String vplexCluster) throws Exception {
        ExportMask sharedVplexExportMask = null;
        // Get initiators WWN in upper case without colons
        Collection<String> initiatorNames = Collections2.transform(inits, CommonTransformerFunctions.fctnInitiatorToPortName());
        // J1 joiner to fetch all the exportMasks for the VPLEX where existingInitiators list match one or all inits.
        Joiner j1 = new Joiner(dbClient);
        j1.join(ExportMask.class, "exportmask").match("existingInitiators", initiatorNames).match("storageDevice", vplexURI).go()
                .printTuples("exportmask");
        List<ExportMask> exportMasks = j1.list("exportmask");
        // exportMasks list could have mask for both the VPLEX cluster for the same initiators for the cross-connect case
        // hence get the ExportMask for the specific VPLEX cluster.
        List<ExportMask> exportMasksForVplexCluster = getExportMasksForVplexCluster(vplexURI, dbClient, varrayURI, vplexCluster,
                exportMasks);
        if (!exportMasksForVplexCluster.isEmpty()) {
            sharedVplexExportMask = exportMasksForVplexCluster.get(0);
            _log.info(String.format("Found ExportMask %s %s with some or all initiators %s in the existing initiators.",
                    sharedVplexExportMask.getMaskName(), sharedVplexExportMask.getId(), initiatorNames));
        }
        return sharedVplexExportMask;
    }

    /**
     * Check if the backend volumes for the vplex volumes in a consistency group are in the same storage system.
     * 
     * @param vplexVolumes List of VPLEX volumes in a consistency group
     * @param dbClient an instance of {@link DbClient}
     * 
     * @return true or false
     * 
     */
    public static boolean isVPLEXCGBackendVolumesInSameStorage(List<Volume> vplexVolumes, DbClient dbClient) {
        Set<String> backendSystems = new HashSet<String>();
        Set<String> haBackendSystems = new HashSet<String>();
        boolean result = true;
        for (Volume vplexVolume : vplexVolumes) {
            Volume srcVolume = getVPLEXBackendVolume(vplexVolume, true, dbClient);
            backendSystems.add(srcVolume.getStorageController().toString());

            Volume haVolume = getVPLEXBackendVolume(vplexVolume, false, dbClient);
            if (haVolume != null) {
                haBackendSystems.add(haVolume.getStorageController().toString());
            }

        }
        if (backendSystems.size() > 1 || haBackendSystems.size() > 1) {
            result = false;
        }
        return result;
    }

    /**
     * Verifies if the passed volumes are all the volumes in the same backend arrays in the passed
     * consistency group.
     * 
     * @param volumes The list of volumes to verify
     * @param cg The consistency group
     * @return true or false
     */
    public static boolean verifyVolumesInCG(List<Volume> volumes, BlockConsistencyGroup cg, DbClient dbClient) {
        List<Volume> cgVolumes = BlockConsistencyGroupUtils.getActiveVplexVolumesInCG(cg, dbClient, null);
        return verifyVolumesInCG(volumes, cgVolumes, dbClient);
    }

    /**
     * Verifies if the passed volumes are all the volumes in the same backend arrays in the passed
     * consistency group volumes.
     * 
     * @param volumes The list of volumes to verify
     * @param cgVolumes All the volumes in the consistency group
     * @return true or false
     */
    public static boolean verifyVolumesInCG(List<Volume> volumes, List<Volume> cgVolumes, DbClient dbClient) {
        boolean result = true;
        // Sort all the volumes in the CG based on the backend volume's storage system.
        Map<String, List<String>> cgBackendSystemToVolumesMap = new HashMap<String, List<String>>();
        for (Volume cgVolume : cgVolumes) {
            Volume srcVolume = VPlexUtil.getVPLEXBackendVolume(cgVolume, true, dbClient);
            List<String> vols = cgBackendSystemToVolumesMap.get(srcVolume.getStorageController().toString());
            if (vols == null) {
                vols = new ArrayList<String>();
                cgBackendSystemToVolumesMap.put(srcVolume.getStorageController().toString(), vols);
            }
            vols.add(cgVolume.getId().toString());
        }
        // Sort the passed volumes, and make sure the volumes are in the CG.
        Map<String, List<String>> backendSystemToVolumesMap = new HashMap<String, List<String>>();
        for (Volume volume : volumes) {
            Volume srcVolume = VPlexUtil.getVPLEXBackendVolume(volume, true, dbClient);
            List<String> vols = backendSystemToVolumesMap.get(srcVolume.getStorageController().toString());
            if (vols == null) {
                vols = new ArrayList<String>();
                backendSystemToVolumesMap.put(srcVolume.getStorageController().toString(), vols);
            }
            vols.add(volume.getId().toString());
            boolean found = false;
            for (Volume cgVolume : cgVolumes) {
                if (volume.getId().equals(cgVolume.getId())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        // Make sure all volumes from the same backend storage systems are selected
        for (Entry<String, List<String>> entry : backendSystemToVolumesMap.entrySet()) {
            String systemId = entry.getKey();
            List<String> selectedVols = entry.getValue();
            List<String> cgVols = cgBackendSystemToVolumesMap.get(systemId);
            if (selectedVols.size() < cgVols.size()) {
                // not all volumes from the same backend system are selected.
                result = false;
                break;
            }
        }
        return result;
    }

    /**
     * Check if the volume is in an ingested VPlex consistency group
     * 
     * @param volume The volume to be checked on
     * @param dbClient
     * @return true or false
     */
    public static boolean isVolumeInIngestedCG(Volume volume, DbClient dbClient) {
        boolean result = false;
        URI cgUri = volume.getConsistencyGroup();
        if (!NullColumnValueGetter.isNullURI(cgUri)) {
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
            if (cg != null) {
                if (!cg.getTypes().contains(Types.LOCAL.toString())) {
                    result = true;
                }
            }
        }
        return result;
    }

    /**
     * Check if the full copy is a vplex full copy and its backend full copy is in a replication group
     * @param fullcopy
     * @param dbClient
     * @return true or false
     */
    public static boolean isBackendFullCopyInReplicationGroup(Volume fullcopy, DbClient dbClient) {
        boolean result = false;
        URI systemURI = fullcopy.getStorageController();
        StorageSystem system = dbClient.queryObject(StorageSystem.class, systemURI);
        String type = system.getSystemType();
        if (type.equals(DiscoveredDataObject.Type.vplex.name())) {
            Volume backendFullcopy = getVPLEXBackendVolume(fullcopy, true, dbClient);
            if (backendFullcopy != null) {
                String replicationGroup = backendFullcopy.getReplicationGroupInstance();
                if (NullColumnValueGetter.isNotNullValue(replicationGroup)) {
                    result = true;
                }
            }
        }
        
        return result;
    }

    // constants related to supporting device structure validation
    private static final String LOCAL_DEVICE = "local-device: ";
    private static final String LOCAL_DEVICE_COMPONENT = "   local-device-component: ";
    private static final String DISTRIBUTED_DEVICE = "distributed-device: ";
    private static final String DISTRIBUTED_DEVICE_COMPONENT = "   distributed-device-component: ";
    private static final String EXTENT = "   extent: ";
    private static final String STORAGE_VOLUME = "   storage-volume: ";
    private static final String START = "^(?s)";
    private static final String ANYTHING = "(.*)";
    private static final String END = "(.*)$";

    // these patterns are used to build up the various
    // supported device structures as outlined in the method javadoc
    private static final StringBuffer EXTENT_STORAGE_VOLUME_PATTERN = 
            new StringBuffer(ANYTHING).append(EXTENT)
                     .append(ANYTHING).append(STORAGE_VOLUME);
    private static final StringBuffer LOCAL_DEVICE_COMPONENT_PATTERN = 
            new StringBuffer(ANYTHING).append(LOCAL_DEVICE_COMPONENT)
                .append(EXTENT_STORAGE_VOLUME_PATTERN);
    private static final StringBuffer DISTRIBUTED_DEVICE_COMPONENT_PATTERN = 
            new StringBuffer(ANYTHING).append(DISTRIBUTED_DEVICE_COMPONENT)
                .append(EXTENT_STORAGE_VOLUME_PATTERN);
    private static final StringBuffer DISTRIBUTED_LEG_MIRROR_PATTERN = 
            new StringBuffer(ANYTHING).append(DISTRIBUTED_DEVICE_COMPONENT)
                .append(LOCAL_DEVICE_COMPONENT_PATTERN)
                .append(LOCAL_DEVICE_COMPONENT_PATTERN);

    /**
     * Analyzes the given String as a VPLEX API drill-down response and
     * checks that it has a structure compatible with ViPR.  Supported structure examples:
     * 
     * a simple local device
     * 
     *  local-device: device_VAPM00140844981-01727 (cluster-1)
     *     extent: extent_VAPM00140844981-01727_1
     *        storage-volume: VAPM00140844981-01727 (blocks: 0 - 2097151)
     *
     * a local device with a mirror
     * 
     *  local-device: device_VAPM00140844981-00464 (cluster-1)
     *     local-device-component: device_VAPM00140801303-01246
     *        extent: extent_VAPM00140801303-01246_1
     *           storage-volume: VAPM00140801303-01246 (blocks: 0 - 786431)
     *     local-device-component: device_VAPM00140844981-004642015Oct07_142827
     *        extent: extent_VAPM00140844981-00464_1
     *           storage-volume: VAPM00140844981-00464 (blocks: 0 - 786431)
     * 
     * a simple distributed device
     * 
     *  distributed-device: dd_VAPM00140844981-00294_V000198700406-02199
     *     distributed-device-component: device_V000198700406-02199 (cluster-2)
     *        extent: extent_V000198700406-02199_1
     *           storage-volume: V000198700406-02199 (blocks: 0 - 524287)
     *     distributed-device-component: device_VAPM00140844981-00294 (cluster-1)
     *        extent: extent_VAPM00140844981-00294_1
     *           storage-volume: VAPM00140844981-00294 (blocks: 0 - 524287)
     *
     * a distributed device with a mirror on one or both legs
     * 
     *  distributed-device: dd_VAPM00140844981-00525_VAPM00140801303-01247
     *     distributed-device-component: device_VAPM00140801303-01247 (cluster-2)
     *        extent: extent_VAPM00140801303-01247_1
     *           storage-volume: VAPM00140801303-01247 (blocks: 0 - 1048575)
     *     distributed-device-component: device_VAPM00140844981-00525 (cluster-1)
     *        local-device-component: device_VAPM00140801303-01258
     *           extent: extent_VAPM00140801303-01258_1
     *              storage-volume: VAPM00140801303-01258 (blocks: 0 - 1048575)
     *        local-device-component: device_VAPM00140844981-005252015Oct07_160927
     *           extent: extent_VAPM00140844981-00525_1
     *              storage-volume: VAPM00140844981-00525 (blocks: 0 - 1048575)
     * 
     * @param deviceName name of the device being analyzed
     * @param drillDownResponse a drill-down command response from the VPLEX API 
     * @return true if the device structure is compatible with ViPR
     */
    public static boolean isDeviceStructureValid(String deviceName, String drillDownResponse) {

        if (drillDownResponse != null && !drillDownResponse.isEmpty()) {
            _log.info("looking at device {} with drill-down {}", deviceName, drillDownResponse);

            // could quite possible run into NullPointer or other Exceptions,
            // and in any of those cases, we'll just return false. so, for readability,
            // there's not a lot of null checking going on here
            try {
                String[] lines = drillDownResponse.split("\n");
                if (lines.length > 1) {
                    // a supported vplex device can have 0, 2, or 4 local device components
                    // 0 indicates a simple local or distributed volume
                    // 2 indicates a mirror configured on local or one leg of distributed
                    // 4 indicates a mirror configured on each leg of a distributed volume
                    int localDeviceComponentCount = 
                            StringUtils.countMatches(drillDownResponse, LOCAL_DEVICE_COMPONENT);

                    // other component counts
                    int storageVolumeCount = StringUtils.countMatches(drillDownResponse, STORAGE_VOLUME);
                    int extentCount = StringUtils.countMatches(drillDownResponse, EXTENT);

                    String firstLine = lines[0];
                    if (firstLine.startsWith(LOCAL_DEVICE)) {
                        return validateLocalDevice(
                                drillDownResponse, localDeviceComponentCount, storageVolumeCount, extentCount);
                    } else if (firstLine.startsWith(DISTRIBUTED_DEVICE)) {
                        return validateDistributedDevice(
                                drillDownResponse, localDeviceComponentCount, storageVolumeCount, extentCount);
                    }
                }
            } catch (Exception ex) {
                _log.error("Exception encountered parsing device drill down: " 
                        + ex.getLocalizedMessage(), ex);
            }
        }

        _log.error("this is not a compatible supporting device structure");
        return false;
    }

    /**
     * Validates a local device drill down response for valid ViPR-compatible structure
     * 
     * @param drillDownResponse the drill-down command response from the VPLEX API
     * @param localDeviceComponentCount count of local-device-components in the drill-down response
     * @param storageVolumeCount count of storage-volumes in the drill-down response
     * @param extentCount count of extents in the drill-down response
     * 
     * @return true if this drill-down structure is compatible for ingestion 
     */
    private static boolean validateLocalDevice(
            String drillDownResponse, int localDeviceComponentCount,
            int storageVolumeCount, int extentCount) {
        // a local device can have 0 or 2 local device components
        switch (localDeviceComponentCount) {
            case 0:
                // this could be a simple local volume
                if (storageVolumeCount == 1 && extentCount == 1) {
                    StringBuffer localDevice = new StringBuffer(START);
                    localDevice.append(LOCAL_DEVICE)
                               .append(EXTENT_STORAGE_VOLUME_PATTERN)
                               .append(END);
                    if (drillDownResponse.matches(localDevice.toString())) {
                        _log.info("this is a simple local volume");
                        return true;
                    }
                }
                break;
            case 2:
                // this could be a local volume with a mirror configured
                if (storageVolumeCount == 2 && extentCount == 2) {
                    StringBuffer localDeviceWithMirror = new StringBuffer(START);
                    localDeviceWithMirror.append(LOCAL_DEVICE)
                                         .append(LOCAL_DEVICE_COMPONENT_PATTERN)
                                         .append(LOCAL_DEVICE_COMPONENT_PATTERN)
                                         .append(END);
                    if (drillDownResponse.matches(localDeviceWithMirror.toString())) {
                        _log.info("this is a local device with mirror");
                        return true;
                    }
                }
                break;
            default :
                // fall through
        }
        
        return false;
    }

    /**
     * Validates a distributed device drill down response for valid ViPR-compatible structure
     * 
     * @param drillDownResponse the drill-down command response from the VPLEX API
     * @param localDeviceComponentCount count of local-device-components in the drill-down response
     * @param storageVolumeCount count of storage-volumes in the drill-down response
     * @param extentCount count of extents in the drill-down response
     * 
     * @return true if this drill-down structure is compatible for ingestion 
     */
    private static boolean validateDistributedDevice(
            String drillDownResponse, int localDeviceComponentCount, 
            int storageVolumeCount, int extentCount) {
        // need to check that distributed device has
        // exactly two distributed device components
        int distributedDeviceComponentCount = 
                StringUtils.countMatches(drillDownResponse, DISTRIBUTED_DEVICE_COMPONENT);
        if (distributedDeviceComponentCount == 2) {
            // we have the right number of distributed device components
            // a distributed device can have 0, 2, or 4 local device components
            switch (localDeviceComponentCount) {
                case 0:
                    // this could be a simple distributed volume
                    if (storageVolumeCount == 2 && extentCount == 2) {
                        StringBuffer distributedDevice = new StringBuffer(START);
                        distributedDevice.append(DISTRIBUTED_DEVICE)
                                         .append(DISTRIBUTED_DEVICE_COMPONENT_PATTERN)
                                         .append(DISTRIBUTED_DEVICE_COMPONENT_PATTERN)
                                         .append(END);
                        if (drillDownResponse.matches(distributedDevice.toString())) {
                            _log.info("this is a simple distributed device");
                            return true;
                        }
                    }
                    break;
                case 2:
                    // this could be a volume with a mirror on one leg or the other
                    if (storageVolumeCount == 3 && extentCount == 3) {
                        StringBuffer distributedDeviceMirrorOnLeg1 = new StringBuffer(START);
                        distributedDeviceMirrorOnLeg1.append(DISTRIBUTED_DEVICE)
                                         .append(DISTRIBUTED_LEG_MIRROR_PATTERN)
                                         .append(DISTRIBUTED_DEVICE_COMPONENT_PATTERN)
                                         .append(END);
                        StringBuffer distributedDeviceMirrorOnLeg2 = new StringBuffer(START);
                        distributedDeviceMirrorOnLeg2.append(DISTRIBUTED_DEVICE)
                                         .append(DISTRIBUTED_DEVICE_COMPONENT_PATTERN)
                                         .append(DISTRIBUTED_LEG_MIRROR_PATTERN)
                                         .append(END);
                        if (drillDownResponse.matches(distributedDeviceMirrorOnLeg1.toString()) 
                         || drillDownResponse.matches(distributedDeviceMirrorOnLeg2.toString())) {
                            _log.info("this is a distributed volume with a mirror on one leg or the other");
                            return true;
                        }
                    }
                    break;
                case 4:
                    // this could be a volume with a mirror on each leg
                    if (storageVolumeCount == 4 && extentCount == 4) {
                        StringBuffer distributedDeviceMirrorOnBothLegs = new StringBuffer(START);
                        distributedDeviceMirrorOnBothLegs.append(DISTRIBUTED_DEVICE)
                                         .append(DISTRIBUTED_LEG_MIRROR_PATTERN)
                                         .append(DISTRIBUTED_LEG_MIRROR_PATTERN)
                                         .append(END);
                        if (drillDownResponse.matches(distributedDeviceMirrorOnBothLegs.toString())) {
                            _log.info("this is a distributed volume with mirrors on both legs");
                            return true;
                        }
                    }
                    break;
                default :
                    // fall through
            }
        }
        
        return false;
    }

    /**
     * Determines if the passed VPLEX volume is built on top of a target
     * volume for a block snapshot.
     * 
     * @param dbClient A reference to a database client.
     * @param vplexVolume A reference to a VPLEX volume.
     * 
     * @return true of the Volume is built on a block snapshot, false otherwise.
     */
    public static boolean isVolumeBuiltOnBlockSnapshot(DbClient dbClient, Volume vplexVolume) {
        boolean isBuiltOnSnapshot = false;
        Volume srcSideBackendVolume = getVPLEXBackendVolume(vplexVolume, true, dbClient, false);
        if (srcSideBackendVolume != null) {
            String nativeGuid = srcSideBackendVolume.getNativeGuid();
            List<BlockSnapshot> snapshots = CustomQueryUtility.getActiveBlockSnapshotByNativeGuid(dbClient, nativeGuid);
            if (!snapshots.isEmpty()) {
                // There is a snapshot with the same native GUID as the source
                // side backend volume, and therefore the VPLEX volume is built
                // on a block snapshot target volume.
                isBuiltOnSnapshot = true;
            }
        }

        return isBuiltOnSnapshot;
    }
    
    /**
     * Determines if the back-end is OpenStack Cinder, if yes returns true
     * otherwise returns false.
     * 
     * @param fcObject
     * @param dbClient
     * @return
     */
    public static boolean isOpenStackBackend(BlockObject fcObject, DbClient dbClient) {
        
        URI backendStorageSystem = null;
        if(fcObject instanceof Volume) {            
            Volume backendVolume = getVPLEXBackendVolume((Volume)fcObject, true, dbClient, true);
            backendStorageSystem = backendVolume.getStorageController();
        } else {
            backendStorageSystem = fcObject.getStorageController();
        }
        StorageSystem backendStorage = dbClient.queryObject(StorageSystem.class, backendStorageSystem);
        String systemType = backendStorage.getSystemType();
        if(DiscoveredDataObject.Type.openstack.name().equals(systemType)) {
            return true;
        }
        
        return false;
    }
}
