/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.datastax.driver.core.ResultSet;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.model.Volume;


public class AtomicBatchTest extends DbsvcTestBase{
    
    private RowMutator rowMutator;
    private String[] objectIds;
    private String[] indexIds;
    
    @Before
    public void setupTest() {
        rowMutator = new RowMutator(this.getDbClientContext(), false);
        
        objectIds = new String[3];
        for (int i = 0; i < objectIds.length; i++) {
            objectIds[i] = URIUtil.createId(Volume.class).toString();
        }
        
        indexIds = new String[3];
        for (int i = 0; i < indexIds.length; i++) {
            indexIds[i] = UUID.randomUUID().toString();
        }
    }
    
    @Test
    public void insertRecords() throws Exception {
        for (String objectId : objectIds) {
            rowMutator.insertRecordColumn("Volume", objectId, new CompositeColumnName("One", "Two", "Three"), "test");
        }
                
        rowMutator.execute();
        
        for (String objectId : objectIds) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", objectId));
            assertEquals(result.one().getString("key"), objectId);
        }
    }
    
    @Test
    public void insertIndexes() throws Exception {
        for (String indexId : indexIds) {
            rowMutator.insertIndexColumn("AltIdIndex", indexId, new IndexColumnName("One", "Two", "Three", "Four", null), "test");
        }
                
        rowMutator.execute();
        
        for (String indexId : indexIds) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"AltIdIndex\" where key='%s'", indexId));
            assertEquals(result.one().getString("key"), indexId);
        }
    }
    
    @Test
    public void insertRecordsAndIndexes() throws Exception {
        for (String objectId : objectIds) {
            rowMutator.insertRecordColumn("Volume", objectId, new CompositeColumnName("One", "Two", "Three"), "test");
        }
        
        for (int i = 0; i < indexIds.length; i++) {
            rowMutator.insertIndexColumn("AltIdIndex", indexIds[i], new IndexColumnName("One", "Two", "Three", "Four", null), "test");
        }
                
        rowMutator.execute();
        
        for (String objectId : objectIds) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", objectId));
            assertEquals(result.one().getString("key"), objectId);
        }
        
        for (String indexId : indexIds) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"AltIdIndex\" where key='%s'", indexId));
            assertEquals(result.one().getString("key"), indexId);
        }
    }
    
    @Test
    public void insertInsertRecordsErrorOccurs() throws Exception {
        rowMutator.insertRecordColumn("Volume", objectIds[0], new CompositeColumnName("One", "Two", "Three"), "test");
        rowMutator.deleteRecordColumn("NO-EXISTS-TABLE", "key", new CompositeColumnName("One", "Two", "Three"));
        rowMutator.insertRecordColumn("Volume", objectIds[1], new CompositeColumnName("One", "Two", "Three"), "test");
        
        try {
            rowMutator.execute();
            fail();
        } catch (Exception e) {
            //exception is expected
        }
        
        for (String objectId : objectIds) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", objectId));
            assertFalse(result.iterator().hasNext());
        }
    }
    
    @Test
    public void insertInsertIndexesErrorOccurs() throws Exception {
        rowMutator.insertIndexColumn("AltIdIndex", indexIds[0], new IndexColumnName("One", "Two", "Three", "Four", null), "test");
        rowMutator.deleteRecordColumn("NO-EXISTS-TABLE", "key", new CompositeColumnName("One", "Two", "Three"));
        rowMutator.insertIndexColumn("AltIdIndex", indexIds[1], new IndexColumnName("One", "Two", "Three", "Four", null), "test");
        
        try {
            rowMutator.execute();
            fail();
        } catch (Exception e) {
            //exception is expected
        }
        
        for (String indexId : indexIds) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"AltIdIndex\" where key='%s'", indexId));
            assertFalse(result.iterator().hasNext());
        }
    }
    
    @Test
    public void insertInsertIndexAndRecordErrorOccurs() throws Exception {
        rowMutator.insertRecordColumn("Volume", objectIds[0], new CompositeColumnName("One", "Two", "Three"), "test");
        rowMutator.deleteRecordColumn("NO-EXISTS-TABLE", "key", new CompositeColumnName("One", "Two", "Three"));
        rowMutator.insertIndexColumn("AltIdIndex", indexIds[0], new IndexColumnName("One", "Two", "Three", "Four", null), "test");
        
        try {
            rowMutator.execute();
            fail();
        } catch (Exception e) {
            //exception is expected
        }
        
        for (String indexId : indexIds) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"AltIdIndex\" where key='%s'", indexId));
            assertFalse(result.iterator().hasNext());
        }
        
        for (String objectId : objectIds) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", objectId));
            assertFalse(result.iterator().hasNext());
        }
    }
    
    @Test
    public void deleteRecordsWithError() {
        for (int i = 0; i < objectIds.length; i++) {
            rowMutator.insertRecordColumn("Volume", objectIds[i], new CompositeColumnName("One", "Two", "Three"), "test");
        }
        
        rowMutator.execute();
        
        rowMutator = new RowMutator(this.getDbClientContext(), false);
        rowMutator.deleteRecordColumn("Volume", objectIds[0], new CompositeColumnName("One", "Two", "Three"));
        rowMutator.deleteRecordColumn("NO-EXISTS-TABLE", "key", new CompositeColumnName("One", "Two", "Three"));
        
        try {
            rowMutator.execute();
            fail();
        } catch (Exception e) {
            //exception is expected
        }
        
        for (String objectId : objectIds) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", objectId));
            assertEquals(result.one().getString("key"), objectId);
        }
    }
    
    @Test
    public void deleteIndexesWithError() {
        for (int i = 0; i < indexIds.length; i++) {
            rowMutator.insertIndexColumn("AltIdIndex", indexIds[i], new IndexColumnName("One", "Two", "Three", "Four", null), "test");
        }
        
        rowMutator.execute();
        
        rowMutator = new RowMutator(this.getDbClientContext(), false);
        rowMutator.deleteIndexColumn("AltIdIndex", indexIds[0], new IndexColumnName("One", "Two", "Three", "Four", null));
        rowMutator.deleteRecordColumn("NO-EXISTS-TABLE", "key", new CompositeColumnName("One", "Two", "Three"));
        
        try {
            rowMutator.execute();
            fail();
        } catch (Exception e) {
            //exception is expected
        }
        
        for (String indexId : indexIds) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"AltIdIndex\" where key='%s'", indexId));
            assertEquals(result.one().getString("key"), indexId);
        }
    }
    
    @Test
    public void deleteRecordsAndIndexesWithError() throws Exception {
        for (String objectId : objectIds) {
            rowMutator.insertRecordColumn("Volume", objectId, new CompositeColumnName("One", "Two", "Three"), "test");
        }
        
        for (int i = 0; i < indexIds.length; i++) {
            rowMutator.insertIndexColumn("AltIdIndex", indexIds[i], new IndexColumnName("One", "Two", "Three", "Four", null), "test");
        }
                
        rowMutator.execute();
        
        rowMutator = new RowMutator(this.getDbClientContext(), false);
        rowMutator.deleteIndexColumn("AltIdIndex", indexIds[0], new IndexColumnName("One", "Two", "Three", "Four", null));
        rowMutator.deleteRecordColumn("Volume", objectIds[0], new CompositeColumnName("One", "Two", "Three"));
        rowMutator.deleteRecordColumn("NO-EXISTS-TABLE", "key", new CompositeColumnName("One", "Two", "Three"));
        
        for (String objectId : objectIds) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", objectId));
            assertEquals(result.one().getString("key"), objectId);
        }
        
        for (String indexId : indexIds) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"AltIdIndex\" where key='%s'", indexId));
            assertEquals(result.one().getString("key"), indexId);
        }
    }
}
