/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.VirtualNAS.VirtualNasState;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.MetricsKeys;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * StorageScheduler service for block and file storage. StorageScheduler is done
 * based on desired class-of-service parameters for the provisioned storage.
 */
public class FileStorageScheduler {

	public final Logger _log = LoggerFactory
			.getLogger(FileStorageScheduler.class);

	private DbClient _dbClient;
	private StorageScheduler _scheduler;

	public void setDbClient(DbClient dbClient) {
		_dbClient = dbClient;
	}

	public void setScheduleUtils(StorageScheduler scheduleUtils) {
		_scheduler = scheduleUtils;
	}

	/**
	 * Schedule storage for fileshare in the varray with the given CoS
	 * capabilities.
	 * 
	 * @param vArray
	 * @param vPool
	 * @param capabilities
	 * @return
	 */
	public List<FileRecommendation> placeFileShare(VirtualArray vArray,
			VirtualPool vPool, VirtualPoolCapabilityValuesWrapper capabilities,
			Project project) {

		_log.debug("Schedule storage for {} resource(s) of size {}.",
				capabilities.getResourceCount(), capabilities.getSize());

		// Get all storage pools that match the passed vpool params and
		// protocols. In addition, the pool must have enough capacity
		// to hold at least one resource of the requested size.
		List<StoragePool> candidatePools = _scheduler.getMatchingPools(vArray,
				vPool, capabilities);

		// Get the recommendations for the candidate pools.
		List<Recommendation> poolRecommendations = _scheduler
				.getRecommendationsForPools(vArray.getId().toString(),
						candidatePools, capabilities);

		List<FileRecommendation> recommendations = getRecommendedStoragePortsForVNAS(
				vPool, vArray.getId(), poolRecommendations, project);
		// We need to place all the resources. If we can't then
		// log an error and clear the list of recommendations.
		if (recommendations.isEmpty()) {
			_log.error(
					"Could not find matching pools for virtual array {} & vpool {}",
					vArray.getId(), vPool.getId());
		}

		return recommendations;
	}

	/**
	 * Select storage port for exporting file share to the given client. One IP
	 * transport zone per varray. Selects only one storage port for all exports
	 * of a file share
	 * 
	 * @param fs
	 *            file share being exported
	 * @param protocol
	 *            file storage protocol for this export
	 * @param clients
	 *            client network address or name
	 * @return
	 */
	public StoragePort placeFileShareExport(FileShare fs, String protocol,
			List<String> clients) {
		StoragePort sp;

		if (fs.getStoragePort() == null) {
			_log.debug("placement for file system {} with no assigned port.",
					fs.getName());
			// if no storage port is selected yet, select one and record the
			// selection
			List<StoragePort> ports = getStorageSystemPortsInVarray(
					fs.getStorageDevice(), fs.getVirtualArray());

			// Filter ports based on protocol (for example, if CIFS or NFS is
			// required)
			if ((null != protocol) && (!protocol.isEmpty())) {
				getPortsWithFileSharingProtocol(protocol, ports);
			}

			if (ports == null || ports.isEmpty()) {
				_log.error(MessageFormat
						.format("There are no active and registered storage ports assigned to virtual array {0}",
								fs.getVirtualArray()));
				throw APIException.badRequests.noStoragePortFoundForVArray(fs
						.getVirtualArray().toString());
			}
			Collections.shuffle(ports);
			sp = ports.get(0);

			// update storage port selections for file share exports
			fs.setStoragePort(sp.getId());
			fs.setPortName(sp.getPortName());
			_dbClient.persistObject(fs);
		} else {
			// if a storage port is already selected for the fileshare, use that
			// port for all exports
			sp = _dbClient.queryObject(StoragePort.class, fs.getStoragePort());
			_log.debug("placement for file system {} with port {}.",
					fs.getName(), sp.getPortName());

			// verify port supports new request.
			if ((null != protocol) && (!protocol.isEmpty())) {
				List<StoragePort> ports = new ArrayList<StoragePort>();
				ports.add(sp);
				getPortsWithFileSharingProtocol(protocol, ports);

				if (ports.isEmpty()) {
					_log.error(MessageFormat
							.format("There are no active and registered storage ports assigned to virtual array {0}",
									fs.getVirtualArray()));
					throw APIException.badRequests
							.noStoragePortFoundForVArray(fs.getVirtualArray()
									.toString());
				}
			}
		}

		return sp;
	}

