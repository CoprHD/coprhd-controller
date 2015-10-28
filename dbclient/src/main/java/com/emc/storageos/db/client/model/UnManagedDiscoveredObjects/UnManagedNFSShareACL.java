/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;

import java.net.URI;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.NFSShareACL;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.ShareACL;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;

@Cf("UnManagedNFSShareACL")
public class UnManagedNFSShareACL extends NFSShareACL {
	
	private String nativeGuid;
	
	
    @Name("nativeGuid")
    public String getNativeGuid() {
        return nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        this.nativeGuid = nativeGuid;
        setChanged("nativeGuid");
    }
	
}
