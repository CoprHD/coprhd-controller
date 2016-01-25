/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StringSet;
import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy;

public class ExportMaskUtilsTest {

    @Test
    public void testExportMaskUtils() {
        // TODO add code to make this work
        ExportGroup egp = new ExportGroup();
        ExportMask e1 = new ExportMask();
        e1.setLabel("e1");
        ExportMask e2 = new ExportMask();
        e2.setLabel("e2");
        ExportMask e3 = new ExportMask();
        e3.setLabel("e3");
        ExportMask e4 = new ExportMask();
        e4.setLabel("e4");
        ExportMask e5 = new ExportMask();
        e5.setLabel("e5");

        StringMap e1vols = new StringMap();
        e1vols.put("k1", "v1");
        e1vols.put("k2", "v2");
        e1vols.put("k3", "v3");
        e1.setExistingVolumes(e1vols);

        StringMap e2vols = new StringMap();
        e2vols.put("k1", "v1");
        e2.setExistingVolumes(e2vols);

        StringMap e3vols = new StringMap();
        e3vols.put("k1", "v1");
        e3vols.put("k2", "v2");
        e3.setExistingVolumes(e3vols);

        StringMap e4vols = new StringMap();
        e4.setExistingVolumes(e4vols);

        StringMap e5vols = new StringMap();
        e5vols.put("k1", "v1");
        e5vols.put("k2", "v2");
        e5vols.put("k3", "v3");
        e5vols.put("k4", "v1");
        e5vols.put("k5", "v2");
        e5vols.put("k6", "v3");
        e5.setExistingVolumes(e5vols);

        List<ExportMask> sortedMasks = new ArrayList<ExportMask>();
        sortedMasks.add(e1);
        sortedMasks.add(e2);
        sortedMasks.add(e3);
        sortedMasks.add(e4);
        sortedMasks.add(e5);

        ExportMaskPolicy policy1 = new ExportMaskPolicy();

        Map<ExportMask, ExportMaskPolicy> maskPolicyMap = new HashMap<ExportMask, ExportMaskPolicy>();
        maskPolicyMap.put(e1, policy1);
        maskPolicyMap.put(e2, policy1);
        maskPolicyMap.put(e3, policy1);
        maskPolicyMap.put(e4, policy1);
        maskPolicyMap.put(e5, policy1);

        sortedMasks = ExportMaskUtils.sortMasksByEligibility(maskPolicyMap, egp);

        Assert.assertEquals(sortedMasks.get(0).getLabel(), "e4");
        Assert.assertEquals(sortedMasks.get(1).getLabel(), "e2");
        Assert.assertEquals(sortedMasks.get(2).getLabel(), "e3");
        Assert.assertEquals(sortedMasks.get(3).getLabel(), "e1");
        Assert.assertEquals(sortedMasks.get(4).getLabel(), "e5");
    }

    @Test
    public void testExportMaskUtilsCSG() {
        // TODO add code to make this work
        ExportGroup egp = new ExportGroup();
        ExportMask e1 = new ExportMask();
        e1.setLabel("e1");
        ExportMask e2 = new ExportMask();
        e2.setLabel("e2");
        ExportMask e3 = new ExportMask();
        e3.setLabel("e3");
        ExportMask e4 = new ExportMask();
        e4.setLabel("e4");
        ExportMask e5 = new ExportMask();
        e5.setLabel("e5");

        StringMap e1vols = new StringMap();
        e1vols.put("k1", "v1");
        e1vols.put("k2", "v2");
        e1vols.put("k3", "v3");
        e1.setExistingVolumes(e1vols);

        StringMap e2vols = new StringMap();
        e2vols.put("k1", "v1");
        e2.setExistingVolumes(e2vols);

        StringMap e3vols = new StringMap();
        e3vols.put("k1", "v1");
        e3vols.put("k2", "v2");
        e3.setExistingVolumes(e3vols);

        StringMap e4vols = new StringMap();
        e4.setExistingVolumes(e4vols);

        StringMap e5vols = new StringMap();
        e5vols.put("k1", "v1");
        e5vols.put("k2", "v2");
        e5vols.put("k3", "v3");
        e5vols.put("k4", "v1");
        e5vols.put("k5", "v2");
        e5vols.put("k6", "v3");
        e5.setExistingVolumes(e5vols);

        List<ExportMask> sortedMasks = new ArrayList<ExportMask>();
        sortedMasks.add(e1);
        sortedMasks.add(e2);
        sortedMasks.add(e3);
        sortedMasks.add(e4);
        sortedMasks.add(e5);

        ExportMaskPolicy policy1simple = new ExportMaskPolicy();
        policy1simple.simpleMask = true;
        ExportMaskPolicy policy2notsimple = new ExportMaskPolicy();
        policy2notsimple.simpleMask = false;

        Map<ExportMask, ExportMaskPolicy> maskPolicyMap = new HashMap<ExportMask, ExportMaskPolicy>();
        maskPolicyMap.put(e1, policy1simple);
        maskPolicyMap.put(e2, policy1simple);
        maskPolicyMap.put(e3, policy2notsimple);
        maskPolicyMap.put(e4, policy1simple);
        maskPolicyMap.put(e5, policy2notsimple);

        sortedMasks = ExportMaskUtils.sortMasksByEligibility(maskPolicyMap, egp);

        Assert.assertEquals(sortedMasks.get(0).getLabel(), "e3");
        Assert.assertEquals(sortedMasks.get(1).getLabel(), "e5");
        Assert.assertEquals(sortedMasks.get(2).getLabel(), "e4");
        Assert.assertEquals(sortedMasks.get(3).getLabel(), "e2");
        Assert.assertEquals(sortedMasks.get(4).getLabel(), "e1");
    }

