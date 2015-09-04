/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.client.service;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.remoting.rmi.RmiServiceExporter;

import com.emc.storageos.coordinator.client.beacon.ServiceBeacon;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Tests beacon and RMI endpoint failover
 */
public class BeaconTest extends CoordinatorTestBase {
    private static final Logger _log = LoggerFactory.getLogger(BeaconTest.class);

    /**
     * Dummy service interface
     */
    public interface DummyService {
        public void test();
    }

    /**
     * Dummy service impl with noop RMI endpoint
     */
    public class DummyServiceImpl implements DummyService {
        private ServiceImpl _svc;
        private ServiceBeacon _beacon;
        private int _port;
        private RmiServiceExporter _rmiExport;

        public DummyServiceImpl(int port, String tag, String endpointKey) {
            _svc = new ServiceImpl();
            _svc.setName("dummysvc");
            _svc.setVersion("1");
            _svc.setId(UUID.randomUUID().toString());
            _svc.setTag(tag);
            Map<String, URI> endpoint = new HashMap<String, URI>();
            URI endpointUri = URI.create(String.format("rmi://localhost:%1$d/test", port));
            endpoint.put(endpointKey, endpointUri);
            _svc.setEndpointMap(endpoint);
            _port = port;
        }

        public Service getServiceInfo() {
            return _svc;
        }

        public void test() {
            _log.info("Test method called");
        }

        public void start(int timeoutMs) throws Exception {
            _beacon = createBeacon(_svc, timeoutMs);
            _beacon.start();
        }

        public void stop() {
            _beacon.stop();
        }

        public void startRmiServer() throws Exception {
            _rmiExport = new RmiServiceExporter();
            _rmiExport.setServiceName("test");
            _rmiExport.setServiceInterface(DummyService.class);
            _rmiExport.setRegistryPort(_port);
            _rmiExport.setService(this);
            _rmiExport.afterPropertiesSet();
        }

        public void stopRmiServer() throws Exception {
            _rmiExport.destroy();
            _rmiExport = null;
        }
    }

    @Test
    public void testBeacon() throws Exception {
        final String tag = "foo";
        final String endpointKey = "key";

        DummyServiceImpl service = new DummyServiceImpl(10099, tag, endpointKey);
        service.startRmiServer();
        service.start(1000 * 5);

        Service si = service.getServiceInfo();
        CoordinatorClient client = connectClient();

        DummyService found = client.locateService(DummyService.class, si.getName(), si.getVersion(), tag, endpointKey);
        Assert.assertNotNull(found);
        found.test();

        List<Service> services = client.locateAllServices(si.getName(), si.getVersion(), tag, endpointKey);
        Assert.assertEquals(services.size(), 1);
        Assert.assertEquals(services.get(0).getName(), si.getName());
        Assert.assertEquals(services.get(0).getVersion(), si.getVersion());
        Assert.assertEquals(services.get(0).getEndpoint(), si.getEndpoint());
        Assert.assertTrue(services.get(0).isTagged(tag));
        Assert.assertEquals(services.get(0).getEndpoint(endpointKey), si.getEndpoint());

        try {
            client.locateService(DummyService.class, si.getName(), si.getVersion(), "random", endpointKey);
        } catch (CoordinatorException expected) {
        }

        try {
            client.locateService(DummyService.class, si.getName(), si.getVersion(), tag, "random");
        } catch (CoordinatorException expected) {
        }
        service.stopRmiServer();
        service.stop();
    }

    @Test
    public void testRmiFailover() throws Exception {
        final String tag = "foo";
        final String endpointKey = "bar";

        DummyServiceImpl service = new DummyServiceImpl(10100, tag, endpointKey);
        service.startRmiServer();
        service.start(1000 * 10);

        DummyServiceImpl service2 = new DummyServiceImpl(10101, tag, endpointKey);
        service2.startRmiServer();
        service2.start(1000 * 10);

        Service si = service.getServiceInfo();
        CoordinatorClient client = connectClient();

        DummyService found = client.locateService(DummyService.class, si.getName(), si.getVersion(), tag, endpointKey);
        Assert.assertNotNull(found);
        found.test();

        service.stopRmiServer();
        for (int index = 0; index < 100; index++) {
            found.test();
        }
        service2.stopRmiServer();

        try {
            found.test();
            Assert.fail("Expected service lookup to fail");
        } catch (FatalCoordinatorException ignore) {
            Assert.assertEquals(ServiceCode.COORDINATOR_ERROR, ignore.getServiceCode());
        }
        service2.startRmiServer();
        found.test();
    }
}
