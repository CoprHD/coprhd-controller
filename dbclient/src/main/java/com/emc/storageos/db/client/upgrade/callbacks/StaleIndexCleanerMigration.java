package com.emc.storageos.db.client.upgrade.callbacks;

import java.util.Collection;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.DbCheckerFileWriter;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper.CheckResult;
import com.emc.storageos.db.client.impl.DbConsistencyCheckerHelper.IndexAndCf;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

public class StaleIndexCleanerMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(StaleIndexCleanerMigration.class);
    
    @Override
    public void process() throws MigrationCallbackException {
        long beginTime = System.currentTimeMillis();
        
        DbConsistencyCheckerHelper checkHelper = new DbConsistencyCheckerHelper((DbClientImpl)getDbClient());
        Collection<IndexAndCf> idxCfs = checkHelper.getAllIndices().values();
        CheckResult checkResult = new CheckResult();
        
        try {
            for (IndexAndCf indexAndCf : idxCfs) {
                try {
                    checkHelper.checkIndexingCF(indexAndCf, false, checkResult);
                } catch (ConnectionException e) {
                    log.error("Failed to check stale index CF {}", indexAndCf, e);
                }
            }
        } finally {
            DbCheckerFileWriter.close();
            if (checkResult.getTotal() > 0) {
                log.info("Totally find {} stale index", checkResult.getTotal());
            }
            DurationFormatUtils.formatDurationHMS(System.currentTimeMillis() - beginTime);
        }
    }

}
