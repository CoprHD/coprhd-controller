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
        for (int i = 0; i < objectIds.length; i++) {
            rowMutator.insertRecordColumn("Volume", objectIds[i], new CompositeColumnName("One", "Two", "Three"), "test");
        }
                
        rowMutator.execute();
        
        for (int i = 0; i < objectIds.length; i++) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", objectIds[i]));
            assertEquals(result.one().getString("key"), objectIds[i]);
        }
    }
    
    @Test
    public void insertIndexes() throws Exception {
        for (int i = 0; i < indexIds.length; i++) {
            rowMutator.insertIndexColumn("AltIdIndex", indexIds[i], new IndexColumnName("One", "Two", "Three", "Four", null), "test");
        }
                
        rowMutator.execute();
        
        for (int i = 0; i < indexIds.length; i++) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"AltIdIndex\" where key='%s'", indexIds[i]));
            assertEquals(result.one().getString("key"), indexIds[i]);
        }
    }
    
    @Test
    public void insertRecordsAndIndexes() throws Exception {
        for (int i = 0; i < objectIds.length; i++) {
            rowMutator.insertRecordColumn("Volume", objectIds[i], new CompositeColumnName("One", "Two", "Three"), "test");
        }
        
        for (int i = 0; i < indexIds.length; i++) {
            rowMutator.insertIndexColumn("AltIdIndex", indexIds[i], new IndexColumnName("One", "Two", "Three", "Four", null), "test");
        }
                
        rowMutator.execute();
        
        for (int i = 0; i < objectIds.length; i++) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", objectIds[i]));
            assertEquals(result.one().getString("key"), objectIds[i]);
        }
        
        for (int i = 0; i < indexIds.length; i++) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"AltIdIndex\" where key='%s'", indexIds[i]));
            assertEquals(result.one().getString("key"), indexIds[i]);
        }
    }
    
    @Test
    public void insertErrorOccurs() throws Exception {
        rowMutator.insertRecordColumn("Volume", objectIds[0], new CompositeColumnName("One", "Two", "Three"), "test");
        rowMutator.deleteRecordColumn("NO-EXISTS-TABLE", "key", new CompositeColumnName("One", "Two", "Three"));
        rowMutator.insertRecordColumn("Volume", objectIds[1], new CompositeColumnName("One", "Two", "Three"), "test");
        
        try {
            rowMutator.execute();
            fail();
        } catch (Exception e) {
            //exception is expected
        }
        
        for (int i = 0; i < objectIds.length; i++) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", objectIds[i]));
            assertFalse(result.iterator().hasNext());
        }
    }
}
