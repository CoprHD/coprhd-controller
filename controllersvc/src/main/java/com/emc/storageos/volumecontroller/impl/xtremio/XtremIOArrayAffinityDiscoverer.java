/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.xtremio;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
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
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export.ArrayAffinityDiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiator;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOLunMap;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Sets;

/**
 * The Class XtremIOArrayAffinityDiscoverer discovers the storage pool information for the host(s) if it has volumes exported to it.
 */
public class XtremIOArrayAffinityDiscoverer {
    private static final Logger log = LoggerFactory.getLogger(XtremIOArrayAffinityDiscoverer.class);

    private List<XtremIOLunMap> xtremIOLunMaps = null;

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
            String igName = getIGNameForInitiator(initiator, system.getSerialNumber(), xtremIOClient, xioClusterName);
            if (igName != null && !igName.isEmpty()) {
                groupInitiatorsByIG.put(igName, initiator);
            }
        }
        log.info("List of IGs found {}",
                Joiner.on(",").join(groupInitiatorsByIG.asMap().entrySet()));

        Set<String> igNames = groupInitiatorsByIG.keySet();
        Set<String> volumeNames = getVolumesForHost(xtremIOClient, xioClusterName, igNames);

        // get storage pool for matching volumes
        if (!volumeNames.isEmpty()) {
            log.info("Volumes found for this Host: {}", volumeNames);
            // As XtremIO array has only one storage pool, add the pool directly.
            // get the storage pool associated with the XtremIO system
            StoragePool storagePool = XtremIOProvUtils.getXtremIOStoragePool(system.getId(), dbClient);

            String maskType = getMaskTypeForHost(xtremIOClient, xioClusterName, groupInitiatorsByIG, igNames, volumeNames);

            ArrayAffinityDiscoveryUtils.addPoolToPreferredPoolMap(preferredPoolMap, storagePool.getId().toString(), maskType);
        } else {
            log.info("No Volumes found for this Host");
        }

        return preferredPoolMap;
    }

    /**
     * Gets the volumes from the array for the given host's IGs.
     */
    private Set<String> getVolumesForHost(XtremIOClient xtremIOClient, String xioClusterName, Set<String> igNames) throws Exception {
        log.info("Querying volumes for IGs {}", igNames.toArray());
        Set<String> volumeNames = new HashSet<String>();
        // get the XtremIO lun map details
        List<XtremIOLunMap> lunMaps = getLunMapsForArray(xtremIOClient, xioClusterName);
        for (XtremIOLunMap lunMap : lunMaps) {
            try {
                if (igNames.contains(lunMap.getIgName())) {
                    log.info("LunMap {} matching for IG {}", lunMap.getMappingInfo().get(2), lunMap.getIgName());
                    volumeNames.add(lunMap.getVolumeName());
                }
            } catch (Exception ex) {
                log.info("Error processing XtremIO lun map {}. {}", lunMap, ex.getMessage());
            }
        }
        return volumeNames;
    }

    /**
     * Gets the lun maps from the array. If already queried, it returns the cached data.
     *
     * @param xtremIOClient the xtrem io client
     * @param xioClusterName the xio cluster name
     * @return the lun maps for array
     * @throws Exception the exception
     */
    private List<XtremIOLunMap> getLunMapsForArray(XtremIOClient xtremIOClient, String xioClusterName) throws Exception {
        if (xtremIOLunMaps == null) {
            xtremIOLunMaps = xtremIOClient.getXtremIOLunMaps(xioClusterName);
        }
        return xtremIOLunMaps;
    }

    /**
     * Identifies the mask type (Host/Cluster) for the given host's IGs.
     */
    private String getMaskTypeForHost(XtremIOClient xtremIOClient, String xioClusterName,
            ArrayListMultimap<String, Initiator> groupInitiatorsByIG, Set<String> hostIGNames, Set<String> volumeNames)
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
            if (Integer.parseInt(xioIG.getNumberOfInitiators()) > groupInitiatorsByIG.get(igName).size()) {
                maskType = ExportGroup.ExportGroupType.Cluster.name();
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
                    // TODO check Default?
                    volumeIGNames.add(volumeIGName);
                }
            }
            log.info("Host IG names: {}, Volumes IG names: {}", hostIGNames, volumeIGNames);
            volumeIGNames.removeAll(hostIGNames);
            if (!volumeIGNames.isEmpty()) {
                maskType = ExportGroup.ExportGroupType.Cluster.name();
            }
        }
        return maskType;
    }

    private String getIGNameForInitiator(Initiator initiator, String storageSerialNumber, XtremIOClient client, String xioClusterName)
            throws Exception {
        String igName = null;
        try {
            String initiatorName = initiator.getMappedInitiatorName(storageSerialNumber);
            if (null != initiatorName) {
                // Get initiator by Name and find IG Group
                XtremIOInitiator initiatorObj = client.getInitiator(initiatorName, xioClusterName);
                if (null != initiatorObj) {
                    igName = initiatorObj.getInitiatorGroup().get(1);
                }
            }
        } catch (Exception e) {
            log.warn("Initiator {} not found", initiator.getLabel());
        }

        return igName;
    }

    /**
     * Find and update preferred pools information for all Hosts.
     *
     * @param system the system
     * @param dbClient the db client
     * @throws Exception the exception
     */
    public void findAndUpdatePreferredPools(StorageSystem system, DbClient dbClient) throws Exception {
        /**
         * Get all initiators on array,
         * Group initiators by IG, also maintain a map of Host to IGs.
         * For each Host entry in the map:
         * - Find if any of its IG has volumes,
         * - Find the mask type for the pool.
         */
        XtremIOClient xtremIOClient = XtremIOProvUtils.getXtremIOClient(dbClient, system, xtremioRestClientFactory);
        String xioClusterName = xtremIOClient.getClusterDetails(system.getSerialNumber()).getName();

        // Group all the initiators and their initiator groups based on ViPR host.
        ArrayListMultimap<String, Initiator> igNameToInitiatorsMap = ArrayListMultimap.create();
        Map<URI, Set<String>> hostToIGNamesMap = new HashMap<URI, Set<String>>();
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
            } else {
                log.info("No host in ViPR found configured for initiator {}", initiatorNetworkId);
            }
        }

        // As XtremIO array has only one storage pool, add the pool directly.
        // get the storage pool associated with the XtremIO system
        StoragePool storagePool = XtremIOProvUtils.getXtremIOStoragePool(system.getId(), dbClient);
        for (URI hostId : hostToIGNamesMap.keySet()) {
            Host host = dbClient.queryObject(Host.class, hostId);
            if (host != null) {
                log.info("Processing Host {}", host.getLabel());
                Map<String, String> preferredPoolMap = new HashMap<String, String>();
                Set<String> volumeNames = getVolumesForHost(xtremIOClient, xioClusterName, hostToIGNamesMap.get(hostId));
                if (!volumeNames.isEmpty()) {
                    String maskType = getMaskTypeForHost(xtremIOClient, xioClusterName,
                            igNameToInitiatorsMap, hostToIGNamesMap.get(hostId), volumeNames);

                    ArrayAffinityDiscoveryUtils.addPoolToPreferredPoolMap(preferredPoolMap, storagePool.getId().toString(), maskType);
                } else {
                    log.info("No Volumes found for this Host");
                }

                if (ArrayAffinityDiscoveryUtils.updatePreferredPools(host, Sets.newHashSet(system.getId().toString()),
                        dbClient, preferredPoolMap)) {
                    dbClient.updateObject(host);
                }
            }
        }
    }
}
