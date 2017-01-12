package com.emc.storageos.db.client.upgrade.callbacks;

import java.io.File;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.DbCheckerFileWriter;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper.CheckResult;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper.IndexAndCf;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

public class StaleIndexCleanerMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(StaleIndexCleanerMigration.class);
    private static final String CLEANUP_COMMAND = "/opt/storageos/bin/cqlsh -k %s -f \"%s\" localhost %s";
    
    @Override
    public void process() throws MigrationCallbackException {
        DbConsistencyCheckerHelper checkHelper = new DbConsistencyCheckerHelper((DbClientImpl)getDbClient());
        Collection<IndexAndCf> idxCfs = checkHelper.getAllIndices().values();
        CheckResult checkResult = new CheckResult();
        
        try {
            for (IndexAndCf indexAndCf : idxCfs) {
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
                for (Entry<String, String> entry : DbCheckerFileWriter.getCleanupfileMap().entrySet()) {
                    if (DbCheckerFileWriter.WriteType.STORAGEOS.name().equalsIgnoreCase(entry.getKey())) {
                        execCleanupScript(String.format(CLEANUP_COMMAND, DbClientContext.LOCAL_KEYSPACE_NAME, entry.getValue(),
                                DbClientContext.DB_THRIFT_PORT));
                    } else if (DbCheckerFileWriter.WriteType.GEOSTORAGEOS.name().equalsIgnoreCase(entry.getKey())) {
                        execCleanupScript(String.format(CLEANUP_COMMAND, DbClientContext.GEO_KEYSPACE_NAME, entry.getValue(),
                                DbClientContext.GEODB_THRIFT_PORT));
                    }
                }
            }
        } catch (Exception e) {
            log.error("failed to cleanup stale/invalid index:", e);
        } finally {
            DbCheckerFileWriter.close();
        }
    }
    
    public void execCleanupScript(String command) {
        Exec.Result result = Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, command.split(" "));
        log.info("result code is {}",result.getExitValue());
    }
}
