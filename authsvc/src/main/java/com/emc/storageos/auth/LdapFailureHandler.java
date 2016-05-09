/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth;

import com.emc.storageos.auth.impl.ImmutableAuthenticationProviders;
import com.emc.storageos.auth.impl.LdapOrADServer;
import com.emc.storageos.auth.impl.LdapServerList;

import java.util.ArrayList;

public class LdapFailureHandler {
    /**
     * Called when any failure happens
     */
    public void handle(LdapServerList allServers, ArrayList<LdapOrADServer> failedServers) {
        for (LdapOrADServer failedServer : failedServers) {
            // Handler and DAO instance share one instance of the server list, so they both know the change made here
            allServers.updateWithDisConnected(failedServer);
        }
    }
}
