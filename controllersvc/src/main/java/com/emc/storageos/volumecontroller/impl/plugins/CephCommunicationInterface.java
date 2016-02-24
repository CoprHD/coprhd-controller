package com.emc.storageos.volumecontroller.impl.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.ceph.CephClient;
import com.emc.storageos.ceph.CephClientFactory;
import com.emc.storageos.ceph.CephException;
import com.emc.storageos.ceph.model.ClusterInfo;
import com.emc.storageos.ceph.model.PoolInfo;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StorageHADomain.HADomainType;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol.Block;
import com.emc.storageos.db.client.model.StorageProtocol.Transport;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.StoragePool.CopyTypes;
import com.emc.storageos.db.client.model.StoragePool.PoolOperationalStatus;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StoragePool.SupportedResourceTypes;
import com.emc.storageos.db.client.model.StoragePort.OperationalStatus;
import com.emc.storageos.db.client.model.StoragePort.PortType;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.ceph.CephUtils;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;

public class CephCommunicationInterface extends ExtendedCommunicationInterfaceImpl {
    private static final Logger _log = LoggerFactory.getLogger(CephCommunicationInterface.class);
    private static final Set<String> RBD_ONLY = Collections.singleton(Protocol.RBD.name());
    private static final StringSet COPY_TYPES = new StringSet();
    private static final String PORT_NAME = "Ceph Port";
    private static final String PORT_GROUP = "Ceph Port Group";

    static {
    	 // UNSYNC_ASSOC -> snapshot, UNSYNC_UNASSOC -> clone
        COPY_TYPES.add(CopyTypes.UNSYNC_ASSOC.name());
        COPY_TYPES.add(CopyTypes.UNSYNC_UNASSOC.name());
    }

    private CephClientFactory _cephClientFactory;

    public void setCephClientFactory(CephClientFactory cephClientFactory) {
        _cephClientFactory = cephClientFactory;
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile) throws BaseCollectionException {
        _log.info("Starting collecting statistics of Ceph StorageProvider. IP={}", accessProfile.getIpAddress());
    }

    @Override
    public void scan(AccessProfile accessProfile) throws BaseCollectionException {
        _log.info("Starting scan of Ceph StorageProvider. IP={}", accessProfile.getIpAddress());
        StorageProvider provider = _dbClient.queryObject(StorageProvider.class, accessProfile.getSystemId());
        StorageProvider.ConnectionStatus status = StorageProvider.ConnectionStatus.NOTCONNECTED;
        Map<String, StorageSystemViewObject> storageSystemsCache = accessProfile.getCache();
        String cephType = StorageSystem.Type.ceph.name();
        try {
            CephClient cephClient = CephUtils.connectToCeph(_cephClientFactory, provider);
            ClusterInfo clusterInfo = cephClient.getClusterInfo();
            String systemNativeGUID = NativeGUIDGenerator.generateNativeGuid(cephType, clusterInfo.getFsid());
            StorageSystemViewObject viewObject = storageSystemsCache.get(systemNativeGUID);
            if (viewObject == null) {
                viewObject = new StorageSystemViewObject();
            }
            viewObject.setDeviceType(cephType);
            viewObject.addprovider(accessProfile.getSystemId().toString());
            viewObject.setProperty(StorageSystemViewObject.SERIAL_NUMBER, clusterInfo.getFsid());
            viewObject.setProperty(StorageSystemViewObject.STORAGE_NAME, systemNativeGUID);
            viewObject.setProperty(StorageSystemViewObject.MODEL, "Ceph Storage Cluster");
            storageSystemsCache.put(systemNativeGUID, viewObject);
            status = StorageProvider.ConnectionStatus.CONNECTED;
        } catch (Exception e) {
            _log.error(String.format("Exception was encountered when attempting to scan Ceph Instance %s",
                    accessProfile.getIpAddress()), e);
            throw CephException.exceptions.operationException(e);
        } finally {
            provider.setConnectionStatus(status.name());
            _dbClient.updateObject(provider);
        }
    }

