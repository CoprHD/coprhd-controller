/*
 * Copyright (c) 2016. EMC  Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.util.UUID;

import com.emc.storageos.db.client.model.TimeSeriesAlternateId;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.model.*;
import com.netflix.astyanax.query.RowQuery;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.util.RangeBuilder;
import com.netflix.astyanax.serializers.CompositeRangeBuilder;

import com.emc.storageos.db.client.impl.*;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.upgrade.BaseDefaultMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class TimeSeriesIndexMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(TimeSeriesIndexMigration.class);
    final public static String SOURCE_INDEX_CF_NAME="TenantToOrder";
    final public static String SOURCE_INDEX_CF_NAME2="timeseriesIndex";

    public TimeSeriesIndexMigration() {
        super();
    }

    @Override
    public void process() throws MigrationCallbackException {
        long start = System.currentTimeMillis();

        log.info("lbyx Adding new index records for class: {} field: {} annotation: {}",
                new Object[] { Order.class.getName(), Order.SUBMITTED, TimeSeriesAlternateId.class.getName()});

        ColumnFamily<String, IndexColumnName> tenantToOrder =
                new ColumnFamily<>(SOURCE_INDEX_CF_NAME, StringSerializer.get(), IndexColumnNameSerializer.get());

        ColumnFamily<String, IndexColumnName> timeseriesIndex =
                new ColumnFamily<>(SOURCE_INDEX_CF_NAME2, StringSerializer.get(), IndexColumnNameSerializer.get());

        DataObjectType doType = TypeMap.getDoType(Order.class);
        ColumnField field = doType.getColumnField(Order.SUBMITTED);
        ColumnFamily<String, TimeSeriesIndexColumnName> newIndexCF = field.getIndexCF();

        //DbClientImpl client = getInternalDbClient();
        DbClientImpl client = (DbClientImpl)dbClient;
        Keyspace ks = client.getKeyspace(Order.class);
        MutationBatch mutationBatch = ks.prepareMutationBatch();

        try {
            long m = 0;
            OperationResult<Rows<String, IndexColumnName>> result = ks.prepareQuery(tenantToOrder).getAllRows()
                    .setRowLimit(100)
                    .withColumnRange(new RangeBuilder().setLimit(0).build())
                    .execute();
            for (Row<String, IndexColumnName> row : result.getResult()) {
                RowQuery<String, IndexColumnName> rowQuery = ks.prepareQuery(tenantToOrder).getKey(row.getKey())
                        .autoPaginate(true)
                        .withColumnRange(new RangeBuilder().setLimit(5).build());
                ColumnList<IndexColumnName> cols = rowQuery.execute().getResult();
                while (!cols.isEmpty()) {
                    for (Column<IndexColumnName> col : cols) {
                        m++;
                        String indexKey = row.getKey();
                        String orderId = col.getName().getTwo();

                        //get time UUID
                        RowQuery<String, IndexColumnName> timeseriesIndexRowQuery = ks.prepareQuery(timeseriesIndex).getKey("Order");
                        CompositeRangeBuilder builder = IndexColumnNameSerializer.get().buildRange().withPrefix("true");
                        ColumnList<IndexColumnName> timeSeriesCol= timeseriesIndexRowQuery.withColumnRange(builder.greaterThanEquals(orderId)
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
                        if ( m % 10000 == 0) {
                            mutationBatch.execute();
                        }
                    }
                    cols = rowQuery.execute().getResult();
                }
            }

            mutationBatch.execute();

            long end = System.currentTimeMillis();
            log.info("Read {} records in {} MS", m, (end - start)/1000);
        }catch (Exception e) {
            log.error("Migration to {} failed e=", newIndexCF.getName(), e);
        }
    }
}
