package com.emc.storageos.volumecontroller.impl;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.ObjectController;
import com.emc.storageos.volumecontroller.ObjectStorageDevice;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

public class ObjectDeviceController implements ObjectController {

	private DbClient _dbClient;
	private Map<String, ObjectStorageDevice> _devices;
	private static final Logger _log = LoggerFactory.getLogger(ObjectDeviceController.class);
	
    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    public void setDevices(Map<String, ObjectStorageDevice> deviceInterfaces) {
        _devices = deviceInterfaces;
    }

    private ObjectStorageDevice getDevice(String deviceType) {
        return _devices.get(deviceType);
    }
    
	@Override
	public void connectStorage(URI storage) throws InternalException {
		// TODO Auto-generated method stub
		_log.info("Object connect storage");

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

}
