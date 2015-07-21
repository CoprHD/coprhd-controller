/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

/*
 * This file contain the class SOSEventManager which is having all functions
 * that manage all events. this class contain a queue which is holding events that 
 * are generated in that one hour. The functions takes the event type generated in
 * Bourne and returns the objects parameters understand by vCenter.
 */
package com.emc.storageos.vasa;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.log4j.Logger;

import com.emc.storageos.vasa.data.internal.Event;
import com.vmware.vim.vasa._1_0.data.xsd.EntityTypeEnum;
import com.vmware.vim.vasa._1_0.data.xsd.EventConfigTypeEnum;
import com.vmware.vim.vasa._1_0.data.xsd.EventTypeEnum;
import com.vmware.vim.vasa._1_0.data.xsd.NameValuePair;
import com.vmware.vim.vasa._1_0.data.xsd.StorageEvent;

/**
 * This class contain functions related to Event management.
 * 
 */
public class SOSEventManager {

	private Queue<StorageEvent> eventQ;
	private Calendar lastEventEnqTime;

	private static Logger log = Logger.getLogger(SOSEventManager.class);

	public SOSEventManager() {
		eventQ = new LinkedList<StorageEvent>();
		lastEventEnqTime = null;
	}

	public StorageEvent getEventRecord() {
		return eventQ.peek();
	}

	public boolean setEventRecord(StorageEvent eventObj) {
		eventQ.add(eventObj);
		return true;
	}

	public Queue<StorageEvent> getEventQ() {
		return this.eventQ;
	}

	public Calendar getLastEventEnqTime() {
		return lastEventEnqTime;
	}

	public void setLastEventEnqTime(Calendar lastEventEnqTime) {
		this.lastEventEnqTime = Calendar.getInstance();
		this.lastEventEnqTime.setTimeInMillis(lastEventEnqTime
				.getTimeInMillis());
	}

	/**
	 * 
	 * @param event
	 *            - The Event generated from StorageOS.
	 * @return TRUE - if the event/alert belongs to event, otherwise false.
	 */
	public boolean isItEvent(Event event) {
		if (event.getRecordType() != null
				& event.getRecordType().equals("Event")) {
			return true;
		}
		return false;
	}

	public boolean isEventRequired(String eventType) {
		if (eventType == null) {
			return false;
		}

		if (eventType.startsWith("GenericSystem") ||

		/*eventType.startsWith("FileSystemCreated")
				|| eventType.startsWith("FileSystemDeleted") || */
				 eventType.startsWith("FileSystemExported")
				|| eventType.startsWith("FileSystemUnexported")
				|| eventType.startsWith("FileSystemRestored")
				|| eventType.startsWith("FileSystemAcive")
				|| eventType.startsWith("FileSystemInactive")
				/*|| eventType.startsWith("FileSystemSnapshotExported")
				|| eventType.startsWith("FileSystemSnapshotUnexported")*/
				|| eventType.startsWith("FileSystemExpanded") ||

				/*eventType.startsWith("VolumeCreated")
				|| eventType.startsWith("VolumeDeleted") || */
				eventType.startsWith("VolumeExpanded")
				|| eventType.startsWith("ExportVolumeAdded")
				|| eventType.startsWith("ExportVolumeRemoved")
				|| eventType.startsWith("VolumeRestored")
				/*|| eventType.startsWith("VolumeSnapshotCreated")
				|| eventType.startsWith("VolumeSnapshotDeleted")
				|| eventType.startsWith("VolumeSnapshotActivated")
				|| eventType.startsWith("VolumeSnapshotDeactivated")
				|| eventType.startsWith("VolumeSnapshotRestored")*/
				|| eventType.startsWith("Vpool")
				|| eventType.contains("STORAGEPORT")
				|| eventType.contains("StoragePort")
				|| eventType.contains("Export")) {
			return true;
		}

		return false;
	}

