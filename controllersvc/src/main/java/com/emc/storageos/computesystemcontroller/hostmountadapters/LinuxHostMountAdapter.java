/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.hostmountadapters;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Host;
import com.iwave.ext.command.CommandException;

/**
 * 
 * @author yelkaa
 * 
 */
public class LinuxHostMountAdapter extends AbstractMountAdapter {

    private LinuxMountUtils mountUtils;

    public LinuxMountUtils getLinuxMountUtils() {
        return mountUtils;
    }

    public void setLinuxMountUtils(LinuxMountUtils mountUtils) {
        this.mountUtils = mountUtils;
    }

    public LinuxHostMountAdapter() {

    }

    public FileExport findExport(FileShare fs, String subDirectory, String securityType) {
        List<FileExport> exportList = queryDBFSExports(fs);
        dbClient.queryByType(FileShare.class, true);
        if (subDirectory == null || subDirectory.equalsIgnoreCase("!nodir") || subDirectory.isEmpty()) {
            for (FileExport export : exportList) {
                if (export.getSubDirectory().isEmpty() && securityType.equals(export.getSecurityType())) {
                    return export;
                }
            }
        }
        for (FileExport export : exportList) {
            if (subDirectory.equals(export.getSubDirectory()) && securityType.equals(export.getSecurityType())) {
                return export;
            }
        }
        throw new IllegalArgumentException("No exports found for the provided security type and subdirectory.");
    }

    private List<FileExport> queryDBFSExports(FileShare fs) {
        FSExportMap exportMap = fs.getFsExports();

        List<FileExport> fileExports = new ArrayList<FileExport>();
        if (exportMap != null) {
            fileExports.addAll(exportMap.values());
        }
        return fileExports;
    }

    @Override
    public void createDirectory(URI hostId, String mountPath) {
        mountUtils = new LinuxMountUtils(dbClient.queryObject(Host.class, hostId));
        // Create directory
        mountUtils.createDirectory(mountPath);
    }

    @Override
    public void addToFSTab(URI hostId, String mountPath, URI resId, String subDirectory, String security, String fsType) {
        mountUtils = new LinuxMountUtils(dbClient.queryObject(Host.class, hostId));
        FileShare fs = dbClient.queryObject(FileShare.class, resId);
        FileExport export = findExport(fs, subDirectory, security);
        // Add to etc/fstab
        mountUtils.addToFSTab(mountPath, export.getMountPoint(), fsType, "nolock,sec=" + security);

    }

    @Override
    public void mountDevice(URI hostId, String mountPath) {
        mountUtils = new LinuxMountUtils(dbClient.queryObject(Host.class, hostId));
        // mount device
        mountUtils.mountPath(mountPath);
    }

    @Override
    public void verifyMountPoint(URI hostId, String mountPath) {
        mountUtils = new LinuxMountUtils(dbClient.queryObject(Host.class, hostId));
        // verify if mount point already exists in host
        mountUtils.verifyMountPoint(mountPath);
    }

    @Override
    public void deleteDirectory(URI hostId, String mountPath) {
        mountUtils = new LinuxMountUtils(dbClient.queryObject(Host.class, hostId));
        // Delete directory
        try {
            if (mountUtils.isDirectoryEmpty(mountPath)) {
                mountUtils.deleteDirectory(mountPath);
            }
        } catch (CommandException ex) {
            if (!ex.getMessage().contains("No such file or directory")) {
                throw ex;
            }
        }
    }

    @Override
    public void removeFromFSTab(URI hostId, String mountPath) {
        mountUtils = new LinuxMountUtils(dbClient.queryObject(Host.class, hostId));
        // remove mount entry from /etc/fstab
        mountUtils.removeFromFSTab(mountPath);
    }

    @Override
    public void unmountDevice(URI hostId, String mountPath) {
        mountUtils = new LinuxMountUtils(dbClient.queryObject(Host.class, hostId));
        // unmount device
        try {
            mountUtils.unmountPath(mountPath);
        } catch (CommandException ex) {
            if (!ex.getMessage().contains("not mounted") && !ex.getMessage().contains("mountpoint not found")) {
                throw ex;
            }
        }
    }

}
