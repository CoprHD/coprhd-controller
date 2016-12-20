/*
 * Copyright (c) 2016. EMC  Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import com.emc.storageos.db.client.impl.*;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.model.*;
import com.netflix.astyanax.query.RowQuery;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.util.RangeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeSeriesIndexMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(TimeSeriesIndexMigration.class);
    private DbClientImpl client;

    public TimeSeriesIndexMigration(DbClientImpl dbClient) {
        client = dbClient;
    }

    @Override
    public void process() throws MigrationCallbackException {
        long start = System.currentTimeMillis();

        log.info("lbybb0");

        Keyspace ks = client.getLocalKeyspace();
        ColumnFamily<String, IndexColumnName> tenantToOrder =
                new ColumnFamily<String, IndexColumnName>("TenantToOrder", StringSerializer.get(),
                        IndexColumnNameSerializer.get());

        DataObjectType doType = TypeMap.getDoType(Order.class);
        ColumnField field = doType.getColumnField(Order.SUBMITTED);
        ColumnFamily<String, TimeSeriesIndexColumnName> newCf = field.getIndexCF();
        MutationBatch mutationBatch = ks.prepareMutationBatch();
        try {
            long n = 0;
            long m;
            OperationResult<Rows<String, IndexColumnName>> result = ks.prepareQuery(tenantToOrder).getAllRows()
                    .setRowLimit(100)
                    .withColumnRange(new RangeBuilder().setLimit(0).build())
                    .execute();
            for (Row<String, IndexColumnName> row : result.getResult()) {
                n++;
                m = 0;
                RowQuery<String, IndexColumnName> rowQuery = ks.prepareQuery(tenantToOrder).getKey(row.getKey())
                        .autoPaginate(true)
                        .withColumnRange(new RangeBuilder().setLimit(5).build());
                ColumnList<IndexColumnName> cols;
                while (!(cols = rowQuery.execute().getResult()).isEmpty()) {
                    for (Column<IndexColumnName> col : cols) {
                        m++;
                        String indexKey = row.getKey();
                        String orderId = col.getName().getTwo();
                        log.info("lbyd11: tid={} order={} m={}", indexKey, orderId, m);

                        TimeSeriesIndexColumnName newCol = new TimeSeriesIndexColumnName(Order.class.getSimpleName(),
                                orderId, col.getName().getTimeUUID());

                        mutationBatch.withRow(newCf, indexKey).putEmptyColumn(newCol, null);
                        if ( m % 10000 == 0) {
                            log.info("lbyd22 commit m={}", m);
                            mutationBatch.execute();
                        }
                    }
                }
            }

            mutationBatch.execute();
            long end = System.currentTimeMillis();
            System.out.println("Read5 "+n+" : "+ (end - start)/1000);
        }catch (Exception e) {
            log.error("lbyy0: e=", e);
        }
    }
}
