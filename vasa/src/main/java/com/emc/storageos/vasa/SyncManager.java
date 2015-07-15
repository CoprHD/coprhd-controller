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
package com.emc.storageos.vasa;

import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.emc.storageos.vasa.data.internal.CoS;
import com.emc.storageos.vasa.data.internal.CoS.CoSElement;
import com.emc.storageos.vasa.data.internal.CoS.CoSList;
import com.emc.storageos.vasa.data.internal.Event.EventList;
import com.emc.storageos.vasa.data.internal.FileShare;
import com.emc.storageos.vasa.data.internal.FileShare.AssociatedResource;
import com.emc.storageos.vasa.data.internal.FileShare.FileSystemExports;
import com.emc.storageos.vasa.data.internal.FileShare.SearchResults;
import com.emc.storageos.vasa.data.internal.StatList;
import com.emc.storageos.vasa.data.internal.StoragePool;
import com.emc.storageos.vasa.data.internal.StoragePort;
import com.emc.storageos.vasa.data.internal.StorageSystem;
import com.emc.storageos.vasa.data.internal.Tenant;
import com.emc.storageos.vasa.data.internal.Volume;
import com.emc.storageos.vasa.data.internal.Volume.AssociatedPool;
import com.emc.storageos.vasa.data.internal.Volume.Itls;
import com.emc.storageos.vasa.data.internal.Volume.Itls.Itl;
import com.emc.storageos.vasa.data.internal.Volume.Itls.Itl.Device;
import com.emc.storageos.vasa.data.internal.Volume.Itls.Itl.Target;
import com.emc.storageos.vasa.fault.SOSAuthenticationFailure;
import com.emc.storageos.vasa.fault.SOSFailure;
import com.emc.storageos.vasa.util.RESTClientUtil;
import com.sun.jersey.api.client.UniformInterfaceException;

/**
 * 
 * All functions of this class query Bourne to get required objects
 * 
 */
public class SyncManager {

	private static final Logger log = Logger.getLogger(SyncManager.class);

	private RESTClientUtil _client;
	private String _providerTenantUri;
	private List<String> _fileCosIdList;
	private List<String> _blockCosIdList;
	private List<CoS> _fileCosDetailList;
	private List<CoS> _blockCosDetailList;

	public SyncManager(String baseURL) {
		_client = new RESTClientUtil(baseURL);
		log.trace("Sync manager initialised with baseURL:" + baseURL);
	}

	/**
	 * Makes call to Bourne to verify login attempt
	 * 
	 * @param username
	 * @param password
	 * @throws SOSAuthenticationFailure
	 *             on invalid login attempt
	 * @throws SOSFailure
	 */
	public synchronized void verifyLogin(String username, String password)
			throws SOSAuthenticationFailure, SOSFailure {

		final String methodName = "verifyLogin(): ";
		final String FILE_COS_LIST_URI = "/file/vpools";

		try {
			_client.setLoginCredentials(username, password);
			_client.queryObject(FILE_COS_LIST_URI, String.class);
		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			if (e.toString().contains("403 Forbidden")) {
				throw new SOSAuthenticationFailure(
						"Incorrect login credentials");
			}
			if (e.toString().contains("401 Unauthorized")) {
				throw new SOSAuthenticationFailure(
						"Incorrect login credentials");
			}
		}
	}

	/**
	 * Caches all tenant Ids and project Ids required for subsequent calls
	 * 
	 * @throws SOSFailure
	 */
	public synchronized void syncAll() throws SOSFailure {

		final String methodName = "syncAll(): ";
		log.trace(methodName + "Entry");

		_fileCosIdList = null;
		_blockCosIdList = null;
		_blockCosDetailList = null;
		_fileCosDetailList = null;

		log.trace(methodName + "Exit");

	}

	/**
	 * Makes call to Bourne to get tenant provider Uri
	 * 
	 * @return tenant provider Uri
	 * @throws SOSFailure
	 *             if call to Bourne fails
	 */
	private String getProviderTenantId() throws SOSFailure {

		final String methodName = "getProviderTenantId(): ";
		log.trace(methodName + "Entry");

		String rootTenantUri = null;

		final String ROOT_TENANT_URI = "/tenant";

		try {

			Tenant tenant = _client.queryObject(ROOT_TENANT_URI, Tenant.class);
			rootTenantUri = tenant.getId();

		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}
		log.trace(methodName + "Exit returing tenant uri[" + rootTenantUri
				+ "]");
		return rootTenantUri;

	}

