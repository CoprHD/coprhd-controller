/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.collections.SetUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.callbacks.StaleRelationURICleanupMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.google.common.collect.Maps;

public class StaleRelationURICleanupMigrationTest extends DbsvcTestBase {
    private StaleRelationURICleanupMigration callback;
    
    @Before
    public void setUp() throws Exception {
        callback = new StaleRelationURICleanupMigration();
        callback.setCoordinatorClient(_coordinator);
        callback.setDbClient(_dbClient);
    }

    @Test
    public void testForExportMask() throws Exception {
    	List<Initiator> existingInitiators = createActiveDataObject(Initiator.class, 2);
        Map<URI, Integer> existingVolumeMaps = createActiveDataObjectMap(Volume.class, 5);
        
        ExportMask exportMask = new ExportMask();
        exportMask.setId(URIUtil.createId(ExportMask.class));
        exportMask.setLabel("label");
        exportMask.setInactive(false);
        exportMask.addInitiators(createFakeDataObject(Initiator.class, 5));
        exportMask.addInitiators(createInactiveDataObject(Initiator.class, 5));
        exportMask.addInitiators(existingInitiators);
        exportMask.setStoragePorts(retrieveIDStrings(createFakeDataObject(StoragePort.class, 5)));
        exportMask.addVolumes(createFakeDataObjectMap(Volume.class, 5));
        exportMask.addVolumes(createInactiveDataObjectMap(Volume.class, 5));
		exportMask.addVolumes(existingVolumeMaps);
		for (String initiator : exportMask.getInitiators()) {
		    exportMask.addZoningMapEntry(initiator, new StringSet());
		}
		
        _dbClient.updateObject(exportMask);
        
        ExportMask target = _dbClient.queryObject(ExportMask.class, exportMask.getId());
        Assert.assertTrue(SetUtils.isEqualSet(target.getInitiators(), exportMask.getInitiators()));
        Assert.assertTrue(SetUtils.isEqualSet(target.getStoragePorts(), exportMask.getStoragePorts()));
        Assert.assertTrue(Maps.difference(target.getVolumes(), exportMask.getVolumes()).areEqual());
        callback.process();
        
        target = _dbClient.queryObject(ExportMask.class, exportMask.getId());
		Assert.assertEquals(existingInitiators.size(), target.getInitiators().size());
		for (Initiator initiator : existingInitiators) {
			Assert.assertTrue(target.getInitiators().contains(initiator.getId().toString()));
		}
		Assert.assertTrue(target.getStoragePorts() == null || target.getStoragePorts().isEmpty());
		Assert.assertEquals(existingVolumeMaps.size(), target.getVolumes().size());
		for (Entry<URI, Integer> entry : existingVolumeMaps.entrySet()) {
			Assert.assertEquals(entry.getValue().toString(), target.getVolumes().get(entry.getKey().toString()));
		}
		for (Initiator initiator : existingInitiators) {
            Assert.assertTrue(exportMask.getZoningMap().containsKey(initiator.getId().toString()));
        }
    }
    
    @Test
    public void testForExportGroup() throws Exception {
        List<Initiator> existingInitiators = createActiveDataObject(Initiator.class, 2);
        List<Host> existingHosts = createActiveDataObject(Host.class, 2);
        ExportMask exportMask = createActiveDataObject(ExportMask.class, 1).get(0);
        
        ExportGroup exportGroup = new ExportGroup();
        exportGroup.setId(URIUtil.createId(ExportGroup.class));
        exportGroup.setLabel("label");
        exportGroup.setInactive(false);
        exportGroup.addInitiators(retrieveIDURIs(createFakeDataObject(Initiator.class, 5)));
        exportGroup.addInitiators(retrieveIDURIs(createInactiveDataObject(Initiator.class, 5)));
        exportGroup.addInitiators(retrieveIDURIs(existingInitiators));
        
        exportGroup.addHosts(retrieveIDURIs(createFakeDataObject(Host.class, 1)));
        exportGroup.addHosts(retrieveIDURIs(createInactiveDataObject(Host.class, 1)));
        exportGroup.addHosts(retrieveIDURIs(existingHosts));
        
        exportGroup.addVolumes(createFakeDataObjectMap(Volume.class, 5));
        exportGroup.addVolumes(createInactiveDataObjectMap(Volume.class, 5));
        
        exportGroup.setSnapshots(new StringSet());
        exportGroup.getSnapshots().add(URIUtil.createId(BlockSnapshot.class).toString());
        
        exportGroup.addClusters(retrieveIDURIs(createFakeDataObject(Cluster.class, 5)));
        exportGroup.addClusters(retrieveIDURIs(createInactiveDataObject(Cluster.class, 5)));
        exportGroup.addExportMask(exportMask.getId()); 
        
        _dbClient.updateObject(exportGroup);
        
        ExportGroup target = _dbClient.queryObject(ExportGroup.class, exportGroup.getId());
        Assert.assertTrue(SetUtils.isEqualSet(exportGroup.getInitiators(), target.getInitiators()));
        Assert.assertTrue(SetUtils.isEqualSet(exportGroup.getHosts(), target.getHosts()));
        Assert.assertTrue(Maps.difference(exportGroup.getVolumes(), target.getVolumes()).areEqual());
        Assert.assertTrue(SetUtils.isEqualSet(exportGroup.getSnapshots(), target.getSnapshots()));
        Assert.assertTrue(SetUtils.isEqualSet(exportGroup.getClusters(), target.getClusters()));
        Assert.assertTrue(SetUtils.isEqualSet(exportGroup.getExportMasks(), target.getExportMasks()));
        
        callback.process();
        
        target = _dbClient.queryObject(ExportGroup.class, exportGroup.getId());
        Assert.assertEquals(existingInitiators.size(), target.getInitiators().size());
        for (Initiator initiator : existingInitiators) {
            Assert.assertTrue(target.getInitiators().contains(initiator.getId().toString()));
        }
        
        Assert.assertEquals(existingHosts.size(), target.getHosts().size());
        for (Host host : existingHosts) {
            Assert.assertTrue(target.getHosts().contains(host.getId().toString()));
        }
        
        Assert.assertTrue(target.getSnapshots() == null || target.getSnapshots().isEmpty());
        Assert.assertTrue(target.getClusters() == null || target.getClusters().isEmpty());
        Assert.assertEquals(1, target.getExportMasks().size());
        Assert.assertEquals(exportMask.getId().toString(), target.getExportMasks().toArray()[0]);
        
        Assert.assertTrue(target.getVolumes() ==null || target.getVolumes().isEmpty());
    }
    
