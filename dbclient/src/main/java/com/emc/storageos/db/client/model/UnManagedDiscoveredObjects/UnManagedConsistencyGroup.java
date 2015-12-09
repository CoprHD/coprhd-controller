/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;

import java.net.URI;
import java.util.Map;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.IndexByKey;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObject;

@Cf("UnManagedConsistencyGroup")
public class UnManagedConsistencyGroup extends UnManagedDiscoveredObject{
	//The number of volumes associated with this Consistency Group
	private int _numberOfVols;  
	//The name of this Consistency Group
	private String _name; 
	//The volumes that are associated to this CG
	private StringSet _associatedVolumes; 
	//Number of Volumes ingested
	private static int _ingestedVolumes; 

}
