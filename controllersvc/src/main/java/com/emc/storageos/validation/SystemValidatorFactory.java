package com.emc.storageos.validation;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;

public class SystemValidatorFactory {
	
	
	static public StorageSystemValidator getValidator(StorageSystem system, DbClient dbClient) {
		String type = system.getSystemType();
		if (type.equals(StorageSystem.Type.vplex.name())) {
			return new VPlexSystemValidator(dbClient);
		}
		// No implementation (yet) for this device type
		return null;
	}

}
