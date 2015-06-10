/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.Assert;

import org.junit.BeforeClass;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.InitiatorHostMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class DbInitiatorHostMigrationTest extends DbSimpleMigrationTestBase {

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {{
            add(new InitiatorHostMigration());
        }});
        
        DbsvcTestBase.setup();
    }
    
    @Override
    protected String getSourceVersion() {
        return "1.1";
    }

    @Override
    protected String getTargetVersion() {
        return "2.0";
    }

    private List<Initiator> oldInitiators = new ArrayList<Initiator>();
    private Set<String> newInitiators = new HashSet<String>();        
    private List<Initiator> oldSingleInitiators = new ArrayList<Initiator>();
    private Set<String> newSingleInitiators = new HashSet<String>();
    private URI missingMaskURI = null;
    
    @Override
    protected void prepareData() throws Exception {
        
        // Create 2 initiators with a host and 2 initiators with a null host. One initiator from each has the same port name as another initiator.
        // Create 2 export groups, one with initiators with a host and one with initiators without a host.
        Initiator oldInitiator00 = createInitiator(URI.create("null"), "10:00:00:00:00", "old-0");
        Initiator oldInitiator01 = createInitiator(URI.create("null"), "10:00:00:00:01", "old-1");
        Initiator newInitiator00 = createInitiator(URIUtil.createId(Host.class), "10:00:00:00:00", "new-0");
        Initiator newInitiator01 = createInitiator(URIUtil.createId(Host.class), "10:00:00:00:01", "new-1");

        newInitiators.add(newInitiator00.getId().toString());
        newInitiators.add(newInitiator01.getId().toString());
        
        oldInitiators.add(oldInitiator00);
        oldInitiators.add(oldInitiator01);
        createExportGroup(oldInitiators, "old-export");

        List<Initiator> validInitiators = new ArrayList<Initiator>();
        validInitiators.add(newInitiator00);
        validInitiators.add(newInitiator01);
        createExportGroup(validInitiators, "new-export");
        
        // Create an initiator with a host and an initiator without a host.
        // Create an export group with the initiator without the host.
        Initiator oldSingleInitiator = createInitiator(URI.create("null"), "10:00:00:00:99", "old-99");
        oldSingleInitiators.add(oldSingleInitiator);
        
        Initiator newSingleInitiator = createInitiator(URIUtil.createId(Host.class), "10:00:00:00:99", "new-99");
        newSingleInitiators.add(newSingleInitiator.getId().toString());

        createExportGroup(oldSingleInitiators, "old-exportWithSingleInitiator");
        
        // Create Export Group with mask that doesn't exist
        ExportGroup exportGroupMissingMask = createExportGroup(oldSingleInitiators, "exportGroupMissingMask");
        missingMaskURI = URIUtil.createId(ExportMask.class);
        exportGroupMissingMask.addExportMask(missingMaskURI);
        _dbClient.updateAndReindexObject(exportGroupMissingMask);

    }
    
    @Override
    protected void verifyResults() throws Exception {

        List<URI> list = _dbClient.queryByType(ExportGroup.class, true);
        int count = 0;
        
        Iterator<ExportGroup> objs = _dbClient.queryIterativeObjects(ExportGroup.class, list);
        while (objs.hasNext()) {
            ExportGroup exportGroup = objs.next();
            count++;
            if (exportGroup.getLabel().equalsIgnoreCase("old-export")) {
                Assert.assertEquals(newInitiators.size(), exportGroup.getInitiators().size());
                Assert.assertTrue(exportGroup.getInitiators().containsAll(newInitiators));
                validateExportMask(exportGroup.getExportMasks(), newInitiators);
            } else if (exportGroup.getLabel().equalsIgnoreCase("new-export")) {
                Assert.assertEquals(newInitiators.size(), exportGroup.getInitiators().size());
                Assert.assertTrue(exportGroup.getInitiators().containsAll(newInitiators));
                validateExportMask(exportGroup.getExportMasks(), newInitiators);
            } else if (exportGroup.getLabel().equalsIgnoreCase("old-exportWithSingleInitiator")) {
                Assert.assertEquals(newSingleInitiators.size(), exportGroup.getInitiators().size());
                Assert.assertTrue(exportGroup.getInitiators().containsAll(newSingleInitiators));
                validateExportMask(exportGroup.getExportMasks(), newSingleInitiators);
            } else if (exportGroup.getLabel().equalsIgnoreCase("exportGroupMissingMask")) {
                Assert.assertEquals(newSingleInitiators.size(), exportGroup.getInitiators().size());
                Assert.assertTrue(exportGroup.getInitiators().containsAll(newSingleInitiators));
                Assert.assertTrue(exportGroup.getExportMasks().size() == 2);
                exportGroup.removeExportMask(missingMaskURI);
                _dbClient.updateAndReindexObject(exportGroup);                
                Assert.assertTrue(exportGroup.getExportMasks().size() == 1);
                validateExportMask(exportGroup.getExportMasks(), newSingleInitiators);
            } else {
                Assert.fail("Found export group that wasn't created in this test");
            }
        }
        
        Assert.assertTrue("We should still have " + 4 + " " + ExportGroup.class.getSimpleName() + " after migration, not " + count, count == 4);
        
        // duplicate initiators should now be marked as inactive
        for (Initiator initiator : oldInitiators) {
            Initiator dbInitiator = _dbClient.queryObject(Initiator.class, initiator.getId());
            Assert.assertTrue(dbInitiator.getInactive());
        }
        for (Initiator initiator : oldSingleInitiators) {
            Initiator dbInitiator = _dbClient.queryObject(Initiator.class, initiator.getId());
            Assert.assertTrue(dbInitiator.getInactive());
        }
        
        // other valid initiators should be still active
        for (String id : newInitiators) {
            Initiator dbInitiator = _dbClient.queryObject(Initiator.class, URI.create(id));
            Assert.assertFalse(dbInitiator.getInactive());
        }
        for (String id : newSingleInitiators) {
            Initiator dbInitiator = _dbClient.queryObject(Initiator.class, URI.create(id));
            Assert.assertFalse(dbInitiator.getInactive());
        }

    }
    
    private Initiator createInitiator(URI host, String port, String label) {
        Initiator initiator = new Initiator();
        initiator.setId(URIUtil.createId(Initiator.class));
        initiator.setHost(host);
        initiator.setInitiatorPort(port);
        initiator.setLabel(label);
        _dbClient.createObject(initiator);
        return initiator;
    }
    
    private ExportGroup createExportGroup(List<Initiator> initiators, String label) {
        StringSet initiatorIds = new StringSet();
        for (Initiator initiator : initiators) {
            initiatorIds.add(initiator.getId().toString());
        }
        ExportGroup exportGroup = new ExportGroup();
        exportGroup.setId(URIUtil.createId(ExportGroup.class));
        exportGroup.setInitiators(initiatorIds);
        exportGroup.setLabel(label);
        exportGroup.setType(ExportGroupType.Host.toString());
        
        ExportMask mask = createExportMask(initiators);
        StringSet masks = new StringSet();
        masks.add(mask.getId().toString());
        exportGroup.setExportMasks(masks);
        
        _dbClient.createObject(exportGroup);
        return exportGroup;
    }
    
    private ExportMask createExportMask(List<Initiator> initiators) {
        StringSet initiatorIds = new StringSet();
        for (Initiator initiator : initiators) {
            initiatorIds.add(initiator.getId().toString());
        }
        StringMap userAdded = new StringMap();
        for (Initiator initiator : initiators) {
            userAdded.put(initiator.getInitiatorPort().replaceAll(":", ""), initiator.getId().toString());
        }
        StringSetMap zoningMap = new StringSetMap();
        StoragePort port = new StoragePort();
        port.setId(URIUtil.createId(StoragePort.class));
        StringSet portMap = new StringSet();
        portMap.add(port.getId().toString());
        
        zoningMap.put(initiators.get(0).getId().toString(), portMap);
        
        ExportMask mask = new ExportMask();
        mask.setId(URIUtil.createId(ExportMask.class));
        mask.setInitiators(initiatorIds);
        mask.setUserAddedInitiators(userAdded);
        mask.setZoningMap(zoningMap);
        _dbClient.createObject(mask);
        return mask;
    }
    
    private void validateExportMask(StringSet exportMaskIds, Set<String> initiators) {
        for (String exportMaskId : exportMaskIds) {
            ExportMask mask = _dbClient.queryObject(ExportMask.class, URI.create(exportMaskId));
            Assert.assertNotNull(mask);
            Assert.assertEquals(initiators.size(), mask.getInitiators().size());
            Assert.assertTrue(mask.getInitiators().containsAll(initiators));
            Assert.assertEquals(initiators.size(), mask.getUserAddedInitiators().size());
            Assert.assertTrue(mask.getUserAddedInitiators().values().containsAll(initiators));
            Assert.assertTrue(initiators.containsAll(mask.getZoningMap().keySet()));
        }
    }

}
