/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;


public class RemoteReplicationElement {

    public static enum ElementType {
        VOLUME,
        CONSISTENCY_GROUP
    }

    private String nativeId;
    private String storageSystemNativeId;
    private ElementType elementType;
}
