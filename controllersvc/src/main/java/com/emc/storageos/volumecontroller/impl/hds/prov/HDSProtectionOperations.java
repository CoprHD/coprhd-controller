/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.api.HDSApiExportManager;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.hds.api.HDSApiProtectionManager;
import com.emc.storageos.hds.model.FreeLun;
import com.emc.storageos.hds.model.HostStorageDomain;
import com.emc.storageos.hds.model.Path;
import com.emc.storageos.hds.model.ReplicationInfo;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeCreateCompleter;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSBlockCreateMirrorJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSBlockCreateSnapshotJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSCreateVolumeJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSDeleteSnapshotJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSCommandHelper;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;

public class HDSProtectionOperations {
    private static final Logger log = LoggerFactory.getLogger(HDSProtectionOperations.class);
    private DbClient dbClient;
    private HDSApiFactory hdsApiFactory;
    private HDSCommandHelper hdsCommandHelper;
    protected NameGenerator nameGenerator;
    private ControllerLockingService locker;
    /**
     * key storageSystemObjId,
     * Value, lock instance
     */
    private Map<String, ReentrantLock> localJVMLockMap = new HashMap<String, ReentrantLock>();

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public HDSApiFactory getHdsApiFactory() {
        return hdsApiFactory;
    }

    public void setHdsApiFactory(HDSApiFactory hdsApiFactory) {
        this.hdsApiFactory = hdsApiFactory;
    }

    public HDSCommandHelper getHdsCommandHelper() {
        return hdsCommandHelper;
    }

