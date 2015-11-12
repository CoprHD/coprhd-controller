/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vplex.api.VPlexVirtualVolumeInfo.WaitOnRebuildResult;
import com.emc.storageos.vplex.api.clientdata.PortInfo;
import com.emc.storageos.vplex.api.clientdata.VolumeInfo;
import com.sun.jersey.api.client.ClientResponse;

/**
 * The VPlex API Client is used to get information from the VPlex and to execute
 * configuration commands on a VPlex. A VPlexApiClient instance represents a
 * connection to a single VPlex management server. Use the {@link VPlexApiFactory} to get a VPlexApiClient instance for a given VPlex
 * management server.
 * 
 * NOTE: The Jersey client releases http connections automatically in two cases:
 * 
 * 1. When the ClientResponse does not have entity.
 * 2. When the ClientResponse does have an entity and it was read from response.
 * 
 * In case when the ClientResponse has an entity and the entity is not read,
 * the Jersey client does not release the connection (see JavaDoc for Jersey's
 * ApacheHttpClientHandler.java), and the connection is not returned back to
 * connection pool to be available for a new request.
 * 
 * Therefore, it is incumbent upon programmer's modifying this class and its
 * associated manager classes to add new requests to ensure that the entity
 * is read from the response or that the connection is closed in a finally
 * block. Currently, the former is the approach taken, as the entity for each
 * response from the VPLEX is read and logged so we know the content of the
 * response from the server.
 */
public class VPlexApiClient {

    // The number of times a get request will be retried
    // and the wait interval between attempts.
    public static final int GET_RETRY_COUNT = 12;
    public static final long GET_SLEEP_TIME_MS = 5000;

    // Logger reference.
    private static Logger s_logger = LoggerFactory.getLogger(VPlexApiClient.class);

    // The base URI of the VPlex Management Station.
    private URI _baseURI;

    // The REST client for executing requests to the VPlex Management Station.
    private RESTClient _client;

    // A reference to the discovery manager.
    private VPlexApiDiscoveryManager _discoveryMgr;

    // A reference to the virtual volume manager.
    private VPlexApiVirtualVolumeManager _virtualVolumeMgr;

    // A reference to the export manager.
    private VPlexApiExportManager _exportMgr;

    // A reference to the migration manager.
    private VPlexApiMigrationManager _migrationMgr;

    // A reference to the consistency group manager manager.
    private VPlexApiConsistencyGroupManager _cgMgr;

    // The ID of the VPLEX session to pass in requests to the
    // VPLEX management server associated with this client. Is
    // set on the first request and updated on every response
    // in case it expires and a new session is created.
    private String _vplexSessionId = null;

    /**
     * Constructor
     * 
     * @param endpoint The URI of the VPlex Management Station.
     * @param client A reference to the REST client for making requests.
     */
    VPlexApiClient(URI endpoint, RESTClient client) {
        _baseURI = endpoint;
        _client = client;
        _discoveryMgr = new VPlexApiDiscoveryManager(this);
        _virtualVolumeMgr = new VPlexApiVirtualVolumeManager(this);
        _exportMgr = new VPlexApiExportManager(this);
        _migrationMgr = new VPlexApiMigrationManager(this);
        _cgMgr = new VPlexApiConsistencyGroupManager(this);
    }

