/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;

import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.upgrade.callbacks.RebuildIndexDuplicatedCFNameMigration;
import com.emc.storageos.db.server.DbsvcTestBase;

public class RebuildIndexDuplicatedCFNameMigrationTest extends DbsvcTestBase {
    private RebuildIndexDuplicatedCFNameMigration target;
    
    @Before
    public void setUp() throws Exception {
    }
    
    @Test
    public void testAltIdDbIndex() throws Exception {
        FileShare testData = new FileShare();
        testData.setId(URIUtil.createId(FileShare.class));
        testData.setPath("duplicated_value");
        testData.setMountPath("duplicated_value");
        
        //reset row mutator time stamp offset to 0 to generate issue
        resetRowMutatorTimeStampOffSet(0);
        getDbClient().updateObject(testData);
        resetRowMutatorTimeStampOffSet(1);
        
        target = new RebuildIndexDuplicatedCFNameMigration();
        target.setDbClient(getDbClient());
        try {
            target.handleDataObjectClass(FileShare.class);
            assertEquals(1, target.getTotalProcessedIndexCount());
            
            FileShare targetData = (FileShare)getDbClient().queryObject(testData.getId());
            assertEquals(testData.getPath(), targetData.getPath());
            assertEquals(testData.getMountPath(), targetData.getMountPath());
            
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