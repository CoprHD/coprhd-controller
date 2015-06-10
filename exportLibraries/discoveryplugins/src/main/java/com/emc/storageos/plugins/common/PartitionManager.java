/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.plugins.common;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StatTimeSeries;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.google.common.collect.Lists;

public class PartitionManager {
   
    private  Logger  _log = LoggerFactory
    .getLogger(PartitionManager.class);
    /**
     * insert in batches
     * @param records
     * @param partitionSize
     * @param dbClient
     */
    public  void insertInBatches(List<Stat> records, int partitionSize,
            DbClient dbClient) {
        List<List<Stat>> stat_partitions =  Lists.partition(records, partitionSize);
        for(List<Stat> partition : stat_partitions) {
            Stat[] statBatch = new Stat[partition.size()];
            statBatch = partition.toArray(statBatch);
            try {
                dbClient.insertTimeSeries(StatTimeSeries.class, statBatch);
                _log.info("{} Stat records persisted to DB", statBatch.length);
            } catch (DatabaseException e) {
                _log.error("Error inserting time series records into the database", e);
            }
           
        }
    }
    
    /**
     * insert Discovered Objects in batches
     * @param records
     * @param partitionSize
     * @param dbClient
     */
    public <T extends DataObject> void insertInBatches(List<T> records, int partitionSize, DbClient dbClient,
            String type) {
        List<List<T>> volume_partitions = Lists.partition(records, partitionSize);
        for (List<T> partition : volume_partitions) {
            try {
                dbClient.createObject(partition);
                _log.info("{} {} Records inserted to DB", partition.size(), type);
            } catch (DatabaseException e) {
                _log.error("Error inserting {} records into the database:", type, e);
            }
        }
    }
    
    /**
     * update Discovered Objects in batches
     * @param records
     * @param partitionSize
     * @param dbClient
     */
    public <T extends DataObject> void updateInBatches(List<T> records, int partitionSize,
            DbClient dbClient, String type) {
        List<List<T>> volume_partitions =  Lists.partition(records, partitionSize);
        for(List<T> partition : volume_partitions) {
            try {
                dbClient.persistObject(partition);
                _log.info("{} {} Records updated to DB",partition.size(), type);
            } catch (DatabaseException e) {
                _log.error("Error updating {} records into the database:", type,e);
            }
           
        }
    }
    
    /**
     * update Discovered Objects in batches
     * @param records
     * @param partitionSize
     * @param dbClient
     */
    public <T extends DataObject> void updateAndReIndexInBatches(List<T> records, int partitionSize,
            DbClient dbClient, String type) {
        List<List<T>> volume_partitions =  Lists.partition(records, partitionSize);
        for(List<T> partition : volume_partitions) {
            try {
                dbClient.updateAndReindexObject(partition);
                _log.info("{} {} Records updated and reindexed to DB",partition.size(), type);
            } catch (DatabaseException e) {
                _log.error("Error updating {} records into the database:", type,e);
            }
           
        }
    }
}
