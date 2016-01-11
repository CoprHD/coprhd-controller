/*
 * Copyright (c) 2016 EMC Corporation
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
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;

@Cf("UnManagedConsistencyGroup")
public class UnManagedConsistencyGroup extends UnManagedDiscoveredObject{
	//The number of volumes associated with this Consistency Group
	private String _numberOfVols;
		
	private Integer _numberOfVolumesNotIngested;
	
	//The name of this Consistency Group
	private String _name;
	
	private StringSet associatedVolumes;
		
	private StringMap _unManagedVolumesMap;
		
	private StringMap _managedVolumesMap;
	
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
	public String getNumberOfVols() {
		return _numberOfVols;
	}

	public void setNumberOfVols(String numberOfVols) {
		this._numberOfVols = numberOfVols;
	}

	@Name("Name")
	public String getName() {
		return _name;
	}

	public void setName(String name) {
		this._name = name;
	}

	@Name("StorageSystem")
	public URI getStorageSystemUri() {
		return _storageSystemUri;
	}

	public void setStorageSystemUri(URI storageSystemUri) {
		this._storageSystemUri = storageSystemUri;
		setChanged("storageDevice");
	}

	@Name("AssociatedVolumes")
	public StringSet getAssociatedVolumes() {
		return associatedVolumes;
	}

	public void setAssociatedVolumes(StringSet associatedVolumes) {
		this.associatedVolumes = associatedVolumes;
	}	

	@Name("UnManagedVolumes")
	public StringMap getUnManagedVolumesMap() {
		if (_unManagedVolumesMap == null) {
			setUnManagedVolumesMap(new StringMap());
        }
		return _unManagedVolumesMap;
	}

	public void setUnManagedVolumesMap(StringMap unManagedVolumesMap) {
		this._unManagedVolumesMap = unManagedVolumesMap;
	}

	@Name("ManagedVolumes")
	public StringMap getManagedVolumesMap() {
		if (_managedVolumesMap == null) {
			setManagedVolumesMap(new StringMap());
        }
		return _managedVolumesMap;
	}

	public void setManagedVolumesMap(StringMap managedVolumesMap) {
		this._managedVolumesMap = managedVolumesMap;
	}

	@Name("NumberOfVolumesNotIngested")
	public Integer getNumberOfVolumesNotIngested() {
		return _numberOfVolumesNotIngested;
	}

	public void setNumberOfVolumesNotIngested(
			Integer numberOfVolumesNotIngested) {
		this._numberOfVolumesNotIngested = numberOfVolumesNotIngested;
	}	 
    
}
