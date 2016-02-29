/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.ResourceService;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;

/**
 * Block Ingest Export Orchestration responsible for ingesting exported block objects.
 */
public abstract class BlockIngestExportOrchestrator extends ResourceService {

    private static final Logger _logger = LoggerFactory.getLogger(BlockIngestExportOrchestrator.class);

    /**
     * Ingests UnManagedExportMasks associated with the current UnManagedVolume being processed.
     *
     * @param requestContext the IngestionRequestContext for this ingestion process
     * @param unManagedVolume unManagedVolume to ingest
     * @param blockObject created BlockObject
     * @param unManagedMasks list of unmanaged masks this unmanaged volume is associated with
     * @param masksIngestedCount number of export masks ingested
     */
    protected <T extends BlockObject> void ingestExportMasks(IngestionRequestContext requestContext,
            UnManagedVolume unManagedVolume, T blockObject,
            List<UnManagedExportMask> unManagedMasks, MutableInt masksIngestedCount)
                    throws IngestionException {
        try {
            _logger.info("Starting with unmanaged masks {} for unmanaged volume {}",
                    Joiner.on(",").join(unManagedVolume.getUnmanagedExportMasks()), unManagedVolume.getNativeGuid());
            List<UnManagedExportMask> uemsToPersist = new ArrayList<UnManagedExportMask>();
            Iterator<UnManagedExportMask> itr = unManagedMasks.iterator();

            List<String> errorMessages = requestContext.getErrorMessagesForVolume(unManagedVolume.getNativeGuid());

            ExportGroup exportGroup = requestContext.getExportGroup();
            StorageSystem system = requestContext.getStorageSystem();
            Host host = null;
            Cluster cluster = null;
            List<Host> hosts = new ArrayList<Host>();
            String exportGroupType = null;
            if (null != requestContext.getHost()) {
                host = _dbClient.queryObject(Host.class, requestContext.getHost());
                hosts.add(host);
                exportGroupType = ExportGroupType.Host.name();
            }

            if (null != requestContext.getCluster()) {
                cluster = _dbClient.queryObject(Cluster.class, requestContext.getCluster());
                hosts.addAll(getHostsOfCluster(requestContext.getCluster()));
                exportGroupType = ExportGroupType.Cluster.name();
            }

            // In cluster/Host , if we don't find at least 1 initiator in
            // registered state, then skip this volume from ingestion.
            StringSet computeInitiators = new StringSet();
            for (Host hostObj : hosts) {
                Set<String> initiatorSet = getInitiatorsOfHost(hostObj.getId());
                Set<URI> initiatorUris = new HashSet<URI>(Collections2.transform(initiatorSet,
                        CommonTransformerFunctions.FCTN_STRING_TO_URI));
                List<Initiator> initiators = _dbClient.queryObject(Initiator.class, initiatorUris);

                if (!VolumeIngestionUtil.validateInitiatorPortsRegistered(initiators)) {
                    // logs already inside the above method.
                    _logger.warn("Host skipped {} as we can't find at least 1 initiator in registered status", hostObj.getLabel());
                    return;
                }
                
                computeInitiators.addAll(initiatorSet);
            }

            if (null != requestContext.getDeviceInitiators() && !requestContext.getDeviceInitiators().isEmpty()) {
                if (exportGroup.checkInternalFlags(Flag.RECOVERPOINT)) {
                    // RP export groups are cluster-based, although they don't contains a cluster/host ID 
                    exportGroupType = ExportGroupType.Cluster.name();
                } else {
                    // note: ViPR-generated greenfield VPLEX export groups
                    // actually have no export group type set
                    exportGroupType = ExportGroupType.Host.name();
                }

                if (!VolumeIngestionUtil.validateInitiatorPortsRegistered(requestContext.getDeviceInitiators())) {
                    _logger.warn("Device with initiators {} skipped as we can't "
                            + "find at least 1 initiator in registered status",
                            requestContext.getDeviceInitiators());
                    return;
                }
                
                // For validation checks below, add these initiator to the compute resource list
                for (Initiator initiator : requestContext.getDeviceInitiators()) {
                    computeInitiators.add(initiator.getId().toString());
                }
            }

            // update the ExportGroupType in UnManagedVolume. This will be used to place the
            // volume in the right ExportGroup based on the ExportGroupType.
            updateExportTypeInUnManagedVolume(unManagedVolume, exportGroupType);

            // If we find an existing export mask in DB, with the expected set of initiators,
            // then add this unmanaged volume to the mask.
            while (itr.hasNext()) {
                UnManagedExportMask unManagedExportMask = itr.next();
                if (!VolumeIngestionUtil.validateStoragePortsInVarray(_dbClient, blockObject,
                        requestContext.getVarray(unManagedVolume).getId(), unManagedExportMask.getKnownStoragePortUris(),
                        unManagedExportMask, errorMessages)) {
                    // logs already inside the above method.
                    itr.remove();
                    continue;
                }
                if (!VolumeIngestionUtil.validateExportMaskMatchesComputeResourceInitiators(_dbClient, exportGroup, computeInitiators,
                        unManagedExportMask, errorMessages)) {
                    // logs already inside the above method.
                    itr.remove();
                    continue;
                }
                _logger.info("looking for an existing export mask for " + unManagedExportMask.getMaskName());
                ExportMask exportMask = getExportsMaskAlreadyIngested(unManagedExportMask, _dbClient);
                if (null == exportMask) {
                    _logger.info("\tno mask found");
                    continue;
                }
                _logger.info("Export Mask {} already available", exportMask.getMaskName());
                masksIngestedCount.increment();
                List<URI> iniList = new ArrayList<URI>(Collections2.transform(exportMask.getInitiators(),
                        CommonTransformerFunctions.FCTN_STRING_TO_URI));
                List<Initiator> initiators = _dbClient.queryObject(Initiator.class, iniList);

                // if the block object is marked as internal then add it to existing volumes
                // of the mask, else add it to user created volumes
                if (blockObject.checkInternalFlags(Flag.PARTIALLY_INGESTED)) {
                    _logger.info("Block object {} is marked internal. Adding to existing volumes of the mask {}",
                            blockObject.getNativeGuid(), exportMask.getMaskName());
                    exportMask.addToExistingVolumesIfAbsent(blockObject, ExportGroup.LUN_UNASSIGNED_STR);
                } else {
                    exportMask.addToUserCreatedVolumes(blockObject);
                    // remove this volume if already in existing
                    exportMask.removeFromExistingVolumes(blockObject);
                }

                // Add new initiators found in ingest to list if absent.
                exportMask.addInitiators(initiators);
                // Add all unknown initiators to existing
                exportMask.addToExistingInitiatorsIfAbsent(new ArrayList(unManagedExportMask.getUnmanagedInitiatorNetworkIds()));
                // Always set this flag to true for ingested masks.
                exportMask.setCreatedBySystem(true);

                List<Initiator> userAddedInis = VolumeIngestionUtil.findUserAddedInisFromExistingIniListInMask(initiators,
                        unManagedExportMask.getId(), _dbClient);
                exportMask.addToUserCreatedInitiators(userAddedInis);

                // remove from existing if present - possible in ingestion after
                // coexistence
                exportMask.removeFromExistingInitiator(userAddedInis);

                // need to sync up all remaining existing volumes
                Map<String, Integer> wwnToHluMap = VolumeIngestionUtil.extractWwnToHluMap(unManagedExportMask, _dbClient);
                exportMask.addToExistingVolumesIfAbsent(wwnToHluMap);

                // find the HLU and set it in the volumes
                Integer hlu = ExportGroup.LUN_UNASSIGNED;
                if (wwnToHluMap.containsKey(blockObject.getWWN())) {
                    hlu = wwnToHluMap.get(blockObject.getWWN());
                }
                exportMask.addVolume(blockObject.getId(), hlu);

                // adding volume we need to add FCZoneReferences
                StringSetMap zoneMap = ExportMaskUtils.getZoneMapFromZoneInfoMap(unManagedExportMask.getZoningMap(), initiators);
                if (!zoneMap.isEmpty()) {
                    exportMask.setZoningMap(zoneMap);
                }

                requestContext.addDataObjectToUpdate(exportMask, unManagedVolume);
                ExportMaskUtils.updateFCZoneReferences(exportGroup, blockObject, unManagedExportMask.getZoningMap(), initiators,
                        _dbClient);

                // remove the unmanaged mask from unmanaged volume only if the block object has not been marked as internal
                if (!blockObject.checkInternalFlags(Flag.PARTIALLY_INGESTED)) {
                    _logger.info("breaking relationship between UnManagedExportMask {} and UnManagedVolume {}",
                            unManagedExportMask.getMaskName(), unManagedVolume.forDisplay());
                    unManagedVolume.getUnmanagedExportMasks().remove(unManagedExportMask.getId().toString());
                    unManagedExportMask.getUnmanagedVolumeUris().remove(unManagedVolume.getId().toString());
                    uemsToPersist.add(unManagedExportMask);
                }

                if (exportGroup.getExportMasks() == null || !exportGroup.getExportMasks().contains(exportMask.getId().toString())) {
                    exportGroup.addExportMask(exportMask.getId().toString());
                }

                VolumeIngestionUtil.updateExportGroup(exportGroup, blockObject, _dbClient, initiators, hosts, cluster);

                _logger.info("Removing unmanaged mask {} from the list of items to process, as block object is added already",
                        unManagedExportMask.getMaskName());
                itr.remove();

                requestContext.addDataObjectToUpdate(exportMask, unManagedVolume);
            }

            _logger.info("{} unmanaged mask(s) remaining to process", unManagedMasks.size());

            List<ExportMask> exportMasksToCreate = new ArrayList<ExportMask>();
            List<UnManagedExportMask> eligibleMasks = null;
            if (!unManagedMasks.isEmpty()) {
                if (null != cluster) {
                    _logger.info("Processing Cluster {} with label {}", cluster.getId(), cluster.getLabel());

                    // get Hosts for Cluster & get Initiators by Host Name
                    // TODO handle multiple Hosts in one call
                    List<URI> hostUris = ComputeSystemHelper
                            .getChildrenUris(_dbClient, requestContext.getCluster(), Host.class, "cluster");
                    _logger.info("Found Hosts {} in cluster {}", Joiner.on(",").join(hostUris), cluster.getId());
                    List<Set<String>> iniGroupByHost = new ArrayList<Set<String>>();
                    for (URI hostUri : hostUris) {
                        iniGroupByHost.add(getInitiatorsOfHost(hostUri));
                    }

                    eligibleMasks = VolumeIngestionUtil.findMatchingExportMaskForCluster(blockObject,
                            unManagedMasks, iniGroupByHost, _dbClient, requestContext.getVarray(unManagedVolume).getId(),
                            requestContext.getVpool(unManagedVolume).getId(), requestContext.getCluster(), errorMessages);
                    // Volume cannot be exposed to both Cluster and Host
                    if (eligibleMasks.size() == 1) {
                        // all initiators of all hosts in 1 MV
                        // add Volume,all Initiators and StoragePorts to
                        // ExportMask
                        _logger.info("Only 1 mask {} found for cluster {}", eligibleMasks.get(0).toString(), cluster.forDisplay());

                        ExportMask exportMaskToCreate = VolumeIngestionUtil.createExportMask(eligibleMasks.get(0), system,
                                unManagedVolume, exportGroup, blockObject,
                                _dbClient, hosts, cluster, cluster.getLabel());
                        exportMasksToCreate.add(exportMaskToCreate);
                        uemsToPersist.add(eligibleMasks.get(0));
                        masksIngestedCount.increment();

                    } else if (eligibleMasks.size() > 1) {
                        _logger.info("Multiple masks ({}) found for cluster {}", Joiner.on(";").join(eligibleMasks),
                                cluster.forDisplay());
                        // 1 MV per Cluster Node
                        for (UnManagedExportMask eligibleMask : eligibleMasks) {
                            ExportMask exportMaskToCreate = VolumeIngestionUtil.createExportMask(eligibleMask, system,
                                    unManagedVolume, exportGroup, blockObject,
                                    _dbClient, hosts, cluster, cluster.getLabel());
                            exportMasksToCreate.add(exportMaskToCreate);
                            uemsToPersist.add(eligibleMask);
                            masksIngestedCount.increment();
                        }
                    }
                } else if (null != host) {
                    _logger.info("Processing Host {} with label {}", host.getId(), host.getLabel());
                    Set<String> initiatorSet = getInitiatorsOfHost(requestContext.getHost());
                    boolean hostPartOfCluster = (!NullColumnValueGetter.isNullURI(host.getCluster()));

                    Map<String, Set<String>> iniByProtocol = VolumeIngestionUtil.groupInitiatorsByProtocol(initiatorSet,
                            _dbClient);
                    eligibleMasks = VolumeIngestionUtil.findMatchingExportMaskForHost(
                            blockObject, unManagedMasks, initiatorSet,
                            iniByProtocol, _dbClient, requestContext.getVarray(unManagedVolume).getId(),
                            requestContext.getVpool(unManagedVolume).getId(), hostPartOfCluster,
                            getInitiatorsOfCluster(host.getCluster(), hostPartOfCluster), null, errorMessages);
                    if (!eligibleMasks.isEmpty()) {
                        _logger.info("Eligible masks {} found for Host {}", Joiner.on(",").join(eligibleMasks), host.getId());
                    } else {
                        _logger.info("No eligible unmanaged export masks found for Host {}", host.getId());
                    }
                    for (UnManagedExportMask eligibleMask : eligibleMasks) {
                        ExportMask exportMaskToCreate = VolumeIngestionUtil.createExportMask(eligibleMask, system,
                                unManagedVolume, exportGroup, blockObject,
                                _dbClient, hosts, cluster, host.getHostName());
                        exportMasksToCreate.add(exportMaskToCreate);
                        uemsToPersist.add(eligibleMask);
                        masksIngestedCount.increment();

                    }
                } else if (null != requestContext.getDeviceInitiators() &&
                        !requestContext.getDeviceInitiators().isEmpty()) {
                    List<Initiator> deviceInitiators = requestContext.getDeviceInitiators();
                    _logger.info("Processing device initiators {}", deviceInitiators);
                    Set<String> initiatorSet = new HashSet<String>();
                    for (Initiator init : deviceInitiators) {
                        initiatorSet.add(init.getId().toString());
                    }
                    boolean hostPartOfCluster = false;

                    Map<String, Set<String>> iniByProtocol = VolumeIngestionUtil.groupInitiatorsByProtocol(initiatorSet, _dbClient);
                    eligibleMasks = VolumeIngestionUtil.findMatchingExportMaskForHost(
                            blockObject, unManagedMasks, initiatorSet,
                            iniByProtocol, _dbClient, requestContext.getVarray(unManagedVolume).getId(),
                            requestContext.getVpool(unManagedVolume).getId(), hostPartOfCluster,
                            getInitiatorsOfCluster(null, hostPartOfCluster), null, errorMessages);
                    if (!eligibleMasks.isEmpty()) {
                        _logger.info("Eligible masks {} found for device initiators {}",
                                Joiner.on(",").join(eligibleMasks), deviceInitiators);
                    } else {
                        _logger.info("No eligible unmanaged export masks found for device initiators {}",
                                deviceInitiators);
                    }
                    for (UnManagedExportMask eligibleMask : eligibleMasks) {
                        // this getHostName will be the name of the VPLEX device
                        ExportMask exportMaskToCreate = VolumeIngestionUtil.createExportMask(eligibleMask, system,
                                unManagedVolume, exportGroup, blockObject,
                                _dbClient, hosts, cluster,
                                deviceInitiators.get(0).getHostName());
                        exportMasksToCreate.add(exportMaskToCreate);
                        uemsToPersist.add(eligibleMask);
                        masksIngestedCount.increment();
                    }
                }
            }

            for (UnManagedExportMask uem : uemsToPersist) {
                requestContext.addDataObjectToUpdate(uem, unManagedVolume);
            }

            for (ExportMask exportMaskToCreate : exportMasksToCreate) {
                requestContext.addDataObjectToCreate(exportMaskToCreate, unManagedVolume);
                exportGroup.addExportMask(exportMaskToCreate.getId());
            }
        } catch (IngestionException e) {
            throw e;
        } catch (Exception e) {
            _logger.error("Export Mask Ingestion failed for UnManaged block object : {}", unManagedVolume.getNativeGuid(), e);
        }
    }

