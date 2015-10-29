/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.metering.vnxfile;

/**
 * Constants defined for VNX File.
 */
public class VNXFileConstants {

    public static final String DEVICETYPE = "deviceType";
    public static final String URI = "uri";

    public static final String ID = "id";
    public static final String COLON = ":";
    public static final String DBCLIENT = "dbClient";
    public static final String STATS = "Stats";
    public static final String FILE = "file";
    public static final String USERNAME = "username";
    public static final String USER_PASS_WORD = "password";
    public static final String AUTHURI = "authuri";
    public static final String COOKIE = "Cookie";
    public static final String CELERRA_SESSION = "CelerraConnector-Sess";
    public static final String PROTOCOL = "protocol";
    public static final String PORTNUMBER = "portnumber";

    public static final String SNAPSHOTINFO = "snapshotinfo";
    public static final String PLUS_SEPERATOR = "\\+";
    public static final String TIME = "time";

    public static final String CASSANDRAINSERTION = "cassandraInsertion";
    public static final String NATIVEGUIDS = "nativeGUIDs";
    public static final String MOVERLIST = "movers";
    public static final String VDMLIST = "vdms";
    public static final String ISVDM = "isVdm";
    public static final String DATAMOVER_MAP = "datamover_map";
    public static final String DATAMOVER_NAME = "datamover_name";
    public static final String TASK_DESCRIPTION = "taskDescription";
    public static final String DATAMOVER_ID = "datamover_id";
    public static final String ROOT_USER_MAPPING = "root_user_mapping";
    public static final String USER_INFO = "user_accnt_info";
    public static final String ROOT_ANON_USER = "0";

    public static final String VOLFILESHAREMAP = "volfilemap";
    public static final String STORAGEPOOLS = "storagePools";
    public static final String CONTROL_STATION_INFO = "controlStationInfo";
    public static final String STORAGE_PORT_GROUPS = "portGroups";
    public static final String STORAGE_PORTS = "ports";
    public static final String INTREFACE_PORT_MAP = "logicalNetworkDevMap";
    public static final String LOGICAL_NETWORK_SPEED_MAP = "portSpeedMap";
    public static final String FILESYSTEMS = "fileSystems";
    public static final String FILE_EXPORTS = "fileExports";
    public static final String VNX_FILE_SYSTEM_DISCOVERY = "vnxfileSystem";
    public static final String VNX_FILE_DM_PORTS = "vnxfileDMPorts";
    public static final String VNX_FILE_SELECTED_FS = "vnxfileSelectedFileSystem";
    public static final String VNX_FILE_CIFS_CONFIG = "vnxfileCifsConfig";
    public static final String VDM_INFO = "vdmInfo";

    // Constants related to Quota Tree
    public static final String QUOTA_DIR_NAME = "qtree_name";
    public static final String QUOTA_DIR_ID = "qtree_id";
    public static final String QUOTA_DIR_PATH = "qtree_path";
    public static final String HARD_QUOTA = "hard_quota";
    public static final String SOFT_QUOTA = "soft_quota";
    public static final String FILEHARD_QUOTA = "file_hard_limit";
    public static final String FILESOFT_QUOTA = "file_soft_limit";
    public static final String SECURITY_STYLE = "security_style";
    public static final String OPLOCKS = "oplocks";
    public static final String QTREE_FORCE_DELETE = "force_delete";
    public static final String QUOTA_DIR_LIST = "quota_dir_list";

    public static final Integer VNX_FILE_BATCH_SIZE = 100;
    public static final String RO = "ro";
    public static final String RW = "rw";
    public static final String ACCESS = "access";
    public static final String ROOT = "root";
    public static final String ANON = "anon";
    public static final String SECURITY_TYPE = "sec";
    public static final String NOBODY = "nobody";
    public static final String HOST_SEPARATORS = "[,:\n]";
    public static final String SECURITY_SEPARATORS = "[,:\n]";

    public static final String FILESYSTEM_NAME = "filesystem_name";
    public static final String FILESYSTEM_ID = "filesystem_id";
    public static final String FILESYSTEM = "filesystem";
    public static final String IS_FILESYSTEM_AVAILABLE_ON_ARRAY = "filesystem_availability";
    public static final String FS_INIT_SIZE = "filesystem_size";
    public static final String SNAPSHOT_NAME = "snapshot_name";
    public static final String SNAPSHOT_ID = "snapshot_id";
    public static final String SNAPSHOTS_LIST = "snapshot_list";
    public static final String MOUNT_PATH = "mount_path";
    public static final String POOL_NAME = "poolname";
    public static final String MOVER_NAME = "mover_name";
    public static final String MOVER_ID = "mover";
    public static final String VDM_NAME = "vdm_name";
    public static final String VDM_ID = "vdm";
    public static final String FILESYSTEM_SIZE = "extendedSize";
    public static final String FILESYSTEM_VIRTUAL_PROVISIONING = "virtualProvisioning";
    public static final String CIFS_SUPPORTED = "cifs_supported";
    public static final String CIFS_SERVERS = "cifs_servers";
    public static final String ORIGINAL_FS_SIZE = "originalFSSize";
    public static final String THIN_FS_ALLOC_SIZE = "thinFsAllocSize";

    public static final String FAULT_DESC = "fault description";
    public static final String FAULT_DIAG = "fault diagnostics";
    public static final String FAULT_MSG = "fault message";
    public static final String CMD_RESULT = "command result";
    public static final String CMD_SUCCESS = "command success";
    public static final String CMD_FAILURE = "command failure";

    public static final String SHARE_NAME = "share";
    public static final String SHARE_COMMENT = "comment";
    public static final String SHARE_UMASK = "umask";
    public static final String SHARE_MAXUSR = "maxusr";
    public static final String SHARE_NETBIOS = "netbios";

    // for totalcapacity filesystems on vdm
    public static final String FILE_CAPACITY_MAP = "filesystemCapacity";
    public static final String SNAP_CAPACITY_MAP = "snapshotCapacity";

}
