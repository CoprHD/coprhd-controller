/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.TransportType;
import com.emc.storageos.db.client.model.StorageProtocol.Block;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.emc.storageos.model.block.export.ITLRestRepList;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;

public class ExportUtils {

    // Logger
    private final static Logger _log = LoggerFactory.getLogger(ExportUtils.class);

    /**
     * Returns initiators-to-target-storage-ports pairing for all initiators to which
     * the block object (volume or snapshot) was exported.
     *
     * @param id the URN of a ViPR block object
     * @param dbClient dbClient an instance of {@link DbClient}
     * @param idEmbeddedInURL
     * @return initiators-to-target-storage-ports pairing for all initiators to which
     *         the block object (volume or snapshot) was exported.
     */
    public static ITLRestRepList getBlockObjectInitiatorTargets(URI id, DbClient dbClient, boolean idEmbeddedInURL) {
        BlockObject blockObject = getBlockObject(id, dbClient);
        ArgValidator.checkEntityNotNull(blockObject, id, idEmbeddedInURL);
        ITLRestRepList list = new ITLRestRepList();
        Map<ExportMask, List<ExportGroup>> exportMasks = getBlockObjectExportMasks(blockObject, dbClient);
        Collection<Initiator> initiators = null;
        List<StoragePort> ports = null;
        List<StoragePort> initiatorPorts = null;

        BlockObject bo = Volume.fetchExportMaskBlockObject(dbClient, blockObject.getId());
        if (bo != null) {
            Map<StoragePort, List<FCZoneReference>> zoneRefs = null;
            for (ExportMask exportMask : exportMasks.keySet()) {
                // now process the initiators - Every initiator must see the volume
                initiators = getInitiators(exportMask, dbClient);
                _log.debug("Found {} initiators in export mask {}", initiators.size(), exportMask.getMaskName());
                ports = getStoragePorts(exportMask, dbClient);
                _log.debug("Found {} storage ports in export mask {}", ports.size(), exportMask.getMaskName());
                String hlu = exportMask.getVolumes().get(bo.getId().toString());
                _log.debug("Start pairing initiators and targets in export mask {}.", exportMask.getMaskName());
                for (Initiator initiator : initiators) {
                    initiatorPorts = getInitiatorPorts(exportMask, initiator, ports, dbClient);
                    zoneRefs = getInitiatorsZoneReferencesForBlockObject(initiator, initiatorPorts, bo, dbClient);
                    list.getExportList().addAll(getItlsForMaskInitiator(dbClient,
                            exportMasks.get(exportMask), exportMask, initiator, hlu, blockObject, initiatorPorts, zoneRefs));
                }
            }
        }
        _log.info("{} ITLs were found for block object {}.", list.getExportList().size(), blockObject.getLabel());
        return list;
    }

    private static List<StoragePort> getInitiatorPorts(ExportMask exportMask, Initiator initiator,
            List<StoragePort> ports, DbClient dbClient) {
        List<StoragePort> initiatorPorts = new ArrayList<StoragePort>();
        // Determine if WWN. Don't use the zoning map for iSCSI or other protocols.
        boolean isWWN = WWNUtility.isValidWWN(initiator.getInitiatorPort());
        if (isWWN) {
            if (exportMask.getZoningMap() != null) {
                // ExportMask specifies zoning
                initiatorPorts = getZonedPorts(initiator, ports, exportMask.getZoningMap());
            }
        } else {
            // for iscsi and other ip initiators, get all ports in the network
            NetworkLite network = NetworkUtil.getEndpointNetworkLite(initiator.getInitiatorPort(), dbClient);
            if (network != null && network.getTransportType().equals(TransportType.IP.toString())) {
                for (StoragePort port : ports) {
                    if (network.getId().equals(port.getNetwork())) {
                        initiatorPorts.add(port);
                    }
                }
            }
        }
        return initiatorPorts;
    }

