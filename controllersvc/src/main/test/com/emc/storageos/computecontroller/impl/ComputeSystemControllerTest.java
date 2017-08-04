package com.emc.storageos.computecontroller.impl;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testng.collections.Lists;

import com.beust.jcommander.internal.Maps;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemControllerImpl;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemControllerImpl.InitiatorChange;
import com.emc.storageos.computesystemcontroller.impl.InitiatorCompleter.InitiatorOperation;
import com.emc.storageos.db.client.model.Initiator;

public class ComputeSystemControllerTest {

    @BeforeClass
    public static void setup() {

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
