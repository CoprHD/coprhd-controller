/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netapp;

import static org.junit.Assert.*;

import java.util.Map;

import netapp.manage.NaServer;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;

/**
 * @author sdorcas
 *
 */
public class VolumeTest {

    static private NaServer server = null;
    static private final String VOL_NAME = "scott1";
    static private final String AGGR_NAME = "aggr0";
    static private final String NEW_VOL_SIZE = "600m";
    private static String host = EnvConfig.get("sanity", "netapp.host");
    private static String portNumber = EnvConfig.get("sanity", "netapp.port");
    private static String userName = EnvConfig.get("sanity", "netapp.username");
    private static String password = EnvConfig.get("sanity", "netapp.password");

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        Server s = new Server(host, Integer.parseInt(portNumber), userName, password, false);
        server = s.getNaServer();
    }

    @Test
    public void testCreateFlexibleVolume() {
        Volume vol = new Volume(server, VOL_NAME);
        boolean result = vol.createFlexibleVolume(AGGR_NAME, false, null, "500m", null, "none");
        assertTrue(result);

        // Check the volume is ready by listing its info
        Map<String, String> info = vol.getVolumeInfo(false);
        assertNotNull(info);
        assertEquals(info.get("name"), VOL_NAME);
    }

    @Test
    public void testEnableSis() {
        Volume vol = new Volume(server, "test1");
        boolean result = vol.enableSis("sun@23");
        assertTrue(result);
    }

    @Test
    public void testSetVolumeOption() {
        Volume vol = new Volume(server, VOL_NAME);
        boolean result = vol.setVolumeOption(VolumeOptionType.minra, "on");
        assertTrue(result);
    }

    @Test
    public void testGetVolumeInfo() {
        Volume vol = new Volume(server, VOL_NAME);
        Map<String, String> result = vol.getVolumeInfo(false);
        assertNotNull(result);
        assertEquals(result.get("name"), VOL_NAME);

    }

    @Test
    public void testSetVolumeSize() {
        Volume vol = new Volume(server, VOL_NAME);
        String size = vol.setVolumeSize(NEW_VOL_SIZE);
        assertEquals(NEW_VOL_SIZE, size);
    }

    @Test
    public void testGetVolumeSize() {
        Volume vol = new Volume(server, VOL_NAME);
        String size = vol.getVolumeSize();
        assertEquals(NEW_VOL_SIZE, size);
    }

    @Test
    public void testSetVolumeOffline() {
        Volume vol = new Volume(server, VOL_NAME);
        vol.setVolumeOffline(0);
        assertTrue(true);
    }

    @Test
    public void testDestroyVolume() {
        Volume vol = new Volume(server, VOL_NAME);
        boolean result = vol.destroyVolume(false);
        assertTrue(result);
    }
}
