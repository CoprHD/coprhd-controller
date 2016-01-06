/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.cinder.model;

public class SnapshotUpdateRequestGen extends SnapshotCreateRequest {
    public SnapshotGen snapshot;

    public class SnapshotGen extends Snapshot {
        public String display_name;
        public String display_description;
    }
}