    /**
     * For each initiator in the list, return all the volumes and snapshots that
     * have been exported to it together with the target ports and zone name when
     * zoning has been performed.
     *
     * @param networkPorts a list of initiator WWNs or IQNs
     * @param dbClient dbClient an instance of {@link DbClient}
     * @param permissionsHelper an instance of {@link PermissionsHelper}
     * @param user a pointer to the logged in user
     * @return all the volumes and snapshots that have been exported to the
     *         initiators together with the target ports and zone name when
     *         zoning has been performed.
     */
    public static ITLRestRepList getInitiatorsItls(List<String> networkPorts, DbClient dbClient,
            PermissionsHelper permissionsHelper, StorageOSUser user) throws DatabaseException {
        ITLRestRepList list = new ITLRestRepList();
        List<String> invalidNetworkPorts = new ArrayList<String>();
        Map<String, Initiator> initiators = new HashMap<String, Initiator>();
        // get the initiators
        for (String networkPort : networkPorts) {
            Initiator initiator = null;
            if (initiators.containsKey(networkPort)) {
                continue;
            }
            initiator = getInitiator(networkPort, dbClient);
            if (initiator == null) {
                invalidNetworkPorts.add(networkPort);
            } else {
                initiators.put(networkPort, initiator);
            }
        }
        // check all the initiators exist and are active
        if (!invalidNetworkPorts.isEmpty()) {
            _log.warn("Could not find active initiator for the following initiator ports {}", invalidNetworkPorts);
        }
        for (Initiator initiator : initiators.values()) {
            list.getExportList().addAll(getItlsForInitiator(initiator, dbClient,
                    permissionsHelper, user).getExportList());
        }
        _log.info("{} ITLs were found for the request.", list.getExportList().size());
        return list;
    }

    /**
     * Gets the list of exports (ITL) for one initiator. The list contains all the volumes and snapshots
     * that are exported to the initiator together with the target ports and the zones when zoning
     * was performed.
     *
     * @param initiator the initiator
     * @param dbClient an instance of {@link DbClient}
     * @param permissionsHelper an instance of {@link PermissionsHelper}
     * @param user a pointer to the logged in user
     * @return the list of ITLs for one initiator
     */
    public static ITLRestRepList getItlsForInitiator(Initiator initiator, DbClient dbClient,
            PermissionsHelper permissionsHelper, StorageOSUser user) throws DatabaseException {
        ITLRestRepList list = new ITLRestRepList();
        Map<ExportMask, List<ExportGroup>> exportMasks = null;
        exportMasks = getInitiatorExportMasks(initiator, dbClient, permissionsHelper, user);
        BlockObject blockObject = null;
        List<StoragePort> ports = null;
        List<StoragePort> initiatorPorts = null;
        Map<StoragePort, List<FCZoneReference>> zoneRefs = null;
        String hlu = "";
        Map<String, BlockObject> blockObjects = getBlockObjectsForMasks(exportMasks.keySet(), dbClient);
        for (ExportMask exportMask : exportMasks.keySet()) {
            _log.info("Finding ITLs for initiator {} in export mask {}", initiator.getInitiatorPort(), exportMask.getMaskName());
            ports = getStoragePorts(exportMask, dbClient);
            initiatorPorts = getInitiatorPorts(exportMask, initiator, ports, dbClient);
            zoneRefs = getInitiatorsZoneReferences(initiator, initiatorPorts, dbClient);
            for (String doUri : exportMask.getVolumes().keySet()) {
                hlu = exportMask.getVolumes().get(doUri);
                blockObject = blockObjects.get(doUri);
                if (blockObject != null) {
                    list.getExportList().addAll(getItlsForMaskInitiator(dbClient,
                            exportMasks.get(exportMask), exportMask, initiator, hlu, blockObject, initiatorPorts, zoneRefs));
                }
            }
        }
        _log.info("{} ITLs were found for initiator {}.", list.getExportList().size(), initiator.getInitiatorPort());
        return list;
    }

