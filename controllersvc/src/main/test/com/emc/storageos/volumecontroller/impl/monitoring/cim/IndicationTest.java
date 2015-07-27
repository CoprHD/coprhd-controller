/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.monitoring.cim;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

import com.emc.storageos.cimadapter.connections.cim.CimConnectionInfo;
import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.TimeSeriesQueryResult;
import com.emc.storageos.db.client.model.Event;
import com.emc.storageos.db.client.model.EventTimeSeries;
import com.emc.storageos.services.util.EnvConfig;

/**
 * Test Class that pumps a heart beat message. To execute this component inside
 * the VM, we need to set the class path accordingly and to have all the required
 * libraries on the class path. Use the command java -cp
 * org.junit.runner.JUnitCore
 * com.emc.storageos.volumecontroller.monitor.cim.IndicationTest
 * 
 * or else please uncomment @Service annotation and move to actual package out
 * of test where the real spring context will be created from
 */
//@Service
public class IndicationTest {

    @SuppressWarnings("unused")
    private ApplicationContext _cxt = null;

    // @Autowired
    // A reference to the connection manager.
    // private ConnectionManager _connectionManager;

    // The logger.
    private static final Logger _logger = LoggerFactory.getLogger(IndicationTest.class);

    // To verify immediate trigger or not
    private Boolean _isIntialLoad = Boolean.TRUE;
    
    private static final String UNIT_TEST_CONFIG_FILE = "sanity";
    private static final String providerIP = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.ipaddress");
    private static final String providerPortStr = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.port");
    private static final int providerPort = Integer.parseInt(providerPortStr);
    private static final String providerUser = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.username");
    private static final String providerPassword = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.password");
    private static final String providerUseSsl = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.usessl");
    private static final String providerNamespace = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.namespace");
    private static final String providerInterOpNamespace = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.interop.namespace");

    @Before
    public void setup() {
        _cxt = new ClassPathXmlApplicationContext("controller-conf.xml");
    }

    @After
    public void cleanup() {
        _cxt = null;
    }

    @Test
    @PostConstruct
    @DependsOn("connectionManager,cIMIndicationProcessor,dbclient")
    public void startListeningForIndications() {
        try {

            _logger.info("Started Listening for CIM Indications....");
            // Create a CIM connection info and add the connection.
            CimConnectionInfo connectionInfo = new CimConnectionInfo();
            connectionInfo.setType(CimConstants.ECOM_CONNECTION_TYPE);
            
            //connectionInfo.setHost("10.247.87.240");
            connectionInfo.setHost(providerIP);
            
            connectionInfo.setPort(providerPort);
            connectionInfo.setUser(providerUser);
            connectionInfo.setPassword(providerPassword);
            connectionInfo.setInteropNS(providerInterOpNamespace);
            connectionInfo.setImplNS(providerNamespace);
            connectionInfo.setUseSSL(Boolean.parseBoolean(providerUseSsl));
            _logger.info("Adding Connection....");
            // _connectionManager.addConnection(connectionInfo);

        } catch (Exception e) {
            _logger.error("Exception adding connection.", e);
        }

    }

    @Scheduled(fixedDelay = 96000)
    public void stopListener() throws Exception {

        // Initial Trigger, Don't shutdown CIM Indication Listener
        if (_isIntialLoad) {
            _isIntialLoad = Boolean.FALSE;
            return;
        }
        _logger.info("Shutting down CIM Indication Listener....");
        //_connectionManager.shutdown();

    }

    public static void verifyCassandraInsteredObject(String rowId, DbClient dbClient) {

        if (rowId == null || rowId.equals("")) {
            _logger.info("No Object persisted to verify, returning");
            return;
        }
        _logger.info("RowId recieved for testing : {}", rowId);
        DateTime dateTime = new DateTime(DateTimeZone.UTC);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(1000);
        try {
            TestQueryResult result = new TestQueryResult(latch);
            _logger.info("Start querying for inserted record");
            dbClient.queryTimeSeries(EventTimeSeries.class, dateTime, result, executor);
        } catch (Exception e) {
            _logger.error("Error --> " + e.getMessage());
            _logger.error(e.getMessage(), e);
        }
        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            _logger.error("Error --> " + e.getMessage());
        }
    }

    /*
     * Test Query Result Class that prints Object values to console
     */
    private static class TestQueryResult implements TimeSeriesQueryResult<Event> {
        private CountDownLatch _latch;

        TestQueryResult(CountDownLatch latch) {
            _latch = latch;
        }

        @Override
        public void data(Event data, long insertionTimeMs) {
            _logger.info("Printing Retrieved Data");
            _logger.info("Retrieved Cassandra object Type : {} ", data.getEventType());
            _logger.info("Retrieved Cassandra object Identifier : {} ", data.getEventId());
            _latch.countDown();
        }

        @Override
        public void done() {
            _logger.info("Done callback with latch count {}", _latch.getCount());
            Assert.assertTrue(_latch.getCount() == 0);
        }

        @Override
        public void error(Throwable e) {
            _logger.error("Error callback", e);
            Assert.assertNull(e);
        }
    }
}
