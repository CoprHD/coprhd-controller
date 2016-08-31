/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.hostmountadapters;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.util.CustomQueryUtility;
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

    public ExportRule findExport(FileShare fs, String subDirectory, String securityType) {
        dbClient.queryByType(FileShare.class, true);
        List<ExportRule> exportList = getExportRules(fs.getId(), false, subDirectory);
        for (ExportRule export : exportList) {
            if (securityType.equals(export.getSecFlavor())) {
                return export;
            }
        }
        throw new IllegalArgumentException("No exports found for the provided security type and subdirectory.");
    }

    private List<FileExportRule> queryDBFSExports(FileShare fs) {
        _log.info("Querying all ExportRules Using FsId {}", fs.getId());
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getFileExportRulesConstraint(fs.getId());
            List<FileExportRule> fileExportRules = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, FileExportRule.class,
                    containmentConstraint);
            return fileExportRules;
        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }
        return null;
    }

    private List<ExportRule> getExportRules(URI id, boolean allDirs, String subDir) {
        FileShare fs = dbClient.queryObject(FileShare.class, id);

        List<ExportRule> exportRule = new ArrayList<>();

        // Query All Export Rules Specific to a File System.
        List<FileExportRule> exports = queryDBFSExports(fs);
        _log.info("Number of existing exports found : {} ", exports.size());
        if (allDirs) {
            // ALL EXPORTS
            for (FileExportRule rule : exports) {
                ExportRule expRule = new ExportRule();
                // Copy Props
                copyPropertiesToSave(rule, expRule, fs);
                exportRule.add(expRule);
            }
        } else if (subDir != null && subDir.length() > 0) {
            // Filter for a specific Sub Directory export
            for (FileExportRule rule : exports) {
                if (rule.getExportPath().endsWith("/" + subDir)) {
                    ExportRule expRule = new ExportRule();
                    // Copy Props
                    copyPropertiesToSave(rule, expRule, fs);
                    exportRule.add(expRule);
                }
            }
        } else {
            // Filter for No SUBDIR - main export rules with no sub dirs
            for (FileExportRule rule : exports) {
                if (rule.getExportPath().equalsIgnoreCase(fs.getPath())) {
                    ExportRule expRule = new ExportRule();
                    // Copy Props
                    copyPropertiesToSave(rule, expRule, fs);
                    exportRule.add(expRule);
                }
            }
        }
        _log.info("Number of export rules returning {}", exportRule.size());
        return exportRule;
    }

    private void copyPropertiesToSave(FileExportRule orig, ExportRule dest, FileShare fs) {

        dest.setFsID(fs.getId());
        dest.setExportPath(orig.getExportPath());
        dest.setSecFlavor(orig.getSecFlavor());
        dest.setAnon(orig.getAnon());
        dest.setReadOnlyHosts(orig.getReadOnlyHosts());
        dest.setReadWriteHosts(orig.getReadWriteHosts());
        dest.setRootHosts(orig.getRootHosts());
        dest.setMountPoint(orig.getMountPoint());
        // Test
        _log.info("Expor Rule : {} - {}", orig, dest);
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
        ExportRule export = findExport(fs, subDirectory, security);
        String options = "nolock,sec=";
        if (fsType.equalsIgnoreCase("nfs") || fsType.equalsIgnoreCase("nfs4")) {
            options = "sec=";
        }
        // Add to etc/fstab
        mountUtils.addToFSTab(mountPath, export.getMountPoint(), fsType, options + security);

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
