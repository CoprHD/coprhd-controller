/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.service;

import java.net.UnknownHostException;

import org.junit.Test;
import org.junit.Assert;
import org.junit.Ignore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;

public class DualInetAddressTest {
    private static final Logger log = LoggerFactory.getLogger(DualInetAddressTest.class);
    private static String[] normalizedInet4Addresses = { "10.10.191.52", "10.10.191.0", "255.255.255.255",
            "255.255.255.0", "0.0.0.0", };
    private static String[] invalidInet4Addresses = { null, "10.10.10.10.10", "f0.f0.f0.f0", "999.9.9.9",
            "abc", "vipr", "1000", "bourne-23.lss.emc.com", "google.com" };
    private static String[] normalizedInet6Addresses = { "fff:fadd::f000:1", "2000::", "::2000", "2002:c0a8:101::42",
            "2003:dead:beef:4dad:23:46:bb:101", "ABCD:EF01:2345:6789:ABCD:EF01:2345:6789",
            "2001:DB8::8:800:200C:0:417A", "FF01::101", "::1", };
    private static String[] invalidInet6Addresses = { null, "fffff:1:1:1:0:0:0:0", "ffff:fffff:1:1:0:0:0:0", "ffff:ffff:1:1:0:0:0:f0000",
            "fffff:ffff:1:1::0", "ffff:fffff:1:1::0", "ffff:ffff:1:1::f0000", "1:2:3:4:5:6:7:8:9",
            "1:2:3:4:5:6:7:1.2.3.4", "f", "vipr", "abc", "bourne-23.lss.emc.com", "google.com" };
    private static String[] invalidFrom4Addresses = { null, "10.10.10.10.10", "f0.f0.f0.f0", "999.9.9.9",
            "abc", "vipr", "1000", "f", "vipr", "abc" };
    private static String[] invalidFrom6Addresses = { null, "fffff:1:1:1:0:0:0:0", "ffff:fffff:1:1:0:0:0:0", "ffff:ffff:1:1:0:0:0:f0000",
            "fffff:ffff:1:1::0", "ffff:fffff:1:1::0", "ffff:ffff:1:1::f0000", "1:2:3:4:5:6:7:8:9",
            "1:2:3:4:5:6:7:1.2.3.4", "1000", "f", "vipr", "abc", };

    private static String c_ip4 = "10.10.191.52";
    private static String c_ip6 = "2620:0:170:2842::1234";
    private static String s_ip4 = "10.247.97.152";
    private static String s_ip6 = "2620:0:170:2842::7152";
    private static String s_host = "lglw7152.lss.emc.com";

    @Test
    public void testNormalization() {
        System.out.println("*** testNormalization: Start");

        // Check for blank strings ("")
        Assert.assertTrue(DualInetAddress.normalizeInet4Address("") == null);
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("") == null);

        for (String addr : normalizedInet4Addresses) {
            Assert.assertTrue(addr.equals(DualInetAddress.normalizeInet4Address(addr)));
        }
        for (String addr : invalidInet4Addresses) {
            Assert.assertTrue(DualInetAddress.normalizeInet4Address(addr) == null);
        }
        Assert.assertTrue(DualInetAddress.normalizeInet4Address("10.20.191").equals("10.20.191.0"));
        Assert.assertTrue(DualInetAddress.normalizeInet4Address("10.20").equals("10.20.0.0"));
        Assert.assertTrue(DualInetAddress.normalizeInet4Address("10").equals("10.0.0.0"));
        Assert.assertTrue(DualInetAddress.normalizeInet4Address("0.0.0").equals("0.0.0.0"));

        for (String addr : normalizedInet6Addresses) {
            // System.out.println(addr + " : " + DualInetAddress.normalizeInet6Address(addr));
            Assert.assertTrue(addr.toLowerCase().equals(DualInetAddress.normalizeInet6Address(addr)));
        }
        for (String addr : invalidInet4Addresses) {
            // System.out.println(addr + " : " + DualInetAddress.normalizeInet6Address(addr));
            Assert.assertTrue(DualInetAddress.normalizeInet6Address(addr) == null);
        }

