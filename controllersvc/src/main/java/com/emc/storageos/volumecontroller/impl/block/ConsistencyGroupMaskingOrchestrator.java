package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.util.StorageDriverManager;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalDeviceMaskingOrchestrator;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import com.google.common.base.Joiner;

public class ConsistencyGroupMaskingOrchestrator extends AbstractMaskingFirstOrchestrator {

    // NOTE:  all code copied from ExternalDeviceMaskingOrchestrator, except where noted

    private static final Logger _log = LoggerFactory.getLogger(ExternalDeviceMaskingOrchestrator.class);
    private StorageDriverManager driverManager = null;
    private BlockStorageDevice device = null;

    @Override
    public synchronized BlockStorageDevice getDevice() {
        if (device == null) {
            device = (BlockStorageDevice) ControllerServiceImpl.getBean(StorageDriverManager.EXTERNAL_STORAGE_DEVICE);
        }
        return device;
    }

    public synchronized StorageDriverManager getDriverManager() {
        if (driverManager == null) {
            driverManager = (StorageDriverManager) ControllerServiceImpl.getBean(StorageDriverManager.STORAGE_DRIVER_MANAGER);
        }
        return driverManager;
    }

    @Override
    public void createWorkFlowAndSubmitForExportGroupCreate(List<URI> initiatorURIs, Map<URI, Integer> volumeMap, String token,
            ExportOrchestrationTask taskCompleter, BlockStorageDevice device,
            ExportGroup exportGroup, StorageSystem storage) throws Exception {
        // Check that storage system is driver managed.
        StorageDriverManager storageDriverManager = getDriverManager();
        if (!storageDriverManager.isDriverManaged(storage.getSystemType())) {
            throw DeviceControllerException.exceptions.invalidSystemType(storage.getSystemType());
        }

        _log.info("Started export group processing.");
        // Get new work flow to setup steps for export group creation
        Workflow workflow = _workflowService.getNewWorkflow(
                MaskingWorkflowEntryPoints.getInstance(), "exportGroupCreate",
                true, token);

        // Create two steps, one for the ExportGroup actions and one for Zoning.
        List<String> maskingSteps = generateExportGroupCreateSteps(workflow, null,
                device, storage, exportGroup, initiatorURIs, volumeMap, false, token);

        // Have to store export group id to be available at device level for each masking step.
        for (String stepId : maskingSteps) {
            WorkflowService.getInstance().storeStepData(
                    workflow.getWorkflowURI(), null, stepId, exportGroup.getId());
        }

        /*
         * This step is for zoning. It is not specific to a single
         * NetworkSystem, as it will look at all the initiators and targets and
         * compute the zones required (which might be on multiple
         * NetworkSystems.)
         *
         * Dependency task for the zoning execution is
         * EXPORT_GROUP_MASKING_TASK, hence enforcing the masking to be executed
         * before zoning is attempted
         */
        String zoningStep = generateDeviceSpecificZoningCreateWorkflow(
                workflow, EXPORT_GROUP_MASKING_TASK, exportGroup, null,
                volumeMap);

        if (!maskingSteps.isEmpty() && null != zoningStep) {
            // Execute the plan and allow the WorkflowExecutor to fire the
            // taskCompleter.
            workflow.executePlan(taskCompleter, String.format(
                    "ExportGroup successfully applied for StorageArray %s",
                    storage.getLabel()));
        }
    }

