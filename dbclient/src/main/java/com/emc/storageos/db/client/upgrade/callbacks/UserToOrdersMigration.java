/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.query.RowQuery;
import com.netflix.astyanax.serializers.StringSerializer;

import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.QueryHitIterator;
import com.emc.storageos.db.client.impl.ClassNameTimeSeriesIndexColumnName;
import com.emc.storageos.db.client.impl.ClassNameTimeSeriesSerializer;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserToOrdersMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(UserToOrdersMigration.class);

    private static long n = 0;
    static class MigrationQueryHitIterator extends QueryHitIterator<URI, IndexColumnName> {
        Keyspace keyspace;
        AlternateIdConstraintImpl constraint;
        ColumnFamily<String, ClassNameTimeSeriesIndexColumnName> cf;
        MutationBatch mutationBatch;
        int pageCount;

        MigrationQueryHitIterator(Keyspace ks, AlternateIdConstraintImpl c, RowQuery<String, IndexColumnName> query)  {
            super(query);

            keyspace = ks;
            constraint = c;
            cf = new ColumnFamily<String, ClassNameTimeSeriesIndexColumnName>("UserToOrdersByTimeStamp",
                    StringSerializer.get(), ClassNameTimeSeriesSerializer.get());
            mutationBatch = ks.prepareMutationBatch();
            pageCount = constraint.getPageCount();
        }

        @Override
        protected URI createQueryHit(Column<IndexColumnName> column) {
            try {
                n++;
                URI id = URI.create(column.getName().getTwo());
                // long timeInMicros = TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID());

                ClassNameTimeSeriesIndexColumnName col = new ClassNameTimeSeriesIndexColumnName(column.getName().getOne(), id.toString(),column.getName().getTimeUUID());
                mutationBatch.withRow(cf, constraint.getAltId()).putEmptyColumn(col, null);
                if ( n % pageCount == 0) {
                    mutationBatch.execute(); // commit
                    mutationBatch = keyspace.prepareMutationBatch();
                }

                return id;
            }catch (Throwable e) {
                log.info("e=",e);
            }
            return null;
        }
    }

    @Override
    public void process() throws MigrationCallbackException {
        long start = System.currentTimeMillis();

        try {
            final AlternateIdConstraintImpl constraint = new AlternateIdConstraintImpl("UserToOrders", "root", Order.class, 0, 0);
            constraint.setPageCount(10000);
            DbClientImpl client = (DbClientImpl)getDbClient();
            Keyspace ks = client.getLocalKeyspace();
            constraint.setKeyspace(ks);

            RowQuery<String, IndexColumnName> query = constraint.genQuery();
            query.autoPaginate(true);

            MigrationQueryHitIterator iterator = new MigrationQueryHitIterator(ks, constraint, query);

            iterator.prime();

            while (iterator.hasNext()) {
                iterator.next();
            }

            iterator.mutationBatch.execute();

            long end = System.currentTimeMillis();
            System.out.println("Read " + n + " : " + (end - start));
        }catch (Throwable e) {
            log.error("e=", e);
        }
    }
}
