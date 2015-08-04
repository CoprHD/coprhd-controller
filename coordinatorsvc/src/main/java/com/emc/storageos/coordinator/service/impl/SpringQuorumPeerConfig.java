/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.service.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.zookeeper.server.quorum.QuorumPeer.LearnerType;
import org.apache.zookeeper.server.quorum.QuorumPeer.QuorumServer;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;

public class SpringQuorumPeerConfig extends QuorumPeerConfig {
    private static final Logger log = LoggerFactory.getLogger(SpringQuorumPeerConfig.class);
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

        // Pre-process the properties to parse and remove server.* key/value
        // In future, if _properties is still used somewhere else after init, we need to clone it keep original values
        preprocessQuorumServers(_properties);
        parseProperties(_properties);
    }

    private void preprocessQuorumServers(Properties zkProp) throws ConfigException {
        log.info("Preprocess quorum server from properties before send to ZK");

        Iterator<Entry<Object, Object>> iterator = _properties.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<Object, Object> entry = iterator.next();
            String key = entry.getKey().toString().trim();
            if (key.startsWith("server.")) {
                String value = entry.getValue().toString().trim();
                createQuorumServer(key, value);
                iterator.remove();
            }
        }
    }

    /**
     * This logic is same as ZK 3.4.6 except splitting values with "," instead of ":"
     */
    private void createQuorumServer(String key, String value) throws ConfigException {
        int dot = key.indexOf('.');
        long sid = Long.parseLong(key.substring(dot + 1));
        String parts[] = value.split(",");
        if ((parts.length != 2) && (parts.length != 3) && (parts.length != 4)) {
            log.error(value
                    + " does not have the form host,port or host,port,port " +
                    " or host,port,port,type");
        }
        InetSocketAddress addr = new InetSocketAddress(parts[0],
                Integer.parseInt(parts[1]));
        if (parts.length == 2) {
            servers.put(Long.valueOf(sid), new QuorumServer(sid, addr));
        } else if (parts.length == 3) {
            InetSocketAddress electionAddr = new InetSocketAddress(
                    parts[0], Integer.parseInt(parts[2]));
            servers.put(Long.valueOf(sid), new QuorumServer(sid, addr,
                    electionAddr));
        } else if (parts.length == 4) {
            InetSocketAddress electionAddr = new InetSocketAddress(
                    parts[0], Integer.parseInt(parts[2]));
            LearnerType type = LearnerType.PARTICIPANT;
            if (parts[3].toLowerCase().equals("observer")) {
                type = LearnerType.OBSERVER;
                observers.put(Long.valueOf(sid), new QuorumServer(sid, addr,
                        electionAddr, type));
            } else if (parts[3].toLowerCase().equals("participant")) {
                type = LearnerType.PARTICIPANT;
                servers.put(Long.valueOf(sid), new QuorumServer(sid, addr,
                        electionAddr, type));
            } else {
                throw new ConfigException("Unrecognised peertype: " + value);
            }
        }
    }
}
