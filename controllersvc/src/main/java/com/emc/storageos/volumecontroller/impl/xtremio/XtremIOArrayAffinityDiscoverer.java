/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.xtremio;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export.ArrayAffinityDiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiator;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOLunMap;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOObjectInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * The Class XtremIOArrayAffinityDiscoverer discovers the storage pool information for the host(s) if it has volumes exported to it.
 */
public class XtremIOArrayAffinityDiscoverer {
    private static final Logger log = LoggerFactory.getLogger(XtremIOArrayAffinityDiscoverer.class);
    private static final String HOST = "Host";

    private XtremIOClientFactory xtremioRestClientFactory;

    public void setXtremioRestClientFactory(XtremIOClientFactory xtremioRestClientFactory) {
        this.xtremioRestClientFactory = xtremioRestClientFactory;
    }

    /**
     * Find and update preferred pools information for the given host.
     *
     * @param system the system
     * @param host the host
     * @param dbClient the db client
     * @throws Exception the exception
     */
    public void findAndUpdatePreferredPoolsForHost(StorageSystem system, Host host, DbClient dbClient) throws Exception {
        Map<String, String> preferredPoolMap = getPreferredPoolMapForHost(system, host, dbClient);
        if (ArrayAffinityDiscoveryUtils.updatePreferredPools(host, Sets.newHashSet(system.getId().toString()),
                dbClient, preferredPoolMap)) {
            dbClient.updateObject(host);
        }
    }

    /**
     * Gets the preferred pool map for the given host.
     */
    private Map<String, String> getPreferredPoolMapForHost(StorageSystem system, Host host, DbClient dbClient) throws Exception {
        /**
         * Group host's initiators by IG,
         * For each IG:
         * - Get list of volume maps [List all volume maps and see which ones have mapping for this IG],
         * - Get pool detail for each volume,
         * - Find the mask type for the pool.
         */
        Map<String, String> preferredPoolMap = new HashMap<String, String>();
        XtremIOClient xtremIOClient = XtremIOProvUtils.getXtremIOClient(dbClient, system, xtremioRestClientFactory);
        String xioClusterName = xtremIOClient.getClusterDetails(system.getSerialNumber()).getName();
        List<Initiator> hostInitiators = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient, Initiator.class,
                        ContainmentConstraint.Factory.getContainedObjectsConstraint(host.getId(), Initiator.class, Constants.HOST));

        ArrayListMultimap<String, Initiator> groupInitiatorsByIG = ArrayListMultimap.create();
        for (Initiator initiator : hostInitiators) {
            log.info("Processing host initiator {}", initiator.getLabel());
            String igName = XtremIOProvUtils.getIGNameForInitiator(initiator, system.getSerialNumber(), xtremIOClient, xioClusterName);
            if (igName != null && !igName.isEmpty()) {
                groupInitiatorsByIG.put(igName, initiator);
            }
        }
        log.info("List of IGs found {}",
                Joiner.on(",").join(groupInitiatorsByIG.asMap().entrySet()));
        Set<String> igNames = groupInitiatorsByIG.keySet();
        if (!igNames.isEmpty()) {
            // map of IG name to Volume names mapped
            Map<String, Set<String>> igToVolumesMap = getIgToVolumesMap(xtremIOClient, xioClusterName);

            Set<String> volumeNames = getVolumesForHost(igNames, igToVolumesMap);
            // consider only unmanaged volumes
            filterKnownVolumes(system, dbClient, xtremIOClient, xioClusterName, volumeNames);

            // get storage pool for matching volumes
            if (!volumeNames.isEmpty()) {
                log.info("UnManaged Volumes found for this Host: {}", volumeNames);
                // As XtremIO array has only one storage pool, add the pool directly.
                // get the storage pool associated with the XtremIO system
                StoragePool storagePool = XtremIOProvUtils.getXtremIOStoragePool(system.getId(), dbClient);
                if (storagePool != null) {
                    String maskType = getMaskTypeForHost(xtremIOClient, xioClusterName, groupInitiatorsByIG, null, igNames, volumeNames);
                    ArrayAffinityDiscoveryUtils.addPoolToPreferredPoolMap(preferredPoolMap, storagePool.getId().toString(), maskType);
                }
            } else {
                log.info("No UnManaged Volumes found for this Host");
            }
        }

