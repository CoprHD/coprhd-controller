/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computecontroller.impl;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.beust.jcommander.internal.Maps;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemControllerImpl;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemControllerImpl.InitiatorChange;
import com.emc.storageos.computesystemcontroller.impl.InitiatorCompleter.InitiatorOperation;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Initiator;
import com.google.common.collect.Lists;

public class ComputeSystemControllerTest {

    @BeforeClass
    public static void setup() {
    }

    public Collection<URI> list(String... elements) {
        if (elements.length == 0) {
            return Lists.newArrayList();
        }
        return URIUtil.toURIList(Lists.newArrayList(elements));
    }

    public Map<URI, List<InitiatorChange>> map(Collection<URI> remove, Collection<URI> add) {
        Map<URI, List<InitiatorChange>> result = Maps.newHashMap();
        URI eg = URI.create("E1");
        result.put(eg, Lists.newArrayList());

        if (!remove.isEmpty()) {
            for (URI element : remove) {
                Initiator initiator = new Initiator();
                initiator.setId(element);
                result.get(eg).add(new InitiatorChange(initiator, InitiatorOperation.REMOVE));
            }
        }
        if (!add.isEmpty()) {
            for (URI element : add) {
                Initiator initiator = new Initiator();
                initiator.setId(element);
                result.get(eg).add(new InitiatorChange(initiator, InitiatorOperation.ADD));
            }
        }
        return result;
    }

    @Test
    public void willRemoveAllHostInitiators() {

        URI exportId = URI.create("E1");
        Assert.assertFalse(ComputeSystemControllerImpl.isRemovingAllHostInitiatorsFromExportGroup(map(list("I1", "I2"), list("I3")),
                exportId, list("I1", "I2")));

        Assert.assertFalse(
                ComputeSystemControllerImpl.isRemovingAllHostInitiatorsFromExportGroup(map(list("I1"), list("I2")), exportId,
                        list("I1", "I3")));
        Assert.assertFalse(
                ComputeSystemControllerImpl.isRemovingAllHostInitiatorsFromExportGroup(map(list("I1"), list("I2", "I4")), exportId,
                        list("I1", "I3")));
        Assert.assertFalse(
                ComputeSystemControllerImpl.isRemovingAllHostInitiatorsFromExportGroup(map(list("I1", "I2"), list()), exportId,
                        list("I1", "I2", "I3")));
        Assert.assertFalse(ComputeSystemControllerImpl.isRemovingAllHostInitiatorsFromExportGroup(map(list(), list("I3")), exportId,
                list("I1", "I2")));
        Assert.assertFalse(
                ComputeSystemControllerImpl.isRemovingAllHostInitiatorsFromExportGroup(map(list("I1", "I2"), list("I3", "I4")), exportId,
                        list("I1", "I2")));

        Assert.assertTrue(
                ComputeSystemControllerImpl.isRemovingAllHostInitiatorsFromExportGroup(map(list("I1", "I2"), list()), exportId,
                        list("I1", "I2")));
        Assert.assertTrue(
                ComputeSystemControllerImpl.isRemovingAllHostInitiatorsFromExportGroup(map(list("I1", "I2", "I3"), list()), exportId,
                        list("I1", "I2")));
        Assert.assertTrue(
                ComputeSystemControllerImpl.isRemovingAllHostInitiatorsFromExportGroup(map(list("I1", "I2"), list("I3")), exportId,
                        list("I1")));
        Assert.assertTrue(
                ComputeSystemControllerImpl.isRemovingAllHostInitiatorsFromExportGroup(map(list("I1", "I2", "I3"), list("I4")), exportId,
                        list("I1")));
        Assert.assertTrue(
                ComputeSystemControllerImpl.isRemovingAllHostInitiatorsFromExportGroup(map(list("I1"), list("I2")), exportId, list("I1")));
        Assert.assertTrue(
                ComputeSystemControllerImpl.isRemovingAllHostInitiatorsFromExportGroup(map(list("I1", "I2", "I3"), list("I2")), exportId,
                        list("I1")));

    }

    @Test
    public void testGetNext() {
        Map<URI, List<InitiatorChange>> map = Maps.newHashMap();
        URI eg1 = URI.create("1");
        URI eg2 = URI.create("2");
        URI eg3 = URI.create("3");
        map.put(eg1, Lists.newArrayList());
        map.put(eg2, Lists.newArrayList());
        map.put(eg3, Lists.newArrayList());

        InitiatorChange init1 = new InitiatorChange(createInitiator(URI.create("I1")), InitiatorOperation.ADD);
        map.get(eg1).add(init1);

        Assert.assertEquals(ComputeSystemControllerImpl.getNextInitiatorOperation(eg1, map, InitiatorOperation.ADD), init1.initiator);
        Assert.assertNull(ComputeSystemControllerImpl.getNextInitiatorOperation(eg1, map, InitiatorOperation.ADD));
        Assert.assertNull(ComputeSystemControllerImpl.getNextInitiatorOperation(eg1, map, InitiatorOperation.REMOVE));
        Assert.assertTrue(map.get(eg1).isEmpty());

        InitiatorChange init2 = new InitiatorChange(createInitiator(URI.create("I2")), InitiatorOperation.REMOVE);
        map.get(eg2).add(init2);

        Assert.assertEquals(ComputeSystemControllerImpl.getNextInitiatorOperation(eg2, map, InitiatorOperation.REMOVE), init2.initiator);
        Assert.assertNull(ComputeSystemControllerImpl.getNextInitiatorOperation(eg2, map, InitiatorOperation.ADD));
        Assert.assertNull(ComputeSystemControllerImpl.getNextInitiatorOperation(eg2, map, InitiatorOperation.REMOVE));
        Assert.assertTrue(map.get(eg2).isEmpty());

        InitiatorChange init3 = new InitiatorChange(createInitiator(URI.create("I3")), InitiatorOperation.REMOVE);
        InitiatorChange init4 = new InitiatorChange(createInitiator(URI.create("I4")), InitiatorOperation.REMOVE);
        InitiatorChange init5 = new InitiatorChange(createInitiator(URI.create("I5")), InitiatorOperation.ADD);
        InitiatorChange init6 = new InitiatorChange(createInitiator(URI.create("I6")), InitiatorOperation.ADD);

        map.get(eg3).add(init3);
        map.get(eg3).add(init4);
        map.get(eg3).add(init5);
        map.get(eg3).add(init6);

        Assert.assertEquals(ComputeSystemControllerImpl.getNextInitiatorOperation(eg3, map, InitiatorOperation.REMOVE), init3.initiator);
        Assert.assertEquals(ComputeSystemControllerImpl.getNextInitiatorOperation(eg3, map, InitiatorOperation.ADD), init5.initiator);
        Assert.assertEquals(ComputeSystemControllerImpl.getNextInitiatorOperation(eg3, map, InitiatorOperation.REMOVE), init4.initiator);
        Assert.assertEquals(ComputeSystemControllerImpl.getNextInitiatorOperation(eg3, map, InitiatorOperation.ADD), init6.initiator);
        Assert.assertNull(ComputeSystemControllerImpl.getNextInitiatorOperation(eg3, map, InitiatorOperation.ADD));
        Assert.assertNull(ComputeSystemControllerImpl.getNextInitiatorOperation(eg3, map, InitiatorOperation.REMOVE));
        Assert.assertTrue(map.get(eg3).isEmpty());

    }

    public Initiator createInitiator(URI uri) {
        Initiator init = new Initiator();
        init.setId(uri);
        return init;
    }
}
