/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.common;

/**
 * Configuration keys for dbsvc
 */
public abstract class DbConfigConstants {
    // Thrift IP address
    public static final String DB_IP = "dbip";

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

    // The setting to override the num_tokens in .yaml file. The number in .yaml file is updated first, while this
    // value in ZK keeps unchanged to allow dbsvc to start without token number change. This value is set to same
    // as .yaml file after node is decommissioned.
    public static final String NUM_TOKENS_KEY = "num_tokens";

    // we change the token number from 256 to 16 in jedi, this will reduce db repair time.
    public static final Integer DEFUALT_NUM_TOKENS = 16;
}
