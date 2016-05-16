/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxunity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeApiClientFactory;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.VNXeFileInterface;
import com.emc.storageos.vnxe.models.VNXePool;
import com.emc.storageos.vnxe.models.VNXeStorageSystem;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;

/**
 * Test class for VnxUnityFileStorageDevice
 */
public class VNXUnityFileStorageDeviceTest {
    private VNXUnityFileStorageDevice _unity;
    private StorageSystem _device;
    private StoragePool _pool;
    private String ip = EnvConfig.get("sanity", "unity.ip");
    private String userName = EnvConfig.get("sanity", "unity.username");
    private String password = EnvConfig.get("sanity", "unity.password");
    private String portNumber = EnvConfig.get("sanity", "unity.port");
    private String client1 = EnvConfig.get("sanity", "unity.client1");
    private String client2 = EnvConfig.get("sanity", "unity.client2");
    private String client3 = EnvConfig.get("sanity", "unity.client3");
    private String client4 = EnvConfig.get("sanity", "unity.client4");

    private static final Logger _log = LoggerFactory.getLogger(VNXUnityFileStorageDeviceTest.class);

    @Before
    public void setup() throws Exception {
        _unity = new VNXUnityFileStorageDevice();
        VNXeApiClientFactory factory = new VNXeApiClientFactory();
        factory.init();
        _unity.setVnxeApiClientFactory(factory);

        // storage device object for tests to use
        _device = new StorageSystem();
        _device.setSystemType("unity");
        _device.setIpAddress(ip);
        _device.setPortNumber(Integer.parseInt(portNumber));
        _device.setUsername(userName);
        _device.setPassword(password);

        _pool = new StoragePool();
    }

    /**
     * Tests Unity discovery
     * 
     * @throws Exception
     */
    @Test
    public void testDiscoverUnity() {
        try {
            VNXeApiClient apiclient = _unity.getVnxUnityClient(_device);
            VNXeStorageSystem storageSystem = apiclient.getStorageSystem();
            Assert.assertTrue("Unity discovery failed " + storageSystem.getId(), false);

        } catch (VNXeException uex) {
            System.out.println("Unity discovery failed: " + uex.getCause());
        }

    }

    /**
     * Tests Unity storage pool discovery
     * 
     * @throws Exception
     */
    @Test
    public void testDiscoverUnityStoragePools() {
        try {
            VNXeApiClient apiclient = _unity.getVnxUnityClient(_device);
            List<? extends VNXePool> unityStoragePools = apiclient.getPools();
            Assert.assertTrue("Unity Storage Pool discovery failed " + unityStoragePools.size(), false);

        } catch (VNXeException uex) {
            System.out.println("Unity Storage pool discovery failed: " + uex.getCause());
        }

    }

    /**
     * Tests Unity storage port discovery
     * 
     * @throws Exception
     */
    @Test
    public void testDiscoverUnityStoragePorts() {

        try {
            VNXeApiClient apiclient = _unity.getVnxUnityClient(_device);
            List<VNXeFileInterface> unityStoragePorts = apiclient.getFileInterfaces();
            Assert.assertTrue("Unity Storage Port discovery failed " + unityStoragePorts.size(), false);

        } catch (VNXeException uex) {
            System.out.println("Unity Storage port discovery failed: " + uex.getCause());
        }

    }

