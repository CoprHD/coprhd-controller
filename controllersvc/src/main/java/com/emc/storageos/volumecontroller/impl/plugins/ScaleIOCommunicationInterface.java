/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.plugins;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.scaleio.ScaleIOException;
import com.emc.storageos.scaleio.api.ScaleIOAttributes;
import com.emc.storageos.scaleio.api.ScaleIOCLI;
import com.emc.storageos.scaleio.api.ScaleIOQueryAllCommand;
import com.emc.storageos.scaleio.api.ScaleIOQueryAllResult;
import com.emc.storageos.scaleio.api.ScaleIOQueryAllSCSIInitiatorsResult;
import com.emc.storageos.scaleio.api.ScaleIOQueryAllSDCResult;
import com.emc.storageos.scaleio.api.ScaleIOQueryAllSDSResult;
import com.emc.storageos.scaleio.api.ScaleIOQueryClusterResult;
import com.emc.storageos.scaleio.api.ScaleIOQueryStoragePoolResult;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.OperationalStatus;
import com.emc.storageos.volumecontroller.impl.scaleio.ScaleIOCLIFactory;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByAltId;
import static com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator.generateNativeGuid;

public class ScaleIOCommunicationInterface extends ExtendedCommunicationInterfaceImpl {
    private static final Logger log = LoggerFactory.getLogger(ScaleIOCommunicationInterface.class);
    private static final int LOCK_WAIT_SECONDS = 300;
    private static final Set<String> SCALEIO_ONLY = Collections.singleton(HostInterface.Protocol.ScaleIO.name());
    private static final Set<String> SCALEIO_AND_ISCSI = new HashSet<>();

    private ScaleIOCLIFactory scaleIOCLIFactory;

    static {
        SCALEIO_AND_ISCSI.add(HostInterface.Protocol.ScaleIO.name());
        SCALEIO_AND_ISCSI.add(HostInterface.Protocol.iSCSI.name());
    }

    public void setScaleIOCLIFactory(ScaleIOCLIFactory scaleIOCLIFactory) {
        this.scaleIOCLIFactory = scaleIOCLIFactory;
        this.scaleIOCLIFactory.setDbClient(_dbClient);
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile) throws BaseCollectionException {

    }

    @Override
    public void scan(AccessProfile accessProfile) throws BaseCollectionException {
        log.info("Starting scan of ScaleIO StorageProvider. IP={}", accessProfile.getIpAddress());
        StorageProvider.ConnectionStatus cxnStatus = StorageProvider.ConnectionStatus.CONNECTED;
        StorageProvider provider = _dbClient.queryObject(StorageProvider.class, accessProfile.getSystemId());
        _locker.acquireLock(accessProfile.getIpAddress(), LOCK_WAIT_SECONDS);
        try {
            ScaleIOCLI scaleIOCLI = scaleIOCLIFactory.using(_dbClient).getCLI(provider);
            if (scaleIOCLI != null) {
                Map<String, StorageSystemViewObject> storageSystemsCache = accessProfile.getCache();

                ScaleIOQueryClusterResult clusterResult = scaleIOCLI.queryClusterCommand();
                StringSet secondaryIps = new StringSet();
                secondaryIps.add(clusterResult.getSecondaryIP());
                provider.setSecondaryIps(secondaryIps);

                ScaleIOQueryAllResult queryAllResult = scaleIOCLI.queryAll();
                String scaleIOType = StorageSystem.Type.scaleio.name();
                String installationId = queryAllResult.getProperty(ScaleIOQueryAllCommand.SCALEIO_INSTALLATION_ID);
                String version = queryAllResult.getProperty(ScaleIOQueryAllCommand.SCALEIO_VERSION).replaceAll("_", ".");
                String minimumSupported = VersionChecker.getMinimumSupportedVersion(StorageSystem.Type.scaleio);
                String compatibility = (VersionChecker.verifyVersionDetails(minimumSupported, version) < 0) ?
                        StorageSystem.CompatibilityStatus.INCOMPATIBLE.name() :
                        StorageSystem.CompatibilityStatus.COMPATIBLE.name();
                provider.setCompatibilityStatus(compatibility);
                provider.setVersionString(version);
                for (String protectionDomain : queryAllResult.getProtectionDomainNames()) {
                    log.info("For ScaleIO instance {}, found ProtectionDomain {}", installationId, protectionDomain);
                    String id = String.format("%s+%s", installationId, protectionDomain);
                    String nativeGuid = generateNativeGuid(scaleIOType, id);
                    StorageSystemViewObject viewObject = storageSystemsCache.get(nativeGuid);
                    if (viewObject == null) {
                        viewObject = new StorageSystemViewObject();
                    }
                    viewObject.setDeviceType(scaleIOType);
                    viewObject.addprovider(accessProfile.getSystemId().toString());
                    viewObject.setProperty(StorageSystemViewObject.MODEL,"ScaleIO ECS");
                    viewObject.setProperty(StorageSystemViewObject.SERIAL_NUMBER,id);
                    storageSystemsCache.put(nativeGuid, viewObject);
                }
            }
        } catch (Exception e) {
            cxnStatus = StorageProvider.ConnectionStatus.NOTCONNECTED;
            log.error(String.format("Exception was encountered when attempting to scan ScaleIO Instance %s",
                    accessProfile.getIpAddress()), e);
            throw ScaleIOException.exceptions.scanFailed(e);
        } finally {
            provider.setConnectionStatus(cxnStatus.name());
            _dbClient.persistObject(provider);
            log.info("Completed scan of ScaleIO StorageProvider. IP={}", accessProfile.getIpAddress());
            _locker.releaseLock(accessProfile.getIpAddress());
        }
    }

