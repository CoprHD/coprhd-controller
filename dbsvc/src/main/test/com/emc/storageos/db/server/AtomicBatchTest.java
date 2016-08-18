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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
    private String[] indexIds;
    private String[] timeSeriesIds;
    private CompositeColumnName[] objects;
    private IndexColumnName[] indexes;
    private TimeSeriesType<AuditLog> timeSeriesType = TypeMap.getTimeSeriesType(AuditLogTimeSeries.class);
    
    @Before
    public void setupTest() {
        rowMutator = new RowMutator(this.getDbClientContext(), false);
        
        objects = new CompositeColumnName[3];
        for (int i = 0; i < objects.length; i++) {
            objects[i] = new CompositeColumnName(URIUtil.createId(Volume.class).toString(), "One", "Two", "Three", UUIDs.timeBased(), null);
        }
        
        indexIds = new String[3];
        indexes = new IndexColumnName[3];
        for (int i = 0; i < indexIds.length; i++) {
            indexIds[i] = UUID.randomUUID().toString();
            indexes[i] = new IndexColumnName("One", "Two", "Three", "Four", UUIDs.timeBased(), null);
        }
        
        timeSeriesIds = new String[3];
        for (int i = 0; i < timeSeriesIds.length; i++) {
            timeSeriesIds[i] = UUID.randomUUID().toString();
        }
    }
    
    @Test
    public void insertRecords() throws Exception {
        for (CompositeColumnName column : objects) {
            rowMutator.insertRecordColumn("Volume", column.getRowKey(), column, column.getRowKey());
        }
                
        rowMutator.execute();
        
        for (CompositeColumnName column : objects) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", column.getRowKey()));
            verifyDataObject(result.one(), column);
        }
    }
    
    @Test
    public void insertIndexes() throws Exception {
        for (int i = 0; i < indexIds.length; i++) {
            rowMutator.insertIndexColumn("AltIdIndex", indexIds[i], indexes[i], indexIds[i]);
        }
                
        rowMutator.execute();
        
        for (int i = 0; i < indexIds.length; i++) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"AltIdIndex\" where key='%s'", indexIds[i]));
            verifyIndexObject(result.one(), indexes[i], indexIds[i]);
        }
    }
    
    @Test
    public void insertRecordsAndIndexes() throws Exception {
        for (CompositeColumnName column : objects) {
            rowMutator.insertRecordColumn("Volume", column.getRowKey(), column, column.getRowKey());
        }
        
        for (int i = 0; i < indexIds.length; i++) {
            rowMutator.insertIndexColumn("AltIdIndex", indexIds[i], indexes[i], indexIds[i]);
        }
                
        rowMutator.execute();
        
        for (CompositeColumnName column : objects) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", column.getRowKey()));
            verifyDataObject(result.one(), column);
        }
        
        for (int i = 0; i < indexIds.length; i++) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"AltIdIndex\" where key='%s'", indexIds[i]));
            verifyIndexObject(result.one(), indexes[i], indexIds[i]);
        }
    }
    
    @Test
    public void insertRecordsErrorOccurs() throws Exception {
        rowMutator.insertRecordColumn("Volume", objects[0].getRowKey(), objects[0], objects[0].getRowKey());
        rowMutator.deleteRecordColumn("NO-EXISTS-TABLE", "key", new CompositeColumnName("One", "Two", "Three"));
        rowMutator.insertRecordColumn("Volume", objects[1].getRowKey(), objects[1], objects[1].getRowKey());
        
        try {
            rowMutator.execute();
            fail();
        } catch (Exception e) {
            //exception is expected
        }
        
        for (CompositeColumnName column : objects) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", column.getRowKey()));
            assertFalse(result.iterator().hasNext());
        }
    }
    
    @Test
    public void insertIndexesErrorOccurs() throws Exception {
        rowMutator.insertIndexColumn("AltIdIndex", indexIds[0], indexes[0], indexIds[0]);
        rowMutator.deleteRecordColumn("NO-EXISTS-TABLE", "key", new CompositeColumnName("One", "Two", "Three"));
        rowMutator.insertIndexColumn("AltIdIndex", indexIds[1], indexes[0], indexIds[0]);
        
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
    public void insertIndexAndRecordErrorOccurs() throws Exception {
        rowMutator.insertRecordColumn("Volume", objects[0].getRowKey(), objects[0], objects[0].getRowKey());
        rowMutator.insertIndexColumn("AltIdIndex", indexIds[0], indexes[0], indexIds[0]);
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
        
        for (CompositeColumnName column : objects) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", column.getRowKey()));
            assertFalse(result.iterator().hasNext());
        }
    }
    
    @Test
    public void deleteRecordsWithError() {
        for (CompositeColumnName column : objects) {
            rowMutator.insertRecordColumn("Volume", column.getRowKey(), column, column.getRowKey());
        }
        
        rowMutator.execute();
        
        rowMutator = new RowMutator(this.getDbClientContext(), false);
        rowMutator.deleteRecordColumn("Volume", objects[0].getRowKey(), objects[0]);
        rowMutator.deleteRecordColumn("NO-EXISTS-TABLE", "key", new CompositeColumnName("One", "Two", "Three"));
        
        try {
            rowMutator.execute();
            fail();
        } catch (Exception e) {
            //exception is expected
        }
        
        for (CompositeColumnName column : objects) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", column.getRowKey()));
            verifyDataObject(result.one(), column);
        }
    }
    
    @Test
    public void deleteIndexesWithError() {
        for (int i = 0; i < indexIds.length; i++) {
            rowMutator.insertIndexColumn("AltIdIndex", indexIds[i], indexes[i], indexIds[i]);
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
        
        for (int i = 0; i < indexIds.length; i++) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"AltIdIndex\" where key='%s'", indexIds[i]));
            verifyIndexObject(result.one(), indexes[i], indexIds[i]);
        }
    }
    
    @Test
    public void deleteRecordsAndIndexesWithError() throws Exception {
        for (CompositeColumnName column : objects) {
            rowMutator.insertRecordColumn("Volume", column.getRowKey(), column, column.getRowKey());
        }
        
        for (int i = 0; i < indexIds.length; i++) {
            rowMutator.insertIndexColumn("AltIdIndex", indexIds[i], indexes[i], indexIds[i]);
        }
                
        rowMutator.execute();
        
        rowMutator = new RowMutator(this.getDbClientContext(), false);
        rowMutator.deleteIndexColumn("AltIdIndex", indexIds[0], new IndexColumnName("One", "Two", "Three", "Four", null));
        rowMutator.deleteRecordColumn("Volume", objects[0].getRowKey(), objects[0]);
        rowMutator.deleteRecordColumn("NO-EXISTS-TABLE", "key", new CompositeColumnName("One", "Two", "Three"));
        
        for (CompositeColumnName column : objects) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"Volume\" where key='%s'", column.getRowKey()));
            verifyDataObject(result.one(), column);
        }
        
        for (int i = 0; i < indexIds.length; i++) {
            ResultSet result = this.getDbClientContext().getSession().execute(String.format("select * from \"AltIdIndex\" where key='%s'", indexIds[i]));
            verifyIndexObject(result.one(), indexes[i], indexIds[i]);
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
        
        RowMutator rowMutatorWithMock = new RowMutator(mockContext, true);
        rowMutatorWithMock.execute();
        
        verify(session, times(2)).execute(any(BatchStatement.class));
        
        doCallRealMethod().when(mockContext).getWriteConsistencyLevel();
        assertEquals(mockContext.getWriteConsistencyLevel(), ConsistencyLevel.LOCAL_QUORUM);
    }
    
    @Test
    public void testNoRetry() {
        DbClientContext mockContext = mock(DbClientContext.class);
        Session session = mock(Session.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        BoundStatement bindStatement = mock(BoundStatement.class);
        
        doReturn(session).when(mockContext).getSession();
        doReturn(ConsistencyLevel.EACH_QUORUM).when(mockContext).getWriteConsistencyLevel();
        doReturn(preparedStatement).when(mockContext).getPreparedStatement(anyString());
        doReturn(bindStatement).when(preparedStatement).bind();
        
        doThrow(new RuntimeException()).when(mockContext).setWriteConsistencyLevel(
                any(ConsistencyLevel.class));
        
        Mockito.when(session.execute(any(BatchStatement.class))).thenAnswer(new Answer() {
            
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                throw new ConnectionException(new InetSocketAddress("localhost", 80), "message");
            }
            });
        
        RowMutator rowMutatorWithMock = new RowMutator(mockContext, false);
        try {
            rowMutatorWithMock.execute();
            fail();
        } catch (Exception e) {
          //exception is expected
        }
        
        verify(session, times(1)).execute(any(BatchStatement.class));
    }
    
    private void verifyDataObject(Row row, CompositeColumnName column) {
        assertEquals(column.getRowKey(), row.getString("key"));
        assertEquals(column.getOne(), row.getString("column1"));
        assertEquals(column.getTwo(), row.getString("column2"));
        assertEquals(column.getThree(), row.getString("column3"));
        assertEquals(column.getTimeUUID(), row.getUUID("column4"));
        assertEquals(column.getRowKey(), UTF8Serializer.instance.deserialize(row.getBytes("value")));
    }
    
    private void verifyIndexObject(Row row, IndexColumnName column, String key) {
        assertEquals(key, row.getString("key"));
        assertEquals(column.getOne(), row.getString("column1"));
        assertEquals(column.getTwo(), row.getString("column2"));
        assertEquals(column.getThree(), row.getString("column3"));
        assertEquals(column.getFour(), row.getString("column4"));
        assertEquals(column.getTimeUUID(), row.getUUID("column5"));
        assertEquals(key, UTF8Serializer.instance.deserialize(row.getBytes("value")));
    }
}
