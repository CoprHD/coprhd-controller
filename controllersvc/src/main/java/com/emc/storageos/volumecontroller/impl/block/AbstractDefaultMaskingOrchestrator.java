/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.DbModelClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportGroupRemoveVolumesCleanupCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.volumecontroller.placement.ExportPathUpdater;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;

/**
 * This class has two roles:
 * <li>Provide a place for code that has workflow step generation</li>
 * <li>Provide a place for common code to be used across the MaskingOrchestrators</li>
 */
abstract public class AbstractDefaultMaskingOrchestrator {
    protected static final Logger _log =
            LoggerFactory.getLogger(AbstractDefaultMaskingOrchestrator.class);
    public static final String EXPORT_GROUP_MASKING_TASK = "export-masking-task";
    public static final String EXPORT_GROUP_CLEANUP_TASK = "export-group-cleanup-task";
    public static final String EXPORT_MASK_CLEANUP_TASK = "export-mask-cleanup-task";
    public static final String EXPORT_GROUP_ZONING_TASK = "zoning-task";
    public static final String EXPORT_GROUP_UPDATE_ZONING_MAP = "update-zoning-map";
    public static final String IGNORE_TASK = "ignore";
    public static final String UNASSOCIATED = "UNASSOCIATED";

    protected DbClient _dbClient;
    protected static volatile BlockStorageScheduler _blockScheduler;
    protected WorkflowService _workflowService;
    protected NetworkDeviceController _networkDeviceController;
    @Autowired
    private DataSourceFactory dataSourceFactory;
    @Autowired
    protected CustomConfigHandler customConfigHandler;
    @Autowired
    protected DbModelClient dbModelClient;

    /**
     * Simple class to hold two values that would be associated with
     * the call to generateExportMaskCreateWorkflow.
     */
    public class GenExportMaskCreateWorkflowResult {
        private URI maskURI;
        private String stepId;

        public GenExportMaskCreateWorkflowResult(URI maskURI, String stepId) {
            this.maskURI = maskURI;
            this.stepId = stepId;
        }

        public String getStepId() {
            return stepId;
        }

        public URI getMaskURI() {
            return maskURI;
        }
    }

    /**
     * Inner helper class that will run the methods to process ViPR initiators, so that can be
     * used to reference initiators on the array side, which typically references them by name
     * (WWN or IQN).
     *
     * Also, there are data-structures to map initiators to compute resources, which may be
     * needed for the masking orchestrators to determine how initiators should be placed into
     * existing ExportMasks or how new ExportMasks should be created.
     */
    public class InitiatorHelper {
        private Map<String, List<URI>> resourceToInitiators;
        private List<Initiator> initiators;
        private List<URI> initiatorURIs;

        // A map of compute resources to their ports. ListMultimap is used for shorthand of a single
        // compute resource to a collection of ports
        private ListMultimap<String, String> computeResourceToPortNames;

        // A mapping of port name (11:22:33:44:55:66:77:88) to initiator URI
        private Map<String, URI> portNameToInitiatorURI;

        // The port names that are part of this request.
        private List<String> portNames;

        public InitiatorHelper(Collection<URI> initiatorURIs) {
            this.initiatorURIs = new ArrayList<>(initiatorURIs);
        }

        public InitiatorHelper(List<Initiator> initiators) {
            this.initiatorURIs = new ArrayList<>();
            for (Initiator initiator : initiators) {
                this.initiatorURIs.add(initiator.getId());
            }
        }

        public InitiatorHelper process(ExportGroup exportGroup) {
            this.computeResourceToPortNames = ArrayListMultimap.create();
            this.portNameToInitiatorURI = new HashMap<>();
            this.portNames = new ArrayList<>();

            // Pre-load the initiators that are expected as part of this creation request.
            initiators = _dbClient.queryObject(Initiator.class, initiatorURIs);

            // Populate data structures to track initiators
            processInitiators(exportGroup, initiators, portNames, portNameToInitiatorURI, computeResourceToPortNames);

            // A map of compute resources to initiators
            resourceToInitiators = mapInitiatorsToComputeResource(exportGroup, initiatorURIs);
            return this;
        }

        public Map<String, List<URI>> getResourceToInitiators() {
            return resourceToInitiators;
        }

        public List<Initiator> getInitiators() {
            return initiators;
        }

        public List<URI> getInitiatorURIs() {
            return initiatorURIs;
        }

        public ListMultimap<String, String> getComputeResourceToPortNames() {
            return computeResourceToPortNames;
        }

        public Map<String, URI> getPortNameToInitiatorURI() {
            return portNameToInitiatorURI;
        }

        public List<String> getPortNames() {
            return portNames;
        }
    }

    public void setNetworkDeviceController(
            NetworkDeviceController networkDeviceController) {
        this._networkDeviceController = networkDeviceController;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this._workflowService = workflowService;
    }

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    public void setBlockScheduler(BlockStorageScheduler blockScheduler) {
        _blockScheduler = blockScheduler;
    }

    /**
     * Select volumes from an export group that resides on a given storage array
     * 
     * 
     * @param exportGroup
     * @param storageURI
     * @return
     * @throws java.io.IOException
     */
    protected Map<URI, Integer> selectExportMaskVolumes(ExportGroup exportGroup,
            URI storageURI)
            throws IOException {
        Map<URI, Integer> volumeMap = new HashMap<URI, Integer>();
        _log.info("Export Group Volumes {} ", Joiner.on(",").join(exportGroup.getVolumes().entrySet()));
        for (String uri : exportGroup.getVolumes().keySet()) {
            URI volUri = URI.create(uri);
            BlockObject blockObj = Volume.fetchExportMaskBlockObject(_dbClient, volUri);
            if (blockObj == null) {
                _log.warn("Volume {} could not be found in DB", volUri.toString());
                continue;
            }
            _log.info("Volume {} storage {}", volUri, blockObj.getStorageController());
            if (!blockObj.getStorageController().equals(storageURI)) {
                continue;
            }
            volumeMap.put(volUri, Integer.valueOf(exportGroup.getVolumes().get(volUri.toString())));
        }
        return volumeMap;
    }

    protected boolean useComputedMaskName() {
        return false;
    }

    protected String getMaskingCustomConfigTypeName(String exportType) {
        return null;
    }

    protected String getComputedExportMaskName(StorageSystem storage, ExportGroup exportGroup,
            List<Initiator> initiators) {
        String configTemplateName = getMaskingCustomConfigTypeName(exportGroup.getType());
        DataSource dataSource = ExportMaskUtils.getExportDatasource(storage, initiators, dataSourceFactory, configTemplateName);
        if (dataSource == null) {
            return null;
        }

        return customConfigHandler.getComputedCustomConfigValue(configTemplateName, storage.getSystemType(), dataSource);
    }

    /**
     * Creates an ExportMask Workflow that generates a new ExportMask in an existing ExportGroup.
     *
     * @param workflow
     * @param previousStep
     * @param storage
     * @param exportGroup
     * @param initiatorURIs
     * @param volumeMap
     * @param token
     * @return URI of the new ExportMask
     * @throws Exception
     */
    public GenExportMaskCreateWorkflowResult generateExportMaskCreateWorkflow(Workflow workflow,
            String previousStep,
            StorageSystem storage,
            ExportGroup exportGroup,
            List<URI> initiatorURIs,
            Map<URI, Integer> volumeMap,
            String token) throws Exception {
        URI exportGroupURI = exportGroup.getId();
        URI storageURI = storage.getId();

        // Create and initialize the Export Mask. This involves assigning and
        // allocating the Storage Ports (targets).
        List<Initiator> initiators =
                _dbClient.queryObject(Initiator.class, initiatorURIs);
        ExportPathParams pathParams = _blockScheduler.calculateExportPathParamForVolumes(
                volumeMap.keySet(), exportGroup.getNumPaths(), storage.getId(), exportGroup.getId());
        if (exportGroup.getType() != null) {
            pathParams.setExportGroupType(exportGroup.getType());
        }
        if (exportGroup.getZoneAllInitiators()) {
            pathParams.setAllowFewerPorts(true);
        }

        Map<URI, List<URI>> assignments = _blockScheduler.assignStoragePorts(storage, exportGroup,
                initiators, null, pathParams, volumeMap.keySet(), _networkDeviceController, exportGroup.getVirtualArray(), token);
        List<URI> targets = BlockStorageScheduler.getTargetURIsFromAssignments(assignments);

        String maskName = useComputedMaskName() ? getComputedExportMaskName(storage, exportGroup, initiators) : null;

        ExportMask exportMask = ExportMaskUtils.initializeExportMask(storage, exportGroup,
                initiators, volumeMap, targets, assignments, maskName, _dbClient);
        List<BlockObject> vols = new ArrayList<BlockObject>();
        for (URI boURI : volumeMap.keySet()) {
            BlockObject bo = BlockObject.fetch(_dbClient, boURI);
            vols.add(bo);
        }
        exportMask.addToUserCreatedVolumes(vols);
        _dbClient.persistObject(exportMask);
        // Make a new TaskCompleter for the exportStep. It has only one subtask.
        // This is due to existing requirements in the doExportGroupCreate completion
        // logic.
        String maskingStep = workflow.createStepId();
        ExportTaskCompleter exportTaskCompleter = new ExportMaskCreateCompleter(
                exportGroupURI, exportMask.getId(), initiatorURIs, volumeMap,
                maskingStep);

        Workflow.Method maskingExecuteMethod = new Workflow.Method(
                "doExportGroupCreate", storageURI, exportGroupURI, volumeMap,
                initiatorURIs, exportMask.getId(), targets, exportTaskCompleter);

        Workflow.Method maskingRollbackMethod = new Workflow.Method(
                "rollbackExportGroupCreate",
                storageURI, exportGroupURI, exportMask.getId(), maskingStep);

        maskingStep = workflow.createStep(EXPORT_GROUP_MASKING_TASK,
                String.format("Creating mask %s (%s)",
                        exportMask.getMaskName(), exportMask.getId().toString()),
                previousStep, storageURI, storage.getSystemType(),
                MaskingWorkflowEntryPoints.class, maskingExecuteMethod,
                maskingRollbackMethod, maskingStep);

        return new GenExportMaskCreateWorkflowResult(exportMask.getId(), maskingStep);
    }

    public String generateExportMaskDeleteWorkflow(Workflow workflow,
            String previousStep,
            StorageSystem storage,
            ExportGroup exportGroup,
            ExportMask exportMask,
            ExportTaskCompleter taskCompleter)
            throws Exception {
        URI exportGroupURI = exportGroup.getId();
        URI exportMaskURI = exportMask.getId();
        URI storageURI = storage.getId();

        String maskingStep = workflow.createStepId();
        ExportTaskCompleter exportTaskCompleter = null;
        if (null != taskCompleter) {
            exportTaskCompleter = taskCompleter;
            exportTaskCompleter.setOpId(maskingStep);
        } else {
            exportTaskCompleter = new ExportMaskDeleteCompleter(exportGroupURI,
                    exportMaskURI, maskingStep);
        }

        Workflow.Method maskingExecuteMethod = new Workflow.Method(
                "doExportGroupDelete", storageURI, exportGroupURI, exportMaskURI,
                exportTaskCompleter);

        maskingStep = workflow.createStep(EXPORT_GROUP_MASKING_TASK,
                String.format("Deleting mask %s (%s)", exportMask.getMaskName(),
                        exportMask.getId().toString()),
                previousStep, storageURI, storage.getSystemType(),
                MaskingWorkflowEntryPoints.class, maskingExecuteMethod, null,
                maskingStep);

        return maskingStep;
    }

