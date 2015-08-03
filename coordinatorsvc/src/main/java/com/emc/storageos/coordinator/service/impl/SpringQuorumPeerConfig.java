/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.service.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

import org.apache.zookeeper.server.quorum.QuorumPeerConfig;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;

public class SpringQuorumPeerConfig extends QuorumPeerConfig {
    private static final String SERVER_ID_FILE = "myid";

    private int _id;
    private Properties _properties;

    public void setMachineId(int id) {
        _id = id;
    }

    public void setProperties(Properties properties) {
        _properties = properties;
    }

    public void init() throws ConfigException, IOException {
        dataDir = (String) _properties.get("dataDir");

        // emit server id file
        File serverIdDir = new File(dataDir);
        if (!serverIdDir.exists()) {
            if (!serverIdDir.mkdirs()) {
                throw CoordinatorException.fatals
                        .unableToCreateServerIDDirectories(serverIdDir.getAbsolutePath());
            }
        }

        File serverId = new File(dataDir, SERVER_ID_FILE);
        if (!serverId.exists()) {
            FileWriter writer = new FileWriter(serverId);
            writer.write(Integer.toString(_id));
            writer.close();
        }

        parseProperties(_properties);
    }
}
