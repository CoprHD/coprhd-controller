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
package com.emc.storageos.volumecontroller.impl.metering.plugins.smis;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.TimeSeriesQueryResult;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StatTimeSeries;
import com.emc.storageos.db.exceptions.DatabaseException;

/**
 * DBClient used in starting its own DBService, used for testing VNX plugin
 */
public class Cassandraforplugin {
    private static final String SERVICE_BEAN = "dbsvc";
    private static final Logger _logger      = LoggerFactory
                                                     .getLogger(Cassandraforplugin.class);

    public static long query(DbClient dbClient) {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(1);
        DummyQueryResult result = new DummyQueryResult(latch);
        DateTime dateTime = new DateTime(DateTimeZone.UTC);
        try {
            dbClient.queryTimeSeries(StatTimeSeries.class, dateTime, result, executor);
        } catch (DatabaseException e) {
            _logger.error("Exception Query" + e);
        }
        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
        return latch.getCount();
    }

    public static DbClient returnDBClient() throws IOException {
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(
                "/dbutils-conf.xml");
        DbClientImpl dbClient = (DbClientImpl) ctx.getBean("dbclient");
        dbClient.setClusterName("StorageOS");
        dbClient.setKeyspaceName("StorageOS");
        dbClient.start();
        return dbClient;
    }

    private static class DummyQueryResult implements TimeSeriesQueryResult<Stat> {
        private CountDownLatch _latch;

        DummyQueryResult ( CountDownLatch latch ) {
            _latch = latch;
        }

        @Override
        public void data(Stat data, long timeinMillis) {
            _logger.info("VolumeID :" + data.getResourceId());
            _logger.info("Provisioned Volume :" + data.getProvisionedCapacity());
            _logger.info("Read IOS :" + data.getBandwidthIn());
            _logger.info("Write IOS :" + data.getBandwidthOut());
            _logger.info("Allocated :" + data.getAllocatedCapacity());
            _logger.info("Project :" + data.getProject());
            _logger.info("VirtualPool :" + data.getVirtualPool());
            _logger.info("Tenant :" + data.getTenant());
            _logger.info("serviceType :" + data.getServiceType());
            _latch.countDown();
        }

        @Override
        public void done() {
            _logger.info("Querying Metrics Done");
        }

        @Override
        public void error(Throwable e) {
            _logger.error("Error callback", e);
        }
    }
}
