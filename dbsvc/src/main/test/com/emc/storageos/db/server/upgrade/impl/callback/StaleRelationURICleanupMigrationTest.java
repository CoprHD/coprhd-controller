package com.emc.storageos.db.server.upgrade.impl.callback;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.upgrade.callbacks.StaleRelationURICleanupMigration;
import com.emc.storageos.db.server.DbsvcTestBase;

public class StaleRelationURICleanupMigrationTest extends DbsvcTestBase{
    private StaleRelationURICleanupMigration callback;
    
    @Before
    public void setUp() throws Exception {
        callback = new StaleRelationURICleanupMigration();
    }

    @Test
    public void testForExportMask() {
        
    }
    
    private <T extends DataObject> List<URI> createDataObject(Class<T> clazz, int count) throws InstantiationException, IllegalAccessException {
        List<URI> result = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            DataObject dataObject = clazz.newInstance();
            dataObject.setId(URIUtil.createId(clazz));
            dataObject.setLabel("Label for " + dataObject.getId());
            getDbClient().updateObject(dataObject);
            
            result.add(dataObject.getId());
        }
        
        return result;
    }
}
