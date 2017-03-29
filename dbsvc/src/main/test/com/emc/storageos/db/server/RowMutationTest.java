/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.utils.UUIDs;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.google.common.collect.Sets;

public class RowMutationTest extends DbsvcTestBase {
	private RowMutator rowMutator;
	private String volumeCF = "Volume";
	private String noExistCF = "no_exists_CF";
	private String indexCF = "LabelPrefixIndex";
    
    @Before
    public void setupTest() {
    	rowMutator = new RowMutator(((DbClientImpl)getDbClient()).getLocalContext(), false);
    }
    
    @Test
    public void insertRecordAndIndexColumn() throws DriverException {
    	String rowKey = URIUtil.createId(Volume.class).toString();
    	String volumeLabel = "volume label";
    	
    	//insert data object
		rowMutator.insertRecordColumn(volumeCF, rowKey, new CompositeColumnName("allocatedCapacity"), 20L);
		rowMutator.insertRecordColumn(volumeCF, rowKey, new CompositeColumnName("label"), volumeLabel);
        
        //insert related index
        rowMutator.insertIndexColumn(indexCF, "vo", new IndexColumnName("Volume", volumeLabel, volumeLabel, rowKey, rowMutator.getTimeUUID()), "");
        
        rowMutator.execute();
        
        //verify data object information
        Volume volume = (Volume)getDbClient().queryObject(URI.create(rowKey));
        Assert.assertNotNull(volume);
        Assert.assertEquals(volume.getAllocatedCapacity().longValue(), 20L);
        Assert.assertEquals(volume.getLabel(), volumeLabel);
        
        //verify index information
        String cql = String.format("select * from \"LabelPrefixIndex\" where key='%s' and column1='Volume' and column2='%s' and column3='%s' and column4='%s'", 
        		"vo", volumeLabel, volumeLabel, rowKey);
        ResultSet resultSet = ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(cql);
        
        Assert.assertNotNull(resultSet.spliterator());
    }
    
    @Test
    public void insertRecordAndIndexColumnWithError() throws Exception {
    	String rowKey = URIUtil.createId(Volume.class).toString();
    	String volumeLabel = "volume label";
    	
    	//insert data object
		rowMutator.insertRecordColumn(volumeCF, rowKey, new CompositeColumnName("allocatedCapacity"), 20L);
		rowMutator.insertRecordColumn(volumeCF, rowKey, new CompositeColumnName("label"), volumeLabel);
        
        //insert related index
        rowMutator.insertIndexColumn(indexCF, "vo", new IndexColumnName("Volume", volumeLabel, volumeLabel, rowKey, rowMutator.getTimeUUID()), "");
        
        //insert error column
        Field field = rowMutator.getClass().getDeclaredField("atomicBatch");
        field.setAccessible(true);
        BatchStatement batchStatement = (BatchStatement)field.get(rowMutator);
        batchStatement.add(new SimpleStatement("insert into \"no_exits_cf\" (key, column1, value) VALUES('key', 'test', '')"));
        
        try {
			rowMutator.execute();
			Assert.fail();
		} catch (Exception e) {
			//expected exception
		}
        
        //no volume should be created
        Volume volume = (Volume)getDbClient().queryObject(URI.create(rowKey));
        Assert.assertNull(volume);

        //no index should be created
        String cql = String.format("select * from \"LabelPrefixIndex\" where key='%s' and column1='Volume' and column2='%s' and column3='%s' and column4='%s'", 
        		"vo", volumeLabel, volumeLabel, rowKey);
        ResultSet resultSet = ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(cql);
        Assert.assertNull(resultSet.one());
    }
    
    @Test
    public void insertDataObject() throws DriverException {
    	String prefix = "AA";
    	Volume volume = new Volume();
        URI id = URIUtil.createId(Volume.class);
        URI pool = URIUtil.createId(StoragePool.class);
        volume.setId(id);
        volume.setLabel(prefix + "volume");
        volume.setPool(pool);
        volume.setNativeGuid(prefix + "native_guid-2");
        volume.setNativeId(prefix + "native_id-2");
        volume.setCompositionType(prefix + "compositionType");
        volume.setInactive(false);
        volume.setAllocatedCapacity(1000L);
        volume.setProvisionedCapacity(2000L);
        getDbClient().updateObject(volume);
        
        Volume target = (Volume)getDbClient().queryObject(id);
        Assert.assertNotNull(target);
        Assert.assertEquals(target.getLabel(), volume.getLabel());
        Assert.assertEquals(target.getPool(), volume.getPool());
        Assert.assertEquals(target.getNativeGuid(), volume.getNativeGuid());
        Assert.assertEquals(target.getNativeId(), volume.getNativeId());
        Assert.assertEquals(target.getCompositionType(), volume.getCompositionType());
        Assert.assertEquals(target.getAllocatedCapacity(), volume.getAllocatedCapacity());
        Assert.assertEquals(target.getProvisionedCapacity(), volume.getProvisionedCapacity());
        
        String cql = String.format("select * from \"LabelPrefixIndex\" where key='%s' and column1='Volume' and column2='%s' and column3='%s' and column4='%s'", 
        		prefix.toLowerCase(), volume.getLabel().toLowerCase(), volume.getLabel(), volume.getId().toString());
        ResultSet resultSet = ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(cql);
        
        Assert.assertNotNull(resultSet.one());        
    }
    
    @Test
    public void testTimeUUID() throws Exception {
        Volume volume = new Volume();
        URI id1 = URIUtil.createId(Volume.class);
        URI pool1 = URIUtil.createId(StoragePool.class);
        volume.setId(id1);
        volume.setLabel("volume1");
        volume.setPool(pool1);
        volume.setInactive(false);
        volume.setAllocatedCapacity(1000L);
        volume.setProvisionedCapacity(2000L);
        volume.setAssociatedSourceVolume(URI.create("test"));
        volume.setVolumeGroupIds(new StringSet(Sets.newHashSet("v1", "v2")));
        getDbClient().updateObject(volume);
        
        DataObjectType doType = TypeMap.getDoType(Volume.class);
        String cql = String.format("select * from \"%s\" where key='%s'", doType.getCF().getName(), volume.getId().toString());
        ResultSet resultSet = ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(cql);
        
        Set<Long> columnTimeUUIDStamps = new HashSet<Long>(); 
        for (Row row : resultSet) {
            if (row.getUUID("column4") != null) {
            	long timestamp = UUIDs.unixTimestamp(row.getUUID("column4"));
                if (columnTimeUUIDStamps.contains(timestamp)) {
                	Assert.fail("timeuuid is duplicated");
                }
                columnTimeUUIDStamps.add(timestamp);
            }
        }
    }
}