    /**
     * Generates export group create steps for a given set of initiators and volumes.
     * Only "greenfield" case is supported --- if export masks with one or more of given
     * initiators exist, we will fail the request.
     *
     * @param workflow
     * @param previousStep
     * @param device
     * @param storage
     * @param exportGroup
     * @param initiatorURIs
     * @param volumeMap
     *            volume URI to HLU map
     * @param zoneStepNeeded
     * @param token
     * @return list of step Ids
     * @throws Exception
     */
    private List<String> generateExportGroupCreateSteps(Workflow workflow,
            String previousStep, BlockStorageDevice device,
            StorageSystem storage, ExportGroup exportGroup,
            List<URI> initiatorURIs, Map<URI, Integer> volumeMap, boolean zoneStepNeeded, String token)
                    throws Exception {
        Map<String, URI> portNameToInitiatorURI = new HashMap<>();
        List<URI> hostURIs = new ArrayList<>();
        List<String> portNames = new ArrayList<>(); // host initiator names

        _log.info("Started export mask steps generation.");
        /*
         * Populate the port WWN/IQNs (portNames) and the mapping of the
         * WWN/IQNs to Initiator URIs
         */
        processInitiators(exportGroup, initiatorURIs, portNames, portNameToInitiatorURI, hostURIs);
        _log.info("Done with initiator processing.");
        /*
         * We always want to have the full list of initiators for the hosts
         * involved in this export. This will allow the export operation to
         * always find any existing exports for a given host.
         */
        queryHostInitiatorsAndAddToList(portNames, portNameToInitiatorURI, initiatorURIs, hostURIs);

        /*
         * Find the export masks that are associated with any or all the ports
         * in portNames. We will have to do processing differently based on
         * whether or there is an existing ExportMasks.
         */
        Map<String, Set<URI>> matchingExportMaskURIs = device.findExportMasks(storage, portNames, false);
        _log.info("Done with matching export masks.");

        List<String> newSteps;
        if (matchingExportMaskURIs == null || matchingExportMaskURIs.isEmpty()) {

            _log.info(String.format(
                    "No existing mask found w/ initiators { %s }",
                    Joiner.on(",").join(portNames)));

            Collection<Map<URI,Integer>> volumeMapByCg = splitVolumeMapByCg(volumeMap);

            newSteps = new ArrayList<>();
            for(Map<URI,Integer> volumeMapForCg : volumeMapByCg) {
                newSteps.addAll(createNewExportMaskWorkflowForInitiators(initiatorURIs,
                        exportGroup, workflow, volumeMapForCg, storage, token,
                        previousStep));
            }

        } else {
            _log.info(String.format("Mask(s) found w/ initiators {%s}. "
                    + "MatchingExportMaskURIs {%s}, portNameToInitiators {%s}",
                    Joiner.on(",").join(portNames),
                    Joiner.on(",").join(matchingExportMaskURIs.keySet()),
                    Joiner.on(",").join(portNameToInitiatorURI.entrySet())));

            /*
             * TODO: TBD. use case not supported for External device (brown field).
             */
            throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
        }
        return newSteps;
    }

    @Override
    public void createWorkFlowAndSubmitForAddVolumes(URI storageURI,
            URI exportGroupURI, Map<URI, Integer> volumeMap, String token,
            ExportTaskCompleter taskCompleter, ExportGroup exportGroup,
            StorageSystem storage) throws Exception {

        // Note: We support only case when add volumes to export group does not change storage ports in
        // existing export masks where volumes are added.
        // Since we only execute masking step on device for existing masks --- no zoning change is required.

        // separate masks will be used for each consistency group (cg)

        List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, storageURI);
        Map<URI,List<ExportMask>> masksByCg = getMasksByCg(exportMasks);      // key=cgId, val=ExportMask
        Map<URI, Map<URI, Integer>> volumesByCg = getVolumesByCg(volumeMap);  // key=cgId, val=[vol,hlu] pairs

        Map<URI,List<ExportMask>> masksToUpdate = new HashMap<>(); // existing masks to update
        List<URI> masksToCreate = new ArrayList<>();               // new masks to create

        // find existing vs new masks
        for (URI cgIdForVolume : volumesByCg.keySet()) {
            if (masksByCg.containsKey(cgIdForVolume)) {
                masksToUpdate.put(cgIdForVolume, masksByCg.get(cgIdForVolume));
            } else {
                masksToCreate.add(cgIdForVolume);
            }
        }

        // update existing masks
        if(!masksToUpdate.isEmpty()) {
            Workflow workflow = _workflowService.getNewWorkflow(
                    MaskingWorkflowEntryPoints.getInstance(),
                    "exportGroupAddVolumes - Added volumes to existing mask",
                    true, token);
            for( URI cgIdForMask : masksToUpdate.keySet()) {
                for (ExportMask exportMask : masksToUpdate.get(cgIdForMask)) {
                    Map<URI, Integer> volumesInCg = volumesByCg.get(cgIdForMask);

                    _log.info("export_volume_add: adding volumes to an existing export %s",volumesInCg);
                    exportMask.addVolumes(volumesInCg);
                    // Have to add volumes to user created volumes set in the mask since
                    // generateExportMaskAddVolumesWorkflow() call below does not do this.
                    for (URI volumeUri : volumesInCg.keySet()) {
                        BlockObject volume = (BlockObject) _dbClient.queryObject(volumeUri);
                        exportMask.addToUserCreatedVolumes(volume);
                    }
                    _dbClient.updateObject(exportMask);

                    String maskingStep = generateExportMaskAddVolumesWorkflow(workflow,
                            null, storage, exportGroup, exportMask, volumesInCg, null);

                    // We do not need zoning step, since storage ports should not change.
                    // Have to store export group id to be available at device level.
                    WorkflowService.getInstance().storeStepData(
                            workflow.getWorkflowURI(), null, maskingStep, exportGroup.getId());
                }
            }
            String successMessage = String.format(
                    "Volumes successfully added to export on StorageArray %s",
                    storage.getLabel());
            workflow.executePlan(taskCompleter, successMessage);
        }

