/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/* 
Copyright (c) 2012 EMC Corporation
All Rights Reserved

This software contains the intellectual property of EMC Corporation
or is licensed to EMC Corporation from third parties.  Use of this
software and the intellectual property contained therein is expressly
imited to the terms and conditions of the License Agreement under which
it is provided by or on behalf of EMC.
 */
/*
 * This file contain the class SOSAlarmManager which is having all functions
 * that manage all alarms. this class contain a queue which is holding alarms that 
 * are generated in that one hour. The functions takes the alarm type generated in
 * Bourne and returns the objects parameters understand by vCenter.
 */
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.emc.storageos.vasa.data.internal.Event;
import com.emc.storageos.vasa.data.internal.StoragePool;
import com.emc.storageos.vasa.data.internal.Volume;
import com.emc.storageos.vasa.data.internal.Volume.AssociatedPool;
import com.emc.storageos.vasa.data.internal.Volume.HighAvailabilityVolume;
import com.emc.storageos.vasa.fault.SOSFailure;
import com.emc.storageos.vasa.util.FaultUtil;
import com.vmware.vim.vasa._1_0.StorageFault;
import com.vmware.vim.vasa._1_0.data.xsd.AlarmStatusEnum;
import com.vmware.vim.vasa._1_0.data.xsd.AlarmTypeEnum;
import com.vmware.vim.vasa._1_0.data.xsd.EntityTypeEnum;
import com.vmware.vim.vasa._1_0.data.xsd.NameValuePair;
import com.vmware.vim.vasa._1_0.data.xsd.StorageAlarm;

/**
 * Interface to access sos.
 */
public class SOSAlarmManager {

	public static final String MESSAGE_KEY = "msg";
	public static final String STATE_KEY = "state";

	public static final String ALARM_ID_KEY = "SOSAlarmId";

	private Queue<StorageAlarm> alarmQ;
	private Calendar lastAlarmEnqTime;
	private Hashtable<String, String> thinlyProvisionedAlarmResourceStatusTable;

	private static Logger log = Logger.getLogger(SOSAlarmManager.class);

	public SOSAlarmManager() {
		alarmQ = new LinkedList<StorageAlarm>();
		lastAlarmEnqTime = null;
		thinlyProvisionedAlarmResourceStatusTable = new Hashtable<String, String>();
	}

	public StorageAlarm getAlarmRecord() {
		return alarmQ.peek();
	}

	public boolean setAlaramRecord(StorageAlarm almObj) {

		this.alarmQ.add(almObj);

		return true;
	}

	public Queue<StorageAlarm> getAlarmQ() {
		return this.alarmQ;
	}

	public Calendar getLastAlarmEnqTime() {
		return lastAlarmEnqTime;
	}

	public void setLastAlarmEnqTime(Calendar lastAlarmEnqTime) {
		this.lastAlarmEnqTime = Calendar.getInstance();
		this.lastAlarmEnqTime.setTimeInMillis(lastAlarmEnqTime
				.getTimeInMillis());
	}

	public boolean isItAlarm(Event alarm) {
		if (alarm.getRecordType() != null
				& alarm.getRecordType().equals("Alert")) {
			return true;
		}

		return false;
	}

	public boolean isAlertRequired(String alarmType) {

		if (alarmType != null) {
			if (alarmType.startsWith("SystemError")
					|| alarmType.startsWith("AlertIndication")
					|| alarmType.startsWith("ArrayGeneric")) {
				return true;
			}
		}

		return false;
	}

	/*
	 * Right now we are returning OBJECT_ALARM only. once bourne reports all
	 * alarms for volume and file share capacity threshold, we should return
	 * SPACE_CAPACITY_ALARM. For capability related alarms should return
	 * CAPABILITY_ALARM.
	 */
	public String getVasaAlarmType(String alarmType) {

		if (alarmType == null) {
			return "";
		}

		return "OBJECT_ALARM";

	}

	/**
	 * 
	 * @param alarmType
	 *            - type of alarm/event generated in StorageOS.
	 * @return - object type string which is recognized by vCenter Server for
	 *         corresponding alarm type.
	 */
	public String getAlarmObjectType(String alarmType) {
		String objectTypeString = "";
		if (alarmType.startsWith("SystemError")) {
			objectTypeString = "SystemError";
		}
		else if (alarmType.startsWith("AlertIndication")) {
			objectTypeString = "AlertIndication";
		}
		else if (alarmType.startsWith("FileShare") 
				|| alarmType.startsWith("FileSystem")) {
			objectTypeString = EntityTypeEnum.StorageFileSystem.getValue();
		}
		else if (alarmType.startsWith("Volume")
				|| alarmType.startsWith("storagevolume")) {
			objectTypeString = EntityTypeEnum.StorageLun.getValue();
		}
		else if (alarmType.startsWith("StoragePort")) {
			objectTypeString = EntityTypeEnum.StoragePort.getValue();
		}
		else if (alarmType.startsWith("ArrayGeneric")) {
			objectTypeString = "ArrayGeneric";
		}
		else {
			log.warn("Unknown alarm type: " + alarmType
					+ "in getAlarmObjectType()");
		}

		return objectTypeString;

	}

