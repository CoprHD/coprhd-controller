/* **********************************************************
 * Copyright 2010 VMware, Inc.  All rights reserved. -- VMware Confidential
 * **********************************************************/
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.axis2.context.ServiceContext;
import org.apache.axis2.service.Lifecycle;
import org.apache.log4j.Logger;

import com.emc.storageos.vasa.util.FaultUtil;
import com.emc.storageos.vasa.util.SSLUtil;
import com.emc.storageos.vasa.util.Util;
import com.vmware.vim.vasa._1_0.InvalidArgument;
import com.vmware.vim.vasa._1_0.InvalidCertificate;
import com.vmware.vim.vasa._1_0.InvalidLogin;
import com.vmware.vim.vasa._1_0.InvalidSession;
import com.vmware.vim.vasa._1_0.LostAlarm;
import com.vmware.vim.vasa._1_0.LostEvent;
import com.vmware.vim.vasa._1_0.NotFound;
import com.vmware.vim.vasa._1_0.NotImplemented;
import com.vmware.vim.vasa._1_0.StorageFault;
import com.vmware.vim.vasa._1_0.VasaServiceSkeletonInterface;
import com.vmware.vim.vasa._1_0.data.xsd.EntityTypeEnum;
import com.vmware.vim.vasa._1_0.data.xsd.MessageCatalog;
import com.vmware.vim.vasa._1_0.data.xsd.StorageAlarm;
import com.vmware.vim.vasa._1_0.data.xsd.StorageArray;
import com.vmware.vim.vasa._1_0.data.xsd.StorageCapability;
import com.vmware.vim.vasa._1_0.data.xsd.StorageEvent;
import com.vmware.vim.vasa._1_0.data.xsd.StorageFileSystem;
import com.vmware.vim.vasa._1_0.data.xsd.StorageLun;
import com.vmware.vim.vasa._1_0.data.xsd.StoragePort;
import com.vmware.vim.vasa._1_0.data.xsd.StorageProcessor;
import com.vmware.vim.vasa._1_0.data.xsd.UsageContext;
import com.vmware.vim.vasa._1_0.data.xsd.VasaAssociationObject;
import com.vmware.vim.vasa._1_0.data.xsd.VasaProviderInfo;

/**
 * Sample VASA Provider implementation
 * 
 */

public class ServiceImpl implements VasaServiceSkeletonInterface, Lifecycle {

	private static Logger log = Logger.getLogger(ServiceImpl.class);
	private static String TRUSTSTOREPASSWORD_PARAM = "trustStorePassword"; //NOSONAR ("Suppressing Sonar violation for Credentials should not be hard-coded")

	private static final String FILE_SEPARATOR = System
			.getProperty("file.separator");

	private String tomcatRoot;
	private String trustStoreFileName = FILE_SEPARATOR + "conf"
			+ FILE_SEPARATOR + "jssecacerts";
	private String trustStorePassword;
	private SSLUtil sslUtil;
	private Config config;

	private ContextManager contextManager;

	public ServiceImpl() {
	}

	/**
	 * Verifies username, password and certificate provided. If inputs are valid
	 * an instance of VasaProviderInfo is returned
	 * 
	 * @param username
	 *            the username
	 * @param password
	 *            the password
	 * @param certificateStr
	 *            certificate string
	 * @return VasaProviderInfo with modelId, vendorId, VASA API version, VASA
	 *         provider version and namespace
	 * @throws InvalidCertificate
	 *             if certificate is invalid
	 * @throws InvalidLogin
	 *             if login attempt is incorrect
	 * 
	 */
	public VasaProviderInfo registerVASACertificate(String username,
			String password, String certificateStr) throws InvalidCertificate,
			InvalidLogin, InvalidSession, StorageFault {
		// Mandatory function
		
		final String methodName = "registerVASACertificate(): ";

		log.info(methodName + "Entry with username[" + username
				+ "], password[****], certificate[****]");

		VasaProviderInfo vpinfo = contextManager.registerVASACertificate(
				username, password, certificateStr);
		log.info(methodName + "Exit returning [vpInfo]");
		return vpinfo;
	}

