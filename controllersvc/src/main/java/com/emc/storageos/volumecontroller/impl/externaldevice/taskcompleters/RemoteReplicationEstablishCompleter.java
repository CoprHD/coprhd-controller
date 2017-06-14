/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters;

import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;

@SuppressWarnings("serial")
public class RemoteReplicationEstablishCompleter extends AbstractRemoteReplicationOperationCompleter {

    public RemoteReplicationEstablishCompleter(RemoteReplicationElement remoteReplicationElement, String opId) {
        super(remoteReplicationElement, opId);
    }

}