    private static Map<String, BlockObject> getBlockObjectsForMasks(Collection<ExportMask> exportMasks, DbClient dbClient) {
        Map<String, BlockObject> blockObjects = new HashMap<String, BlockObject>();
        List<URI> masksURIs = getMaskVolumeURI(exportMasks);
        List<URI> volUris = new ArrayList<URI>();
        List<URI> snapUris = new ArrayList<URI>();
        for (URI uri : masksURIs) {
            if (URIUtil.isType(uri, BlockSnapshot.class)) {
                snapUris.add(uri);
            } else if (URIUtil.isType(uri, Volume.class)) {
                volUris.add(uri);
            }
        }
        Iterator<Volume> vols = dbClient.queryIterativeObjects(Volume.class, volUris, true);
        while (vols.hasNext()) {
            Volume vol = vols.next();
            blockObjects.put(vol.getId().toString(), vol);
        }
        Iterator<BlockSnapshot> snaps = dbClient.queryIterativeObjects(BlockSnapshot.class, snapUris, true);
        while (snaps.hasNext()) {
            BlockSnapshot snap = snaps.next();
            blockObjects.put(snap.getId().toString(), snap);
        }
        return blockObjects;
    }

    private static List<URI> getMaskVolumeURI(Collection<ExportMask> exportMasks) {
        List<URI> uris = new ArrayList<URI>();
        for (ExportMask exportMask : exportMasks) {
            Collection<String> strUris = exportMask.getVolumes().keySet();
            for (String strUri : strUris) {
                URI uri = URI.create(strUri);
                if (!uris.contains(uri)) {
                    uris.add(uri);
                }
            }
        }
        return uris;
    }

