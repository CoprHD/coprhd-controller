/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.backup;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.vipr.model.sys.healthmonitor.DataDiskStats;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.management.backup.util.ZipUtil;
import com.emc.storageos.management.backup.exceptions.BackupException;

public class BackupManager implements BackupManagerMBean {
    private static final Logger log = LoggerFactory.getLogger(BackupManager.class);
    public static final String MBEAN_NAME = "org.emc.storageos.management.backup:type=BackupManager";
    private static final int DEFAULT_DISK_QUOTA_GB = 50;
    private static final String DF_COMMAND = "/bin/df";
    private static final long DF_COMMAND_TIMEOUT = 120000;
    private static final String SPACE_VALUE = "\\s+";

    private BackupContext backupContext;
    private CoordinatorClient coordinatorClient;
    private BackupHandler backupHandler;

    /**
     * Sets backup context
     * 
     * @param backupContext
     */
    public void setBackupContext(BackupContext backupContext) {
        this.backupContext = backupContext;
    }

    /**
     * Gets backup context
     */
    public BackupContext getBackupContext() {
        return this.backupContext;
    }

    /**
     * Sets coordinator client
     * 
     * @param coordinatorClient
     *            The instance of CoordinatorClient
     */
    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    /**
     * Gets coordinator client
     */
    public CoordinatorClient getCoordinatorClient() {
        return this.coordinatorClient;
    }

    /**
     * Gets instance of BackupHandler
     */
    public BackupHandler getBackupHandler() {
        return backupHandler;
    }

    /**
     * Sets instance of BackupHandler
     * 
     * @param backupHandler
     *            The detail instance of BackupHandler
     */
    public void setBackupHandler(BackupHandler backupHandler) {
        this.backupHandler = backupHandler;
    }

    private int getBackupMaxUsedDiskPercentage() {
        PropertyInfo propInfo = coordinatorClient.getPropertyInfo();
        return Integer.parseInt(propInfo.getProperty(BackupConstants.BACKUP_MAX_USED_DISK_PERCENTAGE));
    }

    private int getBackupDisabledDiskPercentage() {
        PropertyInfo propInfo = coordinatorClient.getPropertyInfo();
        return Integer.parseInt(propInfo.getProperty(BackupConstants.BACKUP_THRESHOLD_DISK_PERCENTAGE));
    }

    public int getQuotaGb() {
        DataDiskStats dataDiskStats = getDataDiskStats();
        if (dataDiskStats == null) {
            return DEFAULT_DISK_QUOTA_GB;
        }
        long diskTotalKB = dataDiskStats.getDataUsedKB() + dataDiskStats.getDataAvailKB();
        int quotaGB = (int) ((diskTotalKB * getBackupMaxUsedDiskPercentage()) / (100 * 1024 * 1024));
        log.info("Quota is {} GB", quotaGB);
        return quotaGB;
    }

