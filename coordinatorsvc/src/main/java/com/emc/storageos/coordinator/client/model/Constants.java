/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

import java.util.regex.Pattern;

public interface Constants {

    public static final String SOFTWARE_IMAGE_SUFFIX = ".img";

    public static final String DOWNLOAD_DIR = "/tmp/downloads";

    public static final String LINE_DELIMITER = "\n";

    public static final String REMOTE_DOWNLOAD_LEADER = "remoteDownloadLeader";
    public static final String TARGET_INFO = "targetInfo";
    public static final String NODE_INFO = "nodeInfo";

    public static final String TARGET_INFO_LOCK = "targetInfoLock";
    public static final String REMOTE_DOWNLOAD_LOCK = "remoteDownloadLock";
    public static final String NEW_VERSIONS_LOCK = "newVersionsLock";
    public static final String DISTRIBUTED_UPGRADE_LOCK = "controlNodeUpgradeLock";
    public static final String DISTRIBUTED_PROPERTY_LOCK = "controlNodePropertyLock";

    // service config constants
    // category name under which upgrade target configurations are stored
    public static final String VDC_PROPERTY_DIR = "/tmp/vdcconfig.properties.new";
    public static final String SSL_PROPERTY_TMP = "/tmp/sslconfig.properties.new";
    public static final String TMP_CONFIG_USER_CHANGED_PROPS_PATH = "/tmp/config-override.properties";
    public static final String TMP_CONFIG_CONTROLLER_OVF_PROPS_PATH = "/tmp/controller-ovf.properties";

    // upload image
    public static final long MAX_UPLOAD_SIZE = 800000000L;
    public static final String UPLOAD_DIR = "/tmp/uploads";

    public static final int TRAILER_SHA_OFFSET = 0;
    public static final int TRAILER_SHA_SIZE = 20;

    public static final int TRAILER_CRC_OFFSET = TRAILER_SHA_OFFSET + TRAILER_SHA_SIZE;
    public static final int TRAILER_CRC_SIZE = 4;

    public static final int TRAILER_LEN_OFFSET = TRAILER_CRC_OFFSET + TRAILER_CRC_SIZE;
    public static final int TRAILER_LEN_SIZE = 4;

    public static final int TRAILER_MAGIC_OFFSET = TRAILER_LEN_OFFSET + TRAILER_LEN_SIZE;
    public static final int TRAILER_MAGIC_SIZE = 8;
    public static final long TRAILER_MAGIC_VALUE = 0x3031656e72756f42L;

    public static final int TRAILER_LENGTH = TRAILER_SHA_SIZE + TRAILER_CRC_SIZE + TRAILER_LEN_SIZE + TRAILER_MAGIC_SIZE;
    public static final long TRAILER_LEN_VALUE = TRAILER_LENGTH;

    public static final String NODE_ID_FORMAT = "vipr%s";
    public static final String NODE_IP_PROPERTY_FORMAT = "network_%s_ipaddr";
    public static final Pattern CONTROL_NODE_ID_PATTERN = Pattern.compile("(vipr\\d+)|standalone");
    public static final Pattern CONTROL_NODE_SYSSVC_ID_PATTERN = Pattern.compile("syssvc-(\\d+|standalone)");
    public static final String HIDDEN_TEXT_MASK = "********";

    public static final String STANDALONE_ID = "standalone";

    // category name under which db configurations are stored
    public static final String DB_CONFIG = "dbconfig";

    // category name under which geodb configurations are stored
    public static final String GEODB_CONFIG = "geodbconfig";

    // service name for dbsvc
    public static final String DBSVC_NAME = "dbsvc";

    // service name for geodbsvc
    public static final String GEODBSVC_NAME = "geodbsvc";

    // special configuration id for global config kinds, like SCHEMA_VERSION
    public static final String GLOBAL_ID = "global";

    // version string of the current schema version
    public static final String SCHEMA_VERSION = "schemaversion";

    // see the defintion of ClusterInfo.MigrationStatus
    public static final String MIGRATION_STATUS = "migrationstatus";
    
    // Upgrade failure information
    public static final String  UPGRADE_FAILURE_INFO ="upgradefailureinfo";

    // category name under which external node information are stored
    public static final String NODE_DUALINETADDR_CONFIG = "nodeAddresses";
    public static final String CONFIG_DUAL_INETADDRESSES = "inetaddress";

    // geo add-vdc - reinit db flag name
    public static final String REINIT_DB = "reinitdb";

    // geo remove-vdc - obsolete cassandra peers to be removed in system table
    public static final String OBSOLETE_CASSANDRA_PEERS = "obsoletecassandrapeers";

    // category name under which backup configurations are stored
    public static final String BACKUP_CONFIG = "backupconfig";

    public static final String BACKUP_SCHEDULER_CONFIG = "backupschedulerconfig";

    // startup mode file name on disk
    public static final String STARTUPMODE = "startupmode";
    public static final String STARTUPMODE_HIBERNATE = "hibernate";
    public static final String STARTUPMODE_RESTORE_REINIT = "restorereinit";

    public static final String NODE_RECOVERY_STATUS = "recovery";

    public static final String VDC_HEART_BEATER = "vdcHeartbeater";

    // to notify portal service to update its cache after catalog acl change
    public static final String CATALOG_CONFIG = "catalog";
    public static final String CATALOG_ACL_CHANGE = "acl_change";

    // Db downtime tracker
    public static final String DB_DOWNTIME_TRACKER_CONFIG = "dbDowntimeTracker";
    public static final String  DB_CONSISTENCY_STATUS = "dbconsistencystatus";
}
