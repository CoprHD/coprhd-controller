/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.vipr.client.Task;

public class CreateFileSnapshotExport extends WaitForTask<FileSnapshotRestRep> {
    private final URI fileSnapshotId;
    private final String protocol;
    private final String security;
    private final String permissions;
    private final String user;
    private final List<String> hosts;
    private final String subDirectory;
    private final String comment;

    // Security Types: sys, krb5, krb5i, krb5p
    // Permissions: ro, rw, root
    public CreateFileSnapshotExport(String fileSnapshotId, String comment, String protocol, String security, String permissions,
            String user,
            List<String> hosts, String subDirectory) {
        this(uri(fileSnapshotId), comment, protocol, security, permissions, user, hosts, subDirectory);
    }

    public CreateFileSnapshotExport(URI fileSnapshotId, String comment, String protocol, String security, String permissions, String user,
            List<String> hosts, String subDirectory) {
        this.fileSnapshotId = fileSnapshotId;
        this.protocol = protocol;
        this.security = security;
        this.permissions = permissions;
        this.user = user;
        this.hosts = hosts;
        this.subDirectory = subDirectory;
        this.comment = comment;
        provideDetailArgs(fileSnapshotId, comment, protocol, security, permissions, user, hosts, subDirectory);
    }

    @Override
    protected Task<FileSnapshotRestRep> doExecute() throws Exception {
        FileSystemExportParam export = new FileSystemExportParam();
        export.setProtocol(protocol);
        export.setSecurityType(security);
        export.setPermissions(permissions);
        export.setRootUserMapping(user);
        export.getEndpoints().addAll(hosts);
        if (StringUtils.isNotBlank(comment)) {
            export.setComments(comment);
        }
        if (StringUtils.isNotBlank(subDirectory)) {
            export.setSubDirectory(subDirectory);
        }

        return getClient().fileSnapshots().export(fileSnapshotId, export);
    }
}