/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.MonitoringPropertiesLoader;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;
import com.emc.storageos.services.OperationTypeEnum;

@Component("CIMVolumeRecordableDeviceEvent")
@Scope("prototype")
public class CIMVolumeRecordableDeviceEvent extends
		CIMInstanceRecordableDeviceEvent {
	/**
	 * Logger to log the debug statements
	 */
	private static final Logger _logger = LoggerFactory
			.getLogger(CIMVolumeRecordableDeviceEvent.class);

	/**
	 * Overloaded constructor
	 * 
	 * @param dbClient
	 */
	@Autowired
	public CIMVolumeRecordableDeviceEvent(DbClient dbClient) {
		super(dbClient);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Class<? extends DataObject> getResourceClass() {
		return Volume.class;
	}

	@Override
	public String getNativeGuid() {

		if (_nativeGuid != null) {
			_logger.debug("Using already computed NativeGuid : {}", _nativeGuid);
			return _nativeGuid;
		}
		try
		{
		    _nativeGuid = NativeGUIDGenerator.generateNativeGuid(_indication);
		    logMessage(
	                "NativeGuid for block Computed as  : [{}]",
	                new Object[] { _nativeGuid });
		}catch (Throwable e) {
		    _logger.error("Unable to compute NativeGuid :", e);
        }
		
		return _nativeGuid;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getType() {
		if (_eventType == null) {
			_eventType = determineBourneVolumeEventType();
		}
		if (_eventType != null)
			return _eventType;
		else
			return null;
	}

	/**
	 * RAW event mapped with the Bourne defined Block related events. Creation
	 * and Deletion Event Types were processed here. Volume Active and InActive
	 * and File Share Active and InActive type determination had been done
	 * inside OperationalStausDeterminer Component
	 * 
	 * @param notification
	 * @return
	 */
	private String determineBourneVolumeEventType() {

		String eventType = "";
		eventType = _indication.get(CIMConstants.INDICATION_CLASS_TAG);
		String eventEnum = null;

		logMessage("Raw indication of Type found {}",
				new Object[] { eventType });

		if (eventType != null && eventType.length() > 0) {
			if (eventType.contains(CIMConstants.INST_CREATION_EVENT)) {
				eventEnum = OperationTypeEnum.CREATE_BLOCK_VOLUME.getEvType(true);
			} else if (eventType.contains(CIMConstants.INST_DELETION_EVENT)) {
				eventEnum = OperationTypeEnum.DELETE_BLOCK_VOLUME.getEvType(true);
			} else {

				String[] osDescs = new String[0];
				String[] osCodes = new String[0];
				// Common Functionality.
				osDescs = MonitoringPropertiesLoader
						.splitStringIntoArray(getOperationalStatusDescriptions());
				osCodes = MonitoringPropertiesLoader
						.splitStringIntoArray(getOperationalStatusCodes());

				eventEnum = _evtDeterminer
						.determineEventTypeBasedOnOperationStatusValues(
								_indication, Boolean.TRUE, osDescs, osCodes);
			}
		}

		return eventEnum;
	}

	@Override
	public String getExtensions() {
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		_applicationContext = applicationContext;
	}

	/**
	 * Log the messages. This method eliminates the logging condition check
	 * every time when we need to log a message.
	 * 
	 * @param msg
	 * @param obj
	 */
	private void logMessage(String msg, Object[] obj) {
		if (_monitoringPropertiesLoader.isToLogIndications()) {
			_logger.debug("[Monitoring] -> " + msg, obj);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getRecordType() {
		return RecordType.Event.name();
	}
}
