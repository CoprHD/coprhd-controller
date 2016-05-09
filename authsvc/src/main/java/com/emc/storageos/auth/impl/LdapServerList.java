/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.impl;

import java.util.ArrayList;
import java.util.List;

public class LdapServerList {
    ArrayList<LdapOrADServer> connectedServers = new ArrayList<>();
    ArrayList<LdapOrADServer> disConnectedServers = new ArrayList<>();

    public List<LdapOrADServer> getConnectedServers() {
        return connectedServers;
    }

    public List<LdapOrADServer> getDisconnectedServers() {
        return disConnectedServers;
    }

    public void add(LdapOrADServer server) {
        connectedServers.add(server);
    }

    public void updateWithConnected(LdapOrADServer server) {
        connectedServers.remove(server);
        disConnectedServers.add(server);
    }

    public void updateWithDisConnected(LdapOrADServer server) {
        connectedServers.add(server);
        disConnectedServers.remove(server);
    }
}