    /**
     * Get an initiator as specified by the initiator's network port.
     *
     * @param networkPort The initiator's port WWN or IQN.
     * @return A reference to an initiator.
     */
    private static Initiator getInitiator(String networkPort, DbClient dbClient) {
        Initiator initiator = null;
        URIQueryResultList resultsList = new URIQueryResultList();

        // find the initiator
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(
                networkPort), resultsList);
        Iterator<URI> resultsIter = resultsList.iterator();
        while (resultsIter.hasNext()) {
            initiator = dbClient.queryObject(Initiator.class, resultsIter.next());
            // there should be one initiator, so return as soon as it is found
            if (initiator != null && !initiator.getInactive()) {
                return initiator;
            }
        }
        return null;
    }

    /**
     * For a volume/snapshot-initiator pair find the target ports for the initiator in the list of
     * ports and find the zone name if zoning was performed. Return a list of ITLs for
     * each volume-initiator-target found.
     *
     * @param dbClient db client
     * @param exportMask the export mask the initiator is in
     * @param initiator the initiator
     * @param hlu the lun Id used by the initiator for the volume
     * @param blockObject the volume or snapshot
     * @param initiatorPorts ports to which the initiator is zoned in the export mask
     * @param zoneRefs a map of port-to-zone-reference
     * @param exportGroup the export groups in this export mask
     * @return all ITLs for a volume/snapshot-initiator pair.
     */
    private static List<ITLRestRep> getItlsForMaskInitiator(
            DbClient dbClient, List<ExportGroup> exportGroups,
            ExportMask exportMask, Initiator initiator, String hlu,
            BlockObject blockObject, List<StoragePort> initiatorPorts, Map<StoragePort, List<FCZoneReference>> zoneRefs) {
        List<ITLRestRep> list = new ArrayList<ITLRestRep>();
        Map<StoragePort, FCZoneReference> initiatorZoneRefs = null;

        // Find the block object that would appear in the Export Mask
        BlockObject bo = Volume.fetchExportMaskBlockObject(dbClient, blockObject.getId());
        if (bo != null) {
            _log.debug("Finding target ports for initiator {} and block object {}",
                    initiator.getInitiatorPort(), bo.getNativeGuid());
            initiatorZoneRefs = getZoneReferences(bo.getId(), initiator, initiatorPorts, zoneRefs);
            _log.debug("{} target ports and {} SAN zones were found for initiator {} and block object {}",
                    new Object[] { initiatorPorts.size(), initiatorZoneRefs.size(),
                            initiator.getInitiatorPort(), bo.getNativeGuid() });
            // TODO - Should we add special handling of iscsi initiators?
            for (ExportGroup exportGroup : exportGroups) {
                if (exportGroup.getVolumes() != null &&
                        exportGroup.getVolumes().containsKey(blockObject.getId().toString()) &&
                        exportGroup.getInitiators() != null &&
                        exportGroup.getInitiators().contains(initiator.getId().toString())) {

                    // We want to check if there are any ports in this export group for this initiator
                    List<StoragePort> portsInExportGroupVarray = filterPortsInVarray(
                            exportGroup, exportMask.getStorageDevice(), initiatorPorts);
                    if (!portsInExportGroupVarray.isEmpty()) {
                        for (StoragePort port : portsInExportGroupVarray) {
                            list.add(createInitiatorTargetRefRep(exportGroup, blockObject, hlu,
                                    initiator, port, initiatorZoneRefs.get(port)));
                        }
                    } else {
                        list.add(createInitiatorTargetRefRep(exportGroup, blockObject, hlu,
                                initiator, null, null));
                    }
                }
            }
        }
        return list;
    }

    private static List<StoragePort> filterPortsInVarray(ExportGroup exportGroup,
            URI storageSystemUri, List<StoragePort> initiatorPorts) {
        List<StoragePort> ports = new ArrayList<StoragePort>();
        String varray = exportGroup.getVirtualArray().toString();
        String altVarrayUri = null;
        if (exportGroup.getAltVirtualArrays() != null && exportGroup.getAltVirtualArrays().containsKey(storageSystemUri.toString())) {
            altVarrayUri = exportGroup.getAltVirtualArrays().get(storageSystemUri.toString());
            _log.debug("Found an alternative varray {}", altVarrayUri);
        }
        for (StoragePort port : initiatorPorts) {
            if (port.getTaggedVirtualArrays() != null) {
                if (port.getTaggedVirtualArrays().contains(varray)) {
                    _log.debug("Port {} was found to be in varray {}", port.getPortNetworkId(), varray);
                    ports.add(port);
                } else if (altVarrayUri != null && port.getTaggedVirtualArrays().contains(altVarrayUri)) {
                    _log.debug("Port {} was found to be in alternative varray {}", port.getPortNetworkId(), altVarrayUri);
                    ports.add(port);
                }
            } else {
                _log.debug("Port {} was not found in the export group varray or alternate varray", port.getPortNetworkId());
            }
        }
        return ports;
    }

    /**
     * Find the block object, volume or snapshot, for a given URI.
     *
     * @param id the URN of a ViPR block object, volume or snapshot
     * @param dbClient dbClient an instance of {@link DbClient}
     * @return the volume or snapshot for the given URI
     */
    private static BlockObject getBlockObject(URI id, DbClient dbClient) {
        BlockObject blockObject = BlockObject.fetch(dbClient, id);
        if (blockObject != null && !blockObject.getInactive()) {
            return blockObject;
        }
        return null;
    }

    /**
     * Return the ports that were zoned to an initiator according to the zoningMap.
     *
     * @param initiator - Initiator
     * @param ports - List of ports in the ExportMask
     * @param zoningMap - zoningMap in the ExportMask
     * @return
     */
    private static List<StoragePort> getZonedPorts(Initiator initiator,
            List<StoragePort> ports, StringSetMap zoningMap) {
        List<StoragePort> zonedPorts = new ArrayList<StoragePort>();
        if (zoningMap == null || zoningMap.isEmpty()) {
            return zonedPorts;
        }
        StringSet portSet = zoningMap.get(initiator.getId().toString());
        if (portSet != null) {
            for (StoragePort port : ports) {
                if (portSet.contains(port.getId().toString())) {
                    zonedPorts.add(port);
                }
            }
        }
        return zonedPorts;
    }

    /**
     * Creates and returns an instance of REST response object.
     *
     * @param exportGroup the export group the initiator is in
     * @param blockObject the block object
     * @param hlu the lun id used for the block object by the initiator
     * @param initiator the initiator
     * @param port the target port
     * @param fcZoneReference the record of the SAN zone created on the switch when a zone is created.
     *            Returns null when zoning is not required or when the zone creation failed.
     * @return the export REST response object.
     */
    private static ITLRestRep createInitiatorTargetRefRep(ExportGroup exportGroup,
            BlockObject blockObject, String hlu, Initiator initiator,
            StoragePort port, FCZoneReference fcZoneReference) {
        ITLRestRep rep = new ITLRestRep();
        rep.setHlu(Integer.parseInt(hlu));

        // Set block object
        ITLRestRep.ITLBlockObjectRestRep blockObjectRestRep = new ITLRestRep.ITLBlockObjectRestRep();
        blockObjectRestRep.setId(blockObject.getId());
        blockObjectRestRep.setLink(new RestLinkRep("self", RestLinkFactory.newLink(blockObject)));
        blockObjectRestRep.setWwn(getBlockObjectFormattedWWN(blockObject));
        rep.setBlockObject(blockObjectRestRep);

        // Set initiator
        ITLRestRep.ITLInitiatorRestRep initiatorRestRep = new ITLRestRep.ITLInitiatorRestRep();
        initiatorRestRep.setId(initiator.getId());
        initiatorRestRep.setLink(new RestLinkRep("self", RestLinkFactory.newLink(initiator)));
        initiatorRestRep.setPort(initiator.getInitiatorPort());
        rep.setInitiator(initiatorRestRep);

        // Set storage port
        ITLRestRep.ITLStoragePortRestRep storagePortRestRep = new ITLRestRep.ITLStoragePortRestRep();
        if (port != null) {
            storagePortRestRep.setId(port.getId());
            storagePortRestRep.setLink(new RestLinkRep("self", RestLinkFactory.newLink(port)));
            storagePortRestRep.setPort(port.getPortNetworkId());
            if (port.getIpAddress() != null) {
                storagePortRestRep.setIpAddress(port.getIpAddress());
                storagePortRestRep.setTcpPort(String.valueOf(port.getTcpPortNumber()));
            }
        }
        rep.setStoragePort(storagePortRestRep);

        // Export
        rep.setExport(toNamedRelatedResource(exportGroup, exportGroup.getLabel()));
        if (fcZoneReference != null) {
            rep.setSanZoneName(fcZoneReference.getZoneName());
        }
        return rep;
    }

    private static String getBlockObjectFormattedWWN(BlockObject blockObject) {
        if (blockObject.getWWN() == null) {
            return null;
        }
        return blockObject.getWWN().toUpperCase();
    }

    /**
     * Find the san zone information for the initiator and storage ports. Returns
     * a map of zone references per port.
     *
     * @param initiator the initiator
     * @param ports the target ports
     * @param dbClient an instance of {@link DbClient}
     * @return a map of san zones created for the initiator grouped by port for
     *         the list of target ports. Otherwise, an returns empty map.
     */
    private static Map<StoragePort, List<FCZoneReference>> getInitiatorsZoneReferences(Initiator initiator,
            List<StoragePort> ports, DbClient dbClient) {
        Map<StoragePort, List<FCZoneReference>> targetPortReferences = new HashMap<StoragePort, List<FCZoneReference>>();
        if (initiator.getProtocol().equals(Block.FC.name())) {
            List<FCZoneReference> refs = null;
            for (StoragePort port : ports) {
                String key = FCZoneReference.makeEndpointsKey(
                        Arrays.asList(initiator.getInitiatorPort(), port.getPortNetworkId()));
                refs = new ArrayList<FCZoneReference>();
                targetPortReferences.put(port, refs);
                URIQueryResultList queryList = new URIQueryResultList();
                dbClient.queryByConstraint(AlternateIdConstraint.Factory.getFCZoneReferenceKeyConstraint(key), queryList);
                Iterator<FCZoneReference> refsUris = dbClient.queryIterativeObjects(FCZoneReference.class, iteratorToList(queryList));
                FCZoneReference ref = null;
                while (refsUris.hasNext()) {
                    ref = refsUris.next();
                    if (ref != null && !ref.getInactive()) {
                        refs.add(ref);
                    }
                }
            }
        }
        return targetPortReferences;
    }

    /**
     * Find the san zone information for the initiator/block object.
     *
     * @param blockObjectUri the block object URI
     * @param initiator the initiator
     * @param ports the target ports
     * @param refs a map of port-to-zone-reference
     * @return the list of san zones created for the initiator if any were created. Otherwise, an returns empty map.
     */
    private static Map<StoragePort, FCZoneReference> getZoneReferences(URI blockObjectUri,
            Initiator initiator, List<StoragePort> ports, Map<StoragePort, List<FCZoneReference>> refs) {
        Map<StoragePort, FCZoneReference> targetPortReferences = new HashMap<StoragePort, FCZoneReference>();
        if (initiator.getProtocol().equals(Block.FC.name())) {
            for (StoragePort port : ports) {
                for (FCZoneReference ref : refs.get(port)) {
                    if (ref != null && !ref.getInactive() && blockObjectUri.equals(ref.getVolumeUri())) {
                        targetPortReferences.put(port, ref);
                        break; // there should be one only
                    }
                }
            }
        }
        _log.debug("Found {} san zone references for initiator {} and block object {}",
                new Object[] { targetPortReferences.size(), initiator.getInitiatorPort(), blockObjectUri });
        return targetPortReferences;
    }

    /**
     * Find the san zone information for the initiator and storage ports. Returns
     * a map of zone references per port.
     *
     * @param initiator the initiator
     * @param ports the target ports
     * @param bo block object
     * @param dbClient an instance of {@link DbClient}
     * @return a map of san zones created for the initiator grouped by port for
     *         the list of target ports. Otherwise, an returns empty map.
     */
    private static Map<StoragePort, List<FCZoneReference>> getInitiatorsZoneReferencesForBlockObject(
            Initiator initiator,
            List<StoragePort> ports, BlockObject bo, DbClient dbClient) {
        Map<StoragePort, List<FCZoneReference>> targetPortReferences = new HashMap<StoragePort, List<FCZoneReference>>();
        if (initiator.getProtocol().equals(Block.FC.name())) {
            List<FCZoneReference> refs = null;
            for (StoragePort port : ports) {
                String key = FCZoneReference.makeLabel(
                        Arrays.asList(initiator.getInitiatorPort(), port.getPortNetworkId(),
                                bo.getId().toString()));
                refs = new ArrayList<FCZoneReference>();
                targetPortReferences.put(port, refs);
                URIQueryResultList queryList = new URIQueryResultList();
                dbClient.queryByConstraint(PrefixConstraint.Factory.getLabelPrefixConstraint(FCZoneReference.class, key),
                        queryList);

                while (queryList.iterator().hasNext()) {
                    FCZoneReference ref = dbClient.queryObject(FCZoneReference.class, queryList.iterator().next());
                    if (ref != null && !ref.getInactive()) {
                        refs.add(ref);
                    }
                }
            }
        }
        return targetPortReferences;
    }

    /**
     * Fetches and returns the storage ports for an export mask
     *
     * @param exportMask the export mask
     * @param dbClient an instance of {@link DbClient}
     * @return a list of active storage ports used by the export mask
     */
    private static List<StoragePort> getStoragePorts(ExportMask exportMask, DbClient dbClient) {
        List<StoragePort> ports = new ArrayList<StoragePort>();
        StoragePort port = null;
        if (exportMask.getStoragePorts() != null) {
            for (String initUri : exportMask.getStoragePorts()) {
                port = dbClient.queryObject(StoragePort.class, URI.create(initUri));
                if (port != null && !port.getInactive()) {
                    ports.add(port);
                }
            }
        }
        _log.debug("Found {} stoarge ports in export mask {}", ports.size(), exportMask.getMaskName());
        return ports;
    }

    /**
     * Fetches and returns the initiators for an export mask. If the ExportMask's
     * existing initiators are set, they will also be returned if an instance can
     * be found in ViPR for the given initiator port id.
     *
     * @param exportMask the export mask
     * @param dbClient an instance of {@link DbClient}
     * @return a list of active initiators in the export mask
     */
    private static Collection<Initiator> getInitiators(ExportMask exportMask, DbClient dbClient) {
        Map<String, Initiator> initiators = new HashMap<String, Initiator>();
        Initiator initiator = null;
        if (exportMask.getInitiators() != null) {
            for (String initUri : exportMask.getInitiators()) {
                initiator = dbClient.queryObject(Initiator.class, URI.create(initUri));
                if (initiator != null && !initiator.getInactive()) {
                    initiators.put(initiator.getInitiatorPort(), initiator);
                }
            }
        }
        if (exportMask.getExistingInitiators() != null &&
                !exportMask.getExistingInitiators().isEmpty()) {
            for (String initStr : exportMask.getExistingInitiators()) {
                if (!initiators.containsKey(initStr)) {
                    initStr = Initiator.toPortNetworkId(initStr);
                    Initiator init = getInitiator(initStr, dbClient);
                    if (init != null) {
                        initiators.put(initStr, init);
                    }
                }
            }
        }
        return initiators.values();
    }

    /**
     * Fetches all the export masks in which a block object is member
     *
     * @param blockObject the block object
     * @param dbClient an instance of {@link DbClient}
     * @return a map of export masks in which a block object is member
     */
    private static Map<ExportMask, List<ExportGroup>> getBlockObjectExportMasks(BlockObject blockObject,
            DbClient dbClient) {
        Map<ExportMask, List<ExportGroup>> exportMasks = new HashMap<ExportMask, List<ExportGroup>>();
        ContainmentConstraint constraint = ContainmentConstraint.Factory.getBlockObjectExportGroupConstraint(blockObject.getId());
        // permission are checked by the API service - no need to check again
        List<ExportGroup> exportGroups = getExportGroupsByConstraint(constraint, dbClient, null, null);
        List<ExportMask> masks = getMasksForExportGroups(exportGroups, dbClient);

        // Get the actual export block object associated with the snapshot (if applicable)
        BlockObject bo = Volume.fetchExportMaskBlockObject(dbClient, blockObject.getId());
        if (bo != null) {
            for (ExportMask exportMask : masks) {
                if (exportMask != null && !exportMask.getInactive()
                        && exportMask.hasVolume(bo.getId())
                        && (exportMask.getInitiators() != null
                                || exportMask.getExistingInitiators() != null)) {
                    List<ExportGroup> maskGroups = new ArrayList<ExportGroup>();
                    exportMasks.put(exportMask, maskGroups);
                    for (ExportGroup group : exportGroups) {
                        if (group.getExportMasks().contains(exportMask.getId().toString())) {
                            maskGroups.add(group);
                        }
                    }
                }
            }
            _log.debug("Found {} export masks for block object {}", exportMasks.size(), bo.getLabel());
        }
        return exportMasks;
    }

    /**
     * Gets all the export masks that this initiator is member of.
     *
     * @param initiator the initiator
     * @param dbClient an instance of {@link DbClient}
     * @param permissionsHelper an instance of {@link PermissionsHelper}
     * @param user a pointer to the logged in user
     * @return all the export masks that this initiator is member of
     */
    private static Map<ExportMask, List<ExportGroup>> getInitiatorExportMasks(Initiator initiator,
            DbClient dbClient, PermissionsHelper permissionsHelper, StorageOSUser user) throws DatabaseException {
        Map<ExportMask, List<ExportGroup>> exportMasks = new HashMap<ExportMask, List<ExportGroup>>();
        AlternateIdConstraint constraint = AlternateIdConstraint.Factory.getExportGroupInitiatorConstraint(initiator.getId().toString());
        List<ExportGroup> exportGroups = getExportGroupsByConstraint(constraint, dbClient, permissionsHelper, user);
        List<ExportMask> masks = getMasksForExportGroups(exportGroups, dbClient);
        for (ExportMask exportMask : masks) {
            if (exportMask != null &&
                    !exportMask.getInactive() &&
                    (exportMask.hasInitiator(initiator.getId().toString()) ||
                            (exportMask.hasExistingInitiator(initiator)))
                    &&
                    exportMask.getVolumes() != null) {
                List<ExportGroup> maskGroups = new ArrayList<ExportGroup>();
                exportMasks.put(exportMask, maskGroups);
                for (ExportGroup group : exportGroups) {
                    if (group.getExportMasks().contains(exportMask.getId().toString())) {
                        maskGroups.add(group);
                    }
                }
            }
        }
        _log.info("Found {} export masks for initiator {}", exportMasks.size(), initiator.getInitiatorPort());
        return exportMasks;
    }

    private static List<URI> iteratorToList(URIQueryResultList itr) {
        List<URI> uris = new ArrayList<URI>();
        for (URI uri : itr) {
            uris.add(uri);
        }
        return uris;
    }

    private static List<ExportMask> getMasksForExportGroups(List<ExportGroup> exportGroups,
            DbClient dbClient) {
        List<URI> maskUris = new ArrayList<URI>();
        for (ExportGroup exportGroup : exportGroups) {
            List<URI> uris = StringSetUtil.stringSetToUriList(exportGroup.getExportMasks());
            for (URI uri : uris) {
                if (!maskUris.contains(uri)) {
                    maskUris.add(uri);
                }
            }
        }
        return dbClient.queryObject(ExportMask.class, maskUris);
    }

    private static List<ExportGroup> getExportGroupsByConstraint(Constraint constraint,
            DbClient dbClient, PermissionsHelper permissionsHelper, StorageOSUser user) {
        List<ExportGroup> exportGroups = new ArrayList<ExportGroup>();
        URIQueryResultList egUris = new URIQueryResultList();
        dbClient.queryByConstraint(constraint, egUris);
        List<ExportGroup> queryExportGroups = dbClient.queryObject(ExportGroup.class, iteratorToList(egUris));
        for (ExportGroup exportGroup : queryExportGroups) {
            if (exportGroup == null || exportGroup.getInactive() || exportGroup.getExportMasks() == null
                    || !checkUserPermissions(exportGroup, permissionsHelper, user)) {
                continue;
            }
            exportGroups.add(exportGroup);
        }
        return exportGroups;
    }

    /**
     * Gets all the export masks that this initiator is member of.
     *
     * @param networkPort The initiator's port WWN or IQN.
     * @param dbClient an instance of {@link DbClient}
     * @param permissionsHelper an instance of {@link PermissionsHelper}
     * @param user a pointer to the logged in user
     * @return all the export masks that this initiator is member of
     */
    public static Map<ExportMask, List<ExportGroup>> getInitiatorExportMasks(
            String networkPort, DbClient dbClient,
            PermissionsHelper permissionsHelper, StorageOSUser user) throws DatabaseException {
        Initiator initiator = getInitiator(networkPort, dbClient);
        if (initiator != null) {
            return getInitiatorExportMasks(initiator, dbClient, permissionsHelper, user);
        }
        return Collections.emptyMap();
    }

    /**
     * Checks if the user has permission to see the exports for a given export groups.
     * The user has permissions if
     * <ul>
     * <li>The user has SYSTEM_ADMIN or SYSTEM_MONITOR role, or</li>
     * <li>The user is a TENANT_ADMIN or TENANT_MONITOR for the tenant org, or</li>
     * <li>The user has any permission to the export group's project.</li>
     * </ul>
     *
     * @param group the export group
     * @param permissionsHelper a reference to {@link PermissionsHelper}
     * @param user the user
     * @return true if the user has at least read permissions to the export group
     * @throws DatabaseException when a DB error occurs
     */
    private static boolean checkUserPermissions(ExportGroup group,
            PermissionsHelper permissionsHelper, StorageOSUser user) throws DatabaseException {
        // if the user or helper is null, assume permission checks are not required
        if (permissionsHelper == null || user == null) {
            return true;
        }
        // full list if role is {Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR}
        if (permissionsHelper.userHasGivenRole(user, null, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN)) {
            return true;
        } else {
            // otherwise, filter by only authorized to use
            Project project = permissionsHelper.getObjectById(group.getProject().getURI(), Project.class);
            return project != null &&
                    (permissionsHelper.userHasGivenRole(user,
                            project.getTenantOrg().getURI(), Role.TENANT_ADMIN) ||
                            permissionsHelper.userHasGivenACL(user, project.getId(), ACL.ANY));
        }
    }

}
