/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.monitoring;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJob;

public class MonitoringJobConsumer extends DistributedQueueConsumer<DataCollectionJob>{
    private ScheduledExecutorService _execService = Executors.newScheduledThreadPool(5);
    private final Logger _logger = LoggerFactory.getLogger(MonitoringJobConsumer.class);

    private Map<StorageSystem.Type, IMonitoringStorageSystem> _monitoringImplMap;
    
    // Determines whether or not the node to monitor events from indications sources like 
    // (SMIS, VNXFILE, ISILON)
    private boolean _enableMonitoring;
    
    
    /**
     * Monitoring Delay in Minutes
     */
    private final static int MONITORING_INITIAL_DELAY = 3;
    public final static int MONITORING_INTERVAL = 5;
    /**
     * 
     * @param item {@link DataCollectionJob} MonitoringJob will have new SMIS Provider's URI.
     * @param callback {@link DistributedQueueItemProcessedCallback} callback instance
     */
    @Override
    public void consumeItem(DataCollectionJob item,
            DistributedQueueItemProcessedCallback callback) throws Exception {
        _logger.debug("Entering {}",Thread.currentThread().getStackTrace()[1].getMethodName());
        MonitoringJob monitoringJob = (MonitoringJob)item;
        if(_enableMonitoring){
            try {
            	_logger.debug("Monitoring device type :{}",monitoringJob.getDeviceType());
                /**
                 * Invokes appropriate MonitoringImpl.startMonitoring() based on the StoargeSystem.Type.
                 * For ex: 
                 * isilon -> IsilonMonitoringImpl.startMonitoring()
                 * vnxfile -> VNXFileMonitoringImpl.startMonitoring()
                 */
                _monitoringImplMap.get(monitoringJob.getDeviceType()).startMonitoring(monitoringJob, callback);
            } catch (Exception e) {

                _logger.error("Exception occured",e);
            }
        }else{
            /**
             * Indication is disabled. Hence no need to do any task for monitoring here.
             * 
             */
            _logger.info("Monitoring is disabled for the item {} : {} "
                    ,monitoringJob.getDeviceType() ,monitoringJob.getId());
        }
        
        _logger.debug("Exiting {}",Thread.currentThread().getStackTrace()[1].getMethodName());

    }
    
    /**
     * Starts Periodic background schedulers for Monitoring Use case.
     * @throws IOException 
     */
    public void start() throws IOException{
        _logger.debug("Entering {}",Thread.currentThread().getStackTrace()[1].getMethodName());
        if(_enableMonitoring){
            _execService.scheduleAtFixedRate(
                    new Runnable() {
                        @Override
                        public void run() {
                            _logger.debug("Entering {}",Thread.currentThread().getStackTrace()[1].getMethodName());
                            try {
                                    // Starts schedule activity for the isilon,vnxblock,vmax and vnxfile monitoring
                                    for(Map.Entry<StorageSystem.Type, IMonitoringStorageSystem> entry:_monitoringImplMap.entrySet()){
                                        entry.getValue().scheduledMonitoring();
                                    }
                                
                            } catch (Exception e) {
                                _logger.error(e.getMessage(),e);
                            }
                            _logger.debug("Exiting {}",Thread.currentThread().getStackTrace()[1].getMethodName());
                        }
                    },  MONITORING_INITIAL_DELAY, MONITORING_INTERVAL, TimeUnit.MINUTES);
        }else{
            _logger.info("Monitoring has disabled in this controller. Hence monitoring scheduler has not started here.");
        }
        
        _logger.debug("Exiting {}",Thread.currentThread().getStackTrace()[1].getMethodName());
    }
    
   /**
     * Stopping DisocveryConsumer, would close the execService.
     */
    public void stop() {
        _execService.shutdown();
        try {
            _execService.awaitTermination(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            // To-DO: filter it for timeout sException
            // No need to throw any exception
            _logger.error("TimeOut occured after waiting Client Threads to finish");
        }
    }
    
    
    /**
     * Setter method for the monitoringImpl instances based on the StorageSystem.Type
     * @param monitoringImplMap
     */
    public void setMonitoringImplMap(
            Map<StorageSystem.Type, IMonitoringStorageSystem> monitoringImplMap) {
        this._monitoringImplMap = monitoringImplMap;
    }
    
    /**
     * Setter for the enable monitoring flag.
     * 
     * @param enableMonitoring The value for the enable monitoring flag.
     */
    public void setEnableMonitoring(boolean enableMonitoring) {
        _enableMonitoring = enableMonitoring;
    }

    
}