    @Override
    public void discover(AccessProfile accessProfile) throws BaseCollectionException {
        StorageSystem.CompatibilityStatus compatibilityStatus = StorageSystem.CompatibilityStatus.COMPATIBLE;
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, accessProfile.getSystemId());
        _locker.acquireLock(accessProfile.getIpAddress(), LOCK_WAIT_SECONDS);
        log.info("Starting discovery of ScaleIO StorageProvider. IP={} StorageSystem {}",
                accessProfile.getIpAddress(), storageSystem.getNativeGuid());
        try {
            ScaleIOCLI scaleIOCLI = scaleIOCLIFactory.using(_dbClient).getCLI(storageSystem);
            if (scaleIOCLI != null) {
                ScaleIOQueryAllResult queryAllResult = scaleIOCLI.queryAll();
                ScaleIOQueryAllSDCResult queryAllSDCResult = scaleIOCLI.queryAllSDC();
                ScaleIOQueryAllSDSResult queryAllSDSResult = scaleIOCLI.queryAllSDS();
                ScaleIOQueryAllSCSIInitiatorsResult queryAllSCSIInitiatorsResult = scaleIOCLI.queryAllSCSIInitiators();

                List<StoragePort> ports = new ArrayList<>();
                List<StoragePool> newPools = new ArrayList<StoragePool>();
                List<StoragePool> updatePools = new ArrayList<StoragePool>();
                List<StoragePool> allPools = new ArrayList<StoragePool>();
                String scaleIOType = StorageSystem.Type.scaleio.name();
                String installationId = queryAllResult.getProperty(ScaleIOQueryAllCommand.SCALEIO_INSTALLATION_ID);
                String version = queryAllResult.getProperty(ScaleIOQueryAllCommand.SCALEIO_VERSION).replaceAll("_", ".");
                String minimumSupported = VersionChecker.getMinimumSupportedVersion(StorageSystem.Type.scaleio);
                String compatibility = (VersionChecker.verifyVersionDetails(minimumSupported, version) < 0) ?
                        StorageSystem.CompatibilityStatus.INCOMPATIBLE.name() :
                        StorageSystem.CompatibilityStatus.COMPATIBLE.name();
                storageSystem.setFirmwareVersion(version);
                storageSystem.setCompatibilityStatus(compatibility);
                storageSystem.setReachableStatus(true);
                storageSystem.setLabel(storageSystem.getNativeGuid());

                boolean isSIO1_3x = version.matches("1\\.3[\\.\\d+]+");

                for (String protectionDomain : queryAllResult.getProtectionDomainNames()) {
                    String id = String.format("%s+%s", installationId, protectionDomain);
                    String storageSystemNativeGUID = generateNativeGuid(scaleIOType, id);
                    if (!storageSystemNativeGUID.equals(storageSystem.getNativeGuid())) {
                        // This is not the ProtectionDomain that we're looking for
                        continue;
                    }
                    storageSystem.setSerialNumber(protectionDomain);
                    Network network = createNetwork(installationId);
                    List<StoragePort> thesePorts =
                            createStoragePorts(storageSystem, compatibility, network, queryAllSDSResult, protectionDomain);
                    ports.addAll(thesePorts);
                    createHost(network, queryAllSDCResult);
                    boolean hasSCSIInitiators =
                            createSCSIInitiatorsAndStoragePorts(storageSystem, protectionDomain, compatibilityStatus,
                                    installationId, queryAllSCSIInitiatorsResult, queryAllSDCResult, ports);
                    List<StoragePort> notVisiblePorts = DiscoveryUtils.checkStoragePortsNotVisible(ports, _dbClient,
                            storageSystem.getId());
                    if (notVisiblePorts != null && !notVisiblePorts.isEmpty()) {
                        ports.addAll(notVisiblePorts);
                    }
                    Set<String> supportedProtocols = (hasSCSIInitiators) ? SCALEIO_AND_ISCSI : SCALEIO_ONLY;
                    for (String storagePool : queryAllResult.getStoragePoolsForProtectionDomain(protectionDomain)) {
                        ScaleIOQueryStoragePoolResult storagePoolResult =
                                scaleIOCLI.queryStoragePool(protectionDomain, storagePool);
                        String nativeGuid = String.format("%s-%s-%s", installationId, protectionDomain, storagePool);
                        log.info("Attempting to discover pool {} for ProtectionDomain {}", storagePool, protectionDomain);
                        List<StoragePool> pools =
                                queryActiveResourcesByAltId(_dbClient, StoragePool.class, "nativeGuid", nativeGuid);
                        StoragePool pool = null;
                        if (pools.size() == 0) {
                            log.info("Pool {} is new", storagePool);
                            pool = new StoragePool();
                            pool.setId(URIUtil.createId(StoragePool.class));
                            pool.setPoolName(storagePool);
                            pool.setNativeId(String.format("%s-%s-%s", installationId, protectionDomain, storagePool));
                            pool.setNativeGuid(nativeGuid);
                            pool.setStorageDevice(accessProfile.getSystemId());
                            pool.setPoolServiceType(StoragePool.PoolServiceType.block.toString());
                            pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY.name());
                            pool.setCompatibilityStatus(compatibility);
                            pool.setThinVolumePreAllocationSupported(false);
                            pool.addDriveTypes(Collections.singleton(StoragePool.SupportedDriveTypeValues.SATA.name()));
                            StringSet copyTypes = new StringSet();
                            copyTypes.add(StoragePool.CopyTypes.ASYNC.name());
                            copyTypes.add(StoragePool.CopyTypes.UNSYNC_UNASSOC.name());
                            pool.setSupportedCopyTypes(copyTypes);
                            pool.setMaximumThickVolumeSize(1048576L);
                            pool.setMinimumThickVolumeSize(1L);
                            newPools.add(pool);
                        } else if (pools.size() == 1) {
                            log.info("Pool {} was previously discovered", storagePool);
                            pool = pools.get(0);
                            updatePools.add(pool);
                        } else {
                            log.warn(String.format("There are %d StoragePools with nativeGuid = %s", pools.size(),
                                    nativeGuid));
                            continue;
                        }
                        String freeCapacityString = storagePoolResult.getAvailableCapacity();
                        Long freeCapacityKBytes = ControllerUtils.convertBytesToKBytes(freeCapacityString);
                        pool.setFreeCapacity(freeCapacityKBytes);
                        String totalCapacityString = storagePoolResult.getTotalCapacity();
                        Long totalCapacityKBytes = ControllerUtils.convertBytesToKBytes(totalCapacityString);
                        pool.setTotalCapacity(totalCapacityKBytes);
                        pool.addProtocols(supportedProtocols);
                        // In case there is an upgrade from SIO 1.2x to SIO 1.30, this will update
                        // the pool values to the appropriate values
                        String supportedResourceType = (isSIO1_3x) ?
                                StoragePool.SupportedResourceTypes.THIN_AND_THICK.name() :
                                StoragePool.SupportedResourceTypes.THICK_ONLY.name();
                        pool.setSupportedResourceTypes(supportedResourceType);
                        Long maxThinSize = (isSIO1_3x) ? 1048576L : 0L;
                        Long minThinSize = (isSIO1_3x) ? 1L : 0L;
                        pool.setMaximumThinVolumeSize(maxThinSize);
                        pool.setMinimumThinVolumeSize(minThinSize);
                        pool.setInactive(false);
                        pool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                        
                    }
                }
                log.info(String.format("For StorageSystem %s, discovered %d new pools and %d pools to update",
                        storageSystem.getNativeGuid(), newPools.size(), updatePools.size()));
                StoragePoolAssociationHelper.setStoragePoolVarrays(storageSystem.getId(), newPools, _dbClient);
                allPools.addAll(newPools);
                allPools.addAll(updatePools);
                _dbClient.createObject(newPools);
                _dbClient.updateAndReindexObject(updatePools);
                List<StoragePool> notVisiblePools = DiscoveryUtils.checkStoragePoolsNotVisible(allPools, _dbClient, 
                        storageSystem.getId());
                if (notVisiblePools != null && !notVisiblePools.isEmpty()){
                    allPools.addAll(notVisiblePools);
                }
                StoragePortAssociationHelper.runUpdatePortAssociationsProcess(ports, null, _dbClient, _coordinator, allPools);
            }
        } catch (Exception e) {
            storageSystem.setReachableStatus(false);
            log.error(String.format("Exception was encountered when attempting to discover ScaleIO Instance %s",
                    accessProfile.getIpAddress()), e);
        } finally {
            _locker.releaseLock(accessProfile.getIpAddress());
        }
        _dbClient.updateAndReindexObject(storageSystem);
        log.info("Completed of ScaleIO StorageProvider. IP={} StorageSystem {}",
                accessProfile.getIpAddress(), storageSystem.getNativeGuid());
    }

    private void addPoolsToVirtualPool(VirtualArray virtualArray, List<StoragePool> newPools) {
        Set<String> newPoolIdStrs = new HashSet<String>();
        for (StoragePool pool : newPools) {
            newPoolIdStrs.add(pool.getId().toString());
        }
        String exportedVirtualPoolLabel = String.format("%s-VirtualPool", virtualArray.getLabel());
        List<VirtualPool> results =
                CustomQueryUtility.
                        queryActiveResourcesByConstraint(_dbClient, VirtualPool.class,
                                PrefixConstraint.Factory.
                                        getFullMatchConstraint(VirtualPool.class, "label", exportedVirtualPoolLabel)
                        );
        VirtualPool vpool;
        if (results == null || results.isEmpty()) {
            vpool = new VirtualPool();
            vpool.setId(URIUtil.createId(VirtualPool.class));
            vpool.setLabel(exportedVirtualPoolLabel);
            vpool.addVirtualArrays(Collections.singleton(virtualArray.getId().toString()));
            vpool.addAssignedStoragePools(newPoolIdStrs);
            vpool.setType(VirtualPool.Type.block.name());
            vpool.setDescription(String.format("VirtualPool for ScaleIO VirtualArray %s", virtualArray.getLabel()));
            vpool.setUseMatchedPools(true);
            vpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
            vpool.addProtocols(Collections.singleton(HostInterface.Protocol.ScaleIO.name()));
            StringSetMap arrayInfo = new StringSetMap();
            arrayInfo.put(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE, VirtualPool.SystemType.scaleio.name());
            vpool.addArrayInfoDetails(arrayInfo);
            _dbClient.createObject(vpool);
        } else {
            vpool = results.get(0);
            vpool.addAssignedStoragePools(newPoolIdStrs);
            _dbClient.updateAndReindexObject(vpool);
        }
    }

    /**
     * Create a Host object for every SDC that is found on the system. Create a single
     * initiator for the host in the specified Network.
     *
     * @param network           [in] Network object to associated the hosts' initiator ports
     * @param queryAllSDCResult [in] - SDC query result
     */
    private void createHost(Network network, ScaleIOQueryAllSDCResult queryAllSDCResult) {
        // Find the root tenant and associate any SDC hosts with it
        List<URI> tenantOrgList = _dbClient.queryByType(TenantOrg.class, true);
        Iterator<TenantOrg> it = _dbClient.queryIterativeObjects(TenantOrg.class, tenantOrgList);
        List<String> initiatorsToAddToNetwork = new ArrayList<>();
        URI rootTenant = null;
        while (it.hasNext()) {
            TenantOrg tenantOrg = it.next();
            if (TenantOrg.isRootTenant(tenantOrg)) {
                rootTenant = tenantOrg.getId();
                break;
            }
        }
        for (String id : queryAllSDCResult.getSDCIds()) {
            ScaleIOAttributes attributes = queryAllSDCResult.getClientInfoById(id);
            String ip = attributes.get(ScaleIOQueryAllSDCResult.SDC_IP);
            String guid = attributes.get(ScaleIOQueryAllSDCResult.SDC_GUID);

            // First we search by nativeGuid
            Host host = findByNativeGuid(guid);

            if (host == null) {
                // Host with nativeGuid is not known to ViPR, try and find by IP address
                host = findOrCreateByIp(rootTenant, ip, guid);
            }

            // Create an single initiator for this SDC. If the initiator has already been
            // created, the existing Initiator will be returned. Associate the initiator
            // with the network
            Initiator initiator = createInitiator(host, ip, id);
            if (!network.hasEndpoint(initiator.getInitiatorPort())) {
                initiatorsToAddToNetwork.add(initiator.getInitiatorPort());
            }
        }

        if (!initiatorsToAddToNetwork.isEmpty()) {
            network.addEndpoints(initiatorsToAddToNetwork, true);
            _dbClient.updateAndReindexObject(network);
        }
    }

    private Host findByNativeGuid(String guid) {
        List<Host> resultsByGuid =
                CustomQueryUtility.
                        queryActiveResourcesByAltId(_dbClient, Host.class, "nativeGuid", guid);

        if (resultsByGuid == null || resultsByGuid.isEmpty()) {
            return null;
        }
        return resultsByGuid.get(0);
    }

    private Host findOrCreateByIp(URI rootTenant, String ip, String guid) {
        List<IpInterface> resultsByIp =
                CustomQueryUtility.
                        queryActiveResourcesByAltId(_dbClient, IpInterface.class, "ipAddress", ip);
        Host host = null;

        if (resultsByIp == null || resultsByIp.isEmpty()) {
            host = findHostByHostName(ip);
            if (host == null) {
                log.info(String.format("Could not find any existing Host with IpInterface or Hostname %s. " +
                                "Creating new Host for SDC %s", ip, guid));
                // Host with IP address not found, need to create it
                host = new Host();
                host.setId(URIUtil.createId(Host.class));
                host.setHostName(ip);
                host.setTenant(rootTenant);
                host.setType(DiscoveredDataObject.Type.host.name());
                host.setLabel(ip);
                host.setNativeGuid(guid);
                host.setType(Host.HostType.Other.name());
                host.setDiscoverable(false);
                host.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE.name());
                host.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
                host.setSuccessDiscoveryTime(System.currentTimeMillis());
                host.setInactive(false);
                _dbClient.createObject(host);
            }
        } else {
            // Host with IP address was found, need to update it with nativeGuid unless it's already set.
            IpInterface ipInterface = resultsByIp.get(0);
            host = _dbClient.queryObject(Host.class, ipInterface.getHost());

            if (host != null && Strings.isNullOrEmpty(host.getNativeGuid())) {
                log.info(String.format("Existing Host %s (%s) found and will be used as container for SDC %s",
                        ip, host.getNativeGuid(), guid));
                host.setNativeGuid(guid);
                _dbClient.updateAndReindexObject(host);
            } else {
                log.warn(String.format("Found an IpInterface %s, but its associated Host %s could not be found.",
                        ip, ipInterface.getHost()));
            }
        }

        return host;
    }

    private Host findHostByHostName(String ip) {
        Host host = null;
        List<Host> results =
                    CustomQueryUtility.
                            queryActiveResourcesByAltId(_dbClient, Host.class, "hostName", ip);
        if (results != null && !results.isEmpty()) {
            Collection<URI> hostIdStrings = Collections2.transform(results,
                    CommonTransformerFunctions.fctnDataObjectToID());
            log.info(String.format("Found %d hosts with ip %s as their hostName: %s",
                    results.size(), ip, CommonTransformerFunctions.collectionToString(hostIdStrings)));
            host = results.get(0);
        }
        return host;
    }

    /**
     * Create initiator for the specified host
     *
     * @param host [in] - Host object reference
     * @param ip   [in] - IP address string
     * @param id   [id] - Indentifier for the port
     * @return Initiator object
     */
    private Initiator createInitiator(Host host, String ip, String id) {
        Initiator initiator;
        List<Initiator> results =
                CustomQueryUtility.queryActiveResourcesByAltId(_dbClient, Initiator.class, "iniport", id);
        if (results == null || results.isEmpty()) {
            initiator = new Initiator();
            initiator.setId(URIUtil.createId(Initiator.class));
            initiator.setHost(host.getId());
            initiator.setHostName(ip);
            initiator.setInitiatorPort(id);
            initiator.setProtocol(HostInterface.Protocol.ScaleIO.name());
            initiator.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.name());
            initiator.setInactive(false);
            _dbClient.createObject(initiator);
        } else {
            initiator = results.get(0);
        }
        return initiator;
    }

    /**
     * This will create an IP network for the SIO system to contain the iSCSI initiators
     * associated with the system (if any). It will also create the iSCSI initiators and
     * StoragePorts, placing them in the IP network.
     *
     * @param storageSystem                [in] - StorageSystem object (ProtectionDomain)
     * @param protectionDomainName         [in] - Protection Domain name
     * @param compatibilityStatus          [in] - Compatibility status to use on the ports
     * @param installationId               [in] - Installation ID unique to ScaleIO instance
     * @param queryAllSCSIInitiatorsResult [in] - Result of querying ScaleIO for SCSI initiators
     * @param queryAllSDCResult            [in] - Result of querying ScaleIO for SDC clients
     * @param ports                        [out] - List to update with iSCSI StoragePorts that were discovered
     * @return - true if there were SCSI initiators found on the system.
     * @throws IOException
     */
    private boolean createSCSIInitiatorsAndStoragePorts(StorageSystem storageSystem, String protectionDomainName,
                                                        DiscoveredDataObject.CompatibilityStatus compatibilityStatus,
                                                        String installationId,
                                                        ScaleIOQueryAllSCSIInitiatorsResult queryAllSCSIInitiatorsResult,
                                                        ScaleIOQueryAllSDCResult queryAllSDCResult, List<StoragePort> ports) throws IOException {
        boolean hasSCSIInitiators = false;
        if (queryAllSCSIInitiatorsResult != null && !queryAllSCSIInitiatorsResult.getAllInitiatorIds().isEmpty()) {
            List<String> initiatorsToAddToNetwork = new ArrayList<>();
            String networkId = String.format("%s-IP", installationId);
            Network networkForSCSIInitiators = createIPNetworkForSCSIInitiators(networkId);

            for (String iqn : queryAllSCSIInitiatorsResult.getAllInitiatorIds()) {
                Initiator initiator = createSCSIInitiator(iqn);
                if (!networkForSCSIInitiators.hasEndpoint(initiator.getInitiatorPort())) {
                    initiatorsToAddToNetwork.add(initiator.getInitiatorPort());
                }
                hasSCSIInitiators = true;
            }

            if (!initiatorsToAddToNetwork.isEmpty()) {
                networkForSCSIInitiators.addEndpoints(initiatorsToAddToNetwork, true);
                _dbClient.updateAndReindexObject(networkForSCSIInitiators);
            }

            List<StoragePort> iSCSIPorts =
            createSCSIStoragePorts(storageSystem, protectionDomainName, compatibilityStatus,
                    networkForSCSIInitiators, queryAllSDCResult);
            ports.addAll(iSCSIPorts);
        }
        return hasSCSIInitiators;
    }

    /**
     * Create an IP Network object for iSCSI initiators.
     *
     * @param uniqueId [in] - Unique string identifier for the network
     * @return Network object
     */
    private Network createIPNetworkForSCSIInitiators(String uniqueId) {
        Network network;
        List<Network> results =
                CustomQueryUtility.queryActiveResourcesByAltId(_dbClient, Network.class, "nativeId", uniqueId);
        if (results == null || results.isEmpty()) {
            network = new Network();
            network.setId(URIUtil.createId(Network.class));
            network.setTransportType(StorageProtocol.Transport.IP.name());
            network.setNativeId(uniqueId);
            network.setLabel(String.format("%s-ScaleIONetwork", uniqueId));
            network.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.name());
            network.setInactive(false);
            _dbClient.createObject(network);
        } else {
            network = results.get(0);
        }
        return network;
    }

    /**
     * Create iSCSI initiator for the specified host
     *
     * @param iqn [id] - iSCSI IQN for the port
     * @return Initiator object
     */
    private Initiator createSCSIInitiator(String iqn) {
        Initiator initiator;
        List<Initiator> results =
                CustomQueryUtility.queryActiveResourcesByAltId(_dbClient, Initiator.class, "iniport", iqn);
        if (results == null || results.isEmpty()) {
            initiator = new Initiator();
            initiator.setId(URIUtil.createId(Initiator.class));
            initiator.setInitiatorPort(iqn);
            initiator.setProtocol(HostInterface.Protocol.iSCSI.name());
            initiator.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.name());
            initiator.setInactive(false);
            _dbClient.createObject(initiator);
        } else {
            initiator = results.get(0);
        }
        return initiator;
    }

    /**
     * Create a Network object for the ScaleIO instance and associate it with the VArray
     *
     * @param uniqueId [in] - Unique string identifier for the network
     * @return Network object
     */
    private Network createNetwork(String uniqueId) {
        Network network;
        List<Network> results =
                CustomQueryUtility.queryActiveResourcesByAltId(_dbClient, Network.class, "nativeId", uniqueId);
        if (results == null || results.isEmpty()) {
            network = new Network();
            network.setId(URIUtil.createId(Network.class));
            network.setTransportType(StorageProtocol.Transport.ScaleIO.name());
            network.setNativeId(uniqueId);
            network.setLabel(String.format("%s-ScaleIONetwork", uniqueId));
            network.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.name());
            network.setInactive(false);
            _dbClient.createObject(network);
        } else {
            network = results.get(0);
        }
        return network;
    }

    /**
     * Create a StoragePort for each SDS in the ScaleIO instance. These are psuedo-StoragePorts
     * for the purpose of tying up the host-end to the storage-end of the Network
     *
     * @param storageSystem        [in] - StorageSystem object (ProtectionDomain)
     * @param compatibilityStatus  [in] - Compatibility status to use on the ports
     * @param network              [in] - Network to associate with the ports
     * @param queryAllSDSResult    [in] - SDS query result
     * @param protectionDomainName [in] - Protection Domain name
     */
    private List<StoragePort> createStoragePorts(StorageSystem storageSystem, String compatibilityStatus, Network network,
                                                 ScaleIOQueryAllSDSResult queryAllSDSResult,
                                                 String protectionDomainName) throws IOException {
        List<StoragePort> ports = new ArrayList<>();
        List<String> endpoints = new ArrayList<>();
        String id = queryAllSDSResult.getProtectionDomainId(protectionDomainName);
        for (ScaleIOAttributes attributes : queryAllSDSResult.getSDSForProtectionDomain(id)) {
            String sdsId = attributes.get(ScaleIOQueryAllSDSResult.SDS_ID);
            String sdsIP = attributes.get(ScaleIOQueryAllSDSResult.SDS_IP);
            StoragePort port;
            List<StoragePort> results = CustomQueryUtility.
                    queryActiveResourcesByAltId(_dbClient, StoragePort.class, "portNetworkId", sdsId);
            if (results == null || results.isEmpty()) {
                String nativeGUID =
                        NativeGUIDGenerator.generateNativeGuid(storageSystem,
                                protectionDomainName, NativeGUIDGenerator.ADAPTER);
                StorageHADomain adapter = new StorageHADomain();
                adapter.setStorageDeviceURI(storageSystem.getId());
                adapter.setId(URIUtil.createId(StorageHADomain.class));
                adapter.setAdapterName(protectionDomainName);
                adapter.setLabel(protectionDomainName);
                adapter.setNativeGuid(nativeGUID);
                adapter.setNumberofPorts("1");
                adapter.setAdapterType(StorageHADomain.HADomainType.FRONTEND.name());
                adapter.setInactive(false);
                _dbClient.createObject(adapter);

                port = new StoragePort();
                port.setId(URIUtil.createId(StoragePort.class));
                port.setPortNetworkId(sdsId);
                port.setLabel(String.format("%s-%s-StoragePort", protectionDomainName, sdsId));
                port.setStorageDevice(storageSystem.getId());
                port.setCompatibilityStatus(compatibilityStatus);
                port.setOperationalStatus(OperationalStatus.OK.name());
                port.setIpAddress(sdsIP);
                port.setNetwork(network.getId());
                port.setPortGroup(sdsId);
                port.setPortName(sdsId);
                port.setPortType(StoragePort.PortType.frontend.name());
                port.setStorageHADomain(adapter.getId());
                port.setTransportType(StorageProtocol.Transport.ScaleIO.name());
                port.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                port.setInactive(false);
                _dbClient.createObject(port);
                endpoints.add(port.getPortNetworkId());
            } else {
                port = results.get(0);
            }
            ports.add(port);
        }
        network.addEndpoints(endpoints, true);
        _dbClient.updateAndReindexObject(network);
        return ports;
    }

    /**
     * Create an iSCSI StoragePort for each SDC in the ScaleIO instance. The SDC would present iSCSI
     * targets to iSCSI initiators. These are psuedo-StoragePorts or the purpose of tying up
     * the host-end to the storage-end of the IP Network
     * <p/>
     * Note about StoragePorts created here:
     * The iSCSI target ports are generated and created per StorageSystem, keeping them
     * in line with other arrays. However, ScaleIO itself has a different way of presenting
     * the targets. The targets are actually on the SDC client hosts -- any SDC client in the
     * ScaleIO system that has the SCSI software running on it will be able to present an
     * iSCSI target to an iSCSI initiator. The name of this target will take the form of
     * iqn.2010-12.com.ecs:[SDC GUID]. In order for ViPR to support multiple using multiple
     * StorageSystems (ProtectionDomains) using the same set of SDC, we have to invent targets
     * per ProtectionDomain. This means that we will created a slightly modified IDQ to
     * distinguish between them: iqn.2010-12.com.ecs.[PD name]:[SDC GUID]. Having such an
     * implementation does not affect the volume export because ScaleIO does not require
     * specifying the target.
     *
     * @param storageSystem       [in] - StorageSystem object (ProtectionDomain)
     * @param compatibilityStatus [in] - Compatibility status to use on the ports
     * @param network             [in] - Network to associate with the ports
     * @param queryAllSDCResult   [in] - SDS query result
     */
    private List<StoragePort> createSCSIStoragePorts(StorageSystem storageSystem, String protectionDomainName,
                                                     DiscoveredDataObject.CompatibilityStatus compatibilityStatus,
                                                     Network network, ScaleIOQueryAllSDCResult queryAllSDCResult)
            throws IOException {
        List<StoragePort> ports = new ArrayList<>();
        List<String> endpoints = new ArrayList<>();
        String fixedProtectionDomainName = protectionDomainName.replaceAll("\\s+", "").toLowerCase();
        for (String sdcId : queryAllSDCResult.getSDCIds()) {
            ScaleIOAttributes attributes = queryAllSDCResult.getClientInfoById(sdcId);
            String sdcGUID = attributes.get(ScaleIOQueryAllSDCResult.SDC_GUID);
            String sdcIP = attributes.get(ScaleIOQueryAllSDCResult.SDC_IP);
            String generatedTargetName = String.format("iqn.2010-12.com.ecs.%s:%s", fixedProtectionDomainName,
                    sdcGUID.toLowerCase());
            StoragePort port;
            List<StoragePort> results = CustomQueryUtility.
                    queryActiveResourcesByAltId(_dbClient, StoragePort.class, "portNetworkId", generatedTargetName);
            if (results == null || results.isEmpty()) {
                String nativeGUID =
                        NativeGUIDGenerator.generateNativeGuid(storageSystem,
                                sdcIP, NativeGUIDGenerator.ADAPTER);
                StorageHADomain adapter = new StorageHADomain();
                adapter.setStorageDeviceURI(storageSystem.getId());
                adapter.setId(URIUtil.createId(StorageHADomain.class));
                adapter.setAdapterName(sdcIP);
                adapter.setLabel(sdcIP);
                adapter.setNativeGuid(nativeGUID);
                adapter.setNumberofPorts("1");
                adapter.setAdapterType(StorageHADomain.HADomainType.FRONTEND.name());
                adapter.setInactive(false);
                _dbClient.createObject(adapter);

                port = new StoragePort();
                port.setId(URIUtil.createId(StoragePort.class));
                port.setPortNetworkId(generatedTargetName);
                port.setLabel(generatedTargetName);
                port.setStorageDevice(storageSystem.getId());
                port.setCompatibilityStatus(compatibilityStatus.name());
                port.setOperationalStatus(OperationalStatus.OK.name());
                port.setIpAddress(sdcIP);
                port.setNetwork(network.getId());
                port.setPortGroup("");
                port.setPortName(generatedTargetName);
                port.setPortType(StoragePort.PortType.frontend.name());
                port.setStorageHADomain(adapter.getId());
                port.setTransportType(StorageProtocol.Transport.IP.name());
                port.setInactive(false);
                port.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                _dbClient.createObject(port);
                endpoints.add(port.getPortNetworkId());
            } else {
                port = results.get(0);
            }
            ports.add(port);
        }
        network.addEndpoints(endpoints, true);
        _dbClient.updateAndReindexObject(network);
        return ports;
    }

    private VirtualArray createVirtualArray(String label) {
        List<VirtualArray> virtualArrayList =
                CustomQueryUtility.
                        queryActiveResourcesByConstraint(_dbClient, VirtualArray.class,
                                PrefixConstraint.Factory.getFullMatchConstraint(VirtualArray.class, "label", label));
        VirtualArray virtualArray = null;
        if (virtualArrayList == null || virtualArrayList.isEmpty()) {
            virtualArray = new VirtualArray();
            virtualArray.setId(URIUtil.createId(VirtualArray.class));
            virtualArray.setAutoSanZoning(false);
            virtualArray.setLabel(label);
            _dbClient.createObject(virtualArray);
        } else {
            if (virtualArrayList.size() > 1) {
                log.warn("There are {} VirtualArrays with label '{}'", virtualArrayList.size(), label);
            }
            virtualArray = virtualArrayList.get(0);
        }

        return virtualArray;
    }
}
