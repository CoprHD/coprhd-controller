/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.FileSystemSnapCreateParam;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeFileSystemSnap;

public class FileSystemSnapRequestsTest {
    private static KHClient _client;
    private static String host = EnvConfig.get("sanity", "vnxe.host");
    private static String userName = EnvConfig.get("sanity", "vnxe.username");
    private static String password = EnvConfig.get("sanity", "vnxe.password");

    private static final Logger logger = LoggerFactory.getLogger(FileSystemSnapRequestsTest.class);

    @BeforeClass
    public static void setup() throws Exception {

        synchronized (_client) {
            _client = new KHClient(host, userName, password);
        }
    }

    // @Test
    public void createFileSystemSnap() {
        FileSystemSnapCreateParam parm = new FileSystemSnapCreateParam();
        VNXeBase resource = new VNXeBase();
        resource.setId("res_12");
        parm.setStorageResource(resource);
        parm.setName("test-snap");
        parm.setIsReadOnly(false);

        FileSystemSnapRequests req = new FileSystemSnapRequests(_client);
        VNXeCommandJob response = null;
        try {
            response = req.createFileSystemSnap(parm);
        } catch (VNXeException e) {
            // TODO Auto-generated catch block
            logger.error("VNXeException occured", e);
        }

        System.out.println(response.getId() + "state: " + response.getState());

    }

    /*
     * @Test
     * public void getFileSystemSnap() {
     * 
     * FileSystemSnapRequests req = new FileSystemSnapRequests(_client);
     * 
     * VNXeFileSystemSnap response = null;
     * try {
     * response = req.getByName("test-file-01-snap");
     * } catch (VNXeException e) {
     * // TODO Auto-generated catch block
     * logger.error("VNXeException occured", e);
     * }
     * 
     * System.out.println(response.getId());
     * 
     * 
     * }
     */

    // @Test
    public void deleteSnap() {
        FileSystemSnapRequests req = new FileSystemSnapRequests(_client);
        VNXeCommandJob response = null;
        try {
            response = req.deleteFileSystemSnap("98784247867", "3.1.0");
        } catch (VNXeException e) {
            logger.error("VNXeException occured", e);
        }

        System.out.println(response.getId());
    }

    @Test
    public void restoreSnap() {
        FileSystemSnapRequests req = new FileSystemSnapRequests(_client);
        VNXeCommandJob response = null;
        try {
            response = req.restoreFileSystemSnap("98784247833", null, "3.1.0");
        } catch (VNXeException e) {
            logger.error("VNXeException occured", e);
        }

        System.out.println(response.getId());
    }

    // @Test
    public void getFileSnaps() {
        FileSystemSnapRequests req = new FileSystemSnapRequests(_client);
        List<VNXeFileSystemSnap> snaps = req.getFileSystemSnaps("res_50");
        System.out.println(snaps.size());
    }
}
