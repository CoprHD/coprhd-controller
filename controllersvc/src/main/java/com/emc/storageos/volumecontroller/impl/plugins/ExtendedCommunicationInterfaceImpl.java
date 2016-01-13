/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedCifsShareACL;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedNFSShareACL;
import com.emc.storageos.volumecontroller.ControllerLockingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StatTimeSeries;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.plugins.metering.XMLStatsDumpGenerator;

/**
 * This class provides base implementation of {@link ExtendedCommunicationInterface} functions to avoid having many empty implementations in
 * the subclasses. It is also
 * a good place to add re-usable code.
 * 
 */
public abstract class ExtendedCommunicationInterfaceImpl implements ExtendedCommunicationInterface {
    private static final Logger _logger = LoggerFactory
            .getLogger(ExtendedCommunicationInterfaceImpl.class);
    protected DbClient _dbClient;
    protected CoordinatorClient _coordinator;
    protected NetworkDeviceController _networkDeviceController;

    protected ControllerLockingService _locker;
    protected TaskCompleter _completer;
    protected Map<String, Object> _cache;
    protected Map<String, Object> _keyMap = new ConcurrentHashMap<String, Object>();
    protected static final String METERING = "metering";
    protected static final String SCAN = "scan";
    protected static final String DISCOVER = "discover";
    protected static final String SCAN_INTERVAL = "scan-interval";
    protected static final String DISCOVERY_INTERVAL = "discovery-interval";
    protected static final String NS_DISCOVERY_INTERVAL = "ns-discovery-interval";
    protected static final String METERING_INTERVAL = "metering-interval";
    protected PartitionManager _partitionManager;
    public static final String UNMANAGED_VOLUME = "UnManagedVolume";
    public static final String UNMANAGED_FILESYSTEM = "UnManagedFileSystem";
    public static final String UNMANAGED_EXPORT_MASK = "UnManagedExportMask";
    private XMLStatsDumpGenerator _xmlDumpGenerator;

    @Override
    public final void injectCache(Map<String, Object> cache) {
        _cache = cache;
    }

    @Override
    public final void injectDBClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    @Override
    public final void injectCoordinatorClient(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    @Override
    public final void injectControllerLockingService(ControllerLockingService locker) {
        _locker = locker;
    }

    @Override
    public void injectTaskCompleter(TaskCompleter completer) {
        _completer = completer;
    }

    /**
     * Dump stat records in /tmp location.
     */
    protected void dumpStatRecords() {
        @SuppressWarnings("unchecked")
        Map<String, String> meteringProps = (Map<String, String>) _keyMap.get(Constants.PROPS);
        if (Boolean.parseBoolean(meteringProps.get(Constants.METERINGDUMP))) {
            _xmlDumpGenerator.dumpRecordstoXML(_keyMap);
        }
    }

    /**
     * Inject Stats to Cassandra. To-Do: To verify, how fast batch insertion is
     * working for entries in 1000s. If its taking time, then will need to work
     * out, splitting again the batch into smaller batches.
     * 
     * @throws BaseCollectionException
     */
    protected void injectStats() throws BaseCollectionException {

        DbClient client = (DbClient) _keyMap.get(Constants.dbClient);
        @SuppressWarnings("unchecked")
        List<Stat> stats = (List<Stat>) _keyMap.get(Constants._Stats);
        @SuppressWarnings("unchecked")
        Map<String, String> props = (Map<String, String>) _keyMap.get(Constants.PROPS);
        // insert in batches
        int size = Constants.DEFAULT_PARTITION_SIZE;
        if (null != props.get(Constants.METERING_RECORDS_PARTITION_SIZE)) {
            size = Integer.parseInt(props.get(Constants.METERING_RECORDS_PARTITION_SIZE));
        }

        if (null == _partitionManager) {
            Stat[] statBatch = new Stat[stats.size()];
            statBatch = stats.toArray(statBatch);
            try {
                client.insertTimeSeries(StatTimeSeries.class, statBatch);
                _logger.info("{} Stat records persisted to DB", statBatch.length);
            } catch (DatabaseException e) {
                _logger.error("Error inserting records into the database", e);
            }
        } else {
            _partitionManager.insertInBatches(stats, size, client);
        }
    }

    @Override
    public void cleanup() {
        // do nothing
    }

    public void setXmlDumpGenerator(XMLStatsDumpGenerator xmlDumpGenerator) {
        _xmlDumpGenerator = xmlDumpGenerator;
    }

    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }

