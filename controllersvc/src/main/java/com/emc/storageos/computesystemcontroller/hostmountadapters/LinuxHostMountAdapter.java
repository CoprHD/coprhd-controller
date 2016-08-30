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
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

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

    @Override
    public void doMount(HostDeviceInputOutput args) throws InternalException {
        mountUtils = new LinuxMountUtils(dbClient.queryObject(Host.class, args.getHostId()));
        FileShare fs = dbClient.queryObject(FileShare.class, args.getResId());
        FileExport export = findExport(fs, args.getSubDirectory(), args.getSecurity());
        String fsType = args.getFsType() == null ? "auto" : args.getFsType();
        String subDirectory = args.getSubDirectory() == null ? "!nodir" : args.getSubDirectory();
        // verify mount point
        mountUtils.verifyMountPoint(args.getMountPath());
        // Create directory
        mountUtils.createDirectory(args.getMountPath());
        // Add to the /etc/fstab to allow the os to mount on restart
        mountUtils.addToFSTab(args.getMountPath(), export.getMountPoint(), fsType, "nolock,sec=" + args.getSecurity());
        // Mount the device
        mountUtils.mountPath(args.getMountPath());
    }

    @Override
    public void doUnmount(HostDeviceInputOutput args) throws InternalException {
        mountUtils = new LinuxMountUtils(dbClient.queryObject(Host.class, args.getHostId()));
        // unmount the Export
        mountUtils.unmountPath(args.getMountPath());
        // remove from fstab
        mountUtils.removeFromFSTab(args.getMountPath());
        // delete the directory entry if it's empty
        if (mountUtils.isDirectoryEmpty(args.getMountPath())) {
            mountUtils.deleteDirectory(args.getMountPath());
        }
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
        throw new IllegalArgumentException("no exports found");
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
        if (mountUtils.isDirectoryEmpty(mountPath)) {
            mountUtils.deleteDirectory(mountPath);
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
        mountUtils.unmountPath(mountPath);
    }

}
