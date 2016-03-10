/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup;

import com.emc.storageos.management.backup.exceptions.BackupException;
import org.apache.cassandra.service.StorageService;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class DbBackupHandler extends BackupHandler {
    private static final Logger log = LoggerFactory.getLogger(DbBackupHandler.class);

    public static final String DB_SNAPSHOT_SUBDIR = "snapshots";
    public static final String DB_SSTABLE_TYPE = ".db";

    private List<String> keyspaceList;
    private List<String> ignoreCfList;

    /**
     * Sets vipr keyspace name
     * 
     * @param keyspaceList
     *            The list of vipr keyspace
     */
    public void setKeyspaceList(List<String> keyspaceList) {
        this.keyspaceList = keyspaceList;
    }

    /**
     * Gets keyspace list
     */
    public List<String> getKeyspaceList() {
        return keyspaceList;
    }

    /**
     * Sets ignored column family list to include basic ignore column families, such as "Stats"
     * 
     * @param ignoreCfList
     *            The list of ignored column family
     */
    public void setIgnoreCfList(List<String> ignoreCfList) {
        this.ignoreCfList = ignoreCfList;
    }

    /**
     * Gets ignore ColumnFamily list
     */
    public List<String> getIgnoreCfList() {
        return ignoreCfList;
    }

    /**
     * Gets valid keyspace folder of ViPR DB or GeoDB
     */
    public File getValidKeyspace(final String viprKeyspace) {
        log.debug("Searching ViPR keyspace {}...", viprKeyspace);
        for (String dataFolder : StorageService.instance.getAllDataFileLocations()) {
            File[] keyspaceList = new File(dataFolder).listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.trim().equals(viprKeyspace.trim());
                }
            });
            if (keyspaceList != null && keyspaceList.length == 1 && keyspaceList[0].isDirectory()) {
                log.debug("ViPR keyspace found at {}", keyspaceList[0]);
                return keyspaceList[0];
            }
        }
        throw new IllegalArgumentException(
                String.format("Invalid ViPR keyspace name: %s", viprKeyspace));
    }

    @Override
    public boolean isNeed() {
        // no any precheck here
        return true;
    }

    @Override
    public String createBackup(final String backupTag) {
        // For multi vdc ViPR, need to reinit geodb during restore, so use the special backup type
        // to show the difference
        if (backupType.equals(BackupType.geodb) && backupContext.getVdcList().size() > 1) {
            backupType = BackupType.geodbmultivdc;
        }
        String fullBackupTag = backupTag + BackupConstants.BACKUP_NAME_DELIMITER +
                backupType.name() + BackupConstants.BACKUP_NAME_DELIMITER +
                backupContext.getNodeId() + BackupConstants.BACKUP_NAME_DELIMITER +
                backupContext.getNodeName();
        checkBackupFileExist(backupTag, fullBackupTag);
        for (String viprKeyspace : getKeyspaceList()) {
            try {
                StorageService.instance.takeSnapshot(fullBackupTag, viprKeyspace);
            } catch (IOException ex) {
                clearSnapshot(fullBackupTag);
                throw BackupException.fatals.failedToTakeDbSnapshot(fullBackupTag, viprKeyspace, ex);
            }
        }
        log.info("DB snapshot ({}) has been taken successfully.", fullBackupTag);
        return fullBackupTag;
    }

    @Override
    public File dumpBackup(final String backupTag, final String fullBackupTag) {
        // Prepares backup folder to accept snapshot files
        File targetDir = new File(backupContext.getBackupDir(), backupTag);
        if (!targetDir.exists()) {
            targetDir.mkdir();
        }
        File backupFolder = new File(targetDir, fullBackupTag);
        if (backupFolder.exists()) {
            FileUtils.deleteQuietly(backupFolder);
        }
        backupFolder.mkdir();
        for (String keyspace : getKeyspaceList()) {
            try {
                File ksBackupFolder = new File(backupFolder, keyspace);
                ksBackupFolder.mkdir();

                File[] cfDirs = getValidKeyspace(keyspace).listFiles();
                cfDirs = (cfDirs == null) ? BackupConstants.EMPTY_ARRAY : cfDirs;
                for (File cfDir : cfDirs) {
                    File cfBackupFolder = new File(ksBackupFolder, cfDir.getName());
                    File snapshotFolder = new File(cfDir,
                            DB_SNAPSHOT_SUBDIR + File.separator + fullBackupTag);
                    // Filters ignored Column Family
                    if (ignoreCfList != null && ignoreCfList.contains(cfDir.getName())) {
                        FileUtils.deleteQuietly(snapshotFolder);
                        cfBackupFolder.mkdir();
                        continue;
                    } 
                    if (!snapshotFolder.exists()) {
                        // Handles stale Column Family
                        String[] cfSubFileList = cfDir.list(new FilenameFilter() {
                            @Override
                            public boolean accept(File dir, String name) {
                                return name.endsWith(DB_SSTABLE_TYPE);
                            }
                        });
                        if (cfSubFileList == null || cfSubFileList.length == 0) {
                            log.info("Stale empty cf foler: {}", cfDir.getName());
                        } else {
                            log.warn("No snapshot created for cf: {}", cfDir.getName());
                        }
                        cfBackupFolder.mkdir();
                        continue;
                    }
                    // Moves snapshot folder and renames it with Column Family name
                    Files.move(snapshotFolder.toPath(), cfBackupFolder.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                clearSnapshot(fullBackupTag);
                throw BackupException.fatals.failedToDumpDbSnapshot(fullBackupTag, keyspace, ex);
            }
        }
        log.info("DB snapshot files have been moved to ({}) successfully.",
                backupFolder.getAbsolutePath());
        return backupFolder;
    }

    private void clearSnapshot(final String fullBackupTag) {
        for (String viprKeyspace : getKeyspaceList()) {
            try {
                StorageService.instance.clearSnapshot(fullBackupTag, viprKeyspace);
            } catch (IOException ignore) {
                log.error("Failed to clear DB snapshot: {}, {}", fullBackupTag, ignore.getMessage());
            }
        }
    }

}
