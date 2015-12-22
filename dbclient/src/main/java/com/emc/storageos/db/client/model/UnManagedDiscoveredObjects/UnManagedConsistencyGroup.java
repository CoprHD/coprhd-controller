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
	private String _numberOfVols;  
	//The name of this Consistency Group
	private String _name; 

	//Number of Volumes ingested
	private static int _ingestedVolumes; 
	
	private StringSet associatedVolumes;
	
	private StringSet unManagedVolumes;
	
	private StringSet managedVolumes;
	
	private URI _storageSystemUri;
	
	public enum ConsistencyGroupParameters {
		NATIVE_GUID("NativeGuid", "NativeGuid"),
		VOLUMES("AssociatedVolumes", "AssociatedVolumes");
		
        private final String _infoKey;
        private final String _alternateKey;

        ConsistencyGroupParameters(String infoKey, String alterateKey) {
            _infoKey = infoKey;
            _alternateKey = alterateKey;
        }
	}

	@Name("NumberOfVols")
	public String get_numberOfVols() {
		return _numberOfVols;
	}

	public void set_numberOfVols(String _numberOfVols) {
		this._numberOfVols = _numberOfVols;
	}

	@Name("Name")
	public String get_name() {
		return _name;
	}

	public void set_name(String _name) {
		this._name = _name;
	}

	@Name("StorageSystem")
	public URI get_storageSystemUri() {
		return _storageSystemUri;
	}

	public void set_storageSystemUri(URI _storageSystemUri) {
		this._storageSystemUri = _storageSystemUri;
	}

	@Name("AssociatedVolumes")
	public StringSet getAssociatedVolumes() {
		return associatedVolumes;
	}

	public void setAssociatedVolumes(StringSet associatedVolumes) {
		this.associatedVolumes = associatedVolumes;
	}

	public StringSet getUnManagedVolumes() {
		if (unManagedVolumes == null) {
			setUnManagedVolumes(new StringSet());
        }
		return unManagedVolumes;
	}

	public void setUnManagedVolumes(StringSet unManagedVolumes) {
		this.unManagedVolumes = unManagedVolumes;
	}

	public StringSet getManagedVolumes() {
		if (managedVolumes == null) {
			setManagedVolumes(new StringSet());
        }
		return managedVolumes;
	}

	public void setManagedVolumes(StringSet managedVolumes) {
		this.managedVolumes = managedVolumes;
	}
}
