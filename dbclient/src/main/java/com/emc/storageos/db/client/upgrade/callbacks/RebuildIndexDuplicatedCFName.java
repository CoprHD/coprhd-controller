/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.SchemaRecord;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.DuplicatedIndexCFDetector;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.client.upgrade.DuplicatedIndexCFDetector.DuplciatedIndexDataObject;
import com.emc.storageos.db.client.upgrade.DuplicatedIndexCFDetector.IndexCFKey;
import com.emc.storageos.db.common.DbSchemaChecker;
import com.emc.storageos.db.common.schema.DbSchemas;
import com.emc.storageos.db.common.schema.FieldInfo;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class RebuildIndexDuplicatedCFName extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(RebuildIndexDuplicatedCFName.class);
    
    @Override
    public void process() throws MigrationCallbackException {
        log.info("Begin to run migration handler RebuildIndexDuplicatedCFName");
        
        InternalDbClient dbClient = (InternalDbClient)getDbClient();
        
        Map<Long, String> schemaVersions = dbClient.querySchemaVersions();
        String version = null;
        for (Entry<Long, String> entry : schemaVersions.entrySet()) {
            log.info("schema version information: {} {}", entry.getKey(), entry.getValue());
            version = entry.getValue();
        }
        
        log.info("query latest schema version {}", version);
        SchemaRecord schemaRecord = dbClient.querySchemaRecord(version);
        
        try (BufferedReader reader = new BufferedReader(new StringReader(schemaRecord.getSchema()))) {
            DbSchemas dbSchema = DbSchemaChecker.unmarshalSchemas(version, reader);
            
            DuplicatedIndexCFDetector detector = new DuplicatedIndexCFDetector();
            List<DuplciatedIndexDataObject> duplciatedIndexDataObjects = detector.findDuplicatedIndexCFNames(dbSchema);
            
            for (DuplciatedIndexDataObject duplciatedIndexDataObject : duplciatedIndexDataObjects) {
                for (Entry<IndexCFKey, List<FieldInfo>> entry : duplciatedIndexDataObject.getIndexFieldsMap().entrySet()) {
                    if (entry.getValue().size() > 1) {
                        duplciatedIndexDataObject.getIndexFieldsMap().put(entry.getKey(), entry.getValue());
                        log.info(duplciatedIndexDataObject.getClassName());
                        for (FieldInfo field : entry.getValue()) {
                            log.info(field.getName() + "[" + field.getType() + "]");
                        }
                        log.info("");
                    }
                }
            }
            
            System.out.println("Total Classes: " + duplciatedIndexDataObjects.size());
        } catch (IOException e) {
            log.error("Failed to fun migration handler RebuildIndexDuplicatedCFName {}", e);
        }
        
        log.info("Finish run migration handler RebuildIndexDuplicatedCFName");
    }

}
