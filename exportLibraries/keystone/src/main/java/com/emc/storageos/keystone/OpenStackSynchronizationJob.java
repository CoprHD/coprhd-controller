/*
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.keystone;

import com.emc.storageos.keystone.restapi.utils.KeystoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OpenStackSynchronizationJob {
    private static final Logger _log = LoggerFactory.getLogger(OpenStackSynchronizationJob.class);
    // Interval delay between each execution in seconds.
    private static final int INTERVAL_DELAY = 60;
    // Initial delay before first execution in seconds.
    private static final int INITIAL_DELAY = 60;
    // Maximum time for a timeout when awaiting for termination.
    private static final int MAX_TERMINATION_TIME = 120;

    private KeystoneUtils _keystoneUtilsService;
    private ScheduledExecutorService _dataCollectionExecutorService;

    public void setKeystoneUtilsService(KeystoneUtils _keystoneUtilsService) {
        this._keystoneUtilsService = _keystoneUtilsService;
    }

    public void start() throws Exception {

        _log.info("Start OpenStack Synchronization Job");
        _dataCollectionExecutorService = Executors.newScheduledThreadPool(1);

        // Schedule task at fixed interval.
        _dataCollectionExecutorService.scheduleAtFixedRate(
                new SynchronizationScheduler(),
                INITIAL_DELAY, INTERVAL_DELAY, TimeUnit.SECONDS);
    }

    public void stop() {

        _log.info("Stopping OpenStack Synchronization Job");
        try {
            _dataCollectionExecutorService.shutdown();
            _dataCollectionExecutorService.awaitTermination(MAX_TERMINATION_TIME, TimeUnit.SECONDS);
        } catch (Exception e) {
            _log.error("TimeOut occurred after waiting Client Threads to finish");
        }
    }

    private void getTenantList(){

        _log.info("[MM]: TenantList");
    }

    private class SynchronizationScheduler implements Runnable{

        @Override
        public void run() {
            try {
                getTenantList();
            } catch (Exception e) {
                _log.error(String.format("Exception caught when trying to run OpenStack Synchronization job"), e);
            }
        }
    }
}