    @Test
    public void testForExportGroup2() throws Exception{
        Map<URI, Integer> existingVolumeMaps = createActiveDataObjectMap(Volume.class, 5);
        ExportGroup exportGroup = new ExportGroup();
        exportGroup.setId(URIUtil.createId(ExportGroup.class));
        exportGroup.setLabel("label");
        exportGroup.setInactive(false);
        exportGroup.addVolumes(existingVolumeMaps);
        
        _dbClient.updateObject(exportGroup);
        
        callback.process();
        
        exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroup.getId());
        Assert.assertEquals(existingVolumeMaps.size(), exportGroup.getVolumes().size());
        for (Entry<URI, Integer> entry : existingVolumeMaps.entrySet()) {
            Assert.assertEquals(entry.getValue().toString(), exportGroup.getVolumes().get(entry.getKey().toString()));
        }
    }
    
    private <T extends DataObject> List<T> createFakeDataObject(Class<T> clazz, int count) throws InstantiationException, IllegalAccessException {
        List<T> result = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            T dataObject = clazz.newInstance();
            dataObject.setId(URIUtil.createId(clazz));
            dataObject.setLabel("Label for " + dataObject.getId());
            dataObject.setInactive(false);
            result.add(dataObject);
        }
        
        return result;
    }
    
    private <T extends DataObject> List<T> createActiveDataObject(Class<T> clazz, int count) throws InstantiationException, IllegalAccessException {
        List<T> result = createFakeDataObject(clazz, count);
        _dbClient.updateObject(result);
        return result;
    }
    
    private <T extends DataObject> List<T> createInactiveDataObject(Class<T> clazz, int count) throws InstantiationException, IllegalAccessException {
        List<T> result = createActiveDataObject(clazz, count);
        _dbClient.markForDeletion(result);
        return result;
    }
    
    private <T extends DataObject> Map<URI, Integer> createFakeDataObjectMap(Class<T> clazz, int count) throws InstantiationException, IllegalAccessException {
        List<T> dataObjects = createFakeDataObject(clazz, count);
        return dataObjectList2Map(dataObjects);
    }
    
    private <T extends DataObject> Map<URI, Integer> createActiveDataObjectMap(Class<T> clazz, int count) throws InstantiationException, IllegalAccessException {
        List<T> dataObjects = createActiveDataObject(clazz, count);
        return dataObjectList2Map(dataObjects);
    }
    
    private <T extends DataObject> Map<URI, Integer> createInactiveDataObjectMap(Class<T> clazz, int count) throws InstantiationException, IllegalAccessException {
        List<T> dataObjects = createActiveDataObject(clazz, count);
        _dbClient.markForDeletion(dataObjects);
        return dataObjectList2Map(dataObjects);
    }

	private <T extends DataObject> Map<URI, Integer> dataObjectList2Map(List<T> dataObjects) {
		Map<URI, Integer> result = new HashMap<>();
        for (T t : dataObjects) {
        	result.put(t.getId(), RandomUtils.nextInt());
        }
		return result;
	}
    
    private <T extends DataObject> List<String> retrieveIDStrings(List<T> dataObjects) {
    	List<String> result = new ArrayList<>();
    	
    	for (T t : dataObjects) {
    		result.add(t.getId().toString());
    	}
    	
    	return result;
    }
    
    private <T extends DataObject> List<URI> retrieveIDURIs(List<T> dataObjects) {
        List<URI> result = new ArrayList<>();
        
        for (T t : dataObjects) {
            result.add(t.getId());
        }
        
        return result;
    }
}
