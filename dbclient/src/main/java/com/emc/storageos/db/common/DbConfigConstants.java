/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common;

/**
 * Configuration keys for dbsvc
 */
public abstract class DbConfigConstants {

    // whether this dbsvc has been autobootstrapped or not
    public static final String AUTOBOOT = "autoboot";

    // whether this node has joined a cluster or not
    public static final String JOINED = "joined";

    // client barrier - db clients won't start unless this flag is set
    public static final String INIT_DONE = "initdone";

    // whether this node is ready for db migration
    public static final String MIGRATION_INIT = "migrationinit";

    // migration checkpoint - last executing migration callback
    public static final String MIGRATION_CHECKPOINT = "checkpoint";

    // Node id(host name) to replace explicit node ip
    public static final String NODE_ID = "nodeid";

    // whether this node is ready for reset db
    public static final String RESET_INIT = "resetinit";

    // whether this node is ready for reset db
    public static final String NODE_BLACKLIST = "nodeblacklist";

    // The transport factory used to setup SSL connection used by Cassandra CliMain
    public static final String SSLTransportFactoryName = "org.apache.cassandra.thrift.SSLTransportFactory";

    public static final String DEFAULT_VDC_DB_VERSION = "2.2";
    public static final String VERSION_PART_SEPERATOR = ".";

    // Timestamp for the last data sync with the acitve site
    public static final String LAST_DATA_SYNC_TIME = "lastDataSyncTime";

    // Default GC grace period
    public static final int DEFAULT_GC_GRACE_PERIOD = 5 * 24 * 60 * 60; // 5 days, in seconds
    
    public static final String DB_SCHEMA_LOCK = "dbschema";
    public static final String GEODB_SCHEMA_LOCK = "geodbschema";
}
