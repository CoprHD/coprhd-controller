/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.geo.vdccontroller.impl;

import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import com.emc.storageos.security.keystore.impl.KeyStoreUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedQueue;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.geo.vdccontroller.VdcController;
import com.emc.storageos.security.geo.GeoServiceHelper;
import com.emc.storageos.security.geo.GeoServiceJob;
import com.emc.storageos.security.geo.GeoServiceJobSerializer;

/**
 * Restart any pending jobs upon a node failure or restart
 */
public class GeoServiceJobQueue extends DistributedQueueConsumer<GeoServiceJob> {
    private final Logger _log = LoggerFactory.getLogger(GeoServiceJobQueue.class);
    private static int WAIT_INTERVAL_IN_SEC = 10;
    private static int MAX_DB_RETRY = 30;
    private DbClient _dbClient;
    private VdcController _controller;
    private DistributedQueue<GeoServiceJob> _queue;
    private CoordinatorClient _coordinator;
    private KeyStore viprKeyStore;

    public void setDbClient(DbClient dbClient){
        _dbClient = dbClient;
    }
    public void setVdcController(VdcController controller){
        _controller = controller;
    }
    public void setCoordinator(CoordinatorClient coordinator){
        _coordinator = coordinator;
    }

    /**
     * Setup geosvc job queue
     */
    public void start() {
        _log.info("Get vipr keystore");
        try {
            viprKeyStore = KeyStoreUtil.getViPRKeystore(_coordinator);
        } catch (Exception e) {
            _log.error("Failed to load the VIPR keystore", e);
            throw new IllegalStateException(e);
        }

        _log.info("Starting geosvc job queue");
        try {
            _queue = _coordinator.getQueue(
                               GeoServiceHelper.GEOSVC_QUEUE_NAME, this, new GeoServiceJobSerializer(),
                               GeoServiceHelper.DEFAULT_MAX_THREADS);
        }catch(Exception e){
            _log.error("can not startup geosvc job queue", e);
        }
    }

    /**
     * Stop geosvc job queue
     */
    public void stop() {
        _log.info("Stopping geosvc job queue");
        _queue.stop(GeoServiceHelper.DEFAULT_MAX_WAIT_STOP);
    }

    /**
     * Used by geosvc in case have subtasks to process
     * @param job The geosvc job to be enqueued. 
     * @throws Exception
     */
    public void enqueueJob(GeoServiceJob job) throws Exception {
        _log.info("post job {} task {}", job.getVdcId(), job.getTask());
        _queue.put(job);
    }

    /**
     * Verify the hosting device has not migrated states while waiting for dispatching and continue task
     * @param job The object provisioning job which is being worked on. This could be either creation or deletion job
     * @param callback This must be executed, after the item is processed successfully to remove the item
     *                 from the distributed queue
     *
     * @throws Exception
     */
    @Override
    public void consumeItem(GeoServiceJob job, DistributedQueueItemProcessedCallback callback) throws Exception {

        // verify job, db may not stable right now after reboot, retry may need
        VirtualDataCenter vdcInfo = null;
        int retry = 0;
        while (retry < MAX_DB_RETRY) {
            try {
                vdcInfo = _dbClient.queryObject(VirtualDataCenter.class, job.getVdcId());
                break;
            } catch (DatabaseException e) {
                _log.info("db not stable yet, retry");
                try {
                    TimeUnit.SECONDS.sleep(WAIT_INTERVAL_IN_SEC);
                } catch (InterruptedException ex) {
                	//Ignore this exception
                }
            }
            retry = retry + 1;
        }

        if (vdcInfo == null) {
            _log.info("Failed to query vdc {} from DB. Retry later", job.getVdcId());
            return;
        }

        String task = job.getTask();
        if (task == null) {
            _log.error("The vdc connect job for {} does not have an associated task", job.getVdcId());
            return;
        }

        try {
            _controller.setKeystore(viprKeyStore);

            // these methods will obtain lock and do nothing if operation is already in progress
            GeoServiceJob.JobType type= job.getType();
            switch(type) {
                case VDC_CONNECT_JOB:
                    _log.info("Continuing initialization operation {} for {}", task, job.getVdcId());
                    _controller.connectVdc(vdcInfo, task, job.getParams());
                    break;
                case VDC_REMOVE_JOB:
                    _log.info("vdc operation {} for {}", task, job.getVdcId());
                    _controller.removeVdc(vdcInfo, task, job.getParams());
                    break;
                case VDC_UPDATE_JOB:
                    _log.info("Updating operation {} for {}", task, job.getVdcId());
                    _controller.updateVdc(job.getVdc(), task, job.getParams());
                    break;
                case VDC_DISCONNECT_JOB:
                    _log.info("Disconnecting operation {} for {}", task, job.getVdcId());
                    _controller.disconnectVdc(vdcInfo, task,job.getParams());
                    break;
                case VDC_RECONNECT_JOB:
                    _log.info("Reconnecting operation {} for {}", task, job.getVdcId());
                    _controller.reconnectVdc(vdcInfo, task, job.getParams());
                    break;
                default:
                    _log.error("Invalid operation type {} on {}/{}",
                            new Object[] { job.getType(), task, job.getVdcId() });
            }
        } catch (Exception e) {
            // TODO: retry the task if it is retryable exception
            _log.error("Execute job failed", e);
        }


        // removes item from queue
        callback.itemProcessed();

        _log.info("The job type={} vdcId={} task={} is removed", new Object[] { job.getType(), job.getVdcId(), job.getTask()});

    }

}