    public void setHdsCommandHelper(HDSCommandHelper hdsCommandHelper) {
        this.hdsCommandHelper = hdsCommandHelper;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    /**
     * Creates secondary volume for ShadowImage pair operations.
     * 
     * @param storageSystem
     * @param sourceVolume
     * @param targetVolume
     * @throws Exception
     */
    public void createSecondaryVolumeForClone(StorageSystem storageSystem,
            URI sourceVolume, Volume targetVolume) throws Exception {
        log.info("SecondaryVolume creation operation started");
        String taskId = UUID.randomUUID().toString();
        TaskCompleter taskCompleter = new VolumeCreateCompleter(targetVolume.getId(), taskId);
        String asyncTaskMessageId = null;
        HDSApiClient hdsApiClient = HDSUtils.getHDSApiClient(hdsApiFactory, storageSystem);

        String systemObjectID = HDSUtils.getSystemObjectID(storageSystem);

        BlockObject sourceObj = BlockObject.fetch(dbClient, sourceVolume);
        URI tenantUri = null;

        StoragePool targetPool = dbClient.queryObject(StoragePool.class, targetVolume.getPool());

        if (sourceObj instanceof BlockSnapshot) {
            // In case of snapshot, get the tenant from its parent volume
            NamedURI parentVolUri = ((BlockSnapshot) sourceObj).getParent();
            Volume parentVolume = dbClient.queryObject(Volume.class, parentVolUri);
            tenantUri = parentVolume.getTenant().getURI();

            TenantOrg tenantOrg = dbClient.queryObject(TenantOrg.class, tenantUri);
            // String cloneLabel = generateLabel(tenantOrg, cloneObj);
        } else {
            // This is a default flow
            tenantUri = ((Volume) sourceObj).getTenant().getURI();
        }

        if (targetVolume.getThinlyProvisioned()) {
            asyncTaskMessageId = hdsApiClient.createThinVolumes(systemObjectID,
                    targetPool.getNativeId(), targetVolume.getCapacity(), 1, targetVolume.getLabel(),
                    HDSConstants.QUICK_FORMAT_TYPE, storageSystem.getModel());
        } else {
            String poolObjectID = HDSUtils.getPoolObjectID(targetPool);
            asyncTaskMessageId = hdsApiClient.createThickVolumes(systemObjectID,
                    poolObjectID, targetVolume.getCapacity(), 1, targetVolume.getLabel(), null, storageSystem.getModel(), null);
        }

        if (asyncTaskMessageId != null) {
            HDSJob createHDSJob = new HDSCreateVolumeJob(
                    asyncTaskMessageId, targetVolume.getStorageController(), targetPool.getId(),
                    taskCompleter);
            hdsCommandHelper.waitForAsyncHDSJob(createHDSJob);
        }
        log.info("SecondaryVolume creation operation completed successfully");
    }

    /**
     * Creates secondary volume for ShadowImage pair operations.
     * 
     * @param storageSystem
     * @param sourceVolume
     * @param mirror
     * @throws Exception
     */
    public void createSecondaryVolumeForMirror(StorageSystem storageSystem,
            URI sourceVolume, BlockMirror mirror) throws Exception {
        log.info("SecondaryVolume for mirror creation operation started");
        String taskId = UUID.randomUUID().toString();
        TaskCompleter taskCompleter = new BlockMirrorCreateCompleter(mirror.getId(), taskId);
        String asyncTaskMessageId = null;
        HDSApiClient hdsApiClient = HDSUtils.getHDSApiClient(hdsApiFactory, storageSystem);

        String systemObjectID = HDSUtils.getSystemObjectID(storageSystem);

        StoragePool targetPool = dbClient.queryObject(StoragePool.class, mirror.getPool());
        Volume source = dbClient.queryObject(Volume.class, sourceVolume);
        TenantOrg tenant = dbClient.queryObject(TenantOrg.class, source.getTenant().getURI());
        String tenantName = tenant.getLabel();
        String targetLabelToUse = nameGenerator.generate(tenantName, mirror.getLabel(), mirror.getId().toString(),
                '-', HDSConstants.MAX_VOLUME_NAME_LENGTH);

        if (mirror.getThinlyProvisioned()) {
            asyncTaskMessageId = hdsApiClient.createThinVolumes(systemObjectID,
                    targetPool.getNativeId(), mirror.getCapacity(), 1, targetLabelToUse,
                    HDSConstants.QUICK_FORMAT_TYPE, storageSystem.getModel());
        } else {
            String poolObjectID = HDSUtils.getPoolObjectID(targetPool);
            asyncTaskMessageId = hdsApiClient.createThickVolumes(systemObjectID,
                    poolObjectID, mirror.getCapacity(), 1, targetLabelToUse, null, storageSystem.getModel(), null);
        }

        if (asyncTaskMessageId != null) {
            HDSJob createHDSJob = new HDSBlockCreateMirrorJob(
                    asyncTaskMessageId, mirror.getStorageController(), targetPool.getId(),
                    taskCompleter);
            hdsCommandHelper.waitForAsyncHDSJob(createHDSJob);
        }
        log.info("SecondaryVolume for mirror creation operation completed successfully");
    }

    /**
     * Creates Snapshot Volume
     * 
     * @param storageSystem
     * @param sourceVolume
     * @param snapshotObj
     * @throws Exception
     */
    public void createSecondaryVolumeForSnapshot(StorageSystem storageSystem,
            Volume sourceVolume, BlockSnapshot snapshotObj) throws Exception {

        log.info("SecondaryVolume for snapshot creation operation started");
        String taskId = UUID.randomUUID().toString();
        TaskCompleter taskCompleter = new BlockSnapshotCreateCompleter(Arrays.asList(snapshotObj.getId()), taskId);
        String asyncTaskMessageId = null;
        HDSApiClient hdsApiClient = HDSUtils.getHDSApiClient(hdsApiFactory, storageSystem);

        String systemObjectID = HDSUtils.getSystemObjectID(storageSystem);

        asyncTaskMessageId = hdsApiClient.createSnapshotVolume(systemObjectID, sourceVolume.getCapacity(), storageSystem.getModel());

        if (asyncTaskMessageId != null) {
            HDSJob createHDSJob = new HDSBlockCreateSnapshotJob(
                    asyncTaskMessageId, snapshotObj.getStorageController(), taskCompleter);
            hdsCommandHelper.waitForAsyncHDSJob(createHDSJob);
        }
        log.info("SecondaryVolume for snapshot creation operation completed successfully");

    }

    public void deleteSecondaryVolumeSnapshot(StorageSystem storageSystem,
            BlockSnapshot snapshotObj, TaskCompleter taskCompleter) throws Exception {
        log.info("Snapshot deletion operation started");

        String asyncTaskMessageId = null;
        HDSApiClient hdsApiClient = HDSUtils.getHDSApiClient(hdsApiFactory, storageSystem);

        String systemObjectID = HDSUtils.getSystemObjectID(storageSystem);
        String logicalUnitObjId = HDSUtils.getLogicalUnitObjectId(snapshotObj.getNativeId(), storageSystem);
        asyncTaskMessageId = hdsApiClient.deleteSnapshotVolume(systemObjectID, logicalUnitObjId, storageSystem.getModel());

        if (null != asyncTaskMessageId) {
            HDSJob deleteSnapshotJob = new HDSDeleteSnapshotJob(asyncTaskMessageId,
                    snapshotObj.getStorageController(), taskCompleter);
            hdsCommandHelper.waitForAsyncHDSJob(deleteSnapshotJob);
        }
        log.info("Snapshot deletion operation completed successfully");
    }

    /**
     * Adds Dummy Lun Path to Secondary Volume for pair creation
     * 
     * @param client
     * @param volume
     * @throws Exception
     */
    public void addDummyLunPath(HDSApiClient client,
            BlockObject volume) throws Exception {
        StorageSystem system = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        String systemObjectId = HDSUtils.getSystemObjectID(system);
        ReentrantLock lock = null;
        try {
            /**
             * This will take care synchronization between all cluster node.
             * So that same time two different node can not add same lun to different volumes.
             * This will not take care within the same node.
             */
            locker.acquireLock(systemObjectId, HDSConstants.LOCK_WAIT_SECONDS);
            /**
             * We create and maintain lock instance per storage system.
             * So that multiple thread can not add same lun number to different volumes on same storage system.
             */
            lock = getLock(systemObjectId);
            lock.lock();
            log.info("Acquired Lock to add lun path");
            HostStorageDomain hsd = getDummyHSDFromStorageSystem(client, systemObjectId);
            if (null == hsd) {
                log.info("Creating dummy HSD for ShadowImage");
                // Get any port which belongs to the storage system.
                URIQueryResultList storagePortURIs = new URIQueryResultList();
                dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(system.getId()),
                        storagePortURIs);
                StoragePort storagePort = null;
                Iterator<URI> storagePortsIter = storagePortURIs.iterator();
                while (storagePortsIter.hasNext())
                {
                    URI storagePortURI = storagePortsIter.next();
                    storagePort = dbClient.queryObject(StoragePort.class,
                            storagePortURI);
                    if (storagePort != null && !storagePort.getInactive()) {
                        break;
                    }
                }
                if (storagePort != null) {
                    String portId = HDSUtils.getPortID(storagePort);
                    hsd = client.getHDSApiExportManager().addHostStorageDomain(systemObjectId, portId,
                            HDSConstants.HOST_GROUP_DOMAIN_TYPE, null, HDSConstants.DUMMY_HSD, null, null, system.getModel());
                    log.info("Created dummy HSD on {} portid", portId);
                }
            }
            List<FreeLun> freeLunList = client.getHDSApiExportManager().getFreeLUNInfo(systemObjectId, hsd.getObjectID());
            log.debug("freeLunList.size :{}", freeLunList.size());
            log.debug("Free lun:{}", freeLunList.get(0).getLun());
            Map<String, String> deviceLunList = new HashMap<String, String>();
            deviceLunList.put(volume.getNativeId(), freeLunList.get(0).getLun());
            client.getHDSApiExportManager().addLUN(systemObjectId, hsd.getPortID(), hsd.getDomainID(), deviceLunList, system.getModel());
            log.info("Completed addDummyLunPath method");
        } finally {
            if (lock != null) {
                lock.unlock();
                log.info("Released Lock to add lun path");
            }
            locker.releaseLock(systemObjectId);
        }
    }