    /**
     * Tests file system create, expand, smb share, nfs exports, file system delete.
     * 
     * @throws Exception
     */
    @Test
    public void testFileShares() throws Exception {
        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));
        fs.setLabel("test");
        fs.setCapacity(204800L);

        FileDeviceInputOutput args = new FileDeviceInputOutput();
        args.addStoragePool(_pool);
        args.addFSFileObject(fs);
        Assert.assertTrue("doCreateFS failed", _unity.doCreateFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));

        // test expand capacity
        args.setNewFSCapacity(102400L);
        Assert.assertTrue("doExpandFS failed", _unity.doExpandFS(_device, args)
                .getCommandStatus().equals(Operation.Status.error.name()));

        args.setNewFSCapacity(307200L);
        Assert.assertTrue("doExpandFS failed", _unity.doExpandFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));

        // share file system with SMB
        SMBFileShare smbFileShare = new SMBFileShare("TestSMBShare", "Share created by unit test.", "allow", "change", -1);
        Assert.assertTrue("SMB share doShare() failed", _unity.doShare(_device, args, smbFileShare)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doShare() failed, share not added to FS",
                fs.getSMBFileShares().keySet().contains(smbFileShare.getName()));
        Assert.assertTrue("SMB share doShare() failed, number of shares does not match",
                fs.getSMBFileShares().keySet().size() == 1);

        // add additional share
        SMBFileShare smbFileShare01 = new SMBFileShare("TestSMBShare01", "Share created by unit test.", "allow", "change", -1);
        Assert.assertTrue("SMB share doShare() failed", _unity.doShare(_device, args, smbFileShare01)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doShare() failed, share not added to FS",
                fs.getSMBFileShares().keySet().contains(smbFileShare01.getName()));
        Assert.assertTrue("SMB share doShare() failed, number of shares does not match",
                fs.getSMBFileShares().keySet().size() == 2);

        // add additional share
        SMBFileShare smbFileShare02 = new SMBFileShare("TestSMBShare02", "Share created by unit test.", "allow", "change", -1);
        Assert.assertTrue("SMB share doShare() failed", _unity.doShare(_device, args, smbFileShare02)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doShare() failed, share not added to FS",
                fs.getSMBFileShares().keySet().contains(smbFileShare02.getName()));
        Assert.assertTrue("SMB share doShare() failed, number of shares does not match",
                fs.getSMBFileShares().keySet().size() == 3);

        // modify SMB share
        smbFileShare02.setDescription("Share was modified.");
        Assert.assertTrue("SMB share doShare() for modify failed", _unity.doShare(_device, args, smbFileShare02)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doShare() failed, share not added to FS",
                fs.getSMBFileShares().keySet().contains(smbFileShare02.getName()));
        Assert.assertTrue("SMB share doShare() failed, share was not modified",
                fs.getSMBFileShares().get(smbFileShare02.getName()).getDescription().equals("Share was modified."));
        Assert.assertTrue("SMB share doShare() failed, number of shares does not match",
                fs.getSMBFileShares().keySet().size() == 3);

        // delete one SMB share for FS
        Assert.assertTrue("SMB share doDeleteShare() failed", _unity.doDeleteShare(_device, args, smbFileShare)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertFalse("SMB share doDeleteShare() failed, share was not deleted from FS",
                fs.getSMBFileShares().keySet().contains(smbFileShare.getName()));
        Assert.assertTrue("SMB share doDeleteShare() failed, number of shares does not match",
                fs.getSMBFileShares().keySet().size() == 2);

        // delete all SMB shareds for FS
        Assert.assertTrue("SMB share doDeleteShares() failed", _unity.doDeleteShares(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doDeleteShares() failed, shares were not deleted from FS",
                fs.getSMBFileShares().isEmpty());

        // export
        List<String> clients = new ArrayList<String>();
        clients.add(client1);
        FileExport export1 = new FileExport(clients, "port1", "sys", "root", "nobody", "nfs");

        Assert.assertTrue("doExport failed", _unity.doExport(_device, args, Arrays.asList(export1))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not added to FS", fs.getFsExports().keySet().size() == 1);

        // add client
        clients.add(client2);
        FileExport export2 = new FileExport(clients, "port1", "sys", "root", "nobody", "nfs");

        Assert.assertTrue("doExport failed", _unity.doExport(_device, args, Arrays.asList(export2))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not added to FS", fs.getFsExports().keySet().size() == 1);

        // create a new export
        clients = new ArrayList<String>();
        clients.add(client3);
        FileExport export3 = new FileExport(clients, "port1", "sys", "rw", "root", "nfs");

        Assert.assertTrue("doExport failed", _unity.doExport(_device, args, Arrays.asList(export3))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not added to FS", fs.getFsExports().keySet().size() == 2);

        // unexport
        Assert.assertTrue("doUnexport failed", _unity.doUnexport(_device, args, Arrays.asList(export1))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doUnexport failed, export not deleted from FS", fs.getFsExports().keySet().size() == 1);

        Assert.assertTrue("doUnexport failed", _unity.doUnexport(_device, args, Arrays.asList(export3))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doUnexport failed, export not deleted from FS", fs.getFsExports().keySet().isEmpty());

        // delete
        Assert.assertTrue("doDeleteFs failed", _unity.doDeleteFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));

        VNXeApiClient apiclient = _unity.getVnxUnityClient(_device);
        try {
            apiclient.getFileSystemByFSName(args.getFsLabel());
            Assert.assertTrue("File system delete failed: " + args.getFsLabel(), false);

        } catch (VNXeException uex) {
            System.out.println("doDeleteFS --- delete FS success: " + uex.getCause());
        }
    }

    /**
     * Tests file system delete with existing smb shares and nfs exports.
     * 
     * @throws Exception
     */
    @Test
    public void testFileSystemDelete() throws Exception {
        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));
        fs.setLabel("test");
        fs.setCapacity(102400L);

        FileDeviceInputOutput args = new FileDeviceInputOutput();
        args.addStoragePool(_pool);
        args.addFSFileObject(fs);
        Assert.assertTrue("doCreateFS failed", _unity.doCreateFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));

        // share file system with SMB
        SMBFileShare smbFileShare = new SMBFileShare("TestSMBShare", "Share created by unit test.", "allow", "change", -1);
        Assert.assertTrue("SMB share doShare() failed", _unity.doShare(_device, args, smbFileShare)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doShare() failed, share not added to FS",
                fs.getSMBFileShares().keySet().contains(smbFileShare.getName()));
        Assert.assertTrue("SMB share doShare() failed, number of shares does not match",
                fs.getSMBFileShares().keySet().size() == 1);

        // add additional share
        SMBFileShare smbFileShare01 = new SMBFileShare("TestSMBShare01", "Share created by unit test.", "allow", "change", -1);
        Assert.assertTrue("SMB share doShare() failed", _unity.doShare(_device, args, smbFileShare01)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doShare() failed, share not added to FS",
                fs.getSMBFileShares().keySet().contains(smbFileShare01.getName()));
        Assert.assertTrue("SMB share doShare() failed, number of shares does not match",
                fs.getSMBFileShares().keySet().size() == 2);

        // add additional share
        SMBFileShare smbFileShare02 = new SMBFileShare("TestSMBShare02", "Share created by unit test.", "allow", "change", -1);
        Assert.assertTrue("SMB share doShare() failed", _unity.doShare(_device, args, smbFileShare02)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doShare() failed, share not added to FS",
                fs.getSMBFileShares().keySet().contains(smbFileShare02.getName()));
        Assert.assertTrue("SMB share doShare() failed, number of shares does not match",
                fs.getSMBFileShares().keySet().size() == 3);

        // delete one SMB share for FS
        Assert.assertTrue("SMB share doDeleteShare() failed", _unity.doDeleteShare(_device, args, smbFileShare)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertFalse("SMB share doDeleteShare() failed, share was not deleted from FS",
                fs.getSMBFileShares().keySet().contains(smbFileShare.getName()));
        Assert.assertTrue("SMB share doDeleteShare() failed, number of shares does not match",
                fs.getSMBFileShares().keySet().size() == 2);

        // export
        List<String> clients = new ArrayList<String>();
        clients.add(client1);
        FileExport export1 = new FileExport(clients, "port1", "sys", "root", "nobody", "nfs");

        Assert.assertTrue("doExport failed", _unity.doExport(_device, args, Arrays.asList(export1))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not added to FS", fs.getFsExports().keySet().size() == 1);

        // add client
        clients.add(client2);
        FileExport export2 = new FileExport(clients, "port1", "sys", "root", "nobody", "nfs");

        Assert.assertTrue("doExport failed", _unity.doExport(_device, args, Arrays.asList(export2))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not added to FS", fs.getFsExports().keySet().size() == 1);

        // create a new export
        clients = new ArrayList<String>();
        clients.add(client3);
        FileExport export3 = new FileExport(clients, "port1", "sys", "rw", "root", "nfs");

        Assert.assertTrue("doExport failed", _unity.doExport(_device, args, Arrays.asList(export3))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not added to FS", fs.getFsExports().keySet().size() == 2);

        // delete
        Assert.assertTrue("doDeleteFs failed", _unity.doDeleteFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));

        VNXeApiClient apiclient = _unity.getVnxUnityClient(_device);
        try {
            apiclient.getFileSystemByFSName(args.getFsLabel());
            Assert.assertTrue("File system delete failed: " + args.getFsLabel(), false);

        } catch (VNXeException uex) {
            System.out.println("doDeleteFS --- delete FS success: " + uex.getCause());
        }
    }

    /**
     * Tests that we rollback file share create in case when file share quota set fails.
     * 
     * @throws Exception
     */
    @Test
    public void testFileShareCreateNegative() throws Exception {
        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));
        fs.setLabel("neTest");
        // set negative capacity to force failure of quota creation
        fs.setCapacity(-102400L);

        FileDeviceInputOutput args = new FileDeviceInputOutput();
        args.addStoragePool(_pool);
        args.addFSFileObject(fs);
        Assert.assertTrue("doCreateFS was expected to fail due to negative quota in the test.", _unity.doCreateFS(_device, args)
                .getCommandStatus().equals(Operation.Status.error.name()));

        // verify that doCreate() was rolled back
        // check that file share does not exist
        // try to get list of shares for directory which we tried to create
        VNXeApiClient apiclient = _unity.getVnxUnityClient(_device);
        try {
            apiclient.getFileSystemByFSName(args.getFileObjMountPath());
            Assert.assertTrue("File share create rollback failed, fs path: " + args.getFileObjMountPath(), false);

        } catch (VNXeException uex) {
            System.out.println("testFileShareCreateNegative --- rollback success: " + uex.getCause());
        }

    }

    /**
     * Tests snapshot create, smb share, nfs exports, snapshot delete.
     * 
     * @throws Exception
     */
    @Test
    public void testSnapshots() throws Exception {
        // create FS to use
        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));
        fs.setLabel("test");
        fs.setCapacity(102400L);

        FileDeviceInputOutput args = new FileDeviceInputOutput();
        args.addStoragePool(_pool);
        args.addFSFileObject(fs);
        Assert.assertTrue("doCreateFS failed", _unity.doCreateFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));

        // create snap
        Snapshot snap = new Snapshot();
        snap.setId(URIUtil.createId(Snapshot.class));
        snap.setLabel("test_snap");
        args.addSnapshotFileObject(snap);
        Assert.assertTrue("doSnapshotFS failed", _unity.doSnapshotFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));

        // share snap with SMB
        SMBFileShare smbFileShare = new SMBFileShare("TestSMBShare", "Share created by unit test.", "allow", "change", -1);
        Assert.assertTrue("SMB share doShare() failed", _unity.doShare(_device, args, smbFileShare)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doShare() failed, share not added to snap",
                snap.getSMBFileShares().keySet().contains(smbFileShare.getName()));
        Assert.assertTrue("SMB share doShare() failed, number of shares does not match",
                snap.getSMBFileShares().keySet().size() == 1);

        // add additional share
        SMBFileShare smbFileShare01 = new SMBFileShare("TestSMBShare01", "Share created by unit test.", "allow", "change", -1);
        Assert.assertTrue("SMB share doShare() failed", _unity.doShare(_device, args, smbFileShare01)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doShare() failed, share not added to snap",
                snap.getSMBFileShares().keySet().contains(smbFileShare01.getName()));
        Assert.assertTrue("SMB share doShare() failed, number of shares does not match",
                snap.getSMBFileShares().keySet().size() == 2);

        // add additional share
        SMBFileShare smbFileShare02 = new SMBFileShare("TestSMBShare02", "Share created by unit test.", "allow", "change", -1);
        Assert.assertTrue("SMB share doShare() failed", _unity.doShare(_device, args, smbFileShare02)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doShare() failed, share not added to snap",
                snap.getSMBFileShares().keySet().contains(smbFileShare02.getName()));
        Assert.assertTrue("SMB share doShare() failed, number of shares does not match",
                snap.getSMBFileShares().keySet().size() == 3);

        // delete one SMB share for the snap
        Assert.assertTrue("SMB share doDeleteShare() failed", _unity.doDeleteShare(_device, args, smbFileShare)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertFalse("SMB share doDeleteShare() failed, share was not deleted from snap",
                snap.getSMBFileShares().keySet().contains(smbFileShare.getName()));
        Assert.assertTrue("SMB share doDeleteShare() failed, number of shares does not match",
                snap.getSMBFileShares().keySet().size() == 2);

        // delete all SMB shares for snap
        Assert.assertTrue("SMB share doDeleteShares() failed", _unity.doDeleteShares(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doDeleteShares() failed, shares were not deleted from snap",
                snap.getSMBFileShares().isEmpty());

        // export snap
        List<String> clients = new ArrayList<String>();
        clients.add(client1);
        FileExport export1 = new FileExport(clients, "", "sys", "root", "nobody", "nfs");
        Assert.assertTrue("doExport failed", _unity.doExport(_device, args, Arrays.asList(export1))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not added to snapshot", snap.getFsExports().keySet().size() == 1);

        // add client to the same export
        clients.add(client2);
        FileExport export2 = new FileExport(clients, "", "sys", "root", "nobody", "nfs");
        Assert.assertTrue("doExport failed", _unity.doExport(_device, args, Arrays.asList(export1))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not added to snapshot", snap.getFsExports().keySet().size() == 1);

        // create a new export
        clients = new ArrayList<String>();
        clients.add(client3);
        clients.add(client4);
        FileExport export3 = new FileExport(clients, "", "sys", "rw", "nobody", "nfs");
        Assert.assertTrue("doExport failed", _unity.doExport(_device, args, Arrays.asList(export3))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not added to snapshot", snap.getFsExports().keySet().size() == 2);

        // unexport
        Assert.assertTrue("doUnexport failed", _unity.doUnexport(_device, args, Arrays.asList(export1))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doUnexport failed, export not deleted from snapshot", snap.getFsExports().keySet().size() == 1);

        Assert.assertTrue("doUnexport failed", _unity.doUnexport(_device, args, Arrays.asList(export3))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doUnexport failed, export not deleted from snapshot", snap.getFsExports().keySet().isEmpty());

        // delete snap
        Assert.assertTrue("doDeleteSnapshot failed", _unity.doDeleteSnapshot(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        VNXeApiClient apiclient = _unity.getVnxUnityClient(_device);
        try {
            apiclient.getFileSystemByFSName(args.getFileObjMountPath());
            Assert.assertTrue("Snapshot delete failed: " + args.getFileObjMountPath(), false);

        } catch (VNXeException uex) {
            System.out.println("doDeleteSnapshot --- delete snapshot success: " + uex.getCause());
        }

        // delete file system
        Assert.assertTrue("doDeleteFS failed", _unity.doDeleteFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        try {
            apiclient.getFileSystemByFSName(args.getFsMountPath());
            Assert.assertTrue("FS delete failed: " + args.getFsMountPath(), false);

        } catch (VNXeException uex) {
            System.out.println("doDeleteFS --- delete FS success: " + uex.getCause());
        }
    }

    /**
     * Tests snapshot delete with existing SMB shares and NFS exports.
     * 
     * @throws Exception
     */
    @Test
    public void testSnapshotDelete() throws Exception {
        // create FS to use
        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));
        fs.setLabel("test");
        fs.setCapacity(102400L);

        FileDeviceInputOutput args = new FileDeviceInputOutput();
        args.addStoragePool(_pool);
        args.addFSFileObject(fs);
        Assert.assertTrue("doCreateFS failed", _unity.doCreateFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));

        // create snap
        Snapshot snap = new Snapshot();
        snap.setId(URIUtil.createId(Snapshot.class));
        snap.setLabel("test_snap");
        args.addSnapshotFileObject(snap);
        Assert.assertTrue("doSnapshotFS failed", _unity.doSnapshotFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));

        // share snap with SMB
        SMBFileShare smbFileShare = new SMBFileShare("TestSMBShare", "Share created by unit test.", "allow", "change", -1);
        Assert.assertTrue("SMB share doShare() failed", _unity.doShare(_device, args, smbFileShare)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doShare() failed, share not added to snap",
                snap.getSMBFileShares().keySet().contains(smbFileShare.getName()));
        Assert.assertTrue("SMB share doShare() failed, number of shares does not match",
                snap.getSMBFileShares().keySet().size() == 1);

        // add additional share
        SMBFileShare smbFileShare01 = new SMBFileShare("TestSMBShare01", "Share created by unit test.", "allow", "change", -1);
        Assert.assertTrue("SMB share doShare() failed", _unity.doShare(_device, args, smbFileShare01)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doShare() failed, share not added to snap",
                snap.getSMBFileShares().keySet().contains(smbFileShare01.getName()));
        Assert.assertTrue("SMB share doShare() failed, number of shares does not match",
                snap.getSMBFileShares().keySet().size() == 2);

        // add additional share
        SMBFileShare smbFileShare02 = new SMBFileShare("TestSMBShare02", "Share created by unit test.", "allow", "change", -1);
        Assert.assertTrue("SMB share doShare() failed", _unity.doShare(_device, args, smbFileShare02)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("SMB share doShare() failed, share not added to snap",
                snap.getSMBFileShares().keySet().contains(smbFileShare02.getName()));
        Assert.assertTrue("SMB share doShare() failed, number of shares does not match",
                snap.getSMBFileShares().keySet().size() == 3);

        // export snap
        List<String> clients = new ArrayList<String>();
        clients.add(client1);
        FileExport export1 = new FileExport(clients, "", "sys", "root", "nobody", "nfs");
        Assert.assertTrue("doExport failed", _unity.doExport(_device, args, Arrays.asList(export1))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not added to snapshot", snap.getFsExports().keySet().size() == 1);

        // add client to the same export
        clients.add(client2);
        FileExport export2 = new FileExport(clients, "", "sys", "root", "nobody", "nfs");
        Assert.assertTrue("doExport failed", _unity.doExport(_device, args, Arrays.asList(export1))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not added to snapshot", snap.getFsExports().keySet().size() == 1);

        // create a new export
        clients = new ArrayList<String>();
        clients.add(client3);
        clients.add(client4);
        FileExport export3 = new FileExport(clients, "", "sys", "rw", "nobody", "nfs");
        Assert.assertTrue("doExport failed", _unity.doExport(_device, args, Arrays.asList(export3))
                .getCommandStatus().equals(Operation.Status.ready.name()));
        Assert.assertTrue("doExport failed, export not added to snapshot", snap.getFsExports().keySet().size() == 2);

        // delete snap
        Assert.assertTrue("doDeleteSnapshot failed", _unity.doDeleteSnapshot(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        VNXeApiClient apiclient = _unity.getVnxUnityClient(_device);
        try {
            apiclient.getFileSystemByFSName(args.getFileObjMountPath());
            Assert.assertTrue("Snapshot delete failed: " + args.getFileObjMountPath(), false);

        } catch (VNXeException uex) {
            System.out.println("doDeleteSnapshot --- delete snapshot success: " + uex.getCause());
        }

        // delete file system
        Assert.assertTrue("doDeleteFS failed", _unity.doDeleteFS(_device, args)
                .getCommandStatus().equals(Operation.Status.ready.name()));
        try {
            apiclient.getFileSystemByFSName(args.getFsMountPath());
            Assert.assertTrue("FS delete failed: " + args.getFsMountPath(), false);

        } catch (VNXeException uex) {
            System.out.println("doDeleteFS --- delete FS success: " + uex.getCause());
        }
    }

}