    /**
     * Verifies the client can connect to the VPLEX to identify issues
     * in the client connectivity information such as IP address, port,
     * username, and password.
     */
    public void verifyConnectivity() {

        // Attempt to make a valid request to the VPLEX management server.
        URI requestURI = _baseURI.resolve(VPlexApiConstants.URI_CLUSTERS);
        ClientResponse response = null;
        try {
            response = get(requestURI);
            String responseStr = response.getEntity(String.class);
            s_logger.info("Verify connectivity response is {}", responseStr);
            if (responseStr == null || responseStr.equals("")) {
                s_logger.error("Response from VPLEX was empty.");
                throw VPlexApiException.exceptions.connectionFailure(_baseURI.toString());
            }
            int responseStatus = response.getStatus();
            if (responseStatus != VPlexApiConstants.SUCCESS_STATUS) {
                s_logger.info("Verify connectivity response status is {}", responseStatus);
                if (responseStatus == VPlexApiConstants.AUTHENTICATION_STATUS) {
                    // Bad user name and/or password.
                    throw VPlexApiException.exceptions.authenticationFailure(_baseURI.toString());
                } else {
                    // Could be a 404 because the IP was not that for a VPLEX.
                    throw VPlexApiException.exceptions.connectionFailure(_baseURI.toString());
                }
            }
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            // Could be a java.net.ConnectException for an invalid IP address
            // or a java.net.SocketTimeoutException for a bad port.
            throw VPlexApiException.exceptions.connectionFailure(_baseURI.toString());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Returns the version of the VPlex management software.
     * 
     * @return The version of the VPlex management software.
     * 
     * @throws VPlexApiException When an error occurs getting the version information.
     */
    public String getManagementSoftwareVersion() throws VPlexApiException {
        s_logger.info("Request for management software version for VPlex at {}", _baseURI);
        return _discoveryMgr.getManagementSoftwareVersion();
    }

    /**
     * Determines the serial number of the VPlex management server.
     * 
     * @return The serial number of the VPlex management server.
     * 
     * @throws VPlexApiException When an error occurs querying the VPlex.
     */
    public String getManagementServerSerialNumber() throws VPlexApiException {
        s_logger.info("Request for management server serial number for VPlex at {}", _baseURI);
        return _discoveryMgr.getManagementServerSerialNumber();
    }

    /**
     * Gets information about the VPLEX clusters.
     * 
     * @param shallow true to get just the name and path for each cluster, false
     *            to get additional info about the systems and volumes.
     * @param isStorageVolumeItlsFetch true to get the storage volume ITLs, false otherwise.
     * 
     * @return A list of VPlexClusterInfo instances.
     * 
     * @throws VPlexApiException When an error occurs querying the VPlex.
     */
    public List<VPlexClusterInfo> getClusterInfo(boolean shallow, boolean isStorageVolumeItlsFetch) throws VPlexApiException {
        s_logger.info("Request for cluster info for VPlex at {}", _baseURI);
        return _discoveryMgr.getClusterInfo(shallow, isStorageVolumeItlsFetch);
    }

    /**
     * Gets information about the VPLEX clusters.
     * 
     * @param shallow true to get just the name and path for each cluster, false
     *            to get additional info about the systems and volumes.
     * 
     * @return A list of VPlexClusterInfo instances.
     * 
     * @throws VPlexApiException When an error occurs querying the VPlex.
     */
    public List<VPlexClusterInfo> getClusterInfo(boolean shallow) throws VPlexApiException {
        s_logger.info("Request for cluster info for VPlex at {}", _baseURI);
        return _discoveryMgr.getClusterInfo(shallow, false);
    }

    /**
     * Rediscovers the storage systems attached to the VPlex identified by the
     * passed identifiers for the purpose of discovering new volumes accessible
     * to the VPlex.
     * 
     * @param storageSystemNativeGuids The native guids of the storage systems
     *            to be rediscovered.
     */
    public void rediscoverStorageSystems(List<String> storageSystemNativeGuids)
            throws VPlexApiException {
        s_logger.info("Request to rediscover storage systems on VPlex at {}", _baseURI);
        _discoveryMgr.rediscoverStorageSystems(storageSystemNativeGuids);
    }

    /**
     * Gets the information for the storage systems accessible by the VPlex.
     * 
     * @return A list of VPlexStorageSystemInfo specifying the info for the
     *         storage systems accessible to the VPlex.
     * 
     * @throws VPlexApiException If a VPlex request returns a failed status or
     *             an error occurs processing the response.
     */
    public List<VPlexStorageSystemInfo> getStorageSystemInfo() throws VPlexApiException {
        s_logger.info("Request for storage system info for VPlex at {}", _baseURI);
        return _discoveryMgr.getStorageSystemInfo();
    }

    /**
     * Gets the information for the VPlex Ports.
     * 
     * @param shallow if true, the director info will not be fetched (just the PortInfo data),
     *            otherwise, we will traverse engines and directors assembling complete list
     * @return A list of VPlexPortInfo specifying the info for the VPlex ports.
     * 
     * @throws VPlexApiException If a VPlex request returns a failed status or
     *             an error occurs processing the response.
     */
    public List<VPlexPortInfo> getPortInfo(boolean shallow) throws VPlexApiException {
        s_logger.info("Request for port info for VPlex at {}", _baseURI);

        if (shallow) {
            // gets all with API URI wildcards
            return _discoveryMgr.getPortInfo();
        } else {
            // traverses engines and directors assembling complete list
            return _discoveryMgr.getPortAndDirectorInfo();
        }
    }

    /**
     * Gets the Storage Views in a Vplex System.
     * 
     * @return A list of VPlexStorageViewInfo specifying the info for the VPlex storage views.
     * 
     * @throws VPlexApiException If a VPlex request returns a failed status or
     *             an error occurs processing the response.
     */
    public List<VPlexStorageViewInfo> getStorageViews() throws VPlexApiException {
        s_logger.info("Request for storage view info for VPlex at {}", _baseURI);
        return _discoveryMgr.getStorageViews();
    }

    /**
     * Gets all virtual volumes on the VPLEX storage system.
     * 
     * @param shallow When true does a shallow discovery of the virtual volumes. A
     *            shallow discovery simple gets the name and context path for each virtual
     *            volume. A deep discovery will discover the component structure of the virtual
     *            volumes including the distributed devices, local devices, extents, and storage
     *            volumes used to construct the virtual volume. A deep discovery is far more
     *            expensive as many requests must be made to the VPLEX to discovery the structure
     *            for each volume. A VPLEX with thousands of volumes could requires 10's or 100's
     *            of thousands of requests.
     * 
     * @return A list of VPlexVirtualVolumeInfo for the virtual volumes on the VPLEX.
     * 
     * @throws VPlexApiException When an error occurs getting the volumes.
     */
    public Map<String, VPlexVirtualVolumeInfo> getVirtualVolumes(boolean shallow) throws VPlexApiException {
        s_logger.info("Request for {} discovery of virtual volume info for VPlex at {}",
                (shallow ? "shallow" : "deep"), _baseURI);

        Map<String, VPlexVirtualVolumeInfo> virtualVolumeInfoMap = new HashMap<String, VPlexVirtualVolumeInfo>();
        Map<String, VPlexVirtualVolumeInfo> distributedVirtualVolumesMap = new HashMap<String, VPlexVirtualVolumeInfo>();
        Map<String, Map<String, VPlexVirtualVolumeInfo>> localVirtualVolumesMap = new HashMap<String, Map<String, VPlexVirtualVolumeInfo>>();

        // Get the cluster information.
        List<VPlexClusterInfo> clusterInfoList = _discoveryMgr.getClusterInfoLite();
        for (VPlexClusterInfo clusterInfo : clusterInfoList) {
            String clusterId = clusterInfo.getName();
            // for each cluster get the virtual volume information.
            List<VPlexVirtualVolumeInfo> clusterVirtualVolumeInfoList = 
                    _discoveryMgr.getVirtualVolumesForCluster(clusterId);
            for (VPlexVirtualVolumeInfo virtualVolumeInfo : clusterVirtualVolumeInfoList) {
                virtualVolumeInfo.addCluster(clusterId);
                String virtualVolumeName = virtualVolumeInfo.getName();

                if (!virtualVolumeInfoMap.containsKey(virtualVolumeName)) {
                    // We want the unique list of virtual volumes on all
                    // clusters. Distributed volumes will appear on both
                    // clusters.
                    virtualVolumeInfoMap.put(virtualVolumeName, virtualVolumeInfo);

                    // If we are doing a deep discovery of the virtual volumes
                    // keep a list of the distributed virtual volumes and a list
                    // of the local virtual volumes for each cluster.
                    if (!shallow) {
                        String supportingDeviceName = virtualVolumeInfo.getSupportingDevice();
                        if (VPlexVirtualVolumeInfo.Locality.distributed.name().equals(
                                virtualVolumeInfo.getLocality())) {
                            distributedVirtualVolumesMap.put(supportingDeviceName,
                                    virtualVolumeInfo);
                        } else {
                            Map<String, VPlexVirtualVolumeInfo> clusterLocalVolumesMap = localVirtualVolumesMap
                                    .get(clusterId);
                            if (clusterLocalVolumesMap == null) {
                                clusterLocalVolumesMap = new HashMap<String, VPlexVirtualVolumeInfo>();
                                localVirtualVolumesMap.put(clusterId, clusterLocalVolumesMap);
                            }
                            clusterLocalVolumesMap.put(supportingDeviceName, virtualVolumeInfo);
                        }
                    }
                } else if (VPlexVirtualVolumeInfo.Locality.distributed.name().equals(
                        virtualVolumeInfo.getLocality())) {
                    // on a distributed volume, we need to be sure to add the second
                    // cluster id as well... this is needed by ingestion. see CTRL-10982
                    virtualVolumeInfoMap.get(virtualVolumeName).addCluster(clusterId);
                }
            }
        }
        // Do the deep discovery of the component structure for each
        // virtual volume, if necessary.
        if (!shallow) {
            // Get the component structure for each distributed virtual volume
            // starting with the supporting distributed device.
            _discoveryMgr.setSupportingComponentsForDistributedVirtualVolumes(distributedVirtualVolumesMap);

            // Get the component structure for each local virtual volume
            // starting with the supporting local device.
            for (Map.Entry<String, Map<String, VPlexVirtualVolumeInfo>> mapEntry : localVirtualVolumesMap
                    .entrySet()) {
                _discoveryMgr.setSupportingComponentsForLocalVirtualVolumes(
                        mapEntry.getKey(), mapEntry.getValue());
            }
        }

        return virtualVolumeInfoMap;
    }

    /**
     * Finds the volume with the passed name and discovers it's structure.
     * 
     * @param virtualVolumeName The name of the virtual volume.
     * 
     * @return A VPlexVirtualVolumeInfo containing the volume's structure.
     */
    public VPlexVirtualVolumeInfo getVirtualVolumeStructure(String virtualVolumeName) throws VPlexApiException {
        VPlexVirtualVolumeInfo vvInfo = _discoveryMgr.findVirtualVolume(virtualVolumeName, true);
        VPlexDistributedDeviceInfo ddInfo = _discoveryMgr.findDistributedDevice(vvInfo.getSupportingDevice());
        vvInfo.setSupportingDeviceInfo(ddInfo);
        _discoveryMgr.setSupportingComponentsForDistributedDevice(ddInfo);
        return vvInfo;
    }

    /**
     * Creates a VPlex virtual volume using the passed native volume
     * information. The passed native volume information should identify a
     * single volume on a backend storage array connected to a VPlex cluster
     * when a simple, non-distributed virtual volume is desired. When a
     * distributed virtual volume is desired the native volume info should
     * identify two volumes. One volume should reside on a backend storage array
     * connected to one VPlex cluster in a VPlex metro configuration. The other
     * volumes should reside on a backend storage array connected to the other
     * VPlex cluster in a VPlex Metro configuration.
     * 
     * NOTE: Currently, backend volumes newly exported to the VPlex must be
     * discovered prior to creating a virtual volume using them by invoking the
     * rediscoverStorageSystems API.
     * 
     * @param nativeVolumeInfoList The native volume information.
     * @param isDistributed true for a distributed virtual volume, false
     *            otherwise.
     * @param discoveryRequired true if the passed native volumes are newly
     *            exported and need to be discovered by the VPlex.
     * @param preserveData true if the native volume data should be preserved
     *            during virtual volume creation.
     * @param winningClusterId Used to set detach rules for distributed volumes.
     * @param clusterInfoList A list of VPlexClusterInfo specifying the info for the VPlex
     *            clusters.
     * @param findVirtualVolume If true findVirtualVolume method is called after virtual volume is created.
     * 
     * @return The information for the created virtual volume.
     * 
     * @throws VPlexApiException When an error occurs creating the virtual
     *             volume.
     */
    public VPlexVirtualVolumeInfo createVirtualVolume(
            List<VolumeInfo> nativeVolumeInfoList, boolean isDistributed,
            boolean discoveryRequired, boolean preserveData, String winningClusterId, List<VPlexClusterInfo> clusterInfoList,
            boolean findVirtualVolume)
            throws VPlexApiException {
        s_logger.info("Request for virtual volume creation on VPlex at {}", _baseURI);
        return _virtualVolumeMgr.createVirtualVolume(nativeVolumeInfoList, isDistributed,
                discoveryRequired, preserveData, winningClusterId, clusterInfoList, findVirtualVolume);
    }

    /**
     * Rename a VPlex resource that subclasses VPlexResourceInfo.
     * Adjusts the path and name in the resourceInfo and returns the original object.
     * 
     * @param resourceInfo - VPlexVirtualVolumeInfo, VPlexDistributedDeviceInfo, etc.
     * @param newName String -- the desired new name.
     * @return -- The original VPlexResourceInfo with updated path and name
     * 
     * @throws VPlexApiException When an error occurs renaming resource
     */
    public <T extends VPlexResourceInfo> T renameResource(T resourceInfo, String newName)
            throws VPlexApiException {
        return _virtualVolumeMgr.renameVPlexResource(resourceInfo, newName);
    }

    /**
     * Creates and attaches mirror device to the source device.
     * 
     * @param virtualVolume The virtual volume information to which mirror will be attached
     * @param nativeVolumeInfoList The native volume information.
     * @param discoveryRequired true if the passed native volumes are newly
     *            exported and need to be discovered by the VPlex.
     * @param preserveData true if the native volume data should be preserved
     *            during mirror device creation.
     * 
     * @return The VPlex device info that is attached as a mirror.
     * 
     * @throws VPlexApiException When an error occurs creating and attaching device as a mirror
     */
    public VPlexDeviceInfo createDeviceAndAttachAsMirror(VPlexVirtualVolumeInfo virtualVolume,
            List<VolumeInfo> nativeVolumeInfoList, boolean discoveryRequired, boolean preserveData)
            throws VPlexApiException {
        s_logger.info("Request for mirror creation on VPlex at {}", _baseURI);
        return _virtualVolumeMgr.createDeviceAndAttachAsMirror(virtualVolume, nativeVolumeInfoList, discoveryRequired, preserveData);
    }

    /**
     * Attaches mirror device to the source device
     * 
     * @param locality The constant that tells if it's local or distributed volume
     * @param sourceVirtualVolumeName Name of the virtual volume
     * @param mirrorDeviceName Name of the mirror device
     * 
     * @throws VPlexApiException When an error occurs attaching mirror device to the
     *             source volume device
     */
    public void attachMirror(String locality, String sourceVirtualVolumeName, String mirrorDeviceName)
            throws VPlexApiException {
        _virtualVolumeMgr.attachMirror(locality, sourceVirtualVolumeName, mirrorDeviceName);
    }

    /**
     * Deletes the virtual volume by destroying all the components (i.e,
     * extents, devices) that were created in the process of creating the
     * virtual and unclaiming the storage volume(s).
     * 
     * @param nativeVolumeInfoList The same native volume info that was passed
     *            when the virtual volume was created.
     * 
     * @throws VPlexApiException When an error occurs deleted the virtual
     *             volume.
     */
    public void deleteVirtualVolume(List<VolumeInfo> nativeVolumeInfoList)
            throws VPlexApiException {
        s_logger.info("Request for virtual volume deletion on VPlex at {}", _baseURI);
        _virtualVolumeMgr.deleteVirtualVolume(nativeVolumeInfoList);
    }

    /**
     * Deletes the virtual volume by destroying all the components (i.e,
     * extents, devices) that were created in the process of creating the
     * virtual and unclaiming the storage volume(s).
     * 
     * @param virtualVolumeName The name of the virtual volume to be deleted.
     * @param unclaimVolumes true if the storage volumes should be unclaimed.
     * @param retryOnDismantleFailure When true, retries the delete if the
     *            dismantle of the virtual volume fails, which can occur if the
     *            volume was distributed and had previously been migrated. This is
     *            due to a bug in the VPLEX management software (zeph-q24801)
     * 
     * @throws VPlexApiException When an error occurs deleted the virtual
     *             volume.
     */
    public void deleteVirtualVolume(String virtualVolumeName,
            boolean unclaimVolumes, boolean retryOnDismantleFailure) throws VPlexApiException {
        s_logger.info("Request for virtual volume deletion on VPlex at {}", _baseURI);
        _virtualVolumeMgr.deleteVirtualVolume(virtualVolumeName, unclaimVolumes,
                retryOnDismantleFailure);
    }

    /**
     * Deletes the virtual volume and leaves the underlying structure intact.
     * 
     * @param virtualVolumeName The name of the virtual volume to be destroyed.
     * 
     * @throws VPlexApiException When an error occurs destroying the virtual
     *             volume.
     */
    public void destroyVirtualVolume(String virtualVolumeName) throws VPlexApiException {
        s_logger.info("Request for virtual volume destroy on VPlex at {}", _baseURI);
        _virtualVolumeMgr.destroyVirtualVolume(virtualVolumeName);
    }

    /**
     * Causes the VPLEX to "forget" about the volumes identified by the
     * passed native volume information. Typically called when the calling
     * application has deleted backend volumes and wants the VPLEX to disregard
     * these volumes.
     * 
     * @param nativeVolumeInfoList The native volume information for the
     *            storage volumes to be forgotten.
     */
    public void forgetVolumes(List<VolumeInfo> nativeVolumeInfoList) {
        s_logger.info("Request to forget volumes on VPlex at {}", _baseURI);
        _discoveryMgr.forgetVolumes(nativeVolumeInfoList);
    }

    /**
     * Expands the virtual volume with the passed name to it's full expandable
     * capacity. This API would be invoked after natively expanding the backend
     * volume(s) of the virtual volume to provide additional capacity or say
     * migrating the backend volume(s) to volume(s) with a larger capacity.
     * 
     * @param virtualVolumeName The name of the virtual volume.
     * @param expansionStatusRetryCount Retry count to check virtual volume's expansion status
     * @param expansionStatusSleepTime Sleep time in between expansion status check retries
     * 
     * @throws VPlexApiException When an exception occurs expanding the volume.
     */
    public VPlexVirtualVolumeInfo expandVirtualVolume(String virtualVolumeName,
            int expansionStatusRetryCount, long expansionStatusSleepTime)
            throws VPlexApiException {
        s_logger.info("Request for virtual volume expansion on VPlex at {}", _baseURI);
        return _virtualVolumeMgr.expandVirtualVolume(virtualVolumeName, expansionStatusRetryCount,
                expansionStatusSleepTime);
    }

    /**
     * For the virtual volume with the passed name, migrates the data on the
     * backend volume(s) to the backend volumes identified by the passed native
     * volume information.
     * 
     * @param migrationName The name for this migration.
     * @param virtualVolumeName The name of the virtual volume whose data is to
     *            be migrated.
     * @param nativeVolumeInfoList The native information for the volume(s) to
     *            which the data should be migrated.
     * @param isRemote true if the the migration is across clusters, else false.
     * @param useDeviceMigration true if device migration is required.
     * @param discoveryRequired true if the passed native volumes are newly
     *            exported and need to be discovered by the VPlex.
     * @param startNow true to start the migration now, else migration is
     *            created in a paused state.
     * @param transferSize The transfer size for migration
     * @return A reference to the migration(s) started to migrate the virtual
     *         volume.
     * 
     * @throws VPlexApiException When an error occurs creating and/or
     *             initializing the migration.
     */
    public List<VPlexMigrationInfo> migrateVirtualVolume(String migrationName,
            String virtualVolumeName, List<VolumeInfo> nativeVolumeInfoList,
            boolean isRemote, boolean useDeviceMigration, boolean discoveryRequired,
            boolean startNow, String transferSize) throws VPlexApiException {
        s_logger.info("Request for virtual volume migration on VPlex at {}", _baseURI);
        return _migrationMgr.migrateVirtualVolume(migrationName, virtualVolumeName,
                nativeVolumeInfoList, isRemote, useDeviceMigration, discoveryRequired, startNow, transferSize);
    }

    /**
     * Returns the information, including the current status, of the migration
     * with the passed name.
     * 
     * @param migrationName The name of the migration.
     * 
     * @return A VPlex migration info reference.
     * 
     * @throws VPlexApiException When an error occurs getting the latest
     *             information for the migration or the migration is not found.
     */
    public VPlexMigrationInfo getMigrationInfo(String migrationName) throws VPlexApiException {
        List<VPlexMigrationInfo> migrationInfoList = _discoveryMgr
                .findMigrations(Arrays.asList(migrationName));
        return migrationInfoList.get(0);
    }

    /**
     * Pauses the executing migrations with the passed name.
     * 
     * @param migrationNames The names of the migrations.
     * 
     * @throws VPlexApiException When an error occurs pausing the migrations.
     */
    public void pauseMigrations(List<String> migrationNames) throws VPlexApiException {
        s_logger.info("Request to pause migrations on VPlex at {}", _baseURI);
        _migrationMgr.pauseMigrations(migrationNames);
    }

    /**
     * Resume the paused migrations with with the passed names.
     * 
     * @param migrationNames The names of the migrations.
     * 
     * @throws VPlexApiException When an error occurs resuming the migrations.
     */
    public void resumeMigrations(List<String> migrationNames) throws VPlexApiException {
        s_logger.info("Request to resume migrations on VPlex at {}", _baseURI);
        _migrationMgr.resumeMigrations(migrationNames);
    }

    /**
     * Commits the completed migrations with the passed names and tears down the
     * old devices and unclaims the storage volumes.
     * 
     * @param migrationNames The names of the migrations.
     * @param cleanup true to automatically cleanup after the commit.
     * @param remove true to automatically remove the migration record.
     * @param rename true to rename the volumes after committing the migration.
     * 
     * @return A list of VPlexMigrationInfo instances for the committed
     *         migrations each of which contains a reference to the
     *         VPlexVirtualVolumeInfo associated with that migration which can
     *         be used to update the virtual volume native id, which can change
     *         as a result of the migration.
     * 
     * @throws VPlexApiException When an error occurs committing the migrations.
     */
    public List<VPlexMigrationInfo> commitMigrations(List<String> migrationNames,
            boolean cleanup, boolean remove, boolean rename) throws VPlexApiException {
        s_logger.info("Request to commit migrations on VPlex at {}", _baseURI);
        return _migrationMgr.commitMigrations(migrationNames, cleanup, remove, rename);
    }

    /**
     * Cleans the committed migrations with the passed names tearing down the
     * old devices and unclaiming the storage volumes.
     * 
     * @param migrationNames The names of the migrations.
     * 
     * @throws VPlexApiException When an error occurs cleaning the migrations.
     */
    public void cleanMigrations(List<String> migrationNames) throws VPlexApiException {
        s_logger.info("Request to clean migrations on VPlex at {}", _baseURI);
        _migrationMgr.cleanMigrations(migrationNames);
    }

    /**
     * Cancels the uncommitted, executing, paused, or queued migrations with the
     * passed names and tears down the new devices that were created as targets
     * for the migration and unclaims the storage volumes.
     * 
     * @param migrationNames The names of the migrations.
     * @param cleanup true to automatically cleanup after the cancellation.
     * @param remove true to automatically remove the migration record.
     * 
     * @throws VPlexApiException When an error occurs canceling the migrations.
     */
    public void cancelMigrations(List<String> migrationNames, boolean cleanup,
            boolean remove) throws VPlexApiException {
        s_logger.info("Request to cancel migrations on VPlex at {}", _baseURI);
        _migrationMgr.cancelMigrations(migrationNames, cleanup, remove);
    }

    /**
     * Removes the records for the committed or canceled migrations with the
     * passed names.
     * 
     * @param migrationNames The names of the migrations.
     * 
     * @throws VPlexApiException When an error occurs removing the migration
     *             records.
     */
    public void removeMigrations(List<String> migrationNames) throws VPlexApiException {
        s_logger.info("Request to remove migrations on VPlex at {}", _baseURI);
        _migrationMgr.removeMigrations(migrationNames);
    }

    /**
     * Creates a VPlex storage view so that the passed initiators have access to
     * the passed virtual volumes, via the passed target ports (i.e., the VPlex
     * front-end ports). Note that target ports are required to create a storage
     * view. Initiator ports and virtual volumes are optional and can be added
     * separately.
     * 
     * @param viewName A unique name for the storage view.
     * @param targetPortInfo The info for the target ports.
     * @param initiatorPortInfo The info for the initiator ports.
     * @param virtualVolumeMap Map of virtual volume names to LUN ID.
     * 
     *            NOTE: If you want VPlex to pick the LUN ID pass
     *            VPlexApiConstants.LUN_UNASSIGNED for the virtual volume.
     * 
     * @return A reference to a VPlexStorageViewInfo specifying the storage view
     *         information.
     * 
     * @throws VPlexApiException When an error occurs creating the storage view.
     */
    public VPlexStorageViewInfo createStorageView(String viewName,
            List<PortInfo> targetPortInfo, List<PortInfo> initiatorPortInfo,
            Map<String, Integer> virtualVolumeMap) throws VPlexApiException {

        s_logger.info("Request for storage view creation on VPlex at {}", _baseURI);

        // A storage view name is required. It must be unique across all
        // clusters of the VPlex. We could do a check here, but it would
        // require an additional request, or perhaps 2 in a Metro/Geo
        // configuration.
        if ((viewName == null) || (viewName.trim().length() == 0)) {
            throw new VPlexApiException(
                    "A name for the storage view must be specified.");
        }

        // Targets are required to create a storage view.
        if (targetPortInfo.isEmpty()) {
            throw new VPlexApiException(
                    "Target ports are required to create a storage view");
        }

        return _exportMgr.createStorageView(viewName, targetPortInfo, initiatorPortInfo,
                virtualVolumeMap);
    }

    /**
     * Delete the storage view with the passed name and existence
     * tracking parameter.
     * 
     * @param viewName The name of the storage view to be deleted.
     * @param viewFound An out parameter indicating whether or
     *            not the storage view was actually found on
     *            the VPLEX device during this process.
     * 
     * @throws VPlexApiException When an error occurs deleting the storage view.
     */
    public void deleteStorageView(String viewName, Boolean[] viewFound) throws VPlexApiException {
        s_logger.info("Request for storage view deletion on VPlex at {}", _baseURI);
        _exportMgr.deleteStorageView(viewName, viewFound);
        s_logger.info("Storage view was found for deletion: {}", viewFound[0]);
    }

    /**
     * Adds the initiators identified by the passed port information to the
     * storage view with the passed name.
     * 
     * @param viewName The name of the storage view.
     * @param initiatorPortInfo The port information for the initiators to be
     *            added.
     * 
     * @throws VPlexApiException When an error occurs adding the initiators.
     */
    public void addInitiatorsToStorageView(String viewName,
            List<PortInfo> initiatorPortInfo) throws VPlexApiException {
        s_logger.info("Request to add initiators to storage view on VPlex at {}",
                _baseURI);
        _exportMgr.addInitiatorsToStorageView(viewName, initiatorPortInfo);
    }

    /**
     * Removes the initiators identified by the passed port information from the
     * storage view with the passed name.
     * 
     * @param viewName The name of the storage view.
     * @param initiatorPortInfo The port information for the initiators to be
     *            removed.
     * 
     * @throws VPlexApiException When an error occurs removing the initiators.
     */
    public void removeInitiatorsFromStorageView(String viewName,
            List<PortInfo> initiatorPortInfo) throws VPlexApiException {
        s_logger.info("Request to remove initiators from storage view on VPlex at {}",
                _baseURI);
        _exportMgr.removeInitiatorsFromStorageView(viewName, initiatorPortInfo);
    }

    /**
     * Add targets to the storage view identified by name.
     * 
     * @param viewName -- StorageView name
     * @param targetPortInfo -- The port information for the targets to be added.
     * @throws VPlexApiException When an error occurs adding the targets
     */
    public void addTargetsToStorageView(String viewName,
            List<PortInfo> targetPortInfo) throws VPlexApiException {
        s_logger.info("Request to add targets to storage view on VPlex at {}", _baseURI);
        _exportMgr.addTargetsToStorageView(viewName, targetPortInfo);
    }

    /**
     * Remove targets from the storage view identified by name.
     * 
     * @param viewName -- Storage view name
     * @param targetPortInfo -- The port information for the targets to be removed.
     * @throws VPlexApiException
     */
    public void removeTargetsFromStorageView(String viewName,
            List<PortInfo> targetPortInfo) throws VPlexApiException {
        s_logger.info("Request to remove targets to storage view on VPlex at {}", _baseURI);
        _exportMgr.removeTargetsFromStorageView(viewName, targetPortInfo);
    }

    /**
     * Adds the virtual volumes with the passed names to the storage view with
     * the passed name.
     * 
     * @param virtualVolumeMap Map of virtual volume names to LUN ID.
     * 
     *            NOTE: If you want VPlex to pick the LUN ID pass
     *            VPlexApiConstants.LUN_UNASSIGNED for the virtual volume.
     * 
     * @return A reference to a VPlexStorageViewInfo specifying the storage view
     *         information.
     * 
     * @throws VPlexApiException When an error occurs adding the virtual
     *             volumes.
     */
    public VPlexStorageViewInfo addVirtualVolumesToStorageView(String viewName,
            Map<String, Integer> virtualVolumeMap) throws VPlexApiException {
        s_logger.info("Request to add virtual volumes to storage view on VPlex at {}",
                _baseURI);
        return _exportMgr.addVirtualVolumesToStorageView(viewName, virtualVolumeMap);
    }

    /**
     * Removes the virtual volumes with the passed names from the storage view
     * with the passed name.
     * 
     * @param virtualVolumeNames The names of the virtual volumes to be removed.
     * 
     * @throws VPlexApiException When an error occurs removing the virtual
     *             volumes.
     */
    public void removeVirtualVolumesFromStorageView(String viewName,
            List<String> virtualVolumeNames) throws VPlexApiException {
        s_logger.info(
                "Request to remove virtual volumes from storage view on VPlex at {}",
                _baseURI);
        _exportMgr.removeVirtualVolumesFromStorageView(viewName, virtualVolumeNames);
    }

    /**
     * Gets the name of the cluster on which the passed storage volumes resides.
     * 
     * @param volumeInfo The native volume info for the volumes.
     * 
     * @return The name of the cluster on which the volume resides.
     * 
     * @throws VPlexApiException If an error occurs finding the volume.
     */
    public String getClaimedStorageVolumeClusterName(VolumeInfo volumeInfo)
            throws VPlexApiException {
        VPlexStorageVolumeInfo storageVolumeInfo = _discoveryMgr
                .findStorageVolume(volumeInfo.getVolumeName());
        return storageVolumeInfo.getClusterId();
    }

    /**
     * Gets all consistency groups on the VPLEX.
     * 
     * @return A list of VPlexConsistencyGroupInfo instances corresponding to
     *         the consistency groups on the VPLEX.
     * 
     * @throws VPlexApiException When an error occurs get the consistency
     *             groups.
     */
    public List<VPlexConsistencyGroupInfo> getConsistencyGroups()
            throws VPlexApiException {
        s_logger.info("Request to get all consistency groups on VPlex at {}", _baseURI);

        return _discoveryMgr.getConsistencyGroups();
    }

    /**
     * Creates a consistency group with the passed name on the cluster with the
     * passed name.
     * 
     * @param cgName The name for the consistency group.
     * @param clusterName The name of the cluster on which the group is created.
     * @param isDistributed true if the CG will hold distributed volumes.
     * 
     * @throws VPlexApiException When an error occurs creating the consistency
     *             group.
     */
    public void createConsistencyGroup(String cgName, String clusterName,
            boolean isDistributed) throws VPlexApiException {
        s_logger.info("Request to create consistency group on VPlex at {}", _baseURI);
        _cgMgr.createConsistencyGroup(cgName, clusterName, isDistributed);
    }

    /**
     * Updates the consistency group properties. Should only be invoked
     * when the consistency group has no volumes.
     * 
     * @param cgName The name for the consistency group.
     * @param clusterName The name of the cluster for which the CG should be
     *            found, when not distributed.
     * @param isDistributed true if the CG will hold distributed volumes.
     * 
     * @throws VPlexApiException When an error occurs setting the consistency
     *             group properties.
     */
    public void updateConsistencyGroupProperties(String cgName, String clusterName,
            boolean isDistributed) throws VPlexApiException {
        s_logger.info("Request to update consistency group properties on VPlex at {}",
                _baseURI);

        List<VPlexClusterInfo> clusterInfoList = _discoveryMgr.getClusterInfoLite();
        if (!isDistributed) {
            Iterator<VPlexClusterInfo> clusterInfoIter = clusterInfoList.iterator();
            while (clusterInfoIter.hasNext()) {
                VPlexClusterInfo clusterInfo = clusterInfoIter.next();
                if (!clusterInfo.getName().equals(clusterName)) {
                    clusterInfoIter.remove();
                }
            }

            // Find the consistency group.
            VPlexConsistencyGroupInfo cgInfo = _discoveryMgr.findConsistencyGroup(cgName,
                    clusterInfoList, true);

            // The changes we need to make depend on if the the consistency group
            // has visibility to both clusters indicating it previously held
            // distributed volumes.
            List<String> visibleClusters = cgInfo.getVisibility();
            if (visibleClusters.size() > 1) {
                // Must set to no automatic winner as the CG may have previously held
                // distributed volumes that had a winner set, which would cause a failure
                // when we set update the visibility and cluster info.
                _cgMgr.setDetachRuleNoAutomaticWinner(cgInfo);

                // Now we must clear the CG storage clusters.
                _cgMgr.setConsistencyGroupStorageClusters(cgInfo, new ArrayList<VPlexClusterInfo>());

                // Now set visibility and cluster info for the CG in this order.
                _cgMgr.setConsistencyGroupVisibility(cgInfo, clusterInfoList);
                _cgMgr.setConsistencyGroupStorageClusters(cgInfo, clusterInfoList);
            } else if (!visibleClusters.contains(clusterName)) {
                // Update the visibility and cluster info for the CG in this order.
                _cgMgr.setConsistencyGroupStorageClusters(cgInfo, new ArrayList<VPlexClusterInfo>());
                _cgMgr.setConsistencyGroupVisibility(cgInfo, clusterInfoList);
                _cgMgr.setConsistencyGroupStorageClusters(cgInfo, clusterInfoList);
            }
        } else {
            // Find the consistency group.
            VPlexConsistencyGroupInfo cgInfo = _discoveryMgr.findConsistencyGroup(cgName,
                    clusterInfoList, true);

            // We only have to make changes if the visibility is not
            // currently both clusters.
            List<String> visibleClusters = cgInfo.getVisibility();
            if (visibleClusters.size() != 2) {
                // First clear the CG storage clusters.
                _cgMgr.setConsistencyGroupStorageClusters(cgInfo, new ArrayList<VPlexClusterInfo>());

                // Now set visibility and cluster info for the CG to both clusters
                // in this order.
                _cgMgr.setConsistencyGroupVisibility(cgInfo, clusterInfoList);
                _cgMgr.setConsistencyGroupStorageClusters(cgInfo, clusterInfoList);

                // Now set the detach rule to winner for the cluster with the passed name.
                VPlexClusterInfo winningCluster = null;
                Iterator<VPlexClusterInfo> clusterInfoIter = clusterInfoList.iterator();
                while (clusterInfoIter.hasNext()) {
                    VPlexClusterInfo clusterInfo = clusterInfoIter.next();
                    if (clusterInfo.getName().equals(clusterName)) {
                        winningCluster = clusterInfo;
                        break;
                    }
                }
                _cgMgr.setDetachRuleWinner(cgInfo, winningCluster);
            }
        }
    }

    /**
     * Updates the consistency group RP enabled tag.
     * 
     * @param cgName The name for the consistency group.
     * @param clusterName The name of the cluster for which the CG should be
     *            visible, when not distributed.
     * @param isRPEnabled true if the CG will hold RP protected VPLEX volumes.
     * 
     * @throws VPlexApiException When an error occurs setting the consistency
     *             group visibility.
     */
    public void updateConsistencyRPEnabled(String cgName, String clusterName,
            boolean isRPEnabled) throws VPlexApiException {
        s_logger.info("Request to update consistency group RP enabled on  VPlex at {}",
                _baseURI);
        List<VPlexClusterInfo> clusterInfoList = _discoveryMgr.getClusterInfoLite();
        Iterator<VPlexClusterInfo> clusterInfoIter = clusterInfoList.iterator();
        while (clusterInfoIter.hasNext()) {
            VPlexClusterInfo clusterInfo = clusterInfoIter.next();
            if (!clusterInfo.getName().equals(clusterName)) {
                clusterInfoIter.remove();
            }
        }
        _cgMgr.setConsistencyGroupRPEnabled(cgName, clusterInfoList, isRPEnabled);
    }

    /**
     * Adds the volumes with the passed names to the consistency group with the
     * passed name.
     * 
     * @param cgName The name of the consistency group to which the volumes are
     *            added.
     * @param virtualVolumeNames The names of the virtual volumes to be added to
     *            the consistency group.
     * 
     * @throws VPlexApiException When an error occurs adding the volumes to the
     *             consistency group.
     */
    public void addVolumesToConsistencyGroup(String cgName,
            List<String> virtualVolumeNames) throws VPlexApiException {
        s_logger.info("Request to add volumes to a consistency group on VPlex at {}",
                _baseURI);
        _cgMgr.addVolumesToConsistencyGroup(cgName, virtualVolumeNames);
    }

    /**
     * Removes the volumes with the passed names from the consistency group with
     * the passed name. If the removal of the volumes results in an empty group,
     * delete the consistency group if the passed flag so indicates.
     * 
     * @param virtualVolumeNames The names of the virtual volumes to be removed
     *            from the consistency group.
     * @param cgName The name of the consistency group from which the volume is
     *            removed.
     * @param deleteCGWhenEmpty true to delete the consistency group if the
     *            group is empty after removing the volumes, false otherwise.
     * 
     * @return true if the consistency group was deleted, false otherwise.
     * 
     * @throws VPlexApiException When an error occurs removing the volumes from
     *             the consistency group.
     */
    public boolean removeVolumesFromConsistencyGroup(List<String> virtualVolumeNames,
            String cgName, boolean deleteCGWhenEmpty) throws VPlexApiException {
        s_logger
                .info("Request to remove volumes from a consistency group on VPlex at {}",
                        _baseURI);
        return _cgMgr.removeVolumesFromConsistencyGroup(virtualVolumeNames, cgName,
                deleteCGWhenEmpty);
    }

    /**
     * Deletes the consistency group with the passed name.
     * 
     * @param cgName The name of the consistency group to be deleted.
     * 
     * @throws VPlexApiException When an error occurs deleting the consistency group.
     */
    public void deleteConsistencyGroup(String cgName) throws VPlexApiException {
        s_logger.info("Request to delete consistency group on VPlex at {}",
                _baseURI);
        _cgMgr.deleteConsistencyGroup(cgName);
    }

    /**
     * All cached data associated with the virtual volume with the passed name
     * is invalidated on all directors of the VPLEX cluster. Subsequent reads
     * from host applications will fetch data from storage volume due to cache
     * miss.
     * 
     * NOTE: According to VPLEX engineering, the execution of the cache-invalidate
     * command will wait 5 minutes for the invalidation to complete. If it does
     * not complete successfully or in error within this time, the command will
     * return a failure status, but return a value in the response "message"
     * indicating that the invalidate is still in progress. In this case, the
     * cache-invalidate-status command must be executed to monitor the progress
     * until it completes.
     * 
     * @param virtualVolumeName The name of the virtual volume.
     * 
     * @return true if the invalidate cache is still in progress when the
     *         call returns and the status must be monitored until it completes,
     *         false otherwise.
     * 
     * @throws VPlexApiException When an error occurs invalidating the cache.
     */
    public boolean invalidateVirtualVolumeCache(String virtualVolumeName)
            throws VPlexApiException {
        s_logger.info("Request to invalidate virtual volume cache on VPLEX at {}",
                _baseURI);
        return _virtualVolumeMgr.invalidateVirtualVolumeCache(virtualVolumeName);
    }

    /**
     * Get the cache invalidate status information for the virtual volume with
     * the passed name.
     * 
     * @param virtualVolumeName The name of the virtual volume.
     * 
     * @return A reference to a VPlexCacheStatusInfo specifying the cache status
     *         information.
     */
    public VPlexCacheStatusInfo getCacheStatus(String virtualVolumeName) throws VPlexApiException {
        s_logger.info("Request to get cache invalidate status information on VPLEX at {}",
                _baseURI);
        return _virtualVolumeMgr.getCacheStatus(virtualVolumeName);
    }

    /**
     * Will attempt to dismantle the local device by destroying all components used
     * to build up the device (i.e, extents).
     * 
     * @param deviceInfo The information specifying the device backend volume.
     * 
     * @throws VPlexApiException When an error occurs detaching the mirror from the volume.
     */
    public void deleteLocalDevice(VolumeInfo deviceInfo) throws VPlexApiException {
        s_logger.info("Request to delete local VPLex device at {}",
                _baseURI);
        _virtualVolumeMgr.deleteLocalDevice(deviceInfo);
    }

    /**
     * Will attempt to dismantle the local device by destroying all components used
     * to build up the device (i.e, extents).
     * 
     * @param localDeviceName The name of the local device.
     * 
     * @throws VPlexApiException When an error occurs detaching the mirror from the volume.
     */
    public void deleteLocalDevice(String localDeviceName) throws VPlexApiException {
        s_logger.info("Request to delete local VPLex device at {}",
                _baseURI);
        _virtualVolumeMgr.deleteLocalDevice(localDeviceName);
    }

    /**
     * Detaches the mirror specified by the passed mirror info from the
     * VPLEX volume with the passed name.
     * 
     * @param virtualVolumeName The name of the VPLEX volume.
     * @param mirrorDeviceName The name of the mirror device
     * @param discard The boolean to use discard
     * 
     * @throws VPlexApiException When an error occurs detaching the mirror from the volume.
     */
    public void detachMirrorFromLocalVirtualVolume(String virtualVolumeName,
            String mirrorDeviceName, boolean discard) throws VPlexApiException {
        s_logger.info("Request to detach a mirror from a local virtual volume at {}",
                _baseURI);
        _virtualVolumeMgr.detachMirrorFromLocalVirtualVolume(virtualVolumeName, mirrorDeviceName, discard);
    }

    /**
     * Detaches the mirror specified by the passed mirror info from the
     * VPLEX volume with the passed name.
     * 
     * @param virtualVolumeName The name of the VPLEX volume.
     * @param mirrorDeviceName The name of the mirror device
     * @param discard The boolean to use discard
     * 
     * @throws VPlexApiException When an error occurs detaching the mirror from the volume.
     */
    public void detachLocalMirrorFromDistributedVirtualVolume(String virtualVolumeName,
            String mirrorDeviceName, boolean discard) throws VPlexApiException {
        s_logger.info("Request to detach a mirror from a local virtual volume at {}",
                _baseURI);
        _virtualVolumeMgr.detachLocalMirrorFromDistributedVirtualVolume(virtualVolumeName, mirrorDeviceName, discard);
    }

    /**
     * Detaches the mirror specified by the passed mirror info from the
     * distributed VPLEX volume with the passed name.
     * 
     * @param virtualVolumeName The name of the VPLEX distributed volume.
     * @param clusterId The cluster of the mirror to detach.
     * 
     * @return The name of the detached mirror for use when reattaching the mirror.
     * 
     * @throws VPlexApiException When an error occurs detaching the mirror from
     *             the volume.
     */
    public String detachMirrorFromDistributedVolume(String virtualVolumeName,
            String clusterId) throws VPlexApiException {
        s_logger.info("Request to detach a mirror from a distributed volume at {}",
                _baseURI);
        return _virtualVolumeMgr.detachMirrorFromDistributedVolume(virtualVolumeName, clusterId);
    }

    /**
     * Reattach the mirror on the specified cluster to the distributed VPLEX
     * volume with the passed name.
     * 
     * @param virtualVolumeName The name of the VPLEX distributed volume.
     * @param detachedDeviceName The local device name of the mirror previously detached.
     * 
     * @throws VPlexApiException When an error occurs reattaching the mirror to
     *             the volume.
     */
    public void reattachMirrorToDistributedVolume(String virtualVolumeName,
            String detachedDeviceName) throws VPlexApiException {
        s_logger.info("Request to reattach mirror to distributed volume on VPLEX at {}",
                _baseURI);
        _virtualVolumeMgr.reattachMirrorToDistributedVolume(virtualVolumeName, detachedDeviceName);
    }

    public VPlexVirtualVolumeInfo upgradeVirtualVolumeToDistributed(VPlexVirtualVolumeInfo virtualVolume,
            VolumeInfo newRemoteVolume, boolean discoveryRequired, boolean rename, String clusterId) throws VPlexApiException {
        return _virtualVolumeMgr.createDistributedVirtualVolume(
                virtualVolume, newRemoteVolume, discoveryRequired, rename, clusterId);
    }

    public WaitOnRebuildResult waitOnRebuildCompletion(String virtualVolume)
            throws VPlexApiException {
        return _virtualVolumeMgr.waitOnRebuildCompletion(virtualVolume);
    }

    /**
     * Returns a map of the port WWNs of all initiators to the initiator name in
     * the VPlex.
     * 
     * @param clusterName indicates which VPlex cluster to perform the operation on
     * @return a map of port WWNs to initiator names. Note the keys (WWNs) have
     *         no colons.
     */
    public Map<String, String> getInitiatorWwnToNameMap(String clusterName) {
        return _discoveryMgr.getInitiatorWwnToNameMap(clusterName);
    }

    /**
     * Returns a list of VPlexStorageViewInfo objects representing
     * storage views that contain the given initiator names.
     * 
     * @param clusterName the VPLEX cluster to look in
     * @param initiatorNames the initiator names to look for
     * @return a list of VPlexStorageViewInfo objects
     */
    public List<VPlexStorageViewInfo> getStorageViewsContainingInitiators(
            String clusterName, List<String> initiatorNames) {
        return _discoveryMgr.getStorageViewsContainingInitiators(clusterName,
                initiatorNames);
    }

    /**
     * Finds the cluster name for a given cluster id.
     * 
     * @param clusterId the cluster id (1 or 2)
     * @return the name for the cluster or null in none found
     */
    public String getClusterName(String clusterId) {
        String clusterName = null;
        List<VPlexClusterInfo> clusterInfos = _discoveryMgr.getClusterInfo(true, false);
        for (VPlexClusterInfo clusterInfo : clusterInfos) {
            if (clusterId.equals(clusterInfo.getClusterId())) {
                clusterName = clusterInfo.getName();
                s_logger.info("found cluster name {} for cluster id {}", clusterName, clusterId);
            }
        }

        return clusterName;
    }

    /**
     * Gets a map of cluster IDs to cluster names for the VPLEX device.
     * 
     * @return a map of cluster IDs to cluster names for the VPLEX device
     */
    public Map<String, String> getClusterIdToNameMap() {
        Map<String, String> clusterIdToNameMap = new HashMap<String, String>();
        List<VPlexClusterInfo> clusterInfos = _discoveryMgr.getClusterInfo(true, false);
        for (VPlexClusterInfo clusterInfo : clusterInfos) {
            clusterIdToNameMap.put(clusterInfo.getClusterId(), clusterInfo.getName());
        }

        s_logger.info("cluster id to name map is " + clusterIdToNameMap.toString());
        return clusterIdToNameMap;
    }

    /**
     * Package protected getter for the base URI for the client.
     * 
     * @return The base URI for the client.
     */
    URI getBaseURI() {
        return _baseURI;
    }

    /**
     * Package protected getter for the API client discovery manager.
     * 
     * @return The API client discovery manager.
     */
    VPlexApiDiscoveryManager getDiscoveryManager() {
        return _discoveryMgr;
    }

    /**
     * Package protected getter for the API client virtual volume manager.
     * 
     * @return The API client virtual volume manager.
     */
    VPlexApiVirtualVolumeManager getVirtualVolumeManager() {
        return _virtualVolumeMgr;
    }

    /**
     * Package protected getter for the API client export manager.
     * 
     * @return The API client export manager.
     */
    VPlexApiExportManager getExportManager() {
        return _exportMgr;
    }

    /**
     * Execute a GET request with the default JSON format=0 and cache
     * control max age value of 0 seconds.
     * 
     * @param resourceURI The resource URI.
     * @return The client response.
     */
    ClientResponse get(URI resourceURI) {
        return get(resourceURI,
                VPlexApiConstants.ACCEPT_JSON_FORMAT_0,
                VPlexApiConstants.CACHE_CONTROL_MAXAGE_ZERO);
    }

    /**
     * Execute a GET request with the cache control max age value of
     * 0 seconds and JSON format set by the caller.
     * 
     * @param resourceURI The resource URI.
     * @param jsonFormat The expected JSON response format.
     *            See VPlexApiConstants.ACCEPT_JSON_FORMAT_*
     * @return The client response.
     */
    ClientResponse get(URI resourceURI, String jsonFormat) {
        return get(resourceURI, jsonFormat,
                VPlexApiConstants.CACHE_CONTROL_MAXAGE_ZERO);
    }

    /**
     * Package protected method for executing a GET request.
     * 
     * @param resourceURI The resource URI.
     * @param jsonFormat The expected JSON response format.
     *            See VPlexApiConstants.ACCEPT_JSON_FORMAT_*
     * @param cacheControlMaxAge cache control max age
     * 
     * @return The client response.
     */
    ClientResponse get(URI resourceURI, String jsonFormat, String cacheControlMaxAge) {
        int retryCount = 0;
        ClientResponse response = null;
        while (++retryCount <= GET_RETRY_COUNT) {
            try {
                response = _client.get(resourceURI, _vplexSessionId,
                        jsonFormat, cacheControlMaxAge);
                updateVPLEXSessionId(response);
                break;
            } catch (Exception e) {
                s_logger.warn("VPLEX API client GET request {} failed. Retrying...", resourceURI, e);
                if (retryCount < GET_RETRY_COUNT) {
                    VPlexApiUtils.pauseThread(GET_SLEEP_TIME_MS);
                } else {
                    throw e;
                }
            }
        }
        return response;
    }

    /**
     * Execute a POST request with the default JSON format=0.
     * 
     * @param resourceURI The resource URI.
     * @param postData The POST data.
     * @return The client response.
     */
    ClientResponse post(URI resourceUri, String postData) {
        return post(resourceUri, postData, VPlexApiConstants.ACCEPT_JSON_FORMAT_0);
    }

    /**
     * Package protected method for executing a POST request.
     * 
     * @param resourceURI The resource URI.
     * @param postData The POST data.
     * 
     * @return The client response.
     */
    ClientResponse post(URI resourceURI, String postData, String jsonFormat) {
        ClientResponse response = _client.post(resourceURI, postData, _vplexSessionId, jsonFormat);
        updateVPLEXSessionId(response);

        // We add a sleep here to workaround an issue with the VPLEX API.
        // When a new VPLEX artifact is successfully created (response status 200),
        // and we subsequently make a request to GET all such artifacts, VPLEX
        // on occasion does not return the artifact that was just successfully
        // created. This is because the VPLEX is still asynchronously persisting
        // the new object when it returns success and the subsequent GET, which
        // occurs over a new connection (with a new VPLEX session) will not
        // return the newly created object. My understanding is that we would
        // have to make the GET request using the same VPLEX session id, which
        // means we would have to get in the business of managing VPLEX session
        // ids, which we really don't want to do. So, for now we add a small
        // sleep interval after making POST requests (which are used to
        // make configuration changes on the VPLEX) to allow the VPLEX time
        // to persist changes completely and cause this issue to be far less
        // likely to occur.
        VPlexApiUtils.pauseThread(5000);
        return response;
    }

    /**
     * Execute a PUT request with the default JSON format=0.
     * 
     * @param resourceURI The resource URI.
     * @return The client response.
     */
    ClientResponse put(URI resourceURI) {
        return put(resourceURI, VPlexApiConstants.ACCEPT_JSON_FORMAT_0);
    }

    /**
     * Package protected method for executing a PUT request.
     * 
     * @param resourceURI The resource URI.
     * @param jsonFormat The expected JSON response format.
     *            See VPlexApiConstants.ACCEPT_JSON_FORMAT_*
     * 
     * @return The client response.
     */
    ClientResponse put(URI resourceURI, String jsonFormat) {
        ClientResponse response = _client.put(resourceURI, _vplexSessionId, jsonFormat);
        updateVPLEXSessionId(response);
        return response;
    }

    /**
     * When a VPlex command is executed via a POST request, if that command
     * takes longer than 60 seconds to complete, the VPlex command will return a
     * response with 202 status. The Location header of that response will point
     * to a URI which specifies a task that can be monitored. This function gets
     * the task resource from the Location header of the passed asynchronous
     * response and will wait for the task to complete successfully, fail, or
     * timeout.
     * 
     * @param asyncResponse A response from the VPlex indicating a command is being run
     *            asynchronously because it is taking too long.
     * 
     * @throws VPlexApiException When the command completes unsuccessfully or we
     *             time out because it is taking too long.
     */
    String waitForCompletion(ClientResponse asyncResponse) throws VPlexApiException {
        MultivaluedMap<String, String> headers = asyncResponse.getHeaders();
        String taskResourceStr = headers.getFirst(VPlexApiConstants.LOCATION_HEADER);
        if (taskResourceStr == null) {
            throw new VPlexApiException("Can't find location for asynchronous reponse.");
        }
        s_logger.info("Waiting for task {} to complete", taskResourceStr);
        // Check the task for completion until we've reached the
        // maximum number of status checks.
        int retries = 0;
        while (retries++ < VPlexApiConstants.MAX_RETRIES) {
            ClientResponse taskResponse = get(URI.create(taskResourceStr));
            String responseStr = taskResponse.getEntity(String.class);
            s_logger.info("Wait for completion response is {}", responseStr);
            int taskStatus = taskResponse.getStatus();
            taskResponse.close();
            if (taskStatus == VPlexApiConstants.SUCCESS_STATUS) {
                // Task completed successfully
                s_logger.info("Task {} completed successfully", taskResourceStr);
                return responseStr;
            } else if (taskStatus != VPlexApiConstants.TASK_PENDING_STATUS) {
                // Task failed.
                throw new VPlexApiException(String.format(
                        "Task %s did not complete successfully", taskResourceStr));
            } else {
                // Task is still pending completion, sleep a bit and check again.
                VPlexApiUtils.pauseThread(VPlexApiConstants.TASK_PENDING_WAIT_TIME);
            }
        }

        // We've timed out waiting for the operation to complete.
        throw VPlexApiException.exceptions
                .timeoutWaitingForAsyncOperationToComplete(taskResourceStr);
    }

    /**
     * Gets the target info for the passed ports.
     * 
     * @param portInfoList The list of ports.
     * 
     * @return A map of the associated target info for the ports keyed by the
     *         port WWN.
     * 
     * @throws VPlexApiException When an error occurs getting the target info.
     */
    public Map<String, VPlexTargetInfo> getTargetInfoForPorts(
            List<VPlexPortInfo> portInfoList) throws VPlexApiException {
        s_logger.info("Request to get target info for ports at {}", _baseURI);
        return _exportMgr.getTargetInfoForPorts(portInfoList);
    }

    /**
     * Finds virtual volume by name.
     * 
     * @param virtualVolumeName Name of virtual volume.
     * @return A reference to the virtual volume info.
     */
    public VPlexVirtualVolumeInfo findVirtualVolume(String virtualVolumeName) {
        return _discoveryMgr.findVirtualVolume(virtualVolumeName, false);
    }

    /**
     * Finds virtual volume by name.
     * 
     * @param clusterInfoList List of detailed VPLEX cluster info.
     * @param virtualVolumeInfos List of virtual volumes to find
     * @return A map of virtual volume name to the virtual volume info.
     */
    public Map<String, VPlexVirtualVolumeInfo> findVirtualVolumes(List<VPlexClusterInfo> clusterInfoList,
            List<VPlexVirtualVolumeInfo> virtualVolumeInfos) {
        return _discoveryMgr.findVirtualVolumes(clusterInfoList, virtualVolumeInfos, true, true);
    }

    /**
     * Updates the VPLEX session id with the session specified in the
     * passed response.
     * 
     * @param response A response resulting from a request to the VPLEX.
     */
    private void updateVPLEXSessionId(ClientResponse response) {
        List<NewCookie> cookies = response.getCookies();
        for (NewCookie cookie : cookies) {
            // Look for the session cookie. It should returned in the
            // response if this is the first request from this client
            // or the session expired since the last request, in which
            // case the VPLEX creates a new session and returns the
            // id in the cookie.
            if (VPlexApiConstants.SESSION_COOKIE.equals(cookie.getName())) {
                String newSessionId = cookie.getValue();
                if ((_vplexSessionId == null) || (!_vplexSessionId.equals(newSessionId))) {
                    s_logger.info("VPLEX Session ID changing from {} to {}",
                            (_vplexSessionId == null ? "null" : _vplexSessionId),
                            newSessionId);
                    _vplexSessionId = newSessionId;
                }
                break;
            }
        }
    }

    /**
     * Returns a Map of lowest-level storage-volume resource's WWN to its VPlexStorageVolumeInfo
     * object for a given device name, virtual volume type, and cluster name. If
     * hasMirror is true, this indicates the top-level device is composed of a
     * RAID-1 mirror, so there's an extra layers of components to traverse in finding
     * the lowest-level storage-volume resources.
     * 
     * @param deviceName the name of the top-level device to look at
     * @param virtualVolumeType the type of virtual volume (local or distributed)
     * @param clusterName the cluster name
     * @param hasMirror indicates if the top-level device is a RAID-1 mirror
     * 
     * @return a map of WWNs to VPlexStorageVolumeInfo objects
     * @throws VPlexApiException
     */
    public Map<String, VPlexStorageVolumeInfo> getStorageVolumeInfoForDevice(
            String deviceName, String virtualVolumeType,
            String clusterName, boolean hasMirror) throws VPlexApiException {
        s_logger.info("Request to find storage volume wwns for {} on VPLEX at {}",
                deviceName, _baseURI);

        List<VPlexStorageVolumeInfo> storageVolumes = getDiscoveryManager()
                .getStorageVolumesForDevice(deviceName, virtualVolumeType, clusterName, hasMirror);

        if (!storageVolumes.isEmpty()) {
            s_logger.info("storage volumes found:");
            Iterator<VPlexStorageVolumeInfo> it = storageVolumes.iterator();
            while (it.hasNext()) {
                VPlexStorageVolumeInfo info = it.next();
                s_logger.info(info.toString());
                if (!VPlexApiConstants.STORAGE_VOLUME_TYPE.equals(info.getComponentType())) {
                    s_logger.warn("Unexpected component type {} found for volume {}",
                            info.getComponentType(), info.getName());
                    it.remove();
                }
            }
        }

        Map<String, VPlexStorageVolumeInfo> storageVolumeWwns = new HashMap<String, VPlexStorageVolumeInfo>();
        for (VPlexStorageVolumeInfo info : storageVolumes) {
            if (null == info.getWwn()) {
                String reason = "could not parse WWN for storage volume " + info.getName();
                s_logger.error(reason);
                throw VPlexApiException.exceptions.failedGettingStorageVolumeInfoForIngestion(reason);
            }
            String wwn = info.getWwn();
            if (wwn != null && !wwn.isEmpty()) {
                wwn = wwn.replaceAll("[^A-Fa-f0-9]", "");
                wwn = wwn.toUpperCase();
            }
            storageVolumeWwns.put(wwn, info);
        }

        return storageVolumeWwns;
    }

    /**
     * Returns the top-level supporting device name for a given storage volume native id,
     * wwn, and backend array serial number.
     * 
     * @param volumeNativeId the storage volume's native id
     * @param wwn the storage volume's wwn
     * @param backendArraySerialNum the serial number of the backend array
     * 
     * @return the name of the top level device for the given storage volume
     * @throws VPlexApiException
     */
    @Deprecated
    public String getDeviceForStorageVolume(String volumeNativeId,
            String wwn, String backendArraySerialNum) throws VPlexApiException {

        s_logger.info("Request to find device name for storage volume {} on VPLEX at {}",
                volumeNativeId, _baseURI);

        String deviceName = getDiscoveryManager()
                .getDeviceForStorageVolume(volumeNativeId, wwn, backendArraySerialNum);

        return deviceName;
    }

    /**
     * Returns a VPlexResourceInfo object for the given device name based
     * on its virtual volume type (local or distributed).
     * 
     * @param deviceName the name of the device
     * @param virtualVolumeType the type of virtual volume (local or distributed)
     * 
     * @return a VPlexResourceInfo object for the device name
     * @throws VPlexApiException
     */
    public VPlexResourceInfo getDeviceStructure(String deviceName, String virtualVolumeType)
            throws VPlexApiException {
        s_logger.info("Request to find {} device structure for {} on VPLEX at " + _baseURI,
                virtualVolumeType, deviceName);

        VPlexResourceInfo device = null;

        switch (virtualVolumeType) {
            case VPlexApiConstants.DISTRIBUTED_VIRTUAL_VOLUME:
                device = getDiscoveryManager()
                        .getDeviceStructureForDistributedIngestion(deviceName);
                break;
            case VPlexApiConstants.LOCAL_VIRTUAL_VOLUME:
                device = getDiscoveryManager()
                        .getDeviceStructureForLocalIngestion(deviceName);
                break;
        }

        return device;
    }

    /**
     * Returns a Map of distributed device component context
     * paths from the VPLEX API to VPLEX cluster names.
     * 
     * @return a Map of distributed device component context
     *         paths from the VPLEX API to VPLEX cluster names
     * 
     * @throws VPlexApiException
     */
    public Map<String, String> getDistributedDevicePathToClusterMap()
            throws VPlexApiException {
        return _discoveryMgr.getDistributedDevicePathToClusterMap();
    }

    /**
     * This method finds virtual volume on the VPLEX and then updates virtual volume info.
     * 
     * @param virtualVolumeName virtual volume name
     * @param discoveryMgr reference to VPlexApiDiscoveryManager
     * @return VPlexVirtualVolumeInfo object with updated info.
     */
    public VPlexVirtualVolumeInfo findVirtualVolumeAndUpdateInfo(String virtualVolumeName) {
        return _virtualVolumeMgr.findVirtualVolumeAndUpdateInfo(virtualVolumeName, null);
    }

    /**
     * This method collapses the one legged device for the passed virtual volume device.
     * After this device will change back to local device.
     * 
     * @param sourceDeviceName source device name
     * @throws VPlexApiException
     */
    public void deviceCollapse(String sourceDeviceName) throws VPlexApiException {
        s_logger.info("Request to collpase device {}", _baseURI);
        _virtualVolumeMgr.deviceCollapse(sourceDeviceName);
    }

    /**
     * This method sets device visibility to local.
     * 
     * @param sourceDeviceName source device name
     * @throws VPlexApiException
     */
    public void setDeviceVisibility(String sourceDeviceName) throws VPlexApiException {
        s_logger.info("Request to set device visibility {}", _baseURI);
        _virtualVolumeMgr.setDeviceVisibility(sourceDeviceName);
    }

    /**
     * Calls the VPLEX CLI "drill-down" command for the given device name.
     * 
     * @param deviceName a device name to check with the drill-down command
     * @return the String drill-down command response from the VPLEX API
     * @throws VPlexApiException if the device structure is incompatible with ViPR
     */
    public String getDrillDownInfoForDevice(String deviceName) throws VPlexApiException {
        return _discoveryMgr.getDrillDownInfoForDevice(deviceName);
    }

}
