/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.dbutils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.serializers.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InternalDbClientImpl extends InternalDbClient {
    private static final Logger log = LoggerFactory.getLogger(InternalDbClientImpl.class);

    private List<String> genTimeSeriesKeys(Calendar startTime, Calendar endTime){
        final int KEY_SHARD = 10;//10 shard for TimeSeries column family

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHH");
        List<String> keys = new ArrayList<String>();
        Calendar currentTime = startTime;
        while(true){
            String timeTemp = dateFormat.format(currentTime.getTime());
            for(int i = 0; i < KEY_SHARD; i++){
                keys.add(timeTemp + "-" + i);
            }
            currentTime.add(Calendar.HOUR, 1);
            if(currentTime.compareTo(endTime) > 0){
                break;
            }
        }
        return keys;
    }
    
    public int countTimeSeries(String cfName, Calendar startTime, Calendar endTime){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd/HH");
        String startTimeStr = dateFormat.format(startTime.getTime());
        String endTimeStr =  dateFormat.format(endTime.getTime());
        int recordCount = 0;
        try {
            Keyspace keyspace = getLocalKeyspace();

            ColumnFamily<String, String> cf = new ColumnFamily<String, String>(
                    cfName, StringSerializer.get(), StringSerializer.get());

            List<String> keys = genTimeSeriesKeys(startTime, endTime);
            for(String key : keys){
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
}
