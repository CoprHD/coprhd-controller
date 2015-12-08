/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StoragePool.SupportedDriveTypeValues;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.OperationalStatus;
import com.emc.storageos.db.client.model.StoragePort.PortType;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.xtremio.XtremIOUnManagedVolumeDiscoverer;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOApiException;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiator;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPort;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOSystem;

public class XtremIOCommunicationInterface extends
        ExtendedCommunicationInterfaceImpl {

    private static final Logger _logger = LoggerFactory
            .getLogger(XtremIOCommunicationInterface.class);
    private static final String UP = "up";
    private static final String NEW = "new";
    private static final String EXISTING = "existing";

    private XtremIOClientFactory xtremioRestClientFactory = null;
    private XtremIOUnManagedVolumeDiscoverer unManagedVolumeDiscoverer;

    public void setXtremioRestClientFactory(
            XtremIOClientFactory xtremioRestClientFactory) {
        this.xtremioRestClientFactory = xtremioRestClientFactory;
    }

    public void setUnManagedVolumeDiscoverer(XtremIOUnManagedVolumeDiscoverer unManagedVolumeDiscoverer) {
        this.unManagedVolumeDiscoverer = unManagedVolumeDiscoverer;
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile)
            throws BaseCollectionException {

    }

    @Override
    public void scan(AccessProfile accessProfile)
            throws BaseCollectionException {
        _logger.info("Scanning started for provider: {}", accessProfile.getSystemId());
        StorageProvider.ConnectionStatus cxnStatus = StorageProvider.ConnectionStatus.CONNECTED;
        StorageProvider provider = _dbClient.queryObject(StorageProvider.class,
                accessProfile.getSystemId());
        XtremIOClient xtremIOClient = null;
        try {
            xtremIOClient = (XtremIOClient) xtremioRestClientFactory.getXtremIOV1Client(
                    URI.create(XtremIOConstants.getXIOBaseURI(accessProfile.getIpAddress(), accessProfile.getPortNumber())),
                    accessProfile.getUserName(), accessProfile.getPassword(), true);
            String xmsVersion = xtremIOClient.getXtremIOXMSVersion();
            String minimumSupportedVersion = VersionChecker
                    .getMinimumSupportedVersion(StorageSystem.Type.xtremio).replace("-", ".");
            String compatibility = (VersionChecker.verifyVersionDetails(minimumSupportedVersion, xmsVersion) < 0) ?
                    StorageSystem.CompatibilityStatus.INCOMPATIBLE.name() :
                    StorageSystem.CompatibilityStatus.COMPATIBLE.name();
            provider.setCompatibilityStatus(compatibility);
            provider.setVersionString(xmsVersion);

            String systemType = StorageSystem.Type.xtremio.name();
            List<XtremIOSystem> xioSystems = xtremIOClient.getXtremIOSystemInfo();
            _logger.info("Found {} clusters during scan of XMS {}", xioSystems.size(), accessProfile.getIpAddress());
            Map<String, StorageSystemViewObject> storageSystemsCache = accessProfile.getCache();
            for (XtremIOSystem system : xioSystems) {
                String arrayNativeGUID = NativeGUIDGenerator.generateNativeGuid(DiscoveredDataObject.Type.xtremio.name(),
                        system.getSerialNumber());
                StorageSystemViewObject viewObject = storageSystemsCache.get(arrayNativeGUID);
                if (viewObject == null) {
                    viewObject = new StorageSystemViewObject();
                }
                viewObject.setDeviceType(systemType);
                viewObject.addprovider(accessProfile.getSystemId().toString());
                viewObject.setProperty(StorageSystemViewObject.SERIAL_NUMBER, system.getSerialNumber());
                viewObject.setProperty(StorageSystemViewObject.VERSION, system.getVersion());
                viewObject.setProperty(StorageSystemViewObject.STORAGE_NAME, arrayNativeGUID);
                storageSystemsCache.put(arrayNativeGUID, viewObject);
            }
        } catch (Exception ex) {
            _logger.error("Error scanning XMS", ex);
            cxnStatus = StorageProvider.ConnectionStatus.NOTCONNECTED;
            // throw exception only if system discovery failed.
            throw XtremIOApiException.exceptions.discoveryFailed(provider.toString());
        } finally {
            provider.setConnectionStatus(cxnStatus.name());
            _dbClient.persistObject(provider);
            xtremIOClient.close();
            _logger.info("Completed scan of XtremIO StorageProvider. IP={}", accessProfile.getIpAddress());
        }
    }

    @Override
    public void discover(AccessProfile accessProfile) throws BaseCollectionException {

        _logger.info("Entered XtremIO {} -->{}", accessProfile.toString());
        if (null != accessProfile.getnamespace()
                && (accessProfile.getnamespace().equals(StorageSystem.Discovery_Namespaces.UNMANAGED_VOLUMES.toString()))) {
            discoverUnManagedVolumes(accessProfile);
        } else {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, accessProfile.getSystemId());
            xtremioRestClientFactory.setModel(storageSystem.getFirmwareVersion());
            XtremIOClient xtremIOClient = (XtremIOClient) xtremioRestClientFactory.getRESTClient(
                    URI.create(XtremIOConstants.getXIOBaseURI(accessProfile.getIpAddress(), accessProfile.getPortNumber())),
                    accessProfile.getUserName(), accessProfile.getPassword(), true);
            _logger.info("Discovery started for system {}", accessProfile.getSystemId());
            discoverXtremIOSystem(xtremIOClient, storageSystem);
        }
    }

    public void discoverUnManagedVolumes(AccessProfile accessProfile) {
        StorageSystem storageSystem = null;
        String detailedStatusMessage = null;
        try {
            storageSystem = _dbClient.queryObject(StorageSystem.class, accessProfile.getSystemId());
            if (null == storageSystem) {
                return;
            }

            storageSystem.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS.toString());
            _dbClient.persistObject(storageSystem);
            if (accessProfile.getnamespace().equals(StorageSystem.Discovery_Namespaces.UNMANAGED_VOLUMES.toString())) {
                unManagedVolumeDiscoverer.discoverUnManagedObjects(accessProfile, _dbClient, _partitionManager);
            }

            // discovery succeeds
            detailedStatusMessage = String.format("UnManaged Volumes Discovery completed successfully for XtremIO: %s",
                    storageSystem.getId().toString());
            _logger.info(detailedStatusMessage);

        } catch (Exception e) {
            detailedStatusMessage = String.format("Discovery of unmanaged volumes failed for system %s because %s", storageSystem
                    .getId().toString(), e.getLocalizedMessage());
            _logger.error(detailedStatusMessage, e);
            throw XtremIOApiException.exceptions.discoveryFailed(storageSystem.getId().toString());
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while updating unmanaged volume discovery status for system.", ex);
                }
            }
        }
    }

    private void discoverXtremIOSystem(XtremIOClient restClient, StorageSystem systemInDB) {
        try {
            List<StoragePool> pools = new ArrayList<StoragePool>();
            XtremIOSystem clusterObject = restClient.getClusterDetails(systemInDB.getSerialNumber());

            updateStorageSystemAndPools(clusterObject, systemInDB, pools);
            Map<String, List<StoragePort>> portMap = discoverPorts(restClient, systemInDB);
            List<StoragePort> allPorts = new ArrayList<StoragePort>();
            allPorts.addAll(portMap.get(NEW));
            allPorts.addAll(portMap.get(EXISTING));
            List<StoragePort> notVisiblePorts = DiscoveryUtils.checkStoragePortsNotVisible(
                    allPorts, _dbClient, systemInDB.getId());

            List<StoragePort> allExistingPorts = new ArrayList<StoragePort>(portMap.get(EXISTING));
            if (notVisiblePorts != null && !notVisiblePorts.isEmpty()) {
                allExistingPorts.addAll(notVisiblePorts);
            }
            StoragePortAssociationHelper.runUpdatePortAssociationsProcess(portMap.get(NEW),
                    allExistingPorts, _dbClient, _coordinator,
                    pools);

            discoverInitiators(restClient, systemInDB);
        } catch (Exception e) {
            _logger.error("Error discovering XtremIO cluster", e);
            // throw exception only if system discovery failed.
            throw XtremIOApiException.exceptions.discoveryFailed(systemInDB.toString());
        }
    }

    private void updateStorageSystemAndPools(XtremIOSystem system, StorageSystem systemInDB, List<StoragePool> pools) {
        StoragePool xioSystemPool = null;
        if (null != systemInDB) {
            // systemInDB.set
            String minimumSupported = VersionChecker
                    .getMinimumSupportedVersion(StorageSystem.Type.xtremio).replace("-", ".");
            _logger.info("Minimum Supported Version {}", minimumSupported);
            String compatibility = (VersionChecker.verifyVersionDetails(minimumSupported,
                    system.getVersion()) < 0) ? StorageSystem.CompatibilityStatus.INCOMPATIBLE
                    .name() : StorageSystem.CompatibilityStatus.COMPATIBLE.name();
            systemInDB.setCompatibilityStatus(compatibility);
            systemInDB.setReachableStatus(true);
            _dbClient.persistObject(systemInDB);
        } else {
            throw XtremIOApiException.exceptions.discoveryFailed(system.getSerialNumber());
        }

        try {
            String poolNativeGUID = NativeGUIDGenerator.generateNativeGuid(
                    systemInDB, system.getSerialNumber(), NativeGUIDGenerator.POOL);
            _logger.info("Pool Native Guid : {}", poolNativeGUID);
            @SuppressWarnings("deprecation")
            List<URI> uriList = _dbClient
                    .queryByConstraint(AlternateIdConstraint.Factory.getStoragePoolByNativeGuidConstraint(poolNativeGUID));

            if (uriList.isEmpty()) {
                xioSystemPool = new StoragePool();
                xioSystemPool.setId(URIUtil.createId(StoragePool.class));
                xioSystemPool.setNativeGuid(poolNativeGUID);
                xioSystemPool.setPoolServiceType(PoolServiceType.block.name());
                xioSystemPool.setLabel(poolNativeGUID);
                xioSystemPool.setPoolName(poolNativeGUID);
                StringSet protocols = new StringSet();
                protocols.add("FC");
                protocols.add("iSCSI");
                xioSystemPool.setProtocols(protocols);
                StringSet driveTypes = new StringSet();
                driveTypes.add(SupportedDriveTypeValues.SSD.toString());
                xioSystemPool.addDriveTypes(driveTypes);
            } else {
                // TODO : update System details
                xioSystemPool = _dbClient.queryObject(StoragePool.class,
                        uriList.get(0));
            }
            // fake value set to total capacity
            xioSystemPool.setMaximumThinVolumeSize(system.getTotalCapacity());
            xioSystemPool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY.name());
            xioSystemPool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THIN_ONLY.name());
            xioSystemPool.setPoolServiceType(PoolServiceType.block.name());
            xioSystemPool.setFreeCapacity(system.getTotalCapacity() - system.getUsedCapacity());
            xioSystemPool.setTotalCapacity(system.getTotalCapacity());
            if ((xioSystemPool.getStorageDevice() == null) || !xioSystemPool.getStorageDevice().equals(systemInDB.getId())) {
                xioSystemPool.setStorageDevice(systemInDB.getId());
            }
            pools.add(xioSystemPool);
            xioSystemPool.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.toString());
            xioSystemPool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());

            if (uriList.isEmpty()) {
                _dbClient.createObject(xioSystemPool);
            } else {
                _dbClient.persistObject(xioSystemPool);
            }

        } catch (Exception e) {
            _logger.error("Problem while creating/updating XtremIO Storage Pool", e);
        }
    }

    private StorageHADomain createStorageHADomain(StorageSystem system, String scName, int numOfPorts) {
        StorageHADomain haDomain = null;
        String haDomainNativeGUID = NativeGUIDGenerator.generateNativeGuid(system, scName, NativeGUIDGenerator.ADAPTER);
        _logger.info("HA Domain Native Guid : {}", haDomainNativeGUID);
        @SuppressWarnings("deprecation")
        List<URI> uriHaList = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStorageHADomainByNativeGuidConstraint(haDomainNativeGUID));
        if (uriHaList.isEmpty()) {
            haDomain = new StorageHADomain();
            haDomain.setId(URIUtil.createId(StorageHADomain.class));
            haDomain.setNativeGuid(haDomainNativeGUID);
            haDomain.setName(scName);
            haDomain.setAdapterName(scName);
            haDomain.setStorageDeviceURI(system.getId());
            haDomain.setNumberofPorts(String.valueOf(numOfPorts));
            _dbClient.createObject(haDomain);
        } else {
            haDomain = _dbClient.queryObject(StorageHADomain.class, uriHaList.get(0));
            haDomain.setNumberofPorts(String.valueOf(numOfPorts));
            _dbClient.persistObject(haDomain);
        }

        return haDomain;
    }

    private Map<String, List<StoragePort>> discoverPorts(XtremIOClient restClient, StorageSystem system) {
        Map<String, List<StoragePort>> portMap = new HashMap<String, List<StoragePort>>();
        try {
            String clusterName = restClient.getClusterDetails(system.getSerialNumber()).getName();
            List<XtremIOPort> targetPorts = restClient.getXtremIOPortInfo(clusterName);
            Map<String, List<XtremIOPort>> storageControllerPortMap = new HashMap<String, List<XtremIOPort>>();
            for (XtremIOPort targetPort : targetPorts) {
                String scName = targetPort.getNodeInfo().get(1);
                List<XtremIOPort> scPorts = storageControllerPortMap.get(scName);
                if (scPorts == null) {
                    scPorts = new ArrayList<XtremIOPort>();
                    storageControllerPortMap.put(scName, scPorts);
                }
                scPorts.add(targetPort);
            }

            portMap.put(NEW, new ArrayList<StoragePort>());
            portMap.put(EXISTING, new ArrayList<StoragePort>());
            Long portSpeed = 0L;
            StoragePort port = null;

            for (String scName : storageControllerPortMap.keySet()) {
                List<XtremIOPort> scPorts = storageControllerPortMap.get(scName);
                StorageHADomain haDomain = createStorageHADomain(system, scName, scPorts.size());
                for (XtremIOPort targetPort : scPorts) {
                    String portSpeedStr = targetPort.getPortSpeed().split("G")[0];
                    try {
                        portSpeed = Long.parseLong(portSpeedStr);
                    } catch (NumberFormatException nfe) {
                        portSpeed = 0L;
                    }

                    String nativeGuid = NativeGUIDGenerator.generateNativeGuid(system, targetPort.getPortAddress(),
                            NativeGUIDGenerator.PORT);
                    _logger.info("Speed, Target Port Native Guid {} {}", portSpeed, nativeGuid);

                    @SuppressWarnings("deprecation")
                    List<URI> uriList = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getStoragePortByNativeGuidConstraint(nativeGuid));
                    if (uriList.isEmpty()) {
                        _logger.info("Creating a Target Port {}", nativeGuid);
                        port = new StoragePort();
                        port.setId(URIUtil.createId(StoragePort.class));
                        port.setNativeGuid(nativeGuid);
                        port.setPortSpeed(portSpeed);
                        port.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.toString());
                        port.setPortType(PortType.frontend.toString());
                        if ("iscsi".equalsIgnoreCase(targetPort.getPortType().toLowerCase())) {
                            port.setTransportType(StoragePort.TransportType.IP.toString());
                            port.setPortNetworkId(targetPort.getPortAddress().toLowerCase());
                        } else {
                            port.setTransportType(targetPort.getPortType().toUpperCase());
                            // to make it uniform across other arrays
                            port.setPortNetworkId(targetPort.getPortAddress().toUpperCase());
                        }
                        port.setStorageDevice(system.getId());
                        port.setPortName(targetPort.getName());
                        port.setLabel(nativeGuid);
                        port.setOperationalStatus(getOperationalStatus(targetPort).toString());
                        port.setPortGroup(haDomain.getAdapterName());
                        port.setStorageHADomain(haDomain.getId());
                        port.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                        portMap.get(NEW).add(port);
                        _dbClient.createObject(port);
                    } else {
                        _logger.info("Updating a Target Port {}", nativeGuid);
                        port = _dbClient.queryObject(StoragePort.class, uriList.get(0));
                        port.setPortSpeed(portSpeed);
                        port.setPortName(targetPort.getName());
                        port.setLabel(nativeGuid);
                        port.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.toString());
                        port.setOperationalStatus(getOperationalStatus(targetPort).toString());
                        // Prior to release-2.4, we only had one default StorageHADomain for XIO array.
                        // During re-discovery when new StorageHADomains are created, update that info on storage ports.
                        port.setPortGroup(haDomain.getAdapterName());
                        port.setStorageHADomain(haDomain.getId());
                        port.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                        portMap.get(EXISTING).add(port);
                        _dbClient.persistObject(port);
                    }
                }
            }
        } catch (Exception e) {
            _logger.error("Discovering XtremIO target ports failed", e);
        }
        return portMap;
    }

    private void discoverInitiators(XtremIOClient restClient, StorageSystem system) {
        try {
            String clusterName = restClient.getClusterDetails(system.getSerialNumber()).getName();

            List<XtremIOInitiator> initiators = restClient.getXtremIOInitiatorsInfo(clusterName);
            for (XtremIOInitiator initiator : initiators) {
                @SuppressWarnings("deprecation")
                List<URI> initiatorUris = _dbClient
                        .queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(initiator.getPortAddress()));
                if (initiatorUris.isEmpty()) {
                    continue;
                } else {
                    Initiator initiatorObj = _dbClient.queryObject(Initiator.class, initiatorUris.get(0));
                    initiatorObj.setLabel(initiator.getName());
                    _dbClient.persistObject(initiatorObj);
                }
            }
        } catch (Exception e) {
            _logger.error("Discovering XtremIO Initiator ports failed", e);
        }
    }

    private OperationalStatus getOperationalStatus(XtremIOPort targetPort) {

        if (UP.equalsIgnoreCase(targetPort.getOperationalStatus())) {
            return OperationalStatus.OK;
        } else {
            return OperationalStatus.NOT_OK;
        }
    }
}
