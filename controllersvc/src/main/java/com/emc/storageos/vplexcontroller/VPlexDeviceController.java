/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnDataObjectToID;
import static com.emc.storageos.vplexcontroller.VPlexControllerUtils.getDataObject;
import static com.google.common.collect.Collections2.transform;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationDeviceController;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor.Type;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
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
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.StorageProtocol.Block;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.networkcontroller.impl.NetworkZoningParam;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPDeviceController;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.recoverpoint.requests.RecreateReplicationSetRequestParams;
import com.emc.storageos.recoverpoint.utils.WwnUtils;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.audit.AuditLogManagerFactory;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.InvokeTestFailure;
import com.emc.storageos.util.VPlexSrdfUtil;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.ApplicationAddVolumeList;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.block.AbstractBasicMaskingOrchestrator;
import com.emc.storageos.volumecontroller.impl.block.AbstractDefaultMaskingOrchestrator;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPlacementDescriptor;
import com.emc.storageos.volumecontroller.impl.block.ExportWorkflowEntryPoints;
import com.emc.storageos.volumecontroller.impl.block.MaskingWorkflowEntryPoints;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotResyncCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotSessionRelinkTargetsWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotSessionRestoreWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneResyncCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportAddInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportAddVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddPathsCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemovePathsCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportRemoveInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportRemoveVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.TaskLockingCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeDetachCloneCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VplexMirrorDeactivateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VplexMirrorTaskCompleter;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils;
import com.emc.storageos.volumecontroller.impl.utils.CustomVolumeNamingUtils;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext;
import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext.ExportOperationContextOperation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExportMaskValidationContext;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.volumecontroller.placement.ExportPathUpdater;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplex.api.VPlexClusterInfo;
import com.emc.storageos.vplex.api.VPlexDeviceInfo;
import com.emc.storageos.vplex.api.VPlexDistributedDeviceInfo;
import com.emc.storageos.vplex.api.VPlexInitiatorInfo.Initiator_Type;
import com.emc.storageos.vplex.api.VPlexMigrationInfo;
import com.emc.storageos.vplex.api.VPlexStorageViewInfo;
import com.emc.storageos.vplex.api.VPlexVirtualVolumeInfo;
import com.emc.storageos.vplex.api.VPlexVirtualVolumeInfo.WaitOnRebuildResult;
import com.emc.storageos.vplex.api.clientdata.PortInfo;
import com.emc.storageos.vplex.api.clientdata.VolumeInfo;
import com.emc.storageos.vplexcontroller.completers.CacheStatusTaskCompleter;
import com.emc.storageos.vplexcontroller.completers.MigrationOperationTaskCompleter;
import com.emc.storageos.vplexcontroller.completers.MigrationTaskCompleter;
import com.emc.storageos.vplexcontroller.completers.MigrationWorkflowCompleter;
import com.emc.storageos.vplexcontroller.completers.VolumeGroupUpdateTaskCompleter;
import com.emc.storageos.vplexcontroller.job.VPlexCacheStatusJob;
import com.emc.storageos.vplexcontroller.job.VPlexMigrationJob;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.emc.storageos.workflow.WorkflowTaskCompleter;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;

/**
 * This controller is responsible for the overall execution of VPlex operations
 * and the coordination with the SAN Layers and Block Storage for the underlying
 * arrays. The complex operations use Workflows to coordinate the steps in a
 * VPlex operation that needs to be orchestrated. When operating on a VPlex
 * Metro with two clusters, operations on the underlying arrays for cluster-1
 * and cluster-2 are done in parallel if possible.
 *
 * NOTE RP+VPLEX/MetroPoint SnapShots (aka Bookmarks):
 *
 * 1. RP Snapshots (aka Bookmarks) are stored in the ExportGroup as URIs for BlockSnaphots
 * instead of Volumes. This is because RP uses the Target volume to create the snap, not the
 * source. But in the ExportMask it references the actual Target Volume URI. This causes
 * issues and we need to be aware of it this. Using Volume.fetchExportMaskBlockObject() will
 * do the translation from BlockSnap to the correct Target Volume.
 *
 * 2. The RP Initiators do not have a valid Host URI. They do however have a Host Name.
 * This is accounted for in VPlexUtil.getExportMaskHost() and VPlexUtil.getInitiatorHost().
 *
 * @author Watson
 *
 */
public class VPlexDeviceController extends AbstractBasicMaskingOrchestrator
        implements VPlexController, BlockOrchestrationInterface {

    // Workflow names
    private static final String MIGRATE_VOLUMES_WF_NAME = "migrateVolumes";
    private static final String DELETE_MIGRATION_SOURCES_WF_NAME = "deleteMigrationSources";
    private static final String IMPORT_VOLUMES_WF_NAME = "importVolumes";
    private static final String EXPAND_VOLUME_WF_NAME = "expandVolume";
    private static final String DELETE_CG_WF_NAME = "deleteConsistencyGroup";
    private static final String UPDATE_CG_WF_NAME = "updateConsistencyGroup";
    private static final String COPY_VOLUMES_WF_NAME = "copyVolumes";
    private static final String RESTORE_VOLUME_WF_NAME = "restoreVolume";
    private static final String ATTACH_MIRRORS_WF_NAME = "attachMirrors";
    private static final String DEACTIVATE_MIRROR_WF_NAME = "deactivateMirror";
    private static final String DETACH_MIRROR_WF_NAME = "detachMirror";
    private static final String RESYNC_FULL_COPY_WF_NAME = "resyncFullCopy";
    private static final String DETACH_FULL_COPY_WF_NAME = "detachFullCopy";
    private static final String EXPORT_GROUP_REMOVE_VOLUMES = "exportGroupRemoveVolumes";
    private static final String VOLUME_FULLCOPY_GROUP_RELATION_WF = "volumeFullCopyGroupRelation";
    private static final String RESYNC_SNAPSHOT_WF_NAME = "ResyncSnapshot";
    private static final String PAUSE_MIGRATION_WF_NAME = "PauseMigration";
    private static final String RESUME_MIGRATION_WF_NAME = "ResumeMigration";
    private static final String CANCEL_MIGRATION_WF_NAME = "CancelMigration";
    private static final String DELETE_MIGRATION_WF_NAME = "DeleteMigration";
    private static final String RESTORE_SNAP_SESSION_WF_NAME = "restoreSnapSession";
    private static final String UPDATE_VOLUMEGROUP_WF_NAME = "UpdateVolumeGroup";
    private static final String RELINK_SNAPSHOT_SESSION_TARGETS_WF_NAME = "relinkSnapSessionTargets";

    // Workflow step identifiers
    private static final String EXPORT_STEP = AbstractDefaultMaskingOrchestrator.EXPORT_GROUP_MASKING_TASK;
    private static final String UNEXPORT_STEP = AbstractDefaultMaskingOrchestrator.EXPORT_GROUP_MASKING_TASK;
    private static final String VPLEX_STEP = "vplexVirtual";
    private static final String MIGRATION_CREATE_STEP = "migrate";
    private static final String MIGRATION_COMMIT_STEP = "commit";
    private static final String DELETE_MIGRATION_SOURCES_STEP = "deleteSources";
    private static final String MIGRATION_VOLUME_DELETE_STEP = "delete";
    private static final String VIRTUAL_VOLUME_EXPAND_STEP = "expandVirtualVolume";
    private static final String EXPANSION_MIGRATION_STEP = "expansionMigrate";
    private static final String IMPORT_COPY_STEP = "importCopy";
    private static final String VOLUME_FORGET_STEP = "forgetVolumes";
    private static final String ZONING_STEP = "zoning";
    private static final String ZONING_FC_REF_STEP = "zoningFCRefStep";
    private static final String INITIATOR_REGISTRATION = "initiatorRegistration";
    private static final String RESTORE_VOLUME_STEP = "restoreVolume";
    private static final String RESTORE_VPLEX_VOLUME_STEP = "restoreVPlexVolume";
    private static final String INVALIDATE_CACHE_STEP = "invalidateCache";
    private static final String DETACH_MIRROR_STEP = "detachMirror";
    private static final String ATTACH_MIRROR_STEP = "attachMirror";
    private static final String PROMOTE_MIRROR_STEP = "promoteMirror";
    private static final String MARK_VIRTUAL_VOLUMES_INACTIVE = "markVolumesInactive";
    private static final String WAIT_ON_REBUILD_STEP = "waitOnRebuild";
    private static final String RESYNC_FULL_COPY_STEP = "resyncFullCopy";
    private static final String DETACH_FULL_COPY_STEP = "detachFullCopy";
    private static final String VOLUME_FULLCOPY_GROUP_RELATION_STEP = "volumeFullcopyRelationStep";
    private static final String RESYNC_SNAPSHOT_STEP = "ResyncSnapshotStep";
    private static final String RELINK_SNAPSHOT_SESSION_TARGET_STEP = "RelinkSnapshotSessionTarget";
    private static final String PAUSE_MIGRATION_STEP = "PauseMigrationStep";
    private static final String RESUME_MIGRATION_STEP = "ResumeMigrationStep";
    private static final String CANCEL_MIGRATION_STEP = "CancelMigrationStep";
    private static final String DELETE_MIGRATION_STEP = "DeleteMigrationStep";
    private static final String RESTORE_SNAP_SESSION_STEP = "restoreSnapshotSessionStep";
    private static final String RESTORE_FROM_FULLCOPY_STEP = "restoreFromFullCopy";
    private static final String VALIDATE_VPLEX_VOLUME_STEP = "validateVPlexVolumeStep";

    // Workflow controller method names.
    private static final String DELETE_VOLUMES_METHOD_NAME = "deleteVolumes";
    private static final String CREATE_VIRTUAL_VOLUMES_METHOD_NAME = "createVirtualVolumes";
    private static final String RB_CREATE_VIRTUAL_VOLUMES_METHOD_NAME = "rollbackCreateVirtualVolumes";
    private static final String RB_UPGRADE_VIRTUAL_VOLUME_LOCAL_TO_DISTRIBUUTED_METHOD_NAME = "rollbackUpgradeVirtualVolumeLocalToDistributed";
    private static final String CREATE_MIRRORS_METHOD_NAME = "createMirrors";
    private static final String RB_CREATE_MIRRORS_METHOD_NAME = "rollbackCreateMirrors";
    private static final String DELETE_MIRROR_DEVICE_METHOD_NAME = "deleteMirrorDevice";
    private static final String RB_DELETE_MIRROR_DEVICE_METHOD_NAME = "rollbackDeleteMirrorDevice";
    private static final String DETACH_MIRROR_DEVICE_METHOD_NAME = "detachMirrorDevice";
    private static final String PROMOTE_MIRROR_METHOD_NAME = "promoteMirror";
    private static final String RB_PROMOTE_MIRROR_METHOD_NAME = "rollbackPromoteMirror";
    private static final String MIGRATE_VIRTUAL_VOLUME_METHOD_NAME = "migrateVirtualVolume";
    private static final String RB_MIGRATE_VIRTUAL_VOLUME_METHOD_NAME = "rollbackMigrateVirtualVolume";
    private static final String COMMIT_MIGRATION_METHOD_NAME = "commitMigration";
    private static final String RB_COMMIT_MIGRATION_METHOD_NAME = "rollbackCommitMigration";
    private static final String ROLLBACK_METHOD_NULL = "rollbackMethodNull";
    private static final String CREATE_VIRTUAL_VOLUME_FROM_IMPORT_METHOD_NAME = "createVirtualVolumeFromImportStep";
    private static final String DELETE_VIRTUAL_VOLUMES_METHOD_NAME = "deleteVirtualVolumes";
    private static final String DELETE_MIGRATION_SOURCES_METHOD = "deleteMigrationSources";
    private static final String EXPAND_VOLUME_NATIVELY_METHOD_NAME = "expandVolumeNatively";
    private static final String EXPAND_VIRTUAL_VOLUME_METHOD_NAME = "expandVirtualVolume";
    private static final String EXPANSION_MIGRATION_METHOD_NAME = "migrateVolumeForExpansion";
    private static final String IMPORT_COPY_METHOD_NAME = "importCopy";
    private static final String FORGET_VOLUMES_METHOD_NAME = "forgetVolumes";
    private static final String RB_FORGET_VOLUMES_METHOD_NAME = "rollbackForgetVolumes";
    private static final String CREATE_STORAGE_VIEW = "createStorageView";
    private static final String DELETE_STORAGE_VIEW = "deleteStorageView";
    private static final String REGISTER_INITIATORS_METHOD_NAME = "registerInitiators";
    private static final String RESTORE_VOLUME_METHOD_NAME = "restoreVolume";
    private static final String INVALIDATE_CACHE_METHOD_NAME = "invalidateCache";
    private static final String DETACH_MIRROR_METHOD_NAME = "detachMirror";
    private static final String ATTACH_MIRROR_METHOD_NAME = "attachMirror";
    private static final String RESTORE_RESYNC_RB_METHOD_NAME = "rollbackRestoreResync";
    private static final String WAIT_ON_REBUILD_METHOD_NAME = "waitOnRebuild";
    private static final String RESTORE_FROM_FC_METHOD_NAME = "restoreFromFullCopy";
    private static final String RESYNC_FC_METHOD_NAME = "resyncFullCopy";
    private static final String DETACH_FC_METHOD_NAME = "detachFullCopy";
    private static final String VOLUME_FULLCOPY_RELATION_METHOD = "establishVolumeFullCopyGroupRelation";
    private static final String RESYNC_SNAPSHOT_METHOD_NAME = "resyncSnapshot";
    private static final String RELINK_SNAPSHOT_SESSION_TARGETS_METHOD_NAME = "relinkTargetsToSnapshotSession";
    private static final String PAUSE_MIGRATION_METHOD_NAME = "pauseMigrationStep";
    private static final String RESUME_MIGRATION_METHOD_NAME = "resumeMigrationStep";
    private static final String CANCEL_MIGRATION_METHOD_NAME = "cancelMigrationStep";
    private static final String DELETE_MIGRATION_METHOD_NAME = "deleteMigrationStep";
    private static final String RESTORE_SNAP_SESSION_METHOD_NAME = "restoreSnapshotSession";
    private static final String RESTORE_FROM_FULLCOPY_METHOD_NAME = "restoreFromFullCopy";
    private static final String CREATE_FULL_COPY_METHOD_NAME = "createFullCopy";
    private static final String VALIDATE_VPLEX_VOLUME_METHOD = "validateVPlexVolume";
    private static final String STORAGE_VIEW_ADD_VOLUMES_METHOD = "storageViewAddVolumes";
    private static final String STORAGE_VIEW_ADD_INITS_ROLLBACK_METHOD = "storageViewAddInitiatorsRollback";
    private static final String STORAGE_VIEW_ADD_VOLS_ROLLBACK_METHOD = "storageViewAddVolumesRollback";
    private static final String STORAGE_VIEW_ADD_STORAGE_PORTS_ROLLBACK_METHOD = "storageViewAddStoragePortsRollback";
    private static final String STORAGE_VIEW_ADD_STORAGE_PORTS_METHOD = "storageViewAddStoragePorts";
    private static final String STORAGE_VIEW_ADD_INITS_METHOD = "storageViewAddInitiators";

    // Constants used for creating a migration name.
    private static final String MIGRATION_NAME_PREFIX = "M";
    private static final String MIGRATION_NAME_DATE_FORMAT = "ddHHmmssSSS";

    // Miscellaneous Constants
    private static final String HYPHEN_OPERATOR = "-";
    private static final String COMMIT_MIGRATION_SUSPEND_MESSAGE = "The user has the opportunity to perform any manual validation before the migration is committed. "
            + "If you resume the task, the migration will be committed. "
            + "If you rollback the task, the VPLEX migration will be rolled back and the migration target will be deleted.";
    private static final String DELETE_MIGRATION_SOURCES_SUSPEND_MESSAGE = "If you rollback the deletion of the migration source, it will be inventory deleted from ViPR, "
            + "and you should rename the source volume on the array as ViPR uses a temporary name that may "
            + "conflict with future migrations.";
    private static final int FOUND_NO_VIPR_EXPORT_MASKS = 0;
    private static final int FOUND_ONE_VIPR_EXPORT_MASK = 1;
    
    private static final Long MINUTE_TO_MILLISECONDS = 60000L;
    private static final String CONTROLLER_VPLEX_MIGRATION_TIMEOUT_MINUTES = "controller_vplex_migration_timeout_minutes";
    private static final String CONTROLLER_VPLEX_BACKEND_HDS_DISCOVERY_RETRY = "controller_vplex_backend_hds_discovery_retry";

    // migration speed to transfer size map
    private static final Map<String, String> migrationSpeedToTransferSizeMap;

    static {
        migrationSpeedToTransferSizeMap = new HashMap<String, String>();
        migrationSpeedToTransferSizeMap.put("Lowest", "128KB");
        migrationSpeedToTransferSizeMap.put("Low", "2MB");
        migrationSpeedToTransferSizeMap.put("Medium", "8MB");
        migrationSpeedToTransferSizeMap.put("High", "16MB");
        migrationSpeedToTransferSizeMap.put("Highest", "32MB");
    }

    // Volume restore step data keys
    private static final String REATTACH_MIRROR = "reattachMirror";
    private static final String ADD_BACK_TO_CG = "addToCG";
    private static final String DETACHED_DEVICE = "detachedDevice";
    private static final String RESTORE_DEVICE_NAME = "restoreDeviceName";

    private static CoordinatorClient coordinator;

    private static final Logger _log = LoggerFactory.getLogger(VPlexDeviceController.class);
    private VPlexApiFactory _vplexApiFactory;
    private VPlexApiLockManager _vplexApiLockManager;
    private BlockDeviceController _blockDeviceController;
    private RPDeviceController _rpDeviceController;
    private BlockOrchestrationDeviceController _blockOrchestrationController;
    private ValidatorFactory validator;
    private static volatile VPlexDeviceController _instance;
    private static final URI nullURI = NullColumnValueGetter.getNullURI();
    @Autowired
    private DataSourceFactory dataSourceFactory;
    @Autowired
    private CustomConfigHandler customConfigHandler;
    @Autowired
    protected ValidatorConfig validatorConfig;
    // Consistency group manager implementations. These managers create workflow steps
    // and perform workflow logic specifically for consistency groups.
    private Map<String, ConsistencyGroupManager> consistencyGroupManagers;

    public VPlexDeviceController() {
        _instance = this;
    }

    public static VPlexDeviceController getInstance() {
        return _instance;
    }

    public void setVplexApiLockManager(VPlexApiLockManager lockManager) {
        _vplexApiLockManager = lockManager;
    }

    public void setConsistencyGroupManagers(Map<String, ConsistencyGroupManager> serviceInterfaces) {
        consistencyGroupManagers = serviceInterfaces;
    }

    /**
     * Gets the ConsistencyGroupManager implementation based on implementation type.
     *
     * @param type
     *            The implementation type, includes rp, vplex, etc.
     * @return
     */
    private ConsistencyGroupManager getConsistencyGroupManager(String type) {
        return consistencyGroupManagers.get(type);
    }

    /**
     * Gets the ConsistencyGroupManager implementation based on the Volume being
     * RP or not.
     *
     * @param volume
     * @return
     */
    private ConsistencyGroupManager getConsistencyGroupManager(Volume volume) {
        if (volume != null && volume.checkForRp()) {
            return consistencyGroupManagers.get(DiscoveredDataObject.Type.rp.name());
        }
        return consistencyGroupManagers.get(DiscoveredDataObject.Type.vplex.name());
    }

    /**
     * Gets the ConsistencyGroupManager implementation based on the BlockConsistencyGroup
     * types.
     *
     * @param cg
     *            The BlockConsistencyGroup object.
     * @return
     */
    private ConsistencyGroupManager getConsistencyGroupManager(BlockConsistencyGroup cg) {
        ConsistencyGroupManager consistencyGroupManager = null;

        if (cg.checkForType(Types.RP) && cg.checkForType(Types.VPLEX)) {
            consistencyGroupManager = getConsistencyGroupManager(DiscoveredDataObject.Type.rp.name());
        } else if (!cg.checkForType(Types.RP) && cg.checkForType(Types.VPLEX)) {
            consistencyGroupManager = getConsistencyGroupManager(DiscoveredDataObject.Type.vplex.name());
        }

        return consistencyGroupManager;
    }

    /**
     * VPLEXDeviceController which was not inheriting from AbstractMaskingOrchestrator,
     * is now moved to align with all other Orchestrators. So it has to implement getDevice() method.
     */
    @Override
    public BlockStorageDevice getDevice() {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * A VPlexTaskCompleter.
     *
     *
     */
    @SuppressWarnings("serial")
    static class VPlexTaskCompleter extends TaskLockingCompleter {
        OperationTypeEnum _opType = null;

        public VPlexTaskCompleter(Class clazz, URI id, String opId,
                OperationTypeEnum opType) {
            super(clazz, id, opId);
            _opType = opType;
        }

        public VPlexTaskCompleter(Class clazz, List<URI> uriList, String opId,
                OperationTypeEnum opType) {
            super(clazz, uriList, opId);
            _opType = opType;
        }

        @Override
        protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
            try {
                _log.info("Completing", _opId);

                // When importing volume to VPLEX, the operation is on the
                // the volume being imported. However, when importing a
                // snapshot, execution is on an internal volume created to
                // represent the snapshot, but the operation is on the snapshot
                // itself, so we want to be sure an update the status on the
                // snapshot.
                List<URI> ids = getIds();
                for (URI id : ids) {
                    if (URIUtil.isType(id, Volume.class)) {
                        Volume volume = dbClient.queryObject(Volume.class, id);
                        String nativeGuid = volume.getNativeGuid();
                        if (NullColumnValueGetter.isNotNullValue(nativeGuid)) {
                            List<BlockSnapshot> snapshots = CustomQueryUtility.getActiveBlockSnapshotByNativeGuid(dbClient, nativeGuid);
                            if (!snapshots.isEmpty()) {
                                // There should only be one.
                                switch (status) {
                                    case error:
                                        super.setErrorOnDataObject(dbClient, BlockSnapshot.class, snapshots.get(0), coded);
                                        break;
                                    case ready:
                                        super.setReadyOnDataObject(dbClient, BlockSnapshot.class, snapshots.get(0));
                                        break;
                                    default:
                                        _log.error("Unexpected status {} on completer", status.name());
                                        break;
                                }
                            }
                        }
                    }
                }

                updateWorkflowStatus(status, coded);
            } catch (DatabaseException ex) {
                _log.error("Unable to complete task: " + getOpId());
            }

            super.setStatus(dbClient, status, coded);
            super.complete(dbClient, status, coded);

            // clear VolumeGroup's Partial_Request Flag
            List<Volume> toUpdate = new ArrayList<Volume>();
            for (URI fullCopyURI : getIds()) {
                if (URIUtil.isType(fullCopyURI, Volume.class)) {
                    Volume fullCopy = dbClient.queryObject(Volume.class, fullCopyURI);
                    URI sourceId = fullCopy.getAssociatedSourceVolume();
                    if (!NullColumnValueGetter.isNullURI(sourceId)) {
                        Volume source = dbClient.queryObject(Volume.class, sourceId);
                        if (source != null && source.checkInternalFlags(Flag.VOLUME_GROUP_PARTIAL_REQUEST)) {
                            source.clearInternalFlags(Flag.VOLUME_GROUP_PARTIAL_REQUEST);
                            toUpdate.add(source);
                        }
                    }
                }
            }
            if (!toUpdate.isEmpty()) {
                _log.info("Clearing PARTIAL flag set on Clones for Partial request");
                dbClient.updateObject(toUpdate);
            }

            // Record audit log if opType specified.
            if ((Operation.Status.ready == status) && (_opType != null)) {
                // Record audit log.
                AuditLogManager auditMgr = AuditLogManagerFactory.getAuditLogManager();
                auditMgr.recordAuditLog(null, null, ControllerUtils.BLOCK_EVENT_SERVICE,
                        _opType, System.currentTimeMillis(),
                        AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END,
                        getIds().toString());
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Here we should have already created any underlying volumes. What remains to be done: 1. Export the underlying Storage Volumes from
     * the array to the VPlex. 2. Create the Virtual volume. 3. If a consistency group was specified, then create the consistency group if
     * it does not exist, then add the volumes. If it already exists, just add the volumes.
     */
    @Override
    public String addStepsForCreateVolumes(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId) throws ControllerException {

        try {
            // Get only the VPlex volumes from the descriptors.
            List<VolumeDescriptor> vplexVolumes = VolumeDescriptor.filterByType(volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_VIRT_VOLUME },
                    new VolumeDescriptor.Type[] {});

            // If there are no VPlex volumes, just return
            if (vplexVolumes.isEmpty()) {
                _log.info("No VPLEX create volume steps required.");
                return waitFor;
            }
            _log.info("Adding VPLEX create volume steps...");

            // Segregate the volumes by Device.
            Map<URI, List<VolumeDescriptor>> vplexDescMap = VolumeDescriptor
                    .getDeviceMap(vplexVolumes);

            // For each VPLEX to be provisioned (normally there is only one)
            String lastStep = waitFor;
            for (URI vplexURI : vplexDescMap.keySet()) {
                StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);

                // Build some needed maps to get started.
                Type[] types = new Type[] { Type.BLOCK_DATA, Type.SRDF_SOURCE, Type.SRDF_EXISTING_SOURCE, Type.SRDF_TARGET };
                Map<URI, StorageSystem> arrayMap = buildArrayMap(vplexSystem, volumes, types);
                Map<URI, Volume> volumeMap = buildVolumeMap(vplexSystem, volumes, Type.VPLEX_VIRT_VOLUME);

                // Set the project and tenant to those of an underlying volume.
                // These are used to set the project and tenant of a new ExportGroup if needed.
                Volume firstVolume = volumeMap.values().iterator().next();
                Project vplexProject = VPlexUtil.lookupVplexProject(firstVolume, vplexSystem, _dbClient);
                URI tenantURI = vplexProject.getTenantOrg().getURI();
                _log.info("Project is {}, Tenant is {}", vplexProject.getId(), tenantURI);

                try {
                    // Now we need to do the necessary zoning and export steps to ensure
                    // the VPlex can see these new backend volumes.
                    lastStep = createWorkflowStepsForBlockVolumeExport(workflow, vplexSystem, arrayMap,
                            volumeMap, vplexProject.getId(), tenantURI, lastStep);
                } catch (Exception ex) {
                    _log.error("Could not create volumes for vplex: " + vplexURI, ex);
                    TaskCompleter completer = new VPlexTaskCompleter(Volume.class, vplexURI,
                            taskId, null);
                    ServiceError serviceError = VPlexApiException.errors.jobFailed(ex);
                    completer.error(_dbClient, serviceError);
                    throw ex;
                }

                Map<URI, URI> computeResourceMap = new HashMap<>();
                List<VolumeDescriptor> vplexDescrs = vplexDescMap.get(vplexURI);
                for (VolumeDescriptor descr : vplexDescrs) {
                    URI computeResourceURI = descr.getComputeResource();
                    if (computeResourceURI != null) {
                        computeResourceMap.put(descr.getVolumeURI(), computeResourceURI);
                    }
                }

                // Now create each of the Virtual Volumes that may be necessary.
                List<URI> vplexVolumeURIs = VolumeDescriptor.getVolumeURIs(vplexDescrs);

                // Now make a Step to create the VPlex Virtual volume.
                // This will be done from this controller.
                String stepId = workflow.createStepId();
                lastStep = workflow.createStep(
                        VPLEX_STEP,
                        String.format("VPlex %s creating virtual volumes:%n%s",
                                vplexSystem.getId().toString(),
                                BlockDeviceController.getVolumesMsg(_dbClient, vplexVolumeURIs)),
                        lastStep, vplexURI, vplexSystem.getSystemType(), this.getClass(),
                        createVirtualVolumesMethod(vplexURI, vplexVolumeURIs, computeResourceMap),
                        rollbackCreateVirtualVolumesMethod(vplexURI, vplexVolumeURIs, stepId),
                        stepId);

                // Get one of the vplex volumes so we can determine what ConsistencyGroupManager
                // implementation to use.
                Volume vol = getDataObject(Volume.class, vplexVolumeURIs.get(0), _dbClient);
                ConsistencyGroupManager consistencyGroupManager = getConsistencyGroupManager(vol);

                // Deal with CGs.
                // Filter out any VPlex Volumes that front the SRDF targets for now.
                List<URI> volsForCG = VPlexSrdfUtil.filterOutVplexSrdfTargets(_dbClient, vplexVolumeURIs);
                if (!volsForCG.isEmpty()) {
                    lastStep = consistencyGroupManager.addStepsForCreateConsistencyGroup(workflow, lastStep,
                            vplexSystem, volsForCG, false);
                }

                // If there are VPlex Volumes fronting SRDF targets, handle them.
                // They will go into a separate CG that represents the SRDF targets.
                // That CG will have already been generated?
                volsForCG = VPlexSrdfUtil.returnVplexSrdfTargets(_dbClient, vplexVolumeURIs);
                if (!volsForCG.isEmpty()) {
                    lastStep = consistencyGroupManager.addStepsForAddingVolumesToSRDFTargetCG(
                            workflow, vplexSystem, volsForCG, lastStep);
                }
                _log.info("Added steps for creating consistency group");
            }
            return lastStep;
        } catch (Exception ex) {
            throw VPlexApiException.exceptions.addStepsForCreateVolumesFailed(ex);
        }
    }

    /**
     * Build a map of URI to cached StorageSystem for the underlying arrays.
     *
     * @param vplexSystem
     *            Only return Storage Systems connected this VPlex
     * @param descriptors
     * @param VolmeDescriptor.Type
     *            used to filter descriptors
     * @return Map<arrayURI, StorageSystem>
     */
    private Map<URI, StorageSystem> buildArrayMap(StorageSystem vplexSystem,
            List<VolumeDescriptor> descriptors, VolumeDescriptor.Type[] types) {
        Map<URI, StorageSystem> arrayMap = new HashMap<URI, StorageSystem>();
        // Get only the descriptors for the type if specified..
        if (types != null) {
            descriptors = VolumeDescriptor.filterByType(descriptors,
                    types, new VolumeDescriptor.Type[] {});
        }
        for (VolumeDescriptor desc : descriptors) {
            if (arrayMap.containsKey(desc.getDeviceURI()) == false) {
                if (vplexSystem == null) {
                    StorageSystem array = getDataObject(StorageSystem.class, desc.getDeviceURI(), _dbClient);
                    arrayMap.put(desc.getDeviceURI(), array);
                } else {
                    Set<URI> connectedSystems = ConnectivityUtil
                            .getStorageSystemAssociationsByNetwork(_dbClient,
                                    vplexSystem.getId(), StoragePort.PortType.backend);
                    if (connectedSystems.contains(desc.getDeviceURI())) {
                        StorageSystem array = getDataObject(StorageSystem.class, desc.getDeviceURI(), _dbClient);
                        arrayMap.put(desc.getDeviceURI(), array);
                    }
                }
            }
        }
        return arrayMap;
    }

    /**
     * Build a map of URI to cached Volumes for the underlying Storage Volumes that
     * should be already present (and created).
     *
     * @param vplexSystem
     *            Only return Volume associated with this VPlex
     * @param descriptors
     *            VolumeDescriptors
     * @param VolmeDescriptor.Type
     *            used to filter descriptors
     * @return Map<volumeURI, Volume>
     */
    private Map<URI, Volume> buildVolumeMap(StorageSystem vplexSystem,
            List<VolumeDescriptor> descriptors, VolumeDescriptor.Type type) {
        Map<URI, Volume> volumeMap = new HashMap<URI, Volume>();
        // Get only the descriptors for the type if specified.
        if (type != null) {
            descriptors = VolumeDescriptor.filterByType(descriptors,
                    new VolumeDescriptor.Type[] { type },
                    new VolumeDescriptor.Type[] {});
        }
        // Loop through all the descriptors / filtered descriptors
        for (VolumeDescriptor desc : descriptors) {
            // Check to see if the volumeMap already contains the volume URI.
            if (volumeMap.containsKey(desc.getVolumeURI()) == false) {
                // Load the volume from the descriptor
                Volume volume = getDataObject(Volume.class, desc.getVolumeURI(), _dbClient);

                if (vplexSystem == null) {
                    // If the vplexSystem hasn't been passed in, just add the volume the volumeMap.
                    volumeMap.put(desc.getVolumeURI(), volume);
                } else {
                    // The vplexSystem is present, so we want to only pass back the backing volumes
                    // associated to this VPlex.
                    // Check the storage controller of this Virtual Volume
                    if (desc.getType().equals(VolumeDescriptor.Type.VPLEX_VIRT_VOLUME) &&
                            volume.getStorageController().toString()
                                    .equals(vplexSystem.getId().toString())) {
                        StringSet backingVolumes = volume.getAssociatedVolumes();
                        if (null == backingVolumes || backingVolumes.isEmpty()) {
                            _log.warn("VPLEX volume {} has no backend volumes.", volume.forDisplay());
                        } else {
                            // Add all backing volumes found to the volumeMap
                            for (String backingVolumeId : backingVolumes) {
                                URI backingVolumeURI = URI.create(backingVolumeId);
                                Volume backingVolume = getDataObject(Volume.class, backingVolumeURI, _dbClient);
                                volumeMap.put(backingVolumeURI, backingVolume);
                            }
                        }
                    }
                }
            }
        }
        return volumeMap;
    }

    /**
     * Build a map of URI to cached StorageSystem for the underlying arrays.
     *
     * @param descriptors
     * @param VolmeDescriptor.Type
     *            used to filter descriptors
     * @return Map<arrayURI, StorageSystem>
     */
    private Map<URI, StorageSystem> buildArrayMap(
            List<VolumeDescriptor> descriptors, VolumeDescriptor.Type type) {
        Map<URI, StorageSystem> arrayMap = new HashMap<URI, StorageSystem>();
        // Get only the descriptors for the type if specified..
        if (type != null) {
            descriptors = VolumeDescriptor.filterByType(descriptors,
                    new VolumeDescriptor.Type[] { type },
                    new VolumeDescriptor.Type[] {});
        }
        for (VolumeDescriptor desc : descriptors) {
            if (arrayMap.containsKey(desc.getDeviceURI()) == false) {
                StorageSystem array = getDataObject(StorageSystem.class, desc.getDeviceURI(), _dbClient);
                arrayMap.put(desc.getDeviceURI(), array);
            }
        }
        return arrayMap;
    }

    /**
     * Build a map of URI to cached Volumes for the underlying Storage Volumes that
     * should be already present (and created).
     *
     * @param descriptors
     *            VolumeDescriptors
     * @param VolmeDescriptor.Type
     *            used to filter descriptors
     * @return Map<volumeURI, Volume>
     */
    private Map<URI, Volume> buildVolumeMap(List<VolumeDescriptor> descriptors,
            VolumeDescriptor.Type type) {
        Map<URI, Volume> volumeMap = new HashMap<URI, Volume>();
        // Get only the descriptors for the type if specified.
        if (type != null) {
            descriptors = VolumeDescriptor.filterByType(descriptors,
                    new VolumeDescriptor.Type[] { type },
                    new VolumeDescriptor.Type[] {});
        }
        for (VolumeDescriptor desc : descriptors) {
            if (volumeMap.containsKey(desc.getVolumeURI()) == false) {
                Volume volume = getDataObject(Volume.class, desc.getVolumeURI(), _dbClient);
                volumeMap.put(desc.getVolumeURI(), volume);
            }
        }
        return volumeMap;
    }

    /**
     * Returns a Workflow.Method for creating Virtual Volumes.
     *
     * @param vplexURI
     *            The URI of the VPLEX
     * @param vplexVolumeURIs
     *            The URIs of the volumes
     * @param computeResourceMap
     *            A Map of the compute resource for each volume.
     *
     * @return The create virtual volumes method.
     */
    private Workflow.Method createVirtualVolumesMethod(URI vplexURI, List<URI> vplexVolumeURIs, Map<URI, URI> computeResourceMap) {
        return new Workflow.Method(CREATE_VIRTUAL_VOLUMES_METHOD_NAME, vplexURI, vplexVolumeURIs, computeResourceMap);
    }

    /**
     * Do the creation of a VPlex Virtual Volume. This is called as a Workflow Step.
     * NOTE: The parameters here must match createVirtualVolumesMethod above (except stepId).
     *
     * @param vplexURI
     *            -- URI of the VPlex StorageSystem
     * @param vplexVolumeURIs
     *            -- URI of the VPlex volumes to be created. They must contain
     *            associatedVolumes (URI of the underlying Storage Volumes).
     * @param computeResourceMap
     *            A Map of the compute resource for each volume.
     * @param stepId
     *            - The stepId used for completion.
     * @throws WorkflowException
     */
    public void createVirtualVolumes(URI vplexURI, List<URI> vplexVolumeURIs, Map<URI, URI> computeResourceMap, String stepId)
            throws WorkflowException {
        List<List<VolumeInfo>> rollbackData = new ArrayList<List<VolumeInfo>>();

        // Below variables will act as workflow.storeStepData Keys
        final String volumeInfoStepKey = "VolumeInfo";
        final String vplexVolumeInfoStepKey = "VPlexVirtualVolumeInfo";

        List<URI> createdVplexVolumeURIs = new ArrayList<URI>();
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            // Get the API client.
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

            // Make a map of StorageSystem ids to Storage System
            Map<URI, StorageSystem> storageMap = new HashMap<URI, StorageSystem>();
            // Make a map of Virtual Volumes to Storage Volumes.
            Map<Volume, List<Volume>> volumeMap = new HashMap<Volume, List<Volume>>();
            // Make a string buffer for volume labels
            StringBuffer volumeLabels = new StringBuffer();
            // List of storage system Guids
            List<String> storageSystemGuids = new ArrayList<String>();

            Boolean isDistributedVolume = false;
            Boolean isAnyHDSVolume = false;
            Map<String, Set<URI>> clusterVarrayMap = new HashMap<>();
            for (URI vplexVolumeURI : vplexVolumeURIs) {
                Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
                URI vplexVolumeVarrayURI = vplexVolume.getVirtualArray();
                String clusterId = ConnectivityUtil.getVplexClusterForVarray(
                        vplexVolumeVarrayURI, vplexVolume.getStorageController(), _dbClient);
                if (clusterVarrayMap.containsKey(clusterId)) {
                    clusterVarrayMap.get(clusterId).add(vplexVolumeVarrayURI);
                } else {
                    Set<URI> varraysForCluster = new HashSet<>();
                    varraysForCluster.add(vplexVolumeVarrayURI);
                    clusterVarrayMap.put(clusterId, varraysForCluster);
                }
                volumeLabels.append(vplexVolume.getLabel()).append(" ");
                volumeMap.put(vplexVolume, new ArrayList<Volume>());

                // Find the underlying Storage Volumes
                StringSet associatedVolumes = vplexVolume.getAssociatedVolumes();
                if (associatedVolumes.size() > 1) {
                    isDistributedVolume = true;
                }
                for (String associatedVolume : associatedVolumes) {
                    Volume storageVolume = getDataObject(Volume.class, new URI(associatedVolume), _dbClient);
                    URI storageSystemId = storageVolume.getStorageController();
                    if (storageMap.containsKey(storageSystemId) == false) {
                        StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageSystemId);
                        storageMap.put(storageSystemId, storage);
                        if (!storageSystemGuids.contains(storage.getNativeGuid())) {
                            storageSystemGuids.add(storage.getNativeGuid());
                            //check any storage is HDS as it may need extra discovery
                            if (storage.deviceIsType(DiscoveredDataObject.Type.hds)) {
                                isAnyHDSVolume = true;
                            }
                        }
                    }
                    volumeMap.get(vplexVolume).add(storageVolume);
                }
            }
            _log.info(String.format("Request to create: %s virtual volume(s) %s", volumeMap.size(), volumeLabels));
            long startTime = System.currentTimeMillis();

            // If a new backend system is connected to a VPLEX and the VPLEX does not
            // yet know about the system i.e., the system does not show up in the path 
            // /clusters/cluster-x/storage-elements/storage-arrays, and a user attempts
            // to create a virtual volume, the request may fail because we cannot find 
            // the storage system. When the backend volume on the new system is created 
            // and exported to the VPLEX, the VPLEX will recognize new system. However, 
            // this may not occur immediately. So, when we go to create the vplex volume 
            // using that backend volume, we may not find that system and volume on the 
            // first try. We saw this in development. As such there was a retry loop 
            // added when finding the backend volumes in the discovery that is performed
            // in the method to create the virtual volume.
            //
            // However changes for CTRL-12826 were merged on 7/31/2015 that circumvented 
            // that retry code. Changes were made to do the array re-discover here prior
            // to virtual volume creation, rather than during virtual volume creation and 
            // false was passed to the create virtual volume routine for the discovery 
            // required flag. The newly added call does not do any kind of retry if the 
            // system is not found and so a failure will occur in the scenario described 
            // above. If a system is not found an exception is thrown. Now we will catch
            // that exception and re-enable discovery in the volume creation routine. 
            // Essentially we revert to what was happening before the 12826 changes if there
            // is an issue discovering the systems on the initial try here.
            boolean discoveryRequired = false;
            // Update for COP-36674, We Observe that for HDS system even after successful discovery the backend storage volume not found by
            // VPLEX Intermittently and it may required rediscovery to find it.Making it configurable with default value set false.
            boolean hdsRediscoveryConfigFlag = Boolean
                    .valueOf(ControllerUtils.getPropertyValueFromCoordinator(coordinator, CONTROLLER_VPLEX_BACKEND_HDS_DISCOVERY_RETRY));
            discoveryRequired = hdsRediscoveryConfigFlag && isAnyHDSVolume;
            _log.debug("discoveryRequired: {} , hdsRediscoveryConfigFlag: {}, isAnyHDSVolume: {}",
                    discoveryRequired, hdsRediscoveryConfigFlag, isAnyHDSVolume);
            try {
                client.rediscoverStorageSystems(storageSystemGuids);
            } catch (Exception e) {
                String warnMsg = String.format("Initial discovery of one or more of these backend systems %s failed: %s."
                        + "Discovery is required during virtual volume creation", storageSystemGuids, e.getMessage()); 
                _log.warn(warnMsg);
                discoveryRequired = true;
            }

            // Now make a call to the VPlexAPIClient.createVirtualVolume for each vplex volume.
            StringBuilder buf = new StringBuilder();
            buf.append("Vplex: " + vplexURI + " created virtual volume(s): ");

            boolean thinEnabled = false;
            boolean searchAllClustersForStorageVolumes = ((clusterVarrayMap.keySet().size() > 1 || isDistributedVolume) ? true : false);
            List<VPlexVirtualVolumeInfo> virtualVolumeInfos = new ArrayList<VPlexVirtualVolumeInfo>();
            Map<String, Volume> vplexVolumeNameMap = new HashMap<String, Volume>();
            List<VPlexClusterInfo> clusterInfoList = null;
            for (Volume vplexVolume : volumeMap.keySet()) {
                URI vplexVolumeId = vplexVolume.getId();
                _log.info(String.format("Creating virtual volume: %s (%s)", vplexVolume.getLabel(), vplexVolumeId));
                URI vplexVolumeVarrayURI = vplexVolume.getVirtualArray();
                String clusterId = null;
                for (Entry<String, Set<URI>> clusterEntry : clusterVarrayMap.entrySet()) {
                    if (clusterEntry.getValue().contains(vplexVolumeVarrayURI)) {
                        clusterId = clusterEntry.getKey();
                    }
                }
                List<VolumeInfo> vinfos = new ArrayList<VolumeInfo>();
                for (Volume storageVolume : volumeMap.get(vplexVolume)) {
                    StorageSystem storage = storageMap.get(storageVolume.getStorageController());
                    List<String> itls = VPlexControllerUtils.getVolumeITLs(storageVolume);
                    VolumeInfo info = new VolumeInfo(storage.getNativeGuid(), storage.getSystemType(),
                            storageVolume.getWWN().toUpperCase().replaceAll(":", ""),
                            storageVolume.getNativeId(), storageVolume.getThinlyProvisioned().booleanValue(), itls);

                    if (storageVolume.getVirtualArray().equals(vplexVolumeVarrayURI)) {
                        // We always want the source backend volume identified first. It
                        // may not be first in the map as the map is derived from the
                        // VPLEX volume's associated volumes list which is an unordered
                        // StringSet.
                        vinfos.add(0, info);
                    } else {
                        vinfos.add(info);
                    }

                    if (info.getIsThinProvisioned()) {
                        // if either or both legs of distributed is thin, try for thin-enabled
                        // (or if local and the single backend volume is thin, try as well)
                        thinEnabled = true;
                    }
                }
                // Update rollback information.
                rollbackData.add(vinfos);
                _workflowService.storeStepData(stepId, volumeInfoStepKey, rollbackData);

                InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_045);

                // Make a call to get cluster info
                if (null == clusterInfoList) {
                    if (searchAllClustersForStorageVolumes) {
                        clusterInfoList = client.getClusterInfoDetails();
                    } else {
                        clusterInfoList = new ArrayList<VPlexClusterInfo>();
                    }
                }

                // Make the call to create a virtual volume. It is distributed if there are two (or more?)
                // physical volumes.
                boolean isDistributed = (vinfos.size() >= 2);
                thinEnabled = thinEnabled && verifyVplexSupportsThinProvisioning(vplex);
                VPlexVirtualVolumeInfo vvInfo = client.createVirtualVolume(vinfos, isDistributed,
                        discoveryRequired, false, clusterId, clusterInfoList, false, thinEnabled, searchAllClustersForStorageVolumes);

                // Note: according to client.createVirtualVolume, this will never be the case.
                if (vvInfo == null) {
                    VPlexApiException ex = VPlexApiException.exceptions.cantFindRequestedVolume(vplexVolume.getLabel());
                    throw ex;
                }

                vplexVolumeNameMap.put(vvInfo.getName(), vplexVolume);
                virtualVolumeInfos.add(vvInfo);
            }

            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_046);

            Map<String, VPlexVirtualVolumeInfo> foundVirtualVolumes = client.findVirtualVolumes(clusterInfoList, virtualVolumeInfos);

            if (!foundVirtualVolumes.isEmpty()) {
                List<String> vplexVirtualCustomVolNames = new ArrayList();
                for (Entry<String, Volume> entry : vplexVolumeNameMap.entrySet()) {
                    Volume vplexVolume = entry.getValue();
                    VPlexVirtualVolumeInfo vvInfo = foundVirtualVolumes.get(entry.getKey());
                    try {
                        // Now we try and rename the volume to the customized name. Note that if custom naming
                        // is disabled the custom name will not be generated and will be null.
                        // Create the VPLEX volume name custom configuration datasource and generate the
                        // custom volume name based on whether the volume is a local or distributed volume.
                        String hostOrClusterName = null;
                        URI computeResourceURI = computeResourceMap.get(vplexVolume.getId());
                        if (computeResourceURI != null) {
                            DataObject hostOrCluster = null;
                            if (URIUtil.isType(computeResourceURI, Cluster.class)) {
                                hostOrCluster = getDataObject(Cluster.class, computeResourceURI, _dbClient);
                            } else if (URIUtil.isType(computeResourceURI, Host.class)) {
                                hostOrCluster = getDataObject(Host.class, computeResourceURI, _dbClient);
                            }
                            if ((hostOrCluster != null) &&
                                    ((vplexVolume.getPersonality() == null) ||
                                            (vplexVolume.checkPersonality(Volume.PersonalityTypes.SOURCE)))) {
                                hostOrClusterName = hostOrCluster.getLabel();
                            }
                        }
                        if (CustomVolumeNamingUtils.isCustomVolumeNamingEnabled(customConfigHandler, vplex.getSystemType())) {
                            String customConfigName = CustomVolumeNamingUtils.getCustomConfigName(hostOrClusterName != null);
                            Project project = getDataObject(Project.class, vplexVolume.getProject().getURI(), _dbClient);
                            TenantOrg tenant = getDataObject(TenantOrg.class, vplexVolume.getTenant().getURI(), _dbClient);
                            DataSource customNameDataSource = CustomVolumeNamingUtils.getCustomConfigDataSource(
                                    project, tenant, vplexVolume.getLabel(), vvInfo.getWwn(), hostOrClusterName, dataSourceFactory,
                                    customConfigName, _dbClient);
                            if (customNameDataSource != null) {
                                String customVolumeName = CustomVolumeNamingUtils.getCustomName(customConfigHandler,
                                        customConfigName, customNameDataSource, vplex.getSystemType());
                                vvInfo = CustomVolumeNamingUtils.renameVolumeOnVPlex(vvInfo, customVolumeName, client);
                                // Update the label to match the custom name.
                                vplexVolume.setLabel(vvInfo.getName());

                                // Also, we update the name portion of the project and tenant URIs
                                // to reflect the custom name. This is necessary because the API
                                // to search for volumes by project, extracts the name portion of the
                                // project URI to get the volume name.
                                NamedURI namedURI = vplexVolume.getProject();
                                namedURI.setName(vvInfo.getName());
                                vplexVolume.setProject(namedURI);
                                namedURI = vplexVolume.getTenant();
                                namedURI.setName(vvInfo.getName());
                                vplexVolume.setTenant(namedURI);

                                vplexVirtualCustomVolNames.add(vvInfo.getName());
                            }
                        }
                    } catch (Exception e) {
                        _log.warn(String.format("Error renaming newly created VPLEX volume %s:%s",
                                vplexVolume.getId(), vplexVolume.getLabel()), e);
                    }
                    buf.append(vvInfo.getName() + " ");
                    _log.info(String.format("Created virtual volume: %s path: %s size: %s", 
                            vvInfo.getName(), vvInfo.getPath(), vvInfo.getCapacityBytes()));
                    vplexVolume.setNativeId(vvInfo.getPath());
                    vplexVolume.setNativeGuid(vvInfo.getPath());
                    vplexVolume.setDeviceLabel(vvInfo.getName());
                    vplexVolume.setThinlyProvisioned(vvInfo.isThinEnabled());
                    checkThinEnabledResult(vvInfo, thinEnabled, _workflowService.getWorkflowFromStepId(stepId).getOrchTaskId());
                    vplexVolume.setWWN(vvInfo.getWwn());
                    // For Vplex virtual volumes set allocated capacity to 0 (cop-18608)
                    vplexVolume.setAllocatedCapacity(0L);
                    vplexVolume.setProvisionedCapacity(vvInfo.getCapacityBytes());
                    _dbClient.updateObject(vplexVolume);

                    // Record VPLEX volume created event.
                    createdVplexVolumeURIs.add(vplexVolume.getId());
                    recordBourneVolumeEvent(vplexVolume.getId(),
                            OperationTypeEnum.CREATE_BLOCK_VOLUME.getEvType(true),
                            Operation.Status.ready,
                            OperationTypeEnum.CREATE_BLOCK_VOLUME.getDescription());
                }

                // Store Vplex volume name to the workflow, to be used in rollback method.
                _workflowService.storeStepData(stepId, vplexVolumeInfoStepKey, vplexVirtualCustomVolNames);
            }
            if (foundVirtualVolumes.size() != vplexVolumeNameMap.size()) {
                VPlexApiException ex = VPlexApiException.exceptions.cantFindAllRequestedVolume();
                throw ex;
            }
            long elapsed = System.currentTimeMillis() - startTime;
            _log.info(String.format("TIMER: %s virtual volume(s) %s create took %f seconds", volumeMap.size(), volumeLabels.toString(),
                    (double) elapsed / (double) 1000));
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (VPlexApiException vae) {
            _log.error("Exception creating Vplex Virtual Volume: " + vae.getMessage(), vae);

            // Record VPLEX volume creation failed event for those volumes
            // not created.
            for (URI vplexVolumeURI : vplexVolumeURIs) {
                if (!createdVplexVolumeURIs.contains(vplexVolumeURI)) {
                    recordBourneVolumeEvent(vplexVolumeURI,
                            OperationTypeEnum.CREATE_BLOCK_VOLUME.getEvType(false),
                            Operation.Status.error,
                            OperationTypeEnum.CREATE_BLOCK_VOLUME.getDescription());
                }
            }
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception creating Vplex Virtual Volume: " + ex.getMessage(), ex);

            // Record VPLEX volume creation failed event for those volumes
            // not created.
            for (URI vplexVolumeURI : vplexVolumeURIs) {
                if (!createdVplexVolumeURIs.contains(vplexVolumeURI)) {
                    recordBourneVolumeEvent(vplexVolumeURI,
                            OperationTypeEnum.CREATE_BLOCK_VOLUME.getEvType(false),
                            Operation.Status.error,
                            OperationTypeEnum.CREATE_BLOCK_VOLUME.getDescription());
                }
            }
            String opName = ResourceOperationTypeEnum.CREATE_VIRTUAL_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.createVirtualVolumesFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }
    
    /**
     * Records a VPLEX volume event.
     *
     * @param volumeId
     *            The id of the VPLEX volume.
     * @param evtType
     *            The event type.
     * @param status
     *            The operation status
     * @param description
     *            The event description
     */
    private void recordBourneVolumeEvent(URI volumeId, String evtType, Operation.Status status,
            String description) {
        try {
            VolumeTaskCompleter.recordBourneVolumeEvent(_dbClient, volumeId, evtType,
                    status, description);
        } catch (Exception e) {
            _log.error("Failed recording VPLEX volume event {} for volume {}",
                    evtType, volumeId);
        }
    }

    /**
     * Records a VPLEX mirror event.
     *
     * @param mirrorUri
     *            The id of the VPLEX mirror.
     * @param evtType
     *            The event type.
     * @param status
     *            The operation status
     * @param description
     *            The event description
     */
    private void recordBourneVplexMirrorEvent(URI mirrorUri, String evtType, Operation.Status status,
            String description) {
        try {
            VplexMirrorTaskCompleter.recordBourneVplexMirrorEvent(_dbClient, mirrorUri, evtType,
                    status, description);
        } catch (Exception e) {
            _log.error("Failed recording VPLEX mirror event {} for mirror {}",
                    evtType, mirrorUri);
        }
    }

    private Workflow.Method rollbackCreateVirtualVolumesMethod(
            URI vplexURI, List<URI> vplexVolumeURIs, String executeStepId) {
        return new Workflow.Method(RB_CREATE_VIRTUAL_VOLUMES_METHOD_NAME, vplexURI, vplexVolumeURIs, executeStepId);
    }

    /**
     * Rollback any virtual volumes previously created.
     *
     * @param vplexURI
     * @param vplexVolumeURIs
     * @param executeStepId
     *            - step Id of the execute step; used to retrieve rollback data.
     * @param stepId
     * @throws WorkflowException
     */
    public void rollbackCreateVirtualVolumes(URI vplexURI, List<URI> vplexVolumeURIs, String executeStepId, String stepId)
            throws WorkflowException {
        try {
            //These are the same key values set during createVirtualVolume step
            final String volumeInfoStepKey = "VolumeInfo";
            final String vplexVolumeInfoStepKey = "VPlexVirtualVolumeInfo";

            // Flag to identify whether Volume deletion is attempted when Custom Naming is enabled
            boolean attemptedDeletion = false;

            //StepData set for VolumeInfo
            List<List<VolumeInfo>> rollbackData = (List<List<VolumeInfo>>) _workflowService.loadStepData(executeStepId,
                    volumeInfoStepKey);

            //StepData set for VPlexVirtualVolumeInfo
            List<String> rollbackVplexCustomVolNames = (List<String>) _workflowService.loadStepData(executeStepId, vplexVolumeInfoStepKey);

            if (rollbackData != null) {
                WorkflowStepCompleter.stepExecuting(stepId);

                // Get the API client.
                StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
                VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

                // Rollback for VPlex virtual volumes when custom naming is enabled.
                if (rollbackVplexCustomVolNames != null && !rollbackVplexCustomVolNames.isEmpty()){
                    for (String rollbackVplexCustomVolName : rollbackVplexCustomVolNames){
                        _log.info("As Custom Naming is enabled, rolling back the Vplex Virtual Volume by name : {}",
                                rollbackVplexCustomVolName);

                        // To deleteVirtualVolume() we want to send:
                        // 1. VplexCustomVolumeName : updated custom virtual-volume name.
                        // 2. true : we want to unclaim storage volumes, claimed by this virtual volume
                        // 3. false : we don't want to retry deleteVirtualVolume operation on failure.
                        client.deleteVirtualVolume(rollbackVplexCustomVolName, true, false);
                    }
                    attemptedDeletion = true;
                }
                // For each virtual volume attempted, try and rollback.
                if (!attemptedDeletion) {
                    for (List<VolumeInfo> rollbackList : rollbackData) {
                        client.deleteVirtualVolume(rollbackList);
                    }
                }
            }

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (VPlexApiException vae) {
            _log.error("Exception rollback VPlex Virtual Volume create: " + vae.getLocalizedMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception rollback VPlex Virtual Volume create: " + ex.getLocalizedMessage(), ex);
            ServiceError serviceError = VPlexApiException.errors.createVirtualVolumesRollbackFailed(stepId, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.volumecontroller.impl.vplex.VplexController#deleteVolumes(java.net.URI, java.util.List,
     * java.lang.String)
     * <p>
     * NOTE: The VolumeDescriptor list will not include the underlying Volumes. These have to be
     * added to the VolumeDescriptor list before returning.
     */
    @Override
    public String addStepsForDeleteVolumes(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId) throws ControllerException {
        try {
            // Filter to get only the VPlex volumes.
            List<VolumeDescriptor> vplexVolumes = VolumeDescriptor.filterByType(volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_VIRT_VOLUME },
                    new VolumeDescriptor.Type[] {});
            // If there are no VPlex volumes, just return
            if (vplexVolumes.isEmpty()) {
                return waitFor;
            }

            // Check to see if there are any volumes flagged to not be fully deleted.
            // This will still remove the volume from its VPLEX CG and also clean up
            // any Mirrors but will leave the Virtual Volume intact on the VPLEX.
            List<VolumeDescriptor> doNotDeleteDescriptors = VolumeDescriptor.getDoNotDeleteDescriptors(vplexVolumes);
            List<URI> doNotFullyDeleteVolumeList = VolumeDescriptor.getVolumeURIs(doNotDeleteDescriptors);

            List<URI> allVplexVolumeURIs = VolumeDescriptor.getVolumeURIs(vplexVolumes);

            // Segregate by device.
            Map<URI, List<VolumeDescriptor>> vplexMap = VolumeDescriptor.getDeviceMap(vplexVolumes);

            // For each VPLEX, delete the virtual volumes.
            // This block of code will create one or two steps for each Vplex, one dealing with
            // consistency groups for SRDF targets (if needed), and the other deleting the
            // Virtual Volumes. The delete virtual volumes step will wait on the CG step if present,
            // otherwise the incoming step from the caller. All the delete virtual volumes steps
            // are in the VPLEX_STEP step group, which subsequent steps will wait on.
            // This allows operation for delete virtual volumes in each Vplex to operate in parellel,
            // but subsequent steps will wait on all the delete virtual volumes operations to complete.
            for (URI vplexURI : vplexMap.keySet()) {
                String nextStepWaitFor = waitFor;
                StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
                // First validate that the backend volumes for these VPLEX volumes are
                // the actual volumes used by the VPLEX volume on the VPLEX system. We
                // add this verification in case changes were made outside ViPR, such
                // as a migration, that caused the backend volumes to change. We don't
                // want to delete a backend volume that may in fact be used some other
                // VPLEX volume.
                List<URI> vplexVolumeURIs = VolumeDescriptor.getVolumeURIs(vplexMap.get(vplexURI));

                for (URI vplexVolumeURI : vplexVolumeURIs) {
                    Volume vplexVolume = _dbClient.queryObject(Volume.class, vplexVolumeURI);
                    if (vplexVolume == null || vplexVolume.getInactive() == true) {
                        continue;
                    }

                    // Skip validation if the volume was never successfully created.
                    if (vplexVolume.getDeviceLabel() == null) {
                        _log.info("Volume {} with Id {} was never created on the VPLEX as device label is null "
                                + "hence skip validation on delete", vplexVolume.getLabel(), vplexVolume.getId());
                        continue;
                    }

                    // Skip validation if the volume can't be found. This protects against
                    // deletions where the VPLEX volume deletion was successful, but the
                    // backend volume deletion failed.
                    try {
                        VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexSystem, _dbClient);
                        client.findVirtualVolume(vplexVolume.getDeviceLabel(), vplexVolume.getNativeId());
                    } catch (VPlexApiException ex) {
                        if (ex.getServiceCode() == ServiceCode.VPLEX_CANT_FIND_REQUESTED_VOLUME) {
                            _log.info("VPlex virtual volume: " + vplexVolume.getNativeId()
                                    + " has already been deleted; will skip validation");
                            continue;
                        } else {
                            _log.error("Exception finding Virtual Volume", ex);
                            throw ex;
                        }
                    }

                    createWorkflowStepToValidateVPlexVolume(workflow, vplexSystem, vplexVolumeURI, waitFor);
                    nextStepWaitFor = VALIDATE_VPLEX_VOLUME_STEP;
                }

                // If there are VPlex Volumes fronting SRDF targets, handle them.
                // They will need to be removed from the CG that represents the SRDF targets.
                List<URI> volsForTargetCG = VPlexSrdfUtil.returnVplexSrdfTargets(_dbClient, vplexVolumeURIs);
                if (!volsForTargetCG.isEmpty()) {
                    URI volURI = volsForTargetCG.get(0);
                    Volume vol = VPlexControllerUtils.getDataObject(Volume.class, volURI, _dbClient);
                    ConsistencyGroupManager consistencyGroupManager = getConsistencyGroupManager(vol);
                    nextStepWaitFor = consistencyGroupManager.addStepsForRemovingVolumesFromSRDFTargetCG(
                            workflow, vplexSystem, volsForTargetCG, nextStepWaitFor);
                }

                workflow.createStep(VPLEX_STEP,
                        String.format("Delete VPlex Virtual Volumes:%n%s",
                                BlockDeviceController.getVolumesMsg(_dbClient, vplexVolumeURIs)),
                        nextStepWaitFor, vplexURI,
                        DiscoveredDataObject.Type.vplex.name(), this.getClass(),
                        deleteVirtualVolumesMethod(vplexURI, vplexVolumeURIs, doNotFullyDeleteVolumeList),
                        rollbackMethodNullMethod(), null);
            }

            // Make a Map of array URI to StorageSystem
            Map<URI, StorageSystem> arrayMap = new HashMap<URI, StorageSystem>();
            // Make a Map of StorageSystem to a list of Volume URIs to be deleted for the next Step.
            Map<URI, List<URI>> arrayVolumesMap = new HashMap<URI, List<URI>>();
            // Make a list of ExportGroups that is used.
            List<URI> exportGroupList = new ArrayList<URI>();
            // Create a series of steps to remove the volume from the Export Groups.
            // We leave the Export Groups, anticipating they will be used for other
            // volumes or used later.
            List<URI> backendVolURIs = new ArrayList<URI>();
            for (URI vplexVolumeURI : allVplexVolumeURIs) {
                Volume vplexVolume = _dbClient.queryObject(Volume.class, vplexVolumeURI);
                if ((vplexVolume == null)
                        || (vplexVolume.getInactive())
                        || (vplexVolume.isIngestedVolumeWithoutBackend(_dbClient))
                        || doNotFullyDeleteVolumeList.contains(vplexVolumeURI)) {
                    continue;
                }
                if (null == vplexVolume.getAssociatedVolumes()) {
                    _log.warn("VPLEX volume {} has no backend volumes. It was possibly ingested 'Virtual Volume Only'.",
                            vplexVolume.forDisplay());
                } else {
                    for (String assocVolumeId : vplexVolume.getAssociatedVolumes()) {
                        URI assocVolumeURI = new URI(assocVolumeId);
                        Volume volume = _dbClient.queryObject(Volume.class, assocVolumeURI);
                        if (volume == null || volume.getInactive() == true) {
                            continue;
                        }
                        StorageSystem array = arrayMap.get(volume.getStorageController());
                        if (array == null) {
                            array = _dbClient.queryObject(StorageSystem.class, volume.getStorageController());
                            arrayMap.put(array.getId(), array);
                        }

                        if (arrayVolumesMap.get(array.getId()) == null) {
                            arrayVolumesMap.put(array.getId(), new ArrayList<URI>());
                        }
                        arrayVolumesMap.get(array.getId()).add(volume.getId());
                        backendVolURIs.add(volume.getId());
                    }
                }

                // When virtual volume is deleted then associated mirror is also deleted
                // hence the backend volume for that mirror needs to be deleted as well.
                if (vplexVolume.getMirrors() != null && !(vplexVolume.getMirrors().isEmpty())) {
                    for (String mirrorId : vplexVolume.getMirrors()) {
                        VplexMirror vplexMirror = _dbClient.queryObject(VplexMirror.class, URI.create(mirrorId));
                        if (vplexMirror == null
                                || vplexMirror.getInactive() == true
                                || vplexMirror.getAssociatedVolumes() == null) {
                            continue;
                        }
                        for (String assocVolumeId : vplexMirror.getAssociatedVolumes()) {
                            URI assocVolumeURI = new URI(assocVolumeId);
                            Volume volume = _dbClient.queryObject(Volume.class, assocVolumeURI);
                            if (volume == null || volume.getInactive() == true) {
                                continue;
                            }
                            StorageSystem array = arrayMap.get(volume.getStorageController());
                            if (array == null) {
                                array = _dbClient.queryObject(StorageSystem.class, volume.getStorageController());
                                arrayMap.put(array.getId(), array);
                            }

                            if (arrayVolumesMap.get(array.getId()) == null) {
                                arrayVolumesMap.put(array.getId(), new ArrayList<URI>());
                            }
                            arrayVolumesMap.get(array.getId()).add(volume.getId());
                            backendVolURIs.add(volume.getId());
                        }
                    }
                }
            }

            waitFor = VPLEX_STEP;
            if (vplexAddUnexportVolumeWfSteps(workflow, VPLEX_STEP, backendVolURIs, exportGroupList)) {
                waitFor = UNEXPORT_STEP;
            }
            return waitFor;
        } catch (Exception ex) {
            throw VPlexApiException.exceptions.addStepsForDeleteVolumesFailed(ex);
        }
    }

    /**
     * Add step to Workflow for post - delete clean of Virtual Volumes (i.e. marking them inactive).
     *
     * @param workflow
     *            -- Workflow
     * @param waitFor
     *            -- String waitFor of previous step, we wait on this to complete
     * @param volumes
     *            -- List of VolumeDescriptors
     * @param taskId
     *            -- String overall task id.
     * @param completer
     *            -- VolumeWorkflowCompleter
     * @return -- Returns waitFor of next step
     */
    @Override
    public String addStepsForPostDeleteVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes,
            String taskId, VolumeWorkflowCompleter completer) {
        // Filter to get only the VPlex volumes.
        List<VolumeDescriptor> vplexVolumes = VolumeDescriptor.filterByType(volumes,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_VIRT_VOLUME },
                new VolumeDescriptor.Type[] {});

        // Check to see if there are any volumes flagged to not be fully deleted.
        // Any flagged volumes will be removed from the list of volumes to delete.
        List<VolumeDescriptor> descriptorsToRemove = VolumeDescriptor.getDoNotDeleteDescriptors(vplexVolumes);
        vplexVolumes.removeAll(descriptorsToRemove);

        String returnWaitFor = waitFor;

        // If there are no VPlex volumes, just return
        if (vplexVolumes.isEmpty()) {
            return returnWaitFor;
        }

        // Segregate by device and loop over each VPLEX system.
        // Sort the volumes by its system, and consistency group
        Map<URI, Set<URI>> cgVolsMap = new HashMap<URI, Set<URI>>();
        // Keep a separate map for determining if we should delete the VPLEX CG as part of the delete operation.
        Map<URI, Set<URI>> cgVolsWithBackingVolsMap = new HashMap<URI, Set<URI>>();
        Map<URI, List<VolumeDescriptor>> vplexMap = VolumeDescriptor.getDeviceMap(vplexVolumes);
        for (URI vplexURI : vplexMap.keySet()) {
            List<URI> vplexVolumeURIs = VolumeDescriptor.getVolumeURIs(vplexMap.get(vplexURI));
            List<URI> forgetVolumeURIs = new ArrayList<URI>();
            for (URI vplexVolumeURI : vplexVolumeURIs) {
                Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
                boolean inCG = false;
                if (!NullColumnValueGetter.isNullURI(vplexVolume.getConsistencyGroup())) {
                    inCG = true;
                }
                if (null == vplexVolume.getAssociatedVolumes()) {
                    _log.warn("VPLEX volume {} has no backend volumes. It was possibly ingested 'Virtual Volume Only'.",
                            vplexVolume.forDisplay());
                } else {
                    for (String forgetVolumeId : vplexVolume.getAssociatedVolumes()) {
                        forgetVolumeURIs.add(URI.create(forgetVolumeId));
                    }
                }
                if (inCG) {
                    Set<URI> cgVols = cgVolsMap.get(vplexVolume.getConsistencyGroup());
                    if (cgVols == null) {
                        cgVolsMap.put(vplexVolume.getConsistencyGroup(), new HashSet<>());
                        cgVolsWithBackingVolsMap.put(vplexVolume.getConsistencyGroup(), new HashSet<>());
                    }
                    cgVolsMap.get(vplexVolume.getConsistencyGroup()).add(vplexVolumeURI);
                    cgVolsWithBackingVolsMap.get(vplexVolume.getConsistencyGroup()).add(vplexVolumeURI);
                    cgVolsWithBackingVolsMap.get(vplexVolume.getConsistencyGroup()).addAll(forgetVolumeURIs);
                }
                
                // Adding the VPLEX mirror backend volume to forgetVolumeURIs
                if (vplexVolume.getMirrors() != null && !(vplexVolume.getMirrors().isEmpty())) {
                    for (String mirrorId : vplexVolume.getMirrors()) {
                        VplexMirror vplexMirror = _dbClient.queryObject(VplexMirror.class, URI.create(mirrorId));
                        if (null != vplexMirror && !vplexMirror.getInactive() && null != vplexMirror.getAssociatedVolumes()) {
                            for (String forgetVolumeId : vplexMirror.getAssociatedVolumes()) {
                                forgetVolumeURIs.add(URI.create(forgetVolumeId));
                            }
                        }
                    }
                }
            }

            // Add a step to forget the backend volumes for the deleted
            // VPLEX volumes on this VPLEX system.
            addStepToForgetVolumes(workflow, vplexURI, forgetVolumeURIs, returnWaitFor);
        }

        // Get the VPlex Volume URIs and any VPLEX system URI. It does not matter which
        // system as it the step simply marks ViPR volumes in the database inactive.
        List<URI> allVplexVolumeURIs = VolumeDescriptor.getVolumeURIs(vplexVolumes);
        URI vplexURI = vplexVolumes.get(0).getDeviceURI();

        // Add a step to the Workflow to mark the Virtual Volumes inactive.
        // Rollback does the same thing.
        returnWaitFor = workflow.createStep(null, "Mark virtual volumes inactive", VOLUME_FORGET_STEP,
                vplexURI, DiscoveredDataObject.Type.vplex.name(), this.getClass(),
                markVolumesInactiveMethod(allVplexVolumeURIs),
                markVolumesInactiveMethod(allVplexVolumeURIs), null);

        if (cgVolsMap.isEmpty()) {
            return returnWaitFor;
        }
        Volume vol = getDataObject(Volume.class, allVplexVolumeURIs.get(0), _dbClient);
        ConsistencyGroupManager consistencyGroupManager = getConsistencyGroupManager(vol);

        // Generate step(s) to delete the VPLEX consistency groups
        for (URI cgURI : cgVolsMap.keySet()) {

            // Skip volumes that are part of a VPlex SRDF target CG, as it will
            // be deleted earlier
            List<URI> volURIs = new ArrayList<URI>(cgVolsMap.get(cgURI));
            volURIs = VPlexSrdfUtil.filterOutVplexSrdfTargets(_dbClient, volURIs);
            if (volURIs.isEmpty()) {
                _log.info(String.format("CG %s has all VPLEX SRDF targets, skipping as CG should already be deleted",
                        cgURI));
                continue;
            }

            // find member volumes in the group
            volURIs = new ArrayList<URI>(cgVolsWithBackingVolsMap.get(cgURI));
            Volume firstVol = _dbClient.queryObject(Volume.class, volURIs.get(0));
            URI storage = firstVol.getStorageController();
            // delete CG from array
            if (VPlexUtil.cgHasNoOtherVPlexVolumes(_dbClient, cgURI, volURIs)) {
                _log.info(String.format("Adding step to delete the consistency group %s", cgURI));
                returnWaitFor = consistencyGroupManager.addStepsForDeleteConsistencyGroup(workflow, returnWaitFor,
                        storage, cgURI, false);
            } else {
            	_log.info(String.format("Skipping add step to delete the consistency group %s. Consistency group "
            			+ "contains other VPLEX volumes that have not been accounted for.", cgURI));
            }
        }

        return returnWaitFor;
    }

    public Workflow.Method markVolumesInactiveMethod(List<URI> volumes) {
        return new Workflow.Method(MARK_VIRTUAL_VOLUMES_INACTIVE, volumes);
    }

    /**
     * A workflow step that marks Volumes inactive after all the delete volume
     * workflow steps have completed.
     *
     c* @param volumes
     *            -- List<URI> of volumes
     * @param stepId
     *            -- Workflow Step Id.
     */
    public void markVolumesInactive(List<URI> volumes, String stepId) {
        try {
            for (URI uri : volumes) {
                Volume volume = _dbClient.queryObject(Volume.class, uri);
                if (volume == null) {
                    continue;
                }

                URI fullCopySourceVolumeURI = volume.getAssociatedSourceVolume();
                if (!NullColumnValueGetter.isNullURI(fullCopySourceVolumeURI)) {
                    // This is a full copy VPLEX volume, remove it from
                    // the list of full copies for the source volume.
                    // Note that the passed volumes could also have copies
                    // themselves, but we do not update these copies to
                    // null out the source. This allows one to still know
                    // that the volume was created as a full copy of some
                    // other volume, even though that source volume has been
                    // deleted. However, these copies could at some later
                    // time also be deleted, and the source would refer to
                    // a volume that has been deleted. So, it is important
                    // to check if the full copy source is null or inactive.
                    Volume fullCopySourceVolume = _dbClient.queryObject(Volume.class, fullCopySourceVolumeURI);
                    if ((fullCopySourceVolume != null) && (!fullCopySourceVolume.getInactive())) {
                        StringSet fullCopyIds = fullCopySourceVolume.getFullCopies();
                        if (fullCopyIds != null) {
                            if (fullCopyIds.contains(volume.getId().toString())) {
                                fullCopyIds.remove(volume.getId().toString());
                                _dbClient.updateObject(fullCopySourceVolume);
                            }
                        }
                    }
                }

                if (!volume.getInactive()) {
                    _log.info("Marking volume in-active: " + volume.getId());
                    _dbClient.removeObject(volume);
                }
            }
        } finally {
            WorkflowStepCompleter.stepSucceded(stepId);
        }
    }

    /**
     * Adds a step in the passed workflow to tell the VPLEX system with the
     * passed URI to forget about the backend storage volumes with the passed URIs.
     *
     * @param workflow
     *            A reference to the workflow.
     * @param vplexSystemURI
     *            The URI of the VPLEX storage system.
     * @param volumeURIs
     *            The URIs of the backend volumes to be forgotten.
     * @param waitFor
     *            The step in the workflow for which this step should wait
     *            before executing.
     */
    private void addStepToForgetVolumes(Workflow workflow, URI vplexSystemURI,
            List<URI> volumeURIs, String waitFor) {

        // Get the native volume info for the passed backend volumes.
        List<VolumeInfo> nativeVolumeInfoList = getNativeVolumeInfo(volumeURIs);

        // Add a workflow step to tell the passed VPLEX to forget about
        // the volumes with the passed URIs.
        workflow.createStep(
                VOLUME_FORGET_STEP,
                String.format("Forget Volumes:%n%s", BlockDeviceController.getVolumesMsg(_dbClient, volumeURIs)),
                waitFor, vplexSystemURI, DiscoveredDataObject.Type.vplex.name(), this.getClass(),
                createForgetVolumesMethod(vplexSystemURI, nativeVolumeInfoList), null, null);
    }

    /**
     * Gets the native volume information required by the VPLEX client for
     * the passed backend volumes.
     *
     * @param volumeURIs
     *            The URIs of the VPLEX backend volumes.
     *
     * @return A list of the native volume information for the passed backend volumes.
     *         If any volumes are missing or inactive, they are ignored and not returned.
     */
    private List<VolumeInfo> getNativeVolumeInfo(Collection<URI> volumeURIs) {
        List<VolumeInfo> nativeVolumeInfoList = new ArrayList<>();
        for (URI volumeURI : volumeURIs) {
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            if (volume == null || volume.getInactive()) {
                continue;
            }
            StorageSystem volumeSystem = getDataObject(StorageSystem.class, volume.getStorageController(), _dbClient);
            List<String> itls = VPlexControllerUtils.getVolumeITLs(volume);
            VolumeInfo vInfo = new VolumeInfo(volumeSystem.getNativeGuid(), volumeSystem.getSystemType(),
                    volume.getWWN().toUpperCase().replaceAll(":", ""), volume.getNativeId(),
                    volume.getThinlyProvisioned().booleanValue(), itls);
            nativeVolumeInfoList.add(vInfo);
        }
        return nativeVolumeInfoList;
    }

    /**
     * Creates the workflow execute method for forgetting storage volumes.
     *
     * @param vplexSystemURI
     *            The URI of the VPLEX storage system.
     * @param volumeInfo
     *            The native volume information for the volumes to be forgotten.
     *
     * @return A reference to the created workflow method.
     */
    private Workflow.Method createForgetVolumesMethod(URI vplexSystemURI,
            List<VolumeInfo> volumeInfo) {
        return new Workflow.Method(FORGET_VOLUMES_METHOD_NAME, vplexSystemURI, volumeInfo);
    }

    /**
     * Uses the VPLREX client for the VPLEX storage system with the passed URI to
     * tell the VPLERX system to forget the volumes with the passed URIs.
     *
     * @param vplexSystemURI
     *            The URI of the VPLEX storage system.
     * @param volumeInfo
     *            The native volume information for the volumes to be forgotten.
     * @param stepId
     *            The id of the workflow step that invoked this method.
     */
    public void forgetVolumes(URI vplexSystemURI, List<VolumeInfo> volumeInfo, String stepId) {
        String warnMsg = null;
        String vplexSystemName = null;
        try {
            // Workflow step is executing.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Get the VPLEX client for this VPLEX system.
            StorageSystem vplexSystem = _dbClient.queryObject(StorageSystem.class,
                    vplexSystemURI);
            vplexSystemName = vplexSystem.getLabel();
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexSystem, _dbClient);

            // Tell the VPLEX system to forget about these volumes.
            client.forgetVolumes(volumeInfo);
        } catch (Exception ex) {
            StringBuffer forgottenVolumeInfo = new StringBuffer();
            for (VolumeInfo vInfo : volumeInfo) {
                if (forgottenVolumeInfo.length() != 0) {
                    forgottenVolumeInfo.append(", ");
                }
                forgottenVolumeInfo.append(vInfo.getVolumeWWN());
            }
            ServiceCoded sc = VPlexApiException.exceptions.forgetVolumesFailed(forgottenVolumeInfo.toString(),
                    vplexSystemName, ex.getMessage(), ex);
            warnMsg = sc.getMessage();
            _log.warn(warnMsg);
        }

        // This is a cleanup step that we don't want to impact the
        // workflow execution if it fails.
        WorkflowStepCompleter.stepSucceeded(stepId, warnMsg);
    }

    /**
     * Adds a null provisioning step, but a forgetVolumes rollback step.
     * Useful when we're exporting volumes but if some goes awry we want to forget them
     * after unexporting them.
     *
     * @param workflow
     *            A reference to the workflow.
     * @param vplexSystemURI
     *            The URI of the VPLEX storage system.
     * @param volumeURIs
     *            The URIs of the volumes to be forgotten.
     * @param waitFor
     *            The step in the workflow for which this step should wait
     *            before executing.
     * @return stepId that was created
     */
    private String addRollbackStepToForgetVolumes(Workflow workflow, URI vplexSystemURI,
            List<URI> volumeURIs, String waitFor) {
        // Add a workflow step to tell the passed VPLEX to forget about
        // the volumes with the passed URIs.
        String stepId = workflow.createStepId();
        workflow.createStep(
                VOLUME_FORGET_STEP, String.format("Null provisioning step; forget Volumes on rollback:%n%s",
                        BlockDeviceController.getVolumesMsg(_dbClient, volumeURIs)),
                waitFor, vplexSystemURI,
                DiscoveredDataObject.Type.vplex.name(), this.getClass(), rollbackMethodNullMethod(),
                createRollbackForgetVolumesMethod(vplexSystemURI, volumeURIs, stepId), stepId);
        return stepId;
    }

    /**
     * Creates the workflow execute method for forgetting storage volumes.
     *
     * @param vplexSystemURI
     *            The URI of the VPLEX storage system.
     * @param volumeURIs
     *            The URIs of the volumes to be forgotten.
     * @param forgetVolumeDataStepId
     *            The step id where data is stored identifying volumes that were successfully masked.
     *
     * @return A reference to the created workflow method.
     */
    private Workflow.Method createRollbackForgetVolumesMethod(URI vplexSystemURI,
            List<URI> volumeURIs, String forgetVolumeDataStepId) {
        return new Workflow.Method(RB_FORGET_VOLUMES_METHOD_NAME, vplexSystemURI, volumeURIs, forgetVolumeDataStepId);
    }

    /**
     * Uses the VPLEX client for the VPLEX storage system with the passed URI to
     * tell the VPLEX system to forget the volumes with the passed URIs.
     *
     * @param vplexSystemURI
     *            The URI of the VPLEX storage system.
     * @param volumeURIs
     *            The URIs of the volumes to be forgotten.
     * @param forgetVolumeDataStepId
     *            The step id where data is stored identifying volumes that were successfully masked.
     * @param stepId
     *            The id of the workflow step that invoked this method.
     */
    public void rollbackForgetVolumes(URI vplexSystemURI, List<URI> volumeURIs, String forgetVolumeDataStepId, String stepId) {
        // We only need to forget volumes that were successfully masked to the VPLEX.
        @SuppressWarnings("unchecked")
        Set<URI> maskedVolumeURIs = (Set<URI>) WorkflowService.getInstance().loadWorkflowData(forgetVolumeDataStepId, "forget");
        if (!CollectionUtils.isEmpty(maskedVolumeURIs)) {
            forgetVolumes(vplexSystemURI, getNativeVolumeInfo(maskedVolumeURIs), stepId);            
        } else {
            // If none are exported, then there is nothing to forget.
            String successMsg = String.format("Volumes %s are not exported to the VPLEX and don't need to be forgotten", volumeURIs);
            _log.info(successMsg);
            WorkflowStepCompleter.stepSucceeded(stepId, successMsg);
        }
    }

    private Workflow.Method deleteVirtualVolumesMethod(URI vplexURI, List<URI> volumeURIs, List<URI> doNotFullyDeleteVolumeList) {
        return new Workflow.Method(DELETE_VIRTUAL_VOLUMES_METHOD_NAME, vplexURI,
                volumeURIs, doNotFullyDeleteVolumeList);
    }

    /**
     * A workflow Step to delete VPLex Virtual volumes.
     * This Step is also used to rollback create virtual volumes.
     * NOTE NOTE: The parameters here must match deleteVirtualVolumesMethod above (except stepId).
     *
     * @param vplexURI
     * @param volumeURIs
     * @param doNotFullyDeleteVolumeList
     * @param stepId
     * @throws WorkflowException
     */
    public void deleteVirtualVolumes(URI vplexURI, List<URI> volumeURIs,
            List<URI> doNotFullyDeleteVolumeList, String stepId) throws WorkflowException {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexURI, _dbClient);
            StringBuilder errMsgBuilder = new StringBuilder();
            boolean failed = false;

            // Loop deleting each volume by name (the deviceLabel in the Volume).
            for (URI volumeURI : volumeURIs) {
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                if (volume == null || volume.getInactive() == true) {
                    continue;
                }
                // Skip this operation if the volume has already been deleted
                if (volume.getDeviceLabel() == null) {
                    _log.info("Volume {} with Id {} was never created on the Vplex as device label is null "
                            + "hence skip volume delete on VPLEX", volume.getLabel(), volume.getId());
                    continue;
                }
                try {
                    client.findVirtualVolume(volume.getDeviceLabel(), volume.getNativeId());
                } catch (VPlexApiException ex) {
                    if (ex.getServiceCode() == ServiceCode.VPLEX_CANT_FIND_REQUESTED_VOLUME) {
                        _log.info("VPlex virtual volume: " + volume.getNativeId()
                                + " has already been deleted; will skip deletion of virtual volume");
                        continue;
                    } else {
                        _log.error("Exception finding Virtual Volume", ex);
                        throw ex;
                    }
                }
                try {
                    if (volume.getNativeId() != null) {
                        // Volumes in consistency groups must be removed.
                        BlockConsistencyGroup cg = null;
                        if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
                            cg = getDataObject(BlockConsistencyGroup.class, volume.getConsistencyGroup(), _dbClient);
                        }
                        if (cg != null) {
                            // Call the appropriate ConsistencyGroupManager to delete the CG volume
                            ConsistencyGroupManager consistencyGroupManager = getConsistencyGroupManager(volume);
                            consistencyGroupManager.deleteConsistencyGroupVolume(vplexURI, volume, cg);
                        }

                        // Check to see if there are any entries in the doNotFullyDeleteVolumeList.
                        // If this volume ID is flagged (in that list) we can skip the call
                        // to delete volume.
                        if (doNotFullyDeleteVolumeList == null
                                || doNotFullyDeleteVolumeList.isEmpty()
                                || !doNotFullyDeleteVolumeList.contains(volume.getId())) {
                            // We only retry a dismantle failure on volumes created
                            // in ViPR as the retry code relies on the well-known ViPR
                            // naming conventions and virtual volume structure to find
                            // VPLEX artifacts related to the volume being deleted.
                            _log.info(String.format("Deleting VPlex virtual volume %s (%s)",
                                    volume.getDeviceLabel(), volume.getNativeId()));
                            boolean isIngestedWithoutBackend = volume.isIngestedVolumeWithoutBackend(_dbClient);
                            client.deleteVirtualVolume(volume.getDeviceLabel(), !isIngestedWithoutBackend, !isIngestedWithoutBackend);
                        }

                        // Record VPLEX volume deleted event.
                        recordBourneVolumeEvent(volume.getId(),
                                OperationTypeEnum.DELETE_BLOCK_VOLUME.getEvType(true),
                                Operation.Status.ready,
                                OperationTypeEnum.DELETE_BLOCK_VOLUME.getDescription());

                        if (volume.getMirrors() != null && !(volume.getMirrors().isEmpty())) {
                            for (String mirrorId : volume.getMirrors()) {
                                VplexMirror mirror = _dbClient.queryObject(VplexMirror.class, URI.create(mirrorId));
                                if (null != mirror) {
                                    _log.info("Marking mirror {} {} for deletion.", mirror.getId(), mirror.getDeviceLabel());
                                    _dbClient.removeObject(mirror);
                                }
                            }
                        }
                    }
                } catch (Exception ex) {

                    _log.error("Exception deleting Virtual Volume: " + volumeURI, ex);

                    // Record VPLEX volume deletion failed event.
                    recordBourneVolumeEvent(volume.getId(),
                            OperationTypeEnum.DELETE_BLOCK_VOLUME.getEvType(false),
                            Operation.Status.error,
                            OperationTypeEnum.DELETE_BLOCK_VOLUME.getDescription());

                    // Update error message
                    if (errMsgBuilder.length() != 0) {
                        errMsgBuilder.append("\n");
                    } else {
                        errMsgBuilder.append("Exception deleting vplex virtual volume(s):\n");
                    }
                    errMsgBuilder.append(volume.getLabel());
                    errMsgBuilder.append(":");
                    errMsgBuilder.append(ex.getMessage());

                    failed = true;
                }
            }
            if (failed) {
                String opName = ResourceOperationTypeEnum.DELETE_VIRTUAL_VOLUME.getName();
                ServiceError serviceError = VPlexApiException.errors.jobFailedOp(opName);
                serviceError.setMessage(errMsgBuilder.toString());
                WorkflowStepCompleter.stepFailed(stepId, serviceError);
            } else {
                WorkflowStepCompleter.stepSucceded(stepId);
            }
        } catch (VPlexApiException vae) {
            _log.error("Exception deleting VPlex Virtual Volume: " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception deleting VPlex Virtual Volume: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.DELETE_VIRTUAL_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.deleteVirtualVolumesFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
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

            Workflow workflow = _workflowService.getNewWorkflow(this, "exportGroupCreate", true, opId);

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
                        ExportMaskUtils.filterVolumeMap(volumeMap, srcVolumes), true, workflow, null, opId);
            }

            // If possible, do the HA side export. To do this we must have both
            // HA side initiators, and volumes accessible from the HA side.
            if (haVarray != null && varrayToInitiators.get(haVarray) != null) {
                assembleExportMasksWorkflow(vplex, export, haVarray,
                        varrayToInitiators.get(haVarray),
                        ExportMaskUtils.filterVolumeMap(volumeMap, varrayToVolumes.get(haVarray)), true,
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
            failStep(completer, opId, vae);
        } catch (Exception ex) {
            _log.error("Exception creating Export Group: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.CREATE_EXPORT_GROUP.getName();
            ServiceError serviceError = VPlexApiException.errors.exportGroupCreateFailed(opName, ex);
            failStep(completer, opId, serviceError);
        }
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
     * @param zoningStepNeeded - Determines whether zone step is needed
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
            Map<URI, Integer> blockObjectMap, boolean zoningStepNeeded, Workflow workflow, String waitFor, String opId) throws Exception {

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

        VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexSystem, _dbClient);

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
                
                List<String> initiatorNames = getInitiatorNames(vplexSystem.getSerialNumber(), vplexClusterName, client,
                        initiatorList.iterator(), doInitiatorRefresh);
                
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
        String zoningStepId = waitFor;
        if (zoningStepNeeded) {
            _log.info("Adding step to update zones if required.");
            zoningStepId = addStepsForZoningUpdate(export, initiators, blockObjectMap, workflow, waitFor, exportMasksToCreateOnDevice,
                    exportMasksToUpdateOnDevice);
        } else {
            // If exportMasksToCreateOnDevice list as not empty, new export must be created and zoned on switch .
            if (!exportMasksToCreateOnDevice.isEmpty()) {

                _log.info("Adding zone step to newly created exportMask");
                zoningStepId = addStepsForZoningUpdate(export, initiators, blockObjectMap, workflow, waitFor,
                        exportMasksToCreateOnDevice,
                        new ArrayList<ExportMask>());
            }

            // We are in exportGroupAddVolumes method with controller config "Enable Zoning On Export Add Volume" is set to False
            // create the FCZoneReference for new volumes by referencing existing FCZoneReference on same ExportGroup
            if (!exportMasksToUpdateOnDevice.isEmpty()) {
                _log.info("Update ExportGroup: {} will use existing zone and new FCZoneReference will get added", export);
                zoningStepId = addStepsForFCZoneRefCreate(export, blockObjectMap, workflow, zoningStepId, exportMasksToUpdateOnDevice);

            }

        }
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

        List<String> initiatorNames = getInitiatorNames(
                vplexSystem.getSerialNumber(), vplexCluster, client, inits.iterator(), doInitiatorRefresh);

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
            /*currently the Export Mask is added to the Export Group after all the hosts are being processed
              Change made for COP-31815
              This is so that ViPR can discover this ExportMask in the function:
               VPlexDEviceController.getInitiatorExportMasks 
            */
            exportGroup.addExportMask(exportMask.getId());
            _dbClient.updateObject(exportGroup);
        }

        return foundMatchingStorageView;
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
     * Handles creating a workflow method for creating the zoning reference for ExportMasks being
     * updated with controller config flag Enable Zoning On Export Add Volume set to false
     *
     * @param exportGroup
     *            the ViPR ExportGroup in question
     * @param blockObjectMap
     *            the map of URIs to block volumes for export
     * @param workflow
     *            the controller Workflow
     * @param waitFor
     *            if non-null, step will wait for previous step.
     * @param exportMasksToUpdateOnDevice
     *            collection of ExportMasks to update on the device
     * @return
     */
    private String addStepsForFCZoneRefCreate(URI exportGroup, Map<URI, Integer> blockObjectMap, Workflow workflow,
            String waitFor, List<ExportMask> exportMasksToUpdateOnDevice) {

        // Add a step to create the underlying zoning.
        List<FCZoneReference> fcList = new ArrayList<FCZoneReference>();
        String zoningStepId = workflow.createStepId();
        for (ExportMask exportMask : exportMasksToUpdateOnDevice) {
            // Find one FCZoneReference for each Initiator_port pair for Export masks
            Map<String, FCZoneReference> fcref = getPwwnKeyFCZoneReferencesMap(exportMask, _dbClient);
            List<FCZoneReference> fcs = _networkDeviceController.generateFCZoneReferences(exportGroup, fcref.values(),
                    blockObjectMap.keySet());
            fcList.addAll(fcs);
        }
        Workflow.Method zoningExecuteMethod = _networkDeviceController.createFCZoneReferencesMethod(fcList);
        Workflow.Method zoningRollbackMethod = _networkDeviceController.rollbackPersistFCZoneReferencesMethod(fcList);

        zoningStepId = workflow.createStep(ZONING_FC_REF_STEP,
                String.format("Adding FCZoneReference for existing export"),
                waitFor, nullURI, "network-system",
                _networkDeviceController.getClass(), zoningExecuteMethod, zoningRollbackMethod, zoningStepId);
        return zoningStepId;
    }

    /**
     * Find one FCZoneReference for each Initiator_port pair for referencing Export Mask
     * and store in a Map
     * 
     * @param exportMask export Group
     * @param dbClient db client
     * @return Map of pwwnKey with one FCZoneReference.
     */
    private Map<String, FCZoneReference> getPwwnKeyFCZoneReferencesMap(ExportMask exportMask, DbClient dbClient) {
        Map<String, FCZoneReference> resultFCRefMap = new HashMap<>();
        StringSetMap refreshMap = exportMask.getZoningMap();
        // Zone Map consist of initiator to ports map.
        // Construct Initiator_Port pair (pwwnKey) and find one FCZoneReference for each pwwnKey
        for (String initKey : refreshMap.keySet()) {
            Initiator initiator = dbClient.queryObject(Initiator.class, URI.create(initKey));
            if (initiator.getProtocol().equals(Block.FC.name())) {
                StringSet portsKey = refreshMap.get(initKey);
                for (String portkey : portsKey) {
                    StoragePort port = dbClient.queryObject(StoragePort.class, URI.create(portkey));
                    String pwwnKey = FCZoneReference.makeEndpointsKey(initiator.getInitiatorPort(), port.getPortNetworkId());
                    URIQueryResultList queryList = new URIQueryResultList();
                    // find All FCZoneReference for pwwnKey i.e. initiator_port
                    dbClient.queryByConstraint(AlternateIdConstraint.Factory.getFCZoneReferenceKeyConstraint(pwwnKey), queryList);
                    for (URI uri : queryList) {
                        FCZoneReference fc = dbClient.queryObject(FCZoneReference.class, uri);
                        resultFCRefMap.put(pwwnKey, fc);
                        // Need only one fcRef for each pwwnKey key;
                        break;
                    }
                }
            }
        }
        return resultFCRefMap;
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
                previousStepId, vplexSystem.getId(), vplexSystem.getSystemType(), this.getClass(),
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
        ExportTaskCompleter exportTaskCompleter = new ExportMaskCreateCompleter(
                exportGroup.getId(), exportMask.getId(), inits, blockObjectMap,
                maskingStep);
        // Add a step to export on the VPlex. They call this a Storage View.
        Workflow.Method storageViewExecuteMethod = new Workflow.Method(CREATE_STORAGE_VIEW,
                vplexSystem.getId(), exportGroup.getId(), exportMask.getId(), blockObjectMap, inits, hostTargets, exportTaskCompleter);

        // Workflow.Method storageViewRollbackMethod = new Workflow.Method(ROLLBACK_METHOD_NULL);
        Workflow.Method storageViewRollbackMethod = deleteStorageViewMethod(
                vplexSystem.getId(), exportGroup.getId(), exportMask.getId(), true);
        storageViewStepId = workflow.createStep("storageView",
                String.format("Create VPLEX Storage View for ExportGroup %s Mask %s", exportGroup.getId(), exportMask.getMaskName()),
                storageViewStepId, vplexSystem.getId(), vplexSystem.getSystemType(),
                this.getClass(), storageViewExecuteMethod, storageViewRollbackMethod, maskingStep);
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
        Workflow.Method storageViewRollbackMethod = storageViewAddVolumesRollbackMethod(vplexSystem.getId(), 
                exportGroupUri, exportMask.getId(),
                new ArrayList<URI>(blockObjectMap.keySet()), addVolumeStepId);
        storageViewStepId = workflow.createStep("storageView",
                String.format("Updating VPLEX Storage View for ExportGroup %s Mask %s", exportGroupUri, exportMask.getMaskName()),
                storageViewStepId, vplexSystem.getId(), vplexSystem.getSystemType(),
                this.getClass(), storageViewExecuteMethod, storageViewRollbackMethod, addVolumeStepId);

        if (exportMasksToUpdateOnDeviceWithInitiators.get(exportMask.getId()) != null) {

            List<Initiator> initiatorsToAdd = exportMasksToUpdateOnDeviceWithInitiators.get(exportMask.getId());
            List<URI> initiatorURIs = new ArrayList<URI>();
            for (Initiator initiator : initiatorsToAdd) {
                initiatorURIs.add(initiator.getId());
            }

            String addInitStep = workflow.createStepId();
            ExportMaskAddInitiatorCompleter completer = new ExportMaskAddInitiatorCompleter(exportGroupUri, exportMask.getId(), initiatorURIs,
                    new ArrayList<URI>(), addInitStep);

            Workflow.Method addInitiatorMethod = storageViewAddInitiatorsMethod(vplexSystem.getId(), exportGroupUri, exportMask.getId(),
                    initiatorURIs, null, sharedVplexExportMask, completer);

            Workflow.Method initiatorRollback = storageViewAddInitiatorsRollbackMethod(vplexSystem.getId(), 
                    exportGroupUri, exportMask.getId(),
                    initiatorURIs, null, addInitStep);

            storageViewStepId = workflow.createStep("storageView",
                    String.format("Updating VPLEX Storage View Initiators for ExportGroup %s Mask %s", exportGroupUri, exportMask.getMaskName()),
                    storageViewStepId, vplexSystem.getId(), vplexSystem.getSystemType(),
                    this.getClass(), addInitiatorMethod, initiatorRollback, addInitStep);
        }

        if (exportMasksToUpdateOnDeviceWithStoragePorts.containsKey(exportMask.getId())) {
            List<URI> storagePortURIsToAdd = exportMasksToUpdateOnDeviceWithStoragePorts.get(exportMask.getId());

            String addPortStep = workflow.createStepId();
            ExportMaskAddInitiatorCompleter completer = new ExportMaskAddInitiatorCompleter(exportGroupUri, exportMask.getId(),
                    new ArrayList<URI>(), storagePortURIsToAdd, addPortStep);

            // Create a Step to add storage ports to the Storage View
            Workflow.Method addPortsToViewMethod = storageViewAddStoragePortsMethod(vplexSystem.getId(), exportGroupUri, exportMask.getId(),
                    storagePortURIsToAdd, completer);

            Workflow.Method addToViewRollbackMethod = storageViewAddStoragePortsRollbackMethod(vplexSystem.getId(), exportGroupUri,
                    exportMask.getId(), storagePortURIsToAdd, addPortStep);

            storageViewStepId = workflow.createStep("storageView",
                    String.format("Updating VPLEX Storage View StoragePorts for ExportGroup %s Mask %s", exportGroupUri, exportMask.getMaskName()),
                    storageViewStepId, vplexSystem.getId(), vplexSystem.getSystemType(),
                    this.getClass(), addPortsToViewMethod, addToViewRollbackMethod, addPortStep);
        }
        return storageViewStepId;
    }

    /**
     * A Workflow Step to create a Storage View on the VPlex.
     *
     * @param vplexURI
     * @param exportURI
     * @param blockObjectMap
     *            - A list of Volume/Snapshot URIs to LUN ids.
     * @param initiators
     * @param stepId
     * @throws ControllerException
     */
    public void createStorageView(URI vplexURI, URI exportURI, URI exportMaskURI, Map<URI, Integer> blockObjectMap,
            List<URI> initiators, List<URI> targets, ExportMaskCreateCompleter completer, String stepId) throws ControllerException {
        String lockName = null;
        boolean lockAcquired = false;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            ExportMask exportMask = getDataObject(ExportMask.class, exportMaskURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

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
                        getVPlexInitiatorType(initiator));
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

            failStep(completer, stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception creating VPlex Storage View: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.CREATE_STORAGE_VIEW.getName();
            ServiceError serviceError = VPlexApiException.errors.createStorageViewFailed(opName, ex);
            failStep(completer, stepId, serviceError);
        } finally {
            if (lockAcquired) {
                _vplexApiLockManager.releaseLock(lockName);
            }
        }
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
     * A Workflow Step to register initiators on the VPLEX, if not already found registered.
     * 
     * @param initiatorUris the initiator URIs to check for registration
     * @param vplexURI the VPLEX URI
     * @param vplexClusterName the VPLEX cluster name (like cluster-1 or cluster-2)
     * @param stepId the workflow step id
     * @throws ControllerException if something went wrong
     */
    public void registerInitiators(List<URI> initiatorUris, URI vplexURI, String vplexClusterName, String stepId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
            client.clearInitiatorCache(vplexClusterName);

            List<PortInfo> initiatorPortInfos = new ArrayList<PortInfo>();
            List<Initiator> inits = _dbClient.queryObject(Initiator.class, initiatorUris);
            for (Initiator initiator : inits) {
                PortInfo pi = new PortInfo(initiator.getInitiatorPort().toUpperCase()
                        .replaceAll(":", ""), initiator.getInitiatorNode().toUpperCase()
                                .replaceAll(":", ""),
                        initiator.getLabel(),
                        getVPlexInitiatorType(initiator));
                initiatorPortInfos.add(pi);
            }
            client.registerInitiators(initiatorPortInfos, vplexClusterName);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception ex) {
            _log.error("Exception registering initiators: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.REGISTER_EXPORT_INITIATOR.getName();
            ServiceError serviceError = VPlexApiException.errors.registerInitiatorsStepFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    /**
     * Gets the VPLEX initiator type for the passed initiator.
     *
     * @param initiator
     *            A reference to an initiator.
     *
     * @return The VPLEX initiator type.
     */
    private String getVPlexInitiatorType(Initiator initiator) {
        Initiator_Type initiatorType = Initiator_Type.DEFAULT;
        URI initiatorHostURI = initiator.getHost();
        if (!NullColumnValueGetter.isNullURI(initiatorHostURI)) {
            Host initiatorHost = getDataObject(Host.class, initiatorHostURI, _dbClient);
            if (initiatorHost != null) {
                // The only ViPR host type that maps to a VPLEX initiator type
                // is HPUX, otherwise the type is the default for VPLEX. Note
                // that it is required to specify the HPUX initiator type when
                // registering an HPUX initiator in VPLEX.
                if (Host.HostType.HPUX.name().equals(initiatorHost.getType())) {
                    initiatorType = Initiator_Type.HPUX;
                } else if ((Host.HostType.AIX.name().equals(initiatorHost.getType()))
                        || (Host.HostType.AIXVIO.name().equals(initiatorHost.getType()))) {
                    initiatorType = Initiator_Type.AIX;
                } else if (Host.HostType.SUNVCS.name().equals(initiatorHost.getType())) {
                    initiatorType = Initiator_Type.SUN_VCS;
                }
            }
        }

        return initiatorType.getType();
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
            Workflow workflow = _workflowService.getNewWorkflow(this, "exportGroupDelete", false, opId);

            StringBuffer errorMessages = new StringBuffer();
            boolean isValidationNeeded = validatorConfig.isValidationEnabled()
                    && !ExportUtils.checkIfExportGroupIsRP(exportGroup);
            _log.info("Orchestration level validation needed : {}", isValidationNeeded);

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

            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

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
                        volumeURIList = getVolumeListDiff(exportGroup, exportMask, otherExportGroups, volumeURIList);

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
                        Workflow.Method storageViewExecuteMethod = deleteStorageViewMethod(vplex, exportGroup.getId(), exportMask.getId(), false);
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

            String message = errorMessages.toString();
            if (isValidationNeeded && !message.isEmpty()) {
                _log.error("Error Message {}", errorMessages);

                throw DeviceControllerException.exceptions.deleteExportGroupValidationError(
                        exportGroup.forDisplay(),
                        vplexSystem.forDisplay(),
                        message);
            }

            // Start the workflow
            workflow.executePlan(completer, "Successfully deleted ExportMasks for ExportGroup: " + export);
        } catch (Exception ex) {
            _log.error("Exception deleting ExportGroup: " + ex.getMessage());
            String opName = ResourceOperationTypeEnum.DELETE_EXPORT_GROUP.getName();
            ServiceError serviceError = VPlexApiException.errors.exportGroupDeleteFailed(opName, ex);
            failStep(completer, opId, serviceError);
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
    private Workflow.Method deleteStorageViewMethod(URI vplexURI, URI exportGroupURI, URI exportMaskURI, boolean isRollbackStep) {
        return new Workflow.Method(DELETE_STORAGE_VIEW, vplexURI, exportGroupURI, exportMaskURI, isRollbackStep);
    }

    /**
     * A Workflow Step to delete a VPlex Storage View.
     *
     * @param vplexURI vplex
     * @param exportMaskURI export mask
     * @param isRollbackStep is this being run as a rollback step?
     * @param stepId step ID
     * @throws WorkflowException
     */
    public void deleteStorageView(URI vplexURI, URI exportGroupURI, URI exportMaskURI, boolean isRollbackStep, String stepId)
            throws WorkflowException {
        ExportMaskDeleteCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            completer = new ExportMaskDeleteCompleter(exportGroupURI, exportMaskURI, stepId);
            completer.setRollingBack(isRollbackStep);
            
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            Boolean[] viewFound = new Boolean[] { new Boolean(false) };
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

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

                    ExportMaskValidationContext ctx = new ExportMaskValidationContext();
                    ctx.setStorage(vplex);
                    ctx.setExportMask(exportMask);
                    ctx.setBlockObjects(volumeURIs, _dbClient);
                    ctx.setInitiators(initiators);
                    ctx.setAllowExceptions(!WorkflowService.getInstance().isStepInRollbackState(stepId));
                    validator.exportMaskDelete(ctx).validate();

                    InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_084);

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
            failStep(completer, stepId, vae);
        } catch (DeviceControllerException ex) {
            _log.error("Exception deleting ExportMask: " + exportMaskURI, ex);
            failStep(completer, stepId, ex);
        } catch (Exception ex) {
            _log.error("Exception deleting ExportMask: " + exportMaskURI, ex);
            ServiceError svcError = VPlexApiException.errors.deleteStorageViewFailed(ex);
            failStep(completer, stepId, svcError);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.volumecontroller.impl.vplex.VplexController#exportAddVolume(java.net.URI, java.net.URI,
     * java.net.URI,
     * java.lang.Integer, java.lang.String)
     */
    @Override
    public void exportGroupAddVolumes(URI vplexURI, URI exportURI,
            Map<URI, Integer> volumeMap,
            String opId) throws ControllerException {
        String volListStr = "";
        ExportAddVolumeCompleter completer = null;

        try {
            WorkflowStepCompleter.stepExecuting(opId);

            completer = new ExportAddVolumeCompleter(exportURI, volumeMap, opId);
            volListStr = Joiner.on(',').join(volumeMap.keySet());
            Workflow workflow = _workflowService.getNewWorkflow(this, "exportGroupAddVolumes", true, opId);
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

            // Check if Zoning needs to be checked from system config
            // call the doZoneExportMasksCreate to check/create/remove zones with the flag
            String addZoneWhileAddingVolume = customConfigHandler.getComputedCustomConfigValue(
                    CustomConfigConstants.ZONE_ADD_VOLUME,
                    CustomConfigConstants.GLOBAL_KEY, null);
            // Default behavior is we allow zoning checks against the Network System
            Boolean addZoneOnDeviceOperation = true;
            _log.info("zoneExportAddVolumes checking for custom config value {} to skip zoning checks : (Default) : {}",
                    addZoneWhileAddingVolume, addZoneOnDeviceOperation);
            if (addZoneWhileAddingVolume != null) {
                addZoneOnDeviceOperation = Boolean.valueOf(addZoneWhileAddingVolume);
                _log.info("Boolean convereted of : {} : returned by Config handler as : {} ",
                        addZoneWhileAddingVolume, addZoneOnDeviceOperation);
            } else {
                _log.info("Config handler returned null for value so going by default value {}", addZoneOnDeviceOperation);
            }

            _log.info("zoneExportAddVolumes checking for custom config value {} to skip zoning checks : (Custom Config) : {}",
                    addZoneWhileAddingVolume, addZoneOnDeviceOperation);

            // Add all the volumes to the SRC varray if there are src side volumes and
            // initiators that have connectivity to the source side.
            String srcExportStepId = null;
            if (varrayToInitiators.get(srcVarray) != null && srcVolumes != null) {
                srcExportStepId = assembleExportMasksWorkflow(vplexURI, exportURI, srcVarray,
                        varrayToInitiators.get(srcVarray),
                        ExportMaskUtils.filterVolumeMap(volumeMap, srcVolumes), addZoneOnDeviceOperation,
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
                        ExportMaskUtils.filterVolumeMap(volumeMap, varrayToVolumes.get(haVarray)), addZoneOnDeviceOperation,
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
            failStep(completer, opId, vae);
        } catch (Exception ex) {
            String message = String.format("Failed to add Volumes %s to ExportGroup %s",
                    volListStr, exportURI);
            _log.error(message, ex);
            String opName = ResourceOperationTypeEnum.ADD_EXPORT_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.exportGroupAddVolumesFailed(
                    volListStr, exportURI.toString(), opName, ex);
            failStep(completer, opId, serviceError);
        }
    }

    /**
     * Method for adding volumes to a single ExportMask.
     *
     * @param vplexURI
     * @param exportGroupURI
     * @param exportMaskURI
     * @param volumeMap
     * @param opId
     * @throws ControllerException
     */
    public void storageViewAddVolumes(URI vplexURI, URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, String opId) throws ControllerException {
        String volListStr = "";
        ExportMaskAddVolumeCompleter completer = null;

        try {
            WorkflowStepCompleter.stepExecuting(opId);

            completer = new ExportMaskAddVolumeCompleter(exportGroupURI, exportMaskURI, volumeMap, opId);

            ExportOperationContext context = new VplexExportOperationContext();
            // Prime the context object
            completer.updateWorkflowStepContext(context);

            volListStr = Joiner.on(',').join(volumeMap.keySet());
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            ExportMask exportMask = getDataObject(ExportMask.class, exportMaskURI, _dbClient);

            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_001);

            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
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
                    failStep(completer, opId, serviceError);

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
        } catch (VPlexApiException vae) {
            String message = String.format("Failed to add Volumes %s to ExportMask %s",
                    volListStr, exportMaskURI);
            _log.error(message, vae);
            failStep(completer, opId, vae);
        } catch (Exception ex) {
            String message = String.format("Failed to add Volumes %s to ExportMask %s",
                    volListStr, exportMaskURI);
            _log.error(message, ex);
            String opName = ResourceOperationTypeEnum.ADD_EXPORT_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.exportGroupAddVolumesFailed(
                    volListStr, exportGroupURI.toString(), opName, ex);
            failStep(completer, opId, serviceError);
        }
    }

    /**
     * Method declaration method for storage view add volume rollback.
     * 
     * @param vplexURI
     *            vplex
     * @param exportGroupURI
     *            export group
     * @param exportMaskURI
     *            export mask
     * @param volumeURIs
     *            volume list
     * @param rollbackContextKey
     *            context key for rollback processing
     * @return method
     */
    private Workflow.Method storageViewAddVolumesRollbackMethod(URI vplexURI, URI exportGroupURI, 
            URI exportMaskURI, List<URI> volumeURIs, String rollbackContextKey) {
        return new Workflow.Method(STORAGE_VIEW_ADD_VOLS_ROLLBACK_METHOD, vplexURI, 
                exportGroupURI, exportMaskURI, volumeURIs, rollbackContextKey);
    }

    /**
     * Rollback entry point. This is a wrapper around the exportRemoveVolumes
     * operation, which requires that we create a specific completer using the token
     * that's passed in. This token is generated by the rollback processing.
     *
     * @param vplexURI
     *            [in] - StorageSystem URI
     * @param exportGroupURI
     *            [in] - ExportGroup URI
     * @param exportMaskURI
     *            [in] - ExportMask URI
     * @param volumeURIs
     *            [in] - Impacted volume URIs
     * @param initiatorURIs
     *            [in] - List of Initiator URIs
     * @param rollbackContextKey
     *            [in] - context token
     * @param token
     *            [in] - String token generated by the rollback processing
     * @throws ControllerException
     */
    public void storageViewAddVolumesRollback(URI vplexURI, URI exportGroupURI,
            URI exportMaskURI, List<URI> volumeURIs,
            String rollbackContextKey, String token) throws ControllerException {
        TaskCompleter taskCompleter = new ExportMaskRemoveVolumeCompleter(exportGroupURI, exportMaskURI,
                volumeURIs, token);
        // Take the context of the step in flight and feed it into our current step
        // in order to only perform rollback of operations we successfully performed.
        try {
            ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(rollbackContextKey);
            WorkflowService.getInstance().storeStepData(token, context);
            storageViewRemoveVolumes(vplexURI, exportGroupURI, exportMaskURI, volumeURIs, rollbackContextKey, taskCompleter, rollbackContextKey, token);
        } catch (Exception e) {
            String message = String.format("Failed to remove Volume(s) %s on rollback from ExportGroup %s",
                    Joiner.on(",").join(volumeURIs), exportGroupURI);
            _log.error(message, e);
            String opName = ResourceOperationTypeEnum.DELETE_EXPORT_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.exportGroupRemoveVolumesFailed(
                    Joiner.on(",").join(volumeURIs), exportGroupURI.toString(), opName, e);
            taskCompleter.error(_dbClient, serviceError);
        }
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
            validator.volumeURIs(volumeURIs, false, false, ValCk.ID);
            Workflow workflow = _workflowService.getNewWorkflow(this, EXPORT_GROUP_REMOVE_VOLUMES, false, opId);
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            ExportGroup exportGroup = getDataObject(ExportGroup.class, exportURI, _dbClient);
            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, vplex.getId());

            StringBuffer errorMessages = new StringBuffer();
            boolean isValidationNeeded = validatorConfig.isValidationEnabled();
            _log.info("Orchestration level validation needed : {}", isValidationNeeded);

            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

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
                        volumeURIList = getVolumeListDiff(exportGroup, exportMask, otherExportGroups, volumeURIs);
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
                    Workflow.Method storageViewRemoveVolume = storageViewRemoveVolumesMethod(vplex.getId(),
                            exportGroup.getId(), exportMask.getId(), volumeURIList, opId, completer, null);
                    previousStep = workflow.createStep("removeVolumes",
                            String.format("Removing volumes from export on storage array %s (%s) for export mask %s (%s)",
                                    vplex.getNativeGuid(), vplex.getId().toString(), exportMask.getMaskName(), exportMask.getId()),
                            previousStep, vplex.getId(), vplex.getSystemType(),
                            this.getClass(), storageViewRemoveVolume, rollbackMethodNullMethod(), null);

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
                    Workflow.Method deleteStorageView = deleteStorageViewMethod(vplexURI, exportURI, exportMask.getId(), false);
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
                String message = errorMessages.toString();
                if (isValidationNeeded && !message.isEmpty()) {
                    _log.error("Error Message {}", errorMessages);
                    List<Volume> volumes = _dbClient.queryObject(Volume.class, volumeURIs);
                    String volumesForDisplay = Joiner.on(", ").join(
                            Collections2.transform(volumes,
                                    CommonTransformerFunctions.fctnDataObjectToForDisplay()));
                    throw DeviceControllerException.exceptions.removeVolumesValidationError(
                            volumesForDisplay, vplex.forDisplay(), message);
                }

                workflow.executePlan(
                        completer,
                        String.format("Sucessfully removed volumes or deleted Storage View: %s (%s)", exportGroup.getLabel(),
                                exportGroup.getId()));
            } else {
                completer.ready(_dbClient);
            }
        } catch (VPlexApiException vae) {

            String message = String.format("Failed to remove Volume %s from ExportGroup %s",
                    volListStr, exportURI);
            _log.error(message, vae);
            failStep(completer, opId, vae);
        } catch (Exception ex) {
            String message = String.format("Failed to remove Volume %s from ExportGroup %s",
                    volListStr, exportURI);
            _log.error(message, ex);
            String opName = ResourceOperationTypeEnum.DELETE_EXPORT_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.exportGroupRemoveVolumesFailed(
                    volListStr, exportURI.toString(), opName, ex);
            failStep(completer, opId, serviceError);
        }
    }

    /**
     * @param client
     *            -- VPlexApiClient used for communication
     * @param exportGroupURI
     *            -- Export Group
     * @param exportMaskURI
     *            -- ExportMask corresponding to the StorageView
     * @param volumeURIList
     *            -- URI of virtual volumes
     * @param parentStepId
     *            -- the parent step id
     * @param taskCompleter
     *            -- the task completer, used to find the rollback context,
     *               which will be non-null in the case of rollback
     * @param rollbackContextKey
     *            context key for rollback processing
     * @return
     */
    public Workflow.Method storageViewRemoveVolumesMethod(URI vplexURI, URI exportGroupURI, 
            URI exportMaskURI, List<URI> volumeURIList, String parentStepId,
            TaskCompleter taskCompleter, String rollbackContextKey) {
        return new Workflow.Method("storageViewRemoveVolumes", vplexURI, exportGroupURI, exportMaskURI,
                volumeURIList, parentStepId, taskCompleter, rollbackContextKey);
    }

    /**
     * @param client
     *            -- VPlexApiClient used for communication
     * @param exportGroupURI
     *            -- Export Group
     * @param exportMaskURI
     *            -- ExportMask corresponding to the StorageView
     * @param volumeURIList
     *            -- URI of virtual volumes
     * @param parentStepId
     *            -- Workflow parent step id
     * @param taskCompleter
     *            -- the task completer, used to find the rollback context,
     *               which will be non-null in the case of rollback
     * @param rollbackContextKey
     *            context key for rollback processing
     * @param stepId
     *            -- Workflow step id
     * @throws WorkflowException
     */
    public void storageViewRemoveVolumes(URI vplexURI, URI exportGroupURI, URI exportMaskURI,
            List<URI> volumeURIList, String parentStepId, TaskCompleter taskCompleter, String rollbackContextKey, String stepId)
                    throws WorkflowException {
        ExportMaskRemoveVolumeCompleter completer = null;
        ExportGroup exportGroup = null;
        ExportMask exportMask = null;
        
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            List<URI> volumeIdsToProcess = new ArrayList<>(volumeURIList);
            exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            
            completer = new ExportMaskRemoveVolumeCompleter(exportGroup.getId(), exportMask.getId(), volumeURIList, stepId);
            
            // get the context from the task completer, in case this is a rollback.
            if (taskCompleter != null && rollbackContextKey != null) {
                ExportOperationContext context = 
                        (ExportOperationContext) WorkflowService.getInstance().loadStepData(rollbackContextKey);
                if (context != null) {
                    // a non-null context means this step is running as part of a rollback.
                    List<URI> addedVolumes = new ArrayList<>();
                    if (context.getOperations() != null) {
                        _log.info("Handling removeVolumes as a result of rollback");
                        ListIterator<ExportOperationContextOperation> li = context.getOperations()
                                .listIterator(context.getOperations().size());
                        while (li.hasPrevious()) {
                            ExportOperationContextOperation operation = (ExportOperationContextOperation) li.previous();
                            if (operation != null
                                    && VplexExportOperationContext.OPERATION_ADD_VOLUMES_TO_STORAGE_VIEW.equals(operation.getOperation())) {
                                addedVolumes = (List<URI>) operation.getArgs().get(0);
                                _log.info("Removing volumes {} as part of rollback", Joiner.on(',').join(addedVolumes));
                            }
                        }

                        if (addedVolumes == null || addedVolumes.isEmpty()) {
                            _log.info("There was no context found for add volumes. So there is nothing to rollback.");
                            completer.ready(_dbClient);
                            return;
                        }
                    }
                    // change the list of initiators to process to the 
                    // list that successfully were added during addInitiators.
                    volumeIdsToProcess.clear();
                    volumeIdsToProcess.addAll(addedVolumes);
                }
            }

            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

            // Removes the specified volumes from the storage view
            // and updates the export mask.
            removeVolumesFromStorageViewAndMask(client, exportMask, volumeIdsToProcess, parentStepId);

            completer.ready(_dbClient);
        } catch (VPlexApiException vae) {
            _log.error("Exception removing volumes from Storage View: " + vae.getMessage(), vae);
            failStep(completer, stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception removing volumes from Storage View: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.REMOVE_STORAGE_VIEW_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.storageViewRemoveVolumeFailed(exportMask != null ? exportMask.getMaskName() : "none", 
                    opName, ex);
            failStep(completer, stepId, serviceError);
        }
    }

    /**
     * Remove the specified volumes from the VPlex Storage View.
     * If that is successful, remove the volumes from the ExportMask and persist it.
     *
     * @param client
     *            -- VPlexApiClient used for communication
     * @param exportMask
     *            -- ExportMask corresponding to the StorageView
     * @param volumeURIList
     *            -- URI of virtual volumes
     * @param parentStepId
     *            -- the parent step id
     * @throws Exception
     */
    private void removeVolumesFromStorageViewAndMask(
            VPlexApiClient client, ExportMask exportMask, List<URI> volumeURIList, String parentStepId) throws Exception {

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

        StorageSystem vplex = _dbClient.queryObject(StorageSystem.class, exportMask.getStorageDevice());
        ExportMaskValidationContext ctx = new ExportMaskValidationContext();
        ctx.setStorage(vplex);
        ctx.setExportMask(exportMask);
        ctx.setInitiators(initiators);
        ctx.setAllowExceptions(!WorkflowService.getInstance().isStepInRollbackState(parentStepId));
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
            Workflow workflow = _workflowService.getNewWorkflow(this, "exportAddInitiator", true, opId);
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
                        hostInitiatorURIs, varrayVolumeMap, true, workflow, previousStepId, opId);
            }
        } else {
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
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
            
            // Build a list of StoragePort targets to remove during rollback. Do not remove existing storage ports during rollback.
            List<URI> rollbackTargetURIs = new ArrayList<URI>();
            for (URI target : newTargetURIs) {
            	if (exportMask.getStoragePorts().contains(target.toString())) {
            		// Skip the target port if it exists in the ViPR ExportMask
            		continue;
            	}
            	
            	rollbackTargetURIs.add(target);
            }            
            
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
            Workflow.Method addToViewMethod = storageViewAddInitiatorsMethod(vplexURI, exportURI, exportMask.getId(), hostInitiatorURIs,
                    newTargetURIs, shared, addInitCompleter);

            Workflow.Method addToViewRollbackMethod = storageViewAddInitiatorsRollbackMethod(vplexURI,
                    exportURI, exportMask.getId(), hostInitiatorURIs, rollbackTargetURIs,
                    addInitStep);

            lastStepId = workflow.createStep("storageView", "Add " + message,
                    zoningStepId, vplexURI, vplex.getSystemType(), this.getClass(), 
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
     * @see storageViewAddInitiators
     * @param vplexURI
     * @param exportURI
     * @param maskURI
     * @param initiatorURIs
     * @param targetURIs
     * @param completer the ExportMaskAddInitiatorCompleter
     * @return Workflow.Method for addition to workflow.
     */
    public Workflow.Method storageViewAddInitiatorsMethod(URI vplexURI, URI exportURI, URI maskURI,
            List<URI> initiatorURIs, List<URI> targetURIs, boolean sharedExportMask, ExportMaskAddInitiatorCompleter completer) {
        return new Workflow.Method(STORAGE_VIEW_ADD_INITS_METHOD, vplexURI, exportURI, maskURI, initiatorURIs, targetURIs, sharedExportMask,
                completer);
    }

    /**
     * Workflow Step to add initiator to Storage View.
     * Note arguments (except stepId) must match storageViewAddInitiatorsMethod above.
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
     * @throws WorkflowException
     */
    public void storageViewAddInitiators(URI vplexURI, URI exportURI, URI maskURI,
            List<URI> initiatorURIs,
            List<URI> targetURIs, boolean sharedExportMask,
            ExportMaskAddInitiatorCompleter completer,
            String stepId) throws DeviceControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            ExportOperationContext context = new VplexExportOperationContext();
            // Prime the context object
            completer.updateWorkflowStepContext(context);

            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            ExportGroup exportGroup = getDataObject(ExportGroup.class, exportURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

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
                List<String> initiatorPortWwns = new ArrayList<String>();
                Map<PortInfo, Initiator> portInfosToInitiatorMap = new HashMap<PortInfo, Initiator>();
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
                            getVPlexInitiatorType(initiator));
                    initiatorPortInfos.add(portInfo);
                    initiatorPortWwns.add(initiator.getInitiatorPort());
                    portInfosToInitiatorMap.put(portInfo, initiator);
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
        } catch (VPlexApiException vae) {
            _log.error("VPlexApiException adding initiator to Storage View: " + vae.getMessage(), vae);
            failStep(completer, stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception adding initiator to Storage View: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.ADD_STORAGE_VIEW_INITIATOR.getName();
            ServiceError serviceError = VPlexApiException.errors.storageViewAddInitiatorFailed(opName, ex);
            failStep(completer, stepId, serviceError);
        }
    }

    /**
     * @see storageViewAddInitiatorsRollback
     *
     * @param vplexURI
     *            [in] - StorageSystem URI
     * @param exportGroupURI
     *            [in] - ExportGroup URI
     * @param exportMaskURI
     *            [in] - ExportMask URI
     * @param initiatorURIs
     *            [in] - List of Initiator URIs
     * @param targetURIs
     *            [in] - List of storage port URIs
     * @param rollbackContextKey
     *            [in] - context token
     * @return Workflow.Method for addition to workflow.
     */
    public Workflow.Method storageViewAddInitiatorsRollbackMethod(URI vplexURI, URI exportGroupURI, URI exportMaskURI,
            List<URI> initiatorURIs, List<URI> targetURIs, String rollbackContextKey) {
        return new Workflow.Method(STORAGE_VIEW_ADD_INITS_ROLLBACK_METHOD, vplexURI, 
                exportGroupURI, exportMaskURI, initiatorURIs, targetURIs, rollbackContextKey);
    }

    /**
     * Rollback entry point. This is a wrapper around the exportRemoveInitiators
     * operation, which requires that we create a specific completer using the token
     * that's passed in. This token is generated by the rollback processing.
     *
     * @param vplexURI
     *            [in] - StorageSystem URI
     * @param exportGroupURI
     *            [in] - ExportGroup URI
     * @param exportMaskURI
     *            [in] - ExportMask URI
     * @param initiatorURIs
     *            [in] - List of Initiator URIs
     * @param targetURIs
     *            [in] - List of storage port URIs
     * @param rollbackContextKey
     *            [in] - context token
     * @param token
     *            [in] - String token generated by the rollback processing
     * @throws ControllerException
     */
    public void storageViewAddInitiatorsRollback(URI vplexURI, URI exportGroupURI,
            URI exportMaskURI, List<URI> initiatorURIs, List<URI> targetURIs,
            String rollbackContextKey, String token) throws ControllerException {
        ExportTaskCompleter taskCompleter = new ExportMaskRemoveInitiatorCompleter(exportGroupURI, exportMaskURI,
                initiatorURIs, token);
        // Take the context of the step in flight and feed it into our current step
        // in order to only perform rollback of operations we successfully performed.
        try {
            ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(rollbackContextKey);
            WorkflowService.getInstance().storeStepData(token, context);
        } catch (ClassCastException e) {
            _log.info("Step {} has stored step data other than ExportOperationContext. Exception: {}", token, e);
        }

        storageViewRemoveInitiators(vplexURI, exportGroupURI, exportMaskURI,
                initiatorURIs, targetURIs, taskCompleter, rollbackContextKey, token);
    }

    /**
     * @see storageViewAddStoragePorts
     * @param vplexURI
     *            -- URI of VPlex StorageSystem
     * @param exportURI
     *            -- ExportGroup URI
     * @param maskURI
     *            -- ExportMask URI
     * @param targetURIs
     *            -- list of targets URIs
     * @param completer the ExportMaskAddInitiatorCompleter
     * @return Workflow.Method for addition to workflow.
     */
    public Workflow.Method storageViewAddStoragePortsMethod(URI vplexURI, URI exportURI, URI maskURI,
            List<URI> targetURIs, ExportMaskAddInitiatorCompleter completer) {
        return new Workflow.Method(STORAGE_VIEW_ADD_STORAGE_PORTS_METHOD, vplexURI, exportURI, maskURI, targetURIs, completer);
    }

    /**
     * Workflow Step to add storage port(s) to Storage View.
     * Note arguments (except stepId) must match storageViewAddStoragePortsMethod above.
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
    public void storageViewAddStoragePorts(URI vplexURI, URI exportURI, URI maskURI,
            List<URI> targetURIs, ExportMaskAddInitiatorCompleter completer,
            String stepId) throws DeviceControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            ExportOperationContext context = new VplexExportOperationContext();
            // Prime the context object
            completer.updateWorkflowStepContext(context);

            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            ExportGroup exportGroup = getDataObject(ExportGroup.class, exportURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

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
                        ExportOperationContext.insertContextOperation(completer,
                                VplexExportOperationContext.OPERATION_ADD_TARGETS_TO_STORAGE_VIEW,
                                targetsAddedToStorageView);
                        // Add the targets to the database.
                        for (URI target : targetsAddedToStorageView) {
                            exportMask.addTarget(target);
                        }
                        _dbClient.updateObject(exportMask);
                    }
                }
            }

            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_083);
            completer.ready(_dbClient);
        } catch (VPlexApiException vae) {
            _log.error("VPlexApiException adding storagePorts to Storage View: " + vae.getMessage(), vae);
            failStep(completer, stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception adding storagePorts to Storage View: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.ADD_STORAGE_VIEW_STORAGEPORTS.getName();
            ServiceError serviceError = VPlexApiException.errors.storageViewAddStoragePortFailed(opName, ex);
            failStep(completer, stepId, serviceError);
        }
    }

    /**
     * @see storageViewAddStoragePortsRollback
     * @param vplexURI
     *            -- URI of VPlex StorageSystem
     * @param exportGroupURI
     *            -- ExportGroup URI
     * @param exportMaskURI
     *            -- ExportMask URI
     * @param targetURIs
     *            -- list of targets URIs
     * @param rollbackContextKey
     *            -- Context token
     * @return Workflow.Method for addition to workflow.
     */
    public Workflow.Method storageViewAddStoragePortsRollbackMethod(URI vplexURI, URI exportGroupURI, URI exportMaskURI,
            List<URI> targetURIs, String rollbackContextKey) {
        return new Workflow.Method(STORAGE_VIEW_ADD_STORAGE_PORTS_ROLLBACK_METHOD, vplexURI,
                exportGroupURI, exportMaskURI, targetURIs, rollbackContextKey);
    }

    /**
     * Rollback entry point. This is a wrapper around the storageViewRemoveStoragePorts
     * operation, which requires that we extract the export context using the roll back token
     * that's passed in and store it in the current step's context.
     * 
     * @param vplexURI
     *            -- URI of VPlex StorageSystem
     * @param exportURI
     *            -- ExportGroup URI
     * @param maskURI
     *            -- ExportMask URI
     * @param targetURIs
     *            -- list of targets URIs
     * @param rollbackContextKey
     *            -- Context token
     * @param stepId
     *            -- Workflow step id.
     * @throws DeviceControllerException
     */
    public void storageViewAddStoragePortsRollback(URI vplexURI, URI exportURI, URI maskURI,
            List<URI> targetURIs, String rollbackContextKey, String stepId) throws DeviceControllerException {
        // Take the context of the step in flight and feed it into our current step
        // in order to only perform rollback of operations we successfully performed.
        try {
            ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(rollbackContextKey);
            WorkflowService.getInstance().storeStepData(stepId, context);
        } catch (ClassCastException e) {
            _log.info("Step {} has stored step data other than ExportOperationContext. Exception: {}", stepId, e);
        }

        storageViewRemoveStoragePorts(vplexURI, exportURI, maskURI, targetURIs, rollbackContextKey, stepId);
    }


    /**
     * @see storageViewRemoveStoragePorts
     * @param vplexURI
     *            -- URI of VPlex StorageSystem
     * @param exportURI
     *            -- ExportGroup URI
     * @param maskURI
     *            -- ExportMask URI
     * @param targetURIs
     *            -- list of additional targets URIs
     * @param rollbackContextKey
     *            -- Context token
     * @return Workflow.Method for addition to workflow.
     */
    public Workflow.Method storageViewRemoveStoragePortsMethod(URI vplexURI, URI exportURI, URI maskURI,
            List<URI> targetURIs, String rollbackContextKey) {
        return new Workflow.Method("storageViewRemoveStoragePorts", vplexURI, exportURI, maskURI, targetURIs, rollbackContextKey);
    }

    /**
     * Workflow Step to remove storage ports from Storage View.
     * Note arguments (except stepId) must match storageViewRemoveStoragePortsMethod above.
     *
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
     * @param rollbackContextKey
     *            -- Context token for rollback processing
     * @param stepId
     *            -- Workflow step id.
     * @throws WorkflowException
     */
    public void storageViewRemoveStoragePorts(URI vplexURI, URI exportURI, URI maskURI,
            List<URI> targetURIs, String rollbackContextKey, String stepId) throws DeviceControllerException {
        ExportMaskRemoveInitiatorCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            completer = new ExportMaskRemoveInitiatorCompleter(exportURI, maskURI,
                    new ArrayList<URI>(), stepId);

            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, maskURI);

            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
            String vplexClusterName = VPlexUtil.getVplexClusterName(exportMask, vplexURI, client, _dbClient);
            Map<String, String> targetPortMap = VPlexControllerUtils.getTargetPortToPwwnMap(client, vplexClusterName);
            VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, exportMask.getMaskName());
            _log.info("Refreshing ExportMask {}", exportMask.getMaskName());
            VPlexControllerUtils.refreshExportMask(_dbClient, storageView, exportMask,
                    targetPortMap, _networkDeviceController);

            // get the context from the task completer, in case this is a rollback.
            if (rollbackContextKey != null) {
                ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(rollbackContextKey);
                if (context != null) {
                    // a non-null context means this step is running as part of a rollback.
                    List<URI> addedTargets = new ArrayList<>();
                    if (context.getOperations() != null) {
                        _log.info("Handling storageViewRemoveStoragePorts as a result of rollback");
                        ListIterator<ExportOperationContextOperation> li = context.getOperations()
                                .listIterator(context.getOperations().size());
                        while (li.hasPrevious()) {
                            ExportOperationContextOperation operation = (ExportOperationContextOperation) li.previous();
                            if (operation != null
                                    && VplexExportOperationContext.OPERATION_ADD_TARGETS_TO_STORAGE_VIEW
                                            .equals(operation.getOperation())) {
                                addedTargets = (List<URI>) operation.getArgs().get(0);
                                _log.info(
                                        String.format("Removing target port(s) %s from storage view %s as part of rollback",
                                                Joiner.on(',').join(addedTargets), exportMask.getMaskName()));
                            }
                        }
                    }
                    if (addedTargets == null || addedTargets.isEmpty()) {
                        _log.info("There was no context found for add target. So there is nothing to rollback.");
                        completer.ready(_dbClient);
                        return;
                    }
                    // Change the list of targets to process to the list
                    // that successfully were added during addStoragePorts.
                    targetURIs.clear();
                    targetURIs.addAll(addedTargets);
                }
            }

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

            completer.ready(_dbClient);
        } catch (VPlexApiException vae) {
            _log.error("Exception removing storage ports from Storage View: " + vae.getMessage(), vae);
            failStep(completer, stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception removing storage ports from Storage View: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.DELETE_STORAGE_VIEW_STORAGEPORTS.getName();
            ServiceError serviceError = VPlexApiException.errors.storageViewRemoveStoragePortFailed(opName, ex);
            failStep(completer, stepId, serviceError);
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
            Workflow workflow = _workflowService.getNewWorkflow(this, "exportRemoveInitiator", true, opId);
            boolean hasStep = false; // true if Workflow has a Step
            Initiator firstInitiator = _dbClient.queryObject(Initiator.class, initiatorURIs.get(0));
            StringBuffer errorMessages = new StringBuffer();
            boolean isValidationNeeded = validatorConfig.isValidationEnabled()
                    && !ExportUtils.checkIfInitiatorsForRP(Arrays.asList(firstInitiator));
            _log.info("Orchestration level validation needed : {}", isValidationNeeded);

            _log.info("starting remove initiators for export group: " + exportGroup.toString());
            _log.info("request is to remove these initiators: " + initiatorURIs);

            initiatorURIs = VPlexUtil.filterInitiatorsForVplex(_dbClient, initiatorURIs);

            // get a map of host URI to a list of Initiators in that Host
            Map<URI, List<Initiator>> hostInitiatorsMap = VPlexUtil.makeHostInitiatorsMap(initiatorURIs, _dbClient);

            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

            // Loop, processing each host separately.
            for (URI hostURI : hostInitiatorsMap.keySet()) {
                _log.info("setting up initiator removal for host " + hostURI);

                // Get the initiators (and initiator URIs) for this host
                List<Initiator> initiators = hostInitiatorsMap.get(hostURI);

                // Find the ExportMask for my host.
                List<ExportMask> exportMasks = getExportMaskForHost(exportGroup, hostURI, vplexURI);
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

                    // initiator filter logic is move inside addStepsForRemoveInitiators and addStepsForInitiatorRemoval as
                    // it is not required for zone related operation.
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
                    ctx.setAllowExceptions(!WorkflowService.getInstance().isStepInRollbackState(opId));
                    validator.removeInitiators(ctx).validate();

                    lastStep = addStepsForRemoveInitiators(
                            vplex, workflow, completer, exportGroup, exportMask, initiators,
                            hostURI, initiatorsAlreadyRemovedFromExportGroup, errorMessages, lastStep);
                    if (lastStep != null) {
                        hasStep = true;
                    }
                }
            }

            String message = errorMessages.toString();
            if (isValidationNeeded && !message.isEmpty()) {
                _log.error("Error Message {}", errorMessages);
                List<String> initiatorNames = new ArrayList<String>();
                for (URI initiatorURI : initiatorURIs) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);
                    if (initiator != null) {
                        String normalizedName = Initiator.normalizePort(initiator.getInitiatorPort());
                        initiatorNames.add(normalizedName);
                    } else {
                        _log.warn("no initiator found for URI {}", initiatorURI);
                    }
                }
                throw DeviceControllerException.exceptions.removeInitiatorValidationError(
                        Joiner.on(", ").join(initiatorNames),
                        vplex.getLabel(),
                        message);
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
            URI hostURI, List<URI> initiatorsAlreadyRemovedFromExportGroup,
            StringBuffer errorMessages, String previousStep) throws Exception {
        String lastStep = previousStep;

        // assemble a list of other ExportGroups that reference this ExportMask
        List<ExportGroup> otherExportGroups = ExportUtils.getOtherExportGroups(exportGroup, exportMask,
                _dbClient);

        _log.info(String.format("will be removing initiators %s for host %s mask %s (%s)",
                getInitiatorsWwnsString(initiators), hostURI.toString(), exportMask.getMaskName(), exportMask.getId()));

        List<URI> hostInitiatorURIs = new ArrayList<URI>();
        List<URI> removeInitiatorListURIs = new ArrayList<URI>();
        for (Initiator initiator : initiators) {
            // filter out any of the host's initiators that are not
            // contained within this ExportMask (CTRL-12300)
            if (exportMask.hasInitiator(initiator.getId().toString())) {
                hostInitiatorURIs.add(initiator.getId());
            }
            // removeInitiatorListURIs is created to pass it to next method name addStepsForInitiatorRemoval
            // we have filtering logic placed inside addStepsForInitiatorRemoval to take care of it.
            removeInitiatorListURIs.add(initiator.getId());
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
            if (!ExportUtils.exportMaskHasVolumeFromMutipleExportGroup(exportGroup, otherExportGroups, exportMask)) {

                _log.info("all initiators are being removed and no " + "other ExportGroups reference ExportMask {}",
                        exportMask.getMaskName());
                _log.info("creating a deleteStorageView workflow step for " + exportMask.getMaskName());

                String exportMaskDeleteStep = workflow.createStepId();
                Workflow.Method storageViewExecuteMethod = deleteStorageViewMethod(vplex.getId(), exportGroup.getId(), exportMask.getId(), false);
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
                // export Mask has multiple shared/exclusive volumes or volumes from different virtual array/project, removing the volumes.
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

                    TaskCompleter taskCompleter = new ExportMaskRemoveVolumeCompleter(
                            exportGroup.getId(), exportMask.getId(), volumeURIList, lastStep); 
                    Workflow.Method storageViewRemoveVolume = storageViewRemoveVolumesMethod(vplex.getId(),
                            exportGroup.getId(), exportMask.getId(), volumeURIList, lastStep, taskCompleter, null);
                    lastStep = workflow.createStep("removeVolumes",
                            String.format("Removing volumes from export on storage array %s (%s) for export mask %s (%s)",
                                    vplex.getNativeGuid(), vplex.getId().toString(), exportMask.getMaskName(), exportMask.getId()),
                            lastStep, vplex.getId(), vplex.getSystemType(),
                            this.getClass(), storageViewRemoveVolume, rollbackMethodNullMethod(), null);

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
                    exportMask, removeInitiatorListURIs, targetURIs, lastStep,
                    removeAllInits, hostURI, errorMessages);
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
            boolean removeAllInits, URI computeResourceId, StringBuffer errorMessages) {
        _log.info("these initiators are being marked for removal from export mask {}: {}",
                exportMask.getMaskName(), CommonTransformerFunctions.collectionToString(hostInitiatorURIs));

        String lastStep = workflow.createStepId();

        // filter out any of the host's initiators that are not
        // contained within this ExportMask (CTRL-12300)

        List<URI> initsToRemove = new ArrayList<URI>();
        List<URI> initsToRemoveOnlyFromZone = new ArrayList<URI>();
        for (URI init : hostInitiatorURIs) {
            if (exportMask.hasInitiator(init.toString())) {
                initsToRemove.add(init);
            } else {
                initsToRemoveOnlyFromZone.add(init);
            }
        }

        _log.info("these initiator will be only removed from zone {}",
                CommonTransformerFunctions.collectionToString(initsToRemoveOnlyFromZone));
        // removeInitiatorMethod will make sure not to remove existing initiators
        // from the storage view on the vplex device.
        Workflow.Method removeInitiatorMethod = storageViewRemoveInitiatorsMethod(vplex.getId(), exportGroup.getId(),
                exportMask.getId(), initsToRemove, targetURIs, null, null);
        Workflow.Method removeInitiatorRollbackMethod = new Workflow.Method(ROLLBACK_METHOD_NULL);
        lastStep = workflow.createStep("storageView", "Removing " + initsToRemove.toString(),
                zoneStep, vplex.getId(), vplex.getSystemType(), this.getClass(),
                removeInitiatorMethod, removeInitiatorRollbackMethod, lastStep);

        // Add zoning step for removing initiators
        Map<URI, List<URI>> exportMaskToInitiators = new HashMap<URI, List<URI>>();
        exportMaskToInitiators.put(exportMask.getId(), initsToRemove);
        List<NetworkZoningParam> zoningParam = NetworkZoningParam.convertExportMaskInitiatorMapsToNetworkZoningParam(
                exportGroup.getId(), exportMaskToInitiators, _dbClient);
        
        // we still need to delete zone if it is not part of masking view

        if (!initsToRemoveOnlyFromZone.isEmpty()) {
            NetworkZoningParam.updateZoningParamUsingFCZoneReference(zoningParam, initsToRemoveOnlyFromZone, exportGroup, _dbClient);
        }
        Workflow.Method zoneRemoveInitiatorsMethod = _networkDeviceController.zoneExportRemoveInitiatorsMethod(zoningParam);
        Workflow.Method zoneNullRollbackMethod = _networkDeviceController.zoneNullRollbackMethod();
        lastStep = workflow.createStep(null, "Zone remove initiataors mask: " + exportMask.getMaskName(),
                lastStep, nullURI, "network-system", _networkDeviceController.getClass(),
                zoneRemoveInitiatorsMethod, zoneNullRollbackMethod, null);

        return lastStep;
    }

    /**
     * Returns the workflow method for storageViewRemoveInitiators.
     *
     * @see storageViewRemoveInitiators
     * @param vplexURI
     * @param exportGroupURI
     * @param exportMaskURI
     * @param initiatorURIs
     * @param targetURIs
     * @param taskCompleter
     *            -- the task completer, used to find the rollback context,
     *               which will be non-null in the case of rollback
     * @param rollbackContextKey
     *            context key for rollback processing
     * @return Workflow.Method for addition into workflow.
     */
    private Workflow.Method storageViewRemoveInitiatorsMethod(URI vplexURI, URI exportGroupURI,
            URI exportMaskURI, List<URI> initiatorURIs, List<URI> targetURIs, TaskCompleter taskCompleter,
            String rollbackContextKey) {
        return new Workflow.Method("storageViewRemoveInitiators", vplexURI, exportGroupURI,
                exportMaskURI, initiatorURIs, targetURIs, taskCompleter, rollbackContextKey);
    }

    /**
     * Workflow step to remove an initiator from a single Storage View as given by the ExportMask URI.
     * Note there is a dependence on ExportMask name equaling the Storage View name.
     * Note that arguments must match storageViewRemoveInitiatorsMethod above (except stepId).
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
     *            -- the task completer, used to find the rollback context,
     *               which will be non-null in the case of rollback
     * @param rollbackContextKey
     *            context key for rollback processing
     * @param stepId
     *            -- Workflow step id.
     * @throws WorkflowException
     */
    public void storageViewRemoveInitiators(URI vplexURI, URI exportGroupURI, URI exportMaskURI,
            List<URI> initiatorURIs, List<URI> targetURIs, TaskCompleter taskCompleter, String rollbackContextKey, String stepId)
                    throws WorkflowException {
        ExportMaskRemoveInitiatorCompleter completer = null;

        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            List<URI> initiatorIdsToProcess = new ArrayList<>(initiatorURIs);
            completer = new ExportMaskRemoveInitiatorCompleter(exportGroupURI, exportMaskURI,
                    initiatorURIs, stepId);
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
            String vplexClusterName = VPlexUtil.getVplexClusterName(exportMask, vplexURI, client, _dbClient);
            Map<String, String> targetPortMap = VPlexControllerUtils.getTargetPortToPwwnMap(client, vplexClusterName);
            VPlexStorageViewInfo storageView = client.getStorageView(vplexClusterName, exportMask.getMaskName());
            _log.info("Refreshing ExportMask {}", exportMask.getMaskName());
            VPlexControllerUtils.refreshExportMask(_dbClient, storageView, exportMask,
                    targetPortMap, _networkDeviceController);

            // get the context from the task completer, in case this is a rollback.
            if (taskCompleter != null && rollbackContextKey != null) {
                ExportOperationContext context = 
                        (ExportOperationContext) WorkflowService.getInstance().loadStepData(rollbackContextKey);
                if (context != null) {
                    // a non-null context means this step is running as part of a rollback.
                    List<URI> addedInitiators = new ArrayList<>();
                    if (context.getOperations() != null) {
                        _log.info("Handling removeInitiators as a result of rollback");
                        ListIterator<ExportOperationContextOperation> li = context.getOperations()
                                .listIterator(context.getOperations().size());
                        while (li.hasPrevious()) {
                            ExportOperationContextOperation operation = (ExportOperationContextOperation) li.previous();
                            if (operation != null
                                    && VplexExportOperationContext.OPERATION_ADD_INITIATORS_TO_STORAGE_VIEW
                                            .equals(operation.getOperation())) {
                                addedInitiators = (List<URI>) operation.getArgs().get(0);
                                _log.info("Removing initiators {} as part of rollback", Joiner.on(',').join(addedInitiators));
                            }
                        }
                    }
                    // Update the initiators in the task completer such that we update the export mask/group correctly
                    for (URI initiator : initiatorIdsToProcess) {
                        if (addedInitiators == null || !addedInitiators.contains(initiator)) {
                            completer.removeInitiator(initiator);
                        }
                    }
                    if (addedInitiators == null || addedInitiators.isEmpty()) {
                        _log.info("There was no context found for add initiator. So there is nothing to rollback.");
                        completer.ready(_dbClient);
                        return;
                    }
                    // Change the list of initiators to process to the list 
                    // that successfully were added during addInitiators.
                    initiatorIdsToProcess.clear();
                    initiatorIdsToProcess.addAll(addedInitiators);
                }
            }

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
            for (URI initiatorURI : initiatorIdsToProcess) {
                Initiator initiator = getDataObject(Initiator.class, initiatorURI, _dbClient);
                // We don't want to remove existing initiator, unless this is a rollback step
                if (exportMask.hasExistingInitiator(initiator) && !WorkflowService.getInstance().isStepInRollbackState(stepId)) {
                    continue;
                }
                PortInfo portInfo = new PortInfo(initiator.getInitiatorPort()
                        .toUpperCase().replaceAll(":", ""), initiator.getInitiatorNode()
                                .toUpperCase().replaceAll(":", ""),
                        initiator.getLabel(),
                        getVPlexInitiatorType(initiator));
                initiatorPortInfo.add(portInfo);
            }

            // Remove the initiators if there aren't any existing volumes, unless this is a rollback step or validation is disabled.
            if (!initiatorPortInfo.isEmpty() && (!exportMask.hasAnyExistingVolumes() || !validatorConfig.isValidationEnabled() ||
                    WorkflowService.getInstance().isStepInRollbackState(stepId))) {
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

            completer.ready(_dbClient);
        } catch (VPlexApiException vae) {
            _log.error("Exception removing initiator from Storage View: " + vae.getMessage(), vae);
            failStep(completer, stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception removing initiator from Storage View: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.DELETE_STORAGE_VIEW_INITIATOR.getName();
            ServiceError serviceError = VPlexApiException.errors.storageViewRemoveInitiatorFailed(opName, ex);
            failStep(completer, stepId, serviceError);
        }
    }

    /**
     * Finds the next available HLU for cluster export by querying the cluster's hosts'
     * used HLUs and updates the volumeHLU map with free HLUs.
     */
    @Override
    public void findAndUpdateFreeHLUsForClusterExport(StorageSystem storage, ExportGroup exportGroup, List<URI> initiatorURIs,
            Map<URI, Integer> volumeMap) {
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
        List<String> initiatorNames = getInitiatorNames(vplexStorageSystem.getSerialNumber(), vplexClusterName, client, inits, doRefresh);

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
            List<String> haInitiatorNames = getInitiatorNames(vplexStorageSystem.getSerialNumber(), vplexHAClusterName, client, inits, doRefreshHa);
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

    /**
     * Get the registered initiator names for the given initiators on the given VPLEX cluster.
     * 
     * If the registered initiator name cannot be found in the Initiator.initiatorNames map in
     * the database, then the VPLEX API will be checked and the Initiator object will be updated
     * with the value from the VPLEX API, if found.  If the name is still not found, that means 
     * it's unregistered and no name for the initiator will be returned in the list.
     * 
     * This will also update the Initiator.initiatorMap if the registered name has been
     * manually changed on the VPLEX since the last system discovery (but cache has refreshed).
     * 
     * The auto discovery process would update it when it refreshes all Initiators 
     * in the database (see VPlexCommunicationInterface.updateHostInitiators).
     * 
     * @param vplexSerialNumber the VPLEX system serial number
     * @param vplexClusterName the VPLEX cluster name to look at
     * @param client the VPLEX api client
     * @param initiators an iterator of Initiator objects to check
     * @param doRefresh an out flag to indicate whether refresh has been done 
     *                  (will be updated to false after first call to getInitiatorNameForWwn)
     * @return a List of initiator names registered on the VPLEX for the initiators given (any
     *              initiators that are not currently registered will not be returned in the list).
     */
    private List<String> getInitiatorNames(String vplexSerialNumber, String vplexClusterName, VPlexApiClient client, 
            Iterator<Initiator> initiators, Boolean[] doRefresh) {
        List<String> initiatorNames = new ArrayList<String>();
        List<Initiator> initsToUpdate = new ArrayList<Initiator>();
        // this is the key to look up the name saved in the Initiator.initiatorNames map
        String initiatorNameMapKey = vplexSerialNumber + VPlexApiConstants.INITIATOR_CLUSTER_NAME_DELIM + vplexClusterName;
        while (initiators.hasNext()) {
            Initiator initiator = initiators.next();
            String portWwn = initiator.getInitiatorPort();
            String viprInitiatorName = initiator.getInitiatorNames().get(initiatorNameMapKey);
            String vplexInitiatorName = client.getInitiatorNameForWwn(
                    vplexClusterName, WWNUtility.getUpperWWNWithNoColons(portWwn), doRefresh);
            // if the initiator name couldn't be found in the Initiator.initiatorNames map, use the one from the VPLEX API
            if (viprInitiatorName == null || !viprInitiatorName.equals(vplexInitiatorName)) {
                if (null != vplexInitiatorName) {
                    // if a new initiator name was found, update the Initiator map.
                    _log.info("mapping new initiator name {} to storage system key {}", vplexInitiatorName, initiatorNameMapKey);
                    initiator.mapInitiatorName(initiatorNameMapKey, vplexInitiatorName);
                    initsToUpdate.add(initiator);
                    viprInitiatorName = vplexInitiatorName;
                }
            }
            if (viprInitiatorName != null && !viprInitiatorName.startsWith(VPlexApiConstants.UNREGISTERED_INITIATOR_PREFIX)) {
                initiatorNames.add(viprInitiatorName);
            }
        }

        _dbClient.updateObject(initsToUpdate);
        _log.info("initiator names are " + initiatorNames);
        return initiatorNames;
    }

    public void setVplexApiFactory(VPlexApiFactory _vplexApiFactory) {
        this._vplexApiFactory = _vplexApiFactory;
    }

    public void setBlockDeviceController(BlockDeviceController _blockDeviceController) {
        this._blockDeviceController = _blockDeviceController;
    }

    public void setRpDeviceController(RPDeviceController rpDeviceController) {
        _rpDeviceController = rpDeviceController;
    }

    public void setBlockOrchestrationDeviceController(BlockOrchestrationDeviceController blockOrchestrationController) {
        _blockOrchestrationController = blockOrchestrationController;
    }

    /**
     * Create a descriptor for the passed volume.
     *
     * @param storagePoolURI
     *            URI of the storage pool.
     * @param volumeURI
     *            URI of the volume.
     * @param storageSystemMap
     *            An OUT parameters specifying the list of storage
     *            systems on which volumes are created.
     * @param volumeMap
     *            An OUT parameter specifying the full list volumes to be
     *            created.
     *
     * @return The descriptor for the pool volume.
     *
     * @throws IOException
     *             When an error occurs.
     * @throws WorkflowException
     */
    private VolumeDescriptor createDescriptorForBlockVolumeCreation(URI storagePoolURI,
            URI volumeURI, Map<URI, StorageSystem> storageSystemMap,
            Map<URI, Volume> volumeMap) throws IOException, WorkflowException {

        // Get the storage pool to hold the backend block volumes.
        StoragePool storagePool = _dbClient
                .queryObject(StoragePool.class, storagePoolURI);

        // Get the storage system for this pool. Check the map
        // as we cache the storage systems in the passed map, so
        // we don't retrieve them multiple times from the database.
        URI storageSystemURI = storagePool.getStorageDevice();
        StorageSystem storageSystem = null;
        if (storageSystemMap.containsKey(storageSystemURI)) {
            storageSystem = storageSystemMap.get(storageSystemURI);
        } else {
            storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemURI);
            storageSystemMap.put(storageSystemURI, storageSystem);
        }

        // Get the volume.
        Volume volume = _dbClient.queryObject(Volume.class, volumeURI);

        // Cache the volumes, like the storage systems, in the
        // passed volume map so that we don't access the database
        // multiple time.
        volumeMap.put(volumeURI, volume);

        // Create a descriptor for the volume.
        URI cgURI = null;
        if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
            cgURI = volume.getConsistencyGroup();
        }
        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, volume.getCapacity());
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT,
                new Integer(1));
        return new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA, storageSystemURI,
                volumeURI, storagePoolURI, cgURI, capabilities);
    }

    /**
     * Create workflow steps in the passed workflow to create export groups for the
     * front end ports of the passed storage systems to the backend ports of the
     * passed VPlex storage system for the purpose of exposing volumes on these
     * storage system to the VPlex.
     *
     * @param workflow
     *            The workflow to which the export steps are added.
     * @param vplexSystem
     *            A reference to the VPlex storage system.
     * @param storageSystemMap
     *            The list of storage systems to export.
     * @param volumeMap
     *            The volumes to be exported.
     * @param projectURI
     *            The project reference.
     * @param tenantURI
     *            The tenant reference.
     * @param dependantStepId
     *            The dependent step if, typically a volume creation step.
     *
     * @throws IOException
     *             When an error occurs.
     * @throws ControllerException
     */
    private String createWorkflowStepsForBlockVolumeExport(Workflow workflow,
            StorageSystem vplexSystem, Map<URI, StorageSystem> storageSystemMap,
            Map<URI, Volume> volumeMap, URI projectURI, URI tenantURI, String dependantStepId)
                    throws IOException, ControllerException {

        String lastStep = dependantStepId;
        // The following adds a rollback step to forget the volumes if something
        // goes wrong. This is a nop on provisioning.
        lastStep = addRollbackStepToForgetVolumes(workflow, vplexSystem.getId(),
                new ArrayList<URI>(volumeMap.keySet()), lastStep);
        String forgetRBStep = lastStep;

        URI vplexURI = vplexSystem.getId();
        // Main processing containers. ExportGroup --> StorageSystem --> Volumes
        // Populate the container for the export workflow step generation
        for (Map.Entry<URI, StorageSystem> storageEntry : storageSystemMap.entrySet()) {
            URI storageSystemURI = storageEntry.getKey();
            StorageSystem storageSystem = storageEntry.getValue();
            URI varray = getVolumesVarray(storageSystem, volumeMap.values());
            _log.info(String.format("Creating ExportGroup for storage system %s (%s) in Virtual Aarray[(%s)]",
                    storageSystem.getLabel(), storageSystemURI, varray));

            if (varray == null) {
                // For whatever reason, there were no Volumes for this Storage System found, so we
                // definitely do not want to create anything. Log a warning and continue.
                _log.warn(String.format("No Volumes for storage system %s (%s), no need to create an ExportGroup.",
                        storageSystem.getLabel(), storageSystemURI));
                continue;
            }

            // Get the initiator port map for this VPLEX and storage system
            // for this volumes virtual array.
            VPlexBackendManager backendMgr = new VPlexBackendManager(_dbClient, this, _blockDeviceController,
                    _blockScheduler, _networkDeviceController, projectURI, tenantURI, _vplexApiLockManager, coordinator);
            Map<URI, List<StoragePort>> initiatorPortMap = backendMgr.getInitiatorPortsForArray(
                    vplexURI, storageSystemURI, varray);

            // If there are no networks that can be zoned, error.
            if (initiatorPortMap.isEmpty()) {
                throw DeviceControllerException.exceptions
                        .noNetworksConnectingVPlexToArray(vplexSystem.getNativeGuid(),
                                storageSystem.getNativeGuid());
            }

            // Select from an existing ExportMask if possible
            ExportMaskPlacementDescriptor descriptor = backendMgr.chooseBackendExportMask(vplexSystem, storageSystem, varray, volumeMap,
                    lastStep);

            // refresh export masks per XIO storage array
            boolean needRefresh = storageSystem.deviceIsType(DiscoveredDataObject.Type.xtremio);
            // For every ExportMask in the descriptor ...
            for (URI exportMaskURI : descriptor.getPlacedMasks()) {
                // Create steps to place each set of volumes into its assigned ExportMask
                ExportGroup exportGroup = descriptor.getExportGroupForMask(exportMaskURI);
                ExportMask exportMask = descriptor.getExportMask(exportMaskURI);
                Map<URI, Volume> placedVolumes = descriptor.getPlacedVolumes(exportMaskURI);

                if (needRefresh) {
                    BlockStorageDevice device = _blockDeviceController.getDevice(storageSystem.getSystemType());
                    device.refreshExportMask(storageSystem, exportMask);
                    needRefresh = false;;
                }

                // Add the workflow steps.
                lastStep = backendMgr.addWorkflowStepsToAddBackendVolumes(workflow, lastStep, exportGroup, exportMask, placedVolumes,
                        varray, vplexSystem, storageSystem, forgetRBStep);
            }
        }

        return lastStep;
    }

    /**
     * Return a list of ExportGroups for a BlockObject on an array.
     * This is used by deleteVolumes to find the ExportGroup(s) on the underlying array.
     *
     * @param volume
     *            BlockObject - Volume
     * @return List<ExportGroup>
     * @throws Exception
     */
    private List<ExportGroup> getExportGroupsForVolume(BlockObject volume) throws Exception {
        URIQueryResultList exportGroupURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getBlockObjectExportGroupConstraint(volume.getId()), exportGroupURIs);
        List<ExportGroup> exportGroups = new ArrayList<ExportGroup>();
        for (URI egURI : exportGroupURIs) {
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, egURI);
            if (exportGroup == null || exportGroup.getInactive() == true) {
                continue;
            }
            exportGroups.add(exportGroup);
        }
        return exportGroups;
    }

    /**
     * Gets the end for to communicate with the passed VPlex.
     *
     * @param vplex
     *            The VPlex storage system.
     *
     * @return The URI for the VPlex management server.
     * @throws URISyntaxException
     */
    public URI getCommunicationEndpoint(StorageSystem vplex) throws URISyntaxException {
        URI endpoint = new URI("https", null, vplex.getIpAddress(), vplex.getPortNumber(), "/", null, null);
        return endpoint;
    }

    private Workflow.Method rollbackMethodNullMethod() {
        return new Workflow.Method(ROLLBACK_METHOD_NULL);
    }

    public void rollbackMethodNull(String stepId) throws WorkflowException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }

    /**
     * Deprecating this for now, should be using the migrateVolumes call with the WF passed in from
     * the BlockOrchestrator.
     *
     * {@inheritDoc}
     */
    @Override
    public void migrateVolumes(URI vplexURI, URI virtualVolumeURI,
            List<URI> targetVolumeURIs, Map<URI, URI> migrationsMap,
            Map<URI, URI> poolVolumeMap, URI newCoSURI, URI newNhURI, String successMsg,
            String failMsg, OperationTypeEnum opType, String opId, String wfStepId) throws ControllerException {
        List<URI> migrationURIs = new ArrayList<URI>(migrationsMap.values());
        try {
            _log.info("VPlex controller migrate volume {} on VPlex {}",
                    virtualVolumeURI, vplexURI);

            // Get the VPlex storage system
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            _log.info("Got VPlex system");

            // If a workflow step id is passed, then this is being called
            // from a step in a "parent" workflow. In that case, this
            // sub-workflow takes the name of the step.
            String wfId = (wfStepId != null ? wfStepId : opId);

            // Get a new workflow to execute the migrations.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    MIGRATE_VOLUMES_WF_NAME, false, wfId);
            _log.info("Created new workflow with operation id {}", wfId);

            // Create a step to validate the volume and prevent migration if the
            // the ViPR DB does not properly reflect the actual backend volumes.
            // A successful migration will delete the backend source volumes. If
            // the ViPR DB does not correctly reflect the actual backend volume,
            // we could delete a backend volume used by some other VPLEX volume.
            String waitFor = createWorkflowStepToValidateVPlexVolume(workflow, vplexSystem, virtualVolumeURI, null);

            // We first need to create steps in the workflow to create the new
            // backend volume(s) to which the data for the virtual volume will
            // be migrated.
            List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();
            Map<URI, StorageSystem> storageSystemMap = new HashMap<URI, StorageSystem>();
            Map<URI, Volume> volumeMap = new HashMap<URI, Volume>();
            Iterator<URI> storagePoolIter = poolVolumeMap.keySet().iterator();
            while (storagePoolIter.hasNext()) {
                URI storagePoolURI = storagePoolIter.next();
                URI volumeURI = poolVolumeMap.get(storagePoolURI);
                _log.info("Creating descriptor for volume{} in pool {}", volumeURI, storagePoolURI);
                descriptors.add(createDescriptorForBlockVolumeCreation(storagePoolURI,
                        volumeURI, storageSystemMap, volumeMap));
                _log.info("Created descriptor for volume");
            }

            // Add steps in the block device controller to create the target
            // volumes.
            waitFor = _blockDeviceController.addStepsForCreateVolumes(workflow,
                    waitFor, descriptors, wfId);

            // Set the project and tenant. We prefer a project created for the Vplex system,
            // but will fallback to the volume's project if there isn't a project for the VPlex.
            Volume firstVolume = volumeMap.values().iterator().next();
            Project vplexProject = VPlexUtil.lookupVplexProject(firstVolume, vplexSystem, _dbClient);
            URI tenantURI = vplexProject.getTenantOrg().getURI();
            _log.info("Project is {}, Tenant is {}", vplexProject.getId(), tenantURI);

            // Now we need to do the necessary zoning and export steps to ensure
            // the VPlex can see these new backend volumes.
            createWorkflowStepsForBlockVolumeExport(workflow, vplexSystem,
                    storageSystemMap, volumeMap, vplexProject.getId(), tenantURI, waitFor);
            _log.info("Created workflow steps for volume export.");

            // Now make a migration Step for each passed target to which data
            // for the passed virtual volume will be migrated. The migrations
            // will be done from this controller.
            Iterator<URI> targetVolumeIter = targetVolumeURIs.iterator();
            while (targetVolumeIter.hasNext()) {
                URI targetVolumeURI = targetVolumeIter.next();
                _log.info("Target volume is {}", targetVolumeURI);
                URI migrationURI = migrationsMap.get(targetVolumeURI);
                _log.info("Migration is {}", migrationURI);
                String stepId = workflow.createStepId();
                _log.info("Migration opId is {}", stepId);
                Workflow.Method vplexExecuteMethod = new Workflow.Method(
                        MIGRATE_VIRTUAL_VOLUME_METHOD_NAME, vplexURI, virtualVolumeURI,
                        targetVolumeURI, migrationURI, newNhURI);
                Workflow.Method vplexRollbackMethod = new Workflow.Method(
                        RB_MIGRATE_VIRTUAL_VOLUME_METHOD_NAME, vplexURI, migrationURI, stepId);
                _log.info("Creating workflow migration step");
                workflow.createStep(MIGRATION_CREATE_STEP, String.format(
                        "VPlex %s migrating to target volume %s.", vplexSystem.getId().toString(), 
                        targetVolumeURI.toString()),
                        EXPORT_STEP, vplexSystem.getId(), vplexSystem.getSystemType(),
                        getClass(), vplexExecuteMethod, vplexRollbackMethod, stepId);
                _log.info("Created workflow migration step");
            }

            // Once the migrations complete, we will commit the migrations.
            // So, now we create the steps to commit the migrations.
            String waitForStep = MIGRATION_CREATE_STEP;
            List<URI> migrationSources = new ArrayList<URI>();
            Iterator<URI> migrationsIter = migrationsMap.values().iterator();
            while (migrationsIter.hasNext()) {
                URI migrationURI = migrationsIter.next();
                _log.info("Migration is {}", migrationURI);
                Migration migration = getDataObject(Migration.class, migrationURI, _dbClient);
                // The migration source volume may be null for ingested volumes
                // for which we do not know anything about the backend volumes.
                // If we don't know the source, we know we are migrating an
                // ingested volume and we will not want to do any renaming
                // after the commit as we do when migration ViPR create volumes,
                // which adhere to a standard naming convention.
                Boolean rename = Boolean.TRUE;
                if (migration.getSource() != null) {
                    migrationSources.add(migration.getSource());
                } else {
                    rename = Boolean.FALSE;
                }
                _log.info("Added migration source {}", migration.getSource());
                String stepId = workflow.createStepId();
                _log.info("Commit operation id is {}", stepId);
                Workflow.Method vplexExecuteMethod = new Workflow.Method(
                        COMMIT_MIGRATION_METHOD_NAME, vplexURI, virtualVolumeURI,
                        migrationURI, rename, newCoSURI, newNhURI);
                Workflow.Method vplexRollbackMethod = new Workflow.Method(
                        RB_COMMIT_MIGRATION_METHOD_NAME, migrationURIs, newCoSURI, newNhURI, stepId);
                _log.info("Creating workflow step to commit migration");
                waitForStep = workflow.createStep(
                        MIGRATION_COMMIT_STEP,
                        String.format("VPlex %s committing volume migration",
                                vplexSystem.getId().toString()),
                        waitForStep, vplexSystem.getId(),
                        vplexSystem.getSystemType(), getClass(), vplexExecuteMethod,
                        vplexRollbackMethod, stepId);
                _log.info("Created workflow step to commit migration");
            }

            // Create a step that creates a sub workflow to delete the old
            // migration source volumes, which are no longer used by the
            // virtual volume. We also update the virtual volume CoS. If
            // we make it to this step, then all migrations were committed.
            // We do this in a sub workflow because we don't won't to
            // initiate rollback regardless of success or failure.
            String stepId = workflow.createStepId();
            Workflow.Method vplexExecuteMethod = new Workflow.Method(
                    DELETE_MIGRATION_SOURCES_METHOD, vplexURI, virtualVolumeURI,
                    newCoSURI, newNhURI, migrationSources);
            workflow.createStep(DELETE_MIGRATION_SOURCES_STEP,
                    String.format("Creating workflow to delete migration sources"),
                    MIGRATION_COMMIT_STEP, vplexSystem.getId(), vplexSystem.getSystemType(),
                    getClass(), vplexExecuteMethod, null, stepId);
            _log.info("Created workflow step to create sub workflow for source deletion");

            // Finish up and execute the plan. The Workflow will handle the
            // TaskCompleter
            List<URI> volumes = new ArrayList<URI>();
            volumes.add(virtualVolumeURI);
            volumes.addAll(targetVolumeURIs);
            TaskCompleter completer = new MigrationWorkflowCompleter(volumes,
                    migrationURIs, opId, wfStepId);
            _log.info("Executing workflow plan");
            workflow.executePlan(completer, successMsg);
            _log.info("Workflow plan executed");
        } catch (Exception e) {
            _log.error(failMsg, e);
            List<URI> volumes = new ArrayList<URI>();
            volumes.add(virtualVolumeURI);
            volumes.addAll(targetVolumeURIs);
            TaskCompleter completer = new MigrationWorkflowCompleter(volumes,
                    migrationURIs, opId, wfStepId);
            ServiceError serviceError = VPlexApiException.errors.jobFailed(e);
            serviceError.setMessage(failMsg);
            failStep(completer, opId, serviceError);
        }
    }

    /**
     * Adds steps in the passed workflow to migrate a volume.
     *
     * @param workflow
     * @param vplexURI
     * @param virtualVolumeURI
     * @param targetVolumeURIs
     * @param migrationsMap
     * @param poolVolumeMap
     * @param newVpoolURI
     * @param newVarrayURI
     * @param suspendBeforeCommit
     * @param suspendBeforeDeleteSource
     * @param opId
     * @param waitFor
     * @return
     * @throws InternalException
     */
    public String addStepsForMigrateVolumes(Workflow workflow, URI vplexURI, URI virtualVolumeURI,
            List<URI> targetVolumeURIs, Map<URI, URI> migrationsMap,
            Map<URI, URI> poolVolumeMap, URI newVpoolURI, URI newVarrayURI, boolean suspendBeforeCommit, boolean suspendBeforeDeleteSource,
            String opId, String waitFor)
                    throws InternalException {
        try {
            _log.info("VPlex controller migrate volume {} on VPlex {}",
                    virtualVolumeURI, vplexURI);

            String volumeUserLabel = "Label Unknown";
            Volume virtualVolume = getDataObject(Volume.class, virtualVolumeURI, _dbClient);
            if (virtualVolume != null && virtualVolume.getDeviceLabel() != null && virtualVolume.getLabel() != null) {
                volumeUserLabel = virtualVolume.getLabel() + " (" + virtualVolume.getDeviceLabel() + ")";
            }

            // Get the VPlex storage system
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            _log.info("Got VPlex system");

            // Create a step to validate the volume and prevent migration if the
            // the ViPR DB does not properly reflect the actual backend volumes.
            // A successful migration will delete the backend source volumes. If
            // the ViPR DB does not correctly reflect the actual backend volume,
            // we could delete a backend volume used by some other VPLEX volume.
            waitFor = createWorkflowStepToValidateVPlexVolume(workflow, vplexSystem, virtualVolumeURI, waitFor);

            Map<URI, Volume> volumeMap = new HashMap<URI, Volume>();
            Map<URI, StorageSystem> storageSystemMap = new HashMap<URI, StorageSystem>();
            for (URI volumeURI : targetVolumeURIs) {
                Volume volume = getDataObject(Volume.class, volumeURI, _dbClient);
                volumeMap.put(volumeURI, volume);
                StorageSystem storageSystem = getDataObject(StorageSystem.class, volume.getStorageController(), _dbClient);
                storageSystemMap.put(volume.getStorageController(), storageSystem);
            }

            // Set the project and tenant.
            Volume firstVolume = volumeMap.values().iterator().next();
            Project vplexProject = VPlexUtil.lookupVplexProject(firstVolume, vplexSystem, _dbClient);
            URI tenantURI = vplexProject.getTenantOrg().getURI();
            _log.info("Project is {}, Tenant is {}", vplexProject.getId(), tenantURI);

            waitFor = createWorkflowStepsForBlockVolumeExport(workflow, vplexSystem,
                    storageSystemMap, volumeMap, vplexProject.getId(), tenantURI, waitFor);
            _log.info("Created workflow steps for volume export.");

            // Now make a migration Step for each passed target to which data
            // for the passed virtual volume will be migrated. The migrations
            // will be done from this controller.
            Iterator<URI> targetVolumeIter = targetVolumeURIs.iterator();
            while (targetVolumeIter.hasNext()) {
                URI targetVolumeURI = targetVolumeIter.next();
                _log.info("Target volume is {}", targetVolumeURI);
                URI migrationURI = migrationsMap.get(targetVolumeURI);
                _log.info("Migration is {}", migrationURI);
                String stepId = workflow.createStepId();
                _log.info("Migration opId is {}", stepId);
                Workflow.Method vplexExecuteMethod = new Workflow.Method(
                        MIGRATE_VIRTUAL_VOLUME_METHOD_NAME, vplexURI, virtualVolumeURI,
                        targetVolumeURI, migrationURI, newVarrayURI);
                Workflow.Method vplexRollbackMethod = new Workflow.Method(
                        RB_MIGRATE_VIRTUAL_VOLUME_METHOD_NAME, vplexURI, migrationURI, stepId);
                _log.info("Creating workflow migration step");
                workflow.createStep(MIGRATION_CREATE_STEP, String.format(
                        "VPlex %s migrating to target volume %s.", vplexSystem.getId().toString(), 
                        targetVolumeURI.toString()),
                        waitFor, vplexSystem.getId(), vplexSystem.getSystemType(),
                        getClass(), vplexExecuteMethod, vplexRollbackMethod, stepId);
                _log.info("Created workflow migration step");
            }

            // Once the migrations complete, we will commit the migrations.
            // So, now we create the steps to commit the migrations.
            String waitForStep = MIGRATION_CREATE_STEP;
            List<URI> migrationURIs = new ArrayList<URI>(migrationsMap.values());
            List<URI> migrationSources = new ArrayList<URI>();
            Iterator<URI> migrationsIter = migrationsMap.values().iterator();
            while (migrationsIter.hasNext()) {
                URI migrationURI = migrationsIter.next();
                _log.info("Migration is {}", migrationURI);
                Migration migration = getDataObject(Migration.class, migrationURI, _dbClient);
                // The migration source volume may be null for ingested volumes
                // for which we do not know anything about the backend volumes.
                // If we don't know the source, we know we are migrating an
                // ingested volume and we will not want to do any renaming
                // after the commit as we do when migration ViPR create volumes,
                // which adhere to a standard naming convention.
                Boolean rename = Boolean.TRUE;
                if (migration.getSource() != null) {
                    migrationSources.add(migration.getSource());
                } else {
                    rename = Boolean.FALSE;
                }
                _log.info("Added migration source {}", migration.getSource());
                String stepId = workflow.createStepId();
                _log.info("Commit operation id is {}", stepId);
                Workflow.Method vplexExecuteMethod = new Workflow.Method(
                        COMMIT_MIGRATION_METHOD_NAME, vplexURI, virtualVolumeURI,
                        migrationURI, rename, newVpoolURI, newVarrayURI);
                Workflow.Method vplexRollbackMethod = new Workflow.Method(
                        RB_COMMIT_MIGRATION_METHOD_NAME, migrationURIs, newVpoolURI, newVarrayURI, stepId);
                _log.info("Creating workflow step to commit migration");
                String stepDescription = String.format("migration commit step on VPLEX %s of volume %s",
                        vplexSystem.getSerialNumber(), volumeUserLabel);
                waitForStep = workflow.createStep(
                        MIGRATION_COMMIT_STEP,
                        stepDescription,
                        waitForStep, vplexSystem.getId(),
                        vplexSystem.getSystemType(), getClass(), vplexExecuteMethod,
                        vplexRollbackMethod, suspendBeforeCommit, stepId);
                workflow.setSuspendedStepMessage(stepId, COMMIT_MIGRATION_SUSPEND_MESSAGE);
                _log.info("Created workflow step to commit migration");
            }

            // Create a step that creates a sub workflow to delete the old
            // migration source volumes, which are no longer used by the
            // virtual volume. We also update the virtual volume CoS. If
            // we make it to this step, then all migrations were committed.
            // We do this in a sub workflow because we don't won't to
            // initiate rollback regardless of success or failure.
            String stepId = workflow.createStepId();
            Workflow.Method vplexExecuteMethod = new Workflow.Method(
                    DELETE_MIGRATION_SOURCES_METHOD, vplexURI, virtualVolumeURI,
                    newVpoolURI, newVarrayURI, migrationSources);
            List<String> migrationSourceLabels = new ArrayList<>();
            Iterator<Volume> volumeIter = _dbClient.queryIterativeObjects(Volume.class, migrationSources);
            while (volumeIter.hasNext()) {
                migrationSourceLabels.add(volumeIter.next().getNativeGuid());
            }
            String stepDescription = String.format(
                    "post-migration delete of original source backing volumes [%s] associated with virtual volume %s",
                    Joiner.on(',').join(migrationSourceLabels), volumeUserLabel);
            workflow.createStep(DELETE_MIGRATION_SOURCES_STEP,
                    stepDescription,
                    waitForStep, vplexSystem.getId(), vplexSystem.getSystemType(),
                    getClass(), vplexExecuteMethod, null, suspendBeforeDeleteSource,
                    stepId);
            workflow.setSuspendedStepMessage(stepId, DELETE_MIGRATION_SOURCES_SUSPEND_MESSAGE);
            _log.info("Created workflow step to create sub workflow for source deletion");

            return DELETE_MIGRATION_SOURCES_STEP;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.addStepsForChangeVirtualPoolFailed(e);
        }
    }

    /**
     * Creates a workflow step to validate that the backend volumes of the passed
     * VPLEX volumes are the actual backend volumes used by the VPLEX volume on the
     * VPLEX system. This check is created as a result of a customer issue whereby
     * migrations were done outside of ViPR and as a result, the backend volume used
     * by the VPLEX volume was different than that reflected in the ViPR database.
     * Later, when this VPLEX volume was successfully migrated in ViPR and the
     * migration source was deleted, this resulted in a data loss because that volume
     * was now being used by some other VPLEX volume.
     *
     * @param workflow
     *            A reference to the workflow.
     * @param vplexSystem
     *            A reference to the VPLEX storage system.
     * @param vplexVolumeURI
     *            The step or step group in the workflow to wait for execution.
     *
     * @return The workflow step id.
     */
    private String createWorkflowStepToValidateVPlexVolume(Workflow workflow, StorageSystem vplexSystem, URI vplexVolumeURI,
            String waitFor) {
        URI vplexSystemURI = vplexSystem.getId();
        Workflow.Method validateVPlexVolumeMethod = createValidateVPlexVolumeMethod(vplexSystemURI,
                vplexVolumeURI);
        waitFor = workflow.createStep(VALIDATE_VPLEX_VOLUME_STEP, String.format(
                "Validating VPLEX volume %s on VPLEX %s", vplexSystem.getId().toString(), vplexVolumeURI),
                waitFor, vplexSystemURI, vplexSystem.getSystemType(), this.getClass(),
                validateVPlexVolumeMethod, rollbackMethodNullMethod(), null);

        return waitFor;
    }

    /**
     * Creates a workflow method that can be called by the workflow service
     * to validate that the backend volumes of a VPLEX volume in the ViPR
     * database are the actual backend volumes used by the VPLEX volume on
     * the VPLEX storage system.
     *
     * @param vplexSystemURI
     *            The URI of the VPLEX storage system.
     * @param vplexVolumeURI
     *            The URI of the VPLEX volume to validate.
     *
     * @return A reference to the workflow method.
     */
    private Workflow.Method createValidateVPlexVolumeMethod(URI vplexSystemURI, URI vplexVolumeURI) {
        return new Workflow.Method(VALIDATE_VPLEX_VOLUME_METHOD, vplexSystemURI, vplexVolumeURI);
    }

    /**
     * Validates that the backend volumes of the passed VPLEX volumes in the
     * ViPR database are the actual backend volumes used by the VPLEX volume
     * on the VPLEX system.
     *
     * @param vplexSystemURI
     *            The URI of the VPLEX storage system.
     * @param vplexVolumeURI
     *            The URI of the VPLEX volume to validate.
     * @param stepId
     *            The workflow step id.
     */
    public void validateVPlexVolume(URI vplexSystemURI, URI vplexVolumeURI, String stepId) {
        Volume vplexVolume = null;
        try {
            // Skip this if validation disabled
            ValidatorConfig validatorConfig = new ValidatorConfig();
            validatorConfig.setCoordinator(coordinator);
            if (!validatorConfig.isValidationEnabled()) {
                WorkflowStepCompleter.stepSucceeded(stepId, "Validations not enabled");
                return;
            }

            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Get the VPLEX API client for the VPLEX system.
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexSystemURI, _dbClient);

            // Get the VPLEX volume
            vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);

            // Get a VolumeInfo for each backend volume and any mirrors, mapped by VPLEX cluster name.
            Set<String> volumeIds = new HashSet<>();
            Map<String, List<VolumeInfo>> volumeInfoMap = new HashMap<>();
            StringSet associatedVolumeIds = vplexVolume.getAssociatedVolumes();
            if ((associatedVolumeIds == null) || (associatedVolumeIds.isEmpty())) {
                // Ingested volume w/o backend volume ingestion. We can't verify the backend volumes.
                _log.info("VPLEX volume {}:{} has no backend volumes to validate", vplexVolumeURI, vplexVolume.getLabel());
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            } else {
                volumeIds.addAll(associatedVolumeIds);
            }

            // Now mirrors.
            StringSet mirrorIds = vplexVolume.getMirrors();
            if ((mirrorIds != null) && (mirrorIds.isEmpty() == false)) {
                for (String mirrorId : mirrorIds) {
                    VplexMirror mirror = getDataObject(VplexMirror.class, URI.create(mirrorId), _dbClient);
                    StringSet associatedVolumeIdsForMirror = mirror.getAssociatedVolumes();
                    if ((associatedVolumeIdsForMirror == null) || (associatedVolumeIdsForMirror.isEmpty())) {
                        _log.info("VPLEX mirror {}:{} has no associated volumes", mirrorId, mirror.getLabel());
                        throw DeviceControllerExceptions.vplex.vplexMirrorDoesNotHaveAssociatedVolumes(
                                vplexVolume.getLabel(), mirror.getLabel());
                    } else {
                        volumeIds.addAll(associatedVolumeIdsForMirror);
                    }
                }
            }

            // Get the WWNs for these volumes mapped by VPLEX cluster name.
            for (String volumesId : volumeIds) {
                URI volumeURI = URI.create(volumesId);
                Volume volume = getDataObject(Volume.class, volumeURI, _dbClient);
                String clusterName = VPlexUtil.getVplexClusterName(volume.getVirtualArray(), vplexSystemURI, client, _dbClient);
                StorageSystem storageSystem = getDataObject(StorageSystem.class, volume.getStorageController(), _dbClient);
                List<String> itls = VPlexControllerUtils.getVolumeITLs(volume);
                VolumeInfo volumeInfo = new VolumeInfo(storageSystem.getNativeGuid(), storageSystem.getSystemType(),
                        volume.getWWN().toUpperCase().replaceAll(":", ""), volume.getNativeId(),
                        volume.getThinlyProvisioned().booleanValue(), itls);
                _log.info(String.format("Validating backend volume %s on cluster %s", volumeURI, clusterName));
                if (volumeInfoMap.containsKey(clusterName)) {
                    List<VolumeInfo> clusterVolumeInfos = volumeInfoMap.get(clusterName);
                    clusterVolumeInfos.add(volumeInfo);
                } else {
                    List<VolumeInfo> clusterVolumeInfos = new ArrayList<>();
                    clusterVolumeInfos.add(volumeInfo);
                    volumeInfoMap.put(clusterName, clusterVolumeInfos);
                }
            }

            // Validate the ViPR backend volume WWNs match those on the VPLEX.
            client.validateBackendVolumesForVPlexVolume(vplexVolume.getDeviceLabel(), vplexVolume.getNativeId(), volumeInfoMap);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException ie) {
            _log.info("Exception attempting to validate the backend volumes for VPLEX volume {}", vplexVolumeURI);
            WorkflowStepCompleter.stepFailed(stepId, ie);
        } catch (Exception e) {
            _log.info("Exception attempting to validate the backend volumes for VPLEX volume {}", vplexVolumeURI);
            ServiceCoded sc = DeviceControllerExceptions.vplex.failureValidatingVplexVolume(
                    vplexVolumeURI.toString(), (vplexVolume != null ? vplexVolume.getLabel() : ""), e.getMessage());
            WorkflowStepCompleter.stepFailed(stepId, sc);
        }
    }

    /**
     * Creates and starts a VPlex data migration for the passed virtual volume
     * on the passed VPlex storage system. The passed target is a newly created
     * backend volume to which the data will be migrated. The source for the
     * data migration is the current backend volume for the virtual volume that
     * is in the same varray as the passed target. The method also creates
     * a migration job to monitor the progress of the migration. The workflow
     * step will complete when the migration completes, at which point the
     * migration is automatically committed.
     *
     * @param vplexURI
     *            The URI of the VPlex storage system.
     * @param virtualVolumeURI
     *            The URI of the virtual volume.
     * @param targetVolumeURI
     *            The URI of the migration target.
     * @param migrationURI
     *            The URI of the migration.
     * @param newNhURI
     *            The URI of the new varray for the virtual volume
     *            when a local virtual volume is being migrated to the other
     *            cluster, or null.
     * @param stepId
     *            The workflow step identifier.
     * @throws WorkflowException
     */
    public void migrateVirtualVolume(URI vplexURI, URI virtualVolumeURI,
            URI targetVolumeURI, URI migrationURI, URI newNhURI, String stepId) throws WorkflowException {
        _log.info("Migration {} using target {}", migrationURI, targetVolumeURI);

        try {
            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Initialize the step data. The step data indicates if we
            // successfully started the migration and is used in
            // rollback.
            _workflowService.storeStepData(stepId, Boolean.FALSE);

            // Get the virtual volume.
            Volume virtualVolume = getDataObject(Volume.class, virtualVolumeURI, _dbClient);
            String virtualVolumeName = virtualVolume.getDeviceLabel();
            _log.info("Virtual volume name is {}", virtualVolumeName);

            // Setup the native volume info for the migration target.
            Volume migrationTarget = getDataObject(Volume.class, targetVolumeURI, _dbClient);
            StorageSystem targetStorageSystem = getDataObject(StorageSystem.class,
                    migrationTarget.getStorageController(), _dbClient);
            _log.info("Storage system for migration target is {}",
                    migrationTarget.getStorageController());
            List<String> itls = VPlexControllerUtils.getVolumeITLs(migrationTarget);
            VolumeInfo nativeVolumeInfo = new VolumeInfo(
                    targetStorageSystem.getNativeGuid(), targetStorageSystem.getSystemType(), migrationTarget.getWWN()
                            .toUpperCase().replaceAll(":", ""),
                    migrationTarget.getNativeId(),
                    migrationTarget.getThinlyProvisioned().booleanValue(), itls);

            // Get the migration associated with the target.
            Migration migration = getDataObject(Migration.class, migrationURI, _dbClient);

            // Determine the unique name for the migration. We identifying
            // the migration source and target, using array serial number
            // and volume native id, in the migration name. This was fine
            // for VPlex extent migration, which has a max length of 63
            // for the migration name. However, for remote migrations,
            // which require VPlex device migration, the max length is much
            // more restrictive, like 20 characters. So, we switched over
            // timestamps suffix with stepId.
            StringBuilder migrationNameBuilder = new StringBuilder(MIGRATION_NAME_PREFIX);
            DateFormat dateFormatter = new SimpleDateFormat(MIGRATION_NAME_DATE_FORMAT);
            migrationNameBuilder.append(dateFormatter.format(new Date()));
            // Append step id last 7 char to migration name to make it unique
            if (stepId.length() > 6) {
                String nameSuffex = stepId.substring(stepId.length() - 7);
                migrationNameBuilder.append(nameSuffex.toUpperCase());
            } else {
                migrationNameBuilder.append(stepId.toUpperCase());
            }
            String migrationName = migrationNameBuilder.toString();
            migration.setLabel(migrationName);
            _dbClient.updateObject(migration);
            _log.info("Migration name is {}", migrationName);

            // Get the VPlex API client.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexSystem, _dbClient);
            _log.info("Got VPlex API client for VPlex {}", vplexURI);

            // Get the configured migration speed
            String speed = customConfigHandler.getComputedCustomConfigValue(CustomConfigConstants.MIGRATION_SPEED,
                    vplexSystem.getSystemType(), null);
            _log.info("Migration speed is {}", speed);
            String transferSize = migrationSpeedToTransferSizeMap.get(speed);
            // Make a call to the VPlex API client to migrate the virtual
            // volume. Note that we need to do a remote migration when a
            // local virtual volume is being migrated to the other VPlex
            // cluster. If the passed new varray is not null, then
            // this is the case.
            Boolean isRemoteMigration = newNhURI != null;
            // We support both device and extent migrations, however,
            // when we don't know anything about the backend volumes
            // we must use device migration.
            Boolean useDeviceMigration = migration.getSource() == null;
            List<VPlexMigrationInfo> migrationInfoList = client.migrateVirtualVolume(
                    migrationName, virtualVolumeName, Arrays.asList(nativeVolumeInfo),
                    isRemoteMigration, useDeviceMigration, true, true, transferSize);
            _log.info("Started VPlex migration");

            // We store step data indicating that the migration was successfully
            // create and started. We will use this to determine the behavior
            // on rollback. If we never got to the point that the migration
            // was created and started, then there is no rollback to attempt
            // on the VLPEX as the migrate API already tried to clean everything
            // up on the VLPEX.
            _workflowService.storeStepData(stepId, Boolean.TRUE);

            // Initialize the migration info in the database.
            VPlexMigrationInfo migrationInfo = migrationInfoList.get(0);
            migration.setMigrationStatus(VPlexMigrationInfo.MigrationStatus.READY
                    .getStatusValue());
            migration.setPercentDone("0");
            migration.setStartTime(migrationInfo.getStartTime());
            _dbClient.updateObject(migration);
            _log.info("Update migration info");

            // Create a migration task completer and queue a job to monitor
            // the migration progress. The completer will be invoked by the
            // job when the migration completes.
            MigrationTaskCompleter migrationCompleter = new MigrationTaskCompleter(
                    migrationURI, stepId);
            VPlexMigrationJob migrationJob = new VPlexMigrationJob(migrationCompleter);
            migrationJob.setTimeoutTimeMsec(MINUTE_TO_MILLISECONDS *
                    Long.valueOf(ControllerUtils.getPropertyValueFromCoordinator(coordinator, CONTROLLER_VPLEX_MIGRATION_TIMEOUT_MINUTES)));
            ControllerServiceImpl.enqueueJob(new QueueJob(migrationJob));
            _log.info("Queued job to monitor migration progress.");
        } catch (VPlexApiException vae) {
            _log.error("Exception migrating VPlex virtual volume: " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception migrating VPlex virtual volume: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.MIGRATE_VIRTUAL_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.migrateVirtualVolume(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    /**
     * Called when a migration cannot be created and started or the data
     * migration is terminated prior to completing successfully. The function
     * tries to cancel the migration and cleanup the remnants of the migration
     * on the VPLEX.
     *
     * @param vplexURI
     *            The URI of the VPLex storage system.
     * @param migrationURI
     *            The URI of the migration.
     * @param migrateStepId
     *            The migration step id.
     * @param stepId
     *            The rollback step id.
     * @throws WorkflowException
     */
    public void rollbackMigrateVirtualVolume(URI vplexURI, URI migrationURI,
            String migrateStepId, String stepId) throws WorkflowException {
        Migration migration = null;
        String migrationVolumeLabel = null;
        try {
            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Was the migration created and started? If so, then
            // we'll try and cancel the migration and clean up.
            // Otherwise, there is nothing to do.
            Boolean migrationStarted = (Boolean) _workflowService.loadStepData(migrateStepId);
            if (!migrationStarted.booleanValue()) {
                // The migration was not successfully started.
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }

            // Get the migration.
            migration = _dbClient.queryObject(Migration.class, migrationURI);

            // Get the VPLEX volume for the migration.
            Volume migrationVolume = _dbClient.queryObject(Volume.class, migration.getVolume());
            if (migrationVolume != null) {
                migrationVolumeLabel = migrationVolume.getLabel();
            }

            // The migration could have failed due to an error or it may have
            // failed because it was cancelled outside the scope of the
            // workflow. Check the status, and if it's not cancelled, try and
            // cancel it now.
            if (!VPlexMigrationInfo.MigrationStatus.CANCELLED.getStatusValue().equals(
                    migration.getMigrationStatus())) {
                _log.info("Cancel migration {}", migrationURI);

                // Get the VPlex API client.
                StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
                VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexSystem, _dbClient);
                _log.info("Got VPlex API client for VPlex {}", vplexURI);

                // Try to cancel the migration and cleanup and remove any
                // remnants of the migration.
                client.cancelMigrations(Arrays.asList(migration.getLabel()), true, true);
                _log.info("Migration cancelled");
            }
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (VPlexApiException vae) {
            // Do not allow rollback to go any further COP-21257
            _workflowService.setWorkflowRollbackContOnError(stepId, false);
            _log.error("Error during rollback of start migration: {}", vae.getMessage(), vae);
            if (migration != null) {
                setOrClearVolumeInternalFlag(migration.getVolume(), true);
                vae = VPlexApiException.exceptions.migrationRollbackFailureContactEMC(
                        migration.getVolume().toString(), migrationVolumeLabel, migration.getLabel());
            }
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception e) {
            _log.error("Error during rollback of start migration: {}", e.getMessage());
            // Do not allow rollback to go any further COP-21257
            _workflowService.setWorkflowRollbackContOnError(stepId, false);
            if (migration != null) {
                setOrClearVolumeInternalFlag(migration.getVolume(), true);
                e = VPlexApiException.exceptions.migrationRollbackFailureContactEMC(
                        migration.getVolume().toString(), migrationVolumeLabel, migration.getLabel());
            }
            WorkflowStepCompleter.stepFailed(stepId, VPlexApiException.exceptions.rollbackMigrateVolume(migrationURI.toString(), e));
        }
    }

    /**
     * Sets or clears the volume internal flag.
     * For a virtual volume, this is normally set because it's in an
     * undetermined state (such as from a failed migration rollback).
     * In this case EMC will have to be contacted to update things in the database.
     *
     * @param volumeURI
     * @param set
     *            if true, sets the INTERNAL_OBJECT flag, if false clears it
     */
    void setOrClearVolumeInternalFlag(URI volumeURI, boolean set) {
        Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
        if (set) {
            _log.info(String.format("Setting volumes %s (%s) INTERNAL_OBJECT flag", volume.getLabel(), volumeURI));
            volume.addInternalFlags(DataObject.Flag.INTERNAL_OBJECT);
        } else {
            _log.info(String.format("Clearing volume %s (%s) INTERNAL_OBJECT flag", volume.getLabel(), volumeURI));
            volume.clearInternalFlags(DataObject.Flag.INTERNAL_OBJECT);
        }
        _dbClient.updateObject(volume);
    }

    /**
     * Invoked by the migration workflow to commit the migration after it has
     * been completed.
     *
     * @param vplexURI
     *            The URI of the VPlex storage system.
     * @param virtualVolumeURI
     *            The URI of the virtual volume.
     * @param migrationURI
     *            The URI of the data migration.
     * @param rename
     *            Indicates if the volume should be renamed after commit to
     *            conform to ViPR standard naming conventions.
     * @param newVpoolURI - the new virtual pool for the virtual volume (or null if not changing)
     * @param newVarrayURI - the new varray for the virtual volume (or null if not changing)
     * @param stepId
     *            The workflow step identifier.
     *
     * @throws WorkflowException
     */
    public void commitMigration(URI vplexURI, URI virtualVolumeURI, URI migrationURI,
            Boolean rename, URI newVpoolURI, URI newVarrayURI, String stepId) throws WorkflowException {
        _log.info("Committing migration {}", migrationURI);
        Migration migration = null;
        VPlexApiClient client = null;
        try {
            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Get the migration.
            migration = getDataObject(Migration.class, migrationURI, _dbClient);

            // The migration could have already been committed outside of the
            // workflow, so check the status.
            if (!VPlexMigrationInfo.MigrationStatus.COMMITTED.getStatusValue().equals(
                    migration.getMigrationStatus())) {
                // Get the VPlex API client.
                StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
                client = getVPlexAPIClient(_vplexApiFactory, vplexSystem, _dbClient);
                _log.info("Got VPlex API client for system {}", vplexURI);

                // Make a call to the VPlex API client to commit the migration.
                // Note that for ingested VPLEX volumes created outside ViPR, we
                // don't want to update the name.
                List<VPlexMigrationInfo> migrationInfoList = new ArrayList<VPlexMigrationInfo>();
                Volume virtualVolume = getDataObject(Volume.class, virtualVolumeURI, _dbClient);
                try {
                    migrationInfoList = client.commitMigrations(virtualVolume.getDeviceLabel(),
                            Arrays.asList(migration.getLabel()), true, true, rename.booleanValue());
                    _log.info("Committed migration {}", migration.getLabel());
                } catch (VPlexApiException vae) {
                    _log.error("Exception committing VPlex migration: " + vae.getMessage(), vae);
                    boolean committed = false;
                    // Check the migration status. Maybe it committed even though we had an error.
                    VPlexMigrationInfo migrationInfo = client.getMigrationInfo(migration.getLabel());
                    if (migrationInfo.getStatus().equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.COMMITTED.name())) {
                        _log.info("Migration {} has committed despite exception", migration.getLabel());
                        migrationInfoList.clear();
                        migrationInfoList.add(migrationInfo);
                        committed = true;
                    } else {
                        _log.info("Migration {} status {}", migration.getLabel(), migrationInfo.getStatus());
                    }
                    if (!committed) {
                        // If the exception was a timeout, clear the rollback continue flag
                        // This was observed at customer site COP-21257
                        if (vae.getServiceCode() == ServiceCode.VPLEX_API_RESPONSE_TIMEOUT_ERROR) {
                            // We are going to throw an error, but we don't want to rollback completely
                            _workflowService.setWorkflowRollbackContOnError(stepId, false);
                        }
                        WorkflowStepCompleter.stepFailed(stepId, vae);
                        return;
                    }
                }

                // Below this point migration is committed, no turning back.

                // Initialize the migration info in the database.
                migration.setMigrationStatus(VPlexMigrationInfo.MigrationStatus.COMMITTED.getStatusValue());
                _dbClient.updateObject(migration);
                _log.info("Update migration status to committed");

                // Update the virtual volume native id and associated
                // volumes. Note that we don't update CoS until all
                // commits are successful.
                VPlexVirtualVolumeInfo updatedVirtualVolumeInfo = migrationInfoList.get(0).getVirtualVolumeInfo();

                // update any properties that were changed after migration including deviceLabel, nativeGuid, and nativeId.
                // also, if the updated volume isn't thin-enabled, it is thin-capable, and the target vpool supports thin
                // provisioning, then a call should be made to the VPLEX to flip the thin-enabled flag on for this volume.
                URI targetVolumeUri = migration.getTarget();
                Volume targetVolume = getDataObject(Volume.class, targetVolumeUri, _dbClient);
                if (updatedVirtualVolumeInfo != null) {
                    _log.info(String.format("New virtual volume is %s", updatedVirtualVolumeInfo.toString()));

                    // if the new virtual volume is thin-capable, but thin-enabled is not true,
                    // that means we need to ask the VPLEX to convert it to a thin-enabled volume.
                    // this doesn't happen automatically for thick-to-thin data migrations.
                    boolean isThinEnabled = updatedVirtualVolumeInfo.isThinEnabled();
                    if (!isThinEnabled && VPlexApiConstants.TRUE.equalsIgnoreCase(updatedVirtualVolumeInfo.getThinCapable())) {
                        if (verifyVplexSupportsThinProvisioning(vplexSystem)) {
                            if (null != targetVolume) {
                                _log.info(String.format("migration target Volume is %s", targetVolume.forDisplay()));
                                VirtualPool targetVirtualPool = getDataObject(VirtualPool.class, targetVolume.getVirtualPool(), _dbClient);
                                if (null != targetVirtualPool) {
                                    _log.info(String.format("migration target VirtualPool is %s", targetVirtualPool.forDisplay()));
                                    boolean doEnableThin = VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(
                                            targetVirtualPool.getSupportedProvisioningType());
                                    if (doEnableThin) {
                                        _log.info(String.format(
                                                "the new VirtualPool is thin, requesting VPLEX to enable thin provisioning on %s",
                                                updatedVirtualVolumeInfo.getName()));
                                        isThinEnabled = client.setVirtualVolumeThinEnabled(updatedVirtualVolumeInfo);
                                    }
                                }
                            }
                        }
                    }

                    virtualVolume.setDeviceLabel(updatedVirtualVolumeInfo.getName());
                    virtualVolume.setNativeId(updatedVirtualVolumeInfo.getPath());
                    virtualVolume.setNativeGuid(updatedVirtualVolumeInfo.getPath());
                    virtualVolume.setThinlyProvisioned(isThinEnabled);
                }
                // Note that for ingested volumes, there will be no associated volumes
                // at first.
                StringSet assocVolumes = virtualVolume.getAssociatedVolumes();
                if ((assocVolumes != null) && (!assocVolumes.isEmpty())) {
                    // For a distributed volume, there could be multiple
                    // migrations. When the first completes, there will
                    // be no associated volumes. However, when the second
                    // completes, there will be associated volumes. However,
                    // the migration source could be null.
                    URI sourceVolumeUri = migration.getSource();
                    if (sourceVolumeUri != null) {
                        assocVolumes.remove(sourceVolumeUri.toString());
                        
                        // Retain any previous RP fields on the new target volume.
                        Volume sourceVolume = getDataObject(Volume.class, sourceVolumeUri, _dbClient);
                        if (sourceVolume != null) {
                            boolean targetUpdated = false;
                            if (NullColumnValueGetter.isNotNullValue(sourceVolume.getRpCopyName())) {
                                targetVolume.setRpCopyName(sourceVolume.getRpCopyName());
                                targetUpdated = true;
                            }
    
                            if (NullColumnValueGetter.isNotNullValue(sourceVolume.getInternalSiteName())) {
                                targetVolume.setInternalSiteName(sourceVolume.getInternalSiteName());
                                targetUpdated = true;
                            }
                            if (targetUpdated) {
                                _dbClient.updateObject(targetVolume);
                            }
                        }
                    }
                    assocVolumes.add(migration.getTarget().toString());
                } else {
                    // NOTE: Now an ingested volume will have associated volumes.
                    // It will no longer be considered an ingested volume.
                    assocVolumes = new StringSet();
                    assocVolumes.add(migration.getTarget().toString());
                    virtualVolume.setAssociatedVolumes(assocVolumes);
                }
                updateMigratedVirtualVolumeVpoolAndVarray(virtualVolume, newVpoolURI, newVarrayURI);
                _dbClient.updateObject(virtualVolume);
                _log.info("Updated virtual volume.");
            } else {
                _log.info("The migration is already committed.");
                // Note that we don't set the device label and native id. If the
                // migration was committed outside of Bourne, the virtual volume
                // will still have the old name. If it was committed through
                // Bourne, these values would already have been update.
                // Regardless, we have to update the vpool, and we update the
                // associated volumes in case it was committed outside of
                // Bourne.
                associateVplexVolumeWithMigratedTarget(migration, virtualVolumeURI);
                _log.info("Updated virtual volume.");
            }

            // Update the workflow step status.
            StringBuilder successMsgBuilder = new StringBuilder();
            successMsgBuilder.append("VPlex System: ");
            successMsgBuilder.append(vplexURI);
            successMsgBuilder.append(" migration: ");
            successMsgBuilder.append(migrationURI);
            successMsgBuilder.append(" was committed");
            _log.info(successMsgBuilder.toString());
            WorkflowStepCompleter.stepSucceded(stepId);
            _log.info("Updated workflow step state to success");
        } catch (VPlexApiException vae) {
            _log.error("Exception committing VPlex migration: " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception committing VPlex migration: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.COMMIT_VOLUME_MIGRATION.getName();
            ServiceError serviceError = VPlexApiException.errors.commitMigrationFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    /**
     * Updates the Vpool and Varray of a migrated volume after commit succeeds.
     * NOTE: this routine does not persist the vplex volume; it is the caller's
     * responsibility to do so.
     * 
     * @param volume -- Volume object
     * @param newVpoolURI -- new VPool URI (may be null)
     * @param newVarrayURI -- new Varray URI (may be null)
     */
    private void updateMigratedVirtualVolumeVpoolAndVarray(Volume volume, URI newVpoolURI, URI newVarrayURI) {
        // First update the virtual volume Vpool, if necessary.
        if (newVpoolURI != null) {
            // Typically the source side backend volume has the same vpool as the
            // VPLEX volume. If the source side is not being migrated when the VPLEX
            // volume is migrated, then its vpool will reflect the old vpool. If the
            // source side had the same as the VPLEX volume, then make sure it still
            // does.
            Volume backendSrcVolume = VPlexUtil.getVPLEXBackendVolume(volume, true, _dbClient);
            if (backendSrcVolume != null) {
                if (backendSrcVolume.getVirtualPool().toString().equals(volume.getVirtualPool().toString())) {
                    backendSrcVolume.setVirtualPool(newVpoolURI);
                    _dbClient.updateObject(backendSrcVolume);
                }
            }

            // Now update the vpool for the VPLEX volume.
            volume.setVirtualPool(newVpoolURI);
        } else if (newVarrayURI != null) {
            // Update the virtual volume Varray, if necessary
            volume.setVirtualArray(newVarrayURI);
        }
    }

    /**
     * Rollback when a migration commit fails.
     *
     * @param migrationURIs
     *            The URIs for all migrations.
     * @param newVpoolURI
     *            The URI of the new Vpool after migration commit
     * @param newVarrayURI
     *            The URI of the new Varray after migration commit
     * @param commitStepId
     *            The commit step id.
     * @param stepId
     *            The rollback step id.
     * 
     * @throws WorkflowException
     */
    public void rollbackCommitMigration(List<URI> migrationURIs, URI newVpoolURI, URI newVarrayURI, String commitStepId,
            String stepId) throws WorkflowException {
        // Update step state to executing.
        WorkflowStepCompleter.stepExecuting(stepId);

        try {
            // Determine if any migration was successfully committed.
            boolean migrationCommitted = false;
            Iterator<URI> migrationIter = migrationURIs.iterator();
            while (migrationIter.hasNext()) {
                URI migrationURI = migrationIter.next();
                Migration migration = _dbClient.queryObject(Migration.class, migrationURI);
                Volume volume = _dbClient.queryObject(Volume.class, migration.getVolume());
                // Check migration database record for committed state
                if (VPlexMigrationInfo.MigrationStatus.COMMITTED.getStatusValue().equals(migration.getMigrationStatus())) {
                    migrationCommitted = true;
                    updateMigratedVirtualVolumeVpoolAndVarray(volume, newVpoolURI, newVarrayURI);
                    _dbClient.updateObject(volume);
                    inventoryDeleteMigrationSource(migration.getSource(), volume);
                    continue;
                }
                // Check vplex hardware migration records for committed state
                VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, volume.getStorageController(), _dbClient);
                VPlexMigrationInfo migrationInfo = client.getMigrationInfo(migration.getLabel());
                if (migrationInfo.getStatus().equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.COMMITTED.name())) {
                    migrationCommitted = true;
                    migration.setMigrationStatus(VPlexMigrationInfo.MigrationStatus.COMMITTED.name());
                    _dbClient.updateObject(migration);
                    associateVplexVolumeWithMigratedTarget(migration, migration.getVolume());
                    updateMigratedVirtualVolumeVpoolAndVarray(volume, newVpoolURI, newVarrayURI);
                    _dbClient.updateObject(volume);
                    inventoryDeleteMigrationSource(migration.getSource(), volume);
                    continue;
                }
            }

            // All we want to do is prevent further rollback if any migration
            // has been committed so that we don't end up deleting the migration
            // targets of the committed migrations, which now hold the data.
            // If the migration is not committed, then rollback of the migration
            // creation step will cancel the migration.
            if (migrationCommitted) {
                _log.info("The migration has already been committed or the migration state can not be determined, failing rollback");
                // Don't allow rollback to go further than the first error.
                _workflowService.setWorkflowRollbackContOnError(stepId, false);
                ServiceError serviceError = VPlexApiException.errors.cantRollbackCommittedMigration();
                WorkflowStepCompleter.stepFailed(stepId, serviceError);
            } else {
                _log.info("No Migrations are not committed");
                WorkflowStepCompleter.stepSucceded(stepId);
            }
        } catch (Exception e) {
            _log.info("Exception determining commit rollback state", e);
            // Don't allow rollback to go further than the first error.
            _workflowService.setWorkflowRollbackContOnError(stepId, false);
            ServiceError serviceError = VPlexApiException.errors.cantRollbackExceptionDeterminingCommitState(e);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    /**
     * Updates the virtual volume by removing association to the migration source (if present)
     * and adding association to the migration target.
     *
     * @param migration
     *            Migration structure
     * @param virtualVolumeURI
     *            - URI of virtual volume
     */
    void associateVplexVolumeWithMigratedTarget(Migration migration, URI virtualVolumeURI) {
        // Note that we don't set the device label and native id. If the
        // migration was committed outside of Bourne, the virtual volume
        // will still have the old name. If it was committed through
        // Bourne, these values would already have been update.
        // Regardless, we have to update the vpool, and we update the
        // associated volumes in case it was committed outside of
        // Bourne.
        Volume virtualVolume = getDataObject(Volume.class, virtualVolumeURI, _dbClient);
        StringSet assocVolumes = virtualVolume.getAssociatedVolumes();
        if ((assocVolumes != null) && (!assocVolumes.isEmpty())) {
            if (migration.getSource() != null) {
                assocVolumes.remove(migration.getSource().toString());
            }
            assocVolumes.add(migration.getTarget().toString());
        } else {
            assocVolumes = new StringSet();
            assocVolumes.add(migration.getTarget().toString());
            virtualVolume.setAssociatedVolumes(assocVolumes);
        }
        _dbClient.updateObject(virtualVolume);
        _log.info("Updated virtual volume.");
    }

    /**
     * Try and inventory delete the migration source volume
     * 
     * @param sourceVolumeURI
     *            Source volume URI
     * @param virtualVolume
     *            Volume object for virtual volume, used to clean up consistency group
     */
    private void inventoryDeleteMigrationSource(URI sourceVolumeURI, Volume virtualVolume) {
        if (NullColumnValueGetter.isNullURI(sourceVolumeURI)) {
            return;
        }
        Volume sourceVolume = _dbClient.queryObject(Volume.class, sourceVolumeURI);
        try {
            if (sourceVolume == null || sourceVolume.getInactive()) {
                return;
            }
            URI storageSystemToRemove = sourceVolume.getStorageController();
            ExportUtils.cleanBlockObjectFromExports(sourceVolumeURI, true, _dbClient);
            _dbClient.removeObject(sourceVolume);

            // If the virtual volume is in a CG, and has information about the associated volumes,
            // clean up the CG by removing the source volumes storage system unless it is the same as
            // one of the associated volumes.
            if (!NullColumnValueGetter.isNullURI(virtualVolume.getConsistencyGroup())
                    && (virtualVolume.getAssociatedVolumes() != null && !virtualVolume.getAssociatedVolumes().isEmpty())) {
                Volume srcAssocVolume = VPlexUtil.getVPLEXBackendVolume(virtualVolume, true, _dbClient, false);
                if (srcAssocVolume != null && srcAssocVolume.getStorageController().equals(storageSystemToRemove)) {
                    storageSystemToRemove = null;
                }
                Volume haAssocVolume = VPlexUtil.getVPLEXBackendVolume(virtualVolume, false, _dbClient, false);
                if (haAssocVolume != null && haAssocVolume.getStorageController().equals(storageSystemToRemove)) {
                    storageSystemToRemove = null;
                }
                // No more uses in by this storage system, remove from CG if present
                if (storageSystemToRemove != null) {
                    BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, virtualVolume.getConsistencyGroup());
                    StringSet cgNames = cg.getSystemConsistencyGroups().get(storageSystemToRemove.toString());
                    if (cgNames != null) {
                        for (String cgName : cgNames) {
                            cg.removeSystemConsistencyGroup(storageSystemToRemove.toString(), cgName);
                        }
                        _dbClient.updateObject(cg);
                    }
                }
            }
        } catch (Exception ex) {
            _log.info("Unable to inventory delete migration source volume after commit: " + sourceVolume.getLabel(), ex);
        }
    }

    /**
     * This step is executed after a virtual volume is successfully migrated to
     * delete the old source volumes. We create a sub workflow to perform this
     * task. We do this in a sub workflow because we don't want any of the steps
     * required to delete the sources to initiate rollback in the main workflow
     * if it fails.
     *
     * We also update the class of service, if required, for the virtual
     * volume when the migration is the result of a CoS change. When this
     * step is executed we know that the migrations have been committed and
     * the new CoS now applies to the virtual volume.
     *
     * @param vplexURI
     *            The URI of the VPlex storage system.
     * @param virtualVolumeURI
     *            The URI of the virtual volume.
     * @param newVpoolURI
     *            The CoS to be assigned to the virtual volume
     *            upon successful commit of the migration or null when not
     *            specified.
     * @param newVarrayURI
     *            The varray to be assigned to the virtual volume
     *            upon successful commit of the migration or null when not
     *            specified.
     * @param migrationSources
     *            The migration sources to delete.
     * @param stepId
     *            The workflow step id.
     * @throws WorkflowException
     */
    public void deleteMigrationSources(URI vplexURI, URI virtualVolumeURI,
            URI newVpoolURI, URI newVarrayURI, List<URI> migrationSources, String stepId) throws WorkflowException {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            // First update the virtual volume CoS, if necessary.
            Volume volume = _dbClient.queryObject(Volume.class, virtualVolumeURI);

            // If volumes are exported, and this is change varray operation, we need to remove the volume from the
            // current exportGroup, then
            // add it to another export group, which has the same new virtual array, and the same host,
            // or create a new exportGroup
            if (newVarrayURI != null && volume.isVolumeExported(_dbClient)) {
                URIQueryResultList exportGroupURIs = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory.getBlockObjectExportGroupConstraint(virtualVolumeURI),
                        exportGroupURIs);
                Iterator<URI> iterator = exportGroupURIs.iterator();
                while (iterator.hasNext()) {
                    URI egUri = iterator.next();
                    ExportGroup eg = _dbClient.queryObject(ExportGroup.class, egUri);
                    if (eg != null) {
                        StringMap volumesMap = eg.getVolumes();
                        String lun = volumesMap.get(virtualVolumeURI.toString());
                        if (lun == null || lun.isEmpty()) {
                            lun = ExportGroup.LUN_UNASSIGNED_DECIMAL_STR;
                        }
                        List<URI> initiators = StringSetUtil.stringSetToUriList(eg.getInitiators());
                        ExportGroup newEg = null;
                        if (initiators != null && !initiators.isEmpty()) {
                            URI initiatorUri = initiators.get(0);
                            AlternateIdConstraint constraint = AlternateIdConstraint.Factory
                                    .getExportGroupInitiatorConstraint(initiatorUri.toString());
                            URIQueryResultList egUris = new URIQueryResultList();
                            _dbClient.queryByConstraint(constraint, egUris);
                            Iterator<URI> egIt = egUris.iterator();
                            while (egIt.hasNext()) {
                                ExportGroup theEg = _dbClient.queryObject(ExportGroup.class, egIt.next());
                                if (theEg.getVirtualArray().equals(newVarrayURI)) {
                                    List<URI> theEgInits = StringSetUtil.stringSetToUriList(theEg.getInitiators());
                                    if (theEgInits.containsAll(initiators) && theEgInits.size() == initiators.size()) {
                                        _log.info(String.format("Found existing exportGroup %s", theEg.getId().toString()));
                                        newEg = theEg;
                                        break;
                                    }
                                }
                            }

                        }
                        if (newEg != null) {
                            // add the volume to the export group
                            newEg.addVolume(virtualVolumeURI, Integer.valueOf(lun));
                            _dbClient.updateObject(newEg);
                        } else {
                            // create a new export group
                            _log.info("Creating new ExportGroup");
                            createExportGroup(eg, volume, Integer.valueOf(lun));
                        }
                        eg.removeVolume(virtualVolumeURI);
                        _dbClient.updateObject(eg);
                    }
                }
            }

            if (!migrationSources.isEmpty()) {
                final String workflowKey = "deleteOriginalSources";
                if (!WorkflowService.getInstance().hasWorkflowBeenCreated(stepId, workflowKey)) {
                    // Now create and execute the sub workflow to delete the
                    // migration source volumes if we have any. If the volume
                    // migrated was ingested VPLEX volume we will not have
                    // the sources.
                    String subTaskId = stepId;
                    Workflow subWorkflow = _workflowService.getNewWorkflow(this,
                            DELETE_MIGRATION_SOURCES_WF_NAME, true, subTaskId);

                    WorkflowTaskCompleter completer = new WorkflowTaskCompleter(subWorkflow.getWorkflowURI(), subTaskId);

                    // Creates steps to remove the migration source volumes from all
                    // export groups containing them and delete them.
                    boolean unexportStepsAdded = vplexAddUnexportVolumeWfSteps(subWorkflow,
                            null, migrationSources, null);

                    // Only need to wait for unexport if there was a step for it added
                    // to the workflow.
                    String waitFor = null;
                    if (unexportStepsAdded) {
                        waitFor = UNEXPORT_STEP;

                        // If the migration sources are unexported, Add a step to
                        // forget these backend volumes.
                        addStepToForgetVolumes(subWorkflow, vplexURI, migrationSources, waitFor);
                    }

                    // Add steps to delete the volumes.
                    Iterator<URI> migrationSourcesIter = migrationSources.iterator();
                    while (migrationSourcesIter.hasNext()) {
                        URI migrationSourceURI = migrationSourcesIter.next();
                        _log.info("Migration source URI is {}", migrationSourceURI);
                        Volume migrationSource = _dbClient.queryObject(Volume.class,
                                migrationSourceURI);
                        URI sourceSystemURI = migrationSource.getStorageController();
                        _log.info("Source storage system URI is {}", sourceSystemURI);
                        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class,
                                sourceSystemURI);

                        String subWFStepId = subWorkflow.createStepId();
                        Workflow.Method deleteVolumesMethod = new Workflow.Method(
                                DELETE_VOLUMES_METHOD_NAME, sourceSystemURI,
                                Arrays.asList(migrationSourceURI));
                        _log.info("Creating workflow step to delete source");
                        subWorkflow.createStep(MIGRATION_VOLUME_DELETE_STEP, String.format(
                                "Delete volume from storage system: %s", sourceSystemURI),
                                waitFor, sourceSystemURI, sourceSystem.getSystemType(),
                                BlockDeviceController.class, deleteVolumesMethod, null, subWFStepId);
                        _log.info("Created workflow step to delete source");
                    }

                    // Execute this sub workflow.
                    subWorkflow.executePlan(completer, "Deleted migration sources");
                    // Mark this workflow as created/executed so we don't do it again on retry/resume
                    WorkflowService.getInstance().markWorkflowBeenCreated(stepId, workflowKey);
                }
            } else {
                // No sources to delete. Must have migrated an ingested volume.
                WorkflowStepCompleter.stepSucceded(stepId);
                _log.info("Updated workflow step to success");
            }
        } catch (Exception ex) {
            // Log the error.
            _log.error("Error deleting migration sources", ex);

            // Always return success. This is a cleanup step after a
            // successfully committed migration. We don't want rollback,
            // so we return success.
            WorkflowStepCompleter.stepSucceded(stepId);
        }
    }

    /**
     * Updated importVolume code to create underlying volumes using other controllers.
     *
     * @param volumeDescriptors
     *            -- Contains the VPLEX_VIRTUAL volume, and optionally,
     *            a protection BLOCK_DATA volume to be created.
     * @param vplexSystemProject
     * @param vplexSystemTenant
     * @param importedVolumeURI
     *            -- For the import use case, will give the URI of the existing
     *            storage array volume to be imported.
     * @param newCosURI
     * @param opId
     * @throws ControllerException
     */
    @Override
    public void importVolume(URI vplexURI, List<VolumeDescriptor> volumeDescriptors,
            URI vplexSystemProject, URI vplexSystemTenant, URI newCosURI, String newLabel, String setTransferSpeed,
            Boolean markInactive, String opId) throws ControllerException {
        // Figure out the various arguments.
        List<VolumeDescriptor> vplexDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { Type.VPLEX_VIRT_VOLUME },
                new VolumeDescriptor.Type[] {});
        Assert.isTrue(vplexDescriptors.size() == 1); // must be one VPLEX volume
        VolumeDescriptor vplexDescriptor = vplexDescriptors.get(0);
        URI vplexVolumeURI = vplexDescriptor.getVolumeURI();
        List<URI> volumeURIs = new ArrayList<URI>();
        volumeURIs.add(vplexVolumeURI);

        // See if there are any volumes to be created.
        List<VolumeDescriptor> blockDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { Type.BLOCK_DATA },
                new VolumeDescriptor.Type[] {});
        URI createdVolumeURI = null;
        if (blockDescriptors.size() == 1) {
            createdVolumeURI = blockDescriptors.get(0).getVolumeURI();
        }
        Map<URI, StorageSystem> arrayMap = buildArrayMap(null, blockDescriptors, null);
        Map<URI, Volume> volumeMap = buildVolumeMap(null, blockDescriptors, null);
        if (!blockDescriptors.isEmpty()) {
            volumeURIs.addAll(VolumeDescriptor.getVolumeURIs(blockDescriptors));
        }

        // See if there is a Volume to be imported.
        List<VolumeDescriptor> importDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { Type.VPLEX_IMPORT_VOLUME },
                new VolumeDescriptor.Type[] {});
        URI importedVolumeURI = null;
        if (!importDescriptors.isEmpty()) {
            importedVolumeURI = importDescriptors.get(0).getVolumeURI();
            volumeURIs.add(importedVolumeURI);
        }

        // Get the VPlex storage system and the volumes.
        StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
        Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);

        // If there is a volume to be imported, we're creating a new Virtual Volume from
        // the imported volume. Otherwise, we're upgrading an existing Virtual Volume to be
        // distributed.
        StorageSystem importedArray = null;
        Volume importedVolume = null;
        if (importedVolumeURI != null) {
            importedVolume = getDataObject(Volume.class, importedVolumeURI, _dbClient);
            importedArray = getDataObject(StorageSystem.class, importedVolume.getStorageController(), _dbClient);
            arrayMap.put(importedArray.getId(), importedArray);
            volumeMap.put(importedVolumeURI, importedVolume);
        }

        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    IMPORT_VOLUMES_WF_NAME, false, opId);
            String waitFor = null;

            // Add a rollback step to remove the Virtual Volume if we are
            // importing and therefore creating a new virtual volume.
            List<URI> vplexVolumeURIs = new ArrayList<URI>();
            vplexVolumeURIs.add(vplexVolumeURI);
            if (importedVolumeURI != null) {
                Workflow.Method nullMethod = rollbackMethodNullMethod();
                Workflow.Method virtVolInactiveMethod = markVolumesInactiveMethod(vplexVolumeURIs);
                waitFor = workflow.createStep(null, "Mark virtual volume inactive on rollback",
                        waitFor, vplexURI,
                        vplexSystem.getSystemType(), this.getClass(),
                        nullMethod, virtVolInactiveMethod, null);
            }

            // Create a step in the workflow to to create the new volume
            // to which the data for the virtual volume will saved (if used).
            if (createdVolumeURI != null) {
                waitFor = _blockDeviceController.addStepsForCreateVolumes(
                        workflow, waitFor, volumeDescriptors, opId);
            }

            // Set the project and tenant to those of an underlying volume.
            Volume firstVolume = volumeMap.values().iterator().next();
            URI projectURI = firstVolume.getProject().getURI();
            URI tenantURI = firstVolume.getTenant().getURI();

            Project project = _dbClient.queryObject(Project.class, projectURI);
            // Now we need to do the necessary zoning and export steps to ensure
            // the VPlex can see these new backend volumes.
            if (!project.checkInternalFlags(Flag.INTERNAL_OBJECT) && vplexSystemProject != null && vplexSystemTenant != null) {
                // If project is not set as an INTERAL_OBJECT then this is the case
                // where native volume is moved into VPLEX.
                // vplexSystemProject and vplexSystemTenant are passed in this case
                // and we need to use that else backend export group gets visible
                // in UI as the native volume project at this point is not a VPLEX
                // project.
                createWorkflowStepsForBlockVolumeExport(workflow, vplexSystem, arrayMap,
                        volumeMap, vplexSystemProject, vplexSystemTenant, waitFor);
            } else {
                createWorkflowStepsForBlockVolumeExport(workflow, vplexSystem, arrayMap,
                        volumeMap, projectURI, tenantURI, waitFor);
            }
            String transferSize = null;
            // Get the configured migration speed. This value would be set in VPLEX through
            // "rebuild set-transfer-speed" command.
            if (setTransferSpeed != null) {
                transferSize = migrationSpeedToTransferSizeMap.get(setTransferSpeed);
                if (transferSize == null) {
                    _log.info("Transfer speed parameter {} is invalid", setTransferSpeed);
                }
            }
            if (transferSize == null) {
                String speed = customConfigHandler.getComputedCustomConfigValue(CustomConfigConstants.MIGRATION_SPEED,
                        vplexSystem.getSystemType(), null);
                _log.info("Migration speed is {}", speed);
                transferSize = migrationSpeedToTransferSizeMap.get(speed);
            }

            // Now make a Step to create the VPlex Virtual volumes.
            // This will be done from this controller.
            String stepId = workflow.createStepId();
            Workflow.Method vplexExecuteMethod = createVirtualVolumeFromImportMethod(
                    vplexVolume.getStorageController(), vplexVolumeURI, importedVolumeURI,
                    createdVolumeURI, vplexSystemProject, vplexSystemTenant, newCosURI, newLabel, transferSize);
            Workflow.Method vplexRollbackMethod = null;
            if (importedVolumeURI != null) {
                // If importing to a local/distributed virtual volume, then
                // rollback the VPlex create virtual volumes operation.
                // We will restore the original volume.
                vplexRollbackMethod = deleteVirtualVolumesMethod(vplexURI, vplexVolumeURIs, null);
            } else {
                // COP-16861: If rolling back an upgrade from local to distributed, then
                // try to detach remote mirror and delete new artifacts created on VPLEX
                // and clean up backend array volume.
                // Without this rollback method with original code, if we failed to clean-up
                // on VPLEX it used to still clean-up backed volume which would leave VPLEX
                // volume in bad state. With this rollback we will clean-up backend array only
                // if we were successful in clean-up on VPLEX.
                // We will restore the VPlex local volume.
                vplexRollbackMethod = rollbackUpgradeVirtualVolumeLocalToDistributedMethod(
                        vplexURI, vplexVolume.getDeviceLabel(), vplexVolume.getNativeId(), stepId);
            }
            workflow.createStep(
                    VPLEX_STEP,
                    String.format("VPlex %s creating virtual volume",
                            vplexSystem.getId().toString()),
                    EXPORT_STEP, vplexURI,
                    vplexSystem.getSystemType(), this.getClass(), vplexExecuteMethod,
                    vplexRollbackMethod, stepId);

            // If the imported volume is being made into a distributed volume
            // or a local VPLEX volume is being upgraded to distributed, we add
            // a step to wait for the rebuild of the remote side to complete.
            // Note there is no rollback if this fails as the distributed volume
            // has been successfully created when this step executes. We are
            // simply trying to wait until the rebuild completes.
            if (createdVolumeURI != null) {
                createWorkflowStepForWaitOnRebuild(workflow, vplexSystem,
                        vplexVolumeURI, VPLEX_STEP);
            }

            if (importedVolume != null && importedVolume.getConsistencyGroup() != null) {
                // Add virtual volume to a Vplex consistency group.
                ConsistencyGroupManager consistencyGroupManager = getConsistencyGroupManager(vplexVolume);
                List<URI> volsForCG = Arrays.asList(vplexVolumeURI);
                consistencyGroupManager.addStepsForCreateConsistencyGroup(workflow, VPLEX_STEP,
                        vplexSystem, volsForCG, false);
            }

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage;
            if (importedVolumeURI != null) {
                successMessage = "Import volume to VPLEX virtual volume successful for: " + vplexVolumeURIs.toString();
            } else {
                successMessage = "Upgrade local VPLEX volume to distributed successful for: " + vplexVolumeURIs.toString();
            }
            TaskCompleter completer = new VPlexTaskCompleter(Volume.class,
                    volumeURIs, opId, null);
            ImportRollbackHandler importRollbackHandler = new ImportRollbackHandler();
            Object[] importRollbackHandlerArgs = new Object[] { importedVolumeURI, createdVolumeURI, vplexVolumeURI };
            // TODO DUPP CWF COP-22431: This is a child workflow, needs idempotent check (1 of three flows seems like a
            // subflow)
            workflow.executePlan(completer, successMessage,
                    null, null, importRollbackHandler, importRollbackHandlerArgs);
        } catch (Exception ex) {
            ServiceError serviceError;
            if (importedVolumeURI != null) {
                _log.error("Could not import volume for vplex: " + vplexURI, ex);
                String opName = ResourceOperationTypeEnum.IMPORT_BLOCK_VOLUME.getName();
                serviceError = VPlexApiException.errors.importVolumeFailedException(opName, ex);
            } else {
                _log.error("Could not upgrade volume for vplex: " + vplexURI, ex);
                String opName = ResourceOperationTypeEnum.UPGRADE_VPLEX_LOCAL_TO_DISTRIBUTED.getName();
                serviceError = VPlexApiException.errors.upgradeLocalToDistributedFailedException(opName, ex);
            }
            TaskCompleter completer = new VPlexTaskCompleter(Volume.class,
                    volumeURIs, opId, null);
            failStep(completer, opId, serviceError);

            // Since the WF construction does not throw an exception, the code in the API
            // service that would mark prepared volumes for deletion is not invoked. As such,
            // we will do it here if the flag so indicates. In the case of importing a volume
            // for the purpose of creating a VPLEX fully copy, the flag will be false. In this
            // case only, this workflow is constructed as part of an executing step in an outer
            // workflow. If we fail here, this would cause that workflow step to fail and initiate
            // rollback in the outer workflow. In that outer workflow, there is a rollback step
            // that will mark the volumes for deletion, so in that case, we don't want to do it
            // here.
            if (markInactive) {
                // Mark the prepared VPLEX volume for deletion if this is an import
                // operation, rather than an upgrade of a local volume to distributed.
                if ((importedVolumeURI != null) && (vplexVolume != null)) {
                    _dbClient.removeObject(vplexVolume);
                }

                // For distributed volumes, mark the volume prepared for the HA side of
                // the VPLEX volume for deletion.
                if (createdVolumeURI != null) {
                    Volume createdVolume = _dbClient.queryObject(Volume.class, createdVolumeURI);
                    if (createdVolume != null) {
                        _dbClient.removeObject(createdVolume);
                    }
                }

                // Lastly we may need to mark the import volume for deletion. We only
                // need to do this when the import volume is the internal volume prepared
                // when creating a VPLEX volume from a backend snapshot. For importing
                // a non-VPLEX volume to VPLEX, this will be a public volume.
                if ((importedVolume != null) && (importedVolume.checkInternalFlags(DataObject.Flag.INTERNAL_OBJECT))) {
                    _dbClient.removeObject(importedVolume);
                }
            }
        }
    }

    static public class ImportRollbackHandler implements Workflow.WorkflowRollbackHandler, Serializable {
        @Override
        // Nothing to do.
        public
                void initiatingRollback(Workflow workflow, Object[] args) {
        }

        @Override
        public void rollbackComplete(Workflow workflow, Object[] args) {
            VPlexDeviceController.getInstance().importRollbackHandler(args);
        }
    }

    /**
     * Rollback upgrade of VPLEX local to VPLEX distributed volume.
     *
     * @param vplexURI
     *            Reference to VPLEX system
     * @param virtualVolumeName
     *            Virtual volume name which was supposed to be upgraded
     * @param virtualVolumePath
     *            Virtual volume path which was supposed to be upgraded
     * @param executeStepId
     *            step Id of the execute step; used to retrieve rollback data
     * @return workflow method
     */
    private Workflow.Method rollbackUpgradeVirtualVolumeLocalToDistributedMethod(
            URI vplexURI, String virtualVolumeName, String virtualVolumePath, String executeStepId) {
        return new Workflow.Method(RB_UPGRADE_VIRTUAL_VOLUME_LOCAL_TO_DISTRIBUUTED_METHOD_NAME, 
                vplexURI, virtualVolumeName, virtualVolumePath, executeStepId);
    }

    /**
     * Rollback upgrade of VPLEX local to VPLEX distributed volume.
     *
     * @param vplexURI
     *            Reference to VPLEX system
     * @param virtualVolumeName
     *            Virtual volume name which was supposed to be upgraded
     * @param virtualVolumePath
     *            Virtual volume path which was supposed to be upgraded
     * @param executeStepId
     *            step Id of the execute step; used to retrieve rollback data
     * @param stepId
     *            The rollback step id
     * @throws WorkflowException
     *             When an error occurs updating the workflow step state
     */
    public void rollbackUpgradeVirtualVolumeLocalToDistributed(
            URI vplexURI, String virtualVolumeName, String virtualVolumePath,
            String executeStepId, String stepId)
                    throws WorkflowException {
        try {
            VolumeInfo mirrorInfo = (VolumeInfo) _workflowService.loadStepData(executeStepId);
            if (mirrorInfo != null) {
                WorkflowStepCompleter.stepExecuting(stepId);

                // Get the API client.
                StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
                VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

                // Get the cluster id for this storage volume.
                String clusterId = client.getClaimedStorageVolumeClusterName(mirrorInfo);

                try {
                    // Try to detach mirror that might have been added.
                    client.detachMirrorFromDistributedVolume(virtualVolumeName, clusterId);

                    // Find virtual volume and its supporting device
                    VPlexVirtualVolumeInfo virtualVolumeInfo = client.findVirtualVolume(virtualVolumeName, virtualVolumePath);
                    String sourceDeviceName = virtualVolumeInfo.getSupportingDevice();

                    // Once mirror is detached we need to do device collapse so that its not seen as distributed device.
                    client.deviceCollapse(sourceDeviceName, VPlexApiConstants.DISTRIBUTED_DEVICE);

                    // Once device collapse is successful we need to set visibility of device to local because volume will be seen from
                    // other cluster still as visibility of device changes to global once mirror is attached.
                    client.setDeviceVisibility(sourceDeviceName);

                } catch (Exception e) {
                    _log.error("Exception restoring virtual volume " + virtualVolumeName + " to its original state." + e);
                    _log.info(String
                            .format("Couldn't detach mirror corresponding to the backend volume %s from the VPLEX volume %s on VPLEX cluster %s during rollback. "
                                    + "Its possible mirror was never attached, so just move on to delete backend volume artifacts from the VPLEX",
                                    mirrorInfo.getVolumeName(), virtualVolumeName, clusterId));
                }
                // Its possible that mirror was never attached so we will try to delete the device even if we fail to
                // detach a mirror.
                // If mirror device is still attached this will anyway fail, so its safe to make this call.
                client.deleteLocalDevice(mirrorInfo);
            }

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (VPlexApiException vae) {
            _log.error("Exception rollback VPlex Virtual Volume upgrade from local to distributed : " + vae.getLocalizedMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception rollback VPlex Virtual Volume upgrade from local to distributed : " + ex.getLocalizedMessage(), ex);
            ServiceError serviceError = VPlexApiException.errors.createVirtualVolumesRollbackFailed(stepId, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    /**
     * Handles completion of an import Rollback.
     *
     * @param args
     *            -- Object[] that must match importRollbackHandlerArgs above.
     */
    public void importRollbackHandler(Object[] args) {
        URI importVolumeURI = (URI) args[0];
        URI createVolumeURI = (URI) args[1];
        URI vplexVolumeURI = (URI) args[2];

        Volume vplexVolume = _dbClient.queryObject(Volume.class, vplexVolumeURI);
        if (importVolumeURI != null) {
            // Importing... mark the VPlex volume for deletion.
            _dbClient.removeObject(vplexVolume);
        } else {
            // Upgrading... nothing to do.
        }
    }

    private Workflow.Method createVirtualVolumeFromImportMethod(URI vplexURI,
            URI vplexVolumeURI, URI existingVolumeURI, URI newVolumeURI,
            URI vplexSystemProject, URI vplexSystemTenant, URI newCosURI, String newLabel,
            String transferSize) {
        return new Workflow.Method(CREATE_VIRTUAL_VOLUME_FROM_IMPORT_METHOD_NAME,
                vplexURI, vplexVolumeURI, existingVolumeURI, newVolumeURI,
                vplexSystemProject, vplexSystemTenant, newCosURI, newLabel, transferSize);
    }

    /**
     * Create a Virtual Volume from an Imported Volume.
     * There are three cases here:
     * 1. We want to create a non-distributed virtual volume. In this case,
     * there is an existingVolume, but newVolume == null.
     * 2. We want to create a distributed virtual volume from an existing volume,
     * and then add a mirror to a new volume (in the other varray).
     * In this case, both existingVolume and newVolume are non-null.
     * 3. We had an existing Virtual volume, and we only want to upgrade it
     * to a distributed Virtual Volume (existingVolumeURI == null).
     *
     * @param vplexURI
     * @param vplexVolumeURI
     * @param existingVolumeURI
     * @param newVolumeURI
     * @param vplexSystemProject
     * @param vplexSystemTenant
     * @param newCosURI
     * @param newLabel
     * @param stepId
     * @throws WorkflowException
     */
    public void createVirtualVolumeFromImportStep(URI vplexURI, URI vplexVolumeURI,
            URI existingVolumeURI, URI newVolumeURI, URI vplexSystemProject,
            URI vplexSystemTenant, URI newCosURI, String newLabel, String transferSize,
            String stepId)
                    throws WorkflowException {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            // Get the API client.
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
            // Get the three volumes.
            Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
            Volume existingVolume = null;
            Volume newVolume = null;
            if (existingVolumeURI != null) {
                existingVolume = getDataObject(Volume.class, existingVolumeURI, _dbClient);
            }
            if (newVolumeURI != null) {
                newVolume = getDataObject(Volume.class, newVolumeURI, _dbClient);
            }

            VPlexVirtualVolumeInfo virtvinfo = null;
            VolumeInfo vinfo = null;

            // Make the call to create the (non-distributed) virtual volume.
            if (existingVolume != null) {
                StorageSystem array = getDataObject(StorageSystem.class, existingVolume.getStorageController(), _dbClient);
                List<String> itls = VPlexControllerUtils.getVolumeITLs(existingVolume);
                List<VolumeInfo> vinfos = new ArrayList<VolumeInfo>();
                VirtualPool newVirtualPool = getDataObject(VirtualPool.class, newCosURI, _dbClient);
                boolean thinEnabled = VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(
                        newVirtualPool.getSupportedProvisioningType());
                vinfo = new VolumeInfo(array.getNativeGuid(), array.getSystemType(),
                        existingVolume.getWWN().toUpperCase().replaceAll(":", ""),
                        existingVolume.getNativeId(), thinEnabled, itls);
                vinfos.add(vinfo);
                thinEnabled = thinEnabled && verifyVplexSupportsThinProvisioning(vplex);
                virtvinfo = client.createVirtualVolume(vinfos, false, true, true, null, null, true, thinEnabled, true);
                // Note: According to client.createVirtualVolume code, this will never be the case (null)
                if (virtvinfo == null) {
                    String opName = ResourceOperationTypeEnum.CREATE_VVOLUME_FROM_IMPORT.getName();
                    ServiceError serviceError = VPlexApiException.errors.createVirtualVolumeFromImportStepFailed(opName);
                    WorkflowStepCompleter.stepFailed(stepId, serviceError);
                    return;
                }
                _log.info(String.format("Created virtual volume: %s path: %s thinEnabled: %b",
                        virtvinfo.getName(), virtvinfo.getPath(), virtvinfo.isThinEnabled()));

                checkThinEnabledResult(virtvinfo, thinEnabled, _workflowService.getWorkflowFromStepId(stepId).getOrchTaskId());

                // If the imported volume will be distributed, we must
                // update the virtual volume device label and native Id.
                // This must be done because if the update to distributed
                // fails, we rollback the create of this local virtual
                // and the volume will not be deleted if the native id is
                // not set.
                if (newVolume != null) {
                    vplexVolume.setNativeId(virtvinfo.getPath());
                    vplexVolume.setNativeGuid(virtvinfo.getPath());
                    vplexVolume.setDeviceLabel(virtvinfo.getName());
                    vplexVolume.setThinlyProvisioned(virtvinfo.isThinEnabled());
                    _dbClient.updateObject(vplexVolume);
                }
            } else {
                virtvinfo = client.findVirtualVolume(vplexVolume.getDeviceLabel(), vplexVolume.getNativeId());
            }

            // If we desired a distributed virtual-volume (newVolume != null),
            // now create a mirror to the new volume.
            if (newVolume != null) {
                String clusterId = ConnectivityUtil.getVplexClusterForVarray(
                        vplexVolume.getVirtualArray(), vplexVolume.getStorageController(),
                        _dbClient);
                StorageSystem array = getDataObject(StorageSystem.class, newVolume.getStorageController(), _dbClient);
                List<String> itls = VPlexControllerUtils.getVolumeITLs(newVolume);
                vinfo = new VolumeInfo(array.getNativeGuid(), array.getSystemType(),
                        newVolume.getWWN().toUpperCase().replaceAll(":", ""),
                        newVolume.getNativeId(), newVolume.getThinlyProvisioned().booleanValue(), itls);
                // Add rollback data.
                _workflowService.storeStepData(stepId, vinfo);
                virtvinfo = client.upgradeVirtualVolumeToDistributed(virtvinfo, vinfo, true, clusterId, transferSize);
                if (virtvinfo == null) {
                    String opName = ResourceOperationTypeEnum.UPGRADE_VPLEX_LOCAL_TO_DISTRIBUTED.getName();
                    ServiceError serviceError = VPlexApiException.errors.upgradeLocalToDistributedFailed(opName);
                    WorkflowStepCompleter.stepFailed(stepId, serviceError);
                    return;
                }
            }

            // Update the virtual volume device label and native Id.
            // Also make sure the WWN is set.
            vplexVolume.setNativeId(virtvinfo.getPath());
            vplexVolume.setNativeGuid(virtvinfo.getPath());
            vplexVolume.setDeviceLabel(virtvinfo.getName());
            vplexVolume.setThinlyProvisioned(virtvinfo.isThinEnabled());
            vplexVolume.setWWN(virtvinfo.getWwn());

            // If we are importing, we need to move the existing import volume to
            // the system project/tenant, update its label, and set the new CoS.
            Volume srcSideAssocVolume = null;
            if (existingVolume != null) {
                srcSideAssocVolume = existingVolume;
                existingVolume.setProject(new NamedURI(vplexSystemProject, existingVolume.getLabel()));
                existingVolume.setTenant(new NamedURI(vplexSystemTenant, existingVolume.getLabel()));
                existingVolume.setLabel(newLabel);
                existingVolume.setVirtualPool(newCosURI);
                existingVolume.addInternalFlags(Flag.INTERNAL_OBJECT);
                _dbClient.updateObject(existingVolume);

                // If the VPLEX volume is being upgraded to distributed, it's provisioned
                // should be set and does not change. However, when importing an existing
                // volume to a VPLEX volume, we need to set the provisioned capacity
                // of the VPLEX volume to the provisioned capacity of the existing volume.
                vplexVolume.setProvisionedCapacity(existingVolume.getProvisionedCapacity());

                // For Vplex virtual volumes set allocated capacity to 0 (cop-18608)
                vplexVolume.setAllocatedCapacity(0L);

                // For import associated with creating a VPLEX full copy, we need
                // to add the copy to the list of copies for the source VPLEX volume.
                // We only do this when the copy is successfully completed.
                URI srcVplexVolumeURI = vplexVolume.getAssociatedSourceVolume();
                if (!NullColumnValueGetter.isNullURI(srcVplexVolumeURI)) {
                    // Note that the associated source volume will be null if
                    // this is just a standard import of a non-VPLEX volume. It
                    // will be set in the case we use the import workflow to
                    // import a native copy to a VPLEX volume for the purpose
                    // of creating a full copy.
                    Volume srcVplexVolume = _dbClient.queryObject(Volume.class, srcVplexVolumeURI);
                    if (null != srcVplexVolume) {
                        StringSet srcVplexVolumeCopies = srcVplexVolume.getFullCopies();
                        if (srcVplexVolumeCopies == null) {
                            srcVplexVolumeCopies = new StringSet();
                            srcVplexVolume.setFullCopies(srcVplexVolumeCopies);
                        }
                        srcVplexVolumeCopies.add(vplexVolumeURI.toString());
                        _dbClient.updateObject(srcVplexVolume);
                    }

                    // Also, reflect the replica state in the vplex copy.
                    vplexVolume.setReplicaState(existingVolume.getReplicaState());
                }
            } else {
                // For upgrading local to distributed, set the association in the distributed
                // and update the CoS.
                for (String assocVolume : vplexVolume.getAssociatedVolumes()) {
                    try {
                        srcSideAssocVolume = _dbClient.queryObject(Volume.class, new URI(assocVolume));
                        srcSideAssocVolume.setVirtualPool(newCosURI);
                        _dbClient.updateObject(srcSideAssocVolume);
                    } catch (URISyntaxException ex) {
                        _log.error("Bad assocVolume URI: " + assocVolume, ex);
                    }
                }
                vplexVolume.getAssociatedVolumes().add(newVolumeURI.toString());
                vplexVolume.setVirtualPool(newCosURI);
            }

            // Set custom name if custom naming is enabled and this is not an upgrade from local to distributed.
            // If this is a simple upgrade from local to distributed and the volume has a custom name, then the
            // name would not change. However whenever we are importing, a new VPLEX virtual volume is being
            // created and we need to make sure it has the correct name.
            try {
                if ((CustomVolumeNamingUtils.isCustomVolumeNamingEnabled(customConfigHandler, vplex.getSystemType())) &&
                        (existingVolume != null)) {
                    // Create the VPLEX volume name custom configuration datasource and generate the
                    // custom volume name.
                    String customConfigName = CustomVolumeNamingUtils.getCustomConfigName(false);
                    Project project = getDataObject(Project.class, vplexVolume.getProject().getURI(), _dbClient);
                    TenantOrg tenant = getDataObject(TenantOrg.class, vplexVolume.getTenant().getURI(), _dbClient);
                    DataSource customNameDataSource = CustomVolumeNamingUtils.getCustomConfigDataSource(project, tenant,
                            vplexVolume.getLabel(), vplexVolume.getWWN(), null, dataSourceFactory, customConfigName,
                            _dbClient);
                    if (customNameDataSource != null) {
                        String customVolumeName = CustomVolumeNamingUtils.getCustomName(customConfigHandler,
                                customConfigName, customNameDataSource, vplex.getSystemType());
                        virtvinfo = CustomVolumeNamingUtils.renameVolumeOnVPlex(virtvinfo, customVolumeName, client);
                        vplexVolume.setNativeId(virtvinfo.getPath());
                        vplexVolume.setNativeGuid(virtvinfo.getPath());
                        vplexVolume.setDeviceLabel(virtvinfo.getName());
                        vplexVolume.setLabel(virtvinfo.getName());

                        // Also, we update the name portion of the project and tenant URIs
                        // to reflect the custom name. This is necessary because the API
                        // to search for volumes by project, extracts the name portion of the
                        // project URI to get the volume name.
                        NamedURI namedURI = vplexVolume.getProject();
                        namedURI.setName(virtvinfo.getName());
                        vplexVolume.setProject(namedURI);
                        namedURI = vplexVolume.getTenant();
                        namedURI.setName(virtvinfo.getName());
                        vplexVolume.setTenant(namedURI);
                    }
                }
            } catch (Exception e) {
                _log.warn(String.format("Error attempting to rename VPLEX volume %s", vplexVolumeURI), e);
            }

            // Update the volume.
            _dbClient.updateObject(vplexVolume);

            // Complete the workflow step.
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (VPlexApiException vae) {
            if (existingVolumeURI != null) {
                _log.error("Exception importing non-VPLEX volume to VPLEX: " + vae.getMessage(), vae);
            } else {
                _log.error("Exception upgrading a local VPLEX volume to distributed: " + vae.getMessage(), vae);
            }
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            ServiceError serviceError;
            if (existingVolumeURI != null) {
                _log.error("Exception importing non-VPLEX volume to VPLEX: " + ex.getMessage(), ex);
                String opName = ResourceOperationTypeEnum.IMPORT_BLOCK_VOLUME.getName();
                serviceError = VPlexApiException.errors.importVolumeFailedException(opName, ex);
            } else {
                _log.error("Exception upgrading a local VPLEX volume to distributed: " + ex.getMessage(), ex);
                String opName = ResourceOperationTypeEnum.UPGRADE_VPLEX_LOCAL_TO_DISTRIBUTED.getName();
                serviceError = VPlexApiException.errors.upgradeLocalToDistributedFailedException(opName, ex);
            }
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    /**
     * Creates a workflow step in the passed workflow to wait for
     * the VPLEX distributed volume with the passed URI to rebuild.
     *
     * @param workflow
     *            A reference to a workflow.
     * @param vplexSystem
     *            A reference to a VPLEX storage system.
     * @param vplexVolumeURI
     *            The URI of the distributed virtual volume.
     * @param waitFor
     *            The step to wait for or null.
     */
    private String createWorkflowStepForWaitOnRebuild(Workflow workflow,
            StorageSystem vplexSystem, URI vplexVolumeURI, String waitFor) {
        Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
        URI vplexSystemURI = vplexSystem.getId();
        Workflow.Method rebuildExecuteMethod = createWaitOnRebuildMethod(vplexSystemURI,
                vplexVolumeURI);
        workflow.createStep(WAIT_ON_REBUILD_STEP, String.format(
                "Waitng for volume %s (%s) to rebuild", vplexVolume.getLabel(), vplexVolume.getId()),
                waitFor, vplexSystemURI, vplexSystem.getSystemType(), this.getClass(),
                rebuildExecuteMethod, null, null);

        return WAIT_ON_REBUILD_STEP;
    }

    /**
     * Creates the waitOnRebuild workflow step execution method.
     *
     * @param vplexURI
     *            The URI of the VPLEX system.
     * @param vplexVolumeURI
     *            The URI of the VPLEX volume.
     *
     * @return A reference to the workflow step execution method.
     */
    private Workflow.Method createWaitOnRebuildMethod(URI vplexURI, URI vplexVolumeURI) {
        return new Workflow.Method(WAIT_ON_REBUILD_METHOD_NAME, vplexURI, vplexVolumeURI);
    }

    /**
     * Waits for the rebuild to complete when a non-VPLEX volume is imported to a VPLEX
     * distribute volume or a local VPLEX volume is upgraded to distributed.
     *
     * @param vplexURI
     *            The URI of the VPLEX system.
     * @param vplexVolumeURI
     *            The URI of the VPLEX volume.
     * @param stepId
     *            The workflow step identifier.
     *
     * @throws WorkflowException
     */
    public void waitOnRebuild(URI vplexURI, URI vplexVolumeURI, String stepId)
            throws WorkflowException {

        String volumeName = null;
        try {
            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Get the API client.
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

            // Get the VPLEX volume that is rebuilding.
            Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
            volumeName = vplexVolume.getLabel();

            // Check the rebuild status until it completes, fails, or we timeout waiting.
            WaitOnRebuildResult rebuildResult = client.waitOnRebuildCompletion(vplexVolume.getDeviceLabel());
            _log.info(String.format("Finished waiting on rebuild for virtual volume: %s path: %s rebuild-status: %s",
                    vplexVolume.getDeviceLabel(), vplexVolume.getNativeId(), rebuildResult.name()));

            if (WaitOnRebuildResult.SUCCESS == rebuildResult) {
                WorkflowStepCompleter.stepSucceded(stepId);
            } else {
                ServiceError serviceError;
                if (WaitOnRebuildResult.FAILED == rebuildResult) {
                    serviceError = VPlexApiException.errors.waitOnRebuildFailed(volumeName);
                } else if (WaitOnRebuildResult.TIMED_OUT == rebuildResult) {
                    serviceError = VPlexApiException.errors.waitOnRebuildTimedOut(volumeName);
                } else {
                    serviceError = VPlexApiException.errors.waitOnRebuildInvalid(volumeName);
                }
                WorkflowStepCompleter.stepFailed(stepId, serviceError);
            }
        } catch (VPlexApiException vae) {
            _log.error("Exception checking the rebuild status: " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception checking the rebuild status: " + ex.getMessage(), ex);
            ServiceError serviceError = VPlexApiException.errors.waitOnRebuildException(volumeName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    /**
     * This method will add unexport steps to the passed in workflow. It will create
     * the workflow under the specified group ID and if not null,
     * it will be run after the the 'waitFor' workflow completes.
     *
     *
     * @param subWorkflow
     *            [in] - Workflow to which the unexport steps will be added.
     * @param wfGroupId
     *            [in] - Workflow group ID
     * @param waitFor
     *            [in] - Workflow group ID to wait on before the unexport is run
     * @param uris
     *            [in] - List of volume URIs that will be removed from
     *            exports @throws Exception
     * @param exportGroupTracker
     *            [in] - (Optional) If non-null,
     *            will be populated with the ExportGroup URIs that were
     *            found to be associated with any of the volumes
     * @return boolean - true if there were any remove-volumes-from-export steps added
     *         to the workflow
     *
     */
    private boolean vplexAddUnexportVolumeWfSteps(Workflow subWorkflow,
            String waitFor, List<URI> uris, List<URI> exportGroupTracker)
                    throws Exception {
        boolean workflowStepsAdded = false;
        // Main processing containers. ExportGroup --> StorageSystem --> Volumes
        // Populate the container for the export workflow step generation
        Map<URI, Map<URI, List<URI>>> exportStorageVolumeMap = new HashMap<URI, Map<URI, List<URI>>>();
        for (URI uri : uris) {
            _log.info("Volume URI is {}", uri);
            Volume volume = _dbClient.queryObject(Volume.class, uri);
            if (volume == null || volume.getInactive()) {
                continue;
            }
            URI sourceSystemURI = volume.getStorageController();
            _log.info("Storage system URI is {}", sourceSystemURI);
            List<ExportGroup> sourceExportGroups = getExportGroupsForVolume(volume);
            for (ExportGroup sourceExportGroup : sourceExportGroups) {
                URI sourceExportGroupURI = sourceExportGroup.getId();
                _log.info("Export group URI is {}", sourceExportGroupURI);
                if (exportGroupTracker != null) {
                    exportGroupTracker.add(sourceExportGroupURI);
                }

                Map<URI, List<URI>> storageToVolumes = exportStorageVolumeMap.get(sourceExportGroupURI);
                if (storageToVolumes == null) {
                    storageToVolumes = new HashMap<URI, List<URI>>();
                    exportStorageVolumeMap.put(sourceExportGroupURI,
                            storageToVolumes);
                }

                List<URI> volumeURIs = storageToVolumes.get(sourceSystemURI);
                if (volumeURIs == null) {
                    volumeURIs = new ArrayList<URI>();
                    storageToVolumes.put(sourceSystemURI, volumeURIs);
                }
                volumeURIs.add(uri);
            }
        }

        for (URI exportGroupURI : exportStorageVolumeMap.keySet()) {
            for (URI storageURI : exportStorageVolumeMap.get(exportGroupURI).keySet()) {
                StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
                List<URI> volumes = exportStorageVolumeMap.get(exportGroupURI).get(storageURI);
                if (volumes.isEmpty()) {
                    _log.info(String.format(
                            "Not generating workflow steps to unexport backend volumes in ExportGroup %s because no volumes were found",
                            exportGroupURI.toString()));
                    continue; // CTRL 12474: Do not generate steps if there are no volumes to process
                }
                VPlexBackendManager backendMgr = new VPlexBackendManager(_dbClient, this,
                        _blockDeviceController, _blockScheduler, _networkDeviceController,
                        null, null, _vplexApiLockManager, coordinator);
                boolean stepsAdded = backendMgr.addWorkflowStepsToRemoveBackendVolumes(subWorkflow,
                        waitFor, storage, exportGroupURI, volumes);
                if (stepsAdded) {
                    workflowStepsAdded = true;
                }
            }
        }

        return workflowStepsAdded;
    }

    /*
     * (non-Javadoc)
     * Method to add workflow steps for VPLEX volume expansion
     */
    @Override
    public String addStepsForExpandVolume(
            Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId)
                    throws InternalException {

        List<VolumeDescriptor> vplexVolumeDescriptors = VolumeDescriptor
                .filterByType(volumeDescriptors,
                        new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_VIRT_VOLUME,
                                VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE,
                                VolumeDescriptor.Type.RP_VPLEX_VIRT_TARGET,
                                VolumeDescriptor.Type.RP_EXISTING_SOURCE },
                        new VolumeDescriptor.Type[] {});

        if (vplexVolumeDescriptors == null || vplexVolumeDescriptors.isEmpty()) {
            return waitFor;
        }

        for (VolumeDescriptor descriptor : vplexVolumeDescriptors) {
            Volume vplexVolume = _dbClient.queryObject(Volume.class, descriptor.getVolumeURI());
            URI vplexURI = vplexVolume.getStorageController();
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);

            Workflow.Method expandVolumeNativelyMethod = new Workflow.Method(
                    EXPAND_VOLUME_NATIVELY_METHOD_NAME, vplexURI, vplexVolume.getId(), descriptor.getVolumeSize());

            String createStepId = workflow.createStepId();
            workflow.createStep(VIRTUAL_VOLUME_EXPAND_STEP, String.format(
                    "Expand virtual volume %s for VPlex volume %s", vplexVolume.getId(), vplexVolume.getId()), waitFor,
                    vplexURI, vplexSystem.getSystemType(),
                    this.getClass(), expandVolumeNativelyMethod,
                    null, createStepId);
        }

        return VIRTUAL_VOLUME_EXPAND_STEP;
    }

    public void expandVolumeNatively(URI vplexURI, URI vplexVolumeURI, Long newSize,
            String opId) throws ControllerException {

        _log.info("VPlex controller expand volume {}", vplexVolumeURI);

        try {
            Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
            List<URI> assocVolumeURIs = new ArrayList<URI>();
            StringSet assocVolumeIds = vplexVolume.getAssociatedVolumes();
            if (null == vplexVolume.getAssociatedVolumes() || vplexVolume.getAssociatedVolumes().isEmpty()) {
                _log.error("VPLEX volume {} has no backend volumes.", vplexVolume.forDisplay());
                throw InternalServerErrorException.internalServerErrors.noAssociatedVolumesForVPLEXVolume(vplexVolume.forDisplay());
            }
            for (String assocVolumeId : assocVolumeIds) {
                assocVolumeURIs.add(URI.create(assocVolumeId));
            }

            // Now invoke the expansion of the
            // VPlex volume once the native volume expansion completes.
            // We'll need the native guids of the backend volume storage
            // systems to rediscover the newly expanded volume size.
            List<String> systemNativeGuids = new ArrayList<String>();
            Iterator<URI> assocVolumeURIsIter = assocVolumeURIs.iterator();
            while (assocVolumeURIsIter.hasNext()) {
                URI assocVolumeURI = assocVolumeURIsIter.next();
                Volume volume = getDataObject(Volume.class, assocVolumeURI, _dbClient);
                URI storageSystemURI = volume.getStorageController();
                StorageSystem storageSystem = getDataObject(StorageSystem.class,
                        storageSystemURI, _dbClient);
                systemNativeGuids.add(storageSystem.getNativeGuid());
            }

            if (vplexVolume.getProvisionedCapacity() != null
                    && newSize > vplexVolume.getProvisionedCapacity()) {
                expandVirtualVolume(vplexURI, vplexVolumeURI, newSize, systemNativeGuids, opId);
                _log.info(String.format(
                        "Executed vplex virtual volume expansion. Expanded capacity to: %s", newSize));
            } else {
                _log.info(String
                        .format("Vplex virtual volume expansion not required.  Existing provisioned capacity: %s, Requested expansion capacity: %s",
                                vplexVolume.getProvisionedCapacity(), newSize));
                // Update step status to success since nothing has to be done.
                WorkflowStepCompleter.stepSucceded(opId);
            }
        } catch (Exception e) {
            String failMsg = String.format("Expansion of volume %s failed", vplexVolumeURI);
            _log.error(failMsg, e);
            TaskCompleter completer = new VPlexTaskCompleter(Volume.class,
                    Arrays.asList(vplexVolumeURI), opId, null);
            String opName = ResourceOperationTypeEnum.EXPAND_BLOCK_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.expandVolumeNativelyFailed(
                    vplexVolumeURI.toString(), opName, e);
            failStep(completer, opId, serviceError);
        }
    }

    /**
     * Creates the workflow step to expand the virtual volume.
     *
     * @param workflow
     *            The workflow.
     * @param vplexSystem
     *            The VPlex storage system.
     * @param vplexVolumeURI
     *            The URI of the VPlex volume
     * @param newSize
     *            The new requested volume size.
     * @param systemNativeGuids
     *            The URIs of the backend storage systems, or
     *            null.
     * @param waitFor
     *            The step to wait for.
     *
     * @throws WorkflowException
     *             When an error occurs creating the workflow
     *             step.
     */
    private void createWorkflowStepForVirtualVolumeExpansion(Workflow workflow,
            StorageSystem vplexSystem, URI vplexVolumeURI, Long newSize,
            List<String> systemNativeGuids, String waitFor) throws WorkflowException {
        URI vplexURI = vplexSystem.getId();
        Workflow.Method vplexExecuteMethod = new Workflow.Method(
                EXPAND_VIRTUAL_VOLUME_METHOD_NAME, vplexURI, vplexVolumeURI, newSize,
                systemNativeGuids);
        workflow.createStep(VIRTUAL_VOLUME_EXPAND_STEP, String.format(
                "Expanding volume %s on VPlex system %s", vplexVolumeURI, vplexURI),
                waitFor, vplexURI, vplexSystem.getSystemType(), getClass(),
                vplexExecuteMethod, null, null);
        _log.info("Created step for virtual volume expansion");
    }

    /**
     * Expands the virtual volume with the passed URI to it full expandable capacity.
     *
     * @param vplexSystemURI
     *            The URI of the VPlex system.
     * @param vplexVolumeURI
     *            The URI of the VPlex volume to expand.
     * @param newSize
     *            The new requested volume size.
     * @param systemNativeGuids
     *            The URIs of the backend storage systems, or null
     * @param stepId
     *            The workflow step identifier.
     *
     * @throws WorkflowException
     *             When an error occurs updating the work step state.
     */
    public void expandVirtualVolume(URI vplexSystemURI, URI vplexVolumeURI, Long newSize,
            List<String> systemNativeGuids, String stepId) throws WorkflowException {
        try {
            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Get the virtual volume.
            Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
            String vplexVolumeName = vplexVolume.getDeviceLabel();
            _log.info("Virtual volume name is {}", vplexVolumeName);

            // Get the VPlex API client.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexSystemURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexSystem, _dbClient);
            _log.info("Got VPlex API client for VPlex system {}", vplexSystemURI);

            // If the nativeGuids are passed, rediscover first. When
            // expanding natively, we need to rediscover the backend
            // arrays to pick up the new expanded volume size.
            if (systemNativeGuids != null) {
                client.rediscoverStorageSystems(systemNativeGuids);
                // Sleep for a bit to be sure the VPlex completes the
                // discovery prior to calling expand. Gets around
                // an issue with the VPlex software not returning an
                // Async code.
                try {
                    Thread.sleep(60000);
                } catch (Exception e) {
                    // ignore exceptions
                    _log.warn("thread sleep exception " + e.getLocalizedMessage());
                }
            }

            // Make a call to the VPlex API client to expand the virtual
            // volume.

            int expansionStatusRetryCount = Integer.valueOf(ControllerUtils.getPropertyValueFromCoordinator(
                    coordinator, VPlexApiConstants.EXPANSION_STATUS_RETRY_COUNT));
            long expansionStatusSleepTime = Long.valueOf(ControllerUtils.getPropertyValueFromCoordinator(
                    coordinator, VPlexApiConstants.EXPANSION_STATUS_SLEEP_TIME_MS));
            VPlexVirtualVolumeInfo vplexVolumeInfo = client.expandVirtualVolume(vplexVolumeName,
                    expansionStatusRetryCount, expansionStatusSleepTime);
            _log.info("Completed VPlex volume expansion");

            // Update the VPlex volume size in the database.
            vplexVolume.setCapacity(newSize);
            vplexVolume.setProvisionedCapacity(vplexVolumeInfo.getCapacityBytes());
            // For Vplex virtual volumes set allocated capacity to 0 (cop-18608)
            vplexVolume.setAllocatedCapacity(0L);
            _dbClient.updateObject(vplexVolume);
            _log.info("Updated volume size");

            // Update step status to success.
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (VPlexApiException vae) {
            _log.error("Exception expanding VPlex virtual volume: " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception expanding VPlex virtual volume: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.EXPAND_VIRTUAL_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.expandVirtualVolumeFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expandVolumeUsingMigration(URI vplexURI, URI vplexVolumeURI,
            List<URI> targetVolumeURIs, Map<URI, URI> migrationsMap,
            Map<URI, URI> poolVolumeMap, Long newSize, String opId)
                    throws ControllerException {

        try {
            // Get the VPlex System.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);

            // Get a new workflow to execute the migrations.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    EXPAND_VOLUME_WF_NAME, false, opId);
            _log.info("Created new expansion workflow with operation id {}", opId);

            // Create a workflow step to create and execute the migration
            // workflow.
            Workflow.Method createMigrationMethod = new Workflow.Method(
                    EXPANSION_MIGRATION_METHOD_NAME, vplexURI, vplexVolumeURI,
                    targetVolumeURIs, migrationsMap, poolVolumeMap, opId);
            workflow.createStep(EXPANSION_MIGRATION_STEP,
                    String.format("Migrate VPlex volume %s", vplexVolumeURI), null,
                    vplexURI, vplexSystem.getSystemType(), getClass(),
                    createMigrationMethod, null, null);
            _log.info("Created step for configuring the migration sub workflow.");

            // Now create a workflow step to invoke the expansion of the
            // VPlex volume once the native volume expansion completes.
            createWorkflowStepForVirtualVolumeExpansion(workflow, vplexSystem,
                    vplexVolumeURI, newSize, null, EXPANSION_MIGRATION_STEP);

            List<URI> volumes = new ArrayList<URI>();
            volumes.add(vplexVolumeURI);
            volumes.addAll(targetVolumeURIs);
            TaskCompleter completer = new VPlexTaskCompleter(Volume.class,
                    volumes, opId, null);
            _log.info("Executing workflow plan");
            workflow.executePlan(completer, String.format(
                    "Expansion of volume %s completed successfully", vplexVolumeURI));
            _log.info("Workflow plan executed");
        } catch (Exception e) {
            String failMsg = String.format("Expansion of volume %s failed", vplexVolumeURI);
            _log.error(failMsg, e);
            List<URI> volumes = new ArrayList<URI>();
            volumes.add(vplexVolumeURI);
            volumes.addAll(targetVolumeURIs);
            TaskCompleter completer = new VPlexTaskCompleter(Volume.class,
                    volumes, opId, null);
            String opName = ResourceOperationTypeEnum.EXPAND_BLOCK_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.expandVolumeUsingMigrationFailed(
                    vplexVolumeURI.toString(), opName, e);
            failStep(completer, opId, serviceError);
        }
    }

    /**
     * Migrate volumes to new targets for the purpose of expanding the volumes.
     *
     * @param vplexSystemURI
     *            The URI of the VPlex storage system.
     * @param vplexVolumeURI
     *            The URI of the VPlex volume.
     * @param targetVolumeURIs
     *            The URIs of the volume(s) to which the data is
     *            migrated.
     * @param migrationsMap
     *            The URIs of the migrations keyed by target volume.
     * @param poolVolumeMap
     *            The pool map keys specify the storage pools on which
     *            the target volumes should be created, while the values
     *            specify the target volumes to be created on a given pool.
     * @param opid
     *            The task operation id.
     * @param stepId
     *            The workflow step id.
     *
     * @throws ControllerException
     *             When an error occurs migrating the volumes.
     */
    public void migrateVolumeForExpansion(URI vplexSystemURI, URI vplexVolumeURI,
            List<URI> targetVolumeURIs, Map<URI, URI> migrationsMap,
            Map<URI, URI> poolVolumeMap, String opId, String stepId) throws ControllerException {

        // Update step state to executing.
        WorkflowStepCompleter.stepExecuting(stepId);

        // Setup the migration success/failure messages.
        String successMsg = String.format("Virtual volume %s migrated successfully",
                vplexVolumeURI);
        String failMsg = String.format("Virtual volume %s migration failed",
                vplexVolumeURI);

        // Reuse the migrate volumes API to create a sub workflow to execute
        // the migrations.
        migrateVolumes(vplexSystemURI, vplexVolumeURI, targetVolumeURIs, migrationsMap,
                poolVolumeMap, null, null, successMsg, failMsg, null, opId, stepId);
        _log.info("Created and started sub workflow to execute the migration");
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
                failStep(taskCompleter, token, serviceError);
            } else {
                throw DeviceControllerException.exceptions.exportGroupUpdateFailed(ex);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteConsistencyGroup(URI vplexURI, URI cgURI, String opId)
            throws ControllerException {

        // Get a new workflow to execute the CG deletion.
        Workflow workflow = _workflowService.getNewWorkflow(this, DELETE_CG_WF_NAME,
                false, opId);
        _log.info("Created new delete CG workflow with operation id {}", opId);

        BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, _dbClient);

        if (cg != null) {
            // Get a reference to the ConsistencyGroupManager and delete the consistency group
            ConsistencyGroupManager consistencyGroupManager = getConsistencyGroupManager(cg);

            if (consistencyGroupManager != null) {
                consistencyGroupManager.deleteConsistencyGroup(workflow, vplexURI, cgURI, opId);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateConsistencyGroup(URI vplexURI, URI cgURI, List<URI> addVolumesList,
            List<URI> removeVolumesList, String opId) throws ControllerException {
        _log.info("Update CG {} on VPLEX {}", cgURI, vplexURI);

        // Get a new workflow to execute the CG update.
        Workflow workflow = _workflowService.getNewWorkflow(this, UPDATE_CG_WF_NAME,
                false, opId);
        _log.info("Created new update CG workflow with operation id {}", opId);

        BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, _dbClient);
        ConsistencyGroupManager consistencyGroupManager = null;
        if (cg != null) {
            // Get a reference to the ConsistencyGroupManager and delete the consistency group
            if (cg.created()) {
                consistencyGroupManager = getConsistencyGroupManager(cg);
            } else if (!cg.created() && addVolumesList != null && !addVolumesList.isEmpty()) {
                // Check on volumes to get the right consistency group manager
                Volume volume = _dbClient.queryObject(Volume.class, addVolumesList.get(0));
                consistencyGroupManager = getConsistencyGroupManager(volume);
            }
        }

        if (consistencyGroupManager != null) {
            consistencyGroupManager.updateConsistencyGroup(workflow, vplexURI,
                    cgURI, addVolumesList, removeVolumesList, opId);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createFullCopy(URI vplexURI, List<VolumeDescriptor> volumeDescriptors,
            String opId) throws ControllerException {
        _log.info("Copy volumes on VPLEX", vplexURI);

        // When we copy a VPLEX virtual volume we natively copy the primary backend
        // volume of the virtual volume. This primary copy is then imported to the
        // VPLEX as a local or distributed virtual volume depending upon the type of
        // the VPLEX volume being copied. Note that we could be creating multiple
        // copies of a single source volume that is not in a consistency group or
        // creating a single copy of multiple source volumes which are in a consistency
        // group.
        URI vplexSrcVolumeURI = null;
        List<URI> vplexCopyVolumesURIs = new ArrayList<URI>();
        TaskCompleter completer = null;
        try {
            // Set up the task completer
            List<VolumeDescriptor> vplexVolumeDescrs = VolumeDescriptor
                    .filterByType(volumeDescriptors,
                            new VolumeDescriptor.Type[] { Type.VPLEX_VIRT_VOLUME },
                            new VolumeDescriptor.Type[] {});
            List<VolumeDescriptor> vplexSrcVolumeDescs = getDescriptorsForFullCopySrcVolumes(vplexVolumeDescrs);
            vplexVolumeDescrs.removeAll(vplexSrcVolumeDescs); // VPLEX copy volumes
            vplexCopyVolumesURIs = VolumeDescriptor.getVolumeURIs(vplexVolumeDescrs);
            completer = new VPlexTaskCompleter(Volume.class, vplexCopyVolumesURIs, opId, null);

            Volume firstFullCopy = _dbClient.queryObject(Volume.class, vplexCopyVolumesURIs.get(0));
            URI sourceVolume = firstFullCopy.getAssociatedSourceVolume();
            Volume source = URIUtil.isType(sourceVolume, Volume.class) ? _dbClient.queryObject(Volume.class, sourceVolume) : null;
            VolumeGroup volumeGroup = (source != null) ? source.getApplication(_dbClient) : null;
            if (volumeGroup != null
                    && !ControllerUtils.checkVolumeForVolumeGroupPartialRequest(_dbClient, source)) {
                _log.info("Creating full copy for Application {}", volumeGroup.getLabel());
                // add VolumeGroup to task completer
                completer.addVolumeGroupId(volumeGroup.getId());
            }

            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    COPY_VOLUMES_WF_NAME, false, opId);
            _log.info("Created new full copy workflow with operation id {}", opId);

            /**
             * Volume descriptors contains
             * 1. VPLEX v-volume to be copied (source),
             * 2. VPLEX copy v-volume to be created,
             * 3. backend source volume to be created as clone for (1)'s backend volume
             * 4. Empty volume to be created as HA backend volume for (2) in case of Distributed
             *
             * Group the given volume descriptors by backend Array Replication Group (RG).
             * -For each RG entry, create workflow steps,
             * -These RG steps run in parallel
             */
            Map<String, List<VolumeDescriptor>> repGroupToVolumeDescriptors = groupDescriptorsByReplicationGroup(volumeDescriptors);
            for (String repGroupName : repGroupToVolumeDescriptors.keySet()) {
                _log.info("Processing Array Replication Group {}", repGroupName);
                List<URI> vplexCopyVolumeURIs = new ArrayList<URI>();
                List<VolumeDescriptor> volumeDescriptorsRG = repGroupToVolumeDescriptors.get(repGroupName);

                // We need to know which of the passed volume descriptors represents
                // the VPLEX virtual volumes that are being copied. We also remove it
                // from the list, so the only VPLEX volumes in the list are those
                // of the copies.
                List<VolumeDescriptor> vplexVolumeDescriptors = VolumeDescriptor
                        .filterByType(volumeDescriptorsRG,
                                new VolumeDescriptor.Type[] { Type.VPLEX_VIRT_VOLUME },
                                new VolumeDescriptor.Type[] {});
                List<VolumeDescriptor> vplexSrcVolumeDescrs = getDescriptorsForFullCopySrcVolumes(vplexVolumeDescriptors);
                vplexVolumeDescriptors.removeAll(vplexSrcVolumeDescrs);
                _log.info("Got volume descriptors for VPLEX volumes being copied.");

                // Get the URIs of the VPLEX copy volumes.
                vplexCopyVolumeURIs.addAll(VolumeDescriptor
                        .getVolumeURIs(vplexVolumeDescriptors));

                vplexURI = getDataObject(Volume.class, vplexCopyVolumeURIs.get(0), _dbClient)
                        .getStorageController();

                // Add a rollback step to make sure all full copies are
                // marked inactive and that the full copy relationships
                // are updated. This will also mark any HA backend volumes
                // inactive in the case the copies are distributed. The
                // step only provides functionality on rollback. Normal
                // execution is a no-op.
                List<URI> volumesToMarkInactive = new ArrayList<URI>();
                volumesToMarkInactive.addAll(vplexCopyVolumeURIs);
                List<VolumeDescriptor> blockDescriptors = VolumeDescriptor
                        .filterByType(volumeDescriptorsRG,
                                new VolumeDescriptor.Type[] { Type.BLOCK_DATA },
                                new VolumeDescriptor.Type[] {});
                if (!blockDescriptors.isEmpty()) {
                    volumesToMarkInactive.addAll(VolumeDescriptor.getVolumeURIs(blockDescriptors));
                }
                StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
                Workflow.Method executeMethod = rollbackMethodNullMethod();
                Workflow.Method rollbackMethod = markVolumesInactiveMethod(volumesToMarkInactive);
                String waitFor = workflow.createStep(null, "Mark volumes inactive on rollback",
                        null, vplexURI, vplexSystem.getSystemType(), this.getClass(),
                        executeMethod, rollbackMethod, null);

                if (!vplexSrcVolumeDescrs.isEmpty()) {
                    // Find the backend volume that is the primary volume for one of
                    // the VPLEX volumes being copied. The primary backend volume is the
                    // associated volume in the same virtual array as the VPLEX volume.
                    // It does not matter which one if there are multiple source VPLEX
                    // volumes. These volumes will all be in a consistency group and
                    // use the same backend storage system.
                    VolumeDescriptor vplexSrcVolumeDescr = vplexSrcVolumeDescrs.get(0);
                    vplexSrcVolumeURI = vplexSrcVolumeDescr.getVolumeURI();
                    BlockObject primarySourceObject = getPrimaryForFullCopySrcVolume(vplexSrcVolumeURI);

                    _log.info("Primary volume/snapshot is {}", primarySourceObject.getId());
                    // add CG to taskCompleter
                    if (!NullColumnValueGetter.isNullURI(primarySourceObject.getConsistencyGroup())) {
                        completer.addConsistencyGroupId(primarySourceObject.getConsistencyGroup());
                    }
                }

                // Next, create a step to create and start an import volume
                // workflow for each copy.
                createStepsForFullCopyImport(workflow, vplexURI,
                        vplexVolumeDescriptors, volumeDescriptorsRG, waitFor);
                _log.info("Created workflow steps to import the primary copies");
            }

            _log.info("Executing workflow plan");
            FullCopyOperationCompleteCallback wfCompleteCB = new FullCopyOperationCompleteCallback();
            workflow.executePlan(completer, String.format("Copy of VPLEX volume %s completed successfully", vplexSrcVolumeURI),
                    wfCompleteCB, new Object[] { vplexCopyVolumesURIs }, null, null);
            _log.info("Workflow plan executing");
        } catch (Exception e) {
            String failMsg = String.format("Copy of VPLEX volume %s failed",
                    vplexSrcVolumeURI);
            _log.error(failMsg, e);
            ServiceError serviceError = VPlexApiException.errors.fullCopyVolumesFailed(
                    (vplexSrcVolumeURI != null ? vplexSrcVolumeURI.toString() : ""), e);
            failStep(completer, opId, serviceError);
        }
    }

    /**
     * Finds the volume descriptors in the passed list of volume descriptors that
     * represents the VPLEX volumes to be copied.
     *
     * @param volumeDescriptors
     *            A list of volume descriptors.
     *
     * @return The volume descriptors that represents the VPLEX volumes to be
     *         copied.
     */
    private List<VolumeDescriptor> getDescriptorsForFullCopySrcVolumes(
            List<VolumeDescriptor> volumeDescriptors) {
        List<VolumeDescriptor> vplexSrcVolumeDescrs = new ArrayList<VolumeDescriptor>();
        _log.info("Got all descriptors for VPLEX volumes");
        Iterator<VolumeDescriptor> volumeDescrIter = volumeDescriptors.iterator();
        while (volumeDescrIter.hasNext()) {
            VolumeDescriptor descriptor = volumeDescrIter.next();
            _log.info("Got descriptor for VPLEX volume {}", descriptor.getVolumeURI());
            Object decriptorParam = descriptor.getParameters().get(
                    VolumeDescriptor.PARAM_IS_COPY_SOURCE_ID);
            if (decriptorParam != null) {
                _log.info("Copy source param is not null");
                boolean isCopySource = Boolean.parseBoolean(decriptorParam.toString());
                if (isCopySource) {
                    _log.info("Found descriptor for VPLEX volume being copied");
                    vplexSrcVolumeDescrs.add(descriptor);
                }
            }
        }

        return vplexSrcVolumeDescrs;
    }

    /**
     * For the VPLEX volume with the passed URI, find the associated volume
     * that is the primary backend volume for the VPLEX volume. This will
     * be the backend volume in the same virtual array as the VPLEX volume.
     *
     * @param vplexVolumeURI
     *            The URI of the VPLEX volume.
     *
     * @return A reference to the primary volume for the VPLEX volume.
     */
    private Volume getPrimaryForFullCopySrcVolume(URI vplexVolumeURI) {
        Volume primaryVolume = null;
        Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
        _log.info("Got VPLEX volume {}", vplexVolume.getId());
        URI vplexVolumeVarrayURI = vplexVolume.getVirtualArray();
        _log.info("VPLEX volume virtual array is{}", vplexVolumeVarrayURI);
        primaryVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
        _log.info("Primary volume is {}", primaryVolume.getId());
        return primaryVolume;
    }

    /**
     * Group the given volume descriptors by backend Array Replication Group.
     *
     * Volume descriptors contains
     * 1. VPLEX v-volume to be copied (source),
     * 2. VPLEX copy v-volume to be created,
     * 3. backend source volume to be created as clone for (1)'s backend volume
     * 4. Empty volume to be created as HA backend volume for (2) in case of Distributed
     *
     * @param volumeDescriptors
     *            the volume descriptors
     * @return the replication group to volume desciptors map
     */
    private Map<String, List<VolumeDescriptor>> groupDescriptorsByReplicationGroup(List<VolumeDescriptor> volumeDescriptors) {
        _log.info("Group the given desciptors based on backend Replication Group");
        Map<String, List<VolumeDescriptor>> repGroupToVolumeDescriptors = new HashMap<String, List<VolumeDescriptor>>();
        List<VolumeDescriptor> vplexVolumeDescriptors = VolumeDescriptor
                .filterByType(volumeDescriptors,
                        new VolumeDescriptor.Type[] { Type.VPLEX_VIRT_VOLUME },
                        new VolumeDescriptor.Type[] {});
        List<VolumeDescriptor> vplexSrcVolumeDescrs = getDescriptorsForFullCopySrcVolumes(vplexVolumeDescriptors);
        vplexVolumeDescriptors.removeAll(vplexSrcVolumeDescrs); // has only copy v-volumes
        List<URI> vplexSrcVolumeURIs = VolumeDescriptor.getVolumeURIs(vplexSrcVolumeDescrs);

        if (!vplexSrcVolumeURIs.isEmpty()) {
            List<Volume> vplexSrcVolumes = ControllerUtils.queryVolumesByIterativeQuery(_dbClient, vplexSrcVolumeURIs);
            // group volumes by Array Group
            Map<String, List<Volume>> arrayGroupToVolumesMap = ControllerUtils.groupVolumesByArrayGroup(vplexSrcVolumes, _dbClient);
            /**
             * If only one entry of array replication group, put all descriptors
             * Else group them according to array group
             */
            if (arrayGroupToVolumesMap.size() == 1) {
                String arrayGroupName = arrayGroupToVolumesMap.keySet().iterator().next();
                _log.info("Single entry: Replication Group {}, Volume descriptors {}",
                        arrayGroupName, Joiner.on(',').join(VolumeDescriptor.getVolumeURIs(volumeDescriptors)));
                repGroupToVolumeDescriptors.put(arrayGroupName, volumeDescriptors);
            } else {
                for (String arrayGroupName : arrayGroupToVolumesMap.keySet()) { // AG - Array Replication Group
                    _log.debug("Processing Replication Group {}", arrayGroupName);
                    List<Volume> vplexSrcVolumesAG = arrayGroupToVolumesMap.get(arrayGroupName);

                    List<VolumeDescriptor> descriptorsForAG = new ArrayList<VolumeDescriptor>();
                    // add VPLEX src volumes to descriptor list
                    Collection<URI> vplexSrcVolumesUrisAG = transform(vplexSrcVolumesAG, fctnDataObjectToID());
                    descriptorsForAG.addAll(
                            getDescriptorsForURIsFromGivenList(vplexSrcVolumeDescrs, vplexSrcVolumesUrisAG));

                    for (VolumeDescriptor vplexCopyVolumeDesc : vplexVolumeDescriptors) {
                        URI vplexCopyVolumeURI = vplexCopyVolumeDesc.getVolumeURI();
                        Volume vplexCopyVolume = getDataObject(Volume.class, vplexCopyVolumeURI, _dbClient);
                        if (vplexSrcVolumesUrisAG.contains(vplexCopyVolume.getAssociatedSourceVolume())) {
                            _log.debug("Adding VPLEX copy volume descriptor for {}", vplexCopyVolumeURI);
                            // add VPLEX Copy volumes to descriptor list
                            descriptorsForAG.add(vplexCopyVolumeDesc);

                            // add Copy associated volumes to desciptor list
                            List<VolumeDescriptor> assocDescriptors = getDescriptorsForAssociatedVolumes(
                                    vplexCopyVolumeURI, volumeDescriptors);
                            descriptorsForAG.addAll(assocDescriptors);
                        }
                    }

                    _log.info("Entry: Replication Group {}, Volume descriptors {}",
                            arrayGroupName, Joiner.on(',').join(VolumeDescriptor.getVolumeURIs(descriptorsForAG)));
                    repGroupToVolumeDescriptors.put(arrayGroupName, descriptorsForAG);
                }
            }
        } else {
            // non-application & clone for Snapshot
            _log.info("Request is not for VPLEX volume, returning all.");
            repGroupToVolumeDescriptors.put("SNAPSHOT_GROUP", volumeDescriptors);
        }

        return repGroupToVolumeDescriptors;
    }

    /**
     * From the given desciptor list, get the volume descriptors for the given volumes.
     *
     * @param volumeDescrs
     *            all volume descrs
     * @param volumeURIs
     *            the volume uris
     * @return the descriptors for given volume uris
     */
    private List<VolumeDescriptor> getDescriptorsForURIsFromGivenList(List<VolumeDescriptor> volumeDescrs,
            Collection<URI> volumeURIs) {
        List<VolumeDescriptor> volumeDescsForGivenURIs = new ArrayList<VolumeDescriptor>();
        for (VolumeDescriptor volumeDesc : volumeDescrs) {
            if (volumeURIs.contains(volumeDesc.getVolumeURI())) {
                volumeDescsForGivenURIs.add(volumeDesc);
            }
        }
        return volumeDescsForGivenURIs;
    }

    /**
     * Creates a step in the passed workflow to import each copy of the passed
     * primary backend volume to a VPLEX virtual volume.
     *
     * @param workflow
     *            A reference to the workflow.
     * @param vplexURI
     *            The URI of the VPLEX storage system.
     * @param sourceBlockObject
     *            The primary backend volume/snapshot that was copied.
     * @param vplexVolumeDescriptors
     *            The volume descriptors representing the
     *            copies of the VPLEX volume.
     * @param assocVolumeDescriptors
     *            The volume descriptors representing the
     *            primary copies and, for copies of distributed VPLEX volumes, the
     *            newly created HA volumes. These volumes will comprise the backend
     *            volumes of the VPLEX volume copies.
     * @param waitFor
     *            The step in to workflow for which these steps should wait
     *            to successfully complete before executing.
     *
     * @return The step for which any subsequent steps in the workflow should
     *         wait.
     */
    private String createStepsForFullCopyImport(Workflow workflow, URI vplexURI,
            List<VolumeDescriptor> vplexVolumeDescriptors,
            List<VolumeDescriptor> assocVolumeDescriptors, String waitFor) {
        StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
        _log.info("Got VPLEX {}", vplexURI);

        URI projectURI = null;
        URI tenantURI = null;

        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME);
        for (VolumeDescriptor vplexVolumeDescriptor : vplexVolumeDescriptors) {
            _log.info("Creating step for VPLEX volume copy {}",
                    vplexVolumeDescriptor.getVolumeURI());
            List<VolumeDescriptor> descriptorsForImport = new ArrayList<VolumeDescriptor>();
            descriptorsForImport.add(vplexVolumeDescriptor);
            URI vplexVolumeURI = vplexVolumeDescriptor.getVolumeURI();
            List<VolumeDescriptor> assocDescriptors = getDescriptorsForAssociatedVolumes(
                    vplexVolumeURI, assocVolumeDescriptors);
            descriptorsForImport.addAll(assocDescriptors);

            // get the project and tenant from the vplex volume
            if (projectURI == null) {
                Volume vplexVol = _dbClient.queryObject(Volume.class, vplexVolumeURI);
                if (vplexVol != null && !vplexVol.getInactive()) {
                    projectURI = vplexVol.getProject().getURI();
                    tenantURI = vplexVol.getTenant().getURI();
                }
            }

            _log.info("Added descriptors for the copy's associated volumes");
            String stepId = workflow.createStepId();
            Workflow.Method executeMethod = createImportCopyMethod(vplexURI,
                    descriptorsForImport, projectURI, tenantURI);
            Workflow.Method rollbackMethod = rollbackImportCopyMethod(
                    vplexVolumeDescriptor, assocDescriptors);
            workflow.createStep(IMPORT_COPY_STEP,
                    String.format("Importing copied volume to create VPLEX volume %s",
                            vplexVolumeDescriptor.getVolumeURI()),
                    waitFor, vplexURI, vplexSystem.getSystemType(), this.getClass(),
                    executeMethod, rollbackMethod, stepId);
            _log.info("Added import workflow step to create VPLEX volume copy");

            // Create a task operation status for each ha volume copy that will
            // be updated when the step is executed and completed.
            for (VolumeDescriptor assocDescriptor : assocDescriptors) {
                if (assocDescriptor.getType().equals(VolumeDescriptor.Type.BLOCK_DATA)) {
                    op = _dbClient.createTaskOpStatus(Volume.class,
                            assocDescriptor.getVolumeURI(), stepId, op);
                }
            }
        }

        return IMPORT_COPY_STEP;
    }

    /**
     * From the passed list of descriptors, finds the descriptors for the
     * associated volumes of the VPLEX volume with the passed URI.
     *
     * @param vplexVolumeURI
     *            The URI of the VPLEX volume.
     * @param descriptors
     *            The list of volume descriptors.
     *
     * @return A list containing the volume descriptors representing the
     *         associated volumes for the VPLEX volume.
     */
    private List<VolumeDescriptor> getDescriptorsForAssociatedVolumes(URI vplexVolumeURI,
            List<VolumeDescriptor> descriptors) {
        _log.info("Getting associated volume descriptors for VPLEX copy {}",
                vplexVolumeURI);
        List<VolumeDescriptor> assocVolumeDescriptors = new ArrayList<VolumeDescriptor>();
        Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
        _log.info("Got VPLEX volume");
        StringSet assocVolumeURIs = vplexVolume.getAssociatedVolumes();
        if (null == assocVolumeURIs || assocVolumeURIs.isEmpty()) {
            _log.error("VPLEX volume {} has no backend volumes.", vplexVolume.forDisplay());
            throw InternalServerErrorException.internalServerErrors.noAssociatedVolumesForVPLEXVolume(vplexVolume.forDisplay());
        }
        Iterator<String> assocVolumeURIsIter = assocVolumeURIs.iterator();
        while (assocVolumeURIsIter.hasNext()) {
            URI assocVolumeURI = URI.create(assocVolumeURIsIter.next());
            _log.info("Associated volume is {}", assocVolumeURI);
            Iterator<VolumeDescriptor> descriptorIter = descriptors.iterator();
            while (descriptorIter.hasNext()) {
                VolumeDescriptor descriptor = descriptorIter.next();
                URI volumeURI = descriptor.getVolumeURI();
                _log.info("Descriptor volume is {}", volumeURI);
                if (volumeURI.equals(assocVolumeURI)) {
                    _log.info("Found descriptor for associated volume");
                    assocVolumeDescriptors.add(descriptor);
                    break;
                }
            }
        }
        return assocVolumeDescriptors;
    }

    /**
     * A method the creates the method to import a natively copied volume to a
     * VPLEX virtual volume.
     *
     * @param vplexURI
     *            The URI of the VPLEX storage system.
     * @param cgURI
     *            The list of volume descriptors.
     * @param projectURI
     *            The VPLEX system project URI.
     * @param tenantURI
     *            The VPLEX system tenant URI.
     *
     * @return A reference to the import copy workflow method.
     */
    private Workflow.Method createImportCopyMethod(URI vplexURI,
            List<VolumeDescriptor> volumeDescriptors, URI projectURI, URI tenantURI) {
        return new Workflow.Method(IMPORT_COPY_METHOD_NAME, vplexURI, volumeDescriptors,
                projectURI, tenantURI);
    }

    /**
     * Creates and starts the import workflow to import the volume to a VPLEX
     * virtual volume.
     *
     * @param vplexSystemURI
     *            The URI of the VPLEX system.
     * @param volumeDescriptors
     *            The list of volume descriptors.
     * @param projectURI
     *            The URI of the VPLEX project.
     * @param tenantURI
     *            The URI of the VPLEX tenant.
     * @param stepId
     *            The workflow step id.
     *
     * @throws ControllerException
     *             When an error occurs configuring the import
     *             workflow.
     */
    public void importCopy(URI vplexSystemURI, List<VolumeDescriptor> volumeDescriptors,
            URI projectURI, URI tenantURI, String stepId)
                    throws ControllerException {
        _log.info("Executing import step for full copy.");

        List<VolumeDescriptor> importVolumeDescriptors = VolumeDescriptor.filterByType(
                volumeDescriptors, new VolumeDescriptor.Type[] { Type.VPLEX_IMPORT_VOLUME },
                new VolumeDescriptor.Type[] {});
        VolumeDescriptor importVolumeDescriptor = importVolumeDescriptors.get(0);
        Volume importVolume = getDataObject(Volume.class,
                importVolumeDescriptor.getVolumeURI(), _dbClient);

        // Update step state to executing.
        WorkflowStepCompleter.stepExecuting(stepId);

        // Reuse the import volume API to create a sub workflow to execute
        // the import.
        importVolume(vplexSystemURI, volumeDescriptors, projectURI, tenantURI,
                importVolume.getVirtualPool(), importVolume.getLabel(), null, Boolean.FALSE, stepId);
        _log.info("Created and started sub workflow to import the copy");
    }

    /**
     * Rollback when a failure occurs importing a full copy.
     *
     * @param vplexVolumeDescriptor
     *            The descriptor for the VPLEX copy volume
     * @param assocVolumeDescrs
     *            The descriptors for its backend volumes.
     *
     * @return A reference to the rollback import copy workflow method.
     */
    public Workflow.Method rollbackImportCopyMethod(
            VolumeDescriptor vplexVolumeDescriptor, List<VolumeDescriptor> assocVolumeDescrs) {
        return new Workflow.Method("rollbackImportCopy", vplexVolumeDescriptor,
                assocVolumeDescrs);
    }

    /**
     * If a failure occurs during an import of a full copy, make sure
     * the volume is torn back down.
     *
     * @param vplexVolumeDescriptor
     *            The descriptor for the VPLEX copy volume
     * @param assocVolumeDescrs
     *            The descriptors for its backend volumes.
     * @param stepId
     *            The rollback step id.
     */
    public void rollbackImportCopy(VolumeDescriptor vplexVolumeDescriptor,
            List<VolumeDescriptor> assocVolumeDescrs, String stepId) {

        // Update step state to executing.
        WorkflowStepCompleter.stepExecuting(stepId);

        // Make sure the volume is torn down.
        List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();
        // only delete active ones
        Volume vplexVol = _dbClient.queryObject(Volume.class, vplexVolumeDescriptor.getVolumeURI());
        if (vplexVol != null && !vplexVol.getInactive()) {
            volumeDescriptors.add(vplexVolumeDescriptor);
        }
        for (VolumeDescriptor volDes : assocVolumeDescrs) {
            Volume vol = _dbClient.queryObject(Volume.class, volDes.getVolumeURI());
            if (vol != null && !vol.getInactive()) {
                volumeDescriptors.add(volDes);
            }
        }
        _blockOrchestrationController.deleteVolumes(volumeDescriptors, stepId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restoreFromFullCopy(URI vplexURI, List<URI> fullCopyURIs, String opId)
            throws InternalException {
        TaskCompleter completer = null;
        try {
            completer = new CloneRestoreCompleter(fullCopyURIs, opId);
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    RESTORE_VOLUME_WF_NAME, false, opId);
            _log.info("Created restore volume workflow with operation id {}", opId);

            // add CG to taskCompleter
            Volume firstFullCopy = getDataObject(Volume.class, fullCopyURIs.get(0), _dbClient);
            BlockObject firstSource = BlockObject.fetch(_dbClient, firstFullCopy.getAssociatedSourceVolume());
            if (!NullColumnValueGetter.isNullURI(firstSource.getConsistencyGroup())) {
                completer.addConsistencyGroupId(firstSource.getConsistencyGroup());
            }

            // Get the VPLEX and backend full copy volumes.
            URI nativeSystemURI = null;
            Map<URI, Volume> vplexFullCopyMap = new HashMap<URI, Volume>();
            Map<URI, Volume> nativeFullCopyMap = new HashMap<URI, Volume>();
            for (URI fullCopyURI : fullCopyURIs) {
                Volume fullCopyVolume = getDataObject(Volume.class, fullCopyURI, _dbClient);
                vplexFullCopyMap.put(fullCopyURI, fullCopyVolume);
                Volume nativeFullCopyVolume = VPlexUtil.getVPLEXBackendVolume(fullCopyVolume, true, _dbClient);
                nativeFullCopyMap.put(nativeFullCopyVolume.getId(), nativeFullCopyVolume);
                if (nativeSystemURI == null) {
                    nativeSystemURI = nativeFullCopyVolume.getStorageController();
                }
            }

            // We'll need a list of the native full copy URIs.
            List<URI> nativeFullCopyURIs = new ArrayList<URI>(nativeFullCopyMap.keySet());

            // Get the native system.
            StorageSystem nativeSystem = getDataObject(StorageSystem.class, nativeSystemURI, _dbClient);

            // Maps Vplex volume that needs to be flushed to underlying array volume
            Map<Volume, Volume> vplexToArrayVolumesToFlush = new HashMap<Volume, Volume>();
            for (Volume vplexFullCopyVolume : vplexFullCopyMap.values()) {
                Volume fcSourceVolume = getDataObject(Volume.class,
                        vplexFullCopyVolume.getAssociatedSourceVolume(), _dbClient);
                Volume arrayVolumeToBeRestored = VPlexUtil.getVPLEXBackendVolume(
                        fcSourceVolume, true, _dbClient);
                vplexToArrayVolumesToFlush.put(fcSourceVolume, arrayVolumeToBeRestored);
            }
            Map<URI, String> vplexVolumeIdToDetachStep = new HashMap<URI, String>();

            // Generate pre restore steps
            String waitFor = addPreRestoreResyncSteps(workflow, vplexToArrayVolumesToFlush, vplexVolumeIdToDetachStep, null);

            // Now create a workflow step to natively restore the backend
            // source volumes from the backend full copies. We execute this
            // after the invalidate cache steps.
            waitFor = createWorkflowStepForRestoreNativeFullCopy(workflow, nativeSystem,
                    nativeFullCopyURIs, waitFor, rollbackMethodNullMethod());

            // Generate post restore steps
            waitFor = addPostRestoreResyncSteps(workflow, vplexToArrayVolumesToFlush, vplexVolumeIdToDetachStep, waitFor);

            // Execute the workflow.
            _log.info("Executing workflow plan");
            String successMsg = String.format(
                    "Restore full copy volumes %s completed successfully", fullCopyURIs);
            FullCopyOperationCompleteCallback wfCompleteCB = new FullCopyOperationCompleteCallback();
            workflow.executePlan(completer, successMsg, wfCompleteCB,
                    new Object[] { fullCopyURIs }, null, null);
            _log.info("Workflow plan executing");
        } catch (Exception e) {
            String failMsg = String.format(
                    "Restore full copy volumes %s failed", fullCopyURIs);
            _log.error(failMsg, e);
            ServiceCoded sc = VPlexApiException.exceptions.restoreFromFullCopyFailed(
                    fullCopyURIs.toString(), e);
            failStep(completer, opId, sc);
        }
    }

    /**
     * Callback handler that is invoked when full copy operation
     * workflows complete.
     */
    @SuppressWarnings("serial")
    public static class FullCopyOperationCompleteCallback implements
            Workflow.WorkflowCallbackHandler, Serializable {

        /**
         * {@inheritDoc}
         *
         * @throws WorkflowException
         */
        @SuppressWarnings("unchecked")
        @Override
        public void workflowComplete(Workflow workflow, Object[] args)
                throws WorkflowException {
            _log.info("Full copy operation completed.");
            if ((args != null) && (args.length > 0)) {
                VPlexDeviceController.getInstance().fullCopyOperationComplete(
                        (List<URI>) args[0]);
            } else {
                _log.warn("No args passed");
            }
        }
    }

    /**
     * When a full copy operation completes make sure the replica state of the
     * VPLEX full copy volumes reflect the replica state of their corresponding
     * native full copy volume.
     *
     * @param fullCopyVolumeURIs
     *            The URIs of the VPLEX full copy volumes.
     */
    public void fullCopyOperationComplete(List<URI> fullCopyVolumeURIs) {
        for (URI fullCopyVolumeURI : fullCopyVolumeURIs) {
            Volume fullCopyVolume = _dbClient.queryObject(Volume.class, fullCopyVolumeURI);
            if (fullCopyVolume != null) {
                Volume nativeFullCopyVolume = VPlexUtil.getVPLEXBackendVolume(fullCopyVolume,
                        true, _dbClient, false);
                if (nativeFullCopyVolume != null) {
                    String nativeFCReplicaState = nativeFullCopyVolume.getReplicaState();
                    if (ReplicationState.DETACHED.name().equals(nativeFCReplicaState)) {
                        ReplicationUtils.removeDetachedFullCopyFromSourceFullCopiesList(fullCopyVolume, _dbClient);
                        fullCopyVolume.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());

                        if (NullColumnValueGetter.isNotNullValue(fullCopyVolume.getFullCopySetName())) {
                            fullCopyVolume.setFullCopySetName(NullColumnValueGetter.getNullStr());
                        }
                    }
                    fullCopyVolume.setReplicaState(nativeFCReplicaState);
                    // update the backingReplicationGroupInstance on the virtual volume full copy if present on backend volume
                    String backingReplicationGroupInstance = NullColumnValueGetter
                            .isNotNullValue(nativeFullCopyVolume.getBackingReplicationGroupInstance())
                                    ? nativeFullCopyVolume.getBackingReplicationGroupInstance() : NullColumnValueGetter.getNullStr();
                    fullCopyVolume.setBackingReplicationGroupInstance(backingReplicationGroupInstance);
                    _dbClient.updateObject(fullCopyVolume);
                } else {
                    _log.warn("Can't find native full copy volume");
                }
            } else {
                _log.warn("Full copy volume {} is null", fullCopyVolumeURI);
            }
        }
    }

    /**
     * Create a step in the passed workflow to restore the backend
     * full copy volumes with the passed URIs.
     *
     * @param workflow
     *            A reference to a workflow.
     * @param nativeSystem
     *            A reference to the native storage system.
     * @param nativeFullCopyURIs
     *            The URIs of the native full copies.
     * @param waitFor
     *            The step to wait for or null.
     * @param rollbackMethod
     *            A reference to a rollback method or null.
     *
     * @return RESTORE_VOLUME_STEP
     */
    private String createWorkflowStepForRestoreNativeFullCopy(Workflow workflow,
            StorageSystem nativeSystem, List<URI> nativeFullCopyURIs, String waitFor,
            Workflow.Method rollbackMethod) {
        URI nativeSystemURI = nativeSystem.getId();
        Workflow.Method restoreVolumeMethod = new Workflow.Method(
                RESTORE_FROM_FC_METHOD_NAME, nativeSystemURI, nativeFullCopyURIs,
                Boolean.FALSE);
        workflow.createStep(RESTORE_VOLUME_STEP,
                String.format("Restore native full copies: %s", nativeFullCopyURIs), waitFor,
                nativeSystemURI, nativeSystem.getSystemType(), BlockDeviceController.class,
                restoreVolumeMethod, rollbackMethod, null);
        _log.info("Created workflow step to restore native full copies {}",
                nativeFullCopyURIs);

        return RESTORE_VOLUME_STEP;
    }

    /**
     * Create a step in the passed workflow to invalidate the read cache
     * for the passed volume on the passed VPLEX system.
     *
     * @param workflow
     *            A reference to a workflow.
     * @param vplexSystem
     *            A reference to a VPLEX storage system.
     * @param vplexVolumeURI
     *            The URI of the virtual volume.
     * @param waitFor
     *            The step to wait for or null.
     * @param rollbackMethod
     *            A reference to a rollback method or null.
     *
     * @return INVALIDATE_CACHE_STEP
     */
    private String createWorkflowStepForInvalidateCache(Workflow workflow,
            StorageSystem vplexSystem, URI vplexVolumeURI, String waitFor,
            Workflow.Method rollbackMethod) {
        Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
        URI vplexURI = vplexSystem.getId();
        Workflow.Method invalidateCacheMethod = createInvalidateCacheMethod(vplexURI,
                vplexVolumeURI);
        workflow.createStep(INVALIDATE_CACHE_STEP, String.format(
                "Invalidate read cache for VPLEX volume %s (%s) on system %s", vplexVolume.getLabel(),
                vplexVolumeURI, vplexURI), waitFor, vplexURI, vplexSystem.getSystemType(), this.getClass(),
                invalidateCacheMethod, rollbackMethod, null);
        _log.info("Created workflow step to invalidate the read cache for volume {}",
                vplexVolumeURI);

        return INVALIDATE_CACHE_STEP;
    }

    /**
     * A method that creates the workflow method to invalidate the read cache
     * for a VPLEX virtual volume.
     *
     * @param vplexURI
     *            The URI of the VPLEX system.
     * @param vplexVolumeURI
     *            The URI of a VPLEX volume.
     *
     * @return A reference to the invalidate cache workflow method.
     */
    private Workflow.Method createInvalidateCacheMethod(URI vplexURI, URI vplexVolumeURI) {
        return new Workflow.Method(INVALIDATE_CACHE_METHOD_NAME, vplexURI, vplexVolumeURI);
    }

    /**
     * Called to invalidate the read cache for a VPLEX volume when
     * restoring a snapshot.
     *
     * @param vplexURI
     *            The URI of the VPLEX system.
     * @param vplexVolumeURI
     *            The URI of a VPLEX volume.
     * @param stepId
     *            The workflow step identifier.
     */
    public void invalidateCache(URI vplexURI, URI vplexVolumeURI, String stepId) {
        _log.info("Executing invalidate cache for volume {} on VPLEX {}", vplexVolumeURI,
                vplexURI);

        try {
            // Update workflow step.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Get the API client.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexSystem, _dbClient);
            _log.info("Got VPLEX API client");

            // Get the VPLEX volume.
            Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
            String vplexVolumeName = vplexVolume.getDeviceLabel();
            _log.info("Got VPLEX volumes");

            // Invalidate the cache for the volume.
            boolean stillInProgress = client.invalidateVirtualVolumeCache(vplexVolumeName);
            _log.info("Invalidated the VPLEX volume cache");

            // If the operation is still in progress, create a cache invalidate
            // task completer and queue a job to monitor the invalidation
            // status. The completer will be invoked by the job when the cache
            // invalidation completes.
            if (stillInProgress) {
                CacheStatusTaskCompleter invalidateCompleter = new CacheStatusTaskCompleter(
                        vplexVolumeURI, stepId);
                VPlexCacheStatusJob cacheStatusJob = new VPlexCacheStatusJob(invalidateCompleter);
                ControllerServiceImpl.enqueueJob(new QueueJob(cacheStatusJob));
                _log.info("Queued job to monitor migration progress.");
            } else {
                // Update workflow step state to success.
                WorkflowStepCompleter.stepSucceded(stepId);
                _log.info("Updated workflow step state to success");
            }
        } catch (VPlexApiException vae) {
            _log.error("Exception invalidating VPLEX volume cache " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception e) {
            _log.error("Exception invalidating VPLEX volume cache " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId,
                    VPlexApiException.exceptions.failedInvalidatingVolumeCache(vplexVolumeURI.toString(), e));
        }
    }

    /**
     * Creates a workflow step to detach the mirror with the passed
     * URI from the passed VPLEX volume.
     *
     * @param workflow
     *            A reference to a workflow.
     * @param vplexSystem
     *            A reference to a VPLEX storage system.
     * @param vplexVolume
     *            A reference to the virtual volume.
     * @param stepId
     *            The step id for this step or null.
     * @param waitFor
     *            The step to wait for or null.
     * @param rollbackMethod
     *            A reference to a rollback method or null.
     *
     * @return DETACH_MIRROR_STEP
     */
    private String createWorkflowStepForDetachMirror(Workflow workflow,
            StorageSystem vplexSystem, Volume vplexVolume,
            String stepId, String waitFor, Workflow.Method rollbackMethod) {
        URI vplexURI = vplexSystem.getId();
        URI vplexVolumeURI = vplexVolume.getId();
        Workflow.Method detachMirrorMethod = createDetachMirrorMethod(vplexURI,
                vplexVolumeURI, vplexVolume.getConsistencyGroup());
        workflow.createStep(DETACH_MIRROR_STEP, String.format(
                "Detach mirror for VPLEX volume %s on system %s",
                vplexVolumeURI, vplexURI), waitFor, vplexURI, vplexSystem
                        .getSystemType(),
                this.getClass(), detachMirrorMethod,
                rollbackMethod, stepId);
        _log.info("Created workflow step to detach mirror from volume {}", vplexVolumeURI);

        return DETACH_MIRROR_STEP;
    }

    /**
     * A method that creates the workflow method to detach the remote mirror
     * for a VPLEX distributed virtual volume.
     *
     * @param vplexURI
     *            The URI of the VPLEX system.
     * @param vplexVolumeURI
     *            The URI of the distributed VPLEX volume.
     * @param cgURI
     *            The URI of the volume's CG or null.
     *
     * @return A reference to the detach mirror workflow method.
     */
    private Workflow.Method createDetachMirrorMethod(URI vplexURI, URI vplexVolumeURI, URI cgURI) {
        return new Workflow.Method(DETACH_MIRROR_METHOD_NAME, vplexURI, vplexVolumeURI, cgURI);
    }

    /**
     * Called to detach the remote mirror for a distributed VPLEX volume prior
     * to restoring a native snapshot of the backend source volume.
     *
     * @param vplexURI
     *            The URI of the VPLEX system.
     * @param vplexVolumeURI
     *            The URI of the distributed VPLEX volume.
     * @param cgURI
     *            The URI of the volume's CG or null.
     * @param stepId
     *            The workflow step identifier.
     */
    public void detachMirror(URI vplexURI, URI vplexVolumeURI, URI cgURI, String stepId) {
        _log.info("Executing detach mirror of VPLEX volume {} on VPLEX {}",
                new Object[] { vplexVolumeURI, vplexURI });
        
        String detachedDeviceName = "";
        try {
            // Initialize the data that will tell RB what to do in
            // case of failure.
            Map<String, String> stepData = new HashMap<String, String>();
            stepData.put(REATTACH_MIRROR, Boolean.FALSE.toString());
            stepData.put(ADD_BACK_TO_CG, Boolean.FALSE.toString());
            _workflowService.storeStepData(stepId, stepData);

            // Update workflow step.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Get the API client.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexSystem, _dbClient);
            _log.info("Got VPLEX API client");

            // Get the VPLEX volume.
            Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
            String vplexVolumeName = vplexVolume.getDeviceLabel();
            _log.info("Got VPLEX volume");

            // COP-17337 : If device length is greater than 47 character then VPLEX does
            // not allow reattaching mirror. This is mostly going to be the case for the
            // distributed volume with XIO back-end on both legs. Temporarily rename the
            // device to 47 characters and then detach the mirror. Now when we reattach
            // the device name will not exceed the limit. We must do this before we detach
            // so that the device name is the same for the detach/attach operations. If
            // we try to rename prior to reattaching the mirror, COP-25581 will result.
            VPlexVirtualVolumeInfo vvInfo = client.findVirtualVolume(vplexVolumeName, vplexVolume.getNativeId());
            if (vvInfo != null) {
                String distDeviceName = vvInfo.getSupportingDevice();
                if (distDeviceName.length() > VPlexApiConstants.MAX_DEVICE_NAME_LENGTH_FOR_ATTACH_MIRROR) {
                    String modifiedName = distDeviceName.substring(0, VPlexApiConstants.MAX_DEVICE_NAME_LENGTH_FOR_ATTACH_MIRROR);
                    _log.info("Temporarily renaming the distributed device from {} to {} as VPLEX expects the name "
                            + " to be 47 characters or less when we reattach the mirror.", distDeviceName, modifiedName);
                    client.renameDistributedDevice(distDeviceName, modifiedName);
                    stepData.put(RESTORE_DEVICE_NAME, distDeviceName);
                }
            } else {
                _log.error("Can't find volume {}:{} to detach mirror.", vplexVolumeName, vplexVolumeURI);
                throw VPlexApiException.exceptions.cantFindVolumeForDeatchMirror(vplexVolume.forDisplay());
            }

            // If the volume is in a CG, we must first remove it before
            // detaching the remote mirror.
            if (cgURI != null) {
                ConsistencyGroupManager consistencyGroupManager = getConsistencyGroupManager(vplexVolume);
                consistencyGroupManager.removeVolumeFromCg(cgURI, vplexVolume, client, false);
                
                stepData.put(ADD_BACK_TO_CG, Boolean.TRUE.toString());
                _workflowService.storeStepData(stepId, stepData);
                _log.info("Removed volumes from consistency group.");
            }

            // Detach the mirror.
            detachedDeviceName = client.detachMirrorFromDistributedVolume(vplexVolumeName, null);
            stepData.put(DETACHED_DEVICE, detachedDeviceName);
            stepData.put(REATTACH_MIRROR, Boolean.TRUE.toString());
            _workflowService.storeStepData(stepId, stepData);
            _log.info("Detached the mirror");

            updateThinProperty(client, vplexSystem, vplexVolume);

            // Update workflow step state to success.
            WorkflowStepCompleter.stepSucceded(stepId);
            _log.info("Updated workflow step state to success");
        } catch (VPlexApiException vae) {
            _log.error("Exception detaching mirror for VPLEX distributed volume: " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception e) {
            _log.error("Exception detaching mirror for VPLEX distributed volume: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, VPlexApiException.exceptions
                    .failedDetachingVPlexVolumeMirror(detachedDeviceName, vplexVolumeURI.toString(), e));
        }
    }

    /**
     * Creates a workflow step to reattach a mirror that was previously
     * detached to the passed distributed vplex volume.
     *
     * @param workflow
     *            A reference to a workflow.
     * @param vplexSystem
     *            A reference to a VPLEX storage system.
     * @param vplexVolume
     *            A reference to the virtual volume.
     * @param detachStepId
     *            The step id for the step that detached the mirror.
     * @param waitFor
     *            The step to wait for or null.
     * @param rollbackMethod
     *            A reference to a rollback method or null.
     *
     * @return ATTACH_MIRROR_STEP
     */
    private String createWorkflowStepForAttachMirror(Workflow workflow,
            StorageSystem vplexSystem, Volume vplexVolume,
            String detachStepId, String waitFor, Workflow.Method rollbackMethod) {
        URI vplexURI = vplexSystem.getId();
        URI vplexVolumeURI = vplexVolume.getId();
        Workflow.Method attachMirrorMethod = createAttachMirrorMethod(vplexURI,
                vplexVolumeURI, vplexVolume.getConsistencyGroup(),
                detachStepId);
        workflow.createStep(ATTACH_MIRROR_STEP, String.format(
                "Attach mirror for VPLEX volume %s on system %s",
                vplexVolumeURI, vplexURI), waitFor, vplexURI, vplexSystem
                        .getSystemType(),
                this.getClass(), attachMirrorMethod,
                rollbackMethod, null);
        _log.info("Created workflow step to reattach mirror to volume {}", 
                vplexVolumeURI);

        return ATTACH_MIRROR_STEP;
    }

    /**
     * A method that creates the workflow method to reattach the remote mirror
     * for a VPLEX distributed virtual volume.
     *
     * @param vplexURI
     *            The URI of the VPLEX system.
     * @param vplexVolumeURI
     *            The URI of the distributed VPLEX volume.
     * @param cgURI
     *            The URI of the volume's CG or null.
     * @param detachStepId
     *            The Id of the detach mirror step.
     *
     * @return A reference to the attach mirror workflow method.
     */
    private Workflow.Method createAttachMirrorMethod(URI vplexURI, URI vplexVolumeURI,
            URI cgURI, String detachStepId) {
        return new Workflow.Method(ATTACH_MIRROR_METHOD_NAME, vplexURI, vplexVolumeURI,
                cgURI, detachStepId);
    }

    /**
     * Called to reattach the remote mirror for a distributed VPLEX volume upon
     * successfully restoring a native snapshot of the backend source volume.
     *
     * @param vplexURI
     *            The URI of the VPLEX system.
     * @param vplexVolumeURI
     *            The URI of the distributed VPLEX volume.
     * @param cgURI
     *            The URI of the volume's CG or null.
     * @param detachStepId
     *            The Id of the detach mirror step.
     * @param stepId
     *            The workflow step identifier.
     */
    public void attachMirror(URI vplexURI, URI vplexVolumeURI, URI cgURI, 
            String detachStepId, String stepId) {
        _log.info("Executing attach mirror to VPLEX volume {} on VPLEX {}",
                new Object[] { vplexVolumeURI, vplexURI });
        String mirrorDeviceName = "";
        try {
            // Update workflow step.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Get the API client.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexSystem, _dbClient);
            _log.info("Got VPLEX API client");

            // Get the VPLEX volume.
            Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
            String vplexVolumeName = vplexVolume.getDeviceLabel();
            _log.info("Got VPLEX volume");

            // Attach the mirror.
            @SuppressWarnings("unchecked")
            Map<String, String> detachStepData = (Map<String, String>) _workflowService.loadStepData(detachStepId);
            mirrorDeviceName = detachStepData.get(DETACHED_DEVICE);
            client.reattachMirrorToDistributedVolume(vplexVolumeName, mirrorDeviceName);
            _log.info("Attached the mirror");

            // On a subsequent failure, we don't want rollback to try to
            // reattach the mirror as it must have been successful here.
            // If the reattach fails, rollback will just end up trying again.
            detachStepData.put(REATTACH_MIRROR, Boolean.FALSE.toString());
            _workflowService.storeStepData(detachStepId, detachStepData);

            // If on detach it was required to temporarily modify the distributed device name
            // then we restore the correct name now after the volume is back together.
            String modifiedName = null;
            String successWarningMessage = null;
            String restoreDeviceName = detachStepData.get(RESTORE_DEVICE_NAME);
            if (restoreDeviceName != null) {
                try {
                    modifiedName = restoreDeviceName.substring(0, VPlexApiConstants.MAX_DEVICE_NAME_LENGTH_FOR_ATTACH_MIRROR);
                    client.renameDistributedDevice(modifiedName, restoreDeviceName);
                    _log.info("Restored name of distributed device from {} to {}", modifiedName, restoreDeviceName);
                } catch (Exception e) {
                    // We don't fail the workflow step in this case, but instead just log a message
                    // indicating this error. The distributed device for the volume will just have
                    // the modified name.
                    successWarningMessage = String.format("Failed renaming the distributed device %s back "
                            + " to its orginal name %s after reattaching remote mirror", modifiedName, restoreDeviceName);
                    _log.warn(successWarningMessage);
                }
                // Remove the entry in the step data. We do this regardless of success
                // or failure so that we don't try and rename again in a rollback
                // scenario.
                detachStepData.remove(RESTORE_DEVICE_NAME);
            }

            // If the volume is in a CG, we can now add it back.
            if (cgURI != null) {
                ConsistencyGroupManager consistencyGroupManager = getConsistencyGroupManager(vplexVolume);
                consistencyGroupManager.addVolumeToCg(cgURI, vplexVolume, client, false);

                // No need to try and add it back in rollback if something
                // fails in the step after this point. If the add fails
                // here, rollback will just try again.
                detachStepData.put(ADD_BACK_TO_CG, Boolean.FALSE.toString());
                _workflowService.storeStepData(detachStepId, detachStepData);
                _log.info("Added volume back to consistency group.");
            }

            // Update workflow step state to success.
            if (successWarningMessage != null) {
                WorkflowStepCompleter.stepSucceeded(stepId, successWarningMessage);
            } else {
                WorkflowStepCompleter.stepSucceded(stepId);
            }
            _log.info("Updated workflow step state to success");
        } catch (VPlexApiException vae) {
            _log.error("Exception attaching mirror for VPLEX distributed volume" + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception e) {
            _log.error("Exception attaching mirror for VPLEX distributed volume " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, VPlexApiException.exceptions
                    .failedAttachingVPlexVolumeMirror(mirrorDeviceName, vplexVolumeURI.toString(), e));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resyncFullCopy(URI vplexURI, List<URI> fullCopyURIs, String opId)
            throws InternalException {
        TaskCompleter completer = null;
        try {
            completer = new CloneResyncCompleter(fullCopyURIs, opId);
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    RESYNC_FULL_COPY_WF_NAME, false, opId);
            _log.info("Created resync full copy workflow with operation id {}", opId);

            // add CG to taskCompleter
            Volume firstFullCopy = getDataObject(Volume.class, fullCopyURIs.get(0), _dbClient);
            BlockObject firstSource = BlockObject.fetch(_dbClient, firstFullCopy.getAssociatedSourceVolume());
            if (!NullColumnValueGetter.isNullURI(firstSource.getConsistencyGroup())) {
                completer.addConsistencyGroupId(firstSource.getConsistencyGroup());
            }

            // Get the VPLEX and backend full copy volumes.
            URI nativeSystemURI = null;
            Map<URI, Volume> vplexFullCopyMap = new HashMap<URI, Volume>();
            Map<URI, Volume> nativeFullCopyMap = new HashMap<URI, Volume>();
            for (URI fullCopyURI : fullCopyURIs) {
                Volume fullCopyVolume = getDataObject(Volume.class, fullCopyURI, _dbClient);
                vplexFullCopyMap.put(fullCopyURI, fullCopyVolume);
                Volume nativeFullCopyVolume = VPlexUtil.getVPLEXBackendVolume(fullCopyVolume, true, _dbClient);
                nativeFullCopyMap.put(nativeFullCopyVolume.getId(), nativeFullCopyVolume);
                if (nativeSystemURI == null) {
                    nativeSystemURI = nativeFullCopyVolume.getStorageController();
                }
            }

            // Get the native system.
            StorageSystem nativeSystem = getDataObject(StorageSystem.class, nativeSystemURI, _dbClient);

            // We'll need a list of the native full copy URIs.
            List<URI> nativeFullCopyURIs = new ArrayList<URI>(nativeFullCopyMap.keySet());

            // Maps Vplex volume that needs to be flushed to underlying array volume
            Map<Volume, Volume> vplexToArrayVolumesToFlush = new HashMap<Volume, Volume>();
            for (Volume vplexFullCopyVolume : vplexFullCopyMap.values()) {
                Volume arrayVolumeToBeResynced = VPlexUtil.getVPLEXBackendVolume(
                        vplexFullCopyVolume, true, _dbClient);
                vplexToArrayVolumesToFlush.put(vplexFullCopyVolume, arrayVolumeToBeResynced);
            }
            Map<URI, String> vplexVolumeIdToDetachStep = new HashMap<URI, String>();

            // Generate pre restore steps
            String waitFor = addPreRestoreResyncSteps(workflow, vplexToArrayVolumesToFlush, vplexVolumeIdToDetachStep, null);

            // Now create a workflow step to natively resynchronize the
            // backend full copy volumes. We execute this after the
            // invalidate cache steps.
            createWorkflowStepForResyncNativeFullCopy(workflow, nativeSystem,
                    nativeFullCopyURIs, waitFor, rollbackMethodNullMethod());

            // Generate post restore steps
            waitFor = addPostRestoreResyncSteps(workflow, vplexToArrayVolumesToFlush, vplexVolumeIdToDetachStep, waitFor);

            // Execute the workflow.
            _log.info("Executing workflow plan");
            String successMsg = String.format(
                    "Resynchronize full copy volumes %s completed successfully", fullCopyURIs);
            FullCopyOperationCompleteCallback wfCompleteCB = new FullCopyOperationCompleteCallback();
            workflow.executePlan(completer, successMsg, wfCompleteCB,
                    new Object[] { fullCopyURIs }, null, null);
            _log.info("Workflow plan executing");
        } catch (Exception e) {
            String failMsg = String.format(
                    "Resynchronize full copy volumes %s failed", fullCopyURIs);
            _log.error(failMsg, e);
            ServiceCoded sc = VPlexApiException.exceptions.resyncFullCopyFailed(
                    fullCopyURIs.toString(), e);
            failStep(completer, opId, sc);
        }
    }

    /**
     * Create a step in the passed workflow to resynchronize the
     * backend full copy volumes with the passed URIs.
     *
     * @param workflow
     *            A reference to a workflow.
     * @param nativeSystem
     *            A reference to the native storage system.
     * @param nativeFullCopyURIs
     *            The URIs of the native full copies.
     * @param waitFor
     *            The step to wait for or null.
     * @param rollbackMethod
     *            A reference to a rollback method or null.
     *
     * @return RESYNC_FULL_COPY_STEP
     */
    private String createWorkflowStepForResyncNativeFullCopy(Workflow workflow,
            StorageSystem nativeSystem, List<URI> nativeFullCopyURIs, String waitFor,
            Workflow.Method rollbackMethod) {
        URI nativeSystemURI = nativeSystem.getId();
        Workflow.Method resyncFullCopyMethod = new Workflow.Method(
                RESYNC_FC_METHOD_NAME, nativeSystemURI, nativeFullCopyURIs,
                Boolean.FALSE);
        workflow.createStep(RESYNC_FULL_COPY_STEP,
                String.format("Resynchronize native full copies: %s", nativeFullCopyURIs), waitFor,
                nativeSystemURI, nativeSystem.getSystemType(), BlockDeviceController.class,
                resyncFullCopyMethod, rollbackMethod, null);
        _log.info("Created workflow step to resynchronize native full copies {}",
                nativeFullCopyURIs);

        return RESYNC_FULL_COPY_STEP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void detachFullCopy(URI vplexURI, List<URI> fullCopyURIs, String opId)
            throws InternalException {
        TaskCompleter completer = null;
        try {
            completer = new VolumeDetachCloneCompleter(fullCopyURIs, opId);
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    DETACH_FULL_COPY_WF_NAME, false, opId);
            _log.info("Created detach full copy workflow with operation id {}", opId);

            // add CG to taskCompleter
            Volume firstFullCopy = getDataObject(Volume.class, fullCopyURIs.get(0), _dbClient);
            BlockObject firstSource = BlockObject.fetch(_dbClient, firstFullCopy.getAssociatedSourceVolume());
            if (!NullColumnValueGetter.isNullURI(firstSource.getConsistencyGroup())) {
                completer.addConsistencyGroupId(firstSource.getConsistencyGroup());
            }

            // Get the native full copy volumes.
            URI nativeSystemURI = null;
            Map<URI, Volume> nativeFullCopyMap = new HashMap<URI, Volume>();
            for (URI fullCopyURI : fullCopyURIs) {
                Volume fullCopyVolume = getDataObject(Volume.class, fullCopyURI, _dbClient);
                Volume nativeFullCopyVolume = VPlexUtil.getVPLEXBackendVolume(fullCopyVolume, true, _dbClient);
                nativeFullCopyMap.put(nativeFullCopyVolume.getId(), nativeFullCopyVolume);
                if (nativeSystemURI == null) {
                    nativeSystemURI = nativeFullCopyVolume.getStorageController();
                }
            }

            // Get the native system.
            StorageSystem nativeSystem = getDataObject(StorageSystem.class, nativeSystemURI, _dbClient);

            // We'll need a list of the native full copy URIs.
            List<URI> nativeFullCopyURIs = new ArrayList<URI>(nativeFullCopyMap.keySet());

            // Create a workflow step to detach the native
            // full copies.
            createWorkflowStepForDetachNativeFullCopy(workflow, nativeSystem,
                    nativeFullCopyURIs, null, null);

            // Execute the workflow.
            _log.info("Executing workflow plan");
            String successMsg = String.format(
                    "Detach full copy volumes %s completed successfully", fullCopyURIs);
            FullCopyOperationCompleteCallback wfCompleteCB = new FullCopyOperationCompleteCallback();
            workflow.executePlan(completer, successMsg, wfCompleteCB,
                    new Object[] { fullCopyURIs }, null, null);
            _log.info("Workflow plan executing");
        } catch (Exception e) {
            String failMsg = String.format(
                    "detach full copy volumes %s failed", fullCopyURIs);
            _log.error(failMsg, e);
            ServiceCoded sc = VPlexApiException.exceptions.detachFullCopyFailed(
                    fullCopyURIs.toString(), e);
            failStep(completer, opId, sc);
        }
    }

    /**
     * Create a step in the passed workflow to detach the
     * backend full copy volumes with the passed URIs.
     *
     * @param workflow
     *            A reference to a workflow.
     * @param nativeSystem
     *            A reference to the native storage system.
     * @param nativeFullCopyURIs
     *            The URIs of the native full copies.
     * @param waitFor
     *            The step to wait for or null.
     * @param rollbackMethod
     *            A reference to a rollback method or null.
     *
     * @return DETACH_FULL_COPY_STEP
     */
    private String createWorkflowStepForDetachNativeFullCopy(Workflow workflow,
            StorageSystem nativeSystem, List<URI> nativeFullCopyURIs, String waitFor,
            Workflow.Method rollbackMethod) {
        URI nativeSystemURI = nativeSystem.getId();
        Workflow.Method detachFullCopyMethod = new Workflow.Method(
                DETACH_FC_METHOD_NAME, nativeSystemURI, nativeFullCopyURIs);
        workflow.createStep(DETACH_FULL_COPY_STEP,
                String.format("Detach native full copies: %s", nativeFullCopyURIs), waitFor,
                nativeSystemURI, nativeSystem.getSystemType(), BlockDeviceController.class,
                detachFullCopyMethod, rollbackMethod, null);
        _log.info("Created workflow step to detach native full copies {}",
                nativeFullCopyURIs);

        return DETACH_FULL_COPY_STEP;
    }

    /**
     * Returns the VirtualArray that is hosting a set of Volumes.
     *
     * @param array
     *            The backend StorageSystem whose volumes are being checked.
     * @param volumes
     *            Collection of volume URIs
     * @return VirtualArray URI of these volumes
     * @throws ControllerException if multiple varrays found
     */
    private URI getVolumesVarray(StorageSystem array, Collection<Volume> volumes)
            throws ControllerException {
        URI varray = null;
        for (Volume volume : volumes) {
            if (volume.getStorageController().equals(array.getId())) {
                if (varray == null) {
                    varray = volume.getVirtualArray();
                } else if (!varray.equals(volume.getVirtualArray())) {
                    VirtualArray varray1 = _dbClient.queryObject(VirtualArray.class, varray);
                    VirtualArray varray2 = _dbClient.queryObject(VirtualArray.class, volume.getVirtualArray());
                    DeviceControllerException ex = DeviceControllerException.exceptions.multipleVarraysInVPLEXExportGroup(
                            array.forDisplay(), 
                            varray1 != null ? varray1.forDisplay() : varray.toString(), 
                            varray2 != null ? varray2.forDisplay() : volume.getVirtualArray().toString());
                    _log.error("Multiple varrays connecting VPLEX to array", ex);
                    throw ex;
                }
            }
        }
        return varray;
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
            failStep(taskCompleter, token, serviceError);
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
                    exportGroup.getId(), exportMask.getId(), storageViewInitiators, new ArrayList<URI>(), addInitStep);

            Workflow.Method addToViewMethod = storageViewAddInitiatorsMethod(vplex.getId(), exportGroup.getId(), exportMask.getId(),
                    storageViewInitiators, null, shared, completer);
            Workflow.Method addToViewRollbackMethod = storageViewAddInitiatorsRollbackMethod(vplex.getId(),
                    exportGroup.getId(), exportMask.getId(), storageViewInitiators, null, addInitStep);
            workflow.createStep(STORAGE_VIEW_ADD_INITS_METHOD, message,
                    null, vplex.getId(), vplex.getSystemType(), this.getClass(),
                    addToViewMethod, addToViewRollbackMethod, addInitStep);

            // Create a Step to add storage ports to the Storage View
            String addPortStep = workflow.createStepId();
            ExportMaskAddInitiatorCompleter portCompleter = new ExportMaskAddInitiatorCompleter(exportGroup.getId(), exportMask.getId(),
                    new ArrayList<URI>(), newTargets, addPortStep);
            Workflow.Method addPortsToViewMethod = storageViewAddStoragePortsMethod(vplex.getId(), exportGroup.getId(), exportMask.getId(),
                    newTargets, portCompleter);

            Workflow.Method addPortsToViewRollbackMethod = storageViewAddStoragePortsRollbackMethod(vplex.getId(), exportGroup.getId(),
                    exportMask.getId(), newTargets, addPortStep);

            workflow.createStep(STORAGE_VIEW_ADD_STORAGE_PORTS_METHOD,
                    String.format("Adding storage ports %s to VPLEX storage View %s", Joiner.on(", ").join(newTargets),
                            exportGroup.getGeneratedName()),
                    addInitStep, vplex.getId(), vplex.getSystemType(),
                    this.getClass(), addPortsToViewMethod, addPortsToViewRollbackMethod, addPortStep);

            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, vplex.getId());
            Map<URI, List<URI>> maskToInitiatorsMap = new HashMap<URI, List<URI>>();
            Set<URI> zoningInitiators = new HashSet<>();
            for (ExportMask mask : exportMasks) {
                boolean sharedMask = false;
                if (sharedExportMask != null) {
                    if (sharedExportMask.getId().equals(mask.getId())) {
                        sharedMask = true;
                    }
                }
                maskToInitiatorsMap.put(mask.getId(), new ArrayList<URI>());
                Set<URI> exportMaskHosts = VPlexUtil.getExportMaskHosts(_dbClient, mask, sharedMask);
                // Only add initiators to this ExportMask that are on the host of the Export Mask
                for (Initiator initiator : initiators) {
                    if (exportMaskHosts.contains(VPlexUtil.getInitiatorHost(initiator))) {
                        maskToInitiatorsMap.get(mask.getId()).add(initiator.getId());
                        zoningInitiators.add(initiator.getId());
                    }
                }
            }

            // Create a Step to add the SAN Zone
            String zoningStepId = workflow.createStepId();
            Workflow.Method zoningMethod = _networkDeviceController
                    .zoneExportAddInitiatorsMethod(exportGroup.getId(), maskToInitiatorsMap);
            List<NetworkZoningParam> zoningParams = NetworkZoningParam
                    .convertExportMaskInitiatorMapsToNetworkZoningParam(exportGroup.getId(), maskToInitiatorsMap, _dbClient);
            Workflow.Method zoningRollbackMethod = _networkDeviceController
                    .zoneExportRemoveInitiatorsMethod(zoningParams);
            zoningStepId = workflow.createStep(ZONING_STEP,
                    String.format("Zone initiator %s to ExportGroup %s(%s)",
                           Joiner.on(", ").join(zoningInitiators), exportGroup.getLabel(), exportGroup.getId()),
                    addPortStep, vplex.getId(), vplex.getSystemType(),
                    _networkDeviceController.getClass(), zoningMethod, zoningRollbackMethod, zoningStepId);
        }
    }

    @Override
    public String addStepsForRestoreVolume(Workflow workflow,
            String waitFor, URI storage, URI pool, URI volume, URI snapshotURI,
            Boolean updateOpStatus, String syncDirection, String opId,
            BlockSnapshotRestoreCompleter completer) throws InternalException {
        BlockSnapshot snapshot = getDataObject(BlockSnapshot.class, snapshotURI, _dbClient);

        URI parentVolumeURI = snapshot.getParent().getURI();
        Volume associatedVPlexVolume = null;
        Volume parentVolume = null;

        if (!NullColumnValueGetter.isNullURI(parentVolumeURI)) {
            parentVolume = _dbClient.queryObject(Volume.class, parentVolumeURI);
        }

        if (parentVolume != null) {
            associatedVPlexVolume = Volume.fetchVplexVolume(_dbClient, parentVolume);
        }

        // Do nothing if this is not a native snapshot or the snapshot's parent is not
        // a VPlex associated volume.
        if ((NullColumnValueGetter.isNotNullValue(snapshot.getTechnologyType()) &&
                !snapshot.getTechnologyType().equals(TechnologyType.NATIVE.toString())) ||
                associatedVPlexVolume == null) {
            _log.info(String
                    .format("Skipping restore volume steps. Snapshot %s is not an array snap or its parent volume is not a VPlex associated volume.",
                            parentVolumeURI));
            return waitFor;
        }

        URI vplexSystemURI = associatedVPlexVolume.getStorageController();
        StorageSystem vplexSystem = _dbClient.queryObject(StorageSystem.class, vplexSystemURI);

        Workflow.Method restoreVolumeMethod = new Workflow.Method(
                RESTORE_VOLUME_METHOD_NAME, vplexSystemURI, snapshotURI);
        workflow.createStep(RESTORE_VPLEX_VOLUME_STEP, String.format(
                "Restore volume %s from snapshot %s",
                volume, snapshotURI), waitFor,
                vplexSystemURI, vplexSystem.getSystemType(),
                VPlexDeviceController.class, restoreVolumeMethod, rollbackMethodNullMethod(), null);
        _log.info(
                "Created workflow step to restore VPLEX backend volume {} from snapshot {}",
                volume, snapshotURI);

        return RESTORE_VPLEX_VOLUME_STEP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restoreVolume(URI vplexURI, URI snapshotURI, String opId)
            throws InternalException {

        BlockSnapshot snapshot = getDataObject(BlockSnapshot.class, snapshotURI, _dbClient);
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    RESTORE_VOLUME_WF_NAME, false, opId);
            _log.info("Created restore volume workflow with operation id {}", opId);

            // Get some info from the snapshot we need to do the native
            // restore of the backend volume. Note that if the snapshot
            // is associated with a CG, then all backend volumes in the
            // CG will be restored using their corresponding snapshots,
            // meaning the VPLEX volumes using those backend volumes
            // will be restored.
            URI parentSystemURI = snapshot.getStorageController();
            StorageSystem parentSystem = getDataObject(StorageSystem.class, parentSystemURI, _dbClient);
            URI parentVolumeURI = snapshot.getParent().getURI();
            Volume parentVolume = getDataObject(Volume.class, parentVolumeURI, _dbClient);
            URI parentPoolURI = parentVolume.getPool();

            // Get the VPLEX system.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);

            // Get the VPLEX volume(s) to be restored.
            List<URI> vplexVolumeURIs = new ArrayList<URI>();
            URI cgURI = snapshot.getConsistencyGroup();
            if (NullColumnValueGetter.isNullURI(cgURI)) {
                // If the snapshot is not in a CG, the only VPLEX
                // volume to restore is the VPLEX volume using the
                // snapshot parent.
                URIQueryResultList queryResults = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getVolumeByAssociatedVolumesConstraint(parentVolumeURI.toString()),
                        queryResults);
                URI vplexVolumeURI = queryResults.iterator().next();
                vplexVolumeURIs.add(vplexVolumeURI);
            } else {
                // Otherwise, get all snapshots in the snapset, get the
                // parent volume for each snapshot, and get the VLPEX
                // volume using the snapshot parent.
                List<BlockSnapshot> cgSnaps = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshot, _dbClient);
                for (BlockSnapshot cgSnapshot : cgSnaps) {
                    URIQueryResultList queryResults = new URIQueryResultList();
                    _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getVolumeByAssociatedVolumesConstraint(cgSnapshot.getParent().getURI()
                                    .toString()),
                            queryResults);
                    URI vplexVolumeURI = queryResults.iterator().next();
                    vplexVolumeURIs.add(vplexVolumeURI);
                }
            }

            // The workflow depends on if the VPLEX volumes are local
            // or distributed.
            String waitFor = null;
            Volume firstVplexVolume = getDataObject(Volume.class, vplexVolumeURIs.get(0), _dbClient);
            if (null == firstVplexVolume.getAssociatedVolumes() || firstVplexVolume.getAssociatedVolumes().isEmpty()) {
                _log.error("VPLEX volume {} has no backend volumes.", firstVplexVolume.forDisplay());
                throw InternalServerErrorException.internalServerErrors.noAssociatedVolumesForVPLEXVolume(firstVplexVolume.forDisplay());
            }
            boolean isLocal = firstVplexVolume.getAssociatedVolumes().size() == 1;
            if (isLocal) {
                // Create a step to invalidate the read cache for each
                // VPLEX volume.
                for (URI vplexVolumeURI : vplexVolumeURIs) {
                    waitFor = createWorkflowStepForInvalidateCache(workflow, vplexSystem,
                            vplexVolumeURI, null, null);
                }

                // Now create a workflow step to natively restore the backend
                // volume from the passed snapshot. Note that if the snapshot
                // is associated with a CG, then block controller will restore
                // all backend volumes in the CG using their corresponding
                // snapshots. We execute this after the invalidate cache. We
                // could execute these in parallel for a little better efficiency,
                // but what if the invalidate cache fails, but the restore succeeds,
                // the cache now has invalid data and a cache read hit could return
                // invalid data.
                createWorkflowStepForRestoreNativeSnapshot(workflow, parentSystem,
                        parentVolumeURI, snapshotURI, parentPoolURI, waitFor,
                        null);
            } else {
                for (URI vplexVolumeURI : vplexVolumeURIs) {
                    // For distributed volumes we take snapshots of and restore the
                    // source backend volume. Before we can do the restore, we need
                    // to detach the mirror of the distributed volume. So, create a 
                    // workflow step to detach it from the source.
                    Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
                    
                    String detachStepId = workflow.createStepId();
                    Workflow.Method restoreVolumeRollbackMethod = createRestoreResyncRollbackMethod(
                            vplexURI, vplexVolumeURI,
                            vplexVolume.getConsistencyGroup(), detachStepId);
                    waitFor = createWorkflowStepForDetachMirror(workflow, vplexSystem,
                            vplexVolume, detachStepId, null,
                            restoreVolumeRollbackMethod);

                    // We now create a step to invalidate the cache for the
                    // VPLEX volume. Note that if this step fails we need to
                    // rollback and reattach the mirror.
                    createWorkflowStepForInvalidateCache(workflow, vplexSystem,
                            vplexVolumeURI, waitFor, rollbackMethodNullMethod());

                    // Now create a workflow step to reattach the mirror to initiate
                    // a rebuild of the mirror for the distributed volume. Note that
                    // these steps will not run until after the native restore, which
                    // only gets executed once, not for every VPLEX volume.
                    createWorkflowStepForAttachMirror(workflow, vplexSystem, vplexVolume,
                            detachStepId, RESTORE_VOLUME_STEP,
                            rollbackMethodNullMethod());
                }

                // Create a workflow step to native restore the backend volume
                // from the passed snapshot. This step is executed after the
                // cache has been invalidated for each VPLEX volume. Note that
                // if the snapshot is associated with a CG, then block controller
                // will restore all backend volumes in the CG using their
                // corresponding snapshots. We could execute this in parallel
                // with the restore for a little better efficiency, but what if
                // the invalidate cache fails, but the restore succeeds, the
                // cache now has invalid data and a cache read hit could return
                // invalid data. If this step fails, then again, we need to
                // be sure and rollback and reattach the mirror. There is
                // nothing to rollback for the cache invalidate step. It just
                // means there will be no read cache hits on the volume for a
                // while until the cache is repopulated.
                createWorkflowStepForRestoreNativeSnapshot(workflow, parentSystem,
                        parentVolumeURI, snapshotURI, parentPoolURI, INVALIDATE_CACHE_STEP,
                        rollbackMethodNullMethod());
            }

            // Execute the workflow.
            _log.info("Executing workflow plan");
            TaskCompleter completer = new BlockSnapshotRestoreCompleter(snapshot, opId);
            String successMsg = String.format(
                    "Restore VPLEX volume from snapshot %s of backend volume %s "
                            + "completed successfully",
                    snapshotURI, parentVolumeURI);
            workflow.executePlan(completer, successMsg);
            _log.info("Workflow plan executing");
        } catch (Exception e) {
            String failMsg = String.format(
                    "Restore VPLEX volume from snapshot %s failed", snapshotURI);
            _log.error(failMsg, e);
            TaskCompleter completer = new BlockSnapshotRestoreCompleter(snapshot, opId);
            ServiceError serviceError = VPlexApiException.errors.restoreVolumeFailed(
                    snapshotURI.toString(), e);
            failStep(completer, opId, serviceError);
        }
    }

    /**
     * Create a step in the passed workflow to do a native restore of
     * the backend snapshot with the passed URI.
     *
     * @param workflow
     *            A reference to a workflow.
     * @param waitFor
     *            The step to wait for or null.
     * @param rollbackMethod
     *            A reference to a rollback method or null.
     * @param nativeSystem
     *            A reference to the native storage system.
     * @param nativeFullCopyURIs
     *            The URIs of the native full copies.
     * @return RESTORE_VOLUME_STEP
     */
    private String createWorkflowStepForRestoreNativeSnapshot(Workflow workflow,
            StorageSystem parentSystem, URI parentVolumeURI, URI snapshotURI, URI parentPoolURI, String waitFor,
            Workflow.Method rollbackMethod) {
        URI parentSystemURI = parentSystem.getId();
        Workflow.Method restoreVolumeMethod = new Workflow.Method(
                RESTORE_VOLUME_METHOD_NAME, parentSystemURI, parentPoolURI,
                parentVolumeURI, snapshotURI, Boolean.FALSE, null);
        workflow.createStep(RESTORE_VOLUME_STEP, String.format(
                "Restore VPLEX backend volume %s from snapshot %s",
                parentVolumeURI, snapshotURI), waitFor,
                parentSystemURI, parentSystem.getSystemType(),
                BlockDeviceController.class, restoreVolumeMethod, rollbackMethod, null);
        _log.info(
                "Created workflow step to restore VPLEX backend volume {} from snapshot {}",
                parentVolumeURI, snapshotURI);

        return RESTORE_VOLUME_STEP;
    }

    /**
     * A method that creates the workflow method to rollback when
     * a volume is restored or resynchronized.
     *
     * @param vplexURI
     *            The URI of the VPLEX system.
     * @param vplexVolumeURI
     *            The URI of the distributed VPLEX volume.
     * @param cgURI
     *            The URI of the volume's CG or null.
     * @param detachStepId
     *            The step id of the detach mirror step.
     *
     * @return A reference to the workflow method.
     */
    private Workflow.Method createRestoreResyncRollbackMethod(URI vplexURI,
            URI vplexVolumeURI, URI cgURI, String detachStepId) {
        return new Workflow.Method(RESTORE_RESYNC_RB_METHOD_NAME, vplexURI,
                vplexVolumeURI, cgURI, detachStepId);
    }

    /**
     * Called if the restore/resync volume operation fails
     *
     * @param vplexURI
     *            The URI of the VPLEX system.
     * @param vplexVolumeURI
     *            The URI of the distributed VPLEX volume.
     * @param cgURI
     *            The URI of the volume's CG or null.
     * @param detachStepId
     *            The Id of the detach mirror step.
     * @param stepId
     *            The workflow step identifier.
     */
    public void rollbackRestoreResync(URI vplexURI, URI vplexVolumeURI,
            URI cgURI, String detachStepId, String stepId) {
        _log.info("Executing rollback of restore/resync volume {} on VPLEX {}",
                new Object[] { vplexVolumeURI, vplexURI });

        String mirrorDeviceName = "";
        try {
            // Update workflow step.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Get the rollback data so we know what to do.
            @SuppressWarnings("unchecked")
            Map<String, String> rollbackData = (Map<String, String>) _workflowService
                    .loadStepData(detachStepId);
            String successWarningMessage = null;
            if (rollbackData != null) {
                boolean reattachMirror = Boolean.parseBoolean(rollbackData.get(REATTACH_MIRROR));
                boolean addVolumeBackToCG = Boolean.parseBoolean(rollbackData.get(ADD_BACK_TO_CG));
                String restoreDeviceName = rollbackData.get(RESTORE_DEVICE_NAME);
                if ((restoreDeviceName != null) || reattachMirror || addVolumeBackToCG) {
                    // Get the API client.
                    StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
                    VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexSystem, _dbClient);
                    _log.info("Got VPLEX API client");

                    // Get the VPLEX volume.
                    Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
                    String vplexVolumeName = vplexVolume.getDeviceLabel();
                    _log.info("Got VPLEX volume");

                    // If the rollback data indicates we need to try an reattach
                    // the remote mirror, do this first.
                    if (reattachMirror) {
                        // Attach the mirror.
                        mirrorDeviceName = rollbackData.get(DETACHED_DEVICE);
                        client.reattachMirrorToDistributedVolume(vplexVolumeName, mirrorDeviceName);
                        _log.info("Reattached the mirror");
                    }

                    // If the rollback data indicates the device name was temporarily changed
                    // and needs to be restored, do so after reattaching the device.
                    if (restoreDeviceName != null) {
                        String modifiedName = null;
                        try {
                            modifiedName = restoreDeviceName.substring(0, VPlexApiConstants.MAX_DEVICE_NAME_LENGTH_FOR_ATTACH_MIRROR);
                            client.renameDistributedDevice(modifiedName, restoreDeviceName);
                        } catch (Exception e) {
                            // We don't fail the rollback workflow step in this case, but instead just log a message
                            // indicating this error. The distribute device for the volume will just have
                            // the modified name.
                            successWarningMessage = String.format("Failed renaming the distributed device %s back "
                                    + " to its orginal name %s after reattaching remote mirror", modifiedName, restoreDeviceName);
                            _log.warn(successWarningMessage);
                        }
                    }

                    // If the rollback data indicates we need to try and
                    // add the volume back to a consistency group, do so
                    // only after the remote mirror has been successfully
                    // reattached.
                    if (addVolumeBackToCG) {
                        ConsistencyGroupManager consistencyGroupManager = getConsistencyGroupManager(vplexVolume);
                        consistencyGroupManager.addVolumeToCg(cgURI, vplexVolume, client, false);
                        _log.info("Added volume back to consistency group.");
                    }
                }
            }

            // Update workflow step state to success.
            if (successWarningMessage != null) {
                WorkflowStepCompleter.stepSucceeded(stepId, successWarningMessage);
            } else {
                WorkflowStepCompleter.stepSucceded(stepId);
            }
            _log.info("Updated workflow step state to success");
        } catch (VPlexApiException vae) {
            _log.error("Exception in restore/resync volume rollback for VPLEX distributed volume" + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception e) {
            _log.error("Exception in restore/resync volume rollback for VPLEX distributed volume " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, VPlexApiException.exceptions
                    .failedAttachingVPlexVolumeMirror(mirrorDeviceName, vplexVolumeURI.toString(), e));
        }
    }

    /**
     * Refreshes the connection status for all VPLEX management servers.
     *
     * @return The list of those to which a connection was successful.
     */
    public List<URI> refreshConnectionStatusForAllVPlexManagementServers() {
        List<URI> activeMgmntServers = new ArrayList<URI>();

        List<StorageProvider> vplexMnmgtServers = CustomQueryUtility
                .getActiveStorageProvidersByInterfaceType(_dbClient,
                        StorageProvider.InterfaceType.vplex.name());
        for (StorageProvider vplexMnmgtServer : vplexMnmgtServers) {
            try {
                VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory,
                        vplexMnmgtServer, _dbClient);
                client.verifyConnectivity();
                activeMgmntServers.add(vplexMnmgtServer.getId());
                vplexMnmgtServer.setConnectionStatus(StorageProvider.ConnectionStatus.CONNECTED.toString());
            } catch (Exception e) {
                _log.warn("Can't connect to VPLEX management server {}", vplexMnmgtServer.getIPAddress());
                vplexMnmgtServer.setConnectionStatus(StorageProvider.ConnectionStatus.NOTCONNECTED.toString());
            } finally {
                _dbClient.updateObject(vplexMnmgtServer);
            }
        }

        return activeMgmntServers;
    }

    /**
     * Given an ExportGroup and a hostURI, finds all the ExportMasks in the ExportGroup (if any)
     * corresponding to that host.
     *
     * @param exportGroup
     *            -- ExportGroup object
     * @param hostURI
     *            -- URI of host
     * @param vplexURI
     *            -- URI of VPLEX StorageSystem
     * @return List<ExportMask> or empty list if not found
     * @throws Exception
     */
    private List<ExportMask> getExportMaskForHost(ExportGroup exportGroup, URI hostURI, URI vplexURI) throws Exception {
        List<ExportMask> results = new ArrayList<ExportMask>();
        if (exportGroup == null || exportGroup.getExportMasks() == null) {
            return null;
        }
        // Create a list of sharedExportMask URIs for the src varray and ha varray if its set in the altVirtualArray
        // in the exportGroup
        List<URI> sharedExportMaskURIs = new ArrayList<URI>();
        ExportMask sharedExportMask = VPlexUtil.getSharedExportMaskInDb(exportGroup, vplexURI, _dbClient, exportGroup.getVirtualArray(),
                null, null);
        if (sharedExportMask != null) {
            sharedExportMaskURIs.add(sharedExportMask.getId());
        }
        if (exportGroup.hasAltVirtualArray(vplexURI.toString())) {
            URI haVarray = URI.create(exportGroup.getAltVirtualArrays().get(vplexURI.toString()));
            ExportMask haSharedExportMask = VPlexUtil.getSharedExportMaskInDb(exportGroup, vplexURI, _dbClient, haVarray,
                    null, null);
            if (haSharedExportMask != null) {
                sharedExportMaskURIs.add(haSharedExportMask.getId());
            }
        }
        List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, vplexURI);
        for (ExportMask exportMask : exportMasks) {
            boolean shared = false;
            if (!sharedExportMaskURIs.isEmpty()) {
                if (sharedExportMaskURIs.contains(exportMask.getId())) {
                    shared = true;
                }
            }
            if (VPlexUtil.getExportMaskHosts(_dbClient, exportMask, shared).contains(hostURI)) {
                results.add(exportMask);
            }
        }
        return results;
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
     * Determines if the controller can support migration for the passed VPLEX volume.
     *
     * @param volume
     *            A reference to a VPLEX volume.
     * @param varrayURI
     *            A reference to a varray or null.
     *
     * @return true if migration is supported, false otherwise.
     */
    public static boolean migrationSupportedForVolume(Volume volume, URI varrayURI, DbClient dbClient) {
        boolean supported = true;
        // Migration is supported for all volumes that were not ingested.
        if (volume.isIngestedVolumeWithoutBackend(dbClient)) {
            VirtualPool vpool = dbClient.queryObject(VirtualPool.class,
                    volume.getVirtualPool());
            // Migration is supported for all local volumes.
            if (VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(
                    vpool.getHighAvailability())) {
                StorageSystem vplexSystem = dbClient.queryObject(StorageSystem.class,
                        volume.getStorageController());
                try {
                    VPlexApiFactory apiFactory = VPlexApiFactory.getInstance();
                    VPlexApiClient client = getVPlexAPIClient(apiFactory, vplexSystem,
                            dbClient);
                    VPlexVirtualVolumeInfo vvInfo = client.getVirtualVolumeStructure(volume
                            .getDeviceLabel());
                    VPlexDistributedDeviceInfo ddInfo = (VPlexDistributedDeviceInfo) vvInfo
                            .getSupportingDeviceInfo();
                    List<VPlexDeviceInfo> localDeviceInfoList = ddInfo.getLocalDeviceInfo();
                    for (VPlexDeviceInfo localDeviceInfo : localDeviceInfoList) {
                        _log.info("localDeviceInfo: {}, {}", localDeviceInfo.getName(), localDeviceInfo.getCluster());

                        // If no varray is passed, then we need to check both
                        // sides of the volume, else only the side specified by
                        // the passed varray.
                        if (varrayURI != null) {
                            _log.info("varrayURI:{}", varrayURI);
                            String varrayCluster = ConnectivityUtil.getVplexClusterForVarray(
                                    varrayURI, vplexSystem.getId(), dbClient);
                            _log.info("varrayCluster:{}", varrayCluster);
                            if (!localDeviceInfo.getCluster().contains(varrayCluster)) {
                                continue;
                            }
                        }

                        // For distributed volumes, the local device must be built
                        // on a single extent.
                        _log.info("Local device: {}", localDeviceInfo.getName());
                        _log.info("Extent count: {}", localDeviceInfo.getExtentInfo().size());
                        if (localDeviceInfo.getExtentInfo().size() != 1) {
                            supported = false;
                            break;
                        }
                    }
                } catch (VPlexApiException vae) {
                    _log.error("Exception checking if migration supported for volume:", vae);
                    throw vae;
                } catch (Exception ex) {
                    _log.error("Exception checking if migration supported for volume", ex);
                    throw VPlexApiException.exceptions.failedGettingMigrationSupportedForVolume(vplexSystem.getId().toString(),
                            volume.getLabel());
                }
            }
        }
        return supported;
    }

    /**
     * <p>
     * Here we should have already created any underlying volumes. What remains to be done: 1. Export the underlying Storage Volumes from
     * the array to the VPlex. 2. Create the mirror device and attach it as a mirror to the source virtual volume
     *
     * @param workflow
     *            The workflow to which the steps are added.
     * @param waitFor
     *            The previous workflow step for which these steps will wait
     * @param volumes
     *            The volume descriptors representing the mirror and the its associated backend volume.
     * @param taskId
     *            The workflow taskId
     */

    public String addStepsForCreateMirrors(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId) throws ControllerException {

        try {
            // Get only the VPlex mirrors from the descriptors.
            List<VolumeDescriptor> vplexLocalMirrors = VolumeDescriptor.filterByType(volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_LOCAL_MIRROR },
                    new VolumeDescriptor.Type[] {});

            // If there are no VPlex mirrors, just return
            if (vplexLocalMirrors.isEmpty()) {
                return waitFor;
            }

            // Build some needed maps to get started.
            Map<URI, StorageSystem> arrayMap = buildArrayMap(volumes, Type.BLOCK_DATA);
            Map<URI, Volume> volumeMap = buildVolumeMap(volumes, Type.BLOCK_DATA);

            // Set the project and tenant to those of an underlying volume.
            // These are used to set the project and tenant of a new ExportGroup if needed.
            Volume firstVolume = volumeMap.values().iterator().next();
            URI projectURI = firstVolume.getProject().getURI();
            URI tenantURI = firstVolume.getTenant().getURI();

            // Segregate the volumes by Device.
            Map<URI, List<VolumeDescriptor>> vplexDescMap = VolumeDescriptor
                    .getDeviceMap(vplexLocalMirrors);

            // For each VPLEX mirror to be provisioned (there will be only one for Vplex Local
            // Volume, Vplex Distributed Volume could have one or two mirrors, two mirrors mean
            // one mirror on each leg)
            String lastStep = VPLEX_STEP;
            for (URI vplexURI : vplexDescMap.keySet()) {
                StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
                try {
                    // Now we need to do the necessary zoning and export steps to ensure
                    // the VPlex can see these new backend volumes.
                    createWorkflowStepsForBlockVolumeExport(workflow, vplexSystem, arrayMap,
                            volumeMap, projectURI, tenantURI, waitFor);
                } catch (Exception ex) {
                    _log.error("Could not create volumes for vplex: " + vplexURI, ex);
                    TaskCompleter completer = new VPlexTaskCompleter(Volume.class, vplexURI,
                            taskId, null);
                    ServiceError serviceError = VPlexApiException.errors.jobFailed(ex);
                    completer.error(_dbClient, serviceError);
                    throw ex;
                }

                // Now create each of the Vplex Mirror Devices that may be necessary.
                List<URI> vplexMirrorURIs = VolumeDescriptor.getVolumeURIs(vplexDescMap
                        .get(vplexURI));

                // Now make a Step to create the Mirror.
                String mirrorStep = workflow.createStepId();
                lastStep = workflow.createStep(
                        VPLEX_STEP,
                        String.format("VPlex %s creating mirrors:%n%s",
                                vplexSystem.getIpAddress(),
                                BlockDeviceController.getVolumesMsg(_dbClient, vplexMirrorURIs)),
                        EXPORT_STEP, vplexURI, vplexSystem.getSystemType(), this.getClass(),
                        createMirrorsMethod(vplexURI, vplexMirrorURIs, taskId),
                        rollbackCreateMirrorsMethod(vplexURI, vplexMirrorURIs, mirrorStep),
                        mirrorStep);
            }
            return lastStep;
        } catch (Exception ex) {
            throw VPlexApiException.exceptions.addStepsForCreateMirrors(ex);
        }
    }

    /**
     * Here for each mirror(normally there will be only one) we add a step to detach mirror
     * from virtual volume and a step to promote detach mirror to an independent vplex volume.
     *
     * @param workflow
     *            The workflow to which the steps are added.
     * @param waitFor
     *            The previous workflow step for which these steps will wait.
     * @param vplexURI
     *            The vplex storage system URI.
     * @param mirrors
     *            The mirrors that needs to be promoted to independent volumes.
     * @param promotees
     *            The volume objects that will be used for the mirrors that will be promoted.
     * @param taskId
     *            The workflow taskId.
     *
     * @return The workflow step for which any additional steps should wait.
     *
     * @throws ControllerException
     *             When an error occurs configuring the
     *             promote mirror workflow steps.
     */
    public String addStepsForPromoteMirrors(Workflow workflow, String waitFor, URI vplexURI,
            List<URI> mirrors, List<URI> promotees, String taskId) throws ControllerException {
        try {
            for (URI mirrorURI : mirrors) {
                // Find the volume this mirror will be promoted to
                Volume promotedVolumeForMirror = findPromotedVolumeForMirror(mirrorURI, promotees);

                // Add a step to detach mirror
                waitFor = addStepsForDetachMirror(workflow, waitFor, vplexURI, mirrorURI, promotedVolumeForMirror.getId(), taskId);

                if (promotedVolumeForMirror == null) {
                    throw new IllegalStateException("No volume available for the promotion of mirror " + mirrorURI);
                }

                // Create a step for promoting the mirror.
                String stepId = workflow.createStepId();
                waitFor = workflow.createStep(
                        PROMOTE_MIRROR_STEP,
                        String.format("Promote mirror: %s", mirrorURI),
                        waitFor, vplexURI,
                        DiscoveredDataObject.Type.vplex.name(), this.getClass(),
                        promoteMirrorMethod(vplexURI, mirrorURI, promotedVolumeForMirror.getId()),
                        rollbackPromoteMirrorMethod(vplexURI, mirrorURI, promotedVolumeForMirror.getId(), stepId), stepId);
            }
            return waitFor;
        } catch (Exception ex) {
            throw VPlexApiException.exceptions.addStepsForPromoteMirrors(ex);
        }
    }

    private Volume findPromotedVolumeForMirror(URI vplexMirrorURI, List<URI> promotees) {
        VplexMirror mirror = _dbClient.queryObject(VplexMirror.class, vplexMirrorURI);
        List<Volume> promotedVolumes = _dbClient.queryObject(Volume.class, promotees);
        for (Volume promotee : promotedVolumes) {
            OpStatusMap statusMap = promotee.getOpStatus();
            for (Map.Entry<String, Operation> entry : statusMap.entrySet()) {
                Operation operation = entry.getValue();
                if (operation.getAssociatedResourcesField().contains(mirror.getId().toString())) {
                    return promotee;
                }
            }
        }
        throw new IllegalStateException("No volume available for the promotion of mirror " + mirror.getId());
    }

    private Workflow.Method promoteMirrorMethod(URI vplexURI, URI vplexMirrorURI, URI promoteVolumeURI) {
        return new Workflow.Method(PROMOTE_MIRROR_METHOD_NAME, vplexURI, vplexMirrorURI, promoteVolumeURI);
    }

    /**
     * This method creates the virtual volume from the detached mirror device.
     *
     * @param vplexURI
     *            The vplex storage system URI
     * @param vplexMirrorURI
     *            The URI of the vplex mirror that needs to be promoted to the virtual volume
     * @param promoteVolumeURI
     *            The URI of the volume will be used as a promoted vplex volume
     * @param stepId
     *            The worflow stepId
     *
     * @throws WorkflowException
     *             When an error occurs updating the workflow step
     *             state.
     */
    public void promoteMirror(URI vplexURI, URI vplexMirrorURI, URI promoteVolumeURI, String stepId) throws WorkflowException {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexURI, _dbClient);
            VplexMirror vplexMirror = getDataObject(VplexMirror.class, vplexMirrorURI, _dbClient);
            Volume sourceVplexVolume = getDataObject(Volume.class, vplexMirror.getSource().getURI(), _dbClient);
            Volume promoteVolume = _dbClient.queryObject(Volume.class, promoteVolumeURI);

            // Find virtual volume that should have been created when we did detach mirror.
            // Virtual volume is created with the same name as the device name.
            VPlexVirtualVolumeInfo vvInfo = client.findVirtualVolume(vplexMirror.getDeviceLabel(), null);

            // Get the backend volume for this promoted VPLEX volume.
            StringSet assocVolumes = vplexMirror.getAssociatedVolumes();

            // Get the ViPR label for the promoted VPLEX volume.
            String promotedLabel = String.format("%s-%s", sourceVplexVolume.getLabel(), vplexMirror.getLabel());

            // Rename the vplex volume created using device detach mirror. If custom naming is enabled
            // generate the custom name, else the name follows the default naming convention and must
            // be renamed to append the "_vol" suffix.
            String newVolumeName = null;
            try {
                if (CustomVolumeNamingUtils.isCustomVolumeNamingEnabled(customConfigHandler, DiscoveredDataObject.Type.vplex.name())) {
                    String customConfigName = CustomConfigConstants.CUSTOM_VOLUME_NAME;
                    Project project = _dbClient.queryObject(Project.class, promoteVolume.getProject().getURI());
                    TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, promoteVolume.getTenant().getURI());
                    DataSource customNameDataSource = CustomVolumeNamingUtils.getCustomConfigDataSource(
                            project, tenant, promotedLabel, vvInfo.getWwn(), null, dataSourceFactory,
                            customConfigName, _dbClient);
                    if (customNameDataSource != null) {
                        newVolumeName = CustomVolumeNamingUtils.getCustomName(customConfigHandler,
                                customConfigName, customNameDataSource, DiscoveredDataObject.Type.vplex.name());
                    }
                    // Rename the vplex volume created using device detach mirror,
                    vvInfo = CustomVolumeNamingUtils.renameVolumeOnVPlex(vvInfo, newVolumeName, client);
                    promotedLabel = newVolumeName;
                } else {
                    // Build the name for volume so as to rename the vplex volume that is created
                    // with the same name as the device name to follow the name pattern _vol
                    // as the suffix for the vplex volumes
                    StringBuilder volumeNameBuilder = new StringBuilder();
                    volumeNameBuilder.append(vplexMirror.getDeviceLabel());
                    volumeNameBuilder.append(VPlexApiConstants.VIRTUAL_VOLUME_SUFFIX);
                    newVolumeName = volumeNameBuilder.toString();
                    // Rename the vplex volume created using device detach mirror,
                    vvInfo = CustomVolumeNamingUtils.renameVolumeOnVPlex(vvInfo, newVolumeName, client);
                }
            } catch (Exception e) {
                _log.warn(String.format("Error renaming promoted VPLEX volume %s", promoteVolumeURI), e);
            }

            _log.info(String.format("Renamed promoted virtual volume: %s path: %s", vvInfo.getName(), vvInfo.getPath()));

            // Fill in the details for the promoted vplex volume
            promoteVolume.setLabel(promotedLabel);
            promoteVolume.setNativeId(vvInfo.getPath());
            promoteVolume.setNativeGuid(vvInfo.getPath());
            promoteVolume.setDeviceLabel(vvInfo.getName());
            promoteVolume.setThinlyProvisioned(vvInfo.isThinEnabled());
            promoteVolume.setWWN(vvInfo.getWwn());
            // For Vplex virtual volumes set allocated capacity to 0 (cop-18608)
            promoteVolume.setAllocatedCapacity(0L);
            promoteVolume.setCapacity(vplexMirror.getCapacity());
            promoteVolume.setProvisionedCapacity(vplexMirror.getProvisionedCapacity());
            promoteVolume.setVirtualPool(vplexMirror.getVirtualPool());
            promoteVolume.setVirtualArray(vplexMirror.getVirtualArray());
            promoteVolume.setStorageController(vplexMirror.getStorageController());
            promoteVolume.setSystemType(DiscoveredDataObject.Type.vplex.name());
            promoteVolume.setPool(NullColumnValueGetter.getNullURI());
            promoteVolume.setAssociatedVolumes(new StringSet(assocVolumes));
            promoteVolume.setThinlyProvisioned(vplexMirror.getThinlyProvisioned());
            promoteVolume.setThinVolumePreAllocationSize(vplexMirror.getThinPreAllocationSize());
            // VPLEX volumes created by VIPR have syncActive set to true hence setting same value for promoted vplex
            // volumes
            promoteVolume.setSyncActive(true);

            // Also, we update the name portion of the project and tenant URIs
            // to reflect the new name. This is necessary because the API
            // to search for volumes by project, extracts the name portion of the
            // project URI to get the volume name.
            NamedURI namedURI = promoteVolume.getProject();
            namedURI.setName(promotedLabel);
            promoteVolume.setProject(namedURI);
            namedURI = promoteVolume.getTenant();
            namedURI.setName(promotedLabel);
            promoteVolume.setTenant(namedURI);

            // Remove mirror from the source VPLEX volume
            sourceVplexVolume.getMirrors().remove(vplexMirror.getId().toString());
            _dbClient.updateObject(sourceVplexVolume);

            // Delete the mirror object
            _dbClient.removeObject(vplexMirror);

            // Persist changes for the newly promoted volume
            _dbClient.updateObject(promoteVolume);

            WorkflowStepCompleter.stepSucceded(stepId);

        } catch (VPlexApiException vae) {
            _log.error("Exception promoting mirror volume: " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception promoting mirror volume: " + ex.getMessage(), ex);
            ServiceError serviceError = VPlexApiException.errors.promoteMirrorFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    private Workflow.Method rollbackPromoteMirrorMethod(URI vplexURI, URI mirrorURI, URI promoteeURI, String executeStepId) {
        return new Workflow.Method(RB_PROMOTE_MIRROR_METHOD_NAME, vplexURI, mirrorURI, promoteeURI, executeStepId);
    }

    /**
     * Here we try to delete the virtual volume thats created from the mirror and then try to
     * reattach the mirror so as to leave the original state. This is only going to be best effort.
     * Delete the volume objects that were created as promotees.
     *
     * @param promotees
     *            The URIs of the volumes that were supposed to be promoted from mirror.
     * @param executeStepId
     *            step Id of the execute step
     * @param stepId
     *            The stepId used for completion.
     *
     * @throws WorkflowException
     *             When an error occurs updating the workflow step
     *             state.
     */
    public void rollbackPromoteMirror(URI vplexURI, URI mirrorURI, URI promoteeURI, String executeStepId, String stepId)
            throws WorkflowException {
        try {
            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexURI, _dbClient);

            VplexMirror vplexMirror = _dbClient.queryObject(VplexMirror.class, mirrorURI);
            // Get source volume for the mirror
            Volume sourceVplexVolume = getDataObject(Volume.class, vplexMirror.getSource().getURI(), _dbClient);

            String locality = null;
            if (sourceVplexVolume.getAssociatedVolumes() != null && sourceVplexVolume.getAssociatedVolumes().size() > 1) {
                locality = VPlexApiConstants.DISTRIBUTED_VIRTUAL_VOLUME;
            } else {
                locality = VPlexApiConstants.LOCAL_VIRTUAL_VOLUME;
            }

            // Delete the virtual volume that should have been created when we did detach mirror.
            // Virtual volume is created with the same name as the device name.
            // Delete the virtual volume only, donot tear down
            client.destroyVirtualVolume(vplexMirror.getDeviceLabel());

            // Attach the mirror device back to the source device
            client.attachMirror(locality, sourceVplexVolume.getDeviceLabel(), vplexMirror.getDeviceLabel());
            _log.info("Successfully re-attached mirror %s to the source volume %s during rollback. ", vplexMirror.getDeviceLabel(),
                    sourceVplexVolume.getDeviceLabel());

        } catch (Exception e) {
            // If exception occurs that means mirror is already detached and we couldn't reattach
            // So cleanup database objects related to a mirror.
            VplexMirror vplexMirror = _dbClient.queryObject(VplexMirror.class, mirrorURI);
            Volume sourceVplexVolume = getDataObject(Volume.class, vplexMirror.getSource().getURI(), _dbClient);

            // Remove mirror from the source VPLEX volume
            sourceVplexVolume.getMirrors().remove(vplexMirror.getId().toString());
            _dbClient.updateObject(sourceVplexVolume);
            _log.info("Removed mirror %s from source volume %s", mirrorURI, sourceVplexVolume.getId());

            // Delete mirror and associated volume from database
            if (null != vplexMirror.getAssociatedVolumes()) {
                for (String assocVolumeId : vplexMirror.getAssociatedVolumes()) {
                    Volume volume = _dbClient.queryObject(Volume.class, URI.create(assocVolumeId));
                    _dbClient.removeObject(volume);
                }
            }

            // Delete the mirror object
            _dbClient.removeObject(vplexMirror);

            _log.error("Error during rollback of promote mirror: {}", e.getMessage(), e);
        } finally {
            // Delete the volume that was supposed to be promoted volume
            Volume volume = _dbClient.queryObject(Volume.class, promoteeURI);
            _dbClient.removeObject(volume);

            // Return success so rollback continues
            WorkflowStepCompleter.stepSucceded(stepId);
        }
    }

    /**
     * Here we detach the mentioned mirror device from the source device and
     * dismantle mirror device.
     *
     * @param workflow
     *            The workflow to which the steps are added.
     * @param waitFor
     *            The previous workflow step for which these steps will wait
     * @param vplexURI
     *            The vplex storage system URI
     * @param mirrorURI
     *            The URI of the mirror that needs to be detached
     * @param taskId
     *            The workflow taskId
     */
    public String addStepsForDetachAndDeleteMirror(Workflow workflow, String waitFor,
            URI vplexURI, URI mirrorURI, String taskId) throws ControllerException {
        try {

            // Add a step to detach mirror device
            String detachStep = workflow.createStepId();
            waitFor = workflow.createStep(
                    VPLEX_STEP,
                    String.format("VPlex %s detaching mirror:%n%s",
                            vplexURI, mirrorURI),
                    waitFor, vplexURI,
                    DiscoveredDataObject.Type.vplex.name(), this.getClass(),
                    detachMirrorDeviceMethod(vplexURI, mirrorURI, null, true),
                    rollbackMethodNullMethod(), detachStep);

            // Add a step to delete mirror device
            String deleteMirrorStep = workflow.createStepId();
            waitFor = workflow.createStep(
                    VPLEX_STEP,
                    String.format("VPlex %s deleting mirror:%n%s",
                            vplexURI, mirrorURI),
                    waitFor, vplexURI,
                    DiscoveredDataObject.Type.vplex.name(), this.getClass(),
                    deleteMirrorDeviceMethod(vplexURI, mirrorURI),
                    rollbackDeleteMirrorDeviceMethod(vplexURI, mirrorURI), deleteMirrorStep);

            // Make a list of ExportGroups that is used.
            List<URI> exportGroupList = new ArrayList<URI>();

            // Create a series of steps to remove the volume from the Export Groups.
            // This list will contain only one backend volume uri for the vplex mirror
            List<URI> backendVolURIs = new ArrayList<URI>();
            boolean unexportStepsAdded = false;
            VplexMirror mirror = _dbClient.queryObject(VplexMirror.class, mirrorURI);
            if (mirror.getAssociatedVolumes() != null) {
                for (String volumeId : mirror.getAssociatedVolumes()) {
                    URI volumeURI = new URI(volumeId);
                    Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                    // In order to add volume URI to backendVolURIs for the the backend volumes
                    // that needs to be deleted we are checking for volume nativeId as well,
                    // because its possible that we were not able to create backend volume due
                    // to SMIS communication and rollback didn't clean up VplexMirror and its
                    // associated volumes. So in such a case nativeId will be null and we just
                    // want to skip sending this volume URI to SMIS, else it fails with null
                    // reference when user attempts to cleanup this failed mirror.
                    if (volume == null || volume.getInactive() == true || volume.getNativeId() == null) {
                        continue;
                    }
                    backendVolURIs.add(volume.getId());
                }

                if (!backendVolURIs.isEmpty()) {
                    unexportStepsAdded = vplexAddUnexportVolumeWfSteps(workflow, VPLEX_STEP,
                            backendVolURIs, exportGroupList);
                }
            }

            // If we unexported a backend volume, add a step to forget those volumes.
            if (unexportStepsAdded) {
                waitFor = UNEXPORT_STEP;
                // Add a step to forget the backend volumes for the deleted
                // VPLEX volumes on this VPLEX system.
                addStepToForgetVolumes(workflow, vplexURI, backendVolURIs, waitFor);
            } else {
                waitFor = VPLEX_STEP;
            }
            return waitFor;
        } catch (Exception ex) {
            throw VPlexApiException.exceptions.addStepsForDetachAndDeleteMirror(ex);
        }
    }

    /**
     * Here we detach the mentioned mirror device from the source device.
     * This is called only during promote mirror. Here the third argument
     * for detachMirrorDeviceMethod is set to false which will be used to
     * not to discard device and is converted into virtual volume.
     *
     * @param workflow
     *            The workflow to which the steps are added.
     * @param waitFor
     *            The previous workflow step for which these steps will wait
     * @param vplexURI
     *            The vplex storage system URI
     * @param mirrorURI
     *            The URI of the mirror that needs to be detached
     * @param taskId
     *            The workflow taskId
     *
     */
    public String addStepsForDetachMirror(Workflow workflow, String waitFor,
            URI vplexURI, URI mirrorURI, URI promotedVolumeURI, String taskId) throws ControllerException {
        try {
            String detachStep = workflow.createStepId();
            waitFor = workflow.createStep(
                    VPLEX_STEP,
                    String.format("VPlex %s detaching mirror:%n%s",
                            vplexURI, mirrorURI),
                    waitFor, vplexURI,
                    DiscoveredDataObject.Type.vplex.name(), this.getClass(),
                    detachMirrorDeviceMethod(vplexURI, mirrorURI, promotedVolumeURI, false),
                    rollbackMethodNullMethod(), detachStep);

            return waitFor;
        } catch (Exception ex) {
            throw VPlexApiException.exceptions.addStepsForDetachMirror(ex);
        }
    }

    @Override
    /*
     * This method is use only for creating mirrors for Vplex Virtual Volume
     */
    public void attachContinuousCopies(URI vplexURI, List<VolumeDescriptor> volumes, URI sourceVolumeURI, String taskId)
            throws ControllerException {
        // allUris will contain uri for the vplex mirror and back-end volume for the mirror
        List<URI> allUris = VolumeDescriptor.getVolumeURIs(volumes);

        // Get only vplex mirror backend volume.
        Map<URI, Volume> volumeMap = buildVolumeMap(volumes, Type.BLOCK_DATA);

        // volumesURIs will contain the soureVolumeURI to which mirror will be attached
        // and the backend volume URI of the vplex mirror.
        List<URI> volumesURIs = new ArrayList<URI>();
        volumesURIs.add(sourceVolumeURI);
        volumesURIs.addAll(volumeMap.keySet());

        VplexMirrorTaskCompleter completer = new VplexMirrorTaskCompleter(Volume.class, volumesURIs, taskId);
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    ATTACH_MIRRORS_WF_NAME, true, taskId);
            String waitFor = null; // the wait for key returned by previous call

            // First, call the BlockDeviceController to add its methods to create backend volume for the Vplex mirror.
            waitFor = _blockDeviceController.addStepsForCreateVolumes(
                    workflow, waitFor, volumes, taskId);

            // Add steps for creating mirror
            waitFor = addStepsForCreateMirrors(workflow, waitFor, volumes, taskId);
            String successMessage = "Create mirror successful for: " + allUris.toString();

            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            _log.error("Could not create mirror for the source volume " + sourceVolumeURI + ". Block Mirror and Backend volume URI are: "
                    + allUris, ex);
            ServiceError serviceError = VPlexApiException.errors.attachContinuousCopyFailed(
                    sourceVolumeURI.toString(), ex);
            failStep(completer, taskId, serviceError);
        }
    }

    @Override
    public void deactivateMirror(URI vplexURI, URI mirrorURI, List<VolumeDescriptor> volumeDescriptors,
            String taskId) throws InternalException {
        VplexMirrorDeactivateCompleter completer = new VplexMirrorDeactivateCompleter(mirrorURI, taskId);
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    DEACTIVATE_MIRROR_WF_NAME, true, taskId);
            String waitFor = null; // the wait for key returned by previous call

            // Add steps for detaching and deleting mirror
            waitFor = addStepsForDetachAndDeleteMirror(workflow, waitFor, vplexURI, mirrorURI, taskId);

            // Next, call the BlockDeviceController to add its methods.
            waitFor = _blockDeviceController.addStepsForDeleteVolumes(
                    workflow, waitFor, volumeDescriptors, taskId);

            String successMessage = "Deactivate mirror successful for: " + mirrorURI;
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            _log.error("Deactivate mirror failed for mirror " + mirrorURI, ex);
            ServiceError serviceError = VPlexApiException.errors.deactivateMirrorFailed(ex);
            failStep(completer, taskId, serviceError);
        }
    }

    @Override
    public void detachContinuousCopies(URI vplexURI, URI sourceVolumeURI, List<URI> mirrors, List<URI> promotees,
            String taskId) throws InternalException {
        VplexMirrorTaskCompleter completer = null;
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    DETACH_MIRROR_WF_NAME, true, taskId);
            String waitFor = null; // the wait for key returned by previous call

            // Add steps for detaching and promoting mirror to a virtual volume
            waitFor = addStepsForPromoteMirrors(workflow, waitFor, vplexURI, mirrors, promotees, taskId);
            List<URI> volumesWithTasks = new ArrayList<URI>(promotees);
            volumesWithTasks.add(sourceVolumeURI);

            completer = new VplexMirrorTaskCompleter(Volume.class, volumesWithTasks, taskId);

            String successMessage = "Detach mirror successful for: " + mirrors;
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            _log.error("Detach mirror failed for mirror " + mirrors, ex);
            for (URI uri : promotees) {
                Volume volume = _dbClient.queryObject(Volume.class, uri);
                _dbClient.removeObject(volume);
            }
            ServiceError serviceError = VPlexApiException.errors.detachContinuousCopyFailed(ex);
            failStep(completer, taskId, serviceError);
        }
    }

    /**
     * Returns a Workflow.Method for creating Mirrors.
     *
     * @param vplexURI
     *            URI of the VPlex StorageSystem
     * @param vplexVolumeURIs
     *            URI of the mirrors to be created.
     * @param workflowTaskId
     *            The workflow taskId.
     * @return A reference to the workflow method to create and attach mirror
     */
    private Workflow.Method createMirrorsMethod(URI vplexURI, List<URI> vplexMirrorURIs, String workflowTaskId) {
        return new Workflow.Method(CREATE_MIRRORS_METHOD_NAME, vplexURI, vplexMirrorURIs, workflowTaskId);
    }

    /**
     * Do the creation of a VPlex Mirror device and attach it as a mirror to the Virtual Volume.
     * This is called as a Workflow Step.
     * NOTE: The parameters here must match createMirrorsMethod above (except stepId).
     *
     * @param vplexURI
     *            URI of the VPlex StorageSystem
     * @param vplexMirrorURIs
     *            URI of the mirrors to be created.
     * @param workflowTaskId
     *            The workflow taskId.
     * @param stepId
     *            The stepId used for completion.
     *
     * @throws WorkflowException
     *             When an error occurs updating the workflow step
     *             state.
     */
    public void createMirrors(URI vplexURI, List<URI> vplexMirrorURIs, String workflowTaskId, String stepId) throws WorkflowException {
        List<VolumeInfo> rollbackData = new ArrayList<VolumeInfo>();
        List<URI> createdVplexMirrorURIs = new ArrayList<URI>();
        VplexMirrorTaskCompleter completer = new VplexMirrorTaskCompleter(VplexMirror.class, vplexMirrorURIs, workflowTaskId);
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            // Get the API client.
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

            // Make a map of StorageSystem ids to Storage System
            Map<URI, StorageSystem> storageMap = new HashMap<URI, StorageSystem>();

            // Make a map of Mirrors to Storage Volumes.
            Map<VplexMirror, Volume> mirrorMap = new HashMap<VplexMirror, Volume>();
            for (URI vplexMirrorURI : vplexMirrorURIs) {
                VplexMirror vplexMirror = getDataObject(VplexMirror.class, vplexMirrorURI, _dbClient);
                // Find the underlying Storage Volume, there will be only one associated storage volume
                for (String associatedVolume : vplexMirror.getAssociatedVolumes()) {
                    Volume storageVolume = getDataObject(Volume.class, new URI(associatedVolume), _dbClient);
                    URI storageSystemId = storageVolume.getStorageController();
                    if (storageMap.containsKey(storageSystemId) == false) {
                        StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageSystemId);
                        storageMap.put(storageSystemId, storage);
                    }
                    mirrorMap.put(vplexMirror, storageVolume);
                }
            }

            // Now make a call to the VPlexAPIClient.createDeviceAndAttachAsMirror for each vplex mirror.
            StringBuilder buf = new StringBuilder();
            buf.append("Vplex: " + vplexURI + " created mirror(s): ");
            for (VplexMirror vplexMirror : mirrorMap.keySet()) {
                URI vplexMirrorId = vplexMirror.getId();
                Volume sourceVplexVolume = getDataObject(Volume.class, vplexMirror.getSource().getURI(), _dbClient);
                VPlexVirtualVolumeInfo vplexVolumeInfo = new VPlexVirtualVolumeInfo();
                vplexVolumeInfo.setName(sourceVplexVolume.getDeviceLabel());
                vplexVolumeInfo.setPath(sourceVplexVolume.getNativeId());
                if (null == sourceVplexVolume.getAssociatedVolumes() || sourceVplexVolume.getAssociatedVolumes().isEmpty()) {
                    _log.error("VPLEX volume {} has no backend volumes.", sourceVplexVolume.forDisplay());
                    throw InternalServerErrorException.internalServerErrors
                            .noAssociatedVolumesForVPLEXVolume(sourceVplexVolume.forDisplay());
                }
                if (sourceVplexVolume.getAssociatedVolumes().size() > 1) {
                    vplexVolumeInfo.setLocality(VPlexApiConstants.DISTRIBUTED_VIRTUAL_VOLUME);
                } else {
                    vplexVolumeInfo.setLocality(VPlexApiConstants.LOCAL_VIRTUAL_VOLUME);
                }
                _log.info(String.format("Creating mirror: %s (%s)", vplexMirror.getLabel(), vplexMirrorId));
                Volume storageVolume = mirrorMap.get(vplexMirror);
                long totalProvisioned = storageVolume.getProvisionedCapacity();
                StorageSystem storage = storageMap.get(storageVolume.getStorageController());
                List<String> itls = VPlexControllerUtils.getVolumeITLs(storageVolume);
                VolumeInfo vinfo = new VolumeInfo(storage.getNativeGuid(), storage.getSystemType(),
                        storageVolume.getWWN().toUpperCase().replaceAll(":", ""),
                        storageVolume.getNativeId(), storageVolume.getThinlyProvisioned().booleanValue(), itls);

                // Update rollback information.
                rollbackData.add(vinfo);
                List<VolumeInfo> vinfos = new ArrayList<VolumeInfo>();
                vinfos.add(vinfo);
                _workflowService.storeStepData(stepId, rollbackData);

                // Make the call to create device and attach it as mirror to the source virtual volume device.
                VPlexDeviceInfo vInfo = client.createDeviceAndAttachAsMirror(vplexVolumeInfo, vinfos, true, false);
                buf.append(vInfo.getName() + " ");
                _log.info(String.format("Created mirror : %s path: %s : for virtual volume %s device label %s", vInfo.getName(),
                        vInfo.getPath(), sourceVplexVolume.getLabel(), sourceVplexVolume.getDeviceLabel()));
                vplexMirror.setNativeId(vInfo.getPath());
                vplexMirror.setDeviceLabel(vInfo.getName());
                // For Vplex virtual volumes set allocated capacity to 0 (cop-18608)
                vplexMirror.setAllocatedCapacity(0L);
                vplexMirror.setProvisionedCapacity(totalProvisioned);
                if (vplexVolumeInfo.isThinEnabled() != sourceVplexVolume.getThinlyProvisioned()) {
                    _log.info("Thin provisioned setting changed after mirror operation to " + vplexVolumeInfo.isThinEnabled());
                    sourceVplexVolume.setThinlyProvisioned(vplexVolumeInfo.isThinEnabled());
                    _dbClient.updateObject(sourceVplexVolume);
                }
                vplexMirror.setThinlyProvisioned(vplexVolumeInfo.isThinEnabled());
                _dbClient.updateObject(vplexMirror);

                // Record VPLEX volume created event.
                createdVplexMirrorURIs.add(vplexMirrorId);
                recordBourneVplexMirrorEvent(vplexMirrorId,
                        OperationTypeEnum.CREATE_VOLUME_MIRROR.getEvType(true),
                        Operation.Status.ready,
                        OperationTypeEnum.CREATE_VOLUME_MIRROR.getDescription());
            }

            completer.ready(_dbClient);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (VPlexApiException vae) {
            _log.error("Exception creating Mirror for the Virtual Volume: " + vae.getMessage(), vae);

            // Record VPLEX volume creation failed event for those volumes
            // not created.
            for (URI vplexMirrorURI : vplexMirrorURIs) {
                if (!createdVplexMirrorURIs.contains(vplexMirrorURI)) {
                    recordBourneVplexMirrorEvent(vplexMirrorURI,
                            OperationTypeEnum.CREATE_VOLUME_MIRROR.getEvType(false),
                            Operation.Status.error,
                            OperationTypeEnum.CREATE_VOLUME_MIRROR.getDescription());
                }
            }
            failStep(completer, stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception creating Mirror for the Virtual Volume: " + ex.getMessage(), ex);

            // Record VPLEX volume creation failed event for those volumes
            // not created.
            for (URI vplexMirrorURI : vplexMirrorURIs) {
                if (!createdVplexMirrorURIs.contains(vplexMirrorURI)) {
                    recordBourneVplexMirrorEvent(vplexMirrorURI,
                            OperationTypeEnum.CREATE_VOLUME_MIRROR.getEvType(false),
                            Operation.Status.error,
                            OperationTypeEnum.CREATE_VOLUME_MIRROR.getDescription());
                }
            }
            ServiceError serviceError = VPlexApiException.errors.createMirrorsFailed(ex);
            failStep(completer, stepId, serviceError);
        }
    }

    /**
     * Returns a Workflow.Method for deactivating Mirror
     *
     * @param vplexURI
     *            URI of the VPlex StorageSystem
     * @param vplexMirrorURI
     *            URI of the mirror to be deleted
     * @return A reference to the workflow method to delete mirror device
     */
    private Workflow.Method deleteMirrorDeviceMethod(URI vplexURI, URI vplexMirrorURI) {
        return new Workflow.Method(DELETE_MIRROR_DEVICE_METHOD_NAME, vplexURI, vplexMirrorURI);
    }

    /**
     * Do the delete of a VPlex mirror device .
     * This is called as a Workflow Step.
     * NOTE: The parameters here must match deleteMirrorDeviceMethod above (except stepId).
     *
     * @param vplexURI
     *            URI of the VPlex StorageSystem
     * @param vplexMirrorURI
     *            URI of the mirror to be deleted
     * @param stepId
     *            The stepId used for completion.
     *
     * @throws WorkflowException
     *             When an error occurs updating the workflow step
     *             state.
     */
    public void deleteMirrorDevice(URI vplexURI, URI vplexMirrorURI, String stepId) throws WorkflowException {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexURI, _dbClient);
            VplexMirror vplexMirror = getDataObject(VplexMirror.class, vplexMirrorURI, _dbClient);

            if (vplexMirror.getDeviceLabel() != null) {
                // Call to delete mirror device
                client.deleteLocalDevice(vplexMirror.getDeviceLabel());

                if (vplexMirror.getSource() != null && vplexMirror.getSource().getURI() != null) {
                    Volume vplexVolume = getDataObject(Volume.class, vplexMirror.getSource().getURI(), _dbClient);
                    StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
                    updateThinProperty(client, vplexSystem, vplexVolume);
                }

                // Record VPLEX mirror delete event.
                recordBourneVplexMirrorEvent(vplexMirrorURI,
                        OperationTypeEnum.DELETE_VOLUME_MIRROR.getEvType(true),
                        Operation.Status.ready,
                        OperationTypeEnum.DELETE_VOLUME_MIRROR.getDescription());
            } else {
                _log.info("It seems vplex mirror {} was never created, so just move to the next step.", vplexMirror.getLabel());
            }

            WorkflowStepCompleter.stepSucceded(stepId);

        } catch (VPlexApiException vae) {
            _log.error("Exception deleting VPlex Virtual Volume: " + vae.getMessage(), vae);
            recordBourneVplexMirrorEvent(vplexMirrorURI,
                    OperationTypeEnum.DELETE_VOLUME_MIRROR.getEvType(true),
                    Operation.Status.error,
                    OperationTypeEnum.DELETE_VOLUME_MIRROR.getDescription());
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception deleting VPlex Virtual Volume: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.DELETE_VPLEX_LOCAL_MIRROR.getName();
            ServiceError serviceError = VPlexApiException.errors.deleteMirrorFailed(opName, ex);
            recordBourneVplexMirrorEvent(vplexMirrorURI,
                    OperationTypeEnum.DELETE_VOLUME_MIRROR.getEvType(true),
                    Operation.Status.error,
                    OperationTypeEnum.DELETE_VOLUME_MIRROR.getDescription());
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    /**
     * Returns a Workflow.Method for deactivating Mirror
     *
     * @param vplexURI
     *            URI of the VPlex StorageSystem
     * @param vplexMirrorURI
     *            URI of the mirror to be deleted
     * @return A reference to the workflow method to delete mirror device
     */
    private Workflow.Method rollbackDeleteMirrorDeviceMethod(URI vplexURI, URI vplexMirrorURI) {
        return new Workflow.Method(RB_DELETE_MIRROR_DEVICE_METHOD_NAME, vplexURI, vplexMirrorURI);
    }

    /**
     * Here we will try to reattach mirror device. If we cannot reattach then the mirror
     * is already detached and mirror related objects will be removed from the database.
     *
     * @param vplexURI
     *            URI of the VPlex StorageSystem
     * @param mirrorURI
     *            URI of the mirror to be deleted
     * @param stepId
     *            The stepId used for completion.
     *
     * @throws WorkflowException
     *             When an error occurs updating the workflow step
     *             state.
     */
    public void rollbackDeleteMirrorDevice(URI vplexURI, URI mirrorURI, String stepId) throws WorkflowException {
        try {
            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexURI, _dbClient);

            VplexMirror vplexMirror = _dbClient.queryObject(VplexMirror.class, mirrorURI);
            // Get source volume for the mirror
            Volume sourceVplexVolume = getDataObject(Volume.class, vplexMirror.getSource().getURI(), _dbClient);
            String locality = null;
            if (sourceVplexVolume.getAssociatedVolumes() != null && sourceVplexVolume.getAssociatedVolumes().size() > 1) {
                locality = VPlexApiConstants.DISTRIBUTED_VIRTUAL_VOLUME;
            } else {
                locality = VPlexApiConstants.LOCAL_VIRTUAL_VOLUME;
            }

            // Try to re-attach the mirror device back to the source device
            client.attachMirror(locality, sourceVplexVolume.getDeviceLabel(), vplexMirror.getDeviceLabel());
            _log.info("Successfully re-attached mirror %s to the source volume %s during delete mirror rollback. ",
                    vplexMirror.getDeviceLabel(), sourceVplexVolume.getDeviceLabel());

        } catch (Exception e) {
            // If exception occurs that means mirror is already detached and we couldn't reattach
            // So cleanup database objects related to a mirror.
            VplexMirror vplexMirror = _dbClient.queryObject(VplexMirror.class, mirrorURI);
            Volume sourceVplexVolume = getDataObject(Volume.class, vplexMirror.getSource().getURI(), _dbClient);

            // Remove mirror from the source VPLEX volume
            sourceVplexVolume.getMirrors().remove(vplexMirror.getId().toString());
            _dbClient.updateObject(sourceVplexVolume);
            _log.info("Removed mirror %s from source volume %s", mirrorURI, sourceVplexVolume.getId());

            // Delete mirror and associated volume from database
            if (null != vplexMirror.getAssociatedVolumes()) {
                for (String assocVolumeId : vplexMirror.getAssociatedVolumes()) {
                    Volume volume = _dbClient.queryObject(Volume.class, URI.create(assocVolumeId));
                    if (null != volume) {
                        _dbClient.removeObject(volume);
                    }
                }
            }

            // Delete the mirror object
            _dbClient.removeObject(vplexMirror);

            _log.error("Error during rollback of promote mirror: {}", e.getMessage(), e);
        } finally {
            // Return success so rollback continues
            WorkflowStepCompleter.stepSucceded(stepId);
        }
    }

    /**
     * Returns a Workflow.Method for detaching Mirror
     *
     * @param vplexURI
     *            URI of the VPlex StorageSystem
     * @param vplexMirrorURI
     *            URI of the mirror to be detached
     * @param discard
     *            true or false value, whether to discard device or not.
     * @return A reference to the workflow method to detach mirror device
     */
    private Workflow.Method detachMirrorDeviceMethod(URI vplexURI, URI vplexMirrorURI, URI promotedVolumeURI, boolean discard) {
        return new Workflow.Method(DETACH_MIRROR_DEVICE_METHOD_NAME, vplexURI, vplexMirrorURI, promotedVolumeURI, discard);
    }

    /**
     * This method will detach mirror device from the vplex volume source device.
     * If discard is true it will leave the device and the underlying structure on VPlex.
     * True is used when this method is used in the context of deleting mirror.
     * If discarded is false it will convert the detached mirror into virtual volume with
     * the same name as the mirror device. False is used in the context of promoting mirror
     * to a vplex volume.
     *
     * @param vplexURI
     *            URI of the VPlex StorageSystem
     * @param vplexMirrorURI
     *            URI of the mirror to be detached.
     * @param discard
     *            true or false value, whether to discard device or not.
     * @param stepId
     *            The stepId used for completion.
     *
     * @throws WorkflowException
     *             When an error occurs updating the workflow step
     *             state.
     */
    public void detachMirrorDevice(URI vplexURI, URI vplexMirrorURI, URI promotedVolumeURI, boolean discard, String stepId)
            throws WorkflowException {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplexURI, _dbClient);
            VplexMirror vplexMirror = getDataObject(VplexMirror.class, vplexMirrorURI, _dbClient);
            Volume sourceVplexVolume = getDataObject(Volume.class, vplexMirror.getSource().getURI(), _dbClient);

            if (vplexMirror.getDeviceLabel() != null) {
                if (null == sourceVplexVolume.getAssociatedVolumes() || sourceVplexVolume.getAssociatedVolumes().isEmpty()) {
                    _log.error("VPLEX volume {} has no backend volumes.", sourceVplexVolume.forDisplay());
                    throw InternalServerErrorException.internalServerErrors
                            .noAssociatedVolumesForVPLEXVolume(sourceVplexVolume.forDisplay());
                }
                if (sourceVplexVolume.getAssociatedVolumes().size() > 1) {
                    // Call to detach mirror device from Distributed Virtual Volume
                    client.detachLocalMirrorFromDistributedVirtualVolume(sourceVplexVolume.getDeviceLabel(), vplexMirror.getDeviceLabel(),
                            discard);
                } else {
                    // Call to detach mirror device from Local Virtual Volume
                    client.detachMirrorFromLocalVirtualVolume(sourceVplexVolume.getDeviceLabel(), vplexMirror.getDeviceLabel(), discard);
                }

                // Record VPLEX mirror detach event.
                recordBourneVplexMirrorEvent(vplexMirrorURI,
                        OperationTypeEnum.DETACH_VOLUME_MIRROR.getEvType(true),
                        Operation.Status.ready,
                        OperationTypeEnum.DETACH_VOLUME_MIRROR.getDescription());
            } else {
                _log.info("It seems vplex mirror {} was never created, so move to the next step for cleanup.", vplexMirror.getLabel());
            }
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (VPlexApiException vae) {
            _log.error("Exception detaching Vplex Mirror {} ", vplexMirrorURI + vae.getMessage(), vae);
            if (promotedVolumeURI != null) {
                // If we are here due to promote mirror action then
                // delete the volume that was supposed to be promoted volume.
                Volume volume = _dbClient.queryObject(Volume.class, promotedVolumeURI);
                _dbClient.removeObject(volume);
            }
            recordBourneVplexMirrorEvent(vplexMirrorURI,
                    OperationTypeEnum.DETACH_VOLUME_MIRROR.getEvType(true),
                    Operation.Status.error,
                    OperationTypeEnum.DETACH_VOLUME_MIRROR.getDescription());
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception detaching Vplex Mirror {} ", vplexMirrorURI + ex.getMessage(), ex);
            if (promotedVolumeURI != null) {
                // If we are here due to promote mirror action then
                // delete the volume that was supposed to be promoted volume.
                Volume volume = _dbClient.queryObject(Volume.class, promotedVolumeURI);
                _dbClient.removeObject(volume);
            }
            String opName = ResourceOperationTypeEnum.DETACH_VPLEX_LOCAL_MIRROR.getName();
            ServiceError serviceError = VPlexApiException.errors.detachMirrorFailed(opName, ex);
            recordBourneVplexMirrorEvent(vplexMirrorURI,
                    OperationTypeEnum.DETACH_VOLUME_MIRROR.getEvType(true),
                    Operation.Status.error,
                    OperationTypeEnum.DETACH_VOLUME_MIRROR.getDescription());
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    private Workflow.Method rollbackCreateMirrorsMethod(
            URI vplexURI, List<URI> vplexMirrorURIs, String executeStepId) {
        return new Workflow.Method(RB_CREATE_MIRRORS_METHOD_NAME, vplexURI, vplexMirrorURIs, executeStepId);
    }

    /**
     * Rollback any mirror device previously created.
     *
     * @param vplexURI
     *            URI of the VPlex StorageSystem
     * @param vplexMirrorURIs
     *            URI of the mirrors
     * @param executeStepId
     *            step Id of the execute step; used to retrieve rollback data.
     * @param stepId
     *            The stepId used for completion.
     *
     * @throws WorkflowException
     *             When an error occurs updating the workflow step
     *             state.
     */
    public void rollbackCreateMirrors(URI vplexURI, List<URI> vplexMirrorURIs, String executeStepId, String stepId)
            throws WorkflowException {
        try {
            List<VolumeInfo> rollbackData = (List<VolumeInfo>) _workflowService.loadStepData(executeStepId);
            if (rollbackData != null) {
                WorkflowStepCompleter.stepExecuting(stepId);
                // Get the API client.
                StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
                VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);

                // For each mirror device attempted, try and rollback.
                for (VolumeInfo rollbackInfo : rollbackData) {
                    client.deleteLocalDevice(rollbackInfo);
                }
            }
        } catch (Exception ex) {
            _log.error("Exception rolling back: " + ex.getLocalizedMessage());
        } finally {
            // Even if we have exception cleaning up on Vplex
            // cleanup vplex mirror object in Database
            for (URI uri : vplexMirrorURIs) {
                VplexMirror vplexMirror = _dbClient.queryObject(VplexMirror.class, uri);
                if (vplexMirror != null) {
                    Volume sourceVplexVolume = _dbClient.queryObject(Volume.class, vplexMirror.getSource());
                    sourceVplexVolume.getMirrors().remove(vplexMirror.getId().toString());
                    _dbClient.updateObject(sourceVplexVolume);
                    _dbClient.removeObject(vplexMirror);
                }
            }
            WorkflowStepCompleter.stepSucceded(stepId);
        }
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

    /**
     * For a given ExportGroup and ExportMask, this method filters out the ExportGroup's volumes
     * that are present in other ExportGroups referencing this ExportMask,
     * or contained in the ExportMask's ExistingVolumes collection. Basically it
     * results in a list of volumes that are in the ExportGroup, but should
     * no longer be in the ExportMask.
     *
     * @param exportGroup
     *            the ExportGroup for the source volumes
     * @param exportMask
     *            the ExportMask
     * @param otherExportGroups
     *            a list of other ExportGroups referencing the ExportMask
     * @param volumeURIList
     *            a list of volume URIs that needs to be filter
     * @return a list of volume URIs
     */
    private List<URI> getVolumeListDiff(ExportGroup exportGroup, ExportMask exportMask, List<ExportGroup> otherExportGroups,
            List<URI> volumeURIs) {

        List<URI> volumeURIList = new ArrayList<URI>();
        if (volumeURIs == null) {
            _log.warn("volumeURIs is null, returning empty list");
            return volumeURIList;
        } else {
            volumeURIList.addAll(volumeURIs);
        }
        _log.info("filtering volume list from ExportGroup " + exportGroup.getLabel()
                + " ExportMask " + exportMask.getMaskName());

        if (volumeURIList != null && !volumeURIList.isEmpty()) {

            _log.info("volume list is " + volumeURIList);
            for (ExportGroup otherGroup : otherExportGroups) {
                _log.info("looking at other export group: " + otherGroup.getLabel());
                // Here we partition the other ExportGroup's volume into those it would export in
                // each Varray. Then we remove the volumes in the other group's set for the
                // Varray matching the Varray of our ExportMask.
                // This is done because the other ExportGroup might have volumes it isn't allowed to
                // export to this Varray because autoCrossConnectExport == false for those volumes.
                if (null != otherGroup.getVolumes()) {
                    List<URI> otherGroupVolumes = StringSetUtil.stringSetToUriList(otherGroup.getVolumes().keySet());
                    Map<URI, Set<URI>> varrayToVolumesMap = VPlexUtil.mapBlockObjectsToVarrays(_dbClient,
                            otherGroupVolumes, exportMask.getStorageDevice(), otherGroup);
                    for (URI varray : varrayToVolumesMap.keySet()) {
                        if (ExportMaskUtils.exportMaskInVarray(_dbClient, exportMask, varray)) {
                            _log.info("volume list from other group is " + varrayToVolumesMap.get(varray).toString());
                            volumeURIList.removeAll(varrayToVolumesMap.get(varray));
                        }
                    }
                }
                // if (otherGroup.getVolumes() != null) {
                //
                // List<URI> otherVolumeUris = new ArrayList<URI>();
                // for (String volUri : otherGroup.getVolumes().keySet()) {
                // otherVolumeUris.add(URI.create(volUri));
                // }
                //
                // _log.info("volume list from other group is " + volumeURIList);
                // volumeURIList.removeAll(otherVolumeUris);
                // }
            }
            _log.info("volume list after filter is " + volumeURIList);
        }

        _log.info("volume list before removing existing volumes is " + volumeURIList);
        if (exportMask.getExistingVolumes() != null) {
            for (String existingVolWwn : exportMask.getExistingVolumes().keySet()) {
                // Remove any volumes that match the WWN
                URIQueryResultList results = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getVolumeWwnConstraint(existingVolWwn.toUpperCase()), results);
                if (results != null) {
                    Iterator<URI> resultsIter = results.iterator();
                    if (resultsIter.hasNext()) {
                        volumeURIList.remove(resultsIter.next());
                    }
                }
                // Remove any block snapshots that match the WWN
                // This happens on RP bookmarks.
                results = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getBlockSnapshotWwnConstraint(existingVolWwn.toUpperCase()), results);
                if (results != null) {
                    Iterator<URI> resultsIter = results.iterator();
                    if (resultsIter.hasNext()) {
                        volumeURIList.remove(resultsIter.next());
                    }
                }
            }
        }

        List<URI> volumesNotInMask = new ArrayList<URI>();
        for (URI volumeURI : volumeURIList) {
            if (!exportMask.getVolumes().keySet().contains(volumeURI.toString())) {
                volumesNotInMask.add(volumeURI);
            }
        }
        _log.info("volumes removed because not in mask: " + volumesNotInMask);
        volumeURIList.removeAll(volumesNotInMask);

        _log.info("final volume list is " + volumeURIList);

        return volumeURIList;
    }

   

    /**
     * Validate a VPLEX Storage Provider connection.
     *
     * @param ipAddress
     *            the Storage Provider's IP address
     * @param portNumber
     *            the Storage Provider's IP port
     *
     * @return true if the Storage Provider connection is valid
     */
    @Override
    public boolean validateStorageProviderConnection(String ipAddress, Integer portNumber) {
        boolean connectionValid;

        try {
            // look up the provider by ip address and port
            StringBuffer providerId = new StringBuffer(ipAddress).append(HYPHEN_OPERATOR).append(portNumber);
            URIQueryResultList providerUriList = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getStorageProviderByProviderIDConstraint(providerId.toString()),
                    providerUriList);

            if (providerUriList.iterator().hasNext()) {
                StorageProvider provider = _dbClient.queryObject(StorageProvider.class,
                        providerUriList.iterator().next());

                VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, provider, _dbClient);
                if (client != null) {
                    client.verifyConnectivity();
                    // if we got this far without blowing up, then the connection is valid
                    _log.info("VPLEX Storage Provider connection at {} is valid.", providerId);
                    connectionValid = true;
                } else {
                    _log.error("a VplexApiClient could not be created for provider {}.", provider.getLabel());
                    connectionValid = false;
                }
            } else {
                _log.error("Could not find a VPLEX Storage Provider "
                        + "with address-port {} in ViPR.", providerId);
                connectionValid = false;
            }
        } catch (Exception ex) {
            _log.error("Connection to VPLEX Storage Provider {} is invalid.", ipAddress, ex);
            connectionValid = false;
        }

        return connectionValid;
    }

    @Override
    public String addStepsForChangeVirtualPool(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId) throws InternalException {
        try {
            // Get all the Virtual Volumes.
            List<VolumeDescriptor> vplexVirtualVolumes = VolumeDescriptor.filterByType(
                    volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_VIRT_VOLUME },
                    new VolumeDescriptor.Type[] {});

            if (vplexVirtualVolumes == null || vplexVirtualVolumes.isEmpty()) {
                return waitFor;
            }

            // Find the original change vpool virtual volumes from the descriptors.
            URI newVpoolURI = null;
            List<URI> changeVpoolVirtualVolumeURIs = new ArrayList<URI>();
            for (VolumeDescriptor vplexVirtualVolume : vplexVirtualVolumes) {
                if (vplexVirtualVolume.getParameters() != null
                        && !vplexVirtualVolume.getParameters().isEmpty()) {
                    // Let's check to see if the PARAM_VPOOL_CHANGE_EXISTING_VOLUME_ID was populated
                    // in the descriptor params map. This would indicate that the descriptor
                    // has information about the existing volume for the change vpool operation.
                    Object existingVolumeId = vplexVirtualVolume.getParameters().get(
                            VolumeDescriptor.PARAM_VPOOL_CHANGE_EXISTING_VOLUME_ID);
                    if (existingVolumeId != null) {
                        URI virtualVolumeURI = (URI) existingVolumeId;
                        _log.info(String.format("Adding steps for change vpool for vplex volume %s", virtualVolumeURI.toString()));

                        if (newVpoolURI == null) {
                            newVpoolURI = (URI) vplexVirtualVolume.getParameters().get(
                                    VolumeDescriptor.PARAM_VPOOL_CHANGE_NEW_VPOOL_ID);
                        }

                        changeVpoolVirtualVolumeURIs.add(virtualVolumeURI);
                    }
                }
            }

            // Check to see if this is an RP+VPLEX change vpool request
            List<VolumeDescriptor> rpExistingSourceVolumes = VolumeDescriptor.filterByType(volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_EXISTING_SOURCE },
                    new VolumeDescriptor.Type[] {});

            // Check to see if this is an RP+VPLEX change vpool request
            List<VolumeDescriptor> rpExistingProtectedSourceVolumes = VolumeDescriptor.filterByType(volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_EXISTING_PROTECTED_SOURCE },
                    new VolumeDescriptor.Type[] {});

            boolean rpAddProtectionVPoolChange = (rpExistingSourceVolumes != null && !rpExistingSourceVolumes.isEmpty());
            boolean rpUpgradeProtectionVPoolChange = (rpExistingProtectedSourceVolumes != null && !rpExistingProtectedSourceVolumes
                    .isEmpty());

            // We may need to create new virtual volumes if this is a
            // RP+VPLEX change vpool request
            if (rpAddProtectionVPoolChange || rpUpgradeProtectionVPoolChange) {
                // First let's make a copy of the all the volume descriptors passed in
                List<VolumeDescriptor> copyOfVolumeDescriptors = new ArrayList<VolumeDescriptor>();
                copyOfVolumeDescriptors.addAll(volumes);

                if (rpAddProtectionVPoolChange) {
                    _log.info("Adding VPLEX steps for RP+VPLEX/MetroPoint add protection vpool change...");
                    Iterator<VolumeDescriptor> it = copyOfVolumeDescriptors.iterator();
                    while (it.hasNext()) {
                        VolumeDescriptor currentVolumeDesc = it.next();
                        if (changeVpoolVirtualVolumeURIs.contains(currentVolumeDesc.getVolumeURI())) {
                            // Remove the RP+VPLEX Source Change Vpool Virtual Volume(s) from the copy of the
                            // descriptors as they do not need to be created (because they already exist!)
                            it.remove();
                            break;
                        }
                    }

                    // Lastly add the the RP+VPLEX Source Change Vpool Virtual Volume to the CG (which will create the
                    // CG if it's not
                    // already)
                    for (URI virtualVolumeURI : changeVpoolVirtualVolumeURIs) {
                        Volume changeVpoolVolume = getDataObject(Volume.class, virtualVolumeURI, _dbClient);

                        changeVpoolVolume.getConsistencyGroup();

                        // This is a good time to update the vpool on the existing Virtual Volume to the new vpool
                        changeVpoolVolume.setVirtualPool(newVpoolURI);
                        _dbClient.updateObject(changeVpoolVolume);

                        StorageSystem vplex = getDataObject(StorageSystem.class,
                                changeVpoolVolume.getStorageController(), _dbClient);

                        // Get a handle on the RP consistency group manager
                        ConsistencyGroupManager consistencyGroupManager = getConsistencyGroupManager(DiscoveredDataObject.Type.rp.name());

                        // Add step for create CG
                        waitFor = consistencyGroupManager.addStepsForCreateConsistencyGroup(
                                workflow, waitFor, vplex, Arrays.asList(virtualVolumeURI), false);
                        _log.info("Added steps for CG creation for vplex volume {}", virtualVolumeURI);
                    }
                } else {
                    _log.info("Adding VPLEX steps for RP+VPLEX/MetroPoint upgrade protection vpool change...");
                }

                // Let's now create the virtual volumes for the RP+VPLEX:
                // Source Journal, Target(s), and Target Journal.
                waitFor = addStepsForCreateVolumes(workflow, waitFor, copyOfVolumeDescriptors, taskId);
            }

            // Create steps to migrate the backend volumes.
            String lastStep = waitFor;
            URI cgURI = null;
            // With application support, one VPLEX CG could have multiple replication groups from the same local system.
            // The localSystemToRemoveCG map key is storagesystemUri, value is the list of replication group names to be removed.
            Map<URI, Set<String>> localSystemsToRemoveCG = new HashMap<URI, Set<String>>();
            List<VolumeDescriptor> vplexMigrateVolumes = VolumeDescriptor.filterByType(
                    volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_MIGRATE_VOLUME },
                    new VolumeDescriptor.Type[] {});
            if (vplexMigrateVolumes != null && !vplexMigrateVolumes.isEmpty()) {
                for (URI virtualVolumeURI : changeVpoolVirtualVolumeURIs) {
                    _log.info("Adding migration steps for vplex volume {}", virtualVolumeURI);

                    // A list of the volumes satisfying the new VirtualPool to
                    // which the data on the current backend volumes
                    // will be migrated.
                    List<URI> newVolumes = new ArrayList<URI>();

                    // A Map containing a migration for each new backend
                    // volume
                    Map<URI, URI> migrationMap = new HashMap<URI, URI>();

                    // A map that specifies the storage pool in which
                    // each new volume should be created.
                    Map<URI, URI> poolVolumeMap = new HashMap<URI, URI>();

                    // The URI of the vplex system
                    URI vplexURI = null;

                    for (VolumeDescriptor desc : vplexMigrateVolumes) {
                        // Skip migration targets that are not for the VPLEX
                        // volume being processed.
                        Migration migration = getDataObject(Migration.class, desc.getMigrationId(), _dbClient);
                        if (!migration.getVolume().equals(virtualVolumeURI)) {
                            continue;
                        }

                        // We need the VPLEX system and consistency group,
                        // which should be the same for all volumes being
                        // migrated when multiple volumes are passed.
                        if (vplexURI == null) {
                            Volume virtualVolume = getDataObject(Volume.class, virtualVolumeURI, _dbClient);
                            vplexURI = virtualVolume.getStorageController();
                            cgURI = virtualVolume.getConsistencyGroup();
                        }

                        // Set data required to add the migration steps.
                        newVolumes.add(desc.getVolumeURI());
                        migrationMap.put(desc.getVolumeURI(), desc.getMigrationId());
                        poolVolumeMap.put(desc.getVolumeURI(), desc.getPoolURI());

                        // If the migration is to a different storage system
                        // we may need to remove the backend CG on the source
                        // system after the migration completes. Note that the
                        // migration source is null for an ingested volume
                        // being migrated to known storage.
                        Volume migSrc = null;
                        URI migSrcURI = migration.getSource();
                        if (!NullColumnValueGetter.isNullURI(migSrcURI)) {
                            migSrc = getDataObject(Volume.class, migSrcURI, _dbClient);
                        }
                        URI migTgtURI = migration.getTarget();
                        Volume migTgt = getDataObject(Volume.class, migTgtURI, _dbClient);
                        if ((migSrc != null) && (!migTgt.getStorageController().equals(migSrc.getStorageController()))) {
                            // If we have a volume to migrate and the RG field is NOT set on the volume,
                            // do not remove the RG on the local system.
                            //
                            // Volumes that are in RGs that are being migrated are grouped together so otherwise
                            // we're good as the replication instance will be set on those volumes.
                            String rgName = migSrc.getReplicationGroupInstance();
                            if (NullColumnValueGetter.isNotNullValue(rgName)) {
                                URI storageUri = migSrc.getStorageController();
                                Set<String> rgNames = localSystemsToRemoveCG.get(storageUri);
                                if (rgNames == null) {
                                    rgNames = new HashSet<String>();
                                    localSystemsToRemoveCG.put(storageUri, rgNames);
                                }
                                rgNames.add(rgName);
                                _log.info("Will remove CG {} on local system {}", rgName, storageUri);
                            } else {
                                _log.info("Will not remove CG on local system {}", migSrc.getStorageController());
                            }
                        }
                    }

                    // Note that the last step here is a step group associated
                    // with deleting the migration sources after the migrations
                    // have completed and committed. This means that anything
                    // that waits on this, will occur after the migrations have
                    // completed, been committed, and the migration sources deleted.
                    lastStep = addStepsForMigrateVolumes(workflow, vplexURI,
                            virtualVolumeURI, newVolumes, migrationMap, poolVolumeMap,
                            newVpoolURI, null,
                            VolumeDescriptor.getMigrationSuspendBeforeCommit(volumes),
                            VolumeDescriptor.getMigrationSuspendBeforeDeleteSource(volumes),
                            taskId, waitFor);
                    _log.info("Add migration steps for vplex volume {}", virtualVolumeURI);
                }

                // Add step to delete backend CG if necessary. Note that these
                // are done sequentially, else you can have issues updating the
                // systemConsistencyGroup specified for the group.
                if (!NullColumnValueGetter.isNullURI(cgURI)) {
                    _log.info("Vpool change volumes are in CG {}", cgURI);
                    BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, _dbClient);
                    if (cg.checkForType(Types.LOCAL)) {
                        _log.info("CG {} has local type", cgURI);
                        // If any of the VPLEX volumes involved in the vpool change
                        // is in a VPLEX CG with corresponding local CGs for the backend
                        // volumes, then it is required that all VPLEX volumes in the
                        // CG are part of the vpool change. If the backend volumes are being
                        // migrated to a new storage system, then we need to add a step
                        // to delete the local CG.
                        boolean localCGDeleted = false;
                        for (Map.Entry<URI, Set<String>> entry : localSystemsToRemoveCG.entrySet()) {
                            localCGDeleted = true;
                            URI localSystemURI = entry.getKey();
                            StorageSystem localSystem = getDataObject(StorageSystem.class, localSystemURI, _dbClient);
                            Set<String> rgNames = entry.getValue();
                            for (String rgName : rgNames) {
                                _log.info("Adding step to remove CG {} on local system {}", rgName, localSystemURI);
                                Workflow.Method deleteCGMethod = new Workflow.Method(
                                        "deleteReplicationGroupInConsistencyGroup", localSystemURI, cgURI, rgName, false,
                                        false, true);
                                workflow.createStep("deleteLocalCG", String.format(
                                        "Delete consistency group from storage system: %s", localSystemURI),
                                        lastStep, localSystemURI, localSystem.getSystemType(),
                                        BlockDeviceController.class, deleteCGMethod, null,
                                        null);
                            }
                        }
                        if (localCGDeleted) {
                            lastStep = "deleteLocalCG";
                        }
                    }
                }
            }

            // Return the last step
            return lastStep;
        } catch (Exception ex) {
            throw VPlexApiException.exceptions.addStepsForChangeVirtualPoolFailed(ex);
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

    @Override
    public String addStepsForChangeVirtualArray(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId) throws InternalException {
        try {
            URI cgURI = null;
            URI currentVarrayURI = null;
            URI tgtVarrayURI = null;
            URI vplexURI = null;
            StorageSystem vplexSystem = null;
            List<URI> vplexVolumeURIs = new ArrayList<URI>();
            VPlexConsistencyGroupManager cgMgr = null;

            // Get all the Virtual Volumes and the new varray URI.
            List<VolumeDescriptor> vplexVolumeDescriptors = VolumeDescriptor.filterByType(
                    volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_VIRT_VOLUME },
                    new VolumeDescriptor.Type[] {});
            for (VolumeDescriptor vplexVolumeDescriptor : vplexVolumeDescriptors) {
                // Add the URI for the VPLEX volume.
                URI vplexVolumeURI = vplexVolumeDescriptor.getVolumeURI();
                _log.info("Add steps to change virtual array for volume {}", vplexVolumeURI);
                vplexVolumeURIs.add(vplexVolumeURI);

                // Set the target virtual array if not already set.
                if (tgtVarrayURI == null) {
                    if ((vplexVolumeDescriptor.getParameters() != null) &&
                            (!vplexVolumeDescriptor.getParameters().isEmpty())) {
                        tgtVarrayURI = (URI) vplexVolumeDescriptor.getParameters().get(
                                VolumeDescriptor.PARAM_VARRAY_CHANGE_NEW_VAARAY_ID);
                        _log.info("Target virtual array for varray change is {}", tgtVarrayURI);
                    }
                }

                // We need the VPLEX system and consistency group,
                // which should be the same for all volumes being
                // migrated when multiple volumes are passed.
                if (vplexURI == null) {
                    Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, _dbClient);
                    vplexURI = vplexVolume.getStorageController();
                    vplexSystem = getDataObject(StorageSystem.class, vplexURI, _dbClient);
                    currentVarrayURI = vplexVolume.getVirtualArray();
                    cgURI = vplexVolume.getConsistencyGroup();
                    cgMgr = (VPlexConsistencyGroupManager) getConsistencyGroupManager(vplexVolume);
                }
            }

            // We need to determine if the varray change will migrate the
            // volumes to the other cluster of the VPLEX. If so and the
            // volumes are in a consistency group, then the volumes must
            // first be removed from the consistency group.
            String lastStep = waitFor;
            boolean volumesRemovedFromCG = false;
            if (!NullColumnValueGetter.isNullURI(cgURI)) {
                _log.info("Varray change volumes are in CG {}", cgURI);
                String currentClusterId = ConnectivityUtil.getVplexClusterForVarray(
                        currentVarrayURI, vplexURI, _dbClient);
                String newClusterId = ConnectivityUtil.getVplexClusterForVarray(
                        tgtVarrayURI, vplexURI, _dbClient);
                if (!newClusterId.equals(currentClusterId)) {
                    _log.info("Varray change migrates volumes to cluster {}", newClusterId);
                    // The volumes are in a consistency group and the
                    // volumes will change cluster, so the volumes
                    // must be removed from the CG first.
                    lastStep = cgMgr.addStepForRemoveVolumesFromCG(workflow, waitFor, vplexSystem,
                            vplexVolumeURIs, cgURI);
                    volumesRemovedFromCG = true;
                }
            }

            // Create steps to migrate the backend volumes.
            String migrateStep = null;
            List<URI> localSystemsToRemoveCG = new ArrayList<URI>();
            List<VolumeDescriptor> vplexMigrateVolumes = VolumeDescriptor.filterByType(
                    volumes, new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_MIGRATE_VOLUME },
                    new VolumeDescriptor.Type[] {});
            for (URI vplexVolumeURI : vplexVolumeURIs) {
                _log.info("Adding migration steps for vplex volume {}", vplexVolumeURI);

                // A list of the volumes to which the data on the current
                // backend volumes will be migrated.
                List<URI> newVolumes = new ArrayList<URI>();

                // A Map containing a migration for each new backend
                // volume
                Map<URI, URI> migrationMap = new HashMap<URI, URI>();

                // A map that specifies the storage pool in which
                // each new volume should be created.
                Map<URI, URI> poolVolumeMap = new HashMap<URI, URI>();

                for (VolumeDescriptor desc : vplexMigrateVolumes) {
                    // Skip migration targets that are not for the VPLEX
                    // volume being processed.
                    Migration migration = getDataObject(Migration.class, desc.getMigrationId(), _dbClient);
                    if (!migration.getVolume().equals(vplexVolumeURI)) {
                        continue;
                    }

                    _log.info("Found migration {} for VPLEX volume", migration.getId());

                    // Set data required to add the migration steps.
                    newVolumes.add(desc.getVolumeURI());
                    migrationMap.put(desc.getVolumeURI(), desc.getMigrationId());
                    poolVolumeMap.put(desc.getVolumeURI(), desc.getPoolURI());

                    // If the migration is to a different storage system
                    // we may need to remove the backend CG on the source
                    // system after the migration completes.
                    URI migSrcURI = migration.getSource();
                    Volume migSrc = getDataObject(Volume.class, migSrcURI, _dbClient);
                    URI migTgtURI = migration.getTarget();
                    Volume migTgt = getDataObject(Volume.class, migTgtURI, _dbClient);
                    if ((!migTgt.getStorageController().equals(migSrc.getStorageController())) &&
                            (!localSystemsToRemoveCG.contains(migSrc.getStorageController()))) {
                        _log.info("Will remove CG on local system {} if volume is in a CG.", migSrc.getStorageController());
                        localSystemsToRemoveCG.add(migSrc.getStorageController());
                    }
                }

                // Note that the migrate step here is a step group associated
                // with deleting the migration sources after the migrations
                // have completed and committed. This means that anything
                // that waits on this, will occur after the migrations have
                // completed, been committed, and the migration sources deleted.
                migrateStep = addStepsForMigrateVolumes(workflow, vplexURI,
                        vplexVolumeURI, newVolumes, migrationMap, poolVolumeMap,
                        null, tgtVarrayURI, false, false, taskId, lastStep);
                _log.info("Added migration steps for vplex volume {}", vplexVolumeURI);
            }

            // Update last step
            if (migrateStep != null) {
                lastStep = migrateStep;
            }

            // If the volumes are in a CG, we add the final CG steps.
            if (!NullColumnValueGetter.isNullURI(cgURI)) {
                BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, _dbClient);
                if (volumesRemovedFromCG) {
                    // If the volumes were removed from the consistency group, the
                    // varray change was across clusters. First add a step to delete
                    // the CG on the old cluster and then add steps to create the
                    // CG on the other cluster and add the volumes. The remove step
                    // must be executed first otherwise, when we go to create the CG
                    // on the other cluster, the create CG code will see that the CG
                    // already exists on the VPLEX system and it will not create it.
                    String removeStepId = workflow.createStepId();
                    StringSet systemCGs = cg.getSystemConsistencyGroups().get(vplexURI.toString());
                    String clusterCGName = systemCGs.iterator().next();
                    String clusterName = BlockConsistencyGroupUtils.fetchClusterName(clusterCGName);
                    String cgName = BlockConsistencyGroupUtils.fetchCgName(clusterCGName);
                    cgMgr.addStepForRemoveVPlexCG(workflow, removeStepId, lastStep,
                            vplexSystem, cgURI, cgName, clusterName, Boolean.FALSE, null);
                    lastStep = cgMgr.addStepsForCreateConsistencyGroup(workflow, removeStepId,
                            vplexSystem, vplexVolumeURIs, true);
                    _log.info("Added steps to remove the CG from the source cluster and add to target cluster.");
                }

                if (cg.checkForType(Types.LOCAL)) {
                    _log.info("CG {} has local type", cgURI);
                    // If the backend volumes are being migrated to a new storage system,
                    // then we need to add a step to delete the local CG.
                    boolean localCGDeleted = false;
                    List<URI> localSystemURIs = BlockConsistencyGroupUtils.getLocalSystems(cg, _dbClient);
                    for (URI localSystemURI : localSystemURIs) {
                        _log.info("CG exists on local system {}", localSystemURI);
                        if (localSystemsToRemoveCG.contains(localSystemURI)) {
                            localCGDeleted = true;
                            _log.info("Adding step to remove CG on local system{}", localSystemURI);
                            StorageSystem localSystem = getDataObject(StorageSystem.class, localSystemURI, _dbClient);
                            Workflow.Method deleteCGMethod = new Workflow.Method(
                                    "deleteConsistencyGroup", localSystemURI, cgURI, Boolean.FALSE);
                            workflow.createStep("deleteLocalCG", String.format(
                                    "Delete consistency group from storage system: %s", localSystemURI),
                                    lastStep, localSystemURI, localSystem.getSystemType(),
                                    BlockDeviceController.class, deleteCGMethod, null,
                                    null);
                        }
                    }
                    if (localCGDeleted) {
                        lastStep = "deleteLocalCG";
                    }
                }
            }

            // Return the last step
            return lastStep;
        } catch (Exception ex) {
            throw VPlexApiException.exceptions.addStepsForChangeVirtualPoolFailed(ex);
        }
    }

    @Override
    public void establishVolumeAndFullCopyGroupRelation(URI storage, URI sourceVolume, URI fullCopy, String opId)
            throws InternalException {
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    VOLUME_FULLCOPY_GROUP_RELATION_WF, false, opId);
            _log.info("Created establish volume  and full copy group relation workflow with operation id {}", opId);
            // Get the VPLEX and backend full copy volumes.
            Volume fullCopyVolume = getDataObject(Volume.class, fullCopy, _dbClient);
            Volume nativeFullCopyVolume = VPlexUtil.getVPLEXBackendVolume(fullCopyVolume, true, _dbClient);
            URI nativeSourceVolumeURI = nativeFullCopyVolume.getAssociatedSourceVolume();
            URI nativeSystemURI = nativeFullCopyVolume.getStorageController();
            StorageSystem nativeSystem = getDataObject(StorageSystem.class, nativeSystemURI, _dbClient);

            Workflow.Method establishRelationMethod = new Workflow.Method(VOLUME_FULLCOPY_RELATION_METHOD,
                    nativeSystemURI, nativeSourceVolumeURI, nativeFullCopyVolume.getId());
            workflow.createStep(VOLUME_FULLCOPY_GROUP_RELATION_STEP,
                    "create group relation between Volume group and Full copy group", null,
                    nativeSystemURI, nativeSystem.getSystemType(), BlockDeviceController.class,
                    establishRelationMethod, rollbackMethodNullMethod(), null);
            TaskCompleter completer = new CloneTaskCompleter(fullCopy, opId);
            String successMsg = String.format(
                    "Establish volume and full copy %s group relation completed successfully", fullCopy);
            FullCopyOperationCompleteCallback wfCompleteCB = new FullCopyOperationCompleteCallback();
            workflow.executePlan(completer, successMsg, wfCompleteCB,
                    new Object[] { Arrays.asList(fullCopy) }, null, null);
            _log.info("Workflow plan executing");
        } catch (Exception e) {
            String failMsg = String.format(
                    "Establish volume and full copy %s group relation failed", fullCopy);
            _log.error(failMsg, e);
            TaskCompleter completer = new CloneTaskCompleter(fullCopy, opId);
            ServiceCoded sc = VPlexApiException.exceptions.establishVolumeFullCopyGroupRelationFailed(
                    fullCopy.toString(), e);
            failStep(completer, opId, sc);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resyncSnapshot(URI vplexURI, URI snapshotURI, String opId) throws InternalException {
        // The snapshot target volume could be the source side backend volume for
        // a VPLEX volume if a VPLEX volume was created on the snapshot target volume
        // for the purpose of exporting the snapshot through the VPLEX rather directly
        // through the backend storage system. If this is the case, and that snapshot
        // is resynchronized, then we need do some additional steps because the data
        // on the VPLEX backend volume will have changed, and the VPLEX volume needs
        // to know about that.
        BlockSnapshot snapshot = getDataObject(BlockSnapshot.class, snapshotURI, _dbClient);
        try {
            // Create a new the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    RESYNC_SNAPSHOT_WF_NAME, false, opId);
            _log.info("Created resync snapshot workflow with operation id {}", opId);

            // Get all snapshots that will be resync'd.
            List<BlockSnapshot> snapshotsToResync = new ArrayList<BlockSnapshot>();
            URI cgURI = snapshot.getConsistencyGroup();
            if (!NullColumnValueGetter.isNullURI(cgURI)) {
                snapshotsToResync = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshot, _dbClient);
            } else {
                snapshotsToResync.add(snapshot);
            }

            // Get a list of the VPLEX volumes, if any, that are built
            // using the snapshot target volume.
            List<Volume> vplexVolumes = VPlexUtil.getVPlexVolumesBuiltOnSnapshots(snapshotsToResync, _dbClient);

            // Create the workflow steps.
            if (vplexVolumes.isEmpty()) {
                // If there are no VPLEX volumes built on the snapshots to be resynchronized,
                // then we just need a single step to invoke the block device controller to
                // resync the snapshots.
                createWorkflowStepForResyncNativeSnapshot(workflow, snapshot, null, null);
            } else {
                // Maps Vplex volume that needs to be flushed to underlying array volume
                Map<Volume, Volume> vplexToArrayVolumesToFlush = new HashMap<Volume, Volume>();
                for (Volume vplexVolume : vplexVolumes) {
                    Volume arrayVolumeToBeResynced = VPlexUtil.getVPLEXBackendVolume(
                            vplexVolume, true, _dbClient);
                    vplexToArrayVolumesToFlush.put(vplexVolume, arrayVolumeToBeResynced);
                }
                Map<URI, String> vplexVolumeIdToDetachStep = new HashMap<URI, String>();

                String waitFor = null;

                // Generate pre restore steps
                waitFor = addPreRestoreResyncSteps(workflow, vplexToArrayVolumesToFlush, vplexVolumeIdToDetachStep, waitFor);

                // Now create a workflow step to natively resync the snapshot.
                // Note that if the snapshot is associated with a CG, then block
                // controller will resync all snapshots in the snapshot set. We
                // execute this after the invalidate cache.
                waitFor = createWorkflowStepForResyncNativeSnapshot(workflow, snapshot, waitFor, rollbackMethodNullMethod());

                // Generate post restore steps
                waitFor = addPostRestoreResyncSteps(workflow, vplexToArrayVolumesToFlush, vplexVolumeIdToDetachStep, waitFor);
            }

            // Execute the workflow.
            _log.info("Executing workflow plan");
            TaskCompleter completer = new BlockSnapshotResyncCompleter(snapshot, opId);
            String successMsg = String.format("Resynchronize VPLEX native snapshot %s from volume %s "
                    + "completed successfully", snapshotURI, snapshot.getParent().getURI());
            workflow.executePlan(completer, successMsg);
            _log.info("Workflow plan executing");
        } catch (Exception e) {
            String failMsg = String.format("Resynchronize VPLEX native snapshot %s failed", snapshotURI);
            _log.error(failMsg, e);
            TaskCompleter completer = new BlockSnapshotResyncCompleter(snapshot, opId);
            ServiceError serviceError = VPlexApiException.errors.restoreVolumeFailed(snapshotURI.toString(), e);
            failStep(completer, opId, serviceError);
        }
    }

    /**
     * Create a step in the passed workflow to restore the backend
     * full copy volumes with the passed URIs.
     *
     * @param workflow
     *            A reference to a workflow.
     * @param snapshot
     *            A reference to the snapshot.
     * @param waitFor
     *            The step to wait for or null.
     * @param rollbackMethod
     *            A reference to a rollback method or null.
     *
     * @return RESYNC_SNAPSHOT_STEP
     */
    private String createWorkflowStepForResyncNativeSnapshot(Workflow workflow,
            BlockSnapshot snapshot, String waitFor, Workflow.Method rollbackMethod) {
        URI snapshotURI = snapshot.getId();
        URI parentSystemURI = snapshot.getStorageController();
        StorageSystem parentSystem = getDataObject(StorageSystem.class, parentSystemURI, _dbClient);
        URI parentVolumeURI = snapshot.getParent().getURI();
        Workflow.Method resyncSnapshotMethod = new Workflow.Method(
                RESYNC_SNAPSHOT_METHOD_NAME, parentSystemURI,
                parentVolumeURI, snapshotURI, Boolean.FALSE);
        workflow.createStep(RESYNC_SNAPSHOT_STEP, String.format(
                "Resynchronize VPLEX backend snapshot %s from source %s",
                snapshotURI, parentVolumeURI), waitFor,
                parentSystemURI, parentSystem.getSystemType(),
                BlockDeviceController.class, resyncSnapshotMethod, rollbackMethod, null);
        _log.info("Created workflow step to resync VPLEX backend snapshot {} from source {}",
                snapshotURI, parentVolumeURI);

        return RESYNC_SNAPSHOT_STEP;
    }

    /**
     * Inject coordinatorClient
     *
     * @param coordinatorClient
     */
    public static void setCoordinator(CoordinatorClient coordinator) {
        VPlexDeviceController.coordinator = coordinator;
    }

    public void pauseMigrationStep(URI vplexURI, URI migrationURI, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        try {
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
            Migration migration = getDataObject(Migration.class, migrationURI, _dbClient);
            client.pauseMigrations(Arrays.asList(migration.getLabel()));
            migration.setMigrationStatus(VPlexMigrationInfo.MigrationStatus.PAUSED.getStatusValue());
            _dbClient.updateObject(migration);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception ex) {
            _log.error("Exception pausing migration: ", ex);
            String opName = ResourceOperationTypeEnum.PAUSE_MIGRATION.getName();
            ServiceError serviceError = VPlexApiException.errors.operateMigrationFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }

    }

    public void resumeMigrationStep(URI vplexURI, URI migrationURI, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        try {
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
            Migration migration = getDataObject(Migration.class, migrationURI, _dbClient);
            client.resumeMigrations(Arrays.asList(migration.getLabel()));
            migration.setMigrationStatus(VPlexMigrationInfo.MigrationStatus.IN_PROGRESS.getStatusValue());
            _dbClient.updateObject(migration);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception ex) {
            _log.error("Exception resuming migration: ", ex);
            String opName = ResourceOperationTypeEnum.RESUME_MIGRATION.getName();
            ServiceError serviceError = VPlexApiException.errors.operateMigrationFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);

        }
    }

    public void cancelMigrationStep(URI vplexURI, URI migrationURI, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        try {
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
            Migration migration = getDataObject(Migration.class, migrationURI, _dbClient);
            client.cancelMigrations(Arrays.asList(migration.getLabel()), true, true);
            migration.setMigrationStatus(VPlexMigrationInfo.MigrationStatus.CANCELLED.getStatusValue());
            _dbClient.updateObject(migration);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception ex) {
            _log.error("Exception cancelling migration: ", ex);
            String opName = ResourceOperationTypeEnum.CANCEL_MIGRATION.getName();
            ServiceError serviceError = VPlexApiException.errors.operateMigrationFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    public void deleteMigrationStep(URI vplexURI, URI migrationURI, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        try {
            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
            Migration migration = getDataObject(Migration.class, migrationURI, _dbClient);
            client.removeMigrations(Arrays.asList(migration.getLabel()));
            migration.setInactive(true);
            _dbClient.updateObject(migration);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception ex) {
            _log.error("Exception deleting migration:", ex);
            String opName = ResourceOperationTypeEnum.DELETE_MIGRATION.getName();
            ServiceError serviceError = VPlexApiException.errors.operateMigrationFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    @Override
    public void pauseMigration(URI vplexURI, URI migrationURI, String opId) {
        MigrationOperationTaskCompleter completer = null;
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    PAUSE_MIGRATION_WF_NAME, false, opId);
            _log.info("Created pause migration workflow with operation id {}", opId);

            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            Migration migration = getDataObject(Migration.class, migrationURI, _dbClient);
            URI volId = migration.getVolume();
            completer = new MigrationOperationTaskCompleter(volId, opId);

            Workflow.Method pauseMigrationMethod = new Workflow.Method(PAUSE_MIGRATION_METHOD_NAME,
                    vplexURI, migrationURI);
            workflow.createStep(PAUSE_MIGRATION_STEP, "Pause migration", null,
                    vplexURI, vplex.getSystemType(), getClass(),
                    pauseMigrationMethod, rollbackMethodNullMethod(), null);
            String successMsg = String.format(
                    "Migration %s paused successfully", migrationURI);
            workflow.executePlan(completer, successMsg);
            _log.info("Workflow plan executing");
        } catch (Exception e) {
            String failMsg = String.format(
                    "Pause the migration %s failed", migrationURI);
            _log.error(failMsg, e);
            String opName = ResourceOperationTypeEnum.PAUSE_MIGRATION.getName();
            ServiceError serviceError = VPlexApiException.errors.operateMigrationFailed(opName, e);
            failStep(completer, opId, serviceError);
        }

    }

    @Override
    public void resumeMigration(URI vplexURI, URI migrationURI, String opId) {
        MigrationOperationTaskCompleter completer = null;
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    RESUME_MIGRATION_WF_NAME, false, opId);
            _log.info("Created resume migration workflow with operation id {}", opId);

            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            Migration migration = getDataObject(Migration.class, migrationURI, _dbClient);
            URI volId = migration.getVolume();
            completer = new MigrationOperationTaskCompleter(volId, opId);

            Workflow.Method resumeMigrationMethod = new Workflow.Method(RESUME_MIGRATION_METHOD_NAME,
                    vplexURI, migrationURI);
            workflow.createStep(RESUME_MIGRATION_STEP, "Resume migration", null,
                    vplexURI, vplex.getSystemType(), getClass(),
                    resumeMigrationMethod, rollbackMethodNullMethod(), null);

            String successMsg = String.format(
                    "Migration %s resumed successfully", migrationURI);
            workflow.executePlan(completer, successMsg);
            _log.info("Workflow plan executing");
        } catch (Exception e) {
            String failMsg = String.format(
                    "Pause the migration %s failed", migrationURI);
            _log.error(failMsg, e);
            String opName = ResourceOperationTypeEnum.RESUME_MIGRATION.getName();
            ServiceError serviceError = VPlexApiException.errors.operateMigrationFailed(opName, e);
            failStep(completer, opId, serviceError);
        }
    }

    @Override
    public void cancelMigration(URI vplexURI, URI migrationURI, String opId) {
        MigrationOperationTaskCompleter completer = null;
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    CANCEL_MIGRATION_WF_NAME, false, opId);
            _log.info("Created cancel migration workflow with operation id {}", opId);

            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            Migration migration = getDataObject(Migration.class, migrationURI, _dbClient);
            URI volId = migration.getVolume();
            completer = new MigrationOperationTaskCompleter(volId, opId);

            Workflow.Method cancelMigrationMethod = new Workflow.Method(CANCEL_MIGRATION_METHOD_NAME,
                    vplexURI, migrationURI);
            workflow.createStep(CANCEL_MIGRATION_STEP, "Cancel migration", null,
                    vplexURI, vplex.getSystemType(), getClass(),
                    cancelMigrationMethod, rollbackMethodNullMethod(), null);
            String successMsg = String.format(
                    "Migration %s cancelled successfully", migrationURI);
            workflow.executePlan(completer, successMsg);
            _log.info("Workflow plan executing");
        } catch (Exception e) {
            String failMsg = String.format(
                    "Pause the migration %s failed", migrationURI);
            _log.error(failMsg, e);
            String opName = ResourceOperationTypeEnum.CANCEL_MIGRATION.getName();
            ServiceError serviceError = VPlexApiException.errors.operateMigrationFailed(opName, e);
            failStep(completer, opId, serviceError);
        }
    }

    @Override
    public void deleteMigration(URI vplexURI, URI migrationURI, String opId) {
        MigrationOperationTaskCompleter completer = null;
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    DELETE_MIGRATION_WF_NAME, false, opId);
            _log.info("Created delete migration workflow with operation id {}", opId);

            StorageSystem vplex = getDataObject(StorageSystem.class, vplexURI, _dbClient);
            Migration migration = getDataObject(Migration.class, migrationURI, _dbClient);
            URI volId = migration.getVolume();
            completer = new MigrationOperationTaskCompleter(volId, opId);

            Workflow.Method deleteMigrationMethod = new Workflow.Method(DELETE_MIGRATION_METHOD_NAME,
                    vplexURI, migrationURI);
            workflow.createStep(DELETE_MIGRATION_STEP, "Delete migration", null,
                    vplexURI, vplex.getSystemType(), getClass(),
                    deleteMigrationMethod, rollbackMethodNullMethod(), null);

            String successMsg = String.format(
                    "Migration %s deleted successfully", migrationURI);
            workflow.executePlan(completer, successMsg);
            _log.info("Workflow plan executing");
        } catch (Exception e) {
            String failMsg = String.format(
                    "Pause the migration %s failed", migrationURI);
            _log.error(failMsg, e);
            String opName = ResourceOperationTypeEnum.DELETE_MIGRATION.getName();
            ServiceError serviceError = VPlexApiException.errors.operateMigrationFailed(opName, e);
            failStep(completer, opId, serviceError);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restoreSnapshotSession(URI vplexURI, URI snapSessionURI, String opId)
            throws InternalException {

        BlockSnapshotSession snapSession = getDataObject(BlockSnapshotSession.class, snapSessionURI, _dbClient);
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this, RESTORE_SNAP_SESSION_WF_NAME, false, opId);
            _log.info("Created restore snapshot session workflow with operation id {}", opId);

            // Get the VPLEX volume(s) to be restored.
            List<Volume> vplexVolumes = new ArrayList<Volume>();
            if (!snapSession.hasConsistencyGroup()) {
                // If the snap session is not in a CG, the only VPLEX
                // volume to restore is the VPLEX volume using the
                // snap session parent.
                URI parentVolumeURI = snapSession.getParent().getURI();
                URIQueryResultList queryResults = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getVolumeByAssociatedVolumesConstraint(parentVolumeURI.toString()),
                        queryResults);
                vplexVolumes.add(_dbClient.queryObject(Volume.class, queryResults.iterator().next()));
            } else {
                BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, snapSession.getConsistencyGroup());
                List<Volume> allVplexVolumesInCG = BlockConsistencyGroupUtils.getActiveVplexVolumesInCG(cg, _dbClient, null);
                List<BlockObject> allVplexVolumesInRG = ControllerUtils.getAllVolumesForRGInCG(allVplexVolumesInCG,
                        snapSession.getReplicationGroupInstance(), snapSession.getStorageController(), _dbClient);
                // We only want VPLEX volumes with no personality, i.e., no RP, or VPLEX volumes
                // that are RP source volumes.
                for (BlockObject vplexVolume : allVplexVolumesInRG) {
                    // RP target and sources restore is supported
                    vplexVolumes.add((Volume) vplexVolume);
                }
            }

            // Determine the backend storage system containing the native snapshot session.
            Volume firstVplexVolume = vplexVolumes.get(0);
            Volume firstSnapSessionParentVolume = VPlexUtil.getVPLEXBackendVolume(firstVplexVolume, true, _dbClient);
            StorageSystem snapSessionSystem = getDataObject(StorageSystem.class, firstSnapSessionParentVolume.getStorageController(),
                    _dbClient);

            // Maps Vplex volume that needs to be flushed to underlying array volume
            Map<Volume, Volume> vplexToArrayVolumesToFlush = new HashMap<Volume, Volume>();
            for (Volume vplexVolume : vplexVolumes) {
                Volume arrayVolumeToBeRestored = VPlexUtil.getVPLEXBackendVolume(
                        vplexVolume, true, _dbClient);
                vplexToArrayVolumesToFlush.put(vplexVolume, arrayVolumeToBeRestored);
            }
            Map<URI, String> vplexVolumeIdToDetachStep = new HashMap<URI, String>();
            boolean isRP = firstVplexVolume.checkForRp();
            if (null == firstVplexVolume.getAssociatedVolumes() || firstVplexVolume.getAssociatedVolumes().isEmpty()) {
                _log.error("VPLEX volume {} has no backend volumes.", firstVplexVolume.forDisplay());
                throw InternalServerErrorException.internalServerErrors.noAssociatedVolumesForVPLEXVolume(firstVplexVolume.forDisplay());
            }
            boolean isDistributed = firstVplexVolume.getAssociatedVolumes().size() > 1;

            String waitFor = null;
            if (isRP && isDistributed) {
                ProtectionSystem rpSystem = getDataObject(ProtectionSystem.class,
                        firstVplexVolume.getProtectionController(), _dbClient);
                // Create the pre restore step which will be the first step executed
                // in the workflow.
                createWorkflowStepForDeleteReplicationSet(workflow, rpSystem, vplexVolumes, null);
            }

            // Generate pre restore steps
            waitFor = addPreRestoreResyncSteps(workflow, vplexToArrayVolumesToFlush, vplexVolumeIdToDetachStep, waitFor);

            // Now create a workflow step to natively restore the backend
            // volume. We execute this after the invalidate cache.
            createWorkflowStepForRestoreNativeSnapshotSession(workflow, snapSessionSystem,
                    snapSessionURI, waitFor, rollbackMethodNullMethod());

            // Generate post restore steps
            waitFor = addPostRestoreResyncSteps(workflow, vplexToArrayVolumesToFlush, vplexVolumeIdToDetachStep, waitFor);

            if (isRP && isDistributed) {
                ProtectionSystem rpSystem = getDataObject(ProtectionSystem.class,
                        firstVplexVolume.getProtectionController(), _dbClient);
                // Create the post restore step, which will be the last step executed
                // in the workflow after the volume shave been rebuilt.
                waitFor = createWorkflowStepForRecreateReplicationSet(workflow, rpSystem, vplexVolumes, waitFor);
            }

            // Execute the workflow.
            _log.info("Executing workflow plan");
            TaskCompleter completer = new BlockSnapshotSessionRestoreWorkflowCompleter(snapSessionURI, Boolean.TRUE, opId);
            String successMsg = String.format(
                    "Restore VPLEX volume(s) from snapshot session %s" + "completed successfully", snapSessionURI);
            workflow.executePlan(completer, successMsg);
            _log.info("Workflow plan executing");
        } catch (Exception e) {
            String failMsg = String.format(
                    "Restore VPLEX volume from snapshot session %s failed", snapSessionURI);
            _log.error(failMsg, e);
            TaskCompleter completer = new BlockSnapshotSessionRestoreWorkflowCompleter(snapSessionURI, Boolean.TRUE, opId);
            ServiceError serviceError = VPlexApiException.errors.restoreVolumeFailed(
                    snapSessionURI.toString(), e);
            failStep(completer, opId, serviceError);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void relinkTargetsToSnapshotSession(URI vplexURI, URI tgtSnapSessionURI, List<URI> snapshotURIs,
            String opId) throws InternalException {
        try {
            // Create a new the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    RELINK_SNAPSHOT_SESSION_TARGETS_WF_NAME, false, opId);
            _log.info("Created relink snapshot session targets workflow with operation id {}", opId);

            // First if this is a group operation, we make sure we only process
            // one snapshot per replication group.
            List<URI> filteredSnapshotURIs = new ArrayList<URI>();
            BlockSnapshotSession tgtSnapSession = _dbClient.queryObject(BlockSnapshotSession.class, tgtSnapSessionURI);
            if (tgtSnapSession.hasConsistencyGroup()
                    && NullColumnValueGetter.isNotNullValue(tgtSnapSession.getReplicationGroupInstance())) {
                filteredSnapshotURIs.addAll(ControllerUtils.ensureOneSnapshotPerReplicationGroup(snapshotURIs, _dbClient));
            } else {
                filteredSnapshotURIs.addAll(snapshotURIs);
            }

            // Now we need to make sure we get all the snapshots in each
            // replication group. If a snapshot is not in a replication group,
            // this will just add the snapshot.
            List<BlockSnapshot> snapshotsToRelink = new ArrayList<BlockSnapshot>();
            for (URI filteredSnapshotURI : filteredSnapshotURIs) {
                BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, filteredSnapshotURI);
                snapshotsToRelink.addAll(ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshot, _dbClient));
            }

            // Get a list of the VPLEX volumes, if any, that are built
            // using the snapshot target volumes.
            List<Volume> vplexVolumes = VPlexUtil.getVPlexVolumesBuiltOnSnapshots(snapshotsToRelink, _dbClient);

            // Create the workflow steps.
            if (vplexVolumes.isEmpty()) {
                // If there are no VPLEX volumes built on the snapshots to be relinked,
                // then we just need a single step to invoke the block device controller to
                // relink the snapshots.
                createWorkflowStepForRelinkNativeTargets(workflow, tgtSnapSession, snapshotURIs, null, null);
            } else {
                String waitFor = null;

                // Maps Vplex volume that needs to be flushed to underlying array volume
                Map<Volume, Volume> vplexToArrayVolumesToFlush = new HashMap<Volume, Volume>();
                for (Volume vplexVolume : vplexVolumes) {
                    Volume arrayVolumeToBeRelinked = VPlexUtil.getVPLEXBackendVolume(
                            vplexVolume, true, _dbClient);
                    vplexToArrayVolumesToFlush.put(vplexVolume, arrayVolumeToBeRelinked);
                }

                // Generate pre restore steps
                Map<URI, String> vplexVolumeIdToDetachStep = new HashMap<URI, String>();
                waitFor = addPreRestoreResyncSteps(workflow, vplexToArrayVolumesToFlush, vplexVolumeIdToDetachStep, waitFor);

                // Now create a workflow step to natively relink the snapshots.
                // Note that if a snapshot is associated with a CG, then block
                // controller will relink all snapshots in the snapshot set. We
                // execute this after the invalidate cache.
                waitFor = createWorkflowStepForRelinkNativeTargets(workflow, tgtSnapSession, snapshotURIs, waitFor,
                        rollbackMethodNullMethod());

                // Generate post restore steps
                addPostRestoreResyncSteps(workflow, vplexToArrayVolumesToFlush, vplexVolumeIdToDetachStep, waitFor);
            }

            // Execute the workflow.
            _log.info("Executing workflow plan");
            TaskCompleter completer = new BlockSnapshotSessionRelinkTargetsWorkflowCompleter(tgtSnapSessionURI, Boolean.TRUE, opId);
            String successMsg = String.format("Relink VPLEX native snapshot session targets %s to session %s "
                    + "completed successfully", snapshotURIs, tgtSnapSessionURI);
            workflow.executePlan(completer, successMsg);
            _log.info("Workflow plan executing");
        } catch (Exception e) {
            String failMsg = String.format("Relink VPLEX native snapshot session targets %s to session %s failed", snapshotURIs,
                    tgtSnapSessionURI);
            _log.error(failMsg, e);
            TaskCompleter completer = new BlockSnapshotSessionRelinkTargetsWorkflowCompleter(tgtSnapSessionURI, Boolean.TRUE, opId);
            ServiceError serviceError = VPlexApiException.errors.relinkSnapshotSessionTargetsFailed(snapshotURIs, tgtSnapSessionURI, e);
            failStep(completer, opId, serviceError);
        }
    }

    /**
     * Create a step in the passed workflow to call the block controller to natively
     * relink the passed linked targets to the passed target snapshot session.
     * 
     * @param workflow A reference to a workflow.
     * @param tgtSnapSession A reference to the target snapshot session.
     * @param snapshotURIs The URIs of the block snapshot targets
     * @param waitFor The step this step should wait for, or null to wait for nothing
     * @param rollbackMethod A reference to a rollback method, or null.
     * 
     * @return RELINK_SNAPSHOT_SESSION_TARGET_STEP
     */
    private String createWorkflowStepForRelinkNativeTargets(Workflow workflow,
            BlockSnapshotSession tgtSnapSession, List<URI> snapshotURIs, String waitFor, Workflow.Method rollbackMethod) {
        URI parentSystemURI = tgtSnapSession.getStorageController();
        StorageSystem parentSystem = getDataObject(StorageSystem.class, parentSystemURI, _dbClient);
        Workflow.Method relinkMethod = new Workflow.Method(
                RELINK_SNAPSHOT_SESSION_TARGETS_METHOD_NAME, parentSystemURI,
                tgtSnapSession.getId(), snapshotURIs, Boolean.FALSE);
        workflow.createStep(RELINK_SNAPSHOT_SESSION_TARGET_STEP, String.format(
                "Relink VPLEX backend snapshot session targets %s to session %s",
                snapshotURIs, tgtSnapSession.getId()), waitFor,
                parentSystemURI, parentSystem.getSystemType(),
                BlockDeviceController.class, relinkMethod, rollbackMethod, null);
        _log.info("Created workflow step to relink VPLEX backend snapshot session targets {} to session {}",
                snapshotURIs, tgtSnapSession.getId());

        return RELINK_SNAPSHOT_SESSION_TARGET_STEP;
    }

    /**
     * Create a step in the passed workflow that will temporarily delete the RP replication
     * set prior to a snapshot session restore.
     *
     * @param workflow
     *            A reference to a workflow.
     * @param rpSystem
     *            A reference to the RP protection system.
     * @param vplexVolumes
     *            A list of the VPLEX distributed volumes.
     * @param waitFor
     *            The step to wait for completion.
     *
     * @return RPDeviceController.STEP_PRE_VOLUME_RESTORE
     */
    private String createWorkflowStepForDeleteReplicationSet(Workflow workflow, ProtectionSystem rpSystem,
            List<Volume> vplexVolumes, String waitFor) {
        List<URI> vplexVolumeURIs = new ArrayList<>();
        Map<String, RecreateReplicationSetRequestParams> params = getRecreateReplicationSetParams(rpSystem, vplexVolumes, vplexVolumeURIs);

        acquireRPWorkflowLock(workflow, rpSystem.getId(), vplexVolumeURIs);

        Workflow.Method executeMethod = new Workflow.Method(RPDeviceController.METHOD_DELETE_RSET_STEP, rpSystem.getId(), vplexVolumeURIs);
        Workflow.Method rollbackMethod = new Workflow.Method(RPDeviceController.METHOD_RECREATE_RSET_STEP, rpSystem.getId(),
                vplexVolumeURIs, params);
        workflow.createStep(RPDeviceController.STEP_PRE_VOLUME_RESTORE,
                "Delete RP replication set step for snapshot session restore",
                waitFor, rpSystem.getId(), rpSystem.getSystemType(), RPDeviceController.class,
                executeMethod, rollbackMethod, null);

        return RPDeviceController.STEP_PRE_VOLUME_RESTORE;
    }

    /**
     * acquires a workflow lock for the RecoverPoint CG
     *
     * @param workflow
     *            workflow to lock
     * @param rpSystemId
     *            rp protection system id which will be part of the lock key
     * @param volumeIds
     *            list of volumes to identify consistency groups to lock
     */
    private void acquireRPWorkflowLock(Workflow workflow, URI rpSystemId, List<URI> volumeIds) {

        // this RP specific code should be moved out of this class with https://coprhd.atlassian.net/browse/COP-22852

        Set<URI> cgUris = new HashSet<URI>();
        for (URI volumeId : volumeIds) {
            Volume volume = _dbClient.queryObject(Volume.class, volumeId);
            if (volume != null && !volume.getInactive() && !NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
                cgUris.add(volume.getConsistencyGroup());
            }
        }
        // lock around create and delete operations on the same CG
        List<String> lockKeys = new ArrayList<String>();
        for (URI cgUri : cgUris) {
            lockKeys.add(ControllerLockingUtil.getConsistencyGroupStorageKey(_dbClient, cgUri, rpSystemId));
        }
        if (!lockKeys.isEmpty()) {
            boolean lockAcquired = _workflowService.acquireWorkflowLocks(workflow, lockKeys, LockTimeoutValue.get(LockType.RP_CG));
            if (!lockAcquired) {
                throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(),
                        String.format("acquire lock for RP consistency group(s) %s", StringUtils.join(cgUris, ",")));
            }
        }
    }

    /**
     * Create a step in the passed workflow that will recreate the RP replication set
     * during a snapshot session restore after the remote mirrors have been reattached
     * and the distributed VPLEX volumes are added back to their consistency group.
     *
     * @param workflow
     *            A reference to a workflow.
     * @param rpSystem
     *            A reference to the RP protection system.
     * @param vplexVolumes
     *            A list of the VPLEX distributed volumes.
     * @param waitFor
     *            The step to wait for completion.
     *
     * @return RPDeviceController.STEP_POST_VOLUME_RESTORE
     */
    private String createWorkflowStepForRecreateReplicationSet(Workflow workflow, ProtectionSystem rpSystem,
            List<Volume> vplexVolumes, String waitFor) {
        List<URI> vplexVolumeURIs = new ArrayList<>();
        Map<String, RecreateReplicationSetRequestParams> params = getRecreateReplicationSetParams(rpSystem, vplexVolumes, vplexVolumeURIs);
        Workflow.Method executeMethod = new Workflow.Method(RPDeviceController.METHOD_RECREATE_RSET_STEP, rpSystem.getId(),
                vplexVolumeURIs, params);
        workflow.createStep(RPDeviceController.STEP_POST_VOLUME_RESTORE,
                "Recreate RP replication set step for snapshot session restore",
                waitFor, rpSystem.getId(), rpSystem.getSystemType(), RPDeviceController.class,
                executeMethod, rollbackMethodNullMethod(), null);
        return RPDeviceController.STEP_POST_VOLUME_RESTORE;
    }

    /**
     * Gets the replication set parameters.
     *
     * @param rpSystem
     *            A reference to the RP protection system.
     * @param vplexVolumes
     *            A list of the VPLEX distributed volumes.
     * @param vplexVolumeURIs
     *            An OUT parameters containing the URIs of the passed VPLEX volumes.
     *
     * @return
     */
    private Map<String, RecreateReplicationSetRequestParams> getRecreateReplicationSetParams(ProtectionSystem rpSystem,
            List<Volume> vplexVolumes, List<URI> vplexVolumeURIs) {
        Map<String, RecreateReplicationSetRequestParams> params = new HashMap<String, RecreateReplicationSetRequestParams>();
        for (Volume vplexVolume : vplexVolumes) {
            URI vplexVolumeURI = vplexVolume.getId();
            vplexVolumeURIs.add(vplexVolumeURI);
            RecreateReplicationSetRequestParams volumeParam = _rpDeviceController.getReplicationSettings(rpSystem, vplexVolumeURI);
            params.put(RPHelper.getRPWWn(vplexVolumeURI, _dbClient), volumeParam);
        }
        return params;
    }

    /**
     * Create a step in the passed workflow to do a native restore of
     * the backend snapshot session with the passed URI.
     *
     * @param workflow
     *            A reference to a workflow.
     * @param parentSystem
     *            The backend storage system,
     * @param snapSessionURI
     *            The URI of the snapshot session.
     * @param waitFor
     *            The step to wait for or null.
     * @param rollbackMethod
     *            A reference to a rollback method or null.
     *
     * @return RESTORE_SNAP_SESSION_STEP
     */
    private String createWorkflowStepForRestoreNativeSnapshotSession(Workflow workflow, StorageSystem parentSystem,
            URI snapSessionURI, String waitFor, Workflow.Method rollbackMethod) {
        URI parentSystemURI = parentSystem.getId();
        Workflow.Method restoreMethod = new Workflow.Method(
                RESTORE_SNAP_SESSION_METHOD_NAME, parentSystemURI, snapSessionURI, Boolean.FALSE);
        workflow.createStep(RESTORE_SNAP_SESSION_STEP, String.format(
                "Restore snapshot session %s", snapSessionURI), waitFor,
                parentSystemURI, parentSystem.getSystemType(),
                BlockDeviceController.class, restoreMethod, rollbackMethod, null);
        _log.info("Created workflow step to restore snapshot session {}", snapSessionURI);

        return RESTORE_SNAP_SESSION_STEP;
    }

    @Override
    public void updateVolumeGroup(URI vplexURI, ApplicationAddVolumeList addVolList, List<URI> removeVolumeList, URI volumeGroup,
            String opId) throws InternalException {
        _log.info("Update volume group {}", volumeGroup);
        TaskCompleter completer = null;
        List<URI> addVols = null;
        String waitFor = null;
        // Get a new workflow to execute the volume group update.
        Workflow workflow = _workflowService.getNewWorkflow(this, UPDATE_VOLUMEGROUP_WF_NAME,
                false, opId);
        Set<URI> cgs = new HashSet<URI>();
        try {
            List<URI> allRemoveBEVolumes = new ArrayList<URI>();
            if (removeVolumeList != null && !removeVolumeList.isEmpty()) {
                _log.info("Creating steps for removing volumes from the volume group");
                for (URI voluri : removeVolumeList) {
                    Volume vol = getDataObject(Volume.class, voluri, _dbClient);
                    if (vol == null || vol.getInactive()) {
                        _log.info(String.format("The volume: %s has been deleted. Skip it.", voluri));
                        continue;
                    }
                    cgs.add(vol.getConsistencyGroup());
                    StringSet backends = vol.getAssociatedVolumes();
                    if (backends == null) {
                        _log.info(String.format("The volume: %s do not have backend volumes. Skip it.", vol.getLabel()));
                        continue;
                    }
                    for (String backendId : backends) {
                        allRemoveBEVolumes.add(URI.create(backendId));
                    }
                }
            }
            List<URI> allAddBEVolumes = new ArrayList<URI>();
            ApplicationAddVolumeList addBEVolList = new ApplicationAddVolumeList();
            if (addVolList != null && addVolList.getVolumes() != null && !addVolList.getVolumes().isEmpty()) {
                _log.info("Creating steps for adding volumes to the volume group");

                addVols = addVolList.getVolumes();

                for (URI addVol : addVols) {
                    Volume addVplexVol = getDataObject(Volume.class, addVol, _dbClient);
                    if (addVplexVol == null || addVplexVol.getInactive()) {
                        _log.info(String.format("The volume: %s has been deleted. Skip it.", addVol));
                        continue;
                    }

                    cgs.add(addVplexVol.getConsistencyGroup());
                    StringSet backends = addVplexVol.getAssociatedVolumes();
                    if (backends == null) {
                        _log.info(String.format("The volume: %s do not have backend volumes. Skip it.", addVol));
                        continue;
                    }
                    for (String backendId : backends) {
                        URI backUri = URI.create(backendId);
                        Volume backVol = getDataObject(Volume.class, backUri, _dbClient);
                        if (backVol != null && !backVol.getInactive()) {
                            allAddBEVolumes.add(backUri);
                        }
                    }
                }
            }

            completer = new VolumeGroupUpdateTaskCompleter(volumeGroup, addVols, removeVolumeList, cgs, opId);

            addBEVolList.setVolumes(allAddBEVolumes);
            addBEVolList.setReplicationGroupName(addVolList.getReplicationGroupName());
            addBEVolList.setConsistencyGroup(addVolList.getConsistencyGroup());

            // add steps for add source and remove vols
            waitFor = _blockDeviceController.addStepsForUpdateApplication(workflow, addBEVolList, allRemoveBEVolumes, waitFor, opId);

            addStepsForImportClonesOfApplicationVolumes(workflow, waitFor, addVolList.getVolumes(), opId);

            // Finish up and execute the plan.
            _log.info("Executing workflow plan {}", UPDATE_VOLUMEGROUP_WF_NAME);
            String successMessage = String.format(
                    "Update volume group successful for %s", volumeGroup.toString());
            workflow.executePlan(completer, successMessage);
        } catch (Exception e) {
            _log.error("Exception while updating the volume group", e);
            DeviceControllerException ex = DeviceControllerException.exceptions.failedToUpdateVolumesFromAppication(volumeGroup.toString(),
                    e.getMessage());
            if (completer != null) {
                completer.error(_dbClient, ex);
            } else {
                throw ex;
            }
        }
    }

    /**
     * This method will add steps to import the clone of the source backend volumes,
     * create clone VPlex virtual volumes
     * create HA backend volume for clone virtual volume if distributed.
     *
     * @param workflow
     * @param waitFor
     * @param sourceVolumes
     * @param opId
     * @return
     */
    public void addStepsForImportClonesOfApplicationVolumes(Workflow workflow, String waitFor, List<URI> sourceVolumes, String opId) {
        _log.info("Creating steps for importing clones");
        for (URI vplexSrcUri : sourceVolumes) {
            Volume vplexSrcVolume = getDataObject(Volume.class, vplexSrcUri, _dbClient);
            Volume backendSrc = VPlexUtil.getVPLEXBackendVolume(vplexSrcVolume, true, _dbClient);
            long size = backendSrc.getProvisionedCapacity();
            Volume backendHASrc = VPlexUtil.getVPLEXBackendVolume(vplexSrcVolume, false, _dbClient);
            StringSet backSrcCopies = backendSrc.getFullCopies();
            if (backSrcCopies != null && !backSrcCopies.isEmpty()) {
                for (String copy : backSrcCopies) {
                    List<VolumeDescriptor> vplexVolumeDescriptors = new ArrayList<VolumeDescriptor>();
                    List<VolumeDescriptor> blockDescriptors = new ArrayList<VolumeDescriptor>();
                    Volume backCopy = getDataObject(Volume.class, URI.create(copy), _dbClient);
                    String name = backCopy.getLabel();
                    _log.info(String.format("Creating steps for import clone %s.", name));
                    VolumeDescriptor vplexCopyVolume = prepareVolumeDescriptor(vplexSrcVolume, name,
                            VolumeDescriptor.Type.VPLEX_VIRT_VOLUME, size, false);
                    Volume vplexCopy = getDataObject(Volume.class, vplexCopyVolume.getVolumeURI(), _dbClient);
                    vplexCopy.setAssociatedVolumes(new StringSet());
                    StringSet assVol = vplexCopy.getAssociatedVolumes();
                    if (null == assVol) {
                        assVol = new StringSet();
                        vplexCopy.setAssociatedVolumes(assVol);
                    }
                    assVol.add(backCopy.getId().toString());

                    VirtualPoolCapabilityValuesWrapper capabilities = getCapabilities(backCopy, size);
                    VolumeDescriptor backCopyDesc = new VolumeDescriptor(VolumeDescriptor.Type.VPLEX_IMPORT_VOLUME,
                            backCopy.getStorageController(), backCopy.getId(), backCopy.getPool(), capabilities);
                    blockDescriptors.add(backCopyDesc);
                    if (backendHASrc != null) {
                        // distributed volume
                        name = name + "-ha";
                        VolumeDescriptor haDesc = prepareVolumeDescriptor(backendHASrc, name,
                                VolumeDescriptor.Type.BLOCK_DATA, size, true);
                        blockDescriptors.add(haDesc);
                        assVol.add(haDesc.getVolumeURI().toString());
                    }
                    vplexCopy.setFullCopySetName(backCopy.getFullCopySetName());
                    vplexCopy.setAssociatedSourceVolume(vplexSrcUri);
                    StringSet srcClones = vplexSrcVolume.getFullCopies();
                    if (srcClones == null) {
                        srcClones = new StringSet();
                    }
                    srcClones.add(vplexCopy.getId().toString());
                    backCopy.setFullCopySetName(NullColumnValueGetter.getNullStr());
                    backCopy.addInternalFlags(Flag.INTERNAL_OBJECT);
                    _dbClient.updateObject(backCopy);
                    _dbClient.updateObject(vplexCopy);
                    _dbClient.updateObject(vplexSrcVolume);
                    vplexVolumeDescriptors.add(vplexCopyVolume);
                    createStepsForFullCopyImport(workflow, vplexSrcVolume.getStorageController(),
                            vplexVolumeDescriptors, blockDescriptors, waitFor);
                }
            }
        }
        _log.info("Created workflow steps to import the backend full copies");
    }

    /**
     * Create a volume instance and VolumeDescriptor using the characteristics of the passed in source volume.
     *
     * @param source
     *            - The volume will be used to create the volume instance
     * @param name
     *            - The new volume label
     * @param type
     *            - VolumeDescriptor type
     * @param size
     *            - The volume size
     * @param isInternal
     *            -If the volume is internal
     * @return - The newly created VolumeDescriptor
     */
    private VolumeDescriptor prepareVolumeDescriptor(Volume source, String name, VolumeDescriptor.Type type, long size,
            boolean isInternal) {
        Volume volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        volume.setLabel(name);
        volume.setCapacity(size);
        URI vpoolUri = source.getVirtualPool();
        VirtualPool vpool = getDataObject(VirtualPool.class, vpoolUri, _dbClient);
        volume.setThinlyProvisioned(VirtualPool.ProvisioningType.Thin.toString()
                .equalsIgnoreCase(vpool.getSupportedProvisioningType()));
        volume.setVirtualPool(vpool.getId());
        URI projectId = source.getProject().getURI();
        Project project = getDataObject(Project.class, projectId, _dbClient);
        volume.setProject(new NamedURI(projectId, volume.getLabel()));
        volume.setTenant(new NamedURI(project.getTenantOrg().getURI(), volume.getLabel()));
        volume.setVirtualArray(source.getVirtualArray());
        volume.setPool(source.getPool());

        volume.setProtocol(source.getProtocol());
        volume.setStorageController(source.getStorageController());
        volume.setSystemType(source.getSystemType());
        if (isInternal) {
            volume.addInternalFlags(Flag.INTERNAL_OBJECT);
        }
        _dbClient.createObject(volume);

        VirtualPoolCapabilityValuesWrapper capabilities = getCapabilities(source, size);

        return new VolumeDescriptor(type, volume.getStorageController(),
                volume.getId(), volume.getPool(), capabilities);
    }

    /**
     * Create a VirtualPoolCapabilityValuesWrapper based on the passed in volume
     *
     * @param volume
     *            - The volume used to create the VirtualPoolCapabilityValuesWrapper.
     * @param size
     * @return
     */
    private VirtualPoolCapabilityValuesWrapper getCapabilities(Volume volume, long size) {
        URI vpoolUri = volume.getVirtualPool();
        VirtualPool vpool = getDataObject(VirtualPool.class, vpoolUri, _dbClient);
        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, size);
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, 1);
        if (VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(
                vpool.getSupportedProvisioningType())) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
            // To guarantee that storage pool for a copy has enough physical
            // space to contain current allocated capacity of thin source volume
            capabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_VOLUME_PRE_ALLOCATE_SIZE,
                    volume.getAllocatedCapacity());
        }
        return capabilities;
    }

    /**
     * Add vplex steps that need to be done before restore / resync.
     * These have to do with flushing the vplex cache(s).
     *
     * @param workflow
     *            -- workflow the steps need to be added into
     * @param vplexToArrayVolumes
     *            -- A map of vplex volume to the corresponding array volume that will be restored/resynced.
     *            Note that for HA volumes, the other leg is considered the mirror that will be detached.
     * @param vplexVolumeIdToDetachStep
     *            -- OUT: a map if distributed virtual volume to the detach step (used in rollback)
     * @param inputWaitFor
     *            -- previous step id in the workflow that triggers these steps
     * @return INVALIDATE_CACHE_STEP
     */
    public String addPreRestoreResyncSteps(Workflow workflow,
            Map<Volume, Volume> vplexToArrayVolumes, Map<URI, String> vplexVolumeIdToDetachStep,
            String inputWaitFor) {
        if (vplexToArrayVolumes.isEmpty()) {
            return inputWaitFor;
        }

        // Make a map of vplex system to volumes to be flushed
        Map<URI, List<Volume>> vplexSystemToVolumes = new HashMap<URI, List<Volume>>();
        for (Volume vplexVolume : vplexToArrayVolumes.keySet()) {
            URI storageController = vplexVolume.getStorageController();
            if (!vplexSystemToVolumes.containsKey(storageController)) {
                vplexSystemToVolumes.put(storageController, new ArrayList<Volume>());
            }
            vplexSystemToVolumes.get(storageController).add(vplexVolume);
        }

        // Iterate over all vplex systems, performing the necessary steps.
        for (Map.Entry<URI, List<Volume>> entry : vplexSystemToVolumes.entrySet()) {
            StorageSystem vplexSystem = _dbClient.queryObject(StorageSystem.class, entry.getKey());
            String waitFor = inputWaitFor;

            // Determine the local volumes vs. distributed volumes.
            List<Volume> volumesToFlush = new ArrayList<Volume>();
            List<Volume> distributedVolumes = new ArrayList<Volume>();
            for (Volume vplexVolume : entry.getValue()) {
                if (null == vplexVolume.getAssociatedVolumes() || vplexVolume.getAssociatedVolumes().isEmpty()) {
                    _log.error("VPLEX volume {} has no backend volumes.", vplexVolume.forDisplay());
                    throw InternalServerErrorException.internalServerErrors.noAssociatedVolumesForVPLEXVolume(vplexVolume.forDisplay());
                }
                if (vplexVolume.getAssociatedVolumes().size() > 1) {
                    distributedVolumes.add(vplexVolume);
                }
                volumesToFlush.add(vplexVolume);
            }

            // For distributed volumes, detach mirror (aka the side that is not getting
            // updated after the cache flush.)
            for (Volume distributedVolume : distributedVolumes) {
                

                // For distributed volumes before we can do the
                // operation, we need to detach the associated volume that
                // will not be updated. Create a workflow step to detach it.
                String detachStepId = workflow.createStepId();
                Workflow.Method restoreVolumeRollbackMethod = createRestoreResyncRollbackMethod(
                        entry.getKey(), distributedVolume.getId(), 
                        distributedVolume.getConsistencyGroup(), detachStepId);
                createWorkflowStepForDetachMirror(workflow, vplexSystem,
                        distributedVolume, detachStepId, waitFor,
                        restoreVolumeRollbackMethod);
                vplexVolumeIdToDetachStep.put(distributedVolume.getId(), detachStepId);

            }
            if (!distributedVolumes.isEmpty()) {
                waitFor = DETACH_MIRROR_STEP;
            }

            // Now invalidate the cache for both distributed and local volumes
            for (Volume volumeToFlush : volumesToFlush) {
                // Now create a workflow step to invalidate the cache for the
                // volume that will be updated.
                createWorkflowStepForInvalidateCache(workflow, vplexSystem,
                        volumeToFlush.getId(), waitFor, rollbackMethodNullMethod());
            }
        }
        return INVALIDATE_CACHE_STEP;
    }

    /**
     * Adds the steps post restore / resync that need to be done to reconnect the HA side of
     * distributed volumes.
     *
     * @param workflow
     *            -- Workflow steps are to be added to
     * @param vplexToArrayVolumes
     *            -- map of vplex volume to corresponding array volume
     * @param vplexVolumeIdToDetachStep
     *            -- map of vplex distributed volume to detach step
     *            that was generated in addPrePostRestoreResyncSteps
     * @param inputWaitFor
     *            -- previous step id that will trigger these steps.
     * @return WAIT_ON_REBUILD_STEP
     */
    public String addPostRestoreResyncSteps(Workflow workflow,
            Map<Volume, Volume> vplexToArrayVolumes,
            Map<URI, String> vplexVolumeIdToDetachStep, String inputWaitFor) {

        // If there were no detach steps executed, nothing to do.
        if (vplexToArrayVolumes.isEmpty() || vplexVolumeIdToDetachStep.isEmpty()) {
            return inputWaitFor;
        }

        // Make a map of vplex system to volumes to be flushed
        Map<URI, List<Volume>> vplexSystemToVolumes = new HashMap<URI, List<Volume>>();
        for (Volume vplexVolume : vplexToArrayVolumes.keySet()) {
            URI storageController = vplexVolume.getStorageController();
            if (!vplexSystemToVolumes.containsKey(storageController)) {
                vplexSystemToVolumes.put(storageController, new ArrayList<Volume>());
            }
            vplexSystemToVolumes.get(storageController).add(vplexVolume);
        }

        // Iterate over all vplex systems, performing the necessary steps.
        for (Map.Entry<URI, List<Volume>> entry : vplexSystemToVolumes.entrySet()) {
            StorageSystem vplexSystem = _dbClient.queryObject(StorageSystem.class, entry.getKey());

            // Determine the distributed volumes. No action is required on local volumes.
            List<Volume> distributedVolumes = new ArrayList<Volume>();
            for (Volume vplexVolume : entry.getValue()) {
                if (null == vplexVolume.getAssociatedVolumes() || vplexVolume.getAssociatedVolumes().isEmpty()) {
                    _log.error("VPLEX volume {} has no backend volumes.", vplexVolume.forDisplay());
                    throw InternalServerErrorException.internalServerErrors.noAssociatedVolumesForVPLEXVolume(vplexVolume.forDisplay());
                }
                if (vplexVolume.getAssociatedVolumes().size() > 1) {
                    distributedVolumes.add(vplexVolume);
                }
            }

            // Fire off steps for re-attaching the mirror on distributed volumes.
            for (Volume distributedVolume : distributedVolumes) {                
                // Now create a workflow step to reattach the mirror to initiate
                // a rebuild of the HA mirror for the full copy source volume.
                // Note that these steps will not run until after the native
                // restore, which only gets executed once.
                String rebuildStep = createWorkflowStepForAttachMirror(workflow, vplexSystem,
                        distributedVolume, vplexVolumeIdToDetachStep.get(distributedVolume.getId()),
                        inputWaitFor, rollbackMethodNullMethod());

                // Create a step to wait for rebuild of the HA volume to
                // complete. This should not do any rollback if the step
                // fails because at this point the restore is really
                // complete.
                createWorkflowStepForWaitOnRebuild(workflow, vplexSystem,
                        distributedVolume.getId(), rebuildStep);
            }

        }
        
        return WAIT_ON_REBUILD_STEP;
    }

    /**
     * Add steps to restore full copy
     *
     * @param workflow
     *            - the workflow the steps would be added to
     * @param waitFor
     *            - the step would be waited before the added steps would be executed
     * @param storage
     *            - the storage controller URI
     * @param fullcopies
     *            - the full copies to restore
     * @param opId
     * @param completer
     *            - the CloneRestoreCompleter
     * @return the step id for the added step
     * @throws InternalException
     */
    public String addStepsForRestoreFromFullcopy(Workflow workflow,
            String waitFor, URI storage, List<URI> fullcopies, String opId,
            CloneRestoreCompleter completer) throws InternalException {

        Volume firstFullCopy = getDataObject(Volume.class, fullcopies.get(0), _dbClient);
        if (!firstFullCopy.isVPlexVolume(_dbClient)) {
            return waitFor;
        }
        BlockObject firstSource = BlockObject.fetch(_dbClient, firstFullCopy.getAssociatedSourceVolume());
        if (!NullColumnValueGetter.isNullURI(firstSource.getConsistencyGroup())) {
            completer.addConsistencyGroupId(firstSource.getConsistencyGroup());
        }
        StorageSystem vplexSystem = _dbClient.queryObject(StorageSystem.class, storage);

        Workflow.Method restoreFromFullcopyMethod = new Workflow.Method(
                RESTORE_FROM_FULLCOPY_METHOD_NAME, storage, fullcopies);
        waitFor = workflow.createStep(RESTORE_FROM_FULLCOPY_STEP,
                "Restore volumes from full copies", waitFor,
                storage, vplexSystem.getSystemType(),
                VPlexDeviceController.class, restoreFromFullcopyMethod, null, null);
        _log.info("Created workflow step to restore VPLEX volume from full copies");

        return waitFor;
    }

    private static VPlexApiClient getVPlexAPIClient(VPlexApiFactory vplexApiFactory,
            URI vplexUri, DbClient dbClient) throws URISyntaxException {
        VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(vplexApiFactory, vplexUri, dbClient);
        updateTimeoutValues();
        return client;
    }

    private static VPlexApiClient getVPlexAPIClient(VPlexApiFactory vplexApiFactory,
            StorageSystem vplexSystem, DbClient dbClient) throws URISyntaxException {
        VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(vplexApiFactory, vplexSystem, dbClient);
        updateTimeoutValues();
        return client;
    }

    private static VPlexApiClient getVPlexAPIClient(VPlexApiFactory vplexApiFactory,
            StorageProvider vplexMnmgtSvr, DbClient dbClient) throws URISyntaxException {
        VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(vplexApiFactory, vplexMnmgtSvr, dbClient);
        updateTimeoutValues();
        return client;
    }

    private static void updateTimeoutValues() {
        if (null == coordinator) {
            return;
        }

        // Update the timeout values
        int maxAsyncPollingRetries = Integer.valueOf(ControllerUtils.getPropertyValueFromCoordinator(coordinator,
                "controller_vplex_max_async_polls"));
        VPlexApiClient.setMaxAsyncPollingRetries(maxAsyncPollingRetries);
        int maxMigrationAsyncPollingRetries = Integer.valueOf(ControllerUtils.getPropertyValueFromCoordinator(coordinator,
                "controller_vplex_migration_max_async_polls"));
        VPlexApiClient.setMaxMigrationAsyncPollingRetries(maxMigrationAsyncPollingRetries);
    }

    /**
     * Checks the result of a thin provisioning request after a virtual volume
     * creation request and logs a warning if necessary.
     *
     * @param info
     *            the VPlexVirtualVolumeInfo object to check
     * @param thinEnabled
     *            true if the request was to enable thin provisioning
     * @param taskId
     *            the current Workflow task id
     */
    private void checkThinEnabledResult(VPlexVirtualVolumeInfo info, boolean thinEnabled, String taskId) {
        if (thinEnabled && (null != info) && !info.isThinEnabled()) {
            // this is not considered an error situation, but we need to log it for the user's knowledge.
            // stepId is included so that it will appear in the Task's Logs section in the ViPR UI.
            _log.warn(String.format("Virtual Volume %s was created from a thin virtual pool, but it could not be created "
                    + "as a thin virtual volume due to inadequate thin-capability of a child component. See controllersvc "
                    + "and VPLEX API logs for further details. Task ID: %s", info.getName(), taskId));
        }
    }

    /**
     * Returns true if the firmware version of the given VPLEX supports thin virtual volume provisioning.
     *
     * @param vplex
     *            the VPLEX StorageSystem object to check
     * @return true if the firmware version of the given VPLEX supports thin virtual volume provisioning
     */
    private boolean verifyVplexSupportsThinProvisioning(StorageSystem vplex) {
        if (vplex == null) {
            return false;
        }

        if (VPlexApiConstants.FIRMWARE_MIXED_VERSIONS.equalsIgnoreCase(vplex.getFirmwareVersion().trim())) {
            _log.error("VPLEX indicates its that directors have mixed firmware versions, so could not determine thin provisioning support");
            throw VPlexApiException.exceptions.thinProvisioningVerificationFailed(vplex.getLabel());
        }

        int versionValue = VersionChecker.verifyVersionDetails(VPlexApiConstants.MIN_VERSION_THIN_PROVISIONING, vplex.getFirmwareVersion());
        boolean isCompatible = versionValue >= 0;
        _log.info("VPLEX support for thin volumes is " + isCompatible);
        if (!isCompatible) {
            _log.info("minimum VPLEX thin provisioning firmware version is {}, discovered firmeware version for VPLEX {} is {}",
                    VPlexApiConstants.MIN_VERSION_THIN_PROVISIONING, vplex.forDisplay(), vplex.getFirmwareVersion());
        }
        return isCompatible;
    }

    /**
     * Create a new ExportGroup based on the old ExportGroup, then add the volume to the new exportGroup
     *
     * @param oldExportGroup
     *            The old exportGroup that will be based on for the new exportGroup.
     * @param volume
     *            The volume that will be added to the new exportGroup
     * @param lun
     *            The lun number for the volume.
     */
    private void createExportGroup(ExportGroup oldExportGroup, Volume volume, Integer lun) {
        ExportGroup exportGroup = new ExportGroup();
        exportGroup.setLabel(oldExportGroup.getLabel());
        exportGroup.setType(oldExportGroup.getType());
        exportGroup.setId(URIUtil.createId(ExportGroup.class));
        exportGroup.setProject(oldExportGroup.getProject());
        exportGroup.setVirtualArray(volume.getVirtualArray());
        exportGroup.setTenant(oldExportGroup.getTenant());
        exportGroup.setGeneratedName(oldExportGroup.getGeneratedName());
        exportGroup.addVolume(volume.getId(), lun);
        exportGroup.addInitiators(StringSetUtil.stringSetToUriList(oldExportGroup.getInitiators()));
        exportGroup.addHosts(StringSetUtil.stringSetToUriList(oldExportGroup.getHosts()));
        exportGroup.setClusters(oldExportGroup.getClusters());
        List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, oldExportGroup);
        if (!exportMasks.isEmpty()) {
            for (ExportMask exportMask : exportMasks) {
                exportGroup.addExportMask(exportMask.getId());
            }
        }
        exportGroup.setNumPaths(oldExportGroup.getNumPaths());
        exportGroup.setZoneAllInitiators(oldExportGroup.getZoneAllInitiators());
        _dbClient.createObject(exportGroup);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface#addStepsForCreateFullCopy(com.emc.
     * storageos.workflow.Workflow, java.lang.String, java.util.List, java.lang.String)
     */
    @Override
    public String addStepsForCreateFullCopy(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws InternalException {

        List<VolumeDescriptor> blockVolmeDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_VIRT_VOLUME },
                new VolumeDescriptor.Type[] {});

        // If no volumes to create, just return
        if (blockVolmeDescriptors.isEmpty()) {
            return waitFor;
        }

        URI vplexUri = null;

        for (VolumeDescriptor descriptor : blockVolmeDescriptors) {
            Volume volume = _dbClient.queryObject(Volume.class, descriptor.getVolumeURI());
            if (volume != null && !volume.getInactive()) {
                vplexUri = volume.getStorageController();
                break;
            }
        }

        String stepId = workflow.createStepId();
        // Now add the steps to create the block snapshot on the storage system
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, vplexUri);
        Workflow.Method createFullCopyMethod = new Workflow.Method(CREATE_FULL_COPY_METHOD_NAME, vplexUri, volumeDescriptors);
        Workflow.Method nullRollbackMethod = new Workflow.Method(ROLLBACK_METHOD_NULL);

        waitFor = workflow.createStep(CREATE_FULL_COPY_METHOD_NAME, "Create Block Full Copy for VPlex", waitFor, storageSystem.getId(),
                storageSystem.getSystemType(), this.getClass(), createFullCopyMethod, nullRollbackMethod, stepId);
        _log.info(String.format("Added %s step [%s] in workflow", CREATE_FULL_COPY_METHOD_NAME, stepId));

        return waitFor;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface#addStepsForPostCreateReplica(com.emc.
     * storageos.workflow.Workflow, java.lang.String, java.util.List, java.lang.String)
     */
    @Override
    public String addStepsForPostCreateReplica(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws InternalException {
        // nothing to do after create clone
        return waitFor;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface#addStepsForCreateFullCopy(com.emc.
     * storageos.workflow.Workflow, java.lang.String, java.util.List, java.lang.String)
     */
    @Override
    public String addStepsForPreCreateReplica(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws InternalException {
        return waitFor;
    }

    public void setValidator(ValidatorFactory validator) {
        this.validator = validator;
    }

    /**
     * Updates the thinlyProvisioned property on the given VPLEX virtual volume by checking
     * the VPLEX REST API for a change to the thin-enabled property on the virtual-volume.
     *
     * @param client
     *            a reference to the VPlexApiClient
     * @param vplexSystem
     *            the StorageSystem object for the VPLEX
     * @param vplexVolume
     *            the VPLEX virtual Volume object
     */
    private void updateThinProperty(VPlexApiClient client, StorageSystem vplexSystem, Volume vplexVolume) {
        if (vplexVolume != null && verifyVplexSupportsThinProvisioning(vplexSystem)) {
            _log.info("Checking if thinly provisioned property changed after mirror operation...");
            VPlexVirtualVolumeInfo virtualVolumeInfo = client.findVirtualVolume(vplexVolume.getDeviceLabel(), vplexVolume.getNativeId());
            if (virtualVolumeInfo != null) {
                if (VPlexApiConstants.TRUE.equalsIgnoreCase(virtualVolumeInfo.getThinCapable())) {
                    VirtualPool vpool = getDataObject(VirtualPool.class, vplexVolume.getVirtualPool(), _dbClient);
                    if (vpool != null) {
                        boolean doEnableThin = VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(
                                vpool.getSupportedProvisioningType());
                        if (doEnableThin) {
                            // api client call will update thin-enabled on virtualVolumeInfo object, if it succeeds
                            client.setVirtualVolumeThinEnabled(virtualVolumeInfo);
                        }
                    }
                }
                if (virtualVolumeInfo.isThinEnabled() != vplexVolume.getThinlyProvisioned()) {
                    _log.info("Thin provisioned setting changed after mirror operation to " + virtualVolumeInfo.isThinEnabled());
                    vplexVolume.setThinlyProvisioned(virtualVolumeInfo.isThinEnabled());
                    _dbClient.updateObject(vplexVolume);
                }
            }
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
        VPlexApiClient client = getVPlexAPIClient(_vplexApiFactory, vplex, _dbClient);
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
            Workflow workflow = _workflowService.getNewWorkflow(this, "portRebalance", false, stepId);

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
                    addPathsMethod = storageViewAddInitiatorsMethod(vplex, exportGroupURI, exportMaskURI, initiatorsToAdd, targetsToAdd, sharedMask, completer);
                } else if (!targetsToAdd.isEmpty()) {
                    addPathsMethod = storageViewAddStoragePortsMethod(vplex, exportGroupURI, exportMaskURI, targetsToAdd, completer);
                }
                String description = String.format("Adding paths to ExportMask %s Hosts %s", exportMask.getMaskName(), hostNames.toString());
                workflow.createStep("addPaths", description, null, vplex, vplexSystem.getSystemType(), this.getClass(), addPathsMethod, null, false, addInitiatorStepId);
                ExportMaskAddPathsCompleter workflowCompleter = new ExportMaskAddPathsCompleter(exportGroupURI, exportMaskURI, stepId);
                workflow.executePlan(workflowCompleter, description + " completed successfully");
                return;
            }
            
        } else {
            // Processing the paths to be removed only, Paths not in the removedPaths map will be retained.
            // Note that we only remove ports (targets), never initiators.
            Workflow workflow = _workflowService.getNewWorkflow(this, "portRebalance", false, stepId);
            
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
                removePathsMethod = storageViewRemoveStoragePortsMethod(vplex, exportGroupURI, exportMaskURI, portsToBeRemoved, null);
                String description = String.format("Removing paths to ExportMask %s Hosts %s", exportMask.getMaskName(), hostNames.toString());
                workflow.createStep("removePaths", description, null, vplex, vplexSystem.getSystemType(), this.getClass(), removePathsMethod,
                        null, false, removeInitiatorStepId);
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
     * Convenience method for ensuring that the step is terminated properly, regardless of
     * completer state.
     * 
     * @param completer
     *            completer, can be null
     * @param stepId
     *            step ID
     * @param sc
     *            service coded error
     */
    private void failStep(TaskCompleter completer, String stepId, ServiceCoded sc) {
        if (completer != null) {
            completer.error(_dbClient, sc);
        } else {
            WorkflowStepCompleter.stepFailed(stepId, sc);
        }
    }
}