    /**
     * Check lock is already available for the give array.
     * if not available create a new one.
     * 
     * @param systemObjId
     * @return
     */
    private ReentrantLock getLock(String systemObjId) {
        ReentrantLock lock = null;
        synchronized (localJVMLockMap) {
            lock = localJVMLockMap.get(systemObjId);
            if (lock == null) {
                lock = new ReentrantLock();
                localJVMLockMap.put(systemObjId, lock);
            }
        }
        return lock;
    }

    /**
     * Get DummyHSD from StorageSystem if exist
     * 
     * @param apiClient
     * @param systemObjectId
     * @return dummyHSD
     * @throws Exception
     */
    private HostStorageDomain getDummyHSDFromStorageSystem(HDSApiClient apiClient, String systemObjectId) throws Exception {
        List<HostStorageDomain> hsdList = apiClient.getHDSApiExportManager().getHostStorageDomains(systemObjectId);
        if (hsdList != null) {

            log.debug("HSD list size :{}", hsdList.size());
            for (HostStorageDomain hsd : hsdList) {
                if (hsd != null && HDSConstants.DUMMY_HSD.equalsIgnoreCase(hsd.getNickname())) {
                    log.info("Found ViPR dummy HSD on storage system");
                    return hsd;
                }
            }
        }
        return null;
    }

