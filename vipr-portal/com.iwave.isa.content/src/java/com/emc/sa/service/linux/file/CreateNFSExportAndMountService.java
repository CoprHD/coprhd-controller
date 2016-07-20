/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import static com.emc.sa.service.ServiceParams.ADVISORY_LIMIT;
import static com.emc.sa.service.ServiceParams.COMMENT;
import static com.emc.sa.service.ServiceParams.FILESYSTEM_NAME;
import static com.emc.sa.service.ServiceParams.GRACE_PERIOD;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.SOFT_LIMIT;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.sa.service.vipr.file.FileStorageUtils.Mount;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.file.FileShareRestRep;

@Service("LinuxCreateMountNFSExport")
public class CreateNFSExportAndMountService extends ViPRService {

    @Param(FILESYSTEM_NAME)
    protected String exportName;

    @Param(VIRTUAL_POOL)
    protected URI virtualPool;
    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;
    @Param(PROJECT)
    protected URI project;
    @Param(COMMENT)
    protected String comment;
    @Param(SIZE_IN_GB)
    protected Double sizeInGb;
    @Param(value = SOFT_LIMIT, required = false)
    protected Double softLimit;
    @Param(value = ADVISORY_LIMIT, required = false)
    protected Double advisoryLimit;
    @Param(value = GRACE_PERIOD, required = false)
    protected Double gracePeriod;

    @Bindable(itemType = FileStorageUtils.Mount.class)
    protected Mount[] mountList;

    private FileShareRestRep fs;

    protected CreateNFSExportAndMountHelper createNFSExportAndMountHelper;

    @Override
    public void init() throws Exception {
        super.init();
        createNFSExportAndMountHelper = CreateNFSExportAndMountHelper.createHelper();
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        if (mountList == null || mountList.length == 0) {
            ExecutionUtils.fail("failTask.CreateFileSystemExport.precheck", new Object[] {}, new Object[] {}); // TODO
        }
    }

    @Override
    public void execute() throws Exception {
        // set fs quotas
        int tempSoftLimit = (softLimit != null) ? softLimit.intValue() : 0;
        int tempAdvisoryLimit = (advisoryLimit != null) ? advisoryLimit.intValue() : 0;
        int tempGracePeriod = (gracePeriod != null) ? gracePeriod.intValue() : 0;
        // create filesystem
        URI fileSystemId = FileStorageUtils.createFileSystem(project, virtualArray, virtualPool, exportName, sizeInGb, tempAdvisoryLimit,
                tempSoftLimit, tempGracePeriod);

        // create nfs export
        if (mountList != null) {
            FileStorageUtils.createFileSystemExport(fileSystemId, comment, mountList[0], null);
            if (mountList.length > 1) {
                FileStorageUtils.updateFileSystemExport(fileSystemId, null, mountList);
            }
        }
        // mount the exports
        for (Mount mount : mountList) {
            Host host = BlockStorageUtils.getHost(mount.hostId);
            acquireHostLock(host, null);
            createNFSExportAndMountHelper.mountExport(fs, mount.hostId, null, mount.mountPath, mount.security, host.getHostName());
            releaseHostLock(host, null);
            ExecutionUtils.addAffectedResource(mount.hostId.toString());
        }
    }
}
