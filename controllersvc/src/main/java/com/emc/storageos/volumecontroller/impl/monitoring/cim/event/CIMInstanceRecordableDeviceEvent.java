/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.event;

import java.util.Hashtable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager.EventType;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.CIMRecordableDeviceEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.MonitoringPropertiesLoader;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.EventTypeDeterminer;

public abstract class CIMInstanceRecordableDeviceEvent extends
		CIMRecordableDeviceEvent implements ApplicationContextAware {

	/**
	 * Reference to monitoring properties loader
	 */
	@Autowired
	protected MonitoringPropertiesLoader _monitoringPropertiesLoader;

	/**
	 * Hashtable that holds an incoming CIM Indication
	 */
	protected Hashtable<String, String> _indication;

	/**
	 * Reference to Spring context scoped singleton bean,
	 * EventTypeDeterminer
	 */
	@Autowired
	protected EventTypeDeterminer _evtDeterminer;

	/**
	 * Reference to Spring context
	 */
	protected ApplicationContext _applicationContext;

	/**
	 * A reference to Event Type Enum. This reference will help to avoid
	 * figuring out the event type several times.
	 */
	protected String _eventType;

	/**
	 * A reference to Native GUID. This reference will hold the value of
	 * computed native guid.
	 */
	protected String _nativeGuid;

	/**
	 * Overloaded construtor
	 * 
	 * @param dbClient
	 */
	public CIMInstanceRecordableDeviceEvent(DbClient dbClient) {
		super(dbClient);
	}

	/**
	 * returns the indication
	 * 
	 * @return
	 */
	public Hashtable<String, String> getIndication() {
		return _indication;
	}

	/**
	 * A java bean setter method to set the indication
	 * Also resets the nativeGuidComputed.
	 * 
	 * @param indication
	 */
	public void setIndication(Hashtable<String, String> indication) {
	    _nativeGuid = null;
	    setResource(null);
	    _indication = indication;
	}

	@Override
	public String getEventId() {
		return RecordableBourneEvent.getUniqueEventId();
	}

	@Override
	public String getSource() {
		return _indication.get(CIMConstants.INDICATION_SOURCE);
	}

	@Override
	public long getTimestamp() {
		return Long.parseLong(_indication.get(CIMConstants.INDICATION_TIME));
	}

	/**
	 * Retrieves the Operational Status Descriptions as a String available as
	 * part of Indication provided
	 * 
	 * @param notificaion
	 * @return
	 */
	public String getOperationalStatusDescriptions() {
		String value = _indication
				.get(CIMConstants.SOURCE_INSTANCE_OPERATIONAL_STATUS_DESCRIPTIONS);
		return value;
	}

	/**
	 * Retrieves the Operational Status Codes as a String available as part of
	 * Indication provided
	 * 
	 * @param notificaion
	 * @return
	 */
	public String getOperationalStatusCodes() {
		String value = _indication
				.get(CIMConstants.SOURCE_INSTANCE_OPERATIONAL_STATUS_CODES);
		return value;
	}
}
