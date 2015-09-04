/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.requests;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.ModifyFileSystemParam;
import com.emc.storageos.vnxe.models.NfsShareCreateParam;
import com.emc.storageos.vnxe.models.NfsShareDeleteParam;
import com.emc.storageos.vnxe.models.NfsShareParam;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.CreateFileSystemParam;
import com.emc.storageos.vnxe.models.FileSystemParam;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeFSSupportedProtocolEnum;
import com.emc.storageos.vnxe.models.VNXeFileSystem;
import com.emc.storageos.vnxe.models.VNXeHost;
import com.sun.jersey.api.client.WebResource;

public class FileSystemActionRequestTest {
    private static KHClient _client;
    private static String host = EnvConfig.get("sanity", "vnxe.host");
    private static String userName = EnvConfig.get("sanity", "vnxe.username");
    private static String password = EnvConfig.get("sanity", "vnxe.password");

    @BeforeClass
    public static void setup() throws Exception {
        _client = new KHClient(host, userName, password);
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
            e.printStackTrace();
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
