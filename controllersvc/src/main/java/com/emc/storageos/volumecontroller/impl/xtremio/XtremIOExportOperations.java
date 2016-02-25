/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.xtremio;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.XtremIOConstants.XTREMIO_ENTITY_TYPE;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiator;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOTag;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;

public class XtremIOExportOperations extends XtremIOOperations implements ExportMaskOperations {
    private static final Logger _log = LoggerFactory.getLogger(XtremIOExportOperations.class);

    @Override
    public void createExportMask(StorageSystem storage, URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs, List<URI> targetURIList, List<Initiator> initiatorList,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
        if (exportMask == null || exportMask.getInactive()) {
            throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskURI);
        }

        runLunMapCreationAlgorithm(storage, exportMask, volumeURIHLUs, initiatorList,
                targetURIList, taskCompleter);
    }

    @Override
    public void deleteExportMask(StorageSystem storage, URI exportMaskURI, List<URI> volumeURIList,
            List<URI> targetURIList, List<Initiator> initiatorList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
        if (exportMask == null || exportMask.getInactive()) {
            throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskURI);
        }

        // initiators are volumes are empty list , hence get it from export mask.

        List<URI> volumeUris = new ArrayList<URI>(Collections2.transform(exportMask.getVolumes()
                .keySet(), CommonTransformerFunctions.FCTN_STRING_TO_URI));

        List<URI> initiatorUriList = new ArrayList<URI>(Collections2.transform(
                exportMask.getInitiators(), CommonTransformerFunctions.FCTN_STRING_TO_URI));

        List<Initiator> initiators = dbClient.queryObject(Initiator.class, initiatorUriList);

        runLunMapDeletionOrRemoveInitiatorAlgorithm(storage, exportMask, volumeUris, initiators, taskCompleter);
    }

    @Override
    public void addVolume(StorageSystem storage, URI exportMaskURI, VolumeURIHLU[] volumeURIHLUs,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
        if (exportMask == null || exportMask.getInactive()) {
            throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskURI);
        }
        Set<String> initiatorUris = exportMask.getInitiators();
        List<URI> initiatorUriList = new ArrayList<URI>(Collections2.transform(initiatorUris,
                CommonTransformerFunctions.FCTN_STRING_TO_URI));
        List<Initiator> initiators = dbClient.queryObject(Initiator.class, initiatorUriList);

        runLunMapCreationAlgorithm(storage, exportMask, volumeURIHLUs, initiators, null,
                taskCompleter);

    }

    @Override
    public void removeVolume(StorageSystem storage, URI exportMaskURI, List<URI> volumeUris,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
        if (exportMask == null || exportMask.getInactive()) {
            throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskURI);
        }

        Set<String> initiatorUris = exportMask.getInitiators();
        List<URI> initiatorUriList = new ArrayList<URI>(Collections2.transform(initiatorUris,
                CommonTransformerFunctions.FCTN_STRING_TO_URI));
        List<Initiator> initiators = dbClient.queryObject(Initiator.class, initiatorUriList);
        runLunMapDeletionAlgorithm(storage, exportMask, volumeUris, initiators, taskCompleter);

    }

    @Override
    public void addInitiator(StorageSystem storage, URI exportMaskURI, List<Initiator> initiators,
            List<URI> targets, TaskCompleter taskCompleter) throws DeviceControllerException {
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
        if (exportMask == null || exportMask.getInactive()) {
            throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskURI);
        }
        Map<URI, Integer> map = new HashMap<URI, Integer>();
        for (Entry<String, String> entry : exportMask.getVolumes().entrySet()) {
            map.put(URI.create(entry.getKey()), Integer.parseInt(entry.getValue()));
        }

        // to make it uniform , using these structures
        VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                storage.getSystemType(), map, dbClient);
        runLunMapCreationAlgorithm(storage, exportMask, volumeLunArray, initiators, targets,
                taskCompleter);

    }

    @Override
    public void removeInitiator(StorageSystem storage, URI exportMaskURI,
            List<Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
        if (exportMask == null || exportMask.getInactive()) {
            throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskURI);
        }

        XtremIOClient client = null;
        // if host Name is not available in at least one of the initiator, then
        // set it to
        // Default_IG;
        List<String> failedIGs = new ArrayList<String>();
        ArrayListMultimap<String, Initiator> groupInitiatorsByIG = ArrayListMultimap.create();
        try {
            String hostName = null;
            String clusterName = null;
            client = XtremIOProvUtils.getXtremIOClient(storage, xtremioRestClientFactory);
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            Iterator<Initiator> iniItr = initiators.iterator();
            while (iniItr.hasNext()) {
                Initiator initiator = iniItr.next();
                String igName = null;
                if (null != initiator.getHostName()) {
                    // initiators already grouped by Host
                    hostName = initiator.getHostName();
                    clusterName = initiator.getClusterName();
                }
                igName = getIGNameForInitiator(initiator, storage.getSerialNumber(), client, xioClusterName);
                if (igName != null && !igName.isEmpty()) {
                    groupInitiatorsByIG.put(igName, initiator);
                } else {
                    // initiator not found in Array, remove from DB
                    exportMask.removeFromExistingInitiators(initiator);
                    exportMask.removeFromUserCreatedInitiators(initiator);
                    iniItr.remove();
                }
            }

            _log.info("List of  IGs found {} with size : {}",
                    Joiner.on(",").join(groupInitiatorsByIG.asMap().entrySet()), groupInitiatorsByIG.size());

            // Deleting the initiator automatically removes the initiator from
            // lun map
            for (Initiator initiator : initiators) {
                try {
                    client.deleteInitiator(initiator.getMappedInitiatorName(storage.getSerialNumber()), xioClusterName);
                    exportMask.removeFromExistingInitiators(initiator);
                    exportMask.removeFromUserCreatedInitiators(initiator);
                } catch (Exception e) {
                    failedIGs.add(initiator.getLabel());
                }
            }
            dbClient.updateAndReindexObject(exportMask);

            if (!failedIGs.isEmpty()) {
                String errMsg = "Export Operations failed deleting these initiators: ".concat(Joiner.on(", ").join(
                        failedIGs));
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(errMsg, null);
                taskCompleter.error(dbClient, serviceError);
                return;
            }

            // Clean IGs if empty

            deleteInitiatorGroup(groupInitiatorsByIG, client, xioClusterName);
            // delete IG Folder as well if IGs are empty
            deleteInitiatorGroupFolder(client, xioClusterName, clusterName, hostName, storage);

            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            String errMsg = "Export Operations failed deleting these initiators: ".concat(Joiner.on(", ").join(
                    failedIGs));
            ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(errMsg, null);
            taskCompleter.error(dbClient, serviceError);
            return;
        }

    }

    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames, boolean mustHaveAllPorts) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) {
        try {
            _log.info("Refreshing Initiator labels in ViPR.. ");
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(storage, xtremioRestClientFactory);
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            List<XtremIOInitiator> initiators = client.getXtremIOInitiatorsInfo(xioClusterName);
            List<Initiator> initiatorObjs = new ArrayList<Initiator>();
            for (XtremIOInitiator initiator : initiators) {
                URIQueryResultList initiatorResult = new URIQueryResultList();
                dbClient
                        .queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(initiator.getPortAddress()),
                                initiatorResult);
                if (initiatorResult.iterator().hasNext()) {
                    Initiator initiatorObj = dbClient.queryObject(Initiator.class, initiatorResult.iterator().next());
                    _log.info("Updating Initiator label from {} to {} in ViPR DB", initiatorObj.getLabel(), initiator.getName());
                    initiatorObj.setLabel(initiator.getName());
                    initiatorObj.mapInitiatorName(storage.getSerialNumber(), initiator.getName());
                    initiatorObjs.add(initiatorObj);
                } else {
                    _log.info("No initiator objects in vipr db for port address {}", initiator.getPortAddress());
                }
            }
            if (!initiatorObjs.isEmpty()) {
                dbClient.updateObject(initiatorObjs);
            }
        } catch (Exception e) {
            _log.warn("Refreshing XtremIO Initiator ports failed", e);
        }
        // CTRL-13080 fix - refresh mask will not be used by XtremIo exports, hence returning null is not an issue.
        return null;
    }

    private void runLunMapDeletionAlgorithm(StorageSystem storage, ExportMask exportMask,
            List<URI> volumes, List<Initiator> initiators, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        // find LunMap associated with Volume
        // Then find initiatorGroup associated with this lun map

        // find initiators associated with IG, if the given list is of initiators is same, then run
        // removeLunMap
        ArrayListMultimap<String, Initiator> groupInitiatorsByIG = ArrayListMultimap.create();
        Set<String> igNames = new HashSet<String>();
        XtremIOClient client = null;
        // if host Name is not available in at least one of the initiator, then set it to
        // Default_IG;
        try {
            String hostName = null;
            String clusterName = null;
            client = XtremIOProvUtils.getXtremIOClient(storage, xtremioRestClientFactory);
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            for (Initiator initiator : initiators) {
                String igName = null;
                if (null != initiator.getHostName()) {
                    // initiators already grouped by Host
                    hostName = initiator.getHostName();
                    clusterName = initiator.getClusterName();
                }
                igName = getIGNameForInitiator(initiator, storage.getSerialNumber(), client, xioClusterName);
                if (igName != null && !igName.isEmpty()) {
                    groupInitiatorsByIG.put(igName, initiator);
                    igNames.add(igName);
                }
            }

            _log.info("List of reusable IGs found {} with size : {}",
                    Joiner.on(",").join(groupInitiatorsByIG.asMap().entrySet()),
                    groupInitiatorsByIG.size());

            List<URI> failedVolumes = new ArrayList<URI>();
            for (URI volumeUri : volumes) {
                BlockObject blockObj = BlockObject.fetch(dbClient, volumeUri);
                _log.info("Block Obj {} , wwn {}", blockObj.getId(), blockObj.getWWN());
                XtremIOVolume xtremIOVolume = null;
                if (URIUtil.isType(volumeUri, Volume.class)) {
                    xtremIOVolume = XtremIOProvUtils.isVolumeAvailableInArray(client,
                            blockObj.getLabel(), xioClusterName);
                } else {
                    xtremIOVolume = XtremIOProvUtils.isSnapAvailableInArray(client,
                            blockObj.getDeviceLabel(), xioClusterName);
                }

                if (null != xtremIOVolume) {
                    // I need lun map id and igName
                    // if iGName is available in the above group, then remove lunMap
                    _log.info("Volume Details {}", xtremIOVolume.toString());
                    _log.info("Volume lunMap details {}", xtremIOVolume.getLunMaps().toString());

                    Set<String> lunMaps = new HashSet<String>();
                    String volId = xtremIOVolume.getVolInfo().get(2);

                    if (xtremIOVolume.getLunMaps().isEmpty()) {
                        // handle scenarios where volumes gets unexported already
                        _log.info("Volume  {} doesn't have any existing export available on Array, unexported already.",
                                xtremIOVolume.toString());
                        exportMask.removeFromUserCreatedVolumes(blockObj);
                        exportMask.removeVolume(blockObj.getId());
                        continue;
                    }
                    for (List<Object> lunMapEntries : xtremIOVolume.getLunMaps()) {

                        @SuppressWarnings("unchecked")
                        List<Object> igDetails = (List<Object>) lunMapEntries.get(0);
                        String igName = (String) igDetails.get(1);

                        // Ig details is actually transforming to A double by deofault, even though
                        // its modeled as List<String>
                        // hence this logic
                        Double IgIdDouble = (Double) igDetails.get(2);
                        String igId = String.valueOf(IgIdDouble.intValue());

                        _log.info("IG Name: {} Id: {} found in Lun Map", igName, igId);
                        if (!igNames.contains(igName)) {
                            _log.info(
                                    "Volume is associated with IG {} which is not in the removal list requested, ignoring..",
                                    igName);
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        List<Object> tgtGroupDetails = (List<Object>) lunMapEntries.get(1);
                        Double tgIdDouble = (Double) tgtGroupDetails.get(2);
                        String tgtid = String.valueOf(tgIdDouble.intValue());
                        String lunMapId = volId.concat(XtremIOConstants.UNDERSCORE).concat(igId)
                                .concat(XtremIOConstants.UNDERSCORE).concat(tgtid);
                        _log.info("LunMap Id {} Found associated with Volume {}", lunMapId,
                                blockObj.getLabel());
                        lunMaps.add(lunMapId);

                    }
                    // deletion of lun Maps
                    // there will be only one lun map always
                    for (String lunMap : lunMaps) {
                        try {
                            client.deleteLunMap(lunMap, xioClusterName);
                        } catch (Exception e) {
                            failedVolumes.add(volumeUri);
                            _log.warn("Deletion of Lun Map {} failed}", lunMap, e);

                        }
                    }
                } else {
                    exportMask.removeFromUserCreatedVolumes(blockObj);
                    exportMask.removeVolume(blockObj.getId());
                }

            }
            dbClient.updateAndReindexObject(exportMask);

            if (!failedVolumes.isEmpty()) {
                String errMsg = "Export Operations failed for these volumes: ".concat(Joiner.on(", ").join(
                        failedVolumes));
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(
                        errMsg, null);
                taskCompleter.error(dbClient, serviceError);
                return;
            }

            // Clean IGs if empty

            deleteInitiatorGroup(groupInitiatorsByIG, client, xioClusterName);
            // delete IG Folder as well if IGs are empty
            deleteInitiatorGroupFolder(client, xioClusterName, clusterName, hostName, storage);

            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error(String.format("Export Operations failed - maskName: %s", exportMask.getId()
                    .toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }

    }

    /**
     * It deletes the LunMap if the IG contains no other initiators than the requested ones.
     * Else it removes the requested initiators from the IG
     */
    private void runLunMapDeletionOrRemoveInitiatorAlgorithm(StorageSystem storage, ExportMask exportMask,
            List<URI> volumes, List<Initiator> initiators, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        // find LunMap associated with Volume
        // Then find initiatorGroup associated with this lun map

        // find initiators associated with IG:
        // -if IG has other host's initiators within the same cluster, remove the requested initiators,
        // -else delete the lunMap.
        ArrayListMultimap<String, Initiator> groupInitiatorsByIG = ArrayListMultimap.create();
        Set<String> igNames = new HashSet<String>();
        XtremIOClient client = null;
        // if host Name is not available in at least one of the initiator, then set it to
        // Default_IG;
        try {
            String hostName = null;
            String clusterName = null;
            client = XtremIOProvUtils.getXtremIOClient(storage, xtremioRestClientFactory);
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            for (Initiator initiator : initiators) {
                String igName = null;
                if (null != initiator.getHostName()) {
                    // initiators already grouped by Host
                    hostName = initiator.getHostName();
                    clusterName = initiator.getClusterName();
                }
                igName = getIGNameForInitiator(initiator, storage.getSerialNumber(), client, xioClusterName);
                if (igName != null && !igName.isEmpty()) {
                    groupInitiatorsByIG.put(igName, initiator);
                    igNames.add(igName);
                }
            }

            _log.info("List of reusable IGs found {} with size : {}",
                    Joiner.on(",").join(groupInitiatorsByIG.asMap().entrySet()),
                    groupInitiatorsByIG.size());

            List<URI> failedVolumes = new ArrayList<URI>();
            List<String> failedIGs = new ArrayList<String>();
            for (URI volumeUri : volumes) {
                BlockObject blockObj = BlockObject.fetch(dbClient, volumeUri);
                _log.info("Block Obj {} , wwn {}", blockObj.getId(), blockObj.getWWN());
                XtremIOVolume xtremIOVolume = null;
                if (URIUtil.isType(volumeUri, Volume.class)) {
                    xtremIOVolume = XtremIOProvUtils.isVolumeAvailableInArray(client,
                            blockObj.getLabel(), xioClusterName);
                } else {
                    xtremIOVolume = XtremIOProvUtils.isSnapAvailableInArray(client,
                            blockObj.getDeviceLabel(), xioClusterName);
                }

                if (null != xtremIOVolume) {
                    // I need lun map id and igName
                    // if iGName is available in the above group:
                    // -if IG has other host's initiators within the same cluster, remove the requested initiators,
                    // -else delete the lunMap.
                    _log.info("Volume Details {}", xtremIOVolume.toString());
                    _log.info("Volume lunMap details {}", xtremIOVolume.getLunMaps().toString());

                    // Lun Maps to delete
                    Set<String> lunMaps = new HashSet<String>();
                    boolean removeInitiator = false;
                    String volId = xtremIOVolume.getVolInfo().get(2);

                    if (xtremIOVolume.getLunMaps().isEmpty()) {
                        // handle scenarios where volumes gets unexported already
                        _log.info("Volume  {} doesn't have any existing export available on Array, unexported already.",
                                xtremIOVolume.toString());
                        exportMask.removeFromUserCreatedVolumes(blockObj);
                        exportMask.removeVolume(blockObj.getId());
                        continue;
                    }
                    for (List<Object> lunMapEntries : xtremIOVolume.getLunMaps()) {
                        @SuppressWarnings("unchecked")
                        List<Object> igDetails = (List<Object>) lunMapEntries.get(0);
                        String igName = (String) igDetails.get(1);

                        // IG details is actually transforming to a double by default, even though
                        // its modeled as List<String>
                        // hence this logic
                        Double IgIdDouble = (Double) igDetails.get(2);
                        String igId = String.valueOf(IgIdDouble.intValue());

                        _log.info("IG Name: {} Id: {} found in Lun Map", igName, igId);
                        if (!igNames.contains(igName)) {
                            _log.info(
                                    "Volume is associated with IG {} which is not in the removal list requested, ignoring..",
                                    igName);
                            continue;
                        }

                        // check if IG has other host's initiators within the same cluster.
                        List<Initiator> knownInitiatorsInIG = getKnownInitiatorsForIG(igName, xioClusterName, client);
                        /**
                         * Remove Initiator only
                         * -when IG has other initiators which are known to ViPR and
                         * -if other initiators belong to different host and
                         * -if the hosts are in same cluster
                         * (i.e) Single IG with initiators from multiple hosts of a Cluster case
                         */
                        if (checkIfIGHasOtherHostInitiatorsOfSameCluster(knownInitiatorsInIG,
                                groupInitiatorsByIG.get(igName), hostName, clusterName)) {
                            removeInitiator = true;
                        } else {
                            @SuppressWarnings("unchecked")
                            List<Object> tgtGroupDetails = (List<Object>) lunMapEntries.get(1);
                            Double tgIdDouble = (Double) tgtGroupDetails.get(2);
                            String tgtid = String.valueOf(tgIdDouble.intValue());
                            String lunMapId = volId.concat(XtremIOConstants.UNDERSCORE).concat(igId)
                                    .concat(XtremIOConstants.UNDERSCORE).concat(tgtid);
                            _log.info("LunMap Id {} Found associated with Volume {}", lunMapId,
                                    blockObj.getLabel());
                            lunMaps.add(lunMapId);
                        }
                    }
                    // deletion of lun Maps
                    // there will be only one lun map always
                    for (String lunMap : lunMaps) {
                        try {
                            client.deleteLunMap(lunMap, xioClusterName);
                        } catch (Exception e) {
                            failedVolumes.add(volumeUri);
                            _log.warn("Deletion of Lun Map {} failed}", lunMap, e);
                        }
                    }
                    // remove initiator from IG
                    if (removeInitiator) {
                        _log.info("Removing requested intiators from IG instead of deleting LunMap"
                                + " as the IG contains other Host's initiators belonging to same Cluster.");
                        // Deleting the initiator automatically removes the initiator from lun map
                        for (Initiator initiator : initiators) {
                            try {
                                // check if Initiator has already been deleted during previous volume processing
                                String initiatorName = initiator.getMappedInitiatorName(storage.getSerialNumber());
                                XtremIOInitiator initiatorObj = client.getInitiator(initiatorName, xioClusterName);
                                if (null != initiatorObj) {
                                    client.deleteInitiator(initiatorName, xioClusterName);
                                } else {
                                    _log.info("Initiator {} already deleted", initiatorName);
                                }
                            } catch (Exception e) {
                                failedIGs.add(initiator.getLabel());
                                _log.warn("Removal of Initiator {} from IG failed", initiator.getLabel(), e);
                            }
                        }
                    }
                } else {
                    exportMask.removeFromUserCreatedVolumes(blockObj);
                    exportMask.removeVolume(blockObj.getId());
                }
            }
            dbClient.updateAndReindexObject(exportMask);

            if (!failedVolumes.isEmpty()) {
                String errMsg = "Export Operations failed for these volumes: ".concat(Joiner.on(", ").join(
                        failedVolumes));
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(
                        errMsg, null);
                taskCompleter.error(dbClient, serviceError);
                return;
            }
            if (!failedIGs.isEmpty()) {
                String errMsg = "Export Operations failed deleting these initiators: ".concat(Joiner.on(", ").join(
                        failedIGs));
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(errMsg, null);
                taskCompleter.error(dbClient, serviceError);
                return;
            }

            // Clean IGs if empty
            deleteInitiatorGroup(groupInitiatorsByIG, client, xioClusterName);
            // delete IG Folder as well if IGs are empty
            deleteInitiatorGroupFolder(client, xioClusterName, clusterName, hostName, storage);

            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error(String.format("Export Operations failed - maskName: %s", exportMask.getId()
                    .toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
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
            _log.warn("Initiator {} already deleted", initiator.getLabel());
        }

        return igName;
    }

    private String getInitiatorGroupFolderName(String clusterName, String hostName, StorageSystem storage) {
        String igFolderName = "";
        if (clusterName != null && !clusterName.isEmpty()) {
            // cluster
            DataSource dataSource = dataSourceFactory.createXtremIOClusterInitiatorGroupFolderNameDataSource(
                    clusterName, storage);
            igFolderName = customConfigHandler.getComputedCustomConfigValue(
                    CustomConfigConstants.XTREMIO_CLUSTER_INITIATOR_GROUP_FOLDER_NAME, storage.getSystemType(), dataSource);
        } else {
            DataSource dataSource = dataSourceFactory.createXtremIOHostInitiatorGroupFolderNameDataSource(
                    hostName, storage);
            igFolderName = customConfigHandler.getComputedCustomConfigValue(
                    CustomConfigConstants.XTREMIO_HOST_INITIATOR_GROUP_FOLDER_NAME, storage.getSystemType(), dataSource);
        }

        return igFolderName;
    }

    private void addInitiatorToInitiatorGroup(XtremIOClient client, String xioClusterName,
            String clusterName, String hostName, List<Initiator> initiatorsToBeCreated,
            Set<String> igNames, ExportMask exportMask, StorageSystem storage)
            throws Exception {
        XtremIOInitiatorGroup igGroup = null;
        // create initiator group folder and initiator group
        String igFolderName = getInitiatorGroupFolderName(clusterName, hostName, storage);

        if (null == client.getTagDetails(igFolderName, XTREMIO_ENTITY_TYPE.InitiatorGroup.name(), xioClusterName)) {
            _log.info("Creating IG Folder with name {}", igFolderName);
            client.createTag(igFolderName, null, XtremIOConstants.XTREMIO_ENTITY_TYPE.InitiatorGroup.name(), xioClusterName);
        }

        DataSource dataSource = dataSourceFactory.createXtremIOInitiatorGroupNameDataSource(
                hostName, storage);
        String igName = customConfigHandler.getComputedCustomConfigValue(
                CustomConfigConstants.XTREMIO_INITIATOR_GROUP_NAME, storage.getSystemType(), dataSource);
        igGroup = client.getInitiatorGroup(igName, xioClusterName);
        if (null == igGroup) {
            // create a new IG
            _log.info("Creating Initiator Group with name {}", igName);

            client.createInitiatorGroup(igName, igFolderName, xioClusterName);
            igGroup = client.getInitiatorGroup(igName, xioClusterName);
            if (null == igGroup) {
                _log.info("Neither IG is already present nor able to create on Array {}", hostName);
            } else {
                _log.info("Created Initiator Group {} with # initiators {}", igGroup.getName(),
                        igGroup.getNumberOfInitiators());
                igNames.add(igGroup.getName());
            }
        } else {
            igNames.add(igGroup.getName());
            _log.info("Found Initiator Group {} with # initiators {}", igGroup.getName(),
                    igGroup.getNumberOfInitiators());

        }

        // add all the left out initiators to this folder
        for (Initiator remainingInitiator : initiatorsToBeCreated) {
            _log.info("Initiator {} Label {} ", remainingInitiator.getInitiatorPort(),
                    remainingInitiator.getLabel());
            String initiatorName = ((null == remainingInitiator.getLabel() || remainingInitiator
                    .getLabel().isEmpty()) ? remainingInitiator.getInitiatorPort()
                    : remainingInitiator.getLabel());
            _log.info("Initiator {}  ", initiatorName);
            try {
                String os = null;
                if (client.isVersion2() && !NullColumnValueGetter.isNullURI(remainingInitiator.getHost())) {
                    Host host = dbClient.queryObject(Host.class, remainingInitiator.getHost());
                    os = XtremIOProvUtils.getInitiatorHostOS(host);
                }
                // create initiator
                client.createInitiator(initiatorName, igGroup.getName(),
                        remainingInitiator.getInitiatorPort(), os, xioClusterName);
                remainingInitiator.setLabel(initiatorName);
                remainingInitiator.mapInitiatorName(storage.getSerialNumber(), initiatorName);
                dbClient.updateObject(remainingInitiator);

            } catch (Exception e) {
                // assume initiator already part of another group look for
                // port_address_not_unique
                // CTRL-5956 - Few Initiators cannot be registered on XtremIO Array, throw exception even if one initiator registration
                // fails.
                _log.warn("Initiator {} already available or not able to register the same on Array. Rediscover the Array and try again.",
                        remainingInitiator.getInitiatorPort());
                throw e;

            }
        }
    }

    private void runLunMapCreationAlgorithm(StorageSystem storage, ExportMask exportMask,
            VolumeURIHLU[] volumeURIHLUs, List<Initiator> initiators, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {

        ArrayListMultimap<String, Initiator> groupInitiatorsByIG = ArrayListMultimap.create();
        List<Initiator> initiatorsToBeCreated = new ArrayList<Initiator>();
        XtremIOClient client = null;
        Set<String> igNames = null;

        // if host Name is not available in at least one of the initiator, then set it to
        // Default_IG;
        try {
            String hostName = null;
            String clusterName = null;
            client = XtremIOProvUtils.getXtremIOClient(storage, xtremioRestClientFactory);
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            _log.info("Finding re-usable IGs available on Array {}", storage.getNativeGuid());

            for (Initiator initiator : initiators) {
                String igName = null;
                if (null != initiator.getHostName()) {
                    // initiators already grouped by Host
                    hostName = initiator.getHostName();
                    clusterName = initiator.getClusterName();
                }

                igName = getIGNameForInitiator(initiator, storage.getSerialNumber(), client, xioClusterName);
                if (igName == null || igName.isEmpty()) {
                    _log.info("initiator {} - no IG found. Adding to create list", initiator.getLabel(), igName);
                    initiatorsToBeCreated.add(initiator);
                } else {
                    groupInitiatorsByIG.put(igName, initiator);
                }
            }

            _log.info("Found {} existing IGs: {} after running selection process",
                    groupInitiatorsByIG.size(), Joiner.on(",").join(groupInitiatorsByIG.asMap().entrySet()));

            // since we're reusing existing IGs, volumes might get exposed to other initiators in
            // IG.

            // for initiators without label, create IGs and add them to a host folder by name
            // initiator.getHost() in cases ,where initiator.getLabel() is not present, we try
            // creating IG, if fails
            // then we consider that it's already created
            igNames = new HashSet<String>();
            igNames.addAll(groupInitiatorsByIG.keySet());
            if (!initiatorsToBeCreated.isEmpty()) {
                // create new initiator and add to IG; add IG to IG folder
                addInitiatorToInitiatorGroup(client, xioClusterName, clusterName, hostName,
                        initiatorsToBeCreated, igNames, exportMask, storage);
            }

            if (igNames.isEmpty()) {
                ServiceError serviceError = DeviceControllerException.errors.xtremioInitiatorGroupsNotDetected(storage.getNativeGuid());
                taskCompleter.error(dbClient, serviceError);
                return;
            }

            // create Lun Maps
            for (VolumeURIHLU volURIHLU : volumeURIHLUs) {
                BlockObject blockObj = BlockObject.fetch(dbClient, volURIHLU.getVolumeURI());
                String hluValue = volURIHLU.getHLU().equalsIgnoreCase(ExportGroup.LUN_UNASSIGNED_STR) ? "-1" : volURIHLU.getHLU();
                _log.info("HLU value {}", hluValue);
                for (String igName : igNames) {
                    // Create lun map
                    _log.info("Creating Lun Map for  Volume {} using IG {}", blockObj.getLabel(), igName);
                    client.createLunMap(blockObj.getDeviceLabel(), igName, hluValue, xioClusterName);
                }
            }

            // post process created lun maps
            for (VolumeURIHLU volURIHLU : volumeURIHLUs) {
                BlockObject blockObj = BlockObject.fetch(dbClient, volURIHLU.getVolumeURI());
                Integer hluNumberFound = 0;
                // get volume/snap details again and populate wwn and hlu
                XtremIOVolume xtremIOVolume = null;
                String deviceName = blockObj.getDeviceLabel();
                xtremIOVolume = XtremIOProvUtils.isVolumeAvailableInArray(client, deviceName, xioClusterName);
                // COP-19828: If we can't find a volume by the given name, try to find a snap with the given name
                if (xtremIOVolume == null) {
                    xtremIOVolume = XtremIOProvUtils.isSnapAvailableInArray(client, deviceName, xioClusterName);
                }

                if (xtremIOVolume != null) {
                    _log.info("Volume lunMap details Found {}", xtremIOVolume.getLunMaps().toString());
                    if (!xtremIOVolume.getVolInfo().isEmpty()) {
                        blockObj.setWWN(xtremIOVolume.getVolInfo().get(0));
                        blockObj.setNativeId(xtremIOVolume.getVolInfo().get(0));
                        dbClient.updateObject(blockObj);
                    }

                    for (String igName : igNames) {
                        for (List<Object> lunMapEntries : xtremIOVolume.getLunMaps()) {
                            @SuppressWarnings("unchecked")
                            // This can't be null
                            List<Object> igDetails = (List<Object>) lunMapEntries.get(0);
                            if (null == igDetails.get(1) || null == lunMapEntries.get(2)) {
                                _log.warn("IG Name is null in returned lun map response for volume {}", xtremIOVolume.toString());
                                continue;
                            }
                            String igNameToProcess = (String) igDetails.get(1);

                            _log.info("IG Name: {} found in Lun Map", igNameToProcess);
                            if (!igName.equalsIgnoreCase(igNameToProcess)) {
                                _log.info(
                                        "Volume is associated with IG {} which is not in the expected list requested, ignoring..",
                                        igNameToProcess);
                                continue;
                            }

                            Double hluNumber = (Double) lunMapEntries.get(2);
                            _log.info("Found HLU {} for volume {}", hluNumber, blockObj.getLabel());
                            // for each IG involved, the same volume is visible thro different HLUs.
                            // TODO we might need a list of HLU for each Volume URI
                            hluNumberFound = hluNumber.intValue();
                            exportMask.addVolume(blockObj.getId(), hluNumberFound);

                        }
                    }
                }
            }

            _log.info("Updated Volumes with HLUs {} after successful export",
                    Joiner.on(",").join(exportMask.getVolumes().entrySet()));
            dbClient.updateObject(exportMask);
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error(String.format("Export Operations failed - maskName: %s", exportMask.getId()
                    .toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    private void deleteInitiatorGroupFolder(XtremIOClient client, String xioClusterName, String clusterName, String hostName,
            StorageSystem system) throws Exception {
        String tempIGFolderName = getInitiatorGroupFolderName(clusterName, hostName, system);
        XtremIOTag igFolder = client.getTagDetails(tempIGFolderName, XTREMIO_ENTITY_TYPE.InitiatorGroup.name(), xioClusterName);

        if (null != igFolder && "0".equalsIgnoreCase(igFolder.getNumberOfDirectObjs())) {
            try {
                _log.info("# of IGs  {} in Folder {}", igFolder.getNumberOfDirectObjs(), clusterName);
                client.deleteTag(tempIGFolderName, XtremIOConstants.XTREMIO_ENTITY_TYPE.InitiatorGroup.name(), xioClusterName);
            } catch (Exception e) {
                _log.warn("Deleting Initatiator Group Folder{} fails", clusterName, e);
            }
        }
    }

    private void deleteInitiatorGroup(ArrayListMultimap<String, Initiator> groupInitiatorsByIG,
            XtremIOClient client, String xioClusterName) {
        for (Entry<String, Collection<Initiator>> entry : groupInitiatorsByIG.asMap().entrySet()) {
            String igName = entry.getKey();
            try {
                // find # initiators for this IG
                XtremIOInitiatorGroup ig = client.getInitiatorGroup(igName, xioClusterName);
                if (ig != null) {
                    int numberOfVolumes = Integer.parseInt(ig.getNumberOfVolumes());
                    _log.info("Initiator Group {} left with Volume size {}", igName, numberOfVolumes);
                    if (numberOfVolumes == 0) {
                        // delete Initiator Group
                        client.deleteInitiatorGroup(igName, xioClusterName);
                        // remove export mask from export groip

                    } else {
                        _log.info("Skipping IG Group {} deletion", igName);
                    }
                }
            } catch (Exception e) {
                _log.warn("Deleting Initatiator Group {} fails", igName, e);
            }
        }

    }

    /**
     * Returns a list of ViPR known initiators for the given IG name.
     *
     * @param igName
     * @param xio ClusterName
     * @param xio client
     * @return
     */
    private List<Initiator> getKnownInitiatorsForIG(String igName, String xioClusterName, XtremIOClient client)
            throws Exception {
        _log.info("Getting list of known Initiators for IG {}", igName);
        // get all initiators and see which initiators belong to given IG name.
        // Currently this is the only way to get initiators belonging to IG
        List<Initiator> knownInitiatorsInIG = new ArrayList<Initiator>();
        List<String> allInitiators = new ArrayList<String>();
        List<XtremIOInitiator> initiators = client.getXtremIOInitiatorsInfo(xioClusterName);
        for (XtremIOInitiator initiator : initiators) {
            String igNameInInitiator = initiator.getInitiatorGroup().get(1);
            if (igName.equals(igNameInInitiator)) {
                allInitiators.add(initiator.getPortAddress());
                Initiator knownInitiator = NetworkUtil.getInitiator(initiator.getPortAddress(), dbClient);
                if (knownInitiator != null) {
                    knownInitiatorsInIG.add(knownInitiator);
                }
            }
        }
        _log.info("Initiators present in IG: {}", allInitiators);
        return knownInitiatorsInIG;
    }

    /**
     * Checks if the IG has other Host's Initiators which belong to same cluster.
     */
    private boolean checkIfIGHasOtherHostInitiatorsOfSameCluster(List<Initiator> knownInitiatorsInIG,
            List<Initiator> requestedInitiatorsInIG, String hostName, String clusterName) {
        Collection<String> initiatorsInIG = Collections2.transform(knownInitiatorsInIG,
                CommonTransformerFunctions.fctnInitiatorToPortName());
        Collection<String> requestedInitiators = Collections2.transform(requestedInitiatorsInIG,
                CommonTransformerFunctions.fctnInitiatorToPortName());
        _log.info("ViPR known Initiators present in IG: {}, Initiators requested to be removed: {}",
                initiatorsInIG, requestedInitiators);
        initiatorsInIG.removeAll(requestedInitiators);
        if (!initiatorsInIG.isEmpty()) {
            _log.info("Host name: {}, Cluster name: {}", hostName, clusterName);
            // check if the other initiators belong to different host
            for (Initiator ini : knownInitiatorsInIG) {
                if (ini.getHostName() != null && !ini.getHostName().equalsIgnoreCase(hostName)) {
                    // check if they belong to same cluster
                    if (ini.getClusterName() != null && clusterName != null && !clusterName.isEmpty()
                            && ini.getClusterName().equalsIgnoreCase(clusterName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void updateStorageGroupPolicyAndLimits(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, VirtualPool newVirtualPool, boolean rollback,
            TaskCompleter taskCompleter) throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask) {
        return Collections.emptyMap();
    }
}
