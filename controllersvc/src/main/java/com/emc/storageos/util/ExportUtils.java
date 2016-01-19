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
import java.util.UUID;

import org.apache.http.conn.util.InetAddressUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.DataObjectUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringMapUtil;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.TreeMultimap;

public class ExportUtils {

    // Logger
    private final static Logger _log = LoggerFactory.getLogger(ExportUtils.class);

    public static final String NO_VIPR = "NO_VIPR";   // used to exclude VIPR use of export mask

    /**
     * Get an initiator as specified by the initiator's network port.
     * 
     * @param networkPort The initiator's port WWN or IQN.
     * @return A reference to an initiator.
     */
    public static Initiator getInitiator(String networkPort, DbClient dbClient) {
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
     * A utility function method to get the user-created initiators from an export mask.
     * If an initiator is not found for a given user-created WWN, it is simply
     * ignored and no error is raised.
     * 
     * @param exportMask the export mask
     * @param dbClient an instance of DbClient
     * @return a list of Initiators
     */
    public static List<Initiator> getExportMaskExistingInitiators(ExportMask exportMask, DbClient dbClient) {
        List<Initiator> initiators = new ArrayList<Initiator>();
        Initiator initiator = null;
        if (exportMask.getExistingInitiators() != null &&
                !exportMask.getExistingInitiators().isEmpty()) {
            for (String initStr : exportMask.getExistingInitiators()) {
                initStr = Initiator.toPortNetworkId(initStr);
                initiator = getInitiator(initStr, dbClient);
                if (initiator != null) {
                    initiators.add(initiator);
                }
            }
        }
        return initiators;
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
    public static List<URI> getExportMaskAllInitiators(ExportMask exportMask, DbClient dbClient) {
        List<URI> initiators = new ArrayList<URI>();
        if (exportMask.getInitiators() != null &&
                !exportMask.getInitiators().isEmpty()) {
            initiators.addAll(StringSetUtil.stringSetToUriList(exportMask.getInitiators()));
        }
        if (exportMask.getExistingInitiators() != null &&
                !exportMask.getExistingInitiators().isEmpty()) {
            for (String initStr : exportMask.getExistingInitiators()) {
                initStr = Initiator.toPortNetworkId(initStr);
                Initiator init = getInitiator(initStr, dbClient);
                if (init != null && !initiators.contains(init.getId())) {
                    initiators.add(init.getId());
                }
            }
        }
        return initiators;
    }

    /**
     * Fetches and returns the initiators for one or more export masks.
     * 
     * @param exportMaskUris the export mask URIs
     * @param dbClient an instance of {@link DbClient}
     * @return a list of active initiators in the export mask
     */
    public static List<Initiator> getExportMasksInitiators(Collection<URI> exportMaskUris, DbClient dbClient) {
        List<Initiator> list = new ArrayList<Initiator>();
        for (URI exportMaskUri : exportMaskUris) {
            list.addAll(getExportMaskInitiators(exportMaskUri, dbClient));
        }
        return list;
    }

    /**
     * Get all initiator ports in mask.
     * 
     * @param exportMask
     * @param dbClient
     * @return
     */
    public static Set<String> getExportMaskAllInitiatorPorts(ExportMask exportMask, DbClient dbClient) {
        Set<String> ports = new HashSet<String>();
        if (exportMask.getInitiators() != null && !exportMask.getInitiators().isEmpty()) {
            List<URI> iniUris = StringSetUtil.stringSetToUriList(exportMask.getInitiators());
            List<Initiator> initiators = dbClient.queryObject(Initiator.class, iniUris);
            for (Initiator ini : initiators) {
                if (ini == null || ini.getInitiatorPort() == null) {
                    continue;
                }
                ports.add(Initiator.normalizePort(ini.getInitiatorPort()));
            }
        }

        if (exportMask.getExistingInitiators() != null && !exportMask.getExistingInitiators().isEmpty()) {
            for (String initStr : exportMask.getExistingInitiators()) {
                ports.add(initStr);
            }
        }
        return ports;

    }

    /**
     * Fetches and returns the initiators for an export mask.
     * 
     * @param exportMaskUri the export mask URI
     * @param dbClient an instance of {@link DbClient}
     * @return a list of active initiators in the export mask
     */
    public static List<Initiator> getExportMaskInitiators(URI exportMaskUri, DbClient dbClient) {
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskUri);
        return getExportMaskInitiators(exportMask, dbClient);
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
    public static List<Initiator> getExportMaskInitiators(ExportMask exportMask, DbClient dbClient) {
        if (exportMask != null && exportMask.getInitiators() != null) {
            List<URI> initiators = StringSetUtil.stringSetToUriList(exportMask.getInitiators());
            return dbClient.queryObject(Initiator.class, initiators);
        }
        return new ArrayList<Initiator>();
    }

    /**
     * Fetches and returns the initiators for an export group.
     * 
     * @param exportGroup the export grop
     * @param dbClient an instance of {@link DbClient}
     * @return a list of active initiators in the export mask
     */
    public static List<Initiator> getExportGroupInitiators(ExportGroup exportGroup, DbClient dbClient) {
        List<URI> initiators = StringSetUtil.stringSetToUriList(exportGroup.getInitiators());
        return dbClient.queryObject(Initiator.class, initiators);
    }

    /**
     * Return the storage ports allocated to each initiators in an export mask by looking
     * up the zoningMap.
     * 
     * @param mask
     * @param initiator
     * @return
     */
    public static List<URI> getInitiatorPortsInMask(ExportMask mask, Initiator initiator, DbClient dbClient) {
        List<URI> list = new ArrayList<URI>();
        StringSetMap zoningMap = mask.getZoningMap();
        String strUri = initiator.getId().toString();
        if (zoningMap != null && zoningMap.containsKey(strUri) &&
                initiator.getProtocol().equals(Protocol.FC.toString())) {
            list = StringSetUtil.stringSetToUriList(zoningMap.get(strUri));
        }
        _log.info("getInitiatorPortsInMask {} {}", initiator, Joiner.on(',').join(list));
        return list;
    }

    /**
     * Returns the storage ports allocated to each initiator based
     * on the connectivity between them.
     * 
     * @param mask
     * @param initiator
     * @param dbClient
     * @return
     */
    public static List<URI> getPortsInInitiatorNetwork(ExportMask mask, Initiator initiator, DbClient dbClient)
    {
        List<URI> list = new ArrayList<URI>();
        List<StoragePort> ports = getStoragePorts(mask, dbClient);
        NetworkLite networkLite = NetworkUtil.getEndpointNetworkLite(initiator.getInitiatorPort(), dbClient);
        if (networkLite != null)
        {
            for (StoragePort port : ports)
            {
                if (port.getNetwork() != null &&
                        port.getNetwork().equals(networkLite.getId()))
                {
                    list.add(port.getId());
                }
            }

            if (list.isEmpty() && networkLite.getRoutedNetworks() != null)
            {
                for (StoragePort port : ports)
                {
                    if (port.getNetwork() != null &&
                            networkLite.getRoutedNetworks().contains(port.getNetwork().toString()))
                    {
                        list.add(port.getId());
                    }
                }
            }
        }
        return list;
    }

    /**
     * Fetches and returns the storage ports for an export mask
     * 
     * @param exportMask the export mask
     * @param dbClient an instance of {@link DbClient}
     * @return a list of active storage ports used by the export mask
     */
    public static List<StoragePort> getStoragePorts(ExportMask exportMask, DbClient dbClient) {
        List<StoragePort> ports = new ArrayList<StoragePort>();
        if (exportMask.getStoragePorts() != null) {
            StoragePort port = null;
            for (String initUri : exportMask.getStoragePorts()) {
                port = dbClient.queryObject(StoragePort.class, URI.create(initUri));
                if (port != null && !port.getInactive()) {
                    ports.add(port);
                }
            }
        }
        _log.info("Found {} storage ports in export mask {}", ports.size(), exportMask.getMaskName());
        return ports;
    }

    /**
     * Creates a map of storage ports keyed by the port WWN.
     * 
     * @param ports the storage ports
     * 
     * @return a map of portWwn-to-port of storage ports
     */
    public static Map<String, StoragePort> getStoragePortsByWwnMap(Collection<StoragePort> ports) {
        Map<String, StoragePort> map = new HashMap<String, StoragePort>();
        for (StoragePort port : ports) {
            map.put(port.getPortNetworkId(), port);
        }
        return map;
    }

    /**
     * Fetches all the export masks in which a block object is member
     * 
     * @param blockObject the block object
     * @param dbClient an instance of {@link DbClient}
     * @return a list of export masks in which a block object is member
     */
    public static Map<ExportMask, ExportGroup> getExportMasks(BlockObject blockObject, DbClient dbClient) {
        Map<ExportMask, ExportGroup> exportMasksMap = new HashMap<ExportMask, ExportGroup>();
        URIQueryResultList exportGroups = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.
                Factory.getBlockObjectExportGroupConstraint(blockObject.getId()), exportGroups);
        for (URI egUri : exportGroups) {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, egUri);
            if (exportGroup.getInactive()) {
                continue;
            }
            if (exportGroup.getExportMasks() != null) {
                for (String exportMaskUriStr : exportGroup.getExportMasks()) {
                    ExportMask exportMask = dbClient.queryObject(ExportMask.class,
                            URI.create(exportMaskUriStr));
                    if (exportMask != null && !exportMask.getInactive() && exportMask
                            .getStorageDevice().equals(blockObject.getStorageController())
                            && exportMask.hasVolume(blockObject.getId())
                            && exportMask.getInitiators() != null && exportMask.getStoragePorts() != null) {
                        exportMasksMap.put(exportMask, exportGroup);
                    }
                }
            }
        }
        _log.info("Found {} export masks for block object {}", exportMasksMap.size(), blockObject.getLabel());
        return exportMasksMap;
    }

