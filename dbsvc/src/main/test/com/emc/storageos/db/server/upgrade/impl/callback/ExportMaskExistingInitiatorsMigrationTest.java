/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
*/
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.ExportMaskExistingInitiatorsMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class ExportMaskExistingInitiatorsMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(ExportMaskExistingInitiatorsMigrationTest.class);

    private static final int INITIATOR_PER_HOST_COUNT = 4;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("3.5", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 2L;
            {
                add(new ExportMaskExistingInitiatorsMigration());
            }
        });
        DbsvcTestBase.setup();
    }

    @Override
    protected void prepareData() throws Exception {

        List<ExportMask> exportMasksToCreate = new ArrayList<ExportMask>();

        // set up a vplex system
        StorageSystem vplex = new StorageSystem();
        vplex.setId(URIUtil.createId(StorageSystem.class));
        vplex.setLabel("TEST_VPLEX");
        vplex.setSystemType(DiscoveredDataObject.Type.vplex.name());
        _dbClient.createObject(vplex);

        // set up a vnx system
        StorageSystem vnx = new StorageSystem();
        vnx.setId(URIUtil.createId(StorageSystem.class));
        vnx.setLabel("TEST_VNX");
        vnx.setSystemType(DiscoveredDataObject.Type.vnxblock.name());
        _dbClient.createObject(vnx);

        // set up a vmax system
        StorageSystem vmax = new StorageSystem();
        vmax.setId(URIUtil.createId(StorageSystem.class));
        vmax.setLabel("TEST_VMAX");
        vmax.setSystemType(DiscoveredDataObject.Type.vmax.name());
        _dbClient.createObject(vmax);

        // Setup 1: 1 ExportMasks per array. One Host with 4 initiators where 2 are User Created and 2 are existing
        for (int i = 0; i < 1; i++) {

            ExportMask exportMaskVPLEX = new ExportMask();
            exportMaskVPLEX.setId(URIUtil.createId(ExportMask.class));
            exportMaskVPLEX.setLabel("VplexExportMaskMigrationTestBasic");
            exportMaskVPLEX.setStorageDevice(vplex.getId());
            exportMasksToCreate.add(exportMaskVPLEX);

            ExportMask exportMaskVNX = new ExportMask();
            exportMaskVNX.setId(URIUtil.createId(ExportMask.class));
            exportMaskVNX.setLabel("VnxExportMaskMigrationTestBasic");
            exportMaskVNX.setStorageDevice(vnx.getId());
            exportMasksToCreate.add(exportMaskVNX);

            ExportMask exportMaskVMAX = new ExportMask();
            exportMaskVMAX.setId(URIUtil.createId(ExportMask.class));
            exportMaskVMAX.setLabel("VmaxExportMaskMigrationTestBasic");
            exportMaskVMAX.setStorageDevice(vmax.getId());
            exportMasksToCreate.add(exportMaskVMAX);

            Host host = new Host();
            host.setId(URIUtil.createId(Host.class));
            host.setLabel("hostLabel" + i);
            for (int j = 0; j < INITIATOR_PER_HOST_COUNT; j++) {
                Initiator initiator = new Initiator();
                initiator.setId(URIUtil.createId(Initiator.class));
                initiator.setHost(host.getId());
                initiator.setInitiatorPort("10:00:DE:AD:BE:EF:00" + i + j);
                initiator.setLabel("InitiatorLabel" + i + j);
                _dbClient.createObject(initiator);
                if (j < 2) {
                    exportMaskVPLEX.addInitiator(initiator);
                    exportMaskVPLEX.addToUserCreatedInitiators(initiator);
                    exportMaskVNX.addInitiator(initiator);
                    exportMaskVNX.addToUserCreatedInitiators(initiator);
                    exportMaskVMAX.addInitiator(initiator);
                    exportMaskVMAX.addToUserCreatedInitiators(initiator);
                } else {
                    exportMaskVPLEX.addToExistingInitiatorsIfAbsent(initiator.getInitiatorPort());
                    exportMaskVNX.addToExistingInitiatorsIfAbsent(initiator.getInitiatorPort());
                    exportMaskVMAX.addToExistingInitiatorsIfAbsent(initiator.getInitiatorPort());
                }
            }
            _dbClient.createObject(host);
            exportMaskVPLEX.setResource(host.getId().toString());
            exportMaskVPLEX.setResource(host.getId().toString());
            exportMaskVPLEX.setResource(host.getId().toString());
        }

        // Setup : 2 ExportMasks per array. One Export Mask with all Existing Initiators and other with all User Created.
        for (int i = 1; i < 3; i++) {

            ExportMask exportMaskVPLEX = new ExportMask();
            exportMaskVPLEX.setId(URIUtil.createId(ExportMask.class));
            exportMaskVPLEX.setStorageDevice(vplex.getId());
            exportMasksToCreate.add(exportMaskVPLEX);

            ExportMask exportMaskVNX = new ExportMask();
            exportMaskVNX.setId(URIUtil.createId(ExportMask.class));
            exportMaskVNX.setStorageDevice(vnx.getId());
            exportMasksToCreate.add(exportMaskVNX);

            ExportMask exportMaskVMAX = new ExportMask();
            exportMaskVMAX.setId(URIUtil.createId(ExportMask.class));
            exportMaskVMAX.setStorageDevice(vmax.getId());
            exportMasksToCreate.add(exportMaskVMAX);

            if (i == 1) {
                exportMaskVPLEX.setLabel("VplexExportMaskMigrationTesterUserCreated");
                exportMaskVNX.setLabel("VnxExportMaskMigrationTesterUserCreated");
                exportMaskVMAX.setLabel("VmaxExportMaskMigrationTesterUserCreated");
            } else {
                exportMaskVPLEX.setLabel("VplexExportMaskMigrationTesterExisting");
                exportMaskVNX.setLabel("VnxExportMaskMigrationTesterExisting");
                exportMaskVMAX.setLabel("VmaxExportMaskMigrationTesterExisting");
            }
            Host host = new Host();
            host.setId(URIUtil.createId(Host.class));
            host.setLabel("hostLabel" + i);
            for (int j = 0; j < INITIATOR_PER_HOST_COUNT; j++) {
                Initiator initiator = new Initiator();
                initiator.setId(URIUtil.createId(Initiator.class));
                initiator.setHost(host.getId());
                initiator.setInitiatorPort("10:00:DE:AD:BE:EF:00:" + i + j);
                initiator.setLabel("InitiatorLabel" + i + j);
                _dbClient.createObject(initiator);
                if (i == 1) {
                    exportMaskVPLEX.addInitiator(initiator);
                    exportMaskVPLEX.addToUserCreatedInitiators(initiator);
                    exportMaskVNX.addInitiator(initiator);
                    exportMaskVNX.addToUserCreatedInitiators(initiator);
                    exportMaskVMAX.addInitiator(initiator);
                    exportMaskVMAX.addToUserCreatedInitiators(initiator);
                } else {
                    exportMaskVPLEX.addToExistingInitiatorsIfAbsent(initiator.getInitiatorPort());
                    exportMaskVNX.addToExistingInitiatorsIfAbsent(initiator.getInitiatorPort());
                    exportMaskVMAX.addToExistingInitiatorsIfAbsent(initiator.getInitiatorPort());
                }
            }
            _dbClient.createObject(host);
            exportMaskVPLEX.setResource(host.getId().toString());
            exportMaskVPLEX.setResource(host.getId().toString());
            exportMaskVPLEX.setResource(host.getId().toString());
        }

        // Set Up 3. ExportMask with Existing initiator belonging to other HOSTS..
        for (int i = 5; i < 6; i++) {

            ExportMask exportMaskVPLEX = new ExportMask();
            exportMaskVPLEX.setId(URIUtil.createId(ExportMask.class));
            exportMaskVPLEX.setLabel("VplexExportMaskMigrationTesterMixed");
            exportMaskVPLEX.setStorageDevice(vplex.getId());
            exportMasksToCreate.add(exportMaskVPLEX);

            ExportMask exportMaskVNX = new ExportMask();
            exportMaskVNX.setId(URIUtil.createId(ExportMask.class));
            exportMaskVNX.setLabel("VnxExportMaskMigrationTesterMixed");
            exportMaskVNX.setStorageDevice(vnx.getId());
            exportMasksToCreate.add(exportMaskVNX);

            ExportMask exportMaskVMAX = new ExportMask();
            exportMaskVMAX.setId(URIUtil.createId(ExportMask.class));
            exportMaskVMAX.setLabel("VmaxExportMaskMigrationTesterMixed");
            exportMaskVMAX.setStorageDevice(vmax.getId());
            exportMasksToCreate.add(exportMaskVMAX);

            Host host = new Host();
            host.setId(URIUtil.createId(Host.class));
            host.setLabel("hostLabel" + i);
            for (int j = 0; j < INITIATOR_PER_HOST_COUNT; j++) {
                Initiator initiator = new Initiator();
                initiator.setId(URIUtil.createId(Initiator.class));
                initiator.setHost(host.getId());
                initiator.setInitiatorPort("10:00:DE:AD:BE:EF:00:" + i + j);
                initiator.setLabel("InitiatorLabel" + i + j);
                _dbClient.createObject(initiator);
                if (j < 2) {
                    exportMaskVPLEX.addInitiator(initiator);
                    exportMaskVPLEX.addToUserCreatedInitiators(initiator);
                    exportMaskVNX.addInitiator(initiator);
                    exportMaskVNX.addToUserCreatedInitiators(initiator);
                    exportMaskVMAX.addInitiator(initiator);
                    exportMaskVMAX.addToUserCreatedInitiators(initiator);
                } else {
                    exportMaskVPLEX.addToExistingInitiatorsIfAbsent(initiator.getInitiatorPort());
                    exportMaskVNX.addToExistingInitiatorsIfAbsent(initiator.getInitiatorPort());
                    exportMaskVMAX.addToExistingInitiatorsIfAbsent(initiator.getInitiatorPort());
                }
            }
            _dbClient.createObject(host);
            exportMaskVPLEX.setResource(host.getId().toString());
            exportMaskVPLEX.setResource(host.getId().toString());
            exportMaskVPLEX.setResource(host.getId().toString());

            Host hostA = new Host();
            hostA.setId(URIUtil.createId(Host.class));
            hostA.setLabel("hostLabelA");
            for (int j = 0; j < INITIATOR_PER_HOST_COUNT; j++) {
                Initiator initiator = new Initiator();
                initiator.setId(URIUtil.createId(Initiator.class));
                initiator.setHost(host.getId());
                initiator.setInitiatorPort("10:00:DE:AD:BE:EF:00:A" + j);
                initiator.setLabel("InitiatorLabel" + i + j);
                _dbClient.createObject(initiator);
                exportMaskVPLEX.addToExistingInitiatorsIfAbsent(initiator.getInitiatorPort());
                exportMaskVNX.addToExistingInitiatorsIfAbsent(initiator.getInitiatorPort());
                exportMaskVMAX.addToExistingInitiatorsIfAbsent(initiator.getInitiatorPort());
            }
            _dbClient.createObject(hostA);
        }

        _dbClient.createObject(exportMasksToCreate);

    }

    @Override
    protected void verifyResults() throws Exception {
        log.info("Verifying results of ExportMask ExistingInitiator migration test now.");

        List<URI> exportMaskUris = _dbClient.queryByType(ExportMask.class, true);
        Iterator<ExportMask> exportMasks = _dbClient.queryIterativeObjects(ExportMask.class, exportMaskUris, true);

        while (exportMasks.hasNext()) {
            ExportMask exportMask = exportMasks.next();
            String exportMaskLabel = exportMask.getLabel();
            log.info("Processing exportMask with name {}", exportMaskLabel);
            if (exportMaskLabel.contains("Basic")){
                if (exportMaskLabel.contains("VMAX") || exportMaskLabel.contains("VPLEX")) {
                    if ((exportMask.getInitiators() != null) && exportMask.getInitiators().size() != 4) {
                        log.error("ExportMask with name {} has {} Initiators instead of 4", exportMaskLabel,
                                exportMask.getInitiators().size());
                    }
                    if ((exportMask.getUserAddedInitiators() != null) && exportMask.getUserAddedInitiators().size() != 4) {
                        log.error("ExportMask with name {} has {} UserAddedInitiators instead of 4", exportMaskLabel,
                                exportMask.getUserAddedInitiators().size());
                    }
                    if ((exportMask.getExistingInitiators() != null) && exportMask.getExistingInitiators().size() != 0) {
                        log.error("ExportMask with name {} has {} ExistingInitiators instead of 0", exportMaskLabel,
                                exportMask.getExistingInitiators().size());
                    }
                } else {
                    if ((exportMask.getExistingInitiators() != null) && exportMask.getExistingInitiators().size() != 2) {
                        log.error("ExportMask with name {} has {} Initiators instead of 2", exportMaskLabel,
                                exportMask.getExistingInitiators().size());
                    }
                }
            } else if (exportMaskLabel.contains("UserCreated")){
                if ((exportMask.getInitiators() != null) && exportMask.getInitiators().size() != 4) {
                        log.error("ExportMask with name {} has {} Initiators instead of 4", exportMaskLabel,
                                exportMask.getInitiators().size());
                    }
                if ((exportMask.getUserAddedInitiators() != null) && exportMask.getUserAddedInitiators().size() != 4) {
                        log.error("ExportMask with name {} has {} UserAddedInitiators instead of 4", exportMaskLabel,
                                exportMask.getUserAddedInitiators().size());
                    }
                if ((exportMask.getExistingInitiators() != null) && exportMask.getExistingInitiators().size() != 0) {
                        log.error("ExportMask with name {} has {} ExistingInitiators instead of 0", exportMaskLabel,
                                exportMask.getExistingInitiators().size());
                    }
            } else if (exportMaskLabel.contains("Existing")) {
                if (exportMaskLabel.contains("VMAX") || exportMaskLabel.contains("VPLEX")) {
                    if ((exportMask.getInitiators() != null) && exportMask.getInitiators().size() != 4) {
                        log.error("ExportMask with name {} has {} Initiators instead of 4", exportMaskLabel,
                                exportMask.getInitiators().size());
                    }
                    if ((exportMask.getUserAddedInitiators() != null) && exportMask.getUserAddedInitiators().size() != 4) {
                        log.error("ExportMask with name {} has {} UserAddedInitiators instead of 4", exportMaskLabel,
                                exportMask.getUserAddedInitiators().size());
                    }
                    if ((exportMask.getExistingInitiators() != null) && exportMask.getExistingInitiators().size() != 0) {
                        log.error("ExportMask with name {} has {} ExistingInitiators instead of 0", exportMaskLabel,
                                exportMask.getExistingInitiators().size());
                    }
                } else {
                    if ((exportMask.getExistingInitiators() != null) && exportMask.getExistingInitiators().size() != 4) {
                        log.error("ExportMask with name {} has {} Initiators instead of 4", exportMaskLabel,
                                exportMask.getExistingInitiators().size());
                    }
                }
            } else if (exportMaskLabel.contains("Mixed")){
                if (exportMaskLabel.contains("VMAX") || exportMaskLabel.contains("VPLEX")) {
                    if ((exportMask.getInitiators() != null) && exportMask.getInitiators().size() != 4) {
                        log.error("ExportMask with name {} has {} Initiators instead of 4", exportMaskLabel,
                                exportMask.getInitiators().size());
                    }
                    if ((exportMask.getUserAddedInitiators() != null) && exportMask.getUserAddedInitiators().size() != 4) {
                        log.error("ExportMask with name {} has {} UserAddedInitiators instead of 4", exportMaskLabel,
                                exportMask.getUserAddedInitiators().size());
                    }
                    if ((exportMask.getExistingInitiators() != null) && exportMask.getExistingInitiators().size() != 4) {
                        log.error("ExportMask with name {} has {} ExistingInitiators instead of 4", exportMaskLabel,
                                exportMask.getExistingInitiators().size());
                    }
                } else {
                    if ((exportMask.getExistingInitiators() != null) && exportMask.getExistingInitiators().size() != 6) {
                        log.error("ExportMask with name {} has {} Initiators instead of 6", exportMaskLabel,
                                exportMask.getExistingInitiators().size());
                    }
                }
            }
            else {
                log.error("Unknown ExportMask {}", exportMaskLabel);
            }
            log.info("Processed exportMask with name {}", exportMaskLabel);
            
        }
    }

}