	/**
	 * Unregisters (removes) VASA provider from vCenter server
	 * 
	 * @param existingCertificate
	 *            the existing certificates
	 * @throws InvalidCertificate
	 *             if certificate is invalid
	 * @throws InvalidSession
	 *             if the invocation was with invalid session Id
	 * 
	 */
	public void unregisterVASACertificate(String existingCertificate)
			throws InvalidCertificate, InvalidSession, StorageFault {
		// Mandatory function

		final String methodName = "unregisterVASACertificate(): ";

		log.info(methodName + "Entry with existingCertificate["
				+ (existingCertificate != null ? "****" : null) + "]");

		// run function
		contextManager.unregisterVASACertificate(existingCertificate);

		log.info(methodName + "Exit");
	}

	/**
	 * Returns VASA provider info with a new seesion Id. This session Id is used
	 * by vCenter for suture calls
	 * 
	 * @param usageContext
	 *            this object has host initiators and mount point information
	 * @return VasaProviderInfo instance with new session Id
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */

	public VasaProviderInfo setContext(UsageContext usageContext)
			throws InvalidArgument, InvalidSession, StorageFault {
		// Mandatory function

		final String methodName = "setContext(): ";

		log.info(methodName + "Entry with usageContext[" + usageContext + "]");

		if (usageContext == null) {
			log.error(methodName + " VC context is invalid: [" + usageContext
					+ "]");
			throw FaultUtil.InvalidArgument("VC context is invalid: ["
					+ usageContext + "]");
		}

		// run function

		VasaProviderInfo vpInfo = contextManager.setContext(usageContext);
		log.debug(methodName + "initializing alarms and events");
		// TODO: Discuss with Vasu where to move this line
		// _sosManager.initializeEventsAlarms();

		log.info(methodName + "Exit returning vasa provider info");
		return vpInfo;
	}

	/**
	 * Returns true if DRS migration capability is supported; false otherwise
	 * 
	 * @param srcUniqueId
	 *            Identifier for the source. The identifiers (source and
	 *            destination) can specify two storage LUNs or two filesystems
	 * @param dstUniqueId
	 *            Identifier for the destination. The identifiers (source and
	 *            destination) can specify two storage LUNs or two filesystems.
	 * @return true if DRS migration capability is supported; false otherwise
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 * @throws NotFound
	 *             if srcUniqueId or dstUniqueId is not found
	 */
	public boolean queryDRSMigrationCapabilityForPerformance(
			String srcUniqueId, String dstUniqueId, String entityType)
			throws InvalidArgument, NotFound, InvalidSession, StorageFault {

		final String methodName = "queryDRSMigrationCapabilityForPerformance(): ";

		log.info(methodName + "Entry with srcUniqueId[" + srcUniqueId
				+ "], dstUniqueId[" + dstUniqueId + "], entityType["
				+ entityType + "]");

		/*
		 * verify valid SSL and VASA Sessions.
		 */
		sslUtil.checkHttpRequest(true, true);

		SOSManager sosManager = contextManager.getSOSManager();
		boolean result = sosManager.queryDRSMigrationCapabilityForPerformance(
				srcUniqueId, dstUniqueId, entityType);

		log.info(methodName + "Exit returning [" + result + "]");

		return result;

	}