        // System.out.println(DualInetAddress.normalizeInet6Address("0fff:0:0:0:0:0:0:0111"));
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0fff:0:0:0:0:0:0:0111").equals("fff::111"));
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0fff:0:0:0:0:0::0111").equals("fff::111"));
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0fff:0::0111").equals("fff::111"));
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0fff::0111").equals("fff::111"));
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0fff:0:0:0::0:111").equals("fff::111"));
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("fff:0:0:0::0111").equals("fff::111"));

        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0fff:0:0:d:0:0:0:0111").equals("fff:0:0:d::111"));
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0fff:0::d:0:0:0:0111").equals("fff:0:0:d::111"));
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0fff::d:0:0:0:0111").equals("fff:0:0:d::111"));
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0fff:0:0:00d:0:0:0:0111").equals("fff:0:0:d::111"));
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0fff:0:0:000d:0::0111").equals("fff:0:0:d::111"));

        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0:0000:0:0:000d:0:0:0111").equals("::d:0:0:111"));
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0:0000:0:0:000d:0:0:111").equals("::d:0:0:111"));
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0:0000:0:0:000d::0111").equals("::d:0:0:111"));
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0:0000:0:0:000d:0:0:0111").equals("::d:0:0:111"));

        // System.out.println(DualInetAddress.normalizeInet6Address("0:0000:0:0:000d:0:1.2.3.4"));
        Assert.assertTrue(DualInetAddress.normalizeInet6Address("0:0000:0:0:000d:0:1.2.3.4").equals("::d:0:102:304"));

        System.out.println("*** testNormalization: End");
    }

    @Test
    public void testFromAddress() {
        System.out.println("*** testFromAddress: Start");
        for (String addr : normalizedInet4Addresses) {
            try {
                DualInetAddress d = DualInetAddress.fromAddress(addr);
                Assert.assertTrue(addr.equals("0.0.0.0") && !d.hasInet4() || d.hasInet4());
                Assert.assertTrue(!d.hasInet6());
                Assert.assertTrue(addr.equals("0.0.0.0") && d.getInet4() == null || d.getInet4().equals(addr.toLowerCase()));
                Assert.assertTrue(d.getInet6() == null);
            } catch (UnknownHostException e) {
                Assert.assertTrue(false);
            }
        }

        for (String addr : invalidInet4Addresses) {
            try {
                DualInetAddress d = DualInetAddress.fromAddress(addr);
                Assert.assertTrue(addr == null && !d.hasInet4() && !d.hasInet6() && d.getInet4() == null && d.getInet6() == null);
            } catch (UnknownHostException e) {
                Assert.assertTrue(addr != null);
            }
        }

        for (String addr : normalizedInet6Addresses) {
            try {
                DualInetAddress d = DualInetAddress.fromAddress(addr);
                Assert.assertTrue(addr.equals("::0") && !d.hasInet6() || d.hasInet6());
                Assert.assertTrue(!d.hasInet4());
                Assert.assertTrue(addr.equals("::0") && d.getInet6() == null || d.getInet6().equals(addr.toLowerCase()));
                Assert.assertTrue(d.getInet4() == null);
            } catch (UnknownHostException e) {
                Assert.assertTrue(false);
            }
        }

        for (String addr : invalidInet6Addresses) {
            try {
                DualInetAddress d = DualInetAddress.fromAddress(addr);
                // System.out.println(addr + " : " + d);
                Assert.assertTrue(addr == null && !d.hasInet4() && !d.hasInet6() && d.getInet4() == null && d.getInet6() == null);
            } catch (UnknownHostException e) {
                Assert.assertTrue(addr != null);
            }
        }

        for (int i = 0; i < 3; i++) {
            String ip4 = normalizedInet4Addresses[i];
            String ip6 = normalizedInet6Addresses[i];
            try {
                DualInetAddress d1 = DualInetAddress.fromAddresses(ip4, null);
                DualInetAddress d2 = DualInetAddress.fromAddresses(null, ip6);
                DualInetAddress d3 = DualInetAddress.fromAddresses(ip4, ip6);

                Assert.assertTrue(ip4.equals("0.0.0.0") && !d1.hasInet4() || d1.hasInet4());
                Assert.assertTrue(!d1.hasInet6());
                Assert.assertTrue(ip4.equals("0.0.0.0") && d1.getInet4() == null || d1.getInet4().equals(ip4.toLowerCase()));
                Assert.assertTrue(d1.getInet6() == null);

                Assert.assertTrue(ip6.equals("::0") && !d2.hasInet6() || d2.hasInet6());
                Assert.assertTrue(!d2.hasInet4());
                Assert.assertTrue(ip6.equals("::0") && d2.getInet6() == null || d2.getInet6().equals(ip6.toLowerCase()));
                Assert.assertTrue(d2.getInet4() == null);

                Assert.assertTrue(ip4.equals("0.0.0.0") && !d3.hasInet4() || d3.hasInet4());
                Assert.assertTrue(ip6.equals("::0") && !d3.hasInet6() || d3.hasInet6());
                Assert.assertTrue(ip4.equals("0.0.0.0") && d3.getInet4() == null || d3.getInet4().equals(ip4.toLowerCase()));
                Assert.assertTrue(ip6.equals("::0") && d3.getInet6() == null || d3.getInet6().equals(ip6.toLowerCase()));
            } catch (UnknownHostException e) {
                Assert.assertTrue(false);
            }
        }

        for (int i = 0; i < 3; i++) {
            String ip4 = invalidInet4Addresses[i];
            String ip6 = normalizedInet6Addresses[i];
            try {
                DualInetAddress d1 = DualInetAddress.fromAddresses(ip4, null);
                DualInetAddress d2 = DualInetAddress.fromAddresses(ip4, ip6);
                // System.out.println(d1 + "   " + d2);
                Assert.assertTrue(ip4 == null && !d1.hasInet4() && !d2.hasInet4() &&
                        d1.getInet4() == null && d2.getInet4() == null &&
                        !d1.hasInet6() && d2.hasInet6() &&
                        d1.getInet6() == null && d2.getInet6().equals(ip6.toLowerCase()));
            } catch (UnknownHostException e) {
                Assert.assertTrue(ip4 != null);
            }
        }

        for (int i = 0; i < 3; i++) {
            String ip4 = normalizedInet4Addresses[i];
            String ip6 = invalidInet6Addresses[i];
            try {
                DualInetAddress d1 = DualInetAddress.fromAddresses(null, ip6);
                DualInetAddress d2 = DualInetAddress.fromAddresses(ip4, ip6);
                // System.out.println(d1 + "   " + d2);
                Assert.assertTrue(ip6 == null && !d1.hasInet6() && !d2.hasInet6() &&
                        d1.getInet6() == null && d2.getInet6() == null &&
                        !d1.hasInet4() && d2.hasInet4() &&
                        d1.getInet4() == null && d2.getInet4().equals(ip4.toLowerCase()));
            } catch (UnknownHostException e) {
                Assert.assertTrue(ip4 != null);
            }
        }

        System.out.println("*** testFromAddress: End");
    }

    // This test requires internal accessible hosts and therefore is not viable for public/external builds of CoprHD, therefore disabling by default.
    @Ignore
    @Test
    public void testFromHostname() {
        System.out.println("*** testFromHostname: Start");
        for (String addr : normalizedInet4Addresses) {
            try {
                DualInetAddress d = DualInetAddress.fromHostname(addr);
                Assert.assertTrue(addr.equals("0.0.0.0") && !d.hasInet4() || d.hasInet4());
                Assert.assertTrue(!d.hasInet6());
                Assert.assertTrue(addr.equals("0.0.0.0") && d.getInet4() == null || d.getInet4().equals(addr.toLowerCase()));
                Assert.assertTrue(d.getInet6() == null);
            } catch (UnknownHostException e) {
                Assert.assertTrue(false);
            }
        }

        for (String addr : invalidFrom4Addresses) {
            try {
                DualInetAddress d = DualInetAddress.fromHostname(addr);
                Assert.assertTrue(addr == null && !d.hasInet4() && !d.hasInet6() && d.getInet4() == null && d.getInet6() == null);
            } catch (UnknownHostException e) {
                Assert.assertTrue(addr != null);
            }
        }

        for (String addr : normalizedInet6Addresses) {
            try {
                DualInetAddress d = DualInetAddress.fromHostname(addr);
                Assert.assertTrue(addr.equals("::0") && !d.hasInet6() || d.hasInet6());
                Assert.assertTrue(!d.hasInet4());
                Assert.assertTrue(addr.equals("::0") && d.getInet6() == null || d.getInet6().equals(addr.toLowerCase()));
                Assert.assertTrue(d.getInet4() == null);
            } catch (UnknownHostException e) {
                Assert.assertTrue(false);
            }
        }

        for (String addr : invalidFrom6Addresses) {
            try {
                DualInetAddress d = DualInetAddress.fromHostname(addr);
                // System.out.println(addr + " : " + d);
                Assert.assertTrue(addr == null && !d.hasInet4() && !d.hasInet6() && d.getInet4() == null && d.getInet6() == null);
            } catch (UnknownHostException e) {
                Assert.assertTrue(addr != null);
            }
        }

        try {
            DualInetAddress d = DualInetAddress.fromHostname("lglw7152.lss.emc.com");
            // System.out.println(d);
            Assert.assertTrue(d.hasInet4() && d.hasInet6());
            Assert.assertTrue(d.getInet4().equals("10.247.97.152"));
            Assert.assertTrue(d.getInet6().equals("2620:0:170:2842::7152"));
        } catch (UnknownHostException e) {
            System.err.println(e);
            log.error("Caught UnknownHostException: ", e);
            Assert.assertTrue(false);
        }

        try {
            DualInetAddress d = DualInetAddress.fromHostname("bourne-52.lss.emc.com");
            Assert.assertTrue(d.hasInet4() && !d.hasInet6());
            Assert.assertTrue(d.getInet4().equals("10.10.191.52"));
            Assert.assertTrue(d.getInet6() == null);
        } catch (UnknownHostException e) {
            System.err.println(e);
            log.error("Caught UnknownHostException: ", e);
            Assert.assertTrue(false);
        }

        try {
            DualInetAddress d = DualInetAddress.fromHostname("nonexistant.lss.emc.com");
            System.out.println(d);
            Assert.assertTrue(false);
            ;
        } catch (UnknownHostException e) {
            Assert.assertTrue(true);
        }

        // Test empty ip strings
        try {
            DualInetAddress invalid_address1 = DualInetAddress.fromAddresses("", normalizedInet6Addresses[0]);
            Assert.assertTrue(invalid_address1.getInet4() == null);
            DualInetAddress invalid_address2 = DualInetAddress.fromAddresses(normalizedInet4Addresses[0], "");
            Assert.assertTrue(invalid_address2.getInet6() == null);
            DualInetAddress invalid_address3 = DualInetAddress.fromAddresses("", "");
            Assert.assertTrue(invalid_address3.getInet4() == null);
            Assert.assertTrue(invalid_address3.getInet6() == null);
        } catch (UnknownHostException e) {
            Assert.assertTrue(false);
        }

        System.out.println("*** testFromHostname: End");
    }

    // This test requires internal accessible hosts and therefore is not viable for public/external builds of CoprHD, therefore disabling by default.
    @Ignore
    @Test
    public void testConnectable() {
        System.out.println("*** testConnectable: Start");

        try {
            DualInetAddress c4 = DualInetAddress.fromAddresses(c_ip4, null);
            DualInetAddress c6 = DualInetAddress.fromAddresses(null, c_ip6);
            DualInetAddress cm = DualInetAddress.fromAddresses(c_ip4, c_ip6);
            DualInetAddress s4 = DualInetAddress.fromAddresses(s_ip4, null);
            DualInetAddress s6 = DualInetAddress.fromAddresses(null, s_ip6);
            DualInetAddress sm = DualInetAddress.fromAddresses(s_ip4, s_ip6);

            try {
                DualInetAddress.ConnectableInetAddresses q44 = DualInetAddress.getConnectableAddresses(c4, s4);
                DualInetAddress.ConnectableInetAddresses q66 = DualInetAddress.getConnectableAddresses(c6, s6);
                DualInetAddress.ConnectableInetAddresses q4m = DualInetAddress.getConnectableAddresses(c4, sm);
                DualInetAddress.ConnectableInetAddresses qm4 = DualInetAddress.getConnectableAddresses(cm, s4);
                DualInetAddress.ConnectableInetAddresses qmm = DualInetAddress.getConnectableAddresses(cm, sm);
                DualInetAddress.ConnectableInetAddresses q6m = DualInetAddress.getConnectableAddresses(c6, sm);
                DualInetAddress.ConnectableInetAddresses qm6 = DualInetAddress.getConnectableAddresses(cm, s6);
                Assert.assertEquals(q44.getClient(), c_ip4);
                Assert.assertEquals(q66.getClient(), c_ip6);
                Assert.assertEquals(q4m.getClient(), c_ip4);
                Assert.assertEquals(qm4.getClient(), c_ip4);
                Assert.assertEquals(qmm.getClient(), c_ip4);
                Assert.assertEquals(q6m.getClient(), c_ip6);
                Assert.assertEquals(qm6.getClient(), c_ip6);
                Assert.assertEquals(q44.getServer(), s_ip4);
                Assert.assertEquals(q66.getServer(), s_ip6);
                Assert.assertEquals(q4m.getServer(), s_ip4);
                Assert.assertEquals(qm4.getServer(), s_ip4);
                Assert.assertEquals(qmm.getServer(), s_ip4);
                Assert.assertEquals(q6m.getServer(), s_ip6);
                Assert.assertEquals(qm6.getServer(), s_ip6);
            } catch (Exception e) {
                Assert.assertTrue(false);
            }

            try {
                DualInetAddress.ConnectableInetAddresses q46 = DualInetAddress.getConnectableAddresses(c4, s6);
                Assert.assertTrue(false);
            } catch (Exception e) {
                Assert.assertTrue(true);
            }

            try {
                DualInetAddress.ConnectableInetAddresses q46 = DualInetAddress.getConnectableAddresses(c6, s4);
                Assert.assertTrue(false);
            } catch (Exception e) {
                Assert.assertTrue(true);
            }

            try {
                String s4m = c4.getConnectableAddress(s_host);
                String smm = cm.getConnectableAddress(s_host);
                String s6m = c6.getConnectableAddress(s_host);
                Assert.assertEquals(s4m, s_ip4);
                Assert.assertEquals(smm, s_ip4);
                Assert.assertEquals(s6m, s_ip6);
            } catch (Exception e) {
                Assert.assertTrue(false);
            }

        } catch (UnknownHostException e) {
            Assert.assertTrue(false);
        }

        System.out.println("*** testConnectable: End");
    }

    @Test
    public void testEquals() {
        System.out.println("*** testEqual: Start");

        try {

            DualInetAddress cn = DualInetAddress.fromAddress(null);
            DualInetAddress sn = DualInetAddress.fromAddress(null);
            DualInetAddress c4 = DualInetAddress.fromAddresses(c_ip4, null);
            DualInetAddress c6 = DualInetAddress.fromAddresses(null, c_ip6);
            DualInetAddress cm = DualInetAddress.fromAddresses(c_ip4, c_ip6);
            DualInetAddress s4 = DualInetAddress.fromAddresses(c_ip4, null);
            DualInetAddress s6 = DualInetAddress.fromAddresses(null, c_ip6);
            DualInetAddress sm = DualInetAddress.fromAddresses(c_ip4, c_ip6);

            // Checks for empty ip strings
            DualInetAddress blank_v4 = DualInetAddress.fromAddresses("", c_ip6);
            DualInetAddress blank_v6 = DualInetAddress.fromAddresses(c_ip4, "");
            DualInetAddress blank_all = DualInetAddress.fromAddresses("", "");
            Assert.assertTrue(blank_v4.equals(s6));
            Assert.assertTrue(blank_v6.equals(s4));
            Assert.assertTrue(blank_all.equals(cn));

            // Test strings, nulls for equals
            Assert.assertFalse(cn == null);

            Assert.assertTrue(cn.equals(cn));
            Assert.assertTrue(sn.equals(sn));
            Assert.assertTrue(sn.equals(cn));
            Assert.assertTrue(cn.equals(sn));
            Assert.assertTrue(c4.equals(c4));
            Assert.assertTrue(s4.equals(s4));
            Assert.assertTrue(s4.equals(c4));
            Assert.assertTrue(c4.equals(s4));
            Assert.assertTrue(c6.equals(c6));
            Assert.assertTrue(s6.equals(s6));
            Assert.assertTrue(s6.equals(c6));
            Assert.assertTrue(c6.equals(s6));
            Assert.assertTrue(cm.equals(cm));
            Assert.assertTrue(sm.equals(sm));
            Assert.assertTrue(sm.equals(cm));
            Assert.assertTrue(cm.equals(sm));
            Assert.assertFalse(cn.equals(c4));
            Assert.assertFalse(cn.equals(cm));
            Assert.assertFalse(cn.equals(c6));
            Assert.assertFalse(c4.equals(cn));
            Assert.assertFalse(c4.equals(cm));
            Assert.assertFalse(c4.equals(c6));
            Assert.assertFalse(c6.equals(cn));
            Assert.assertFalse(c6.equals(cm));
            Assert.assertFalse(c6.equals(c4));
            Assert.assertFalse(cm.equals(cn));
            Assert.assertFalse(cm.equals(c4));
            Assert.assertFalse(cm.equals(c6));
            Assert.assertTrue(sn.hashCode() == cn.hashCode());
            Assert.assertTrue(cn.hashCode() == sn.hashCode());
            Assert.assertTrue(s4.hashCode() == c4.hashCode());
            Assert.assertTrue(c4.hashCode() == s4.hashCode());
            Assert.assertTrue(s6.hashCode() == c6.hashCode());
            Assert.assertTrue(c6.hashCode() == s6.hashCode());
            Assert.assertTrue(sm.hashCode() == cm.hashCode());
            Assert.assertTrue(cm.hashCode() == sm.hashCode());
            Assert.assertFalse(cn.hashCode() == c4.hashCode());
            Assert.assertFalse(cn.hashCode() == cm.hashCode());
            Assert.assertFalse(cn.hashCode() == c6.hashCode());
            Assert.assertFalse(c4.hashCode() == cn.hashCode());
            Assert.assertFalse(c4.hashCode() == cm.hashCode());
            Assert.assertFalse(c4.hashCode() == c6.hashCode());
            Assert.assertFalse(c6.hashCode() == cn.hashCode());
            Assert.assertFalse(c6.hashCode() == cm.hashCode());
            Assert.assertFalse(c6.hashCode() == c4.hashCode());
            Assert.assertFalse(cm.hashCode() == cn.hashCode());
            Assert.assertFalse(cm.hashCode() == c4.hashCode());
            Assert.assertFalse(cm.hashCode() == c6.hashCode());

        } catch (UnknownHostException e) {
            Assert.assertTrue(false);
        }

        System.out.println("*** testEqual: Start");

    }
}