    @Test
    public void testExportMaskUtilsFAST() {
        // TODO add code to make this work
        ExportGroup egp = new ExportGroup();
        ExportMask e1 = new ExportMask();
        e1.setLabel("e1");
        e1.setMaskName("e1");
        ExportMask e2 = new ExportMask();
        e2.setLabel("e2-FAST1");
        e2.setMaskName("e2-FAST1");
        ExportMask e3 = new ExportMask();
        e3.setLabel("e3");
        e3.setMaskName("e3");
        ExportMask e4 = new ExportMask();
        e4.setLabel("e4-FAST1");
        e4.setMaskName("e4-FAST1");
        ExportMask e5 = new ExportMask();
        e5.setLabel("e5");
        e5.setMaskName("e5");

        StringMap e1vols = new StringMap();
        e1vols.put("k1", "v1");
        e1vols.put("k2", "v2");
        e1vols.put("k3", "v3");
        e1.setExistingVolumes(e1vols);

        StringMap e2vols = new StringMap();
        e2vols.put("k1", "v1");
        e2.setExistingVolumes(e2vols);

        StringMap e3vols = new StringMap();
        e3vols.put("k1", "v1");
        e3vols.put("k2", "v2");
        e3.setExistingVolumes(e3vols);

        StringMap e4vols = new StringMap();
        e4.setExistingVolumes(e4vols);

        StringMap e5vols = new StringMap();
        e5vols.put("k1", "v1");
        e5vols.put("k2", "v2");
        e5vols.put("k3", "v3");
        e5vols.put("k4", "v1");
        e5vols.put("k5", "v2");
        e5vols.put("k6", "v3");
        e5.setExistingVolumes(e5vols);

        List<ExportMask> sortedMasks = new ArrayList<ExportMask>();
        sortedMasks.add(e1);
        sortedMasks.add(e2);
        sortedMasks.add(e3);
        sortedMasks.add(e4);
        sortedMasks.add(e5);

        ExportMaskPolicy policy1simple = new ExportMaskPolicy();
        policy1simple.simpleMask = true;
        policy1simple.setExportType(ExportMaskPolicy.EXPORT_TYPE.PHANTOM.name());
        ExportMaskPolicy policy2notsimple = new ExportMaskPolicy();
        policy2notsimple.simpleMask = false;

        StringSet fastTiers = new StringSet();
        fastTiers.add("FAST1");
        ExportMaskPolicy policy3simpleFAST = new ExportMaskPolicy();
        policy3simpleFAST.simpleMask = true;
        policy3simpleFAST.setTierPolicies(fastTiers);

        Map<ExportMask, ExportMaskPolicy> maskPolicyMap = new HashMap<ExportMask, ExportMaskPolicy>();
        maskPolicyMap.put(e1, policy1simple);
        maskPolicyMap.put(e2, policy3simpleFAST);
        maskPolicyMap.put(e3, policy1simple);
        maskPolicyMap.put(e4, policy3simpleFAST);
        maskPolicyMap.put(e5, policy1simple);

        sortedMasks = ExportMaskUtils.sortMasksByEligibility(maskPolicyMap, egp);

        System.out.println(Joiner.on('\n').join(sortedMasks));
        Assert.assertEquals(sortedMasks.get(0).getLabel(), "e4-FAST1");
        Assert.assertEquals(sortedMasks.get(1).getLabel(), "e2-FAST1");
        Assert.assertEquals(sortedMasks.get(2).getLabel(), "e3");
        Assert.assertEquals(sortedMasks.get(3).getLabel(), "e1");
        Assert.assertEquals(sortedMasks.get(4).getLabel(), "e5");
    }

