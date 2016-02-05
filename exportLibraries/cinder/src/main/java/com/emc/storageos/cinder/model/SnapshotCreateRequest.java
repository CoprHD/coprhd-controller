/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cinder.model;

import java.util.Map;

public class SnapshotCreateRequest {

    /**
     * Json model for snapshot create request
     * {
     * "snapshot": {
     * "name": "snap-001",
     * "description": "Daily backup",
     * "volume_id": "5aa119a8-d25b-45a7-8d1b-88e127885635",
     * "force": true
     * }
     * }
     */

    public Snapshot snapshot = new Snapshot();

    public class Snapshot {
        public String name;
        public String description;
        public String volume_id;
        public boolean force;
        public Map<String, String> metadata;
    }

}
