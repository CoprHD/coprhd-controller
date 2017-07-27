package com.emc.storageos.vplexcontroller;

import static com.emc.storageos.vplexcontroller.utils.VPlexControllerUtils.getDataObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.InvokeTestFailure;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExportMaskValidationContext;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.VPlexStorageViewInfo;
import com.emc.storageos.vplex.api.clientdata.PortInfo;
import com.emc.storageos.vplexcontroller.utils.VPlexControllerUtils;
import com.emc.storageos.vplexcontroller.utils.VplexExportOperationContext;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.google.common.base.Joiner;

public class VPlexExportOperations extends VPlexOperations{
	
	private static final Logger _log = LoggerFactory.getLogger(VPlexExportOperations.class);
	
	/**
     * Register initiators on the VPLEX, if not already found registered.
     * 
     * @param initiatorUris the initiator URIs to check for registration
     * @param vplexURI the VPLEX URI
     * @param vplexClusterName the VPLEX cluster name (like cluster-1 or cluster-2)
     * @throws Exception if something went wrong
     */
	public void registerInitiators(List<URI> initiatorUris, URI vplexURI, String vplexClusterName) throws Exception {
		StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
        VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
        client.clearInitiatorCache(vplexClusterName);
        
		List<PortInfo> initiatorPortInfos = new ArrayList<PortInfo>();
        List<Initiator> inits = _dbClient.queryObject(Initiator.class, initiatorUris);
        for (Initiator initiator : inits) {
            PortInfo pi = new PortInfo(initiator.getInitiatorPort().toUpperCase()
                    .replaceAll(":", ""), initiator.getInitiatorNode().toUpperCase()
                            .replaceAll(":", ""),
                    initiator.getLabel(),
                    VPlexControllerUtils.getVPlexInitiatorType(initiator, _dbClient));
            initiatorPortInfos.add(pi);
        }
        
        client.registerInitiators(initiatorPortInfos, vplexClusterName);
		
	}
	
	/**
     * Create a Storage View on the VPlex.
     *
     * @param vplexURI
     * @param exportURI
     * @param blockObjectMap
     *            - A list of Volume/Snapshot URIs to LUN ids.
     * @param initiators
     * @param targets
     * @param completer 
     *            - Task completer
     * @throws Exception
     */
	public void createStorageView(URI vplexURI, URI exportURI, URI exportMaskURI, Map<URI, Integer> blockObjectMap,
            List<URI> initiators, List<URI> targets, ExportMaskCreateCompleter completer) throws Exception {
		
		String lockName = null;
        boolean lockAcquired = false;
        try {
			StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
	        ExportMask exportMask = getDataObject(ExportMask.class, exportMaskURI, _dbClient);
	        VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
	
	        // Setup the call to the VPlex API.
	        List<PortInfo> targetPortInfos = new ArrayList<PortInfo>();
	        for (URI target : targets) {
	            StoragePort port = getDataObject(StoragePort.class, target, _dbClient);
	            PortInfo pi = new PortInfo(port.getPortNetworkId().toUpperCase()
	                    .replaceAll(":", ""), null, port.getPortName(), null);
	            targetPortInfos.add(pi);
	            if (lockName == null) {
	                String clusterId = ConnectivityUtil.getVplexClusterOfPort(port);
	                lockName = _vplexApiLockManager.getLockName(vplexURI, clusterId);
	            }
	        }
	        List<PortInfo> initiatorPortInfos = new ArrayList<PortInfo>();
	        for (URI init : initiators) {
	            Initiator initiator = getDataObject(Initiator.class, init, _dbClient);
	            PortInfo pi = new PortInfo(initiator.getInitiatorPort().toUpperCase()
	                    .replaceAll(":", ""), initiator.getInitiatorNode().toUpperCase()
	                            .replaceAll(":", ""),
	                    initiator.getLabel(),
	                    VPlexControllerUtils.getVPlexInitiatorType(initiator, _dbClient));
	            initiatorPortInfos.add(pi);
	        }
	        List<BlockObject> blockObjects = new ArrayList<BlockObject>();
	        Map<String, Integer> boMap = new HashMap<String, Integer>();
	        for (URI vol : blockObjectMap.keySet()) {
	            Integer lun = blockObjectMap.get(vol);
	            if (lun == null) {
	                lun = ExportGroup.LUN_UNASSIGNED;
	            }
	            BlockObject bo = Volume.fetchExportMaskBlockObject(_dbClient, vol);
	            blockObjects.add(bo);
	            boMap.put(bo.getDeviceLabel(), lun);
	        }
	
	        lockAcquired = _vplexApiLockManager.acquireLock(lockName, LockTimeoutValue.get(LockType.VPLEX_API_LIB));
	        if (!lockAcquired) {
	            throw VPlexApiException.exceptions.couldNotObtainConcurrencyLock(vplex.getLabel());
	        }
	        VPlexStorageViewInfo svInfo = client.createStorageView(exportMask.getMaskName(),
	                targetPortInfos, initiatorPortInfos, boMap);
	
	        // When VPLEX volumes or snapshots are exported to a storage view, they get a WWN,
	        // so set the WWN from the returned storage view information.
	        Map<URI, Integer> updatedBlockObjectMap = new HashMap<URI, Integer>();
	        for (BlockObject bo : blockObjects) {
	            String deviceLabel = bo.getDeviceLabel();
	            bo.setWWN(svInfo.getWWNForStorageViewVolume(deviceLabel));
	            _dbClient.updateObject(bo);
	
	            updatedBlockObjectMap.put(bo.getId(),
	                    svInfo.getHLUForStorageViewVolume(deviceLabel));
	        }
	
	        // We also need to update the volume/lun id map in the export mask
	        // to those assigned by the VPLEX.
	        _log.info("Updating volume/lun map in export mask {}", exportMask.getId());
	        _log.info("updatedBlockObjectMap: " + updatedBlockObjectMap.toString());
	        exportMask.addVolumes(updatedBlockObjectMap);
	        // Update user created volumes
	        exportMask.addToUserCreatedVolumes(blockObjects);
	        // update native id (this is the context path to the storage view on the vplex)
	        // e.g.: /clusters/cluster-1/exports/storage-views/V1_myserver_288
	        // this column being set to non-null can be used to indicate a storage view that
	        // has been successfully created on the VPLEX device
	        exportMask.setNativeId(svInfo.getPath());
	        _dbClient.updateObject(exportMask);
	
	        completer.ready(_dbClient);
        } catch (VPlexApiException vae) {
            _log.error("Exception creating VPlex Storage View: " + vae.getMessage(), vae);

            if ((null != vae.getMessage()) &&
                    vae.getMessage().toUpperCase().contains(
                            VPlexApiConstants.DUPLICATE_STORAGE_VIEW_ERROR_FRAGMENT.toUpperCase())) {
                _log.error("storage view creation failure is due to duplicate storage view name");
                ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
                if (null != exportMask) {
                    _log.error("marking ExportMask inactive so that rollback will "
                            + "not delete the existing storage view on the VPLEX");
                    _dbClient.removeObject(exportMask);
                }
            }

            completer.error(_dbClient, vae);
        } finally {
            if (lockAcquired) {
                _vplexApiLockManager.releaseLock(lockName);
            }
        }
	}
	
