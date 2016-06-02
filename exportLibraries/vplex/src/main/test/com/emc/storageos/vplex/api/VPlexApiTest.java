/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vplex.api.VPlexVirtualVolumeInfo.WaitOnRebuildResult;
import com.emc.storageos.vplex.api.clientdata.PortInfo;
import com.emc.storageos.vplex.api.clientdata.VolumeInfo;

/**
 * Test client for VPlex API.
 */
public class VPlexApiTest {

    // Properties file name
    private static final String PROP_FILE_NAME = "/com/emc/storageos/vplex/api/VPlexApiTest.properties";
    // Properties file for vplex credentials.
    private static final String UNIT_TEST_CONFIG_FILE = "sanity";

    // Property keys
    private static final String VPLEX_VERSION_KEY = "VPLEX_VERSION";

    private static final String VPLEX_PROVIDER_IP = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "vplex.host.ipaddress");
    private static final String VPLEX_PROVIDER_PORT = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "vplex.host.port");
    private static final String VPLEX_PROVIDER_USER = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "vplex.host.username");
    private static final String VPLEX_PROVIDER_PWD = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "vplex.host.password");

    private static final String CONNECTED_SYSTEMS_PROP_KEY = "CONNECTED_SYSTEMS";
    private static final String SIMPLE_VV_INFO_PROP_KEY = "SIMPLE_VIRTUAL_VOLUME_TEST";
    private static final String DISTRIBUTED_VV_INFO_PROP_KEY = "DISTRIBUTED_VIRTUAL_VOLUME_TEST";
    private static final String STORAGE_VIEW_TARGETS_PROP_KEY = "STORAGE_VIEW_TARGETS";
    private static final String STORAGE_VIEW_INITIATORS_PROP_KEY = "STORAGE_VIEW_INITIATORS";
    private static final String STORAGE_VIEW_NAME_PROP_KEY = "STORAGE_VIEW_NAME";
    private static final String MIGRATION_VV_INFO_PROP_KEY = "MIGRATION_VIRTUAL_VOLUME_TEST";
    private static final String SIMPLE_MIGRATION_VV_INFO_PROP_KEY = "SIMPLE_MIGRATION_VIRTUAL_VOLUME_TEST";
    private static final String MIGRATION_NAME_PROP_KEY = "MIGRATION_NAME";
    private static final String DEVICE_MIRROR_TEST_VOLUME_PROP_KEY = "DEVICE_MIRROR_TEST_VOLUME";

    // The configuration properties for the test.
    private static Properties _properties = new Properties();

    // Factory used to create and manage client connections;
    private static volatile VPlexApiFactory _apiFactory = null;

    // The VPlex API client used to make http requests to the VPlex.
    private static volatile VPlexApiClient _client = null;

    // The native guids for the arrays attached to the VPlex
    private static final List<String> _attachedStorageSystems = new ArrayList<String>();

    /**
     * Setup is called once before tests are executed. Loads the test properties,
     * creates the VPlex API factory, and creates the VPlex API client connection to
     * the VPlex management server specified in the test properties file.
     * 
     * @throws Exception When test setup fails.
     */
    @BeforeClass
    public static void setup() throws Exception {
        try {
            // Load test properties.
            _properties.load(VPlexApiTest.class.getResourceAsStream(PROP_FILE_NAME));

            // Create API factory.
            _apiFactory = VPlexApiFactory.getInstance();

            // Get the Http API client.
            URI vplexEndpointURI = new URI("https", null, VPLEX_PROVIDER_IP, Integer.parseInt(VPLEX_PROVIDER_PORT),
                    "/", null, null);

            _client = _apiFactory.getClient(vplexEndpointURI, _properties.getProperty(VPLEX_PROVIDER_USER),
                    _properties.getProperty(VPLEX_PROVIDER_PWD));

            // Setup the list of storage systems attached to the VPlex.
            String connectedStorageSystemInfo = _properties
                    .getProperty(CONNECTED_SYSTEMS_PROP_KEY);
            StringTokenizer tokenizer = new StringTokenizer(connectedStorageSystemInfo,
                    ",");
            while (tokenizer.hasMoreTokens()) {
                String storageSystemGuid = tokenizer.nextToken();
                _attachedStorageSystems.add(storageSystemGuid);
            }
        } catch (Exception e) {
            System.out.println("Failed setup: " + e.getMessage());
            Assert.assertTrue(false);
        }
    }

    /**
     * Tests the virtual volume deep discovery
     */
    @Test
    public void testGetVirtualVolumes() {
        boolean wasException = false;
        try {
            double start = System.currentTimeMillis();
            Map<String, VPlexVirtualVolumeInfo> volumesMap = _client.getVirtualVolumes(false);
            double end = System.currentTimeMillis();
            double duration = (end - start) / 1000;
            double timePerVolume = duration / volumesMap.size();
            double projected = (timePerVolume * 5000) / 60;
            System.out.printf("Number of volumes found: %d%n", volumesMap.size());
            System.out.printf("Virtual volume deep discovery time (s): %.2f%n", duration);
            System.out.printf("Average per volume (s): %.5f%n", timePerVolume);
            System.out.printf("Projected Time for 5000 volumes (m): %.2f%n", projected);

        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the virtual volume deep discovery
     */
    @Test
    public void testGetConsistencyGroups() {
        boolean wasException = false;
        try {
            List<VPlexConsistencyGroupInfo> cgInfoList = _client.getConsistencyGroups();
            for (VPlexConsistencyGroupInfo cgInfo : cgInfoList) {
                System.out.println(cgInfo.toString());
            }
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the API getStorageSystemInfo, which gets the information about the
     * backend storage systems connected to the VPlex.
     */
    @Test
    public void testGetManagementSoftwareVersion() {
        boolean wasException = false;
        try {
            String supportedVersion = _properties.getProperty(VPLEX_VERSION_KEY);
            String vplexVersion = _client.getManagementSoftwareVersion();
            Assert.assertEquals(supportedVersion, vplexVersion);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the API getStorageSystemInfo, which gets the information about the
     * backend storage systems connected to the VPlex.
     */
    @Test
    public void testGetStorageSystemInfo() {
        boolean wasException = false;
        try {
            List<VPlexStorageSystemInfo> storageSystemInfoList = _client
                    .getStorageSystemInfo();
            Assert.assertTrue(storageSystemInfoList.size() == _attachedStorageSystems
                    .size());
            for (VPlexStorageSystemInfo storageSystemInfo : storageSystemInfoList) {
                boolean foundAMatch = false;
                for (String nativeGuid : _attachedStorageSystems) {
                    if (storageSystemInfo.matches(nativeGuid)) {
                        foundAMatch = true;
                        break;
                    }
                }
                Assert.assertTrue(foundAMatch);
            }
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the API getPortInfo, which gets the information about the
     * director ports in the VPlex engines.
     */
    @Test
    public void testGetPortInfo() {
        boolean wasException = false;
        try {
            List<VPlexPortInfo> portInfoList = _client.getPortInfo(false);
            Integer totalPorts = 0;
            List<String> directorList = new ArrayList<String>();
            for (VPlexPortInfo portInfo : portInfoList) {
                VPlexDirectorInfo directorInfo = portInfo.getDirectorInfo();
                if (!directorList.contains(directorInfo.getName())) {
                    totalPorts += directorInfo.getPortInfo().size();
                    directorList.add(directorInfo.getName());
                }
            }
            Assert.assertTrue(portInfoList.size() == totalPorts);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the API rediscoverStorageSystems. Test sleeps a bit at the end
     * to ensure the rediscover call does not interfere with subsequent tests.
     * 
     * @throws InterruptedException If an error occurs during the sleep.
     */
    @Test
    public void testRediscoverStorageSystems() throws InterruptedException {
        boolean wasException = false;
        try {
            List<VPlexStorageSystemInfo> storageSystemInfoList = _client
                    .getStorageSystemInfo();
            Assert.assertTrue(!storageSystemInfoList.isEmpty());
            List<String> storageSystemNativeGuids = new ArrayList<String>();
            for (VPlexStorageSystemInfo storageSystemInfo : storageSystemInfoList) {
                storageSystemNativeGuids.add(storageSystemInfo.getUniqueId());
            }
            _client.rediscoverStorageSystems(storageSystemNativeGuids);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Let rediscover finish, which can interfere with subsequent tests.
        Thread.sleep(2000);
    }

    /**
     * Tests the API createVirtualVolume when only a single storage volume
     * is passed, thereby creating a simple virtual volume on one cluster
     * of the VPlex.
     */
    @Test
    public void testCreateVirtualVolumeSimple() {
        boolean wasException = false;
        try {
            // Create the virtual volume.
            String volumeInfo = _properties.getProperty(SIMPLE_VV_INFO_PROP_KEY);
            StringTokenizer tokenizer = new StringTokenizer(volumeInfo, ",");
            String systemGuid = tokenizer.nextToken();
            tokenizer.nextToken();
            String volumeNativeId = tokenizer.nextToken();
            VPlexVirtualVolumeInfo vvInfo = createSimpleVirtualVolume();
            Assert.assertNotNull(vvInfo);
            StringBuilder builder = new StringBuilder();
            builder.append(VPlexApiConstants.DEVICE_PREFIX);
            builder.append(VPlexApiConstants.VOLUME_NAME_PREFIX);
            builder.append(systemGuid.substring(systemGuid.indexOf("+") + 1));
            builder.append("-");
            builder.append(volumeNativeId);
            builder.append(VPlexApiConstants.VIRTUAL_VOLUME_SUFFIX);
            Assert.assertEquals(builder.toString(), vvInfo.getName());

            // Cleanup
            _client.deleteVirtualVolume(vvInfo.getName(), true, false);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the API createVirtualVolume when multiple storage volumes
     * are passed, thereby creating a distributed virtual volume on the
     * VPlex.
     */
    @Test
    public void testCreateVirtualVolumeDistributed() {
        boolean wasException = false;
        try {
            // Create the distributed virtual volume.
            StringBuilder vvNameBuilder = new StringBuilder(VPlexApiConstants.DIST_DEVICE_PREFIX);
            List<VolumeInfo> nativeVolumeInfoList = new ArrayList<VolumeInfo>();
            String distVolumeInfo = _properties.getProperty(DISTRIBUTED_VV_INFO_PROP_KEY);
            StringTokenizer tokenizer = new StringTokenizer(distVolumeInfo, "::");
            while (tokenizer.hasMoreTokens()) {
                String volumeInfo = tokenizer.nextToken();
                StringTokenizer volumeInfoTokenizer = new StringTokenizer(volumeInfo, ",");
                String systemGuid = volumeInfoTokenizer.nextToken();
                String volumeId = volumeInfoTokenizer.nextToken();
                String volumeNativeId = volumeInfoTokenizer.nextToken();
                nativeVolumeInfoList.add(new VolumeInfo(systemGuid, "vmax", volumeId, volumeNativeId, false, Collections
                        .<String> emptyList()));
                vvNameBuilder.append(VPlexApiConstants.DIST_DEVICE_NAME_DELIM);
                vvNameBuilder.append(VPlexApiConstants.VOLUME_NAME_PREFIX);
                vvNameBuilder.append(systemGuid.substring(systemGuid.indexOf("+") + 1));
                vvNameBuilder.append("-");
                vvNameBuilder.append(volumeNativeId);
            }
            vvNameBuilder.append(VPlexApiConstants.VIRTUAL_VOLUME_SUFFIX);
            List<VPlexClusterInfo> clusterInfoList = _client.getClusterInfo(false);
            VPlexVirtualVolumeInfo vvInfo = _client.createVirtualVolume(
                    nativeVolumeInfoList, true, false, false, "1", clusterInfoList, true);
            Assert.assertNotNull(vvInfo);
            Assert.assertEquals(vvNameBuilder.toString(), vvInfo.getName());

            // Cleanup
            _client.deleteVirtualVolume(vvInfo.getName(), true, false);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the API createStorageView when the view name is not specified.
     */
    @Test
    public void testCreateStorageViewNoViewName() {

        // Get the target ports
        List<PortInfo> targetPortInfoList = new ArrayList<PortInfo>();
        String storageViewTargetsStr = _properties
                .getProperty(STORAGE_VIEW_TARGETS_PROP_KEY);
        StringTokenizer tokenizer = new StringTokenizer(storageViewTargetsStr, ",");
        while (tokenizer.hasMoreTokens()) {
            String portWWN = tokenizer.nextToken();
            PortInfo targetPortInfo = new PortInfo(portWWN);
            targetPortInfoList.add(targetPortInfo);
        }

        // Test with a null view name.
        boolean wasException = false;
        try {
            Assert.assertTrue(!targetPortInfoList.isEmpty());
            _client.createStorageView(null, targetPortInfoList, null, null);
        } catch (VPlexApiException vae) {
            wasException = true;
        }
        Assert.assertTrue(wasException);

        // Test with a blank view name.
        wasException = false;
        try {
            Assert.assertTrue(!targetPortInfoList.isEmpty());
            _client.createStorageView("", targetPortInfoList, null, null);
        } catch (VPlexApiException vae) {
            wasException = true;
        }
        Assert.assertTrue(wasException);

        // Test with a view name consisting only of white space.
        wasException = false;
        try {
            Assert.assertTrue(!targetPortInfoList.isEmpty());
            _client.createStorageView(" ", targetPortInfoList, null, null);
        } catch (VPlexApiException vae) {
            wasException = true;
        }
        Assert.assertTrue(wasException);
    }

    /**
     * Tests the API createStorageView when no targets are specified.
     */
    @Test
    public void testCreateStorageViewNoTargets() {

        // Get the storage view name
        String storageViewName = _properties.getProperty(STORAGE_VIEW_NAME_PROP_KEY);

        // Test with a null view name.
        boolean wasException = false;
        try {
            Assert.assertNotNull(storageViewName);
            Assert.assertTrue(storageViewName.trim().length() != 0);
            _client.createStorageView(storageViewName, new ArrayList<PortInfo>(), null,
                    null);
        } catch (VPlexApiException vae) {
            wasException = true;
        }
        Assert.assertTrue(wasException);
    }

    /**
     * Tests the API createStorageView when only a target ports are passed
     * in the request.
     */
    @Test
    public void testCreateStorageViewTargetsOnly() {
        boolean wasException = false;
        try {
            // Get the storage view name
            String storageViewName = _properties.getProperty(STORAGE_VIEW_NAME_PROP_KEY);

            // Get the target ports
            List<PortInfo> targetPortInfoList = new ArrayList<PortInfo>();
            String storageViewTargetsStr = _properties
                    .getProperty(STORAGE_VIEW_TARGETS_PROP_KEY);
            StringTokenizer tokenizer = new StringTokenizer(storageViewTargetsStr, ",");
            while (tokenizer.hasMoreTokens()) {
                String portWWN = tokenizer.nextToken();
                PortInfo targetPortInfo = new PortInfo(portWWN);
                targetPortInfoList.add(targetPortInfo);
            }

            // Create the storage view
            VPlexStorageViewInfo storageViewInfo = _client.createStorageView(
                    storageViewName, targetPortInfoList, null, null);
            Assert.assertNotNull(storageViewInfo);
            Assert.assertEquals(storageViewInfo.getName(), storageViewName);

            // Cleanup
            Boolean[] viewFound = new Boolean[] { new Boolean(false) };
            _client.deleteStorageView(storageViewName, viewFound);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the API createStorageView when both target ports and virtual
     * volumes are passed in the request.
     */
    @Test
    public void testCreateStorageViewWithVolumes() {
        boolean wasException = false;
        try {
            // Get the storage view name
            String storageViewName = _properties.getProperty(STORAGE_VIEW_NAME_PROP_KEY);

            // Get the target ports
            List<PortInfo> targetPortInfoList = new ArrayList<PortInfo>();
            String storageViewTargetsStr = _properties
                    .getProperty(STORAGE_VIEW_TARGETS_PROP_KEY);
            StringTokenizer tokenizer = new StringTokenizer(storageViewTargetsStr, ",");
            while (tokenizer.hasMoreTokens()) {
                String portWWN = tokenizer.nextToken();
                PortInfo targetPortInfo = new PortInfo(portWWN);
                targetPortInfoList.add(targetPortInfo);
            }

            // Create the virtual volume.
            VPlexVirtualVolumeInfo vvInfo = createSimpleVirtualVolume();
            Assert.assertNotNull(vvInfo);
            Map<String, Integer> vvMap = new HashMap<String, Integer>();
            vvMap.put(vvInfo.getName(), Integer.valueOf(VPlexApiConstants.LUN_UNASSIGNED));

            // Create the storage view
            VPlexStorageViewInfo storageViewInfo = _client.createStorageView(
                    storageViewName, targetPortInfoList, null, vvMap);
            Assert.assertNotNull(storageViewInfo);
            Assert.assertEquals(storageViewInfo.getName(), storageViewName);

            // Cleanup
            Boolean[] viewFound = new Boolean[] { new Boolean(false) };
            _client.deleteStorageView(storageViewName, viewFound);
            _client.deleteVirtualVolume(vvInfo.getName(), true, false);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the API createStorageView when target ports, initiator ports, and
     * virtual volumes are passed in the request.
     */
    @Test
    public void testCreateStorageViewWithInitiatorsAndVolumes() {
        boolean wasException = false;
        try {
            // Get the storage view name
            String storageViewName = _properties.getProperty(STORAGE_VIEW_NAME_PROP_KEY);

            // Get the target ports
            List<PortInfo> targetPortInfoList = new ArrayList<PortInfo>();
            String storageViewTargetsStr = _properties
                    .getProperty(STORAGE_VIEW_TARGETS_PROP_KEY);
            StringTokenizer tokenizer = new StringTokenizer(storageViewTargetsStr, ",");
            while (tokenizer.hasMoreTokens()) {
                String portWWN = tokenizer.nextToken();
                PortInfo targetPortInfo = new PortInfo(portWWN);
                targetPortInfoList.add(targetPortInfo);
            }

            // Get the initiator ports
            List<PortInfo> initiatorPortInfoList = new ArrayList<PortInfo>();
            String storageViewInitiatorsStr = _properties
                    .getProperty(STORAGE_VIEW_INITIATORS_PROP_KEY);
            tokenizer = new StringTokenizer(storageViewInitiatorsStr, ",");
            while (tokenizer.hasMoreTokens()) {
                String portWWN = tokenizer.nextToken();
                PortInfo initiatorPortInfo = new PortInfo(portWWN);
                initiatorPortInfoList.add(initiatorPortInfo);
            }

            // Create the virtual volume.
            VPlexVirtualVolumeInfo vvInfo = createSimpleVirtualVolume();
            Assert.assertNotNull(vvInfo);
            Map<String, Integer> vvMap = new HashMap<String, Integer>();
            vvMap.put(vvInfo.getName(), Integer.valueOf(VPlexApiConstants.LUN_UNASSIGNED));

            // Create the storage view
            VPlexStorageViewInfo storageViewInfo = _client.createStorageView(
                    storageViewName, targetPortInfoList, initiatorPortInfoList, vvMap);
            Assert.assertNotNull(storageViewInfo);
            Assert.assertEquals(storageViewInfo.getName(), storageViewName);

            // Cleanup
            Boolean[] viewFound = new Boolean[] { new Boolean(false) };
            _client.deleteStorageView(storageViewName, viewFound);
            _client.deleteVirtualVolume(vvInfo.getName(), true, false);
            _client.getExportManager().unregisterInitiators(initiatorPortInfoList);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the API addVirtualVolumesToStorageView.
     */
    @Test
    public void testAddVirtualVolumesToStorageView() {
        boolean wasException = false;
        try {
            // Get the storage view name
            String storageViewName = _properties.getProperty(STORAGE_VIEW_NAME_PROP_KEY);

            // Get the target ports
            List<PortInfo> targetPortInfoList = new ArrayList<PortInfo>();
            String storageViewTargetsStr = _properties
                    .getProperty(STORAGE_VIEW_TARGETS_PROP_KEY);
            StringTokenizer tokenizer = new StringTokenizer(storageViewTargetsStr, ",");
            while (tokenizer.hasMoreTokens()) {
                String portWWN = tokenizer.nextToken();
                PortInfo targetPortInfo = new PortInfo(portWWN);
                targetPortInfoList.add(targetPortInfo);
            }

            // Create the storage view
            VPlexStorageViewInfo storageViewInfo = _client.createStorageView(
                    storageViewName, targetPortInfoList, null, null);
            Assert.assertNotNull(storageViewInfo);
            Assert.assertEquals(storageViewInfo.getName(), storageViewName);

            // Create a virtual volume.
            VPlexVirtualVolumeInfo vvInfo = createSimpleVirtualVolume();
            Assert.assertNotNull(vvInfo);
            Map<String, Integer> vvMap = new HashMap<String, Integer>();
            vvMap.put(vvInfo.getName(), Integer.valueOf(VPlexApiConstants.LUN_UNASSIGNED));

            // Add the virtual volume to the storage view.
            _client.addVirtualVolumesToStorageView(storageViewName, vvMap);

            // Cleanup
            Boolean[] viewFound = new Boolean[] { new Boolean(false) };
            _client.deleteStorageView(storageViewName, viewFound);
            _client.deleteVirtualVolume(vvInfo.getName(), true, false);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the API removeVirtualVolumesFromStorageView.
     */
    @Test
    public void testRemoveVirtualVolumesFromStorageView() {
        boolean wasException = false;
        try {
            // Get the storage view name
            String storageViewName = _properties.getProperty(STORAGE_VIEW_NAME_PROP_KEY);

            // Get the target ports
            List<PortInfo> targetPortInfoList = new ArrayList<PortInfo>();
            String storageViewTargetsStr = _properties
                    .getProperty(STORAGE_VIEW_TARGETS_PROP_KEY);
            StringTokenizer tokenizer = new StringTokenizer(storageViewTargetsStr, ",");
            while (tokenizer.hasMoreTokens()) {
                String portWWN = tokenizer.nextToken();
                PortInfo targetPortInfo = new PortInfo(portWWN);
                targetPortInfoList.add(targetPortInfo);
            }

            // Create the virtual volume.
            VPlexVirtualVolumeInfo vvInfo = createSimpleVirtualVolume();
            Assert.assertNotNull(vvInfo);
            Map<String, Integer> vvMap = new HashMap<String, Integer>();
            vvMap.put(vvInfo.getName(), Integer.valueOf(VPlexApiConstants.LUN_UNASSIGNED));

            // Create the storage view
            VPlexStorageViewInfo storageViewInfo = _client.createStorageView(
                    storageViewName, targetPortInfoList, null, vvMap);
            Assert.assertNotNull(storageViewInfo);
            Assert.assertEquals(storageViewInfo.getName(), storageViewName);

            // Remove the virtual volume from the storage view.
            List<String> vvNames = new ArrayList<String>();
            vvNames.addAll(vvMap.keySet());
            _client.removeVirtualVolumesFromStorageView(storageViewName, vvNames);

            // Cleanup
            Boolean[] viewFound = new Boolean[] { new Boolean(false) };
            _client.deleteStorageView(storageViewName, viewFound);
            _client.deleteVirtualVolume(vvInfo.getName(), true, false);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the API addInitiatorsToStorageView.
     */
    @Test
    public void testAddInitiatorsToStorageView() {
        boolean wasException = false;
        try {
            // Get the storage view name
            String storageViewName = _properties.getProperty(STORAGE_VIEW_NAME_PROP_KEY);

            // Get the target ports
            List<PortInfo> targetPortInfoList = new ArrayList<PortInfo>();
            String storageViewTargetsStr = _properties
                    .getProperty(STORAGE_VIEW_TARGETS_PROP_KEY);
            StringTokenizer tokenizer = new StringTokenizer(storageViewTargetsStr, ",");
            while (tokenizer.hasMoreTokens()) {
                String portWWN = tokenizer.nextToken();
                PortInfo targetPortInfo = new PortInfo(portWWN);
                targetPortInfoList.add(targetPortInfo);
            }

            // Create the storage view
            VPlexStorageViewInfo storageViewInfo = _client.createStorageView(
                    storageViewName, targetPortInfoList, null, null);
            Assert.assertNotNull(storageViewInfo);
            Assert.assertEquals(storageViewInfo.getName(), storageViewName);

            // Get the initiator ports
            List<PortInfo> initiatorPortInfoList = new ArrayList<PortInfo>();
            String storageViewInitiatorsStr = _properties
                    .getProperty(STORAGE_VIEW_INITIATORS_PROP_KEY);
            tokenizer = new StringTokenizer(storageViewInitiatorsStr, ",");
            while (tokenizer.hasMoreTokens()) {
                String portWWN = tokenizer.nextToken();
                PortInfo initiatorPortInfo = new PortInfo(portWWN);
                initiatorPortInfoList.add(initiatorPortInfo);
            }

            // Add the initiators to the storage view.
            _client.addInitiatorsToStorageView(storageViewName, initiatorPortInfoList);

            // Cleanup
            Boolean[] viewFound = new Boolean[] { new Boolean(false) };
            _client.deleteStorageView(storageViewName, viewFound);
            _client.getExportManager().unregisterInitiators(initiatorPortInfoList);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the API removeInitiatorFromStorageView.
     */
    @Test
    public void testRemoveInitiatorsFromStorageView() {
        boolean wasException = false;
        try {
            // Get the storage view name
            String storageViewName = _properties.getProperty(STORAGE_VIEW_NAME_PROP_KEY);

            // Get the target ports
            List<PortInfo> targetPortInfoList = new ArrayList<PortInfo>();
            String storageViewTargetsStr = _properties
                    .getProperty(STORAGE_VIEW_TARGETS_PROP_KEY);
            StringTokenizer tokenizer = new StringTokenizer(storageViewTargetsStr, ",");
            while (tokenizer.hasMoreTokens()) {
                String portWWN = tokenizer.nextToken();
                PortInfo targetPortInfo = new PortInfo(portWWN);
                targetPortInfoList.add(targetPortInfo);
            }

            // Get the initiator ports
            List<PortInfo> initiatorPortInfoList = new ArrayList<PortInfo>();
            String storageViewInitiatorsStr = _properties
                    .getProperty(STORAGE_VIEW_INITIATORS_PROP_KEY);
            tokenizer = new StringTokenizer(storageViewInitiatorsStr, ",");
            while (tokenizer.hasMoreTokens()) {
                String portWWN = tokenizer.nextToken();
                PortInfo initiatorPortInfo = new PortInfo(portWWN);
                initiatorPortInfoList.add(initiatorPortInfo);
            }

            // Create the storage view
            VPlexStorageViewInfo storageViewInfo = _client.createStorageView(
                    storageViewName, targetPortInfoList, initiatorPortInfoList, null);
            Assert.assertNotNull(storageViewInfo);
            Assert.assertEquals(storageViewInfo.getName(), storageViewName);

            // Remove the initiators from the storage view.
            _client.removeInitiatorsFromStorageView(storageViewName, initiatorPortInfoList);

            // Cleanup
            Boolean[] viewFound = new Boolean[] { new Boolean(false) };
            _client.deleteStorageView(storageViewName, viewFound);
            _client.getExportManager().unregisterInitiators(initiatorPortInfoList);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the API migrateVirtualVolume for a simple virtual volume.
     */
    @Test
    public void testMigrateVirtualVolumeSimple() {

        boolean wasException = false;
        try {
            // Create the simple virtual volume.
            // Create the virtual volume.
            String volumeInfo = _properties.getProperty(SIMPLE_VV_INFO_PROP_KEY);
            StringTokenizer tokenizer = new StringTokenizer(volumeInfo, ",");
            String systemGuid = tokenizer.nextToken();
            tokenizer.nextToken();
            String volumeNativeId = tokenizer.nextToken();
            VPlexVirtualVolumeInfo vvInfo = createSimpleVirtualVolume();
            Assert.assertNotNull(vvInfo);
            StringBuilder vvNameBuilder = new StringBuilder();
            vvNameBuilder.append(VPlexApiConstants.DEVICE_PREFIX);
            vvNameBuilder.append(VPlexApiConstants.VOLUME_NAME_PREFIX);
            vvNameBuilder.append(systemGuid.substring(systemGuid.indexOf("+") + 1));
            vvNameBuilder.append("-");
            vvNameBuilder.append(volumeNativeId);
            vvNameBuilder.append(VPlexApiConstants.VIRTUAL_VOLUME_SUFFIX);
            String vvName = vvNameBuilder.toString();
            Assert.assertEquals(vvName, vvInfo.getName());

            // Migrate the virtual volume
            List<VolumeInfo> nativeVolumeInfoList = new ArrayList<VolumeInfo>();
            String migrationVolumeInfo = _properties
                    .getProperty(SIMPLE_MIGRATION_VV_INFO_PROP_KEY);
            StringTokenizer volumeInfoTokenizer = new StringTokenizer(migrationVolumeInfo, ",");
            systemGuid = volumeInfoTokenizer.nextToken();
            String volumeId = volumeInfoTokenizer.nextToken();
            volumeNativeId = volumeInfoTokenizer.nextToken();
            nativeVolumeInfoList.add(new VolumeInfo(systemGuid, "vmax", volumeId,
                    volumeNativeId, false, Collections.<String> emptyList()));
            vvNameBuilder = new StringBuilder();
            vvNameBuilder.append(VPlexApiConstants.DEVICE_PREFIX);
            vvNameBuilder.append(VPlexApiConstants.VOLUME_NAME_PREFIX);
            vvNameBuilder.append(systemGuid.substring(systemGuid.indexOf("+") + 1));
            vvNameBuilder.append("-");
            vvNameBuilder.append(volumeNativeId);
            vvNameBuilder.append(VPlexApiConstants.VIRTUAL_VOLUME_SUFFIX);

            String migrationName = _properties.getProperty(MIGRATION_NAME_PROP_KEY);
            List<VPlexMigrationInfo> migrationInfoList = _client.migrateVirtualVolume(
                    migrationName, vvName, nativeVolumeInfoList, false, false, false, true, null);

            Assert.assertEquals(migrationInfoList.size(), 1);

            // Wait until migrations complete and commit the migrations with
            // automatic clean and remove.
            Thread.sleep(15000);
            List<String> migrationNames = new ArrayList<String>();
            for (VPlexMigrationInfo migrationInfo : migrationInfoList) {
                migrationNames.add(migrationInfo.getName());
            }
            migrationInfoList = _client.commitMigrations(vvName, migrationNames, true, true, true);
            Assert.assertEquals(migrationInfoList.size(), 1);

            // Clean up the virtual volume.
            vvInfo = migrationInfoList.get(0).getVirtualVolumeInfo();
            Assert.assertEquals(vvNameBuilder.toString(), vvInfo.getName());
            _client.deleteVirtualVolume(vvInfo.getName(), true, false);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the API migrateVirtualVolume for a distributed virtual volume.
     */
    @Test
    public void testMigrateVirtualVolumeDistributed() {

        boolean wasException = false;
        try {
            // Create the distributed virtual volume.
            StringBuilder vvNameBuilder = new StringBuilder(
                    VPlexApiConstants.DIST_DEVICE_PREFIX);
            List<VolumeInfo> nativeVolumeInfoList = new ArrayList<VolumeInfo>();
            String distVolumeInfo = _properties.getProperty(DISTRIBUTED_VV_INFO_PROP_KEY);
            StringTokenizer tokenizer = new StringTokenizer(distVolumeInfo, "::");
            while (tokenizer.hasMoreTokens()) {
                String volumeInfo = tokenizer.nextToken();
                StringTokenizer volumeInfoTokenizer = new StringTokenizer(volumeInfo, ",");
                String systemGuid = volumeInfoTokenizer.nextToken();
                String volumeId = volumeInfoTokenizer.nextToken();
                String volumeNativeId = volumeInfoTokenizer.nextToken();
                nativeVolumeInfoList.add(new VolumeInfo(systemGuid, "vmax", volumeId,
                        volumeNativeId, false, Collections.<String> emptyList()));
                vvNameBuilder.append(VPlexApiConstants.DIST_DEVICE_NAME_DELIM);
                vvNameBuilder.append(VPlexApiConstants.VOLUME_NAME_PREFIX);
                vvNameBuilder.append(systemGuid.substring(systemGuid.indexOf("+") + 1));
                vvNameBuilder.append("-");
                vvNameBuilder.append(volumeNativeId);
            }
            vvNameBuilder.append(VPlexApiConstants.VIRTUAL_VOLUME_SUFFIX);
            String vvName = vvNameBuilder.toString();
            List<VPlexClusterInfo> clusterInfoList = _client.getClusterInfo(false);
            VPlexVirtualVolumeInfo vvInfo = _client.createVirtualVolume(
                    nativeVolumeInfoList, true, false, false, "1", clusterInfoList, true);
            Assert.assertNotNull(vvInfo);
            Assert.assertEquals(vvName, vvInfo.getName());

            // Migrate the virtual volume
            vvNameBuilder = new StringBuilder(
                    VPlexApiConstants.DIST_DEVICE_PREFIX);
            nativeVolumeInfoList.clear();
            String migrationVolumeInfo = _properties
                    .getProperty(MIGRATION_VV_INFO_PROP_KEY);
            tokenizer = new StringTokenizer(migrationVolumeInfo, "::");
            while (tokenizer.hasMoreTokens()) {
                String volumeInfo = tokenizer.nextToken();
                StringTokenizer volumeInfoTokenizer = new StringTokenizer(volumeInfo, ",");
                String systemGuid = volumeInfoTokenizer.nextToken();
                String volumeId = volumeInfoTokenizer.nextToken();
                String volumeNativeId = volumeInfoTokenizer.nextToken();
                nativeVolumeInfoList.add(new VolumeInfo(systemGuid, "vmax", volumeId,
                        volumeNativeId, false, Collections.<String> emptyList()));
                vvNameBuilder.append(VPlexApiConstants.DIST_DEVICE_NAME_DELIM);
                vvNameBuilder.append(VPlexApiConstants.VOLUME_NAME_PREFIX);
                vvNameBuilder.append(systemGuid.substring(systemGuid.indexOf("+") + 1));
                vvNameBuilder.append("-");
                vvNameBuilder.append(volumeNativeId);
            }
            vvNameBuilder.append(VPlexApiConstants.VIRTUAL_VOLUME_SUFFIX);
            String migrationName = _properties.getProperty(MIGRATION_NAME_PROP_KEY);
            List<VPlexMigrationInfo> migrationInfoList = _client.migrateVirtualVolume(
                    migrationName, vvName, nativeVolumeInfoList, false, false, false,
                    true, null);
            Assert.assertEquals(migrationInfoList.size(), 2);

            // Wait until migrations complete and commit the migrations with
            // automatic clean and remove.
            Thread.sleep(15000);
            for (VPlexMigrationInfo migrationInfo : migrationInfoList) {
                List<String> migrationNames = new ArrayList<String>();
                migrationNames.add(migrationInfo.getName());
                List<VPlexMigrationInfo> committedMigrationInfoList = _client.commitMigrations(vvName, migrationNames, true, true, true);
                Assert.assertEquals(committedMigrationInfoList.size(), 1);
                VPlexMigrationInfo committedMigrationInfo = committedMigrationInfoList.get(0);
                vvInfo = committedMigrationInfo.getVirtualVolumeInfo();
                vvName = vvInfo.getName();
            }

            // Clean up the virtual volume. Use the second one as it will have the
            // fully update virtual volume name.
            Assert.assertEquals(vvNameBuilder.toString(), vvInfo.getName());
            _client.deleteVirtualVolume(vvInfo.getName(), true, true);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /*
     * Tests creation of a distributed virtual volume from a non-distributed virtual volume
     * and the addition of a new remote volume.
     */
    @Test
    public void testCreateVolumeWithDeviceMirror() {
        VPlexVirtualVolumeInfo vvInfo = null;
        VolumeInfo newVolumeInfo = null;
        VPlexVirtualVolumeInfo distVolInfo = null;
        try {
            vvInfo = createSimpleVirtualVolume();
            String volumeInfo = _properties.getProperty(DEVICE_MIRROR_TEST_VOLUME_PROP_KEY);
            StringTokenizer tokenizer = new StringTokenizer(volumeInfo, ",");
            String storageSystemGuid = tokenizer.nextToken();
            String volumeId = tokenizer.nextToken();
            String volumeNativeId = tokenizer.nextToken();
            String transferSize = "8M";
            newVolumeInfo = new VolumeInfo(storageSystemGuid, "vmax", volumeId, volumeNativeId, false, Collections.<String> emptyList());
            distVolInfo = _client.upgradeVirtualVolumeToDistributed(vvInfo, newVolumeInfo, true, true, "1", transferSize);
            Assert.assertNotNull(distVolInfo);
            WaitOnRebuildResult goodRebuild = _client.waitOnRebuildCompletion(distVolInfo.getName());
            Assert.assertEquals(WaitOnRebuildResult.SUCCESS, goodRebuild);
        } catch (Exception ex) {
            Assert.fail(ex.getMessage());
        } finally {
            try {
                if (distVolInfo != null) {
                    _client.deleteVirtualVolume(distVolInfo.getName(), true, false);
                } else if (vvInfo != null) {
                    _client.deleteVirtualVolume(vvInfo.getName(), true, false);
                }
            } catch (Exception exx) {
                // ignoring exceptions
                System.out.println("an ignorable exception was encountered: "
                        + exx.getLocalizedMessage());
            }
        }
    }

    /**
     * Creates a simple virtual volume from one storage volume.
     * 
     * @return A reference to the VPlexVirtualVolumeInfo.
     * 
     * @throws VPlexApiException When an error occurs creating the virtual volume.
     */
    private VPlexVirtualVolumeInfo createSimpleVirtualVolume() throws VPlexApiException {
        String volumeInfo = _properties.getProperty(SIMPLE_VV_INFO_PROP_KEY);
        StringTokenizer tokenizer = new StringTokenizer(volumeInfo, ",");
        String storageSystemGuid = tokenizer.nextToken();
        String volumeId = tokenizer.nextToken();
        String volumeNativeId = tokenizer.nextToken();
        List<VolumeInfo> nativeVolumeInfoList = new ArrayList<VolumeInfo>();
        VolumeInfo nativeVolumeInfo = new VolumeInfo(storageSystemGuid, "vmax", volumeId,
                volumeNativeId, false, Collections.<String> emptyList());
        nativeVolumeInfoList.add(nativeVolumeInfo);
        List<VPlexClusterInfo> clusterInfoList = _client.getClusterInfo(false);
        VPlexVirtualVolumeInfo vvInfo = _client.createVirtualVolume(
                nativeVolumeInfoList, false, false, false, null, clusterInfoList, true);
        return vvInfo;
    }
}