        // create new masks
        if(!masksToCreate.isEmpty()) {
            if (exportGroup.getInitiators() == null || exportGroup.getInitiators().isEmpty()) {
                _log.info("Cannot add volumes to ExportMask.  ExportGroup doesn't have any initiators %s", exportGroup.getId());
                taskCompleter.ready(_dbClient);
            } else {
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupCreate",
                        true, token);

                for (URI cgId : masksToCreate) {
                    // This is the case when export group does not have export mask for storage array & CG where the volumes belongs.
                    // In this case we will create new export masks for the storage array and each compute resource in the
                    // export group. Essentially for every existing mask we will add a new mask for the array and initiators in
                    // the existing mask, for each CG.

                    Map<URI, Integer> volumes = volumesByCg.get(cgId);
                    _log.info("export_volume_add: adding volume, creating a new export mask for volumes %s", volumes);

                    List<URI> initiatorURIs = new ArrayList<>();
                    for (String initiatorId : exportGroup.getInitiators()) {
                        Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(initiatorId));
                        initiatorURIs.add(initiator.getId());
                    }

                    // This call will create steps for a new mask for each compute resource
                    // and new volumes. For example, if there are 3 compute resources in the group,
                    // the step will create 3 new masks for these resources and new volumes.
                    List<String> maskingSteps = createNewExportMaskWorkflowForInitiators(initiatorURIs,
                            exportGroup, workflow, volumes, storage, token,null);

                    // Have to store export group id to be available at device level for each masking step.
                    for (String stepId : maskingSteps) {
                        WorkflowService.getInstance().storeStepData(
                                workflow.getWorkflowURI(), null, stepId, exportGroup.getId());
                    }
                    generateZoningCreateWorkflow(workflow,EXPORT_GROUP_MASKING_TASK,
                            exportGroup, null, volumes);
                }
                String successMessage = String.format("Volumes successfully added to export " +
                        "StorageArray %s", storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            }
        }
    }

    private Map<URI, List<ExportMask>> getMasksByCg(List<ExportMask> exportMasks) throws URISyntaxException {
        // generate map of masks with volume ID as key
        Map<URI,List<ExportMask>> masksByVolume = new HashMap<>();
        for(ExportMask exportMask : exportMasks) {
            if ((exportMask.getVolumes() == null) && !exportMask.getVolumes().isEmpty()) {
                continue; // skip if no vols in Mask
            }
            URI firstVolumeIdInMask = new URI(exportMask.getVolumes().get(0));  //TODO: consider checking all vols, to confirm all vols in mask are in same CG
            if (masksByVolume.containsKey(firstVolumeIdInMask)) {
                masksByVolume.get(firstVolumeIdInMask).add(exportMask);
            } else {
                masksByVolume.put(firstVolumeIdInMask,Arrays.asList(exportMask));
            }
        }

        // generate map of masks, with CG ID as key
        Map<URI,List<ExportMask>> cgIdToMasks = new HashMap<>();
        List<Volume> volsInMasks = _dbClient.queryObjectField(Volume.class,
                "consistencyGroup",masksByVolume.keySet());
        for( Volume volInMask : volsInMasks ) {
            List<ExportMask> masksForVolume = masksByVolume.get(volInMask.getId());
            if (cgIdToMasks.containsKey(volInMask.getConsistencyGroup())) {
                cgIdToMasks.get(volInMask.getConsistencyGroup()).addAll(masksForVolume);
            } else {
                cgIdToMasks.put(volInMask.getConsistencyGroup(),masksForVolume);
            }
        }
        return cgIdToMasks;
    }

    //  split volumeMap by CG into separate volumeMaps
    private Collection<Map<URI,Integer>> splitVolumeMapByCg(Map<URI,Integer> volumeMap) {
        return getVolumesByCg(volumeMap).values();
    }

    //  get a volumeMap separated by CG
    private Map<URI,Map<URI,Integer>> getVolumesByCg(Map<URI,Integer> volumeMap) {
        List<Volume> volumes = _dbClient.queryObjectField(Volume.class,
                "consistencyGroup",volumeMap.keySet());
        Map<URI,Map<URI,Integer>> volumeMapByCg = new HashMap<>();
        for(Volume volume : volumes) {
            URI volId = volume.getId();
            URI cgId = volume.getConsistencyGroup();
            if(volumeMapByCg.containsKey(cgId)) {
                volumeMapByCg.get(cgId).put(volId, volumeMap.get(volId));
            } else {
                Map<URI,Integer> newMap = new HashMap<>();
                newMap.put(volId, volumeMap.get(volId));
                volumeMapByCg.put(cgId, newMap);
            }
        }
        return volumeMapByCg;
    }

    @Override
    public void findAndUpdateFreeHLUsForClusterExport(StorageSystem storage, ExportGroup exportGroup, List<URI> initiatorURIs,
            Map<URI, Integer> volumeMap) throws Exception {
        // TODO Auto-generated method stub
    }

}