	/**
	 * Returns array details of the requested arrayIds
	 * 
	 * @param arrayId
	 *            array of arrayIds
	 * @return array of StorageArray objects having storage array details
	 *         respectively
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public StorageArray[] queryArrays(String[] arrayId) throws InvalidArgument,
			InvalidSession, StorageFault {
		// Mandatory function

		final String methodName = "queryArrays(): ";

		log.info(methodName + "Entry");

		if (arrayId != null) {
			log.info(methodName + "input array Ids: " + Arrays.asList(arrayId));
		}

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		UsageContext uc = contextManager.getUsageContext();

		if (Util.isEmpty(uc.getHostInitiator())) {

			StorageArray[] arrays = new StorageArray[0];
			log.info(methodName + "Exit returning storage arrays of size["
					+ (arrays != null ? arrays.length : 0) + "]");
			return arrays;

		}

		SOSManager sosManager = contextManager.getSOSManager();
		StorageArray[] arrays = sosManager.queryArrays(arrayId);

		log.info(methodName + "Exit returning storage arrays of size["
				+ (arrays != null ? arrays.length : 0) + "]");

		return arrays;
	}

	/**
	 * Returns unique identifiers of an entity type
	 * 
	 * @param entityType
	 *            one the values: StorageArray, StorageProcessor, StoragePort,
	 *            StorageLun, StorageCapability, StorageFileSystem
	 * @return array of unique identifiers for an entity type
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public String[] queryUniqueIdentifiersForEntity(String entityType)
			throws InvalidArgument, InvalidSession, StorageFault {
		// Mandatory function

		final String methodName = "queryUniqueIdentifiersForEntity(): ";

		log.info(methodName + "Entry with entityType[" + entityType + "]");

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);
		SOSManager sosManager = contextManager.getSOSManager();
		List<String> returnIdArray = new ArrayList<String>();

		try {
			
			if (EntityTypeEnum.StorageProcessor.getValue().equals(entityType)) {
				boolean supportsBlock = new Boolean(
						config.getConfigValue("config/service/storageTopology/storageArray/support-block-profile"));

				if (!supportsBlock) {
					log.error(methodName + "entity type [" + entityType
							+ "] is invalid since block profile is not supported");
					throw FaultUtil.InvalidArgument("entity type [" + entityType
							+ "] is invalid since block profile is not supported");
				}
			}

			if (EntityTypeEnum.StorageFileSystem.getValue().equals(entityType)) {
				boolean supportsFile = new Boolean(
						config.getConfigValue("config/service/storageTopology/storageArray/support-file-profile"));

				if (!supportsFile) {
					log.error(methodName + "entity type [" + entityType
							+ "] is invalid since file profile is not supported");
					throw FaultUtil.InvalidArgument("entity type [" + entityType
							+ "] is invalid since file profile is not supported");
				}
			}

			// run function

			if (EntityTypeEnum.StorageArray.getValue().equals(entityType)) {

				sosManager.sync();
				// TODO: Get array Id from Bourne once /zone uri is implemented
				// For now we are getting the array ID from config file
				String storageArrayId = sosManager.getArrayId();
				returnIdArray.add(storageArrayId);
			} else if (EntityTypeEnum.StorageProcessor.getValue().equals(
					entityType)) {

				// TODO: Get processor details from Bourne once REST API is
				// implemented
				// For now we are getting the processor ID from config file

				/*
				 * StorageProcessor[] processors = this
				 * .queryStorageProcessors(null); for (StorageProcessor p :
				 * processors) { returnIdArray.add(p.getUniqueIdentifier()); }
				 */

