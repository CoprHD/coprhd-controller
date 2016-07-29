/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;

import java.util.List;

/**
 * This class describes operational object for remote link operations.
 * All operations on the remote replication link is instance of this class as parameter.
 */
public class RemoteReplicationArgument {

    /**
     * Remote replication element type of this argument. Type: Input.
     */
    private RemoteReplicationSet.ElementType type;

    /**
     *  Device nativeId of replication element for this argument.
     *  Depending on argument type, this can be nativeId of
     *  replication set, replication group or replication pair. Type: Input.
     */
    private String nativeId;

    /**
     * Parent native id of remote replication element of this argument.
     * Depending on element type and containment can be either nativeId of parent replication group
     * or parent replication set. Type: Input.
     */
    private String parentNativeId;

    /**
     * Device specific capabilities. Type: Input.
     */
    private List<CapabilityInstance> capabilities;
}
