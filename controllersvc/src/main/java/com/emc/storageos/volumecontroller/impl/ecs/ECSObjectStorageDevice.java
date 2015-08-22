package com.emc.storageos.volumecontroller.impl.ecs;

import com.emc.storageos.ecs.api.ECSApiFactory;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
import com.emc.storageos.volumecontroller.ObjectStorageDevice;

public class ECSObjectStorageDevice implements ObjectStorageDevice {
	  private ECSApiFactory _factory;

	    /**
	     * Set ECS API factory
	     * 
	     * @param factory
	     */
	    public void setECSApiFactory(ECSApiFactory factory) {
	        _factory = factory;
	    }
}