    /**
     * Update the exportGroupType in the unManagedVolume SupportedVolumeInformation.
     *
     * @param unManagedVolume
     * @param exportGroupType
     */
    private void updateExportTypeInUnManagedVolume(
            UnManagedVolume unManagedVolume, String exportGroupType) {
        if (null != exportGroupType) {
            StringMap volumeCharacteristics = unManagedVolume.getVolumeCharacterstics();
            if (null != volumeCharacteristics) {
                volumeCharacteristics.put(SupportedVolumeCharacterstics.EXPORTGROUP_TYPE.toString(), exportGroupType);
            } else {
                _logger.error("UnManagedVolume {} volumeCharacteristics not found.", unManagedVolume.getLabel());
            }
        } else {
            _logger.warn("Unknown ExportGroupType found during ingestion for unManagedVolume: {}", unManagedVolume.getLabel());
        }
    }

    /**
     * Find existing export mask in DB which contains the right set of initiators.
     *
     * @param mask
     * @param dbClient
     * @param iniUriStr
     * @return
     */
    protected abstract ExportMask getExportsMaskAlreadyIngested(UnManagedExportMask mask, DbClient dbClient);

    /**
     * Get initiators of Host from ViPR DB
     *
     * @param hostURI
     * @return
     */
    protected Set<String> getInitiatorsOfHost(URI hostURI) {
        Set<String> initiatorList = new HashSet<String>();
        List<NamedElementQueryResultList.NamedElement> dataObjects = listChildren(hostURI, Initiator.class, "iniport", "host");
        for (NamedElementQueryResultList.NamedElement dataObject : dataObjects) {
            initiatorList.add(dataObject.getId().toString());
        }
        return initiatorList;
    }

    /**
     * Get Initiators of Cluster
     *
     * @param clusterUri
     * @return
     */
    protected Set<String> getInitiatorsOfCluster(URI clusterUri, boolean hostPartOfCluster) {
        Set<String> clusterInis = new HashSet<String>();
        if (!hostPartOfCluster) {
            return clusterInis;
        }
        List<URI> hostUris = ComputeSystemHelper.getChildrenUris(_dbClient, clusterUri, Host.class, "cluster");
        _logger.info("Found Hosts {} in cluster {}", Joiner.on(",").join(hostUris), clusterUri);

        for (URI hostUri : hostUris) {
            clusterInis.addAll(getInitiatorsOfHost(hostUri));
        }
        return clusterInis;
    }

    /**
     * Get Hosts of Cluster
     *
     * @param clusterUri
     * @return
     */
    protected List<Host> getHostsOfCluster(URI clusterUri) {
        List<URI> hostUris = ComputeSystemHelper.getChildrenUris(_dbClient, clusterUri, Host.class, "cluster");
        _logger.info("Found Hosts {} in cluster {}", Joiner.on(",").join(hostUris), clusterUri);

        return _dbClient.queryObject(Host.class, hostUris);

    }

}
