/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.protectioncontroller.impl.recoverpoint;

import static com.emc.storageos.db.client.constraint.AlternateIdConstraint.Factory.getVolumesByAssociatedId;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import com.emc.fapiclient.ws.FunctionalAPIActionFailedException_Exception;
import com.emc.fapiclient.ws.FunctionalAPIInternalError_Exception;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshot.TechnologyType;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.ProtectionSet.ProtectionStatus;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.SystemType;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.locking.LockRetryException;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.protectioncontroller.RPController;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.recoverpoint.impl.RecoverPointClient;
import com.emc.storageos.recoverpoint.objectmodel.RPBookmark;
import com.emc.storageos.recoverpoint.objectmodel.RPConsistencyGroup;
import com.emc.storageos.recoverpoint.objectmodel.RPSite;
import com.emc.storageos.recoverpoint.requests.CGPolicyParams;
import com.emc.storageos.recoverpoint.requests.CGRequestParams;
import com.emc.storageos.recoverpoint.requests.CreateBookmarkRequestParams;
import com.emc.storageos.recoverpoint.requests.CreateCopyParams;
import com.emc.storageos.recoverpoint.requests.CreateRSetParams;
import com.emc.storageos.recoverpoint.requests.CreateVolumeParams;
import com.emc.storageos.recoverpoint.requests.MultiCopyDisableImageRequestParams;
import com.emc.storageos.recoverpoint.requests.MultiCopyEnableImageRequestParams;
import com.emc.storageos.recoverpoint.requests.MultiCopyRestoreImageRequestParams;
import com.emc.storageos.recoverpoint.requests.RPCopyRequestParams;
import com.emc.storageos.recoverpoint.requests.RecreateReplicationSetRequestParams;
import com.emc.storageos.recoverpoint.requests.UpdateCGPolicyParams;
import com.emc.storageos.recoverpoint.responses.CreateBookmarkResponse;
import com.emc.storageos.recoverpoint.responses.GetBookmarksResponse;
import com.emc.storageos.recoverpoint.responses.MultiCopyDisableImageResponse;
import com.emc.storageos.recoverpoint.responses.MultiCopyEnableImageResponse;
import com.emc.storageos.recoverpoint.responses.MultiCopyRestoreImageResponse;
import com.emc.storageos.recoverpoint.responses.RecoverPointCGResponse;
import com.emc.storageos.recoverpoint.responses.RecoverPointStatisticsResponse;
import com.emc.storageos.recoverpoint.responses.RecoverPointVolumeProtectionInfo;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.services.util.TimeUtils;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.ApplicationAddVolumeList;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.StorageController;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.impl.block.ExportWorkflowUtils;
import com.emc.storageos.volumecontroller.impl.block.MaskingOrchestrator;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.AuditBlockUtil;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotActivateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotDeactivateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.RPCGCopyVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.RPCGCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.RPCGExportCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.RPCGExportDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.RPCGExportOrchestrationCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.RPCGProtectionTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.TaskLockingCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeVpoolChangeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.plugins.RPStatisticsHelper;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.volumecontroller.placement.StoragePortsAssigner;
import com.emc.storageos.volumecontroller.placement.StoragePortsAssignerFactory;
import com.emc.storageos.vplexcontroller.VPlexDeviceController;
import com.emc.storageos.vplexcontroller.completers.VolumeGroupUpdateTaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowState;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.base.Joiner;

/**
 * RecoverPoint specific protection controller implementation.
 */
public class RPDeviceController implements RPController, BlockOrchestrationInterface, MaskingOrchestrator {

    private static final String REPLICATION_GROUP_RPTARGET_SUFFIX = "-RPTARGET";
    private static final String DASHED_NEWLINE = "---------------------------------%n";
    private static final String ALPHA_NUMERICS = "[^A-Za-z0-9_]";

    // RecoverPoint consistency group name prefix
    private static final String CG_NAME_PREFIX = "ViPR-";
    private static final String VIPR_SNAPSHOT_PREFIX = "ViPR-snapshot-";

    // Various steps for workflows
    private static final String STEP_REMOVE_PROTECTION = "rpRemoveProtectionStep";
    private static final String STEP_CG_CREATION = "rpCgCreation";
    private static final String STEP_CG_MODIFY = "rpCgModify";
    private static final String STEP_EXPORT_GROUP = "rpExportGroup";
    private static final String STEP_DV_REMOVE_CG = "rpDvRemoveCG";
    private static final String STEP_DV_REMOVE_VOLUME_EXPORT = "rpDvRemoveVolumeExport";
    private static final String STEP_DV_CLEANUP = "rpDvDeleteCleanup";
    private static final String STEP_ENABLE_IMAGE_ACCESS = "rpEnableImageAccess";
    private static final String STEP_DISABLE_IMAGE_ACCESS = "rpDisableImageAccess";
    private static final String STEP_EXPORT_DELETE_SNAPSHOT = "rpExportDeleteSnapshot";
    private static final String STEP_EXPORT_GROUP_DELETE = "rpExportGroupDelete";
    private static final String STEP_EXPORT_GROUP_DISABLE = "rpExportGroupDisable";
    private static final String STEP_EXPORT_REMOVE_SNAPSHOT = "rpExportRemoveSnapshot";
    private static final String STEP_POST_VOLUME_CREATE = "rpPostVolumeCreate";
    private static final String STEP_ADD_JOURNAL_VOLUME = "rpAddJournalVolume";
    private static final String STEP_PRE_VOLUME_EXPAND = "rpPreVolumeExpand";
    private static final String STEP_POST_VOLUME_EXPAND = "rpPostVolumeExpand";
    private static final String STEP_BOOKMARK_CREATE = "rpCreateBookmark";
    private static final String STEP_CREATE_BLOCK_SNAPSHOT = "rpCreateBlockSnapshot";
    private static final String STEP_UPDATE_CG_POLICY = "rpUpdateConsistencyGroupPolicy";
    private static final String STEP_EXPORT_ORCHESTRATION = "rpExportOrchestration";
    private static final String STEP_RP_EXPORT_ORCHESTRATION = "rpExportGroupOrchestration";
    private static final String STEP_PERFORM_PROTECTION_OPERATION = "performProtectionOperationStep";

    public static final String STEP_PRE_VOLUME_RESTORE = "rpPreVolumeRestore";
    public static final String STEP_POST_VOLUME_RESTORE = "rpPostVolumeRestore";

    // Methods in the create workflow. Constants helps us avoid step dependency flubs.
    private static final String METHOD_CG_CREATE_STEP = "cgCreateStep";
    private static final String METHOD_CG_CREATE_ROLLBACK_STEP = "cgCreateRollbackStep";

    // Methods in the add journal volume workflow.
    private static final String METHOD_ADD_JOURNAL_STEP = "addJournalStep";
    private static final String METHOD_ADD_JOURNAL_ROLLBACK_STEP = "addJournalRollbackStep";

    // Methods in the update workflow.
    private static final String METHOD_CG_MODIFY_STEP = "cgModifyStep";
    private static final String METHOD_CG_MODIFY_ROLLBACK_STEP = "cgModifyRollbackStep";

    // Methods in the delete workflow.
    private static final String METHOD_DELETE_CG_STEP = "cgDeleteStep";

    // Methods in the export group create workflow
    private static final String METHOD_ENABLE_IMAGE_ACCESS_STEP = "enableImageAccessStep";
    private static final String METHOD_ENABLE_IMAGE_ACCESS_ROLLBACK_STEP = "enableImageAccessStepRollback";

    // Methods in the create full copy workflow
    private static final String METHOD_ENABLE_IMAGE_ACCESS_CREATE_REPLICA_STEP = "enableImageAccessForCreateReplicaStep";
    private static final String METHOD_DISABLE_IMAGE_ACCESS_CREATE_REPLICA_STEP = "disableImageAccessForCreateReplicaStep";

    // Methods in the export group delete workflow
    private static final String METHOD_DISABLE_IMAGE_ACCESS_STEP = "disableImageAccessStep";

    // Methods in the export group remove volume workflow
    private static final String METHOD_DISABLE_IMAGE_ACCESS_SINGLE_STEP = "disableImageAccessSingleStep";

    // Methods in restore volume from snapshot workflow
    private static final String METHOD_RESTORE_VOLUME_STEP = "restoreVolume";

    // Methods in the expand volume workflow
    public static final String METHOD_DELETE_RSET_STEP = "deleteRSetStep";
    private static final String METHOD_DELETE_RSET_ROLLBACK_STEP = "recreateRSetStep"; // Intentionally recreateRSetStep
    public static final String METHOD_RECREATE_RSET_STEP = "recreateRSetStep";

    // Methods in the create RP snapshot workflow
    private static final String METHOD_CREATE_BOOKMARK_STEP = "createBookmarkStep";
    private static final String METHOD_ROLLBACK_CREATE_BOOKMARK_STEP = "createBookmarkRollbackStep";
    private static final String METHOD_CREATE_BLOCK_SNAPSHOT_STEP = "createBlockSnapshotStep";
    private static final String METHOD_ROLLBACK_CREATE_BLOCK_SNAPSHOT = "createBlockSnapshotRollbackStep";
    private static final String METHOD_SNAPSHOT_DISABLE_IMAGE_ACCESS_SINGLE_STEP = "snapshotDisableImageAccessSingleStep";

    // Methods in the RP update CG workflow
    private static final String METHOD_UPDATE_CG_POLICY_STEP = "updateConsistencyGroupPolicyStep";

    private static final String METHOD_RP_VPLEX_REINSTATE_SRC_VVOL_STEP = "rpVPlexReinstateSourceVirtualVolumeStep";

    // Methods in the RP export workflow
    private static final String METHOD_EXPORT_ORCHESTRATE_STEP = "exportOrchestrationSteps";
    private static final String METHOD_EXPORT_ORCHESTRATE_ROLLBACK_STEP = "exportOrchestrationRollbackSteps";

    // Methods in the RP export workflow
    private static final String METHOD_RP_EXPORT_ORCHESTRATE_STEP = "rpExportOrchestrationSteps";
    private static final String METHOD_RP_EXPORT_ORCHESTRATE_ROLLBACK_STEP = "rpExportOrchestrationRollbackSteps";

    private static final String EXPORT_ORCHESTRATOR_WF_NAME = "RP_EXPORT_ORCHESTRATION_WORKFLOW";
    private static final String ROLLBACK_METHOD_NULL = "rollbackMethodNull";

    // Methods in the RP remove protection workflow
    private static final String METHOD_REMOVE_PROTECTION_STEP = "removeProtectionStep";
    private static final String METHOD_REMOVE_PROTECTION_ROLLBACK_STEP = "removeProtectionRollback";

    private static final String METHOD_PERFORM_PROTECTION_OPERATION = "performProtectionOperationStep";

    protected final static String CONTROLLER_SVC = "controllersvc";
    protected final static String CONTROLLER_SVC_VER = "1";
    private static final Logger _log = LoggerFactory.getLogger(RPDeviceController.class);
    private static final int MAX_ATTEMPTS_TO_WAIT_FOR_CG_CREATE = 10;
    private static final int SECONDS_TO_WAIT_FOR_CG_CREATE = 30;

    // RP OP constants
    private static final String STOP = "stop";
    private static final String START = "start";
    private static final String SYNC = "sync";
    private static final String PAUSE = "pause";
    private static final String RESUME = "resume";
    private static final String FAILOVER_TEST = "failover-test";
    private static final String FAILOVER = "failover";
    private static final String FAILOVER_CANCEL = "failover-cancel";
    private static final String SWAP = "swap";
    private static final String FAILOVER_TEST_CANCEL = "failover-test-cancel";
    private static final String CHANGE_ACCESS_MODE = "change-access-mode";

    private static DbClient _dbClient = null;
    protected CoordinatorClient _coordinator;
    private Map<String, BlockStorageDevice> _devices;
    private NameGenerator _nameGenerator;
    private WorkflowService _workflowService;
    private ExportWorkflowUtils _exportWfUtils;
    private RPStatisticsHelper _rpStatsHelper;
    private RecordableEventManager _eventManager;
    private ControllerLockingService _locker;

    @Autowired
    private BlockDeviceController _blockDeviceController;

    @Autowired
    private VPlexDeviceController _vplexDeviceController;

    private Map<URI, Set<URI>> exportGroupVolumesAdded;
    private List<URI> exportGroupsCreated;

    @Autowired
    private AuditLogManager _auditMgr;

    /* Inner class for handling exports for RP */
    private class RPExport {
        private URI storageSystem;
        private String rpSite;
        private URI varray;
        private List<URI> volumes;
        private URI computeResource;
        // If journal varray is specified which is different from the copy for that RP site,
        // then this flag is true to indicate this is a journal only export
        private boolean isJournalExport;

        public RPExport() {
        }

        public RPExport(URI storageSystem, String rpSite, URI varray) {
            this.storageSystem = storageSystem;
            this.rpSite = rpSite;
            this.varray = varray;
        }

        public URI getStorageSystem() {
            return storageSystem;
        }

        public void setStorageSystem(URI storageSystem) {
            this.storageSystem = storageSystem;
        }

        public String getRpSite() {
            return rpSite;
        }

        public void setRpSite(String rpSite) {
            this.rpSite = rpSite;
        }

        public URI getVarray() {
            return varray;
        }

        public void setVarray(URI varray) {
            this.varray = varray;
        }

        public List<URI> getVolumes() {
            if (volumes == null) {
                volumes = new ArrayList<URI>();
            }
            return volumes;
        }

        public void setVolumes(List<URI> volumes) {
            this.volumes = volumes;
        }

        public URI getComputeResource() {
            return computeResource;
        }

        public void setComputeResource(URI computeResource) {
            this.computeResource = computeResource;
        }

        public boolean getIsJournalExport() {
            return isJournalExport;
        }

        public void setIsJournalExport(boolean isJournalExport) {
            this.isJournalExport = isJournalExport;
        }

        @Override
        public String toString() {
            return "RPExport [storageSystem=" + this.getStorageSystem().toString() + ", rpSite=" + this.getRpSite() + ", varray="
                    + this.getVarray().toString() + "]";
        }
    }

    public void setLocker(ControllerLockingService locker) {
        this._locker = locker;
    }

    public RPStatisticsHelper getRpStatsHelper() {
        return _rpStatsHelper;
    }

    public void setRpStatsHelper(RPStatisticsHelper rpStatsHelper) {
        this._rpStatsHelper = rpStatsHelper;
    }

    public void setEventManager(RecordableEventManager eventManager) {
        _eventManager = eventManager;
    }

    public void setCoordinator(CoordinatorClient locator) {
        _coordinator = locator;
    }

    public void setDevices(Map<String, BlockStorageDevice> deviceInterfaces) {
        _devices = deviceInterfaces;
    }

    public static synchronized void setDbClient(DbClient dbClient) {
        if (_dbClient == null) {
            _dbClient = dbClient;
        }
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this._workflowService = workflowService;
    }

    public void setExportWorkflowUtils(ExportWorkflowUtils exportWorkflowUtils) {
        _exportWfUtils = exportWorkflowUtils;
    }

    public NameGenerator getNameGenerator() {
        return _nameGenerator;
    }

    public void setNameGenerator(NameGenerator _nameGenerator) {
        this._nameGenerator = _nameGenerator;
    }

    @Override
    public void connect(URI systemId) throws InternalException {
        _log.debug("BEGIN RPDeviceController.connect()");
        ProtectionSystem rpSystem = null;
        rpSystem = _dbClient.queryObject(ProtectionSystem.class, systemId);

        // Verify non-null storage device returned from the database client.
        if (rpSystem == null) {
            throw DeviceControllerExceptions.recoverpoint.failedConnectingForMonitoring(systemId);
        }

        RecoverPointClient rp = RPHelper.getRecoverPointClient(rpSystem);
        rp.ping();
        _log.debug("END RPDeviceController.connect()");
    }

    @Override
    public void disconnect(URI systemId) throws InternalException {
        _log.info("BEGIN RecoverPointProtection.disconnectStorage()");
        // Retrieve the storage device info from the database.
        ProtectionSystem protectionObj = null;
        protectionObj = _dbClient.queryObject(ProtectionSystem.class, systemId);
        // Verify non-null storage device returned from the database client.
        if (protectionObj == null) {
            throw DeviceControllerExceptions.recoverpoint.failedConnectingForMonitoring(systemId);
        }

        _log.info("END RecoverPointProtection.disconnectStorage()");
    }

