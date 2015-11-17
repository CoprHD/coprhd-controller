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

    public String PROJECT = "project";
    public String VIRTUAL_ARRAY = "virtualArray";
    public String VIRTUAL_POOL = "virtualPool";
    public String COMPUTE_VIRTUAL_POOL = "computeVirtualPool";
    public String NAME = "name";
    public String TYPE = "type";
    public String STORAGE_TYPE = "storageType";
    public String VOLUMES = "volumes";
    public String DELETION_TYPE = "deletionType";
    public String STORAGE_SYSTEMS = "storageSystems";
    public String PROTECTION_SYSTEMS = "protectionSystems";
    public String REMOVED_VOLUMES = "removedVolumes";
    public String SNAPSHOTS = "snapshots";
    public String FILESYSTEMS = "fileSystems";
    public String NFS_PERMISSIONS = "nfsPermissions";
    public String EXPORT_HOSTS = "exportHosts";
    public String FAILOVER_TARGET = "failoverTarget";
    public String NUMBER_OF_VOLUMES = "numberOfVolumes";
    public String NUMBER_OF_HOSTS = "numberOfHosts";
    public String COUNT = "count";
    public String VOLUME = "volume";
    public String SNAPSHOT = "snapshot";
    public String EXPORT = "export";
    public String CONSISTENCY_GROUP = "consistencyGroup";
    public String HLU = "hlu";
    public String MIN_PATHS = "minPaths";
    public String MAX_PATHS = "maxPaths";
    public String PATHS_PER_INITIATOR = "pathsPerInitiator";
    public String COPIES = "copies";
    public String INGESTION_METHOD = "ingestionMethod";
    public String READ_ONLY = "readOnly";

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
}
