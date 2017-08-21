/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.cinder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.cinder.api.CinderApi;
import com.emc.storageos.cinder.api.CinderApiFactory;
import com.emc.storageos.cinder.model.VolumeAttachResponse;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.PerformancePolicy;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.google.common.base.Joiner;

public class CinderExportOperations implements ExportMaskOperations {

    private static final String WWPNS = "wwpns";
    private static final String WWNNS = "wwnns";

    private static Logger log = LoggerFactory.getLogger(CinderExportOperations.class);
    private DbClient dbClient;
    private CinderApiFactory cinderApiFactory;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setCinderApiFactory(CinderApiFactory cinderApiFactory) {
        this.cinderApiFactory = cinderApiFactory;
    }

    @Override
    public void createExportMask(StorageSystem storage, URI exportMaskId,
            VolumeURIHLU[] volumeURIHLUs, List<URI> targetURIList,
            List<Initiator> initiatorList, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        log.info("{} createExportMask START...", storage.getSerialNumber());
        try {
            log.info("createExportMask: Export mask id: {}", exportMaskId);
            log.info("createExportMask: volume-HLU pairs: {}", Joiner.on(',').join(volumeURIHLUs));
            log.info("createExportMask: initiators: {}", Joiner.on(',').join(initiatorList));
            log.info("createExportMask: assignments: {}", Joiner.on(',').join(targetURIList));
            log.info("User assigned HLUs will be ignored as Cinder does not support it.");

            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskId);
            List<Volume> volumes = new ArrayList<Volume>();
            // map to store target LUN id generated for each volume
            Map<URI, Integer> volumeToTargetLunMap = new HashMap<URI, Integer>();

            // Map to store volume to initiatorTargetMap
            Map<Volume, Map<String, List<String>>> volumeToFCInitiatorTargetMap = new HashMap<>();

            for (VolumeURIHLU volumeURIHLU : volumeURIHLUs) {
                URI volumeURI = volumeURIHLU.getVolumeURI();
                Volume volume = dbClient.queryObject(Volume.class, volumeURI);
                volumes.add(volume);
            }

            attachVolumesToInitiators(storage, volumes, initiatorList,
                    volumeToTargetLunMap, volumeToFCInitiatorTargetMap,
                    exportMask);

            // Update targets in the export mask
            if (!volumeToFCInitiatorTargetMap.isEmpty()) {
                updateTargetsInExportMask(storage, volumes.get(0), volumeToFCInitiatorTargetMap, initiatorList, exportMask);
            }

            updateTargetLunIdInExportMask(volumeToTargetLunMap, exportMask);
            dbClient.updateObject(exportMask);

            taskCompleter.ready(dbClient);
        } catch (final Exception ex) {
            log.error("Problem in AttachVolumes: ", ex);
            ServiceError serviceError = DeviceControllerErrors.cinder
                    .operationFailed("doAttachVolumes", ex.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
        log.info("{} createExportMask END...", storage.getSerialNumber());
    }

    @Override
    public void deleteExportMask(StorageSystem storage, URI exportMaskId,
            List<URI> volumeURIList, List<URI> targetURIList,
            List<Initiator> initiatorList, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        log.info("{} deleteExportMask START...", storage.getSerialNumber());

        try {
            log.info("Export mask id: {}", exportMaskId);
            if (volumeURIList != null) {
                log.info("deleteExportMask: volumes:  {}", Joiner.on(',').join(volumeURIList));
            }
            if (targetURIList != null) {
                log.info("deleteExportMask: assignments: {}", Joiner.on(',').join(targetURIList));
            }
            if (initiatorList != null) {
                log.info("deleteExportMask: initiators: {}", Joiner.on(',').join(initiatorList));
            }

            // There is no masking concept on Cinder to delete the export mask.
            // But before marking the task completer as ready,
            // detach the volumes from the initiators that are there in the export mask.
            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskId);
            List<Volume> volumeList = new ArrayList<Volume>();
            StringMap volumes = exportMask.getUserAddedVolumes();
            StringMap initiators = exportMask.getUserAddedInitiators();
            if (volumes != null) {
                for (String vol : volumes.values()) {
                    URI volumeURI = URI.create(vol);
                    volumeURIList.add(volumeURI);
                    Volume volume = dbClient.queryObject(Volume.class, volumeURI);
                    volumeList.add(volume);
                }
            }
            if (initiators != null) {
                for (String ini : initiators.values()) {
                    Initiator initiatorObj = dbClient.queryObject(Initiator.class, URI.create(ini));
                    initiatorList.add(initiatorObj);
                }
            }

            log.info("deleteExportMask: volumes:  {}", Joiner.on(',').join(volumeURIList));
            log.info("deleteExportMask: assignments: {}", Joiner.on(',').join(targetURIList));
            log.info("deleteExportMask: initiators: {}", Joiner.on(',').join(initiatorList));

            detachVolumesFromInitiators(storage, volumeList, initiatorList);

            taskCompleter.ready(dbClient);
        } catch (final Exception ex) {
            log.error("Problem in DetachVolumes: ", ex);
            ServiceError serviceError = DeviceControllerErrors.cinder
                    .operationFailed("doDetachVolumes", ex.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
        log.info("{} deleteExportMask END...", storage.getSerialNumber());
    }

    @Override
    public void addVolumes(StorageSystem storage, URI exportMaskId,
            VolumeURIHLU[] volumeURIHLUs, List<Initiator> initiatorList, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        log.info("{} addVolumes START...", storage.getSerialNumber());

        try {
            log.info("addVolumes: Export mask id: {}", exportMaskId);
            log.info("addVolumes: volume-HLU pairs: {}", Joiner.on(',').join(volumeURIHLUs));
            if (initiatorList != null) {
                log.info("addVolumes: initiators impacted: {}", Joiner.on(',').join(initiatorList));
            }
            log.info("User assigned HLUs will be ignored as Cinder does not support it.");

            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskId);
            List<Volume> volumes = new ArrayList<Volume>();
            List<Initiator> maskInitiatorList = new ArrayList<Initiator>();
            // map to store target LUN id generated for each volume
            Map<URI, Integer> volumeToTargetLunMap = new HashMap<URI, Integer>();
            StringMap initiators = exportMask.getUserAddedInitiators();

            for (VolumeURIHLU volumeURIHLU : volumeURIHLUs) {
                URI volumeURI = volumeURIHLU.getVolumeURI();
                Volume volume = dbClient.queryObject(Volume.class, volumeURI);
                volumes.add(volume);
            }
            for (String ini : initiators.values()) {
                Initiator initiator = dbClient.queryObject(Initiator.class,
                        URI.create(ini));
                maskInitiatorList.add(initiator);
            }

            // Map to store volume to initiatorTargetMap
            Map<Volume, Map<String, List<String>>> volumeToFCInitiatorTargetMap = new HashMap<Volume, Map<String, List<String>>>();

            attachVolumesToInitiators(storage, volumes, maskInitiatorList,
                    volumeToTargetLunMap, volumeToFCInitiatorTargetMap,
                    exportMask);

            // Update targets in the export mask
            if (!volumeToFCInitiatorTargetMap.isEmpty()) {
                updateTargetsInExportMask(storage, volumes.get(0), volumeToFCInitiatorTargetMap, maskInitiatorList, exportMask);
            }
            updateTargetLunIdInExportMask(volumeToTargetLunMap, exportMask);
            dbClient.updateObject(exportMask);

            taskCompleter.ready(dbClient);
        } catch (final Exception ex) {
            log.error("Problem in AddVolumes: ", ex);
            ServiceError serviceError = DeviceControllerErrors.cinder
                    .operationFailed("doAddVolumes", ex.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
        log.info("{} addVolumes END...", storage.getSerialNumber());
    }

    @Override
    public void removeVolumes(StorageSystem storage, URI exportMaskId,
            List<URI> volumeURIs, List<Initiator> initiatorList, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        log.info("{} removeVolumes START...", storage.getSerialNumber());

        try {
            log.info("removeVolumes: Export mask id: {}", exportMaskId);
            log.info("removeVolumes: volumes: {}", Joiner.on(',').join(volumeURIs));
            if (initiatorList != null) {
                log.info("removeVolumes: impacted initiators: {}", Joiner.on(",").join(initiatorList));
            }

            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskId);
            List<Volume> volumes = new ArrayList<Volume>();
            List<Initiator> userAddedInitiatorList = new ArrayList<Initiator>();
            StringMap initiators = exportMask.getUserAddedInitiators();

            for (URI volumeURI : volumeURIs) {
                Volume volume = dbClient.queryObject(Volume.class, volumeURI);
                volumes.add(volume);
            }
            for (String ini : initiators.values()) {
                Initiator initiator = dbClient.queryObject(Initiator.class,
                        URI.create(ini));
                userAddedInitiatorList.add(initiator);
            }

            detachVolumesFromInitiators(storage, volumes, userAddedInitiatorList);

            taskCompleter.ready(dbClient);
        } catch (final Exception ex) {
            log.error("Problem in RemoveVolumes: ", ex);
            ServiceError serviceError = DeviceControllerErrors.cinder
                    .operationFailed("doRemoveVolumes", ex.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
        log.info("{} removeVolumes END...", storage.getSerialNumber());
    }

    @Override
    public void addInitiators(StorageSystem storage, URI exportMaskId,
            List<URI> volumeURIs, List<Initiator> initiators,
            List<URI> targets, TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} addInitiators START...", storage.getSerialNumber());

        try {
            log.info("addInitiators: Export mask id: {}", exportMaskId);
            if (volumeURIs != null) {
                log.info("addInitiators: volumes : {}", Joiner.on(',').join(volumeURIs));
            }
            log.info("addInitiators: initiators : {}", Joiner.on(',').join(initiators));
            log.info("addInitiators: targets : {}", Joiner.on(",").join(targets));

            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskId);
            List<Volume> volumeList = new ArrayList<Volume>();
            // map to store target LUN id generated for each volume
            Map<URI, Integer> volumeToTargetLunMap = new HashMap<URI, Integer>();
            StringMap volumes = exportMask.getUserAddedVolumes();

            for (String vol : volumes.values()) {
                Volume volume = dbClient.queryObject(Volume.class,
                        URI.create(vol));
                volumeList.add(volume);
            }

            // Map to store volume to initiatorTargetMap
            Map<Volume, Map<String, List<String>>> volumeToFCInitiatorTargetMap = new HashMap<Volume, Map<String, List<String>>>();

            attachVolumesToInitiators(storage, volumeList, initiators,
                    volumeToTargetLunMap, volumeToFCInitiatorTargetMap,
                    exportMask);

            // Update targets in the export mask
            if (!volumeToFCInitiatorTargetMap.isEmpty()) {
                updateTargetsInExportMask(storage, volumeList.get(0), volumeToFCInitiatorTargetMap, initiators, exportMask);
                dbClient.updateObject(exportMask);
            }

            // TODO : update volumeToTargetLunMap in export mask.?
            // Do we get different LUN ID for the new initiators from the same Host.?
            taskCompleter.ready(dbClient);
        } catch (final Exception ex) {
            log.error("Problem in AddInitiators: ", ex);
            ServiceError serviceError = DeviceControllerErrors.cinder
                    .operationFailed("doAddInitiators", ex.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
        log.info("{} addInitiators END...", storage.getSerialNumber());
    }

    @Override
    public void removeInitiators(StorageSystem storage, URI exportMaskId,
            List<URI> volumeURIList, List<Initiator> initiators,
            List<URI> targets, TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} removeInitiators START...", storage.getSerialNumber());

        try {
            log.info("removeInitiators: Export mask id: {}", exportMaskId);
            if (volumeURIList != null) {
                log.info("removeInitiators: volumes : {}", Joiner.on(',').join(volumeURIList));
            }
            log.info("removeInitiators: initiators : {}", Joiner.on(',').join(initiators));
            log.info("removeInitiators: targets : {}", Joiner.on(',').join(targets));

            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskId);
            List<Volume> volumeList = new ArrayList<Volume>();
            StringMap volumes = exportMask.getUserAddedVolumes();

            for (String vol : volumes.values()) {
                Volume volume = dbClient.queryObject(Volume.class,
                        URI.create(vol));
                volumeList.add(volume);
            }

            detachVolumesFromInitiators(storage, volumeList, initiators);

            taskCompleter.ready(dbClient);
        } catch (final Exception ex) {
            log.error("Problem in RemoveInitiators: ", ex);
            ServiceError serviceError = DeviceControllerErrors.cinder
                    .operationFailed("doRemoveInitiators", ex.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
        log.info("{} removeInitiators END...", storage.getSerialNumber());
    }

    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames, boolean mustHaveAllPorts) throws DeviceControllerException {
        // not supported for Cinder. There are no masking concepts. So, return null.
        return null;
    }

    @Override
    public Set<Integer> findHLUsForInitiators(StorageSystem storage, List<String> initiatorNames, boolean mustHaveAllPorts) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) throws DeviceControllerException {
        // not supported for Cinder. There are no masking concepts. So, return the given mask as it is.
        return mask;
    }

    /**
     * Attaches volumes to initiators.
     * 
     * @param storage
     *            the storage
     * @param volumes
     *            the volumes
     * @param initiators
     *            the initiators
     * @param volumeToTargetLunMap
     *            the volume to target lun map
     * @throws Exception
     *             the exception
     */
    private void attachVolumesToInitiators(StorageSystem storage,
            List<Volume> volumes, List<Initiator> initiators,
            Map<URI, Integer> volumeToTargetLunMap,
            Map<Volume, Map<String, List<String>>> volumeToInitiatorTargetMap,
            ExportMask exportMask) throws Exception {
        CinderEndPointInfo ep = CinderUtils.getCinderEndPoint(
                storage.getActiveProviderURI(), dbClient);
        log.debug("Getting the cinder APi for the provider with id  {}",
                storage.getActiveProviderURI());
        CinderApi cinderApi = cinderApiFactory.getApi(
                storage.getActiveProviderURI(), ep);

        List<Initiator> iSCSIInitiators = new ArrayList<Initiator>();
        List<Initiator> fcInitiators = new ArrayList<Initiator>();
        splitInitiatorsByProtocol(initiators, iSCSIInitiators, fcInitiators);
        String host = getHostNameFromInitiators(initiators);

        Map<String, String[]> mapSettingVsValues = getFCInitiatorsArray(fcInitiators);
        String[] fcInitiatorsWwpns = mapSettingVsValues.get(WWPNS);
        String[] fcInitiatorsWwnns = mapSettingVsValues.get(WWNNS);

        for (Volume volume : volumes) {

            // cinder generated volume ID
            String volumeId = volume.getNativeId();
            int targetLunId = -1;
            VolumeAttachResponse attachResponse = null;

            // for iSCSI
            for (Initiator initiator : iSCSIInitiators) {
                String initiatorPort = initiator.getInitiatorPort();
                log.debug(String
                        .format("Attaching volume %s ( %s ) to initiator %s on Openstack cinder node",
                                volumeId, volume.getId(), initiatorPort));
                attachResponse = cinderApi.attachVolume(
                        volumeId, initiatorPort, null, null, host);
                log.info("Got response : {}", attachResponse.connection_info.toString());
                targetLunId = attachResponse.connection_info.data.target_lun;
            }

            // for FC
            if (fcInitiatorsWwpns.length > 0) {
                log.debug(String
                        .format("Attaching volume %s ( %s ) to initiators %s on Openstack cinder node",
                                volumeId, volume.getId(), fcInitiatorsWwpns));
                attachResponse = cinderApi.attachVolume(
                        volumeId, null, fcInitiatorsWwpns, fcInitiatorsWwnns, host);
                log.info("Got response : {}", attachResponse.connection_info.toString());
                targetLunId = attachResponse.connection_info.data.target_lun;

                Map<String, List<String>> initTargetMap = attachResponse.connection_info.data.initiator_target_map;
                if (null != initTargetMap && !initTargetMap.isEmpty()) {
                    volumeToInitiatorTargetMap.put(volume,
                            attachResponse.connection_info.data.initiator_target_map);
                }

            }

            volumeToTargetLunMap.put(volume.getId(), targetLunId);

            // After the successful export, create or modify the storage ports
            CinderStoragePortOperations storagePortOperationsInstance = CinderStoragePortOperations.getInstance(storage, dbClient);
            storagePortOperationsInstance.invoke(attachResponse);
        }

        // Add ITLs to volume objects
        storeITLMappingInVolume(volumeToTargetLunMap, exportMask);

    }

    /**
     * Detaches volumes from initiators.
     * 
     * @param storage
     *            the storage
     * @param volumes
     *            the volumes
     * @param initiators
     *            the initiators
     * @throws Exception
     *             the exception
     */
    private void detachVolumesFromInitiators(StorageSystem storage,
            List<Volume> volumes, List<Initiator> initiators) throws Exception {
        CinderEndPointInfo ep = CinderUtils.getCinderEndPoint(
                storage.getActiveProviderURI(), dbClient);
        log.debug("Getting the cinder APi for the provider with id {}",
                storage.getActiveProviderURI());
        CinderApi cinderApi = cinderApiFactory.getApi(
                storage.getActiveProviderURI(), ep);

        List<Initiator> iSCSIInitiators = new ArrayList<Initiator>();
        List<Initiator> fcInitiators = new ArrayList<Initiator>();
        splitInitiatorsByProtocol(initiators, iSCSIInitiators, fcInitiators);
        String host = getHostNameFromInitiators(initiators);

        Map<String, String[]> mapSettingVsValues = getFCInitiatorsArray(fcInitiators);
        String[] fcInitiatorsWwpns = mapSettingVsValues.get(WWPNS);
        String[] fcInitiatorsWwnns = mapSettingVsValues.get(WWNNS);

        for (Volume volume : volumes) {
            // cinder generated volume ID
            String volumeId = volume.getNativeId();

            // for iSCSI
            for (Initiator initiator : iSCSIInitiators) {
                String initiatorPort = initiator.getInitiatorPort();
                log.debug(String
                        .format("Detaching volume %s ( %s ) from initiator %s on Openstack cinder node",
                                volumeId, volume.getId(), initiatorPort));
                cinderApi.detachVolume(volumeId, initiatorPort, null, null, host);

                // TODO : Do not use Job to poll status till we figure out how
                // to get detach status.
                /*
                 * CinderJob detachJob = new CinderDetachVolumeJob(volumeId,
                 * volume.getLabel(), storage.getId(),
                 * CinderConstants.ComponentType.volume.name(), ep,
                 * taskCompleter); ControllerServiceImpl.enqueueJob(new
                 * QueueJob(detachJob));
                 */
            }

            // for FC
            if (fcInitiatorsWwpns.length > 0) {
                log.debug(String
                        .format("Detaching volume %s ( %s ) from initiator %s on Openstack cinder node",
                                volumeId, volume.getId(), fcInitiatorsWwpns));
                cinderApi.detachVolume(volumeId, null, fcInitiatorsWwpns, fcInitiatorsWwnns, host);
            }

            // If ITLs are added, remove them
            removeITLsFromVolume(volume);
        }
    }

    private void splitInitiatorsByProtocol(List<Initiator> initiatorList,
            List<Initiator> iSCSIInitiators, List<Initiator> fcInitiators) {
        for (Initiator initiator : initiatorList) {
            if (Protocol.iSCSI.name().equalsIgnoreCase(initiator.getProtocol())) {
                iSCSIInitiators.add(initiator);
            } else if (Protocol.FC.name().equalsIgnoreCase(initiator.getProtocol())) {
                fcInitiators.add(initiator);
            }
        }
    }

    private Map<String, String[]> getFCInitiatorsArray(List<Initiator> fcInitiators) {
        Map<String, String[]> mapSettingVsValues = new HashMap<>();
        // form an array with all FC initiator wwpns
        // to put into attach request body
        String[] fcInitiatorsWwpns = new String[fcInitiators.size()];
        String[] fcInitiatorsWwnns = new String[fcInitiators.size()];
        int index = 0;
        for (Initiator fcInitiator : fcInitiators) {
            // remove colons in initiator port
            fcInitiatorsWwpns[index] = fcInitiator.getInitiatorPort().replaceAll(CinderConstants.COLON, "");
            String wwnn = fcInitiator.getInitiatorNode();
            if (null != wwnn && wwnn.length() > 0) {
                fcInitiatorsWwnns[index] = wwnn.replaceAll(CinderConstants.COLON, "");
            }

            index++;
        }

        mapSettingVsValues.put(WWPNS, fcInitiatorsWwpns);
        mapSettingVsValues.put(WWNNS, fcInitiatorsWwnns);
        return mapSettingVsValues;
    }

    private String getHostNameFromInitiators(List<Initiator> initiators) {
        String host = null;
        for (Initiator initiator : initiators) {
            host = initiator.getHostName();
            break; // all Initiators given belong to the same host
        }
        return host;
    }

    /**
     * Updates the target LUN ID for volumes in the export mask.
     * 
     * @param volumeToTargetLunMap
     * @param exportMask
     */
    private void updateTargetLunIdInExportMask(
            Map<URI, Integer> volumeToTargetLunMap, ExportMask exportMask) {
        for (URI volumeURI : volumeToTargetLunMap.keySet()) {
            Integer targetLunId = volumeToTargetLunMap.get(volumeURI);
            exportMask.getVolumes().put(volumeURI.toString(),
                    targetLunId.toString());
        }
    }

    /**
     * Updates the initiator to target list map in the export mask
     * 
     * @throws Exception
     */
    private void updateTargetsInExportMask(
            StorageSystem storage,
            Volume volume,
            Map<Volume, Map<String, List<String>>> volumeToInitiatorTargetMapFromAttachResponse,
            List<Initiator> fcInitiatorList, ExportMask exportMask)
                    throws Exception {
        log.debug("START - updateTargetsInExportMask");
        // ITLS for initiator URIs vs Target port URIs - This will be the final
        // filtered list to send for the zoning map update
        Map<URI, List<URI>> mapFilteredInitiatorURIVsTargetURIList = new HashMap<URI, List<URI>>();

        // From the initiators list, construct the map of initiator WWNs to
        // their URIs
        Map<String, URI> initiatorsWWNVsURI = getWWNvsURIFCInitiatorsMap(fcInitiatorList);

        URI varrayURI = volume.getVirtualArray();

        /*
         * Get the list of storage ports from the storage system which are
         * associated with the varray This will be map of storage port WWNs with
         * their URIs
         */
        Map<String, URI> mapVarrayTaggedPortWWNVsURI = getVarrayTaggedStoragePortWWNs(storage, varrayURI);

        // List of WWN entries, used below for filtering the target port list
        // from attach response
        Set<String> varrayTaggedPortWWNs = mapVarrayTaggedPortWWNVsURI.keySet();

        URI vpoolURI = volume.getVirtualPool();
        VirtualPool vpool = dbClient.queryObject(VirtualPool.class, vpoolURI);
        int pathsPerInitiator = vpool.getPathsPerInitiator();

        // Process the attach response output
        Set<Volume> volumeKeysSet = volumeToInitiatorTargetMapFromAttachResponse.keySet();
        for (Volume volumeRes : volumeKeysSet) {
            log.info(String
                    .format("Processing attach response for the volume with URI %s and name %s",
                            volumeRes.getId(), volumeRes.getLabel()));
            Map<String, List<String>> initiatorTargetMap = volumeToInitiatorTargetMapFromAttachResponse
                    .get(volumeRes);
            Set<String> initiatorKeysSet = initiatorTargetMap.keySet();
            for (String initiatorKey : initiatorKeysSet) {
                // The list of filtered target ports ( which are varray tagged )
                // from the attach response
                List<String> filteredTargetList = filterTargetsFromResponse(
                        varrayTaggedPortWWNs, initiatorTargetMap, initiatorKey);
                log.info(String.format(
                        "For initiator %s accessible storage ports are %s ",
                        initiatorKey, filteredTargetList.toString()));

                List<String> tmpTargetList = null;
                if (!isVplex(volumeRes)) {
                    // For VPLEX - no path validations
                    // Path validations are required only for the Host Exports
                    tmpTargetList = checkPathsPerInitiator(pathsPerInitiator, filteredTargetList);

                    if (null == tmpTargetList) {
                        // Rollback case - throw the exception
                        throw new Exception(
                                String.format(
                                        "Paths per initiator criteria is not met for the initiator : %s "
                                                + " Target counts is: %s Expected paths per initiator is: %s",
                                        initiatorKey,
                                        String.valueOf(filteredTargetList.size()),
                                        String.valueOf(pathsPerInitiator)));
                    }

                } else {
                    tmpTargetList = filteredTargetList;
                }

                // Now populate URIs for the map to be returned - convert WWNs
                // to URIs
                populateInitiatorTargetURIMap(
                        mapFilteredInitiatorURIVsTargetURIList,
                        initiatorsWWNVsURI, mapVarrayTaggedPortWWNVsURI,
                        initiatorKey, tmpTargetList);

            } // End initiator iteration

        } // End volume iteration

        // Clean all existing targets in the export mask and add new targets
        List<URI> storagePortListFromMask = StringSetUtil.stringSetToUriList(exportMask.getStoragePorts());
        for (URI removeUri : storagePortListFromMask) {
            exportMask.removeTarget(removeUri);
        }
        exportMask.setStoragePorts(null);

        // Now add new target ports and populate the zoning map
        Set<URI> initiatorURIKeys = mapFilteredInitiatorURIVsTargetURIList.keySet();
        for (URI initiatorURI : initiatorURIKeys) {
            List<URI> storagePortURIList = mapFilteredInitiatorURIVsTargetURIList.get(initiatorURI);
            for (URI portURI : storagePortURIList) {
                exportMask.addTarget(portURI);
            }
        }

        log.debug("END - updateTargetsInExportMask");

    }

    /***
     * Check if the request is for vplex.
     * 
     * @param volume
     * @return
     */
    private boolean isVplex(Volume volume) {
        boolean isVplex = false;
        VirtualPool vpool = dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
        isVplex = VirtualPool.vPoolSpecifiesHighAvailability(vpool);
        return isVplex;
    }

    private Map<String, URI> getWWNvsURIFCInitiatorsMap(
            List<Initiator> fcInitiatorList) {
        log.debug("START - getWWNvsURIFCInitiatorsMap");
        Map<String, URI> initiatorsWWNVsURI = new HashMap<String, URI>();
        for (Initiator init : fcInitiatorList) {
            String wwnNoColon = Initiator.normalizePort(init.getInitiatorPort());
            URI uri = init.getId();
            initiatorsWWNVsURI.put(wwnNoColon, uri);
        }

        log.debug("END - getWWNvsURIFCInitiatorsMap");
        return initiatorsWWNVsURI;
    }

    /**
     * Filters the target port list from the response based on the varray tagging.
     * 
     * @param varrayTaggedPortWWNs
     * @param initiatorTargetMap
     * @param initiatorKey
     * @return
     */
    private List<String> filterTargetsFromResponse(
            Set<String> varrayTaggedPortWWNs,
            Map<String, List<String>> initiatorTargetMap, String initiatorKey) {
        log.debug("START - filterTargetsFromResponse");
        List<String> filteredTargetList = new ArrayList<String>();

        List<String> targetPortListFromResponse = initiatorTargetMap.get(initiatorKey);
        for (String portWWN : targetPortListFromResponse) {
            // Some of the drivers returns ( for e.g NetApp unified driver )
            // the response in lower case, hence it is required to do both checks.
            if (varrayTaggedPortWWNs.contains(portWWN)
                    || varrayTaggedPortWWNs.contains(portWWN.toUpperCase())) {
                filteredTargetList.add(portWWN.toUpperCase());
            }
        }

        log.debug("END - filterTargetsFromResponse");
        return filteredTargetList;
    }

    /**
     * Checks if there are number of targets matching paths per initiator
     * assigned for the vpool.
     * 
     * @param pathsPerInitiator
     * @param targetPathsList
     * @return
     */
    private List<String> checkPathsPerInitiator(int pathsPerInitiator,
            List<String> targetPathsList) {
        log.debug("START - checkPathsPerInitiator");

        List<String> tmpTargetList = new ArrayList<String>();
        int targetPathsCount = targetPathsList.size();

        if (targetPathsCount == pathsPerInitiator) {
            // Happy path, just update the targets list
            tmpTargetList.addAll(targetPathsList);
        } else if (targetPathsCount > pathsPerInitiator) {
            // Select the subset of ports
            if (1 == pathsPerInitiator) {
                tmpTargetList.add(targetPathsList.get(0));
            } else {
                tmpTargetList.addAll(targetPathsList.subList(0, pathsPerInitiator));
            }

        } else {
            return null; // rollback case
        }

        log.debug("END - checkPathsPerInitiator");
        return tmpTargetList;
    }

    /**
     * 
     * @param mapFilteredInitiatorURIVsTargetURIList
     * @param initiatorsWWNVsURI
     * @param mapVarrayTaggedPortWWNVsURI
     * @param initiatorKey
     * @param tmpTargetList
     */
    private void populateInitiatorTargetURIMap(
            Map<URI, List<URI>> mapFilteredInitiatorURIVsTargetURIList,
            Map<String, URI> initiatorsWWNVsURI,
            Map<String, URI> mapVarrayTaggedPortWWNVsURI, String initiatorKey,
            List<String> tmpTargetList) {
        log.debug("START - populateInitiatorTargetURIMap");
        // Convert target storage port wwns to uris
        List<URI> tmpTargetPortURIList = new ArrayList<URI>();
        for (String portWWN : tmpTargetList) {
            tmpTargetPortURIList.add(mapVarrayTaggedPortWWNVsURI.get(portWWN));
        }

        boolean isUpdateMap = false;
        URI initatiatorURI = initiatorsWWNVsURI.get(initiatorKey);
        if (mapFilteredInitiatorURIVsTargetURIList.containsKey(initatiatorURI)) {
            List<URI> existingTargetURIs = mapFilteredInitiatorURIVsTargetURIList.get(initatiatorURI);
            for (URI targetPortURI : tmpTargetPortURIList) {
                if (!existingTargetURIs.contains(targetPortURI)) {
                    existingTargetURIs.add(targetPortURI);
                    isUpdateMap = true;
                }
            }

            if (isUpdateMap) {
                mapFilteredInitiatorURIVsTargetURIList.put(initatiatorURI, existingTargetURIs);
            }

        } else {
            mapFilteredInitiatorURIVsTargetURIList.put(initatiatorURI, tmpTargetPortURIList);
        }

        log.debug("END - populateInitiatorTargetURIMap");
    }

    /**
     * Gets the list of storage port WWNs which are tagged with the virtual
     * array.
     * 
     * @param storage
     *            StorageSystem from which ports needs to be queried.
     * @param varrayURI
     *            URI of the tagged virtual array
     * @return
     */
    private Map<String, URI> getVarrayTaggedStoragePortWWNs(
            StorageSystem storage, URI varrayURI) {
        log.debug("START - getVarrayTaggedStoragePortWWNs");
        // Get the list of storage ports of a storage system which are varray
        // tagged
        Map<URI, List<StoragePort>> networkUriVsStoragePorts = ConnectivityUtil
                .getStoragePortsOfTypeAndVArray(dbClient, storage.getId(),
                        StoragePort.PortType.frontend, varrayURI);

        Map<String, URI> varrayTaggedStoragePortWWNs = new HashMap<String, URI>();

        Set<URI> networkUriSet = networkUriVsStoragePorts.keySet();
        for (URI nwUri : networkUriSet) {
            List<StoragePort> ports = networkUriVsStoragePorts.get(nwUri);
            for (StoragePort port : ports) {
                log.info("Varray Tagged Port is " + port.getPortNetworkId());
                String wwnNoColon = port.getPortNetworkId().replaceAll(CinderConstants.COLON, "");
                varrayTaggedStoragePortWWNs.put(wwnNoColon, port.getId());
            }
        }

        log.debug("END - getVarrayTaggedStoragePortWWNs");

        return varrayTaggedStoragePortWWNs;
    }

    /**
     * Create Initiator Target LUN Mapping as an extension in volume object
     * 
     * @param volume
     *            - volume in which ITL to be added
     * @param exportMask
     *            - exportMask in which the volume is to be added
     * @param targetLunId
     *            - integer value of host LUN id on which volume is accessible.
     */
    private void storeITLMappingInVolume(Map<URI, Integer> volumeToTargetLunMap, ExportMask exportMask) {
        log.debug("START - createITLMappingInVolume");
        for (URI volumeURI : volumeToTargetLunMap.keySet()) {
            Integer targetLunId = volumeToTargetLunMap.get(volumeURI);
            Volume volume = dbClient.queryObject(Volume.class, volumeURI);

            StringSetMap zoningMap = exportMask.getZoningMap();
            Set<String> zoningMapKeys = zoningMap.keySet();
            int initiatorIndex = 0;
            for (String initiator : zoningMapKeys) {
                Initiator initiatorObj = dbClient.queryObject(Initiator.class, URI.create(initiator));
                String initiatorWWPN = initiatorObj.getInitiatorPort().replaceAll(CinderConstants.COLON, "");
                StringSet targetPorts = zoningMap.get(initiator);
                int targetIndex = 0;
                for (String target : targetPorts) {
                    StoragePort targetPort = dbClient.queryObject(StoragePort.class, URI.create(target));
                    String targetPortWWN = targetPort.getPortNetworkId().replaceAll(CinderConstants.COLON, "");

                    // Format is - <InitiatorWWPN>-<TargetWWPN>-<LunId>
                    String itl = initiatorWWPN + "-" + targetPortWWN + "-" + String.valueOf(targetLunId);

                    // ITL keys will be formed as ITL-00, ITL-01, ITL-10, ITL-11 so on
                    String itlKey = CinderConstants.PREFIX_ITL + String.valueOf(initiatorIndex) + String.valueOf(targetIndex);
                    log.info(String.format("Adding ITL %s with key %s", itl, itlKey));
                    StringMap extensionsMap = volume.getExtensions();
                    if (null == extensionsMap) {
                        extensionsMap = new StringMap();
                        extensionsMap.put(itlKey, itl);
                        volume.setExtensions(extensionsMap);
                    } else {
                        volume.getExtensions().put(itlKey, itl);
                    }

                    targetIndex++;
                }

                initiatorIndex++;
            }

            dbClient.updateAndReindexObject(volume);

        }

        log.debug("END - createITLMappingInVolume");

    }

    /**
     * Remove the list of ITLs from the volume extensions
     * 
     * @param volume
     * @return
     */
    private void removeITLsFromVolume(Volume volume) {
        StringMap extensions = volume.getExtensions();
        Set<Map.Entry<String, String>> mapEntries = extensions.entrySet();
        for (Iterator<Map.Entry<String, String>> it = mapEntries.iterator(); it.hasNext();) {
            Map.Entry<String, String> entry = it.next();
            if (entry.getKey().startsWith(CinderConstants.PREFIX_ITL)) {
                it.remove();
            }
        }

    }

    @Override
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask) {
        return Collections.emptyMap();
    }

    @Override
    public void addPaths(StorageSystem storage, URI exportMask, Map<URI, List<URI>> newPaths, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void removePaths(StorageSystem storage, URI exportMask, Map<URI, List<URI>> adjustedPaths, Map<URI, List<URI>> removePaths, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
    
    @Override
    public void changePortGroupAddPaths(StorageSystem storage, URI newMaskURI, URI oldMaskURI, URI portGroupURI, 
            TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
    
    @Override
    public ExportMask findExportMasksForPortGroupChange(StorageSystem storage,
            List<String> initiatorNames,
            URI portGroupURI) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void updatePerformancePolicy(StorageSystem storageSystem, ExportMask exportMask, List<URI> volumeURIs,
            PerformancePolicy newPerfPolicy, boolean isRollback, TaskCompleter taskCompleter) throws Exception {   
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();        
    }
}
