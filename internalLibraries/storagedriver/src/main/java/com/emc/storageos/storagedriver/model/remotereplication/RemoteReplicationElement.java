/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;


public class RemoteReplicationElement {

    public enum ElementType {
        VOLUME,
        FILE_SYSTEM
    }

    private String nativeId;
    private String storageSystemNativeId;
    private ElementType elementType;
}
