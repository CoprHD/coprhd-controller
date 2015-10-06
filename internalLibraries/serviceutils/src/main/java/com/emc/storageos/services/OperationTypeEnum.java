/*
 * Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.services;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;

public enum OperationTypeEnum {

    /* EventName failEventName event summary */
    CREATE_TENANT("TenantCreated", "", "Subtenant created"),
    UPDATE_TENANT("TenantUpdated", "", "Tenant updated"),
    DELETE_TENANT("TenantDeleted", "", "Subtenant deleted"),
    UPDATE_TENANT_STS("UPDATE TENANT STS", "", "update tenant's STS operation."),
    REASSIGN_TENANT_ROLES("TenantUpdated", "", "Tenant roles changed"),
    MODIFY_TENANT_ROLES("TenantUpdated", "", "Tenant roles updated"),
    CREATE_TENANT_TAG("TAG TENANT", "", "operation to tag on a tenant."),
    SET_TENANT_NAMESPACE("Set Tenant NS", "", "attach tenant with namespace"),
    GET_TENANT_NAMESPACE("Get Tenant NS", "", "get namespace of tenant"),
    UNSET_TENANT_NAMESPACE("Unset Tenant NS", "", "detach tenant from namespace"),

    REASSIGN_ZONE_ROLES("REPLACE ZONE ROLES", "", "operation to overwrite zone roles."),
    MODIFY_ZONE_ROLES("MODIFY ZONE ROLES", "", "operation to modify the zone roles."),
    CREATE_PROJECT("ProjectCreated", "", "create project."),
    UPDATE_PROJECT("ProjectUpdated", "", "update project"),
    DELETE_PROJECT("ProjectDeleted", "", "delete project"),
    MODIFY_PROJECT_ACL("ProjectUpdated", "", "Project ACLs updated"),
    REASSIGN_PROJECT_ACL("ProjectUpdated", "", "Project ACLs changed"),
    CREATE_VPOOL("VpoolCreated", "", "VirtualPool Create"),
    UPDATE_VPOOL("VpoolUpdated", "", "VirtualPool Update"),
    DELETE_VPOOL("VpoolDeleted", "", "VirtualPool Delete"),
    REASSIGN_VPOOL_ACL("VpoolUpdated", "", "operation to overwrite VirtualPool acls"),
    MODIFY_VPOOL_ACL("VpoolUpdated", "", "operation to modify VirtualPool acls"),
    ASSIGN_VPOOL_TAG("ASSIGN VPOOL TAG", "", "operation to assign a tag to a VirtualPool"),

    CREATE_BLOCK_VOLUME("VolumeCreated", "VolumeCreateFailed", "Volume Create"),
    DELETE_BLOCK_VOLUME("VolumeDeleted", "VolumeDeleteFailed", "Volume Delete"),
    EXPAND_BLOCK_VOLUME("VolumeExpanded", "VolumeExpandFailed", "Volume expand"),

    CREATE_VOLUME_SNAPSHOT("VolumeSnapshotCreated", "VolumeSnapshotCreateFailed", "VolumeSnapshot Create"),
    ASSIGN_VOLUME_TAG("ASSIGN VOLUME TAG", "", "operation to tag a volume"),
    DELETE_VOLUME_SNAPSHOT("VolumeSnapshotDeleted", "VolumeSnapshotDeleteFailed", "VolumeSnapshot Delete"),
    RESTORE_VOLUME_SNAPSHOT("VolumeSnapshotRestored", "VolumeSnapshotRestoreFailed", "VolumeSnapshot Restore"),
    RESYNCHRONIZE_VOLUME_SNAPSHOT("VolumeSnapshotResynchronized", "VolumeSnapshotResynchronizeFailed", "VolumeSnapshot Resynchronize"),
    ACTIVATE_VOLUME_SNAPSHOT("VolumeSnapshotActivated", "VolumeSnapshotActivateFailed", "VolumeSnapshot Activate"),
    DEACTIVATE_VOLUME_SNAPSHOT("VolumeSnapshotDeactivated", "VolumeSnapshotDeactivateFailed", "VolumeSnapshot Deactivate"),
    ESTABLISH_VOLUME_SNAPSHOT("VolumeSnapshotGroupEstablished", "VolumeSnapshotGroupEstablishFailed", "VolumeSnapshot GroupEstablish"),
    CHANGE_VOLUME_VPOOL("VolumeVPoolChanged", "VolumeVPoolChangeFailed", "Volume VirtualPool Change"),
    CHANGE_VOLUME_AUTO_TIERING_POLICY("VolumeAutoTieringPolicyChanged", "VolumeAutoTieringPolicyChangeFailed",
            "Volume Auto-tiering Policy Change"),
    ASSIGN_VOLUME_SNAPSHOT_TAG("TAG VOLUME SNAPSHOT", "", "operation to tag a volume snapshot"),

    CREATE_SRDF_LINK("SRDFLinkCreated", "SRDFLinkCreateFailed", "SRDF Link Create"),
    SUSPEND_SRDF_LINK("SRDFLinkSuspended", "SRDFLinkSuspendFailed", "SRDF Link Suspend"),
    DETACH_SRDF_LINK("SRDFLinkDetached", "SRDFLinkDetachFailed", "SRDF Link Detach"),
    PAUSE_SRDF_LINK("SRDFLinkPaused", "SRDFLinkPauseFailed", "SRDF Link Pause"),
    RESUME_SRDF_LINK("SRDFLinkResumed", "SRDFLinkResumeFailed", "SRDF Link Resume"),
    FAILOVER_SRDF_LINK("SRDFLinkFailedOver", "SRDFLinkFailOverFailed", "SRDF Link Fail Over"),
    SYNC_SRDF_LINK("SRDFLinkSynced", "SRDFLinkSyncFailed", "SRDF Link Sync"),
    SWAP_SRDF_VOLUME("SRDFVolumeSwapped", "SRDFVolumeSwapFailed", "SRDF Volume Swap"),
    STOP_SRDF_LINK("SRDFLinkStopped", "SRDFLinkStopFailed", "SRDF Link Stop"),
    CHANGE_SRDF_COPYMODE("SRDFCopyModeChanged", "SRDFCopyModeChangeFailed", "SRDF Copy Mode Change"),

    START_RP_LINK("RPLinkStarted", "RPLinkStartFailed", "RP Link Create"),
    STOP_RP_LINK("RPLinkStopped", "RPLinkStopFailed", "RP Link Stop"),
    PAUSE_RP_LINK("RPLinkPaused", "RPLinkPauseFailed", "RP Link Pause"),
    RESUME_RP_LINK("RPLinkResumed", "RPLinkResumeFailed", "RP Link Resume"),
    SWAP_RP_VOLUME("RPVolumeSwapped", "RPVolumeSwapFailed", "RP Volume Swap"),
    SYNC_RP_LINK("RPLinkSynced", "RPLinkSyncFailed", "RP Link Sync"),
    FAILOVER_RP_LINK("RPLinkFailedOver", "RPLinkFailOverFailed", "RP Link Fail Over"),
    FAILOVER_CANCEL_RP_LINK("RPLinkFailedOverCanceled", "RPLinkFailOverCancelFailed", "RP Link Fail Over Cancel"),
    FAILOVER_TEST_RP_LINK("RPLinkTestFailedOver", "RPLinkTestFailOverFailed", "RP Link Test Fail Over"),
    FAILOVER_TEST_CANCEL_RP_LINK("RPLinkTestCancelFailedOver", "RPLinkTestCancelFailOverFailed", "RP Link Test Cancel Fail Over"),

    CREATE_VOLUME_MIRROR("VolumeMirrorCreated", "VolumeMirrorCreateFailed", "VolumeMirror Create"),
    DEACTIVATE_VOLUME_MIRROR("VolumeMirrorDeactivated", "VolumeMirrorDeactivateFailed", "VolumeMirror Deactivate"),
    DELETE_VOLUME_MIRROR("VolumeMirrorDeleted", "VolumeMirrorDeleteFailed", "VolumeMirror Delete"),
    DETACH_VOLUME_MIRROR("VolumeMirrorDetached", "VolumeMirrorDetachFailed", "VolumeMirror detach"),
    FRACTURE_VOLUME_MIRROR("VolumeMirrorFractured", "VolumeMirrorFractureFailed", "VolumeMirror fracture"),
    RESUME_VOLUME_MIRROR("VolumeMirrorResumed", "VolumeMirrorResumeFailed", "VolumeMirror resume"),
    ESTABLISH_VOLUME_MIRROR("VolumeMirrorGroupEstablished", "VolumeMirrorGroupEstablishFailed", "VolumeMirror GroupEstablish"),
    CREATE_VOLUME_FULL_COPY("VolumeFullCopyCreated", "VolumeFullCopyCreateFailed", "VolumeFullCopy Created"),
    DETACH_VOLUME_FULL_COPY("VolumeFullCopyDetached", "VolumeFullCopyDetachFailed", "VolumeFullCopy Detached"),
    ACTIVATE_VOLUME_FULL_COPY("VolumeFullCopyActivated", "VolumeFullCopyActivateFailed", "VolumeFullCopy Activated"),
    RESTORE_VOLUME_FULL_COPY("VolumeFullCopyRestored", "VolumeFullCopyRestoredFailed", "VolumeFullCopy Restored"),
    RESYNCHRONIZE_VOLUME_FULL_COPY("VolumeFullCopyResynchronized", "VolumeFullCopyResynchronizeFailed", "VolumeFullCopy Resynchronized"),
    ESTABLISH_VOLUME_FULL_COPY("VolumeFullCopyGroupEstablished", "VolumeFullCopyGroupEstablishFailed", "VolumeFullCopy GroupEstablish"),

    CREATE_CONSISTENCY_GROUP_SNAPSHOT("ConsistencyGroupSnapshotCreated", "ConsistencyGroupSnapshotCreateFailed",
            "ConsistencyGroupSnapshot Create"),
    DELETE_CONSISTENCY_GROUP_SNAPSHOT("ConsistencyGroupSnapshotDeleted", "ConsistencyGroupSnapshotDeleteFailed",
            "ConsistencyGroupSnapshot Delete"),
    RESTORE_CONSISTENCY_GROUP_SNAPSHOT("ConsistencyGroupSnapshotRestored", "ConsistencyGroupSnapshotRestoreFailed",
            "ConsistencyGroupSnapshot Restore"),
    DEACTIVATE_CONSISTENCY_GROUP_SNAPSHOT("ConsistencyGroupSnapshotDeactivated", "ConsistencyGroupSnapshotDeactivateFailed",
            "ConsistencyGroupSnapshot Deactivate"),
    ACTIVATE_CONSISTENCY_GROUP_SNAPSHOT("ConsistencyGroupSnapshotActivated", "ConsistencyGroupSnapshotActivateFailed",
            "ConsistencyGroupSnapshot Activate"),

    CREATE_EXPORT_GROUP("ExportCreated", "ExportCreateFailed", "Export Create"),
    UPDATE_EXPORT_GROUP("ExportUpdated", "ExportUpdateFailed", "Export Update"),
    DELETE_EXPORT_GROUP("ExportDeleted", "ExportDeleteFailed", "Export Delete"),
    ADD_EXPORT_INITIATOR("ExportInitiatorAdded", "ExportInitiatorAddFailed", "ExportInitiator Add"),
    ADD_EXPORT_VOLUME("ExportVolumeAdded", "ExportVolumeAddFailed", "ExportVolume Add"),
    DELETE_EXPORT_INITIATOR("ExportInitiatorRemoved", "ExportInitiatorRemoveFailed", "ExportInitiator Remove"),
    DELETE_EXPORT_VOLUME("ExportVolumeRemoved", "ExportVolumeRemoveFailed", "ExportVolume Remove"),

    CREATE_FILE_SYSTEM("FileSystemCreated", "FileSystemCreateFailed", "FileSystem created"),
    DELETE_FILE_SYSTEM("FileSystemDeleted", "FileSystemDeleteFailed", "FileSystem deleted"),
    EXPORT_FILE_SYSTEM("FileSystemExported", "FileSystemExportFailed", "FileSystem exported"),
    UPDATE_EXPORT_RULES_FILE_SYSTEM("FileSystemExportRulesUpdated", "FileSystemExportRulesUpdateFailed", "FileSystem export rules updated"),
    UNEXPORT_FILE_SYSTEM("FileSystemUnexported", "FileSystemUnexportFailed", "FileSystem unexported"),
    EXPAND_FILE_SYSTEM("FileSystemExpanded", "FileSystemExpandFailed", "FileSystem expanded"),
    RELEASE_FILE_SYSTEM("FileSystemReleased", "", "FileSystem released"),
    UNDO_RELEASE_FILE_SYSTEM("FileSystemReleaseUndone", "", "FileSystem release undone"),

    CREATE_FILE_SYSTEM_SHARE("FileSystemShared", "FileSystemShareFailed", "FileSystem shared"),
    ASSIGN_FILE_SYSTEM_TAG("TAG A FILESYSTEM", "", "operation to tag a filesystem"),
    DELETE_FILE_SYSTEM_SHARE("FileSystemShareDeleted", "FileSystemShareDeleteFailed", "FileSystem share deleted"),

    CREATE_FILE_SYSTEM_SNAPSHOT("FileSystemSnapshotCreated", "FileSystemSnapshotCreateFailed", "FileSystem snapshot created"),
    DELETE_FILE_SNAPSHOT("FileSystemSnapshotDeleted", "FileSystemSnapshotDeleteFailed", "FileSystem snapshot deleted"),
    EXPORT_FILE_SNAPSHOT("FileSystemSnapshotExported", "FileSystemSnapshotExportFailed", "FileSystem snapshot exported"),
    UPDATE_EXPORT_RULES_FILE_SNAPSHOT("FileSystemSnapshotExportRulesUpdated", "FileSystemSnapshotExportRulesUpdateFailed",
            "FileSystem snapshot export rules updated"),
    UNEXPORT_FILE_SNAPSHOT("FileSystemSnapshotUnexported", "FileSystemSnapshotUnexportFailed", "FileSystem snapshot unexported"),

    CREATE_FILE_SYSTEM_QUOTA_DIR("FileSystemQuotaDirCreated", "FileSystemQuotaDirCreateFailed", "FileSystem QuotaDir created"),
    DELETE_FILE_SYSTEM_QUOTA_DIR("FileSystemQuotaDirDeleted", "FileSystemQuotaDirDeleteFailed", "FileSystem QuotaDir deleted"),
    UPDATE_FILE_SYSTEM_QUOTA_DIR("FileSystemQuotaDirUpdated", "FileSystemQuotaDirUpdateFailed", "FileSystem QuotaDir updated"),

    CREATE_FILE_SNAPSHOT_SHARE("FileSystemSnapshotShared", "FileSystemSnapshotShareFailed", "FileSystem snapshot shared"),
    ASSIGN_FILE_SNAPSHOT_TAG("TAG A FILESYSTEM SNAPSHOT", "", "tag a fileshare snapshot"),
    DELETE_FILE_SNAPSHOT_SHARE("FileSystemSnapshotShareDeleted", "FileSystemSnapshotShareDeleteFailed", "FileSystem snapshot share deleted"),
    RESTORE_FILE_SNAPSHOT("FileSystemRestored", "FileSystemRestoreFailed", "FileSystem restored"),

    CREATE_BUCKET("BucketCreated", "BucketCreateFailed", "Bucket created"),
    DELETE_BUCKET("BucketDeleted", "BucketDeleteFailed", "Bucket deleted"),
    UPDATE_BUCKET("BucketUpdated", "BucketUpdateFailed", "Bucket updated"),

    STORAGE_PORT_REGISTER("StoragePortRegistered", "", "Storage Port Registered"),
    STORAGE_PORT_DEREGISTER("StoragePortUnregistered", "", "Storage Port Unregistered"),
    STORAGE_PORT_UPDATE("StoragePortUpdated", "", "Storage Port Updated"),

    STORAGE_POOL_REGISTER("StoragePoolRegistered", "", "Storage Pool Registered"),
    STORAGE_POOL_DEREGISTER("StoragePoolUnregistered", "", "Storage Pool Unregistered"),
    STORAGE_POOL_UPDATE("StoragePoolUpdated", "", "Storage Pool Updated"),

    OPERATE_BLOCK_VOLUME("VolumeEventOkStatus", "VolumeEventNotOkStatus", "operation on Volume"),
    OPERATE_FILE_SYSTEM("FileSystemEventOkStatus", "FileSystemEventNotOkStatus", "operation on file system"),

    STORAGE_PROVIDER_DOWN("STORAGE PROVIDER DOWN", "", "Storage Provider is down"),
    STORAGE_PROVIDER_UP("STORAGE PROVIDER UP", "", "Storage Provider is up"),

    CREATE_KEYPOOL("CREATE KEYPOOL", "", "create keypool operation"),
    DELETE_KEYPOOL("DELETE KEYPOOL", "", "delete keypool operation"),
    UPDATE_KEYPOOL_ACCESSMODE("UPDATE KEYPOOL", "", "update keypool operation"),
    CREATE_NAMESPACE("CREATE NAMESPACE", "", "create object namespace operaiton"),
    UPDATE_NAMESPACE("UPDATE NAMESPACE", "", "update object namespace operaiton"),
    DELETE_NAMESPACE("DELETE NAMESPACE", "", "delete object namespace operaiton"),
    CREATE_VARRAY("CREATE VARRAY", "", "create varray operation"),
    DELETE_VARRAY("DELETE VARRAY", "", "delete varray oepration"),
    UPDATE_VARRAY("UPDATE VARRAY", "", "update varray oepration"),
    MODIFY_VARRAY_ACL("UPDATE VARRAY ACL", "", "operation to update varray acls"),
    REASSIGN_VARRAY_ACL("REPLACE VARRAY ACL", "", "operation to overwrite varray acls"),
    SET_VARRAY_PROTECTIONTYPE("SET VARRAY PROTECTION TYPE", "", "operationa to set varray protection type"),
    GET_VARRAY_PROTECTIONTYPE("GET VARRAY PROTECTION TYPE", "", "operationa to get varray protection type"),
    UNSET_VARRAY_PROTECTIONTYPE("UNSET VARRAY PROTECTION TYPE", "", "operationa to unset varray protection type"),
    SET_VARRAY_REGISTERED("SET VARRAY REGISTERED STATUS", "", "operationa to set varray registered status"),
    GET_VARRAY_REGISTERED("GET VARRAY REGISTERED STATUS", "", "operationa to get varray registered status"),

    CREATE_DATA_STORE("CREATE DATASTORE", "", "create data store operation"),
    DELETE_DATA_STORE("DELETE DATASTORE", "", "delete data store operation"),
    CREATE_SECRET_KEY("CREATE SECRET KEY", "", "create a secret key for a user"),
    DELETE_SECRET_KEY("DELETE SECRET KEY", "", "delete a users secret key"),
    REGISTER_SMISPROVIDER("REGISTER SMIS PROVIDER", "", "operation to register smis provider"),
    REGISTER_STORAGEPROVIDER("REGISTER STORAGE PROVIDER", "", "operation to register storage provider"),
    DELETE_SMISPROVIDER("DELETE SMIS PROVIDER", "", "oepration to delete smis provider"),
    DELETE_STORAGEPROVIDER("DELETE STORAGE PROVIDER", "", "oepration to delete storage provider"),
    UPDATE_SMISPROVIDER("UPDATE SMIS PROVIDER", "", "operation to update smis provider"),
    UPDATE_STORAGEPROVIDER("UPDATE STORAGE PROVIDER", "", "operation to update storage provider"),
    SCAN_STORAGEPROVIDER("SCAN STORAGE PROVIDER", "", "operation to scan storage provider"),
    REGISTER_STORAGE_SYSTEM("REGISTER SMIS SYSTEM", "", "operation to register sims system"),
    UPDATE_STORAGE_POOL("UPDATE STORAGEPOOL", "", "operation to update storage pool."),
    DELETE_STORAGE_POOL("DELETE STORAGEPOOL", "", "operation to delete storage pool."),
    DEREGISTER_STORAGE_POOL("UNREGISTER STORAGEPOOL", "", "operation to unregister a storage pool."),
    ASSIGN_STORAGE_POOL_TAG("TAG STORAGEPOOL", "", "operation to tag a storage pool."),
    UPDATE_STORAGE_PORT("UPDATE STORAGEPORT", "", "operation to update storage port"),
    DELETE_STORAGE_PORT("DELETE STORAGEPORT", "", "operation to delete storage port"),
    DEREGISTER_STORAGE_PORT("UNREGISTER STORAGEPORT", "", "operation to unregister storage port"),
    ASSIGN_STORAGE_PORT_TAG("TAG STORAGEPORT", "", "tag a storage port"),
    CREATE_STORAGE_SYSTEM("CREATE STORAGESYSTEM", "", "operation to create a storage system"),
    UPDATE_STORAGE_SYSTEM("UPDATE STORAGESYSTEM", "", "operation to update a storage system"),
    DISCOVER_ALL_STORAGE_SYSTEM("DISCOVER ALL STORAGESYSTEMS", "", "operation to discover all storage systems"),
    DISCOVER_STORAGE_SYSTEM("DISCOVER STORAGESYSTEM", "", "operation to discover one storage systems"),
    DEREGISTER_STORAGE_SYSTEM("UNREGISTER STORAGESYSTEM", "", "operation to unregister a storage systems"),
    CREATE_STORAGE_POOL("CREATE STORAGEPOOL", "", "create storagepool operation"),
    CREATE_STORAGE_PORT("CREATE STORAGEPORT", "", "create storageport operation"),
    ASSIGN_STORAGE_SYSTEM_TAG("TAG STORAGESYSTEM", "", "operation to tag a storage system"),
    REGISTER_STORAGE_POOL("REGISTER STORAGEPOOL", "", "operation to register storageport"),
    REGISTER_STORAGE_PORT("REGISTER STORAGEPORT", "", "operation to register storage port"),
    CREATE_NETWORK("NetworkCreated", "", "network created"),
    DELETE_NETWORK("NetworkDeleted", "", "network deleted"),
    UPDATE_NETWORK("NetworkUpdated", "", "network updated"),
    DEREGISTER_NETWORK("Unregister Network", "", "network unregistered"),
    REGISTER_NETWORK("Register Network", "", "network registered"),
    ASSIGN_NETWORK_TAG("TAG NETWORK", "", "operation to tag a network"),
    CREATE_ATMOS_SUBTENANT("CREATE ATMOS SUBTENANT", "", "create atmos subtenant"),
    DELETE_ATMOS_SUBTENANT("DELETE ATMOS SUBTENANT", "", "delete atmos subtenant"),
    CREATE_S3_BUCKET("CREATE S3 BUCKET", "", "create S3 bucket"),
    SET_S3_BUCKET_ACL("SET ACL ON S3 BUCKET", "", "operation to set an acl on S3 bucket"),
    SET_S3_BUCKET_VERSION("VERSION S3 BUCKET", "", "operation to set the version of S3 bucket"),
    CREATE_SWIFT_CONTAINER("CREATE SWIFT CONTAINER", "", "operation to create swift container"),
    DELETE_SWIFT_CONTAINER("DELETE SWIFT CONTAINER", "", "operation to delete swift container"),
    CREATE_INITIATOR("CREATE INITIATOR", "", "operation to create initiator."),
    DELETE_INITIATOR("DELETE INITIATOR", "", "operation to delete initiator."),
    REGISTER_INITIATOR("REGISTER INITIATOR", "", "operation to register initiator."),
    DEREGISTER_INITIATOR("DEREGISTER INITIATOR", "", "operation to unregister initiator."),
    CREATE_NETWORK_SYSTEM("CREATE NETWORKSYSTEM", "", "operation to create a network system."),
    UPDATE_NETWORK_SYSTEM("UPDATE NETWORKSYSTEM", "", "operation to update a network system."),
    DELETE_NETWORK_SYSTEM("DELETE NETWORKSYSTEM", "", "operation to delete a network system."),
    DISCOVER_NETWORK_SYSTEM("DISCOVER NETWORKSYSTEM", "", "operation to discover one network system."),
    DEREGISTER_NETWORK_SYSTEM("UNREGISTER NETWORKSYSTEM", "", "operation to unregister a network system."),
    REGISTER_NETWORK_SYSTEM("REGISTER STORAGESYSTEM", "", "operation to register a network system."),
    ADD_SAN_ZONE("ADD SAN ZONE", "", "operation to add one or more san zones."),
    REMOVE_SAN_ZONE("REMOVE SAN ZONE", "", "operation to remove one or more san zones."),
    UPDATE_SAN_ZONE("UPDATE SAN ZONE", "", "operation to update one or more san zones."),
    ACTIVATE_SAN_ZONE("ACTIVATE SAN ZONE", "", "operation to activate one or more san zones."),
    PERFORM_PROTECTION_OPERATION("PERFORM PROTECTION OPERATION", "", "operation to protect a block volume"),
    DISCOVER_PROTECTION_SET("DISCOVER_PROTECTION_SET", "", "operation to discover protection set"),
    PERFORM_PROTECTION_ACTION("PERFORM PROTECTION ACTION", "", "operation to perform link management"),
    CREATE_AUTHPROVIDER("CREATE AUTH PROVIDER", "", "operation to create a authentication provider."),
    UPDATE_AUTHPROVIDER("UPDATE AUTH PROVIDER", "", "operation to update a authentication provider."),
    UPDATE_AUTHPROVIDER_GROUP_ATTR("UPDATE AUTH PROVIDER GROUP ATTR", "",
            "operation to update a authentication provider's group attribute.  This may affect existing tenants, project acls and role assignments."),
    DELETE_AUTHPROVIDER("DELETE AUTH PROVIDER", "", "operation to delete a authentication provider."),
    CREATE_PROTECTION_SYSTEM("CREATE PROTECTION SYSTEM", "", "operation to create a protection system."),
    UPDATE_PROTECTION_SYSTEM("UPDATE PROTECTION SYSTEM", "", "operation to update a protection system."),
    DELETE_PROTECTION_SYSTEM("DELETE PROTECTION SYSTEM", "", "operation to delete a protection system."),

    CREATE_APPROVAL("CRAETE APPROVAL", "", "operation to create an approval"),
    UPDATE_APPROVAL("UPDATE APPROVAL", "", "operation to update an approval"),
    DELETE_APPROVAL("DELETE APPROVAL", "", "operation to delete an approval"),

    CREATE_CATALOG_CATEGORY("CRAETE CATALOG CATEGORY", "", "operation to create a catalog category"),
    UPDATE_CATALOG_CATEGORY("UPDATE CATALOG CATEGORY", "", "operation to update a catalog category"),
    DELETE_CATALOG_CATEGORY("DELETE CATALOG CATEGORY", "", "operation to delete a catalog category"),
    MODIFY_CATALOG_CATEGORY_ACL("MODIFY CATALOG CATEGORY ACL", "", "operation to modify a catalog category acls"),

    CREATE_CATALOG_IMAGE("CRAETE CATALOG IMAGE", "", "operation to create a catalog image"),
    UPDATE_CATALOG_IMAGE("UPDATE CATALOG IMAGE", "", "operation to update a catalog image"),
    DELETE_CATALOG_IMAGE("DELETE CATALOG IMAGE", "", "operation to delete a catalog image"),

    CREATE_CATALOG_SERVICE("CRAETE CATALOG SERVICE", "", "operation to create a catalog service"),
    UPDATE_CATALOG_SERVICE("UPDATE CATALOG SERVICE", "", "operation to update a catalog service"),
    DELETE_CATALOG_SERVICE("DELETE CATALOG SERVICE", "", "operation to delete a catalog service"),
    MODIFY_CATALOG_SERVICE_ACL("MODIFY CATALOG SERVICE ACL", "", "operation to modify a catalog service acls"),

    CREATE_EXECUTION_WINDOW("CRAETE EXECUTION WINDOW", "", "operation to create a execution window"),
    UPDATE_EXECUTION_WINDOW("UPDATE EXECUTION WINDOW", "", "operation to update a execution window"),
    DELETE_EXECUTION_WINDOW("DELETE EXECUTION WINDOW", "", "operation to delete a execution window"),

    UPDATE_CATALOG_PREFERENCES("UPDATE CATALOG PREFERENCES", "", "operation to update catalog preferences"),
    UPDATE_USER_PREFERENCES("UPDATE USER PREFERENCES", "", "operation to update user preferences"),

    CREATE_ORDER("CRAETE ORDER", "", "operation to create a order"),
    UPDATE_ORDER("UPDATE ORDER", "", "operation to update a order"),
    DELETE_ORDER("DELETE ORDER", "", "operation to delete a order"),

    CREATE_HOST("CREATE HOST", "", "operation to create a compute host."),
    UPDATE_HOST("UPDATE HOST", "", "operation to update a compute host."),
    DELETE_HOST("DELETE HOST", "", "operation to delete a compute host."),
    DETACH_HOST_STORAGE("DELETE HOST STORAGE", "", "operation to detach storage from a compute host."),

    CREATE_CLUSTER("CREATE CLUSTER", "", "operation to create a cluster."),
    UPDATE_CLUSTER("UPDATE CLUSTER", "", "operation to update a cluster."),
    DELETE_CLUSTER("DELETE CLUSTER", "", "operation to delete a cluster."),
    DETACH_CLUSTER_STORAGE("DELETE CLUSTER STORAGE", "", "operation to detach storage from a cluster."),

    CREATE_VCENTER("CREATE VCENTER", "", "operation to create a vcenter."),
    UPDATE_VCENTER("UPDATE VCENTER", "", "operation to update a vcenter."),
    DELETE_VCENTER("DELETE VCENTER", "", "operation to delete a vcenter."),
    DETACH_VCENTER_STORAGE("DELETE VCENTER", "", "operation to detach storage from a vcenter."),

    CREATE_VCENTER_DATACENTER("CREATE VCENTER DATACENTER", "", "operation to create a vcenter data center."),
    UPDATE_VCENTER_DATACENTER("UPDATE VCENTER DATACENTER", "", "operation to update a vcenter data center."),
    DELETE_VCENTER_DATACENTER("DELETE VCENTER DATACENTER", "", "operation to delete a vcenter data center."),
    DETACH_VCENTER_DATACENTER_STORAGE("DELETE VCENTER STORAGE", "", "operation to detach storage from a vcenter data center."),

    CREATE_HOST_INITIATOR("CREATE HOST INITIATOR", "", "operation to create a host initiator."),
    UPDATE_HOST_INITIATOR("UPDATE HOST INITIATOR", "", "operation to update a host initiator."),
    DELETE_HOST_INITIATOR("DELETE HOST INITIATOR", "", "operation to delete a host initiator."),

    CREATE_HOST_IPINTERFACE("CREATE HOST IPINTERFACE", "", "operation to create a host ip interface."),
    UPDATE_HOST_IPINTERFACE("UPDATE HOST IPINTERFACE", "", "operation to update a host ip interface."),
    DELETE_HOST_IPINTERFACE("DELETE HOST IPINTERFACE", "", "operation to delete a host ip interface."),
    DEREGISTER_HOST_IPINTERFACE("UNREGISTER HOST IPINTERFACE", "", "operation to unregister a host ip interface."),
    REGISTER_HOST_IPINTERFACE("REGISTER HOST IPINTERFACE", "", "operation to register a host ip interface."),

    CREATE_COMPUTE_SYSTEM("CREATE COMPUTE SYSTEM", "", "operation to create a compute system."),
    UPDATE_COMPUTE_SYSTEM("UPDATE COMPUTE SYSTEM", "", "operation to update a compute system."),
    REGISTER_COMPUTE_SYSTEM("REGISTER COMPUTE SYSTEM", "", "operation to register a compute system."),
    DEREGISTER_COMPUTE_SYSTEM("UNREGISTER COMPUTE SYSTEM", "", "operation to unregister a compute system."),
    DELETE_COMPUTE_SYSTEM("DELETE COMPUTE SYSTEM", "", "operation to delete a compute system."),

    REGISTER_COMPUTE_ELEMENT("REGISTER COMPUTE ELEMENT", "", "operation to register a compute element."),
    DEREGISTER_COMPUTE_ELEMENT("UNREGISTER COMPUTE ELEMENT", "", "operation to unregister a compute element."),

    CREATE_COMPUTE_IMAGE("CREATE COMPUTE IMAGE", "", "operation to create a compute image."),
    UPDATE_COMPUTE_IMAGE("UPDATE COMPUTE IMAGE", "", "operation to update a compute image."),
    DELETE_COMPUTE_IMAGE("DELETE COMPUTE IMAGE", "", "operation to delete a compute image."),
    INSTALL_COMPUTE_IMAGE("INSTALL COMPUTE IMAGE", "", "operation to install a compute image."),

    POWERUP_COMPUTE_ELEMENT("POWERUP COMPUTE ELEMENT", "POWERUP COMPUTE ELEMENT_FAILED", "operation to power-up a compute element."),
    POWERDOWN_COMPUTE_ELEMENT("POWERDOWN COMPUTE ELEMENT", "POWERDOWN COMPUTE ELEMENT_FAILED", "operation to power-down a compute element."),

    CREATE_COMPUTE_VPOOL("CREATE COMPUTE VPOOL", "", "operation to create a virtual compute pool"),
    UPDATE_COMPUTE_VPOOL("UPDATE COMPUTE VPOOL", "", "operation to update a virtual compute pool"),
    DELETE_COMPUTE_VPOOL("DELETE COMPUTE VPOOL", "", "operation to delete a virtual compute pool"),
    CREATE_UPDATE_VCENTER_CLUSTER("CREATE UPDATE VCENTER CLUSTER", "", "create or update a cluster in vCenter server"),

    SSH_LOGIN("SSH LOGIN", "", "ssh login."),
    AUTHENTICATION("AUTHENTICATION", "", "authentication"),
    UPDATE_VERSION("UPDATE VERSION", "", "operation to upgrade."),
    INSTALL_IMAGE("INSTALL IMAGE", "", "operation to upload a image from remote server."),
    REMOVE_IMAGE("REMOVE IMAGE", "", "operation to remove a image."),
    UPLOAD_IMAGE("UPLOAD IMAGE", "", "operation to uploade a image from remote server."),
    WAKEUP_UPGRAGE_MANAGER("WAKEUP UPGRADE MANAGER", "", "operation to wakeup upgrade manager."),
    UPDATE_SYSTEM_PROPERTY("UPDATE SYSTEM PROPERTY", "", "operation to update system property."),
    SEND_ALERT("SEND ALERT", "", "operation to send a alert."),
    SEND_REGISTRATION("SEND ALERT", "", "operation to send a registration."),
    SEND_HEARTBEAT("SEND HEARTBEAT", "", "operation to send heartbeat signal."),
    SEND_STAT("SEND STAT", "", "operation to send a state"),
    SEND_LICENSE_EXPIRED("SEND LICENSE EXPIRED", "", "operation to inform license expiration."),
    SEND_CAPACITY_EXCEEDED("SEND CAPACITY EXCEEDED", "", "operation to inform of exceeding storage capacity."),
    ADD_LICENSE("ADD LICENSE", "", "operation to add license to system."),
    CREATE_ESRS_CONFIGURATION("CREATE ESRS CONFIGURATION", "", "operation to create esrs configuration."),
    SCHEDULE_EVENT("SCHEDULE EVENT", "", "operation to schedule event."),
    CHANGE_LOCAL_AUTHUSER_PASSWORD("CHANGE LOCAL AUTHUSER PASSWORD", "", "operation to change local authuser password."),
    RESET_LOCAL_USER_PASSWORD("RESET LOCAL AUTHUSER PASSWORD", "", "operation to reset local authuser password."),
    CHANGE_LOCAL_AUTHUSER_AUTHKEY("CHANGE LOCAL AUTHUSER AUTHKEY", "", "operation to change local authuser authkey."),
    RESTART_SERVICE("RESTART SERVICE", "", "operation to restart a service."),
    REBOOT_NODE("REBOOT NODE", "", "operation to reboot a node."),
    POWER_OFF_CLUSTER("POWEROFF CLUSTER", "", "operation to poweroff a cluster."),
    POWER_OFF_NODE("POWEROFF NODE", "", "operation to poweroff a node"),
    CREATE_CAS_POOL("CREATE CAS POOL", "", "operation to create cas pool."),    // centera specific
    UPDATE_CAS_POOL("UPDATE CAS POOL", "", "operation to update cas pool."),
    CREATE_CAS_PROFILE("CREATE CAS PROFILE", "", "operation to create cas profile."),
    UPDATE_CAS_PROFILE("UPDATE CAS PROFILE", "", "operation to update cas profile."),
    CREATE_CAS_CLUSTER("CREATE CAS CLUSTER", "", "operation to create cas cluster."),
    UPDATE_CAS_CLUSTER("UPDATE CAS CLUSTER", "", "operation to update cas cluster."),
    ADD_VDC("ADD VDC", "", "operation to add a vdc to ViPR"),
    REMOVE_VDC("REMOVE VDC", "", "operation to remove a vdc from ViPR"),
    DISCONNECT_VDC("DISCONNECT VDC", "", "operation to temporarily disconnect a vdc from ViPR"),
    RECONNECT_VDC("RECONNECT VDC", "", "operation to reverse a disconnect vdc operation"),
    UPDATE_VDC("UPDATE VDC", "", "operation to update a vdc in ViPR"),
    PREPARE_VDC("PREPARE VDC", "",
            "operation to prepare a vdc for GEO scenario. Root user TENANT_ADMIN and project ownerships will be removed."),
    SET_KEY_AND_CERTIFICATE("SET KEY AND CERTIFICATE", "", "operation to set ViPR's key and certificate"),
    REGENERATE_KEY_AND_CERTIFICATE("REGENERATE KEY AND CERTIFICATE", "", "operation to regenerate ViPR's key and certificate"),
    UPDATE_TRUSTED_CERTIFICATES("UPDATE TRUSTED CERTIFICATES", "", "operation to update ViPR's trusted certificates"),
    UPDATE_TRUSTED_CERTIFICATES_PARTIAL("UPDATE TRUSTED CERTIFICATES PARTIAL SUCCESS", "",
            "operation to update ViPR's trusted certificates succeeded partially"),
    UPDATE_TRUSTSTORE_SETTINGS("UPDATE TRUSTSTORE SETTINGS", "", "operation to update ViPR's trust store settings"),
    ADD_ALIAS("ADD ALIAS", "", "operation to add one or more aliases."),
    REMOVE_ALIAS("REMOVE ALIAS", "", "operation to remove one or more aliases."),
    UPDATE_ALIAS("UPDATE ALIAS", "", "operation to update one or more aliases."),
    DELETE_TASK("DELETE TASK", "", "Delete a Task"),
    SEND_PASSWORD_TO_BE_EXPIRE_MAIL("SEND PASSWORD TO BE EXPIRED MAIL SUCCESS", "SEND PASSWORD TO BE EXPIRED MAIL FAIL",
            "operation to send password to be expired mail"),
    DELETE_CONFIG("DELETE CONFIG", "", "operation to delete controller config"),
    CREATE_CONFIG("CREATE CONFIG", "", "operation to create controller config"),
    UPDATE_CONFIG("UPDATE CONFIG", "", "operation to update controller config"),
    REGISTER_CONFIG("REGISTER CONFIG", "", "operation to register controller config"),
    DEREGISTER_CONFIG("DEREGISTER CONFIG", "", "operation to deregister controller config"),
    UPDATE_FILE_SYSTEM_SHARE_ACL("UPDATE FILE SHARE ACL", "", "operation to update filesystem share ACL"),
    UPDATE_FILE_SNAPSHOT_SHARE_ACL("UPDATE SNAPSHOT SHARE ACL", "", "operation to update snapshot share ACL"),
    DELETE_FILE_SYSTEM_SHARE_ACL("DELETE FILE SHARE ACL", "", "operation to delete filesystem share ACL"),
    DELETE_FILE_SNAPSHOT_SHARE_ACL("DELETE SNAPSHOT SHARE ACL", "", "operation to delete snapshot share ACL"),
    UPDATE_FILE_SYSTEM_NFS_ACL("UPDATE FILE NFS ACL", "", "operation to update filesystem nfs ACL"),
    UPDATE_FILE_SNAPSHOT_NFS_ACL("UPDATE SNAPSHOT NFS ACL", "", "operation to update snapshot nfs ACL"),
    DELETE_FILE_SYSTEM_NFS_ACL("DELETE FILE NFS ACL", "", "operation to delete filesystem nfs ACL"),
    DELETE_FILE_SNAPSHOT_NFS_ACL("DELETE SNAPSHOT NFS ACL", "", "operation to delete snapshot nfs ACL"),
    CREATE_BACKUP("CREATE BACKUP", "", "operation to create ViPR backup"),
    UPLOAD_BACKUP("UPLOAD BACKUP", "", "operation to upload ViPR backup to external location"),
    RECOVER_NODES("RECOVER NODES", "", "operation to recover corrupted nodes"),
    RECONFIG_IP("Reconfig IPs", "", "trigger ip reconfiguration"),
    CREATE_USERGROUP("CREATE USER GROUP", "", "operation to create a user group."),
    UPDATE_USERGROUP("UPDATE USER GROUP", "", "operation to update a user group."),
    DELETE_USERGROUP("DELETE USER GROUP", "", "operation to delete a user group."),
    ADD_JOURNAL_VOLUME("ADD JOURNAL VOLUME", "", "operation to add a journal volume"),
    ArrayGeneric("", "", ""),
    IMAGESERVER_VERIFY_IMPORT_IMAGES("IMAGESERVER_VERIFY_IMPORT_IMAGES", "",
            "operation to verify a compute image server and import images."),
    UPDATE_VERIFY_COMPUTE_IMAGESERVER("UPDATE AND VERIFY COMPUTE IMAGE SERVER", "",
            "operation to update and verify a compute image server."),
    DELETE_COMPUTE_IMAGESERVER("DELETE COMPUTE IMAGE SERVER", "", "operation to delete a compute image server."), ;

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
