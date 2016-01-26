package com.emc.storageos.coordinator.client.model;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.coordinator.common.Configuration;

public class SiteTest {
    
    private Site site;
    
    @Before
    public void setUp() throws Exception {
        site = new Site();
    }

    @Test
    public void test() {
        site.setNodeCount(3);
        site.setUuid("uuid-1");
        site.setVip("10.247.101.158");
        site.getHostIPv4AddressMap().put("node1", "10.247.101.157");
        site.getHostIPv4AddressMap().put("node2", "10.247.101.156");
        site.getHostIPv4AddressMap().put("node3", "10.247.101.155");
        site.getHostIPv4AddressMap().put("node4", "10.247.101.154");
        site.getHostIPv4AddressMap().put("node5", "10.247.101.153");
        
        Configuration configuration = site.toConfiguration();
        
        Site target = new Site(configuration);
        
        for (int i=1; i<=site.getNodeCount(); i++) {
            assertEquals(site.getHostIPv4AddressMap().get("node"+i), target.getHostIPv4AddressMap().get("node"+i));
        }
    }

}
