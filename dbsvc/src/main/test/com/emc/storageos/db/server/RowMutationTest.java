/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.CompositeColumnNameSerializer;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.impl.IndexColumnNameSerializer;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.google.common.collect.Sets;
import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.cql.CqlStatement;
import com.netflix.astyanax.cql.CqlStatementResult;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.util.TimeUUIDUtils;

public class RowMutationTest extends DbsvcTestBase {
	private RowMutator rowMutator;
	private ColumnFamily<String, CompositeColumnName> volumeCF;
	private ColumnFamily<String, CompositeColumnName> noExistCF;
	private ColumnFamily<String, IndexColumnName> indexCF;
    
    @Before
    public void setupTest() {
    	volumeCF = new ColumnFamily<String, CompositeColumnName>("Volume",
                StringSerializer.get(),
                CompositeColumnNameSerializer.get());
    	
    	indexCF = new ColumnFamily<String, IndexColumnName>("LabelPrefixIndex",
                StringSerializer.get(),
                IndexColumnNameSerializer.get());
    	
    	noExistCF = new ColumnFamily<String, CompositeColumnName>("no_exits_CF",
                StringSerializer.get(),
                CompositeColumnNameSerializer.get());
    	
        rowMutator = new RowMutator(((DbClientImpl)this.getDbClient()).getLocalContext().getKeyspace(), false);
    }
    
    @Test
    public void insertRecordAndIndexColumn() throws ConnectionException {
    	String rowKey = URIUtil.createId(Volume.class).toString();
    	String volumeLabel = "volume label";
    	
    	//insert data object
        ColumnListMutation<CompositeColumnName> columnList = rowMutator.getRecordColumnList(volumeCF, rowKey);
        columnList.putColumn(new CompositeColumnName("allocatedCapacity"), 20);
		columnList.putColumn(new CompositeColumnName("label"), volumeLabel);
        
        //insert related index
        rowMutator.getIndexColumnList(indexCF, "vo").
        	putColumn(new IndexColumnName("Volume", volumeLabel, volumeLabel, rowKey, rowMutator.getTimeUUID()), "");
        
        rowMutator.execute();
        
        //verify data object information
        Volume volume = (Volume)this.getDbClient().queryObject(URI.create(rowKey));
        Assert.assertNotNull(volume);
        Assert.assertEquals(volume.getAllocatedCapacity().longValue(), 20L);
        Assert.assertEquals(volume.getLabel(), volumeLabel);
        
        //verify index information
        CqlStatement statement = ((DbClientImpl)this.getDbClient()).getLocalContext().getKeyspace().prepareCqlStatement();
        String cql = String.format("select * from \"LabelPrefixIndex\" where key='%s' and column1='Volume' and column2='%s' and column3='%s' and column4='%s'", 
        		"vo", volumeLabel, volumeLabel, rowKey);
        CqlStatementResult result = statement.withCql(cql).execute().getResult();
		Rows<String, IndexColumnName> rows = result.getRows(indexCF);
        
        Assert.assertEquals(rows.size(), 1);
    }
    
    @Test
    public void insertRecordAndIndexColumnWithError() throws ConnectionException {
    	String rowKey = URIUtil.createId(Volume.class).toString();
    	String volumeLabel = "volume label";
    	
    	//insert data object
        ColumnListMutation<CompositeColumnName> columnList = rowMutator.getRecordColumnList(volumeCF, rowKey);
        columnList.putColumn(new CompositeColumnName("allocatedCapacity"), 20);
		columnList.putColumn(new CompositeColumnName("label"), volumeLabel);
        
        //insert related index
        rowMutator.getIndexColumnList(indexCF, "vo").
        	putColumn(new IndexColumnName("Volume", volumeLabel, volumeLabel, rowKey, rowMutator.getTimeUUID()), "");
        
        //insert error column
        ColumnListMutation<CompositeColumnName> no_columnList = rowMutator.getRecordColumnList(noExistCF, rowKey);
        no_columnList.putColumn(new CompositeColumnName("test"), 20);
        
        try {
			rowMutator.execute();
			Assert.fail();
		} catch (Exception e) {
			//expected exception
		}
        
        //no volume should be created
        Volume volume = (Volume)this.getDbClient().queryObject(URI.create(rowKey));
        Assert.assertNull(volume);

        //no index should be created
        CqlStatement statement = ((DbClientImpl)this.getDbClient()).getLocalContext().getKeyspace().prepareCqlStatement();
        String cql = String.format("select * from \"LabelPrefixIndex\" where key='%s' and column1='Volume' and column2='%s' and column3='%s' and column4='%s'", 
        		"vo", volumeLabel, volumeLabel, rowKey);
        CqlStatementResult result = statement.withCql(cql).execute().getResult();
		Rows<String, IndexColumnName> rows = result.getRows(indexCF);
        Assert.assertEquals(rows.size(), 0);
    }
    
    @Test
    public void insertDataObject() throws ConnectionException {
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
        this.getDbClient().updateObject(volume);
        
        Volume target = (Volume)this.getDbClient().queryObject(id);
        Assert.assertNotNull(target);
        Assert.assertEquals(target.getLabel(), volume.getLabel());
        Assert.assertEquals(target.getPool(), volume.getPool());
        Assert.assertEquals(target.getNativeGuid(), volume.getNativeGuid());
        Assert.assertEquals(target.getNativeId(), volume.getNativeId());
        Assert.assertEquals(target.getCompositionType(), volume.getCompositionType());
        Assert.assertEquals(target.getAllocatedCapacity(), volume.getAllocatedCapacity());
        Assert.assertEquals(target.getProvisionedCapacity(), volume.getProvisionedCapacity());
        
        CqlStatement statement = ((DbClientImpl)this.getDbClient()).getLocalContext().getKeyspace().prepareCqlStatement();
        String cql = String.format("select * from \"LabelPrefixIndex\" where key='%s' and column1='Volume' and column2='%s' and column3='%s' and column4='%s'", 
        		prefix.toLowerCase(), volume.getLabel().toLowerCase(), volume.getLabel(), volume.getId().toString());
        CqlStatementResult result = statement.withCql(cql).execute().getResult();
		Rows<String, IndexColumnName> rows = result.getRows(indexCF);
        
        Assert.assertEquals(rows.size(), 1);        
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
        OperationResult<ColumnList<CompositeColumnName>> result =
                ((DbClientImpl)getDbClient()).getLocalContext().getKeyspace().prepareQuery(doType.getCF())
                        .getKey(volume.getId().toString())
                        .execute();
        
        List<Long> columnTimeUUIDStamps = new ArrayList<Long>(); 
        for (Column<CompositeColumnName> column : result.getResult()) {
            if (column.getName().getTimeUUID() != null) {
                columnTimeUUIDStamps.add(TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID()));
            }
        }
        
        Collections.sort(columnTimeUUIDStamps);
        
        for (int i = 1; i < columnTimeUUIDStamps.size(); i++) {
            Assert.assertEquals(1, columnTimeUUIDStamps.get(i) - columnTimeUUIDStamps.get(i - 1));
        }
    }
}
