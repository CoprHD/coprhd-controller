/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.DbModelClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.StorageProtocol.Transport;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.ZoneInfo;
import com.emc.storageos.db.client.model.ZoneInfoMap;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.DataObjectUtils;
import com.emc.storageos.db.client.util.ExportMaskNameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringMapUtil;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.db.joiner.Joiner;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.networkcontroller.impl.NetworkScheduler;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.net.util.IPAddressUtil;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class ExportMaskUtils {
    private static final Logger _log = LoggerFactory.getLogger(ExportMaskUtils.class);

    private static final ExportMaskNameGenerator nameGenerator = new ExportMaskNameGenerator();

    /**
     * Look up export mask for the storage array in the exports.
     * This method is not suitable for VNX/VMAX because an ExportGroup can now have multiple
     * ExportMasks for the same Storage System (one for each different host).
     * It is still used for VPlex.
     * 
     * @param exportGroup export mask belongs to this export group
     * @param sdUri export mask is on this storage system
     * @return
     * @throws DatabaseException
     * 
     */
    public static ExportMask getExportMask(DbClient dbClient, ExportGroup exportGroup, URI sdUri)
            throws DatabaseException {
        if (exportGroup.getExportMasks() == null ||
                exportGroup.getExportMasks().isEmpty()) {
            return null;
        }

        // At this point, exportGroup.exportMasks should be non-null and non-empty,
        // so try to find the ExportMask that has the same StorageSystem URI
        ExportMask foundExportMask = null;
        for (String maskUriStr : exportGroup.getExportMasks()) {
            URI maskUri = URI.create(maskUriStr);
            ExportMask mask = dbClient.queryObject(ExportMask.class, maskUri);
            if (mask != null && mask.getStorageDevice().equals(sdUri)) {
                foundExportMask = mask;
                break;
            }
        }
        return foundExportMask;
    }

    /**
     * Returns a list of ExportMasks from an ExportGroup that are for a specified storage-system.
     * 
     * @param dbClient - database client.
     * @param exportGroup - the ExportGroup to be examined
     * @param ssysURI - the StorageSystem URI; if NULL returns ALL ExportMasks
     * @return List<ExportMask> -- an empty list is returned if there are no matches.
     */
    public static List<ExportMask> getExportMasks(DbClient dbClient, ExportGroup exportGroup, URI ssysURI) {
        List<ExportMask> returnMasks = new ArrayList<ExportMask>();
        if (exportGroup.getExportMasks() == null ||
                exportGroup.getExportMasks().isEmpty()) {
            return returnMasks;
        }
        for (String maskUriStr : exportGroup.getExportMasks()) {
            URI maskUri = URI.create(maskUriStr);
            ExportMask mask = dbClient.queryObject(ExportMask.class, maskUri);
            if (mask == null) {
                continue;
            }
            if (ssysURI == null || mask.getStorageDevice().equals(ssysURI)) {
                returnMasks.add(mask);
            }
        }
        return returnMasks;
    }

    /**
     * Returns all ExportMasks in an ExportGroup.
     * 
     * @param dbClient
     * @param exportGroup
     * @return
     */
    public static List<ExportMask> getExportMasks(DbClient dbClient, ExportGroup exportGroup) {
        return getExportMasks(dbClient, exportGroup, null);
    }

    /**
     * Find all export groups that are referencing the export mask
     * 
     * @param dbClient db client
     * @param exportMask export mask
     * @return list of export groups referring to the export mask
     */
    public static List<ExportGroup> getExportGroups(DbClient dbClient, ExportMask exportMask) {
        URIQueryResultList exportGroupURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint
                .Factory.getExportMaskExportGroupConstraint(exportMask.getId()), exportGroupURIs);
        List<ExportGroup> exportGroups = new ArrayList<ExportGroup>();
        for (URI egURI : exportGroupURIs) {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, egURI);
            if (exportGroup == null || exportGroup.getInactive() == true) {
                continue;
            }
            exportGroups.add(exportGroup);
        }
        return exportGroups;
    }

    public static String getExportType(DbClient dbClient, ExportMask exportMask) {
        String maskResource = exportMask.getResource();

        if (!NullColumnValueGetter.isNullValue(maskResource)) {
            if (maskResource.contains("urn:storageos:Host")) {
                return ExportGroupType.Host.name();
            } else {
                return ExportGroupType.Cluster.name();
            }
        } else {
            // handle the case of brownfield scenario where the resource will not be set in the mask
            List<ExportGroup> groups = getExportGroups(dbClient, exportMask);
            // Applies only for VMAX where even if the export mask may belong to more than one export group,
            // they all should be of the same type.
            if (!groups.isEmpty()) {
                return groups.get(0).getType();
            }
        }

        return null;
    }

    /**
     * Return all initiators, existing and user added, associated with this mask.
     * 
     * @param dbClient db client
     * @param mask export mask
     * @return set of initiator URIs
     */
    public static Set<URI> getAllInitiatorsForExportMask(DbClient dbClient, ExportMask mask) {
        Set<URI> initiators = new HashSet<>();
        for (Initiator existingInitiator : getExistingInitiatorsForExportMask(dbClient, mask, null)) {
            initiators.add(existingInitiator.getId());
        }
        if (mask.getInitiators() != null) {
            for (String initiatorId : mask.getInitiators()) {
                initiators.add(URI.create(initiatorId));
            }
        }
        if (mask.getUserAddedInitiators() != null) {
            for (String initiatorId : mask.getUserAddedInitiators().values()) {
                initiators.add(URI.create(initiatorId));
            }
        }
        return initiators;
    }

    /**
     * Returns all Initiators of the specified Transport type in the ExportMask.
     * 
     * @param dbClient
     * @param exportMask -- ExportMask to be examined
     * @param transportType Transport enum typically Transport.FC
     * @return Set<Initiator>
     */
    public static Set<Initiator> getInitiatorsForExportMask(DbClient dbClient,
            ExportMask exportMask, Transport transportType) {
        Set<Initiator> initiators = new HashSet<Initiator>();
        if (exportMask.getInitiators() != null) {
            for (String initiatorId : exportMask.getInitiators()) {
                Initiator initiator = dbClient.queryObject(
                        Initiator.class, URI.create(initiatorId));
                if (initiator == null) {
                    continue;
                }
                if (transportType == null
                        || transportType == StorageProtocol.block2Transport(initiator.getProtocol())) {
                    initiators.add(initiator);
                }
            }
        }
        return initiators;
    }

    /**
     * Get the Initiators that correspond to the addresses in an ExportMask existingInitiators
     * field. (These will have no entries in ExportMask.initiators.)
     * 
     * @param dbClient -- Database client.
     * @param exportMask -- An existing ExportMask
     * @param transportType -- Transport type, e.g. FC
     * @return Set<Inititator> of initiators having same address as those in ExportMask.existingInitiators
     */
    public static Set<Initiator> getExistingInitiatorsForExportMask(DbClient dbClient,
            ExportMask exportMask, Transport transportType) {
        Set<Initiator> initiators = new HashSet<Initiator>();
        if (exportMask.getExistingInitiators() != null) {
            for (String address : exportMask.getExistingInitiators()) {
                String normalizedAddress = Initiator.toPortNetworkId(address);
                URIQueryResultList result = new URIQueryResultList();
                dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(normalizedAddress), result);
                for (URI uri : result) {
                    Initiator initiator = dbClient.queryObject(Initiator.class, uri);
                    if (initiator != null && !initiator.getInactive()) {
                        if (transportType == null || transportType == StorageProtocol.block2Transport(initiator.getProtocol())) {
                            initiators.add(initiator);
                        }
                    }
                }
            }
        }
        return initiators;
    }

    /**
     * Return all the StoragePorts in an ExportMask of a specificed Transport type.
     * 
     * @param dbClient
     * @param exportMask
     * @param transportType
     * @return
     */
    public static Set<StoragePort> getPortsForExportMask(DbClient dbClient,
            ExportMask exportMask, Transport transportType) {
        Set<StoragePort> ports = new HashSet<StoragePort>();
        if (exportMask.getStoragePorts() == null) {
            return ports;
        }

        for (String portId : exportMask.getStoragePorts()) {
            StoragePort port = dbClient.queryObject(StoragePort.class, URI.create(portId));
            if (port == null) {
                continue;
            }
            if (transportType == Transport.valueOf(port.getTransportType())) {
                ports.add(port);
            }
        }
        return ports;
    }

    /**
     * Return the StoragePort ids for an Export Mask of a specificed Transport type.
     * 
     * @param dbClient
     * @param exportMask
     * @param transportType
     * @return
     */
    public static Set<String> getPortIdsForExportMask(DbClient dbClient,
            ExportMask exportMask, Transport transportType) {
        Set<String> portIds = new HashSet<String>();
        Set<StoragePort> ports = getPortsForExportMask(dbClient, exportMask, transportType);
        for (StoragePort port : ports) {
            portIds.add(port.getId().toString());
        }
        return portIds;
    }

    /**
     * Create an export mask object. The actual mask is created at the array by the controller service.
     * 
     * @param dbClient db client
     * @param exportGroup export group
     * @param sdUri storage device ID
     * @param maskName name to give the mask.
     * @return Export Mask object
     */
    public static ExportMask createExportMask(DbClient dbClient, ExportGroup exportGroup, URI sdUri, String maskName)
            throws DatabaseException {
        ExportMask exportMask = new ExportMask();

        exportMask.setId(URIUtil.createId(ExportMask.class));
        exportMask.setMaskName(maskName);
        exportMask.setStorageDevice(sdUri);
        dbClient.createObject(exportMask);

        exportGroup.addExportMask(exportMask.getId());
        dbClient.updateAndReindexObject(exportGroup);

        return exportMask;
    }

    public static boolean hasExportMaskForStorage(DbClient dbClient,
            ExportGroup exportGroup,
            URI storageURI) {
        StringSet maskUriSet = exportGroup.getExportMasks();
        if (maskUriSet != null) {
            for (String maskUriString : maskUriSet) {
                ExportMask mask = dbClient.queryObject(ExportMask.class,
                        URI.create(maskUriString));
                URI maskStorageURI = mask.getStorageDevice();
                if (maskStorageURI.equals(storageURI)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Generate a name for the export mask based on the initiators sent in.
     * If there are no initiators, just use the generated export group name.
     * 
     * It needs to be guaranteed that initiators in the list all belong to
     * one host.
     * 
     * 
     * @param dbClient database handle
     * @param initiators initiators that are going to be used
     * @param exportGroup export group to get generated name from
     * @param storageSystem [in] - StorageSystem object
     * @return a string to use for the name
     */
    public static String getMaskName(DbClient dbClient, List<Initiator> initiators, ExportGroup exportGroup,
            StorageSystem storageSystem) {
        // Critical assertion
        if (exportGroup == null) {
            return null; // You've got bigger problems than the name. ;)
        }

        // Important assertions
        if (initiators == null || dbClient == null || initiators.isEmpty()) {
            return exportGroup.getGeneratedName();
        }

        // Create a set of unique host and cluster names
        Set<String> hosts = new HashSet<String>();
        Set<String> clusters = new HashSet<String>();
        for (Initiator initiator : initiators) {
            String host = initiator.getHostName();
            if (host != null) {
                String fixedHost = host;
                // In case this is a hostname, extract the first part,
                // which generally is the unique part of the hostname
                if (!IPAddressUtil.isIPv4LiteralAddress(host) &&
                        !IPAddressUtil.isIPv6LiteralAddress(host)) {
                    int firstDot = host.indexOf('.');
                    if (firstDot != -1) {
                        fixedHost = host.substring(0, firstDot);
                    }
                }
                hosts.add(fixedHost);
            }
            String cluster = initiator.getClusterName();
            if (cluster != null) {
                clusters.add(cluster);
            }
        }

        // Attempt to generate a unique name based on the sets that were generated.
        // First start with the ExportGroup.label
        String cluster = null;
        // If there is unique cluster name, append to the name
        if (clusters.size() == 1) {
            cluster = clusters.iterator().next();
        }

        String host = null;
        // If there is a unique host name, append to the name
        if (hosts.size() == 1) {
            host = hosts.iterator().next();
        }

        // Get the last 3 digits of the array serial number and append it
        // to the name. Where we append the name depends on whether the
        // cluster and/or host Strings are filled in
        String serialNo = storageSystem.getSerialNumber();
        String lastDigitsOfSerialNo = serialNo.substring(serialNo.length() - 3);
        // Append SerialNo to host if cluster is empty and host is non-empty OR
        // if cluster and host are both non-empty
        if ((Strings.isNullOrEmpty(cluster) && !Strings.isNullOrEmpty(host)) ||
                (!Strings.isNullOrEmpty(cluster) && !Strings.isNullOrEmpty(host))) {
            host = String.format("%s_%s", host, lastDigitsOfSerialNo);
        }
        // Append SerialNo to cluster if cluster is non-empty and host is empty
        if (!Strings.isNullOrEmpty(cluster) && Strings.isNullOrEmpty(host)) {
            cluster = String.format("%s_%s", cluster, lastDigitsOfSerialNo);
        }

        String alternateName = (cluster == null && host == null) ? exportGroup.getLabel() : null;

        return nameGenerator.generate(cluster, host, alternateName);
    }

    /**
     * Fetches an ExportMask from the database. Returns null if not found or
     * if inactive.
     * 
     * @param dbClient
     * @param exportMaskURI
     * @return ExportMask object
     */
    static public ExportMask getExportMask(DbClient dbClient, URI exportMaskURI) {
        ExportMask mask = dbClient.queryObject(ExportMask.class, exportMaskURI);
        if (mask == null || mask.getInactive() == true) {
            _log.error(String.format("ExportMask %s was null or inactive", exportMaskURI));
            return null;
        }
        return mask;
    }

    /**
     * Returns a list of all the Volumes in an ExportMask
     * 
     * @param exportMask
     * @return
     */
    static public List<URI> getVolumeURIs(ExportMask exportMask) {
        List<URI> volumeURIs = new ArrayList<URI>();
        if (exportMask.getVolumes() != null) {
            volumeURIs.addAll(Collections2.transform(exportMask.getVolumes().keySet(),
                    CommonTransformerFunctions.FCTN_STRING_TO_URI));
        }
        return volumeURIs;
    }

    public static DataSource getExportDatasource(StorageSystem storage, List<Initiator> initiators,
            DataSourceFactory factory, String configName) {

        if (configName == null || configName.isEmpty()) {
            return null;
        }
        // Create a set of unique host and cluster names
        Set<String> hosts = new HashSet<String>();
        Set<String> clusters = new HashSet<String>();
        for (Initiator initiator : initiators) {
            String host = initiator.getHostName();
            if (host != null) {
                hosts.add(host);
            }
            String cluster = initiator.getClusterName();
            if (cluster != null) {
                clusters.add(cluster);
            }
        }

        // Attempt to generate a unique name based on the sets that were generated.
        String cluster = null;
        // If there is unique cluster name, append to the name
        if (clusters.size() == 1) {
            cluster = clusters.iterator().next();
        }

        String host = null;
        // If there is a unique host name, append to the name
        if (hosts.size() == 1) {
            host = hosts.iterator().next();
        }

        return factory.createExportMaskDataSource(configName, host, cluster, storage);
    }

    /**
     * Create and initialize the ExportMask. To do this we:
     * 1. Create and persist the ExportMask.
     * 2. Save our targets and exportMaskURI in the ExportGroupCreateData.
     * 
     * 
     * @param storage - Storage System
     * @param exportGroup - ExportGroup object this ExportMask will apply to
     * @param initiators - Initiator objects pointing to initiators for this mask
     * @param volumeMap - Map of Volume URIs to Integer HLUs
     * @param targets List<URI> of StoragePorts
     * @param zoneAssignments - Map from InitiatorURI to List of assigned port URIs.
     * @param maskName the mask name
     * @param dbClient an instance of DbClient
     * @return new ExportMask object, persisted in database
     * @throws Exception
     */
    static public ExportMask initializeExportMask(
            StorageSystem storage, ExportGroup exportGroup,
            List<Initiator> initiators, Map<URI, Integer> volumeMap,
            List<URI> targets, Map<URI, List<URI>> zoneAssignments, String maskName, DbClient dbClient)
            throws Exception {
        if (maskName == null) {
            maskName = ExportMaskUtils.getMaskName(dbClient, initiators, exportGroup, storage);
        }
        ExportMask exportMask = ExportMaskUtils.createExportMask(dbClient, exportGroup,
                storage.getId(), maskName);
        String resourceRef;
        if (exportGroup.getType() != null) {
            if (exportGroup.getType().equals(ExportGroup.ExportGroupType.Cluster.name())) {
                resourceRef = initiators.get(0).getClusterName();
            } else {
                resourceRef = initiators.get(0).getHost().toString();
            }
            exportMask.setResource(resourceRef);
        } else {
            // This resource is used when we add initiators to existing masks on VMAX, which should not be
            // case with VPLEX and RP, which do not associate their initiators with hosts or clusters.
            exportMask.setResource(NullColumnValueGetter.getNullURI().toString());
        }

        exportMask.setCreatedBySystem(true);
        exportMaskUpdate(exportMask, volumeMap, initiators, targets);
        if (!exportGroup.getZoneAllInitiators() && null != zoneAssignments) {
            StringSetMap zoneMap = getZoneMapFromAssignments(zoneAssignments);
            if (!zoneMap.isEmpty()) {
                exportMask.setZoningMap(zoneMap);
            }
        }
        dbClient.updateAndReindexObject(exportMask);
        return exportMask;
    }

    // don't want to disturb the existing method, hence overloaded
    static public <T extends BlockObject> ExportMask initializeExportMaskWithVolumes(
            StorageSystem storage, ExportGroup exportGroup, String maskName, String maskLabel,
            List<Initiator> initiators, Map<URI, Integer> volumeMap,
            List<URI> targets, ZoneInfoMap zoneInfoMap,
            T volume, Set<String> unManagedInitiators, String nativeId,
            List<Initiator> userAddedInis, DbClient dbClient,
            Map<String, Integer> wwnToHluMap)
            throws Exception {
        ExportMask exportMask = ExportMaskUtils.createExportMask(dbClient, exportGroup,
                storage.getId(), maskName);
        String resourceRef;
        if (exportGroup.getType() != null) {
            if (exportGroup.getType().equals(ExportGroup.ExportGroupType.Cluster.name())) {
                resourceRef = initiators.get(0).getClusterName();
            } else {
                resourceRef = initiators.get(0).getHost().toString();
            }
            exportMask.setResource(resourceRef);
        } else {
            // This resource is used when we add initiators to existing masks on VMAX, which should not be
            // case with VPLEX and RP, which do not associate their initiators with hosts or clusters.
            exportMask.setResource(NullColumnValueGetter.getNullURI().toString());
        }

        exportMask.setLabel(maskLabel);
        exportMask.setCreatedBySystem(true);
        exportMaskUpdate(exportMask, null, initiators, targets);

        StringSetMap zoneMap = getZoneMapFromZoneInfoMap(zoneInfoMap, initiators);
        if (!zoneMap.isEmpty()) {
            exportMask.setZoningMap(zoneMap);
        }

        exportMask.addToExistingInitiatorsIfAbsent(new ArrayList<String>(unManagedInitiators));
        exportMask.addToUserCreatedInitiators(userAddedInis);

        // if the block object is marked as internal, then add to existing volumes of the mask
        if (volume.checkInternalFlags(Flag.NO_PUBLIC_ACCESS)) {
            _log.info("Block object {} is marked internal. Adding to existing volumes of the mask {}", volume.getNativeGuid(),
                    exportMask.getMaskName());
            String hlu = ExportGroup.LUN_UNASSIGNED_STR;
            if (wwnToHluMap.containsKey(volume.getWWN())) {
                hlu = String.valueOf(wwnToHluMap.get(volume.getWWN()));
            }
            exportMask.addToExistingVolumesIfAbsent(volume, hlu);
        } else {
            exportMask.addToUserCreatedVolumes(volume);
            exportMask.removeFromExistingVolumes(volume);
        }

        Integer hlu = wwnToHluMap.get(volume.getWWN()) != null ? 
                wwnToHluMap.get(volume.getWWN()) : ExportGroup.LUN_UNASSIGNED;
        exportMask.addVolume(volume.getId(), hlu);
        exportMask.setNativeId(nativeId);

        // need to sync up all remaining existing volumes
        exportMask.addToExistingVolumesIfAbsent(wwnToHluMap);

        dbClient.updateAndReindexObject(exportMask);
        // Update the FCZoneReferences if zoning is enables for the varray
        updateFCZoneReferences(exportGroup, volume, zoneInfoMap, initiators, dbClient);
        return exportMask;
    }

    public static <T extends BlockObject> void updateFCZoneReferences(ExportGroup exportGroup, T volume,
            ZoneInfoMap zoneInfoMap, List<Initiator> initiators, DbClient dbClient) {
        if (NetworkScheduler.isZoningRequired(dbClient, exportGroup.getVirtualArray())) {
            dbClient.updateAndReindexObject(getFCZoneReferences(volume, exportGroup, zoneInfoMap, initiators));
        }

    }

    /**
     * Given zoneInfoMap stored in an UnManagedExportMask, create a zone map for the
     * initiators in the list.
     * 
     * @param zoneInfoMap zoneInfoMap stored from a UnManagedExportMask
     * @param initiators a list of initiators for which the zone map should created
     * @return a zone map of initiator-uri-to-list-of-ports-uris
     */
    public static StringSetMap getZoneMapFromZoneInfoMap(ZoneInfoMap zoneInfoMap, List<Initiator> initiators) {
        StringSetMap zoneMap = new StringSetMap();
        if (zoneInfoMap != null && initiators != null) {
            for (Initiator initiator : initiators) {
                for (ZoneInfo info : zoneInfoMap.values()) {
                    if (info.getInitiatorId().equals(initiator.getId().toString())) {
                        zoneMap.put(initiator.getId().toString(), info.getPortId());
                    }
                }
            }
        }
        _log.info("getZoneMapFromZoneInfoMap created zone map {}", zoneMap);
        return zoneMap;
    }

    /**
     * Create FCZoneReference objects to the list of zoneInfoMap.
     * 
     * @param volume the FCZoneReference volume
     * @param exportGroup the FCZoneReference export group
     * @param zoneInfoMap the zone info maps
     * @param initiators the initiators
     * @return a list of FCZoneReference
     */
    private static <T extends BlockObject> List<FCZoneReference> getFCZoneReferences(T volume, ExportGroup exportGroup,
            ZoneInfoMap zoneInfoMap, List<Initiator> initiators) {
        List<FCZoneReference> refs = new ArrayList<FCZoneReference>();
        for (Initiator initiator : initiators) {
            for (ZoneInfo info : zoneInfoMap.values()) {
                if (info.getInitiatorId().equals(initiator.getId().toString())) {
                    refs.add(createFCZoneReference(info, initiator, volume, exportGroup));
                }
            }
        }
        return refs;
    }

    /**
     * Creates an instance of FCZoneReference
     * 
     * @param info the zone info containing the zone, its network,
     *            its network system, ...
     * @param initiator the zone initiator
     * @param volume volume the FCZoneReference volume
     * @param exportGroup the FCZoneReference export group
     * @return an instance of FCZoneReference
     */
    private static <T extends BlockObject> FCZoneReference createFCZoneReference(ZoneInfo info,
            Initiator initiator, T volume, ExportGroup exportGroup) {
        FCZoneReference ref = new FCZoneReference();
        ref.setPwwnKey(info.getZoneReferenceKey());
        ref.setFabricId(info.getFabricId());
        ref.setNetworkSystemUri(URI.create(info.getNetworkSystemId()));
        ref.setVolumeUri(volume.getId());
        ref.setGroupUri(exportGroup.getId());
        ref.setZoneName(info.getZoneName());
        ref.setId(URIUtil.createId(FCZoneReference.class));
        ref.setLabel(FCZoneReference.makeLabel(ref.getPwwnKey(), volume.getId().toString()));
        ref.setExistingZone(true);
        return ref;
    }

    /**
     * Adds the volumes, initiators, and targets to an ExportMask.
     * 
     * @param exportMask
     * @param volumeMap
     * @param initiators
     * @param targets
     */
    static void exportMaskUpdate(ExportMask exportMask, Map<URI, Integer> volumeMap,
            List<Initiator> initiators,
            List<URI> targets) {
        if (volumeMap != null) {
            for (URI volume : volumeMap.keySet()) {
                exportMask.addVolume(volume, volumeMap.get(volume));
            }
        }
        if (initiators != null) {
            for (Initiator initiator : initiators) {
                exportMask.addInitiator(initiator);
            }
        }
        if (targets != null) {
            for (URI target : targets) {
                exportMask.addTarget(target);
            }
        }
    }

    /**
     * Returns a StringSetMap containing the Initiator to StoragePort URIs from zoning assignments.
     * 
     * @param assignments Map<URI, List<URI>> of zoning assignments.
     * @return StringSetMap with same information encoded as
     */
    static public StringSetMap getZoneMapFromAssignments(Map<URI, List<URI>> assignments) {
        StringSetMap zoneMap = new StringSetMap();
        for (URI initiatorURI : assignments.keySet()) {
            StringSet portIds = new StringSet();
            List<URI> portURIs = assignments.get(initiatorURI);
            for (URI portURI : portURIs) {
                portIds.add(portURI.toString());
            }
            zoneMap.put(initiatorURI.toString(), portIds);
        }
        return zoneMap;
    }

    static public boolean isUsable(ExportMask mask) {
        return (mask != null && !mask.getInactive());
    }

    static public ExportMask asExportMask(DbClient dbClient, String maskUriStr) {
        URI maskURI = URI.create(maskUriStr);
        return dbClient.queryObject(ExportMask.class, maskURI);
    }

    public static String[] getBlockObjectAlternateNames(Collection<URI> uris, DbClient dbClient) throws Exception {
        String[] results = {};
        Set<String> names = new HashSet<String>();
        for (URI uri : uris) {
            names.add(getBlockObjectAlternateName(uri, dbClient));
        }
        return names.toArray(results);
    }

    /**
     * This method will take a URI and return alternateName for the BlockObject object to which the
     * URI applies.
     * 
     * @param uri
     *            - URI
     * @return Returns a nativeId String value
     * @throws DeviceControllerException.exceptions.notAVolumeOrBlocksnapshotUri
     *             if URI is not a Volume/BlockSnapshot URI
     */
    public static String getBlockObjectAlternateName(URI uri, DbClient dbClient) throws Exception {
        String nativeId;
        if (URIUtil.isType(uri, Volume.class)) {
            Volume volume = dbClient.queryObject(Volume.class, uri);
            nativeId = volume.getAlternateName();
        } else if (URIUtil.isType(uri, BlockSnapshot.class)) {
            BlockSnapshot blockSnapshot = dbClient.queryObject(BlockSnapshot.class, uri);
            nativeId = blockSnapshot.getAlternateName();
        } else if (URIUtil.isType(uri, BlockMirror.class)) {
            BlockMirror blockMirror = dbClient.queryObject(BlockMirror.class, uri);
            nativeId = blockMirror.getAlternateName();
        } else {
            throw DeviceControllerException.exceptions.notAVolumeOrBlocksnapshotUri(uri);
        }
        return nativeId;
    }

    static public Map<String, Set<URI>> mapComputeResourceToExportMask(DbClient dbClient, ExportGroup exportGroup, URI storage) {
        Map<String, Set<URI>> computeResourceToExportMaskURIs = new HashMap<String, Set<URI>>();
        if (exportGroup.getExportMasks() != null) {
            for (String exportMaskURIStr : exportGroup.getExportMasks()) {
                ExportMask mask = asExportMask(dbClient, exportMaskURIStr);
                if (mask == null || (mask.getStorageDevice() != null && !mask.getStorageDevice().equals(storage))) {
                    continue;
                }
                Set<Initiator> initiators = getInitiatorsForExportMask(dbClient, mask, null);
                for (Initiator initiator : initiators) {
                    String key = NullColumnValueGetter.getNullURI().toString();
                    if (exportGroup.forCluster() && initiator.getClusterName() != null) {
                        key = initiator.getClusterName();
                    } else if (initiator.getHost() != null) {
                        key = initiator.getHost().toString();
                    }
                    if (computeResourceToExportMaskURIs.get(key) == null) {
                        computeResourceToExportMaskURIs.put(key, new HashSet<URI>());
                    }
                    computeResourceToExportMaskURIs.get(key).add(mask.getId());
                }
            }
        }
        return computeResourceToExportMaskURIs;
    }

    static public Map<String, URI> mapHostToExportMask(DbClient dbClient, ExportGroup exportGroup, URI storage) {
        Map<String, URI> hostToExportMaskURI = new HashMap<String, URI>();
        if (exportGroup.getExportMasks() != null) {
            for (String exportMaskURIStr : exportGroup.getExportMasks()) {
                ExportMask mask = asExportMask(dbClient, exportMaskURIStr);
                if (mask == null || (mask.getStorageDevice() != null && !mask.getStorageDevice().equals(storage))) {
                    continue;
                }
                Set<Initiator> initiators = getInitiatorsForExportMask(dbClient, mask, null);
                for (Initiator initiator : initiators) {
                    String key = NullColumnValueGetter.getNullURI().toString();
                    if (initiator.getHost() != null) {
                        key = initiator.getHost().toString();
                        hostToExportMaskURI.put(key, mask.getId());
                    } else {
                        _log.info("Host Name empty in initiator {}", initiator.getId());
                    }

                }
            }
        }
        return hostToExportMaskURI;
    }

    static public Map<String, Set<URI>> groupInitiatorsByProtocol(Set<String> iniStrList, DbClient dbClient) {
        Map<String, Set<URI>> iniByProtocol = new HashMap<String, Set<URI>>();
        List<URI> iniList = new ArrayList<URI>(Collections2.transform(
                iniStrList, CommonTransformerFunctions.FCTN_STRING_TO_URI));
        List<Initiator> initiators = dbClient.queryObject(Initiator.class, iniList);
        for (Initiator ini : initiators) {
            if (null == ini.getProtocol()) {
                _log.warn("Initiator {} with protocol set to Null", ini.getId());
                continue;
            }
            if (!iniByProtocol.containsKey(ini.getProtocol())) {
                iniByProtocol.put(ini.getProtocol(), new HashSet<URI>());
            }
            iniByProtocol.get(ini.getProtocol()).add(ini.getId());
        }
        return iniByProtocol;

    }

    /**
     * Method returns a mapping of ExportMask URIs to a Map of Volume URIs to Integers.
     * The Mapping of Volume URIs to Integers represents how many ExportGroups that the
     * volume belongs to.
     * 
     * @param dbClient [in] - DbClient for accessing DB
     * @param volumeURIs [in] - List of volume URIs to check
     * @param initiatorURIs [in] - List of Initiator URIs
     * @return Map of URI:ExportMask to (Map of URI:Volume to Integer). The Integer count
     *         represents the total number of Export*Group*s that the volume belongs to.
     */
    static public Map<URI, Map<URI, Integer>> mapExportMaskToVolumeShareCount(DbClient dbClient,
            List<URI> volumeURIs,
            List<URI> initiatorURIs) {
        List<Initiator> initiators = dbClient.queryObject(Initiator.class, initiatorURIs);
        // Generate a mapping of volume URIs to the # of
        // ExportGroups that it is associated with
        Map<URI, Map<URI, Integer>> exportMaskToVolumeCount = new HashMap<>();
        for (URI volumeURI : volumeURIs) {
            for (Initiator initiator : initiators) {
                Integer count = ExportUtils.getNumberOfExportGroupsWithVolume(initiator, volumeURI, dbClient);
                List<ExportMask> exportMasks = ExportUtils.getInitiatorExportMasks(initiator, dbClient);
                for (ExportMask exportMask : exportMasks) {
                    if (!exportMask.hasVolume(volumeURI)) {
                        continue;
                    }
                    Map<URI, Integer> countMap = exportMaskToVolumeCount.get(exportMask.getId());
                    if (countMap == null) {
                        countMap = new HashMap<>();
                        exportMaskToVolumeCount.put(exportMask.getId(), countMap);
                    }
                    countMap.put(volumeURI, count);
                }
            }
        }
        return exportMaskToVolumeCount;
    }

    /**
     * Sorts export masks by eligibility.
     * For instance, less utilized export masks will be listed before more utilized ones.
     * 
     * @param maskSet list of export masks
     * @return list of sorted export masks
     */
    static public List<ExportMask> sortMasksByEligibility(Map<ExportMask, ExportMaskPolicy> maskMap, ExportGroup exportGroup) {
        List<ExportMaskComparatorContainer> exportMaskContainerList = new ArrayList<ExportMaskComparatorContainer>();
        for (Map.Entry<ExportMask, ExportMaskPolicy> entry : maskMap.entrySet()) {
            exportMaskContainerList.add(new ExportMaskComparatorContainer(entry.getKey(), entry.getValue(), exportGroup));
        }
        Collections.sort(exportMaskContainerList, new ExportMaskComparator());
        List<ExportMask> sortedMasks = new ArrayList<ExportMask>();
        for (ExportMaskComparatorContainer container : exportMaskContainerList) {
            ExportMaskPolicy policy = container.policy;
            ExportMask mask = container.mask;
            _log.info(String.format(
                    "Sorted ExportMasks by eligibility: %s { isSimple:%s, igType:%s, xpType:%s, localAutoTier:%s, autoTiers:%s }",
                    mask.getMaskName(), policy.isSimpleMask(), policy.getIgType(), policy.getExportType(),
                    policy.localTierPolicy, CommonTransformerFunctions.collectionToString(policy.getTierPolicies())));
            sortedMasks.add(container.mask);
        }
        return sortedMasks;
    }

    /**
     * Determine if the ExportMask is "in" a given Varray.
     * This is determined by if all the target ports are tagged for for the Varray.
     * 
     * @param exportMask -- ExportMask
     * @param varrayURI -- Varray URI
     * @return -- true if ExportMask in given Varray
     */
    public static boolean exportMaskInVarray(DbClient dbClient, ExportMask exportMask, URI varrayURI) {
        if (exportMask.getStoragePorts() == null || exportMask.getStoragePorts().isEmpty()) {
            return false;
        }
        List<URI> targetURIs = StringSetUtil.stringSetToUriList(exportMask.getStoragePorts());
        List<StoragePort> ports = dbClient.queryObject(StoragePort.class, targetURIs);
        for (StoragePort port : ports) {
            if (port.getTaggedVirtualArrays() == null 
                    || !port.getTaggedVirtualArrays().contains(varrayURI.toString())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Filter the volumeMap to only contain the desired includedVolumes.
     * 
     * @param volumeMap -- Map of volumes to LUN ids
     * @param includedVolumes -- Set of included volumes
     * @return -- Filter volumeMap containing only the includedVolumes
     */
    public static Map<URI, Integer> filterVolumeMap(Map<URI, Integer> volumeMap, Set<URI> includedVolumes) {
        Map<URI, Integer> result = new HashMap<URI, Integer>();
        if (includedVolumes == null) {
            return result;
        }
        for (URI includedVolume : includedVolumes) {
            result.put(includedVolume, volumeMap.get(includedVolume));
        }
        return result;
    }

    /**
     * Filters a volume list to only those in the ExportMask.volumes
     * 
     * @param volumeURIs -- list of Volume URIs
     * @param exportMask -- ExportMask
     * @return List<URI> of volume URIs filtered
     */
    public static List<URI> filterVolumesByExportMask(List<URI> volumeURIs, ExportMask exportMask) {
        List<URI> result = new ArrayList<URI>();
        for (URI uri : volumeURIs) {
            if (exportMask.hasVolume(uri)) {
                result.add(uri);
            }
        }
        return result;
    }

    /**
     * Routine will examine the ExportMask and determine if any of its initiators or volumes no
     * longer exist in the database or are marked as inactive. If so, they will get removed
     * from the list.
     * 
     * @param dbClient [in] - DbClient object
     * @param exportMask [in] - ExportMask object to check and sanitize
     */
    public static void sanitizeExportMaskContainers(DbClient dbClient, ExportMask exportMask) {
        if (exportMask != null) {
            List<URI> initiatorURIs = StringSetUtil.stringSetToUriList(exportMask.getInitiators());
            List<URI> initiatorsToRemove = new ArrayList<>();
            for (URI initiatorURI : initiatorURIs) {
                DataObject initiator = dbClient.queryObject(initiatorURI);
                if (initiator == null || initiator.getInactive()) {
                    initiatorsToRemove.add(initiatorURI);
                }
            }

            if (!initiatorsToRemove.isEmpty()) {
                _log.info(String.format(
                        "sanitizeExportMaskContainers - Removing non-existent/inactive Initiators from ExportMask %s (%s): %s",
                        exportMask.getMaskName(), exportMask.getId(),
                        CommonTransformerFunctions.collectionToString(initiatorsToRemove)));
                exportMask.removeInitiatorURIs(initiatorsToRemove);
                exportMask.removeFromUserAddedInitiatorsByURI(initiatorsToRemove);
            }

            Map<URI, Integer> volumeMap = StringMapUtil.stringMapToVolumeMap(exportMask.getVolumes());
            List<URI> volumesToRemove = new ArrayList<>();
            for (URI volumeURI : volumeMap.keySet()) {
                DataObject volume = dbClient.queryObject(volumeURI);
                if (volume == null || volume.getInactive()) {
                    volumesToRemove.add(volumeURI);
                }
            }

            if (!volumesToRemove.isEmpty()) {
                _log.info(String.format(
                        "sanitizeExportMaskContainers - Removing non-existent/inactive BlockObjects from ExportMask %s (%s): %s",
                        exportMask.getMaskName(), exportMask.getId(),
                        CommonTransformerFunctions.collectionToString(volumesToRemove)));
                exportMask.removeVolumes(volumesToRemove);
                exportMask.removeFromUserAddedVolumesByURI(volumesToRemove);
            }

            List<URI> storagePorts = StringSetUtil.stringSetToUriList(exportMask.getStoragePorts());
            List<URI> storagePortsToRemove = new ArrayList<>();
            for (URI storagePortURI : storagePorts) {
                DataObject storagePort = dbClient.queryObject(storagePortURI);
                if (storagePort == null || storagePort.getInactive()) {
                    storagePortsToRemove.add(storagePortURI);
                }
            }

            if (!storagePortsToRemove.isEmpty()) {
                _log.info(String.format(
                        "sanitizeExportMaskContainers - Removing non-existent/inactive StoragePorts from ExportMask %s (%s): %s",
                        exportMask.getMaskName(), exportMask.getId(),
                        CommonTransformerFunctions.collectionToString(storagePortsToRemove)));
                exportMask.removeTargets(storagePortsToRemove);
            }
        }
    }

    /**
     * When a co-exist initiator is delete in ViPR, no action will be taken on the storage array
     * but the ExportMask and FCZoneReferences in ViPR need to be updated. Note that the only
     * time a co-exist initiator can be removed is by actually deleting this initiator in ViPR.
     * This means all references will need to be deleted.
     * 
     * @param dbModelClient an instance of DbModelClient
     * @param exportMaskUri the export mask being updates
     * @param initiatorsUris the ids of the initiators being removed.
     */
    public static void removeMaskCoexistInitiators(DbModelClient dbModelClient, URI exportMaskUri, List<URI> initiatorsUris) {
        _log.info("removeMaskEoexistInitiators - Removing FCZoneReferences for initiators {}", initiatorsUris);

        ExportMask mask = dbModelClient.find(ExportMask.class, exportMaskUri);
        if (mask == null || mask.getInactive() || initiatorsUris == null) {
            return;
        }
        // Get the initiators that are removed and all ports in the mask. Generate all possible keys.
        List<Initiator> initiators = DataObjectUtils.iteratorToList(dbModelClient.find(Initiator.class, initiatorsUris));
        List<StoragePort> ports = DataObjectUtils.iteratorToList(dbModelClient.find(StoragePort.class,
                StringSetUtil.stringSetToUriList(mask.getStoragePorts())));
        List<String> keys = new ArrayList<String>();
        for (Initiator initiator : initiators) {
            for (StoragePort port : ports) {
                keys.add(FCZoneReference.makeEndpointsKey(initiator.getInitiatorPort(), port.getPortNetworkId()));
            }
        }
        if (!keys.isEmpty()) {
            _log.info("removeMaskEoexistInitiators - Removing FCZoneReferences for keys {}", keys);
            Joiner joiner = dbModelClient.join(FCZoneReference.class, "refs", "pwwnKey", keys).go();
            List<FCZoneReference> list = joiner.list("refs");
            if (list != null && !list.isEmpty()) {
                _log.info("removeMaskEoexistInitiators - found {} FCZoneReferences for keys {}", list.size(), keys);
                dbModelClient.remove(list);
            }
        }
        // now clean the export mask
        mask.removeInitiators(initiators);
        for (URI uri : initiatorsUris) {
            mask.removeZoningMapEntry(uri.toString());
        }
        _log.info("removeMaskEoexistInitiators - removed initiators {} from mask {}", initiatorsUris, mask.getMaskName());
        dbModelClient.update(mask);
    }

    /**
     * Checks if any of the initiators belongs to a true Host and returns true
     * if all initiators do not belong to a host. This effectively checks if all
     * initiators are for an RP or vplex system because the host id is set to
     * null for these initiators.
     * 
     * @param initiators a list of initiators.
     * @return true if all initiators have a null host id.
     */
    public static boolean areBackendInitiators(List<Initiator> initiators) {
        boolean backend = true;
        for (Initiator initiator : initiators) {
            if (!NullColumnValueGetter.isNullURI(initiator.getHost())
                    && URIUtil.isType(initiator.getHost(), Host.class)) {
                backend = false;
                break;
            }
        }
        return backend;
    }

    /**
     * Find a set of ExportMasks to which the given Initiators belong.
     * 
     * @param dbClient [IN] - For accessing DB
     * @param initiators [IN] - List of initiators to search for among the ExportMasks found in the DB.
     * @return HashMap of ExportMask URI to ExportMask object (Using HashMap, since URI is Comparable)
     */
    public static HashMap<URI, ExportMask> getExportMasksWithInitiators(DbClient dbClient, List<Initiator> initiators) {
        final String initiatorAliasStr = "initiator";
        final String portNameAliasStr = "iniport";
        final String exportMaskAliasStr = "em";
        final String initiatorStr = "initiators";

        // Find all the ExportMasks that contain the 'initiators'
        HashMap<URI, ExportMask> exportMasksWithInitiator = new HashMap<>();
        for (Initiator initiator : initiators) {
            Joiner joiner = new Joiner(dbClient);
            Joiner query = joiner.join(Initiator.class, initiatorAliasStr).match(portNameAliasStr, initiator.getInitiatorPort())
                    .join(initiatorAliasStr, ExportMask.class, exportMaskAliasStr, initiatorStr).go();
            Set<ExportMask> matchedMasks = query.set(exportMaskAliasStr);
            for (ExportMask exportMask : matchedMasks) {
                exportMasksWithInitiator.put(exportMask.getId(), exportMask);
            }
        }

        return exportMasksWithInitiator;
    }

}
