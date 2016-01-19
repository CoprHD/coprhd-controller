/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Host.HostType;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockConsistencyGroupCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockConsistencyGroupDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockConsistencyGroupUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportAddInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportAddVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportRemoveInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportRemoveVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MultiVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeExpandCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.workflow.WorkflowService;

/**
 * Block storage device controller test
 * 
 * Configuration
 * 
 * 1. VM arguments -Dproduct.home=/opt/storageos
 * 
 * 2. Classpath
 * Folders
 * cimadapter/src/main/resources
 * controllersvc/src/main/test/resources
 * discoveryplugins/src/main/resources
 * dbutils/src/conf
 * 
 * Project
 * dbutils (project only, no exported entries and required projects)
 * 
 * Log file will be in controllersvc/logs dir. Configure
 * cimadapter/src/main/resources/log4j.properties as necessary
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath*:**ibmxiv-storagedevice-conf-test.xml" })
public class BlockStorageDeviceTest {
    private static final Logger _logger = LoggerFactory
            .getLogger(BlockStorageDeviceTest.class);
    private static int NUM_OF_VOLUMES = 2;
    private static int NUM_OF_SNAPSHOTS = 1;
    private static final String STORAGE_DEVICE = "storageDevice";
    private static final String LABEL_PREFIX = "DummyVolTestJ";

    static private int initIndex = 22;

    @Autowired
    private DbClient _dbClient = null;
    @Autowired
    private WorkflowService _workflowService = null;
    @Autowired
    private BlockStorageDevice _deviceController = null;
    @Autowired
    private ExportMaskOperations _exportMaksHelper = null;

    private StorageSystem _storageSystem = null;
    private StoragePool _storagePool = null;
    private Project _project = null;

    private static enum Operation {
        Create, Expand, Delete;
    }

    @Before
    public void setup() {
        Assert.notNull(_dbClient);
        Assert.notNull(_workflowService);
        Assert.notNull(_deviceController);

        _dbClient.start();
        _workflowService.start();

        _project = createProject();
        _storageSystem = getStorageSystem();
        Assert.notNull(_storageSystem);

        List<StoragePool> pools = CustomQueryUtility
                .queryActiveResourcesByRelation(_dbClient,
                        _storageSystem.getId(), StoragePool.class,
                        "storageDevice");
        Assert.notEmpty(pools);

        // use a thin pool
        for (StoragePool pool : pools) {
            if (pool.getMaximumThinVolumeSize() > 0) {
                _storagePool = pool;
                break;
            }
        }

        Assert.notNull(_storagePool);
    }

    @After
    public void cleanup() {
        if (_dbClient != null) {
            _dbClient.stop();
        }

        if (_workflowService != null) {
            _workflowService.stop();
        }
    }

    @Test
    public void testVolumeCreation() {
        performOperation(Operation.Create);
    }

    @Test
    public void testVolumeExpansion() {
        performOperation(Operation.Expand);
    }

    @Test
    public void testVolumeDeletion() {
        performOperation(Operation.Delete);
    }

    @Test
    public void testExportGroupCreation() {
        ExportGroup exportGroup = getExportGroup();
        ExportMask exportMask = getExportMask();

        int lun = 1;
        Map<URI, Integer> volumeMap = new HashMap<URI, Integer>();
        for (Volume volume : getVolumes(_storageSystem)) {
            volumeMap.put(volume.getId(), lun++);
            break;
        }

        List<Initiator> initiatorList = getInitiators();
        List<URI> initiatorURIs = new ArrayList<URI>();
        for (Initiator initiator : initiatorList) {
            initiatorURIs.add(initiator.getId());
        }

        String maskingStep = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        ExportTaskCompleter taskCompleter = new ExportMaskCreateCompleter(
                exportGroup.getId(), exportMask.getId(), initiatorURIs, volumeMap,
                maskingStep);
        _deviceController.doExportGroupCreate(_storageSystem, exportMask, volumeMap, initiatorList, null,
                taskCompleter);
    }

    @Test
    public void testExportGroupDeletion() {
        ExportGroup exportGroup = getExportGroup();
        ExportMask exportMask = getExportMask();

        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        ExportTaskCompleter taskCompleter = new ExportDeleteCompleter(exportGroup.getId(), false, token);
        _deviceController.doExportGroupDelete(_storageSystem, exportMask, taskCompleter);
    }

    @Test
    public void testExportAddVolume() {
        ExportGroup exportGroup = getExportGroup();
        ExportMask exportMask = getExportMask();

        List<Volume> volumes = getVolumes(_storageSystem);
        Volume volume = volumes.get(0);
        Map<URI, Integer> volumeMap = new HashMap<URI, Integer>();
        int lun = 1;
        volumeMap.put(volume.getId(), lun);

        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        ExportTaskCompleter taskCompleter = new ExportAddVolumeCompleter(exportGroup.getId(), volumeMap, token);
        _deviceController.doExportAddVolume(_storageSystem, exportMask, volume.getId(), lun, taskCompleter);
    }

    @Test
    public void testExportAddVolumes() {
        ExportGroup exportGroup = getExportGroup();
        ExportMask exportMask = getExportMask();
        List<Volume> volumes = getVolumes(_storageSystem);
        Map<URI, Integer> volumeMap = new HashMap<URI, Integer>();
        int lun = 1;
        for (Volume volume : volumes) {
            volumeMap.put(volume.getId(), lun++);
        }

        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        ExportTaskCompleter taskCompleter = new ExportAddVolumeCompleter(exportGroup.getId(), volumeMap, token);
        _deviceController.doExportAddVolumes(_storageSystem, exportMask, volumeMap, taskCompleter);
    }

    @Test
    public void testExportRemoveVolume() {
        ExportGroup exportGroup = getExportGroup();
        ExportMask exportMask = getExportMask();
        List<Volume> volumes = getVolumes(_storageSystem);
        Volume volume = volumes.get(0);
        List<URI> volumeURIs = new ArrayList<URI>(1);
        volumeURIs.add(volume.getId());

        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        ExportTaskCompleter taskCompleter = new ExportRemoveVolumeCompleter(exportGroup.getId(), volumeURIs, token);
        _deviceController.doExportRemoveVolume(_storageSystem, exportMask, volume.getId(), taskCompleter);
    }

    @Test
    public void testExportRemoveVolumes() {
        ExportGroup exportGroup = getExportGroup();
        ExportMask exportMask = getExportMask();
        List<Volume> volumes = getVolumes(_storageSystem);

        List<URI> volumeURIs = new ArrayList<URI>();
        for (Volume volume : volumes) {
            volumeURIs.add(volume.getId());
        }

        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        ExportTaskCompleter taskCompleter = new ExportRemoveVolumeCompleter(exportGroup.getId(), volumeURIs, token);
        _deviceController.doExportRemoveVolumes(_storageSystem, exportMask, volumeURIs, taskCompleter);
    }

    @Test
    public void testExportAddInitiator() {
        ExportGroup exportGroup = getExportGroup();
        ExportMask exportMask = getExportMask();
        Initiator initiator = getInitiators().get(0);
        List<URI> initiatorURIs = new ArrayList<URI>();
        initiatorURIs.add(initiator.getId());
        List<URI> targets = new ArrayList<URI>();

        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        ExportTaskCompleter taskCompleter = new ExportAddInitiatorCompleter(exportGroup.getId(), initiatorURIs, token);
        _deviceController.doExportAddInitiator(_storageSystem, exportMask, initiator, targets, taskCompleter);
    }

    @Test
    public void testExportAddInitiators() {
        ExportGroup exportGroup = getExportGroup();
        ExportMask exportMask = getExportMask();
        List<Initiator> initiators = getInitiators();

        List<URI> initiatorURIs = new ArrayList<URI>();
        List<Initiator> initiatorArgs = new ArrayList<Initiator>();
        for (Initiator initiator : initiators) {
            if (initiator.getInitiatorPort().endsWith("2")) {
                initiatorURIs.add(initiator.getId());
                initiatorArgs.add(initiator);
            }
        }

        List<URI> targets = new ArrayList<URI>();

        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        ExportTaskCompleter taskCompleter = new ExportAddInitiatorCompleter(exportGroup.getId(), initiatorURIs, token);
        _deviceController.doExportAddInitiators(_storageSystem, exportMask, initiatorArgs, targets, taskCompleter);
    }

    @Test
    public void testExportRemoveInitiator() {
        ExportGroup exportGroup = getExportGroup();
        ExportMask exportMask = getExportMask();
        Initiator initiator = getInitiators().get(0);
        ;

        List<URI> initiatorURIs = new ArrayList<URI>(1);
        initiatorURIs.add(initiator.getId());

        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        ExportTaskCompleter taskCompleter = new ExportRemoveInitiatorCompleter(exportGroup.getId(), initiatorURIs, token);
        _deviceController.doExportRemoveInitiator(_storageSystem, exportMask, initiator, null, taskCompleter);
    }

    @Test
    public void testExportRemoveInitiators() {
        ExportGroup exportGroup = getExportGroup();
        ExportMask exportMask = getExportMask();
        List<Initiator> initiators = getInitiators();

        List<URI> initiatorURIs = new ArrayList<URI>();
        List<Initiator> initiatorArgs = new ArrayList<Initiator>();
        for (Initiator initiator : initiators) {
            if (initiator.getInitiatorPort().endsWith("1")) {
                initiatorURIs.add(initiator.getId());
                initiatorArgs.add(initiator);
            }
        }

        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        ExportTaskCompleter taskCompleter = new ExportRemoveInitiatorCompleter(exportGroup.getId(), initiatorURIs, token);
        _deviceController.doExportRemoveInitiators(_storageSystem, exportMask, initiators, null, taskCompleter);
    }

    @Test
    public void testFindMasks() {
        List<Initiator> initiators = getInitiators();
        List<String> initiatorNames = new ArrayList<String>();
        for (Initiator initiator : initiators) {
            initiatorNames.add(initiator.getInitiatorPort());
        }

        _deviceController.findExportMasks(_storageSystem, initiatorNames, true);
    }

    @Test
    public void testRefreshExportMask() {
        ExportMask exportMask = getExportMask();
        _deviceController.refreshExportMask(_storageSystem, exportMask);
    }

    @Test
    public void testCreateSnapshot() {
        Boolean createInactive = false;
        List<BlockSnapshot> snapshots = createSnapshots();
        List<URI> snapshotList = new ArrayList<URI>();
        for (BlockSnapshot snapshot : snapshots) {
            snapshotList.add(snapshot.getId());
            break;
        }

        String token = UUID.randomUUID().toString()
                + UUID.randomUUID().toString();
        TaskCompleter taskCompleter = new BlockSnapshotCreateCompleter(
                snapshotList, token);
        _deviceController.doCreateSnapshot(_storageSystem, snapshotList,
                createInactive, false, taskCompleter);
    }

    @Test
    public void testRestoreFromSnapshot() {
        List<BlockSnapshot> snapshots = getSnapshots(_storageSystem);
        BlockSnapshot snapshot = snapshots.get(0);
        URI volume = getVolumes(_storageSystem).get(0).getId();
        String token = UUID.randomUUID().toString()
                + UUID.randomUUID().toString();
        TaskCompleter taskCompleter = new BlockSnapshotRestoreCompleter(
                snapshot, token);
        _deviceController.doRestoreFromSnapshot(_storageSystem, volume,
                snapshot.getId(), taskCompleter);
    }

    @Test
    public void testDeleteSnapshot() {
        List<BlockSnapshot> snapshots = getSnapshots(_storageSystem);
        BlockSnapshot snapshot = snapshots.get(0);
        String token = UUID.randomUUID().toString()
                + UUID.randomUUID().toString();
        TaskCompleter taskCompleter = BlockSnapshotDeleteCompleter.createCompleter(_dbClient,
                snapshot, token);
        _deviceController.doDeleteSnapshot(_storageSystem, snapshot.getId(),
                taskCompleter);
    }

    @Test(expected = com.emc.storageos.exceptions.DeviceControllerException.class)
    public void testActivateSnapshot() {
        List<BlockSnapshot> snapshots = getSnapshots(_storageSystem);
        List<URI> snapshotList = new ArrayList<URI>();
        snapshotList.add(snapshots.get(0).getId());
        String token = UUID.randomUUID().toString()
                + UUID.randomUUID().toString();
        TaskCompleter taskCompleter = BlockSnapshotDeleteCompleter.createCompleter(_dbClient,
                snapshots.get(0), token);
        _deviceController.doActivateSnapshot(_storageSystem, snapshotList,
                taskCompleter);
    }

    @Test
    public void testCreateClone() {
        URI sourceVolume = getVolumes(_storageSystem).get(0).getId();
        URI cloneVolume = createVolumes().get(0).getId();
        Boolean createInactive = false;

        String token = UUID.randomUUID().toString()
                + UUID.randomUUID().toString();
        CloneCreateCompleter taskCompleter = new CloneCreateCompleter(
                cloneVolume, token);
        _deviceController.doCreateClone(_storageSystem, sourceVolume,
                cloneVolume, createInactive, taskCompleter);
    }

    @Test(expected = com.emc.storageos.exceptions.DeviceControllerException.class)
    public void testActivateFullCopy() {
        URI fullCopy = null;
        TaskCompleter completer = null;
        _deviceController.doActivateFullCopy(_storageSystem, fullCopy,
                completer);
    }

    @Test(expected = com.emc.storageos.exceptions.DeviceControllerException.class)
    public void testDetachClone() {
        URI cloneVolume = null;
        TaskCompleter taskCompleter = null;
        _deviceController.doDetachClone(_storageSystem, cloneVolume,
                taskCompleter);
    }

    @Test
    public void testCreateConsistencyGroup() {
        URI consistencyGroup = createConsistencyGroup().getId();
        String token = UUID.randomUUID().toString()
                + UUID.randomUUID().toString();
        BlockConsistencyGroupCreateCompleter taskCompleter = new BlockConsistencyGroupCreateCompleter(
                consistencyGroup, token);
        _deviceController.doCreateConsistencyGroup(_storageSystem,
                consistencyGroup, null, taskCompleter);
    }

    @Test
    public void testAddToConsistencyGroup() {
        URI consistencyGroupId = getConsistencyGroup().getId();
        List<Volume> volumes = getVolumes(_storageSystem);
        List<URI> blockObjects = new ArrayList<URI>();
        for (Volume volume : volumes) {
            blockObjects.add(volume.getId());
        }

        String token = UUID.randomUUID().toString()
                + UUID.randomUUID().toString();
        BlockConsistencyGroupUpdateCompleter taskCompleter = new BlockConsistencyGroupUpdateCompleter(
                consistencyGroupId, token);
        _deviceController.doAddToConsistencyGroup(_storageSystem,
 consistencyGroupId, getConsistencyGroup().getLabel(), blockObjects,
                taskCompleter);
    }

    @Test
    public void testRemoveFromConsistencyGroup() {
        URI consistencyGroupId = getConsistencyGroup().getId();
        List<Volume> volumes = getVolumes(_storageSystem);
        List<URI> blockObjects = new ArrayList<URI>();
        for (Volume volume : volumes) {
            blockObjects.add(volume.getId());
        }

        String token = UUID.randomUUID().toString()
                + UUID.randomUUID().toString();
        BlockConsistencyGroupUpdateCompleter taskCompleter = new BlockConsistencyGroupUpdateCompleter(
                consistencyGroupId, token);
        _deviceController.doRemoveFromConsistencyGroup(_storageSystem,
                consistencyGroupId, blockObjects, taskCompleter);
    }

    @Test
    public void testDeleteConsistencyGroup() {
        URI consistencyGroup = getConsistencyGroup().getId();
        String token = UUID.randomUUID().toString()
                + UUID.randomUUID().toString();
        BlockConsistencyGroupDeleteCompleter taskCompleter = new BlockConsistencyGroupDeleteCompleter(
                consistencyGroup, token);
        _deviceController.doDeleteConsistencyGroup(_storageSystem,
                consistencyGroup, null, null, true, taskCompleter);
    }

    @Test(expected = com.emc.storageos.exceptions.DeviceControllerException.class)
    public void testCopySnapshotsToTarget() {
        List<URI> snapshotList = null;
        TaskCompleter taskCompleter = null;
        _deviceController.doCopySnapshotsToTarget(_storageSystem, snapshotList,
                taskCompleter);
    }

    @Test
    public void testCheckSyncProgress() {
        List<BlockSnapshot> snapshots = getSnapshots(_storageSystem);
        BlockSnapshot snapshot = snapshots.get(0);
        URI volumeURI = getVolumes(_storageSystem).get(0).getId();
        _deviceController.checkSyncProgress(_storageSystem.getId(), volumeURI,
                snapshot.getId());
    }

    private void performOperation(Operation operation) {
        String taskId = UUID.randomUUID().toString();

        if (Operation.Create.equals(operation)) {
            List<Volume> volumes = createVolumes();
            VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
            TaskCompleter taskCompleter = new VolumeCreateCompleter(volumes
                    .get(0).getId(), taskId);
            _deviceController.doCreateVolumes(_storageSystem, _storagePool,
                    taskId, volumes, capabilities, taskCompleter);
            // TODO - assert vols are created
            // update vol labels with real label
        } else if (Operation.Expand.equals(operation)) {
            List<Volume> volumes = getVolumes(_storageSystem);
            Volume volume = volumes.get(0);
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class,
                    volume.getPool());
            long size = volume.getProvisionedCapacity() * 2;
            TaskCompleter taskCompleter = new VolumeExpandCompleter(
                    volume.getId(), size, taskId);
            _deviceController.doExpandVolume(_storageSystem, storagePool,
                    volume, size, taskCompleter);
            // TODO - assert original vol's size (provisionedCapacity or
            // capacity??) are changed
        } else if (Operation.Delete.equals(operation)) {
            List<Volume> volumes = getVolumes(_storageSystem);
            List<URI> ids = new ArrayList<URI>(volumes.size());
            List<VolumeTaskCompleter> volumeTaskCompleters = new ArrayList<>(
                    volumes.size());
            for (Volume volume : volumes) {
                URI uri = volume.getId();
                ids.add(uri);
                volumeTaskCompleters
                        .add(new VolumeCreateCompleter(uri, taskId));
            }

            MultiVolumeTaskCompleter multiTaskCompleter = new MultiVolumeTaskCompleter(
                    ids, volumeTaskCompleters, taskId);
            _deviceController.doDeleteVolumes(_storageSystem, taskId, volumes,
                    multiTaskCompleter);
            // TODO - assert vols are deleted from db
        }
    }

    private Project createProject() {
        final TenantOrg tenantorg = new TenantOrg();
        tenantorg.setId(URIUtil.createId(TenantOrg.class));
        tenantorg.setLabel("EMC");
        tenantorg.setParentTenant(new NamedURI(URIUtil
                .createId(TenantOrg.class), tenantorg.getLabel()));

        _dbClient.createObject(tenantorg);
        _logger.info("TenantOrg :" + tenantorg.getId());

        final Project project = new Project();
        project.setId(URIUtil.createId(Project.class));
        project.setLabel("project");
        project.setTenantOrg(new NamedURI(tenantorg.getId(), project.getLabel()));

        _dbClient.createObject(project);
        _logger.info("Project :" + project.getId());
        _logger.info("TenantOrg-Proj :" + project.getTenantOrg());

        return project;
    }

    private StorageSystem getStorageSystem() {
        StorageSystem storageSystem = null;
        List<URI> objectURIs = _dbClient
                .queryByType(StorageSystem.class, true);
        Iterator<StorageSystem> iter = _dbClient.queryIterativeObjects(
                StorageSystem.class, objectURIs);
        while (iter.hasNext()) {
            storageSystem = iter.next();
            if (storageSystem.getSystemType().equals(
                    DiscoveredDataObject.Type.ibmxiv.name())) {
                break;
            }
        }

        return storageSystem;
    }

    private List<Volume> createVolumes() {
        List<Volume> volumes = new ArrayList<Volume>(NUM_OF_VOLUMES);
        for (int i = 0; i < NUM_OF_VOLUMES; i++) {
            Volume volume = new Volume();
            volume.setId(URIUtil.createId(Volume.class));
            volume.setInactive(false);
            volume.setLabel(LABEL_PREFIX + i);
            volume.setCapacity(17298180736L); // which capacity to set?
            volume.setStorageController(_storageSystem.getId());
            volume.setPool(_storagePool.getId());
            volume.setVirtualPool(URIUtil.createId(VirtualPool.class));
            volume.setProject(new NamedURI(_project.getId(), volume.getLabel()));
            volume.setTenant(new NamedURI(_project.getTenantOrg().getURI(), volume.getLabel()));

            volumes.add(volume);
        }

        _dbClient.createObject(volumes);

        return volumes;
    }

    private List<Volume> getVolumes(StorageSystem storeageSystem) {
        // get all object URIs contained by the StorageSystem
        URIQueryResultList queryResults = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getContainedObjectsConstraint(storeageSystem.getId(),
                        Volume.class, STORAGE_DEVICE), queryResults);

        Iterator<Volume> volumeIter = _dbClient.queryIterativeObjects(
                Volume.class, queryResults);
        List<Volume> volumes = new ArrayList<Volume>();
        while (volumeIter.hasNext()) {
            Volume volume = volumeIter.next();
            String label = volume.getLabel();
            _logger.info("Volume: " + label);

            if (label.startsWith(LABEL_PREFIX)) {
                volumes.add(volume);
            }
        }

        return volumes;
    }

    private ExportGroup getExportGroup() {
        ExportGroup exportGroup = null;
        List<URI> objectURIs = _dbClient
                .queryByType(ExportGroup.class, true);
        Iterator<ExportGroup> iter = _dbClient.queryIterativeObjects(
                ExportGroup.class, objectURIs);
        while (iter.hasNext()) {
            exportGroup = iter.next();
            break;
        }

        if (exportGroup == null) {
            exportGroup = new ExportGroup();
            exportGroup.setId(URIUtil.createId(ExportGroup.class));
            exportGroup.setLabel("EMCViPR");
            exportGroup.setInactive(false);
            exportGroup.setProject(new NamedURI(_project.getId(), exportGroup.getLabel()));
            exportGroup.setTenant(new NamedURI(_project.getTenantOrg().getURI(), exportGroup.getLabel()));
            exportGroup.setVirtualArray(URIUtil.createId(VirtualArray.class));
            _dbClient.createObject(exportGroup);
        }

        StringSet masks = exportGroup.getExportMasks();
        if (masks == null) {
            exportGroup.addExportMask(getExportMask().getId());
            _dbClient.persistObject(exportGroup);
        }

        return exportGroup;
    }

    private ExportMask getExportMask() {
        ExportMask exportMask = null;
        List<URI> objectURIs = _dbClient
                .queryByType(ExportMask.class, true);
        Iterator<ExportMask> iter = _dbClient.queryIterativeObjects(
                ExportMask.class, objectURIs);
        while (iter.hasNext()) {
            ExportMask mask = iter.next();
            mask.setMaskName("host2278");
            mask.setStorageDevice(_storageSystem.getId());
            _dbClient.persistObject(mask);
            return mask;
        }

        exportMask = new ExportMask();
        exportMask.setId(URIUtil.createId(ExportMask.class));
        exportMask.setLabel("EMCViPR2");
        exportMask.setInactive(false);
        _dbClient.createObject(exportMask);

        return exportMask;
    }

    private Host getHost() {
        Host host = null;
        List<URI> objectURIs = _dbClient
                .queryByType(Host.class, true);
        Iterator<Host> iter = _dbClient.queryIterativeObjects(
                Host.class, objectURIs);
        while (iter.hasNext()) {
            return iter.next();
        }

        host = new Host();
        host.setId(URIUtil.createId(Host.class));
        host.setHostName("dummy.lss.emc.com");
        host.setUsername("user");
        host.setPassword("password");
        host.setPortNumber(8111);
        host.setOsVersion("1.0");
        host.setType(HostType.Windows.name());
        host.setTenant(_project.getTenantOrg().getURI());
        host.setLabel("EMCViPR");
        host.setInactive(false);
        _dbClient.createObject(host);

        return host;
    }

    private List<Initiator> getInitiators() {
        List<Initiator> initiators = new ArrayList<Initiator>();

        List<URI> objectURIs = _dbClient
                .queryByType(Initiator.class, true);
        Iterator<Initiator> iter = _dbClient.queryIterativeObjects(
                Initiator.class, objectURIs);

        while (iter.hasNext()) {
            initiators.add(iter.next());
        }

        if (initiators.isEmpty()) {
            initiators.add(createInitiator(initIndex++));
            initiators.add(createInitiator(initIndex++));
        }
        else if (initiators.size() == 1) {
            initiators.add(createInitiator(initIndex + 1));
        }

        return initiators;
    }

    private Initiator createInitiator(int index) {
        Initiator initiator = new Initiator();
        initiator.setId(URIUtil.createId(Initiator.class));
        initiator.setHostName("dummy.lss.emc.com");
        initiator.setHost(getHost().getId());
        initiator.setProtocol(Protocol.FC.name());
        initiator.setInitiatorNode("nwwwn A" + index);
        String byte1 = String.format("%02x", index / 256);
        String byte0 = String.format("%02x", index % 256);
        initiator.setInitiatorPort("10:00:00:00:00:00:" + byte1 + ":"
                + byte0);
        initiator.setLabel("EMCViPR" + index);
        initiator.setInactive(false);
        _dbClient.createObject(initiator);

        return initiator;
    }

    private List<BlockSnapshot> getSnapshots(StorageSystem storeageSystem) {
        // get all object URIs contained by the StorageSystem
        URIQueryResultList queryResults = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getContainedObjectsConstraint(storeageSystem.getId(),
                        BlockSnapshot.class, STORAGE_DEVICE), queryResults);

        Iterator<BlockSnapshot> snapshotIter = _dbClient.queryIterativeObjects(
                BlockSnapshot.class, queryResults);
        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
        while (snapshotIter.hasNext()) {
            BlockSnapshot snapshot = snapshotIter.next();
            String label = snapshot.getDeviceLabel();
            _logger.info("Snapshot: " + label);
            if (label != null && label.startsWith(LABEL_PREFIX)) {
                snapshot.setSnapsetLabel("ViPRTest");
                _dbClient.persistObject(snapshot);
                snapshots.add(snapshot);
            }
        }

        if (!snapshots.isEmpty()) {
            return snapshots;
        } else {
            return createSnapshots();
        }
    }

    private List<BlockSnapshot> createSnapshots() {
        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>(
                NUM_OF_SNAPSHOTS);
        for (int i = 0; i < NUM_OF_SNAPSHOTS; i++) {
            BlockSnapshot snapshot = new BlockSnapshot();
            snapshot.setId(URIUtil.createId(BlockSnapshot.class));
            snapshot.setInactive(false);
            snapshot.setLabel(LABEL_PREFIX + "_snap_" + 1);
            snapshot.setStorageController(_storageSystem.getId());
            snapshot.setProject(new NamedURI(_project.getId(), snapshot
                    .getLabel()));
            snapshot.setParent(new NamedURI(getVolumes(_storageSystem).get(0)
                    .getId(), snapshot.getLabel()));
            snapshot.setConsistencyGroup(getConsistencyGroup().getId());
            snapshots.add(snapshot);
        }

        _dbClient.createObject(snapshots);

        return snapshots;
    }

    private BlockConsistencyGroup getConsistencyGroup() {
        List<URI> objectURIs = _dbClient.queryByType(
                BlockConsistencyGroup.class, true);
        Iterator<BlockConsistencyGroup> iter = _dbClient.queryIterativeObjects(
                BlockConsistencyGroup.class, objectURIs);
        while (iter.hasNext()) {
            BlockConsistencyGroup cg = iter.next();
            if (cg.getDeviceName() != null) {
                return cg;
            }
        }

        return createConsistencyGroup();
    }

    private BlockConsistencyGroup createConsistencyGroup() {
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setId(URIUtil.createId(BlockConsistencyGroup.class));
        cg.setType(BlockConsistencyGroup.Types.LOCAL.name());
        cg.setLabel("ViPRTest");
        cg.setProject(new NamedURI(_project.getId(), cg.getLabel()));
        cg.setTenant(new NamedURI(_project.getTenantOrg().getURI(), cg
                .getLabel()));
        cg.setInactive(false);
        _dbClient.createObject(cg);

        return cg;
    }
}