	/**
	 * It searches the given alarm in the Q.
	 * 
	 * @return - true, if the alarm already exists in the Q, false, otherwise.
	 * @param alarm
	 *            -The Event generated in StorageOS. in StorageOS all are
	 *            considered as events.
	 * 
	 * 
	 */

	public boolean isAlarmExistsInQueue(Event alarm) {
		Iterator<StorageAlarm> it = getAlarmQ().iterator();

		while (it.hasNext()) {
			StorageAlarm storageAlarm = it.next();

			NameValuePair nvpList[] = storageAlarm.getParameterList();
			String alarmId = alarm.getEventId().toString();
			for (NameValuePair nvp : nvpList) {
				if (nvp.getParameterName().equalsIgnoreCase("SOSAlarmId")
						&& nvp.getParameterValue().equalsIgnoreCase(alarmId)) {
					return true;
				}

			}
		}

		return false;

	}

	public String getAlarmObjectTypeId(String alarmType) {
		String objectTypeIdString = "";
		if (alarmType.startsWith("FileShare") || alarmType.startsWith("FileSystem")) {
			objectTypeIdString = EntityTypeEnum.StorageFileSystem.getValue();
		}
		else if (alarmType.startsWith("Volume")) {
			objectTypeIdString = EntityTypeEnum.StorageLun.getValue();
		}
		else if (alarmType.startsWith("Cos")) {
			objectTypeIdString = EntityTypeEnum.StorageCapability.getValue();
		}
		else if (alarmType.startsWith("StorageArray")) {
			objectTypeIdString = EntityTypeEnum.StorageArray.getValue();
		}
		else if (alarmType.startsWith("StorageProcessor")) {
			objectTypeIdString = EntityTypeEnum.StorageProcessor.getValue();
		}
		else if (alarmType.startsWith("StoragePort")) {
			objectTypeIdString = EntityTypeEnum.StoragePort.getValue();
		}
		else if (alarmType.startsWith("ArrayGeneric")) {
			objectTypeIdString = "ArrayGeneric";
		}
		else {
			log.warn("Unknown alarm type: " + alarmType
					+ "in getAlarmObjectTypeId()");
		}

		return objectTypeIdString;

	}

	/**
	 * 
	 * @param alarmType
	 *            - type of event/alarm generated in StorageOS.
	 * @return - messageID for corresponding alarmType. this message should be
	 *         defined in catalog's alarm.mvsg file.
	 */

	public String getMessageIdForAlarm(String alarmType) {

		if (alarmType == null) {
			return "";
		}

		return ("StorageOS." + alarmType);

	}

	public String getAlertStatus(String severity) {

		String status = "GREEN";
		if (severity == null) {
			return "GREEN";
		}
		if (severity.contains("MAJOR") || severity.contains("CRITICAL")
				|| severity.contains("FATAL") || severity.contains("NOTICE")
				|| severity.contains("EMERGENCY")) {
			status = "RED";
		}
		else if (severity.contains("WARNING") || severity.contains("MINOR")) {
			status = "YELLOW";
		}
		else if (severity.contains("UNKNOWN") || severity.contains("OTHER") 
				|| severity.contains("INFORMATION")) {
			status = "GREEN";
		}
		else {
			log.warn("Unknown severity : " + severity + "in getAlertStatus()");
		}

		return status;

	}

	public StorageAlarm getAlarmObject(long alarmId, String resourceId,
			String resourceType, String messageId, String alarmStatus,
			String alarmType, String msgKey, String msgText) {

		StorageAlarm alarm = new StorageAlarm();

		alarm.setMessageId(messageId);
		alarm.setAlarmId(alarmId);
		alarm.setObjectId(resourceId);
		alarm.setStatus(alarmStatus);
		alarm.setAlarmType(alarmType);
		alarm.setObjectType(resourceType);
		GregorianCalendar gc = new GregorianCalendar();
		gc.setTimeInMillis(new Date().getTime());
		alarm.setAlarmTimeStamp(gc);

		NameValuePair pair = new NameValuePair();
		pair.setParameterName(ALARM_ID_KEY);
		pair.setParameterValue(UUID.randomUUID().toString());
		alarm.addParameterList(pair);

		pair = new NameValuePair();
		pair.setParameterName(msgKey);
		pair.setParameterValue(msgText);
		alarm.addParameterList(pair);

		alarm.addParameterList(pair);
		return alarm;
	}

	private boolean isThinlyProvisionAlarmRequired(String resourceId,
			String status) {

		boolean isAlarmrequired = false;

		if (null == thinlyProvisionedAlarmResourceStatusTable.get(resourceId)) {

			thinlyProvisionedAlarmResourceStatusTable.put(resourceId, status);

			// Initially we report only non-Green status as alarms
			if (!AlarmStatusEnum.Green.getValue().equals(status)) {
				isAlarmrequired = true;
			}

		} else {
			if (status.equals(thinlyProvisionedAlarmResourceStatusTable
					.get(resourceId))) {
				isAlarmrequired = false;
			} else {
				// Any status change is reported as alarm
				thinlyProvisionedAlarmResourceStatusTable.put(resourceId,
						status);
				isAlarmrequired = true;
			}
		}

		return isAlarmrequired;

	}