	/**
     * Delete a VPlex Storage View.
     *
     * @param vplexURI vplex
     * @param exportGroupURI export group
     * @param exportMaskURI export mask
     * @param completer task completer
     * @param stepId step ID
     * @throws Exception
     */
	public void deleteStorageView(URI vplexURI, URI exportGroupURI, URI exportMaskURI, ExportMaskDeleteCompleter completer, String stepId)
            throws Exception {
		try {
			StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            Boolean[] viewFound = new Boolean[] { new Boolean(false) };
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

            if (exportMask != null) {

                String vplexClusterName = VPlexUtil.getVplexClusterName(exportMask, vplexURI, client, _dbClient);
                VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, exportMask.getMaskName());
                if (storageView != null) {
                    // we can ignore this in the case of a missing storage view on the VPLEX, it has already been
                    // deleted
                    _log.info("Refreshing ExportMask {}", exportMask.getMaskName());
                    VPlexControllerUtils.refreshExportMask(
                            _dbClient, storageView, exportMask,
                            VPlexControllerUtils.getTargetPortToPwwnMap(client, vplexClusterName),
                            _networkDeviceController);
                }

                if (exportMask.hasAnyExistingVolumes() || exportMask.hasAnyExistingInitiators()) {
                    _log.warn("ExportMask {} still has non-ViPR-created existing volumes or initiators, "
                            + "so ViPR will not remove it from the VPLEX device", exportMask.getMaskName());
                }

                if (exportMask.getInactive()) {
                    _log.warn("ExportMask {} is already inactive, so there's "
                            + "no need to delete it off the VPLEX", exportMask.getMaskName());
                } else {
                    List<URI> volumeURIs = new ArrayList<URI>();
                    if (exportMask.getUserAddedVolumes() != null && !exportMask.getUserAddedVolumes().isEmpty()) {
                        volumeURIs = StringSetUtil.stringSetToUriList(exportMask.getUserAddedVolumes().values());
                    }
                    List<Initiator> initiators = new ArrayList<>();
                    if (exportMask.getUserAddedInitiators() != null && !exportMask.getUserAddedInitiators().isEmpty()) {
                        List<URI> initiatorURIs = StringSetUtil.stringSetToUriList(exportMask.getUserAddedInitiators().values());
                        initiators.addAll(_dbClient.queryObject(Initiator.class, initiatorURIs));
                    }

                    validator.volumeURIs(volumeURIs, false, false, ValCk.ID);

                    ExportMaskValidationContext ctx = new ExportMaskValidationContext();
                    ctx.setStorage(vplex);
                    ctx.setExportMask(exportMask);
                    ctx.setBlockObjects(volumeURIs, _dbClient);
                    ctx.setInitiators(initiators);
                    ctx.setAllowExceptions(!WorkflowService.getInstance().isStepInRollbackState(stepId));
                    validator.exportMaskDelete(ctx).validate();

                    InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_074);

                    // note: there's a chance if the existing storage view originally had only
                    // storage ports configured in it, then it would be deleted by this
                    _log.info("removing this export mask from VPLEX: " + exportMask.getMaskName());
                    client.deleteStorageView(exportMask.getMaskName(), vplexClusterName, viewFound);
                    if (viewFound[0]) {
                        _log.info("as expected, storage view was found for deletion on the VPLEX.");
                    } else {
                        _log.info("storage view was not found on the VPLEX during deletion, "
                                + "but no errors were encountered.");
                    }
                }

                _log.info("Marking export mask for deletion from Vipr: " + exportMask.getMaskName());
                _dbClient.markForDeletion(exportMask);

                _log.info("updating ExportGroups containing this ExportMask");
                List<ExportGroup> exportGroups = ExportMaskUtils.getExportGroups(_dbClient, exportMask);
                for (ExportGroup exportGroup : exportGroups) {
                    _log.info("Removing mask from ExportGroup " + exportGroup.getGeneratedName());
                    exportGroup.removeExportMask(exportMaskURI);
                    _dbClient.updateObject(exportGroup);
                }
            } else {
                _log.info("ExportMask to delete could not be found in database: " + exportMaskURI);
            }

