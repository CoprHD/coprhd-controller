/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

public class SnapshotUpdateRequest {
    public Snapshot snapshot;

    public class Snapshot {
        public String name;
        public String description;
    }
}