	private List<FileRecommendation> getRecommendedStoragePortsForVNAS(
			VirtualPool vPool, URI vArrayURI,
			List<Recommendation> poolRecommendations, Project project) {

		List<FileRecommendation> result = new ArrayList<FileRecommendation>();

		_log.debug(
				"Get matching recommendations based on assigned VNAS in project {}",
				project);

		List<VirtualNAS> vNASList = getVNASServersInProject(project, vArrayURI,
				vPool);

		if (vNASList == null || vNASList.isEmpty()) {
			_log.debug(
					"Get matching recommendations based on free VNAS in varray {}",
					vArrayURI);
			vNASList = getUnassignedVNASServers(vArrayURI, vPool);
		}

		VirtualNAS vNAS = null;
		if (vNASList != null && !vNASList.isEmpty()) {
			
			boolean meteringConfigured = true;
			
			if(meteringConfigured) {
				sortVNASListOnDyanamicLoad(vNASList);
			} else {
				sortVNASListOnStaticLoad(vNASList);
			}
			
			vNAS = getTheLeastUsedVNASServer(vNASList, poolRecommendations);
		}
		
		for (Recommendation recommendation : poolRecommendations) {
			FileRecommendation rec = new FileRecommendation(recommendation);

			if (vNAS != null
					&& rec.getSourceDevice().equals(vNAS.getStorageDeviceURI())) {

				_log.debug("Getting the storage ports of VNAS server: {}",
						vNAS.getId());

				List<StoragePort> storagePortList = getAssociatedStoragePorts(vNAS);

				if (storagePortList != null && !storagePortList.isEmpty()) {

					List<URI> spURIList = new ArrayList<URI>();

					for (StoragePort sp : storagePortList) {
						spURIList.add(sp.getId());
					}
					rec.setStoragePorts(spURIList);
					result.add(rec);
				}
			}
		}

		if (result.isEmpty()) {
			_log.debug("No recommendations found for selecting vNAS. Calling selectStorageHADomainMatchingVpool");
			result = selectStorageHADomainMatchingVpool(vPool, vArrayURI,
					poolRecommendations);
		}
		return result;
	}

	private void sortVNASListOnDyanamicLoad(List<VirtualNAS> vNASList) {
		
		Collections.sort(vNASList, new Comparator<VirtualNAS>() {

			@Override
			public int compare(VirtualNAS v1, VirtualNAS v2) {
				
				int value = 0;
				Long storageCapacityOfV1 = MetricsKeys.getLong(MetricsKeys.storageCapacity, v1.getMetrics());
				Long storageCapacityOfV2 = MetricsKeys.getLong(MetricsKeys.storageCapacity, v2.getMetrics());
				value = storageCapacityOfV1.compareTo(storageCapacityOfV2);
				
				if(value == 0) {
					Long storageObjectsOfV1 = MetricsKeys.getLong(MetricsKeys.storageObjects, v1.getMetrics());
					Long storageObjectsOfV2 = MetricsKeys.getLong(MetricsKeys.storageObjects, v2.getMetrics());
					value = storageObjectsOfV1.compareTo(storageObjectsOfV2);
				}
				
				if(value == 0) {
					Double avgUsedPercentV1 = MetricsKeys.getDoubleOrNull(MetricsKeys.avgPercentBusy, v1.getMetrics());
					Double avgUsedPercentV2 = MetricsKeys.getDoubleOrNull(MetricsKeys.avgPercentBusy, v2.getMetrics());
					if(avgUsedPercentV1 != null && avgUsedPercentV2 != null) {
						value = avgUsedPercentV1.compareTo(avgUsedPercentV2);
					} else {
						value = 0;
					}
				}
				return value;
			}
		});
		
	}

	private void sortVNASListOnStaticLoad(List<VirtualNAS> vNASList) {
		
		Collections.sort(vNASList, new Comparator<VirtualNAS>() {

			@Override
			public int compare(VirtualNAS v1, VirtualNAS v2) {
				Long storageCapacityOfV1 = MetricsKeys.getLong(MetricsKeys.storageCapacity, v1.getMetrics());
				Long storageCapacityOfV2 = MetricsKeys.getLong(MetricsKeys.storageCapacity, v2.getMetrics());
				
				int value = storageCapacityOfV1.compareTo(storageCapacityOfV2);
				
				if(value != 0) {
					return value;
				} else {
					Long storageObjectsOfV1 = MetricsKeys.getLong(MetricsKeys.storageObjects, v1.getMetrics());
					Long storageObjectsOfV2 = MetricsKeys.getLong(MetricsKeys.storageObjects, v2.getMetrics());
					return storageObjectsOfV1.compareTo(storageObjectsOfV2);
				}
				
			}
		});
		
	}

