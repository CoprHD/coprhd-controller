/*
 * Copyright (c) 2016. EMC  Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.util.ArrayList; 
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.exceptions.ConnectionException;
import com.emc.storageos.db.client.impl.ColumnFamilyDefinition;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.impl.TimeSeriesIndexColumnName;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.TimeSeriesAlternateId;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class TimeSeriesIndexMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(TimeSeriesIndexMigration.class);
    public final static String SOURCE_INDEX_CF_NAME="TenantToOrder";
    public final static String SOURCE_INDEX_CF_NAME2="timeseriesIndex";
    public final static int TASK_SIZE=10000;
    private ColumnFamilyDefinition newIndexCF;

    class MigrationTask implements Runnable {
        private Row row;
        private RowMutator rowMutator;
        private DbClientContext dbClientContext;

        public MigrationTask(DbClientContext dbClientContext, Row row) {
            this.dbClientContext = dbClientContext;
            rowMutator = new RowMutator(dbClientContext, false);
            this.row = row;
        }

        @Override
        public void run() {
            try {
                migrationIndex(row);
                rowMutator.execute();
            }catch (ConnectionException e) {
                log.error("lby e=",e);
            }
        }

        private void migrationIndex(Row row) throws ConnectionException {
            String orderId = row.getString("column2");
            
            String queryString = String.format("select * from \"%s\" where key='Order' and column1='true' and column2='%s'", SOURCE_INDEX_CF_NAME2, orderId);
            ResultSet resultSet= dbClientContext.getSession().execute(queryString);
            UUID timeUUID = null;
            Row resultRow = resultSet.one();
            if (resultRow != null) {
                timeUUID = resultRow.getUUID("column5");
            }

            if (timeUUID == null) {
                log.error("The timeUUID null orderID={}", orderId);
                throw new NullPointerException();
            }

            TimeSeriesIndexColumnName newCol = new TimeSeriesIndexColumnName(Order.class.getSimpleName(), orderId, timeUUID);

            rowMutator.insertIndexColumn(newIndexCF.getName(), row.getString("key"), newCol, null);
        }
    }

    public TimeSeriesIndexMigration() {
        super();
    }

    @Override
    public void process() throws MigrationCallbackException {
        log.info("Adding new index records for class: {} field: {} annotation: {}",
                new Object[] { Order.class.getName(), Order.SUBMITTED, TimeSeriesAlternateId.class.getName()});

        DataObjectType doType = TypeMap.getDoType(Order.class);
        ColumnField field = doType.getColumnField(Order.SUBMITTED);
        newIndexCF = field.getIndexCF();

        DbClientImpl client = (DbClientImpl)dbClient;
        DbClientContext dbClientContext = client.getDbClientContext(Order.class);

        List<CompletableFuture<Void>> tasks = new ArrayList(TASK_SIZE);

        try {
        	SimpleStatement queryStatement = new SimpleStatement("select * from \"TenantToOrder\"");
            queryStatement.setFetchSize(1000);
        	ResultSet resultSet = dbClientContext.getSession().execute(queryStatement);
            
        	for (Row row : resultSet) {
        		if (tasks.size() < TASK_SIZE) {
                    CompletableFuture<Void> task =
                            CompletableFuture.runAsync(
                                    new MigrationTask(dbClientContext, row));
                    tasks.add(task);
                }else {
                    CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
                    tasks = new ArrayList(TASK_SIZE);
                }
        	}

            if (!tasks.isEmpty()) {
                CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
            }
        }catch (Exception e) {
            log.error("Migration to {} failed e=", newIndexCF.getName(), e);
        }
    }
}
