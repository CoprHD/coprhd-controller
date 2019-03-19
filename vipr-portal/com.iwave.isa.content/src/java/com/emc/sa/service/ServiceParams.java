/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service;

/**
 * This interface defines the parameter names for operation parameters.
 *
 * @author jonnymiller
 */
public interface ServiceParams {
    public String SIZE_IN_GB = "size";
    public String SIZE_BOOT_VOL_IN_GB = "sizeBootVols";
    public String HOST = "host";
    public String VCENTER = "vcenter";
    public String VMFS_VERSION = "vmfsVersion";
    public String DATASTORE = "datastore";
    public String DATASTORE_NAME = "datastoreName";
    public String MOUNT_POINT = "mountPoint";
    public String FILE_SYSTEM_TYPE = "fileSystemType";
    public String BLOCK_SIZE = "blockSize";
    public String BASE_NAME = "baseName";
    public String DATACENTER = "datacenter";
    public String PROTOCOL = "protocol";
    public String MULTIPATH_POLICY = "multipathPolicy";
    public String STORAGE_IO_CONTROL = "storageIOControl";

    public String WINDOWS_DOMAIN = "domain";
    public String VOLUME_NAME = "volumeName";
    public String SHARE_NAME = "shareName";
    public String EXPORT_NAME = "exportName";
    public String RW_USER_SIDS = "rwUserSids";
    public String RW_GROUP_SIDS = "rwGroupSids";

    public String SHARE_COMMENT = "shareComment";

    public String COPY_NAME = "copyName";

    public String MOBILITY_GROUP = "mobilityGroup";
    public String MOBILITY_GROUP_RESOURCES = "mobilityGroupResources";

    public String PROJECT = "project";
    public String VIRTUAL_ARRAY = "virtualArray";
    public String VIRTUAL_POOL = "virtualPool";
    public String COMPUTE_VIRTUAL_POOL = "computeVirtualPool";
    public String NAME = "name";
    public String DESCRIPTION = "description";
    public String TYPE = "type";
    public String STORAGE_TYPE = "storageType";
    public String VOLUMES = "volumes";
    public String DELETION_TYPE = "deletionType";
    public String STORAGE_SYSTEMS = "storageSystems";
    public String STORAGE_SYSTEM = "storageSystem";
    public String PROTECTION_SYSTEMS = "protectionSystems";
    public String REMOVED_VOLUMES = "removedVolumes";
    public String SNAPSHOTS = "snapshots";
    public String FILESYSTEMS = "fileSystems";
    public String FILESYSTEM = "fileSystem";
    public String FILE_COPIES = "fileCopies";
    public String FILE_POLICY = "filePolicy";
    public String FILESYSTEM_SRC_VARRAY = "sourceVirtualArray";
    public String FILESYSTEM_TRGT_VARRAY = "targetVirtualArray";
    public String NFS_PERMISSIONS = "nfsPermissions";
    public String EXPORT_HOSTS = "exportHosts";
    public String FAILOVER_TARGET = "failoverTarget";
    public String FAILOVER_TARGET_FILE = "failoverTargetFile";
    public String REPLICATE_CONFIG_FAILOVER = "replicateConfFailover";
    public String REPLICATE_CONFIG_FAILBACK = "replicateConfFailback";
    public String FAILBACK_TARGET = "failbackTarget";
    public String FAILBACK_TARGET_FILE = "failbackTargetFile";
    public String IMAGE_TO_ACCESS = "imageToAccess";
    public String POINT_IN_TIME = "pointInTime";
    public String DIRECT_ACCESS = "directAccess";
    public String NUMBER_OF_VOLUMES = "numberOfVolumes";
    public String NUMBER_OF_HOSTS = "numberOfHosts";
    public String COUNT = "count";
    public String VOLUME = "volume";
    public String SNAPSHOT = "snapshot";
    public String SNAPSHOT_SESSION = "snapshotSession";
    public String EXPORT = "export";
    public String CONSISTENCY_GROUP = "consistencyGroup";
    public String HLU = "hlu";
    public String MIN_PATHS = "minPaths";
    public String MAX_PATHS = "maxPaths";
    public String PATHS_PER_INITIATOR = "pathsPerInitiator";
    public String PORT_GROUP = "portGroup";
    public String CURRENT_PORT_GROUP = "currentPortGroup";
    public String CHANGE_PORT_GROUP = "changePortGroup";
    public String COPIES = "copies";
    public String COPY = "copy";
    public String INGESTION_METHOD = "ingestionMethod";
    public String READ_ONLY = "readOnly";
    public String MIGRATION_SUSPEND = "migrationSuspend";
    public String DISPLAY_JOURNALS = "displayJournals";
    public String PORTS = "ports";
    public String RESULTING_PATHS = "resultingPaths";
    public String REMOVED_PATHS = "removedPaths";
    public String USE_EXISTING_PATHS = "useExistingPaths";
    public String SUSPEND_WAIT = "suspendWait";

