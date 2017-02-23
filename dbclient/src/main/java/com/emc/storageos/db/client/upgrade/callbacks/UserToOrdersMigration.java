/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.util.RangeBuilder;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.query.RowQuery;

import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.ClassNameTimeSeries;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.ClassNameTimeSeriesIndexColumnName;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.impl.IndexColumnNameSerializer;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class UserToOrdersMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(UserToOrdersMigration.class);

    final public static String SOURCE_INDEX_CF_NAME="UserToOrders";
    private Keyspace ks = null;
    private ColumnFamily<String, ClassNameTimeSeriesIndexColumnName> newIndexCF;

    public UserToOrdersMigration() {
        super();
    }

    @Override
    public void process() throws MigrationCallbackException {
        log.info("Adding new index records for class: {} field: {} annotation: {} name={}",
                new Object[] { Order.class, Order.SUBMITTED_BY_USER_ID, ClassNameTimeSeries.class.getName(), name});

        DataObjectType doType = TypeMap.getDoType(Order.class);
        ColumnField field = doType.getColumnField(Order.SUBMITTED_BY_USER_ID);
        newIndexCF = field.getIndexCF();

        ColumnFamily<String, IndexColumnName> userToOrders =
                new ColumnFamily<>(SOURCE_INDEX_CF_NAME, StringSerializer.get(), IndexColumnNameSerializer.get());

        DbClientImpl client = (DbClientImpl)dbClient;
        ks = client.getKeyspace(Order.class);
        MutationBatch mutationBatch = ks.prepareMutationBatch();

        long m = 0;
        try {
            OperationResult<Rows<String, IndexColumnName>> result = ks.prepareQuery(userToOrders).getAllRows()
                    .setRowLimit(1000)
                    .withColumnRange(new RangeBuilder().setLimit(0).build())
                    .execute();

            ColumnList<IndexColumnName> cols;
            for (Row<String, IndexColumnName> row : result.getResult()) {
                RowQuery<String, IndexColumnName> rowQuery = ks.prepareQuery(userToOrders).getKey(row.getKey())
                        .autoPaginate(true)
                        .withColumnRange(new RangeBuilder().setLimit(5).build());

                while (!(cols = rowQuery.execute().getResult()).isEmpty()) {
                    m++;
                    for (Column<IndexColumnName> col : cols) {
                        String indexKey = row.getKey();
                        String orderId = col.getName().getTwo();

                        ClassNameTimeSeriesIndexColumnName newCol = new ClassNameTimeSeriesIndexColumnName(col.getName().getOne(), orderId,
                                col.getName().getTimeUUID());
                        mutationBatch.withRow(newIndexCF, indexKey).putEmptyColumn(newCol, null);
                        if ( m % 10000 == 0) {
                            mutationBatch.execute();
                        }
                    }
                }
            }

            mutationBatch.execute();
        }catch (Exception e) {
            log.error("Migration to {} failed e=", newIndexCF.getName(), e);
        }
    }
}