    public String generateExportMaskAddVolumesWorkflow(Workflow workflow,
            String previousStep,
            StorageSystem storage,
            ExportGroup exportGroup,
            ExportMask exportMask,
            Map<URI, Integer> volumesToAdd)
            throws Exception {
        URI exportGroupURI = exportGroup.getId();
        URI exportMaskURI = exportMask.getId();
        URI storageURI = storage.getId();

        String maskingStep = workflow.createStepId();
        ExportTaskCompleter exportTaskCompleter = new ExportMaskAddVolumeCompleter(
                exportGroupURI, exportMask.getId(), volumesToAdd, maskingStep);

        Workflow.Method maskingExecuteMethod = new Workflow.Method(
                "doExportGroupAddVolumes", storageURI, exportGroupURI, exportMaskURI,
                volumesToAdd, exportTaskCompleter);

        Workflow.Method maskingRollbackMethod = new Workflow.Method(
                "rollbackExportGroupAddVolumes", storageURI, exportGroupURI,
                exportMaskURI, volumesToAdd, maskingStep);

        maskingStep = workflow.createStep(EXPORT_GROUP_MASKING_TASK,
                String.format("Adding volumes to mask %s (%s)",
                        exportMask.getMaskName(), exportMask.getId().toString()),
                previousStep, storageURI, storage.getSystemType(),
                MaskingWorkflowEntryPoints.class, maskingExecuteMethod,
                maskingRollbackMethod, maskingStep);

        return maskingStep;
    }

    public String generateExportMaskRemoveVolumesWorkflow(Workflow workflow,
            String previousStep,
            StorageSystem storage,
            ExportGroup exportGroup,
            ExportMask exportMask,
            List<URI> volumesToRemove,
            ExportTaskCompleter
            completer)
            throws Exception {
        URI exportGroupURI = exportGroup.getId();
        URI exportMaskURI = exportMask.getId();
        URI storageURI = storage.getId();

        String maskingStep = workflow.createStepId();
        ExportTaskCompleter exportTaskCompleter;
        if (completer != null) {
            exportTaskCompleter = completer;
            exportTaskCompleter.setOpId(maskingStep);
        } else {
            exportTaskCompleter =
                    new ExportMaskRemoveVolumeCompleter(exportGroupURI, exportMask.getId(),
                            volumesToRemove, maskingStep);
        }

        Workflow.Method maskingExecuteMethod = new Workflow.Method(
                "doExportGroupRemoveVolumes", storageURI, exportGroupURI,
                exportMaskURI, volumesToRemove, exportTaskCompleter);

        maskingStep = workflow.createStep(EXPORT_GROUP_MASKING_TASK,
                String.format("Removing volumes from mask %s (%s)",
                        exportMask.getMaskName(), exportMask.getId().toString()),
                previousStep, storageURI, storage.getSystemType(),
                MaskingWorkflowEntryPoints.class, maskingExecuteMethod, null,
                maskingStep);

        return maskingStep;
    }

    public String generateExportGroupRemoveVolumesCleanup(Workflow workflow,
            String previousStep,
            StorageSystem storage,
            ExportGroup exportGroup,
            List<URI> volumeURIs) {
        URI exportGroupURI = exportGroup.getId();
        URI storageURI = storage.getId();

        String cleanupStep = workflow.createStepId();
        ExportTaskCompleter exportTaskCompleter =
                new ExportGroupRemoveVolumesCleanupCompleter(exportGroupURI, cleanupStep);

        Workflow.Method cleanupExecuteMethod = new Workflow.Method(
                "doExportGroupRemoveVolumesCleanup", storageURI, exportGroupURI,
                volumeURIs, exportTaskCompleter);

        cleanupStep = workflow.createStep(EXPORT_GROUP_CLEANUP_TASK,
                String.format("Cleanup of volumes from export group %s",
                        exportGroup.getLabel()),
                previousStep, storageURI, storage.getSystemType(),
                MaskingWorkflowEntryPoints.class, cleanupExecuteMethod, null,
                cleanupStep);

        return cleanupStep;
    }

    public String generateExportMaskAddInitiatorsWorkflow(Workflow workflow,
            String previousStep,
            StorageSystem storage,
            ExportGroup exportGroup,
            ExportMask exportMask,
            List<URI> initiatorURIs,
            Set<URI> newVolumeURIs,
            String token)
            throws Exception {
        URI exportGroupURI = exportGroup.getId();
        URI exportMaskURI = exportMask.getId();
        URI storageURI = storage.getId();
        List<URI> newTargetURIs = new ArrayList<>();

        // Only update the ports of a mask that we created.
        List<Initiator> initiators =
                _dbClient.queryObject(Initiator.class, initiatorURIs);

        // Allocate any new ports that are required for the initiators
        // and update the zoning map in the exportMask.
        Collection<URI> volumeURIs = (exportMask.getVolumes() == null) ? newVolumeURIs :
                (Collection<URI>) (Collections2.transform(exportMask.getVolumes().keySet(),
                        CommonTransformerFunctions.FCTN_STRING_TO_URI));
        ExportPathParams pathParams = _blockScheduler.calculateExportPathParamForVolumes(
                volumeURIs, exportGroup.getNumPaths(), storageURI, exportGroupURI);
        if (exportGroup.getType() != null) {
            pathParams.setExportGroupType(exportGroup.getType());
        }
        Map<URI, List<URI>> assignments = _blockScheduler.assignStoragePorts(storage, exportGroup, initiators,
                exportMask.getZoningMap(), pathParams, volumeURIs, _networkDeviceController, exportGroup.getVirtualArray(), token);
        newTargetURIs = BlockStorageScheduler.getTargetURIsFromAssignments(assignments);
        exportMask.addZoningMap(BlockStorageScheduler.getZoneMapFromAssignments(assignments));
        _dbClient.persistObject(exportMask);

        String maskingStep = workflow.createStepId();
        ExportTaskCompleter exportTaskCompleter = new ExportMaskAddInitiatorCompleter(
                exportGroupURI, exportMask.getId(), initiatorURIs, newTargetURIs,
                maskingStep);

        Workflow.Method maskingExecuteMethod = new Workflow.Method(
                "doExportGroupAddInitiators", storageURI, exportGroupURI,
                exportMaskURI, initiatorURIs, newTargetURIs, exportTaskCompleter);

        Workflow.Method rollbackMethod = new Workflow.Method(
                "rollbackExportGroupAddInitiators", storageURI, exportGroupURI,
                exportMaskURI, initiatorURIs, maskingStep);

        maskingStep = workflow.createStep(EXPORT_GROUP_MASKING_TASK,
                String.format("Adding initiators to mask %s (%s)",
                        exportMask.getMaskName(), exportMask.getId().toString()),
                previousStep, storageURI, storage.getSystemType(),
                MaskingWorkflowEntryPoints.class, maskingExecuteMethod,
                rollbackMethod, maskingStep);

        return maskingStep;
    }

    protected String generateExportMaskRemoveInitiatorsWorkflow(Workflow workflow, String previousStep,
            StorageSystem storage, ExportGroup exportGroup, ExportMask exportMask,
            List<URI> initiatorURIs, boolean removeTargets) throws Exception {
        return generateExportMaskRemoveInitiatorsWorkflow(workflow, previousStep, storage, exportGroup, exportMask, initiatorURIs,
                removeTargets, null);
    }

    public String generateExportMaskRemoveInitiatorsWorkflow(Workflow workflow,
            String previousStep,
            StorageSystem storage,
            ExportGroup exportGroup,
            ExportMask exportMask,
            List<URI> initiatorURIs,
            boolean removeTargets,
            ExportTaskCompleter completer)
            throws Exception {
        URI exportGroupURI = exportGroup.getId();
        URI exportMaskURI = exportMask.getId();
        URI storageURI = storage.getId();

        String maskingStep = workflow.createStepId();
        ExportTaskCompleter exportTaskCompleter = null;
        if (completer != null) {
            exportTaskCompleter = completer;
            exportTaskCompleter.setOpId(maskingStep);
        } else {
            exportTaskCompleter = new ExportMaskRemoveInitiatorCompleter(exportGroupURI,
                    exportMask.getId(), initiatorURIs, maskingStep);
        }

        Workflow.Method maskingExecuteMethod = new Workflow.Method(
                "doExportGroupRemoveInitiators", storageURI, exportGroupURI,
                exportMaskURI, initiatorURIs, removeTargets, exportTaskCompleter);

        maskingStep = workflow.createStep(EXPORT_GROUP_MASKING_TASK,
                String.format("Removing initiators from %s (%s)",
                        exportMask.getMaskName(), exportMask.getId().toString()),
                previousStep, storageURI, storage.getSystemType(),
                MaskingWorkflowEntryPoints.class, maskingExecuteMethod,
                null, maskingStep);

        return maskingStep;
    }

    /**
     * Creates a zoning workflow for a new ExportGroup.
     * There is an optional last parameter - the zoningStep id.
     *
     * @param workflow
     * @param previousStep
     * @param exportGroup
     * @param volumeMap
     * @return zoningStep - String id of zoning step
     * @throws WorkflowException
     */
    protected String generateZoningCreateWorkflow(Workflow workflow,
            String previousStep,
            ExportGroup exportGroup,
            List<URI> exportMaskURIs,
            Map<URI, Integer> volumeMap) {
        String zoningStep = workflow.createStepId();
        return generateZoningCreateWorkflow(workflow, previousStep,
                exportGroup, exportMaskURIs, volumeMap, zoningStep);
    }

    protected String generateZoningCreateWorkflow(Workflow workflow,
            String previousStep,
            ExportGroup exportGroup,
            List<URI> exportMaskURIs,
            Map<URI, Integer> volumeMap, String zoningStep)
            throws WorkflowException {
        URI exportGroupURI = exportGroup.getId();
        // Create two steps, one for Zoning, one for the ExportGroup actions.
        // This step is for zoning. It is not specific to a single NetworkSystem,
        // as it will look at all the initiators and targets and compute the zones
        // required (which might be on multiple NetworkSystems.)

        Workflow.Method zoningExecuteMethod = _networkDeviceController
                .zoneExportMasksCreateMethod(exportGroupURI, exportMaskURIs, new HashSet(volumeMap.keySet()));
        Workflow.Method zoningRollbackMethod = _networkDeviceController
                .zoneRollbackMethod(exportGroupURI, zoningStep);

        zoningStep = workflow.createStep(
                (previousStep == null ? EXPORT_GROUP_ZONING_TASK : null),
                "Zoning subtask for export-group: " + exportGroupURI,
                previousStep, NullColumnValueGetter.getNullURI(),
                "network-system", _networkDeviceController.getClass(),
                zoningExecuteMethod, zoningRollbackMethod, zoningStep);

        return zoningStep;
    }

    protected String generateZoningDeleteWorkflow(Workflow workflow,
            String previousStep,
            ExportGroup exportGroup,
            List<ExportMask> exportMasks)
            throws WorkflowException {
        URI exportGroupURI = exportGroup.getId();
        List<URI> exportMaskURIs = new ArrayList<URI>();
        List<URI> volumeURIs = new ArrayList<URI>();
        for (ExportMask mask : exportMasks) {
            exportMaskURIs.add(mask.getId());
            volumeURIs.addAll(ExportMaskUtils.getVolumeURIs(mask));
        }

        String zoningStep = workflow.createStepId();

        Workflow.Method zoningExecuteMethod = _networkDeviceController
                .zoneExportMasksDeleteMethod(exportGroupURI, exportMaskURIs, volumeURIs);

        zoningStep = workflow.createStep(
                (previousStep == null ? EXPORT_GROUP_ZONING_TASK : null),
                "Zoning subtask for export-group: " + exportGroupURI,
                previousStep, NullColumnValueGetter.getNullURI(),
                "network-system", _networkDeviceController.getClass(),
                zoningExecuteMethod, null, zoningStep);

        return zoningStep;
    }

