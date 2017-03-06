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

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.*;
import com.netflix.astyanax.query.RowQuery;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.util.RangeBuilder;
import com.netflix.astyanax.serializers.CompositeRangeBuilder;

import com.emc.storageos.db.client.impl.*;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.TimeSeriesAlternateId;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class TimeSeriesIndexMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(TimeSeriesIndexMigration.class);
    public final static String SOURCE_INDEX_CF_NAME="TenantToOrder";
    public final static String SOURCE_INDEX_CF_NAME2="timeseriesIndex";
    public final static int TASK_SIZE=10000;

    class MigrationTask implements Runnable {
        private ColumnList<IndexColumnName> columns;
        private String indexKey;
        private Keyspace keyspace;
        private MutationBatch mutationBatch;

        private ColumnFamily<String, IndexColumnName> tenantToOrder;
        private ColumnFamily<String, IndexColumnName> timeseriesIndex;
        private ColumnFamily<String, TimeSeriesIndexColumnName> newIndexCF;

        public MigrationTask(Keyspace ks, String indexKey, ColumnList<IndexColumnName> cols,
            ColumnFamily<String, IndexColumnName> tenantToOrder,
            ColumnFamily<String, IndexColumnName> timeseriesIndex,
            ColumnFamily<String, TimeSeriesIndexColumnName> newCF) {
            keyspace = ks;
            mutationBatch = keyspace.prepareMutationBatch();
            columns = cols;
            this.indexKey =indexKey;
            this.tenantToOrder = tenantToOrder;
            this.timeseriesIndex = timeseriesIndex;
            newIndexCF = newCF;
        }

        @Override
        public void run() {
            try {
                for (Column<IndexColumnName> col : columns) {
                    migrationIndex(col);
                }

                mutationBatch.execute();
            }catch (ConnectionException e) {
                log.error("lby e=",e);
            }
        }

        private void migrationIndex(Column<IndexColumnName> col) throws ConnectionException {
            String orderId = col.getName().getTwo();

            RowQuery<String, IndexColumnName> timeseriesIndexRowQuery = keyspace.prepareQuery(timeseriesIndex).getKey("Order");
            CompositeRangeBuilder builder = IndexColumnNameSerializer.get().buildRange().withPrefix("true");
            ColumnList<IndexColumnName> timeSeriesCol = timeseriesIndexRowQuery.withColumnRange(builder.greaterThanEquals(orderId)
                    .lessThanEquals(orderId).limit(1)).execute().getResult();
            UUID timeUUID = null;
            for (IndexColumnName c : timeSeriesCol.getColumnNames()) {
                timeUUID = c.getTimeUUID();
                break;
            }

            if (timeUUID == null) {
                log.error("The timeUUID null orderID={}", orderId);
                throw new NullPointerException();
            }

            TimeSeriesIndexColumnName newCol = new TimeSeriesIndexColumnName(Order.class.getSimpleName(), orderId, timeUUID);

            mutationBatch.withRow(newIndexCF, indexKey).putEmptyColumn(newCol, null);
        }
    }

    public TimeSeriesIndexMigration() {
        super();
    }

    @Override
    public void process() throws MigrationCallbackException {
        log.info("Adding new index records for class: {} field: {} annotation: {}",
                new Object[] { Order.class.getName(), Order.SUBMITTED, TimeSeriesAlternateId.class.getName()});

        ColumnFamily<String, IndexColumnName> tenantToOrder =
                new ColumnFamily<>(SOURCE_INDEX_CF_NAME, StringSerializer.get(), IndexColumnNameSerializer.get());

        ColumnFamily<String, IndexColumnName> timeseriesIndex =
                new ColumnFamily<>(SOURCE_INDEX_CF_NAME2, StringSerializer.get(), IndexColumnNameSerializer.get());

        DataObjectType doType = TypeMap.getDoType(Order.class);
        ColumnField field = doType.getColumnField(Order.SUBMITTED);
        ColumnFamily<String, TimeSeriesIndexColumnName> newIndexCF = field.getIndexCF();

        DbClientImpl client = (DbClientImpl)dbClient;
        Keyspace ks = client.getKeyspace(Order.class);

        List<CompletableFuture<Void>> tasks = new ArrayList(TASK_SIZE);

        try {
            OperationResult<Rows<String, IndexColumnName>> result = ks.prepareQuery(tenantToOrder).getAllRows()
                    .setRowLimit(1000)
                    .withColumnRange(new RangeBuilder().setLimit(0).build())
                    .execute();
            for (Row<String, IndexColumnName> row : result.getResult()) {
                RowQuery<String, IndexColumnName> rowQuery = ks.prepareQuery(tenantToOrder).getKey(row.getKey())
                        .autoPaginate(true)
                        .withColumnRange(new RangeBuilder().setLimit(5).build());
                ColumnList<IndexColumnName> cols = rowQuery.execute().getResult();
                while (!cols.isEmpty()) {
                    if (tasks.size() < TASK_SIZE) {
                        CompletableFuture<Void> task =
                                CompletableFuture.runAsync(
                                        new MigrationTask(ks, row.getKey(), cols, tenantToOrder, timeseriesIndex,
                                                newIndexCF));
                        tasks.add(task);
                    }else {
                        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
                        tasks = new ArrayList(TASK_SIZE);
                    }
                    cols = rowQuery.execute().getResult();
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
