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

	//Number of Volumes ingested
	private static int _ingestedVolumes; 
	
	private URI _storageSystemUri;
	
	public URI get_storageSystemUri() {
		return _storageSystemUri;
	}

	public void set_storageSystemUri(URI _storageSystemUri) {
		this._storageSystemUri = _storageSystemUri;
	}

	public enum SupportedConsistencyGroupInformation {
		NATIVE_GUID("NativeGuid", "NativeGuid");
		
        private final String _infoKey;
        private final String _alternateKey;

        SupportedConsistencyGroupInformation(String infoKey, String alterateKey) {
            _infoKey = infoKey;
            _alternateKey = alterateKey;
        }
	}

	public int get_numberOfVols() {
		return _numberOfVols;
	}

	public void set_numberOfVols(int _numberOfVols) {
		this._numberOfVols = _numberOfVols;
	}

	public String get_name() {
		return _name;
	}

	public void set_name(String _name) {
		this._name = _name;
	}


	
	

}
