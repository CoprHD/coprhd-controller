/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbutils;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.serializers.StringSerializer;

import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.constraint.impl.ContainmentConstraintImpl;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.common.DependencyTracker.Dependency;
import com.emc.storageos.db.exceptions.DatabaseException;


public class InternalDbClientImpl extends InternalDbClient {
    private static final Logger log = LoggerFactory.getLogger(InternalDbClientImpl.class);

    private List<String> genTimeSeriesKeys(Calendar startTime, Calendar endTime) {
        final int KEY_SHARD = 10;// 10 shard for TimeSeries column family

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHH");
        List<String> keys = new ArrayList<String>();
        Calendar currentTime = startTime;
        while (true) {
            String timeTemp = dateFormat.format(currentTime.getTime());
            for (int i = 0; i < KEY_SHARD; i++) {
                keys.add(timeTemp + "-" + i);
            }
            currentTime.add(Calendar.HOUR, 1);
            if (currentTime.compareTo(endTime) > 0) {
                break;
            }
        }
        return keys;
    }

    public int countTimeSeries(String cfName, Calendar startTime, Calendar endTime) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd/HH");
        String startTimeStr = dateFormat.format(startTime.getTime());
        String endTimeStr = dateFormat.format(endTime.getTime());
        int recordCount = 0;
        try {
            Keyspace keyspace = getLocalKeyspace();

            ColumnFamily<String, String> cf = new ColumnFamily<String, String>(
                    cfName, StringSerializer.get(), StringSerializer.get());

            List<String> keys = genTimeSeriesKeys(startTime, endTime);
            for (String key : keys) {
                recordCount += keyspace.prepareQuery(cf).getKey(key).getCount().execute().getResult();
            }
            System.out.println(String.format("Column Family %s's record count between %s and %s is: %s",
                    cfName, startTimeStr, endTimeStr, recordCount));

            return recordCount;
        } catch (ConnectionException e) {
            System.err.println(String.format("Exception=%s", e));
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    public Column<CompositeColumnName> getLatestModifiedField(DataObjectType type, URI id, Set<String> ignoreList) {
        Column<CompositeColumnName> latestField = null;
        ColumnFamily<String, CompositeColumnName> cf = type.getCF();
        Keyspace ks = this.getKeyspace(type.getDataObjectClass());
        Rows<String, CompositeColumnName> rows = this.queryRowsWithAllColumns(ks,
                Lists.newArrayList(id), cf);
        if (rows.isEmpty()) {
            log.warn("Can not find the latest modified field of {}", id);
            return latestField;
        }
        
        long latestTimeStampe = 0;
        for (Column<CompositeColumnName> column : rows.iterator().next().getColumns()) {
            if (ignoreList != null && ignoreList.contains(column.getName().getOne())) {
                continue;
            }
            
            if (column.getTimestamp() > latestTimeStampe) {
                latestTimeStampe = column.getTimestamp();
                latestField = column;
            }
        }

        return latestField;
    }
    
    public List<URI> getReferUris(URI targetUri, Class<? extends DataObject> type, Dependency dependency) {
        List<URI> references = new ArrayList<>();
        if (targetUri == null) {
            return references;
        }

        ContainmentConstraint constraint = 
                new ContainmentConstraintImpl(targetUri, dependency.getType(), dependency.getColumnField());
        URIQueryResultList result = new URIQueryResultList();
        this.queryByConstraint(constraint, result);
        Iterator<URI> resultIt = result.iterator();
        if(resultIt.hasNext()) {
            references.add(resultIt.next());
        }

        return references;
    }
}
