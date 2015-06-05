/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.simulators.db.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;

import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.CompositeColumnNameSerializer;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.simulators.db.model.Export;
import com.emc.storageos.simulators.db.model.Quota;
import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.serializers.StringSerializer;

/**
 * Simulator Db client implementation
 */
public class SimulatorDbClient extends DbClientImpl {
    private static final String QUOTA_INDEX = "QuotaIndex";
    private static final String ROW_KEY = "urn:quota:index";
    private ColumnFamily<String, CompositeColumnName> _quotaIndexCF
            = new ColumnFamily<String, CompositeColumnName>
                    (QUOTA_INDEX, StringSerializer.get(), CompositeColumnNameSerializer.get());;

    private static final String EXPORT_INDEX = "ExportIndex";
    private ColumnFamily<String, CompositeColumnName> _exportCF
            = new ColumnFamily<String, CompositeColumnName>
                    (EXPORT_INDEX, StringSerializer.get(), CompositeColumnNameSerializer.get());

    /**
     * Store quota object
     *
     * @param quota             quota object to be stored
     */
    public void persistQuotaIndex(Quota quota) {
        if (quota == null)
            return;

        try {
            MutationBatch batch = getLocalKeyspace().prepareMutationBatch();
            batch.lockCurrentTimestamp();

            ColumnListMutation<CompositeColumnName> columnList = batch.withRow(_quotaIndexCF, ROW_KEY);

            URI uri = URI.create(String.format("urn:quota:%1$s", quota.getId()));
            CompositeColumnName columnName = new CompositeColumnName(uri.toString());
            columnList.putColumn(columnName, quota.toBytes(), null);

            batch.execute();
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    /**
     * Fetch quota from corresponding column
     *
     * @param uri           quota id
     * @return              quota object
     */
    public Quota queryQuotaIndex(URI uri) {
        Quota quota = null;

        try {
            OperationResult<Column<CompositeColumnName>> result =
                    getLocalKeyspace().prepareQuery(_quotaIndexCF)
                            .getKey(ROW_KEY)
                            .getColumn(new CompositeColumnName(uri.toString()))
                            .execute();
            Column<CompositeColumnName> it = result.getResult();
            quota = new Quota();
            quota.loadBytes(it.getByteArrayValue());

            return quota;
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    /**
     * Page querying quota objects
     *
     * @param uri           start quota id
     * @param num           number per page
     * @return              quota list
     */
    public ArrayList<Quota> queryQuotaIndexByPage(URI uri, int num) {
        ArrayList<Quota> quotaList = new ArrayList<Quota>();

        try {
            OperationResult<ColumnList<CompositeColumnName>> result =
                    getLocalKeyspace().prepareQuery(_quotaIndexCF)
                            .getKey(ROW_KEY)
                            .withColumnRange(new CompositeColumnName(uri.toString()), null, false, num)
                            .execute();
            Iterator<Column<CompositeColumnName>> it = result.getResult().iterator();
            while (it.hasNext()) {
                Column<CompositeColumnName> next = it.next();
                Quota quota = new Quota();
                quota.loadBytes(next.getByteArrayValue());
                quotaList.add(quota);
            }

            return quotaList;
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    /**
     * Delete quota
     *
     * @param uri               quota id
     */
    public void deleteQuotaIndex(URI uri) {
        try {
            MutationBatch batch = getLocalKeyspace().prepareMutationBatch();
            batch.withRow(_quotaIndexCF, ROW_KEY).deleteColumn(new CompositeColumnName(uri.toString()));
            batch.execute();
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    /**
     * Store export
     *
     * @param export            export object to be stored
     * @return
     */
    public void persistExport(Export export) {
        if (export == null)
            return;

        try {
            MutationBatch batch = getLocalKeyspace().prepareMutationBatch();
            batch.lockCurrentTimestamp();

            URI uri = URI.create(String.format("urn:export:%1$s", export.getId()));
            ColumnListMutation<CompositeColumnName> columnList = batch.withRow(_exportCF, uri.toString());
            CompositeColumnName columnName = new CompositeColumnName(uri.toString());
            columnList.putColumn(columnName, export.toBytes(), null);

            batch.execute();
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    /**
     * Query export object
     *
     * @param uri               export key
     * @return                  export object
     */
    public Export queryExport(URI uri) {
        Export export = null;

        try {
            OperationResult<Column<CompositeColumnName>> result =
                    getLocalKeyspace().prepareQuery(_exportCF)
                            .getKey(uri.toString())
                            .getColumn(new CompositeColumnName(uri.toString()))
                            .execute();
            Column<CompositeColumnName> it = result.getResult();
            export = new Export();
            export.loadBytes(it.getByteArrayValue());

            return export;
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    /**
     * Delete export
     *
     * @param uri
     */
    public void deleteExport(URI uri) {
        try {
            MutationBatch batch = getLocalKeyspace().prepareMutationBatch();
            batch.withRow(_exportCF, uri.toString()).delete();
            batch.execute();
        } catch (final ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }
}
