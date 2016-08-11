/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import java.util.UUID;

import org.apache.cassandra.serializers.UTF8Serializer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.ConnectionException;
import com.datastax.driver.core.utils.UUIDs;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.impl.TimeSeriesType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.AuditLog;
import com.emc.storageos.db.client.model.AuditLogTimeSeries;
import com.emc.storageos.db.client.model.Volume;

public class AtomicBatchTest extends DbsvcTestBase{
    
    private RowMutator rowMutator;
    private String[] objectIds;
    private String[] indexIds;
    private String[] timeSeriesIds;
    private TimeSeriesType<AuditLog> timeSeriesType = TypeMap.getTimeSeriesType(AuditLogTimeSeries.class);
    
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
        
        timeSeriesIds = new String[3];
        for (int i = 0; i < timeSeriesIds.length; i++) {
            timeSeriesIds[i] = UUID.randomUUID().toString();
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
        rowMutator.insertIndexColumn("AltIdIndex", indexIds[0], new IndexColumnName("One", "Two", "Three", "Four", null), "test");
        rowMutator.deleteRecordColumn("NO-EXISTS-TABLE", "key", new CompositeColumnName("One", "Two", "Three"));
        
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
    
    @Test
    public void insertTimeSeriesColumn() {
        UUID[] uuids = new UUID[timeSeriesIds.length];
        for (int i = 0; i < timeSeriesIds.length; i++) {
            uuids[i] = UUIDs.timeBased();
            rowMutator.insertTimeSeriesColumn("AuditLogs", timeSeriesIds[i], uuids[i], "test", timeSeriesType.getTtl());
        }
        
        rowMutator.execute();
        
        for (int i = 0; i < timeSeriesIds.length; i++) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"AuditLogs\" where key='%s'", timeSeriesIds[i]));
            Row row = result.one();
            assertEquals(row.getString("key"), timeSeriesIds[i]);
            assertEquals(row.getUUID("column1"), uuids[i]);
        }
    }
    
    @Test
    public void insertTimeSeriesColumnWithError() {
        UUID[] uuids = new UUID[timeSeriesIds.length];
        for (int i = 0; i < timeSeriesIds.length; i++) {
            uuids[i] = UUIDs.timeBased();
            rowMutator.insertTimeSeriesColumn("AuditLogs", timeSeriesIds[i], uuids[i], "test", timeSeriesType.getTtl());
        }
        
        rowMutator.deleteRecordColumn("NO-EXISTS-TABLE", "key", new CompositeColumnName("One", "Two", "Three"));
        try {
            rowMutator.execute();
            fail();
        } catch (Exception e) {
            //exception is expected
        }
        
        for (int i = 0; i < timeSeriesIds.length; i++) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"AuditLogs\" where key='%s'", timeSeriesIds[i]));
            assertFalse(result.iterator().hasNext());
        }
    }
    
    @Test
    public void insertSchemaRecord() {
        String key = UUID.randomUUID().toString();
        String versions = "v1, v2, v3";
        String schemaColumn = "schema";
        
        rowMutator.insertSchemaRecord("SchemaRecord", key, schemaColumn, versions);
        rowMutator.execute();
        
        ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"SchemaRecord\" where key='%s'", key));
        Row row = result.one();
        assertEquals(row.getString("key"), key);
        assertEquals(row.getString("column1"), schemaColumn);
        assertEquals(UTF8Serializer.instance.deserialize(row.getBytes("value")), versions);
    }
    
    @Test
    public void testRetryFailedWriteWithLocalQuorum() {
        DbClientContext mockContext = mock(DbClientContext.class);
        Session session = mock(Session.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        BoundStatement bindStatement = mock(BoundStatement.class);
        
        doReturn(session).when(mockContext).getSession();
        doReturn(ConsistencyLevel.EACH_QUORUM).when(mockContext).getWriteConsistencyLevel();
        doCallRealMethod().when(mockContext).setWriteConsistencyLevel(any(ConsistencyLevel.class));
        doReturn(preparedStatement).when(mockContext).getPreparedStatement(anyString());
        doReturn(bindStatement).when(preparedStatement).bind();
        
        Mockito.when(session.execute(any(BatchStatement.class))).thenAnswer(new Answer() {
            
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                BatchStatement batch = (BatchStatement)args[0];
                if (batch.getConsistencyLevel() == ConsistencyLevel.EACH_QUORUM) {
                    throw new ConnectionException(new InetSocketAddress("localhost", 80), "message");
                }
                
                assertEquals(batch.getConsistencyLevel(), ConsistencyLevel.LOCAL_QUORUM);
                return null;
            }
            });
        
        RowMutator rowMutator = new RowMutator(mockContext, true);
        rowMutator.execute();
        
        verify(session, times(2)).execute(any(BatchStatement.class));
        
        doCallRealMethod().when(mockContext).getWriteConsistencyLevel();
        assertEquals(mockContext.getWriteConsistencyLevel(), ConsistencyLevel.LOCAL_QUORUM);
    }
}
