/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

public class SnapshotCreateRequestGen extends SnapshotCreateRequest {

    /**
     * Json model for snapshot create request
     * {
     * "snapshot": {
     * "name": "snap-001",
     * "description": "Daily backup",
     * "volume_id": "5aa119a8-d25b-45a7-8d1b-88e127885635",
     * "force": true
     * "metadata":{
     * 
     * }
     * }
     * }
     */

    public SnapshotGen snapshot = new SnapshotGen();

    public class SnapshotGen extends Snapshot {
        public String display_name;
        public String display_description;
    }

}