            completer.ready(_dbClient);
        } catch (VPlexApiException vae) {
            _log.error("Exception deleting ExportMask: " + exportMaskURI, vae);
            VPlexControllerUtils.failStep(completer, stepId, vae, _dbClient);
        }
	}
	
	/**
     * Add initiators to Storage View.
     *
     * @param vplexURI
     *            -- URI of VPlex StorageSystem
     * @param exportURI
     *            -- ExportGroup URI
     * @param maskURI
     *            -- ExportMask URI. Optional.
     *            If non-null, only the indicated ExportMask will be processed.
     *            Otherwise, all ExportMasks will be processed.
     * @param initiatorURIs
     *            -- List of initiator URIs to be added.
     * @param targetURIs
     *            -- optional list of additional targets URIs (VPLEX FE ports) to be added.
     *            If non null, the targets (VPlex front end ports) indicated by the targetURIs will be added
     *            to the Storage View.
     * @param completer the ExportMaskAddInitiatorCompleter
     * @param stepId
     *            -- Workflow step id.
     * @throws Exception
     */
	public void storageViewAddInitiators(URI vplexURI, URI exportURI, URI maskURI,
            List<URI> initiatorURIs,
            List<URI> targetURIs, boolean sharedExportMask,
            ExportMaskAddInitiatorCompleter completer,
            String stepId) throws Exception {
		
		ExportOperationContext context = new VplexExportOperationContext();
        // Prime the context object
        completer.updateWorkflowStepContext(context);

        StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
        ExportGroup exportGroup = getDataObject(ExportGroup.class, exportURI, _dbClient);
        VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

        List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, vplexURI);

        for (ExportMask exportMask : exportMasks) {
            // If a specific ExportMask is to be processed, ignore any others.
            if (maskURI != null && !exportMask.getId().equals(maskURI)) {
                continue;
            }

            _log.info("Refreshing ExportMask {}", exportMask.getMaskName());
            String vplexClusterName = VPlexUtil.getVplexClusterName(exportMask, vplexURI, client, _dbClient);
            VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, exportMask.getMaskName());
            VPlexControllerUtils.refreshExportMask(_dbClient, storageView, exportMask,
                    VPlexControllerUtils.getTargetPortToPwwnMap(client, vplexClusterName),
                    _networkDeviceController);

            // Determine host of ExportMask
            Set<URI> exportMaskHosts = VPlexUtil.getExportMaskHosts(_dbClient, exportMask, sharedExportMask);
            List<Initiator> inits = _dbClient.queryObject(Initiator.class, initiatorURIs);
            if (sharedExportMask) {
                for (Initiator initUri : inits) {
                    URI hostUri = VPlexUtil.getInitiatorHost(initUri);
                    if (null != hostUri) {
                        exportMaskHosts.add(hostUri);
                    }
                }
            }

            // Invoke artificial failure to simulate invalid storageview name on vplex
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_060);

            // Add new targets if specified
            if (targetURIs != null && targetURIs.isEmpty() == false) {
                List<PortInfo> targetPortInfos = new ArrayList<PortInfo>();
                List<URI> targetsAddedToStorageView = new ArrayList<URI>();
                for (URI target : targetURIs) {
                    // Do not try to add a port twice.
                    if (exportMask.getStoragePorts().contains(target.toString())) {
                        continue;
                    }
                    // Log any ports not listed as a target in the Export Masks zoningMap
                    Set<String> zoningMapTargets = BlockStorageScheduler
                            .getTargetIdsFromAssignments(exportMask.getZoningMap());
                    if (!zoningMapTargets.contains(target.toString())) {
                        _log.info(String.format("Target %s not in zoning map", target));
                    }
                    // Build the PortInfo structure for the port to be added
                    StoragePort port = getDataObject(StoragePort.class, target, _dbClient);
                    PortInfo pi = new PortInfo(port.getPortNetworkId().toUpperCase().replaceAll(":", ""),
                            null, port.getPortName(), null);
                    targetPortInfos.add(pi);
                    targetsAddedToStorageView.add(target);
                }
                if (!targetPortInfos.isEmpty()) {
                    // Add the targets on the VPLEX
                    client.addTargetsToStorageView(exportMask.getMaskName(), targetPortInfos);
                    // Add the targets to the database.
                    for (URI target : targetsAddedToStorageView) {
                        exportMask.addTarget(target);
                    }
                }
            }

            List<PortInfo> initiatorPortInfos = new ArrayList<PortInfo>();
            for (Initiator initiator : inits) {
                // Only add this initiator if it's for the same host as other initiators in mask
                if (!exportMaskHosts.contains(VPlexUtil.getInitiatorHost(initiator))) {
                    continue;
                }

                // Only add this initiator if it's not in the mask already after refresh
                if (exportMask.hasInitiator(initiator.getId().toString())) {
                    continue;
                }

                PortInfo portInfo = new PortInfo(initiator.getInitiatorPort()
                        .toUpperCase().replaceAll(":", ""), initiator.getInitiatorNode()
                                .toUpperCase().replaceAll(":", ""),
                        initiator.getLabel(),
                        VPlexControllerUtils.getVPlexInitiatorType(initiator, _dbClient));
                initiatorPortInfos.add(portInfo);
            }

            if (!initiatorPortInfos.isEmpty()) {
                String lockName = null;
                boolean lockAcquired = false;
                try {
                    StringSet portIds = exportMask.getStoragePorts();
                    StoragePort exportMaskPort = getDataObject(StoragePort.class, URI.create(portIds.iterator().next()), _dbClient);
                    String clusterId = ConnectivityUtil.getVplexClusterOfPort(exportMaskPort);
                    lockName = _vplexApiLockManager.getLockName(vplexURI, clusterId);
                    lockAcquired = _vplexApiLockManager.acquireLock(lockName, LockTimeoutValue.get(LockType.VPLEX_API_LIB));
                    if (!lockAcquired) {
                        throw VPlexApiException.exceptions.couldNotObtainConcurrencyLock(vplex.getLabel());
                    }
                    // Add the initiators to the VPLEX
                    client.addInitiatorsToStorageView(exportMask.getMaskName(), vplexClusterName, initiatorPortInfos);
                    ExportOperationContext.insertContextOperation(completer,
                            VplexExportOperationContext.OPERATION_ADD_INITIATORS_TO_STORAGE_VIEW,
                            initiatorURIs);
                } finally {
                    if (lockAcquired) {
                        _vplexApiLockManager.releaseLock(lockName);
                    }
                }
            }
        }

        InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_003);

        completer.ready(_dbClient);  				
	}
	
	/**
     * Remove initiators from a single Storage View as given by the ExportMask URI.
     * Note there is a dependence on ExportMask name equaling the Storage View name.
     *
     * @param vplexURI
     *            -- URI of Vplex Storage System.
     * @param exportGroupURI
     *            -- URI of Export Group.
     * @param exportMaskURI
     *            -- URI of one ExportMask. Call only processes indicaated mask.
     * @param initiatorURIs
     *            -- URIs of Initiators to be removed.
     * @param targetURIs
     *            -- optional targets to be removed from the Storage View.
     *            If non null, a list of URIs for VPlex front-end ports that will be removed from Storage View.
     * @param taskCompleter
     *            -- the task completer
     * @param stepId
     *            -- Workflow step id.
     * @throws Exception
     */
	public void storageViewRemoveInitiators(URI vplexURI, URI exportGroupURI, URI exportMaskURI,
            List<URI> initiatorURIs, List<URI> targetURIs, TaskCompleter taskCompleter, String stepId)
                    throws Exception {
        
        StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
        ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
        VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
        String vplexClusterName = VPlexUtil.getVplexClusterName(exportMask, vplexURI, client, _dbClient);
        Map<String, String> targetPortMap = VPlexControllerUtils.getTargetPortToPwwnMap(client, vplexClusterName);
        VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, exportMask.getMaskName());
        _log.info("Refreshing ExportMask {}", exportMask.getMaskName());
        VPlexControllerUtils.refreshExportMask(_dbClient, storageView, exportMask,
                targetPortMap, _networkDeviceController);            

        // validate the remove initiator operation against the export mask volumes
        List<URI> volumeURIList = (exportMask.getUserAddedVolumes() != null)
                ? URIUtil.toURIList(exportMask.getUserAddedVolumes().values())
                : new ArrayList<URI>();
        if (volumeURIList.isEmpty()) {
            _log.warn("volume URI list for validating remove initiators is empty...");
        }
        ExportMaskValidationContext ctx = new ExportMaskValidationContext();
        ctx.setStorage(vplex);
        ctx.setExportMask(exportMask);
        ctx.setBlockObjects(volumeURIList, _dbClient);
        ctx.setAllowExceptions(!WorkflowService.getInstance().isStepInRollbackState(stepId));
        validator.removeInitiators(ctx).validate();

        // Invoke artificial failure to simulate invalid storageview name on vplex
        InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_060);

        // Optionally remove targets from the StorageView.
        // If there is any existing initiator and existing volume then we skip
        // removing storageports from the storage view as we don't know which ports
        // might be existing ports. If storage ports are removed then we could end up
        // removing all storage ports but leaving the existing initiators and volumes.
        if (!exportMask.hasAnyExistingInitiators() && !exportMask.hasAnyExistingVolumes()) {
            if (targetURIs != null && targetURIs.isEmpty() == false) {
                List<PortInfo> targetPortInfos = new ArrayList<PortInfo>();
                List<URI> targetsAddedToStorageView = new ArrayList<URI>();
                for (URI target : targetURIs) {
                    // Do not try to remove a port twice.
                    if (!exportMask.getStoragePorts().contains(target.toString())) {
                        continue;
                    }
                    // Build the PortInfo structure for the port to be added
                    StoragePort port = getDataObject(StoragePort.class, target, _dbClient);
                    PortInfo pi = new PortInfo(port.getPortNetworkId().toUpperCase().replaceAll(":", ""),
                            null, port.getPortName(), null);
                    targetPortInfos.add(pi);
                    targetsAddedToStorageView.add(target);
                }
                if (!targetPortInfos.isEmpty()) {
                    // Remove the targets from the VPLEX
                    client.removeTargetsFromStorageView(exportMask.getMaskName(), targetPortInfos);
                }
            }
        }

        // Update the initiators in the ExportMask.
        List<PortInfo> initiatorPortInfo = new ArrayList<PortInfo>();
        for (URI initiatorURI : initiatorURIs) {
            Initiator initiator = getDataObject(Initiator.class, initiatorURI, _dbClient);
            // We don't want to remove existing initiator, unless this is a rollback step
            if (exportMask.hasExistingInitiator(initiator) && !WorkflowService.getInstance().isStepInRollbackState(stepId)) {
                continue;
            }
            PortInfo portInfo = new PortInfo(initiator.getInitiatorPort()
                    .toUpperCase().replaceAll(":", ""), initiator.getInitiatorNode()
                            .toUpperCase().replaceAll(":", ""),
                    initiator.getLabel(),
                    VPlexControllerUtils.getVPlexInitiatorType(initiator, _dbClient));
            initiatorPortInfo.add(portInfo);
        }

        // Remove the initiators if there aren't any existing volumes, unless this is a rollback step or validation is disabled.
        if (!initiatorPortInfo.isEmpty() && !exportMask.hasAnyExistingVolumes()) {
            String lockName = null;
            boolean lockAcquired = false;
            try {
                ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
                String clusterId = ConnectivityUtil.getVplexClusterForVarray(exportGroup.getVirtualArray(), vplexURI, _dbClient);
                lockName = _vplexApiLockManager.getLockName(vplexURI, clusterId);
                lockAcquired = _vplexApiLockManager.acquireLock(lockName, LockTimeoutValue.get(LockType.VPLEX_API_LIB));
                if (!lockAcquired) {
                    throw VPlexApiException.exceptions.couldNotObtainConcurrencyLock(vplex.getLabel());
                }
                // Remove the targets from the VPLEX
                // Test mechanism to invoke a failure. No-op on production systems.
                InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_016);

                client.removeInitiatorsFromStorageView(exportMask.getMaskName(), vplexClusterName, initiatorPortInfo);
            } finally {
                if (lockAcquired) {
                    _vplexApiLockManager.releaseLock(lockName);
                }
            }
        }

        taskCompleter.ready(_dbClient);
        
    }
	
	/**
     * Add volumes to a storage view.
     *
     * @param vplexURI
     * 			  -- URI of Vplex Storage System.
     * @param exportGroupURI
     *            -- URI of Export Group.
     * @param exportMaskURI
     *            -- URI of one ExportMask. Call only processes indicaated mask.
     * @param volumeMap
     *            -- the map of URIs to block volumes
     * @param completer 
     *            -- task completer ExportMaskAddVolumeCompleter
     * @param opId
     *            -- Workflow step id.
     * @throws Exception
     */
	public void storageViewAddVolumes(URI vplexURI, URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, ExportMaskAddVolumeCompleter completer, String opId) throws Exception {
		
		String volListStr = "";
		ExportOperationContext context = new VplexExportOperationContext();
        // Prime the context object
        completer.updateWorkflowStepContext(context);

        volListStr = Joiner.on(',').join(volumeMap.keySet());
        StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
        ExportMask exportMask = getDataObject(ExportMask.class, exportMaskURI, _dbClient);

        InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_001);

        VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
        // Need to massage the map to fit the API
        List<BlockObject> volumes = new ArrayList<BlockObject>();
        Map<String, Integer> deviceLabelToHLU = new HashMap<String, Integer>();
        boolean duplicateHLU = false;
        List<URI> volumesToAdd = new ArrayList<URI>();

        String vplexClusterName = VPlexUtil.getVplexClusterName(exportMask, vplexURI, client, _dbClient);
        VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, exportMask.getMaskName());
        VPlexControllerUtils.refreshExportMask(_dbClient, storageView, exportMask,
                VPlexControllerUtils.getTargetPortToPwwnMap(client, vplexClusterName),
                _networkDeviceController);
        exportMask = getDataObject(ExportMask.class, exportMaskURI, _dbClient);

        for (Map.Entry<URI, Integer> entry : volumeMap.entrySet()) {
            if (exportMask.hasVolume(entry.getKey())) {
                _log.info(String
                        .format(
                                "Volume %s is already in Exportmask %s %s hence skipping adding volume again. This must be shared exportmask. ",
                                entry.getKey(), exportMask.getMaskName(), exportMask.getId()));
                continue;
            }
            Integer requestedHLU = entry.getValue();
            // If user have provided specific HLU for volume, then check if its already in use
            if (requestedHLU.intValue() != VPlexApiConstants.LUN_UNASSIGNED &&
                    exportMask.anyVolumeHasHLU(requestedHLU.toString())) {
                String message = String.format("Failed to add Volumes %s to ExportMask %s",
                        volListStr, exportMaskURI);
                _log.error(message);
                String opName = ResourceOperationTypeEnum.ADD_EXPORT_VOLUME.getName();
                ServiceError serviceError = VPlexApiException.errors.exportHasExistingVolumeWithRequestedHLU(entry.getKey().toString(),
                        requestedHLU.toString(), opName);
                VPlexControllerUtils.failStep(completer, opId, serviceError, _dbClient);

                duplicateHLU = true;
                break;
            }
            BlockObject vol = Volume.fetchExportMaskBlockObject(_dbClient, entry.getKey());
            volumes.add(vol);
            deviceLabelToHLU.put(vol.getDeviceLabel(), requestedHLU);
            volumesToAdd.add(entry.getKey());
        }

        // If duplicate HLU are found then return, completer is set to error above
        if (duplicateHLU) {
            return;
        }

        // If deviceLabelToHLU map is empty then volumes already exists in the storage view hence return.
        if (deviceLabelToHLU.isEmpty()) {
            completer.ready(_dbClient);
            return;
        }

        VPlexStorageViewInfo svInfo = client.addVirtualVolumesToStorageView(
                exportMask.getMaskName(), vplexClusterName, deviceLabelToHLU);
        ExportOperationContext.insertContextOperation(completer,
                VplexExportOperationContext.OPERATION_ADD_VOLUMES_TO_STORAGE_VIEW,
                volumesToAdd);

        // When VPLEX volumes are exported to a storage view, they get a WWN,
        // so set the WWN from the returned storage view information.
        Map<URI, Integer> updatedVolumeMap = new HashMap<URI, Integer>();
        for (BlockObject volume : volumes) {
            String deviceLabel = volume.getDeviceLabel();
            String wwn = svInfo.getWWNForStorageViewVolume(volume.getDeviceLabel());
            volume.setWWN(wwn);
            _dbClient.updateObject(volume);

            updatedVolumeMap.put(volume.getId(),
                    svInfo.getHLUForStorageViewVolume(deviceLabel));

            // because this is a managed volume, remove from existing volumes if present.
            // this may happen in the case where a vipr-managed volume was added manually outside of vipr by the
            // user.
            if (exportMask.hasExistingVolume(wwn)) {
                _log.info("wwn {} has been added to the storage view {} by the user, but it "
                        + "was already in existing volumes, removing from existing volumes.",
                        wwn, exportMask.forDisplay());
                exportMask.removeFromExistingVolumes(wwn);
            }

            exportMask.addToUserCreatedVolumes(volume);
        }
        // We also need to update the volume/lun id map in the export mask
        // to those assigned by the VPLEX.
        _log.info("Updating volume/lun map in export mask {}", exportMask.getId());
        exportMask.addVolumes(updatedVolumeMap);
        _dbClient.updateObject(exportMask);
        InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_002);

        completer.ready(_dbClient);
		
	}
	
	/**
	 * Remove volumes from Storage View.
	 * 
	 * @param vplexURI
     * 			  -- URI of Vplex Storage System.
     * @param exportMask
     *            -- ExportMask corresonding to the StorageView
     * @param volumeURIList
     *            -- URI of virtual volumes
     * @param taskCompleter
     *            -- the task completer
     * @param stepId
     *            -- Workflow step id
     * @throws Exception
    */
	public void storageViewRemoveVolumes(URI vplexURI, ExportMask exportMask, List<URI> volumeURIList, 
			TaskCompleter taskCompleter, String stepId) throws Exception {
		
		StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
        VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
        // If no volumes to remove, just return.
        if (volumeURIList.isEmpty()) {
            return;
        }
        
        // validate the remove volume operation against the export mask initiators
        List<Initiator> initiators = new ArrayList<Initiator>();
        if (exportMask.getUserAddedInitiators() != null && !exportMask.getUserAddedInitiators().isEmpty()) {
            Iterator<Initiator> initItr = _dbClient.queryIterativeObjects(Initiator.class,
                    URIUtil.toURIList(exportMask.getUserAddedInitiators().values()), true);
            while (initItr.hasNext()) {
                initiators.add(initItr.next());
            }
        }
        validator.volumeURIs(volumeURIList, false, false, ValCk.ID);

        ExportMaskValidationContext ctx = new ExportMaskValidationContext();
        ctx.setStorage(vplex);
        ctx.setExportMask(exportMask);
        ctx.setInitiators(initiators);
        ctx.setAllowExceptions(!WorkflowService.getInstance().isStepInRollbackState(stepId));
        validator.removeVolumes(ctx).validate();

        // Determine the virtual volume names.
        List<String> blockObjectNames = new ArrayList<String>();
        for (URI boURI : volumeURIList) {
            BlockObject blockObject = Volume.fetchExportMaskBlockObject(_dbClient, boURI);
            blockObjectNames.add(blockObject.getDeviceLabel());
        }
        // Remove volumes from the storage view.
        String vplexClusterName = VPlexUtil.getVplexClusterName(exportMask, vplex.getId(), client, _dbClient);
        _log.info("about to remove {} from StorageView {} on cluster {}", blockObjectNames, exportMask.getMaskName(), vplexClusterName);
        client.removeVirtualVolumesFromStorageView(exportMask.getMaskName(), vplexClusterName,
                blockObjectNames);

        _log.info("successfully removed " + blockObjectNames + " from StorageView " + exportMask.getMaskName());
		
	}
	
	/**
     * Add storage port(s) to Storage View.
     *
     * @param vplexURI
     *            -- URI of VPlex StorageSystem
     * @param exportURI
     *            -- ExportGroup URI
     * @param maskURI
     *            -- ExportMask URI.
     * @param targetURIs
     *            -- list of targets URIs (VPLEX FE ports) to be added.
     *            If not null, the targets (VPlex front end ports) indicated by the targetURIs will be added
     *            to the Storage View making sure they do belong to zoningMap storagePorts.
     *            If null, then ports are calculated from the zoningMap.
     * @param completer the ExportMaskAddInitiatorCompleter
     * @param stepId
     *            -- Workflow step id.
     * @throws WorkflowException
     */
	public void storageViewAddStoragePorts(URI vplexURI, URI exportURI, URI maskURI, List<URI> targetURIs, 
			ExportMaskAddInitiatorCompleter completer, String stepId) throws Exception {
		
		StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
        ExportGroup exportGroup = getDataObject(ExportGroup.class, exportURI, _dbClient);
        VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

        List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, vplexURI);

        for (ExportMask exportMask : exportMasks) {
            // If a specific ExportMask is to be processed, ignore any others.
            if (maskURI != null && !exportMask.getId().equals(maskURI)) {
                continue;
            }

            ArrayList<URI> filteredTargetURIs = new ArrayList<URI>();
            // Filter or get targets from the zoning map
            if (exportMask.getZoningMap() != null) {
                Set<String> zoningMapTargets = BlockStorageScheduler
                        .getTargetIdsFromAssignments(exportMask.getZoningMap());
                List<URI> zoningMapTargetURIs = StringSetUtil.stringSetToUriList(zoningMapTargets);
                if (targetURIs == null || targetURIs.isEmpty()) {
                    // Add all storage ports from the zoning map
                    if (zoningMapTargetURIs != null && !zoningMapTargetURIs.isEmpty()) {
                        filteredTargetURIs.addAll(zoningMapTargetURIs);
                    }
                } else {
                    // Log any ports not in the zoning map.
                    for (URI targetURI : targetURIs) {
                        filteredTargetURIs.add(targetURI);
                        if (zoningMapTargetURIs.contains(targetURI)) {
                            _log.info(String.format("Target %s not in zoning map", targetURI));
                        }   
                    }
                }
            }

            // Add new targets if specified
            if (filteredTargetURIs != null && filteredTargetURIs.isEmpty() == false) {
                List<PortInfo> targetPortInfos = new ArrayList<PortInfo>();
                List<URI> targetsAddedToStorageView = new ArrayList<URI>();
                for (URI target : filteredTargetURIs) {
                    // Do not try to add a port twice.
                    if (exportMask.getStoragePorts().contains(target.toString())) {
                        continue;
                    }
                    // Build the PortInfo structure for the port to be added
                    StoragePort port = getDataObject(StoragePort.class, target, _dbClient);
                    PortInfo pi = new PortInfo(port.getPortNetworkId().toUpperCase().replaceAll(":", ""),
                            null, port.getPortName(), null);
                    targetPortInfos.add(pi);
                    targetsAddedToStorageView.add(target);
                }
                if (!targetPortInfos.isEmpty()) {
                    // Add the targets on the VPLEX
                    client.addTargetsToStorageView(exportMask.getMaskName(), targetPortInfos);
                    // Add the targets to the database.
                    for (URI target : targetsAddedToStorageView) {
                        exportMask.addTarget(target);
                    }
                    _dbClient.updateObject(exportMask);
                }
            }
        }
        completer.ready(_dbClient);
	}
	
	/**
     * Remove storage ports from Storage View.
     
     * @param vplexURI
     *            -- URI of VPlex StorageSystem
     * @param exportURI
     *            -- ExportGroup URI
     * @param maskURI
     *            -- ExportMask URI.
     * @param targetURIs
     *            -- list of targets URIs (VPLEX FE ports) to be removed.
     *            If non null, the targets (VPlex front end ports) indicated by the targetURIs will be removed
     *            from the Storage View.
     * @param stepId
     *            -- Workflow step id.
     * @throws Exception
     */
	public void storageViewRemoveStoragePorts(URI vplexURI, URI exportURI, URI maskURI,
            List<URI> targetURIs, TaskCompleter taskCompleter, String stepId) throws Exception {
		
		StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
        ExportMask exportMask = _dbClient.queryObject(ExportMask.class, maskURI);

        VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
        String vplexClusterName = VPlexUtil.getVplexClusterName(exportMask, vplexURI, client, _dbClient);
        Map<String, String> targetPortMap = VPlexControllerUtils.getTargetPortToPwwnMap(client, vplexClusterName);
        VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, exportMask.getMaskName());
        _log.info("Refreshing ExportMask {}", exportMask.getMaskName());
        VPlexControllerUtils.refreshExportMask(_dbClient, storageView, exportMask,
                targetPortMap, _networkDeviceController);

        // validate the remove storage port operation against the export mask volumes
        // this is conceptually the same as remove initiators, so will validate with volumes
        List<URI> volumeURIList = (exportMask.getUserAddedVolumes() != null)
                ? URIUtil.toURIList(exportMask.getUserAddedVolumes().values())
                : new ArrayList<URI>();
        if (volumeURIList.isEmpty()) {
            _log.warn("volume URI list for validating remove initiators is empty...");
        }

        // Optionally remove targets from the StorageView.
        // If there is any existing initiator and existing volume then we skip
        // removing storage ports from the storage view as we don't know which ports
        // might be existing ports. If storage ports are removed then we could end up
        // removing all storage ports but leaving the existing initiators and volumes.
        if (!exportMask.hasAnyExistingInitiators() && !exportMask.hasAnyExistingVolumes()) {
            ExportMaskValidationContext ctx = new ExportMaskValidationContext();
            ctx.setStorage(vplex);
            ctx.setExportMask(exportMask);
            ctx.setBlockObjects(volumeURIList, _dbClient);
            ctx.setAllowExceptions(!WorkflowService.getInstance().isStepInRollbackState(stepId));
            validator.removeInitiators(ctx).validate();

            if (targetURIs != null && targetURIs.isEmpty() == false) {
                List<PortInfo> targetPortInfos = new ArrayList<PortInfo>();
                List<URI> targetsToRemoveFromStorageView = new ArrayList<URI>();
                for (URI target : targetURIs) {
                    // Do not try to remove a port twice.
                    if (!exportMask.getStoragePorts().contains(target.toString())) {
                        continue;
                    }
                    // Build the PortInfo structure for the port to be added
                    StoragePort port = getDataObject(StoragePort.class, target, _dbClient);
                    PortInfo pi = new PortInfo(port.getPortNetworkId().toUpperCase().replaceAll(":", ""),
                            null, port.getPortName(), null);
                    targetPortInfos.add(pi);
                    targetsToRemoveFromStorageView.add(target);
                }
                if (!targetPortInfos.isEmpty()) {
                    // Remove the targets from the VPLEX
                    client.removeTargetsFromStorageView(exportMask.getMaskName(), targetPortInfos);
                    // Remove the targets to the database.
                    for (URI target : targetsToRemoveFromStorageView) {
                        exportMask.removeTarget(target);
                    }
                    _dbClient.updateObject(exportMask);
                }
            }
        }

        taskCompleter.ready(_dbClient);
	}

}
