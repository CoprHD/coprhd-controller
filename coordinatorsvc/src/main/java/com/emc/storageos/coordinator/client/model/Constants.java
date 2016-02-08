/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

import java.util.regex.Pattern;

import com.emc.storageos.services.util.FileUtils;

public interface Constants {

    public static final String SOFTWARE_IMAGE_SUFFIX = ".img";

    public static final String DOWNLOAD_DIR = FileUtils.generateTmpFileName("downloads");

    public static final String LINE_DELIMITER = "\n";

    public static final String REMOTE_DOWNLOAD_LEADER = "remoteDownloadLeader";
    public static final String TARGET_INFO = "targetInfo";
    String DOWNLOADINFO_KIND = "downloadinfo";
    public static final String NODE_INFO = "nodeInfo";

    public static final String TARGET_INFO_LOCK = "targetInfoLock";
    public static final String REMOTE_DOWNLOAD_LOCK = "remoteDownloadLock";
    public static final String NEW_VERSIONS_LOCK = "newVersionsLock";
    public static final String DISTRIBUTED_UPGRADE_LOCK = "controlNodeUpgradeLock";
    public static final String DISTRIBUTED_REBOOT_LOCK = "controlNodeRebootLock";

    // service config constants
    // category name under which upgrade target configurations are stored
    public static final String VDC_PROPERTY_DIR = FileUtils.generateTmpFileName("vdcconfig.properties.new");
    public static final String SSL_PROPERTY_TMP = FileUtils.generateTmpFileName("sslconfig.properties.new");
    public static final String TMP_CONFIG_USER_CHANGED_PROPS_PATH = FileUtils.generateTmpFileName("config-override.properties");
    public static final String TMP_CONFIG_CONTROLLER_OVF_PROPS_PATH = FileUtils.generateTmpFileName("controller-ovf.properties");
    public static final String DATA_REVISION_TMP = FileUtils.generateTmpFileName("datarevisionconfig.properties.new");
    public static final String KEY_DATA_REVISION = "target_data_revision";
    public static final String KEY_DATA_REVISION_COMMITTED = "target_data_revision_committed";

    //ipsec command constants
    public static final String VDC_CONFIG_VERSION = "vdc_config_version";
    public static final String IPSEC_KEY = "ipsec_key";
    public static final String IPSEC_STATUS = "ipsec_status";
    public static final String IPSEC_CHECK_CONNECTION = "check-connection";
    public static final String IPSEC_GET_PROPS = "get-props";
    public static final String IPSEC_SYNC_KEY = "sync-key";
    public static final String IPSEC_SYNC_STATUS = "sync-status";

            
    // upload image
    public static final long MAX_UPLOAD_SIZE = 800000000L;
    public static final String UPLOAD_DIR = FileUtils.generateTmpFileName("uploads");

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
    
    public static final String CONFIG_DR_ACTIVE_KIND = "disasterRecoveryActive";
    public static final String CONFIG_DR_ACTIVE_ID = "global";
    public static final String CONFIG_DR_ACTIVE_SITEID = "siteId";

    String CONFIG_GEO_LOCAL_VDC_KIND = "geoLocalVDC";
    String CONFIG_GEO_LOCAL_VDC_ID = "global";
    String CONFIG_GEO_LOCAL_VDC_SHORT_ID = "vdcShortId";
    
    public static final String CONFIG_GEO_FIRST_VDC_SHORT_ID = "vdc1";
    public static final String CONFIG_DR_FIRST_SITE_SHORT_ID = "site1";
    
    public static final String SITE_STATE = "state";
    public static final String SITE_ID_FILE= "site_id_file";
    
    public static final String KEY_CERTIFICATE_PAIR_CONFIG_KIND = "keyCertificatePairConfig";
    public static final String ZK_OBSERVER_CONFIG_SUFFIX= ":2888:2889:observer;2181";
    public static final String MY_VDC_ID_KEY= "vdc_myid";
    public static final String VDC_NODECOUNT_KEY_TEMPLATE= "vdc_%s_node_count";
    public static final String ZK_SERVER_CONFIG_PREFIX= "server.";
    public static final String STANDBY_PROPERTY_REGEX=".*standby\\d_network_\\d_ipaddr6?";
    
    public static final String SWITCHOVER_BARRIER_SET_STATE_TO_SYNCED = "switchoverBarrierSetStateToSynced";
    public static final String SWITCHOVER_BARRIER_SET_STATE_TO_STANDBY_SWITCHINGOVER = "switchoverBarrierSetStateToStandbySwitchingOver";
    public static final String SWITCHOVER_BARRIER_SET_STATE_TO_ACTIVE = "switchoverBarrierSetStateToActive";
    public static final String SWITCHOVER_BARRIER_STANDBY_RESTART_OLD_ACTIVE = "switchoverStandbySiteRemoveBarrier";
    public static final String SWITCHOVER_BARRIER_RESTART = "switchoverRestartBarrier";
    public static final String RESUME_BARRIER_RESTART_DBSVC = "resumeRestartDbsvcBarrier";
    public static final String FAILOVER_BARRIER = "failoverBarrier";
    public static final String DB_DOWNTIME_TRACKER_CONFIG = "dbDowntimeTracker";
    public static final String DB_CONSISTENCY_STATUS = "dbconsistencystatus";
    public static final String FAILBACK_DETECT_LEADER = "failbackDetectLeader";
}
