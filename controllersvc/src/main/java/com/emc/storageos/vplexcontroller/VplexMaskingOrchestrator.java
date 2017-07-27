package com.emc.storageos.vplexcontroller;

import static com.emc.storageos.vplexcontroller.utils.VPlexControllerUtils.getDataObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.networkcontroller.impl.NetworkZoningParam;
import com.emc.storageos.recoverpoint.utils.WwnUtils;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.AbstractBasicMaskingOrchestrator;
import com.emc.storageos.volumecontroller.impl.block.ExportWorkflowEntryPoints;
import com.emc.storageos.volumecontroller.impl.block.MaskingWorkflowEntryPoints;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportAddInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportAddVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddPathsCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemovePathsCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportRemoveInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportRemoveVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.volumecontroller.placement.ExportPathUpdater;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplex.api.VPlexStorageViewInfo;
import com.emc.storageos.vplexcontroller.utils.VPlexControllerUtils;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;

public class VplexMaskingOrchestrator extends AbstractBasicMaskingOrchestrator {

    private static final Logger _log = LoggerFactory.getLogger(VplexMaskingOrchestrator.class);
	private VPlexApiFactory _vplexApiFactory;
	private static CoordinatorClient coordinator;
    private VPlexApiLockManager _vplexApiLockManager;
    
    private static final URI nullURI = NullColumnValueGetter.getNullURI();
    @Autowired
    private DataSourceFactory dataSourceFactory;
    @Autowired
    private CustomConfigHandler customConfigHandler;
    
    private static final int FOUND_NO_VIPR_EXPORT_MASKS = 0;
    private static final int FOUND_ONE_VIPR_EXPORT_MASK = 1;

    private static final String STORAGE_VIEW_ADD_VOLUMES_METHOD = "storageViewAddVolumes";
    private static final String STORAGE_VIEW_ADD_INITIATORS_METHOD = "storageViewAddInitiators";
    private static final String STORAGE_VIEW_ADD_STORAGE_PORTS_METHOD = "storageViewAddStoragePorts";
    private static final String STORAGE_VIEW_ADD_INITS_ROLLBACK_METHOD = "storageViewAddInitiatorsRollback";
    private static final String STORAGE_VIEW_ADD_VOLS_ROLLBACK_METHOD = "storageViewAddVolumesRollback";
    private static final String STORAGE_VIEW_REMOVE_INITIATORS_METHOD = "storageViewRemoveInitiators";
    private static final String STORAGE_VIEW_REMOVE_VOLUMES_METHOD = "storageViewRemoveVolumes";
    private static final String STORAGE_VIEW_REMOVE_STORAGE_PORTS_METHOD = "storageViewRemoveStoragePorts";
 
    private static final String ZONING_STEP = "zoning";
    private static final String INITIATOR_REGISTRATION = "initiatorRegistration";
    private static final String ROLLBACK_METHOD_NULL = "rollbackMethodNull";
    private static final String CREATE_STORAGE_VIEW = "createStorageView";
    private static final String ROLLBACK_CREATE_STORAGE_VIEW = "rollbackCreateStorageView";
    private static final String DELETE_STORAGE_VIEW = "deleteStorageView";
    private static final String REGISTER_INITIATORS_METHOD_NAME = "registerInitiators";
    private static final String EXPORT_GROUP_REMOVE_VOLUMES = "exportGroupRemoveVolumes";
    
         
	/**
     * Inject coordinatorClient
     *
     * @param coordinatorClient
     */
    public static void setCoordinator(CoordinatorClient coordinator) {
    	VplexMaskingOrchestrator.coordinator = coordinator;
    }

    public void setVplexApiFactory(VPlexApiFactory _vplexApiFactory) {
        this._vplexApiFactory = _vplexApiFactory;
    }
    
    public void setVplexApiLockManager(VPlexApiLockManager lockManager) {
        _vplexApiLockManager = lockManager;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.volumecontroller.impl.vplex.VplexController#exportGroupCreate(java.net.URI, java.net.URI,
     * java.util.Map,
     * java.util.List, java.lang.String)
     */
    @Override
    public void exportGroupCreate(URI vplex, URI export, List<URI> initiators,
            Map<URI, Integer> volumeMap,
            String opId) throws ControllerException {
        ExportCreateCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);

