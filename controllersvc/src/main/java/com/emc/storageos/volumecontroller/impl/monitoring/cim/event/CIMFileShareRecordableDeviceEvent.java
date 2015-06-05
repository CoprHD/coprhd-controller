/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.event;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.monitoring.StorageDeviceInfo;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.MonitoringPropertiesLoader;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;
import com.emc.storageos.services.OperationTypeEnum;

@Component("CIMFileShareRecordableDeviceEvent")
@Scope("prototype")
public class CIMFileShareRecordableDeviceEvent extends
		CIMInstanceRecordableDeviceEvent implements ApplicationContextAware {
	/**
	 * Logger to log the debug statements
	 */
	private static final Logger _logger = LoggerFactory
			.getLogger(CIMFileShareRecordableDeviceEvent.class);
	
    @Autowired
    private DbClient dbClient;

	/**
	 * Reference to storage device info object that helps to get the serial
	 * number
	 */
	@Autowired
	protected StorageDeviceInfo _storageDeviceInfo;

	/**
	 * Overloaded constructor
	 * 
	 * @param dbClient
	 */
	@Autowired
	public CIMFileShareRecordableDeviceEvent(DbClient dbClient) {
		super(dbClient);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Class<? extends DataObject> getResourceClass() {
		return FileShare.class;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getNativeGuid() {

	    if (_nativeGuid != null) {
            _logger.debug("Using already computed NativeGuid : {}", _nativeGuid);
            return _nativeGuid;
        }
        
		try
        {
		    _nativeGuid = NativeGUIDGenerator.generateNativeGuid(_indication, getSerialNumber());
            logMessage(
                    "NativeGuid for FileShare Computed as  : [{}]",
                    new Object[] { _nativeGuid });
        }catch (Exception e) {
            _logger.error("Unable to compute NativeGuid :", e);
        }
		
		return _nativeGuid;

	}
	
	/**
	 * Gives vnxfile's serialNumber based on the indicationSource
	 * @return
	 */
	private String getSerialNumber(){
	    String serialNumber = null;
	    String indicationSource = _indication.get(CIMConstants.INDICATION_SOURCE);
	    _logger.debug("indicationSource :{}",indicationSource);
	    List<URI> storageSystemURIList = dbClient.queryByType(StorageSystem.class, true);
	    for(URI storageSystemURI: storageSystemURIList){
	        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageSystemURI);
	        _logger.debug("storageSystem.getSmisProviderIP() :{}",storageSystem.getSmisProviderIP());
	        if(storageSystem.getSmisProviderIP()!=null && 
	                storageSystem.getSmisProviderIP().equals(indicationSource)){
	            serialNumber = storageSystem.getSerialNumber();
	            break;
	        }
	    }
	    _logger.debug("serialNumber :{}",serialNumber);
	    return serialNumber;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getType() {
		if (_eventType == null) {
			_eventType = getBourneFileShareEventType();
		}
		if (_eventType != null)
			return _eventType;
		else
			return null;
	}

	/**
	 * RAW event mapped with the Bourne defined Block related events.
	 * 
	 * @param notification
	 * @return
	 */
	private String getBourneFileShareEventType() {

		String eventType = "";
		eventType = _indication.get(CIMConstants.INDICATION_CLASS_TAG);
		String eventEnum = null;

		logMessage("Raw Indication's Event Type found as : {}",
				new Object[] { eventType });

		if (eventType != null && eventType.length() > 0) {
			if (eventType.contains(CIMConstants.INST_CREATION_EVENT)) {
				eventEnum = OperationTypeEnum.CREATE_FILE_SYSTEM.getEvType(true);
			} else if (eventType.contains(CIMConstants.INST_DELETION_EVENT)) {
				eventEnum = OperationTypeEnum.DELETE_FILE_SYSTEM.getEvType(true);
			} else {
				
				String[] osDescs = new String[0];
				String[] osCodes = new String[0];
		        // Common Functionality.
				osDescs = MonitoringPropertiesLoader.splitStringIntoArray(getOperationalStatusDescriptions());
				osCodes = MonitoringPropertiesLoader.splitStringIntoArray(getOperationalStatusCodes());
		        
				eventEnum = _evtDeterminer
						.determineEventTypeBasedOnOperationStatusValues(
								_indication, Boolean.FALSE,osDescs ,osCodes);
			}
		}

		return eventEnum;
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
			_logger.debug("-> " + msg, obj);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getRecordType() {
		return RecordType.Event.name();
	}

	@Override
	public String getExtensions() {
		return null;
	}


}
