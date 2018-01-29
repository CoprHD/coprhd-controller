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
import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
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
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;

/**
 * Block Ingest Export Orchestration responsible for ingesting exported block objects.
 */
public abstract class BlockIngestExportOrchestrator extends ResourceService {

    private static final Logger _logger = LoggerFactory.getLogger(BlockIngestExportOrchestrator.class);

    protected CustomConfigHandler _customConfigHandler;

    public void setCustomConfigHandler(CustomConfigHandler customConfigHandler) {
        _customConfigHandler = customConfigHandler;
    }

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
            _logger.info("Ingesting unmanaged masks {} for unmanaged volume {}",
                    Joiner.on(",").join(unManagedVolume.getUnmanagedExportMasks()), unManagedVolume.getNativeGuid());
            VolumeIngestionUtil.validateUnManagedExportMasks(unManagedVolume, unManagedMasks, _dbClient);
            List<UnManagedExportMask> uemsToPersist = new ArrayList<UnManagedExportMask>();
            Iterator<UnManagedExportMask> itr = unManagedMasks.iterator();

            List<String> errorMessages = requestContext.getErrorMessagesForVolume(unManagedVolume.getNativeGuid());

            ExportGroup exportGroup = requestContext.getExportGroup();
            StorageSystem system = requestContext.getStorageSystem();
            boolean portGroupEnabled = false;
            if (Type.vmax.name().equals(system.getSystemType())) {
                portGroupEnabled = Boolean.valueOf(
                        _customConfigHandler.getComputedCustomConfigValue(
                                CustomConfigConstants.VMAX_USE_PORT_GROUP_ENABLED,
                                system.getSystemType(), null));
            }
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
                if (VolumeIngestionUtil.isVplexVolume(unManagedVolume)) {
                    boolean crossConnectedDistributedVolume = 
                            VolumeIngestionUtil.isVplexDistributedVolume(unManagedVolume) && 
                            requestContext.getVpool(unManagedVolume).getAutoCrossConnectExport();
                    if (!crossConnectedDistributedVolume &&
                        !VolumeIngestionUtil.isRpExportMask(unManagedExportMask, _dbClient) &&
                        !VolumeIngestionUtil.validateExportMaskMatchesVplexCluster(requestContext, unManagedVolume, unManagedExportMask)) {
                        // logs already inside the above method.
                        itr.remove();
                        continue;
                    }
                }

                _logger.info("looking for an existing export mask for " + unManagedExportMask.getMaskName());
                ExportMask exportMask = getExportMaskAlreadyIngested(unManagedExportMask, _dbClient);

                if (null != exportMask) {
                    // check if mask has already been loaded
                    DataObject loadedExportMask = requestContext.findInUpdatedObjects(exportMask.getId());

                    if (loadedExportMask != null) {
                        exportMask = (ExportMask) loadedExportMask;
                    }
                } else {
                    // check if mask has already been created
                    exportMask = getExportMaskAlreadyCreated(unManagedExportMask, requestContext.getRootIngestionRequestContext(), _dbClient);

                    if (exportMask == null) {
                        continue;
                    }
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
                ExportMaskUtils.setExportMaskResource(_dbClient, exportGroup, exportMask);

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
                ExportMaskUtils.updateFCZoneReferences(exportGroup, exportMask, blockObject, unManagedExportMask.getZoningMap(), initiators,
                        _dbClient);

                // remove the unmanaged mask from unmanaged volume only if the block object has not been marked as internal
                if (!blockObject.checkInternalFlags(Flag.PARTIALLY_INGESTED)) {
                    _logger.info("block object {} is fully ingested, "
                            + "breaking relationship between UnManagedExportMask {} and UnManagedVolume {}",
                            blockObject.forDisplay(), unManagedExportMask.getMaskName(), unManagedVolume.forDisplay());
                    unManagedVolume.getUnmanagedExportMasks().remove(unManagedExportMask.getId().toString());
                    unManagedExportMask.getUnmanagedVolumeUris().remove(unManagedVolume.getId().toString());
                    uemsToPersist.add(unManagedExportMask);
                }

                if (exportGroup.getExportMasks() == null || !exportGroup.getExportMasks().contains(exportMask.getId().toString())) {
                    exportGroup.addExportMask(exportMask.getId().toString());
                }

                VolumeIngestionUtil.updateExportGroup(exportGroup, blockObject, wwnToHluMap, _dbClient, initiators, hosts, cluster);

                _logger.info("Removing unmanaged mask {} from the list of items to process, as block object is added already",
                        unManagedExportMask.getMaskName());
                itr.remove();

                requestContext.addDataObjectToUpdate(exportMask, unManagedVolume);
            }

            _logger.info("{} unmanaged mask(s) validated as eligible for further processing: {}", 
                    unManagedMasks.size(), VolumeIngestionUtil.getMaskNames(URIUtil.toUris(unManagedMasks), _dbClient));

