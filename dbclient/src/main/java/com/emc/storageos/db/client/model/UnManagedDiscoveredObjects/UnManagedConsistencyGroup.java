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

	public void setManagedVolumesMap(StringMap _managedVolumesMap) {
		this._managedVolumesMap = _managedVolumesMap;
	}

	@Name("NumberOfVolumesNotIngested")
	public Integer getNumberOfVolumesNotIngested() {
		return _numberOfVolumesNotIngested;
	}

	public void setNumberOfVolumesNotIngested(
			Integer _numberOfVolumesNotIngested) {
		this._numberOfVolumesNotIngested = _numberOfVolumesNotIngested;
	}	 
    
}