    /**
     * Synchronize the existing active DB Un-Managed FS objects with the newly discovered
     * Un-Managed FS objects listed by the Array.
     */
    protected void markUnManagedFSObjectsInActive(StorageSystem storageSystem,
            Set<URI> discoveredUnManagedFileSystems) {
        _logger.info(" -- Processing {} discovered Un-Managed FS Objects from -- {}",
                discoveredUnManagedFileSystems.size(), storageSystem.getLabel());
        if (discoveredUnManagedFileSystems.isEmpty()) {
            return;
        }
        // Get all available existing unmanaged FS URIs for this array from DB
        URIQueryResultList allAvailableUnManagedFileSystemsInDB = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageDeviceUnManagedFileSystemConstraint(storageSystem.getId()),
                allAvailableUnManagedFileSystemsInDB);

        List<UnManagedFileSystem> unManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        int totalObjs = 0;
        while (allAvailableUnManagedFileSystemsInDB.iterator().hasNext()) {
            URI unManagedFileSystemUri = allAvailableUnManagedFileSystemsInDB.iterator()
                    .next();

            if (!discoveredUnManagedFileSystems.contains(unManagedFileSystemUri)) {
                UnManagedFileSystem uFS = _dbClient.queryObject(
                        UnManagedFileSystem.class, unManagedFileSystemUri);
                _logger.info(
                        "Found a stale un-managed active file system in DB {} - Marking this to In-Active",
                        uFS.getNativeGuid());
                uFS.setInactive(true);
                unManagedFileSystems.add(uFS);
                if (unManagedFileSystems.size() == 1000) {
                    totalObjs += 1000;
                    _partitionManager.updateInBatches(unManagedFileSystems,
                            Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                            UNMANAGED_FILESYSTEM);
                    unManagedFileSystems.clear();
                }
            }
        }
        totalObjs += unManagedFileSystems.size();
        if (!unManagedFileSystems.isEmpty()) {
            _partitionManager.updateInBatches(unManagedFileSystems,
                    Constants.DEFAULT_PARTITION_SIZE, _dbClient, UNMANAGED_FILESYSTEM);
        }
        _logger.info("Total number of stale unmanaged file systems processed {}",
                totalObjs);

    }

    /**
     * check Pre Existing Storage Export Rule exists in DB
     * 
     * @param dbClient
     * @param exportRuleNativeGuid
     * @return unManagedFileExportRule
     * @throws java.io.IOException
     */
    protected UnManagedFileExportRule checkUnManagedFsExportRuleExistsInDB(DbClient dbClient,
            String exportRuleNativeGuid) {
        UnManagedFileExportRule unManagedExportRule = null;
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileExporRuleNativeGUIdConstraint(exportRuleNativeGuid), result);
        Iterator<URI> iter = result.iterator();
        while (iter.hasNext()) {
            URI unExportRuleURI = iter.next();
            unManagedExportRule = dbClient.queryObject(UnManagedFileExportRule.class, unExportRuleURI);
            return unManagedExportRule;
        }
        return unManagedExportRule;
    }

    /**
     * check Pre Existing Storage CIFS ACLs exists in DB
     * 
     * @param dbClient
     * @param cifsNativeGuid
     * @return UnManagedCifsShareACL
     * @throws java.io.IOException
     */
    protected UnManagedCifsShareACL checkUnManagedFsCifsACLExistsInDB(DbClient dbClient,
            String cifsACLNativeGuid) {
        UnManagedCifsShareACL unManagedCifsAcl = null;
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileCifsACLNativeGUIdConstraint(cifsACLNativeGuid), result);
        Iterator<URI> iter = result.iterator();
        while (iter.hasNext()) {
            URI cifsAclURI = iter.next();
            unManagedCifsAcl = dbClient.queryObject(UnManagedCifsShareACL.class, cifsAclURI);
            return unManagedCifsAcl;
        }
        return unManagedCifsAcl;
    }
    
    /**
     * check Pre Existing Storage NFS ACLs exists in DB
     * 
     * @param dbClient
     * @param nfsNativeGuid
     * @return UnManagedNFSShareACL
     * @throws java.io.IOException
     */
    protected UnManagedNFSShareACL checkUnManagedFsNfssACLExistsInDB(DbClient dbClient,
            String nfsACLNativeGuid) {
        UnManagedNFSShareACL unManagedNfsAcl = null;
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileNfsACLNativeGUIdConstraint(nfsACLNativeGuid), result);
        Iterator<URI> iter = result.iterator();
        while (iter.hasNext()) {
            URI cifsAclURI = iter.next();
            unManagedNfsAcl = dbClient.queryObject(UnManagedNFSShareACL.class, cifsAclURI);
            return unManagedNfsAcl;
        }
        return unManagedNfsAcl;
    }

    @Override
    public void injectNetworkDeviceController(
            NetworkDeviceController networkDeviceController) {
        _networkDeviceController = networkDeviceController;

    }

}
