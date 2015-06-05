/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.alert;

import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.MonitoringPropertiesLoader;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;

public class CIMVolumeAlertRecordableDeviceEvent extends
		CIMAlertRecordableDeviceEvent {
	/**
	 * Logger to log the debug statements
	 */
	private static final Logger _logger = LoggerFactory
			.getLogger(CIMVolumeAlertRecordableDeviceEvent.class);

	/**
	 * Overloaded constructor
	 * 
	 * @param dbClient
	 */
	public CIMVolumeAlertRecordableDeviceEvent(DbClient dbClient,
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
	public String getNativeGuid() {

		String nativeGuid = _indication
				.get(CIMConstants.ALERT_MANAGED_ELEMENT_COMPOSITE_ID);
		if (_monitoringPropertiesLoader.isToLogIndications()) {
			_logger.debug("Block related Alert - NativeGuid/Alternate ID {}",
					nativeGuid);
		}

		// Convert the composite id to match Bourne format.
		if (nativeGuid != null
				&& nativeGuid.indexOf(CIMConstants.CLARIION_PREFIX) != -1) {
			nativeGuid = nativeGuid.replace(CIMConstants.CLARIION_PREFIX,
					CIMConstants.CLARIION_PREFIX_TO_UPPER);
		}
		if (nativeGuid != null) {
			nativeGuid = nativeGuid.replace("/", CIMConstants.VOLUME_PREFIX);
		}
		if (_monitoringPropertiesLoader.isToLogIndications()) {
			_logger.debug(
					"Bourne Specific format of Alternate ID for block {}",
					nativeGuid);
		}
		return nativeGuid;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Class<? extends DataObject> getResourceClass() {
		return Volume.class;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getExtensions() {
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getRecordType() {
		return RecordType.Alert.name();
	}

}
