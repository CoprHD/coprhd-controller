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

        // the signature is assertEquals(String expected, String actual)
        Assert.assertEquals("vdc2", vdcConfig.get(VdcConfigUtil.VDC_MYID));
        Assert.assertEquals("vdc1,vdc2,vdc3", vdcConfig.get(VdcConfigUtil.VDC_IDS));
        Assert.assertEquals("3", vdcConfig.get(String.format(VdcConfigUtil.VDC_NODE_COUNT_PTN, "vdc1")));
        Assert.assertEquals("5", vdcConfig.get(String.format(VdcConfigUtil.VDC_NODE_COUNT_PTN, "vdc2")));
        Assert.assertEquals("1", vdcConfig.get(String.format(VdcConfigUtil.VDC_NODE_COUNT_PTN, "vdc3")));
        Assert.assertEquals("1.1.1.1", vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc1", 1)));
        Assert.assertEquals("1.1.1.2", vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc1", 2)));
        Assert.assertEquals("1.1.1.3", vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc1", 3)));
        Assert.assertEquals("2.1.1.1", vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc2", 1)));
        Assert.assertEquals("2.1.1.2", vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc2", 2)));
        Assert.assertEquals("2.1.1.3", vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc2", 3)));
        Assert.assertEquals("2.1.1.4", vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc2", 4)));
        Assert.assertEquals("2.1.1.5", vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc2", 5)));
        Assert.assertEquals("3.1.1.1", vdcConfig.get(String.format(VdcConfigUtil.VDC_IPADDR_PTN, "vdc3", 1)));
        Assert.assertEquals("", vdcConfig.get(VdcConfigUtil.SITE_IDS));
        Assert.assertEquals("false", vdcConfig.get(VdcConfigUtil.SITE_IS_STANDBY));
        Assert.assertEquals("", vdcConfig.get(VdcConfigUtil.SITE_MYID));
    }

    private static class VdcCoordinatorClient extends CoordinatorClientImpl {
        private Map<String, List<Configuration>> vdcSiteMap = new HashMap<>();

        public VdcCoordinatorClient() {
            String siteId = "11111111-1111-1111-1111-111111111111";
            Site site = new Site();
            site.setUuid(siteId);
            site.setVdcShortId("vdc1");
            site.setNodeCount(3);
            site.setStandbyShortId("");
            site.setHostIPv4AddressMap(new StringMap() {
                {
                    put("node1", "1.1.1.1");
                    put("node2", "1.1.1.2");
                    put("node3", "1.1.1.3");
                }
            });
            List<Configuration> siteList = new ArrayList<>();
            siteList.add(site.toConfiguration());
            vdcSiteMap.put("vdc1", siteList);

            siteId = "22222222-2222-2222-2222-222222222222";
            site = new Site();
            site.setUuid(siteId);
            site.setVdcShortId("vdc2");
            site.setNodeCount(5);
            site.setStandbyShortId("");
            site.setHostIPv4AddressMap(new StringMap() {
                {
                    put("node1", "2.1.1.1");
                    put("node2", "2.1.1.2");
                    put("node3", "2.1.1.3");
                    put("node4", "2.1.1.4");
                    put("node5", "2.1.1.5");
                }
            });
            siteList = new ArrayList<>();
            siteList.add(site.toConfiguration());
            vdcSiteMap.put("vdc2", siteList);

            siteId = "33333333-3333-3333-3333-333333333333";
            site = new Site();
            site.setUuid(siteId);
            site.setVdcShortId("vdc3");
            site.setNodeCount(1);
            site.setStandbyShortId("");
            site.setHostIPv4AddressMap(new StringMap() {
                {
                    put("node1", "3.1.1.1");
                }
            });
            siteList = new ArrayList<>();
            siteList.add(site.toConfiguration());
            vdcSiteMap.put("vdc3", siteList);
        }

        @Override
        public String getSiteId() {
            return "22222222-2222-2222-2222-222222222222";
        }

        @Override
        public Configuration queryConfiguration(String kind, String id) {
            ConfigurationImpl configuration = new ConfigurationImpl();
            switch(kind) {
                case Constants.CONFIG_DR_ACTIVE_KIND:
                    switch(id) {
                        case "vdc1":
                            configuration.setConfig(Constants.CONFIG_DR_ACTIVE_SITEID,
                                    "11111111-1111-1111-1111-111111111111");
                            break;
                        case "vdc2":
                            configuration.setConfig(Constants.CONFIG_DR_ACTIVE_SITEID, 
                                    "22222222-2222-2222-2222-222222222222");
                            break;
                        case "vdc3":
                            configuration.setConfig(Constants.CONFIG_DR_ACTIVE_SITEID,
                                    "33333333-3333-3333-3333-333333333333");
                            break;
                    }
                    break;
                case Constants.CONFIG_GEO_LOCAL_VDC_KIND:
                    configuration.setConfig(Constants.CONFIG_GEO_LOCAL_VDC_SHORT_ID, "vdc2");
                    break;
                default:
                    String[] kindSplits = kind.split("/");
                    if (kindSplits.length == 2 && kindSplits[0].equals(Site.CONFIG_KIND)) {
                        List<Configuration> siteList = vdcSiteMap.get(kindSplits[1]);
                        for (Configuration site : siteList) {
                            if (site.getId().equals(id)) {
                                return site;
                            }
                        }
                        return null;
                    }
                    throw new UnsupportedOperationException(String.format("Unsupported configuration kind: %s", kind));
            }
            return configuration;
        }

        @Override
        public List<Configuration> queryAllConfiguration(String kind) {
            String[] kindSplits = kind.split("/");
            if (!kindSplits[0].equals(Site.CONFIG_KIND)) {
                throw new UnsupportedOperationException(String.format("Unsupported configuration kind: %s", kind));
            }
            if (kindSplits.length == 1) {
                // return a list of VDCs
                List<Configuration> vdcConfigs = new ArrayList<>();
                ConfigurationImpl config = new ConfigurationImpl();
                config.setId("vdc1");
                vdcConfigs.add(config);
                config = new ConfigurationImpl();
                config.setId("vdc2");
                vdcConfigs.add(config);
                config = new ConfigurationImpl();
                config.setId("vdc3");
                vdcConfigs.add(config);
                return vdcConfigs;
            }
            // return a list of sites
            return vdcSiteMap.get(kindSplits[1]);
        }
    }
}
