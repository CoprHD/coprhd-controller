/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.PlatformUtils;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.management.backup.exceptions.BackupException;

public class RestoreManager {

    private static final Logger log = LoggerFactory.getLogger(RestoreManager.class);

    private static final String OUTPUT_FORMAT = "  %-40s - %s";
    private String[] serviceNames = new String[] {"dbsvc", "geodbsvc", "coordinatorsvc"};

    private RestoreHandler dbRestoreHandler;
    private RestoreHandler zkRestoreHandler;
    private RestoreHandler geoDbRestoreHandler;
    private String nodeId;
    private int nodeCount = 0;
    private String ipAddress4;
    private String ipAddress6;
    private Boolean enableChangeVersion;

    private enum Validation {
        passed,
        failed
    }

    public void setDbRestoreHandler(RestoreHandler dbRestoreHandler) {
        this.dbRestoreHandler = dbRestoreHandler;
    }

    public void setZkRestoreHandler(RestoreHandler zkRestoreHandler) {
        this.zkRestoreHandler = zkRestoreHandler;
    }

    public void setGeoDbRestoreHandler(RestoreHandler geoDbRestoreHandler) {
        this.geoDbRestoreHandler = geoDbRestoreHandler;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    public void setIpAddress4(String ipAddress4) {
        this.ipAddress4 = ipAddress4;
    }

    public void setIpAddress6(String ipAddress6) {
        this.ipAddress6 = ipAddress6;
    }

    public void setEnableChangeVersion(Boolean enableChangeVersion) {
        this.enableChangeVersion = enableChangeVersion;
    }

    /**
     * Purges existing ViPR data
     * 
     * @param needReinit
     *            Need to create reinit marker or not
     */
    public void purge(final boolean needReinit) {
        validateViPRServiceDown();
        try {
            dbRestoreHandler.purge();
            geoDbRestoreHandler.purge();
            geoDbRestoreHandler.checkReinitFile(needReinit);
            zkRestoreHandler.purge();
        } catch (Exception ex) {
            log.error("Failed to purge data(needReinit={})", needReinit, ex);
            throw BackupException.fatals.failedToPurgeViprData(ex);
        }
        log.info(String.format(OUTPUT_FORMAT,
                "ViPR data purge validation", Validation.passed.name()));
    }

    /**
     * Restores backup data to current node, including local db, geo db and coordinator
     * 
     * @param backupPath
     *            The backup data folder
     * @param snapshotName
     *            The backup which will be restored
     * @param geoRestoreFromScratch
     *            True if restore geodb from scratch, or else false
     */
    public void restore(final String backupPath, final String snapshotName, final boolean geoRestoreFromScratch) {
        log.info("Start to restore backup...");
        try {
            validateBackupFolder(backupPath, snapshotName);
            purge(false);

            dbRestoreHandler.replace();
            log.info(String.format(OUTPUT_FORMAT,
                    "Restore data of local database", Validation.passed.name()));
            zkRestoreHandler.replace();
            log.info(String.format(OUTPUT_FORMAT,
                    "Restore data of coordinator", Validation.passed.name()));
            geoDbRestoreHandler.replace(geoRestoreFromScratch);
            log.info(String.format(OUTPUT_FORMAT,
                    "Restore data of geo database", Validation.passed.name()));
        } catch (Exception ex) {
            log.error("Failed to restore with backupset({})", snapshotName, ex);
            throw BackupException.fatals.failedToRestoreBackup(snapshotName, ex);
        }
        log.info("Backup ({}) has been restored on local successfully", snapshotName);
    }

    /**
     * Checks ViPR is running or not.
     */
    private void validateViPRServiceDown() {
        for (String serviceName : serviceNames) {
            boolean isRunning = isServiceRunning(serviceName);
            if (isRunning) {
                log.info("{} is still running", serviceName);
                throw new IllegalStateException(serviceName + " is running");
            }
        }
        log.info(String.format(OUTPUT_FORMAT, "ViPR service down validation", Validation.passed.name()));
    }

    private boolean isServiceRunning(String serviceName) {
        try {
            int pid = PlatformUtils.getServicePid(serviceName);
            log.debug("Found pid({}) of {}", pid, serviceName);
            return pid != 0;
        } catch (Exception ex) {
            log.debug("Can't find pid of {}", serviceName);
            return false;
        }
    }

    /**
     * Validates data structure under backup folder
     * 
     * @param backupPath
     *            The backup folder path
     * @param snapshotName
     *            The backup which will be restored
     */
    private void validateBackupFolder(final String backupPath, final String snapshotName)
            throws IOException {
        File backupFolder = new File(backupPath);
        if (!backupFolder.exists()) {
            throw new FileNotFoundException(String.format("(%s) is not exist", backupPath));
        }

        // Validate backup files
        File[] backupFiles = backupFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(snapshotName + BackupConstants.BACKUP_NAME_DELIMITER)
                        && name.endsWith(BackupConstants.COMPRESS_SUFFIX);
            }
        });
        String errorMessage = String.format("Need db, geodb and zk backup files under folder");
        if (backupFiles == null || backupFiles.length < BackupType.values().length - 1) {
            throw new IllegalArgumentException(errorMessage);
        }

        int matched = 0;
        boolean backupInMultiVdc = false;
        for (File backupFile : backupFiles) {
            String backupFileName = backupFile.getName();
            log.debug("Checking backup file: {}", backupFileName);
            if (!backupFileName.contains(nodeId)
                    && !backupFileName.contains(BackupType.zk.name())) {
                continue;
            }
            if (backupFileName.contains(BackupConstants.BACKUP_NAME_DELIMITER +
                    BackupType.db.name())) {
                dbRestoreHandler.setBackupArchive(backupFile);
                ++matched;
            } else if (backupFileName.contains(BackupConstants.BACKUP_NAME_DELIMITER +
                    BackupType.zk.name())) {
                zkRestoreHandler.setBackupArchive(backupFile);
                ++matched;
            } else if (backupFileName.contains(BackupConstants.BACKUP_NAME_DELIMITER +
                    BackupType.geodb.name())) {
                geoDbRestoreHandler.setBackupArchive(backupFile);
                ++matched;
                if (backupFileName.contains(BackupConstants.BACKUP_NAME_DELIMITER +
                        BackupType.geodbmultivdc.name())) {
                    backupInMultiVdc = true;
                }
            } else {
                log.debug("Invalid backup file: {}", backupFile.getName());
                continue;
            }
            log.debug("Found backup file: {}", backupFile.getName());
        }

        if (matched != BackupType.values().length - 1) {
            throw new IllegalArgumentException(errorMessage);
        }

        // Check backup info
        checkBackupInfo(new File(backupFolder, snapshotName + BackupConstants.BACKUP_INFO_SUFFIX), backupInMultiVdc);

        log.info(String.format(OUTPUT_FORMAT,
                "ViPR backup folder validation", Validation.passed.name()));
    }

    /**
     * Checks version and IPs
     * 
     * @param backupInfoFile The backup info file
     */
    public void checkBackupInfo(final File backupInfoFile, boolean backupInMultiVdc) {
        try (InputStream fis = new FileInputStream(backupInfoFile)) {
            Properties properties = new Properties();
            properties.load(fis);
            checkVersion(properties);
            checkHosts(properties, backupInMultiVdc);
        } catch (IOException ex) {
            // Ignore this exception
            log.warn("Unable to check backup Info", ex);
        }
    }

    private void checkVersion(Properties properties) throws IOException {
        String backupVersion = properties.getProperty(BackupConstants.BACKUP_INFO_VERSION);
        String currentVersion = PlatformUtils.getProductIdent();
        log.info("Backup Version:  {}\nCurrent Version:  {}", backupVersion, currentVersion);
        if (!enableChangeVersion && !backupVersion.equals(currentVersion)) {
            throw new IllegalArgumentException("version is not allowed to be changed");
        }
    }

    private void checkHosts(Properties properties, boolean backupInMultiVdc) throws IOException {
        String backupHosts = properties.getProperty(BackupConstants.BACKUP_INFO_HOSTS);
        log.info("Backup Hosts: {}", backupHosts);

        String[] backupHostArray = backupHosts.replaceAll("\\[|\\]", "").split(", ");
        boolean isHostValid = checkHostsCount(backupHostArray) && checkHostsIp(backupHostArray);

        if (backupInMultiVdc && !isHostValid) {
            String errMessage = "Node count and ip are not allowed to be changed when backup was taken in multi vdc environment";
            log.error(errMessage);
            throw new IllegalArgumentException(errMessage);
        }
    }

    private boolean checkHostsCount(String[] backupHostArray) {
        log.info("Current Host Count: {}", nodeCount);

        boolean hostCountEqual = true;
        if (backupHostArray.length != nodeCount) {
            int backupQuorumHostCount = backupHostArray.length / 2 + 1;
            if (nodeCount < backupQuorumHostCount) {
                log.warn("Current host count is less than quorum of backup host count, the integrity of backup data can't be ensured");
            }
            hostCountEqual = false;
        }
        return hostCountEqual;
    }

    private boolean checkHostsIp(String[] backupHostArray) throws IOException {
        DualInetAddress currentHostAddress = DualInetAddress.fromAddresses(ipAddress4, ipAddress6);
        log.info("Current Host Ip: {}", currentHostAddress.toString());

        boolean inHostArray = false;
        for (String backupHost : backupHostArray) {
            DualInetAddress backupHostAddress;
            if (backupHost.contains(BackupConstants.HOSTS_IP_DELIMITER)) {
                String[] ips = backupHost.trim().split(BackupConstants.HOSTS_IP_DELIMITER);
                backupHostAddress = DualInetAddress.fromAddresses(ips[0], ips[1]);
            } else {
                backupHostAddress = DualInetAddress.fromAddress(backupHost.trim());
            }
            if (currentHostAddress.equals(backupHostAddress)) {
                inHostArray = true;
                break;
            }
        }
        return inHostArray;
    }
}