    /**
     * Gets all the export masks that this initiator is member of.
     * 
     * @param initiator the initiator
     * @param dbClient an instance of {@link DbClient}
     * @return all the export masks that this initiator is member of
     */
    public static List<ExportMask> getInitiatorExportMasks(
            Initiator initiator, DbClient dbClient) {
        List<ExportMask> exportMasks = new ArrayList<ExportMask>();
        URIQueryResultList egUris = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                getExportGroupInitiatorConstraint(initiator.getId().toString())
                , egUris);
        ExportGroup exportGroup = null;
        for (URI egUri : egUris) {
            exportGroup = dbClient.queryObject(ExportGroup.class, egUri);
            if (exportGroup == null || exportGroup.getInactive() || exportGroup.getExportMasks() == null) {
                continue;
            }
            Collection<String> exportMaskUris = exportGroup.getExportMasks();
            for (String exportMaskUri : exportMaskUris) {
                ExportMask exportMask = dbClient.queryObject(ExportMask.class, URI.create(exportMaskUri));
                if (exportMask != null &&
                        !exportMask.getInactive() &&
                        exportMask.hasInitiator(initiator.getId().toString()) &&
                        exportMask.getVolumes() != null &&
                        exportMask.getStoragePorts() != null) {
                    exportMasks.add(exportMask);
                }
            }
        }
        _log.info("Found {} export masks for initiator {}", exportMasks.size(), initiator.getInitiatorPort());
        return exportMasks;
    }

    /**
     * Returns all the ExportGroups the initiator is a member of.
     * 
     * @param initiator Initiator
     * @param dbClient
     * @return List<ExportGroup> that contain a key to the Initiator URI
     */
    public static List<ExportGroup> getInitiatorExportGroups(
            Initiator initiator, DbClient dbClient) {
        List<ExportGroup> exportGroups = new ArrayList<ExportGroup>();
        URIQueryResultList egUris = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                getExportGroupInitiatorConstraint(initiator.getId().toString())
                , egUris);
        ExportGroup exportGroup = null;
        for (URI egUri : egUris) {
            exportGroup = dbClient.queryObject(ExportGroup.class, egUri);
            if (exportGroup == null || exportGroup.getInactive() || exportGroup.getExportMasks() == null) {
                continue;
            }
            exportGroups.add(exportGroup);
        }
        return exportGroups;
    }

    /**
     * Returns all the ExportGroups the initiator and volume/snapshot is a member of.
     * 
     * @param initiator Initiator
     * @param blockObjectId ID of a volume or snapshot
     * @param dbClient db client handle
     * @return List<ExportGroup> that contain a key to the Initiator URI
     */
    public static List<ExportGroup> getInitiatorVolumeExportGroups(
            Initiator initiator, URI blockObjectId, DbClient dbClient) {
        List<ExportGroup> exportGroups = new ArrayList<ExportGroup>();
        URIQueryResultList egUris = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                getExportGroupInitiatorConstraint(initiator.getId().toString())
                , egUris);
        for (URI egUri : egUris) {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, egUri);
            if (exportGroup == null || exportGroup.getInactive() || exportGroup.getExportMasks() == null) {
                continue;
            }
            if (exportGroup.hasBlockObject(blockObjectId)) {
                exportGroups.add(exportGroup);
            }
        }
        return exportGroups;
    }

    /**
     * Cleans up the export group references to the export mask and volumes therein
     * 
     * @param exportMask export mask
     */
    public static void cleanupAssociatedMaskResources(DbClient dbClient, ExportMask exportMask) {
        List<ExportGroup> exportGroups = ExportMaskUtils.getExportGroups(dbClient, exportMask);
        if (exportGroups != null) {
            // Remove the mask references in the export group
            for (ExportGroup exportGroup : exportGroups) {
                // Remove this mask from the export group
                exportGroup.removeExportMask(exportMask.getId().toString());

                // Remove the volumes from the export group
                if (exportMask.getUserAddedVolumes() != null) {
                    Set<URI> removeSet = new HashSet<>();
                    TreeMultimap<String, URI> volumesToExportMasks =
                            buildVolumesToExportMasksMap(dbClient, exportGroup);
                    for (String volumeURIString : exportMask.getUserAddedVolumes().values()) {
                        // Should only remove those volumes in the ExportGroup that are not already in another
                        // ExportMask associated with the ExportGroup. For example, if there is an ExportGroup
                        // for a cluster, there could be an ExportMask for each host, which could have the volume.
                        // In that case, we do not want to remove the volume from the ExportGroup
                        if (!volumeIsInAnotherExportMask(exportMask, volumeURIString, volumesToExportMasks)) {
                            URI volumeURI = URI.create(volumeURIString);
                            removeSet.add(volumeURI);
                        }
                    }
                    List<URI> volumeURIs = new ArrayList<>(removeSet);
                    exportGroup.removeVolumes(volumeURIs);
                }
            }

            // Update all of the export groups in the DB
            dbClient.updateAndReindexObject(exportGroups);
        }
    }

    /**
     * Create a TreeMultimap that gives a mapping of a volume URI String to a list of
     * ExportMasks that the volume is associated with. All ExportMasks are associated
     * with the ExportGroup.
     * 
     * @param dbClient [in] - DB client object
     * @param exportGroup [in] - ExportGroup object to use build the mapping
     * @return Mapping of volume URI String to list of ExportMask URIs.
     */
    private static TreeMultimap<String, URI> buildVolumesToExportMasksMap(DbClient dbClient,
            ExportGroup exportGroup) {
        TreeMultimap<String, URI> volumesToExportMasks = TreeMultimap.create();
        for (String exportMaskURIString : exportGroup.getExportMasks()) {
            URI exportMaskURI = URI.create(exportMaskURIString);
            ExportMask checkMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
            if (checkMask.getUserAddedVolumes() != null) {
                for (String volUriStr : checkMask.getUserAddedVolumes().values()) {
                    volumesToExportMasks.put(volUriStr, checkMask.getId());
                }
            }
        }
        return volumesToExportMasks;
    }

    /**
     * Routine will determine if the volume is associated with an ExportMask other than 'exportMask'.
     * 
     * @param exportMask [in] ExportMask that is currently being validated
     * @param volumeURIString [in] VolumeURI String
     * @param volumesToExportMasks [in] - Used for checking the volume
     * @return true if the Volume is in an ExportMask associated to ExportGroup
     */
    private static boolean volumeIsInAnotherExportMask(ExportMask exportMask, String volumeURIString,
            TreeMultimap<String, URI> volumesToExportMasks) {
        boolean isInAnotherMask = false;
        if (volumesToExportMasks.containsKey(volumeURIString)) {
            // Create a temporary set (so that it can be modified)
            Set<URI> exportMaskURIs = new HashSet<>(volumesToExportMasks.get(volumeURIString));
            // Remove the 'exportMask' URI from the list. If anything is left, then it means that
            // there is another ExportMask with the volume in it.
            exportMaskURIs.remove(exportMask.getId());
            isInAnotherMask = !exportMaskURIs.isEmpty();
        }
        return isInAnotherMask;
    }

    public static String getFileMountPoint(String fileStoragePort, String path) {
        if (InetAddressUtils.isIPv6Address(fileStoragePort)) {
            fileStoragePort = "[" + fileStoragePort + "]";
        }

        return fileStoragePort + ":" + path;
    }

    static public boolean isExportMaskShared(DbClient dbClient, URI exportMaskURI, Collection<URI> exportGroupURIs) {
        List<ExportGroup> results =
                CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, ExportGroup.class,
                        ContainmentConstraint.Factory.getConstraint(ExportGroup.class, "exportMasks", exportMaskURI));
        int count = 0;
        for (ExportGroup exportGroup : results) {
            count++;
            if (exportGroupURIs != null) {
                exportGroupURIs.add(exportGroup.getId());
            }
        }
        return count > 1;
    }

    /**
     * This function is used to determine if an initiator is in an export mask other than the one
     * being processed and this other export mask is used by a different export group yet they all
     * are for the same host or compute resource. This situation happens when volumes in different
     * virtual arrays but on the same storage array are exported to the same host. In this situation
     * the application creates 2 export groups and 2 export masks in ViPR and 2 different masking 
     * views on the storage array, yet the masking views share the same initiator group. 
     * <p>
     * This function checks that another export masks is not sharing the same initiator group 
     * but that is not under the same export group (this is handled elsewhere) by searching 
     * for an export mask that:<ol>
     * <li>is for the same storage system</li>
     * <li>is not one used by the same export group</li>
     * <li>has the initiator added into it by the application</li>
     * <li>has the exact set of initiators which a prerequisite to sharing an initiator group</li>
     * </ol>  
     * @param dbClient an instance of DbClient
     * @param initiatorUri the URI of the initiator being checked
     * @param curExportMask the export mask being processed
     * @param exportMaskURIs other export masks in the same export group as the export mask being processed.
     * @return true if the initiator is found in other export masks.
     */
    public static boolean isInitiatorShared(DbClient dbClient, URI initiatorUri, ExportMask curExportMask, Collection<URI> exportMaskURIs) {
        List<ExportMask> results =
                CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, ExportMask.class,
                        ContainmentConstraint.Factory.getConstraint(ExportMask.class, "initiators", initiatorUri));
        for (ExportMask exportMask : results) {
            if (exportMask != null && !exportMask.getId().equals(curExportMask.getId()) && 
                    exportMask.getStorageDevice().equals(curExportMask.getStorageDevice()) &&
                            !exportMaskURIs.contains(exportMask.getId()) && 
                            exportMask.hasUserInitiator(initiatorUri) && 
                            StringSetUtil.areEqual(exportMask.getInitiators(), curExportMask.getInitiators())) {
                _log.info(String.format("Initiator %s is shared with mask %s.", 
                        initiatorUri, exportMask.getMaskName()));
                return true;
            }
        }
        return false;
    }

    static public int getNumberOfExportGroupsWithVolume(Initiator initiator, URI blockObjectId, DbClient dbClient) {
        List<ExportGroup> list = getInitiatorVolumeExportGroups(initiator, blockObjectId, dbClient);
        return (list != null) ? list.size() : 0;
    }

    static public String computeResourceForInitiator(ExportGroup exportGroup, Initiator initiator) {
        String value = NullColumnValueGetter.getNullURI().toString();
        if (exportGroup.forCluster()) {
            value = initiator.getClusterName();
        } else if (exportGroup.forHost() || (exportGroup.forInitiator() && initiator.getHost() != null)) {
            value = initiator.getHost().toString();
        }
        return value;
    }

    /**
     * Using the ExportGroup object, produces a mapping of the BlockObject URI to LUN value
     * 
     * @param dbClient
     * @param storage
     * @param exportGroup
     * @return
     */
    static public Map<URI, Integer> getExportGroupVolumeMap(DbClient dbClient, StorageSystem storage,
            ExportGroup exportGroup) {
        Map<URI, Integer> map = new HashMap<>();
        if (exportGroup != null && exportGroup.getVolumes() != null) {
            for (Map.Entry<String, String> entry : exportGroup.getVolumes().entrySet()) {
                URI uri = URI.create(entry.getKey());
                Integer lun = Integer.valueOf(entry.getValue());
                BlockObject blockObject = BlockObject.fetch(dbClient, uri);
                if (blockObject != null && blockObject.getStorageController().equals(storage.getId())) {
                    map.put(uri, lun);
                }
            }
        }
        return map;
    }

    /**
     * This method checks to see if there are storagePorts in exportMask storagePorts
     * which do not exist in the zoningMap so as to remove those ports from the storage
     * view. This is called from exportGroupRemoveVolumes.
     * 
     * @param exportMask reference to exportMask
     * @return list of storagePort URIs that don't exist in zoningMap.
     */
    public static List<URI> checkIfStoragePortsNeedsToBeRemoved(ExportMask exportMask) {
        List<URI> storagePortURIs = new ArrayList<URI>();
        StringSetMap zoningMap = exportMask.getZoningMap();
        StringSet existingStoragePorts = exportMask.getStoragePorts();
        StringSet zoningMapStoragePorts = new StringSet();
        if (zoningMap != null) {
            for (String initiatorId : zoningMap.keySet()) {
                StringSet ports = zoningMap.get(initiatorId);
                if (ports != null && !ports.isEmpty()) {
                    zoningMapStoragePorts.addAll(ports);
                }
            }
        }
        existingStoragePorts.removeAll(zoningMapStoragePorts);
        if (!existingStoragePorts.isEmpty()) {
            storagePortURIs = StringSetUtil.stringSetToUriList(existingStoragePorts);
            _log.info("Storage ports needs to be removed are:" + storagePortURIs);
            ;
        }

        return storagePortURIs;
    }

    /**
     * This method updates zoning map to add new assignments.
     * 
     * @param dbClient an instance of {@link DbClient}
     * @param exportMask The reference to exportMask
     * @param assignments New assignments Map of initiator to storagePorts that will be updated in the zoning map
     * @param exportMasksToUpdateOnDeviceWithStoragePorts OUT param -- Map of ExportMask to new Storage ports
     * @return returns an updated exportMask
     */
    public static ExportMask updateZoningMap(DbClient dbClient, ExportMask exportMask, Map<URI, List<URI>> assignments,
            Map<URI, List<URI>> exportMasksToUpdateOnDeviceWithStoragePorts) {

        StringSetMap existingZoningMap = exportMask.getZoningMap();
        for (URI initiatorURI : assignments.keySet()) {
            boolean initiatorMatchFound = false;
            if (existingZoningMap != null && !existingZoningMap.isEmpty()) {
                for (String initiatorId : existingZoningMap.keySet()) {
                    if (initiatorURI.toString().equals(initiatorId)) {
                        StringSet ports = existingZoningMap.get(initiatorId);
                        if (ports != null && !ports.isEmpty()) {
                            initiatorMatchFound = true;
                            StringSet newTargets = StringSetUtil.uriListToStringSet(assignments.get(initiatorURI));
                            if (!ports.containsAll(newTargets)) {
                                ports.addAll(newTargets);
                                // Adds zoning map entry with new and existing ports. Its kind of updating storage ports for the initiator.
                                exportMask.addZoningMapEntry(initiatorId, ports);
                                updateExportMaskStoragePortsMap(exportMask, exportMasksToUpdateOnDeviceWithStoragePorts,
                                        assignments, initiatorURI);
                            }
                        }
                    }
                }
            }
            if (!initiatorMatchFound) {
                // Adds new zoning map entry for the initiator with new assignments as there isn't one already.
                exportMask.addZoningMapEntry(initiatorURI.toString(), StringSetUtil.uriListToStringSet(assignments.get(initiatorURI)));
                updateExportMaskStoragePortsMap(exportMask, exportMasksToUpdateOnDeviceWithStoragePorts,
                        assignments, initiatorURI);
            }
        }
        dbClient.persistObject(exportMask);

        return exportMask;
    }

    /**
     * This method just updates the passed in exportMasksToUpdateOnDeviceWithStoragePorts map with
     * the new storage ports assigned for the initiator for a exportMask.
     * 
     * @param exportMask The reference to exportMask
     * @param exportMasksToUpdateOnDeviceWithStoragePorts OUT param -- map of exportMask to update with new storage ports
     * @param assignments New assignments Map of initiator to storage ports
     * @param initiatorURI The initiator URI for which storage ports are updated in the exportMask
     */
    private static void updateExportMaskStoragePortsMap(ExportMask exportMask,
            Map<URI, List<URI>> exportMasksToUpdateOnDeviceWithStoragePorts,
            Map<URI, List<URI>> assignments, URI initiatorURI) {

        if (exportMasksToUpdateOnDeviceWithStoragePorts.get(exportMask.getId()) != null) {
            exportMasksToUpdateOnDeviceWithStoragePorts.get(exportMask.getId()).addAll(assignments.get(initiatorURI));
        } else {
            exportMasksToUpdateOnDeviceWithStoragePorts.put(exportMask.getId(), assignments.get(initiatorURI));
        }
    }

    /**
     * Take in a list of storage port names (hex digits separated by colons),
     * then returns a list of URIs representing the StoragePort URIs they represent.
     * 
     * This method ignores the storage ports from cinder storage systems.
     * 
     * @param storagePorts [in] - Storage port name, hex digits separated by colons
     * @return List of StoragePort URIs
     */
    public static List<String> storagePortNamesToURIs(DbClient dbClient,
            List<String> storagePorts) {
        List<String> storagePortURIStrings = new ArrayList<String>();
        Map<URI, String> systemURIToType = new HashMap<URI, String>();
        for (String port : storagePorts) {
            URIQueryResultList portUriList = new URIQueryResultList();
            dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getStoragePortEndpointConstraint(port), portUriList);
            Iterator<URI> storagePortIter = portUriList.iterator();
            while (storagePortIter.hasNext()) {
                URI portURI = storagePortIter.next();
                StoragePort sPort = dbClient.queryObject(StoragePort.class, portURI);
                if (sPort != null && !sPort.getInactive()) {
                    String systemType = getStoragePortSystemType(dbClient, sPort, systemURIToType);
                    // ignore cinder managed storage system's port
                    if (!DiscoveredDataObject.Type.openstack.name().equals(systemType)) {
                        storagePortURIStrings.add(portURI.toString());
                    }
                }
            }
        }
        return storagePortURIStrings;
    }

    private static String getStoragePortSystemType(DbClient dbClient,
            StoragePort port, Map<URI, String> systemURIToType) {
        URI systemURI = port.getStorageDevice();
        String systemType = systemURIToType.get(systemURI);
        if (systemType == null) {
            StorageSystem system = dbClient.queryObject(StorageSystem.class, systemURI);
            systemType = system.getSystemType();
            systemURIToType.put(systemURI, systemType);
        }
        return systemType;
    }

    /**
     * Checks to see if the export group is for RecoverPoint
     * 
     * @param exportGroup
     *            The export group to check
     * @return True if this export group is for RecoverPoint, false otherwise.
     */
    public static boolean checkIfExportGroupIsRP(ExportGroup exportGroup) {
        if (exportGroup == null) {
            return false;
        }

        return exportGroup.checkInternalFlags(Flag.RECOVERPOINT);
    }

    /**
     * Checks to see if the initiators passed in are for RecoverPoint.
     * 
     * Convenience method to load the actual Initiators from the StringSet first
     * before calling checkIfInitiatorsForRP(List<Initiator> initiatorList).
     * 
     * @param dbClient
     *            DB Client
     * @param initiatorList
     *            The StringSet of initiator IDs to check
     * @return True if there are RecoverPoint Initiators in the passed in list,
     *         false otherwise
     */
    public static boolean checkIfInitiatorsForRP(DbClient dbClient, StringSet initiatorList) {
        if (dbClient == null || initiatorList == null) {
            return false;
        }

        List<Initiator> initiators = new ArrayList<Initiator>();
        for (String initiatorId : initiatorList) {
            Initiator initiator = dbClient.queryObject(Initiator.class, URI.create(initiatorId));
            if (initiator != null) {
                initiators.add(initiator);
            }
        }

        return checkIfInitiatorsForRP(initiators);
    }

    /**
     * Check the list of passed in initiators and check if the RP flag is set.
     * 
     * @param initiatorList
     *            List of Initiators
     * @return True if there are RecoverPoint Initiators in the passed in list,
     *         false otherwise
     */
    public static boolean checkIfInitiatorsForRP(List<Initiator> initiatorList) {
        if (initiatorList == null) {
            return false;
        }

        _log.debug("Checking Initiators to see if this is RP");
        boolean isRP = true;
        for (Initiator initiator : initiatorList) {
            if (!initiator.checkInternalFlags(Flag.RECOVERPOINT)) {
                isRP = false;
                break;
            }
        }

        _log.debug("Are these RP initiators? " + (isRP ? "Yes!" : "No!"));
        return isRP;
    }

    /**
     * Figure out whether or not we need to use the EMC Force flag for the SMIS
     * operation being performed on this volume.
     * 
     * @param _dbClient
     *            DB Client
     * @param blockObjectURI
     *            BlockObject to check
     * @return Whether or not to use the EMC force flag
     */
    public static boolean useEMCForceFlag(DbClient _dbClient, URI blockObjectURI) {
        boolean forceFlag = false;
        // If there are any volumes that are RP, then we need to use the force flag on this operation
        BlockObject bo = Volume.fetchExportMaskBlockObject(_dbClient, blockObjectURI);

        if (bo != null && BlockObject.checkForRP(_dbClient, bo.getId())) {
            // Set the force flag if the Block object is an RP Volume or BlockSnapshot.
            forceFlag = true;
        }

        return forceFlag;
    }

    /**
     * Get the varrays used for the set of volumes for a storage system.
     * For the VPlex, it will include the HA virtual array if there are distributed volumes.
     * 
     * @param exportGroup -- ExportGroup instance
     * @param storageURI -- the URI of the Storage System
     * @param dbClient
     */
    public static List<URI> getVarraysForStorageSystemVolumes(ExportGroup exportGroup, URI storageURI, DbClient dbClient) {
        List<URI> varrayURIs = new ArrayList<URI>();
        varrayURIs.add(exportGroup.getVirtualArray());
        Map<URI, Map<URI, Integer>> systemToVolumeMap = getStorageToVolumeMap(exportGroup, false, dbClient);
        if (systemToVolumeMap.containsKey(storageURI)) {
            Set<URI> blockObjectURIs = systemToVolumeMap.get(storageURI).keySet();
            for (URI blockObjectURI : blockObjectURIs) {
                BlockObject blockObject = BlockObject.fetch(dbClient, blockObjectURI);
                Set<URI> blockObjectVarrays = getBlockObjectVarrays(blockObject, dbClient);
                varrayURIs.addAll(blockObjectVarrays);
            }
        }
        return varrayURIs;
    }

    /**
     * Given an exportGroup, generate a map of Storage System URI to a map of BlockObject URI to lun id
     * for BlockObjects in the Export Group.
     * 
     * @param exportGroup
     * @param protection
     * @param dbClient
     * @return Map of Storage System URI to map of BlockObject URI to lun id Integer
     */
    public static Map<URI, Map<URI, Integer>> getStorageToVolumeMap(ExportGroup exportGroup, boolean protection, DbClient dbClient) {
        Map<URI, Map<URI, Integer>> map = new HashMap<URI, Map<URI, Integer>>();

        StringMap volumes = exportGroup.getVolumes();
        if (volumes == null) {
            return map;
        }

        for (String uriString : volumes.keySet()) {
            URI blockURI = URI.create(uriString);
            BlockObject block = BlockObject.fetch(dbClient, blockURI);
            // If this is an RP-based Block Snapshot, use the protection controller instead of the underlying block controller
            URI storage = (block.getProtectionController() != null && protection && block.getId().toString().contains("BlockSnapshot")) ?
                    block.getProtectionController() : block.getStorageController();

            if (map.get(storage) == null) {
                map.put(storage, new HashMap<URI, Integer>());
            }
            map.get(storage).put(blockURI, Integer.valueOf(volumes.get(uriString)));
        }
        return map;
    }

    /**
     * Get the possible Varrays a BlockObject can be associated with.
     * Handles the Vplex... which can be the BlockObject's varray,
     * or the HA Virtual array in the Vpool.
     * 
     * @param blockObject
     * @param dbClient
     * @return Set<URI> of Varray URIs
     */
    public static Set<URI> getBlockObjectVarrays(BlockObject blockObject, DbClient dbClient) {
        Set<URI> varrayURIs = new HashSet<URI>();
        varrayURIs.add(blockObject.getVirtualArray());
        VirtualPool vpool = getBlockObjectVirtualPool(blockObject, dbClient);
        if (vpool != null) {
            if (vpool.getHaVarrayVpoolMap() != null) {
                for (String varrayId : vpool.getHaVarrayVpoolMap().keySet()) {
                    URI varrayURI = URI.create(varrayId);
                    if (!varrayURIs.contains(varrayURI)) {
                        varrayURIs.add(varrayURI);
                    }
                }
            }
        }
        return varrayURIs;
    }

    /**
     * Get the Virtual Pool for a Block Object.
     * 
     * @param blockObject
     * @param dbClient
     * @return VirtualPool or null if could not locate
     */
    public static VirtualPool getBlockObjectVirtualPool(BlockObject blockObject, DbClient dbClient) {
        Volume volume = null;
        if (blockObject instanceof BlockSnapshot) {
            BlockSnapshot snapshot = (BlockSnapshot) blockObject;
            volume = dbClient.queryObject(Volume.class, snapshot.getParent());
        } else if (blockObject instanceof Volume) {
            volume = (Volume) blockObject;
        }
        if (volume != null) {
            VirtualPool vpool = dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
            return vpool;
        }
        return null;
    }

    /**
     * Filters Initiators for non-VPLEX systems by the ExportGroup varray.
     * Initiators not in the Varray are removed from the newInitiators list.
     * 
     * @param exportGroup -- ExportGroup used to get virtual array.
     * @param newInitiators -- List of new initiators to be processed
     * @param storageURI -- storage system URI
     * @param dbClient -- DbClient for database
     * @return filteredInitiators -- New list of filtered initiators
     */
    public static List<URI> filterNonVplexInitiatorsByExportGroupVarray(
            ExportGroup exportGroup, List<URI> newInitiators, URI storageURI, DbClient dbClient) {
        List<URI> filteredInitiators = new ArrayList<URI>(newInitiators);
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageURI);
        // in the case of RecoverPoint, storageURI refers to a ProtectionSystem so storageSystem will be null
        if (storageSystem != null && !storageSystem.getSystemType().equals(DiscoveredDataObject.Type.vplex.name())) {
            filterOutInitiatorsNotAssociatedWithVArray(exportGroup.getVirtualArray(), filteredInitiators, dbClient);
        }
        return filteredInitiators;
    }

    /**
     * Routine will examine the 'newInitiators' list and remove any that do not have any association
     * to the VirtualArray.
     * 
     * @param virtualArrayURI [in] - VirtualArray URI reference
     * @param newInitiators [in/out] - List of initiator URIs to examine.
     * @param dbClient [in] -- Used to access database
     */
    public static void filterOutInitiatorsNotAssociatedWithVArray(URI virtualArrayURI,
            List<URI> newInitiators, DbClient dbClient) {
        Iterator<URI> it = newInitiators.iterator();
        while (it.hasNext()) {
            URI uri = it.next();
            Initiator initiator = dbClient.queryObject(Initiator.class, uri);
            if (initiator == null) {
                _log.info(String.format("Initiator %s was not found in DB. Will be eliminated from request payload.",
                        uri.toString()));
                it.remove();
                continue;
            }
            if (!isInitiatorInVArraysNetworks(virtualArrayURI, initiator, dbClient)) {
                _log.info(String.format("Initiator %s (%s) will be eliminated from the payload " +
                        "because it was not associated with Virtual Array %s",
                        initiator.getInitiatorPort(), initiator.getId().toString(),
                        virtualArrayURI.toString()));
                it.remove();
            }
        }
    }

    /**
     * Validate if the initiator is linked to the VirtualArray through some Network
     * 
     * @param virtualArrayURI [in] - VirtualArray URI reference
     * @param initiator [in] - the initiator
     * @return true iff the initiator belongs to a Network and that Network has the VirtualArray
     */
    public static boolean isInitiatorInVArraysNetworks(URI virtualArrayURI, Initiator initiator, DbClient dbClient) {
        boolean foundAnAssociatedNetwork = false;
        Set<NetworkLite> networks = NetworkUtil.getEndpointAllNetworksLite(initiator.getInitiatorPort(), dbClient);
        if (networks == null || networks.isEmpty()) {
            // No network associated with the initiator, so it should be removed from the list
            _log.info(String.format("Initiator %s (%s) is not associated with any network.",
                    initiator.getInitiatorPort(), initiator.getId().toString()));
            return false;
        } else {
            // Search through the networks determining if the any are associated with ExportGroup's VirtualArray.
            for (NetworkLite networkLite : networks) {
                if (networkLite == null) {
                    continue;
                }
                Set<String> varraySet = networkLite.fetchAllVirtualArrays();
                if (varraySet != null && varraySet.contains(virtualArrayURI.toString())) {
                    _log.info(String.format("Initiator %s (%s) was found to be associated to VirtualArray %s through network %s.",
                            initiator.getInitiatorPort(), initiator.getId().toString(), virtualArrayURI.toString(),
                            networkLite.getNativeGuid()));
                    foundAnAssociatedNetwork = true;
                    // Though we could break this loop here, let's continue the loop so that
                    // we can log what other networks that the initiator is seen in
                }
            }
        }
        return foundAnAssociatedNetwork;
    }

    /**
     * Check if any ExportGroups passed in contain the initiator
     * 
     * @param dbClient [in] - DB client object
     * @param exportGroupURIs [in] - List of ExportGroup URIs referencing ExportGroups to check
     * @param initiator [in] - The initiator check
     * @return true if any of the ExportGroups referenced in the exportGroupURIs list has the initiator
     */
    public static boolean checkIfAnyExportGroupsContainInitiator(DbClient dbClient, Set<URI> exportGroupURIs, Initiator initiator) {
        Iterator<ExportGroup> exportGroupIterator =
                dbClient.queryIterativeObjects(ExportGroup.class, exportGroupURIs, true);
        while (exportGroupIterator.hasNext()) {
            ExportGroup exportGroup = exportGroupIterator.next();
            if (exportGroup.hasInitiator(initiator)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if any ExportGroups passed in contain the initiator and block object
     * 
     * @param dbClient [in] - DB client object
     * @param exportGroupURIs [in] - List of ExportGroup URIs referencing ExportGroups to check
     * @param initiator [in] - The initiator check
     * @param blockObject [in] - The block object
     * @return true if any of the ExportGroups referenced in the exportGroupURIs list has the initiator
     */
    public static boolean checkIfAnyExportGroupsContainInitiatorAndBlockObject(
            DbClient dbClient, Set<URI> exportGroupURIs, Initiator initiator,
            BlockObject blockObject) {
        Iterator<ExportGroup> exportGroupIterator =
                dbClient.queryIterativeObjects(ExportGroup.class, exportGroupURIs, true);
        while (exportGroupIterator.hasNext()) {
            ExportGroup exportGroup = exportGroupIterator.next();
            if (exportGroup.hasInitiator(initiator) && exportGroup.hasBlockObject(blockObject.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the list of export groups referencing the mask
     * 
     * @param uri the export mask UTI
     * @param dbClient and instance of {@link DbClient}
     * @return the list of export groups referencing the mask
     */
    public static List<ExportGroup> getExportGroupsForMask(URI uri, DbClient dbClient) {
        URIQueryResultList exportGroupUris = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.
                Factory.getExportMaskExportGroupConstraint(uri), exportGroupUris);
        return DataObjectUtils.iteratorToList(dbClient.queryIterativeObjects(ExportGroup.class,
                DataObjectUtils.iteratorToList(exportGroupUris)));
    }

    /**
     * Find out if the mirror is part of any export group/export mask.
     * If yes, remove the mirror and add the promoted volume.
     * 
     * @param mirror
     * @param promotedVolume
     * @param dbClient
     */
    public static void updatePromotedMirrorExports(BlockMirror mirror, Volume promotedVolume, DbClient dbClient) {
        URIQueryResultList egUris = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.
                Factory.getBlockObjectExportGroupConstraint(mirror.getId()), egUris);
        List<ExportGroup> exportGroups = dbClient.queryObject(ExportGroup.class, egUris);
        Set<ExportMask> mirrorExportMasks = new HashSet<ExportMask>();
        List<DataObject> updatedObjects = new ArrayList<DataObject>();
        for (ExportGroup exportGroup : exportGroups) {
            if (!exportGroup.getInactive() && exportGroup.getExportMasks() != null) {
                List<URI> exportMasks = new ArrayList<URI>(Collections2.transform(
                        exportGroup.getExportMasks(), CommonTransformerFunctions.FCTN_STRING_TO_URI));
                mirrorExportMasks.addAll(dbClient.queryObject(ExportMask.class, exportMasks));
                // remove the mirror from export group and add the promoted volume
                String lunString = exportGroup.getVolumes().get(mirror.getId().toString());
                _log.info("Removing mirror {} from export group {}", mirror.getId(), exportGroup.getId());
                exportGroup.removeVolume(mirror.getId());
                _log.info("Adding promoted volume {} to export group {}", promotedVolume.getId(), exportGroup.getId());
                exportGroup.getVolumes().put(promotedVolume.getId().toString(), lunString);
                updatedObjects.add(exportGroup);
            }
        }

        for (ExportMask exportMask : mirrorExportMasks) {
            if (!exportMask.getInactive()
                    && exportMask.getStorageDevice().equals(mirror.getStorageController())
                    && exportMask.hasVolume(mirror.getId())
                    && exportMask.getInitiators() != null && exportMask.getStoragePorts() != null) {
                String lunString = exportMask.getVolumes().get(mirror.getId().toString());
                _log.info("Removing mirror {} from export mask {}", mirror.getId(), exportMask.getId());
                exportMask.removeVolume(mirror.getId());
                exportMask.removeFromUserCreatedVolumes(mirror);
                _log.info("Adding promoted volume {} to export mask {}", promotedVolume.getId(), exportMask.getId());
                exportMask.addToUserCreatedVolumes(promotedVolume);
                exportMask.getVolumes().put(promotedVolume.getId().toString(), lunString);
                updatedObjects.add(exportMask);
            }
        }

        dbClient.updateAndReindexObject(updatedObjects);
    }

    /**
     * Find all the ports in a storage system that can be assigned in a given virtual array. These are
     * registered ports that are assigned to the virtual array, in good discovery and operational status.
     * 
     * @param dbClient an instance of {@link DbClient}
     * @param storageSystemURI the URI of the storage system
     * @param varrayURI the virtual array
     * @param pathParams ExportPathParams may contain a set of allowable ports. Optional, can be null.
     * @return a list of storage ports that are in good operational status and assigned to the virtual array
     */
    public static List<StoragePort> getStorageSystemAssignablePorts(DbClient dbClient, URI storageSystemURI, 
            URI varrayURI, ExportPathParams pathParams) {
        URIQueryResultList sports = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.
                getStorageDeviceStoragePortConstraint(storageSystemURI), sports);
        Iterator<URI> it = sports.iterator();
        List<StoragePort> spList = new ArrayList<StoragePort>();
        List<String> notRegisteredOrOk = new ArrayList<String>();
        List<String> notInVarray = new ArrayList<String>();
        List<String> notInPathParams = new ArrayList<String>();
        while (it.hasNext()) {
            StoragePort sp = dbClient.queryObject(StoragePort.class, it.next());
            if (sp.getInactive() || sp.getNetwork() == null
                    || !DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name().equals(sp.getCompatibilityStatus())
                    || !DiscoveryStatus.VISIBLE.name().equals(sp.getDiscoveryStatus())
                    || !sp.getRegistrationStatus().equals(StoragePort.RegistrationStatus.REGISTERED.name())
                    || StoragePort.OperationalStatus.NOT_OK.equals(StoragePort.OperationalStatus.valueOf(sp.getOperationalStatus()))
                    || StoragePort.PortType.valueOf(sp.getPortType()) != StoragePort.PortType.frontend) {
                _log.debug(
                        "Storage port {} is not selected because it is inactive, is not compatible, is not visible, has no network assignment, "
                                +
                                "is not registered, has a status other than OK, or is not a frontend port", sp.getLabel());
                notRegisteredOrOk.add(sp.qualifiedPortName());
            } else if (sp.getTaggedVirtualArrays() == null || !sp.getTaggedVirtualArrays().contains(varrayURI.toString())) {
                _log.debug("Storage port {} not selected because it is not connected " +
                        "or assigned to requested virtual array {}", sp.getNativeGuid(), varrayURI);
                notInVarray.add(sp.qualifiedPortName());
            } else if (pathParams != null && !pathParams.getStoragePorts().isEmpty() 
                    && !pathParams.getStoragePorts().contains(sp.getId().toString())) {
                _log.debug("Storage port {} not selected because it is not in ExportPathParams port list", sp.getNativeGuid());
                notInPathParams.add(sp.qualifiedPortName());
            } else {
                spList.add(sp);
            }
        }
        if (!notRegisteredOrOk.isEmpty()) {
            _log.info("Ports not selected because they are inactive, have no network assignment, " +
                    "are not registered, bad operational status, or not type front-end: "
                    + Joiner.on(" ").join(notRegisteredOrOk));
        }
        if (!notInVarray.isEmpty()) {
            _log.info("Ports not selected because they are not assigned to the requested virtual array: "
                    + varrayURI + " " + Joiner.on(" ").join(notInVarray));
        }
        if (!notInPathParams.isEmpty()) {
            _log.info("Ports not selected because they are not in the ExportPathParams port list: " 
                    + Joiner.on(" ").join(notInPathParams));
        }
        return spList;
    }

    /**
     * Given a list of storage ports and networks, map the ports to the networks. If the port network
     * is in the networks collection, the port is mapped to it. If the port network is not in the
     * networks collection but can is routed to it, then the port is mapped to the routed network.
     * 
     * @param ports the ports to be mapped to their networks
     * @param networks the networks
     * @param _dbClient and instance of DbClient
     * @return a map of networks and ports that can be used by initiators in the network.
     */
    public static Map<NetworkLite, List<StoragePort>> mapStoragePortsToNetworks(Collection<StoragePort> ports,
            Collection<NetworkLite> networks, DbClient _dbClient) {
        Map<NetworkLite, List<StoragePort>> localPorts = new HashMap<NetworkLite, List<StoragePort>>();
        Map<NetworkLite, List<StoragePort>> remotePorts = new HashMap<NetworkLite, List<StoragePort>>();
        for (NetworkLite network : networks) {
            for (StoragePort port : ports) {
                if (port.getNetwork().equals(network.getId())) {
                    StringMapUtil.addToListMap(localPorts, network, port);
                } else if (network.hasRoutedNetworks(port.getNetwork())) {
                    StringMapUtil.addToListMap(remotePorts, network, port);
                }
            }
        }
        // consolidate local and remote ports
        for (NetworkLite network : networks) {
            if (localPorts.get(network) == null && remotePorts.get(network) != null) {
                localPorts.put(network, remotePorts.get(network));
            }
        }
        return localPorts;
    }

    /**
     * Consolidate the assignments made from pre-zoned ports with those made by ordinary port assignment.
     * existingAndPrezonedZoningMap contains all pre-existing assignments plus those made from pre-zoned
     * ports. exportMask.zoningMap contains all pre-existing assignments, and 'assignments' has all ports
     * made by ordinary assignment.
     * 
     * The function consolidate the targets to be added to the masking view by adding those taken from
     * pre-zoned ports to those taken from the other set of ports. The way ports taken from pre-zoned
     * ports are identified is by comparing existingAndPrezonedZoningMap to exportMask.zoningMap, these
     * are ports assigned to initiators found in existingAndPrezonedZoningMap but not in exportMask.zoningMap.
     * This is because the port assignment never adds new ports to already used initiators.
     * 
     * @param exportMaskZoningMap -- the export mask zoningMap before any assignments are made
     * @param assignments -- assignments made from all ports not based on what is pre-zoned.
     * @param existingAndPrezonedZoningMap -- assignments made from pre-zoned ports plus all pre-existing assignments.
     */
    public static void addPrezonedAssignments(StringSetMap exportMaskZoningMap, Map<URI, List<URI>> assignments,
            StringSetMap existingAndPrezonedZoningMap) {
        for (String iniUriStr : existingAndPrezonedZoningMap.keySet()) {
            StringSet iniPorts = new StringSet(existingAndPrezonedZoningMap.get(iniUriStr));
            if (exportMaskZoningMap != null) {
                if (exportMaskZoningMap.containsKey(iniUriStr)) {
                    iniPorts.removeAll(exportMaskZoningMap.get(iniUriStr));
                }
            }
            if (!iniPorts.isEmpty()) {
                URI iniUri = URI.create(iniUriStr);
                if (!assignments.containsKey(iniUri)) {
                    assignments.put(iniUri, new ArrayList<URI>());
                }
                assignments.get(iniUri).addAll(StringSetUtil.stringSetToUriList(iniPorts));
            }
        }
    }

    /**
     * Gets the ExportGroup to be used for VPlex when reusing an ExportMask.
     * Will find the ExportGroup containing the mask, or it will create a new one if necessary.
     *
     * @param vplex - VPlex StorageSystem
     * @param array - Backend StorageSystem
     * @param virtualArrayURI - VirtualArray to which the VPlex and backend StorageSystem apply
     * @param mask - ExportMask that is being reused
     * @param initiators - Collection<Initiator> VPLEX initiators to array
     * @param tenantURI - Tenant URI
     * @param projectURI - Project URI
     * @return ExportGroup that is applicable for the Vplex and backend array
     */
    public static ExportGroup getVPlexExportGroup(DbClient dbClient, StorageSystem vplex, StorageSystem array, URI virtualArrayURI,
            ExportMask mask, Collection<Initiator> initiators, URI tenantURI, URI projectURI) {
        // Determine all the possible existing Export Groups
        Map<String, ExportGroup> possibleExportGroups = new HashMap<String, ExportGroup>();
        for (Initiator initiator : initiators) {
            List<ExportGroup> groups = ExportUtils.getInitiatorExportGroups(initiator, dbClient);
            for (ExportGroup group : groups) {
                if (!possibleExportGroups.containsKey(group.getId().toString())) {
                    possibleExportGroups.put(group.getId().toString(), group);
                }
            }
        }

        // If there are possible Export Groups, look for one with this mask.
        for (ExportGroup group : possibleExportGroups.values()) {
            if (group.hasMask(mask.getId())) {
                _log.info(String.format("Returning ExportGroup %s", group.getLabel()));
                return group;
            }
        }

        return createVplexExportGroup(dbClient, vplex, array, initiators, virtualArrayURI, projectURI, tenantURI, 0, mask);
    }

    /**
     * Create an ExportGroup.
     *
     * @param vplex -- VPLEX StorageSystem
     * @param array -- Array StorageSystem
     * @param initiators -- Collection<Initiator> representing VPLEX back-end ports.
     * @param virtualArrayURI
     * @param projectURI
     * @param tenantURI
     * @param numPaths Value of maxPaths to be put in ExportGroup
     * @param exportMask IFF non-null, will add the exportMask to the Export Group.
     * @return newly created ExportGroup persisted in DB.
     */
    public static ExportGroup createVplexExportGroup(DbClient dbClient, StorageSystem vplex, StorageSystem array,
            Collection<Initiator> initiators, URI virtualArrayURI, URI projectURI, URI tenantURI, int numPaths, ExportMask exportMask) {
        String groupName = getExportGroupName(vplex, array)
                + "_" + UUID.randomUUID().toString().substring(28);
        if (exportMask != null) {
            String arrayName = array.getSystemType().replace("block", "")
                    + array.getSerialNumber().substring(array.getSerialNumber().length() - 4);
            groupName = exportMask.getMaskName() + "_" + arrayName;
        }

        // No existing group has the mask, let's create one.
        ExportGroup exportGroup = new ExportGroup();
        exportGroup.setId(URIUtil.createId(ExportGroup.class));
        exportGroup.setLabel(groupName);
        exportGroup.setProject(new NamedURI(projectURI, exportGroup.getLabel()));
        exportGroup.setVirtualArray(vplex.getVirtualArray());
        exportGroup.setTenant(new NamedURI(tenantURI, exportGroup.getLabel()));
        exportGroup.setGeneratedName(groupName);
        exportGroup.setVolumes(new StringMap());
        exportGroup.setOpStatus(new OpStatusMap());
        exportGroup.setVirtualArray(virtualArrayURI);
        exportGroup.setNumPaths(numPaths);

        // Add the initiators into the ExportGroup.
        for (Initiator initiator : initiators) {
            exportGroup.addInitiator(initiator);
        }

        // If we have an Export Mask, add it into the Export Group.
        if (exportMask != null) {
            exportGroup.addExportMask(exportMask.getId());
        }

        // Persist the ExportGroup
        dbClient.createObject(exportGroup);
        _log.info(String.format("Returning new ExportGroup %s", exportGroup.getLabel()));
        return exportGroup;
    }

    /**
     * Returns the ExportGroup name to be used between a particular VPlex and underlying Storage Array.
     * It is based on the serial numbers of the Vplex and Array. Therefore the same ExportGroup name
     * will always be used, and it always starts with "VPlex".
     *
     * @param vplex [IN] - VPlex StorageArray
     * @param array [IN] - Backend StorageArray
     * @return String that represents the unique combination of Vplex-to-backend array
     */
    public static String getExportGroupName(StorageSystem vplex, StorageSystem array) {
        // Unfortunately, using the full VPlex serial number with the Array serial number
        // proves to be quite lengthy! We can run into issues on SMIS where
        // max length (represented as STOR_DEV_GROUP_MAX_LEN) is 64 characters.
        // Not to mention, other steps append to this name too.
        // So lets chop everything but the last 4 digits from both serial numbers.
        // This should be unique enough.
        int endIndex = vplex.getSerialNumber().length();
        int beginIndex = endIndex - 4;
        String modfiedVPlexSerialNumber = vplex.getSerialNumber().substring(beginIndex, endIndex);

        endIndex = array.getSerialNumber().length();
        beginIndex = endIndex - 4;
        String modfiedArraySerialNumber = array.getSerialNumber().substring(beginIndex, endIndex);

        return String.format("VPlex_%s_%s", modfiedVPlexSerialNumber, modfiedArraySerialNumber);
    }
    
    /**
     * Given an updatedBlockObjectMap (maps BlockObject URI to Lun Integer) representing the desired state,
     * and an Export Group, makes addedBlockObjects containing the entries that were added,
     * and removedBlockObjects containing the entries that were removed.
     * @param updatedBlockObjectMap : desired state of the Block Object Map
     * @param exportGroup : existing map taken from exportGroup.getVolumes()
     * @param addedBlockObjects : OUTPUT - contains map of added Block Objects
     * @param removedBlockObjects : OUTPUT -- contains map of removed Block Objects
     */
    public static void getAddedAndRemovedBlockObjects(Map<URI, Integer> updatedBlockObjectMap, 
            ExportGroup exportGroup, Map<URI, Integer> addedBlockObjects, Map<URI, Integer> removedBlockObjects) {
        Map<URI, Integer> existingBlockObjectMap = StringMapUtil.stringMapToVolumeMap(exportGroup.getVolumes());
        // Determine the removed entries; they are in existing but not updated
        for (Entry<URI, Integer> entry : existingBlockObjectMap.entrySet()) {
            if (!updatedBlockObjectMap.keySet().contains(entry.getKey())) {
                removedBlockObjects.put(entry.getKey(), entry.getValue());
            }
        }
        // Determine the added entries; they are in updated but not existing
        for (Entry<URI, Integer> entry : updatedBlockObjectMap.entrySet()) {
            if (!existingBlockObjectMap.keySet().contains(entry.getKey())) {
                addedBlockObjects.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * This routine will examine the ExportGroup and ExportMask and attempt to reconcile its HLUs.
     * This would include volumes in 'volumeMap' and any that appear to not have their HLUs filled in.
     * For this routine we only care about ExportMasks that were created by the system.
     *
     * NOTE: ExportGroup is not persisted here.
     *
     * @param dbClient [IN] - DbClient for DB access
     * @param exportGroup [IN] - ExportGroup to update with HLUs
     * @param exportMask [IN] - ExportMask that we updated for this add volumes request
     */
    public static void reconcileHLUs(DbClient dbClient, ExportGroup exportGroup, ExportMask exportMask, Map<URI, Integer> volumeMap) {
        // We should only care to do this when there are system created ExportMasks that have volumes
        if (exportMask.getCreatedBySystem() && exportMask.getVolumes() != null) {
            // CTRL-11544: Set the hlu in the export group too
            for (URI boURI : volumeMap.keySet()) {
                String hlu = exportMask.returnVolumeHLU(boURI);
                _log.info(String.format("ExportGroup %s (%s) update volume HLU: %s -> %s", exportGroup.getLabel(), exportGroup.getId(),
                        boURI, hlu));
                exportGroup.addVolume(boURI, Integer.parseInt(hlu));
            }
            reconcileExportGroupsHLUs(dbClient, exportGroup);
        }
    }

    /**
     * Examine ExportGroup's volumes to find any that do not have their HLU filled in. In case it is not filled, the ExportMasks
     * will be searched to find an HLU to assign for the volume.
     *
     * NOTE: ExportGroup is not persisted here.
     *
     * @param dbClient [IN] - DbClient for DB access
     * @param exportGroup [IN] - ExportGroup to examine volumes
     */
    public static void reconcileExportGroupsHLUs(DbClient dbClient, ExportGroup exportGroup) {
        // Find the volumes that don't have their HLU filled in ...
        List<String> egVolumesWithoutHLUs = findVolumesWithoutHLUs(exportGroup);
        if (!egVolumesWithoutHLUs.isEmpty()) {
            // There are volumes in the ExportGroup that don't have their HLUs filled in.
            // Search through each ExportMask associated with the ExportGroup ...
            for (ExportMask thisMask : ExportMaskUtils.getExportMasks(dbClient, exportGroup)) {
                Iterator<String> volumeIter = egVolumesWithoutHLUs.iterator();
                while (volumeIter.hasNext()) {
                    URI volumeURI = URI.create(volumeIter.next());
                    if (thisMask.hasVolume(volumeURI)) {
                        // This ExportMask has the volume we're interested in.
                        String hlu = thisMask.returnVolumeHLU(volumeURI);
                        // Let's apply its HLU if it's not the 'Unassigned' value ...
                        if (hlu != ExportGroup.LUN_UNASSIGNED_DECIMAL_STR) {
                            _log.info(String.format("ExportGroup %s (%s) update volume HLU: %s -> %s", exportGroup.getLabel(),
                                    exportGroup.getId(), volumeURI, hlu));
                            exportGroup.addVolume(volumeURI, Integer.valueOf(hlu));
                            // Now that we've found an HLU for this volume, there's no need to search for it in other ExportMasks.
                            // Let's remove it from the array list.
                            volumeIter.remove();
                        }
                    }
                }
            }
        }
    }

    /**
     * Return a list of Volume URI Strings that have ExportGroup.LUN_UNASSIGNED_DECIMAL_STR as their HLU
     *
     * @param exportGroup [IN] - ExportGroup to check
     *
     * @return List or Volume URI Strings
     */
    public static List<String> findVolumesWithoutHLUs(ExportGroup exportGroup) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : exportGroup.getVolumes().entrySet()) {
            String volumeURIStr = entry.getKey();
            String hlu = entry.getValue();
            if (hlu.equals(ExportGroup.LUN_UNASSIGNED_DECIMAL_STR)) {
                result.add(volumeURIStr);
            }
        }
        return result;
    }
}