    /**
     * Deletes shadowImage Pair
     * 
     * @param storageSystem
     * @param source
     * @param target
     * @throws Exception
     */
    public void deleteShadowImagePair(StorageSystem storageSystem, Volume source, Volume target) throws Exception {
        log.info("Delete pair operation started");
        HDSApiClient apiClient = HDSUtils.getHDSApiClient(hdsApiFactory, storageSystem);
        HDSApiProtectionManager apiProtectionManager = apiClient.getHdsApiProtectionManager();

        Map<String, String> repliMap = apiProtectionManager.
                getReplicationRelatedObjectIds(source.getNativeId(), target.getNativeId());
        log.info("Replication Obj Ids :{}", repliMap);
        String replicationGroupObjId = repliMap.get(HDSConstants.REPLICATION_GROUP_OBJ_ID);
        String replicationInfoObjId = repliMap.get(HDSConstants.REPLICATION_INFO_OBJ_ID);
        apiProtectionManager.deleteShadowImagePair(replicationGroupObjId, replicationInfoObjId, storageSystem.getModel());
        log.info("Delete pair operation completed");
    }

    /**
     * Modifies pair operation to split|resync|restore
     * 
     * @param storageSystem
     * @param sourceVolumeNativeId
     * @param targetVolumeNativeId
     * @param operationType
     * @throws Exception
     */
    public boolean modifyShadowImagePair(StorageSystem storageSystem, String sourceVolumeNativeId,
            String targetVolumeNativeId, HDSApiProtectionManager.ShadowImageOperationType operationType) throws Exception {
        HDSApiClient apiClient = HDSUtils.getHDSApiClient(hdsApiFactory, storageSystem);
        HDSApiProtectionManager apiProtectionManager = apiClient.getHdsApiProtectionManager();
        log.info("{} pair operation started", operationType.name());
        Map<String, String> repliMap = apiProtectionManager.
                getReplicationRelatedObjectIds(sourceVolumeNativeId, targetVolumeNativeId);
        log.info("Replication Obj Ids :{}", repliMap);
        String replicationGroupObjId = repliMap.get(HDSConstants.REPLICATION_GROUP_OBJ_ID);
        String replicationInfoObjId = repliMap.get(HDSConstants.REPLICATION_INFO_OBJ_ID);
        ReplicationInfo replicationInfo = apiProtectionManager.modifyShadowImagePair(replicationGroupObjId, replicationInfoObjId,
                operationType, storageSystem.getModel());
        log.info("{} pair operation completed", operationType.name());
        return (replicationInfo != null);
    }

