/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.HostLun;
import com.emc.storageos.vnxe.models.Snap;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeExportResult;
import com.emc.storageos.vnxe.models.VNXeHost;
import com.emc.storageos.vnxe.models.VNXeHostInitiator;
import com.emc.storageos.vnxe.models.VNXeLunSnap;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.WorkflowService;
import com.google.common.base.Joiner;

public class VNXeExportOperations extends VNXeOperations implements ExportMaskOperations {
    private static final Logger _logger = LoggerFactory.getLogger(VNXeExportOperations.class);
    private static final String OTHER = "other";
    private WorkflowService workflowService;
    
    public void setWorkflowService(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }
    
    public void getWorkflowService(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }
    
    @Override
    public void createExportMask(StorageSystem storage, URI exportMask,
            VolumeURIHLU[] volumeURIHLUs, List<URI> targetURIList,
            List<Initiator> initiatorList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _logger.info("{} createExportMask START...", storage.getSerialNumber());

        VNXeApiClient apiClient = getVnxeClient(storage);
        try {
            _logger.info("createExportMask: Export mask id: {}", exportMask);
            _logger.info("createExportMask: volume-HLU pairs: {}", Joiner.on(',').join(volumeURIHLUs));
            _logger.info("createExportMask: initiators: {}", Joiner.on(',').join(initiatorList));
            _logger.info("createExportMask: assignments: {}", Joiner.on(',').join(targetURIList));
            Set<String> processedCGs = new HashSet<String>();
            List<VNXeHostInitiator> initiators = prepareInitiators(initiatorList);
            ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMask);
            String opId = taskCompleter.getOpId();
            for (VolumeURIHLU volURIHLU : volumeURIHLUs) {
                URI volUri = volURIHLU.getVolumeURI();
                String hlu = volURIHLU.getHLU();
                _logger.info(String.format("hlu %s", hlu));
                BlockObject blockObject = BlockObject.fetch(_dbClient, volUri);
                String nativeId = blockObject.getNativeId();
                VNXeExportResult result = null;
                Integer newhlu = -1;
                if (hlu != null && !hlu.isEmpty() && !hlu.equals(ExportGroup.LUN_UNASSIGNED_STR)) {
                    newhlu = Integer.valueOf(hlu);
                }
                String cgName = VNXeUtils.getBlockObjectCGName(blockObject, _dbClient);
                if (cgName != null && !processedCGs.contains(cgName)) {
                    processedCGs.add(cgName);
                    VNXeUtils.getCGLock(workflowService, storage, cgName, opId);
                }
                if (URIUtil.isType(volUri, Volume.class)) {
                    result = apiClient.exportLun(nativeId, initiators, newhlu);
                    mask.addVolume(volUri, result.getHlu());
                } else if (URIUtil.isType(volUri, BlockSnapshot.class)) {
                    if (BlockObject.checkForRP(_dbClient, volUri)) {
                        _logger.info(String.format(
                                "BlockObject %s is a RecoverPoint bookmark.  Exporting associated lun %s instead of snap.",
                                volUri, nativeId));
                        result = apiClient.exportLun(nativeId, initiators, newhlu);
                    } else {
                        result = apiClient.exportSnap(nativeId, initiators, null);
                        setSnapWWN(apiClient, blockObject, nativeId);
                    }
                    mask.addVolume(volUri, result.getHlu());
                }
            }
            _dbClient.updateObject(mask);
            taskCompleter.ready(_dbClient);

        } catch (Exception e) {
            _logger.error("Unexpected error: createExportMask failed.", e);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("createExportMask", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        _logger.info("{} createExportMask END...", storage.getSerialNumber());
    }

    private List<VNXeHostInitiator> prepareInitiators(List<Initiator> initiators) {
        List<VNXeHostInitiator> result = new ArrayList<VNXeHostInitiator>();
        for (Initiator init : initiators) {
            _logger.info("initiator: {}", init.getId().toString());
            VNXeHostInitiator hostInit = new VNXeHostInitiator();
            hostInit.setName(init.getHostName());
            String protocol = init.getProtocol();
            if (protocol.equalsIgnoreCase(Protocol.iSCSI.name())) {
                hostInit.setType(VNXeHostInitiator.HostInitiatorTypeEnum.INITIATOR_TYPE_ISCSI);
                hostInit.setChapUserName(init.getInitiatorPort());
                hostInit.setInitiatorId(init.getInitiatorPort());

            } else if (protocol.equalsIgnoreCase(Protocol.FC.name())) {
                hostInit.setType(VNXeHostInitiator.HostInitiatorTypeEnum.INITIATOR_TYPE_FC);
                String portWWN = init.getInitiatorPort();
                String nodeWWN = init.getInitiatorNode();
                StringBuilder builder = new StringBuilder(nodeWWN);
                builder.append(":");
                builder.append(portWWN);
                hostInit.setInitiatorId(builder.toString());
                hostInit.setNodeWWN(nodeWWN);
                hostInit.setPortWWN(portWWN);
            } else {
                _logger.info("The initiator {} protocol {} is not supported, skip",
                        init.getId(), init.getProtocol());
                continue;
            }
            URI hostUri = init.getHost();
            if (!NullColumnValueGetter.isNullURI(hostUri)) {
                Host host = _dbClient.queryObject(Host.class, hostUri);
                if (host != null) {
                    String hostType = host.getType();
                    if (NullColumnValueGetter.isNotNullValue(hostType) && !hostType.equalsIgnoreCase(OTHER)) {
                        hostInit.setHostOsType(hostType);
                    }
                }
            }

            hostInit.setName(init.getHostName());
            result.add(hostInit);

        }
        return result;
    }

    @Override
    public void deleteExportMask(StorageSystem storage, URI exportMaskUri,
            List<URI> volumeURIList, List<URI> targetURIList,
            List<Initiator> initiatorList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _logger.info("{} deleteExportMask START...", storage.getSerialNumber());

        try {
            _logger.info("Export mask id: {}", exportMaskUri);
            if (volumeURIList != null) {
                _logger.info("deleteExportMask: volumes:  {}", Joiner.on(',').join(volumeURIList));
            }
            if (targetURIList != null) {
                _logger.info("deleteExportMask: assignments: {}", Joiner.on(',').join(targetURIList));
            }
            if (initiatorList != null) {
                _logger.info("deleteExportMask: initiators: {}", Joiner.on(',').join(initiatorList));
            }

            VNXeApiClient apiClient = getVnxeClient(storage);
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskUri);
            StringSet inits = exportMask.getInitiators();
            for (String init : inits) {
                _logger.info("Initiator: {}", init);
                Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(init));
                if (initiator != null) {
                    initiatorList.add(initiator);
                }
            }

            String opId = taskCompleter.getOpId();
            Set<String> processedCGs = new HashSet<String>();
            List<VNXeHostInitiator> initiators = prepareInitiators(initiatorList);
            for (URI volUri : volumeURIList) {
                BlockObject blockObject = BlockObject.fetch(_dbClient, volUri);
                String nativeId = blockObject.getNativeId();
                String cgName = VNXeUtils.getBlockObjectCGName(blockObject, _dbClient);
                if (cgName != null && !processedCGs.contains(cgName)) {
                    processedCGs.add(cgName);
                    VNXeUtils.getCGLock(workflowService, storage, cgName, opId);
                }
                if (URIUtil.isType(volUri, Volume.class)) {
                    apiClient.unexportLun(nativeId, initiators);
                } else if (URIUtil.isType(volUri, BlockSnapshot.class)) {
                    if (BlockObject.checkForRP(_dbClient, volUri)) {
                        _logger.info(String.format(
                                "BlockObject %s is a RecoverPoint bookmark.  Un-exporting associated lun %s instead of snap.",
                                volUri, nativeId));
                        apiClient.unexportLun(nativeId, initiators);
                    } else {
                        apiClient.unexportSnap(nativeId, initiators);
                        setSnapWWN(apiClient, blockObject, nativeId);
                    }
                }
                // update the exportMask object
                exportMask.removeVolume(volUri);
            }

            _dbClient.updateObject(exportMask);

            List<ExportGroup> exportGroups = ExportMaskUtils.getExportGroups(_dbClient, exportMask);
            if (exportGroups != null) {
                // Remove the mask references in the export group
                for (ExportGroup exportGroup : exportGroups) {
                    // Remove this mask from the export group
                    exportGroup.removeExportMask(exportMask.getId().toString());
                }
                // Update all of the export groups in the DB
                _dbClient.updateObject(exportGroups);
            }

            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _logger.error("Unexpected error: deleteExportMask failed.", e);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("deleteExportMask", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        _logger.info("{} deleteExportMask END...", storage.getSerialNumber());
    }

    @Override
    public void addVolumes(StorageSystem storage, URI exportMaskUri,
            VolumeURIHLU[] volumeURIHLUs, List<Initiator> initiatorList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _logger.info("{} addVolume START...", storage.getSerialNumber());
        try {
            _logger.info("addVolumes: Export mask id: {}", exportMaskUri);
            _logger.info("addVolumes: volume-HLU pairs: {}", Joiner.on(',').join(volumeURIHLUs));
            if (initiatorList != null) {
                _logger.info("addVolumes: initiators impacted: {}", Joiner.on(',').join(initiatorList));
            }

            VNXeApiClient apiClient = getVnxeClient(storage);
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskUri);
            StringSet initiatorUris = exportMask.getInitiators();
            List<Initiator> initiators = new ArrayList<Initiator>();
            for (String initiatorUri : initiatorUris) {
                Initiator init = _dbClient.queryObject(Initiator.class, URI.create(initiatorUri));
                initiators.add(init);
            }
            List<VNXeHostInitiator> vnxeInitiators = prepareInitiators(initiators);

            String opId = taskCompleter.getOpId();
            Set<String> processedCGs = new HashSet<String>();
            for (VolumeURIHLU volURIHLU : volumeURIHLUs) {
                URI volUri = volURIHLU.getVolumeURI();
                String hlu = volURIHLU.getHLU();
                _logger.info(String.format("hlu %s", hlu));
                BlockObject blockObject = BlockObject.fetch(_dbClient, volUri);
                VNXeExportResult result = null;
                Integer newhlu = -1;
                if (hlu != null && !hlu.isEmpty() && !hlu.equals(ExportGroup.LUN_UNASSIGNED_STR)) {
                    newhlu = Integer.valueOf(hlu);
                }
                // COP-25254 this method could be called when create vplex volumes from snapshot. in this case
                // the volume passed in is an internal volume, representing the snapshot. we need to find the snapshot
                // with the same nativeGUID, then export the snapshot.
                BlockObject snapshot = findSnapshotByInternalVolume(blockObject); 
                boolean isVplexVolumeFromSnap = false;
                URI vplexBackendVol = null;
                if (snapshot != null) {
                    blockObject = snapshot;
                    exportMask.addVolume(volUri, newhlu);
                    isVplexVolumeFromSnap = true;
                    vplexBackendVol = volUri;
                    volUri = blockObject.getId();
                }
                String cgName = VNXeUtils.getBlockObjectCGName(blockObject, _dbClient);
                if (cgName != null && !processedCGs.contains(cgName)) {
                    processedCGs.add(cgName);
                    VNXeUtils.getCGLock(workflowService, storage, cgName, opId);
                }
                String nativeId = blockObject.getNativeId();
                if (URIUtil.isType(volUri, Volume.class)) {
                    result = apiClient.exportLun(nativeId, vnxeInitiators, newhlu);
                    exportMask.addVolume(volUri, result.getHlu());
                } else if (URIUtil.isType(volUri, BlockSnapshot.class)) {
                    result = apiClient.exportSnap(nativeId, vnxeInitiators, newhlu);
                    exportMask.addVolume(volUri, result.getHlu());
                    String snapWWN = setSnapWWN(apiClient, blockObject, nativeId);
                    if (isVplexVolumeFromSnap) {
                        Volume backendVol = _dbClient.queryObject(Volume.class, vplexBackendVol);
                        backendVol.setWWN(snapWWN);
                        _dbClient.updateObject(backendVol);                        
                    }
                }

            }
            _dbClient.updateObject(exportMask);
            taskCompleter.ready(_dbClient);

        } catch (Exception e) {
            _logger.error("Add volumes error: ", e);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("addVolume", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        _logger.info("{} addVolumes END...", storage.getSerialNumber());
    }

    @Override
    public void removeVolumes(StorageSystem storage, URI exportMaskUri,
            List<URI> volumes, List<Initiator> initiatorList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _logger.info("{} removeVolumes: START...", storage.getSerialNumber());

        try {
            _logger.info("removeVolumes: Export mask id: {}", exportMaskUri);
            _logger.info("removeVolumes: volumes: {}", Joiner.on(',').join(volumes));
            if (initiatorList != null) {
                _logger.info("removeVolumes: impacted initiators: {}", Joiner.on(",").join(initiatorList));
            }

            VNXeApiClient apiClient = getVnxeClient(storage);
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskUri);
            StringSet initiatorUris = exportMask.getInitiators();
            List<Initiator> initiators = new ArrayList<Initiator>();
            for (String initiatorUri : initiatorUris) {
                Initiator init = _dbClient.queryObject(Initiator.class, URI.create(initiatorUri));
                initiators.add(init);
            }
            List<VNXeHostInitiator> vnxeInitiators = prepareInitiators(initiators);
            String opId = taskCompleter.getOpId();
            Set<String> processedCGs = new HashSet<String>();
            for (URI volUri : volumes) {
                BlockObject blockObject = BlockObject.fetch(_dbClient, volUri);
                // COP-25254 this method could be called when delete vplex volume created from snapshot. in this case
                // the volume passed in is an internal volume, representing the snapshot. we need to find the snapshot
                // with the same nativeGUID, then unexport the snapshot.
                BlockObject snapshot = findSnapshotByInternalVolume(blockObject);
                if (snapshot != null) {
                    blockObject = snapshot;
                    exportMask.removeVolume(volUri);
                    volUri = blockObject.getId();
                }
                String cgName = VNXeUtils.getBlockObjectCGName(blockObject, _dbClient);
                if (cgName != null && !processedCGs.contains(cgName)) {
                    processedCGs.add(cgName);
                    VNXeUtils.getCGLock(workflowService, storage, cgName, opId);
                }
                String nativeId = blockObject.getNativeId();
                if (URIUtil.isType(volUri, Volume.class)) {
                    apiClient.unexportLun(nativeId, vnxeInitiators);
                } else if (URIUtil.isType(volUri, BlockSnapshot.class)) {
                    apiClient.unexportSnap(nativeId, vnxeInitiators);
                    setSnapWWN(apiClient, blockObject, nativeId);
                }
                // update the exportMask object
                exportMask.removeVolume(volUri);
            }

            _dbClient.updateObject(exportMask);

            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _logger.error("Unexpected error: removeVolumes failed.", e);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("remove volumes failed", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        _logger.info("{} removeVolumes END...", storage.getSerialNumber());
    }

    @Override
    public void addInitiators(StorageSystem storage, URI exportMaskUri,
            List<URI> volumeURIs, List<Initiator> initiatorList,
            List<URI> targets, TaskCompleter taskCompleter) throws DeviceControllerException {

        _logger.info("{} addInitiator START...", storage.getSerialNumber());
        try {
            VNXeApiClient apiClient = getVnxeClient(storage);
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskUri);
            StringSet initiatorUris = exportMask.getInitiators();
            List<Initiator> initiators = new ArrayList<Initiator>();
            for (String initiatorUri : initiatorUris) {
                Initiator init = _dbClient.queryObject(Initiator.class, URI.create(initiatorUri));
                initiators.add(init);
            }
            // Finding existing host in the array
            List<VNXeHostInitiator> vnxeInitiators = prepareInitiators(initiators);
            String hostId = null;
            for (VNXeHostInitiator init : vnxeInitiators) {
                VNXeHostInitiator foundInit = apiClient.getInitiatorByWWN(init.getInitiatorId());
                if (foundInit != null) {
                    VNXeBase host = foundInit.getParentHost();
                    if (host != null) {
                        hostId = host.getId();
                        break;
                    }
                }
            }
            if (hostId == null) {
                String msg = String.format("No existing host found in the array for the existing exportMask %s", exportMask.getMaskName());
                _logger.error(msg);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("addiniator", msg);
                taskCompleter.error(_dbClient, error);
                return;
            }

            List<VNXeHostInitiator> newInitiators = prepareInitiators(initiatorList);
            for (VNXeHostInitiator newInit : newInitiators) {
                VNXeHostInitiator init = apiClient.getInitiatorByWWN(newInit.getInitiatorId());
                if (init != null) {
                    // found it
                    VNXeBase host = init.getParentHost();
                    if (host != null && host.getId().equals(hostId)) {
                        // do nothing. it is already in the array
                        _logger.info("The initiator exist in the host in the array");
                    } else {
                        String msg = String.format(
                                "The new initiator %s does not belong to the same host as other initiators in the ExportMask",
                                newInit.getInitiatorId());
                        _logger.error(msg);
                        ServiceError error = DeviceControllerErrors.vnxe.jobFailed("addiniator", msg);
                        taskCompleter.error(_dbClient, error);
                        return;
                    }
                } else {
                    apiClient.createInitiator(newInit, hostId);
                }
            }
            for (Initiator initiator : initiatorList) {
                exportMask.getInitiators().add(initiator.getId().toString());
            }
            _dbClient.updateObject(exportMask);
            taskCompleter.ready(_dbClient);

        } catch (Exception e) {
            _logger.error("Add initiators error: ", e);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("addInitiator", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

    }

    @Override
    public void removeInitiators(StorageSystem storage, URI exportMask,
            List<URI> volumeURIList, List<Initiator> initiators,
            List<URI> targets, TaskCompleter taskCompleter) throws DeviceControllerException {
        _logger.info("{} removeInitiators START...", storage.getSerialNumber());
        ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMask);
        if (mask == null || mask.getInactive()) {
            _logger.error(String.format("The exportMask %s is invalid.", exportMask));
            throw DeviceControllerException.exceptions.invalidObjectNull();
        }
        try {
            for (Initiator initiator : initiators) {
                mask.removeFromExistingInitiators(initiator);
                mask.removeFromUserCreatedInitiators(initiator);
            }
            _dbClient.updateObject(mask);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _logger.error("Problem in removeInitiators: ", e);
            ServiceError serviceError = DeviceControllerErrors.vnxe.jobFailed("removeInitiator", e.getMessage());
            taskCompleter.error(_dbClient, serviceError);
        }
        _logger.info("{} removeInitiators END...", storage.getSerialNumber());

    }

    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames, boolean mustHaveAllPorts) throws DeviceControllerException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Set<Integer> findHLUsForInitiators(StorageSystem storage, List<String> initiatorNames, boolean mustHaveAllPorts) {
        Set<Integer> usedHLUs = new HashSet<Integer>();
        try {
            String vnxeHostId = null;
            VNXeApiClient apiClient = getVnxeClient(storage);
            for (String initiatorName : initiatorNames) {
                initiatorName = Initiator.toPortNetworkId(initiatorName);
                URIQueryResultList initiatorResult = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(initiatorName),
                        initiatorResult);
                if (initiatorResult.iterator().hasNext()) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorResult.iterator().next());
                    String initiatorId = initiator.getInitiatorPort();
                    if (Protocol.FC.name().equals(initiator.getProtocol())) {
                        initiatorId = initiator.getInitiatorNode() + ":" + initiatorId;

                        // query VNX Unity initiator
                        VNXeHostInitiator vnxeInitiator = apiClient.getInitiatorByWWN(initiatorId);
                        if (vnxeInitiator != null) {
                            VNXeBase parentHost = vnxeInitiator.getParentHost();
                            if (parentHost != null) {
                                vnxeHostId = parentHost.getId();
                                break; // TODO verify - all initiators part of same vnxeHost?
                            }
                        }
                    }
                }
            }

            if (vnxeHostId == null) {
                _logger.info("No Host found on array for initiators {}", Joiner.on(',').join(initiatorNames));
            } else {
                _logger.info("Found matching host {} on array", vnxeHostId);
                // Get vnxeHost from vnxeHostId
                VNXeHost vnxeHost = apiClient.getHostById(vnxeHostId);
                List<VNXeBase> hostLunIds = vnxeHost.getHostLUNs();
                if (hostLunIds != null && !hostLunIds.isEmpty()) {
                    for (VNXeBase hostLunId : hostLunIds) {
                        HostLun hostLun = apiClient.getHostLun(hostLunId.getId());
                        _logger.info("Looking at Host Lun {}; Lun: {}, HLU: {}", hostLun.getLun(), hostLun.getHlu());
                        usedHLUs.add(hostLun.getHlu());
                    }
                }
            }

            _logger.info(String.format("HLUs found for Initiators { %s }: %s",
                    Joiner.on(',').join(initiatorNames), usedHLUs));
        } catch (Exception e) {
            String errMsg = "Encountered an error when attempting to query used HLUs for initiators: " + e.getMessage();
            _logger.error(errMsg, e);
            // throw // TODO
        }
        return usedHLUs;
    }

    @Override
    public Integer getMaximumAllowedHLU(StorageSystem storage) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) throws DeviceControllerException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void updateStorageGroupPolicyAndLimits(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, VirtualPool newVirtualPool, boolean rollback,
            TaskCompleter taskCompleter) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask) {
        return Collections.emptyMap();
    }

    /**
     * set snap wwn after export/unexport. if a snap is not exported to any host, its wwn is null
     *
     * @param apiClient
     * @param blockObj
     * @param snapId
     */
    private String setSnapWWN(VNXeApiClient apiClient, BlockObject blockObj, String snapId) {
        String wwn = null;
        if (!apiClient.isUnityClient()) {
            VNXeLunSnap snap = apiClient.getLunSnapshot(snapId);
            wwn = snap.getPromotedWWN();
        } else {
            Snap snap = apiClient.getSnapshot(snapId);
            wwn = snap.getAttachedWWN();
        }

        if (wwn == null) {
            wwn = NullColumnValueGetter.getNullStr();
        }
        blockObj.setWWN(wwn);
        _dbClient.updateObject(blockObj);
        return wwn;
    }
    
    /**
     * Find the corresponding blocksnapshot with the same nativeGUID as the internal volume
     * 
     * @param volume The block objct of the internal volume
     * @return The snapshot blockObject. return null if there is no corresponding snapshot.
     */
    private BlockObject findSnapshotByInternalVolume(BlockObject volume) {
        BlockObject snap = null;
        String nativeGuid = volume.getNativeGuid();
        if (NullColumnValueGetter.isNotNullValue(nativeGuid) &&
                URIUtil.isType(volume.getId(), Volume.class) ) {
            List<BlockSnapshot> snapshots = CustomQueryUtility.getActiveBlockSnapshotByNativeGuid(_dbClient, nativeGuid);
            if (snapshots != null && !snapshots.isEmpty()) {
                snap = (BlockObject)snapshots.get(0);
            }
        }
        return snap;
    }

}