	private List<VirtualNAS> getUnassignedVNASServers(URI vArrayURI,
			VirtualPool vpool) {

		List<VirtualNAS> vNASList = new ArrayList<VirtualNAS>();

		_log.debug("Get VNAS servers in the vArray {}", vArrayURI);

		List<URI> vNASURIList = _dbClient
				.queryByConstraint(ContainmentConstraint.Factory
						.getVirtualNASInVirtualArrayConstraint(vArrayURI));

		vNASList = _dbClient.queryObject(VirtualNAS.class, vNASURIList);
		if (vNASList != null && !vNASList.isEmpty()) {
			for (Iterator<VirtualNAS> iterator = vNASList.iterator(); iterator
					.hasNext();) {
				VirtualNAS vNAS = iterator.next();
				// Remove vNAS assigned to projects
				if (vNAS.getProject() != null) {
					_log.debug("Removing vNAS {} as it is assigned to project",
							vNAS.getId());
					iterator.remove();
				} else if (!isVNASActive(vNAS)) {
					_log.debug("Removing vNAS {} as it is inactive",
							vNAS.getId());
					iterator.remove();
				} else if (MetricsKeys.getBoolean(MetricsKeys.overLoaded,
						vNAS.getMetrics())) {
					_log.debug("Removing vNAS {} as it is overloaded",
							vNAS.getId());
					iterator.remove();
				} else if (!vNAS.getProtocols().containsAll(
						vpool.getProtocols())) {
					_log.debug("Removing vNAS {} as it does not support vpool protocols: {}",
							vNAS.getId(), vpool.getProtocols());
					iterator.remove();
				}
			}
		}

		return vNASList;
	}

	private List<StoragePort> getAssociatedStoragePorts(VirtualNAS vNAS) {

		StringSet spIdSet = vNAS.getStoragePorts();

		List<URI> spURIList = new ArrayList<URI>();
		if (spIdSet != null && !spIdSet.isEmpty()) {
			for (String id : spIdSet) {
				spURIList.add(URI.create(id));
			}
		}

		List<StoragePort> spList = _dbClient.queryObject(StoragePort.class,
				spURIList);

		if (spIdSet != null && !spList.isEmpty()) {
			for (Iterator<StoragePort> iterator = spList.iterator(); iterator
					.hasNext();) {
				StoragePort storagePort = iterator.next();

				if (storagePort.getInactive()
						|| storagePort.getTaggedVirtualArrays() == null
						|| !RegistrationStatus.REGISTERED.toString()
								.equalsIgnoreCase(
										storagePort.getRegistrationStatus())
						|| (StoragePort.OperationalStatus.valueOf(storagePort
								.getOperationalStatus()))
								.equals(StoragePort.OperationalStatus.NOT_OK)
						|| !DiscoveredDataObject.CompatibilityStatus.COMPATIBLE
								.name().equals(
										storagePort.getCompatibilityStatus())
						|| !DiscoveryStatus.VISIBLE.name().equals(
								storagePort.getDiscoveryStatus())) {

					iterator.remove();
				}

			}
		}
		
		if (spList != null && !spList.isEmpty()) {
			Collections.sort(spList, new Comparator<StoragePort>() {

				@Override
				public int compare(StoragePort sp1, StoragePort sp2) {
					
					if (sp1.getMetrics() != null && sp2.getMetrics() != null) {

						Double sp1UsedPercent = MetricsKeys.getDoubleOrNull(MetricsKeys.avgPortPercentBusy, sp1.getMetrics());
						Double sp2UsedPercent = MetricsKeys.getDoubleOrNull(MetricsKeys.avgPortPercentBusy, sp2.getMetrics());

						if (sp1UsedPercent != null && sp2UsedPercent != null) {
							return sp1UsedPercent.compareTo(sp2UsedPercent);
						}
					}
					return 0;
				}
			});
		}
		return spList;

	}

	private VirtualNAS getTheLeastUsedVNASServer(List<VirtualNAS> vNASList,
			List<Recommendation> poolRecommendations) {

		List<URI> storageSystemURIList = new ArrayList<URI>();
		for (Iterator<Recommendation> iterator = poolRecommendations.iterator(); iterator
				.hasNext();) {
			Recommendation recommendation = iterator.next();
			storageSystemURIList.add(recommendation.getSourceDevice());
		}

		for (Iterator<VirtualNAS> iterator = vNASList.iterator(); iterator
				.hasNext();) {
			VirtualNAS vNAS = iterator.next();
			if (storageSystemURIList.contains(vNAS.getStorageDeviceURI())) {
				return vNAS;
			}

		}

		return null;
	}

