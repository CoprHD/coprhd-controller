/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class LdapServerList {
    private static Logger log = LoggerFactory.getLogger(LdapServerList.class);

    private List<LdapOrADServer> connectedServers = new CopyOnWriteArrayList<>();
    private List<LdapOrADServer> disConnectedServers = new CopyOnWriteArrayList<>();

    public List<LdapOrADServer> getConnectedServers() {
        return connectedServers;
    }

    public List<LdapOrADServer> getDisconnectedServers() {
        return disConnectedServers;
    }

    public void add(LdapOrADServer server) {
        connectedServers.add(server);
    }

    public void markAsConnected(LdapOrADServer server) {
        connectedServers.add(server);
        disConnectedServers.remove(server);
        log.info("Change back to connected ldap server {}. Now all connected servers are {}",
                server, connectedServers);
    }

    public void markAsDisConnected(LdapOrADServer server) {
        disConnectedServers.add(server);
        connectedServers.remove(server);
        log.info("Add one disconnected ldap server {} and All disconnected servers are {}",
                server, disConnectedServers);
    }
}
