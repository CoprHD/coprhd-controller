/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.AltIdDbIndex;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.CompositeColumnNameSerializer;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper.CheckResult;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper.IndexAndCf;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.impl.IndexColumnNameSerializer;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.serializers.CompositeRangeBuilder;
import com.netflix.astyanax.serializers.StringSerializer;

public class DbConsistencyCheckerHelperTest extends DbsvcTestBase {
    private static final long TIME_STAMP_3_6 = 1480409489868000L;
    private static final long TIME_STAMP_3_5 = 1480318877880000L;
    private static final long TIME_STAMP_3_0 = 1480318251194000L;
    private static final long TIME_STAMP_2_4_1 = 1480317537505000L;
    private DbConsistencyCheckerHelper helper;
    
    @Before
    public void setUp() throws Exception {
        Map<Long, String> timeStampVersionMap = new TreeMap<Long, String>();
        timeStampVersionMap.put(TIME_STAMP_2_4_1, "2.4.1");
        timeStampVersionMap.put(TIME_STAMP_3_0, "3.0");
        timeStampVersionMap.put(TIME_STAMP_3_5, "3.5");
        timeStampVersionMap.put(TIME_STAMP_3_6, "3.6");
        
        helper = new DbConsistencyCheckerHelper((DbClientImpl)getDbClient()) {

            @Override
            protected Map<Long, String> querySchemaVersions() {
                return timeStampVersionMap;
            }
            
        };
    }
    
    @After
    public void cleanup() throws Exception {
        cleanupDataObjectCF(FileShare.class);
    }
    
    @Test
    public void testFindDataCreatedInWhichDBVersion() {
        assertEquals("Unknown", helper.findDataCreatedInWhichDBVersion(null));
        assertEquals("Unknown",
                helper.findDataCreatedInWhichDBVersion(ThreadLocalRandom.current().nextLong(Long.MIN_VALUE, TIME_STAMP_2_4_1)));
        assertEquals("2.4.1",
                helper.findDataCreatedInWhichDBVersion(ThreadLocalRandom.current().nextLong(TIME_STAMP_2_4_1, TIME_STAMP_3_0)));
        assertEquals("3.0",
                helper.findDataCreatedInWhichDBVersion(ThreadLocalRandom.current().nextLong(TIME_STAMP_3_0, TIME_STAMP_3_5)));
        assertEquals("3.5",
                helper.findDataCreatedInWhichDBVersion(ThreadLocalRandom.current().nextLong(TIME_STAMP_3_5, TIME_STAMP_3_6)));
        assertEquals("3.6",
                helper.findDataCreatedInWhichDBVersion(ThreadLocalRandom.current().nextLong(TIME_STAMP_3_6, Long.MAX_VALUE)));
    }
    
    @Test
    public void testCheckCFIndexing() throws Exception {
        ColumnFamily<String, CompositeColumnName> cf = new ColumnFamily<String, CompositeColumnName>("FileShare",
                StringSerializer.get(),
                CompositeColumnNameSerializer.get());
        ColumnFamily<String, IndexColumnName> indexCF = new ColumnFamily<String, IndexColumnName>(
                "AltIdIndex", StringSerializer.get(), IndexColumnNameSerializer.get());
        
        Keyspace keyspace = ((DbClientImpl)getDbClient()).getLocalContext().getKeyspace();
        
        FileShare testData = new FileShare();
        testData.setId(URIUtil.createId(FileShare.class));
        testData.setPath("A1");
        testData.setMountPath("A2");
        getDbClient().updateObject(testData);
        
        keyspace.prepareQuery(indexCF).withCql(String.format(
                "delete from \"AltIdIndex\" where key='%s'", "A1")).execute();
        
        CheckResult checkResult = new CheckResult();
        helper.checkCFIndices(TypeMap.getDoType(FileShare.class), false, checkResult);
        assertEquals(1, checkResult.getTotal());
        
        keyspace.prepareQuery(indexCF).withCql(String.format(
                "delete from \"AltIdIndex\" where key='%s'", "A2")).execute();
        
        checkResult = new CheckResult();
        helper.checkCFIndices(TypeMap.getDoType(FileShare.class), false, checkResult);
        assertEquals(2, checkResult.getTotal());
        
        helper = new DbConsistencyCheckerHelper((DbClientImpl)getDbClient()) {

            @Override
            protected boolean isDataObjectRemoved(Class<? extends DataObject> clazz, String key) {
                return true;
            }                        
        };
        
        checkResult = new CheckResult();
        helper.checkCFIndices(TypeMap.getDoType(FileShare.class), false, checkResult);
        assertEquals(0, checkResult.getTotal());
    }
    
