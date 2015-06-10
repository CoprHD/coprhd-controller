/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.impl;

import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.YamlConfigurationLoader;
import org.apache.cassandra.exceptions.ConfigurationException;

import java.net.URL;

/**
 * This class can modify the configuration just after it's read from disk, based on
 * information stored elsewhere, e.g. ZooKeeper.
 * */
public class CassandraConfigLoader extends YamlConfigurationLoader {

    public static final String SYSPROP_NUM_TOKENS = "storageos.dbsvc.num_tokens";

    @Override
    public Config loadConfig(URL url) throws ConfigurationException {
        Config cfg = super.loadConfig(url);

        String numTokensEffective = System.getProperty(SYSPROP_NUM_TOKENS);
        if (numTokensEffective != null) {
            cfg.num_tokens = Integer.parseInt(numTokensEffective);
        }

        return cfg;
    }
}