	private List<String> fetchStoragePortsIdsByHostInitiators(
			String csvSeparatedInitiatorList) throws SOSFailure {

		final String methodName = "fetchStoragePortsIdsByHostInitiators(): ";
		log.trace(methodName + "Entry with input: csvSeparatedInitiatorList["
				+ csvSeparatedInitiatorList + "]");

		Set<String> storagePortIdSet = new HashSet<String>();
		for (Itls itlObj : this.fetchExportITLS(csvSeparatedInitiatorList)) {

			if (itlObj != null) {

				for (Itl itl : itlObj.getItls()) {
					Target target = itl.getTarget();
					if (target != null && target.getId() != null) {
						storagePortIdSet.add(target.getId());
					}

				}
			}

		}

		log.trace(methodName + "Exit returning storage port Id list of size["
				+ storagePortIdSet.size() + "]");
		return new ArrayList<String>(storagePortIdSet);
	}

	private Hashtable<String, List<String>> fetchPortToVolumeTable(
			String csvSeparatedInitiatorList) throws SOSFailure {

		final String methodName = "fetchPortToVolumeTable(): ";
		log.trace(methodName + "Entry with input: csvSeparatedInitiatorList["
				+ csvSeparatedInitiatorList + "]");

		// Hashtable<String, String> table = new Hashtable<String, String>();

		Hashtable<String, List<String>> table2 = new Hashtable<String, List<String>>();

		for (Itls itlObj : this.fetchExportITLS(csvSeparatedInitiatorList)) {

			if (itlObj != null) {
				for (Itl itl : itlObj.getItls()) {
					Target target = itl.getTarget();
					String portId = target.getId();
					// Device device = itl.getDevice();

					/*
					 * if (target != null && device != null) {
					 * table.put(target.getId(), device.getId()); }
					 */

					if (portId != null) {
						Set<String> volIds = new HashSet<String>();

						for (Itl itl2 : itlObj.getItls()) {
							Target port = itl2.getTarget();
							Device volume = itl2.getDevice();
							if (portId.equals(port.getId())) {
								volIds.add(volume.getId());
							}
						}
						table2.put(portId, new ArrayList<String>(volIds));

					}

				}
			}

		}

		log.trace(methodName + "Exit returning port-volume table of size["
				+ table2.size() + "]");
		return table2;
	}