	public String getVasaEventType(String eventType) {

		if (eventType == null)
			return "";

		String strVasaEventType = EventTypeEnum.System.getValue();

		if (eventType.startsWith("GenericSystem")
				|| eventType.startsWith("FileSystemRestored")
				|| eventType.startsWith("FileSystemAcive")
				|| eventType.startsWith("FileSystemInactive")
				/*|| eventType.startsWith("FileSystemCreated")
				|| eventType.startsWith("FileSystemDeleted") ||

				eventType.startsWith("VolumeCreated")
				|| eventType.startsWith("VolumeDeleted")*/
				|| eventType.startsWith("VolumeRestored")
				|| eventType.startsWith("VolumeAcive")
				|| eventType.startsWith("VolumeInactive")) {

			strVasaEventType = EventTypeEnum.System.getValue();
		} else if (eventType.startsWith("FileSystemExported")
				|| eventType.startsWith("FileSystemUnexported")
				|| eventType.startsWith("FileSystemExpanded")
				/*|| eventType.startsWith("FileSystemSnapshotExported")
				|| eventType.startsWith("FileSystemSnapshotUnexported") */
				|| eventType.startsWith("ExportVolumeAdded")
				|| eventType.startsWith("ExportVolumeRemoved")
				/*|| eventType.startsWith("VolumeSnapshotExported")
				|| eventType.startsWith("VolumeSnapshotUnexported")
				|| eventType.startsWith("VolumeSnapshotRestored")*/
				|| eventType.startsWith("VolumeExpanded")
				|| eventType.startsWith("Vpool")
				|| eventType.contains("STORAGEPORT")
				|| eventType.contains("StoragePort")) {

			strVasaEventType = EventTypeEnum.Config.getValue();
		} else {
			log.warn("Unknown event type: [" + eventType
					+ "] in getVasaEventType()");
		}

		return strVasaEventType;

	}

	public String getVasaConfigType(String eventType) {

		if (eventType == null)
			return "";

		String strVasaConfigType = EventConfigTypeEnum.New.getValue();

		if (eventType.startsWith("FileSystemExported")
				//|| eventType.startsWith("FileSystemSnapshotExported")
				|| eventType.startsWith("ExportVolumeAdded")
				//|| eventType.startsWith("VolumeSnapshotExported")
				|| eventType.startsWith("VpoolCreated")
				|| eventType.startsWith("CREATE STORAGEPORT")
				|| eventType.startsWith("REGISTER STORAGEPORT")
				|| eventType.startsWith("StoragePortRegistered")) {

			return EventConfigTypeEnum.New.getValue();
		} else if (eventType.startsWith("FileSystemUnexported")
				//|| eventType.startsWith("FileSystemSnapshotUnexported")
				|| eventType.startsWith("ExportVolumeRemoved")
				//|| eventType.startsWith("VolumeSnapshotUnexported")
				|| eventType.startsWith("VpoolDeleted")
				|| eventType.startsWith("DELETE STORAGEPORT")
				|| eventType.startsWith("UNREGISTER STORAGEPORT")
				|| eventType.startsWith("StoragePortUnregistered")) {

			return EventConfigTypeEnum.Delete.getValue();

		} else if (//eventType.startsWith("VolumeSnapshotRestored") ||
				eventType.startsWith("FileSystemExpanded")
				|| eventType.startsWith("VpoolUpdated")
				|| eventType.startsWith("VolumeExpanded")
				|| eventType.startsWith("UPDATE STORAGEPORT")
				|| eventType.startsWith("StoragePortUpdated")) {
			return EventConfigTypeEnum.Update.getValue();
		} else {
			log.warn("Unknown event type: " + eventType
					+ "in getVasaConfigType()");
		}

		return strVasaConfigType;

	}

	/**
	 * Returns the object type on which the event occurred
	 * 
	 * @param eventType
	 *            - type of event generated in StorageOS.
	 * @return - object type string which is recognized by vCenter Server for
	 *         corresponding event type.
	 */
	public String getEventObjectType(String eventType) {
		String objectTypeString = "";
		if (eventType.startsWith("FileShare")
				|| eventType.startsWith("FileSystem")) {
			objectTypeString = EntityTypeEnum.StorageFileSystem.getValue();
		}
		else if (eventType.startsWith("Volume")
				|| eventType.startsWith("ExportVolume")) {
			objectTypeString = EntityTypeEnum.StorageLun.getValue();
		}
		else if (eventType.startsWith("Vpool")) {
			objectTypeString = EntityTypeEnum.StorageCapability.getValue();
		}
		else if (eventType.startsWith("StorageArray")) {
			objectTypeString = EntityTypeEnum.StorageArray.getValue();
		}
		else if (eventType.startsWith("StorageProcessor")) {
			objectTypeString = EntityTypeEnum.StorageProcessor.getValue();
		}
		else if (eventType.contains("STORAGEPORT")
				|| eventType.contains("StoragePort")) {
			objectTypeString = EntityTypeEnum.StoragePort.getValue();
		}
		else {
			log.warn("Unknown event type [" + eventType
					+ "] in getEventObjectType()");
		}

		return objectTypeString;

	}

	/**
	 * Returns the messageId defined in event.vmsg
	 * 
	 * @param eventType
	 *            - type of event generated in StorageOS.
	 * @return - messageID for corresponding eventType. this message should be
	 *         defined in catalog's event.mvsg file.
	 * 
	 *         The messageId = StorageOS.eventType. for example eventType =
	 *         FileSystemCreated, then the messageId=
	 *         StorageOS.FileSystemCreated. all these messages should be defined
	 *         in the events.mvsg file under catalog folder.
	 */