    /**
     * Removes Dummy Lun Path from Secondary Volume
     * 
     * @param storageSystem
     * @param blockObjectURI
     * @throws Exception
     */
    public void removeDummyLunPath(StorageSystem storageSystem,
            URI blockObjectURI) throws Exception {
        log.info("Started dummy lun path removal from secondary volume");
        HDSApiClient apiClient = HDSUtils.getHDSApiClient(hdsApiFactory, storageSystem);
        HDSApiExportManager apiExportManager = apiClient.getHDSApiExportManager();
        String systemObjectId = HDSUtils.getSystemObjectID(storageSystem);
        // Volume volume=dbClient.queryObject(Volume.class, volumeURI);
        BlockObject blockObj = BlockObject.fetch(dbClient, blockObjectURI);

        String dummyLunPathId = getDummyHSDPathId(storageSystem, blockObj);
        if (dummyLunPathId != null) {
            apiExportManager.deleteLunPathsFromSystem(systemObjectId, Arrays.asList(dummyLunPathId), storageSystem.getModel());
            log.info("Deleted Dummy Lun path from secondary volume");
        } else {
            log.info("Dummy lun path has been removed already");
        }
    }

    /**
     * Get Dummy Lun Path's path objectID
     * 
     * @param storageSystem
     * @param volume
     * @return
     * @throws Exception
     */
    private String getDummyHSDPathId(StorageSystem storageSystem,
            BlockObject blockObj) throws Exception {
        String dummyLunPathId = null;
        HDSApiClient apiClient = HDSUtils.getHDSApiClient(hdsApiFactory, storageSystem);
        HDSApiExportManager apiExportManager = apiClient.getHDSApiExportManager();
        String systemObjectId = HDSUtils.getSystemObjectID(storageSystem);
        List<HostStorageDomain> hsdList = apiExportManager.getHostStorageDomains(systemObjectId);
        if (hsdList != null) {
            for (HostStorageDomain hsd : hsdList) {
                if (hsd != null && HDSConstants.DUMMY_HSD.equalsIgnoreCase(
                        hsd.getNickname())) {
                    if (hsd.getPathList() != null) {
                        for (Path path : hsd.getPathList()) {
                            if (path.getDevNum().equalsIgnoreCase(blockObj.getNativeId())) {
                                dummyLunPathId = path.getObjectID();
                                log.info("Found secondary volume's dummy lun path id :{}", dummyLunPathId);
                                return dummyLunPathId;
                            }
                        }
                    }
                }
            }
        }
        log.info("Dummy lun path has been removed already for this secondary volume");
        return dummyLunPathId;
    }

    /**
     * Returns pair name based on source and target volume's nativeId
     * 
     * @param source
     * @param target
     * @return pairName Ex: 100_104_SI
     */
    public String generatePairName(BlockObject source, BlockObject target) {
        StringBuilder pairName = new StringBuilder();
        pairName.append(source.getNativeId());
        pairName.append(HDSConstants.UNDERSCORE_OPERATOR);
        pairName.append(target.getNativeId());
        pairName.append(HDSConstants.UNDERSCORE_OPERATOR);
        pairName.append(HDSConstants.SI);
        return pairName.toString();
    }

    /**
     * Set the controller locking service.
     * 
     * @param locker An instance of ControllerLockingService
     */
    public void setLocker(ControllerLockingService locker) {
        this.locker = locker;
    }
}
