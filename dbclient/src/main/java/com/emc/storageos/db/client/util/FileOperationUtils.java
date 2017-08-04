package com.emc.storageos.db.client.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FileMountInfo;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.MountInfo;

public class FileOperationUtils {

    private static final Logger _log = LoggerFactory.getLogger(FileOperationUtils.class);

    /**
     * Utility method to find Export rule from DB using Fileshare object
     * 
     * @param fFileshare object
     * @param subDirectory
     * @param securityType
     * @param dbClient
     * @return Export rule
     */
    public static ExportRule findExport(FileShare fs, String subDirectory, String securityType, DbClient dbClient) {
        dbClient.queryByType(FileShare.class, true);
        List<ExportRule> exportList = getExportRules(fs.getId(), false, subDirectory, dbClient);
        for (ExportRule export : exportList) {
            List<String> securityTypes = Arrays.asList(export.getSecFlavor().split("\\s*,\\s*"));
            if (securityTypes.contains(securityType)) {
                return export;
            }
        }
        throw new IllegalArgumentException("No exports found for the provided security type and subdirectory.");
    }

    /**
     * Utility method to find Export rule from DB using Id of the Filesystem
     * 
     * @param Filesystem id
     * @param subDirectory
     * @param securityType
     * @param dbClient
     * @return Export rule
     */
    public static ExportRule findExport(URI id, String subDirectory, String securityType, DbClient dbClient) {
        FileShare fs = dbClient.queryObject(FileShare.class, id);
        return findExport(fs, subDirectory, securityType, dbClient);
    }

    public static List<FileExportRule> queryDBFSExports(FileShare fs, DbClient dbClient) {
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

    public static List<ExportRule> getExportRules(URI id, boolean allDirs, String subDir, DbClient dbClient) {
        FileShare fs = dbClient.queryObject(FileShare.class, id);

        List<ExportRule> exportRule = new ArrayList<>();

        // Query All Export Rules Specific to a File System.
        List<FileExportRule> exports = queryDBFSExports(fs, dbClient);
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

    private static void copyPropertiesToSave(FileExportRule orig, ExportRule dest, FileShare fs) {

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

    public static List<MountInfo> queryDBFSMounts(URI fsId, DbClient dbClient) {
        _log.info("Querying File System mounts using FsId {}", fsId);
        List<MountInfo> fsMounts = new ArrayList<MountInfo>();
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getFileMountsConstraint(fsId);
            List<FileMountInfo> fsDBMounts = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, FileMountInfo.class,
                    containmentConstraint);
            if (fsDBMounts != null && !fsDBMounts.isEmpty()) {
                for (FileMountInfo dbMount : fsDBMounts) {
                    MountInfo mountInfo = new MountInfo();
                    getMountInfo(dbMount, mountInfo);
                    fsMounts.add(mountInfo);
                }
            }
            return fsMounts;
        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }

        return fsMounts;
    }

    private static void getMountInfo(FileMountInfo orig, MountInfo dest) {

        dest.setFsId(orig.getFsId());
        dest.setHostId(orig.getHostId());
        dest.setMountPath(orig.getMountPath());
        dest.setSecurityType(orig.getSecurityType());
        dest.setSubDirectory(orig.getSubDirectory());
    }

    /**
     * Method to get the list file system mounts which are mount on a host
     *
     * @param host
     *            host system URI
     * @return List<MountInfo> List of mount infos
     */
    public static List<MountInfo> queryDBHostMounts(URI host, DbClient dbClient) {
        _log.info("Querying NFS mounts for host {}", host);
        List<MountInfo> hostMounts = new ArrayList<MountInfo>();
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getHostFileMountsConstraint(host);
            List<FileMountInfo> fileMounts = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, FileMountInfo.class,
                    containmentConstraint);
            if (fileMounts != null && !fileMounts.isEmpty()) {
                for (FileMountInfo dbMount : fileMounts) {
                    MountInfo mountInfo = new MountInfo();
                    getMountInfo(dbMount, mountInfo);
                    hostMounts.add(mountInfo);
                }
            }
            return hostMounts;
        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }

        return hostMounts;
    }
}
