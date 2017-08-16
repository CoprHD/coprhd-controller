/*
 * Copyright (c) 2016 EMC Corporation
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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.sa.service.vipr.file.FileStorageUtils.FileExportRule;
import com.emc.sa.service.vipr.file.FileStorageUtils.Mount;
import com.emc.storageos.db.client.model.Host;

/**
 * 
 * @author yelkaa
 * 
 */

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

    protected MountNFSExportHelper mountNFSExportHelper;


    @Override
    public void init() throws Exception {
        super.init();
        mountNFSExportHelper = MountNFSExportHelper.createHelper();
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        if (mountList == null || mountList.length == 0) {
            ExecutionUtils.fail("failTask.CreateFileSystemExport.precheck", new Object[] {}, new Object[] {});
        }
    }

    @Override
    public void execute() throws Exception {
        // set fs quotas
        int tempSoftLimit = (softLimit != null) ? softLimit.intValue() : 0;
        int tempAdvisoryLimit = (advisoryLimit != null) ? advisoryLimit.intValue() : 0;
        int tempGracePeriod = (gracePeriod != null) ? gracePeriod.intValue() : 0;

        // convert mount object to export
        List<FileExportRule> exportList = new ArrayList<FileExportRule>();
        for (Mount mount : mountList) {
            FileExportRule export = new FileExportRule();
            List<String> exportHosts = new ArrayList<String>();
            exportHosts.add(BlockStorageUtils.getHost(mount.getHost()).getHostName());
            export.setExportHosts(exportHosts);
            export.setPermission(mount.getPermission());
            export.setSecurity(mount.getSecurity());
            export.setDomain(mount.getDomain());
            export.setRootUserMapping(mount.getRootUserMapping());
            exportList.add(export);
        }

        // create filesystem
        URI fileSystemId = FileStorageUtils.createFileSystemWithoutRollBack(project, virtualArray, virtualPool, exportName, sizeInGb,
                tempAdvisoryLimit,
				tempSoftLimit, tempGracePeriod, null);

        // create nfs export
        if (exportList != null) {
            String rootUserMapping = exportList.get(0).getRootUserMapping().trim();
            String domain = exportList.get(0).getDomain();
            if(StringUtils.isNotBlank(domain)) {
                rootUserMapping = domain.trim() + "\\" + rootUserMapping.trim();
            }
            FileStorageUtils.createFileSystemExportWithoutRollBack(fileSystemId, comment, exportList.get(0).getSecurity(),
                    exportList.get(0).getPermission(), rootUserMapping, exportList.get(0).getExportHosts(), null, false);
            if (!exportList.isEmpty()) {
                FileStorageUtils.updateFileSystemExport(fileSystemId, null, exportList.toArray(new FileExportRule[exportList.size()]),
                        false);
            }
        }
        // mount the exports
        for (Mount mount : mountList) {
            Host host = BlockStorageUtils.getHost(mount.getHost());
            mountNFSExportHelper.mountExport(fileSystemId, mount.getHost(), null, mount.getMountPath(), mount.getSecurity(),
                    host.getHostName(), mount.getFsType());
            ExecutionUtils.addAffectedResource(mount.getHost().toString());
        }
    }
}