				returnIdArray = Arrays.asList(sosManager
						.getStorageProcessorId());

			} else if (EntityTypeEnum.StoragePort.getValue().equals(entityType)) {
				returnIdArray = sosManager.getStoragePortIds();
			} else if (EntityTypeEnum.StorageLun.getValue().equals(entityType)) {
				returnIdArray = sosManager.getVolumeIds();
			} else if (EntityTypeEnum.StorageFileSystem.getValue().equals(
					entityType)) {
				returnIdArray = sosManager.getFileSystemIds();
			} else if (EntityTypeEnum.StorageCapability.getValue().equals(
					entityType)) {
				returnIdArray = sosManager.getCosIds(true);

			} else {
				/*
				 * Unknown StorageEntity
				 */
				log.error(methodName + "Invalid entity type: [" + entityType
						+ "]");
				throw FaultUtil.InvalidArgument("Invalid entity type");
			}
		} catch (InvalidArgument ia) {
			log.error(methodName + "invalid argument", ia);
			throw (ia);
		} catch (Exception e) {
			log.error(methodName + "unknown exception occured", e);
			handleExceptionsAsStorageFault(e);
		}
		log.info(methodName + "Exit returning number of entities["
				+ returnIdArray.size() + "]");

		return returnIdArray.toArray(new String[0]);
	}

	/**
	 * Returns number of entities of an entity type
	 * 
	 * @param entityType
	 *            one the values: StorageArray, StorageProcessor, StoragePort,
	 *            StorageLun, StorageCapability, StorageFileSystem
	 * @return number of entities of an entity type
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public int getNumberOfEntities(String entityType) throws InvalidArgument,
			InvalidSession, StorageFault {
		// Mandatory function

		final String methodName = "getNumberOfEntities(): ";

		log.info(methodName + "Entry with inputs entityType[" + entityType
				+ "]");

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		// run function
		if (entityType == null) {
			log.error(methodName + "entity type is null !");
			throw FaultUtil.InvalidArgument("Entity type is null");
		}

		

		/*
		 * Since there is no REST API to the the number of objects (file share,
		 * lun, cos) we have to get the identifiers and return the count
		 */

		String[] ids = this.queryUniqueIdentifiersForEntity(entityType);

		if (ids != null) {
			log.info(methodName + "Exit returning " + ids.length);
			return ids.length;
		} else {
			log.info(methodName + "Exit returning 0");
			return 0;
		}

	}

	/**
	 * Returns events since <code>lastEventId</code>
	 * 
	 * @param lastEventId
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public StorageEvent[] getEvents(long lastEventId) throws LostEvent,
			InvalidArgument, InvalidSession, StorageFault {
		// Mandatory function
		final String methodName = "getEvents(): ";

		log.info(methodName + "Entry with lastEventId[" + lastEventId + "]");

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		// verify argument
		Util.eventOrAlarmIdIsValid(lastEventId);

		// run function

		// StorageEvent[] events = _sosManager.getEvents(lastEventId);

		SOSManager sosManager = contextManager.getSOSManager();
		StorageEvent[] events = sosManager.getEvents(lastEventId);
		// contextManager.getUsageContext(), lastEventId);

		log.info(methodName + "Exit returning events of size [" + events.length
				+ "]");
		return events;

		// return null;
	}

	/**
	 * Returns alarms since <code>lastAlarmId</code>
	 * 
	 * @param lastAlarmId
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public StorageAlarm[] getAlarms(long lastAlarmId) throws InvalidArgument,
			LostAlarm, InvalidSession, StorageFault {
		// Mandatory function

		final String methodName = "getAlarms(): ";

		log.info(methodName + "Entry with lastAlarmId[" + lastAlarmId + "]");

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		// verify argument
		Util.eventOrAlarmIdIsValid(lastAlarmId);

		// run function

		SOSManager sosManager = contextManager.getSOSManager();
		StorageAlarm[] alarms = sosManager.getAlarms(lastAlarmId);

		log.info(methodName + "Exit returning alarms of size[" + alarms.length
				+ "]");
		return alarms;
	}

	/**
	 * Returns the URIs of .vmsg files which will be used for events and alarms
	 * 
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public MessageCatalog[] queryCatalog() throws InvalidSession, StorageFault {
		// Mandatory function

		final String methodName = "queryCatalog(): ";

		log.info(methodName + "Entry");

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		// run function
		MessageCatalog[] msgCalalog = contextManager.queryCatalog();

		log.info(methodName + "Exit returning catalogs of size["
				+ msgCalalog.length + "]");

		return msgCalalog;
	}

	/**
	 * Block Profile Required Functions
	 */

	/**
	 * Returns unique identifiers for LUNs for the give array Id
	 * 
	 * @param arrayUniqueId
	 *            the array Id
	 * @return the array of unique identifiers of LUNs
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public String[] queryUniqueIdentifiersForLuns(String arrayUniqueId)
			throws InvalidArgument, NotFound, InvalidSession, StorageFault,
			NotImplemented {

		final String methodName = "queryUniqueIdentifiersForLuns(): ";

		log.info(methodName + "Entry with arrayUniqueId[" + arrayUniqueId + "]");

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		SOSManager sosManager = contextManager.getSOSManager();

		String[] ids = sosManager.queryUniqueIdentifiersForLuns(arrayUniqueId);

		log.info(methodName + "Exit returning ids of size[" + ids.length + "]");
		return ids;
	}

	/**
	 * Returns the association between given array Ids and their processors
	 * respectively
	 * 
	 * @param arrayId
	 *            string array of array Ids
	 * @return
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public VasaAssociationObject[] queryAssociatedProcessorsForArray(
			String[] arrayId) throws InvalidArgument, InvalidSession,
			StorageFault, NotImplemented {

		final String methodName = "queryAssociatedProcessorsForArray(): ";

		log.info(methodName + "Entry");

		if (arrayId != null) {
			log.info(methodName + "input array Ids: " + Arrays.asList(arrayId));
		}

		// verify Block profile implmented
		// inventoryManager.profileImplemented(ProfileEnum._BlockDeviceProfile);

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		// run function
		SOSManager sosManager = contextManager.getSOSManager();
		VasaAssociationObject[] vasaAsssociationObjs = sosManager
				.queryAssociatedProcessorsForArray(arrayId);

		log.info(methodName + "Exit returning vasaAssociationObjs of size["
				+ vasaAsssociationObjs.length + "]");

		return vasaAsssociationObjs;
	}

	/**
	 * Returns the association between given processor Ids and their ports
	 * respectively
	 * 
	 * @param processorId
	 *            string array of processor Id
	 * @return array of association objects
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public VasaAssociationObject[] queryAssociatedPortsForProcessor(
			String[] processorId) throws InvalidArgument, InvalidSession,
			StorageFault, NotImplemented {

		final String methodName = "queryAssociatedPortsForProcessor(): ";

		log.info(methodName + "Entry");

		if (processorId != null) {
			log.info(methodName + "input processor Ids["
					+ Arrays.asList(processorId) + "]");
		}

		// verify Block profile implmented

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		// run function
		SOSManager sosManager = contextManager.getSOSManager();
		VasaAssociationObject[] vasaAsssociationObjs = sosManager
				.queryAssociatedPortsForProcessor(processorId);

		log.info(methodName
				+ "Exit returning vasa association objects of size["
				+ vasaAsssociationObjs.length + "]");

		return vasaAsssociationObjs;
	}

	/**
	 * Returns storage port details for the given processor Ids
	 * 
	 * @param processorId
	 *            string array of processor Id
	 * @return details of given processor Ids
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public StoragePort[] queryStoragePorts(String[] processorId)
			throws InvalidArgument, InvalidSession, StorageFault,
			NotImplemented {

		final String methodName = "queryStoragePorts(): ";

		log.info(methodName + "Entry with  processor Ids");

		if (processorId != null)
			log.debug(methodName + "processor Ids["
					+ Arrays.asList(processorId) + "]");

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		// run function
		SOSManager sosManager = contextManager.getSOSManager();
		StoragePort[] ports = sosManager.queryStoragePorts(processorId);

		log.info(methodName + "Exit returning storage ports of size["
				+ ports.length + "]");

		return ports;
	}

	/**
	 * Returns association details between given port Id and the luns
	 * respectively
	 * 
	 * @param portId
	 *            array of port Ids
	 * @return the association details of every given port to a lun respectively
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public VasaAssociationObject[] queryAssociatedLunsForPort(String[] portId)
			throws InvalidArgument, InvalidSession, StorageFault,
			NotImplemented {

		final String methodName = "queryAssociatedLunsForPort(): ";

		log.info(methodName + "Entry");

		if (portId != null) {
			log.debug(methodName + "input port Ids[" + Arrays.asList(portId) + "]");
		}

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		// run function
		SOSManager sosManager = contextManager.getSOSManager();
		VasaAssociationObject[] vasaAsssociationObjs = sosManager
				.queryAssociatedLUNsForPort(portId);

		log.info(methodName
				+ "Exit returning vasa association objects of size["
				+ vasaAsssociationObjs.length + "]");

		return vasaAsssociationObjs;
	}

	/**
	 * Returns processor details for the given processor Ids
	 * 
	 * @param processorId
	 *            array of processor Ids
	 * @return array of <code>StorageProcessor</code> objects having processor
	 *         details
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public StorageProcessor[] queryStorageProcessors(String[] processorId)
			throws InvalidArgument, InvalidSession, StorageFault,
			NotImplemented {

		final String methodName = "queryStorageProcessors(): ";

		log.info(methodName + "Entry");

		if (processorId != null)
			log.debug(methodName + "input processorIds["
					+ Arrays.asList(processorId) + "]");

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		// run function
		SOSManager sosManager = contextManager.getSOSManager();
		StorageProcessor[] processors = sosManager
				.queryStorageProcessors(processorId);

		log.info(methodName + "Exit returning processors of size["
				+ processors.length + "]");
		return processors;
	}

	/**
	 * Returns LUN details for the given lun Ids
	 * 
	 * @param lunId
	 *            array of lunId Ids
	 * @return array of <code>StorageLun</code> objects having LUN details
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public StorageLun[] queryStorageLuns(String[] lunId)
			throws InvalidArgument, InvalidSession, StorageFault,
			NotImplemented {

		final String methodName = "queryStorageLuns(): ";

		log.info(methodName + "Entry");

		if (lunId != null) {
			log.debug(methodName + "inputs processorIds["
					+ Arrays.asList(lunId) + "]");
		}

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		// run function
		SOSManager sosManager = contextManager.getSOSManager();
		StorageLun[] luns = sosManager.queryStorageLuns(lunId);

		log.info(methodName + "Exit returning array of luns of size["
				+ luns.length + "]");

		return luns;
	}

	/**
	 * FileSystem Profile Required Functions
	 */

	/**
	 * Returns LUN details for the given lun Ids
	 * 
	 * @param arrayId
	 *            the storage array Id
	 * @return array of <code>String</code> objects having file system Ids
	 *         details
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public String[] queryUniqueIdentifiersForFileSystems(String arrayId)
			throws InvalidArgument, NotFound, InvalidSession, StorageFault,
			NotImplemented {

		final String methodName = "queryUniqueIdentifiersForFileSystems(): ";

		log.info(methodName + "Entry with arrayId[" + arrayId + "]");

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		Util.uniqueIdIsValid(arrayId);

		SOSManager sosManager = contextManager.getSOSManager();

		// run function
		String[] ids = sosManager.queryUniqueIdentifiersForFileSystems(arrayId);

		log.info(methodName + "Exit returning ids of size[" + ids.length + "]");

		return ids;
	}

	/**
	 * Returns file system details for the given lun Ids
	 * 
	 * @param fileSystem
	 *            array of file system Ids
	 * @return array of <code>StorageFileSystem</code> objects having file
	 *         system details
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public StorageFileSystem[] queryStorageFileSystems(String[] fileSystem)
			throws InvalidArgument, InvalidSession, StorageFault,
			NotImplemented {

		final String methodName = "queryStorageFileSystems(): ";

		log.info(methodName + "Entry");

		if (fileSystem != null) {
			log.info(methodName + "input file system Ids: "
					+ Arrays.asList(fileSystem));
		}

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		// run function
		SOSManager sosManager = contextManager.getSOSManager();
		StorageFileSystem[] fileSystems = sosManager
				.queryStorageFileSystems(fileSystem);

		log.info(methodName + "Exit returning fileSystems of size["
				+ fileSystems.length + "]");

		return fileSystems;
	}

	/**
	 * Capability Profile Required Functions
	 */

	/**
	 * Returns storage capability details for the given capId Ids
	 * 
	 * @param capId
	 *            array of storage capability Ids
	 * @return array of <code>StorageCapability</code> objects having storage
	 *         capability details details
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public StorageCapability[] queryStorageCapabilities(String[] capId)
			throws InvalidArgument, InvalidSession, StorageFault,
			NotImplemented {

		final String methodName = "queryStorageCapabilities(): ";

		log.info(methodName + "Entry");
		if (capId != null) {
			log.info(methodName + "input storage capability Ids: "
					+ Arrays.asList(capId));
		}

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		// run function
		SOSManager sosManager = contextManager.getSOSManager();
		StorageCapability[] capabilities = sosManager
				.queryStorageCapabilities(capId);

		log.info(methodName + "Exit returning array of capabilities of size["
				+ capabilities.length + "]");

		return capabilities;
	}

	/**
	 * Block and Capability Profile Required Functions
	 */

	/**
	 * Returns the association detail between given LUN Ids and the storage
	 * capabilities
	 * 
	 * @param capId
	 *            array of LUN Ids
	 * @return array of <code>VasaAssociationObject</code> objects having
	 *         association detail between given LUN Ids and the storage
	 *         capabilities
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public VasaAssociationObject[] queryAssociatedCapabilityForLun(
			String[] lunId) throws InvalidArgument, InvalidSession,
			StorageFault, NotImplemented {

		final String methodName = "queryAssociatedCapabilityForLun(): ";

		log.info(methodName + "Entry");

		if (lunId != null) {
			log.info(methodName + "input LUN Ids: " + Arrays.asList(lunId));
		}

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		// run function
		SOSManager sosManager = contextManager.getSOSManager();
		VasaAssociationObject[] objs = sosManager
				.queryAssociatedCapabilityForLun(lunId);

		log.info(methodName
				+ "Exit returning vasa association objects of size["
				+ objs.length + "]");

		return objs;
	}

	/**
	 * FileSystem and Capability Profile Required Functions
	 */

	/**
	 * Returns the association detail between given file system Ids and the
	 * storage capabilities
	 * 
	 * @param capId
	 *            array of file system Ids
	 * @return array of <code>VasaAssociationObject</code> objects having
	 *         association detail between given file system Ids and the storage
	 *         capabilities
	 * @throws InvalidArgument
	 *             if usage context is incorrect
	 * @throws InvalidSession
	 *             if session Id is invalid
	 * @throws StorageFault
	 *             if there is a failure in the underlying storage
	 */
	public VasaAssociationObject[] queryAssociatedCapabilityForFileSystem(
			String[] fsId) throws InvalidArgument, InvalidSession,
			StorageFault, NotImplemented {

		final String methodName = "queryAssociatedCapabilityForFileSystem(): ";

		log.info(methodName + "Entry");
		if (fsId != null) {
			log.info(methodName + "input file system Ids: "
					+ Arrays.asList(fsId));
		}

		// verify valid SSL and VASA Sessions.
		sslUtil.checkHttpRequest(true, true);

		// run function
		SOSManager sosManager = contextManager.getSOSManager();
		VasaAssociationObject[] objs = sosManager
				.queryAssociatedCapabilityForFileSystem(fsId);

		log.info(methodName
				+ "Exit returning vasa association objects of size["
				+ objs.length + "]");

		return objs;
	}

	/**
	 * Init is called when a new instance of the implementing class has been
	 * created. This occurs in sync with session/ServiceContext creation. This
	 * method gives classes a chance to do any setup work (grab resources,
	 * establish connections, etc) before they are invoked by a service request.
	 * 
	 * @param sc
	 * @throws org.apache.axis2.AxisFault
	 */
	@Override
	public void init(ServiceContext sc) throws org.apache.axis2.AxisFault {

		final String methodName = "init(): ";

		log.info(methodName + "Entry ");
		try {
			log.trace(methodName + "System properties:\n"
					+ System.getProperties());
			log.info(methodName + "SOS VASA API Version = "
					+ FaultUtil.getVasaApiVersion());

			// tomcatRoot = System.getProperty("catalina.home");
			String path = System.getProperty("vasa.keystore");

			log.debug(methodName + "Full path of trustStoreFileName:" + path);

			/**
			 * Get configurable parameters
			 */
			String password = (String) sc.getAxisService().getParameterValue(
					TRUSTSTOREPASSWORD_PARAM);
			if (password != null) {
				trustStorePassword = password;
				log.debug(methodName + "parameter: " + TRUSTSTOREPASSWORD_PARAM
						+ " found");
			}
			else {
				trustStorePassword = new String("");
			}

			contextManager = ContextManagerImpl.getInstance();

			config = Config.getInstance();

			/**
			 * Setup globals
			 */

			sslUtil = new SSLUtil(path, trustStorePassword, false);
			contextManager.init(sslUtil);

			contextManager.initializeVasaProviderInfo();

			log.debug(methodName + "Service life cycle initiated");

			log.info(methodName + "Exit ");
		} catch (Exception e) {
			/**
			 * Be careful here. Catch the expection and print it. If this
			 * routine returns an Exception it is silently ignored by tomcat.
			 */
			log.debug("Init exception: " + e);
		}
	}

	protected void handleExceptionsAsStorageFault(Exception e)
			throws InvalidArgument, InvalidSession, StorageFault {
		if (e instanceof InvalidArgument) {
			throw (InvalidArgument) e;
		} else if (e instanceof InvalidSession) {
			throw (InvalidSession) e;
		} else if (e instanceof StorageFault) {
			throw (StorageFault) e;
		} else {

			StorageFault sfe = FaultUtil.StorageFault(e);
			sfe.setStackTrace(e.getStackTrace());
			throw sfe;
		}
	}

	@Override
	public void destroy(ServiceContext sc) {

	}

}


