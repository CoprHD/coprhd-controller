/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 * @author mudit.jain@emc.com
 */

package com.emc.storageos.volumecontroller.impl.isilon;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy;
import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;

public class IsilonFileStorageDeviceReplicationTest {
    private static IsilonFileStorageDevice _isi;
    private static StorageSystem _device;
    private static String ip = EnvConfig.get("sanity", "isilon.ip");
    private static String userName = EnvConfig.get("sanity", "isilon.username");
    private static String password = EnvConfig.get("sanity", "isilon.password");
    private static BiosCommandResult result;

    public static void setUp() {
        _isi = new IsilonFileStorageDevice();
        IsilonApiFactory factory = new IsilonApiFactory();
        factory.init();
        _isi.setIsilonApiFactory(factory);
        _device = new StorageSystem();
        _device.setSystemType("isilon");
        _device.setIpAddress(ip);
        _device.setPortNumber(8080);
        _device.setUsername(userName);
        _device.setPassword(password);
    }

    public static void testCreateReplicationPolicy() {
        IsilonFileStorageDeviceReplicationTest.setUp();
        result = _isi.doCreateReplicationPolicy(_device, "mudit_policy", "/ifs/vipr/muditjainsource", "10.247.96.116",
                "/ifs/vipr/mudtjaintarget", IsilonSyncPolicy.Action.copy, "this_is_mudit_policy", "");
        result.getCommandStatus();
    }

    public static void testStartReplicationPolicy() {
        IsilonFileStorageDeviceReplicationTest.setUp();
        result = _isi.doStartReplicationPolicy(_device, "mudit_policy");
        result.getCommandStatus();
    }

    public static void testPauseReplicationPolicy() {
        IsilonFileStorageDeviceReplicationTest.setUp();
        result = _isi.doPauseReplicationPolicy(_device, "mudit_policy");
        result.getCommandStatus();
    }

    public static void testResumeReplicationPolicy() {
        IsilonFileStorageDeviceReplicationTest.setUp();
        result = _isi.doResumeReplicationPolicy(_device, "mudit_policy");
        // result = _isi.doResumeReplicationPolicy(_device, "mudit_polic"); // negative test to with non-existed policy
        result.getCommandStatus();
    }

    public static void testDeleteReplicationPolicy() {
        IsilonFileStorageDeviceReplicationTest.setUp();
        result = _isi.dodeleteReplicationPolicy(_device, "mudit_policy");
        result.getCommandStatus();
    }

    public static void testModifyReplicationPolicy() {
        IsilonFileStorageDeviceReplicationTest.setUp();
        result = _isi.doModifyReplicationPolicy(_device, "test_policy", "");
        result.getCommandStatus();
    }

    public static void testFailover() {
        _isi = new IsilonFileStorageDevice();
        IsilonApiFactory factory = new IsilonApiFactory();
        factory.init();
        _isi.setIsilonApiFactory(factory);
        _device = new StorageSystem();
        _device.setSystemType("isilon");
        _device.setIpAddress("10.247.96.116");
        _device.setPortNumber(8080);
        _device.setUsername(userName);
        _device.setPassword(password);

        result = _isi.doFailover(_device, "mudit_policy");
        result.getCommandStatus();
    }

    public static void testFailBack() {
        _isi = new IsilonFileStorageDevice();
        IsilonApiFactory factory = new IsilonApiFactory();
        factory.init();
        _isi.setIsilonApiFactory(factory);
        _device = new StorageSystem();
        _device.setSystemType("isilon");
        _device.setIpAddress("10.247.96.111");
        _device.setPortNumber(8080);
        _device.setUsername(userName);
        _device.setPassword(password);
        StorageSystem _device2 = new StorageSystem();
        _device2.setSystemType("isilon");
        _device2.setIpAddress("10.247.96.116");
        _device2.setPortNumber(8080);
        _device2.setUsername(userName);
        _device2.setPassword(password);

        result = _isi.doFailBack(_device, _device2, "mudit_policy");

    }

    public static void main(String args[]) {

        // IsilonFileStorageDeviceReplicationTest.testCreateReplicationPolicy();
        // IsilonFileStorageDeviceReplicationTest.testStartReplicationPolicy();
        // IsilonFileStorageDeviceReplicationTest.testPauseReplicationPolicy();
        // IsilonFileStorageDeviceReplicationTest.testResumeReplicationPolicy();
        // IsilonFileStorageDeviceReplicationTest.testDeleteReplicationPolicy();
        // IsilonFileStorageDeviceReplicationTest.testModifyReplicationPolicy();
        // IsilonFileStorageDeviceReplicationTest.testFailover();
        IsilonFileStorageDeviceReplicationTest.testFailBack();
    }
}
