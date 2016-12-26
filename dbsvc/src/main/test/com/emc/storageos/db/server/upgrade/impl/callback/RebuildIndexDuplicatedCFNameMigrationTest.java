/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;

import org.junit.Test;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.DbConsistencyChecker;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.upgrade.callbacks.RebuildIndexDuplicatedCFNameMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.util.TimeUUIDUtils;

public class RebuildIndexDuplicatedCFNameMigrationTest extends DbsvcTestBase {
    private RebuildIndexDuplicatedCFNameMigration target;
    
    @Test
    public void testHandleDataObjectClass() throws Exception {
        DataObjectType doType = TypeMap.getDoType(FileShare.class);
        for (int i = 0; i < 5; i++) {
            FileShare testData = new FileShare();
            testData.setId(URIUtil.createId(FileShare.class));
            testData.setPath("duplicated_value" + i);
            testData.setMountPath("duplicated_value" + i);
            
            getDbClient().updateObject(testData);
        }
        
        //create data object whose index are neede to be rebuild
    	resetRowMutatorTimeStampOffSet(0);
    	FileShare[] testDataArray = new FileShare[10];
        for (int i = 0; i < 10; i++) {
        	FileShare testData = new FileShare();
        	testData.setId(URIUtil.createId(FileShare.class));
            testData.setPath("duplicated_value" + i);
            testData.setMountPath("duplicated_value" + i);
            
            testDataArray[i] = testData;
            getDbClient().updateObject(testData);
        }
        
        resetRowMutatorTimeStampOffSet(1);
        
        target = new RebuildIndexDuplicatedCFNameMigration();
        target.setDbClient(getDbClient());
        target.process();
        assertEquals(testDataArray.length, target.getTotalProcessedIndexCount());
        
        for (FileShare testData : testDataArray) {
	        FileShare targetData = (FileShare)getDbClient().queryObject(testData.getId());
	        assertEquals(testData.getPath(), targetData.getPath());
	        assertEquals(testData.getMountPath(), targetData.getMountPath());
	        
	    	OperationResult<ColumnList<CompositeColumnName>> result =
	    			((DbClientImpl)getDbClient()).getLocalContext().getKeyspace().prepareQuery(doType.getCF())
	                        .getKey(testData.getId().toString())
	                        .execute();
	        
	        long pathTime = 0;
	        long mountPathTime = 0;
	        
			for (Column<CompositeColumnName> column : result.getResult()) {
				if (column.getName().getOne().equals("path")) {
					pathTime = TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID());
				} else if (column.getName().getOne().equals("mountPath")) {
					mountPathTime = TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID());
				}
			}
	        
	        assertEquals(1, Math.abs(pathTime - mountPathTime));
        }
    }
    
    private void resetRowMutatorTimeStampOffSet(int newValue) throws Exception {
        Field field = RowMutator.class.getDeclaredField("TIME_STAMP_OFFSET");
        field.setAccessible(true);
        field.set(null, newValue);
    }
}