        return preferredPoolMap;
    }

    /**
     * Gets the volumes for the given host's IGs.
     */
    private Set<String> getVolumesForHost(Set<String> igNames, Map<String, Set<String>> igToVolumesMap) throws Exception {
        Set<String> volumeNames = new HashSet<String>();
        if (igNames != null) {
            log.info("Querying volumes for IGs {}", igNames.toArray());
            for (String igName : igNames) {
                Set<String> igVolumes = igToVolumesMap.get(igName);
                log.info("Volumes {} found for IG {}", igVolumes, igName);
                if (igVolumes != null) {
                    volumeNames.addAll(igVolumes);
                }
            }
        }
        return volumeNames;
    }

    /**
     * Queries the lun maps information from array, forms IG name to volume names map.
     */
    private Map<String, Set<String>> getIgToVolumesMap(XtremIOClient xtremIOClient, String xioClusterName) throws Exception {
        log.info("Querying lun maps for cluster {}", xioClusterName);
        Map<String, Set<String>> igToVolumesMap = new HashMap<>();

        // get the XtremIO lun map links and process them in batches
        List<XtremIOObjectInfo> lunMapLinks = xtremIOClient.getXtremIOLunMapLinks(xioClusterName);
        List<List<XtremIOObjectInfo>> lunMapPartitions = Lists.partition(lunMapLinks, Constants.DEFAULT_PARTITION_SIZE);
        for (List<XtremIOObjectInfo> partition : lunMapPartitions) {
            // Get the lun map details
            List<XtremIOLunMap> lunMaps = xtremIOClient.getXtremIOLunMapsForLinks(partition, xioClusterName);
            for (XtremIOLunMap lunMap : lunMaps) {
                try {
                    log.info("Looking at lun map {}; IG name: {}, Volume: {}",
                            lunMap.getMappingInfo().get(2), lunMap.getIgName(), lunMap.getVolumeName());
                    String igName = lunMap.getIgName();
                    Set<String> volumes = igToVolumesMap.get(igName);
                    if (volumes == null) {
                        volumes = new HashSet<String>();
                        igToVolumesMap.put(igName, volumes);
                    }
                    volumes.add(lunMap.getVolumeName());
                } catch (Exception ex) {
                    log.warn("Error processing XtremIO lun map {}. {}", lunMap, ex.getMessage());
                }
            }
        }
        return igToVolumesMap;
    }

    /**
     * Filter the known volumes in DB, as the preferredPools list in Host needs
     * to have pools of unmanaged volumes alone.
     */
    private void filterKnownVolumes(StorageSystem system, DbClient dbClient,
            XtremIOClient xtremIOClient, String xioClusterName, Set<String> igVolumes) throws Exception {
        Iterator<String> itr = igVolumes.iterator();
        while (itr.hasNext()) {
            String volumeName = itr.next();
            XtremIOVolume xioVolume = xtremIOClient.getVolumeDetails(volumeName, xioClusterName);
            String managedVolumeNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                    system.getNativeGuid(), xioVolume.getVolInfo().get(0));
            Volume dbVolume = DiscoveryUtils.checkStorageVolumeExistsInDB(dbClient, managedVolumeNativeGuid);
            if (dbVolume != null) {
                itr.remove();
            }
        }
    }

    /**
     * Identifies the mask type (Host/Cluster) for the given host's IGs.
     *
     * @param xtremIOClient - xtremio client
     * @param xioClusterName - xio cluster name
     * @param groupInitiatorsByIG - IG name to initiators map
     * @param igNameToHostsMap - IG name to hosts map
     * @param hostIGNames - host to IG names map
     * @param volumeNames - volume names for the host
     * @return the mask type for host
     * @throws Exception
     */
    private String getMaskTypeForHost(XtremIOClient xtremIOClient, String xioClusterName,
            ArrayListMultimap<String, Initiator> groupInitiatorsByIG, Map<String, Set<String>> igNameToHostsMap, Set<String> hostIGNames,
            Set<String> volumeNames)
            throws Exception {
        log.debug("Finding out mask type for the host");
        /**
         * 1. If any of the host's IG has initiators other than host's initiators, then cluster type. Otherwise, exclusive type.
         * 2. Further check: Get Lun mappings from host's volumes, get IG names from each Lun mapping,
         * - if volumes are shared with more than the host's IGs, it means it is a shared volume.
         */
        String maskType = ExportGroup.ExportGroupType.Host.name();
        for (String igName : hostIGNames) {
            XtremIOInitiatorGroup xioIG = xtremIOClient.getInitiatorGroup(igName, xioClusterName);
            if (Integer.parseInt(xioIG.getNumberOfInitiators()) > groupInitiatorsByIG.get(igName).size()
                    || (igNameToHostsMap != null && igNameToHostsMap.get(igName) != null && igNameToHostsMap.get(igName).size() > 1)) {
                maskType = ExportGroup.ExportGroupType.Cluster.name();
                log.info("This Host has volume(s) shared with multiple hosts");
                break;
            }
        }
        if (!ExportGroup.ExportGroupType.Cluster.name().equalsIgnoreCase(maskType)) {
            Set<String> volumeIGNames = new HashSet<String>();
            for (String volumeName : volumeNames) {
                XtremIOVolume xioVolume = xtremIOClient.getVolumeDetails(volumeName, xioClusterName);
                for (List<Object> lunMapEntries : xioVolume.getLunMaps()) {
                    @SuppressWarnings("unchecked")
                    List<Object> igDetails = (List<Object>) lunMapEntries.get(0);
                    if (null == igDetails.get(1)) {
                        log.warn("IG Name is null in returned lun map response for volume {}", volumeName);
                        continue;
                    }
                    String volumeIGName = (String) igDetails.get(1);
                    volumeIGNames.add(volumeIGName);
                }
            }
            log.info("Host IG names: {}, Volumes IG names: {}", hostIGNames, volumeIGNames);
            volumeIGNames.removeAll(hostIGNames);
            if (!volumeIGNames.isEmpty()) {
                maskType = ExportGroup.ExportGroupType.Cluster.name();
                log.info("This Host has volume(s) shared with multiple hosts");
            }
        }
        return maskType;
    }

    /**
     * Find and update preferred pools information for all Hosts.
     *
     * @param system the system
     * @param dbClient the db client
     * @param partitionManager
     * @throws Exception the exception
     */
    public void findAndUpdatePreferredPools(StorageSystem system, DbClient dbClient, PartitionManager partitionManager) throws Exception {
        /**
         * Get all initiators on array,
         * Group initiators by IG, also maintain a map of Host to IGs, and a map of IG to Hosts.
         * For each Host in the DB:
         * - Find if any of its IG has volumes,
         * - Find the mask type for the host.
         */
        XtremIOClient xtremIOClient = XtremIOProvUtils.getXtremIOClient(dbClient, system, xtremioRestClientFactory);
        String xioClusterName = xtremIOClient.getClusterDetails(system.getSerialNumber()).getName();

        // Group all the initiators and their initiator groups based on ViPR host.
        ArrayListMultimap<String, Initiator> igNameToInitiatorsMap = ArrayListMultimap.create();
        Map<URI, Set<String>> hostToIGNamesMap = new HashMap<URI, Set<String>>();
        Map<String, Set<String>> igNameToHostsMap = new HashMap<String, Set<String>>();
        List<XtremIOInitiator> initiators = xtremIOClient.getXtremIOInitiatorsInfo(xioClusterName);
        for (XtremIOInitiator initiator : initiators) {
            String initiatorNetworkId = initiator.getPortAddress();
            // check if a host initiator exists for this id
            Initiator knownInitiator = NetworkUtil.getInitiator(initiatorNetworkId, dbClient);
            if (knownInitiator == null) {
                log.debug("Skipping XtremIO initiator {} as it is not found in database", initiatorNetworkId);
                continue;
            }
            URI hostId = knownInitiator.getHost();
            String hostName = knownInitiator.getHostName();
            if (!NullColumnValueGetter.isNullURI(hostId)) {
                log.info("Found a host {}({}) in ViPR for initiator {}",
                        hostId.toString(), knownInitiator.getHostName(), initiatorNetworkId);
                String igName = initiator.getInitiatorGroup().get(1);
                igNameToInitiatorsMap.put(igName, knownInitiator);

                Set<String> hostIGNames = hostToIGNamesMap.get(hostId);
                if (hostIGNames == null) {
                    hostIGNames = new HashSet<String>();
                    hostToIGNamesMap.put(hostId, hostIGNames);
                }
                hostIGNames.add(igName);

                Set<String> igHostNames = igNameToHostsMap.get(igName);
                if (igHostNames == null) {
                    igHostNames = new HashSet<String>();
                    igNameToHostsMap.put(igName, igHostNames);
                }
                igHostNames.add(hostName);
            } else {
                log.info("No host in ViPR found configured for initiator {}", initiatorNetworkId);
            }
        }
        log.info("IG name to Initiators Map: {}", Joiner.on(",").join(igNameToInitiatorsMap.asMap().entrySet()));
        log.info("IG name to Hosts Map: {}", Joiner.on(",").join(igNameToHostsMap.entrySet()));
        log.info("Host to IG names Map: {}", Joiner.on(",").join(hostToIGNamesMap.entrySet()));

        // map of IG name to Volume names mapped
        Map<String, Set<String>> igToVolumesMap = getIgToVolumesMap(xtremIOClient, xioClusterName);

        // As XtremIO array has only one storage pool, add the pool directly.
        // get the storage pool associated with the XtremIO system
        StoragePool storagePool = XtremIOProvUtils.getXtremIOStoragePool(system.getId(), dbClient);
        List<Host> hostsToUpdate = new ArrayList<Host>();
        List<URI> hostURIs = dbClient.queryByType(Host.class, true);
        Iterator<Host> hosts = dbClient.queryIterativeObjectFields(Host.class, ArrayAffinityDiscoveryUtils.HOST_PROPERTIES, hostURIs);
        while (hosts.hasNext()) {
            Host host = hosts.next();
            if (host != null && !host.getInactive()) {
                log.info("Processing Host {}", host.getLabel());
                Map<String, String> preferredPoolMap = new HashMap<String, String>();
                Set<String> volumeNames = getVolumesForHost(hostToIGNamesMap.get(host.getId()), igToVolumesMap);
                // consider only unmanaged volumes
                filterKnownVolumes(system, dbClient, xtremIOClient, xioClusterName, volumeNames);
                if (!volumeNames.isEmpty()) {
                    log.info("UnManaged Volumes found for this Host: {}", volumeNames);
                    if (storagePool != null) {
                        String maskType = getMaskTypeForHost(xtremIOClient, xioClusterName,
                                igNameToInitiatorsMap, igNameToHostsMap, hostToIGNamesMap.get(host.getId()), volumeNames);
                        ArrayAffinityDiscoveryUtils.addPoolToPreferredPoolMap(preferredPoolMap,
                                storagePool.getId().toString(), maskType);
                    }
                } else {
                    log.info("No UnManaged Volumes found for this Host");
                }

                if (ArrayAffinityDiscoveryUtils.updatePreferredPools(host, Sets.newHashSet(system.getId().toString()),
                        dbClient, preferredPoolMap)) {
                    hostsToUpdate.add(host);
                }
            }
        }
        if (!hostsToUpdate.isEmpty()) {
            partitionManager.updateAndReIndexInBatches(hostsToUpdate, Constants.DEFAULT_PARTITION_SIZE, dbClient, HOST);
        }
    }
}