    @Override
    public String addStepsForCreateVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws InternalException {

        // Just grab a legit target volume that already has an assigned protection controller.
        // This will work for all operations, adding, removing, vpool change, etc.
        List<VolumeDescriptor> protectionControllerDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_TARGET, VolumeDescriptor.Type.RP_VPLEX_VIRT_TARGET,
                        VolumeDescriptor.Type.RP_EXISTING_PROTECTED_SOURCE, VolumeDescriptor.Type.RP_JOURNAL,
                        VolumeDescriptor.Type.RP_VPLEX_VIRT_JOURNAL },
                new VolumeDescriptor.Type[] {});
        // If there are no RP volumes, just return
        if (protectionControllerDescriptors.isEmpty()) {
            _log.info("No RP Steps required");
            return waitFor;
        }

        _log.info("Adding RP steps for create volumes");

        // Determine if this operation only involves adding additional journal capacity
        boolean isJournalAdd = false;
        List<VolumeDescriptor> journalDescriptors = VolumeDescriptor.filterByType(protectionControllerDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_JOURNAL, VolumeDescriptor.Type.RP_VPLEX_VIRT_JOURNAL },
                new VolumeDescriptor.Type[] {});
        if (!journalDescriptors.isEmpty()) {
            for (VolumeDescriptor journDesc : journalDescriptors) {
                if (journDesc.getCapabilitiesValues().getAddJournalCapacity()) {
                    isJournalAdd = true;
                    break;
                }
            }
        }

        // Grab any volume from the list so we can grab the protection system, which will be the same for all volumes.
        Volume volume = _dbClient.queryObject(Volume.class, protectionControllerDescriptors.get(0).getVolumeURI());
        ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, volume.getProtectionController());

        // Get only the RP volumes from the descriptors.
        List<VolumeDescriptor> volumeDescriptorsTypeFilter = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_SOURCE, VolumeDescriptor.Type.RP_JOURNAL,
                        VolumeDescriptor.Type.RP_TARGET, VolumeDescriptor.Type.RP_EXISTING_SOURCE,
                        VolumeDescriptor.Type.RP_EXISTING_PROTECTED_SOURCE, VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE,
                        VolumeDescriptor.Type.RP_VPLEX_VIRT_TARGET, VolumeDescriptor.Type.RP_VPLEX_VIRT_JOURNAL },
                new VolumeDescriptor.Type[] {});
        // If there are no RP volumes, just return
        if (volumeDescriptorsTypeFilter.isEmpty()) {
            return waitFor;
        }

        String lastStep = waitFor;

        try {
            List<VolumeDescriptor> existingProtectedSourceDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_EXISTING_PROTECTED_SOURCE }, new VolumeDescriptor.Type[] {});

            boolean executeCreateSteps = true;
            if (!existingProtectedSourceDescriptors.isEmpty() || isJournalAdd) {
                executeCreateSteps = false;
            }

            addExportVolumesSteps(workflow, volumeDescriptorsTypeFilter, waitFor, rpSystem, taskId);

            // Handle creation or updating of the Consistency Group (moved from the Export Workflow)
            // Get the CG Params based on the volume descriptors
            CGRequestParams params = this.getCGRequestParams(volumeDescriptors, rpSystem);
            updateCGParams(params);

            if (isJournalAdd) {
                lastStep = addAddJournalVolumesToCGStep(workflow, volumeDescriptors, params, rpSystem, taskId);
                return lastStep;
            }

            if (executeCreateSteps) {
                _log.info("Adding steps for Create/Update CG...");
                lastStep = addCreateOrUpdateCGStep(workflow, volumeDescriptors, params, rpSystem, taskId);
                lastStep = addPostVolumeCreateSteps(workflow, volumeDescriptors, rpSystem, taskId);
            } else {
                _log.info("Adding steps for Modifying CG...");
                lastStep = addModifyCGStep(workflow, volumeDescriptors, params, rpSystem, taskId);
            }

        } catch (Exception e) {
            doFailAddStep(volumeDescriptorsTypeFilter, taskId, e);
            throw e;
        }

        return lastStep;
    }

    /**
     * Adds any post volume create steps that are needed.
     *
     * @param workflow
     *            the current WF
     * @param volumeDescriptors
     *            all volume descriptors
     * @param rpSystem
     *            the PS
     * @param taskId
     *            the current task
     * @return the previous step group
     */
    private String addPostVolumeCreateSteps(Workflow workflow, List<VolumeDescriptor> volumeDescriptors, ProtectionSystem rpSystem,
            String taskId) {

        // Post Volume Create Step 1: RP VPlex reinstate Virtual Volume to original request.
        List<VolumeDescriptor> rpSourceDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE }, new VolumeDescriptor.Type[] {});
        if (rpSourceDescriptors != null && !rpSourceDescriptors.isEmpty()) {
            String stepId = workflow.createStepId();
            Workflow.Method rpVPlexRestoreSourceVirtualVolumeMethod = new Workflow.Method(METHOD_RP_VPLEX_REINSTATE_SRC_VVOL_STEP,
                    rpSourceDescriptors);

            workflow.createStep(STEP_POST_VOLUME_CREATE, "RP VPlex reinstate Virtual Volume to original request", STEP_CG_CREATION,
                    rpSystem.getId(), rpSystem.getSystemType(), this.getClass(), rpVPlexRestoreSourceVirtualVolumeMethod, null, stepId);
            return STEP_POST_VOLUME_CREATE;
        } else {
            return STEP_CG_CREATION;
        }
    }

    /**
     * WF Step to reinstate the RP VPLEX Source Virtual Volume to use the originally requested assets.
     *
     * With RP+VPLEX there is an option when the user adds High Availability to the Source VPool
     * to use the HA VArray (and optionally an HA VPool) as the RecoverPoint Source.
     * Meaning the HA VArray should be used for connectivity to RP and not the Source VArray.
     *
     * During RP+VPLEX placement we perform a "swap" in the backend so that Source becomes HA and
     * HA becomes Source.
     *
     * After the VPlex Virtual Volume is created we want to reverse the swap back to the original
     * request VPool/VArray for clarity purposes for the user. (i.e. we want the Virtual Volume
     * to show that it was created with the requested VPool and VArray).
     *
     * So from the backing volumes, try and find the original VPool and VArray that were used
     * for the volume create request. We can use that volume to update the VPlex Virtual
     * Volume.
     *
     * @param rpSourceDescriptors
     *            Descriptors for RP_VPLEX_VIRT_SOURCE or RP_EXISTING_SOURCE volumes
     * @param token
     *            Workflow step ID
     * @return Whether or not the operation succeeded
     * @throws InternalException
     */
    public boolean rpVPlexReinstateSourceVirtualVolumeStep(List<VolumeDescriptor> rpSourceDescriptors, String token)
            throws InternalException {
        try {
            WorkflowStepCompleter.stepExecuting(token);

            for (VolumeDescriptor volumeDescriptor : rpSourceDescriptors) {
                Volume srcVolume = _dbClient.queryObject(Volume.class, volumeDescriptor.getVolumeURI());
                // We're only concerned with the RP VPLEX Source Virtual Volume if it's VPLEX Distributed
                if (srcVolume != null && srcVolume.getAssociatedVolumes() != null && srcVolume.getAssociatedVolumes().size() >= 2) {
                    // Find the volume with the original requested assets (original Virtual Pool and Virtual Array)
                    Volume volWithOriginalAssets = findRPVPlexVolumeWithOrginalAssets(srcVolume.getAssociatedVolumes());
                    if (volWithOriginalAssets != null) {
                        _log.info(String.format(
                                "Request was for using HA side of RP VPLEX Source to protect. So we need to update the "
                                        + "Virtual Volume [%s] with the original requested assets "
                                        + "(original Virtual Pool [%s] and Virtual Array [%s])",
                                srcVolume.getLabel(), volWithOriginalAssets.getVirtualPool(), volWithOriginalAssets.getVirtualArray()));
                        // Update the Virtual Volume with the original assets.
                        srcVolume.setVirtualArray(volWithOriginalAssets.getVirtualArray());
                        srcVolume.setVirtualPool(volWithOriginalAssets.getVirtualPool());
                        _dbClient.updateObject(srcVolume);
                    }
                }
            }

            // Update the workflow state.
            WorkflowStepCompleter.stepSucceded(token);
            _log.info(METHOD_RP_VPLEX_REINSTATE_SRC_VVOL_STEP + " is complete.");

        } catch (Exception e) {
            stepFailed(token, e, METHOD_RP_VPLEX_REINSTATE_SRC_VVOL_STEP);
            return false;
        }

        return true;
    }

    /**
     * Find the volume with the original requested assets (original Virtual Pool and Virtual Array)
     * and make sure that the RP VPLEX Source Virtual Volume has those set. This is what is reflected
     * in the UI. The reason that they could be different is because of the possibility that the
     * user chose to use the HA Virtual Pool / Virtual Array as the leg connected to RP.
     *
     * @param backingVolumes
     *            backing volumes of the VPlex Virtual Volume passed in
     * @return Volume that has the original Virtual Assets from the volume create request
     */
    private Volume findRPVPlexVolumeWithOrginalAssets(StringSet backingVolumeURIs) {
        Volume volWithOriginalAssets = null;
        for (String backingVolumeURI : backingVolumeURIs) {
            Volume backingVolume = _dbClient.queryObject(Volume.class, URI.create(backingVolumeURI));
            if (backingVolume != null && backingVolume.getVirtualPool() != null) {
                VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, backingVolume.getVirtualPool());
                // Check to see if this backing volume has the the vpool with RP+VPLEX with HA connected.
                // If it is, this is the correct volume to return. It has the original assets (varray & vpool)
                // we need to update the virtual volume with.
                if (VirtualPool.isRPVPlexProtectHASide(vpool)) {
                    volWithOriginalAssets = backingVolume;
                    break;
                }
            }
        }

        return volWithOriginalAssets;
    }

    private void doFailAddStep(List<VolumeDescriptor> volumeDescriptors, String taskId, Exception e) throws InternalException {
        final List<URI> volumeURIs = getVolumeURIs(volumeDescriptors);
        final TaskLockingCompleter completer = new RPCGCreateCompleter(volumeURIs, taskId);
        _log.error("Could not create protection for RecoverPoint on volumes: " + volumeURIs, e);
        final ServiceCoded error;
        if (e instanceof ServiceCoded) {
            error = (ServiceCoded) e;
        } else {
            error = DeviceControllerErrors.recoverpoint.couldNotCreateProtectionOnVolumes(volumeURIs);
        }
        _log.error(error.getMessage());
        completer.error(_dbClient, _locker, error);
    }

    private List<URI> getVolumeURIs(List<VolumeDescriptor> volumeDescriptors) {
        List<URI> volumeURIs = new ArrayList<URI>();
        for (VolumeDescriptor volumeDescriptor : volumeDescriptors) {
            volumeURIs.add(volumeDescriptor.getVolumeURI());
        }
        return volumeURIs;
    }

    @Override
    public String addStepsForDeleteVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId)
            throws InternalException {
        // Filter to get only the RP volumes.
        List<VolumeDescriptor> rpVolumes = VolumeDescriptor.filterByType(volumes,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_SOURCE, VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE },
                new VolumeDescriptor.Type[] {});
        // If there are no RP volumes, just return
        if (rpVolumes.isEmpty()) {
            return waitFor;
        }

        // Task 1: If this is the last volume, remove the consistency group
        waitFor = addDeleteCGStep(workflow, waitFor, rpVolumes);

        // Tasks 2: Remove the volumes from the export group
        return addExportRemoveVolumesSteps(workflow, waitFor, rpVolumes);
    }

    @Override
    public String addStepsForPostDeleteVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId,
            VolumeWorkflowCompleter completer) throws InternalException {
        return waitFor;
    }

    /**
     * RP Specific steps to perform after a volume has been deleted
     *
     * @param workflow
     *            - a Workflow that is being constructed
     * @param waitFor
     *            -- The String key that should be used for waiting on previous steps in Workflow.createStep
     * @param volumes
     *            -- The entire list of VolumeDescriptors for this request (all technologies).
     * @param taskId
     *            -- The top level operation's taskId
     * @param completer
     *            -- The completer for the entire workflow.
     * @param blockDeviceController
     *            -- Reference to a BlockDeviceController, used for specific
     *            steps on the volumes not covered by RP but required for the operation to be complete.
     * @return A waitFor key that can be used by subsequent controllers to wait on
     *         the Steps created by this controller.
     * @throws InternalException
     */
    public String addStepsForPostDeleteVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId,
            VolumeWorkflowCompleter completer, BlockDeviceController blockDeviceController) throws InternalException {
        // Filter to get only the RP volumes.
        List<VolumeDescriptor> rpSourceDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_SOURCE, VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE },
                new VolumeDescriptor.Type[] {});
        // If there are no RP volumes, just return
        if (rpSourceDescriptors.isEmpty()) {
            return waitFor;
        }

        return addRemoveProtectionOnVolumeStep(workflow, waitFor, volumeDescriptors, taskId, blockDeviceController);
    }

    @Override
    public String addStepsForChangeVirtualPool(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws InternalException {
        return addStepsForCreateVolumes(workflow, waitFor, volumeDescriptors, taskId);
    }

    /**
     * Create the RP Client consistency group request object based on the incoming prepared volumes.
     *
     * @param volumeDescriptors
     *            volume descriptor objects
     * @param rpSystem
     * @return RP request to create CG
     * @throws DatabaseException
     */
    private CGRequestParams getCGRequestParams(List<VolumeDescriptor> volumeDescriptors, ProtectionSystem rpSystem)
            throws DatabaseException {
        _log.info("Creating CG Request param...");

        // Maps of replication set request objects, where the key is the rset name itself
        Map<String, CreateRSetParams> rsetParamsMap = new HashMap<String, CreateRSetParams>();
        // Maps of the copy request objects, where the key is the copy name itself
        Map<String, CreateCopyParams> copyParamsMap = new HashMap<String, CreateCopyParams>();

        // The parameters we need at the CG Level that we can only get from looking at the Volumes
        Project project = null;
        String cgName = null;
        Set<String> productionCopies = new HashSet<String>();
        BlockConsistencyGroup cg = null;
        String copyMode = null;
        String rpoType = null;
        Long rpoValue = null;
        int maxNumberOfSnapShots = 0;

        Map<URI, Volume> volumeMap = new HashMap<URI, Volume>();

        // Sort the volume descriptors using the natural order of the enum.
        // In this case sort as:
        // SOURCE, TARGET, JOURNAL
        // We want SOURCE volumes to be processed first below to populate the
        // productionCopies in order.
        VolumeDescriptor.sortByType(volumeDescriptors);

        // Next create all of the request objects we need
        for (VolumeDescriptor volumeDescriptor : volumeDescriptors) {
            Volume volume = null;
            if (volumeMap.containsKey(volumeDescriptor.getVolumeURI())) {
                volume = volumeMap.get(volumeDescriptor.getVolumeURI());
            } else {
                volume = _dbClient.queryObject(Volume.class, volumeDescriptor.getVolumeURI());
                volumeMap.put(volume.getId(), volume);
            }

            boolean isMetroPoint = RPHelper.isMetroPointVolume(_dbClient, volume);
            boolean isRPSource = RPHelper.isRPSource(volumeDescriptor);
            boolean isRPTarget = RPHelper.isRPTarget(volumeDescriptor);
            boolean extraParamsGathered = false;

            if (volumeDescriptor.getCapabilitiesValues() != null) {
                maxNumberOfSnapShots = volumeDescriptor.getCapabilitiesValues().getRPMaxSnaps();
            }

            // Set up the source and target volumes in their respective replication sets
            if (isRPSource || isRPTarget) {
                // Gather the extra params we need (once is sufficient)
                if (isRPSource && !extraParamsGathered) {
                    project = _dbClient.queryObject(Project.class, volume.getProject());
                    cg = _dbClient.queryObject(BlockConsistencyGroup.class,
                            volumeDescriptor.getCapabilitiesValues().getBlockConsistencyGroup());
                    cgName = cg.getCgNameOnStorageSystem(rpSystem.getId());
                    if (cgName == null) {
                        cgName = CG_NAME_PREFIX + cg.getLabel();
                    }
                    copyMode = volumeDescriptor.getCapabilitiesValues().getRpCopyMode();
                    rpoType = volumeDescriptor.getCapabilitiesValues().getRpRpoType();
                    rpoValue = volumeDescriptor.getCapabilitiesValues().getRpRpoValue();

                    // Flag so we only grab this information once
                    extraParamsGathered = true;
                }

                if (isMetroPoint && isRPSource) {
                    // we need to handle metropoint request a bit differently.
                    // since the same metro volume will be part of 2 (production) copies in the replication set,
                    // we need to fetch the correct internal site names and other site related parameters from the
                    // backing volume.
                    StringSet backingVolumes = volume.getAssociatedVolumes();
                    if (null == backingVolumes || backingVolumes.isEmpty()) {
                        _log.error("VPLEX volume {} has no backend volumes.", volume.forDisplay());
                        throw InternalServerErrorException.internalServerErrors.noAssociatedVolumesForVPLEXVolume(volume.forDisplay());
                    }
                    for (String backingVolumeStr : backingVolumes) {
                        Volume backingVolume = _dbClient.queryObject(Volume.class, URI.create(backingVolumeStr));
                        CreateVolumeParams volumeParams = populateVolumeParams(volume.getId(), volume.getStorageController(),
                                backingVolume.getVirtualArray(), backingVolume.getInternalSiteName(), true, backingVolume.getRpCopyName(),
                                RPHelper.getRPWWn(volume.getId(), _dbClient), maxNumberOfSnapShots);
                        _log.info(String.format("Creating RSet Param for MetroPoint RP PROD - VOLUME: [%s] Name: [%s]",
                                backingVolume.getLabel(), volume.getRSetName()));
                        populateRsetsMap(rsetParamsMap, volumeParams, volume);
                        productionCopies.add(backingVolume.getRpCopyName());
                    }
                } else {
                    CreateVolumeParams volumeParams = populateVolumeParams(volume.getId(), volume.getStorageController(),
                            volume.getVirtualArray(), volume.getInternalSiteName(), isRPSource, volume.getRpCopyName(),
                            RPHelper.getRPWWn(volume.getId(), _dbClient), maxNumberOfSnapShots);
                    String type = isRPSource ? "PROD" : "TARGET";
                    _log.info(String.format("Creating RSet Param for RP %s - VOLUME: [%s] Name: [%s]", type, volume.getLabel(),
                            volume.getRSetName()));
                    populateRsetsMap(rsetParamsMap, volumeParams, volume);
                    if (isRPSource) {
                        productionCopies.add(volume.getRpCopyName());
                    }
                }
            }

            // Set up the journal volumes in the copy objects
            if (volumeDescriptor.getType().equals(VolumeDescriptor.Type.RP_JOURNAL)
                    || volumeDescriptor.getType().equals(VolumeDescriptor.Type.RP_VPLEX_VIRT_JOURNAL)) {
                if (cgName == null) {
                    project = _dbClient.queryObject(Project.class, volume.getProject());
                    cg = _dbClient.queryObject(BlockConsistencyGroup.class,
                            volumeDescriptor.getCapabilitiesValues().getBlockConsistencyGroup());
                    cgName = cg.getCgNameOnStorageSystem(rpSystem.getId());
                    if (cgName == null) {
                        cgName = CG_NAME_PREFIX + cg.getLabel();
                    }
                }
                CreateVolumeParams volumeParams = populateVolumeParams(volume.getId(), volume.getStorageController(),
                        volume.getVirtualArray(), volume.getInternalSiteName(), RPHelper.isProductionJournal(productionCopies, volume),
                        volume.getRpCopyName(), RPHelper.getRPWWn(volume.getId(), _dbClient), maxNumberOfSnapShots);
                String key = volume.getRpCopyName();
                _log.info(String.format("Creating Copy Param for RP JOURNAL: VOLUME - [%s] Name: [%s]", volume.getLabel(), key));
                if (copyParamsMap.containsKey(key)) {
                    copyParamsMap.get(key).getJournals().add(volumeParams);
                } else {
                    CreateCopyParams copyParams = new CreateCopyParams();
                    copyParams.setName(key);
                    copyParams.setJournals(new ArrayList<CreateVolumeParams>());
                    copyParams.getJournals().add(volumeParams);
                    copyParamsMap.put(key, copyParams);
                }
            }
        }

        // Set up the CG Request
        CGRequestParams cgParams = new CGRequestParams();
        cgParams.setCopies(new ArrayList<CreateCopyParams>());
        cgParams.getCopies().addAll(copyParamsMap.values());
        cgParams.setRsets(new ArrayList<CreateRSetParams>());
        cgParams.getRsets().addAll(rsetParamsMap.values());
        cgParams.setCgName(cgName);
        cgParams.setCgUri(cg.getId());
        cgParams.setProject(project.getId());
        cgParams.setTenant(project.getTenantOrg().getURI());
        CGPolicyParams policyParams = new CGPolicyParams();
        policyParams.setCopyMode(copyMode);
        policyParams.setRpoType(rpoType);
        policyParams.setRpoValue(rpoValue);
        cgParams.setCgPolicy(policyParams);
        _log.info(String.format("CG Request param complete:%n %s", cgParams));
        return cgParams;
    }

    /**
     * Adds the volumes to the replication sets map.
     *
     * @param rsetParamsMap
     *            the replication sets map.
     * @param volumeParams
     *            the volume params.
     * @param volume
     *            the volume from which to pull the replication set name.
     */
    private void populateRsetsMap(Map<String, CreateRSetParams> rsetParamsMap, CreateVolumeParams volumeParams, Volume volume) {
        String key = volume.getRSetName();
        if (rsetParamsMap.containsKey(key)) {
            rsetParamsMap.get(key).getVolumes().add(volumeParams);
        } else {
            CreateRSetParams rsetParams = new CreateRSetParams();
            rsetParams.setName(key);
            rsetParams.setVolumes(new ArrayList<CreateVolumeParams>());
            rsetParams.getVolumes().add(volumeParams);
            rsetParamsMap.put(key, rsetParams);
        }
    }

    /**
     * Assemble the CreateVolumeParams object with the input arguments.
     * Written to keep the prepare code tidy.
     *
     * @param volumeId
     *            Volume URI
     * @param storageSystemId
     *            Storage system for this Volume
     * @param neighborhoodId
     *            Neighborhood for this volume
     * @param internalSiteName
     *            internal site name
     * @param production
     *            Whether or not this volume is a production (source) volume at the time of the request
     * @param wwn
     *            volume wwn
     * @return volume parameter for RP
     */
    private CreateVolumeParams populateVolumeParams(URI volumeId, URI storageSystemId, URI neighborhoodId, String internalSiteName,
            boolean production, String rpCopyName, String wwn, int maxNumberOfSnapShots) {
        CreateVolumeParams volumeParams = new CreateVolumeParams();
        volumeParams.setVirtualArray(neighborhoodId);
        volumeParams.setProduction(production);
        volumeParams.setInternalSiteName(internalSiteName);
        volumeParams.setStorageSystem(storageSystemId);
        volumeParams.setVolumeURI(volumeId);
        volumeParams.setRpCopyName(rpCopyName);
        volumeParams.setWwn(wwn);
        volumeParams.setMaxNumberOfSnapShots(maxNumberOfSnapShots);
        return volumeParams;
    }

    /**
     * @param workflow
     * @param volumeDescriptorsTypeFilter
     * @param waitFor
     * @param volumeDescriptors
     * @param params
     * @param rpSystem
     * @param taskId
     * @throws RecoverPointException
     * @throws ControllerException
     * @throws DeviceControllerException
     */
    private void addExportVolumesSteps(Workflow workflow, List<VolumeDescriptor> volumeDescriptors, String waitFor,
            ProtectionSystem rpSystem, String taskId) throws InternalException {

        // The steps for RP exportGroup creation and the actual export orchestrations are separated into 2 steps.
        // The main reason for doing this is to aid in rollback and delete the export group artifacts created by RP
        // after the
        // actual export rollbacks have happened.
        String stepId = workflow.createStepId();
        Workflow.Method rpExportOrchestrationExecuteMethod = new Workflow.Method(METHOD_RP_EXPORT_ORCHESTRATE_STEP);
        Workflow.Method rpExportOrchestrationExecuteRollbackMethod = new Workflow.Method(METHOD_RP_EXPORT_ORCHESTRATE_ROLLBACK_STEP);

        workflow.createStep(STEP_RP_EXPORT_ORCHESTRATION, "Create RP Export group orchestration subtask for RP CG", waitFor,
                rpSystem.getId(), rpSystem.getSystemType(), false, this.getClass(), rpExportOrchestrationExecuteMethod,
                rpExportOrchestrationExecuteRollbackMethod, false, stepId);

        // This step creates a sub-workflow to do the orchestration. The rollback for this step calls a
        // workflow facility WorkflowService.rollbackChildWorkflow, which will roll back the entire
        // orchestration sub-workflow. The stepId of the orchestration create step must be passed to
        // the rollback step so that rollbackChildWorkflow can locate the correct child workflow.
        stepId = workflow.createStepId();
        Workflow.Method exportOrchestrationExecuteMethod = new Workflow.Method(METHOD_EXPORT_ORCHESTRATE_STEP, volumeDescriptors,
                rpSystem.getId());

        Workflow.Method exportOrchestrationExecutionRollbackMethod = new Workflow.Method(METHOD_EXPORT_ORCHESTRATE_ROLLBACK_STEP,
                workflow.getWorkflowURI(), stepId);

        workflow.createStep(STEP_EXPORT_ORCHESTRATION, "Create export group orchestration subtask for RP CG", STEP_RP_EXPORT_ORCHESTRATION,
                rpSystem.getId(), rpSystem.getSystemType(), false, this.getClass(), exportOrchestrationExecuteMethod,
                exportOrchestrationExecutionRollbackMethod, false, stepId);
    }

    /**
     * Workflow step method for rolling back the ExportOrchestration sub-workflow steps.
     *
     * @param parentWorkflow
     *            -- the URI of the parent Workflow, which is used to locate the sub-workflow
     * @param exportOrchestrationStepId
     *            -- the Step id of the of the step that creates the Export Orchestration sub-workflow.
     * @param token
     *            the task -- the step id for the rollback step
     * @return
     * @throws WorkflowException
     */
    public boolean exportOrchestrationRollbackSteps(URI parentWorkflow, String exportOrchestrationStepId, String token)
            throws WorkflowException {
        // The workflow service now provides a rollback facility for a child workflow. It rolls back every step in an
        // already
        // (successfully) completed child workflow. The child workflow is located by the parentWorkflow URI and
        // exportOrchestrationStepId.
        _workflowService.rollbackChildWorkflow(parentWorkflow, exportOrchestrationStepId, token);

        return true;
    }

    /**
     * RP export group orchestration steps.
     * Currently this is a dummy no-op method and all the RP export group assembly are done in the actual export
     * orchestration method.
     * The main reason to have this method is to make sure the roll back of RP export groups happen after the actual
     * export rollbacks.
     *
     * @param stepId
     *            - Operation's step ID
     * @return - Always returns true
     */
    public boolean rpExportOrchestrationSteps(String stepId) {
        WorkflowStepCompleter.stepSucceded(stepId);
        _log.info("Completed rpExportOrchestrationSteps");
        return true;
    }

    /**
     * RP Export group rollback orchestration steps
     *
     * @param stepId
     *            - Operation's step ID
     * @return - True on successful rollback, false otherwise
     */
    public boolean rpExportOrchestrationRollbackSteps(String stepId) {
        _log.info("Executing rpExportOrchestrationRollbackSteps");
        WorkflowStepCompleter.stepExecuting(stepId);
        try {
            rpExportGroupRollback();
            WorkflowStepCompleter.stepSucceded(stepId);
            _log.info("Completed rpExportOrchestrationRollbackSteps");

        } catch (Exception e) {
            stepFailed(stepId, "rpExportOrchestrationRollbackSteps");
            _log.info("Failed rpExportOrchestrationRollbackSteps");

        }
        return true;
    }

    /**
     * @param volumeDescriptors
     *            - Volume descriptors
     * @param rpSystemId
     *            - RP system
     * @param taskId
     *            - task ID
     * @return - True on success, false otherwise
     * @throws InternalException
     */
    public boolean exportOrchestrationSteps(List<VolumeDescriptor> volumeDescriptors, URI rpSystemId, String taskId)
            throws InternalException {
        List<URI> volUris = VolumeDescriptor.getVolumeURIs(volumeDescriptors);
        RPCGExportOrchestrationCompleter completer = new RPCGExportOrchestrationCompleter(volUris, taskId);
        Workflow workflow = null;
        boolean lockException = false;
        Map<URI, Set<URI>> exportGroupVolumesAdded = new HashMap<URI, Set<URI>>();
        exportGroupsCreated = new ArrayList<URI>();
        final String COMPUTE_RESOURCE_CLUSTER = "cluster";
        try {
            final String workflowKey = "rpExportOrchestration";
            if (!WorkflowService.getInstance().hasWorkflowBeenCreated(taskId, workflowKey)) {
                // Generate the Workflow.
                workflow = _workflowService.getNewWorkflow(this, EXPORT_ORCHESTRATOR_WF_NAME, true, taskId);

                String waitFor = null; // the wait for key returned by previous call

                ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, rpSystemId);

                // Get the CG Params based on the volume descriptors
                CGRequestParams params = this.getCGRequestParams(volumeDescriptors, rpSystem);
                updateCGParams(params);

                _log.info("Start adding RP Export Volumes steps....");

                // Get the RP Exports from the CGRequestParams object
                Collection<RPExport> rpExports = generateStorageSystemExportMaps(params, volumeDescriptors);

                Map<String, Set<URI>> rpSiteInitiatorsMap = getRPSiteInitiators(rpSystem, rpExports);

                // Acquire all the RP lock keys needed for export before we start assembling the export groups.
                acquireRPLockKeysForExport(taskId, rpExports, rpSiteInitiatorsMap);

                // For each RP Export, create a workflow to either add the volumes to an existing export group
                // or create a new one.
                for (RPExport rpExport : rpExports) {
                    URI storageSystemURI = rpExport.getStorageSystem();
                    String internalSiteName = rpExport.getRpSite();
                    URI varrayURI = rpExport.getVarray();
                    List<URI> volumes = rpExport.getVolumes();

                    List<URI> initiatorSet = new ArrayList<URI>();

                    String rpSiteName = (rpSystem.getRpSiteNames() != null) ? rpSystem.getRpSiteNames().get(internalSiteName)
                            : internalSiteName;

                    StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemURI);

                    VirtualArray varray = _dbClient.queryObject(VirtualArray.class, varrayURI);

                    _log.info("--------------------");
                    _log.info(String.format("RP Export: StorageSystem = [%s] RPSite = [%s] VirtualArray = [%s]", storageSystem.getLabel(),
                            rpSiteName, varray.getLabel()));

                    boolean isJournalExport = rpExport.getIsJournalExport();
                    String exportGroupGeneratedName = RPHelper.generateExportGroupName(rpSystem, storageSystem, internalSiteName, varray,
                            isJournalExport);
                    // Setup the export group - we may or may not need to create it, but we need to have everything ready in case we do
                    ExportGroup exportGroup = RPHelper.createRPExportGroup(exportGroupGeneratedName, varray,
                            _dbClient.queryObject(Project.class, params.getProject()), 0, isJournalExport);

                    // Get the initiators of the RP Cluster (all of the RPAs on one side of a configuration)
                    Map<String, Map<String, String>> rpaWWNs = RPHelper.getRecoverPointClient(rpSystem).getInitiatorWWNs(internalSiteName);

                    if (rpaWWNs == null || rpaWWNs.isEmpty()) {
                        throw DeviceControllerExceptions.recoverpoint.noInitiatorsFoundOnRPAs();
                    }

                    // Convert to initiator object
                    List<Initiator> initiators = new ArrayList<Initiator>();
                    for (String rpaId : rpaWWNs.keySet()) {
                        for (Map.Entry<String, String> rpaWWN : rpaWWNs.get(rpaId).entrySet()) {
                            Initiator initiator = ExportUtils.getInitiator(rpaWWN.getKey(), _dbClient);
                            initiators.add(initiator);
                        }
                    }

                    // We need to find and distill only those RP initiators that correspond to the network of the
                    // storage
                    // system and
                    // that network has front end port from the storage system.
                    // In certain lab environments, its quite possible that there are 2 networks one for the storage
                    // system
                    // FE ports and one for
                    // the BE ports.
                    // In such configs, RP initiators will be spread across those 2 networks. RP controller does not
                    // care
                    // about storage system
                    // back-end ports, so
                    // we will ignore those initiators that are connected to a network that has only storage system back
                    // end
                    // port connectivity.
                    Map<URI, Set<Initiator>> rpNetworkToInitiatorsMap = new HashMap<URI, Set<Initiator>>();
                    Set<URI> rpSiteInitiatorUris = rpSiteInitiatorsMap.get(internalSiteName);
                    if (rpSiteInitiatorUris != null) {
                        for (URI rpSiteInitiatorUri : rpSiteInitiatorUris) {
                            Initiator rpSiteInitiator = _dbClient.queryObject(Initiator.class, rpSiteInitiatorUri);
                            URI rpInitiatorNetworkURI = getInitiatorNetwork(exportGroup, rpSiteInitiator);
                            if (rpInitiatorNetworkURI != null) {
                                if (rpNetworkToInitiatorsMap.get(rpInitiatorNetworkURI) == null) {
                                    rpNetworkToInitiatorsMap.put(rpInitiatorNetworkURI, new HashSet<Initiator>());
                                }
                                rpNetworkToInitiatorsMap.get(rpInitiatorNetworkURI).add(rpSiteInitiator);
                                _log.info(String.format("RP Initiator [%s] found on network: [%s]", rpSiteInitiator.getInitiatorPort(),
                                        rpInitiatorNetworkURI.toASCIIString()));
                            } else {
                                _log.info(String.format("RP Initiator [%s] was not found on any network. Excluding from automated exports",
                                        rpSiteInitiator.getInitiatorPort()));
                            }
                        }
                    }

                    // Compute numPaths. This is how its done:
                    // We know the RP site and the Network/TransportZone it is on.
                    // Determine all the storage ports for the storage array for all the networks they are on.
                    // Next, if we find the network for the RP site in the above list, return all the storage ports
                    // corresponding to that.
                    // For RP we will try and use as many Storage ports as possible.
                    Map<URI, List<StoragePort>> initiatorPortMap = getInitiatorPortsForArray(rpNetworkToInitiatorsMap, storageSystemURI,
                            varrayURI, rpSiteName);

                    for (URI networkURI : initiatorPortMap.keySet()) {
                        for (StoragePort storagePort : initiatorPortMap.get(networkURI)) {
                            _log.info(String.format("Network : [%s] - Port : [%s]", networkURI.toString(), storagePort.getLabel()));
                        }
                    }

                    int numPaths = computeNumPaths(initiatorPortMap, varrayURI, storageSystem);
                    _log.info("Total paths = " + numPaths);

                    // Stems from above comment where we distill the RP network and the initiators in that network.
                    List<Initiator> initiatorList = new ArrayList<Initiator>();
                    for (URI rpNetworkURI : rpNetworkToInitiatorsMap.keySet()) {
                        if (initiatorPortMap.containsKey(rpNetworkURI)) {
                            initiatorList.addAll(rpNetworkToInitiatorsMap.get(rpNetworkURI));
                        }
                    }

                    for (Initiator initiator : initiatorList) {
                        initiatorSet.add(initiator.getId());
                    }

                    // See if the export group already exists
                    ExportGroup exportGroupInDB = exportGroupExistsInDB(exportGroup);
                    boolean addExportGroupToDB = false;
                    if (exportGroupInDB != null) {
                        exportGroup = exportGroupInDB;
                        // If the export already exists, check to see if any of the volumes have already been exported.
                        // No
                        // need to
                        // re-export volumes.
                        List<URI> volumesToRemove = new ArrayList<URI>();
                        for (URI volumeURI : volumes) {
                            if (exportGroup.getVolumes() != null && !exportGroup.getVolumes().isEmpty()
                                    && exportGroup.getVolumes().containsKey(volumeURI.toString())) {
                                _log.info(String.format(
                                        "Volume [%s] already exported to export group [%s], " + "it will be not be re-exported",
                                        volumeURI.toString(), exportGroup.getGeneratedName()));
                                volumesToRemove.add(volumeURI);
                            }
                        }

                        // Remove volumes if they have already been exported
                        if (!volumesToRemove.isEmpty()) {
                            volumes.removeAll(volumesToRemove);
                        }

                        // If there are no more volumes to export, skip this one and continue,
                        // nothing else needs to be done here.
                        if (volumes.isEmpty()) {
                            _log.info(String.format("No volumes needed to be exported to export group [%s], continue",
                                    exportGroup.getGeneratedName()));
                            continue;
                        }
                    } else {
                        addExportGroupToDB = true;
                    }

                    // Add volumes to the export group
                    Map<URI, Integer> volumesToAdd = new HashMap<URI, Integer>();
                    for (URI volumeID : volumes) {
                        exportGroup.addVolume(volumeID, ExportGroup.LUN_UNASSIGNED);
                        volumesToAdd.put(volumeID, ExportGroup.LUN_UNASSIGNED);
                    }

                    // Keep track of volumes added to export group
                    if (!volumesToAdd.isEmpty()) {
                        exportGroupVolumesAdded.put(exportGroup.getId(), volumesToAdd.keySet());
                    }

                    // Update Host/Cluster export information if the source volume is exported information on the Source
                    // volume
                    if (rpExport.getComputeResource() != null) {
                        URI computeResource = rpExport.getComputeResource();
                        _log.info(String.format("RP Export: ComputeResource : %s", computeResource.toString()));

                        if (computeResource.toString().toLowerCase().contains(COMPUTE_RESOURCE_CLUSTER)) {
                            Cluster cluster = _dbClient.queryObject(Cluster.class, computeResource);
                            exportGroup.addCluster(cluster);
                        } else {
                            Host host = _dbClient.queryObject(Host.class, rpExport.getComputeResource());
                            exportGroup.addHost(host);
                        }
                    }

                    // Persist the export group
                    if (addExportGroupToDB) {
                        exportGroup.addInitiators(initiatorSet);
                        exportGroup.setNumPaths(numPaths);
                        _dbClient.createObject(exportGroup);
                        // Keep track of newly created EGs in case of rollback
                        exportGroupsCreated.add(exportGroup.getId());
                    } else {
                        _dbClient.updateObject(exportGroup);
                    }

                    // If the export group already exists, add the volumes to it, otherwise create a brand new
                    // export group.
                    StringBuilder buffer = new StringBuilder();
                    buffer.append(String.format(DASHED_NEWLINE));
                    if (!addExportGroupToDB) {

                        buffer.append(String.format(
                                "Adding volumes to existing Export Group for Storage System [%s], RP Site [%s], Virtual Array [%s]%n",
                                storageSystem.getLabel(), rpSiteName, varray.getLabel()));
                        buffer.append(String.format("Export Group name is : [%s]%n", exportGroup.getGeneratedName()));
                        buffer.append(String.format("Export Group will have these volumes added: [%s]%n", Joiner.on(',').join(volumes)));
                        buffer.append(String.format(DASHED_NEWLINE));
                        _log.info(buffer.toString());

                        waitFor = _exportWfUtils.generateExportGroupAddVolumes(workflow, STEP_EXPORT_GROUP, waitFor, storageSystemURI,
                                exportGroup.getId(), volumesToAdd);

                        _log.info("Added Export Group add volumes step in workflow");
                    } else {
                        buffer.append(String.format(
                                "Creating new Export Group for Storage System [%s], RP Site [%s], Virtual Array [%s]%n",
                                storageSystem.getLabel(), rpSiteName, varray.getLabel()));
                        buffer.append(String.format("Export Group name is: [%s]%n", exportGroup.getGeneratedName()));
                        buffer.append(String.format("Export Group will have these initiators: [%s]%n", Joiner.on(',').join(initiatorSet)));
                        buffer.append(String.format("Export Group will have these volumes added: [%s]%n", Joiner.on(',').join(volumes)));
                        buffer.append(String.format(DASHED_NEWLINE));
                        _log.info(buffer.toString());

                        String exportStep = workflow.createStepId();
                        initTaskStatus(exportGroup, exportStep, Operation.Status.pending, "create export");

                        waitFor = _exportWfUtils.generateExportGroupCreateWorkflow(workflow, STEP_EXPORT_GROUP, waitFor, storageSystemURI,
                                exportGroup.getId(), volumesToAdd, initiatorSet);

                        _log.info("Added Export Group create step in workflow. New Export Group Id: " + exportGroup.getId());
                    }
                }

                String successMessage = "Export orchestration completed successfully";

                // Finish up and execute the plan.
                // The Workflow will handle the TaskCompleter
                Object[] callbackArgs = new Object[] { volUris };
                workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
                // Mark this workflow as created/executed so we don't do it again on retry/resume
                WorkflowService.getInstance().markWorkflowBeenCreated(taskId, workflowKey);
            }
        } catch (LockRetryException ex) {
            /**
             * Added this catch block to mark the current workflow as completed so that lock retry will not get exception while creating new
             * workflow using the same taskid.
             */
            _log.warn(String.format("Lock retry exception key: %s remaining time %d", ex.getLockIdentifier(),
                    ex.getRemainingWaitTimeSeconds()));
            if (workflow != null && !NullColumnValueGetter.isNullURI(workflow.getWorkflowURI())
                    && workflow.getWorkflowState() == WorkflowState.CREATED) {
                com.emc.storageos.db.client.model.Workflow wf = _dbClient.queryObject(com.emc.storageos.db.client.model.Workflow.class,
                        workflow.getWorkflowURI());
                if (!wf.getCompleted()) {
                    _log.error("Marking the status to completed for the newly created workflow {}", wf.getId());
                    wf.setCompleted(true);
                    _dbClient.updateObject(wf);
                }
            }
            throw ex;
        } catch (Exception ex) {
            _log.error("Could not create volumes: " + volUris, ex);

            // Rollback ViPR level RP export group changes
            rpExportGroupRollback();

            if (workflow != null) {
                _workflowService.releaseAllWorkflowLocks(workflow);
            }
            String opName = ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME.getName();
            ServiceError serviceError = null;
            if (lockException) {
                serviceError = DeviceControllerException.errors.createVolumesAborted(volUris.toString(), ex);
            } else {
                serviceError = DeviceControllerException.errors.createVolumesFailed(volUris.toString(), opName, ex);
            }
            completer.error(_dbClient, _locker, serviceError);
            return false;
        }

        _log.info("End adding RP Export Volumes steps.");
        return true;
    }

    /**
     * ViPR level deletion/update of any RP Export Groups that are newly created. If they are pre-existing,
     * then we simply want to remove any volume references that had been added to those Export Groups.
     */
    private void rpExportGroupRollback() {
        // Rollback any newly created export groups
        if (exportGroupsCreated != null && !exportGroupsCreated.isEmpty()) {
            for (URI exportGroupURI : exportGroupsCreated) {
                ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
                if (exportGroup != null && !exportGroup.getInactive()) {
                    _log.info(String.format("Marking ExportGroup [%s](%s) for deletion.", exportGroup.getLabel(), exportGroup.getId()));
                    _dbClient.markForDeletion(exportGroup);
                }
            }
        }

        // Rollback any volumes that have been added/persisted to existing export groups
        if (exportGroupVolumesAdded != null && !exportGroupVolumesAdded.isEmpty()) {
            for (Entry<URI, Set<URI>> entry : exportGroupVolumesAdded.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    if (exportGroupsCreated != null && !exportGroupsCreated.isEmpty()) {
                        if (exportGroupsCreated.contains(entry.getKey())) {
                            // We already marked this EG for deletion, so keep going.
                            continue;
                        }
                    }
                    ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, entry.getKey());
                    _log.info(String.format("Removing volumes (%s) from ExportGroup (%s).", entry.getValue(), entry.getKey()));
                    exportGroup.removeVolumes(new ArrayList<URI>(entry.getValue()));
                    _dbClient.updateObject(exportGroup);
                }
            }
        }
    }

    /**
     * This method acquires all the RP locks necessary for the operation based on the RPExport information.
     *
     * @param taskId
     *            Task Id
     * @param lockException
     *            lockException
     * @param rpExports
     *            RPExports information
     * @param rpSiteInitiatorsMap
     *            RP site initiators map
     */
    private void acquireRPLockKeysForExport(String taskId, Collection<RPExport> rpExports, Map<String, Set<URI>> rpSiteInitiatorsMap) {
        _log.info("Start : Acquiring RP lock keys for export");
        List<String> lockKeys = new ArrayList<String>();

        for (RPExport rpExport : rpExports) {
            Set<URI> rpSiteInitiatorUris = rpSiteInitiatorsMap.get(rpExport.getRpSite());
            lockKeys.addAll(
                    ControllerLockingUtil.getStorageLockKeysForRecoverPoint(_dbClient, rpSiteInitiatorUris, rpExport.getStorageSystem()));
        }

        boolean acquiredLocks = _exportWfUtils.getWorkflowService().acquireWorkflowStepLocks(taskId, lockKeys,
                LockTimeoutValue.get(LockType.RP_EXPORT));
        if (!acquiredLocks) {
            throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(), "ExportOrchestrationSteps: RP Export");
        }
        for (String lockKey : lockKeys) {
            _log.info("Acquired lock : " + lockKey);
        }
        _log.info("Done : Acquiring RP lock keys for export");
    }

    /**
     * Build a map of initiators for each RP site/cluster in the export request.
     *
     * @param rpSystem
     *            RP system
     * @param rpExports
     *            RP Export objects
     * @return Map of RP site to its initiators
     */
    private Map<String, Set<URI>> getRPSiteInitiators(ProtectionSystem rpSystem, Collection<RPExport> rpExports) {
        Map<String, Set<URI>> rpSiteInitiators = new HashMap<String, Set<URI>>();
        // Get the initiators of the RP Cluster (all of the RPAs on one side of a configuration)

        for (RPExport rpExport : rpExports) {

            String rpSiteName = rpExport.getRpSite();
            Map<String, Map<String, String>> rpaWWNs = RPHelper.getRecoverPointClient(rpSystem).getInitiatorWWNs(rpSiteName);

            if (rpaWWNs == null || rpaWWNs.isEmpty()) {
                throw DeviceControllerExceptions.recoverpoint.noInitiatorsFoundOnRPAs();
            }

            // Convert to initiator object
            for (String rpaId : rpaWWNs.keySet()) {
                for (Map.Entry<String, String> rpaWWN : rpaWWNs.get(rpaId).entrySet()) {
                    Initiator initiator = ExportUtils.getInitiator(rpaWWN.getKey(), _dbClient);
                    if (initiator == null) {
                        _log.error(
                                String.format("Could not find initiator for %s on RP ID %s, site %s", rpaWWN.getKey(), rpaId, rpSiteName));
                        throw DeviceControllerExceptions.recoverpoint.noInitiatorsFoundOnRPAs();
                    }

                    if (!rpSiteInitiators.containsKey(rpSiteName)) {
                        rpSiteInitiators.put(rpSiteName, new HashSet<URI>());
                    }

                    _log.info(String.format("Adding initiator %s, port %s on RP ID %s, site %s", initiator.getId(), rpaWWN.getKey(), rpaId,
                            rpSiteName));
                    rpSiteInitiators.get(rpSiteName).add(initiator.getId());
                }
            }
        }
        return rpSiteInitiators;
    }

    @SuppressWarnings("serial")
    private static class WorkflowCallback implements Workflow.WorkflowCallbackHandler, Serializable {
        @SuppressWarnings("unchecked")
        @Override
        public void workflowComplete(Workflow workflow, Object[] args) throws WorkflowException {
            List<URI> volumes = (List<URI>) args[0];
            String msg = BlockDeviceController.getVolumesMsg(_dbClient, volumes);
            _log.info("Processed volumes:\n" + msg);
        }
    }

    /**
     * This operation will add additional journal volumes to a recoverpoint consistency group
     *
     * @param rpSystemId
     *            - recoverpoint system
     * @param volumeDescriptors
     *            - journal volumes to add
     * @param taskId
     *            - task tracking the operation
     * @return boolean indicating the result of the operation
     */
    public boolean addJournalStep(URI rpSystemId, List<VolumeDescriptor> volumeDescriptors, String taskId) {
        WorkflowStepCompleter.stepExecuting(taskId);
        if (volumeDescriptors.isEmpty()) {
            stepFailed(taskId, "addJournalStep");
        }
        ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, rpSystemId);
        RecoverPointClient rp = RPHelper.getRecoverPointClient(rpSystem);
        CGRequestParams cgParams = this.getCGRequestParams(volumeDescriptors, rpSystem);
        updateCGParams(cgParams);

        try {
            rp.addJournalVolumesToCG(cgParams, volumeDescriptors.get(0).getCapabilitiesValues().getRPCopyType());
            WorkflowStepCompleter.stepSucceded(taskId);
        } catch (Exception e) {
            stepFailed(taskId, "addJournalStep");
        }
        return true;
    }

    /**
     * Recoverpoint specific workflow method for creating an Export Group
     * NOTE: Workflow.Method requires that opId is added as a param.
     *
     * @param opId
     */
    public boolean createExportGroupStep(String opId) {
        // This is currently a dummy workflow step. If there are any specific things
        // that need to be added for RP Export Group create, they can be added here.
        WorkflowStepCompleter.stepSucceded(opId);
        return true;
    }

    /**
     * Recoverpoint specific rollback for creating an Export Group
     * NOTE: Workflow.Method requires that opId is added as a param.
     *
     * @param exportGroupURI
     * @param opId
     * @throws ControllerException
     */
    public void createExportGroupRollbackStep(URI exportGroupURI, String opId) throws ControllerException {
        try {
            _log.info(String.format("rollbackCreateRPExportGroup start - Export Group: [%s]", exportGroupURI));

            WorkflowStepCompleter.stepExecuting(opId);

            // If there was a rollback triggered, we need to cleanup the Export Group we created.
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            exportGroup.setInactive(true);
            _dbClient.updateObject(exportGroup);

            _log.info(String.format("Rollback complete for Export Group: [%s]", exportGroupURI));

            WorkflowStepCompleter.stepSucceded(opId);

            _log.info(String.format("rollbackCreateRPExportGroup end - Export Group: [%s]", exportGroupURI));
        } catch (InternalException e) {
            _log.error(String.format("rollbackCreateRPExportGroup Failed - Export Group: [%s]", exportGroupURI));
            WorkflowStepCompleter.stepFailed(opId, e);
        } catch (Exception e) {
            _log.error(String.format("rollbackCreateRPExportGroup Failed - Export Group: [%s]", exportGroupURI));
            WorkflowStepCompleter.stepFailed(opId, DeviceControllerException.errors.jobFailed(e));
        }
    }

    /**
     * Method that adds the step to the workflow that creates the CG.
     *
     * @param workflow
     * @param recommendation
     * @param rpSystem
     * @param protectionSet
     * @throws InternalException
     * @return the step group
     */
    private String addCreateOrUpdateCGStep(Workflow workflow, List<VolumeDescriptor> volumeDescriptors, CGRequestParams cgParams,
            ProtectionSystem rpSystem, String taskId) throws InternalException {
        String stepId = workflow.createStepId();
        Workflow.Method cgCreationExecuteMethod = new Workflow.Method(METHOD_CG_CREATE_STEP, rpSystem.getId(), volumeDescriptors);
        Workflow.Method cgCreationExecutionRollbackMethod = new Workflow.Method(METHOD_CG_CREATE_ROLLBACK_STEP, rpSystem.getId(),
                volumeDescriptors);

        workflow.createStep(STEP_CG_CREATION, "Create consistency group subtask for RP CG: " + cgParams.getCgName(),
                STEP_EXPORT_ORCHESTRATION, rpSystem.getId(), rpSystem.getSystemType(), this.getClass(), cgCreationExecuteMethod,
                cgCreationExecutionRollbackMethod, stepId);

        return STEP_CG_CREATION;
    }

    /**
     * Method that adds the step to the workflow for adding a journal volume to a CG.
     *
     * @param workflow
     * @param recommendation
     * @param rpSystem
     * @throws InternalException
     * @return the step group
     */
    private String addAddJournalVolumesToCGStep(Workflow workflow, List<VolumeDescriptor> volumeDescriptors, CGRequestParams cgParams,
            ProtectionSystem rpSystem, String taskId) throws InternalException {
        String stepId = workflow.createStepId();
        Workflow.Method addJournalExecuteMethod = new Workflow.Method(METHOD_ADD_JOURNAL_STEP, rpSystem.getId(), volumeDescriptors);
        Workflow.Method addJournalExecutionRollbackMethod = new Workflow.Method(METHOD_ADD_JOURNAL_ROLLBACK_STEP, rpSystem.getId());

        workflow.createStep(STEP_ADD_JOURNAL_VOLUME,
                "Create add journal volume to consistency group subtask for RP CG: " + cgParams.getCgName(), STEP_EXPORT_ORCHESTRATION,
                rpSystem.getId(), rpSystem.getSystemType(), this.getClass(), addJournalExecuteMethod, addJournalExecutionRollbackMethod,
                stepId);

        return STEP_ADD_JOURNAL_VOLUME;
    }

    /**
     * Workflow step method for rolling back adding journal volumes to CG.
     *
     * @param rpSystem
     *            RP system
     * @param token
     *            the task
     * @return
     * @throws WorkflowException
     */
    public boolean addJournalRollbackStep(URI rpSystemId, String token) throws WorkflowException {
        // nothing to do for now.
        WorkflowStepCompleter.stepSucceded(token);
        return true;
    }

    /**
     * Workflow step method for creating/updating a consistency group.
     *
     * @param rpSystemId
     *            RP system Id
     * @param recommendation
     *            parameters needed to create the CG
     * @param token
     *            the task
     * @return
     * @throws InternalException
     */
    public boolean cgCreateStep(URI rpSystemId, List<VolumeDescriptor> volumeDescriptors, String token) throws InternalException {
        RecoverPointClient rp;
        CGRequestParams cgParams = null;
        boolean metropoint = false;
        boolean lockException = false;
        RPHelper.setLinkStateWaitTimeOut(_coordinator);
        try {
            // Get only the RP volumes from the descriptors.
            List<VolumeDescriptor> sourceVolumeDescriptors = VolumeDescriptor.filterByType(
                    volumeDescriptors, new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_SOURCE,
                            VolumeDescriptor.Type.RP_EXISTING_SOURCE, VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE },
                    new VolumeDescriptor.Type[] {});

            WorkflowStepCompleter.stepExecuting(token);
            ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, rpSystemId);
            URI cgId = volumeDescriptors.iterator().next().getCapabilitiesValues().getBlockConsistencyGroup();
            boolean attachAsClean = true;

            for (VolumeDescriptor sourceVolumedescriptor : sourceVolumeDescriptors) {
                Volume sourceVolume = _dbClient.queryObject(Volume.class, sourceVolumedescriptor.getVolumeURI());
                metropoint = RPHelper.isMetroPointVolume(_dbClient, sourceVolume);
                // if this is a change vpool, attachAsClean should be false so that source and target are synchronized
                if (VolumeDescriptor.Type.RP_EXISTING_SOURCE.equals(sourceVolumedescriptor.getType())) {
                    attachAsClean = false;
                }
            }

            // Build the CG Request params
            cgParams = getCGRequestParams(volumeDescriptors, rpSystem);
            updateCGParams(cgParams);

            // Validate the source/target volumes before creating a CG.
            validateCGVolumes(volumeDescriptors);

            rp = RPHelper.getRecoverPointClient(rpSystem);

            // Scan the rp sites for volume visibility
            rp.waitForVolumesToBeVisible(cgParams);
            
            // Before acquiring a lock on the CG we need to ensure that the 
            // CG is created. If it hasn't, then the first CGRequestParams
            // to be allowed to pass through needs to have the journals
            // defined.
            //
            // NOTE: The CG may not yet be created on the RP protection system and 
            // that's OK since this might be the first request going in.  
            waitForCGToBeCreated(cgId, cgParams);

            // lock around create and delete operations on the same CG
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getConsistencyGroupStorageKey(_dbClient, cgId, rpSystem.getId()));
            boolean lockAcquired = _workflowService.acquireWorkflowStepLocks(token, lockKeys, LockTimeoutValue.get(LockType.RP_CG));
            if (!lockAcquired) {
                lockException = true;
                throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(),
                        String.format("Create or add volumes to RP consistency group id: %s", cgId.toString()));
            }

            RecoverPointCGResponse response = null;
            // The CG already exists if it contains volumes and is of type RP
            _log.info("Submitting RP Request: " + cgParams);

            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgId);
            
            // Check to see if the CG has been created in ViPR and on the RP protection system
            boolean cgAlreadyExists = rpCGExists(cg, rp, cgParams.getCgName(), rpSystem.getId());
            
            if (cgAlreadyExists) {
                // cg exists in both the ViPR db and on the RP system
                _log.info(String.format("RP CG [%s] already exists, adding replication set(s) to it...", cgParams.getCgName()));                
                response = rp.addReplicationSetsToCG(cgParams, metropoint, attachAsClean);
            } else {
                _log.info(String.format("RP CG [%s] does not already exist, creating it now and adding replication set(s) to it...", cgParams.getCgName()));
                response = rp.createCG(cgParams, metropoint, attachAsClean);

                // "Turn-on" the consistency group
                cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgParams.getCgUri());
                cg.addSystemConsistencyGroup(rpSystemId.toString(), cgParams.getCgName());
                cg.addConsistencyGroupTypes(Types.RP.name());
            }

            // At this point, always clear the journal provisioning lock on the
            // CG for any concurrent orders that may come in.
            cg.setJournalProvisioningLock(0L);
            _dbClient.updateObject(cg);

            setVolumeConsistencyGroup(volumeDescriptors, cgParams.getCgUri());

            // If this was a vpool Update, now is a good time to update the vpool and Volume information
            if (VolumeDescriptor.getVirtualPoolChangeVolume(volumeDescriptors) != null) {
                Volume volume = _dbClient.queryObject(Volume.class, VolumeDescriptor.getVirtualPoolChangeVolume(volumeDescriptors));
                URI newVpoolURI = getVirtualPoolChangeNewVirtualPool(volumeDescriptors);
                volume.setVirtualPool(newVpoolURI);
                volume.setPersonality(Volume.PersonalityTypes.SOURCE.toString());
                volume.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                volume.setLinkStatus(Volume.LinkStatus.IN_SYNC.name());
                volume.setProtectionController(rpSystemId);
                _dbClient.updateObject(volume);

                // We might need to update the vpools of the backing volumes if this is an RP+VPLEX
                // or MetroPoint change vpool.
                VPlexUtil.updateVPlexBackingVolumeVpools(volume, newVpoolURI, _dbClient);

                // Record Audit operation. (virtualpool change only)
                AuditBlockUtil.auditBlock(_dbClient, OperationTypeEnum.CHANGE_VOLUME_VPOOL, true, AuditLogManager.AUDITOP_END, token);
            }

            // Create the ProtectionSet to contain the CG UID (which is truly unique to the protection system)
            if (response.getCgId() != null) {
                List<ProtectionSet> protectionSets = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, ProtectionSet.class,
                        AlternateIdConstraint.Factory.getConstraint(ProtectionSet.class, "protectionId", response.getCgId().toString()));
                ProtectionSet protectionSet = null;

                if (protectionSets.isEmpty()) {
                    // A protection set corresponding to the CG does not exist so we need to create one
                    protectionSet = createProtectionSet(rpSystem, cgParams, response.getCgId());
                } else {
                    // Update the existing protection set. We will only have 1 protection set
                    // get the first one.
                    protectionSet = protectionSets.get(0);
                    protectionSet = updateProtectionSet(protectionSet, cgParams);
                }
                _dbClient.updateObject(protectionSet);
            }

            // Set the CG last created time to now.
            rpSystem.setCgLastCreatedTime(Calendar.getInstance());
            _dbClient.updateObject(rpSystem);

            // Update the workflow state.
            WorkflowStepCompleter.stepSucceded(token);

            // collect and update the protection system statistics to account for
            // the newly created CG.
            _log.info("Collecting RP statistics post CG create.");
            collectRPStatistics(rpSystem);
        } catch (Exception e) {
            if (lockException) {
                List<URI> volUris = VolumeDescriptor.getVolumeURIs(volumeDescriptors);
                ServiceError serviceError = DeviceControllerException.errors.createVolumesAborted(volUris.toString(), e);
                doFailCgCreateStep(volumeDescriptors, cgParams, rpSystemId, token);
                stepFailed(token, serviceError, "cgCreateStep");
            } else {
                doFailCgCreateStep(volumeDescriptors, cgParams, rpSystemId, token);
                stepFailed(token, e, "cgCreateStep");
            }
            return false;
        }
        return true;
    }
    
    /**
     * Workflow step method for cg create rollback
     *
     * @param rpSystem
     *            RP system
     * @param params
     *            parameters needed to create the CG
     * @param token
     *            the task
     * @return
     * @throws WorkflowException
     */
    public boolean cgCreateRollbackStep(URI rpSystemId, List<VolumeDescriptor> volumeDescriptors, String token) throws WorkflowException {

        _log.info("Start cg create rollback step");
        WorkflowStepCompleter.stepExecuting(token);
        RPHelper.setLinkStateWaitTimeOut(_coordinator);
        // Get only the RP source volumes from the descriptors.
        List<VolumeDescriptor> sourceVolumeDescriptors = VolumeDescriptor
                .filterByType(
                        volumeDescriptors, new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_SOURCE,
                                VolumeDescriptor.Type.RP_EXISTING_SOURCE, VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE },
                        new VolumeDescriptor.Type[] {});

        // Get all journal volumes
        List<VolumeDescriptor> journalVolumeDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_JOURNAL, VolumeDescriptor.Type.RP_VPLEX_VIRT_JOURNAL },
                new VolumeDescriptor.Type[] {});

        if (sourceVolumeDescriptors == null || sourceVolumeDescriptors.isEmpty()) {
            WorkflowStepCompleter.stepSucceded(token);
            return true;
        }

        List<URI> volumeIDs = new ArrayList<URI>();
        for (VolumeDescriptor descriptor : sourceVolumeDescriptors) {
            volumeIDs.add(descriptor.getVolumeURI());
        }

        List<URI> journalVolumeIDs = new ArrayList<URI>();
        for (VolumeDescriptor journalDescriptor : journalVolumeDescriptors) {
            journalVolumeIDs.add(journalDescriptor.getVolumeURI());
        }

        return cgDeleteStep(rpSystemId, volumeIDs, journalVolumeIDs, token);
    }

    /**
     * Sets the volume consistency group
     *
     * @param volumeDescriptors
     * @param cgURI
     */
    private void setVolumeConsistencyGroup(List<VolumeDescriptor> volumeDescriptors, URI cgURI) {
        for (VolumeDescriptor volumeDescriptor : volumeDescriptors) {
            Volume volume = _dbClient.queryObject(Volume.class, volumeDescriptor.getVolumeURI());
            volume.setConsistencyGroup(cgURI);
            _dbClient.updateObject(volume);
        }
    }

    /**
     * Validates the source and target volumes to ensure the provisioned
     * sizes are all the same.
     *
     * @param volumeDescriptors
     *            the volumes to validate
     */
    private void validateCGVolumes(List<VolumeDescriptor> volumeDescriptors) {
        // Validate that the source and target volumes are the same size. If they are not
        // then CG creation or fail-over will fail.
        for (VolumeDescriptor volumeDescriptor : volumeDescriptors) {
            if (volumeDescriptor.getType().equals(VolumeDescriptor.Type.RP_SOURCE)
                    || volumeDescriptor.getType().equals(VolumeDescriptor.Type.RP_EXISTING_SOURCE)
                    || volumeDescriptor.getType().equals(VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE)) {
                // Find the Source volume from the descriptor
                Volume sourceVolume = _dbClient.queryObject(Volume.class, volumeDescriptor.getVolumeURI());
                StorageSystem sourceStorageSystem = _dbClient.queryObject(StorageSystem.class, sourceVolume.getStorageController());

                // Check all Target volumes of the Source to ensure that Source capacity < Target capacity.
                for (String targetId : sourceVolume.getRpTargets()) {
                    Volume targetVolume = _dbClient.queryObject(Volume.class, URI.create(targetId));
                    StorageSystem targetStorageSystem = _dbClient.queryObject(StorageSystem.class, targetVolume.getStorageController());

                    // target must be equal to or larger than the source
                    if (Long.compare(targetVolume.getProvisionedCapacity(), sourceVolume.getProvisionedCapacity()) < 0) {
                        _log.error(String.format(
                                "Source volume [%s - %s] has provisioned capacity of [%s] and Target volume [%s - %s] has provisioned capacity of [%s]. "
                                        + "Source capacity cannot be > Target capacity.",
                                sourceVolume.getLabel(), sourceVolume.getId(), sourceVolume.getProvisionedCapacity(),
                                targetVolume.getLabel(), targetVolume.getId(), targetVolume.getProvisionedCapacity()));
                        throw DeviceControllerExceptions.recoverpoint.cgCannotBeCreatedInvalidVolumeSizes(
                                sourceStorageSystem.getSystemType(),
                                String.valueOf(sourceVolume.getProvisionedCapacity()), targetStorageSystem.getSystemType(),
                                String.valueOf(targetVolume.getProvisionedCapacity()));
                    }
                }
            }
        }
    }

    /**
     * Helper method to retrieve the vpool change vpool hiding in the volume descriptors
     *
     * @param volumeDescriptors
     *            list of volumes
     * @return URI of the vpool change vpool
     */
    public URI getVirtualPoolChangeNewVirtualPool(List<VolumeDescriptor> volumeDescriptors) {
        if (volumeDescriptors != null) {
            for (VolumeDescriptor volumeDescriptor : volumeDescriptors) {
                if (volumeDescriptor.getParameters() != null) {
                    if ((URI) volumeDescriptor.getParameters().get(VolumeDescriptor.PARAM_VPOOL_CHANGE_NEW_VPOOL_ID) != null) {
                        return (URI) volumeDescriptor.getParameters().get(VolumeDescriptor.PARAM_VPOOL_CHANGE_NEW_VPOOL_ID);
                    }
                }
            }
        }
        return null;
    }

    /**
     * process failure of creating a cg step.
     *
     * @param volumeDescriptors
     *            volumes
     * @param cgParams
     *            cg parameters
     * @param protectionSetId
     *            protection set id
     * @param token
     *            task ID for audit
     * @param e
     *            exception
     * @param lockException
     * @throws InternalException
     */
    private void doFailCgCreateStep(List<VolumeDescriptor> volumeDescriptors, CGRequestParams cgParams, URI protectionSetId, String token)
            throws InternalException {
        // Record Audit operation. (vpool change only)
        if (VolumeDescriptor.getVirtualPoolChangeVolume(volumeDescriptors) != null) {
            AuditBlockUtil.auditBlock(_dbClient, OperationTypeEnum.CHANGE_VOLUME_VPOOL, true, AuditLogManager.AUDITOP_END, token);
        }
    }

    /**
     * Helper method that consolidates all of the volumes into storage systems to make the minimum amount of export
     * calls.
     *
     * @param volumeDescriptors
     *
     * @param recommendation
     */
    private Collection<RPExport> generateStorageSystemExportMaps(CGRequestParams cgParams, List<VolumeDescriptor> volumeDescriptors) {
        _log.info("Generate the storage system exports...START");
        Map<String, RPExport> rpExportMap = new HashMap<String, RPExport>();

        // First, iterate through source/target volumes (via the replication set). This will be slightly
        // different than the journals since we need to consider that we might have a MetroPoint source
        // volume.
        for (CreateRSetParams rset : cgParams.getRsets()) {
            _log.info("Replication Set: " + rset.getName());
            Set<CreateVolumeParams> createVolumeParams = new HashSet<CreateVolumeParams>();
            createVolumeParams.addAll(rset.getVolumes());
            List<URI> processedRsetVolumes = new ArrayList<URI>();
            for (CreateVolumeParams rsetVolume : createVolumeParams) {
                // MetroPoint RSets will have the Source volume listed twice:
                //
                // 1. Once for the Active Production Copy
                // 2. Once for the Standby Production Copy
                //
                // This is the same volume WWN but it is for two distinct RP Copies.
                //
                // We only need a single reference to the Source volume for export purposes
                // as we already make allowances in the below code for exporting this volume to
                // multiple VPLEX export groups (aka Storage Views).
                //
                // So if we have already created exports for this Source volume, we can skip
                // the second reference and continue processing.
                if (processedRsetVolumes.contains(rsetVolume.getVolumeURI())) {
                    continue;
                }
                processedRsetVolumes.add(rsetVolume.getVolumeURI());

                // Retrieve the volume
                Volume volume = _dbClient.queryObject(Volume.class, rsetVolume.getVolumeURI());

                _log.info(String.format("Generating Exports for %s volume [%s](%s)...", volume.getPersonality().toString(),
                        volume.getLabel(), volume.getId()));

                // List of volumes to export, normally just one volume will be added to this list unless
                // we have a MetroPoint config. In which case we would have two (each leg of the VPLEX).
                Set<Volume> volumes = new HashSet<Volume>();

                // Check to see if this is a SOURCE volume
                if (volume.checkPersonality(PersonalityTypes.SOURCE.toString())) {
                    // Check the vpool to ensure we're exporting the source volume to the correct storage system.
                    // In the case of MetroPoint, however, it could be a change vpool. In that case get the change
                    // vpool new vpool.
                    URI vpoolURI = null;
                    if (VolumeDescriptor.getVirtualPoolChangeVolume(volumeDescriptors) != null) {
                        vpoolURI = getVirtualPoolChangeNewVirtualPool(volumeDescriptors);
                    } else {
                        vpoolURI = volume.getVirtualPool();
                    }

                    VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, vpoolURI);

                    // In an RP+VPLEX distributed setup, the user can choose to protect only the HA side,
                    // so we would export only to the HA StorageView on the VPLEX.
                    boolean exportToHASideOnly = VirtualPool.isRPVPlexProtectHASide(vpool);

                    if (exportToHASideOnly || VirtualPool.vPoolSpecifiesMetroPoint(vpool)) {
                        _log.info("Export is for {}. Basing export(s) off backing VPLEX volumes for RP Source volume [{}].",
                                (exportToHASideOnly ? "RP+VPLEX distributed HA side only" : "MetroPoint"), volume.getLabel());
                        // If MetroPoint is enabled we need to create exports for each leg of the VPLEX.
                        // Get the associated volumes and add them to the list so we can create RPExports
                        // for each one.
                        StringSet backingVolumes = volume.getAssociatedVolumes();
                        if (null == backingVolumes || backingVolumes.isEmpty()) {
                            _log.error("VPLEX volume {} has no backend volumes.", volume.forDisplay());
                            throw InternalServerErrorException.internalServerErrors.noAssociatedVolumesForVPLEXVolume(volume.forDisplay());
                        }
                        for (String volumeId : backingVolumes) {
                            Volume vol = _dbClient.queryObject(Volume.class, URI.create(volumeId));

                            // Check to see if we only want to export to the HA side of the RP+VPLEX setup
                            if (exportToHASideOnly) {
                                if (!vol.getVirtualArray().toString().equals(vpool.getHaVarrayConnectedToRp())) {
                                    continue;
                                }
                            }
                            volumes.add(vol);
                        }
                    } else {
                        // Not RP+VPLEX distributed or MetroPoint, add the volume and continue on.
                        volumes.add(volume);
                    }
                } else {
                    // Not a SOURCE volume, add the volume and continue on.
                    volumes.add(volume);
                }

                for (Volume vol : volumes) {
                    URI storageSystem = rsetVolume.getStorageSystem();
                    String rpSiteName = vol.getInternalSiteName();
                    URI varray = vol.getVirtualArray();
                    // Intentionally want the ID of the parent volume, not the inner looping vol.
                    // This is because we could be trying to create exports for MetroPoint.
                    URI volumeId = volume.getId();

                    // Generate a unique key based on Storage System + Internal Site + Virtual Array
                    String key = storageSystem.toString() + rpSiteName + varray.toString();

                    // Try and get an existing rp export object from the map using the key
                    RPExport rpExport = rpExportMap.get(key);

                    // If it doesn't exist, create the entry and add it to the map with the key
                    if (rpExport == null) {
                        rpExport = new RPExport(storageSystem, rpSiteName, varray);
                        rpExportMap.put(key, rpExport);
                    }

                    // Add host information to the export if specified
                    if (vol.checkPersonality(Volume.PersonalityTypes.SOURCE.name())) {
                        for (VolumeDescriptor desc : volumeDescriptors) {
                            if (desc.getVolumeURI().equals(vol.getId())) {
                                if (!NullColumnValueGetter.isNullURI(desc.getComputeResource())) {
                                    _log.info("Add Host/Cluster information for source volume exports");
                                    rpExport.setComputeResource(desc.getComputeResource());
                                    break;
                                }
                            }
                        }
                    }
                    _log.info(String.format("Adding %s volume [%s](%s) to export: %s", volume.getPersonality().toString(),
                            volume.getLabel(), volume.getId(), rpExport.toString()));
                    rpExport.getVolumes().add(volumeId);
                }
            }
        }

        // Second, Iterate through the journal volumes (via the copies)
        // We first build all the source/target copy volumes and the then journals. The order is reversed from the
        // initial implementation.
        // This is because, if dedicated export groups for journals are not required then we can piggyback on the export
        // group created for
        // the
        // source/target for the journal of that copy.
        // If the VirtualArray for the journal on a copy is different than the VirtualArray for that copy, then a new
        // ExportGroup will be
        // created for the journal.
        for (CreateCopyParams copy : cgParams.getCopies()) {
            _log.info("Copy: " + copy.getName());
            for (CreateVolumeParams journalVolume : copy.getJournals()) {
                // Retrieve the volume
                Volume volume = _dbClient.queryObject(Volume.class, journalVolume.getVolumeURI());

                _log.info(String.format("Generating export for %s volume [%s](%s)...", volume.getPersonality().toString(),
                        volume.getLabel(), volume.getId()));

                URI storageSystem = journalVolume.getStorageSystem();
                String rpSiteName = volume.getInternalSiteName();
                URI varray = volume.getVirtualArray();
                URI volumeId = volume.getId();

                // Generate a unique key based on Storage System + Internal Site + Virtual Array
                String key = storageSystem.toString() + rpSiteName + varray.toString();

                // Try and get an existing rp export object from the map using the key
                // If a separate varray is specified is for journals, a new entry will be created.
                RPExport rpExport = rpExportMap.get(key);

                // If it doesn't exist, create the entry and add it to the map with the key
                if (rpExport == null) {
                    _log.info("RPExport is for journals only");
                    rpExport = new RPExport(storageSystem, rpSiteName, varray);
                    rpExport.setIsJournalExport(true);
                    rpExportMap.put(key, rpExport);
                }

                _log.info(String.format("Adding %s volume [%s](%s) to export: %s", volume.getPersonality().toString(), volume.getLabel(),
                        volume.getId(), rpExport.toString()));
                rpExport.getVolumes().add(volumeId);
            }
        }

        _log.info("Generate the storage system exports...END");

        return rpExportMap.values();
    }

    private boolean stepFailed(final String token, final String step) throws WorkflowException {
        WorkflowStepCompleter.stepFailed(token, DeviceControllerErrors.recoverpoint.stepFailed(step));
        return false;
    }

    private boolean stepFailed(final String token, final ServiceCoded e, final String step) throws WorkflowException {
        if (e != null) {
            _log.error(String.format("RecoverPoint %s step failed: Exception:", step), e);
        }

        WorkflowStepCompleter.stepFailed(token, e);
        return false;
    }

    private boolean stepFailed(final String token, final Exception e, final String step) throws WorkflowException {
        if (e != null) {
            _log.error(String.format("RecoverPoint %s step failed: Exception:", step), e);
        }

        if (e instanceof ServiceCoded) {
            WorkflowStepCompleter.stepFailed(token, (ServiceCoded) e);
            return false;
        }
        WorkflowStepCompleter.stepFailed(token, DeviceControllerErrors.recoverpoint.stepFailed(step));
        return false;
    }

    /**
     * The step that deletes the CG from the RecoverPoint appliance if all of the volumeIDs are in the request,
     * otherwise delete replication sets and associated journals.
     *
     * @param rpSystem
     *            protection system
     * @param volumeIDs
     *            volume IDs
     * @param journalVolumeIDs
     *            Volume IDs of journals
     * @param token
     *            task ID
     * @return true if successful
     * @throws ControllerException
     */
    public boolean cgDeleteStep(URI rpSystem, List<URI> volumeIDs, List<URI> journalVolumeIDs, String token) throws ControllerException {
        WorkflowStepCompleter.stepExecuting(token);

        _log.info("cgDeleteStep is running");
        boolean lockException = false;
        RPHelper.setLinkStateWaitTimeOut(_coordinator);
        try {
            // Validate input arguments
            if (rpSystem == null) {
                _log.error("Protection system not sent into cgDeleteStep");
                throw DeviceControllerExceptions.recoverpoint.cgDeleteStepInvalidParam("protection system URI");
            }

            ProtectionSystem system = _dbClient.queryObject(ProtectionSystem.class, rpSystem);
            if (system == null) {
                _log.error("Protection system not in database");
                throw DeviceControllerExceptions.recoverpoint.cgDeleteStepInvalidParam("protection system null");
            }

            if (system.getInactive()) {
                _log.error("Protection system set to be deleted");
                throw DeviceControllerExceptions.recoverpoint.cgDeleteStepInvalidParam("protection system deleted");
            }

            if (volumeIDs == null) {
                _log.error("Volume IDs list is null");
                throw DeviceControllerExceptions.recoverpoint.cgDeleteStepInvalidParam("volume IDs null");
            }

            if (volumeIDs.isEmpty()) {
                _log.error("Volume IDs list is empty");
                throw DeviceControllerExceptions.recoverpoint.cgDeleteStepInvalidParam("volume IDs empty");
            }

            List<Volume> volumes = _dbClient.queryObject(Volume.class, volumeIDs);
            if (volumes.isEmpty()) {
                _log.info("All volumes already deleted. Not performing RP CG operation");
                WorkflowStepCompleter.stepSucceded(token);
                return true;
            }

            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, volumes.get(0).getConsistencyGroup());

            // lock around create and delete operations on the same CG
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getConsistencyGroupStorageKey(_dbClient, cg.getId(), system.getId()));
            boolean lockAcquired = _workflowService.acquireWorkflowLocks(_workflowService.getWorkflowFromStepId(token), lockKeys,
                    LockTimeoutValue.get(LockType.RP_CG));
            if (!lockAcquired) {
                lockException = true;
                throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(),
                        String.format("Delete or remove volumes from RP consistency group %s", cg.getCgNameOnStorageSystem(rpSystem)));
            }

            // Validate that all volumes belong to the same BlockConsistencyGroup
            for (Volume volume : volumes) {
                if (!volume.getConsistencyGroup().equals(cg.getId())) {
                    _log.error("Not all volumes belong to the same consistency group.");
                    throw DeviceControllerExceptions.recoverpoint.cgDeleteStepInvalidParam("volumes from different consistency groups");
                }
            }

            // Find a valid protection set reference so we can cleanup the protection set later.
            ProtectionSet protectionSet = null;
            for (Volume volume : volumes) {
                if (!NullColumnValueGetter.isNullNamedURI(volume.getProtectionSet())) {
                    protectionSet = _dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());
                    break;
                }
            }

            RecoverPointClient rp = RPHelper.getRecoverPointClient(system);

            // Validate that we found the protection info for each volume.
            RecoverPointVolumeProtectionInfo volumeProtectionInfo = null;
            for (Volume volume : volumes) {
                try {
                    if (volumeProtectionInfo == null) {
                        volumeProtectionInfo = rp.getProtectionInfoForVolume(RPHelper.getRPWWn(volume.getId(), _dbClient));
                        VirtualPool virtualPool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
                        volumeProtectionInfo.setMetroPoint(VirtualPool.vPoolSpecifiesMetroPoint(virtualPool));
                    }
                } catch (Exception e) {
                    // Do nothing. If we cannot find volume protection info for a volume, we do not want that
                    // exception preventing us from trying to find it for other volumes being deleted.
                    _log.warn(
                            "Looks like the volume(s) we're trying to remove from the RP appliance are no longer associated with a RP CG, continuing delete process.");
                }
            }

            // If we haven't been able to find protection info for at least one volume, we know there
            // is nothing in RP we need to cleanup.
            if (volumeProtectionInfo == null) {
                _log.warn(
                        "Looks like the volume(s) we're trying to remove from the RP appliance are no longer associated with a RP CG, continuing delete process.");
                WorkflowStepCompleter.stepSucceded(token);
                return true;
            }

            if (RPHelper.cgSourceVolumesContainsAll(_dbClient, cg.getId(), volumeIDs)) {
                // We are deleting all source volumes in the consistency group so we can delete the
                // RecoverPoint CG as well.
                rp.deleteCG(volumeProtectionInfo);

                // We want to reflect the CG being deleted in the BlockConsistencyGroup
                if (volumeIDs != null && !volumeIDs.isEmpty()) {
                    // Get the CG URI from the first volume
                    Volume vol = _dbClient.queryObject(Volume.class, volumeIDs.get(0));

                    if (vol.getConsistencyGroup() != null) {
                    	cleanUpRPCG(system.getId(), vol.getConsistencyGroup());
                    }

                    if (protectionSet == null || protectionSet.getInactive() || protectionSet.getVolumes() == null
                            || protectionSet.getVolumes().isEmpty()) {
                        _log.info("Cleanup unnecessary as protection set in ViPR is empty or has already been marked for deletion.");
                    } else {
                        _log.info("Removing all volume from protection set: " + protectionSet.getLabel());

                        // Remove all volumes in the ProtectionSet and mark for deletion
                        List<String> removeVolumeIDs = new ArrayList<String>(protectionSet.getVolumes());
                        cleanupProtectionSetVolumes(protectionSet, removeVolumeIDs, true);
                    }
                }

                setProtectionSetStatus(volumeProtectionInfo, ProtectionStatus.DISABLED.toString(), system);
            } else {
                List<RecoverPointVolumeProtectionInfo> replicationSetsToRemove = new ArrayList<RecoverPointVolumeProtectionInfo>();
                List<String> removeVolumeIDs = new ArrayList<String>();
                for (Volume volume : volumes) {
                    _log.info(String.format("Volume [%s] (%s) needs to have its replication set removed from RP", volume.getLabel(),
                            volume.getId()));

                    // Delete the replication set if there are more volumes (other replication sets).
                    // If there are no other replications sets we will simply delete the CG instead.
                    volumeProtectionInfo = rp.getProtectionInfoForVolume(RPHelper.getRPWWn(volume.getId(), _dbClient));

                    // Volume Info to give RP to clean up the RSets
                    replicationSetsToRemove.add(volumeProtectionInfo);
                    // Source volume to be removed from Protection Set
                    if (!NullColumnValueGetter.isNullURI(volume.getId())) {
                        removeVolumeIDs.add(volume.getId().toString());
                    }
                    // All Target volumes to be removed from Protection Set
                    List<Volume> targetVolumes = RPHelper.getTargetVolumes(volume, _dbClient);
                    for (Volume targetVol : targetVolumes) {
                        removeVolumeIDs.add(targetVol.getId().toString());
                    }
                }

                // Remove the Replication Sets from RP.
                // Wait for a min before attempting to delete the rsets.
                // RP has not yet synced up with the fact that new rsets were added and we attempted to delete them
                // because the rollback
                // started.
                // This delay helps in RP catching up before we issue another command.
                _log.info("waiting for 1 min before deleting replication sets");
                Thread.sleep(1000 * 60);
                rp.deleteReplicationSets(replicationSetsToRemove);

                // Remove any journal volumes that were added in this operation. Otherwise CG ends up in a weird state.
                if (journalVolumeIDs.isEmpty()) {
                    _log.info("There are no journal volumes to be deleted");
                } else {
                    List<Volume> journalVolumes = _dbClient.queryObject(Volume.class, journalVolumeIDs);
                    for (Volume journalVolume : journalVolumes) {
                        String journalWWN = RPHelper.getRPWWn(journalVolume.getId(), _dbClient);
                        _log.info(String.format("Removing Journal volume - %s : WWN - %s", journalVolume.getLabel(), journalWWN));
                        volumeProtectionInfo = rp.getProtectionInfoForVolume(journalWWN);
                        rp.deleteJournalFromCopy(volumeProtectionInfo, journalWWN);
                        removeVolumeIDs.add(journalVolume.getId().toString());
                    }
                }

                // Cleanup the ViPR Protection Set
                cleanupProtectionSetVolumes(protectionSet, removeVolumeIDs, false);
            }
            WorkflowStepCompleter.stepSucceded(token);
            _log.info("cgDeleteStep is complete");

            // collect and update the protection system statistics to account for
            // the CG that has been removed
            _log.info("Collection RP statistics post CG delete.");

            // Collect stats, even if we didn't delete the CG, because the volume count in the CG will go down.
            collectRPStatistics(system);
        } catch (Exception e) {
            if (lockException) {
                ServiceError serviceError = DeviceControllerException.errors.deleteVolumesAborted(volumeIDs.toString(), e);
                return stepFailed(token, serviceError, "cgDeleteStep");
            } else {
                return stepFailed(token, e, "cgDeleteStep");
            }
        }
        return true;
    }

    /**
     * Method to clean up a RP consistency group.
     * 
     * @param protectionSystemUri The URI of the RP protection system.
     * @param cgUri The URI of the ViPR consistency group.
     */
    private void cleanUpRPCG(URI protectionSystemUri, URI cgUri) {
        BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
        String cgName = CG_NAME_PREFIX + cg.getLabel();
        
        // Remove all storage system CG entries stored on the BlockConsistencyGroup that match
        // the give CG name. For RecoverPoint, there will be an entry for the distributed CG
        // on each cluster so this takes care of removing each of those.
        List<String> cgRefsToDelete = new ArrayList<String>();
        StringSetMap sysCgs = cg.getSystemConsistencyGroups();
        if (sysCgs != null && !sysCgs.isEmpty()) {
            StringSet cgsForRp = sysCgs.get(protectionSystemUri.toString());

            if (cgsForRp != null && !cgsForRp.isEmpty()) {
                Iterator<String> itr = cgsForRp.iterator();
                while (itr.hasNext()) {
                    String rpCgName = itr.next();
                    if (cg.nameExistsForStorageSystem(protectionSystemUri, cgName)) {
                        cgRefsToDelete.add(rpCgName);
                    }
                }
            }
        }

        // Remove the RP CG from the BlockConsistencyGroup if it is there.
        for (String cgRef : cgRefsToDelete) {
            _log.info(String.format("Removing system consistency group %s from protection system %s",
                    cgRef, protectionSystemUri.toString()));
            cg.removeSystemConsistencyGroup(protectionSystemUri.toString(), cgRef);
        }

        // Remove the RP type
        StringSet cgTypes = cg.getTypes();
        cgTypes.remove(BlockConsistencyGroup.Types.RP.name());
        cg.setTypes(cgTypes);

        StringSet requestedTypes = cg.getRequestedTypes();
        requestedTypes.remove(BlockConsistencyGroup.Types.RP.name());
        cg.setRequestedTypes(requestedTypes);
        
        _dbClient.updateObject(cg);
    }    
    
    /**
     * Cleans up the given ProtectionSet by removing volumes from it and marking for deletion if specified.
     * Also removes the volume's association on the BlockConsistencyGroup.
     *
     * @param protectionSet
     *            the protection set from which to remove volumes.
     * @param volumeIDs
     *            the volume ids to remove from the protection set.
     * @param markProtectionSetForDeletion
     *            if true, marks the protection set for deletion.
     */
    private void cleanupProtectionSetVolumes(ProtectionSet protectionSet, List<String> volumeIDs, boolean markProtectionSetForDeletion) {
        if (protectionSet != null) {
            _log.info("Removing the following volumes from protection set {}: {}", protectionSet.getLabel(), volumeIDs.toString());
            StringSet psetVolumes = protectionSet.getVolumes();
            psetVolumes.removeAll(volumeIDs);
            protectionSet.setVolumes(psetVolumes);

            if (markProtectionSetForDeletion) {
                // Mark the protection set for deletion
                protectionSet.setInactive(true);
            }

            _dbClient.updateObject(protectionSet);
        }

    }

    /**
     * The step that rolls back the delete of the CG from the RecoverPoint appliance. It is a no-op.
     *
     * @param rpSystem
     *            protection system
     * @param volumeID
     *            volume ID
     * @param token
     *            task ID
     * @return true if successful
     * @throws WorkflowException
     */
    public boolean cgDeleteRollbackStep(URI rpSystem, Set<URI> volumeIDs, String token) throws WorkflowException {
        WorkflowStepCompleter.stepExecuting(token);
        _log.info("cgDeleteStep rollback is a no-op");
        WorkflowStepCompleter.stepSucceded(token);
        return true;
    }

    /**
     * Add the steps that will remove the consistency groups (if this the last replication set in the CG),
     * otherwise it will remove the replication set associated with the volume.
     *
     * @param workflow
     *            workflow to add steps to
     * @param volumeID
     *            volume ID of the volume sent from the API
     * @throws InternalException
     */
    private String addDeleteCGStep(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors) throws InternalException {
        String returnStep = waitFor;

        // Create a map of all of the protection sets this delete operation impacts.
        Map<URI, Set<URI>> cgVolumeMap = new HashMap<URI, Set<URI>>();
        Map<URI, URI> cgProtectionSystemMap = new HashMap<URI, URI>();
        boolean validCg = false;
        for (VolumeDescriptor volumeDescriptor : volumeDescriptors) {
            Volume volume = _dbClient.queryObject(Volume.class, volumeDescriptor.getVolumeURI());

            if (NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
                // Don't try to delete the CG, there isn't one
                continue;
            } else {
                // If we have at least one valid CG to try and delete, we must keep track so we can
                // return the correct waitFor.
                validCg = true;
            }

            if (cgVolumeMap.get(volume.getConsistencyGroup()) == null) {
                cgVolumeMap.put(volume.getConsistencyGroup(), new HashSet<URI>());
            }
            cgVolumeMap.get(volume.getConsistencyGroup()).add(volume.getId());

            // Set the consistency group to protection system mapping
            if (!NullColumnValueGetter.isNullURI(cgProtectionSystemMap.get(volume.getConsistencyGroup()))
                    || !NullColumnValueGetter.isNullURI(volume.getProtectionController())) {
                cgProtectionSystemMap.put(volume.getConsistencyGroup(), volume.getProtectionController());
            }
        }

        // For each of the protection sets, create a series of steps to delete replication sets/cgs
        for (Entry<URI, Set<URI>> entry : cgVolumeMap.entrySet()) {
            URI cgId = entry.getKey();
            Set<URI> volumes = entry.getValue();
            BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(BlockConsistencyGroup.class, cgId);

            boolean deleteEntireCG = RPHelper.cgSourceVolumesContainsAll(_dbClient, consistencyGroup.getId(), volumes);
            if (!deleteEntireCG) {
                // If we're not deleting the entire CG, we need to ensure that none of the
                // link statuses on the volumes are in the failed-over state. Deleting
                // replication sets is not allowed while image access is enabled.
                for (URI volumeURI : volumes) {
                    Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                    if (volume != null && Volume.LinkStatus.FAILED_OVER.name().equalsIgnoreCase(volume.getLinkStatus())) {
                        String imageAccessEnabledError = String.format("Can not delete or remove protection from volume [%s](%s) "
                                + "while image access is enabled in RecoverPoint", volume.getLabel(), volume.getId());
                        _log.error(imageAccessEnabledError);
                        throw DeviceControllerExceptions.recoverpoint.cgDeleteStepInvalidParam(imageAccessEnabledError);
                    }
                }
            }

            ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, cgProtectionSystemMap.get(cgId));

            // If we are happy with deleting the entire CG based on our internal information, perform a check externally
            // to see if the volumes match the RSets. If there are extras that we're not managing, then we should revert
            // to removing the RSets instead.
            if (deleteEntireCG) {
                deleteEntireCG = RPHelper.validateCGForDelete(_dbClient, rpSystem, cgId, volumes);
            }

            // All protection sets can be deleted at the same time, but only one step per protection set can be running
            String cgWaitFor = waitFor;

            String stepId = workflow.createStepId();
            List<URI> volumeList = new ArrayList<URI>();
            volumeList.addAll(volumes);
            Workflow.Method cgRemovalExecuteMethod = new Workflow.Method(METHOD_DELETE_CG_STEP, rpSystem.getId(), volumeList,
                    new ArrayList<URI>()); // empty journalVolumeIDs list

            // Make all of the steps in removing this CG (or replication sets from this CG) sequential.
            cgWaitFor = workflow.createStep(STEP_DV_REMOVE_CG,
                    "Remove replication set(s) and/or consistency group subtask (if no more volumes) for RP CG: "
                            + consistencyGroup.getLabel(),
                    cgWaitFor, rpSystem.getId(), rpSystem.getSystemType(), this.getClass(), cgRemovalExecuteMethod, null, stepId);
        }

        if (!validCg) {
            // If there are no valid CGs to process then return the original waitFor.
            return returnStep;
        }

        return STEP_DV_REMOVE_CG;
    }

    /**
     * Add the steps that will remove the volumes from the export group
     * TODO: This could stand to be refactored to be simpler.
     *
     * @param workflow
     *            workflow object
     * @param waitFor
     *            step that these steps are dependent on
     * @param filteredSourceVolumeDescriptors
     *            volumes to act on
     * @return "waitFor" step that future steps should wait on
     * @throws InternalException
     */
    private String addExportRemoveVolumesSteps(Workflow workflow, String waitFor, List<VolumeDescriptor> filteredSourceVolumeDescriptors)
            throws InternalException {
        _log.info("Adding steps to remove volumes from export groups.");
        String returnStep = waitFor;
        Set<URI> volumeURIs = RPHelper.getVolumesToDelete(VolumeDescriptor.getVolumeURIs(filteredSourceVolumeDescriptors), _dbClient);

        _log.info(String.format("Following volume(s) will be unexported from their RP export groups :  [%s]",
                Joiner.on("--").join(volumeURIs)));

        Map<URI, RPExport> rpExports = new HashMap<URI, RPExport>();
        for (URI volumeURI : volumeURIs) {
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            if (volume == null) {
                _log.warn("Could not load volume with given URI: " + volumeURI);
                continue;
            }

            // get the protection system for this volume
            URI rpSystemId = volume.getProtectionController();
            ProtectionSystem rpSystem = null;
            if (rpSystemId != null) {
                rpSystem = _dbClient.queryObject(ProtectionSystem.class, rpSystemId);
                if (rpSystem == null || rpSystem.getInactive()) {
                    _log.warn("No protection system information found for volume {}. Volume cannot be removed from RP export groups.",
                            volume.getLabel());
                    continue;
                }
            }

            // Get the storage controller URI of the volume
            URI storageURI = volume.getStorageController();

            // Get the vpool of the volume
            VirtualPool virtualPool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());

            if (VirtualPool.isRPVPlexProtectHASide(virtualPool)) {
                _log.info(String.format("RP+VPLEX protect HA Source Volume [%s] to be removed from RP export group.", volume.getLabel()));
                // We are dealing with a RP+VPLEX distributed volume that has the HA as the protected side so we need to
                // get
                // the HA side export group only.
                if (volume.getAssociatedVolumes() != null && volume.getAssociatedVolumes().size() == 2) {
                    for (String associatedVolURI : volume.getAssociatedVolumes()) {
                        Volume associatedVolume = _dbClient.queryObject(Volume.class, URI.create(associatedVolURI));
                        if (associatedVolume.getVirtualArray().toString().equals(virtualPool.getHaVarrayConnectedToRp())) {
                            ExportGroup exportGroup = getExportGroup(rpSystem, volume.getId(), associatedVolume.getVirtualArray(),
                                    associatedVolume.getInternalSiteName());
                            if (exportGroup != null) {
                                _log.info(String.format("Removing volume [%s] from RP export group [%s].", volume.getLabel(),
                                        exportGroup.getGeneratedName()));
                            }
                            // Assuming we've found the correct Export Group for this volume, let's
                            // then add the information we need to the rpExports map.
                            addExportGroup(rpExports, exportGroup, volumeURI, storageURI);
                            break;
                        }
                    }
                }
            } else if (volume.getAssociatedVolumes() != null && volume.getAssociatedVolumes().size() == 2) {
                for (String associatedVolURI : volume.getAssociatedVolumes()) {
                    _log.info(String.format("VPLEX %s Volume [%s] to be removed from RP export group.", volume.getPersonality(), associatedVolURI));
                    Volume associatedVolume = _dbClient.queryObject(Volume.class, URI.create(associatedVolURI));
                    String internalSiteName = associatedVolume.getInternalSiteName();
                    URI virtualArray = associatedVolume.getVirtualArray();
                    
                    if (!VirtualPool.vPoolSpecifiesMetroPoint(virtualPool)) {
                    	// Only MetroPoint associated volumes will have the internalSiteName set. For VPlex distributed volumes
                    	// the parent (virtual volume) internal site name should be used.
                    	internalSiteName = volume.getInternalSiteName();
                    	// If we are using the parent volume's internal site name, we also need to use the parent volume's virtual array.
                    	// Again, only in the case of MetroPoint volumes would we want to use the associated volume's virtual array.
                    	virtualArray = volume.getVirtualArray();
                    }
                    
                    ExportGroup exportGroup = getExportGroup(rpSystem, volume.getId(), virtualArray,
                    		internalSiteName);
                    if (exportGroup != null) {
                        _log.info(String.format("Removing volume [%s] from RP export group [%s].", volume.getLabel(),
                                exportGroup.getGeneratedName()));
                    }
                    // Assuming we've found the correct Export Group for this volume, let's
                    // then add the information we need to the rpExports map.
                    addExportGroup(rpExports, exportGroup, volumeURI, storageURI);
                }
            } else {
                _log.info(String.format("Volume [%s] to be removed from RP export group.", volume.getLabel()));
                // Find the Export Group for this regular RP volume
                ExportGroup exportGroup = getExportGroup(rpSystem, volume.getId(), volume.getVirtualArray(), volume.getInternalSiteName());

                if (exportGroup != null) {
                    _log.info(String.format("Removing volume [%s] from RP export group [%s].", volume.getLabel(),
                            exportGroup.getGeneratedName()));
                }
                // Assuming we've found the correct Export Group for this volume, let's
                // then add the information we need to the rpExports map.
                addExportGroup(rpExports, exportGroup, volumeURI, storageURI);
            }
        }

        // Generate the workflow steps for export volume removal and volume deletion
        for (URI exportURI : rpExports.keySet()) {
            _log.info(String.format("RP export group will have these volumes removed: [%s]",
                    Joiner.on(',').join(rpExports.get(exportURI).getVolumes())));
            RPExport rpExport = rpExports.get(exportURI);
            if (!rpExport.getVolumes().isEmpty()) {
                _exportWfUtils.generateExportGroupRemoveVolumes(workflow, STEP_DV_REMOVE_VOLUME_EXPORT, waitFor,
                        rpExport.getStorageSystem(), exportURI, rpExport.getVolumes());
                returnStep = STEP_DV_REMOVE_VOLUME_EXPORT;
            }
        }

        _log.info("Completed adding steps to remove volumes from RP export groups.");

        return returnStep;
    }

    /**
     * Convenience method to add an RPExport object to the map of RPExports.
     *
     * @param rpExports
     *            the Map we want to add to.
     * @param exportGroup
     *            the export group who's ID we want to use as the key.
     * @param volumeURI
     *            the volume we want to add to the RPExport.
     * @param storageURI
     *            the storage system.
     */
    private void addExportGroup(Map<URI, RPExport> rpExports, ExportGroup exportGroup, URI volumeURI, URI storageURI) {
        if (exportGroup != null) {
            RPExport rpExport = rpExports.get(exportGroup.getId());
            if (rpExport == null) {
                rpExport = new RPExport();
                rpExport.setStorageSystem(storageURI);
                rpExports.put(exportGroup.getId(), rpExport);
            }
            if (!rpExport.getVolumes().contains(volumeURI)) {
            	// only add the volume if it doesn't already exist
            	rpExport.getVolumes().add(volumeURI);
            } else {
            	_log.info(String.format("Skipping volume %s because there is already an RPExport mapping for ExportGroup %s.", 
            			volumeURI, exportGroup.getId()));
            }
        }
    }

    /*
     * RPDeviceController.exportGroupCreate()
     * 
     * This method is a mini-orchestration of all of the steps necessary to create an export based on
     * a Bourne Snapshot object associated with a RecoverPoint bookmark.
     * 
     * This controller does not service block devices for export, only RP bookmark snapshots.
     * 
     * The method is responsible for performing the following steps:
     * - Enable the volumes to a specific bookmark.
     * - Call the block controller to export the target volume
     * 
     * @param protectionDevice The RP System used to manage the protection
     * 
     * @param exportgroupID The export group
     * 
     * @param snapshots snapshot list
     * 
     * @param initatorURIs initiators to send to the block controller
     * 
     * @param token The task object
     */
    @Override
    public void exportGroupCreate(URI protectionDevice, URI exportGroupID, List<URI> initiatorURIs, Map<URI, Integer> snapshots,
            String token) throws ControllerException {
        TaskCompleter taskCompleter = null;
        try {
            // Grab the RP System information; we'll need it to talk to the RP client
            ProtectionSystem rpSystem = getRPSystem(protectionDevice);

            taskCompleter = new RPCGExportCompleter(exportGroupID, token);

            // Ensure the bookmarks actually exist before creating the export group
            searchForBookmarks(protectionDevice, snapshots.keySet());

            // Create a new token/taskid and use that in the workflow. Multiple threads entering this method might
            // collide with each others
            // workflows in cassandra if the taskid is not unique.
            String newToken = UUID.randomUUID().toString();

            // Set up workflow steps.
            Workflow workflow = _workflowService.getNewWorkflow(this, "exportGroupCreate", true, newToken);

            // Tasks 1: Activate the bookmarks
            //
            // Enable image access on the target volumes
            addEnableImageAccessStep(workflow, rpSystem, snapshots, null);

            // Tasks 2: Export Volumes
            //
            // Export the volumes associated with the snapshots to the host
            addExportSnapshotSteps(workflow, rpSystem, exportGroupID, snapshots, initiatorURIs);

            // Execute the plan and allow the WorkflowExecutor to fire the taskCompleter.
            String successMessage = String.format("Workflow of Export Group %s successfully created", exportGroupID);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (InternalException e) {
            _log.error("Operation failed with Exception: ", e);
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, e);
            }
        } catch (Exception e) {
            _log.error("Operation failed with Exception: ", e);
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, DeviceControllerException.errors.jobFailed(e));
            }
        }
    }

    /**
     * Method that adds the export snapshot step.
     *
     * @param workflow
     *            workflow object
     * @param rpSystem
     *            RP system
     * @param exportGroupID
     *            export group ID
     * @param snapshots
     *            snapshots, HLUs
     * @param initiatorURIs
     *            initiators
     * @throws InternalException
     * @throws URISyntaxException
     */
    private void addExportSnapshotSteps(Workflow workflow, ProtectionSystem rpSystem, URI exportGroupID, Map<URI, Integer> snapshots,
            List<URI> initiatorURIs) throws InternalException {
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupID);

        // Reformat the incoming arguments for the block export create call
        String exportStep = workflow.createStepId();
        initTaskStatus(exportGroup, exportStep, Operation.Status.pending, "create export");

        // Get the mapping of storage systems to snapshot block objects for export
        Map<URI, Map<URI, Integer>> storageToBlockObjects = getStorageToBlockObjects(snapshots);

        for (Map.Entry<URI, Map<URI, Integer>> entry : storageToBlockObjects.entrySet()) {
            _log.info(String
                    .format(
                            "Adding workflow step to export RP bookmark and associated target volumes.  ExportGroup: %s, Initiators: %s, Volume Map: %s",
                            exportGroup.getId(), initiatorURIs, entry.getValue()));
            _exportWfUtils.generateExportGroupCreateWorkflow(workflow, null, STEP_ENABLE_IMAGE_ACCESS, entry.getKey(), exportGroupID,
                    entry.getValue(), initiatorURIs);
        }

        _log.info("Finished adding export group create steps in workflow: " + exportGroup.getId());
    }

    /**
     * Given a Map of snapshots (bookmarks) to HLUs, this method obtains all target copy volumes
     * corresponding to the snapshot and groups them by storage system.
     *
     * @param snapshots
     *            the base mapping of snapshots to HLU
     * @return a mapping of snapshot export BlockObjects by storage system
     */
    private Map<URI, Map<URI, Integer>> getStorageToBlockObjects(Map<URI, Integer> snapshots) {
        Map<URI, Map<URI, Integer>> storageToBlockObjects = new HashMap<URI, Map<URI, Integer>>();
        for (Map.Entry<URI, Integer> entry : snapshots.entrySet()) {
            BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, entry.getKey());
            // Get the export objects corresponding to this snapshot
            List<BlockObject> blockObjects = getExportObjectsForBookmark(snapshot);

            for (BlockObject blockObject : blockObjects) {
                URI storage = blockObject.getStorageController();
                Map<URI, Integer> volumesForStorage = storageToBlockObjects.get(storage);
                if (volumesForStorage == null) {
                    volumesForStorage = new HashMap<URI, Integer>();
                    storageToBlockObjects.put(storage, volumesForStorage);
                }
                // Add the BlockObject entry and set the HLU to the HLU corresponding to the snapshot
                volumesForStorage.put(blockObject.getId(), entry.getValue());
            }
        }

        return storageToBlockObjects;
    }

    /**
     * Method that adds the steps to the workflow to enable image access
     *
     * @param workflow
     *            workflow object
     * @param rpSystem
     *            RP system
     * @param snapshots
     *            snapshot map
     * @throws WorkflowException
     */
    private String addEnableImageAccessStep(Workflow workflow, ProtectionSystem rpSystem, Map<URI, Integer> snapshots, String waitFor)
            throws InternalException {
        String stepId = workflow.createStepId();
        Workflow.Method enableImageAccessExecuteMethod = new Workflow.Method(METHOD_ENABLE_IMAGE_ACCESS_STEP, rpSystem.getId(), snapshots);
        Workflow.Method enableImageAccessExecutionRollbackMethod = new Workflow.Method(METHOD_ENABLE_IMAGE_ACCESS_ROLLBACK_STEP,
                rpSystem.getId(), snapshots);

        workflow.createStep(STEP_ENABLE_IMAGE_ACCESS, "Enable image access subtask for export group: " + snapshots.keySet(), waitFor,
                rpSystem.getId(), rpSystem.getSystemType(), this.getClass(), enableImageAccessExecuteMethod,
                enableImageAccessExecutionRollbackMethod, stepId);

        _log.info(String.format("Added enable image access step [%s] in workflow", stepId));

        return STEP_ENABLE_IMAGE_ACCESS;
    }

    /**
     * Workflow step method for enabling an image access
     *
     * @param rpSystem
     *            RP system
     * @param snapshots
     *            Snapshot list to enable
     * @param token
     *            the task
     * @return true if successful
     * @throws ControllerException
     */
    public boolean enableImageAccessStep(URI rpSystemId, Map<URI, Integer> snapshots, String token) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(token);
            URI device = null;
            for (URI snapshotID : snapshots.keySet()) {
                BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotID);
                if (device == null) {
                    device = snapshot.getStorageController();
                }
            }

            // Enable snapshots
            enableImageForSnapshots(rpSystemId, device, new ArrayList<URI>(snapshots.keySet()), token);

            // Update the workflow state.
            WorkflowStepCompleter.stepSucceded(token);
        } catch (Exception e) {
            stepFailed(token, e, "enableImageAccessStep");
            return false;
        }

        return true;
    }

    /**
     * Workflow rollback step method for enabling an image access
     *
     * @param rpSystemId
     *            RP System
     * @param snapshots
     *            list of snapshots to rollback
     * @param stepId
     * @return
     * @throws ControllerException
     */
    public boolean enableImageAccessStepRollback(URI rpSystemId, Map<URI, Integer> snapshots, String stepId)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            // disable image access

            // The sync active field is irrelevant but we will set it to false anyway. This rollback call to
            // disableImageForSnapshots will mark the snapshots inactive.
            boolean setSnapshotSyncActive = false;
            disableImageForSnapshots(rpSystemId, new ArrayList<URI>(snapshots.keySet()), setSnapshotSyncActive,
                    stepId);

            // Update the workflow state.
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception e) {
            return stepFailed(stepId, e, "enableImageAccessStepRollback");
        }
        return true;
    }

    /*
     * RPDeviceController.exportGroupDelete()
     * 
     * This method is a mini-orchestration of all of the steps necessary to delete an export group.
     * 
     * This controller does not service block devices for export, only RP bookmark snapshots.
     * 
     * The method is responsible for performing the following steps:
     * - Call the block controller to delete the export of the target volumes
     * - Disable the bookmarks associated with the snapshots.
     * 
     * @param protectionDevice The RP System used to manage the protection
     * 
     * @param exportgroupID The export group
     * 
     * @param token The task object associated with the volume creation task that we piggy-back our events on
     */
    @Override
    public void exportGroupDelete(URI protectionDevice, URI exportGroupID, String token) throws InternalException {
        TaskCompleter taskCompleter = null;
        try {
            // Grab the RP System information; we'll need it to talk to the RP client
            ProtectionSystem rpSystem = getRPSystem(protectionDevice);

            taskCompleter = new RPCGExportDeleteCompleter(exportGroupID, token);

            // Create a new token/taskid and use that in the workflow. Multiple threads entering this method might
            // collide with each others
            // workflows in cassandra if the taskid is not unique.
            String newToken = UUID.randomUUID().toString();

            // Set up workflow steps.
            Workflow workflow = _workflowService.getNewWorkflow(this, "exportGroupDelete", true, newToken);

            // Task 1: deactivate the bookmarks
            //
            // Disable image access on the target volumes
            // This is important to do first because:
            // After the export group is deleted (in the next step), we may not have access to the object.
            // If export delete itself were to fail, it's good that we at least got this step done. Easier to remediate.
            addDisableImageAccessSteps(workflow, rpSystem, exportGroupID);

            // Task 2: Export Delete Volumes
            //
            // Delete of the export group with the volumes associated with the snapshots to the host
            addExportSnapshotDeleteSteps(workflow, rpSystem, exportGroupID);

            // Execute the plan and allow the WorkflowExecutor to fire the taskCompleter.
            String successMessage = String.format("Workflow of Export Group %s Delete successfully created", exportGroupID);
            workflow.executePlan(taskCompleter, successMessage);

        } catch (InternalException e) {
            _log.error("Operation failed with Exception: ", e);
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, e);
            }
        } catch (Exception e) {
            _log.error("Operation failed with Exception: ", e);
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, DeviceControllerException.errors.jobFailed(e));
            }
        }
    }

    /**
     * @param storageURI
     * @param exportGroupURI
     * @param initiators
     * @param token
     */
    @Override
    public void exportGroupAddInitiators(URI storageURI, URI exportGroupURI, List<URI> initiators, String token) throws InternalException {
        WorkflowStepCompleter.stepFailed(token, DeviceControllerErrors.recoverpoint.rpNotSupportExportGroupInitiatorsAddOperation());
    }

    /**
     * @param storageURI
     * @param exportGroupURI
     * @param initiators
     * @param token
     */
    @Override
    public void exportGroupRemoveInitiators(URI storageURI, URI exportGroupURI, List<URI> initiators, String token)
            throws InternalException {
        WorkflowStepCompleter.stepFailed(token, DeviceControllerErrors.recoverpoint.rpNotSupportExportGroupInitiatorsRemoveOperation());
    }

    /**
     * Method that adds the export snapshot delete step.
     *
     * @param workflow
     *            workflow object
     * @param rpSystem
     *            RP system
     * @param exportGroupID
     *            export group ID
     * @throws InternalException
     */
    private void addExportSnapshotDeleteSteps(Workflow workflow, ProtectionSystem rpSystem, URI exportGroupID) throws InternalException {
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupID);

        // Collect all of the information needed to assemble a step for the workflow
        String exportStep = workflow.createStepId();
        initTaskStatus(exportGroup, exportStep, Operation.Status.pending, "export delete");
        StorageSystem device = null;
        for (String volumeIDString : exportGroup.getVolumes().keySet()) {
            URI blockID;
            try {
                blockID = new URI(volumeIDString);
                BlockObject block = BlockObject.fetch(_dbClient, blockID);
                if (block.getProtectionController() != null && device == null) {
                    device = _dbClient.queryObject(StorageSystem.class, block.getStorageController());

                    _exportWfUtils.generateExportGroupDeleteWorkflow(workflow, STEP_EXPORT_DELETE_SNAPSHOT, STEP_EXPORT_GROUP_DELETE,
                            device.getId(), exportGroupID);
                }
            } catch (URISyntaxException e) {
                _log.error("Couldn't find volume ID for export delete: " + volumeIDString, e);
                // continue
            }
        }

        _log.info("Created export group delete step in workflow: " + exportGroup.getId());
    }

    /*
     * Method that adds the steps to the workflow to disable image access (for BLOCK snapshots)
     * 
     * @param workflow Workflow
     * 
     * @param waitFor waitFor step id
     * 
     * @param snapshots list of snapshot to disable
     * 
     * @param rpSystem RP system
     * 
     * @throws InternalException
     */
    private void addBlockSnapshotDisableImageAccessStep(Workflow workflow, String waitFor, List<URI> snapshots, ProtectionSystem rpSystem)
            throws InternalException {
        String stepId = workflow.createStepId();

        Workflow.Method disableImageAccessExecuteMethod = new Workflow.Method(METHOD_SNAPSHOT_DISABLE_IMAGE_ACCESS_SINGLE_STEP,
                rpSystem.getId(), snapshots);

        workflow.createStep(STEP_DISABLE_IMAGE_ACCESS, "Disable image access subtask for snapshots ", waitFor, rpSystem.getId(),
                rpSystem.getSystemType(), this.getClass(), disableImageAccessExecuteMethod, null, stepId);

        _log.info(String.format("Added block snapshot disable access step [%s] in workflow", stepId));
    }

    /**
     * Workflow step method for disabling an image access. Called only when disabling image access
     * during a snapshot create that is part array snapshot and part RP bookmark.
     *
     * @param rpSystemId
     *            RP system
     * @param snapshots
     *            List of snapshot URIs
     * @param token
     *            step Id
     * @return
     * @throws ControllerException
     */
    public boolean snapshotDisableImageAccessSingleStep(URI rpSystemId, List<URI> snapshots, String token)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(token);
            // Pass the value of true for the isSyncActive field because this is a disable image
            // access call for a snapshot create request that is part local array snap and part
            // RP bookmark.
            boolean setSnapshotSyncActive = true;
            disableImageForSnapshots(rpSystemId, snapshots, setSnapshotSyncActive, token);
            // Update the workflow state.
            WorkflowStepCompleter.stepSucceded(token);
        } catch (Exception e) {
            _log.error(String.format("snapshotDisableImageAccessSingleStep Failed - Protection System: %s", String.valueOf(rpSystemId)));
            return stepFailed(token, e, "snapshotDisableImageAccessSingleStep");
        }

        return true;
    }

    /**
     * Method that adds the steps to the workflow to disable image access
     *
     * @param workflow
     *            workflow object
     * @param rpSystem
     *            RP system
     * @param exportGroupID
     *            export group ID
     * @throws WorkflowException
     */
    private void addDisableImageAccessSteps(Workflow workflow, ProtectionSystem rpSystem, URI exportGroupID) throws WorkflowException {
        String stepId = workflow.createStepId();

        Workflow.Method disableImageAccessExecuteMethod = new Workflow.Method(METHOD_DISABLE_IMAGE_ACCESS_STEP, rpSystem.getId(),
                exportGroupID);

        workflow.createStep(STEP_EXPORT_GROUP_DELETE, "Disable image access subtask for export group: " + exportGroupID, null,
                rpSystem.getId(), rpSystem.getSystemType(), this.getClass(), disableImageAccessExecuteMethod, null, stepId);

        _log.info(String.format("Added disable image access step [%s] in workflow", stepId));
    }

    /**
     * Workflow step method for disabling an image access of all snapshots in an export group
     *
     * @param rpSystem
     *            RP system
     * @param token
     *            the task
     * @return true if successful
     * @param exportGroupID
     *            export group ID
     * @throws ControllerException
     */
    public boolean disableImageAccessStep(URI rpSystemId, URI exportGroupURI, String token) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(token);

            List<URI> snapshots = new ArrayList<URI>();
            // In order to find all of the snapshots to deactivate, go through the devices, find the RP snapshots, and
            // deactivate any active
            // ones
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            for (String exportVolumeIDStr : exportGroup.getVolumes().keySet()) {
                URI blockID;
                blockID = new URI(exportVolumeIDStr);
                BlockObject block = BlockObject.fetch(_dbClient, blockID);
                if (block.getProtectionController() != null) {
                    if (block.getId().toString().contains("BlockSnapshot")) {
                        // Collect this snapshot; it needs to be disabled
                        snapshots.add(block.getId());
                    }
                }
            }

            disableImageForSnapshots(rpSystemId, new ArrayList<URI>(snapshots), false, token);

            // Update the workflow state.
            WorkflowStepCompleter.stepSucceded(token);
        } catch (Exception e) {
            _log.error(String.format("disableImageAccessStep Failed - Protection System: %s, export group: %s", String.valueOf(rpSystemId),
                    String.valueOf(exportGroupURI)));
            return stepFailed(token, e, "disableImageAccessStep");
        }
        return true;
    }

    /**
     * Add steps to disable image access
     *
     * @param workflow
     *            workflow object
     * @param rpSystem
     *            protection system
     * @param exportGroupID
     *            export group ID
     * @param snapshotIDs
     *            snapshot ID
     * @throws InternalException
     */
    private void addDisableImageAccessSteps(Workflow workflow, ProtectionSystem rpSystem, URI exportGroupID, List<URI> snapshotIDs)
            throws InternalException {
        String stepId = workflow.createStepId();

        Workflow.Method disableImageAccessExecuteMethod = new Workflow.Method(METHOD_DISABLE_IMAGE_ACCESS_SINGLE_STEP, rpSystem.getId(),
                exportGroupID, snapshotIDs);

        workflow.createStep(STEP_EXPORT_GROUP_DISABLE, "Disable image access subtask for export group: " + exportGroupID, null,
                rpSystem.getId(), rpSystem.getSystemType(), this.getClass(), disableImageAccessExecuteMethod, null, stepId);

        _log.info(String.format("Added disable image access step [%s] in workflow", stepId));
    }

    /**
     * Workflow step method for disabling an image access
     *
     * @param rpSystemId
     *            RP system URI
     * @param exportGroupURI
     *            ExportGroup URI
     * @param snapshots
     *            list of snapshots to disable
     * @param token
     * @return boolean
     * @throws ControllerException
     */
    public boolean disableImageAccessSingleStep(URI rpSystemId, URI exportGroupURI, List<URI> snapshots, String token)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(token);

            disableImageForSnapshots(rpSystemId, snapshots, false, token);

            // Update the workflow state.
            WorkflowStepCompleter.stepSucceded(token);
        } catch (Exception e) {
            _log.error(String.format("disableImageAccessSingleStep Failed - Protection System: %s, export group: %s",
                    String.valueOf(rpSystemId), String.valueOf(exportGroupURI)));
            return stepFailed(token, e, "disableImageAccessSingleStep");
        }

        return true;
    }

    /*
     * RPDeviceController.exportAddVolume()
     * 
     * This method is a mini-orchestration of all of the steps necessary to add a volume to an export group
     * that is based on a Bourne Snapshot object associated with a RecoverPoint bookmark.
     * 
     * This controller does not service block devices for export, only RP bookmark snapshots.
     * 
     * The method is responsible for performing the following steps:
     * - Enable the volumes to a specific bookmark.
     * - Call the block controller to export the target volume
     * 
     * @param protectionDevice The RP System used to manage the protection
     * 
     * @param exportGroupID The export group
     * 
     * @param snapshot RP snapshot
     * 
     * @param lun HLU
     * 
     * @param token The task object associated with the volume creation task that we piggy-back our events on
     */
    @Override
    public void exportGroupAddVolumes(URI protectionDevice, URI exportGroupID, Map<URI, Integer> snapshots, String token)
            throws InternalException {
        TaskCompleter taskCompleter = null;
        try {
            // Grab the RP System information; we'll need it to talk to the RP client
            ProtectionSystem rpSystem = getRPSystem(protectionDevice);

            taskCompleter = new RPCGExportCompleter(exportGroupID, token);

            // Ensure the bookmarks actually exist before creating the export group
            searchForBookmarks(protectionDevice, snapshots.keySet());

            // Create a new token/taskid and use that in the workflow. Multiple threads entering this method might
            // collide with each others
            // workflows in cassandra if the taskid is not unique.
            String newToken = UUID.randomUUID().toString();

            // Set up workflow steps.
            Workflow workflow = _workflowService.getNewWorkflow(this, "exportGroupAddVolume", true, newToken);

            // Tasks 1: Activate the bookmark
            //
            // Enable image access on the target volume
            addEnableImageAccessStep(workflow, rpSystem, snapshots, null);

            // Tasks 2: Export Volumes
            //
            // Export the volumes associated with the snapshots to the host
            addExportAddVolumeSteps(workflow, rpSystem, exportGroupID, snapshots);

            // Execute the plan and allow the WorkflowExecutor to fire the taskCompleter.
            String successMessage = String.format("Workflow of Export Group %s Add Volume successfully created", exportGroupID);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (InternalException e) {
            _log.error("Operation failed with Exception: ", e);
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, e);
            }
        } catch (Exception e) {
            _log.error("Operation failed with Exception: ", e);
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, DeviceControllerException.errors.jobFailed(e));
            }
        }
    }

    /**
     * Method that adds the export snapshot step for a single volume
     *
     * @param workflow
     *            workflow object
     * @param rpSystem
     *            RP system
     * @param exportGroupID
     *            export group ID
     * @param snapshotID
     *            snapshot ID
     * @param hlu
     *            host logical unit
     * @throws InternalException
     */
    private void addExportAddVolumeSteps(Workflow workflow, ProtectionSystem rpSystem, URI exportGroupID, Map<URI, Integer> snapshots)
            throws InternalException {
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupID);

        String exportStep = workflow.createStepId();
        initTaskStatus(exportGroup, exportStep, Operation.Status.pending, "export add volume");

        // Get the mapping of storage systems to snapshot block objects for export
        Map<URI, Map<URI, Integer>> storageToBlockObjects = getStorageToBlockObjects(snapshots);

        for (Map.Entry<URI, Map<URI, Integer>> entry : storageToBlockObjects.entrySet()) {
            _log.info(String
                    .format(
                            "Adding workflow step to add RP bookmark and associated target volumes to export.  ExportGroup: %s, Storage System: %s, Volume Map: %s",
                            exportGroup.getId(), entry.getKey(), entry.getValue()));
            _exportWfUtils.generateExportGroupAddVolumes(workflow, null, STEP_ENABLE_IMAGE_ACCESS, entry.getKey(), exportGroupID,
                    entry.getValue());
        }

        _log.info("Finished adding export group add volume steps in workflow: " + exportGroup.getId());
    }

    /**
     * RPDeviceController.exportRemoveVolume()
     *
     * This method is a mini-orchestration of all of the steps necessary to remove an RP volume from an export group.
     *
     * This controller does not service block devices for export, only RP bookmark snapshots.
     *
     * The method is responsible for performing the following steps:
     * - Call the block controller to delete the export of the target volume
     * - Disable the bookmarks associated with the snapshot.
     *
     * @param protectionDevice
     *            The RP System used to manage the protection
     * @param exportgroupID
     *            The export group
     * @param snapshotID
     *            snapshot ID to remove
     * @param token
     *            The task object
     */
    @Override
    public void exportGroupRemoveVolumes(URI protectionDevice, URI exportGroupID, List<URI> snapshotIDs, String token)
            throws InternalException {
        TaskCompleter taskCompleter = null;
        try {
            // Grab the RP System information; we'll need it to talk to the RP client
            ProtectionSystem rpSystem = getRPSystem(protectionDevice);

            taskCompleter = new RPCGExportDeleteCompleter(exportGroupID, token);

            // Create a new token/taskid and use that in the workflow. Multiple threads entering this method might
            // collide with each others
            // workflows in cassandra if the taskid is not unique.
            String newToken = UUID.randomUUID().toString();

            // Set up workflow steps.
            Workflow workflow = _workflowService.getNewWorkflow(this, "exportRemoveVolume", true, newToken);

            // Task 1: deactivate the bookmark
            //
            // Disable image access on the target volumes
            // We want to run this first so we at least get the target volume freed-up, even if
            // the export remove fails.
            addDisableImageAccessSteps(workflow, rpSystem, exportGroupID, snapshotIDs);

            // Task 2: Export Volume removal
            //
            // Export the volumes associated with the snapshots to the host
            addExportRemoveVolumeSteps(workflow, rpSystem, exportGroupID, snapshotIDs);

            // Execute the plan and allow the WorkflowExecutor to fire the taskCompleter.
            String successMessage = String.format("Workflow of Export Group %s Remove Volume successfully created", exportGroupID);
            workflow.executePlan(taskCompleter, successMessage);

        } catch (InternalException e) {
            _log.error("Operation failed with Exception: ", e);
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, e);
            }
        } catch (Exception e) {
            _log.error("Operation failed with Exception: ", e);
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, DeviceControllerException.errors.jobFailed(e));
            }
        }
    }

    /**
     * Add the export remove volume step to the workflow
     *
     * @param workflow
     *            workflow object
     * @param rpSystem
     *            protection system
     * @param exportGroupID
     *            export group
     * @param boIDs
     *            volume/snapshot IDs
     * @throws InternalException
     */
    private void addExportRemoveVolumeSteps(Workflow workflow, ProtectionSystem rpSystem, URI exportGroupID, List<URI> boIDs)
            throws InternalException {
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupID);
        String exportStep = workflow.createStepId();
        initTaskStatus(exportGroup, exportStep, Operation.Status.pending, "export remove volumes (that contain RP snapshots)");
        Map<URI, List<URI>> deviceToBlockObjects = new HashMap<URI, List<URI>>();

        for (URI snapshotID : boIDs) {
            BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotID);
            // Get the export objects corresponding to this snapshot
            List<BlockObject> objectsToRemove = getExportObjectsForBookmark(snapshot);

            for (BlockObject blockObject : objectsToRemove) {
                List<URI> blockObjects = deviceToBlockObjects.get(blockObject.getStorageController());
                if (blockObjects == null) {
                    blockObjects = new ArrayList<URI>();
                    deviceToBlockObjects.put(blockObject.getStorageController(), blockObjects);
                }
                blockObjects.add(blockObject.getId());
            }
        }

        for (Map.Entry<URI, List<URI>> deviceEntry : deviceToBlockObjects.entrySet()) {
            _log.info(String
                    .format(
                            "Adding workflow step to remove RP bookmarks and associated target volumes from export.  ExportGroup: %s, Storage System: %s, BlockObjects: %s",
                            exportGroup.getId(), deviceEntry.getKey(), deviceEntry.getValue()));
            _exportWfUtils.generateExportGroupRemoveVolumes(workflow, STEP_EXPORT_REMOVE_SNAPSHOT, STEP_EXPORT_GROUP_DISABLE,
                    deviceEntry.getKey(), exportGroupID, deviceEntry.getValue());
        }

        _log.info(String.format("Created export group remove snapshot steps in workflow: %s", exportGroup.getId()));
    }

    /**
     * Gets the export objects corresponding to the given BlockSnapshot (bookmark). The
     * BlockObjects corresponding to a BlockSnapshot includes itself along with all target
     * Volumes belonging to the same target copy (virtual array).
     *
     * @param snapshot
     *            the BlockSnapshot
     * @return a list of BlockObjects for export for a given RP bookmark
     */
    private List<BlockObject> getExportObjectsForBookmark(BlockSnapshot snapshot) {
        List<BlockObject> exportBlockObjects = new ArrayList<BlockObject>();
        // Add the snapshot to the list
        exportBlockObjects.add(snapshot);

        List<Volume> targetVolumesForCopy = RPHelper.getTargetVolumesForVarray(_dbClient, snapshot.getConsistencyGroup(),
                snapshot.getVirtualArray());

        for (Volume targetCopyVolume : targetVolumesForCopy) {
            // Do not add the target volume that is already referenced by the BlockSnapshot
            if (!targetCopyVolume.getNativeId().equalsIgnoreCase(snapshot.getNativeId())) {
                exportBlockObjects.add(targetCopyVolume);
            }
        }

        return exportBlockObjects;
    }

    /**
     * Update the params objects with the proper WWN information so the CG can be created.
     *
     * @param params
     *            cg params
     * @throws InternalException
     */
    private void updateCGParams(CGRequestParams params) throws InternalException {
        for (CreateCopyParams copy : params.getCopies()) {
            _log.info("View copy: " + copy.getName());
            // Fill the map with varray
            for (CreateVolumeParams volume : copy.getJournals()) {
                Volume dbVolume = _dbClient.queryObject(Volume.class, volume.getVolumeURI());
                volume.setNativeGuid(dbVolume.getNativeGuid());
                volume.setWwn(RPHelper.getRPWWn(dbVolume.getId(), _dbClient));
            }
        }

        for (CreateRSetParams rset : params.getRsets()) {
            _log.info("View rset: " + rset.getName());
            for (CreateVolumeParams volume : rset.getVolumes()) {
                Volume dbVolume = _dbClient.queryObject(Volume.class, volume.getVolumeURI());
                volume.setNativeGuid(dbVolume.getNativeGuid());
                volume.setWwn(RPHelper.getRPWWn(dbVolume.getId(), _dbClient));
            }
        }
    }

    /**
     * RP specific workflow steps required prior to expanding the underlying volume are added here.
     * Ex. RP CG remove replication sets.
     *
     * @param workflow
     * @param volURI
     * @param expandVolURIs
     * @param taskId
     * @return
     * @throws WorkflowException
     */
    public String addPreVolumeExpandSteps(Workflow workflow, List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws WorkflowException {

        // Just grab a legit target volume that already has an assigned protection controller.
        // This will work for all operations, adding, removing, vpool change, etc.
        List<VolumeDescriptor> protectionControllerDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_TARGET, VolumeDescriptor.Type.RP_VPLEX_VIRT_TARGET },
                new VolumeDescriptor.Type[] {});
        // If there are no RP volumes, just return
        if (protectionControllerDescriptors.isEmpty()) {
            return null;
        }

        // Grab any volume from the list so we can grab the protection system, which will be the same for all volumes.
        Volume volume = _dbClient.queryObject(Volume.class, protectionControllerDescriptors.get(0).getVolumeURI());
        ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, volume.getProtectionController());

        // Get only the RP volumes from the descriptors.
        List<VolumeDescriptor> volumeDescriptorsTypeFilter = VolumeDescriptor
                .filterByType(
                        volumeDescriptors, new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_SOURCE,
                                VolumeDescriptor.Type.RP_EXISTING_SOURCE, VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE, },
                        new VolumeDescriptor.Type[] {});
        // If there are no RP volumes, just return
        if (volumeDescriptorsTypeFilter.isEmpty()) {
            return null;
        }

        for (VolumeDescriptor descriptor : volumeDescriptorsTypeFilter) {
            URI volURI = descriptor.getVolumeURI();
            ProtectionSystem rp = _dbClient.queryObject(ProtectionSystem.class, volume.getProtectionController());

            Map<String, RecreateReplicationSetRequestParams> rsetParams = new HashMap<String, RecreateReplicationSetRequestParams>();

            RecreateReplicationSetRequestParams rsetParam = getReplicationSettings(rpSystem, volURI);
            rsetParams.put(RPHelper.getRPWWn(volURI, _dbClient), rsetParam);

            String stepId = workflow.createStepId();
            Workflow.Method deleteRsetExecuteMethod = new Workflow.Method(METHOD_DELETE_RSET_STEP, rpSystem.getId(), Arrays.asList(volURI));

            Workflow.Method deleteRsetRollbackeMethod = new Workflow.Method(METHOD_DELETE_RSET_ROLLBACK_STEP, rpSystem.getId(),
                    Arrays.asList(volURI), rsetParams);

            workflow.createStep(STEP_PRE_VOLUME_EXPAND, "Pre volume expand, delete replication set subtask for RP: " + volURI.toString(),
                    null, rpSystem.getId(), rp.getSystemType(), this.getClass(), deleteRsetExecuteMethod, deleteRsetRollbackeMethod,
                    stepId);

            _log.info("addPreVolumeExpandSteps Replication Set in workflow");
        }

        return STEP_PRE_VOLUME_EXPAND;
    }

    /**
     * Gets the replication settings from RP for a given volume.
     *
     * @param rpSystem
     *            the RecoverPoint system.
     * @param volumeId
     *            the volume ID.
     * @return the replication set params to perform a recreate operation
     * @throws RecoverPointException
     */
    public RecreateReplicationSetRequestParams getReplicationSettings(ProtectionSystem rpSystem, URI volumeId)
            throws RecoverPointException {
        RecoverPointClient rp = RPHelper.getRecoverPointClient(rpSystem);
        Volume volume = _dbClient.queryObject(Volume.class, volumeId);
        RecoverPointVolumeProtectionInfo volumeProtectionInfo = rp.getProtectionInfoForVolume(RPHelper.getRPWWn(volume.getId(), _dbClient));
        return rp.getReplicationSet(volumeProtectionInfo);
    }

    /**
     * RP specific workflow steps after volume expansion are added here in this method
     * RP CG replication sets that were removed during pre expand are reconstructed with the new expanded volumes.
     *
     * @param workflow
     * @param waitFor
     * @param volume
     *            descriptors
     * @param taskId
     * @return
     * @throws WorkflowException
     */
    public String addPostVolumeExpandSteps(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws WorkflowException {

        // Get only the RP volumes from the descriptors.
        List<VolumeDescriptor> volumeDescriptorsTypeFilter = VolumeDescriptor
                .filterByType(
                        volumeDescriptors, new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_SOURCE,
                                VolumeDescriptor.Type.RP_EXISTING_SOURCE, VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE },
                        new VolumeDescriptor.Type[] {});
        // If there are no RP volumes, just return
        if (volumeDescriptorsTypeFilter.isEmpty()) {
            return waitFor;
        }

        for (VolumeDescriptor descriptor : volumeDescriptorsTypeFilter) {
            Volume volume = _dbClient.queryObject(Volume.class, descriptor.getVolumeURI());
            ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, volume.getProtectionController());

            Map<String, RecreateReplicationSetRequestParams> rsetParams = new HashMap<String, RecreateReplicationSetRequestParams>();

            RecreateReplicationSetRequestParams rsetParam = getReplicationSettings(rpSystem, volume.getId());
            rsetParams.put(RPHelper.getRPWWn(volume.getId(), _dbClient), rsetParam);

            String stepId = workflow.createStepId();
            Workflow.Method recreateRSetExecuteMethod = new Workflow.Method(METHOD_RECREATE_RSET_STEP, rpSystem.getId(),
                    Arrays.asList(volume.getId()), rsetParams);

            workflow.createStep(STEP_POST_VOLUME_EXPAND,
                    "Post volume Expand, Recreate replication set subtask for RP: " + volume.toString(), waitFor, rpSystem.getId(),
                    rpSystem.getSystemType(), this.getClass(), recreateRSetExecuteMethod, null, stepId);

            _log.info("Recreate Replication Set in workflow");
        }
        return STEP_POST_VOLUME_EXPAND;
    }

    /**
     * Delete the replication set
     *
     * @param rpSystem
     *            RP system
     * @param params
     *            parameters needed to create the CG
     * @param token
     *            the task
     * @return
     * @throws InternalException
     */
    public boolean deleteRSetStep(URI rpSystemId, List<URI> volumeIds, String token) throws InternalException {
        List<String> replicationSetNames = new ArrayList<String>();
        try {
            RPHelper.setLinkStateWaitTimeOut(_coordinator);
            List<RecoverPointVolumeProtectionInfo> volumeProtectionInfoList = new ArrayList<RecoverPointVolumeProtectionInfo>();

            ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, rpSystemId);
            RecoverPointClient rp = RPHelper.getRecoverPointClient(rpSystem);

            for (URI volumeId : volumeIds) {
                Volume volume = _dbClient.queryObject(Volume.class, volumeId);
                RecoverPointVolumeProtectionInfo volumeProtectionInfo = rp
                        .getProtectionInfoForVolume(RPHelper.getRPWWn(volume.getId(), _dbClient));
                // Get the volume's source volume in order to determine if we are dealing with a MetroPoint
                // configuration.
                Volume sourceVolume = RPHelper.getRPSourceVolume(_dbClient, volume);
                VirtualPool virtualPool = _dbClient.queryObject(VirtualPool.class, sourceVolume.getVirtualPool());
                // Set the MetroPoint flag
                volumeProtectionInfo.setMetroPoint(VirtualPool.vPoolSpecifiesMetroPoint(virtualPool));
                volumeProtectionInfoList.add(volumeProtectionInfo);

                replicationSetNames.add(volume.getRSetName());
            }

            if (!volumeProtectionInfoList.isEmpty()) {
                rp.deleteReplicationSets(volumeProtectionInfoList);
            }

            // Update the workflow state.
            WorkflowStepCompleter.stepSucceded(token);
        } catch (Exception e) {
            _log.error(String.format("deleteRSetStep Failed - Replication Sets: %s", replicationSetNames.toString()));
            return stepFailed(token, e, "deleteRSetStep");
        }
        return true;
    }

    /**
     * Recreate the replication set
     *
     * @param rpSystem
     *            RP system
     * @param params
     *            parameters needed to create the CG
     * @param token
     *            the task
     * @return
     * @throws InternalException
     */
    public boolean recreateRSetStep(URI rpSystemId, List<URI> volumeIds, Map<String, RecreateReplicationSetRequestParams> rsetParams,
            String token) throws InternalException {

        List<String> replicationSetNames = new ArrayList<String>();
        RPHelper.setLinkStateWaitTimeOut(_coordinator);
        try {
            ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, rpSystemId);

            for (URI volumeId : volumeIds) {
                Volume volume = _dbClient.queryObject(Volume.class, volumeId);
                replicationSetNames.add(volume.getRSetName());
            }

            RecoverPointClient rp = RPHelper.getRecoverPointClient(rpSystem);
            _log.info("Sleeping for 15 seconds before rescanning bus to account for latencies.");
            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                _log.warn("Thread sleep interrupted.  Allowing to continue without sleep");
            }

            rp.recreateReplicationSets(rsetParams);

            // Update the workflow state.
            WorkflowStepCompleter.stepSucceded(token);
        } catch (Exception e) {
            _log.error(String.format("recreateRSetStep Failed - Replication Set(s): %s", replicationSetNames.toString()));
            return stepFailed(token, e, "recreateRSetStep");
        }
        return true;
    }

    /**
     * Create a protection set in the database that corresponds to this CG.
     *
     * @param params
     *            CG params object
     * @param cgId
     * @throws InternalException
     */
    private ProtectionSet createProtectionSet(ProtectionSystem rpSystem, CGRequestParams params, Long cgId) throws InternalException {
        ProtectionSet protectionSet = new ProtectionSet();

        protectionSet.setProtectionSystem(rpSystem.getId());
        protectionSet.setLabel(params.getCgName());
        protectionSet.setNativeGuid(rpSystem.getNativeGuid() + Constants.PLUS + cgId);
        protectionSet.setProtectionStatus(ProtectionStatus.ENABLED.toString());
        protectionSet.setProtectionId(cgId.toString());
        protectionSet.setId(URIUtil.createId(ProtectionSet.class));
        _dbClient.createObject(protectionSet);

        protectionSet = updateProtectionSet(protectionSet, params);

        return protectionSet;
    }

    /**
     * Update a protection set in the database that corresponds to the CG.
     *
     * BH Note: Currently this only supports adding to the protection set. We may eventually
     * want to support removal as well.
     *
     * @param params
     *            CG params object
     * @throws InternalException
     */
    private ProtectionSet updateProtectionSet(ProtectionSet protectionSet, CGRequestParams params) throws InternalException {
        StringSet protectionSetVolumes = new StringSet();
        _log.info(String.format("Updating protection set [%s]", protectionSet.getLabel()));

        // Keep a list of all volumes that were created. This will be used to ensure we do not
        // consider volumes from this create request when setting access state and link status
        // based on existing volumes in the CG.
        List<URI> volumesInCreateRequest = new ArrayList<URI>();
        for (CreateRSetParams rset : params.getRsets()) {
            for (CreateVolumeParams volume : rset.getVolumes()) {
                volumesInCreateRequest.add(volume.getVolumeURI());
            }
        }

        // Loop through the RSet volumes to update the protection set info and potentially add the volume to the
        // protection set
        for (CreateRSetParams rset : params.getRsets()) {
            for (CreateVolumeParams volume : rset.getVolumes()) {
                if (protectionSet.getVolumes() != null && protectionSet.getVolumes().contains(volume.getVolumeURI().toString())) {
                    // Protection Set already has a reference to this volume, continue.
                    continue;
                } else {
                    Volume vol = _dbClient.queryObject(Volume.class, volume.getVolumeURI());
                    // Set the project of the Protection Set from the volume if it
                    // hasn't already been set.
                    if (protectionSet.getProject() == null) {
                        protectionSet.setProject(vol.getProject().getURI());
                    }
                    vol.setProtectionSet(new NamedURI(protectionSet.getId(), protectionSet.getLabel()));

                    if (vol.checkPersonality(Volume.PersonalityTypes.SOURCE.toString())) {
                        vol.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                        vol.setLinkStatus(Volume.LinkStatus.IN_SYNC.name());

                        // Check the CG for an existing source volume. If the CG has an existing
                        // source volume, we want to mirror the access state and link status for
                        // all new source volumes.
                        List<Volume> existingCgSourceVolumes = RPHelper.getCgSourceVolumes(vol.getConsistencyGroup(), _dbClient);

                        if (existingCgSourceVolumes != null) {
                            for (Volume sourceVolume : existingCgSourceVolumes) {
                                if (!vol.getId().equals(sourceVolume.getId()) && !volumesInCreateRequest.contains(sourceVolume.getId())) {
                                    _log.info(String
                                            .format("Updating source volume %s. Setting access state = %s, link status = %s.  Based on existing CG source volume %s.",
                                                    vol.getId(), sourceVolume.getAccessState(), sourceVolume.getLinkStatus(),
                                                    sourceVolume.getId()));
                                    vol.setAccessState(sourceVolume.getAccessState());
                                    vol.setLinkStatus(sourceVolume.getLinkStatus());
                                    break;
                                }
                            }
                        }
                    } else if (vol.checkPersonality(Volume.PersonalityTypes.TARGET.toString())) {
                        vol.setAccessState(Volume.VolumeAccessState.NOT_READY.name());
                        vol.setLinkStatus(Volume.LinkStatus.IN_SYNC.name());

                        // Check the CG for an existing target volume corresponding to the same RP copy
                        // as this volume and mirror its access state and link statues. If the target
                        // copy happens to be in direct access mode, it's important that the new volume
                        // be marked FAILED_OVER and READWRITE.
                        List<Volume> existingCgTargets = RPHelper.getTargetVolumesForVarray(_dbClient, vol.getConsistencyGroup(),
                                vol.getVirtualArray());
                        if (existingCgTargets != null && vol.getRpCopyName() != null) {
                            for (Volume targetVolume : existingCgTargets) {
                                // If we have found a target volume that isn't the same and the RP copy matches,
                                // lets use it to set the access state and link status values.
                                if (!vol.getId().equals(targetVolume.getId())
                                        && !volumesInCreateRequest.contains(targetVolume.getId())
                                        && vol.getRpCopyName().equalsIgnoreCase(targetVolume.getRpCopyName())) {
                                    _log.info(String
                                            .format(
                                                    "Updating volume %s. Setting access state = %s, link status = %s.  Based on existing CG target volume %s.",
                                                    vol.getId(), targetVolume.getAccessState(), targetVolume.getLinkStatus(),
                                                    targetVolume.getId()));
                                    vol.setAccessState(targetVolume.getAccessState());
                                    vol.setLinkStatus(targetVolume.getLinkStatus());
                                    break;
                                }
                            }
                        }
                    }
                    _dbClient.updateObject(vol);
                    protectionSetVolumes.add(vol.getId().toString());
                    _log.info(String.format("Adding volume [%s] to protection set [%s]", vol.getLabel(), protectionSet.getLabel()));
                }
            }
        }

        // Loop through the Copy volumes to update the protection set info and potentially add the volume to the
        // protection set
        for (CreateCopyParams copy : params.getCopies()) {
            for (CreateVolumeParams volume : copy.getJournals()) {
                if (protectionSet.getVolumes() != null && protectionSet.getVolumes().contains(volume.getVolumeURI().toString())) {
                    // Protection Set already has a reference to this volume, continue.
                    continue;
                } else {
                    Volume vol = _dbClient.queryObject(Volume.class, volume.getVolumeURI());
                    vol.setProtectionSet(new NamedURI(protectionSet.getId(), protectionSet.getLabel()));
                    vol.setAccessState(Volume.VolumeAccessState.NOT_READY.name());
                    _dbClient.updateObject(vol);
                    protectionSetVolumes.add(vol.getId().toString());
                    _log.info(String.format("Adding volume [%s] to protection set [%s]", vol.getLabel(), protectionSet.getLabel()));
                }
            }
        }

        if (protectionSet.getVolumes() == null) {
            protectionSet.setVolumes(protectionSetVolumes);
        } else {
            protectionSet.getVolumes().addAll(protectionSetVolumes);
        }

        _dbClient.updateObject(protectionSet);

        return protectionSet;
    }

    private ProtectionSystem getRPSystem(URI protectionDevice) throws InternalException {
        ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, protectionDevice);
        // Verify non-null storage device returned from the database client.
        if (rpSystem == null) {
            throw DeviceControllerExceptions.recoverpoint.failedConnectingForMonitoring(protectionDevice);
        }
        return rpSystem;
    }

    /**
     * Find the export group associated with this volume and this protection system.
     *
     * @param rpSystem
     * @param volumeUri
     * @param virtualArrayUri
     * @param internalSiteName
     * @return
     * @throws InternalException
     */
    private ExportGroup getExportGroup(ProtectionSystem rpSystem, URI volumeUri, URI virtualArrayUri, String internalSiteName)
            throws InternalException {
        _log.info(String.format("getExportGroup start: for volume %s - internal site name %s - va %s", volumeUri, internalSiteName,
                virtualArrayUri.toString()));

        // Get all exportGroups that this "volumeUri" is a part of.
        URIQueryResultList exportGroupURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getBlockObjectExportGroupConstraint(volumeUri), exportGroupURIs);

        for (URI exportURI : exportGroupURIs) {
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportURI);
            if (exportGroup == null || exportGroup.getVolumes() == null) {
                continue;
            }

            // The Export Group we're looking for will have:
            // 1. The Volume associated to it.
            // 2. Have the same Virtual Array as the Volume.
            // 3. Have the Initiators for the Volumes RP internal site in it.
            if (exportGroup.getVolumes().containsKey(volumeUri.toString()) && exportGroup.getVirtualArray().equals(virtualArrayUri)) {

                // Get the Initiators from the Export Group
                List<String> exportWWNs = new ArrayList<String>();
                if (exportGroup.getInitiators() != null) {
                    for (String exportWWN : exportGroup.getInitiators()) {
                        URI exportWWNURI = URI.create(exportWWN);
                        Initiator initiator = _dbClient.queryObject(Initiator.class, exportWWNURI);
                        exportWWNs.add(initiator.getInitiatorNode());
                        exportWWNs.add(initiator.getInitiatorPort());
                    }
                }

                // Get the Initiators from the Protection System for the Volumes RP internal site
                // NOTE: Sometimes the URI is still in the DB, but the object isn't. (I found this happens when a
                // previous create export
                // group
                // workflow failed. It creates the object in the DB but it subsequently gets deleted)
                StringSet rpWWNs = rpSystem.getSiteInitiators().get(internalSiteName);
                if (rpWWNs == null) {
                    _log.error("Couldn't find site initiators for rp cluster: " + internalSiteName);
                    _log.error("RP Site Initiators: {}" + rpSystem.getSiteInitiators().toString());
                    return null;
                }

                // Check to see if the Export Group has at least one of the RP Initiators we're looking for, if so,
                // return
                // the Export Group
                for (String rpWWN : rpWWNs) {
                    for (String exportWWN : exportWWNs) {
                        if (exportWWN.equalsIgnoreCase(rpWWN)) {
                            _log.info(String.format("Found exportGroup matching varray and rpSite for volume %s : %s - %s",
                                    volumeUri.toString(), exportGroup.getGeneratedName(), exportGroup.getLabel()));
                            return exportGroup;
                        }
                    }
                }
            }
        }
        _log.info("getExportGroup: group does NOT exist");
        return null;
    }

    /**
     * Using the passed in export group, try to find a matching one based on generated name. Return
     * null if it can't be found meaning we should create a new one.
     *
     * @param exportGroupToFind
     *            The export group to find
     * @return The found export group, or null if it doesn't exist
     * @throws InternalException
     */
    private ExportGroup exportGroupExistsInDB(ExportGroup exportGroupToFind) throws InternalException {
        // Query for all existing Export Groups, a little expensive.
        List<URI> allActiveExportGroups = _dbClient.queryByType(ExportGroup.class, true);
        for (URI exportGroupURI : allActiveExportGroups) {
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            // Sometimes the URI is still in the DB, but the object isn't is marked for deletion so
            // we need to check to see if it's active as well if the names match. Also make sure
            // it's for the same project.
            if (exportGroup != null && !exportGroup.getInactive()
                    && exportGroup.getProject().getURI().equals(exportGroupToFind.getProject().getURI())) {
                // Ensure backwards compatibility by formatting the existing generated name to the same as the
                // potential new one.
                // We're looking for a format of:
                // rpSystem.getNativeGuid() + "_" + storageSystem.getLabel() + "_" + rpSiteName + "_" +
                // varray.getLabel()
                // and replacing all non alpha-numerics with "" (except "_").
                String generatedName = exportGroup.getGeneratedName().trim().replaceAll(ALPHA_NUMERICS, "");
                if (generatedName.equals(exportGroupToFind.getGeneratedName())) {
                    _log.info("Export Group already exists in database.");
                    return exportGroup;
                }
            }
        }
        _log.info("Export Group does NOT already exist in database.");
        return null;
    }

    @Override
    public void
            updateConsistencyGroupPolicy(URI protectionDevice, URI consistencyGroup, List<URI> volumeURIs, URI newVpoolURI, String task)
                    throws InternalException {
        _log.info(String.format("Request to update consistency group policy for volumes %s through virtual pool change to %s", volumeURIs,
                newVpoolURI));
        VolumeVpoolChangeTaskCompleter taskCompleter = null;
        URI oldVpoolURI = null;
        List<Volume> volumes = new ArrayList<Volume>();
        List<Volume> vplexBackendVolumes = new ArrayList<Volume>();
        try {
            // Get all CG source volumes. The entire CG policy is being updated so we
            // need to capture the existing vpools for all the source volumes before
            // changing them.
            List<Volume> cgVolumes = RPHelper.getCgSourceVolumes(consistencyGroup, _dbClient);

            VirtualPool newVpool = _dbClient.queryObject(VirtualPool.class, newVpoolURI);
            Map<URI, URI> oldVpools = new HashMap<URI, URI>();

            for (Volume volume : cgVolumes) {
                // Save the old virtual pool
                oldVpoolURI = volume.getVirtualPool();
                oldVpools.put(volume.getId(), oldVpoolURI);

                // Update to the new virtual pool
                volume.setVirtualPool(newVpoolURI);
                volumes.add(volume);

                // If this is a VPlex volume, there will be
                StringSet associatedVolumeIds = volume.getAssociatedVolumes();

                // Perform additional tasks if this volume is a VPlex volume
                if (associatedVolumeIds != null && !associatedVolumeIds.isEmpty()) {
                    Volume backendSrc = null;
                    Volume backendHa = null;

                    for (String associatedVolumeId : associatedVolumeIds) {
                        Volume associatedVolume = _dbClient.queryObject(Volume.class, URI.create(associatedVolumeId));
                        // Assign the associated volumes to either be the source or HA
                        if (associatedVolume != null) {
                            if (associatedVolume.getVirtualArray().equals(volume.getVirtualArray())) {
                                backendSrc = associatedVolume;
                            } else {
                                backendHa = associatedVolume;
                            }
                        }
                    }

                    if (backendSrc != null) {
                        // Change the back end volume's vPool too
                        backendSrc.setVirtualPool(newVpoolURI);
                        vplexBackendVolumes.add(backendSrc);
                        _log.info(String.format("Changing VirtualPool for VPLEX backend source volume %s (%s) from %s to %s",
                                backendSrc.getLabel(), backendSrc.getId(), oldVpoolURI, newVpoolURI));

                        if (backendHa != null) {
                            VirtualPool newHAVpool = VirtualPool.getHAVPool(newVpool, _dbClient);
                            if (newHAVpool == null) { // it may not be set
                                newHAVpool = newVpool;
                            }
                            backendHa.setVirtualPool(newHAVpool.getId());
                            vplexBackendVolumes.add(backendHa);
                        }
                    }
                }
            }

            _dbClient.updateObject(volumes);
            _dbClient.updateObject(vplexBackendVolumes);

            // The VolumeVpoolChangeTaskCompleter will restore the old Virtual Pool
            taskCompleter = new VolumeVpoolChangeTaskCompleter(volumeURIs, oldVpools, task);
        } catch (Exception ex) {
            _log.error("Unexpected exception reading volume or generating taskCompleter: ", ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            VolumeWorkflowCompleter completer = new VolumeWorkflowCompleter(volumeURIs, task);
            completer.error(_dbClient, serviceError);
        }

        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, "updateReplicationMode", false, task);

            ProtectionSystem protectionSystem = _dbClient.queryObject(ProtectionSystem.class, protectionDevice);

            if (!volumes.isEmpty()) {
                VirtualPool newVirtualPool = _dbClient.queryObject(VirtualPool.class, newVpoolURI);
                // Add workflow step
                addUpdateConsistencyGroupPolicyStep(workflow, protectionSystem, consistencyGroup, newVirtualPool.getRpCopyMode());
            }
            if (!workflow.getAllStepStatus().isEmpty()) {
                _log.info("The updateAutoTieringPolicy workflow has {} step(s). Starting the workflow.",
                        workflow.getAllStepStatus().size());

                workflow.executePlan(taskCompleter, "Updated the consistency group policy successfully.");
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception ex) {
            _log.error("Unexpected exception: ", ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    /**
     * Add workflow step for updating consistency group policy.
     *
     * @param workflow
     *            Workflow
     * @param protectionSystem
     *            Protection System
     * @param cgUri
     *            the consistency group URI
     * @param copyMode
     *            the requested copy mode (SYNCHRONOUS or ASYNCHRONOUS)
     * @return the step identifier
     */
    public String addUpdateConsistencyGroupPolicyStep(Workflow workflow, ProtectionSystem protectionSystem, URI cgUri, String copyMode)
            throws InternalException {

        String stepId = workflow.createStepId();
        Workflow.Method updateCgPolicyMethod = new Workflow.Method(METHOD_UPDATE_CG_POLICY_STEP, protectionSystem, cgUri, copyMode);

        workflow.createStep(STEP_UPDATE_CG_POLICY,
                String.format("Create subtask to update replication mode for CG %s to %s: ", cgUri, copyMode), null,
                protectionSystem.getId(), protectionSystem.getSystemType(), this.getClass(), updateCgPolicyMethod,
                rollbackMethodNullMethod(), stepId);

        _log.info(String.format("Added update consistency group replication mode step [%s] in workflow", stepId));

        return STEP_UPDATE_CG_POLICY;
    }

    /**
     * Updates the consistency group policy (replication mode).
     *
     * @param protectionSystem
     *            the RP protection system
     * @param cgUri
     *            the consistency group ID
     * @param copyMode
     *            the copy/replication mode (sync or async)
     * @param stepId
     *            the step ID
     * @return true if the step executes successfully, false otherwise.
     */
    public boolean updateConsistencyGroupPolicyStep(ProtectionSystem protectionSystem, URI cgUri, String copyMode, String stepId) {
        try {
            _log.info(String.format("Updating consistency group policy for CG %s.", cgUri));
            WorkflowStepCompleter.stepExecuting(stepId);

            RecoverPointClient rp = RPHelper.getRecoverPointClient(protectionSystem);
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgUri);

            // lock around update policy operations on the same CG
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getConsistencyGroupStorageKey(_dbClient, cgUri, protectionSystem.getId()));
            boolean lockAcquired = _workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.RP_CG));
            if (!lockAcquired) {
                throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(),
                        String.format("Upgrade policy for RP consistency group %s; id: %s", cg.getLabel(), cgUri.toString()));
            }

            CGPolicyParams policyParams = new CGPolicyParams(copyMode);
            UpdateCGPolicyParams updateParams = new UpdateCGPolicyParams(cg.getCgNameOnStorageSystem(protectionSystem.getId()),
                    policyParams);
            rp.updateConsistencyGroupPolicy(updateParams);

            // Update the workflow state.
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            _log.error("Operation failed with Exception: ", e);
            return stepFailed(stepId, (ServiceCoded) e, "updateConsistencyGroupPolicyStep");
        } catch (Exception e) {
            _log.error("Operation failed with Exception: ", e);
            return stepFailed(stepId, e, "updateConsistencyGroupPolicyStep");
        }

        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.RPController#stopProtection(java.net.URI, java.net.URI, java.lang.String)
     */
    @Override
    public void performProtectionOperation(URI protectionSystem, URI id, URI copyID, String pointInTime, String imageAccessMode, String op,
            String task) throws ControllerException {
        RPCGProtectionTaskCompleter taskCompleter = null;
        try {

            if (URIUtil.isType(id, Volume.class)) {
                taskCompleter = new RPCGProtectionTaskCompleter(id, Volume.class, task);
            } else if (URIUtil.isType(id, BlockConsistencyGroup.class)) {
                taskCompleter = new RPCGProtectionTaskCompleter(id, BlockConsistencyGroup.class, task);
            }

            ProtectionSystem rpSystem = getRPSystem(protectionSystem);

            // set the protection volume to the source volume if the copyID is null (operation is performed on all
            // copies)
            // otherwise set it to the volume referenced by the copyID (operation is performed on specifc copy)
            Volume protectionVolume = (copyID == null) ? _dbClient.queryObject(Volume.class, id)
                    : _dbClient.queryObject(Volume.class, copyID);

            if (op.equals(STOP)) {
                taskCompleter.setOperationTypeEnum(OperationTypeEnum.STOP_RP_LINK);
            } else if (op.equals(START)) {
                taskCompleter.setOperationTypeEnum(OperationTypeEnum.START_RP_LINK);
            } else if (op.equals(SYNC)) {
                taskCompleter.setOperationTypeEnum(OperationTypeEnum.SYNC_RP_LINK);
            } else if (op.equals(PAUSE)) {
                taskCompleter.setOperationTypeEnum(OperationTypeEnum.PAUSE_RP_LINK);
            } else if (op.equals(RESUME)) {
                taskCompleter.setOperationTypeEnum(OperationTypeEnum.RESUME_RP_LINK);
            } else if (op.equals(FAILOVER_TEST)) {
                taskCompleter.setOperationTypeEnum(OperationTypeEnum.FAILOVER_TEST_RP_LINK);
            } else if (op.equals(FAILOVER)) {
                if (protectionVolume.getLinkStatus() != null
                        && protectionVolume.getLinkStatus().equalsIgnoreCase(Volume.LinkStatus.FAILED_OVER.name())) {
                    taskCompleter.setOperationTypeEnum(OperationTypeEnum.FAILOVER_CANCEL_RP_LINK);
                } else {
                    taskCompleter.setOperationTypeEnum(OperationTypeEnum.FAILOVER_RP_LINK);
                }
            } else if (op.equals(FAILOVER_CANCEL)) {
                taskCompleter.setOperationTypeEnum(OperationTypeEnum.FAILOVER_CANCEL_RP_LINK);
            } else if (op.equals(SWAP)) {
                taskCompleter.setOperationTypeEnum(OperationTypeEnum.SWAP_RP_VOLUME);
            } else if (op.equals(FAILOVER_TEST_CANCEL)) {
                taskCompleter.setOperationTypeEnum(OperationTypeEnum.FAILOVER_TEST_CANCEL_RP_LINK);
            } else if (op.equals(CHANGE_ACCESS_MODE)) {
                taskCompleter.setOperationTypeEnum(OperationTypeEnum.CHANGE_RP_IMAGE_ACCESS_MODE);
            } else {
                taskCompleter.error(_dbClient, _locker, DeviceControllerErrors.recoverpoint.methodNotSupported());
                return;
            }

            // Set up workflow steps.
            Workflow workflow = _workflowService.getNewWorkflow(this, "performProtectionOperation", true, task);

            // add step to perform protection operation
            String stepId = workflow.createStepId();
            Workflow.Method performProtOpExecuteMethod = new Workflow.Method(METHOD_PERFORM_PROTECTION_OPERATION, protectionSystem,
                    protectionVolume.getConsistencyGroup(), id, copyID, pointInTime, imageAccessMode, op);

            workflow.createStep(STEP_PERFORM_PROTECTION_OPERATION,
                    String.format("Performing protection operation %s on RP volume: %s", op, id.toString()),
                    null, rpSystem.getId(), rpSystem.getSystemType(), this.getClass(), performProtOpExecuteMethod, null,
                    stepId);

            String successMessage = String.format("Successfully performed protection operation %s on RP volume: %s", op, id.toString());
            workflow.executePlan(taskCompleter, successMessage);

        } catch (Exception e) {
            _log.error("Operation failed with Exception: ", e);
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, _locker, DeviceControllerException.errors.jobFailed(e));
            }
        }
    }

    /**
     * Workflow step to perform RP protection operation
     *
     * @param protectionSystem
     * @param cgId
     * @param volId
     * @param copyID
     * @param pointInTime
     * @param imageAccessMode
     * @param op
     * @param stepId
     * @return
     * @throws ControllerException
     */
    public boolean performProtectionOperationStep(URI protectionSystem, URI cgId, URI volId, URI copyID, String pointInTime,
            String imageAccessMode, String op,
            String stepId) throws ControllerException {
        WorkflowStepCompleter.stepExecuting(stepId);
        RPHelper.setLinkStateWaitTimeOut(_coordinator);
        try {

            ProtectionSystem rpSystem = getRPSystem(protectionSystem);

            // Take out a workflow step lock on the CG
            _workflowService.getWorkflowFromStepId(stepId);
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getConsistencyGroupStorageKey(_dbClient, cgId, rpSystem.getId()));
            boolean lockAcquired = _workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.RP_CG));
            if (!lockAcquired) {
                throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(),
                        String.format("failed to get lock while restoring volumes in RP consistency group: %s", cgId.toString()));
            }

            // set the protection volume to the source volume if the copyID is null (operation is performed on all
            // copies)
            // otherwise set it to the volume referenced by the copyID (operation is performed on specifc copy)
            Volume protectionVolume = (copyID == null) ? _dbClient.queryObject(Volume.class, volId)
                    : _dbClient.queryObject(Volume.class, copyID);

            RecoverPointClient rp = RPHelper.getRecoverPointClient(rpSystem);

            RecoverPointVolumeProtectionInfo volumeProtectionInfo = rp
                    .getProtectionInfoForVolume(RPHelper.getRPWWn(protectionVolume.getId(), _dbClient));

            if (op.equals(STOP)) {
                rp.disableProtection(volumeProtectionInfo);
                setProtectionSetStatus(volumeProtectionInfo, ProtectionStatus.DISABLED.toString(), rpSystem);
                _log.info("doStopProtection {} - complete", rpSystem.getId());
            } else if (op.equals(START)) {
                rp.enableProtection(volumeProtectionInfo);
                setProtectionSetStatus(volumeProtectionInfo, ProtectionStatus.ENABLED.toString(), rpSystem);
            } else if (op.equals(SYNC)) {
                Set<String> volumeWWNs = new HashSet<String>();
                volumeWWNs.add(RPHelper.getRPWWn(protectionVolume.getId(), _dbClient));
                // Create and enable a temporary bookmark for the volume associated with this volume
                CreateBookmarkRequestParams request = new CreateBookmarkRequestParams();
                request.setVolumeWWNSet(volumeWWNs);
                request.setBookmark("Sync-Snapshot");
                rp.createBookmarks(request);
            } else if (op.equals(PAUSE)) {
                rp.pauseTransfer(volumeProtectionInfo);
                setProtectionSetStatus(volumeProtectionInfo, ProtectionStatus.PAUSED.toString(), rpSystem);
            } else if (op.equals(RESUME)) {
                rp.resumeTransfer(volumeProtectionInfo);
                setProtectionSetStatus(volumeProtectionInfo, ProtectionStatus.ENABLED.toString(), rpSystem);
            } else if (op.equals(FAILOVER_TEST)) {
                RPCopyRequestParams copyParams = new RPCopyRequestParams();
                copyParams.setCopyVolumeInfo(volumeProtectionInfo);
                rp.failoverCopyTest(copyParams);
            } else if (op.equals(FAILOVER)) {
                // If the "protectionVolume" is a source personality volume, we're probably dealing with a failover
                // cancel.
                if (protectionVolume.getLinkStatus() != null
                        && protectionVolume.getLinkStatus().equalsIgnoreCase(Volume.LinkStatus.FAILED_OVER.name())) {
                    // TODO: ViPR 2.0 needs to support this.
                    // TODO BEGIN: allow re-failover perform the same as a failback in 2.0 since the UI support will not
                    // be there to do a
                    // swap or cancel.
                    // Jira CTRL-2773: Once UI adds support for /swap and /failover-cancel, we can remove this and
                    // replace with an error.
                    // If protectionVolume is a source, then the "source" sent in must be a target. Verify.
                    Volume targetVolume = null;
                    if (protectionVolume.checkPersonality(Volume.PersonalityTypes.SOURCE.toString())) {
                        targetVolume = _dbClient.queryObject(Volume.class, volId);
                    } else {
                        targetVolume = protectionVolume;
                    }

                    // Disable the image access that is in effect.
                    volumeProtectionInfo = rp.getProtectionInfoForVolume(RPHelper.getRPWWn(targetVolume.getId(), _dbClient));
                    RPCopyRequestParams copyParams = new RPCopyRequestParams();
                    copyParams.setCopyVolumeInfo(volumeProtectionInfo);
                    rp.failoverCopyCancel(copyParams);
                    // Set the flags back to where they belong.
                    updatePostFailoverCancel(targetVolume);
                    // TODO END
                    // Replace with this error: taskCompleter.error(_dbClient, _locker,
                    // DeviceControllerErrors.recoverpoint.stepFailed("performFailoverOperation: source volume specified
                    // for failover where target volume specified is not in failover state"));
                } else {
                    // Standard failover case.
                    RPCopyRequestParams copyParams = new RPCopyRequestParams();
                    copyParams.setCopyVolumeInfo(volumeProtectionInfo);

                    if (pointInTime != null) {
                        // Build a Date reference.
                        copyParams.setApitTime(TimeUtils.getDateTimestamp(pointInTime));
                    }

                    rp.failoverCopy(copyParams);
                    updatePostFailover(protectionVolume);
                }
            } else if (op.equals(FAILOVER_CANCEL)) {
                // If the "protectionVolume" is a source personality volume, we're probably dealing with a failover
                // cancel.
                if (protectionVolume.checkPersonality(Volume.PersonalityTypes.SOURCE.name())) {
                    throw DeviceControllerExceptions.recoverpoint.failoverWrongTargetSpecified();
                } else {
                    if (protectionVolume.getLinkStatus() != null
                            && protectionVolume.getLinkStatus().equalsIgnoreCase(Volume.LinkStatus.FAILED_OVER.name())) {
                        // Disable the image access that is in effect.
                        volumeProtectionInfo = rp.getProtectionInfoForVolume(RPHelper.getRPWWn(protectionVolume.getId(), _dbClient));
                        RPCopyRequestParams copyParams = new RPCopyRequestParams();
                        copyParams.setCopyVolumeInfo(volumeProtectionInfo);
                        rp.failoverCopyCancel(copyParams);
                        // Set the flags back to where they belong.
                        updatePostFailoverCancel(protectionVolume);
                    } else {
                        // Illegal condition, you sent down a target volume that's a source where the target is not a
                        // failed over target.
                        throw DeviceControllerExceptions.recoverpoint.failoverWrongTargetSpecified();
                    }
                }
            } else if (op.equals(SWAP)) {
                RPCopyRequestParams copyParams = new RPCopyRequestParams();
                copyParams.setCopyVolumeInfo(volumeProtectionInfo);
                rp.swapCopy(copyParams);
                protectionVolume = updatePostSwapPersonalities(protectionVolume);
                rp.setCopyAsProduction(copyParams);

                // if metropoint:
                // 1. delete the standby CDP copy
                // 2. add back the standby production copy
                // 3. add back the standby CDP copy
                if (RPHelper.isMetroPointVolume(_dbClient, protectionVolume)) {
                    // If the standby production copy does not exist, it means we are in a swapped state and need
                    // to reconstruct the standby production copy during swap-back. If the standby production
                    // copy exists, we can skip all the CG reconstruction steps.
                    if (!rp.doesStandbyProdCopyExist(volumeProtectionInfo)) {
                        _log.info(String
                                .format(
                                        "Adding back standby production copy after swap back to original VPlex Metro for Metropoint volume %s (%s)",
                                        protectionVolume.getLabel(), protectionVolume.getId().toString()));

                        List<Volume> standbyLocalCopyVols = RPHelper.getMetropointStandbyCopies(protectionVolume, _dbClient);
                        CreateCopyParams standbyLocalCopyParams = null;
                        List<CreateRSetParams> rSets = new ArrayList<CreateRSetParams>();
                        Set<URI> journalVolumes = new HashSet<URI>();
                        List<String> deletedCopies = new ArrayList<>();
                        if (!standbyLocalCopyVols.isEmpty()) {
                        	for (Volume standbyCopyVol : standbyLocalCopyVols) {
                                
                        		//For case where there are multiple Rsets, we should invoke deleteCopy only once, it shall
                        		//remove the related copy information from other Rsets as well. rpCopyName is same across
                        		//all Rpsets, so track the delete based on rpCopyName
                        		String rpCopyName = standbyCopyVol.getRpCopyName().trim();
                        		if(!deletedCopies.contains(rpCopyName)) {
                        			// 1. delete the standby CDP copy if it exists
                        			 if (rp.doesProtectionVolumeExist(RPHelper.getRPWWn(standbyCopyVol.getId(), _dbClient))) {
                                         RecoverPointVolumeProtectionInfo standbyCdpCopy = rp
                                                 .getProtectionInfoForVolume(RPHelper.getRPWWn(standbyCopyVol.getId(), _dbClient));
                                         rp.deleteCopy(standbyCdpCopy);
                                         deletedCopies.add(rpCopyName);
                                        
                                     }
                        		}                               

                                // set up volume info for the standby copy volume
                                CreateVolumeParams vol = new CreateVolumeParams();
                                vol.setWwn(RPHelper.getRPWWn(standbyCopyVol.getId(), _dbClient));
                                vol.setInternalSiteName(standbyCopyVol.getInternalSiteName());
                                vol.setProduction(false);
                                List<CreateVolumeParams> volumes = new ArrayList<CreateVolumeParams>();
                                volumes.add(vol);
                                CreateRSetParams rSet = new CreateRSetParams();
                                rSet.setName(standbyCopyVol.getRSetName());
                                rSet.setVolumes(volumes);
                                rSets.add(rSet);

                                List<Volume> standbyJournals = RPHelper.findExistingJournalsForCopy(_dbClient,
                                        standbyCopyVol.getConsistencyGroup(), standbyCopyVol.getRpCopyName());

                                // compile a unique set of journal volumes
                                for (Volume standbyJournal : standbyJournals) {
                                    journalVolumes.add(standbyJournal.getId());
                                }
                            }

                            // prepare journal volumes info
                            String rpCopyName = null;
                            List<CreateVolumeParams> journalVols = new ArrayList<CreateVolumeParams>();
                            for (URI journalVolId : journalVolumes) {
                                Volume standbyLocalJournal = _dbClient.queryObject(Volume.class, journalVolId);
                                if (standbyLocalJournal != null) {
                                    _log.info(String.format("Found standby local journal volume %s (%s) for metropoint volume %s (%s)",
                                            standbyLocalJournal.getLabel(), standbyLocalJournal.getId().toString(),
                                            protectionVolume.getLabel(), protectionVolume.getId().toString()));
                                    rpCopyName = standbyLocalJournal.getRpCopyName();
                                    CreateVolumeParams journalVolParams = new CreateVolumeParams();
                                    journalVolParams.setWwn(RPHelper.getRPWWn(standbyLocalJournal.getId(), _dbClient));
                                    journalVolParams.setInternalSiteName(standbyLocalJournal.getInternalSiteName());
                                    journalVols.add(journalVolParams);
                                }
                            }

                            // if we found any journal volumes, add them to the local copies list
                            if (!journalVols.isEmpty()) {
                                standbyLocalCopyParams = new CreateCopyParams();
                                standbyLocalCopyParams.setName(rpCopyName);
                                standbyLocalCopyParams.setJournals(journalVols);
                            } else {
                                _log.error("no journal volumes found for standby production copy for source volume "
                                        + protectionVolume.getLabel());
                            }
                        }

                        String standbyProductionCopyName = RPHelper.getStandbyProductionCopyName(_dbClient, protectionVolume);
                        // Build standby production journal
                        if (standbyProductionCopyName != null) {
                            List<Volume> existingStandbyJournals = RPHelper.findExistingJournalsForCopy(_dbClient,
                                    protectionVolume.getConsistencyGroup(), standbyProductionCopyName);

                            // Get the first standby production journal
                            Volume standbyProdJournal = existingStandbyJournals.get(0);

                            if (standbyProdJournal != null) {
                                _log.info(String.format("Found standby production journal volume %s (%s) for metropoint volume %s (%s)",
                                        standbyProdJournal.getLabel(), standbyProdJournal.getId().toString(), protectionVolume.getLabel(),
                                        protectionVolume.getId().toString()));
                                List<CreateVolumeParams> journalVols = new ArrayList<CreateVolumeParams>();
                                CreateVolumeParams journalVolParams = new CreateVolumeParams();
                                journalVolParams.setWwn(RPHelper.getRPWWn(standbyProdJournal.getId(), _dbClient));
                                journalVolParams.setInternalSiteName(standbyProdJournal.getInternalSiteName());
                                journalVols.add(journalVolParams);

                                CreateCopyParams standbyProdCopyParams = new CreateCopyParams();
                                standbyProdCopyParams.setName(standbyProdJournal.getRpCopyName());
                                standbyProdCopyParams.setJournals(journalVols);

                                // 2. and 3. add back the standby production copy; add back the standby CDP copy
                                rp.addStandbyProductionCopy(standbyProdCopyParams, standbyLocalCopyParams, rSets, copyParams);
                            } else {
                                _log.error(String
                                        .format(
                                                "Cannot add standby production copy because the standby production journal could not be found for copy %s.",
                                                standbyProductionCopyName));
                            }
                        }
                    }
                }

                // Wait for RP to remove copy snapshots. There is a sleep here because we do not 'wait' for the RP failover
                // component of the swap operation to complete. There were issues with this which are documented in the
                // RecoverPointClient. So we sleep here in order to give RP the time it needs to remove the swap target
                // copy bookmarks before we cleanup the corresponding BlockSnapshot objects from ViPR.
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // When we perform a swap, the target swap copy will become the production copy and lose all
                // its bookmarks so we need to sync with RP.
                RPHelper.cleanupSnapshots(_dbClient, rpSystem);

            } else if (op.equals(FAILOVER_TEST_CANCEL)) {
                RPCopyRequestParams copyParams = new RPCopyRequestParams();
                copyParams.setCopyVolumeInfo(volumeProtectionInfo);
                rp.failoverCopyTestCancel(copyParams);
            } else if (op.equals(CHANGE_ACCESS_MODE)) {
                RPCopyRequestParams copyParams = new RPCopyRequestParams();
                copyParams.setCopyVolumeInfo(volumeProtectionInfo);
                copyParams.setImageAccessMode(imageAccessMode);
                if (imageAccessMode != null) {
                    rp.updateImageAccessMode(copyParams);

                    // RecoverPoint will remove all bookmarks on any copy set to direct access mode. We need to remove
                    // any
                    // BlockSnapshot objects that reference the target copy being set to direct access mode.
                    if (Copy.ImageAccessMode.DIRECT_ACCESS.name().equalsIgnoreCase(imageAccessMode)) {
                        _log.info(String
                                .format(
                                        "Updated imaged access mode for copy %s at %s to DIRECT_ACCESS. Removing all bookmarks associated with that copy and site.",
                                        volumeProtectionInfo.getRpCopyName(), volumeProtectionInfo.getRpSiteName()));
                        // Wait for RP to remove copy snapshots
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        RPHelper.cleanupSnapshots(_dbClient, rpSystem);
                    }

                } else {
                    throw DeviceControllerExceptions.recoverpoint.imageAccessModeNotSupported(imageAccessMode);
                }
            } else {
                throw DeviceControllerExceptions.recoverpoint.protectionOperationNotSupported(op);
            }

            _log.info("performProtectionOperation: after " + op + " operation successful");
            WorkflowStepCompleter.stepSucceded(stepId);
            return true;
        } catch (InternalException e) {
            _log.error("Operation failed with Exception: ", e);
            return stepFailed(stepId, (ServiceCoded) e, "removeProtection operation failed.");
        } catch (Exception e) {
            stepFailed(stepId, e, "removeProtection operation failed.");
            return false;
        }
    }

    /**
     * After a swap, we need to swap personalities of source and target volumes
     *
     * @param id
     *            volume we failed over to
     * @throws InternalException
     */
    private Volume updatePostSwapPersonalities(Volume volume) throws InternalException {
        _log.info("Changing personality of source and targets");
        ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());
        List<URI> volumeIDs = new ArrayList<URI>();
        for (String volumeString : protectionSet.getVolumes()) {
            URI volumeURI;
            try {
                volumeURI = new URI(volumeString);
                volumeIDs.add(volumeURI);
            } catch (URISyntaxException e) {
                _log.error("URI syntax incorrect: ", e);
            }
        }

        // Changing personalities means that the source was on "Copy Name A" and it's now on "Copy Name B":
        // 1. a. Any previous TARGET volume that matches the copy name of the incoming volume is now a SOURCE volume
        // b. That volume needs its RP Target volumes list filled-in as well; it's all of the devices that are
        // the same replication set name that aren't the new SOURCE volume itself.
        // 2. All SOURCE volumes are now TARGET volumes and their RP Target lists need to be null'd out
        //
        for (URI protectionVolumeID : volumeIDs) {
            Volume protectionVolume = _dbClient.queryObject(Volume.class, protectionVolumeID);
            if (protectionVolume == null || protectionVolume.getInactive()) {
                continue;
            }
            if ((protectionVolume.checkPersonality(Volume.PersonalityTypes.TARGET.toString()))
                    && (protectionVolume.getRpCopyName().equals(volume.getRpCopyName()))) {
                // This is a TARGET we failed over to. We need to build up all of its targets
                for (URI potentialTargetVolumeID : volumeIDs) {
                    Volume potentialTargetVolume = _dbClient.queryObject(Volume.class, potentialTargetVolumeID);
                    if (potentialTargetVolume == null || potentialTargetVolume.getInactive()) {
                        continue;
                    }
                    if (!potentialTargetVolume.checkPersonality(Volume.PersonalityTypes.METADATA.toString())
                            && NullColumnValueGetter.isNotNullValue(potentialTargetVolume.getRSetName())
                            && potentialTargetVolume.getRSetName().equals(protectionVolume.getRSetName())
                            && !potentialTargetVolumeID.equals(protectionVolume.getId())) {
                        if (protectionVolume.getRpTargets() == null) {
                            protectionVolume.setRpTargets(new StringSet());
                        }
                        protectionVolume.getRpTargets().add(String.valueOf(potentialTargetVolume.getId()));
                    }
                }

                _log.info("Change personality of failover target " + RPHelper.getRPWWn(protectionVolume.getId(), _dbClient) + " to source");
                protectionVolume.setPersonality(Volume.PersonalityTypes.SOURCE.toString());
                protectionVolume.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                protectionVolume.setLinkStatus(Volume.LinkStatus.IN_SYNC.name());
                _dbClient.updateObject(protectionVolume);
            } else if (protectionVolume.checkPersonality(Volume.PersonalityTypes.SOURCE.toString())) {
                _log.info("Change personality of source volume " + RPHelper.getRPWWn(protectionVolume.getId(), _dbClient) + " to target");
                protectionVolume.setPersonality(Volume.PersonalityTypes.TARGET.toString());
                protectionVolume.setAccessState(Volume.VolumeAccessState.NOT_READY.name());
                protectionVolume.setLinkStatus(Volume.LinkStatus.IN_SYNC.name());
                //Clear targets - For some reason RP targets are not getting cleared when it is being set with
                // new empty instance of StringSet(), hence getting rpTargets and clearing them and resetting
                StringSet resetRpTargets = protectionVolume.getRpTargets();
                resetRpTargets.clear();
                protectionVolume.setRpTargets(resetRpTargets);
                _dbClient.updateObject(protectionVolume);
            } else if (!protectionVolume.checkPersonality(Volume.PersonalityTypes.METADATA.toString())) {
                _log.info("Target " + RPHelper.getRPWWn(protectionVolume.getId(), _dbClient) + " is a target that remains a target");
                // TODO: Handle failover to CRR. Need to remove the CDP volumes (including journals)
            }
        }

        return _dbClient.queryObject(Volume.class, volume.getId());
    }

    /**
     * After a failover, we need to set specific flags
     *
     * @param id
     *            volume we failed over to
     * @throws InternalException
     */
    private void updatePostFailover(Volume volume) throws InternalException {
        _log.info("Setting respective flags after failover");
        ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());
        List<URI> volumeIDs = new ArrayList<URI>();
        for (String volumeString : protectionSet.getVolumes()) {
            URI volumeURI;
            try {
                volumeURI = new URI(volumeString);
                volumeIDs.add(volumeURI);
            } catch (URISyntaxException e) {
                _log.error("URI syntax incorrect: ", e);
            }
        }

        for (URI protectionVolumeID : volumeIDs) {
            Volume protectionVolume = _dbClient.queryObject(Volume.class, protectionVolumeID);
            if (protectionVolume == null || protectionVolume.getInactive()) {
                continue;
            }
            if ((protectionVolume.checkPersonality(Volume.PersonalityTypes.TARGET.toString()))
                    && (protectionVolume.getRpCopyName().equals(volume.getRpCopyName()))) {
                _log.info("Change flags of failover target " + RPHelper.getRPWWn(protectionVolume.getId(), _dbClient));
                protectionVolume.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                protectionVolume.setLinkStatus(Volume.LinkStatus.FAILED_OVER.name());
                _dbClient.updateObject(protectionVolume);
            } else if (protectionVolume.checkPersonality(Volume.PersonalityTypes.SOURCE.toString())) {
                _log.info("Change flags of failover source " + RPHelper.getRPWWn(protectionVolume.getId(), _dbClient));
                protectionVolume.setLinkStatus(Volume.LinkStatus.FAILED_OVER.name());
                _dbClient.updateObject(protectionVolume);
            }
        }
    }

    /**
     * After a failover of a failover (without swap), we need to set specific flags
     *
     * @param id
     *            volume we failed over to
     * @throws InternalException
     */
    private void updatePostFailoverCancel(Volume volume) throws InternalException {
        _log.info("Setting respective flags after failover of failover");
        ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());
        List<URI> volumeIDs = new ArrayList<URI>();
        for (String volumeString : protectionSet.getVolumes()) {
            URI volumeURI;
            try {
                volumeURI = new URI(volumeString);
                volumeIDs.add(volumeURI);
            } catch (URISyntaxException e) {
                _log.error("URI syntax incorrect: ", e);
            }
        }

        for (URI protectionVolumeID : volumeIDs) {
            Volume protectionVolume = _dbClient.queryObject(Volume.class, protectionVolumeID);
            if (protectionVolume == null || protectionVolume.getInactive()) {
                continue;
            }
            if ((protectionVolume.checkPersonality(Volume.PersonalityTypes.TARGET.toString()))
                    && (protectionVolume.getRpCopyName().equals(volume.getRpCopyName()))) {
                _log.info("Change flags of failover target " + RPHelper.getRPWWn(protectionVolume.getId(), _dbClient));
                protectionVolume.setAccessState(Volume.VolumeAccessState.NOT_READY.name());
                protectionVolume.setLinkStatus(Volume.LinkStatus.IN_SYNC.name());
                _dbClient.updateObject(protectionVolume);
            } else if (protectionVolume.checkPersonality(Volume.PersonalityTypes.SOURCE.toString())) {
                _log.info("Change flags of failover source " + RPHelper.getRPWWn(protectionVolume.getId(), _dbClient));
                protectionVolume.setLinkStatus(Volume.LinkStatus.IN_SYNC.name());
                _dbClient.updateObject(protectionVolume);
            }
        }
    }

    @Override
    public void discover(AsyncTask[] tasks) throws InternalException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.protectioncontroller.RPController#createSnapshot(java.net.URI, java.net.URI, java.util.List,
     * java.lang.Boolean, java.lang.Boolean, java.lang.String)
     */
    @Override
    public void createSnapshot(URI protectionDevice, URI storageURI, List<URI> snapshotList, Boolean createInactive, Boolean readOnly,
            String opId) throws InternalException {
        TaskCompleter completer = new BlockSnapshotCreateCompleter(snapshotList, opId);
        // if snapshot is part of a CG, add CG id to the completer
        List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, snapshotList);
        ControllerUtils.checkSnapshotsInConsistencyGroup(snapshots, _dbClient, completer);

        Map<URI, Integer> snapshotMap = new HashMap<URI, Integer>();
        try {
            ProtectionSystem system = null;
            system = _dbClient.queryObject(ProtectionSystem.class, protectionDevice);
            // Verify non-null storage device returned from the database client.
            if (system == null) {
                throw DeviceControllerExceptions.recoverpoint.failedConnectingForMonitoring(protectionDevice);
            }

            // Make sure we have at least 1 snap/bookmark otherwise there is nothing to create
            if (snapshotList == null || snapshotList.isEmpty()) {
                throw DeviceControllerExceptions.recoverpoint.failedToFindExpectedBookmarks();
            }

            // A temporary date/time stamp
            String snapshotName = VIPR_SNAPSHOT_PREFIX + new SimpleDateFormat("yyMMdd-HHmmss").format(new java.util.Date());

            Set<String> volumeWWNs = new HashSet<String>();
            boolean rpBookmarkOnly = false;

            for (URI snapshotID : snapshotList) {
                // create a snapshot map, a map is required to re-use the existing enable image access method.
                // using a lun number of -1 for all snaps, this value is not used, hence ok to use that value.
                snapshotMap.put(snapshotID, ExportGroup.LUN_UNASSIGNED);
            }

            // Get the volume associated with this snapshot.
            // Note we could have multiple snapshots in this request depending on the number of targets for the
            // source. We only need 1 of the snapshots to create the bookmark on RP. So just grab the
            // first one in the list.
            BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotList.get(0));
            if (snapshot.getEmName() != null) {
                rpBookmarkOnly = true;
                snapshotName = snapshot.getEmName();
            }

            Volume volume = _dbClient.queryObject(Volume.class, snapshot.getParent().getURI());

            // Snapshot object's parent volume is the underlying block volume for VPLEX volumes.
            // Retrieve the VPLEX volume if the "volume" object is part of VPLEX volume.
            // if not, then the "volume" object is a regular block volume that is RP protected.
            if (Volume.checkForVplexBackEndVolume(_dbClient, volume)) {
                volumeWWNs.add(RPHelper.getRPWWn(Volume.fetchVplexVolume(_dbClient, volume).getId(), _dbClient));
            } else {
                volumeWWNs.add(RPHelper.getRPWWn(volume.getId(), _dbClient));
            }

            // Create a new token/taskid and use that in the workflow.
            // Multiple threads entering this method might collide with each others workflows in cassandra if the taskid
            // is not unique.
            String newToken = UUID.randomUUID().toString();
            // Set up workflow steps.
            Workflow workflow = _workflowService.getNewWorkflow(this, "createSnapshot", true, newToken);

            // Step 1 - Create a RP bookmark
            String waitFor = addCreateBookmarkStep(workflow, snapshotList, system, snapshotName, volumeWWNs, rpBookmarkOnly, null);

            if (!rpBookmarkOnly) {
                // Local array snap, additional steps required for snap operation

                // Step 2 - Enable image access
                waitFor = addEnableImageAccessStep(workflow, system, snapshotMap, waitFor);

                // Step 3 - Invoke block storage doCreateSnapshot
                waitFor = addCreateBlockSnapshotStep(workflow, waitFor, storageURI, snapshotList, createInactive, readOnly, system);

                // Step 4 - Disable image access
                addBlockSnapshotDisableImageAccessStep(workflow, waitFor, snapshotList, system);
            } else {
                _log.info("RP Bookmark only requested...");
            }

            String successMessage = String.format("Successfully created snapshot for %s", Joiner.on(",").join(snapshotList));
            workflow.executePlan(completer, successMessage);
        } catch (InternalException e) {
            _log.error("Operation failed with Exception: ", e);
            if (completer != null) {
                completer.error(_dbClient, e);
            }
        } catch (Exception e) {
            _log.error("Operation failed with Exception: ", e);
            if (completer != null) {
                completer.error(_dbClient, DeviceControllerException.errors.jobFailed(e));
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface#addStepsForPreCreateReplica(com.emc.
     * storageos.workflow
     * .Workflow, java.lang.String, java.util.List, java.lang.String)
     */
    @Override
    public String addStepsForPreCreateReplica(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws InternalException {

        _log.info("Adding steps for create replica");
        return addStepsForPreOrPostCreateReplica(workflow, waitFor, volumeDescriptors, true, taskId);
    }

    @Override
    public String addStepsForPostCreateReplica(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws InternalException {
        _log.info("Adding steps for post create replica");
        return addStepsForPreOrPostCreateReplica(workflow, waitFor, volumeDescriptors, false, taskId);
    }

    /**
     * adds steps for either pre-create copy or post-create copy for recoverpoint protected volumes
     *
     * @param workflow
     * @param waitFor
     * @param volumeDescriptors
     * @param preCreate
     *            true if this is a pre-create copy steps or false for post create copy steps
     * @param taskId
     * @return
     * @throws InternalException
     */
    private String addStepsForPreOrPostCreateReplica(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors,
            boolean preCreate, String taskId) throws InternalException {

        List<VolumeDescriptor> blockVolmeDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_DATA, VolumeDescriptor.Type.BLOCK_SNAPSHOT,
                        VolumeDescriptor.Type.VPLEX_IMPORT_VOLUME, VolumeDescriptor.Type.BLOCK_SNAPSHOT_SESSION },
                new VolumeDescriptor.Type[] {});

        // If no volumes to create, just return
        if (blockVolmeDescriptors.isEmpty()) {
            _log.warn("Skipping RP create steps for create replica because no block volume descriptors were found");
            return waitFor;
        }

        // get the list of parent volumes that are to be copied
        Map<VolumeDescriptor, List<URI>> descriptorToParentIds = new HashMap<VolumeDescriptor, List<URI>>();
        Class<? extends DataObject> clazz = Volume.class;
        for (VolumeDescriptor descriptor : blockVolmeDescriptors) {
            List<URI> parentIds = new ArrayList<>();
            if (URIUtil.isType(descriptor.getVolumeURI(), BlockSnapshotSession.class)) {
                // for snapshot sessions, if its a single volume snapshot session, parent will be filled in
                // for CG snapshot sessions, get all the parents in the group from the replication group name
                BlockSnapshotSession snapshotSession = _dbClient.queryObject(BlockSnapshotSession.class, descriptor.getVolumeURI());
                if (snapshotSession != null && !snapshotSession.getInactive()) {
                    if (!NullColumnValueGetter.isNullNamedURI(snapshotSession.getParent())) {
                        parentIds.add(snapshotSession.getParent().getURI());
                    } else if (!NullColumnValueGetter.isNullValue(snapshotSession.getReplicationGroupInstance())) {
                        List<Volume> volsInRG = ControllerUtils.getVolumesPartOfRG(snapshotSession.getStorageController(),
                                snapshotSession.getReplicationGroupInstance(), _dbClient);
                        for (Volume vol : volsInRG) {
                            parentIds.add(vol.getId());
                        }
                    } else {
                        _log.warn(
                                String.format(
                                        "Skipping BlockSnapshotSession object with null parent and null replicationGroupInstance: %s",
                                        snapshotSession.getId().toString()));
                    }
                    clazz = BlockSnapshotSession.class;
                }
            } else if (URIUtil.isType(descriptor.getVolumeURI(), BlockSnapshot.class)) {
                BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, descriptor.getVolumeURI());
                if (snapshot != null && !snapshot.getInactive() && !NullColumnValueGetter.isNullNamedURI(snapshot.getParent())) {
                    parentIds.add(snapshot.getParent().getURI());
                    clazz = BlockSnapshot.class;
                } else {
                    _log.warn(String.format("Skipping snapshot with null parent: %s", descriptor.getVolumeURI().toString()));
                }
            } else if (URIUtil.isType(descriptor.getVolumeURI(), Volume.class)) {
                Volume volume = _dbClient.queryObject(Volume.class, descriptor.getVolumeURI());
                if (volume != null && !volume.getInactive() && !NullColumnValueGetter.isNullURI(volume.getAssociatedSourceVolume())) {
                    parentIds.add(volume.getAssociatedSourceVolume());
                } else {
                    _log.warn(String.format("Skipping full copy with null parent: %s", descriptor.getVolumeURI().toString()));
                }
            } else {
                _log.warn(String.format("Skipping unsupported copy type: %s", descriptor.getVolumeURI().toString()));
            }
            if (!parentIds.isEmpty()) {
                descriptorToParentIds.put(descriptor, parentIds);
            }
        }

        // get the descriptor and wwn of each target volume being copied
        // also get the protection system and one source volume to be used for locking
        ProtectionSystem protectionSystem = null;
        Volume aSrcVolume = null;
        Set<String> volumeWWNs = new HashSet<String>();
        Set<URI> copyList = new HashSet<URI>();
        for (Entry<VolumeDescriptor, List<URI>> entry : descriptorToParentIds.entrySet()) {
            VolumeDescriptor descriptor = entry.getKey();
            List<URI> parentIds = entry.getValue();
            for (URI parentId : parentIds) {
                if (URIUtil.isType(parentId, Volume.class)) {
                    Volume parentVolume = _dbClient.queryObject(Volume.class, parentId);
                    if (parentVolume != null && !parentVolume.getInactive()) {
                        if (Volume.checkForVplexBackEndVolume(_dbClient, parentVolume)) {
                            parentVolume = Volume.fetchVplexVolume(_dbClient, parentVolume);
                        }
                        // recoverpoint enable image access is only required if target volumes are copied and
                        // the target volumes are RP volumes.
                        if (StringUtils.equals(parentVolume.getPersonality(), Volume.PersonalityTypes.TARGET.toString())
                                && Volume.checkForRP(_dbClient, parentVolume.getId())) {
                            volumeWWNs.add(RPHelper.getRPWWn(parentVolume.getId(), _dbClient));
                            copyList.add(descriptor.getVolumeURI());
                            if (protectionSystem == null) {
                                if (!NullColumnValueGetter.isNullURI(parentVolume.getProtectionController())) {
                                    aSrcVolume = RPHelper.getRPSourceVolumeFromTarget(_dbClient, parentVolume);
                                    protectionSystem = _dbClient.queryObject(ProtectionSystem.class, aSrcVolume.getProtectionController());
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!volumeWWNs.isEmpty()) {

            if (preCreate) {

                // A temporary date/time stamp for the bookmark name
                String bookmarkName = VIPR_SNAPSHOT_PREFIX + (new Random()).nextInt();

                // Step 1 - Create a RP bookmark
                String rpWaitFor = addCreateBookmarkStep(workflow, new ArrayList<URI>(), protectionSystem, bookmarkName, volumeWWNs, false,
                        waitFor);

                // Lock CG for the duration of the workflow so enable and disable can complete before another workflow
                // tries to enable image
                // access
                List<String> locks = new ArrayList<String>();
                String lockName = ControllerLockingUtil.getConsistencyGroupStorageKey(_dbClient, aSrcVolume.getConsistencyGroup(),
                        protectionSystem.getId());
                if (null != lockName) {
                    locks.add(lockName);
                    acquireWorkflowLockOrThrow(workflow, locks);
                }

                // Step 2 - Enable image access
                return addEnableImageAccessForCreateReplicaStep(workflow, protectionSystem, clazz, new ArrayList<URI>(copyList),
                        bookmarkName, volumeWWNs, rpWaitFor);

            } else {
                return addDisableImageAccessForCreateReplicaStep(workflow, protectionSystem, clazz, new ArrayList<URI>(copyList),
                        volumeWWNs, waitFor);
            }
        } else {
            _log.warn("Skipping RP create steps for create replica. No qualifying volume WWNs found.");
        }

        return waitFor;
    }

    /**
     * Add WF step for creating block snapshots
     *
     * @param workflow
     *            Workflow
     * @param waitFor
     *            wait on this step/step-group to finish before invoking the step herein
     * @param storageURI
     *            UID of the storage system
     * @param snapshotList
     *            List of snaphots in the request
     * @param createInactive
     *            Specifies whether the snapshot is created and activated or just created
     * @param readOnly
     *            Specifies whether the snapshot should be created as read only
     * @param rpSystem
     *            Protection system
     * @return This method step, so the caller can wait on this for invoking subsequent step(s).
     */
    private String addCreateBlockSnapshotStep(Workflow workflow, String waitFor, URI storageURI, List<URI> snapshotList,
            Boolean createInactive, Boolean readOnly, ProtectionSystem rpSystem) throws InternalException {

        String stepId = workflow.createStepId();
        // Now add the steps to create the block snapshot on the storage system
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageURI);
        Workflow.Method createBlockSnapshotMethod = new Workflow.Method(METHOD_CREATE_BLOCK_SNAPSHOT_STEP, storageURI, snapshotList,
                createInactive, readOnly);
        Workflow.Method rollbackCreateBlockSnapshotMethod = new Workflow.Method(METHOD_ROLLBACK_CREATE_BLOCK_SNAPSHOT);

        workflow.createStep(STEP_CREATE_BLOCK_SNAPSHOT, "Create Block Snapshot subtask for RP: ", waitFor, storageSystem.getId(),
                storageSystem.getSystemType(), this.getClass(), createBlockSnapshotMethod, rollbackCreateBlockSnapshotMethod, stepId);
        _log.info(String.format("Added createBlockSnapshot step [%s] in workflow", stepId));

        return STEP_CREATE_BLOCK_SNAPSHOT;
    }

    /**
     * Invokes the storage specific BlockController method to perform the snapshot operation
     *
     * @param storageURI
     *            Storage System URI
     * @param snapshotList
     *            List of snaps in the request
     * @param createInactive
     *            Specifies whether the snapshot is created and activated or just created
     * @param readOnly
     *            Specifies whether the snapshot is created as read only
     * @param stepId
     *            workflow step Id for this step.
     * @return true if successful, false otherwise
     */
    public boolean createBlockSnapshotStep(URI storageURI, List<URI> snapshotList, Boolean createInactive, Boolean readOnly,
            String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageURI);
            BlockController controller = getController(BlockController.class, storageSystem.getSystemType());
            controller.createSnapshot(storageURI, snapshotList, createInactive, readOnly, stepId);
        } catch (Exception e) {
            WorkflowStepCompleter.stepFailed(stepId, DeviceControllerException.errors.jobFailed(e));
            return false;
        }
        return true;
    }

    /**
     * Rollback method for Block snapshot create.
     *
     * @param stepId
     * @return
     */
    public boolean createBlockSnapshotRollbackStep(String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        _log.info(String.format("rollbackCreateBlockSnapshotStep : Nothing to rollback for step id [%s]", stepId));
        WorkflowStepCompleter.stepSucceded(stepId);
        return true;
    }

    /**
     * Add workflow step for creating bookmarks.
     *
     * @param workflow
     *            Workflow
     * @param snapshotList
     *            List of snapshots
     * @param system
     *            Protection System
     * @param name
     *            Snapshot name
     * @param volumeWWNs
     *            WWNs of the volumes whose snap is requested
     * @param emOnly
     *            if true, an RP bookmark is taken or a local array snap is performed.
     * @return
     */
    public String addCreateBookmarkStep(Workflow workflow, List<URI> snapshotList, ProtectionSystem system, String bookmarkName,
            Set<String> volumeWWNs, boolean emOnly, String waitFor) throws InternalException {

        String stepId = workflow.createStepId();
        Workflow.Method createBookmarkMethod = new Workflow.Method(METHOD_CREATE_BOOKMARK_STEP, snapshotList, system, bookmarkName,
                volumeWWNs, emOnly);

        Workflow.Method rollbackCreateBookmarkMethod = new Workflow.Method(METHOD_ROLLBACK_CREATE_BOOKMARK_STEP);

        workflow.createStep(STEP_BOOKMARK_CREATE, String.format("Create RecoverPoint bookmark %s", bookmarkName), waitFor, system.getId(),
                system.getSystemType(), this.getClass(), createBookmarkMethod, rollbackCreateBookmarkMethod, stepId);

        _log.info(String.format("Added create bookmark %s step [%s] in workflow", bookmarkName, stepId));

        return STEP_BOOKMARK_CREATE;
    }

    /**
     * This method creates a RP bookmark
     *
     * @param snapshotList
     *            List of snapshot
     * @param system
     *            Protection Sytem
     * @param snapshotName
     *            snapshot name
     * @param volumeWWNs
     *            WWNs of the volumes whose snap is requested
     * @param rpBookmarkOnly
     *            if true, an RP bookmark is taken or a local array snap is performed.
     * @param token
     *            step Id corresponding to this step.
     * @return true if successful, false otherwise.
     */
    public boolean createBookmarkStep(List<URI> snapshotList, ProtectionSystem system, String snapshotName, Set<String> volumeWWNs,
            boolean rpBookmarkOnly, String token) {

        RPHelper.setLinkStateWaitTimeOut(_coordinator);
        RecoverPointClient rp = RPHelper.getRecoverPointClient(system);
        CreateBookmarkRequestParams request = new CreateBookmarkRequestParams();
        request.setVolumeWWNSet(volumeWWNs);
        request.setBookmark(snapshotName);

        try {
            // Create the bookmark on the RP System
            CreateBookmarkResponse response = rp.createBookmarks(request);

            if (response == null) {
                throw DeviceControllerExceptions.recoverpoint.failedToCreateBookmark();
            }

            if (snapshotList != null && !snapshotList.isEmpty()) {
                // RP Bookmark-only flow.
                if (rpBookmarkOnly) {
                    // This will update the blocksnapshot object based on the return of the EM call
                    // The construct method will set the task completer on each snapshot
                    constructSnapshotObjectFromBookmark(response, system, snapshotList, snapshotName, token);
                } else {
                    // Update the snapshot object with the snapshotName, this field is required during enable and
                    // disable
                    // image access later on.
                    for (URI snapshotURI : snapshotList) {
                        BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotURI);
                        snapshot.setEmName(snapshotName);
                        _dbClient.updateObject(snapshot);
                    }
                }
            }
            WorkflowStepCompleter.stepSucceded(token);
        } catch (RecoverPointException e) {
            _log.error("create bookmark step failed with a RecoverPoint exception: ", e);
            WorkflowStepCompleter.stepFailed(token, e);
            return false;
        } catch (Exception e) {
            _log.error("create bookmark step failed with an unchecked exception: ", e);
            WorkflowStepCompleter.stepFailed(token, DeviceControllerException.errors.jobFailed(e));
            return false;
        }

        return true;
    }

    /**
     * Rollback method for create bookmark step.
     * Currently, this is just a dummy step and does nothing.
     *
     * @param stepId
     * @return
     */
    public boolean createBookmarkRollbackStep(String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        _log.info(String.format("rollbackCreateBookmarkStep - Nothing to rollback for step id [%s], return", stepId));
        WorkflowStepCompleter.stepSucceded(stepId);
        return true;
    }

    /**
     * Amend the BlockSnapshot object based on the results of the Bookmark creation operation
     *
     * @param result
     *            result from the snapshot creation command
     * @param system
     *            protection system
     * @param snapshotList
     *            snapshot list generated
     * @param name
     *            emName
     * @param opId
     *            operation ID for task completer
     * @throws InternalException
     * @throws FunctionalAPIInternalError_Exception
     * @throws FunctionalAPIActionFailedException_Exception
     */
    private void constructSnapshotObjectFromBookmark(CreateBookmarkResponse response, ProtectionSystem system, List<URI> snapshotList,
            String name, String opId) throws InternalException {

        ProtectionSet protectionSet = null;
        RecoverPointClient rp = RPHelper.getRecoverPointClient(system);

        // Update each snapshot object with the respective information.
        for (URI snapshotID : snapshotList) {
            // Get the snapshot and the associated volume
            BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotID);
            Volume volume = _dbClient.queryObject(Volume.class, snapshot.getParent().getURI());

            // For RP+VPLEX volumes, we need to fetch the VPLEX volume.
            // The snapshot objects references the block/back-end volume as its parent.
            // Fetch the VPLEX volume that is created with this volume as the back-end volume.
            if (Volume.checkForVplexBackEndVolume(_dbClient, volume)) {
                volume = Volume.fetchVplexVolume(_dbClient, volume);
            }

            if (protectionSet == null || !protectionSet.getId().equals(volume.getProtectionSet().getURI())) {
                protectionSet = _dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());
            }

            // Gather the bookmark date, which is different than the snapshot date
            Date bookmarkDate = new Date();
            if (response.getVolumeWWNBookmarkDateMap() != null) {
                bookmarkDate = response.getVolumeWWNBookmarkDateMap().get(RPHelper.getRPWWn(volume.getId(), _dbClient));
            } else {
                _log.warn("Bookmark date was not filled-in.  Will use current date/time.");
            }

            snapshot.setEmName(name);

            snapshot.setInactive(false);
            snapshot.setEmBookmarkTime("" + bookmarkDate.getTime());
            snapshot.setCreationTime(Calendar.getInstance());
            snapshot.setTechnologyType(TechnologyType.RP.toString());

            Volume targetVolume = RPHelper.getRPTargetVolumeFromSource(_dbClient, volume, snapshot.getVirtualArray());

            // This section will identify and store the COPY ID associated with the bookmarks created.
            // It is critical to store this information so we can later determine which bookmarks have
            // been deleted from the RPA.
            //
            // May be able to remove this if the protection set object is more detailed (for instance, if
            // we store the copy id with the volume)
            RecoverPointVolumeProtectionInfo protectionInfo = rp
                    .getProtectionInfoForVolume(RPHelper.getRPWWn(targetVolume.getId(), _dbClient));
            for (RPConsistencyGroup rpcg : response.getCgBookmarkMap().keySet()) {
                if (rpcg.getCGUID().getId() == protectionInfo.getRpVolumeGroupID()) {
                    for (RPBookmark bookmark : response.getCgBookmarkMap().get(rpcg)) {
                        if (bookmark.getBookmarkName() != null && bookmark.getBookmarkName().equalsIgnoreCase(name) && bookmark
                                .getCGGroupCopyUID().getGlobalCopyUID().getCopyUID() == protectionInfo.getRpVolumeGroupCopyID()) {
                            snapshot.setEmCGGroupCopyId(protectionInfo.getRpVolumeGroupCopyID());
                            break;
                        }
                    }
                }
            }

            if (targetVolume.getId().equals(volume.getId())) {
                _log.error("The source and the target volumes are the same");
                throw DeviceControllerExceptions.recoverpoint.cannotActivateSnapshotNoTargetVolume();
            }

            snapshot.setDeviceLabel(targetVolume.getDeviceLabel());
            snapshot.setStorageController(targetVolume.getStorageController());
            snapshot.setSystemType(targetVolume.getSystemType());
            snapshot.setVirtualArray(targetVolume.getVirtualArray());
            snapshot.setNativeId(targetVolume.getNativeId());
            snapshot.setAlternateName(targetVolume.getAlternateName());
            snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(system, snapshot));
            snapshot.setIsSyncActive(false);

            // Setting the WWN of the bookmark to the WWN of the volume, no functional reason for now.
            snapshot.setWWN(RPHelper.getRPWWn(targetVolume.getId(), _dbClient));
            snapshot.setProtectionController(system.getId());
            snapshot.setProtectionSet(volume.getProtectionSet().getURI());

            _log.info(String.format("Updated bookmark %1$s associated with block volume %2$s on site %3$s.", name, volume.getDeviceLabel(),
                    snapshot.getEmInternalSiteName()));
            _dbClient.updateObject(snapshot);

            List<URI> taskSnapshotURIList = new ArrayList<URI>();
            taskSnapshotURIList.add(snapshot.getId());
            TaskCompleter completer = new BlockSnapshotCreateCompleter(taskSnapshotURIList, opId);
            completer.ready(_dbClient);
        }
        // Get information about the bookmarks created so we can get to them later.
        _log.info("Bookmark(s) created for snapshot operation");
        return;
    }

    /**
     * Gets a list of volume IDs to be restored. If the snapshot corresponds to
     * a consistency group, we must get all the volumes associated to other
     * BlockSnapshots that share the same snapset label. Secondly, if the snapshot's
     * parent volume is a VPlex backing volume, we must lookup the associated
     * VPlex volume and use that.
     *
     * @param snapshot
     *            the snapshot to restore.
     * @param volume
     *            the volume to be restored.
     * @return a list of volume IDs to be restored.
     */
    private List<URI> getVolumesForRestore(BlockSnapshot snapshot, Volume volume) {
        List<URI> volumeURIs = new ArrayList<URI>();

        URI cgURI = snapshot.getConsistencyGroup();
        if (NullColumnValueGetter.isNullURI(cgURI)) {
            // If the snapshot is not in a CG, delete the replication set
            // for only the requested volume.
            volumeURIs.add(volume.getId());
        } else {
            // Otherwise, get all snapshots in the snapset, get the parent volume for each
            // snapshot. If the parent is a VPlex backing volume, get the VLPEX volume
            // using the snapshot parent.
            List<BlockSnapshot> cgSnaps = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshot, _dbClient);
            for (BlockSnapshot cgSnapshot : cgSnaps) {
                URIQueryResultList queryResults = new URIQueryResultList();
                _dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getVolumeByAssociatedVolumesConstraint(cgSnapshot.getParent().getURI().toString()),
                        queryResults);
                URI vplexVolumeURI = null;
                if (queryResults.iterator().hasNext()) {
                    vplexVolumeURI = queryResults.iterator().next();
                    if (vplexVolumeURI != null) {
                        volumeURIs.add(vplexVolumeURI);
                    }
                } else {
                    volumeURIs.add(cgSnapshot.getParent().getURI());
                }
            }
        }

        return volumeURIs;
    }

    /**
     * Adds the necessary RecoverPoint controller steps that need to be executed prior
     * to restoring a volume from snapshot. The pre-restore step is required if we
     * are restoring a native array snapshot of the following parent volumes:
     * <ul>
     * <li>A BlockSnapshot parent volume that is a regular RP source/target residing on a VMAX.</li>
     * <li>A BlockSnapshot parent volume that is a backing volume to a VPlex distributed volume.</li>
     * </ul>
     *
     * @param workflow
     *            the Workflow being constructed
     * @param storageSystemURI
     *            the URI of storage controller
     * @param volumeURI
     *            the URI of volume to be restored
     * @param snapshotURI
     *            the URI of snapshot used for restoration
     * @param taskId
     *            the top level operation's taskId
     * @return A waitFor key that can be used by subsequent controllers to wait on
     */
    public String addPreRestoreVolumeSteps(Workflow workflow, URI storageSystemURI, URI volumeURI, URI snapshotURI, String taskId) {

        String waitFor = null;
        BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotURI);

        // Only consider native snapshots
        if (snapshot != null && NullColumnValueGetter.isNotNullValue(snapshot.getTechnologyType())
                && snapshot.getTechnologyType().equals(TechnologyType.NATIVE.toString())) {

            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemURI);

            if (volume != null && storageSystem != null) {
                boolean vplexDistBackingVolume = false;
                URI cgId = volume.getConsistencyGroup();
                Volume associatedVPlexVolume = Volume.fetchVplexVolume(_dbClient, volume);
                if (associatedVPlexVolume != null && associatedVPlexVolume.getAssociatedVolumes() != null
                        && associatedVPlexVolume.getAssociatedVolumes().size() == 2) {
                    vplexDistBackingVolume = true;
                }

                if (vplexDistBackingVolume) {
                    volume = associatedVPlexVolume;
                }

                // Only add the pre-restore step if we are restoring a native snapshot who's parent
                // volume is:
                // 1 - A regular RP source/target residing on a VMAX.
                // 2 - A backing volume to a VPlex distributed volume. Non-distributed VPlex volumes
                // do not require this step because there is not cleanup on the VPlex required
                // before performing the native block restore.
                if (!NullColumnValueGetter.isNullURI(volume.getProtectionController()) && (vplexDistBackingVolume
                        || (storageSystem != null && NullColumnValueGetter.isNotNullValue(storageSystem.getSystemType())
                        && storageSystem.getSystemType().equals(SystemType.vmax.toString())))) {

                    ProtectionSystem rpSystem = null;
                    rpSystem = _dbClient.queryObject(ProtectionSystem.class, volume.getProtectionController());
                    if (rpSystem == null) {
                        // Verify non-null storage device returned from the database client.
                        throw DeviceControllerExceptions.recoverpoint.failedConnectingForMonitoring(volume.getProtectionController());
                    }

                    List<URI> volumeURIs = getVolumesForRestore(snapshot, volume);

                    // Validate the replication sets for all volumes to restore. Must ensure the source
                    // volume size is not greater than the target volume size
                    List<Volume> volumes = _dbClient.queryObject(Volume.class, volumeURIs);
                    RPHelper.validateRSetVolumeSizes(_dbClient, volumes);

                    Map<String, RecreateReplicationSetRequestParams> rsetParams = new HashMap<String, RecreateReplicationSetRequestParams>();

                    // Lock CG
                    List<String> locks = new ArrayList<String>();
                    String lockName = ControllerLockingUtil.getConsistencyGroupStorageKey(_dbClient, cgId, rpSystem.getId());
                    if (null != lockName) {
                        locks.add(lockName);
                        acquireWorkflowLockOrThrow(workflow, locks);
                    }

                    for (URI volumeId : volumeURIs) {
                        Volume vol = _dbClient.queryObject(Volume.class, volumeId);
                        RecreateReplicationSetRequestParams rsetParam = getReplicationSettings(rpSystem, vol.getId());
                        rsetParams.put(RPHelper.getRPWWn(vol.getId(), _dbClient), rsetParam);
                    }

                    String stepId = workflow.createStepId();
                    Workflow.Method deleteRsetExecuteMethod = new Workflow.Method(METHOD_DELETE_RSET_STEP, rpSystem.getId(), volumeURIs);

                    Workflow.Method recreateRSetExecuteMethod = new Workflow.Method(METHOD_RECREATE_RSET_STEP, rpSystem.getId(),
                            volumeURIs,
                            rsetParams);

                    waitFor = workflow.createStep(STEP_PRE_VOLUME_RESTORE,
                            "Pre volume restore from snapshot, delete replication set step for RP: " + volumeURI.toString(), null,
                            rpSystem.getId(), rpSystem.getSystemType(), this.getClass(), deleteRsetExecuteMethod,
                            recreateRSetExecuteMethod,
                            stepId);

                    _log.info(String.format("Created workflow step to delete replication set for volume %s.", volume.getId().toString()));
                }
            }
        }

        return waitFor;
    }

    /**
     * Adds the necessary RecoverPoint controller steps that need to be executed after
     * restoring a volume from snapshot. The post-restore step is required if we
     * are restoring a native array snapshot of the following parent volumes:
     * <ul>
     * <li>A BlockSnapshot parent volume that is a regular RP source/target residing on a VMAX.</li>
     * <li>A BlockSnapshot parent volume that is a backing volume to a VPlex distributed volume.</li>
     * </ul>
     *
     * @param workflow
     *            the Workflow being constructed
     * @param storageSystemURI
     *            the URI of storage controller
     * @param volumeURI
     *            the URI of volume to be restored
     * @param snapshotURI
     *            the URI of snapshot used for restoration
     * @param taskId
     *            the top level operation's taskId
     * @return A waitFor key that can be used by subsequent controllers to wait on
     */
    public String addPostRestoreVolumeSteps(Workflow workflow, String waitFor, URI storageSystemURI, URI volumeURI, URI snapshotURI,
            String taskId) {

        BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotURI);

        // Only consider native snapshots
        if (snapshot != null && NullColumnValueGetter.isNotNullValue(snapshot.getTechnologyType())
                && snapshot.getTechnologyType().equals(TechnologyType.NATIVE.name())) {

            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemURI);

            if (volume != null && storageSystem != null) {
                boolean vplexDistBackingVolume = false;
                Volume associatedVPlexVolume = Volume.fetchVplexVolume(_dbClient, volume);
                if (associatedVPlexVolume != null && associatedVPlexVolume.getAssociatedVolumes() != null
                        && associatedVPlexVolume.getAssociatedVolumes().size() == 2) {
                    vplexDistBackingVolume = true;
                }

                if (vplexDistBackingVolume) {
                    volume = associatedVPlexVolume;
                }

                // Only add the pre-restore step if we are restoring a native snapshot who's parent
                // volume is:
                // 1 - A regular RP source/target residing on a VMAX.
                // 2 - A backing volume to a VPlex distributed volume
                if (!NullColumnValueGetter.isNullURI(volume.getProtectionController()) && (vplexDistBackingVolume
                        || (storageSystem != null && NullColumnValueGetter.isNotNullValue(storageSystem.getSystemType())
                        && storageSystem.getSystemType().equals(SystemType.vmax.name())))) {

                    ProtectionSystem rpSystem = null;
                    rpSystem = _dbClient.queryObject(ProtectionSystem.class, volume.getProtectionController());
                    if (rpSystem == null) {
                        // Verify non-null storage device returned from the database client.
                        throw DeviceControllerExceptions.recoverpoint.failedConnectingForMonitoring(volume.getProtectionController());
                    }

                    List<URI> volumeURIs = getVolumesForRestore(snapshot, volume);

                    Map<String, RecreateReplicationSetRequestParams> rsetParams = new HashMap<String, RecreateReplicationSetRequestParams>();

                    for (URI volumeId : volumeURIs) {
                        Volume vol = _dbClient.queryObject(Volume.class, volumeId);
                        RecreateReplicationSetRequestParams rsetParam = getReplicationSettings(rpSystem, vol.getId());
                        rsetParams.put(RPHelper.getRPWWn(vol.getId(), _dbClient), rsetParam);
                    }

                    String stepId = workflow.createStepId();
                    Workflow.Method recreateRSetExecuteMethod = new Workflow.Method(METHOD_RECREATE_RSET_STEP, rpSystem.getId(),
                            volumeURIs,
                            rsetParams);

                    waitFor = workflow.createStep(STEP_POST_VOLUME_RESTORE,
                            "Post volume restore from snapshot, re-create replication set step for RP: " + volume.toString(), waitFor,
                            rpSystem.getId(), rpSystem.getSystemType(), this.getClass(), recreateRSetExecuteMethod,
                            rollbackMethodNullMethod(), stepId);

                    _log.info(
                            String.format("Created workflow step to re-create replication set for volume %s.", volume.getId().toString()));
                }
            }
        }

        return waitFor;
    }

    @Override
    public String addStepsForRestoreVolume(Workflow workflow, String waitFor, URI storage, URI pool, URI volume, URI snapshot,
            Boolean updateOpStatus, String syncDirection, String taskId, BlockSnapshotRestoreCompleter completer) throws InternalException {

        BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class, snapshot);

        if (snap != null && NullColumnValueGetter.isNotNullValue(snap.getTechnologyType())) {
            Volume vol = _dbClient.queryObject(Volume.class, volume);

            if (vol != null) {
                if (snap.getTechnologyType().equals(TechnologyType.RP.toString())) {
                    // Perform an RP controller restore operation only if restoring from an RP BlockSnapshot.
                    ProtectionSystem rpSystem = null;
                    rpSystem = _dbClient.queryObject(ProtectionSystem.class, vol.getProtectionController());
                    if (rpSystem == null) {
                        // Verify non-null storage device returned from the database client.
                        throw DeviceControllerExceptions.recoverpoint.failedConnectingForMonitoring(vol.getProtectionController());
                    }

                    String stepId = workflow.createStepId();
                    Workflow.Method restoreVolumeFromSnapshotMethod = new Workflow.Method(METHOD_RESTORE_VOLUME_STEP, rpSystem.getId(),
                            storage, snapshot, completer);

                    waitFor = workflow.createStep(null, "Restore volume from RP snapshot: " + volume.toString(), waitFor, rpSystem.getId(),
                            rpSystem.getSystemType(), this.getClass(), restoreVolumeFromSnapshotMethod, rollbackMethodNullMethod(), stepId);

                    _log.info(String.format("Created workflow step to restore RP volume %s from snapshot %s.", volume, snapshot));
                }
            }
        }

        return waitFor;
    }

    /**
     * Restore an RP bookmark. This will enable the specified bookmark on the CG if the CG is not already enabled. This
     * step is
     * required for RP bookmark restores.
     *
     * @param protectionDevice
     *            RP protection system URI
     * @param storageDevice
     *            storage device of the volume
     * @param snapshotId
     *            snapshot URI
     * @param task
     *            task ID
     * @return true if the step completed successfully, false otherwise.
     * @throws InternalException
     */
    public boolean restoreVolume(URI protectionDevice, URI storageDevice, URI snapshotID, BlockSnapshotRestoreCompleter completer,
            String stepId) throws InternalException {
        try {
            _log.info("Restoring bookmark on the RP CG");
            RPHelper.setLinkStateWaitTimeOut(_coordinator);
            WorkflowStepCompleter.stepExecuting(stepId);

            ProtectionSystem system = null;
            system = _dbClient.queryObject(ProtectionSystem.class, protectionDevice);
            if (system == null) {
                // Verify non-null storage device returned from the database client.
                throw DeviceControllerExceptions.recoverpoint.failedConnectingForMonitoring(protectionDevice);
            }

            Set<String> volumeWWNs = new HashSet<String>();
            String emName = null;

            // Get the volume associated with this snapshot
            BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotID);
            if (snapshot.getEmName() != null) {
                emName = snapshot.getEmName();
            }

            Volume volume = _dbClient.queryObject(Volume.class, snapshot.getParent().getURI());

            // Take out a workflow step lock on the CG
            _workflowService.getWorkflowFromStepId(stepId);
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getConsistencyGroupStorageKey(_dbClient, volume.getConsistencyGroup(), system.getId()));
            boolean lockAcquired = _workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.RP_CG));
            if (!lockAcquired) {
                throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(),
                        String.format("failed to get lock while restoring volumes in RP consistency group: %s", volume
                                .getConsistencyGroup().toString()));
            }

            // Now determine the target volume that corresponds to the site of the snapshot
            ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());
            Volume targetVolume = ProtectionSet.getTargetVolumeFromSourceAndInternalSiteName(_dbClient, protectionSet, volume,
                    snapshot.getEmInternalSiteName());

            volumeWWNs.add(RPHelper.getRPWWn(targetVolume.getId(), _dbClient));

            // Now restore image access
            RecoverPointClient rp = RPHelper.getRecoverPointClient(system);
            MultiCopyRestoreImageRequestParams request = new MultiCopyRestoreImageRequestParams();
            request.setBookmark(emName);
            request.setVolumeWWNSet(volumeWWNs);
            MultiCopyRestoreImageResponse response = rp.restoreImageCopies(request);

            if (response == null) {
                throw DeviceControllerExceptions.recoverpoint.failedToImageAccessBookmark();
            }

            WorkflowStepCompleter.stepSucceded(stepId);
            _log.info("restoreVolume step is complete");

        } catch (InternalException e) {
            _log.error("Operation failed with Exception: ", e);
            return stepFailed(stepId, (ServiceCoded) e, "restoreVolumeStep");
        } catch (URISyntaxException e) {
            _log.error("Operation failed with Exception: ", e);
            return stepFailed(stepId, e, "restoreVolumeStep");
        } catch (Exception e) {
            _log.error("Operation failed with Exception: ", e);
            return stepFailed(stepId, e, "restoreVolumeStep");
        }

        return true;
    }

    /**
     * Enable image access for RP snapshots.
     *
     * @param protectionDevice
     *            protection system
     * @param storageDevice
     *            storage device of the backing (parent) volume
     * @param snapshotList
     *            list of snapshots to enable
     * @param opId
     *            task ID
     * @return true if operation was successful
     * @throws ControllerException
     * @throws URISyntaxException
     */
    private boolean enableImageForSnapshots(URI protectionDevice, URI storageDevice, List<URI> snapshotList, String opId)
            throws ControllerException, URISyntaxException {
        TaskCompleter completer = null;
        try {
            _log.info("Activating a bookmark on the RP CG(s)");

            completer = new BlockSnapshotActivateCompleter(snapshotList, opId);

            ProtectionSystem system = null;
            try {
                system = _dbClient.queryObject(ProtectionSystem.class, protectionDevice);
            } catch (DatabaseException e) {
                throw DeviceControllerExceptions.recoverpoint.databaseExceptionActivateSnapshot(protectionDevice);
            }

            // Verify non-null storage device returned from the database client.
            if (system == null) {
                throw DeviceControllerExceptions.recoverpoint.databaseExceptionActivateSnapshot(protectionDevice);
            }

            // acquire a workflow lock so another thread doesn't disable image access while this thread
            // is still creating the snapshot
            if (snapshotList != null && !snapshotList.isEmpty()) {
                BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotList.get(0));
                Volume parent = _dbClient.queryObject(Volume.class, snapshot.getParent().getURI());
                final List<Volume> vplexVolumes = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, Volume.class,
                        getVolumesByAssociatedId(parent.getId().toString()));

                if (vplexVolumes != null && !vplexVolumes.isEmpty()) {
                    parent = vplexVolumes.get(0);
                }
                String lockName = ControllerLockingUtil.getConsistencyGroupStorageKey(_dbClient, parent.getConsistencyGroup(),
                        system.getId());
                if (null != lockName) {
                    List<String> locks = new ArrayList<String>();
                    locks.add(lockName);
                    acquireWorkflowLockOrThrow(_workflowService.getWorkflowFromStepId(opId), locks);
                }
            }

            // Keep a mapping of the emNames(bookmark names) to target copy volume WWNs
            Map<String, Set<String>> emNamesToVolumeWWNs = new HashMap<String, Set<String>>();
            // Keep a mapping of the emNames(bookmark names) to BlockSnapshot objects
            Map<String, Set<URI>> emNamesToSnapshots = new HashMap<String, Set<URI>>();

            for (URI snapshotID : snapshotList) {
                BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotID);

                String emName = snapshot.getEmName();
                if (NullColumnValueGetter.isNotNullValue(emName)) {
                    if (!emNamesToVolumeWWNs.containsKey(emName)) {
                        emNamesToVolumeWWNs.put(emName, new HashSet<String>());
                    }

                    if (!emNamesToSnapshots.containsKey(emName)) {
                        emNamesToSnapshots.put(emName, new HashSet<URI>());
                    }

                    emNamesToSnapshots.get(emName).add(snapshotID);
                } else {
                    throw DeviceControllerExceptions.recoverpoint.failedToActivateSnapshotEmNameMissing(snapshotID);
                }

                // Get the volume associated with this snapshot
                Volume volume = _dbClient.queryObject(Volume.class, snapshot.getParent().getURI());
                // For RP+VPLEX volumes, we need to fetch the VPLEX volume.
                // The snapshot objects references the block/back-end volume as its parent.
                // Fetch the VPLEX volume that is created with this volume as the back-end volume.
                if (Volume.checkForVplexBackEndVolume(_dbClient, volume)) {
                    volume = Volume.fetchVplexVolume(_dbClient, volume);
                }

                String wwn = null;
                // If the volume type is TARGET, then the enable image access request is part of snapshot create, just
                // add the volumeWWN to
                // the list.
                // If the personality is SOURCE, then the enable image access request is part of export operation.
                if (volume.checkPersonality(Volume.PersonalityTypes.TARGET.toString())) {
                    wwn = RPHelper.getRPWWn(volume.getId(), _dbClient);
                } else {
                    // Now determine the target volume that corresponds to the site of the snapshot
                    ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());
                    Volume targetVolume = ProtectionSet.getTargetVolumeFromSourceAndInternalSiteName(_dbClient, protectionSet, volume,
                            snapshot.getEmInternalSiteName());
                    wwn = RPHelper.getRPWWn(targetVolume.getId(), _dbClient);
                }

                // Add the volume WWN
                emNamesToVolumeWWNs.get(emName).add(wwn);
            }

            // Now enable image access to that bookmark
            RecoverPointClient rp = RPHelper.getRecoverPointClient(system);

            // Iterate over the the emNames and call RP to enableImageCopies for the WWNs
            // correponding to each emName.
            for (Map.Entry<String, Set<String>> emNameEntry : emNamesToVolumeWWNs.entrySet()) {
                MultiCopyEnableImageRequestParams request = new MultiCopyEnableImageRequestParams();
                request.setVolumeWWNSet(emNameEntry.getValue());
                request.setBookmark(emNameEntry.getKey());
                MultiCopyEnableImageResponse response = rp.enableImageCopies(request);

                if (response == null) {
                    throw DeviceControllerExceptions.recoverpoint.failedEnableAccessOnRP();
                }
            }

            completer.ready(_dbClient);
            return true;

        } catch (InternalException e) {
            _log.error("Operation failed with Exception: ", e);
            if (completer != null) {
                completer.error(_dbClient, e);
            }
            throw e;
        } catch (URISyntaxException e) {
            _log.error("Operation failed with Exception: ", e);
            if (completer != null) {
                completer.error(_dbClient, DeviceControllerException.errors.invalidURI(e));
            }
            throw e;
        } catch (Exception e) {
            _log.error("Operation failed with Exception: ", e);
            if (completer != null) {
                completer.error(_dbClient, DeviceControllerException.errors.jobFailed(e));
            }
            throw e;
        }
    }

    /**
     * Method that adds the steps to the workflow to enable image access before create native array replica operation
     *
     * @param workflow
     * @param rpSystem
     * @param clazz
     *            type of replica (such as Volume, BlockSnapshot or BlockSnapshotSession)
     * @param copyList
     *            list of replica ids
     * @param bookmarkName
     *            name of the bookmark created for this operation
     * @param volumeWWNs
     *            wwns of volumes that are parents to replica objects
     * @param waitFor
     * @return
     * @throws InternalException
     */
    private String addEnableImageAccessForCreateReplicaStep(Workflow workflow, ProtectionSystem rpSystem,
            Class<? extends DataObject> clazz,
            List<URI> copyList, String bookmarkName, Set<String> volumeWWNs, String waitFor) throws InternalException {
        String stepId = workflow.createStepId();
        Workflow.Method enableImageAccessExecuteMethod = new Workflow.Method(METHOD_ENABLE_IMAGE_ACCESS_CREATE_REPLICA_STEP,
                rpSystem.getId(), clazz, copyList, bookmarkName, volumeWWNs);
        Workflow.Method enableImageAccessExecutionRollbackMethod = new Workflow.Method(METHOD_DISABLE_IMAGE_ACCESS_CREATE_REPLICA_STEP,
                rpSystem.getId(), clazz, copyList, volumeWWNs);

        workflow.createStep(STEP_ENABLE_IMAGE_ACCESS, String.format("Enable image access for bookmark %s", bookmarkName), waitFor,
                rpSystem.getId(), rpSystem.getSystemType(), this.getClass(), enableImageAccessExecuteMethod,
                enableImageAccessExecutionRollbackMethod, stepId);

        _log.info(String.format("Added enable image access for bookmark %s step [%s] in workflow", bookmarkName, stepId));

        return STEP_ENABLE_IMAGE_ACCESS;
    }

    /**
     * Enable image access before create native array replica operation
     *
     * @param protectionDevice
     * @param clazz
     *            type of replica (such as Volume, BlockSnapshot or BlockSnapshotSession)
     * @param copyList
     *            list of replica ids
     * @param bookmarkName
     *            name of the bookmark created for this operation
     * @param volumeWWNs
     *            wwns of volumes that are parents to replica objects
     * @param opId
     * @return
     * @throws ControllerException
     */
    public boolean enableImageAccessForCreateReplicaStep(URI protectionDevice, Class<? extends DataObject> clazz, List<URI> copyList,
            String bookmarkName, Set<String> volumeWWNs, String opId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);

            _log.info(String.format("Activating bookmark %s on the RP CG(s)", bookmarkName));

            completer = new RPCGCopyVolumeCompleter(clazz, copyList, opId);

            // Verify non-null storage device returned from the database client.
            ProtectionSystem system = _dbClient.queryObject(ProtectionSystem.class, protectionDevice);
            if (system == null || system.getInactive()) {
                throw DeviceControllerExceptions.recoverpoint.databaseExceptionActivateSnapshot(protectionDevice);
            }

            // enable image access to that bookmark
            RecoverPointClient rp = RPHelper.getRecoverPointClient(system);
            MultiCopyEnableImageRequestParams request = new MultiCopyEnableImageRequestParams();
            request.setVolumeWWNSet(volumeWWNs);
            request.setBookmark(bookmarkName);
            MultiCopyEnableImageResponse response = rp.enableImageCopies(request);

            if (response == null) {
                throw DeviceControllerExceptions.recoverpoint.failedEnableAccessOnRP();
            }

            completer.ready(_dbClient);

            // Update the workflow state.
            WorkflowStepCompleter.stepSucceded(opId);

            return true;

        } catch (InternalException e) {
            _log.error("Operation failed with Exception: ", e);
            if (completer != null) {
                completer.error(_dbClient, e);
            }
            stepFailed(opId, "enableImageAccessStep: Failed to enable image");
            return false;
        } catch (Exception e) {
            _log.error("Operation failed with Exception: ", e);
            if (completer != null) {
                completer.error(_dbClient, DeviceControllerException.errors.jobFailed(e));
            }
            stepFailed(opId, "enableImageAccessStep: Failed to enable image");
            return false;
        }
    }

    /**
     * Method that adds the steps to the workflow to disable image access during create native array replica operation
     *
     * @param workflow
     * @param rpSystem
     * @param clazz
     *            type of replica (such as Volume, BlockSnapshot or BlockSnapshotSession)
     * @param copyList
     *            list of replica ids
     * @param volumeWWNs
     *            wwns of volumes that are parents to replica objects
     * @param waitFor
     * @return
     * @throws InternalException
     */
    private String addDisableImageAccessForCreateReplicaStep(Workflow workflow, ProtectionSystem rpSystem,
            Class<? extends DataObject> clazz, List<URI> copyList, Set<String> volumeWWNs, String waitFor) throws InternalException {
        String stepId = workflow.createStepId();

        Workflow.Method disableImageAccessExecuteMethod = new Workflow.Method(METHOD_DISABLE_IMAGE_ACCESS_CREATE_REPLICA_STEP,
                rpSystem.getId(), clazz, copyList, volumeWWNs);

        workflow.createStep(STEP_DISABLE_IMAGE_ACCESS, String.format("Disable image access for bookmark"), waitFor, rpSystem.getId(),
                rpSystem.getSystemType(), this.getClass(), disableImageAccessExecuteMethod, null, stepId);

        _log.info(String.format("Added disable access for bookmark step [%s] in workflow", stepId));
        return STEP_DISABLE_IMAGE_ACCESS;
    }

    /**
     * Disable image access for after create native array replica operation
     *
     * @param protectionDevice
     * @param clazz
     *            type of replica (such as Volume, BlockSnapshot or BlockSnapshotSession)
     * @param copyList
     *            list of replica ids
     * @param volumeWWNs
     *            wwns of volumes that are parents to replica objects
     * @param opId
     * @throws ControllerException
     */
    public void disableImageAccessForCreateReplicaStep(URI protectionDevice, Class<? extends DataObject> clazz, List<URI> copyList,
            Set<String> volumeWWNs, String opId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            _log.info("Deactivating a bookmark on the RP CG(s)");

            completer = new RPCGCopyVolumeCompleter(clazz, copyList, opId);

            // Verify non-null storage device returned from the database client.
            ProtectionSystem system = _dbClient.queryObject(ProtectionSystem.class, protectionDevice);
            if (system == null || system.getInactive()) {
                throw DeviceControllerExceptions.recoverpoint.databaseExceptionActivateSnapshot(protectionDevice);
            }

            // disable image access to that bookmark
            RecoverPointClient rp = RPHelper.getRecoverPointClient(system);
            MultiCopyDisableImageRequestParams request = new MultiCopyDisableImageRequestParams();
            request.setVolumeWWNSet(volumeWWNs);
            MultiCopyDisableImageResponse response = rp.disableImageCopies(request);
            if (response == null) {
                throw DeviceControllerExceptions.recoverpoint.failedDisableAccessOnRP();
            }

            completer.ready(_dbClient);
        } catch (InternalException e) {
            _log.error("Operation failed with Exception: ", e);
            if (completer != null) {
                completer.error(_dbClient, e);
            }
        } catch (Exception e) {
            _log.error("Operation failed with Exception: ", e);
            if (completer != null) {
                completer.error(_dbClient, DeviceControllerException.errors.jobFailed(e));
            }
        }
    }

    /**
     * Disable image access for RP snapshots.
     *
     * @param protectionDevice
     *            protection system
     * @param snapshotList
     *            list of snapshots to enable
     * @param setSnapshotSyncActive
     *            true if the isSyncActive field on BlockSnapshot should be true, false otherwise.
     * @param opId
     * @throws ControllerException
     */
    private void disableImageForSnapshots(URI protectionDevice, List<URI> snapshotList,
            boolean setSnapshotSyncActive, String opId) throws ControllerException {
        BlockSnapshotDeactivateCompleter completer = null;
        try {
            _log.info("Deactivating a bookmark on the RP CG(s)");

            completer = new BlockSnapshotDeactivateCompleter(snapshotList, setSnapshotSyncActive, opId);

            ProtectionSystem system = null;
            try {
                system = _dbClient.queryObject(ProtectionSystem.class, protectionDevice);
            } catch (DatabaseException e) {
                throw DeviceControllerExceptions.recoverpoint.databaseExceptionDeactivateSnapshot(protectionDevice);
            }

            if (system == null) {
                throw DeviceControllerExceptions.recoverpoint.databaseExceptionDeactivateSnapshot(protectionDevice);
            }

            // Keep a mapping of the emNames(bookmark names) to target copy volume WWNs
            Map<String, Set<String>> emNamesToVolumeWWNs = new HashMap<String, Set<String>>();
            // Keep a mapping of the emNames(bookmark names) to BlockSnapshot objects
            Map<String, Set<URI>> emNamesToSnapshots = new HashMap<String, Set<URI>>();

            for (URI snapshotID : snapshotList) {
                BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotID);

                // Determine if we can actually disable image access to the copy associated with the snapshot first
                if (!doDisableImageCopies(snapshot)) {
                    // Skip this snapshot because we cannot disable image access. Likely due to the snapshot
                    // being exported to multiple hosts.
                    _log.warn(String
                            .format("Cannot disable image access for snapshot %s so it will be skipped.  Likely due to the snapshot being exported to multiple hosts",
                                    snapshot.getId()));
                    continue;
                }

                String emName = snapshot.getEmName();
                if (NullColumnValueGetter.isNotNullValue(emName)) {
                    if (!emNamesToVolumeWWNs.containsKey(emName)) {
                        emNamesToVolumeWWNs.put(emName, new HashSet<String>());
                    }

                    if (!emNamesToSnapshots.containsKey(emName)) {
                        emNamesToSnapshots.put(emName, new HashSet<URI>());
                    }

                    emNamesToSnapshots.get(emName).add(snapshotID);
                } else {
                    throw DeviceControllerExceptions.recoverpoint.failedToDeactivateSnapshotEmNameMissing(snapshotID);
                }

                // Get the volume associated with this snapshot
                Volume volume = _dbClient.queryObject(Volume.class, snapshot.getParent().getURI());

                // For RP+VPLEX volumes, we need to fetch the VPLEX volume.
                // The snapshot objects references the block/back-end volume as its parent.
                // Fetch the VPLEX volume that is created with this volume as the back-end volume.
                if (Volume.checkForVplexBackEndVolume(_dbClient, volume)) {
                    volume = Volume.fetchVplexVolume(_dbClient, volume);
                }

                String wwn = null;
                // If the volume type is TARGET, then the enable image access request is part of snapshot create, just
                // add the volumeWWN to the list.
                // If the personality is SOURCE, then the enable image access request is part of export operation.
                if (volume.checkPersonality(Volume.PersonalityTypes.TARGET.toString())) {
                    wwn = RPHelper.getRPWWn(volume.getId(), _dbClient);
                } else {
                    // Now determine the target volume that corresponds to the site of the snapshot
                    ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());
                    Volume targetVolume = ProtectionSet.getTargetVolumeFromSourceAndInternalSiteName(_dbClient, protectionSet, volume,
                            snapshot.getEmInternalSiteName());
                    wwn = RPHelper.getRPWWn(targetVolume.getId(), _dbClient);
                }

                // Add the volume WWN
                emNamesToVolumeWWNs.get(emName).add(wwn);
            }

            // Now disable image access on the bookmark copies
            RecoverPointClient rp = RPHelper.getRecoverPointClient(system);

            // Iterate over the the emNames and call RP to disableImageCopies for the WWNs
            // correponding to each emName.
            for (Map.Entry<String, Set<String>> emNameEntry : emNamesToVolumeWWNs.entrySet()) {
                MultiCopyDisableImageRequestParams request = new MultiCopyDisableImageRequestParams();
                request.setVolumeWWNSet(emNameEntry.getValue());
                request.setEmName(emNameEntry.getKey());
                MultiCopyDisableImageResponse response = rp.disableImageCopies(request);

                if (response == null) {
                    throw DeviceControllerExceptions.recoverpoint.failedDisableAccessOnRP();
                }

                // Let the completer know about the deactivated snapshots (ones who's associated copies
                // had image access disabled). This will be used to update the BlockSnapshot fields accordingly.
                // This is done because not all snapshots used when creating the completer will be deactivated.
                // We need to maintain a collection of snapshots so that those exported to multiple hosts to not
                // get deactivated.
                completer.addDeactivatedSnapshots(emNamesToSnapshots.get(emNameEntry.getKey()));
            }

            completer.ready(_dbClient);
        } catch (InternalException e) {
            _log.error("Operation failed with Exception: ", e);
            if (completer != null) {
                completer.error(_dbClient, e);
            }
        } catch (URISyntaxException e) {
            _log.error("Operation failed with Exception: ", e);
            if (completer != null) {
                completer.error(_dbClient, DeviceControllerException.errors.invalidURI(e));
            }
        } catch (Exception e) {
            _log.error("Operation failed with Exception: ", e);
            if (completer != null) {
                completer.error(_dbClient, DeviceControllerException.errors.jobFailed(e));
            }
        }
    }

    /**
     * It is possible that RP snapshots are exported to more than one host and hence part of more than one ExportGroup.
     * If the same snapshot
     * is part of more than one active ExportGroup, do not disable Image Access on the RP CG.
     *
     * @param snapshot
     *            snapshot to be unexported
     * @return true if it is safe to disable image access on the CG, false otherwise
     */
    public boolean doDisableImageCopies(BlockSnapshot snapshot) {
        ContainmentConstraint constraint = ContainmentConstraint.Factory.getBlockObjectExportGroupConstraint(snapshot.getId());

        URIQueryResultList exportGroupIdsForSnapshot = new URIQueryResultList();
        _dbClient.queryByConstraint(constraint, exportGroupIdsForSnapshot);

        Iterator<URI> exportGroupIdsForSnapshotIter = exportGroupIdsForSnapshot.iterator();
        Set<URI> exportGroupURIs = new HashSet<URI>();
        while (exportGroupIdsForSnapshotIter.hasNext()) {
            exportGroupURIs.add(exportGroupIdsForSnapshotIter.next());
        }

        if (exportGroupURIs.size() > 1) {
            _log.info(String.format("Snapshot %s is in %d active exportGroups. Not safe to disable the CG", snapshot.getEmName(),
                    exportGroupURIs.size()));
            return false;
        }

        _log.info("Safe to disable image access on the CG");
        return true;
    }

    @Override
    public void deleteSnapshot(URI protectionDevice, URI snapshotURI, String opId) throws InternalException {
        TaskCompleter taskCompleter = null;
        try {
            BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class, snapshotURI);
            taskCompleter = BlockSnapshotDeleteCompleter.createCompleter(_dbClient, snap, opId);

            List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();

            URI cgId = null;
            if (snap.getConsistencyGroup() != null) {
                cgId = snap.getConsistencyGroup();
            }

            if (cgId != null) {
                // Account for all CG BlockSnapshots if this requested BlockSnapshot
                // references a CG.
                snapshots = ControllerUtils.getSnapshotsPartOfReplicationGroup(snap, _dbClient);
            } else {
                snapshots.add(snap);
            }

            for (BlockSnapshot snapshot : snapshots) {
                if (snapshot != null && !snapshot.getInactive()) {
                    snapshot.setInactive(true);
                    snapshot.setIsSyncActive(false);
                    _dbClient.updateObject(snapshot);
                }

                // Perhaps the snap is already deleted/inactive.
                // In that case, we'll just say all is well, so that this operation
                // is idempotent.
            }
            taskCompleter.ready(_dbClient);
        } catch (InternalException e) {
            String message = String.format("Generic exception when trying to delete snapshot %s on protection system %s",
                    String.valueOf(snapshotURI), protectionDevice);
            _log.error(message, e);
            taskCompleter.error(_dbClient, e);
        } catch (Exception e) {
            String message = String.format("Generic exception when trying to delete snapshot %s on protection system %s",
                    String.valueOf(snapshotURI), protectionDevice);
            _log.error(message, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    /**
     * Collects the RP statistics for the given <code>ProtectionSystem</code>.
     *
     * @param protectionSystem
     * @throws InternalException
     */
    private void collectRPStatistics(ProtectionSystem protectionSystem) throws InternalException {
        RecoverPointClient rpClient = RPHelper.getRecoverPointClient(protectionSystem);
        Set<RPSite> rpSites = rpClient.getAssociatedRPSites();
        RecoverPointStatisticsResponse response = rpClient.getRPSystemStatistics();

        _rpStatsHelper.updateProtectionSystemMetrics(protectionSystem, rpSites, response, _dbClient);
    }

    private void setProtectionSetStatus(RecoverPointVolumeProtectionInfo volumeProtectionInfo, String protectionSetStatus,
            ProtectionSystem system) {
        //
        // If volumeProtectionInfo is the source, then set the protection status of the whole protection set.
        // We don't have the ability to set the status of the individual copies, yet.
        //
        if (volumeProtectionInfo
                .getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_SOURCE) {
            URIQueryResultList list = new URIQueryResultList();
            Constraint constraint = ContainmentConstraint.Factory.getProtectionSystemProtectionSetConstraint(system.getId());
            try {
                _dbClient.queryByConstraint(constraint, list);
                Iterator<URI> it = list.iterator();
                while (it.hasNext()) {
                    URI protectionSetId = it.next();
                    _log.info("Check protection set ID: " + protectionSetId);
                    ProtectionSet protectionSet;
                    protectionSet = _dbClient.queryObject(ProtectionSet.class, protectionSetId);
                    if (protectionSet.getInactive() == false) {
                        _log.info("Change the status to: " + protectionSetStatus);
                        protectionSet.setProtectionStatus(protectionSetStatus);
                        _dbClient.updateObject(protectionSet);
                        break;
                    }
                }
            } catch (DatabaseException e) {
                // Don't worry about this
            }
        } else {
            _log.info("Did not pause the protection source.  Not updating protection status");
        }
    }

    /**
     * Looks up controller dependency for given hardware
     *
     * @param clazz
     *            controller interface
     * @param hw
     *            hardware name
     * @param <T>
     * @return
     * @throws CoordinatorException
     */
    protected <T extends StorageController> T getController(Class<T> clazz, String hw) throws CoordinatorException {
        return _coordinator.locateService(clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
    }

    /**
     * Check if initiator being added to export-group is good.
     *
     * @param exportGroup
     * @param initiator
     * @throws InternalException
     */
    private URI getInitiatorNetwork(ExportGroup exportGroup, Initiator initiator) throws InternalException {
        _log.info(String.format("Export(%s), Initiator: p(%s), port(%s)", exportGroup.getLabel(), initiator.getProtocol(),
                initiator.getInitiatorPort()));

        NetworkLite net = BlockStorageScheduler.lookupNetworkLite(_dbClient, StorageProtocol.block2Transport(initiator.getProtocol()),
                initiator.getInitiatorPort());

        // If this port is unplugged or in a network we don't know about or in a network that is unregistered, then we
        // can't use it.
        if (net == null || RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(net.getRegistrationStatus())) {
            return null;
        }

        return net.getId();
    }

    private void initTaskStatus(ExportGroup exportGroup, String task, Operation.Status status, String message) {
        if (exportGroup.getOpStatus() == null) {
            exportGroup.setOpStatus(new OpStatusMap());
        }
        final Operation op = new Operation();
        if (status == Operation.Status.ready) {
            op.ready();
        }
        exportGroup.getOpStatus().put(task, op);
    }

    @Override
    public void exportGroupUpdate(URI storageURI, URI exportGroupURI, Workflow storageWorkflow, String token) throws Exception {

        TaskCompleter taskCompleter = null;
        try {
            _log.info(
                    String.format("exportGroupUpdate start - Array: %s ExportMask: %s", storageURI.toString(), exportGroupURI.toString()));
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            ProtectionSystem storage = _dbClient.queryObject(ProtectionSystem.class, storageURI);
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
            String successMessage = String.format("ExportGroup %s successfully updated for StorageArray %s", exportGroup.getLabel(),
                    storage.getLabel());
            storageWorkflow.setService(_workflowService);
            storageWorkflow.executePlan(taskCompleter, successMessage);
        } catch (InternalException e) {
            _log.error("Operation failed with Exception: ", e);
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, e);
            }
        } catch (Exception e) {
            _log.error("Operation failed with Exception: ", e);
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, DeviceControllerException.errors.jobFailed(e));
            }
        }
    }

    /**
     * Searches for all specified bookmarks (RP snapshots). If even just one
     * bookmark does not exist, an exception will be thrown.
     *
     * @param protectionDevice
     *            the protection system URI
     * @param snapshots
     *            the RP snapshots to search for
     */
    private void searchForBookmarks(URI protectionDevice, Set<URI> snapshots) {
        ProtectionSystem rpSystem = getRPSystem(protectionDevice);

        RecoverPointClient rpClient = RPHelper.getRecoverPointClient(rpSystem);

        // Check that the bookmarks actually exist
        Set<Integer> cgIDs = null;
        boolean bookmarkExists;

        // Map used to keep track of which BlockSnapshots map to which CGs
        Map<Integer, List<BlockSnapshot>> cgSnaps = new HashMap<Integer, List<BlockSnapshot>>();

        for (URI snapshotID : snapshots) {
            cgIDs = new HashSet<Integer>();

            BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotID);

            // Get the volume associated with this snapshot
            Volume volume = _dbClient.queryObject(Volume.class, snapshot.getParent().getURI());

            // Now get the protection set (CG) associated with the volume so we can use
            // it to search for the bookmark
            ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());

            Integer cgID = null;

            try {
                cgID = Integer.valueOf(protectionSet.getProtectionId());
            } catch (NumberFormatException nfe) {
                throw DeviceControllerExceptions.recoverpoint.exceptionLookingForBookmarks(nfe);
            }

            cgIDs.add(cgID);

            if (cgSnaps.get(cgID) == null) {
                cgSnaps.put(cgID, new ArrayList<BlockSnapshot>());
            }

            cgSnaps.get(cgID).add(snapshot);
        }

        GetBookmarksResponse bookmarkResponse = rpClient.getRPBookmarks(cgIDs);

        // Iterate over the BlockSnapshots for each CG and determine if each
        // one exists in RP. Fail if any of the snapshots does not exist.
        for (Integer cgID : cgSnaps.keySet()) {
            for (BlockSnapshot snapshot : cgSnaps.get(cgID)) {
                bookmarkExists = false;

                if (bookmarkResponse.getCgBookmarkMap() != null && !bookmarkResponse.getCgBookmarkMap().isEmpty()) {
                    List<RPBookmark> rpBookmarks = bookmarkResponse.getCgBookmarkMap().get(cgID);

                    if (rpBookmarks != null && !rpBookmarks.isEmpty()) {
                        // Find the bookmark
                        for (RPBookmark rpBookmark : rpBookmarks) {
                            if (rpBookmark.getBookmarkName().equals(snapshot.getEmName())) {
                                bookmarkExists = true;
                            }
                        }
                    }
                }

                if (!bookmarkExists) {
                    throw DeviceControllerExceptions.recoverpoint.failedToFindExpectedBookmarks();
                }
            }
        }

    }

    @Override
    public void findAndUpdateFreeHLUsForClusterExport(StorageSystem storageSystem, ExportGroup exportGroup,
            List<URI> initiatorURIs, Map<URI, Integer> volumeMap) {
        // TODO
    }

    @Override
    public void exportGroupChangePathParams(URI storageURI, URI exportGroupURI,
            URI volumeURI, String token) throws Exception {
        // Not supported, will not be called because API code not present
    }

    @Override
    public void increaseMaxPaths(Workflow workflow, StorageSystem storageSystem, ExportGroup exportGroup, ExportMask exportMask,
            List<URI> newInitiators, String token) throws Exception {
        // Not supported, will not be called because API code not present
    }

    /**
     * Returns the Storage Ports on the Storage device that should be used for a particular
     * storage array. This is done by finding ports in the array and RP initiators that have
     * common Networks. Returns a map of NetworkURI to List<StoragePort>.
     *
     * @param rpInitiatorNetworkURI
     *            The URI of network where this RP site is in
     * @param arrayURI
     *            The URI of a connected backend storage system.
     * @param varrayURI
     *            The URI of the virtual array.
     *
     * @return Map<URI, List<StoragePort>> A map of Network URI to a List<StoragePort>
     */
    private Map<URI, List<StoragePort>> getInitiatorPortsForArray(Map<URI, Set<Initiator>> rpNetworkToInitiatorMap, URI arrayURI,
            URI varray, String internalSiteName) throws ControllerException {

        Map<URI, List<StoragePort>> initiatorMap = new HashMap<URI, List<StoragePort>>();

        // Then get the front end ports on the Storage array.
        Map<URI, List<StoragePort>> arrayTargetMap = ConnectivityUtil.getStoragePortsOfType(_dbClient, arrayURI,
                StoragePort.PortType.frontend);

        // Eliminate any storage ports that are not explicitly assigned
        // or implicitly connected to the passed varray.
        Set<URI> arrayTargetNetworks = new HashSet<URI>();
        arrayTargetNetworks.addAll(arrayTargetMap.keySet());
        Iterator<URI> arrayTargetNetworksIter = arrayTargetNetworks.iterator();
        while (arrayTargetNetworksIter.hasNext()) {
            URI networkURI = arrayTargetNetworksIter.next();
            Iterator<StoragePort> targetStoragePortsIter = arrayTargetMap.get(networkURI).iterator();
            while (targetStoragePortsIter.hasNext()) {
                StoragePort targetStoragePort = targetStoragePortsIter.next();
                StringSet taggedVArraysForPort = targetStoragePort.getTaggedVirtualArrays();
                if ((taggedVArraysForPort == null) || (!taggedVArraysForPort.contains(varray.toString()))) {
                    targetStoragePortsIter.remove();
                }
            }

            // If the entry for this network is now empty then
            // remove the entry from the target storage port map.
            if (arrayTargetMap.get(networkURI).isEmpty()) {
                arrayTargetMap.remove(networkURI);
            }
        }

        // Get all the ports corresponding to the network that the RP initiators are in.
        // we will use all available ports
        for (URI rpInitiatorNetworkURI : rpNetworkToInitiatorMap.keySet()) {
            if (arrayTargetMap.keySet().contains(rpInitiatorNetworkURI)) {
                initiatorMap.put(rpInitiatorNetworkURI, arrayTargetMap.get(rpInitiatorNetworkURI));
            }
        }

        // If there are no initiator ports, fail the operation, because we cannot zone.
        if (initiatorMap.isEmpty()) {
            throw RecoverPointException.exceptions.getInitiatorPortsForArrayFailed(internalSiteName,
                    _dbClient.queryObject(StorageSystem.class, arrayURI).getLabel());
        }

        return initiatorMap;
    }

    /**
     * Compute the number of paths to use on the back end array.
     * This is done on a per Network basis and then summed together.
     * Within each Network, we determine the number of ports available, and then
     * convert to paths. Currently we don't allocate more paths than initiators.
     *
     * @param initiatorPortMap
     *            -- used to determine networks and initiator counts
     * @param varray
     *            -- only Networks in the specified varray are considered
     * @param array
     *            -- StorageSystem -- used to determine available ports
     * @return
     */
    private Integer computeNumPaths(Map<URI, List<StoragePort>> initiatorPortMap, URI varray, StorageSystem array) {
        // Get the number of ports per path.
        StoragePortsAssigner assigner = StoragePortsAssignerFactory.getAssigner(array.getSystemType());
        int portsPerPath = assigner.getNumberOfPortsPerPath();
        // Get the array's front end ports for this varray only
        Map<URI, List<StoragePort>> arrayTargetMap = ConnectivityUtil.getStoragePortsOfTypeAndVArray(_dbClient, array.getId(),
                StoragePort.PortType.frontend, varray);

        int numPaths = 0;
        for (URI networkURI : initiatorPortMap.keySet()) {
            if (arrayTargetMap.get(networkURI) != null) {
                int pathsInNetwork = arrayTargetMap.get(networkURI).size() / portsPerPath;
                int initiatorsInNetwork = initiatorPortMap.get(networkURI).size();
                if (pathsInNetwork > initiatorsInNetwork) {
                    pathsInNetwork = initiatorsInNetwork;
                }
                _log.info(String.format("Network %s has %s paths", networkURI, pathsInNetwork));
                numPaths += pathsInNetwork;
            } else {
                _log.info(String.format("Storage Array %s has no ports in Network %s", array.getNativeGuid(), networkURI));
            }
        }
        return numPaths;
    }

    @Override
    public String addStepsForExpandVolume(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeURIs, String taskId)
            throws InternalException {
        // There are no RP specific operations done during the expand process.
        // Most of what is required from RP as part of the volume expand is handled in Pre and Post Expand steps.
        return null;
    }

    @Override
    public String addStepsForChangeVirtualArray(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId)
            throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    /**
     * Adds steps for modifying a RP CG.
     *
     * @param workflow
     *            The current workflow
     * @param volumeDescriptors
     *            VolumeDescriptors for the operation
     * @param cgParams
     *            Current CG params
     * @param rpSystem
     *            The RecoverPoint system
     * @param taskId
     *            Current task id
     * @return The step group
     * @throws InternalException
     */
    private String addModifyCGStep(Workflow workflow, List<VolumeDescriptor> volumeDescriptors, CGRequestParams cgParams,
            ProtectionSystem rpSystem, String taskId) throws InternalException {
        String stepId = workflow.createStepId();
        Workflow.Method cgCreationExecuteMethod = new Workflow.Method(METHOD_CG_MODIFY_STEP, rpSystem.getId(), volumeDescriptors, cgParams);
        Workflow.Method cgCreationExecutionRollbackMethod = new Workflow.Method(METHOD_CG_MODIFY_ROLLBACK_STEP, rpSystem.getId());

        workflow.createStep(STEP_CG_MODIFY, "Modify consistency group subtask for RP CG: " + cgParams.getCgName(),
                STEP_EXPORT_ORCHESTRATION, rpSystem.getId(), rpSystem.getSystemType(), this.getClass(), cgCreationExecuteMethod,
                cgCreationExecutionRollbackMethod, stepId);

        return STEP_CG_MODIFY;
    }

    /**
     * Workflow step method for modifying a consistency group.
     *
     * @param rpSystemId
     *            RP system Id
     * @param recommendation
     *            parameters needed to create the CG
     * @param token
     *            the task
     * @return true if the operation is a success, false otherwise
     * @throws InternalException
     */
    public boolean cgModifyStep(URI rpSystemId, List<VolumeDescriptor> volumeDescriptors, CGRequestParams cgParams, String token)
            throws InternalException {
        try {
            RPHelper.setLinkStateWaitTimeOut(_coordinator);
            // Get only the RP_EXISTING_PROTECTED_SOURCE descriptors
            List<VolumeDescriptor> existingProtectedSourceVolumeDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_EXISTING_PROTECTED_SOURCE }, new VolumeDescriptor.Type[] {});

            WorkflowStepCompleter.stepExecuting(token);
            _log.info("Modify CG step executing");

            ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, rpSystemId);

            if (!existingProtectedSourceVolumeDescriptors.isEmpty()) {
                // Get the first descriptor, that's all we need. This operation will
                // affect all the RSets in the CG by adding a new standby copy.
                VolumeDescriptor descriptor = existingProtectedSourceVolumeDescriptors.get(0);

                Volume sourceVolume = _dbClient.queryObject(Volume.class, descriptor.getVolumeURI());

                URI newVpoolURI = (URI) descriptor.getParameters().get(VolumeDescriptor.PARAM_VPOOL_CHANGE_NEW_VPOOL_ID);
                URI oldVPoolURI = (URI) descriptor.getParameters().get(VolumeDescriptor.PARAM_VPOOL_CHANGE_OLD_VPOOL_ID);

                VirtualPool newVpool = _dbClient.queryObject(VirtualPool.class, newVpoolURI);
                VirtualPool oldVpool = _dbClient.queryObject(VirtualPool.class, oldVPoolURI);

                // Phase 1 - Only support upgrade from RP+VPLEX to MetroPoint.
                // This includes:
                // Adding a secondary journal and possibly adding MP targets to an existing RP+VPLEX CG
                // as it is non-disruptive. Further CG Updates will be considered in the future.
                if (VirtualPool.vPoolSpecifiesRPVPlex(oldVpool) && !VirtualPool.vPoolSpecifiesMetroPoint(oldVpool)
                        && VirtualPool.vPoolSpecifiesMetroPoint(newVpool)) {
                    upgradeRPVPlexToMetroPoint(sourceVolume, newVpool, oldVpool, rpSystem);
                }

                // Update the ProtectionSet with any newly added protection set objects
                // TODO support remove as well?
                ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, sourceVolume.getProtectionSet());
                updateProtectionSet(protectionSet, cgParams);
            }

            // Collect and update the protection system statistics to account for
            // the newly updated CG
            _log.info("Collecting RP statistics post CG update.");
            collectRPStatistics(rpSystem);

            // Update the workflow state.
            _log.info("Modify CG step completed");
            WorkflowStepCompleter.stepSucceded(token);
        } catch (Exception e) {
            _log.error("Failed modifying cg: " + e.getStackTrace());
            doFailCgModifyStep(volumeDescriptors, cgParams, rpSystemId, token, e);
            return false;
        }
        return true;
    }

    /**
     * Upgrades a RP+VPLEX CG to MetroPoint by adding a standby journal to the HA side.
     *
     * Prerequisite: All RSets(volumes) in the CG must have had their HA sides already exported to RP in VPLEX.
     *
     * @param sourceVolume
     *            A single source volume from the CG, we only need one.
     * @param rpSystem
     *            The rpSystem we're using
     */
    private void upgradeRPVPlexToMetroPoint(Volume sourceVolume, VirtualPool newVpool, VirtualPool oldVpool, ProtectionSystem rpSystem) {
        // Grab the new standby journal from the CG
        String standbyCopyName = RPHelper.getStandbyProductionCopyName(_dbClient, sourceVolume);
        List<Volume> existingStandbyJournals = RPHelper.findExistingJournalsForCopy(_dbClient, sourceVolume.getConsistencyGroup(),
                standbyCopyName);

        if (existingStandbyJournals.isEmpty()) {
            _log.error(String.format("Could not find standby journal during upgrade to MetroPoint operation. "
                    + "Expected to find a new standby journal for RP copy [%s]", standbyCopyName));
            throw RecoverPointException.exceptions.cannotFindJournal(String.format("for RP copy [%s]", standbyCopyName));
        }

        Volume standbyProdJournal = existingStandbyJournals.get(0);

        // Add new standby journal
        if (standbyProdJournal != null) {
            _log.info(String.format("Upgrade RP+VPLEX CG to MetroPoint by adding new standby journal [%s] to the CG",
                    standbyProdJournal.getLabel()));
            RecoverPointClient rp = RPHelper.getRecoverPointClient(rpSystem);

            RecoverPointVolumeProtectionInfo protectionInfo = rp
                    .getProtectionInfoForVolume(RPHelper.getRPWWn(sourceVolume.getId(), _dbClient));
            _log.info(String.format("RecoverPointVolumeProtectionInfo [%s] retrieved", protectionInfo.getRpProtectionName()));

            RPCopyRequestParams copyParams = new RPCopyRequestParams();
            copyParams.setCopyVolumeInfo(protectionInfo);

            List<CreateVolumeParams> journaVols = new ArrayList<CreateVolumeParams>();
            CreateVolumeParams journalVolParams = new CreateVolumeParams();
            journalVolParams.setWwn(RPHelper.getRPWWn(standbyProdJournal.getId(), _dbClient));
            journalVolParams.setInternalSiteName(standbyProdJournal.getInternalSiteName());
            journaVols.add(journalVolParams);

            CreateCopyParams standbyProdCopyParams = new CreateCopyParams();
            standbyProdCopyParams.setName(standbyProdJournal.getRpCopyName());
            standbyProdCopyParams.setJournals(journaVols);

            _log.info(String.format("Adding standby journal [%s] to teh RP CG...", standbyProdJournal.getLabel()));

            // TODO BH - Empty, not sure why we need this
            List<CreateRSetParams> rSets = new ArrayList<CreateRSetParams>();

            rp.addStandbyProductionCopy(standbyProdCopyParams, null, rSets, copyParams);
            _log.info("Standby journal added successfully.");

            // TODO Add new Targets if they exist ??

            // Next we need to update the vpool reference of any existing related volumes
            // that were referencing the old vpool.
            // We'll start by getting all source volumes from the ViPR CG
            BlockConsistencyGroup viprCG = _dbClient.queryObject(BlockConsistencyGroup.class, sourceVolume.getConsistencyGroup());
            List<Volume> allSourceVolumesInCG = BlockConsistencyGroupUtils.getActiveVplexVolumesInCG(viprCG, _dbClient,
                    Volume.PersonalityTypes.SOURCE);

            for (Volume sourceVol : allSourceVolumesInCG) {
                // For each source volume, we'll get all the related volumes (Targets, Journals, Backing volumes for
                // VPLEX...etc)
                Set<Volume> allRelatedVolumes = RPHelper.getAllRelatedVolumesForSource(sourceVol.getId(), _dbClient, true, true);
                // For each volume related to the source, check to see if it is referencing the old vpool.
                // If it is, update the reference and persist the change.
                for (Volume rpRelatedVol : allRelatedVolumes) {
                    if (rpRelatedVol.getVirtualPool().equals(oldVpool.getId())) {
                        rpRelatedVol.setVirtualPool(newVpool.getId());
                        _dbClient.updateObject(rpRelatedVol);
                        _log.info(String.format("Volume [%s] has had its virtual pool updated to [%s].", rpRelatedVol.getLabel(),
                                newVpool.getLabel()));
                    }
                }
            }
        }
    }

    /**
     * Workflow step method for creating a consistency group.
     *
     * @param rpSystem
     *            RP system
     * @param params
     *            parameters needed to create the CG
     * @param token
     *            the task
     * @return true if the operation is a success, false otherwise
     * @throws WorkflowException
     */
    public boolean cgModifyRollbackStep(URI rpSystemId, String token) throws WorkflowException {
        // nothing to do for now.
        WorkflowStepCompleter.stepSucceded(token);
        return true;
    }

    /**
     * Process failure of modifying a cg step.
     *
     * @param volumeDescriptors
     *            volumes
     * @param cgParams
     *            cg parameters
     * @param protectionSetId
     *            protection set id
     * @param token
     *            task ID for audit
     * @param e
     *            exception
     * @throws InternalException
     */
    private void doFailCgModifyStep(List<VolumeDescriptor> volumeDescriptors, CGRequestParams cgParams, URI protectionSetId, String token,
            Exception e) throws InternalException {
        // Record Audit operation. (vpool change only)
        if (VolumeDescriptor.getVirtualPoolChangeVolume(volumeDescriptors) != null) {
            AuditBlockUtil.auditBlock(_dbClient, OperationTypeEnum.CHANGE_VOLUME_VPOOL, true, AuditLogManager.AUDITOP_END, token);
        }
        stepFailed(token, e, METHOD_CG_MODIFY_STEP);
    }

    /**
     * Creates a rollback workflow method that does nothing, but allows rollback
     * to continue to prior steps back up the workflow chain.
     *
     * @return A workflow method
     */
    private Workflow.Method rollbackMethodNullMethod() {
        return new Workflow.Method(ROLLBACK_METHOD_NULL);
    }

    /**
     * The null rollback method. Simply marks the step as succeeded.
     *
     * @param stepId
     *            the step id.
     * @throws WorkflowException
     */
    public void rollbackMethodNull(String stepId) throws WorkflowException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }

    /**
     * Step to remove protection on RP Source volumes
     *
     * @param workflow
     *            The current WF
     * @param waitFor
     *            The previous waitFor step ID or Group
     * @param volumeDescriptors
     *            RP Source volume descriptors
     * @param taskId
     *            The Task ID
     * @param blockDeviceController
     *            Reference to a BlockDeviceController, used for specific steps on
     *            the volumes not covered by RP but required for the operation to be complete.
     * @return The next waitFor step ID or Group
     */
    private String addRemoveProtectionOnVolumeStep(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors,
            String taskId, BlockDeviceController blockDeviceController) {
        List<URI> volumeURIs = new ArrayList<URI>();
        URI newVpoolURI = null;

        // Filter to get only the RP Source volumes.
        List<VolumeDescriptor> rpSourceDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_SOURCE, VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE },
                new VolumeDescriptor.Type[] {});

        for (VolumeDescriptor descriptor : rpSourceDescriptors) {
            if (descriptor.getParameters().get(VolumeDescriptor.PARAM_DO_NOT_DELETE_VOLUME) != null) {
                // This is a rollback protection operation. We do not want to delete the volume but we do
                // want to remove protection from it.
                newVpoolURI = (URI) descriptor.getParameters().get(VolumeDescriptor.PARAM_VPOOL_CHANGE_NEW_VPOOL_ID);
                _log.info(String.format("Adding step to remove protection from Volume (%s) and move it to vpool (%s)",
                        descriptor.getVolumeURI(), newVpoolURI));
                volumeURIs.add(descriptor.getVolumeURI());
            }
        }

        if (volumeURIs.isEmpty()) {
            return waitFor;
        }

        // Filter to get only the Block Data volumes
        List<VolumeDescriptor> blockDataDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_DATA }, new VolumeDescriptor.Type[] {});

        // Check to see if there are any BLOCK_DATA volumes flagged to not be fully deleted.
        // These volumes could potentially need to have some untag operation performed
        // on the underlying array even though they won't be deleted.
        List<VolumeDescriptor> doNotDeleteDescriptors = VolumeDescriptor.getDoNotDeleteDescriptors(blockDataDescriptors);

        // Breakup the descriptors further into RP and RP+VPLEX descriptors
        List<VolumeDescriptor> rpDescriptors = new ArrayList<VolumeDescriptor>();
        List<VolumeDescriptor> rpVPlexDescriptors = new ArrayList<VolumeDescriptor>();
        for (VolumeDescriptor descr : doNotDeleteDescriptors) {
            Volume volume = _dbClient.queryObject(Volume.class, descr.getVolumeURI());
            // Check to see if this volume is associated to a RP+VPLEX Source volume.
            if (RPHelper.isAssociatedToRpVplexType(volume, _dbClient, PersonalityTypes.SOURCE)) {
                rpVPlexDescriptors.add(descr);
            } else {
                rpDescriptors.add(descr);
            }
        }

        if (doNotDeleteDescriptors != null && !doNotDeleteDescriptors.isEmpty()) {
            // Call the BlockDeviceController to perform untag operations on the volumes.
            // NOTE: Only needed for RP volumes.
            waitFor = blockDeviceController.addStepsForUntagVolumes(workflow, waitFor, rpDescriptors, taskId);

            // Call the BlockDeviceController to remove the volumes from any backend array CGs.
            // NOTE: Only needed for RP+VPLEX/MP volumes.
            waitFor = blockDeviceController.addStepsForUpdateConsistencyGroup(workflow, waitFor, null, rpVPlexDescriptors);
        }

        // Grab any volume from the list so we can grab the protection system. This
        // request could be over multiple protection systems but we don't really
        // care at this point. We just need this reference to pass into the
        // WorkFlow.
        Volume volume = _dbClient.queryObject(Volume.class, volumeURIs.get(0));
        ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, volume.getProtectionController());

        String stepId = workflow.createStepId();
        Workflow.Method removeProtectionExecuteMethod = new Workflow.Method(METHOD_REMOVE_PROTECTION_STEP, volumeURIs, newVpoolURI);

        workflow.createStep(STEP_REMOVE_PROTECTION, "Remove RP protection on volume(s)", waitFor, rpSystem.getId(),
                rpSystem.getSystemType(), this.getClass(), removeProtectionExecuteMethod, null, stepId);

        return STEP_REMOVE_PROTECTION;
    }

    /**
     * Removes ViPR level protection attributes from RP Source volumes
     *
     * @param volumes
     *            All volumes to remove protection attributes from
     * @param newVpoolURI
     *            The vpool to move this volume to
     * @param stepId
     *            The step id in this WF
     */
    public boolean removeProtectionStep(List<URI> volumeURIs, URI newVpoolURI, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        try {
            for (URI volumeURI : volumeURIs) {
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);

                if (RPHelper.isVPlexVolume(volume, _dbClient)) {
                    // We might need to update the vpools of the backing volumes after the
                    // change vpool operation to remove protection
                    VPlexUtil.updateVPlexBackingVolumeVpools(volume, newVpoolURI, _dbClient);
                }

                // Rollback protection on the volume
                VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, newVpoolURI);
                _log.info(String.format("Removing protection from Volume [%s] (%s) and moving it to Virtual Pool [%s] (%s)",
                        volume.getLabel(), volume.getId(), vpool.getLabel(), vpool.getId()));
                // Rollback Protection on the volume
                RPHelper.rollbackProtectionOnVolume(volume, vpool, _dbClient);
            }

            WorkflowStepCompleter.stepSucceded(stepId);
            return true;
        } catch (Exception e) {
            stepFailed(stepId, e, "removeProtection operation failed.");
            return false;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.protectioncontroller.RPController#updateApplication(java.net.URI,
     * com.emc.storageos.volumecontroller.ApplicationAddVolumeList, java.util.List, java.net.URI, java.lang.String)
     */
    @Override
    public void updateApplication(URI systemURI, ApplicationAddVolumeList addVolList, List<URI> removeVolumeURIs, URI applicationId,
            String taskId) {

        // get all source and target devices
        // for remove volumes source and targets can be processed in the same step
        // for add volumes, split up volumes into source and target
        // assign a different replication group name for target volumes so they don't end up in the same group as source
        // volumes
        // create one step for remove volumes and add source volumes and a separate step for add target volumes

        TaskCompleter completer = null;

        try {
            Set<URI> impactedCGs = new HashSet<URI>();
            List<URI> allRemoveVolumes = new ArrayList<URI>();
            Set<URI> removeVolumeSet = new HashSet<URI>();
            if (removeVolumeURIs != null && !removeVolumeURIs.isEmpty()) {
                // get source and target volumes to be removed from the application
                removeVolumeSet = RPHelper.getReplicationSetVolumes(removeVolumeURIs, _dbClient);
                for (URI removeUri : removeVolumeSet) {
                    Volume removeVol = _dbClient.queryObject(Volume.class, removeUri);
                    URI cguri = removeVol.getConsistencyGroup();
                    impactedCGs.add(cguri);
                    addBackendVolumes(removeVol, false, allRemoveVolumes, null);
                }
            }

            Set<URI> vplexVolumes = new HashSet<URI>();
            Set<URI> addVolumeSet = new HashSet<URI>();

            ApplicationAddVolumeList addSourceVols = new ApplicationAddVolumeList();
            ApplicationAddVolumeList addTargetVols = new ApplicationAddVolumeList();
            boolean existingSnapOrClone = false;
            URI protectionSystemId = null;
            ProtectionSystem protectionSystem = null;
            Set<String> volumeWWNs = new HashSet<String>();
            Volume aSrcVolume = null;
            if (addVolList != null && addVolList.getVolumes() != null && !addVolList.getVolumes().isEmpty()) {
                URI addVolCg = null;
                // get source and target volumes to be added the application
                addVolumeSet = RPHelper.getReplicationSetVolumes(addVolList.getVolumes(), _dbClient);
                // split up add volumes list by source and target
                List<URI> allAddSourceVolumes = new ArrayList<URI>();
                List<URI> allAddTargetVolumes = new ArrayList<URI>();
                for (URI volUri : addVolumeSet) {
                    Volume vol = _dbClient.queryObject(Volume.class, volUri);
                    if (protectionSystemId == null) {
                        protectionSystemId = vol.getProtectionController();
                    }
                    URI cguri = vol.getConsistencyGroup();
                    if (addVolCg == null && cguri != null) {
                        addVolCg = cguri;
                    }
                    impactedCGs.add(cguri);
                    if (vol.checkPersonality(Volume.PersonalityTypes.SOURCE.name())) {
                        addBackendVolumes(vol, true, allAddSourceVolumes, vplexVolumes);
                        aSrcVolume = vol;
                    } else if (vol.checkPersonality(Volume.PersonalityTypes.TARGET.name())) {
                        addBackendVolumes(vol, true, allAddTargetVolumes, vplexVolumes);
                        volumeWWNs.add(RPHelper.getRPWWn(vol.getId(), _dbClient));
                    }
                }

                if (protectionSystemId != null) {
                    protectionSystem = _dbClient.queryObject(ProtectionSystem.class, protectionSystemId);
                }

                addSourceVols.setConsistencyGroup(addVolCg);
                addSourceVols.setReplicationGroupName(addVolList.getReplicationGroupName());
                addSourceVols.setVolumes(allAddSourceVolumes);

                String targetReplicationGroupName = addVolList.getReplicationGroupName() + REPLICATION_GROUP_RPTARGET_SUFFIX;
                addTargetVols.setConsistencyGroup(addVolCg);
                addTargetVols.setReplicationGroupName(targetReplicationGroupName);
                addTargetVols.setVolumes(allAddTargetVolumes);

                // if there are any target clones or snapshots, need to create a bookmark and enable image access
                List<Volume> existingVols = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, Volume.class,
                        AlternateIdConstraint.Factory.getVolumeByReplicationGroupInstance(targetReplicationGroupName));
                for (Volume existingVol : existingVols) {
                    if (existingVol.getFullCopies() != null && !existingVol.getFullCopies().isEmpty()) {
                        existingSnapOrClone = true;
                        break;
                    } else if (ControllerUtils.checkIfVolumeHasSnapshotSession(existingVol.getId(), _dbClient)) {
                        existingSnapOrClone = true;
                        break;
                    } else if (ControllerUtils.checkIfVolumeHasSnapshot(existingVol, _dbClient)) {
                        existingSnapOrClone = true;
                        break;
                    }
                }
            }

            // Get a new workflow to execute the volume group update.
            Workflow workflow = _workflowService.getNewWorkflow(this, BlockDeviceController.UPDATE_VOLUMES_FOR_APPLICATION_WS_NAME, false,
                    taskId);

            // create the completer add the steps and execute the plan.
            completer = new VolumeGroupUpdateTaskCompleter(applicationId, addVolumeSet, removeVolumeSet, impactedCGs, taskId);
            String waitFor = null;

            if (existingSnapOrClone) {
                // A temporary date/time stamp for the bookmark name
                String bookmarkName = VIPR_SNAPSHOT_PREFIX + (new Random()).nextInt();

                // Step 1 - Create a RP bookmark
                String rpWaitFor = addCreateBookmarkStep(workflow, new ArrayList<URI>(), protectionSystem, bookmarkName, volumeWWNs, false,
                        waitFor);

                // Lock CG for the duration of the workflow so enable and disable can complete before another workflow
                // tries to enable image
                // access
                List<String> locks = new ArrayList<String>();
                String lockName = ControllerLockingUtil.getConsistencyGroupStorageKey(_dbClient, aSrcVolume.getConsistencyGroup(),
                        protectionSystem.getId());
                if (null != lockName) {
                    locks.add(lockName);
                    acquireWorkflowLockOrThrow(workflow, locks);
                }

                // Step 2 - Enable image access
                waitFor = addEnableImageAccessForCreateReplicaStep(workflow, protectionSystem, null, new ArrayList<URI>(), bookmarkName,
                        volumeWWNs, rpWaitFor);
            }

            // add steps for add source and remove vols
            waitFor = _blockDeviceController.addStepsForUpdateApplication(workflow, addSourceVols, allRemoveVolumes, waitFor, taskId);

            // add steps for add target vols
            waitFor = _blockDeviceController.addStepsForUpdateApplication(workflow, addTargetVols, null, waitFor, taskId);

            if (existingSnapOrClone) {
                waitFor = addDisableImageAccessForCreateReplicaStep(workflow, protectionSystem, null, new ArrayList<URI>(), volumeWWNs,
                        waitFor);
            }

            if (!vplexVolumes.isEmpty()) {
                _vplexDeviceController.addStepsForImportClonesOfApplicationVolumes(workflow, waitFor, new ArrayList<URI>(vplexVolumes),
                        taskId);
            }

            _log.info("Executing workflow plan {}", BlockDeviceController.UPDATE_VOLUMES_FOR_APPLICATION_WS_NAME);
            String successMessage = String.format("Update application successful for %s", applicationId.toString());
            workflow.executePlan(completer, successMessage);
        } catch (Exception e) {
            _log.error("Exception while updating the application", e);
            if (completer != null) {
                completer.error(_dbClient,
                        DeviceControllerException.exceptions.failedToUpdateVolumesFromAppication(applicationId.toString(), e.getMessage()));
            }
            throw e;
        }
    }

    /**
     * Add block(backend) volumes if the volume is a VPLEX volume to the Block volume list to call to
     * BlockDeviceController.
     *
     * @param volume
     *            The volume will be processed
     * @param isAdd
     *            if the volume is for add or remove
     * @param allVolumes
     *            output all block volume list
     * @param vplexVolumes
     *            output all vplex volumes whose backend volumes are not in RG
     */
    private void addBackendVolumes(Volume volume, boolean isAdd, List<URI> allVolumes, Set<URI> vplexVolumes) {
        if (RPHelper.isVPlexVolume(volume, _dbClient)) {
            StringSet backends = volume.getAssociatedVolumes();
            if (null == backends || backends.isEmpty()) {
                _log.error("VPLEX volume {} has no backend volumes.", volume.forDisplay());
                throw InternalServerErrorException.internalServerErrors.noAssociatedVolumesForVPLEXVolume(volume.forDisplay());
            }
            for (String backendId : backends) {
                URI backendUri = URI.create(backendId);
                allVolumes.add(backendUri);
                if (isAdd && !vplexVolumes.contains(volume.getId())) {
                    Volume backVol = _dbClient.queryObject(Volume.class, backendUri);
                    if (backVol != null && !backVol.getInactive()
                            && NullColumnValueGetter.isNullValue(backVol.getReplicationGroupInstance())) {
                        vplexVolumes.add(volume.getId());
                    }
                }
            }
        } else {
            allVolumes.add(volume.getId());
        }
    }

    /**
     * Adds the necessary RecoverPoint controller steps that need to be executed prior
     * to restoring a volume from full copy. The pre-restore step is required if we
     * are restoring a VPLEX distributed or VMAX full copy
     *
     * @param workflow
     *            the Workflow being constructed
     * @param waitFor
     *            the step that the newly created steps will wait for.
     * @param storageSystemURI
     *            the URI of storage controller
     * @param fullCopies
     *            the URI of full copies to restore
     * @param taskId
     *            the top level operation's taskId
     * @return A waitFor key that can be used by subsequent controllers to wait on
     */
    public String addPreRestoreFromFullcopySteps(Workflow workflow, String waitFor, URI storageSystemURI, List<URI> fullCopies,
            String taskId) {
        if (fullCopies != null && !fullCopies.isEmpty()) {
            List<Volume> sourceVolumes = checkIfDistributedVplexOrVmaxFullCopies(fullCopies);
            if (!sourceVolumes.isEmpty()) {
                Map<String, RecreateReplicationSetRequestParams> rsetParams = new HashMap<String, RecreateReplicationSetRequestParams>();
                List<URI> volumeURIs = new ArrayList<URI>();
                URI rpSystemId = sourceVolumes.get(0).getProtectionController();
                ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, rpSystemId);
                for (Volume vol : sourceVolumes) {
                    RecreateReplicationSetRequestParams rsetParam = getReplicationSettings(rpSystem, vol.getId());
                    rsetParams.put(RPHelper.getRPWWn(vol.getId(), _dbClient), rsetParam);
                    volumeURIs.add(vol.getId());
                }
                // Lock CG
                List<String> locks = new ArrayList<String>();
                String lockName = ControllerLockingUtil.getConsistencyGroupStorageKey(_dbClient,
                        sourceVolumes.get(0).getConsistencyGroup(), rpSystem.getId());
                if (null != lockName) {
                    locks.add(lockName);
                    acquireWorkflowLockOrThrow(workflow, locks);
                }

                String stepId = workflow.createStepId();
                Workflow.Method deleteRsetExecuteMethod = new Workflow.Method(METHOD_DELETE_RSET_STEP, rpSystem.getId(), volumeURIs);

                // rollback method for deleteRset. If deleteRest fails, recreate the Rset
                Workflow.Method recreateRSetExecuteMethod = new Workflow.Method(METHOD_RECREATE_RSET_STEP, rpSystem.getId(), volumeURIs,
                        rsetParams);

                waitFor = workflow.createStep(STEP_PRE_VOLUME_RESTORE,
                        "Pre volume restore from full copy, delete replication set step for RP", waitFor, rpSystem.getId(),
                        rpSystem.getSystemType(), this.getClass(), deleteRsetExecuteMethod, recreateRSetExecuteMethod, stepId);

                _log.info("Created workflow step to delete replication set for volumes");

            }
        }

        return waitFor;
    }

    /**
     * Adds the necessary RecoverPoint controller steps that need to be executed after
     * restoring a volume from full copy. The post-restore step is required if we
     * are restoring a VPLEX full copy, whoes source volume is a distributed VPLEX volume
     * or a VMAX volume
     *
     * @param workflow
     *            the Workflow being constructed
     * @param waitFor
     *            the step that the newly created steps will wait for.
     * @param storageSystemURI
     *            the URI of storage controller
     * @param fullCopies
     *            the URI of full copies to restore
     * @param taskId
     *            the top level operation's taskId
     * @return A waitFor key that can be used by subsequent controllers to wait on
     */
    public String addPostRestoreFromFullcopySteps(Workflow workflow, String waitFor, URI storageSystemURI, List<URI> fullCopies,
            String taskId) {
        if (fullCopies != null && !fullCopies.isEmpty()) {
            List<Volume> sourceVolumes = checkIfDistributedVplexOrVmaxFullCopies(fullCopies);
            if (!sourceVolumes.isEmpty()) {
                Map<String, RecreateReplicationSetRequestParams> rsetParams = new HashMap<String, RecreateReplicationSetRequestParams>();
                List<URI> volumeURIs = new ArrayList<URI>();
                URI rpSystemId = sourceVolumes.get(0).getProtectionController();
                ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, rpSystemId);
                for (Volume vol : sourceVolumes) {
                    RecreateReplicationSetRequestParams rsetParam = getReplicationSettings(rpSystem, vol.getId());
                    rsetParams.put(RPHelper.getRPWWn(vol.getId(), _dbClient), rsetParam);
                    volumeURIs.add(vol.getId());
                }

                String stepId = workflow.createStepId();

                Workflow.Method recreateRSetExecuteMethod = new Workflow.Method(METHOD_RECREATE_RSET_STEP, rpSystemId, volumeURIs,
                        rsetParams);

                waitFor = workflow.createStep(STEP_PRE_VOLUME_RESTORE,
                        "Post volume restore from full copy, add replication set step for RP", waitFor, rpSystemId,
                        rpSystem.getSystemType(), this.getClass(), recreateRSetExecuteMethod, rollbackMethodNullMethod(), stepId);

                _log.info("Created workflow step to recreate replication set for volumes");

            }
        }

        return waitFor;
    }

    /**
     * Check if the full copies source volumes are distributed vplex volumes or VMAX volume
     *
     * @param fullcopies
     *            - URI of full copies
     * @return - the source volumes that are vmax or vplex distributed volumes
     */
    private List<Volume> checkIfDistributedVplexOrVmaxFullCopies(List<URI> fullcopies) {
        List<Volume> sourceVolumes = new ArrayList<Volume>();

        for (URI fullCopyUri : fullcopies) {
            Volume fullCopy = _dbClient.queryObject(Volume.class, fullCopyUri);
            if (fullCopy != null) {
                boolean toadd = false;
                URI volume = fullCopy.getAssociatedSourceVolume();
                Volume sourceVol = _dbClient.queryObject(Volume.class, volume);
                if (sourceVol != null) {
                    if (!sourceVol.checkForRp()) {
                        toadd = false;
                    } else if (sourceVol.getAssociatedVolumes() != null && sourceVol.getAssociatedVolumes().size() == 2) {
                        // RP + VPLEX distributed
                        toadd = true;
                    } else {
                        // RP + VMAX
                        URI storage = sourceVol.getStorageController();
                        if (!NullColumnValueGetter.isNullURI(storage)) {
                            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
                            if (storageSystem != null && storageSystem.getSystemType().equals(SystemType.vmax.name())) {
                                toadd = true;
                            }
                        } else {
                            _log.error(String.format("The source %s storage system is null", sourceVol.getLabel()));
                        }
                    }
                }

                // Only add the post-restore step if we are restoring a full copy whoes source
                // volume is a distributed vplex or vmax volume
                if (!NullColumnValueGetter.isNullURI(sourceVol.getProtectionController()) && toadd) {
                    ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, sourceVol.getProtectionController());
                    if (rpSystem == null) {
                        // Verify non-null storage device returned from the database client.
                        throw DeviceControllerExceptions.recoverpoint.failedConnectingForMonitoring(sourceVol.getProtectionController());
                    }

                    sourceVolumes.add(sourceVol);
                }
            }
        }
        return sourceVolumes;
    }

    /**
     * Attempts to acquire a workflow lock based on the RP lockname.
     *
     * @param workflow
     * @param locks
     * @throws LockRetryException
     */
    private void acquireWorkflowLockOrThrow(Workflow workflow, List<String> locks) throws LockRetryException {
        _log.info("Attempting to acquire workflow lock {}", Joiner.on(',').join(locks));
        _workflowService.acquireWorkflowLocks(workflow, locks, LockTimeoutValue.get(LockType.RP_CG));
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface#addStepsForCreateFullCopy(com.emc.
     * storageos.workflow.Workflow
     * , java.lang.String, java.util.List, java.lang.String)
     */
    @Override
    public String addStepsForCreateFullCopy(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws InternalException {
        // full copy steps are added with addStepsForPreCreateReplica and addStepsForPostCreateReplica
        return waitFor;
    }

    @Override
    public Map<URI, String> getCopyAccessStates(URI protectionSystemURI, List<URI> volumeURIs) {
        _log.info(String.format("Finding RecoverPoint copy states for volumes %s", volumeURIs));
        Map<URI, String> copyAccessStates = new HashMap<URI, String>();

        if (protectionSystemURI != null && volumeURIs != null) {
            // Validate that all volumeURIs share the same protection system that is passed in.
            // Also, create a WWN to volume URI map so we can tie the WWN volume access state back
            // to the ViPR volume URI.
            Map<String, URI> wwnToVolumeUri = new HashMap<String, URI>();

            for (URI volumeURI : volumeURIs) {
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                if (!protectionSystemURI.equals(volume.getProtectionController())) {
                    throw DeviceControllerExceptions.recoverpoint.failedToGetCopyAccessStateProtectionSystemMismatch(volume.getId(),
                            protectionSystemURI);
                }

                String wwn = RPHelper.getRPWWn(volumeURI, _dbClient);
                wwnToVolumeUri.put(wwn, volumeURI);
            }

            ProtectionSystem protectionSystem = _dbClient.queryObject(ProtectionSystem.class, protectionSystemURI);
            RecoverPointClient rp = RPHelper.getRecoverPointClient(protectionSystem);

            Map<String, String> wwnToAccessState = rp.getCopyAccessStates(wwnToVolumeUri.keySet());

            for (Map.Entry<String, String> wwnEntry : wwnToAccessState.entrySet()) {
                copyAccessStates.put(wwnToVolumeUri.get(wwnEntry.getKey()), wwnEntry.getValue());
            }
        }

        _log.info(String.format("Found the following RecoverPoint copy states %s", copyAccessStates));

        return copyAccessStates;
    }

    @Override
    public void portRebalance(URI storageSystem, URI exportGroup, URI varray, URI exportMask, Map<URI, List<URI>> adjustedpaths,
            Map<URI, List<URI>> removedPaths, boolean isAdd, String token) throws Exception {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
    
    /**
     * If the RP CG hasn't been created yet and the current request does not have 
     * the journal information, then wait. The request can not proceed without
     * journals.
     * 
     * Waiting forever is not an option and eventually this will timeout to allow the 
     * request to go through. If that is the case, an exception could be thrown by RP 
     * for a request to add replication sets to a new CG without journals.
     * 
     * @param cgId ID of the RP CG being used 
     * @param cgParams The current request params
     */
    private void waitForCGToBeCreated(URI cgId, CGRequestParams cgParams) {
        BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgId);
        // If the CG has yet to be created and the CG request params does NOT contain any 
        // journals, we need to wait. We can not create an RP CG without journals.
        // 
        // The assumption is that there are multiple create requests coming down for that CG 
        // and at least one of those requests has the journals defined.
        if (!cg.created() && CollectionUtils.isEmpty(cgParams.getCopies())) {            
            // Get the names of the RSets to be added - used for
            // meaningful log messages.
            StringBuffer rsetNames = new StringBuffer();
            Iterator<CreateRSetParams> rsetIter = cgParams.getRsets().iterator();
            while (rsetIter.hasNext()) {
                CreateRSetParams rsetParam = rsetIter.next();
                rsetNames.append(rsetParam.getName());
                rsetNames.append(", ");
            }
            rsetNames.delete(rsetNames.length() - 2, rsetNames.length());
            
            // Now, let the waiting begin...
            int waitingOnCGCreate = 0;
            while (!cg.created() && (waitingOnCGCreate < MAX_ATTEMPTS_TO_WAIT_FOR_CG_CREATE)) {                      
                _log.info(String.format("RP CG [%s] has not been created yet. Wait to add replication set(s) [%s], "
                        + "sleeping for %s seconds.", 
                        cgParams.getCgName(), rsetNames.toString(), SECONDS_TO_WAIT_FOR_CG_CREATE));                
                try {
                    Thread.sleep(SECONDS_TO_WAIT_FOR_CG_CREATE * 1000);
                } catch (InterruptedException e) {
                    _log.error(e.getMessage());
                }
                
                waitingOnCGCreate++;
                
                // Reload the CG to see if it has been updated
                cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgId);
            }
            
            if (waitingOnCGCreate >= MAX_ATTEMPTS_TO_WAIT_FOR_CG_CREATE) {
                _log.warn(String.format("Maximum wait has been reached while waiting for RP CG [%s] to be created. "
                        + "Releasing request to add replication set(s) [%s]. The request may potentially fail.", 
                        cgParams.getCgName(), rsetNames.toString()));
            } else {
                _log.info(String.format("RP CG [%s] created. Releasing request to add replication set(s) [%s].", 
                        cgParams.getCgName(), rsetNames.toString()));
            }
        }
    }
    
    /**
     * Convenience method to check if the RP CG exists on RP and in ViPR
     * 
     * @param cg The ViPR CG
     * @param rp The RP Client reference
     * @param cgName CG name from the CG params
     * @param rpSystemId Protection system Id
     * @return true if RP CG exists on RP and in ViPR, false otherwise 
     */
    private boolean rpCGExists(BlockConsistencyGroup cg, RecoverPointClient rp, String cgName, URI rpSystemId) {
        return (cg.created() && cg.nameExistsForStorageSystem(rpSystemId, cgName) && rp.doesCgExist(cgName));
    }
    
    @Override
    public void changePortGroup(URI storageSystem, URI exportGroup, URI portGroupURI, List<URI> exportMaskURIs, boolean waitForApproval,
            String token) {
     // supported only for VMAX.
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
}