    @Test
    public void testInitiatorOrdering() {
        String HOST1 = "host1";
        String HOST2 = "host2";
        String HOST3 = "host3";

        Initiator i1 = new Initiator("FC", "200000000001", HOST1, "cluster1", true);
        i1.setId(URIUtil.createId(Initiator.class));

        Initiator i2 = new Initiator("FC", "200000000002", HOST1, "cluster1", true);
        i2.setId(URIUtil.createId(Initiator.class));

        Initiator i3 = new Initiator("FC", "200000000003", HOST2, "cluster1", true);
        i3.setId(URIUtil.createId(Initiator.class));

        Initiator i4 = new Initiator("FC", "200000000004", HOST2, "cluster1", true);
        i4.setId(URIUtil.createId(Initiator.class));

        Initiator i5 = new Initiator("FC", "200000000005", HOST3, "cluster1", true);
        i5.setId(URIUtil.createId(Initiator.class));

        Multimap<String, Initiator> testMap = HashMultimap.create();

        testMap.put(HOST1, i1);
        testMap.put(HOST1, i2);
        Assert.assertEquals("HOST1 doesn't have expected number of initiators", 2, testMap.get(HOST1).size());
        System.out.println(Joiner.on(',').join(testMap.get(HOST1)));

        testMap.put(HOST2, i3);
        testMap.put(HOST2, i4);
        testMap.put(HOST2, i4);
        testMap.put(HOST2, i4);
        Assert.assertEquals("HOST2 doesn't have expected number of initiators", 2, testMap.get(HOST2).size());
        System.out.println(Joiner.on(',').join(testMap.get(HOST2)));

        testMap.put(HOST3, i5);
        Assert.assertEquals("HOST3 doesn't have expected number of initiators", 1, testMap.get(HOST3).size());
        System.out.println(Joiner.on(',').join(testMap.get(HOST3)));

        Set<Initiator> initiatorSet = new TreeSet<>();
        initiatorSet.add(i1);
        initiatorSet.add(i2);
        initiatorSet.add(i3);
        initiatorSet.add(i4);
        initiatorSet.add(i4);
        initiatorSet.add(i4);
        initiatorSet.add(i4);
        initiatorSet.add(i5);
        initiatorSet.add(i5);
        Assert.assertEquals("Size of initiatorHashSet is unexpected", 5, initiatorSet.size());

        // Test Initiator.hashCode
        System.out.println("################# Testing Initiator.hashCode #################");
        // Make same as i5
        Initiator i6 = new Initiator("FC", "200000000005", HOST3, "cluster1", true);
        i6.setId(i5.getId());
        Assert.assertEquals("i5 and i6 should be equal", i5, i6);
        Assert.assertEquals("Hash codes are different", i5.hashCode(), i6.hashCode());
        System.out.println(String.format("i5.hashCode = %d i6.hashCode = %d", i5.hashCode(), i6.hashCode()));

        // Strange case 1: Same port WWN, but different ID
        Initiator i7 = new Initiator("FC", "200000000005", HOST3, "cluster1", true);
        i7.setId(URIUtil.createId(Initiator.class));
        Assert.assertNotEquals("i5 and i7 should not be equal", i5, i7);
        Assert.assertNotEquals("Hash codes are the same", i5.hashCode(), i7.hashCode());
        System.out.println(String.format("i5.hashCode = %d i7.hashCode = %d", i5.hashCode(), i7.hashCode()));

        // Strange case 2: Different port WWN, but same ID
        Initiator i8 = new Initiator("FC", "200000000008", HOST3, "cluster1", true);
        i8.setId(i5.getId());
        Assert.assertNotEquals("i5 and i8 should not be equal", i5, i8);
        Assert.assertNotEquals("Hash codes are the same", i5.hashCode(), i8.hashCode());
        System.out.println(String.format("i5.hashCode = %d i8.hashCode = %d", i5.hashCode(), i8.hashCode()));

        Map<Initiator, String> map = new HashMap<>();
        map.put(i1, "Initiator 1");
        map.put(i2, "Initiator 2");
        map.put(i3, "Initiator 3");
        map.put(i4, "Initiator 4");
        map.put(i5, "Initiator 5");
        map.put(i6, "Initiator 6");
        map.put(i7, "Initiator 7");
        map.put(i8, "Initiator 8");
        // map.size() should be 7 because i5 and i6 are equivalent based on hashCode
        Assert.assertEquals("Unexpected map size", 7, map.size());
        System.out.println(String.format("map.keys = %s", Joiner.on(',').join(map.keySet())));
        System.out.println(String.format("map.entries = %s", Joiner.on(',').join(map.entrySet())));
    }
}
