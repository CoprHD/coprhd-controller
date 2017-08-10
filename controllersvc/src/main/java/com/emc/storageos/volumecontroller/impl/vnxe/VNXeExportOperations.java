/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.PerformancePolicy;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.InvokeTestFailure;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.Snap;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeExportResult;
import com.emc.storageos.vnxe.models.VNXeHost;
import com.emc.storageos.vnxe.models.VNXeHostInitiator;
import com.emc.storageos.vnxe.models.VNXeHostInitiator.HostInitiatorTypeEnum;
import com.emc.storageos.vnxe.models.VNXeLunSnap;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext;
import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext.ExportOperationContextOperation;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExportMaskValidationContext;
import com.emc.storageos.volumecontroller.impl.validators.vnxe.AbstractVNXeValidator;
import com.emc.storageos.workflow.WorkflowService;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;

public class VNXeExportOperations extends VNXeOperations implements ExportMaskOperations {
    private static final Logger _logger = LoggerFactory.getLogger(VNXeExportOperations.class);
    private static final String OTHER = "other";
    // maximum retries for initiator completely removed
    private static final int MAX_REMOVE_INITIATOR_RETRIES = 10;
    // wait 15 seconds before another try
    private static final int WAIT_FOR_RETRY = 15000;

    private WorkflowService workflowService;
    private ValidatorFactory validator;