	private List<VirtualNAS> getVNASServersInProject(Project project,
			URI varrayUri, VirtualPool vpool) {

		List<VirtualNAS> vNASList = null;

		_log.debug("Get VNAS servers assigned to project {}", project);

		StringSet vNASServerIdSet = project.getAssignedVNasServers();

		if (vNASServerIdSet != null && !vNASServerIdSet.isEmpty()) {

			_log.debug("Number of vNAS servers assigned to this project: {}",
					vNASServerIdSet.size());

			List<URI> vNASURIList = new ArrayList<URI>();
			for (String vNASId : vNASServerIdSet) {
				vNASURIList.add(URI.create(vNASId));
			}

			vNASList = _dbClient.queryObject(VirtualNAS.class, vNASURIList);

			for (Iterator<VirtualNAS> iterator = vNASList.iterator(); iterator
					.hasNext();) {
				VirtualNAS virtualNAS = iterator.next();

				// Remove inactive, incompatible, invisible vNAS

				if (!isVNASActive(virtualNAS)) {
					_log.debug("Removing vNAS {} as it is inactive", virtualNAS.getId());
					iterator.remove();
				} else if (!virtualNAS.getAssignedVirtualArrays().contains(
						varrayUri)) {
					_log.debug("Removing vNAS {} as it is not part of varray: {}",
							virtualNAS.getId(), varrayUri);
					iterator.remove();
				} else if (MetricsKeys.getBoolean(MetricsKeys.overLoaded,
						virtualNAS.getMetrics())) {
					_log.debug("Removing vNAS {} as it is overloaded",
							virtualNAS.getId());
					iterator.remove();
				} else if (!virtualNAS.getProtocols().containsAll(
						vpool.getProtocols())) {
					_log.debug("Removing vNAS {} as it does not support vpool protocols: {}",
							virtualNAS.getId(), vpool.getProtocols());
					iterator.remove();
				}
			}

		}

		return vNASList;

	}

