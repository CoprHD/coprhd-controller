/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_MIRROR_TO_URI;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnBlockObjectToNativeID;
import static com.emc.storageos.volumecontroller.impl.ControllerUtils.checkCloneConsistencyGroup;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Collections2.transform;
import static java.lang.String.format;
import static java.util.Arrays.asList;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.bind.DataBindingException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshot.TechnologyType;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DecommissionedResource;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageProvider.InterfaceType;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.SynchronizationState;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.model.factories.VolumeFactory;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.srdfcontroller.SRDFDeviceController;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl.Lock;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockConsistencyGroupCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockConsistencyGroupDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockConsistencyGroupUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorDeactivateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorDetachCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorFractureCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorResumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotActivateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotEstablishGroupTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotResyncCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockWaitForSynchronizedCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CleanupMetaVolumeMembersCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneActivateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneCreateWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneFractureCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneResyncCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MultiVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SimpleTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeDetachCloneCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeExpandCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DiscoverTaskCompleter;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.ScanTaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.MetaVolumeRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.ConsistencyUtils;
import com.emc.storageos.volumecontroller.impl.utils.MetaVolumeUtils;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.base.Joiner;

/**
 * Generic Block Controller Implementation that does all of the database
 * operations and calls methods on the array specific implementations
 *
 *
 *
 */
public class BlockDeviceController implements BlockController, BlockOrchestrationInterface {
    // Constants for Event Types
    private static final String EVENT_SERVICE_TYPE = "block";
    private static final String EVENT_SERVICE_SOURCE = "BlockController";
    private DbClient _dbClient;
    private static final Logger _log = LoggerFactory.getLogger(BlockDeviceController.class);
    private static final int SCAN_LOCK_TIMEOUT = 60;   // wait at most 60 seconds for scan lock
    private Map<String, BlockStorageDevice> _devices;

    private RecordableEventManager _eventManager;
    private BlockStorageScheduler _blockScheduler;
    private WorkflowService _workflowService;
    private SRDFDeviceController srdfDeviceController;

    private static final String ATTACH_MIRRORS_WF_NAME = "ATTACH_MIRRORS_WORKFLOW";
    private static final String DETACH_MIRRORS_WF_NAME = "DETACH_MIRRORS_WORKFLOW";
    private static final String RESUME_MIRRORS_WF_NAME = "RESUME_MIRRORS_WORKFLOW";
    private static final String ESTABLISH_VOLUME_MIRROR_GROUP_WF_NAME = "ESTABLISH_VOLUME_MIRROR_GROUP_WORKFLOW";
    private static final String ESTABLISH_VOLUME_SNAPSHOT_GROUP_WF_NAME = "ESTABLISH_VOLUME_SNAPSHOT_GROUP_WORKFLOW";
    private static final String ESTABLISH_VOLUME_FULL_COPY_GROUP_WF_NAME = "ESTABLISH_VOLUME_FULL_COPY_GROUP_WORKFLOW";
    private static final String PAUSE_MIRRORS_WF_NAME = "PAUSE_MIRRORS_WORKFLOW";
    private static final String RESTORE_VOLUME_WF_NAME = "RESTORE_VOLUME_WORKFLOW";
    private static final String EXPAND_VOLUME_WF_NAME = "expandVolume";
    private static final String ROLLBACK_METHOD_NULL = "rollbackMethodNull";
    private static final String TERMINATE_RESTORE_SESSIONS_METHOD = "terminateRestoreSessions";
    private static final String FRACTURE_CLONE_METHOD = "fractureClone";
    private static final String UPDATE_CONSISTENCY_GROUP_WF_NAME = "UPDATE_CONSISTENCY_GROUP_WORKFLOW";
    static final String CREATE_LIST_SNAPSHOT_METHOD = "createListSnapshot";

    public static final String BLOCK_VOLUME_EXPAND_GROUP = "BlockDeviceExpandVolume";

    public static final String RESTORE_VOLUME_STEP = "restoreVolume";
    private static final String RESTORE_VOLUME_METHOD_NAME = "restoreVolume";

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    public void setBlockScheduler(BlockStorageScheduler blockScheduler) {
        _blockScheduler = blockScheduler;
    }

    public void setDevices(Map<String, BlockStorageDevice> deviceInterfaces) {
        _devices = deviceInterfaces;
    }

    public void setEventManager(RecordableEventManager eventManager) {
        _eventManager = eventManager;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        _workflowService = workflowService;
    }

    public BlockStorageDevice getDevice(String deviceType) {
        return _devices.get(deviceType);
    }

    /**
     * Creates a rollback workflow method that does nothing, but allows rollback
     * to continue to prior steps back up the workflow chain.
     *
     * @return A workflow method
     */
    Workflow.Method rollbackMethodNullMethod() {
        return new Workflow.Method(ROLLBACK_METHOD_NULL);
    }