    public void setValidator(ValidatorFactory validator) {
        this.validator = validator;
    }

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
        List<URI> mappedVolumes = new ArrayList<URI>();
        ExportMask mask = null;
        try {
            _logger.info("createExportMask: Export mask id: {}", exportMask);
            _logger.info("createExportMask: volume-HLU pairs: {}", Joiner.on(',').join(volumeURIHLUs));
            _logger.info("createExportMask: initiators: {}", Joiner.on(',').join(initiatorList));
            _logger.info("createExportMask: assignments: {}", Joiner.on(',').join(targetURIList));

            ExportOperationContext context = new VNXeExportOperationContext();
            taskCompleter.updateWorkflowStepContext(context);

            mask = _dbClient.queryObject(ExportMask.class, exportMask);
            if (mask == null || mask.getInactive()) {
                throw new DeviceControllerException("Invalid ExportMask URI: " + exportMask);
            }

            Set<String> processedCGs = new HashSet<String>();
            Collection<VNXeHostInitiator> initiators = prepareInitiators(initiatorList).values();
            VNXeBase host = apiClient.prepareHostsForExport(initiators);
            validateInitiators(_dbClient, initiatorList, apiClient, host.getId());

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
                    result = apiClient.exportLun(host, nativeId, newhlu);
                    mask.addVolume(volUri, result.getHlu());
                    if (result.isNewAccess()) {
                        mappedVolumes.add(volUri);
                    }
                } else if (URIUtil.isType(volUri, BlockSnapshot.class)) {
                    if (BlockObject.checkForRP(_dbClient, volUri)) {
                        _logger.info(String.format(
                                "BlockObject %s is a RecoverPoint bookmark.  Exporting associated lun %s instead of snap.",
                                volUri, nativeId));
                        result = apiClient.exportLun(host, nativeId, newhlu);
                    } else {
                        result = apiClient.exportSnap(host, nativeId, newhlu);
                        setSnapWWN(apiClient, blockObject, nativeId);
                    }
                    mask.addVolume(volUri, result.getHlu());
                    if (result.isNewAccess()) {
                        mappedVolumes.add(volUri);
                    }
                }
            }

            ExportOperationContext.insertContextOperation(taskCompleter,
                    VNXeExportOperationContext.OPERATION_ADD_VOLUMES_TO_HOST_EXPORT,
                    mappedVolumes);
            mask.setNativeId(host.getId());
            _dbClient.updateObject(mask);
            taskCompleter.ready(_dbClient);

        } catch (Exception e) {
            _logger.error("Unexpected error: createExportMask failed.", e);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("createExportMask", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

        _logger.info("{} createExportMask END...", storage.getSerialNumber());
    }

    private Map<Initiator, VNXeHostInitiator> prepareInitiators(List<Initiator> initiators) {
        Map<Initiator, VNXeHostInitiator> result = new HashMap<Initiator, VNXeHostInitiator>();
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

            result.put(init, hostInit);

        }
        return result;
    }

    @Override
    public void deleteExportMask(StorageSystem storage, URI exportMaskUri,
            List<URI> volumeURIList, List<URI> targetURIList,
            List<Initiator> initiatorList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _logger.info("{} deleteExportMask START...", storage.getSerialNumber());
        boolean removeLastInitiator = false;

        List<URI> volumesToBeUnmapped = new ArrayList<URI>();
        try {
            _logger.info("Export mask id: {}", exportMaskUri);
            if (volumeURIList != null) {
                _logger.info("deleteExportMask: volumes:  {}", Joiner.on(',').join(volumeURIList));
            }
            if (targetURIList != null) {
                _logger.info("deleteExportMask: assignments: {}", Joiner.on(',').join(targetURIList));
            }
            if (initiatorList != null) {
                if (!initiatorList.isEmpty()) {
                    removeLastInitiator = true;
                    _logger.info("deleteExportMask: initiators: {}", Joiner.on(',').join(initiatorList));
                }
            }
            
            // Get the context from the task completer, in case this is a rollback.
            boolean isRollback = WorkflowService.getInstance().isStepInRollbackState(taskCompleter.getOpId());
            if (isRollback) {
                List<URI> addedVolumes = new ArrayList<URI>();
                ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(taskCompleter.getOpId());
                if (context != null && context.getOperations() != null) {
                    _logger.info("Handling deleteExportMask as a result of rollback");
                    ListIterator li = context.getOperations().listIterator(context.getOperations().size());
                    while (li.hasPrevious()) {
                        ExportOperationContextOperation operation = (ExportOperationContextOperation) li.previous();
                        if (operation != null
                                && VNXeExportOperationContext.OPERATION_ADD_VOLUMES_TO_HOST_EXPORT.equals(operation.getOperation())) {
                            addedVolumes = (List<URI>) operation.getArgs().get(0);
                            _logger.info("Removing volumes {} as part of rollback", Joiner.on(',').join(addedVolumes));
                        }
                    }
                }

                volumesToBeUnmapped = addedVolumes;
                if (volumesToBeUnmapped == null || volumesToBeUnmapped.isEmpty()) {
                    _logger.info("There was no context found for add volumes. So there is nothing to rollback.");
                    taskCompleter.ready(_dbClient);
                    return;
                }
            } else {
                volumesToBeUnmapped = volumeURIList;
            }

            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskUri);
            if (exportMask == null || exportMask.getInactive()) {
                throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskUri);
            }

            if (initiatorList.isEmpty()) {
                initiatorList = ExportUtils.getExportMaskInitiators(exportMask, _dbClient);
            }

            VNXeApiClient apiClient = getVnxeClient(storage);
            String hostId = getHostIdFromInitiators(initiatorList, apiClient);
            Set<String> allExportedVolumes = new HashSet<>();

            if (hostId != null) {
                ExportMaskValidationContext ctx = new ExportMaskValidationContext();
                ctx.setStorage(storage);
                ctx.setExportMask(exportMask);
                ctx.setBlockObjects(volumeURIList, _dbClient);
                ctx.setInitiators(initiatorList);
                // Allow exceptions to be thrown when not rolling back
                ctx.setAllowExceptions(!isRollback);
                AbstractVNXeValidator deleteMaskValidator = (AbstractVNXeValidator) validator.exportMaskDelete(ctx);
                deleteMaskValidator.setHostId(hostId);
                deleteMaskValidator.validate();

                if (removeLastInitiator) {
                    ctx = new ExportMaskValidationContext();
                    ctx.setStorage(storage);
                    ctx.setExportMask(exportMask);
                    ctx.setBlockObjects(volumeURIList, _dbClient);
                    ctx.setAllowExceptions(!isRollback);
                    AbstractVNXeValidator removeInitiatorsValidator = (AbstractVNXeValidator) validator.removeInitiators(ctx);
                    removeInitiatorsValidator.setHostId(hostId);
                    removeInitiatorsValidator.validate();

                    boolean hasSharedInitiator = false;
                    for (String strUri : exportMask.getInitiators()) {
                        if (ExportUtils.isInitiatorSharedByMasks(_dbClient, exportMask, URI.create(strUri))) {
                            hasSharedInitiator = true;
                            _logger.info("Initiators are used by multiple export masks");
                            break;
                        }
                    }

                    if (hasSharedInitiator) {
                        // if any initiator is shared, all initiators have to be shared, and each mask should have same set of initiators
                        // Otherwise, removing initiator will not be allowed, user can delete individual export mask
                        Collection<ExportMask> masksWithSharedInitiators = validateAllMasks(_dbClient, exportMask, apiClient, hostId);
                        _logger.info("Masks use the same initiators {}", Joiner.on(", ").join(
                                Collections2.transform(masksWithSharedInitiators, CommonTransformerFunctions.fctnDataObjectToForDisplay())));
                        // need to unexport all volumes of all export masks
                        // except shared export co-exists with exclusive export, don't touch exclusive export
                        // in case of multiple shared exports (e.g., with different projects), all exported LUNs will be unmapped, regardless exclusive export
                        String exportType = ExportMaskUtils.getExportType(_dbClient, exportMask);
                        if (ExportGroupType.Cluster.name().equals(exportType)) {
                            Iterator<ExportMask> maskIter = masksWithSharedInitiators.iterator();
                            while (maskIter.hasNext()) {
                                ExportMask mask = maskIter.next();
                                if (!ExportGroupType.Cluster.name().equals(ExportMaskUtils.getExportType(_dbClient, mask))) {
                                    _logger.info("Ignore exclusive export {}", mask.getMaskName());
                                    maskIter.remove();
                                }
                            }
                        }

                        volumesToBeUnmapped.addAll(getExportedVolumes(_dbClient, storage.getId(), masksWithSharedInitiators));
                    }
                }

                allExportedVolumes = ExportUtils.getAllLUNsForHost(_dbClient, exportMask);
            }

            String opId = taskCompleter.getOpId();
            Set<String> processedCGs = new HashSet<String>();
            for (URI volUri : volumesToBeUnmapped) {
                if (hostId != null) {
                    BlockObject blockObject = BlockObject.fetch(_dbClient, volUri);
                    String nativeId = blockObject.getNativeId();
                    String cgName = VNXeUtils.getBlockObjectCGName(blockObject, _dbClient);
                    if (cgName != null && !processedCGs.contains(cgName)) {
                        processedCGs.add(cgName);
                        VNXeUtils.getCGLock(workflowService, storage, cgName, opId);
                    }
                    if (URIUtil.isType(volUri, Volume.class)) {
                        apiClient.unexportLun(hostId, nativeId);
                    } else if (URIUtil.isType(volUri, BlockSnapshot.class)) {
                        if (BlockObject.checkForRP(_dbClient, volUri)) {
                            _logger.info(String.format(
                                    "BlockObject %s is a RecoverPoint bookmark. Un-exporting associated lun %s instead of snap.",
                                    volUri, nativeId));
                            apiClient.unexportLun(hostId, nativeId);
                        } else {
                            apiClient.unexportSnap(hostId, nativeId);
                            setSnapWWN(apiClient, blockObject, nativeId);
                        }
                    }
                }
                
                // update the exportMask object
                exportMask.removeVolume(volUri);                
            }

            // check if there are LUNs on array
            // initiator will not be able to removed if there are LUNs belongs to other masks (if initiator is shared), or unknown to ViPR
            Set<String> lunIds = new HashSet<>();
            if (hostId != null) {
                lunIds = apiClient.getHostLUNIds(hostId);
                _logger.info("Mapped resources {}", Joiner.on(", ").join(lunIds));

            }

            boolean hasLUN = lunIds.isEmpty()? false : true;
            lunIds.removeAll(allExportedVolumes);
            boolean hasUnknownLUN = lunIds.isEmpty()? false : true;
            _logger.info("Export mask deletion - hasLUN {}, hasUnknownLUN {}", hasLUN, hasUnknownLUN);

            for (Initiator initiator : initiatorList) {
                _logger.info("Processing initiator {}", initiator.getLabel());
                if (hostId != null && (!hasLUN || (!hasUnknownLUN && !ExportUtils.isInitiatorSharedByMasks(_dbClient, exportMask, initiator.getId())))) {
                    String initiatorId = initiator.getInitiatorPort();
                    if (Protocol.FC.name().equals(initiator.getProtocol())) {
                        initiatorId = initiator.getInitiatorNode() + ":" + initiatorId;
                    }

                    try {
                        if (hasLUN) {
                            // move and delete initiator
                            apiClient.deleteInitiators(new ArrayList<String>(Arrays.asList(initiatorId)));
                        } else {
                            apiClient.deleteInitiator(initiatorId);
                        }
                    } catch (VNXeException e) {
                        _logger.warn("Error on deleting initiator: {}", e.getMessage());
                    }
                }
                exportMask.removeFromExistingInitiators(initiator);
                exportMask.removeFromUserCreatedInitiators(initiator);
            }

            _dbClient.updateObject(exportMask);

            if (hostId != null) {
                List<VNXeHostInitiator> vnxeInitiators = apiClient.getInitiatorsByHostId(hostId);
                if (vnxeInitiators.isEmpty()) {
                    Set<String> vnxeLUNIds = apiClient.getHostLUNIds(hostId);
                    if ((vnxeLUNIds.isEmpty())) {
                        try {
                            apiClient.deleteHost(hostId);
                        } catch (VNXeException e) {
                            _logger.warn("Error on deleting host: {}", e.getMessage());
                        }
                    }
                }
            }
            
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

    private String getHostIdFromInitiators(Collection<Initiator> initiators, VNXeApiClient apiClient) throws Exception {
        // all initiator on ViPR host should be on single host
        String vnxeHostId = null;
        for (Initiator initiator : initiators) {
            _logger.info("Processing initiator {}", initiator.getLabel());
            String initiatorId = initiator.getInitiatorPort();
            if (Protocol.FC.name().equals(initiator.getProtocol())) {
                initiatorId = initiator.getInitiatorNode() + ":" + initiatorId;
            }

            // query initiator on array
            VNXeHostInitiator vnxeInitiator = apiClient.getInitiatorByWWN(initiatorId);
            if (vnxeInitiator != null) {
                VNXeBase parentHost = vnxeInitiator.getParentHost();
                if (parentHost != null) {
                    if (vnxeHostId == null) {
                        vnxeHostId = parentHost.getId();
                    } else if (!vnxeHostId.equals(parentHost.getId())) {
                        String msg = String.format(
                                "Initiator %s belongs to %s, but other initiator belongs to %s. Please move initiator to the correct host",
                                initiator.getInitiatorPort(), parentHost.getId(), vnxeHostId);
                        _logger.error(msg);
                        throw new DeviceControllerException(msg);
                    }
                }
            }
        }

        if (vnxeHostId == null) {
            _logger.warn("No host found");
        }

        return vnxeHostId;
    }

    @Override
    public void addVolumes(StorageSystem storage, URI exportMaskUri,
            VolumeURIHLU[] volumeURIHLUs, List<Initiator> initiatorList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _logger.info("{} addVolume START...", storage.getSerialNumber());
        List<URI> mappedVolumes = new ArrayList<URI>();
        ExportMask exportMask = null;

        try {
            _logger.info("addVolumes: Export mask id: {}", exportMaskUri);
            _logger.info("addVolumes: volume-HLU pairs: {}", Joiner.on(',').join(volumeURIHLUs));
            if (initiatorList != null) {
                _logger.info("addVolumes: initiators impacted: {}", Joiner.on(',').join(initiatorList));
            }

            ExportOperationContext context = new VNXeExportOperationContext();
            taskCompleter.updateWorkflowStepContext(context);

            VNXeApiClient apiClient = getVnxeClient(storage);
            exportMask = _dbClient.queryObject(ExportMask.class, exportMaskUri);
            if (exportMask == null || exportMask.getInactive()) {
                throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskUri);
            }

            List<Initiator> initiators = ExportUtils.getExportMaskInitiators(exportMask, _dbClient);
            Collection<VNXeHostInitiator> vnxeInitiators = prepareInitiators(initiators).values();
            VNXeBase host = apiClient.prepareHostsForExport(vnxeInitiators);

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
                    result = apiClient.exportLun(host, nativeId, newhlu);
                    exportMask.addVolume(volUri, result.getHlu());
                    if (result.isNewAccess()) {
                        mappedVolumes.add(volUri);
                    }
                } else if (URIUtil.isType(volUri, BlockSnapshot.class)) {
                    result = apiClient.exportSnap(host, nativeId, newhlu);
                    exportMask.addVolume(volUri, result.getHlu());
                    if (result.isNewAccess()) {
                        mappedVolumes.add(volUri);
                    }
                    String snapWWN = setSnapWWN(apiClient, blockObject, nativeId);
                    if (isVplexVolumeFromSnap) {
                        Volume backendVol = _dbClient.queryObject(Volume.class, vplexBackendVol);
                        backendVol.setWWN(snapWWN);
                        _dbClient.updateObject(backendVol);                        
                    }
                }
            }
            ExportOperationContext.insertContextOperation(taskCompleter,
                    VNXeExportOperationContext.OPERATION_ADD_VOLUMES_TO_HOST_EXPORT,
                    mappedVolumes);
            _dbClient.updateObject(exportMask);
            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_002);
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

            boolean isRollback = WorkflowService.getInstance().isStepInRollbackState(taskCompleter.getOpId());
            if (isRollback) {
                List<URI> addedVolumes = new ArrayList<URI>();
                // Get the context from the task completer, in case this is a rollback.
                ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(taskCompleter.getOpId());
                if (context != null && context.getOperations() != null) {
                    _logger.info("Handling removeVolumes as a result of rollback");
                    ListIterator li = context.getOperations().listIterator(context.getOperations().size());
                    while (li.hasPrevious()) {
                        ExportOperationContextOperation operation = (ExportOperationContextOperation) li.previous();
                        if (operation != null
                                & VNXeExportOperationContext.OPERATION_ADD_VOLUMES_TO_HOST_EXPORT.equals(operation.getOperation())) {
                            addedVolumes = (List<URI>) operation.getArgs().get(0);
                            _logger.info("Removing volumes {} as part of rollback", Joiner.on(',').join(addedVolumes));
                        }
                    }
                }

                volumes = addedVolumes;
                if (volumes == null || volumes.isEmpty()) {
                    _logger.info("There was no context found for add volumes. So there is nothing to rollback.");
                    taskCompleter.ready(_dbClient);
                    return;
                }
            }

            if (volumes == null || volumes.isEmpty()) {
                taskCompleter.ready(_dbClient);
                _logger.warn("{} removeVolumes invoked with zero volumes, resulting in no-op....",
                        storage.getSerialNumber());
                return;
            }

            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskUri);
            if (exportMask == null || exportMask.getInactive()) {
                throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskUri);
            }

            List<Initiator> initiators = ExportUtils.getExportMaskInitiators(exportMask, _dbClient);
            VNXeApiClient apiClient = getVnxeClient(storage);
            String hostId = getHostIdFromInitiators(initiators, apiClient);
            if (hostId != null) {
                ExportMaskValidationContext ctx = new ExportMaskValidationContext();
                ctx.setStorage(storage);
                ctx.setExportMask(exportMask);
                ctx.setInitiators(initiatorList);
                // Allow exceptions to be thrown when not rolling back
                ctx.setAllowExceptions(!isRollback);
                AbstractVNXeValidator removeVolumesValidator = (AbstractVNXeValidator) validator.removeVolumes(ctx);
                removeVolumesValidator.setHostId(hostId);
                removeVolumesValidator.validate();
            }

            String opId = taskCompleter.getOpId();
            Set<String> processedCGs = new HashSet<String>();
            StringMap volsMap = exportMask.getVolumes();
            for (URI volUri : volumes) {
                if (hostId != null && volsMap != null && !volsMap.isEmpty() &&
                        volsMap.keySet().contains(volUri.toString())) {
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
                        apiClient.unexportLun(hostId, nativeId);
                    } else if (URIUtil.isType(volUri, BlockSnapshot.class)) {
                        apiClient.unexportSnap(hostId, nativeId);
                        setSnapWWN(apiClient, blockObject, nativeId);
                    }
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
        List<Initiator> createdInitiators = new ArrayList<Initiator>();
        ExportMask exportMask = null;

        try {
            ExportOperationContext context = new VNXeExportOperationContext();
            taskCompleter.updateWorkflowStepContext(context);

            exportMask = _dbClient.queryObject(ExportMask.class, exportMaskUri);
            if (exportMask == null || exportMask.getInactive()) {
                throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskUri);
            }

            VNXeApiClient apiClient = getVnxeClient(storage);
            List<Initiator> initiators = ExportUtils.getExportMaskInitiators(exportMask, _dbClient);
            // Finding existing host from the array
            Collection<VNXeHostInitiator> vnxeInitiators = prepareInitiators(initiators).values();
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

            validateInitiators(_dbClient, initiatorList, apiClient, hostId);

            Map<Initiator, VNXeHostInitiator> initiatorMap = prepareInitiators(initiatorList);
            for (Entry<Initiator, VNXeHostInitiator> entry : initiatorMap.entrySet()) {
                VNXeHostInitiator newInit = entry.getValue();
                VNXeHostInitiator init = apiClient.getInitiatorByWWN(newInit.getInitiatorId());
                // COP-27752 - fresh deleted initiator may not be deleted completely
                int retry = 0;
                while (retry <= MAX_REMOVE_INITIATOR_RETRIES && init != null && init.getParentHost() == null) {
                    try {
                        Thread.sleep(WAIT_FOR_RETRY);
                    } catch (InterruptedException e1) {
                        Thread.currentThread().interrupt();
                    }
                    init = apiClient.getInitiatorByWWN(newInit.getInitiatorId());
                }

                if (init != null) {
                    // found it
                    VNXeBase host = init.getParentHost();
                    if (host != null && host.getId().equals(hostId)) {
                        // do nothing. it is already in the array
                        _logger.info("The initiator exist in the host in the array");
                    } else if (host == null) {
                        // initiator without parent host, add parent host
                        apiClient.setInitiatorHost(init.getId(), hostId);
                    } else {
                        String msg = String.format(
                                "Initiator %s belongs to %s, but other initiator belongs to %s. Please move initiator to the correct host",
                                init.getInitiatorId(), host.getId(), hostId);
                        _logger.error(msg);

                        if (!createdInitiators.isEmpty()) {
                            for (Initiator initiator : createdInitiators) {
                                exportMask.getInitiators().add(initiator.getId().toString());
                            }
                            _dbClient.updateObject(exportMask);
                            ExportOperationContext.insertContextOperation(taskCompleter,
                                    VNXeExportOperationContext.OPERATION_ADD_INITIATORS_TO_HOST,
                                    createdInitiators);
                        }

                        ServiceError error = DeviceControllerErrors.vnxe.jobFailed("addiniator", msg);
                        taskCompleter.error(_dbClient, error);
                        return;
                    }
                } else {
                    apiClient.createInitiator(newInit, hostId);
                    createdInitiators.add(entry.getKey());
                }
            }
            for (Initiator initiator : initiatorList) {
                exportMask.getInitiators().add(initiator.getId().toString());
            }
            _dbClient.updateObject(exportMask);
            ExportOperationContext.insertContextOperation(taskCompleter,
                    VNXeExportOperationContext.OPERATION_ADD_INITIATORS_TO_HOST,
                    createdInitiators);
            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_003);
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

        boolean isRollback = WorkflowService.getInstance().isStepInRollbackState(taskCompleter.getOpId());
        if (isRollback) {
            List<Initiator> addedInitiators = new ArrayList<Initiator>();
            // Get the context from the task completer, in case this is a rollback.
            ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(taskCompleter.getOpId());
            if (context != null && context.getOperations() != null) {
                _logger.info("Handling removeInitiators as a result of rollback");
                ListIterator li = context.getOperations().listIterator(context.getOperations().size());
                while (li.hasPrevious()) {
                    ExportOperationContextOperation operation = (ExportOperationContextOperation) li.previous();
                    if (operation != null
                            && VNXeExportOperationContext.OPERATION_ADD_INITIATORS_TO_HOST.equals(operation.getOperation())) {
                        addedInitiators = (List<Initiator>) operation.getArgs().get(0);
                        _logger.info("Removing initiators {} as part of rollback", Joiner.on(',').join(addedInitiators));
                    }
                }
            }
            // Update the initiators in the task completer such that we update the export mask/group correctly
            for (Initiator initiator : initiators) {
                if (addedInitiators == null || !addedInitiators.contains(initiator)) {
                    ((ExportMaskRemoveInitiatorCompleter) taskCompleter).removeInitiator(initiator.getId());
                }
            }
            initiators = addedInitiators;
            if (initiators == null || initiators.isEmpty()) {
                _logger.info("There was no context found for add initiator. So there is nothing to rollback.");
                taskCompleter.ready(_dbClient);
                return;
            }
        }

        StringSet initiatorsInMask = mask.getInitiators();
        List<Initiator> initiatorToBeRemoved = new ArrayList<>();
        for (Initiator initiator : initiators) {
            if (initiatorsInMask.contains(initiator.getId().toString())) {
                initiatorToBeRemoved.add(initiator);
            }
        }

        try {
            VNXeApiClient apiClient = getVnxeClient(storage);
            List<Initiator> allInitiators = ExportUtils.getExportMaskInitiators(exportMask, _dbClient);
            String vnxeHostId = getHostIdFromInitiators(allInitiators, apiClient);
            if (vnxeHostId != null) {
                List<VNXeHostInitiator> vnxeInitiators = apiClient.getInitiatorsByHostId(vnxeHostId);
                Map<Initiator, VNXeHostInitiator> vnxeInitiatorsToBeRemoved = prepareInitiators(initiatorToBeRemoved); // initiators is a subset of allInitiators

                Set<String> initiatorIds = new HashSet<String>();
                for (VNXeHostInitiator vnxeInit : vnxeInitiators) {
                    initiatorIds.add(vnxeInit.getInitiatorId());
                }

                Set<String> initiatorsToBeRemoved = new HashSet<String>();
                for (VNXeHostInitiator vnxeInit : vnxeInitiatorsToBeRemoved.values()) {
                    String initiatorId = vnxeInit.getId();
                    if (initiatorIds.remove(initiatorId)) {
                        initiatorsToBeRemoved.add(initiatorId);
                    }
                }

                ExportMaskValidationContext ctx = new ExportMaskValidationContext();
                ctx.setStorage(storage);
                ctx.setExportMask(mask);
                ctx.setBlockObjects(volumeURIList, _dbClient);
                // Allow exceptions to be thrown when not rolling back
                ctx.setAllowExceptions(!isRollback);
                AbstractVNXeValidator removeInitiatorsValidator = (AbstractVNXeValidator) validator.removeInitiators(ctx);
                removeInitiatorsValidator.setHostId(vnxeHostId);
                removeInitiatorsValidator.validate();

                // remove initiator from array, if
                // 1. rollback, or
                // 2. none shared initiator, or
                // 3. shared initiators, but all export masks have same set of initiators
                if (!isRollback) {
                    boolean hasSharedInitiator = false;
                    for (Initiator initiator : initiatorToBeRemoved) {
                        if (ExportUtils.isInitiatorSharedByMasks(_dbClient, mask, initiator.getId())) {
                            hasSharedInitiator = true;
                            break;
                        }
                    }

                    if (hasSharedInitiator) {
                        validateAllMasks(_dbClient, mask, apiClient, vnxeHostId);
                    }
                }
            }

            List<String> initiatorIdList = new ArrayList<>();
            for (Initiator initiator : initiatorToBeRemoved) {
                _logger.info("Processing initiator {}", initiator.getLabel());
                if (vnxeHostId != null) {
                    String initiatorId = initiator.getInitiatorPort();
                    if (Protocol.FC.name().equals(initiator.getProtocol())) {
                        initiatorId = initiator.getInitiatorNode() + ":" + initiatorId;
                    }
                    initiatorIdList.add(initiatorId);
                }
                mask.removeFromExistingInitiators(initiator);
                mask.removeFromUserCreatedInitiators(initiator);
            }

            if (!initiatorIdList.isEmpty()) {
                apiClient.deleteInitiators(initiatorIdList);
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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) throws DeviceControllerException {
        _logger.info("Refreshing export mask {}", mask.getMaskName());

        try {
            VNXeApiClient apiClient = getVnxeClient(storage);
            List<Initiator> initiatorList = ExportUtils.getExportMaskInitiators(mask, _dbClient);
            String vnxeHostId = getHostIdFromInitiators(initiatorList, apiClient);
            if (vnxeHostId != null) {
                VNXeHost vnxeHost = apiClient.getHostById(vnxeHostId);
                if (vnxeHost != null) {
                    Map<String, Integer> discoveredVolumes = apiClient.getHostLUNWWNs(vnxeHostId);

                    // Clear the existing volumes to update with the latest info
                    if (mask.getExistingVolumes() != null && !mask.getExistingVolumes().isEmpty()) {
                        mask.getExistingVolumes().clear();
                    }

                    // COP-27296 fix
                    if (null == mask.getUserAddedVolumes()) {
                        mask.setUserAddedVolumes(new StringMap());
                    }

                    Set<String> existingVolumes = Sets.difference(discoveredVolumes.keySet(), mask.getUserAddedVolumes().keySet());

                    _logger.info(String.format("VNXe discovered volumes: {%s}%n", Joiner.on(',').join(discoveredVolumes.keySet())));
                    _logger.info(String.format("%nVNXe existing volumes : {%s}%n", Joiner.on(',').join(existingVolumes)));

                    for (String lunId : existingVolumes) {
                        mask.addToExistingVolumesIfAbsent(lunId, discoveredVolumes.get(lunId).toString());
                    }
                    // Update user added volume's HLU information in ExportMask and ExportGroup
                    ExportMaskUtils.updateHLUsInExportMask(mask, discoveredVolumes, _dbClient);
                    _dbClient.updateObject(mask);
                }
            }
        } catch (Exception e) {
            _logger.warn("Error on refreshing export mask {}", mask.getMaskName());
        }

        return mask;
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

    /**
     * Check if all shared masks have same set of initiators
     * Could be subset of host initiators, but all masks should have same initiators
     * Initiators have already been removed from host will be ignored in the validation
     *
     * @param dbClient
     * @param exportMask
     * @param apiClient
     * @param vnxeHostId
     * @return collection of export masks of the host
     */
    private Collection<ExportMask> validateAllMasks(DbClient dbClient, ExportMask exportMask,
            VNXeApiClient apiClient, String vnxeHostId) throws DeviceControllerException {
        URI hostUri = null;
        for (String init : exportMask.getInitiators()) {
            Initiator initiator = dbClient.queryObject(Initiator.class, URI.create(init));
            if (initiator != null && !initiator.getInactive()) {
                hostUri = initiator.getHost();
                if (!NullColumnValueGetter.isNullURI(hostUri)) {
                    break;
                }
            }
        }

        // get initiators on array
        Set<String> viprInitiators = new HashSet<>();
        List<VNXeHostInitiator> initiatorList = apiClient.getInitiatorsByHostId(vnxeHostId);
        if (initiatorList != null) {
            for (VNXeHostInitiator initiator : initiatorList) {
                String portWWN = initiator.getPortWWN();
                if (HostInitiatorTypeEnum.INITIATOR_TYPE_ISCSI.equals(initiator.getType())) {
                    portWWN = initiator.getInitiatorId();
                }

                Initiator viprInitiator = NetworkUtil.findInitiatorInDB(portWWN, dbClient);
                if (viprInitiator != null) {
                    if (!hostUri.equals(viprInitiator.getHost())) {
                        String msg = String.format("Initiator %s is not associated with the correct host. Please move the initiator to the correct host", portWWN);
                        _logger.error(msg);
                        throw new DeviceControllerException(msg);
                    }

                    viprInitiators.add(viprInitiator.getId().toString());
                }
            }
        }

        // get initiators from host
        Map<URI, ExportMask> exportMasks = new HashMap<>();
        Map<URI, Set<String>> maskToInitiator = new HashMap<>();
        if (!NullColumnValueGetter.isNullURI(hostUri)) {
            URIQueryResultList list = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.getContainedObjectsConstraint(hostUri, Initiator.class, "host"), list);
            Iterator<URI> uriIter = list.iterator();
            while (uriIter.hasNext()) {
                URI initiatorId = uriIter.next();
                URIQueryResultList egUris = new URIQueryResultList();
                dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                        getExportGroupInitiatorConstraint(initiatorId.toString()), egUris);
                ExportGroup exportGroup = null;
                for (URI egUri : egUris) {
                    exportGroup = dbClient.queryObject(ExportGroup.class, egUri);
                    if (exportGroup == null || exportGroup.getInactive() || exportGroup.getExportMasks() == null) {
                        continue;
                    }
                    List<ExportMask> masks = ExportMaskUtils.getExportMasks(dbClient, exportGroup);
                    for (ExportMask mask : masks) {
                        if (mask != null && !mask.getInactive() && !mask.getId().equals(exportMask.getId()) &&
                                mask.hasInitiator(initiatorId.toString()) && mask.getVolumes() != null) {
                            if (!exportMasks.containsKey(mask.getId())) {
                                exportMasks.put(mask.getId(), mask);
                                Set<String> initiators = new HashSet<String>(mask.getInitiators());
                                // not include initiators that have been removed from array
                                initiators.retainAll(viprInitiators);
                                maskToInitiator.put(mask.getId(), initiators);
                            }
                        }
                    }
                }
            }
        }

        // validate each mask has same set of initiators (not include initiators that have been removed from array)
        Set<String> usedInitiators = new HashSet<String>(exportMask.getInitiators());
        usedInitiators.retainAll(viprInitiators);
        for (Set<String> initiators : maskToInitiator.values()) {
            if (usedInitiators.size() != initiators.size() || !usedInitiators.containsAll(initiators)) {
                throw new DeviceControllerException("Removing initiators from export group is not allowed because host has multiple export groups with different sets of initiators. Please delete individual export group.");
            }
        }

        return exportMasks.values();
    }

    /**
     * Get export volumes of export masks
     *
     * @param dbClient
     * @param storageUri
     * @param masks
     * @return set of exported volumes
     */
    private Set<URI> getExportedVolumes(DbClient dbClient, URI storageUri, Collection<ExportMask> masks) {
        Set<URI> volumes = new HashSet<>();
        for (ExportMask mask : masks) {
            StringMap volumeMap = mask.getVolumes();
            if (volumeMap != null && !volumeMap.isEmpty()) {
                for (String strUri : mask.getVolumes().keySet()) {
                    BlockObject bo = BlockObject.fetch(dbClient, URI.create(strUri));
                    if (bo != null && !bo.getInactive() && storageUri.equals(mask.getStorageDevice())) {
                        volumes.add(bo.getId());
                    }
                }
            }
        }

        return volumes;
    }

    /**
     * Validate all initiators of VNXeHost on single ViPR host, or unknown to ViPR
     *
     * @param dbClient DbClient
     * @param initiators list of initiators that are/will be in the mask (for export mask creation)
     * @param apiClient VNXeApiClient
     * @param vnxeHostId VNXe host Id
     * @return true if validation is passed
     */
    private boolean validateInitiators(DbClient dbClient, List<Initiator> initiators, VNXeApiClient apiClient, String vnxeHostId) {
        if (ExportMaskUtils.areBackendInitiators(initiators)) {
            return true;
        }

        List<VNXeHostInitiator> initiatorList = apiClient.getInitiatorsByHostId(vnxeHostId);
        URI hostId = null;
        if (initiatorList != null) {
            for (VNXeHostInitiator initiator : initiatorList) {
                String portWWN = null;
                if (HostInitiatorTypeEnum.INITIATOR_TYPE_ISCSI.equals(initiator.getType())) {
                    portWWN = initiator.getInitiatorId();
                } else {
                    portWWN = initiator.getPortWWN();
                }

                Initiator viprInitiator = NetworkUtil.findInitiatorInDB(portWWN, dbClient);
                if (viprInitiator != null) {
                    if (NullColumnValueGetter.isNullURI(hostId)) {
                        hostId = viprInitiator.getHost();
                    } else if (!hostId.equals(viprInitiator.getHost())) {
                        String msg = String.format("Found initiators with different host Ids. Initiator %s belongs to host %s, but other initiator belongs to host %s",
                                portWWN, viprInitiator.getHost(), hostId);
                        throw new DeviceControllerException(msg);
                    }
                }
            }
        }

        return true;
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
