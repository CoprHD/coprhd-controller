/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.hostmountadapters;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.util.FileOperationUtils;
import com.emc.storageos.model.file.ExportRule;
import com.iwave.ext.command.CommandException;

/**
 * 
 * @author yelkaa
 * 
 */
public class LinuxHostMountAdapter extends AbstractMountAdapter {

    private static final Logger _log = LoggerFactory.getLogger(LinuxHostMountAdapter.class);
    private LinuxMountUtils mountUtils;

    public LinuxMountUtils getLinuxMountUtils() {
        return mountUtils;
    }

    public void setLinuxMountUtils(LinuxMountUtils mountUtils) {
        this.mountUtils = mountUtils;
    }

    public LinuxHostMountAdapter() {

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
        ExportRule export = FileOperationUtils.findExport(fs, subDirectory, security, dbClient);
        String options = "nolock,sec=" + security;
        // Add to etc/fstab
        mountUtils.addToFSTab(mountPath, export.getMountPoint(), fsType, options);
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
        } catch (ComputeSystemControllerException ex) {
            if (!ex.getMessage().contains("not mounted") && !ex.getMessage().contains("mountpoint not found")) {
                throw ex;
            }
        }
    }

}
