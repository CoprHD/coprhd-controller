package com.emc.storageos.volumecontroller.impl;

import java.net.URI;
import java.util.Set;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.FileController;
import com.emc.storageos.volumecontroller.ObjectController;

public class ObjectControllerImpl extends AbstractDiscoveredSystemController
		implements ObjectController {

    // device specific FileController implementations
    private Set<ObjectController> _deviceImpl;
    private Dispatcher _dispatcher;
    private DbClient _dbClient;

	@Override
	public void connectStorage(URI storage) throws InternalException {
		// TODO Auto-generated method stub

	}

	@Override
	public void disconnectStorage(URI storage) throws InternalException {
		// TODO Auto-generated method stub

	}

	@Override
	public void discoverStorageSystem(AsyncTask[] tasks)
			throws InternalException {
		// TODO Auto-generated method stub

	}

	@Override
	public void scanStorageProviders(AsyncTask[] tasks)
			throws InternalException {
		// TODO Auto-generated method stub

	}

	@Override
	public void startMonitoring(AsyncTask task, Type deviceType)
			throws InternalException {
		// TODO Auto-generated method stub

	}

	@Override
	protected Controller lookupDeviceController(DiscoveredSystemObject device) {
		// TODO Auto-generated method stub
		return null;
	}

}