	private List<Itls> fetchExportITLS(String csvSeparatedInitiatorList)
			throws SOSFailure {

		final String methodName = "fetchExportITLS(): ";
		log.trace(methodName + "Entry with input: csvSeparatedInitiatorList["
				+ csvSeparatedInitiatorList + "]");

		final String ITL_LIST_URI = "/block/exports/?initiators=%s";

		List<Itls> itlList = new ArrayList<Itls>();

		// for (String initiatorId : csvSeparatedInitiatorList.split(",")) {

		try {

			Itls itlObj = _client.queryObject(
					String.format(ITL_LIST_URI, csvSeparatedInitiatorList),
					Itls.class);

			if (itlObj != null && itlObj.getItls() != null) {
				itlList.add(itlObj);
			}

		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {

			/*
			 * if (e.getMessage().contains("400 Bad Request")) {
			 * 
			 * log.debug(methodName +
			 * "No export information is available for initiator[" + initiatorId
			 * + "]"); continue; } else {
			 */
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
			// }

		}
		// }

		log.trace(methodName + "Exit ITL list of size[" + itlList.size() + "]");

		return itlList;

	}

	private List<StoragePort> fetchStoragePortsByHostInitiators(
			String csvSeparatedInitiatorList) throws SOSFailure {

		final String methodName = "fetchStoragePortsByHostInitiators(): ";
		log.trace(methodName + "Entry with input: csvSeparatedInitiatorList["
				+ csvSeparatedInitiatorList + "]");

		Set<String> storagePortLinkSet = new HashSet<String>();
		List<StoragePort> storagePortList = new ArrayList<StoragePort>();

		for (Itls itlObj : this.fetchExportITLS(csvSeparatedInitiatorList)) {

			if (itlObj != null) {

				for (Itl itl : itlObj.getItls()) {
					Target target = itl.getTarget();
					if (target != null && target.getId() != null) {
						storagePortLinkSet.add(target.getLink().getHref());
					}

				}
			}

		}

		// try {
		if (storagePortLinkSet != null) {
			for (String storagePortLink : storagePortLinkSet) {
				StoragePort storagePort = this
						.fetchStoragePortByHref(storagePortLink);
				storagePortList.add(storagePort);
			}
		}

		log.trace(methodName + "Exit returning storage port list of size["
				+ storagePortList.size() + "]");
		return new ArrayList<StoragePort>(storagePortList);
	}

	private StoragePort fetchStoragePortByHref(String href) throws SOSFailure {

		final String methodName = "fetchStoragePortByHref(): ";
		log.trace(methodName + "Entry with input: href[" + href + "]");

		StoragePort storagePort = null;

		try {

			storagePort = _client.queryObject(href, StoragePort.class);
			if (storagePort == null) {
				storagePort = new StoragePort();
			}

		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}

		log.trace(methodName + "Exit returning storage port :[" + storagePort
				+ "]");
		return storagePort;
	}

	private StoragePort fetchStoragePort(String storageSystemId,
			String storagePortId) throws SOSFailure {

		final String methodName = "fetchStoragePort(): ";
		log.trace(methodName + "Entry with input: storageSystemId["
				+ storageSystemId + "] storagePortId[" + storagePortId + "]");

		StoragePort storagePort = null;
		final String STORAGE_PORT_DETAIL_URI1 = "/vdc/storage-systems/%s/storage-ports/%s";
		final String STORAGE_PORT_DETAIL_URI2 = "/vdc/storage-ports/%s";

		if (null == storageSystemId) {
			storagePort = this.fetchStoragePortByHref(String.format(
					STORAGE_PORT_DETAIL_URI2, storagePortId));
		} else {
			storagePort = this.fetchStoragePortByHref(String.format(
					STORAGE_PORT_DETAIL_URI1, storageSystemId, storagePortId));
		}

		log.trace(methodName + "Exit returning storage port :[" + storagePort
				+ "]");
		return storagePort;
	}

	private List<String> fetchVolumeIdsByHostInitiators(
			String csvSeparatedInitiatorList) throws SOSFailure {

		final String methodName = "fetchVolumeIdsByHostInitiators(): ";
		log.trace(methodName + "Entry with input: csvSeparatedInitiatorList["
				+ csvSeparatedInitiatorList + "]");

		Set<String> volumeIdSet = new HashSet<String>();

		for (Itls itlObj : this.fetchExportITLS(csvSeparatedInitiatorList)) {

			if (itlObj != null) {
				for (Itl itl : itlObj.getItls()) {
					Device device = itl.getDevice();
					if (device != null && device.getId() != null) {
						volumeIdSet.add(device.getId());
					}

				}
			}

		}

		log.trace(methodName + "Exit returning volume ID list of size["
				+ volumeIdSet.size() + "]");
		return new ArrayList<String>(volumeIdSet);
	}

	private String fetchFileSystemIdByMountPath(String mountPath)
			throws SOSFailure {

		final String methodName = "fetchFileSystemIdByMountPath(): ";

		final String FILESYSTEM_SEARCH_BY_MOUNTPATH = "/file/filesystems/search?mountPath=%s";

		log.trace(methodName + "Entry with input: mountPath[" + mountPath + "]");

		String filesystemId = null;

		try {

			SearchResults results = _client.queryObject(
					String.format(FILESYSTEM_SEARCH_BY_MOUNTPATH, mountPath),
					FileShare.SearchResults.class);

			if (results != null) {
				AssociatedResource resouce = results.getResource();
				if (resouce != null) {
					String tempFilesystemId = resouce.getId();

					FileSystemExports exports = fetchFileSystemExports(tempFilesystemId);

					if (exports != null && exports.getFsExportList() != null
							&& !exports.getFsExportList().isEmpty()) {
						filesystemId = tempFilesystemId;
					}
				}
			}
		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}

		log.trace(methodName + "Exit returning filesystem ID[" + filesystemId
				+ "]");
		return filesystemId;
	}

	/**
	 * Returns details of all active volumes
	 * 
	 * @return list of <code>Volume</code>
	 * @throws SOSFailure
	 */
	private List<Volume> fetchVolumeDetailsById(List<String> volumeIdList)
			throws SOSFailure {

		final String methodName = "fetchVolumeDetailsById(): ";

		final String VOLUME_DETAIL_URI = "/block/volumes/%s";

		log.trace(methodName + "Entry with input: volumeIdList[" + volumeIdList
				+ "]");

		List<Volume> volumeDetailList = new ArrayList<Volume>();

		try {
			if (volumeIdList != null) {

				for (String volumeId : volumeIdList) {
					Volume volume = _client.queryObject(
							String.format(VOLUME_DETAIL_URI, volumeId),
							Volume.class);

					if (volume != null) {

						if (!volume.isInactive() && volume.getId() != null) {
							volumeDetailList.add(volume);
							log.trace(methodName + volume);
						}

					}
				}
			}
		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}

		log.trace(methodName + "Exit returning volume list of size["
				+ volumeDetailList.size() + "]");
		return volumeDetailList;
	}

	private List<FileShare> fetchFileShareDetailById(
			List<String> fileSystemIdList) throws SOSFailure {

		final String methodName = "fetchFileShareDetailById(): ";
		log.trace(methodName + "Entry with input: " + fileSystemIdList);

		List<FileShare> fileshareDetailList = new ArrayList<FileShare>();

		final String FILESYSTEM_DETAIL_URI = "/file/filesystems/%s";
		try {

			if (fileSystemIdList != null) {

				for (String fileSystemId : fileSystemIdList) {
					FileShare fileshare = _client.queryObject(
							String.format(FILESYSTEM_DETAIL_URI, fileSystemId),
							FileShare.class);

					if (fileshare != null) {
						if (!fileshare.isInactive()
								&& fileshare.getId() != null) {
							fileshareDetailList.add(fileshare);
							log.trace(methodName + fileshare);

						}

					}
				}
			}
		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}

		log.trace(methodName + "Exit returning file system list of size["
				+ fileshareDetailList.size() + "]");

		return fileshareDetailList;
	}

	/**
	 * Returns list of file Cos Ids
	 * 
	 * @return list of file CoS ids
	 * @throws SOSFailure
	 */
	private List<String> fetchFileCosIdList() throws SOSFailure {

		final String methodName = "fetchFileCosIdList(): ";
		log.trace(methodName + "Entry");

		final String FILE_COS_URI = "/file/vpools";

		List<String> fileCosIdList = new ArrayList<String>();

		try {
			CoSList cosElemList = _client.queryObject(FILE_COS_URI,
					CoSList.class);

			if (cosElemList != null && cosElemList.getCosElements() != null) {
				for (CoSElement elem : cosElemList.getCosElements()) {
					if (elem != null) {
						fileCosIdList.add(elem.getId());
					}
				}
			}

			log.trace(methodName + "File CoS Ids: " + fileCosIdList);

			if (cosElemList != null && fileCosIdList != null) {
				log.trace(methodName + "Exit returning cos list of size["
						+ fileCosIdList.size() + "]");
				return fileCosIdList;

			}
			log.trace(methodName + "Exit returning cos list of size[0]");
			return new ArrayList<String>();

		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}
	}

	/**
	 * Returns list of file Cos Ids
	 * 
	 * @return list of file Cos Ids
	 * @throws SOSFailure
	 */
	private List<String> fetchBlockCosIdList() throws SOSFailure {

		final String methodName = "fetchBlockCosIdList(): ";
		log.trace(methodName + "Entry");

		final String BLOCK_COS_URI = "/block/vpools";

		List<String> blockCosIdList = new ArrayList<String>();

		try {
			CoSList cosElemList = _client.queryObject(BLOCK_COS_URI,
					CoSList.class);

			if (cosElemList != null && cosElemList.getCosElements() != null) { 
				for (CoSElement elem : cosElemList.getCosElements()) {
					if (elem != null) {
						blockCosIdList.add(elem.getId());
					}

				}
			}

			log.trace(methodName + "Block CoS Ids: " + blockCosIdList);

			if (cosElemList != null && blockCosIdList != null) {
				log.trace(methodName + "Exit returning cos list of size["
						+ blockCosIdList.size() + "]");
				return blockCosIdList;
			}
			log.trace(methodName + "Exit returning cos list of size[0]");
			return new ArrayList<String>();

		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}
	}

	/**
	 * Returns list of all active Block related CoSes with their details
	 * 
	 * @return list of all active Block related CoSes with their details
	 * @throws SOSFailure
	 */
	private List<CoS> fetchDetailsOfAllBlockCos() throws SOSFailure {

		final String methodName = "fetchDetailsOfAllBlockCos(): ";
		log.trace(methodName + "Entry");

		final String BLOCK_COS_DETAIL_URI = "/block/vpools/%s";
		List<CoS> blockCosIdList = new ArrayList<CoS>();

		try {

			for (String cosId : _blockCosIdList) {

				CoS.BlockCoS cos = _client.queryObject(
						String.format(BLOCK_COS_DETAIL_URI, cosId),
						CoS.BlockCoS.class);

				if (cos.isInactive() == false && cos.getId() != null) {
					blockCosIdList.add(cos);
					log.trace(methodName + cos);
				}

			}
			log.trace(methodName + "Exit returning cos list of size["
					+ blockCosIdList.size() + "]");

			return blockCosIdList;

		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}
	}

	/**
	 * Returns list of all active Block related CoSes with their details
	 * 
	 * @return list of all active Block related CoSes with their details
	 * @throws SOSFailure
	 */
	private List<CoS> fetchDetailsOfAllFileCos() throws SOSFailure {

		final String methodName = "fetchDetailsOfAllFileCos(): ";
		log.trace(methodName + "Entry");

		final String FILE_COS_DETAIL_URI = "/file/vpools/%s";
		List<CoS> fileCosIdList = new ArrayList<CoS>();

		try {

			for (String cosId : _fileCosIdList) {
				CoS.FileCoS cos = _client.queryObject(
						String.format(FILE_COS_DETAIL_URI, cosId),
						CoS.FileCoS.class);

				if (cos.isInactive() == false && cos.getId() != null) {
					fileCosIdList.add(cos);
					log.trace(methodName + cos);
				}
			}

			log.trace(methodName + "Exit returning cos list of size["
					+ fileCosIdList.size() + "]");

			return fileCosIdList;

		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}
	}

	/**
	 * This method returns list of fileshares with their details
	 * 
	 * @return the _fileshareList
	 * @throws SOSFailure
	 *             if call(s) to Bourne fails
	 */
	public synchronized List<FileShare> getFileSystemDetailList(
			List<String> fileSystemIdList) throws SOSFailure {

		return this.fetchFileShareDetailById(fileSystemIdList);
	}

	/**
	 * @return the _fileCosDetailList
	 * @throws SOSFailure
	 *             if call(s) to Bourne fails
	 */
	public synchronized List<CoS> getFileCosDetailList() throws SOSFailure {

		if (_fileCosIdList == null) {
			_fileCosIdList = this.fetchFileCosIdList();
			_fileCosDetailList = this.fetchDetailsOfAllFileCos();
		}
		return _fileCosDetailList;
	}

	/**
	 * @return the _blockCosDetailList
	 * @throws SOSFailure
	 *             if call(s) to Bourne fails
	 */
	public synchronized List<CoS> getBlockCosDetailList() throws SOSFailure {

		if (_blockCosIdList == null) {
			_blockCosIdList = this.fetchBlockCosIdList();
			_blockCosDetailList = this.fetchDetailsOfAllBlockCos();
		}

		return _blockCosDetailList;
	}

	/**
	 * Returns list of all active CoSes with their details
	 * 
	 * @return List of <code>CoS</code> objects
	 * @throws SOSFailure
	 *             if call(s) to Bourne fails
	 */
	public synchronized List<CoS> getCosDetailList() throws SOSFailure {

		List<CoS> cosList = new ArrayList<CoS>();

		cosList.addAll(this.getBlockCosDetailList());
		cosList.addAll(this.getFileCosDetailList());

		return cosList;

	}

	public synchronized void resetCoS() {
			_blockCosIdList = null;
			_fileCosIdList = null;
			_blockCosDetailList = null;
			_fileCosDetailList = null;
	}

	/**
	 * Returns list of all active storage ports with their details based on
	 * given port Ids
	 * 
	 * @param portIds
	 *            List of portIds
	 * @return List of <code>StoragePort</code> objects
	 * @throws SOSFailure
	 *             if call(s) to Bourne fails
	 */
	public synchronized List<StoragePort> getStoragePorts(
			String csvSeparatedInitiatorList) throws SOSFailure {

		return this
				.fetchStoragePortsByHostInitiators(csvSeparatedInitiatorList);

	}

	public synchronized List<String> getStoragePortIdList(
			String csvSeparatedInitiatorList) throws SOSFailure {

		return this
				.fetchStoragePortsIdsByHostInitiators(csvSeparatedInitiatorList);

	}

	public synchronized StoragePort getStoragePort(String storageSystemId,
			String storagePortId) throws SOSFailure {

		return this.fetchStoragePort(storageSystemId, storagePortId);

	}

	public synchronized StoragePort getStoragePort(String storagePortId)
			throws SOSFailure {

		return this.fetchStoragePort(null, storagePortId);

	}

	public synchronized Hashtable<String, List<String>> getStoragePortToVolumeTable(
			String csvSeparatedInitiatorList) throws SOSFailure {

		return this.fetchPortToVolumeTable(csvSeparatedInitiatorList);

	}

	public synchronized List<String> getVolumeIdList(
			String csvSeparatedInitiatorList) throws SOSFailure {

		final String methodName = "getVolumeIdList(): ";
		log.trace(methodName + "Entry with volumeIds:"
				+ csvSeparatedInitiatorList);

		List<String> volumeIdList = this
				.fetchVolumeIdsByHostInitiators(csvSeparatedInitiatorList);

		log.trace(methodName + "Exit returning volume list of size["
				+ volumeIdList.size() + "]");

		return volumeIdList;

	}

	public synchronized List<String> getFileSystemIdList(List<String> mountPaths)
			throws SOSFailure {

		final String methodName = "getFileSystemIdList(): ";
		log.trace(methodName + "Entry with mount paths:" + mountPaths);

		List<String> filesystemIdList = new ArrayList<String>();

		if (mountPaths != null) {
			for (String mountPath : mountPaths) {
				if (mountPath != null && mountPath.trim().length() > 0) {
					String filesystemId = this
							.fetchFileSystemIdByMountPath(mountPath.trim());
					if (filesystemId != null && filesystemId.length() > 0) {
						filesystemIdList.add(filesystemId);
					}
				}
			}
		}

		log.trace(methodName + "Exit returning filesystem Id list of size["
				+ filesystemIdList.size() + "]");

		return filesystemIdList;

	}

	/**
	 * Returns list of all active file shares with their details based on given
	 * volume Ids
	 * 
	 * @param list
	 *            of volumeIds
	 * @return List of <code>Volume</code> objects
	 * @throws SOSFailure
	 *             if call(s) to Bourne fails
	 */
	public synchronized List<Volume> getVolumeDetailList(List<String> volumeIds)
			throws SOSFailure {

		final String methodName = "getVolumeDetailList(): ";
		log.trace(methodName + "Entry with volumeIds:" + volumeIds);

		List<Volume> volumeList = this.fetchVolumeDetailsById(volumeIds);

		log.trace(methodName + "Exit returning volume list of size["
				+ volumeList.size() + "]");

		return volumeList;

	}

	/**
	 * Returns list of all active CoSes with their details based on given CoS
	 * Ids
	 * 
	 * @param list
	 *            of cosIds
	 * @return List of <code>CoS</code> objects
	 * @throws SOSFailure
	 *             if call(s) to Bourne fails
	 */
	public List<CoS> getCosDetailList(List<String> cosIds) throws SOSFailure {

		final String methodName = "getCosDetailList(): ";
		log.trace(methodName + "Entry with cos Ids:" + cosIds);

		List<CoS> returnList = new ArrayList<CoS>();
		List<CoS> cosList = getCosDetailList();

		Comparator<CoS> c = new Comparator<CoS>() {

			@Override
			public int compare(CoS cos1, CoS cos2) {
				return cos1.getId().compareTo(cos2.getId());

			}
		};

		int index = -1;
		Collections.sort(cosList, c);
		for (String givenCosId : cosIds) {

			CoS cos = new CoS(givenCosId);
			index = Collections.binarySearch(cosList, cos, c);
			if (index >= 0) {
				log.trace(methodName + "givenCosId[" + givenCosId
						+ "] is found at cosList[" + index + "]");
				returnList.add(cosList.get(index));
			}
		}

		log.trace(methodName + "Exit returning cos list of size["
				+ returnList.size() + "]");

		return returnList;

	}

	/**
	 * Returns Storage system with its details based on given storage system Id
	 * 
	 * @param storageSystemId
	 * @return Object of <code>StorageSystem</code>
	 * @throws SOSFailure
	 *             if call(s) to Bourne fails
	 */
	public synchronized StorageSystem getStorageSystem(String storageSystemId)
			throws SOSFailure {

		final String methodName = "getStorageSystem(): ";

		log.trace(methodName + "Entry with storageSystemId[" + storageSystemId
				+ "]");

		final String STORAGE_SYSTEM_DETAIL = "/vdc/storage-systems/%s";

		StorageSystem system = null;

		try {
			system = _client.queryObject(
					String.format(STORAGE_SYSTEM_DETAIL, storageSystemId),
					StorageSystem.class);

		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}

		log.trace(methodName + "Exit returning: " + system);

		return system;

	}

	/**
	 * Returns Bourne event list based on given timestamp
	 * 
	 * @param timestamp
	 *            object of <code>java.util.Date</code>
	 * @return Object of <code>EventList</code>
	 * @throws SOSFailure
	 *             if call(s) to Bourne fails
	 */
	public synchronized EventList getEvents(Calendar timestamp)
			throws SOSFailure {

		final String methodName = "getEvents(): ";

		final String TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH";

		final String givenTimestamp = new SimpleDateFormat(TIMESTAMP_PATTERN)
				.format(timestamp.getTime());

		log.trace(methodName + "Entry with timestamp[" + givenTimestamp + "]");

		EventList eventListObj = null;
		// try {
		/*
		 * eventListObj = _client .queryObject(String.format(MONITOR_URI,
		 * givenTimestamp), EventList.class);
		 */

		eventListObj = this.getEvents(givenTimestamp);

		/*
		 * } catch (UniformInterfaceException e) { log.error(methodName +
		 * "UniformInterfaceException occured", e); throw new SOSFailure(e); }
		 */

		log.trace(methodName + "Exit returing eventListObj[" + eventListObj
				+ "]");

		return eventListObj;

	}

	/**
	 * Returns Bourne event list based on given timestamp
	 * 
	 * @param timestamp
	 *            of the format <code>YYYY-MM-DDYHH:mm</code>
	 * @return Object of <code>EventList</code>
	 * @throws SOSFailure
	 *             if call(s) to Bourne fails
	 */
	public synchronized EventList getEvents(String timestamp) throws SOSFailure {

		final String methodName = "getEvents(): ";

		final String MONITOR_URI = "/monitoring/events.xml/?time_bucket=%s";

		log.trace(methodName + "Entry with timestamp[" + timestamp + "]");

		EventList eventListObj = null;
		try {
			eventListObj = _client.queryObject(
					String.format(MONITOR_URI, timestamp), EventList.class);

		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {

			if (e.toString().contains("403 Forbidden")) {
				log.warn(methodName + " The call to REST API: "
						+ String.format(MONITOR_URI, timestamp)
						+ " returned response of \"403 Forbidden\"");
				return null;
			}

			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}

		log.trace(methodName + "Exit returing eventListObj[" + eventListObj
				+ "]");

		return eventListObj;

	}

	/**
	 * Returns Bourne stats list based on given timestamp
	 * 
	 * @param timestamp
	 *            of the format <code>YYYY-MM-DDTHH:mm</code>
	 * @return Object of <code>EventList</code>
	 * @throws SOSFailure
	 *             if call(s) to Bourne fails
	 */
	public synchronized StatList getStatistics(String timestamp)
			throws SOSFailure {

		final String methodName = "getStatistics(): ";

		final String METER_URI = "/metering/stats.xml/?time_bucket=%s";

		log.trace(methodName + "Entry with timestamp[" + timestamp + "]");

		StatList statListObj = null;
		try {

			statListObj = _client.queryObject(
					String.format(METER_URI, timestamp), StatList.class);

		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}

		log.trace(methodName + "Exit returing eventListObj[" + statListObj
				+ "]");

		return statListObj;

	}

	public synchronized StatList getStatistics(Date date) throws SOSFailure {

		final String methodName = "getStatistics(): ";

		final String TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH";

		String timestamp = new SimpleDateFormat(TIMESTAMP_PATTERN).format(date);

		log.trace(methodName + "Entry with timestamp[" + timestamp + "]");

		StatList statListObj = null;

		statListObj = this.getStatistics(timestamp);

		log.trace(methodName + "Exit returing statListObj[" + statListObj + "]");

		return statListObj;

	}

	public synchronized String getArrayId() throws SOSFailure {

		String arrayId = "StorageArray";

		if (this._providerTenantUri == null) {
			this._providerTenantUri = this.getProviderTenantId();
		}
		arrayId = this._providerTenantUri.replace("TenantOrg", arrayId);

		return arrayId;
	}

	public synchronized String getProcessorId() throws SOSFailure {

		String arrayId = "StorageProcessor";

		if (this._providerTenantUri == null) {
			this._providerTenantUri = this.getProviderTenantId();
		}
		arrayId = this._providerTenantUri.replace("TenantOrg", arrayId);

		return arrayId;
	}

	public synchronized AssociatedPool fetchAssociatedPoolOfVolume(
			String volumeID) throws SOSFailure {

		final String methodName = "fetchAssociatedPoolForVolume(): ";

		log.trace(methodName + "Entry with volumeID[" + volumeID + "]");

		final String ASSOCIATED_POOL_OF_VOLUME_URI = "/block/volumes/%s/storage-pool";

		AssociatedPool associatedPool = null;

		try {
			associatedPool = _client.queryObject(
					String.format(ASSOCIATED_POOL_OF_VOLUME_URI, volumeID),
					Volume.AssociatedPool.class);

			associatedPool = associatedPool.getStoragepool() == null ? null
					: associatedPool;
			

		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}

		log.trace(methodName + "Exit returning associated storage pool["
				+ associatedPool + "]");

		return associatedPool;

	}

	public synchronized StoragePool fetchStoragePool(String storageSystemId,
			String storagePoolId) throws SOSFailure {

		final String methodName = "fetchStoragePool(): ";

		final String STORAGE_POOL_DETAIL_URI = "/vdc/storage-systems/%s/storage-pools/%s";

		log.trace(methodName + "Entry with storageSystemId[" + storageSystemId
				+ "] storagePoolId[" + storagePoolId + "]");

		StoragePool storagePool = null;

		try {
			storagePool = _client.queryObject(String.format(
					STORAGE_POOL_DETAIL_URI, storageSystemId, storagePoolId),
					StoragePool.class);

		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}

		log.trace(methodName + "Exit returning storagePool[ " + storagePool
				+ "]");

		return storagePool;

	}

	public synchronized StoragePool fetchStoragePoolByHref(String href)
			throws SOSFailure {

		final String methodName = "fetchStoragePoolByHref(): ";

		log.trace(methodName + "Entry with href[" + href + "]");

		StoragePool storagePool = null;

		try {
			storagePool = _client.queryObject(href, StoragePool.class);

		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}

		log.trace(methodName + "Exit returning storagePool[ " + storagePool
				+ "]");

		return storagePool;

	}

	private synchronized FileSystemExports fetchFileSystemExports(
			String fileSystemID) throws SOSFailure {

		final String methodName = "fetchFileSystemExports(): ";

		log.trace(methodName + "Entry with fileSystemID[" + fileSystemID + "]");

		final String FS_EXPORT_DETAIL_URI = "/file/filesystems/%s/exports";

		FileSystemExports exports = null;

		try {
			exports = _client.queryObject(
					String.format(FS_EXPORT_DETAIL_URI, fileSystemID),
					FileSystemExports.class);

		} catch (NoSuchAlgorithmException e) {
			log.error(methodName + "NoSuchAlgorithmException occured", e);
			throw new SOSFailure(e);
		} catch (UniformInterfaceException e) {
			log.error(methodName + "UniformInterfaceException occured", e);
			throw new SOSFailure(e);
		}

		log.trace(methodName + "Exit returning: " + exports);

		return exports;

	}

}

