/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import com.emc.storageos.systemservices.impl.healthmonitor.models.*;
import com.emc.vipr.model.sys.healthmonitor.DiskStats;
import com.emc.vipr.model.sys.healthmonitor.NodeHealth;
import com.emc.vipr.model.sys.healthmonitor.NodeStats;
import com.emc.vipr.model.sys.healthmonitor.ServiceStats;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class HealthMonitorServiceTest extends HealthMonitorService {

    private static final List<String> AVAILABLE_SERVICES = new ArrayList<String>() {
        {
            add("syssvc");
            add("apisvc");
        }
    };

    private static final String NODE_ID = "syssvc-node1";
    private static final String NODE_NAME = "testName";
    private static final String NODE_IP = "standalone";

    @Test
    public void testNodeStats() {
        NodeStats nodeStats = getNodeStats(NODE_ID, NODE_NAME, NODE_IP, 0,
                AVAILABLE_SERVICES);
        verifyNodeStats(nodeStats);
    }

    @Test
    public void testNodeStatsWithInterval() {
        NodeStats nodeStats = getNodeStats(NODE_ID, NODE_NAME, NODE_IP, 10,
                AVAILABLE_SERVICES);
        verifyNodeStats(nodeStats);
    }

    private void verifyNodeStats(NodeStats nodeStats) {
        Assert.assertTrue(nodeStats.getDiskStatsList() != null && !nodeStats
                .getDiskStatsList().isEmpty());
        Assert.assertTrue(nodeStats.getServiceStatsList() != null && !nodeStats
                .getServiceStatsList().isEmpty());

        // service stats
        for (ServiceStats serviceStats : nodeStats.getServiceStatsList()) {
            Assert.assertTrue(serviceStats.getServiceName() != null && !serviceStats
                    .getServiceName().isEmpty());
            Assert.assertNotNull(serviceStats.getCommand());
            Assert.assertTrue(serviceStats.getFileDescriptors() >= 0);
            Assert.assertNotNull(serviceStats.getProcessStatus());
            Assert.assertNotNull(serviceStats.getProcessStatus().getStartTime());
            Assert.assertNotNull(serviceStats.getProcessStatus().getUpTime());
            Assert.assertTrue(serviceStats.getProcessStatus().getNumberOfThreads() >= 0);
            Assert.assertTrue(serviceStats.getProcessStatus().getResidentMem() >= 0);
            Assert.assertTrue(serviceStats.getProcessStatus().getVirtualMemSizeInBytes() >= 0);
        }

        // Node stats
        Assert.assertEquals(NODE_ID, nodeStats.getNodeId());
        Assert.assertEquals(NODE_NAME, nodeStats.getNodeName());
        Assert.assertEquals(NODE_IP, nodeStats.getIp());
        Assert.assertNotNull(nodeStats.getMemoryStats());
        Assert.assertNotNull(nodeStats.getMemoryStats().getMemFree());
        Assert.assertNotNull(nodeStats.getMemoryStats().getMemBuffers());
        Assert.assertNotNull(nodeStats.getMemoryStats().getMemTotal());
        Assert.assertNotNull(nodeStats.getLoadAvgStats());
        Assert.assertTrue(nodeStats.getLoadAvgStats().getLoadAvgTasksPastFifteenMinutes() >= 0);
        Assert.assertTrue(nodeStats.getLoadAvgStats().getLoadAvgTasksPastFiveMinutes() >= 0);
        Assert.assertTrue(nodeStats.getLoadAvgStats().getLoadAvgTasksPastMinute() >= 0);

        // disk stats
        for (DiskStats diskStats : nodeStats.getDiskStatsList()) {
            Assert.assertNotNull(diskStats.getDiskId());
            Assert.assertTrue(diskStats.getSectorsReadPerSec() >= 0);
            Assert.assertTrue(diskStats.getSectorsWritePerSec() >= 0);
            Assert.assertTrue(diskStats.getReadPerSec() >= 0);
            Assert.assertTrue(diskStats.getWritePerSec() >= 0);
            Assert.assertTrue(diskStats.getUtilPerc() >= 0);
            Assert.assertTrue(diskStats.getAvgSvcTime() >= 0);
            Assert.assertTrue(diskStats.getAvgWait() >= 0);
        }

        // Test service list order
        Assert.assertEquals(AVAILABLE_SERVICES.get(0), nodeStats.getServiceStatsList()
                .get(0).getServiceName());
    }

    @Test
    public void testNodeStatsWithNoAvailableServices() {
        NodeStats nodeStats = getNodeStats(NODE_ID, NODE_NAME, NODE_IP, 0,
                null);
        Assert.assertTrue(nodeStats.getDiskStatsList() != null && !nodeStats
                .getDiskStatsList().isEmpty());
        Assert.assertTrue(nodeStats.getServiceStatsList() != null && !nodeStats
                .getServiceStatsList().isEmpty());

        // service stats
        for (ServiceStats serviceStats : nodeStats.getServiceStatsList()) {
            Assert.assertTrue(serviceStats.getServiceName() != null && !serviceStats
                    .getServiceName().isEmpty());
        }

        // Node stats
        Assert.assertEquals(NODE_ID, nodeStats.getNodeId());
        Assert.assertEquals(NODE_NAME, nodeStats.getNodeName());
        Assert.assertEquals(NODE_IP, nodeStats.getIp());
        Assert.assertNotNull(nodeStats.getMemoryStats().getMemFree());

        // disk stats
        for (DiskStats diskStats : nodeStats.getDiskStatsList()) {
            Assert.assertNotNull(diskStats.getDiskId());
        }
    }

    @Test
    public void testNodeHealth(){
        NodeHealth nodeHealth = getNodeHealth(NODE_ID, NODE_ID, NODE_IP, AVAILABLE_SERVICES);
        Assert.assertNotNull(nodeHealth);
        Assert.assertEquals(NODE_ID, nodeHealth.getNodeId());
        Assert.assertEquals(NODE_IP, nodeHealth.getIp());
        Assert.assertNotNull(nodeHealth.getStatus());
        Assert.assertNotNull(nodeHealth.getServiceHealthList());

        // Test service list order
        Assert.assertEquals(AVAILABLE_SERVICES.get(0), nodeHealth.getServiceHealthList
                ().get(0).getServiceName());
    }

    @Test
    public void testNodeHealthWithInvalidServices() {
        List<String> invalidServices = new ArrayList<String>(){{
            add("syssvc");
            add("mysvc");
        }};
        NodeHealth nodeHealth = getNodeHealth(NODE_ID, NODE_ID, NODE_IP, invalidServices);
        Assert.assertNotNull(nodeHealth);
        Assert.assertTrue(Status.DEGRADED.toString().equals(nodeHealth.getStatus()));
    }
}
