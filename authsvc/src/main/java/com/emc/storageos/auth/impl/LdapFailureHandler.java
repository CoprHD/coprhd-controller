/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.impl;

import com.emc.storageos.auth.impl.LdapOrADServer;
import com.emc.storageos.auth.impl.LdapServerList;

import java.util.ArrayList;
import java.util.List;

public class LdapFailureHandler {
    /**
     * Called when any failure happens
     */
    public void handle(LdapServerList allServers, LdapOrADServer disconnectedServer) {
        allServers.markAsDisConnected(disconnectedServer);
    }
}
