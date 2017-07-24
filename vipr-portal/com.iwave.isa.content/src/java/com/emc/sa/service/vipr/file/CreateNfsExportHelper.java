/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.BYPASS_DNS_CHECK;
import static com.emc.sa.service.ServiceParams.COMMENT;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.VOLUME_NAME;
import static com.emc.sa.service.vipr.file.FileConstants.DEFAULT_ROOT_USER;
import static com.emc.sa.service.vipr.file.FileConstants.DEFAULT_SECURITY_TYPE;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.storageos.model.file.FileSystemExportParam;

public class CreateNfsExportHelper {

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
    @Param(VOLUME_NAME)
    protected String exportName;

    private String permissions;
    private List<String> exportHosts;
    private String security = DEFAULT_SECURITY_TYPE;
    private String rootUser = DEFAULT_ROOT_USER;

    @Param(value = BYPASS_DNS_CHECK, required = false)
    protected boolean bypassDnsCheck;

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public List<String> getExportHosts() {
        return exportHosts;
    }

    public void setExportHosts(List<String> exportHosts) {
        this.exportHosts = exportHosts;
    }

    public String getSecurity() {
        return security;
    }

    public void setSecurity(String security) {
        this.security = security;
    }

    public String getRootUser() {
        return rootUser;
    }

    public void setRootUser(String rootUser) {
        this.rootUser = rootUser;
    }

    public URI createNfsExport() {
        URI fileSystemId = FileStorageUtils.createFileSystem(project, virtualArray, virtualPool, exportName,
                sizeInGb);
        FileStorageUtils.createFileSystemExport(fileSystemId, comment, security, permissions, rootUser, exportHosts, null, bypassDnsCheck);
        return fileSystemId;
    }

    public FileSystemExportParam getNfsExport(URI fileSystemId) {
        FileSystemExportParam export = FileStorageUtils.getNfsExport(fileSystemId, security, permissions, rootUser);
        if (export == null) {
            throw new IllegalStateException(ExecutionUtils.getMessage("illegalState.locateNfsExport"));
        }
        return export;
    }
}