    @Override
    public void discover(AccessProfile accessProfile) throws BaseCollectionException {
        _log.info("Starting discovery of Ceph StorageProvider. IP={}", accessProfile.getIpAddress());
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, accessProfile.getSystemId());
        List<StoragePool> newPools = new ArrayList<StoragePool>();
        List<StoragePool> updatePools = new ArrayList<StoragePool>();
        List<StoragePool> allPools = new ArrayList<StoragePool>();
        String statusMsg = null;
        try {
            CephClient cephClient = CephUtils.connectToCeph(_cephClientFactory, system);
            system.setReachableStatus(true);
            system.setSharedStorageCapacity(true);
            ClusterInfo clusterInfo = cephClient.getClusterInfo();
            List<PoolInfo> pools = cephClient.getPools();
            for (PoolInfo pool: pools) {
                String poolNativeGUID = NativeGUIDGenerator.generateNativeGuid(
                        system, Long.toString(pool.getId()), NativeGUIDGenerator.POOL);
                List<StoragePool> storagePools = CustomQueryUtility.queryActiveResourcesByAltId(
                        _dbClient, StoragePool.class, "nativeGuid", poolNativeGUID);
                StoragePool storagePool = null;
                if (storagePools.isEmpty()) {
                    storagePool = new StoragePool();
                    storagePool.setId(URIUtil.createId(StoragePool.class));
                    storagePool.setNativeId(Long.toString(pool.getId()));
                    storagePool.setNativeGuid(poolNativeGUID);
                    storagePool.setPoolName(pool.getName());
                    storagePool.setLabel(pool.getName());

                    storagePool.setStorageDevice(system.getId());
                    storagePool.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
                    storagePool.setPoolServiceType(PoolServiceType.block.toString());
                    storagePool.setSupportedResourceTypes(SupportedResourceTypes.THIN_ONLY.name());
                    storagePool.setThinVolumePreAllocationSupported(false);
                    storagePool.addProtocols(RBD_ONLY);
                    storagePool.setSupportedCopyTypes(COPY_TYPES);

                    storagePool.setOperationalStatus(PoolOperationalStatus.READY.name());
                    storagePool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                    storagePool.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
                    storagePool.setMinimumThinVolumeSize(1024L); // ???
                    storagePool.setMaximumThinVolumeSize(10737418240L); // ???
                    storagePool.setInactive(false);
                    newPools.add(storagePool);
                } else if (storagePools.size() == 1) {
                    storagePool = storagePools.get(0);
                    updatePools.add(storagePool);
                } else {
                    _log.warn(String.format("There are %d StoragePools with nativeGuid = %s", storagePools.size(),
                            poolNativeGUID));
                    continue;
                }
                storagePool.setFreeCapacity(clusterInfo.getKbAvail());
                storagePool.setTotalCapacity(clusterInfo.getKb());
            }
            StoragePoolAssociationHelper.setStoragePoolVarrays(system.getId(), newPools, _dbClient);
            allPools.addAll(newPools);
            allPools.addAll(updatePools);
            DiscoveryUtils.checkStoragePoolsNotVisible(allPools, _dbClient, system.getId());
            _dbClient.createObject(newPools);
            _dbClient.updateObject(updatePools);

            String adapterNativeGUID = NativeGUIDGenerator.generateNativeGuid(
                    system, "-", NativeGUIDGenerator.ADAPTER); // ???
            List<StorageHADomain> storageAdapters = CustomQueryUtility.queryActiveResourcesByAltId
                    (_dbClient, StorageHADomain.class, "nativeGuid", adapterNativeGUID);
            StorageHADomain storageAdapter = null;
            if (storageAdapters.isEmpty()) {
                storageAdapter = new StorageHADomain();
                storageAdapter.setId(URIUtil.createId(StorageHADomain.class));
                storageAdapter.setStorageDeviceURI(system.getId());
                storageAdapter.setNativeGuid(adapterNativeGUID);
                String monitorHost = accessProfile.getIpAddress();
                storageAdapter.setAdapterName(monitorHost);
                storageAdapter.setName(monitorHost);
                storageAdapter.setLabel(monitorHost);

                storageAdapter.setNumberofPorts("1");
                storageAdapter.setAdapterType(HADomainType.FRONTEND.name());
                storageAdapter.setProtocol(Block.RBD.name());
                storageAdapter.setInactive(false);
                _dbClient.createObject(storageAdapter);
            } else {
                storageAdapter = storageAdapters.get(0);
                if (storageAdapters.size() != 1) {
                    _log.warn(String.format("There are %d StorageHADomains with nativeGuid = %s", storageAdapters.size(),
                            adapterNativeGUID));
                }
            }

            String portNativeGUID = NativeGUIDGenerator.generateNativeGuid(system, "-", NativeGUIDGenerator.PORT); // ???
            List<StoragePort> storagePorts = CustomQueryUtility.queryActiveResourcesByAltId(
                    _dbClient, StoragePort.class, "nativeGuid", portNativeGUID);
            StoragePort storagePort = null;
            if (storagePorts.isEmpty()) {
                storagePort = new StoragePort();
                storagePort.setId(URIUtil.createId(StoragePort.class));
                storagePort.setNativeGuid(portNativeGUID);
                storagePort.setPortNetworkId(portNativeGUID);
                storagePort.setPortName(PORT_NAME);
                storagePort.setPortGroup(PORT_GROUP);

                storagePort.setStorageDevice(system.getId());
                storagePort.setStorageHADomain(storageAdapter.getId());
                storagePort.setPortType(PortType.frontend.name());
                storagePort.setTransportType(Transport.IP.name());

                storagePort.setOperationalStatus(OperationalStatus.OK.name());
                storagePort.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
                storagePort.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                storagePort.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
                storagePort.setInactive(false);

                _dbClient.createObject(storagePort);
            } else {
                storagePort = storagePorts.get(0);
                if (storagePorts.size() != 1) {
                    _log.warn(String.format("There are %d StoragePorts with nativeGuid = %s", storagePorts.size(),
                            portNativeGUID));
                }
            }

            StoragePortAssociationHelper.runUpdatePortAssociationsProcess(
                    Collections.singletonList(storagePort), null, _dbClient, _coordinator, allPools);

            statusMsg = String.format("Discovery completed successfully for Storage System: %s",
                    system.getNativeGuid());
        } catch (Exception e) {
            system.setReachableStatus(false);
            _log.error(String.format("Exception was encountered when attempting to discover Ceph Instance %s",
                    accessProfile.getIpAddress()), e);
            statusMsg = String.format("Discovery failed because %s", e.getLocalizedMessage());
            throw CephException.exceptions.operationException(e);
        } finally {
            if (system != null) {
                system.setLastDiscoveryStatusMessage(statusMsg);
                _dbClient.updateObject(system);
            }
        }
    }
}
