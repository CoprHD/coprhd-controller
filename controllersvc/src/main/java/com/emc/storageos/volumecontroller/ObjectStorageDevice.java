package com.emc.storageos.volumecontroller;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;

public interface ObjectStorageDevice {

	BiosCommandResult doCreateBucket(StorageSystem storageObj, ObjectDeviceInputOutput ob) 
			throws ControllerException;
		
}
