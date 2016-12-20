/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import com.beust.jcommander.internal.Lists;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DbKeyspace;
import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;
import com.emc.storageos.db.client.model.SchemaRecord;
import com.emc.storageos.db.client.upgrade.DuplicatedIndexCFDetector;
import com.emc.storageos.db.client.upgrade.DuplicatedIndexCFDetector.DuplciatedIndexDataObject;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.client.upgrade.callbacks.RebuildIndexDuplicatedCFNameMigration;
import com.emc.storageos.db.common.schema.DataObjectSchema;
import com.emc.storageos.db.common.schema.DbSchemas;
import com.emc.storageos.db.server.DbsvcTestBase;

public class RebuildIndexDuplicatedCFNameMigrationTest extends DbsvcTestBase {
    private static final long TIME_STAMP_3_5 = 1480318877880000L;
    private static final long TIME_STAMP_3_0 = 1480318251194000L;
    private static final long TIME_STAMP_2_4_1 = 1480317537505000L;
    
    private InternalDbClient mockDbClient;
    private RebuildIndexDuplicatedCFNameMigration target;
    private DuplicatedIndexCFDetector detector;
    
    @Before
    public void setUp() throws Exception {
        mockDbClient = mock(InternalDbClient.class);
        detector = new DuplicatedIndexCFDetector();
    }

    @Test
    public void testProcess() throws Exception {
        Map<Long, String> timeStampVersionMap = new TreeMap<Long, String>();
        timeStampVersionMap.put(TIME_STAMP_3_0, "3.0");
        timeStampVersionMap.put(TIME_STAMP_2_4_1, "2.4.1");
        timeStampVersionMap.put(TIME_STAMP_3_5, "3.5");
        doReturn(timeStampVersionMap).when(mockDbClient).querySchemaVersions();
        
        SchemaRecord schemaRecord = new SchemaRecord();
        schemaRecord.setVersion("3.5");
        schemaRecord.setSchema(IOUtils.toString(getClass().getResourceAsStream("/com/emc/storageos/db/server/upgrade/impl/callback/schema_data_for_test.xml")));
        doReturn(schemaRecord).when(mockDbClient).querySchemaRecord("3.5");
        
        target = spy(new RebuildIndexDuplicatedCFNameMigration());
        doReturn(mockDbClient).when(target).getDbClient();
        doReturn(0).when(target).handleDataObjectClass(any(String.class), any(List.class));
        
        target.process();
        
        //there are 50 duplicated index CF in schema_data_for_test.xml
        verify(target, times(50)).handleDataObjectClass(any(String.class), any(List.class));
    }
    
    @Test
    public void testHandleDataObjectClassGlobalModelClass() {
        
        @Cf("GlobalDummyDataObject")
        @DbKeyspace(Keyspaces.GLOBAL)
        class GlobalDummyDataObject extends DataObject {
            
        }
        
        DuplciatedIndexDataObject duplciatedIndexDataObject = new DuplciatedIndexDataObject();
        duplciatedIndexDataObject.setClassName(GlobalDummyDataObject.class.getName());
        
        target = spy(new RebuildIndexDuplicatedCFNameMigration());
        doReturn(mockDbClient).when(target).getDbClient();
        
        try {
            target.handleDataObjectClass(GlobalDummyDataObject.class.getName(), Arrays.asList(duplciatedIndexDataObject));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }
    
    @Test
    public void testAltIdDbIndex() throws Exception {
        AutoTieringPolicy testData = new AutoTieringPolicy();
        testData.setId(URIUtil.createId(AutoTieringPolicy.class));
        testData.setNativeGuid("duplicated_value");
        testData.setPolicyName("duplicated_value");
        
        //reset row mutator time stamp offset to 0 to generate issue
        resetRowMutatorTimeStampOffSet(0);
        getDbClient().updateObject(testData);
        resetRowMutatorTimeStampOffSet(1);
        
        DbSchemas dbSchemas = new DbSchemas();
        dbSchemas.addSchema(new DataObjectSchema(AutoTieringPolicy.class));
        
        List<DuplciatedIndexDataObject> result = detector.findDuplicatedIndexCFNames(dbSchemas);
        
        target = new RebuildIndexDuplicatedCFNameMigration();
        target.setDbClient(getDbClient());
        try {
            int totalProcessedIndex = target.handleDataObjectClass(AutoTieringPolicy.class.getName(), result);
            assertEquals(1, totalProcessedIndex);
            
            AutoTieringPolicy targetData = (AutoTieringPolicy)getDbClient().queryObject(testData.getId());
            assertEquals(testData.getNativeGuid(), targetData.getNativeGuid());
            assertEquals(testData.getPolicyName(), targetData.getPolicyName());
            
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }
    
    private void resetRowMutatorTimeStampOffSet(int newValue) throws Exception {
        Field field = RowMutator.class.getDeclaredField("TIME_STAMP_OFFSET");
        field.setAccessible(true);
        field.set(null, newValue);
    }
}
