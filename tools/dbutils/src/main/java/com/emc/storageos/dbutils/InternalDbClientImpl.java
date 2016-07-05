/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbutils;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.ConnectionException;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.constraint.impl.ContainmentConstraintImpl;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbClientContext;
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
            String queryStringFormat = "select count(*) from \"%s\" where key='%s'";
            
            List<String> keys = genTimeSeriesKeys(startTime, endTime);
            for (String key : keys) {
                String queryString = String.format(queryStringFormat, cfName, key);
                recordCount += this.getLocalContext().getSession().execute(queryString).one().getInt(0);
            }
            System.out.println(String.format("Column Family %s's record count between %s and %s is: %s",
                    cfName, startTimeStr, endTimeStr, recordCount));

            return recordCount;
        } catch (ConnectionException e) {
            System.err.println(String.format("Exception=%s", e));
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    public CompositeColumnName getLatestModifiedField(DataObjectType type, URI id, Set<String> ignoreList) {
        
        StringBuilder queryString = new StringBuilder("select key, column1, column2, column3, column4, value, WRITETIME(value) as timestamp from \"");
        queryString.append(type.getCF().getName());
        queryString.append("\" where key=?");
        
        DbClientContext dbClientContext = this.getDbClientContext(type.getDataObjectClass());
        
        PreparedStatement preparedStatement = dbClientContext.getSession().prepare(queryString.toString());
        
        log.info("QueryString: {}", preparedStatement.getQueryString());
        ResultSet resultSet = dbClientContext.getSession().execute(preparedStatement.bind(id.toString()));
        
        if (resultSet == null || resultSet.one() == null) {
            log.warn("Can not find the latest modified field of {}", id);
            return null;
        }
        
        long latestTimeStamp = 0;
        Row resultRow = null;
        for (Row row : resultSet) {
            if (ignoreList != null && ignoreList.contains(row.getString(1))) {
                continue;
            }
            
            long timeStamp = row.getLong("timestamp");
            if (timeStamp > latestTimeStamp) {
                latestTimeStamp = timeStamp;
                resultRow = row;
            }
        }

        return new CompositeColumnName(
                resultRow.getString(0),
                resultRow.getString(1),
                resultRow.getString(2),
                resultRow.getString(3),
                resultRow.getUUID(4),
                resultRow.getBytes(5),
                latestTimeStamp);
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
