/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbCheckerFileWriter;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper.CheckResult;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper.IndexAndCf;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.KeyspaceUtil;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

/**
 * This migration handler is for COP-27665
 * If we see an object URI in index table but never see it in object table, 
 * it's definitely a potential problem for NullPointerException.
 * So, create a new migration callback. Scan all index tables including DecommissionIndex, 
 * AggregatedIndex, RelationIndex etc, and delete invalid object URI
 */
public class StaleIndexCleanerMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(StaleIndexCleanerMigration.class);
    private static final String CLEANUP_COMMAND = "/opt/storageos/bin/cqlsh -k %s -f %s localhost %s";
    private DbConsistencyCheckerHelper checkHelper;
    
    @Override
    public void process() throws MigrationCallbackException {
        checkHelper = new DbConsistencyCheckerHelper((DbClientImpl)getDbClient());
        checkHelper.setDoubleConfirmed(false);
        Map<String, IndexAndCf> allIdxCfs = getAllIndexCFs();
        CheckResult checkResult = new CheckResult();
        
        try {
            for (IndexAndCf indexAndCf : allIdxCfs.values()) {
                try {
                    checkHelper.checkIndexingCF(indexAndCf, false, checkResult, true);
                } catch (ConnectionException e) {
                    log.error("Failed to check stale index CF {}", indexAndCf, e);
                }
            }
            
            ThreadPoolExecutor executor = checkHelper.getExecutor(); 
            executor.shutdown();
            try {
                if (!executor.awaitTermination(120, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                } 
            } catch (Exception e) {
                executor.shutdownNow();
            }
            
            log.info("Totally find {} stale index", checkResult.getTotal());
            if (checkResult.getTotal() > 0) {
                executeCleanupScripts();
            }
        } catch (Exception e) {
            log.error("failed to cleanup stale/invalid index:", e);
        } finally {
            DbCheckerFileWriter.close();
        }
    }

    private void executeCleanupScripts() {
        for (Entry<String, String> entry : DbCheckerFileWriter.getCleanupfileMap().entrySet()) {
            if (DbCheckerFileWriter.WriteType.STORAGEOS.name().equalsIgnoreCase(entry.getKey())) {
                execCleanupScript(String.format(CLEANUP_COMMAND, DbClientContext.LOCAL_KEYSPACE_NAME, entry.getValue(),
                        DbClientContext.DB_THRIFT_PORT));
                FileUtils.deleteQuietly(new File(entry.getValue()));
            }
        }
    }

    private Map<String, IndexAndCf> getAllIndexCFs() {
        Map<String, IndexAndCf> allIdxCfs = new TreeMap<>();
        for (DataObjectType objType : TypeMap.getAllDoTypes()) {
            if (KeyspaceUtil.isLocal(objType.getDataObjectClass())) {
                Map<String, IndexAndCf> idxCfs = checkHelper.getIndicesOfCF(objType);
                allIdxCfs.putAll(idxCfs);
            }
        }
        return allIdxCfs;
    }
    
    public void execCleanupScript(String command) {
        Exec.Result result = Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, command.split(" "));
        log.info("External command result: {}", result);
    }
}