    public String generateZoningAddInitiatorsWorkflow(Workflow workflow,
            String previousStep,
            ExportGroup exportGroup,
            Map<URI, List<URI>> exportMasksToInitiators)
            throws WorkflowException {
        URI exportGroupURI = exportGroup.getId();
        String zoningStep = workflow.createStepId();

        Workflow.Method zoningExecuteMethod = _networkDeviceController
                .zoneExportAddInitiatorsMethod(exportGroupURI, exportMasksToInitiators);

        Workflow.Method zoningRollbackMethod = _networkDeviceController
                .zoneExportRemoveInitiatorsMethod(exportGroupURI, exportMasksToInitiators);

        zoningStep = workflow.createStep(
                (previousStep == null ? EXPORT_GROUP_ZONING_TASK : null),
                "Zoning subtask for export-group: " + exportGroupURI,
                previousStep, NullColumnValueGetter.getNullURI(),
                "network-system", _networkDeviceController.getClass(),
                zoningExecuteMethod, zoningRollbackMethod, zoningStep);

        return zoningStep;
    }

    public String generateZoningRemoveInitiatorsWorkflow(Workflow workflow,
            String previousStep,
            ExportGroup exportGroup,
            Map<URI, List<URI>> exportMasksToInitiators)
            throws WorkflowException {
        URI exportGroupURI = exportGroup.getId();
        String zoningStep = workflow.createStepId();

        Workflow.Method zoningExecuteMethod = _networkDeviceController
                .zoneExportRemoveInitiatorsMethod(exportGroupURI, exportMasksToInitiators);

        zoningStep = workflow.createStep(
                (previousStep == null ? EXPORT_GROUP_ZONING_TASK : null),
                "Zoning subtask for export-group: " + exportGroupURI,
                previousStep, NullColumnValueGetter.getNullURI(),
                "network-system", _networkDeviceController.getClass(),
                zoningExecuteMethod, null, zoningStep);

        return zoningStep;
    }

    protected String generateZoningAddVolumesWorkflow(Workflow workflow,
            String previousStep,
            ExportGroup exportGroup,
            List<ExportMask> exportMasks,
            List<URI> volumeURIs)
            throws WorkflowException {
        URI exportGroupURI = exportGroup.getId();
        List<URI> exportMaskURIs = new ArrayList<URI>();
        for (ExportMask mask : exportMasks) {
            exportMaskURIs.add(mask.getId());
        }

        String zoningStep = workflow.createStepId();

        Workflow.Method zoningExecuteMethod = _networkDeviceController.zoneExportAddVolumesMethod(
                exportGroupURI, exportMaskURIs, volumeURIs);

        zoningStep = workflow.createStep(
                (previousStep == null ? EXPORT_GROUP_ZONING_TASK : null),
                "Zoning subtask for export-group: " + exportGroupURI,
                previousStep, NullColumnValueGetter.getNullURI(),
                "network-system", _networkDeviceController.getClass(),
                zoningExecuteMethod, null, zoningStep);

        return zoningStep;
    }

    protected String generateZoningRemoveVolumesWorkflow(Workflow workflow,
            String previousStep,
            ExportGroup exportGroup,
            List<ExportMask> exportMasks,
            Collection<URI> volumeURIs)
            throws WorkflowException {
        URI exportGroupURI = exportGroup.getId();
        List<URI> exportMaskURIs = new ArrayList<URI>();
        for (ExportMask mask : exportMasks) {
            exportMaskURIs.add(mask.getId());
        }

        String zoningStep = workflow.createStepId();

        Workflow.Method zoningExecuteMethod =
                _networkDeviceController.zoneExportRemoveVolumesMethod(exportGroupURI,
                        exportMaskURIs, volumeURIs);

        zoningStep = workflow.createStep(
                (previousStep == null ? EXPORT_GROUP_ZONING_TASK : null),
                "Zoning subtask for export-group: " + exportGroupURI,
                previousStep, NullColumnValueGetter.getNullURI(),
                "network-system", _networkDeviceController.getClass(),
                zoningExecuteMethod, null, zoningStep);

        return zoningStep;
    }

    /**
     * Generate the sequence of workflow based on the device type for addInitiators operation in exportmask.
     * This is default implementation.
     * If there is any device specific implementation, we should override this method and implement
     * device specific logic.
     *
     * @param workflow
     * @param previousStep
     * @param storage
     * @param exportGroup
     * @param mask
     * @param initiatorsURIs
     * @param maskToInitiatorsMap
     * @param token
     * @throws
     */
    public String generateDeviceSpecificAddInitiatorWorkFlow(Workflow workflow,
            String previousStep, StorageSystem storage, ExportGroup exportGroup, ExportMask mask,
            List<URI> initiatorsURIs, Map<URI, List<URI>> maskToInitiatorsMap,
            String token) throws Exception {
        String zoningStep = generateZoningAddInitiatorsWorkflow(workflow, previousStep,
                exportGroup, maskToInitiatorsMap);

        return generateExportMaskAddInitiatorsWorkflow(workflow, zoningStep, storage,
                exportGroup, mask, initiatorsURIs, null, token);
    }

    /**
     * Generates device specific sequence of workflow to addVolumes in exportmask.
     *
     * @param workflow
     * @param attachGroupSnapshot
     * @param storage
     * @param exportGroup
     * @param mask
     * @param volumesToAdd
     * @param volumeURIs
     * @return workflow stepId: exportmask stepId.
     * @throws Exception
     */
    public String generateDeviceSpecificAddVolumeWorkFlow(Workflow workflow,
            String attachGroupSnapshot, StorageSystem storage, ExportGroup exportGroup,
            ExportMask mask, Map<URI, Integer> volumesToAdd, List<URI> volumeURIs) throws Exception {
        List<ExportMask> masks = new ArrayList<ExportMask>();
        masks.add(mask);
        String zoningStep = generateZoningAddVolumesWorkflow(workflow, attachGroupSnapshot,
                exportGroup, masks, volumeURIs);
        return generateExportMaskAddVolumesWorkflow(workflow, zoningStep, storage, exportGroup,
                mask, volumesToAdd);
    }

    /**
     * Generates Device specific workflow step to create exportmask.
     *
     * @param workflow
     * @param previousStepId
     * @param storage
     * @param exportGroup
     * @param hostInitiators
     * @param volumeMap
     * @param token
     * @return
     * @throws Exception
     */
    public GenExportMaskCreateWorkflowResult
            generateDeviceSpecificExportMaskCreateWorkFlow(Workflow workflow,
                    String previousStepId,
                    StorageSystem storage,
                    ExportGroup exportGroup,
                    List<URI> hostInitiators,
                    Map<URI, Integer> volumeMap,
                    String token) throws Exception {
        return generateExportMaskCreateWorkflow(workflow, previousStepId, storage, exportGroup,
                hostInitiators, volumeMap, token);
    }

    /**
     * Generates Device specific workflow step to create zoning in exportmask.
     *
     * @param workflow
     * @param previousStepId
     * @param exportGroup
     * @param exportMaskList
     * @param overallVolumeMap
     */
    public String generateDeviceSpecificZoningCreateWorkflow(Workflow workflow,
            String previousStepId, ExportGroup exportGroup, List<URI> exportMaskList,
            Map<URI, Integer> overallVolumeMap) {
        return generateZoningCreateWorkflow(workflow, previousStepId, exportGroup, exportMaskList, overallVolumeMap);
    }

    /**
     * Generates Device specific workflow step to addInitiators in exportMask.
     *
     * @param workflow
     * @param zoningGroupId
     * @param storage
     * @param exportGroup
     * @param mask
     * @param newInitiators
     * @param token
     * @throws Exception
     */
    public String generateDeviceSpecificExportMaskAddInitiatorsWorkflow(Workflow workflow,
            String zoningGroupId, StorageSystem storage, ExportGroup exportGroup,
            ExportMask mask, List<URI> newInitiators, String token) throws Exception {
        return generateExportMaskAddInitiatorsWorkflow(workflow, zoningGroupId, storage,
                exportGroup, mask, newInitiators, null, token);
    }