    public String MOBILITY_GROUP_METHOD = "mobilityGroupMethod";

    public String APPLICATION = "application";
    public String APPLICATION_SUB_GROUP = "applicationSubGroup";
    public String APPLICATION_COPY_SETS = "applicationCopySets";
    public String NEW_APPLICATION_SUB_GROUP = "newApplicationSubGroup";
    public String APPLICATION_SITE = "applicationSite";
    public String FULL_COPY_NAME = "fullCopyName";
    public String HIGH_AVAILABILITY = "highAvailability";

    public String APPLICATION_SNAPSHOT_TYPE = "applicationSnapshotType";
    public String TARGET_VIRTUAL_POOL = "targetVirtualPool";
    public String TARGET_VIRTUAL_ARRAY = "targetVirtualArray";

    public String DATA_STORE = "dataStoreName";
    public String DATASTORE_DESCRIPTION = "dataStoreDescription";
    public String FILE_SHARE_ID = "fileShareID";
    public String DESTINATION_BUCKET = "destinationBucket";
    public String OBJECT_ACCESS_MODE = "objectAccessMode";
    public String HDFS_ACCESS_MODE = "hdfsAccessMode";
    public String ENABLE_FILESYSTEM = "enableFileSystem";
    public String DO_FORMAT = "doFormat";
    public String PARTITION_TYPE = "partitionType";
    public String LABEL = "label";

    public String CLUSTER = "cluster";
    public String COMPUTE_IMAGE = "computeImage";
    public String FQDNS = "fqdns";
    public String HOST_IPS = "ips";
    public String NETMASK = "netmask";
    public String GATEWAY = "gateway";
    public String DNS_SERVERS = "dnsServers";
    public String PLATFORM = "platform";
    public String NTP_SERVER = "ntpServer";
    public String MANAGEMENT_NETWORK = "managementNetwork";
    public String HOST_PASSWORD = "hostPassword"; // NOSONAR ("False positive, field does not store a password")
    public String CONFIRM_PASSWORD = "confirmPassword"; // NOSONAR ("False positive, field does not store a password")

    public String MAXUSERS = "maxUsers";
    public String PERMISSIONTYPE = "permissionType";
    public String PERMISSION = "permission";
    public String SECURITY = "security";
    public String USER = "user";
    public String SUB_DIRECTORY = "subDirectory";
    public String ROOT_USER_MAPPING = "rootUserMapping";

    public String SUBDIRECTORY = "subDirectory";
    public String ALLDDIRECTORY = "allDirectory";
    public String COMMENT = "comment";
    public String OPLOCK = "oplock";
    public String SECURITY_STYLE = "securityStyle";
    public String QUOTA_DIRECTORIES = "quotaDirectories";

    public String SOFT_QUOTA = "softQuota";
    public String HARD_QUOTA = "hardQuota";
    public String RETENTION = "retention";
    public String NAMESPACE = "namespace";
    public String TENANT = "tenant";
    public String OWNER = "owner";
    public String BUCKET = "bucket";
    public String ACL_TYPE = "aclType";
    public String ACL_NAME = "aclName";
    public String ACL_DOMAIN = "aclDomain";
    public String ACL_PERMISSION = "aclPermission";

    public String ADVISORY_LIMIT = "advisoryLimit";
    public String SOFT_LIMIT = "softLimit";
    public String GRACE_PERIOD = "gracePeriod";

    public String DELETE_TARGET = "deleteTarget";

    public String LINKED_SNAPSHOT = "linkedSnapshot";
    public String LINKED_SNAPSHOT_NAME = "linkedSnapshotName";
    public String LINKED_SNAPSHOT_COUNT = "linkedSnapshotCount";
    public String LINKED_SNAPSHOT_COPYMODE = "linkedSnapshotCopyMode";

    public String MOUNT_PATH = "mountPath";
    public String SECURITY_TYPE = "securityType";
    public String MOUNTED_NFS_EXPORTS = "mountedNFSExports";
    public String FILESYSTEM_NAME = "fileSystemName";
    public String MOUNT_LIST = "mountList";
    public String FS_TYPE = "fsType";
 
    public String ARTIFICIAL_FAILURE = "artificialFailure";
    public String BYPASS_DNS_CHECK = "bypassDnsCheck";
}