    /**
     * A rollback workflow method that does nothing, but allows rollback
     * to continue to prior steps back up the workflow chain. Can be and is
     * used in workflows in other controllers that invoke operations on this
     * block controller. If the block operation happens to fail, this no-op
     * rollback method is invoked. It says the rollback step succeeded,
     * which will then allow other rollback operations to execute for other
     * workflow steps executed by the other controller.
     *
     * See the VPlexDeviceController restoreVolume method which creates a
     * workflow step that invokes the BlockDeviceController restoreVolume
     * method. The rollback method for this step is this no-op. If the
     * BlockDeviceController restoreVolume step fails, this rollback
     * method is invoked, which simply says the rollback for the step
     * was successful. This in turn allows the other steps in the workflow
     * rollback.
     *
     * @param stepId The id of the step being rolled back.
     *
     * @throws WorkflowException
     */
    public void rollbackMethodNull(String stepId) throws WorkflowException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }

    /**
     * Fail the task
     *
     * @param clazz
     * @param id
     * @param opId
     * @param msg
     */
    @Deprecated
    private void doFailTask(Class<? extends DataObject> clazz, URI id, String opId, String msg) {
        try {
            _dbClient.updateTaskOpStatus(clazz, id, opId, new Operation(Operation.Status.error.name(), msg));
        } catch (DatabaseException ioe) {
            _log.error(ioe.getMessage());
        }
    }

    /**
     * Set the status of operation to 'ready'
     *
     * @param clazz The data object class.
     * @param ids The ids of the data objects for which the task completed.
     * @param opId The task id.
     */
    private void doSuccessTask(
            Class<? extends DataObject> clazz, List<URI> ids, String opId) {
        try {
            for (URI id : ids) {
                _dbClient.ready(clazz, id, opId);
            }
        } catch (DatabaseException ioe) {
            _log.error(ioe.getMessage());
        }
    }

    /**
     * Fail the task. Called when an exception occurs attempting to
     * execute a task on multiple data objects.
     *
     * @param clazz The data object class.
     * @param ids The ids of the data objects for which the task failed.
     * @param opId The task id.
     * @param serviceCoded Original exception.
     */
    private void doFailTask(
            Class<? extends DataObject> clazz, List<URI> ids, String opId, ServiceCoded serviceCoded) {
        try {
            for (URI id : ids) {
                _dbClient.error(clazz, id, opId, serviceCoded);
            }
        } catch (DatabaseException ioe) {
            _log.error(ioe.getMessage());
        }
    }

    /**
     * Fail the task. Called when an exception occurs attempting to
     * execute a task.
     *
     * @param clazz The data object class.
     * @param id The id of the data object for which the task failed.
     * @param opId The task id.
     * @param serviceCoded Original exception.
     */
    private void doFailTask(
            Class<? extends DataObject> clazz, URI id, String opId, ServiceCoded serviceCoded) {

        List<URI> ids = new ArrayList<URI>();
        ids.add(id);
        doFailTask(clazz, ids, opId, serviceCoded);
    }

    /**
     * Create a nice event based on the volume
     *
     * @param volume Volume for which the event is about
     * @param type Type of event such as VolumeCreated or VolumeDeleted
     * @param description Description for the event if needed
     * @param extensions The event extension data
     */
    public void recordVolumeEvent(Volume volume, String type, String description,
            String extensions) {
        // TODO fix the bogus user ID once we have AuthZ working

        RecordableBourneEvent event = ControllerUtils.convertToRecordableBourneEvent(volume, type, description,
                extensions, _dbClient, EVENT_SERVICE_TYPE, RecordType.Event.name(), EVENT_SERVICE_SOURCE);
        try {
            _eventManager.recordEvents(event);
        } catch (Exception ex) {
            _log.error("Failed to record event. Event description: {}. Error: ", description, ex);
        }
    }

    /**
     * Workflow step associated with creating volumes.
     * TODO: RB3294, Do we really need these steps? Can we just call the method directly if that's all its really doing?
     *
     * @param systemURI storage system
     * @param poolURI storage pool
     * @param volumeURIs list of volume uris that were pre-created in the db
     * @param stepId the task id from the workflow service
     * @return true if successful, false otherwise
     */
    public boolean createVolumesStep(URI systemURI, URI poolURI, List<URI> volumeURIs,
            VirtualPoolCapabilityValuesWrapper cosCapabilities, String stepId) throws WorkflowException {
        boolean status = true;      // no error initially
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            // Now call addZones to add all the required zones.
            createVolumes(systemURI, poolURI, volumeURIs, cosCapabilities, stepId);
        } catch (Exception ex) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
            status = false;
        }
        return status;
    }

    static final String CREATE_VOLUMES_STEP_GROUP = "BlockDeviceCreateVolumes";
    static final String MODIFY_VOLUMES_STEP_GROUP = "BlockDeviceModifyVolumes";
    static final String CREATE_MIRRORS_STEP_GROUP = "BlockDeviceCreateMirrors";
    static final String CREATE_CONSISTENCY_GROUP_STEP_GROUP = "BlockDeviceCreateGroup";
    static final String UPDATE_CONSISTENCY_GROUP_STEP_GROUP = "BlockDeviceUpdateGroup";
    static final String CREATE_SNAPSHOTS_STEP_GROUP = "BlockDeviceCreateSnapshots";

    /**
     * {@inheritDoc}
     */
    @Override
    public String addStepsForCreateVolumes(Workflow workflow, String waitFor,
            List<VolumeDescriptor> origVolumes, String taskId) throws ControllerException {

        // Get the list of descriptors the BlockDeviceController needs to create volumes for.
        List<VolumeDescriptor> volumeDescriptors = VolumeDescriptor.filterByType(origVolumes,
                new VolumeDescriptor.Type[] {
                        VolumeDescriptor.Type.BLOCK_DATA,
                        VolumeDescriptor.Type.RP_SOURCE,
                        VolumeDescriptor.Type.RP_JOURNAL,
                        VolumeDescriptor.Type.RP_TARGET,
                        VolumeDescriptor.Type.SRDF_SOURCE,
                        VolumeDescriptor.Type.SRDF_TARGET
                }, null);

        // If no volumes to create, just return
        if (volumeDescriptors.isEmpty()) {
            return waitFor;
        }

        // Segregate by pool to list of volumes.
        Map<URI, Map<Long, List<VolumeDescriptor>>> poolMap = VolumeDescriptor.getPoolSizeMap(volumeDescriptors);

        // Add a Step to create the consistency group if needed
        waitFor = addStepsForCreateConsistencyGroup(workflow, waitFor, volumeDescriptors, CREATE_CONSISTENCY_GROUP_STEP_GROUP);

        // Add a Step for each Pool in each Device.
        // For meta volumes add Step for each meta volume, except vmax thin meta volumes.
        for (URI poolURI : poolMap.keySet()) {
            for (Long volumeSize : poolMap.get(poolURI).keySet()) {
                List<VolumeDescriptor> descriptors = poolMap.get(poolURI).get(volumeSize);
                List<URI> volumeURIs = VolumeDescriptor.getVolumeURIs(descriptors);
                VolumeDescriptor first = descriptors.get(0);
                URI deviceURI = first.getDeviceURI();
                VirtualPoolCapabilityValuesWrapper capabilities = first.getCapabilitiesValues();

                // Check if volumes have to be created as meta volumes
                _log.debug(String.format("Capabilities : isMeta: %s, Meta Type: %s, Member size: %s, Count: %s",
                        capabilities.getIsMetaVolume(), capabilities.getMetaVolumeType(), capabilities.getMetaVolumeMemberSize(),
                        capabilities.getMetaVolumeMemberCount()));

                boolean createAsMetaVolume = capabilities.getIsMetaVolume()
                        || MetaVolumeUtils.createAsMetaVolume(first.getVolumeURI(), _dbClient, capabilities);
                if (createAsMetaVolume) {
                    Volume volume = _dbClient.queryObject(Volume.class, first.getVolumeURI());
                    StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, volume.getStorageController());
                    // For vmax thin meta volumes we can create multiple meta volumes in one smis request
                    if (volume.getThinlyProvisioned() && storageSystem.getSystemType().equals(StorageSystem.Type.vmax.toString())) {
                        workflow.createStep(CREATE_VOLUMES_STEP_GROUP,
                                String.format("Creating meta volumes:%n%s", getVolumesMsg(_dbClient, volumeURIs)),
                                waitFor, deviceURI, getDeviceType(deviceURI),
                                this.getClass(),
                                createMetaVolumesMethod(deviceURI, poolURI, volumeURIs, capabilities),
                                rollbackCreateMetaVolumesMethod(deviceURI, volumeURIs), null);
                    } else {
                        // Add workflow step for each meta volume
                        for (URI metaVolumeURI : volumeURIs) {
                            List<URI> metaVolumeURIs = new ArrayList<URI>();
                            metaVolumeURIs.add(metaVolumeURI);
                            String stepId = workflow.createStepId();
                            workflow.createStep(CREATE_VOLUMES_STEP_GROUP,
                                    String.format("Creating meta volume:%n%s", getVolumesMsg(_dbClient, metaVolumeURIs)),
                                    waitFor, deviceURI, getDeviceType(deviceURI),
                                    this.getClass(),
                                    createMetaVolumeMethod(deviceURI, poolURI, metaVolumeURI, capabilities),
                                    rollbackCreateMetaVolumeMethod(deviceURI, metaVolumeURI, stepId), stepId);
                        }
                    }
                } else {
                    workflow.createStep(CREATE_VOLUMES_STEP_GROUP,
                            String.format("Creating volumes:%n%s", getVolumesMsg(_dbClient, volumeURIs)),
                            waitFor, deviceURI, getDeviceType(deviceURI),
                            this.getClass(),
                            createVolumesMethod(deviceURI, poolURI, volumeURIs, capabilities),
                            rollbackCreateVolumesMethod(deviceURI, volumeURIs), null);
                }
                // Following workflow step is only applicable to HDS Thin Volume modification.
                if (getDeviceType(deviceURI).equalsIgnoreCase(Type.hds.name())) {
                    boolean modifyHitachiVolumeToApplyTieringPolicy = HDSUtils.isVolumeModifyApplicable(
                            first.getVolumeURI(), _dbClient);
                    if (modifyHitachiVolumeToApplyTieringPolicy) {
                        workflow.createStep(MODIFY_VOLUMES_STEP_GROUP,
                                String.format("Modifying volumes:%n%s", getVolumesMsg(_dbClient, volumeURIs)),
                                CREATE_VOLUMES_STEP_GROUP, deviceURI, getDeviceType(deviceURI), this.getClass(),
                                moidfyVolumesMethod(deviceURI, poolURI, volumeURIs),
                                rollbackCreateVolumesMethod(deviceURI, volumeURIs), null);
                    }
                }
            }
        }
        waitFor = CREATE_VOLUMES_STEP_GROUP;

        return waitFor;
    }

    /**
     * Add Steps to create any BLOCK_MIRRORs specified in the VolumeDescriptor list.
     *
     * @param workflow -- The Workflow being built
     * @param waitFor -- Previous steps to waitFor
     * @param volumes -- List<VolumeDescriptors> -- volumes of all types to be processed
     * @return last step added to waitFor
     * @throws ControllerException
     */
    public String addStepsForCreateMirrors(Workflow workflow, String waitFor,
            URI storage, URI sourceVolume, List<URI> mirrorList, boolean isCG) throws ControllerException {
        String stepId = waitFor;
        if (!isCG) {
            for (URI mirror : mirrorList) {
                stepId = workflow.createStep(CREATE_MIRRORS_STEP_GROUP,
                        String.format("Creating mirror for %s", sourceVolume), waitFor,
                        storage, getDeviceType(storage),
                        this.getClass(),
                        createMirrorMethod(storage, asList(mirror), isCG, false),
                        rollbackMirrorMethod(storage, asList(mirror)), null);
            }
        } else {
            stepId = workflow.createStep(CREATE_MIRRORS_STEP_GROUP,
                    String.format("Creating mirror for %s", sourceVolume), waitFor,
                    storage, getDeviceType(storage),
                    this.getClass(),
                    createMirrorMethod(storage, mirrorList, isCG, false),
                    rollbackMirrorMethod(storage, mirrorList), null);
        }
        return stepId;
    }

    /**
     * Add Steps to create the required consistency group
     *
     * @param workflow -- The Workflow being built
     * @param waitFor -- Previous steps to waitFor
     * @param volumesDescriptors -- List<VolumeDescriptors> -- volumes of all types to be processed
     * @return last step added to waitFor
     * @throws ControllerException
     */
    public String addStepsForCreateConsistencyGroup(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumesDescriptors, String stepGroup) throws ControllerException {

        // Filter any BLOCK_DATAs that need to be created.
        List<VolumeDescriptor> volumes = VolumeDescriptor.filterByType(volumesDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_DATA },
                new VolumeDescriptor.Type[] {});

        // If no volumes to be created, just return.
        if (volumes.isEmpty()) {
            return waitFor;
        }

        // Get the consistency group. If no consistency group to be created,
        // just return. Get CG from any descriptor.
        final VolumeDescriptor firstVolume = volumes.get(0);
        if (firstVolume == null || NullColumnValueGetter.isNullURI(firstVolume.getConsistencyGroupURI())) {
            return waitFor;
        }
        final URI consistencyGroupURI = firstVolume.getConsistencyGroupURI();
        final BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupURI);

        if (firstVolume.getType() != null) {
            if (VolumeDescriptor.Type.SRDF_SOURCE.toString().equalsIgnoreCase(firstVolume.getType().toString())
                    || VolumeDescriptor.Type.SRDF_TARGET.toString().equalsIgnoreCase(firstVolume.getType().toString())
                    || VolumeDescriptor.Type.SRDF_EXISTING_SOURCE.toString().equalsIgnoreCase(firstVolume.getType().toString())) {
                return waitFor;
            }
        }

        // Create the CG on each system it has yet to be created on. Note that
        // typically, for a volume creation request in a CG, the system will be
        // the same for all volumes because all volumes will reside in the CG
        // on that array. However, it is also called when creating VPLEX distributed
        // volumes in CGs. In this case, the backend volumes will be on different
        // arrays and we will need the CG created on both backend arrays.
        List<URI> deviceURIs = new ArrayList<URI>();
        for (VolumeDescriptor descr : volumes) {
            // If the descriptor's associated volume is the backing volume for a RP+VPlex
            // journal/target volume, we want to ignore its storage system. We do not want to
            // create backing array consistency groups for RP+VPlex target volumes. Only
            // source volume.
            Volume volume = _dbClient.queryObject(Volume.class, descr.getVolumeURI());
            if (!RPHelper.isAssociatedToRpVplexType(volume, _dbClient, PersonalityTypes.TARGET, PersonalityTypes.METADATA)) {
                URI deviceURI = descr.getDeviceURI();
                if (!deviceURIs.contains(deviceURI)) {
                    deviceURIs.add(deviceURI);
                }
            }
        }

        boolean createdCg = false;
        for (URI deviceURI : deviceURIs) {
            // If the consistency group has already been created in the array, just return
            if (!consistencyGroup.created(deviceURI)) {
                // Create step to create consistency group
                waitFor = workflow.createStep(stepGroup,
                        String.format("Creating consistency group  %s", consistencyGroupURI), waitFor,
                        deviceURI, getDeviceType(deviceURI),
                        this.getClass(),
                        new Workflow.Method("createConsistencyGroup", deviceURI, consistencyGroupURI),
                        new Workflow.Method("deleteConsistencyGroup", deviceURI, consistencyGroupURI, false), null);
                createdCg = true;
                _log.info(String.format("Step created for creating CG [%s] on device [%s]", consistencyGroup.getLabel(), deviceURI));
            }
        }

        if (createdCg) {
            waitFor = stepGroup;
        }

        return waitFor;
    }

    /**
     * Add Steps to add volumes to the required consistency group
     *
     * @param workflow -- The Workflow being built
     * @param waitFor -- Previous steps to waitFor
     * @param volumesDescriptors -- List<VolumeDescriptors> -- volumes of all types to be processed
     * @return last step added to waitFor
     * @throws ControllerException
     */
    public String addStepsForAddToConsistencyGroup(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumesDescriptors) throws ControllerException {

        // Filter any BLOCK_DATAs that need to be created.
        List<VolumeDescriptor> volumes = VolumeDescriptor.filterByType(volumesDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_DATA },
                new VolumeDescriptor.Type[] {});

        // If no volumes to be created, just return.
        if (volumes.isEmpty()) {
            return waitFor;
        }

        // Get the consistency group. If no consistency group,
        // just return. Get CG from any descriptor.
        final VolumeDescriptor firstVolume = volumes.get(0);
        if (firstVolume == null || NullColumnValueGetter.isNullURI(firstVolume.getConsistencyGroupURI())) {
            return waitFor;
        }
        final URI consistencyGroupURI = firstVolume.getConsistencyGroupURI();
        final BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupURI);

        if (firstVolume.getType() != null) {
            if (VolumeDescriptor.Type.SRDF_SOURCE.toString().equalsIgnoreCase(firstVolume.getType().toString())
                    || VolumeDescriptor.Type.SRDF_TARGET.toString().equalsIgnoreCase(firstVolume.getType().toString())
                    || VolumeDescriptor.Type.SRDF_EXISTING_SOURCE.toString().equalsIgnoreCase(firstVolume.getType().toString())) {
                return waitFor;
            }
        }

        // Store map of devices to volumes, we could have multiple devices (example VPLEX metro volume with
        // two different backing arrays).
        Map<URI, List<URI>> deviceURIs = new HashMap<URI, List<URI>>();
        for (VolumeDescriptor descr : volumes) {
            Volume volume = _dbClient.queryObject(Volume.class, descr.getVolumeURI());

            URI deviceURI = descr.getDeviceURI();
            List<URI> volumesToUpdate = deviceURIs.get(deviceURI);
            if (volumesToUpdate == null) {
                volumesToUpdate = new ArrayList<URI>();
                deviceURIs.put(deviceURI, volumesToUpdate);
            }
            volumesToUpdate.add(volume.getId());
        }

        for (Map.Entry<URI, List<URI>> entry : deviceURIs.entrySet()) {
            URI deviceURI = entry.getKey();
            List<URI> volumesToAdd = entry.getValue();

            waitFor = workflow.createStep(UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                    String.format("Updating consistency group  %s", consistencyGroupURI), waitFor,
                    entry.getKey(), getDeviceType(deviceURI),
                    this.getClass(),
                    new Workflow.Method("updateConsistencyGroup", deviceURI, consistencyGroupURI, volumesToAdd, null),
                    null, null);
            _log.info(String.format("Step created for adding volumes [%s] to CG [%s] on device [%s]",
                    Joiner.on("\t").join(volumesToAdd),
                    consistencyGroup.getLabel(),
                    deviceURI));
        }

        return waitFor;
    }

    /**
     * Returns a message containing information about each volume.
     *
     * @param volumeURIs
     * @return
     */
    static public String getVolumesMsg(DbClient dbClient, List<URI> volumeURIs) {
        StringBuilder builder = new StringBuilder();
        for (URI uri : volumeURIs) {
            BlockObject obj = BlockObject.fetch(dbClient, uri);
            if (obj == null) {
                continue;
            }
            builder.append("Volume: " + obj.getLabel() + " (" + obj.getId() + ")");
            if (obj.getWWN() != null) {
                builder.append(" wwn: " + obj.getWWN());
            }
            if (obj.getNativeId() != null) {
                builder.append(" native id: " + obj.getNativeId());
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    /**
     * Return a Workflow.Method for createVolumes.
     *
     * @param systemURI
     * @param poolURI
     * @param volumeURIs
     * @param capabilities
     * @return Workflow.Method
     */
    private Workflow.Method moidfyVolumesMethod(URI systemURI, URI poolURI, List<URI> volumeURIs) {
        return new Workflow.Method("modifyVolumes", systemURI, poolURI, volumeURIs);
    }

    /**
     * {@inheritDoc} NOTE NOTE: The signature here MUST match the Workflow.Method modifyVolumesMethod just above (except opId).
     * Currently this workflow step is used only for Hitachi Thin Volumes modification to update volume tieringPolicy.
     * Hitachi allows setting of tieringpolicy at LDEV level, hence We should have a LDEV id of a LogicalUnit.
     * But LDEV is only created after we LogicalUnit is created. Hence createVolumes workflow includes creation of LU (i.e. LDEV)
     * And LDEV modification (to set tieringPolicy.)
     *
     */
    @Override
    public void modifyVolumes(URI systemURI, URI poolURI, List<URI> volumeURIs, String opId) throws ControllerException {

        List<Volume> volumes = new ArrayList<Volume>();
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                    systemURI);
            List<VolumeTaskCompleter> volumeCompleters = new ArrayList<VolumeTaskCompleter>();
            Iterator<URI> volumeURIsIter = volumeURIs.iterator();
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "moidfyVolumes start - Array:%s Pool:%s", systemURI.toString(),
                    poolURI.toString()));
            while (volumeURIsIter.hasNext()) {
                URI volumeURI = volumeURIsIter.next();
                logMsgBuilder.append(String.format("%nVolume:%s", volumeURI.toString()));
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                volumes.add(volume);
                VolumeUpdateCompleter volumeCompleter = new VolumeUpdateCompleter(
                        volumeURI, opId);
                volumeCompleters.add(volumeCompleter);
            }
            _log.info(logMsgBuilder.toString());
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class, poolURI);
            MultiVolumeTaskCompleter completer = new MultiVolumeTaskCompleter(volumeURIs, volumeCompleters, opId);

            Volume volume = volumes.get(0);
            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
            WorkflowStepCompleter.stepExecuting(completer.getOpId());
            getDevice(storageSystem.getSystemType()).doModifyVolumes(storageSystem,
                    storagePool, opId, volumes, completer);
            logMsgBuilder = new StringBuilder(String.format(
                    "modifyVolumes end - Array:%s Pool:%s", systemURI.toString(),
                    poolURI.toString()));
            volumeURIsIter = volumeURIs.iterator();
            while (volumeURIsIter.hasNext()) {
                logMsgBuilder.append(String.format("%nVolume:%s", volumeURIsIter.next()
                        .toString()));
            }
            _log.info(logMsgBuilder.toString());
        } catch (InternalException e) {
            _log.error(String.format("modifyVolumes Failed - Array: %s Pool:%s Volume:%s",
                    systemURI.toString(), poolURI.toString(), Joiner.on("\t").join(volumeURIs)));
            doFailTask(Volume.class, volumeURIs, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);

        } catch (Exception e) {
            _log.error(String.format("modifyVolumes Failed - Array: %s Pool:%s Volume:%s",
                    systemURI.toString(), poolURI.toString(), Joiner.on("\t").join(volumeURIs)));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURIs, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    /**
     * Return a Workflow.Method for createVolumes.
     *
     * @param systemURI
     * @param poolURI
     * @param volumeURIs
     * @param capabilities
     * @return Workflow.Method
     */
    private Workflow.Method createVolumesMethod(URI systemURI, URI poolURI, List<URI> volumeURIs,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        return new Workflow.Method("createVolumes", systemURI, poolURI, volumeURIs, capabilities);
    }

    /**
     * {@inheritDoc} NOTE NOTE: The signature here MUST match the Workflow.Method createVolumesMethod just above (except opId).
     */
    @Override
    public void createVolumes(URI systemURI, URI poolURI, List<URI> volumeURIs,
            VirtualPoolCapabilityValuesWrapper capabilities, String opId) throws ControllerException {

        boolean opCreateFailed = false;
        List<Volume> volumes = new ArrayList<Volume>();
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                    systemURI);
            List<VolumeTaskCompleter> volumeCompleters = new ArrayList<VolumeTaskCompleter>();
            Iterator<URI> volumeURIsIter = volumeURIs.iterator();
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "createVolumes start - Array:%s Pool:%s", systemURI.toString(),
                    poolURI.toString()));
            while (volumeURIsIter.hasNext()) {
                URI volumeURI = volumeURIsIter.next();
                logMsgBuilder.append(String.format("%nVolume:%s", volumeURI.toString()));
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                volumes.add(volume);
                VolumeCreateCompleter volumeCompleter = new VolumeCreateCompleter(
                        volumeURI, opId);
                volumeCompleters.add(volumeCompleter);
            }
            _log.info(logMsgBuilder.toString());
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class, poolURI);
            MultiVolumeTaskCompleter completer = new MultiVolumeTaskCompleter(volumeURIs, volumeCompleters, opId);

            Volume volume = volumes.get(0);
            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
            WorkflowStepCompleter.stepExecuting(completer.getOpId());
            getDevice(storageSystem.getSystemType()).doCreateVolumes(storageSystem,
                    storagePool, opId, volumes, capabilities, completer);
            logMsgBuilder = new StringBuilder(String.format(
                    "createVolumes end - Array:%s Pool:%s", systemURI.toString(),
                    poolURI.toString()));
            volumeURIsIter = volumeURIs.iterator();
            while (volumeURIsIter.hasNext()) {
                logMsgBuilder.append(String.format("%nVolume:%s", volumeURIsIter.next()
                        .toString()));
            }
            _log.info(logMsgBuilder.toString());
        } catch (InternalException e) {
            _log.error(String.format("createVolume Failed - Array: %s Pool:%s Volume:%s",
                    systemURI.toString(), poolURI.toString(), Joiner.on("\t").join(volumeURIs)));
            doFailTask(Volume.class, volumeURIs, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
            opCreateFailed = true;
        } catch (Exception e) {
            _log.error(String.format("createVolume Failed - Array: %s Pool:%s Volume:%s",
                    systemURI.toString(), poolURI.toString(), Joiner.on("\t").join(volumeURIs)));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURIs, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            opCreateFailed = true;
        }
        if (opCreateFailed) {
            for (Volume volume : volumes) {
                volume.setInactive(true);
                _dbClient.persistObject(volume);
            }
        }
    }

    /**
     * Return a Workflow.Method for rollbackCreateVolumes
     *
     * @param systemURI
     * @param volumeURI
     * @return Workflow.Method
     */
    public static Workflow.Method rollbackCreateVolumesMethod(URI systemURI, List<URI> volumeURIs) {
        return new Workflow.Method("rollBackCreateVolumes", systemURI, volumeURIs);
    }

    /**
     * {@inheritDoc} NOTE: The signature here MUST match the Workflow.Method rollbackCreateVolumesMethod just above (except opId).
     */
    @Override
    public void rollBackCreateVolumes(URI systemURI, List<URI> volumeURIs, String opId) throws ControllerException {
        try {
            String logMsg = String.format(
                    "rollbackCreateVolume start - Array:%s, Volume:%s", systemURI.toString(), Joiner.on(',').join(volumeURIs));
            _log.info(logMsg.toString());

            WorkflowStepCompleter.stepExecuting(opId);

            for (URI volumeId : volumeURIs) {
                Volume volume = _dbClient.queryObject(Volume.class, volumeId);

                // CTRL-5597 clean volumes which have failed only in a multi-volume request
                if (null != volume.getNativeGuid()) {
                    StorageSystem system = _dbClient.queryObject(StorageSystem.class,
                            volume.getStorageController());
                    if (Type.xtremio.toString().equalsIgnoreCase(system.getSystemType())) {
                        continue;
                    }
                }
                // clearing targets explicitly, during vpool change if target volume creation failed for same reason,
                // then we need to clear srdfTargets field for source
                if (null != volume.getSrdfTargets()) {
                    _log.info("Clearing targets for existing source");
                    volume.getSrdfTargets().clear();
                    _dbClient.persistObject(volume);
                }
                // for change Virtual Pool, if failed, clear targets for source
                if (!NullColumnValueGetter.isNullNamedURI(volume.getSrdfParent())) {
                    URI sourceUri = volume.getSrdfParent().getURI();
                    Volume sourceVolume = _dbClient.queryObject(Volume.class, sourceUri);
                    if (null != sourceVolume.getSrdfTargets()) {
                        sourceVolume.getSrdfTargets().clear();
                        _dbClient.persistObject(sourceVolume);
                    }

                    // Clearing target CG
                    URI cgUri = volume.getConsistencyGroup();
                    if (null != cgUri) {
                        BlockConsistencyGroup targetCG = _dbClient.queryObject(
                                BlockConsistencyGroup.class, cgUri);
                        if (null != targetCG && (null == targetCG.getTypes()
                                || null == targetCG.getStorageController())) {
                            _log.info("Set target CG {} inactive", targetCG.getLabel());
                            targetCG.setInactive(true);
                            _dbClient.persistObject(targetCG);
                        }

                        // clear association between target volume and target cg
                        volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                        _dbClient.updateAndReindexObject(volume);
                    }
                }

                // Check for loose export groups associated with this rolled-back volume
                URIQueryResultList exportGroupURIs = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeExportGroupConstraint(
                        volume.getId()), exportGroupURIs);
                while (exportGroupURIs.iterator().hasNext()) {
                    URI exportGroupURI = exportGroupURIs.iterator().next();
                    ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
                    if (!exportGroup.getInactive()) {
                        if (exportGroup.checkInternalFlags(Flag.INTERNAL_OBJECT)) {
                            // Make sure the volume is not in an export mask
                            boolean foundInMask = false;
                            if (exportGroup.getExportMasks() != null) {
                                for (String exportMaskId : exportGroup.getExportMasks()) {
                                    ExportMask mask = _dbClient.queryObject(ExportMask.class, URI.create(exportMaskId));
                                    if (mask.hasVolume(volume.getId())) {
                                        foundInMask = true;
                                        break;
                                    }
                                }
                            }

                            // If we didn't find that volume in a mask, it's OK to remove it.
                            if (!foundInMask) {
                                exportGroup.removeVolume(volume.getId());
                                if (exportGroup.getVolumes().isEmpty()) {
                                    _dbClient.removeObject(exportGroup);
                                } else {
                                    _dbClient.updateAndReindexObject(exportGroup);
                                }
                            }
                        }
                    }
                }
            }

            // Call regular delete volumes
            deleteVolumes(systemURI, volumeURIs, opId);

            logMsg = String.format(
                    "rollbackCreateVolume end - Array:%s, Volume:%s", systemURI.toString(), Joiner.on(',').join(volumeURIs));
            _log.info(logMsg.toString());
        } catch (InternalException e) {
            _log.error(String.format("rollbackCreateVolume Failed - Array:%s, Volume:%s", systemURI.toString(),
                    Joiner.on(',').join(volumeURIs)));
            doFailTask(Volume.class, volumeURIs, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
        } catch (Exception e) {
            _log.error(String.format("rollbackCreateVolume Failed - Array:%s, Volume:%s", systemURI.toString(),
                    Joiner.on(',').join(volumeURIs)));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURIs, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    /**
     * Return a Workflow.Method for createMetaVolumes.
     *
     * @param systemURI
     * @param poolURI
     * @param volumeURIs
     * @param capabilities
     * @return Workflow.Method
     */
    private Workflow.Method createMetaVolumesMethod(URI systemURI, URI poolURI, List<URI> volumeURIs,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        return new Workflow.Method("createMetaVolumes", systemURI, poolURI, volumeURIs, capabilities);
    }

    /**
     * Return a Workflow.Method for rollbackCreateMetaVolumes.
     *
     * @param systemURI
     * @param volumeURIs
     * @return Workflow.Method
     */
    public static Workflow.Method rollbackCreateMetaVolumesMethod(URI systemURI, List<URI> volumeURIs) {
        return rollbackCreateVolumesMethod(systemURI, volumeURIs);
    }

    /**
     * Return a Workflow.Method for createVolumes.
     *
     * @param systemURI
     * @param poolURI
     * @param volumeURI
     * @param capabilities
     * @return Workflow.Method
     */
    private Workflow.Method createMetaVolumeMethod(URI systemURI, URI poolURI, URI volumeURI,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        return new Workflow.Method("createMetaVolume", systemURI, poolURI, volumeURI, capabilities);
    }

    /**
     * Return a Workflow.Method for rollbackCreateMetaVolume.
     *
     * @param systemURI
     * @param volumeURI
     * @return Workflow.Method
     */
    public static Workflow.Method rollbackCreateMetaVolumeMethod(URI systemURI, URI volumeURI, String createMetaVolumeStepId) {
        return new Workflow.Method("rollBackCreateMetaVolume", systemURI, volumeURI, createMetaVolumeStepId);
    }

    /**
     * {@inheritDoc} NOTE: The signature here MUST match the Workflow.Method rollbackCreateMetaVolumeMethod just above (except opId).
     */
    @Override
    public void rollBackCreateMetaVolume(URI systemURI, URI volumeURI, String createStepId, String opId) throws ControllerException {

        try {
            String logMsg = String.format(
                    "rollbackCreateMetaVolume start - Array:%s, Volume:%s", systemURI.toString(), volumeURI.toString());
            _log.info(logMsg.toString());

            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);

            CleanupMetaVolumeMembersCompleter cleanupCompleter = null;
            WorkflowStepCompleter.stepExecuting(opId);
            // Check if we need to cleanup dangling meta members volumes on array.
            // Meta members are temporary array volumes. They only exist until they are added to a meta volume.
            // We store these volumes in WF create step data.
            List<String> metaMembers = (ArrayList<String>) _workflowService.loadStepData(createStepId);
            if (metaMembers != null && !metaMembers.isEmpty()) {
                boolean isWFStep = false;
                cleanupCompleter = new CleanupMetaVolumeMembersCompleter(volumeURI, isWFStep, createStepId, opId);
                getDevice(storageSystem.getSystemType()).doCleanupMetaMembers(storageSystem, volume, cleanupCompleter);
            }
            // TEMPER Used for negative testing.
            // Comment out call to doCleanupMetaMembers above
            // cleanupCompleter.setSuccess(false);
            // // TEMPER
            // Delete meta volume.
            // Delete only if meta members cleanup was successful (in case it was executed).
            if (cleanupCompleter == null || cleanupCompleter.isSuccess()) {
                List<URI> volumeURIs = new ArrayList<URI>();
                volumeURIs.add(volumeURI);
                deleteVolumeStep(systemURI, volumeURIs, opId);
            } else {
                ServiceError serviceError;
                if (cleanupCompleter.getError() != null) {
                    serviceError = cleanupCompleter.getError();
                } else {
                    serviceError = DeviceControllerException.errors.jobFailedOp("CleanupMetaVolumeMembers");
                }
                doFailTask(Volume.class, volumeURI, opId, serviceError);
                WorkflowStepCompleter.stepFailed(opId, serviceError);
            }
            logMsg = String.format(
                    "rollbackCreateMetaVolume end - Array:%s, Volume:%s", systemURI.toString(), volumeURI.toString());
            _log.info(logMsg.toString());
        } catch (InternalException e) {
            _log.error(String.format("rollbackCreateMetaVolume Failed - Array:%s, Volume:%s", systemURI.toString(),
                    volumeURI.toString()));
            doFailTask(Volume.class, volumeURI, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
        } catch (Exception e) {
            _log.error(String.format("rollbackCreateMetaVolume Failed - Array:%s, Volume:%s", systemURI.toString(),
                    volumeURI.toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURI, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    /**
     * Return a Workflow.Method for rollbackExpandVolume.
     *
     * @param systemURI
     * @param volumeURI
     * @return Workflow.Method
     */
    public static Workflow.Method rollbackExpandVolumeMethod(URI systemURI, URI volumeURI, String expandStepId) {
        return new Workflow.Method("rollBackExpandVolume", systemURI, volumeURI, expandStepId);
    }

    /**
     * {@inheritDoc} NOTE: The signature here MUST match the Workflow.Method rollbackExpandVolume just above (except opId).
     */
    @Override
    public void rollBackExpandVolume(URI systemURI, URI volumeURI, String expandStepId, String opId) throws ControllerException {

        try {
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "rollbackExpandVolume start - Array:%s, Volume:%s", systemURI.toString(), volumeURI.toString()));
            _log.info(logMsgBuilder.toString());

            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);

            WorkflowStepCompleter.stepExecuting(opId);

            // Check if we need to cleanup dangling meta members volumes on array.
            // Meta members are temporary array volumes. They only exist until they are added to a meta volume.
            // We store these volumes in WF expand step data.
            List<String> metaMembers = (ArrayList<String>) _workflowService.loadStepData(expandStepId);
            if (metaMembers != null && !metaMembers.isEmpty()) {
                CleanupMetaVolumeMembersCompleter cleanupCompleter = null;
                boolean isWFStep = true;
                cleanupCompleter = new CleanupMetaVolumeMembersCompleter(volumeURI, isWFStep, expandStepId, opId);
                getDevice(storageSystem.getSystemType()).doCleanupMetaMembers(storageSystem, volume, cleanupCompleter);
                // TEMPER Used for negative testing.
                // Comment out call to doCleanupMetaMembers above
                // cleanupCompleter.setSuccess(false);
                // // TEMPER
                if (!cleanupCompleter.isSuccess()) {
                    ServiceError serviceError;
                    if (cleanupCompleter.getError() != null) {
                        serviceError = cleanupCompleter.getError();
                    } else {
                        serviceError = DeviceControllerException.errors.jobFailedOp("CleanupMetaVolumeMembers");
                    }
                    doFailTask(Volume.class, volumeURI, opId, serviceError);
                    WorkflowStepCompleter.stepFailed(opId, serviceError);
                }
            }
            else {
                // We came here if: a. Volume was expanded as a regular volume. or b. Volume was expanded as a meta volume,
                // but there are no dangling meta members left.
                _log.info("rollbackExpandVolume: nothing to cleanup in rollback.");
                WorkflowStepCompleter.stepSucceded(opId);
            }
            logMsgBuilder = new StringBuilder(String.format(
                    "rollbackExpandVolume end - Array:%s, Volume:%s", systemURI.toString(), volumeURI.toString()));
            _log.info(logMsgBuilder.toString());
        } catch (InternalException e) {
            _log.error(String.format("rollbackExpandVolume Failed - Array:%s,  Volume:%s", systemURI.toString(),
                    volumeURI.toString()));
            doFailTask(Volume.class, volumeURI, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
        } catch (Exception e) {
            _log.error(String.format("rollbackExpandVolume Failed - Array:%s, Volume:%s", systemURI.toString(),
                    volumeURI.toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURI, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    /**
     * {@inheritDoc} NOTE NOTE: The signature here MUST match the Workflow.Method createMetaVolumesMethod just above (except opId).
     */
    @Override
    public void createMetaVolumes(URI systemURI, URI poolURI, List<URI> volumeURIs,
            VirtualPoolCapabilityValuesWrapper capabilities, String opId) throws ControllerException {
        boolean opCreateFailed = false;
        List<Volume> volumes = new ArrayList<Volume>();
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                    systemURI);

            List<VolumeTaskCompleter> volumeCompleters = new ArrayList<VolumeTaskCompleter>();
            Iterator<URI> volumeURIsIter = volumeURIs.iterator();
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "createMetaVolumes start - Array:%s Pool:%s", systemURI.toString(),
                    poolURI.toString()));
            while (volumeURIsIter.hasNext()) {
                URI volumeURI = volumeURIsIter.next();
                logMsgBuilder.append(String.format("%nVolume:%s", volumeURI.toString()));
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                volumes.add(volume);
                VolumeCreateCompleter volumeCompleter = new VolumeCreateCompleter(
                        volumeURI, opId);
                volumeCompleters.add(volumeCompleter);
            }
            _log.info(logMsgBuilder.toString());
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class, poolURI);
            MultiVolumeTaskCompleter completer = new MultiVolumeTaskCompleter(volumeURIs, volumeCompleters, opId);

            Volume volume = volumes.get(0);
            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());

            // All volumes are in the same storage pool with the same capacity. Get recommendation for the first volume.
            MetaVolumeRecommendation recommendation = MetaVolumeUtils.getCreateRecommendation(storageSystem, storagePool,
                    volume.getCapacity(), volume.getThinlyProvisioned(), vpool.getFastExpansion(), capabilities);

            for (Volume metaVolume : volumes) {
                MetaVolumeUtils.prepareMetaVolume(metaVolume, recommendation.getMetaMemberSize(), recommendation.getMetaMemberCount(),
                        recommendation.getMetaVolumeType().toString(), _dbClient);
            }

            WorkflowStepCompleter.stepExecuting(completer.getOpId());
            getDevice(storageSystem.getSystemType()).doCreateMetaVolumes(storageSystem,
                    storagePool, volumes, capabilities, recommendation, completer);

            logMsgBuilder = new StringBuilder(String.format(
                    "createMetaVolumes end - Array:%s Pool:%s", systemURI.toString(),
                    poolURI.toString()));
            volumeURIsIter = volumeURIs.iterator();
            while (volumeURIsIter.hasNext()) {
                logMsgBuilder.append(String.format("%nVolume:%s", volumeURIsIter.next()
                        .toString()));
            }
            _log.info(logMsgBuilder.toString());
        } catch (InternalException e) {
            _log.error(String.format("createMetaVolumes Failed - Array: %s Pool:%s Volume:%s",
                    systemURI.toString(), poolURI.toString(), Joiner.on("\t").join(volumeURIs)));
            doFailTask(Volume.class, volumeURIs, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
            opCreateFailed = true;
        } catch (Exception e) {
            _log.error(String.format("createMetaVolumes Failed - Array: %s Pool:%s Volume:%s",
                    systemURI.toString(), poolURI.toString(), Joiner.on("\t").join(volumeURIs)));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURIs, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            opCreateFailed = true;
        }
        if (opCreateFailed) {
            for (Volume volume : volumes) {
                volume.setInactive(true);
                _dbClient.persistObject(volume);
            }
        }
    }

    /**
     * {@inheritDoc} NOTE NOTE: The signature here MUST match the Workflow.Method createMetaVolumeMethod just above (except opId).
     */
    @Override
    public void createMetaVolume(URI systemURI, URI poolURI, URI volumeURI,
            VirtualPoolCapabilityValuesWrapper capabilities, String opId) throws ControllerException {

        try {
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "createMetaVolume start - Array:%s Pool:%s, Volume:%s", systemURI.toString(),
                    poolURI.toString(), volumeURI.toString()));
            _log.info(logMsgBuilder.toString());

            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class, poolURI);
            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());

            MetaVolumeRecommendation recommendation = MetaVolumeUtils.getCreateRecommendation(storageSystem, storagePool,
                    volume.getCapacity(), volume.getThinlyProvisioned(), vpool.getFastExpansion(), capabilities);
            MetaVolumeUtils.prepareMetaVolume(volume, recommendation.getMetaMemberSize(), recommendation.getMetaMemberCount(),
                    recommendation.getMetaVolumeType().toString(), _dbClient);

            VolumeCreateCompleter completer = new VolumeCreateCompleter(volumeURI, opId);
            WorkflowStepCompleter.stepExecuting(completer.getOpId());
            getDevice(storageSystem.getSystemType()).doCreateMetaVolume(storageSystem,
                    storagePool, volume, capabilities, recommendation, completer);

            logMsgBuilder = new StringBuilder(String.format(
                    "createMetaVolume end - Array:%s Pool:%s, Volume:%s", systemURI.toString(),
                    poolURI.toString(), volumeURI.toString()));
            _log.info(logMsgBuilder.toString());
        } catch (InternalException e) {
            _log.error(String.format("createMetaVolume Failed - Array:%s Pool:%s, Volume:%s", systemURI.toString(),
                    poolURI.toString(), volumeURI.toString()));
            doFailTask(Volume.class, volumeURI, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
        } catch (Exception e) {
            _log.error(String.format("createMetaVolume Failed - Array:%s Pool:%s, Volume:%s", systemURI.toString(),
                    poolURI.toString(), volumeURI.toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURI, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    /**
     * Return a Workflow.Method for expandVolume.
     *
     * @param storage storage system
     * @param pool storage pool
     * @param volume volume to expand
     * @param size size to expand to
     * @return Workflow.Method
     */
    public static Workflow.Method expandVolumesMethod(URI storage, URI pool, URI volume, Long size) {
        return new Workflow.Method("expandVolume", storage, pool, volume, size);
    }

    /*
     * {@inheritDoc}
     * <p>
     * Single step workflow to expand volume with rollback.
     */
    @Override
    public void expandBlockVolume(URI storage, URI pool, URI volume, Long size, String opId)
            throws ControllerException {
        SimpleTaskCompleter completer = new SimpleTaskCompleter(Volume.class, volume, opId);

        try {
            // Get a new workflow to execute volume expand
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    EXPAND_VOLUME_WF_NAME, false, opId);
            _log.info("Created new expansion workflow with operation id {}", opId);

            String stepId = workflow.createStepId();
            workflow.createStep(BLOCK_VOLUME_EXPAND_GROUP, String.format(
                    "Expand volume %s", volume), null,
                    storage, getDeviceType(storage),
                    BlockDeviceController.class,
                    expandVolumesMethod(storage, pool, volume, size),
                    rollbackExpandVolumeMethod(storage, volume, stepId),
                    stepId);
            _log.info("Executing workflow plan {}", BLOCK_VOLUME_EXPAND_GROUP);

            workflow.executePlan(completer, String.format(
                    "Expansion of volume %s completed successfully", volume));
        } catch (Exception ex) {
            _log.error("Could not expand volume: " + volume, ex);
            String opName = ResourceOperationTypeEnum.EXPAND_BLOCK_VOLUME.getName();
            ServiceError serviceError = DeviceControllerException.errors.expandVolumeFailed(
                    volume.toString(), opName, ex);
            completer.error(_dbClient, serviceError);
        }
    }

    /*
     * Add workflow steps for volume expand.
     */
    @Override
    public String addStepsForExpandVolume(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws InternalException {

        // The the list of Volumes that the BlockDeviceController needs to process.
        volumeDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] {
                        VolumeDescriptor.Type.BLOCK_DATA,
                        VolumeDescriptor.Type.RP_SOURCE,
                        VolumeDescriptor.Type.RP_TARGET,
                        VolumeDescriptor.Type.RP_EXISTING_SOURCE,
                        VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE,
                        VolumeDescriptor.Type.RP_VPLEX_VIRT_TARGET
                }, null);
        if (volumeDescriptors == null || volumeDescriptors.isEmpty()) {
            return waitFor;
        }

        Map<URI, Long> volumesToExpand = new HashMap<URI, Long>();

        // Check to see if there are any migrations
        List<Migration> migrations = null;
        if (volumeDescriptors != null) {
            List<VolumeDescriptor> migrateDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_MIGRATE_VOLUME }, null);

            if (migrateDescriptors != null && !migrateDescriptors.isEmpty()) {
                // Load the migration objects for use later
                migrations = new ArrayList<Migration>();
                Iterator<VolumeDescriptor> migrationIter = migrateDescriptors.iterator();
                while (migrationIter.hasNext()) {
                    Migration migration = _dbClient.queryObject(Migration.class, migrationIter.next().getMigrationId());
                    migrations.add(migration);
                }
            }
        }

        for (VolumeDescriptor descriptor : volumeDescriptors) {
            // Grab the volume, let's see if an expand is really needed
            Volume volume = _dbClient.queryObject(Volume.class, descriptor.getVolumeURI());

            // If this volume is a VPLEX volume, check to see if we need to expand its backend volume.
            if (volume.getAssociatedVolumes() != null && !volume.getAssociatedVolumes().isEmpty()) {
                for (String volStr : volume.getAssociatedVolumes()) {
                    URI volStrURI = URI.create(volStr);
                    Volume associatedVolume = _dbClient.queryObject(Volume.class, volStrURI);

                    boolean migrationExists = false;
                    // If there are any volumes that are tagged for migration, ignore them.
                    if (migrations != null && !migrations.isEmpty()) {
                        for (Migration migration : migrations) {
                            if (migration.getTarget().equals(volume.getId())) {
                                _log.info("Volume [{}] has a migration, ignore this volume for expand.", volume.getLabel());
                                migrationExists = true;
                                break;
                            }
                        }
                    }

                    // Only expand backend volume if there is no existing migration and
                    // the new size > existing backend volume's provisioned capacity, otherwise we can ignore.
                    if (!migrationExists
                            && associatedVolume.getProvisionedCapacity() != null
                            && descriptor.getVolumeSize() > associatedVolume.getProvisionedCapacity().longValue()) {
                        volumesToExpand.put(volStrURI, descriptor.getVolumeSize());
                    }
                }
            }
            else {
                // Only expand the volume if it's an existing volume (provisoned capacity is not null and not 0) and
                // new size > existing volume's provisioned capacity, otherwise we can ignore.
                if (volume.getProvisionedCapacity() != null
                        && volume.getProvisionedCapacity().longValue() != 0
                        && descriptor.getVolumeSize() > volume.getProvisionedCapacity().longValue()) {
                    volumesToExpand.put(volume.getId(), descriptor.getVolumeSize());
                }
            }
        }

        String nextStep = (volumesToExpand.size() > 0) ? BLOCK_VOLUME_EXPAND_GROUP : waitFor;

        for (Map.Entry<URI, Long> entry : volumesToExpand.entrySet()) {
            _log.info("Creating WF step for Expand Volume for  {}", entry.getKey().toString());
            Volume volumeToExpand = _dbClient.queryObject(Volume.class, entry.getKey());
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, volumeToExpand.getStorageController());
            String stepId = workflow.createStepId();
            workflow.createStep(
                    BLOCK_VOLUME_EXPAND_GROUP,
                    String.format(
                            "Expand Block volume %s", volumeToExpand),
                    waitFor,
                    storage.getId(),
                    getDeviceType(storage.getId()),
                    BlockDeviceController.class,
                    expandVolumesMethod(volumeToExpand.getStorageController(), volumeToExpand.getPool(), volumeToExpand.getId(),
                            entry.getValue()),
                    rollbackExpandVolumeMethod(volumeToExpand.getStorageController(), volumeToExpand.getId(), stepId),
                    stepId);
            _log.info("Creating workflow step {}", BLOCK_VOLUME_EXPAND_GROUP);
        }

        return nextStep;
    }

    @Override
    public void expandVolume(URI storage, URI pool, URI volume, Long size, String opId)
            throws ControllerException {
        try {
            StorageSystem storageObj = _dbClient
                    .queryObject(StorageSystem.class, storage);
            Volume volumeObj = _dbClient.queryObject(Volume.class, volume);
            _log.info(String.format(
                    "expandVolume start - Array: %s Pool:%s Volume:%s, IsMetaVolume: %s, OldSize: %s, NewSize: %s",
                    storage.toString(), pool.toString(), volume.toString(), volumeObj.getIsComposite(), volumeObj.getCapacity(), size));
            StoragePool poolObj = _dbClient.queryObject(StoragePool.class, pool);
            VolumeExpandCompleter completer = new VolumeExpandCompleter(volume, size, opId);

            long metaMemberSize = volumeObj.getIsComposite() ? volumeObj.getMetaMemberSize() : volumeObj.getCapacity();
            long metaCapacity = volumeObj.getIsComposite() ? volumeObj.getTotalMetaMemberCapacity() : volumeObj.getCapacity();

            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volumeObj.getVirtualPool());
            boolean isThinlyProvisioned = volumeObj.getThinlyProvisioned();
            MetaVolumeRecommendation recommendation = MetaVolumeUtils.getExpandRecommendation(storageObj, poolObj,
                    metaCapacity, size, metaMemberSize, isThinlyProvisioned, vpool.getFastExpansion());
            if (recommendation.isCreateMetaVolumes()) {
                // check if we are required to create any members.
                // When expansion size fits into total meta member size, no new members should be created.
                // Also check that this is not recovery to clean dangling meta volumes with zero-capacity expansion.
                if (recommendation.getMetaMemberCount() == 0 && (volumeObj.getMetaVolumeMembers() == null ||
                        volumeObj.getMetaVolumeMembers().isEmpty())) {
                    volumeObj.setCapacity(size);
                    _dbClient.persistObject(volumeObj);
                    _log.info(String
                            .format(
                                    "Expanded volume within its total meta volume capacity (simple case) - Array: %s Pool:%s Volume:%s, IsMetaVolume: %s, Total meta volume capacity: %s, NewSize: %s",
                                    storage.toString(), pool.toString(), volume.toString(), volumeObj.getIsComposite(),
                                    volumeObj.getTotalMetaMemberCapacity(), volumeObj.getCapacity()));
                    completer.ready(_dbClient);
                } else {
                    // set meta related data in task completer
                    long metaMemberCount = volumeObj.getIsComposite() ? recommendation.getMetaMemberCount()
                            + volumeObj.getMetaMemberCount() :
                            recommendation.getMetaMemberCount() + 1;
                    completer.setMetaMemberSize(recommendation.getMetaMemberSize());
                    completer.setMetaMemberCount((int) metaMemberCount);
                    completer.setTotalMetaMembersSize(metaMemberCount * recommendation.getMetaMemberSize());
                    completer.setComposite(true);
                    completer.setMetaVolumeType(recommendation.getMetaVolumeType().toString());

                    getDevice(storageObj.getSystemType()).doExpandAsMetaVolume(storageObj, poolObj,
                            volumeObj, size, recommendation, completer);
                }
            } else {
                // expand as regular volume
                getDevice(storageObj.getSystemType()).doExpandVolume(storageObj, poolObj,
                        volumeObj, size, completer);
            }
            _log.info(String.format("expandVolume end - Array: %s Pool:%s Volume:%s",
                    storage.toString(), pool.toString(), volume.toString()));
        } catch (Exception e) {
            _log.error(String.format("expandVolume Failed - Array: %s Pool:%s Volume:%s",
                    storage.toString(), pool.toString(), volume.toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            List<URI> volumes = Arrays.asList(volume);
            doFailTask(Volume.class, volumes, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    static final String DELETE_VOLUMES_STEP_GROUP = "BlockDeviceDeleteVolumes";

    /**
     * {@inheritDoc}
     */
    @Override
    public String addStepsForDeleteVolumes(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId) throws ControllerException {

        // Add steps for deleting any local mirrors that may be present.
        // waitFor = addStepsForDeleteMirrors(workflow, waitFor, volumes);

        // The the list of Volumes that the BlockDeviceController needs to process.
        volumes = VolumeDescriptor.filterByType(volumes,
                new VolumeDescriptor.Type[] {
                        VolumeDescriptor.Type.BLOCK_DATA,
                        VolumeDescriptor.Type.RP_JOURNAL,
                        VolumeDescriptor.Type.RP_TARGET,
                        VolumeDescriptor.Type.RP_VPLEX_VIRT_JOURNAL,
                        VolumeDescriptor.Type.RP_VPLEX_VIRT_TARGET
                }, null);
        // Check to see if there are any volumes flagged to not be fully deleted.
        // Any flagged volumes will be removed from the list of volumes to delete.
        List<VolumeDescriptor> descriptorsToRemove = VolumeDescriptor.getDoNotDeleteDescriptors(volumes);
        volumes.removeAll(descriptorsToRemove);
        
        // If there are no volumes, just return
        if (volumes.isEmpty()) {
            return waitFor;
        }
        
        // Segregate by device.
        Map<URI, List<VolumeDescriptor>> deviceMap = VolumeDescriptor.getDeviceMap(volumes);

        // Add a step to delete the volumes in each device.
        for (URI deviceURI : deviceMap.keySet()) {
            volumes = deviceMap.get(deviceURI);
            List<URI> volumeURIs = VolumeDescriptor.getVolumeURIs(volumes);

            workflow.createStep(DELETE_VOLUMES_STEP_GROUP,
                    String.format("Deleting volumes:%n%s", getVolumesMsg(_dbClient, volumeURIs)),
                    waitFor, deviceURI, getDeviceType(deviceURI),
                    this.getClass(),
                    deleteVolumesMethod(deviceURI, volumeURIs),
                    null, null);
        }
        return DELETE_VOLUMES_STEP_GROUP;
    }

    /**
     * Return a Workflow.Method for deleteVolumes.
     *
     * @param systemURI
     * @param volumeURIs
     * @return
     */
    private Workflow.Method deleteVolumesMethod(URI systemURI, List<URI> volumeURIs) {
        return new Workflow.Method("deleteVolumes", systemURI, volumeURIs);
    }

    /**
     * {@inheritDoc} NOTE NOTE: The arguments here must match deleteVolumesMethod defined above (except opId).
     */
    @Override
    public void deleteVolumes(URI systemURI, List<URI> volumeURIs, String opId)
            throws ControllerException {
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                    systemURI);
            List<Volume> volumes = new ArrayList<Volume>();
            List<VolumeTaskCompleter> volumeCompleters = new ArrayList<VolumeTaskCompleter>();
            Iterator<URI> volumeURIsIter = volumeURIs.iterator();
            String arrayName = systemURI.toString();
            StringBuilder entryLogMsgBuilder = new StringBuilder(String.format(
                    "deleteVolume start - Array:%s", arrayName));
            StringBuilder exitLogMsgBuilder = new StringBuilder(String.format(
                    "deleteVolume end - Array:%s", arrayName));
            while (volumeURIsIter.hasNext()) {
                URI volumeURI = volumeURIsIter.next();
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                String poolId = NullColumnValueGetter.isNullURI(volume.getPool()) ? "null" : volume.getPool().toString();
                entryLogMsgBuilder.append(String.format("%nPool:%s Volume:%s", poolId, volumeURI.toString()));
                exitLogMsgBuilder.append(String.format("%nPool:%s Volume:%s", poolId, volumeURI.toString()));
                VolumeDeleteCompleter volumeCompleter = new VolumeDeleteCompleter(volumeURI, opId);
                if (volume.getInactive() == false) {
                    // It is possible that there is a BlockSnaphot instance that references the
                    // same device Volume if a VPLEX virtual volume has been created from the
                    // snapshot. If this is the case then the VPLEX volume is being deleted and
                    // volume is represents the source side backend volume for that VPLEX volume.
                    // In this case, we won't delete the backend volume because the volume is
                    // still a block snapshot target and the deletion would fail. The volume will
                    // be deleted when the BlockSnapshot instance is deleted. All we want to do is
                    // mark the Volume instance inactive.
                    List<BlockSnapshot> snapshots = CustomQueryUtility
                            .getActiveBlockSnapshotByNativeGuid(_dbClient, volume.getNativeGuid());
                    if (!snapshots.isEmpty()) {
                        volume.setInactive(true);
                        _dbClient.persistObject(volume);
                        continue;
                    }

                    // Add the volume to the list to delete
                    volumes.add(volume);
                } else {
                    // Add the proper status, since we won't be deleting this volume
                    String opName = ResourceOperationTypeEnum.DELETE_BLOCK_VOLUME.getName();
                    ServiceError serviceError = DeviceControllerException.errors.jobFailedOp(opName);
                    serviceError.setMessage("Volume does not exist or is already deleted");
                    _log.info("Volume does not exist or is already deleted");
                    volumeCompleter.error(_dbClient, serviceError);
                }
                volumeCompleters.add(volumeCompleter);
            }
            _log.info(entryLogMsgBuilder.toString());
            if (!volumes.isEmpty()) {
                WorkflowStepCompleter.stepExecuting(opId);
                TaskCompleter completer = new MultiVolumeTaskCompleter(volumeURIs,
                        volumeCompleters, opId);
                getDevice(storageSystem.getSystemType()).doDeleteVolumes(storageSystem, opId,
                        volumes, completer);
            } else {
                doSuccessTask(Volume.class, volumeURIs, opId);
                WorkflowStepCompleter.stepSucceded(opId);
            }
            _log.info(exitLogMsgBuilder.toString());
        } catch (InternalException e) {
            doFailTask(Volume.class, volumeURIs, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURIs, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, DeviceControllerException.exceptions.unexpectedCondition(e.getMessage()));
        }
    }

    /**
     * Workflow step to delete a volume
     *
     * @param storageURI the storage system ID
     * @param volumes the volume IDs
     * @param token the task ID from the workflow
     * @return true if the step was fired off properly.
     * @throws WorkflowException
     */
    public boolean deleteVolumeStep(URI storageURI, List<URI> volumes, String token) throws WorkflowException {
        boolean status = true;
        String volumeList = Joiner.on(',').join(volumes);
        try {
            WorkflowStepCompleter.stepExecuting(token);
            _log.info("Delete Volume Step Started. " + volumeList);
            deleteVolumes(storageURI, volumes, token);
            _log.info("Delete Volume Step Dispatched: " + volumeList);
        } catch (Exception ex) {
            _log.error("Delete Volume Step Failed." + volumeList);
            String opName = ResourceOperationTypeEnum.DELETE_VOLUME_WORKFLOW_STEP.getName();
            ServiceError serviceError = DeviceControllerException.errors.jobFailedOp(opName);
            WorkflowStepCompleter.stepFailed(token, serviceError);
            status = false;
        }
        return status;
    }

    private void exportMaskUpdate(ExportMask exportMask, Map<URI, Integer> volumeMap, List<Initiator> initiators,
            List<URI> targets) throws Exception {
        if (volumeMap != null) {
            for (URI volume : volumeMap.keySet()) {
                exportMask.addVolume(volume, volumeMap.get(volume));
            }
        }

        if (initiators != null) {
            for (Initiator initiator : initiators) {
                exportMask.addInitiator(initiator);
            }
        }

        if (targets != null) {
            for (URI target : targets) {
                exportMask.addTarget(target);
            }
        }
    }

    /**
     * Select volumes from an export group that resides on a given storage array
     *
     * @param exportGroup
     * @param exportMask
     * @return
     * @throws IOException
     */
    private Map<URI, Integer> selectExportMaskVolumes(ExportGroup exportGroup, ExportMask exportMask)
            throws IOException {
        Map<URI, Integer> volumeMap = new HashMap<URI, Integer>();

        for (String uri : exportGroup.getVolumes().keySet()) {
            URI blockURI;
            try {
                blockURI = new URI(uri);
                BlockObject block = BlockObject.fetch(_dbClient, blockURI);
                if (!block.getStorageController().equals(exportMask.getStorageDevice())) {
                    continue;
                }
                volumeMap.put(blockURI, Integer.valueOf(exportGroup.getVolumes().get(blockURI.toString())));
            } catch (URISyntaxException e) {
                _log.error(e.getMessage(), e);
            }
        }
        return volumeMap;
    }

    @Override
    public void createSingleSnapshot(URI storage, List<URI> snapshotList, Boolean createInactive, Boolean readOnly, String opId)
            throws ControllerException {
        WorkflowStepCompleter.stepExecuting(opId);
        TaskCompleter completer = null;
        try {
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockSnapshotCreateCompleter(snapshotList, opId);
            getDevice(storageObj.getSystemType()).doCreateSingleSnapshot(storageObj, snapshotList, createInactive, readOnly, completer);
        } catch (Exception e) {
            if (completer != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                WorkflowStepCompleter.stepFailed(opId, serviceError);
                completer.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.createVolumeSnapshotFailed(e);
            }
        }
    }

    @Override
    public void createSnapshot(URI storage, List<URI> snapshotList, Boolean createInactive, Boolean readOnly, String opId)
            throws ControllerException {
        TaskCompleter completer = null;
        try {
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockSnapshotCreateCompleter(snapshotList, opId);
            getDevice(storageObj.getSystemType()).doCreateSnapshot(storageObj, snapshotList, createInactive, readOnly, completer);
        } catch (Exception e) {
            if (completer != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                completer.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.createVolumeSnapshotFailed(e);
            }
        }
    }

    @Override
    public void activateSnapshot(URI storage, List<URI> snapshotList, String opId)
            throws ControllerException {
        TaskCompleter completer = null;
        try {
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockSnapshotActivateCompleter(snapshotList, opId);
            getDevice(storageObj.getSystemType()).doActivateSnapshot(storageObj, snapshotList, completer);
        } catch (Exception e) {
            if (completer != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                completer.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.activateVolumeSnapshotFailed(e);
            }
        }
    }

    @Override
    public void deleteSnapshot(URI storage, URI snapshot, String opId) throws ControllerException {
        _log.info("START deleteSnapshot");
        TaskCompleter completer = null;
        WorkflowStepCompleter.stepExecuting(opId);
        try {
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            BlockSnapshot snapObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            completer = BlockSnapshotDeleteCompleter.createCompleter(_dbClient, snapObj, opId);
            getDevice(storageObj.getSystemType()).doDeleteSnapshot(storageObj, snapshot, completer);
            WorkflowStepCompleter.stepSucceded(opId);
        } catch (Exception e) {
            if (completer != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                WorkflowStepCompleter.stepFailed(opId, serviceError);
                completer.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.deleteVolumeSnapshotFailed(e);
            }
        }
    }

    @Override
    public void establishVolumeAndSnapshotGroupRelation(URI storage, URI sourceVolume, URI snapshot, String opId)
            throws ControllerException {
        _log.info("START establishVolumeAndSnapshotGroupRelation workflow");

        Workflow workflow = _workflowService.getNewWorkflow(this, ESTABLISH_VOLUME_SNAPSHOT_GROUP_WF_NAME, false, opId);
        TaskCompleter taskCompleter = null;
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);

        try {
            workflow.createStep("establishStep", "create group relation between Volume group and Snapshot group", null, storage,
                    storageObj.getSystemType(),
                    this.getClass(), establishVolumeAndSnapshotGroupRelationMethod(storage, sourceVolume, snapshot), null, null);

            taskCompleter = new BlockSnapshotEstablishGroupTaskCompleter(snapshot, opId);
            workflow.executePlan(taskCompleter, "Successfully created group relation between Volume group and Snapshot group");
        } catch (Exception e) {
            String msg = String.format("Failed to create group relation between Volume group and Snapshot group."
                    + "Source volume: %s, Snapshot: %s",
                    sourceVolume, snapshot);
            _log.error(msg, e);
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                taskCompleter.error(_dbClient, serviceError);
            }
        }
    }

    private Workflow.Method establishVolumeAndSnapshotGroupRelationMethod(URI storage, URI sourceVolume, URI snapshot) {
        return new Workflow.Method("establishVolumeSnapshotGroupRelation", storage, sourceVolume, snapshot);
    }

    public void establishVolumeSnapshotGroupRelation(
            URI storage, URI sourceVolume, URI snapshot, String opId)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            TaskCompleter completer = new BlockSnapshotEstablishGroupTaskCompleter(snapshot, opId);
            getDevice(storageObj.getSystemType())
                    .doEstablishVolumeSnapshotGroupRelation(
                            storageObj, sourceVolume, snapshot, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    @Override
    public String addStepsForRestoreVolume(Workflow workflow, String waitFor,
            URI storage, URI pool, URI volume, URI snapshot, Boolean updateOpStatus, String taskId,
            BlockSnapshotRestoreCompleter completer) throws ControllerException {

        BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class, snapshot);

        URI parentVolumeURI = snap.getParent().getURI();
        Volume parentVolume = _dbClient.queryObject(Volume.class, parentVolumeURI);
        Volume associatedVPlexVolume =
                Volume.fetchVplexVolume(_dbClient, parentVolume);

        // Do nothing if this is not a native snapshot
        // Do not add block restore steps if this is a snapshot of a VPlex volume. The
        // VPlex controller will add the required block restore steps.
        if (NullColumnValueGetter.isNotNullValue(snap.getTechnologyType()) &&
                !snap.getTechnologyType().equals(TechnologyType.NATIVE.toString()) ||
                associatedVPlexVolume != null) {
            return waitFor;
        }

        Workflow.Method restoreVolumeMethod = new Workflow.Method(
                RESTORE_VOLUME_METHOD_NAME, storage, pool,
                volume, snapshot, Boolean.TRUE);
        workflow.createStep(RESTORE_VOLUME_STEP, String.format(
                "Restore volume %s from snapshot %s",
                volume, snapshot), waitFor,
                storage, getDeviceType(storage),
                BlockDeviceController.class, restoreVolumeMethod, null, null);
        _log.info(
                "Created workflow step to restore block volume {} from snapshot {}",
                volume, snapshot);

        return RESTORE_VOLUME_STEP;
    }

    private static final String BLOCK_VOLUME_RESTORE_GROUP = "BlockDeviceRestoreVolumeGroup";
    private static final String POST_BLOCK_VOLUME_RESTORE_GROUP = "PostBlockDeviceRestoreVolumeGroup";

    @Override
    public void restoreVolume(URI storage, URI pool, URI volume, URI snapshot, Boolean updateOpStatus, String opId)
            throws ControllerException {

        SimpleTaskCompleter completer = new SimpleTaskCompleter(BlockSnapshot.class, snapshot, opId);

        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, RESTORE_VOLUME_WF_NAME, false, opId);
            _log.info("Created new restore workflow with operation id {}", opId);

            Volume sourceVolume = _dbClient.queryObject(Volume.class, volume);
            BlockSnapshot blockSnapshot = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, storage);

            String description = String.format("Restore volume %s from snapshot %s", volume, snapshot);
            String waitFor = null;

            /**
             * We need to split the SRDF link for R2 snap restore if it is not paused already.
             * Refer OPT#476788
             */
            if (isNonSplitSRDFTargetVolume(sourceVolume)) {
                URI srdfSourceVolumeURI = sourceVolume.getSrdfParent().getURI();
                Volume srdfSourceVolume = _dbClient.queryObject(Volume.class, srdfSourceVolumeURI);
                URI srdfSourceStorageSystemURI = srdfSourceVolume.getStorageController();
                // split all members the group
                Workflow.Method splitMethod = srdfDeviceController.splitSRDFLinkMethod(srdfSourceStorageSystemURI,
                        srdfSourceVolumeURI, volume, false);
                Workflow.Method splitRollbackMethod = srdfDeviceController.resumeSyncPairMethod(srdfSourceStorageSystemURI,
                        srdfSourceVolumeURI, volume);

                waitFor = workflow.createStep(SRDFDeviceController.SPLIT_SRDF_MIRRORS_STEP_GROUP,
                        SRDFDeviceController.SPLIT_SRDF_MIRRORS_STEP_DESC, waitFor, srdfSourceStorageSystemURI,
                        getDeviceType(srdfSourceStorageSystemURI), SRDFDeviceController.class, splitMethod,
                        splitRollbackMethod, null);
            }

            waitFor = workflow.createStep(BLOCK_VOLUME_RESTORE_GROUP, description, waitFor,
                    storage, getDeviceType(storage), BlockDeviceController.class,
                    restoreVolumeMethod(storage, pool, volume, snapshot, updateOpStatus),
                    rollbackMethodNullMethod(), null);

            // Skip the step for VMAX3, as restore operation may still be in progress (OPT#476325)
            // Regardless, termination of restore session should be call before restore
            // Note this is not needed for VNX
            if (!system.checkIfVmax3()) {
                addPostRestoreVolumeSteps(workflow, system, sourceVolume, blockSnapshot, waitFor);
            }

            _log.info("Executing workflow {}", BLOCK_VOLUME_RESTORE_GROUP);

            String msg = String.format("Restore of volume %s from %s completed successfully", volume, snapshot);
            workflow.executePlan(completer, msg);
        } catch (Exception e) {
            String msg = String.format("Could not restore volume %s from snapshot %s", volume, snapshot);
            _log.error(msg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
        }
    }

    private boolean isNonSplitSRDFTargetVolume(Volume volume) {
        _log.info("volume.getPersonality() : {} volume.getLinkStatus() : {} ", volume.getPersonality(), volume.getLinkStatus());
        return (volume != null && !NullColumnValueGetter.isNullNamedURI(volume.getSrdfParent()) &&
                Volume.PersonalityTypes.TARGET.toString().equalsIgnoreCase(volume.getPersonality())
                && !(Volume.LinkStatus.FAILED_OVER.name().equalsIgnoreCase(volume.getLinkStatus())
                || Volume.LinkStatus.SUSPENDED.name().equalsIgnoreCase(volume.getLinkStatus())
                || Volume.LinkStatus.SPLIT.name().equalsIgnoreCase(volume.getLinkStatus())));
    }

    /**
     * Return a Workflow.Method for restoreVolume
     *
     * @param storage storage system
     * @param pool storage pool
     * @param volume target of restore operation
     * @param snapshot snapshot to restore from
     * @param updateOpStatus update operation status flag
     * @return Workflow.Method
     */
    public static Workflow.Method restoreVolumeMethod(URI storage, URI pool, URI volume, URI snapshot,
            Boolean updateOpStatus) {
        return new Workflow.Method("restoreVolumeStep", storage, pool, volume, snapshot, updateOpStatus);
    }

    public boolean restoreVolumeStep(URI storage, URI pool, URI volume, URI snapshot, Boolean updateOpStatus,
            String opId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            StorageSystem storageDevice = _dbClient.queryObject(StorageSystem.class, storage);
            BlockSnapshot snapObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            completer = new BlockSnapshotRestoreCompleter(snapObj, opId, updateOpStatus);
            getDevice(storageDevice.getSystemType()).doRestoreFromSnapshot(storageDevice, volume, snapshot, completer);
        } catch (Exception e) {
            _log.error(String.format("restoreVolume failed - storage: %s, pool: %s, volume: %s, snapshot: %s",
                    storage.toString(), pool.toString(), volume.toString(), snapshot.toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
            doFailTask(BlockSnapshot.class, snapshot, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            return false;
        }
        return true;
    }

    private void addPostRestoreVolumeSteps(Workflow workflow, StorageSystem system, Volume sourceVolume,
            BlockSnapshot blockSnapshot, String waitFor) {
        _log.info("Creating post restore volume steps");
        if (Type.vmax.toString().equalsIgnoreCase(system.getSystemType())) {
            _log.info("Adding terminate restore session post-step for VMAX snapshot {}", blockSnapshot.getId());
            String description = String.format("Terminating VMAX restore session from %s to %s", blockSnapshot.getId(),
                    sourceVolume.getId());
            workflow.createStep(POST_BLOCK_VOLUME_RESTORE_GROUP, description, waitFor,
                    system.getId(), system.getSystemType(), BlockDeviceController.class,
                    terminateRestoreSessionsMethod(system.getId(), sourceVolume.getId(), blockSnapshot.getId()),
                    null, null);
        }
    }

    public static Workflow.Method terminateRestoreSessionsMethod(URI storage, URI source, URI snapshot) {
        return new Workflow.Method(TERMINATE_RESTORE_SESSIONS_METHOD, storage, source, snapshot);
    }

    public boolean terminateRestoreSessions(URI storage, URI source, URI snapshot, String opId) {
        _log.info("Terminating restore sessions for snapshot: {}", snapshot);
        TaskCompleter completer = null;
        try {
            StorageSystem storageDevice = _dbClient.queryObject(StorageSystem.class, storage);
            BlockSnapshot snapObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            completer = new SimpleTaskCompleter(BlockSnapshot.class, snapshot, opId);
            WorkflowStepCompleter.stepExecuting(opId);
            // Synchronous operation
            getDevice(storageDevice.getSystemType()).doTerminateAnyRestoreSessions(storageDevice, source, snapObj,
                    completer);
            completer.ready(_dbClient);
        } catch (Exception e) {
            _log.error(
                    String.format("Terminate restore sessions step failed - storage: %s, volume: %s, snapshot: %s",
                            storage.toString(), source.toString(), snapshot.toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
            doFailTask(BlockSnapshot.class, snapshot, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            return false;
        }

        return true;
    }

    public Workflow.Method createMirrorMethod(URI storage, List<URI> mirrorList, Boolean isCG, Boolean createInactive) {
        return new Workflow.Method("createMirror", storage, mirrorList, isCG, createInactive);
    }

    /**
     * {@inheritDoc} NOTE NOTE: The signature here MUST match the Workflow.Method createMirrorMethod just above (except opId).
     */
    @Override
    public void createMirror(URI storage, List<URI> mirrorList, Boolean isCG, Boolean createInactive, String opId)
            throws ControllerException {
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            if (!isCG) {
                completer = new BlockMirrorCreateCompleter(mirrorList.get(0), opId);
                getDevice(storageObj.getSystemType()).doCreateMirror(storageObj, mirrorList.get(0), createInactive, completer);
            } else {
                completer = new BlockMirrorCreateCompleter(mirrorList, opId);
                getDevice(storageObj.getSystemType()).doCreateGroupMirrors(storageObj, mirrorList, createInactive, completer);
            }
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            if (completer != null) {
                completer.error(_dbClient, serviceError);
            }
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    public Workflow.Method rollbackMirrorMethod(URI storage, List<URI> mirrorList) {
        return new Workflow.Method("rollbackMirror", storage, mirrorList);
    }

    /**
     * {@inheritDoc} NOTE NOTE: The signature here MUST match the Workflow.Method rollbackMirrorMethod just above (except opId).
     */
    public void rollbackMirror(URI storage, List<URI> mirrorList, String taskId) {
        WorkflowStepCompleter.stepExecuting(taskId);

        try {
            List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
            boolean isCG = isCGMirror(mirrorList.get(0), _dbClient);
            List<BlockMirror> mirrorsNoRollback = new ArrayList<BlockMirror>();
            for (BlockMirror mirror : mirrors) {
                // for CG mirror, filter out mirrors with invalid replication group. It is necessary as the fracture/detach/delete will
                // expect mirrors with valid replication group
                // for non CG mirror, filter out mirror with no native Id
                if ((isCG && NullColumnValueGetter.isNullValue(mirror.getReplicationGroupInstance()) || (!isCG && isNullOrEmpty(mirror
                        .getNativeId())))) {
                    mirror.setInactive(true);
                    mirrorsNoRollback.add(mirror);
                }
            }

            if (!mirrorsNoRollback.isEmpty()) {
                _dbClient.persistObject(mirrorsNoRollback);
                mirrors.removeAll(mirrorsNoRollback);
            }

            if (!mirrors.isEmpty()) {
                List<URI> mirrorURIsToRollback = new ArrayList<URI>(transform(mirrors, FCTN_MIRROR_TO_URI));
                String mirrorNativeIds = Joiner.on(", ").join(transform(mirrors, fctnBlockObjectToNativeID()));

                if (mirrorIsPausable(mirrors)) {
                    _log.info("Attempting to fracture {} for rollback", mirrorNativeIds);
                    fractureMirror(storage, mirrorURIsToRollback, isCG, false, taskId);
                }

                _log.info("Attempting to detach {} for rollback", mirrorNativeIds);
                detachMirror(storage, mirrorURIsToRollback, isCG, false, taskId);
                _log.info("Attempting to delete {} for rollback", mirrorNativeIds);
                deleteMirror(storage, mirrorURIsToRollback, isCG, taskId);
            }
            WorkflowStepCompleter.stepSucceded(taskId);
        } catch (InternalException ie) {
            _log.error(String.format("rollbackMirror Failed - Array:%s, Mirror:%s", storage, Joiner.on("\t").join(mirrorList)));
            doFailTask(Volume.class, mirrorList, taskId, ie);
            WorkflowStepCompleter.stepFailed(taskId, ie);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(taskId, serviceError);
            doFailTask(Volume.class, mirrorList, taskId, serviceError);
        }
    }

    @Override
    public void attachNativeContinuousCopies(URI storage, URI sourceVolume, List<URI> mirrorList, String opId) throws ControllerException {
        _log.info("START attach continuous copies workflow");

        Workflow workflow = _workflowService.getNewWorkflow(this, ATTACH_MIRRORS_WF_NAME, true, opId);
        TaskCompleter taskCompleter = null;

        Volume sourceVolumeObj = _dbClient.queryObject(Volume.class, sourceVolume);
        boolean isCG = sourceVolumeObj.isInCG();
        try {
            addStepsForCreateMirrors(workflow, null, storage, sourceVolume, mirrorList, isCG);
            taskCompleter = new BlockMirrorTaskCompleter(BlockMirror.class, mirrorList, opId);
            workflow.executePlan(taskCompleter, "Successfully attached continuous copies");
        } catch (Exception e) {
            String msg = String.format("Failed to execute attach continuous copies workflow for volume %s",
                    sourceVolume);

            _log.error(msg, e);
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                taskCompleter.error(_dbClient, serviceError);
            }
        }
    }

    @Override
    public void detachNativeContinuousCopies(URI storage, List<URI> mirrors, List<URI> promotees,
            String opId) throws ControllerException {
        _log.info("START detach continuous copies workflow");

        Workflow workflow = _workflowService.getNewWorkflow(this, DETACH_MIRRORS_WF_NAME, false, opId);
        TaskCompleter taskCompleter = null;

        try {
            addStepsForPromoteMirrors(workflow, null, mirrors, promotees);

            // There is a task for the source volume, as well as for each newly promoted volume
            List<URI> volumesWithTasks = new ArrayList<URI>(promotees);
            volumesWithTasks.addAll(getSourceVolumesFromURIs(mirrors));
            taskCompleter = new BlockMirrorTaskCompleter(Volume.class, volumesWithTasks, opId);
            ControllerUtils.checkMirrorConsistencyGroup(mirrors, _dbClient, taskCompleter);

            workflow.executePlan(taskCompleter, "Successfully detached continuous copies");
        } catch (Exception e) {
            List<Volume> promotedVolumes = _dbClient.queryObject(Volume.class, promotees);
            for (Volume promotedVolume : promotedVolumes) {
                promotedVolume.setInactive(true);
            }
            _dbClient.persistObject(promotedVolumes);

            String msg = String.format("Failed to execute detach continuous copies workflow for mirrors: %s", mirrors);
            _log.error(msg, e);
        }
    }

    private List<URI> getSourceVolumesFromURIs(List<URI> mirrorList) {
        List<URI> sourceVolumes = new ArrayList<URI>();
        for (URI mirror : mirrorList) {
            BlockMirror mirrorObj = _dbClient.queryObject(BlockMirror.class, mirror);
            sourceVolumes.add(mirrorObj.getSource().getURI());
        }

        return sourceVolumes;
    }

    private List<URI> getSourceVolumes(List<BlockMirror> mirrorList) {
        List<URI> sourceVolumes = new ArrayList<URI>();
        for (BlockMirror mirror : mirrorList) {
            sourceVolumes.add(mirror.getSource().getURI());
        }

        return sourceVolumes;
    }

    public Workflow.Method removeMirrorFromGroupMethod(URI storage, List<URI> mirrorList) {
        return new Workflow.Method("removeMirrorFromGroup", storage, mirrorList);
    }

    public void removeMirrorFromGroup(URI storage, List<URI> mirrorList, String opId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, mirrorList.get(0));

            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockMirrorTaskCompleter(BlockMirror.class, mirrorList, opId);
            getDevice(storageObj.getSystemType()).doRemoveMirrorFromDeviceMaskingGroup(storageObj,
                    mirrorList, completer);
        } catch (Exception e) {
            if (completer != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                completer.error(_dbClient, serviceError);
            }
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    public Workflow.Method fractureMirrorMethod(URI storage, List<URI> mirrorList, Boolean isCG, Boolean sync) {
        return new Workflow.Method("fractureMirror", storage, mirrorList, isCG, sync);
    }

    /**
     * {@inheritDoc} NOTE NOTE: The signature here MUST match the Workflow.Method fractureMirrorMethod just above (except opId).
     */
    public void fractureMirror(URI storage, List<URI> mirrorList, Boolean isCG, Boolean sync, String opId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockMirrorFractureCompleter(mirrorList, opId);
            if (!isCG) {
                getDevice(storageObj.getSystemType()).doFractureMirror(storageObj, mirrorList.get(0), sync, completer);
            } else {
                completer.addConsistencyGroupId(ConsistencyUtils.getMirrorsConsistencyGroup(mirrorList, _dbClient).getId());
                getDevice(storageObj.getSystemType()).doFractureGroupMirrors(storageObj, mirrorList, sync, completer);
            }
        } catch (Exception e) {
            if (completer != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                completer.error(_dbClient, serviceError);
            }
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private boolean isCGMirror(URI mirrorURI, DbClient dbClient) {
        BlockMirror mirror = dbClient.queryObject(BlockMirror.class, mirrorURI);
        Volume sourceVolume = dbClient.queryObject(Volume.class, mirror.getSource());
        return sourceVolume.isInCG();
    }

    @Override
    public void pauseNativeContinuousCopies(URI storage, List<URI> mirrors, Boolean sync,
            String opId) throws ControllerException {
        _log.info("START pause continuous copies workflow");

        boolean isCG = isCGMirror(mirrors.get(0), _dbClient);
        if (mirrors.size() == 1 || isCG) {
            fractureMirror(storage, mirrors, isCG, sync, opId);
            return;
        }

        Workflow workflow = _workflowService.getNewWorkflow(this, PAUSE_MIRRORS_WF_NAME, false, opId);
        TaskCompleter taskCompleter = null;
        BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, mirrors.get(0));
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);

        try {
            for (URI mirrorUri : mirrors) {
                BlockMirror blockMirror = _dbClient.queryObject(BlockMirror.class, mirrorUri);
                if (!mirrorIsPausable(asList(blockMirror))) {
                    String errorMsg = format("Can not pause continuous copy %s with synchronization state %s for volume %s",
                            blockMirror.getId(), blockMirror.getSyncState(), blockMirror.getSource().getURI());
                    _log.error(errorMsg);
                    String opName = ResourceOperationTypeEnum.PAUSE_NATIVE_CONTINUOUS_COPIES.getName();
                    ServiceError serviceError = DeviceControllerException.errors.jobFailedOp(opName);
                    WorkflowStepCompleter.stepFailed(opId, serviceError);
                    throw new IllegalStateException(errorMsg);
                }
                workflow.createStep("pauseMirror", "pause mirror", null, storage, storageObj.getSystemType(),
                        this.getClass(), fractureMirrorMethod(storage, asList(mirrorUri), isCG, sync), null, null);
            }

            taskCompleter = new BlockMirrorTaskCompleter(Volume.class, asList(mirror.getSource().getURI()), opId);
            workflow.executePlan(taskCompleter, "Successfully paused continuous copies");
        } catch (Exception e) {
            String msg = String.format("Failed to execute pause continuous copies workflow for volume %s",
                    mirror.getSource().getURI());

            _log.error(msg, e);
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                taskCompleter.error(_dbClient, serviceError);
            }
        }
    }

    @Override
    public void resumeNativeContinuousCopies(URI storage, List<URI> mirrors, String opId) throws ControllerException {
        _log.info("START resume continuous copies workflow");

        Workflow workflow = _workflowService.getNewWorkflow(this, RESUME_MIRRORS_WF_NAME, false, opId);
        TaskCompleter taskCompleter = null;
        List<BlockMirror> mirrorList = _dbClient.queryObject(BlockMirror.class, mirrors);
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        List<URI> sourceVolumes = getSourceVolumes(mirrorList);

        try {
            taskCompleter = new BlockMirrorTaskCompleter(Volume.class, sourceVolumes, opId);
            boolean isCG = ControllerUtils.checkMirrorConsistencyGroup(mirrors, _dbClient, taskCompleter);

            if (!isCG) {
                for (BlockMirror blockMirror : mirrorList) {
                    if (SynchronizationState.FRACTURED.toString().equals(blockMirror.getSyncState())) {
                        workflow.createStep("resumeStep", "resume", null, storage, storageObj.getSystemType(),
                                this.getClass(), resumeNativeContinuousCopyMethod(storage, asList(blockMirror.getId()), isCG), null, null);
                    }
                }
            } else {
                if (hasFracturedState(mirrorList)) {
                    workflow.createStep("resumeStep", "resume", null, storage, storageObj.getSystemType(),
                            this.getClass(), resumeNativeContinuousCopyMethod(storage, mirrors, isCG), null, null);
                }
            }

            workflow.executePlan(taskCompleter, "Successfully resumed continuous copies");
        } catch (Exception e) {
            String msg = String.format("Failed to execute resume continuous copies workflow for volume %s",
                    Joiner.on("\t").join(sourceVolumes));

            _log.error(msg, e);
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                taskCompleter.error(_dbClient, serviceError);
            }
        }
    }

    // Any of mirrors in Fractured state will return true
    private boolean hasFracturedState(List<BlockMirror> mirrorList) {
        for (BlockMirror mirror : mirrorList) {
            if (SynchronizationState.FRACTURED.toString().equals(mirror.getSyncState())) {
                return true;
            }
        }

        return false;
    }

    private Workflow.Method resumeNativeContinuousCopyMethod(URI storage, List<URI> mirrorList, Boolean isCG) {
        return new Workflow.Method("resumeNativeContinuousCopy", storage, mirrorList, isCG);
    }

    public void resumeNativeContinuousCopy(URI storage, List<URI> mirrorList, Boolean isCG, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            TaskCompleter completer = new BlockMirrorResumeCompleter(mirrorList, opId);
            if (!isCG) {
                getDevice(storageObj.getSystemType()).doResumeNativeContinuousCopy(storageObj, mirrorList.get(0), completer);
            } else {
                completer.addConsistencyGroupId(ConsistencyUtils.getMirrorsConsistencyGroup(mirrorList, _dbClient).getId());
                getDevice(storageObj.getSystemType()).doResumeGroupNativeContinuousCopies(storageObj, mirrorList, completer);
            }
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    public Workflow.Method detachMirrorMethod(URI storage, List<URI> mirrorList, Boolean isCG) {
        return new Workflow.Method("detachMirror", storage, mirrorList, isCG, true);
    }

    /**
     * {@inheritDoc} NOTE NOTE: The signature here MUST match the Workflow.Method detachMirrorMethod just above (except opId).
     */
    @Override
    public void detachMirror(URI storage, List<URI> mirrorList, Boolean isCG,
            Boolean deleteGroup, String opId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            _log.info("Start detach Mirror for mirror {}, isCG {}", mirrorList, isCG);
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockMirrorDetachCompleter(mirrorList, opId);
            if (!isCG) {
                getDevice(storageObj.getSystemType()).doDetachMirror(storageObj, mirrorList.get(0), completer);
            } else {
                completer.addConsistencyGroupId(ConsistencyUtils.getMirrorsConsistencyGroup(mirrorList, _dbClient).getId());
                getDevice(storageObj.getSystemType()).doDetachGroupMirrors(storageObj, mirrorList, deleteGroup, completer);
            }
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            if (completer != null) {
                completer.error(_dbClient, serviceError);
            }
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    public String addStepsForDetachMirror(Workflow workflow, String waitFor,
            String stepGroup, List<URI> mirrorList, Boolean isCG) throws ControllerException {
        List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
        URI controller = mirrors.get(0).getStorageController();

        String stepId = null;

        // Optionally create a step to pause (fracture) the mirror
        if (mirrorIsPausable(mirrors)) {

            stepId = workflow.createStep(stepGroup,
                    String.format("Fracture mirror: %s", mirrorList.get(0)),
                    waitFor, controller, getDeviceType(controller),
                    this.getClass(),
                    fractureMirrorMethod(controller, mirrorList, isCG, false),
                    null, null);
        }

        // Create a step to detach the mirror
        stepId = workflow.createStep(stepGroup,
                String.format("Detach mirror: %s", mirrorList.get(0)),
                stepId == null ? waitFor : stepId,
                controller, getDeviceType(controller),
                this.getClass(),
                detachMirrorMethod(controller, mirrorList, isCG),
                null, null);

        return stepId;
    }

    private String addStepsToRemoveMirrorFromGroup(Workflow workflow,
            String waitFor, String stepGroup, List<URI> mirrorList) {
        List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
        URI controller = mirrors.get(0).getStorageController();
        String stepId = workflow.createStep(stepGroup,
                String.format("Remove mirror from DeviceMaskingGroup: %s", mirrorList.get(0)),
                waitFor, controller, getDeviceType(controller),
                this.getClass(),
                removeMirrorFromGroupMethod(controller, mirrorList),
                null, null);

        return stepId;
    }

    public static final String PROMOTE_MIRROR_STEP_GROUP = "BlockDevicePromoteMirror";

    /**
     * Adds the additional steps necessary to promote mirrors to regular block volumes
     *
     * @param workflow
     * @param waitFor
     * @param descriptors
     * @param promotees
     * @return
     * @throws ControllerException
     */
    public String addStepsForPromoteMirrors(Workflow workflow, String waitFor,
            List<URI> mirrorList, List<URI> promotees)
            throws ControllerException {
        boolean isCG = isCGMirror(mirrorList.get(0), _dbClient);
        List<Volume> promotedVolumes = _dbClient.queryObject(Volume.class, promotees);
        if (!isCG) {
            List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
            for (BlockMirror mirror : mirrors) {
                URI controller = mirror.getStorageController();

                // Add steps for detaching the mirror
                String stepId = addStepsForDetachMirror(workflow, waitFor, PROMOTE_MIRROR_STEP_GROUP, mirrorList, isCG);

                // Find the volume this mirror will be promoted to
                URI promotedVolumeForMirror = findPromotedVolumeForMirror(mirror.getId(), promotedVolumes);

                // Create a step for promoting the mirror.
                stepId = workflow.createStep(PROMOTE_MIRROR_STEP_GROUP,
                        String.format("Promote mirror: %s", mirror.getId()),
                        stepId, controller, getDeviceType(controller),
                        this.getClass(),
                        promoteMirrorMethod(asList(mirror.getId()), asList(promotedVolumeForMirror), isCG),
                        null, null);
            }
        } else {
            BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, mirrorList.get(0));
            URI controller = mirror.getStorageController();

            // Add steps for detaching the mirror
            String stepId = addStepsForDetachMirror(workflow, waitFor, PROMOTE_MIRROR_STEP_GROUP, mirrorList, isCG);

            // Find the volumes this set of mirrors will be promoted to
            List<URI> promotedVolumesForMirrors = findPromotedVolumesForMirrors(mirrorList, promotedVolumes);

            // Create a step for promoting the mirrors.
            stepId = workflow.createStep(PROMOTE_MIRROR_STEP_GROUP,
                    String.format("Promote mirrors: %s", Joiner.on("\t").join(mirrorList)),
                    stepId, controller, getDeviceType(controller),
                    this.getClass(),
                    promoteMirrorMethod(mirrorList, promotedVolumesForMirrors, isCG),
                    null, null);
        }

        return PROMOTE_MIRROR_STEP_GROUP;
    }

    private URI findPromotedVolumeForMirror(URI mirror, List<Volume> promotedVolumes) {
        for (Volume promotee : promotedVolumes) {
            OpStatusMap statusMap = promotee.getOpStatus();
            for (Map.Entry<String, Operation> entry : statusMap.entrySet()) {
                Operation operation = entry.getValue();
                if (operation.getAssociatedResourcesField().contains(mirror.toString())) {
                    return promotee.getId();
                }
            }
        }
        throw new IllegalStateException("No volume available for the promotion of mirror " + mirror);
    }

    private List<URI> findPromotedVolumesForMirrors(List<URI> mirrorList, List<Volume> promotedVolumes) {
        List<URI> orderedPromotedVolumes = new ArrayList<URI>(mirrorList.size());
        for (URI mirror : mirrorList) {
            orderedPromotedVolumes.add(findPromotedVolumeForMirror(mirror, promotedVolumes));
        }

        return orderedPromotedVolumes;
    }

    public Workflow.Method promoteMirrorMethod(List<URI> mirrorList, List<URI> promotedVolumeList, Boolean isCG) {
        return new Workflow.Method("promoteMirror", mirrorList, promotedVolumeList, isCG);
    }

    public void promoteMirror(List<URI> mirrorList, List<URI> promotedVolumeList, Boolean isCG, String opId) {
        _log.info("START promoteMirror");
        Volume promoted = null;
        try {
            List<BlockMirror> mirrors = new ArrayList<BlockMirror>(mirrorList.size());
            List<Volume> promotedVolumes = new ArrayList<Volume>(mirrorList.size());
            int count = 0;

            for (URI id : mirrorList) {
                BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, id);
                Volume source = _dbClient.queryObject(Volume.class, mirror.getSource().getURI());
                String promotedLabel = ControllerUtils.getMirrorLabel(source.getLabel(), mirror.getLabel());
                if (isCG) {
                    promotedLabel = mirror.getLabel();
                }

                promoted = VolumeFactory.newInstance(mirror);
                promoted.setId(promotedVolumeList.get(count++));
                promoted.setLabel(promotedLabel);
                promotedVolumes.add(promoted);
                _log.info("Promoted mirror {} to volume {}", mirror.getId(), promoted.getId());
                // If there are exports masks/export groups associated, then
                // remove the mirror from them and add the promoted volume.
                ExportUtils.updatePromotedMirrorExports(mirror, promoted, _dbClient);
                mirror.setInactive(true);
                mirrors.add(mirror);
            }
            _dbClient.persistObject(mirrors);
            _dbClient.persistObject(promotedVolumes);

            WorkflowStepCompleter.stepSucceded(opId);
        } catch (Exception e) {
            String msg = String.format("Failed to promote mirror %s", Joiner.on("\t").join(mirrorList));
            _log.error(msg, e);
            WorkflowStepCompleter.stepFailed(opId, DeviceControllerException.exceptions.stopVolumeMirrorFailed(mirrorList.get(0)));
        }
    }

    public static final String DELETE_MIRROR_STEP_GROUP = "BlockDeviceDeleteMirror";

    /**
     * Adds the additional steps necessary to delete local mirrors.
     *
     * @param workflow
     * @param waitFor
     * @param descriptors List<VolumeDescriptor> volumes to be processed
     * @return
     */
    public String addStepsForDeleteMirrors(Workflow workflow, String waitFor,
            List<VolumeDescriptor> descriptors)
            throws ControllerException {
        // Get only the BLOCK_MIRROR descriptors.
        descriptors = VolumeDescriptor.filterByType(descriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_MIRROR }, null);
        if (descriptors.isEmpty()) {
            return waitFor;
        }

        List<URI> uris = VolumeDescriptor.getVolumeURIs(descriptors);
        boolean isCG = isCGMirror(uris.get(0), _dbClient);
        if (!isCG) {
            Iterator<BlockMirror> mirrorIterator = _dbClient.queryIterativeObjects(BlockMirror.class, uris);
            while (mirrorIterator.hasNext()) {
                BlockMirror mirror = mirrorIterator.next();
                URI controller = mirror.getStorageController();
                String stepId = null;

                // Add steps for detaching the mirror
                stepId = addStepsForDetachMirror(workflow, waitFor, DELETE_MIRROR_STEP_GROUP, asList(mirror.getId()), isCG);
                // Create a step for deleting the mirror.
                stepId = workflow.createStep(DELETE_MIRROR_STEP_GROUP,
                        String.format("Delete mirror: %s", mirror.getId()),
                        stepId, controller, getDeviceType(controller),
                        this.getClass(),
                        deleteMirrorMethod(controller, asList(mirror.getId()), isCG),
                        null, null);
            }
        } else {
            BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, uris.get(0));
            URI controller = mirror.getStorageController();
            String stepId = null;

            // Add steps for detaching the mirror
            stepId = addStepsForDetachMirror(workflow, waitFor, DELETE_MIRROR_STEP_GROUP, uris, isCG);
            // Create a step for deleting the mirror.
            stepId = workflow.createStep(DELETE_MIRROR_STEP_GROUP,
                    String.format("Delete mirror: %s", mirror.getId()),
                    stepId, controller, getDeviceType(controller),
                    this.getClass(),
                    deleteMirrorMethod(controller, uris, isCG),
                    null, null);
        }

        return DELETE_MIRROR_STEP_GROUP;
    }

    public Workflow.Method deleteMirrorMethod(URI storage, List<URI> mirrorList, Boolean isCG) {
        return new Workflow.Method("deleteMirror", storage, mirrorList, isCG);
    }

    /**
     * {@inheritDoc} NOTE NOTE: The signature here MUST match the Workflow.Method deleteMirrorMethod just above (except opId).
     */
    @Override
    public void deleteMirror(URI storage, List<URI> mirrorList, Boolean isCG, String opId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockMirrorDeleteCompleter(mirrorList, opId);
            if (!isCG) {
                getDevice(storageObj.getSystemType()).doDeleteMirror(storageObj, mirrorList.get(0), completer);
            } else {
                getDevice(storageObj.getSystemType()).doDeleteGroupMirrors(storageObj, mirrorList, completer);
            }
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            if (completer != null) {
                completer.error(_dbClient, serviceError);
            }
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    @Override
    public void createConsistencyGroup(URI storage, URI consistencyGroup, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);

            // Lock the CG for the step duration.
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getConsistencyGroupStorageKey(_dbClient, consistencyGroup, storage));
            _workflowService.acquireWorkflowStepLocks(opId, lockKeys, LockTimeoutValue.get(LockType.ARRAY_CG));

            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            TaskCompleter completer = new BlockConsistencyGroupCreateCompleter(consistencyGroup, opId);

            // Check if already created, if not create, if so just complete.
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroup);
            if (!cg.created(storage)) {
                getDevice(storageObj.getSystemType()).doCreateConsistencyGroup(storageObj, consistencyGroup, completer);
            } else {
                _log.info(String.format("Consistency group %s (%s) already created", cg.getLabel(), cg.getId()));
                completer.ready(_dbClient);
            }
        } catch (Exception e) {
            throw DeviceControllerException.exceptions.createConsistencyGroupFailed(e);
        }
    }

    @Override
    public void deleteConsistencyGroup(URI storage, URI consistencyGroup, Boolean markInactive, String opId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockConsistencyGroupDeleteCompleter(consistencyGroup, opId);
            getDevice(storageObj.getSystemType()).doDeleteConsistencyGroup(storageObj, consistencyGroup, markInactive, completer);
        } catch (Exception e) {
            if (completer != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                completer.error(_dbClient, serviceError);
            }
            throw DeviceControllerException.exceptions.deleteConsistencyGroupFailed(e);
        }
    }

    /**
     * An orchestration controller method for detaching and deleting a mirror
     *
     * @param storage URI of storage controller.
     * @param mirrorList List of URIs of block mirrors
     * @param promotees List of URIs of promoted volumes
     * @param isCG CG mirror or not
     * @param opId Operation ID
     * @throws ControllerException
     */
    @Override
    public void deactivateMirror(URI storage, List<URI> mirrorList, List<URI> promotees, Boolean isCG, String opId) throws ControllerException {
        _log.info("deactivateMirror: START");
        TaskCompleter taskCompleter = null;
        String mirrorStr = Joiner.on("\t").join(mirrorList);

        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            Workflow workflow = _workflowService.getNewWorkflow(this, "deactivateMirror", true, opId);
            taskCompleter = new BlockMirrorDeactivateCompleter(mirrorList, promotees, opId);
            ControllerUtils.checkMirrorConsistencyGroup(mirrorList, _dbClient, taskCompleter);

            String detachStep = workflow.createStepId();
            Workflow.Method detach = detachMirrorMethod(storage, mirrorList, isCG);
            workflow.createStep("deactivate", "detaching mirror volume: " + mirrorStr, null, storage,
                    storageSystem.getSystemType(), getClass(), detach, null, detachStep);

            // for single volume mirror, the mirror will be deleted
            List<URI> mirrorsToDelete = mirrorList;
            // for group mirror, find mirrors to be deleted and mirrors to be promoted, and do the promotion
            if (isCG) {
                mirrorsToDelete = new ArrayList<URI>();
                List<Volume> promotedVolumes = _dbClient.queryObject(Volume.class, promotees);
                List<URI> orderedMirrorsToPromote = new ArrayList<URI>();
                List<URI> orderedPromotedVolumes = new ArrayList<URI>();

                for (URI mirror : mirrorList) {
                    URI promotedVolume = null;
                    for (Volume promotee : promotedVolumes) {
                        OpStatusMap statusMap = promotee.getOpStatus();
                        for (Map.Entry<String, Operation> entry : statusMap.entrySet()) {
                            Operation operation = entry.getValue();
                            if (operation.getAssociatedResourcesField().contains(mirror.toString())) {
                                promotedVolume = promotee.getId();
                            }
                        }
                    }

                    if (promotedVolume != null) {
                        orderedMirrorsToPromote.add(mirror);
                        orderedPromotedVolumes.add(promotedVolume);
                    } else {
                        mirrorsToDelete.add(mirror);
                    }
                }

                if (!orderedMirrorsToPromote.isEmpty()) {
                    // Create a step for promoting the mirrors.
                    String stepId = workflow.createStep(PROMOTE_MIRROR_STEP_GROUP,
                            String.format("Promote mirrors : %s", Joiner.on("\t").join(orderedMirrorsToPromote)),
                            detachStep, storage, storageSystem.getSystemType(),
                            this.getClass(),
                            promoteMirrorMethod(orderedMirrorsToPromote, orderedPromotedVolumes, isCG),
                            null, null);
                }
            }

            String deleteStep = workflow.createStepId();
            Workflow.Method delete = deleteMirrorMethod(storage, mirrorsToDelete, isCG);

            workflow.createStep("deactivate", "deleting mirror volume: " + Joiner.on("\t").join(mirrorsToDelete), detachStep, storage,
                    storageSystem.getSystemType(), getClass(), delete, null, deleteStep);

            String successMessage = String.format("Successfully deactivated mirror %s on StorageArray %s",
                    mirrorStr, storage);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            if (_log.isErrorEnabled()) {
                String msg = String.format("Deactivate mirror failed for mirror %s", mirrorStr);
                _log.error(msg);
            }
            if (taskCompleter != null) {
                String opName = ResourceOperationTypeEnum.DEACTIVATE_VOLUME_MIRROR.getName();
                ServiceError serviceError = DeviceControllerException.errors.jobFailedOp(opName);
                taskCompleter.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.deactivateMirrorFailed(e);
            }
        }
    }

    @Override
    public void establishVolumeAndNativeContinuousCopyGroupRelation(URI storage, URI sourceVolume, URI mirror, String opId)
            throws ControllerException {
        _log.info("START establishVolumeAndNativeContinuousCopyGroupRelation workflow");

        Workflow workflow = _workflowService.getNewWorkflow(this, ESTABLISH_VOLUME_MIRROR_GROUP_WF_NAME, false, opId);
        TaskCompleter taskCompleter = null;
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);

        try {
            workflow.createStep("establishStep", "create group relation between Volume group and Mirror group", null, storage,
                    storageObj.getSystemType(),
                    this.getClass(), establishVolumeAndNativeContinuousCopyGroupRelationMethod(storage, sourceVolume, mirror), null, null);

            taskCompleter = new BlockMirrorTaskCompleter(Volume.class, asList(sourceVolume), opId);
            workflow.executePlan(taskCompleter, "Successfully created group relation between Volume group and Mirror group");
        } catch (Exception e) {
            String msg = String.format("Failed to create group relation between Volume group and Mirror group."
                    + "Source volume: %s, Mirror: %s",
                    sourceVolume, mirror);
            _log.error(msg, e);
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                taskCompleter.error(_dbClient, serviceError);
            }
        }
    }

    private Workflow.Method establishVolumeAndNativeContinuousCopyGroupRelationMethod(URI storage, URI sourceVolume, URI mirror) {
        return new Workflow.Method("establishVolumeNativeContinuousCopyGroupRelation", storage, sourceVolume, mirror);
    }

    public void establishVolumeNativeContinuousCopyGroupRelation(
            URI storage, URI sourceVolume, URI mirror, String opId)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            TaskCompleter completer = new BlockMirrorTaskCompleter(BlockMirror.class, mirror, opId);
            getDevice(storageObj.getSystemType())
                    .doEstablishVolumeNativeContinuousCopyGroupRelation(
                            storageObj, sourceVolume, mirror, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    static final String FULL_COPY_WORKFLOW = "fullCopyVolumes";
    static final String FULL_COPY_CREATE_STEP_GROUP = "createFullCopiesStepGroup";
    static final String FULL_COPY_WFS_STEP_GROUP = "waitForSyncStepGroup";
    static final String FULL_COPY_DETACH_STEP_GROUP = "detachFullCopyStepGroup";
    static final String FULL_COPY_FRACTURE_STEP_GROUP = "fractureFullCopyStepGroup";
    static final String SNAPSHOT_DELETE_STEP_GROUP = "deleteSnapshotStepGroup";
    static final String MIRROR_FRACTURE_STEP_GROUP = "fractureMirrorStepGroup";
    static final String MIRROR_DETACH_STEP_GROUP = "detachMirrorStepGroup";

    @Override
    public void createFullCopy(URI storage, List<URI> fullCopyVolumes, Boolean createInactive,
            String taskId)
            throws ControllerException {
        _log.info("START fullCopyVolumes");
        TaskCompleter taskCompleter = new CloneCreateWorkflowCompleter(fullCopyVolumes, taskId);
        Volume clone = _dbClient.queryObject(Volume.class, fullCopyVolumes.get(0));
        URI sourceVolume = clone.getAssociatedSourceVolume();

        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            Workflow workflow = _workflowService.getNewWorkflow(this, FULL_COPY_WORKFLOW, true, taskId);
            boolean isCG = false;
            // check if the clone is in a CG
            if (checkCloneConsistencyGroup(fullCopyVolumes.get(0), _dbClient, taskCompleter)) {
                isCG = true;
                _log.info("Creating group full copy");
                Workflow.Method createMethod = createFullCopyVolumeMethod(storage, sourceVolume, fullCopyVolumes, createInactive, isCG);
                Workflow.Method rollbackMethod = rollbackFullCopyVolumeMethod(storage, fullCopyVolumes);
                workflow.createStep(FULL_COPY_CREATE_STEP_GROUP, "Creating full copy", null, storage,
                        storageSystem.getSystemType(), getClass(), createMethod,
                        rollbackMethod, null);

                if (!createInactive) {
                    // After all full copies have been created, wait for synchronization to complete
                    if (!storageSystem.deviceIsType(Type.vnxblock)) {
                        Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage, fullCopyVolumes, isCG);
                        String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                                "Waiting for synchronization", FULL_COPY_CREATE_STEP_GROUP, storage,
                                storageSystem.getSystemType(), getClass(), waitForSyncMethod, null, null);
                        setCloneReplicaStateStep(workflow, storageSystem, fullCopyVolumes, waitForSyncStep, ReplicationState.SYNCHRONIZED);
                    } else {
                        String previousStep = FULL_COPY_CREATE_STEP_GROUP;
                        for (URI cloneUri : fullCopyVolumes) {
                            Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage, Arrays.asList(cloneUri),
                                    false);
                            String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                                    "Waiting for synchronization", previousStep, storage,
                                    storageSystem.getSystemType(), getClass(), waitForSyncMethod, null, null);
                            previousStep = waitForSyncStep;
                        }

                        workflow.createStep(FULL_COPY_FRACTURE_STEP_GROUP, "fracture full copy", previousStep,
                                storage, storageSystem.getSystemType(), BlockDeviceController.class,
                                fractureCloneMethod(storage, fullCopyVolumes, isCG), null, null);

                    }
                }

            } else {
                for (URI uri : fullCopyVolumes) {
                    Workflow.Method createMethod = createFullCopyVolumeMethod(storage, sourceVolume, Arrays.asList(uri), createInactive,
                            isCG);
                    Workflow.Method rollbackMethod = rollbackFullCopyVolumeMethod(storage, asList(uri));
                    workflow.createStep(FULL_COPY_CREATE_STEP_GROUP, "Creating full copy", null, storage,
                            storageSystem.getSystemType(), getClass(), createMethod,
                            rollbackMethod, null);
                    if (!createInactive) {
                        // After all full copies have been created, wait for synchronization to complete
                        Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage, Arrays.asList(uri), isCG);
                        String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                                "Waiting for synchronization", FULL_COPY_CREATE_STEP_GROUP, storage,
                                storageSystem.getSystemType(), getClass(), waitForSyncMethod, null, null);
                        Volume cloneVol = _dbClient.queryObject(Volume.class, uri);
                        BlockObject sourceObj = BlockObject.fetch(_dbClient, cloneVol.getAssociatedSourceVolume());
                        // detach if source is snapshot, or storage system is not vmax/vnx/hds
                        if (storageSystem.deviceIsType(Type.openstack)) {
                            setCloneReplicaStateStep(workflow, storageSystem, asList(uri), waitForSyncStep, ReplicationState.SYNCHRONIZED);
                        }
                        else if (sourceObj instanceof BlockSnapshot
                                || !(storageSystem.deviceIsType(Type.vmax) || storageSystem.deviceIsType(Type.hds)
                                || storageSystem.deviceIsType(Type.vnxblock))) {

                            Workflow.Method detachMethod = detachFullCopyMethod(storage, asList(uri));
                            workflow.createStep(FULL_COPY_DETACH_STEP_GROUP, "Detaching full copy", waitForSyncStep,
                                    storage, storageSystem.getSystemType(), getClass(), detachMethod, null, null);
                        } else if (storageSystem.deviceIsType(Type.vnxblock)) {
                            workflow.createStep(FULL_COPY_FRACTURE_STEP_GROUP, "fracture full copy", waitForSyncStep,
                                    storage, storageSystem.getSystemType(), BlockDeviceController.class,
                                    fractureCloneMethod(storage, Arrays.asList(uri), isCG), null, null);
                        } else {
                            setCloneReplicaStateStep(workflow, storageSystem, asList(uri), waitForSyncStep, ReplicationState.SYNCHRONIZED);
                        }
                    }
                }

            }
            String successMsg = String.format("Full copy of %s to %s successful", sourceVolume, fullCopyVolumes);
            workflow.executePlan(taskCompleter, successMsg);
        } catch (InternalException e) {
            _log.error("Failed to create full copy of volume", e);
            doFailTask(Volume.class, sourceVolume, taskId, e);
            WorkflowStepCompleter.stepFailed(taskId, e);
        } catch (Exception e) {
            _log.error("Failed to create full copy of volume", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, sourceVolume, taskId, serviceError);
            WorkflowStepCompleter.stepFailed(taskId, serviceError);
        }
    }

    public Workflow.Method createFullCopyVolumeMethod(URI storage, URI sourceVolume, List<URI> fullCopyVolumes,
            Boolean createInactive, boolean isCG) {
        return new Workflow.Method("createFullCopyVolume", storage, sourceVolume, fullCopyVolumes, createInactive, isCG);
    }

    public void createFullCopyVolume(URI storage, URI sourceVolume, List<URI> fullCopyVolumes, Boolean createInactive, boolean isCG,
            String taskId) {
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            TaskCompleter taskCompleter = new CloneCreateCompleter(fullCopyVolumes, taskId);
            WorkflowStepCompleter.stepExecuting(taskId);
            if (isCG) {
                getDevice(storageSystem.getSystemType()).doCreateGroupClone(storageSystem, fullCopyVolumes,
                        createInactive, taskCompleter);
            } else {
                getDevice(storageSystem.getSystemType()).doCreateClone(storageSystem, sourceVolume, fullCopyVolumes.get(0),
                        createInactive, taskCompleter);
            }
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(taskId, serviceError);
            doFailTask(Volume.class, fullCopyVolumes, taskId, serviceError);
        }
    }

    public Workflow.Method rollbackFullCopyVolumeMethod(URI storage, List<URI> fullCopy) {
        return new Workflow.Method("rollbackFullCopyVolume", storage, fullCopy);
    }

    public void rollbackFullCopyVolume(URI storage, List<URI> fullCopy, String taskId) {
        WorkflowStepCompleter.stepExecuting(taskId);

        List<Volume> volumes = _dbClient.queryObject(Volume.class, fullCopy);
        try {
            if (!isNullOrEmpty(volumes.get(0).getNativeId()) && !volumes.get(0).getInactive()) {
                _log.info("Attempting to detach for rollback");
                detachFullCopy(storage, fullCopy, taskId);
                _log.info("Attempting to delete for rollback");
                deleteVolumes(storage, fullCopy, taskId);
            } else {
                for (Volume volume : volumes) {
                    volume.setInactive(true);
                    _dbClient.persistObject(volume);
                }
            }
            WorkflowStepCompleter.stepSucceded(taskId);
        } catch (InternalException ie) {
            _log.error(String.format("rollbackFullCopyVolume Failed - Array:%s, Volume:%s", storage, fullCopy));
            doFailTask(Volume.class, fullCopy, taskId, ie);
            WorkflowStepCompleter.stepFailed(taskId, ie);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(taskId, serviceError);
            doFailTask(Volume.class, fullCopy, taskId, serviceError);
        }
    }

    private static final String ACTIVATE_CLONE_WF_NAME = "ACTIVATE_CLONE_WORKFLOW";
    private static final String ACTIVATE_CLONE_GROUP = "BlockDeviceActivateClone";

    @Override
    public void activateFullCopy(URI storage, List<URI> fullCopy, String opId) {
        TaskCompleter completer = new CloneCreateWorkflowCompleter(fullCopy, opId);
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);

            if (storageSystem.deviceIsType(Type.vnxblock)) {
                // need to create a workflow to wait sync finish, then do fracture/activate
                Workflow workflow = _workflowService.getNewWorkflow(this, RESYNC_CLONE_WF_NAME, false, opId);
                _log.info("Created new activate workflow with operation id {}", opId);
                boolean isCG = checkCloneConsistencyGroup(fullCopy.get(0), _dbClient, completer);
                String previousStep = null;
                if (isCG) {
                    for (URI cloneUri : fullCopy) {
                        Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage, Arrays.asList(cloneUri), false);
                        String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                                "Waiting for synchronization", previousStep, storage,
                                storageSystem.getSystemType(), getClass(), waitForSyncMethod, null, null);
                        previousStep = waitForSyncStep;
                    }

                } else {
                    Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage, fullCopy, isCG);
                    String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                            "Waiting for synchronization", previousStep, storage,
                            storageSystem.getSystemType(), getClass(), waitForSyncMethod, null, null);
                    previousStep = waitForSyncStep;
                }
                workflow.createStep(ACTIVATE_CLONE_GROUP, "Activating clone", previousStep,
                        storage, getDeviceType(storage), BlockDeviceController.class,
                        activateCloneMethod(storage, fullCopy), rollbackMethodNullMethod(), null);
                _log.info("Executing workflow");
                String msg = String.format("Actitvate %s completed successfully", fullCopy.get(0));

                completer = new CloneCreateWorkflowCompleter(fullCopy, opId);

                workflow.executePlan(completer, msg);

            } else {
                activateFullCopyStep(storage, fullCopy, opId);
            }
        } catch (Exception e) {
            String msg = String.format("Could not activate the clone %s", fullCopy.get(0));
            _log.error(msg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
        }

    }

    public static Workflow.Method activateCloneMethod(URI storage, List<URI> clone) {
        return new Workflow.Method("activateFullCopyStep", storage, clone);
    }

    public void activateFullCopyStep(URI storage, List<URI> fullCopy, String opId) {
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            TaskCompleter taskCompleter = new CloneActivateCompleter(fullCopy, opId);
            if (checkCloneConsistencyGroup(fullCopy.get(0), _dbClient, taskCompleter)) {
                getDevice(storageSystem.getSystemType()).doActivateGroupFullCopy(storageSystem, fullCopy, taskCompleter);
            } else {
                getDevice(storageSystem.getSystemType()).doActivateFullCopy(storageSystem, fullCopy.get(0), taskCompleter);
            }
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            doFailTask(Volume.class, fullCopy, opId, serviceError);
        }
    }

    public Workflow.Method detachFullCopyMethod(URI storage, List<URI> fullCopyVolume) {
        return new Workflow.Method("detachFullCopy", storage, fullCopyVolume);
    }

    @Override
    public void detachFullCopy(URI storage, List<URI> fullCopyVolumes, String taskId)
            throws ControllerException {
        _log.info("START detachFullCopy: {}", fullCopyVolumes);

        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            TaskCompleter taskCompleter = new VolumeDetachCloneCompleter(fullCopyVolumes, taskId);
            if (checkCloneConsistencyGroup(fullCopyVolumes.get(0), _dbClient, taskCompleter)) {
                _log.info("detach group full copy");
                getDevice(storageSystem.getSystemType()).doDetachGroupClone(storageSystem, fullCopyVolumes, taskCompleter);

            } else {
                getDevice(storageSystem.getSystemType()).doDetachClone(storageSystem, fullCopyVolumes.get(0),
                        taskCompleter);
            }
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(taskId, serviceError);
            doFailTask(Volume.class, fullCopyVolumes, taskId, serviceError);
        }
    }

    @Override
    public void establishVolumeAndFullCopyGroupRelation(URI storage, URI sourceVolume, URI fullCopy, String opId)
            throws ControllerException {
        _log.info("START establishVolumeAndFullCopyGroupRelation workflow");

        Workflow workflow = _workflowService.getNewWorkflow(this, ESTABLISH_VOLUME_FULL_COPY_GROUP_WF_NAME, false, opId);
        TaskCompleter taskCompleter = null;
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);

        try {
            workflow.createStep("establishStep", "create group relation between Volume group and Full copy group", null, storage,
                    storageObj.getSystemType(),
                    this.getClass(), establishVolumeAndFullCopyGroupRelationMethod(storage, sourceVolume, fullCopy), null, null);

            taskCompleter = new CloneTaskCompleter(fullCopy, opId);
            workflow.executePlan(taskCompleter, "Successfully created group relation between Volume group and Full copy group");
        } catch (Exception e) {
            String msg = String.format("Failed to create group relation between Volume group and Full copy group."
                    + "Source volume: %s, Full copy: %s",
                    sourceVolume, fullCopy);
            _log.error(msg, e);
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                taskCompleter.error(_dbClient, serviceError);
            }
        }
    }

    private Workflow.Method establishVolumeAndFullCopyGroupRelationMethod(URI storage, URI sourceVolume, URI fullCopy) {
        return new Workflow.Method("establishVolumeFullCopyGroupRelation", storage, sourceVolume, fullCopy);
    }

    public void establishVolumeFullCopyGroupRelation(
            URI storage, URI sourceVolume, URI fullCopy, String opId)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            TaskCompleter completer = new CloneTaskCompleter(fullCopy, opId);
            getDevice(storageObj.getSystemType())
                    .doEstablishVolumeFullCopyGroupRelation(
                            storageObj, sourceVolume, fullCopy, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    @Override
    public Integer checkSyncProgress(URI storage, URI source, URI target, String task) {
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            return getDevice(storageSystem.getSystemType()).checkSyncProgress(storage, source, target);
        } catch (Exception e) {
            String msg = String.format("Failed to check synchronization progress for %s", target);
            _log.error(msg, e);
        }
        return null;
    }

    /**
     * Creates a connection to monitor events generated by the storage
     * identified by the passed URI.
     *
     * @param storage A database client URI that identifies the storage to be
     *            monitored.
     *
     * @throws ControllerException When errors occur connecting the storage for
     *             event monitoring.
     */
    @Override
    public void connectStorage(URI storage) throws ControllerException {
        // Retrieve the storage device info from the database.
        StorageSystem storageObj = null;
        try {
            storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        } catch (Exception e) {
            throw DeviceControllerException.exceptions.connectStorageFailedDb(e);
        }
        // Verify non-null storage device returned from the database client.
        if (storageObj == null) {
            throw DeviceControllerException.exceptions.connectStorageFailedNull();
        }
        // Get the block device reference for the type of block device managed
        // by the controller.
        BlockStorageDevice storageDevice = getDevice(storageObj.getSystemType());
        if (storageDevice == null) {
            throw DeviceControllerException.exceptions.connectStorageFailedNoDevice(
                    storageObj.getSystemType());
        }
        storageDevice.doConnect(storageObj);
        _log.info("Adding to storage device to work pool: {}", storageObj.getId());

    }

    /**
     * Removes a connection that was previously established for monitoring
     * events from the storage identified by the passed URI.
     *
     * @param storage A database client URI that identifies the storage to be
     *            disconnected.
     *
     * @throws ControllerException When errors occur disconnecting the storage
     *             for event monitoring.
     */
    @Override
    public void disconnectStorage(URI storage) throws ControllerException {
        // Retrieve the storage device info from the database.
        StorageSystem storageObj = null;
        try {
            storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        } catch (Exception e) {
            throw DeviceControllerException.exceptions.disconnectStorageFailedDb(e);
        }

        // Verify non-null storage device returned from the database client.
        if (storageObj == null) {
            throw DeviceControllerException.exceptions.disconnectStorageFailedNull();
        }

        // Get the block device reference for the type of block device managed
        // by the controller.
        BlockStorageDevice storageDevice = getDevice(storageObj.getSystemType());
        if (storageDevice == null) {
            throw DeviceControllerException.exceptions.disconnectStorageFailedNull();
        }

        storageDevice.doDisconnect(storageObj);
        _log.info("Removing storage device from work pool: {}", storageObj.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void discoverStorageSystem(AsyncTask[] tasks)
            throws ControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void scanStorageProviders(AsyncTask[] tasks)
            throws ControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    private String addStorageToSMIS(StorageSystem storageSystem, StorageProvider provider)
            throws DataBindingException, ControllerException {
        String system = "";
        if (provider != null) {
            // Populate provider info. This information normally corresponds to the active provider.
            // Do not persist system information in the
            storageSystem.setSmisPassword(provider.getPassword());
            storageSystem.setSmisUserName(provider.getUserName());
            storageSystem.setSmisPortNumber(provider.getPortNumber());
            storageSystem.setSmisProviderIP(provider.getIPAddress());
            storageSystem.setSmisUseSSL(provider.getUseSSL());
            system = getDevice(storageSystem.getSystemType()).doAddStorageSystem(storageSystem);
            _log.info("Storage is added to the SMI-S provider : " + provider.getProviderID());
        }
        return system;
    }

    private boolean scanProvider(StorageProvider provider, StorageSystem storageSystem,
            boolean activeProvider, String opId) throws DatabaseException,
            BaseCollectionException,
            ControllerException {

        Map<String, StorageSystemViewObject> storageCache = new HashMap<String, StorageSystemViewObject>();
        _dbClient.createTaskOpStatus(StorageProvider.class, provider.getId(), opId,
                ResourceOperationTypeEnum.SCAN_SMISPROVIDER);
        ScanTaskCompleter scanCompleter = new ScanTaskCompleter(StorageProvider.class, provider.getId(), opId);
        try {
            scanCompleter.statusPending(_dbClient, "Scan for storage system is Initiated");
            provider.setLastScanStatusMessage("");
            _dbClient.persistObject(provider);
            ControllerServiceImpl.performScan(provider.getId(), scanCompleter, storageCache);
            scanCompleter.statusReady(_dbClient, "Scan for storage system has completed");
        } catch (Exception ex) {
            _log.error("Scan failed for {}--->", provider, ex);
            scanCompleter.statusError(_dbClient, DeviceControllerErrors.dataCollectionErrors.scanFailed(ex));
            throw DeviceControllerException.exceptions.scanProviderFailed(storageSystem.getNativeGuid(),
                    provider.getId().toString());
        }
        if (!storageCache.containsKey(storageSystem.getNativeGuid())) {
            return false;
        }
        else {
            StorageSystemViewObject vo = storageCache.get(storageSystem.getNativeGuid());

            String model = vo.getProperty(StorageSystemViewObject.MODEL);
            if (StringUtils.isNotBlank(model)) {
                storageSystem.setModel(model);
            }
            String serialNo = vo.getProperty(StorageSystemViewObject.SERIAL_NUMBER);
            if (StringUtils.isNotBlank(serialNo)) {
                storageSystem.setSerialNumber(serialNo);
            }
            String version = vo.getProperty(StorageSystemViewObject.VERSION);
            if (StringUtils.isNotBlank(version)) {
                storageSystem.setMajorVersion(version);
            }
            String name = vo.getProperty(StorageSystemViewObject.STORAGE_NAME);
            if (StringUtils.isNotBlank(name)) {
                storageSystem.setLabel(name);
            }
            provider.addStorageSystem(_dbClient, storageSystem, activeProvider);
            return true;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws WorkflowException
     */
    @Override
    public void addStorageSystem(URI storage, URI[] providers, boolean activeProvider, String opId) throws ControllerException {

        if (providers == null) {
            return;
        }
        String allProviders = Joiner.on("\t").join(providers);

        DiscoverTaskCompleter completer = new DiscoverTaskCompleter(StorageSystem.class, storage, opId, ControllerServiceImpl.DISCOVERY);
        StringBuilder failedProviders = new StringBuilder();
        boolean exceptionIntercepted = false;
        boolean needDiscovery = false;

        boolean acquiredLock = false;
        try {
            acquiredLock = ControllerServiceImpl.Lock.SCAN_COLLECTION_LOCK.acquire(SCAN_LOCK_TIMEOUT);
        } catch (Exception ex) {
            _log.error("Exception while acquiring a lock ", ex);
            acquiredLock = false;
        }

        if (acquiredLock) {
            try {
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);

                for (int ii = 0; ii < providers.length; ii++) {
                    try {
                        StorageProvider providerSMIS = _dbClient.queryObject(StorageProvider.class, providers[ii]);

                        if (providerSMIS == null) {
                            throw DeviceControllerException.exceptions.entityNullOrEmpty(null);
                        }
                        if (providerSMIS.getInactive()) {
                            throw DeviceControllerException.exceptions.entityInactive(providerSMIS.getId());
                        }
                        boolean found = scanProvider(providerSMIS, storageSystem, activeProvider && ii == 0, opId);
                        if (!found) {
                            if (storageSystem.getSystemType().equals(Type.vnxblock.toString()) &&
                                    StringUtils.isNotBlank(storageSystem.getIpAddress())) {
                                String system = addStorageToSMIS(storageSystem, providerSMIS);
                                if (!system.equalsIgnoreCase(storageSystem.getNativeGuid())) {
                                    throw DeviceControllerException.exceptions.addStorageSystemFailed(storageSystem.getNativeGuid(),
                                            providerSMIS.getId().toString());
                                }
                                providerSMIS.addStorageSystem(_dbClient, storageSystem, activeProvider && ii == 0);
                                if (activeProvider && ii == 0) {
                                    providerSMIS.removeDecommissionedSystem(_dbClient, storageSystem.getNativeGuid());
                                }
                                storageSystem.setReachableStatus(true);
                                _dbClient.persistObject(storageSystem);
                            }
                            else {
                                throw DeviceControllerException.exceptions.scanFailedToFindSystem(providerSMIS.getId().toString(),
                                        storageSystem.getNativeGuid());
                            }
                        }
                        else {
                            storageSystem.setReachableStatus(true);
                            _dbClient.persistObject(storageSystem);
                        }
                        if (providers.length > 1) {
                            completer.statusPending(_dbClient,
                                    "Adding storage to SMIS Providers : completed " + (ii + 1) + " providers out of " + providers.length);
                        }
                    } catch (Exception ex) {// any type of exceptions for a particular provider
                        _log.error("Failed to add storage from the following provider: " + providers[ii], ex);
                        failedProviders.append(providers[ii]).append(' ');
                        exceptionIntercepted = true;
                    }
                }
                if (activeProvider) {
                    updateActiveProvider(storageSystem);
                    _dbClient.persistObject(storageSystem);
                }

                DecommissionedResource.removeDecommissionedFlag(_dbClient, storageSystem.getNativeGuid(), StorageSystem.class);

                if (exceptionIntercepted) {
                    String opName = ResourceOperationTypeEnum.ADD_STORAGE_SYSTEM.getName();
                    ServiceError serviceError = DeviceControllerException.errors.jobFailedOp(opName);
                    completer.error(_dbClient, serviceError);
                }
                else {
                    recordBourneStorageEvent(RecordableEventManager.EventType.StorageDeviceAdded,
                            storageSystem, "Added SMI-S Storage");
                    _log.info("Storage is added to the SMI-S providers: ");
                    if (activeProvider) {
                        needDiscovery = true;

                        storageSystem.setLastDiscoveryRunTime(new Long(0));
                        completer.statusPending(_dbClient,
                                "Storage is added to the specified SMI-S providers : " + allProviders);
                        // We need to set timer back to 0 to indicate that it is a new system ready for discovery.
                        _dbClient.persistObject(storageSystem);
                    }
                    else {
                        completer.ready(_dbClient);
                    }
                }
            } catch (Exception outEx) {
                exceptionIntercepted = true;
                _log.error("Failed to add SMIS providers", outEx);
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(outEx);
                completer.error(_dbClient, serviceError);
            } finally {
                try {
                    ControllerServiceImpl.Lock.SCAN_COLLECTION_LOCK.release();
                } catch (Exception ex) {
                    _log.error("Failed to release SCAN lock; scanning might become disabled", ex);
                }
                if (needDiscovery && !exceptionIntercepted) {
                    try {
                        ControllerServiceImpl.scheduleDiscoverJobs(
                                new AsyncTask[] { new AsyncTask(StorageSystem.class, storage, opId) },
                                Lock.DISCOVER_COLLECTION_LOCK, ControllerServiceImpl.DISCOVERY);
                    } catch (Exception ex) {
                        _log.error("Failed to start discovery : " + storage, ex);
                    }
                }

            }
        }
        else {
            String opName = ResourceOperationTypeEnum.ADD_STORAGE_SYSTEM.getName();
            ServiceError serviceError = DeviceControllerException.errors.jobFailedOp(opName);
            completer.error(_dbClient, serviceError);
            _log.debug("Not able to Acquire Scanning lock-->{}", Thread.currentThread().getId());
        }
    }

    private void recordBourneStorageEvent(RecordableEventManager.EventType evtType, StorageSystem storage, String desc) {

        RecordableBourneEvent event = ControllerUtils
                .convertToRecordableBourneEvent(storage, evtType.toString(),
                        desc, "", _dbClient,
                        ControllerUtils.BLOCK_EVENT_SERVICE,
                        RecordType.Event.name(),
                        ControllerUtils.BLOCK_EVENT_SOURCE);

        try {
            _eventManager.recordEvents(event);
            _log.info("Bourne {} event recorded", evtType.name());
        } catch (Exception ex) {
            _log.error(
                    "Failed to record event. Event description: {}. Error: ",
                    evtType.name(), ex);
        }
    }

    private void updateActiveProvider(StorageSystem storage) throws DatabaseException {
        if (storage.getActiveProviderURI() == null) {
            return;
        }
        StorageProvider mainProvider = _dbClient.queryObject(StorageProvider.class, storage.getActiveProviderURI());
        if (mainProvider != null) {
            storage.setSmisPassword(mainProvider.getPassword());
            storage.setSmisUserName(mainProvider.getUserName());
            storage.setSmisPortNumber(mainProvider.getPortNumber());
            storage.setSmisProviderIP(mainProvider.getIPAddress());
            storage.setSmisUseSSL(mainProvider.getUseSSL());
            _dbClient.persistObject(storage);
        }
    }

    @Override
    public void startMonitoring(AsyncTask task, Type deviceType)
            throws ControllerException {
        // TODO Auto-generated method stub
    }

    public <T extends BlockObject> Workflow.Method waitForSynchronizedMethod(Class clazz, URI storage, List<URI> target, boolean isCG) {
        return new Workflow.Method("waitForSynchronized", clazz, storage, target, isCG);
    }

    public void waitForSynchronized(Class<? extends BlockObject> clazz, URI storage, List<URI> target, boolean isCG, String opId)
            throws ControllerException {
        _log.info("START waitForSynchronized for {}", target);
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockWaitForSynchronizedCompleter(clazz, target, opId);
            if (!isCG) {
                getDevice(storageObj.getSystemType()).doWaitForSynchronized(clazz, storageObj, target.get(0), completer);
            } else {
                getDevice(storageObj.getSystemType()).doWaitForGroupSynchronized(storageObj, target, completer);
            }
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            if (completer != null) {
                completer.error(_dbClient, serviceError);
            }
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    /**
     * Get the deviceType for a StorageSystem.
     *
     * @param deviceURI -- StorageSystem URI
     * @return deviceType String
     */
    String getDeviceType(URI deviceURI) throws ControllerException {
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, deviceURI);
        if (storageSystem == null) {
            throw DeviceControllerException.exceptions.getDeviceTypeFailed(deviceURI.toString());
        }
        return storageSystem.getSystemType();
    }

    /**
     * Checks if given BlockMirrors are applicable for pausing.
     * A mirror is valid for pausing if it is
     * 1) Not null
     * 2) Has valid sync state integer value
     * 3) Sync state is not already fractured (paused) and not resynchronizing
     *
     * @param mirrorList The BlockMirrors to test
     * @return true, if at least one mirror is applicable for pause operation
     */
    private boolean mirrorIsPausable(List<BlockMirror> mirrorList) {
        for (BlockMirror mirror : mirrorList) {
            try {
                boolean hasPausableMirror = mirror != null &&
                        mirror.getInactive() == false &&
                        !SynchronizationState.FRACTURED.toString().equals(mirror.getSyncState()) &&
                        !SynchronizationState.RESYNCHRONIZING.toString().equals(mirror.getSyncState());
                if (hasPausableMirror) { // will continue to look up if it is false
                    return hasPausableMirror;
                }
            } catch (NumberFormatException nfe) {
                _log.warn("Failed to parse sync state ({}) for mirror {}", mirror.getSyncState(), mirror.getId());
            }
        }
        return false;
    }

    /**
     * Pending mirrors are mirrors that are pending creation on the physical array
     *
     * @param mirrorURIs
     * @return list of active mirrors, waiting to be created
     */
    private List<BlockMirror> getPendingMirrors(StringSet mirrorURIs) {
        List<BlockMirror> result = new ArrayList<BlockMirror>();

        for (String uri : mirrorURIs) {
            BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, URI.create(uri));
            if (isPending(mirror)) {
                result.add(mirror);
            }
        }
        return result;
    }

    /**
     * Check if a mirror exists in ViPR as an active model and is pending creation on the
     * storage array.
     *
     * @param mirror
     * @return true if the mirror is pending creation
     */
    private boolean isPending(BlockMirror mirror) {
        return !isInactive(mirror) && isNullOrEmpty(mirror.getSynchronizedInstance());
    }

    private boolean isInactive(BlockMirror mirror) {
        return mirror == null || (mirror.getInactive() != null && mirror.getInactive());
    }

    @Override
    public void noActionRollBackStep(URI deviceURI, String opID) {
        _log.info("Running empty Roll back step for storage system {}", deviceURI);
        WorkflowStepCompleter.stepSucceded(opID);

    }

    @Override
    public void updateConsistencyGroup(URI storage, URI consistencyGroup,
            List<URI> addVolumesList,
            List<URI> removeVolumesList, String task) {
        TaskCompleter completer = new BlockConsistencyGroupUpdateCompleter(consistencyGroup, task);
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroup);
            boolean srdfCG = false;

            // check if SRDF
            if (addVolumesList != null && !addVolumesList.isEmpty()) {
                URI volumeURI = addVolumesList.get(0);
                if (URIUtil.isType(volumeURI, Volume.class)) {
                    Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                    if (volume.isSRDFSource()) {
                        srdfCG = true;
                        cg.getRequestedTypes().add(Types.SRDF.name());
                        _dbClient.persistObject(cg);
                    }
                }
            }
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    UPDATE_CONSISTENCY_GROUP_WF_NAME, false, task);
            String waitFor = null;
            // check if cg is created, if not create it
            if (!cg.created()) {
                _log.info("Consistency group not created. Creating it");
                waitFor = workflow.createStep(UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                        String.format("Creating consistency group %s", consistencyGroup),
                        waitFor, storage, storageSystem.getSystemType(),
                        this.getClass(),
                        createConsistencyGroupMethod(storage, consistencyGroup),
                        rollbackMethodNullMethod(), null);
            }

            if (addVolumesList != null && !addVolumesList.isEmpty()) {
                waitFor = workflow.createStep(UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                        String.format("Updating consistency group %s", consistencyGroup),
                        waitFor, storage, storageSystem.getSystemType(),
                        this.getClass(),
                        addToConsistencyGroupMethod(storage, consistencyGroup, addVolumesList),
                        rollbackMethodNullMethod(), null);
            }

            if (removeVolumesList != null && !removeVolumesList.isEmpty()) {
                waitFor = workflow.createStep(UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                        String.format("Updating consistency group %s", consistencyGroup),
                        waitFor, storage, storageSystem.getSystemType(),
                        this.getClass(),
                        removeFromConsistencyGroupMethod(storage, consistencyGroup, removeVolumesList),
                        rollbackMethodNullMethod(), null);
            }

            // For SRDF, we need to create target consistency group and
            // add target volumes to that target consistency group.
            if (srdfCG) {
                createTargetConsistencyGroup(cg, addVolumesList, workflow, waitFor);
            }
            // Finish up and execute the plan.
            _log.info("Executing workflow plan {}", UPDATE_CONSISTENCY_GROUP_STEP_GROUP);
            String successMessage = String.format(
                    "Update consistency group successful for %s", consistencyGroup);
            workflow.executePlan(completer, successMessage);
        } catch (Exception e) {
            completer.error(_dbClient, DeviceControllerException.exceptions.failedToUpdateConsistencyGroup(e.getMessage()));
        }
    }

    private static Workflow.Method createConsistencyGroupMethod(URI storage, URI consistencyGroup) {
        return new Workflow.Method("createConsistencyGroupStep", storage, consistencyGroup);
    }

    public boolean createConsistencyGroupStep(URI storage, URI consistencyGroup, String opId)
            throws ControllerException {
        TaskCompleter taskCompleter = null;
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            taskCompleter = new BlockConsistencyGroupCreateCompleter(consistencyGroup, opId);
            getDevice(storageSystem.getSystemType()).doCreateConsistencyGroup(
                    storageSystem, consistencyGroup, taskCompleter);
        } catch (Exception e) {
            _log.error("create consistency group job failed:", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            return false;
        }
        return true;
    }

    private static Workflow.Method addToConsistencyGroupMethod(URI storage, URI consistencyGroup, List<URI> addVolumesList) {
        return new Workflow.Method("addToConsistencyGroup", storage, consistencyGroup, addVolumesList);
    }

    public boolean addToConsistencyGroup(URI storage, URI consistencyGroup, List<URI> addVolumesList, String opId)
            throws ControllerException {
        TaskCompleter taskCompleter = null;
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            taskCompleter = new BlockConsistencyGroupUpdateCompleter(consistencyGroup, opId);
            getDevice(storageSystem.getSystemType()).doAddToConsistencyGroup(
                    storageSystem, consistencyGroup, addVolumesList, taskCompleter);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            return false;
        }
        return true;
    }

    private static Workflow.Method removeFromConsistencyGroupMethod(URI storage, URI consistencyGroup, List<URI> removeVolumesList) {
        return new Workflow.Method("removeFromConsistencyGroup", storage, consistencyGroup, removeVolumesList);
    }

    public boolean removeFromConsistencyGroup(URI storage, URI consistencyGroup, List<URI> removeVolumesList, String opId)
            throws ControllerException {
        TaskCompleter taskCompleter = null;
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            taskCompleter = new BlockConsistencyGroupUpdateCompleter(consistencyGroup, opId);
            getDevice(storageSystem.getSystemType()).doRemoveFromConsistencyGroup(
                    storageSystem, consistencyGroup, removeVolumesList, taskCompleter);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            return false;
        }
        return true;
    }

    /**
     * This method creates SRDF target consistency group for the SRDF source consistency group
     * 1. Creates target consistency group in ViPR DB & Array
     * 2. Get the target volumes for the SRDF source volumes and adds them to the
     * target consistency group
     * Note: Target CG will be created using source system provider
     * 
     * @param sourceCG the source cg
     * @param addVolumesList the add volumes list
     * @param workflow the workflow
     * @param waitFor the wait for
     */
    private void createTargetConsistencyGroup(BlockConsistencyGroup sourceCG,
            List<URI> addVolumesList, Workflow workflow, String waitFor) {
        Volume sourceVolume = _dbClient.queryObject(Volume.class, addVolumesList.get(0));
        VirtualPool vPool = _dbClient.queryObject(VirtualPool.class, sourceVolume.getVirtualPool());
        VirtualArray protectionVirtualArray = null;
        if (vPool.getProtectionRemoteCopySettings() != null) {
            for (String protectionVarray : vPool.getProtectionRemoteCopySettings().keySet()) {
                protectionVirtualArray = _dbClient.queryObject(VirtualArray.class, URI.create(protectionVarray));
            }
        }
        String vArrayName = null;
        if (protectionVirtualArray != null) {
            vArrayName = protectionVirtualArray.getLabel();
        }
        String cgName = sourceCG.getLabel() + "-Target-" + vArrayName;
        List<BlockConsistencyGroup> groups = CustomQueryUtility
                .queryActiveResourcesByConstraint(_dbClient,
                        BlockConsistencyGroup.class, PrefixConstraint.Factory
                                .getFullMatchConstraint(
                                        BlockConsistencyGroup.class, "label",
                                        cgName));
        BlockConsistencyGroup targetCG = null;
        boolean createGroup = false;
        if (groups.isEmpty()) {
            // create target CG
            targetCG = new BlockConsistencyGroup();
            targetCG.setId(URIUtil.createId(BlockConsistencyGroup.class));
            targetCG.setLabel(cgName);
            // TODO verify project and tenant NamedURIs
            targetCG.setProject(sourceCG.getProject());
            targetCG.setTenant(sourceCG.getTenant());
            targetCG.setAlternateLabel(sourceCG.getLabel());
            targetCG.getRequestedTypes().add(Types.SRDF.name());
            _dbClient.createObject(targetCG);
            createGroup = true;
        } else {
            targetCG = groups.get(0);
        }

        // Get SRDF Target volume list for the source volume list
        List<URI> addTargetVolumesList = getSRDFTargetVolumes(addVolumesList);
        if (!addTargetVolumesList.isEmpty()) {
            Volume targetVolume = _dbClient.queryObject(Volume.class, addTargetVolumesList.get(0));
            StorageSystem targetSystem = _dbClient.queryObject(StorageSystem.class, targetVolume.getStorageController());
            if (createGroup || !targetCG.created()) {
                _log.info("Creating target Consistency group on Array.");
                waitFor = workflow.createStep(UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                        String.format("Creating consistency group %s", targetCG.getId()),
                        waitFor, targetSystem.getId(), targetSystem.getSystemType(),
                        this.getClass(),
                        createConsistencyGroupMethod(targetSystem.getId(), targetCG.getId()),
                        rollbackMethodNullMethod(), null);
            }

            _log.info("Adding target volumes to target Consistency group.");
            workflow.createStep(UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                    String.format("Updating consistency group %s", targetCG.getId()),
                    waitFor, targetSystem.getId(), targetSystem.getSystemType(),
                    this.getClass(),
                    addToConsistencyGroupMethod(targetSystem.getId(), targetCG.getId(), addTargetVolumesList),
                    rollbackMethodNullMethod(), null);
        }
    }

    /**
     *
     * @param sourceVolumeList
     * @return URI list of target volumes for the given source volumes
     */
    private List<URI> getSRDFTargetVolumes(List<URI> sourceVolumeList) {
        List<URI> addTargetVolumesList = new ArrayList<URI>();

        Iterator<URI> it = sourceVolumeList.iterator();
        while (it.hasNext()) {
            URI sourceVolumeURI = it.next();
            Volume sourceVolume = _dbClient.queryObject(Volume.class, sourceVolumeURI);
            if (sourceVolume.isSRDFSource()) {
                StringSet targets = sourceVolume.getSrdfTargets();
                for (String target : targets) {
                    addTargetVolumesList.add(URI.create(target));
                }
            }
        }
        return addTargetVolumesList;
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress,
            Integer portNumber, String interfaceType) {
        List<String> systemType = InterfaceType
                .getSystemTypesForInterfaceType(InterfaceType
                        .valueOf(interfaceType));
        return getDevice(systemType.get(0)).validateStorageProviderConnection(
                ipAddress, portNumber);
    }

    @Override
    public String addStepsForPostDeleteVolumes(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId,
            VolumeWorkflowCompleter completer) {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForChangeVirtualPool(Workflow workflow,
            String waitFor, List<VolumeDescriptor> volumes, String taskId) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForChangeVirtualArray(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    private static final String RESTORE_FROM_CLONE_WF_NAME = "RESTORE_FROM_CLONE_WORKFLOW";
    private static final String RESTORE_FROM_CLONE_GROUP = "BlockDeviceRestoreFromClone";
    private static final String FRACTURE_CLONE_GROUP = "PostBlockDeviceFractureClone";

    @Override
    public void restoreFromFullCopy(URI storage, List<URI> clones,
            Boolean updateOpStatus, String opId) throws InternalException {
        CloneCreateWorkflowCompleter completer = new CloneCreateWorkflowCompleter(clones, opId);
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, RESTORE_FROM_CLONE_WF_NAME, false, opId);
            _log.info("Created new restore workflow with operation id {}", opId);
            String waitFor = null;

            Volume clone = _dbClient.queryObject(Volume.class, clones.get(0));
            URI source = clone.getAssociatedSourceVolume();
            BlockObject sourceObj = BlockObject.fetch(_dbClient, source);
            /**
             * We need to detach SRDF link before performing clone restore to SRDF R2 volume.
             * OPT#477320
             */
            if (sourceObj instanceof Volume && isNonSplitSRDFTargetVolume((Volume) sourceObj)) {

                Volume sourceVolume = (Volume) sourceObj;
                URI srdfSourceVolumeURI = sourceVolume.getSrdfParent().getURI();
                Volume srdfSourceVolume = _dbClient.queryObject(Volume.class, srdfSourceVolumeURI);
                URI srdfSourceStorageSystemURI = srdfSourceVolume.getStorageController();
                // split all members the group
                Workflow.Method splitMethod = srdfDeviceController.splitSRDFLinkMethod(srdfSourceStorageSystemURI,
                        srdfSourceVolumeURI, source, false);

                Workflow.Method splitRollbackMethod = srdfDeviceController.resumeSyncPairMethod(srdfSourceStorageSystemURI,
                        srdfSourceVolumeURI, source);

                waitFor = workflow.createStep(SRDFDeviceController.SPLIT_SRDF_MIRRORS_STEP_GROUP,
                        SRDFDeviceController.SPLIT_SRDF_MIRRORS_STEP_DESC, waitFor, srdfSourceStorageSystemURI,
                        getDeviceType(srdfSourceStorageSystemURI), SRDFDeviceController.class, splitMethod,
                        splitRollbackMethod, null);
            }

            StorageSystem system = _dbClient.queryObject(StorageSystem.class, storage);

            String description = String.format("Restore volume from %s", clones.get(0));
            boolean isCG = checkCloneConsistencyGroup(clones.get(0), _dbClient, completer);

            workflow.createStep(RESTORE_FROM_CLONE_GROUP, description, waitFor,
                    storage, getDeviceType(storage), BlockDeviceController.class,
                    restoreFromCloneMethod(storage, clones, updateOpStatus, isCG),
                    rollbackMethodNullMethod(), null);

            String previousStep = RESTORE_FROM_CLONE_GROUP;
            if (isCG && system.deviceIsType(Type.vnxblock)) {
                for (URI cloneUri : clones) {
                    Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage, Arrays.asList(cloneUri), false);
                    String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                            "Waiting for synchronization", previousStep, storage,
                            system.getSystemType(), getClass(), waitForSyncMethod, rollbackMethodNullMethod(), null);
                    previousStep = waitForSyncStep;
                }

            } else {
                Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage, clones, isCG);
                String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                        "Waiting for synchronization", previousStep, storage,
                        system.getSystemType(), getClass(), waitForSyncMethod, rollbackMethodNullMethod(), null);
                previousStep = waitForSyncStep;
            }
            if (system.deviceIsType(Type.vmax) || system.deviceIsType(Type.vnxblock)) {
                addFractureSteps(workflow, system, clones, previousStep, isCG);
            }
            _log.info("Executing workflow {}", RESTORE_FROM_CLONE_GROUP);
            String msg = String.format("Restore from %s completed successfully", clones.get(0));

            workflow.executePlan(completer, msg);

        } catch (Exception e) {
            String msg = String.format("Could not restore from the clone %s", clones.get(0));
            _log.error(msg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
        }

    }

    /**
     * Return a Workflow.Method for restoreVolume
     *
     * @param storage storage system
     * @param pool storage pool
     * @param volume target of restore operation
     * @param snapshot snapshot to restore from
     * @param updateOpStatus update operation status flag
     * @return Workflow.Method
     */
    public static Workflow.Method restoreFromCloneMethod(URI storage, List<URI> clone, Boolean updateOpStatus, boolean isCG) {
        return new Workflow.Method("restoreFromCloneStep", storage, clone, updateOpStatus, isCG);
    }

    public boolean restoreFromCloneStep(URI storage, List<URI> clones, Boolean updateOpStatus, boolean isCG, String opId)
            throws ControllerException {
        TaskCompleter completer = null;
        try {
            StorageSystem storageDevice = _dbClient.queryObject(StorageSystem.class, storage);
            if (!isCG) {
                completer = new CloneRestoreCompleter(clones.get(0), opId);
                getDevice(storageDevice.getSystemType()).doRestoreFromClone(storageDevice, clones.get(0), completer);
            } else {
                CloneRestoreCompleter taskCompleter = new CloneRestoreCompleter(clones, opId);
                getDevice(storageDevice.getSystemType()).doRestoreFromGroupClone(storageDevice, clones, taskCompleter);
            }

        } catch (Exception e) {
            _log.error(String.format("restoreFromClone failed - storage: %s,clone: %s",
                    storage.toString(), clones.get(0).toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
            doFailTask(Volume.class, clones, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            return false;
        }
        return true;
    }

    private void addFractureSteps(Workflow workflow, StorageSystem system, List<URI> clone, String previousStep, boolean isCG) {

        _log.info("Adding fracture restore post-step for clone {}", clone.toString());
        String description = String.format("Fracture clone %s", clone.toString());
        workflow.createStep(FRACTURE_CLONE_GROUP, description, previousStep,
                system.getId(), system.getSystemType(), BlockDeviceController.class,
                fractureCloneMethod(system.getId(), clone, isCG),
                rollbackMethodNullMethod(), null);

    }

    public static Workflow.Method fractureCloneMethod(URI storage, List<URI> clone, boolean isCG) {
        return new Workflow.Method(FRACTURE_CLONE_METHOD, storage, clone, isCG);
    }

    public boolean fractureClone(URI storage, List<URI> clone, boolean isCG, String opId) {
        _log.info("Fracture clone: {}", clone.get(0));
        TaskCompleter completer = null;
        try {
            StorageSystem storageDevice = _dbClient.queryObject(StorageSystem.class, storage);
            WorkflowStepCompleter.stepExecuting(opId);
            if (!isCG) {
                Volume cloneVol = _dbClient.queryObject(Volume.class, clone.get(0));
                completer = new CloneFractureCompleter(clone.get(0), opId);
                WorkflowStepCompleter.stepExecuting(opId);
                // Synchronous operation
                getDevice(storageDevice.getSystemType()).doFractureClone(storageDevice, cloneVol.getAssociatedSourceVolume(), clone.get(0),
                        completer);
            } else {
                _log.info("Fracture group clone.");
                completer = new CloneFractureCompleter(clone, opId);
                WorkflowStepCompleter.stepExecuting(opId);
                // Synchronous operation
                getDevice(storageDevice.getSystemType()).doFractureGroupClone(storageDevice, clone, completer);
            }
        } catch (Exception e) {
            _log.error(
                    String.format("Fracture restore sessions step failed - storage: %s, clone: %s",
                            storage.toString(), clone.toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
            doFailTask(Volume.class, clone, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            return false;
        }

        return true;
    }

    private static final String RESYNC_CLONE_WF_NAME = "RESYNC_CLONE_WORKFLOW";
    private static final String RESYNC_CLONE_GROUP = "BlockDeviceResyncClone";

    @Override
    public void resyncFullCopy(URI storage, List<URI> clones,
            Boolean updateOpStatus, String opId) throws InternalException {
        CloneCreateWorkflowCompleter completer = new CloneCreateWorkflowCompleter(clones, opId);
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, RESYNC_CLONE_WF_NAME, false, opId);
            _log.info("Created new resync workflow with operation id {}", opId);
            boolean isCG = checkCloneConsistencyGroup(clones.get(0), _dbClient, completer);
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, storage);
            String description = String.format("Resync clone %s", clones.get(0));
            workflow.createStep(RESYNC_CLONE_GROUP, description, null,
                    storage, getDeviceType(storage), BlockDeviceController.class,
                    resyncCloneMethod(storage, clones, updateOpStatus, isCG),
                    rollbackMethodNullMethod(), null);
            String previousStep = RESYNC_CLONE_GROUP;
            if (isCG && system.deviceIsType(Type.vnxblock)) {
                for (URI cloneUri : clones) {
                    Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage, Arrays.asList(cloneUri), false);
                    String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                            "Waiting for synchronization", previousStep, storage,
                            system.getSystemType(), getClass(), waitForSyncMethod, null, null);
                    previousStep = waitForSyncStep;
                }

            } else {
                Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage, clones, isCG);
                String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                        "Waiting for synchronization", previousStep, storage,
                        system.getSystemType(), getClass(), waitForSyncMethod, null, null);
                previousStep = waitForSyncStep;
            }
            if (system.deviceIsType(Type.vnxblock)) {
                addFractureSteps(workflow, system, clones, previousStep, isCG);
            } else {
                setCloneReplicaStateStep(workflow, system, clones, previousStep, ReplicationState.SYNCHRONIZED);
            }
            _log.info("Executing workflow {}", RESYNC_CLONE_GROUP);
            String msg = String.format("Resync %s completed successfully", clones.get(0));

            workflow.executePlan(completer, msg);

        } catch (Exception e) {
            String msg = String.format("Could not resync the clone %s", clones.get(0));
            _log.error(msg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
        }

    }

    /**
     * Return a Workflow.Method for resync
     *
     * @param storage storage system
     * @param clone list of clones
     * @param updateOpStatus update operation status flag
     * @return Workflow.Method
     */
    public static Workflow.Method resyncCloneMethod(URI storage, List<URI> clone, Boolean updateOpStatus, boolean isCG) {
        return new Workflow.Method("resyncFullCopyStep", storage, clone, updateOpStatus, isCG);
    }

    public boolean resyncFullCopyStep(URI storage, List<URI> clone, Boolean updateOpStatus, boolean isCG, String opId)
            throws ControllerException {
        _log.info("Start resync full copy");
        CloneResyncCompleter taskCompleter = new CloneResyncCompleter(clone, opId);
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            if (checkCloneConsistencyGroup(clone.get(0), _dbClient, taskCompleter)) {
                _log.info("resync group full copy");
                getDevice(storageSystem.getSystemType()).doResyncGroupClone(storageSystem, clone, taskCompleter);
            } else {
                getDevice(storageSystem.getSystemType()).doResyncClone(storageSystem, clone.get(0), taskCompleter);
            }
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            doFailTask(Volume.class, clone, opId, serviceError);
            return false;
        }
        return true;
    }

    private void setCloneReplicaStateStep(Workflow workflow, StorageSystem system, List<URI> clones, String previousStep,
            ReplicationState state) {
        _log.info("Setting the clone replica state.");
        workflow.createStep("SET_FINAL_REPLICA_STATE", "Set the clones replica state", previousStep,
                system.getId(), system.getSystemType(), BlockDeviceController.class,
                setCloneStateMethod(clones, state), null, null);

    }

    private final static String SET_CLONE_STATE_METHOD = "setCloneState";

    public static Workflow.Method setCloneStateMethod(List<URI> clone, ReplicationState state) {
        return new Workflow.Method(SET_CLONE_STATE_METHOD, clone, state);
    }

    public void setCloneState(List<URI> clones, ReplicationState state, String opId) {
        _log.info("Set clones state");
        List<Volume> cloneVols = _dbClient.queryObject(Volume.class, clones);
        for (Volume cloneVol : cloneVols) {
            cloneVol.setReplicaState(state.name());
        }
        _dbClient.persistObject(cloneVols);
        CloneCreateWorkflowCompleter completer = new CloneCreateWorkflowCompleter(clones, opId);
        completer.ready(_dbClient);
    }

    public void setSrdfDeviceController(SRDFDeviceController srdfDeviceController) {
        this.srdfDeviceController = srdfDeviceController;
    }

    @Override
    public void resyncSnapshot(URI storage, URI volume, URI snapshot, Boolean updateOpStatus, String opId)
            throws ControllerException {
        TaskCompleter completer = null;
        try {
            StorageSystem storageDevice = _dbClient.queryObject(StorageSystem.class, storage);
            BlockSnapshot snapObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            completer = new BlockSnapshotResyncCompleter(snapObj, opId, updateOpStatus);
            getDevice(storageDevice.getSystemType()).doResyncSnapshot(storageDevice, volume, snapshot, completer);
        } catch (Exception e) {
            _log.error(String.format("resync snapshot failed - storage: %s, volume: %s, snapshot: %s",
                    storage.toString(), volume.toString(), snapshot.toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
            doFailTask(BlockSnapshot.class, snapshot, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    public String detachCloneStep(Workflow workflow, String waitFor, StorageSystem storageSystem, List<URI> cloneList,
            boolean isRemoveAll) {
        URI storage = storageSystem.getId();
        if (isRemoveAll) {
            Workflow.Method detachMethod = detachFullCopyMethod(storage, cloneList);
            waitFor = workflow.createStep(FULL_COPY_DETACH_STEP_GROUP, "Detaching group clone", waitFor,
                    storage, storageSystem.getSystemType(), getClass(), detachMethod, null, null);
        } else {
            Workflow.Method detachMethod = detachListCloneMethod(storage, cloneList);
            waitFor = workflow.createStep(FULL_COPY_DETACH_STEP_GROUP, "Detaching list clone", waitFor,
                    storage, storageSystem.getSystemType(), getClass(), detachMethod, null, null);
        }

        return waitFor;
    }

    public Workflow.Method detachListCloneMethod(URI storage, List<URI> cloneList) {
        return new Workflow.Method("detachListClone", storage, cloneList);
    }

    public void detachListClone(URI storage, List<URI> cloneList, String taskId)
            throws ControllerException {
        _log.info("START detachListClone: {}", Joiner.on("\t").join(cloneList));

        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            TaskCompleter taskCompleter = new VolumeDetachCloneCompleter(cloneList, taskId);
            getDevice(storageSystem.getSystemType()).doDetachListReplica(storageSystem, cloneList, taskCompleter);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(taskId, serviceError);
            doFailTask(Volume.class, cloneList, taskId, serviceError);
        }
    }

    public String deleteMirrorStep(Workflow workflow, String waitFor, URI storage, StorageSystem storageSystem, List<URI> mirrorList,
            boolean isRemoveAll) {
        String mirrorStr = Joiner.on("\t").join(mirrorList);
        _log.info("Start deleteMirror Step for mirror:{}", mirrorStr);
        List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
        if (isRemoveAll) {
            // Optionally create a step to pause (fracture) the mirror
            if (mirrorIsPausable(mirrors)) {
                _log.info("Adding group fracture mirror step");
                waitFor = workflow.createStep("deactivate",
                        String.format("Fracture mirror: %s", mirrorList.get(0)),
                        waitFor, storage, getDeviceType(storage),
                        this.getClass(),
                        fractureMirrorMethod(storage, mirrorList, isRemoveAll, false),
                        null, null);
            }
            _log.info("Adding group detach mirror step");
            Workflow.Method detach = detachMirrorMethod(storage, mirrorList, isRemoveAll);
            waitFor = workflow.createStep("deactivate", "detaching mirror volume: " + mirrorStr, waitFor, storage,
                    storageSystem.getSystemType(), getClass(), detach, null, null);
            _log.info("Adding group delete mirror step");
            Workflow.Method delete = deleteMirrorMethod(storage, mirrorList, isRemoveAll);
            waitFor = workflow.createStep("deactivate", "deleting mirror volume: " + mirrorStr, waitFor, storage,
                    storageSystem.getSystemType(), getClass(), delete, null, null);
        } else {
            for (BlockMirror mirror : mirrors) {
                // Optionally create a step to pause (fracture) the mirror
                if (mirrorIsPausable(Arrays.asList(mirror))) {
                    _log.info("Adding fracture mirror step");
                    waitFor = workflow.createStep("deactivate",
                            String.format("Fracture mirror: %s", mirrorList.get(0)),
                            waitFor, storage, getDeviceType(storage),
                            this.getClass(),
                            fractureMirrorMethod(storage, Arrays.asList(mirror.getId()), isRemoveAll, false),
                            null, null);
                }
                _log.info("Adding detach mirror step");
                Workflow.Method detach = detachMirrorMethod(storage, Arrays.asList(mirror.getId()), isRemoveAll);
                waitFor = workflow.createStep("deactivate", "detaching mirror volume: " + mirror.getId(), waitFor, storage,
                        storageSystem.getSystemType(), getClass(), detach, null, null);
                _log.info("Adding delete mirror step");
                Workflow.Method delete = deleteMirrorMethod(storage, Arrays.asList(mirror.getId()), isRemoveAll);
                waitFor = workflow.createStep("deactivate", "deleting mirror volume: " + mirror.getId(), waitFor, storage,
                        storageSystem.getSystemType(), getClass(), delete, null, null);
            }
        }
        return waitFor;
    }

    public String deleteSnapshotStep(Workflow workflow, String waitFor, URI storage, StorageSystem storageSystem,
            List<URI> snapshotList, boolean isRemoveAll) {
        if (isRemoveAll) {
            _log.info("Adding group remove snapshot step");
            Workflow.Method deleteMethod = deleteSnapshotMethod(storage, snapshotList.get(0));
            waitFor = workflow.createStep(SNAPSHOT_DELETE_STEP_GROUP, "Deleting snapshot", waitFor,
                    storage, storageSystem.getSystemType(), getClass(), deleteMethod, null, null);
        } else {
            for (URI uri : snapshotList) {
                _log.info("Adding single remove snapshot step: {}", uri);
                Workflow.Method deleteMethod = deleteSelectedSnapshotMethod(storage, uri);
                waitFor = workflow.createStep(SNAPSHOT_DELETE_STEP_GROUP, "Deleting snapshot", waitFor,
                        storage, storageSystem.getSystemType(), getClass(), deleteMethod, null, null);
            }
        }

        return waitFor;
    }

    public Workflow.Method deleteSnapshotMethod(URI storage, URI snapshot) {
        return new Workflow.Method("deleteSnapshot", storage, snapshot);
    }

    public Workflow.Method deleteSelectedSnapshotMethod(URI storage, URI snapshot) {
        return new Workflow.Method("deleteSelectedSnapshot", storage, snapshot);
    }

    public void deleteSelectedSnapshot(URI storage, URI snapshot, String opId) throws ControllerException {
        _log.info("START deleteSelectedSnapshot");
        TaskCompleter completer = null;
        WorkflowStepCompleter.stepExecuting(opId);
        try {
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            BlockSnapshot snapObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            completer = BlockSnapshotDeleteCompleter.createCompleter(_dbClient, snapObj, opId);
            getDevice(storageObj.getSystemType()).doDeleteSelectedSnapshot(storageObj, snapshot, completer);
            WorkflowStepCompleter.stepSucceded(opId);
        } catch (Exception e) {
            if (completer != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                WorkflowStepCompleter.stepFailed(opId, serviceError);
                completer.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.deleteVolumeSnapshotFailed(e);
            }
        }
    }

    /**
     * Add step to create list clone.
     *
     * @param workflow The Workflow being built
     * @param storageSystem Storage system
     * @param waitFor Previous step to waitFor
     * @param cloneList List of URIs for clones to be created
     * @return last step added to waitFor
     */
    public String createListCloneStep(Workflow workflow, StorageSystem storageSystem, List<URI> cloneList, String waitFor) {
        URI storage = storageSystem.getId();
        Workflow.Method createMethod = createListCloneMethod(storage, cloneList, false);
        Workflow.Method rollbackMethod = rollbackListCloneMethod(storage, cloneList);
        waitFor = workflow.createStep(BlockDeviceController.FULL_COPY_CREATE_STEP_GROUP, "Creating full copy", waitFor, storage,
                storageSystem.getSystemType(), getClass(), createMethod, rollbackMethod, null);

        return waitFor;
    }

    public Workflow.Method createListCloneMethod(URI storage, List<URI> cloneList, Boolean createInactive) {
        return new Workflow.Method("createListClone", storage, cloneList, createInactive);
    }

    public void createListClone(URI storage, List<URI> cloneList, Boolean createInactive, String taskId) {
        try {
            WorkflowStepCompleter.stepExecuting(taskId);
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            TaskCompleter taskCompleter = new CloneCreateCompleter(cloneList, taskId);
            getDevice(storageSystem.getSystemType()).doCreateListReplica(storageSystem, cloneList, createInactive, taskCompleter);
        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(taskId, serviceError);
            doFailTask(Volume.class, cloneList, taskId, serviceError);
        }
    }

    public Workflow.Method rollbackListCloneMethod(URI storage, List<URI> cloneList) {
        return new Workflow.Method("rollbackListClone", storage, cloneList);
    }

    public void rollbackListClone(URI storage, List<URI> cloneList, String taskId) {
        WorkflowStepCompleter.stepExecuting(taskId);
        _log.info("Rollback list clone");
        List<Volume> clones = _dbClient.queryObject(Volume.class, cloneList);
        List<Volume> clonesNoRollback = new ArrayList<Volume>();
        List<URI> clonesToRollback = new ArrayList<URI>();
        try {
            for (Volume clone : clones) {
                if (isNullOrEmpty(clone.getNativeId())) {
                    clone.setInactive(true);
                    clonesNoRollback.add(clone);
                } else {
                    clonesToRollback.add(clone.getId());
                }
            }

            if (!clonesNoRollback.isEmpty()) {
                _dbClient.persistObject(clonesNoRollback);
            }

            if (!clonesToRollback.isEmpty()) {
                _log.info("Detach list clone for rollback");
                detachListClone(storage, clonesToRollback, taskId);
                _log.info("Delete clones for rollback");
                deleteVolumes(storage, clonesToRollback, taskId);
            }

            WorkflowStepCompleter.stepSucceded(taskId);
        } catch (InternalException ie) {
            _log.error(String.format("rollbackListClone failed - Array: %s, clones: %s", storage, Joiner.on("\t").join(cloneList)));
            _log.error(ie.getMessage(), ie);
            doFailTask(Volume.class, cloneList, taskId, ie);
            WorkflowStepCompleter.stepFailed(taskId, ie);
        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(taskId, serviceError);
            doFailTask(Volume.class, cloneList, taskId, serviceError);
        }
    }

    /**
     * Add Steps to create list mirror.
     *
     * @param workflow The Workflow being built
     * @param storageSystem Storage system
     * @param waitFor Previous step to waitFor
     * @param mirrorList List of URIs for mirrors to be created
     * @return last step added to waitFor
     */
    public String createListMirrorStep(Workflow workflow, String waitFor, StorageSystem storageSystem, List<URI> mirrorList)
            throws ControllerException {
        URI storage = storageSystem.getId();
        waitFor = workflow.createStep(CREATE_MIRRORS_STEP_GROUP, "Creating list mirror", waitFor,
                storage, storageSystem.getSystemType(),
                this.getClass(),
                createListMirrorMethod(storage, mirrorList, false),
                rollbackListMirrorMethod(storage, mirrorList), null);

        return waitFor;
    }

    public Workflow.Method createListMirrorMethod(URI storage, List<URI> mirrorList, Boolean createInactive) {
        return new Workflow.Method("createListMirror", storage, mirrorList, createInactive);
    }

    public void createListMirror(URI storage, List<URI> mirrorList, Boolean createInactive, String opId)
            throws ControllerException {
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockMirrorCreateCompleter(mirrorList, opId);
            getDevice(storageObj.getSystemType()).doCreateListReplica(storageObj, mirrorList, createInactive, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            if (completer != null) {
                completer.error(_dbClient, serviceError);
            }
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    public Workflow.Method rollbackListMirrorMethod(URI storage, List<URI> mirrorList) {
        return new Workflow.Method("rollbackListMirror", storage, mirrorList);
    }

    public void rollbackListMirror(URI storage, List<URI> mirrorList, String taskId) {
        WorkflowStepCompleter.stepExecuting(taskId);

        try {
            List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
            List<URI> mirrorURIsToRollback = new ArrayList<URI>(transform(mirrors, FCTN_MIRROR_TO_URI));
            String mirrorNativeIds = Joiner.on(", ").join(transform(mirrors, fctnBlockObjectToNativeID()));
            if (mirrorIsPausable(mirrors)) {
                _log.info("Attempting to fracture {} for rollback", mirrorNativeIds);
                fractureMirror(storage, mirrorURIsToRollback, false, false, taskId);
            }

            _log.info("Attempting to detach {} for rollback", mirrorNativeIds);
            detachMirror(storage, mirrorURIsToRollback, false, false, taskId);
            _log.info("Attempting to delete {} for rollback", mirrorNativeIds);
            deleteMirror(storage, mirrorURIsToRollback, false, taskId);
            WorkflowStepCompleter.stepSucceded(taskId);
        } catch (InternalException ie) {
            _log.error(String.format("rollbackListMirror failed - Array:%s, Mirror:%s", storage, Joiner.on("\t").join(mirrorList)));
            doFailTask(Volume.class, mirrorList, taskId, ie);
            WorkflowStepCompleter.stepFailed(taskId, ie);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(taskId, serviceError);
            doFailTask(Volume.class, mirrorList, taskId, serviceError);
        }
    }

    public void createListSnapshot(URI storage, List<URI> snapshotList, Boolean createInactive, Boolean readOnly, String opId)
            throws ControllerException {
        WorkflowStepCompleter.stepExecuting(opId);
        TaskCompleter completer = null;
        try {
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockSnapshotCreateCompleter(snapshotList, opId);
            getDevice(storageObj.getSystemType()).doCreateListReplica(storageObj, snapshotList, createInactive, completer);
        } catch (Exception e) {
            if (completer != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                WorkflowStepCompleter.stepFailed(opId, serviceError);
                completer.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.createVolumeSnapshotFailed(e);
            }
        }
    }
}
