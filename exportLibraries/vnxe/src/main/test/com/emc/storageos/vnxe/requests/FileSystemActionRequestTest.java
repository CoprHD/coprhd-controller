/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.ModifyFileSystemParam;
import com.emc.storageos.vnxe.models.NfsShareCreateParam;
import com.emc.storageos.vnxe.models.NfsShareDeleteParam;
import com.emc.storageos.vnxe.models.NfsShareParam;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.CreateFileSystemParam;
import com.emc.storageos.vnxe.models.FileSystemParam;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeFSSupportedProtocolEnum;

public class FileSystemActionRequestTest {
    private static KHClient _client;
    private static String host = EnvConfig.get("sanity", "vnxe.host");
    private static String userName = EnvConfig.get("sanity", "vnxe.username");
    private static String password = EnvConfig.get("sanity", "vnxe.password");
    private static final Logger logger = LoggerFactory.getLogger(FileSystemActionRequestTest.class);

    @BeforeClass
    public static void setup() throws Exception {
        synchronized (_client) {
            _client = new KHClient(host, userName, password);
        }
    }

    @Test
    public void createFileSystem() {
        CreateFileSystemParam parm = new CreateFileSystemParam();
        parm.setName("test-file-03");

        FileSystemParam fsParm = new FileSystemParam();
        fsParm.setIsThinEnabled(true);
        VNXeBase nasServer = new VNXeBase();
        nasServer.setId("nas_1");
        fsParm.setNasServer(nasServer);
        VNXeBase pool = new VNXeBase();
        pool.setId("pool_1");
        fsParm.setPool(pool);
        fsParm.setSize(2200000000L);
        fsParm.setSupportedProtocols(0);
        fsParm.setIsCacheDisabled(true);
        fsParm.setSupportedProtocols(VNXeFSSupportedProtocolEnum.NFS_CIFS.getValue());

        parm.setFsParameters(fsParm);
        FileSystemActionRequest req = new FileSystemActionRequest(_client);
        VNXeCommandJob response = null;
        try {
            response = req.createFileSystemAsync(parm);
        } catch (VNXeException e) {
            // TODO Auto-generated catch block
            logger.error("VNXeException occured", e);
        }

        System.out.println(response.getId() + "state: " + response.getState());

    }

    // @Test
    public void modifyFileSystem() {
        ModifyFileSystemParam parm = new ModifyFileSystemParam();

        NfsShareCreateParam nfsShareParm = new NfsShareCreateParam();
        nfsShareParm.setName("fs-21-share-1");
        nfsShareParm.setPath("/");
        NfsShareParam shareParm = new NfsShareParam();
        List<VNXeBase> hosts = new ArrayList<VNXeBase>();
        VNXeBase host = new VNXeBase();
        host.setId("Host_1");
        hosts.add(host);
        shareParm.setReadWriteHosts(hosts);
        nfsShareParm.setNfsShareParameters(shareParm);
        List<NfsShareCreateParam> nfsList = new ArrayList<NfsShareCreateParam>();
        nfsList.add(nfsShareParm);
        parm.setNfsShareCreate(nfsList);

        FileSystemActionRequest req = new FileSystemActionRequest(_client);
        VNXeCommandJob job = req.modifyFileSystemAsync(parm, "res_4");
        System.out.println(job.getId());

    }

    // @Test
    public void removeNfsShare() {
        ModifyFileSystemParam parm = new ModifyFileSystemParam();

        NfsShareDeleteParam nfsShareParm = new NfsShareDeleteParam();
        VNXeBase nfs = new VNXeBase();
        nfs.setId("NFSShare_1");
        nfsShareParm.setNfsShare(nfs);
        List<NfsShareDeleteParam> shares = new ArrayList<NfsShareDeleteParam>();
        shares.add(nfsShareParm);
        parm.setNfsShareDelete(shares);

        FileSystemActionRequest req = new FileSystemActionRequest(_client);
        VNXeCommandJob job = req.modifyFileSystemAsync(parm, "res_4");
        System.out.println(job.getId());

    }

    // @Test
    public void expandFileSystem() {
        String resourceId = "res_4";
        long newSize = 2000000000L;
        ModifyFileSystemParam modifyFSParm = new ModifyFileSystemParam();

        // set fileSystemParam
        FileSystemParam fsParm = new FileSystemParam();
        fsParm.setSize(newSize);
        modifyFSParm.setFsParameters(fsParm);
        FileSystemActionRequest req = new FileSystemActionRequest(_client);
        VNXeCommandJob job = req.modifyFileSystemAsync(modifyFSParm, resourceId);
        System.out.println(job.getId());

    }
}