    @Test
    public void testCheckIndexingCF() throws Exception {
        ColumnFamily<String, CompositeColumnName> cf = new ColumnFamily<String, CompositeColumnName>("FileShare",
                StringSerializer.get(),
                CompositeColumnNameSerializer.get());
        
        FileShare testData = new FileShare();
        testData.setId(URIUtil.createId(FileShare.class));
        testData.setPath("path1");
        testData.setMountPath("mountPath1");
        getDbClient().updateObject(testData);
        
        Keyspace keyspace = ((DbClientImpl)getDbClient()).getLocalContext().getKeyspace();
        
        //delete data object
        MutationBatch mutationBatch = keyspace.prepareMutationBatch();
        mutationBatch.withRow(cf, testData.getId().toString()).delete();
        mutationBatch.execute();

        CheckResult checkResult = new CheckResult();
        ColumnFamily<String, IndexColumnName> indexCF = new ColumnFamily<String, IndexColumnName>(
                "AltIdIndex", StringSerializer.get(), IndexColumnNameSerializer.get());
        
        //find inconsistency: index exits but data object is deleted
        IndexAndCf indexAndCf = new IndexAndCf(AltIdDbIndex.class, indexCF, keyspace);
        helper.checkIndexingCF(indexAndCf, false, checkResult);
        
        assertEquals(2, checkResult.getTotal());
        
        testData = new FileShare();
        testData.setId(URIUtil.createId(FileShare.class));
        testData.setPath("path2");
        testData.setMountPath("mountPath2");
        getDbClient().updateObject(testData);
        
        //create duplicated index
        keyspace.prepareQuery(indexCF)
                .withCql(String.format(
                        "INSERT INTO \"AltIdIndex\" (key, column1, column2, column3, column4, column5, value) VALUES ('pa', 'FileShare', '%s', '', '', now(), intasblob(10));",
                        testData.getId().toString()))
                .execute();
        
        checkResult = new CheckResult();
        helper.checkIndexingCF(indexAndCf, false, checkResult);
        assertEquals(3, checkResult.getTotal());
    }
    
    @Test
    public void testIsIndexExists() throws Exception{
        FileShare testData = new FileShare();
        testData.setId(URIUtil.createId(FileShare.class));
        testData.setPath("path1");
        testData.setMountPath("mountPath1");
        getDbClient().updateObject(testData);
        
        ColumnFamily<String, IndexColumnName> indexCF = new ColumnFamily<String, IndexColumnName>(
                "AltIdIndex", StringSerializer.get(), IndexColumnNameSerializer.get());
        Keyspace keyspace = ((DbClientImpl)getDbClient()).getLocalContext().getKeyspace();
        
        CompositeRangeBuilder builder = IndexColumnNameSerializer.get().buildRange();
        builder.withPrefix("FileShare").greaterThanEquals(testData.getId().toString()).lessThanEquals(testData.getId().toString());
        Rows<String, IndexColumnName> result = keyspace.prepareQuery(indexCF).getAllRows().withColumnRange(builder).execute().getResult();
        
        for (Row<String, IndexColumnName> row : result) {
            System.out.println(row.getColumns().getColumnByIndex(0).getName());
            assertTrue(helper.isIndexExists(keyspace, indexCF, row.getKey(), row.getColumns().getColumnByIndex(0).getName()));
        }
        
        ((DbClientImpl)getDbClient()).internalRemoveObjects(testData);
        for (Row<String, IndexColumnName> row : result) {
            assertFalse(helper.isIndexExists(keyspace, indexCF, row.getKey(), row.getColumns().getColumnByIndex(0).getName()));
        }
    }
}
