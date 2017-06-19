/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.datastax.driver.core.ResultSet;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.AltIdDbIndex;
import com.emc.storageos.db.client.impl.ClassNameTimeSeriesDBIndex;
import com.emc.storageos.db.client.impl.CompositeIndexColumnName;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper.CheckResult;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper.IndexAndCf;
import com.emc.storageos.db.client.impl.TimeSeriesDbIndex;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.uimodels.Order;

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
        cleanupDataObjectCF(Order.class);
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
        FileShare testData = new FileShare();
        testData.setId(URIUtil.createId(FileShare.class));
        testData.setPath("A1");
        testData.setMountPath("A2");
        getDbClient().createObject(testData);
        
        ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(String.format(
                "delete from \"AltIdIndex\" where key='%s'", "A1"));
        
        CheckResult checkResult = new CheckResult();
        helper.checkCFIndices(TypeMap.getDoType(FileShare.class), false, checkResult);
        assertEquals(1, checkResult.getTotal());
        
        ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(String.format(
                "delete from \"AltIdIndex\" where key='%s'", "A2"));
        
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
        
        testData = new FileShare();
        testData.setId(URIUtil.createId(FileShare.class));
        testData.setPath("A'A'");
        testData.setMountPath("A2");
        getDbClient().createObject(testData);
        
        checkResult = new CheckResult();
        helper.checkCFIndices(TypeMap.getDoType(FileShare.class), false, checkResult);
        assertEquals(0, checkResult.getTotal());
    }
    
    @Test
    public void testCheckIndexingCF_SkipRecordWithNoInactiveColumn() throws Exception {
    	ColumnFamily<String, CompositeColumnName> cf = new ColumnFamily<String, CompositeColumnName>("FileShare",
                StringSerializer.get(),
                CompositeColumnNameSerializer.get());
        
        FileShare testData = new FileShare();
        testData.setId(URIUtil.createId(FileShare.class));
        testData.setPath("path1");
        testData.setMountPath("mountPath1");
        getDbClient().createObject(testData);
        
        Keyspace keyspace = ((DbClientImpl)getDbClient()).getLocalContext().getKeyspace();
        keyspace.prepareQuery(cf)
                .withCql(String.format(
                        "delete from \"FileShare\" where key='%s' and column1='inactive'",
                        testData.getId().toString()))
                .execute();
        
        CheckResult checkResult = new CheckResult();
        helper.checkCFIndices(TypeMap.getDoType(FileShare.class), false, checkResult);
        assertEquals(0, checkResult.getTotal());
        
        testData = new FileShare();
        testData.setId(URIUtil.createId(FileShare.class));
        testData.setPath("path1");
        testData.setMountPath("mountPath1");
        getDbClient().createObject(testData);
        
        testData = (FileShare)getDbClient().queryObject(testData.getId());
        testData.setInactive(true);
        getDbClient().updateObject(testData);
        
        helper.checkCFIndices(TypeMap.getDoType(FileShare.class), false, checkResult);
        assertEquals(0, checkResult.getTotal());
    }
    
    @Test
    public void testCheckIndexingCF() throws Exception {
        FileShare testData = new FileShare();
        testData.setId(URIUtil.createId(FileShare.class));
        testData.setPath("path1");
        testData.setMountPath("mountPath1");
        getDbClient().createObject(testData);
        
        //delete data object
        ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(String.format(
                "delete from \"FileShare\" where key='%s'", testData.getId().toString()));

        CheckResult checkResult = new CheckResult();
        
        //find inconsistency: index exits but data object is deleted
        IndexAndCf indexAndCf = new IndexAndCf(AltIdDbIndex.class, "AltIdIndex", ((DbClientImpl)getDbClient()).getLocalContext());
        helper.checkIndexingCF(indexAndCf, false, checkResult);
        
        assertEquals(2, checkResult.getTotal());
        
        testData = new FileShare();
        testData.setId(URIUtil.createId(FileShare.class));
        testData.setPath("path2");
        testData.setMountPath("mountPath2");
        getDbClient().createObject(testData);
        
        //create duplicated index
        ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(String.format(
                        "INSERT INTO \"AltIdIndex\" (key, column1, column2, column3, column4, column5, value) VALUES ('pa', 'FileShare', '%s', '', '', now(), intasblob(10));",
                        testData.getId().toString()));
        
        checkResult = new CheckResult();
        helper.checkIndexingCF(indexAndCf, false, checkResult);
        assertEquals(3, checkResult.getTotal());
        
        ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute("TRUNCATE \"AltIdIndex\"");
        //test large columns for single row key
        for (int i = 0; i < 123; i++) {
        	((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(String.format(
                    "INSERT INTO \"AltIdIndex\" (key, column1, column2, column3, column4, column5, value) VALUES ('sa', 'FileShare', '%s', '', '', now(), intasblob(10));",
                    i));
        }
        
        checkResult = new CheckResult();
        helper.checkIndexingCF(indexAndCf, false, checkResult);
        assertEquals(123, checkResult.getTotal());
    }
    
    @Test
    public void testIsIndexExists() throws Exception{
        FileShare testData = new FileShare();
        testData.setId(URIUtil.createId(FileShare.class));
        testData.setPath("path1");
        testData.setMountPath("mountPath1");
        getDbClient().createObject(testData);
        
        String queryString = String.format("select * from \"AltIdIndex\" where column1='FileShare' and column2='%s' ALLOW FILTERING", testData.getId().toString());
        ResultSet resultSet = ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(queryString);
        
        //TODO java driver
        /*for (Row<String, IndexColumnName> row : result) {
            assertTrue(helper.isIndexExists(keyspace, indexCF, row.getKey(), row.getColumns().getColumnByIndex(0).getName()));
        }
        
        ((DbClientImpl)getDbClient()).internalRemoveObjects(testData);
        for (Row<String, IndexColumnName> row : result) {
            assertFalse(helper.isIndexExists(keyspace, indexCF, row.getKey(), row.getColumns().getColumnByIndex(0).getName()));
        }*/
    }
    
    @Test
    public void testClassNameTimeSeriesIndex() throws Exception {
        DbConsistencyCheckerHelperMock mockHelper = new DbConsistencyCheckerHelperMock((DbClientImpl)getDbClient());
        
        Order order = new Order();
        order.setId(URIUtil.createId(Order.class));
        order.setLabel("order1");
        order.setSubmittedByUserId("root");
        getDbClient().createObject(order);
        
        Order result = (Order)getDbClient().queryObject(order.getId());
        System.out.println(result.getLabel());
        IndexAndCf indexAndCf = new IndexAndCf(ClassNameTimeSeriesDBIndex.class, "UserToOrdersByTimeStamp", ((DbClientImpl)getDbClient()).getLocalContext());
        
        CheckResult checkResult = new CheckResult();
        mockHelper.checkIndexingCF(indexAndCf, false, checkResult);
        assertEquals(0, checkResult.getTotal());
        
        ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(String.format("delete from \"Order\" where key='%s'", order.getId()));
        checkResult = new CheckResult();
        mockHelper.checkIndexingCF(indexAndCf, false, checkResult);
        assertEquals(1, checkResult.getTotal());
        
        ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(mockHelper.getCleanIndexCQL());
        checkResult = new CheckResult();
        mockHelper.checkIndexingCF(indexAndCf, false, checkResult);
        assertEquals(0, checkResult.getTotal());
    }
    
    @Test
    public void testTimeSeriesAlternateId() throws Exception {
        DbConsistencyCheckerHelperMock mockHelper = new DbConsistencyCheckerHelperMock((DbClientImpl)getDbClient());
        
        Order order = new Order();
        order.setId(URIUtil.createId(Order.class));
        order.setLabel("order2");
        order.setTenant("tenant");
        order.setIndexed(true);
        getDbClient().createObject(order);
        
        IndexAndCf indexAndCf = new IndexAndCf(TimeSeriesDbIndex.class, "AllOrdersByTimeStamp", ((DbClientImpl)getDbClient()).getLocalContext());
        
        CheckResult checkResult = new CheckResult();
        mockHelper.checkIndexingCF(indexAndCf, false, checkResult);
        assertEquals(0, checkResult.getTotal());
        
        ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(String.format("delete from \"Order\" where key='%s'", order.getId()));
        checkResult = new CheckResult();
        mockHelper.checkIndexingCF(indexAndCf, false, checkResult);
        assertEquals(1, checkResult.getTotal());
        
        ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(mockHelper.getCleanIndexCQL());
        checkResult = new CheckResult();
        mockHelper.checkIndexingCF(indexAndCf, false, checkResult);
        assertEquals(0, checkResult.getTotal());
    }
    
    @Test
    public void testCFIndexForOrder() throws Exception {
        DbConsistencyCheckerHelperMock mockHelper = new DbConsistencyCheckerHelperMock((DbClientImpl)getDbClient());
        
        Order order = new Order();
        order.setId(URIUtil.createId(Order.class));
        order.setLabel("order2");
        order.setSubmittedByUserId("Tom");
        order.setTenant("urn:storageos:TenantOrg:128e0354-c26e-438b-b1e6-1a6ceaa9b380:global");
        order.setIndexed(true);
        getDbClient().createObject(order);
        
        CheckResult checkResult = new CheckResult();
        mockHelper.checkCFIndices(TypeMap.getDoType(Order.class), true, checkResult);
        assertEquals(0, checkResult.getTotal());
        
        ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(String.format("delete from \"UserToOrdersByTimeStamp\" where key='%s'", order.getSubmittedByUserId()));
        ((DbClientImpl)getDbClient()).getLocalContext().getSession().execute(String.format("delete from \"AllOrdersByTimeStamp\" where key='%s'", order.getTenant()));
        checkResult = new CheckResult();
        mockHelper.checkCFIndices(TypeMap.getDoType(Order.class), true, checkResult);
        assertEquals(2, checkResult.getTotal());
    }
    
    class DbConsistencyCheckerHelperMock extends DbConsistencyCheckerHelper {
        
        private String cleanIndexCQL = null;

        public DbConsistencyCheckerHelperMock(DbClientImpl dbClient) {
            super(dbClient);
        }

        @Override
        protected String generateCleanIndexCQL(IndexAndCf indexAndCf, IndexEntry idxEntry, UUID timeUUID,
                CompositeIndexColumnName compositeIndexColumnName) {
            cleanIndexCQL = super.generateCleanIndexCQL(indexAndCf, idxEntry, timeUUID, compositeIndexColumnName);
            return cleanIndexCQL;
        }

        public String getCleanIndexCQL() {
            return cleanIndexCQL;
        }

    };
}
