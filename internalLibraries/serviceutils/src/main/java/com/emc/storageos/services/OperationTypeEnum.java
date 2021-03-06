/*
 * Copyright 2012-2014 EMC Corporation
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.emc.storageos.services;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;

public enum OperationTypeEnum {

    /* EventName failEventName event summary */
    CREATE_TENANT("TenantCreated", "", "Subtenant is created."),
    UPDATE_TENANT("TenantUpdated", "", "Tenant is updated."),
    DELETE_TENANT("TenantDeleted", "", "Subtenant is deleted."),
    UPDATE_TENANT_STS("UPDATE TENANT STS", "", "Tenant's STS operation is updated."),
    REASSIGN_TENANT_ROLES("TenantUpdated", "", "Tenant's roles is changed."),
    MODIFY_TENANT_ROLES("TenantUpdated", "", "Tenant's roles are updated."),
    CREATE_TENANT_TAG("TAG TENANT", "", "Tag operation performed on a tenant."),
    SET_TENANT_NAMESPACE("Set Tenant NS", "", "Tenant namespace info is updated."),
    GET_TENANT_NAMESPACE("Get Tenant NS", "", "Tenant namespace info is retrieved."),
    UNSET_TENANT_NAMESPACE("Unset Tenant NS", "", "Tenant namespace info is removed."),

    REASSIGN_ZONE_ROLES("REPLACE ZONE ROLES", "", "Zone roles are replaced."),
    MODIFY_ZONE_ROLES("MODIFY ZONE ROLES", "", "Zone roles are modified."),
    CREATE_PROJECT("ProjectCreated", "", "Project is created."),
    UPDATE_PROJECT("ProjectUpdated", "", "Project is updated."),
    DELETE_PROJECT("ProjectDeleted", "", "Project is deleted."),
    MODIFY_PROJECT_ACL("ProjectUpdated", "", "Project ACLs are updated."),
    REASSIGN_PROJECT_ACL("ProjectUpdated", "", "Project ACLs are changed."),
    CREATE_VPOOL("VpoolCreated", "", "Virtual pool is created."),
    UPDATE_VPOOL("VpoolUpdated", "", "Virtual pool is updated."),
    DELETE_VPOOL("VpoolDeleted", "", "Virtual pool is deleted."),
    REASSIGN_VPOOL_ACL("VpoolUpdated", "", "Virtual pool ACLs are reassigned."),
    MODIFY_VPOOL_ACL("VpoolUpdated", "", "Virtual pool ACLs are modified."),
    ASSIGN_VPOOL_TAG("ASSIGN VPOOL TAG", "", "Tag operation performed on a virtual pool."),

    CREATE_QOS("QosCreated", "", "Quality of service profile is created."),
    UPDATE_QOS("QosUpdated", "", "Quality of service profile is updated."),
    DELETE_QOS("QosDeleted", "", "Quality of service profile is deleted."),

    CREATE_BLOCK_VOLUME("VolumeCreated", "VolumeCreateFailed", "Volume is created."),
    DELETE_BLOCK_VOLUME("VolumeDeleted", "VolumeDeleteFailed", "Volume is deleted."),
    EXPAND_BLOCK_VOLUME("VolumeExpanded", "VolumeExpandFailed", "Volume is expanded."),

    CREATE_VOLUME_SNAPSHOT("VolumeSnapshotCreated", "VolumeSnapshotCreateFailed", "Volume snapshot is created."),
    EXPAND_VOLUME_SNAPSHOT("VolumeSnapshotExpanded", "VolumeSnapshotExpandFailed", "Volume snapshot expanded."),
    ASSIGN_VOLUME_TAG("ASSIGN VOLUME TAG", "", "Tag operation performed on a volume."),
    DELETE_VOLUME_SNAPSHOT("VolumeSnapshotDeleted", "VolumeSnapshotDeleteFailed", "Volume snapshot is deleted."),
    RESTORE_VOLUME_SNAPSHOT("VolumeSnapshotRestored", "VolumeSnapshotRestoreFailed", "Volume snapshot is restored."),
    RESYNCHRONIZE_VOLUME_SNAPSHOT("VolumeSnapshotResynchronized", "VolumeSnapshotResynchronizeFailed", "Volume snapshot is resynchronized."),
    ACTIVATE_VOLUME_SNAPSHOT("VolumeSnapshotActivated", "VolumeSnapshotActivateFailed", "Volume snapshot is activated."),
    DEACTIVATE_VOLUME_SNAPSHOT("VolumeSnapshotDeactivated", "VolumeSnapshotDeactivateFailed", "Volume snapshot is deactivated."),
    ESTABLISH_VOLUME_SNAPSHOT("VolumeSnapshotGroupEstablished", "VolumeSnapshotGroupEstablishFailed", "Volume snapshot group establish performed."),
    CHANGE_VOLUME_VPOOL("VolumeVPoolChanged", "VolumeVPoolChangeFailed", "Volume's virtual pool has changed."),
    CHANGE_VOLUME_AUTO_TIERING_POLICY("VolumeAutoTieringPolicyChanged", "VolumeAutoTieringPolicyChangeFailed",
            "Volume auto-tiering policy is updated."),
    ASSIGN_VOLUME_SNAPSHOT_TAG("TAG VOLUME SNAPSHOT", "", "Tag operation performed on a volume snapshot."),

    CREATE_SRDF_LINK("SRDFLinkCreated", "SRDFLinkCreateFailed", "SRDF link is created."),
    SUSPEND_SRDF_LINK("SRDFLinkSuspended", "SRDFLinkSuspendFailed", "SRDF link is suspended."),
    DETACH_SRDF_LINK("SRDFLinkDetached", "SRDFLinkDetachFailed", "SRDF link is detached."),
    PAUSE_SRDF_LINK("SRDFLinkPaused", "SRDFLinkPauseFailed", "SRDF link is paused."),
    RESUME_SRDF_LINK("SRDFLinkResumed", "SRDFLinkResumeFailed", "SRDF link is resumed."),
    FAILOVER_SRDF_LINK("SRDFLinkFailedOver", "SRDFLinkFailOverFailed", "SRDF link is failed over."),
    SYNC_SRDF_LINK("SRDFLinkSynced", "SRDFLinkSyncFailed", "SRDF link is synchronized."),
    SWAP_SRDF_VOLUME("SRDFVolumeSwapped", "SRDFVolumeSwapFailed", "SRDF volume is swapped."),
    STOP_SRDF_LINK("SRDFLinkStopped", "SRDFLinkStopFailed", "SRDF link is stopped."),
    CHANGE_SRDF_COPYMODE("SRDFCopyModeChanged", "SRDFCopyModeChangeFailed", "SRDF copy mode is changed."),

    START_RP_LINK("RPLinkStarted", "RPLinkStartFailed", "RP link is created."),
    STOP_RP_LINK("RPLinkStopped", "RPLinkStopFailed", "RP link is stopped."),
    PAUSE_RP_LINK("RPLinkPaused", "RPLinkPauseFailed", "RP link is paused."),
    RESUME_RP_LINK("RPLinkResumed", "RPLinkResumeFailed", "RP link is resumed."),
    SWAP_RP_VOLUME("RPVolumeSwapped", "RPVolumeSwapFailed", "RP volume is swapped."),
    SYNC_RP_LINK("RPLinkSynced", "RPLinkSyncFailed", "RP link is synchronized."),
    FAILOVER_RP_LINK("RPLinkFailedOver", "RPLinkFailOverFailed", "RP link is failed over."),
    FAILOVER_CANCEL_RP_LINK("RPLinkFailedOverCanceled", "RPLinkFailOverCancelFailed", "RP fail over link is cancelled."),
    FAILOVER_TEST_RP_LINK("RPLinkTestFailedOver", "RPLinkTestFailOverFailed", "RP link failed over in test mode."),
    FAILOVER_TEST_CANCEL_RP_LINK("RPLinkTestCancelFailedOver", "RPLinkTestCancelFailOverFailed", "RP link in failed over test mode is cancelled."),
    CHANGE_RP_IMAGE_ACCESS_MODE("RPImageAccessModeChanged", "RPImageAccessModeChangeFailed", "RP image access mode change."),

    CREATE_VOLUME_MIRROR("VolumeMirrorCreated", "VolumeMirrorCreateFailed", "Volume mirror is created."),
    DEACTIVATE_VOLUME_MIRROR("VolumeMirrorDeactivated", "VolumeMirrorDeactivateFailed", "Volume mirror is deactivated."),
    DELETE_VOLUME_MIRROR("VolumeMirrorDeleted", "VolumeMirrorDeleteFailed", "Volume mirror is deleted."),
    DETACH_VOLUME_MIRROR("VolumeMirrorDetached", "VolumeMirrorDetachFailed", "Volume mirror is detached."),
    FRACTURE_VOLUME_MIRROR("VolumeMirrorFractured", "VolumeMirrorFractureFailed", "Volume mirror is fractured."),
    RESUME_VOLUME_MIRROR("VolumeMirrorResumed", "VolumeMirrorResumeFailed", "Volume mirror is resumed."),
    ESTABLISH_VOLUME_MIRROR("VolumeMirrorGroupEstablished", "VolumeMirrorGroupEstablishFailed", "Volume mirror group establish performed."),
    CREATE_VOLUME_FULL_COPY("VolumeFullCopyCreated", "VolumeFullCopyCreateFailed", "Volume full copy is created."),
    DETACH_VOLUME_FULL_COPY("VolumeFullCopyDetached", "VolumeFullCopyDetachFailed", "Volume full copy is detached."),
    ACTIVATE_VOLUME_FULL_COPY("VolumeFullCopyActivated", "VolumeFullCopyActivateFailed", "Volume full copy is activated."),
    RESTORE_VOLUME_FULL_COPY("VolumeFullCopyRestored", "VolumeFullCopyRestoredFailed", "Volume full copy is restored."),
    RESYNCHRONIZE_VOLUME_FULL_COPY("VolumeFullCopyResynchronized", "VolumeFullCopyResynchronizeFailed", "Volume full copy is resynchronized."),
    ESTABLISH_VOLUME_FULL_COPY("VolumeFullCopyGroupEstablished", "VolumeFullCopyGroupEstablishFailed", "Volume full copy group establish performed."),

    CREATE_CONSISTENCY_GROUP_SNAPSHOT("ConsistencyGroupSnapshotCreated", "ConsistencyGroupSnapshotCreateFailed",
            "Consistency group snapshot is created."),
    DELETE_CONSISTENCY_GROUP_SNAPSHOT("ConsistencyGroupSnapshotDeleted", "ConsistencyGroupSnapshotDeleteFailed",
            "Consistency group snapshot is deleted."),
    RESTORE_CONSISTENCY_GROUP_SNAPSHOT("ConsistencyGroupSnapshotRestored", "ConsistencyGroupSnapshotRestoreFailed",
            "Consistency group snapshot is restored."),
    DEACTIVATE_CONSISTENCY_GROUP_SNAPSHOT("ConsistencyGroupSnapshotDeactivated", "ConsistencyGroupSnapshotDeactivateFailed",
            "Consistency group snapshot is deactivated."),
    ACTIVATE_CONSISTENCY_GROUP_SNAPSHOT("ConsistencyGroupSnapshotActivated", "ConsistencyGroupSnapshotActivateFailed",
            "Consistency group snapshot is activated."),

    CREATE_EXPORT_GROUP("ExportCreated", "ExportCreateFailed", "Export is created."),
    UPDATE_EXPORT_GROUP("ExportUpdated", "ExportUpdateFailed", "Export is updated."),
    DELETE_EXPORT_GROUP("ExportDeleted", "ExportDeleteFailed", "Export is deleted."),
    ADD_EXPORT_INITIATOR("ExportInitiatorAdded", "ExportInitiatorAddFailed", "Export initiator is added."),
    ADD_EXPORT_VOLUME("ExportVolumeAdded", "ExportVolumeAddFailed", "Export volume is added."),
    DELETE_EXPORT_INITIATOR("ExportInitiatorRemoved", "ExportInitiatorRemoveFailed", "Export initiator is removed."),
    DELETE_EXPORT_VOLUME("ExportVolumeRemoved", "ExportVolumeRemoveFailed", "Export volume is removed."),

    CREATE_FILE_SYSTEM("FileSystemCreated", "FileSystemCreateFailed", "File system is created."),
    INGEST_FILE_SYSTEM("FileSystemIngested", "FileSystemIngestFailed", "File system is ingested."),
    UPDATE_FILE_SYSTEM("FileSystemUpdated", "FileSystemUpdateFailed", "File system is updated."),
    DELETE_FILE_SYSTEM("FileSystemDeleted", "FileSystemDeleteFailed", "File system is deleted."),
    EXPORT_FILE_SYSTEM("FileSystemExported", "FileSystemExportFailed", "File system is exported."),
    UPDATE_EXPORT_RULES_FILE_SYSTEM("FileSystemExportRulesUpdated", "FileSystemExportRulesUpdateFailed", "File system export rules is updated."),
    UNEXPORT_FILE_SYSTEM("FileSystemUnexported", "FileSystemUnexportFailed", "File system is unexported."),
    EXPAND_FILE_SYSTEM("FileSystemExpanded", "FileSystemExpandFailed", "File system is expanded."),
    REDUCE_FILE_SYSTEM("FileSystemReduced", "FileSystemReduceFailed", "File system is reduced."),
    RELEASE_FILE_SYSTEM("FileSystemReleased", "", "File system is released."),
    UNDO_RELEASE_FILE_SYSTEM("FileSystemReleaseUndone", "", "File system release undone."),
    CHANGE_FILE_SYSTEM_VPOOL("ChangeFileSystemVpool", "ChangeFileSystemVpoolFailed", "File system's virtual pool is changed."),
    CREATE_MIRROR_FILE_SYSTEM("CreateMirrorFileSystem", "CreateMirrorFileSystemFailed", "File system mirror is created."),
    DELETE_MIRROR_FILE_SYSTEM("DeleteMirrorFileSystem", "DeleteMirrorFileSystemFailed", "File system mirror is deleted."),

    CREATE_FILE_SYSTEM_SHARE("FileSystemShared", "FileSystemShareFailed", "File system is shared."),
    ASSIGN_FILE_SYSTEM_TAG("TAG A FILESYSTEM", "", "Tag operation performed on a file system."),
    ASSIGN_FILE_SYSTEM_SNAPSHOT_SCHEDULE("FileSystemSnapshotScheduleAssigned", "FileSystemSnapshotScheduledAssignedFailed", "File system snapshot schedule is assigned."),
    ASSIGN_FILE_POLICY("AssignFileSystemPolicy", "FileSystemPolicyAssignFailed", "File system policy is assigned."),
    UNASSIGN_FILE_SYSTEM_SNAPSHOT_SCHEDULE("FileSystemSnapshotScheduleUnassigned", "FileSystemSnapshotScheduledUnassignedFailed", "File system snapshot schedule is unassigned."),
    DELETE_FILE_SYSTEM_SHARE("FileSystemShareDeleted", "FileSystemShareDeleteFailed", "File system share deleted."),
    GET_FILE_SYSTEM_SNAPSHOT_BY_SCHEDULE("FileSystemScheduleSnapshot", "FileSystemScheduleSnapshotFailed",
            "File system by snapshot schedule is retrieved."),
    UPDATE_STORAGE_SYSTEM_POLICY_BY_POLICY_RESOURCE("StorageSystemPolicyForPolicyResource", "StorageSystemPolicyForPolicyResourceFailed",
            "Storage System Policy For Policy Resource is retrieved."),

    CREATE_FILE_SYSTEM_SNAPSHOT("FileSystemSnapshotCreated", "FileSystemSnapshotCreateFailed", "File system snapshot is created."),
    DELETE_FILE_SNAPSHOT("FileSystemSnapshotDeleted", "FileSystemSnapshotDeleteFailed", "File system snapshot is deleted."),
    EXPORT_FILE_SNAPSHOT("FileSystemSnapshotExported", "FileSystemSnapshotExportFailed", "File system snapshot is exported."),
    UPDATE_EXPORT_RULES_FILE_SNAPSHOT("FileSystemSnapshotExportRulesUpdated", "FileSystemSnapshotExportRulesUpdateFailed",
            "File system snapshot export rules are updated."),
    UNEXPORT_FILE_SNAPSHOT("FileSystemSnapshotUnexported", "FileSystemSnapshotUnexportFailed", "File system snapshot is unexported."),

    CREATE_FILE_SYSTEM_QUOTA_DIR("FileSystemQuotaDirCreated", "FileSystemQuotaDirCreateFailed", "File system quota directory is created."),
    DELETE_FILE_SYSTEM_QUOTA_DIR("FileSystemQuotaDirDeleted", "FileSystemQuotaDirDeleteFailed", "File system quota directory is deleted."),
    UPDATE_FILE_SYSTEM_QUOTA_DIR("FileSystemQuotaDirUpdated", "FileSystemQuotaDirUpdateFailed", "File system quota directory is updated."),

    CREATE_FILE_SNAPSHOT_SHARE("FileSystemSnapshotShared", "FileSystemSnapshotShareFailed", "File system snapshot is shared."),
    ASSIGN_FILE_SNAPSHOT_TAG("TAG A FILESYSTEM SNAPSHOT", "", "Tag operation performed on file system snapshot."),
    DELETE_FILE_SNAPSHOT_SHARE("FileSystemSnapshotShareDeleted", "FileSystemSnapshotShareDeleteFailed",
            "File system snapshot share is deleted."),
    RESTORE_FILE_SNAPSHOT("FileSystemRestored", "FileSystemRestoreFailed", "File system is restored."),

    CREATE_FILE_MIRROR("FileSystemMirrorCreated", "FileSystemMirrorCreateFailed", "File system mirror is created."),
    DELETE_FILE_MIRROR("FileSystemMirrorDeleted", "FileSystemMirrorDeleteFailed", "File system mirror is deleted."),

    SUSPEND_FILE_MIRROR("FileSystemMirrorSuspended", "FileSysteMirrorSuspendFailed", "File system mirror is suspended."),
    DETACH_FILE_MIRROR("FileSystemMirrorDetach", "FileSystemMirrorDetachFailed", "File system mirror is detached."),
    PAUSE_FILE_MIRROR("FileSystemMirrorPaused", "FileSystemMirrorPauseFailed", "File system mirror link is paused."),
    RESUME_FILE_MIRROR("FileSystemMirrorResumed", "FileSystemMirrorResumeFailed", "File system mirror is resumed."),
    FAILOVER_FILE_MIRROR("FileSystemMirrorFailover", "FileSystemMirrorFailOverFailed", "File system mirror is failed over."),
    FAILBACK_FILE_MIRROR("FileSystemMirrorFailback", "FileSystemMirrorFailbackFailed", "File system mirror is failed back."),
    STOP_FILE_MIRROR("FileSystemMirrorStop", "FileSystemMirrorStopFailed", "File system mirror is stopped."),
    START_FILE_MIRROR("FileSystemMirrorStart", "FileSystemMirrorStartFailed", "File system mirror is started."),
    REFRESH_FILE_MIRROR("FileSystemMirrorRefresh", "FileSystemMirrorRefreshFailed", "File system mirror is refreshed."),
    CANCEL_FILE_MIRROR("FileSystemMirrorCancel", "FileSystemMirrorCancelFailed", "File system mirror is cancelled."),
    RESYNC_FILE_MIRROR("FileSystemMirrorResync", "FileSystemMirrorResyncFailed", "File system mirror is resynchronized."),
    MODIFY_FILE_MIRROR_RPO("UPDATE FILE SHARE REPLICATION RPO", "FileSystemMirrorModifyRPOFailed",
            "File system replication RPO is updated."),

    CREATE_BUCKET("BucketCreated", "BucketCreateFailed", "Bucket is created."),
    DELETE_BUCKET("BucketDeleted", "BucketDeleteFailed", "Bucket is deleted."),
    UPDATE_BUCKET("BucketUpdated", "BucketUpdateFailed", "Bucket is updated."),

    STORAGE_PORT_REGISTER("StoragePortRegistered", "", "Storage port is registered."),
    STORAGE_PORT_DEREGISTER("StoragePortUnregistered", "", "Storage port is unregistered."),
    STORAGE_PORT_UPDATE("StoragePortUpdated", "", "Storage port is updated."),

    STORAGE_POOL_REGISTER("StoragePoolRegistered", "", "Storage pool is registered."),
    STORAGE_POOL_DEREGISTER("StoragePoolUnregistered", "", "Storage pool is unregistered."),
    STORAGE_POOL_UPDATE("StoragePoolUpdated", "", "Storage pool is updated."),

    OPERATE_BLOCK_VOLUME("VolumeEventOkStatus", "VolumeEventNotOkStatus", "Volume operation performed."),
    OPERATE_FILE_SYSTEM("FileSystemEventOkStatus", "FileSystemEventNotOkStatus", "File system operation performed."),

    STORAGE_PROVIDER_DOWN("STORAGE PROVIDER DOWN", "", "Storage provider is down."),
    STORAGE_PROVIDER_UP("STORAGE PROVIDER UP", "", "Storage provider is up."),

    CREATE_KEYPOOL("CREATE KEYPOOL", "", "Keypool is created."),
    DELETE_KEYPOOL("DELETE KEYPOOL", "", "Keypool is deleted."),
    UPDATE_KEYPOOL_ACCESSMODE("UPDATE KEYPOOL", "", "Keypool access mode is updated."),
    CREATE_NAMESPACE("CREATE NAMESPACE", "", "Namespace is created."),
    UPDATE_NAMESPACE("UPDATE NAMESPACE", "", "Namespace is updated."),
    DELETE_NAMESPACE("DELETE NAMESPACE", "", "Namespace is deleted."),
    CREATE_VARRAY("CREATE VARRAY", "", "Virtual array is created."),
    DELETE_VARRAY("DELETE VARRAY", "", "Virtual array is deleted."),
    UPDATE_VARRAY("UPDATE VARRAY", "", "Virtual array is updated."),
    MODIFY_VARRAY_ACL("UPDATE VARRAY ACL", "", "Virtual array ACLs are updated."),
    REASSIGN_VARRAY_ACL("REPLACE VARRAY ACL", "", "Virtual array ACLs are overwritten."),
    SET_VARRAY_PROTECTIONTYPE("SET VARRAY PROTECTION TYPE", "", "Virtual array protection type is assigned."),
    GET_VARRAY_PROTECTIONTYPE("GET VARRAY PROTECTION TYPE", "", "Virtual array protection type is retrieved."),
    UNSET_VARRAY_PROTECTIONTYPE("UNSET VARRAY PROTECTION TYPE", "", "Virtual array protection type is unassigned."),
    SET_VARRAY_REGISTERED("SET VARRAY REGISTERED STATUS", "", "Virtual array registration status is assigned."),
    GET_VARRAY_REGISTERED("GET VARRAY REGISTERED STATUS", "", "Virtual array registration status is retrieved."),

    CREATE_DATA_STORE("CREATE DATASTORE", "", "Datastore is created."),
    DELETE_DATA_STORE("DELETE DATASTORE", "", "Datastore is deleted."),
    CREATE_SECRET_KEY("CREATE SECRET KEY", "", "Secret key for a user is created."),
    DELETE_SECRET_KEY("DELETE SECRET KEY", "", "Secret key for a user is deleted."),
    REGISTER_SMISPROVIDER("REGISTER SMIS PROVIDER", "", "SMI-S provider is registered."),
    REGISTER_STORAGEPROVIDER("REGISTER STORAGE PROVIDER", "", "Storage provider is registered."),
    DELETE_SMISPROVIDER("DELETE SMIS PROVIDER", "", "SMI-S provider is deleted."),
    DELETE_STORAGEPROVIDER("DELETE STORAGE PROVIDER", "", "Storage provider is deleted."),
    UPDATE_SMISPROVIDER("UPDATE SMIS PROVIDER", "", "SMI-S provider is updated."),
    UPDATE_STORAGEPROVIDER("UPDATE STORAGE PROVIDER", "", "Storage provider is updated."),
    SCAN_STORAGEPROVIDER("SCAN STORAGE PROVIDER", "", "Storage provider is scanned."),
    REGISTER_STORAGE_SYSTEM("REGISTER SMIS SYSTEM", "", "SMI-S system is registered."),
    UPDATE_STORAGE_POOL("UPDATE STORAGEPOOL", "", "Storage pool is updated."),
    DELETE_STORAGE_POOL("DELETE STORAGEPOOL", "", "Storage pool is deleted."),
    DEREGISTER_STORAGE_POOL("UNREGISTER STORAGEPOOL", "", "Storage pool is unregistered."),
    ASSIGN_STORAGE_POOL_TAG("TAG STORAGEPOOL", "", "Tag operation performed on a storage pool."),
    UPDATE_STORAGE_PORT("UPDATE STORAGEPORT", "", "Storage port is updated."),
    DELETE_STORAGE_PORT("DELETE STORAGEPORT", "", "Storage port is deleted."),
    DEREGISTER_STORAGE_PORT("UNREGISTER STORAGEPORT", "", "Storage port is unregistered."),
    ASSIGN_STORAGE_PORT_TAG("TAG STORAGEPORT", "", "Tag operation performed on a storage port."),
    CREATE_STORAGE_SYSTEM("CREATE STORAGESYSTEM", "", "Storage system is created."),
    UPDATE_STORAGE_SYSTEM("UPDATE STORAGESYSTEM", "", "Storage system is updated."),
    DISCOVER_ALL_STORAGE_SYSTEM("DISCOVER ALL STORAGESYSTEMS", "", "Storage systems are discovered."),
    DISCOVER_STORAGE_SYSTEM("DISCOVER STORAGESYSTEM", "", "Storage system is discovered."),
    DEREGISTER_STORAGE_SYSTEM("UNREGISTER STORAGESYSTEM", "", "Storage system is unregistered."),
    CREATE_STORAGE_POOL("CREATE STORAGEPOOL", "", "Storage pool is created."),
    CREATE_STORAGE_PORT("CREATE STORAGEPORT", "", "Storage port is created."),
    ASSIGN_STORAGE_SYSTEM_TAG("TAG STORAGESYSTEM", "", "Tag operation performed on a storage system."),
    REGISTER_STORAGE_POOL("REGISTER STORAGEPOOL", "", "Storage pool is registered."),
    REGISTER_STORAGE_PORT("REGISTER STORAGEPORT", "", "Storage port is registered."),
    CREATE_NETWORK("NetworkCreated", "", "Network is created."),
    DELETE_NETWORK("NetworkDeleted", "", "Network is deleted."),
    UPDATE_NETWORK("NetworkUpdated", "", "Network is updated."),
    DEREGISTER_NETWORK("Unregister Network", "", "Network is unregistered."),
    REGISTER_NETWORK("Register Network", "", "Network is registered."),
    ASSIGN_NETWORK_TAG("TAG NETWORK", "", "Tag operation performed on a network."),
    CREATE_ATMOS_SUBTENANT("CREATE ATMOS SUBTENANT", "", "Atmos subtenant is created."),
    DELETE_ATMOS_SUBTENANT("DELETE ATMOS SUBTENANT", "", "Atmos subtenant is deleted."),
    CREATE_S3_BUCKET("CREATE S3 BUCKET", "", "S3 bucket is created."),
    SET_S3_BUCKET_ACL("SET ACL ON S3 BUCKET", "", "S3 bucket ACLs are assigned."),
    SET_S3_BUCKET_VERSION("VERSION S3 BUCKET", "", "S3 bucket version is assigned."),
    CREATE_SWIFT_CONTAINER("CREATE SWIFT CONTAINER", "", "Swift container is created."),
    DELETE_SWIFT_CONTAINER("DELETE SWIFT CONTAINER", "", "Swift container is deleted."),
    CREATE_INITIATOR("CREATE INITIATOR", "", "Initiator is created."),
    DELETE_INITIATOR("DELETE INITIATOR", "", "Initiator is deleted."),
    REGISTER_INITIATOR("REGISTER INITIATOR", "", "Initiator is registered."),
    DEREGISTER_INITIATOR("DEREGISTER INITIATOR", "", "Initiator is deregistered."),
    CREATE_NETWORK_SYSTEM("CREATE NETWORKSYSTEM", "", "Network system is created."),
    UPDATE_NETWORK_SYSTEM("UPDATE NETWORKSYSTEM", "", "Network system is updated."),
    DELETE_NETWORK_SYSTEM("DELETE NETWORKSYSTEM", "", "Network system is deleted."),
    DISCOVER_NETWORK_SYSTEM("DISCOVER NETWORKSYSTEM", "", "Network system is discovered."),
    DEREGISTER_NETWORK_SYSTEM("UNREGISTER NETWORKSYSTEM", "", "Network system is deregistered."),
    REGISTER_NETWORK_SYSTEM("REGISTER STORAGESYSTEM", "", "Storage system is registered."),
    ADD_SAN_ZONE("ADD SAN ZONE", "", "SAN zone is added."),
    REMOVE_SAN_ZONE("REMOVE SAN ZONE", "", "SAN zone(s) are removed."),
    UPDATE_SAN_ZONE("UPDATE SAN ZONE", "", "SAN zone(s) are updated."),
    ACTIVATE_SAN_ZONE("ACTIVATE SAN ZONE", "", "SAN zone(s) are activated."),
    PERFORM_PROTECTION_OPERATION("PERFORM PROTECTION OPERATION", "", "Protection operation performed."),
    DISCOVER_PROTECTION_SET("DISCOVER_PROTECTION_SET", "", "Protection set is discovered."),
    PERFORM_PROTECTION_ACTION("PERFORM PROTECTION ACTION", "", "Protection action performed."),
    CREATE_AUTHPROVIDER("CREATE AUTH PROVIDER", "", "Authentication provider is created."),
    UPDATE_AUTHPROVIDER("UPDATE AUTH PROVIDER", "", "Authentication provider is updated."),
    UPDATE_AUTHPROVIDER_GROUP_ATTR("UPDATE AUTH PROVIDER GROUP ATTR", "",
            "Authentication provider group attribute is updated. This may affect existing tenants, project ACLs, and role assignments."),
    DELETE_AUTHPROVIDER("DELETE AUTH PROVIDER", "", "Authentication provider is deleted."),
    CREATE_PROTECTION_SYSTEM("CREATE PROTECTION SYSTEM", "", "Protection system is created."),
    UPDATE_PROTECTION_SYSTEM("UPDATE PROTECTION SYSTEM", "", "Protection system is updated."),
    DELETE_PROTECTION_SYSTEM("DELETE PROTECTION SYSTEM", "", "Protection system is deleted."),

    CREATE_APPROVAL("CREATE APPROVAL", "", "Approval is created."),
    UPDATE_APPROVAL("UPDATE APPROVAL", "", "Approval is updated."),
    DELETE_APPROVAL("DELETE APPROVAL", "", "Approval is deleted."),

    CREATE_CATALOG_CATEGORY("CREATE CATALOG CATEGORY", "", "Catalog category is created."),
    UPDATE_CATALOG_CATEGORY("UPDATE CATALOG CATEGORY", "", "Catalog category is updated."),
    DELETE_CATALOG_CATEGORY("DELETE CATALOG CATEGORY", "", "Catalog category is deleted."),
    MODIFY_CATALOG_CATEGORY_ACL("MODIFY CATALOG CATEGORY ACL", "", "Catalog category ACLs are updated."),

    CREATE_CATALOG_IMAGE("CREATE CATALOG IMAGE", "", "Catalog image is created."),
    UPDATE_CATALOG_IMAGE("UPDATE CATALOG IMAGE", "", "Catalog image is updated."),
    DELETE_CATALOG_IMAGE("DELETE CATALOG IMAGE", "", "Catalog image is deleted."),

    CREATE_CATALOG_SERVICE("CREATE CATALOG SERVICE", "", "Catalog service is created."),
    UPDATE_CATALOG_SERVICE("UPDATE CATALOG SERVICE", "", "Catalog service is updated."),
    DELETE_CATALOG_SERVICE("DELETE CATALOG SERVICE", "", "Catalog service is deleted."),
    MODIFY_CATALOG_SERVICE_ACL("MODIFY CATALOG SERVICE ACL", "", "Catalog service ACLs are updated."),

    CREATE_EXECUTION_WINDOW("CREATE EXECUTION WINDOW", "", "Execution window is created."),
    UPDATE_EXECUTION_WINDOW("UPDATE EXECUTION WINDOW", "", "Execution window is updated."),
    DELETE_EXECUTION_WINDOW("DELETE EXECUTION WINDOW", "", "Execution window is deleted."),

    UPDATE_CATALOG_PREFERENCES("UPDATE CATALOG PREFERENCES", "", "Catalog preferences are updated."),
    UPDATE_USER_PREFERENCES("UPDATE USER PREFERENCES", "", "User preferences are updated."),

    CREATE_SCHEDULED_EVENT("CREATE SCHEDULED_EVENT", "", "Scheduled event is created."),
    UPDATE_SCHEDULED_EVENT("UPDATE SCHEDULED_EVENT", "", "Scheduled event is updated."),
    DELETE_SCHEDULED_EVENT("DELETE SCHEDULED_EVENT", "", "Scheduled event is deleted."),

    CREATE_ORDER("CREATE ORDER", "", "Order is created."),
    UPDATE_ORDER("UPDATE ORDER", "", "Order is updated."),
    DELETE_ORDER("DELETE ORDER", "", "Order is deleted."),
    DOWNLOAD_ORDER("DOWNLOAD ORDER", "", "Order downloaded."),

    CREATE_HOST("CREATE HOST", "", "Compute host is created."),
    UPDATE_HOST("UPDATE HOST", "", "Compute host is updated."),
    DELETE_HOST("DELETE HOST", "", "Compute host is deleted."),
    DETACH_HOST_STORAGE("DELETE HOST STORAGE", "", "Compute host storage is detached."),
    UPDATE_HOST_BOOT_VOLUME("UPDATE HOST BOOT VOLUME", "", "Host boot volume is updated."),

    CREATE_CLUSTER("CREATE CLUSTER", "", "Cluster is created."),
    UPDATE_CLUSTER("UPDATE CLUSTER", "", "Cluster is updated."),
    DELETE_CLUSTER("DELETE CLUSTER", "", "Cluster is deleted."),
    DETACH_CLUSTER_STORAGE("DELETE CLUSTER STORAGE", "", "Cluster is detached."),

    CREATE_VCENTER("CREATE VCENTER", "", "Vcenter is created."),
    UPDATE_VCENTER("UPDATE VCENTER", "", "Vcenter is updated."),
    DELETE_VCENTER("DELETE VCENTER", "", "Vcenter is deleted."),
    DETACH_VCENTER_STORAGE("DETACH VCENTER STORAGE", "", "Vcenter storage is detached."),

    CREATE_VCENTER_DATACENTER("CREATE VCENTER DATACENTER", "", "Vcenter data center is created."),
    UPDATE_VCENTER_DATACENTER("UPDATE VCENTER DATACENTER", "", "Vcenter data center is updated."),
    DELETE_VCENTER_DATACENTER("DELETE VCENTER DATACENTER", "", "Vcenter data center is deleted."),
    DETACH_VCENTER_DATACENTER_STORAGE("DETACH VCENTER DATACENTER STORAGE", "", "Vcenter data center storage is detached."),

    CREATE_HOST_INITIATOR("CREATE HOST INITIATOR", "", "Host initiator is created."),
    UPDATE_HOST_INITIATOR("UPDATE HOST INITIATOR", "", "Host initiator is updated."),
    DELETE_HOST_INITIATOR("DELETE HOST INITIATOR", "", "Host initiator is deleted."),

    CREATE_HOST_IPINTERFACE("CREATE HOST IPINTERFACE", "", "Host IP interface is created."),
    UPDATE_HOST_IPINTERFACE("UPDATE HOST IPINTERFACE", "", "Host IP interface is updated."),
    DELETE_HOST_IPINTERFACE("DELETE HOST IPINTERFACE", "", "Host IP interface is deleted."),
    DEREGISTER_HOST_IPINTERFACE("UNREGISTER HOST IPINTERFACE", "", "Host IP interface is deregistered."),
    REGISTER_HOST_IPINTERFACE("REGISTER HOST IPINTERFACE", "", "Host IP interface is registered."),

    CREATE_COMPUTE_SYSTEM("CREATE COMPUTE SYSTEM", "", "Compute system is created."),
    UPDATE_COMPUTE_SYSTEM("UPDATE COMPUTE SYSTEM", "", "Compute system is updated."),
    REGISTER_COMPUTE_SYSTEM("REGISTER COMPUTE SYSTEM", "", "Compute system is registered."),
    DEREGISTER_COMPUTE_SYSTEM("UNREGISTER COMPUTE SYSTEM", "", "Compute system is deregistered."),
    DELETE_COMPUTE_SYSTEM("DELETE COMPUTE SYSTEM", "", "Compute system is deleted."),

    REGISTER_COMPUTE_ELEMENT("REGISTER COMPUTE ELEMENT", "", "Compute element is registered."),
    DEREGISTER_COMPUTE_ELEMENT("UNREGISTER COMPUTE ELEMENT", "", "Compute element is deregistered."),

    CREATE_COMPUTE_IMAGE("CREATE COMPUTE IMAGE", "", "Compute image is created."),
    UPDATE_COMPUTE_IMAGE("UPDATE COMPUTE IMAGE", "", "Compute image is updated."),
    DELETE_COMPUTE_IMAGE("DELETE COMPUTE IMAGE", "", "Compute image is deleted."),
    INSTALL_COMPUTE_IMAGE("INSTALL COMPUTE IMAGE", "", "Compute image is installed."),

    POWERUP_COMPUTE_ELEMENT("POWERUP COMPUTE ELEMENT", "POWERUP COMPUTE ELEMENT_FAILED", "Compute element is powered-up."),
    POWERDOWN_COMPUTE_ELEMENT("POWERDOWN COMPUTE ELEMENT", "POWERDOWN COMPUTE ELEMENT_FAILED",
            "Compute element is powered-down."),

    CREATE_COMPUTE_VPOOL("CREATE COMPUTE VPOOL", "", "Compute virtual pool is created."),
    UPDATE_COMPUTE_VPOOL("UPDATE COMPUTE VPOOL", "", "Compute virtual pool is updated."),
    DELETE_COMPUTE_VPOOL("DELETE COMPUTE VPOOL", "", "Compute virtual pool is deleted."),
    CREATE_UPDATE_VCENTER_CLUSTER("CREATE UPDATE VCENTER CLUSTER", "", "Vcenter cluster is created/updated."),

    SSH_LOGIN("SSH LOGIN", "", "SSH login performed."),
    AUTHENTICATION("AUTHENTICATION", "", "Authentication performed."),
    UPDATE_VERSION("UPDATE VERSION", "", "Version update performed."),
    INSTALL_IMAGE("INSTALL IMAGE", "", "Image installation performed."),
    REMOVE_IMAGE("REMOVE IMAGE", "", "Image removal performed."),
    UPLOAD_IMAGE("UPLOAD IMAGE", "", "Image upload performed."),
    WAKEUP_UPGRAGE_MANAGER("WAKEUP UPGRADE MANAGER", "", "Upgrade manager wake-up performed."),
    UPDATE_SYSTEM_PROPERTY("UPDATE SYSTEM PROPERTY", "", "System property is updated."),
    SEND_ALERT("SEND ALERT", "", "Alert is sent."),
    SEND_REGISTRATION("SEND ALERT", "", "Registration is sent."),
    SEND_HEARTBEAT("SEND HEARTBEAT", "", "Heart-beat signal is sent."),
    SEND_STAT("SEND STAT", "", "Statistic is sent."),
    SEND_LICENSE_EXPIRED("SEND LICENSE EXPIRED", "", "License expiration information notice is sent."),
    SEND_CAPACITY_EXCEEDED("SEND CAPACITY EXCEEDED", "", "Storage capacity exceeded notice is sent."),
    ADD_LICENSE("ADD LICENSE", "", "License is added to system."),
    CREATE_ESRS_CONFIGURATION("CREATE ESRS CONFIGURATION", "", "ESRS configuration is created."),
    SCHEDULE_EVENT("SCHEDULE EVENT", "", "Schedule event performed."),
    CHANGE_LOCAL_AUTHUSER_PASSWORD("CHANGE LOCAL AUTHUSER PASSWORD", "", "Local authuser password is changed."),
    RESET_LOCAL_USER_PASSWORD("RESET LOCAL AUTHUSER PASSWORD", "", "Local authuser password is reset."),
    CHANGE_LOCAL_AUTHUSER_AUTHKEY("CHANGE LOCAL AUTHUSER AUTHKEY", "", "Local authuser authkey is updated."),
    RESTART_SERVICE("RESTART SERVICE", "", "Service is restarted."),
    REBOOT_NODE("REBOOT NODE", "", "Node is rebooted."),
    POWER_OFF_CLUSTER("POWEROFF CLUSTER", "", "Cluster is powered off."),
    POWER_OFF_NODE("POWEROFF NODE", "", "Node is powered off."),
    CREATE_CAS_POOL("CREATE CAS POOL", "", "CAS pool is created."),
    UPDATE_CAS_POOL("UPDATE CAS POOL", "", "CAS pool is updated."),
    CREATE_CAS_PROFILE("CREATE CAS PROFILE", "", "CAS profile is created."),
    UPDATE_CAS_PROFILE("UPDATE CAS PROFILE", "", "CAS profile is updated."),
    CREATE_CAS_CLUSTER("CREATE CAS CLUSTER", "", "CAS cluster is created."),
    UPDATE_CAS_CLUSTER("UPDATE CAS CLUSTER", "", "CAS cluster is updated."),
    ADD_VDC("ADD VDC", "", "Virtual data center is added."),
    REMOVE_VDC("REMOVE VDC", "", "Virtual data center is removed."),
    DISCONNECT_VDC("DISCONNECT VDC", "", "Virtual data center is disconnected."),
    RECONNECT_VDC("RECONNECT VDC", "", "Virtual data center is reconnected."),
    UPDATE_VDC("UPDATE VDC", "", "Virtual data center is updated."),
    PREPARE_VDC("PREPARE VDC", "",
            "Virtual data center is prepared for GEO scenario. Root user TENANT_ADMIN and project ownerships will be removed."),
    SET_KEY_AND_CERTIFICATE("SET KEY AND CERTIFICATE", "", "Key and certificates are assigned."),
    REGENERATE_KEY_AND_CERTIFICATE("REGENERATE KEY AND CERTIFICATE", "", "Key and certificate are regenerated."),
    UPDATE_TRUSTED_CERTIFICATES("UPDATE TRUSTED CERTIFICATES", "", "Trusted certificates are updated."),
    UPDATE_TRUSTED_CERTIFICATES_PARTIAL("UPDATE TRUSTED CERTIFICATES PARTIAL SUCCESS", "",
            "Trusted certificates update partially successful."),
    UPDATE_TRUSTSTORE_SETTINGS("UPDATE TRUSTSTORE SETTINGS", "", "Trust store settings are updated."),
    ADD_ALIAS("ADD ALIAS", "", "Alias(es) are added."),
    REMOVE_ALIAS("REMOVE ALIAS", "", "Alias(es) are removed."),
    UPDATE_ALIAS("UPDATE ALIAS", "", "Alias(es) are updated."),
    DELETE_TASK("DELETE TASK", "", "Task is deleted."),
    SEND_PASSWORD_TO_BE_EXPIRE_MAIL("SEND PASSWORD TO BE EXPIRED MAIL SUCCESS", "SEND PASSWORD TO BE EXPIRED MAIL FAIL",
            "Mail about expiring password is sent."),
    DELETE_CONFIG("DELETE CONFIG", "", "Controller configuration is deleted."),
    CREATE_CONFIG("CREATE CONFIG", "", "Controller configuration is created."),
    UPDATE_CONFIG("UPDATE CONFIG", "", "Controller configuration is updated."),
    REGISTER_CONFIG("REGISTER CONFIG", "", "Controller configuration is registered."),
    DEREGISTER_CONFIG("DEREGISTER CONFIG", "", "Controller configuration is deregistered."),
    UPDATE_FILE_SYSTEM_SHARE_ACL("UPDATE FILE SHARE ACL", "", "File system share ACL is updated."),
    UPDATE_FILE_SNAPSHOT_SHARE_ACL("UPDATE SNAPSHOT SHARE ACL", "", "File system snapshot share ACL is updated."),
    DELETE_FILE_SYSTEM_SHARE_ACL("DELETE FILE SHARE ACL", "", "File system share ACL is deleted."),
    DELETE_FILE_SNAPSHOT_SHARE_ACL("DELETE SNAPSHOT SHARE ACL", "", "File system snapshot share ACL is deleted."),
    UPDATE_FILE_SYSTEM_NFS_ACL("UPDATE FILE NFS ACL", "", "NFS file system ACL is updated."),
    UPDATE_FILE_SNAPSHOT_NFS_ACL("UPDATE SNAPSHOT NFS ACL", "", "NFS file system snapshot ACL is updated."),
    DELETE_FILE_SYSTEM_NFS_ACL("DELETE FILE NFS ACL", "", "NFS File system ACL is deleted."), 
    DELETE_FILE_SNAPSHOT_NFS_ACL("DELETE SNAPSHOT NFS ACL", "", "NFS File system snapshot is deleted."),
    CREATE_BACKUP("CREATE BACKUP", "", "ViPR backup is created."),
    DELETE_BACKUP("DELETE BACKUP", "", "ViPR backup is deleted."),
    UPLOAD_BACKUP("UPLOAD BACKUP", "", "ViPR backup is uploaded to external location."),
    PULL_BACKUP("PULL BACKUP", "", "ViPR backup is pulled from external location."),
    PULL_BACKUP_CANCEL("PULL BACKUP CANCEL", "", "ViPR backup pull operation is cancelled."),
    RESTORE_BACKUP("RESTORE BACKUP", "", "ViPR backup is restored."),
    RECOVER_NODES("RECOVER NODES", "", "Corrupted nodes are recovered."),
    RECONFIG_IP("Reconfig IPs", "", "IP reconfiguration is triggered."),
    CREATE_USERGROUP("CREATE USER GROUP", "", "User group is created."),
    UPDATE_USERGROUP("UPDATE USER GROUP", "", "User group is updated."),
    DELETE_USERGROUP("DELETE USER GROUP", "", "User group is deleted."),
    ADD_JOURNAL_VOLUME("ADD JOURNAL VOLUME", "", "Journal volume is added."),
    ArrayGeneric("", "", ""),
    IMAGESERVER_VERIFY_IMPORT_IMAGES("IMAGESERVER_VERIFY_IMPORT_IMAGES", "",
            "Compute image server and import images are verified."),
    UPDATE_VERIFY_COMPUTE_IMAGESERVER("UPDATE AND VERIFY COMPUTE IMAGE SERVER", "",
            "Compute image server is updated and verified."),
    DELETE_COMPUTE_IMAGESERVER("DELETE COMPUTE IMAGE SERVER", "", "Compute image server is deleted."),
    CREATE_VOLUME_GROUP("CREATE VOLUME GROUP", "", "Volume group is created."),
    DELETE_VOLUME_GROUP("DELETE VOLUME GROUP", "", "Volume group is deleted."),
    UPDATE_VOLUME_GROUP("UPDATE VOLUME GROUP", "", "Volume group is updated."),

    CREATE_VOLUME_GROUP_FULL_COPY("VolumeGroupFullCopyCreated", "VolumeGroupFullCopyCreateFailed", "Volume group full copy is created."),
    ACTIVATE_VOLUME_GROUP_FULL_COPY("VolumeGroupFullCopyActivated", "VolumeGroupFullCopyActivateFailed", "Volume group full copy is activated."),
    DETACH_VOLUME_GROUP_FULL_COPY("VolumeGroupFullCopyDetached", "VolumeGroupFullCopyDetachFailed", "Volume group full copy is detached."),
    RESTORE_VOLUME_GROUP_FULL_COPY("VolumeGroupFullCopyRestored", "VolumeGroupFullCopyRestoreFailed", "Volume group full copy is restored."),
    RESYNCHRONIZE_VOLUME_GROUP_FULL_COPY("VolumeGroupFullCopyResynchronized", "VolumeGroupFullCopy ResynchronizeFailed","Volume group full copy is resynchronized."),

    CREATE_VOLUME_GROUP_SNAPSHOT("VolumeGroupSnapshotCreated", "VolumeGroupSnapshotCreateFailed", "Volume group snapshot is created."),
    ACTIVATE_VOLUME_GROUP_SNAPSHOT("VolumeGroupSnapshotActivated", "VolumeGroupSnapshotActivateFailed", "Volume group snapshot is activated."),
    DEACTIVATE_VOLUME_GROUP_SNAPSHOT("VolumeGroupSnapshotDeactivated", "VolumeGroupSnapshotDeactivateFailed", "Volume group snapshot is deactivated."),
    RESTORE_VOLUME_GROUP_SNAPSHOT("VolumeGroupSnapshotRestored", "VolumeGroupSnapshotRestoreFailed", "Volume group snapshot is restored."),
    RESYNCHRONIZE_VOLUME_GROUP_SNAPSHOT("VolumeGroupSnapshotResynchronized", "VolumeGroupSnapshotResynchronizeFailed", "Volume group snapshot is resynchronized."),

    CREATE_VOLUME_GROUP_SNAPSHOT_SESSION("VolumeGroupSnapshotSessionCreated", "VolumeGroupSnapshotSessionCreateFailed",
            "Volume group snapshot session is created."),
    RESTORE_VOLUME_GROUP_SNAPSHOT_SESSION("VolumeGroupSnapshotSessionRestored", "VolumeGroupSnapshotSessionRestoreFailed",
            "Volume group snapshot session is restored."),
    DELETE_VOLUME_GROUP_SNAPSHOT_SESSION("VolumeGroupSnapshotSessionDeleted", "VolumeGroupSnapshotSessionDeleteFailed",
            "Volume group snapshot session is deleted."),
    LINK_VOLUME_GROUP_SNAPSHOT_SESSION_TARGET("LinkVolumeGroupSnapshotSessionTargets", "LinkVolumeGroupSnapshotSessionTargetsFailed",
            "Volume group snapshot session is linked."),
    RELINK_VOLUME_GROUP_SNAPSHOT_SESSION_TARGET("RelinkVolumeGroupSnapshotSessionTargets", "RelinkVolumeGroupSnapshotSessionTargetsFailed",
            "Volume group snapshot session is relinked."),
    UNLINK_VOLUME_GROUP_SNAPSHOT_SESSION_TARGET("UnlinkVolumeGroupSnapshotSessionTargets", "UnlinkVolumeGroupSnapshotSessionTargetsFailed",
            "Volume group snapshot session is unlinked."),

    CREATE_SNAPSHOT_SESSION("BlockSnapshotSessionCreated", "BlockSnapshotSessionCreateFailed", "Block snapshot session is created."),
    RESTORE_SNAPSHOT_SESSION("BlockSnapshotSessionRestored", "BlockSnapshotSessionRestoreFailed", "Block snapshot session is restored."),
    DELETE_SNAPSHOT_SESSION("BlockSnapshotSessionDeleted", "BlockSnapshotSessionDeleteFailed", "Block snapshot session is deleted."),
    LINK_SNAPSHOT_SESSION_TARGET("LinkBlockSnapshotSessionTargets", "LinkBlockSnapshotSessionTargetsFailed", "Block snapshot session is linked."),
    RELINK_SNAPSHOT_SESSION_TARGET("RelinkBlockSnapshotSessionTargets", "RelinkBlockSnapshotSessionTargetsFailed", "Block snapshot session is relinked."),
    UNLINK_SNAPSHOT_SESSION_TARGET("UnlinkBlockSnapshotSessionTargets", "UnlinkBlockSnapshotSessionTargetsFailed", "Block snapshot session is unlinked."),

    UPDATE_BUCKET_ACL("UPDATE BUCKET ACL", "", "Bucket ACL is updated."),
    DELETE_BUCKET_ACL("DELETE BUCKET ACL", "", "Bucket ACL is deleted."),
    SYNC_BUCKET_ACL("SYNC BUCKET ACL", "", "Bucket ACL is synchronized."),

    /* Disaster Recovery Operations */
    ADD_STANDBY("ADD STANDBY", "", "Adding new standby site to ensemble is initiated."),
    REMOVE_STANDBY("REMOVE STANDBY", "", "Removing standby site from ensemble is initiated."),
    PAUSE_STANDBY("PAUSE STANDBY REPLICATION", "", "Pausing standby site is initiated."),
    RESUME_STANDBY("RESUME STANDBY REPLICATION", "", "Resuming standby site is initiated."),
    RETRY_STANDBY_OP("RETRY STANDBY OPERATION", "", "Retry standby site is initiated."),
    IPSEC_KEY_ROTATE("ROTATE IPSEC KEY", "", "Pre shared key is rotated."),
    SWITCHOVER("SWITCHOVER TO A STANDBY", "", "Standby site switch-over is initiated."),
    ACTIVE_SWITCHOVER("ACTIVE BECOME STANDBY AFTER SWITCHOVER", "", "Active site is set to standby site after switch-over."),
    STANDBY_SWITCHOVER("STANDBY BECOME ACTIVE AFTER SWITCHOVER", "", "Standby site is set to active site after switch-over."),
    FAILOVER("FAILOVER TO A STANDBY", "", "Standby failover is initiated."),
    STANDBY_FAILOVER("STANDBY BECOME ACTIVE AFTER FAILOVER", "", "Standby site is set to active site after failover."),
    STANDBY_DEGRADE("DEGRADE STANDBY", "", "operation that marks standby as degraded."),
    STANDBY_REJOIN("STANDBY REJOIN VDC FROM STANDBY_DEGRADED STATE", "",
            "Rejoined virtual data center from degraded state for standby site is marked."),
    UPDATE_SITE("UPDATE SITE", "", "Site information is updated."),
    ADD_STORAGE_SYSTEM_TYPE("ADD STORAGE SYSTEM TYPE", "", "Storage system type is added."),
    REMOVE_STORAGE_SYSTEM_TYPE("REMOVE STORAGE SYSTEM TYPE", "", "Storage system type is removed."),
    CREATE_SCHEDULE_POLICY("SchedulePolicyCreated", "", "Schedule policy is created."),
    EXPORT_PATH_ADJUSTMENT("EXPORT PATH ADJUSTMENT", "", "Export path adjustment performed."),
    REGISTER_STORAGE_PORT_GROUP("REGIESTER PORT GROUP", "", "Storage port group is registered"),
    DEREGISTER_STORAGE_PORT_GROUP("DEREGIESTER PORT GROUP", "", "Storage port group is unregistered"),
    CREATE_STORAGE_PORT_GROUP("CREATE STORAGE PORT GROUP", "", "Storage port group is created"),
    DELETE_STORAGE_PORT_GROUP("DELETE STORAGE PORT GROUP", "", "Storage port group is deleted"),
    EXPORT_CHANGE_PORT_GROUP("CHANGE EXPORT PORT GROUP", "", "Change port group for export"),
    /* Filesystem Mount Operations*/
    MOUNT_NFS_EXPORT("MOUNT NFS EXPORT", "mount nfs export operation failed", "NFS export is mounted."),
    UNMOUNT_NFS_EXPORT("UNMOUNT NFS EXPORT", "unmount nfs operation failed", "NFS export is unmounted."),
    LIST_MOUNTED_EXPORTS("LIST MOUNTED EXPORTS", "list mounted nfs operation failed", "Mounted exports are retrieved."),

    DELETE_FILE_POLICY("DELETE FILE POLICY", "Delete file policy failed", "File policy is deleted."),
    UNASSIGN_FILE_POLICY("FilePolicyUnassign", "FilePolicyUnassignFailed", "File policy is unassigned."),
    /* Storage Driver Operations */
    INSTALL_STORAGE_DRIVER("INSTALL STORAGE DRIVER", "", "Storage driver is installed."),
    UNINSTALL_STORAGE_DRIVER("UNINSTALL STORAGE DRIVER", "", "Storage driver is uninstalled."),
    UPGRADE_STORAGE_DRIVER("UPGRADE STORAGE DRIVER", "", "Storage driver is upgraded.");

    private final String _evType;
    private final String _fail_evType;
    private final String _description;

    OperationTypeEnum(String evType, String fail_evType, String description) {
        _evType = evType;
        _fail_evType = fail_evType;
        _description = description;
    }

    @XmlElement
    public String getEvType(boolean status) {
        if (status) {
            return _evType;
        } else {
            return _fail_evType;
        }
    }

    public String getDescription() {
        return _description;
    }

    private static final Map<String, OperationTypeEnum> resourceOpMap = new HashMap<String, OperationTypeEnum>();

    static {
        for (OperationTypeEnum res : values()) {
            resourceOpMap.put(res.toString(), res);
        }
    }

}
