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

import java.util.Map;

public class SnapshotCreateRequestGen extends SnapshotCreateRequest{
	
	/**
	 * Json model for snapshot create request
	 * {
    	"snapshot": {
        	"name": "snap-001",
        	"description": "Daily backup",
        	"volume_id": "5aa119a8-d25b-45a7-8d1b-88e127885635",
        	"force": true
                "metadata":{
 
      			}
     		}
		}
	 */
	
	public SnapshotGen snapshot  = new SnapshotGen();
	public class SnapshotGen extends Snapshot{				
		public String display_name;
		public String display_description;
	}

}
