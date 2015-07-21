/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.isilon;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
//import com.emc.storageos.simulators.impl.Main;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Test class for IsilonFileStorageDevice
 */
public class IsilonSimulatorTest {
    private IsilonFileStorageDevice _isi;
    private StorageSystem _device;
    private StoragePool _pool;
    private String ip = EnvConfig.get("sanity", "isilon.ip");
    private String userName = EnvConfig.get("sanity", "isilon.username");
    private String password = EnvConfig.get("sanity", "isilon.password");
    private String portNumber = EnvConfig.get("sanity", "isilon.port");
    private String client1 = EnvConfig.get("sanity", "isilon.client1");
    private String client2 = EnvConfig.get("sanity", "isilon.client2");

    private static final Logger _log = LoggerFactory.getLogger(IsilonFileStorageDeviceTest.class);

    @Before
    public void setUp() throws Exception {
        // start up simulator server
       // Main.main(new String[]{ "/" + getClass().getResource("/simulator-config.xml").getPath() } );
        _isi = new IsilonFileStorageDevice();
        IsilonApiFactory factory = new IsilonApiFactory();
        factory.init();
        _isi.setIsilonApiFactory(factory);

        // storage device object for tests to use
        _device = new StorageSystem();
        _device.setSystemType("isilon");
        _device.setIpAddress(ip);
        _device.setPortNumber(Integer.parseInt(portNumber));
        _device.setUsername(userName);
        _device.setPassword(password);

        _pool = new StoragePool();
    }

    private void testFileShares() throws Exception {
        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));
        fs.setLabel("test");
        fs.setCapacity(102400L);

        FileDeviceInputOutput args = new FileDeviceInputOutput();
        args.addStoragePool(_pool);
        args.addFSFileObject(fs);
        Assert.assertTrue("doCreateFS failed", _isi.doCreateFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));

        // export
        List<String> clients = new ArrayList<String>();
        clients.add(client1);
        clients.add(client2);
        FileExport export = new FileExport(clients, "", "sys", "root", "nobody", "nfs");

        Assert.assertTrue("doExport failed", _isi.doExport(_device, args, Arrays.asList(export))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not added to FS", fs.getFsExports().keySet().size() == 1);

        // unexport
        Assert.assertTrue("doUnexport failed", _isi.doUnexport(_device, args, Arrays.asList(export))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doUnexport failed, export not deleted from FS", fs.getFsExports().keySet().isEmpty());

        // delete
        Assert.assertTrue("doDeleteFs failed", _isi.doDeleteFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));
    }

    private void testSnapshots() throws Exception {
        // create FS to use
        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));
        fs.setLabel("test");
        fs.setCapacity(102400L);

        FileDeviceInputOutput args = new FileDeviceInputOutput();
        args.addStoragePool(_pool);
        args.addFSFileObject(fs);
        Assert.assertTrue("doCreateFS failed", _isi.doCreateFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));

        // create
        Snapshot snap = new Snapshot();
        snap.setId(URIUtil.createId(Snapshot.class));
        snap.setLabel("test_snap");
        args.addSnapshotFileObject(snap);
        Assert.assertTrue("doCreateFS failed", _isi.doSnapshotFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));

        // export
        List<String> clients = new ArrayList<String>();
        clients.add(client1);
        clients.add(client2);
        FileExport export = new FileExport(clients, "", "sys", "root", "nobody", "nfs");
        Assert.assertTrue("doExport failed", _isi.doExport(_device, args, Arrays.asList(export))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not added to snapshot", snap.getFsExports().keySet().size() == 1);

        // unexport
        Assert.assertTrue("doUnexport failed", _isi.doUnexport(_device, args, Arrays.asList(export))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not deleted from snapshot", snap.getFsExports().keySet().isEmpty());

        // delete
        Assert.assertTrue("doDeleteSnapshot failed", _isi.doDeleteSnapshot(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));

        Assert.assertTrue("doDeleteFs failed", _isi.doDeleteFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));
    }

    @Test
    public void testAll() throws Exception {
        testFileShares();
        testSnapshots();
    }

}

