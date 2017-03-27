/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.SimpleStatement;
import com.emc.storageos.db.client.impl.ClassNameTimeSeriesIndexColumnName;
import com.emc.storageos.db.client.impl.ColumnFamilyDefinition;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.ClassNameTimeSeries;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class UserToOrdersMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(UserToOrdersMigration.class);

    private DbClientContext dbClientContext = null;
    private ColumnFamilyDefinition newIndexCF;

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

        DbClientImpl client = (DbClientImpl)dbClient;
        dbClientContext = client.getDbClientContext(Order.class);
        RowMutator rowMutator = new RowMutator(dbClientContext, false);
        
        long m = 0;
        try {
        	SimpleStatement queryStatement = new SimpleStatement("select * from \"UserToOrders\"");
            queryStatement.setFetchSize(100);
        	ResultSet resultSet = dbClientContext.getSession().execute(queryStatement);
        	
            for (Row row : resultSet) {
            	String indexKey = row.getString("key");
                String orderId = row.getString("column2");

                ClassNameTimeSeriesIndexColumnName newCol = new ClassNameTimeSeriesIndexColumnName(row.getString("column1"), orderId,
                        row.getUUID("column5"));
                rowMutator.insertIndexColumn(newIndexCF.getName(), indexKey, newCol, null);
                if ( m % 10000 == 0) {
                	rowMutator.execute();
                	rowMutator = new RowMutator(dbClientContext, false);
                }
            }

            /*ColumnList<IndexColumnName> cols;
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

            mutationBatch.execute();*/
        }catch (Exception e) {
            log.error("Migration to {} failed e=", newIndexCF.getName(), e);
        }
    }
}