    /**
     * Gets platform MBeanServer and register current MBean. Therefore, can find it by JMX client from
     * local or remote.
     */
    public void init() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            mbs.registerMBean(this, new ObjectName(MBEAN_NAME));
        } catch (JMException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkBackupDir() {
        if (!backupContext.getBackupDir().exists() && !backupContext.getBackupDir().mkdirs()) {
            throw BackupException.fatals.failedToCreateBackupFolder(
                    backupContext.getBackupDir().getAbsolutePath());
        }
    }

    /**
     * Validates backup quota limitation and disk used status
     */
    public void checkQuotaAndDiskStatus() {
        validateQuotaLimit();
        validateDiskUsedStatus();
    }

    /**
     * Ensure the actual disk space of backup files does not exceed the Quota size.
     * Only calculate compress file under backup dir by now.
     */
    public void validateQuotaLimit() {
        long currentSize = 0;
        long backupQuotaByte = getQuotaGb() * BackupConstants.GIGABYTE;

        File[] backupFiles = backupContext.getBackupDir().listFiles();
        if (backupFiles != null && backupFiles.length != 0) {
            for (File file : backupFiles) {
                if (!file.isDirectory()) {
                    continue;
                }
                File[] backupSubFiles = file.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.endsWith(BackupConstants.COMPRESS_SUFFIX);
                    }
                });
                if (backupSubFiles == null || backupSubFiles.length == 0) {
                    continue;
                }
                for (File subfile : backupSubFiles) {
                    currentSize += subfile.length();
                }
            }
        }
        log.debug("Quota size: {}\tCurrent backup size: {}", backupQuotaByte, currentSize);
        if (currentSize > backupQuotaByte) {
            throw BackupException.fatals.backupSizeExceedQuota(
                    getReadableSize(backupQuotaByte),
                    getReadableSize(currentSize - backupQuotaByte));
        }
    }

    private String getReadableSize(long size) {
        String sizeStr = null;
        if (size < BackupConstants.KILOBYTE) {
            sizeStr = String.format("%dB", size);
        } else if (size < BackupConstants.MEGABYTE) {
            sizeStr = String.format("%dKB", size / BackupConstants.KILOBYTE);
        } else if (size < BackupConstants.GIGABYTE) {
            sizeStr = String.format("%dMB", size / BackupConstants.MEGABYTE);
        } else {
            sizeStr = String.format("%dGB", size / BackupConstants.GIGABYTE);
        }
        return sizeStr;
    }

    private void validateDiskUsedStatus() {
        DataDiskStats dataDiskStatus = getDataDiskStats();
        if (dataDiskStatus == null) {
            log.info("Can't find disk size of /data");
            return;
        }
        long dataTotalKB = dataDiskStatus.getDataUsedKB() + dataDiskStatus.getDataAvailKB();
        int diskUsedPercentage = (int) (dataDiskStatus.getDataUsedKB() * 100 / dataTotalKB);
        log.info("Disk used percentage limit: {}\tCurrent Disk used percentage: {}",
                getBackupDisabledDiskPercentage(), diskUsedPercentage);
        if (diskUsedPercentage > getBackupDisabledDiskPercentage()) {
            throw BackupException.fatals.backupDisabledAsDiskFull(
                    diskUsedPercentage, getBackupDisabledDiskPercentage());
        }
    }

    private DataDiskStats getDataDiskStats() {
        final String[] cmd = { DF_COMMAND };
        Exec.Result result = Exec.sudo(DF_COMMAND_TIMEOUT, cmd);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.error("getDataDiskStats() is unsuccessful. Command exit value is: {}",
                    result.getExitValue());
            return null;
        }
        log.info("df result: {}", result.getStdOutput());
        String[] lines = result.getStdOutput().split("\n");
        DataDiskStats dataDiskStats = new DataDiskStats();
        for (String line : lines) {
            String[] v = line.split(SPACE_VALUE);
            if (v != null && v.length > 5) {
                if ("/data".equals(v[5].trim())) {
                    dataDiskStats.setDataUsedKB(Long.parseLong(v[2]));
                    dataDiskStats.setDataAvailKB(Long.parseLong(v[3]));
                    return dataDiskStats;
                }
            }
        }
        return null;
    }

    @Override
    public void create(final String backupTag) {
        Preconditions.checkArgument(backupTag != null
                && !backupTag.trim().isEmpty()
                && backupTag.length() < 256,
                "Invalid backup name: %s", backupTag);
        Preconditions.checkNotNull(backupHandler);
        if (!backupHandler.isNeed()) {
            throw BackupException.fatals.noNeedBackup();
        }
        checkBackupDir();
        checkQuotaAndDiskStatus();
        log.info("Start to create backup with prefix ({})...", backupTag);

        // 1. construct full backup tag and take db snapshot
        String fullBackupTag = backupHandler.createBackup(backupTag);
        // 2. dump backup files to system backup folder
        File backupFolder = backupHandler.dumpBackup(backupTag, fullBackupTag);
        // 3. compress backup files
        File backupZip = compressBackupFolder(backupFolder);
        checkQuotaAndDiskStatus();
        // 4. record the digest of backup file
        computeMd5(backupZip, backupZip.getName() + BackupConstants.MD5_SUFFIX);
        // Includes RuntimeException here, to ensure no junk data left
        log.info("Backup is created successfully: {}", backupTag);
    }

    /**
     * Compresses backup folder to package and delete both backup folder and compress package if any
     * exception thrown.
     * 
     * @param backupFolder
     *            The folder which will be compressed
     */
    private File compressBackupFolder(File backupFolder) {
        File backupZip = new File(backupFolder.getParentFile(),
                backupFolder.getName() + BackupConstants.COMPRESS_SUFFIX);
        try {
            ZipUtil.pack(backupFolder, backupZip);
        } catch (IOException ex) {
            if (backupZip.exists()) {
                backupZip.delete();
            }
            throw BackupException.fatals.failedToCompressBackupFolder(
                    backupFolder.getName(), backupZip.getName(), ex);
        } finally {
            if (backupFolder != null && backupFolder.exists()) {
                FileUtils.deleteQuietly(backupFolder);
            }
        }
        return backupZip;
    }

    /**
     * Computes md5 of specified file and save the result to another file.
     * 
     * @param targetFile
     *            The file which needs to record md5
     * @param md5FileName
     *            The file to save the md5 result
     */
    private void computeMd5(File targetFile, String md5FileName) {
        Preconditions.checkArgument(targetFile != null && targetFile.exists(), "Invalid File");
        Preconditions.checkArgument(md5FileName != null && !md5FileName.trim().isEmpty(), "Invalid File");
        PrintWriter out = null;
        try {
            File md5File = new File(targetFile.getParentFile(), md5FileName);
            StringBuilder digestBuilder = new StringBuilder();
            digestBuilder.append(Files.hash(targetFile, Hashing.md5()).toString());
            digestBuilder.append("\t");
            digestBuilder.append(targetFile.length());
            digestBuilder.append("\t");
            digestBuilder.append(targetFile.getName());
            out = new PrintWriter(new BufferedWriter(new FileWriter(md5File, false)));
            out.println(digestBuilder.toString());
        } catch (IOException ex) {
            throw BackupException.fatals.failedToComputeMd5(targetFile.getName(), ex);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    @Override
    public List<BackupSetInfo> list() {
        checkBackupDir();
        List<BackupSetInfo> backupSetInfoList = new ArrayList<BackupSetInfo>();
        File[] backupDirs = backupContext.getBackupDir().listFiles();
        if (backupDirs == null || backupDirs.length == 0) {
            return backupSetInfoList;
        }
        for (File dir : backupDirs) {
            if (!dir.isDirectory()) {
                continue;
            }

            File[] backupFiles = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(BackupConstants.COMPRESS_SUFFIX) || name.endsWith(BackupConstants.BACKUP_INFO_SUFFIX);
                }
            });

            if (backupFiles == null || backupFiles.length == 0) {
                continue;
            }

            for (File file : backupFiles) {
                BackupSetInfo backupSetInfo = new BackupSetInfo();
                backupSetInfo.setName(file.getName());

                long createTime = 0;
                if (file.getName().endsWith(BackupConstants.BACKUP_INFO_SUFFIX)) {
                    log.info("Get the create time from info file {}", file.getName());
                    BackupOps ops = new BackupOps();
                    createTime = ops.getCreateTimeFromPropFile(file);
                }

                if (createTime == 0) {
                    createTime = file.lastModified();
                }

                backupSetInfo.setCreateTime(createTime);
                backupSetInfo.setSize(file.length());
                backupSetInfoList.add(backupSetInfo);
            }
        }
        log.info("Backup is listed successfully: {}", backupSetInfoList);
        return backupSetInfoList;
    }

    @Override
    public void delete(final String backupTag) {
        Preconditions.checkArgument(backupTag != null && !backupTag.trim().isEmpty() && backupTag.length() < 256,
                "Invalid backup name: %s", backupTag);
        checkBackupDir();
        File[] backupFiles = backupContext.getBackupDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                File file = new File(dir, name);
                return name.equals(backupTag) && file.isDirectory();
            }
        });
        if (backupFiles == null || backupFiles.length == 0) {
            throw BackupException.fatals.backupFileNotFound(backupTag);
        }
        for (File file : backupFiles) {
            try {
                FileUtils.forceDelete(file);
            } catch (IOException ex) {
                throw BackupException.fatals.failedToDeleteBackupFile(file.getName(), ex);
            }
        }
        log.info("Backup is deleted successfully: {}", Arrays.toString(backupFiles));
    }

    /**
     * Executes clean up operations here:
     * <p>
     * 1. Unregister MBean from PlatformMBeanServer
     */
    public void shutdown() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            mbs.unregisterMBean(new ObjectName(MBEAN_NAME));
        } catch (JMException e) {
            throw new RuntimeException(e);
        }
    }
}