            List<ExportMask> exportMasksToCreate = new ArrayList<ExportMask>();
            List<UnManagedExportMask> eligibleMasks = null;
            if (!unManagedMasks.isEmpty()) {
                if (null != cluster) {
                    _logger.info("Processing Cluster {}", cluster.forDisplay());

                    // get Hosts for Cluster & get Initiators by Host Name
                    // TODO handle multiple Hosts in one call
                    List<URI> hostUris = ComputeSystemHelper
                            .getChildrenUris(_dbClient, requestContext.getCluster(), Host.class, "cluster");
                    _logger.info("Found Hosts {} in cluster {}", Joiner.on(",").join(hostUris), cluster.forDisplay());
                    List<Set<String>> iniGroupByHost = new ArrayList<Set<String>>();
                    URI varrayUri = requestContext.getVarray(unManagedVolume).getId();
                    boolean isVplexDistributedVolume = VolumeIngestionUtil.isVplexDistributedVolume(unManagedVolume);
                    boolean isVplexAutoCrossConnect = requestContext.getVpool(unManagedVolume).getAutoCrossConnectExport();
                    for (URI hostUri : hostUris) {
                        Set<String> initsOfHost = getInitiatorsOfHost(hostUri);
                        Host host2 = _dbClient.queryObject(Host.class, hostUri);
                        _logger.info("Host {} has these initiators: " 
                                + VolumeIngestionUtil.getInitiatorNames(URIUtil.toURIList(initsOfHost), _dbClient), host2.forDisplay());
                        if (isVplexDistributedVolume && !isVplexAutoCrossConnect) {
                            _logger.info("this is a distributed vplex volume which may have split fabrics with different connectivity per host");
                            Iterator<String> initsOfHostIt = initsOfHost.iterator();
                            while (initsOfHostIt.hasNext()) {
                                String uriStr = initsOfHostIt.next();
                                Initiator init = _dbClient.queryObject(Initiator.class, URI.create(uriStr));
                                if (null != init) {
                                    _logger.info("checking initiator {} for connectivity", init.getInitiatorPort());
                                    Set<String> connectedVarrays = ConnectivityUtil.getInitiatorVarrays(init.getInitiatorPort(), _dbClient);
                                    _logger.info("initiator's connected varrays are: {}", connectedVarrays);
                                    if (!connectedVarrays.contains(varrayUri.toString())) {
                                        _logger.info("initiator {} of host {} is not connected to varray {}, removing",
                                                init.getInitiatorPort(), host2.getLabel(), VolumeIngestionUtil.getVarrayName(varrayUri, _dbClient));
                                        initsOfHostIt.remove();
                                    }
                                }
                            }
                        }
                        if (!initsOfHost.isEmpty()) {
                            iniGroupByHost.add(initsOfHost);
                        }
                    }

                    eligibleMasks = VolumeIngestionUtil.findMatchingExportMaskForCluster(blockObject,
                            unManagedMasks, iniGroupByHost, _dbClient, varrayUri,
                            requestContext.getVpool(unManagedVolume).getId(), requestContext.getCluster(), errorMessages);
                    // Volume cannot be exposed to both Cluster and Host
                    if (eligibleMasks.size() == 1) {
                        // all initiators of all hosts in 1 MV
                        // add Volume,all Initiators and StoragePorts to
                        // ExportMask
                        _logger.info("Only 1 eligible mask found for cluster {}: ", cluster.forDisplay(), eligibleMasks.get(0).toString());

                        ExportMask exportMaskToCreate = VolumeIngestionUtil.createExportMask(eligibleMasks.get(0), unManagedVolume,
                                exportGroup, blockObject, _dbClient, hosts, cluster, cluster.getLabel());
                        updateExportMaskWithPortGroup(system, eligibleMasks.get(0), exportMaskToCreate);
                        exportMasksToCreate.add(exportMaskToCreate);
                        uemsToPersist.add(eligibleMasks.get(0));
                        masksIngestedCount.increment();

                    } else if (eligibleMasks.size() > 1) {
                        _logger.info("Multiple masks found for cluster {}: {}", cluster.forDisplay(), Joiner.on(";").join(eligibleMasks));
                        // 1 MV per Cluster Node
                        for (UnManagedExportMask eligibleMask : eligibleMasks) {
                            _logger.info("Setting up eligible mask " + eligibleMask.forDisplay());
                            ExportMask exportMaskToCreate = VolumeIngestionUtil.createExportMask(eligibleMask, unManagedVolume, exportGroup,
                                    blockObject, _dbClient, hosts, cluster, cluster.getLabel());
                            updateExportMaskWithPortGroup(system, eligibleMask, exportMaskToCreate);
                            exportMasksToCreate.add(exportMaskToCreate);
                            uemsToPersist.add(eligibleMask);
                            masksIngestedCount.increment();
                        }
                    }
                } else if (null != host) {
                    _logger.info("Processing Host {} ", host.forDisplay());
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
                        _logger.info("Eligible masks found for Host {}: {}", host.forDisplay(), Joiner.on(",").join(eligibleMasks));
                    } else {
                        _logger.info("No eligible unmanaged export masks found for Host {}", host.forDisplay());
                    }
                    for (UnManagedExportMask eligibleMask : eligibleMasks) {
                        _logger.info("Setting up eligible mask " + eligibleMask.forDisplay());
                        ExportMask exportMaskToCreate = VolumeIngestionUtil.createExportMask(eligibleMask, unManagedVolume, exportGroup,
                                blockObject, _dbClient, hosts, cluster, host.getHostName());
                        updateExportMaskWithPortGroup(system, eligibleMask, exportMaskToCreate);
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
                        _logger.info("Eligible masks found for device initiators {}: {}",
                                deviceInitiators, Joiner.on(",").join(eligibleMasks));
                    } else {
                        _logger.info("No eligible unmanaged export masks found for device initiators {}",
                                deviceInitiators);
                    }
                    for (UnManagedExportMask eligibleMask : eligibleMasks) {
                        _logger.info("Setting up eligible mask " + eligibleMask.forDisplay());
                        // this getHostName will be the name of the VPLEX device
                        ExportMask exportMaskToCreate = VolumeIngestionUtil.createExportMask(eligibleMask, unManagedVolume, exportGroup,
                                blockObject, _dbClient, hosts, cluster, deviceInitiators.get(0).getHostName());
                        updateExportMaskWithPortGroup(system, eligibleMask, exportMaskToCreate);
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
    protected abstract ExportMask getExportMaskAlreadyIngested(UnManagedExportMask mask, DbClient dbClient);

    /**
     * Find existing but newly-created export mask in IngestionRequestContext which contains
     * the right attributes.
     *
     * @param mask
     * @param requestContext
     * @param dbClient a reference to the database client
     * @return
     */
    protected abstract ExportMask getExportMaskAlreadyCreated(UnManagedExportMask mask, IngestionRequestContext requestContext, DbClient dbClient);

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

    /**
     * Update the ingested exportMask with port group info. If the port group is not in the DB yet, create it.
     * 
     * @param system
     *            - The storage system the export mask belongs to
     * @param unmanagedMask
     *            - The corresponding unmanaged export mask
     * @param mask
     *            - The ingested export mask
     */
    protected void updateExportMaskWithPortGroup(StorageSystem system, UnManagedExportMask unmanagedMask, ExportMask mask) {
        boolean portGroupEnabled = false;
        if (Type.vmax.name().equals(system.getSystemType())) {
            portGroupEnabled = Boolean.valueOf(
                    _customConfigHandler.getComputedCustomConfigValue(
                            CustomConfigConstants.VMAX_USE_PORT_GROUP_ENABLED,
                            system.getSystemType(), null));
        }
        // Set port group
        String portGroupName = unmanagedMask.getPortGroup();
        if (NullColumnValueGetter.isNotNullValue(portGroupName)) {
            // Port group name is set in the UnManagedMask
            String guid = String.format("%s+%s", system.getNativeGuid(), portGroupName);
            URIQueryResultList result = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getPortGroupNativeGuidConstraint(guid), result);
            Iterator<URI> it = result.iterator();
            boolean foundPG = it.hasNext();
            StoragePortGroup portGroup = null;
            if (!foundPG) {
                portGroup = new StoragePortGroup();
                portGroup.setId(URIUtil.createId(StoragePortGroup.class));
                portGroup.setLabel(portGroupName);
                portGroup.setNativeGuid(guid);
                portGroup.setStorageDevice(system.getId());
                portGroup.setInactive(false);
                List<URI> targets = new ArrayList<URI>(Collections2.transform(
                        unmanagedMask.getKnownStoragePortUris(), CommonTransformerFunctions.FCTN_STRING_TO_URI));
                portGroup.setStoragePorts(StringSetUtil.uriListToStringSet(targets));
                _dbClient.createObject(portGroup);
            } else {
                URI pgURI = it.next();
                portGroup = _dbClient.queryObject(StoragePortGroup.class, pgURI);
            }
            if (portGroupEnabled) {
                portGroup.setRegistrationStatus(RegistrationStatus.REGISTERED.name());
                portGroup.setMutable(false);
            } else {
                portGroup.setRegistrationStatus(RegistrationStatus.UNREGISTERED.name());
                portGroup.setMutable(true);
            }
            _dbClient.updateObject(portGroup);
            mask.setPortGroup(portGroup.getId());
            _dbClient.updateObject(mask);
        }

    }

}
