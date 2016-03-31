/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.vnx;

import java.net.URI;

import com.emc.storageos.volumecontroller.impl.smis.AbstractReplicaOperations;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

public class VnxReplicaOperations extends AbstractReplicaOperations {
    @Override
    protected int getSyncType(URI uri) {
        return SmisConstants.MIRROR_VALUE; // only mirror is supported
    }
}