    /**
     * Generates device specific workflow step to do zoning for addInitiators in exportmask.
     *
     * @param workflow
     * @param object
     * @param exportGroup
     * @param zoneMasksToInitiatorsURIs
     */
    public String generateDeviceSpecificZoningAddInitiatorsWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup,
            Map<URI, List<URI>> zoneMasksToInitiatorsURIs) {
        return generateZoningAddInitiatorsWorkflow(workflow, previousStep, exportGroup,
                zoneMasksToInitiatorsURIs);
    }

    /**
     * Generates device specific workflow steps to delete zoning & exportmask.
     *
     * @param workflow
     * @param object
     * @param exportGroup
     * @param mask
     * @param storage
     * @return
     * @throws Exception
     */
    public String generateDeviceSpecificDeleteWorkflow(Workflow workflow, String previousStep,
            ExportGroup exportGroup, ExportMask mask, StorageSystem storage) throws Exception {
        String unZoneStep = generateZoningDeleteWorkflow(workflow, previousStep, exportGroup,
                Arrays.asList(mask));
        return generateExportMaskDeleteWorkflow(workflow, unZoneStep, storage,
                exportGroup, mask, null);
    }

    /**
     * Generates device specific workflow step to remove initiators in exportmask..
     *
     * @param workflow
     * @param exportGroup
     * @param mask
     * @param storage
     * @param maskToInitiatorsMap
     * @param initiatorsToRemove
     * @param removeTargets whether or not to remove storage ports from mask
     * @return
     * @throws Exception
     */
    public String generateDeviceSpecificRemoveInitiatorsWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup, ExportMask mask,
            StorageSystem storage, Map<URI, List<URI>> maskToInitiatorsMap,
            List<URI> initiatorsToRemove, boolean removeTargets) throws Exception {

        String unZoneStep = generateZoningRemoveInitiatorsWorkflow(workflow, previousStep, exportGroup,
                maskToInitiatorsMap);

        return generateExportMaskRemoveInitiatorsWorkflow(workflow, unZoneStep, storage,
                exportGroup, mask, initiatorsToRemove, removeTargets);
    }

    /**
     * Generates device specific workflow step to remove volumes in exportmask.
     *
     * @param workflow
     * @param previousStep
     * @param exportGroup
     * @param mask
     * @param storage
     * @param volumesToRemove
     * @param completer
     * @throws Exception
     */
    public String generateDeviceSpecificRemoveVolumesWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup, ExportMask mask, StorageSystem storage,
            List<URI> volumesToRemove, ExportTaskCompleter completer) throws Exception {
        String zoningStep = generateZoningRemoveVolumesWorkflow(workflow, previousStep,
                exportGroup, Arrays.asList(mask), volumesToRemove);

        return generateExportMaskRemoveVolumesWorkflow(workflow, zoningStep, storage, exportGroup,
                mask, volumesToRemove, completer);
    }

    /**
     * Generates device specific workflow step to delete exportmask.
     *
     * @param workflow
     * @param object
     * @param exportGroup
     * @param exportMask
     * @param storage
     * @throws Exception
     */
    public String generateDeviceSpecificExportMaskDeleteWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup, ExportMask exportMask,
            StorageSystem storage) throws Exception {
        return generateExportMaskDeleteWorkflow(workflow, previousStep, storage,
                exportGroup, exportMask, null);
    }

    /**
     * Generates device specific workflow step to removeVolumes in exportMask.
     *
     * @param workflow
     * @param previousStep
     * @param exportGroup
     * @param exportMask
     * @param storage
     * @param volumesToRemove
     * @param completer
     * @return last step in workflow
     * @throws Exception
     */
    public String generateDeviceSpecificExportMaskRemoveVolumesWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup, ExportMask exportMask,
            StorageSystem storage, List<URI> volumesToRemove, ExportTaskCompleter completer) throws Exception {
        return generateExportMaskRemoveVolumesWorkflow(workflow, previousStep, storage,
                exportGroup, exportMask, volumesToRemove, completer);
    }

    /**
     * Generates device specific workflow step to removing zoning for removeVolumes in exportMask.
     *
     * @param workflow
     * @param previousStep
     * @param exportGroup
     * @param exportMasksToZoneRemoveVolumes
     * @param volumesToZoneRemoveVolumes
     */
    public String generateDeviceSpecificZoningRemoveVolumesWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup,
            List<ExportMask> exportMasksToZoneRemoveVolumes,
            List<URI> volumesToZoneRemoveVolumes) {
        return generateZoningRemoveVolumesWorkflow(workflow, previousStep, exportGroup,
                exportMasksToZoneRemoveVolumes, volumesToZoneRemoveVolumes);
    }

    /**
     * Generates device specific workflow step to delete zoning in exportmask.
     *
     * @param workflow
     * @param previousStep
     * @param exportGroup
     * @param exportMasksToZoneDelete
     */
    public String generateDeviceSpecificZoningDeleteWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup,
            List<ExportMask> exportMasksToZoneDelete) {
        return generateZoningDeleteWorkflow(workflow, previousStep, exportGroup, exportMasksToZoneDelete);
    }

    /**
     * Generates device specific workflow step to remove volumes from ExportGroup.
     *
     * @param workflow
     * @param previousStep
     * @param storage
     * @param exportGroup
     * @param volumeURIs
     */
    public String generateDeviceSpecificExportGroupRemoveVolumesCleanup(Workflow workflow,
            String previousStep, StorageSystem storage, ExportGroup exportGroup,
            List<URI> volumeURIs) {
        return generateExportGroupRemoveVolumesCleanup(workflow, previousStep, storage,
                exportGroup, volumeURIs);
    }

    /**
     * Routine will update 'portNames' based on the hosts. Each host referenced by the
     * hostURIs list will be looked up and its initiators queried. If the initiator is
     * not in the list of initiators to export, its portname will be added to the
     * portName list
     *
     * @param portNames [out] - List or initiator.initiatorPortNames. The list
     *            will include new members if there are any host initiators
     *            that are not already part of the export request.
     * @param portNamesToInitiatorURI [out] - Map of portName to initiator URI. This
     *            map should contain all the port names in
     *            'portNames'.
     * @param initiatorsToExport [in] - List of Initiator URIs that are passed in the
     *            export request call.
     * @param hostURIs [in] - List of Host URIs applicable to the
     *            initiatorsToExport list
     */
    protected void queryHostInitiatorsAndAddToList(List<String> portNames,
            Map<String, URI> portNamesToInitiatorURI,
            Collection<URI> initiatorsToExport,
            List<URI> hostURIs, DbClient dbClient) {
        for (URI hostURI : hostURIs) {
            URIQueryResultList results = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.getContainedObjectsConstraint(hostURI, Initiator.class, "host"),
                    results);
            while (results.iterator().hasNext()) {
                URI uri = results.iterator().next();
                if (!initiatorsToExport.contains(uri)) {
                    Initiator initiator = dbClient.queryObject(Initiator.class, uri);
                    if (initiator.getInactive()) {
                        continue;
                    }
                    String normalizedName = initiator.getInitiatorPort();
                    if (WWNUtility.isValidWWN(normalizedName)) {
                        normalizedName = WWNUtility.getUpperWWNWithNoColons(initiator
                                .getInitiatorPort());
                    }
                    if (!portNames.contains(normalizedName)) {
                        portNames.add(normalizedName);
                        portNamesToInitiatorURI.put(normalizedName, uri);
                    }
                }
            }
        }
    }

    /**
     * Routine will update 'portNames' based on the hosts. Each host referenced by the
     * hostURIs list will be looked up and its initiators queried. If the initiator is
     * not in the list of initiators to export, its portname will be added to the
     * portName list
     *
     * @param portNames [out] - List or initiator.initiatorPortNames. The list
     *            will include new members if there are any host initiators
     *            that are not already part of the export request.
     * @param portNamesToInitiatorURI [out] - Map of portName to initiator URI. This
     *            map should contain all the port names in
     *            'portNames'.
     * @param initiatorsToExport [in] - List of Initiator URIs that are passed in the
     *            export request call.
     * @param hostURIs [in] - List of Host URIs applicable to the
     *            initiatorsToExport list
     */
    protected void queryHostInitiatorsAndAddToList(List<String> portNames,
            Map<String, URI> portNamesToInitiatorURI,
            Collection<URI> initiatorsToExport,
            List<URI> hostURIs) {
        queryHostInitiatorsAndAddToList(portNames, portNamesToInitiatorURI, initiatorsToExport, hostURIs, _dbClient);
    }

    /**
     * Checks whether the volume is in existing mask.
     * It also validates whether requested HLU is already
     * used for some other volume or not.
     *
     * @param volumeMap
     * @param exportMask
     * @param exportGroup
     * @param token
     * @return
     */
    protected Map<URI, Integer> getVolumesToAdd(Map<URI, Integer> volumeMap,
            ExportMask exportMask, ExportGroup exportGroup, String token) {
        Map<URI, Integer> volumesToAdd = new HashMap<URI, Integer>();
        for (URI boURI : volumeMap.keySet()) {
            BlockObject bo = BlockObject.fetch(_dbClient, boURI);
            if (!exportMask.hasExistingVolume(bo.getWWN())
                    && !exportMask.hasUserAddedVolume(bo.getWWN())) {
                URI thisVol = bo.getId();
                Integer hlu = volumeMap.get(thisVol);
                volumesToAdd.put(thisVol, hlu);
            }
            // Check if the requested HLU for the volume is
            // already taken by a pre-existing volume.
            Integer requestedHLU = volumeMap.get(bo.getId());
            StringMap existingVolumesInMask = exportMask.getExistingVolumes();
            if (existingVolumesInMask != null &&  requestedHLU.intValue() != ExportGroup.LUN_UNASSIGNED
                    && !ExportGroup.LUN_UNASSIGNED_DECIMAL_STR.equals(requestedHLU.toString())
                    && existingVolumesInMask.containsValue(requestedHLU.toString())) {
                ExportOrchestrationTask completer = new ExportOrchestrationTask(
                        exportGroup.getId(), token);
                ServiceError serviceError = DeviceControllerException.errors
                        .exportHasExistingVolumeWithRequestedHLU(boURI.toString(),
                                requestedHLU.toString());
                completer.error(_dbClient, serviceError);
                return null;
            }
        }
        return volumesToAdd;
    }

    /**
     * This function processes the initiatorURIs and return a mapping of String
     * host resource reference to a list Initiator URIs.
     *
     * This is the default implementation and it will group the
     * initiator's host reference
     *
     * @param exportGroup [in] - ExportGroup object to examine
     * @param initiatorURIs [in] - Initiator URIs
     * @return Map of String:computeResourceName to List of Initiator URIs
     */
    protected Map<String, List<URI>> mapInitiatorsToComputeResource(
            ExportGroup exportGroup, Collection<URI> initiatorURIs) {
        Map<String, List<URI>> hostInitiatorMap = new ConcurrentHashMap<String, List<URI>>();
        // Bogus URI for those initiators without a host object, helps maintain a good map.
        // We want to put bunch up the non-host initiators together.
        URI fillerHostURI = URIUtil.createId(Host.class); // could just be NullColumnValueGetter.getNullURI()
        if (!initiatorURIs.isEmpty()) {

            for (URI newExportMaskInitiator : initiatorURIs) {

                Initiator initiator = _dbClient.queryObject(Initiator.class, newExportMaskInitiator);
                // Not all initiators have hosts, be sure to handle either case.
                URI hostURI = initiator.getHost();
                if (hostURI == null) {
                    hostURI = fillerHostURI;
                }
                String hostURIStr = hostURI.toString();
                List<URI> initiatorSet = hostInitiatorMap.get(hostURIStr);
                if (initiatorSet == null) {
                    initiatorSet = new ArrayList<URI>();
                }
                initiatorSet.add(initiator.getId());
                hostInitiatorMap.put(hostURIStr, initiatorSet);

                _log.info(String.format("host = %s, initiators to add: %d, ",
                        hostURI, hostInitiatorMap.get(hostURIStr).size()));
            }
        }
        return hostInitiatorMap;
    }

    /**
     * Creates a new ExportMask for host for the given initiators.
     *
     * @param initiatorURIs
     * @param exportGroup
     * @param workflow
     * @param volumeMap
     * @param storage
     * @param token
     * @param previousStep
     * @return
     * @throws Exception
     */
    protected String createNewExportMaskWorkflowForInitiators(List<URI> initiatorURIs,
            ExportGroup exportGroup, Workflow workflow, Map<URI, Integer> volumeMap,
            StorageSystem storage, String token, String previousStep) throws Exception {
        if (!initiatorURIs.isEmpty()) {
            Map<String, List<URI>> computeResourceToInitiators = mapInitiatorsToComputeResource(
                    exportGroup, initiatorURIs);
            for (Map.Entry<String, List<URI>> resourceEntry : computeResourceToInitiators
                    .entrySet()) {
                String computeKey = resourceEntry.getKey();
                List<URI> computeInitiatorURIs = resourceEntry.getValue();
                _log.info(String.format("New export masks for %s", computeKey));
                GenExportMaskCreateWorkflowResult result = generateDeviceSpecificExportMaskCreateWorkFlow(workflow, previousStep,
                        storage, exportGroup, computeInitiatorURIs, volumeMap, token);
                previousStep = result.getStepId();
            }
        }
        return previousStep;
    }

    /**
     * Cache-lookup-and-update routine. It takes in a Map that will be used as a local cache.
     * ExportMaskPolicy will be looked up based on the parameters that are passed in. If it
     * exists, then the policy will always be retrieved from the cache. If not, the ExportMaskPolicy
     * will be evaluated, mapped, and returned. This routine can be used to defray the cost of
     * calling device.getExportMaskPolicy, which can be expensive if it contains many volumes.
     *
     * @param policyCache [in/out] - This is the local cache
     * @param device [in] - BlockStorageDevice interface to be used if we need to evaluate the
     *            ExportMaskPolicy for the ExportMask
     * @param storage [in] - StorageSystem representing the array
     * @param mask [in] - ExportMask object representing the mask on the array
     * @return ExportMaskPolicy associated with the ExportMask
     */
    protected ExportMaskPolicy getExportMaskPolicy(Map<URI, ExportMaskPolicy> policyCache,
            BlockStorageDevice device, StorageSystem storage, ExportMask mask) {
        ExportMaskPolicy policy = policyCache.get(mask.getId());
        if (policy == null) {
            policy = device.getExportMaskPolicy(storage, mask);
            policyCache.put(mask.getId(), policy);
        }
        return policy;
    }

    /**
     * Method does a validation given the passed in values. It list of volumeURIs
     * represents all of the volumes in the exportMask.
     *
     * @param exportMask [in] - ExportMask object in which to evaluate the condition
     * @param volumeURIs [in] - Set of Volume/BlockObject URIs to check. Assume that
     *            these will be removed from the exportMask. If removing them
     *            from exportMask results in an empty volume list,
     *            then this method will return true.
     * @return true, iff the volumeURIs list represents all of the volume/blockObjects
     *         in the exportMask.
     */
    protected boolean removingLastExportMaskVolumes(ExportMask exportMask,
            List<URI> volumeURIs) {
        // Print out the export mask
        _log.info("Checking removingLastExportMaskVolumes: " + exportMask);

        // Check to see if anyone "sneaked" an extra volume in here.
        if (exportMask.getExistingVolumes() != null &&
                exportMask.getExistingVolumes().size() > 0) {
            _log.info("there are existing external volumes in this export mask");
            return false;
        }

        boolean result = false;
        List<URI> boURIs = new ArrayList<URI>();
        for (URI volumeURI : volumeURIs) {
            BlockObject bo = Volume.fetchExportMaskBlockObject(_dbClient, volumeURI);
            if (bo != null) {
                boURIs.add(bo.getId());
            }
        }
        // If there are still user added volumes in the existing EM, then we shouldn't delete the EM.
        if (null != exportMask.getUserAddedVolumes() && !exportMask.getUserAddedVolumes().isEmpty()) {
            Collection<String> volumeURIStrings =
                    Collections2.transform(boURIs, CommonTransformerFunctions
                            .FCTN_URI_TO_STRING);
            Collection<String> exportMaskVolumeURIStrings =
                    new ArrayList<String>(exportMask.getUserAddedVolumes().values());
            exportMaskVolumeURIStrings.removeAll(volumeURIStrings);
            result = (exportMaskVolumeURIStrings.isEmpty());
        }

        return result;
    }

    /**
     * Utility for merging a bunch of maskURIs into a single Set of URIs.
     *
     * @param exportGroup [in] - ExportGroup object
     * @param maskURIs [in] - Collection of Set of URIs
     * @return Set of String -- the union of ExportGroup.exportMasks and maskURIs.
     *         There shouldn't be any duplicates.
     */
    protected Set<String> mergeWithExportGroupMaskURIs(ExportGroup exportGroup,
            Collection<Set<URI>> maskURIs) {
        Set<String> set = new HashSet<String>();
        if (exportGroup != null && maskURIs != null &&
                exportGroup.getExportMasks() != null) {
            Set<String> exportGroupMaskNames = new HashSet<String>();
            for (String it : exportGroup.getExportMasks()) {
                URI uri = URI.create(it);
                ExportMask exportMask = _dbClient.queryObject(ExportMask.class, uri);
                exportGroupMaskNames.add(exportMask.getMaskName());
            }
            set.addAll(exportGroup.getExportMasks());
            for (Set<URI> entry : maskURIs) {
                Collection<String> uris = Collections2.transform(entry,
                        CommonTransformerFunctions.FCTN_URI_TO_STRING);
                set.addAll(uris);
            }
            Iterator<String> currMaskIter = set.iterator();
            while (currMaskIter.hasNext()) {
                URI currMaskURI = URI.create(currMaskIter.next());
                ExportMask currMask = _dbClient.queryObject(ExportMask.class, currMaskURI);
                // Eliminate any ExportMasks that are inactive OR any that are not associated with the ExportGroup,
                // but have the same name as a ExportMask that is currently associated with the ExportGroup. We
                // should have a single ExportMask representing something on the array. It should not happen normally
                // but we should be defensive of the possibility.
                if (currMask.getInactive() ||
                        (!exportGroup.hasMask(currMaskURI) && exportGroupMaskNames.contains(currMask.getLabel()))) {
                    _log.info(String.format(
                            "Could not merge ExportMask %s because there is already a mask named %s associated with ExportGroup %s",
                            currMaskURI.toString(), currMask.getMaskName(), exportGroup.getLabel()));
                    currMaskIter.remove();
                }
            }
        }
        return set;
    }

    /**
     * A utility for processing initiators and updating data structures. The data
     * structures are for mapping an initiator reference on the array, which is
     * in terms of a WWN/IQN, to an initiator reference in ViPR, which is in terms of
     * URI. There's an optional parameter to return a list of host URIs referenced by
     * the list of initiators. There's an optional parameter to return a Multimap of
     * compute resource to a list of array port WWNs.
     *
     * @param exportGroup [in] - ExportGroup object
     * @param initiators [in] - Initiator objects to process
     * @param portNames [out] - Port names/WWNs of the initiators
     * @param portNameToInitiatorURI [out] - Map of port name/WWN to initiator URI
     * @param hostURIs [out] - Optional. List of URIs of the hosts that list of
     *            initiators point to.
     * @param computeResourceToPortNames [out] - Optional. Multimap of compute String
     *            name to List of portNames.
     */
    protected void processInitiators(ExportGroup exportGroup,
            Collection<Initiator> initiators,
            Collection<String> portNames,
            Map<String, URI> portNameToInitiatorURI,
            Collection<URI> hostURIs,
            ListMultimap<String, String> computeResourceToPortNames) {
        for (Initiator initiator : initiators) {
            String normalizedName = Initiator.normalizePort(initiator.getInitiatorPort());
            portNames.add(normalizedName);
            portNameToInitiatorURI.put(normalizedName, initiator.getId());
            if (hostURIs != null) {
                if (!NullColumnValueGetter.isNullURI(initiator.getHost()) &&
                        !hostURIs.contains(initiator.getHost())) {
                    hostURIs.add(initiator.getHost());
                }
            }
            if (computeResourceToPortNames != null) {
                String computeResourceId;
                if (exportGroup != null && exportGroup.forCluster()) {
                    computeResourceId = initiator.getClusterName();
                } else {
                    URI hostURI = initiator.getHost();
                    if (hostURI == null) {
                        // Bogus URI for those initiators without a host object,
                        // helps maintain a good map. We want to put bunch up the non-host
                        // initiators together.
                        hostURI = NullColumnValueGetter.getNullURI();
                    }
                    computeResourceId = hostURI.toString();
                }
                computeResourceToPortNames.put(computeResourceId, normalizedName);
            }
        }
    }

    /**
     * A utility for processing initiators and updating data structures. The data
     * structures are for mapping an initiator reference on the array, which is
     * in terms of a WWN, to an initiator reference in ViPR, which is in terms of
     * URI.
     *
     * @param exportGroup [in] - ExportGroup object
     * @param initiators [in] - Initiator objects
     * @param portNames [out] - Port names/WWNs of the initiators
     * @param portNameToInitiatorURI [out] - Map of port name/WWN to initiator URI
     * @param computeResourceToPortNames [out] - Multimap of compute String
     *            name to List of portNames.
     */
    protected void processInitiators(ExportGroup exportGroup,
            Collection<Initiator> initiators,
            Collection<String> portNames,
            Map<String, URI> portNameToInitiatorURI,
            ListMultimap<String, String> computeResourceToPortNames) {
        processInitiators(exportGroup, initiators, portNames,
                portNameToInitiatorURI, null, computeResourceToPortNames);
    }

    /**
     * A utility for processing initiators and updating data structures. The data
     * structures are for mapping an initiator reference on the array, which is
     * in terms of a WWN, to an initiator reference in ViPR, which is in terms of
     * URI.
     *
     * @param exportGroup [in] - ExportGroup object
     * @param initiatorURIs [in] - Initiator URIs
     * @param portNames [out] - Port names/WWNs of the initiators
     * @param portNameToInitiatorURI [out] - Map of port name/WWN to initiator URI
     * @param hostURIs [out] - List of URIs of the hosts that list of
     *            initiators point to.
     */
    protected void processInitiators(ExportGroup exportGroup,
            Collection<URI> initiatorURIs,
            Collection<String> portNames,
            Map<String, URI> portNameToInitiatorURI,
            Collection<URI> hostURIs) {
        Collection<String> initiatorURIStrs =
                Collections2.transform(initiatorURIs,
                        CommonTransformerFunctions.FCTN_URI_TO_STRING);
        Collection<Initiator> initiators =
                Collections2.transform(initiatorURIStrs,
                        CommonTransformerFunctions.fctnStringToInitiator(_dbClient));
        processInitiators(exportGroup, initiators, portNames,
                portNameToInitiatorURI, hostURIs, null);
    }

    /**
     * A utility for processing initiators and updating data structures. The data
     * structures are for mapping an initiator reference on the array, which is
     * in terms of a WWN, to an initiator reference in ViPR, which is in terms of
     * URI.
     *
     * @param exportGroup [in] - ExportGroup object
     * @param initiatorURIs [in] - Initiator URIs
     * @param portNames [out] - Port names/WWNs of the initiators
     * @param portNameToInitiatorURI [out] - Map of port name/WWN to initiator URI
     */
    protected void processInitiators(ExportGroup exportGroup,
            Collection<URI> initiatorURIs,
            Collection<String> portNames,
            Map<String, URI> portNameToInitiatorURI) {
        Collection<String> initiatorURIStrs =
                Collections2.transform(initiatorURIs,
                        CommonTransformerFunctions.FCTN_URI_TO_STRING);
        Collection<Initiator> initiators =
                Collections2.transform(initiatorURIStrs,
                        CommonTransformerFunctions.fctnStringToInitiator(_dbClient));
        processInitiators(exportGroup, initiators, portNames,
                portNameToInitiatorURI, null, null);
    }

    /**
     * Routine will examine the ExportGroup object's ExportMask and produce a mapping of the ExportMasks'
     * initiator port name to a list of ExportMask URIs.
     *
     * @param exportGroup [in] - ExportGroup object to examine
     * @return Map of String to set of URIs. The key will be Initiator.normalizePort(initiator.portName).
     *         Value will be set of ExportMask URIs.
     */
    protected Map<String, Set<URI>> getInitiatorToExportMaskMap(ExportGroup exportGroup) {
        Map<String, Set<URI>> mapping = new HashMap<String, Set<URI>>();
        for (String maskURIStr : exportGroup.getExportMasks()) {
            ExportMask mask = ExportMaskUtils.asExportMask(_dbClient, maskURIStr);
            if (ExportMaskUtils.isUsable(mask)) {
                Set<Initiator> initiators = ExportMaskUtils.getInitiatorsForExportMask(_dbClient, mask, null);
                for (Initiator initiator : initiators) {
                    String name = Initiator.normalizePort(initiator.getInitiatorPort());
                    Set<URI> maskURIs = mapping.get(name);
                    if (maskURIs == null) {
                        maskURIs = new HashSet<URI>();
                        mapping.put(name, maskURIs);
                    }
                    maskURIs.add(mask.getId());
                }
            }

        }
        return mapping;
    }

    /**
     * Routine will examine the ExportGroup object's ExportMask and the passed in map of
     * compute-resource-to-initiators map to produce a mapping of the ExportMasks'
     * initiator port name to a list of ExportMask URIs.
     *
     *
     * @param exportGroup [in] - ExportGroup object to examine
     * @param storage
     * @param computeResourceToInitiators [in] - Mapping of compute resource string key to
     *            list of Initiator URIs. @return Map of String to set of URIs. The key will be Initiator.normalizePort(initiator.portName).
     * @param partialMasks [out] - list of masks that were found to be "partial" masks, where there are multiple masks that make up one
     *            compute resource
     *            Value will be set of ExportMask URIs.
     */
    protected Map<String, Set<URI>> determineInitiatorToExportMaskPlacements(ExportGroup exportGroup, URI storage,
            Map<String, List<URI>> computeResourceToInitiators,
            Map<String, Set<URI>> initiatorToExportMapOnArray,
            Map<String, URI> portNameToInitiatorURI,
            Set<URI> partialMasks) {
        Map<String, Set<URI>> initiatorToExportMaskURIMap = new HashMap<String, Set<URI>>();
        Map<String, Set<URI>> computeResourceToExportMaskMap =
                ExportMaskUtils.mapComputeResourceToExportMask(_dbClient, exportGroup, storage);
        Set<URI> allExportMaskURIs = new HashSet<>();
        // Put together initial mapping based on what ExportMasks are currently
        // associated with the ExportGroup
        for (Map.Entry<String, List<URI>> entry : computeResourceToInitiators.entrySet()) {
            String computeResource = entry.getKey();
            List<URI> initiatorSet = entry.getValue();
            if (computeResourceToExportMaskMap.get(computeResource) != null) {
                for (URI exportMaskURI : computeResourceToExportMaskMap.get(computeResource)) {
                    if (exportMaskURI == null) {
                        _log.info(String.format(
                                "determineInitiatorToExportMaskPlacements - No ExportMask for compute resource %s in ExportGroup %s",
                                computeResource, exportGroup.getLabel()));
                        continue;
                    }
                    for (URI initiatorURI : initiatorSet) {
                        Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);
                        if (initiator == null) {
                            continue;
                        }
                        String normalizedName = Initiator.normalizePort(initiator.getInitiatorPort());
                        Set<URI> exportMaskURIs = initiatorToExportMaskURIMap.get(normalizedName);
                        if (exportMaskURIs == null) {
                            exportMaskURIs = new TreeSet<URI>();
                            initiatorToExportMaskURIMap.put(normalizedName, exportMaskURIs);
                        }
                        exportMaskURIs.add(exportMaskURI);
                    }
                }
            }
        }

        // Collect all export masks under review; used for checks for aggregate masks that,
        // when combined, make up a cluster masking view
        for (Map.Entry<String, Set<URI>> entry : initiatorToExportMapOnArray.entrySet()) {
            allExportMaskURIs.addAll(entry.getValue());
        }

        Collection<URI> volumes = new HashSet<URI>();
        if (exportGroup.getVolumes() != null) {
            volumes = Collections2.transform(exportGroup.getVolumes().keySet(),
                    CommonTransformerFunctions.FCTN_STRING_TO_URI);
        }
        ExportPathParams exportPathParams = _blockScheduler
                .calculateExportPathParamForVolumes(volumes, 0, storage, exportGroup.getId());
        _log.info(String.format("determineInitiatorToExportMaskPlacements - ExportGroup=%s, exportPathParams=%s",
                exportGroup.getId().toString(), exportPathParams));
        // Update mapping based on what is seen on the array
        for (Map.Entry<String, Set<URI>> entry : initiatorToExportMapOnArray.entrySet()) {
            String portName = entry.getKey();

            // Validate this initiator and determine if it exists in the database
            URI initiatorURI = portNameToInitiatorURI.get(portName);
            if (initiatorURI == null) {
                URIQueryResultList uris = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                        getInitiatorPortInitiatorConstraint(portName), uris);
                if (!uris.iterator().hasNext()) {
                    // There is no such initiator
                    _log.info(String.format("determineInitiatorToExportMaskPlacements - Could not find initiator port %s in DB",
                            portName));
                    continue;
                }
                initiatorURI = uris.iterator().next();
            }

            // We should have a non-null initiator URI at this point
            Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);
            if (initiator == null) {
                _log.info(String.format("determineInitiatorToExportMaskPlacements - Initiator %s does not exist in DB",
                        initiatorURI.toString()));
                continue;
            }

            _log.info(String
                    .format("determineInitiatorToExportMaskPlacements - Scanning masks that contain initiator %s to see if those masks qualify for consideration for re-use",
                            initiator.getInitiatorPort()));

            // This container is for capturing those ExportMasks that we find to
            // be matching based on the initiators, but unusable based on the
            // StoragePorts that the mask has. Basically, in order for a mask to
            // be considered a match, the initiators have to match, but the
            // StoragePorts have to be in the same Network as the ExportGroup's
            // VArray Network.
            Map<URI, Map<String, String>> masksWithUnmatchedStoragePorts =
                    new HashMap<URI, Map<String, String>>();

            // Take a look at the ExportMask's initiators to see what compute resource that they support.
            String computeResource = ExportUtils.computeResourceForInitiator(exportGroup, initiator);
            List<URI> uriList = computeResourceToInitiators.get(computeResource);
            List<String> portsForComputeResource = new ArrayList<String>();
            Iterator<Initiator> iterator = _dbClient.queryIterativeObjects(Initiator.class, uriList);
            while (iterator.hasNext()) {
                portsForComputeResource.add(iterator.next().getInitiatorPort());
            }

            // At this point we have a non-null initiator object that we can use in the mapping
            Map<URI, Integer> maskToTotalMatchingPorts = new HashMap<URI, Integer>();
            int totalPorts = 0;
            Set<URI> candidateExportMaskURIs = new HashSet<URI>();
            Set<URI> exportMaskURIs = entry.getValue();
            for (URI exportMaskURI : exportMaskURIs) {
                ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
                if (mask == null || mask.getInactive()) {
                    continue;
                }
                _log.info(String
                        .format("determineInitiatorToExportMaskPlacements - Checking to see if we can consider mask %s, given its initiators, storage ports, and volumes",
                                mask.getMaskName()));
                Map<String, String> storagePortToNetworkName = new HashMap<String, String>();
                if (mask.getCreatedBySystem()) {
                    if (mask.getResource().equals(computeResource)) {
                        if (maskHasStoragePortsInExportVarray(exportGroup, mask, initiator, storagePortToNetworkName)) {
                            _log.info(String
                                    .format("determineInitiatorToExportMaskPlacements - ViPR-created mask %s qualifies for consideration for re-use",
                                            mask.getMaskName()));
                            candidateExportMaskURIs.add(exportMaskURI);
                            totalPorts += storagePortToNetworkName.keySet().size();
                            maskToTotalMatchingPorts.put(exportMaskURI, totalPorts);
                            // Ingest Fix : In ingest case, more than 1 export mask with createdBySystem flag set to true is possible
                            // remove the break statement
                            // break; First ViPR-created ExportMask associated with the resource, we will use
                        } else {
                            masksWithUnmatchedStoragePorts.put(exportMaskURI, storagePortToNetworkName);
                            _log.info(String
                                    .format("determineInitiatorToExportMaskPlacements - ViPR-created mask %s does not qualify for consideration for re-use due to storage ports mismatch with varray.",
                                            mask.getMaskName()));
                        }
                    }
                } else if (maskHasInitiatorsBasedOnExportType(exportGroup, mask, initiator, portsForComputeResource) ||
                        maskHasInitiatorsBasedOnExportType(exportGroup, mask, allExportMaskURIs, portsForComputeResource, partialMasks)) {
                    if (maskHasStoragePortsInExportVarray(exportGroup, mask, initiator, storagePortToNetworkName)) {
                        _log.info(String.format(
                                "determineInitiatorToExportMaskPlacements - Pre-existing mask %s qualifies for consideration for re-use",
                                mask.getMaskName()));
                        // This is a non-ViPR create ExportMask and it has the initiator
                        // as an existing initiator. Add it this as a matching candidate
                        candidateExportMaskURIs.add(exportMaskURI);
                        // We don't have zone ingest information for pre-existing masks, so for the purpose of
                        // matching more coexistence masks, we assume there are zones from every port to every
                        // initiator in the mask.
                        // Existing Initiators - initiators which are not userAdded.
                        int existingInitiators = mask.getExistingInitiators() == null ? 0 : mask.getExistingInitiators().size();
                        int userAddedInitators = mask.getUserAddedInitiators() == null ? 0 : mask.getUserAddedInitiators().size();
                        int totalInitiators = existingInitiators + userAddedInitators;
                        totalPorts += storagePortToNetworkName.keySet().size() * totalInitiators;
                        maskToTotalMatchingPorts.put(exportMaskURI, totalPorts);
                    } else {
                        masksWithUnmatchedStoragePorts.put(exportMaskURI, storagePortToNetworkName);
                        _log.info(String
                                .format("determineInitiatorToExportMaskPlacements - Pre-existing mask %s does not qualify for consideration for re-use due to storage ports mismatch with varray.",
                                        mask.getMaskName()));
                    }
                } else {
                    _log.info(String
                            .format("determineInitiatorToExportMaskPlacements - Pre-existing mask %s does not qualify for consideration for re-use due to initiators not suitable for export group type.",
                                    mask.getMaskName()));
                }
            }

            if (!candidateExportMaskURIs.isEmpty()) {
                if (validateCandidateMasksAgainstExportPathParams(exportPathParams, maskToTotalMatchingPorts)) {
                    _log.info(String.format(
                            "determineInitiatorToExportMaskPlacements - Initiator %s (%s) will be mapped to these ExportMask URIs: %s",
                            portName, initiatorURI.toString(), Joiner.on(',').join(exportMaskURIs)));
                    initiatorToExportMaskURIMap.put(portName, candidateExportMaskURIs);
                }
            } else {
                if (masksWithUnmatchedStoragePorts.isEmpty()) {
                    _log.info(String.format(
                            "determineInitiatorToExportMaskPlacements - Could not find ExportMask to which %s can be associated", portName));
                } else {
                    // We found matching exports on the array, but they were not viable due to
                    // the StoragePorts used (they were pointing to a different VArray), so we should
                    // warn the user in this case. We will likely still attempt to make a new mask,
                    // so if the user doesn't prefer that, this message will tell them why we took that
                    // path.
                    StringBuilder exportMaskInfo = new StringBuilder();
                    for (Map.Entry<URI, Map<String, String>> maskToStoragePortsEntry : masksWithUnmatchedStoragePorts.entrySet()) {
                        URI exportMaskURI = maskToStoragePortsEntry.getKey();
                        Map<String, String> storagePortToNetworks = maskToStoragePortsEntry.getValue();
                        ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
                        exportMaskInfo.append(String.format("MaskingView=%s StoragePorts [ %s ]%n", mask.getMaskName(),
                                Joiner.on(',').join(storagePortToNetworks.entrySet())));
                    }

                    VirtualArray virtualArray =
                            _dbClient.queryObject(VirtualArray.class, exportGroup.getVirtualArray());
                    Exception e = DeviceControllerException.exceptions.
                            existingExportFoundButWithSPsInDifferentNetwork(virtualArray.getLabel(),
                                    exportMaskInfo.toString());
                    _log.warn(e.getMessage());
                }
            }
        }

        _log.info(String.format("determineInitiatorToExportMaskPlacements - initiatorToExportMaskURIMap: %s",
                Joiner.on(',').join(initiatorToExportMaskURIMap.entrySet())));
        return initiatorToExportMaskURIMap;
    }

    /**
     * Searches the storage device for any ExportMask (e.g., MaskingView) that contains
     * any of the initiators. For any ExportMasks that it finds, it will map the set
     * of Volumes that are appropriate for the ExportMask. Placement of the volume
     * to an ExportMask will be array dependent. It is up to the client to determine if
     * one or more of the ExportMasks are preferable for the actual volume placement.
     *
     * The default implementation will map all the volumes into each matched ExportMask,
     * but give a PlacementHint that only a single ExportMask should take all the volumes.
     * This puts the onus on the caller of suggestExportMasksForPlacement to
     * look at the hint to determine if one or more ExportMasks are appropriate for the
     * Volumes.
     *
     * @param storage
     *            [IN] - StorageSystem object
     * @param device
     *            [IN] - BlockStorageDevice device
     * @param initiators
     *            [IN] - List of Initiators to match with ExportMasks on the array
     * @param descriptor
     *            [IN] - Placement context. Some of the data (like which volumes to place), should have been
     *            filled in by the client before calling.
     *
     */
    protected void suggestExportMasksForPlacement(StorageSystem storage, BlockStorageDevice device, List<Initiator> initiators,
            ExportMaskPlacementDescriptor descriptor) {
        // It's okay if a single ExportMask has all the volumes
        descriptor.setPlacementHint(ExportMaskPlacementDescriptor.PlacementHint.VOLUMES_TO_SINGLE_MASK);
        // Find all the ExportMasks on the array that have the initiators (or a subset of them)
        Map<URI, ExportMask> matchingMasks = readExistingExportMasks(storage, device, initiators);
        descriptor.setMasks(matchingMasks);
        // Place all the volumes in each matching ExportMasks that were found
        for (URI exportMaskURI : matchingMasks.keySet()) {
            descriptor.placeVolumes(exportMaskURI, descriptor.getVolumesToPlace());
        }
    }

    /**
     * Searches the storage device for any ExportMask (e.g. MaskingView) that contains
     * any of the initiators.
     *
     * @param storage -- Storage system to be searched.
     * @param device -- Storage device to be searched for Export Masks
     * @param initiators - List of Initiator objects to be searched for.
     * @return a map of URIs to Export Masks that contain any of the initiators
     */
    protected Map<URI, ExportMask> readExistingExportMasks(
            StorageSystem storage,
            BlockStorageDevice device, List<Initiator> initiators) {
        // We use the ExportGroup to pass the ExportGroupType to the
        // routines below.
        ExportGroup exportGroup = new ExportGroup();
        exportGroup.setType(ExportGroupType.Host.name());

        Map<String, URI> portNameToInitiatorURI = new HashMap<String, URI>();
        List<String> portNames = new ArrayList<String>();
        ListMultimap<String, String> computeResourceToPortNames =
                ArrayListMultimap.create();
        // Populate data structures to track initiators
        processInitiators(exportGroup, initiators, portNames, portNameToInitiatorURI,
                computeResourceToPortNames);
        // Find the export masks that are associated with any or all the ports in
        // portNames. We will have to do processing differently based on whether
        // or not there is an existing ExportMasks.
        Map<String, Set<URI>> exportMaskURIs =
                device.findExportMasks(storage, portNames, false);

        // Refresh the export masks so they are up-to-date.
        // Map of export mask URI to Export Mask.
        Map<URI, ExportMask> updatedMasks = new HashMap<URI, ExportMask>();
        for (Set<URI> masks : exportMaskURIs.values()) {
            for (URI maskURI : masks) {
                if (!updatedMasks.containsKey(maskURI)) {
                    ExportMask mask = _dbClient.queryObject(ExportMask.class, maskURI);
                    ExportMask updatedMask = refreshExportMask(storage, device, mask);
                    updatedMasks.put(maskURI, updatedMask);
                }
            }
        }
        return updatedMasks;
    }

    protected ExportMask refreshExportMask(StorageSystem storage,
            BlockStorageDevice device, ExportMask mask) {
        ExportMask updatedMask = device.refreshExportMask(storage, mask);
        return updatedMask;
    }

    /**
     * Function will examine the ExportGroup's initiators and the initiatorURIs list.
     * A mapping of initiator URI to a Boolean flag will be returned. If the flag
     * is == Boolean.TRUE, then it implies that the initiator is part of a full list
     * of initiators for a compute resource. Otherwise, it will be == Boolean.FALSE.
     *
     * Example: Say exportGroup points to 2 hosts, each having 2 initiators
     * for a total of 4 initiators:
     * {H1I1, H1I2, H2I1, H2I2}
     *
     * If the call is flagInitiatorsThatArePartOfAFullList(exportGroup, {H1I1, H1I2, H2I1})
     * Then the resultant mapping will be:
     * {{H1I1 -> True}, {H1I2 -> True}, {H2I1 -> False}}
     *
     * If the call is flagInitiatorsThatArePartOfAFullList(exportGroup, {H1I1, H2I1})
     * Then the resultant mapping will be:
     * {{H1I1 -> False}, {H2I1 -> False}}
     *
     * @param exportGroup [in] - ExportGroup object
     * @param initiatorURIs [in] - List of Initiator URIs that are passed in the
     *            removeInitiators request
     * @return Map of Initiator URI to Boolean. There will be an entry for every
     *         initiator in the initiatorURIs list. If the mapping for the initiatorURI
     *         is Boolean.TRUE, then it implies that the initiator is part of a full
     *         list of initiators for a compute resource.
     *
     */
    protected Map<URI, Boolean> flagInitiatorsThatArePartOfAFullList(ExportGroup exportGroup,
            List<URI> initiatorURIs) {
        Map<URI, Boolean> initiatorFlagMap = new HashMap<URI, Boolean>();
        // We only care about Host and Cluster exports for this processing
        if (exportGroup.forCluster() || exportGroup.forHost()) {
            // Get a mapping of compute resource to its list of Initiator URIs
            // for the initiatorURIs list
            Map<String, List<URI>> computeResourceMapForRequest =
                    mapInitiatorsToComputeResource(exportGroup, initiatorURIs);
            // Get a mapping of compute resource to its list of Initiator URIs
            // for the ExportGroup's initiator list
            Collection<URI> egInitiators =
                    Collections2.transform(exportGroup.getInitiators(),
                            CommonTransformerFunctions.FCTN_STRING_TO_URI);
            Map<String, List<URI>> computeResourcesMapForExport =
                    mapInitiatorsToComputeResource(exportGroup, egInitiators);
            // For each compute resource, get the list of initiators and compare
            // that with the passed in initiators, grouped by compute resource.
            // If all the initiators for a compute resource are found, then the
            // all the compute resource initiator URI keys will be mapped to Boolean.TRUE.
            // Otherwise, it will be mapped to Boolean.FALSE;
            for (Map.Entry<String, List<URI>> computeResource : computeResourcesMapForExport.entrySet()) {
                List<URI> initiatorsForComputeResource = computeResource.getValue();
                List<URI> initiatorsInRequest = computeResourceMapForRequest.get(computeResource.getKey());
                if (initiatorsInRequest != null) {
                    initiatorsForComputeResource.removeAll(initiatorsInRequest);
                    Boolean isFullList = (initiatorsForComputeResource.isEmpty());
                    for (URI uri : initiatorsInRequest) {
                        initiatorFlagMap.put(uri, isFullList);
                    }
                }
            }
        }
        // For each URI in initiatorURIs find out if there is an entry in the initiatorFlagMap.
        // This would indicate the above code has filled in the appropriate value for it.
        // If it is not found, we need to set it to the default value -- Boolean.FALSE.
        // This way, there will be an entry for every URI in the initiatorURIs list.
        for (URI uri : initiatorURIs) {
            if (!initiatorFlagMap.containsKey(uri)) {
                initiatorFlagMap.put(uri, Boolean.FALSE);
            }
        }
        return initiatorFlagMap;
    }

    /**
     * If the ExportGroup.Type is Host or Cluster, we need to check if the ExportMask has all the
     * compute resource's initiators. If it does, it's a match. If the ExportGroup.Type is Initiator,
     * then we just need to verify that the initiator is in it for it be considered a match.
     *
     *
     * @param exportGroup [in] - ExportGroup object to examine
     * @param mask [in] - ExportMask object
     * @param initiator [in] - Initiator object to validate
     * @param portsForComputeResource [in] - List of port names for the compute resource being exported to
     * @return true If the export is for a host or cluster and all of the compute resource's initiators in the mask
     *         ELSE
     *         IF the export has the initiator
     */
    private boolean maskHasInitiatorsBasedOnExportType(ExportGroup exportGroup, ExportMask mask,
            Initiator initiator, List<String> portsForComputeResource) {
        boolean result = false;
        if (exportGroup.forHost() || exportGroup.forCluster()) {
            result = mask.hasExactlyTheseInitiators(portsForComputeResource);
        } else if (mask.hasInitiator(initiator.getId().toString()) || mask.hasExistingInitiator(initiator)) {
            result = true;
        }
        return result;
    }

    /**
     * If the ExportGroup.Type is Host or Cluster, we need to check if the ExportMask has all the
     * compute resource's initiators. If it does, it's a match. If the ExportGroup.Type is Initiator,
     * then we just need to verify that the initiator is in it for it be considered a match.
     *
     *
     * @param exportGroup [in] - ExportGroup object to examine
     * @param mask [in] - ExportMask object
     * @param initiator [in] - Initiator object to validate
     * @param portsForComputeResource [in] - List of port names for the compute resource being exported to
     * @param partialMasks [out] - Set of export masks that, when put together, make up a cluster mask
     * @return true If the export is for a host or cluster and all of the compute resource's initiators in the mask
     *         ELSE
     *         IF the export has the initiator
     */
    private boolean maskHasInitiatorsBasedOnExportType(ExportGroup exportGroup, ExportMask mask,
            Set<URI> otherMaskURIs, List<String> portsForComputeResource,
            Set<URI> partialMasks) {
        Set<String> foundPorts = new HashSet<>();
        if (exportGroup.forHost() || exportGroup.forCluster()) {
            if (mask.hasExactlyTheseInitiators(portsForComputeResource)) {
                return true;
            }

            // Make sure the mask in question contains only ports in the compute resource in order to qualify
            if (mask.hasAnyInitiators() && mask.hasExactlySubsetOfTheseInitiators(portsForComputeResource)) {
                // Specifically for cluster: Either we have a mask that already works for multiple hosts (the case of cluster),
                // but maybe not all of the hosts in the cluster, or this mask is a non-cascaded IG mask that only works for one host.
                //
                // If the mask serves a subset of hosts in the cluster already, we can use that mask. The export engine will only
                // create the IG that's needed and add it to the cascaded IG.
                if (exportGroup.forCluster() && maskAppliesToMultipleHosts(mask)) {
                    return true;
                } else if (exportGroup.forHost()) {
                    return !maskAppliesToMultipleHosts(mask); // Only allow this mask if it applies to one host.
                }

                // If the mask only serves one host, we need to see other masks that fill up a cluster in order to use those masks.
                // Otherwise, we'll create a whole new masking view, etc.
                // We need to use all the initiators , existingInitiators will be null at times
                foundPorts.addAll(ExportUtils.getExportMaskAllInitiatorPorts(mask, _dbClient));
                partialMasks.add(mask.getId());
            } else {
                return false;
            }

            List<ExportMask> otherMasks = _dbClient.queryObject(ExportMask.class, otherMaskURIs);
            // Now look for even more ports that might make up the whole compute resource
            for (ExportMask otherMask : otherMasks) {
                // Exclude cluster masking views from this port check; cluster masking views can not be combined with host-based MVs
                // to create a "virtual" cluster masking view from the perspective of ViPR.
                //
                // Also, without excluding cluster-based masking views here, we would qualify masks that are for single hosts
                // when a cluster export is requested and a cluster masking view is available.
                if (!exportGroup.forCluster() || !maskAppliesToMultipleHosts(otherMask)) {
                    if (otherMask.hasAnyInitiators() && otherMask.hasExactlySubsetOfTheseInitiators(portsForComputeResource)) {
                        partialMasks.add(otherMask.getId());
                        foundPorts.addAll(ExportUtils.getExportMaskAllInitiatorPorts(otherMask, _dbClient));
                    }
                }
            }

            // If we have all of the ports
            if (foundPorts.size() == portsForComputeResource.size()) {
                return true;
            }
        }

        partialMasks.clear();
        return false;
    }

    /**
     * Routine validates if the ExportMask has StoragePorts that point to the same
     * VArray as the ExportGroup's and that the Network associated with the StoragePorts
     * matches those of the initiator.
     *
     *
     * @param exportGroup [in] - ExportGroup object
     * @param mask [in] - ExportMask object
     * @param initiator [in] - Initiator object to validate
     * @param storagePortToNetwork [out] - will populate the map with StoragePort.name to Network.Name
     * @return true --> iff the ExportMask has viable StoragePorts that are associated to the ExportGroup's
     *         VArray and it matches the export path parameters of the ExportGroup
     */
    private boolean maskHasStoragePortsInExportVarray(ExportGroup exportGroup,
            ExportMask mask, Initiator initiator,
            Map<String, String> storagePortToNetwork) {
        boolean isMatched = false;
        SetMultimap<URI, URI> initiatorToMatchedSP = TreeMultimap.create();
        if (mask.getStoragePorts() != null) {
            VirtualArray virtualArray = _dbClient.queryObject(VirtualArray.class, exportGroup.getVirtualArray());
            // Look up the Initiator's network
            NetworkLite initiatorNetwork = BlockStorageScheduler.lookupNetworkLite(_dbClient,
                    StorageProtocol.block2Transport(initiator.getProtocol()), initiator.getInitiatorPort());
            if (initiatorNetwork == null) {
                _log.info(String.format("maskHasStoragePortsInExportVarray - Initiator %s is not in any network, returning false",
                        initiator.getInitiatorPort()));
                return false;
            }
            for (String uriString : mask.getStoragePorts()) {
                URI uri = URI.create(uriString);
                StoragePort port = _dbClient.queryObject(StoragePort.class, uri);
                // Basic validation of the StoragePort
                if (port == null || port.getInactive()) {
                    _log.info(String.format("maskHasStoragePortsInExportVarray - Could not find port or it is inactive %s", uri.toString()));
                    continue;
                }
                // StoragePort needs to be in the REGISTERED and VISIBLE status
                if (!port.getRegistrationStatus().equals(StoragePort.RegistrationStatus.REGISTERED.name()) ||
                        port.getDiscoveryStatus().equals(DiscoveryStatus.NOTVISIBLE.name())) {
                    _log.info(String.format("maskHasStoragePortsInExportVarray - Port %s (%s) is not registered or not visible",
                            port.getPortName(), uri.toString()));
                    continue;
                }
                // Look up the StoragePort's network
                NetworkLite storagePortNetwork = BlockStorageScheduler.lookupNetworkLite(_dbClient,
                        StorageProtocol.Transport.valueOf(port.getTransportType()), port.getPortNetworkId());
                if (storagePortNetwork == null) {
                    _log.info(String.format("maskHasStoragePortsInExportVarray - Port %s (%s) is not associated with any network",
                            port.getPortName(), uri.toString()));
                    storagePortToNetwork.put(port.getPortName(), UNASSOCIATED);
                    continue;
                }
                // Keep track of the StoragePort's network name
                storagePortToNetwork.put(port.getPortName(), storagePortNetwork.getLabel());
                // Port must belong to the VArray of the ExportGroup
                if (!port.taggedToVirtualArray(exportGroup.getVirtualArray())) {
                    _log.info(String.format("maskHasStoragePortsInExportVarray - Port %s (%s) is not tagged to VArray %s (%s)",
                            port.getPortName(), uri.toString(), virtualArray.getLabel(), exportGroup.getVirtualArray().toString()));
                    // CTRL-8959 - All mask Ports should be part of the VArray, to be able to consider this Export mask for reuse.
                    // reverted the fix, as the probability of Consistent lun violation will be more.
                    continue;
                }
                // Check if the StoragePort and Initiator point to the same Network
                if (storagePortNetwork.connectedToNetwork(initiatorNetwork.getId())) {
                    _log.info(
                            String.format(
                                    "maskHasStoragePortsInExportVarray - StoragePort matches: VArray=%s (%s), StoragePort=%s, Network=%s, Initiator=%s",
                                    virtualArray.getLabel(), exportGroup.getVirtualArray().toString(), port.getPortName(),
                                    storagePortNetwork.getLabel(),
                                    initiator.getInitiatorPort()));
                }
                // Got here, so we can update the list of initiators to list of StoragePorts
                // that show a relationship through the Network and the VArray
                initiatorToMatchedSP.put(initiator.getId(), port.getId());
            }
        }
        // Validate that the ExportMask is a positive match based on the StoragePorts
        // that it references and the ExportGroups path parameters.
        Set<URI> matchedSPs = initiatorToMatchedSP.get(initiator.getId());
        isMatched = (matchedSPs != null && !matchedSPs.isEmpty());
        _log.info(String.format("maskHasStoragePortsInExportVarray - Returning %s", isMatched));
        return isMatched;
    }

    /**
     * Looks at the maskToTotalMatchingPorts map and generates exception if any of them do not meet
     * the exportPathParams.minPaths value
     *
     * @param exportPathParams [in] - ExportPathParams for the ExportGroup
     * @param maskToTotalMatchingPorts [in] - Map of the ExportMask URI to an Integer value
     *            representing the number of StoragePorts that match network constraints
     *            of the initiator.
     * @return true if valid, false otherwise
     */
    private boolean validateCandidateMasksAgainstExportPathParams(ExportPathParams exportPathParams,
            Map<URI, Integer> maskToTotalMatchingPorts) {
        for (Map.Entry<URI, Integer> entry : maskToTotalMatchingPorts.entrySet()) {
            URI exportMaskURI = entry.getKey();
            Integer totalPorts = entry.getValue();
            if (totalPorts < exportPathParams.getMinPaths()) {
                List<ExportMask> masks = _dbClient.queryObjectField(ExportMask.class, "maskName",
                        Arrays.asList(exportMaskURI));
                String exportMaskName = masks.get(0).getMaskName();
                Exception e = DeviceControllerException.exceptions.
                        existingExportFoundButNotEnoughPortsToSatisfyMinPaths(exportMaskName, totalPorts.toString(),
                                exportPathParams.toString());
                _log.warn(e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Simple conversion routine. Takes in a map initiator portname String to Set of ExportMask URIs and
     * creates a map of ExportMask URI to List of Initiator URIs
     *
     * @param initiatorToExportMasks Map of Initiator URI String to Set of ExportMask URIs
     * @param portNameToInitiatorURI Map of Initiator portname String to Initiator URI
     * @return Map of ExportMask URI to list of Initiator URIs
     */
    protected Map<URI, List<URI>> toExportMaskToInitiatorURIs(Map<String, Set<URI>> initiatorToExportMasks,
            Map<String, URI> portNameToInitiatorURI) {
        Map<URI, List<URI>> map = Collections.EMPTY_MAP;
        if (initiatorToExportMasks != null && initiatorToExportMasks.size() > 0) {
            map = new HashMap<>();
            for (Map.Entry<String, Set<URI>> entry : initiatorToExportMasks.entrySet()) {
                for (URI exportMaskURI : entry.getValue()) {
                    List<URI> initiators = map.get(exportMaskURI);
                    if (initiators == null) {
                        initiators = new ArrayList<>();
                        map.put(exportMaskURI, initiators);
                    }
                    URI initiatorURI = portNameToInitiatorURI.get(entry.getKey());
                    if (initiatorURI != null && !initiators.contains(initiatorURI)) {
                        initiators.add(initiatorURI);
                    }
                }
            }
        }
        return map;
    }

    /**
     * Check to see if this mask applies to multiple hosts already.
     * Helps us to determine if this is a qualifying mask for brownfield
     *
     * @param mask export mask
     * @return true if the mask has initiators from multiple hosts
     */
    protected boolean maskAppliesToMultipleHosts(ExportMask mask) {
        Set<URI> existingMaskHosts = new HashSet<URI>();
        Set<URI> initiatorIds = ExportMaskUtils.getAllInitiatorsForExportMask(_dbClient, mask);
        for (URI initiatorId : initiatorIds) {
            // We should have a non-null initiator URI at this point
            Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorId);
            if (initiator == null) {
                _log.info(String.format("maskAppliesToMultipleHosts - Initiator %s does not exist in DB",
                        initiatorId.toString()));
                continue;
            }

            existingMaskHosts.add(initiator.getHost());
        }

        return existingMaskHosts.size() > 1;
    }

    /**
     * Update the zoning map for a newly "accepted" export mask. This applies to
     * brown field scenarios where a export mask was found on the storage array.
     * This function finds the zones of the export mask existing initiators and
     * existing ports and creates the zoning map between the two sets.
     * <p>
     * Note, persistence should be done by the caling function.
     *
     * @param exportGroup the masking view export group
     * @param exportMask the export mask being updated.
     * @param doPersist a boolean that indicate if the changes should be persisted
     */
    protected void updateZoningMap(ExportGroup exportGroup, ExportMask exportMask, boolean doPersist) {
        _networkDeviceController.updateZoningMap(exportGroup, exportMask, doPersist);
    }

    /**
     * Overloaded version of {@link #updateZoningMap(ExportGroup, ExportMask, boolean)}
     *
     * @param exportGroup
     * @param exportMask
     */
    public void updateZoningMap(ExportGroup exportGroup, ExportMask exportMask) {
        updateZoningMap(exportGroup, exportMask, false);
    }

    /**
     * Method to display the ExportGroup collections and its related ExportMasks per StorageSystem
     *
     * @param exportGroup [in] - ExportGroup to display
     * @param storage [in] - Used to filter the associated ExportMasks to display
     */
    protected void logExportGroup(ExportGroup exportGroup, URI storage) {
        if (exportGroup != null) {
            StackTraceElement[] elements = new Throwable().getStackTrace();
            String caller = String.format("%s#%s", elements[1].getClassName(), elements[1].getMethodName());
            StringBuilder message = new StringBuilder();
            message.append(String.format("ExportGroup before %s %n %s", caller, exportGroup.toString()));
            message.append(String.format("ExportMasks associated with ExportGroup and StorageSystem %s:", storage));
            for (ExportMask exportMask : ExportMaskUtils.getExportMasks(_dbClient, exportGroup, storage)) {
                message.append('\n').append(exportMask.toString());
            }
            _log.info(message.toString());
        }
    }

    /**
     * Creates a map of compute resource to export masks associated with that resource.
     *
     * @param portNameToInitiatorURI port name -> initiator URI simple map
     * @param resourceToInitiators compute resource -> initiator ports
     * @param initiatorToExportMaskPlacementMap initiator port -> masks
     * @return map of compute resource -> export masks that qualify for analysis
     */
    public static Map<String, Set<URI>> createResourceMaskMap(Map<String, URI> portNameToInitiatorURI,
            Map<String, List<URI>> resourceToInitiators,
            Map<String, Set<URI>> initiatorToExportMaskPlacementMap) {
        Map<String, Set<URI>> resourceMaskMap = new HashMap<String, Set<URI>>();
        for (Map.Entry<String, List<URI>> resourceToInitiatorEntry : resourceToInitiators.entrySet()) {
            // For each resource, we have a list of Initiator URIs
            for (Map.Entry<String, Set<URI>> entry : initiatorToExportMaskPlacementMap.entrySet()) {
                // The initiator to exportmask map only has ports; find that port and its corresponding initiator URI
                URI portNameURI = portNameToInitiatorURI.get(entry.getKey());
                if (portNameURI != null) {
                    if (resourceToInitiatorEntry.getValue().contains(portNameURI)) {
                        if (resourceMaskMap.get(resourceToInitiatorEntry.getKey()) == null) {
                            resourceMaskMap.put(resourceToInitiatorEntry.getKey(), new HashSet<URI>());
                        }
                        resourceMaskMap.get(resourceToInitiatorEntry.getKey()).addAll(entry.getValue());
                    }
                }
            }
        }
        return resourceMaskMap;
    }
}