	public String getMessageIdForEvent(String eventType) {

		String msgIdPrefix = "StorageOS.";
		if (eventType == null) {
			return "";
		}
		else if (eventType.startsWith("ExportVolumeAdded")) {
			return msgIdPrefix + "VolumeExported";
		}
		else if (eventType.startsWith("ExportVolumeRemoved")) {
			return msgIdPrefix + "VolumeUnexported";
		}
		else if (eventType.contains("STORAGEPORT")) {
			if (eventType.contains("CREATE")) {
				return msgIdPrefix + "StoragePortCreated";
			} else if (eventType.contains("REGISTER")) {
				return msgIdPrefix + "StoragePortRegistered";
			} else if (eventType.contains("UNREGISTER")) {
				return msgIdPrefix + "StoragePortUnregistered";
			} else if (eventType.contains("DELETE")) {
				return msgIdPrefix + "StoragePortDeleted";
			} else if (eventType.contains("UPDATE")) {
				return msgIdPrefix + "StoragePortUpdated";
			}
		} else if (eventType.contains("StoragePortRegistered")) {
			return msgIdPrefix + "StoragePortRegistered";
		} else if (eventType.contains("StoragePortUnregistered")) {
			return msgIdPrefix + "StoragePortUnregistered";
		} else if (eventType.contains("StoragePortUpdated")) {
			return msgIdPrefix + "StoragePortUpdated";
		}

		return msgIdPrefix + eventType;

	}

	/**
	 * It searches the given event in the Q.
	 * 
	 * @return - true, if the event already exists in the Q, false, otherwise.
	 * @param event
	 *            -The Event generated in StorageOS
	 * 
	 * 
	 */

	public boolean isEventExistsInQueue(Event event) {
		Iterator<StorageEvent> it = getEventQ().iterator();

		while (it.hasNext()) {
			StorageEvent storageEvent = it.next();

			NameValuePair nvpList[] = storageEvent.getParameterList();
			String eventId = event.getEventId().toString();
			for (NameValuePair nvp : nvpList) {
				if (nvp.getParameterName().equalsIgnoreCase("SOSEventId")
						&& nvp.getParameterValue().equalsIgnoreCase(eventId)) {
					return true;
				}

			}
		}

		return false;

	}

	public String getEventObjectTypeId(String eventType) {
		String objectTypeIdString = "";
		if (eventType.startsWith("FileShare")
				|| eventType.startsWith("FileSystem")) {
			objectTypeIdString = EntityTypeEnum.StorageFileSystem.getValue();
		}
		else if (eventType.startsWith("Volume")
				|| eventType.startsWith("ExportVolume")) {
			objectTypeIdString = EntityTypeEnum.StorageLun.getValue();
		}
		else if (eventType.startsWith("Vpool")) {
			objectTypeIdString = EntityTypeEnum.StorageCapability.getValue();
		}
		else if (eventType.startsWith("StorageArray")) {
			objectTypeIdString = EntityTypeEnum.StorageArray.getValue();
		}
		else if (eventType.startsWith("StorageProcessor")) {
			objectTypeIdString = EntityTypeEnum.StorageProcessor.getValue();
		}
		else if (eventType.contains("STORAGEPORT")
				|| eventType.contains("StoragePort")) {
			objectTypeIdString = EntityTypeEnum.StoragePort.getValue();
		}
		else {
			log.warn("Unknown event type: [" + eventType
					+ "] in getEventObjectTypeId()");
		}

		return objectTypeIdString;

	}
	
	public StorageEvent createNewEvent(String bourneEventId,
			String bourneEventTimestamp, long lastEventId, String resourceId,
			String resourceType, String eventType, String eventConfigType,
			String messageId) {

		StorageEvent storageEvent = new StorageEvent();
		storageEvent.setEventId(lastEventId);
		storageEvent.setObjectId(resourceId);
		storageEvent.setEventObjType(resourceType);

		storageEvent.setEventType(eventType);

		storageEvent.setEventConfigType(eventConfigType);

		GregorianCalendar gc = new GregorianCalendar();
		gc.setTimeInMillis(Long.parseLong(bourneEventTimestamp));
		storageEvent.setEventTimeStamp(gc);

		storageEvent.setMessageId(messageId);

		// Storageos generated eventid is used to check the
		// duplicates in the Q!!.
		NameValuePair nvp = new NameValuePair();
		nvp.setParameterName("SOSEventId");
		nvp.setParameterValue(bourneEventId);
		storageEvent.addParameterList(nvp);

		NameValuePair nvp2 = new NameValuePair();
		nvp2.setParameterName("resId");
		nvp2.setParameterValue(resourceId);
		storageEvent.addParameterList(nvp2);

		return storageEvent;
	}

}