	private boolean isVNASActive(VirtualNAS virtualNAS) {

		if (virtualNAS.getInactive()
				|| virtualNAS.getAssignedVirtualArrays() == null
				|| virtualNAS.getAssignedVirtualArrays().isEmpty()
				|| !RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
						virtualNAS.getRegistrationStatus())
				|| !DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()
						.equals(virtualNAS.getCompatibilityStatus())
				|| !VirtualNasState.LOADED.getNasState().equals(virtualNAS.getVNasState())
				|| !DiscoveryStatus.VISIBLE.name().equals(
						virtualNAS.getDiscoveryStatus())) {
			return false;
		}
		return true;
	}

	/**
	 * Fetches and returns all the storage ports for a given storage system that
	 * are in a given varray
	 * 
	 * @param storageSystemUri
	 *            the storage system URI
	 * @param varray
	 *            the varray URI
	 * @return a list of all the storage ports for a given storage system
	 */
	private List<StoragePort> getStorageSystemPortsInVarray(
			URI storageSystemUri, URI varray) {
		List<URI> allPorts = _dbClient
				.queryByConstraint(ContainmentConstraint.Factory
						.getStorageDeviceStoragePortConstraint(storageSystemUri));
		List<StoragePort> ports = _dbClient.queryObject(StoragePort.class,
				allPorts);
		Iterator<StoragePort> itr = ports.iterator();
		StoragePort temp = null;
		while (itr.hasNext()) {
			temp = itr.next();
			if (temp.getInactive()
					|| temp.getTaggedVirtualArrays() == null
					|| !temp.getTaggedVirtualArrays().contains(
							varray.toString())
					|| !RegistrationStatus.REGISTERED.toString()
							.equalsIgnoreCase(temp.getRegistrationStatus())
					|| (StoragePort.OperationalStatus.valueOf(temp
							.getOperationalStatus()))
							.equals(StoragePort.OperationalStatus.NOT_OK)
					|| !DiscoveredDataObject.CompatibilityStatus.COMPATIBLE
							.name().equals(temp.getCompatibilityStatus())
					|| !DiscoveryStatus.VISIBLE.name().equals(
							temp.getDiscoveryStatus())) {
				itr.remove();
			}
		}
		return ports;
	}

	/**
	 * Removes storage ports that do not support the specified file sharing
	 * protocol.
	 * 
	 * @param protocol
	 *            the required protocol that the port must support.
	 * @param ports
	 *            the list of available ports.
	 */
	private void getPortsWithFileSharingProtocol(String protocol,
			List<StoragePort> ports) {

		if (null == protocol || null == ports || ports.isEmpty()) {
			return;
		}

		_log.debug("Validate protocol: {}", protocol);
		if (!StorageProtocol.File.NFS.name().equalsIgnoreCase(protocol)
				&& !StorageProtocol.File.CIFS.name().equalsIgnoreCase(protocol)) {
			_log.warn("Not a valid file sharing protocol: {}", protocol);
			return;
		}

		StoragePort tempPort = null;
		StorageHADomain haDomain = null;
		Iterator<StoragePort> itr = ports.iterator();
		while (itr.hasNext()) {
			tempPort = itr.next();

			haDomain = null;
			URI domainUri = tempPort.getStorageHADomain();
			if (null != domainUri) {
				haDomain = _dbClient.queryObject(StorageHADomain.class,
						domainUri);
			}

			if (null != haDomain) {
				StringSet supportedProtocols = haDomain
						.getFileSharingProtocols();
				if (supportedProtocols == null
						|| !supportedProtocols.contains(protocol)) {
					itr.remove();
					_log.debug("Removing port {}", tempPort.getPortName());
				}
			}

			_log.debug("Number ports remainng: {}", ports.size());
		}
	}

	/**
	 * Select the right StorageHADomain matching vpool protocols.
	 * 
	 * @param vpool
	 * @param vArray
	 * @param poolRecommends
	 *            recommendations after selecting matching storage pools.
	 * @return list of FileRecommendation
	 */
	private List<FileRecommendation> selectStorageHADomainMatchingVpool(
			VirtualPool vpool, URI vArray, List<Recommendation> poolRecommends) {

		_log.debug("select matching StorageHADomain");
		List<FileRecommendation> result = new ArrayList<FileRecommendation>();
		for (Recommendation recommendation : poolRecommends) {
			FileRecommendation rec = new FileRecommendation(recommendation);
			URI storageUri = recommendation.getSourceDevice();

			StorageSystem storage = _dbClient.queryObject(StorageSystem.class,
					storageUri);
			// Same check for VNXe will be done here.
			// TODO: normalize behavior across file arrays so that this check is
			// not required.
			// TODO: Implement fake storageHADomain for DD to fit the viPR model
			if (!storage.getSystemType().equals(Type.netapp.toString())
					&& !storage.getSystemType().equals(Type.netappc.toString())
					&& !storage.getSystemType().equals(Type.vnxe.toString())
					&& !storage.getSystemType().equals(
							Type.datadomain.toString())) {
				result.add(rec);
				continue;
			}

			List<StoragePort> portList = getStorageSystemPortsInVarray(
					storageUri, vArray);
			if (portList == null || portList.isEmpty()) {
				_log.info("No valid storage port found from the virtual array: "
						+ vArray);
				continue;
			}

			List<URI> storagePorts = new ArrayList<URI>();
			for (StoragePort port : portList) {

				_log.debug("Looking for port {}", port.getLabel());
				URI haDomainUri = port.getStorageHADomain();
				// Data Domain does not have a filer entity.
				if ((haDomainUri == null)
						&& (!storage.getSystemType().equals(
								Type.datadomain.toString()))) {
					_log.info("No StorageHADomain URI for port {}",
							port.getLabel());
					continue;
				}

				StorageHADomain haDomain = null;
				if (haDomainUri != null) {
					haDomain = _dbClient.queryObject(StorageHADomain.class,
							haDomainUri);
				}
				if (haDomain != null) {
					StringSet protocols = haDomain.getFileSharingProtocols();
					// to see if it matches virtualPool's protocols
					StringSet vpoolProtocols = vpool.getProtocols();
					if (protocols != null
							&& protocols.containsAll(vpoolProtocols)) {
						_log.info(
								"Found the StorageHADomain {} for recommended storagepool: {}",
								haDomain.getName(),
								recommendation.getSourcePool());
						storagePorts.add(port.getId());
					}
				} else if (storage.getSystemType().equals(
						Type.datadomain.toString())) {
					// The same file system on DD can support NFS and CIFS
					storagePorts.add(port.getId());
				} else {
					_log.error("No StorageHADomain for port {}",
							port.getIpAddress());
				}
			}

			// select storage port randomly from all candidate ports (to
			// minimize collisions).
			Collections.shuffle(storagePorts);
			rec.setStoragePorts(storagePorts);
			result.add(rec);
		}
		return result;

	}

}