            completer = new ExportCreateCompleter(export, volumeMap, opId);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, export);
            StorageSystem vplexSystem = _dbClient.queryObject(StorageSystem.class, vplex);
            _log.info(String.format("VPLEX exportGroupCreate %s vplex %s",
                    exportGroup.getLabel(), vplexSystem.getNativeGuid()));
            URI srcVarray = exportGroup.getVirtualArray();
            boolean isRecoverPointExport = ExportUtils.checkIfInitiatorsForRP(_dbClient,
                    exportGroup.getInitiators());

            initiators = VPlexUtil.filterInitiatorsForVplex(_dbClient, initiators);

            if (initiators == null || initiators.isEmpty()) {
                _log.info("ExportGroup created with no initiators connected to VPLEX supplied, no need to orchestrate VPLEX further.");
                completer.ready(_dbClient);
                return;
            }

            // Determine whether this export will be done across both VPLEX clusters, or just one.
            // If both, we will set up some data structures to handle both exports.
            // Distributed volumes can be exported to both clusters.
            // Local volumes may be from either the src or HA varray, and will be exported
            // the the appropriate hosts.
            // Hosts may have connectivity to one varray (local or HA), or both.
            // Only one HA varray is allowed (technically one Varray not matching the ExportGroup).
            // It is persisted in the exportGroup.altVirtualArray map with the Vplex System ID as key.

            // Get a mapping of Virtual Array to the Volumes visible in each Virtual Array.
            Map<URI, Set<URI>> varrayToVolumes = VPlexUtil.mapBlockObjectsToVarrays(_dbClient, volumeMap.keySet(),
                    vplex, exportGroup);
            // Extract the src volumes into their own set.
            Set<URI> srcVolumes = varrayToVolumes.get(srcVarray);
            // Remove the srcVolumes from the varraysToVolumesMap
            varrayToVolumes.remove(srcVarray);

            URI haVarray = null;
            // If there is one (or more) HA virtual array as given by the volumes
            // And we're not exporting to recover point Initiators
            if (!varrayToVolumes.isEmpty() && !isRecoverPointExport) {
                // Make sure there is only one "HA" varray and return the HA virtual array.
                haVarray = VPlexUtil.pickHAVarray(varrayToVolumes);
            }

            // Partition the initiators by Varray.
            // Some initiators may be connected to both virtual arrays, others connected to
            // only one of the virtual arrays or the other.
            List<URI> varrayURIs = new ArrayList<URI>();
            varrayURIs.add(srcVarray);
            if (haVarray != null) {
                varrayURIs.add(haVarray);
            }
            Map<URI, List<URI>> varrayToInitiators = VPlexUtil.partitionInitiatorsByVarray(
                    _dbClient, initiators, varrayURIs, vplexSystem);

            if (varrayToInitiators.isEmpty()) {
                throw VPlexApiException.exceptions
                        .exportCreateNoinitiatorsHaveCorrectConnectivity(
                                initiators.toString(), varrayURIs.toString());
            }

            // Validate that the export can be successful:
            // If both src and ha volumes are present, all hosts must be connected.
            // Otherwise, at least one host must be connected.
            URI source = (srcVolumes == null || srcVolumes.isEmpty()) ? null : srcVarray;
            VPlexUtil.validateVPlexClusterExport(_dbClient, source,
                    haVarray, initiators, varrayToInitiators);

            Workflow workflow = _workflowService.getNewWorkflow(VPlexMaskingWorkflowEntryPoints.getInstance(), "exportGroupCreate", true, opId);

            // If possible, do the HA side export. To do this we must have both
            // HA side initiators, and volumes accessible from the HA side.
            if (haVarray != null && varrayToInitiators.get(haVarray) != null) {
                exportGroup.putAltVirtualArray(vplex.toString(), haVarray.toString());
                _dbClient.updateObject(exportGroup);
            }

            findAndUpdateFreeHLUsForClusterExport(vplexSystem, exportGroup, initiators, volumeMap);

            // Do the source side export if there are src side volumes and initiators.
            if (srcVolumes != null && varrayToInitiators.get(srcVarray) != null) {
                assembleExportMasksWorkflow(vplex, export, srcVarray,
                        varrayToInitiators.get(srcVarray),
                        ExportMaskUtils.filterVolumeMap(volumeMap, srcVolumes), workflow, null, opId);
            }

            // If possible, do the HA side export. To do this we must have both
            // HA side initiators, and volumes accessible from the HA side.
            if (haVarray != null && varrayToInitiators.get(haVarray) != null) {
                assembleExportMasksWorkflow(vplex, export, haVarray,
                        varrayToInitiators.get(haVarray),
                        ExportMaskUtils.filterVolumeMap(volumeMap, varrayToVolumes.get(haVarray)),
                        workflow, null, opId);
            }

            // Initiate the workflow.
            StringBuilder buf = new StringBuilder();
            buf.append(String.format(
                    "VPLEX create ExportGroup %s for initiators %s completed successfully", export, initiators.toString()));
            workflow.executePlan(completer, buf.toString());
            _log.info("VPLEX exportGroupCreate workflow scheduled");

        } catch (VPlexApiException vae) {
            _log.error("Exception creating Export Group: " + vae.getMessage(), vae);
            VPlexControllerUtils.failStep(completer, opId, vae, _dbClient);
        } catch (Exception ex) {
            _log.error("Exception creating Export Group: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.CREATE_EXPORT_GROUP.getName();
            ServiceError serviceError = VPlexApiException.errors.exportGroupCreateFailed(opName, ex);
            VPlexControllerUtils.failStep(completer, opId, serviceError, _dbClient);
        }
    }
    
    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.volumecontroller.impl.vplex.VplexController#exportGroupDelete(java.net.URI, java.net.URI,
     * java.lang.String)
     */
    @Override
    public void exportGroupDelete(URI vplex, URI export, String opId)
            throws ControllerException {
        ExportDeleteCompleter completer = null;
        try {
            _log.info("Entering exportGroupDelete");
            WorkflowStepCompleter.stepExecuting(opId);

            completer = new ExportDeleteCompleter(export, opId);
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplex, _dbClient);
            ExportGroup exportGroup = null;
            try {
                exportGroup = getDataObject(ExportGroup.class, export, _dbClient);
            } catch (VPlexApiException ve) {
                // This exception is caught specifically to handle rollback
                // cases. The export group will be marked inactive before this
                // method is called hence it will always throw this exception in
                // rollback scenarios. Hence this exception is caught as storage
                // view will be already deleted due to rollback steps.
                completer.ready(_dbClient);
                return;
            }

            _log.info("Attempting to delete ExportGroup " + exportGroup.getGeneratedName()
                    + " on VPLEX " + vplexSystem.getLabel());
            Workflow workflow = _workflowService.getNewWorkflow(VPlexMaskingWorkflowEntryPoints.getInstance(), "exportGroupDelete", false, opId);

            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, vplex);
            if (exportMasks.isEmpty()) {
                throw VPlexApiException.exceptions.exportGroupDeleteFailedNull(vplex.toString());
            }

            // If none of the export group volumes are contained in any of the
            // export groups mask, simply remove the export masks from the export
            // group. This will cause the export group to be deleted by the
            // completer. This could happen in a rollback scenario where we are rolling
            // back a failed export group creation.
            if (!exportGroupMasksContainExportGroupVolume(exportGroup, exportMasks)) {
                for (ExportMask exportMask : exportMasks) {
                    exportGroup.removeExportMask(exportMask.getId());
                }
                _dbClient.updateObject(exportGroup);
                completer.ready(_dbClient);
                return;
            }

            // Add a steps to remove exports on the VPlex.
            List<URI> exportMaskUris = new ArrayList<URI>();
            List<URI> volumeUris = new ArrayList<URI>();
            String storageViewStepId = null;

            VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

            for (ExportMask exportMask : exportMasks) {
                if (exportMask.getStorageDevice().equals(vplex)) {

                    String vplexClusterName = VPlexUtil.getVplexClusterName(exportMask, vplex, client, _dbClient);
                    VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, exportMask.getMaskName());

                    _log.info("Refreshing ExportMask {}", exportMask.getMaskName());
                    Map<String, String> targetPortToPwwnMap = VPlexControllerUtils.getTargetPortToPwwnMap(client, vplexClusterName);
                    VPlexControllerUtils.refreshExportMask(
                            _dbClient, storageView, exportMask, targetPortToPwwnMap, _networkDeviceController);

                    // assemble a list of other ExportGroups that reference this ExportMask
                    List<ExportGroup> otherExportGroups = ExportUtils.getOtherExportGroups(exportGroup, exportMask, _dbClient);

                    boolean existingVolumes = exportMask.hasAnyExistingVolumes();
                    boolean existingInitiators = exportMask.hasAnyExistingInitiators();

                    boolean removeVolumes = false;
                    List<URI> volumeURIList = new ArrayList<URI>();

                    if (!otherExportGroups.isEmpty()) {
                        if (exportGroup.getVolumes() != null) {
                            for (String volUri : exportGroup.getVolumes().keySet()) {
                                volumeURIList.add(URI.create(volUri));
                            }
                        }
                        volumeURIList = VPlexControllerUtils.getVolumeListDiff(exportGroup, exportMask, otherExportGroups, volumeURIList, _dbClient);

                        if (!volumeURIList.isEmpty()) {
                            removeVolumes = true;
                        }
                    } else if (existingVolumes || existingInitiators) {
                        // It won't make sense to leave the storage view with only exiting volumes
                        // or only existing initiators, so only if there are both existing volumes
                        // and initiators in that case we will delete ViPR created volumes and
                        // initiators.
                        if (existingVolumes) {
                            _log.info("Storage view will not be deleted because Export Mask {} has existing volumes: {}",
                                    exportMask.getMaskName(), exportMask.getExistingVolumes());
                        }
                        if (existingInitiators) {
                            _log.info("Storage view will not be deleted because Export Mask {} has existing initiators: {}",
                                    exportMask.getMaskName(), exportMask.getExistingInitiators());
                        }

                        if (exportMask.getUserAddedVolumes() != null && !exportMask.getUserAddedVolumes().isEmpty()) {
                            StringMap volumes = exportMask.getUserAddedVolumes();
                            if (volumes != null) {
                                for (String vol : volumes.values()) {
                                    URI volumeURI = URI.create(vol);
                                    volumeURIList.add(volumeURI);
                                }
                            }

                            if (!volumeURIList.isEmpty()) {
                                removeVolumes = true;
                            }
                        }

                    } else {
                        _log.info("creating a deleteStorageView workflow step for " + exportMask.getMaskName());
                        String exportMaskDeleteStep = workflow.createStepId();
                        Workflow.Method storageViewExecuteMethod = deleteStorageViewMethod(vplex, exportGroup.getId(), exportMask.getId());
                        storageViewStepId = workflow.createStep(DELETE_STORAGE_VIEW,
                                String.format("Delete VPLEX Storage View %s for ExportGroup %s",
                                        exportMask.getMaskName(), export),
                                storageViewStepId, vplexSystem.getId(), vplexSystem.getSystemType(),
                                this.getClass(), storageViewExecuteMethod, null, exportMaskDeleteStep);
                    }

                    if (removeVolumes) {
                        _log.info("removing volumes: " + volumeURIList);
                        Workflow.Method method = ExportWorkflowEntryPoints.exportRemoveVolumesMethod(vplexSystem.getId(), export,
                                volumeURIList);

                        storageViewStepId = workflow.createStep("removeVolumes",
                                String.format("Removing volumes from export on storage array %s (%s)",
                                        vplexSystem.getNativeGuid(), vplexSystem.getId().toString()),
                                storageViewStepId, NullColumnValueGetter.getNullURI(),
                                vplexSystem.getSystemType(), ExportWorkflowEntryPoints.class, method,
                                null, null);

                    }

                    _log.info("determining which volumes to remove from ExportMask " + exportMask.getMaskName());
                    exportMaskUris.add(exportMask.getId());
                    for (URI volumeUri : ExportMaskUtils.getVolumeURIs(exportMask)) {
                        if (exportGroup.hasBlockObject(volumeUri)) {
                            volumeUris.add(volumeUri);
                            _log.info("   this ExportGroup volume is a match: " + volumeUri);
                        } else {
                            _log.info("   this ExportGroup volume is not in this export mask, so skipping: " + volumeUri);
                        }
                    }
                }
            }

            if (!exportMaskUris.isEmpty()) {
                _log.info("exportGroupDelete export mask URIs: " + exportMaskUris);
                _log.info("exportGroupDelete volume URIs: " + volumeUris);
                String zoningStep = workflow.createStepId();
                List<NetworkZoningParam> zoningParams = NetworkZoningParam.convertExportMasksToNetworkZoningParam(export, exportMaskUris,
                        _dbClient);
                Workflow.Method zoningExecuteMethod = _networkDeviceController.zoneExportMasksDeleteMethod(
                        zoningParams, volumeUris);
                workflow.createStep(ZONING_STEP,
                        String.format("Delete ExportMasks %s for VPlex %s", export, vplex),
                        storageViewStepId, nullURI, "network-system",
                        _networkDeviceController.getClass(), zoningExecuteMethod, null, zoningStep);
            }

            // Start the workflow
            workflow.executePlan(completer, "Successfully deleted ExportMasks for ExportGroup: " + export);
        } catch (Exception ex) {
            _log.error("Exception deleting ExportGroup: " + ex.getMessage());
            String opName = ResourceOperationTypeEnum.DELETE_EXPORT_GROUP.getName();
            ServiceError serviceError = VPlexApiException.errors.exportGroupDeleteFailed(opName, ex);
            VPlexControllerUtils.failStep(completer, opId, serviceError, _dbClient);
        }
    }
    
    /**
     * Checks to see if any of the volumes in the passed export group are in any of the
     * passed export masks.
     *
     * @param exportGroup
     *            A reference to the export group
     * @param exportMasks
     *            A list of export group's export masks.
     *
     * @return true if any volume in the export group is in any o fth epassed masks, false otherwise.
     */
    private boolean exportGroupMasksContainExportGroupVolume(ExportGroup exportGroup, List<ExportMask> exportMasks) {
        boolean maskContainsVolume = false;
        StringMap egVolumes = exportGroup.getVolumes();
        if (egVolumes != null && !egVolumes.isEmpty()) {
            for (ExportMask exportMask : exportMasks) {
                StringMap emVolumes = exportMask.getVolumes();
                if (emVolumes != null && !emVolumes.isEmpty()) {
                    Set<String> emVolumeIds = new HashSet<>();
                    emVolumeIds.addAll(emVolumes.keySet());
                    emVolumeIds.retainAll(egVolumes.keySet());
                    if (!emVolumeIds.isEmpty()) {
                        maskContainsVolume = true;
                        break;
                    }
                }
            }
        }

        return maskContainsVolume;
    }

    
    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.volumecontroller.impl.vplex.VplexController#exportRemoveVolume(java.net.URI, java.net.URI,
     * java.net.URI,
     * java.lang.String)
     */
    @Override
    public void exportGroupRemoveVolumes(URI vplexURI, URI exportURI,
            List<URI> volumeURIs, String opId)
                    throws ControllerException {
        String volListStr = "";
        ExportRemoveVolumeCompleter completer = null;
        boolean hasSteps = false;
        try {
            _log.info("entering export group remove volumes");
            WorkflowStepCompleter.stepExecuting(opId);

            completer = new ExportRemoveVolumeCompleter(exportURI, volumeURIs, opId);
            volListStr = Joiner.on(',').join(volumeURIs);
            Workflow workflow = _workflowService.getNewWorkflow(VPlexMaskingWorkflowEntryPoints.getInstance(), EXPORT_GROUP_REMOVE_VOLUMES, false, opId);
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            ExportGroup exportGroup = getDataObject(ExportGroup.class, exportURI, _dbClient);
            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, vplex.getId());

            VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

            // For safety, run remove volumes serially
            String previousStep = null;

            for (ExportMask exportMask : exportMasks) {
                if (exportMask.getInactive()) {
                    _log.info(String.format("ExportMask %s (%s) is inactive, skipping", 
                            exportMask.getMaskName(), exportMask.getId()));
                    continue;
                }
                String vplexClusterName = VPlexUtil.getVplexClusterName(exportMask, vplexURI, client, _dbClient);
                Map<String, String> targetPortToPwwnMap = VPlexControllerUtils.getTargetPortToPwwnMap(client, vplexClusterName);
                VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, exportMask.getMaskName());
                _log.info("Refreshing ExportMask {}", exportMask.getMaskName());
                VPlexControllerUtils.refreshExportMask(
                        _dbClient, storageView, exportMask, targetPortToPwwnMap, _networkDeviceController);

                List<URI> volumeURIList = new ArrayList<URI>();
                List<URI> remainingVolumesInMask = new ArrayList<URI>();
                if (exportMask.getVolumes() != null && !exportMask.getVolumes().isEmpty()) {
                    // note that this is the assumed behavior even for the
                    // situation in which this export mask is in use by other
                    // export groups... see CTRL-3941
                    // assemble a list of other ExportGroups that reference this ExportMask
                    List<ExportGroup> otherExportGroups = ExportUtils.getOtherExportGroups(exportGroup, exportMask,
                            _dbClient);

                    if (otherExportGroups != null && !otherExportGroups.isEmpty()) {
                        // Gets the list of volume URIs that are not other Export Groups
                        volumeURIList = VPlexControllerUtils.getVolumeListDiff(exportGroup, exportMask, otherExportGroups, volumeURIs, _dbClient);
                    } else {
                        volumeURIList = volumeURIs;
                    }

                    Map<URI, BlockObject> blockObjectMap = VPlexUtil.translateRPSnapshots(_dbClient, volumeURIList);
                    volumeURIList.clear();
                    volumeURIList.addAll(blockObjectMap.keySet());
                    volumeURIList = ExportMaskUtils.filterVolumesByExportMask(volumeURIList, exportMask);

                    for (String volumeURI : exportMask.getVolumes().keySet()) {
                        remainingVolumesInMask.add(URI.create(volumeURI));
                    }
                    remainingVolumesInMask.removeAll(volumeURIList);
                }

                _log.info(String.format("exportGroupRemove: mask %s volumes to process: %s", exportMask.getMaskName(),
                        volumeURIList.toString()));

                // Fetch exportMask again as exportMask zoning Map might have changed in zoneExportRemoveVolumes
                exportMask = _dbClient.queryObject(ExportMask.class, exportMask.getId());

                boolean existingVolumes = exportMask.hasAnyExistingVolumes();
                // The useradded - existing initiators enhancement made in refreshExportMask
                // guarantees that all the discovered initiators are added to userAdded,
                // irrespective of whether it's already registered on the array manually.
                boolean existingInitiators = exportMask.hasAnyExistingInitiators();
                boolean canMaskBeDeleted = remainingVolumesInMask.isEmpty() && !existingInitiators && !existingVolumes;

                if (!canMaskBeDeleted) {
                    _log.info("this mask is not empty, so just updating: "
                            + exportMask.getMaskName());

                    completer.addExportMaskToRemovedVolumeMapping(exportMask.getId(), volumeURIList);
                    
                    Workflow.Method storageViewRemoveVolume = new Workflow.Method(STORAGE_VIEW_REMOVE_VOLUMES_METHOD, vplex.getId(), 
                    		exportGroup.getId(), exportMask, volumeURIList);
                    previousStep = workflow.createStep("removeVolumes",
                            String.format("Removing volumes from export on storage array %s (%s) for export mask %s (%s)",
                                    vplex.getNativeGuid(), vplex.getId().toString(), exportMask.getMaskName(), exportMask.getId()),
                            previousStep, vplex.getId(), vplex.getSystemType(),
                            VPlexMaskingWorkflowEntryPoints.class, storageViewRemoveVolume, rollbackMethodNullMethod(), null);

                    // Add zoning step for removing volumes
                    List<NetworkZoningParam> zoningParam = NetworkZoningParam.convertExportMasksToNetworkZoningParam(
                            exportGroup.getId(), Collections.singletonList(exportMask.getId()), _dbClient);
                    Workflow.Method zoneRemoveVolumesMethod = _networkDeviceController.zoneExportRemoveVolumesMethod(
                            zoningParam, volumeURIList);
                    previousStep = workflow.createStep(null, "Zone remove volumes mask: " + exportMask.getMaskName(),
                            previousStep, nullURI, "network-system", _networkDeviceController.getClass(),
                            zoneRemoveVolumesMethod, _networkDeviceController.zoneNullRollbackMethod(), null);

                    hasSteps = true;

                    /**
                     * TODO
                     * ExportremoveVolumeCompleter should be enhanced to remove export mask from export group if last volume removed.
                     * We already take care of this.
                     */
                } else {
                    _log.info("this mask is empty of ViPR-managed volumes, and there are no existing volumes or initiators, so deleting: "
                            + exportMask.getMaskName());
                    List<NetworkZoningParam> zoningParams = NetworkZoningParam.convertExportMasksToNetworkZoningParam(
                            exportURI, Collections.singletonList(exportMask.getId()), _dbClient);
                    hasSteps = true;

                    String exportMaskDeleteStep = workflow.createStepId();
                    Workflow.Method deleteStorageView = deleteStorageViewMethod(vplexURI, exportURI, exportMask.getId());
                    previousStep = workflow.createStep(DELETE_STORAGE_VIEW,
                            String.format("Deleting storage view: %s (%s)", exportMask.getMaskName(), exportMask.getId()),
                            previousStep, vplexURI, vplex.getSystemType(), this.getClass(), deleteStorageView, null, exportMaskDeleteStep);

                    // Unzone step (likely just a FCZoneReference removal since this is remove volume only)
                    previousStep = workflow.createStep(
                            ZONING_STEP,
                            "Zoning subtask for export-group: " + exportURI,
                            previousStep, NullColumnValueGetter.getNullURI(),
                            "network-system", _networkDeviceController.getClass(),
                            _networkDeviceController.zoneExportRemoveVolumesMethod(
                                    zoningParams, volumeURIs),
                            null, workflow.createStepId());
                }
            }

            if (hasSteps) {
                workflow.executePlan(completer,
                        String.format("Sucessfully removed volumes or deleted Storage View: %s (%s)", exportGroup.getLabel(),
                                exportGroup.getId()));
            } else {
                completer.ready(_dbClient);
            }
        } catch (VPlexApiException vae) {
            String message = String.format("Failed to remove Volume %s from ExportGroup %s",
                    volListStr, exportURI);
            _log.error(message, vae);
            VPlexControllerUtils.failStep(completer, opId, vae, _dbClient);
        } catch (Exception ex) {
            String message = String.format("Failed to remove Volume %s from ExportGroup %s",
                    volListStr, exportURI);
            _log.error(message, ex);
            String opName = ResourceOperationTypeEnum.DELETE_EXPORT_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.exportGroupRemoveVolumesFailed(
                    volListStr, exportURI.toString(), opName, ex);
            VPlexControllerUtils.failStep(completer, opId, serviceError, _dbClient);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.volumecontroller.impl.vplex.VplexController#exportAddInitiator(java.net.URI, java.net.URI,
     * java.net.URI,
     * java.lang.String)
     */
    @Override
    public void exportGroupAddInitiators(URI vplexURI, URI exportURI,
            List<URI> initiatorURIs, String opId)
                    throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);

            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            ExportGroup exportGroup = getDataObject(ExportGroup.class, exportURI, _dbClient);
            ExportAddInitiatorCompleter completer = new ExportAddInitiatorCompleter(exportURI, initiatorURIs, opId);
            Workflow workflow = _workflowService.getNewWorkflow(VPlexMaskingWorkflowEntryPoints.getInstance(), "exportAddInitiator", true, opId);
            boolean isRecoverPointExport = ExportUtils.checkIfInitiatorsForRP(_dbClient,
                    exportGroup.getInitiators());

            initiatorURIs = VPlexUtil.filterInitiatorsForVplex(_dbClient, initiatorURIs);

            // get a map of host URI to a list of Initiators in that Host
            Map<URI, List<Initiator>> hostInitiatorsMap = VPlexUtil.makeHostInitiatorsMap(initiatorURIs, _dbClient);

            // Get the varrays involved.
            List<URI> varrayList = new ArrayList<URI>();
            varrayList.add(exportGroup.getVirtualArray());
            // If not exporting to recover point initiators, export to the altVirtualArray
            // if already present, or create an altVirtualArray if there are distributed volumes.
            if (!isRecoverPointExport) {
                if ((exportGroup.getAltVirtualArrays() != null)
                        && (exportGroup.getAltVirtualArrays().get(vplexURI.toString()) != null)) {
                    // If there is an alternate Varray entry for this Vplex, it indicates we have HA volumes.
                    varrayList.add(URI.create(exportGroup.getAltVirtualArrays().get(vplexURI.toString())));
                } else {
                    // Check to see if there are distributed volumes. If so, maybe we
                    // could export to both varrays.
                    Map<URI, Integer> volumeMap = ExportUtils.getExportGroupVolumeMap(_dbClient, vplex, exportGroup);
                    Map<URI, Set<URI>> varrayToVolumes = VPlexUtil.mapBlockObjectsToVarrays(
                            _dbClient, volumeMap.keySet(), vplexURI, exportGroup);
                    // Remove the local volumes
                    varrayToVolumes.remove(exportGroup.getVirtualArray());
                    if (!varrayToVolumes.isEmpty()) {
                        URI haVarray = VPlexUtil.pickHAVarray(varrayToVolumes);
                        exportGroup.putAltVirtualArray(vplex.toString(), haVarray.toString());
                        _dbClient.updateObject(exportGroup);
                    }
                }
            }

            // Process each host separately.
            String previousStep = null;
            for (URI hostURI : hostInitiatorsMap.keySet()) {
                List<URI> initURIs = new ArrayList<URI>();
                for (Initiator initiator : hostInitiatorsMap.get(hostURI)) {
                    initURIs.add(initiator.getId());
                }
                // Partition the Initiators by Varray. We may need to do two different ExportMasks,
                // one for each of the varrays.
                Map<URI, List<URI>> varraysToInitiators = VPlexUtil.partitionInitiatorsByVarray(
                        _dbClient, initURIs, varrayList, vplex);

                if (varraysToInitiators.isEmpty()) {
                    throw VPlexApiException.exceptions
                            .exportCreateNoinitiatorsHaveCorrectConnectivity(
                                    exportGroup.getInitiators().toString(), varrayList.toString());
                }

                // Now process the Initiators for a host and a given varray.
                for (URI varrayURI : varraysToInitiators.keySet()) {
                    initURIs = varraysToInitiators.get(varrayURI);
                    List<Initiator> initiators = new ArrayList<Initiator>();
                    for (Initiator initiator : hostInitiatorsMap.get(hostURI)) {
                        if (initURIs.contains(initiator.getId())) {
                            initiators.add(initiator);
                        }
                    }
                    if (!initiators.isEmpty()) {
                        previousStep = addStepsForAddInitiators(
                                workflow, vplex, exportGroup, varrayURI,
                                initURIs, initiators, hostURI, previousStep, opId);
                    }
                }
            }

            // Fire off the Workflow
            String message = String.format("Successfully added initiators %s to ExportGroup %s",
                    initiatorURIs.toString(), exportGroup.getLabel());
            workflow.executePlan(completer, "Successfully added initiators: " + message);
        } catch (VPlexApiException vae) {
            _log.error("Exception adding initiators to Storage View: " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(opId, vae);
        } catch (Exception ex) {
            _log.error("Exception adding initiators to Storage View: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.ADD_EXPORT_INITIATOR.getName();
            ServiceError serviceError = VPlexApiException.errors.exportGroupAddInitiatorsFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }
    
    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.volumecontroller.impl.vplex.VplexController#exportRemoveInitiator(java.net.URI,
     * java.net.URI, java.net.URI,
     * java.lang.String)
     */
    @Override
    public void exportGroupRemoveInitiators(URI vplexURI, URI exportURI,
            List<URI> initiatorURIs,
            String opId) throws ControllerException {
        try {
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            ExportGroup exportGroup = getDataObject(ExportGroup.class, exportURI, _dbClient);
            ExportRemoveInitiatorCompleter completer = new ExportRemoveInitiatorCompleter(exportURI, initiatorURIs, opId);
            Workflow workflow = _workflowService.getNewWorkflow(VPlexMaskingWorkflowEntryPoints.getInstance(), "exportRemoveInitiator", true, opId);
            boolean hasStep = false; // true if Workflow has a Step

            _log.info("starting remove initiators for export group: " + exportGroup.toString());
            _log.info("request is to remove these initiators: " + initiatorURIs);

            initiatorURIs = VPlexUtil.filterInitiatorsForVplex(_dbClient, initiatorURIs);

            // get a map of host URI to a list of Initiators in that Host
            Map<URI, List<Initiator>> hostInitiatorsMap = VPlexUtil.makeHostInitiatorsMap(initiatorURIs, _dbClient);

            VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

            // Loop, processing each host separately.
            for (URI hostURI : hostInitiatorsMap.keySet()) {
                _log.info("setting up initiator removal for host " + hostURI);

                // Get the initiators (and initiator URIs) for this host
                List<Initiator> initiators = hostInitiatorsMap.get(hostURI);

                // Find the ExportMask for my host.
                List<ExportMask> exportMasks = VPlexControllerUtils.getExportMaskForHost(exportGroup, hostURI, vplexURI, _dbClient);
                if (exportMasks == null || exportMasks.isEmpty()) {
                    // If there is no ExportMask for this host, there is nothing to do.
                    _log.info("No export mask found for hostURI: " + hostURI);
                    continue;
                }

                String lastStep = null;

                List<URI> initiatorsAlreadyRemovedFromExportGroup = new ArrayList<URI>();
                for (ExportMask exportMask : exportMasks) {

                    _log.info("adding remove initiators steps for "
                            + "export mask / storage view: " + exportMask.getMaskName());

                    String vplexClusterName = VPlexUtil.getVplexClusterName(exportMask, vplexURI, client, _dbClient);
                    Map<String, String> targetPortToPwwnMap = VPlexControllerUtils.getTargetPortToPwwnMap(client, vplexClusterName);
                    VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, exportMask.getMaskName());
                    _log.info("Refreshing ExportMask {}", exportMask.getMaskName());
                    VPlexControllerUtils.refreshExportMask(
                            _dbClient, storageView, exportMask, targetPortToPwwnMap, _networkDeviceController);

                    // filter out any of the host's initiators that are not
                    // contained within this ExportMask (CTRL-12300)
                    List<Initiator> initsToRemove = new ArrayList<Initiator>();
                    for (Initiator init : initiators) {
                        if (exportMask.hasInitiator(init.getId().toString())) {
                            initsToRemove.add(init);
                        }
                    }

                    lastStep = addStepsForRemoveInitiators(
                            vplex, workflow, completer, exportGroup, exportMask, initsToRemove,
                            hostURI, initiatorsAlreadyRemovedFromExportGroup, lastStep);
                    if (lastStep != null) {
                        hasStep = true;
                    }
                }
            }

            // Fire off the workflow if there were initiators to delete. Otherwise just fire completer.
            if (hasStep) {
                workflow.executePlan(completer, "Successfully removed initiators: " + initiatorURIs.toString());
            } else {
                _log.info(String.format("No updates to ExportMasks needed... complete"));
                completer.ready(_dbClient);
            }
        } catch (VPlexApiException vae) {
            _log.error("Exception in exportRemoveInitiators: " + initiatorURIs.toString(), vae);
            WorkflowStepCompleter.stepFailed(opId, vae);
        } catch (Exception ex) {
            _log.error("Exception in exportRemoveInitiators: " + initiatorURIs.toString(), ex);
            String opName = ResourceOperationTypeEnum.DELETE_EXPORT_INITIATOR.getName();
            ServiceError serviceError = VPlexApiException.errors.exportGroupRemoveInitiatorsFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }
    
    @Override
    public void exportGroupUpdate(URI storageURI, URI exportGroupURI,
            Workflow storageWorkflow, String token) throws Exception {
        TaskCompleter taskCompleter = null;
        try {
            _log.info(String.format("exportGroupUpdate start - Array: %s ExportGroup: %s",
                    storageURI.toString(), exportGroupURI.toString()));
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
            String successMessage = String.format(
                    "ExportGroup %s successfully updated for StorageArray %s",
                    exportGroup.getLabel(), storage.getLabel());
            storageWorkflow.setService(_workflowService);
            storageWorkflow.executePlan(taskCompleter, successMessage);
        } catch (Exception ex) {
            _log.error("ExportGroupUpdate Orchestration failed.", ex);
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
                VPlexControllerUtils.failStep(taskCompleter, token, serviceError, _dbClient);
            } else {
                throw DeviceControllerException.exceptions.exportGroupUpdateFailed(ex);
            }
        }
    }
    
	@Override
	public void exportGroupAddVolumes(URI vplexURI, URI exportURI, Map<URI, Integer> volumeMap, String opId)
			throws Exception {
		String volListStr = "";
        ExportAddVolumeCompleter completer = null;

        try {
            WorkflowStepCompleter.stepExecuting(opId);

            completer = new ExportAddVolumeCompleter(exportURI, volumeMap, opId);
            volListStr = Joiner.on(',').join(volumeMap.keySet());
            Workflow workflow = _workflowService.getNewWorkflow(VPlexMaskingWorkflowEntryPoints.getInstance(), "exportGroupAddVolumes", true, opId);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportURI);
            StorageSystem vplexSystem = _dbClient.queryObject(StorageSystem.class, vplexURI);
            URI srcVarray = exportGroup.getVirtualArray();
            boolean isRecoverPointExport = ExportUtils.checkIfInitiatorsForRP(_dbClient,
                    exportGroup.getInitiators());

            if (!exportGroup.hasInitiators()) {
                // VPLEX API restricts adding volumes before initiators
                VPlexApiException.exceptions
                        .cannotAddVolumesToExportGroupWithoutInitiators(exportGroup.forDisplay());
            }

            // Determine whether this export will be done across both VPLEX clusters,
            // or just the src or ha varray.
            // We get a map of varray to the volumes that can be exported in each varray.
            Map<URI, Set<URI>> varrayToVolumes = VPlexUtil.mapBlockObjectsToVarrays(_dbClient, volumeMap.keySet(),
                    vplexURI, exportGroup);
            // Put the srcVolumes in their own set, and remove the src varray from the varrayToVolumes map.
            Set<URI> srcVolumes = varrayToVolumes.get(srcVarray);
            varrayToVolumes.remove(srcVarray);
            URI haVarray = null;
            // If any of the volumes specify an HA varray,
            // and we're not exporting to recover point initiators
            if (!varrayToVolumes.isEmpty() && !isRecoverPointExport) {
                if (exportGroup.hasAltVirtualArray(vplexURI.toString())) {
                    // If we've already chosen an haVarray, go with with the choice
                    haVarray = URI.create(exportGroup.getAltVirtualArrays().get(vplexURI.toString()));
                } else {
                    // Otherwise we will make sure there is only one HA varray and persist it.
                    haVarray = VPlexUtil.pickHAVarray(varrayToVolumes);
                }
            }

            // Partition the initiators by varray into varrayToInitiators map.
            List<URI> varrayURIs = new ArrayList<URI>();
            varrayURIs.add(exportGroup.getVirtualArray());
            if (haVarray != null) {
                varrayURIs.add(haVarray);
            }
            List<URI> exportGroupInitiatorList = StringSetUtil.stringSetToUriList(exportGroup.getInitiators());
            Map<URI, List<URI>> varrayToInitiators = VPlexUtil.partitionInitiatorsByVarray(
                    _dbClient, 
                    exportGroupInitiatorList,
                    varrayURIs, vplexSystem);

            if (varrayToInitiators.isEmpty()) {
                throw VPlexApiException.exceptions
                        .exportCreateNoinitiatorsHaveCorrectConnectivity(
                                exportGroup.getInitiators().toString(), varrayURIs.toString());
            }

            findAndUpdateFreeHLUsForClusterExport(vplexSystem, exportGroup, exportGroupInitiatorList, volumeMap);

            // Add all the volumes to the SRC varray if there are src side volumes and
            // initiators that have connectivity to the source side.
            String srcExportStepId = null;
            if (varrayToInitiators.get(srcVarray) != null && srcVolumes != null) {
                srcExportStepId = assembleExportMasksWorkflow(vplexURI, exportURI, srcVarray,
                        varrayToInitiators.get(srcVarray),
                        ExportMaskUtils.filterVolumeMap(volumeMap, srcVolumes),
                        workflow, null, opId);
            }

            // IF the haVarray has been set, and we have initiators with connectivity to the ha varray,
            // and there are volumes to be added to the ha varray, do so.
            if (haVarray != null && varrayToInitiators.get(haVarray) != null
                    && varrayToVolumes.get(haVarray) != null) {
                exportGroup.putAltVirtualArray(vplexURI.toString(), haVarray.toString());
                _dbClient.updateObject(exportGroup);
                assembleExportMasksWorkflow(vplexURI, exportURI, haVarray,
                        varrayToInitiators.get(haVarray),
                        ExportMaskUtils.filterVolumeMap(volumeMap, varrayToVolumes.get(haVarray)),
                        workflow, srcExportStepId, opId);
            }

            // Initiate the workflow.
            String message = String
                    .format("VPLEX ExportGroup Add Volumes (%s) for export %s completed successfully", volListStr, exportURI);
            workflow.executePlan(completer, message);

        } catch (VPlexApiException vae) {
            String message = String.format("Failed to add Volumes %s to ExportGroup %s",
                    volListStr, exportURI);
            _log.error(message, vae);
            VPlexControllerUtils.failStep(completer, opId, vae, _dbClient);
        } catch (Exception ex) {
            String message = String.format("Failed to add Volumes %s to ExportGroup %s",
                    volListStr, exportURI);
            _log.error(message, ex);
            String opName = ResourceOperationTypeEnum.ADD_EXPORT_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.exportGroupAddVolumesFailed(
                    volListStr, exportURI.toString(), opName, ex);
            VPlexControllerUtils.failStep(completer, opId, serviceError, _dbClient);
        }

	}
	
	@Override
    public void increaseMaxPaths(Workflow workflow, StorageSystem vplex,
            ExportGroup exportGroup, ExportMask exportMask, List<URI> newInitiators,
            String token) throws Exception {

        // Allocate any new ports that are required for the initiators
        // and update the zoning map in the exportMask.
        List<Initiator> initiators = _dbClient.queryObject(Initiator.class, newInitiators);
        Collection<URI> volumeURIs = (Collections2.transform(exportMask.getVolumes().keySet(),
                CommonTransformerFunctions.FCTN_STRING_TO_URI));
        ExportPathParams pathParams = _blockScheduler.calculateExportPathParamForVolumes(
                volumeURIs, exportGroup.getNumPaths(), vplex.getId(), exportGroup.getId());
        if (exportGroup.getType() != null) {
            pathParams.setExportGroupType(exportGroup.getType());
        }
        // Determine the Varray for the targets. Default to ExportGroup.virtualArray
        URI varrayURI = exportGroup.getVirtualArray();
        if (exportGroup.hasAltVirtualArray(vplex.getId().toString())) {
            URI altVarrayURI = URI.create(exportGroup.getAltVirtualArrays().get(vplex.getId().toString()));
            if (ExportMaskUtils.exportMaskInVarray(_dbClient, exportMask, altVarrayURI)) {
                // If the targets match the alternate varray, use that instead.
                varrayURI = altVarrayURI;
            }
        }

        // Assign additional storage port(s).
        Map<URI, List<URI>> assignments = _blockScheduler.assignStoragePorts(vplex, exportGroup,
                initiators, exportMask.getZoningMap(), pathParams, volumeURIs, _networkDeviceController, varrayURI, token);
        List<URI> newTargets = BlockStorageScheduler.getTargetURIsFromAssignments(assignments);
        exportMask.addZoningMap(BlockStorageScheduler.getZoneMapFromAssignments(assignments));
        _dbClient.updateObject(exportMask);

        if (newTargets.isEmpty() == false) {
            // Only include initiators that were assigned ports in the Storage View.
            // If we include any inititators that are not assigned and zoned to ports,
            // creation or update of the Storage View will fail because we won't be
            // able to register those initiators.
            List<URI> storageViewInitiators = newInitiators;

            // Create a Step to add the SAN Zone
            String zoningStepId = workflow.createStepId();
            Workflow.Method zoningMethod = zoneAddInitiatorStepMethod(
                    vplex.getId(), exportGroup.getId(), newInitiators, varrayURI);
            Workflow.Method zoningRollbackMethod = zoneRollbackMethod(exportGroup.getId(),
                    zoningStepId);
            zoningStepId = workflow.createStep(ZONING_STEP,
                    String.format("Zone initiator %s to ExportGroup %s(%s)",
                            null, exportGroup.getLabel(), exportGroup.getId()),
                    null, vplex.getId(), vplex.getSystemType(),
                    this.getClass(), zoningMethod, zoningRollbackMethod, zoningStepId);

            // Create a Step to add the initiator to the Storage View
            String message = String.format("adding initiators %s to StorageView %s", storageViewInitiators.toString(),
                    exportGroup.getGeneratedName());
            ExportMask sharedExportMask = VPlexUtil
                    .getSharedExportMaskInDb(exportGroup, vplex.getId(), _dbClient, varrayURI, null, null);
            boolean shared = false;
            if (null != sharedExportMask && sharedExportMask.getId().equals(exportMask.getId())) {
                shared = true;
            }

            String addInitStep = workflow.createStepId();
            ExportMaskAddInitiatorCompleter completer = new ExportMaskAddInitiatorCompleter(
                    exportGroup.getId(), exportMask.getId(), storageViewInitiators, newTargets, addInitStep);

            Workflow.Method addToViewMethod = new Workflow.Method(STORAGE_VIEW_ADD_INITIATORS_METHOD, vplex.getId(), 
            		exportGroup.getId(), exportMask.getId(), storageViewInitiators, newTargets, shared, completer);
            Workflow.Method addToViewRollbackMethod = new Workflow.Method(ROLLBACK_METHOD_NULL);
            workflow.createStep("storageView", "Add " + message,
                    ZONING_STEP, vplex.getId(), vplex.getSystemType(), VPlexMaskingWorkflowEntryPoints.class,
                    addToViewMethod, addToViewRollbackMethod, addInitStep);
        }
    }
	
	@Override
    public void portRebalance(URI vplex, URI exportGroupURI, URI varray, URI exportMaskURI, Map<URI, List<URI>> adjustedPaths,
            Map<URI, List<URI>> removedPaths, boolean isAdd, String stepId) throws Exception
    {
        // Retrieve the ExportGroup and ExportMask and validate
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
        ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
        if (exportGroup == null || exportMask == null || exportGroup.getInactive() || exportMask.getInactive() || 
        		!exportGroup.hasMask(exportMaskURI)) {
        	String reason = String.format("Bad exportGroup %s or exportMask %s", exportGroupURI, exportMaskURI);
        	_log.error(reason);
        	ServiceCoded coded = WorkflowException.exceptions.workflowConstructionError(reason);
        	WorkflowStepCompleter.stepFailed(stepId, coded);
        	return;
        }

        // Check if the ExportMask is in the desired varray (in cross-coupled ExportGroups)
        if (!ExportMaskUtils.exportMaskInVarray(_dbClient, exportMask, varray)) {
        	_log.info(String.format("ExportMask %s (%s) not in specified varray %s", exportMask.getMaskName(), exportMask.getId(), varray));
        	WorkflowStepCompleter.stepSucceeded(stepId, String.format("No operation done: Mask not in specified varray %s", varray));
        	return;
        }

        // Refresh the ExportMask so we have the latest data.
        StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplex, _dbClient);
        VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
        String vplexClusterName = VPlexUtil.getVplexClusterName(exportMask, vplex, client, _dbClient);
        VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, exportMask.getMaskName());
        _log.info("Processing and Refreshing ExportMask {}", exportMask.getMaskName());
        Map<String, String> targetPortToPwwnMap = VPlexControllerUtils.getTargetPortToPwwnMap(client, vplexClusterName);
        VPlexControllerUtils.refreshExportMask(
                _dbClient, storageView, exportMask, targetPortToPwwnMap, _networkDeviceController);
        
        // Determine hosts in ExportMask
        Set<URI> hostsInExportMask = new HashSet<URI>();
        Set<String> hostNames = ExportMaskUtils.getHostNamesInMask(exportMask, _dbClient);
        Set<Initiator> initiatorsInMask = ExportMaskUtils.getInitiatorsForExportMask(_dbClient, exportMask, null);
        for (Initiator initiator : initiatorsInMask) {
            if (initiator.getHost() != null) {
               hostsInExportMask.add(initiator.getHost());
            }
        }
        boolean sharedMask = (hostsInExportMask.size() > 1);
        
        if (isAdd) {
            // Processing added paths only
            Workflow workflow = _workflowService.getNewWorkflow(VPlexMaskingWorkflowEntryPoints.getInstance(), "portRebalance", false, stepId);

            // Determine initiators and targets to be added. 
            // These are initiators not in mask that are in a host in the mask.
            // Earlier versions of the Vplex code may not have had all the initiators in the mask, as
            // earlier code only put initiators in the Storage View for which ports were added.
            // Targets to be added may be on existing initiators or newly added initiators.
            List<URI> initiatorsToAdd = new ArrayList<URI>();
            List<URI> targetsToAdd = new ArrayList<URI>();
            for (URI initiatorURI : adjustedPaths.keySet()) {
                if (!exportMask.hasInitiator(initiatorURI.toString())) {
                    // Initiator not in ExportMask
                    Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);
                    if (initiator != null && !initiator.getInactive()) {
                        if (hostsInExportMask.contains(initiator.getHost())) {
                            initiatorsToAdd.add(initiatorURI);
                            for (URI targetURI : adjustedPaths.get(initiatorURI)) {
                                if (!exportMask.hasTargets(Arrays.asList(targetURI)) && 
                                        !targetsToAdd.contains(targetURI)) {
                                    targetsToAdd.add(targetURI);
                                }
                            }
                        }
                    }
                } else {
                    // Initiator already in ExportMask, look for additional targets
                    for (URI targetURI : adjustedPaths.get(initiatorURI)) {
                        if (!exportMask.hasTargets(Arrays.asList(targetURI)) && 
                                !targetsToAdd.contains(targetURI)) {
                            targetsToAdd.add(targetURI);
                        }
                    }

                }
            }
            _log.info("Targets to add: " + targetsToAdd.toString());
            _log.info("Initiators to add: " + initiatorsToAdd.toString());
            
            // Invoke either storageViewAddInitiators if there are initiators to be added (it will add targets also),
            // or storageViewAddStoragePorts if no initiators to be added (which adds only targets).
            Workflow.Method addPathsMethod = null;
            if (!initiatorsToAdd.isEmpty() || !targetsToAdd.isEmpty()) {
                String addInitiatorStepId = workflow.createStepId();
                ExportMaskAddInitiatorCompleter completer = new ExportMaskAddInitiatorCompleter(
                        exportGroupURI, exportMaskURI, initiatorsToAdd, targetsToAdd, addInitiatorStepId);;
                if (!initiatorsToAdd.isEmpty()) {
                    addPathsMethod = new Workflow.Method(STORAGE_VIEW_ADD_INITIATORS_METHOD, vplex, 
                    		exportGroup.getId(), exportMask.getId(), initiatorsToAdd, targetsToAdd, sharedMask, completer);
                } else if (!targetsToAdd.isEmpty()) {
                    addPathsMethod = new Workflow.Method(STORAGE_VIEW_ADD_STORAGE_PORTS_METHOD, vplex, exportGroupURI, 
                    		exportMaskURI, targetsToAdd);
                }
                String description = String.format("Adding paths to ExportMask %s Hosts %s", exportMask.getMaskName(), hostNames.toString());
                workflow.createStep("addPaths", description, null, vplex, vplexSystem.getSystemType(), 
                		VPlexMaskingWorkflowEntryPoints.class, addPathsMethod, null, false, addInitiatorStepId);
                ExportMaskAddPathsCompleter workflowCompleter = new ExportMaskAddPathsCompleter(exportGroupURI, exportMaskURI, stepId);
                workflow.executePlan(workflowCompleter, description + " completed successfully");
                return;
            }
            
        } else {
            // Processing the paths to be removed only, Paths not in the removedPaths map will be retained.
            // Note that we only remove ports (targets), never initiators.
            Workflow workflow = _workflowService.getNewWorkflow(VPlexMaskingWorkflowEntryPoints.getInstance(), "portRebalance", false, stepId);
            
            // Compute the targets to be removed.
            Set<URI> targetsToBeRemoved = ExportMaskUtils.getAllPortsInZoneMap(removedPaths);
            Collection<URI> targetsInMask = Collections2.transform(exportMask.getStoragePorts(), 
                    CommonTransformerFunctions.FCTN_STRING_TO_URI);
            targetsToBeRemoved.retainAll(targetsInMask);
            Set<URI> targetsToBeRetained = ExportMaskUtils.getAllPortsInZoneMap(adjustedPaths);
            targetsToBeRemoved.removeAll(targetsToBeRetained);
            List<URI> portsToBeRemoved = new ArrayList<URI>(targetsToBeRemoved);
            _log.info("Targets to be removed: " + portsToBeRemoved.toString());

            // Call storageViewRemoveStoragePorts to remove any necessary targets.
            Workflow.Method removePathsMethod = null;
            if (!portsToBeRemoved.isEmpty()) {
                String removeInitiatorStepId = workflow.createStepId();
                removePathsMethod = new Workflow.Method(STORAGE_VIEW_REMOVE_STORAGE_PORTS_METHOD, vplex, exportGroupURI, 
                		exportMaskURI, portsToBeRemoved);
                String description = String.format("Removing paths to ExportMask %s Hosts %s", 
                		exportMask.getMaskName(), hostNames.toString());
                workflow.createStep("removePaths", description, null, vplex, vplexSystem.getSystemType(), 
                		VPlexMaskingWorkflowEntryPoints.class, removePathsMethod, null, false, removeInitiatorStepId);
                ExportMaskRemovePathsCompleter workflowCompleter = new ExportMaskRemovePathsCompleter(exportGroupURI, exportMaskURI, stepId);
                workflowCompleter.setRemovedStoragePorts(portsToBeRemoved);
                workflow.executePlan(workflowCompleter, description + " completed successfully");
                return;
            }
        } 

        // Apparently nothing to do, return success
        WorkflowStepCompleter.stepSucceeded(stepId, "No operation performed on VPLEX mask");
    }
	
	/**
     * Method to assemble the find or create VPLEX storage views (export masks) workflow for the given
     * ExportGroup, Initiators, and the block object Map.
     *
     * @param vplexURI
     *            the URI of the VPLEX StorageSystem object
     * @param export
     *            the ExportGroup in question
     * @param varrayUri
     *            -- NOTE! The varrayURI may NOT be the same as the exportGroup varray!.
     * @param initiators
     *            if initiators is null, the method will use all initiators from the ExportGroup
     * @param blockObjectMap
     *            the key (URI) of this map can reference either the volume itself or a snapshot.
     * @param workflow
     *            the controller workflow
     * @param waitFor
     *            -- If non-null, will wait on previous workflow step
     * @param opId
     *            the workflow step id
     * @return the last Workflow Step id
     * @throws Exception
     */
    private String assembleExportMasksWorkflow(URI vplexURI, URI export, URI varrayUri, List<URI> initiators,
            Map<URI, Integer> blockObjectMap, Workflow workflow, String waitFor, String opId) throws Exception {

        long startAssembly = new Date().getTime();

        VPlexControllerUtils.cleanStaleExportMasks(_dbClient, vplexURI);
        _log.info("TIMER: clean stale export masks took {} ms", new Date().getTime() - startAssembly);

        StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
        ExportGroup exportGroup = getDataObject(ExportGroup.class, export, _dbClient);

        // filter volume map just for this VPLEX's virtual volumes
        _log.info("object map before filtering for this VPLEX: " + blockObjectMap);
        Map<URI, Integer> filteredBlockObjectMap = new HashMap<URI, Integer>();
        for (URI boURI : blockObjectMap.keySet()) {
            BlockObject bo = Volume.fetchExportMaskBlockObject(_dbClient, boURI);
            if (bo.getStorageController().equals(vplexURI)) {
                filteredBlockObjectMap.put(bo.getId(), blockObjectMap.get(boURI));
            }
        }

        blockObjectMap = filteredBlockObjectMap;
        _log.info("object map after filtering for this VPLEX: " + blockObjectMap);

        // validate volume to lun map to make sure there aren't any duplicate LUN entries
        ExportUtils.validateExportGroupVolumeMap(exportGroup.getLabel(), blockObjectMap);

        VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplexSystem, _dbClient, coordinator);

        // we will need lists of new export masks to create and existing export masks to simply update
        List<ExportMask> exportMasksToCreateOnDevice = new ArrayList<ExportMask>();
        List<ExportMask> exportMasksToUpdateOnDevice = new ArrayList<ExportMask>();
        // In case we are just updating the existing export masks we will need this map
        // to add initiators which are not already there.
        Map<URI, List<Initiator>> exportMasksToUpdateOnDeviceWithInitiators = new HashMap<URI, List<Initiator>>();
        // In case we are just updating the existing export masks we will need this map
        // to add storage ports which are not already there.
        Map<URI, List<URI>> exportMasksToUpdateOnDeviceWithStoragePorts = new HashMap<URI, List<URI>>();

        // try to collect initiators, if none supplied by caller
        if (initiators == null) {
            initiators = new ArrayList<URI>();
            if (exportGroup.hasInitiators()) {
                for (String initiator : exportGroup.getInitiators()) {
                    initiators.add(URI.create(initiator));
                }
            }
        }

        // sort initiators in a host to initiator map
        Map<URI, List<Initiator>> hostInitiatorMap = VPlexUtil.makeHostInitiatorsMap(initiators, _dbClient);

        // This Set will be used to track shared export mask in database.
        Set<ExportMask> sharedExportMasks = new HashSet<ExportMask>();

        // look at each host
        String lockName = null;
        boolean lockAcquired = false;
        String vplexClusterId = ConnectivityUtil.getVplexClusterForVarray(varrayUri, vplexURI, _dbClient);
        String vplexClusterName = VPlexUtil.getVplexClusterName(varrayUri, vplexURI, client, _dbClient);
        try {
            lockName = _vplexApiLockManager.getLockName(vplexURI, vplexClusterId);
            lockAcquired = _vplexApiLockManager.acquireLock(lockName, LockTimeoutValue.get(LockType.VPLEX_API_LIB));
            if (!lockAcquired) {
                throw VPlexApiException.exceptions.couldNotObtainConcurrencyLock(vplexSystem.getLabel());
            }

            Map<String, String> targetPortToPwwnMap = VPlexControllerUtils.getTargetPortToPwwnMap(client, vplexClusterName);
            Boolean[] doInitiatorRefresh =  new Boolean[] { new Boolean(true) };
            
            /**
             * COP-28674: During Vblock boot volume export, if existing storage views are found then check for existing volumes
             * If found throw exception. This condition is valid only for boot volume vblock export.
             */
            if (exportGroup.forHost() && ExportMaskUtils.isVblockHost(initiators, _dbClient) && ExportMaskUtils.isBootVolume(_dbClient, blockObjectMap)) {
                _log.info("VBlock boot volume Export: Validating the Vplex Cluster {} to find existing storage views", vplexClusterName);
                List<Initiator> initiatorList = _dbClient.queryObject(Initiator.class, initiators);
                
                List<String> initiatorNames = VPlexControllerUtils.getInitiatorNames(vplexSystem.getSerialNumber(), vplexClusterName, client,
                        initiatorList.iterator(), doInitiatorRefresh, _dbClient);
                
                List<VPlexStorageViewInfo> storageViewInfos = client.getStorageViewsContainingInitiators(vplexClusterName,
                        initiatorNames);
                List<String> maskNames = new ArrayList<String>();
                if(!CollectionUtils.isEmpty(storageViewInfos)) {
                    for (VPlexStorageViewInfo storageView : storageViewInfos) {
                        if(!CollectionUtils.isEmpty(storageView.getVirtualVolumes())) {
                            maskNames.add(storageView.getName());
                        } else {
                            _log.info("Found Storage View {} for cluster {} with empty volumes",storageView.getName(),
                                    vplexClusterName);
                        }
                    }
                } else {
                    _log.info("No Storage views found for cluster {}", vplexClusterName);
                }
                
                if (!CollectionUtils.isEmpty(maskNames)) {
                    Set<URI> computeResourceSet = hostInitiatorMap.keySet();
                    throw VPlexApiException.exceptions.existingMaskFoundDuringBootVolumeExport(Joiner.on(", ").join(maskNames),
                            Joiner.on(", ").join(computeResourceSet), vplexClusterName);
                } else {
                    _log.info("VBlock Boot volume Export : Validation passed");
                }
            }

            for (URI hostUri : hostInitiatorMap.keySet()) {
                _log.info("assembling export masks workflow, now looking at host URI: " + hostUri);

                List<Initiator> inits = hostInitiatorMap.get(hostUri);
                _log.info("this host contains these initiators: " + inits);

                boolean foundMatchingStorageView = false;
                boolean allPortsFromMaskMatchForVarray = true;

                _log.info("attempting to locate an existing ExportMask for this host's initiators on VPLEX Cluster " + vplexClusterId);
                Map<URI, ExportMask> vplexExportMasks = new HashMap<URI, ExportMask>();
                allPortsFromMaskMatchForVarray = filterExportMasks(vplexExportMasks, inits, varrayUri, vplexSystem, vplexClusterId);
                ExportMask sharedVplexExportMask = null;
                switch (vplexExportMasks.size()) {
                    case FOUND_NO_VIPR_EXPORT_MASKS:
                        // Didn't find any single existing ExportMask for this Host/Initiators.
                        // Check if there is a shared ExportMask in CoprHD with by checking the existing initiators list.
                        sharedVplexExportMask = VPlexUtil.getExportMasksWithExistingInitiators(vplexURI, _dbClient, inits,
                                varrayUri,
                                vplexClusterId);

                        // If an ExportMask like this is found, there is already a storage view
                        // on the VPLEX with some or all the initiators, so ViPR will reuse the ExportMask
                        // and storage view in a "shared by multiple hosts" kind of way.
                        // If not found, ViPR will attempt to find a suitable existing storage view
                        // on the VPLEX and set it up as a new ExportMask.
                        if (null != sharedVplexExportMask) {
                            sharedExportMasks.add(sharedVplexExportMask);
                            // If sharedVplexExportMask is found then that export mask will be used to add any missing
                            // initiators or storage ports as needed, and volumes requested, if not already present.
                            setupExistingExportMaskWithNewHost(blockObjectMap, vplexSystem, exportGroup, varrayUri,
                                    exportMasksToUpdateOnDevice, exportMasksToUpdateOnDeviceWithInitiators,
                                    exportMasksToUpdateOnDeviceWithStoragePorts, inits, sharedVplexExportMask, opId);

                            VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, sharedVplexExportMask.getMaskName());
                            _log.info("Refreshing ExportMask {}", sharedVplexExportMask.getMaskName());
                            VPlexControllerUtils.refreshExportMask(
                                    _dbClient, storageView, sharedVplexExportMask, targetPortToPwwnMap, _networkDeviceController);

                            foundMatchingStorageView = true;
                            break;
                        } else {
                            _log.info("could not find an existing matching ExportMask in ViPR, "
                                    + "so ViPR will see if there is one already on the VPLEX system");

                            // ViPR will attempt to find a suitable existing storage view
                            // on the VPLEX and set it up as a new ExportMask.
                            long start = new Date().getTime();
                            foundMatchingStorageView = checkForExistingStorageViews(client, targetPortToPwwnMap,
                                    vplexSystem, vplexClusterName, inits, exportGroup,
                                    varrayUri, blockObjectMap,
                                    exportMasksToUpdateOnDevice, exportMasksToUpdateOnDeviceWithInitiators,
                                    exportMasksToUpdateOnDeviceWithStoragePorts, doInitiatorRefresh, opId);
                            long elapsed = new Date().getTime() - start;
                            _log.info("TIMER: finding an existing storage view took {} ms and returned {}",
                                    elapsed, foundMatchingStorageView);
                            break;
                        }
                    case FOUND_ONE_VIPR_EXPORT_MASK:
                        // get the single value in the map
                        ExportMask viprExportMask = vplexExportMasks.values().iterator().next();

                        _log.info("a valid ExportMask matching these initiators exists already in ViPR "
                                + "for this VPLEX device, so ViPR will re-use it: " + viprExportMask.getMaskName());

                        VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, viprExportMask.getMaskName());
                        _log.info("Refreshing ExportMask {}", viprExportMask.getMaskName());
                        VPlexControllerUtils.refreshExportMask(
                                _dbClient, storageView, viprExportMask, targetPortToPwwnMap, _networkDeviceController);

                        reuseExistingExportMask(blockObjectMap, vplexSystem, exportGroup,
                                varrayUri, exportMasksToUpdateOnDevice,
                                exportMasksToUpdateOnDeviceWithStoragePorts, inits,
                                allPortsFromMaskMatchForVarray, viprExportMask, opId);

                        foundMatchingStorageView = true;

                        break;
                    default:
                        String message = "Invalid Configuration: more than one VPLEX ExportMask "
                                + "in this cluster exists in ViPR for these initiators " + inits;
                        _log.error(message);
                        throw new Exception(message);
                }

                // If a matching storage view has STILL not been found, either use a shared ExportMask if found in the database,
                // or set up a brand new ExportMask (to create a storage view on the VPLEX) for the Host.
                if (!foundMatchingStorageView) {
                    if (null == sharedVplexExportMask) {
                        // If we reached here, it means there isn't an existing storage view on the VPLEX with the host,
                        // and no other shared ExportMask was found in the database by looking at existingInitiators.
                        // So, ViPR will try to find if there is shared export mask already for the ExportGroup in the database.
                        sharedVplexExportMask = VPlexUtil.getSharedExportMaskInDb(exportGroup, vplexURI, _dbClient,
                                varrayUri, vplexClusterId, hostInitiatorMap);
                    }

                    if (null != sharedVplexExportMask) {
                        // If a sharedExportMask was found, then the new host will be added to that ExportMask
                        _log.info(String.format(
                                "Shared export mask %s %s found for the export group %s %s which will be reused for initiators %s .",
                                sharedVplexExportMask.getMaskName(), sharedVplexExportMask.getId(), exportGroup.getLabel(),
                                exportGroup.getId(), inits.toString()));
                        sharedExportMasks.add(sharedVplexExportMask);

                        VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, sharedVplexExportMask.getMaskName());
                        _log.info("Refreshing ExportMask {}", sharedVplexExportMask.getMaskName());
                        VPlexControllerUtils.refreshExportMask(
                                _dbClient, storageView, sharedVplexExportMask, targetPortToPwwnMap, _networkDeviceController);

                        setupExistingExportMaskWithNewHost(blockObjectMap, vplexSystem, exportGroup, varrayUri,
                                exportMasksToUpdateOnDevice, exportMasksToUpdateOnDeviceWithInitiators,
                                exportMasksToUpdateOnDeviceWithStoragePorts, inits, sharedVplexExportMask, opId);
                    } else {
                        // Otherwise, create a brand new ExportMask, which will enable the storage view to
                        // be created on the VPLEX device as part of this workflow
                        _log.info("did not find a matching existing storage view anywhere, so ViPR "
                                + "will initialize a new one and push it to the VPLEX device");
                        setupNewExportMask(blockObjectMap, vplexSystem, exportGroup, varrayUri,
                                exportMasksToCreateOnDevice, inits, vplexClusterId, opId);
                    }
                }
            }
        } finally {
            if (lockAcquired) {
                _vplexApiLockManager.releaseLock(lockName);
            }
        }

        _log.info("updating zoning if necessary for both new and updated export masks");
        String zoningStepId = addStepsForZoningUpdate(export, initiators,
                blockObjectMap, workflow, waitFor, exportMasksToCreateOnDevice,
                exportMasksToUpdateOnDevice);

        _log.info("set up initiator pre-registration step");
        String storageViewStepId = 
                addStepsForInitiatorRegistration(initiators, vplexSystem, vplexClusterName, workflow, zoningStepId);

        _log.info("processing the export masks to be created");
        for (ExportMask exportMask : exportMasksToCreateOnDevice) {
            storageViewStepId = addStepsForExportMaskCreate(blockObjectMap, workflow, vplexSystem, exportGroup,
                    storageViewStepId, exportMask);
        }

        _log.info("processing the export masks to be updated");
        for (ExportMask exportMask : exportMasksToUpdateOnDevice) {
            boolean shared = false;
            if (sharedExportMasks.contains(exportMask)) {
                shared = true;
            }

            storageViewStepId = addStepsForExportMaskUpdate(export,
                    blockObjectMap, workflow, vplexSystem,
                    exportMasksToUpdateOnDeviceWithInitiators,
                    exportMasksToUpdateOnDeviceWithStoragePorts,
                    storageViewStepId, exportMask, shared);

        }

        long elapsed = new Date().getTime() - startAssembly;
        _log.info("TIMER: export mask assembly took {} ms", elapsed);

        return storageViewStepId;
    }

    /**
     * Filters export masks so that only those associated with this VPLEX and list of
     * host initiators are included. Also, returns a flag indicating whether
     * or not the storage ports in the export mask belong to the same vplex
     * cluster as the varray.
     *
     * @param vplexExportMasks
     *            an out collection
     * @param inits
     *            the initiators for the host in question
     * @param varrayUri
     *            virtual array URI
     * @param vplexSystem
     *            StorageSystem object represnting the VPLEX system
     * @param vplexCluster
     *            a String indicating the VPLEX cluster in question
     * @return a flag indicating the storage port networking status
     */
    private boolean filterExportMasks(Map<URI, ExportMask> vplexExportMasks, List<Initiator> inits,
            URI varrayUri, StorageSystem vplexSystem, String vplexCluster) {
        boolean allPortsFromMaskMatchForVarray = true;

        for (Initiator init : inits) {

            List<ExportMask> masks = ExportUtils.getInitiatorExportMasks(init, _dbClient);
            for (ExportMask mask : masks) {
                if (mask.getStorageDevice().equals(vplexSystem.getId())) {

                    // This could be a match, but:
                    // We need to make sure the storage ports presents in the exportmask
                    // belongs to the same vplex cluster as the varray.
                    // This indicates which cluster this is part of.

                    boolean clusterMatch = false;

                    _log.info("this ExportMask contains these storage ports: " + mask.getStoragePorts());
                    for (String portUri : mask.getStoragePorts()) {
                        StoragePort port = getDataObject(StoragePort.class, URI.create(portUri), _dbClient);
                        if (port != null) {
                            if (clusterMatch == false) {
                                // We need to match the VPLEX cluster for the exportMask
                                // as the exportMask for the same host can be in both VPLEX clusters
                                String vplexClusterForMask = ConnectivityUtil.getVplexClusterOfPort(port);
                                clusterMatch = vplexClusterForMask.equals(vplexCluster);
                                if (clusterMatch) {
                                    _log.info("a matching ExportMask " + mask.getMaskName()
                                            + " was found on this VPLEX " + vplexSystem.getSmisProviderIP()
                                            + " on  cluster " + vplexCluster);
                                    vplexExportMasks.put(mask.getId(), mask);
                                } else {
                                    break;
                                }
                            }
                            if (clusterMatch) {
                                StringSet taggedVarrays = port.getTaggedVirtualArrays();
                                if (taggedVarrays != null && taggedVarrays.contains(varrayUri.toString())) {
                                    _log.info("Virtual Array " + varrayUri +
                                            " has connectivity to port " + port.getLabel());
                                } else {
                                    allPortsFromMaskMatchForVarray = false;
                                }
                            }
                        }
                    }
                }
            }
        }

        return allPortsFromMaskMatchForVarray;
    }

    /**
     * Checks for the existence of an existing Storage View on the VPLEX device based
     * on the host's initiator ports. Returns a flag indicating whether or not a
     * storage view was actually found on the device.
     *
     * @param client
     *            a VPLEX API client instance
     * @param targetPortToPwwnMap
     *            cached storage port data from the VPLEX API
     * @param vplexSystem
     *            a StorageSystem object representing the VPLEX system
     * @param vplexCluster
     *            a String indicating which VPLEX question to look at
     * @param inits
     *            the host initiators of the host in question
     * @param exportGroup
     *            the ViPR export group
     * @param varrayUri
     *            -- NOTE! The varrayUri may not be the same as the one in ExportGroup
     * @param blockObjectMap
     *            the map of URIs to block volumes for export
     * @param exportMasksToUpdateOnDevice
     *            collection of ExportMasks to update
     * @param exportMasksToUpdateOnDeviceWithInitiators
     *            a map of ExportMasks to initiators
     * @param exportMasksToUpdateOnDeviceWithStoragePorts
     *            a map of ExportMasks to storage ports
     * @param opId
     *            the workflow step id used to find the workflow to store/load zoning map
     * @return whether or not a storage view was actually found on the device
     */
    private boolean checkForExistingStorageViews(VPlexApiClient client,
            Map<String, String> targetPortToPwwnMap,
            StorageSystem vplexSystem, String vplexCluster, List<Initiator> inits,
            ExportGroup exportGroup, URI varrayUri, Map<URI, Integer> blockObjectMap,
            List<ExportMask> exportMasksToUpdateOnDevice,
            Map<URI, List<Initiator>> exportMasksToUpdateOnDeviceWithInitiators,
            Map<URI, List<URI>> exportMasksToUpdateOnDeviceWithStoragePorts, 
            Boolean[] doInitiatorRefresh, String opId) throws Exception {
        boolean foundMatchingStorageView = false;

        List<String> initiatorNames = VPlexControllerUtils.getInitiatorNames(
                vplexSystem.getSerialNumber(), vplexCluster, client, inits.iterator(), doInitiatorRefresh, _dbClient);

        long start = new Date().getTime();
        List<VPlexStorageViewInfo> storageViewInfos = client.getStorageViewsContainingInitiators(
                vplexCluster, initiatorNames);
        long elapsed = new Date().getTime() - start;
        _log.info("TIMER: finding storage views containing initiators took {} ms", elapsed);

        if (storageViewInfos.size() > 1) {
            List<String> names = new ArrayList<String>();
            for (VPlexStorageViewInfo info : storageViewInfos) {
                names.add(info.getName());
            }
            // this means we found more than one storage view on the vplex containing the initiators.
            // this could mean the host's initiators are split across several storage views, or maybe in
            // multiple storage views, which we don't want to deal with. the exception provides a
            // list of storage view names and initiators.
            throw VPlexApiException.exceptions.tooManyExistingStorageViewsFound(
                    Joiner.on(", ").join(names), Joiner.on(", ").join(initiatorNames));

        } else if (storageViewInfos.size() == 1) {

            _log.info("a matching storage view was found on the VPLEX device, so ViPR will import it.");

            VPlexStorageViewInfo storageView = storageViewInfos.get(0);
            foundMatchingStorageView = true;

            // Grab the storage ports that have been allocated for this
            // existing mask.
            List<String> storagePorts = storageView.getPorts();

            if (storagePorts != null && storagePorts.isEmpty()) {
                _log.warn("No storage ports were found in the existing storage view {}, cannot reuse.",
                        storageView.getName());
                return false;
            }

            // convert storage view target ports like P0000000046E01E80-A0-FC02
            // to port wwn format that ViPR better understands like 0x50001442601e8002
            List<String> portWwns = new ArrayList<String>();
            for (String storagePort : storagePorts) {
                if (targetPortToPwwnMap.keySet().contains(storagePort)) {
                    portWwns.add(WwnUtils.convertWWN(targetPortToPwwnMap.get(storagePort), WwnUtils.FORMAT.COLON));
                }
            }

            List<String> storagePortURIs = ExportUtils.storagePortNamesToURIs(_dbClient, portWwns);
            _log.info("this storage view contains storage port URIs: " + storagePortURIs);

            // If there are no storage ports in the storageview we don't know which cluster
            // storageview is from.
            if (storagePortURIs == null || storagePortURIs.isEmpty()) {
                _log.warn("No storage ports managed by ViPR were found in the existing storage view {}, cannot reuse",
                        storageView.getName());
                return false;
            }

            List<String> initiatorPorts = storageView.getInitiatorPwwns();

            for (Initiator init : inits) {
                String port = init.getInitiatorPort();
                String normalizedName = Initiator.normalizePort(port);
                _log.info("   looking at initiator " + normalizedName + " host " + VPlexUtil.getInitiatorHostResourceName(init));
                if (initiatorPorts.contains(normalizedName)) {
                    _log.info("      found a matching initiator for " + normalizedName
                            + " host " + VPlexUtil.getInitiatorHostResourceName(init)
                            + " in storage view " + storageView.getName());
                }
            }

            ExportMask exportMask = new ExportMask();
            exportMask.setMaskName(storageView.getName());
            exportMask.setStorageDevice(vplexSystem.getId());
            exportMask.setId(URIUtil.createId(ExportMask.class));
            exportMask.setCreatedBySystem(false);
            exportMask.setNativeId(storageView.getPath());

            List<Initiator> initsToAdd = new ArrayList<Initiator>();
            for (Initiator init : inits) {
                // add all the the initiators the user has requested to add
                // to the exportMask initiators list
                exportMask.addInitiator(init);
                exportMask.addToUserCreatedInitiators(init);
                String normalInit = Initiator.normalizePort(init.getInitiatorPort());
                if (!storageView.getInitiatorPwwns().contains(normalInit)) {
                    initsToAdd.add(init);
                }
            }

            exportMask.setStoragePorts(storagePortURIs);

            // Update the tracking containers
            exportMask.addToExistingVolumesIfAbsent(storageView.getWwnToHluMap());
            exportMask.addToExistingInitiatorsIfAbsent(initiatorPorts);

            // Create zoningMap for the matched initiators and storagePorts
            _networkDeviceController.updateZoningMapForInitiators(exportGroup, exportMask, false);

            _dbClient.createObject(exportMask);

            if (!initsToAdd.isEmpty()) {
                ExportPathParams pathParams = _blockScheduler.calculateExportPathParamForVolumes(
                        blockObjectMap.keySet(), exportGroup.getNumPaths(), vplexSystem.getId(), exportGroup.getId());

                // Try to assign new ports by passing in existingMap
                Map<URI, List<URI>> assignments = _blockScheduler.assignStoragePorts(vplexSystem, exportGroup,
                        initsToAdd, exportMask.getZoningMap(), pathParams, null, _networkDeviceController, varrayUri, opId);
                // Consolidate the prezoned ports with the new assignments to get the total ports needed in the mask
                if (assignments != null && !assignments.isEmpty()) {
                    // Update zoningMap if there are new assignments
                    exportMask = ExportUtils.updateZoningMap(_dbClient, exportMask, assignments,
                            exportMasksToUpdateOnDeviceWithStoragePorts);
                }
            }

            exportMasksToUpdateOnDevice.add(exportMask);

            // add the initiators to the map for the exportMask that do not exist
            // already in the storage view as to create steps to add those initiators
            exportMasksToUpdateOnDeviceWithInitiators.put(exportMask.getId(), initsToAdd);

            // Storage ports that needs to be added will be calculated in the
            // add storage ports method from the zoning Map.
            exportMasksToUpdateOnDeviceWithStoragePorts.put(exportMask.getId(), new ArrayList<URI>());
        }

        return foundMatchingStorageView;
    }
    
    /**
     * Handles setting up a new ExportMask for creation on the VPLEX device.
     *
     * @param blockObjectMap
     *            the map of URIs to block volumes for export
     * @param vplexSystem
     *            a StorageSystem object representing the VPLEX system
     * @param exportGroup
     *            the ViPR export group
     * @param varrayUri
     *            -- NOTE: may not be same as ExportGroup varray
     * @param exportMasksToCreateOnDevice
     *            collection of ExportMasks to create
     * @param inits
     *            the host initiators of the host in question
     * @param vplexCluster
     *            String representing the VPLEX cluster in question
     * @param opId
     *            the workflow step id
     * @throws Exception
     */
    private void setupNewExportMask(Map<URI, Integer> blockObjectMap,
            StorageSystem vplexSystem, ExportGroup exportGroup,
            URI varrayUri, List<ExportMask> exportMasksToCreateOnDevice,
            List<Initiator> initiators, String vplexCluster, String opId) throws Exception {

        ExportPathParams pathParams = _blockScheduler.calculateExportPathParamForVolumes(
                blockObjectMap.keySet(), 0, vplexSystem.getId(), exportGroup.getId());
        if (exportGroup.getType() != null) {
            pathParams.setExportGroupType(exportGroup.getType());
        }
        Map<URI, List<URI>> assignments = _blockScheduler.assignStoragePorts(vplexSystem, exportGroup,
                initiators, null, pathParams, blockObjectMap.keySet(), _networkDeviceController, varrayUri, opId);
        List<URI> targets = BlockStorageScheduler.getTargetURIsFromAssignments(assignments);

        String maskName = getComputedExportMaskName(vplexSystem, varrayUri, initiators,
                CustomConfigConstants.VPLEX_STORAGE_VIEW_NAME);

        ExportMask exportMask = ExportMaskUtils.initializeExportMask(vplexSystem,
                exportGroup, initiators, blockObjectMap, targets, assignments, maskName, _dbClient);
        exportMask.addToUserCreatedInitiators(initiators);
        exportMask.addInitiators(initiators);// Consistency
        _dbClient.updateObject(exportMask);

        exportMasksToCreateOnDevice.add(exportMask);
    }

    /**
     * This method is used to setup exportMask for the two cases as below
     * 1. When initiators to be added are already present on the StorageView on VPLEX
     * 2. When there is a sharedStorageView on VPLEX for the hosts in the exportGroup then new host
     * is added to the same ExportMask in database and same storage view on the VPLEX
     *
     * @param blockObjectMap
     *            the map of URIs to block volumes for export
     * @param vplexSystem
     *            reference to VPLEX storage system
     * @param exportGroup
     *            reference to EXportGroup object
     * @param varrayUri
     *            -- NOTE: may not be same as ExportGroup varray
     * @param exportMasksToUpdateOnDevice
     *            Out param to track exportMasks that needs to be updated
     * @param exportMasksToUpdateOnDeviceWithInitiators
     *            Out Param to track exportMasks that needs to be updated with the initiators
     * @param exportMasksToUpdateOnDeviceWithStoragePorts
     *            Out Param to track exportMasks that needs to be updated with the storageports
     * @param inits
     *            List of initiators that needs to be added
     * @param sharedVplexExportMask
     *            ExportMask which represents multiple host.
     * @param opId
     *            the workflow step id used to find the workflow to locate the zoning map stored in ZK
     * @throws Exception
     */
    private void setupExistingExportMaskWithNewHost(Map<URI, Integer> blockObjectMap,
            StorageSystem vplexSystem, ExportGroup exportGroup,
            URI varrayUri, List<ExportMask> exportMasksToUpdateOnDevice,
            Map<URI, List<Initiator>> exportMasksToUpdateOnDeviceWithInitiators,
            Map<URI, List<URI>> exportMasksToUpdateOnDeviceWithStoragePorts,
            List<Initiator> inits, ExportMask sharedVplexExportMask, String opId) throws Exception {

        ExportPathParams pathParams = _blockScheduler.calculateExportPathParamForVolumes(
                blockObjectMap.keySet(), exportGroup.getNumPaths(), vplexSystem.getId(), exportGroup.getId());

        // Try to assign new ports by passing in existingMap
        Map<URI, List<URI>> assignments = _blockScheduler.assignStoragePorts(vplexSystem, exportGroup,
                inits, sharedVplexExportMask.getZoningMap(), pathParams, null, _networkDeviceController, varrayUri, opId);
        if (assignments != null && !assignments.isEmpty()) {
            // Update zoningMap if there are new assignments
            sharedVplexExportMask = ExportUtils.updateZoningMap(_dbClient, sharedVplexExportMask, assignments,
                    exportMasksToUpdateOnDeviceWithStoragePorts);
        }

        List<Initiator> initsToAddOnVplexDevice = new ArrayList<Initiator>();
        for (Initiator init : inits) {
            if (!sharedVplexExportMask.hasExistingInitiator(init)) {
                initsToAddOnVplexDevice.add(init);
            }
        }

        // add the initiators to the map for the exportMask that do not exist
        // already in the storage view as to create steps to add those initiators
        if (!initsToAddOnVplexDevice.isEmpty()) {
            if (exportMasksToUpdateOnDeviceWithInitiators.get(sharedVplexExportMask.getId()) == null) {
                exportMasksToUpdateOnDeviceWithInitiators.put(sharedVplexExportMask.getId(), new ArrayList<Initiator>());
            }
            exportMasksToUpdateOnDeviceWithInitiators.get(sharedVplexExportMask.getId()).addAll(initsToAddOnVplexDevice);
        }

        // Storage ports that needs to be added will be calculated in the
        // add storage ports method from the zoning Map.
        exportMasksToUpdateOnDeviceWithStoragePorts.put(sharedVplexExportMask.getId(), new ArrayList<URI>());

        exportMasksToUpdateOnDevice.add(sharedVplexExportMask);

    }
    
    /**
     * Add workflow steps for adding Initiators to a specific varray for the given VPlex.
     *
     * @param workflow
     *            -- Workflow steps go into
     * @param vplex
     *            -- Storage system
     * @param exportGroup
     *            -- ExportGroup operation invoked on
     * @param varrayURI
     *            -- Virtual Array URI that the Initiators are in
     * @param hostInitiatorURIs
     *            -- URIs of the Initiators
     * @param initiators
     *            -- list of Initiator objects
     * @param hostURI
     *            -- The hostURI
     * @param previousStepId
     *            -- wait on this step if non-null
     * @param opId
     *            -- step id for our operation
     * @return StepId of last step generated
     */
    private String addStepsForAddInitiators(Workflow workflow, StorageSystem vplex,
            ExportGroup exportGroup, URI varrayURI, List<URI> hostInitiatorURIs,
            List<Initiator> initiators, URI hostURI, String previousStepId, String opId) throws Exception {
        String lastStepId = null;
        URI vplexURI = vplex.getId();
        URI exportURI = exportGroup.getId();
        String initListStr = Joiner.on(',').join(hostInitiatorURIs);

        // Find the ExportMask for my host.
        ExportMask exportMask = VPlexUtil.getExportMaskForHostInVarray(_dbClient,
                exportGroup, hostURI, vplexURI, varrayURI);
        if (exportMask == null) {
            _log.info("No export mask found for hostURI: " + hostURI + " varrayURI: " + varrayURI);

            Map<URI, Integer> volumeMap = ExportUtils.getExportGroupVolumeMap(_dbClient, vplex, exportGroup);
            // Partition the Volumes by varray.
            Map<URI, Set<URI>> varrayToVolumes = VPlexUtil.mapBlockObjectsToVarrays(_dbClient, volumeMap.keySet(),
                    vplexURI, exportGroup);
            // Filter the volumes by our Varray.
            Map<URI, Integer> varrayVolumeMap = ExportMaskUtils.filterVolumeMap(volumeMap, varrayToVolumes.get(varrayURI));

            // Create the ExportMask if there are volumes in this varray.
            if (!varrayVolumeMap.isEmpty()) {
                lastStepId = assembleExportMasksWorkflow(vplexURI, exportURI, varrayURI,
                        hostInitiatorURIs, varrayVolumeMap, workflow, previousStepId, opId);
            }
        } else {
            VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
            String vplexClusterName = VPlexUtil.getVplexClusterName(exportMask, vplexURI, client, _dbClient);
            VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, exportMask.getMaskName());
            _log.info("Refreshing ExportMask {}", exportMask.getMaskName());
            VPlexControllerUtils.refreshExportMask(
                    _dbClient, storageView, exportMask,
                    VPlexControllerUtils.getTargetPortToPwwnMap(client, vplexClusterName),
                    _networkDeviceController);

            if (exportMask.getVolumes() == null) {
                // This can occur in Brownfield scenarios where we have not added any volumes yet to the HA side,
                // CTRL10760
                _log.info(String.format("No volumes in ExportMask %s (%s), so not adding initiators",
                        exportMask.getMaskName(), exportMask.getId()));
                return lastStepId;
            }
            _log.info(String.format("Adding initiators %s for host %s mask %s (%s)",
                    getInitiatorsWwnsString(initiators), hostURI.toString(), exportMask.getMaskName(), exportMask.getId()));

            // Calculate the path parameters for the volumes in this ExportMask
            Collection<URI> volumeURIs = new HashSet<URI>();
            if (exportMask.getVolumes() != null && !exportMask.getVolumes().isEmpty()) {
                volumeURIs = (Collections2.transform(exportMask.getVolumes().keySet(),
                        CommonTransformerFunctions.FCTN_STRING_TO_URI));
            } else if (exportGroup.getVolumes() != null && !exportGroup.getVolumes().isEmpty()) { // Hit this condition
 // in CTRL-9944
                // (unknown why)
                _log.info(String.format("No volumes in ExportMask %s, using ExportGroup %s for ExportPathParam", exportMask.getId(),
                        exportGroup.getId()));
                Map<URI, Integer> volumeMap = ExportUtils.getExportGroupVolumeMap(_dbClient, vplex, exportGroup);
                // Partition the Volumes by varray. Then use only the volumes in the requested varray.
                Map<URI, Set<URI>> varrayToVolumes = VPlexUtil.mapBlockObjectsToVarrays(_dbClient, volumeMap.keySet(), vplexURI,
                        exportGroup);
                volumeURIs = varrayToVolumes.get(varrayURI);
            } else {
                _log.info(String.format("No volumes at all- using default path parameters: %s", exportMask.getId()));
            }

            ExportPathParams pathParams = _blockScheduler.calculateExportPathParamForVolumes(
                    volumeURIs, exportGroup.getNumPaths(), exportMask.getStorageDevice(), exportGroup.getId());
            if (exportGroup.getType() != null) {
                pathParams.setExportGroupType(exportGroup.getType());
            }

            // Assign additional StoragePorts if needed.
            Map<URI, List<URI>> assignments = _blockScheduler.assignStoragePorts(vplex, exportGroup,
                    initiators, exportMask.getZoningMap(), pathParams, volumeURIs, _networkDeviceController, varrayURI, opId);
            List<URI> newTargetURIs = BlockStorageScheduler.getTargetURIsFromAssignments(assignments);
            exportMask.addZoningMap(BlockStorageScheduler.getZoneMapFromAssignments(assignments));
            _dbClient.updateObject(exportMask);

            _log.info(String.format("Adding targets %s for host %s",
                    newTargetURIs.toString(), hostURI.toString()));

            // Create a Step to add the SAN Zone
            String zoningStepId = workflow.createStepId();
            Workflow.Method zoningMethod = zoneAddInitiatorStepMethod(
                    vplexURI, exportURI, hostInitiatorURIs, varrayURI);
            Workflow.Method zoningRollbackMethod = zoneRollbackMethod(exportURI, zoningStepId);
            zoningStepId = workflow.createStep(ZONING_STEP,
                    String.format("Zone initiator %s to ExportGroup %s(%s)",
                            initListStr, exportGroup.getLabel(), exportURI),
                    previousStepId, vplexURI, vplex.getSystemType(), this.getClass(), zoningMethod, zoningRollbackMethod, zoningStepId);

            // Create a Step to add the initiator to the Storage View
            String message = String.format("initiators %s to StorageView %s", initListStr,
                    exportGroup.getGeneratedName());
            ExportMask sharedExportMask = VPlexUtil.getSharedExportMaskInDb(exportGroup, vplexURI, _dbClient, varrayURI, null, null);
            boolean shared = false;
            if (null != sharedExportMask && sharedExportMask.getId().equals(exportMask.getId())) {
                shared = true;
            }

            String addInitStep = workflow.createStepId();
            ExportMaskAddInitiatorCompleter addInitCompleter = new ExportMaskAddInitiatorCompleter(exportURI, exportMask.getId(),
                    hostInitiatorURIs, newTargetURIs, addInitStep);
            Workflow.Method addToViewMethod = new Workflow.Method(STORAGE_VIEW_ADD_INITIATORS_METHOD, vplexURI, 
            		exportURI, exportMask.getId(), hostInitiatorURIs, null, shared, addInitCompleter);

            Workflow.Method addToViewRollbackMethod = new Workflow.Method(STORAGE_VIEW_ADD_INITS_ROLLBACK_METHOD, vplexURI, 
            		exportURI, exportMask.getId(), hostInitiatorURIs, null, addInitStep);
            lastStepId = workflow.createStep("storageView", "Add " + message,
                    zoningStepId, vplexURI, vplex.getSystemType(), VPlexMaskingWorkflowEntryPoints.class, 
                    addToViewMethod, addToViewRollbackMethod, addInitStep);
        }
        return lastStepId;
    }

    private Workflow.Method zoneAddInitiatorStepMethod(URI vplexURI, URI exportURI,
            List<URI> initiatorURIs, URI varrayURI) {
        return new Workflow.Method("zoneAddInitiatorStep", vplexURI, exportURI, initiatorURIs, varrayURI);
    }

    /**
     * Workflow step to add an initiator. Calls NetworkDeviceController.
     * Arguments here should match zoneAddInitiatorStepMethod above except for stepId.
     *
     * @param exportURI
     * @param initiatorURIs
     * @param varrayURI
     * @throws WorkflowException
     */
    public void zoneAddInitiatorStep(URI vplexURI, URI exportURI,
            List<URI> initiatorURIs, URI varrayURI, String stepId) throws WorkflowException {
        String initListStr = "";
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            initListStr = Joiner.on(',').join(initiatorURIs);
            ExportGroup exportGroup = getDataObject(ExportGroup.class, exportURI, _dbClient);
            _log.info(String.format(
                    "Adding initiators %s to ExportGroup %s (%s)",
                    initListStr, exportGroup.getLabel(), exportGroup.getId()));
            List<Initiator> initiators = _dbClient.queryObject(Initiator.class, initiatorURIs);
            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, vplexURI);
            Map<URI, List<URI>> maskToInitiatorsMap = new HashMap<URI, List<URI>>();
            ExportMask sharedExportMask = VPlexUtil.getSharedExportMaskInDb(exportGroup, vplexURI, _dbClient, varrayURI, null, null);
            for (ExportMask exportMask : exportMasks) {
                boolean shared = false;
                if (sharedExportMask != null) {
                    if (sharedExportMask.getId().equals(exportMask.getId())) {
                        shared = true;
                    }
                }
                maskToInitiatorsMap.put(exportMask.getId(), new ArrayList<URI>());
                Set<URI> exportMaskHosts = VPlexUtil.getExportMaskHosts(_dbClient, exportMask, shared);
                // Only add initiators to this ExportMask that are on the host of the Export Mask
                for (Initiator initiator : initiators) {
                    if (exportMaskHosts.contains(VPlexUtil.getInitiatorHost(initiator))) {
                        maskToInitiatorsMap.get(exportMask.getId()).add(initiator.getId());
                    }
                }
            }
            _networkDeviceController.zoneExportAddInitiators(exportURI, maskToInitiatorsMap, stepId);
        } catch (Exception ex) {
            _log.error("Exception adding initators: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.ADD_INITIATOR_WORKFLOW_STEP.getName();
            ServiceError serviceError = VPlexApiException.errors.zoneAddInitiatorStepFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    

    
    /**
     * Add steps necessary for Export removeInitiators.
     * This routine may be called multiple times for an Export Group on each
     * exportMask that needs to be adjusted.
     *
     * @param vplex
     *            -- StorageSystem VPLEX
     * @param workflow
     *            -- Workflow steps being added to
     * @param exportGroup
     *            -- ExportGroup
     * @param exportMask
     *            -- ExportMask being processed
     * @param initiators
     *            -- List<Initiator> initiators being removed
     * @param hostURI
     *            -- Host URI
     * @param hostInitiatorURIs
     *            -- list of Host Initiators
     * @param errorMessages
     *            -- collector for any validation-related error messages
     * @param previousStep
     *            -- previous step to wait on
     * @return String last step added to workflow; null if no steps added
     * @throws Exception
     */
    private String addStepsForRemoveInitiators(StorageSystem vplex, Workflow workflow,
            ExportRemoveInitiatorCompleter completer,
            ExportGroup exportGroup, ExportMask exportMask, List<Initiator> initiators,
            URI hostURI, List<URI> initiatorsAlreadyRemovedFromExportGroup, String previousStep) throws Exception {
        String lastStep = previousStep;

        // assemble a list of other ExportGroups that reference this ExportMask
        List<ExportGroup> otherExportGroups = ExportUtils.getOtherExportGroups(exportGroup, exportMask,
                _dbClient);

        _log.info(String.format("will be removing initiators %s for host %s mask %s (%s)",
                getInitiatorsWwnsString(initiators), hostURI.toString(), exportMask.getMaskName(), exportMask.getId()));

        List<URI> hostInitiatorURIs = new ArrayList<URI>();
        for (Initiator initiator : initiators) {
            hostInitiatorURIs.add(initiator.getId());
        }

        // Determine the targets we should remove for the initiators being removed.
        // Normally one or more targets will be removed for each initiator that
        // is removed according to the zoning map.
        List<URI> targetURIs = getTargetURIs(exportMask, hostInitiatorURIs);

        _log.info(String.format("will be removing targets %s for host %s",
                targetURIs.toString(), hostURI.toString()));

        // What is about to happen...
        //
        // 1. if all the initiators in the storage view are getting removed
        // and there are no existing (external) initiators in the Export Mask
        // then delete the storage view
        //
        // 2. if there are other ExportGroups referencing this ExportMask
        // AND
        // if there are no more of this ExportMask's initiators present
        // in this ExportGroup's initiators
        // THEN
        // remove this ExportGroup's volumes
        // that aren't also present in other ExportGroups
        // from this ExportMask
        // AND FINALLY
        // remove the initiator(s) from the ExportGroup
        // but NOT from the ExportMask
        // (because other ExportGroups are referencing it still)
        //
        // 3. otherwise,
        // just remove initiators from the storage view, and if removing
        // all initiators, also remove any volumes present in the ExportGroup

        boolean removeAllInits = (hostInitiatorURIs.size() >= exportMask.getInitiators().size());
        boolean canDeleteMask = removeAllInits && !exportMask.hasAnyExistingInitiators();

        if (canDeleteMask) {
            if (!ExportUtils.exportMaskHasBothExclusiveAndSharedVolumes(exportGroup, otherExportGroups, exportMask)) {
                _log.info("all initiators are being removed and no " + "other ExportGroups reference ExportMask {}",
                        exportMask.getMaskName());
                _log.info("creating a deleteStorageView workflow step for " + exportMask.getMaskName());

                String exportMaskDeleteStep = workflow.createStepId();
                Workflow.Method storageViewExecuteMethod = deleteStorageViewMethod(vplex.getId(), exportGroup.getId(), exportMask.getId());
                lastStep = workflow.createStep(
                        DELETE_STORAGE_VIEW,
                        String.format("Delete VPLEX Storage View %s for ExportGroup %s", exportMask.getMaskName(),
                                exportGroup.getId()),
                        lastStep, vplex.getId(), vplex.getSystemType(), this.getClass(),
                        storageViewExecuteMethod, null, exportMaskDeleteStep);

                // Add zoning step for deleting ExportMask
                List<NetworkZoningParam> zoningParam = NetworkZoningParam.convertExportMasksToNetworkZoningParam(
                        exportGroup.getId(), Collections.singletonList(exportMask.getId()), _dbClient);
                Workflow.Method zoneMaskDeleteMethod = _networkDeviceController.zoneExportMasksDeleteMethod(zoningParam,
                        StringSetUtil.stringSetToUriList(exportMask.getVolumes().keySet()));
                Workflow.Method zoneNullRollbackMethod = _networkDeviceController.zoneNullRollbackMethod();
                lastStep = workflow
                        .createStep(null, "Zone delete mask: " + exportMask.getMaskName(), lastStep, nullURI, "network-system",
                                _networkDeviceController.getClass(), zoneMaskDeleteMethod, zoneNullRollbackMethod, null);
            } else {
                // export Mask has shared and exclusive volumes, removing the volumes.
                // The volumes can be taken from the export Group

                List<URI> volumeURIList = URIUtil.toURIList(exportGroup.getVolumes().keySet());

                if (!volumeURIList.isEmpty()) {
                    List<Volume> volumes = _dbClient.queryObject(Volume.class, volumeURIList);
                    String volumesForDisplay = Joiner.on(", ").join(
                            Collections2.transform(volumes,
                                    CommonTransformerFunctions.fctnDataObjectToForDisplay()));
                    _log.info("there are some volumes that need to be removed: " + volumesForDisplay);

                    _log.info("creating a remove volumes workflow step with " + exportMask.getMaskName()
                            + " for volumes " + CommonTransformerFunctions.collectionToString(volumeURIList));

                    completer.addExportMaskToRemovedVolumeMapping(exportMask.getId(), volumeURIList);

                    Workflow.Method storageViewRemoveVolume = new Workflow.Method(STORAGE_VIEW_REMOVE_VOLUMES_METHOD, vplex.getId(), 
                    		exportGroup.getId(), exportMask, volumeURIList);
                    lastStep = workflow.createStep("removeVolumes",
                            String.format("Removing volumes from export on storage array %s (%s) for export mask %s (%s)",
                                    vplex.getNativeGuid(), vplex.getId().toString(), exportMask.getMaskName(), exportMask.getId()),
                            lastStep, vplex.getId(), vplex.getSystemType(),
                            VPlexMaskingWorkflowEntryPoints.class, storageViewRemoveVolume, rollbackMethodNullMethod(), null);

                    // Add zoning step for removing volumes
                    List<NetworkZoningParam> zoningParam = NetworkZoningParam.convertExportMasksToNetworkZoningParam(
                            exportGroup.getId(), Collections.singletonList(exportMask.getId()), _dbClient);
                    Workflow.Method zoneRemoveVolumesMethod = _networkDeviceController.zoneExportRemoveVolumesMethod(
                            zoningParam, volumeURIList);
                    lastStep = workflow.createStep(null, "Zone remove volumes mask: " + exportMask.getMaskName(),
                            lastStep, nullURI, "network-system", _networkDeviceController.getClass(),
                            zoneRemoveVolumesMethod, _networkDeviceController.zoneNullRollbackMethod(), null);
                }
            }
        } else {
            // this is just a simple initiator removal, so just do it...
            lastStep = addStepsForInitiatorRemoval(vplex, workflow, completer, exportGroup,
                    exportMask, hostInitiatorURIs, targetURIs, lastStep,
                    removeAllInits, hostURI);
        }

        return lastStep;
    }

   

    /**
     * Adds workflow steps for the final removal of a set of Initiators
     * from a given ExportMask (Storage View) on the VPLEX.
     *
     * @param vplex
     *            the VPLEX system
     * @param workflow
     *            the current Workflow
     * @param exportGroup
     *            the ExportGroup from which these initiators have been removed
     * @param exportMask
     *            the ExportMask from which these initiators are being removed
     * @param hostInitiatorURIs
     *            the Initiator URIs that are being removed
     * @param targetURIs
     *            the target port URIs to be removed
     * @param zoneStep
     *            the zoning step id
     * @param removeAllInits
     *            a flag indicating whether all initiators
     *            for the containing host are being removed at once
     * @param computeResourceId
     *            compute resource
     * @param errorMessages
     *            -- collector for any validation-related error messages
     * @return a workflow step id
     */
    private String addStepsForInitiatorRemoval(StorageSystem vplex, Workflow workflow,
            ExportRemoveInitiatorCompleter completer, ExportGroup exportGroup, ExportMask exportMask,
            List<URI> hostInitiatorURIs, List<URI> targetURIs, String zoneStep,
            boolean removeAllInits, URI computeResourceId) {
        _log.info("these initiators are being marked for removal from export mask {}: {}",
                exportMask.getMaskName(), CommonTransformerFunctions.collectionToString(hostInitiatorURIs));

        String lastStep = workflow.createStepId();

        // removeInitiatorMethod will make sure not to remove existing initiators
        // from the storage view on the vplex device.
        Workflow.Method removeInitiatorMethod = new Workflow.Method(STORAGE_VIEW_REMOVE_INITIATORS_METHOD, vplex.getId(), exportGroup.getId(),
        		exportMask.getId(), hostInitiatorURIs, targetURIs);
        
        Workflow.Method removeInitiatorRollbackMethod = new Workflow.Method(ROLLBACK_METHOD_NULL);
        
        lastStep = workflow.createStep("storageView", "Removing " + hostInitiatorURIs.toString(),
                zoneStep, vplex.getId(), vplex.getSystemType(), VPlexMaskingWorkflowEntryPoints.class,
                removeInitiatorMethod, removeInitiatorRollbackMethod, lastStep);

        // Add zoning step for removing initiators
        Map<URI, List<URI>> exportMaskToInitiators = new HashMap<URI, List<URI>>();
        exportMaskToInitiators.put(exportMask.getId(), hostInitiatorURIs);
        List<NetworkZoningParam> zoningParam = NetworkZoningParam.convertExportMaskInitiatorMapsToNetworkZoningParam(
                exportGroup.getId(), exportMaskToInitiators, _dbClient);
        Workflow.Method zoneRemoveInitiatorsMethod = _networkDeviceController.zoneExportRemoveInitiatorsMethod(zoningParam);
        Workflow.Method zoneNullRollbackMethod = _networkDeviceController.zoneNullRollbackMethod();
        lastStep = workflow.createStep(null, "Zone remove initiataors mask: " + exportMask.getMaskName(),
                lastStep, nullURI, "network-system", _networkDeviceController.getClass(),
                zoneRemoveInitiatorsMethod, zoneNullRollbackMethod, null);

        return lastStep;
    }
    
    /**
     * Handles creating a workflow method for updating the zoning for
     * both the new ExportMasks being created and those being updated.
     *
     * @param export
     *            the ViPR ExportGroup in question
     * @param initiators
     *            the initiators of all the hosts in this export
     * @param blockObjectMap
     *            the map of URIs to block volumes for export
     * @param workflow
     *            the controller Workflow
     * @param waitFor
     *            if non-null, step will wait for previous step.
     * @param exportMasksToCreateOnDevice
     *            collection of ExportMasks to create on the device
     * @param exportMasksToUpdateOnDevice
     *            collection of ExportMasks to update on the device
     * @return
     */
    private String addStepsForZoningUpdate(URI export, List<URI> initiators,
            Map<URI, Integer> blockObjectMap, Workflow workflow,
            String waitFor,
            List<ExportMask> exportMasksToCreateOnDevice,
            List<ExportMask> exportMasksToUpdateOnDevice) {
        // Add a step to create the underlying zoning.
        String zoningStepId = workflow.createStepId();
        List<URI> exportMaskURIs = new ArrayList<URI>();
        for (ExportMask mask : exportMasksToCreateOnDevice) {
            exportMaskURIs.add(mask.getId());
        }
        for (ExportMask mask : exportMasksToUpdateOnDevice) {
            exportMaskURIs.add(mask.getId());
        }
        List<URI> volumeURIs = new ArrayList<URI>();
        volumeURIs.addAll(blockObjectMap.keySet());
        Workflow.Method zoningExecuteMethod = _networkDeviceController.zoneExportMasksCreateMethod(export, exportMaskURIs, volumeURIs);
        Workflow.Method zoningRollbackMethod = _networkDeviceController.zoneRollbackMethod(export, zoningStepId);
        zoningStepId = workflow.createStep(ZONING_STEP,
                String.format("Zone ExportGroup %s for initiators %s", export, initiators.toString()),
                waitFor, nullURI, "network-system",
                _networkDeviceController.getClass(), zoningExecuteMethod, zoningRollbackMethod, zoningStepId);
        return zoningStepId;
    }

    /**
     * Handles setting up workflow step for registering any unregistered initiators.
     * 
     * @param initiators the initiators to check for registration
     * @param vplexSystem the VPLEX StorageSystem object
     * @param vplexClusterName the VPLEX cluster name (like cluster-1 or cluster-2)
     * @param workflow the workflow
     * @param previousStepId the previous workflow step id to wait for
     * @return the id of the initiator registration step
     */
    private String addStepsForInitiatorRegistration(List<URI> initiators,
            StorageSystem vplexSystem, String vplexClusterName, Workflow workflow, String previousStepId) {

        Workflow.Method executeMethod = registerInitiatorsMethod(initiators, vplexSystem.getId(), vplexClusterName);
        // if rollback occurs, the unzoning step will cause any previously unregistered inits
        // to go back to unregistered when zoning disappears -- so, a null rollback method is fine here.
        Workflow.Method rollbackMethod = rollbackMethodNullMethod();
        String stepId = workflow.createStep(INITIATOR_REGISTRATION, "Register any unregistered initiators on VPLEX",
                previousStepId, vplexSystem.getId(), vplexSystem.getSystemType(), VPlexMaskingWorkflowEntryPoints.class,
                executeMethod, rollbackMethod, null);
        return stepId;
    }

    /**
     * Handles adding ExportMask creation into the export workflow.
     *
     * @param blockObjectMap
     *            the map of URIs to block volumes for export
     * @param workflow
     *            the controller Workflow
     * @param vplexSystem
     *            a StorageSystem objet representing the VPLEX
     * @param exportGroup
     *            the ViPR ExportGroup in question
     * @param storageViewStepId
     *            the current workflow step id, to be updated on return
     * @param exportMask
     *            the ExportMask object to be created
     * @return the workflow step id
     */
    private String addStepsForExportMaskCreate(Map<URI, Integer> blockObjectMap, Workflow workflow,
            StorageSystem vplexSystem, ExportGroup exportGroup,
            String storageViewStepId, ExportMask exportMask) {
        _log.info("adding step to create export mask: " + exportMask.getMaskName());

        List<URI> inits = new ArrayList<URI>();
        for (String init : exportMask.getInitiators()) {
            inits.add(URI.create(init));
        }

        // Make the targets for this host.
        List<URI> hostTargets = new ArrayList<URI>();
        for (String targetId : exportMask.getStoragePorts()) {
            hostTargets.add(URI.create(targetId));
        }

        String maskingStep = workflow.createStepId();
        // Add a step to export on the VPlex. They call this a Storage View.
        Workflow.Method storageViewExecuteMethod = new Workflow.Method(CREATE_STORAGE_VIEW,
                vplexSystem.getId(), exportGroup.getId(), exportMask.getId(), blockObjectMap, inits, hostTargets);

        // Workflow.Method storageViewRollbackMethod = new Workflow.Method(ROLLBACK_METHOD_NULL);
        Workflow.Method storageViewRollbackMethod = new Workflow.Method(ROLLBACK_CREATE_STORAGE_VIEW, vplexSystem.getId(), exportGroup.getId(), exportMask.getId());
        storageViewStepId = workflow.createStep("storageView",
                String.format("Create VPLEX Storage View for ExportGroup %s Mask %s", exportGroup.getId(), exportMask.getMaskName()),
                storageViewStepId, vplexSystem.getId(), vplexSystem.getSystemType(),
                VPlexMaskingWorkflowEntryPoints.class, storageViewExecuteMethod, storageViewRollbackMethod, maskingStep);
        return storageViewStepId;
    }

    /**
     * Handles adding ExportMask updates into the export workflow.
     *
     * @param exportGroupUri
     *            the ViPR ExportGroup in question
     * @param blockObjectMap
     *            the map of URIs to block volumes for export
     * @param workflow
     *            the controller Workflow
     * @param vplexSystem
     *            a StorageSystem objet representing the VPLEX
     * @param exportMasksToUpdateOnDeviceWithInitiators
     *            map of ExportMasks to update to initiators
     * @param exportMasksToUpdateOnDeviceWithStoragePorts
     *            map of ExportMasks to update to storage ports
     * @param storageViewStepId
     *            the current workflow step id, to be updated on return
     * @param exportMask
     *            the ExportMask object to be updated
     * @param sharedVplexExportMask
     *            boolean that indicates whether passed exportMask is shared for multiple host
     * @return
     */
    private String addStepsForExportMaskUpdate(URI exportGroupUri,
            Map<URI, Integer> blockObjectMap, Workflow workflow, StorageSystem vplexSystem,
            Map<URI, List<Initiator>> exportMasksToUpdateOnDeviceWithInitiators,
            Map<URI, List<URI>> exportMasksToUpdateOnDeviceWithStoragePorts,
            String storageViewStepId, ExportMask exportMask, boolean sharedVplexExportMask) {
        _log.info("adding steps to update export mask: " + exportMask.getMaskName());

        String addVolumeStepId = workflow.createStepId();
        // Add a step to update export mask on the VPlex.
        Workflow.Method storageViewExecuteMethod = new Workflow.Method(STORAGE_VIEW_ADD_VOLUMES_METHOD,
                vplexSystem.getId(), exportGroupUri, exportMask.getId(), blockObjectMap);
        
        Workflow.Method storageViewRollbackMethod = new Workflow.Method(STORAGE_VIEW_ADD_VOLS_ROLLBACK_METHOD, vplexSystem.getId(), 
                exportGroupUri, exportMask.getId(), new ArrayList<URI>(blockObjectMap.keySet()), addVolumeStepId);
        
        storageViewStepId = workflow.createStep("storageView",
                String.format("Updating VPLEX Storage View for ExportGroup %s Mask %s", exportGroupUri, exportMask.getMaskName()),
                storageViewStepId, vplexSystem.getId(), vplexSystem.getSystemType(),
                VPlexMaskingWorkflowEntryPoints.class, storageViewExecuteMethod, storageViewRollbackMethod, addVolumeStepId);

        if (exportMasksToUpdateOnDeviceWithInitiators.get(exportMask.getId()) != null) {

            List<Initiator> initiatorsToAdd = exportMasksToUpdateOnDeviceWithInitiators.get(exportMask.getId());
            List<URI> initiatorURIs = new ArrayList<URI>();
            for (Initiator initiator : initiatorsToAdd) {
                initiatorURIs.add(initiator.getId());
            }

            String addInitStep = workflow.createStepId();
            ExportMaskAddInitiatorCompleter completer = new ExportMaskAddInitiatorCompleter(exportGroupUri, exportMask.getId(), initiatorURIs,
                    new ArrayList<URI>(), addInitStep);

            Workflow.Method addInitiatorMethod = new Workflow.Method(STORAGE_VIEW_ADD_INITIATORS_METHOD, vplexSystem.getId(), 
            		exportGroupUri, exportMask.getId(), initiatorURIs, null, sharedVplexExportMask, completer);

            Workflow.Method initiatorRollback = new Workflow.Method(STORAGE_VIEW_ADD_INITS_ROLLBACK_METHOD, vplexSystem.getId(), 
            		exportGroupUri, exportMask.getId(), initiatorURIs, null, addInitStep);

            storageViewStepId = workflow.createStep("storageView",
                    String.format("Updating VPLEX Storage View Initiators for ExportGroup %s Mask %s", exportGroupUri, exportMask.getMaskName()),
                    storageViewStepId, vplexSystem.getId(), vplexSystem.getSystemType(),
                    VPlexMaskingWorkflowEntryPoints.class, addInitiatorMethod, initiatorRollback, addInitStep);
        }

        if (exportMasksToUpdateOnDeviceWithStoragePorts.containsKey(exportMask.getId())) {
            List<URI> storagePortURIsToAdd = exportMasksToUpdateOnDeviceWithStoragePorts.get(exportMask.getId());

            String addPortStep = workflow.createStepId();
            
            // Create a Step to add storage ports to the Storage View
            Workflow.Method addPortsToViewMethod = new Workflow.Method(STORAGE_VIEW_ADD_STORAGE_PORTS_METHOD, vplexSystem.getId(), 
            		exportGroupUri, exportMask.getId(), storagePortURIsToAdd);

            Workflow.Method addToViewRollbackMethod = new Workflow.Method(STORAGE_VIEW_REMOVE_STORAGE_PORTS_METHOD, vplexSystem.getId(),
            		exportGroupUri, exportMask.getId(), storagePortURIsToAdd);

            storageViewStepId = workflow.createStep("storageView",
                    String.format("Updating VPLEX Storage View StoragePorts for ExportGroup %s Mask %s", exportGroupUri, 
                    		exportMask.getMaskName()),
                    storageViewStepId, vplexSystem.getId(), vplexSystem.getSystemType(),
                    VPlexMaskingWorkflowEntryPoints.class, addPortsToViewMethod, addToViewRollbackMethod, addPortStep);
        }
        return storageViewStepId;
    }
    
    /**
     * Handles re-using an existing ViPR ExportMask for a volume export process.
     *
     * @param blockObjectMap
     *            the map of URIs to block volumes for export
     * @param vplexSystem
     *            a StorageSystem object representing the VPLEX system
     * @param exportGroup
     *            the ViPR export group
     * @param varrayUri
     *            -- NOTE the varrayUri may not be the same as the one in exportGroup
     * @param exportMasksToUpdateOnDevice
     *            collection of ExportMasks to update
     * @param exportMasksToUpdateOnDeviceWithStoragePorts
     *            a map of ExportMasks to storage ports
     * @param inits
     *            the host initiators of the host in question
     * @param allPortsFromMaskMatchForVarray
     *            a flag indicating the storage port networking status
     * @param viprExportMask
     *            the ExportMask in the ViPR database to re-use
     * @param opId
     *            the workflow step id
     */
    private void reuseExistingExportMask(Map<URI, Integer> blockObjectMap,
            StorageSystem vplexSystem, ExportGroup exportGroup, URI varrayUri,
            List<ExportMask> exportMasksToUpdateOnDevice,
            Map<URI, List<URI>> exportMasksToUpdateOnDeviceWithStoragePorts,
            List<Initiator> inits, boolean allPortsFromMaskMatchForVarray,
            ExportMask viprExportMask, String opId) {

        exportMasksToUpdateOnDevice.add(viprExportMask);
        ExportPathParams pathParams = _blockScheduler.calculateExportPathParamForVolumes(
                blockObjectMap.keySet(), exportGroup.getNumPaths(), vplexSystem.getId(), exportGroup.getId());
        if (exportGroup.getType() != null) {
            pathParams.setExportGroupType(exportGroup.getType());
        }

        // If allPortsFromMaskMatchForVarray passed in is false then assign storageports using the varray
        // This will mostly be the case where volumes are getting exported to the host for which
        // there is already exportmask in database but the storageports in that exportmask are not
        // in the passed in varray (varrayUri)
        if (!allPortsFromMaskMatchForVarray) {
            Map<URI, List<URI>> assignments = _blockScheduler.assignStoragePorts(vplexSystem, exportGroup,
                    inits, viprExportMask.getZoningMap(), pathParams, null, _networkDeviceController, varrayUri, opId);
            // Consolidate the prezoned ports with the new assignments to get the total ports needed in the mask
            if (assignments != null && !assignments.isEmpty()) {
                // Update zoning Map with these new assignments
                viprExportMask = ExportUtils.updateZoningMap(_dbClient, viprExportMask, assignments,
                        exportMasksToUpdateOnDeviceWithStoragePorts);
            }
        }
    }

    private String getComputedExportMaskName(StorageSystem storage,
            URI varrayURI, List<Initiator> initiators, String configTemplateName) {
        DataSource dataSource = ExportMaskUtils.getExportDatasource(storage, initiators, dataSourceFactory,
                configTemplateName);
        if (dataSource == null) {
            return null;
        } else {
            String vplexCluster = ConnectivityUtil.getVplexClusterForVarray(varrayURI, storage.getId(), _dbClient);
            dataSource.addProperty(CustomConfigConstants.VPLEX_CLUSTER_NUMBER, vplexCluster);

            String clusterSerialNo = VPlexUtil.getVPlexClusterSerialNumber(vplexCluster, storage);
            dataSource.addProperty(CustomConfigConstants.VPLEX_CLUSTER_SERIAL_NUMBER, clusterSerialNo);
        }
        return customConfigHandler.getComputedCustomConfigValue(configTemplateName, storage.getSystemType(), dataSource);
    }
    
    /**
     * Returns a Workflow.Method for registering initiators.
     *
     * @param initiatorUris the initiator URIs to check for registration
     * @param vplexURI the VPLEX URI
     * @param vplexClusterName the VPLEX cluster name (like cluster-1 or cluster-2)
     *
     * @return The register initiators method.
     */
    private Workflow.Method registerInitiatorsMethod(List<URI> initiatorUris, URI vplexURI, String vplexClusterName ) {
        return new Workflow.Method(REGISTER_INITIATORS_METHOD_NAME, initiatorUris, vplexURI, vplexClusterName);
    }

     
    
    /**
     * Create a Workflow Method for deleteStorageView(). Args must match deleteStorageView
     * except for extra stepId arg.
     *
     * @param vplexURI vplex
     * @param exportGroupURI export group
     * @param exportMaskURI export mask
     * @param isRollbackStep is this a rollback step?
     * @return
     */
    private Workflow.Method deleteStorageViewMethod(URI vplexURI, URI exportGroupURI, URI exportMaskURI) {
        return new Workflow.Method(DELETE_STORAGE_VIEW, vplexURI, exportGroupURI, exportMaskURI);
    }
    
    private Workflow.Method rollbackMethodNullMethod() {
        return new Workflow.Method(ROLLBACK_METHOD_NULL);
    }            
    
    private Workflow.Method zoneRollbackMethod(URI exportGroupURI, String rollbackContextKey) {
        return new Workflow.Method("zoneRollback", exportGroupURI, rollbackContextKey);
    }

    /**
     * TODO: Remove - it's in NetworkDeviceController
     * Args should match zoneRollbackMethod above except for taskId.
     *
     * @param exportGroupURI
     * @param rollbackContextKey
     * @param taskId
     * @throws DeviceControllerException
     */
    public void zoneRollback(URI exportGroupURI, String rollbackContextKey, String taskId) throws DeviceControllerException {
        _networkDeviceController.zoneRollback(exportGroupURI, rollbackContextKey, taskId);
    }        
    
     /**
     * Validates if there is a HLU conflict between cluster volumes and the host volumes.
     * 
     * @param storage the storage
     * @param exportGroup the export group
     * @param newInitiatorURIs the host initiators to be added to the export group
     **/
    @Override
    public void checkForConsistentLunViolation(StorageSystem storage, ExportGroup exportGroup, List<URI> newInitiatorURIs) {

        Map<String, Integer> volumeHluPair = new HashMap<String, Integer>();
        // For 'add host to cluster' operation, validate and fail beforehand if HLU conflict is detected
        if (!exportGroup.checkInternalFlags(Flag.INTERNAL_OBJECT) && exportGroup.forCluster() && exportGroup.getVolumes() != null) {
            // get HLUs from ExportGroup as these are the volumes that will be exported to new Host.
            Collection<String> egHlus = exportGroup.getVolumes().values();
            Collection<Integer> clusterHlus = Collections2.transform(egHlus, CommonTransformerFunctions.FCTN_STRING_TO_INTEGER);
            Set<Integer> newHostUsedHlus;
            try {
                newHostUsedHlus = findHLUsForInitiators(storage, exportGroup, newInitiatorURIs, false);
            } catch (Exception e) {
                String errMsg = "Encountered an error when attempting to query used HLUs for initiators: " + e.getMessage();
                _log.error(errMsg, e);
                throw VPlexApiException.exceptions.hluRetrievalFailed(errMsg, e);
            }
            // newHostUsedHlus now will contain the intersection of the two Set of HLUs which are conflicting one's
            newHostUsedHlus.retainAll(clusterHlus);
            if (!newHostUsedHlus.isEmpty()) {
                _log.info("Conflicting HLUs: {}", newHostUsedHlus);
                for (Map.Entry<String, String> entry : exportGroup.getVolumes().entrySet()) {
                    Integer hlu = Integer.valueOf(entry.getValue());
                    if (newHostUsedHlus.contains(hlu)) {
                        volumeHluPair.put(entry.getKey(), hlu);
                    }
                }
                throw DeviceControllerException.exceptions.addHostHLUViolation(volumeHluPair);
            }
        }
    }
    
	@Override
	public void findAndUpdateFreeHLUsForClusterExport(StorageSystem storage, ExportGroup exportGroup,
			List<URI> initiatorURIs, Map<URI, Integer> volumeMap) throws Exception {
		try {
            if (!exportGroup.checkInternalFlags(Flag.INTERNAL_OBJECT) && exportGroup.forCluster()
                    && volumeMap.values().contains(ExportGroup.LUN_UNASSIGNED)
                    && ExportUtils.systemSupportsConsistentHLUGeneration(storage)) {
                _log.info("Find and update free HLUs for Cluster Export START..");
                /**
                 * Group the initiators by Host. For each Host, call device.findHLUsForInitiators() to get used HLUs.
                 * Add all hosts's HLUs to a Set.
                 * Get the maximum allowed HLU for the storage array.
                 * Calculate the free lowest available HLUs.
                 * Update the new values in the VolumeHLU Map.
                 */
                Set<Integer> usedHlus = findHLUsForInitiators(storage, exportGroup, initiatorURIs, false);
                Integer maxHLU = ExportUtils.getMaximumAllowedHLU(storage);
                Set<Integer> freeHLUs = ExportUtils.calculateFreeHLUs(usedHlus, maxHLU);

                ExportUtils.updateFreeHLUsInVolumeMap(volumeMap, freeHLUs);

                _log.info("Find and update free HLUs for Cluster Export END.");
            } else {
                _log.info("Find and update free HLUs for Cluster Export not required");
            }
        } catch (Exception e) {
            String errMsg = "Encountered an error when attempting to query used HLUs for initiators: " + e.getMessage();
            _log.error(errMsg, e);
            throw VPlexApiException.exceptions.hluRetrievalFailed(errMsg, e);
        }

	}
	
	/**
     * 
     * @param vplexStorageSystem
     * @param exportGroup
     * @param initiatorURIs
     * @param mustHaveAllPorts
     * @return
     * @throws Exception
     */
    public Set<Integer> findHLUsForInitiators(StorageSystem vplexStorageSystem, ExportGroup exportGroup, List<URI> initiatorURIs,
            boolean mustHaveAllPorts) throws Exception {
        long startTime = System.currentTimeMillis();
        Set<Integer> usedHLUs = new HashSet<Integer>();
        VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(_vplexApiFactory, vplexStorageSystem, _dbClient);

        URI srcVarrayURI = exportGroup.getVirtualArray();
        /**
         * Find used HLUs here for VPLEX local and distributed
         */
        _log.info("varray uri :{} Vplex URI :{}", srcVarrayURI, vplexStorageSystem.getId());
        String vplexClusterName = VPlexUtil.getVplexClusterName(srcVarrayURI, vplexStorageSystem.getId(), client, _dbClient);
        _log.info("vplexClusterName :{}", vplexClusterName);

        Iterator<Initiator> inits = _dbClient.queryIterativeObjects(Initiator.class, initiatorURIs);
        Boolean[] doRefresh =  new Boolean[] { new Boolean(true) };
        List<String> initiatorNames = VPlexControllerUtils.getInitiatorNames(vplexStorageSystem.getSerialNumber(), vplexClusterName, client, inits, doRefresh, _dbClient);

        List<VPlexStorageViewInfo> storageViewInfos = client.getStorageViewsContainingInitiators(
                vplexClusterName, initiatorNames);
        _log.info("initiatorNames {}", initiatorNames);
        if (exportGroup.hasAltVirtualArray(vplexStorageSystem.getId().toString())) {
            // If there is an alternate Varray entry for this Vplex, it indicates we have HA volumes.
            URI haVarrayURI = URI.create(exportGroup.getAltVirtualArrays().get(vplexStorageSystem.getId().toString()));
            _log.info("haVarrayURI :{} Vplex URI :{}", haVarrayURI, vplexStorageSystem.getId());
            String vplexHAClusterName = VPlexUtil.getVplexClusterName(haVarrayURI, vplexStorageSystem.getId(), client, _dbClient);
            _log.info("vplexHAClusterName :{}", vplexHAClusterName);

            inits = _dbClient.queryIterativeObjects(Initiator.class, initiatorURIs);
            Boolean[] doRefreshHa =  new Boolean[] { new Boolean(true) };
            List<String> haInitiatorNames = VPlexControllerUtils.getInitiatorNames(vplexStorageSystem.getSerialNumber(), vplexHAClusterName, client, inits, doRefreshHa, _dbClient);
            storageViewInfos.addAll(client.getStorageViewsContainingInitiators(
                    vplexHAClusterName, haInitiatorNames));
        }

        for (VPlexStorageViewInfo storageViewInfo : storageViewInfos) {
            if (storageViewInfo != null && !CollectionUtils.isEmpty(storageViewInfo.getWwnToHluMap())) {
                _log.info("storageViewInfo.getWwnToHluMap() :{}", storageViewInfo.getWwnToHluMap());
                usedHLUs.addAll(storageViewInfo.getWwnToHluMap().values());
            }
        }

        _log.info(String.format("HLUs found for Initiators { %s }: %s",
                Joiner.on(',').join(initiatorNames), usedHLUs));
        long totalTime = System.currentTimeMillis() - startTime;
        _log.info(String.format("find used HLUs for Initiators took %f seconds", (double) totalTime / (double) 1000));
        return usedHLUs;
    }

    @Override
    public void exportGroupChangePathParams(URI storageURI, URI exportGroupURI,
            URI blockObjectURI, String token) throws Exception {
        ExportOrchestrationTask taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
        ExportPathUpdater updater = new ExportPathUpdater(_dbClient);
        try {
            String workflowKey = "exportGroupChangePathParams";
            if (_workflowService.hasWorkflowBeenCreated(token, workflowKey)) {
                return;
            }
            Workflow workflow = _workflowService.getNewWorkflow(
                    MaskingWorkflowEntryPoints.getInstance(),
                    workflowKey, true, token);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class,
                    storageURI);

            BlockObject bo = Volume.fetchExportMaskBlockObject(_dbClient, blockObjectURI);
            _log.info(String.format("Changing path parameters for volume %s (%s)",
                    bo.getLabel(), bo.getId()));

            updater.generateExportGroupChangePathParamsWorkflow(workflow, _blockScheduler, this,
                    storage, exportGroup, bo, token);

            if (!workflow.getAllStepStatus().isEmpty()) {
                _log.info("The changePathParams workflow has {} steps. Starting the workflow.",
                        workflow.getAllStepStatus().size());
                workflow.executePlan(taskCompleter, "Update the export group on all export masks successfully.");
                _workflowService.markWorkflowBeenCreated(token, workflowKey);
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception ex) {
            _log.error("ExportGroup Orchestration failed.", ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(ex.getMessage(), ex);
            VPlexControllerUtils.failStep(taskCompleter, token, serviceError, _dbClient);
        }
    }

    /**
     * Given a list of Initiators, generates a string for logging of all
     * the port WWNs.
     *
     * @param initiators
     * @return String of WWNs
     */
    private String getInitiatorsWwnsString(List<Initiator> initiators) {
        StringBuilder buf = new StringBuilder();
        for (Initiator initiator : initiators) {
            buf.append(initiator.getInitiatorPort().toString() + " ");
        }
        return buf.toString();
    }
    
    /**
     * Determine the targets we should remove for the initiators being removed.
     * Normally one or more targets will be removed for each initiator that
     * is removed according to the zoning map.
     *
     * @param exportMask
     *            The export mask
     * @param hostInitiatorURIs
     *            Initiaror URI list
     */
    private List<URI> getTargetURIs(ExportMask exportMask, List<URI> hostInitiatorURIs) {

        List<URI> targetURIs = new ArrayList<URI>();
        if (exportMask.getZoningMap() != null && !exportMask.getZoningMap().isEmpty()) {
            for (URI initiatorURI : hostInitiatorURIs) {
                StringSet targets = exportMask.getZoningMap().get(initiatorURI.toString());
                if (targets == null) {
                    continue; // no targets for this initiator
                }
                for (String target : targets) {
                    // Make sure this target is not in any other initiator's entry
                    // in the zoning map that is not being removed.
                    boolean found = false;
                    for (String initiatorX : exportMask.getZoningMap().keySet()) {
                        if (hostInitiatorURIs.contains(URI.create(initiatorX))) {
                            continue;
                        }
                        StringSet targetsX = exportMask.getZoningMap().get(initiatorX.toString());
                        if (targetsX != null && targetsX.contains(target)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        targetURIs.add(URI.create(target));
                    }
                }
            }
        }
        return targetURIs;
    }
    
	@Override
	public BlockStorageDevice getDevice() {
		return null;
	}

}
