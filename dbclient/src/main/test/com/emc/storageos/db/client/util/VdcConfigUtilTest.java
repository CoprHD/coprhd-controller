/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.exceptions.DatabaseException;
import static com.emc.storageos.db.client.util.VdcConfigUtil.*;

public class VdcConfigUtilTest {
    private static VdcConfigUtil vdcConfigUtil = new VdcConfigUtil();
    private static DbClient vdcDbClient = new VdcDbClient();

    @BeforeClass
    public static void setup() {
        vdcConfigUtil.setDbclient(vdcDbClient);
    }

    @Test
    public void testGenProperties() {
        Map<String, String> vdcConfig = vdcConfigUtil.genVdcProperties();

        Assert.assertEquals(vdcConfig.get(VDC_MYID), "vdc2");
        Assert.assertEquals(vdcConfig.get(VDC_IDS), "vdc1,vdc2,vdc3");
        Assert.assertEquals(vdcConfig.get(String.format(VDC_NODE_COUNT_PTN, "vdc1")),
                "3");
        Assert.assertEquals(vdcConfig.get(String.format(VDC_NODE_COUNT_PTN, "vdc2")),
                "5");
        Assert.assertEquals(vdcConfig.get(String.format(VDC_NODE_COUNT_PTN, "vdc3")),
                "1");
        Assert.assertEquals(vdcConfig.get(String.format(VDC_IPADDR_PTN, "vdc1", 1)),
                "1.1.1.1");
        Assert.assertEquals(vdcConfig.get(String.format(VDC_IPADDR_PTN, "vdc1", 2)),
                "1.1.1.2");
        Assert.assertEquals(vdcConfig.get(String.format(VDC_IPADDR_PTN, "vdc1", 3)),
                "1.1.1.3");
        Assert.assertEquals(vdcConfig.get(String.format(VDC_IPADDR_PTN, "vdc2", 1)),
                "2.1.1.1");
        Assert.assertEquals(vdcConfig.get(String.format(VDC_IPADDR_PTN, "vdc2", 2)),
                "2.1.1.2");
        Assert.assertEquals(vdcConfig.get(String.format(VDC_IPADDR_PTN, "vdc2", 3)),
                "2.1.1.3");
        Assert.assertEquals(vdcConfig.get(String.format(VDC_IPADDR_PTN, "vdc2", 4)),
                "2.1.1.4");
        Assert.assertEquals(vdcConfig.get(String.format(VDC_IPADDR_PTN, "vdc2", 5)),
                "2.1.1.5");
        Assert.assertEquals(vdcConfig.get(String.format(VDC_IPADDR_PTN, "vdc3", 1)),
                "3.1.1.1");
    }

    @Test
    public void testHashCode() {
        Map<String, String> vdcConfig = vdcConfigUtil.genVdcProperties();
        String hashCode1 = vdcConfig.get(VDC_CONFIG_HASHCODE);
        vdcConfig = vdcConfigUtil.genVdcProperties();
        String hashCode2 = vdcConfig.get(VDC_CONFIG_HASHCODE);
        Assert.assertEquals(hashCode1, hashCode2);

        try {
            ((VdcDbClient) vdcDbClient).getVdcMap().remove(new URI(
                    "urn:storageos:VirtualDataCenter:11111111-1111-1111-1111-111111111111:"));

            String hashCode3 = vdcConfig.get(VDC_CONFIG_HASHCODE);
            Assert.assertNotSame(hashCode1, hashCode3);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static class VdcDbClient extends DbClientImpl {
        private Map<URI, VirtualDataCenter> vdcMap = new HashMap<>();

        public VdcDbClient() {
            try {
                URI vdcId = new URI("urn:storageos:VirtualDataCenter:11111111-1111-1111-1111-111111111111:");
                VirtualDataCenter vdc = new VirtualDataCenter();
                vdc.setId(vdcId);
                vdc.setShortId("vdc1");
                vdc.setHostCount(3);
                vdc.setLocal(false);
                vdc.setHostIPv4AddressesMap(new StringMap() {{
                    put("node1", "1.1.1.1");
                    put("node2", "1.1.1.2");
                    put("node3", "1.1.1.3");
                }});
                vdcMap.put(vdcId, vdc);

                vdcId = new URI("urn:storageos:VirtualDataCenter:22222222-2222-2222-2222-222222222222:");
                vdc = new VirtualDataCenter();
                vdc.setId(vdcId);
                vdc.setShortId("vdc2");
                vdc.setHostCount(5);
                vdc.setLocal(true);
                vdc.setHostIPv4AddressesMap(new StringMap() {{
                    put("node1", "2.1.1.1");
                    put("node2", "2.1.1.2");
                    put("node3", "2.1.1.3");
                    put("node4", "2.1.1.4");
                    put("node5", "2.1.1.5");
                }});
                vdcMap.put(vdcId, vdc);

                vdcId = new URI("urn:storageos:VirtualDataCenter:33333333-3333-3333-3333-333333333333:");
                vdc = new VirtualDataCenter();
                vdc.setId(vdcId);
                vdc.setShortId("vdc3");
                vdc.setHostCount(1);
                vdc.setLocal(false);
                vdc.setHostIPv4AddressesMap(new StringMap() {{
                    put("node1", "3.1.1.1");
                }});
                vdcMap.put(vdcId, vdc);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        public Map<URI, VirtualDataCenter> getVdcMap() {
            return vdcMap;
        }

        @Override
        public <T extends DataObject> T queryObject(Class<T> clazz, URI id)
                throws DatabaseException {
            if (! VirtualDataCenter.class.equals(clazz)) {
                throw new UnsupportedOperationException(String.format("CF %s not supported",
                        clazz.getName()));
            }
            return (T) vdcMap.get(id);
        }

        @Override
        public <T extends DataObject> List<URI> queryByType(Class<T> clazz, boolean activeOnly)
                throws DatabaseException {
            if (! VirtualDataCenter.class.equals(clazz)) {
                throw new UnsupportedOperationException(String.format("CF %s not supported",
                        clazz.getName()));
            }
            return new ArrayList<>(vdcMap.keySet());
        }
    }
}
