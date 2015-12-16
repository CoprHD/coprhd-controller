/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Namespace;
import com.emc.storageos.plugins.common.domainmodel.NamespaceList;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery.LocalReplicaObject;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery.RemoteMirrorObject;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.SMIExecutor;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.google.common.collect.Sets;

/**
 * This class is responsible for collecting statistics information from SMI
 * Providers. Isilon plugin should implement this interface
 * ICommunicationInterface,and have their own logic of collecting statistics
 * information.
 */
public class SMICommunicationInterface extends ExtendedCommunicationInterfaceImpl {
    private static final Logger _logger = LoggerFactory
            .getLogger(SMICommunicationInterface.class);
    private SMIExecutor executor;
    private WBEMClient _wbemClient;
    private boolean debug;
    private NamespaceList namespaces;

    /**
     * To-Do : Argument Changes, to accomodate ProSphere usage
     */
    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile)
            throws BaseCollectionException {
        try {
            _logger.info("Access Profile Details :" + accessProfile.toString());
            _wbemClient = getCIMClient(accessProfile);
            initMap(accessProfile);
            StorageSystem storageSystem = queryStorageSystem(accessProfile);
            String providerVersion = getProviderVersionString(storageSystem);
            if (null != providerVersion) {
                _keyMap.put(Constants.VERSION, providerVersion);
                _keyMap.put(Constants.IS_NEW_SMIS_PROVIDER, isSMIS8XProvider(providerVersion));
            }
            Namespace _ns = null;
            _ns = (Namespace) namespaces.getNsList().get(METERING);
            _logger.info("CIMClient initialized successfully");
            executor.setKeyMap(_keyMap);
            executor.execute(_ns);
            _logger.info("Started Injection of Stats to Cassandra");
            dumpStatRecords();
            injectStats();
        } catch (Exception e) {
            throw new SMIPluginException(e.getMessage());
        } finally {
            releaseResources();
        }
    }

    /**
     * Initialize the Map
     * 
     * @param _keyMap
     * @param cacheVolumes
     * @param cachePools
     * @param accessProfile
     */
    private void initMap(AccessProfile accessProfile) {
        _keyMap.put(Constants._computerSystem, CimObjectPathCreator.createInstance(Constants._cimSystem,
                accessProfile.getInteropNamespace()));
        _keyMap.put(Constants._cimClient, _wbemClient);
        _keyMap.put(Constants._serialID, accessProfile.getserialID());
        _keyMap.put(Constants.dbClient, _dbClient);
        if (_networkDeviceController != null) {
            _keyMap.put(Constants.networkDeviceController, _networkDeviceController);
        }
        _keyMap.put(Constants._Volumes, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants._nativeGUIDs, Sets.newHashSet());
        _keyMap.put(Constants._Stats, new LinkedList<Stat>());
        _keyMap.put(Constants._InteropNamespace, accessProfile.getInteropNamespace());
        _keyMap.put(Constants._debug, debug);
        _keyMap.put(Constants.ACCESSPROFILE, accessProfile);
        List<String> manifestCollectionList = new LinkedList<String>();
        manifestCollectionList.add(Constants.MANIFEST_COLLECTION_NAME);
        _keyMap.put(Constants.MANIFEST_EXISTS, manifestCollectionList);
        _keyMap.put(Constants.PROPS, accessProfile.getProps());
        // Add storagePool Object path & LinkedList<CIMObjectPath> to Map
        _keyMap.put(Constants._storagePool, CimObjectPathCreator.createInstance(Constants._cimPool,
                accessProfile.getInteropNamespace()));
        _keyMap.put(Constants.STORAGEPOOLS, new LinkedList<CIMObjectPath>());
    }

    /**
     * Creates a new WEBClient for a given IP, based on AccessProfile
     * 
     * @param accessProfile
     *            : AccessProfile for the providers
     * @throws WBEMException
     *             : if WBEMException while creating the WBEMClient
     * @throws SMIPluginException
     * @return WBEMClient : initialized instance of WBEMClientCIMXML
     */
    private WBEMClient getCIMClient(AccessProfile accessProfile)
            throws SMIPluginException {
        WBEMClient cimClient = null;
        try {
            final CIMConnectionFactory connectionFactory = (CIMConnectionFactory) accessProfile
                    .getCimConnectionFactory();
            CimConnection cxn = connectionFactory.getConnection(
                    accessProfile.getIpAddress(), accessProfile.getProviderPort());
            if (cxn == null) {
                throw new SMIPluginException(String.format(
                        "Not able to get CimConnection to SMISProvider %s on port %s",
                        accessProfile.getIpAddress(), accessProfile.getProviderPort()),
                        SMIPluginException.ERRORCODE_NO_WBEMCLIENT);
            }
            cimClient = cxn.getCimClient();
            if (null == cimClient) {
                throw new SMIPluginException("Not able to get CIMOM client",
                        SMIPluginException.ERRORCODE_NO_WBEMCLIENT);
            }
        } catch (final IllegalStateException ex) {
            _logger.error("Not able to get CIMOM Client instance for ip {} due to ",
                    accessProfile.getIpAddress(), ex);
            throw new SMIPluginException(SMIPluginException.ERRORCODE_NO_WBEMCLIENT,
                    ex.fillInStackTrace(), ex.getMessage());
        }
        return cimClient;
    }

    /**
     * Stop the Plugin Thread, by gracefully clearing its resources.
     */
    @Override
    public void cleanup() {
        _logger.info("Stopping the Plugin Thread and clearing Resources");
        releaseResources();
    }

    /**
     * releaseResources
     */
    private void releaseResources() {
        execServiceShut();
        _keyMap.clear();
        namespaces = null;
    }

    /**
     * Shut Down Executor
     */
    private void execServiceShut() {
        ExecutorService execService = executor.getExecService();
        /*
         * Threads spawned using ExecutorService, mostly stuck in Blocking IOs,
         * not responsible to interruption. Currently, we don't have the handle
         * to get the underlying socket, so that altleast we can close it. The
         * alternate way used to handle this scenario, is to introduce timeout
         * to Blocking Calls.We have used set timeout value, for each SMI Call
         * to Provider, by this way we would be able to step out, if the SMI
         * call got stuck in-between. shutDownNow is used instead of shutdown,
         * as the call to Stop has arrived after a expected finish time of say x
         * minutes, and there is no point in waiting for the threads which got
         * spawned by Executor Service to complete. Hence, shutDownNow is
         * used.As timeout has been introduced in Blocking Calls, this should
         * not raise a concern on threads getting abruptly shutting down To-Do:
         * Find out ways to get the underlying socket, so that we can gracefully
         * clean those threads, instead of abruptly shutting down.
         */
        execService.shutdown();
        try {
            execService.awaitTermination(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            // To-DO: filter it for timeout sException
            // No need to throw any exception
            _logger.info("TimeOut occured after waiting Client Threads to finish");
        }
    }

    public void setExecutor(SMIExecutor executor) {
        this.executor = executor;
    }

    public SMIExecutor getExecutor() {
        return executor;
    }

    public void setDebug(boolean debugValue) {
        debug = debugValue;
    }

    public void setNamespaces(NamespaceList namespaces) {
        this.namespaces = namespaces;
    }

    public NamespaceList getNamespaces() {
        return namespaces;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void scan(AccessProfile accessProfile) throws BaseCollectionException {
        URI providerURI = null;
        StorageProvider providerObj = null;
        String detailedStatusMessage = "Unknown Status";
        try {
            _logger.info("Access Profile Details :" + accessProfile.toString());
            providerURI = accessProfile.getSystemId();
            providerObj = _dbClient.queryObject(StorageProvider.class, providerURI);
            _keyMap = new ConcurrentHashMap<String, Object>();
            _wbemClient = getCIMClient(accessProfile);
            _logger.info("CIMClient initialized successfully");
            _keyMap.put(Constants.PROPS, accessProfile.getProps());
            if (accessProfile.getCache() == null) {
                accessProfile.setCache(new HashMap<String, StorageSystemViewObject>());
            }
            _keyMap.put(Constants._computerSystem, new ArrayList<CIMObjectPath>());
            _keyMap.put(Constants.REGISTEREDPROFILE, CimObjectPathCreator.createInstance(
                    Constants.PROFILECLASS, "interop"));
            _keyMap.put(Constants._cimClient, _wbemClient);
            _keyMap.put(Constants.dbClient, _dbClient);
            _keyMap.put(Constants.COORDINATOR_CLIENT, _coordinator);
            if (_networkDeviceController != null) {
                _keyMap.put(Constants.networkDeviceController, _networkDeviceController);
            }
            _keyMap.put(Constants._InteropNamespace, accessProfile.getInteropNamespace());
            _keyMap.put(Constants.ACCESSPROFILE, accessProfile);
            _keyMap.put(Constants.SYSTEMCACHE, accessProfile.getCache());
            executor.setKeyMap(_keyMap);
            executor.execute((Namespace) namespaces.getNsList().get(SCAN));

            // scan succeeds
            detailedStatusMessage = String.format("Scan job completed successfully for " +
                    "SMISProvider: %s", providerURI.toString());
        } catch (Exception e) {
            detailedStatusMessage = String.format("Scan job failed for SMISProvider: %s because %s",
                    providerURI.toString(), e.getMessage());
            throw new SMIPluginException(detailedStatusMessage);
        } finally {
            if (providerObj != null) {
                try {
                    // set detailed message
                    providerObj.setLastScanStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(providerObj);
                } catch (DatabaseException ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
            releaseResources();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void discover(AccessProfile accessProfile) throws BaseCollectionException {
        URI storageSystemURI = null;
        StorageSystem storageSystem = null;
        String detailedStatusMessage = "Unknown Status";
        long startTime = System.currentTimeMillis();
        try {
            _logger.info("Access Profile Details :" + accessProfile.toString());
            storageSystemURI = accessProfile.getSystemId();
            storageSystem = queryStorageSystem(accessProfile);
            _keyMap = new ConcurrentHashMap<String, Object>();
            _wbemClient = getCIMClient(accessProfile);
            _logger.info("CIMClient initialized successfully");

            _keyMap.put(Constants._cimClient, _wbemClient);
            _keyMap.put(Constants.dbClient, _dbClient);
            _keyMap.put(Constants.COORDINATOR_CLIENT, _coordinator);
            if (_networkDeviceController != null) {
                _keyMap.put(Constants.networkDeviceController, _networkDeviceController);
            }
            _keyMap.put(Constants.PROPS, accessProfile.getProps());
            _keyMap.put(Constants._InteropNamespace, accessProfile.getInteropNamespace());
            _keyMap.put(Constants.ACCESSPROFILE, accessProfile);
            _keyMap.put(Constants.SYSTEMID, accessProfile.getSystemId());
            _keyMap.put(Constants._serialID, accessProfile.getserialID());
            _keyMap.put(Constants.STORAGEPOOLS, new LinkedList<CIMObjectPath>());
            _keyMap.put(Constants.PROTOCOLS, new HashSet<String>());
            _keyMap.put(Constants.STORAGEETHERNETPORTS, new LinkedList<CIMObjectPath>());
            _keyMap.put(Constants.IPENDPOINTS, new LinkedList<CIMObjectPath>());
            _keyMap.put(Constants.STORAGEVOLUMES, new LinkedList<CIMObjectPath>());
            String providerVersion = getProviderVersionString(storageSystem);
            if (null != providerVersion) {
                _keyMap.put(Constants.VERSION, providerVersion);
                _keyMap.put(Constants.IS_NEW_SMIS_PROVIDER, isSMIS8XProvider(providerVersion));
            }

            Map<URI, StoragePool> poolsToMatchWithVpool = new HashMap<URI, StoragePool>();
            _keyMap.put(Constants.MODIFIED_STORAGEPOOLS, poolsToMatchWithVpool);
            // need this nested structure to be able to minimize the changes on existing code.
            List<List<StoragePort>> portsToRunNetworkConnectivity = new ArrayList<List<StoragePort>>();
            _keyMap.put(Constants.STORAGE_PORTS, portsToRunNetworkConnectivity);

            List<StoragePort> discoveredPorts = new ArrayList<StoragePort>();
            _keyMap.put(Constants.DISCOVERED_PORTS, discoveredPorts);
            _keyMap.put(Constants.SLO_NAMES, new HashSet<String>());
            if (Type.ibmxiv.name().equals(accessProfile.getSystemType())) {
                initIBMDiscoveryKeyMap(accessProfile);
            }
            else {
                initEMCDiscoveryKeyMap(accessProfile);
            }

            executor.setKeyMap(_keyMap);
            executor.execute((Namespace) namespaces.getNsList().get(DISCOVER));

        } catch (Exception e) {
            detailedStatusMessage = String.format("Discovery failed for Storage System: %s because %s",
                    storageSystemURI.toString(), e.getMessage());
            _logger.error(detailedStatusMessage, e);
            throw new SMIPluginException(detailedStatusMessage);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (DatabaseException ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
            releaseResources();
            long totalTime = System.currentTimeMillis() - startTime;
            _logger.info(String.format("Discovery of Storage System %s took %f seconds", storageSystemURI.toString(), (double) totalTime
                    / (double) 1000));
        }
    }

    private StorageSystem queryStorageSystem(AccessProfile accessProfile)
            throws IOException {
        return _dbClient.queryObject(StorageSystem.class, accessProfile.getSystemId());
    }

    private void initEMCDiscoveryKeyMap(AccessProfile accessProfile) {
        _keyMap.put(Constants._computerSystem, CimObjectPathCreator.createInstance(
                Constants.EmcStorageSystem, accessProfile.getInteropNamespace()));
        _keyMap.put(Constants.TIER, CimObjectPathCreator.createInstance(
                Constants.EMC_STORAGE_TIER, accessProfile.getInteropNamespace()));
        _keyMap.put(Constants.CONFIGURATIONSERVICE, CimObjectPathCreator.createInstance(
                Constants.EMCCONTROLLERCONFIGURATIONSERVICE, accessProfile.getInteropNamespace()));
        _keyMap.put(Constants.TIERPOLICYSERVICE, CimObjectPathCreator.createInstance(
                Constants.EMCTIERPOLICYSERVICE, accessProfile.getInteropNamespace()));
        _keyMap.put(Constants.STORAGE_VOLUME_VIEWS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.DEVICEANDTHINPOOLS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.THINPOOLS, new LinkedList<CIMObjectPath>());

        _keyMap.put(Constants.STORAGEPROCESSORS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.POOLCAPABILITIES, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.STORAGETIERS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.REPLICATIONGROUPS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.VMAXFASTPOLICIES, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.VNXFASTPOLICIES, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.VNXPOOLCAPABILITIES, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.VNXPOOLCAPABILITIES_TIER, new LinkedList<String>());
        _keyMap.put(Constants.VNXPOOLSETTINGS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.VNXPOOLS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.VMAXPOOLS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.VMAX2POOLS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.VMAX2_THIN_POOLS, new LinkedList<CIMObjectPath>());

        _keyMap.put(Constants.TIERDOMAINS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.MASKING_VIEWS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.VNXPOOLSETTINGINSTANCES, new LinkedList<CIMInstance>());
        _keyMap.put(Constants.MASKING_GROUPS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.ENDPOINTS_RAGROUP, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.VOLUME_RAGROUP, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.UN_VOLUMES_RAGP, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.SRDF_LINKS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.STORAGE_GROUPS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.AUTO_TIER_VOLUMES, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.EVENT_MANAGER, accessProfile.getRecordableEventManager());

        Set<URI> systemsToRunRPConnectivity = new HashSet<URI>();
        _keyMap.put(Constants.SYSTEMS_RUN_RP_CONNECTIVITY, systemsToRunRPConnectivity);

        Map<String, RemoteMirrorObject> unManagedVolToRaGroupMap = new HashMap<String, RemoteMirrorObject>();
        _keyMap.put(Constants.UN_VOLUME_RAGROUP_MAP, unManagedVolToRaGroupMap);

        Map<String, String> policyToGroupMap = new HashMap<String, String>();
        _keyMap.put(Constants.POLICY_STORAGE_GROUP_MAPPING, policyToGroupMap);

        Map<String, String> volumesWithSLO = new HashMap<String, String>();
        _keyMap.put(Constants.VOLUMES_WITH_SLOS, volumesWithSLO);

        Map<String, String> volumeToSpaceConsumedMap = new HashMap<String, String>();
        _keyMap.put(Constants.VOLUME_SPACE_CONSUMED_MAP, volumeToSpaceConsumedMap);

        Map<String, Set<String>> vmax2ThinPoolToBoundVolumesMap = new HashMap<String, Set<String>>();
        _keyMap.put(Constants.VMAX2_THIN_POOL_TO_BOUND_VOLUMES, vmax2ThinPoolToBoundVolumesMap);

        // modifiedSettingInstances
        _keyMap.put(Constants.MODIFIED_SETTING_INSTANCES, new LinkedList<CIMInstance>());
        _keyMap.put(Constants.SYSTEMCREATEDDEVICEGROUPNAMES, new LinkedList<String>());
        _keyMap.put(Constants.USED_IN_CHECKING_GROUPNAMES_EXISTENCE, new LinkedList<String>());
        _keyMap.put(Constants.USED_IN_CHECKING_GROUPNAMES_TO_FASTPOLICY, new LinkedList<String>());
        _keyMap.put(Constants.REPLICATIONSERVICE, new LinkedList<CIMObjectPath>());
        _keyMap.put("connectivityCollection", new LinkedList<CIMObjectPath>());

        Map<String, URI> raGroupMap = new HashMap<String, URI>();
        _keyMap.put(Constants.RAGROUP, raGroupMap);

        // deviceMaskingGroups
        _keyMap.put(Constants.DEVICEMASKINGROUPS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.FASTPOLICY, CimObjectPathCreator.createInstance(
                Constants.CIMFASTPOLICYRULE, accessProfile.getInteropNamespace()));

        // deviceMaskingGroups
        Map<String, Set<String>> policytopoolMapping = new HashMap<String, Set<String>>();
        _keyMap.put(Constants.POLICY_TO_POOLS_MAPPING, policytopoolMapping);

        Map<String, StringSet> volumeToStorageGroupMapping = new HashMap<String, StringSet>();
        _keyMap.put(Constants.VOLUME_STORAGE_GROUP_MAPPING, volumeToStorageGroupMapping);

        Map<String, Set<Integer>> initiatorToHLU = new HashMap<String, Set<Integer>>();
        _keyMap.put(Constants.INITIATOR_HLU_MAP, initiatorToHLU);

        // unmanaged volume (local mirrors/snapshots) discovery
        Map<String, LocalReplicaObject> unManagedVolToLocalReplicaMap = new HashMap<String, LocalReplicaObject>();
        _keyMap.put(Constants.UN_VOLUME_LOCAL_REPLICA_MAP, unManagedVolToLocalReplicaMap);

        Map<String, Map<String, String>> snapshotToSynchronizationAspectMap = new HashMap<String, Map<String, String>>();
        _keyMap.put(Constants.SNAPSHOT_NAMES_SYNCHRONIZATION_ASPECT_MAP, snapshotToSynchronizationAspectMap);
    }

    private void initIBMDiscoveryKeyMap(AccessProfile accessProfile) {
        _keyMap.put(Constants._computerSystem, CimObjectPathCreator.createInstance(
                Constants.IBM_STORAGE_SYSTEM, accessProfile.getInteropNamespace()));
    }

    /**
     * Return the provider version.
     * 
     * @param storageSystem
     * @return
     */
    private String getProviderVersionString(StorageSystem storageSystem) throws IOException {
        String providerVersion = null;
        if (!NullColumnValueGetter.isNullURI(storageSystem.getActiveProviderURI())) {
            StorageProvider provider = _dbClient.queryObject(StorageProvider.class, storageSystem.getActiveProviderURI());

            if (null != provider && null != provider.getVersionString()) {
                providerVersion = provider.getVersionString().replaceFirst("[^\\d]", "");
            }
        }
        return providerVersion;
    }

    /**
     * Verify the version specified in the KeyMap and return true if major version is < 8.
     * If Version string is not set in keyamp, then return true.
     * 
     * @param keyMap
     * @return
     */
    private boolean isSMIS8XProvider(String providerVersion) {
        String provStr[] = providerVersion.split(Constants.SMIS_DOT_REGEX);
        return Integer.parseInt(provStr[0]) >= 8;
    }
}
