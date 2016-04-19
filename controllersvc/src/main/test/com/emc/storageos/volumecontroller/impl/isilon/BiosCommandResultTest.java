/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.isilon;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.util.DummyDbClient;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;

/**
 * Test class for IsilonFileStorageDevice
 */
public class BiosCommandResultTest {
    private IsilonFileStorageDevice _isi;
    private StorageSystem _device;
    private StoragePool _pool;
    private String ip = EnvConfig.get("sanity", "isilon.ip");
    private String userName = EnvConfig.get("sanity", "isilon.username");
    private String password = EnvConfig.get("sanity", "isilon.password");
    private String portNumber = EnvConfig.get("sanity", "isilon.port");

    @Before
    public void setUp() throws Exception {
        _isi = new IsilonFileStorageDevice();
        IsilonApiFactory factory = new IsilonApiFactory();
        factory.init();
        _isi.setIsilonApiFactory(factory);
        _isi.setDbClient(new DummyDbClient() {
            @Override
            public List<URI> queryByConstraint(Constraint constraint) throws DatabaseException {
                return new ArrayList<>();
            }
        });

        // storage device object for tests to use
        _device = new StorageSystem();
        _device.setIpAddress(ip);
        _device.setPortNumber(Integer.parseInt(portNumber));
        _device.setUsername(userName);
        _device.setPassword(password);

        _pool = new StoragePool();
    }

    // In the default/public build, we do not have access to an isilon, so this test is off by default.
    @Ignore
    @Test
    public void doCreateFS() throws Exception {
        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));
        fs.setLabel("test");
        fs.setCapacity(102400L);

        FileDeviceInputOutput args = new FileDeviceInputOutput();
        args.addStoragePool(_pool);
        args.setVPool(createVPool());
        args.addFSFileObject(fs);
        final BiosCommandResult result = _isi.doCreateFS(_device, args);
        Assert.assertEquals(Status.error.name(), result.getCommandStatus());
        Assert.assertEquals(false, result.getCommandSuccess());
        Assert.assertEquals(ServiceCode.ISILON_CONNECTION_ERROR, result.getServiceCoded().getServiceCode());
        Assert.assertEquals("Unable to connect to isilon using url https://127.0.0.1:8080/",
                result.getMessage());
        Assert.assertNotNull(result.getServiceCoded());
    }

    private VirtualPool createVPool() {
        VirtualPool vpool = new VirtualPool();
        vpool.setId(URIUtil.createId(VirtualPool.class));
        vpool.setLabel("test-vpool");
        return vpool;
    }
}
