/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.vipr.client.Task;

public class CreateFileSystemExport extends WaitForTask<FileShareRestRep> {
    private final URI fileSystemId;
    private final String protocol;
    private final String security;
    private final String permissions;
    private final String user;
    private final List<String> hosts;
    private final String subDirectory;
    private final String comment;
    private final boolean bypassDnsCheck;

    // Security Types: sys, krb5, krb5i, krb5p
    // Permissions: ro, rw, root
    public CreateFileSystemExport(String fileSystemId, String comment, String protocol, String security, String permissions, String user,
            List<String> hosts, String subDirectory, boolean bypassDnsCheck) {
        this(uri(fileSystemId), comment, protocol, security, permissions, user, hosts, subDirectory, bypassDnsCheck);
    }


    public CreateFileSystemExport(URI fileSystemId, String comment, String protocol, String security, String permissions, String user,
            List<String> hosts, String subDirectory, boolean bypassDnsCheck) {
        this.fileSystemId = fileSystemId;
        this.protocol = protocol;
        this.security = security;
        this.permissions = permissions;
        this.user = user;
        this.hosts = hosts;
        this.subDirectory = subDirectory;
        this.comment = comment;
        this.bypassDnsCheck=bypassDnsCheck;
        provideDetailArgs(fileSystemId, comment, protocol, security, permissions, user, hosts, subDirectory,bypassDnsCheck);
    }



    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        FileSystemExportParam export = new FileSystemExportParam();
        export.setProtocol(protocol);
        export.setSecurityType(security);
        export.setPermissions(permissions);
        export.setRootUserMapping(user);
        export.getEndpoints().addAll(hosts);
        export.setBypassDnsCheck(bypassDnsCheck);
        if (StringUtils.isNotBlank(comment)) {
            export.setComments(comment);
        }
        if (StringUtils.isNotBlank(subDirectory)) {
            export.setSubDirectory(subDirectory);
        }

        return getClient().fileSystems().export(fileSystemId, export);
    }
}