	public String getThinlyProvisionedStatus(SyncManager manager, Volume volume)
			throws StorageFault {

		final String methodName = "getThinlyProvisioningStatus(): ";

		log.debug(methodName + "Called with input:" + volume);

		if (volume != null && volume.isThinlyProvisioned()) {

			if (volume.getAllocatedCapacityInGB() >= volume
					.getRequestedCapacityInGB()) {
				return AlarmStatusEnum.Green.getValue();
			}
			try {
				if (volume.getHaVolumeList() != null
						&& volume.getHaVolumeList().getHaVolumeList() != null) {

					// This is a VPlex volume
					return getThinlyProvisionStatusOfHAVolumes(manager, volume
							.getHaVolumeList().getHaVolumeList());

				} else {
					// This is a normal volume
					AssociatedPool associatedPool = manager
						.fetchAssociatedPoolOfVolume(volume.getId());

					StoragePool storagePool = manager
							.fetchStoragePoolByHref(associatedPool
									.getStoragepool().getLink().getHref());

					return this
							.getThinlyProvisionStatusOfStoragePool(storagePool);
				}

			} catch (SOSFailure e) {
				log.error(methodName + "SOSFailure failure occured", e);
				throw FaultUtil.StorageFault(e);
				}

				}
		return AlarmStatusEnum.Green.getValue();
	}

	private String getThinlyProvisionStatusOfHAVolumes(SyncManager manager,
			List<HighAvailabilityVolume> haVolList) throws StorageFault {

		final String methodName = "getThinlyProvisionStatusOfHAVolumes(): ";

		log.debug(methodName + "Entry with input: haVolList" + haVolList);

		String alarmStatus = null;
		StoragePool storagePool = null;
		Set<String> alarmSet = new HashSet<String>();

		for (HighAvailabilityVolume haVol : haVolList) {

			try {

				AssociatedPool associatedPool = manager
						.fetchAssociatedPoolOfVolume(haVol.getId());

				if (log.isTraceEnabled()) {
					log.trace(methodName + haVol.getId()
							+ " is associated with " + associatedPool);
				}

				storagePool = manager.fetchStoragePoolByHref(associatedPool
						.getStoragepool().getLink().getHref());

				alarmStatus = this
						.getThinlyProvisionStatusOfStoragePool(storagePool);

				alarmSet.add(alarmStatus);

			} catch (SOSFailure e) {
				log.error(methodName + "SOSFailure failure occured", e);
				throw FaultUtil.StorageFault(e);
			}

		}
		if (alarmSet.contains(AlarmStatusEnum.Red.getValue())) {
			log.debug(methodName + "Exit returning [RED]");
			return AlarmStatusEnum.Red.getValue();
		}
		if (alarmSet.contains(AlarmStatusEnum.Yellow.getValue())) {
			log.debug(methodName + "Exit returning [YELLOW]");
			return AlarmStatusEnum.Yellow.getValue();
		}

		log.debug(methodName + "Exit returning [GREEN]");
		return AlarmStatusEnum.Green.getValue();
	}

	private String getThinlyProvisionStatusOfStoragePool(StoragePool storagePool) {

		String status = AlarmStatusEnum.Green.getValue();

		if (storagePool.getPercentSubscribed() < 100) {
			return AlarmStatusEnum.Green.getValue();
		}
		if (storagePool.getPercentUsed() > 80) {
			return AlarmStatusEnum.Red.getValue();
		}
		if (storagePool.getPercentUsed() > 50) {
			return AlarmStatusEnum.Yellow.getValue();
		}
		return status;
	}

	public List<StorageAlarm> getThinProvisionAlarms(SyncManager manager,
			List<Volume> volumeList, long lastAlarmId) throws StorageFault {

		final String methodName = "getThinProvisionAlarms(): ";
		log.debug(methodName + " Entry with last alarm id[" + lastAlarmId + "]");

		StorageAlarm alarm = null;
		List<StorageAlarm> alarmList = new ArrayList<StorageAlarm>();
		String status = null;
		String resourceId = null;
		String resourceType = null;

		if (volumeList != null && !volumeList.isEmpty()) {
			for (Volume volume : volumeList) {

				resourceId = volume.getId();
				resourceType = EntityTypeEnum.StorageLun.getValue();

				status = this.getThinlyProvisionedStatus(manager, volume);

				if (isThinlyProvisionAlarmRequired(resourceId, status)) {

					alarm = this.getAlarmObject(++lastAlarmId, resourceId,
							resourceType, "Alarm.ThinProvisioned", status,
							AlarmTypeEnum.SpaceCapacity.getValue(), STATE_KEY,
							status);

					log.debug(methodName + "New alarm generated of id["
							+ lastAlarmId + "] status[" + status
							+ "] on resource id[" + resourceId + "]");

					alarmList.add(alarm);
				}

			}

		}

		log.debug(methodName + "Exit returning  alarm list of size["
				+ alarmList.size() + "]");
		return alarmList;
	}

}

