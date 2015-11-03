/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.db.client.model.StringMap;

public class VdcConfigUtilTest {
    private static VdcConfigUtil vdcConfigUtil;

    @BeforeClass
    public static void setup() {
        CoordinatorClient coordinatorClient = new VdcCoordinatorClient();
        vdcConfigUtil = new VdcConfigUtil(coordinatorClient);
    }

    @Test
    public void testGenProperties() {
        Map<String, String> vdcConfig = vdcConfigUtil.genVdcProperties();

        Assert.assertEquals(vdcConfig.get(VdcConfigUtil.VDC_MYID), "vdc2");
        Assert.assertEquals(vdcConfig.get(VdcConfigUtil.VDC_IDS), "vdc1,vdc2,vdc3");
        Assert.assertEquals(vdcConfig.get(String.format(VdcConfigUtil.VDC_NODE_COUNT_PTN, "vdc1")),
                "3");
        Assert.assertEquals(vdcConfig.get(String.format(VdcConfigUtil.VDC_NODE_COUNT_PTN, "vdc2")),
                "5");
        Assert.assertEquals(vdcConfig.get(String.format(VdcConfigUtil.VDC_NODE_COUNT_PTN, "vdc3")),
                "1");
        Assert.assertEquals(vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc1", 1)),
                "1.1.1.1");
        Assert.assertEquals(vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc1", 2)),
                "1.1.1.2");
        Assert.assertEquals(vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc1", 3)),
                "1.1.1.3");
        Assert.assertEquals(vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc2", 1)),
                "2.1.1.1");
        Assert.assertEquals(vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc2", 2)),
                "2.1.1.2");
        Assert.assertEquals(vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc2", 3)),
                "2.1.1.3");
        Assert.assertEquals(vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc2", 4)),
                "2.1.1.4");
        Assert.assertEquals(vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc2", 5)),
                "2.1.1.5");
        Assert.assertEquals(vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc3", 1)),
                "3.1.1.1");
    }

    private static class VdcCoordinatorClient extends CoordinatorClientImpl {
        private Map<String, Configuration> siteMap = new HashMap<>();

        public VdcCoordinatorClient() {
            String siteId = "11111111-1111-1111-1111-111111111111";
            Site site = new Site();
            site.setUuid(siteId);
            site.setVdcShortId("vdc1");
            site.setNodeCount(3);
            site.setHostIPv4AddressMap(new StringMap() {
                {
                    put("node1", "1.1.1.1");
                    put("node2", "1.1.1.2");
                    put("node3", "1.1.1.3");
                }
            });
            siteMap.put(siteId, site.toConfiguration());

            siteId = "22222222-2222-2222-2222-222222222222";
            site = new Site();
            site.setUuid(siteId);
            site.setVdcShortId("vdc2");
            site.setNodeCount(5);
            site.setHostIPv4AddressMap(new StringMap() {
                {
                    put("node1", "2.1.1.1");
                    put("node2", "2.1.1.2");
                    put("node3", "2.1.1.3");
                    put("node4", "2.1.1.4");
                    put("node5", "2.1.1.5");
                }
            });
            siteMap.put(siteId, site.toConfiguration());

            siteId = "33333333-3333-3333-3333-333333333333";
            site = new Site();
            site.setUuid(siteId);
            site.setVdcShortId("vdc3");
            site.setNodeCount(1);
            site.setHostIPv4AddressMap(new StringMap() {
                {
                    put("node1", "3.1.1.1");
                }
            });
            siteMap.put(siteId, site.toConfiguration());
        }

        @Override
        public String getSiteId() {
            return "22222222-2222-2222-2222-222222222222";
        }

        @Override
        public Configuration queryConfiguration(String kind, String id) {
            ConfigurationImpl configuration = new ConfigurationImpl();
            switch(kind) {
                case Constants.CONFIG_DR_PRIMARY_KIND:
                    configuration.setConfig(Constants.CONFIG_DR_PRIMARY_SITEID, "22222222-2222-2222-2222-222222222222");
                    break;
                case Constants.CONFIG_GEO_LOCAL_VDC_KIND:
                    configuration.setConfig(Constants.CONFIG_GEO_LOCAL_VDC_SHORT_ID, "vdc2");
                    break;
                case Site.CONFIG_KIND:
                    return siteMap.get(id);
                default:
                    throw new UnsupportedOperationException(String.format("Unsupported configuration kind: %s", kind));
            }
            return configuration;
        }

        @Override
        public List<Configuration> queryAllConfiguration(String kind) {
            if (!Site.CONFIG_KIND.equals(kind)) {
                throw new UnsupportedOperationException(String.format("Unsupported configuration kind: %s", kind));
            }
            return new ArrayList<>(siteMap.values());
        }
    }
}
