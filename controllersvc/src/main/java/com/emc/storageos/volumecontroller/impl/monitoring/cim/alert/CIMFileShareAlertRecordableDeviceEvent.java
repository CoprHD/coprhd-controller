/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.alert;

import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.MonitoringPropertiesLoader;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

public class CIMFileShareAlertRecordableDeviceEvent extends
		CIMAlertRecordableDeviceEvent {

	/**
	 * Logger to log the debug statements
	 */
	private static final Logger _logger = LoggerFactory
			.getLogger(CIMFileShareAlertRecordableDeviceEvent.class);

	/**
	 * Over loaded constructor
	 * 
	 * @param dbClient
	 */
	public CIMFileShareAlertRecordableDeviceEvent(DbClient dbClient,
			MonitoringPropertiesLoader mLoader,
			Hashtable<String, String> notification) {
		super(dbClient);
		_monitoringPropertiesLoader = mLoader;
		_indication = notification;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Class<? extends DataObject> getResourceClass() {
		return FileShare.class;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getRecordType() {
		return RecordType.Alert.name();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getNativeGuid() {
		StringBuilder nativeGuid = new StringBuilder();
		// NEED A FIX FOR FILE SHARE ALERTS
		return nativeGuid.toString();

	}
}
